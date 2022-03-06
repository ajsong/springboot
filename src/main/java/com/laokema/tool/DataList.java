//Developed by @mario 1.0.20220306
package com.laokema.tool;

import com.alibaba.fastjson.JSONArray;
import java.util.*;

public class DataList implements Iterable<DataMap> {
	public List<DataMap> list = new ArrayList<>();
	public DataList() {}
	public DataList(Object list) {
		if (list == null) return;
		if (list instanceof DataList) {
			this.list = ((DataList) list).list;
			return;
		}
		if (list instanceof JSONArray) {
			List<DataMap> items = new ArrayList<>();
			for (Object item : ((JSONArray) list)) {
				if (!(item instanceof Map)) return;
				items.add(new DataMap(item));
			}
			this.list = items;
			return;
		}
		if (list instanceof DataMap) {
			this.list.add((DataMap) list);
			return;
		}
		this.addAll(list);
	}
	public DataMap get(int index) {
		return this.list.get(index);
	}
	public void add(DataMap data) {
		this.list.add(data);
	}
	@SuppressWarnings("unchecked")
	public void addAll(Object list) {
		if (list instanceof DataList) {
			this.list.addAll((List<DataMap>) list);
			return;
		}
		if (!(list instanceof List)) throw new IllegalArgumentException("PARAMER MUSH BE List<DataMap>");
		if (((List<Object>) list).size() == 0 || ((List<Object>) list).get(0).getClass() != DataMap.class) return;
		this.list.addAll((List<DataMap>) list);
	}
	public void set(int index, DataMap data) {
		this.list.set(index, data);
	}
	public void set(int index, String key, Object value) {
		DataMap data = this.get(index);
		data.put(key, value);
		this.set(index, data);
	}
	public void remove(int index) {
		this.list.remove(index);
	}
	public boolean isEmpty() {
		return this.list.isEmpty();
	}
	public void clear() {
		this.list.clear();
	}
	public int size() {
		return this.list.size();
	}
	public Object[] toArray() {
		return this.list.toArray();
	}
	@Override
	public Iterator<DataMap> iterator() {
		return new Iterator<DataMap>() {
			private int cursor = 0;
			@Override
			public boolean hasNext() {
				return cursor < DataList.this.list.size();
			}
			@Override
			public DataMap next() {
				return DataList.this.list.get(cursor++);
			}
		};
	}
}
