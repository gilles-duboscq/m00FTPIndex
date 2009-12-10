package com.m00ware.ftpindex.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.m00ware.ftpindex.Node;

/**
 * @author Wooden
 *
 */
public abstract class CachingSearchIndex implements SearchIndex
{
	private HashMap<String, CachedSearch> cache;
	private int maxCacheSize;
	
	public CachingSearchIndex()
	{
		this.cache = new HashMap<String, CachedSearch>();
		this.maxCacheSize = 30;
	}

	/* (non-Javadoc)
	 * @see com.m00ware.ftpindex.search.SearchIndex#search(java.lang.String, int, int)
	 */
	@Override
	public SearchResults search(String search, int page, int resultsPerPage)
	{
		if(page < 1)
			throw new IllegalArgumentException();
		CachedSearch cached = cache.get(search);
		if(cached == null){
			List<String> searchTerms = new LinkedList<String>();
			int idx = search.indexOf('"');
			while(idx >= 0){
				int idx2 = search.indexOf('"', idx+1);
				for(String term : search.substring(0, idx).split("\\s")){
					term = term.toLowerCase().trim();
					if(term.length() > 0)
						searchTerms.add(term);
				}
				searchTerms.add(search.substring(idx+1, idx2).toLowerCase());
				search = search.substring(idx2+1, search.length());
				idx = search.indexOf('"');
			}
			for(String term : search.split("\\s")){
				term = term.toLowerCase().trim();
				if(term.length() > 0)
					searchTerms.add(term);
			}
			cached = new CachedSearch(this.doSearch(searchTerms), search);
			this.cache(cached);
			
		}
		return getPagedResults(cached, page, resultsPerPage);
	}
	
	public void setMaxCacheSize(int maxCacheSize)
	{
		this.maxCacheSize = maxCacheSize;
	}
	
	public int getMaxCacheSize()
	{
		return maxCacheSize;
	}
	
	public static List<Node> sortSearchResults(List<SearchResult> results){
		ArrayList<SearchResult> resultsArray = new ArrayList<SearchResult>(results);
		Collections.sort(resultsArray);
		ArrayList<Node> resultNodesArray = new ArrayList<Node>(resultsArray.size());
		for(SearchResult sr : resultsArray){
			resultNodesArray.add(sr.getNode());
		}
		return resultNodesArray;
	}
	
	protected static SearchResults getPagedResults(CachedSearch search, int page, int resultsPerPage){
		int size = search.getResults().size();
		int start = (page-1)*resultsPerPage;
		int end = Math.min(size, page*resultsPerPage);
		if(start >= end){
			return new SearchResults(new ArrayList<Node>(0),0);
		}
		return new SearchResults(search.getResults().subList(start, end), size);
	}
	
	protected synchronized void cache(CachedSearch cached)
	{
		if(this.cache.size()+1 > maxCacheSize){
			CachedSearch oldest = null;
			long oldestTimestamp = Long.MAX_VALUE;
			for(CachedSearch cs : this.cache.values()){
				if(cs.timestamp < oldestTimestamp){
					oldest = cs;
					oldestTimestamp = oldest.getTimestamp();
				}
			}
			if(oldest != null){
				this.cache.remove(oldest.getSearch());
			}
		}
		this.cache.put(cached.getSearch(), cached);
	}
	
	protected abstract List<Node> doSearch(List<String> searchTerms);

}
