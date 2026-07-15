package org.lucee.extension.aws.kinesis.util;

import java.time.Duration;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.util.Util;
import lucee.runtime.type.Struct;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

/**
 * HTTP connection pool settings for the AWS SDK v2 Kinesis client (per credential/host/region).
 *
 * Mirrors the S3 extension pool configuration pattern (LDEV-6373).
 * Env / system properties use the lucee.kinesis.pool.* / LUCEE_KINESIS_POOL_* names.
 * Application.cfc: this.kinesis.pool = { maxConnections: 200, ... }
 *
 * connectionTimeout is applied as AWS SDK connectionAcquisitionTimeout (wait for a pool slot),
 * matching the S3 extension README semantics.
 */
public class KinesisHttpPoolSettings {

	/** AWS SDK Apache client default. */
	public static final int SDK_DEFAULT_MAX_CONNECTIONS = 50;
	/** Extension default when maxConnections is not configured (same as S3 extension). */
	public static final int DEFAULT_MAX_CONNECTIONS = 128;
	public static final double DEFAULT_WARN_UTILIZATION = 0.8D;

	private Integer maxConnections;
	private Integer connectionTimeout;
	private Integer socketTimeout;
	private Long connectionMaxIdleMillis;
	private Double warnUtilization = DEFAULT_WARN_UTILIZATION;

	public Integer getMaxConnections() {
		return maxConnections;
	}

	public void setMaxConnections(Integer maxConnections) {
		this.maxConnections = maxConnections;
	}

	public Integer getConnectionTimeout() {
		return connectionTimeout;
	}

	public void setConnectionTimeout(Integer connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public Integer getSocketTimeout() {
		return socketTimeout;
	}

	public void setSocketTimeout(Integer socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

	public Long getConnectionMaxIdleMillis() {
		return connectionMaxIdleMillis;
	}

	public void setConnectionMaxIdleMillis(Long connectionMaxIdleMillis) {
		this.connectionMaxIdleMillis = connectionMaxIdleMillis;
	}

	public Double getWarnUtilization() {
		return warnUtilization;
	}

	public void setWarnUtilization(Double warnUtilization) {
		this.warnUtilization = warnUtilization;
	}

	public int getEffectiveMaxConnections() {
		return maxConnections != null ? maxConnections.intValue() : DEFAULT_MAX_CONNECTIONS;
	}

	/**
	 * Pool-slot wait as a Duration (connectionTimeout field / env), or null when unset.
	 */
	public Duration getConnectionAcquisitionTimeoutDuration() {
		if (connectionTimeout == null) return null;
		return Duration.ofMillis(connectionTimeout.longValue());
	}

	/**
	 * Applies settings to an Apache HTTP client builder (sync KinesisClient transport).
	 */
	public ApacheHttpClient.Builder apply(ApacheHttpClient.Builder builder) {
		builder.maxConnections(Integer.valueOf(getEffectiveMaxConnections()));
		if (connectionTimeout != null) {
			builder.connectionAcquisitionTimeout(Duration.ofMillis(connectionTimeout.longValue()));
		}
		if (socketTimeout != null) {
			builder.socketTimeout(Duration.ofMillis(socketTimeout.longValue()));
		}
		if (connectionMaxIdleMillis != null) {
			builder.connectionMaxIdleTime(Duration.ofMillis(connectionMaxIdleMillis.longValue()));
		}
		return builder;
	}

	public String toCacheKey() {
		return (maxConnections == null ? "" : maxConnections) + ":" + (connectionTimeout == null ? "" : connectionTimeout) + ":" + (socketTimeout == null ? "" : socketTimeout)
				+ ":" + (connectionMaxIdleMillis == null ? "" : connectionMaxIdleMillis) + ":" + (warnUtilization == null ? "" : warnUtilization);
	}

	/**
	 * Env defaults, then optional Application.cfc this.kinesis.pool struct overlay.
	 */
	public static KinesisHttpPoolSettings load(CFMLEngine eng, Struct poolStruct) {
		KinesisHttpPoolSettings settings = fromEnv();
		if (poolStruct != null && eng != null) mergeStruct(settings, poolStruct, eng);
		return settings;
	}

	public static KinesisHttpPoolSettings fromEnv() {
		KinesisHttpPoolSettings settings = new KinesisHttpPoolSettings();
		settings.maxConnections = toInteger(CommonUtil.getSystemPropOrEnvVar("lucee.kinesis.pool.maxconnections", null));
		if (settings.maxConnections == null) settings.maxConnections = toInteger(CommonUtil.getSystemPropOrEnvVar("lucee.kinesis.maxconnections", null));

		settings.connectionTimeout = toInteger(CommonUtil.getSystemPropOrEnvVar("lucee.kinesis.pool.connectiontimeout", null));
		settings.socketTimeout = toInteger(CommonUtil.getSystemPropOrEnvVar("lucee.kinesis.pool.sockettimeout", null));
		settings.connectionMaxIdleMillis = toLong(CommonUtil.getSystemPropOrEnvVar("lucee.kinesis.pool.connectionmaxidlemillis", null));

		String warn = CommonUtil.getSystemPropOrEnvVar("lucee.kinesis.pool.warnutilization", null);
		if (!Util.isEmpty(warn, true)) settings.warnUtilization = toDouble(warn);

		return settings;
	}

	private static void mergeStruct(KinesisHttpPoolSettings settings, Struct pool, CFMLEngine eng) {
		setInt(settings, "maxConnections", pool, eng);
		setInt(settings, "connectionTimeout", pool, eng);
		setInt(settings, "socketTimeout", pool, eng);
		setLong(settings, "connectionMaxIdleMillis", pool, eng);
		setDouble(settings, "warnUtilization", pool, eng);
	}

	private static void setInt(KinesisHttpPoolSettings settings, String key, Struct sct, CFMLEngine eng) {
		Object raw = sct.get(key, null);
		if (raw == null) return;
		Integer v = toInteger(eng.getCastUtil().toString(raw, null));
		if (v == null) return;
		if ("maxConnections".equals(key)) settings.setMaxConnections(v);
		else if ("connectionTimeout".equals(key)) settings.setConnectionTimeout(v);
		else if ("socketTimeout".equals(key)) settings.setSocketTimeout(v);
	}

	private static void setLong(KinesisHttpPoolSettings settings, String key, Struct sct, CFMLEngine eng) {
		Object raw = sct.get(key, null);
		if (raw == null) return;
		Long v = toLong(eng.getCastUtil().toString(raw, null));
		if (v != null) settings.setConnectionMaxIdleMillis(v);
	}

	private static void setDouble(KinesisHttpPoolSettings settings, String key, Struct sct, CFMLEngine eng) {
		Object raw = sct.get(key, null);
		if (raw == null) return;
		Double v = toDouble(eng.getCastUtil().toString(raw, null));
		if (v != null) settings.setWarnUtilization(v);
	}

	private static Integer toInteger(String str) {
		if (Util.isEmpty(str, true)) return null;
		try {
			return Integer.valueOf(str.trim());
		}
		catch (Exception e) {
			return null;
		}
	}

	private static Long toLong(String str) {
		if (Util.isEmpty(str, true)) return null;
		try {
			return Long.valueOf(str.trim());
		}
		catch (Exception e) {
			return null;
		}
	}

	private static Double toDouble(String str) {
		if (Util.isEmpty(str, true)) return null;
		try {
			return Double.valueOf(str.trim());
		}
		catch (Exception e) {
			return null;
		}
	}
}
