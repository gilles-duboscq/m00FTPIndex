package com.m00ware.ftpindex;

import java.net.InetAddress;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Wooden
 *
 */
public class FTP
{
	private InetAddress address;
	private int port;
	private int currentIndexingId;
	private Lock indexingLock;
	private boolean indexingInProgress;
	private FilesDB db;
	private String pathSeparator;
	private Date lastIndexing;
	private Directory root;
	private boolean up;
	private long outdatedLimit;
	
	public FTP(FilesDB db, InetAddress address, int port)
	{
		this(db, address, port, null, 0, null);
	}
	
	public FTP(FilesDB db, InetAddress address, int port, Directory root, int currentIndexingId, Date lastIndexing)
	{
		indexingInProgress = false;
		this.db = db;
		this.pathSeparator = "/";
		this.root = root;
		this.address = address;
		this.port = port;
		this.lastIndexing = lastIndexing;
		this.outdatedLimit = TimeUnit.DAYS.toMillis(7);
		if(this.root == null){
			this.root = db.createRootNode(this);
			this.root.setIndexingId(currentIndexingId);
		}else{
			this.root.setFTP(this);
		}
	}
	                    
	public InetAddress getAddress()
	{
		return address;
	}
	
	public int getPort()
	{
		return port;
	}
	
	public void acquireIndexingLock()
	{
		synchronized(this){
			if(indexingLock == null){
				indexingLock = new ReentrantLock();
			}
		}
		indexingLock.lock();
		indexingInProgress = true;
		currentIndexingId++;
		lastIndexing = new Date();
	}
	
	public void releaseIndexingLock()
	{
		indexingInProgress = false;
		indexingLock.unlock();
	}
	
	public boolean isIndexingInProgress()
	{
		return indexingInProgress;
	}
	
	public FilesDB getDb()
	{
		return db;
	}

	public String getPathSeparator()
	{
		return pathSeparator;
	}

	public void setPathSeparator(String pathSeparator)
	{
		this.pathSeparator = pathSeparator;
	}
	
	public Date getLastIndexing()
	{
		return lastIndexing;
	}
	
	public Directory getRoot()
	{
		return root;
	}
	
	public int getCurrentIndexingId()
	{
		return currentIndexingId;
	}
	
	public boolean isUp()
	{
		return up;
	}
	
	public void setUp(boolean up)
	{
		this.up = up;
	}
	
	public String getLastIndexedString()
	{
		if(this.isIndexingInProgress()){
			return this.getUpStatusString()+"Indexing in progres...";
		}
		if(this.getLastIndexing() == null){
			return this.getUpStatusString()+"Never indexed";
		}
		SimpleDateFormat sdf = new SimpleDateFormat("MMM d yyyy HH:mm", Locale.UK);
		return this.getUpStatusString()+sdf.format(this.getLastIndexing());
	}
	
	public String getUpStatusString()
	{
		return up ?  "Up   ":"Down ";
	}
	
	public Node getNodeFromPath(String path){
		String[] pathTerms = path.split(this.getPathSeparator());
		Node node = this.getRoot();
		for(int j = 1; j < pathTerms.length && node != null; j++){
			if(node instanceof Directory){
				Directory dir = (Directory) node;
				node = dir.getChild(pathTerms[j]);
			}else{
				node = null;
			}
		}
		return node;
	}
	
	@Override
	public String toString()
	{
		return this.getAddress().getHostName()+":"+this.getPort();
	}

	public static String formatSize(long size){
		NumberFormat nf = NumberFormat.getInstance(Locale.UK);
		nf.setMaximumFractionDigits(1);
		nf.setMinimumFractionDigits(1);
		nf.setGroupingUsed(false);
		if(size > 0x4000000000000l){
			return nf.format((double)size/0x10000000000l)+"EB";
		}
		if(size > 0x10000000000l){
			return nf.format((double)size/0x10000000000l)+"TB";
		}
		if(size > 0x40000000l){
			return nf.format((double)size/0x40000000l)+"GB";
		}
		if(size > 0x100000l){
			return nf.format((double)size/0x100000l)+"MB";
		}
		if(size > 0x400l){
			return nf.format((double)size/0x400l)+"KB";
		}
		return nf.format(size)+"B";
	}
	
	public long getOutdatedLimit()
	{
		return outdatedLimit;
	}
	
	public void setOutdatedLimit(long outdatedLimit)
	{
		this.outdatedLimit = outdatedLimit;
	}
}
