package com.m00ware.ftpindex.raw3;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.*;

import org.xbill.DNS.*;

import com.m00ware.ftpindex.*;
import com.m00ware.ftpindex.Node.NodeType;
import com.m00ware.ftpindex.indexer.IndexerScheduler;
import com.m00ware.ftpindex.raw3.Block.BlockType;
import com.m00ware.ftpindex.search.SearchIndex;

/**
 * XXX : To avoid problems with SoftDirectories/RawDirectories, a reference to the child list & NodeListing should
 * be kept somewhere until the listing is written for the 1st time (to avoid garbage collection before we get a
 * chance to save everything) : maybe a set or something? the fixup could also be a nice place for that :
 * - fixup would waste less cpu cycle inserting/removing from the set
 * - a set would probably waste less memory (even if the minimal consumption is higher (set overhead)??)
 * 
 * @author Wooden
 * 
 */
public class RawFilesDB3 implements FilesDB {
    private static final int STD_BLOCK_SIZE = Block.CLUSTER_SIZE * 128;
    public static final Charset UTF8 = Charset.forName("UTF-8");
    private List<DBEventListener> listeners;
    private Raw3Metrics metrics;
    private FileChannel channel;
    private BufferPool bufferPool;
    private int nextBlockPos;
    private FTPListing ftps;
    private Resolver dnsResolver;

    public RawFilesDB3(java.io.File dbFile) throws FileNotFoundException {
        channel = new RandomAccessFile(dbFile, "rw").getChannel();
        metrics = new Raw3Metrics();
        listeners = new LinkedList<DBEventListener>();
        bufferPool = new BufferPool();
        try {
            dnsResolver = new ExtendedResolver();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addChild(Directory parent, Node child) {
        ArrayList<Node> children = new ArrayList<Node>(1);
        children.add(child);
        this.addChildren(parent, children);
    }

    @Override
    public void addChildren(Directory parent, List<Node> children) {
        // TODO Auto-generated method stub

    }

    @Override
    public void addFTP(FTP ftp) {
        try {
            ftps.addFTP(ftp);
        } catch (IOException e) {
            System.err.println("IOE while saving new FTP we're fu****g doomed! bye bye...");
            e.printStackTrace();
        }
    }

    @Override
    public Node createNode(Directory parent, String name, long size, NodeType type) {
        switch (type) {
        case directory:
            return new RawDirectory(parent, name, size);
        case file:
            // TODO
        }
        return null;
    }

    @Override
    public Directory createRootNode(FTP ftp) {
        return new RawDirectory(ftp, "");
    }

    @Override
    public void forceCommit() {
        // TODO Auto-generated method stub

    }

    @Override
    public List<Node> getChildren(Directory parent) {
        if (!(parent instanceof RawDirectory)) {
            throw new IllegalArgumentException("RawFilesDB3.getChildren : Why on earth was i provided a non-raw direcory?");
        }
        try {
            return ((RawDirectory) parent).getListing().getNodes();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public FTP getFTP(InetAddress addr, int port) {
        return this.getFTP(addr, port, true);
    }

    @Override
    public FTP getFTP(InetAddress addr, int port, boolean create) {
        for (FTP ftp : ftps.getFtps()) {
            if (ftp.getAddress().equals(addr) && ftp.getPort() == port) {
                return ftp;
            }
        }
        if (!create) {
            return null;
        }
        if (dnsResolver != null) {
            try {
                Name name = ReverseMap.fromAddress(addr.getAddress());
                int type = Type.PTR;
                int dclass = DClass.IN;
                Record rec = Record.newRecord(name, type, dclass);
                Message query = Message.newQuery(rec);
                Message response = dnsResolver.send(query);
                Record[] answers = response.getSectionArray(Section.ANSWER);
                if (answers.length > 0) {
                    String hostName = answers[0].rdataToString();
                    if (hostName.endsWith(".")) {
                        hostName = hostName.substring(0, hostName.length() - 1);
                    }
                    addr = InetAddress.getByAddress(hostName, addr.getAddress());
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FTP newFtp = new FTP(this, addr, port);
        this.addFTP(newFtp);
        return newFtp;
    }

    @Override
    public List<FTP> getFTPs() {
        return ftps.getFtps();
    }

    @Override
    public SearchIndex getSearchIndex() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Object> getStats() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void init() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void registerEventListener(DBEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeChild(Directory parent, Node child) {
        ArrayList<Node> children = new ArrayList<Node>(1);
        children.add(child);
        this.removeChildren(parent, children);
    }

    @Override
    public void removeChildren(Directory parent, List<Node> children) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeEventListener(DBEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void shutdown() {
        // TODO Auto-generated method stub

    }

    @Override
    public void signalIndexerScheduler(IndexerScheduler is) {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateFTP(FTP ftp) {
        try {
            ftps.updateFTP(ftp);
        } catch (IOException e) {
            System.err.println("Doh! update FTP failed with a nasty IOE! the DB is probably dead...");
            e.printStackTrace();
        }
    }

    @Override
    public void updateNode(Node node) {
        // TODO Auto-generated method stub

    }

    public Raw3Metrics getMetrics() {
        return this.metrics;
    }

    /* package */Block allocNewBlock(BlockType type) throws IOException {
        synchronized (channel) {
            ByteBuffer buffer = this.getBufferPool().getBuffer(8);
            buffer.putInt(STD_BLOCK_SIZE);
            buffer.putShort((short) 0x00); // reserved for flags
            buffer.putShort((short) type.ordinal());
            buffer.flip();
            channel.position(nextBlockPos);
            channel.write(buffer);
            this.getBufferPool().returnBuffer(buffer);
            int blockPos = (int) channel.position();
            nextBlockPos = STD_BLOCK_SIZE + blockPos;
            return new Block(STD_BLOCK_SIZE, blockPos, this, type);
        }
    }

    /* package */void writeChunk(WritableChunk chunk) throws IOException {
        synchronized (channel) {
            channel.write(chunk.getBuffer(), chunk.getPosition());
        }
    }

    /* package */ByteBuffer loadBufferFromFile(Block block) throws IOException {
        synchronized (channel) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(block.getSize());
            channel.read(buffer, block.getPosition());
            buffer.position(0);
            return buffer;
        }
    }

    /* package */BufferPool getBufferPool() {
        return bufferPool;
    }

    /* package */Node getNode(int ptr) {
        return null; // TODO
    }

    /* package */SpaceManager getListingSpaceManager() {
        return null; // TODO
    }

    /* package */SpaceManager getNodesSpaceManager() {
        return null; // TODO
    }

    /* package */static void putString(ByteBuffer buffer, String string) {
        byte[] bytes = string.getBytes(UTF8);
        assert (bytes.length < 1 << Short.SIZE);
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }

    /* package */static String getString(ByteBuffer buffer) {
        int sz = buffer.getShort() & 0xffff;
        byte[] bytes = new byte[sz];
        buffer.get(bytes);
        return new String(bytes, UTF8);
    }

    /* package */static String getString(ByteBuffer buffer, int sz) {
        byte[] bytes = new byte[sz];
        buffer.get(bytes);
        return new String(bytes, UTF8);
    }

    /* package */Extent getExtent(int listingPtr) {
        // TODO Auto-generated method stub
        return null;
    }
}