package com.m00ware.ftpindex.raw;

import java.util.List;

import com.m00ware.ftpindex.Node;
import com.m00ware.ftpindex.search.CachingSearchIndex;

/**
 * @author Wooden
 * 
 */
public class RawSearchIndex extends CachingSearchIndex {
    private RawFilesDB rawDb;

    public RawSearchIndex(RawFilesDB rawDb) {
        super();
        this.rawDb = rawDb;
    }

    @Override
    protected List<Node> doSearch(List<String> search) {
        return this.rawDb.search(search);
    }
}