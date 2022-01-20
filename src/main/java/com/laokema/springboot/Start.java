package com.laokema.springboot;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Start {
	@RequestMapping("/")
	Object index() {
		return "forward:/wap";
	}
}
