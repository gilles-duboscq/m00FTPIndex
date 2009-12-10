package com.m00ware.ftpindex.raw3;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.m00ware.ftpindex.raw3.Block.BlockType;

/**
 * @author Wooden
 */
public class SpaceManager {
	private static final int MIN_SMALL_INTERVAL = 1;
	private static final int MAX_SMALL_INTERVAL = 256;
	private static final int MAX_POOLED_LIST = 64;
	
	//#IFDEF STATS
	//private Map<Integer, Integer> intervalUsage;
	//#ENDIF
	private List<Interval>[] smallIntervals;
	private NavigableMap<Integer, List<Interval>> otherIntervalsBySize;
	private NavigableMap<Integer, Interval> intervalsByPosition;
	private List<List<Interval>> intervalListPool;
	private RawFilesDB3 db;
	private Interval lastSplit;
	private BlockType type;
	
	@SuppressWarnings("unchecked")
	public SpaceManager(RawFilesDB3 db, BlockType type) {
		this.db = db;
		this.smallIntervals = new List[MAX_SMALL_INTERVAL-MIN_SMALL_INTERVAL];
		for(int i = 0; i < smallIntervals.length; i++){
			this.smallIntervals[i] = new LinkedList<Interval>();
		}
		this.otherIntervalsBySize = new TreeMap<Integer, List<Interval>>();
		this.intervalsByPosition = new TreeMap<Integer, Interval>();
		this.intervalListPool = new LinkedList<List<Interval>>();
		this.type = type;
		//#IFDEF STATS
		//this.intervalUsage = new HashMap<Integer, Integer>();
		//#ENDIF
	}
	
	public Extent allocExtent(int size) throws IOException{
		return new Extent(this.alloc(size));
	}
	
	public Extent extend(Extent extent, int additionalSpace) throws IOException{
		return this.extend(extent, additionalSpace, additionalSpace);
	}
	
	public synchronized Extent extend(Extent extent, int additionalSpace, int additionalOnRealloc) throws IOException{
		assert(additionalOnRealloc >= additionalSpace);
		int end = extent.getInterval().getEnd();
		Interval nextInterval = this.intervalsByPosition.get(end);
		if(nextInterval != null && nextInterval.getSize() >= additionalSpace){
			extent.getInterval().mergeAfter(nextInterval);
			this.remove(nextInterval);
			return extent;
		}else{
			//re-alloc&copy&free
			Extent newExtent = this.allocExtent(extent.getInterval().getSize()+additionalOnRealloc);
			newExtent.copyFrom(extent);
			this.free(extent.getInterval());
			return newExtent;
		}
	}
	
	public synchronized Interval alloc(int size) throws IOException{
		if(size >= MIN_SMALL_INTERVAL){
			int bucket = size;
			while(bucket <= MAX_SMALL_INTERVAL){
				List<Interval> bin = smallIntervals[bucket+MIN_SMALL_INTERVAL];
				if(!bin.isEmpty()){
					Interval interval = bin.remove(0);
					intervalsByPosition.remove(interval.getPosition());
					return this.cutAndReturn(interval, size);
				}
				if(bucket == size){ // locality heuristic : alloc last split interval if there is no perfect match
					if(lastSplit.getSize() >= size){
						this.remove(lastSplit);
						return this.cutAndReturn(lastSplit, size);
					}
				}
				bucket++;
			}
		}
		Entry<Integer, List<Interval>> ceilingEntry = otherIntervalsBySize.ceilingEntry(size);
		Interval interval = null;;
		if(ceilingEntry != null){
			Integer foundSize = ceilingEntry.getKey();
			if(foundSize != size){
				if(lastSplit.getSize() >= size){
					this.remove(lastSplit);
					return this.cutAndReturn(lastSplit, size);
				}
			}
			List<Interval> intervalList = ceilingEntry.getValue();
			interval = intervalList.remove(0); // it must NOT be empty that's the contract...
			if(intervalList.isEmpty()){
				otherIntervalsBySize.remove(foundSize);
				this.poolIntervalList(intervalList);
			}
			intervalsByPosition.remove(interval.getPosition());
			return this.cutAndReturn(interval, size);
		}else{
			Block block = this.db.allocNewBlock(type);
			return this.cutAndReturn(new Interval(new Position(block, 0), block.getSize()), size);
		}
	}
	
	public synchronized void free(Interval interval){
		//merge after
		Entry<Integer, Interval> ceilingEntry = intervalsByPosition.ceilingEntry(interval.getAbsolutePosition());
		if(ceilingEntry != null){
			final int afterPosition = ceilingEntry.getKey();
			if(afterPosition == interval.getAbsolutePosition() || afterPosition < interval.getEnd())
				throw new IllegalArgumentException("Something got messed up : trying to insert an interval at an occupied position??");
			final Interval afterInterval = ceilingEntry.getValue();
			if(interval.mergeAfter(afterInterval)){
				remove(afterInterval);
			}
		}
		//merge before
		Entry<Integer, Interval> lowerEntry = intervalsByPosition.lowerEntry(interval.getAbsolutePosition());
		if(lowerEntry != null){
			if(lowerEntry.getValue().getEnd() >= interval.getAbsolutePosition())
				throw new IllegalArgumentException("Something got messed up : trying to insert an interval at an occupied position??");
			final Interval beforeInterval = lowerEntry.getValue();
			if(interval.mergeAfter(beforeInterval)){
				remove(beforeInterval);
			}
		}
		//real insertion
		this.freeNoMerge(interval);
	}

	public synchronized void freeNoMerge(Interval interval){
		final int size = interval.getSize();
		if(size >= MIN_SMALL_INTERVAL && size <= MAX_SMALL_INTERVAL){
			smallIntervals[size+MIN_SMALL_INTERVAL].add(interval);
		}else{
			List<Interval> list = otherIntervalsBySize.get(size);
			if(list == null){
				list = this.getPooledIntervalList();
				otherIntervalsBySize.put(size, list);
			}
			list.add(interval);
		}
		intervalsByPosition.put(interval.getAbsolutePosition(), interval);
	}
	
	public BlockType getType() {
		return type;
	}
	
	private void remove(final Interval interval) {
		intervalsByPosition.remove(interval.getAbsolutePosition());
		final int size = interval.getSize();
		if(size >= MIN_SMALL_INTERVAL && size <= MAX_SMALL_INTERVAL){
			smallIntervals[size+MIN_SMALL_INTERVAL].remove(interval);
		}else{
			List<Interval> list = otherIntervalsBySize.get(size);
			list.remove(interval);// NPE if the contract got broken
			if(list.isEmpty())
				otherIntervalsBySize.remove(size);
		}
	}

	private Interval cutAndReturn(Interval interval, int size){
		if(interval.getSize() > size){
			lastSplit = interval.cutToSize(size);
			this.freeNoMerge(lastSplit);
		}
		return interval;
	}
	
	private void poolIntervalList(List<Interval> list){
		if(this.intervalListPool.size() < MAX_POOLED_LIST){
			this.intervalListPool.add(list);
		}
	}
	
	private List<Interval> getPooledIntervalList(){
		if(this.intervalListPool.size() > 0)
			return this.intervalListPool.remove(0);
		return new LinkedList<Interval>();
	}

}
