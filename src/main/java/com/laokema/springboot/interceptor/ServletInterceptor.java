package com.laokema.springboot.interceptor;

import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletInterceptor implements HandlerInterceptor {
	//控制器方法被调用之前
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		/*try {
				//Method method = ((HandlerMethod) handler).getBean().getClass().getMethod("construct", HttpServletRequest.class, HttpServletResponse.class);
				//method.invoke(((HandlerMethod) handler).getBean(), request, response);
			} catch (Exception e) {
				System.out.println("getMethod error in url: " + request.getRequestURI());
				e.printStackTrace();
				return false;
			}*/
		//System.out.println(((HandlerMethod) handler).getBean().getClass().getName()); //获取控制器的名字
		//System.out.println(((HandlerMethod) handler).getMethod().getName()); //获取方法名
		if (handler instanceof ResourceHttpRequestHandler) {
			return true;
		} else return handler instanceof HandlerMethod;
	}

	//控制器的方法处理之后 如果出现异常则不调用
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {

	}

	//出不出现异常都会调用
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {

	}
}
