<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!doctype html>
<html lang="zh-CN" style="font-size:100px;">
<head>
<meta name="viewport" content="width=320,minimum-scale=1.0,maximum-scale=1.0,initial-scale=1.0,user-scalable=0" />
<meta name="format-detection" content="telephone=no" />
<meta name="format-detection" content="email=no" />
<meta name="format-detection" content="address=no" />
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta charset="UTF-8">
<title>${tips}</title>
</head>

<body>
<style>
html, body{height:100%; margin:0; padding:0; position:relative; text-align:center; font-family:Arial, serif; background:#fff; -webkit-font-smoothing:antialiased;}
.tip-view{position:absolute; left:0; top:50%; width:100%; height:auto; margin-top:-0.5rem; -webkit-transform:translateY(-50%); transform:translateY(-50%);}
.tip-view i{display:block; margin:0 auto; width:1.5rem; height:1.5rem; background:url("data:image/png;base64,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") no-repeat center center; background-size:cover;}
.tip-view span{display:block; width:100%; height:0.34rem; line-height:0.34rem; font-size:0.18rem;}
.tip-view font{display:block; width:100%; height:0.2rem; line-height:0.2rem; font-size:0.14rem; color:#ccc;}
.tip-view strong{font-weight:normal;}
</style>
<div class="tip-view">
	<i style="background-image:url(/images/404.svg);width:2.5rem;height:2.5rem;"></i>
	<span style="margin-top:0.2rem;color:#999;">${tips}</span>
	<font>That will be return after at <strong>5</strong>s</font>
</div>
</body>
</html>
<script>
let count = 5, timer = setInterval(function() {
	if (count <= 0) {
		clearInterval(timer);timer = null;
		history.back();
		return;
	}
	count--;
	let strong = document.getElementsByTagName('strong');
	if (strong.length) strong[0].innerHTML = count;
}, 1000);
</script>