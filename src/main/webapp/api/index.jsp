<%@ page import="java.util.Arrays" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<jsp:include page="header.jsp" />
<%--@ include file="header.jsp" --%>
<body class="gr">

<div class="navBar">
	<div class="titleView-x search-input"><input type="search" id="keyword" placeholder="请输入商品关键字" /></div>
</div>

<div class="home-index main-padding-bottom">
	<div class="pullRefresh">
		<c:if test="${flashes != null}">
		<div class="pageView">
			<div class="slide">
				<ul>
					<c:forEach items="${flashes}" var="g">
					<li ad_type="${g.ad_type}" ad_content="${g.ad_content}" pic="${g.pic}"></li>
					</c:forEach>
				</ul>
			</div>
			<div class="pager"></div>
		</div>
		</c:if>

		<%if(Arrays.asList((String[])request.getAttribute("function")).contains("category")){%>
		<div class="cate">
			<%if(Arrays.asList((String[])request.getAttribute("function")).contains("groupbuy")){%>
			<a class="groupbuy" href="/goods/groupbuy"><div></div><span>特价拼团</span></a>
			<%}%>
			<%if(Arrays.asList((String[])request.getAttribute("function")).contains("purchase")){%>
			<a class="purchase" href="/goods/purchase"><div></div><span>限时秒杀</span></a>
			<%}%>
			<%if(Arrays.asList((String[])request.getAttribute("function")).contains("chop")){%>
			<a class="chop" href="/goods/chop"><div></div><span>限量砍价</span></a>
			<%}%>
			<c:forEach items="${categories}" var="g">
			<a href="/goods?category_id=${g.id}&title=${g.name}"><div url="${g.pic}"></div><span>${g.name}</span></a>
			</c:forEach>
		</div>
		<%}%>

		<div class="tip2 tip"><i></i>好货推荐</div>
		<%if(request.getAttribute("recommend")!=null){%>
		<ul class="list goods-item">
			<c:forEach items="${recommend}" var="g">
			<li>
				<a href="/goods/detail?id=${g.id}">
					<div class="pic" url="${g.pic}"></div>
					<div class="title"><div>${g.name}</div><font><c:if test="${g.purchase_price>0}">正在秒杀中</c:if></font><span><strong>￥<fmt:formatNumber type="number" pattern="0.00" value="${g.price}" /></strong><s>￥<fmt:formatNumber type="number" pattern="0.00" value="${g.market_price}" /></s></span></div>
				</a>
			</li>
			</c:forEach>
		</ul>
		<%}%>
	</div>
</div>

<div class="footer">
	<a class="ico1 this" href="/"></a>
	<a class="ico2" href="/category"></a>
	<a class="ico3" href="/article"></a>
	<a class="ico4 badge" href="/cart"></a>
	<a class="ico5" href="/member"></a>
</div>

<c:import url="footer.jsp" />
<script>
var offset = $('.pullRefresh .list > li').length;
function createHtml(g){
	var html = '<li>\
		<a href="/goods/detail?id='+g.id+'">\
	        <div class="pic" url="'+g.pic+'"></div>\
	        <div class="title"><div>'+g.name+'</div><font>'+(g.purchase_price>0?'正在秒杀中':'')+'</font><span><strong>￥'+g.price.numberFormat(2)+'</strong><s>￥'+g.market_price.numberFormat(2)+'</s></span></div>\
	    </a>\
    </li>';
	offset++;
	return html;
}
function setLists(){
	$('.list a .pic').loadbackground({error: 'images/nopic.png'});
}
function resize(){
	setLists();
	$('.pageView').autoHeight(320, 153);
	$('.pageView li').css({ width:$('.pageView').width(), height:$('.pageView').height() });
	$('.pageView').touchmove({
		pager : '.pager',
		drag : true,
		auto : 4000,
		autoWait : 4000,
		complete : function(){
			$('.pager').css('margin-left', -$('.pager').width()/2);
			setAds('.pageView .slide li');
		}
	});
}
$(window).resize(resize);
$(function(){
	resize();
	setAds('.pageView .slide li');
	$('.cate a div').loadbackground({error: 'images/nopic.png'});
	$('#keyword').onkey(function(code){
		if(code===13)location.href = '/search?keyword='+$('#keyword').val();
	});
	$('.pullRefresh').pullRefresh({
		header : true,
		footer : true,
		footerNoMoreText : '- END -',
		refresh : function(fn){
			let _this = this;
			offset = 0;
			$.getJSON('index', function(json){
				if(json.error!==0){ $.overloadError(json.msg);return }
				let html = '';
				if($.isArray(json.data.recommend))for(var i=0; i<json.data.recommend.length; i++)html += createHtml(json.data.recommend[i]);
				_this.find('.list').html(html);
				setLists();
				fn();
			});
		},
		load : function(fn){
			let _this = this;
			$.getJSON('index', { offset:offset }, function(json){
				if(json.error!==0){ $.overloadError(json.msg);return }
				let html = '';
				if($.isArray(json.data.recommend))for(var i=0; i<json.data.recommend.length; i++)html += createHtml(json.data.recommend[i]);
				_this.find('.list').append(html);
				setLists();
				fn();
			});
		}
	});
});
</script>