package org.lucee.extension.aws.kinesis.function;

import java.util.List;

import org.lucee.extension.aws.kinesis.AmazonKinesisClient;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Query;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.AccessDeniedException;
import software.amazon.awssdk.services.kinesis.model.DescribeLimitsResponse;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.Shard;

public class KinesisInfo extends KinesisFunction {

	private static final long serialVersionUID = 5961249624495798999L;

	public static Struct call(PageContext pc, String streamName, String accessKeyId, String secretAccessKey, String host, String location, double timeout) throws PageException {

		CFMLEngine eng = CFMLEngineFactory.getInstance();

		// for backward compatibility, when host was not existing
		if (eng.getDecisionUtil().isNumber(host)) {
			timeout = eng.getCastUtil().toDoubleValue(host);
			host = null;
		}

		if (eng.getStringUtil().isEmpty(location, true)) location = null;

		try {
			Log log = pc.getConfig().getLog("application");

			KinesisClient client = AmazonKinesisClient.get(accessKeyId, secretAccessKey, host, location, toTimeout(timeout), log);

			Struct result = eng.getCreationUtil().createStruct();

			// Describe the stream and print shard IDs
			String exclusiveStartShardId = null;
			Query qShards = eng.getCreationUtil().createQuery(new String[] { "shardId", "parentShardId", "adjacentParentShardId" }, 0, "shards");
			result.put("shards", qShards);
			int row;
			do {
				// describeStream
				DescribeStreamRequest describeStreamRequest = DescribeStreamRequest.builder().streamName(streamName).exclusiveStartShardId(exclusiveStartShardId).build();
				DescribeStreamResponse describeStreamResponse = client.describeStream(describeStreamRequest);
				List<Shard> listShards = describeStreamResponse.streamDescription().shards();

				for (Shard s: listShards) {
					row = qShards.addRow();
					qShards.setAtEL("shardId", row, s.shardId());
					qShards.setAtEL("parentShardId", row, s.parentShardId());
					qShards.setAtEL("adjacentParentShardId", row, s.adjacentParentShardId());
				}

				if (describeStreamResponse.streamDescription().hasMoreShards()) {
					exclusiveStartShardId = describeStreamResponse.streamDescription().shards().get(describeStreamResponse.streamDescription().shards().size() - 1).shardId();
				}
				else {
					exclusiveStartShardId = null;
				}
			}
			while (exclusiveStartShardId != null);

			// describeLimits
			try {
				DescribeLimitsResponse limitsResponse = client.describeLimits();
				result.put("shardLimit", limitsResponse.shardLimit());
			}
			catch (AccessDeniedException ade) {
				result.put("shardLimit", ade.getMessage());
				// if (throwOnAccessDenied) throw ade;
			}

			return result;
		}
		catch (Exception e) {
			throw eng.getCastUtil().toPageException(e);
		}
	}

	@Override
	public Object invoke(PageContext pc, Object[] args) throws PageException {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Cast cast = engine.getCastUtil();

		if (args.length < 1 || args.length > 68) throw engine.getExceptionUtil().createFunctionException(pc, "KinesisInfo", 1, 6, args.length);

		// streamName
		String streamName = cast.toString(args[0]);
		if (Util.isEmpty(streamName, true))
			throw engine.getExceptionUtil().createFunctionException(pc, "KinesisGet", 1, "streamName", "invalid streamName [" + streamName + "],value cannot be empty", null);
		else streamName = streamName.trim();

		// accessKeyId
		String accessKeyId = null;
		if (args.length > 1) {
			String tmp = cast.toString(args[1]);
			if (!Util.isEmpty(tmp, true)) accessKeyId = tmp.trim();
			else accessKeyId = null;
		}

		// secretAccessKey
		String secretAccessKey = null;
		if (args.length > 2) {
			String tmp = cast.toString(args[2]);
			if (!Util.isEmpty(tmp, true)) secretAccessKey = tmp.trim();
			else secretAccessKey = null;
		}

		// host
		String host = null;
		if (args.length > 3) {
			String tmp = cast.toString(args[3]);
			if (!Util.isEmpty(tmp, true)) host = tmp.trim();
			else host = null;
		}

		// location
		String location = null;
		if (args.length > 4) {
			String tmp = cast.toString(args[4]);
			if (!Util.isEmpty(tmp, true)) location = tmp.trim();
			else location = null;
		}

		// timeout
		double timeout = 0;
		if (args.length > 5) {
			double tmp = cast.toDoubleValue(args[5]);
			if (tmp > 0) timeout = tmp;
			else timeout = 0;
		}

		return call(pc, streamName, accessKeyId, secretAccessKey, host, location, timeout);

	}
}