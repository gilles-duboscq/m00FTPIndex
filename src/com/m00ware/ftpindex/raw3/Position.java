package com.m00ware.ftpindex.raw3;

public class Position {
    private final Block block;
    private final int offset;

    public Position(Block block, int offset) {
        this.block = block;
        this.offset = offset;
    }

    public Block getBlock() {
        return block;
    }

    public int getOffset() {
        return offset;
    }

    public int getAbsolutePosition() {
        return block.getPosition() + this.offset;
    }
}