//Developed by @mario 1.0.20220117
package com.laokema.tool;

import com.alibaba.fastjson.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.regex.*;

public class DB {
	static DB db = null;
	static String host = null;
	static String username = null;
	static String password = null;
	static String database = null;
	static String prefix = null;
	static Connection conn = null;
	static PreparedStatement ps =  null;
	static String rootPath = "";
	static String cacheDir = "sql_c";

	private String table = "";
	private List<String> left = null;
	private List<String> right = null;
	private List<String> inner = null;
	private List<String> cross = null;
	private String where = "";
	private List<Object> whereParams = null;
	private String field = "";
	private String distinct = "";
	private String order = "";
	private String group = "";
	private String having = "";
	private int offset = 0;
	private int pagesize = 0;
	private int cached = 0;
	private boolean pagination = false;
	private String paginationMark = "";
	private boolean printSql = false;

	static {
		try {
			Properties properties = new Properties();
			properties.load(DB.class.getClassLoader().getResourceAsStream("application.properties"));
			host = properties.getProperty("db.host");
			username = properties.getProperty("db.username");
			password = properties.getProperty("db.password");
			database = properties.getProperty("db.database");
			prefix = properties.getProperty("db.prefix");
		} catch (IOException e) {
			System.out.println("获取配置文件失败：" + e.getMessage());
			e.printStackTrace();
		}
	}

