package com.laokema.springboot.api;

import com.alibaba.fastjson.JSONArray;
import com.laokema.springboot.model.*;
import com.laokema.tool.*;
import java.util.*;

public class Cart extends Core {
	private final GoodsMod goodsMod;
	private final MemberMod memberMod;
	private final OrderMod orderMod;
	private final CommissionMod commissionMod;
	private final CouponMod couponMod;
	private final AddressMod addressMod;

	public Cart() {
		this.goodsMod = Common.model(GoodsMod.class);
		this.memberMod = Common.model(MemberMod.class);
		this.orderMod = Common.model(OrderMod.class);
		this.commissionMod = Common.model(CommissionMod.class);
		this.couponMod = Common.model(CouponMod.class);
		this.addressMod = Common.model(AddressMod.class);
	}

	//购物车首页
	//支持分单，如不需要分单按照默认即可(shop_id=0)
	public Object index() {
		this._merge();
		String where = "";
		if (this.member_id>0) {
			where += " AND (c.member_id='"+this.member_id+"' OR session_id='"+this.session_id+"')";
		} else {
			where += " AND session_id='"+this.session_id+"'";
		}
		int count = DB.share("cart c").where(where).count("DISTINCT(shop_id)");
		String field;
		if (count>1) {
			field = "c.shop_id";
		} else {
			field = "DISTINCT(c.shop_id)";
		}
		//$sql .= " ORDER BY id ASC";
		//exit($sql);
		DB.DataList rs = DB.share("cart c").left("goods g", "c.goods_id=g.id").where(where+" AND g.status=1").select(field);
		if (rs != null) {
			int k = -1;
			for (DB.DataMap s : rs) {
				k++;
				DB.DataMap row = DB.share("shop").where(s.getInt("shop_id")).row("name, avatar");
				if (row != null) {
					s.put("shop_name", row.getString("name"));
					s.put("shop_avatar", row.getString("avatar"));
					rs.set(k, s);
				}
				DB.DataList goods = DB.share("cart c").left("goods g", "c.goods_id=g.id").where(where+" AND c.shop_id='"+s.getInt("shop_id")+"' AND g.status='1'")
						.select("c.id, c.goods_id, c.spec, c.price as cart_price, c.quantity, c.reseller_id, g.id as goods_id, g.name, g.pic, g.stocks, g.price, g.promote_price, g.stock_alert_number, '' as spec_name");
				if (goods != null) {
					for (int i = 0; i < goods.size(); i++) {
						DB.DataMap g = goods.get(i);
						goods.set(i, "cart_price", g.getFloat("cart_price"));
						if (Arrays.asList(this.function).contains("grade")) {
							if (this.member_id > 0) {
								g.put("grade_price", DB.share("goods_grade_price").where("goods_id='"+g.getInt("goods_id")+"' AND grade_id='"+this.memberObj.getInt("grade_id")+"'").value("price", Float.class));
							} else {
								g.put("grade_price", 0);
							}
							g.put("price", this.goodsMod.get_min_price(new Float[]{g.getFloat("price"), g.getFloat("grade_price")}));
						}
						goods.set(i, "price", this.goodsMod.get_min_price(new Float[]{g.getFloat("price"), g.getFloat("promote_price")}));
						goods.set(i, "stocks", g.getInt("stocks"));
						if (g.getString("spec").length() > 0) {
							DB.DataMap spec = DB.share("goods_spec").where("goods_id='"+g.getInt("goods_id")+"' AND spec='"+g.getString("spec")+"'").row();
							if (spec != null) {
								if (spec.getString("pic").length() > 0) goods.set(i, "pic", spec.getString("pic"));
								goods.set(i, "price", this.goodsMod.get_min_price(new Float[]{spec.getFloat("price"), spec.getFloat("promote_price")}));
								//获取最新库存
								goods.set(i, "stocks", spec.getInt("stocks"));
								//获取规格名
								String spec_name = "";
								List<String> p = new ArrayList<>();
								String[] specs = g.getString("spec").split(",");
								for (String sp : specs) {
									DB.DataMap r = DB.share("goods_spec_category").where(sp).row("name");
									if (r != null) p.add(r.getString("name"));
								}
								if (p.size() > 0) spec_name = Common.implode(";", p);
								goods.set(i, "spec_name", spec_name);
							}
						}
					}
				}
				s.put("goods", goods);
				rs.set(k, s);
			}
		}
		rs = Common.add_domain_deep(rs, "shop_avatar, pic");
		return success(rs);
	}

