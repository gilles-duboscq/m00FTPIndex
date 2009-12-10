package com.m00ware.ftpindex.raw3;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 
 * @author Wooden
 */
public class Extent {
	private final Interval interval;
	private int remaining;
	
	public Extent(Interval interval) {
		this(interval, interval.getSize());
	}
	
	public Extent(Interval interval, int remaining) {
		this.interval = interval;
		this.remaining = remaining;
	}
	
	public Interval getInterval() {
		return interval;
	}
	
	public int getRemaining() {
		return remaining;
	}
	
	public int getOccupied(){
		return this.interval.getSize() - this.remaining;
	}
	
	public synchronized Extent append(ByteBuffer buffer, int reallocMinimum) throws IOException{
		Extent ext = this;
		int rem = ext.getRemaining() - buffer.remaining();
		if(rem < 0){
			ext = ext.getInterval().getPosition().getBlock().getDb().getListingSpaceManager().extend(ext, rem, Math.max(rem, reallocMinimum));
		}
		int offset = ext.getInterval().getEnd() - ext.getRemaining();
		Block block = ext.getInterval().getPosition().getBlock();
		ext.remaining -= buffer.remaining();
		block.writeBufferToBlock(offset, buffer);
		return ext;
	}

	public void writeInt(int value, int offset) throws IOException {
		Position position = this.getInterval().getPosition();
		position.getBlock().writeIntToBlock(offset+position.getOffset(), value);
	}
	
	public void writeBuffer(ByteBuffer buffer, int offset) throws IOException {
		Position position = this.getInterval().getPosition();
		position.getBlock().writeBufferToBlock(offset+position.getOffset(), buffer);
	}
	
	public int readInt(int offset) throws IOException {
		Position position = this.getInterval().getPosition();
		return position.getBlock().readIntFromBlock(offset+position.getOffset());
	}
	
	/**
	 * Remember to return the buffer to the pool
	 * 
	 * @param offset
	 * @param size
	 * @return
	 * @throws IOException
	 */
	public ByteBuffer readBuffer(int offset, int size) throws IOException {
		Position position = this.getInterval().getPosition();
		return position.getBlock().readBufferFromBlock(offset+position.getOffset(), size);
	}
	
	/**
	 * Remember to return the buffer to the pool
	 * 
	 * @return
	 * @throws IOException
	 */
	public ByteBuffer readFullBuffer() throws IOException {
		Position position = this.getInterval().getPosition();
		return position.getBlock().readBufferFromBlock(position.getOffset(), this.getInterval().getSize());
	}

	public synchronized void copyFrom(Extent extent) {
		int size = extent.getOccupied();
		Position fromPos = extent.getInterval().getPosition();
		int offsetInFromBlock = fromPos.getOffset();
		Block fromBlock = fromPos.getBlock();
		int offsetInToBlock = this.getInterval().getEnd() - this.getRemaining();
		Block toBlock = this.getInterval().getPosition().getBlock();
		
		toBlock.copyFrom(fromBlock, offsetInFromBlock, offsetInToBlock, size);
		
		this.remaining -= size;
	}

	public void skip(int i) {
		this.remaining -= i;
	}
	
}
