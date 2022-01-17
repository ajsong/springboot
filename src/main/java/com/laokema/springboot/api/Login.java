package com.laokema.springboot.api;

import com.laokema.tool.Common;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/login")
public class Login {
	@RequestMapping()
	Object index() {
		return Common.success(":/api/login");
	}
}
