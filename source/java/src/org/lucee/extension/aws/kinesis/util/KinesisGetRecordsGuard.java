package org.lucee.extension.aws.kinesis.util;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lucee.commons.io.log.Log;

/**
 * Client-side GetRecords pacing for the Kinesis 5 TPS per-shard quota.
 * Logs stream, shardId, and timestamp on each GetRecords attempt.
 */
public final class KinesisGetRecordsGuard {

	public static final int MAX_GET_RECORDS_PER_SECOND = 5;
	public static final long MIN_INTERVAL_MS = 1000L / MAX_GET_RECORDS_PER_SECOND;
	public static final int MAX_THROTTLE_RETRIES = 3;

	private static final ConcurrentHashMap<String, AtomicLong> lastCallAt = new ConcurrentHashMap<String, AtomicLong>();

	private KinesisGetRecordsGuard() {
	}

	public static long getMinIntervalMs() {
		return MIN_INTERVAL_MS;
	}

	public static String shardKey(String streamName, String shardId) {
		String stream = streamName == null ? "" : streamName;
		String shard = shardId == null || shardId.length() == 0 ? "(unspecified)" : shardId;
		return stream + ":" + shard;
	}

	public static long computeWaitMs(long lastCallAtMs, long nowMs) {
		if (lastCallAtMs <= 0L) return 0L;
		long elapsed = nowMs - lastCallAtMs;
		if (elapsed >= MIN_INTERVAL_MS) return 0L;
		return MIN_INTERVAL_MS - elapsed;
	}

	public static String formatLog(String streamName, String shardId, long waitMs, String timestampIso) {
		return "GetRecords stream=" + streamName + " shardId=" + shardId + " ts=" + timestampIso + " waitMs=" + waitMs;
	}

	public static boolean isThroughputExceeded(Throwable e) {
		while (e != null) {
			String name = e.getClass().getName();
			if (name != null && name.indexOf("ProvisionedThroughputExceeded") != -1) return true;
			String msg = e.getMessage();
			if (msg != null && msg.indexOf("Rate exceeded for Shard") != -1) return true;
			e = e.getCause();
		}
		return false;
	}

	public static long backoffMs(int attempt) {
		if (attempt < 1) attempt = 1;
		long wait = MIN_INTERVAL_MS;
		for (int i = 1; i < attempt; i++) {
			wait = wait * 2L;
		}
		if (wait > 5000L) wait = 5000L;
		return wait;
	}

	/**
	 * Sleeps if this JVM already called GetRecords on the same shard too recently.
	 * Returns waitMs actually applied (0 if no wait).
	 */
	public static long throttle(String streamName, String shardId, Log log) {
		String key = shardKey(streamName, shardId);
		long now = System.currentTimeMillis();
		AtomicLong last = lastCallAt.computeIfAbsent(key, k -> new AtomicLong(0L));
		long waitMs = 0L;
		synchronized (last) {
			waitMs = computeWaitMs(last.get(), now);
			if (waitMs > 0L) {
				try {
					Thread.sleep(waitMs);
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
				now = System.currentTimeMillis();
			}
			last.set(now);
		}
		if (log != null) {
			int level = waitMs > 0L ? Log.LEVEL_WARN : Log.LEVEL_INFO;
			log.log(level, "Kinesis", formatLog(streamName, shardId, waitMs, Instant.ofEpochMilli(now).toString()));
		}
		return waitMs;
	}

	public static void reset() {
		lastCallAt.clear();
	}
}
