package com.m00ware.ftpindex.raw;

import java.io.*;
import java.lang.ref.SoftReference;
import java.net.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.*;

import org.xbill.DNS.*;

import com.m00ware.ftpindex.*;
import com.m00ware.ftpindex.Node.NodeType;
import com.m00ware.ftpindex.File;
import com.m00ware.ftpindex.indexer.IndexerScheduler;
import com.m00ware.ftpindex.raw.xr.ExtensibleRecord;
import com.m00ware.ftpindex.search.*;

/**
 * @author Wooden TODO add updateFTP event
 */
public class RawFilesDB implements FilesDB, EmbeddedObjectsDB {
    private static final int MAGIC = 0xf112babe;
    private static final byte TYPE_FILE = 0x00;
    private static final byte TYPE_DIRECTORY = 0x01;
    private static final byte BIT_FILE_TYPE = 0x01;
    private static final byte BIT_SUSPECT = 0x02;
    private static final byte BIT_OUTDATED = 0x04;
    private static final byte SEARCH_RECORD_NOP = 0x00;
    private static final byte SEARCH_RECORD_CD = 0x01;
    private static final byte SEARCH_RECORD_SAME = 0x02;
    private static final byte SEARCH_RECORD_CDLAST = 0x03;
    private static final byte SEARCH_RECORD_CDUP = 0x04;
    private static final byte SEARCH_RECORD_SKIP = 0x05;
    private static final byte SEARCH_RECORD_STOP = 0x06;
    private static final byte SEARCH_RECORD_CDUPNONE = 0x07;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final java.io.File dbFile;
    private final List<FTP> ftps;
    private final Set<DBEventListener> listeners;
    private final ExecutorService eventExecutor;
    private final List<VacantSpace> vacantSpaces;
    private final List<VacantSpace> unusableVacantSpaces;
    private RandomAccessFile raf;
    private ByteBuffer writeBuffer;
    private ByteBuffer readBuffer;
    private int writeBufferLength;
    private final List<Fixup> absoluteFixups;
    private final List<Fixup> writeBufferFixups;
    private int appendPosition;
    private int ftpSectionPos;
    private int ftpSectionSize;
    private int ftpSectionFixup;
    private int unusableVacantSpaceThreshold;
    private final List<SearchBucket> searchBuckets;
    private final List<ExtensibleRecord> extensibleRecords;
    private int xrSectionPos;
    private int xrSectionSize;
    private int xrSectionFixup;
    private int searchBucketsSize;
    private final Map<SearchBucket, SoftReference<ByteBuffer>> searchBucketsCache;
    private final Map<FTP, List<SearchBucket>> ftpSearchBuckets;
    private final RawSearchIndex search;
    private Resolver dnsResolver;

    public RawFilesDB(java.io.File dbFile) {
        this.dbFile = dbFile;
        ftps = Collections.synchronizedList(new LinkedList<FTP>());
        listeners = Collections.synchronizedSet(new HashSet<DBEventListener>());
        eventExecutor = new ThreadPoolExecutor(1, 2, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new DeamonThreadFactory());
        vacantSpaces = Collections.synchronizedList(new LinkedList<VacantSpace>());
        unusableVacantSpaces = Collections.synchronizedList(new LinkedList<VacantSpace>());
        setWriteBufferLength(16 * 1024);
        ensureReadBufferSize(16 * 1024);
        absoluteFixups = Collections.synchronizedList(new LinkedList<Fixup>());
        writeBufferFixups = Collections.synchronizedList(new LinkedList<Fixup>());
        unusableVacantSpaceThreshold = 0x14;
        searchBuckets = Collections.synchronizedList(new LinkedList<SearchBucket>());
        extensibleRecords = Collections.synchronizedList(new LinkedList<ExtensibleRecord>());
        searchBucketsCache = Collections.synchronizedMap(new HashMap<SearchBucket, SoftReference<ByteBuffer>>());
        ftpSearchBuckets = Collections.synchronizedMap(new HashMap<FTP, List<SearchBucket>>());
        searchBucketsSize = 64 * 1024;
        search = new RawSearchIndex(this);
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
        addChildren(parent, children);
    }

