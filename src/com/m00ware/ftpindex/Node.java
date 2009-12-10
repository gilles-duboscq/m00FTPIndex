package com.m00ware.ftpindex;

import java.util.Date;

/**
 * @author Wooden
 *
 */
public abstract class Node
{
	public enum NodeType{
		file,
		directory
	}
	
	private Directory parent; //immutable
	private String name; //immutable
	private Date date;
	private int indexingId;
	protected FTP ftp; //immutable
	protected long size;
	protected Date lastSeen;
	private boolean suspect;
	private boolean outDated;
	
	public Node(FTP ftp, String name, long size)
	{
		this((Directory)null, name, size);
		this. ftp = ftp;
	}
	
	public Node(Directory parent, String name, long size)
	{
		this.name = name;
		this.parent = parent;
		this.size = size;
		if(!this.isRoot())
			this.ftp = this.parent.getFtp();
		
	}

	public Directory getParent()
	{
		return parent;
	}

	public String getName()
	{
		return name;
	}
	
	public boolean isRoot()
	{
		return (parent == null);
	}

	public Date getDate()
	{
		return date;
	}

	public void setDate(Date date)
	{
		this.date = date;
	}
	
	public Date getLastSeen()
	{
		return lastSeen;
	}
	
	public void setLastSeen(Date lastSeen)
	{
		this.lastSeen = lastSeen;
	}

	public int getIndexingId()
	{
		return indexingId;
	}
	
	public void incIndexingId()
	{
		this.indexingId++;
	}

	public void setIndexingId(int indexingId)
	{
		this.indexingId = indexingId;
	}
	
	public FTP getFtp()
	{
		return ftp;
	}
	
	//should we cache the path?
	public String getPath(){
		if(this.isRoot()){
			//return this.getName();
			return "";
		}
		return this.getParent().getPath() + this.getFtp().getPathSeparator() + this.getName();
	}
	
	public void setSize(long size)
	{
		this.size = size;
	}
	
	public long getSize()
	{
		return size;
	}

	public boolean isSuspect()
	{
		return suspect;
	}

	public void setSuspect(boolean suspect)
	{
		this.suspect = suspect;
	}
	
	public boolean isOutDated()
	{
		return outDated;
	}
	public void setOutDated(boolean outDated)
	{
		this.outDated = outDated;
	}
	
	public abstract NodeType getNodeType();
	
	@Override
	public String toString()
	{
		return this.getName()+" "+FTP.formatSize(this.size);
	}
}
