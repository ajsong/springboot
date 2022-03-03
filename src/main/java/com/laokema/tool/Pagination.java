//Developed by @mario 1.2.20220217
package com.laokema.tool;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.regex.*;

/*
.pagination{text-align:right; margin-top:20px; font-size:12px;}
.num_records{color:#337ab7; font-size:12px;}
.nav_na{display:inline-block; padding:0 5px; margin:0 1px; text-align:center; min-width:24px; height:24px; line-height:24px; font-size:12px; color:#fff; background:#ccc; vertical-align:middle; border-radius:2px; cursor:default;}
a{display:inline-block; padding:0 5px; margin:0 1px; text-align:center; min-width:24px; height:24px; line-height:24px; font-size:12px; color:#fff; vertical-align:middle; border-radius:2px; text-decoration:none;}
.nav_page{background:#589ad3; -webkit-transition:all 0.3s ease-out; transition:all 0.3s ease-out;}
.nav_page:hover{background:#337ab7;}
.first_page, .back_page, .next_page, .last_page{background:#337ab7;}
.back_section, .next_section{padding:0; min-width:18px; background:url("data:image/svg+xml;charset=utf-8,%3Csvg%20viewBox%3D%220%200%201024%201024%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%3Cpath%20d%3D%22M646.31808%20515.54304A75.22304%2075.22304%200%200%201%20721.55136%20440.32a75.22304%2075.22304%200%200%201%2075.23328%2075.22304%2075.23328%2075.23328%200%200%201-75.23328%2075.23328%2075.23328%2075.23328%200%200%201-75.23328-75.23328z%20m-209.54112%200A75.22304%2075.22304%200%200%201%20512.01024%20440.32a75.2128%2075.2128%200%200%201%2075.22304%2075.22304%2075.22304%2075.22304%200%200%201-75.22304%2075.23328%2075.23328%2075.23328%200%200%201-75.23328-75.23328z%20m-209.54112%200A75.2128%2075.2128%200%200%201%20302.45888%20440.32a75.22304%2075.22304%200%200%201%2075.23328%2075.22304%2075.23328%2075.23328%200%200%201-75.23328%2075.23328%2075.22304%2075.22304%200%200%201-75.23328-75.23328z%22%20fill%3D%22%23999999%22%3E%3C%2Fpath%3E%3C%2Fsvg%3E") no-repeat center center; background-size:18px 18px;}
.back_section:hover, .next_section:hover{background-image:url("data:image/svg+xml;charset=utf-8,%3Csvg%20viewBox%3D%220%200%201024%201024%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%3Cpath%20d%3D%22M840.5%20750.059c14.617%2014.617%2014.617%2038.29%200%2052.90699999-14.616%2014.616-38.326%2014.616-52.943%201e-8l-264.751-264.513c-14.615-14.598-14.615-38.29%200-52.906l264.75-264.512c14.617-14.61599999%2038.327-14.616%2052.943%200%2014.617%2014.61599999%2014.617%2038.29%200%2052.906L602.22%20512%20840.5%20750.059z%20m-339.306%200c14.61599999%2014.617%2014.616%2038.29%200%2052.907-14.616%2014.616-38.327%2014.616-52.943%200L183.5%20538.453c-14.616-14.598-14.616-38.29%200-52.906L448.251%20221.035c14.616-14.616%2038.327-14.616%2052.943%200s14.616%2038.29%200%2052.906L262.914%20512l238.28%20238.059z%22%20fill%3D%22%23f59942%22%3E%3C%2Fpath%3E%3C%2Fsvg%3E");}
.next_section{-webkit-transform:rotateY(180deg); transform:rotateY(180deg);}
@media (max-width: 768px) {
.pagination{clear:both;}
.nav_ge{display:block; margin-top:5px;}
*/

//分页封装
//Pagination util = new Pagination(request, 数据查询总数[, 每页条数]);
public class Pagination {
	private String url; //当前页
	private String queryString; //QueryString
	private int BRSR; //偏移码
	private int records; //总记录数
	private int pageSize; //每页的条数
	private int currentPage; //当前页
	private int prevPage; //上一页
	private int nextPage; //下一页
	private int lastPage; //尾页
	private String firstString; //首页HTML
	private String prevString; //上一页HTML
	private String nextString; //下一页HTML
	private String lastString; //尾页HTML
	private String prevSetionString; //上一段HTML
	private String nextSetionString; //下一段HTML
	private String rewritePage; //重写分页链接,伪静态用,[P]将替换为页数

	private String pageMark; //分页参数名称
	private int sectionPages = 5; //每段页面数量
	private int numPages = 0; //总页数
	private int currentSetion = 0; //当前段数

	//构造器
	public Pagination(HttpServletRequest request, int records) {
		init(request, records, 10);
	}
	public Pagination(HttpServletRequest request, int records, int pageSize) {
		init(request, records, pageSize);
	}

