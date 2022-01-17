package com.laokema.springboot.interceptor;

import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;

public class ServletInterceptor implements HandlerInterceptor {
	//控制器方法被调用之前
	//@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		String uri = request.getRequestURI();
		if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/") || uri.startsWith("/uploads/") || uri.startsWith("/uploadfiles/")) {
			return true;
		}
		if (uri.equals("/error")) return false;
		try {
			Method method = ((HandlerMethod) handler).getBean().getClass().getMethod("construct", HttpServletRequest.class, HttpServletResponse.class);
			method.invoke(((HandlerMethod) handler).getBean(), request, response);
		} catch (Exception e) {
			System.out.println(uri);
			return false;
		}
		//System.out.println(((HandlerMethod) handler).getBean().getClass().getName()); //获取控制器的名字
		//System.out.println(((HandlerMethod) handler).getMethod().getName()); //获取方法名
		return true;
	}

	//控制器的方法处理之后 如果出现异常则不调用
	//@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {

	}

	//出不出现异常都会调用
	//@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {

	}
}
