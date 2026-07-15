package org.lucee.extension.aws.kinesis.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lucee.commons.io.log.Log;
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
	private static Integer javaMajorNumber;

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

		Struct kinesisApp = null;

		// application context
		ApplicationContext ac = pc != null ? pc.getApplicationContext() : null;
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
				kinesisApp = caster.toStruct(sct.get("kinesis", null), null);
				if (kinesisApp != null) {
					if (Util.isEmpty(accessKeyId, true)) accessKeyId = caster.toString(kinesisApp.get("accesskeyid", null), null);
					if (Util.isEmpty(accessKeyId, true)) accessKeyId = caster.toString(kinesisApp.get("accesskey", null), null);

					if (Util.isEmpty(secretAccessKey, true)) secretAccessKey = caster.toString(kinesisApp.get("secretaccesskey", null), null);
					if (Util.isEmpty(secretAccessKey, true)) secretAccessKey = caster.toString(kinesisApp.get("secretkey", null), null);

					if (Util.isEmpty(host, true)) host = caster.toString(kinesisApp.get("host", null), null);
					if (Util.isEmpty(host, true)) host = caster.toString(kinesisApp.get("server", null), null);

					if (Util.isEmpty(region, true)) region = caster.toString(kinesisApp.get("region", null), null);
					if (Util.isEmpty(region, true)) region = caster.toString(kinesisApp.get("location", null), null);
				}
			}

		}

		Struct poolStruct = kinesisApp == null ? null : caster.toStruct(kinesisApp.get("pool", null), null);
		KinesisHttpPoolSettings httpPool = KinesisHttpPoolSettings.load(eng, poolStruct);

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
			props.setHttpPool(httpPool);
			return props;
		}

		return null;
	}

	/**
	 * Resolves HTTP pool settings from Application.cfc this.kinesis.pool and env/system properties.
	 */
	public static KinesisHttpPoolSettings toHttpPoolSettings(PageContext pc) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast caster = eng.getCastUtil();
		Struct poolStruct = null;

		ApplicationContext ac = pc != null ? pc.getApplicationContext() : null;
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
				Struct kinesisApp = caster.toStruct(sct.get("kinesis", null), null);
				if (kinesisApp != null) poolStruct = caster.toStruct(kinesisApp.get("pool", null), null);
			}
		}
		return KinesisHttpPoolSettings.load(eng, poolStruct);
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

	public static ExecutorService createExecutorService(int maxThreads, Log log) {
		// virtual threads
		if (javaMajorNumber() >= 19) {
			try {
				MethodHandles.Lookup lookup = MethodHandles.lookup();
				MethodType methodType = MethodType.methodType(ExecutorService.class);
				MethodHandle methodHandle = lookup.findStatic(Executors.class, "newVirtualThreadPerTaskExecutor", methodType);
				ExecutorService es = (ExecutorService) methodHandle.invoke();
				if (log != null) log.log(Log.LEVEL_INFO, "Kinesis", "use virtual threads for threading");
				return es;
			}
			catch (Throwable t) {
				if (log != null) log.log(Log.LEVEL_ERROR, "Kinesis", t);
				// in case of an exception, we simply ignore it and fall back to regular threads
				if (t instanceof ThreadDeath) throw (ThreadDeath) t;
			}

		}
		// regulat threads
		ExecutorService es = Executors.newFixedThreadPool(maxThreads);
		if (log != null) log.log(Log.LEVEL_INFO, "Kinesis", "use regular threads for threading");
		return es;
	}

	public static int javaMajorNumber() {
		if (javaMajorNumber == null) {
			String version = System.getProperty("java.version");
			int index = version.indexOf('.');
			if (index == -1) return javaMajorNumber = 0;
			version = version.substring(0, index);
			try {
				return javaMajorNumber = Integer.parseInt(version);
			}
			catch (NumberFormatException nfe) {
				return javaMajorNumber = 0;
			}
		}
		return javaMajorNumber;
	}
}
