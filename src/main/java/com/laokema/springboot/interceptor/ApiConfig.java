package com.laokema.springboot.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiConfig implements WebMvcConfigurer {
	public void addInterceptors(InterceptorRegistry registry) {
		//拦截有 "/cch" 前缀的路径，除了 "/cch/login","/cch/dologin"
		//registry.addInterceptor(new SysInterceptor()).addPathPatterns("/cch/**").excludePathPatterns("/cch/login", "/cch/dologin");
		registry.addInterceptor(new ServletInterceptor()).addPathPatterns("/**").excludePathPatterns("/login", "/register");
	}
}

