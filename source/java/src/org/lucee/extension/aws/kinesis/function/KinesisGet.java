package org.lucee.extension.aws.kinesis.function;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
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
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Query;
import lucee.runtime.type.dt.DateTime;
import lucee.runtime.util.Cast;
import lucee.runtime.util.Creation;
import lucee.runtime.util.Decision;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest.Builder;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;

public class KinesisGet extends KinesisFunction {

	private static final long serialVersionUID = 5961249624495798999L;
	private static final int RECORD_LIMIT = 1000;
	public static final Charset UTF_8 = Charset.forName("UTF-8");

	private final static Key _data;
	private final static Key _partitionKey;
	private final static Key _sequenceNumber;
	private final static Key _approximateArrival;
	private final static Key _encryptionType;
	private final static Key _raw;
	private final static Key _iteration;

	static {
		Creation creator = CFMLEngineFactory.getInstance().getCreationUtil();
		_data = creator.createKey("data");
		_partitionKey = creator.createKey("partitionKey");
		_sequenceNumber = creator.createKey("sequenceNumber");
		_approximateArrival = creator.createKey("approximateArrival");
		_encryptionType = creator.createKey("encryptionType");
		_raw = creator.createKey("raw");
		_iteration = creator.createKey("iteration");
	}

	public static Query call(PageContext pc, String streamName, String shardId, String sequenceNumber, DateTime timestamp, String iteratorType, double maxrows, String accessKeyId,
			String secretAccessKey, String host, String location, double timeout) throws PageException {

		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast caster = eng.getCastUtil();
		Log log = pc.getConfig().getLog("application");
		try {
			KinesisClient client = AmazonKinesisClient.get(accessKeyId, secretAccessKey, host, location, toTimeout(timeout), log);

			// validate maxrows
			int mr = Integer.MAX_VALUE;
			if (maxrows > 0d) mr = (int) maxrows;

			// validate location
			if (eng.getStringUtil().isEmpty(location, true)) location = null;

			// validate iteratorType
			boolean sequenceNumberRequired = false;
			boolean timestampRequired = false;

			if (!Util.isEmpty(iteratorType, true)) {
				iteratorType = iteratorType.trim().toUpperCase();
				if ("TRIM_HORIZON".equalsIgnoreCase(iteratorType)) iteratorType = "TRIM_HORIZON";
				else if ("LATEST".equalsIgnoreCase(iteratorType)) iteratorType = "LATEST";
				else if ("AT_SEQUENCE_NUMBER".equalsIgnoreCase(iteratorType) || "AFTER_SEQUENCE_NUMBER".equalsIgnoreCase(iteratorType)) {
					sequenceNumberRequired = true;
				}
				else if ("AT_TIMESTAMP".equalsIgnoreCase(iteratorType)) {
					timestampRequired = true;
				}
				else throw eng.getExceptionUtil().createFunctionException(pc, "KinesisGet", 3, "iteratorType",
						"invalid iteratorType [" + iteratorType + "], valid types are [TRIM_HORIZON,LATEST,AT_SEQUENCE_NUMBER,AFTER_SEQUENCE_NUMBER,AT_TIMESTAMP]", null);
			}
			else {
				iteratorType = "TRIM_HORIZON";
			}

			// validate sequenceNumber
			if (eng.getStringUtil().isEmpty(sequenceNumber, true)) {
				sequenceNumber = null;
				if (sequenceNumberRequired) throw eng.getExceptionUtil().createFunctionException(pc, "KinesisGet", 4, "sequenceNumber",
						"when iteratorType is to [" + iteratorType + "], then the [sequenceNumber] is required", null);
			}
			else {
				sequenceNumber = sequenceNumber.trim();
				if (!sequenceNumberRequired) throw eng.getExceptionUtil().createFunctionException(pc, "KinesisGet", 4, "sequenceNumber",
						"when iteratorType is [" + iteratorType + "], then the [sequenceNumber] cannot be set", null);
			}

			// validate timestamp
			if (timestamp == null) {
				if (timestampRequired) throw eng.getExceptionUtil().createFunctionException(pc, "KinesisGet", 5, "timestamp",
						"when iteratorType is to [" + iteratorType + "], then the [timestamp] is required", null);
			}
			else {
				if (!timestampRequired) throw eng.getExceptionUtil().createFunctionException(pc, "KinesisGet", 5, "timestamp",
						"when iteratorType is [" + iteratorType + "], then the [timestamp] cannot be set", null);
			}

			if (sequenceNumber != null) iteratorType = "AT_SEQUENCE_NUMBER";

			// validate shardId
			String shardIterator;
			if (Util.isEmpty(shardId, true)) {
				shardIterator = getLatestShardIterator(client, streamName);
			}
			else {
				Builder builder = GetShardIteratorRequest.builder();
				builder.streamName(streamName.trim());
				builder.shardId(shardId.trim());
				builder.shardIteratorType(iteratorType);
				if (sequenceNumberRequired) {
					builder.startingSequenceNumber(sequenceNumber);
				}
				else if (timestampRequired) {
					builder.timestamp(timestamp.toInstant());
				}
				GetShardIteratorRequest request = builder.build();
				GetShardIteratorResponse shardIteratorResponse = client.getShardIterator(request);
				shardIterator = shardIteratorResponse.shardIterator();

			}

			// result query
			Query result = eng.getCreationUtil().createQuery(new Key[] { _data, _partitionKey, _sequenceNumber, _approximateArrival, _encryptionType, _raw }, 0, "result");

			boolean moreRecordsAvailable = true;
			Record r;
			int row;
			outer: while (moreRecordsAvailable) {
				GetRecordsResponse rsp = client.getRecords(GetRecordsRequest.builder().shardIterator(shardIterator).limit(RECORD_LIMIT).build());
				// result.setAtEL(_data, result.addRow(), "millisBehindLatest:" + rsp.millisBehindLatest());
				// result.setAtEL(_data, result.addRow(), "hasRecords:" + rsp.hasRecords());

				List<Record> records = rsp.records();

				// result.setAtEL(_data, result.addRow(), "records.isEmpty:" + records.isEmpty());
				// result.setAtEL(_data, result.addRow(), "records.size:" + records.size());

				Iterator<Record> it = records.iterator();
				while (it.hasNext()) {
					r = it.next();
					row = result.addRow();
					result.setAtEL(_encryptionType, row, r.encryptionTypeAsString());
					result.setAtEL(_partitionKey, row, r.partitionKey());
					result.setAtEL(_sequenceNumber, row, r.sequenceNumber());
					result.setAtEL(_approximateArrival, row, caster.toDateTime(Date.from(r.approximateArrivalTimestamp()), null));
					result.setAtEL(_raw, row, r);
					result.setAtEL(_data, row, Functions.deserializeJSON(r.data().asString(UTF_8)));
					if (row >= mr) break outer;
				}

				shardIterator = rsp.nextShardIterator();

				if (shardIterator == null || rsp.millisBehindLatest() == 0) { // records.isEmpty() ||
					moreRecordsAvailable = false;
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
		Decision dec = engine.getDecisionUtil();

		if (args.length < 1 || args.length > 11) throw engine.getExceptionUtil().createFunctionException(pc, "KinesisGet", 1, 11, args.length);

		// streamName
		String streamName = dec.isEmpty(args[0]) ? null : cast.toString(args[0]);
		if (Util.isEmpty(streamName, true))
			throw engine.getExceptionUtil().createFunctionException(pc, "KinesisGet", 1, "streamName", "invalid streamName [" + streamName + "],value cannot be empty", null);
		else streamName = streamName.trim();

		// shardId
		String shardId = null;
		if (args.length > 1) {
			String tmp = dec.isEmpty(args[1]) ? null : cast.toString(args[1]);
			if (!Util.isEmpty(tmp, true)) shardId = tmp.trim();
			else shardId = null;
		}

		// sequenceNumber
		String sequenceNumber = null;
		if (args.length > 2) {
			String tmp = dec.isEmpty(args[2]) ? null : cast.toString(args[2]);
			if (!Util.isEmpty(tmp, true)) sequenceNumber = tmp.trim();
			else sequenceNumber = null;
		}

		// timestamp
		DateTime timestamp = null;
		if (args.length > 3) {
			timestamp = dec.isEmpty(args[3]) ? null : cast.toDateTime(args[3], null);
		}

		// iteratorType
		String iteratorType = null;
		if (args.length > 4) {
			String tmp = dec.isEmpty(args[4]) ? null : cast.toString(args[4]);
			if (!Util.isEmpty(tmp, true)) iteratorType = tmp.trim();
			else iteratorType = null;
		}

		// maxrows
		double maxrows = 0;
		if (args.length > 5) {
			double tmp = dec.isEmpty(args[5]) ? null : cast.toDoubleValue(args[5]);
			if (tmp > 0) maxrows = tmp;
			else maxrows = 0;
		}

		// accessKeyId
		String accessKeyId = null;
		if (args.length > 6) {
			String tmp = dec.isEmpty(args[6]) ? null : cast.toString(args[67]);
			if (!Util.isEmpty(tmp, true)) accessKeyId = tmp.trim();
			else accessKeyId = null;
		}

		// secretAccessKey
		String secretAccessKey = null;
		if (args.length > 7) {
			String tmp = dec.isEmpty(args[7]) ? null : cast.toString(args[7]);
			if (!Util.isEmpty(tmp, true)) secretAccessKey = tmp.trim();
			else secretAccessKey = null;
		}

		// host
		String host = null;
		if (args.length > 8) {
			String tmp = dec.isEmpty(args[8]) ? null : cast.toString(args[8]);
			if (!Util.isEmpty(tmp, true)) host = tmp.trim();
			else host = null;
		}

		// location
		String location = null;
		if (args.length > 9) {
			String tmp = dec.isEmpty(args[9]) ? null : cast.toString(args[9]);
			if (!Util.isEmpty(tmp, true)) location = tmp.trim();
			else location = null;
		}

		// timeout
		double timeout = 0;
		if (args.length > 10) {
			double tmp = dec.isEmpty(args[10]) ? null : cast.toDoubleValue(args[10]);
			if (tmp > 0) timeout = tmp;
			else timeout = 0;
		}

		return call(pc, streamName, shardId, sequenceNumber, timestamp, iteratorType, maxrows, accessKeyId, secretAccessKey, host, location, timeout);

	}
}