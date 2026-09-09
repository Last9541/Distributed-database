package internal.lsm;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Config {
    private String dataDir="./data";
    private int memtableMaxBytes=67108864;
    private int blockSize=8192;
    private double bloomFalsePositive=0.01;
    private int walFsyncEveryN=1;
    private String compression="off";
    private String log_level="info";
    private int rollSize=67108864;
    private int maxImmutableTables=4;
    @JsonIgnore
    private int bloomFilterSizePerKey;
    @JsonIgnore
    private int bloomHashingFunctionNumber;
    private int refreshN=10;
    private int blockCacheMb=64;
    private boolean cacheIndexBlocks=true;
    private int maxOpenFiles=1024;
    private int sizeTieredFanIn=4;
    private float sizeTieredSizeRatio=2.0f;
    private int tombstoneGraceSeconds=86400;
    private int compactionMaxConcurrent=1;
    private int compactionIoMbPerS=0;
    private int l0CompactionTrigger=8;
    private int l0StopWrites=20;

    public Config()
    {

    }

    public int getSizeTieredFanIn() {
        return sizeTieredFanIn;
    }

    public float getSizeTieredSizeRatio() {
        return sizeTieredSizeRatio;
    }

    public int getTombstoneGraceSeconds() {
        return tombstoneGraceSeconds;
    }

    public int getCompactionMaxConcurrent() {
        return compactionMaxConcurrent;
    }

    public int getCompactionIoMbPerS() {
        return compactionIoMbPerS;
    }

    public int getL0CompactionTrigger() {
        return l0CompactionTrigger;
    }

    public int getL0StopWrites() {
        return l0StopWrites;
    }

    public int getMaxOpenFiles() {
        return maxOpenFiles;
    }

    public void setMaxOpenFiles(int maxOpenFiles) {
        this.maxOpenFiles = maxOpenFiles;
    }

    public int getBlockCacheMb() {
        return blockCacheMb;
    }

    public void setBlockCacheMb(int blockCacheMb) {
        this.blockCacheMb = blockCacheMb;
    }

    public boolean isCacheIndexBlocks() {
        return cacheIndexBlocks;
    }

    public void setCacheIndexBlocks(boolean cacheIndexBlocks) {
        this.cacheIndexBlocks = cacheIndexBlocks;
    }

    public void configGenerateBloomValues()
    {
        bloomFilterSizePerKey =(int)Math.ceil(-Math.log(bloomFalsePositive) / Math.pow(Math.log(2), 2));
        bloomHashingFunctionNumber = (int)Math.round(bloomFilterSizePerKey * Math.log(2));
    }

    @JsonIgnore
    public int getBloomHashingFunctionNumber() {
        return bloomHashingFunctionNumber;
    }

    @JsonIgnore
    public void setBloomHashingFunctionNumber(int bloomHashingFunctionNumber) {
        this.bloomHashingFunctionNumber = bloomHashingFunctionNumber;
    }

    @JsonIgnore
    public int getBloomFilterSizePerKey() {
        return bloomFilterSizePerKey;
    }

    @JsonIgnore
    public void setBloomFilterSizePerKey(int bloomFilterSizePerKey) {
        this.bloomFilterSizePerKey = bloomFilterSizePerKey;
    }

    public int getRefreshN() {
        return refreshN;
    }

    public void setRefreshN(int refreshN) {
        this.refreshN = refreshN;
    }

    public int getMaxImmutableTables() {
        return maxImmutableTables;
    }

    public void setMaxImmutableTables(int maxImmutableTables) {
        this.maxImmutableTables = maxImmutableTables;
    }

    public int getRollSize() {
        return rollSize;
    }

    public void setRollSize(int rollSize) {
        this.rollSize = rollSize;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getMemtableMaxBytes() {
        return memtableMaxBytes;
    }

    public void setMemtableMaxBytes(int memtableMaxBytes) {
        this.memtableMaxBytes = memtableMaxBytes;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public double getBloomFalsePositive() {
        return bloomFalsePositive;
    }

    public void setBloomFalsePositive(double bloomFalsePositive) {
        this.bloomFalsePositive = bloomFalsePositive;
        configGenerateBloomValues();
    }

    public int getWalFsyncEveryN() {
        return walFsyncEveryN;
    }

    public void setWalFsyncEveryN(int walFsyncEveryN) {
        this.walFsyncEveryN = walFsyncEveryN;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getLog_level() {
        return log_level;
    }

    public void setLog_level(String log_level) {
        this.log_level = log_level;
    }
}
