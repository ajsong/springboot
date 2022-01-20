package com.laokema.springboot.api;

import com.laokema.tool.*;

import java.util.*;

public class Member extends Core {
	public Object index() {
		int not_pay = 0, not_shipping = 0, not_confirm = 0, not_comment = 0, notify = 0, coupon_count = 0;
		DB.DataMap member = null;
		if (this.member_id > 0) {
			not_pay = _get_status_order_count(0);
			not_shipping = _get_status_order_count(1);
			not_confirm = _get_status_order_count(2);
			not_comment = _get_status_order_count(3);
			notify = _get_message_count();
			coupon_count = _get_coupon_count();
			//获取会员所有信息
			member = this.get_member_from_sign(this.sign);
			if (member != null && !member.isEmpty()) {
				//获取当前等级的下个等级
				int score = 0;
				DB.DataMap row = DB.share("grade").where("status=1 AND id>'"+member.get("grade_id")+"'").order("sort ASC, id ASC").field("score").find();
				if (row != null && row.get("grade_score") != null) score = (int) row.get("grade_score");
				if (score == 0) {
					score = DB.share("grade").where(member.get("grade_id")).value("score", Member.class, Integer.class);
				}
				member.put("next_score", score);
				this.setSession("member", member);
			}
		}
		int cart_total = _get_cart_count();
		Map<String, Object> data = new HashMap<>();
		data.put("member_id", this.member_id);
		data.put("cart_total", cart_total);
		data.put("coupon_count", coupon_count);
		data.put("not_pay", not_pay);
		data.put("not_shipping", not_shipping);
		data.put("not_confirm", not_confirm);
		data.put("not_comment", not_comment);
		data.put("notify", notify);
		data.put("member", member);
		return Common.success(data, "@/api/member");
	}

	//获取购物车商品总数
	public int _get_cart_count() {
		String where;
		if (this.member_id > 0) {
			where = " AND (member_id='"+this.member_id+"' OR session_id='"+this.session_id+"')";
		} else {
			where = " AND session_id='"+this.session_id+"'";
		}
		return DB.share("cart").where(where).sum("quantity");
	}

	//获取指定状态未读订单总数
	public int _get_status_order_count(int status) {
		return DB.share("order").where("status='"+status+"' AND member_id='"+this.member_id+"' AND readed=0").count();
	}

	//获取未读站内信息总数
	public int _get_message_count() {
		return DB.share("message").where("member_id='"+this.member_id+"' AND readed=0").count();
	}

	//获取优惠券总数
	public int _get_coupon_count() {
		return DB.share("coupon_sn").where("status='1' AND member_id='"+this.member_id+"' AND member_id>0").count();
	}

	//设置
	public Object set() {
		return Common.success("@/api/member.set");
	}
}