	//购物车数量
	public Object total() {
		return total(true);
	}
	public Object total(boolean is_show) {
		int quantity = 0;
		float total_price = 0;
		String where = "goods_id>0";
		if (this.member_id>0) {
			where += " AND (c.member_id='"+this.member_id+"' OR session_id='"+this.session_id+"')";
		} else {
			where += " AND session_id='"+this.session_id+"'";
		}
		DB.DataList rs = DB.share("cart c").left("goods g", "c.goods_id=g.id").where(where+" AND g.status='1'")
				.select("c.goods_id, c.spec, c.quantity, g.stocks, g.price, g.promote_price");
		if (rs != null) {
			for (DB.DataMap g : rs) {
				float price = this.goodsMod.get_min_price(new Float[]{g.getFloat("price"), g.getFloat("promote_price")});
				int stocks = g.getInt("stocks");
				if (g.getString("spec").length() > 0) {
					DB.DataMap spec = DB.share("goods_spec").where("goods_id='"+g.getInt("goods_id")+"' AND spec='"+g.getString("spec")+"'").find();
					if (spec != null) {
						price = this.goodsMod.get_min_price(new Float[]{spec.getFloat("price"), spec.getFloat("promote_price")});
						stocks = spec.getInt("stocks");
					}
				}
				if (stocks<=0) continue;
				quantity += g.getInt("quantity");
				total_price += price * g.getFloat("quantity");
			}
		}
		Map<String, Object> data = new HashMap<>();
		data.put("quantity", quantity);
		data.put("total_price", total_price);
		if (is_show) success(data);
		return data;
	}

	//合并购物车里同一会员、同一商品、同一规格的商品的数量
	private void _merge() {
		String table = "--CART--";
		String where = "";
		if (this.member_id>0) {
			where += "(member_id='"+this.member_id+"' OR session_id='"+this.session_id+"')";
		} else {
			where += "session_id='"+this.session_id+"'";
		}
		DB.DataList rs = DB.query("SELECT id, goods_id, spec FROM "+table+" WHERE "+where+" AND CONCAT(goods_id,spec) IN (SELECT CONCAT(goods_id,spec) FROM "+table+" WHERE "+where+" GROUP BY goods_id,spec HAVING COUNT(*)>1) AND id IN (SELECT MIN(id) FROM "+table+" WHERE "+where+" GROUP BY goods_id,spec HAVING COUNT(*)>1)");
		if (rs != null) {
			for (DB.DataMap g : rs) {
				int quantity = DB.share("cart").where(where+" AND goods_id='"+g.getInt("goods_id")+"' AND spec='"+g.getString("spec")+"'").sum("quantity");
				DB.share("cart").where(g.getInt("id")).update("quantity", quantity);
			}
		}
		rs = DB.query("SELECT id FROM "+table+" WHERE "+where+" AND CONCAT(goods_id,spec) IN (SELECT CONCAT(goods_id,spec) FROM "+table+" WHERE "+where+" GROUP BY goods_id,spec HAVING COUNT(*)>1) AND id NOT IN (SELECT MIN(id) FROM "+table+" WHERE "+where+" GROUP BY goods_id,spec HAVING COUNT(*)>1)");
		if (rs != null) {
			for (DB.DataMap g : rs) {
				DB.share("cart").delete(g.getInt("id"));
			}
		}
	}

