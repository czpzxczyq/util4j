package net.jueb.util4j.hotSwap.classSources;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jueb.util4j.file.FileUtil;

public class DefaultClassSource implements ClassSource,FileAlterationListener{

	protected final Logger log=LoggerFactory.getLogger(getClass());
	public static final long DEFAULT_UPDATE_INTERVAL=TimeUnit.SECONDS.toMillis(10);
	private final ReentrantReadWriteLock rwLock=new ReentrantReadWriteLock();
	private final long updateInterval;
	private final Set<URI> classDirs=new HashSet<>();
	private final Set<URI> jarDirs=new HashSet<>();
	private final Set<URI> jarFiles=new HashSet<>();
	private final Set<ClassSourceListener> listeners=new HashSet<>();
	private final List<ClassSourceInfo> classSources=new ArrayList<>();
	private FileAlterationMonitor monitor;
	
	public DefaultClassSource(long updateInterval) throws Exception {
		if(updateInterval<=1000)
		{
			throw new IllegalArgumentException("updateInterval low 1000,updateInterval="+updateInterval);
		}
		this.updateInterval=updateInterval;
		init();
	}
	
	private void init() throws Exception
	{
		scanClassSources();
		monitor=new FileAlterationMonitor(updateInterval);
		monitor.start();
	}
	
	protected IOFileFilter buildFilterBySuffixs(String directory,String ...suffixs)
	{
	    IOFileFilter iOFileFilter=FileFilterUtils.directoryFileFilter(); //子目录变化
		for(String suffix:suffixs)
		{//后缀过滤器
			iOFileFilter=FileFilterUtils.or(iOFileFilter,FileFilterUtils.suffixFileFilter(suffix));
		}
	    return iOFileFilter;
	}
	
	protected IOFileFilter buildFilterByNames(String directory,String ...fileName)
	{
	    IOFileFilter iOFileFilter=FileFilterUtils.directoryFileFilter(); //子目录变化
		for(String name:fileName)
		{//名字过滤器
			iOFileFilter=FileFilterUtils.or(iOFileFilter,FileFilterUtils.nameFileFilter(name));
		}
	    return iOFileFilter;
	}
	
	/**
	 * 监视目录
	 * @param directory
	 * @param filter
	 */
	protected void monitorDir(String directory,IOFileFilter filter)
	{
		FileAlterationObserver observer=new FileAlterationObserver(directory,filter);
		observer.checkAndNotify();//检查一次用于刷新时间戳,否则后加入monitor会出现大量文件创建事件
		observer.addListener(this);
		monitor.addObserver(observer);
	}

	class ClassSourceInfoImpl implements ClassSourceInfo
	{
		private final  URL url;
		List<String> classNames;
		public ClassSourceInfoImpl(URL url, List<String> classNames) {
			super();
			this.url = url;
			this.classNames = classNames;
		}

		@Override
		public URL getUrl() {
			return url;
		}

		@Override
		public List<String> getClassNames() {
			return classNames;
		}
	}
	
	/**
	 * 扫描类资源到缓存
	 */
	public void scanClassSources()
	{
		rwLock.writeLock().lock();
		boolean success=false;
		try {
			List<ClassSourceInfo> infos=new ArrayList<>();
			for(URI uri:classDirs)
			{
				if(!validationDir(uri))
				{
					continue;
				}
				File file=new File(uri);
				HashMap<String, File> map=FileUtil.findClassByDirAndSub(file);
				ClassSourceInfo info=new ClassSourceInfoImpl(uri.toURL(), new ArrayList<>(map.keySet()));
				infos.add(info);
			}
			Map<URI,JarFile> allJarFiles=new HashMap<>();
			for(URI uri:jarDirs)
			{
				if(!validationDir(uri))
				{
					continue;
				}
				File file=new File(uri);
				Set<File> jars=FileUtil.findJarFileByDirAndSub(file);
				for(File f:jars)
				{
					allJarFiles.put(f.toURI(), new JarFile(f));
				}
			}
			for(URI uri:jarFiles)
			{
				if(!validationJar(uri))
				{
					continue;
				}
				File f=new File(uri);
				allJarFiles.put(f.toURI(), new JarFile(f));
			}
			for(Entry<URI, JarFile> f:allJarFiles.entrySet())
			{
				URI uri=f.getKey();
				JarFile jar=f.getValue();
				try {
					Map<String,JarEntry> map=FileUtil.findClassByJar(jar);
					ClassSourceInfo info=new ClassSourceInfoImpl(uri.toURL(), new ArrayList<>(map.keySet()));
					infos.add(info);
				} finally {
					jar.close();
				}
			}
			classSources.clear();
			classSources.addAll(infos);
			success=true;
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		}finally {
			rwLock.writeLock().unlock();
		}
		if(success)
		{
			onScaned();
			for(ClassSourceListener l:listeners)
			{
				try {
					l.onSourcesFind();
				} catch (Exception e) {
				}
			}
		}
	}
	
