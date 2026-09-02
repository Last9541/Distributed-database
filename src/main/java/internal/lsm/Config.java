package internal.lsm;

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
    private int bloomFilterSizePerKey=12;
    private int bloomHashingFunctionNumber=3;
    private int refreshN=10;

    public Config()
    {

    }

    public Config(String dataDir, int memtableMaxBytes, int blockSize, double bloomFalsePositive, int walFsyncEveryN, String compression, String log_level,int maxImmutableTables,int bloomFilterSizePerKey,int bloomHashingFunctionNumber,int refreshN) {
        this.dataDir = dataDir;
        this.memtableMaxBytes = memtableMaxBytes;
        this.blockSize = blockSize;
        this.bloomFalsePositive = bloomFalsePositive;
        this.walFsyncEveryN = walFsyncEveryN;
        this.compression = compression;
        this.log_level = log_level;
        this.maxImmutableTables=maxImmutableTables;
        this.bloomFilterSizePerKey=bloomFilterSizePerKey;
        this.bloomHashingFunctionNumber=bloomHashingFunctionNumber;
        this.refreshN=refreshN;
    }

    public int getBloomHashingFunctionNumber() {
        return bloomHashingFunctionNumber;
    }

    public void setBloomHashingFunctionNumber(int bloomHashingFunctionNumber) {
        this.bloomHashingFunctionNumber = bloomHashingFunctionNumber;
    }

    public int getBloomFilterSizePerKey() {
        return bloomFilterSizePerKey;
    }

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
