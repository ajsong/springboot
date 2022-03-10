package com.laokema.springboot.api;

import com.laokema.tool.*;
import java.util.*;

public class Category extends Core {
	//分类首页
	public Object index(){
		Map<String, Object> map = new HashMap<>();
		map.put("category", get_categories());
		return Common.success(map);
	}

	private DataList get_categories() {
		return get_categories(0);
	}
	private DataList get_categories(int parent_id) {
		DataList category = DB.share("goods_category").where("status='1' AND parent_id=?", parent_id).order("sort ASC, id ASC").select("*, NULL as categories");
		if (category != null) {
			for (DataMap g : category) {
				g.put("flashes", _flashes(g.getInt("id")));
				if (((int) DB.share("goods_category").where("parent_id=?", g.getInt("id")).count()) > 0) g.put("categories", get_categories(g.getInt("id")));
			}
		}
		category = Common.add_domain_deep(category, "pic");
		return category;
	}

	//幻灯广告
	private DataList _flashes(int id) {
		DataList ads = DB.share("ad").where("(begin_time|begin_time<=)&(end_time|end_time>=)&status&position", 0, this.now, 0, this.now, 1, "category"+id)
				.order("sort ASC, id DESC").pagesize(5).cached(60*60*24*7).select();
		ads = Common.add_domain_deep(ads, "pic");
		return ads;
	}
}
