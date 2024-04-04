package org.lucee.extension.aws.kinesis.util;

public class KinesisProps {

	private String secretAccessKey;
	private String accessKeyId;
	private String host;
	private String region;

	public void setSecretAccessKey(String secretAccessKey) {
		this.secretAccessKey = secretAccessKey;
	}

	public void setAccessKeyId(String accessKeyId) {
		this.accessKeyId = accessKeyId;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getSecretAccessKey() {
		return secretAccessKey;
	}

	public String getAccessKeyId() {
		return accessKeyId;
	}

	public String getHost() {
		return host;
	}

	public String getRegion() {
		return region;
	}

}
