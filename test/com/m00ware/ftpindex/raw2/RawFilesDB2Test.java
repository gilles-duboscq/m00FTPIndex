package com.m00ware.ftpindex.raw2;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.m00ware.ftpindex.Directory;
import com.m00ware.ftpindex.FTP;
import com.m00ware.ftpindex.File;
import com.m00ware.ftpindex.FilesDB;
import com.m00ware.ftpindex.Node;
import com.m00ware.ftpindex.Node.NodeType;
import com.m00ware.ftpindex.raw.RawFilesDB;


/**
 * @author Wooden
 *
 */
public class RawFilesDB2Test
{

	// false test...
	@Test
	public void testCreateUseDB() throws Exception
	{
		java.io.File dbFile = new java.io.File("testDb.mdb");
		if(dbFile.exists())
			dbFile.delete();

		RawFilesDB db = new RawFilesDB(dbFile);
		db.init();
		db.setUnusableVacantSpaceThreshold(0);
		dbFile.deleteOnExit();
		FTP ftp  = db.getFTP(InetAddress.getByName("127.0.0.1"), 21);
		Directory root = ftp.getRoot();
		Directory d1 = (Directory) db.createNode(root, "d1", 0, NodeType.directory);
		Directory d2 = (Directory) db.createNode(root, "d2", 0, NodeType.directory);
		Directory d3 = (Directory) db.createNode(root, "d3", 0, NodeType.directory);
		Directory d4 = (Directory) db.createNode(root, "d4", 0, NodeType.directory);
		List<Node> children = new ArrayList<Node>(3);
		children.add(d2);
		children.add(d3);
		children.add(d4);
		root.addChild(d1);
		root.addChildren(children);
		
		File f11 = (File) db.createNode(d1, "f11", 1024, NodeType.file);
		File f12 = (File) db.createNode(d1, "f12", 1024, NodeType.file);
		File f13 = (File) db.createNode(d1, "f13", 1024, NodeType.file);
		children = new ArrayList<Node>(2);
		children.add(f12);
		children.add(f13);
		d1.addChild(f11);
		d1.addChildren(children);
		
		Directory d33 = (Directory) db.createNode(d3, "d33", 0, NodeType.directory);
		File f331 = (File) db.createNode(d33, "f331", 4096, NodeType.file);
		File f332 = (File) db.createNode(d33, "f332", 2048, NodeType.file);
		Directory d333 = (Directory) db.createNode(d33, "d333", 0, NodeType.directory);
		children = new ArrayList<Node>(3);
		children.add(f331);
		children.add(f332);
		children.add(d333);
		d33.addChildren(children);
		
		d3.addChild(d33);
		System.out.println("Built tree : ");
		dumpDB(db);
		System.out.println("Removing nodes from RAM...");
		root.releaseMemory();
		System.out.println("Resulting Tree : ");
		dumpDB(db);
		System.out.println("Removing d33...");
		d33 = (Directory) ftp.getNodeFromPath("/d3/d33");
		d3 = (Directory) ftp.getNodeFromPath("/d3");
		d3.removeChild(d33);
		System.out.println("Resulting Tree : ");
		dumpDB(db);
		System.out.println("Adding d41 ...");
		Directory d41 = (Directory) db.createNode(d4, "d41", 0, NodeType.directory);
		File f411 = (File) db.createNode(d41, "f411", 4096, NodeType.file);
		File f412 = (File) db.createNode(d41, "f412", 2048, NodeType.file);
		Directory d413 = (Directory) db.createNode(d41, "d413", 0, NodeType.directory);
		children = new ArrayList<Node>(3);
		children.add(f411);
		children.add(f412);
		children.add(d413);
		d41.addChildren(children);
		d4 = (Directory) ftp.getNodeFromPath("/d4");
		d4.addChild(d41);
		System.out.println("Resulting Tree : ");
		dumpDB(db);
		System.out.println("Removing nodes from RAM...");
		root.releaseMemory();
		System.out.println("Removing d41...");
		d41 = (Directory) ftp.getNodeFromPath("/d4/d41");
		d4 = (Directory) ftp.getNodeFromPath("/d4");
		d4.removeChild(d41);
		System.out.println("Removing nodes from RAM...");
		root.releaseMemory();
		System.out.println("Resulting Tree : ");
		dumpDB(db);
		System.out.println("Removing nodes from RAM...");
		root.releaseMemory();
		System.out.println("Adding d21 ...");
		Directory d21 = (Directory) db.createNode(d2, "d21", 0, NodeType.directory);
		File f211 = (File) db.createNode(d21, "f211", 4096, NodeType.file);
		File f212 = (File) db.createNode(d21, "f212", 2048, NodeType.file);
		Directory d213 = (Directory) db.createNode(d21, "d213", 0, NodeType.directory);
		children = new ArrayList<Node>(3);
		children.add(f211);
		children.add(f212);
		children.add(d213);
		d21.addChildren(children);
		d2 = (Directory) ftp.getNodeFromPath("/d2");
		d2.addChild(d21);
		System.out.println("Resulting Tree : ");
		dumpDB(db);
		System.out.println("Closing db...");
		db.shutdown();
		System.out.println("Re-openning...");
		db = new RawFilesDB(dbFile);
		db.init();
		db.setUnusableVacantSpaceThreshold(0);
		System.out.println("Resulting Tree : ");
		dumpDB(db);
	}
	
	public static void dumpDB(FilesDB db)
	{
		for(FTP ftp : db.getFTPs())
		{
			System.out.println("FTP : " + ftp.getAddress().getHostName() + ":" + ftp.getPort());
			dumpDirectory(ftp.getRoot(), 0);
		}
	}

	public static void dumpDirectory(Directory directory, int indent)
	{
		String indentStr = "";
		for(int i = 0; i < indent; i++)
			indentStr += "  ";
		System.out.println(indentStr + "+ " + directory.getName());
		indentStr += "  ";
		for(Node node : directory.getChildren())
		{
			if(node instanceof Directory)
				dumpDirectory((Directory) node, indent + 1);
			else if(node instanceof com.m00ware.ftpindex.File) System.out.println(indentStr + "- " + node.getName());
		}
	}
}
