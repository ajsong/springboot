//Developed by @mario 1.0.20220113
package com.laokema.tool;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.*;

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
			Pattern r = Pattern.compile(pageMark+"=\\d+&?");
			Matcher m = r.matcher(this.queryString);
			this.queryString = m.replaceAll("");
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
			html = "<span class=\"ezr_nav_ge\"></span><a href=\"";
			if (rewritePage.length() == 0) {
				html += url + (queryString.length() > 0 ? "?" + queryString : "");
			} else {
				html += rewritePage.replaceAll("\\[P]", "0");
			}
			html += "\" class=\"ezr_first_page\">" + firstString + "</a>";
		} else {
			html = "<span class=\"ezr_nav_ge\"></span>";
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
			html += "\" class=\"ezr_back\">" + prevString + "</a>";
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
			html += "\" class=\"ezr_next\">" + nextString + "</a>";
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
			html += "\" class=\"ezr_last_page\">" + lastString + "</a>";
		} else {
			html = "<span class=\"ezr_nav_ge\"></span>";
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
			html += "\" class=\"ezr_back_section\">" + prevSetionString + "</a>";
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
			html += "\" class=\"ezr_next_section\">" + nextSetionString + "</a>";
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
						html.append("<span class=\"ezr_nav_na\" data-limit=\"").append(pageSize).append("\">").append(i + 1).append("</span> ");
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
						html.append("\" class=\"ezr_nav\">").append(i + 1).append("</a></span> ");
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
		return "<span class=\"ezr_num_records\">" + records + "</span>";
	}
	public int getNumPages() {
		return numPages;
	}
	public String getNumPages(boolean returnHtml) {
		return "<span class=\"ezr_num_pages\">" + numPages + "</span>";
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
}
