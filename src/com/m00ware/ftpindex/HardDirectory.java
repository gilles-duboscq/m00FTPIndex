package com.m00ware.ftpindex;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Wooden
 *
 */
public class HardDirectory extends Directory {
	protected List<Node> children;
	
	public HardDirectory(Directory parent, String name, long size)
	{
		this(parent, name);
	}
	
	public HardDirectory(Directory parent, String name)
	{
		this(parent, name, new LinkedList<Node>());
	}

	public HardDirectory(Directory parent, String name, List<Node> children)
	{
		super(parent, name, 0);
		this.children = children;
	}
	
	public HardDirectory(FTP ftp, String name)
	{
		this(ftp, name, new LinkedList<Node>());
	}
	
	public HardDirectory(FTP ftp, String name, List<Node> children)
	{
		super(ftp, name);
		this.children = children;
	}

	public synchronized List<Node> getChildren(boolean load)
	{
		if(children == null && load){
			children = this.getFtp().getDb().getChildren(this);
		}
		return children;
	}
}
