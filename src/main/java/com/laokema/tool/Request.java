//Developed by @mario 1.0.20220113
package com.laokema.tool;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

public class Request {
	private RequestWrapper request;
	private HttpServletResponse response;
	private Map<String, String[]> params;

	public Request(HttpServletRequest request, HttpServletResponse response) {
		init(request, response);
	}
	public void init(HttpServletRequest request, HttpServletResponse response) {
		try {
			this.request = new RequestWrapper(request);
			this.request.setCharacterEncoding("utf-8");
			this.response = response;
		} catch (Exception e) {
			e.printStackTrace();
		}
		/*//
		//输出二进制流
		try {
			String result = IOUtils.toString(this.request.getInputStream(), StandardCharsets.UTF_8);
			System.out.println(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
		//*/
	}

	public Object getAttribute(String key) {
		return this.request.getAttribute(key);
	}
	public void setAttribute(String key, Object value) {
		this.request.setAttribute(key, value);
	}
	public void removeAttribute(String key) {
		this.request.removeAttribute(key);
	}

	public String get(String key) {
		return get(key, "");
	}
	public String get(String key, String defaultValue) {
		Map<String, String[]> params = getParams();
		String[] values = params.get(key);
		if (values == null || values.length == 0 || (values.length == 1 && values[0].length() == 0)) return defaultValue;
		return values[0];
	}
	public int get(String key, int defaultValue) {
		Map<String, String[]> params = getParams();
		String[] values = params.get(key);
		if (values == null || values.length == 0 || (values.length == 1 && values[0].length() == 0)) return defaultValue;
		return Integer.parseInt(values[0]);
	}
	public float get(String key, float defaultValue) {
		Map<String, String[]> params = getParams();
		String[] values = params.get(key);
		if (values == null || values.length == 0 || (values.length == 1 && values[0].length() == 0)) return defaultValue;
		return Float.parseFloat(values[0]);
	}
	public String[] get(String key, boolean isMultiple) {
		Map<String, String[]> params = getParams();
		return params.get(key);
	}

	public String session(String key) {
		return session(key, "");
	}
	public String session(String key, String defaultValue) {
		Object value = this.request.getSession().getAttribute(key);
		if (value == null) return defaultValue;
		if (!(value instanceof String) || ((String)value).length() == 0) return defaultValue;
		return (String) value;
	}
	public int session(String key, int defaultValue) {
		Object value = this.request.getSession().getAttribute(key);
		if (value == null) return defaultValue;
		if (!(value instanceof Integer)) return defaultValue;
		return Integer.parseInt(String.valueOf(value));
	}
	public float session(String key, float defaultValue) {
		Object value = this.request.getSession().getAttribute(key);
		if (value == null) return defaultValue;
		if (!(value instanceof Float)) return defaultValue;
		return Float.parseFloat(String.valueOf(value));
	}
	public String[] session(String key, boolean isMultiple) {
		Object value = this.request.getSession().getAttribute(key);
		if (!(value instanceof String[])) return null;
		return (String[]) value;
	}
	@SuppressWarnings("unchecked")
	public <T> T session(String key, Class<T> clazz) {
		Object value = this.request.getSession().getAttribute(key);
		if (value == null) return null;
		if (value instanceof Map) value = mapToInstance((Map<String, Object>) value, clazz);
		if (value != null && value.getClass() != clazz) return null;
		return (T) value;
	}

	public String file(String key) {
		return file(key, "");
	}
	public String file(String key, String dir) {
		return file(key, dir, "jpg,png,gif,bmp");
	}
	public String file(String key, String dir, String fileType) {
		Map<String, Object> files = file(dir, fileType, false);
		if (files == null || files.keySet().size() == 0) return "";
		return (String) files.get(key);
	}
	public Map<String, Object> file(String dir, String fileType, boolean returnDetail) {
		Upload upload = new Upload(this.request, this.response);
		return upload.file(dir, fileType, returnDetail);
	}

