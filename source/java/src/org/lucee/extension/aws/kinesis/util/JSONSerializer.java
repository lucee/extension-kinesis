
package org.lucee.extension.aws.kinesis.util;

import java.io.File;
import java.lang.ref.SoftReference;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.Component;
import lucee.runtime.converter.ConverterException;
import lucee.runtime.converter.ScriptConvertable;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Array;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;
import lucee.runtime.type.dt.DateTime;
import lucee.runtime.type.dt.TimeSpan;

/**
 * class to serialize and desirilize WDDX Packes
 */
public final class JSONSerializer {

	private String pattern;

	private String eol;
	private String indent1;
	private String indent2;
	private String indent3;
	private String indent4;
	private String indent5;
	private String indent6;
	private String indent7;
	private String indent8;
	private String indent9;
	private String indent10;
	private int level = 0;

	private CFMLEngine eng;

	public JSONSerializer(Charset charset) {
		try {
			eng = CFMLEngineFactory.getInstance();
		}
		catch (Exception e) {

		}
		this.eol = "\n";
		this.indent1 = "   ";
		this.indent2 = indent1 + indent1;
		this.indent3 = indent1 + indent1 + indent1;
		this.indent4 = indent1 + indent1 + indent1 + indent1;
		this.indent5 = indent1 + indent1 + indent1 + indent1 + indent1;
		this.indent6 = indent1 + indent1 + indent1 + indent1 + indent1 + indent1;
		this.indent7 = indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1;
		this.indent8 = indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1;
		this.indent9 = indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1;
		this.indent10 = indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1 + indent1;

	}

	/**
	 * serialize a Date
	 * 
	 * @param date Date to serialize
	 * @param sb
	 * @throws PageException
	 * @throws ConverterException
	 */
	private void _serializeDate(Date date, StringBuilder sb) throws PageException {
		sb.append(escapeJS(JSONDateFormat.format(date, null, pattern)));
	}

	/**
	 * serialize a DateTime
	 * 
	 * @param dateTime DateTime to serialize
	 * @param sb
	 * @throws ConverterException
	 */
	private void _serializeDateTime(DateTime dateTime, StringBuilder sb) {
		sb.append(escapeJS(JSONDateFormat.format(dateTime, null, pattern)));
	}

	/**
	 * serialize an Array
	 * 
	 * @param array Array to serialize
	 * @param sb
	 * @param serializeQueryByColumns
	 * @param done
	 * @throws ConverterException
	 * @throws PageException
	 */
	private void _serializeArray(Array array, StringBuilder sb) throws PageException {
		_serializeList(array.toList(), sb);
	}

	/**
	 * serialize a List (as Array)
	 * 
	 * @param list List to serialize
	 * @param sb
	 * @param serializeQueryByColumns
	 * @param done
	 * @throws ConverterException
	 * @throws PageException
	 */
	private void _serializeList(List list, StringBuilder sb) throws PageException {

		sb.append("[");
		sb.append(eol);
		right();

		boolean doIt = false;
		ListIterator it = list.listIterator();
		while (it.hasNext()) {
			if (doIt) {
				sb.append(',');
				sb.append(eol);
				sb.append(indent());
			}
			else {
				sb.append(indent());
			}
			doIt = true;
			_serialize(it.next(), sb);
		}

		sb.append(eol);
		left();
		sb.append(indent());
		sb.append(']');
	}

	private void _serializeArray(Object[] arr, StringBuilder sb) throws PageException {
		sb.append("[");
		sb.append(eol);
		right();

		for (int i = 0; i < arr.length; i++) {
			if (i > 0) {
				sb.append(',');
				sb.append(eol);
				sb.append(indent());
			}
			else {
				sb.append(indent());
			}

			_serialize(arr[i], sb);
		}

		sb.append(eol);
		left();
		sb.append(indent());
		sb.append(']');
	}

