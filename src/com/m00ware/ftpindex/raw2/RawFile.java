package com.m00ware.ftpindex.raw2;

import com.m00ware.ftpindex.Directory;
import com.m00ware.ftpindex.File;

/**
 * @author Wooden
 *
 */
public class RawFile extends File
{
	private int entryPos;
	private int entrySize;
	private int searchEntryPos;
	
	public RawFile(Directory parent, String name, long size)
	{
		this(parent, name, size, -1, -1);
	}
	
	public RawFile(Directory parent, String name, long size, int entryPos, int entrySize)
	{
		super(parent, name, size);
		this.entryPos = entryPos;
		this.entrySize = entrySize;
	}
	
	public int getEntryPos()
	{
		return entryPos;
	}
	
	public void setEntryPos(int entryPos)
	{
		this.entryPos = entryPos;
	}
	
	public int getEntrySize()
	{
		return entrySize;
	}
	
	public void setEntrySize(int entrySize)
	{
		this.entrySize = entrySize;
	}
	public int getSearchEntryPos()
	{
		return searchEntryPos;
	}
	public void setSearchEntryPos(int searchEntryPos)
	{
		this.searchEntryPos = searchEntryPos;
	}
}
