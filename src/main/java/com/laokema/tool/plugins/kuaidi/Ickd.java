//Developed by @mario 1.0.20220219
//爱查快递
//http://www.ickd.cn
package com.laokema.tool.plugins.kuaidi;

import com.alibaba.fastjson.*;
import com.laokema.tool.*;
import java.util.*;
import java.util.regex.*;

public class Ickd {

	public String[] get(String spellName, String mailNo) {
		if (spellName == null || spellName.length() == 0 || mailNo == null || mailNo.length() == 0) {
			Common.error("#error?tips=缺少快递单号或订单编号");
			return null;
		}
		String index = "";
		Map<String, String> kd = company();
		Matcher matcher = Pattern.compile("[\\u4E00-\\u9FA5]").matcher(spellName);
		if (matcher.find()) {
			String companyName = spellName.replace("快递", "").replace("物流", "").replace("快运", "").replace("速递", "").replace("速运", "");
			for (String key : kd.keySet()) {
				if (kd.get(key).contains(companyName)) {
					index = key;
					break;
				}
			}
		} else if (kd.get(spellName.toLowerCase()) != null) {
			index = spellName.toLowerCase();
		}
		if (index.length() == 0) {
			Common.error("#error?tips=没有该物流公司代号: "+spellName);
			return null;
		}
		String data = Common.requestUrl("get", "http://biz.trace.ickd.cn/"+index+"/"+mailNo+"?callback=callback");
		if (data != null && data.contains("callback(")) {
			String result = data.replace("callback(", "");
			result = result.replaceAll("<span[^>]+?>", "");
			result = result.replace("</span>", "");
			result = result.replaceAll("</?a[^>]*?>", "");
			result = result.substring(0, result.length() - 2);
			JSONObject json = JSON.parseObject(result);
			if (json != null) {
				if (json.getInteger("errCode") == 0 && json.getJSONObject("data").getClass().isArray()) {
					return json.getObject("data", String[].class);
				} else {
					Common.writeLog(json.getString("message"), "kuaidi.txt");
				}
			} else {
				Common.writeLog(data, "kuaidi.txt");
			}
		} else {
			Common.writeLog("爱查快递接口发生异常", "kuaidi.txt");
		}
		return Common.runMethod(getClass().getPackage().getName()+".Kd100", "get", spellName, mailNo);
	}

	private Map<String, String> company() {
		return new HashMap<String, String>(){
			{
				put("ems", "EMS快递");
				put("shunfeng", "顺丰快递");
				put("shentong", "申通快递");
				put("yuantong", "圆通快递");
				put("yunda", "韵达快递");
				put("huitong", "汇通快递");
				put("tiantian", "天天快递");
				put("zhongtong", "中通快递");
				put("zhaijisong", "宅急送快递");
				put("pingyou", "中国邮政");
				put("quanfeng", "全峰快递");
				put("guotong", "国通快递");
				put("kuaijie", "快捷快递");
				put("jingdong", "京东快递");
				put("ririshun", "日日顺物流");
				put("longbang", "龙邦快递");
				put("debang", "德邦物流");
				put("rufeng", "如风达快递");
				put("quanritong", "全日通快递");
				put("jiaji", "佳吉快运");
				put("dhl", "DHL快递");
			}
		};
	}
}
