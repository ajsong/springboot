package com.laokema.springboot.model;

import com.laokema.tool.*;
import java.util.*;

public class CouponMod extends BaseMod {
	private final GoodsMod goodsMod;

	public CouponMod() {
		this.init();
		this.goodsMod = Common.model(GoodsMod.class);
	}

	//发放优惠券，1发放成功，0发放失败，-1已领取，-2优惠券不存在
	public int send(int coupon_id, int member_id) {
		long now = this.now;
		DataMap coupon = DB.share("coupon").where("id='"+coupon_id+"' AND status=1 AND begin_time<='"+now+"' AND (end_time>='"+now+"' OR end_time=0 OR handy_time>0)").row();
		if (coupon != null) {
			if (coupon.getInt("num_per_person")==0 || coupon.getInt("num_per_person")>this.got(coupon_id, member_id)) { //每人限领
				int success;
				if (coupon.getInt("auto_add")==1) { //自动增加
					int status = coupon.getInt("status");
					if (status==0) status = 1;
					String _now = String.valueOf(now);
					String sn = Common.date("m") + Common.date("d") + _now.substring(_now.length()-3) + String.valueOf(new Date()).substring(2, 6) + Common.rand(100, 999);
					success = DB.share("coupon_sn").insert("member_id, coupon_id, coupon_money, sn, add_time, get_time, times, status", member_id, coupon.getInt("id"), coupon.getFloat("coupon_money"), sn, now, now, coupon.getInt("times"), status);
				} else { //由已发行的没领取的优惠券内更新
					success = DB.share("coupon_sn").where("status=1 AND get_time=0 AND member_id=0 AND coupon_id="+coupon_id).order("id ASC").pagesize(1)
							.update("member_id, get_time", member_id, now);
				}
				if (success > 0) return 1;
				else return 0;
			}
			return -1;
		}
		return -2;
	}

	//是否已经获得了此优惠券
	public int got(int coupon_id, int member_id) {
		return DB.share("coupon_sn").where("coupon_id='"+coupon_id+"' AND member_id='"+member_id+"' AND member_id>0").count();
	}

	//获取优惠券内容
	public DataMap get_coupon_info(DataMap coupon) {
		if (coupon != null) {
			coupon.put("coupon_money", coupon.getFloat("coupon_money"));
			if (coupon.getFloat("min_price") > 0) {
				coupon.put("min_price_memo", "满"+coupon.getFloat("min_price")+"元可使用");
			} else {
				coupon.put("min_price_memo", "无门槛");
			}
			if (coupon.has("permit_goods")) {
				coupon.put("memo", "(部分商品可用)");
			} else {
				coupon.put("memo", "(全场通用)");
			}
			String begin_time = coupon.getString("begin_time");
			String end_time = coupon.getString("end_time");
			if (Long.parseLong(end_time)>0) {
				coupon.put("end_time", Common.date("Y-m-d", end_time));
			} else {
				coupon.put("end_time", "长期有效");
			}
			if (Long.parseLong(begin_time)>0) {
				coupon.put("begin_time", Common.date("Y-m-d", begin_time));
				coupon.put("time_memo", begin_time + " ~ " + end_time);
			} else {
				coupon.put("time_memo", "有效期至 " + end_time);
			}
			coupon = Common.add_domain_deep(coupon, "pic");
		}
		return coupon;
	}

	//获取优惠券对应的优惠金额
	public float get_money(String sn, DataList coupons, DataList shops) {
		if (coupons != null) {
			for (DataMap coupon : coupons) {
				if (coupon.getString("coupon").equals(sn)) {
					float money = 0;
					if (coupon.getFloat("coupon_money")<0 && coupon.getFloat("coupon_discount")<0) { //抵全额
						if (coupon.has("goods") && shops != null) {
							for (int goods_id : coupon.getArray("goods", Integer.class)) { //查找出对应上的商品的goods_price
								for (DataMap s : shops) {
									if (s.has("goods")) {
										for (DataMap g : s.getDataList("goods")) {
											float goods_price = g.getFloat("goods_price");
											if (g.getInt("id")==goods_id) return goods_price;
										}
									}
								}
							}
						}
					} else if (coupon.getFloat("coupon_money")>0) { //定额
						money = coupon.getFloat("coupon_money");
					} else if (coupon.getFloat("coupon_discount")>0) { //折扣
						if (coupon.has("goods") && shops != null) {
							for (int goods_id : coupon.getArray("goods", Integer.class)) {
								for (DataMap s : shops) {
									if (s.has("goods")) {
										for (DataMap g : s.getDataList("goods")) {
											float goods_price = g.getFloat("goods_price");
											if (g.getInt("id")==goods_id) money += goods_price - (coupon.getFloat("coupon_discount")/10*goods_price);
										}
									}
								}
							}
						}
					}
					money = Float.parseFloat(Common.round(money, 2));
					return money;
				}
			}
		}
		return 0;
	}

