package com.laokema.springboot;

import com.alibaba.fastjson.JSON;
import com.j256.simplemagic.*;
import com.laokema.springboot.api.Home;
import com.laokema.tool.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.jar.*;

@RestController
public class Start {
	@RequestMapping("/**")
	void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String banNum = (String) request.getSession().getAttribute("banNum");
		int banCount = (banNum == null || banNum.length() == 0) ? 0 : Integer.parseInt(banNum);
		if (banCount >= 3) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return;
		}
		String uri = request.getRequestURI();
		String static_resource_dir = Common.get_property("sdk.static.resource.dir");
		String upload_path = Common.get_property("sdk.upload.path");
		if (uri.matches("^/(" + static_resource_dir + "|" + Common.trim(upload_path, "/") + ")/.*")) {
			String[] resource = new String[2];
			Redis redis = Common.redis();
			boolean hasRedis = redis.ping();
			if (hasRedis) {
				if (redis.hasKey(uri)) {
					resource = Common.stringToBean((String) redis.get(uri), String[].class);
					if (resource == null || resource[0] == null) return;
					String mimeType = resource[0];
					response.setContentType(mimeType);
					ServletOutputStream out = response.getOutputStream();
					if (mimeType.startsWith("image/")) {
						byte[] buffer = Common.base64_decode(resource[1], true);
						for (int i = 0; i < buffer.length; i++) if (buffer[i] < 0) buffer[i] += 256;
						out.write(buffer);
					} else {
						InputStream ips = new ByteArrayInputStream(resource[1].getBytes());
						int len;
						byte[] buffer = new byte[1024 * 10];
						while ((len = ips.read(buffer)) != -1) out.write(buffer, 0, len);
						ips.close();
					}
					out.flush();
					out.close();
					return;
				}
			}
			try {
				ContentInfo contentInfo = ContentInfoUtil.findExtensionMatch(uri);
				String mimeType = contentInfo != null ? contentInfo.getMimeType() : null;
				if (mimeType == null) return;
				response.setContentType(mimeType);
				resource[0] = mimeType;
				StringBuilder sbf = new StringBuilder();
				ServletOutputStream out = response.getOutputStream();
				int len;
				byte[] buffer = new byte[1024 * 10];
				if (Common.is_jar_run() && !uri.startsWith(upload_path)) {
					JarFile jarFile = new JarFile(Common.get_jar_path());
					JarEntry entry = jarFile.getJarEntry("static" + uri);
					if (entry == null) return;
					InputStream ips = jarFile.getInputStream(entry);
					ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
					while ((len = ips.read(buffer)) != -1) {
						sbf.append(new String(buffer, 0, len));
						byteArray.write(buffer, 0, len);
						out.write(buffer, 0, len);
					}
					ips.close();
					if (mimeType.startsWith("image/")) {
						sbf = new StringBuilder();
						sbf.append(Common.base64_encode(byteArray.toByteArray()));
					}
					byteArray.flush();
					byteArray.close();
				} else {
					File file;
					if (uri.startsWith(upload_path)) {
						file = new File(Common.get_root_path(), uri);
					} else {
						file = new File(Objects.requireNonNull(this.getClass().getResource("/")).getPath(), "static" + uri);
					}
					if (!file.exists()) return;
					FileInputStream ips = new FileInputStream(file);
					ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
					while ((len = ips.read(buffer)) != -1) {
						sbf.append(new String(buffer, 0, len));
						byteArray.write(buffer, 0, len);
						out.write(buffer, 0, len);
					}
					ips.close();
					if (mimeType.startsWith("image/")) {
						sbf = new StringBuilder();
						sbf.append(Common.base64_encode(byteArray.toByteArray()));
					}
					byteArray.flush();
					byteArray.close();
				}
				out.flush();
				out.close();
				if (hasRedis) {
					resource[1] = sbf.toString();
					redis.set(uri, JSON.toJSONString(resource), -1);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
		String module_name = Common.get_property("sdk.module.name");
		if (!uri.matches("^/(" + module_name + ").*")) {
			String[] modules = module_name.split("\\|");
			boolean isAjax = (request.getHeader("x-requested-with") != null && request.getHeader("x-requested-with").equalsIgnoreCase("XMLHttpRequest"));
			uri = "/" + (isAjax ? modules[0] : modules[1]) + uri;
		}
		request.getRequestDispatcher(uri).forward(request, response);
		//return "forward:/wap"; //RestController改为Controller
	}

	@RequestMapping("/s/{id:\\d+}")
	Object homeCode(@PathVariable int id) {
		return Common.runMethod(Home.class, "code", id);
	}
}
