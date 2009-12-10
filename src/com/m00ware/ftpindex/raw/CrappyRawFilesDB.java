package com.m00ware.ftpindex.raw;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.m00ware.ftpindex.DBEventListener;
import com.m00ware.ftpindex.Directory;
import com.m00ware.ftpindex.FTP;
import com.m00ware.ftpindex.File;
import com.m00ware.ftpindex.FilesDB;
import com.m00ware.ftpindex.HardDirectory;
import com.m00ware.ftpindex.Node;
import com.m00ware.ftpindex.Node.NodeType;
import com.m00ware.ftpindex.indexer.IndexerScheduler;
import com.m00ware.ftpindex.search.SearchIndex;

/**
 * @author Wooden
 *
 */
public class CrappyRawFilesDB implements FilesDB
{
	private static final int TYPE_FILE = 0x01;
	private static final int TYPE_DIRECTORY = 0x02;
	private static final int MAGIC = 0xf11ebabe;
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private java.io.File dbFile;
	private List<FTP> ftps;
	private Set<DBEventListener> listeners;
	private ExecutorService eventExecutor;
	
	public CrappyRawFilesDB(java.io.File dbFile)
	{
		this.dbFile = dbFile;
		this.ftps = new LinkedList<FTP>();
		this.listeners = Collections.synchronizedSet(new HashSet<DBEventListener>());
		this.eventExecutor = new ThreadPoolExecutor(1, 2, 10L, TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>());
	}
	
	@Override
	public void init()
	{
		if(!this.dbFile.exists())
			return;
		RandomAccessFile raf = null;
		try{
			raf = new RandomAccessFile(this.dbFile, "r");
			if(raf.readInt() != MAGIC){
				throw new IOException("MAGIC not found");
			}
			int numFTP = raf.readInt();
			ftps = new ArrayList<FTP>(numFTP);
			for(int i = 0; i < numFTP; i++){
				String host = readString(raf);
				int port = raf.readShort()&0xffff;
				int indexingId = raf.readInt();
				String separator = readString(raf);
				Date lastIndexing = new Date(raf.readLong());
				Directory root = (Directory) this.readNode(null, raf);
				root.setIndexingId(indexingId);
				FTP ftp = new FTP(this, InetAddress.getByName(host), port, root, indexingId, lastIndexing);
				ftp.setPathSeparator(separator);
				ftps.add(ftp);
			}
		}catch(IOException ioe){
			ioe.printStackTrace();
		}finally{
			if(raf != null){
				try{
					raf.close();
				}catch(IOException ioe){}
			}
		}
	}
	
	public void writeDB()
	{
		System.out.println("Saving DB...");
		Deque<FixupDirectory> fixups = new LinkedList<FixupDirectory>();
		RandomAccessFile raf = null;
		try{
			this.dbFile.createNewFile();
			raf = new RandomAccessFile(this.dbFile, "rw");
			raf.writeInt(MAGIC);
			raf.writeInt(ftps.size());
			for(FTP ftp : ftps){
				writeString(raf, ftp.getAddress().getHostName());
				raf.writeShort((short) ftp.getPort());
				raf.writeInt(ftp.getCurrentIndexingId());
				writeString(raf, ftp.getPathSeparator());
				raf.writeLong(ftp.getLastIndexing().getTime());
				fixups.push(this.writeNode(ftp.getRoot(), raf));
			}
			while(!fixups.isEmpty()){
				FixupDirectory fd = fixups.pop();
				int writePos = (int) raf.getFilePointer();
				raf.writeInt(fd.getDirectory().getChildren().size());
				for(Node node : fd.getDirectory().getChildren()){
					FixupDirectory newFD = this.writeNode(node, raf);
					if(newFD != null)
						fixups.push(newFD);
				}
				int currentPos = (int) raf.getFilePointer();
				raf.seek(fd.getFixupPos());
				raf.writeInt(writePos);
				raf.seek(currentPos);
			}
			System.out.println("DB Saved");
		}catch(IOException ioe){
			ioe.printStackTrace();
		}finally{
			if(raf != null){
				try{
					raf.close();
				}catch(IOException e){}
			}
		}
	}
	
	/*private void deepLoad()
	{
		for(FTP ftp : ftps){
			deepLoad(ftp.getRoot());
		}
	}
	
	private void deepLoad(Directory directory)
	{
		for(Node node : directory.getChildren()){
			if(node instanceof Directory)
				deepLoad((Directory) node);
		}
	}*/

	private Node readNode(Directory parent, RandomAccessFile raf) throws IOException
	{
		int type = raf.read();
		String name = readString(raf);
		Date date = new Date(raf.readLong());
		long size = raf.readLong();
		Node node = null;
		if(type == TYPE_FILE){
			node = new File(parent, name, size);
		}else if(type == TYPE_DIRECTORY){
			int childListPos = raf.readInt();
			node = new HardDirectory(parent, name, this.getChildren((Directory) node, childListPos, raf));
			node.setSize(size);
		}
		if(node != null)
			node.setDate(date);
		return node;
	}
	
	private FixupDirectory writeNode(Node node, RandomAccessFile raf) throws IOException{
		if(node instanceof File)
			raf.write((byte) TYPE_FILE);
		else if(node instanceof Directory)
			raf.write((byte) TYPE_DIRECTORY);
		writeString(raf, node.getName());
		if(node.getDate() != null)
			raf.writeLong(node.getDate().getTime());
		else
			raf.writeLong(0x0000000000000000);
		raf.writeLong(node.getSize());
		if(node instanceof Directory){
			FixupDirectory fixup = new FixupDirectory((int) raf.getFilePointer(), (Directory)node);
			raf.writeInt(0xffffffff);
			return fixup;
		}
		return null;
	}
	
