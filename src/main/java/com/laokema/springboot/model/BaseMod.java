package com.laokema.springboot.model;

import com.laokema.tool.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.*;
import java.util.*;

public class BaseMod {
	public long now;
	public Request request;
	public HttpServletResponse response;
	public Integer edition;
	public String[] function;
	public Integer member_id;
	public String member_name;
	public String sign;
	public Map<String, String> configs;
	public static DB.DataMap client;

	public void init() {
		ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		HttpServletResponse response = Objects.requireNonNull(servletRequestAttributes).getResponse();
		this.now = Common.time();
		this.sign = "";
		this.request = new Request();
		this.response = response;
		if (client == null) {
			client = DB.share("client").cached(60*60*24*3).find();
		}
		this.edition = client.getInt("edition");
		String function = client.getString("function");
		if (function != null && function.length() > 0) this.function = function.split(",");

		this.configs = new HashMap<>();
		DB.DataList CONFIG = DB.share("op_config").field("name, content").cached(60*60*24*3).select();
		for (DB.DataMap g : CONFIG) this.configs.put((String) g.get("name"), (String) g.get("content"));
		CONFIG = DB.share("config").field("name, content").cached(60*60*24*3).select();
		for (DB.DataMap g : CONFIG) this.configs.put((String) g.get("name"), (String) g.get("content"));

		this.member();
	}

	public DB.DataMap member() {
		this.member_id = 0;
		this.member_name = "";
		this.sign = this.request.get("sign");
		DB.DataMap member = (DB.DataMap) this.request.getSession().getAttribute("member");
		if (member != null) {
			this.member_id = member.getInt("id");
			this.member_name = member.getString("name");
			this.sign = member.getString("sign");
		}
		return member;
	}
}
