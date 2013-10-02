package com.m00ware.ftpindex.search;

import com.m00ware.ftpindex.Node;

public class SearchResult implements Comparable<SearchResult> {
    private int score;
    private Node node;

    public SearchResult(Node node, int score) {
        this.node = node;
        this.score = score;
    }

    public Node getNode() {
        return node;
    }

    public int getScore() {
        return score;
    }

    @Override
    public int compareTo(SearchResult o) {
        boolean thisUp = this.getNode().getFtp().isUp();
        boolean thatUp = o.getNode().getFtp().isUp();
        if (thisUp && !thatUp) {
            return -1;
        }
        if (!thisUp && thatUp) {
            return 1;
        }
        return o.getScore() - this.score;
    }

}