	//加入到购物车
	public Object add() {
		this._merge();
		int cart_id = this.request.get("cart_id", 0); //存在cart_id即修改规格(先删除购物车再添加)
		String goodsJson = this.request.get("goods");
		//邀请我的渠道商的店铺id
		int reseller_id = this.request.session("reseller_id", 0);
		//操作是根据quantity值, 传1:当前操作是购物车修改商品总数量, 0:进行累加(数字)/累减(-数字)
		int edit = this.request.get("edit", 0);
		if (goodsJson.length() > 0) {
			Map<String, Object> goodsMap = Common.json_decode(goodsJson);
			if (goodsMap != null && goodsMap.keySet().size() > 0) {
				for (String k : goodsMap.keySet()) {
					DB.DataMap g = new DB.DataMap(goodsMap.get(k));
					DB.DataMap goods = DB.share("goods").where(g.getInt("goods_id")).row("shop_id, stocks, price, promote_price");
					if (goods == null) continue;
					if (Arrays.asList(this.function).contains("grade")) {
						if (this.member_id > 0) {
							goods.put("grade_price", DB.share("goods_grade_price").where("goods_id='"+g.getInt("goods_id")+"' AND grade_id='"+this.memberObj.getInt("grade_id")+"'").value("price", Float.class));
						} else {
							goods.put("grade_price", 0);
						}
						goods.put("price", this.goodsMod.get_min_price(new Float[]{goods.getFloat("price"), goods.getFloat("promote_price")}));
					}
					int shop_id = goods.getInt("shop_id");
					int stocks = goods.getInt("stocks");
					float price = this.goodsMod.get_min_price(new Float[]{goods.getFloat("price"), goods.getFloat("promote_price")});
					if (g.getString("spec").length() > 0) {
						//检测库存
						DB.DataMap spec = DB.share("goods_spec").where("goods_id='"+g.getInt("goods_id")+"' AND spec='"+g.getString("spec")+"'").row("stocks, price, promote_price");
						if (spec != null) {
							stocks = spec.getInt("stocks");
							price = this.goodsMod.get_min_price(new Float[]{spec.getFloat("price"), spec.getFloat("promote_price")});
						}
					}
					String where = "goods_id='"+g.getInt("goods_id")+"'";
					if (g.getString("spec").length() > 0) where += " AND spec='"+g.getString("spec")+"'";
					if (this.member_id > 0) {
						where += " AND (member_id='"+this.member_id+"' OR session_id='"+this.session_id+"')";
					} else {
						where += " AND session_id='"+this.session_id+"'";
					}
					DB.DataMap row = DB.share("cart").where(where).row("id, quantity");
					if (row != null) {
						if (edit == 0) {
							if (g.getInt("quantity")<0 && row.getInt("quantity")-g.getInt("quantity")<=0) {
								DB.share("cart").delete(row.getInt("id"));
							} else {
								if (row.getInt("quantity")+g.getInt("quantity") > stocks) return error("该商品规格的库存只剩下"+stocks+"件");
								DB.share("cart").where(row.getInt("id")).incr("quantity", g.getInt("quantity"));
							}
						} else {
							if (g.getInt("quantity")<=0) {
								DB.share("cart").delete(row.getInt("id"));
							} else {
								if (g.getInt("quantity") > stocks) return error("该商品规格的库存只剩下"+stocks+"件");
								DB.share("cart").where(row.getInt("id")).incr("quantity", g.getInt("quantity"));
							}
						}
					} else {
						if (g.getInt("quantity") > stocks) return error("该商品规格的库存只剩下"+stocks+"件");
						if (cart_id>0) {
							DB.share("cart").where(cart_id).update("member_id, spec, price, quantity", this.member_id, g.getString("spec"), price, g.getInt("quantity"));
						} else {
							DB.share("cart").insert("member_id, session_id, shop_id, ip, reseller_id, goods_id, spec, price, quantity, add_time", this.member_id, this.session_id, shop_id, this.ip, reseller_id, g.getInt("goods_id"), g.getString("spec"), price, g.getInt("quantity"), this.now);
						}
					}
				}
			}
		}
		return total();
	}

