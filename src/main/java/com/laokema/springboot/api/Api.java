package com.laokema.springboot.api;

import com.laokema.tool.Common;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import java.util.regex.*;

@RestController
@RequestMapping(value = {
	"/api",
	"/wap"
})
public class Api {
	@RequestMapping("/**")
	Object init(HttpServletRequest request, HttpServletResponse response) {
		Common.setServlet(request, response);
		String[] nonConstruct = new String[]{"/login", "/register", "/error"};
		String uri = request.getRequestURI();
		Matcher matcher = Pattern.compile("^/\\w+/(\\w+)(/(\\w+))?").matcher(uri);
		String app = "home";
		String act = "index";
		if (matcher.find()) {
			app = matcher.group(1);
			if (matcher.group(3) != null) act = matcher.group(3);
		}
		try {
			Class<?> clazz = Class.forName(this.getClass().getPackage().getName() + "." + Character.toUpperCase(app.charAt(0)) + app.substring(1));
			Object controller = clazz.getConstructor().newInstance();
			if (uri.contains("/passport") || !uri.matches(".*("+ StringUtils.join(nonConstruct, "|") +").*")) {
				clazz.getMethod("__construct", HttpServletRequest.class, HttpServletResponse.class).invoke(controller, request, response);
			}
			return clazz.getMethod(act).invoke(controller);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			//e.printStackTrace();
		} catch (Exception e) {
			System.out.println("getMethod error in url: " + uri);
			e.printStackTrace();
		}
		return Common.error("@/error");
	}
}
