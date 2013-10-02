package com.m00ware.ftpindex.raw3;

import java.nio.ByteBuffer;

/**
 * @author Wooden
 * 
 */
public class WritableChunk {
    private ByteBuffer buffer;
    private int position;

    public WritableChunk(ByteBuffer buffer, int position) {
        this.buffer = buffer;
        this.position = position;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public int getPosition() {
        return position;
    }
}