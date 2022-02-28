//Developed by @mario 1.0.20220228
//https://luosimao.com
package com.laokema.tool.plugins.sms;

import com.alibaba.fastjson.JSON;
import com.laokema.tool.*;
import org.apache.commons.lang3.StringUtils;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;

public class Luosimao {
	private String api_key = "";

	public Luosimao(String api_key) {
		this.api_key = api_key;
	}

	public boolean send(String mobile, String content) {
		return send(mobile, content, 0, "【铁壳测试】");
	}
	public boolean send(String mobile, String content, int template_id, String sign) {
		if (api_key.length() == 0) {
			System.out.println("SMS LOST API KEY");
			return false;
		}
		//if (!$this->maxip()) return false;
		Map<String, String> param = new HashMap<String, String>(){
			{
				put("api_key", api_key);
				put("sign", sign);
				put("use_ssl", "false");
			}
		};
		LuosimaoApi sms = new LuosimaoApi(param);
		//发送接口，签名需在后台报备
		Map<String, String> res = sms.send(mobile, content);
		if (res != null) {
			if (res.get("error") == null) {
				System.out.println("FAILED, NO ERROR");
				return false;
			}
			if (Integer.parseInt(res.get("error")) != 0) {
				System.out.println("FAILED, CODE:" + res.get("error") + ", MSG:" + sms.get_error(Integer.parseInt(res.get("error"))) + " (" + res.get("msg") + ")");
				return false;
			}
		} else {
			System.out.println("SMS SEND ERROR: " + sms.last_error());
			return false;
		}
		//余额查询
		//Map<String, String> res = sms.get_deposit();
		//float deposit = Float.parseFloat(res.get("deposit"));
		return true;
	}

	public static class LuosimaoApi {
		//Luosimao api key
		private String _api_key = "";
		private String _voice_api_key = "";

		private String _sign = "【铁壳测试】";

		private boolean _use_ssl = false;

		private final Map<String, String> _ssl_api_url = new HashMap<String, String>(){
			{
				put("send", "https://sms-api.luosimao.com/v1/send.json");
				put("send_batch", "https://sms-api.luosimao.com/v1/send_batch.json");
				put("status", "https://sms-api.luosimao.com/v1/status.json");
				put("voice", "https://voice-api.luosimao.com/v1/verify.json");
				put("voice_status", "https://voice-api.luosimao.com/v1/status.json");
			}
		};

		private final Map<String, String> _api_url = new HashMap<String, String>(){
			{
				put("send", "http://sms-api.luosimao.com/v1/send.json");
				put("send_batch", "http://sms-api.luosimao.com/v1/send_batch.json");
				put("status", "http://sms-api.luosimao.com/v1/status.json");
				put("voice", "http://voice-api.luosimao.com/v1/verify.json");
				put("voice_status", "http://voice-api.luosimao.com/v1/status.json");
			}
		};

		private int _last_error = 0;

		/**
		 * param 配置参数
		 * api_key api秘钥，在luosimao短信后台短信->触发发送下面可查看
		 * use_ssl 启用HTTPS地址，HTTPS有一定性能损耗，可选，默认不启用
		 */
		public LuosimaoApi(Map<String, String> param) {
			if (param.get("api_key") == null || param.get("api_key").length() == 0 || param.get("voice_api_key") == null || param.get("voice_api_key").length() == 0) {
				throw new IllegalArgumentException("API KEY ERROR");
			}
			_api_key = param.get("api_key");
			_voice_api_key = param.get("voice_api_key");
			if (param.get("use_ssl") != null) _use_ssl = param.get("use_ssl").equalsIgnoreCase("true");
			if (param.get("sign") != null) _sign = param.get("sign");
		}

		//触发，单发，适用于验证码，订单触发提醒类
		public Map<String, String> send(String mobile, String message) {
			List<String> mobileList = new ArrayList<>();
			mobileList.add(mobile);
			return send_batch(mobileList, message);
		}

