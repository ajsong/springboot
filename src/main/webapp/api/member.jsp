<%@ page import="java.util.Arrays" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="header.jsp"%>
<body class="gr">

<div class="navBar navBar-hidden">
	<a class="left sign" href="/member/sign"><i class="member-sign"></i></a>
	<div class="titleView-x">会员</div>
	<a class="right" href="/member/set"><i class="member-set"></i></a>
</div>

<div class="member-index main-bottom width-wrap">
	<div class="topView">
		<div class="infoView">
			<c:if test="${member_id>0}">
			<c:if test="${member.avatar.length()>0}"><div class="avatar" style="background-image:url(${member.avatar});"></div></c:if>
			<span><c:if test="${member.nick_name.length()>0}">${member.nick_name}</c:if><c:if test="${member.nick_name.length()==0}">${member.name}</c:if></span>
			</c:if>
			<c:if test="${member_id==0}">
			<div class="avatar"></div>
			<div class="btnView">
				<a href="/login">登录</a>
				<a href="/register">注册</a>
			</div>
			</c:if>
		</div>
		<div class="moneyView">
			<a href="/member/money"><div><i></i><span>我的余额<small><c:if test="${member_id>0}"><fmt:formatNumber type="number" value="${member.money}" pattern="0.00"/></c:if><c:if test="${member_id==0}">0.00</c:if></small></span></div></a>
			<c:if test="${edition>2}">
			<a class="ge-left" href="/member/commission"><div><i class="commission"></i><span>我的佣金<small><c:if test="${member_id>0}"><fmt:formatNumber type="number" value="${member.commission}" pattern="0.00"/></c:if><c:if test="${member_id==0}">0.00</c:if></small></span></div></a>
			</c:if>
		</div>
	</div>

	<section>
		<ul class="tableView tableView-light">
			<li><a href="/order"><h1><big>全部订单</big><em class="ico1"></em>我的订单</h1></a></li>
			<li class="orderList">
				<a class="ico1 badge" href="/order/index?status=0"><div><sub></sub></div></a>
				<a class="ico2 badge" href="/order/index?status=1"><div><sub></sub></div></a>
				<a class="ico3 badge" href="/order/index?status=2"><div><sub></sub></div></a>
				<a class="ico4 badge" href="/order/index?status=3"><div><sub></sub></div></a>
			</li>
		</ul>

		<%if(Arrays.asList((String[])request.getAttribute("function")).contains("shop")){%>
		<ul class="tableView tableView-light ge-top ge-light">
			<li><a href="/member/business"><h1><em class="ico12"></em>我是商家</h1></a></li>
		</ul>
		<%}%>

		<c:if test="${edition>2}">
		<ul class="tableView tableView-light ge-top ge-light">
			<%if(Arrays.asList((String[])request.getAttribute("function")).contains("groupbuy")){%><li><a href="/groupbuy/index"><h1><em class="ico13"></em>我的拼团</h1></a></li><%}%>
			<%if(Arrays.asList((String[])request.getAttribute("function")).contains("chop")){%><li><a href="/chop/index"><h1><em class="ico14"></em>我发起的砍价</h1></a></li><%}%>
			<%if(Arrays.asList((String[])request.getAttribute("function")).contains("integral")){%>
			<li><a href="/member/integral"><h1><em class="ico3"></em>我的积分</h1></a></li>
			<li><a href="/goods/index?integral=1"><h1><em class="ico5"></em>积分商城</h1></a></li>
			<li><a href="/order/index?integral_order=1"><h1><em class="ico2"></em>积分商城订单</h1></a></li>
			<%}%>
		</ul>
		</c:if>

		<ul class="tableView tableView-light ge-top ge-light">
			<%if(Arrays.asList((String[])request.getAttribute("function")).contains("coupon")){%>
			<li><a href="/coupon/index?status=1"><h1><c:if test="${coupon_count>0}"><big>${coupon_count}张</big></c:if><em class="ico4"></em>我的优惠券</h1></a></li>
			<%}%>
			<c:if test="${edition>2}"><li><a href="/member/code"><h1><em class="ico7"></em>分享赚佣金</h1></a></li></c:if>
			<li><a href="/message/index"><h1><em class="ico8"></em>我的消息</h1></a></li>
			<li><a href="/favorite/index?type_id=1"><h1><em class="ico9"></em>我的收藏</h1></a></li>
			<li><a href="/member/goods_history"><h1><em class="ico10"></em>足迹</h1></a></li>
		</ul>

		<ul class="tableView tableView-light ge-top ge-light">
			<li><a href="/member/set"><h1><em class="ico11"></em>设置</h1></a></li>
		</ul>
	</section>
</div>

<div class="footer">
	<a class="ico1" href="/"></a>
	<a class="ico2" href="/category"></a>
	<a class="ico3" href="/article"></a>
	<a class="ico4 badge" href="/cart"></a>
	<a class="ico5 this" href="/member"></a>
</div>
  
<%@ include file="footer.jsp"%>
<script>
$(function(){
	$('.navBar .badge sub').html('<c:if test="${cart_total>0}"><b>${cart_total}</b></c:if>');
	$('.orderList .ico1 sub').html('<c:if test="${cart_total>0}">${not_pay}</c:if>');
	$('.orderList .ico2 sub').html('<c:if test="${cart_total>0}">${not_shipping}</c:if>');
	$('.orderList .ico3 sub').html('<c:if test="${cart_total>0}">${not_confirm}</c:if>');
	$('.orderList .ico4 sub').html('<c:if test="${cart_total>0}">${not_comment}</c:if>');
	$('.navBar .sign, .navBar .right, .moneyView a, section a').checklogin();
	$(window).scroll(function(){
		if($(window).scrollTop()>=$('.topView').height()-$('.navBar').height()){
			$('.navBar').removeClass('navBar-hidden');
		}else{
			$('.navBar').addClass('navBar-hidden');
		}
	});
});
</script>
