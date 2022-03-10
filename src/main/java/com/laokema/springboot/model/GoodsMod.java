package com.laokema.springboot.model;

import com.laokema.tool.*;
import java.util.*;
import java.util.regex.*;

public class GoodsMod extends BaseMod {
	private final CouponMod couponMod;

	public GoodsMod() {
		this.couponMod = Common.model(CouponMod.class);
	}

	//获取产品详情
	public DataMap detail(int goods_id) {
		return detail(goods_id, false, "1");
	}
	public DataMap detail(int goods_id, boolean show_origin_pic, String status) {
		String where = "";
		if (status.length() > 0) where = " AND g.status='"+status+"'";
		DataMap row = DB.share("goods g").where("g.id='"+goods_id+"' "+where)
			.row("g.*, NULL as pics, NULL as specs, '' as spec, NULL as country, '' as sale_method_name, 0 as favorited," +
				"0 as groupbuy_show, 0 as groupbuy_now," +
				"0 as purchase_show, 0 as purchase_now," +
				"0 as chop_show, 0 as chop_now");
		if (row != null) {
			row = this.set_min_price(row);
			row.put("pics", this.get_pics(goods_id, show_origin_pic));
			row.put("specs", this.get_specs(goods_id, true));
			row.put("params", this.get_params(row.getString("params")));
			row.put("coupons", this.get_coupons(row.getInt("id"), row.getInt("shop_id")));
			if (row.has("specs")) {
				DataList _specs = row.getDataList("specs");
				String[] specs = new String[_specs.size()];
				for (int i = 0; i < _specs.size(); i++) specs[i] = _specs.get(i).getString("name");
				row.put("spec", Common.implode("、", specs));
			}
			row.put("favorited", this.favorited(goods_id, this.member_id));
			row = Common.add_domain_deep(row, "pic");
			row = this.set_activity(row);
		}
		return row;
	}

	//获取产品详情，不判断是否下架
	public DataMap get_detail(int goods_id, boolean show_origin_pic) {
		return this.detail(goods_id, show_origin_pic, "");
	}

	//设置最低价格
	public DataMap set_min_price(DataMap row) {
		if (row != null) {
			row = this.set_activity(row);
			if (row.getInt("market_price")==0) {
				if (row.getFloat("price")>row.getFloat("promote_price") || row.getFloat("groupbuy_price")>0 || row.getFloat("purchase_price")>0 || row.getFloat("chop_price")>0) {
					row.put("market_price", row.getFloat("price"));
				}
			}
			row.put("origin_price", this.get_min_price(new Float[]{row.getFloat("price"), row.getFloat("promote_price")}));
			row.put("price", this.get_min_price(new Float[]{row.getFloat("price"), row.getFloat("promote_price"), row.getFloat("groupbuy_price"), row.getFloat("purchase_price"), row.getFloat("chop_price")}));
		}
		return row;
	}
	//设置最低价格(列表)
	public DataList set_min_prices(DataList rs) {
		if (rs != null) {
			for (int i = 0; i < rs.size(); i++) {
				rs.set(i, this.set_min_price(rs.get(i)));
			}
		}
		return rs;
	}
	private int get_num_of_show(int[] shows) {
		int num = 0;
		for (int show : shows) {
			if (show==1) num++;
		}
		return num;
	}

