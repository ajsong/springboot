package com.laokema.springboot.api;

import com.laokema.springboot.model.ArticleMod;
import com.laokema.tool.*;
import java.util.*;

public class Article extends Core {
	private final ArticleMod articleMod;
	
	public Article() {
		this.articleMod = Common.model(ArticleMod.class);
	}

	//发现首页
	public Object index() {
		String where = "";
		int category_id = this.request.get("type_id", 0);
		//关键词搜索
		String keyword = this.request.get("keyword");
		int offset = this.request.get("offset", 0);
		int pagesize = this.request.get("pagesize", 8);
		if (keyword.length() > 0) {
			where += " AND (title like '%"+keyword+"%' OR content like '%"+keyword+"%')";
		}
		if (category_id > 0) {
			where += " AND category_id='"+category_id+"'";
		}
		DB.DataList rs = DB.share("article").where("status=1 AND (mark='' OR mark IS NULL) "+where).order("sort ASC, id DESC").limit(offset, pagesize).select();
		if (rs != null) {
			for (DB.DataMap g : rs) {
				g.put("pics", this.articleMod.pics(g.getInt("id"), 3));
				g.put("goods", this.articleMod.goods(g.getInt("id")));
				g.put("content", g.getString("content").replaceAll("[\n\r]+", "").replaceAll("</?[^>]+>", ""));
				g.put("add_time", Common.get_time_word(g.getLong("add_time")));
			}
		}

		DB.DataList flashes = this._flash();

		Map<String, Object> map = new HashMap<>();
		map.put("flashes", flashes);
		map.put("articles", rs);

		return Common.success(map);
	}

	//轮播图
	private DB.DataList _flash() {
		DB.DataList flashes = DB.share("ad").where("(begin_time|begin_time<=)&(end_time|end_time>=)&status&position", 0, this.now, 0, this.now, 1, "faxian")
				.order("sort ASC, id DESC").pagesize(5).select();
		flashes = Common.add_domain_deep(flashes, "pic");
		return flashes;
	}
}
