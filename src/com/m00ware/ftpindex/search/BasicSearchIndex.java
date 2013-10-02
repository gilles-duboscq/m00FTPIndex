package com.m00ware.ftpindex.search;

import java.util.*;

import com.m00ware.ftpindex.*;

/**
 * @author Wooden
 * 
 */
public class BasicSearchIndex extends CachingSearchIndex {
    private FilesDB db;

    public BasicSearchIndex(FilesDB db) {
        this.db = db;
    }

    @Override
    public List<Node> doSearch(List<String> searchTerms) {

        List<SearchResult> results = new LinkedList<SearchResult>();
        for (FTP ftp : db.getFTPs()) {
            explore(ftp.getRoot(), results, searchTerms);
        }
        return CachingSearchIndex.sortSearchResults(results);
    }

    private void explore(Directory directory, List<SearchResult> results, List<String> search) {
        List<Directory> toExplore = new LinkedList<Directory>();
        for (Node node : directory.getChildren()) {
            if (node instanceof Directory) {
                toExplore.add((Directory) node);
            }
            int score = 0;
            String name = node.getName().toLowerCase();
            for (String term : search) {
                if (name.contains(term)) {
                    score++;
                }
            }
            if (score > 0) {
                results.add(new SearchResult(node, score));
            }
        }
        for (Directory dir : toExplore) {
            explore(dir, results, search);
        }
    }
}