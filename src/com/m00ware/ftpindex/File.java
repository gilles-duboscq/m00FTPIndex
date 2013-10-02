package com.m00ware.ftpindex;

/**
 * @author Wooden
 * 
 */
public class File extends Node {

    public File(Directory parent, String name, long size) {
        super(parent, name, size);
    }

    @Override
    public void setSize(long size) {
        if (size != this.size) {
            getParent().forwardDeltaSize(size - this.size);
            this.size = size;
        }
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.file;
    }

    @Override
    public String toString() {
        return "File : " + super.toString();
    }
}
