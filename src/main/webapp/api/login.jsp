<%@ page import="java.net.URLEncoder" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="header.jsp"%>
<div class="navBar navBar-transparent">
	<a class="left" href="javascript:history.back()"><i class="return"></i></a>
	<div class="titleView-x">登录</div>
	<a class="right" href="/register?url=<%if(request.getParameter("url")!=null && request.getParameter("url").length()>0){URLEncoder.encode(request.getParameter("url"),"UTF-8");}%>"><span>注册</span></a>
</div>

<div class="login-index width-wrap">
	<form action="/passport/login" method="post">
	<%if(request.getParameter("url")!=null && request.getParameter("url").length()>0){%>
	<input type="hidden" name="gourl" value="<%=request.getParameter("url")%>" />
	<%}else if(request.getSession().getAttribute("api_gourl")!=null && ((String)request.getSession().getAttribute("api_gourl")).length()>0){%>
	<input type="hidden" name="gourl" value="<%=request.getSession().getAttribute("api_gourl")%>" />
	<%}else{%>
	<input type="hidden" name="gourl" value="/member" />
	<%}%>
	<ul class="inputView">
		<li class="ge-bottom"><div><i></i><input type="tel" name="mobile" id="mobile" placeholder="请输入手机号码" /></div></li>
		<li class="ge-bottom"><div class="password"><em></em><i></i><input type="password" name="password" id="password" placeholder="请输入密码" /></div></li>
		<div class="buttonView">
			<a href="javascript:void(0)" class="btn">登录</a>
		</div>
		<a href="/forget" class="forget">忘记密码</a>
	</ul>
	<!--<input type="hidden" name="remember" value="1" />-->
	</form>
</div>
<%@ include file="footer.jsp"%>
<script>
function myFn(){
	if(!$('#mobile').val().length || !$('#password').val().length){
		$.overloadError('请输入手机号码与密码');
		return;
	}
	$('form').submit();
}
$(function(){
	$('html, body').addClass('height-wrap');
	$('#password').onkey({
		callback : function(code){
			if(code===13)myFn();
		}
	});
	$('.password em').click(function(){
		let input = $('#password');
		if(!!input.data('show')){
			input.removeData('show');
			input.prop('type', 'password');
			$(this).removeClass('x');
		}else{
			input.data('show', true);
			input.prop('type', 'text');
			$(this).addClass('x');
		}
	});
	$('.btn').click(function(){
		myFn();
		return false;
	});
});
</script>