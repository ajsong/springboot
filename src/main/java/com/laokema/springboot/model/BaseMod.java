package com.laokema.springboot.model;

import com.laokema.springboot.kernel.Kernel;
import com.laokema.tool.*;
import javax.servlet.http.*;

public class BaseMod extends Kernel {
	public Integer edition;
	public String[] function;
	public Integer member_id;
	public String member_name;
	public DataMap memberObj;
	public String sign;

	public void __construct(HttpServletRequest request, HttpServletResponse response) {
		super.__construct(request, response);
		this.member_id = 0;
		this.member_name = "";
		this.sign = this.request.get("sign");

		this.edition = client.getInt("edition");
		String function = client.getString("function");
		if (function != null && function.length() > 0) this.function = function.split(",");

		setConfigs();
		this.member();
	}

	public DataMap member() {
		if (this.memberObj != null) return this.memberObj;
		DataMap member = (DataMap) this.request.getSession().getAttribute("member");
		if (member != null) {
			this.member_id = member.getInt("id");
			this.member_name = member.getString("name");
			this.sign = member.getString("sign");
			this.memberObj = member;
			return member;
		}
		this.memberObj = DB.createInstanceDataMap("member");
		return this.memberObj;
	}
}
