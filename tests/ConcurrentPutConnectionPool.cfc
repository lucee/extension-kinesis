/**
 * Concurrent kinesisPut load against the AWS SDK Apache HTTP connection pool.
 *
 * Exercises the CFML frontend (kinesisPut) with more concurrent callers than the
 * AWS SDK default pool size (50), so a misconfigured / unconfigured pool would
 * surface ConnectionPoolTimeoutException ("Timeout waiting for connection from pool").
 *
 * Never skipped: CI (and local) must provide LocalStack / LUCEE_KINESIS_* and stream
 * kinesis-pool-test (or LUCEE_KINESIS_TEST_STREAM). Missing deps fail the suite.
 * See also HttpConnectionPoolSettings.cfc for pool-config coverage without PutRecord.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="kinesis" {

    variables.parallelCount = 55;
    variables.streamName = "kinesis-pool-test";

    function beforeAll() {
        var cfg = new KinesisLocalStack().requireReady("ConcurrentPutConnectionPool");
        variables.kinesisAccessKeyId = cfg.accessKeyId;
        variables.kinesisSecretAccessKey = cfg.secretAccessKey;
        variables.kinesisHost = cfg.host;
        variables.kinesisRegion = cfg.region;
        variables.streamName = cfg.streamName;
        variables.localstack = new KinesisLocalStack();
    }

    function run(testResults, testBox) {
        describe(
            title = "Concurrent kinesisPut beyond SDK default HTTP pool (50)",
            body = function() {

                it(title = "55 concurrent sync kinesisPut calls do not hit connection pool timeout", body = function() {
                    var result = runParallelPuts(
                        parallelCount = variables.parallelCount,
                        streamName = variables.streamName
                    );

                    assertNoPoolTimeout(result, "sync kinesisPut");
                    expect(result.successCount).toBe(variables.parallelCount);
                });

                it(title = "two waves of 55 concurrent sync kinesisPut reuse the pool without timeout", body = function() {
                    var wave1 = runParallelPuts(
                        parallelCount = variables.parallelCount,
                        streamName = variables.streamName,
                        waveId = createUUID()
                    );
                    assertNoPoolTimeout(wave1, "sync kinesisPut wave 1");
                    expect(wave1.successCount).toBe(variables.parallelCount);

                    var wave2 = runParallelPuts(
                        parallelCount = variables.parallelCount,
                        streamName = variables.streamName,
                        waveId = createUUID()
                    );
                    assertNoPoolTimeout(wave2, "sync kinesisPut wave 2");
                    expect(wave2.successCount).toBe(variables.parallelCount);
                });

                it(title = "55 concurrent kinesisPut(parallel=true) do not hit connection pool timeout", body = function() {
                    // Do not nest CF threads around parallel=true: that queues N jobs onto the
                    // extension's fixed executor (default maxThreads=10) while each CF thread
                    // waits only ~10s. Under Lucee 7 load this races. Fire async puts from the
                    // request thread and wait for all listeners (same pattern as KenesisTest).
                    var result = runAsyncPuts(
                        parallelCount = variables.parallelCount,
                        streamName = variables.streamName
                    );

                    assertNoPoolTimeout(result, "kinesisPut parallel=true");
                    expect(result.successCount).toBe(variables.parallelCount);
                });

                // No negative "force pool exhaustion" case: LocalStack PutRecord is too fast for a
                // reliable ConnectionPoolTimeoutException under maxConnections=5 (all waiters
                // acquire before connectionTimeout). Mirrors S3 LDEV-6373 (load must succeed).
                // Pool wiring is covered by HttpConnectionPoolSettings.cfc.
            }
        );
    }

    /**
     * Spawns parallelCount CF threads each calling sync kinesisPut (parallel=false).
     * Sync puts hold an HTTP connection for the duration of PutRecord, which is what
     * saturates the Apache connection pool when concurrency exceeds maxConnections.
     */
    private struct function runParallelPuts(
        required numeric parallelCount,
        required string streamName,
        string waveId = createUUID()
    ) {
        var names = [];
        var record = variables.localstack.sampleRecord("http-pool-concurrency");

        for (var i = 1; i <= arguments.parallelCount; i++) {
            var threadName = "kinesis-pool-#arguments.waveId#-#i#";
            arrayAppend(names, threadName);

            thread
                name=threadName
                action="run"
                streamName=arguments.streamName
                record=duplicate(record)
                putIndex=i
                accessKeyId=variables.kinesisAccessKeyId
                secretAccessKey=variables.kinesisSecretAccessKey
                host=variables.kinesisHost
                region=variables.kinesisRegion
            {
                thread.success = false;
                thread.error = javacast("null", "");

                try {
                    var rsp = kinesisPut(
                        data = record,
                        partitionKey = "kinesis-pool-" & putIndex,
                        streamName = streamName,
                        parallel = false,
                        accessKeyId = accessKeyId,
                        secretAccessKey = secretAccessKey,
                        host = host,
                        location = region
                    );
                    if (!structKeyExists(rsp, "sequenceNumber")) {
                        throw(message = "kinesisPut response missing sequenceNumber", detail = serializeJSON(rsp));
                    }
                    thread.success = true;
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
            streamName: arguments.streamName
        };
    }

    /**
     * Queues parallelCount kinesisPut(parallel=true) calls from the request thread,
     * then waits for all onSuccess/onError listeners. Avoids nesting CF threads around
     * the extension's async executor (which defaults to maxThreads=10 on Java 11).
     */
    private struct function runAsyncPuts(
        required numeric parallelCount,
        required string streamName
    ) {
        var doneKeys = [];
        var record = variables.localstack.sampleRecord("http-pool-concurrency");
        var waveId = createUUID();

        for (var i = 1; i <= arguments.parallelCount; i++) {
            var doneKey = "kinesis-pool-async-" & waveId & "-" & i;
            arrayAppend(doneKeys, doneKey);

            kinesisPut(
                data = duplicate(record),
                partitionKey = "kinesis-pool-async-" & i,
                streamName = arguments.streamName,
                parallel = true,
                listener = createAsyncDoneListener(doneKey),
                accessKeyId = variables.kinesisAccessKeyId,
                secretAccessKey = variables.kinesisSecretAccessKey,
                host = variables.kinesisHost,
                location = variables.kinesisRegion
            );
        }

        // Default executor is 10 threads; allow plenty of time for the queue to drain.
        var waits = 1200;
        while ((--waits) > 0) {
            var pending = 0;
            for (var key in doneKeys) {
                if (!structKeyExists(application, key)) {
                    pending++;
                }
            }
            if (pending == 0) {
                break;
            }
            sleep(50);
        }

        var exceptions = [];
        var successCount = 0;
        for (var key in doneKeys) {
            if (!structKeyExists(application, key)) {
                arrayAppend(exceptions, {
                    message: "parallel kinesisPut did not complete in time",
                    detail: "doneKey=#key#"
                });
                continue;
            }
            var outcome = application[key];
            structDelete(application, key);
            if (outcome.ok ?: false) {
                successCount++;
            }
            else {
                arrayAppend(exceptions, outcome.error ?: {
                    message: "parallel kinesisPut onError without detail",
                    detail: "doneKey=#key#"
                });
            }
        }

        return {
            exceptions: exceptions,
            successCount: successCount,
            parallelCount: arguments.parallelCount,
            streamName: arguments.streamName
        };
    }

    /**
     * Bind doneKey as a function argument so async listeners do not all close over the loop var.
     */
    private struct function createAsyncDoneListener(required string doneKey) {
        var key = arguments.doneKey;
        return {
            onSuccess: function(result) {
                application[key] = { ok: true };
            },
            onError: function(error) {
                application[key] = { ok: false, error: error };
            }
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

}
