package org.lucee.extension.aws.kinesis.function;

import org.lucee.extension.aws.kinesis.AmazonKinesisClient;
import org.lucee.extension.aws.kinesis.util.Functions;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import lucee.runtime.util.Creation;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.KinesisResponseMetadata;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;

public class KinesisPut extends KinesisFunction {

	private static final long serialVersionUID = 5961249624495798999L;

	public static Struct call(PageContext pc, Struct data, String partitionKey, String streamName, String accessKeyId, String secretAccessKey, String host, String location,
			double timeout) throws PageException {
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

			SdkBytes bytes = SdkBytes.fromString(Functions.serializeJSON(pc, data, false), KinesisGet.UTF_8); // MUST serailize data to json
			PutRecordRequest req = PutRecordRequest.builder().partitionKey(partitionKey).streamName(streamName).data(bytes).build();
			PutRecordResponse rsp = client.putRecord(req);

			Creation creator = eng.getCreationUtil();

			Struct result = creator.createStruct();

			result.set("encryptionType", rsp.encryptionTypeAsString());
			result.set("shardId", rsp.shardId());
			result.set("sequenceNumber", rsp.sequenceNumber());

			// meta
			KinesisResponseMetadata krm = rsp.responseMetadata();
			Struct meta = creator.createStruct();
			result.set("metadata", meta);
			meta.set("extendedRequestId", krm.extendedRequestId());
			meta.set("requestId", krm.requestId());
			result.set("raw", rsp);
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

		if (args.length < 1 || args.length > 8) throw engine.getExceptionUtil().createFunctionException(pc, "KinesisPut", 1, 8, args.length);
		// data
		Struct data = cast.toStruct(args[0]);

		// partitionKey
		String partitionKey = null;
		if (args.length > 1) {
			String tmp = cast.toString(args[1]);
			if (!Util.isEmpty(tmp, true)) partitionKey = tmp.trim();
			else partitionKey = null;
		}

		// streamName
		String streamName = null;
		if (args.length > 2) {
			String tmp = cast.toString(args[2]);
			if (!Util.isEmpty(tmp, true)) streamName = tmp.trim();
			else streamName = null;
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

		return call(pc, data, partitionKey, streamName, accessKeyId, secretAccessKey, host, location, timeout);

	}
}