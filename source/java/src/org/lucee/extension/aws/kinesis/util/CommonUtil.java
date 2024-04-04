package org.lucee.extension.aws.kinesis.util;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.listener.ApplicationContext;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;

public class CommonUtil {
	private static BIF bif;

	public static String getSystemPropOrEnvVar(String name, String defaultValue) {
		// env
		String value = System.getenv(name);
		if (!Util.isEmpty(value)) return value;

		// prop
		value = System.getProperty(name);
		if (!Util.isEmpty(value)) return value;

		// env 2
		name = name.replace('.', '_').toUpperCase();
		value = System.getenv(name);
		if (!Util.isEmpty(value)) return value;

		return defaultValue;
	}

	public static KinesisProps toKinesisProps(PageContext pc, String accessKeyId, String secretAccessKey, String host, String region) throws PageException {

		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast caster = eng.getCastUtil();

		// application context
		ApplicationContext ac = pc.getApplicationContext();
		if (ac != null) {

			if (bif == null) {
				try {
					bif = CFMLEngineFactory.getInstance().getClassUtil().loadBIF(pc, "lucee.runtime.functions.system.GetApplicationSettings");
				}
				catch (Exception e) {
					throw caster.toPageException(e);
				}
			}

			Struct sct = caster.toStruct(bif.invoke(pc, new Object[] { Boolean.TRUE }), null);
			if (sct != null) {
				sct = caster.toStruct(sct.get("kinesis", null), null);
				if (sct != null) {
					if (Util.isEmpty(accessKeyId, true)) accessKeyId = caster.toString(sct.get("accesskeyid", null), null);
					if (Util.isEmpty(accessKeyId, true)) accessKeyId = caster.toString(sct.get("accesskey", null), null);

					if (Util.isEmpty(secretAccessKey, true)) secretAccessKey = caster.toString(sct.get("secretaccesskey", null), null);
					if (Util.isEmpty(secretAccessKey, true)) secretAccessKey = caster.toString(sct.get("secretkey", null), null);

					if (Util.isEmpty(host, true)) host = caster.toString(sct.get("host", null), null);
					if (Util.isEmpty(host, true)) host = caster.toString(sct.get("server", null), null);

					if (Util.isEmpty(region, true)) region = caster.toString(sct.get("region", null), null);
					if (Util.isEmpty(region, true)) region = caster.toString(sct.get("location", null), null);
				}
			}

		}

		// env var/sys prop
		if (Util.isEmpty(accessKeyId, true)) accessKeyId = getSystemPropOrEnvVar("lucee.kinesis.accesskeyid", null);
		if (Util.isEmpty(accessKeyId, true)) accessKeyId = getSystemPropOrEnvVar("lucee.kinesis.accesskey", null);

		if (Util.isEmpty(secretAccessKey, true)) secretAccessKey = getSystemPropOrEnvVar("lucee.kinesis.secretaccesskey", null);
		if (Util.isEmpty(secretAccessKey, true)) secretAccessKey = getSystemPropOrEnvVar("lucee.kinesis.secretkey", null);

		if (Util.isEmpty(host, true)) host = getSystemPropOrEnvVar("lucee.kinesis.host", null);
		if (Util.isEmpty(host, true)) host = getSystemPropOrEnvVar("lucee.kinesis.server", null);
		if (Util.isEmpty(host, true)) host = getSystemPropOrEnvVar("lucee.kinesis.provider", null);

		if (Util.isEmpty(region, true)) region = getSystemPropOrEnvVar("lucee.kinesis.region", null);
		if (Util.isEmpty(region, true)) region = getSystemPropOrEnvVar("lucee.kinesis.location", null);

		if (!Util.isEmpty(accessKeyId, true) && !Util.isEmpty(secretAccessKey, true)) {
			KinesisProps props = new KinesisProps();
			props.setSecretAccessKey(secretAccessKey);
			props.setAccessKeyId(accessKeyId);
			if (!Util.isEmpty(host, true)) {
				props.setHost(host);
			}
			if (!Util.isEmpty(region, true)) {
				props.setRegion(region);
			}
			return props;
		}

		return null;
	}

	public static PageException toPageException(Exception e) {
		String msg = e.getMessage();
		if (msg != null && msg.indexOf("Unable to load credentials from any of the providers") != -1) {
			PageException exp = CFMLEngineFactory.getInstance().getExceptionUtil()
					.createApplicationException("you can define the credentials as argument for the function " + "[accessKeyId, secretAccessKey, host, region],"
							+ " in the application.cfc [this.kinesis.accessKeyId, this.kinesis.secretAccessKey, this.kinesis.host, this.kinesis.region], "
							+ " in the system properties [lucee.kinesis.secretaccesskey, lucee.kinesis.accesskeyid, lucee.kinesis.host, lucee.kinesis.region]"
							+ " or in the environment variables [LUCEE_KINESIS_SECRETACCESSKEY, LUCEE_KINESIS_ACCESSKEYID, LUCEE_KINESIS_HOST, LUCEE_KINESIS_REGION]"

					);
			exp.initCause(e);
			return exp;
		}

		return CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
	}
}
