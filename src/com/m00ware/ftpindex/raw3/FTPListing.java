package com.m00ware.ftpindex.raw3;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.util.*;

import com.m00ware.ftpindex.*;

/**
 * 
 * @author Wooden
 */
public class FTPListing {
    private List<FTP> ftps;
    private Map<FTP, Integer> ftpsPos;
    private Extent extent;
    private RawFilesDB3 db;

    public FTPListing(RawFilesDB3 db) {
        this(db, null);
    }

    public FTPListing(RawFilesDB3 db, Extent extent) {
        this.ftps = new LinkedList<FTP>();
        this.ftpsPos = new HashMap<FTP, Integer>();
        this.extent = extent;
        this.db = db;
        if (this.extent != null) {
            try {
                load();
            } catch (BufferUnderflowException e) {
                System.err.println("Looks like things are going crazy 'round here, we probably won't recover, prepare for a nice explosion... [Could not load FTPLisiting : BUE]");
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("Looks like things are going crazy 'round here, we probably won't recover, prepare for a nice explosion... [Could not load FTPLisiting : IOE]");
                e.printStackTrace();
            }
        }
    }

    public synchronized void addFTP(FTP ftp) throws IOException {
        ftps.add(ftp);
        ByteBuffer buffer = this.db.getBufferPool().getBuffer(64);
        writeFTPtoBuffer(ftp, buffer);
        buffer.flip();
        if (this.extent == null) {
            this.extent = this.db.getListingSpaceManager().allocExtent(32 * 50);
            this.extent.skip(4); // reserve space for counter
        }
        ftpsPos.put(ftp, this.extent.getOccupied());
        this.extent = this.extent.append(buffer, 32 * 10);
        this.db.getBufferPool().returnBuffer(buffer);
        this.extent.writeInt(this.ftps.size(), 0);
    }

    public synchronized void updateFTP(FTP ftp) throws IOException {
        int offset = ftpsPos.get(ftp);
        ByteBuffer buffer = this.db.getBufferPool().getBuffer(64);
        writeFTPtoBuffer(ftp, buffer);
        this.extent.writeBuffer(buffer, offset);
        this.db.getBufferPool().returnBuffer(buffer);
    }

    public List<FTP> getFtps() {
        return ftps;
    }

    private void load() throws IOException, BufferUnderflowException {
        ByteBuffer buffer = this.extent.readFullBuffer();
        int start = buffer.position();
        int size = buffer.getInt();
        for (int i = 0; i < size; i++) {
            ftps.add(readFTPfromBuffer(buffer));
        }
        this.extent.skip(buffer.position() - start);
        this.db.getBufferPool().returnBuffer(buffer);
    }

    private void writeFTPtoBuffer(FTP ftp, ByteBuffer buffer) {
        byte[] address = ftp.getAddress().getAddress();
        buffer.put((byte) address.length);
        buffer.put(address);
        buffer.putShort((short) ftp.getPort());
        buffer.putInt(ftp.getCurrentIndexingId());
        RawFilesDB3.putString(buffer, ftp.getPathSeparator());
        buffer.putLong(ftp.getLastIndexing().getTime());
        buffer.putLong(ftp.getOutdatedLimit());
    }

    private FTP readFTPfromBuffer(ByteBuffer buffer) {
        int addrSz = buffer.get();
        assert (addrSz == 4 || addrSz == 6);
        byte[] addressBytes = new byte[addrSz];
        buffer.get(addressBytes);
        InetAddress address = null;
        try {
            address = InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        int port = buffer.getShort() & 0xffff;
        int indexingId = buffer.getInt();
        String separator = RawFilesDB3.getString(buffer);
        Date lastIndexing = new Date(buffer.getLong());
        long outDate = buffer.getLong();
        int rootPtr = buffer.getInt();
        Node root = db.getNode(rootPtr);
        assert (root instanceof Directory);
        FTP ftp = new FTP(db, address, port, (Directory) root, indexingId, lastIndexing);
        ftp.setOutdatedLimit(outDate);
        ftp.setPathSeparator(separator);
        return ftp;
    }
}