package internal.lsm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import internal.lsm.implementation.IndexEntry;
import internal.lsm.implementation.SSTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class TableHandle implements Comparable<TableHandle> {
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
    private int entryCount;
    @JsonIgnore
    private int sparseIndexSize;
    @JsonIgnore
    private long sparseIndexPos;
    @JsonIgnore
    private long bloomFilterPos;
    @JsonIgnore
    private int bloomFilterSize;

    private int level=0;

    @JsonIgnore
    private AtomicBoolean compaction=new AtomicBoolean(false);

    @JsonIgnore
    private int refCount=0;
    @JsonIgnore
    private SSTable sst;


    public TableHandle(long id) {
        this.id = id;
    }

    public TableHandle()
    {

    }

    public TableHandle(long id, String fileName, byte[] minKey,
                       byte[] maxKey, long minSeqNo, long maxSeqNo, Instant createdAt,
                       long fileSize, int bloomFilterSizePerKey, int bloomHashingFunctionNumber,
                       long sparseIndexPos, int sparseIndexSize,long bloomFilterPos,int bloomFilterSize,int entryCount,SSTable sst) {
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
        this.sparseIndexPos=sparseIndexPos;
        this.sparseIndexSize=sparseIndexSize;
        this.bloomFilterPos=bloomFilterPos;
        this.bloomFilterSize=bloomFilterSize;
        this.entryCount=entryCount;
        this.sst=sst;
    }


    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public synchronized void increment()
    {
        if (refCount < 0)
            throw new IllegalStateException("Invalid refCount");
        refCount++;
    }
    public synchronized void decrement()
    {
        if (refCount <= 0)
            throw new IllegalStateException("Invalid refCount");
        refCount--;
        if(refCount==0) {
            try {
                sst.lru.remove(id);
                Files.deleteIfExists(sst.sstPath.resolve(Path.of(fileName)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @JsonIgnore
    public void setSst(SSTable sst) {
        this.sst = sst;
    }

    public AtomicBoolean getCompaction() {
        return compaction;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
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
    public long getSparseIndexPos() {
        return sparseIndexPos;
    }

    @JsonIgnore
    public void setSparseIndexPos(long sparseIndexPos) {
        this.sparseIndexPos = sparseIndexPos;
    }

    @JsonIgnore
    public long getBloomFilterPos() {
        return bloomFilterPos;
    }

    @JsonIgnore
    public void setBloomFilterPos(long bloomFilterPos) {
        this.bloomFilterPos = bloomFilterPos;
    }

    @JsonIgnore
    public int getBloomFilterSize() {
        return bloomFilterSize;
    }

    @JsonIgnore
    public void setBloomFilterSize(int bloomFilterSize) {
        this.bloomFilterSize = bloomFilterSize;
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


    @Override
    public int compareTo(TableHandle o) {
        //return o.fileName.compareTo(this.fileName);
        return Long.compare(o.id,(this.id));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TableHandle that = (TableHandle) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
