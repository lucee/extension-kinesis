/**
 * Isolated tests for GetRecords shard logging and client-side throttle (no AWS).
 */
component extends="org.lucee.cfml.test.LuceeTestCase" labels="kinesis" {

    variables.bundleName = "org.lucee.kinesis.extension";
    variables.guardClass = "org.lucee.extension.aws.kinesis.util.KinesisGetRecordsGuard";

    function beforeAll() {
        try {
            createObject("java", variables.guardClass, variables.bundleName);
        }
        catch (any e) {
            variables.bundleName = "kinesis.extension";
            createObject("java", variables.guardClass, variables.bundleName);
        }
    }

    function afterEach() {
        guard().reset();
    }

    function run(testResults, testBox) {
        describe(title = "KinesisGetRecordsGuard", body = function() {

            it(title = "min interval is 200ms (5 GetRecords per second per shard)", body = function() {
                expect(guard().getMinIntervalMs()).toBe(200);
            });

            it(title = "computeWaitMs is 0 when this shard has never been called", body = function() {
                expect(guard().computeWaitMs(0, 1000)).toBe(0);
            });

            it(title = "computeWaitMs is full interval when the last call was this millisecond", body = function() {
                expect(guard().computeWaitMs(1000, 1000)).toBe(200);
            });

            it(title = "computeWaitMs is remaining interval when called 150ms after the last GetRecords", body = function() {
                expect(guard().computeWaitMs(1000, 1150)).toBe(50);
            });

            it(title = "computeWaitMs is 0 when called after the min interval", body = function() {
                expect(guard().computeWaitMs(1000, 1200)).toBe(0);
                expect(guard().computeWaitMs(1000, 1500)).toBe(0);
            });

            it(title = "shardKey includes stream and shard", body = function() {
                expect(guard().shardKey("kds-dk-logs-staging", "shardId-000000000003"))
                    .toBe("kds-dk-logs-staging:shardId-000000000003");
            });

            it(title = "formatLog includes shardId and timestamp", body = function() {
                var line = guard().formatLog(
                    "kds-dk-logs-staging",
                    "shardId-000000000003",
                    50,
                    "2026-09-02T08:00:00.123Z"
                );
                expect(line).toInclude("GetRecords");
                expect(line).toInclude("shardId=shardId-000000000003");
                expect(line).toInclude("ts=2026-09-02T08:00:00.123Z");
                expect(line).toInclude("waitMs=50");
                expect(line).toInclude("stream=kds-dk-logs-staging");
            });

            it(title = "isThroughputExceeded detects Rate exceeded messages", body = function() {
                var e = createObject("java", "java.lang.RuntimeException").init(
                    "Rate exceeded for Shard - 120849107321/kds-dk-logs-staging/shardId-000000000003"
                );
                expect(guard().isThroughputExceeded(e)).toBeTrue();
                expect(guard().isThroughputExceeded(createObject("java", "java.lang.RuntimeException").init("unrelated"))).toBeFalse();
            });

            it(title = "backoffMs grows with the attempt number", body = function() {
                expect(guard().backoffMs(1)).toBe(200);
                expect(guard().backoffMs(2)).toBe(400);
                expect(guard().backoffMs(3)).toBe(800);
            });
        });
    }

    private any function guard() {
        return createObject("java", variables.guardClass, variables.bundleName);
    }

}
