package org.lucee.extension.aws.kinesis.function;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.Component;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;

public abstract class AbstrExecutable implements Runnable {

	private static final Key ON_SUCCESS;
	private static final Key ON_ERROR;

	static {
		ON_SUCCESS = CFMLEngineFactory.getInstance().getCreationUtil().createKey("onSuccess");
		ON_ERROR = CFMLEngineFactory.getInstance().getCreationUtil().createKey("onError");
	}

	private Method clonePageContext;
	private static final Class[] ARGS = new Class[] { PageContext.class, OutputStream.class, boolean.class, boolean.class, boolean.class };

	private CFMLEngine eng;
	private Config config;
	private Object listener;
	private PageContext pc;

	public AbstrExecutable(CFMLEngine eng, PageContext parent, Object listener) throws PageException {
		this.eng = eng;
		this.config = parent.getConfig();
		this.pc = listener != null ? clonePageContext(parent) : null;
		this.listener = listener;
	}

	@Override
	public void run() {
		try {
			boolean hasOnSuccess = has(pc, ON_SUCCESS);
			if (pc != null) eng.registerThreadPageContext(pc);

			Struct res = call(hasOnSuccess);
			if (hasOnSuccess) {
				eng.registerThreadPageContext(pc);
				call(pc, ON_SUCCESS, new Object[] { res });
			}
		}
		catch (Exception e) {
			if (has(pc, ON_ERROR)) {
				try {
					call(pc, ON_ERROR, new Object[] { eng.getCastUtil().toPageException(e).getCatchBlock(config) });
				}
				catch (Exception ee) {
					config.getLog("application").error("Kinesis", ee);
				}
			}
			else {
				config.getLog("application").error("Kinesis", e);
			}
		}
		finally {
			if (pc != null) eng.releasePageContext(pc, true);
		}
	}

	private PageContext clonePageContext(PageContext parent) throws PageException {
		try {
			if (clonePageContext == null || clonePageContext.getDeclaringClass().getClassLoader() != parent.getClass().getClassLoader()) {
				Class<?> clazz = eng.getClassUtil().loadClass(config.getClass().getClassLoader(), "lucee.runtime.thread.ThreadUtil");
				clonePageContext = clazz.getMethod("clonePageContext", ARGS);
			}
			return (PageContext) clonePageContext.invoke(null, new Object[] { parent, new ByteArrayOutputStream(), false, false, false });
		}
		catch (Exception e) {
			throw eng.getCastUtil().toPageException(e);
		}
	}

	public boolean has(PageContext pc, Collection.Key functionName) {
		if (listener != null) {
			if (listener instanceof Component) return ((Component) listener).contains(pc, functionName);
			if (listener instanceof Struct) {
				return ((Struct) listener).get(functionName, null) instanceof UDF;
			}
		}
		return false;
	}

	private Object call(PageContext pc, Key key, Object[] args) throws PageException {
		if (listener instanceof Component) {
			return ((Component) listener).call(pc, key, args);
		}
		return ((UDF) eng.getCastUtil().toStruct(listener).get(key)).call(pc, key, args, false);
	}

	public abstract Struct call(boolean returnData) throws PageException;
}