	//设置活动
	public DataMap set_activity(DataMap row) {
		if (row != null) {
			long now = this.now;
			//拼团
			row.put("groupbuy_show", 0);
			row.put("groupbuy_now", 0);
			row.put("groupbuy_price", this.get_groupbuy_price(row.getInt("id")));
			if (row.getFloat("groupbuy_price")>0 && row.has("groupbuy_end_time")) {
				row.put("groupbuy_now", now);
				if (row.getInt("groupbuy_begin_time")==0 ||
						(row.getInt("groupbuy_begin_time")>0 && row.getInt("groupbuy_end_time")==0) ||
						(row.getInt("groupbuy_begin_time")>0 && row.getInt("groupbuy_now")<row.getInt("groupbuy_end_time"))) {
					row.put("groupbuy_show", 1);
				} else {
					row.put("groupbuy_price", 0);
				}
			}
			//秒杀
			row.put("purchase_show", 0);
			row.put("purchase_now", 0);
			row.put("purchase_price", this.get_purchase_price(row.getInt("id")));
			if (row.getFloat("purchase_price")>0 && row.has("purchase_end_time")) {
				row.put("purchase_now", now);
				if (row.getInt("purchase_begin_time")==0 ||
						(row.getInt("purchase_begin_time")>0 && row.getInt("purchase_end_time")==0) ||
						(row.getInt("purchase_begin_time")>0 && row.getInt("purchase_now")<row.getInt("purchase_end_time"))) {
					row.put("purchase_show", 1);
				} else {
					row.put("purchase_price", 0);
				}
			}
			//砍价
			row.put("chop_show", 0);
			row.put("chop_now", 0);
			row.put("chop_price", this.get_chop_price(row.getInt("id")));
			if (row.getFloat("chop_price")>0 && row.has("chop_end_time")) {
				row.put("chop_now", now);
				if (row.getInt("chop_begin_time")==0 ||
						(row.getInt("chop_begin_time")>0 && row.getInt("chop_end_time")==0) ||
						(row.getInt("chop_begin_time")>0 && row.getInt("chop_now")<row.getInt("chop_end_time"))) {
					row.put("chop_show", 1);
				} else {
					row.put("chop_price", 0);
				}
			}
			if (!Arrays.asList(this.function).contains("groupbuy")) {
				row.put("groupbuy_show", 0);
				row.put("groupbuy_price", 0);
			}
			if (!Arrays.asList(this.function).contains("purchase")) {
				row.put("purchase_show", 0);
				row.put("purchase_price", 0);
			}
			if (!Arrays.asList(this.function).contains("chop")) {
				row.put("chop_show", 0);
				row.put("chop_price", 0);
			}
			//价格最低即默认选用该活动
			int num = this.get_num_of_show(new int[]{row.getInt("groupbuy_show"), row.getInt("purchase_show"), row.getInt("chop_show")});
			if (num>1) {
				String key = this.get_min_price_key(new LinkedHashMap<String, Float>(){{
					put("groupbuy", row.getFloat("groupbuy_price"));
					put("purchase", row.getFloat("purchase_price"));
					put("chop", row.getFloat("chop_price"));
				}});
				switch (key) {
					case "groupbuy":
						row.put("purchase_show", 0);
						break;
					case "purchase":
						row.put("groupbuy_show", 0);
						break;
					case "chop":
						row.put("chop_show", 0);
						break;
				}
			}
			//如果特价比活动价都低就不进行活动
			if (row.has("promote_price") &&
					row.getFloat("promote_price")<row.getFloat("groupbuy_price") &&
							row.getFloat("promote_price")<row.getFloat("purchase_price") &&
									row.getFloat("promote_price")<row.getFloat("chop_price")) {
				row.put("purchase_show", 0);
				row.put("groupbuy_show", 0);
				row.put("chop_show", 0);
			}
		}
		return row;
	}

	//获取最低价格
	public float get_min_price(Float[] prices) {
		if (prices == null || prices.length == 0) return 0;
		Arrays.sort(prices); //正序排序
		//Arrays.sort(prices, (o1, o2) -> o1 > o2 ? -1 : 1); //倒序排序
		for (float price : prices) {
			if (price > 0) return price;
		}
		return prices[0];
	}

	//获取最低价格的key
	public String get_min_price_key(Map<String, Float> prices) {
		if (prices == null || prices.isEmpty()) return "";
		prices = Common.sortMapByValue(prices);
		for (String key : prices.keySet()) {
			if (prices.get(key) > 0) return key;
		}
		return new ArrayList<>(prices.keySet()).get(0);
	}

