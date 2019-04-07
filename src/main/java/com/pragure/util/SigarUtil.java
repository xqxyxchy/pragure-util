package com.pragure.util;

import java.io.File;
import java.text.DecimalFormat;

import org.hyperic.sigar.CpuInfo;
import org.hyperic.sigar.CpuPerc;
import org.hyperic.sigar.FileSystem;
import org.hyperic.sigar.FileSystemUsage;
import org.hyperic.sigar.Mem;
import org.hyperic.sigar.NetInterfaceConfig;
import org.hyperic.sigar.NetInterfaceStat;
import org.hyperic.sigar.OperatingSystem;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.hyperic.sigar.Swap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Resources;

/** 
 * <pre>
 * 硬件资源获取工具
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
public class SigarUtil {

	private static Logger LOGGER = LoggerFactory.getLogger(SigarUtil.class);
	private static Sigar sigar;
	
	static {
		try {
			String file = Resources.getResource("sigar/.sigar_shellrc").getFile();
			File classPath = new File(file).getParentFile();

			String path = System.getProperty("java.library.path");

			path += System.getProperty("path.separator") + classPath.getCanonicalPath();
			System.setProperty("java.library.path", path);

			sigar = new Sigar();
		} catch (Exception e) {
			LOGGER.error("初始化java.library.path失败：", e);
		}
	}
	
	public static void monitor() throws Exception {
		os();
		disk();
		cpu();
		memory();
		net();
	}

	public static void os() {
		// 取当前操作系统的信息
		OperatingSystem OS = OperatingSystem.getInstance();
		// 操作系统内核类型如： 386、486、586等x86
		LOGGER.debug("=====================================================>");
		LOGGER.debug("操作系统:    " + OS.getArch());
		LOGGER.debug("操作系统CpuEndian():    " + OS.getCpuEndian());//
		LOGGER.debug("操作系统DataModel():    " + OS.getDataModel());//
		// 系统描述
		LOGGER.debug("操作系统的描述:    " + OS.getDescription());
		// 操作系统类型
		LOGGER.debug("OS.getName():    " + OS.getName());
		LOGGER.debug("OS.getPatchLevel():    " + OS.getPatchLevel());//
		// 操作系统的卖主
		LOGGER.debug("操作系统的卖主:    " + OS.getVendor());
		// 卖主名称
		LOGGER.debug("操作系统的卖主名:    " + OS.getVendorCodeName());
		// 操作系统名称
		LOGGER.debug("操作系统名称:    " + OS.getVendorName());
		// 操作系统卖主类型
		LOGGER.debug("操作系统卖主类型:    " + OS.getVendorVersion());
		// 操作系统的版本号
		LOGGER.debug("操作系统的版本号:    " + OS.getVersion());
	}

	private static String format(Number number) {
		return format(number, "#.00");
	}
	
	private static String format(Number number, String fmt) {
		DecimalFormat df = new DecimalFormat(fmt);
		return df.format(number);
	}
	
	public static Result cpuUsedOver(double usedLimit) throws SigarException {
		Result result = new Result(Result.CPU);
		CpuPerc cpuPerc = sigar.getCpuPerc();
		double used = 1.0D - cpuPerc.getIdle();
		result.setPercent(format(used * 100D));
		
		if(used >= usedLimit) {
			LOGGER.debug("cpu used \"{}%\".", format(used * 100D));
			return result;
		}
		
		return result;
	}
	
	public static void cpu() throws SigarException {
		LOGGER.debug("=====================================================>");
		// CPU的总量（单位：HZ）及CPU的相关信息
		CpuInfo infos[] = sigar.getCpuInfoList();
		CpuPerc cpuList[] = null;
		cpuList = sigar.getCpuPercList();
		for (int i = 0; i < infos.length; i++) {// 不管是单块CPU还是多CPU都适用
			CpuInfo info = infos[i];
			LOGGER.debug("第" + (i + 1) + "块CPU信息");
			LOGGER.debug("CPU的总量MHz:    " + info.getMhz());// CPU的总量MHz
			LOGGER.debug("CPU生产商:    " + info.getVendor());// 获得CPU的卖主，如：Intel
			LOGGER.debug("CPU类别:    " + info.getModel());// 获得CPU的类别，如：Celeron
			LOGGER.debug("CPU缓存数量:    " + info.getCacheSize());// 缓冲存储器数量
			// 当前CPU的用户使用率、系统使用率、当前等待率、当前空闲率、总的使用率
			printCpuPerc(cpuList[i]);
		}
	}

	public static void printCpuPerc(CpuPerc cpu) {
		LOGGER.debug("------------------------------------------------------");
		LOGGER.debug("CPU用户使用率:    " + CpuPerc.format(cpu.getUser()));// 用户使用率
		LOGGER.debug("CPU系统使用率:    " + CpuPerc.format(cpu.getSys()));// 系统使用率
		LOGGER.debug("CPU当前等待率:    " + CpuPerc.format(cpu.getWait()));// 当前等待率
		LOGGER.debug("CPU当前错误率:    " + CpuPerc.format(cpu.getNice()));// 当前错误率
		LOGGER.debug("CPU当前空闲率:    " + CpuPerc.format(cpu.getIdle()));// 当前空闲率
		LOGGER.debug("CPU总的使用率:    " + CpuPerc.format(cpu.getCombined()));// 总的使用率
	}

	public static Result diskUsedOver(double usedLimit) throws SigarException {
		Result result = new Result(Result.DISK);
		FileSystem fslist[] = sigar.getFileSystemList();
		
		for (int i = 0; i < fslist.length; i++) {
			FileSystem fs = fslist[i];
			FileSystemUsage usage = null;
			usage = sigar.getFileSystemUsage(fs.getDirName());
			switch (fs.getType()) {
			case 0: // TYPE_UNKNOWN ：未知
				break;
			case 1: // TYPE_NONE
				break;
			case 2: // TYPE_LOCAL_DISK : 本地硬盘
				double used = usage.getUsePercent();
				result.setPercent(format(used * 100D));
				if(used >= usedLimit) {
					double totalDisk = usage.getTotal() / (1024.0D * 1024.0D);//GB
					double usedDisk = usage.getUsed() / (1024.0D * 1024.0D);//GB
					LOGGER.debug("disk \"{}\", total \"{}GB\", used \"{}GB\", used percent \"{}%\".", 
							fs.getDevName(), 
							format(totalDisk), 
							format(usedDisk), 
							format(used * 100D));
					return result;
				}
				break;
			case 3:// TYPE_NETWORK ：网络
				break;
			case 4:// TYPE_RAM_DISK ：闪存
				break;
			case 5:// TYPE_CDROM ：光驱
				break;
			case 6:// TYPE_SWAP ：页面交换
				break;
			}
		}
		
		return result;
	}
	
	public static void disk() throws Exception {
		LOGGER.debug("=====================================================>");
		// 通过sigar.getFileSystemList()来获得FileSystem列表对象，然后对其进行编历
		FileSystem fslist[] = sigar.getFileSystemList();
		for (int i = 0; i < fslist.length; i++) {
			LOGGER.debug("------------------------------------------------------");
			LOGGER.debug("分区的盘符名称" + i);
			FileSystem fs = fslist[i];
			// 分区的盘符名称
			LOGGER.debug("盘符名称:    " + fs.getDevName());
			// 分区的盘符名称
			LOGGER.debug("盘符路径:    " + fs.getDirName());
			LOGGER.debug("盘符标志:    " + fs.getFlags());//
			// 文件系统类型，比如 FAT32、NTFS
			LOGGER.debug("盘符类型:    " + fs.getSysTypeName());
			// 文件系统类型名，比如本地硬盘、光驱、网络文件系统等
			LOGGER.debug("盘符类型名:    " + fs.getTypeName());
			// 文件系统类型
			LOGGER.debug("盘符文件系统类型:    " + fs.getType());
			FileSystemUsage usage = null;
			usage = sigar.getFileSystemUsage(fs.getDirName());
			switch (fs.getType()) {
			case 0: // TYPE_UNKNOWN ：未知
				break;
			case 1: // TYPE_NONE
				break;
			case 2: // TYPE_LOCAL_DISK : 本地硬盘
				// 文件系统总大小
				LOGGER.debug(fs.getDevName() + "总大小:    " + usage.getTotal() + "KB");
				// 文件系统剩余大小
				LOGGER.debug(fs.getDevName() + "剩余大小:    " + usage.getFree() + "KB");
				// 文件系统可用大小
				LOGGER.debug(fs.getDevName() + "可用大小:    " + usage.getAvail() + "KB");
				// 文件系统已经使用量
				LOGGER.debug(fs.getDevName() + "已经使用量:    " + usage.getUsed() + "KB");
				double usePercent = usage.getUsePercent() * 100D;
				// 文件系统资源的利用率
				LOGGER.debug(fs.getDevName() + "资源的利用率:    " + usePercent + "%");
				break;
			case 3:// TYPE_NETWORK ：网络
				break;
			case 4:// TYPE_RAM_DISK ：闪存
				break;
			case 5:// TYPE_CDROM ：光驱
				break;
			case 6:// TYPE_SWAP ：页面交换
				break;
			}
			LOGGER.debug(fs.getDevName() + "读出：    " + usage.getDiskReads());
			LOGGER.debug(fs.getDevName() + "写入：    " + usage.getDiskWrites());
		}
		return;
	}

	public static Result memUsedOver(double usedLimit) throws SigarException {
		Result result = new Result(Result.MEMORY);
		Mem mem = sigar.getMem();
		double used = mem.getUsed() * 1.0D / mem.getTotal();
		result.setPercent(format(used * 100D));
		if(used >= usedLimit) {
			double totalMem = mem.getTotal() / (1024.0D * 1024.0D * 1024.0D);//GB
			double usedMem = mem.getUsed() / (1024.0D * 1024.0D * 1024.0D);//GB
			LOGGER.debug("memory total \"{}GB\", used \"{}GB\", used percent \"{}%\".", 
					format(totalMem), 
					format(usedMem), 
					format(used * 100D));
			result.setOver(true);
			
			return result;
		}
		
		return result;
	}
	
	public static void memory() throws SigarException {
		LOGGER.debug("=====================================================>");
		// 物理内存信息
		Mem mem = sigar.getMem();
		// 内存总量
		LOGGER.debug("内存总量:    " + mem.getTotal() / 1024L + "KB av");
		// 当前内存使用量
		LOGGER.debug("当前内存使用量:    " + mem.getUsed() / 1024L + "KB used");
		// 当前内存剩余量
		LOGGER.debug("当前内存剩余量:    " + mem.getFree() / 1024L + "KB free");

		// 系统页面文件交换区信息
		Swap swap = sigar.getSwap();
		// 交换区总量
		LOGGER.debug("交换区总量:    " + swap.getTotal() / 1024L + "KB av");
		// 当前交换区使用量
		LOGGER.debug("当前交换区使用量:    " + swap.getUsed() / 1024L + "KB used");
		// 当前交换区剩余量
		LOGGER.debug("当前交换区剩余量:    " + swap.getFree() / 1024L + "KB free");
	}

	public static void net() throws Exception {
		LOGGER.debug("=====================================================>");
		String ifNames[] = sigar.getNetInterfaceList();
		for (int i = 0; i < ifNames.length; i++) {
			LOGGER.debug("------------------------------------------------------");
			String name = ifNames[i];
			NetInterfaceConfig ifconfig = sigar.getNetInterfaceConfig(name);
			LOGGER.debug("网络设备名:    " + name);// 网络设备名
			LOGGER.debug("IP地址:    " + ifconfig.getAddress());// IP地址
			LOGGER.debug("子网掩码:    " + ifconfig.getNetmask());// 子网掩码
			if ((ifconfig.getFlags() & 1L) <= 0L) {
				LOGGER.debug("!IFF_UP...skipping getNetInterfaceStat");
				continue;
			}
			NetInterfaceStat ifstat = sigar.getNetInterfaceStat(name);
			LOGGER.debug(name + "接收的总包裹数:" + ifstat.getRxPackets());// 接收的总包裹数
			LOGGER.debug(name + "发送的总包裹数:" + ifstat.getTxPackets());// 发送的总包裹数
			LOGGER.debug(name + "接收到的总字节数:" + ifstat.getRxBytes());// 接收到的总字节数
			LOGGER.debug(name + "发送的总字节数:" + ifstat.getTxBytes());// 发送的总字节数
			LOGGER.debug(name + "接收到的错误包数:" + ifstat.getRxErrors());// 接收到的错误包数
			LOGGER.debug(name + "发送数据包时的错误数:" + ifstat.getTxErrors());// 发送数据包时的错误数
			LOGGER.debug(name + "接收时丢弃的包数:" + ifstat.getRxDropped());// 接收时丢弃的包数
			LOGGER.debug(name + "发送时丢弃的包数:" + ifstat.getTxDropped());// 发送时丢弃的包数
		}
	}

	public static class Result {
		public static final String CPU = "cpu";
		public static final String MEMORY = "memory";
		public static final String DISK = "disk";
		
		private String key = CPU;
		private boolean over = false;
		private String percent = "0";
		
		public Result() {
			super();
		}
		
		public Result(String key) {
			super();
			this.key = key;
		}
		
		public Result(String key, boolean over, String percent) {
			this(key);
			this.over = over;
			this.percent = percent;
		}
		
		public String getKey() {
			return key;
		}
		public void setKey(String key) {
			this.key = key;
		}
		public boolean isOver() {
			return over;
		}
		public void setOver(boolean over) {
			this.over = over;
		}
		public String getPercent() {
			return percent;
		}
		public void setPercent(String percent) {
			this.percent = percent;
		}
		@Override
		public String toString() {
			return "Result [key=" + key + ", over=" + over + ", percent=" + percent + "]";
		}
	}
	
}
