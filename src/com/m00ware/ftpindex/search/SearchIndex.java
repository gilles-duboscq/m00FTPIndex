package com.m00ware.ftpindex.search;

public interface SearchIndex {
    public SearchResults search(String search, int page, int resultsPerPage);
}