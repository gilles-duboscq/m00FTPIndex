package com.m00ware.ftpindex;

import java.lang.ref.SoftReference;
import java.util.*;

/**
 * @author Wooden
 * 
 */
public class SoftDirectory extends Directory {
    protected SoftReference<List<Node>> softChildren;

    public SoftDirectory(Directory parent, String name, long size) {
        this(parent, name);
    }

    public SoftDirectory(Directory parent, String name) {
        this(parent, name, new LinkedList<Node>());
    }

    public SoftDirectory(Directory parent, String name, List<Node> children) {
        super(parent, name, 0);
        this.softChildren = new SoftReference<List<Node>>(children);
    }

    public SoftDirectory(FTP ftp, String name) {
        this(ftp, name, new LinkedList<Node>());
    }

    public SoftDirectory(FTP ftp, String name, List<Node> children) {
        super(ftp, name);
        this.softChildren = new SoftReference<List<Node>>(children);
    }

    @Override
    public synchronized List<Node> getChildren(boolean load) {
        List<Node> list = softChildren.get();
        if (list == null && load) {
            list = this.getFtp().getDb().getChildren(this);
            this.softChildren = new SoftReference<List<Node>>(list);
        }
        return list;
    }
}
