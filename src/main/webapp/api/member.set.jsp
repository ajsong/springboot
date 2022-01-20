<%@ page import="java.net.URLEncoder" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/api/header.jsp"%>
<body class="gr">

<div class="navBar">
	<a class="left" href="/wap/member"><i class="return"></i></a>
	<div class="titleView-x">更多</div>
</div>

<div class="member-set">
	<section>
		<ul class="tableView tableView-light tableView-noMargin">
			<li>
				<a href="/wap/member/edit"><h1>个人资料</h1></a>
			</li>
			<li>
				<a href="/wap/member/password"><h1>修改密码</h1></a>
			</li>
		</ul>
		<ul class="tableView tableView-light tableView-noMargin">
			<li>
				<a href="/wap/address/index"><h1>收货地址管理</h1></a>
			</li>
		</ul>
		<ul class="tableView tableView-light tableView-noMargin">
			<li>
				<a href="/wap/feedback"><h1>我要反馈</h1></a>
			</li>
		</ul>
		<ul class="tableView tableView-light tableView-noMargin">
			<li>
				<a href="/wap/article/detail?id=help"><h1>帮助中心</h1></a>
			</li>
			<!--
			<li>
				<a href="tel://020-36603191"><h1 class="noPush"><big>020-36603191</big>客服电话</h1></a>
			</li>
			-->
			<li>
				<a href="/wap/article/detail?id=about"><h1>关于我们</h1></a>
			</li>
			<!--
			<li class="r">
				<a href="/wap/article/detail?id=join"><h1>招商加盟</h1></a>
			</li>
			-->
			<li class="r">
				<a href="/wap/passport/logout?gourl=<%=URLEncoder.encode("/member", "UTF-8")%>"><h1 class="noPush">退出登录</h1></a>
			</li>
		</ul>
	</section>
</div>

<%@ include file="/api/footer.jsp"%>