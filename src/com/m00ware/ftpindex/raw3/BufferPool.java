package com.m00ware.ftpindex.raw3;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * @author Wooden
 * 
 */
public class BufferPool {
    private static final int POOL_SIZE = 16;
    private static final int BIGGER_POOL_SIZE = 8;
    private static final int BUFFER_SIZE = 2 * 1024;

    private int buffersPtr;
    private ByteBuffer[] buffers;
    private List<ByteBuffer> biggers;

    public BufferPool() {
        buffers = new ByteBuffer[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            buffers[i] = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }
        biggers = new LinkedList<ByteBuffer>();
        buffersPtr = POOL_SIZE - 1;
    }

    public synchronized ByteBuffer getBuffer(int size) {
        if (size <= BUFFER_SIZE && buffersPtr >= 0) {
            return buffers[buffersPtr--];
        } else if (size > BUFFER_SIZE) {
            int deltaMin = Integer.MAX_VALUE;
            ByteBuffer buff = null;
            for (ByteBuffer bb : biggers) {
                int delta = bb.capacity() - size;
                if (size >= 0 && delta < deltaMin) {
                    deltaMin = delta;
                    buff = bb;
                }
            }
            if (buff != null) {
                return buff;
            }
        }
        return ByteBuffer.allocate(size);
    }

    public synchronized void returnBuffer(ByteBuffer buffer) {
        buffer.clear();
        if (buffer.isDirect() && buffer.capacity() == BUFFER_SIZE) {
            buffers[++buffersPtr] = buffer;
        } else if (buffer.capacity() > BUFFER_SIZE && biggers.size() < BIGGER_POOL_SIZE) {
            biggers.add(buffer);
        }
    }
}