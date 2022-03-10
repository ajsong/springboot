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
	public DataMap memberObj;
	public static JSONObject not_check_login;

	public void __construct(HttpServletRequest request, HttpServletResponse response) {
		super.__construct(request, response);

		Object access_allow = Common.getJsonProperty("sdk.access.allow");
		if ( access_allow != null ) {
			String accessAllowHost = null;
			JSONArray access_allow_host = Common.getJsonProperty("sdk.access.allow.host");
			if ( access_allow_host != null ) {
				if (access_allow_host.size() == 1 && access_allow_host.get(0).equals("*")) accessAllowHost = "*";
				else {
					String host = Common.domain();
					if (access_allow_host.contains(host)) accessAllowHost = host;
				}
			}
			boolean isContains = false;
			if (access_allow instanceof JSONArray) {
				JSONArray accessAllow = (JSONArray) access_allow;
				if (accessAllow.size() == 1 && (accessAllow.get(0) instanceof String) && accessAllow.get(0).equals("*")) isContains = true;
			} else if (access_allow instanceof JSONObject) {
				JSONObject accessAllow = (JSONObject) access_allow;
				if (accessAllow.getJSONArray(this.app) != null && accessAllow.getJSONArray(this.app).size() > 0 &&
						(accessAllow.getJSONArray(this.app).contains("*") || accessAllow.getJSONArray(this.app).contains(this.act))) isContains = true;
			}
			if (accessAllowHost != null && isContains) {
				this.response.setHeader("Access-Control-Allow-Origin", accessAllowHost);
				//this.response.setHeader("Access-Control-Allow-Origin", "*"); //允许所有地址跨域请求
				this.response.setHeader("Access-Control-Allow-Methods", "*"); //设置允许的请求方法, *表示所有, POST,GET,OPTIONS,DELETE
				this.response.setHeader("Access-Control-Allow-Credentials", "true"); //设置允许请求携带cookie, 此时origin不能用*
				this.response.setHeader("Access-Control-Allow-Headers", "x-requested-with,content-type,token,sign"); //设置头
			}
		}

		this.edition = client.getInt("edition");
		String function = client.getString("function");
		if (function != null && function.length() > 0) this.function = function.split(",");
		request.setAttribute("edition", this.edition);
		request.setAttribute("function", this.function);

		//检测系统版本权限
		//需要检查权限的方法
		JSONObject need_check_edition_actions = new JSONObject();
		DataList actions;
		if (this.getSessionDataList("client_function") != null) {
			actions = this.getSessionDataList("client_function");
		} else {
			actions = DB.share("client_function").where("status=1").cached(60*60*24*7).select("value");
			this.setSession("client_function", actions);
		}
		if (actions != null) {
			for (DataMap g : actions) {
				need_check_edition_actions.put(g.getString("value"), new JSONArray(Collections.singletonList("*")));
			}
		}
		List<String> editions = new ArrayList<>();
		if ( need_check_edition_actions.getJSONArray(this.app) != null ) {
			JSONArray param = need_check_edition_actions.getJSONArray(this.app);
			if ( param != null && !param.isEmpty() ) {
				if ( param.contains("*") ) {
					DataList rs = DB.share("menu").where("app='"+this.app+"'").cached(60*60*24*7).select("edition");
					if (rs != null) {
						for (DataMap g : rs) {
							String[] edition = g.getString("edition").split(",");
							for (String e : edition) {
								if (!editions.contains(e)) editions.add(e);
							}
						}
					}
				} else if ( param.contains(this.act) ) {
					DataList rs = DB.share("menu").where("app='"+this.app+"' AND act='"+this.act+"'").cached(60*60*24*7).select("edition");
					if (rs != null) {
						for (DataMap g : rs) {
							String[] edition = g.getString("edition").split(",");
							for (String e : edition) {
								if (!editions.contains(e)) editions.add(e);
							}
						}
					}
				}
			}
		}
		this.check_edition(editions);

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

		this.memberObj = (DataMap) this.getSession("member");
		if (this.memberObj != null) {
			this.member_id = this.memberObj.getInt("id");
			this.member_name = this.memberObj.getString("name");
			this.shop_id = this.memberObj.getInt("shop_id");
			this.sign = this.memberObj.getString("sign");
		}

		if (this.member_id <= 0) {
			if (not_check_login == null) not_check_login = Common.getJsonProperty("sdk.not.check.login");
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
	public DataMap get_member_from_sign(String sign) {
		return get_member_from_sign(sign, false);
	}
	public DataMap get_member_from_sign(String sign, boolean is_session) {
		if (sign == null || sign.length() == 0) return null;
		if (this.memberObj == null || is_session) {
			DataMap member = DB.share("member").where("sign='" + sign + "'").field("*, 0 as shop_id, null as shop, null as grade").find();
			if (member == null) {
				member = (DataMap) this.getSession("member");
				if (member == null) {
					if (is_session) {
						errorWrite("该账号已在其他设备登录", -9);
					}
					return null;
				}
			}
			if (Arrays.asList(this.function).contains("shop")) {
				DataMap shop = DB.share("shop s").left("member m", "s.member_id=m.id").where("m.id='" + member.get("id") + "'").field("s.*").find();
				if (shop != null) {
					member.put("shop_id", shop.getInt("id"));
					member.put("shop", shop);
				}
			}
			if (Arrays.asList(this.function).contains("grade")) {
				DataMap grade = DB.share("grade").where(Integer.parseInt(String.valueOf(member.get("grade_id")))).find();
				if (grade != null) {
					member.put("grade", grade);
				}
			}
			DataList thirdparty = DB.share("member_thirdparty").where(member.getInt("id")).select();
			if (thirdparty != null) {
				for (DataMap t : thirdparty) {
					String type = t.getString("type") + "_openid";
					member.put(type, t.getString("mark"));
					if (t.getString("type").equals("wechat")) member.put("openid", t.getString("mark"));
				}
			}
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
			DataMap member = this.memberObj;
			this.member_id = member.getInt("id");
			this.member_name = member.getString("name");
			this.shop_id = member.getInt("shop_id");
			this.sign = member.getString("sign");
		}
		return this.memberObj;
	}

	//是否登录
	public boolean _check_login() {
		DataMap member = (DataMap) this.getSession("member");
		if (member != null && member.getInt("id") > 0 && this.sign.length() == 0) {
			return this.get_member_from_sign(member.getString("sign"), true) != null;
		} else if (this.sign.length() > 0) {
			return this.get_member_from_sign(this.sign) != null;
		} else if (this.getCookie("member_name") != null && this.getCookie("member_token") != null) {
			member = this.cookieAccount("member_token", this.getCookie("member_name"), this.getCookie("member_token"), "sign");
			if (member != null) return this.get_member_from_sign(member.getString("sign")) != null;
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
			String queryString = this.request.getQueryString();
			queryString = (queryString != null && queryString.length() > 0) ? "?" + queryString : "";
			this.setSession("api_gourl", this.request.getRequestURI() + queryString);
			Object ret = Common.error("请登录", -100);
			try {
				if (ret instanceof String) {
					if (((String)ret).startsWith("redirect:")) {
						this.response.sendRedirect(((String) ret).replaceFirst("redirect:", ""));
					} else {
						PrintWriter out = this.response.getWriter();
						out.write((String) ret);
						out.close();
					}
				} else {
					PrintWriter out = this.response.getWriter();
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

	//检测系统版本权限与已开通功能
	@SuppressWarnings("unchecked")
	public void check_edition(Object editions) {
		if (Common.isNumeric(editions)) editions = String.valueOf(editions);
		if ((editions instanceof String) && ((String) editions).length() == 0) return;
		if (editions instanceof String) editions = ((String) editions).split(",");
		if (editions.getClass().isArray()) editions = Arrays.asList(((String[]) editions));
		if (!(editions instanceof List) || ((List<String>) editions).size() == 0) return;
		boolean function = false;
		for (String f : this.function) {
			if (((List<String>) editions).contains(f)) {
				function = true;
				break;
			}
		}
		if (!((List<String>) editions).contains(String.valueOf(this.edition)) && !function) {
			Common.error503();
		}
	}

	//检测是否开通了指定的前端
	public void check_facade(String facade) {
		check_facade(facade, false);
	}
	public void check_facade(String facade, boolean additional) {
		int count = DB.share("client_facade").where("facade='"+facade+"'").count();
		if (count == 0 && !additional) {
			Common.error503();
		}
	}

}
