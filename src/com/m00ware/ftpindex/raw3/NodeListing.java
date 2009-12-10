package com.m00ware.ftpindex.raw3;

import java.util.List;

import com.m00ware.ftpindex.Node;

/**
 * @author Wooden
 *
 */
public class NodeListing {
	private Extent extent;
	private RawDirectory directory;
	
	public NodeListing(Extent extent, RawDirectory directory) {
		this.extent = extent;
		this.directory = directory;
	}
	
	public List<Node> getNodes(){
		return null; // TODO
	}

}
