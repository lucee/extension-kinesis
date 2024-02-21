package org.lucee.extension.aws.kinesis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

	private String accessKeyId;

	private String secretAccessKey;

	private String host;

	private String region;

	public static KinesisClient get(String accessKeyId, String secretAccessKey, String host, String region, long liveTimeout, Log log) {
		String key = accessKeyId + ":" + secretAccessKey + ":" + host + ":" + region;
		AmazonKinesisClient client = pool.get(key);
		if (client == null || client.isExpired()) {
			client = new AmazonKinesisClient(accessKeyId, secretAccessKey, host, region, liveTimeout, log);
			pool.put(key, client);
		}
		return client.getAmazonKinesis();
	}

	private AmazonKinesisClient(String accessKeyId, String secretAccessKey, String host, String region, long liveTimeout, Log log) {
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
		this.host = host;
		this.region = region;
		this.log = log;
		this.created = System.currentTimeMillis();
		client = create();
		this.liveTimeout = liveTimeout;
	}

	public KinesisClient create() {

		KinesisClientBuilder builder = KinesisClient.builder();

		// has credentilas
		if (!Util.isEmpty(accessKeyId, true)) {
			AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
			builder.credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
		}
		// TODO other settings needed?
		builder.region(Region.US_EAST_1); // TODO is this best?

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

}