	protected void onScaned()
	{
		
	}
	
	protected boolean validationJar(URI uri)
	{
		boolean result=false;
		try {
			File file= new File(uri.toURL().getFile());
			result=file.exists() && file.isFile() && file.getName().endsWith(".jar");
		} catch (Exception e) {
		}
		return result;
	}
	
	protected boolean validationDir(URI uri)
	{
		boolean result=false;
		try {
			File file=new File(uri.toURL().getFile());
			result=file.exists() && file.isDirectory();
		} catch (Exception e) {
		}
		return result;
	}
	
	public void addClassDir(URI uri)
	{
		if(!validationDir(uri))
		{
			log.error("unSupprot uri:"+uri.getPath());
			return ;
		}
		rwLock.writeLock().lock();
		try {
			if(classDirs.contains(uri))
			{
				log.error("repeat add uri:"+uri);
				return ;
			}
			File file=new File(uri.getPath());
			String directory=file.getPath();
			String suffix=".class";
			monitorDir(directory,buildFilterBySuffixs(directory, suffix));
			classDirs.add(uri);
		}
		finally {
			rwLock.writeLock().unlock();
		}
		scanClassSources();
	}
	
	public void addJarDir(URI uri)
	{
		if(!validationDir(uri))
		{
			log.error("unSupprot uri:"+uri);
			return ;
		}
		rwLock.writeLock().lock();
		try {
			if(jarDirs.contains(uri))
			{
				log.error("repeat add uri:"+uri);
				return ;
			}
			File file=new File(uri.getPath());
			String directory=file.getPath();
			String suffix=".jar";
			monitorDir(directory,buildFilterBySuffixs(directory, suffix));
			jarDirs.add(uri);
		}
		finally {
			rwLock.writeLock().unlock();
		}
		scanClassSources();
	}
	
	public void addJar(URI uri)
	{
		if(!validationJar(uri))
		{
			log.error("unSupprot uri:"+uri);
			return ;
		}
		rwLock.writeLock().lock();
		try {
			if(jarFiles.contains(uri))
			{
				log.error("repeat add uri:"+uri);
				return ;
			}
			File file=new File(uri.getPath());
			String directory=file.getParentFile().getPath();
			String name=file.getName();
			monitorDir(directory,buildFilterByNames(directory, name));
			jarFiles.add(uri);
		}
		finally {
			rwLock.writeLock().unlock();
		}
		scanClassSources();
	}

	@Override
	public List<ClassSourceInfo> getClassSources() {
		rwLock.readLock().lock();
		try {
			return Collections.unmodifiableList(classSources);
		}finally {
			rwLock.readLock().unlock();
		}
	}

	@Override
	public void addEventListener(ClassSourceListener listener) {
		Objects.requireNonNull(listener);
		rwLock.readLock().lock();
		try {
			listeners.add(listener);
		}finally {
			rwLock.readLock().unlock();
		}
	}

	@Override
	public void removeEventListener(ClassSourceListener listener) {
		Objects.requireNonNull(listener);
		rwLock.readLock().lock();
		try {
			listeners.remove(listener);
		}finally {
			rwLock.readLock().unlock();
		}
	}
	
	@Override
	public void onDirectoryChange(File paramFile) {
	}
	@Override
	public void onDirectoryCreate(File paramFile) {
	}
	@Override
	public void onDirectoryDelete(File paramFile) {
	}
	@Override
	public void onFileChange(File paramFile) {
		log.debug("onFileChange-->scanClassSources,fileName:"+paramFile.getName());
		scanClassSources();
	}
	@Override
	public void onFileCreate(File paramFile) {
		log.debug("onFileCreate-->scanClassSources,fileName:"+paramFile.getName());
		scanClassSources();
	}
	@Override
	public void onFileDelete(File paramFile) {
		log.debug("onFileDelete-->scanClassSources,fileName:"+paramFile.getName());
		scanClassSources();
	}
	@Override
	public void onStart(FileAlterationObserver paramFileAlterationObserver) {
	}
	@Override
	public void onStop(FileAlterationObserver paramFileAlterationObserver) {
	}
}
