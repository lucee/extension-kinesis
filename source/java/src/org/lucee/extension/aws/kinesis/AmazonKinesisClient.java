package org.lucee.extension.aws.kinesis;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lucee.extension.aws.kinesis.util.KinesisProps;

import lucee.commons.io.log.Log;
import lucee.loader.util.Util;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.KinesisClientBuilder;

public class AmazonKinesisClient {

	private static Map<String, AmazonKinesisClient> pool = new ConcurrentHashMap<String, AmazonKinesisClient>();

	private KinesisClient client;
	private Log log;
	private long created;
	private long liveTimeout;

	private KinesisProps props;

	public static KinesisClient get(KinesisProps props, long liveTimeout, Log log) {

		String key = props == null ? "system" : props.getAccessKeyId() + ":" + props.getSecretAccessKey() + ":" + props.getHost() + ":" + props.getRegion();
		AmazonKinesisClient client = pool.get(key);
		if (client == null || client.isExpired()) {
			client = new AmazonKinesisClient(props, liveTimeout, log);
			pool.put(key, client);
		}
		return client.getAmazonKinesis();
	}

	private AmazonKinesisClient(KinesisProps props, long liveTimeout, Log log) {
		this.props = props;
		this.log = log;
		this.created = System.currentTimeMillis();
		client = create();
		this.liveTimeout = liveTimeout;
	}

	public KinesisClient create() {

		KinesisClientBuilder builder = KinesisClient.builder();

		// has credentilas
		if (props != null) {
			AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(props.getAccessKeyId(), props.getSecretAccessKey());
			builder.credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
		}

		// host
		if (props != null && !Util.isEmpty(props.getHost(), true)) {
			builder.endpointOverride(URI.create("http://" + props.getHost()));
		}

		// region
		builder.region(toRegion(props != null ? props.getRegion() : null, Region.US_EAST_1)); // TODO is this best?

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

	public static Region toRegion(String region, Region defaultValue) {
		if (Util.isEmpty(region, true)) return defaultValue;
		region = region.trim();

		for (Region r: Region.regions()) {
			if (r.id().equalsIgnoreCase(region)) return r;
		}
		return defaultValue;
	}

}
