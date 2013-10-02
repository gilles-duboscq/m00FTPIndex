package com.m00ware.ftpindex.raw3;

/**
 * @author Wooden
 * 
 */
public class Raw3Metrics {
    private static final long TIMED_UPDATE_INTERVAL = 1 * 1000;
    private static final float TIMED_SMOOTHING = 0.8f;

    private int numInMemoryBlocks;
    private int numBlocks;
    private int numFiles;
    private int numDirectory;
    private float clusterWritePerSecond;
    private float dirtyClusterPerSecond;
    private float trueDirtyClusterPerSecond;
    private int clusterWriteDelta;
    private int dirtyClusterDelta;
    private int trueDirtyClusterDelta;
    private long lastTimestamp;
    private long indexedSize;

    public void incrementNumInMemoryBlocks(int inc) {
        this.numInMemoryBlocks += inc;
    }

    public void incrementNumBlocks(int inc) {
        this.numBlocks += inc;
    }

    public void incrementNumFiles(int inc) {
        this.numFiles += inc;
    }

    public void incrementNumDirectory(int inc) {
        this.numDirectory += inc;
    }

    public void addClusterWrite() {
        this.clusterWriteDelta++;
        this.checkStamp();
    }

    public void addDirtyCluster(boolean newDirty) {
        this.dirtyClusterDelta++;
        if (newDirty) {
            this.trueDirtyClusterDelta++;
        }
        this.checkStamp();
    }

    public int getNumInMemoryBlocks() {
        return numInMemoryBlocks;
    }

    public int getNumBlocks() {
        return numBlocks;
    }

    public int getNumFiles() {
        return numFiles;
    }

    public int getNumDirectory() {
        return numDirectory;
    }

    public float getClusterWritePerSecond() {
        return clusterWritePerSecond;
    }

    public float getDirtyClusterPerSecond() {
        return dirtyClusterPerSecond;
    }

    public float getTrueDirtyClusterPerSecond() {
        return trueDirtyClusterPerSecond;
    }

    public long getIndexedSize() {
        return indexedSize;
    }

    private synchronized void checkStamp() {
        long newTime = System.currentTimeMillis();
        long deltaTime = newTime - lastTimestamp;
        if (deltaTime > TIMED_UPDATE_INTERVAL) {
            clusterWritePerSecond = clusterWritePerSecond * TIMED_SMOOTHING + (1000 * clusterWriteDelta / deltaTime) * (1 - TIMED_SMOOTHING);
            dirtyClusterPerSecond = dirtyClusterPerSecond * TIMED_SMOOTHING + (1000 * dirtyClusterDelta / deltaTime) * (1 - TIMED_SMOOTHING);
            trueDirtyClusterPerSecond = trueDirtyClusterPerSecond * TIMED_SMOOTHING + (1000 * trueDirtyClusterDelta / deltaTime) * (1 - TIMED_SMOOTHING);
            clusterWriteDelta = 0;
            dirtyClusterDelta = 0;
            trueDirtyClusterDelta = 0;
            lastTimestamp = newTime;
        }
    }
}