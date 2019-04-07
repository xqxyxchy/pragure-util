package com.pragure.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pragure.contstants.Group;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.setting.Setting;

/** 
 * <pre>
 * SVN（未测试git）差异文件工具
 * 配置setting文件：
 * 1、配置[dir]指定处理的目录；
 * 2、配置[change.log]指定提交日志文件，可配置多个；
 * 3、配置[exclude]指定排除的提交记录；
 * 4、配置[code.dir]指定提交日志文件对应的代码目录；
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
public class ChangeUtil {

	public static void main(String[] args) {
		change("change.setting");
		DiscardUtil.main(args);
	}
	
	/**
	 * 创建差异文件
	 *
	 * @param settingUrl 	配置对象
	 */
	public static void change(String settingUrl) {
		Setting setting = new Setting(settingUrl);
		String dir = getDir(setting);
		LOG.debug("handle dir {}", dir);
		
		List<String> groups = setting.getGroups();
		for(String group : groups) {
			switchCase(dir, group, setting);
		}
	}
	
	/**
	 * 获取处理目录
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
	 * 处理事件
	 *
	 * @param dir		处理目录
	 * @param group		处理分组key
	 * @param setting 	配置对象
	 */
	private static void switchCase(String dir, String group, Setting setting) {
		if(Group.CHANGE_LOG.equalsIgnoreCase(group)) {
			createChangeFiles(dir, setting.getMap(group), setting.getMap(Group.EXCLUDE), setting.getMap(Group.CODE_DIR));
		}
	}
	
	/**
	 * 创建差异文件
	 *
	 * @param dir			处理目录
	 * @param groupMap		分组配置
	 * @param excludeMap	排除配置
	 * @param codeDirMap 	代码配置
	 */
	private static void createChangeFiles(String dir, Map<String, String> groupMap, Map<String, String> excludeMap, Map<String, String> codeDirMap) {
		Set<String> changeLogs = groupMap.keySet();
		List<String> lines = null;
		List<String> notes = null;
		String[] codePathPrefixArray = null;
		String codePath = null;
		String codeDir = null;
		Set<String> changeFilePaths = null;
		List<String> changeNotes = null;
		List<String> changeNotesTemp = null;
		int idx = 0;
		String resultFileName = null;
		String resultNoteFileName = null;
		File changeDir = createChangeDir(dir);
		File changeFile = null;
		int sum = 0;
		
		// 遍历svn提交记录文件
		for(String fileName : changeLogs) {
			LOG.debug("----------------------------------------------------");
			LOG.debug("Handle {}", fileName);
			
			codePathPrefixArray = groupMap.get(fileName).split(";");
			changeFilePaths = new HashSet<String>();
			
			try {
				lines = FileUtil.readLines(dir + fileName, CharsetUtil.UTF_8);
			} catch (Exception e) {
				LOG.error(e.getMessage());
			}
			
			if(BeanUtil.isEmpty(lines)) {
				continue;
			}
			
			line : for(String line : lines) {
				// 排除文件
				for(String exclude : excludeMap.keySet()) {
					if(line.contains(exclude)) {
						continue line;
					}
				}
				
				// 保留代码文件
				for(String codePathPrefix : codePathPrefixArray) {
					if(line.contains(codePathPrefix)) {
						idx = line.indexOf(codePathPrefix);
						codePath = line.substring(idx + codePathPrefix.length());
						changeFilePaths.add(codePath);
					}
				}
			}
			
			if(changeFilePaths.size() > 0) {
				// 输出差异结果
				resultFileName = changeDir.getAbsolutePath() + File.separator + fileName + ".result";
				FileUtil.writeLines(new ArrayList<>(changeFilePaths), resultFileName, CharsetUtil.UTF_8);
				LOG.debug("write result file {}", resultFileName);
			}
			
			try {
				lines = FileUtil.readLines(dir + fileName, "GB2312");
			} catch (Exception e) {
				LOG.error(e.getMessage());
			}
			
			notes = new ArrayList<String>();
			
			// 生成提交描述
			line : for(String line : lines) {
				// 排除文件
				for(String exclude : excludeMap.keySet()) {
					if(line.contains(exclude)) {
						continue line;
					}
				}
				
				// 保留代码文件
				for(String codePathPrefix : codePathPrefixArray) {
					if(!line.contains(codePathPrefix)) {
						try {
							notes.add(URLDecoder.decode(URLEncoder.encode(line, CharsetUtil.UTF_8), CharsetUtil.UTF_8));
						} catch (UnsupportedEncodingException e) {
						}
					}
				}
			}
			
			// 获取提交描述
			changeNotes = new ArrayList<String>();
			note : for(String note : notes) {
				if(StrUtil.isBlank(note)) {
					continue;
				}
				
				if(note.startsWith("Changed paths")) {
					continue;
				}
				
				if(note.startsWith("r")) {
					continue;
				}
				
				if("----------------------------------------------------------------------------".equals(note)) {
					continue;
				}
				
				for(String exclude : excludeMap.keySet()) {
					if(note.contains(exclude)) {
						continue note;
					}
				}
				
				for(String codePathPrefix : codePathPrefixArray) {
					if(note.contains(codePathPrefix)) {
						continue note;
					}
				}
				
				if(!changeNotes.contains(note)) {
					changeNotes.add(note);
					LOG.debug("result note : {}", note);
				}
			}
			
			if(changeNotes.size() > 0) {
				// 输出提交描述
				changeNotesTemp = new ArrayList<String>();
				for(int i = 0, sz = changeNotes.size(); i < sz; i ++) {
					changeNotesTemp.add((i + 1) + "、" + changeNotes.get(i));
				}
				resultNoteFileName = changeDir.getAbsolutePath() + File.separator + fileName + ".result.note";
				FileUtil.writeLines(changeNotesTemp, resultNoteFileName, CharsetUtil.UTF_8);
				LOG.debug("write result note file {}", resultNoteFileName);
			}
			
			// 复制文件
			codeDir = codeDirMap.get(fileName);
			if(StrUtil.isBlank(codeDir)) {
				LOG.warn("Code dir is null: {}", fileName);
				continue;
			}
			
			// 建目录、复制文件、删除空目录
			for(String changeFilePath : changeFilePaths) {
				changeFile = new File(dir + codeDir + changeFilePath);
				if(!FileUtil.exist(changeFile)) {
					LOG.warn("File no exist: {}", changeFile.getAbsolutePath());
					continue;
				}
				
				if(FileUtil.isDirectory(changeFile)) {
					LOG.warn("File is directory: {}", changeFile.getAbsolutePath());
					continue;
				}
				
				FileUtil.copy(changeFile.getAbsolutePath(), changeDir.getAbsolutePath() + File.separator + changeFilePath, true);
				sum ++;
				LOG.debug("Copy file : {}", changeFile.getAbsolutePath());
			}
		}
		
		LOG.debug("Hanlde file count : {}", sum);
	}

	/**
	 * 创建差异目录
	 *
	 * @param dir
	 * @return 
	 */
	private static File createChangeDir(String dir) {
		File change = new File(dir + "change");
		if(FileUtil.exist(change)) {
			FileUtil.del(change);
		}
		FileUtil.mkdir(change);
		
		LOG.debug("Change file : {}", change.getAbsolutePath());
		return change;
	}
	
	/**
	 * 日志对象
	 */
	private final static Log LOG = LogFactory.get();
	
}
