package com.m00ware.ftpindex.search;

import java.util.List;

import com.m00ware.ftpindex.Node;

public class SearchResults
{
	private List<Node> nodes;
	private int totalResults;
	
	public SearchResults(List<Node> nodes, int totalResults)
	{
		this.nodes = nodes;
		this.totalResults = totalResults;
	}
	
	public List<Node> getNodes()
	{
		return nodes;
	}
	
	public int getTotalResults()
	{
		return totalResults;
	}
}
