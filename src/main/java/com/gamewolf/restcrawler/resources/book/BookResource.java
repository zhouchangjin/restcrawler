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
import com.gamewolf.database.util.JSONUtils;
import com.gamewolf.dbcrawler.base.DBCrawler;
import com.gamewolf.dbcrawler.crawler.book.model.DDBookFSearch;
import com.gamewolf.dbcrawler.crawler.book.model.DangDangPricePage;
import com.gamewolf.dbcrawler.crawler.book.model.JDCrawlerSearch;
import com.gamewolf.dbcrawler.crawler.book.model.JDPageObject;
import com.gamewolf.restcrawler.api.Book;
import com.gamewolf.restcrawler.resources.base.BaseResource;
import com.gamewolf.util.book.BookExtractor;
import com.harmonywisdom.crawler.annotation.PageCrawlerDBSetting;
import com.harmonywisdom.crawler.httputil.HtmlFetcher;

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
		if(dd.size()>0) {
			
			DDBookFSearch search=(DDBookFSearch) dd.get(0);
			if(search.getLink()!=null && !search.getLink().equals("")) {
				//System.out.println(search.getLink());
				
				
				
				//000-isbn
				book.setIsbn13(isbn);
				
				//001-图书名称
				book.setBookName(search.getBookName());
				//002-图书封面
				book.setBookCover(search.getBookCover());
				
				
				//003-作者
				book.setAuthorName(search.getAuthor());
				
				//004-出版社
				book.setPressName(search.getPressName());
				
				//005-链接（当当）
				
				
				String dangdangPageLink=search.getLink().replace("product.dangdang.com", "product.m.dangdang.com");
				book.setDangdangLink(dangdangPageLink);
				dangdangPageCrawler.getCrawler().addPage(dangdangPageLink);
				List<Object> list=dangdangPageCrawler.getCrawler().crawl(DangDangPricePage.class);
				dangdangPageCrawler.getCrawler().clearPage();
				if(list.size()==1) {
					DangDangPricePage dangdangPricePage=(DangDangPricePage)list.get(0);
					
					if(dangdangPricePage!=null 
							&& dangdangPricePage.getProductJson()!=null 
							&& !dangdangPricePage.getProductJson().equals("")) {
						findInDangDang=true;

						//006-售价（当当）
						float dangdangPrice=Float.parseFloat(dangdangPricePage.getPrice());
						book.setDangdangPrice(dangdangPrice);
						
						//007-定价
						String oriPriceStr=BookExtractor.getPriceFromText(dangdangPricePage.getOriginalPrice());
						if(oriPriceStr.equals("")) {
							oriPriceStr="0.0";
						}
						float oriPrice= Float.parseFloat(oriPriceStr);
						book.setPrice(oriPrice);
						
						//008-童书
						if(dangdangPricePage.getCategoryText()!=null && dangdangPricePage.getCategoryText().contains("童书")) {
							book.setKids(true);
						}else {
							book.setKids(false);
						}
						
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
												
												//009-丛书
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
												
												//011-开本
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
												//012-出版时间和印刷时间
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
												//013-包装
												String pack=item.getString("content");
												book.setPack(pack);
											}else if(item.getString("name").equals("纸张")) {
												//014-纸张
												String paper=item.getString("content");
												book.setPaper(paper);
											}else {
												//暂时没啥好做的
												System.out.println(item.getString("name")+":"+item.getString("content"));
											}
										}
										
										//010-套书
										if(suitFlagA && suitFlagB) {
											book.setSuite(true);
										}
										
									}else if(obj.getString("name").equals("简介")){
										//015-简介
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
								//016-类别
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
									//019-年龄段
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
							
							//017-评分（京东）
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
						//欠缺018-页数
			
					}
				}//结束
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
				boolean suitFlagA=false;
				boolean suitFlagB=false;
				String skuName=jdCrawlerSearch.getBookName();
				if(skuName.contains("套") || skuName.contains("册")) {
					suitFlagA=true;
				}
				//手机版才是这个地址
				//String jdPageLink="http:"+jdCrawlerSearch.getLink().replace("item.jd.com/", "item.m.jd.com/product/");
				String jdPageLink="http:"+jdCrawlerSearch.getLink();
				book.setJdLink(jdPageLink);
				//下面是反爬虫
				//jdPageCrawler.getCrawler().context.setHost("item.jd.com");
				//jdPageCrawler.getCrawler().context.setReferer(jingdongPage);
				jdPageCrawler.getCrawler().addPage(jdPageLink);
				List<Object> jdPageList=jdPageCrawler.getCrawler().crawl(JDPageObject.class);
				jdPageCrawler.getCrawler().clearPage();
				
				if(jdPageList.size()>0) {
					JDPageObject obj=(JDPageObject)jdPageList.get(0);
					System.out.println(JSON.toJSONString(obj));
					if(!obj.getBookName().equals("") && !findInDangDang) {
						if(!obj.getBookName().equals("")) {
							//000-isbn
							book.setIsbn13(isbn);
							//001-图书名称
							book.setBookName(obj.getBookName());
							//002-图书封面
							if(!obj.getBookCover().startsWith("http")) {
								book.setBookCover("http:"+obj.getBookCover());
							}else {
								book.setBookCover(obj.getBookCover());
							}
							//003-作者
							book.setAuthorName(obj.getAuthorName());
							
							//004-出版社
							book.setPressName(obj.getExpressName());
							
							//008-童书
							if(obj.getCatName().contains("童书") || obj.getCatName().contains("Children")) {
								book.setKids(true);
							}else {
								book.setKids(false);
							}
							
							//009-丛书
							if(obj.getIsbn13().equals(isbn)) {
								//一定不是丛书
								book.setBookset(false);
								suitFlagB=true;
								
							}else {
								//丛书非套书
								book.setBookset(true);
								book.setSuite(false);
							}
							//010-套书
							if(suitFlagA && suitFlagB) {
								book.setSuite(true);
							}
							
							//011-开本
							book.setSize(BookExtractor.getKaiben(obj.getBookSize()));
							book.setSizeStr(obj.getBookSize());
							
							//013-包装
							book.setPack(obj.getPack());
							
							//014-纸张
							book.setPaper(obj.getPaper());
							
							//015-简介
							book.setSummary(obj.getSummary());

							//016-类别
							if(obj.getCat().contains(",4761")) {
								//绘本
								book.setBookType("HB");
								book.setBookTypeStr("绘本");
							}else if(obj.getCat().contains(",3394")) {
								//儿童文学
								book.setBookType("ZGETWX");
								book.setBookTypeStr("中国儿童文学");
							}else if(obj.getCat().contains(",3399")) {
								//科普百科
								book.setBookType("KPBK");
								book.setBookTypeStr("科普百科");
								
							}else if(obj.getCat().contains(",3391")) {
								//动漫卡通
								book.setBookType("ZGET_KTDM");
								book.setBookTypeStr("卡通动漫");
								
							}else if(obj.getCat().contains(",3401")) {
								//英文
								book.setBookType("YW");
								book.setBookTypeStr("英文");
							}else if(obj.getCat().contains(",3397")) {
								//音乐舞蹈
								book.setBookType("ZXYS_YSKT");
								book.setBookTypeStr("艺术课堂");
							}else if(obj.getCat().contains(",3400")) {
								//励志成长
								book.setBookType("QT");
								book.setBookTypeStr("其他");
							}else if(obj.getCat().contains(",4762")) {
								//美术书法
								book.setBookType("ZXYS_YSKT");
								book.setBookTypeStr("艺术课堂");
							}else {
								//
								book.setBookType("QT");
								book.setBookTypeStr("其他");
							}
							
						}
						
					}
					
					if(!obj.getBookName().equals("")) {
						
						if(book.getAgeType()==null 
								|| book.getAgeType().equals("") 
								|| book.getAgeType().equals("age_3_6,age_7_10,age_11_14")) {
							//019-年龄段
							if(obj.getBookName().contains("0-2岁")) {
								
								book.setAgeType("age_0_2");
								book.setAgeTypeStr("0到2岁");
								
							}else if(obj.getBookName().contains("3-6岁")) {
								
								book.setAgeType("age_3_6");
								book.setAgeTypeStr("3到6岁");
								
							}else if(obj.getBookName().contains("7-10岁")) {
								
								book.setAgeType("age_7_10");
								book.setAgeTypeStr("7到10岁");
								
							}else if(obj.getBookName().contains("11-14岁")) {
								book.setAgeType("age_11_14");
								book.setAgeTypeStr("11到14岁");
							}else {
								book.setAgeType("age_3_6,age_7_10,age_11_14");
								book.setAgeTypeStr("3到14岁");
							}
						}
						
						if(obj.getPageSize().matches("[0-9]+")) {
							//018-页数
							book.setPage(Integer.parseInt(obj.getPageSize()));
						}
						
						if(book.getPublishDate()==null || book.getPublishDate().equals("")) {
							//012-出版时间和印刷时间
							book.setPublishDate(obj.getPublishDate());
							book.setPrintDate(obj.getPublishDate());
						}
						//005-链接（京东）
						book.setJdLink(jdPageLink);
						

						//017-评分（京东）
						
						
						
						String urlForPrice="http://c0.3.cn/stock?skuId="+obj.getSkuid()+"&venderId="+obj.getVenderId()+"&cat="+obj.getCat()+"&area=1_72_2799_0";
						//System.out.println(urlForPrice);
						String json=HtmlFetcher.FetchFromUrlWithCharsetSpecified(urlForPrice,"gb2312");
						//System.out.println(json);
						
						JSONObject jsonObject=JSON.parseObject(json);
						if(jsonObject.containsKey("stock")) {
							JSONObject stock=jsonObject.getJSONObject("stock");
							if(stock.containsKey("jdPrice")) {
								JSONObject priceObj=stock.getJSONObject("jdPrice");
								if(priceObj.containsKey("m")) {
										String priceStr=priceObj.getString("m");
										String opStr=priceObj.getString("op");
										try {
											if(book.getPrice()==0) {
												//007-定价
												float price=Float.parseFloat(priceStr);
												book.setPrice(price);
											}
											//006-售价（京东）
											float jdPrice=Float.parseFloat(opStr);
											book.setJingdongPrice(jdPrice);
										}catch(Exception e) {
											e.printStackTrace();
										}
										
								}
							}
						}
						
						
						
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