	//获取拼团价,spec为规格tree
	public float get_groupbuy_price(int goods_id) {
		return get_groupbuy_price(goods_id, "", 0);
	}
	public float get_groupbuy_price(int goods_id, String spec, int member_id) {
		long now = this.now;
		DataMap row = DB.share("goods").where(goods_id).row("groupbuy_price, groupbuy_begin_time, groupbuy_end_time, groupbuy_amount, groupbuy_count, groupbuy_limit");
		if (row == null) return 0;
		if (spec.length() > 0) {
			DataMap _spec = DB.share("goods_spec").where("goods_id='"+goods_id+"' AND spec='"+spec+"'").row("groupbuy_price");
			if (_spec != null) {
				if (_spec.getFloat("groupbuy_price")>0) row.put("groupbuy_price", _spec.getFloat("groupbuy_price"));
			}
		}
		if (row.getFloat("groupbuy_price")<=0 || row.getInt("groupbuy_amount")<=0) return 0;
		if (row.getLong("groupbuy_begin_time")>0 && row.getLong("groupbuy_begin_time")>now) return 0;
		if (row.getLong("groupbuy_end_time")>0 && row.getLong("groupbuy_end_time")<now) return 0;
		if (row.getInt("groupbuy_count")>=row.getInt("groupbuy_amount")) return 0;
		if (member_id<=0) member_id = this.member_id;
		if (row.getInt("groupbuy_limit")>0 && member_id>0) {
			int count = DB.share("order_goods og").left("order o", "og.order_id=o.id").where("goods_id='"+goods_id+"' AND og.member_id='"+member_id+"' AND o.type=1").count();
			if (count>=row.getInt("groupbuy_limit")) return 0;
		}
		return row.getFloat("groupbuy_price");
	}

	//获取秒杀价
	public float get_purchase_price(int goods_id) {
		return get_purchase_price(goods_id, "", 0);
	}
	public float get_purchase_price(int goods_id, String spec, int member_id) {
		long now = this.now;
		DataMap row = DB.share("goods").where(goods_id).row("purchase_price, purchase_begin_time, purchase_end_time, purchase_amount, purchase_count, purchase_limit");
		if (row == null) return 0;
		if (spec.length() > 0) {
			DataMap _spec = DB.share("goods_spec").where("goods_id='"+goods_id+"' AND spec='"+spec+"'").row("purchase_price");
			if (_spec != null) {
				if (_spec.getFloat("purchase_price")>0) row.put("purchase_price", _spec.getFloat("purchase_price"));
			}
		}
		if (row.getFloat("purchase_price")<=0 || row.getInt("purchase_amount")<=0) return 0;
		if (row.getLong("purchase_begin_time")>0 && row.getLong("purchase_begin_time")>now) return 0;
		if (row.getLong("purchase_end_time")>0 && row.getLong("purchase_end_time")<now) return 0;
		if (row.getInt("purchase_count")>=row.getInt("purchase_amount")) return 0;
		if (member_id<=0) member_id = this.member_id;
		if (row.getInt("purchase_limit")>0 && member_id>0) {
			int count = DB.share("order_goods og").left("order o", "og.order_id=o.id").where("goods_id='"+goods_id+"' AND og.member_id='"+member_id+"' AND o.type=2").count();
			if (count>=row.getInt("purchase_limit")) return 0;
		}
		return row.getFloat("purchase_price");
	}

	//获取砍价最低价
	public float get_chop_price(int goods_id) {
		return get_chop_price(goods_id, "", 0);
	}
	public float get_chop_price(int goods_id, String spec, int member_id) {
		long now = this.now;
		DataMap row = DB.share("goods").where(goods_id).row("chop_price, chop_begin_time, chop_end_time, chop_amount, chop_count");
		if (row == null) return 0;
		if (spec.length() > 0) {
			DataMap _spec = DB.share("goods_spec").where("goods_id='"+goods_id+"' AND spec='"+spec+"'").row("chop_price");
			if (_spec != null) {
				if (_spec.getFloat("chop_price")>0) row.put("chop_price", _spec.getFloat("chop_price"));
			}
		}
		if (row.getFloat("chop_price")<=0 || row.getInt("chop_amount")<=0) return 0;
		if (row.getLong("chop_begin_time")>0 && row.getLong("chop_begin_time")>now) return 0;
		if (row.getLong("chop_end_time")>0 && row.getLong("chop_end_time")<now) return 0;
		if (row.getInt("chop_count")>=row.getInt("chop_amount")) return 0;
		return row.getFloat("chop_price");
	}

