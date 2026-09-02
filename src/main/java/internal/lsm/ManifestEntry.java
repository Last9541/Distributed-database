package internal.lsm;

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;

public class ManifestEntry {
    private long id;
    private String fileName;
    private byte[] minKey;
    private byte[] maxKey;
    private long minSeqNo;
    private long maxSeqNo;
    private Instant createdAt;
    private long fileSize;
    private int bloomFilterSizePerKey;
    private int bloomHashingFunctionNumber;

    public ManifestEntry(long id, String fileName, byte[] minKey, byte[] maxKey, long minSeqNo, long maxSeqNo, Instant createdAt, long fileSize, int bloomFilterSizePerKey, int bloomHashingFunctionNumber) {
        this.id = id;
        this.fileName = fileName;
        this.minKey = minKey;
        this.maxKey = maxKey;
        this.minSeqNo = minSeqNo;
        this.maxSeqNo = maxSeqNo;
        this.createdAt = createdAt;
        this.fileSize = fileSize;
        this.bloomFilterSizePerKey = bloomFilterSizePerKey;
        this.bloomHashingFunctionNumber = bloomHashingFunctionNumber;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getMinKey() {
        return minKey;
    }

    public void setMinKey(byte[] minKey) {
        this.minKey = minKey;
    }

    public byte[] getMaxKey() {
        return maxKey;
    }

    public void setMaxKey(byte[] maxKey) {
        this.maxKey = maxKey;
    }

    public long getMinSeqNo() {
        return minSeqNo;
    }

    public void setMinSeqNo(long minSeqNo) {
        this.minSeqNo = minSeqNo;
    }

    public long getMaxSeqNo() {
        return maxSeqNo;
    }

    public void setMaxSeqNo(long maxSeqNo) {
        this.maxSeqNo = maxSeqNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getBloomFilterSizePerKey() {
        return bloomFilterSizePerKey;
    }

    public void setBloomFilterSizePerKey(int bloomFilterSizePerKey) {
        this.bloomFilterSizePerKey = bloomFilterSizePerKey;
    }

    public int getBloomHashingFunctionNumber() {
        return bloomHashingFunctionNumber;
    }

    public void setBloomHashingFunctionNumber(int bloomHashingFunctionNumber) {
        this.bloomHashingFunctionNumber = bloomHashingFunctionNumber;
    }
}
