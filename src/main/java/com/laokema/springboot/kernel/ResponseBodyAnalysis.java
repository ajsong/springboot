//Developed by @mario 1.0.20220310
package com.laokema.springboot.kernel;

import com.laokema.tool.Common;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class ResponseBodyAnalysis implements ResponseBodyAdvice<Object> {
	@Override
	public Object beforeBodyWrite(Object body, MethodParameter arg1, MediaType arg2, Class arg3, ServerHttpRequest arg4, ServerHttpResponse arg5) {
		Common.destroy();
		return body;
	}
	@Override
	public boolean supports(MethodParameter arg0, Class arg1) {
		return true;
	}
}
