package org.lucee.extension.aws.kinesis.function;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.type.Array;
import lucee.runtime.util.Cast;

public abstract class KinesisFunction extends BIF {
	private static final long serialVersionUID = 435549313519427604L;
	private static final long DEFAULT_TIMEOUT = 10000L;

	protected static long toTimeout(double timeout) {
		if (timeout > 0D) return CFMLEngineFactory.getInstance().getCastUtil().toLongValue(timeout);
		return DEFAULT_TIMEOUT;
	}

	protected static Collection<String> toCollection(Array instanceIds) throws PageException {
		List<String> list = new ArrayList<>();
		Iterator<Object> it = instanceIds.valueIterator();
		Cast caster = CFMLEngineFactory.getInstance().getCastUtil();
		String str;
		while (it.hasNext()) {
			str = caster.toString(it.next());
			if (!Util.isEmpty(str)) list.add(str);
		}

		return list;
	}
}
