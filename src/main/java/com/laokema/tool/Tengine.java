//Developed by @mario 1.0.20220211
package com.laokema.tool;

import com.alibaba.fastjson.*;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.web.context.request.*;
import javax.servlet.http.*;
import java.io.*;
import java.math.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public class Tengine {

	private final Map<String, Object> data = new HashMap<>();
	static boolean cacheEnabled;
	static String cacheDir;
	static String cacheSplitChar; //数据MD5、模板文件SHA1、模板内容的分隔符
	static String runtimeDir;
	static String rootPath;

	static {
		try {
			Properties properties = new Properties();
			properties.load(DB.class.getClassLoader().getResourceAsStream("application.properties"));
			cacheEnabled = properties.getProperty("sdk.mvc.view.cache.enabled").equalsIgnoreCase("true");
			cacheDir = properties.getProperty("sdk.mvc.view.cache-dir");
			cacheSplitChar = properties.getProperty("sdk.mvc.view.cache-split-char");
			runtimeDir = properties.getProperty("sdk.runtime.dir", "/runtime");
			if (cacheDir.length() == 0) cacheDir = "templates_c";
			if (cacheSplitChar.length() == 0) cacheSplitChar = "!@#$%^&*(^^^^^^%$#";
			if (runtimeDir.length() == 0) runtimeDir = "/runtime";
			ApplicationHome ah = new ApplicationHome(Tengine.class);
			rootPath = ah.getSource().getParentFile().getPath();
		} catch (IOException e) {
			System.out.println("获取配置文件失败：" + e.getMessage());
			e.printStackTrace();
		}
	}

	public void assign(String key, Object value) {
		this.data.put(key, value);
	}

	public void assigns(Map<String, Object> data) {
		for (String key : data.keySet()) {
			this.data.put(key, data.get(key));
		}
	}

	public String analysis(String templateFile) {
		if (!new File(templateFile).exists()) throw new IllegalArgumentException("\nTemplate file is not exist:\n" + templateFile);
		String cachePath = rootPath + runtimeDir + "/" + cacheDir;
		String cacheFile = md5(templateFile);
		String cacheFilePath = cachePath + "/" + cacheFile;
		if (cacheEnabled) {
			File file = new File(cacheFilePath);
			if (file.exists()) {
				try {
					int len;
					byte[] buffer = new byte[1024 * 10];
					StringBuilder sbf = new StringBuilder();
					FileInputStream ips = new FileInputStream(file);
					while ((len = ips.read(buffer)) != -1) {
						sbf.append(new String(buffer, 0, len));
					}
					ips.close();
					String[] param = sbf.toString().split(cacheSplitChar.replaceAll("([\\^$?*+(\\[\\\\])", "\\\\$1"));
					if (param[0].equals(md5(JSON.toJSONString(this.data))) && param[1].equals(sha1(templateFile)) && param.length > 2) return param[2];
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		String html = analysis(templateFile, 0);
		if (cacheEnabled) {
			File paths = new File(cachePath);
			if (!paths.exists()) {
				if (!paths.mkdirs()) throw new IllegalArgumentException("\nFile path create fail:\n" + cachePath);
			}
			File file = new File(cacheFilePath);
			try {
				FileWriter fileWritter = new FileWriter(file);
				fileWritter.write(md5(JSON.toJSONString(this.data)) + cacheSplitChar + sha1(templateFile) + cacheSplitChar + (html == null ? "" : html));
				fileWritter.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return html;
	}
	public String analysis(String templateFile, int level) {
		File file = new File(templateFile);
		if (!file.exists()) throw new IllegalArgumentException("\nFile is not exist:\n" + templateFile);
		String dir = file.getParent();
		try {
			FileInputStream ips = new FileInputStream(file);
			StringBuilder sbf = new StringBuilder();
			int len;
			byte[] buffer = new byte[1024 * 10];
			while ((len = ips.read(buffer)) != -1) {
				sbf.append(new String(buffer, 0, len));
			}
			ips.close();

			//{include file=""}
			Matcher matcher = Pattern.compile("\\{include file=\"([^\"]+)\"\\s*}").matcher(sbf.toString());
			StringBuffer html = new StringBuffer();
			while (matcher.find()) {
				String ret = analysis(dir + (matcher.group(1).startsWith("/") ? matcher.group(1) : "/" + matcher.group(1)), level + 1);
				matcher.appendReplacement(html, ret.replaceAll("([$\\\\])", "\\\\$1"));
			}
			matcher.appendTail(html);

			return analysis(html);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	public String analysis(StringBuffer html) {
		return analysis(html, 0);
	}
	public String analysis(StringBuffer html, int level) {
		Map<String, String> originMap = new HashMap<>();
		Matcher matcher = Pattern.compile("\\{origin\\s*}([\\s\\S]+?)\\{/origin\\s*}").matcher(html.toString());
		html = new StringBuffer();
		int originIndex = 0;
		while (matcher.find()) {
			String key = "{==originIndex:" + originIndex + "==}";
			originMap.put(key, matcher.group(1));
			matcher.appendReplacement(html, key.replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);

		Map<String, String[]> forMap = new HashMap<>();
		matcher = Pattern.compile("\\{for:([^\\s]+)\\s+([^\\s]+)\\s+to\\s+([^\\s|}]+?)(\\s+step\\s+(\\d+))?\\s*}([\\s\\S]+?)\\{/for:\\1\\s*}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			String key = "{==forKey:" + matcher.group(1) + "==}";
			forMap.put(key, new String[]{matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(5), matcher.group(6)});
			matcher.appendReplacement(html, key.replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);

		Map<String, String[]> foreachMap = new HashMap<>();
		matcher = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)(\\s+key\\s*=\\s*(\\w+))?\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			String key = "{==foreachKey:" + matcher.group(1) + "==}";
			foreachMap.put(key, new String[]{matcher.group(1), matcher.group(2), matcher.group(4), matcher.group(5)});
			matcher.appendReplacement(html, key.replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);

		//{if aa==bb [&& xx!=yy]}content{/if}
		html = parseIf(html);

		//{for:v 0 to 5 [step 2]}{$v}{/for:v}
		matcher = Pattern.compile("\\{==forKey:([^=]+)==}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			if (forMap.get(matcher.group(0)) == null) {
				matcher.appendReplacement(html, "");
				continue;
			}
			String[] param = forMap.get(matcher.group(0));
			Object start = parse(param[1]);
			Object end = parse(param[2]);
			Object step = param[3] == null ? 1 : parse(param[3]);
			if ((start instanceof Float) || (start instanceof Double)) start = Double.valueOf(String.valueOf(start)).intValue();
			if ((end instanceof Float) || (end instanceof Double)) end = Double.valueOf(String.valueOf(end)).intValue();
			if ((step instanceof Float) || (step instanceof Double)) step = Double.valueOf(String.valueOf(step)).intValue();
			if (((int) end) < ((int) start)) {
				int tmp = (int) end;
				end = start;
				start = tmp;
			}
			StringBuilder ret = new StringBuilder();
			for (int i = ((int) start); i <= ((int) end); i += ((int) step)) {
				String content = param[4].replaceAll("\\{\\$" + param[0] + "\\s*}", String.valueOf(i));
				StringBuffer sb = parseIf(new StringBuffer(content));
				ret.append(sb);
			}
			matcher.appendReplacement(html, ret.toString().replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);

		//{foreach:$rs item=g [key=i]}{$g.name}{$i}{/foreach:$rs}
		matcher = Pattern.compile("\\{==foreachKey:([^=]+)==}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			if (foreachMap.get(matcher.group(0)) == null) {
				matcher.appendReplacement(html, "");
				continue;
			}
			String[] param = foreachMap.get(matcher.group(0));
			Object obj = parse(param[0]);
			if (obj == null) {
				matcher.appendReplacement(html, "");
				continue;
			}
			StringBuilder ret = new StringBuilder();
			String indexKey = param[2] == null ? "index" : param[2];
			if (obj instanceof List) {
				try {
					for (int i = 0; i < (int) obj.getClass().getMethod("size").invoke(obj); i++) {
						Object o = obj.getClass().getMethod("get", int.class).invoke(obj, i);
						String content = param[3].replaceAll("\\{\\$" + indexKey + "\\s*}", String.valueOf(i));
						StringBuffer sb = parseIf(new StringBuffer(content), o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (obj.getClass().isArray()) {
				if (obj instanceof String[]) {
					for (int i = 0; i < ((String[]) obj).length; i++) {
						Object o = ((String[]) obj)[i];
						String content = param[3].replaceAll("\\{\\$" + indexKey + "\\s*}", String.valueOf(i));
						StringBuffer sb = parseIf(new StringBuffer(content), o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (obj instanceof Integer[]) {
					for (int i = 0; i < ((Integer[]) obj).length; i++) {
						Object o = ((Integer[]) obj)[i];
						String content = param[3].replaceAll("\\{\\$" + indexKey + "\\s*}", String.valueOf(i));
						StringBuffer sb = parseIf(new StringBuffer(content), o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (obj instanceof Long[]) {
					for (int i = 0; i < ((Long[]) obj).length; i++) {
						Object o = ((Long[]) obj)[i];
						String content = param[3].replaceAll("\\{\\$" + indexKey + "\\s*}", String.valueOf(i));
						StringBuffer sb = parseIf(new StringBuffer(content), o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (obj instanceof Float[]) {
					for (int i = 0; i < ((Float[]) obj).length; i++) {
						Object o = ((Float[]) obj)[i];
						String content = param[3].replaceAll("\\{\\$" + indexKey + "\\s*}", String.valueOf(i));
						StringBuffer sb = parseIf(new StringBuffer(content), o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (obj instanceof Double[]) {
					for (int i = 0; i < ((Double[]) obj).length; i++) {
						Object o = ((Double[]) obj)[i];
						String content = param[3].replaceAll("\\{\\$" + indexKey + "\\s*}", String.valueOf(i));
						StringBuffer sb = parseIf(new StringBuffer(content), o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else {
					throw new IllegalArgumentException(String.valueOf(ret.toString().getClass()));
				}
			}
			matcher.appendReplacement(html, ret.toString().replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);

		//{variable} {trim(variable)}
		html = new StringBuffer(parseVariable(html.toString()));

		//{origin}
		matcher = Pattern.compile("\\{==originIndex:(.+?)==}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(0);
			String content = "";
			if (originMap.get(key) != null) content = originMap.get(key);
			matcher.appendReplacement(html, content);
		}
		matcher.appendTail(html);

		//repeat once to prevent missing
		if (level == 0) html = new StringBuffer(analysis(html, level + 1));

		return html.toString();
	}

	private StringBuffer parseIf(StringBuffer html) {
		return parseIf(html, this.data, null);
	}
	private StringBuffer parseIf(StringBuffer html, Object obj, String item) {
		return parseIf(html, obj, item, false);
	}
	private StringBuffer parseIf(StringBuffer html, Object obj, String item, boolean nonMark) {
		Matcher matcher;
		if (nonMark) {
			matcher = Pattern.compile("\\{if(\\s+)([^}]+)}([\\s\\S]*?)\\{/if\\s*}").matcher(html.toString());
		} else {
			matcher = Pattern.compile("\\{if(:[^\\s]+)\\s+([^}]+)}([\\s\\S]*?)\\{/if\\1\\s*}").matcher(html.toString());
		}
		html = new StringBuffer();
		while (matcher.find()) {
			String mark = matcher.group(1) == null ? "" : matcher.group(1).trim();
			String content = parseIfContent(matcher.group(3), mark);
			Matcher logicMatcher = Pattern.compile("^(.+?)((&&|\\|\\||and|or|&amp;&amp;)\\s+.+)?$").matcher(matcher.group(2).trim());
			if (logicMatcher.find()) {
				boolean logic = parseIfJudge(logicMatcher.group(1), obj, item);
				if (logicMatcher.group(2) != null) logic = parseIfLogic(logic, logicMatcher.group(2), obj, item);
				if (!logic) content = parseIfElse(matcher.group(3), mark, obj, item);
			}
			if (content.length() > 0) {
				StringBuffer sb = parseIf(new StringBuffer(content));
				content = sb.toString();
			}
			matcher.appendReplacement(html, content.replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);
		if (!nonMark) html = parseIf(html, obj, item, true);
		return html;
	}
	private boolean parseIfJudge(String str, Object obj, String item) {
		str = str.trim();
		boolean res = false;
		Matcher matcher = Pattern.compile("^(.+?)(==|!=|<>|&lt;&gt;|<=|&lt;=|>=|&gt;=|<|&lt;|>|&gt;|=)(.+)$").matcher(str);
		if (matcher.find()) {
			Object leftRet = parse(matcher.group(1), obj, item);
			Object rightRet = parse(matcher.group(3), obj, item);
			boolean isNumber = (leftRet instanceof Integer) || (leftRet instanceof Long) || (leftRet instanceof Float) || (leftRet instanceof Double) || (leftRet instanceof BigDecimal) ||
					(rightRet instanceof Integer) || (rightRet instanceof Long) || (rightRet instanceof Float) || (rightRet instanceof Double) || (rightRet instanceof BigDecimal);
			switch (matcher.group(2)) {
				case "==":case "=":
					//byte,short,char,int,long,float,double,boolean
					if (isNumber) {
						res = Float.parseFloat(String.valueOf(leftRet)) == Float.parseFloat(String.valueOf(rightRet));
					} else {
						res = leftRet.equals(rightRet);
					}
					break;
				case "!=":case "<>":case "&lt;&gt;":
					if (isNumber) {
						res = Float.parseFloat(String.valueOf(leftRet)) != Float.parseFloat(String.valueOf(rightRet));
					} else {
						res = !leftRet.equals(rightRet);
					}
					break;
				case "<=":case "&lt;=":
					if ((leftRet instanceof Boolean) || (rightRet instanceof Boolean)) return false;
					if ((leftRet instanceof String) || (rightRet instanceof String)) return false;
					res = Float.parseFloat(String.valueOf(leftRet)) <= Float.parseFloat(String.valueOf(rightRet));
					break;
				case "<":case "&lt;":
					if ((leftRet instanceof Boolean) || (rightRet instanceof Boolean)) return false;
					if ((leftRet instanceof String) || (rightRet instanceof String)) return false;
					res = Float.parseFloat(String.valueOf(leftRet)) < Float.parseFloat(String.valueOf(rightRet));
					break;
				case ">=":case "&gt;=":
					if ((leftRet instanceof Boolean) || (rightRet instanceof Boolean)) return false;
					if ((leftRet instanceof String) || (rightRet instanceof String)) return false;
					res = Float.parseFloat(String.valueOf(leftRet)) >= Float.parseFloat(String.valueOf(rightRet));
					break;
				case ">":case "&gt;":
					if ((leftRet instanceof Boolean) || (rightRet instanceof Boolean)) return false;
					if ((leftRet instanceof String) || (rightRet instanceof String)) return false;
					res = Float.parseFloat(String.valueOf(leftRet)) > Float.parseFloat(String.valueOf(rightRet));
					break;
			}
		} else {
			boolean isReverse = str.startsWith("!");
			if (isReverse) str = str.substring(1);
			Object ret = parse(str, obj, item);
			if ((ret instanceof Integer) || (ret instanceof Long)) {
				res = Integer.parseInt(String.valueOf(ret)) > 0;
			} else if ((ret instanceof Float) || (ret instanceof Double) || (ret instanceof BigDecimal)) {
				res = Float.parseFloat(String.valueOf(ret)) > 0;
			} else if (ret instanceof Boolean) {
				res = Boolean.TRUE.equals(ret);
			} else if (ret instanceof String) {
				res = ((String) ret).length() > 0;
			} else {
				res = ret != null;
			}
			if (isReverse) res = !res;
		}
		return res;
	}
	private boolean parseIfLogic(boolean logic, String str, Object obj, String item) {
		str = str.trim();
		Matcher matcher = Pattern.compile("^(&&|\\|\\||and|or|&amp;&amp;)(\\s+.+?)((&&|\\|\\||and|or|&amp;&amp;)\\s+.+)?$").matcher(str);
		if (matcher.find()) {
			switch (matcher.group(1)) {
				case "&&":case "and":case "&amp;&amp;": {
					if (!logic) return false;
					boolean judge = parseIfJudge(matcher.group(2), obj, item);
					if (judge) {
						if (matcher.group(3) != null) judge = parseIfLogic(judge, matcher.group(3), obj, item);
						return judge;
					}
					break;
				}
				case "||":case "or": {
					boolean judge = parseIfJudge(matcher.group(2), obj, item);
					if (judge) return true;
					if (matcher.group(3) != null) judge = parseIfLogic(judge, matcher.group(3), obj, item);
					if (judge) return true;
					break;
				}
			}
		}
		return false;
	}
	private String parseIfElse(String str, String mark, Object obj, String item) {
		boolean nonElseIf = true;
		if (mark == null) mark = "";
		Matcher matcher = Pattern.compile("^[\\s\\S]*?\\{else\\s*if"+mark+"\\s+([^}]+)}([\\s\\S]*)$").matcher(str);
		if (!matcher.find()) {
			nonElseIf = false;
			matcher = Pattern.compile("^[\\s\\S]*?\\{else"+mark+"(\\s*)}([\\s\\S]*)$").matcher(str);
		}
		if (!matcher.find()) return "";
		String content = parseIfContent(matcher.group(2), mark);
		if (!nonElseIf) return content;
		Matcher logicMatcher = Pattern.compile("^(.+?)((&&|\\|\\||and|or|&amp;&amp;)\\s+.+)?$").matcher(matcher.group(1).trim());
		if (logicMatcher.find()) {
			boolean logic = parseIfJudge(logicMatcher.group(1), obj, item);
			if (logicMatcher.group(2) != null) logic = parseIfLogic(logic, logicMatcher.group(2), obj, item);
			if (!logic) content = parseIfElse(matcher.group(2), mark, obj, item);
		}
		return content;
	}
	private String parseIfContent(String str, String mark) {
		if (mark == null) mark = "";
		Matcher matcher = Pattern.compile("^([\\s\\S]*?)(\\{else"+mark+"[\\s\\S]+)?$").matcher(str);
		if (matcher.find()) return matcher.group(1);
		return "";
	}

	private String parseVariable(String str) {
		return parseVariable(str, this.data, null);
	}
	private String parseVariable(String str, Object obj, String item) {
		Matcher matcher = Pattern.compile("\\{([^\\s]+?)\\s*}").matcher(str);
		StringBuffer html = new StringBuffer();
		while (matcher.find()) {
			Object ret = parse(matcher.group(1), obj, item);
			if (ret == null) ret = "";
			matcher.appendReplacement(html, String.valueOf(ret).replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);
		return html.toString();
	}
	
	private Object parse(String str) {
		return parse(str, this.data, null);
	}
	private Object parse(String str, Object obj, String item) {
		str = str.trim();
		Object ret = "";
		if (str.startsWith("$")) {
			String field = str.substring(1);
			if (obj instanceof Map) {
				Map<String, Object> o = JSONObject.parseObject(JSON.toJSONString(obj));
				if (field.contains(".")) {
					String[] keys = field.split("\\.");
					if (o.get(keys[0]) != null) {
						Map<String, Object> g = JSONObject.parseObject(JSON.toJSONString(o.get(keys[0])));
						if (g.get(keys[1]) != null) ret = g.get(keys[1]);
					} else if (item != null) {
						if (keys[0].equals(item)) {
							if (o.get(keys[1]) != null) ret = o.get(keys[1]);
						}
					}
				} else {
					if (o.get(field) != null) ret = o.get(field);
				}
			} else {
				ret = obj;
			}
		} else if (str.startsWith("'") || str.startsWith("\"")) {
			Matcher matcher = Pattern.compile("^(['\"])(.+?)\\1$").matcher(str);
			if (matcher.find()) ret = matcher.group(2);
		} else if (str.startsWith("count(")) {
			Matcher matcher = Pattern.compile("^count\\(([^)]+)\\)$").matcher(str);
			if (!matcher.find()) return 0;
			ret = parse(matcher.group(1), obj, item);
			if (ret == null) return 0;
			if (ret instanceof List) {
				try {
					ret = ret.getClass().getMethod("size").invoke(ret);
				} catch (Exception e) {
					e.printStackTrace();
					return 0;
				}
			} else if (ret.getClass().isArray()) {
				if (ret instanceof String[]) {
					return Arrays.asList((String[]) ret).size();
				} else if (ret instanceof Integer[]) {
					return Arrays.asList((Integer[]) ret).size();
				} else if (ret instanceof Long[]) {
					return Arrays.asList((Long[]) ret).size();
				} else if (ret instanceof Float[]) {
					return Arrays.asList((Float[]) ret).size();
				} else if (ret instanceof Double[]) {
					return Arrays.asList((Double[]) ret).size();
				} else {
					throw new IllegalArgumentException(String.valueOf(ret.getClass()));
				}
			} else if (ret instanceof String) {
				ret = ((String) ret).length();
			} else {
				ret = 0;
			}
		} else if (str.startsWith("strlen(")) {
			Matcher matcher = Pattern.compile("^strlen\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				try {
					Object r = parse(matcher.group(1), obj, item);
					if (!(r instanceof String)) return 0;
					ret = ((String) r).length();
				} catch (Exception e) {
					e.printStackTrace();
					ret = 0;
				}
			}
		} else if (str.startsWith("trim(")) {
			Matcher matcher = Pattern.compile("^trim\\(([^,]+)(,\\s*([^)]+))?\\)$").matcher(str);
			if (matcher.find()) {
				String characters = " ";
				str = (String) parse(matcher.group(1), obj, item);
				if (matcher.group(2) != null) characters = matcher.group(3);
				ret = trim(str, characters);
			}
		} else if (str.startsWith("urlencode(")) {
			Matcher matcher = Pattern.compile("^urlencode\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				try {
					str = (String) parse(matcher.group(1), obj, item);
					ret = URLEncoder.encode(str, "UTF-8");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else if (str.startsWith("number_format(")) {
			Matcher matcher = Pattern.compile("^number_format\\(([^,]+),\\s*([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				double number = Double.parseDouble(String.valueOf(parse(matcher.group(1), obj, item)));
				int digits = (int) Float.parseFloat(String.valueOf(parse(matcher.group(2), obj, item)));
				ret = String.format("%."+digits+"f", number);
			}
		} else if (str.startsWith("in_array(")) {
			Matcher matcher = Pattern.compile("^in_array\\(([^,]+),\\s*([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				Object element = parse(matcher.group(1), obj, item);
				Object array = parse(matcher.group(2), obj, item);
				if (element == null || array == null) return Boolean.FALSE;
				if (array instanceof List) {
					try {
						return array.getClass().getMethod("contains", Object.class).invoke(array, element);
					} catch (Exception e) {
						e.printStackTrace();
						return Boolean.FALSE;
					}
				} else if (array.getClass().isArray()) {
					if (array instanceof String[]) {
						return Arrays.asList((String[]) array).contains((String) element);
					} else if (array instanceof Integer[]) {
						return Arrays.asList((Integer[]) array).contains((int) element);
					} else if (array instanceof Long[]) {
						return Arrays.asList((Long[]) array).contains((long) element);
					} else if (array instanceof Float[]) {
						return Arrays.asList((Float[]) array).contains((float) element);
					} else if (array instanceof Double[]) {
						return Arrays.asList((Double[]) array).contains((double) element);
					} else {
						throw new IllegalArgumentException(String.valueOf(array.getClass()));
					}
				}
				return Boolean.FALSE;
			}
			ret = Boolean.FALSE;
		} else if (isNumeric(str)) {
			ret = Float.parseFloat(str);
		} else if (str.equalsIgnoreCase("true")) {
			return Boolean.TRUE;
		} else if (str.equalsIgnoreCase("false")) {
			return Boolean.FALSE;
		} else if (str.startsWith("sys.")) {
			ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			HttpServletRequest request = Objects.requireNonNull(servletRequestAttributes).getRequest();
			String[] sys = str.split("\\.");
			switch (sys[1].toLowerCase()) {
				case "time": {
					ret = new Date().getTime() / 1000;
					break;
				}
				case "get": {
					if (sys.length < 3) return "";
					ret = request.getParameter(sys[2]);
					if (ret == null) return "";
					break;
				}
				case "param": {
					if (sys.length < 3) return "";
					String uri = request.getRequestURI();
					if (!uri.matches("^/\\w+/\\w+/\\w+/\\w+.*")) return "";
					uri = uri.replaceAll("^/\\w+/\\w+/\\w+/", "");
					String[] params = uri.split("/");
					if (sys[2].matches("^-?\\d+$")) {
						int index = Integer.parseInt(sys[2]);
						if (index >= params.length || index < -params.length) return "";
						if (index < 0) index += params.length;
						ret = params[index];
					} else {
						for (int i = 0; i < params.length; i+=2) {
							if (params[i].equals(sys[2])) {
								if (i < params.length - 1) ret = params[i + 1];
								break;
							}
						}
					}
					if (ret == null) return "";
					break;
				}
				case "path": {
					if (sys.length < 3) return "";
					String uri = trim(request.getRequestURI(), "/");
					String[] params = uri.split("/");
					if (!sys[2].matches("^-?\\d+$")) return "";
					int index = Integer.parseInt(sys[2]);
					if (index >= params.length || index < -params.length) return "";
					if (index < 0) index += params.length;
					ret = params[index];
					if (ret == null) return "";
					break;
				}
				case "session": {
					if (sys.length < 3) return "";
					ret = request.getSession().getAttribute(sys[2]);
					if (ret == null) return "";
					break;
				}
				case "cookie": {
					if (sys.length < 3) return "";
					Cookie[] cookies = request.getCookies();
					if (cookies == null) return "";
					try {
						for (Cookie cookie : cookies) {
							if (cookie.getName().equals(sys[2])) {
								ret = URLDecoder.decode(cookie.getValue(), "UTF-8");
								break;
							}
						}
					} catch (Exception e) {
						return "";
					}
					if (ret == null) return "";
					break;
				}
				case "server": {
					if (sys.length < 3) return "";
					Properties properties = System.getProperties();
					ret = properties.get(sys[2]);
					if (ret == null) return "";
					break;
				}
				case "header": {
					if (sys.length < 3) return "";
					Enumeration<String> headerNames = request.getHeaderNames();
					while (headerNames.hasMoreElements()) {
						String key = headerNames.nextElement();
						if (key.equals(sys[2])) {
							ret = request.getHeader(key);
							break;
						}
					}
					break;
				}
			}
		}
		return ret;
	}

	private String trim(String str, String symbol) {
		return str.replaceAll("(^" + symbol.replaceAll("([|\\[]\\(\\)\\^\\$\\\\])", "\\\\$1") + "|" + symbol.replaceAll("([|\\[]\\(\\)\\^\\$\\\\])", "\\\\$1") + "$)", "");
	}
	private boolean isNumeric(Object str) {
		if ((str instanceof Integer) || (str instanceof Float) || (str instanceof Double)) return true;
		Pattern pattern = Pattern.compile("^[-+]?[\\d]+$");
		return pattern.matcher((CharSequence) str).matches();
	}
	private String md5(String str) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(str.getBytes());
			return new BigInteger(1, md.digest()).toString(16);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	private String sha1(String filePath) {
		try {
			FileInputStream in = new FileInputStream(filePath);
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] buffer = new byte[1024 * 1024 * 10];
			int len;
			while ((len = in.read(buffer)) > 0) {
				md.update(buffer, 0, len);
			}
			in.close();
			StringBuilder sha1 = new StringBuilder(new BigInteger(1, md.digest()).toString(16));
			int length = 40 - sha1.length();
			if (length > 0) {
				for (int i = 0; i < length; i++) {
					sha1.insert(0, "0");
				}
			}
			return sha1.toString();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