	//获取优惠券对应的商品id
	public int get_goods_id(String sn, DataList coupons, DataList shops) {
		if (coupons != null) {
			for (DataMap coupon : coupons) {
				if (coupon.getString("coupon").equals(sn)) {
					if (coupon.has("goods") && shops != null) {
						for (int goods_id : coupon.getArray("goods", Integer.class)) {
							for (DataMap s : shops) {
								if (s.has("goods")) {
									for (DataMap g : s.getDataList("goods")) {
										if (g.getInt("id")==goods_id) return goods_id;
									}
								}
							}
						}
					}
					return 0;
				}
			}
		}
		return 0;
	}

	//使用优惠券
	public void using(String sn, int member_id) {
		if (sn.length() == 0) return;
		DataMap row = DB.share("coupon_sn").where("sn='"+sn+"' AND member_id='"+member_id+"' AND member_id>0").row();
		int times = row.getInt("times");
		int status = row.getInt("status");
		if (times>0) {
			times--;
			if (times==0) status = -1;
		}
		long now = this.now;
		DB.share("coupon_sn").where("sn='"+sn+"' AND member_id='"+member_id+"' AND member_id>0").update("times, use_time, status", times, now, status);
		DB.share("coupon_history").insert("coupon_sn_id, add_time", row.getInt("id"), now);
	}

	//释放使用优惠券
	public void free(String sn, int member_id) {
		int id = DB.share("coupon_sn").where("sn='"+sn+"' AND member_id='"+member_id+"' AND member_id>0").value("id", Integer.class);
		if (id > 0) {
			DB.share("coupon_sn").where("sn='"+sn+"' AND member_id='"+member_id+"' AND member_id>0").update("status", 0);
			DB.share("coupon_history").delete("coupon_sn_id='"+id+"'");
		}
	}

	//获得我的优惠券(在下单时)
	public DataList coupons(int member_id, float price, int[] goods) {
		DataList valid_coupons = new DataList();
		DataList all_coupons = DB.share("coupon_sn cs").inner("coupon c", "cs.coupon_id=c.id").sort("cs.id DESC")
			.where("cs.member_id='"+member_id+"' AND cs.member_id>'0' AND cs.times!=0 AND cs.status=1 AND c.status=1 AND c.begin_time<='"+this.now+"' AND (c.end_time>='"+this.now+"' OR c.end_time=0 OR c.handy_time>0) AND c.offline_use=0")
			.select("cs.id, cs.coupon_money, cs.sn, cs.coupon_id, cs.times, cs.get_time, c.name, c.begin_time, c.end_time, c.min_price, c.permit_goods, c.handy_time, c.day_times, NULL as goods");
		if (all_coupons != null) {
			for (DataMap coupon : all_coupons) {
				//满足一天内使用次数
				if (this.get_coupon_history(coupon.getInt("id"), coupon.getInt("day_times"))) {
					//满足价格范围
					if (this.in_coupon_price(price, coupon.getFloat("min_price"))) {
						//满足动态时间
						if (coupon.getInt("handy_time")>0) {
							int handy_time = coupon.getInt("get_time") + coupon.getInt("handy_time") * 24*60*60;
							if (handy_time >= this.now) {
								//优惠券限制了特定商品
								if (coupon.getInt("permit_goods")==1) {
									int[] coupon_goods = this.get_coupon_goods(coupon.getInt("coupon_id"));
									if (this.is_coupon_goods(goods, coupon_goods)) {
										coupon.put("goods", this.get_goods(coupon_goods));
										valid_coupons.add(coupon);
									}
								} else {
									valid_coupons.add(coupon);
								}
							}
						} else {
							if (coupon.getInt("permit_goods")==1) {
								int[] coupon_goods = this.get_coupon_goods(coupon.getInt("coupon_id"));
								if (this.is_coupon_goods(goods, coupon_goods)) {
									coupon.put("goods", this.get_goods(coupon_goods));
									valid_coupons.add(coupon);
								}
							} else {
								valid_coupons.add(coupon);
							}
						}
					}
				}
			}
		}
		return valid_coupons;
	}

