package org.lucee.extension.aws.kinesis.function;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.lucee.extension.aws.kinesis.AmazonKinesisClient;
import org.lucee.extension.aws.kinesis.util.Functions;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Query;
import lucee.runtime.util.Cast;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;

public class KinesisGet extends KinesisFunction {

	private static final long serialVersionUID = 5961249624495798999L;
	private static final int RECORD_LIMIT = 100;
	public static final Charset UTF_8 = Charset.forName("UTF-8");

	public static Query call(PageContext pc, String streamName, String shardId, String iteratorType, String accessKeyId, String secretAccessKey, String host, String location,
			double timeout) throws PageException {

		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Log log = pc.getConfig().getLog("application");
		try {
			KinesisClient client = AmazonKinesisClient.get(accessKeyId, secretAccessKey, host, location, toTimeout(timeout), log);

			// validate location
			if (eng.getStringUtil().isEmpty(location, true)) location = null;

			// validate iteratorType
			if (!Util.isEmpty(iteratorType, true)) {
				iteratorType = iteratorType.trim();
				if ("TRIM_HORIZON".equalsIgnoreCase(iteratorType)) iteratorType = "TRIM_HORIZON";
				else if ("LATEST".equalsIgnoreCase(iteratorType)) iteratorType = "LATEST";
				else if ("AT_SEQUENCE_NUMBER".equalsIgnoreCase(iteratorType)) iteratorType = "AT_SEQUENCE_NUMBER";
				else if ("AFTER_SEQUENCE_NUMBER".equalsIgnoreCase(iteratorType)) iteratorType = "AFTER_SEQUENCE_NUMBER";
				else if ("AT_TIMESTAMP".equalsIgnoreCase(iteratorType)) iteratorType = "AT_TIMESTAMP";
				else throw eng.getExceptionUtil().createFunctionException(pc, "KinesisGet", 3, "iteratorType",
						"invalid iteratorType [" + iteratorType + "], valid types are [TRIM_HORIZON,LATEST,AT_SEQUENCE_NUMBER,AFTER_SEQUENCE_NUMBER,AT_TIMESTAMP]", null);
			}
			else {
				iteratorType = "TRIM_HORIZON";
			}

			// validate shardId
			String shardIterator;
			if (Util.isEmpty(shardId, true)) {
				shardIterator = getLatestShardIterator(client, streamName);
			}
			else {
				GetShardIteratorRequest shardIteratorRequest = GetShardIteratorRequest.builder().streamName(streamName.trim()).shardId(shardId.trim())
						.shardIteratorType(iteratorType).build();
				GetShardIteratorResponse shardIteratorResponse = client.getShardIterator(shardIteratorRequest);
				shardIterator = shardIteratorResponse.shardIterator();
			}

			// result query
			Query result = eng.getCreationUtil().createQuery(new String[] { "encryptionType", "partitionKey", "sequenceNumber", "approximateArrival", "data", "raw" }, 0, "result");

			int count = 10;// temporary limit for testing to avoid infiniti loop
			boolean moreRecordsAvailable = true;
			Record r;
			int row;
			while (moreRecordsAvailable) {
				if ((count--) <= 0) break;
				GetRecordsResponse rsp = client.getRecords(GetRecordsRequest.builder().shardIterator(shardIterator).limit(RECORD_LIMIT).build());
				List<Record> records = rsp.records();
				Iterator<Record> it = records.iterator();
				while (it.hasNext()) {
					r = it.next();
					row = result.addRow();
					result.setAtEL("encryptionType", row, r.encryptionTypeAsString());
					result.setAtEL("partitionKey", row, r.partitionKey());
					result.setAtEL("sequenceNumber", row, r.sequenceNumber());
					result.setAtEL("approximateArrival", row, r.approximateArrivalTimestamp().toString());
					result.setAtEL("raw", row, r);
					result.setAtEL("data", row, Functions.deserializeJSON(r.data().asString(UTF_8)));
				}

				if (records.size() < RECORD_LIMIT) {
					// If less than RECORD_LIMIT records were returned, there are no more records to fetch
					moreRecordsAvailable = false;
				}
				else {
					// Update the shardIterator for the next iteration
					shardIterator = records.get(records.size() - 1).sequenceNumber();
				}
			}
			return result;
		}
		catch (Exception e) {
			throw eng.getCastUtil().toPageException(e);
		}
	}

	private static String getLatestShardIterator(KinesisClient kinesisClient, String streamName) {
		String lastShardId = null;
		DescribeStreamRequest describeStreamRequest = DescribeStreamRequest.builder().streamName(streamName).build();

		List<Shard> shards = new ArrayList<>();
		DescribeStreamResponse streamRes;
		do {
			streamRes = kinesisClient.describeStream(describeStreamRequest);
			shards.addAll(streamRes.streamDescription().shards());

			if (shards.size() > 0) {
				lastShardId = shards.get(shards.size() - 1).shardId();
			}
		}
		while (streamRes.streamDescription().hasMoreShards());

		GetShardIteratorRequest itReq = GetShardIteratorRequest.builder().streamName(streamName).shardIteratorType("TRIM_HORIZON").shardId(lastShardId).build();

		GetShardIteratorResponse shardIteratorResult = kinesisClient.getShardIterator(itReq);
		return shardIteratorResult.shardIterator();
	}

	@Override
	public Object invoke(PageContext pc, Object[] args) throws PageException {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Cast cast = engine.getCastUtil();

		if (args.length < 1 || args.length > 8) throw engine.getExceptionUtil().createFunctionException(pc, "KinesisGet", 1, 8, args.length);

		// streamName
		String streamName = cast.toString(args[0]);
		if (Util.isEmpty(streamName, true))
			throw engine.getExceptionUtil().createFunctionException(pc, "KinesisGet", 1, "streamName", "invalid streamName [" + streamName + "],value cannot be empty", null);
		else streamName = streamName.trim();

		// shardId
		String shardId = null;
		if (args.length > 1) {
			String tmp = cast.toString(args[1]);
			if (!Util.isEmpty(tmp, true)) shardId = tmp.trim();
			else shardId = null;
		}

		// iteratorType
		String iteratorType = null;
		if (args.length > 2) {
			String tmp = cast.toString(args[2]);
			if (!Util.isEmpty(tmp, true)) iteratorType = tmp.trim();
			else iteratorType = null;
		}

		// accessKeyId
		String accessKeyId = null;
		if (args.length > 3) {
			String tmp = cast.toString(args[3]);
			if (!Util.isEmpty(tmp, true)) accessKeyId = tmp.trim();
			else accessKeyId = null;
		}

		// secretAccessKey
		String secretAccessKey = null;
		if (args.length > 4) {
			String tmp = cast.toString(args[4]);
			if (!Util.isEmpty(tmp, true)) secretAccessKey = tmp.trim();
			else secretAccessKey = null;
		}

		// host
		String host = null;
		if (args.length > 5) {
			String tmp = cast.toString(args[5]);
			if (!Util.isEmpty(tmp, true)) host = tmp.trim();
			else host = null;
		}

		// location
		String location = null;
		if (args.length > 6) {
			String tmp = cast.toString(args[6]);
			if (!Util.isEmpty(tmp, true)) location = tmp.trim();
			else location = null;
		}

		// timeout
		double timeout = 0;
		if (args.length > 7) {
			double tmp = cast.toDoubleValue(args[7]);
			if (tmp > 0) timeout = tmp;
			else timeout = 0;
		}

		return call(pc, streamName, shardId, iteratorType, accessKeyId, secretAccessKey, host, location, timeout);

	}
}