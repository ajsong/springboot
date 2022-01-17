package com.laokema.springboot.interceptor;

import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.laokema.tool.Common.log;

public class LoginInterceptor implements HandlerInterceptor {
	//控制器方法被调用之前
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		//获取控制器的名字
		//System.out.println(((HandlerMethod) handler).getBean().getClass().getName());
		System.out.println(((HandlerMethod) handler).getBean());
		//获取方法名
		System.out.println(((HandlerMethod) handler).getMethod().getName());

		HttpSession session = request.getSession();

		// 获取用户信息，如果没有用户信息直接返回提示信息
		Object userInfo = session.getAttribute("userInfo");
		if (userInfo == null) {
			log("没有登录");
			response.getWriter().write("Please Login In");
			return false;
		} else {
			log("已经登录过啦，用户信息为：" + session.getAttribute("userInfo"));
		}

		return true;
	}

	//控制器的方法处理之后 如果出现异常则不调用
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {

	}

	//出不出现异常都会调用
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {

	}
}