	//从购物车删除
	public Object delete() {
		int id = this.request.get("cart_id", 0);
		if (id<=0) return error("数据错误");
		String where = "id='"+id+"'";
		if (this.member_id>0) {
			where += " AND (member_id='"+this.member_id+"' OR session_id='"+this.session_id+"')";
		} else {
			where += " AND session_id='"+this.session_id+"'";
		}
		DB.share("cart").delete(where);
		return total();
	}

	//扫码支付
	public Object nativer() {
		String order_sn = this.request.get("order_sn");
		String url = this.request.get("url");
		Map<String, Object> data = new HashMap<>();
		data.put("order_sn", order_sn);
		data.put("url", url);
		return success(data);
	}

	//结算接口
	public Object commit() {
		if (this.member_id<=0) return error("请登录", -100);
		//订单类型
		int type = this.request.get("type", 0);
		//所属订单类型主id
		int parent_id = this.request.get("parent_id", 0);
		//使用积分抵扣
		int integral_cash = this.request.get("integral_cash", 0);
		//积分商城订单
		int integral_order = this.request.get("integral_order", 0);
		//是否显示线下支付
		String offline = this.configs.get("G_ORDER_PAYMOTHED_OFFLINE");

		DB.DataList shops = this._split_shops();
		if (shops == null) return error("商品不存在或已下架");
		int[] goods_ids = this._get_goods_ids(shops);
		shops = Common.add_domain_deep(shops, "pic");
		float total_price = this._get_total_price(shops);

		//获取默认地址
		DB.DataMap address = this.addressMod.default_address(this.member_id);

		//获取用户的佣金、余额、积分
		float money = 0; //佣金和余额之和
		int integral = 0; //积分
		DB.DataMap member = DB.share("member").where(this.member_id).row("money, commission, integral");
		if (member != null) {
			money = member.getFloat("money") + member.getFloat("commission");
			integral = member.getInt("integral");
		}
		DB.DataList coupons = this.couponMod.coupons(this.member_id, total_price, goods_ids);
		if (type>0) {
			coupons = null;
		}

		//积分抵扣
		DB.DataMap integral_pay = null;
		if (integral_cash > 0) {
			if (this.memberMod.order_integral_check(this.member_id, total_price)) {
				integral_pay = this.memberMod.check_pay_with_integral(this.member_id, total_price);
			}
		}

		//计算总运费, 积分商城订单免运费
		float shipping_fee = 0;
		if (shops != null && integral_order == 0) {
			for (DB.DataMap s : shops) {
				shipping_fee += s.getFloat("shipping_fee");
			}
		}

		//商品总金额
		float goods_total_price = 0;
		if (shops != null) {
			for (DB.DataMap s : shops) {
				for (DB.DataMap g : s.getDataList("goods")) {
					goods_total_price += g.getFloat("goods_price");
				}
			}
		}

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", type);
		data.put("money", money);
		data.put("offline", offline);
		data.put("total_price", total_price);
		data.put("address", address);
		data.put("shops", shops);
		data.put("shipping_fee", shipping_fee);
		data.put("coupons", coupons);
		data.put("goods_total_price", goods_total_price);
		data.put("integral_order", integral_order);
		data.put("integral", integral);
		data.put("integral_pay", integral_pay);
		return success(data);
	}

	//检测是否有实体商品
	private boolean _is_entity_goods(DB.DataList shops) {
		if (shops == null) return false;
		for (DB.DataMap s : shops) {
			for (DB.DataMap g : s.getDataList("goods")) {
				int type = DB.share("goods").where(g.getInt("id")).value("type", Integer.class);
				if (type==0 || type==4) return true;
			}
		}
		return false;
	}

