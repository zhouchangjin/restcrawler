package com.gamewolf.restcrawler.resources.book;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.gamewolf.dbcrawler.base.DBCrawler;
import com.gamewolf.dbcrawler.crawler.book.model.DDBookFSearch;
import com.gamewolf.dbcrawler.crawler.book.model.DangDangPricePage;
import com.gamewolf.dbcrawler.crawler.book.model.JDCrawlerSearch;
import com.gamewolf.dbcrawler.crawler.book.model.JDPageObject;
import com.gamewolf.restcrawler.api.Book;
import com.gamewolf.restcrawler.resources.base.BaseResource;
import com.gamewolf.util.book.BookExtractor;
import com.harmonywisdom.crawler.annotation.PageCrawlerDBSetting;

@Path("/book")
@Produces(MediaType.APPLICATION_JSON)
public class BookResource extends BaseResource{
	
	@PageCrawlerDBSetting(value = "ISBN_DD")
	public DBCrawler dangdangSearchCrawler;
	
	@PageCrawlerDBSetting(value = "ISBN_JD")
	public DBCrawler jdSearchCrawler;
	
	@PageCrawlerDBSetting(value="DETAIL_JD_PRICE")
	public DBCrawler jdPageCrawler;
	
	@PageCrawlerDBSetting(value = "DETAIL_DD_PRICE")
	public DBCrawler dangdangPageCrawler;
	
	private static final String dangdangSearchUrl="http://search.dangdang.com/?key=";
	
	private static final String jdSearchUrl="https://search.jd.com/Search?keyword=";
	