    @Override
    public void addChildren(Directory parent, List<Node> children) {
        if (!(parent instanceof RawDirectory)) {
            throw new IllegalArgumentException("Unknwon node type"); // convert it?
        }
        RawDirectory rawParent = (RawDirectory) parent;
        try {
            // find listing
            int childListPos = rawParent.getChildListPos();
            if (childListPos < 0) {
                return;
            }
            eventExecutor.execute(new ChildEventRunner(listeners, children, true));
            int oldNumChild;
            int childListSize;
            synchronized (raf) {
                synchronized (readBuffer) {
                    raf.seek(childListPos);
                    oldNumChild = readSingleInt();
                }
                childListSize = 4 + 4 * oldNumChild;
            }
            // try to expand
            int expendSize = 4 * children.size();
            int expendStart = childListPos + childListSize;
            VacantSpace vs = null;
            synchronized (unusableVacantSpaces) {
                for (VacantSpace vacantSpace : unusableVacantSpaces) {
                    if (vacantSpace.getStart() == expendStart) {
                        vs = vacantSpace;
                        break;
                    }
                }
                if (vs != null && vs.getSize() >= expendSize) {
                    unusableVacantSpaces.remove(vs);
                } else {
                    vs = null;
                }
            }
            if (vs == null) {
                synchronized (vacantSpaces) {
                    for (VacantSpace vacantSpace : vacantSpaces) {
                        if (vacantSpace.getStart() == expendStart) {
                            vs = vacantSpace;
                            break;
                        }
                    }
                    if (vs != null && vs.getSize() >= expendSize) {
                        vacantSpaces.remove(vs);
                    } else {
                        vs = null;
                    }
                }
            }
            if (vs != null) {
                // System.out.println("Found vacant space for in place extention of "+parent+" : "+vs);
                synchronized (writeBuffer) {
                    writeBuffer.clear();
                    for (Node child : children) {
                        createWriteBufferFixupNode(child);
                    }
                    writeBuffer.flip();
                    synchronized (raf) {
                        // System.out.println("In place extention of listing for "+parent+" : writing new list entries @0x"+Long.toHexString(expendStart)+" len:0x"+Long.toHexString(expendSize));
                        raf.seek(expendStart);
                        raf.getChannel().write(writeBuffer);
                        // System.out.println("Fixup num children @0x"+Long.toHexString(childListPos)+" ("+oldNumChild+children.size()+")");
                        raf.seek(childListPos);
                        raf.writeInt(oldNumChild + children.size());
                    }
                    processWriteBufferFixups(expendStart);
                }
                if (vs.getSize() > expendSize) {
                    // System.out.println("Marking remaining space as vacant :");
                    saveVacantSpace(new VacantSpace(vs.getStart() + expendSize, vs.getSize() - expendSize));
                }
            } else {// if can't expand, write new listing
                    // System.out.println("Could not find proper vacant space, writing new listing for "+parent);
                int newChildListPos = writeListing(rawParent.getChildren());
                rawParent.setChildListPos(newChildListPos);
                // update parent's childListPos in db file
                int entryPos = rawParent.getEntryPos();
                synchronized (raf) {
                    // System.out.println("Fixup childListPos, seeking to entryPos @0x"+Long.toHexString(entryPos));
                    raf.seek(entryPos);
                    raf.skipBytes(1);
                    skipString();
                    raf.skipBytes(24);
                    // System.out.println("Reached 0x"+Long.toHexString(raf.getFilePointer())+" writing childListPos:->0x"+Long.toHexString(newChildListPos));
                    raf.writeInt(newChildListPos);
                }
                // System.out.println("Marking old listing as vacant :");
                // better mark the old list as vacant only once the node is
                // properly updated
                saveMergeVacantSpace(new VacantSpace(childListPos, childListSize));
            }
            // process fixups (will write all children recursively)
            processAbsoluteFixups();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    @Override
    public void addFTP(FTP ftp) {
        synchronized (ftps) {
            if (ftp != null) {
                ftps.add(ftp);
            }
            try {
                // System.out.println("Re-writing ftp section, marking old section as vacant :");
                // we can safely mark the old section as vacant event before the
                // new one is written because ftp list stays in memory
                // thos updates to this list (even updates to single entries,
                // must be synchronized(ftps)
                saveMergeVacantSpace(new VacantSpace(ftpSectionPos, ftpSectionSize)); // discard old ftp section
                ftpSectionPos = writeFTPsSection();
                synchronized (raf) {
                    // System.out.println("Fixup ftp section pointer @0x"+Long.toHexString(this.ftpSectionFixup)+":->0x"+Long.toHexString(this.ftpSectionPos));
                    raf.seek(ftpSectionFixup);
                    raf.writeInt(ftpSectionPos);
                }
                processAbsoluteFixups();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        if (ftp != null) {
            for (DBEventListener listener : listeners) {
                listener.newFtp(ftp);
            }
        }
    }

    @Override
    public void forceCommit() {
        synchronized (raf) {
            try {
                raf.getChannel().force(true);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public void createDB() throws IOException {
        raf.writeInt(MAGIC);
        ftpSectionFixup = (int) raf.getFilePointer();
        raf.writeInt(0x00000000);
        xrSectionFixup = (int) raf.getFilePointer();
        raf.writeInt(0x00000000);
        appendPosition = (int) raf.getFilePointer();
        ftpSectionPos = writeFTPsSection();
        raf.seek(ftpSectionFixup);
        raf.writeInt(ftpSectionPos);
        xrSectionPos = writeExtensibleRecordsList();
        raf.seek(xrSectionFixup);
        raf.writeInt(xrSectionPos);
        processAbsoluteFixups();
    }

    public void compact() throws IOException {
        // TODO
        synchronized (writeBuffer) {
            synchronized (raf) {
                raf.close();
                java.io.File compactFile = new java.io.File(dbFile.getName() + ".compact");
                if (compactFile.exists()) {
                    compactFile.delete();
                }
                compactFile.deleteOnExit();
                raf = new RandomAccessFile(compactFile, "rw");
                createDB();
                raf.close();
                dbFile.delete();
                compactFile.renameTo(dbFile);
                raf = new RandomAccessFile(dbFile, "rw");
            }
        }
    }

    @Override
    public FTP getFTP(InetAddress addr, int port) {
        return this.getFTP(addr, port, true);
    }

    @Override
    public FTP getFTP(InetAddress addr, int port, boolean create) {
        for (FTP ftp : ftps) {
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
        addFTP(newFtp);
        return newFtp;
    }

    @Override
    public List<FTP> getFTPs() {
        return ftps;
    }

    @Override
    public void init() throws IOException {
        boolean doCreate = !dbFile.exists();
        raf = new RandomAccessFile(dbFile, "rw");
        appendPosition = (int) raf.length();
        if (doCreate) {
            createDB();
        } else {
            if (raf.readInt() != MAGIC) {
                throw new IOException("Unrecognized DB file format");
            }
            ftpSectionFixup = (int) raf.getFilePointer();
            ftpSectionPos = raf.readInt();
            xrSectionFixup = (int) raf.getFilePointer();
            xrSectionPos = raf.readInt();
            raf.seek(ftpSectionPos);
            int ftpListSize = raf.readInt();
            for (int i = 0; i < ftpListSize; i++) {
                String hostname = this.readString();
                int port = raf.readShort() & 0xffff;
                int indexingId = raf.readInt();
                String pathSeparator = this.readString();
                long timeStamp = raf.readLong();
                Date lastIndexing = null;
                if (timeStamp > 0) {
                    lastIndexing = new Date(timeStamp);
                }
                Node root = readNode(raf.readInt(), null);
                try {
                    InetAddress addr = InetAddress.getByName(hostname);
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
                    FTP ftp = new FTP(this, addr, port, (Directory) root, indexingId, lastIndexing);
                    ftp.setPathSeparator(pathSeparator);
                    ftps.add(ftp);
                    for (DBEventListener listener : listeners) {
                        listener.newFtp(ftp);
                    }
                } catch (UnknownHostException uhe) {
                    System.err.println("unknown host : " + uhe.getMessage());
                }

            }
            ftpSectionSize = (int) (raf.getFilePointer() - ftpSectionPos);
            // read xr section
            raf.seek(xrSectionPos);
            int xrListSize = raf.readInt();
            int[] pointers = new int[xrListSize];
            for (int i = 0; i < xrListSize; i++) {
                pointers[i] = raf.readInt();
            }
            xrSectionSize = (int) (raf.getFilePointer() - xrSectionPos);
            for (int i = 0; i < xrListSize; i++) {
                ExtensibleRecord xr = readExtensibleRecord(pointers[i]);
                if (xr != null) {
                    extensibleRecords.add(xr);
                }
            }
            vacantSpaces.addAll(this.getEmbeddedObjects(VacantSpace.class));
            searchBuckets.addAll(this.getEmbeddedObjects(SearchBucket.class));
        }
    }

    @Override
    public void registerEventListener(DBEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeChild(Directory parent, Node child) {
        ArrayList<Node> children = new ArrayList<Node>(1);
        children.add(child);
        removeChildren(parent, children);
    }

    @Override
    public void removeChildren(Directory parent, List<Node> children) {
        eventExecutor.execute(new ChildEventRunner(listeners, children, false));
        if (!(parent instanceof RawDirectory)) {
            throw new IllegalArgumentException("Unknwon node type"); // convert it?
        }
        RawDirectory rawParent = (RawDirectory) parent;
        try {
            // remove from listing & compact (re-write listing..)
            int childListPos = rawParent.getChildListPos();
            int newListSize;
            int originalListSize;
            synchronized (writeBuffer) {
                writeBuffer.clear();
                writeBuffer.putInt(parent.getChildren().size());
                for (Node child : parent.getChildren()) {
                    createWriteBufferFixupNode(child);
                }
                writeBuffer.flip();
                newListSize = writeBuffer.limit();
                synchronized (raf) {
                    raf.seek(childListPos);
                    synchronized (readBuffer) {
                        originalListSize = readSingleInt() * 4 + 4;
                    }

                    // System.out.println("Removing "+children.size()+" children from "+parent+" rewriting childList @0x"+Long.toHexString(childListPos)+" (newChildListEntrySize=0x"+Long.toHexString(newListSize)+",originalListSize=0x"+Long.toHexString(originalListSize)+")");
                    raf.seek(childListPos);
                    raf.getChannel().write(writeBuffer);
                }
            }
            int freedSpaceStart = childListPos + newListSize;
            int freedSpace = originalListSize - newListSize;
            // System.out.println("Marking freed space as vacant:");
            saveMergeVacantSpace(new VacantSpace(freedSpaceStart, freedSpace));
            // remove entry & remove all children recursively
            for (Node child : children) {
                freeNode(child);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    @Override
    public void removeEventListener(DBEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void shutdown() {
        synchronized (vacantSpaces) {
            saveEmbeddableObjects(vacantSpaces);
            saveEmbeddableObject(searchBuckets);
        }
        forceCommit();
        try {
            raf.close();
        } catch (IOException ioe) {}
        eventExecutor.shutdown();
    }

    @Override
    public Node createNode(Directory parent, String name, long size, NodeType type) {
        if (type == NodeType.file) {
            return new RawFile(parent, name, size);
        } else if (type == NodeType.directory) {
            return new RawDirectory(parent, name);
        }
        return null;
    }

    @Override
    public Directory createRootNode(FTP ftp) {
        return new RawDirectory(ftp, "");
    }

    @Override
    public void updateNode(Node node) {
        byte bits;
        int entryPos;
        int entrySize;
        if (node instanceof RawFile) {
            bits = (BIT_FILE_TYPE & TYPE_FILE);
            entryPos = ((RawFile) node).getEntryPos();
            entrySize = ((RawFile) node).getEntrySize();
        } else if (node instanceof RawDirectory) {
            bits = (BIT_FILE_TYPE & TYPE_DIRECTORY);
            entryPos = ((RawDirectory) node).getEntryPos();
            entrySize = ((RawDirectory) node).getEntrySize();
        } else {
            throw new IllegalArgumentException("Unknwon node type");
        }
        if (entryPos < 0) {
            return;
        }
        if (node.isSuspect()) {
            bits |= BIT_SUSPECT;
        }
        if (node.isOutDated()) {
            bits |= BIT_OUTDATED;
        }
        try {
            synchronized (writeBuffer) {
                writeBuffer.clear();
                writeBuffer.put(bits);
                this.writeString(node.getName());
                if (node.getDate() != null) {
                    writeBuffer.putLong(node.getDate().getTime());
                } else {
                    writeBuffer.putLong(0x0000000000000000);
                }
                writeBuffer.putLong(node.getSize());
                if (node.getLastSeen() != null) {
                    writeBuffer.putLong(node.getLastSeen().getTime());
                } else {
                    writeBuffer.putLong(0x0000000000000000);
                }
                writeBuffer.flip();
                int newEntrySize = writeBuffer.limit();
                if (node instanceof RawDirectory) {
                    newEntrySize += 4;
                }
                if (newEntrySize != entrySize) {
                    System.err.println("Can not update Node : entry size changed!");
                } else {
                    synchronized (raf) {
                        // System.out.println("Updating node "+node+" @0x"+Long.toHexString(entryPos)+" (entrySize=0x"+Long.toHexString(entrySize)+")");
                        raf.seek(entryPos);
                        raf.getChannel().write(writeBuffer);
                    }
                }
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public int getWriteBufferLength() {
        return writeBufferLength;
    }

    public void setWriteBufferLength(int writeBufferLength) {
        if (writeBuffer == null) {
            writeBuffer = ByteBuffer.allocateDirect(writeBufferLength);
            return;
        }
        synchronized (writeBuffer) {
            if (writeBufferLength != this.writeBufferLength) {
                writeBuffer = ByteBuffer.allocateDirect(writeBufferLength);
            }
        }
        this.writeBufferLength = writeBufferLength;
    }

    @Override
    public List<Node> getChildren(Directory parent) {
        if (!(parent instanceof RawDirectory)) {
            throw new IllegalArgumentException();
        }
        List<Node> list = new LinkedList<Node>();
        int childListPos = ((RawDirectory) parent).getChildListPos();
        try {
            int[] pointers;
            synchronized (raf) {
                raf.seek(childListPos);
                synchronized (readBuffer) {
                    int listSize = readSingleInt();
                    readBuffer.clear();
                    readBuffer.limit(listSize * 4);
                    readByteBufferFully();
                    // System.out.println("Reading children for "+parent+" @0x"+Long.toHexString(childListPos)+" : size="+listSize);
                    pointers = new int[listSize];
                    for (int i = 0; i < listSize; i++) {
                        pointers[i] = readBuffer.getInt();
                    }

                }
                for (int pointer : pointers) {
                    list.add(readNode(pointer, parent));
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return list;
    }

    public int getUnusableVacantSpaceThreshold() {
        return unusableVacantSpaceThreshold;
    }

    public void setUnusableVacantSpaceThreshold(int unusableVacantSpaceThreshold) {
        this.unusableVacantSpaceThreshold = unusableVacantSpaceThreshold;
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> map = new HashMap<String, Object>();
        try {
            map.put(FilesDB.STAT_DBFILE_SIZE, raf.length());
        } catch (IOException ioe) {}
        return map;
    }

    @Override
    public void updateFTP(FTP ftp) {
        addFTP(null); // TODO: do a true update creates potentially useless
                      // vacant space...
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> getEmbeddedObjects(Class<T> clazz) {
        List<T> list = new LinkedList<T>();
        if (!ExtensibleRecord.class.isAssignableFrom(clazz)) {
            return list;
        }
        synchronized (extensibleRecords) {
            for (ExtensibleRecord xr : extensibleRecords) {
                if (clazz.isAssignableFrom(xr.getClass())) {
                    list.add((T) xr);
                }
            }
        }
        return list;
    }

    @Override
    public <T> T createEmbeddableObject(Class<T> clazz) {
        return ExtensibleRecord.createExtensibleRecord(clazz);
    }

    @Override
    public boolean saveEmbeddableObject(Object obj) {
        if (obj instanceof ExtensibleRecord) {
            try {
                saveExtensibleRecord((ExtensibleRecord) obj);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public boolean saveEmbeddableObjects(List<?> objs) {
        boolean success = true;
        ArrayList<ExtensibleRecord> records = new ArrayList<ExtensibleRecord>(objs.size());
        for (Object obj : objs) {
            if (obj instanceof ExtensibleRecord) {
                records.add((ExtensibleRecord) obj);
            } else {
                success = false;
            }
        }
        try {
            saveExtensibleRecords(records);
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }
        return success;
    }

    @Override
    public boolean removeEmbeddableObject(Object obj) {
        if (obj instanceof ExtensibleRecord) {
            removeExtensibleRecord((ExtensibleRecord) obj);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeEmbeddableObjects(List<?> objs) {
        boolean success = true;
        ArrayList<ExtensibleRecord> records = new ArrayList<ExtensibleRecord>(objs.size());
        for (Object obj : objs) {
            if (obj instanceof ExtensibleRecord) {
                records.add((ExtensibleRecord) obj);
            } else {
                success = false;
            }
        }
        removeExtensibleRecords(records);
        return success;
    }

    public int getSearchBucketsSize() {
        return searchBucketsSize;
    }

    public void setSearchBucketsSize(int searchBucketsSize) {
        this.searchBucketsSize = searchBucketsSize;
    }

    @Override
    public void signalIndexerScheduler(IndexerScheduler is) {
        /*
         * is.registerIndexerEventListener(new IndexerEventListener() {
         * @Override public void indexingStart(FTP ftp){ // do nothing }
         * @Override public void indexingEnd(FTP ftp){
         * System.out.println("Updating search buckets for "+ftp); long t =
         * System.currentTimeMillis(); try{
         * RawFilesDB2.this.updateFTPSearchBuckets(ftp); }catch(IOException
         * ioe){ ioe.printStackTrace(); } t = System.currentTimeMillis() - t;
         * System
         * .out.println("Finished updating search  buckets for "+ftp+" : took "
         * +t+"ms"); } });
         */
    }

    public void doDebugUpdateFTPSearchBuckets(FTP ftp) {
        System.out.println("Updating search buckets for " + ftp);
        long t = System.currentTimeMillis();
        try {
            RawFilesDB.this.updateFTPSearchBuckets(ftp);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        t = System.currentTimeMillis() - t;
        System.out.println("Finished updating search  buckets for " + ftp + " : took " + t + "ms");
        System.out.println("There are " + searchBuckets.size() + " Search Buckets");
    }

    @Override
    public SearchIndex getSearchIndex() {
        return search;
    }

    public List<Node> search(List<String> search) {
        List<SearchResult> results = new LinkedList<SearchResult>();
        Deque<SearchPathElement> path = new LinkedList<SearchPathElement>();
        byte[][] searchBytes = new byte[search.size()][];
        boolean[][] impossible = new boolean[search.size()][];
        int[] md2 = new int[search.size()];
        int i = 0;
        for (String term : search) {
            byte[] bytes = term.getBytes(UTF8);
            searchBytes[i] = bytes;
            impossible[i] = SearchUtil.getImpossibleArray(bytes);
            md2[i] = SearchUtil.getMD2(bytes);
            i++;
        }
        synchronized (searchBuckets) {
            boolean outOfSync = true;
            byte[] last = null;
            for (SearchBucket bucket : searchBuckets) {
                ByteBuffer buffer = null;
                SoftReference<ByteBuffer> softRef = searchBucketsCache.get(bucket);
                if (softRef != null) {
                    buffer = softRef.get();
                }
                if (buffer == null) {
                    buffer = ByteBuffer.allocateDirect(bucket.getSize());
                    searchBucketsCache.put(bucket, new SoftReference<ByteBuffer>(buffer));
                } else {
                    buffer.clear();
                }
                boolean stop = false;
                while (!stop && buffer.hasRemaining()) {
                    int score = 0;
                    int op = buffer.get();
                    switch (op) {
                    case SEARCH_RECORD_NOP: {
                        // System.out.println("NOP");
                        break;
                    }
                    case SEARCH_RECORD_CD: {
                        outOfSync = false;
                        int size = buffer.get();
                        byte[] address = new byte[size];
                        buffer.get(address);
                        InetAddress addr;
                        try {
                            addr = InetAddress.getByAddress(address);
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                            continue;
                        }
                        int port = buffer.getShort() & 0xffff;
                        String pathStr = this.readString(buffer);
                        path.clear();
                        FTP ftp = this.getFTP(addr, port);
                        // System.out.print("CD");
                        if (ftp == null) {
                            System.out.println("Wrong entry in search bucket : unknown FTP");
                            outOfSync = true;
                            continue;
                        }
                        Node node = ftp.getNodeFromPath(pathStr);
                        if (node == null) {
                            System.out.println("Wrong entry in search bucket : unknown Node");
                            outOfSync = true;
                            continue;
                        }
                        SearchPathElement rootSPE = new SearchPathElement(pathStr, null);
                        rootSPE.setDirectory((Directory) node);
                        path.push(rootSPE);
                        last = null;
                        // System.out.println(" "+ftp+" -> '"+pathStr+"'");
                        break;
                    }
                    case SEARCH_RECORD_SAME: {
                        last = readStringBytes(buffer);
                        // System.out.println("SAME "+last);
                        break;
                    }
                    case SEARCH_RECORD_CDLAST: {
                        path.push(new SearchPathElement(last, path.peek()));
                        last = readStringBytes(buffer);
                        // System.out.println("CDLAST "+last);
                        break;
                    }
                    case SEARCH_RECORD_CDUP: {
                        last = readStringBytes(buffer);
                        // System.out.println("CDUP "+last);
                        path.pop();
                        break;
                    }
                    case SEARCH_RECORD_CDUPNONE: {
                        last = null;
                        // System.out.println("CDUPNONE");
                        path.pop();
                        break;
                    }
                    case SEARCH_RECORD_SKIP: {
                        int offset = buffer.getInt();
                        // System.out.println("SKIP "+offset);
                        if (offset > 0) {
                            buffer.position(buffer.position() + offset);
                            break;
                        }// else fall through
                    }
                    case SEARCH_RECORD_STOP: {
                        stop = true;
                        // System.out.println("STOP");
                        break;
                    }
                    }
                    /*
                     * System.out.print("Path : "); for(SearchPathElement spe :
                     * path){ System.out.print("/"+spe.getName()); }
                     * System.out.println();
                     */
                    if (!outOfSync && last != null) {
                        byte[] test = SearchUtil.fuzzyUTF8StrToLower(last);
                        for (i = 0; i < searchBytes.length; i++) {
                            if (SearchUtil.bytesContains(test, searchBytes[i], impossible[i], md2[i])) {
                                score++;
                            }
                        }
                        if (score > 0) {
                            Node node = path.peek().getDirectory().getChild(new String(last, UTF8));
                            results.add(new SearchResult(node, score));
                        }
                    }
                }
            }
        }
        return CachingSearchIndex.sortSearchResults(results);
    }

    private void saveExtensibleRecord(ExtensibleRecord record) throws IOException {
        ArrayList<ExtensibleRecord> records = new ArrayList<ExtensibleRecord>(1);
        records.add(record);
        saveExtensibleRecords(records);
    }

    private void saveExtensibleRecords(List<ExtensibleRecord> records) throws IOException {
        synchronized (extensibleRecords) {
            for (ExtensibleRecord record : records) {
                if (!extensibleRecords.contains(record)) {
                    extensibleRecords.add(record);
                } else {
                    if (record.getXRPos() > 0) {
                        saveMergeVacantSpace(new VacantSpace(record.getXRPos(), record.getXRSize()));
                        record.setXRPos(-1);
                    }
                }
            }
            saveMergeVacantSpace(new VacantSpace(xrSectionPos, xrSectionSize));
            xrSectionPos = writeExtensibleRecordsList();
            synchronized (raf) {
                raf.seek(xrSectionFixup);
                raf.writeInt(xrSectionPos);
            }
        }
    }

    private void removeExtensibleRecord(ExtensibleRecord record) {
        ArrayList<ExtensibleRecord> records = new ArrayList<ExtensibleRecord>(1);
        records.add(record);
        removeExtensibleRecords(records);
    }

    private void removeExtensibleRecords(ArrayList<ExtensibleRecord> records) {
        synchronized (extensibleRecords) {
            extensibleRecords.removeAll(records);
            for (ExtensibleRecord xr : records) {
                saveMergeVacantSpace(new VacantSpace(xr.getXRPos(), xr.getXRSize()));
            }
        }
    }

    private void updateFTPSearchBuckets(FTP ftp) throws IOException {
        List<SearchBucket> privateSearchBuckets;
        synchronized (ftpSearchBuckets) {
            privateSearchBuckets = ftpSearchBuckets.get(ftp);
            if (privateSearchBuckets == null) {
                privateSearchBuckets = new LinkedList<SearchBucket>();
                ftpSearchBuckets.put(ftp, privateSearchBuckets);
            }
        }
        synchronized (privateSearchBuckets) {
            searchBuckets.removeAll(privateSearchBuckets);
            removeEmbeddableObjects(privateSearchBuckets);
            for (SearchBucket bucket : privateSearchBuckets) {
                searchBucketsCache.remove(bucket);
                freeSearchBucket(bucket);
            }
            privateSearchBuckets.clear();
            // we're ready let's create the SBs
            ByteBuffer tempEntryBuffer = ByteBuffer.allocateDirect(4 * 1024);
            Directory root = ftp.getRoot();
            byte[] address = ftp.getAddress().getAddress();
            tempEntryBuffer.put(SEARCH_RECORD_CD);
            tempEntryBuffer.put((byte) address.length);
            tempEntryBuffer.put(address);
            tempEntryBuffer.putShort((short) ftp.getPort());
            this.writeString("", tempEntryBuffer);
            tempEntryBuffer.flip();
            appendSearchEntry(tempEntryBuffer, privateSearchBuckets);
            createSearchBuckets(tempEntryBuffer, root, privateSearchBuckets, true);
            SearchBucket last = privateSearchBuckets.get(privateSearchBuckets.size() - 1);
            ByteBuffer tempBuffer = last.getTempBuffer();
            if (tempEntryBuffer.remaining() >= 1) {
                tempEntryBuffer.clear();
                tempEntryBuffer.put(SEARCH_RECORD_STOP);
                tempEntryBuffer.flip();
                appendSearchEntry(tempEntryBuffer, privateSearchBuckets);
            }
            for (SearchBucket bucket : privateSearchBuckets) {
                tempBuffer = bucket.getTempBuffer();
                synchronized (raf) {
                    raf.seek(bucket.getStart());
                    tempBuffer.flip();
                    raf.getChannel().write(tempBuffer);
                }
                searchBucketsCache.put(bucket, new SoftReference<ByteBuffer>(tempBuffer));
                bucket.setTempBuffer(null);
            }
            searchBuckets.addAll(privateSearchBuckets);
            saveEmbeddableObjects(privateSearchBuckets);
        }
    }

    private boolean createSearchBuckets(ByteBuffer buffer, Directory directory, List<SearchBucket> buckets, boolean ignoreFirst) {
        boolean first = !ignoreFirst;
        boolean doCDUP = false;
        for (Node node : directory.getChildren()) {
            buffer.clear();
            if (first) {
                buffer.put(SEARCH_RECORD_CDLAST);
                first = false;
            } else if (doCDUP) {
                buffer.put(SEARCH_RECORD_CDUP);
                doCDUP = false;
            } else {
                buffer.put(SEARCH_RECORD_SAME);
            }
            this.writeString(node.getName(), buffer);
            buffer.flip();
            appendSearchEntry(buffer, buckets);
            if (node instanceof Directory) {
                doCDUP = createSearchBuckets(buffer, (Directory) node, buckets, false);
            }
        }
        if (doCDUP) {
            buffer.clear();
            buffer.put(SEARCH_RECORD_CDUPNONE);
            buffer.flip();
            appendSearchEntry(buffer, buckets);
        }
        return !first && !ignoreFirst;
    }

    // it' a bit tricky around the temp buffer but things go right thanks to rem
    private void appendSearchEntry(ByteBuffer buffer, List<SearchBucket> buckets) {
        SearchBucket lastBucket;
        ByteBuffer temp;
        int rem;
        if (buckets.isEmpty()) {
            lastBucket = null;
            temp = null;
            rem = 0;
        } else {
            lastBucket = buckets.get(buckets.size() - 1);
            temp = lastBucket.getTempBuffer();
            if (temp == null) {
                throw new IllegalStateException("SearchBucket must have a temp buffer at this point");
            }
            rem = temp.remaining();
        }

        if (rem < buffer.remaining()) {
            if (rem > 0x05) {
                temp.put(SEARCH_RECORD_SKIP);
                temp.putInt(-1);// skip to next bucket
            } else {
                for (int i = 0; i < rem; i++) {
                    temp.put(SEARCH_RECORD_NOP);
                }
            }
            // alloc new bucket
            int target = getSearchBucketsSize();
            if (target < buffer.remaining()) {
                throw new IllegalStateException("SearchBucketSize is too small : " + target);
            }
            VacantSpace vs = findVacantSpace(target);
            if (vs != null) {
                lastBucket = new SearchBucket(vs.getStart(), target);
                int remVS = vs.getSize() - target;
                if (remVS > 0) {
                    saveVacantSpace(new VacantSpace(vs.getStart() + target, remVS));
                }
            } else {
                synchronized (raf) {
                    lastBucket = new SearchBucket(appendPosition, target);
                    appendPosition += target;
                }
            }
            buckets.add(lastBucket);
            temp = ByteBuffer.allocateDirect(target);
            lastBucket.setTempBuffer(temp);
        }
        temp.put(buffer);
        lastBucket.setAppendPosition(temp.position());
    }

    private void freeSearchBucket(SearchBucket bucket) {
        saveMergeVacantSpace(new VacantSpace(bucket.getStart(), bucket.getSize()));
    }

    private ExtensibleRecord readExtensibleRecord(int xrPos) throws IOException {
        synchronized (raf) {
            long originalPos = raf.getFilePointer();
            raf.seek(xrPos);
            int id = raf.readShort() & 0xffff;
            int size = raf.readShort() & 0xffff;
            if (size < 0) {
                System.out.println("Negative size for an xr");
                return null;
            }
            synchronized (readBuffer) {
                readBuffer.clear();
                readBuffer.limit(size);
                readByteBufferFully();
                raf.seek(originalPos);
                ExtensibleRecord xr = ExtensibleRecord.getExtensibleRecord(id, readBuffer);
                xr.setXRPos(xrPos);
                xr.setXRSize(size + 4);
                return xr;
            }
        }
    }

    private int writeExtensibleRecordsList() throws IOException {
        synchronized (extensibleRecords) {
            synchronized (writeBuffer) {
                writeBuffer.clear();
                writeBuffer.putInt(extensibleRecords.size());
                for (ExtensibleRecord xr : extensibleRecords) {
                    createWriteBufferFixupExtensibleRecord(xr);
                }
                xrSectionSize = writeBuffer.position();
                return insertWriteBuffer();
            }
        }
    }

    private int writeFTPsSection() throws IOException {
        try {
            synchronized (ftps) {
                synchronized (writeBuffer) {
                    writeBuffer.clear();
                    writeBuffer.putInt(ftps.size());
                    for (FTP ftp : ftps) {
                        this.writeString(ftp.getAddress().getHostName());
                        writeBuffer.putShort((short) ftp.getPort());
                        writeBuffer.putInt(ftp.getCurrentIndexingId());
                        this.writeString(ftp.getPathSeparator());
                        if (ftp.getLastIndexing() != null) {
                            writeBuffer.putLong(ftp.getLastIndexing().getTime());
                        } else {
                            writeBuffer.putLong(0x0000000000000000l);
                        }
                        createWriteBufferFixupNode(ftp.getRoot());
                    }
                    ftpSectionSize = writeBuffer.position();
                    // System.out.println("Inserting write buffer for ftp section:");
                    return insertWriteBuffer();
                }
            }
        } catch (BufferOverflowException boe) {
            setWriteBufferLength(getWriteBufferLength() + 1024);
            return writeFTPsSection();
        }
    }

    private Node readNode(int entryPos, Directory parent) throws IOException {
        synchronized (raf) {
            long originalPos = raf.getFilePointer();
            raf.seek(entryPos);
            // System.out.println("Reading node @0x"+Long.toHexString(entryPos));
            int bits = raf.read();
            boolean directory = (bits & BIT_FILE_TYPE) == TYPE_DIRECTORY;
            String name = this.readString();
            Date lastMod;
            long size;
            Date lastSeen;
            int childListPos = -1;
            synchronized (readBuffer) {
                readBuffer.clear();
                if (directory) {
                    readBuffer.limit(28);
                } else {
                    readBuffer.limit(24);
                }
                readByteBufferFully();
                lastMod = new Date(readBuffer.getLong());
                size = readBuffer.getLong();
                lastSeen = new Date(readBuffer.getLong());
                if (directory) {
                    childListPos = readBuffer.getInt();
                }
            }
            boolean suspect = (BIT_SUSPECT & bits) != 0;
            boolean outDated = (BIT_OUTDATED & bits) != 0;
            Node node;
            if (directory) {
                node = new RawDirectory(parent, name, childListPos, entryPos, (int) raf.getFilePointer() - entryPos);
                ((Directory) node).forceSize(size);
            } else {
                node = new RawFile(parent, name, size, entryPos, (int) raf.getFilePointer() - entryPos);
            }
            node.setSuspect(suspect);
            node.setOutDated(outDated);
            node.setDate(lastMod);
            node.setLastSeen(lastSeen);
            raf.seek(originalPos);
            return node;
        }
    }

    private int readSingleInt() throws IOException {
        readBuffer.clear();
        readBuffer.limit(4);
        readByteBufferFully();
        return readBuffer.getInt();
    }

    private void readByteBufferFully() throws IOException {
        int rem = readBuffer.remaining();
        FileChannel channel = raf.getChannel();
        while (rem > 0) {
            int res = channel.read(readBuffer);
            if (res < 0) {
                throw new EOFException("could not readByteBufferFully");
            }
            rem -= res;
        }
        readBuffer.flip();
    }

    private void processAbsoluteFixups() throws IOException {
        synchronized (writeBuffer) // MUST own the wb monitor before claiming
                                   // the raf one if we may call a method that
                                   // need the wb monitor
        {
            synchronized (raf) {
                while (!absoluteFixups.isEmpty()) {
                    Fixup fixup = absoluteFixups.remove(0);
                    if (fixup instanceof FixupNode) {
                        Node node = ((FixupNode) fixup).getNode();
                        int nodePos = getNodePositionOrWrite(node);
                        // System.out.println("Writing fixup for node "+node+" @0x"+Long.toHexString(fixup.getFixupPos())+":->0x"+Long.toHexString(nodePos));
                        raf.seek(fixup.getFixupPos());
                        raf.writeInt(nodePos);
                    } else if (fixup instanceof FixupListing) {
                        RawDirectory listing = ((FixupListing) fixup).getListing();
                        int listingPos = getListingPositionOrWrite(listing);
                        // System.out.println("Writing fixup for listing of "+listing+" @0x"+Long.toHexString(fixup.getFixupPos())+":->0x"+Long.toHexString(listingPos));
                        raf.seek(fixup.getFixupPos());
                        raf.writeInt(listingPos);
                    } else if (fixup instanceof FixupExtendedRecord) {
                        ExtensibleRecord xr = ((FixupExtendedRecord) fixup).getRecord();
                        int xrPos = getExtentedRecordPositionOrWrite(xr);
                        raf.seek(fixup.getFixupPos());
                        raf.writeInt(xrPos);
                    }
                }
            }
        }
    }

    private void processWriteBufferFixups(int writePos) {
        synchronized (writeBufferFixups) {
            for (Fixup fixup : writeBufferFixups) {
                fixup.makeAbsolute(writePos);
                absoluteFixups.add(fixup);
            }
        }
        writeBufferFixups.clear();
    }

    private int getExtentedRecordPositionOrWrite(ExtensibleRecord xr) throws IOException {
        int xrPos = xr.getXRPos();
        if (xrPos < 0) {
            synchronized (writeBuffer) {
                writeBuffer.clear();
                xr.writeToBuffer(writeBuffer);
                writeBuffer.flip();
                xrPos = insertWriteBuffer();
            }
            xr.setXRPos(xrPos);
        }
        return xrPos;
    }

    private int getListingPositionOrWrite(RawDirectory listing) throws IOException {
        int childListPos = listing.getChildListPos();
        if (childListPos < 0) {
            // System.out.println("Writing listing for "+listing);
            childListPos = writeListing(listing.getChildren());
            listing.setChildListPos(childListPos);
        }
        return childListPos;
    }

    private int getNodePositionOrWrite(Node node) throws IOException {
        if (node instanceof RawFile) {
            RawFile rawFile = (RawFile) node;
            int entryPos = rawFile.getEntryPos();
            if (entryPos < 0) {
                entryPos = writeNode(rawFile);
                rawFile.setEntryPos(entryPos);
            }
            return entryPos;
        } else if (node instanceof RawDirectory) {
            RawDirectory rawDirectory = (RawDirectory) node;
            int entryPos = rawDirectory.getEntryPos();
            if (entryPos < 0) {
                entryPos = writeNode(rawDirectory);
                rawDirectory.setEntryPos(entryPos);
            }
            return entryPos;
        }
        throw new IllegalArgumentException("Unknown Node type");
    }

    // listing size = 4+size*4
    private int writeListing(List<Node> children) throws IOException {
        try {
            synchronized (writeBuffer) {
                writeBuffer.clear();
                writeBuffer.putInt(children.size());
                for (Node child : children) {
                    createWriteBufferFixupNode(child);
                }
                // System.out.println("Inserting write buffer for listing");
                return insertWriteBuffer();
            }
        } catch (BufferOverflowException boe) {
            setWriteBufferLength(getWriteBufferLength() + 1024);
            return writeListing(children);
        }
    }

    private int writeNode(Node node) throws IOException {
        synchronized (writeBuffer) {
            writeBuffer.clear();
            byte bits = 0x00;
            if (node instanceof File) {
                bits |= BIT_FILE_TYPE & TYPE_FILE;
            } else if (node instanceof Directory) {
                bits |= BIT_FILE_TYPE & TYPE_DIRECTORY;
            } else {
                throw new IllegalArgumentException("Unknown Node type");
            }
            if (node.isSuspect()) {
                bits |= BIT_SUSPECT;
            }
            if (node.isOutDated()) {
                bits |= BIT_OUTDATED;
            }
            writeBuffer.put(bits);
            this.writeString(node.getName());
            if (node.getDate() != null) {
                writeBuffer.putLong(node.getDate().getTime());
            } else {
                writeBuffer.putLong(0x0000000000000000);
            }
            writeBuffer.putLong(node.getSize());
            if (node.getLastSeen() != null) {
                writeBuffer.putLong(node.getLastSeen().getTime());
            } else {
                writeBuffer.putLong(0x0000000000000000);
            }
            if (node instanceof RawDirectory) {
                createWriteBufferFixupListing(((RawDirectory) node));
            }
            if (node instanceof RawDirectory) {
                ((RawDirectory) node).setEntrySize(writeBuffer.position());
            }
            if (node instanceof RawFile) {
                ((RawFile) node).setEntrySize(writeBuffer.position());
            }
            // System.out.println("Inserting write buffer to write node "+node+":");
            return insertWriteBuffer();
        }
    }

    private int insertWriteBuffer() throws IOException // methods calling this
                                                       // must ensure they do
                                                       // so while owning the
                                                       // wb monitor
    {
        writeBuffer.flip();
        int target = writeBuffer.limit();
        VacantSpace best = findVacantSpace(target);
        int writePos;
        synchronized (raf) {
            if (best != null) { // insert here
                // System.out.println("Found vacant space for inserting buffer of size 0x"+Long.toHexString(target)+" : "+best);
                writePos = best.getStart();
                raf.seek(best.getStart());
                raf.getChannel().write(writeBuffer);
                int rem = best.getSize() - target;
                if (rem > 0) {
                    // System.out.println("Saving remaining space:");
                    saveVacantSpace(new VacantSpace((int) raf.getFilePointer(), rem));
                }
            } else { // append
                     // System.out.println("Appending buffer of size 0x"+Long.toHexString(target)+" at the end of file (0x"+Long.toHexString(this.appendPosition)+")");
                writePos = appendPosition;
                raf.seek(appendPosition);
                raf.getChannel().write(writeBuffer);
                appendPosition = (int) raf.getFilePointer();
            }
        }
        processWriteBufferFixups(writePos);
        return writePos;
    }

    /**
     * @param target
     * @return
     */
    private VacantSpace findVacantSpace(int target) {
        int bestDist = Integer.MAX_VALUE;
        VacantSpace best = null;
        synchronized (vacantSpaces) {
            for (VacantSpace vs : vacantSpaces) {
                int dist = vs.getSize() - target;
                if (dist == 0) {
                    best = vs;
                    bestDist = 0;
                    break;
                }
                if (dist > 0 && dist < bestDist) {
                    best = vs;
                    bestDist = dist;
                }
            }
            if (best != null) {
                vacantSpaces.remove(best);
            }
        }
        return best;
    }

    private void saveMergeVacantSpace(VacantSpace vacantSpace) {
        if (vacantSpace.getSize() <= 0) {
            return;
        }
        int start = vacantSpace.getStart();
        int end = vacantSpace.getEnd();
        VacantSpace mergeBefore = null;
        VacantSpace mergeAfter = null;
        synchronized (unusableVacantSpaces) {
            for (VacantSpace vs : unusableVacantSpaces) {
                if (vs.getEnd() == start) {
                    mergeBefore = vs;
                } else if (vs.getStart() == end) {
                    mergeAfter = vs;
                }
            }
            if (mergeAfter != null) {
                unusableVacantSpaces.remove(mergeAfter);
            }
            if (mergeBefore != null) {
                unusableVacantSpaces.remove(mergeBefore);
            }
        }
        if (mergeAfter == null || mergeBefore == null) {
            boolean usableMergeAfter = false;
            boolean usableMergeBefore = false;
            synchronized (vacantSpaces) {
                for (VacantSpace vs : vacantSpaces) {
                    if (vs.getEnd() == start) {
                        mergeBefore = vs;
                        usableMergeBefore = true;
                    } else if (vs.getStart() == end) {
                        mergeAfter = vs;
                        usableMergeAfter = true;
                    }
                }
                if (usableMergeAfter) {
                    vacantSpaces.remove(mergeAfter);
                }
                if (usableMergeBefore) {
                    vacantSpaces.remove(mergeBefore);
                }
            }
        }
        VacantSpace newVS = null;
        if (mergeAfter != null && mergeBefore != null) {
            newVS = new VacantSpace(mergeBefore.getStart(), mergeBefore.getSize() + vacantSpace.getSize() + mergeAfter.getSize());
            // System.out.println("Merging "+mergeBefore+" and "+vacantSpace+" and "+mergeAfter+" into "+newVS);
        } else if (mergeAfter == null && mergeBefore != null) {
            newVS = new VacantSpace(mergeBefore.getStart(), mergeBefore.getSize() + vacantSpace.getSize());
            // System.out.println("Merging "+mergeBefore+" and "+vacantSpace+" into "+newVS);
        } else if (mergeAfter != null && mergeBefore == null) {
            newVS = new VacantSpace(vacantSpace.getStart(), vacantSpace.getSize() + mergeAfter.getSize());
            // System.out.println("Merging "+vacantSpace+" and "+mergeAfter+" into "+newVS);
        }
        if (newVS != null) {
            saveVacantSpace(newVS);
        } else {
            saveVacantSpace(vacantSpace);
        }
    }

    private void saveVacantSpace(VacantSpace vs) {
        if (vs.getSize() <= 0) {
            return;
        }
        // System.out.println("Saving vacant space : "+vs);
        if (vs.getSize() > unusableVacantSpaceThreshold) {
            vacantSpaces.add(vs);
        } else {
            unusableVacantSpaces.add(vs);
        }
    }

    /*
     * methods calling this must ensure they do so while owning the wb monitor
     */
    private void createWriteBufferFixupNode(Node node) {
        writeBufferFixups.add(new FixupNode(writeBuffer.position(), node));
        writeBuffer.putInt(0x00000000);
    }

    /*
     * methods calling this must ensure they do so while owning the wb monitor
     */
    private void createWriteBufferFixupListing(RawDirectory listing) {
        writeBufferFixups.add(new FixupListing(writeBuffer.position(), listing));
        writeBuffer.putInt(0x00000000);
    }

    /*
     * methods calling this must ensure they do so while owning the wb monitor
     */
    private void createWriteBufferFixupExtensibleRecord(ExtensibleRecord record) {
        writeBufferFixups.add(new FixupExtendedRecord(writeBuffer.position(), record));
        writeBuffer.putInt(0x00000000);
    }

    private void freeNode(Node node) throws IOException {
        if (node instanceof RawFile) {
            RawFile rawFile = (RawFile) node;
            // System.out.println("Freenode : marking old entry for "+node+" as vacant:");
            saveMergeVacantSpace(new VacantSpace(rawFile.getEntryPos(), rawFile.getEntrySize()));
        } else if (node instanceof RawDirectory) {
            RawDirectory rawDirectory = (RawDirectory) node;
            for (Node child : rawDirectory.getChildren()) {
                freeNode(child);
            }
            int childListPos = rawDirectory.getChildListPos();
            rawDirectory.setChildListPos(-1); // make it non-db
            // System.out.println("Freenode : marking old entry for "+node+" as vacant:");
            saveMergeVacantSpace(new VacantSpace(rawDirectory.getEntryPos(), rawDirectory.getEntrySize()));
            int listingSize;
            synchronized (raf) {
                raf.seek(childListPos);
                listingSize = raf.readInt() * 4 + 4;
            }
            // System.out.println("Freenode : marking old childList for "+node+" as vacant:");
            saveMergeVacantSpace(new VacantSpace(childListPos, listingSize));
        }
    }

    private String readString() throws IOException // must own raf monitor
                                                   // before calling this
    {
        int len = raf.readShort() & 0xffff;
        if (len > 256) {
            System.out.println("Suspicious string len : " + len);
        }
        byte[] data = new byte[len];
        raf.read(data);
        return new String(data, UTF8);
    }

    private String readString(ByteBuffer buffer) {
        int len = buffer.getShort() & 0xffff;
        byte[] data = new byte[len];
        buffer.get(data);
        return new String(data, UTF8);
    }

    private byte[] readStringBytes(ByteBuffer buffer) {
        int len = buffer.getShort() & 0xffff;
        byte[] data = new byte[len];
        buffer.get(data);
        return data;
    }

    /*
     * must own raf monitor before calling this
     */
    private void skipString() throws IOException {
        int len = raf.readShort() & 0xffff;
        raf.skipBytes(len);
    }

    /*
     * must own raf monitor before calling this
     */
    private void writeString(String str) {
        byte[] data = str.getBytes(UTF8);
        writeBuffer.putShort((short) data.length);
        writeBuffer.put(data);
    }

    private void writeString(String str, ByteBuffer buffer) {
        byte[] data = str.getBytes(UTF8);
        buffer.putShort((short) data.length);
        buffer.put(data);
    }

    private void ensureReadBufferSize(int size) {
        if (readBuffer == null || readBuffer.capacity() < size) {
            readBuffer = ByteBuffer.allocateDirect(size);
        }
    }

    private static class ChildEventRunner implements Runnable {
        private final Set<DBEventListener> listeners;
        private final List<Node> children;
        private final boolean newChild;

        public ChildEventRunner(Set<DBEventListener> listeners, List<Node> children, boolean newChild) {
            this.listeners = listeners;
            this.children = children;
            this.newChild = newChild;
        }

        @Override
        public void run() {
            if (newChild) {
                for (Node child : children) {
                    for (DBEventListener el : listeners) {
                        if (child instanceof File) {
                            el.newFile((File) child);
                        }
                        if (child instanceof Directory) {
                            el.newDirectory((Directory) child);
                        }
                    }
                }
            } else {
                for (Node child : children) {
                    for (DBEventListener el : listeners) {
                        if (child instanceof File) {
                            el.removedFile((File) child);
                        }
                        if (child instanceof Directory) {
                            el.removedDirectory((Directory) child);
                        }
                    }
                }
            }
        }

    }

    private static class SearchPathElement {
        private String name;
        private Directory directory;
        private final SearchPathElement parent;
        private byte[] nameBytes;

        public SearchPathElement(String name, SearchPathElement parent) {
            this.parent = parent;
            this.name = name;
        }

        public SearchPathElement(byte[] nameBytes, SearchPathElement parent) {
            this.parent = parent;
            this.nameBytes = nameBytes;
        }

        public Directory getDirectory() {
            if (directory == null) {
                directory = (Directory) parent.getDirectory().getChild(getName());
            }
            return directory;
        }

        public String getName() {
            if (name == null) {
                name = new String(nameBytes, UTF8);
            }
            return name;
        }

        public void setDirectory(Directory directory) {
            this.directory = directory;
        }
    }
}