	public void init(HttpServletRequest request, int records, int pageSize) {
		this.pageMark = "offset";
		this.url = request.getRequestURI();
		this.queryString = request.getQueryString();
		if (this.queryString != null && this.queryString.length() != 0) {
			this.queryString = Pattern.compile(pageMark+"=\\d+&?").matcher(this.queryString).replaceAll("");
		}
		this.queryString = (this.queryString == null || this.queryString.length() == 0) ? "" : "&" + this.queryString;
		String BRSR = request.getParameter(pageMark);
		this.BRSR = (BRSR != null && BRSR.length() > 0) ? Integer.parseInt(BRSR) : 0;
		this.records = records;
		this.pageSize = pageSize;
		this.rewritePage = "";
		initNumPages();
		initPrevPage();
		initNextPage();
		initLastPage();
		initPageHtml();
	}
	private void initNumPages() {
		numPages = (records - (records % pageSize)) / pageSize;
		if ( records % pageSize != 0 ) {
			numPages++;
		}
		// Calculate which page we are browsing
		currentPage = (BRSR - ( BRSR % pageSize )) / pageSize;
		if (currentPage < 0) {
			currentPage = 0;
		} else if (currentPage > numPages - 1) {
			currentPage = numPages - 1;
		}
		// Calculate which setion of sectionPages we are browsing
		currentSetion = (currentPage - ( currentPage % sectionPages )) / sectionPages;
		// addition; steve warwick --
		// if the num pages are less than the num links make browse links
		// eq the num pages - ensures consistent navigation output
		if (numPages <= sectionPages) {
			sectionPages = numPages;
		}
	}
	// 初始化上一页
	private void initPrevPage() {
		if (currentPage != 0) {
			prevPage = currentPage - 1;
		}else{
			prevPage = 1;
		}
	}
	// 初始化下一页
	private void initNextPage() {
		if (currentPage != lastPage - 1) {
			nextPage = currentPage + 1;
		}else{
			nextPage = lastPage;
		}
	}
	// 初始化尾页
	private void initLastPage() {
		if (records % pageSize == 0) {
			lastPage = records / pageSize;
		} else {
			lastPage = records / pageSize + 1;
		}
	}
	// 初始化首尾上下HTML
	private void initPageHtml() {
		firstString = "First";
		prevString = "<font style=\"font-size:10px;\"><</font>";
		nextString = "<font style=\"font-size:10px;\">></font>";
		lastString = "Last";
		prevSetionString = "";
		nextSetionString = "";
	}

	public Pagination isCn() {
		firstString = "首页";
		lastString = "尾页";
		return this;
	}
	public Pagination setFirstString(String html) {
		this.firstString = html;
		return this;
	}
	public Pagination setLastString(String html) {
		this.lastString = html;
		return this;
	}
	public Pagination setPrevString(String html) {
		this.prevString = html;
		return this;
	}
	public Pagination setNextString(String html) {
		this.nextString = html;
		return this;
	}
	public Pagination setPrevSetionString(String html) {
		this.prevSetionString = html;
		return this;
	}
	public Pagination setNextSetionString(String html) {
		this.nextSetionString = html;
		return this;
	}
	public Pagination setRewritePage(String rewritePage) {
		this.rewritePage = rewritePage;
		return this;
	}
	public Pagination setPageMark(String pageMark) {
		this.pageMark = pageMark;
		return this;
	}

