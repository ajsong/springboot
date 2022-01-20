package com.laokema.springboot.api;

import com.laokema.tool.Common;

public class Login {
	public Object index() {
		return Common.success("@/api/login");
	}
}
