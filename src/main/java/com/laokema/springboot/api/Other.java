package com.laokema.springboot.api;

import com.laokema.tool.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/other")
public class Other extends Core {
	//上传文件
	@RequestMapping("/uploadfile")
	Object uploadfile() {
		int UPLOAD_LOCAL = Integer.parseInt(Common.get_property("upload.local"));
		String name = this.request.get("name", "filename");
		String dir = this.request.get("dir", "pic");
		int local = this.request.get("local", UPLOAD_LOCAL);
		int detail = this.request.get("detail", 0);
		String type = this.request.get("type", "jpg,jpeg,png,gif,bmp");
		if (detail == 1) {
			Map<String, Object> files = this.request.file(dir, type, true);
			if (files == null) return Common.error("请选择文件");
			return Common.success(files);
		} else {
			String file = this.request.file(name, dir, type);
			if (file.length() == 0) return Common.error("请选择文件");
			return Common.success(file);
		}
	}
}