	public int getFirstPage() {
		return 0;
	}
	public String getFirstPage(boolean returnHtml) {
		String html;
		if (currentPage != 0) {
			html = "<span class=\"nav_ge\"></span><a href=\"";
			if (rewritePage.length() == 0) {
				html += url + (queryString.length() > 0 ? "?" + queryString : "");
			} else {
				html += rewritePage.replaceAll("\\[P]", "0");
			}
			html += "\" class=\"first_page\">" + firstString + "</a>";
		} else {
			html = "<span class=\"nav_ge\"></span>";
		}
		return html;
	}
	public int getPrevPage() {
		return prevPage;
	}
	public String getPrevPage(boolean returnHtml) {
		String html;
		if (BRSR - pageSize > 0) {
			html = "<a href=\"";
			if (rewritePage.length() == 0) {
				html += url + "?" + pageMark + "=" + (BRSR - pageSize) + queryString;
			} else {
				html += rewritePage.replaceAll("\\[P]", String.valueOf(BRSR - pageSize));
			}
			html += "\" class=\"back_page\">" + prevString + "</a>";
		} else {
			html = "";
		}
		return html;
	}
	public int getNextPage() {
		return nextPage;
	}
	public String getNextPage(boolean returnHtml) {
		String html;
		if ( numPages >= sectionPages && (BRSR + pageSize) < records ) {
			html = "<a href=\"";
			if (rewritePage.length() == 0) {
				html += url + "?" + pageMark + "=" + (BRSR + pageSize) + queryString;
			} else {
				html += rewritePage.replaceAll("\\[P]", String.valueOf(BRSR + pageSize));
			}
			html += "\" class=\"next_page\">" + nextString + "</a>";
		} else {
			html = "";
		}
		return html;
	}
	public int getLastPage() {
		return lastPage;
	}
	public String getLastPage(boolean returnHtml) {
		String html;
		if ( (numPages >= sectionPages) && ((BRSR + pageSize) < records) ) {
			html = "<a href=\"";
			if (rewritePage.length() == 0) {
				html += url + "?" + pageMark + "=" + (numPages * pageSize - pageSize) + queryString;
			} else {
				html += rewritePage.replaceAll("\\[P]", String.valueOf(numPages * pageSize - pageSize));
			}
			html += "\" class=\"last_page\">" + lastString + "</a>";
		} else {
			html = "<span class=\"nav_ge\"></span>";
		}
		return html;
	}
	public int getPrevSetion() {
		return currentSetion * sectionPages * pageSize - sectionPages * pageSize;
	}
	public String getPrevSetion(boolean returnHtml) {
		String html;
		if ( currentSetion * sectionPages >= sectionPages ) {
			int _BRSR = getPrevSetion();
			html = "<a href=\"";
			if (rewritePage.length() == 0) {
				html += url + "?" + pageMark + "=" + _BRSR + queryString;
			} else {
				html += rewritePage.replaceAll("\\[P]", String.valueOf(_BRSR));
			}
			html += "\" class=\"back_section\">" + prevSetionString + "</a>";
		} else {
			html = "";
		}
		return html;
	}
	public int getNextSetion() {
		int _BRSR = BRSR + sectionPages * pageSize;
		if ( _BRSR > (numPages - 1) * pageSize ) {
			_BRSR = (numPages - 1) * pageSize;
		}
		return _BRSR;
	}
	public String getNextSetion(boolean returnHtml) {
		String html;
		if ( BRSR < (numPages - 1) * pageSize ) {
			int _BRSR = getNextSetion();
			html = "<a href=\"";
			if (rewritePage.length() == 0) {
				html += url + "?" + pageMark + "=" + _BRSR + queryString;
			} else {
				html += rewritePage.replaceAll("\\[P]", String.valueOf(_BRSR));
			}
			html += "\" class=\"next_section\">" + nextSetionString + "</a>";
		} else {
			html = "";
		}
		return html;
	}
	public String getNav() {
		StringBuilder html = new StringBuilder();
		if (records > pageSize) {
			for (int i = getPageStart(); i < getPageEnd(); i++ ) {
				if ( i * pageSize < records ) {
					if (i == currentPage) {
						html.append("<span class=\"nav_na\" data-limit=\"").append(pageSize).append("\">").append(i + 1).append("</span> ");
					} else {
						html.append("<span><a href=\"");
						if (rewritePage.length() == 0) {
							html.append(url);
							if (queryString.length() > 0 || i > 0) html.append("?");
							if (i > 0) {
								html.append(pageMark).append("=").append(i * pageSize);
							}
							html.append(queryString);
						} else {
							html.append(rewritePage.replaceAll("\\[P]", String.valueOf(i * pageSize)));
						}
						html.append("\" class=\"nav_page\">").append(i + 1).append("</a></span> ");
					}
				}
			}
		} else {
			html = new StringBuilder();
		}
		return html.toString();
	}
	public int getCurrentPage() {
		return currentPage + 1;
	}
	public int getRecords() {
		return records;
	}
	public String getRecords(boolean returnHtml) {
		return "<span class=\"num_records\">" + records + "</span>";
	}
	public int getNumPages() {
		return numPages;
	}
	public String getNumPages(boolean returnHtml) {
		return "<span class=\"num_pages\">" + numPages + "</span>";
	}
	public int getPageStart() {
		return currentSetion * sectionPages;
	}
	public int getPageEnd() {
		return (currentSetion * sectionPages) + sectionPages;
	}
	public int getPageSize() {
		return pageSize;
	}
	public String getPage() {
		return getPage(false);
	}
	public String getPage(boolean showSetion) {
		return "共 " + getRecords(true) + " 个记录 " + getCurrentPage() + " / " + getNumPages(true) + " 页 " + getFirstPage(true) + " " + (showSetion ? getPrevSetion(true) + " " : "") + getPrevPage(true) + " " + getNav() + " " + getNextPage(true) + " " + (showSetion ? getNextSetion(true) + " " : "") + getLastPage(true);
	}
	public Map<String, Object> getPageMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("records", getRecords(true));
		map.put("currentPage", getCurrentPage());
		map.put("numPages", getNumPages(true));
		map.put("firstPage", getFirstPage(true));
		map.put("prevSetion", getPrevSetion(true));
		map.put("prevPage", getPrevPage(true));
		map.put("nav", getNav());
		map.put("nextPage", getNextPage(true));
		map.put("nextSetion", getNextSetion(true));
		map.put("lastPage", getLastPage(true));
		return map;
	}
}
