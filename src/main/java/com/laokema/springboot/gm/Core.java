package com.laokema.springboot.gm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.laokema.springboot.kernel.Kernel;
import com.laokema.tool.Common;
import com.laokema.tool.DB;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

public class Core extends Kernel {
	public Integer edition;
	public String[] function;
	public static DB.DataMap defines;
	public DB.DataMap admin;
	public Integer admin_id;
	public String admin_name;

	public void __construct(HttpServletRequest request, HttpServletResponse response) {
		super.__construct(request, response);

		if (client == null) {
			client = DB.share("client").cached(60*60*24*3).find();
		}
		this.edition = client.getInt("edition");
		String function = client.getString("function");
		if (function != null && function.length() > 0) this.function = function.split(",");
		request.setAttribute("edition", this.edition);
		request.setAttribute("function", this.function);
		request.setAttribute("client", client.data);

		setConfigs();
		request.setAttribute("config", this.configs);

		if (defines == null) {
			defines = DB.share("client_define").field("id|client_id").cached(60*60*24*3).find();
		}
		request.setAttribute("defines", defines.data);

	}

	//是否登录
	public boolean _check_login() {
		/*DB.DataMap member = (DB.DataMap) this.getSession("member");
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
		}*/
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

}
