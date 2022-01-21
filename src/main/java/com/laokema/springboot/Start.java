package com.laokema.springboot;

import com.j256.simplemagic.*;
import com.laokema.tool.Common;
import org.springframework.web.bind.annotation.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.*;

@RestController
public class Start {
	@RequestMapping("/**")
	void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String uri = request.getRequestURI();
		if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/") || uri.startsWith("/uploads/")) {
			try {
				ContentInfo contentInfo = ContentInfoUtil.findExtensionMatch(uri);
				String mimeType = contentInfo != null ? contentInfo.getMimeType() : null;
				if (mimeType == null && uri.endsWith(".svg")) mimeType = "image/svg+xml";
				if (mimeType != null) response.setContentType(mimeType);
				ServletOutputStream out = response.getOutputStream();
				int len;
				byte[] buffer = new byte[1024 * 10];
				if (Common.is_jar_run() && !uri.startsWith("/uploads/")) {
					JarFile jarFile = new JarFile(Common.get_jar_path());
					JarEntry entry = jarFile.getJarEntry("static" + uri);
					if (entry == null) return;
					InputStream ips = jarFile.getInputStream(entry);
					while ((len = ips.read(buffer)) != -1) out.write(buffer, 0, len);
					ips.close();
				} else {
					File file;
					if (uri.startsWith("/uploads/")) {
						file = new File(Common.get_root_path(), uri);
					} else {
						file = new File(Objects.requireNonNull(this.getClass().getResource("/")).getPath(), "static" + uri);
					}
					if (!file.exists()) return;
					FileInputStream ips = new FileInputStream(file);
					while ((len = ips.read(buffer)) != -1) out.write(buffer, 0, len);
					ips.close();
				}
				out.flush();
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
		if (uri.equals("/")) uri = "/wap";
		if (!uri.startsWith("/wap") && !uri.startsWith("/api")) uri = "/wap" + uri;
		request.getRequestDispatcher(uri).forward(request,response);
		//return "forward:/wap";
	}
}
