package com.m00ware.ftpindex.raw.xr;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * @author Wooden
 * 
 */
public abstract class ExtensibleRecord {
    private static Map<Integer, ExtensibleRecordFactory> factoriesById = new HashMap<Integer, ExtensibleRecordFactory>();
    private static Map<Class<? extends ExtensibleRecord>, ExtensibleRecordFactory> factoriesByType = new HashMap<Class<? extends ExtensibleRecord>, ExtensibleRecordFactory>();

    private int xrPos = -1;
    private int xrSize = -1;

    protected abstract void doWriteToBuffer(ByteBuffer buffer);

    protected abstract int getRecordId();

    public void writeToBuffer(ByteBuffer buffer) {
        buffer.putShort((short) this.getRecordId());
        int start = buffer.position();
        buffer.position(start + 2);
        this.doWriteToBuffer(buffer);
        int size = buffer.position() - start;
        buffer.position(start);
        buffer.putShort((short) size);
        buffer.position(start + size);
    }

    public int getXRPos() {
        return xrPos;
    }

    public int getXRSize() {
        return xrSize;
    }

    public void setXRPos(int xrPos) {
        this.xrPos = xrPos;
    }

    public void setXRSize(int xrSize) {
        this.xrSize = xrSize;
    }

    public static ExtensibleRecord getExtensibleRecord(int id, ByteBuffer buffer) {
        ExtensibleRecordFactory erf = factoriesById.get(id);
        if (erf != null) {
            return erf.createExtensibleRecord(buffer);
        }
        return null;
    }

    public static ExtensibleRecord readExtensibleRecord(ByteBuffer buffer) {
        int id = buffer.getShort() & 0xffff;
        int size = buffer.getShort() & 0xffff;
        int oldLimit = buffer.limit();
        buffer.limit(buffer.position() + size);
        ExtensibleRecord record = ExtensibleRecord.getExtensibleRecord(id, buffer);
        buffer.limit(oldLimit);
        return record;
    }

    @SuppressWarnings("unchecked")
    public static <T> T createExtensibleRecord(Class<T> clazz) {
        ExtensibleRecordFactory erf = factoriesByType.get(clazz);
        if (erf != null) {
            return (T) erf.createExtensibleRecord();
        }
        return null;
    }

    public static void registerExtensibleRecordFactory(ExtensibleRecordFactory factory) {
        factoriesById.put(factory.getRecordId(), factory);
        factoriesByType.put(factory.getObjectType(), factory);
    }
}
