/**
 * Functional coverage for KinesisInfo.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="kinesis" {

    variables.localstack = "";
    variables.cfg = {};

    function beforeAll() {
        variables.localstack = new KinesisLocalStack();
        variables.cfg = variables.localstack.requireReady("KinesisInfo");
    }

    function run(testResults, testBox) {
        describe(title = "KinesisInfo", body = function() {

            it(title = "describes the configured stream with at least one shard", body = function() {
                var info = kinesisInfo(
                    streamName = variables.cfg.streamName,
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                expect(isStruct(info)).toBeTrue();
                expect(structKeyExists(info, "shards")).toBeTrue();
                expect(info.shards.recordCount).toBeGT(0);
                expect(len(trim(info.shards.shardId[1]))).toBeGT(0);
            });

            it(title = "lists streams when streamName is omitted and includes the test stream", body = function() {
                var all = kinesisInfo(
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                expect(isStruct(all)).toBeTrue();
                expect(structKeyExists(all, variables.cfg.streamName)).toBeTrue();
                expect(all[variables.cfg.streamName].shards.recordCount).toBeGT(0);
            });

            it(title = "throws for an unknown stream name", body = function() {
                expect(function() {
                    kinesisInfo(
                        streamName = "no-such-kinesis-stream-" & createUUID(),
                        accessKeyId = variables.cfg.accessKeyId,
                        secretAccessKey = variables.cfg.secretAccessKey,
                        host = variables.cfg.host,
                        location = variables.cfg.region
                    );
                }).toThrow();
            });

        });
    }

}