	//获取商品的产地
	public DataMap get_country(int country_id) {
		if (country_id > 0) {
			return DB.share("country").where(country_id).row("id, name, flag_pic");
		}
		return null;
	}

	//获取商品的图片
	public DataList get_pics(int goods_id, boolean show_origin_pic) {
		DataList pics = DB.share("goods_pic").where("goods_id='"+goods_id+"'").sort("id ASC").select();
		pics = Common.add_domain_deep(pics, "pic");
		//为商品增加缩略图
		if (!show_origin_pic && pics != null) {
			int num = pics.size();
			String size = "small";
			if (num==1) {
				size = "big";
			} else if (num==2) {
				size = "medium";
			}
			for (DataMap p : pics) {
				if (p.getString("pic").contains("!")) continue;
				pics.set(1, "pic", Common.get_upyun_thumb_url(p.getString("pic"), size));
			}
		}
		return pics;
	}

	//规格
	//20160120 by ajsong 因为APP界面原因,需要检测是否只有一条规格记录且名称为默认规格,是的话就返回空, $allways_show不检测是否只有一条记录
	public DataList get_specs(int goods_id, boolean allways_show) {
		if (!allways_show) {
			String spec = DB.share("goods_spec").where("goods_id='"+goods_id+"'").value("spec");
			if (spec.length() == 0 || spec.equals("默认规格")) return null;
		}
		DataList rs = DB.share("goods_spec_linkage l").left("goods_spec_category c", "l.spec_id=c.id")
			.where("l.goods_id='"+goods_id+"' AND l.parent_id='0'").sort("l.id ASC").select("l.spec_id as id, c.name, NULL as sub");
		if (rs != null) {
			for (int i = 0; i < rs.size(); i++) {
				DataMap g = rs.get(i);
				rs.set(i, "sub", DB.share("goods_spec_linkage l").left("goods_spec_category c", "l.spec_id=c.id")
					.where("l.goods_id='"+goods_id+"' AND l.parent_id='"+g.getInt("id")+"'").sort("l.id ASC").select("l.spec_id as id, c.name"));
			}
		}
		return rs;
	}

	//参数
	public List<Map<String, String>> get_params(String params) {
		if (params.length() == 0) return null;
		List<Map<String, String>> arr = new ArrayList<>();
		String[] ps = params.split("\\^");
		for (String p : ps) {
			String[] s = p.split("`");
			arr.add(new HashMap<String, String>(){{
				put("name", s[0]);
				put("value", s[1]);
			}});
		}
		return arr;
	}

	//优惠券
	public DataList get_coupons(int goods_id, int shop_id) {
		long now = this.now;
		DataList rs = DB.share("coupon")
			.where("shop_id='"+shop_id+"' AND type=0 AND permit_goods=0 AND status=1 AND begin_time<='"+now+"' AND (end_time>='"+now+"' OR end_time=0 OR handy_time>0)")
			.sort("id ASC").select("*, '' as time_memo, '' as min_price_memo");
		DataList _private = DB.share("coupon c").inner("coupon_goods cg", "c.id=cg.coupon_id")
			.where("shop_id='"+shop_id+"' AND type=0 AND permit_goods=1 AND goods_id='"+goods_id+"' AND c.status=1 AND begin_time<='"+now+"' AND (end_time>='"+now+"' OR end_time=0 OR handy_time>0)").sort("c.id ASC").select("c.*, '' as time_memo, '' as min_price_memo");
		if (_private != null) rs.addAll(_private);
		if (rs != null) {
			for (int i = 0; i < rs.size(); i++) {
				DataMap g = rs.get(i);
				rs.set(i, this.couponMod.get_coupon_info(g));
			}
		}
		return rs;
	}

	//获取某个规格下的价格
	public float get_spec_price(int goods_id, String spec) {
		DataMap row = DB.share("goods_spec").where("goods_id='"+goods_id+"' AND spec='"+spec+"'").row("price, promote_price");
		return this.get_min_price(new Float[]{row.getFloat("price"), row.getFloat("promote_price")});
	}

