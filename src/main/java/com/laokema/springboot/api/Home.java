package com.laokema.springboot.api;

import com.alibaba.fastjson.JSON;
import com.laokema.tool.*;
import java.util.*;

public class Home extends Core {
	//首页
	public Object index() {
		DataList flashes = _flashes();
		DataList categories = _categories();
		DataList coupons = _coupons();
		DataList recommend = _goods(1); //推荐
		DataList hotsale = _goods(2); //热销
		DataList boutique = _goods(3); //精品
		DataList newgoods = _goods(4); //新品
		DataList discount = _goods(5); //折扣

		Map<String, Object> data = new HashMap<>();
		data.put("flashes", flashes);
		data.put("categories", categories);
		data.put("coupons", coupons);
		data.put("recommend", recommend);
		data.put("hotsale", hotsale);
		data.put("boutique", boutique);
		data.put("newgoods", newgoods);
		data.put("discount", discount);

		return Common.success(data);
	}

	public Object code(Integer id) {
		return "id: " + id + " clientId: " + client.getInt("id") + " path: "+this.request.path(-1);
	}

	//幻灯广告
	private DataList _flashes() {
		DataList rs = DB.share("ad").where("(begin_time|begin_time<=)&(end_time|end_time>=)&status&position", 0, this.now, 0, this.now, 1, "flash")
				.order("sort ASC, id DESC").pagesize(5).select();
		rs = Common.add_domain_deep(rs, "pic");
		return rs;
	}

	//商品分类
	private DataList _categories() {
		DataList rs = DB.share("goods_category").where("status='1' AND parent_id=0").field("id, name, pic").order("sort ASC, id ASC").cached(60*2).select();
		rs = Common.add_domain_deep(rs, "pic");
		return rs;
	}

	//优惠券
	private DataList _coupons() {
		DataList rs = DB.share("coupon").where("status='1'").order("id DESC").pagesize(10).select();
		/*if ($rs) {
			$coupon_mod = m('coupon');
			foreach ($rs as $k=>$g) {
				$rs[$k] = $coupon_mod->get_coupon_info($g);
			}
		}*/
		return rs;
	}

	//商品
	public DataList _goods(int ext_property) {
		return _goods(ext_property, "");
	}
	public DataList _goods(int ext_property, String not_in) {
		int offset = 0;
		int pagesize = 6;
		if (ext_property == 1) {
			offset = this.request.get("offset", 0);
			pagesize = this.request.get("pagesize", 12);
		}
		String where = "";
		if (not_in.length() > 0) where = " AND g.id NOT IN (" + not_in + ")";
		DataList rs = DB.share("goods g").where("g.status=1 AND LOCATE(',"+ext_property+",', CONCAT(',',ext_property,','))>0"+where)
				.order("g.sort ASC, g.id DESC").field("g.*, 0.0 as grade_price").limit(offset, pagesize).cached(60*2).select();
		/*if (rs != null) {
			foreach ($rs as $k=>$g) {
				unset($rs[$k]->content);
				if (in_array('grade', $this->function)) {
					if ($this->member_id) {
						$rs[$k]->grade_price = floatval(SQL::share('goods_grade_price')->where("goods_id='{$g->id}' AND grade_id='{$this->member_grade_id}'")->value('price'));
					} else {
						$grade_id = intval(SQL::share('goods_grade_price')->where("goods_id='{$g->id}'")->value('MIN(grade_id)'));
						$rs[$k]->grade_price = floatval(SQL::share('goods_grade_price')->where("goods_id='{$g->id}' AND grade_id='{$grade_id}'")->value('price'));
					}
				}
			}
		}*/
		//$rs = $this->goods_mod->set_min_prices($rs);
		rs = Common.add_domain_deep(rs, "pic");
		return rs;
	}
}
