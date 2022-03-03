package com.laokema.springboot.model;

import java.util.*;

public class GoodsMod extends BaseMod {

	//获取最低价格
	public float get_min_price(Float[] prices) {
		if (prices == null || prices.length == 0) return 0;
		Arrays.sort(prices); //正序排序
		Arrays.sort(prices, (o1, o2) -> o1 > o2 ? -1 : 1);
		for (float price : prices) {
			if (price > 0) return price;
		}
		return prices[0];
	}
}
