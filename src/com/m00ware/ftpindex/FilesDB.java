package com.m00ware.ftpindex;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import com.m00ware.ftpindex.Node.NodeType;
import com.m00ware.ftpindex.indexer.IndexerScheduler;
import com.m00ware.ftpindex.search.SearchIndex;

/**
 * @author Wooden
 *
 */
public interface FilesDB
{
	public String STAT_DBFILE_SIZE = "dbfile.size";

	public void init() throws IOException;
	
	public List<FTP> getFTPs();

	public void removeChild(Directory parent, Node child);
	
	public void removeChildren(Directory parent, List<Node> children);

	public void addChild(Directory parent, Node child);
	
	public void addChildren(Directory parent, List<Node> children);
	
	public void updateNode(Node node);
	
	public void addFTP(FTP ftp);
	
	public FTP getFTP(InetAddress addr, int port);
	
	public FTP getFTP(InetAddress addr, int port, boolean create);
	
	public void registerEventListener(DBEventListener listener);
	
	public void removeEventListener(DBEventListener listener);
	
	public void forceCommit();
	
	public void shutdown();

	public Node createNode(Directory parent, String name, long size, NodeType type);

	public Directory createRootNode(FTP ftp);
	
	public List<Node> getChildren(Directory parent);
	
	public Map<String, Object> getStats();
	
	public void updateFTP(FTP ftp);
	
	public void signalIndexerScheduler(IndexerScheduler is);
	
	public SearchIndex getSearchIndex();
}
