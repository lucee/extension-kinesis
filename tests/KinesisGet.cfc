/**
 * Functional coverage for KinesisGet (put then read back via LocalStack).
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="kinesis" {

    variables.localstack = "";
    variables.cfg = {};

    function beforeAll() {
        variables.localstack = new KinesisLocalStack();
        variables.cfg = variables.localstack.requireReady("KinesisGet");
    }

    function run(testResults, testBox) {
        describe(title = "KinesisGet", body = function() {

            it(title = "reads the put record with AT_SEQUENCE_NUMBER", body = function() {
                var marker = createUUID();
                var putRsp = putRecord(marker);

                var rows = kinesisGet(
                    streamName = variables.cfg.streamName,
                    shardId = putRsp.shardId,
                    sequenceNumber = putRsp.sequenceNumber,
                    iteratorType = "AT_SEQUENCE_NUMBER",
                    maxrows = 1,
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                expect(isQuery(rows)).toBeTrue();
                expect(rows.recordCount).toBeGT(0);
                expect(rows.sequenceNumber[1]).toBe(putRsp.sequenceNumber);
                expect(rows.partitionKey[1]).toBe("kinesis-get-" & marker);
            });

            it(title = "reads later records with AFTER_SEQUENCE_NUMBER", body = function() {
                var marker = createUUID();
                var first = putRecord(marker & "-a");
                putRecord(marker & "-b");
                putRecord(marker & "-c");

                var rows = kinesisGet(
                    streamName = variables.cfg.streamName,
                    shardId = first.shardId,
                    sequenceNumber = first.sequenceNumber,
                    iteratorType = "AFTER_SEQUENCE_NUMBER",
                    maxrows = 10,
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                expect(isQuery(rows)).toBeTrue();
                expect(rows.recordCount).toBeGTE(2);
            });

            it(title = "reads with AT_TIMESTAMP on an explicit shard", body = function() {
                var marker = createUUID();
                var putRsp = putRecord(marker);

                var rows = kinesisGet(
                    streamName = variables.cfg.streamName,
                    shardId = putRsp.shardId,
                    timestamp = dateAdd("n", -5, now()),
                    iteratorType = "AT_TIMESTAMP",
                    maxrows = 5,
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                expect(isQuery(rows)).toBeTrue();
                expect(rows.recordCount).toBeGT(0);
            });

            it(title = "reads with implicit shardId (TRIM_HORIZON on latest shard)", body = function() {
                putRecord(createUUID());

                var rows = kinesisGet(
                    streamName = variables.cfg.streamName,
                    maxrows = 1,
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );

                expect(isQuery(rows)).toBeTrue();
                expect(rows.recordCount).toBe(1);
            });

            it(title = "rejects invalid iteratorType", body = function() {
                expect(function() {
                    kinesisGet(
                        streamName = variables.cfg.streamName,
                        iteratorType = "NOT_A_REAL_ITERATOR",
                        accessKeyId = variables.cfg.accessKeyId,
                        secretAccessKey = variables.cfg.secretAccessKey,
                        host = variables.cfg.host,
                        location = variables.cfg.region
                    );
                }).toThrow();
            });

        });
    }

    private struct function putRecord(required string marker) {
        return kinesisPut(
            data = variables.localstack.sampleRecord("get-" & arguments.marker),
            partitionKey = "kinesis-get-" & arguments.marker,
            streamName = variables.cfg.streamName,
            parallel = false,
            accessKeyId = variables.cfg.accessKeyId,
            secretAccessKey = variables.cfg.secretAccessKey,
            host = variables.cfg.host,
            location = variables.cfg.region
        );
    }

}
