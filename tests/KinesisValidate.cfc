/**
 * Functional coverage for KinesisValidate.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="kinesis" {

    variables.localstack = "";
    variables.cfg = {};

    function beforeAll() {
        variables.localstack = new KinesisLocalStack();
        variables.cfg = variables.localstack.requireReady("KinesisValidate");
    }

    function run(testResults, testBox) {
        describe(title = "KinesisValidate", body = function() {

            it(title = "succeeds against LocalStack with configured credentials", body = function() {
                KinesisValidate(
                    accessKeyId = variables.cfg.accessKeyId,
                    secretAccessKey = variables.cfg.secretAccessKey,
                    host = variables.cfg.host,
                    location = variables.cfg.region
                );
                expect(true).toBeTrue();
            });

            it(title = "throws when host is unreachable", body = function() {
                expect(function() {
                    KinesisValidate(
                        accessKeyId = variables.cfg.accessKeyId,
                        secretAccessKey = variables.cfg.secretAccessKey,
                        host = "127.0.0.1:1",
                        location = variables.cfg.region,
                        timeout = 1000
                    );
                }).toThrow();
            });

        });
    }

}
