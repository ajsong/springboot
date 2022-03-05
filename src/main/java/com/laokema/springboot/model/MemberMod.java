package com.laokema.springboot.model;

import com.laokema.tool.*;

public class MemberMod extends BaseMod {
	public MemberMod() {
		this.init();
	}

	//积分抵扣
	//order_min_price, 订单满多少元才可用
	//order_min_integral, 会员现时最少多少积分才可用
	//order_integral_money, 多少积分抵扣1元
	//order_integral_total_percent, 积分只可抵扣总价格的百分率，小数形式
	public boolean order_integral_check(int member_id, float price){
		if (member_id<=0 ||
				this.configs.get("order_min_price") == null ||
				this.configs.get("order_min_integral") == null ||
				this.configs.get("order_integral_money") == null ||
				this.configs.get("order_integral_total_percent") == null) return false;
		//订单总价是否满使用积分的价格
		if (price < Float.parseFloat(this.configs.get("order_min_price"))) return false;
		//会员积分是否达标
		int integral = DB.share("member").where(member_id).value("integral", Integer.class);
		return integral >= Integer.parseInt(this.configs.get("order_min_integral"));
	}

	//检测使用积分抵扣
	//成功，返回对象，否则null
	public DB.DataMap check_pay_with_integral(int member_id, float price) {
		if (member_id<=0 ||
				this.configs.get("order_min_price") == null ||
				this.configs.get("order_min_integral") == null ||
				this.configs.get("order_integral_money") == null ||
				this.configs.get("order_integral_total_percent") == null) return null;
		int integral = DB.share("member").where(member_id).value("integral", Integer.class);
		DB.DataMap integral_pay = new DB.DataMap();
		//积分最多可抵现
		float integral_money = price * Float.parseFloat(this.configs.get("order_integral_total_percent"));
		//用户积分不够扣除即获取积分最多可抵金额
		if (integral < Math.ceil(integral_money * Float.parseFloat(this.configs.get("order_integral_money")))) {
			integral_money = integral / Float.parseFloat(this.configs.get("order_integral_money"));
		}
		integral_pay.put("integral", Math.ceil(integral_money * Float.parseFloat(this.configs.get("order_integral_money"))));
		integral_pay.put("money", integral_money);
		return integral_pay;
	}
}
