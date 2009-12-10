package com.m00ware.ftpindex;

import java.util.Calendar;

import com.m00ware.ftpindex.Node.NodeType;

/**
 * @author Wooden
 *
 */
public class TempChild
{
	private String name;
	private Calendar date;
	private long size;
	private NodeType type;
	
	public TempChild(String name, Calendar date, long size, NodeType type)
	{
		this.name = name;
		this.date = date;
		this.size = size;
		this.type = type;
	}
	
	public Calendar getDate()
	{
		return date;
	}
	
	public String getName()
	{
		return name;
	}
	
	public long getSize()
	{
		return size;
	}
	
	public NodeType getType()
	{
		return type;
	}
}
