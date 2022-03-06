package com.laokema.springboot.api;

import com.laokema.tool.*;

import java.util.*;

public class Passport extends Core {

	public Object login() {
		this._clearsession();
		if (!Common.isPost()) {
			return Common.success("ok");
		}
		String mobile = this.request.get("mobile");
		String password = this.request.get("password");
		String udid = this.request.get("udid");
		DataMap member = null;
		//String openid = this.request.get("openid"); //增加判断$_GET['openid']为了区分是否主动登录
		if (mobile.length() == 0) return Common.error("手机号码不能为空");
		if (password.length() == 0) return Common.error("密码不能为空");
		member = DB.share("member").where("name|mobile", mobile, mobile).find();
		if (member == null) {
			return Common.error("账号不存在");
		}
		String crypt_password = Common.crypt_password(password, member.getString("salt"));
		if (!crypt_password.equals(member.getString("password"))) {
			return Common.error("账号或密码错误", -2);
		}
		if (member.getInt("status") == 1) {
			//推送强制下线通知
			/*if (strlen($member->udid) && $member->udid!=$udid && $push_type!='nopush') {
				$push = p('push', $push_type);
				$push->send($member->udid, '账号已在其他设备登录', array('action'=>'login', 'state'=>-100));
			}*/

			Map<String, Object> data = new HashMap<>();
			if (udid.length() > 0) {
				//清除之前登录过有相同udid的账号的udid
				DB.share("member").where("udid=?", udid).update("udid", "");
			}
			data.put("udid", udid);

			//环信登录需要原始密码
			if (password.length() > 0) {
				data.put("origin_password", password);
			}
			data.put("last_time", this.now);
			data.put("last_ip", this.ip);
			data.put("logins", "+1");
			DB.share("member").where(member.getInt("id")).update(data);
			return this._after_passport(member, true, false);
		} else {
			return Common.error("账号已经被冻结", -1);
		}
	}

	//处理登录或注册后的操作
	private Object  _after_passport(DataMap member, boolean is_login, boolean is_register) {
		if (member == null) {
			return Common.error("member is null");
		}

		//生成签名
		if (this.is_wx && is_login) {
			this.sign = member.getString("sign");
		} else {
			//不理是否微信登录都更新一下sign会好点
			this.sign = Common.generate_sign();
			member.put("sign", this.sign);
			DB.share("member").where(member.getInt("id")).update("sign", this.sign);
		}

		if (member.getString("avatar") != null && member.getString("avatar").length() > 0) {
			member.put("avatar", Common.add_domain(member.getString("avatar")));
		} else {
			member.put("avatar", Common.add_domain("/images/avatar.png"));
		}
		member.put("format_reg_time", Common.date("Y-m-d", member.getLong("reg_time")));

		//总财富
		member.put("total_price", member.getDouble("money") + member.getDouble("commission"));

		//登录与注册都需要记录openid
		/*$openid = $this->request->session('openid');
		if (strlen($openid)) {
			if (!SQL::share('member_thirdparty')->where("mark='{$openid}'")->exist()) {
				SQL::share('member_thirdparty')->insert(array('member_id'=>$member->id, 'type'=>'wechat', 'mark'=>$openid));
			}
			$_SESSION['weixin_authed'] = 1;
		}*/

		//更新在线
		DB.share("member").where(member.getInt("id")).update("session_id", this.session_id);

		//if ($is_login) $this->_check_login();

		//更新购物车
		DB.share("cart").where("session_id=?", this.session_id).update("member_id", member.getInt("id"));

		//是否已绑定手机(账号)
		member.put("is_mobile", (member.getString("name") == null || member.getString("name").length() == 0) ? 0 : 1);

		if (is_register) {
			//设置为最低等级
			if (Arrays.asList(this.function).contains("grade")) {
				/*$grade = SQL::share('grade')->where("status=1")->sort('score ASC, id ASC')->row('id, score');
				if ($grade) {
					SQL::share('member')->where($member->id)->update(array('grade_id'=>$grade->id, 'grade_score'=>$grade->score, 'grade_time'=>time()));
					$member->grade_id = $grade->id;
					$member->grade_score = $grade->score;
				}*/
			}
		}

		//获取当前等级的下个等级
		if (Arrays.asList(this.function).contains("grade")) {
			/*$score = 0;
			$grade = SQL::share('grade')->where("status=1 AND id>'{$member->grade_id}'")->sort('score ASC, id ASC')->row('score');
			if ($grade) $score = intval($grade->score);
			if ($score == 0) {
				$score = intval(SQL::share('grade')->where($member->grade_id)->value('score'));
			}
			$member->next_score = "{$score}";
			$grade = SQL::share('grade')->where($member->grade_id)->row();
			$member->grade = $grade;*/
		}

		member = this.get_member_from_sign(this.sign);
		member = Common.add_domain_deep(member, "avatar");
		member.remove("password");
		member.remove("salt");
		member.remove("withdraw_password");
		member.remove("withdraw_salt");

		this.removeSession("sms_code");
		this.removeSession("sms_mobile");
		this.removeSession("check_mobile_code");
		this.removeSession("check_mobile_mobile");
		this.removeSession("forget_sms_code");
		this.removeSession("forget_sms_mobile");

		int remember = this.request.get("remember", 0);
		if (is_login && remember != 0) {
			this.cookieAccount("member_token", member.getString("name").length() > 0 ? member.getString("name") : member.getString("mobile"));
		}

		//微信端跳转回之前查看的页面
		if (this.is_wx && !this.is_mini && is_login && Common.isWeb()) {
			String url = this.request.session("weixin_url", "/");
			return Common.location(url);
		}

		this.setSession("gourl", this.request.session("api_gourl", "/"));
		this.removeSession("api_gourl");
		return Common.success(member);
	}

	//清除session
	private void  _clearsession() {
		this.removeSession("member");
		this.member_id = 0;
	}

}
