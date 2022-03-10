//Developed by @mario 1.1.20220310
package com.laokema.tool;

import com.alibaba.fastjson.JSONObject;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;

public class DataMap {
	public Map<String, Object> data = new HashMap<>();
	public DataMap() {}
	public DataMap(Object data) {
		if (data == null) return;
		if (data instanceof DataMap) {
			this.data = ((DataMap) data).data;
			return;
		}
		if (data instanceof JSONObject) {
			this.data = ((JSONObject) data).getInnerMap();
			return;
		}
		this.putAll(data);
	}
	public Object get(String key) {
		return this.data.get(key);
	}
	public Object getOne() {
		String key = new ArrayList<>(this.data.keySet()).get(0);
		return this.data.get(key);
	}
	public String getString(String key) {
		Object ret = this.data.get(key);
		return ret == null ? "" : String.valueOf(ret);
	}
	public int getInt(String key) {
		Object ret = this.data.get(key);
		return ret == null ? Integer.parseInt("0") : Integer.parseInt(String.valueOf(ret));
	}
	public Long getLong(String key) {
		Object ret = this.data.get(key);
		return ret == null ? Long.parseLong("0") : Long.parseLong(String.valueOf(ret));
	}
	public Float getFloat(String key) {
		Object ret = this.data.get(key);
		return ret == null ? Float.parseFloat("0") : Float.parseFloat(String.valueOf(ret));
	}
	public Double getDouble(String key) {
		Object ret = this.data.get(key);
		return ret == null ? Double.parseDouble("0") : Double.parseDouble(String.valueOf(ret));
	}
	public BigDecimal getBigDecimal(String key) {
		Object ret = this.data.get(key);
		return ret == null ? new BigDecimal("0") : new BigDecimal(String.valueOf(ret));
	}
	@SuppressWarnings("unchecked")
	public <T> T[] getArray(String key, Class<T> type) {
		Object ret = this.data.get(key);
		if (ret == null) return null;
		if (!ret.getClass().isArray()) return null;
		int len = Array.getLength(ret);
		Object[] obj = new Object[len];
		for (int i = 0; i < len; i++) {
			if (type == Integer.class) obj[i] = Integer.parseInt(String.valueOf(Array.get(ret, i)));
			else if (type == Long.class) obj[i] = Long.parseLong(String.valueOf(Array.get(ret, i)));
			else if (type == Float.class) obj[i] = Float.parseFloat(String.valueOf(Array.get(ret, i)));
			else if (type == Double.class) obj[i] = Double.parseDouble(String.valueOf(Array.get(ret, i)));
			else if (type == BigDecimal.class) obj[i] = new BigDecimal(String.valueOf(Array.get(ret, i)));
			else obj[i] = String.valueOf(Array.get(ret, i));
		}
		return (T[]) obj;
	}
	@SuppressWarnings("unchecked")
	public List<Object> getList(String key) {
		Object ret = this.data.get(key);
		if (ret == null) return null;
		if (ret instanceof List) return new ArrayList<>((List<Object>) ret);
		if (ret instanceof DataList) {
			List<DataMap> list = ((DataList) ret).list;
			if (list.isEmpty()) return null;
			return (List<Object>) dataToMap(list);
		}
		return null;
	}
	public List<Object> getListOrNew(String key) {
		List<Object> list = this.getList(key);
		return list == null ? new ArrayList<>() : list;
	}
	@SuppressWarnings("unchecked")
	public Map<String, Object> getMap(String key) {
		Object ret = this.data.get(key);
		if (ret == null) return null;
		if (ret instanceof Map) return new LinkedHashMap<>((Map<String, Object>) ret);
		if (ret instanceof DataMap) {
			Map<String, Object> data = ((DataMap) ret).data;
			if (data.isEmpty()) return null;
			return data;
		}
		return null;
	}
	public Map<String, Object> getMapOrNew(String key) {
		Map<String, Object> map = this.getMap(key);
		return map == null ? new HashMap<>() : map;
	}
	public DataList getDataList(String key) {
		Object ret = this.data.get(key);
		if (ret == null) return null;
		if ((ret instanceof DataList) || (ret instanceof List)) return new DataList(ret);
		return null;
	}
	public DataList getDataListOrNew(String key) {
		DataList list = this.getDataList(key);
		return list == null ? new DataList() : list;
	}
	public DataMap getDataMap(String key) {
		Object ret = this.data.get(key);
		if (ret == null) return null;
		if ((ret instanceof DataMap) || (ret instanceof Map)) return new DataMap(ret);
		return null;
	}
	public DataMap getDataMapOrNew(String key) {
		DataMap map = this.getDataMap(key);
		return map == null ? new DataMap() : map;
	}
	@SuppressWarnings("unchecked")
	public boolean has(String key) {
		Object value = get(key);
		if (value == null) return false;
		if (value.getClass() == Integer.class) {
			return Integer.parseInt(String.valueOf(value)) != 0;
		} else if (value.getClass() == Long.class) {
			return Long.parseLong(String.valueOf(value)) != 0;
		} else if (value.getClass() == Float.class) {
			return Float.parseFloat(String.valueOf(value)) != 0;
		} else if (value.getClass() == Double.class) {
			return Double.parseDouble(String.valueOf(value)) != 0;
		} else if (value.getClass() == BigDecimal.class) {
			return !new BigDecimal(String.valueOf(value)).equals(new BigDecimal("0"));
		} else if (value.getClass() == String.class) {
			return ((String) value).length() > 0;
		} else if (value instanceof List) {
			return ((List<Object>) value).size() > 0;
		} else if (value instanceof Map) {
			return ((Map<String, Object>) value).keySet().size() > 0;
		} else if (value instanceof DataList) {
			return !((DataList) value).list.isEmpty();
		} else if (value instanceof DataMap) {
			return !((DataMap) value).data.isEmpty();
		} else if (value.getClass().isArray()) {
			return Array.getLength(value) > 0;
		}
		return true;
	}
	public void put(String key, Object value) {
		this.data.put(key, value);
	}
	@SuppressWarnings("unchecked")
	public void putAll(Object data) {
		if (data instanceof DataMap) {
			this.data.putAll(((DataMap) data).data);
			return;
		}
		if (!(data instanceof Map)) throw new IllegalArgumentException("PARAMER MUSH BE Map<String, Object>");
		if (((Map<String, Object>) data).isEmpty()) return;
		this.data.putAll((Map<String, Object>) data);
	}
	public void remove(String key) {
		this.data.remove(key);
	}
	public boolean isEmpty() {
		return this.data.isEmpty();
	}
	public void clear() {
		this.data.clear();
	}
	@SuppressWarnings("unchecked")
	public <K> Set<K> keySet() {
		return (Set<K>) this.data.keySet();
	}
	@SuppressWarnings("unchecked")
	public <V> Collection<V> values() {
		return (Collection<V>) this.data.values();
	}
	@SuppressWarnings("unchecked")
	public <T> Set<T> entrySet() {
		return (Set<T>) this.data.entrySet();
	}
	public String toString() {
		return this.data.toString();
	}
	//DataMap转Map<String, Object>
	@SuppressWarnings("unchecked")
	public static Object dataToMap(Object obj) {
		if (obj == null) return null;
		if (obj instanceof List) {
			List<Object> list = new ArrayList<>();
			for (Object item : (List<?>)obj) list.add(dataToMap(item));
			return list;
		} else if (obj instanceof Map) {
			Map<String, Object> map = new HashMap<>();
			for (String key : ((Map<String, Object>) obj).keySet()) map.put(key, dataToMap(((Map<?, ?>) obj).get(key)));
			return map;
		} else if (obj instanceof DataList) {
			return dataToMap(((DataList) obj).list);
		} else if (obj instanceof DataMap) {
			Map<String, Object> data = ((DataMap)obj).data;
			Map<String, Object> map = new LinkedHashMap<>();
			for (String key : data.keySet()) map.put(key, dataToMap(data.get(key)));
			return map;
		}
		return obj;
	}
}
