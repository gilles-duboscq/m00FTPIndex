package com.m00ware.ftpindex.raw;

import com.m00ware.ftpindex.raw.xr.ExtensibleRecord;

/**
 * @author Wooden
 * 
 */
public class FixupExtendedRecord extends Fixup {
    private ExtensibleRecord record;

    /**
     * @param position
     */
    public FixupExtendedRecord(int position, ExtensibleRecord record) {
        super(position);
        this.record = record;
    }

    public ExtensibleRecord getRecord() {
        return record;
    }
}
