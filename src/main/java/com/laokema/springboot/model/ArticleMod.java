package com.laokema.springboot.model;

import com.laokema.tool.*;
import java.util.*;

public class ArticleMod extends BaseMod {
	//获取文章详情
	public DB.DataMap detail(int id) {
		return DB.share("article").where("status=1 AND id="+id).find();
	}

	//分类列表
	public DB.DataList categories() {
		return categories(0);
	}
	public DB.DataList categories(int parent_id) {
		DB.DataList rs = DB.share("article_category").where("status=1 AND parent_id="+parent_id).order("sort ASC, id ASC").field("*, NULL as categories").select();
		if (rs != null) {
			for (DB.DataMap g : rs) {
				if (g.getInt("parent_id") > 0) g.put("categories", this.categories(g.getInt("parent_id")));
			}
			rs = Common.add_domain_deep(rs, "pic");
		}
		return rs;
	}

	//关联图片
	public DB.DataList pics(int article_id, int limit) {
		return DB.share("article_pic").where("article_id="+article_id).order("id ASC").pagesize(limit).field("pic").select();
	}

	//关联商品
	public DB.DataList goods(int article_id) {
		return DB.share("article_goods ag").left("goods g", "goods_id=g.id").where("article_id="+article_id).order("ag.id ASC").select("g.id, g.name, g.model, g.pic, g.price");
	}

	//是否点赞
	public int liked(int member_id, int article_id) {
		if (member_id == 0) return 0;
		return DB.share("article_like").where("article_id='"+article_id+"' AND member_id='"+member_id+"'").count();
	}
}