	//该商品是否已经收藏
	public int favorited(int goods_id, int member_id) {
		int favorited = 0;
		if (member_id > 0) {
			favorited = DB.share("favorite").where("item_id='"+goods_id+"' AND member_id='"+member_id+"'").count();
		}
		return favorited;
	}

	//状态
	public String status_name(int status) {
		String name = String.valueOf(status);
		switch (status) {
			case 0:
				name = "下架";
				break;
			case 1:
				name = "正常";
				break;
		}
		return name;
	}

	//销售方式：1，保税直发，2：海外直采
	public String sale_method_name(int sale_method) {
		String name = "";
		switch (sale_method) {
			case 1:
				name = "保税直发";
				break;
			case 2:
				name = "海外直采";
				break;
		}
		return name;
	}

	//获取分类
	public DataList get_categories() {
		return get_categories(0);
	}
	public DataList get_categories(int parent_id) {
		DataList rs = DB.share("goods_category").where("status=1 AND parent_id='{$parent_id}'").sort("sort ASC, id ASC").select("*, NULL as categories");
		if (rs != null) {
			for (int i = 0; i < rs.size(); i++) {
				rs.set(i, "categories", this.get_categories(rs.get(i).getInt("id")));
			}
		}
		return rs;
	}

	//生成分类的option, separated,parents_and_me不用设置,函数递归用, attributes键值:key自定义属性名称,value在categories里的字段名
	public String set_categories_option(DataList categories) {
		return set_categories_option(categories, 0, new HashMap<>(), "", "");
	}
	public String set_categories_option(DataList categories, int selected_id, Map<String, String> attributes, String separated, String parents_and_me) {
		if (categories == null) return "";
		StringBuilder html = new StringBuilder();
		for (int k = 0; k < categories.size(); k++) {
			DataMap g = categories.get(k);
			html.append("<option value=\"").append(g.getInt("id")).append("\" tree=\"").append(parents_and_me).append(g.getInt("id")).append("\"");
			if (g.getInt("id") == selected_id) html.append(" selected");
			for (String name : attributes.keySet()) {
				Matcher matcher = Pattern.compile("\\((\\w+)\\)").matcher(attributes.get(name));
				StringBuffer res = new StringBuffer();
				while (matcher.find()) {
					String str = g.getString(matcher.group(1));
					matcher.appendReplacement(res, (str != null ? str : "").replace("\"", "\\\""));
				}
				matcher.appendTail(res);
				html.append(" ").append(name).append("=\"").append(res).append("\"");
			}
			html.append(">").append(separated).append(k == categories.size() - 1 ? '└' : '├').append(g.getString("name")).append("</option>");
			DataList _categories = g.getDataList("categories");
			if (g.has("categories")) {
				html.append(this.set_categories_option(g.getDataList("categories"), selected_id, attributes, "　" + separated, parents_and_me + g.getInt("id") + ","));
			}
		}
		return html.toString();
	}

	//获取分类与所有上级的id
	public String get_category_parents_tree(int category_id) {
		String ids = category_id + "";
		DataMap row = DB.share("goods_category").where("status=1 AND id='"+category_id+"'").row("parent_id");
		if (row != null && row.getInt("parent_id")>0) ids = this.get_category_parents_tree(row.getInt("parent_id")) + ',' + ids;
		return ids;
	}

	//获取分类与所有下级的id
	public String get_category_children_tree(int category_id) {
		StringBuilder ids = new StringBuilder(category_id + "");
		DataList rs = DB.share("goods_category").where("status=1 AND parent_id='"+category_id+"'").select("id");
		if (rs != null) {
			for (DataMap g : rs) {
				ids.append(",").append(g.getInt("id"));
				int count = DB.share("goods_category").where("status=1 AND parent_id='"+g.getInt("id")+"'").count();
				if (count > 0) ids.append(",").append(this.get_category_children_tree(g.getInt("id")));
			}
		}
		return Common.trim(ids.toString(), ",");
	}
}
