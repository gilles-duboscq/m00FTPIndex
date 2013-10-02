package com.m00ware.ftpindex.raw;

import com.m00ware.ftpindex.Directory;
import com.m00ware.ftpindex.FTP;
import com.m00ware.ftpindex.HardDirectory;

/**
 * @author Wooden
 *
 */
public class RawDirectory extends HardDirectory
{
	private int entryPos;
	private int entrySize;
	private int childListPos;
	private int searchEntryPos;

	/**
	 * @param parent
	 * @param name
	 */
	public RawDirectory(Directory parent, String name, int childListPos, int entryPos, int entrySize)
	{
		super(parent, name, null);
		this.childListPos = childListPos;
		this.entryPos = entryPos;
		this.entrySize = entrySize;
	}

	public RawDirectory(Directory parent, String name)
	{
		super(parent, name);
		this.childListPos = -1;
		this.entryPos = -1;
		this.entrySize = -1;
	}

	/**
	 * @param ftp
	 * @param name
	 */
	public RawDirectory(FTP ftp, String name)
	{
		super(ftp, name);
		this.childListPos = -1;
		this.entrySize = -1;
		this.entryPos = -1;
	}
	
	public int getChildListPos()
	{
		return childListPos;
	}
	
	public int getEntryPos()
	{
		return entryPos;
	}

	public void setChildListPos(int childListPos)
	{
		this.childListPos = childListPos;
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
