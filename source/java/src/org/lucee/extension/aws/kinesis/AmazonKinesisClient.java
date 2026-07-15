package org.lucee.extension.aws.kinesis;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lucee.extension.aws.kinesis.util.KinesisHttpPoolMonitor;
import org.lucee.extension.aws.kinesis.util.KinesisHttpPoolSettings;
import org.lucee.extension.aws.kinesis.util.KinesisProps;

import lucee.commons.io.log.Log;
import lucee.loader.util.Util;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.KinesisClientBuilder;

public class AmazonKinesisClient {

	private static Map<String, AmazonKinesisClient> pool = new ConcurrentHashMap<String, AmazonKinesisClient>();

	private KinesisClient client;
	private SdkHttpClient httpClient;
	private Log log;
	private long created;
	private long liveTimeout;

	private KinesisProps props;
	private KinesisHttpPoolSettings httpPool;

	public static KinesisClient get(KinesisProps props, long liveTimeout, Log log) {
		return get(props, liveTimeout, log, null);
	}

	public static KinesisClient get(KinesisProps props, long liveTimeout, Log log, KinesisHttpPoolSettings httpPool) {
		KinesisHttpPoolSettings poolSettings = httpPool == null ? KinesisHttpPoolSettings.fromEnv() : httpPool;
		String key = props == null ? "system" : props.getAccessKeyId() + ":" + props.getSecretAccessKey() + ":" + props.getHost() + ":" + props.getRegion();
		key = key + ":" + poolSettings.toCacheKey();

		AmazonKinesisClient client = pool.get(key);
		if (client == null || client.isExpired()) {
			synchronized (pool) {
				AmazonKinesisClient existing = pool.get(key);
				if (existing == null || existing.isExpired()) {
					if (existing != null) existing.shutdownQuietly();
					client = new AmazonKinesisClient(props, liveTimeout, log, poolSettings);
					pool.put(key, client);
					if (log != null) {
						log.log(Log.LEVEL_DEBUG, "Kinesis",
								"create client maxConnections=" + poolSettings.getEffectiveMaxConnections() + " keyHost="
										+ (props == null ? "system" : props.getHost()));
					}
				}
				else {
					client = existing;
				}
			}
		}
		return client.getAmazonKinesis();
	}

	private AmazonKinesisClient(KinesisProps props, long liveTimeout, Log log, KinesisHttpPoolSettings httpPool) {
		this.props = props;
		this.log = log;
		this.httpPool = httpPool == null ? KinesisHttpPoolSettings.fromEnv() : httpPool;
		this.created = System.currentTimeMillis();
		client = create();
		this.liveTimeout = liveTimeout;
	}

	public KinesisClient create() {
		KinesisClientBuilder builder = KinesisClient.builder();

		// credentials
		if (props != null) {
			AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey());
			builder.credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
		}

		// host
		if (props != null && !Util.isEmpty(props.getHost(), true)) {
			builder.endpointOverride(URI.create("http://" + props.getHost()));
		}

		// region
		builder.region(toRegion(props != null ? props.getRegion() : null, Region.US_EAST_1));

		// HTTP connection pool (AWS SDK v2 Apache client)
		ApacheHttpClient.Builder httpBuilder = ApacheHttpClient.builder();
		httpPool.apply(httpBuilder);
		httpClient = httpBuilder.build();
		builder.httpClient(httpClient);

		String label = props == null ? "system" : (props.getAccessKeyId() + ":...@" + props.getHost());
		builder.overrideConfiguration(ClientOverrideConfiguration.builder().addExecutionInterceptor(new KinesisHttpPoolMonitor(httpPool, log, label)).build());

		return builder.build();
	}

	private boolean isExpired() {
		return (liveTimeout + System.currentTimeMillis()) < created;
	}

	public KinesisClient getAmazonKinesis() {
		return client;
	}

	private void invalidateAmazonKinesis(IllegalStateException ise) throws KinesisException {
		if (log != null) log.error("Kinesis", ise);
		try {
			shutdownQuietly();
			client = create();
		}
		catch (Exception e) {
			if (log != null) log.error("Kinesis", e);
			throw new KinesisException("failed to invalidate client");
		}
	}

	public void release() {
		// FUTURE remove method
	}

	void shutdownQuietly() {
		try {
			if (client != null) client.close();
		}
		catch (Exception e) {
			if (log != null) log.error("Kinesis", e);
		}
		try {
			if (httpClient != null) httpClient.close();
		}
		catch (Exception e) {
			if (log != null) log.error("Kinesis", e);
		}
		client = null;
		httpClient = null;
	}

	public static Region toRegion(String region, Region defaultValue) {
		if (Util.isEmpty(region, true)) return defaultValue;
		region = region.trim();

		for (Region r: Region.regions()) {
			if (r.id().equalsIgnoreCase(region)) return r;
		}
		return defaultValue;
	}

}