	//根据提交的产品数据来分单，返回分单后的店铺
	private DB.DataList _split_shops() {
		String province = this.request.get("province");
		String city = this.request.get("city");
		String district = this.request.get("district");
		int type = this.request.get("type", 0); //空为自动判断,0为独立购买
		String _goods = this.request.get("goods");
		List<Integer> _shop_ids = new ArrayList<>();
		//积分商城订单
		int integral_order = this.request.get("integral_order", 0);
		if (_goods == null) {
			error("请选择商品");
			return null;
		}
		JSONArray _gs = Common.json_decode(_goods);
		if (_gs == null) {
			error("请选择商品");
			return null;
		}
		DB.DataList gs = new DB.DataList(_gs);
		DB.DataList goods = new DB.DataList();
		for (DB.DataMap g : gs) {
			DB.DataMap row = DB.share("goods g").where("g.id='"+g.getInt("goods_id")+"' AND g.status=1")
				.row("g.id, g.name, g.pic, g.shop_id, g.integral, g.stocks, g.price, g.promote_price, g.market_price," +
					"g.free_shipping, g.shipping_fee, g.shipping_fee_id, g.weight, g.free_shipping_count," +
					"g.groupbuy_price, g.groupbuy_begin_time, g.groupbuy_end_time, g.groupbuy_free_shipping," +
					"g.purchase_price, g.purchase_begin_time, g.purchase_end_time, g.purchase_free_shipping," +
					"g.chop_price, g.chop_begin_time, g.chop_end_time, g.chop_free_shipping," +
					"0 as quantity, 0 as goods_price, '' as spec, '' as spec_name");
			if (row != null) {
				String pic = row.getString("pic");
				int stocks = row.getInt("stocks");
				float price = row.getFloat("price");
				if (Arrays.asList(this.function).contains("grade")) {
					if (this.member_id > 0) {
						row.put("grade_price", DB.share("goods_grade_price").where("goods_id='"+g.getInt("goods_id")+"' AND grade_id='"+this.memberObj.getInt("grade_id")+"'").value("price", Float.class));
					} else {
						row.put("grade_price", 0);
					}
					price = this.goodsMod.get_min_price(new Float[]{row.getFloat("price"), row.getFloat("grade_price")});
				}
				float promote_price = row.getFloat("promote_price");
				float groupbuy_price = 0;
				float purchase_price = 0;
				float chop_price = 0;
				if (type>0) {
					groupbuy_price = this.goodsMod.get_groupbuy_price(row.getInt("id"));
					purchase_price = this.goodsMod.get_purchase_price(row.getInt("id"));
					chop_price = this.goodsMod.get_chop_price(row.getInt("id"));
				}
				if (g.has("spec")) {
					DB.DataMap spec = DB.share("goods_spec").where("goods_id='"+g.getInt("goods_id")+"' AND spec='"+g.getString("spec")+"'").row();
					if (spec != null) {
						if (spec.has("pic")) pic = spec.getString("pic");
						stocks = spec.getInt("stocks");
						price = spec.getFloat("price");
						if (spec.getFloat("promote_price")>0) promote_price = spec.getFloat("promote_price");
						if (type>0) {
							if (spec.getFloat("groupbuy_price")>0) groupbuy_price = this.goodsMod.get_groupbuy_price(row.getInt("id"), g.getString("spec"), this.member_id);
							if (spec.getFloat("purchase_price")>0) purchase_price = this.goodsMod.get_purchase_price(row.getInt("id"), g.getString("spec"), this.member_id);
							if (spec.getFloat("chop_price")>0) chop_price = this.goodsMod.get_chop_price(row.getInt("id"), g.getString("spec"), this.member_id);
						}
					}
				}
				if (stocks<=0) continue;
				if (g.has("spec")) {
					//获取规格名
					String spec_name = "";
					List<String> p = new ArrayList<>();
					String[] specs = g.getString("spec").split(",");
					for (String s : specs) {
						DB.DataList r = DB.share("goods_spec_category").where(Integer.parseInt(s)).select("name");
						if (r != null) {
							for (DB.DataMap rg : r) {
								p.add(rg.getString("name"));
							}
						}
					}
					if (p.size() > 0) spec_name = Common.implode(";", p);
					row.put("spec_name", spec_name);
				}
				row.put("groupbuy_price", groupbuy_price);
				row.put("purchase_price", purchase_price);
				row.put("chop_price", chop_price);
				row.put("spec", g.has("spec") ? g.getString("spec") : "");
				row.put("pic", pic);
				row.put("quantity", g.getInt("quantity"));
				row.put("price", this.goodsMod.get_min_price(new Float[]{price, promote_price, groupbuy_price, purchase_price, chop_price}));
				row.put("goods_price", integral_order > 0 ? row.getInt("integral") : row.getFloat("price") * row.getInt("quantity"));
				goods.add(row);
				_shop_ids.add(row.getInt("shop_id"));
			}
		}
		//print_r($goods);
		if (goods.size() == 0) {
			error("商品不存在或已下架");
			return null;
		}
		String shop_ids = Common.implode(",", _shop_ids);
		boolean single_shop = false; //兼容单店铺的项目,清空店铺表里的所有记录或者goods表的shop_id设为0即自动转为单店铺
		DB.DataList shops = DB.share("shop").where("id IN ("+shop_ids+")")
				.select("id as shop_id, name as shop_name, avatar as shop_avatar, 0 as shop_price, 0 as shipping_fee, NULL as goods");
		if (shops == null) {
			single_shop = true;
			DB.DataMap shop = new DB.DataMap();
			shop.put("shop_id", 0);
			shop.put("shop_price", 0);
			shop.put("shipping_fee", 0);
			shop.put("goods", new DB.DataList());
			shops = new DB.DataList(shop);
		}
		//计算每个店铺的商品价格
		for (DB.DataMap g : goods) {
			for (int j = 0; j < shops.size(); j++) {
				DB.DataMap shop = shops.get(j);
				//兼容单店铺的项目
				if (single_shop) {
					DB.DataList _g = shop.getDataListOrNew("goods");
					_g.add(g);
					shop.put("goods", _g);
					shop.put("shop_price", shop.getFloat("shop_price") + g.getFloat("goods_price"));
					shops.set(j, g);
				} else {
					//同一个店铺内的
					if (g.getInt("shop_id")==shop.getInt("shop_id")) {
						DB.DataList _g = shop.getDataListOrNew("goods");
						_g.add(g);
						shop.put("goods", _g);
						shop.put("shop_price", shop.getFloat("shop_price") + g.getFloat("goods_price"));
						shops.set(j, g);
						break;
					}
				}
			}
		}
		//计算每个店铺的运费
		if (province.length() == 0 || city.length() == 0 || district.length() == 0) {
			//获取默认地址
			DB.DataMap address = this.addressMod.default_address(0);
			province = address.getString("province");
			city = address.getString("city");
			district = address.getString("district");
		}
		int province_id = DB.share("province").where("name='"+province+"'").value("province_id", Integer.class);
		int city_id = DB.share("city").where("name='"+city+"' AND parent_id='"+province_id+"'").value("city_id", Integer.class);
		int district_id = DB.share("district").where("name='"+district+"' AND parent_id='"+city_id+"'").value("district_id", Integer.class);
		if (district_id > 0) {
			for (int k = 0; k < shops.size(); k++) {
				DB.DataMap shop = shops.get(k);
				float shipping_fee = integral_order == 0 ? 0 : this.caculate_shipping_fee(shop.getDataList("goods"), district_id);
				shop.put("shipping_fee", shipping_fee); //积分商城订单免运费
				if (shipping_fee>0) {
					shop.put("shop_price", shop.getFloat("shop_price") + shipping_fee);
				}
				shops.set(k, shop);
			}
		}
		shops = Common.add_domain_deep(shops, "pic, shop_avatar");
		return shops;
	}

