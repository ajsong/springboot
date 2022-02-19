//Developed by @mario 1.0.20220219
//快递鸟 - 物流公司对应字母
//http://www.kdniao.com/YundanChaxunAPI.aspx
package com.laokema.tool.plugins.kuaidi;

import com.alibaba.fastjson.*;
import com.laokema.tool.*;
import java.util.*;
import java.util.regex.*;

public class Kdniao {

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
		String EBusinessID = "1256920"; //电商ID
		String AppKey = "e7bbede8-6d12-439f-9ebf-d835613b638f"; //电商加密私钥
		String requestData = "OrderCode=&ShipperCode="+index+"&LogisticCode="+mailNo;
		String dataSign = Common.url_encode(Common.base64_encode(Common.md5(requestData + AppKey)));
		Map<String, Object> postData = new LinkedHashMap<>();
		postData.put("EBusinessID", EBusinessID);
		postData.put("RequestType", "1002");
		postData.put("RequestData", Common.url_encode(requestData));
		postData.put("DataType", "2");
		postData.put("DataSign", dataSign);
		JSONObject json = Common.requestUrl("post", "http://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx", postData, true);
		if (json != null) {
			if (json.getInteger("Success") == 1) {
				//json.getString("State") 2:在途中, 3:签收, 4:问题件
				String result = JSON.toJSONString(json);
				result = result.replace("\"Traces\":", "\"data\":");
				result = result.replace("\"AcceptStation\":", "\"context\":");
				result = result.replace("\"AcceptTime\":", "\"time\":");
				result = result.replaceAll("</?a[^>]*?>", "");
				json = JSON.parseObject(result);
				if (json != null && json.getJSONObject("data").getClass().isArray()) {
					/*String[] res = json.getObject("data", String[].class);
					String[] reverse = new String[res.length];
					List<String> list = new ArrayList<>(Arrays.asList(res));
					Collections.reverse(list);
					for (int i = 0; i < res.length; i++) {
						reverse[i] = list.get(i);
					}
					return reverse;*/
					return json.getObject("data", String[].class);
				} else {
					//return Common.runMethod(getClass().getPackage().getName()+".Ickd", "get", spellName, mailNo);
					return null;
				}
			} else {
				Common.writeLog(json.getString("Reason"), "kuaidi.txt");
			}
		} else {
			Common.writeLog("快递鸟接口发生异常", "kuaidi.txt");
		}
		return null;
	}

	private Map<String, String> company() {
		return new HashMap<String, String>(){
			{
				put("EMS", "EMS快递");
				put("SF", "顺丰快递");
				put("STO", "申通快递");
				put("YTO", "圆通快递");
				put("YD", "韵达快递");
				put("HTKY", "汇通快递");
				put("HHTT", "天天快递");
				put("ZTO", "中通快递");
				put("ZJS", "宅急送快递");
				put("YZPY", "中国邮政");
				put("QFKD", "全峰快递");
				put("GTO", "国通快递");
				put("FAST", "快捷快递");
				put("JD", "京东快递");
				put("LB", "龙邦快递");
				put("DBL", "德邦物流");
				put("RFD", "如风达快递");
				put("QRT", "全日通快递");
				put("JJKY", "佳吉快运");
				put("DHL", "DHL快递");
			}
		};
	}
}