	@Override
	public void addChild(Directory parent, Node child)
	{
		eventExecutor.execute(new ChildEventRunner(listeners, child, true));
		//System.out.println("ADD: '"+parent.getName()+"' now contains '"+child.getName()+"' ("+child.getClass().getSimpleName()+") "+child.getSize()+"Bytes");
	}
	
	@Override
	public List<Node> getChildren(Directory parent)
	{
		// TODO Auto-generated method stub
		return null;
	}

	public List<Node> getChildren(Directory directory, long childListPos, RandomAccessFile raf) throws IOException
	{
		List<Node> children = new LinkedList<Node>();
		if(childListPos < 0)
			return children;
		long oldPos = raf.getFilePointer();
		raf.seek(childListPos);
		int childrenCount = raf.readInt();
		for(int i = 0; i < childrenCount; i++){
			children.add(this.readNode(directory, raf));
		}
		raf.seek(oldPos);
		return children;
	}

	@Override
	public List<FTP> getFTPs()
	{
		return ftps;
	}

	@Override
	public void removeChild(Directory parent, Node child)
	{
		eventExecutor.execute(new ChildEventRunner(listeners, child, false));
		System.out.println("REM: '"+parent.getName()+"' no longer contains '"+child.getName()+"' ("+child.getClass().getSimpleName()+")");
	}
	
	private static String readString(RandomAccessFile raf) throws IOException
	{
		int len = raf.readShort()&0xffff;
		byte[] data = new byte[len];
		raf.read(data);
		return new String(data, UTF8);
	}
	
	private static void writeString(RandomAccessFile raf, String str) throws IOException
	{
		byte[] data = str.getBytes(UTF8);
		raf.writeShort((short) data.length);
		raf.write(data);
	}
	
	private static class FixupDirectory
	{
		private int fixupPos;
		private Directory directory;
		public FixupDirectory(int position, Directory directory)
		{
			this.fixupPos = position;
			this.directory = directory;
		}
		public int getFixupPos()
		{
			return fixupPos;
		}
		public Directory getDirectory()
		{
			return directory;
		}
		
	}

	@Override
	public void addFTP(FTP ftp)
	{
		ftps.add(ftp);
		System.out.println("New FTP : "+ftp.getAddress()+":"+ftp.getPort());
		for(DBEventListener listener : this.listeners){
			listener.newFtp(ftp);
		}
	}

	//bad...
	public Class<? extends Directory> getDirectoryClass()
	{
		return Directory.class;
	}

	public Class<? extends File> getFileClass()
	{
		return File.class;
	}

	@Override
	public FTP getFTP(InetAddress addr, int port)
	{
		return this.getFTP(addr, port, true);
	}

	@Override
	public FTP getFTP(InetAddress addr, int port, boolean create)
	{
		for(FTP ftp : ftps){
			if(ftp.getAddress().equals(addr) && ftp.getPort() == port){
				return ftp;
			}
		}
		FTP newFtp = new FTP(this, addr, port);
		this.addFTP(newFtp);
		return newFtp;
	}

	@Override
	public void registerEventListener(DBEventListener listener)
	{
		listeners.add(listener);
	}

	@Override
	public void removeEventListener(DBEventListener listener)
	{
		listeners.remove(listener);
	}
	
	private static class ChildEventRunner implements Runnable{
		private Set<DBEventListener> listeners;
		private Node child;
		private boolean newChild;

		public ChildEventRunner(Set<DBEventListener> listeners, Node child, boolean newChild)
		{
			this.listeners = listeners;
			this.child = child;
			this.newChild = newChild;
		}

		@Override
		public void run()
		{
			if(newChild){
				for(DBEventListener el : listeners){
					if(child instanceof File){
						el.newFile((File) child);
					}
					if(child instanceof Directory){
						el.newDirectory((Directory) child);
					}
				}
			}else{
				for(DBEventListener el : listeners){
					if(child instanceof File){
						el.removedFile((File) child);
					}
					if(child instanceof Directory){
						el.removedDirectory((Directory) child);
					}
				}
			}
		}
		
	}

	@Override
	public void shutdown()
	{
		//FTP.shutdown();
		this.writeDB();
		this.eventExecutor.shutdown();
	}

	@Override
	public void forceCommit()
	{
		this.writeDB();
	}

	public void deepSizeUpdate()
	{
		for(FTP ftp : this.getFTPs()){
			deepSizeUpdate(ftp.getRoot());
		}
	}
	
	private static void deepSizeUpdate(Directory directory)
	{
		long size = 0;
		for(Node node : directory.getChildren()){
			if(node instanceof File){
				size += node.getSize();
			}else if(node instanceof Directory){
				deepSizeUpdate((Directory) node);
				size += node.getSize();
			}
		}
		directory.setSize(size);
	}

	@Override
	public Node createNode(Directory parent, String name, long size, NodeType type)
	{
		if(type == NodeType.file){
			return new File(parent, name, size);
		}else if(type == NodeType.directory){
			return new HardDirectory(parent, name);
		}
		return null;
	}

	@Override
	public Directory createRootNode(FTP ftp)
	{
		return new HardDirectory(ftp, "");
	}

	@Override
	public void updateNode(Node node)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addChildren(Directory parent, List<Node> children)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeChildren(Directory parent, List<Node> children)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public Map<String, Object> getStats()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateFTP(FTP ftp)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void signalIndexerScheduler(IndexerScheduler is)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public SearchIndex getSearchIndex()
	{
		// TODO Auto-generated method stub
		return null;
	}
}