	//修改收货地址
	public Object change_address() {
		int id = this.request.get("id", 0);
		String _goods = this.request.get("goods");
		//是否积分商城订单
		int integral_order = this.request.get("integral_order", 0);
		if (integral_order>0) return success('0');
		if (_goods.length() == 0) return error("缺失商品");
		DB.DataList origin_goods = new DB.DataList(Common.json_decode(_goods));
		if (origin_goods.isEmpty()) return error("缺失商品");
		DB.DataMap address = DB.share("address").where("member_id='"+this.member_id+"' AND id='"+id+"'").row();
		if (address == null) return error("缺失收货地址");
		int province = DB.share("province").where("name='"+address.getString("province")+"'").value("province_id", Integer.class);
		int city = DB.share("city").where("name='"+address.getString("city")+"' AND parent_id='"+province+"'").value("city_id", Integer.class);
		int district = DB.share("district").where("name='"+address.getString("district")+"' AND parent_id='"+city+"'").value("district_id", Integer.class);
		if (district == 0) return error("缺失区域数据");
		DB.DataList goods = new DB.DataList();
		for (DB.DataMap g : origin_goods) {
			DB.DataMap row = DB.share("goods").where(g.getInt("goods_id"))
					.row("free_shipping, shipping_fee, shipping_fee_id, weight, free_shipping_count, 0 as quantity," +
							"groupbuy_price, groupbuy_free_shipping," +
							"purchase_price, purchase_free_shipping," +
							"chop_price, chop_free_shipping");
			row.put("quantity", g.getInt("quantity"));
			row.put("groupbuy_price", this.goodsMod.get_groupbuy_price(g.getInt("goods_id")));
			row.put("purchase_price", this.goodsMod.get_purchase_price(g.getInt("goods_id")));
			row.put("chop_price", this.goodsMod.get_chop_price(g.getInt("goods_id")));
			goods.add(row);
		}
		float shipping_fee = this.caculate_shipping_fee(goods, district);
		return success(shipping_fee);
	}

