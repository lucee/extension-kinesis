/**
 * Shared LocalStack / LUCEE_KINESIS_* setup for integration specs.
 * Extends LuceeTestCase without labels so testLabels=kinesis does not run it as a suite.
 * Callers: new KinesisLocalStack().requireReady().
 */
component extends="org.lucee.cfml.test.LuceeTestCase" {

    function run(testResults, testBox) {
        // helper component — intentional no specs
    }


    /**
     * Resolve env, apply application.kinesis, validate endpoint, require stream.
     *
     * @return struct: accessKeyId, secretAccessKey, host, region, streamName
     */
    public struct function requireReady(string logLabel = "KinesisLocalStack") {
        var cfg = {
            accessKeyId: envOr("LUCEE_KINESIS_ACCESSKEYID", envOr("AWS_ACCESS_KEY_ID", "test")),
            secretAccessKey: envOr("LUCEE_KINESIS_SECRETACCESSKEY", envOr("AWS_SECRET_ACCESS_KEY", "test")),
            host: envOr("LUCEE_KINESIS_HOST", ""),
            region: envOr("LUCEE_KINESIS_REGION", "us-east-1"),
            streamName: envOr("LUCEE_KINESIS_TEST_STREAM", "kinesis-pool-test")
        };

        if (!len(cfg.host)) {
            throw(
                type = "org.lucee.extension.aws.kinesis.test.MissingKinesisHost",
                message = "LUCEE_KINESIS_HOST is required (e.g. localhost:4566 for LocalStack)."
            );
        }

        application action="update" kinesis={
            accessKeyId: cfg.accessKeyId,
            secretAccessKey: cfg.secretAccessKey,
            host: cfg.host,
            region: cfg.region,
            pool: {
                maxConnections: 128,
                connectionTimeout: 10000
            }
        };

        try {
            KinesisValidate(
                accessKeyId = cfg.accessKeyId,
                secretAccessKey = cfg.secretAccessKey,
                host = cfg.host,
                location = cfg.region
            );
        }
        catch (any e) {
            throw(
                type = "org.lucee.extension.aws.kinesis.test.KinesisUnavailable",
                message = "KinesisValidate failed (#arguments.logLabel#, host=#cfg.host#). "
                    & (e.message ?: e.toString()),
                detail = e.detail ?: ""
            );
        }

        if (!streamExists(cfg, cfg.streamName)) {
            throw(
                type = "org.lucee.extension.aws.kinesis.test.MissingKinesisStream",
                message = "Required stream '#cfg.streamName#' not found on #cfg.host#. "
                    & "Create it in CI (KINESIS_INITIALIZE_STREAMS / aws create-stream) or set LUCEE_KINESIS_TEST_STREAM."
            );
        }

        systemOutput(
            arguments.logLabel & ": host=" & cfg.host
                & " stream=" & cfg.streamName
                & " region=" & cfg.region,
            true
        );

        return cfg;
    }

    /**
     * Named credential/endpoint args shared by Kinesis* BIFs.
     */
    public struct function endpointArgs(required struct cfg) {
        return {
            accessKeyId: arguments.cfg.accessKeyId,
            secretAccessKey: arguments.cfg.secretAccessKey,
            host: arguments.cfg.host,
            location: arguments.cfg.region
        };
    }

    public boolean function streamExists(required struct cfg, required string streamName) {
        try {
            kinesisInfo(
                streamName = arguments.streamName,
                accessKeyId = arguments.cfg.accessKeyId,
                secretAccessKey = arguments.cfg.secretAccessKey,
                host = arguments.cfg.host,
                location = arguments.cfg.region
            );
            return true;
        }
        catch (any e) {
            return false;
        }
    }

    public struct function sampleRecord(string action = "kinesis-function-test") {
        return {
            "source": "kinesis-extension-test",
            "type": "function-test",
            "detail": {
                "metadata": {
                    "key": createUUID(),
                    "ts": dateTimeFormat(now(), "iso8601"),
                    "datehour": dateTimeFormat(now(), "yyyy-mm-dd HH:nn:ss")
                },
                "data": {
                    "action": arguments.action,
                    "notes": "extension function coverage"
                }
            }
        };
    }

    public string function envOr(required string name, required string defaultValue) {
        var env = server.system.environment ?: {};
        if (structKeyExists(env, arguments.name) && len(trim(env[arguments.name]))) {
            return trim(env[arguments.name]);
        }
        var prop = createObject("java", "java.lang.System").getProperty(arguments.name);
        if (!isNull(prop) && len(trim(prop))) {
            return trim(prop);
        }
        return arguments.defaultValue;
    }

}
