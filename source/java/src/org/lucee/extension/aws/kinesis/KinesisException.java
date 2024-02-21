package org.lucee.extension.aws.kinesis;

import java.io.IOException;

public class KinesisException extends IOException {

	private static final long serialVersionUID = 1785247900379572030L;
	private String ec;
	private long proposedSize;

	public KinesisException(String message) {
		super(message);
	}

	public KinesisException(String message, Throwable t) {
		super(message, t);
	}

	public void setErrorCode(String ec) {
		this.ec = ec;
	}

	public String getErrorCode() {
		return ec;
	}

	public void setProposedSize(long proposedSize) {
		this.proposedSize = proposedSize;
	}

	public long getProposedSize() {
		return proposedSize;
	}
}