package com.m00ware.ftpindex;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Wooden
 *
 */
public abstract class Directory extends Node
{
	
	public Directory(Directory parent, String name, long size)
	{
		this(parent, name);
	}
	
	public Directory(Directory parent, String name)
	{
		super(parent, name, 0);
	}
	
	public Directory(FTP ftp, String name)
	{
		super(ftp, name, 0);
	}

	public List<Node> getChildren(){
		return this.getChildren(true);
	}
	
	public abstract List<Node> getChildren(boolean load);
	
	public void removeChildren(List<Node> children)
	{
		if(children.isEmpty())
			return;
		List<Node> list = this.getChildren(false);
		if(list != null){
			list.removeAll(children);
		}
		long delta = 0;
		for(Node child : children){
			delta -= child.getSize();
		}
		this.forwardDeltaSize(delta);
		this.getFtp().getDb().removeChildren(this, children);
	}
	
	public void removeChild(Node child)
	{
		List<Node> list = this.getChildren(false);
		if(list != null){
			list.remove(child);
		}
		this.forwardDeltaSize(-child.getSize());
		this.getFtp().getDb().removeChild(this, child);
	}
	
	public List<Node> getOutDatedChildren()
	{
		
		int currentIndexingId = this.getIndexingId();
		List<Node> outDated = new LinkedList<Node>();
		for(Node child : this.getChildren()){
			if(child.getIndexingId() != currentIndexingId)
				outDated.add(child);
		}
		return outDated;
	}
	
	public void addChildren(List<Node> children)
	{
		if(children.isEmpty())
			return;
		long delta = 0;
		for(Node child : children){
			child.setIndexingId(this.getIndexingId());
			delta += child.getSize();
		}
		this.forwardDeltaSize(delta);
		this.getChildren().addAll(children);
		this.getFtp().getDb().addChildren(this, children);
	}
	
	public void addChild(Node child)
	{
		child.setIndexingId(this.getIndexingId());
		this.getChildren().add(child);
		this.forwardDeltaSize(child.getSize());
		this.getFtp().getDb().addChild(this, child);
	}
	
	public List<Directory> checkAddChilds(List<TempChild> tempChildren)
	{
		List<Directory> subDirs = new LinkedList<Directory>();
		List<Node> invalidNodes = new LinkedList<Node>();
		List<Node> newChildren = new LinkedList<Node>();
		for(TempChild tc : tempChildren){
			boolean found = false;
			for(Node oldChild : this.getChildren()){
				if(oldChild.getName().equals(tc.getName())){
					if(tc.getType() == oldChild.getNodeType()){
						boolean needsUpdate = 
							oldChild.isSuspect()
							|| this.getIndexingId() != oldChild.getIndexingId()
							|| oldChild.getSize() != tc.getSize();
						oldChild.setSuspect(false);
						oldChild.setOutDated(false);
						oldChild.setIndexingId(this.getIndexingId());
						oldChild.setLastSeen(new Date(System.currentTimeMillis()));
						if(tc.getDate() != null){
							oldChild.setDate(tc.getDate().getTime());
							needsUpdate = needsUpdate || !tc.getDate().equals(oldChild.getDate());
						}else{
							oldChild.setSuspect(true);
							System.out.println("Null date for "+oldChild.getPath());
						}
						if(oldChild instanceof File)
							((File) oldChild).setSize(tc.getSize());
						if(oldChild instanceof Directory)
							subDirs.add((Directory)oldChild);
						if(needsUpdate)
							this.getFtp().getDb().updateNode(oldChild);
						found = true;
						break;
					}else{
						invalidNodes.add(oldChild);
					}
				}
			}
			if(!found){
				Node newNode = this.getFtp().getDb().createNode(this, tc.getName(), tc.getSize(), tc.getType());
				if(tc.getDate() != null){
					newNode.setDate(tc.getDate().getTime());
				}else{
					newNode.setSuspect(true);
					System.out.println("Null date for "+newNode.getPath());
				}
				newNode.setLastSeen(new Date(System.currentTimeMillis()));
				newChildren.add(newNode);
				if(newNode instanceof Directory)
					subDirs.add((Directory)newNode);
			}
		}
		this.addChildren(newChildren);
		this.removeChildren(invalidNodes);
		return subDirs;
	}

	/*package*/ void setFTP(FTP ftp)
	{
		this.ftp = ftp;
	}

	public Node getChild(String name)
	{
		for(Node node : this.getChildren()){
			if(name.equals(node.getName())){
				return node;
			}
		}
		return null;
	}

	public void forwardDeltaSize(long delta)
	{
		if(delta == 0)
			return;
		if(!this.isRoot())
			this.getParent().forwardDeltaSize(delta);
		this.setSize(this.size + delta);
	}
	
	@Override
	public void setSize(long size)
	{
		super.setSize(size);
		this.getFtp().getDb().updateNode(this);
	}
	
	public void forceSize(long size)
	{
		super.setSize(size);
	}
	
	@Override
	public NodeType getNodeType()
	{
		return NodeType.directory;
	}
	
	public void releaseMemory(){
		//this.softChildren = new SoftReference<List<Node>>(null);
	}
	
	@Override
	public String toString()
	{
		return "Directory : "+(this.isRoot() ? "(Root)" : "")+super.toString();
	}
}
