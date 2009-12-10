package com.m00ware.ftpindex.raw3;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Wooden
 *
 */
public class Block {
	public static final int CLUSTER_SIZE = 0x1000;
	private int size;
	private int position;
	private boolean[] dirtyBitmap;
	private ByteBuffer buffer;
	private RawFilesDB3 db;
	private BlockType type;
	
	public enum BlockType {
		nodes,
		listings
	}
	
	public Block(int size, int position, RawFilesDB3 db, BlockType type) {
		this.size = size;
		this.position = position;
		int bitmapSize = size/CLUSTER_SIZE;
		bitmapSize += size%CLUSTER_SIZE > 0 ? 1 : 0;
		this.dirtyBitmap = new boolean[bitmapSize];
		this.db = db;
		this.type = type;
	}
	
	public synchronized void commit() throws IOException{
		if(!this.isInMemory())
			return;
		int offset = 0;
		for(int i = 0; i < dirtyBitmap.length; i++){
			if(dirtyBitmap[i]){
				buffer.position(offset);
				buffer.limit(offset+CLUSTER_SIZE);
				this.db.writeChunk(new WritableChunk(buffer.slice(), this.getPosition()+offset));
				dirtyBitmap[i] = false;
			}
			offset += CLUSTER_SIZE;
		}
	}
	
	public synchronized void writeBufferToBlock(int offset, ByteBuffer buffer) throws IOException{
		this.ensureInMemory();
		final int length = buffer.remaining();
		this.buffer.position(offset);
		this.buffer.put(buffer);
		int pos = offset;
		while(pos < length+offset){
			this.dirtyBitmap[pos/CLUSTER_SIZE] = true;
			pos += CLUSTER_SIZE;
		}
		this.dirtyBitmap[(length+offset-1)/CLUSTER_SIZE] = true;
	}
	
	/**
	 * Remember to release this buffer to the pool
	 * 
	 * @param offset the starting offset in this block
	 * @param size number of bytes to read
	 * @return a ByteBuffer containing a copy of the data from this block
	 * @throws IOException
	 */
	public synchronized ByteBuffer readBufferFromBlock(int offset, int size) throws IOException{
		this.ensureInMemory();
		ByteBuffer buffer = this.db.getBufferPool().getBuffer(size);
		this.buffer.position(offset);
		this.buffer.limit(offset+size);
		buffer.put(this.buffer);
		buffer.flip();
		this.buffer.limit(this.buffer.capacity());
		return buffer;
	}
	
	public synchronized void writeIntToBlock(int offset, int value) throws IOException{
		this.ensureInMemory();
		this.buffer.putInt(offset, value);
		this.dirtyBitmap[offset/CLUSTER_SIZE] = true;
		this.dirtyBitmap[(offset+4)/CLUSTER_SIZE] = true;
	}
	
	public synchronized int readIntFromBlock(int offset) throws IOException{
		this.ensureInMemory();
		return buffer.getInt(offset);
	}
	
	public synchronized void pruneFromMemory() throws IOException{
		this.commit();
		this.buffer = null;
	}
	
	public Extent getExtent(int offset) throws IOException{
		int sz = this.readIntFromBlock(offset);
		return new Extent(new Interval(new Position(this, offset), sz));
	}
	
	public int getSize() {
		return size;
	}
	
	public int getPosition() {
		return position;
	}
	
	public RawFilesDB3 getDb() {
		return db;
	}
	
	public boolean isInMemory(){
		return buffer != null;
	}

	private void ensureInMemory() throws IOException {
		if(!this.isInMemory()){
			this.buffer = this.db.loadBufferFromFile(this);
		}
	}
	
	public BlockType getType() {
		return type;
	}

	public void copyFrom(Block fromBlock, int offsetInFromBlock, int offsetInToBlock, int size) {
		assert(offsetInToBlock+size < this.size);
		// ensure lock order to avoid deadlock
		Block firstLock;
		Block secondLock;
		if(fromBlock.position > this.position){
			firstLock = fromBlock;
			secondLock = this;
		}else{
			firstLock = this;
			secondLock = fromBlock;
		}
		synchronized (firstLock) {
			synchronized (secondLock) {
				final ByteBuffer fromBuffer = fromBlock.buffer;
				fromBuffer.position(offsetInFromBlock);
				fromBuffer.limit(offsetInFromBlock+size);
				this.buffer.position(offsetInToBlock);
				this.buffer.put(fromBuffer);
				fromBuffer.limit(fromBuffer.capacity());
			}
		}
	}
}
