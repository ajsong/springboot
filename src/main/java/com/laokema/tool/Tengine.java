//Developed by @mario 1.4.20220303
package com.laokema.tool;

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.web.context.request.*;
import javax.servlet.http.*;
import java.io.*;
import java.math.*;
import java.net.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

public class Tengine {

	static boolean cacheEnabled;
	static String cacheDir;
	static String cacheSplitChar; //数据MD5、模板文件SHA1、模板内容的分隔符
	static String runtimeDir;
	static String rootPath;

	private final Map<String, Object> data = new HashMap<>();
	private Class<?> clazz;

	static {
		try {
			Properties properties = new Properties();
			properties.load(Tengine.class.getClassLoader().getResourceAsStream("application.properties"));
			cacheEnabled = properties.getProperty("sdk.mvc.view.cache.enabled").equalsIgnoreCase("true");
			cacheDir = properties.getProperty("sdk.mvc.view.cache-dir");
			cacheSplitChar = properties.getProperty("sdk.mvc.view.cache-split-char");
			runtimeDir = properties.getProperty("sdk.runtime.dir");
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

	private String trim(String str, String symbol) {
		return str.replaceAll("(^" + symbol.replaceAll("([|\\[]\\(\\)\\^\\$\\\\])", "\\\\$1") + "|" + symbol.replaceAll("([|\\[]\\(\\)\\^\\$\\\\])", "\\\\$1") + "$)", "");
	}
	private boolean isNumeric(Object str) {
		if ((str instanceof Integer) || (str instanceof Float) || (str instanceof Double)) return true;
		Pattern pattern = Pattern.compile("^[-+]?\\d+(\\.\\d+)?$");
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
	private void writeError(String content) {
		String path = rootPath + runtimeDir;
		File filePath = new File(path);
		if (!filePath.exists()) {
			if (!filePath.mkdirs()) throw new IllegalArgumentException("FILE PATH CREATE FAIL:\n" + path);
		}
		try {
			String format = "yyyy-MM-dd HH:mm:ss".replace("m", "M").replace("h", "H").replace("n", "m");
			SimpleDateFormat dateformat = new SimpleDateFormat(format);
			FileWriter fileWritter = new FileWriter(path + "/error.txt", true);
			fileWritter.write(dateformat.format(new Date()) + "\n" + content);
			fileWritter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setClazzForCustom(Class<?> clazz) {
		this.clazz = clazz;
	}

	public void assign(String key, Object value) {
		this.data.put(key, value);
	}
	public void assigns(Map<String, Object> data) {
		this.data.putAll(data);
	}

	public String analysis(String templatePath, boolean isExcludeCache) {
		File template = new File(templatePath);
		if (!template.exists()) throw new IllegalArgumentException("TEMPLATE FILE IS NOT EXIST:\n" + templatePath);
		String dataMd5 = md5(JSON.toJSONString(this.data, SerializerFeature.WriteMapNullValue));
		String cachePath = rootPath + runtimeDir + "/" + cacheDir;
		String cacheFile = template.getName() + "." + dataMd5;
		String cacheFilePath = cachePath + "/" + cacheFile;
		if (cacheEnabled && !isExcludeCache) {
			File file = new File(cacheFilePath);
			if (file.exists()) {
				try {
					int len;
					byte[] buffer = new byte[1024 * 2];
					StringBuilder sbf = new StringBuilder();
					FileInputStream ips = new FileInputStream(file);
					while ((len = ips.read(buffer)) != -1) sbf.append(new String(buffer, 0, len));
					ips.close();
					String[] param = sbf.toString().split(cacheSplitChar.replaceAll("([\\^$?*+(\\[\\\\])", "\\\\$1"));
					if (param[0].equals(dataMd5) && param[1].equals(sha1(templatePath)) && param.length > 2) return param[2];
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}
		}
		String html = analysis(templatePath, 0);
		if (cacheEnabled && !isExcludeCache) {
			File paths = new File(cachePath);
			if (!paths.exists()) {
				if (!paths.mkdirs()) throw new IllegalArgumentException("FILE PATH CREATE FAIL:\n" + cachePath);
			}
			File file = new File(cacheFilePath);
			try {
				FileWriter fileWritter = new FileWriter(file);
				fileWritter.write(dataMd5 + cacheSplitChar + sha1(templatePath) + cacheSplitChar + (html == null ? "" : html));
				fileWritter.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return html;
	}
	public String analysis(String templatePath, int level) {
		File file = new File(templatePath);
		if (!file.exists()) throw new IllegalArgumentException("FILE IS NOT EXIST:\n" + templatePath);
		String dir = file.getParent();
		try {
			FileInputStream ips = new FileInputStream(file);
			StringBuilder sbf = new StringBuilder();
			int len;
			byte[] buffer = new byte[1024 * 2];
			while ((len = ips.read(buffer)) != -1) {
				sbf.append(new String(buffer, 0, len));
			}
			ips.close();

			//{include file=""}
			Matcher matcher = Pattern.compile("\\{include file=\"([^\"]+)\"\\s*}").matcher(sbf.toString());
			StringBuffer html = new StringBuffer();
			while (matcher.find()) {
				String path;
				if (matcher.group(1).startsWith("/")) {
					// /file.html
					for (int i = 0; i < level; i++) dir = new File(dir).getParent();
					path = dir + matcher.group(1);
				} else if (matcher.group(1).startsWith("./")) {
					// ./file.html
					path = dir + "/" + matcher.group(1).replace("./", "");
				} else if (matcher.group(1).startsWith("../")) {
					// ../file.html
					Matcher m = Pattern.compile("(\\.\\./)").matcher(matcher.group(1));
					while (m.find()) dir = new File(dir).getParent();
					path = dir + "/" + matcher.group(1).replace("../", "");
				} else if (matcher.group(1).startsWith("~")) {
					// ~file.html
					path = rootPath + "/" + matcher.group(1).substring(1);
				} else {
					// file.html
					path = dir + "/" + matcher.group(1);
				}
				String ret = analysis(path, level + 1);
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
		Matcher matcher = Pattern.compile("\\{(origin|literal)\\s*}([\\s\\S]+?)\\{/\\1\\s*}").matcher(html.toString());
		html = new StringBuffer();
		int originIndex = 0;
		while (matcher.find()) {
			String key = "{==originIndex:" + originIndex + "==}";
			originMap.put(key, matcher.group(2));
			matcher.appendReplacement(html, key.replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);

		Map<String, String[]> forMap = new HashMap<>();
		matcher = Pattern.compile("\\{for:([^\\s]+)\\s+([^\\s]+)\\s+to\\s+([^\\s|}]+?)(\\s+step\\s*=\\s*(\\d+))?\\s*}([\\s\\S]+?)\\{/for:\\1\\s*}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			String key = "{==forKey:" + matcher.group(1).replace("->", ".") + "==}";
			forMap.put(key, new String[]{matcher.group(1).replace("->", "."), matcher.group(2), matcher.group(3), matcher.group(5), matcher.group(6)});
			matcher.appendReplacement(html, key.replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);

		Map<String, String[]> foreachMap = new HashMap<>();
		matcher = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			String key = "{==foreachKey:" + matcher.group(1).replace("->", ".") + "==}";
			foreachMap.put(key, new String[]{matcher.group(1).replace("->", "."), matcher.group(2), matcher.group(3)});
			matcher.appendReplacement(html, key.replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);

		//{switch count($rs)}{case 0}xx{case 1}yy{default}zz{/switch}
		html = parseSwitch(html);

		//{if aa==bb [&& xx!=yy]}content{/if}
		html = parseIf(html);

		//{for:k 0 to 5 [step=2]}{$k}{$k.total}{if $k.first}{if $k.last}{/for:k}
		html = parseFor(forMap, html);

		//{foreach:$rs item=g}{$g.name}{$rs.index}{$rs.iteration}{$rs.total}{if $rs.first}{if $rs.last}{/foreach:$rs}
		html = parseForeach(foreachMap, html);

		//{$title} {trim($g.name)}
		html = new StringBuffer(parseVariable(html.toString()));

		//{origin}{/origin}
		html = parseOrigin(originMap, html);

		String content = html.toString();
		if (content.contains("{switch") || content.contains("{if") || content.contains("{for:") || content.contains("{foreach:") ||
				Pattern.compile("\\{(\\S[^}]+)}").matcher(content).matches()) {
			html = new StringBuffer(analysis(html, level + 1));
		}

		return html.toString();
	}

	private StringBuffer parseSwitch(StringBuffer html) {
		return parseSwitch(html, false);
	}
	private StringBuffer parseSwitch(StringBuffer html, boolean nonMark) {
		Matcher matcher;
		if (nonMark) {
			matcher = Pattern.compile("\\{switch(\\s+)([^}]+)}([\\s\\S]*?)\\{/switch\\s*}").matcher(html.toString());
		} else {
			matcher = Pattern.compile("\\{switch(:[^\\s]+)\\s+([^}]+)}([\\s\\S]*?)\\{/switch\\1\\s*}").matcher(html.toString());
		}
		html = new StringBuffer();
		while (matcher.find()) {
			String mark = matcher.group(1) == null ? "" : matcher.group(1).trim();
			String content = "";
			Object expression = parse(matcher.group(2).replace("->", "."));
			boolean isCase = false;
			Matcher caseMatcher = Pattern.compile("\\{case"+mark+"\\s+([^}]+)}([\\s\\S]+?)(?=\\{case"+mark+"\\s|\\{default"+mark+"\\s*}|$)").matcher(matcher.group(3));
			while (caseMatcher.find()) {
				Object label = parse(caseMatcher.group(1).replace("->", "."));
				boolean res = parseSwitchJudge(expression, label);
				if (res) {
					content = caseMatcher.group(2);
					isCase = true;
					break;
				}
			}
			if (!isCase) {
				Matcher defaultMatcher = Pattern.compile("\\{default"+mark+"\\s*}([\\s\\S]+)$").matcher(matcher.group(3));
				if (defaultMatcher.find()) {
					content = defaultMatcher.group(1);
				}
			}
			matcher.appendReplacement(html, content.replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);
		if (!nonMark) html = parseSwitch(html, true);
		return html;
	}
	private boolean parseSwitchJudge(Object leftRet, Object rightRet) {
		boolean res;
		boolean isNumber = (leftRet instanceof Integer) || (leftRet instanceof Long) || (leftRet instanceof Float) || (leftRet instanceof Double) || (leftRet instanceof BigDecimal) ||
				(rightRet instanceof Integer) || (rightRet instanceof Long) || (rightRet instanceof Float) || (rightRet instanceof Double) || (rightRet instanceof BigDecimal);
		if (leftRet == null && rightRet == null) return true;
		if (leftRet == null || rightRet == null) return false;
		if (isNumber) {
			res = Float.parseFloat(String.valueOf(leftRet)) == Float.parseFloat(String.valueOf(rightRet));
		} else {
			res = leftRet.equals(rightRet);
		}
		return res;
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
			Matcher logicMatcher = Pattern.compile("^(.+?)((&&|\\|\\||and|or|&amp;&amp;)\\s+.+)?$").matcher(matcher.group(2).replace("->", ".").trim());
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
	/*private List<String> parseIfSub(String str) { //elseif、else暂时无法实现
		List<String> list = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		int k = 0;
		char[] arr = str.toCharArray();
		for (int i = 0; i < arr.length; i++) {
			char ch = arr[i];
			if (ch == '{' && i < arr.length - 3) {
				if (arr[i+1] == 'i' && arr[i+2] == 'f') k++;
				if (arr[i+1] == '/' && arr[i+2] == 'i' && arr[i+3] == 'f') k--;
			}
			if (k == 0) {
				list.add(sb.toString());
				sb.delete(0, sb.length());
			} else {
				sb.append(ch);
			}
		}
		if (sb.length()>0) list.add(sb.toString());
		list = list.stream().filter(string -> !string.isEmpty()).collect(Collectors.toList());
		List<String> l = new ArrayList<>(list);
		for (String string : l) {
			Matcher matcher = Pattern.compile("^\\{if[^}]+}([\\s\\S]+)$").matcher(string);
			if (matcher.find() && matcher.group(1).contains("{if")) {
				List<String> items = parseIfSub(matcher.group(1));
				list.addAll(items);
			}
		}
		return list;
	}*/
	private boolean parseIfJudge(String str, Object obj, String item) {
		str = str.trim().replace("->", ".");
		boolean res = false;
		Matcher matcher = Pattern.compile("^(.+?)(==|!=|<>|&lt;&gt;|<=|&lt;=|>=|&gt;=|<|&lt;|>|&gt;|=)(.+)$").matcher(str);
		if (matcher.find()) {
			Object leftRet = parseCompute(matcher.group(1), obj, item);
			Object rightRet = parseCompute(matcher.group(3), obj, item);
			boolean isNumber = (leftRet instanceof Integer) || (leftRet instanceof Long) || (leftRet instanceof Float) || (leftRet instanceof Double) || (leftRet instanceof BigDecimal) ||
					(rightRet instanceof Integer) || (rightRet instanceof Long) || (rightRet instanceof Float) || (rightRet instanceof Double) || (rightRet instanceof BigDecimal);
			switch (matcher.group(2)) {
				case "==":case "=":
					//byte,short,char,int,long,float,double,boolean
					if (leftRet == null && rightRet == null) return true;
					if (leftRet == null || rightRet == null) return false;
					if (isNumber) {
						res = Float.parseFloat(String.valueOf(leftRet)) == Float.parseFloat(String.valueOf(rightRet));
					} else {
						res = leftRet.equals(rightRet);
					}
					break;
				case "!=":case "<>":case "&lt;&gt;":
					if (leftRet == null && rightRet == null) return false;
					if (leftRet == null || rightRet == null) return true;
					if (isNumber) {
						res = Float.parseFloat(String.valueOf(leftRet)) != Float.parseFloat(String.valueOf(rightRet));
					} else {
						res = !leftRet.equals(rightRet);
					}
					break;
				case "<=":case "&lt;=":
					if (leftRet == null || rightRet == null) return false;
					if ((leftRet instanceof Boolean) || (rightRet instanceof Boolean)) return false;
					if ((leftRet instanceof String) || (rightRet instanceof String)) return false;
					res = Float.parseFloat(String.valueOf(leftRet)) <= Float.parseFloat(String.valueOf(rightRet));
					break;
				case "<":case "&lt;":
					if (leftRet == null || rightRet == null) return false;
					if ((leftRet instanceof Boolean) || (rightRet instanceof Boolean)) return false;
					if ((leftRet instanceof String) || (rightRet instanceof String)) return false;
					res = Float.parseFloat(String.valueOf(leftRet)) < Float.parseFloat(String.valueOf(rightRet));
					break;
				case ">=":case "&gt;=":
					if (leftRet == null || rightRet == null) return false;
					if ((leftRet instanceof Boolean) || (rightRet instanceof Boolean)) return false;
					if ((leftRet instanceof String) || (rightRet instanceof String)) return false;
					res = Float.parseFloat(String.valueOf(leftRet)) >= Float.parseFloat(String.valueOf(rightRet));
					break;
				case ">":case "&gt;":
					if (leftRet == null || rightRet == null) return false;
					if ((leftRet instanceof Boolean) || (rightRet instanceof Boolean)) return false;
					if ((leftRet instanceof String) || (rightRet instanceof String)) return false;
					res = Float.parseFloat(String.valueOf(leftRet)) > Float.parseFloat(String.valueOf(rightRet));
					break;
			}
		} else {
			boolean isReverse = str.startsWith("!");
			if (isReverse) str = str.substring(1);
			Object ret = parseCompute(str, obj, item);
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
						if (matcher.group(3) != null) judge = parseIfLogic(true, matcher.group(3), obj, item);
						return judge;
					}
					break;
				}
				case "||":case "or": {
					boolean judge = parseIfJudge(matcher.group(2), obj, item);
					if (judge) return true;
					if (matcher.group(3) != null) judge = parseIfLogic(false, matcher.group(3), obj, item);
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

	private StringBuffer parseFor(Map<String, String[]> map, StringBuffer html) {
		return parseFor(map, html, this.data, null);
	}
	private StringBuffer parseFor(Map<String, String[]> map, StringBuffer html, Object obj, String item) {
		Matcher matcher = Pattern.compile("\\{==forKey:([^=]+)==}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			if (map.get(matcher.group(0)) == null) {
				matcher.appendReplacement(html, "");
				continue;
			}
			String[] param = map.get(matcher.group(0));
			Object start = parseCompute(param[1], obj, item);
			Object end = parseCompute(param[2], obj, item);
			Object step = param[3] == null ? 1 : parseCompute(param[3], obj, item);
			if ((start instanceof Float) || (start instanceof Double)) start = Double.valueOf(String.valueOf(start)).intValue();
			if ((end instanceof Float) || (end instanceof Double)) end = Double.valueOf(String.valueOf(end)).intValue();
			if ((step instanceof Float) || (step instanceof Double)) step = Double.valueOf(String.valueOf(step)).intValue();
			if (((int) end) < ((int) start)) {
				int tmp = (int) end;
				end = start;
				start = tmp;
			}
			StringBuilder ret = new StringBuilder();
			int len = (int) end;
			int total = len - ((int) start);
			for (int i = ((int) start); i < len; i += ((int) step)) {
				String content = param[4].replaceAll("\\{\\$" + param[0] + "\\s*}", String.valueOf(i));
				content = content.replaceAll("\\$" + param[0] + "\\b", String.valueOf(i));
				content = content.replaceAll("\\$" + param[0] + "\\.first\\b", (i == 0 ? "true" : "false"));
				content = content.replaceAll("\\$" + param[0] + "\\.last\\b", (i == len - 1 ? "true" : "false"));
				content = content.replaceAll("\\$" + param[0] + "\\.total\\b", String.valueOf(total));
				boolean hasChild = false;
				StringBuffer sb = new StringBuffer(content);
				if (content.contains("{for:")) {
					hasChild = true;
					Matcher m = Pattern.compile("\\{for:([^\\s]+)\\s+([^\\s]+)\\s+to\\s+([^\\s|}]+?)(\\s+step\\s+(\\d+))?\\s*}([\\s\\S]+?)\\{/for:\\1\\s*}").matcher(content);
					sb = new StringBuffer();
					while (m.find()) {
						String key = "{==forKey:" + m.group(1).replace("->", ".") + "==}";
						map.put(key, new String[]{m.group(1).replace("->", "."), m.group(2), m.group(3), m.group(5), m.group(6)});
						m.appendReplacement(sb, key.replaceAll("([$\\\\])", "\\\\$1"));
					}
					m.appendTail(sb);
				}
				sb = parseIf(sb);
				if (hasChild) sb = parseFor(map, sb, obj, item);
				content = parseVariable(sb.toString(), obj, item);
				ret.append(content);
			}
			matcher.appendReplacement(html, ret.toString().replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);
		return html;
	}

	private StringBuffer parseForeach(Map<String, String[]> map, StringBuffer html) {
		return parseForeach(map, html, this.data, null);
	}
	private StringBuffer parseForeach(Map<String, String[]> map, StringBuffer html, Object obj, String item) {
		Matcher matcher = Pattern.compile("\\{==foreachKey:([^=]+)==}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			if (map.get(matcher.group(0)) == null) {
				matcher.appendReplacement(html, "");
				continue;
			}
			String[] param = map.get(matcher.group(0));
			Object items = parse(param[0], obj, item);
			if (items == null || (!(items instanceof List) && !items.getClass().isArray())) {
				matcher.appendReplacement(html, "");
				continue;
			}
			StringBuilder ret = new StringBuilder();
			if (items instanceof List) {
				try {
					int len = (int) items.getClass().getMethod("size").invoke(items);
					for (int i = 0; i < len; i++) {
						Object o = items.getClass().getMethod("get", int.class).invoke(items, i);
						String content = parseForeachContent(param[2], param[0], i, len);
						boolean hasChild = false;
						StringBuffer sb = new StringBuffer(content);
						if (content.contains("{foreach:")) {
							hasChild = true;
							Matcher m = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(content);
							sb = new StringBuffer();
							while (m.find()) {
								String key = "{==foreachKey:" + m.group(1).replace("->", ".") + "==}";
								map.put(key, new String[]{m.group(1).replace("->", "."), m.group(2), m.group(3)});
								m.appendReplacement(sb, key.replaceAll("([$\\\\])", "\\\\$1"));
							}
							m.appendTail(sb);
						}
						sb = parseIf(sb, o, param[1]);
						if (hasChild) sb = parseForeach(map, sb, o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				if (items instanceof String[]) {
					int len = ((String[]) items).length;
					for (int i = 0; i < len; i++) {
						Object o = ((String[]) items)[i];
						String content = parseForeachContent(param[2], param[0], i, len);
						boolean hasChild = false;
						StringBuffer sb = new StringBuffer(content);
						if (content.contains("{foreach")) {
							hasChild = true;
							Matcher m = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(content);
							sb = new StringBuffer();
							while (m.find()) {
								String key = "{==foreachKey:" + m.group(1).replace("->", ".") + "==}";
								map.put(key, new String[]{m.group(1).replace("->", "."), m.group(2), m.group(3)});
								m.appendReplacement(sb, key.replaceAll("([$\\\\])", "\\\\$1"));
							}
							m.appendTail(sb);
						}
						sb = parseIf(sb, o, param[1]);
						if (hasChild) sb = parseForeach(map, sb, o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (items instanceof Integer[]) {
					int len = ((Integer[]) items).length;
					for (int i = 0; i < len; i++) {
						Object o = ((Integer[]) items)[i];
						String content = parseForeachContent(param[2], param[0], i, len);
						boolean hasChild = false;
						StringBuffer sb = new StringBuffer(content);
						if (content.contains("{foreach")) {
							hasChild = true;
							Matcher m = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(content);
							sb = new StringBuffer();
							while (m.find()) {
								String key = "{==foreachKey:" + m.group(1).replace("->", ".") + "==}";
								map.put(key, new String[]{m.group(1).replace("->", "."), m.group(2), m.group(3)});
								m.appendReplacement(sb, key.replaceAll("([$\\\\])", "\\\\$1"));
							}
							m.appendTail(sb);
						}
						sb = parseIf(sb, o, param[1]);
						if (hasChild) sb = parseForeach(map, sb, o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (items instanceof Long[]) {
					int len = ((Long[]) items).length;
					for (int i = 0; i < len; i++) {
						Object o = ((Long[]) items)[i];
						String content = parseForeachContent(param[2], param[0], i, len);
						boolean hasChild = false;
						StringBuffer sb = new StringBuffer(content);
						if (content.contains("{foreach")) {
							hasChild = true;
							Matcher m = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(content);
							sb = new StringBuffer();
							while (m.find()) {
								String key = "{==foreachKey:" + m.group(1).replace("->", ".") + "==}";
								map.put(key, new String[]{m.group(1).replace("->", "."), m.group(2), m.group(3)});
								m.appendReplacement(sb, key.replaceAll("([$\\\\])", "\\\\$1"));
							}
							m.appendTail(sb);
						}
						sb = parseIf(sb, o, param[1]);
						if (hasChild) sb = parseForeach(map, sb, o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (items instanceof Float[]) {
					int len = ((Float[]) items).length;
					for (int i = 0; i < len; i++) {
						Object o = ((Float[]) items)[i];
						String content = parseForeachContent(param[2], param[0], i, len);
						boolean hasChild = false;
						StringBuffer sb = new StringBuffer(content);
						if (content.contains("{foreach")) {
							hasChild = true;
							Matcher m = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(content);
							sb = new StringBuffer();
							while (m.find()) {
								String key = "{==foreachKey:" + m.group(1).replace("->", ".") + "==}";
								map.put(key, new String[]{m.group(1).replace("->", "."), m.group(2), m.group(3)});
								m.appendReplacement(sb, key.replaceAll("([$\\\\])", "\\\\$1"));
							}
							m.appendTail(sb);
						}
						sb = parseIf(sb, o, param[1]);
						if (hasChild) sb = parseForeach(map, sb, o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (items instanceof Double[]) {
					int len = ((Double[]) items).length;
					for (int i = 0; i < len; i++) {
						Object o = ((Double[]) items)[i];
						String content = parseForeachContent(param[2], param[0], i, len);
						boolean hasChild = false;
						StringBuffer sb = new StringBuffer(content);
						if (content.contains("{foreach")) {
							hasChild = true;
							Matcher m = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(content);
							sb = new StringBuffer();
							while (m.find()) {
								String key = "{==foreachKey:" + m.group(1).replace("->", ".") + "==}";
								map.put(key, new String[]{m.group(1).replace("->", "."), m.group(2), m.group(3)});
								m.appendReplacement(sb, key.replaceAll("([$\\\\])", "\\\\$1"));
							}
							m.appendTail(sb);
						}
						sb = parseIf(sb, o, param[1]);
						if (hasChild) sb = parseForeach(map, sb, o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else if (items instanceof BigDecimal[]) {
					int len = ((BigDecimal[]) items).length;
					for (int i = 0; i < len; i++) {
						Object o = ((BigDecimal[]) items)[i];
						String content = parseForeachContent(param[2], param[0], i, len);
						boolean hasChild = false;
						StringBuffer sb = new StringBuffer(content);
						if (content.contains("{foreach")) {
							hasChild = true;
							Matcher m = Pattern.compile("\\{foreach:([^\\s]+)\\s+item\\s*=\\s*(\\w+)\\s*}([\\s\\S]+?)\\{/foreach:\\1\\s*}").matcher(content);
							sb = new StringBuffer();
							while (m.find()) {
								String key = "{==foreachKey:" + m.group(1).replace("->", ".") + "==}";
								map.put(key, new String[]{m.group(1).replace("->", "."), m.group(2), m.group(3)});
								m.appendReplacement(sb, key.replaceAll("([$\\\\])", "\\\\$1"));
							}
							m.appendTail(sb);
						}
						sb = parseIf(sb, o, param[1]);
						if (hasChild) sb = parseForeach(map, sb, o, param[1]);
						content = parseVariable(sb.toString(), o, param[1]);
						ret.append(content);
					}
				} else {
					throw new IllegalArgumentException(String.valueOf(items.getClass()));
				}
			}
			matcher.appendReplacement(html, ret.toString().replaceAll("([$\\\\])", "\\\\$1"));
		}
		matcher.appendTail(html);
		return html;
	}
	private String parseForeachContent(String str, String search, int i, int len) {
		search = search.replaceAll("(\\$)", "\\\\$1");
		str = str.replaceAll("\\{" + search + "\\.index\\s*}", String.valueOf(i));
		str = str.replaceAll(search + "\\.index\\b", String.valueOf(i));
		str = str.replaceAll("\\{" + search + "\\.iteration\\s*}", String.valueOf((i + 1)));
		str = str.replaceAll(search + "\\.iteration\\b", String.valueOf((i + 1)));
		str = str.replaceAll(search + "\\.first\\b", (i == 0 ? "true" : "false"));
		str = str.replaceAll(search + "\\.last\\b", (i == len - 1 ? "true" : "false"));
		return str.replaceAll(search + "\\.total\\b", String.valueOf(len));
	}

	private String parseVariable(String str) {
		return parseVariable(str, this.data, null);
	}
	private String parseVariable(String str, Object obj, String item) {
		Matcher matcher = Pattern.compile("\\{(\\S[^}]+)}").matcher(str);
		StringBuffer html = new StringBuffer();
		while (matcher.find()) {
			Object ret = parse(matcher.group(1), obj, item);
			if (ret == null) ret = "";
			matcher.appendReplacement(html, String.valueOf(ret).replaceAll("([$\\\\])", "\\\\$1").replace("{", "{ "));
		}
		matcher.appendTail(html);
		return html.toString();
	}

	private StringBuffer parseOrigin(Map<String, String> map, StringBuffer html) {
		Matcher matcher = Pattern.compile("\\{==originIndex:(.+?)==}").matcher(html.toString());
		html = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(0);
			String content = "";
			if (map.get(key) != null) content = map.get(key);
			matcher.appendReplacement(html, content);
		}
		matcher.appendTail(html);
		return html;
	}

	private Object parseCompute(String str, Object obj, String item) {
		str = str.trim().replace("->", ".");
		Object res = null;
		Matcher matcher = Pattern.compile("^(.+?)(([+\\-*/])(.+))?$").matcher(str);
		if (matcher.find()) {
			Object leftRet = parse(matcher.group(1), obj, item);
			if (matcher.group(2) == null) return leftRet;
			Object rightRet = parse(matcher.group(4), obj, item);
			if (leftRet == null || rightRet == null) return 0;
			BigDecimal left = new BigDecimal(String.valueOf(leftRet));
			BigDecimal right = new BigDecimal(String.valueOf(rightRet));
			switch (matcher.group(3)) {
				case "+":
					res = left.add(right).doubleValue();
					break;
				case "-":
					res = left.subtract(right).doubleValue();
					break;
				case "*":
					res = left.multiply(right).doubleValue();
					break;
				case "/":
					res = left.divide(right, 2, RoundingMode.HALF_UP).doubleValue();
					break;
			}
		}
		return res;
	}
	
	private Object parse(String str) {
		return parse(str, this.data, null);
	}
	@SuppressWarnings("unchecked")
	private Object parse(String str, Object obj, String item) {
		if (str == null) return null;
		str = str.trim().replace("->", ".");
		Object ret = null;
		if (str.startsWith("$") && !Pattern.compile("^(.+?)([+\\-*/])(.+)$").matcher(str).matches()) {
			String field = str.substring(1);
			if (obj instanceof Map) {
				Matcher matcher = Pattern.compile("^([^.]+)(.+)?$").matcher(field);
				if (!matcher.find()) return null;
				Object o = obj;
				String key = matcher.group(1);
				String _item = key;
				if (item == null) {
					try {
						if (((Map<String, Object>) o).get(key) == null) return null;
					} catch (ClassCastException e) {
						e.printStackTrace();
						System.out.println(field);
						System.out.println(o);
						return null;
					}
					o = ((Map<String, Object>) o).get(key);
				}
				if (matcher.group(2) != null) {
					Matcher m = Pattern.compile("(\\.(\\w+))").matcher(matcher.group(2));
					while (m.find()) {
						key = m.group(2);
						if (item == null || _item.equals(item)) {
							try {
								if (((Map<String, Object>) o).get(key) == null) return null;
							} catch (ClassCastException e) {
								e.printStackTrace();
								System.out.println(field);
								System.out.println(o);
								return null;
							}
							o = ((Map<String, Object>) o).get(key);
						}
					}
				}
				ret = o;
			} else {
				ret = obj;
			}
		} else if (str.startsWith("'") || str.startsWith("\"")) {
			Matcher matcher = Pattern.compile("^(['\"])(.*?)\\1$").matcher(str);
			if (matcher.find()) ret = matcher.group(2);
		} else if (str.startsWith("count(")) {
			Matcher matcher = Pattern.compile("^count\\(([^)]+)\\)$").matcher(str);
			if (!matcher.find()) return 0;
			ret = parseCompute(matcher.group(1), obj, item);
			if (ret == null) return 0;
			if (ret instanceof List) {
				try {
					ret = ret.getClass().getMethod("size").invoke(ret);
				} catch (Exception e) {
					e.printStackTrace();
					ret = 0;
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
				} else if (ret instanceof BigDecimal[]) {
					return Arrays.asList((BigDecimal[]) ret).size();
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
					Object r = parseCompute(matcher.group(1), obj, item);
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
				str = (String) parseCompute(matcher.group(1), obj, item);
				if (str == null) return null;
				if (matcher.group(2) != null) characters = matcher.group(3);
				ret = trim(str, characters);
			}
		} else if (str.startsWith("urlencode(")) {
			Matcher matcher = Pattern.compile("^urlencode\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				try {
					str = (String) parseCompute(matcher.group(1), obj, item);
					if (str == null) return null;
					ret = URLEncoder.encode(str, "UTF-8");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else if (str.startsWith("number_format(") || str.startsWith("round(")) {
			Matcher matcher = Pattern.compile("^(number_format|round)\\(([^,]+),\\s*([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				double number = Double.parseDouble(String.valueOf(parseCompute(matcher.group(2), obj, item)));
				int digits = (int) Float.parseFloat(String.valueOf(parseCompute(matcher.group(3), obj, item)));
				ret = String.format("%."+digits+"f", number);
			}
		} else if (str.startsWith("is_array(")) {
			Matcher matcher = Pattern.compile("^is_array\\((.+)\\)$").matcher(str);
			if (matcher.find()) {
				Object o = parse(matcher.group(1), obj, item);
				if (o == null) return Boolean.FALSE;
				return (o instanceof List) || o.getClass().isArray();
			}
			return Boolean.FALSE;
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
					} else if (array instanceof BigDecimal[]) {
						return Arrays.asList((BigDecimal[]) array).contains((BigDecimal) element);
					} else {
						throw new IllegalArgumentException(array.getClass().getName() + " IS NOT SUPPORT");
					}
				}
				return Boolean.FALSE;
			}
			ret = Boolean.FALSE;
		} else if (str.startsWith("isset(")) {
			Matcher matcher = Pattern.compile("^isset\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				Object o = parse(matcher.group(1), obj, item);
				return o != null;
			}
			return Boolean.FALSE;
		} else if (str.startsWith("json_encode(")) {
			Matcher matcher = Pattern.compile("^json_encode\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				Object o = parse(matcher.group(1), obj, item);
				if (o == null) return null;
				ret = JSON.toJSONString(o, SerializerFeature.WriteMapNullValue);
			}
		} else if (str.startsWith("strtolower(") || str.startsWith("lower(")) {
			Matcher matcher = Pattern.compile("^(strto)?lower\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				Object o = parseCompute(matcher.group(2), obj, item);
				if (!(o instanceof String)) return null;
				ret = ((String) o).toLowerCase();
			}
		} else if (str.startsWith("strtoupper(") || str.startsWith("upper(")) {
			Matcher matcher = Pattern.compile("^(strto)?upper\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				Object o = parseCompute(matcher.group(2), obj, item);
				if (!(o instanceof String)) return null;
				ret = ((String) o).toUpperCase();
			}
		} else if (str.startsWith("intval(")) {
			Matcher matcher = Pattern.compile("^intval\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				float number = Float.parseFloat(String.valueOf(parse(matcher.group(1), obj, item)));
				ret = (int) number;
			}
		} else if (str.startsWith("nl2br(")) {
			Matcher matcher = Pattern.compile("^nl2br\\(([^)]+)\\)$").matcher(str);
			if (matcher.find()) {
				String res = (String) parse(matcher.group(1), obj, item);
				ret = res.replace("\n", "<br>");
			}
		} else if (Pattern.compile("^(.+?)([+\\-*/])(.+)$").matcher(str).matches()) {
			ret = parseCompute(str, obj, item);
		} else if (isNumeric(str)) {
			ret = Float.parseFloat(str);
		} else if (str.equalsIgnoreCase("true")) {
			return Boolean.TRUE;
		} else if (str.equalsIgnoreCase("false")) {
			return Boolean.FALSE;
		} else if (str.startsWith("tengine.")) {
			ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			HttpServletRequest request = Objects.requireNonNull(servletRequestAttributes).getRequest();
			String[] sys = str.split("\\.");
			switch (sys[1].toLowerCase()) {
				case "time": {
					ret = new Date().getTime() / 1000;
					break;
				}
				case "get": {
					if (sys.length < 3) return null;
					ret = request.getParameter(sys[2]);
					if (ret == null) return null;
					break;
				}
				case "param": {
					if (sys.length < 3) return null;
					String uri = request.getRequestURI();
					if (!uri.matches("^/\\w+/\\w+/\\w+/\\w+.*")) return null;
					uri = uri.replaceAll("^/\\w+/\\w+/\\w+/", "");
					String[] params = uri.split("/");
					if (sys[2].matches("^-?\\d+$")) {
						int index = Integer.parseInt(sys[2]);
						if (index >= params.length || index < -params.length) return null;
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
					if (ret == null) return null;
					break;
				}
				case "path": {
					if (sys.length < 3) return null;
					String uri = trim(request.getRequestURI(), "/");
					String[] params = uri.split("/");
					if (!sys[2].matches("^-?\\d+$")) return null;
					int index = Integer.parseInt(sys[2]);
					if (index >= params.length || index < -params.length) return null;
					if (index < 0) index += params.length;
					ret = params[index];
					if (ret == null) return null;
					break;
				}
				case "session": {
					if (sys.length < 3) return null;
					ret = request.getSession().getAttribute(sys[2]);
					if (ret == null) return null;
					break;
				}
				case "cookie": {
					if (sys.length < 3) return null;
					Cookie[] cookies = request.getCookies();
					if (cookies == null) return null;
					try {
						for (Cookie cookie : cookies) {
							if (cookie.getName().equals(sys[2])) {
								ret = URLDecoder.decode(cookie.getValue(), "UTF-8");
								break;
							}
						}
					} catch (Exception e) {
						return null;
					}
					if (ret == null) return null;
					break;
				}
				case "server": {
					if (sys.length < 3) return null;
					Properties properties = System.getProperties();
					ret = properties.get(sys[2]);
					if (ret == null) return null;
					break;
				}
				case "header": {
					if (sys.length < 3) return null;
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
		} else if (this.clazz != null) {
			//自定义方法, 定义在当前app内, 且为 public static 方法, 如 {testFun('merge', 'string')}
			Matcher matcher = Pattern.compile("^(\\w+)\\(([^,]+)(,[^)]+)?\\)$").matcher(str);
			if (matcher.find()) {
				String funName = matcher.group(1);
				Object param = parseCompute(matcher.group(2), obj, item);
				if (param == null) return null;
				List<Object> list = new ArrayList<>();
				list.add(param);
				Class<?>[] parameterTypes = new Class<?>[list.size()];
				Object[] args = new Object[list.size()];
				if (matcher.group(3) != null) {
					Matcher m = Pattern.compile("(,\\s*([^,]+))").matcher(matcher.group(3));
					while (m.find()) {
						Object r = parseCompute(m.group(2), obj, item);
						if (r == null) return null;
						list.add(r);
					}
					parameterTypes = new Class<?>[list.size()];
					args = new Object[list.size()];
					for (int i = 0; i < list.size(); i++) {
						parameterTypes[i] = list.get(i).getClass();
						args[i] = list.get(i);
					}
				}
				try {
					ret = this.clazz.getMethod(funName, parameterTypes).invoke(null, args);
				} catch (Exception e) {
					writeError("Tengine template variable error: " + str + "\n" + e.getMessage() + "\n\n");
					return null;
				}
			}
		}
		return ret;
	}
}
