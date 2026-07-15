package org.lucee.extension.aws.kinesis.util;

import java.util.concurrent.atomic.AtomicInteger;

import lucee.commons.io.log.Log;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * Tracks in-flight Kinesis HTTP requests and logs when the connection pool is under pressure or exhausted.
 */
public final class KinesisHttpPoolMonitor implements ExecutionInterceptor {

	private static final long WARN_INTERVAL_MS = 60_000L;

	private final int maxConnections;
	private final double warnUtilization;
	private final Log log;
	private final String clientLabel;
	private final AtomicInteger inFlight = new AtomicInteger();
	private volatile long lastWarnAt;

	public KinesisHttpPoolMonitor(KinesisHttpPoolSettings pool, Log log, String clientLabel) {
		this.maxConnections = pool.getEffectiveMaxConnections();
		this.warnUtilization = pool.getWarnUtilization() == null ? KinesisHttpPoolSettings.DEFAULT_WARN_UTILIZATION : pool.getWarnUtilization().doubleValue();
		this.log = log;
		this.clientLabel = clientLabel;
	}

	@Override
	public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
		int current = inFlight.incrementAndGet();
		if (log == null || warnUtilization <= 0D) return;

		int warnAt = (int) Math.ceil(maxConnections * warnUtilization);
		if (warnAt < 1) warnAt = 1;
		if (current >= warnAt) {
			long now = System.currentTimeMillis();
			if (now - lastWarnAt >= WARN_INTERVAL_MS) {
				lastWarnAt = now;
				String msg = "Kinesis HTTP connection pool utilization high: " + current + "/" + maxConnections + " in-flight requests"
						+ " (warn threshold " + (int) (warnUtilization * 100) + "%) for client [" + clientLabel + "]";
				if (current >= maxConnections) {
					log.log(Log.LEVEL_ERROR, "Kinesis", msg + "; pool limit reached, further requests may block until a connection is released");
				}
				else {
					log.log(Log.LEVEL_WARN, "Kinesis", msg + "; consider raising [this.kinesis.pool.maxConnections] or LUCEE_KINESIS_POOL_MAXCONNECTIONS if this persists");
				}
			}
		}
	}

	@Override
	public void afterExecution(Context.AfterExecution context, ExecutionAttributes executionAttributes) {
		inFlight.decrementAndGet();
	}

	@Override
	public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes executionAttributes) {
		inFlight.decrementAndGet();
		if (log == null || context == null) return;
		Throwable e = context.exception();
		if (isPoolTimeout(e)) {
			log.log(Log.LEVEL_ERROR, "Kinesis",
					"Kinesis HTTP connection pool exhausted (timeout waiting for connection from pool) for client [" + clientLabel + "]; "
							+ "in-flight at failure: ~" + inFlight.get() + ", configured maxConnections: " + maxConnections
							+ ". Increase [this.kinesis.pool.maxConnections] / LUCEE_KINESIS_POOL_MAXCONNECTIONS or reduce concurrent PutRecord traffic. Cause: "
							+ e.getMessage());
		}
	}

	/**
	 * Detects Apache connection-pool timeouts without referencing
	 * {@code ConnectionPoolTimeoutException} by type — that class is not always
	 * visible to this OSGi bundle (NoClassDefFoundError on instanceof).
	 */
	public static boolean isPoolTimeout(Throwable e) {
		while (e != null) {
			String name = e.getClass().getName();
			if ("org.apache.http.conn.ConnectionPoolTimeoutException".equals(name)) return true;
			String msg = e.getMessage();
			if (msg != null && msg.indexOf("Timeout waiting for connection from pool") != -1) return true;
			if (msg != null && msg.indexOf("Acquire operation took longer than the configured maximum time") != -1) return true;
			e = e.getCause();
		}
		return false;
	}
}
