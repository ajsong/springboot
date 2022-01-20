package com.laokema.springboot.api;

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.laokema.springboot.kernel.Kernel;
import com.laokema.tool.*;
import javax.servlet.http.*;
import java.io.PrintWriter;
import java.util.*;

public class Core extends Kernel {
	public Integer edition;
	public String[] function;
	public Integer member_id;
	public String member_name;
	public Integer shop_id;
	public String sign;
	public DB.DataMap memberObj;

	public void __construct(HttpServletRequest request, HttpServletResponse response) {
		super.__construct(request, response);
		Common.setTemplateDir("api");

		if (client == null) {
			client = DB.share("client").cached(60*60*24*3).find();
		}
		this.edition = (Integer) client.get("edition");
		String function = (String) client.get("function");
		if (function != null && function.length() > 0) this.function = function.split(",");
		request.setAttribute("edition", this.edition);
		request.setAttribute("function", this.function);

		setConfigs();
		request.setAttribute("config", this.configs);

		this.member_id = 0;
		this.member_name = "";
		this.shop_id = 0;
		this.sign = this.request.get("sign");
		if (this.sign == null || this.sign.length() == 0) this.sign = this.headers.get("sign");
		if (this.sign == null || this.sign.length() == 0) this.sign = this.headers.get("Sign");
		if (this.sign == null || this.sign.length() == 0) this.sign = this.headers.get("token");
		if (this.sign == null || this.sign.length() == 0) this.sign = this.headers.get("Token");
		if (this.sign == null) this.sign = "";
		if (this.sign.length() > 0) this._check_login();

		DB.DataMap member = (DB.DataMap) this.getSession("member");
		if (member != null) {
			this.member_id = member.getInt("id");
			this.member_name = member.getString("name");
			this.shop_id = member.getInt("shop_id");
			this.sign = member.getString("sign");
		}

		if (this.member_id <= 0) {
			JSONObject not_check_login = Common.get_json_property("not_check_login");
			if ( this.is_wap && !not_check_login.isEmpty() && not_check_login.getJSONObject("wap") != null && !not_check_login.getJSONObject("wap").isEmpty() ) {
				JSONObject obj = not_check_login.getJSONObject("wap");
				JSONObject global = not_check_login.getJSONObject("global");
				if ( global != null && !global.isEmpty() ) {
					for (String key : global.keySet()) obj.put(key, global.get(key));
				}
				JSONArray param = obj.getJSONArray(this.app);
				if ( param == null || param.isEmpty() ) {
					if (!this.check_login()) return;
				} else {
					if ( !param.contains("*") && !param.contains(this.act) ) {
						if (!this.check_login()) return;
					} else if ( this.headers.get("Authorization") != null && this.headers.get("Authorization").length() > 0 ) {
						if (!this.check_login()) return;
					}
				}
			}
			if ( this.is_web && !not_check_login.isEmpty() && not_check_login.getJSONObject("web") != null && !not_check_login.getJSONObject("web").isEmpty() ) {
				JSONObject obj = not_check_login.getJSONObject("web");
				JSONObject global = not_check_login.getJSONObject("global");
				if ( global != null && !global.isEmpty() ) {
					for (String key : global.keySet()) obj.put(key, global.get(key));
				}
				JSONArray param = obj.getJSONArray(this.app);
				if ( param == null || param.isEmpty() ) {
					if (!this.check_login()) return;
				} else {
					if ( !param.contains("*") && !param.contains(this.act) ) {
						if (!this.check_login()) return;
					} else if ( this.headers.get("Authorization") != null && this.headers.get("Authorization").length() > 0 ) {
						if (!this.check_login()) return;
					}
				}
			}
			if ( this.is_mini && !not_check_login.isEmpty() && not_check_login.getJSONObject("mini") != null && !not_check_login.getJSONObject("mini").isEmpty() ) {
				JSONObject obj = not_check_login.getJSONObject("mini");
				JSONObject global = not_check_login.getJSONObject("global");
				if ( global != null && !global.isEmpty() ) {
					for (String key : global.keySet()) obj.put(key, global.get(key));
				}
				JSONArray param = obj.getJSONArray(this.app);
				if ( param == null || param.isEmpty() ) {
					if (!this.check_login()) return;
				} else {
					if ( !param.contains("*") && !param.contains(this.act) ) {
						if (!this.check_login()) return;
					} else if ( this.headers.get("Authorization") != null && this.headers.get("Authorization").length() > 0 ) {
						if (!this.check_login()) return;
					}
				}
			}
			if ( !not_check_login.isEmpty() && not_check_login.getJSONObject("global") != null && !not_check_login.getJSONObject("global").isEmpty() ) {
				JSONArray param = not_check_login.getJSONObject("global").getJSONArray(this.app);
				if ( param == null || param.isEmpty() ) {
					this.check_login();
				} else {
					if ( !param.contains("*") && !param.contains(this.act) ) {
						this.check_login();
					} else if ( this.headers.get("Authorization") != null && this.headers.get("Authorization").length() > 0 ) {
						this.check_login();
					}
				}
			}
		}
	}

