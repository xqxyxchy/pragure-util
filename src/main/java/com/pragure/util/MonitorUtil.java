package com.pragure.util;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.email.EmailPopulatingBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.config.TransportStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pragure.util.SigarUtil.Result;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.setting.Setting;

/** 
 * <pre>
 * 硬件资源监控工具
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
public class MonitorUtil {
	
	public static Setting setting;
	
	static {
		setting = new Setting("monitor.setting");
	}

	public static void main(String[] args) {
		execute();
	}
	
	public static void execute() {
		boolean inner = setting.getBool("timer.inner", "true");
		MonitorTask task = new MonitorTask();
		if(inner)
		{
			Timer timer = new Timer();
			long interval = setting.getLong("timer.interval", "300000");
			timer.schedule(task, 1000L, interval);
		}
		else
		{
			LoggerFactory.getLogger(MonitorUtil.class).error("No supports for other time scheduler.");
		}
	}
	
}

class MonitorTask extends TimerTask {

	private static Logger LOGGER = LoggerFactory.getLogger(MonitorTask.class);
	
	// 3分钟内连续超过阈值，则发送通知
	// 1个小时只发送一次通知
	// 恢复后立即通知
	
	private String CACHE_KEY_OVER = "over";
	private String CACHE_KEY_NOTIFY = "notify";
	
	private String CACHE_KEY_RUNNING = "running";
	
	private Cache<Object, Object> overCache = CacheBuilder.newBuilder()
														.maximumSize(3)
														.expireAfterWrite(Long.valueOf(MonitorUtil.setting.getByGroup("monitor", "cache.over")), TimeUnit.SECONDS)
														.build();
	
	private Cache<Object, Object> notifyCache = CacheBuilder.newBuilder()
														.maximumSize(3)
														.expireAfterWrite(Long.valueOf(MonitorUtil.setting.getByGroup("monitor", "cache.notify")), TimeUnit.SECONDS)
														.build();
	
	private Cache<Object, Object> runningCache = CacheBuilder.newBuilder()
			.maximumSize(3)
			.build();
	
	@Override
	public void run() {
		try {
			LOGGER.debug("--------------------------------------------------");
			StringBuilder result = new StringBuilder();
			
			String monitorName = MonitorUtil.setting.getByGroup("monitor", "name");
			result.append("host ").append(monitorName).append(";").append("\n\t\b");
			
			double monitorCpu = Double.valueOf(MonitorUtil.setting.getByGroup("monitor", "cpu"));
			double monitorMem = Double.valueOf(MonitorUtil.setting.getByGroup("monitor", "mem"));
			double monitorDisk = Double.valueOf(MonitorUtil.setting.getByGroup("monitor", "disk"));
			
			// CPU 使用率
			Result cpuResult = SigarUtil.cpuUsedOver(monitorCpu);
			if(cpuResult.isOver())
			{
				result.append("cpu used ").append(cpuResult.getPercent()).append(";").append("\n\t\b");
				LOGGER.debug("cpu result ==> {}.", cpuResult);
			}
			
			// 内存 使用率
			Result memResult = SigarUtil.memUsedOver(monitorMem);
			if(memResult.isOver())
			{
				result.append("memory used ").append(memResult.getPercent()).append(";").append("\n\t\b");
				LOGGER.debug("memory result ==> {}.", memResult);
			}
			
			// 硬盘 使用率
			Result diskResult = SigarUtil.diskUsedOver(monitorDisk);
			if(diskResult.isOver())
			{
				result.append("disk used ").append(diskResult.getPercent()).append(";").append("\n\t\b");
				LOGGER.debug("disk result ==> {}.", diskResult);
			}
			
			boolean isSend = cpuResult.isOver() || memResult.isOver() || diskResult.isOver();
			
			if(isSend)
			{
				// 达到第一条发送条件
				Object over = overCache.getIfPresent(CACHE_KEY_OVER);
				LOGGER.debug("over cache ==> {}.", over);
				if(null == over)
				{
					overCache.put(CACHE_KEY_OVER, new Result[] {cpuResult, memResult, diskResult});
				}

				Object running = runningCache.getIfPresent(CACHE_KEY_RUNNING);
				LOGGER.debug("running cache ==> {}.", running);
				if(null == running)
				{
					running = DateUtil.now();
					runningCache.put(CACHE_KEY_RUNNING, running);
				}
				
				Object notify = notifyCache.getIfPresent(CACHE_KEY_NOTIFY);
				LOGGER.debug("notify cache ==> {}.", notify);
				if(null == notify)
				{
					if(null != running 
							&& DateUtil.between(DateUtil.parse(running.toString()), new Date(), DateUnit.SECOND) 
								>= Long.valueOf(MonitorUtil.setting.getByGroup("monitor", "cache.over")))
					{
						// 达到第二条发送条件-真正发送通知
						send("-服务器过载-", result, monitorName, cpuResult, memResult, diskResult, isSend);
						notifyCache.put(CACHE_KEY_NOTIFY, DateUtil.now());
					}
				}
				else
				{
					LOGGER.debug("No duplication notify in {} seconds.", MonitorUtil.setting.getByGroup("monitor", "cache.notify"));
					return;
				}
			}
			else
			{
				Object notify = notifyCache.getIfPresent(CACHE_KEY_NOTIFY);
				LOGGER.debug("notify cache ==> {}.", notify);
				if(null != notify)
				{
					send("-服务器恢复-", result, monitorName, cpuResult, memResult, diskResult, true);
				}

				overCache.invalidateAll();
				notifyCache.invalidateAll();
				runningCache.invalidateAll();

				LOGGER.debug("server machine is all right.");
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	private void send(String emailTile, StringBuilder result, 
			String monitorName, Result cpuResult, Result memResult, Result diskResult,
			boolean isSend) {
		LOGGER.debug("Starting send notify.");
		
		// 发送邮件通知
		if(Boolean.valueOf(MonitorUtil.setting.getByGroup("monitor", "email.enable")) && isSend)
		{
			LOGGER.debug("Starting send email.");
			sendEmail(DateUtil.now() + emailTile + monitorName, result.toString());
		}
	}
	
	private void sendEmail(String subject, String content, byte[] ... attachments) {
		String senderName = MonitorUtil.setting.getByGroup("email", "sender.name");
		String senderAddress = MonitorUtil.setting.getByGroup("email", "sender.address");
		String senderpassword = MonitorUtil.setting.getByGroup("email", "sender.password");
		String senderSmtp = MonitorUtil.setting.getByGroup("email", "sender.smtp");
		
		EmailPopulatingBuilder builder = 
				EmailBuilder.startingBlank()
		          .from(senderName, senderAddress)
		          .withSubject(subject)
		          .withPlainText(content);

		String recAddress = MonitorUtil.setting.getByGroup("monitor", "email.address");
		String[] recAddressArr = recAddress.split(",");
		for(String recAddressStr : recAddressArr)
		{
			builder.to(recAddressStr, recAddressStr);
		}
		
		Email email = builder.buildEmail();
		
		Mailer mailer = MailerBuilder
		          .withSMTPServer(senderSmtp, 465, senderAddress, senderpassword)
		          .withTransportStrategy(TransportStrategy.SMTPS)
		          .withSessionTimeout(10 * 1000)
		          .clearEmailAddressCriteria() // turns off email validation
		          //.withDebugLogging(true)
		          .buildMailer();

		mailer.sendMail(email);
		
		LOGGER.debug("send mail ==> " + subject);
	}
	
}
