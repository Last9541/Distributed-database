package internal.lsm.implementation;

import internal.lsm.Config;
import internal.lsm.errors.IOFailure;
import internal.lsm.errors.InvalidArgument;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

public class SSTable {

    protected Path sstPath;

    private long segmentId=0;

    private long tempSegmentId=0;

    private LsmImplementation lsmImplementation;

    protected Config config;

    private String magic="SSTB";

    private CRC32C crc32C=new CRC32C();

    private int headerSize=8;

    private int add(long a,long b,int m)
    {
        return (int)((a%m + b%m)%m);
    }

    private int mul(long a,long b,int m)
    {
        return (int)((a%m * b%m)%m);
    }

    private int hashFun1(byte[] key,int m)
    {
        int hash=0;
        for (byte b : key) {
            hash = add(mul(hash, 257, m), b + 128, m);
        }
        return hash;
    }

    private int hashFun2(byte[] key,int m)
    {
        int hash=0;
        for (byte b : key) {
            hash = add(mul(hash, 263, m), b + 128, m);
        }
        return hash;
    }

    private void writeBloom(byte[] bloom,byte[] key,int m)
    {
        int fun1=hashFun1(key,m);
        int fun2=Math.max(1,hashFun2(key,m));
        for(int i=1;i<=3;i++)
        {
            int index=add(fun1,mul(i,fun2,m),m);
            bloom[index/8]|= (byte) (1<<(index%8));
        }
    }

    private boolean readBloom(byte[] bloom,byte[] key,int m)
    {
        int fun1=hashFun1(key,m);
        int fun2=Math.max(1,hashFun2(key,m));
        for(int i=1;i<=3;i++)
        {
            int index=add(fun1,mul(i,fun2,m),m);
            if((bloom[index/8]&(byte) (1<<(index%8)))==0)
                return false;
        }
        return true;
    }


    public SSTable()
    {
        if(!(this instanceof LsmImplementation))
            throw new RuntimeException("Ako ikad dodje ovde onda nzm sta da kazem");
        lsmImplementation=(LsmImplementation) this;
    }


