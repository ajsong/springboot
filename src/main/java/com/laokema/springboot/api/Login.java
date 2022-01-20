package com.laokema.springboot.api;

import com.laokema.tool.Common;

public class Login {
	public Object index() {
		Common.setTemplateDir("api");
		return Common.success();
	}
}