		//批量发送，用于大批量发送
		public Map<String, String> send_batch(List<String> mobileList, String message) {
			return send_batch(mobileList, message, "");
		}
		@SuppressWarnings("unchecked")
		public Map<String, String> send_batch(List<String> mobileList, String message, String time) {
			String api_url = !_use_ssl ? _api_url.get("send_batch") : _ssl_api_url.get("send_batch");
			String mobile_list = StringUtils.join(mobileList, ",");
			Map<String, String> param = new HashMap<>();
			param.put("mobile_list", mobile_list);
			param.put("message", message + _sign); //公共签名为【铁壳测试】, 在螺丝帽后台的短信->签名管理下可添加自定义签名
			param.put("time", time);
			String res = httpPost(_api_key, api_url, param);
			return JSON.toJavaObject(JSON.parseObject(res), Map.class);
		}

		//获取短信账号余额
		@SuppressWarnings("unchecked")
		public Map<String, String> get_deposit() {
			String api_url = !_use_ssl ? _api_url.get("status") : _ssl_api_url.get("status");
			String res = httpGet(_api_key, api_url);
			return JSON.toJavaObject(JSON.parseObject(res), Map.class);
		}

		//发送语音验证码, 支持4-6位数字验证码
		public Map<String, String> voice(String mobile) {
			return voice(mobile, "");
		}
		@SuppressWarnings("unchecked")
		public Map<String, String> voice(String mobile, String code) {
			String api_url = !_use_ssl ? _api_url.get("voice") : _ssl_api_url.get("voice");
			Map<String, String> param = new HashMap<>();
			param.put("mobile", mobile);
			param.put("code", code);
			String res = httpPost(_voice_api_key, api_url, param);
			return JSON.toJavaObject(JSON.parseObject(res), Map.class);
		}

		//获取语音验证码余额
		@SuppressWarnings("unchecked")
		public Map<String, String> get_voice_deposit() {
			String api_url = !_use_ssl ? _api_url.get("voice_status") : _ssl_api_url.get("voice_status");
			String res = httpGet(_voice_api_key, api_url);
			return JSON.toJavaObject(JSON.parseObject(res), Map.class);
		}

		/**
		 * type 接收类型，用于在服务器端接收上行和发送状态，接收地址需要在luosimao后台设置
		 * param  传入的参数，从推送的url中获取，官方文档：https://luosimao.com/docs/api/
		 */
		public Object recv(String type) {
			return recv("status", null);
		}
		public Object recv(String type, Map<String, String> param) {
			if (type.equalsIgnoreCase("status")) {
				if (param.get("batch_id") != null && param.get("mobile") != null && param.get("status") != null) { //状态
					// do record
				}
			} else if (type.equalsIgnoreCase("incoming")) { //上行回复
				if (param.get("mobile") != null && param.get("message") != null) {
					// do record
				}
			}
			return null;
		}

