package com.laokema.springboot;

import com.alibaba.fastjson.JSON;
import com.j256.simplemagic.*;
import com.laokema.springboot.api.Home;
import com.laokema.tool.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@RestController
public class Start {
	@RequestMapping(value = {"/**", "/error"})
	Object index(HttpServletRequest request, HttpServletResponse response) {
		String ban = (String) request.getSession().getAttribute("appActMissing");
		int count = (ban == null || ban.length() == 0) ? 0 : Integer.parseInt(ban);
		if (count >= 3) return Common.error404(response);
		String uri = request.getRequestURI();
		//static
		if (uri.contains("favicon.ico")) return null;
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
							byte[] buffer = new byte[1024 * 4];
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
				byte[] buffer = new byte[1024 * 4];
				if (Common.isJarRun() && !uri.startsWith(upload_path)) {
					JarFile jarFile = new JarFile(Common.getJarPath());
					JarEntry entry = jarFile.getJarEntry("static" + uri);
					if (entry == null || entry.isDirectory()) return null;
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
		if (moduleMap.get("setup").equals("true")) {
			if (request.getRequestURI().matches("^/(" + moduleMap.get("modules") + ")\\b.*")) return Common.error404(response);
		}
		String module = moduleMap.get("module");
		String app = moduleMap.get("app");
		String act = moduleMap.get("act");
		try {
			Class<?> clazz = Class.forName((this.getClass().getPackage().getName() + "." + module).toLowerCase() + "." + Character.toUpperCase(app.charAt(0)) + app.substring(1));
			Object instance;
			try {
				instance = clazz.getConstructor(HttpServletRequest.class, HttpServletResponse.class).newInstance(request, response);
			} catch (Exception e) {
				instance = clazz.getConstructor().newInstance();
			}
			try {
				clazz.getMethod("__construct", HttpServletRequest.class, HttpServletResponse.class).invoke(instance, request, response);
			} catch (NoSuchMethodException e) {
				//Method?????????
			}
			return clazz.getMethod(act).invoke(instance);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			//e.printStackTrace();
			count++;
			request.getSession().setAttribute("appActMissing", String.valueOf(count));
			return Common.error("@error?type=404&tips=HERE IS NO PAGES CALLED "+app.toUpperCase()+"/"+act.toUpperCase());
		} catch (Exception e) {
			System.out.println("getMethod error in url: " + request.getRequestURI());
			e.printStackTrace();
		}
		return Common.error("@error");
	}

	@RequestMapping("/clear/static")
	Object clearStatic() {
		Redis redis = Common.redis();
		if (!redis.ping()) return "REDIS IS NOT EXIST";
		String[] staticDir = Common.getProperty("sdk.static.resource.dir").split("\\|");
		for (int i = 0; i < staticDir.length; i++) staticDir[i] = "/" + staticDir[i] + "/";
		Set<String> keys = redis.getListKey(staticDir);
		if (keys != null) redis.del(keys.toArray(new String[0]));
		return "CLEAR COMPLETE";
	}

	@RequestMapping("/s/{id:\\d+}")
	Object homeCode(@PathVariable int id) {
		return Common.runMethod(Home.class, "code", id);
	}
}
