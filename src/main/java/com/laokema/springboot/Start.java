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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class Start {
	@RequestMapping("/**")
	Object index(HttpServletRequest request, HttpServletResponse response) {
		String ban = (String) request.getSession().getAttribute("appAct");
		int count = (ban == null || ban.length() == 0) ? 0 : Integer.parseInt(ban);
		if (count >= 3) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return null;
		}
		String uri = request.getRequestURI();
		//static
		String static_resource_dir = Common.getProperty("sdk.static.resource.dir");
		String upload_path = Common.getProperty("sdk.upload.path");
		if (uri.matches("^/(" + static_resource_dir + "|" + Common.trim(upload_path, "/") + ")/.*")) {
			String[] resource = new String[2];
			Redis redis = Common.redis();
			boolean hasRedis = redis.ping();
			if (hasRedis) {
				if (redis.hasKey(uri)) {
					resource = Common.stringToBean((String) redis.get(uri), String[].class);
					if (resource == null || resource[0] == null) return null;
					String mimeType = resource[0];
					response.setContentType(mimeType);
					try {
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
					} catch (Exception e) {
						return null;
					}
					return null;
				}
			}
			try {
				ContentInfo contentInfo = ContentInfoUtil.findExtensionMatch(uri);
				String mimeType = contentInfo != null ? contentInfo.getMimeType() : null;
				if (mimeType == null) return null;
				response.setContentType(mimeType);
				resource[0] = mimeType;
				StringBuilder sbf = new StringBuilder();
				ServletOutputStream out = response.getOutputStream();
				int len;
				byte[] buffer = new byte[1024 * 10];
				if (Common.isJarRun() && !uri.startsWith(upload_path)) {
					JarFile jarFile = new JarFile(Common.getJarPath());
					JarEntry entry = jarFile.getJarEntry("static" + uri);
					if (entry == null) return null;
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
						file = new File(Common.root(), uri);
					} else {
						file = new File(Objects.requireNonNull(this.getClass().getResource("/")).getPath(), "static" + uri);
					}
					if (!file.exists()) return null;
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
			return null;
		}
		//web
		Map<String, String> moduleMap = Common.getModule(request);
		if (moduleMap.get("setup").equalsIgnoreCase("true")) {
			if (request.getRequestURI().matches("^/(" + moduleMap.get("modules") + ")\\b.*")) {
				response.setStatus(HttpStatus.NOT_FOUND.value());
				return null;
			}
		}
		String module = moduleMap.get("module");
		String app = moduleMap.get("app");
		String act = moduleMap.get("act");
		try {
			Class<?> clazz = Class.forName((this.getClass().getPackage().getName() + "." + module).toLowerCase() + "." + Character.toUpperCase(app.charAt(0)) + app.substring(1));
			Object instance = clazz.getConstructor().newInstance();
			try {
				clazz.getMethod("__construct", HttpServletRequest.class, HttpServletResponse.class).invoke(instance, request, response);
			} catch (NoSuchMethodException e) {
				//Method不存在
			}
			return clazz.getMethod(act).invoke(instance);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			//e.printStackTrace();
			count++;
			request.getSession().setAttribute("appAct", String.valueOf(count));
		} catch (Exception e) {
			System.out.println("getMethod error in url: " + request.getRequestURI());
			e.printStackTrace();
		}
		return Common.error("@/error");
	}

	@RequestMapping("/s/{id:\\d+}")
	Object homeCode(@PathVariable int id) {
		return Common.runMethod(Home.class, "code", id);
	}
}
