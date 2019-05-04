package com.gamewolf.restcrawler;

import com.gamewolf.restcrawler.resources.TestResource;
import com.gamewolf.restcrawler.resources.book.BookResource;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;


public class CrawlerApplication extends Application<CrawlerConfiguration> {

	@Override
	public void run(CrawlerConfiguration crawlerConfig, Environment env) throws Exception {
		
		//注册resource
		final TestResource resource=new TestResource();
		final BookResource bookResource=new BookResource();
		env.jersey().register(resource);
		env.jersey().register(bookResource);
		
	}
	
	
	public static void main(String args[]) {
		try {
			new CrawlerApplication().run(args);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
