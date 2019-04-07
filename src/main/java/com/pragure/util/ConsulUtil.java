package com.pragure.util;

import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.hutool.json.JSONUtil;
import cn.hutool.setting.Setting;

/** 
 * <pre>
 * 删除consul注册中已失效服务
 * 配置setting文件：
 * 1、配置[host]指定处理的主机；
 * 2、配置[consul]指定处理的consul地址，一般不用改；
 * </pre>
 * 
 * <pre> 
 * 构建组：pragure-util
 * 作者：eddy
 * 邮箱：xqxyxchy@126.com
 * 日期：2019年4月7日-下午9:00:25
 * 版权：eddy版权所有
 * </pre>
 */
public class ConsulUtil {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ConsulUtil.class);

	private static final String ID = "ID";
	private static final String SERVICE = "Service";
	private static final String ADDRESS = "Address";
	private static final String PORT = "Port";
	
	private static final String HEALTH = "http://Address:Port/health";
	
	private static String GROUP = "consul";
	private static String CONSUL = null;
	private static String DEREGISTER = null;
	
	static {
		// 初始化配置
		Setting setting = new Setting("consul.setting", true);
		CONSUL = setting.getByGroup("services", GROUP);
		DEREGISTER = setting.getByGroup("services.deregister", GROUP);
		
		boolean exit = false;
		if(StrUtil.isBlank(CONSUL)) {
			exit = true;
			LOGGER.error("please set value of services in the file consul.setting");
		}
		else {
			LOGGER.info("consul services url ==> {}", CONSUL);
		}
		
		if(StrUtil.isBlank(DEREGISTER)) {
			exit = true;
			LOGGER.error("please set value of services in the file consul.setting");
		}
		else {
			LOGGER.info("consul services deregister url ==> {}", DEREGISTER);
		}
		
		if(exit) {
			System.exit(-1);
		}
	}
	
	public static void main(String[] args) {
		checkAndRemove();
	}

	public static void checkAndRemove() {
		Map<String, Object> servicesMap = findConsulServices();
		LOGGER.debug("services ==> {}", servicesMap);
		parseServices(servicesMap);
	}
	
	/**
	 * 解析并检查服务
	 *
	 * @param servicesMap 
	 */
	@SuppressWarnings("unchecked")
	private static void parseServices(Map<String, Object> servicesMap) {
		if(BeanUtil.isEmpty(servicesMap))
		{
			LOGGER.warn("no services data.");
			return;
		}
		
		Map<String, Object> valueMap = null;
		for (Iterator<Object> iterator = servicesMap.values().iterator(); iterator.hasNext();) {
			valueMap = (Map<String, Object>) iterator.next();
			String id = (String) valueMap.get(ID);
			String service = (String) valueMap.get(SERVICE);
			String address = (String) valueMap.get(ADDRESS);
			int port = (int) valueMap.get(PORT);
			
			LOGGER.debug("check service {} ...", service);
			
			HttpResponse response = null;
			try {
				String url = HEALTH.replace(ADDRESS, address).replaceAll(PORT, port+"");
				
				response = HttpUtil.createGet(url).execute();
				LOGGER.debug("health response {}", response.body());
			} catch (Exception e) {
				LOGGER.error("health error:{}", e.getMessage());
				LOGGER.debug("deregister service {} ...", service);
				String url = DEREGISTER + "/" + id;
				
				response = HttpUtil.createRequest(Method.PUT, url).execute();
				LOGGER.debug("deregister service response \"{}\".", response.body());
			}
		}
	}
	
	/**
	 * 获取consul中服务的注册信息
	 *
	 * @return 
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> findConsulServices() {
		HttpResponse response = HttpUtil.createGet(CONSUL)
										.contentType("application/json")
										.execute();
		return JSONUtil.toBean(response.body(), Map.class);
	}
	
}
