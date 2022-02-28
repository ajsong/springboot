//Developed by @mario 1.3.20220228
package com.laokema.tool;

import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class Upload {
	private Request.RequestWrapper request;
	private HttpServletResponse response;

	public Upload() {
		try {
			ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			HttpServletRequest req = Objects.requireNonNull(servletRequestAttributes).getRequest();
			HttpServletResponse res = Objects.requireNonNull(servletRequestAttributes).getResponse();
			this.request = new Request.RequestWrapper(req);
			this.request.setCharacterEncoding("utf-8");
			this.response = res;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Map<String, Object> file() {
		return file("", "jpg,png,gif,bmp");
	}

	public Map<String, Object> file(String dir, String fileType) {
		return file(dir, fileType, false);
	}

	public Map<String, Object> file(String dir, String fileType, boolean returnDetail) {
		return file(dir, fileType, null, returnDetail);
	}

	public Map<String, Object> file(String dir, String fileType, Map<String, Object> thirdParty, boolean returnDetail) {
		String[] fileTypes = fileType.split(",");
		Map<String, Object> res = new HashMap<>();
		int maxMemSize = 0;
		long maxFileSize = 0;
		//获取web.xml配置的路径
		//ServletContext context = this.pageContext.getServletContext();
		//String uploadDir = context.getInitParameter("uploadpath");
		String uploadDir = "";
		try {
			Properties properties = new Properties();
			properties.load(Upload.class.getClassLoader().getResourceAsStream("application.properties"));
			uploadDir = properties.getProperty("upload.path") + (dir.length() > 0 ? "/" + Common.trim(dir, "/") : "");
			maxMemSize = Integer.parseInt(properties.getProperty("upload.memorysize")) * 1024;
			maxFileSize = Long.parseLong(properties.getProperty("upload.filesize")) * 1024;
		} catch (Exception e) {
			System.out.println("获取配置文件失败：" + e.getMessage());
			e.printStackTrace();
		}
		//获取文件上传目录路径，在项目部署路径下的uploads目录里，若想让浏览器不能直接访问到图片，可以放在WEB-INF下
		ApplicationHome ah = new ApplicationHome(Common.class);
		String rootPath = ah.getSource().getParentFile().getPath();
		if (!rootPath.endsWith("/")) rootPath += "/";
		String filePath = rootPath + uploadDir.replaceFirst("/", "");
		File path = new File(filePath);
		if (!path.exists()) {
			if (!path.mkdirs()) throw new IllegalArgumentException("FILE PATH CREATE FAIL:\n" + filePath);
		}
		boolean isMultipart = ServletFileUpload.isMultipartContent(this.request);
		if (isMultipart) {
			DiskFileItemFactory factory = new DiskFileItemFactory(); //创建工厂类
			factory.setSizeThreshold(maxMemSize); //设置内存缓冲区的大小
			//factory.setRepository(new File("/tmp")); //指定临时文件目录，如果单个文件的大小超过内存缓冲区，该文件将会临时缓存在此目录下
			//如果没有指定临时文件目录，默认采用系统默认的临时文件路径，可以通过System.getProperty("java.io.tmpdir")获取，Tomcat系统默认临时目录为"<tomcat安装目录>/temp/"
			ServletFileUpload upload = new ServletFileUpload(factory); //创建解析器
			upload.setFileSizeMax(maxFileSize); //上传文件的最大限制
			upload.setSizeMax(maxFileSize * 10); //设置所有文件，也就是请求大小限制
			upload.setHeaderEncoding("utf-8"); //处理中文乱码
			try {
				List<FileItem> fileItems = upload.parseRequest(this.request);
				for (FileItem item: fileItems) {
					if (item.isFormField() || item.getName() == null || item.getName().length() == 0) continue;
					String fieldName = item.getFieldName(); //表单项的name的属性值
					String fileName = item.getName(); //文件字段的文件名，如果是普通字段，则返回null
					//String content = item.getString("utf-8"); //字段的内容。如果是普通字段，则是它的value值；如果是文件字段，则是文件内容
					//String type = item.getContentType(); //上传的文件类型，例如text/plain、image/png。如果是普通字段，则返回null
					long size = item.getSize(); //字段内容的大小，单位是字节

					String suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(); //获取文件后缀名
					if (suffix.equalsIgnoreCase("jpeg")) suffix = "jpg";
					if (fileTypes.length > 0 && !Arrays.asList(fileTypes).contains(suffix)) {
						String html = "<meta charset=\"UTF-8\"><script>alert('PLEASE SELECT THE FILE OF "+fileType+"\\nNOW IS "+suffix+"');history.back();</script>";
						PrintWriter out = this.response.getWriter();
						out.write(html);
						out.close();
						return null;
					}
					String name = Common.generate_sn();
					String filename = name + "." + suffix;
					String uploadFilePath = uploadDir + "/" + filename;
					File file = new File(filePath, filename);
					item.write(file);
					if (thirdParty != null) {
						Class<?> packageName = (Class<?>) thirdParty.get("package");
						String accessKey = (String) thirdParty.get("accessKey");
						String secretKey = (String) thirdParty.get("secretKey");
						String bucket = (String) thirdParty.get("bucket");
						String domain = (String) thirdParty.get("domain");
						Object sdk = Common.plugin(packageName, accessKey, secretKey, bucket, domain);
						Map<String, Object> ret = Common.getMethod(sdk, "upload", file.getPath(), uploadDir, name, suffix);
						if (ret == null) throw new IllegalArgumentException(packageName + " IS UPLOAD FAIL\n" + file.getPath() + "\n" + dir + ", " + name + ", " + suffix);
						if (!file.delete()) throw new IllegalArgumentException("FILE DELETE FAIL:\n" + file.getPath());
						uploadFilePath = (String) ret.get("file");
					}
					if (returnDetail) {
						Map<String, Object> items = new HashMap<>();
						items.put("file", uploadFilePath);
						items.put("name", fileName);
						items.put("type", suffix);
						items.put("size", size);
						if (fileTypes.length > 0 && Arrays.asList(fileTypes).contains(suffix)) {
							int width, height;
							InputStream stream = new FileInputStream(new File(filePath, filename));
							ImageInfo src = new ImageInfo(stream);
							width = src.getWidth();
							height = src.getHeight();
							stream.close();
							items.put("width", width);
							items.put("height", height);
						}
						res.put(fieldName, items);
					} else {
						res.put(fieldName, uploadFilePath);
					}
				}
			} catch(Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return res;
	}

	public static class ImageInfo {
		public int height;
		public int width;
		public String mimeType;
		public ImageInfo(File file) throws IOException {
			try (InputStream is = new FileInputStream(file)) {
				processStream(is);
			}
		}
		public ImageInfo(InputStream is) throws IOException {
			processStream(is);
		}
		public ImageInfo(byte[] bytes) throws IOException {
			try (InputStream is = new ByteArrayInputStream(bytes)) {
				processStream(is);
			}
		}
		@SuppressWarnings("all")
		private void processStream(InputStream is) throws IOException {
			int c1 = is.read();
			int c2 = is.read();
			int c3 = is.read();
			mimeType = null;
			width = height = -1;
			if (c1 == 'G' && c2 == 'I' && c3 == 'F') { //GIF
				is.skip(3);
				width = readInt(is, 2, false);
				height = readInt(is, 2, false);
				mimeType = "image/gif";
			} else if (c1 == 0xFF && c2 == 0xD8) { //JPG
				while (c3 == 255) {
					int marker = is.read();
					int len = readInt(is, 2, true);
					if (marker == 192 || marker == 193 || marker == 194) {
						is.skip(1);
						height = readInt(is, 2, true);
						width = readInt(is, 2, true);
						mimeType = "image/jpeg";
						break;
					}
					is.skip(len - 2);
					c3 = is.read();
				}
			} else if (c1 == 137 && c2 == 80 && c3 == 78) { //PNG
				is.skip(15);
				width = readInt(is, 2, true);
				is.skip(2);
				height = readInt(is, 2, true);
				mimeType = "image/png";
			} else if (c1 == 66 && c2 == 77) { //BMP
				is.skip(15);
				width = readInt(is, 2, false);
				is.skip(2);
				height = readInt(is, 2, false);
				mimeType = "image/bmp";
			} else if (c1 == 'R' && c2 == 'I' && c3 == 'F') { //WEBP
				byte[] bytes = new byte[27];
				is.read(bytes);
				width = ((int) bytes[24] & 0xff) << 8 | ((int) bytes[23] & 0xff);
				height = ((int) bytes[26] & 0xff) << 8 | ((int) bytes[25] & 0xff);
				mimeType = "image/webp";
			} else {
				int c4 = is.read();
				if ((c1 == 'M' && c2 == 'M' && c3 == 0 && c4 == 42) || (c1 == 'I' && c2 == 'I' && c3 == 42 && c4 == 0)) { //TIFF
					boolean bigEndian = c1 == 'M';
					int ifd = 0;
					int entries;
					ifd = readInt(is, 4, bigEndian);
					is.skip(ifd - 8);
					entries = readInt(is, 2, bigEndian);
					for (int i = 1; i <= entries; i++) {
						int tag = readInt(is, 2, bigEndian);
						int fieldType = readInt(is, 2, bigEndian);
						int valOffset;
						if ((fieldType == 3 || fieldType == 8)) {
							valOffset = readInt(is, 2, bigEndian);
							is.skip(2);
						} else {
							valOffset = readInt(is, 4, bigEndian);
						}
						if (tag == 256) {
							width = valOffset;
						} else if (tag == 257) {
							height = valOffset;
						}
						if (width != -1 && height != -1) {
							mimeType = "image/tiff";
							break;
						}
					}
				}
			}
			if (mimeType == null) {
				throw new IOException("Unsupported image type");
			}
		}
		private int readInt(InputStream is, int noOfBytes, boolean bigEndian) throws IOException {
			int ret = 0;
			int sv = bigEndian ? ((noOfBytes - 1) * 8) : 0;
			int cnt = bigEndian ? -8 : 8;
			for (int i = 0; i < noOfBytes; i++) {
				ret |= is.read() << sv;
				sv += cnt;
			}
			return ret;
		}
		public int getWidth() {
			return width;
		}
		public int getHeight() {
			return height;
		}
		public String getMimeType() {
			return mimeType;
		}
		@Override
		public String toString() {
			return "MIME Type : " + mimeType + "\t Width : " + width + "\t Height : " + height;
		}
	}
}
