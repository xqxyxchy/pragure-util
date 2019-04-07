package com.pragure.util;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pragure.contstants.Group;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.setting.Setting;

/** 
 * <pre>
 * 文件内容删除工具
 * 配置setting文件：
 * 1、配置[dir]指定处理的目录；
 * 2、配置[delete.file]指定清理的文件；
 * 3、配置[delete.property]指定清理的文件属性；
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
public class ClearUtil {

	public static void main(String[] args) {
		clear("clear.setting");
	}
	
	/**
	 * 处理清理
	 *
	 * @param settingUrl 配置文件路径（相对地址）
	 */
	public static void clear(String settingUrl) {
		Setting setting = new Setting(settingUrl);
		String dir = getDir(setting);
		List<String> groups = setting.getGroups();
		for(String group : groups) {
			switchCase(dir, group, setting);
		}
	}
	
	/**
	 * 获取处理的目录
	 *
	 * @param setting	配置对象
	 * @return 
	 */
	private static String getDir(Setting setting) {
		String dir = setting.getByGroup("dir", Group.DIR);
		if(StrUtil.isBlank(dir)) {
			dir = ".";
		}
		
		return FileUtil.getAbsolutePath(dir);
	}

	/**
	 * 处理动作
	 *
	 * @param dir		处理目录
	 * @param group		配置分组key
	 * @param setting 	配置对象
	 */
	private static void switchCase(String dir, String group, Setting setting) {
		if(Group.DELETE_FILE.equalsIgnoreCase(group)) {
			deleteFile(dir, setting.getMap(group));
		}
		else if(Group.DELETE_PROPERTY.equalsIgnoreCase(group)) {
			deleteProperty(dir, setting.getMap(group));
		}
	}
	
	/**
	 * 清理文件属性
	 *
	 * @param dir		处理目录
	 * @param groupMap 	配置内容
	 */
	private static void deleteProperty(String dir, Map<String, String> groupMap) {
		Collection<String> fileNames = groupMap.values();
		Set<String> ignores = groupMap.keySet();
		Set<String> fileNameSet = new HashSet<String>(fileNames);
		
		List<File> files = null;
		String line = null;
		int osz = 0;
		int nsz = 0;
		String[] ignoresArray = new String[ignores.size()];
		
		for(final String fileName : fileNameSet) {
			LOG.debug("----------------------------------------------------");
			LOG.debug("scan {}", fileName);
			files = FileUtil.loopFiles(dir, new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.getName().equalsIgnoreCase(fileName);
				}
			});
			for(File file : files) {
				line = FileUtil.readString(file, CharsetUtil.UTF_8);
				osz = line.length();
				ignoresArray = ignores.toArray(ignoresArray);
				line = line.replaceAll(ArrayUtil.join(ignoresArray, "|"), "");
				nsz = line.length();
				if(osz > nsz) {
					FileUtil.writeString(line, file, CharsetUtil.UTF_8);
					LOG.debug("clear {}", file.getAbsolutePath());
				}
			}
		}
	}

	/**
	 * 清理文件
	 *
	 * @param dir		处理目录
	 * @param groupMap 	配置内容
	 */
	private static void deleteFile(String dir, Map<String, String> groupMap) {
		Set<String> keys = groupMap.keySet();
		List<File> files = null;
		for(final String key : keys) {
			LOG.debug("----------------------------------------------------");
			LOG.debug("scan {}", key);
			files = FileUtil.loopFiles(dir, new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.getName().equalsIgnoreCase(key);
				}
			});
			for(File file : files) {
				FileUtil.del(file);
				LOG.debug("delete file {}", file.getAbsolutePath());
			}
		}
	}
	
	/**
	 * 日志对象
	 */
	private final static Log LOG = LogFactory.get();
	
}
