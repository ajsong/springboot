package com.laokema.springboot.kernel;

import com.laokema.tool.*;
import com.laokema.tool.plugins.upload.Qiniu;
import org.springframework.stereotype.Controller;
import javax.servlet.http.*;
import java.io.*;
import java.net.*;
import java.util.*;

@Controller
public class Kernel {
	public String app;
	public String act;
	public HttpServletRequest servletRequest;
	public HttpServletResponse servletResponse;
	public String session_id;
	public Request request;
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
	public static DB.DataMap client;

	public void __construct(HttpServletRequest request, HttpServletResponse response) {
		Map<String, String> moduleMap = Common.getModule(request);
		this.app = moduleMap.get("app");
		this.act = moduleMap.get("act");

		Common.setServlet(request, response);
		this.servletRequest = request;
		this.servletResponse = response;
		this.session_id = request.getSession().getId().toLowerCase();
		this.request = new Request(request, response);
		this.now = Common.time();
		this.ip = Common.ip();
		this.headers = Common.getHeaders();
		this.is_wx = Common.isWX();
		this.is_mini = Common.isMini();
		this.is_wap = Common.isWap();
		this.is_web = Common.isWeb();

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
			int UPLOAD_LOCAL = Common.getProperty("sdk.upload.local", 1);
			if (UPLOAD_LOCAL == 0) {
				String uploadType = client.getString("upload_type");
				if (uploadType != null && uploadType.length() > 0) {
					uploadThird = new HashMap<>();
					String[] uploadFields = client.getString("upload_fields").split("\\|");
					if (uploadType.equalsIgnoreCase("qniu")) {
						uploadThird.put("package", Qiniu.class);
						for (String field : uploadFields) {
							String[] fields = field.split("：");
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

	//获取/设置Session
	public Object getSession(String key) {
		return this.servletRequest.getSession().getAttribute(key);
	}
	@SuppressWarnings("unchecked")
	public <T> T getSession(String key, Class<T> clazz) {
		Object value = this.getSession(key);
		if (value == null) return null;
		if (value instanceof Map) value = Common.mapToBean((Map<String, Object>) value, clazz);
		if (value != null && value.getClass() != clazz) return null;
		return (T) value;
	}
	public void setSession(String key, Object value) {
		if (value == null) {
			removeSession(key);
		} else {
			this.servletRequest.getSession().setAttribute(key, value);
		}
	}
	public void removeSession(String key) {
		this.servletRequest.getSession().removeAttribute(key);
	}

	//获取/设置Cookie
	public String getCookie(String key) {
		try {
			Cookie[] cookies = this.servletRequest.getCookies();
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
			if (expiry > 0) cookie.setMaxAge(expiry); //有效时长(单位秒), 默认为-1, 页面关闭就失效
			cookie.setPath("/"); //设置访问该域名下某个路径时生效
			this.servletResponse.addCookie(cookie);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	public void removeCookie(String key) {
		Cookie cookie = new Cookie(key, "");
		cookie.setMaxAge(0);
		cookie.setPath("/");
		this.servletResponse.addCookie(cookie);
	}

	//加载配置参数
	public void setConfigs() {
		this.configs = new HashMap<>();
		List<DB.DataMap> CONFIG = DB.share("op_config").field("name, content").cached(60*60*24*3).select();
		for (DB.DataMap g : CONFIG) this.configs.put((String) g.get("name"), (String) g.get("content"));
		CONFIG = DB.share("config").field("name, content").cached(60*60*24*3).select();
		for (DB.DataMap g : CONFIG) this.configs.put((String) g.get("name"), (String) g.get("content"));
	}

	//通过COOKIE获取账号资料,token为空字符串时插入记录,为null时删除记录, 需创建对应token表, 表_token, name:16, token:32
	public DB.DataMap cookieAccount(String table, String name) {
		return cookieAccount(table, name, "");
	}
	public DB.DataMap cookieAccount(String table, String name, String token) {
		return cookieAccount(table, name, token, "m.*");
	}
	public DB.DataMap cookieAccount(String table, String name, String token, String field) {
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

	//是否微信端打开
	public boolean is_weixin() {
		return this.is_wx || this.is_mini;
	}
}