	/**
	 * serialize a Struct
	 * 
	 * @param struct Struct to serialize
	 * @param sb
	 * @param serializeQueryByColumns
	 * @param addUDFs
	 * @param done
	 * @throws ConverterException
	 * @throws PageException
	 */
	public void _serializeStruct(Struct struct, StringBuilder sb) throws PageException {
		// { null: { null: { null: null, null: null, null: null }, null: { null: 7425915, null: 0, null:
		// 3643064, null: { null: null }, null: null, null: null, null: null, null: 1740047 } }, null: null,
		// null: null }
		sb.append("{");
		sb.append(eol);
		right();
		Iterator<Entry<Key, Object>> it = struct.entryIterator();
		Entry<Key, Object> e;
		String k;
		Object value;
		boolean doIt = false;
		while (it.hasNext()) {

			e = it.next();
			k = e.getKey().getString();
			value = e.getValue();

			if ((value instanceof UDF)) continue;
			if (doIt) {
				sb.append(',');
				sb.append(eol);
				sb.append(indent());
			}
			else {
				sb.append(indent());
			}
			doIt = true;
			sb.append(escapeJS(k));
			sb.append(": ");
			_serialize(value, sb);
		}

		if (struct instanceof Component) {
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException("serialize components is not supported");
		}
		sb.append(eol);
		left();
		sb.append(indent());
		sb.append('}');
	}

	/**
	 * serialize a Map (as Struct)
	 * 
	 * @param map Map to serialize
	 * @param sb
	 * @param serializeQueryByColumns
	 * @param done
	 * @throws ConverterException
	 * @throws PageException
	 */
	private void _serializeMap(Map map, StringBuilder sb) throws PageException {
		sb.append("{");
		sb.append(eol);
		right();

		Iterator it = map.keySet().iterator();
		boolean doIt = false;
		while (it.hasNext()) {
			Object key = it.next();
			if (doIt) {
				sb.append(',');
				sb.append(eol);
				sb.append(indent());
			}
			else {
				sb.append(indent());
			}
			doIt = true;
			sb.append(escapeJS(key.toString()));
			sb.append(": ");
			_serialize(map.get(key), sb);
		}
		sb.append(eol);
		left();
		sb.append(indent());
		sb.append('}');
	}

	/**
	 * serialize an Object to his xml Format represenation
	 * 
	 * @param object Object to serialize
	 * @param sb StringBuilder to write data
	 * @param serializeQueryByColumns
	 * @param done
	 * @throws ConverterException
	 * @throws PageException
	 */
	private void _serialize(Object object, StringBuilder sb) throws PageException {

		// NULL
		if (object == null) {
			sb.append("null");
			return;
		}
		// String
		if (object instanceof String || object instanceof StringBuilder) {
			sb.append(escapeJS(object.toString()));
			return;
		}
		// TimeZone
		if (object instanceof TimeZone) {
			sb.append(escapeJS(((TimeZone) object).getID()));
			return;
		}
		// Locale
		if (object instanceof Locale) {
			sb.append(escapeJS(object.toString()));
			return;
		}
		// Character
		if (object instanceof Character) {
			sb.append(escapeJS(String.valueOf(((Character) object).charValue())));
			return;
		}
		// Number
		if (object instanceof Number) {
			if (eng != null) sb.append(eng.getCastUtil().toString(object));
			else sb.append(((Number) object).toString());

			return;
		}
		// Boolean
		if (object instanceof Boolean) {
			if (eng != null) sb.append(eng.getCastUtil().toString(((Boolean) object).booleanValue()));
			else sb.append(((Boolean) object).booleanValue() ? "true" : "false");
			return;
		}
		// DateTime
		if (object instanceof DateTime) {
			_serializeDateTime((DateTime) object, sb);
			return;
		}
		// Date
		if (object instanceof Date) {
			_serializeDate((Date) object, sb);
			return;
		}

		// Timespan
		if (object instanceof TimeSpan) {
			_serializeTimeSpan((TimeSpan) object, sb);
			return;
		}
		// File
		if (object instanceof File) {
			_serialize(((File) object).getAbsolutePath(), sb);
			return;
		}
		// String Converter
		if (object instanceof ScriptConvertable) {
			sb.append(((ScriptConvertable) object).serialize());
			return;
		}

		// Struct
		if (object instanceof Struct) {
			_serializeStruct((Struct) object, sb);
			return;
		}
		// Map
		if (object instanceof Map) {
			_serializeMap((Map) object, sb);
			return;
		}
		// Array
		if (object instanceof Array) {
			_serializeArray((Array) object, sb);
			return;
		}
		// List
		if (object instanceof List) {
			_serializeList((List) object, sb);
			return;
		}
		throw CFMLEngineFactory.getInstance().getExceptionUtil()
				.createApplicationException("type [" + CFMLEngineFactory.getInstance().getCastUtil().toTypeName(object) + "] is not upported");

	}

	private void _serializeTimeSpan(TimeSpan ts, StringBuilder sb) throws PageException {
		sb.append(indent());
		sb.append(ts.castToDoubleValue());

	}

