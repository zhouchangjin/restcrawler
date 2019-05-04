package com.gamewolf.restcrawler.resources.base;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.gamewolf.dbcrawler.base.DBCrawler;
import com.harmonywisdom.crawler.annotation.PageCrawlerDBSetting;

public class BaseResource {
	
	public BaseResource() {
		System.out.println("=========初始化工作============");
		initialization();
	}
	
	
	private void initialization() {
		crawlerAware();
	}
	
	private void crawlerAware() {
		
		Field f[] = this.getClass().getDeclaredFields();
		for (Field field : f) {
			if (field.getType().equals(DBCrawler.class) && field.isAnnotationPresent(PageCrawlerDBSetting.class)) {
				PageCrawlerDBSetting setting = field.getAnnotation(PageCrawlerDBSetting.class);
				DBCrawler crawler = DBCrawler.startBuild()
						.fromJDBCPropertieFile(setting.propertiePath(), setting.propertieFile(), setting.isResource())
						.setTable(setting.table()).setMappingConfig(setting.javaClass()).setIdName(setting.idField())
						.setConfigurationColumn(setting.colName()).setId(setting.value()).build();
			try {
					field.setAccessible(true);
					field.set(this, crawler);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}

			}
		}
	}
}
