package com.m00ware.ftpindex.raw3;

/**
 * @author Wooden
 *
 */
public class Interval{
	private Position position;
	private int size;
	
	public Interval(Position position, int size) {
		this.position = position;
		this.size = size;
	}
	
	public int getAbsolutePosition(){
		return this.position.getAbsolutePosition();
	}
	
	public Position getPosition() {
		return position;
	}
	
	public int getSize() {
		return size;
	}
	
	/**
	 * @return the end of the interval (exclusive)
	 */
	public int getEnd(){
		return this.getAbsolutePosition() + this.size;
	}
	
	public Interval cutToSize(int size){
		assert(size < this.size);
		Interval ret = new Interval(new Position(this.position.getBlock(), this.position.getOffset()+size), this.size - size);
		this.size = size;
		return ret;
	}
	
	public boolean mergeBefore(Interval interval){
		if(interval.getAbsolutePosition()+interval.size != this.getAbsolutePosition())
			return false;
		this.position = interval.position;
		return true;
	}
	
	public boolean mergeAfter(Interval interval){
		if(this.getAbsolutePosition()+this.size != interval.getAbsolutePosition())
			return false;
		this.size += interval.size;
		return true;
	}

}
