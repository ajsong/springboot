package com.laokema.springboot;

import com.laokema.tool.Common;
import org.springframework.http.HttpStatus;
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
		String banNum = (String) request.getSession().getAttribute("banNum");
		int banCount = (banNum == null || banNum.length() == 0) ? 0 : Integer.parseInt(banNum);
		if (banCount >= 3) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return null;
		}
		Matcher matcher = Pattern.compile("^/\\w+/(\\w+)(/(\\w+))?").matcher(request.getRequestURI());
		String app = "home";
		String act = "index";
		if (matcher.find()) {
			app = matcher.group(1);
			if (matcher.group(3) != null) act = matcher.group(3);
		}
		try {
			Class<?> clazz = Class.forName(this.getClass().getName().toLowerCase() + "." + Character.toUpperCase(app.charAt(0)) + app.substring(1));
			Object instance = clazz.getConstructor().newInstance();
			try {
				clazz.getMethod("__construct", HttpServletRequest.class, HttpServletResponse.class).invoke(instance, request, response);
			} catch (NoSuchMethodException e) {
				//Method不存在
			}
			return clazz.getMethod(act).invoke(instance);
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			//e.printStackTrace();
			banCount++;
			request.getSession().setAttribute("banNum", String.valueOf(banCount));
		} catch (Exception e) {
			System.out.println("getMethod error in url: " + request.getRequestURI());
			e.printStackTrace();
		}
		return Common.error("@/error");
	}
}
