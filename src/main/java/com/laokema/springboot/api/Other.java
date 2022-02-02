package com.laokema.springboot.api;

import com.laokema.tool.*;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.*;

public class Other extends Core {
	//上传文件
	public Object uploadfile() {
		String name = this.request.get("name", "filename");
		String dir = this.request.get("dir", "pic");
		int detail = this.request.get("detail", 0);
		String type = this.request.get("type", "jpg,jpeg,png,gif,bmp");
		if (detail == 1) {
			Map<String, Object> files = this.request.file(dir, type, uploadThird, true);
			if (files == null) return Common.error("请选择文件");
			return Common.success(files);
		} else {
			String file = this.request.file(name, dir, type, uploadThird);
			if (file.length() == 0) return Common.error("请选择文件");
			return Common.success(file);
		}
	}

	//Springboot上传文件
	public String upload(@RequestParam("filename") MultipartFile file, @RequestParam(value = "dir", defaultValue = "") String dir) {
		if (file.isEmpty()) {
			return "上传失败，请选择文件";
		}
		String uploadDir = Common.getProperty("upload.path") + (dir.length() > 0 ? "/" + dir : "");
		String filePath = Common.root() + uploadDir.replaceFirst("/", "");
		Common.makedir(filePath);

		String fileName = file.getOriginalFilename();
		assert fileName != null;
		String suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(); //获取文件后缀名
		if (suffix.equalsIgnoreCase("jpeg")) suffix = "jpg";
		String name = Common.generate_sn();
		String filename = name + "." + suffix;
		File dest = new File(filePath, filename);
		try {
			file.transferTo(dest);
			return "上传成功";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "上传失败";
	}

	//Springboot批量上传文件
	public String multiUpload(HttpServletRequest request) {
		List<MultipartFile> files = ((MultipartHttpServletRequest) request).getFiles("filename");
		String filePath = "/Users/itinypocket/workspace/temp/";
		for (int i = 0; i < files.size(); i++) {
			MultipartFile file = files.get(i);
			if (file.isEmpty()) {
				return "上传第" + (i+1) + "个文件失败";
			}
			String fileName = file.getOriginalFilename();
			File dest = new File(filePath + fileName);
			try {
				file.transferTo(dest);
			} catch (Exception e) {
				e.printStackTrace();
				return "上传第" + (i+1) + "个文件失败";
			}
		}
		return "上传成功";
	}
}
