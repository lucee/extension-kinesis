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

        });
    }

}
