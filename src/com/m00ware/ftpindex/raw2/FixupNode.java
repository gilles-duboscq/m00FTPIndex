package com.m00ware.ftpindex.raw2;

import com.m00ware.ftpindex.Node;

public class FixupNode extends Fixup
{
	private Node node;
	public FixupNode(int position, Node node)
	{
		super(position);
		this.node = node;
	}
	public Node getNode()
	{
		return node;
	}
	
	@Override
	public String toString()
	{
		return "FixupNode for "+node+super.toString();
	}
}