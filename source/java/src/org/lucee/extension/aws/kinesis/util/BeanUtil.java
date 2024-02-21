package org.lucee.extension.aws.kinesis.util;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Array;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Creation;

public class BeanUtil {

	public static Object beanToCFML(Object obj, boolean legacyMode) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Creation creator = eng.getCreationUtil();

		return beanToCFML(eng, creator, obj, legacyMode);
	}

	private static Object beanToCFML(CFMLEngine eng, Creation creator, Object obj, boolean legacyMode) throws PageException {

		// simple
		if (obj == null) {
			return legacyMode ? "" : obj;
		}

		if (eng.getDecisionUtil().isSimpleValue(obj)) {// TODO isSimpleValue
			return obj;
		}

		// List
		if (obj instanceof List) {
			Array result = creator.createArray();
			for (Object o: ((List) obj)) {
				result.append(beanToCFML(eng, creator, o, legacyMode));
			}
			return result;
		}

		else if (obj instanceof Map) {
			Struct result = creator.createStruct(Struct.TYPE_LINKED);
			Iterator<Entry> it = ((Map) obj).entrySet().iterator();
			Entry e;
			Object value;
			String key;
			while (it.hasNext()) {
				e = it.next();
				key = e.getKey().toString();
				value = beanToCFML(eng, creator, e.getValue(), legacyMode);
				if (legacyMode) legacyMode(eng, creator, result, key, value);
				result.setEL(key, value);
			}
			return result;
		}

		Struct result = creator.createStruct(Struct.TYPE_LINKED);
		Class<?> clazz = obj.getClass();
		String name;
		Key k;
		Object v;
		for (Method m: clazz.getMethods()) {
			name = m.getName();
			if (!name.equals("getNextToken") && !name.equals("getClass") && name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))
					&& m.getParameterCount() == 0 && !m.getReturnType().equals(void.class)) {
				k = creator.createKey(name.substring(3, 4).toLowerCase() + name.substring(4));
				v = beanToCFML(eng, creator, eng.getClassUtil().callMethod(obj, creator.createKey(name), new Object[] {}), legacyMode);
				if (legacyMode) legacyMode(eng, creator, result, k.getString(), v);
				result.setEL(k, v);
			}
		}
		return result;

	}

	private static void legacyMode(CFMLEngine eng, Creation creator, Struct result, String key, Object value) throws PageException {
		boolean setOriginal = true;
		if (value instanceof Array && key.endsWith("s")) {
			int size = ((Array) value).size();
			Object modified;
			if (size == 1) {
				Struct item = creator.createStruct();
				modified = item;
				item.set("item", ((Array) value).getE(1));
			}
			else if (size == 0) modified = "";
			else modified = value;

			result.setEL(key.substring(0, key.length() - 1), modified);
			result.setEL(key.substring(0, key.length() - 1) + "Set", modified);
			result.setEL(key + "Set", modified);
			result.setEL(key, modified);
			result.setEL(key + "Array", value);

			if ("securityGroups".equalsIgnoreCase(key)) {
				result.setEL("groupSet", modified);
			}

			if (size > 0) {
				Object o = ((Array) value).getE(1);
				Array child;
				if (o instanceof Struct && (child = eng.getCastUtil().toArray(((Struct) o).get("privateIpAddresses", null), null)) != null) {
					Object publicDnsName = null;
					Struct grandChild;
					if ((grandChild = eng.getCastUtil().toStruct(child.get(1, null), null)) != null) {
						Object privateIpAddress = grandChild.get("privateIpAddress", null);
						if (privateIpAddress != null) result.setEL("ipAddress", privateIpAddress);
						Struct association;
						if ((association = eng.getCastUtil().toStruct(grandChild.get("association", null), null)) != null) {
							publicDnsName = association.get("publicDnsName", null);
						}

					}
					if (publicDnsName != null) result.setEL("dnsName", publicDnsName);
					else result.setEL("dnsName", "");
				}
			} // association.publicDnsName
		}
		else if (value instanceof Struct && ((Struct) value).containsKey("httpProtocolIpv6")) {
			((Struct) value).set("httpProtocolIpv4", "enabled");
		}
		else if ("state".equalsIgnoreCase(key)) {
			result.setEL("instanceState", value);
		}
		else if ("stateTransitionReason".equalsIgnoreCase(key)) {
			result.setEL("reason", value);
		}
	}

}
