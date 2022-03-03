package com.laokema.springboot.api;

import com.laokema.springboot.model.GoodsMod;
import com.laokema.tool.*;
import java.util.*;

public class Cart extends Core {
	private final GoodsMod goodsMod;

	public Cart() {
		this.goodsMod = Common.model(GoodsMod.class);
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
}
