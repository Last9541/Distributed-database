package internal.lsm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import internal.lsm.implementation.IndexEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TableHandle {
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
    @JsonIgnore
    private List<IndexEntry> sparseIndex;
    @JsonIgnore
    private int sparseIndexSize;
    @JsonIgnore
    private byte[] bloomFilter;

    public TableHandle()
    {

    }

    public TableHandle(long id, String fileName, byte[] minKey,
                       byte[] maxKey, long minSeqNo, long maxSeqNo, Instant createdAt,
                       long fileSize, int bloomFilterSizePerKey, int bloomHashingFunctionNumber,
                       List<IndexEntry> sparseIndex, int sparseIndexSize,byte[] bloomFilter) {
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
        this.sparseIndex=sparseIndex;
        this.sparseIndexSize=sparseIndexSize;
        this.bloomFilter=bloomFilter;
    }
    @JsonIgnore
    public void setSparseIndex(List<IndexEntry> sparseIndex) {
        this.sparseIndex = sparseIndex;
    }
    @JsonIgnore
    public int getSparseIndexSize() {
        return sparseIndexSize;
    }
    @JsonIgnore
    public void setSparseIndexSize(int sparseIndexSize) {
        this.sparseIndexSize = sparseIndexSize;
    }
    @JsonIgnore
    public byte[] getBloomFilter() {
        return bloomFilter;
    }
    @JsonIgnore
    public void setBloomFilter(byte[] bloomFilter) {
        this.bloomFilter = bloomFilter;
    }

    @JsonIgnore
    public List<IndexEntry> getSparseIndex() {
        return sparseIndex;
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
