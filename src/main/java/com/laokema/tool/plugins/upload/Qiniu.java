//Developed by @mario 1.0.20220127
package com.laokema.tool.plugins.upload;

import com.alibaba.fastjson.*;
import com.j256.simplemagic.*;
import com.laokema.tool.*;
import com.qiniu.http.Response;
import com.qiniu.storage.*;
import com.qiniu.storage.model.FetchRet;
import com.qiniu.util.*;
import java.io.*;
import java.util.*;

public class Qiniu {
	public String accessKey;
	public String secretKey;
	public String bucket;
	public String domain;
	public Region region;

	public Qiniu(String accessKey, String secretKey, String bucket, String domain) {
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.bucket = bucket;
		this.domain = domain;
		this.region = Region.huanan();
	}

	public void setRegion(String region) {
		switch (region) {
			case "huadong":this.region = Region.huadong();break; //华东
			case "huabei":this.region = Region.huabei();break; //华北
			case "beimei":this.region = Region.beimei();break; //北美
			case "xinjiapo":this.region = Region.xinjiapo();break; //东南亚
			default:this.region = Region.huanan(); //华南
		}
	}
	public void setRegion(int index) {
		switch (index) {
			case 0:this.region = Region.region0();break;
			case 1:this.region = Region.region1();break;
			case 3:this.region = Region.regionNa0();break;
			case 4:this.region = Region.regionAs0();break;
			default:this.region = Region.region2();
		}
	}

	public Map<String, Object> upload(String filepath, String dir, String name, String ext) {
		String filename;
		if (dir != null && dir.length() > 0) {
			dir = Common.trim(dir, "/");
			filename = dir + "/" + name + "." + ext;
		} else {
			filename = name + "." + ext;
		}
		Map<String, Object> ret = new HashMap<>();
		ret.put("file", "");
		ret.put("width", 0);
		ret.put("height", 0);
		ret.put("size", 0);
		Configuration cfg = new Configuration(this.region);
		UploadManager uploadManager = new UploadManager(cfg);
		Auth auth = Auth.create(this.accessKey, this.secretKey);
		if (filepath.startsWith("http:") || filepath.startsWith("https:")) { //网络资源
			try {
				BucketManager bucketManager = new BucketManager(auth, cfg);
				FetchRet fetchRet = bucketManager.fetch(filepath, bucket, filename);
				ret.put("file", Common.trim(this.domain, "/") + "/" + Common.trim(fetchRet.key, "/"));
				if (fetchRet.mimeType.startsWith("image/")) {
					int width, height;
					InputStream stream = new FileInputStream(filepath);
					Upload.ImageInfo src = new Upload.ImageInfo(stream);
					width = src.getWidth();
					height = src.getHeight();
					stream.close();
					ret.put("width", width);
					ret.put("height", height);
				}
				ret.put("size", fetchRet.fsize);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else { //本地文件
			StringMap putPolicy = new StringMap();
			putPolicy.put("returnBody", "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"size\":$(fsize)}");
			String upToken = auth.uploadToken(this.bucket, null, 3600, putPolicy);
			try {
				Response res = uploadManager.put(filepath, filename, upToken); //默认不指定filename的情况下，以文件内容的hash值作为文件名
				JSONObject result = JSON.parseObject(res.bodyString());
				ret.put("file", Common.trim(this.domain, "/") + "/" + Common.trim((String) result.get("key"), "/"));
				ContentInfo contentInfo = ContentInfoUtil.findExtensionMatch(filepath);
				String mimeType = contentInfo != null ? contentInfo.getMimeType() : null;
				if (mimeType != null && mimeType.startsWith("image/")) {
					int width, height;
					InputStream stream = new FileInputStream(filepath);
					Upload.ImageInfo src = new Upload.ImageInfo(stream);
					width = src.getWidth();
					height = src.getHeight();
					stream.close();
					ret.put("width", width);
					ret.put("height", height);
				}
				ret.put("size", result.get("size"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ret;
	}

	public boolean delete(String url) {
		Configuration cfg = new Configuration(this.region);
		Auth auth = Auth.create(this.accessKey, this.secretKey);
		BucketManager bucketManager = new BucketManager(auth, cfg);
		try {
			if (!url.startsWith(this.domain)) return false;
			url = url.replaceAll(this.domain + "/", "");
			bucketManager.delete(this.bucket, url);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
}
