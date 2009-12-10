package com.m00ware.ftpindex.web;

/**
 * @author Wooden
 *
 */
public class Tab
{
	private String name;
	private String href;
	
	public Tab(String name, String href)
	{
		this.name = name;
		this.href = href;
	}
	
	public String getHref()
	{
		return href;
	}
	
	public String getName()
	{
		return name;
	}
}
