//Developed by @mario 1.9.20220305
package com.laokema.tool;

import com.alibaba.fastjson.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.web.context.request.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.lang.reflect.Array;
import java.math.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.regex.*;

public class DB {
	static DB db = null;
	static int dbType = 0; //0:Mysql, 1:SQLite
	static String sqliteDatabase = "";
	static String sqliteDir = "db";
	static String host = null;
	static String username = null;
	static String password = null;
	static String slaverHost = null;
	static String slaverUsername = null;
	static String slaverPassword = null;
	static String prefix = null;
	static String cacheDir = null;
	static String runtimeDir = null;
	static String rootPath = "";
	static Connection conn = null;
	static PreparedStatement ps =  null;

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
			host = properties.getProperty("spring.datasource.url");
			username = properties.getProperty("spring.datasource.username");
			password = properties.getProperty("spring.datasource.password");
			slaverHost = properties.getProperty("sdk.datasource.slaver.url");
			slaverUsername = properties.getProperty("sdk.datasource.slaver.username");
			slaverPassword = properties.getProperty("sdk.datasource.slaver.password");
			prefix = properties.getProperty("sdk.datasource.prefix");
			cacheDir = properties.getProperty("sdk.datasource.cache-dir");
			runtimeDir = properties.getProperty("sdk.runtime.dir");
			if (slaverHost.length() == 0) slaverHost = host;
			if (slaverUsername.length() == 0) slaverUsername = username;
			if (slaverPassword.length() == 0) slaverPassword = password;
			if (cacheDir.length() == 0) cacheDir = "sql_c";
			if (runtimeDir.length() == 0) runtimeDir = "/runtime";
			ApplicationHome ah = new ApplicationHome(DB.class);
			rootPath = ah.getSource().getParentFile().getPath();
		} catch (IOException e) {
			System.out.println("获取配置文件失败：" + e.getMessage());
			e.printStackTrace();
		}
	}

	//数据库连接, connectionType:[0读|1写]
	public static void init(int connectionType) {
		try {
			if (dbType == 0) {
				Class.forName("com.mysql.cj.jdbc.Driver");
				if (connectionType == 0) {
					conn = DriverManager.getConnection(host, username, password);
				} else {
					conn = DriverManager.getConnection(slaverHost, slaverUsername, slaverPassword);
				}
			} else if (dbType == 1) {
				String sqlitePath = rootPath + "/" + sqliteDir;
				File paths = new File(sqlitePath);
				if (!paths.exists()) {
					if (!paths.mkdirs()) throw new IllegalArgumentException("FILE PATH CREATE FAIL:\n" + sqlitePath);
				}
				Class.forName("org.sqlite.JDBC");
				conn = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath + "/" + sqliteDatabase + ".sqlite");
			}
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
		return DB.share(table, "");
	}
	public static DB share(String table, String sqliteTable) {
		if (table.startsWith("~")) {
			//连接SQLite数据库
			/*<dependency>
				<groupId>org.xerial</groupId>
				<artifactId>sqlite-jdbc</artifactId>
			</dependency>*/
			dbType = 1;
			sqliteDatabase = table.substring(1);
			DB db =  new DB();
			if (sqliteTable.length() > 0) db.table(sqliteTable);
			return db;
		}
		//连接Mysql数据库
		dbType = 0;
		if (db == null) db = new DB();
		if (table.length() > 0) db.table(table);
		return db;
	}
	//指定表名, 可设置别名, 如: table('table t'), 支持双减号转表前缀(不区分大小写), 如: table('--TABLE-- t')
	public DB table(String table) {
		boolean restore = true;
		if (table.startsWith("!")) { //表名前加!代表不restore
			restore = false;
			table = table.substring(1);
		}
		if (restore) restore();
		this.table = DB.replaceTable(table);
		return this;
	}
	//左联接
	public DB left(String table, String on) {
		String sql = " LEFT JOIN " + DB.replaceTable(table) + " ON " + on;
		if (this.left == null) this.left = new ArrayList<>();
		this.left.add(sql);
		return this;
	}
	//右联接
	public DB right(String table, String on) {
		String sql = " RIGHT JOIN " + DB.replaceTable(table) + " ON " + on;
		if (this.right == null) this.right = new ArrayList<>();
		this.right.add(sql);
		return this;
	}
	//等值联接
	public DB inner(String table, String on) {
		String sql = " INNER JOIN " + DB.replaceTable(table) + " ON " + on;
		if (this.inner == null) this.inner = new ArrayList<>();
		this.inner.add(sql);
		return this;
	}
	//多联接
	public DB cross(String table) {
		if (this.cross == null) this.cross = new ArrayList<>();
		this.cross.add(", " + DB.replaceTable(table));
		return this;
	}
	//条件
	@SuppressWarnings("unchecked")
	public DB where(Object where, Object...whereParams) {
		String wheres = this.where;
		if ((where instanceof Integer) || Pattern.compile("^\\d+$").matcher(String.valueOf(where)).matches()) { //默认数值为id
			wheres += (wheres.length() > 0 ? " AND " : "") + "id=" + where;
		} else if (where instanceof String[]) {
			String[] items = new String[((String[])where).length];
			int i = 0;
			for (String item : (String[])where) {
				items[i] = item.contains("=") ? item : (item.contains(".") ? item + "=?" : "`" + item + "`=?");
				i++;
			}
			wheres += (wheres.length() > 0 ? " AND " : "") + StringUtils.join(items, " AND ");
		} else if (where instanceof Map) {
			Map<String, Object> entry = (Map<String, Object>) where;
			String[] items = new String[entry.keySet().size()];
			whereParams = new Object[entry.keySet().size()];
			int i = 0;
			for (String key : entry.keySet()) {
				items[i] = key.contains("=") ? key : (key.contains(".") ? key + "=?" : "`" + key + "`=?");
				whereParams[i] = entry.get(key);
				i++;
			}
			wheres += (wheres.length() > 0 ? " AND " : "") + StringUtils.join(items, " AND ");
		} else if ((where instanceof String) && ((String)where).length() > 0){
			String _where = (String) where;
			if (_where.contains("&") || _where.contains("|")) {
				Matcher matcher = Pattern.compile("([a-z_.]+([A-Z_.!%<>=]+)?)").matcher(_where);
				StringBuffer res = new StringBuffer();
				while (matcher.find()) {
					String item = matcher.group(1);
					String mark = matcher.group(2);
					String operator = "=?";
					if (mark != null) {
						switch (mark) {
							case "!":operator = "!=?";break; //field!
							case "IN":operator = " IN (?)";break; //fieldIN
							case "!IN":case "NOTIN":operator = " NOT IN (?)";break; //field!IN fieldNOTIN
							case "NULL":operator = " IS NULL";break; //fieldNULL
							case "!NULL":case "NOTNULL":operator = " IS NOT NULL";break; //field!NULL fieldNOTNULL
							default:
								if (mark.contains("%")) { //field%LIKE fieldLIKE% field%LIKE% field%uploads_LIKE%
									operator = " LIKE '" + mark.replace("LIKE", "?") + "'";
								} else {
									operator = mark + "?"; //field< field<= field> field>=
								}
						}
						item = item.substring(0, item.length() - mark.length());
					}
					item = item.contains(".") ? item + operator : "`" + item + "`" + operator;
					matcher.appendReplacement(res, item);
				}
				matcher.appendTail(res);
				_where = res.toString().replace("&", " AND ").replace("|", " OR ");
			}
			wheres += (wheres.length() > 0 ? " AND " : "") + _where.replaceFirst("^ AND ", "");
		}
		this.where = wheres;
		//绑定参数
		if (whereParams.length > 0) {
			if (this.whereParams == null) this.whereParams = new ArrayList<>();
			this.whereParams.addAll(Arrays.asList(whereParams));
		}
		return this;
	}
	//要查询的字段
	//.field(new String[]{"id", "name"}) or .field("id, name")
	@SuppressWarnings("unchecked")
	public DB field(Object field) {
		String fields = this.field;
		if (field instanceof String[]) {
			String[] f = new String[((String[]) field).length];
			for (int i = 0; i < ((String[]) field).length; i++) {
				String _field = ((String[]) field)[i];
				f[i] = _field.contains(".") ? _field : "`" + _field + "`";
			}
			fields += (fields.length() > 0 ? ", " : "") + StringUtils.join(f, ", ");
		} else if (field instanceof Map) { //指定别名
			Map<String, String> entry = (Map<String, String>) field;
			String[] items = new String[entry.size()];
			int i = 0;
			for (Map.Entry<String, String> item : entry.entrySet()) {
				String _field = item.getKey();
				items[i] = (_field.contains(".") ? _field : "`" + _field + "`") + (item.getValue().length() > 0 ? " as " + item.getValue() : "");
				i++;
			}
			fields += (fields.length() > 0 ? ", " : "") + StringUtils.join(items, ", ");
		} else if ((field instanceof String) && ((String)field).length() > 0){
			if (((String)field).trim().matches("^\\w+(\\|\\w+)+$")) { //排除不需要的字段
				try {
					List<String> fieldArray = new ArrayList<>();
					String[] items = ((String)field).split("\\|");
					String sql = DB.replaceTable("SHOW COLUMNS FROM " + this.table);
					if (conn == null) DB.init(0);
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
				fields += (fields.length() > 0 ? ", " : "") + ((String)field).trim();
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
	public DB sort(String order) {
		return order(order);
	}
	public DB order(String order) {
		this.order = order.length() > 0 ? " ORDER BY " + order : "";
		return this;
	}
	//按字段排序, 如: ORDER BY FIELD(`id`, 1, 9, 8, 4)
	public DB sortField(String field, String value) {
		return orderField(field, value);
	}
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
	public <T> T count() {
		return count("COUNT(*)");
	}
	public <T> T count(String field) {
		return count(field, Integer.class);
	}
	@SuppressWarnings("unchecked")
	public <T> T count(String field, Class<? extends Number> type) {
		DataMap ret = this.field(field).find();
		if (ret == null) {
			if (type == Integer.class) return (T) Integer.valueOf("0");
			else if (type == Long.class) return (T) Long.valueOf("0");
			else if (type == Float.class) return (T) Float.valueOf("0");
			else if (type == Double.class) return (T) Double.valueOf("0");
			else if (type == BigDecimal.class) return (T) new BigDecimal("0");
			return null;
		}
		T res = (T) ret.getOne();
		if (res.getClass() != type || res.getClass() == BigDecimal.class) {
			try {
				String t = type.toString().substring(type.toString().lastIndexOf(".") + 1);
				if (t.equals("Integer")) {
					t = "Int";
					if (res.getClass() == Double.class) res = (T) String.valueOf(res).split("\\.")[0];
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
	public <T> T sum(String field) {
		return sum(field, Integer.class);
	}
	public <T> T sum(String field, Class<? extends Number> type) {
		return count("SUM(" + field + ")", type);
	}
	//查询字段平均值
	public <T> T avg(String field) {
		return avg(field, Double.class);
	}
	public <T> T avg(String field, Class<? extends Number> type) {
		return count("AVG(" + field + ")", type);
	}
	//查询字段最小值
	public <T> T min(String field) {
		return min(field, Integer.class);
	}
	public <T> T min(String field, Class<? extends Number> type) {
		return count("MIN(" + field + ")", type);
	}
	//查询字段最大值
	public <T> T max(String field) {
		return max(field, Integer.class);
	}
	public <T> T max(String field, Class<? extends Number> type) {
		return count("MAX(" + field + ")", type);
	}
	//查询字段
	@SuppressWarnings("unchecked")
	public <T> T value(String field) {
		return (T) value(field, String.class);
	}
	@SuppressWarnings("unchecked")
	public <T> T value(String field, Class<T> type) {
		DataMap obj = this.field(field).find();
		if (obj == null) {
			if (type == Integer.class) return (T) Integer.valueOf("0");
			else if (type == Long.class) return (T) Long.valueOf("0");
			else if (type == Float.class) return (T) Float.valueOf("0");
			else if (type == Double.class) return (T) Double.valueOf("0");
			else if (type == BigDecimal.class) return (T) new BigDecimal("0");
			else if (type == String.class) return (T) "";
			return null;
		}
		if (field.contains(".")) field = field.substring(field.lastIndexOf(".") + 1);
		return (T) obj.get(field);
	}
	//查询某列值
	@SuppressWarnings("unchecked")
	public <T> T[] column(String field, Class<T> type) {
		try {
			DataList list = field(field).select();
			if (list == null) return null;
			Object[] columns = new Object[list.size()];
			for (int i = 0; i < list.size(); i++) {
				DataMap obj = list.get(i);
				columns[i] = obj.get(field);
			}
			return (T[]) columns;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	//查询单条记录
	public DataMap row(Object field) {
		return field(field).row();
	}
	public DataMap row() {
		return find();
	}
	public DataMap find(Object field) {
		return field(field).find();
	}
	public DataMap find() {
		DataList list = this.pagesize(1).select();
		return list == null ? null : list.get(0);
	}
	//查询
	public DataList select(Object field) {
		return field(field).select();
	}
	public DataList select() {
		String sql = _createSql();
		DataList res = new DataList();
		try {
			if (this.printSql) System.out.println(sql);
			if (this.cached != 0) {
				DataList r = _cacheSql(sql);
				if (r != null) return r;
			} else if (this.pagination && this.pagesize != 1) {
				_setPagination();
			}
			if (conn == null) DB.init(0);
			ps = conn.prepareStatement(sql);
			if (this.whereParams != null) {
				for (int i = 0; i < this.whereParams.size(); i++) { //绑定参数
					ps.setObject(i + 1, this.whereParams.get(i));
				}
			}
			ResultSet rs = ps.executeQuery();
			String[] columnNames = DB.getColumnNames(rs);
			while (rs.next()) {
				DataMap item = new DataMap();
				for (int i = 0; i < columnNames.length; i++) {
					String columnName = columnNames[i];
					Object value = Pattern.compile(".*(COUNT|SUM|AVG|MIN|MAX|DISTINCT)\\(.*", Pattern.CASE_INSENSITIVE).matcher(this.field).matches() ? rs.getObject(i + 1) : rs.getObject(columnName);
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
	//查询字段(使用对象)
	//String name = DB.share("user").where("id=1").value("name", User.class, String.class);
	@SuppressWarnings("unchecked")
	public <T, R> R value(String field, Class<T> clazz, Class<R> type) {
		if (field == null || field.length() == 0 || field.equals("*")) return count();
		try {
			T obj = field(field).find(clazz);
			if (obj == null) {
				if (type == Integer.class) return (R) Integer.valueOf("0");
				else if (type == Long.class) return (R) Long.valueOf("0");
				else if (type == Float.class) return (R) Float.valueOf("0");
				else if (type == Double.class) return (R) Double.valueOf("0");
				else if (type == BigDecimal.class) return (R) new BigDecimal("0");
				else if (type == String.class) return (R) "";
				return null;
			}
			if (field.contains(".")) field = field.substring(field.lastIndexOf(".") + 1);
			R res;
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
	//查询某列值(使用对象)
	@SuppressWarnings("unchecked")
	public <T, R> R[] column(String field, Class<T> clazz, Class<R> type) {
		try {
			List<T> list = field(field).select(clazz);
			if (list == null) return null;
			Object[] columns = new Object[list.size()];
			for (int i = 0; i < list.size(); i++) {
				T obj = list.get(i);
				String getterName = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
				Method getter = clazz.getMethod(getterName);
				columns[i] = getter.invoke(obj);
			}
			return (R[]) columns;
		} catch (Exception e) {
			e.printStackTrace();
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
			if (conn == null) DB.init(0);
			ps = conn.prepareStatement(sql);
			if (this.whereParams != null) {
				for (int i = 0; i < this.whereParams.size(); i++) { //绑定参数
					ps.setObject(i + 1, this.whereParams.get(i));
				}
			}
			ResultSet rs = ps.executeQuery();
			String[] columnNames = DB.getColumnNames(rs);
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
			if (conn == null) DB.init(0);
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
	public static String[] getColumnNames(ResultSet rs) {
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
	public static boolean isExistColumn(ResultSet rs, String columnName) {
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
		String field = this.field;
		if (field.length() == 0) {
			if (this.distinct.length() == 0) field = "*";
			else field = this.distinct;
		} else if (this.distinct.length() > 0) {
			if (!field.trim().equals("*")) field = this.distinct + ", " + field;
			else field = this.distinct;
		}
		StringBuilder sql = new StringBuilder("SELECT ").append(field).append(" FROM ").append(this.table);
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
	private DataList _cacheSql(String sql) {
		Redis redis = new Redis();
		boolean hasRedis = redis.ping();
		if (hasRedis) {
			if (redis.hasKey(sql)) {
				JSONArray array = JSONObject.parseArray((String) redis.get(sql));
				DataList list = new DataList();
				for (Object item : array) list.add(new DataMap(item));
				return list;
			}
		}
		String cachePath = rootPath + runtimeDir + "/" + cacheDir;
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
					DataList list = new DataList();
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
	private void _cacheSql(String sql, Object res) {
		Redis redis = new Redis();
		boolean hasRedis = redis.ping();
		if (hasRedis) {
			if (res instanceof DataList) {
				List<Map<String, Object>> list = new ArrayList<>();
				for (DataMap map : ((DataList) res).list) list.add(map.data);
				redis.set(sql, JSON.toJSONString(list), this.cached);
			} else {
				redis.set(sql, JSON.toJSONString(res), this.cached);
			}
			return;
		}
		String cachePath = rootPath + runtimeDir + "/" + cacheDir;
		File paths = new File(cachePath);
		if (!paths.exists()) {
			if (!paths.mkdirs()) throw new IllegalArgumentException("FILE PATH CREATE FAIL:\n" + cachePath);
		}
		File file = new File(cachePath + "/" + _md5(sql));
		try {
			if (res instanceof DataList) {
				List<Map<String, Object>> list = new ArrayList<>();
				for (DataMap map : ((DataList) res).list) list.add(map.data);
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
	private <T> List<T> _cacheSql(String sql, Class<T> clazz) {
		Redis redis = new Redis();
		boolean hasRedis = redis.ping();
		if (hasRedis) {
			if (redis.hasKey(sql)) {
				return JSONObject.parseArray((String) redis.get(sql), clazz);
			}
		}
		String cachePath = rootPath + runtimeDir + "/" + cacheDir;
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
	//清除两端字符串
	private String _trim(String str) {
		return str.replaceAll("(^,|,$)", "");
	}
	//插入记录, 失败返回-1
	//int row = DB.share("user").insert(new String[]{"name", "age"}, name, age);
	public int insert(String data, Object...dataParams) {
		List<String> map = new ArrayList<>();
		String[] fields = data.split(",");
		for (String field : fields) map.add(field.trim());
		return insert(map, dataParams);
	}
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
		int row;
		try {
			StringBuilder sql = new StringBuilder("INSERT INTO " + this.table + " (");
			for (String d : data) sql.append(d).append(", ");
			sql = new StringBuilder(sql.toString().replaceAll("(^, |, $)", "")).append(") VALUES(");
			for (String ignored : data) sql.append("?, ");
			sql = new StringBuilder(sql.toString().replaceAll("(^, |, $)", "")).append(")");
			String sq = DB.replaceTable(sql.toString());
			if (this.printSql) System.out.println(sq);
			if (conn == null) DB.init(1);
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
			row = -1;
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
	public int incr(String field) {
		return incr(field, 1);
	}
	public int incr(String field, int step) {
		return setInc(field, step);
	}
	public int setInc(String field) {
		return setInc(field, 1);
	}
	public int setInc(String field, int step) {
		String value = String.valueOf(step);
		if (step > 0) value = "+" + value;
		return setField(field, value);
	}
	//字段递减
	public int decr(String field) {
		return decr(field, 1);
	}
	public int decr(String field, int step) {
		return setDec(field, step);
	}
	public int setDec(String field) {
		return setDec(field, 1);
	}
	public int setDec(String field, int step) {
		return setInc(field, -step);
	}
	//更新记录, 失败返回-1
	//int row = DB.share("user").where("id=?", id).update(new String[]{"name", "age"}, name, age);
	public int update(String data, Object...dataParams) {
		List<String> map = new ArrayList<>();
		String[] fields = data.split(",");
		for (String field : fields) map.add(field.trim());
		return update(map, dataParams);
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
		int row;
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
			if (this.pagesize > 0) sql.append(" LIMIT ").append(this.pagesize);
			String sq = DB.replaceTable(sql.toString());
			if (this.printSql) System.out.println(sq);
			if (conn == null) DB.init(1);
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
			row = -1;
		} finally {
			DB.close();
		}
		return row;
	}
	//删除记录, 失败返回-1
	//int row = DB.share("user").where("id=?", id).delete();
	public int delete() {
		return delete(null);
	}
	public int delete(Object where, Object...whereParams) {
		if (where != null) this.where(where, whereParams);
		int row;
		try {
			StringBuilder sql = new StringBuilder("DELETE FROM " + this.table);
			if (this.where.length() > 0) sql.append(" WHERE ").append(this.where);
			String sq = DB.replaceTable(sql.toString());
			if (this.printSql) System.out.println(sq);
			if (conn == null) DB.init(1);
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
			row = -1;
		} finally {
			DB.close();
		}
		return row;
	}
	//原生查询
	public static DataList query(String sql, Object...dataParams) {
		DataList res = new DataList();
		try {
			sql = DB.replaceTable(sql);
			if (conn == null) DB.init(0);
			ps =  conn.prepareStatement(sql);
			for (int i = 0; i < dataParams.length; i++) {
				ps.setObject(i + 1, dataParams[i]);
			}
			ResultSet rs = ps.executeQuery();
			String[] columnNames = DB.getColumnNames(rs);
			while (rs.next()) {
				DataMap item = new DataMap();
				for (String columnName : columnNames) {
					Object value = rs.getObject(columnName);
					if (value != null && value.getClass().equals(BigDecimal.class)) value = ((BigDecimal)value).doubleValue();
					item.put(columnName, value);
				}
				res.add(item);
			}
		} catch (SQLException e) {
			System.out.println("SQL数据库QUERY查询异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		if (res.size() > 0) return res;
		return null;
	}
	//原生执行
	public static int execute(String sql, Object...dataParams) {
		int row = 0;
		try {
			sql = DB.replaceTable(sql);
			if (conn == null) DB.init(1);
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
		if (sql.matches("^\\w+(\\s+\\w+)?$")) sql = prefix + sql.replace(prefix, "");
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
			String sql = "SHOW COLUMNS FROM " + DB.replaceTable(table);
			if (conn == null) DB.init(0);
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
			System.out.println("SQL数据库获取指定列类型异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		return type;
	}
	//是否存在表
	public boolean tableExist(String table) {
		//ALTER TABLE table ENGINE=InnoDB //修改数据表引擎为InnoDB
		String sql;
		boolean has_table = false;
		if (dbType == 1) {
			sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='"+DB.replaceTable(table)+"'";
		} else {
			sql = "SHOW TABLES LIKE '"+DB.replaceTable(table)+"'";
		}
		try {
			if (conn == null) DB.init(0);
			ps =  conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				if (dbType == 1) {
					if (rs.getInt(1) > 0) has_table = true;
				} else {
					has_table = true;
				}
			}
			return has_table;
		} catch (Exception e) {
			System.out.println("SQL数据库查询是否存在表异常");
			e.printStackTrace();
			return false;
		} finally {
			DB.close();
		}
	}
	//创建数据表,可创建sqlite3
	/*DB.share("~db").tableCreate(new LinkedHashMap<String, Object>(){{
		put("member", new LinkedHashMap<String, Object>(){{
			put("table_engine", "InnoDB");
			put("table_auto_increment", 10);
			put("table_comment", "表注释");
			put("id", new LinkedHashMap<String, String>(){{put("type", "key");}});
			put("name", new LinkedHashMap<String, String>(){{put("type", "varchar(255)");put("comment", "名称");put("charset", "utf8mb4");}});
			put("price", new LinkedHashMap<String, String>(){{put("type", "decimal(10,2)");put("default", "0.00");}});
			put("content", new LinkedHashMap<String, String>(){{put("type", "text");}});
			put("clicks", new LinkedHashMap<String, String>(){{put("type", "int");put("index", "clicks");}});
		}});
	}});*/
	public void tableCreate(Object tables) {
		tableCreate(tables, false);
	}
	@SuppressWarnings("unchecked")
	public void tableCreate(Object tables, boolean re_create) {
		String sql = "";
		if (!(tables instanceof String) && !(tables instanceof Map)) throw new IllegalArgumentException("tableCreate parament 1 must be String or Map<String, Map<String, Object>>");
		if (tables instanceof String) sql = (String) tables;
		else {
			Map<String, Object> infos = (Map<String, Object>) tables;
			for (String table_name : infos.keySet()) {
				Object table_info = infos.get(table_name);
				if (!(table_info instanceof Map)) throw new IllegalArgumentException("tableCreate parament 1 must be String or Map<String, Map<String, Object>>");
				if (!re_create && tableExist(table_name)) continue;
				String key_field = "";
				StringBuilder field_sql = new StringBuilder();
				List<String[]> index = new ArrayList<>(); //索引
				tableRemove(table_name);
				Map<String, Object> tableInfo = (Map<String, Object>) table_info;
				sql += "CREATE TABLE `"+table_name+"` (\n";
				for (String field_name : tableInfo.keySet()) {
					Object field_info = tableInfo.get(field_name);
					if (Arrays.asList(new String[]{"table_engine", "table_auto_increment", "table_comment"}).contains(field_name)) continue;
					field_sql.append("`").append(field_name).append("`");
					Map<String, String> fieldInfo = (Map<String, String>) field_info;
					if (fieldInfo.get("type") != null) {
						if (fieldInfo.get("type").equals("key")) {
							key_field = field_name;
							field_sql.append(dbType == 1 ? " integer NOT NULL PRIMARY KEY AUTOINCREMENT" : " int(11) NOT NULL AUTO_INCREMENT");
						}
						else if (dbType == 1 && fieldInfo.get("type").contains("varchar")) {
							field_sql.append(" text");
						}
						else if (dbType == 1 && fieldInfo.get("type").contains("int")) {
							field_sql.append(" integer");
						}
						else if (dbType == 1 && fieldInfo.get("type").contains("decimal")) {
							field_sql.append(" numeric");
						}
						else field_sql.append(" ").append(fieldInfo.get("type"));
					} else {
						field_sql.append(dbType == 1 ? " text" : " varchar(255)");
					}
					if (dbType != 1 && fieldInfo.get("charset") != null) field_sql.append(" CHARACTER SET ").append(fieldInfo.get("charset"));
					if (fieldInfo.get("default") != null) {
						field_sql.append(" DEFAULT '").append(fieldInfo.get("default")).append("'");
					} else if (fieldInfo.get("type") != null && (fieldInfo.get("type").contains("int") || fieldInfo.get("type").contains("decimal"))) {
						field_sql.append(fieldInfo.get("type").contains("decimal") ? " DEFAULT '0.00'" : " DEFAULT '0'");
					} else if (fieldInfo.get("type") != null && fieldInfo.get("type").contains("varchar")) {
						field_sql.append(" DEFAULT NULL");
					}
					if (dbType != 1 && fieldInfo.get("index") != null) index.add(new String[]{fieldInfo.get("index"), field_name});
					if (dbType != 1 && fieldInfo.get("comment") != null) field_sql.append(" COMMENT '").append(fieldInfo.get("comment").replace("'", "\\'")).append("'");
					field_sql.append(",\n");
				}
				if (dbType != 1 && key_field.length() > 0) field_sql.append("PRIMARY KEY (`").append(key_field).append("`)");
				field_sql = new StringBuilder(_trim(field_sql.toString().trim()));
				if (index.size() > 0) {
					for (String[] i : index) field_sql.append(",\n" + "KEY `").append(i[0]).append("` (`").append(i[1]).append("`)");
				}
				sql += _trim(field_sql.toString().trim()) + "\n";
				sql += ")";
				if (dbType != 1) {
					String engine = tableInfo.get("table_engine") != null ? (String) tableInfo.get("table_engine") : "InnoDB";
					sql += " ENGINE=" + engine;
					if (tableInfo.get("table_auto_increment") != null) sql += " AUTO_INCREMENT=" + tableInfo.get("table_auto_increment");
					sql += " DEFAULT CHARSET=utf8";
					if (tableInfo.get("table_comment") != null) sql += " COMMENT='" + ((String) tableInfo.get("table_comment")).replace("'", "\\'") + "'";
				}
				sql += ";";
				sql = DB.replaceTable(sql);
			}
		}
		if (sql.length() > 0) DB.execute(sql);
	}
	//删除表, DB.share().tableRemove("table");
	public void tableRemove(String table) {
		DB.execute("DROP TABLE IF EXISTS `" + table + "`");
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
	//创建指定表Map
	public static Map<String, Object> createInstanceMap(String table) {
		Map<String, Object> map = new HashMap<>();
		try {
			String sql = "SHOW COLUMNS FROM " + DB.replaceTable(table);
			if (conn == null) DB.init(0);
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
			System.out.println("SQL数据库创建指定表Map异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
		return map;
	}
	public static DataMap createInstanceDataMap(String table) {
		Map<String, Object> data = DB.createInstanceMap(table);
		return new DataMap(data);
	}
	//生成实例class文件
	//DB.createInstanceFile("member", "com.laokema.javaweb.model.index");
	public static void createInstanceFile(String table, String packageName) {
		try {
			table = DB.replaceTable(table);
			String _table = table.replace(prefix, "");
			String sql = "SHOW COLUMNS FROM " + table;
			if (conn == null) DB.init(0);
			ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			String clazz = Character.toUpperCase(_table.charAt(0)) + _table.substring(1);
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
			FileWriter writer = new FileWriter(rootPath + "/" + clazz + ".java");
			writer.write(content.toString());
			writer.close();
		} catch (Exception e) {
			System.out.println("SQL数据库生成实例class文件异常");
			e.printStackTrace();
		} finally {
			DB.close();
		}
	}
	/*
	多个update操作共同协同工作时使用事务
	DB.startTransaction(); //开启事务
	if (DB.update(xxx) == -1) {
		DB.rollback();
	    return false; //失败，返回
	}
	if (DB.update(xxx) == -1) {
		DB.rollback();
	    return false; //失败，返回
	}
	DB.commit(); //成功，提交事务
	*/
	//开启事务
	public static void startTransaction() {
		try {
			if (conn == null) DB.init(1);
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			System.out.println("SQL数据库事务关闭自动提交异常");
			e.printStackTrace();
		}
	}
	//回滚事务
	public static void rollback() {
		try {
			conn.rollback();
		} catch (SQLException e) {
			System.out.println("SQL数据库回滚事务异常");
			e.printStackTrace();
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				System.out.println("SQL数据库事务开启自动提交异常");
				e.printStackTrace();
			}
			DB.close();
		}
	}
	//提交事务
	public static void commit() {
		try {
			conn.commit();
		} catch (SQLException e) {
			System.out.println("SQL数据库提交事务异常");
			e.printStackTrace();
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				System.out.println("SQL数据库事务开启自动提交异常");
				e.printStackTrace();
			}
			DB.close();
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

	//数据集实例
	public static class DataList implements Iterable<DataMap> {
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

	//数据行实例
	public static class DataMap {
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
				if (list.size() == 0) return null;
				return Collections.singletonList(list);
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
				if (data.keySet().size() == 0) return null;
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
			return false;
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
	}
}