	public <T> T mapToInstance(Map<String, Object> map, Class<T> clazz) {
		try {
			T obj = clazz.getConstructor().newInstance();
			for (String key : map.keySet()) {
				Object value = map.get(key);
				try {
					Field f = obj.getClass().getDeclaredField(key);
					String setterName = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
					Method setter = clazz.getMethod(setterName, f.getType());
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
							System.out.println("Common.mapToInstance");
							System.out.println(clazz.getName()+"     "+setterName+"      "+f.getName()+" = "+f.getType().getName()+"        data = "+value.getClass().getName());
						}
					}
					if (value != null) setter.invoke(obj, value);
				} catch (NoSuchFieldException e) {
					String packageName = Request.class.getPackage().getName();
					if (clazz.getSuperclass().getName().equals(packageName.substring(0, packageName.lastIndexOf(".") + 1) + "model.BaseModel")) {
						Method getter = clazz.getMethod("set", String.class, Object.class);
						getter.invoke(obj, key, value);
					}
				}
			}
			return obj;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public Map<String, String[]> getParams() {
		if (this.params != null) return this.params;
		Map<String, String[]> params = new HashMap<>();
		if (ServletFileUpload.isMultipartContent(this.request)) {
			//Multipart form
			FileItemFactory factory = new DiskFileItemFactory();
			ServletFileUpload upload = new ServletFileUpload(factory);
			try {
				List<FileItem> fileItems = upload.parseRequest(this.request);
				for (FileItem item : fileItems) {
					if (item.isFormField()) {
						String name = item.getFieldName();
						String value = item.getString("utf-8");
						params.put(name, new String[]{value.trim()});
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			//Simple form
			Map<String, String[]> map = this.request.getParameterMap();
			if (map != null) params.putAll(this.request.getParameterMap());
		}
		this.params = params;
		return params;
	}

	//自定义HttpServletRequestWrapper, 让request输入流可重复使用多次
	public static class RequestWrapper extends HttpServletRequestWrapper {
		private static final String UTF_8 = "UTF-8";
		private final Map<String, String[]> paramsMap;
		private final byte[] body; // 报文

		public RequestWrapper(HttpServletRequest request) throws IOException {
			super(request);
			body = readBytes(request.getInputStream());

			// 首先从POST中获取数据
			if ("POST".equalsIgnoreCase(request.getMethod())) {
				paramsMap = getParamMapFromPost(this);
			} else {
				paramsMap = getParamMapFromGet(this);
			}
		}

		@Override
		public Map<String, String[]> getParameterMap() {
			return paramsMap;
		}

		@Override
		public String getParameter(String name) {// 重写getParameter，代表参数从当前类中的map获取
			String[] values = paramsMap.get(name);
			if (values == null || values.length == 0) {
				return null;
			}
			return values[0];
		}

		@Override
		public String[] getParameterValues(String name) {// 同上
			return paramsMap.get(name);
		}

		@Override
		public Enumeration<String> getParameterNames() {
			return Collections.enumeration(paramsMap.keySet());
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(getInputStream()));
		}

		@Override
		public ServletInputStream getInputStream() {
			final ByteArrayInputStream bais = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public boolean isFinished() {
					return false;
				}
				@Override
				public boolean isReady() {
					return false;
				}
				@Override
				public void setReadListener(ReadListener readListener) {

				}
				@Override
				public int read() {
					return bais.read();
				}
			};
		}

		private Map<String, String[]> getParamMapFromGet(HttpServletRequest request) {
			return parseQueryString(request.getQueryString());
		}

		private HashMap<String, String[]> getParamMapFromPost(HttpServletRequest request) {
			String body = StringUtils.EMPTY;
			try {
				body = getRequestBody(request.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
			HashMap<String, String[]> result = new HashMap<>();
			if (0 == body.length()) {
				return result;
			}
			return parseQueryString(body);
		}

		private String getRequestBody(InputStream stream) throws IOException {
			String line;
			StringBuilder body = new StringBuilder();
			int counter = 0;
			// 读取POST提交的数据内容
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			while ((line = reader.readLine()) != null) {
				if (counter > 0) {
					body.append("rn");
				}
				body.append(line);
				counter++;
			}
			reader.close();
			return body.toString();
		}

		public HashMap<String, String[]> parseQueryString(String s) {
			String[] valArray;
			if (s == null) {
				//throw new IllegalArgumentException();
				return null;
			}
			HashMap<String, String[]> ht = new HashMap<>();
			StringTokenizer st = new StringTokenizer(s, "&");
			while (st.hasMoreTokens()) {
				String pair = st.nextToken();
				int pos = pair.indexOf('=');
				if (pos == -1) {
					continue;
				}
				String key = pair.substring(0, pos);
				String val = pair.substring(pos + 1);
				if (ht.containsKey(key)) {
					String[] oldVals = ht.get(key);
					valArray = new String[oldVals.length + 1];
					for (int i = 0; i < oldVals.length; i++) {
						valArray[i] = oldVals[i];
					}
					valArray[oldVals.length] = decodeValue(val);
				} else {
					valArray = new String[1];
					valArray[0] = decodeValue(val);
				}
				ht.put(key, valArray);
			}
			return ht;
		}

		private static byte[] readBytes(InputStream in) throws IOException {
			BufferedInputStream bufin = new BufferedInputStream(in);
			final int buffSize = 1024;
			ByteArrayOutputStream out = new ByteArrayOutputStream(buffSize);
			byte[] temp = new byte[buffSize];
			int size;
			while ((size = bufin.read(temp)) != -1) {
				out.write(temp, 0, size);
			}
			out.flush();
			byte[] content = out.toByteArray();
			bufin.close();
			out.close();
			return content;
		}

		//自定义解码函数
		private String decodeValue(String value) {
			try {
				if (value.contains("%u")) {
					return URLEncoder.encode(value, UTF_8);
				} else {
					return URLDecoder.decode(value.replaceAll("%(?![0-9a-fA-F]{2})", "%25"), UTF_8);
				}
			} catch (UnsupportedEncodingException e) {
				return StringUtils.EMPTY;// 非UTF-8编码
			}
		}
	}
}
