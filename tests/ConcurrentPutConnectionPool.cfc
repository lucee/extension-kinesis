/**
 * Concurrent kinesisPut load against the AWS SDK Apache HTTP connection pool.
 *
 * Exercises the CFML frontend (kinesisPut) with more concurrent callers than the
 * AWS SDK default pool size (50), so a misconfigured / unconfigured pool would
 * surface ConnectionPoolTimeoutException ("Timeout waiting for connection from pool").
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="kinesis" {

    variables.parallelCount = 55;
    variables.streamName = "kds-dk-logs-dev";
    variables.notSupported = true;

    function beforeAll() {
        try {
            KinesisValidate();
            variables.notSupported = false;
        }
        catch (any e) {
            variables.notSupported = true;
        }

        if (!variables.notSupported) {
            try {
                kinesisInfo(variables.streamName);
            }
            catch (any e1) {
                try {
                    kinesisInfo("kds-dk-logs-localdev");
                    variables.streamName = "kds-dk-logs-localdev";
                }
                catch (any e2) {
                }
            }
        }
    }

    function run(testResults, testBox) {
        describe(
            title = "Concurrent kinesisPut beyond SDK default HTTP pool (50)",
            skip = variables.notSupported,
            body = function() {

                it(title = "55 concurrent sync kinesisPut calls do not hit connection pool timeout", body = function() {
                    var result = runParallelPuts(
                        parallelCount = variables.parallelCount,
                        streamName = variables.streamName,
                        parallelFlag = false
                    );

                    assertNoPoolTimeout(result, "sync kinesisPut");
                    expect(result.successCount).toBe(variables.parallelCount);
                });

                it(title = "two waves of 55 concurrent sync kinesisPut reuse the pool without timeout", body = function() {
                    var wave1 = runParallelPuts(
                        parallelCount = variables.parallelCount,
                        streamName = variables.streamName,
                        parallelFlag = false,
                        waveId = createUUID()
                    );
                    assertNoPoolTimeout(wave1, "sync kinesisPut wave 1");
                    expect(wave1.successCount).toBe(variables.parallelCount);

                    var wave2 = runParallelPuts(
                        parallelCount = variables.parallelCount,
                        streamName = variables.streamName,
                        parallelFlag = false,
                        waveId = createUUID()
                    );
                    assertNoPoolTimeout(wave2, "sync kinesisPut wave 2");
                    expect(wave2.successCount).toBe(variables.parallelCount);
                });

                it(title = "55 concurrent kinesisPut(parallel=true) do not hit connection pool timeout", body = function() {
                    var result = runParallelPuts(
                        parallelCount = variables.parallelCount,
                        streamName = variables.streamName,
                        parallelFlag = true
                    );

                    assertNoPoolTimeout(result, "kinesisPut parallel=true");
                    expect(result.successCount).toBe(variables.parallelCount);
                });

                it(
                    title = "with maxConnections forced to 5, 55 concurrent sync puts surface pool exhaustion",
                    body = function() {
                        application action="update" kinesis={
                            pool: {
                                maxConnections: 5,
                                connectionTimeout: 500
                            }
                        };

                        try {
                            var result = runParallelPuts(
                                parallelCount = variables.parallelCount,
                                streamName = variables.streamName,
                                parallelFlag = false,
                                waveId = "exhaust-" & createUUID()
                            );

                            var poolTimeouts = 0;
                            for (var err in result.exceptions) {
                                if (isConnectionPoolTimeout(err)) {
                                    poolTimeouts++;
                                }
                            }

                            expect(poolTimeouts).toBeGT(
                                0,
                                "Expected ConnectionPoolTimeoutException with maxConnections=5 and #variables.parallelCount# concurrent puts; "
                                    & "got #arrayLen(result.exceptions)# exception(s), #result.successCount# success(es). "
                                    & "If zero pool timeouts, pool settings may not have been applied."
                            );
                        }
                        finally {
                            application action="update" kinesis={
                                pool: {
                                    maxConnections: 128,
                                    connectionTimeout: 10000
                                }
                            };
                        }
                    }
                );
            }
        );
    }

    /**
     * Spawns parallelCount CF threads each calling kinesisPut (CFML frontend).
     * Sync puts (parallelFlag=false) hold an HTTP connection for the duration of PutRecord,
     * which is what saturates the Apache connection pool when concurrency exceeds maxConnections.
     */
    private struct function runParallelPuts(
        required numeric parallelCount,
        required string streamName,
        boolean parallelFlag = false,
        string waveId = createUUID()
    ) {
        var names = [];
        var record = createRecord();

        for (var i = 1; i <= arguments.parallelCount; i++) {
            var threadName = "kinesis-pool-#arguments.waveId#-#i#";
            arrayAppend(names, threadName);

            thread
                name=threadName
                action="run"
                streamName=arguments.streamName
                parallelFlag=arguments.parallelFlag
                record=duplicate(record)
                putIndex=i
            {
                thread.success = false;
                thread.error = javacast("null", "");

                try {
                    if (parallelFlag) {
                        var doneKey = "kinesis-pool-done-" & createUUID();

                        kinesisPut(
                            data = record,
                            partitionKey = "kinesis-pool-" & putIndex,
                            streamName = streamName,
                            parallel = true,
                            listener = {
                                onSuccess: function(result) {
                                    application[doneKey] = { ok: true };
                                },
                                onError: function(error) {
                                    application[doneKey] = { ok: false, error: error };
                                }
                            }
                        );

                        var waits = 200;
                        while ((--waits) > 0) {
                            sleep(50);
                            if (structKeyExists(application, doneKey)) {
                                break;
                            }
                        }

                        if (!structKeyExists(application, doneKey)) {
                            throw(message = "parallel kinesisPut did not complete in time", detail = "putIndex=#putIndex#");
                        }

                        var outcome = application[doneKey];
                        structDelete(application, doneKey);

                        if (!(outcome.ok ?: false)) {
                            throw(outcome.error ?: { message: "parallel kinesisPut onError without detail" });
                        }

                        thread.success = true;
                    }
                    else {
                        var rsp = kinesisPut(
                            data = record,
                            partitionKey = "kinesis-pool-" & putIndex,
                            streamName = streamName,
                            parallel = false
                        );
                        if (!structKeyExists(rsp, "sequenceNumber")) {
                            throw(message = "kinesisPut response missing sequenceNumber", detail = serializeJSON(rsp));
                        }
                        thread.success = true;
                    }
                }
                catch (any err) {
                    thread.success = false;
                    thread.error = err;
                }
            }
        }

        thread action="join" name=arrayToList(names);

        var exceptions = [];
        var successCount = 0;
        for (var name in names) {
            var meta = cfthread[name];
            if (meta.success ?: false) {
                successCount++;
            }
            else if (!isNull(meta.error) && isDefined("meta.error")) {
                arrayAppend(exceptions, meta.error);
            }
            else if (!(meta.success ?: false)) {
                arrayAppend(exceptions, {
                    message: "thread finished without success",
                    detail: "name=#name# status=#meta.status ?: ''#"
                });
            }
        }

        return {
            exceptions: exceptions,
            successCount: successCount,
            parallelCount: arguments.parallelCount,
            streamName: arguments.streamName,
            parallelFlag: arguments.parallelFlag
        };
    }

    private void function assertNoPoolTimeout(required struct result, required string label) {
        if (!arrayLen(result.exceptions)) {
            return;
        }

        var first = result.exceptions[1];
        var msg = first.message ?: (isSimpleValue(first) ? first : serializeJSON(first));

        if (isConnectionPoolTimeout(first)) {
            throw(
                message = "Connection pool timeout during #arguments.label# [#result.parallelCount# concurrent calls]. #msg#",
                detail = first.detail ?: ""
            );
        }

        throw(
            message = "Unexpected error during #arguments.label#: #msg#",
            detail = first.detail ?: ""
        );
    }

    private boolean function isConnectionPoolTimeout(required any e) {
        var msg = "";
        try {
            msg &= (e.message ?: "");
        }
        catch (any ignore) {
        }
        try {
            msg &= (e.detail ?: "");
        }
        catch (any ignore) {
        }
        try {
            msg &= (e.stackTrace ?: "");
        }
        catch (any ignore) {
        }
        try {
            if (!isNull(e.cause)) {
                msg &= (e.cause.message ?: "");
                msg &= (e.cause.stackTrace ?: "");
            }
        }
        catch (any ignore) {
        }

        return findNoCase("Timeout waiting for connection from pool", msg)
            || findNoCase("ConnectionPoolTimeoutException", msg)
            || findNoCase("Acquire operation took longer than the configured maximum time", msg);
    }

    private struct function createRecord() {
        return {
            "source": "kinesis-http-pool-test",
            "type": "pool-exhaustion-test",
            "detail": {
                "metadata": {
                    "key": createUUID(),
                    "ts": dateTimeFormat(now(), "iso8601"),
                    "datehour": dateTimeFormat(now(), "yyyy-mm-dd HH:nn:ss")
                },
                "data": {
                    "action": "http-pool-concurrency",
                    "notes": "connection pool concurrency probe"
                }
            }
        };
    }

}
