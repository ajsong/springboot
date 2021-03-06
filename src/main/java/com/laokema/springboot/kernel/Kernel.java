package com.laokema.springboot.kernel;

import com.laokema.tool.*;
import com.laokema.tool.plugins.upload.Qiniu;
import javax.servlet.http.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class Kernel {
	public String app;
	public String act;
	public Request request;
	public HttpServletResponse response;
	public String session_id;
	public long now;
	public String ip;
	public Map<String, String> headers;
	public Map<String, String> configs;
	public boolean is_wx;
	public boolean is_mini;
	public boolean is_web;
	public boolean is_wap;
	public static String[] uriMap;
	public static Map<String, Object> uploadThird;
	public static DataMap client;

	public void __construct(HttpServletRequest request, HttpServletResponse response) {
		Map<String, String> moduleMap = Common.getModule(request);
		this.app = moduleMap.get("app");
		this.act = moduleMap.get("act");
		this.request = Common.request();
		this.response = response;
		this.session_id = request.getSession().getId().toLowerCase();
		this.now = Common.time();
		this.ip = Common.ip();
		this.headers = Common.getHeaders();
		this.is_wx = Common.isWX();
		this.is_mini = Common.isMini();
		this.is_wap = Common.isWap();
		this.is_web = Common.isWeb();

		if (client == null) {
			client = DB.share("client").cached(60*60*24*3).find();
		}

		if (uriMap == null) {
			String uri_map = Common.getProperty("sdk.uri.map");
			if (uri_map != null && uri_map.length() > 0) uriMap = uri_map.split(",");
		}
		if (uriMap != null) {
			for (String map : uriMap) {
				String[] items = map.split("=");
				if (request.getRequestURI().matches(items[0])) {
					String[] key = items[1].split("&");
					this.app = key[0];
					this.act = key[1];
					break;
				}
			}
		}

		if (uploadThird == null) {
			boolean UPLOAD_LOCAL = Common.getProperty("sdk.upload.local", true);
			if (UPLOAD_LOCAL) {
				String uploadType = client.getString("upload_type");
				if (uploadType != null && uploadType.length() > 0) {
					uploadThird = new HashMap<>();
					String[] uploadFields = client.getString("upload_fields").split("\\|");
					if (uploadType.equalsIgnoreCase("qniu")) {
						uploadThird.put("package", Qiniu.class);
						for (String field : uploadFields) {
							String[] fields = field.split("???");
							switch (fields[0]) {
								case "qiniu_accessKey":uploadThird.put("accessKey", fields[1]);break;
								case "qiniu_secretKey":uploadThird.put("secretKey", fields[1]);break;
								case "qiniu_bucketname":uploadThird.put("bucket", fields[1]);break;
								case "qiniu_domain":uploadThird.put("domain", fields.length > 1 ? fields[1] : client.getString("domain"));break;
							}
						}
					}
				}
			}
		}
	}

	//??????/??????Session
	public Object getSession(String key) {
		//System.out.println(this.request.getSession().getMaxInactiveInterval());
		try {
			return this.request.getSession().getAttribute(key);
		} catch (NullPointerException e) {
			return null;
		}
	}
	@SuppressWarnings("unchecked")
	public <T> T getSession(String key, Class<T> clazz) {
		Object value = this.getSession(key);
		if (value == null) return null;
		if (value instanceof Map) value = Common.mapToBean((Map<String, Object>) value, clazz);
		if (value != null && value.getClass() != clazz) return null;
		return (T) value;
	}
	public DataList getSessionDataList(String key) {
		Object ret = this.getSession(key);
		if (ret == null) return null;
		if (ret instanceof DataList) return (DataList) ret;
		return null;
	}
	public DataMap getSessionDataMap(String key) {
		Object ret = this.getSession(key);
		if (ret == null) return null;
		if (ret instanceof DataMap) return (DataMap) ret;
		return null;
	}
	public void setSession(String key, Object value) {
		if (value == null) {
			this.removeSession(key);
		} else {
			this.request.getSession().setAttribute(key, value);
		}
	}
	public void removeSession(String key) {
		this.request.getSession().removeAttribute(key);
	}

	//??????/??????Cookie
	public String getCookie(String key) {
		try {
			Cookie[] cookies = this.request.getCookies();
			if (cookies != null) {
				for (Cookie cookie : cookies) {
					if (cookie.getName().equals(key)) return URLDecoder.decode(cookie.getValue(), "UTF-8");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	public void setCookie(String key, String value) {
		setCookie(key, value, -1);
	}
	public void setCookie(String key, String value, int expiry) {
		if (value == null) {
			removeCookie(key);
			return;
		}
		try {
			Cookie cookie = new Cookie(key, URLEncoder.encode(value, "UTF-8"));
			if (expiry > 0) cookie.setMaxAge(expiry); //????????????(?????????), ?????????-1, ?????????????????????
			cookie.setPath("/"); //?????????????????????????????????????????????
			this.response.addCookie(cookie);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	public void removeCookie(String key) {
		Cookie cookie = new Cookie(key, "");
		cookie.setMaxAge(0);
		cookie.setPath("/");
		this.response.addCookie(cookie);
	}

	//??????????????????
	public void setConfigs() {
		this.configs = new HashMap<>();
		DataList CONFIG = DB.share("op_config").field("name, content").cached(60*60*24*3).select();
		for (DataMap g : CONFIG) this.configs.put((String) g.get("name"), (String) g.get("content"));
		CONFIG = DB.share("config").field("name, content").cached(60*60*24*3).select();
		for (DataMap g : CONFIG) this.configs.put((String) g.get("name"), (String) g.get("content"));
	}

	//??????COOKIE??????????????????,token??????????????????????????????,???null???????????????, ???????????????token???, ???_token, name:16, token:32
	public DataMap cookieAccount(String table, String name) {
		return cookieAccount(table, name, "");
	}
	public DataMap cookieAccount(String table, String name, String token) {
		return cookieAccount(table, name, token, "m.*");
	}
	public DataMap cookieAccount(String table, String name, String token, String field) {
		try {
			String[] tables = table.split("_");
			String master = tables[0];
			if (token != null) {
				if (token.length() > 0) {
					return DB.share(master + " m").left(table + " t", "m.name=t.name").where("t.name&t.token", name, token).cached(60*60*24*7).field(field).find();
				} else {
					token = Common.md5(Common.uuid());
					DB.share(table).delete("name=?", name);
					DB.share(table).insert(new String[]{"name", "token"}, name, token);
					this.setCookie(master + "_name", name, (int)(this.now+60*60*24*365));
					this.setCookie(master + "_token", token, (int)(this.now+60*60*24*365));
				}
			} else {
				DB.share(table).delete("name=?", name);
				this.removeCookie(master + "_name");
				this.removeCookie(master + "_token");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	//?????????????????????
	public boolean is_weixin() {
		return this.is_wx || this.is_mini;
	}


	//success
	public Object success() {
		return Common.success();
	}
	public Object success(Object data) {
		return Common.success(data);
	}
	public Object success(Object data, String msg) {
		return Common.success(data, msg);
	}
	public Object success(Object data, String msg, int msg_type) {
		return Common.success(data, msg, msg_type);
	}
	public Object success(Object data, String msg, int msg_type, Map<String, Object> element) {
		return Common.success(data, msg, msg_type, element);
	}

	//error
	public Object error() {
		return Common.error();
	}
	public Object error(String msg) {
		return Common.error(msg);
	}
	public Object error(String msg, String url) {
		return Common.error(msg, url);
	}
	public Object error(String msg, int msg_type) {
		return Common.error(msg, msg_type);
	}
	public void errorWrite() {
		Common.outputHtml((String) error());
	}
	public void errorWrite(String msg) {
		Common.outputHtml((String) error(msg));
	}
	public void errorWrite(String msg, int msg_type) {
		Common.outputHtml((String) error(msg, msg_type));
	}
}
