package internal.lsm.implementation;

import internal.lsm.Global;
import internal.lsm.TableHandle;
import internal.lsm.errors.CorruptionDetected;
import internal.lsm.errors.NotFound;
import internal.lsm.implementation.lru.LruSizeKey;
import internal.lsm.implementation.lru.LruWithSize;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class CompactionIterator implements Comparable<CompactionIterator> {
    private int cur=0;
    private List<IndexEntry> sparseIndex;
    private ByteBuffer block;
    private FileChannel fileChannel;
    private TableHandle tableHandle;
    private MemtableEntry memtableEntry;
    private SSTable ssTable;
    private long startTime;
    private Index writtenSize;

    public CompactionIterator(List<IndexEntry> sparseIndex, ByteBuffer block, FileChannel fileChannel,TableHandle tableHandle,SSTable ssTable,long startTime,Index writtenSize) {
        this.sparseIndex = sparseIndex;
        this.block = block;
        this.fileChannel = fileChannel;
        this.tableHandle=tableHandle;
        this.ssTable=ssTable;
        this.startTime=startTime;
        this.writtenSize=writtenSize;
    }


    public MemtableEntry getMemtableEntry() {
        return memtableEntry;
    }


    public boolean nextEntry() throws IOException, InterruptedException {
        if(block.position()==block.limit())
        {
            if(!next())
                return false;
        }
        int end1=block.limit();
        if(end1-block.position()<=Integer.BYTES)
            throw new CorruptionDetected("Nevalidan sst fajl");
        int keySize=block.getInt();
        if(keySize<=0 || end1-block.position()<keySize)
            throw new CorruptionDetected("Nevalidan sst fajl");
        byte[] key1=new byte[keySize];
        block.get(key1);
        if(end1-block.position()<=Integer.BYTES)
            throw new CorruptionDetected("Nevalidan sst fajl");
        int valueSize=block.getInt();
        if(valueSize<0 || end1-block.position()<valueSize)
            throw new CorruptionDetected("Nevalidan sst fajl");
        byte[] value=new byte[valueSize];
        if(valueSize!=0)
        {
            block.get(value);
        }
        if(end1-block.position()<Long.BYTES+Byte.BYTES)
            throw new CorruptionDetected("Nevalidan sst fajl");
        long seqNo=block.getLong();
        byte tombstone=block.get();
        if(tombstone!=0 && tombstone!=1)
            throw new CorruptionDetected("Nevalidan sst fajl (tombstone)");
        memtableEntry=new MemtableEntry(key1,value,seqNo,tombstone==1);
        return true;
    }

    public boolean next() throws IOException, InterruptedException {
        Index pos=new Index(0);
        byte[] fullCapacity = null;
        cur++;
        if(cur>=sparseIndex.size())
            return false;
        IndexEntry indexEntry = sparseIndex.get(cur);
        if (indexEntry.getIndex() < ssTable.headerSize || indexEntry.getIndex() >= tableHandle.getSparseIndexPos())
            throw new CorruptionDetected("Nevalidan sst fajl");
        //fileChannel.position(indexEntry.getIndex());
        pos.setVal(indexEntry.getIndex());
        long end = (cur+1< sparseIndex.size()) ? sparseIndex.get(cur+1).getIndex() : tableHandle.getSparseIndexPos();
        if (end <= indexEntry.getIndex() || end - indexEntry.getIndex() <= 2 * Integer.BYTES || end - indexEntry.getIndex() > Integer.MAX_VALUE)
            throw new CorruptionDetected("Nevalidan sst fajl");
        LruSizeKey lruSizeKey = new LruSizeKey(tableHandle.getId(), pos.getVal());
        ByteBuffer byteBuffer;
        if(ssTable.lruWithSize!=null)
            fullCapacity = ssTable.lruWithSize.get(lruSizeKey);
        if (fullCapacity == null) {
            byteBuffer = ByteBuffer.allocate((int) (end - indexEntry.getIndex()));
            if (!ssTable.bufferRead(byteBuffer, fileChannel, pos))
                throw new CorruptionDetected("Nevalidan sst fajl");
            writtenSize.setVal(writtenSize.getVal()+byteBuffer.limit());
            if(ssTable.config.getCompactionIoMbPerS()>0) {
                long cur = System.currentTimeMillis();
                long requiredTimeMs = writtenSize.getVal() * 1000L / (ssTable.config.getCompactionIoMbPerS() * 1024L * 1024L);
                long sleepTime = requiredTimeMs - (cur - startTime);
                if (sleepTime > 0)
                    Thread.sleep(sleepTime);
            }

            fullCapacity = new byte[byteBuffer.capacity()];
            byteBuffer.get(fullCapacity);
            //todo radim get ali ne i put
//                    if(lruWithSize!=null)
//                        lruWithSize.put(lruSizeKey, fullCapacity);
        }
        byteBuffer=ByteBuffer.wrap(fullCapacity);
        byte[] checksum=new byte[byteBuffer.capacity()-Integer.BYTES];
        byteBuffer.get(checksum);
        int chs;
        synchronized (ssTable.crc32C) {
            ssTable.crc32C.update(checksum, 0, checksum.length);
            chs = (int) ssTable.crc32C.getValue();
            ssTable.crc32C.reset();
        }
        if(chs!=byteBuffer.getInt())
            throw new CorruptionDetected("Nevalidan checksum");
        byteBuffer.position(checksum.length-Integer.BYTES);
        int position=byteBuffer.getInt();
        if(position<0||position>= checksum.length-Integer.BYTES*2)
            throw new CorruptionDetected("Nevalidan sst fajl");
        byte[] needed=new byte[position];
        ByteBuffer.wrap(checksum).get(needed);
        block=ByteBuffer.wrap(needed);
        return true;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public TableHandle getTableHandle() {
        return tableHandle;
    }

    public int getCur() {
        return cur;
    }

    public void setCur(int cur) {
        this.cur = cur;
    }

    public List<IndexEntry> getSparseIndex() {
        return sparseIndex;
    }

    public void setSparseIndex(List<IndexEntry> sparseIndex) {
        this.sparseIndex = sparseIndex;
    }

    public ByteBuffer getBlock() {
        return block;
    }

    public void setBlock(ByteBuffer block) {
        this.block = block;
    }

    @Override
    public int compareTo(CompactionIterator o) {
        return memtableEntry.compareTo(o.memtableEntry);
    }
}
