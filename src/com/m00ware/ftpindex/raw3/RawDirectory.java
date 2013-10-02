package com.m00ware.ftpindex.raw3;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.Date;

import com.m00ware.ftpindex.*;

/**
 * @author Wooden
 * 
 */
public class RawDirectory extends SoftDirectory {
    private static final int LISTING_PTR_OFFSET = 0x05;
    private static final int NODE_SIZE_NO_NAME = 0x1B;
    private SoftReference<NodeListing> softListing;
    private Position position;

    /**
     * @param parent
     * @param name
     * @param size
     */
    public RawDirectory(Directory parent, String name, long size) {
        this(parent, name, size, null, null);
    }

    public RawDirectory(Directory parent, String name, long size, Position position, Extent listingExtent) {
        super(parent, name, size);
        this.position = position;
        NodeListing listing = null;
        if (listingExtent != null) {
            listing = new NodeListing(listingExtent, this);
        }
        this.softListing = new SoftReference<NodeListing>(listing);
    }

    /**
     * @param ftp
     * @param name
     */
    public RawDirectory(FTP ftp, String name) {
        super(ftp, name);
    }

    public NodeListing getListing() throws IOException {
        return this.getLisiting(true);
    }

    public synchronized NodeListing getLisiting(boolean load) throws IOException {
        NodeListing listing = this.softListing.get();
        if (listing == null && load) {
            Block block = this.position.getBlock();
            int offset = this.position.getOffset() + LISTING_PTR_OFFSET; // listing ptr offset
            Extent extent = block.getExtent(offset);
            listing = new NodeListing(extent, this);
        }
        return listing;
    }

    private RawFilesDB3 getDb() {
        return (RawFilesDB3) this.getFtp().getDb();
    }

    public static RawDirectory readNode(Position position) throws IOException {
        Block block = position.getBlock();
        int offset = position.getOffset();
        ByteBuffer buffer = block.readBufferFromBlock(offset, NODE_SIZE_NO_NAME);
        int flags = buffer.get() & 0xff;
        int parentPtr = buffer.getInt();
        int listingPtr = buffer.getInt();
        long size = buffer.getLong(); // TODO update node layout & constants
        long dateTimestamp = buffer.getLong();
        long lastSeenTimestamp = buffer.getLong();
        int strLen = buffer.getShort() & 0xffff;
        block.getDb().getBufferPool().returnBuffer(buffer);
        buffer = block.readBufferFromBlock(offset, strLen);
        String name = RawFilesDB3.getString(buffer, strLen);
        Directory parent = (Directory) block.getDb().getNode(parentPtr);
        Extent extent = block.getDb().getExtent(listingPtr);
        RawDirectory dir = new RawDirectory(parent, name, size, position, extent);
        dir.setDate(new Date(dateTimestamp));
        dir.setLastSeen(new Date(lastSeenTimestamp));
        // TODO set flags
        return dir;
    }
}