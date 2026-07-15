/**
 * Functional coverage for KinesisPut.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="kinesis" {

    variables.localstack = "";
    variables.cfg = {};

    function beforeAll() {
        variables.localstack = new KinesisLocalStack();
        variables.cfg = variables.localstack.requireReady("KinesisPut");
    }

    function run(testResults, testBox) {
        describe(title = "KinesisPut", body = function() {

            it(title = "puts a single struct record and returns sequenceNumber and shardId", body = function() {
                var rsp = kinesisPut(
                    data = variables.localstack.sampleRecord("put-single"),
                    partitionKey = "kinesis-put-single",
                    streamName = variables.cfg.streamName,
                    parallel = false,
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                expect(isStruct(rsp)).toBeTrue();
                expect(structKeyExists(rsp, "sequenceNumber")).toBeTrue();
                expect(structKeyExists(rsp, "shardId")).toBeTrue();
                expect(len(trim(rsp.sequenceNumber))).toBeGT(0);
                expect(len(trim(rsp.shardId))).toBeGT(0);
            });

            it(title = "puts an array of records in one call", body = function() {
                var rsp = kinesisPut(
                    data = [
                        variables.localstack.sampleRecord("put-batch-1"),
                        variables.localstack.sampleRecord("put-batch-2"),
                        variables.localstack.sampleRecord("put-batch-3")
                    ],
                    partitionKey = "kinesis-put-batch",
                    streamName = variables.cfg.streamName,
                    parallel = false,
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                expect(isStruct(rsp)).toBeTrue();
                expect(arrayLen(rsp.sequenceNumber)).toBe(3);
                expect(arrayLen(rsp.shardId)).toBe(3);
            });

            it(title = "puts asynchronously with parallel=true and listener", body = function() {
                var doneKey = "kinesis-put-async-" & createUUID();

                kinesisPut(
                    data = variables.localstack.sampleRecord("put-async"),
                    partitionKey = "kinesis-put-async",
                    streamName = variables.cfg.streamName,
                    parallel = true,
                    listener = {
                        onSuccess: function(result) {
                            application[doneKey] = { ok: true, result: result };
                        },
                        onError: function(error) {
                            application[doneKey] = { ok: false, error: error };
                        }
                    },
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                var waits = 400;
                while ((--waits) > 0) {
                    sleep(50);
                    if (structKeyExists(application, doneKey)) {
                        break;
                    }
                }

                expect(structKeyExists(application, doneKey)).toBeTrue(
                    "parallel kinesisPut listener did not fire in time"
                );
                var outcome = application[doneKey];
                structDelete(application, doneKey);
                expect(outcome.ok ?: false).toBeTrue();
                expect(structKeyExists(outcome.result, "sequenceNumber")).toBeTrue();
            });

            it(title = "queues 10 parallel=true puts and all listeners complete", body = function() {
                var completed = createObject("java", "java.util.concurrent.ConcurrentHashMap").init();
                var total = 10;

                for (var i = 1; i <= total; i++) {
                    kinesisPut(
                        data = variables.localstack.sampleRecord("put-async-batch"),
                        partitionKey = "kinesis-put-async-batch-" & i,
                        streamName = variables.cfg.streamName,
                        parallel = true,
                        listener = asyncMapListener(completed, toString(i)),
                        accessKeyId = variables.cfg.accessKeyId,
                        secretAccessKey = variables.cfg.secretAccessKey,
                        host = variables.cfg.host,
                        location = variables.cfg.region
                    );
                }

                var waits = 600;
                while ((--waits) > 0) {
                    if (completed.size() >= total) {
                        break;
                    }
                    sleep(50);
                }

                expect(completed.size()).toBe(
                    total,
                    "expected #total# async listeners; got #completed.size()#"
                );
                for (var i = 1; i <= total; i++) {
                    expect(completed.get(toString(i))).toBe("ok");
                }
            });

        });
    }

    /**
     * Listener writes completion into a ConcurrentHashMap (avoids application-scope races).
     */
    private struct function asyncMapListener(required any map, required string id) {
        var m = arguments.map;
        var putId = arguments.id;
        return {
            onSuccess: function(result) {
                m.put(putId, "ok");
            },
            onError: function(error) {
                m.put(putId, "err");
            }
        };
    }

}