    public void ssTableWrite(Memtable memtable,long segmentId)
    {
        List<IndexEntry>sparseIndex=new ArrayList<>();
        //todo ovo ako se ne secam nece raditi ali da vidim da li barem pomaze u compile time
        assert lsmImplementation!=null;
        if(!memtable.isImmutable())
            throw new InvalidArgument("Ovo ne sme da se ikada desi ako se desilo proveri STO STO STO");
        //todo proveriti da li mi trebaju sve ove permisije
        Path sstTmp=sstPath.resolve(Path.of(String.format("%06d.sst.tmp", tempSegmentIncrementAndGet())));
        try(FileChannel fileChannel = FileChannel.open(sstTmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)){
            //todo jednog dana ovde ce doci magic/version al me jako mrzi sada da se bakcem time, takodje i dalje fali provera toga u wal-u nemoj zaboraviti
            ByteBuffer record = ByteBuffer.allocate(headerSize);
            record.put(magic.getBytes(StandardCharsets.US_ASCII));
            record.put((byte)1);
            record.put(new byte[3]);
            record.flip();
            while (record.hasRemaining())
            {
                fileChannel.write(record);
            }

            int no=config.getBloomFilterSizePerKey()*memtable.getMemtable().size()/8;
            if(config.getBloomFilterSizePerKey()*memtable.getMemtable().size()%8!=0)
                no++;
            byte[] bloomFilter=new byte[no];
            //todo posto je ovo int ima smisla da i memtable size i memtable entry size bude int
            ByteBuffer block = ByteBuffer.allocate(0);
            //todo ne treba ti ovo sa blockom

            List<IndexEntry> restartPoints=new ArrayList<>();
            int restartSize=0;
            int count=0;

            for (MemtableEntry memtableEntry : memtable.getMemtable().values()) {
                int additionalSize=0;

                if(count%config.getRefreshN()==0) {
                    additionalSize = Integer.BYTES + Long.BYTES + memtableEntry.getKey().length;
                }
                writeBloom(bloomFilter,memtableEntry.getKey(),bloomFilter.length*8);
                while(block.remaining()<memtableEntry.getSize()+restartSize+additionalSize+Integer.BYTES*2+Long.BYTES) {

                    if(block.remaining()==config.getBlockSize())
                        throw new IOFailure("oVoneSmeDaSeDesI");

                    if(block.remaining()!=0)
                    {
                    blockWrite(fileChannel, block, restartPoints);
                    }
                    restartPoints.clear();
                    restartSize=0;
                    count=0;
                    additionalSize = Integer.BYTES + Long.BYTES + memtableEntry.getKey().length;
                    block=ByteBuffer.allocate(config.getBlockSize());
                    sparseIndex.add(new IndexEntry(memtableEntry.getKey(),fileChannel.position()));
                }

                    ByteBuffer buffer=ByteBuffer.allocate(Integer.BYTES*2+Long.BYTES+Byte.BYTES+memtableEntry.getKey().length+memtableEntry.getValue().length);
                    //todo dodaj prefix (delta) compression
                    buffer.putInt(memtableEntry.getKey().length);
                    buffer.put(memtableEntry.getKey());
                    buffer.putInt(memtableEntry.getValue().length);
                    buffer.put(memtableEntry.getValue());
                    buffer.putLong(memtableEntry.getSeqNo());
                    buffer.put(memtableEntry.isTombstone()?(byte) 1:(byte) 0);

                    if(count%config.getRefreshN()==0)
                    {
                        IndexEntry indexEntry=new IndexEntry(memtableEntry.getKey(),fileChannel.position());
                        restartPoints.add(indexEntry);
                        //todo moze i +=additionalSize
                        restartSize+=indexEntry.getSize();
                    }
                    byte[] arr=new byte[buffer.position()];
                    buffer.flip();
                    buffer.asReadOnlyBuffer().get(arr);
                    block.put(arr);
                    while(buffer.hasRemaining())
                    {
                        fileChannel.write(buffer);
                    }
                    count++;
            }
            if(block.remaining()!=0)
                blockWrite(fileChannel, block, restartPoints);
            long pos=fileChannel.position();
            for(IndexEntry sparseIndexEntry:sparseIndex)
            {
                ByteBuffer byteBuffer=ByteBuffer.allocate(sparseIndexEntry.getKey().length + Long.BYTES + Integer.BYTES);
                byteBuffer.putInt(sparseIndexEntry.getKey().length);
                byteBuffer.put(sparseIndexEntry.getKey());
                byteBuffer.putLong(sparseIndexEntry.getIndex());
                byteBuffer.flip();
                while (byteBuffer.hasRemaining())
                {
                    fileChannel.write(byteBuffer);
                }
            }
            long pos2=fileChannel.position();
            ByteBuffer bloomBuffer=ByteBuffer.allocate(bloomFilter.length);
            bloomBuffer.put(bloomFilter);
            bloomBuffer.flip();
            while(bloomBuffer.hasRemaining())
            {
                fileChannel.write(bloomBuffer);
            }

            ByteBuffer posBuf=ByteBuffer.allocate(Long.BYTES*2 + 2*Integer.BYTES);
            posBuf.putLong(pos);
            posBuf.putInt(sparseIndex.size());
            posBuf.putLong(pos2);
            posBuf.putInt(bloomFilter.length);
            posBuf.flip();
            while(posBuf.hasRemaining())
            {
                fileChannel.write(posBuf);
            }

            fileChannel.force(true);
            Files.move(sstTmp,sstPath.resolve(Path.of(String.format("%06d.sst", segmentIncrementAndGet()))), StandardCopyOption.ATOMIC_MOVE);
//            lsmImplementation.walDelete(segmentId); nemoj da ovo otkomentarises hocu samo da ostane i ovde u jednom commitu
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private synchronized long tempSegmentIncrementAndGet()
    {
        long old=tempSegmentId;
        tempSegmentId++;
        return old;
    }
    private synchronized long segmentIncrementAndGet()
    {
        long old=segmentId;
        segmentId++;
        return old;
    }

    private void blockWrite(FileChannel fileChannel, ByteBuffer block, List<IndexEntry> restartPoints) throws IOException {

        ByteBuffer checksumAndRestartPoints=ByteBuffer.allocate(block.remaining());
        //todo mora da se doda i duzina
        checksumAndRestartPoints.putInt(restartPoints.size());
        block.putInt(restartPoints.size());
        for(IndexEntry indexEntry:restartPoints)
        {
            checksumAndRestartPoints.putInt(indexEntry.getKey().length);
            block.putInt(indexEntry.getKey().length);
            checksumAndRestartPoints.put(indexEntry.getKey());
            block.put(indexEntry.getKey());
            checksumAndRestartPoints.putLong(indexEntry.getIndex());
            block.putLong(indexEntry.getIndex());
        }
        checksumAndRestartPoints.putLong(fileChannel.position());
        block.putLong(fileChannel.position());
        byte[] arr=new byte[block.position()];
        ByteBuffer read=block.asReadOnlyBuffer();
        read.flip();
        read.get(arr);
        crc32C.update(arr,0,arr.length);
        int checksum=(int)crc32C.getValue();
        crc32C.reset();
        checksumAndRestartPoints.putInt(checksum);
        checksumAndRestartPoints.flip();
        while(checksumAndRestartPoints.hasRemaining())
        {
            fileChannel.write(checksumAndRestartPoints);
        }
    }

    public void loadSegmentsId()
    {
        try(DirectoryStream<Path> filesStream = Files.newDirectoryStream(sstPath)) {
            for(Path file:filesStream)
            {
                try {
                    String name = file.getFileName().toString();
                    if (Files.isRegularFile(file) && name.contains(".")) {
                        if (name.substring(name.indexOf('.')).equals(".sst")) {
                            segmentId = Math.max(segmentId, Long.parseLong(name.substring(0, name.indexOf('.'))));

                        } else {
                            if (name.substring(name.indexOf('.')).equals(".sst.tmp")) {
                                tempSegmentId = Math.max(tempSegmentId, Long.parseLong(name.substring(0, name.indexOf('.'))));
                            }
                        }
                    }
                }
                catch (NumberFormatException ignored)
                {

                }

            }
            segmentId++;
            tempSegmentId++;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void ssTableWrite(List<Memtable>copy,long segmentId)
    {


        for(Memtable x:copy){
            //todo proveri ovo on a local variable
            synchronized (x) {
                if(!x.isRead()) {
                    ssTableWrite(x,segmentId);
                    lsmImplementation.memtableListLock.writeLock().lock();
                    try {
                        lsmImplementation.getMemtables().remove(x);
                        if (lsmImplementation.getMemtables().size() == config.getMaxImmutableTables()) {
                            lsmImplementation.conditionMemtableLock.signalAll();
                        }
                    }
                    finally {
                        lsmImplementation.memtableListLock.writeLock().unlock();
                    }
                    x.setRead(true);
                }
            }
        }
    }

}
