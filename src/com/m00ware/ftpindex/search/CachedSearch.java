package com.m00ware.ftpindex.search;

import java.util.List;

import com.m00ware.ftpindex.Node;

public class CachedSearch{
	private List<Node> results;
	long timestamp;
	private String search;
	
	public CachedSearch(List<Node> results, String search)
	{
		this.search = search;
		this.results = results;
		this.timestamp = System.currentTimeMillis();
	}
	
	public List<Node> getResults()
	{
		return results;
	}
	
	public String getSearch()
	{
		return search;
	}
	
	public long getTimestamp()
	{
		return timestamp;
	}
}