	@GET
	@Path("{isbn}")
	public Book book(@PathParam(value="isbn")String isbn) {
		System.out.println("===================");
		Book book=new Book();
		
		String dangdangPage=dangdangSearchUrl+isbn;
		
		String jingdongPage=jdSearchUrl+isbn;
		
		//当当抓取
		dangdangSearchCrawler.getCrawler().addPage(dangdangPage);
		List<Object> dd=dangdangSearchCrawler.getCrawler().crawl(DDBookFSearch.class);
		dangdangSearchCrawler.getCrawler().clearPage();
		boolean findInDangDang=false;
		boolean findInJD=false;
		if(dd.size()>0 && false) {
			
			DDBookFSearch search=(DDBookFSearch) dd.get(0);
			if(search.getLink()!=null && !search.getLink().equals("")) {
				System.out.println(search.getLink());
				findInDangDang=true;
				book.setBookName(search.getBookName());
				book.setBookCover(search.getBookCover());
				book.setDangdangLink(search.getLink());
				book.setPressName(search.getPressName());
				book.setAuthorName(search.getAuthor());
				book.setIsbn13(isbn);
				//
				dangdangPageCrawler.getCrawler().addPage(search.getLink());
				List<Object> list=dangdangPageCrawler.getCrawler().crawl(DangDangPricePage.class);
				dangdangPageCrawler.getCrawler().clearPage();
				if(list.size()==1) {
					DangDangPricePage dangdangPricePage=(DangDangPricePage)list.get(0);
					float dangdangPrice=Float.parseFloat(dangdangPricePage.getPrice());
					book.setDangdangPrice(dangdangPrice);
					String oriPriceStr=BookExtractor.getPriceFromText(dangdangPricePage.getOriginalPrice());
					if(oriPriceStr.equals("")) {
						oriPriceStr="0.0";
					}
					float oriPrice= Float.parseFloat(oriPriceStr);
					if(dangdangPricePage.getCategoryText()!=null && dangdangPricePage.getCategoryText().contains("童书")) {
						book.setKids(true);
					}else {
						book.setKids(false);
					}
					book.setPrice(oriPrice);
					String json=dangdangPricePage.getProductJson();
					int pos=json.lastIndexOf(";");
					if(json.length()-pos<10) {
						json=json.substring(0, pos);
					}
					JSONObject jsonObj=JSON.parseObject(json);
					boolean suitFlagA=false;
					boolean suitFlagB=false;
					if(jsonObj.containsKey("product_desc_sorted")) {
						JSONArray array=jsonObj.getJSONArray("product_desc_sorted");
						for(int i=0;i<array.size();i++) {
							JSONObject obj=array.getJSONObject(i);
							if(obj.containsKey("name") ) {
								
								if(obj.getString("name").equals("出版信息")) {
									JSONArray infoArray=obj.getJSONArray("content");
									for(int j=0;j<infoArray.size();j++) {
										JSONObject item=infoArray.getJSONObject(j);
										
										if(item.getString("name").equals("书名")) {
											String bookName=item.getString("content");
											book.setBookName(bookName);
											if(bookName.contains("套") || bookName.contains("册")) {
												suitFlagA=true;
											}else {
												suitFlagA=false;
											}
										}else if(item.getString("name").equals("ISBN")) {
											String isbnshow=item.getString("content");
											if(isbnshow.equals(isbn)) {
												//不是丛书
												book.setBookset(false);
												suitFlagB=true;
											}else {
												//是丛书
												book.setBookset(true);
												suitFlagB=false;
											}
											
										}else if(item.getString("name").equals("开本")) {
											
											String kaibenText=item.getString("content");
											int kaiben=BookExtractor.getKaiben(kaibenText);
											book.setSize(kaiben);
											
											book.setSizeStr(kaibenText);
											
										}else if(item.getString("name").equals("作者")) {
											String author=item.getString("content");
											book.setAuthorName(author);
										}else if(item.getString("name").equals("出版社")) {
											String pressName=item.getString("content");
											book.setPressName(pressName);
										}else if(item.getString("name").equals("出版时间")) {
											String pubTime=item.getString("content");
											SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
											try {
												Date time=sdf.parse(pubTime);
												book.setPublishDate(pubTime);
												book.setPrintDate(pubTime);
											} catch (ParseException e) {
												System.out.println(pubTime);
												e.printStackTrace();
											}
										}else if(item.getString("name").equals("包装")) {
											String pack=item.getString("content");
											book.setPack(pack);
										}else if(item.getString("name").equals("纸张")) {
											String paper=item.getString("content");
											book.setPaper(paper);
										}else {
											//暂时没啥好做的
											System.out.println(item.getString("name")+":"+item.getString("content"));
										}
									}
									if(suitFlagA && suitFlagB) {
										book.setSuite(true);
									}
									
								}else if(obj.getString("name").equals("简介")){
									String summary=obj.getString("content");
									book.setSummary(summary);
								}else {
									
								}
								
								
								
							}
						}
					}
					
					if(jsonObj.containsKey("product_info_new")) {
						JSONObject info=jsonObj.getJSONObject("product_info_new");
						
						if(info.containsKey("category_info")) {
							JSONObject cat=info.getJSONObject("category_info");
							if(cat.containsKey("category_path")) {
								String path=cat.getString("category_path");
								/**
								String catArr[]=path.split("\\.");
								for(String catId:catArr) {
									System.out.print(catId+"-");
								}
								System.out.println();
								**/
								if(path.startsWith("01.41.01")) {
									book.setAgeType("age_0_2");
									book.setAgeTypeStr("0到2岁");
								}else if(path.startsWith("01.41.02")) {
									book.setAgeType("age_3_6");
									book.setAgeTypeStr("3到6岁");
								}else if(path.startsWith("01.41.03")) {
									book.setAgeType("age_7_10");
									book.setAgeTypeStr("7到10岁");
								}else if(path.startsWith("01.41.04")) {
									book.setAgeType("age_11_14");
									book.setAgeTypeStr("11到14岁");
								}else {
									book.setAgeType("age_3_6,age_7_10,age_11_14");
									book.setAgeTypeStr("3到14岁");
									
									if(path.startsWith("01.41.50")) {
										//动漫卡通
										if(isbn.startsWith("9787")) {
											book.setBookType("ZGET_KTDM");
											book.setBookTypeStr("卡通动漫");
										}else {
											book.setBookType("WGET_KTDM");
											book.setBookTypeStr("卡通动漫");
										}
									}else if(path.startsWith("01.41.05")) {
										book.setBookType("KPBK");
										book.setBookTypeStr("科普百科");
									}else if(path.startsWith("01.41.26")) {
										book.setBookType("ZGETWX");
										book.setBookTypeStr("中国儿童文学");
									}else if(path.startsWith("01.41.27")) {
										book.setBookType("WGETWX");
										book.setBookTypeStr("外国儿童文学");
									}else if(path.startsWith("01.41.57") || path.startsWith("01.41.51")) {
										book.setBookType("YW");
										book.setBookTypeStr("英文");
									}else if(path.startsWith("01.41.70") || path.startsWith("01.41.44.07") || path.startsWith("01.41.45.03")) {
										book.setBookType("HB");
										book.setBookTypeStr("绘本");
									}else if(path.startsWith("01.41.65")) {
										book.setBookType("ZXYS_YYQM");
										book.setBookTypeStr("音乐启蒙");
									}else if(path.startsWith("01.41.48")) {
										book.setBookType("QT");
										book.setBookTypeStr("其他");
									}else if(path.startsWith("01.41.45")) {
										if(path.startsWith("01.41.45.13")) {
											book.setBookType("KPBK_SLH");
											book.setBookTypeStr("数理化");
										}else if(path.startsWith("01.41.45.09")) {
											book.setBookType("ZXYS_YSKT");
											book.setBookTypeStr("艺术课堂");
										}else {
											book.setBookType("QT_YEQM");
											book.setBookTypeStr("幼儿启蒙");
										}
										
									}else {
										//System.out.println("其他："+path);
										book.setBookType("QT");
										book.setBookTypeStr("其他");
									}
								}
								
							}
						}
						
						if(info.containsKey("stars")) {
							JSONObject stars=info.getJSONObject("stars");
							if(stars.containsKey("full_star")) {
								float score=0.0f;
								int intStar=stars.getInteger("full_star");
								score=intStar*1.0f;
								boolean half=stars.getBooleanValue("has_half_star");
								if(half) {
									score+=0.5;
								}
								book.setDangdangScore(score*2);
							}
						}else {
							book.setDangdangScore(0.0f);
						}
					}
				}
				//return book;
			}

			
		}
		
		//京东抓取
		
		
		jdSearchCrawler.getCrawler().addPage(jingdongPage);
		List<Object> jdObjects=jdSearchCrawler.getCrawler().crawl(JDCrawlerSearch.class);
		jdSearchCrawler.getCrawler().clearPage();
		
		if(jdObjects.size()>0) {
			
			JDCrawlerSearch jdCrawlerSearch=(JDCrawlerSearch) jdObjects.get(0);
			if(jdCrawlerSearch.getLink()!=null && !jdCrawlerSearch.getLink().equals("")) {
				findInJD=true;
				
				if(!findInDangDang) {
					book.setBookName(jdCrawlerSearch.getBookName());
					book.setBookCover(jdCrawlerSearch.getBookCover());
					
				}
				book.setJdLink(jdCrawlerSearch.getLink());
				
				jdPageCrawler.getCrawler().addPage(book.getJdLink());
				List<Object> jdPageList=jdPageCrawler.getCrawler().crawl(JDPageObject.class);
				jdPageCrawler.getCrawler().clearPage();
				
				if(jdPageList.size()>0) {
					JDPageObject obj=(JDPageObject)jdPageList.get(0);
					if(!findInDangDang) {
						book.setBookName(obj.getBookName());
						book.setBookCover(obj.getBookCover());
						book.setAuthorName(obj.getAuthorName());
						//book.set
					}
				}
			}
			
	
		}
		
		
		if(findInDangDang || findInJD) {
			return book;
		}else {
			return book;
		}
		
	}

}