	//get member info from sign
	public DB.DataMap get_member_from_sign(String sign) {
		return get_member_from_sign(sign, false);
	}
	public DB.DataMap get_member_from_sign(String sign, boolean is_session) {
		if (sign == null || sign.length() == 0) return null;
		if (this.memberObj == null || is_session) {
			DB.DataMap member = DB.share("member").where("sign='" + sign + "'").field("*, 0 as shop_id, null as shop, null as grade").find();
			if (member == null) {
				member = (DB.DataMap) this.getSession("member");
				if (member == null) {
					if (is_session) {
						Common.error("该账号已在其他设备登录", -9);
					}
					return null;
				}
			}
			if (Arrays.asList(this.function).contains("shop")) {
				DB.DataMap shop = DB.share("shop s").left("member m", "s.member_id=m.id").where("m.id='" + member.get("id") + "'").field("s.*").find();
				if (shop != null) {
					member.put("shop_id", shop.getInt("id"));
					member.put("shop", shop);
				}
			}
			if (Arrays.asList(this.function).contains("grade")) {
				DB.DataMap grade = DB.share("grade").where(Integer.parseInt(String.valueOf(member.get("grade_id")))).find();
				if (grade != null) {
					member.put("grade", grade);
				}
			}
			/*$thirdparty = SQL::share('member_thirdparty')->where($member->id)->find();
			if ($thirdparty) {
				foreach ($thirdparty as $t) {
					$type = "{$t->type}_openid";
					$member->{$type} = $t->mark;
					if ($t->type=='wechat') $member->openid = $t->mark;
				}
			}*/
			this.member_id = (Integer) member.get("id");
			this.member_name = (String) member.get("name");
			this.shop_id = Integer.parseInt(String.valueOf(member.get("shop_id")));
			this.sign = (String) member.get("sign");
			member.put("total_price", ((double)member.get("money")) + ((double)member.get("commission"))); //总财富
			member = Common.add_domain_deep(member, new String[]{"avatar", "pic"});
			member.remove("origin_password");
			member.remove("salt");
			member.remove("withdraw_salt");
			this.setSession("member", member);
			this.memberObj = member;
		} else {
			DB.DataMap member = this.memberObj;
			this.member_id = member.getInt("id");
			this.member_name = member.getString("name");
			this.shop_id = member.getInt("shop_id");
			this.sign = member.getString("sign");
		}
		return this.memberObj;
	}

	//是否登录
	public boolean _check_login() {
		DB.DataMap member = (DB.DataMap) this.getSession("member");
		if (member != null && ((int)member.get("id")) > 0 && this.sign.length() == 0) {
			return this.get_member_from_sign((String) member.get("sign"), true) != null;
		} else if (this.sign.length() > 0) {
			return this.get_member_from_sign(this.sign) != null;
		} else if (this.getCookie("member_name") != null && this.getCookie("member_token") != null) {
			member = this.cookieAccount("member_token", this.getCookie("member_name"), this.getCookie("member_token"), "sign");
			if (member != null) return this.get_member_from_sign((String) member.get("sign")) != null;
		} else if (this.headers.get("Authorization") != null && this.headers.get("Authorization").length() > 0) {
			if (this.headers.get("Authorization").toLowerCase().contains("basic")) {
				String sign = Common.base64_decode(this.headers.get("Authorization").substring(6));
				if (sign.length() > 0) return this.get_member_from_sign(sign) != null;
			}
		}
		return false;
	}

	//对是否登录函数的封装，如果登录了，则返回true，
	//否则，返回错误信息：-100，APP需检查此返回值，判断是否需要重新登录
	public boolean check_login(){
		if (!this._check_login()) {
			String queryString = this.servletRequest.getQueryString();
			queryString = (queryString != null && queryString.length() > 0) ? "?" + queryString : "";
			this.setSession("api_gourl", this.servletRequest.getRequestURI() + queryString);
			Object ret = Common.error("请登录", -100);
			try {
				if (ret instanceof String) {
					if (((String)ret).startsWith("redirect:")) {
						this.servletResponse.sendRedirect(((String) ret).replaceFirst("redirect:", ""));
					} else {
						PrintWriter out = this.servletResponse.getWriter();
						out.write((String) ret);
						out.close();
					}
				} else {
					PrintWriter out = this.servletResponse.getWriter();
					out.write(JSON.toJSONString(ret, SerializerFeature.WriteMapNullValue));
					out.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return false;
		}
		return true;
	}

}
