package com.pragure.util;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pragure.contstants.Group;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.setting.Setting;

/** 
 * <pre>
 * 文件剔除工具
 * 配置setting文件：
 * 1、配置[dir]指定处理的目录；
 * 2、配置[module]指定处理的模块；
 * 2、配置[version]指定处理的版本信息；
 * 3、配置[senior]指定高级版处理方式；
 * 3、配置[standard]指定标准版处理方式；
 * 3、配置[junior]指定初级版处理方式；
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
public class DiscardUtil {

	public static void main(String[] args) {
		discard("discard.setting");
	}
	
	/**
	 * 处理剔除
	 *
	 * @param settingUrl 配置文件路径（相对地址）
	 */
	public static void discard(String settingUrl) {
		Setting setting = new Setting(settingUrl, true);
		String root = getRoot(setting);
		String dir = setting.getByGroup("dir", Group.DIR);
		Collection<String> modules = setting.getMap("module").values();
		List<String> groups = setting.getGroups();
		for(String group : groups) {
			switchCase(root, dir, modules, group, setting);
		}
	}
	
	/**
	 * 获取处理的根目录
	 *
	 * @param setting	配置对象
	 * @return 
	 */
	private static String getRoot(Setting setting) {
		String dir = setting.getByGroup("root", Group.DIR);
		if(StrUtil.isBlank(dir)) {
			dir = ".";
		}
		
		return FileUtil.getAbsolutePath(dir);
	}

	/**
	 * 处理动作
	 *
	 * @param root		根目录
	 * @param dir		目录
	 * @param modules	模块
	 * @param group		分组key
	 * @param setting 	配置对象
	 */
	private static void switchCase(String root, String dir, Collection<String> modules, String group, Setting setting) {
		if(!Group.DIR.equalsIgnoreCase(group) && !Group.MODULE.equalsIgnoreCase(group)) {
			discard(root, dir, modules, setting.getMap(group));
		}
	}
	
	/**
	 * 剔除处理
	 *
	 * @param root		根目录
	 * @param dir		目录
	 * @param modules	模块
	 * @param groupMap 	分组配置
	 */
	private static void discard(String root, String dir, Collection<String> modules, Map<String, String> groupMap) {
		Set<String> keys = groupMap.keySet();
		String groupDir = groupMap.get(Group.DIR);
		String srcDir = root + File.separator + dir;
		String distDir = root + File.separator + groupDir;
		
		if(FileUtil.exist(distDir)) {
			FileUtil.del(distDir);
		}
		FileUtil.mkParentDirs(distDir);
		
		LOG.debug("----------------------------------------------------");
		
		for(String module : modules) {
			// 复制代码
			LOG.debug("start to copy code {} ...", module);
			FileUtil.copy(srcDir + File.separator + module, distDir, true);
		}
		
		// 处理该版本源码
		LOG.debug("start to discard {}", groupDir);
		String discardDir = distDir;
		String discardFile = null;
		String discardValue = null;
		for(final String key : keys) {
			if(Group.DIR.equalsIgnoreCase(key)) {
				continue;
			}
			
			discardFile = discardDir + File.separator + key;
			if(FileUtil.exist(discardFile)) {
				FileUtil.del(discardFile);
				LOG.debug("discard file {}", discardFile);
			}
			
			discardValue = discardDir + File.separator + groupMap.get(key);
			if(FileUtil.exist(discardValue) && discardValue.endsWith("pom.xml")) {
				File file = new File(discardFile);
				String line = FileUtil.readString(discardValue, CharsetUtil.UTF_8);
				String src = "<module>modules/" + file.getName() + "</module>";
				line = line.replaceAll(src, "");
				FileUtil.writeString(line, discardValue, CharsetUtil.UTF_8);
				LOG.debug("discard file line {}", src);
			}
		}
	}
	
	private final static Log LOG = LogFactory.get();
	
}