	//数据库连接
	public static void init() {
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			conn = DriverManager.getConnection("jdbc:mysql://" + host + "/" + database + "?useUnicode=true&characterEncoding=utf8", username, password);
		} catch (Exception e) {
			System.out.println("SQL驱动程序初始化失败：" + e.getMessage());
			e.printStackTrace();
		}
	}
	//创建单例
	public static DB share() {
		return DB.share("");
	}
	public static DB share(String table) {
		DB instance = db;
		if (instance == null) {
			db = new DB();
			instance = db;
		}
		if (table.length() > 0) instance.name(table);
		return instance;
	}
	//指定表名, 可设置别名, 支持双减号转表前缀, 如: name('table t')
	public DB name(String table) {
		boolean restore = true;
		if (table.startsWith("!")) { //表名前加!代表不restore
			restore = false;
			table = table.substring(1);
		}
		if (restore) restore();
		this.table = (table.matches("^--\\w+--") ? "" : prefix) + table.replace(prefix, "");
		return this;
	}
	//左联接
	public DB left(String table, String on) {
		String sql = " LEFT JOIN " + prefix + table.replace(prefix, "") + " ON " + on;
		if (this.left == null) this.left = new ArrayList<>();
		this.left.add(sql);
		return this;
	}
	//右联接
	public DB right(String table, String on) {
		String sql = " RIGHT JOIN " + prefix + table.replace(prefix, "") + " ON " + on;
		if (this.right == null) this.right = new ArrayList<>();
		this.right.add(sql);
		return this;
	}
	//等值联接
	public DB inner(String table, String on) {
		String sql = " INNER JOIN " + prefix + table.replace(prefix, "") + " ON " + on;
		if (this.inner == null) this.inner = new ArrayList<>();
		this.inner.add(sql);
		return this;
	}
	//多联接
	public DB cross(String table) {
		if (this.cross == null) this.cross = new ArrayList<>();
		this.cross.add("," + prefix + table.replace(prefix, ""));
		return this;
	}
	//条件
	public DB where(Object where, Object...params) {
		String wheres = this.where;
		if (where instanceof Integer) {
			wheres += (wheres.length() > 0 ? " AND " : "") + "id=" + where;
		} else if (where instanceof String[]) {
			String[] items = new String[((String[])where).length];
			int i = 0;
			for (String item : (String[])where) {
				items[i] = item.contains("=") ? item : "`" + item + "`=?";
				i++;
			}
			wheres += (wheres.length() > 0 ? " AND " : "") + StringUtils.join(items, " AND ");
		} else if ((where instanceof String) && ((String)where).length() > 0){
			String _where = (String) where;
			if (_where.contains("|") && !_where.contains("=")) {
				String[] items = _where.split("\\|");
				int i = 0;
				for (String item : items) {
					items[i] = item.contains(".") ? item + "=?" : "`" + item + "`=?";
					i++;
				}
				wheres += (wheres.length() > 0 ? " AND (" : "(") + StringUtils.join(items, " OR ") + ")";
			} else if (_where.contains("&") && !_where.contains("=")) {
				String[] items = _where.split("&");
				int i = 0;
				for (String item : items) {
					items[i] = item.contains(".") ? item + "=?" : "`" + item + "`=?";
					i++;
				}
				wheres += (wheres.length() > 0 ? " AND " : "") + StringUtils.join(items, " AND ");
			} else {
				wheres += (wheres.length() > 0 ? " AND " : "") + ((String)where).replaceFirst("^ AND ", "");
			}
		}
		this.where = wheres;
		//绑定参数
		if (params.length > 0) {
			if (this.whereParams == null) this.whereParams = new ArrayList<>();
			this.whereParams.addAll(Arrays.asList(params));
		}
		return this;
	}
	//要查询的字段
	//.field(["id", "name"]) or .field("id, name")
	@SuppressWarnings("unchecked")
	public DB field(Object field) {
		String fields = this.field;
		if (field instanceof String[]) {
			fields += (fields.length() > 0 ? ", " : "") + "`" + StringUtils.join((String[])field, "`, ") + "`";
		} else if (field instanceof Map) { //指定别名
			Map<String, String> entry = (Map<String, String>) field;
			String[] items = new String[entry.size()];
			int i = 0;
			for (Map.Entry<String, String> item : entry.entrySet()) {
				items[i] = "`" + item.getKey() + "`" + (item.getValue().length() > 0 ? " as " + item.getValue() : "");
				i++;
			}
			fields += (fields.length() > 0 ? ", " : "") + StringUtils.join(items, ", ");
		} else if ((field instanceof String) && ((String)field).length() > 0){
			if (((String)field).matches("^\\w+(\\|\\w+)+$")) { //排除不需要的字段
				try {
					List<String> fieldArray = new ArrayList<>();
					String[] items = ((String)field).split("\\|");
					String sql = DB.replaceTable("SHOW COLUMNS FROM " + this.table);
					if (conn == null) DB.init();
					ps = conn.prepareStatement(sql);
					ResultSet rs = ps.executeQuery();
					while (rs.next()) {
						if (!Arrays.asList(items).contains(rs.getString("Field"))) fieldArray.add(rs.getString("Field"));
					}
					fields = StringUtils.join(fieldArray, ", ");
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					DB.close();
				}
			} else {
				fields += (fields.length() > 0 ? ", " : "") + field;
			}
		}
		this.field = fields;
		return this;
	}
	//去重查询
	public DB distinct(String field) {
		this.distinct = "DISTINCT(" + field + ")";
		return this;
	}
	//时间对比查询, .whereTime('d', 'add_time', '<1') //查询add_time小于1天的记录
	public DB whereTime(String interval, String field, String operatorAndValue) {
		return whereTime(interval, field, operatorAndValue, "");
	}
	public DB whereTime(String interval, String field, String operatorAndValue, String now) {
		switch (interval) {
			case "y":interval = "YEAR";break;
			case "q":interval = "QUARTER";break;
			case "m":interval = "MONTH";break;
			case "w":interval = "WEEK";break;
			case "d":interval = "DAY";break;
			case "h":interval = "HOUR";break;
			case "n":interval = "MINUTE";break;
			case "s":interval = "SECOND";break;
		}
		interval = interval.toUpperCase();
		String his = "";
		switch (interval) {
			case "HOUR":his = " %H";break;
			case "MINUTE":his = " %H:%i";break;
			case "SECOND":his = " %H:%i:%s";break;
		}
		if (now.length() == 0) {
			if (his.length() == 0) now = "DATE_FORMAT(NOW(),'%Y-%m-%d')";
			else {
				if (interval.equals("HOUR")) now = "DATE_FORMAT(NOW(),'%Y-%m-%d %H')";
				else if (interval.equals("MINUTE")) now = "DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i')";
				else now = "NOW()";
			}
		}
		//fieldOpe = "IF(ISNUMERIC(" + field + "),FROM_UNIXTIME(" + field + ",'%Y-%m-%d" + his + "')," + field + ")";
		String fieldOpe = "FROM_UNIXTIME(" + field + ",'%Y-%m-%d" + his + "')";
		this.where += (this.where.length() > 0 ? " AND " : "") + "TIMESTAMPDIFF(" + interval + "," + fieldOpe + "," + now + ")" + operatorAndValue;
		return this;
	}
	//LIKE查询, 如: name LIKE 'G_ARTICLE/_%' ESCAPE '/'
	public DB like(String field, String str) {
		return like(field, str, "");
	}
	public DB like(String field, String str, String escape) {
		String where = field + " LIKE '" + str + "'";
		if (escape.length() > 0) where += " ESCAPE '" + escape + "'";
		this.where += (this.where.length() > 0 ? " AND " : "") + where;
		return this;
	}
	//排序
	public DB order(String order) {
		this.order = order.length() > 0 ? " ORDER BY " + order : "";
		return this;
	}
	//按字段排序, 如: ORDER BY FIELD(`id`, 1, 9, 8, 4)
	public DB orderField(String field, String value) {
		this.order = field.length() > 0 ? " ORDER BY FIELD(`" + field + "`, " + value + ")" : "";
		return this;
	}
	//分组(聚合)
	public DB group(String group) {
		this.group = group.length() > 0 ? " GROUP BY " + group : "";
		return this;
	}
	//聚合筛选, 语法与where一样
	public DB having(String having) {
		this.having = having.length() > 0 ? " HAVING " + having : "";
		return this;
	}
	//记录偏移量
	public DB offset(int offset) {
		this.offset = offset;
		return this;
	}
	//返回记录最大数目
	public DB pagesize(int pagesize) {
		this.pagesize = pagesize;
		return this;
	}
	//设定记录偏移量与返回记录最大数目
	public DB limit(int offset, int pagesize) {
		this.offset = offset;
		this.pagesize = pagesize;
		return this;
	}
	//使用缓存查询结果,0不缓存,-1永久缓存,>0缓存时间(单位秒)
	public DB cached(int cached) {
		this.cached = cached;
		return this;
	}
	//设置分页
	public DB pagination(boolean pagination) {
		return pagination(pagination, "page");
	}
	public DB pagination(boolean pagination, String paginationMark) {
		this.pagination = pagination;
		this.paginationMark = paginationMark;
		return this;
	}
	//打印sql语句
	public DB printSql() {
		this.printSql = true;
		return this;
	}
	//记录是否存在
	public boolean exist() {
		return (Long)count() > 0;
	}
	//记录数量
	public static class Count<T> {
		private T count;
		public Count() {}
		public void setCount(T count) {
			this.count = count;
		}
		public T getCount() {
			return this.count;
		}
	}
	public <T> T count() {
		return count("COUNT(*)");
	}
	public <T> T count(String field) {
		return count(field, Integer.class);
	}
	@SuppressWarnings("unchecked")
	public <T> T count(String field, Class<? extends Number> type) {
		Count<T> count = this.field(field + " as count").find(Count.class);
		T res = count.getCount();
		if (res == null) {
			switch (type.getName()) {
				case "java.lang.Integer": res = (T) new Integer(0);break;
				case "java.lang.Double": res = (T) new Double(0);break;
			}
		} else if (!res.getClass().equals(type) || res.getClass().equals(BigDecimal.class)) {
			try {
				String t = type.toString().substring(type.toString().lastIndexOf(".") + 1);
				if (t.equals("Integer")) {
					t = "Int";
					if (res.getClass().getName().equals("java.lang.Double")) res = (T) String.valueOf(res).split("\\.")[0];
				}
				String parseName = "parse" + t;
				Method parse = type.getMethod(parseName, String.class);
				res = (T) parse.invoke(null, String.valueOf(res));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return res;
	}
	//查询字段总和
	@SuppressWarnings("unchecked")
	public <T> T sum(String field) {
		return (T) sum(field, Integer.class);
	}
	public <T> T sum(String field, Class<? extends Number> type) {
		return count("SUM(" + field + ")", type);
	}
	//查询字段平均值
	@SuppressWarnings("unchecked")
	public <T> T avg(String field) {
		return (T) avg(field, Double.class);
	}
	public <T> T avg(String field, Class<? extends Number> type) {
		return count("AVG(" + field + ")", type);
	}
	//查询字段最小值
	@SuppressWarnings("unchecked")
	public <T> T min(String field) {
		return (T) min(field, Integer.class);
	}
	public <T> T min(String field, Class<? extends Number> type) {
		return count("MIN(" + field + ")", type);
	}
	//查询字段最大值
	@SuppressWarnings("unchecked")
	public <T> T max(String field) {
		return (T) max(field, Integer.class);
	}
	public <T> T max(String field, Class<? extends Number> type) {
		return count("MAX(" + field + ")", type);
	}
	//查询字段
	//String name = DB.share("user").where("id=1").value("name", User.class);
	public <T, R> R value(String field, Class<T> clazz) {
		return value(field, clazz, null);
	}
	@SuppressWarnings("unchecked")
	public <T, R> R value(String field, Class<T> clazz, Class<R> type) {
		if (field == null || field.length() == 0 || field.equals("*")) return count();
		try {
			R res = null;
			T obj = field(field).find(clazz);
			if (obj == null) {
				if (type != null) {
					if (type == Integer.class) {
						return (R) Integer.valueOf("0");
					} else if (type == Long.class) {
						return (R) Long.valueOf("0");
					} else if (type == Float.class) {
						return (R) Float.valueOf("0");
					} else if (type == Double.class) {
						return (R) Double.valueOf("0");
					}
				}
				return null;
			}
			if (field.contains(".")) field = field.substring(field.lastIndexOf(".") + 1);
			try {
				Field f = obj.getClass().getDeclaredField(field);
				String getterName = "get" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);
				Method getter = clazz.getMethod(getterName);
				res = (R) getter.invoke(obj);
			} catch (NoSuchFieldException e) {
				if (type != null) {
					Method getter = clazz.getMethod("get", String.class, type);
					res = (R) getter.invoke(obj, field, type);
				} else {
					Method getter = clazz.getMethod("get", String.class);
					res = (R) getter.invoke(obj, field);
				}
			}
			return res;
		} catch (Exception e) {
			System.out.println("SQL数据库查询字段异常");
			e.printStackTrace();
		}
		return null;
	}
	@SuppressWarnings("unchecked")
	public <T> T valueFromMap(String field, Class<T> type) {
		DataMap obj = field(field).find();
		if (obj == null) {
			if (type != null) {
				if (type == Integer.class) {
					return (T) Integer.valueOf("0");
				} else if (type == Long.class) {
					return (T) Long.valueOf("0");
				} else if (type == Float.class) {
					return (T) Float.valueOf("0");
				} else if (type == Double.class) {
					return (T) Double.valueOf("0");
				}
			}
			return null;
		}
		if (field.contains(".")) field = field.substring(field.lastIndexOf(".") + 1);
		return (T) obj.get(field);
	}
	//查询某列值
	public <T> String[] column(String field, Class<T> clazz) {
		String[] columns = new String[0];
		try {
			List<T> list = field(field).select(clazz);
			if (list != null) {
				columns = new String[list.size()];
				for (int i = 0; i < list.size(); i++) {
					T obj = list.get(i);
					String getterName = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
					Method getter = clazz.getMethod(getterName);
					columns[i] = (String) getter.invoke(obj);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return columns;
	}
	//查询单条记录(返回Map)
	public DataMap row() {
		return find();
	}
	public DataMap find() {
		List<DataMap> list = this.pagesize(1).select();
		return list == null ? null : list.get(0);
	}
	//查询(返回List<DataMap>)
	public List<DataMap> select() {
		String sql = _createSql();
		List<DataMap> res = new ArrayList<>();
		try {
			if (this.printSql) System.out.println(sql);
			if (this.cached != 0) {
				List<DataMap> r = _cacheSql(sql);
				if (r != null) return r;
			} else if (this.pagination && this.pagesize != 1) {
				_setPagination();
			}
			if (conn == null) DB.init();
			ps = conn.prepareStatement(sql);
			if (this.whereParams != null) {
				for (int i = 0; i < this.whereParams.size(); i++) { //绑定参数
					ps.setObject(i + 1, this.whereParams.get(i));
				}
			}
			ResultSet rs = ps.executeQuery();
			String[] columnNames = _getColumnNames(rs);
			while (rs.next()) {
				DataMap item = new DataMap();
				for (String columnName : columnNames) {
					Object value = rs.getObject(columnName);
					if (value != null && value.getClass().equals(BigDecimal.class)) value = ((BigDecimal)value).doubleValue();
					item.put(columnName, value);
				}
				res.add(item);
			}
		} catch (Exception e) {
			System.out.println("SQL数据库查询异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		if (res.size() > 0) {
			if (this.cached != 0 && sql.length() > 0) _cacheSql(sql, res);
			return res;
		}
		return null;
	}
	//查询单条记录(返回对象)
	//User user = DB.share("user").find(User.class);
	public <T> T row(Class<T> clazz) {
		return find(clazz);
	}
	public <T> T find(Class<T> clazz) {
		List<T> list = this.pagesize(1).select(clazz);
		return list == null ? null : list.get(0);
	}
	//查询(返回List<对象>)
	//List<User> user = DB.share("user").select(User.class);
	public <T> List<T> select(Class<T> clazz) {
		String sql = _createSql();
		List<T> res = new ArrayList<>();
		try {
			if (this.printSql) System.out.println(sql);
			if (this.cached != 0) {
				List<T> r = _cacheSql(sql, clazz);
				if (r != null) return r;
			} else if (this.pagination && this.pagesize != 1) {
				_setPagination();
			}
			if (conn == null) DB.init();
			ps = conn.prepareStatement(sql);
			if (this.whereParams != null) {
				for (int i = 0; i < this.whereParams.size(); i++) { //绑定参数
					ps.setObject(i + 1, this.whereParams.get(i));
				}
			}
			ResultSet rs = ps.executeQuery();
			String[] columnNames = _getColumnNames(rs);
			Constructor<T> constructor = clazz.getConstructor();
			//Field[] fields = clazz.getDeclaredFields(); //获取所有属性
			while (rs.next()) {
				boolean seted = false;
				T obj = constructor.newInstance(); //创建一个实例
				for (String columnName : columnNames) {
					Object value = rs.getObject(columnName);
					if (value != null && value.getClass().equals(BigDecimal.class)) value = ((BigDecimal)value).doubleValue();
					try {
						Field f = obj.getClass().getDeclaredField(columnName);
						String setterName = "set" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1); //构造 setter 方法名
						Method setter = clazz.getMethod(setterName, f.getType()); //调用对应实例的 setter 方法给它设置属性
						if (value != null && !f.getType().equals(value.getClass()) && !f.getType().getName().equals("java.lang.Object")) {
							if (f.getType() == Integer.class) {
								value = Integer.parseInt(String.valueOf(value));
							} else if (f.getType() == Long.class) {
								value = Long.parseLong(String.valueOf(value));
							} else if (f.getType() == Float.class) {
								value = Float.parseFloat(String.valueOf(value));
							} else if (f.getType() == Double.class) {
								value = Double.parseDouble(String.valueOf(value));
							} else if (f.getType() == String.class) {
								value = String.valueOf(value);
							} else {
								System.out.println(clazz.getName()+"     "+setterName+"      "+f.getName()+" = "+f.getType().getName()+"        data = "+value.getClass().getName());
							}
						}
						if (value != null) setter.invoke(obj, value);
						seted = true;
					} catch (NoSuchFieldException e) {
						Method setter = clazz.getMethod("set", String.class, Object.class);
						if (value != null) setter.invoke(obj, columnName, value);
						seted = true;
					}
				}
				/*for (Field f : fields) {
					if (!Arrays.asList(columnNames).contains(f.getName())) continue;
					Object value = rs.getObject(f.getName());
					if (value != null && value.getClass().equals(BigDecimal.class)) value = ((BigDecimal)value).doubleValue();
					String setterName = "set" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1); //构造 setter 方法名
					Method setter = clazz.getMethod(setterName, f.getType()); //调用对应实例的 setter 方法给它设置属性
					if (value != null && !f.getType().equals(value.getClass()) && !f.getType().getName().equals("java.lang.Object")) {
						System.out.println(clazz.getName()+"     "+setterName+"      "+f.getName()+" = "+f.getType().getName()+"        data = "+value.getClass().getName());
					}
					if (value != null) setter.invoke(obj, value);
					seted = true;
				}*/
				if (seted) res.add(obj);
			}
		} catch (Exception e) {
			System.out.println("SQL数据库查询异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		if (res.size() > 0) {
			if (this.cached != 0 && sql.length() > 0) _cacheSql(sql, res);
			return res;
		}
		return null;
	}
	//分页用获取总记录数
	private void _setPagination() {
		String sql = _createSql(true);
		if (sql.matches("SELECT DISTINCT\\(")) {
			sql = sql.replaceAll("SELECT DISTINCT\\(([^)]+)\\).*\\s+FROM\\b", "SELECT COUNT(DISTINCT($1)) FROM");
		} else {
			sql = sql.replaceAll("^SELECT.*\\s+FROM\\b", "SELECT COUNT(*) FROM");
			sql = sql.replaceAll(" ORDER BY (\\s*,?.+?(A|DE)SC)+", "");
			if (sql.contains("GROUP BY")) sql = "SELECT COUNT(*) FROM (" + sql + ") gb";
		}
		try {
			if (this.printSql) System.out.println(sql);
			if (conn == null) DB.init();
			ps = conn.prepareStatement(sql);
			if (this.whereParams != null) {
				for (int i = 0; i < this.whereParams.size(); i++) {
					ps.setObject(i + 1, this.whereParams.get(i));
				}
			}
			ResultSet rs = ps.executeQuery();
			int records = 0;
			while (rs.next()) {
				records = rs.getInt(1);
			}
			ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			HttpServletRequest request = Objects.requireNonNull(servletRequestAttributes).getRequest();
			//HttpServletResponse response = Objects.requireNonNull(servletRequestAttributes).getResponse();
			Pagination p = new Pagination(request, records).isCn();
			request.setAttribute(this.paginationMark, p);
		} catch (Exception e) {
			System.out.println("SQL数据库设置分页异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
	}
	//获取所有列名
	private String[] _getColumnNames(ResultSet rs) {
		String[] names = new String[0];
		try {
			ResultSetMetaData metaData = rs.getMetaData();
			int count = metaData.getColumnCount();
			names = new String[count];
			for (int i = 0; i < count; i++) {
				names[i] = metaData.getColumnName(i+1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return names;
	}
	//判断查询结果集中是否存在某列
	private boolean _isExistColumn(ResultSet rs, String columnName) {
		try {
			if (rs.findColumn(columnName) > 0) return true;
		} catch (SQLException e) {
			return false;
		}
		return false;
	}
	//创建SQL语句
	private String _createSql() {
		return _createSql(false);
	}
	private String _createSql(boolean isPagination) {
		StringBuilder sql = new StringBuilder();
		String field = this.field;
		if (field.length() == 0) {
			if (this.distinct.length() == 0) field = "*";
			else field = this.distinct;
		} else if (this.distinct.length() > 0) {
			if (!field.trim().equals("*")) field = this.distinct + ", " + field;
			else field = this.distinct;
		}
		sql.append("SELECT ").append(field).append(" FROM ").append(this.table);
		if (this.left != null) sql.append(StringUtils.join(this.left, ""));
		if (this.right != null) sql.append(StringUtils.join(this.right, ""));
		if (this.inner != null) sql.append(StringUtils.join(this.inner, ""));
		if (this.cross != null) sql.append(StringUtils.join(this.cross, ""));
		if (this.where.length() > 0) sql.append(" WHERE ").append(this.where);
		if (this.group.length() > 0) sql.append(this.group);
		if (this.having.length() > 0) sql.append(this.having);
		if (!isPagination) {
			if (this.order.length() > 0) sql.append(this.order);
			if (this.pagesize == 1) sql.append(" LIMIT 1");
			else if (this.pagesize > 0) sql.append(" LIMIT ").append(this.offset).append(",").append(this.pagesize);
		}
		return DB.replaceTable(sql.toString());
	}
	//获取/设置sql缓存
	private List<DataMap> _cacheSql(String sql) {
		if (rootPath == null || rootPath.length() == 0) {
			rootPath = Objects.requireNonNull(this.getClass().getResource("/")).getPath();
			if (rootPath == null) rootPath = "";
		}
		String cachePath = rootPath + "/temp/" + cacheDir;
		File file = new File(cachePath + "/" + _md5(sql));
		if (file.exists()) {
			if (this.cached == -1 || (new Date().getTime()/1000 - file.lastModified()/1000) <= this.cached) {
				StringBuilder res = new StringBuilder();
				BufferedReader reader = null;
				try {
					reader = new BufferedReader(new FileReader(file));
					String line;
					while ((line = reader.readLine()) != null) {
						res.append(line);
					}
					JSONArray array = JSONObject.parseArray(res.toString());
					List<DataMap> list = new ArrayList<>();
					for (Object item : array) list.add(new DataMap(item));
					return list;
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					try{
						if (reader != null) reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return null;
	}
	private <T> List<T> _cacheSql(String sql, Class<T> clazz) {
		if (rootPath == null || rootPath.length() == 0) {
			rootPath = Objects.requireNonNull(this.getClass().getResource("/")).getPath();
			if (rootPath == null) rootPath = "";
		}
		String cachePath = rootPath + "/temp/" + cacheDir;
		File file = new File(cachePath + "/" + _md5(sql));
		if (file.exists()) {
			if (this.cached == -1 || (new Date().getTime()/1000 - file.lastModified()/1000) <= this.cached) {
				StringBuilder res = new StringBuilder();
				BufferedReader reader = null;
				try {
					reader = new BufferedReader(new FileReader(file));
					String line;
					while ((line = reader.readLine()) != null) {
						res.append(line);
					}
					return JSONObject.parseArray(res.toString(), clazz);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					try{
						if (reader != null) reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return null;
	}
	private void _cacheSql(String sql, List<?> res) {
		if (rootPath == null || rootPath.length() == 0) {
			rootPath = Objects.requireNonNull(this.getClass().getResource("/")).getPath();
			if (rootPath == null) rootPath = "";
		}
		String cachePath = rootPath + "/temp/" + cacheDir;
		File paths = new File(cachePath);
		if (!paths.exists()) {
			if (!paths.mkdirs()) throw new IllegalArgumentException("File path create fail: " + cachePath);
		}
		File file = new File(cachePath + "/" + _md5(sql));
		try {
			if (res.get(0) instanceof DataMap) {
				List<Map<String, Object>> list = new ArrayList<>();
				for (Object map : res) list.add(((DataMap)map).data);
				FileWriter fileWritter = new FileWriter(file);
				fileWritter.write(JSON.toJSONString(list));
				fileWritter.close();
			} else {
				FileWriter fileWritter = new FileWriter(file);
				fileWritter.write(JSON.toJSONString(res));
				fileWritter.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	//MD5
	private String _md5(String str) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(str.getBytes());
			return new BigInteger(1, md.digest()).toString(16);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	//插入记录
	//int row = DB.share("user").insert(new String[]{"name", "age"}, name, age);
	public int insert(List<String> data, Object...dataParams) {
		return insert(data.toArray(new String[0]), dataParams);
	}
	public int insert(String[] data, Object...dataParams) {
		return insert(data, new ArrayList<>(Arrays.asList(dataParams)));
	}
	public int insert(Map<String, Object> datas) {
		String[] data = new String[datas.keySet().size()];
		List<Object> dataParams = new ArrayList<>();
		int i = 0;
		for (String key : datas.keySet()) {
			data[i] = key;
			dataParams.add(datas.get(key));
		}
		return insert(data, dataParams);
	}
	public int insert(String[] data, List<Object> dataParams) {
		int row = 0;
		try {
			StringBuilder sql = new StringBuilder("INSERT INTO " + this.table + " (");
			for (String d : data) sql.append(d).append(", ");
			sql = new StringBuilder(sql.toString().replaceAll("(^, |, $)", "")).append(") VALUES(");
			for (String d : data) sql.append("?, ");
			sql = new StringBuilder(sql.toString().replaceAll("(^, |, $)", "")).append(")");
			String sq = DB.replaceTable(sql.toString());
			if (this.printSql) System.out.println(sq);
			if (conn == null) DB.init();
			ps =  conn.prepareStatement(sq);
			int k = 0;
			if (dataParams != null) {
				for (Object dataParam : dataParams) {
					ps.setObject(k + 1, dataParam);
					k++;
				}
			}
			if (this.whereParams != null) {
				for (Object whereParam : this.whereParams) {
					ps.setObject(k + 1, whereParam);
					k++;
				}
			}
			row =  ps.executeUpdate();
		} catch (SQLException e) {
			System.out.println("SQL数据库插入异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		return row;
	}
	//快捷更新某字段
	public int setField(String field, String value) {
		return update(field, value);
	}
	//字段递增
	public int setInc(String field) {
		return setInc(field, 1);
	}
	public int setInc(String field, int step) {
		String value = String.valueOf(step);
		if (step > 0) value = "+" + value;
		return setField(field, value);
	}
	//字段递减
	public int setDec(String field) {
		return setDec(field, 1);
	}
	public int setDec(String field, int step) {
		return setInc(field, -step);
	}
	//更新记录
	//int row = DB.share("user").where("id=?", id).update(new String[]{"name", "age"}, name, age);
	public int update(String data, Object...dataParams) {
		List<String> datas = new ArrayList<>();
		datas.add(data);
		return update(datas, dataParams);
	}
	public int update(List<String> data, Object...dataParams) {
		return update(data.toArray(new String[0]), dataParams);
	}
	public int update(String[] data, Object...dataParams) {
		return update(data, new ArrayList<>(Arrays.asList(dataParams)));
	}
	public int update(Map<String, Object> datas) {
		String[] data = new String[datas.keySet().size()];
		List<Object> dataParams = new ArrayList<>();
		int i = 0;
		for (String key : datas.keySet()) {
			data[i] = key;
			dataParams.add(datas.get(key));
			i++;
		}
		return update(data, dataParams);
	}
	public int update(String[] data, List<Object> dataParams) {
		int row = 0;
		try {
			StringBuilder sql = new StringBuilder("UPDATE " + this.table + " SET ");
			int k = 0;
			for (String d : data) {
				if (dataParams != null && dataParams.size() > 0) sql.append("`");
				sql.append(d);
				if (dataParams != null && dataParams.size() > 0) {
					Object param = dataParams.get(k);
					if ((param instanceof String) && ((String)param).matches("^[+\\-*/]")) sql.append("`=`").append(d).append("`").append(param);
					else sql.append("`=?");
				}
				sql.append(", ");
				k++;
			}
			sql = new StringBuilder(sql.toString().replaceAll("(^, |, $)", ""));
			if (this.where.length() > 0) sql.append(" WHERE ").append(this.where);
			String sq = DB.replaceTable(sql.toString());
			if (this.printSql) System.out.println(sq);
			if (conn == null) DB.init();
			ps =  conn.prepareStatement(sq);
			k = 0;
			if (dataParams != null) {
				for (Object param : dataParams) {
					if (!(param instanceof String) || !((String) param).matches("^[+\\-*/]")) {
						ps.setObject(k + 1, param);
						k++;
					}
				}
			}
			if (this.whereParams != null) {
				for (Object whereParam : this.whereParams) {
					ps.setObject(k + 1, whereParam);
					k++;
				}
			}
			row =  ps.executeUpdate();
		} catch (SQLException e) {
			System.out.println("SQL数据库更新异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		return row;
	}
	//删除记录
	//int row = DB.share("user").where("id=?", id).delete();
	public int delete() {
		return delete(null);
	}
	public int delete(Object where, Object...whereParams) {
		if (where != null) this.where(where, whereParams);
		int row = 0;
		try {
			StringBuilder sql = new StringBuilder("DELETE FROM " + this.table);
			if (this.where.length() > 0) sql.append(" WHERE ").append(this.where);
			String sq = DB.replaceTable(sql.toString());
			if (this.printSql) System.out.println(sq);
			if (conn == null) DB.init();
			ps =  conn.prepareStatement(sq);
			int k = 0;
			if (this.whereParams != null) {
				for (Object whereParam : this.whereParams) {
					ps.setObject(k + 1, whereParam);
					k++;
				}
			}
			row =  ps.executeUpdate();
		} catch (SQLException e) {
			System.out.println("SQL数据库删除异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		return row;
	}
	//复原参数
	public void restore() {
		this.table = "";
		this.left = null;
		this.right = null;
		this.inner = null;
		this.cross = null;
		this.where = "";
		this.whereParams = null;
		this.field = "";
		this.distinct = "";
		this.order = "";
		this.group = "";
		this.having = "";
		this.offset = 0;
		this.pagesize = 0;
		this.cached = 0;
		this.pagination = false;
		this.paginationMark = "";
		this.printSql = false;
	}
	//原生查询
	public static ResultSet query(String sql, Object...dataParams) {
		ResultSet rs = null;
		try {
			sql = DB.replaceTable(sql);
			if (conn == null) DB.init();
			ps =  conn.prepareStatement(sql);
			for (int i = 0; i < dataParams.length; i++) {
				ps.setObject(i + 1, dataParams[i]);
			}
			rs = ps.executeQuery();
		} catch (SQLException e) {
			System.out.println("SQL数据库QUERY查询异常");
			e.printStackTrace();
		}
		return rs;
	}
	//原生执行
	public static int execute(String sql, Object...dataParams) {
		int row = 0;
		try {
			sql = DB.replaceTable(sql);
			if (conn == null) DB.init();
			ps =  conn.prepareStatement(sql);
			for (int i = 0; i < dataParams.length; i++) {
				ps.setObject(i + 1, dataParams[i]);
			}
			row =  ps.executeUpdate();
		} catch (SQLException e) {
			System.out.println("SQL数据库增删改异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		return row;
	}
	//替换SQL语句中表名双减号为表前缀
	public static String replaceTable(String sql) {
		Matcher matcher = Pattern.compile("(--(\\w+)--)").matcher(sql);
		StringBuffer res = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(res, prefix + matcher.group(2).toLowerCase());
		}
		matcher.appendTail(res);
		return res.toString();
	}
	//获取指定列类型
	public static String getColumnType(String table, String column) {
		String type = "";
		try {
			String sql = DB.replaceTable("SHOW COLUMNS FROM " + prefix + table);
			if (conn == null) DB.init();
			ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				if (rs.getString("Field").equals(column)) {
					type = rs.getString("Type");
					if (type.startsWith("int(")) type = "Integer";
					else if (type.startsWith("varchar(")) type = "String";
					else if (type.startsWith("decimal(")) type = "Double";
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			DB.close();
		}
		return type;
	}
	//创建指定表的Map
	public static Map<String, Object> createInstanceMap(String table) {
		Map<String, Object> map = new HashMap<>();
		try {
			String sql = DB.replaceTable("SHOW COLUMNS FROM " + prefix + table);
			if (conn == null) DB.init();
			ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				if (rs.getString("Type").startsWith("int(")) {
					map.put(rs.getString("Field"), rs.getInt("Default"));
				} else if (rs.getString("Type").startsWith("decimal(")) {
					map.put(rs.getString("Field"), rs.getDouble("Default"));
				} else {
					String value = rs.getString("Default");
					if (value == null) value = "";
					map.put(rs.getString("Field"), value);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			DB.close();
		}
		return map;
	}
	//生成实例class实例的文件
	//DB.createInstanceFile("member", "com.laokema.javaweb.model.index");
	public static void createInstanceFile(String table, String packageName) {
		try {
			String sql = DB.replaceTable("SHOW COLUMNS FROM " + prefix + table.replaceAll(prefix, ""));
			if (conn == null) DB.init();
			ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			String clazz = Character.toUpperCase(table.charAt(0)) + table.substring(1);
			StringBuilder content = new StringBuilder("package ").append(packageName).append(";\n\n")
					.append("public class ").append(clazz).append(" {\n\n");
			StringBuilder method = new StringBuilder();
			while (rs.next()) {
				String field = rs.getString("Field");
				String type = rs.getString("Type");
				if (type.startsWith("int(")) type = "Integer";
				else if (type.startsWith("varchar(")) type = "String";
				else if (type.startsWith("decimal(")) type = "Double";
				content.append("\tpublic ").append(type).append(" ").append(field).append(";\n");
				String Field = Character.toUpperCase(field.charAt(0)) + field.substring(1);
				method.append("\n\tpublic ").append(type).append(" get").append(Field).append("() {\n\t\treturn ").append(field).append(";\n\t}\n");
				method.append("\tpublic void set").append(Field).append("(").append(type).append(" ").append(field).append(") {\n\t\tthis.")
						.append(field).append(" = ").append(field).append(";\n\t}\n");
			}
			content.append(method).append("\n}");
			if (rootPath.length() == 0) {
				rootPath = Objects.requireNonNull(DB.class.getResource("/")).getPath();
				if (rootPath == null) rootPath = "";
			}
			FileWriter writer = new FileWriter(rootPath + "/" + clazz + ".java");
			writer.write(content.toString());
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/*
	多个update操作共同协同工作时使用事务
	DB.startTransaction(); //开启事务
	if (DB.update(xxx) == null) {
		DB.rollback();
	    return false; //失败，返回
	}
	if (DB.update(xxx) == null) {
		DB.rollback();
	    return false; //失败，返回
	}
	DB.commit(); //成功，提交事务
	*/
	//开启事务
	public static void startTransaction() {
		try {
			if (conn == null) DB.init();
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	//回滚事务
	public static void rollback() {
		try {
			conn.rollback();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	//提交事务
	public static void commit() {
		try {
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	public static void close() {
		try {
			if (ps != null) { //仅需关闭 PreparedStatement，关闭它时 ResultSet 会自动关闭
				ps.close();
				ps = null;
			}
			if (conn != null) {
				conn.close();
				conn = null;
			}
		} catch (SQLException e) {
			System.out.println("SQL数据库关闭异常");
			e.printStackTrace();
		}
	}

	//行实例
	public static class DataMap {
		public Map<String, Object> data = new HashMap<>();
		public DataMap() {}
		@SuppressWarnings("unchecked")
		public DataMap(Object m) {
			this.data.putAll((Map<? extends String, ?>) m);
		}
		public Object get(String key) {
			return this.data.get(key);
		}
		public String getString(String key) {
			return String.valueOf(this.data.get(key));
		}
		public Integer getInt(String key) {
			return Integer.parseInt(String.valueOf(this.data.get(key)));
		}
		public Long getLong(String key) {
			return Long.parseLong(String.valueOf(this.data.get(key)));
		}
		public Float getFloat(String key) {
			return Float.parseFloat(String.valueOf(this.data.get(key)));
		}
		public Double getDouble(String key) {
			return Double.parseDouble(String.valueOf(this.data.get(key)));
		}
		public void put(String key, Object value) {
			this.data.put(key, value);
		}
		@SuppressWarnings("unchecked")
		public void putAll(Object m) {
			this.data.putAll((Map<? extends String, ?>) m);
		}
		public void remove(String key) {
			this.data.remove(key);
		}
		public boolean isEmpty() {
			return this.data.isEmpty();
		}
		public String toString() {
			return this.data.toString();
		}
	}
}
