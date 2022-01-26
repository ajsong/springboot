package com.laokema.springboot;

import com.laokema.tool.Common;
import com.laokema.tool.Request;
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
		String uri = request.getRequestURI();
		Matcher matcher = Pattern.compile("^/\\w+/(\\w+)(/(\\w+))?").matcher(uri);
		String app = "home";
		String act = "index";
		if (matcher.find()) {
			app = matcher.group(1);
			if (matcher.group(3) != null) act = matcher.group(3);
		}
		try {
			Class<?> clazz = Class.forName(this.getClass().getName().toLowerCase() + "." + Character.toUpperCase(app.charAt(0)) + app.substring(1));
			Object controller = clazz.getConstructor().newInstance();
			try {
				clazz.getMethod("__construct", HttpServletRequest.class, HttpServletResponse.class).invoke(controller, request, response);
			} catch (NoSuchMethodException e) {
				// Method不在当前类定义,继续向上转型
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
