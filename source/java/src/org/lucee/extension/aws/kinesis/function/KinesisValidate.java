package org.lucee.extension.aws.kinesis.function;

import java.nio.charset.Charset;

import org.lucee.extension.aws.kinesis.AmazonKinesisClient;
import org.lucee.extension.aws.kinesis.util.CommonUtil;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.util.Cast;
import lucee.runtime.util.Decision;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ListStreamsRequest;

public class KinesisValidate extends KinesisFunction {

	private static final long serialVersionUID = 8983735867531664057L;
	public static final Charset UTF_8 = Charset.forName("UTF-8");

	@Override
	public Object invoke(PageContext pc, Object[] args) throws PageException {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Cast cast = engine.getCastUtil();
		Decision dec = engine.getDecisionUtil();

		if (args.length > 5) throw engine.getExceptionUtil().createFunctionException(pc, "KinesisConnect", 1, 11, args.length);

		// accessKeyId
		String accessKeyId = null;
		if (args.length > 0) {
			String tmp = dec.isEmpty(args[0]) ? null : cast.toString(args[0]);
			if (!Util.isEmpty(tmp, true)) accessKeyId = tmp.trim();
			else accessKeyId = null;
		}

		// secretAccessKey
		String secretAccessKey = null;
		if (args.length > 1) {
			String tmp = dec.isEmpty(args[1]) ? null : cast.toString(args[1]);
			if (!Util.isEmpty(tmp, true)) secretAccessKey = tmp.trim();
			else secretAccessKey = null;
		}

		// host
		String host = null;
		if (args.length > 2) {
			String tmp = dec.isEmpty(args[2]) ? null : cast.toString(args[2]);
			if (!Util.isEmpty(tmp, true)) host = tmp.trim();
			else host = null;
		}

		// location
		String location = null;
		if (args.length > 3) {
			String tmp = dec.isEmpty(args[3]) ? null : cast.toString(args[3]);
			if (!Util.isEmpty(tmp, true)) location = tmp.trim();
			else location = null;
		}

		// timeout
		double timeout = 0;
		if (args.length > 4) {
			double tmp = dec.isEmpty(args[4]) ? null : cast.toDoubleValue(args[4]);
			if (tmp > 0) timeout = tmp;
			else timeout = 0;
		}

		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Log log = pc.getConfig().getLog("application");
		try {
			KinesisClient client = AmazonKinesisClient.get(CommonUtil.toKinesisProps(pc, accessKeyId, secretAccessKey, host, location), toTimeout(timeout), log);

			// Perform the request
			client.listStreams(ListStreamsRequest.builder().limit(1).build());

			// If the request succeeds, the connection is considered valid
			// System.out.println("Connection successful. Available streams: " + response.streamNames());

		}
		catch (Exception e) {
			throw CommonUtil.toPageException(e);
		}

		return null;

	}
}