	//计算运费，只计算商品里运费最高的
	public float caculate_shipping_fee(DB.DataList goods, int district) {
		float shipping_fee = 0;
		if (goods != null) {
			for (DB.DataMap g : goods) {
				if (g.getInt("free_shipping")==0) {
					if ((g.getFloat("groupbuy_price")>0 && g.getInt("groupbuy_free_shipping")==1) ||
							(g.getFloat("purchase_price")>0 && g.getInt("purchase_free_shipping")==1) ||
							(g.getFloat("chop_price")>0 && g.getInt("chop_free_shipping")==1) ||
							(g.getInt("free_shipping_count")>0 && g.getInt("free_shipping_count")<=g.getInt("quantity"))) continue;
					if (g.getInt("shipping_fee_id")>0) {
						DB.DataMap shipping = DB.share("shipping_fee").where(g.getInt("shipping_fee_id")).row();
						DB.DataList rs = DB.share("shipping_fee_area").where("shipping_fee_id='"+g.getInt("shipping_fee_id")+"' AND districts LIKE '%"+district+"%'").select();
						if (rs != null) {
							for (DB.DataMap area : rs) {
								float fee = area.getFloat("first_price");
								if (shipping.getInt("type")==0) { //按重量
									double weight = Math.ceil(g.getDouble("weight")) - area.getInt("first");
									if (weight>0 && area.getInt("second")>0) {
										weight = weight / area.getInt("second");
										weight = Math.ceil(weight);
										fee += weight * area.getFloat("second_price");
									}
								} else { //按件数
									int quantity = Integer.parseInt(String.valueOf(Math.ceil(g.getInt("quantity")))) - area.getInt("first");
									if (quantity>0 && area.getInt("second")>0) {
										quantity = quantity / area.getInt("second");
										quantity = Integer.parseInt(String.valueOf(quantity));
										fee += quantity * area.getFloat("second_price");
									}
								}
								if (fee > shipping_fee) {
									shipping_fee = fee;
								}
							}
						} else {
							//没有设置该地区的运费,使用默认运费
							if (shipping != null) {
								float fee = shipping.getFloat("first_price");
								if (shipping.getInt("type")==0) { //按重量
									double weight = Math.ceil(g.getDouble("weight")) - shipping.getInt("first");
									if (weight>0 && shipping.getInt("second")>0) {
										weight = weight / shipping.getInt("second");
										weight = Math.ceil(weight);
										fee += weight * shipping.getFloat("second_price");
									}
								} else { //按件数
									int quantity = Integer.parseInt(String.valueOf(Math.ceil(g.getInt("quantity")))) - shipping.getInt("first");
									if (quantity>0 && shipping.getInt("second")>0) {
										quantity = quantity / shipping.getInt("second");
										quantity = Integer.parseInt(String.valueOf(quantity));
										fee += quantity * shipping.getFloat("second_price");
									}
								}
								if (fee > shipping_fee) {
									shipping_fee = fee;
								}
							}
						}
					} else {
						if (g.getFloat("shipping_fee") > shipping_fee) {
							shipping_fee = g.getFloat("shipping_fee");
						}
					}
				}
			}
		}
		return shipping_fee;
	}

