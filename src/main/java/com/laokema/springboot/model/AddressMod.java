package com.laokema.springboot.model;

import com.laokema.tool.*;

public class AddressMod extends BaseMod {
	public AddressMod() {
		this.init();
	}

	//获取默认地址，已经登录的情况下才返回
	public DataMap default_address(int member_id) {
		DataMap address;
		if (member_id>0) {
			address = DB.share("address").where("member_id='"+member_id+"'").order("is_default DESC, id DESC").row();
			if (address == null) address = this._init_address();
		} else {
			address = this._init_address();
		}
		return address;
	}

	//初始化一个地址对象。
	private DataMap _init_address() {
		return DB.createInstanceDataMap("address");
	}
}