		/**
		 * api_url 接口地址
		 * param post参数
		 * timeout 超时时间,单位秒
		 */
		private String httpPost(String api_key, String api_url, Map<String, String> param) {
			if (api_url == null || api_url.length() == 0) {
				throw new IllegalArgumentException("api_url ERROR");
			}
			HttpURLConnection conn = null;
			BufferedReader reader = null;
			StringBuilder res = new StringBuilder();
			try {
				if (api_url.startsWith("https:")) {
					//忽略安全证书
					try {
						TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
							public X509Certificate[] getAcceptedIssuers() {return null;}
							public void checkClientTrusted(X509Certificate[] certs, String authType) {}
							public void checkServerTrusted(X509Certificate[] certs, String authType) {}
						}};
						SSLContext sc = SSLContext.getInstance("SSL");
						sc.init(null, trustAllCerts, new java.security.SecureRandom());
						HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
						HostnameVerifier allHostsValid = (hostname, session) -> true;
						HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				conn = (HttpURLConnection) new URL(api_url).openConnection();
				conn.setConnectTimeout(5000);
				conn.setUseCaches(false); //禁止缓存
				conn.setRequestProperty("Accept-Charset", "utf-8"); //设置接收编码
				conn.setRequestProperty("Connection", "keep-alive"); //开启长连接可以持续传输
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				conn.setRequestProperty("Authorization", "Basic api:key-" + api_key);
				StringBuilder postData = new StringBuilder();
				for (String key : param.keySet()) {
					postData.append("&").append(key).append("=").append(param.get(key));
				}
				byte[] bytes = Common.trim(postData.toString(), "&").getBytes(StandardCharsets.UTF_8);
				conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
				conn.setRequestMethod("POST");
				conn.setDoOutput(true);
				conn.getOutputStream().write(bytes);
				if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
					reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
					String line;
					while ((line = reader.readLine()) != null) {
						res.append(line).append("\n");
					}
				} else {
					_last_error = conn.getResponseCode();
				}
			} catch (Exception e) {
				System.out.println("请求异常\n" + api_url);
				e.printStackTrace();
			} finally {
				try {
					if (reader != null) reader.close();
					if (conn != null) conn.disconnect();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return res.toString();
		}

		/**
		 * api_url 接口地址
		 * timeout 超时时间,单位秒
		 */
		private String httpGet(String api_key, String api_url) {
			if (api_url == null || api_url.length() == 0) {
				throw new IllegalArgumentException("api_url ERROR");
			}
			HttpURLConnection conn = null;
			BufferedReader reader = null;
			StringBuilder res = new StringBuilder();
			try {
				if (api_url.startsWith("https:")) {
					//忽略安全证书
					try {
						TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
							public X509Certificate[] getAcceptedIssuers() {return null;}
							public void checkClientTrusted(X509Certificate[] certs, String authType) {}
							public void checkServerTrusted(X509Certificate[] certs, String authType) {}
						}};
						SSLContext sc = SSLContext.getInstance("SSL");
						sc.init(null, trustAllCerts, new java.security.SecureRandom());
						HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
						HostnameVerifier allHostsValid = (hostname, session) -> true;
						HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				conn = (HttpURLConnection) new URL(api_url).openConnection();
				conn.setConnectTimeout(5000);
				conn.setUseCaches(false); //禁止缓存
				conn.setRequestProperty("Accept-Charset", "utf-8"); //设置接收编码
				conn.setRequestProperty("Connection", "keep-alive"); //开启长连接可以持续传输
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				conn.setRequestProperty("Authorization", "Basic api:key-" + api_key);
				conn.connect();
				if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
					reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
					String line;
					while ((line = reader.readLine()) != null) {
						res.append(line).append("\n");
					}
				} else {
					_last_error = conn.getResponseCode();
				}
			} catch (Exception e) {
				System.out.println("请求异常\n" + api_url);
				e.printStackTrace();
			} finally {
				try {
					if (reader != null) reader.close();
					if (conn != null) conn.disconnect();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return res.toString();
		}

		public String get_error(int error) {
			String msg = "";
			switch (error) {
				case -10:msg = "验证信息失败";break; //检查api key是否和各种中心内的一致，调用传入是否正确
				case -11:msg = "接口禁用";break; //滥发违规内容，验证码被刷等，请联系客服解除
				case -20:msg = "短信余额不足";break; //进入个人中心购买充值
				case -30:msg = "短信内容为空";break; //检查调用传入参数：message
				case -31:msg = "短信内容存在敏感词";break; //修改短信内容，更换词语
				case -32:msg = "短信内容缺少签名信息";break; //短信内容末尾增加签名信息eg.【公司名称】
				case -34:msg = "签名不可用";break; //在后台 短信->签名管理下进行添加签名
				case -40:msg = "错误的手机号";break; //检查手机号是否正确
				case -41:msg = "号码在黑名单中";break; //号码因频繁发送或其他原因暂停发送，请联系客服确认
				case -42:msg = "验证码类短信发送频率过快";break; //前台增加60秒获取限制
				case -43:msg = "号码数量太多";break; //单次提交控制在10万个号码以内
				case -50:msg = "请求发送IP不在白名单内";break; //查看触发短信IP白名单的设置
				case -60:msg = "定时时间为过去";break; //检查定时的时间，取消定时或重新设定定时时间
			}
			return msg;
		}

		public String last_error(){
			return get_error(_last_error);
		}
	}
}