	//获取订单总价
	private float _get_total_price(DB.DataList shops) {
		float shop_price = 0;
		if (shops != null) {
			for (DB.DataMap shop : shops) {
				shop_price += shop.getFloat("shop_price");
			}
		}
		return shop_price;
	}

	//判断该会员是否该店铺的批发会员
	public int is_reseller(int shop_id) {
		return DB.share("shop_seller").where("shop_id='"+shop_id+"' AND member_id='"+this.member_id+"'").count();
	}

	//检测订单的商品是否还可购买
	//该函数用在我的订单列表里未支付的订单进行支付时，判断商品库存是否足够、商品是否下架等
	public Object check_order_goods() {
		int order_id = this.request.get("order_id", 0);
		if (this.member_id<=0) return error("请登录", -100);
		if (order_id<=0) return error("数据错误");
		DB.DataList goods = DB.share("order_goods og").inner("order o", "og.order_id=o.id").where("o.id='"+order_id+"' AND o.member_id='"+this.member_id+"'")
				.select("og.shop_id, og.goods_id, og.goods_name, g.stocks, g.status, g.id, og.quantity");
		if (goods == null) return error("订单商品已经失效");
		for (DB.DataMap g : goods) {
			String goods_name = g.getString("goods_name").substring(0, 6);
			if (g.getInt("id") <= 0) return error("商品（"+goods_name+"）已经失效");
			if (g.getInt("stocks") <= 0) return error("商品（"+goods_name+"）已经下架");
			if (g.getInt("status") == 0) return error("商品（"+goods_name+"）已经下架");
			if (g.getInt("quantity") > g.getInt("stocks")) return error("商品（"+goods_name+"）库存不足");
		}
		DB.DataMap row = DB.share("order").where(order_id).row("id, order_sn, total_price, pay_method");
		return success(row);
	}

	//根据shops数组来获取goods_id的数组
	private int[] _get_goods_ids(DB.DataList shops) {
		List<Integer> ids = new ArrayList<>();
		if (shops != null) {
			for (DB.DataMap shop : shops) {
				if (shop.has("goods")) {
					for (Object g : shop.getList("goods")) {
						DB.DataMap goods = new DB.DataMap(g);
						ids.add(goods.getInt("id"));
					}
				}
			}
			ids = Common.array_unique(ids);
		}
		int[] s = new int[ids.size()];
		for (int i = 0; i < ids.size(); i++) s[i] = ids.get(i);
		return s;
	}
}
