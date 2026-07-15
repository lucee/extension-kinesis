/**
 * CFML coverage for Kinesis HTTP pool settings (no AWS required).
 *
 * Verifies env / defaults used by AmazonKinesisClient so CI can exercise the
 * configurable pool without a live Kinesis endpoint.
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="kinesis" {

    variables.bundleName = "org.lucee.kinesis.extension";
    variables.settingsClass = "org.lucee.extension.aws.kinesis.util.KinesisHttpPoolSettings";
    variables.monitorClass = "org.lucee.extension.aws.kinesis.util.KinesisHttpPoolMonitor";

    function beforeAll() {
        try {
            createObject("java", variables.settingsClass, variables.bundleName);
        }
        catch (any e) {
            variables.bundleName = "kinesis.extension";
            createObject("java", variables.settingsClass, variables.bundleName);
        }
    }

    function afterEach() {
        clearPoolSystemProperties();
    }

    function run(testResults, testBox) {
        describe(title = "KinesisHttpPoolSettings (CFML / Java bridge)", body = function() {

            it(title = "effective maxConnections defaults to 128 (above AWS SDK default of 50)", body = function() {
                clearPoolSystemProperties();
                var settings = newSettings().fromEnv();
                expect(settings.getEffectiveMaxConnections()).toBe(128);
                expect(settings.getEffectiveMaxConnections()).toBeGT(50);
            });

            it(title = "fromEnv reads lucee.kinesis.pool.maxconnections", body = function() {
                clearPoolSystemProperties();
                createObject("java", "java.lang.System").setProperty("lucee.kinesis.pool.maxconnections", "200");
                var settings = newSettings().fromEnv();
                expect(settings.getEffectiveMaxConnections()).toBe(200);
            });

            it(title = "fromEnv reads lucee.kinesis.pool.connectiontimeout (pool acquisition wait)", body = function() {
                clearPoolSystemProperties();
                createObject("java", "java.lang.System").setProperty("lucee.kinesis.pool.connectiontimeout", "5000");
                var settings = newSettings().fromEnv();
                expect(settings.getConnectionTimeout()).toBe(5000);
                expect(settings.getConnectionAcquisitionTimeoutDuration().toMillis()).toBe(5000);
            });

            it(title = "toCacheKey changes when maxConnections changes", body = function() {
                var a = newSettings();
                a.setMaxConnections(50);
                var b = newSettings();
                b.setMaxConnections(200);
                expect(a.toCacheKey()).notToBe(b.toCacheKey());
            });

            it(title = "apply configures ApacheHttpClient builder without error", body = function() {
                var settings = newSettings();
                settings.setMaxConnections(175);
                settings.setConnectionTimeout(7500);
                var apacheHttpClient = createObject(
                    "java",
                    "software.amazon.awssdk.http.apache.ApacheHttpClient",
                    variables.bundleName
                );
                var applied = settings.apply(apacheHttpClient.builder());
                expect(isNull(applied)).toBeFalse();
                expect(settings.getEffectiveMaxConnections()).toBe(175);
            });

            it(title = "KinesisHttpPoolMonitor.isPoolTimeout detects pool timeout message", body = function() {
                var monitor = createObject("java", variables.monitorClass, variables.bundleName);
                var withMessage = createObject("java", "java.lang.RuntimeException").init(
                    "Timeout waiting for connection from pool"
                );
                expect(monitor.isPoolTimeout(withMessage)).toBeTrue();
                expect(monitor.isPoolTimeout(createObject("java", "java.lang.RuntimeException").init("unrelated"))).toBeFalse();
            });
        });
    }

    private any function newSettings() {
        return createObject("java", variables.settingsClass, variables.bundleName);
    }

    private void function clearPoolSystemProperties() {
        var sys = createObject("java", "java.lang.System");
        sys.clearProperty("lucee.kinesis.pool.maxconnections");
        sys.clearProperty("lucee.kinesis.pool.connectiontimeout");
        sys.clearProperty("lucee.kinesis.pool.sockettimeout");
        sys.clearProperty("lucee.kinesis.pool.connectionmaxidlemillis");
        sys.clearProperty("lucee.kinesis.pool.warnutilization");
        sys.clearProperty("lucee.kinesis.maxconnections");
    }

}
