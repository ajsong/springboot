//Developed by @mario 1.3.20220122
package com.laokema.tool;

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.j256.simplemagic.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.*;
import java.util.*;
import java.lang.*;
import java.util.jar.*;
import java.util.regex.*;

public class Common {
	static String rootPath;
	static String templateDir;
	static HttpServletRequest request;
	static HttpServletResponse response;
	static String imgDomain;
	static DB.DataMap clientDefine;
	static Redis redis;

	//打印log
	public static void log(Object obj) {
		System.out.println(obj);
	}

	//设置全局Request、Response
	public static void setServlet(HttpServletRequest req, HttpServletResponse res) {
		request = req;
		response = res;
		response.setContentType("text/html; charset=utf-8");
	}

	//设置模板的路径前缀
	public static void setTemplateDir(String dir) {
		if (dir == null) dir = "";
		if (dir.length() > 0) dir = "/" + trim(dir, "/");
		templateDir = dir;
	}

	//获取根目录路径
	public static String get_root_path() {
		if (rootPath == null || rootPath.length() == 0) {
			ApplicationHome ah = new ApplicationHome(Common.class);
			rootPath = ah.getSource().getParentFile().getPath();
		}
		return rootPath;
	}

	//当前是否jar运行
	public static boolean is_jar_run() {
		return new File(get_jar_path()).isFile();
	}

	//获取jar路径
	public static String get_jar_path() {
		return Common.class.getProtectionDomain().getCodeSource().getLocation().getFile();
	}

