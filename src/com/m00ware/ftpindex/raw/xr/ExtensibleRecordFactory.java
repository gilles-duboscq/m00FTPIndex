package com.m00ware.ftpindex.raw.xr;

import java.nio.ByteBuffer;

/**
 * @author Wooden
 * 
 */
public interface ExtensibleRecordFactory {
    public ExtensibleRecord createExtensibleRecord(ByteBuffer buffer);

    public ExtensibleRecord createExtensibleRecord();

    public int getRecordId();

    public Class<? extends ExtensibleRecord> getObjectType();
}