	//获取优惠券使用历史
	public boolean get_coupon_history(int coupon_sn_id, int day_times) {
		if (day_times<=0) return true;
		long now1 = Common.time(Common.date("Y-m-d 00:00:00"));
		long now2 = Common.time(Common.date("Y-m-d 23:59:59"));
		int count = DB.share("coupon_history").where("coupon_sn_id='"+coupon_sn_id+"' AND add_time>='"+now1+"' AND add_time<='"+now2+"'").count();
		return day_times > count;
	}

	//优惠券的最低使用价格
	public boolean in_coupon_price(float buy_price, float coupon_price) {
		if (coupon_price==0) {
			return true;
		} else {
			return buy_price >= coupon_price;
		}
	}

	//获取优惠券的商品/类型
	public int[] get_coupon_goods(int coupon_id) {
		List<Integer> ids = new ArrayList<>();
		DataList goods = DB.share("coupon_goods").where("coupon_id='"+coupon_id+"'").select("goods_id");
		if (goods != null) {
			for (DataMap g : goods) {
				ids.add(g.getInt("goods_id"));
			}
			ids = Common.array_unique(ids);
		}
		int[] s = new int[ids.size()];
		for (int i = 0; i < ids.size(); i++) s[i] = ids.get(i);
		return s;
	}

	//购买的商品是否优惠券范围内的商品/类型
	public boolean is_coupon_goods(int[] buy_goods, int[] coupon_goods) {
		if (buy_goods != null && buy_goods.length > 0) {
			//优惠券的限制商品改为限制分类
			if (Integer.parseInt(this.configs.get("G_COUPON_PERMIT_CATEGORY"))==1) {
				StringBuilder category_ids = new StringBuilder();
				for (int goods_id : buy_goods) {
					int category_id = DB.share("goods").where(goods_id).value("category_id", Integer.class);
					category_ids.append(',').append(this.goodsMod.get_category_parents_tree(category_id));
				}
				category_ids = new StringBuilder(Common.trim(category_ids.toString(), ","));
				String[] _buy_goods = category_ids.toString().split(",");
				buy_goods = Arrays.stream(_buy_goods).mapToInt(Integer::parseInt).toArray();
			}
			for (int goods_id : buy_goods) {
				for (int i : coupon_goods) {
					if ((goods_id + "").equals(i + "")) return true;
				}
			}
		}
		return false;
	}

	//根据商品/类型获取优惠券的商品id
	public int[] get_goods(int[] coupon_goods) {
		if (Integer.parseInt(this.configs.get("G_COUPON_PERMIT_CATEGORY"))==1) {
			String _categories = "";
			for (int category_id : coupon_goods) {
				_categories = ',' + this.goodsMod.get_category_children_tree(category_id);
			}
			_categories = Common.trim(_categories, ",");
			String[] categories = _categories.split(",");
			//去重
			categories = Common.array_unique(categories);
			List<Integer> _goods = new ArrayList<>();
			for (String category_id : categories) {
				DataList rs = DB.share("goods").where("category_id='"+category_id+"'").select("id");
				if (rs != null) {
					for (DataMap g : rs) {
						_goods.add(g.getInt("id"));
					}
				}
			}
			//去重
			_goods = Common.array_unique(_goods);
			Integer[] goods = Common.listToArray(_goods);
			return Arrays.stream(goods).mapToInt(Integer::valueOf).toArray();
		} else {
			return Common.array_unique(coupon_goods);
		}
	}
}