	/**
	 * serialize an Object to his literal Format
	 * 
	 * @param object Object to serialize
	 * @param serializeQueryByColumns
	 * @return serialized wddx package
	 * @throws ConverterException
	 * @throws PageException
	 */
	public String serialize(Object object) throws PageException {
		StringBuilder sb = new StringBuilder(256);
		_serialize(object, sb);
		return sb.toString();
	}

	/**
	 * @return return current blockquote
	 */

	private void right() {
		level++;
	}

	private void left() {
		level--;
	}

	private String indent() {
		if (level == 0) return "";

		switch (level) {
		case 1:
			return indent1;
		case 2:
			return indent2;
		case 3:
			return indent3;
		case 4:
			return indent4;
		case 5:
			return indent5;
		case 6:
			return indent6;
		case 7:
			return indent7;
		case 8:
			return indent8;
		case 9:
			return indent9;
		case 10:
			return indent10;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < level; i++) {
			sb.append(indent1);
		}
		return sb.toString();
	}

	public static String escapeJS(String str) {
		char quotesUsed = '"';

		char[] arr = str.toCharArray();
		StringBuilder rtn = new StringBuilder(arr.length);
		rtn.append(quotesUsed);

		for (int i = 0; i < arr.length; i++) {
			switch (arr[i]) {
			case '\\':
				rtn.append("\\\\");
				break;
			case '\n':
				rtn.append("\\n");
				break;
			case '\r':
				rtn.append("\\r");
				break;
			case '\f':
				rtn.append("\\f");
				break;
			case '\b':
				rtn.append("\\b");
				break;
			case '\t':
				rtn.append("\\t");
				break;
			case '"':
				if (quotesUsed == '"') rtn.append("\\\"");
				else rtn.append('"');
				break;
			case '\'':
				if (quotesUsed == '\'') rtn.append("\\\'");
				else rtn.append('\'');
				break;
			case '/':
				// escape </script>
				if (i > 0 && arr[i - 1] == '<' && i + 1 < arr.length && arr[i + 1] == 's' && i + 2 < arr.length && arr[i + 2] == 'c' && i + 3 < arr.length && arr[i + 3] == 'r'
						&& i + 4 < arr.length && arr[i + 4] == 'i' && i + 5 < arr.length && arr[i + 5] == 'p' && i + 6 < arr.length && arr[i + 6] == 't' && i + 7 < arr.length
						&& (Character.isWhitespace(arr[i + 7]) || arr[i + 7] == '>')

				) {
					rtn.append("\\/");
					break;
				}

			default:
				if (Character.isISOControl(arr[i]) || (arr[i] >= 128)) {
					if (arr[i] < 0x10) rtn.append("\\u000");
					else if (arr[i] < 0x100) rtn.append("\\u00");
					else if (arr[i] < 0x1000) rtn.append("\\u0");
					else rtn.append("\\u");
					rtn.append(Integer.toHexString(arr[i]));
				}
				else {
					rtn.append(arr[i]);
				}
				break;
			}
		}
		return rtn.append(quotesUsed).toString();
	}

	private static class JSONDateFormat {

		public static final String PATTERN_CF = "MMMM, dd yyyy HH:mm:ss Z";
		public static final String PATTERN_ISO8601 = "yyyy-MM-dd'T'HH:mm:ssZ"; // preferred pattern for json

		private static Map<String, SoftReference<DateFormat>> map = new ConcurrentHashMap<String, SoftReference<DateFormat>>();
		// private static DateFormat format=null;
		private static Locale locale = Locale.ENGLISH;
		private final static Object sync = new Object();

		public static String format(Date date, TimeZone tz, String pattern) {
			String id = locale.hashCode() + "-" + tz.getID();
			synchronized (sync) {
				SoftReference<DateFormat> tmp = map.get(id);
				DateFormat format = tmp == null ? null : tmp.get();
				if (format == null) {
					format = new SimpleDateFormat(pattern, locale);
					format.setTimeZone(tz);
					map.put(id, new SoftReference<DateFormat>(format));
				}
				return format.format(date);
			}
		}
	}

	public static void main(String[] args) throws PageException, ConverterException {
		Map sub = new HashMap<>();
		sub.put("str", "Sub");
		List list = new ArrayList<>();
		list.add("aaa");
		list.add(123);
		list.add(true);
		Map map = new HashMap<>();
		map.put("str", "Susi");
		map.put("nbr", 1223);
		map.put("bol", false);
		map.put("map", sub);
		map.put("lst", list);

		JSONSerializer s = new JSONSerializer(null);
		print.e(s.serialize(map));

	}
}