	//获取jar内文件的内容
	public static void get_jar_file(String jarPath, String filepath) {
		try {
			JarFile jarFile = new JarFile(jarPath);
			JarEntry entry = jarFile.getJarEntry(trim(filepath, "/"));
			InputStream input = jarFile.getInputStream(entry);
			InputStreamReader in = new InputStreamReader(input);
			BufferedReader reader = new BufferedReader(in);
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//历遍jar内文件目录
	public static void get_jar_filepath(String jarPath) {
		try {
			JarFile jarFile = new JarFile(jarPath);
			for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) { //这个循环会读取jar包中所有文件，包括文件夹
				JarEntry jarEntry = e.nextElement(); //jarEntry就是我们读取的jar包中每一个文件了，包括目录
				System.out.println(jarEntry.getName());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//获取web.xml的display-name
	public static String get_display_name() {
		return request.getServletContext().getServletContextName();
	}

	//读取配置文件
	public static Map<String, String> get_properties() {
		return get_properties("application.properties");
	}
	public static Map<String, String> get_properties(String filename) {
		Map<String, String> map = new HashMap<>();
		try {
			Properties properties = new Properties();
			properties.load(Common.class.getClassLoader().getResourceAsStream(filename));
			for (String key: properties.stringPropertyNames()) {
				map.put(key, properties.getProperty(key));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	}
	public static String get_property(String key) {
		Map<String, String> map = get_properties();
		return map.get(key);
	}

	//解析配置文件参数值(json类型)
	@SuppressWarnings("unchecked")
	public static <T> T get_json_property(String param) {
		Map<String, String> properties = get_properties();
		String value = properties.get(param);
		if (value != null && value.length() > 0) {
			if (value.startsWith("[")) {
				return (T) JSONArray.parseArray(value);
			} else if (value.startsWith("{")) {
				return (T) JSON.parseObject(value);
			}
		}
		return (T) value;
	}

	//读取自定义配置文件
	public static Map<String, Object> get_my_property(String filepath) {
		Map<String, Object> map = new HashMap<>();
		try {
			Properties properties = new Properties();
			properties.load(new FileInputStream(filepath));
			for (String key: properties.stringPropertyNames()) {
				String value = properties.getProperty(key);
				if (value.startsWith("[")) {
					List<Object> list = JSONObject.parseArray(JSON.parseArray(value).toJSONString(), Object.class);
					map.put(key, list);
				} else if (value.startsWith("{")) {
					JSONObject obj = JSON.parseObject(value);
					Map<String, Object> subMap = new HashMap<>(obj);
					map.put(key, subMap);
				} else {
					map.put(key, value);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	}

	//生成自定义配置文件
	public static void save_my_property(Map<String, Object> params, String filepath) {
		StringBuilder content = new StringBuilder();
		for (Map.Entry<String, Object> param : params.entrySet()) {
			Object value = param.getValue();
			content.append(param.getKey()).append(" = ").append((value instanceof String) ? value : JSON.toJSONString(value)).append("\n");
		}
		try {
			FileWriter fileWritter = new FileWriter(get_root_path() + "/" + trim(filepath.replaceAll(get_root_path(), ""), "/"));
			fileWritter.write(content.toString());
			fileWritter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//生成序列号
	public static String generate_sn() {
		return date("yyyyMMddHHmmss") + rand(10000, 99999);
	}

	//生成sign
	public static String generate_sign() {
		return md5(md5(String.valueOf(rand(100000, 999999))) + time());
	}

	//生成密码盐值salt
	public static String generate_salt() {
		return String.valueOf(rand(100000, 999999));
	}

	//根据盐值生成加密密码
	public static String crypt_password(String password, String salt) {
		if (password == null || password.length() == 0 || salt == null || salt.length() == 0) return "";
		return md5(md5(password) + salt);
	}

	//清除两端字符串
	public static String trim(String str) {
		return trim(str, " ");
	}
	public static String trim(String str, String symbol) {
		return str.replaceAll("(^" + symbol + "|" + symbol + "$)", "");
	}

	//是否POST请求
	public static boolean isPost() {
		return request.getMethod().equalsIgnoreCase("POST");
	}

	//是否PUT请求
	public static boolean isPut() {
		return request.getMethod().equalsIgnoreCase("PUT");
	}

	//是否DELETE请求
	public static boolean isDelete() {
		return request.getMethod().equalsIgnoreCase("DELETE");
	}

	//是否WAP
	public static boolean isWap() {
		return isMobileWeb();
	}

	//是否WEB
	public static boolean isWeb() {
		return (!isAjax() && !isWap());
	}

	//是否微信端打开
	public static boolean isWX() {
		return StringUtils.containsIgnoreCase(get_headers().get("user-agent"), "MicroMessenger");
	}

	//是否微信小程序打开
	public static boolean isMini() {
		return (StringUtils.containsIgnoreCase(get_headers().get("referer"), "https://servicewechat.com/wx") && isWX());
	}

	//是否微信开发者工具打开
	public static boolean isDevTools() {
		return StringUtils.containsIgnoreCase(get_headers().get("user-agent"), "wechatdevtools");
	}

	//是否AJAX
	public static boolean isAjax() {
		return (request.getRequestURI().startsWith("/api") ||
				(request.getHeader("x-requested-with") != null && request.getHeader("x-requested-with").equalsIgnoreCase("XMLHttpRequest")) ||
				request.getHeader("user-agent").contains("RequestType/ajax"));
	}

	//判断移动端浏览器打开
	public static boolean isMobileWeb() {
		String[] keywords = new String[]{
			"nokia", "sony", "ericsson", "mot", "samsung", "htc", "sgh", "lg", "sharp", "sie-", "philips", "panasonic", "alcatel", "lenovo", "blackberry",
			"meizu", "netfront", "symbian", "ucweb", "windowsce", "palm", "operamini", "operamobi", "openwave", "nexusone", "cldc", "midp", "wap", "mobile",
			"smartphone", "windows ce", "windows phone", "ipod", "iphone", "ipad", "android"
		};
		return get_headers().get("user-agent").matches("(" + StringUtils.join(keywords, "|") + ")");
	}

	//字符串是否数字
	public static boolean isNumeric(Object str) {
		if ((str instanceof Integer) || (str instanceof Float) || (str instanceof Double)) return true;
		Pattern pattern = Pattern.compile("^[-+]?[\\d]+$");
		return pattern.matcher((CharSequence) str).matches();
	}

	//验证手机号
	public static boolean isMobile(String str) {
		return Pattern.compile("^13[\\d]{9}$|^14[5,7]{1}\\d{8}$|^15[^4]{1}\\d{8}$|^17[03678]{1}\\d{8}$|^18[\\d]{9}$").matcher(str).matches();
	}

	//验证座机
	public static boolean isTel(String str) {
		return Pattern.compile("^((\\d{3,4}-)?\\d{8}(-\\d+)?|(\\(\\d{3,4}\\))?\\d{8}(-\\d+)?)$").matcher(str).matches();
	}

	//验证电话号码(包括手机号与座机)
	public static boolean isPhone(String str) {
		return isMobile(str) || isTel(str);
	}

	//验证邮箱
	public static boolean isEmail(String str) {
		return Pattern.compile("^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$").matcher(str).matches();
	}

	//验证is_date
	public static boolean isDate(String str) {
		return Pattern.compile("^(?:(?!0000)[0-9]{4}[/-](?:(?:0?[1-9]|1[0-2])[/-](?:0?[1-9]|1[0-9]|2[0-8])|(?:0?[13-9]|1[0-2])[/-](?:29|30)|(?:0?[13578]|1[02])[/-]31)|(?:[0-9]{2}(?:0[48]|[2468][048]|[13579][26])|(?:0[48]|[2468][048]|[13579][26])00)[/-]0?2[/-]29)$").matcher(str).matches();
	}

	//验证邮箱
	public static boolean isIdcard(String str) {
		int ID_LENGTH = 17;
		boolean vIDNumByRegex =  str.matches("(\\d{17}[\\dxX]|\\d{14}[\\dxX])");
		//系数列表
		int[] ratioArr = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
		//校验码列表
		char[] checkCodeList = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
		//获取身份证号字符数组
		char[] cIds = str.toCharArray();
		//获取最后一位（身份证校验码）
		char oCode = cIds[ID_LENGTH];
		int[] iIds = new int[ID_LENGTH];
		int idSum = 0;// 身份证号第1-17位与系数之积的和
		int residue = 0;// 余数(用加出来和除以11，看余数是多少？)
		for (int i = 0; i < ID_LENGTH; i++) {
			iIds[i] = cIds[i] - '0';
			idSum += iIds[i] * ratioArr[i];
		}
		residue = idSum % 11;// 取得余数
		boolean vIDNumByCode = Character.toUpperCase(oCode) == checkCodeList[residue];
		return vIDNumByRegex && vIDNumByCode;
	}

	//获取主机头信息
	public static Map<String, String> get_headers() {
		Map<String, String> headers = new HashMap<>();
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String key = headerNames.nextElement();
			String value = request.getHeader(key);
			headers.put(key, value);
		}
		return headers;
	}

	//MD5
	public static String md5(String str) {
		try {
			//生成一个MD5加密计算摘要
			MessageDigest md = MessageDigest.getInstance("MD5");
			//计算md5函数
			md.update(str.getBytes());
			//digest()最后确定返回md5 hash值，返回值为8位字符串。因为md5 hash值是16位的hex值，实际上就是8位的字符
			//BigInteger函数则将8位的字符串转换成16位hex值，用字符串来表示；得到字符串形式的hash值
			//一个byte是八位二进制，也就是2位十六进制字符（2的8次方等于16的2次方）
			return new BigInteger(1, md.digest()).toString(16);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	//base64 encode
	public static String base64_encode(String str) {
		return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
	}
	public static String base64_encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	//base64 decode
	public static String base64_decode(String str) {
		byte[] bytes = Base64.getDecoder().decode(str);
		return new String(bytes, StandardCharsets.UTF_8);
	}
	public static byte[] base64_decode(String str, boolean returnByte) {
		return Base64.getDecoder().decode(str);
	}

	//json_encode
	public static String json_encode(Object obj) {
		return JSON.toJSONString(obj, SerializerFeature.WriteMapNullValue);
	}

	//json_decode
	public static Map<String, Object> json_decode(String str) {
		return JSONObject.parseObject(str);
	}

	//时间戳
	public static long time() {
		return time(date("yyyy-MM-dd HH:mm:ss"));
	}
	public static long time(String date) {
		try {
			SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			return time(dateformat.parse(date));
		} catch (ParseException e) {
			e.printStackTrace();
			return 0;
		}
	}
	public static long time(Date date) {
		return date.getTime() / 1000;
	}

	//日期格式化
	public static String date(String format) {
		SimpleDateFormat dateformat = new SimpleDateFormat(format);
		return dateformat.format(new Date());
	}
	public static String date(String format, long timestamp) {
		SimpleDateFormat dateformat = new SimpleDateFormat(format);
		return dateformat.format(new Date(timestamp));
	}

	//获取当前时间的指定部分
	public static int getCalendar(String interval) {
		interval = interval.toLowerCase();
		Calendar c = Calendar.getInstance();
		switch (interval) {
			case "y":return c.get(Calendar.YEAR);
			case "m":return c.get(Calendar.MONTH);
			case "w":return c.get(Calendar.DAY_OF_WEEK);
			case "d":return c.get(Calendar.DATE);
			case "h":return c.get(Calendar.HOUR_OF_DAY);
			case "n":return c.get(Calendar.MINUTE);
			case "s":return c.get(Calendar.SECOND);
		}
		return 0;
	}

	//利用UUID生成伪随机字符串
	public static String uuid() {
		return UUID.randomUUID().toString();
	}

	//指定范围随机数
	public static int rand(int min, int max) {
		Random random = new Random();
		return random.nextInt(max) % (max - min + 1) + min;
	}

	//指定长度随机字符串
	public static String random_str(int length) {
		ArrayList<String> list = new ArrayList<>();
		int begin = 97;
		//生成小写字母,并加入集合
		for(int i = begin; i < begin + 26; i++) {
			list.add((char)i + "");
		}
		//生成大写字母,并加入集合
		begin = 65;
		for(int i = begin; i < begin + 26; i++) {
			list.add((char)i + "");
		}
		//将0-9的数字加入集合
		for(int i = 0; i < 10; i++) {
			list.add(i + "");
		}
		Random random = new Random();
		StringBuilder res = new StringBuilder();
		for (int i = 0; i < length; i++) {
			res.append(list.get(random.nextInt(list.size())));
		}
		return res.toString();
	}

	//将时间转换成刚刚、分钟、小时
	public static String get_time_word(String date) {
		return get_time_word(time(date));
	}
	public static String get_time_word(Date date) {
		return get_time_word(time(date));
	}
	public static String get_time_word(long timestamp) {
		long between = time() - timestamp;
		if (between < 60) return "刚刚";
		if (between < 3600) return Math.floor((double) (between / 60)) + "分钟前";
		if (between < 86400) return Math.floor((double) (between/3600)) + "小时前";
		if (between <= 864000) return Math.floor((double) (between/86400)) + "天前";
		return date("Y-m-d", timestamp);
	}

	//列出文件夹下所有文件
	public static List<String> listFiles(String directoryPath, boolean isAddDirectory) {
		List<String> list = new ArrayList<>();
		File baseFile = new File(directoryPath);
		if (baseFile.isFile() || !baseFile.exists()) return list;
		File[] files = baseFile.listFiles();
		assert files != null;
		for (File file : files) {
			if (file.isDirectory()) {
				if (isAddDirectory) list.add(file.getAbsolutePath());
				list.addAll(listFiles(file.getAbsolutePath(), isAddDirectory));
			} else {
				list.add(file.getAbsolutePath());
			}
		}
		return list;
	}

	//输出文件内容
	public static void flushFile(String filepath, HttpServletResponse response) {
		try {
			File file = new File(filepath);
			if (!file.exists()) return;
			Path path = Paths.get(file.getPath());
			FileInputStream ips = new FileInputStream(file);
			response.setContentType(Files.probeContentType(path));
			ServletOutputStream out = response.getOutputStream();
			int len;
			byte[] buffer = new byte[1024 * 10];
			while ((len = ips.read(buffer)) != -1) out.write(buffer, 0, len);
			out.flush();
			out.close();
			ips.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//创建文件夹
	public static void makedir(String dir) {
		String filePath = get_root_path() + dir.replaceAll(get_root_path(), "").replaceFirst("/", "");
		File path = new File(filePath);
		if (path.exists()) return;
		if (!path.mkdirs()) throw new IllegalArgumentException("File path create fail: " + filePath);
	}

	//获取ip
	public static String ip() {
		String ip = request.getHeader("x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_CLIENT_IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_X_FORWARDED_FOR");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}

	//返回http协议
	public static String https() {
		return request.getScheme().equals("https") ? "https://" : "http://";
	}

	//当前网址
	public static String domain() {
		String path = request.getContextPath();
		return request.getScheme() + "://" + request.getServerName() + (request.getServerPort() != 80 ? ":" + request.getServerPort() : "") + path;
	}

	//格式化URL,suffix增加网址后缀, 如七牛?imageMogr2/thumbnail/200x200, 又拍云(需自定义)!logo
	public static String add_domain(String url) {
		return add_domain(url, "");
	}
	public static String add_domain(String url, String suffix) {
		if (imgDomain == null || imgDomain.length() == 0) {
			DB.DataMap client = DB.share("client").cached(60*60*24*3).find();
			imgDomain = (String) client.get("domain");
		}
		String server = https() + request.getServerName();
		if (url != null && url.length() > 0 && !url.startsWith("http://") && !url.startsWith("https://")) {
			if (url.startsWith("//")) {
				url = https() + url.substring(2);
			} else {
				if (url.contains("%domain%") && !url.contains(server)) {
					url = url.replaceAll("%domain%", server);
				} else {
					url = url.replaceAll("%domain%", "");
					if (url.charAt(0) == '/') {
						url = (imgDomain.length() > 0 ? imgDomain : server) + url;
					} else {
						if (Pattern.matches("^((http|https|ftp)://)?[\\w-_]+(\\.[\\w\\-_]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?$", server + "/" + url)) {
							url = (imgDomain.length() > 0 ? imgDomain : server) + "/" + url;
						} else {
							url = url.replaceAll("\"/uploads/", "\"" + server + "/uploads/");
						}
					}
				}
			}
		}
		if (url != null && url.length() > 0 && !url.contains("/images/") && suffix.length() > 0 && !url.contains(suffix)) url += suffix;
		if (url != null) url = url.replaceAll("%domain%", "");
		return url;
	}

	//递归一个数组/对象的属性加上域名
	public static <T> T add_domain_deep(T obj, String field) {
		return add_domain_deep(obj, new String[]{field});
	}
	@SuppressWarnings("unchecked")
	public static <T> T add_domain_deep(T obj, String[] fields) {
		if (obj instanceof List) {
			List<Object> o = (List<Object>)obj;
			for (int i = 0; i < o.size(); i++) {
				Object e = add_domain_deep(o.get(i), fields);
				o.set(i, e);
			}
		} else if (obj instanceof Map) {
			Map<String, String> map = new HashMap<>((Map<String, String>) obj);
			for (String key : map.keySet()) {
				if (!Arrays.asList(fields).contains(key)) continue;
				map.put(key, add_domain(map.get(key)));
			}
			obj = (T) map;
		} else if (obj instanceof String) {
			obj = (T) add_domain((String) obj);
		} else if (obj != null) {
			try {
				Class<?> clazz = obj.getClass();
				Field[] _fields = clazz.getDeclaredFields();
				for (Field f : _fields) {
					if (!Arrays.asList(fields).contains(f.getName())) continue;
					String getterName = "get" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);
					Method getter = clazz.getMethod(getterName);
					String url = (String) getter.invoke(obj);
					String setterName = "set" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);
					Method setter = clazz.getMethod(setterName, f.getType());
					setter.invoke(obj, add_domain(url));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return obj;
	}

	//输出script
	public static String script(String msg) {
		return script(msg, "");
	}
	public static String script(String msg, String url) {
		try {
			String html = "<meta charset=\"UTF-8\"><script>";
			if (msg != null && msg.length() > 0) html += "alert('" + msg + "');";
			if (url != null && url.length() > 0) {
				if (url.startsWith("javascript:")) {
					html += url.substring(11);
				} else if (url.startsWith("js:")) {
					html += url.substring(3);
				} else {
					html += "location.href = '" + url + "';";
				}
			}
			html += "</script>";
			return html;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}
	public static void writeScript(String msg) {
		writeScript(msg, "");
	}
	public static void writeScript(String msg, String url) {
		try {
			String html = script(msg, url);
			PrintWriter out = response.getWriter();
			out.write(html);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static String historyBack() {
		return historyBack("");
	}
	public static String historyBack(String msg) {
		return script(msg, "javascript:history.back()");
	}
	public static void writeHistoryBack() {
		writeHistoryBack("");
	}
	public static void writeHistoryBack(String msg) {
		writeScript(msg, "javascript:history.back()");
	}

	//跳转网址
	public static String location(String url) {
		return "redirect:" + url.replaceFirst("redirect:", "");
	}

	//写log
	public static void write_log(String content) {
		//String path = request.getSession().getServletContext().getRealPath("/temp");
		String path = get_root_path() + "/temp";
		File filePath = new File(path);
		if (!filePath.exists()) {
			if (!filePath.mkdirs()) throw new IllegalArgumentException("File path create fail: " + path);
		}
		write_log(content, path + "/log.txt");
	}
	public static void write_log(String content, String file) {
		try {
			FileWriter fileWritter = new FileWriter(file, true);
			fileWritter.write(content);
			fileWritter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void write_error(String content) {
		String path = get_root_path() + "/temp";
		File filePath = new File(path);
		if (!filePath.exists()) {
			if (!filePath.mkdirs()) throw new IllegalArgumentException("File path create fail: " + path);
		}
		write_log(content, path + "/error.txt");
	}

	//初始化Redis
	public static Redis redis() {
		 if (redis == null) redis = new Redis();
		 return redis;
	}

	//上传文件
	public static String upload_file(String key) {
		return upload_file(key, "");
	}
	public static String upload_file(String key, String dir) {
		return upload_file(key, dir, "jpg,png,gif,bmp");
	}
	public static String upload_file(String key, String dir, String fileType) {
		Map<String, Object> files = upload_file(dir, fileType, false);
		if (files == null || files.keySet().size() == 0) return "";
		return (String) files.get(key);
	}
	public static Map<String, Object> upload_file(String dir, String fileType, boolean returnDetail) {
		Upload upload = new Upload(request, response);
		return upload.file(dir, fileType, returnDetail);
	}

	//字符串转任何类型
	@SuppressWarnings("unchecked")
	public static <T> T stringToBean(String value, Class<T> clazz) {
		if (value == null || value.length() <= 0 || clazz == null) return null;
		if (clazz == Integer.class) {
			return (T) Integer.valueOf(value);
		} else if (clazz == Long.class) {
			return (T) Long.valueOf(value);
		} else if (clazz == String.class) {
			return (T) value;
		} else {
			try {
				return JSON.toJavaObject(JSON.parseObject(value), clazz);
			} catch (Exception e) {
				return JSON.toJavaObject(JSON.parseArray(value), clazz);
			}
		}
	}

	//任何类型转字符串
	public static <T> String beanToString(T value) {
		if (value == null) return null;
		if (value.getClass() == Integer.class) {
			return "" + value;
		} else if (value.getClass() == Long.class) {
			return "" + value;
		} else if (value.getClass() == String.class) {
			return (String) value;
		} else {
			return JSON.toJSONString(value);
		}
	}

	//Map转对象
	public static <T> T mapToInstance(Map<String, Object> map, Class<T> clazz) {
		try {
			T obj = clazz.getConstructor().newInstance();
			for (String key : map.keySet()) {
				Object value = map.get(key);
				try {
					Field f = obj.getClass().getDeclaredField(key);
					String setterName = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
					Method setter = clazz.getMethod(setterName, f.getType());
					if (value != null && !f.getType().equals(value.getClass()) && !f.getType().getName().equals("java.lang.Object")) {
						if (f.getType() == Integer.class) {
							value = Integer.parseInt(String.valueOf(value));
						} else if (f.getType() == Long.class) {
							value = Long.parseLong(String.valueOf(value));
						} else if (f.getType() == Float.class) {
							value = Float.parseFloat(String.valueOf(value));
						} else if (f.getType() == Double.class) {
							value = Double.parseDouble(String.valueOf(value));
						} else if (f.getType() == String.class) {
							value = String.valueOf(value);
						} else {
							System.out.println("Common.mapToInstance");
							System.out.println(clazz.getName()+"     "+setterName+"      "+f.getName()+" = "+f.getType().getName()+"        data = "+value.getClass().getName());
						}
					}
					if (value != null) setter.invoke(obj, value);
				} catch (NoSuchFieldException e) {
					String packageName = Common.class.getPackage().getName();
					if (clazz.getSuperclass().getName().equals(packageName.substring(0, packageName.lastIndexOf(".") + 1) + "model.BaseModel")) {
						Method getter = clazz.getMethod("set", String.class, Object.class);
						getter.invoke(obj, key, value);
					}
				}
			}
			return obj;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	//对象转Map
	public static Map<String, Object> instanceToMap(Object obj) {
		JSONObject map = JSON.parseObject(JSON.toJSONString(obj));
		return new HashMap<>(map);
	}

	//DB.DataMap转Map<String, Object>
	public static Object dataToMap(Object obj) {
		if (obj == null) return null;
		if (obj instanceof List) {
			List<Object> list = new ArrayList<>();
			for (Object map : (List<?>)obj) {
				list.add(dataToMap(map));
			}
			return list;
		} else if (obj instanceof DB.DataMap) {
			return ((DB.DataMap)obj).data;
		}
		return obj;
	}

	//请求
	public static String requestUrl(String method, String url) {
		return requestUrl(method, url, "", false, null);
	}
	public static String requestUrl(String method, String url, String data) {
		return requestUrl(method, url, data, false, null);
	}
	public static JSONObject requestUrl(String method, String url, String data, boolean returnJson) {
		Map<String, Object> map = new HashMap<>();
		if (data != null && data.length() > 0) {
			String[] _params = data.split("&");
			for (String param : _params) {
				String[] p = param.split("=");
				map.put(p[0], p.length > 1 ? p[1] : "");
			}
		}
		return requestUrl(method, url, map, returnJson);
	}
	public static JSONObject requestUrl(String method, String url, Map<String, Object> data, boolean returnJson) {
		String res = requestUrl(method, url, data, false, null);
		return JSON.parseObject(res);
	}
	public static String requestUrl(String method, String url, Map<String, Object> data) {
		return requestUrl(method, url, data, false, null);
	}
	public static String requestUrl(String method, String url, String data, boolean postJson, Map<String, String> headers) {
		Map<String, Object> map = new HashMap<>();
		if (data != null && data.length() > 0) {
			String[] _params = data.split("&");
			for (String param : _params) {
				String[] p = param.split("=");
				map.put(p[0], p.length > 1 ? p[1] : "");
			}
		}
		return requestUrl(method, url, map, postJson, headers);
	}
	public static String requestUrl(String method, String url, Map<String, Object> data, boolean postJson, Map<String, String> headers) {
		method = method.toUpperCase();
		HttpURLConnection conn = null;
		BufferedReader reader = null;
		StringBuilder res = new StringBuilder();
		try {
			if (url.startsWith("/")) url = domain() + url;
			conn = (HttpURLConnection) new URL(url).openConnection();
			conn.setConnectTimeout(5000);
			conn.setUseCaches(false);
			conn.setRequestProperty("User-Agent", request.getHeader("user-agent") + " RequestType/ajax");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			if (headers != null) {
				for (String key : headers.keySet()) {
					conn.setRequestProperty(key, headers.get(key));
				}
			}
			if (method.equalsIgnoreCase("POST")) {
				conn.setRequestMethod("POST");
				conn.setDoOutput(true); //发送POST请求必须设置
				conn.setDoInput(true);
				boolean isMultipart = false;
				for (String key : data.keySet()) {
					if ((data.get(key) instanceof String) && ((String) data.get(key)).startsWith("@")) {
						isMultipart = true;
						break;
					}
				}
				if (isMultipart) {
					String boundary = "WebKitFormBoundary" + random_str(16);
					conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
					OutputStream stream = new DataOutputStream(conn.getOutputStream());
					for (String key : data.keySet()) {
						Object value = data.get(key);
						if (value == null) continue;
						StringBuilder strBuf = new StringBuilder();
						if ((data.get(key) instanceof String) && ((String) data.get(key)).startsWith("@")) { //file
							File file = new File(((String) value).substring(1));
							String filename = file.getName();
							ContentInfo contentInfo = ContentInfoUtil.findExtensionMatch(filename);
							String mimeType = contentInfo != null ? contentInfo.getMimeType() : null;
							if (mimeType == null && filename.endsWith(".svg")) mimeType = "image/svg+xml";
							if (mimeType == null) mimeType = "application/octet-stream";
							strBuf.append("\r\n").append("--").append(boundary).append("\r\n");
							strBuf.append("Content-Disposition: form-data; name=\"").append(key).append("\"; filename=\"").append(filename).append("\"\r\n");
							strBuf.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
							stream.write(strBuf.toString().getBytes());
							DataInputStream in = new DataInputStream(new FileInputStream(file));
							int bytes;
							byte[] bufferOut = new byte[1024];
							while ((bytes = in.read(bufferOut)) != -1) {
								stream.write(bufferOut, 0, bytes);
							}
							in.close();
						} else { //input
							strBuf.append("\r\n").append("--").append(boundary).append("\r\n");
							strBuf.append("Content-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n");
							strBuf.append(value);
							stream.write(strBuf.toString().getBytes());
						}
					}
					byte[] endData = ("\r\n--" + boundary + "--\r\n").getBytes();
					stream.write(endData);
					stream.flush();
					stream.close();
				} else {
					StringBuilder postData = new StringBuilder();
					//使用JSON提交
					if (postJson) {
						conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
						postData = new StringBuilder(JSONObject.toJSONString(data));
					} else {
						for (String key : data.keySet()) {
							postData.append("&").append(key).append("=").append(data.get(key));
						}
					}
					byte[] bytes = trim(postData.toString(), "&").getBytes(StandardCharsets.UTF_8);
					conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
					conn.getOutputStream().write(bytes);
				}
			} else {
				conn.connect();
			}
			/*// 获取所有响应头字段
			Map<String, List<String>> fields = conn.getHeaderFields();
			for (String key : fields.keySet()) {
				System.out.println(key + ": " + fields.get(key));
			}*/
			int status = conn.getResponseCode();
			if (status == HttpURLConnection.HTTP_OK) {
				reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
				String line;
				while ((line = reader.readLine()) != null) {
					res.append(line).append("\n");
				}
			}
		} catch (Exception e) {
			System.out.println("发送 " + method + " 请求异常");
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) reader.close();
				if (conn != null) conn.disconnect();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return res.toString();
	}

	//display
	public static Object display(Object data, String webPath) {
		return display(data, webPath, null);
	}
	@SuppressWarnings("unchecked")
	public static Object display(Object data, String webPath, Map<String, Object> element) {
		try {
			ModelAndView mv = new ModelAndView(webPath);
			if (webPath.equals("/error")) return mv;
			if (clientDefine == null) {
				clientDefine = DB.share("client_define").field("WEB_NAME, WEB_TITLE").cached(60*60*24*3).find();
			}
			if (data instanceof Map) {
				for (String key : ((Map<String, Object>)data).keySet()) mv.addObject(key, dataToMap(((Map<String, Object>)data).get(key)));
			} else {
				mv.addObject("data", dataToMap(data));
			}
			Object memberObj = request.getSession().getAttribute("member");
			if (memberObj != null) {
				JSONObject json = JSON.parseObject(JSON.toJSONString(memberObj));
				Map<String, Object> member = new HashMap<>(json);
				if (member.get("id") != null && ((int)member.get("id")) > 0) {
					member = add_domain_deep(member, "avatar");
					member.put("reg_time_word", date("Y-m-d", Long.parseLong(String.valueOf(member.get("reg_time")))));
					//memberObj = mapToInstance(member, Class.forName("com.laokema.javaweb.model.index.Member"));
					mv.addObject("member", member);
					mv.addObject("logined", 1);
				}
			} else {
				Map<String, Object> member = DB.createInstanceMap("member");
				member.put("id", 0);
				member.put("avatar", add_domain("/images/avatar.png"));
				//memberObj = mapToInstance(member, Class.forName("com.laokema.javaweb.model.index.Member"));
				mv.addObject("member", member);
				mv.addObject("logined", 0);
			}
			String app = "home";
			String act = "index";
			Matcher matcher = Pattern.compile("^/(\\w+)(/(\\w+))?").matcher(request.getRequestURI().replaceAll("/" + get_display_name(), ""));
			if (matcher.find()) {
				app = matcher.group(1);
				if (matcher.group(3) != null) act = matcher.group(3);
			}
			mv.addObject("app", app);
			mv.addObject("act", act);
			mv.addObject("domain", domain());
			if (mv.getModel().get("WEB_TITLE") == null || ((String)request.getAttribute("WEB_TITLE")).length() == 0) mv.addObject("WEB_TITLE", clientDefine.get("WEB_TITLE"));
			mv.addObject("WEB_NAME", clientDefine.get("WEB_NAME"));
			Map<String, String[]> map = request.getParameterMap();
			if (map != null) {
				for (String key : map.keySet()) {
					String[] values = map.get(key);
					if (values != null && values.length > 0) mv.addObject(key, values.length == 1 ? values[0] : values);
				}
			}
			if (element != null) {
				for (String key : element.keySet()) mv.addObject(key, element.get(key));
			}
			String output = request.getParameter("output");
			if (output == null || !output.equals("json")) {
				return mv;
			} else {
				mv.getModel().remove("output");
				return mv.getModel();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	//success
	public static Object success() {
		return success(null);
	}
	public static Object success(Object data) {
		return success(data, "SUCCESS");
	}
	public static Object success(Object data, String msg) {
		return success(data, msg, 0, null);
	}
	public static Object success(Object data, String msg, int msg_type) {
		return success(data, msg, msg_type, null);
	}
	public static Object success(Object data, String msg, int msg_type, Map<String, Object> element) {
		if (request == null) {
			ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			HttpServletRequest req = Objects.requireNonNull(servletRequestAttributes).getRequest();
			HttpServletResponse res = Objects.requireNonNull(servletRequestAttributes).getResponse();
			setServlet(req, res);
		}
		String gourl = request.getParameter("gourl");
		String goalert = request.getParameter("goalert");
		if ((gourl == null || gourl.length() == 0) && request.getSession().getAttribute("gourl") != null) {
			gourl = (String) request.getSession().getAttribute("gourl");
			request.getSession().removeAttribute("gourl");
		}
		if (gourl != null && gourl.length() > 0) {
			return script(goalert, gourl);
		}
		if ((data instanceof String) && (((String)data).startsWith("@"))) {
			msg = (String) data;
			data = null;
		}
		if (msg.startsWith("redirect:")) {
			return msg;
		} else if (!isAjax() && msg.startsWith("@")) {
			return display(data, msg.substring(1), element);
		} else if (!isAjax()) {
			Matcher matcher = Pattern.compile("^/\\w+/(\\w+)(/(\\w+))?").matcher(request.getRequestURI());
			String app = "home";
			String act = "index";
			if (matcher.find()) {
				app = matcher.group(1);
				if (matcher.group(3) != null) act = matcher.group(3);
			}
			if (templateDir == null) templateDir = "";
			return display(data, templateDir + "/" + app + "." + act, element);
		} else {
			try {
				Map<String, Object> json = new HashMap<>();
				if (data == null) {
					request.removeAttribute("edition");
					request.removeAttribute("function");
					request.removeAttribute("config");
					Map<String, Object> datas = new HashMap<>();
					Enumeration<String> attribute = request.getAttributeNames();
					while (attribute.hasMoreElements()) {
						Object obj = attribute.nextElement();
						datas.put(obj.toString(), request.getAttribute(obj.toString()));
					}
					json.put("data", datas);
				} else {
					json.put("data", dataToMap(data));
				}
				json.put("msg_type", msg_type);
				json.put("msg", msg.startsWith("@") ? "SUCCESS" : msg);
				json.put("error", 0);
				if (element != null) json.putAll(element);
				/*PrintWriter out = response.getWriter();
				out.write(JSON.toJSONString(json, SerializerFeature.WriteMapNullValue));
				out.close();*/
				return json;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	//error
	public static Object error() {
		return error("DATA ERROR");
	}
	public static Object error(String msg) {
		return error(msg, 0);
	}
	public static Object error(String msg, String url) {
		return script(msg, url);
	}
	public static Object error(String msg, int msg_type) {
		if (request == null) {
			ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			HttpServletRequest req = Objects.requireNonNull(servletRequestAttributes).getRequest();
			HttpServletResponse res = Objects.requireNonNull(servletRequestAttributes).getResponse();
			setServlet(req, res);
		}
		String gourl = request.getParameter("gourl");
		if (gourl != null && gourl.length() > 0) {
			return historyBack(msg);
		}
		if (!isAjax() && msg.startsWith("@")) {
			return display(null, msg.substring(1));
		} else if (!isAjax()) {
			switch (msg_type) {
				case -100:case -9:return "redirect:/login";
				case -1:return "redirect:/";
				default:return historyBack(msg);
			}
		} else {
			if (msg_type == -9 || msg_type == -100) msg_type = -10;
			Map<String, Object> json = new HashMap<>();
			json.put("msg_type", msg_type);
			json.put("msg", msg);
			json.put("error", 1);
			return json;
		}
	}

}
