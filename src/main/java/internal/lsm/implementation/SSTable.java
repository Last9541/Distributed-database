package internal.lsm.implementation;

import cmd.lsmkv.Main;
import internal.lsm.Config;
import internal.lsm.Global;
import internal.lsm.Manifest;
import internal.lsm.TableHandle;
import internal.lsm.errors.CorruptionDetected;
import internal.lsm.errors.IOFailure;
import internal.lsm.errors.InvalidArgument;
import internal.lsm.errors.NotFound;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

public class SSTable {

    protected Path sstPath;

    private File manifestFile=new File("data/manifest.json");

    private File manifestFileTemp=new File("data/manifest.json.tmp");

    private Manifest manifest=new Manifest();

    private long segmentId=0;

    private long tempSegmentId=0;

    private LsmImplementation lsmImplementation;

    protected Config config;

    private String magic="SSTB";

    private final CRC32C crc32C=new CRC32C();

    private int headerSize=8;

    private int footerSize=2*Integer.BYTES + 2*Long.BYTES;

    private int add(long a,long b,int m)
    {
        return (int)((a%m + b%m)%m);
    }

    private int mul(long a,long b,int m)
    {
        return (int)((a%m * b%m)%m);
    }

    public boolean bufferRead(ByteBuffer byteBuffer,FileChannel fileChannel) throws IOException {
        while (byteBuffer.hasRemaining()) {
            if (fileChannel.read(byteBuffer) == -1) {
                return false;
            }
        }
        byteBuffer.flip();
        return true;
    }



    public void headerCheck(FileChannel fileChannel, int headerSize, String magic) throws IOException {
        ByteBuffer byteBuffer=ByteBuffer.allocate(headerSize);
        if (!bufferRead(byteBuffer, fileChannel))
            throw new CorruptionDetected("Nevalidan header");
        byte[] header = new byte[headerSize];
        byteBuffer.get(header);
        String mag = new String(header, 0, magic.length(), StandardCharsets.US_ASCII);
        if (!magic.equals(mag))
            throw new CorruptionDetected("Nevalidan magic");
        if (header[magic.length()] != 1)
            throw new CorruptionDetected("Nevalidan version");
        for (int i = magic.length() + 1; i < headerSize; i++) {
            if (header[i] != 0)
                throw new CorruptionDetected("Greska pri ucitavanju fajla");
        }
    }

    public void init()
    {
        try {
            if (Files.notExists(manifestFile.toPath())) {
                Files.createFile(manifestFile.toPath());
            } else {
                if(Files.size(manifestFile.toPath())!=0) {
                    manifest = Main.mapper.readValue(manifestFile, Manifest.class);
                    for (TableHandle x : manifest.getSet()) {
                        //todo sta ako se osnovna putanja promenila
                        try (FileChannel fileChannel = FileChannel.open(sstPath.resolve(Path.of(x.getFileName())), StandardOpenOption.READ)) {
                            long size = fileChannel.size();
                            long footerStart = size - footerSize;
                            if (footerStart < headerSize)
                                throw new CorruptionDetected("Premali fajl");
                            headerCheck(fileChannel, headerSize, magic);
                            fileChannel.position(footerStart);
                            ByteBuffer byteBuffer = ByteBuffer.allocate(footerSize);
                            if (!bufferRead(byteBuffer, fileChannel))
                                throw new CorruptionDetected("Nevalidan footer");
                            long sparsePos = byteBuffer.getLong();
                            if (sparsePos >= footerStart || sparsePos < headerSize)
                                throw new CorruptionDetected("Nemoguca pocetna pozicija za sparseIndex");
                            int sparseSize = byteBuffer.getInt();
                            //ovo je bas gornji prag za sparseSize, mogu da podelim ovu velicinu sa velicinom iz configa pa da nadjem koliko je priblizno velik, samo sto se ta velicina moze menjati
                            if (sparseSize <= 0 || sparseSize > size - headerSize)
                                throw new CorruptionDetected("Nevalidan sparseSize");
                            long bloomPos = byteBuffer.getLong();
                            if (bloomPos >= footerStart || bloomPos < headerSize)
                                throw new CorruptionDetected("Nemoguca pocetna pozicija za bloomIndex");
                            if (bloomPos>=sparsePos)
                                throw new CorruptionDetected("Bloom i sparse delovi se seku");
                            int bloomSize = byteBuffer.getInt();
                            if (bloomSize <= 0 || bloomSize > size - headerSize || footerStart - bloomPos != bloomSize)
                                throw new CorruptionDetected("Nevalidan bloomSize");
                            x.setBloomFilterPos(bloomPos);
                            x.setBloomFilterSize(bloomSize);
                            x.setSparseIndexPos(sparsePos);
                            x.setSparseIndexSize(sparseSize);
                        }
                    }
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        //deleteSstTmp();
        loadSegmentsId();
    }

//    private void deleteSstTmp()
//    {
//        try(DirectoryStream<Path> filesStream = Files.newDirectoryStream(sstPath)){
//            for(Path file:filesStream) {
//                String name = file.getFileName().toString();
//                if (Files.isRegularFile(file) && name.contains(".") && name.substring(name.indexOf('.')).equals(".sst.tmp")) {
//                    Files.deleteIfExists(file);
//                }
//            }
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

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
        for(int i=1;i<=config.getBloomHashingFunctionNumber();i++)
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
        long minSeqNo=memtable.getMemtable().firstEntry().getValue().getSeqNo();
        long maxSeqNo=minSeqNo;
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
                if(memtableEntry.getSeqNo()>maxSeqNo)
                    maxSeqNo=memtableEntry.getSeqNo();
                if(memtableEntry.getSeqNo()<minSeqNo)
                    minSeqNo=memtableEntry.getSeqNo();
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

            ByteBuffer posBuf=ByteBuffer.allocate(footerSize);
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
            long id=segmentIncrementAndGet();
            String filename=String.format("%06d.sst",id);
            Path filePath=sstPath.resolve(Path.of(filename));
            Files.move(sstTmp,filePath, StandardCopyOption.ATOMIC_MOVE);
            manifest.add(new TableHandle(id,filename,memtable.getMemtable().firstKey().getBytes(),memtable.getMemtable().lastKey().getBytes(),minSeqNo,maxSeqNo,Files.readAttributes(filePath, BasicFileAttributes.class).creationTime().toInstant(),Files.size(filePath),config.getBloomFilterSizePerKey(),config.getBloomHashingFunctionNumber(),pos,sparseIndex.size(),pos2,bloomFilter.length));
            Main.mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFileTemp,manifest);
            Files.move(manifestFileTemp.toPath(),manifestFile.toPath(),StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }




    public int binarySearch(List<IndexEntry> list,byte[] key)
    {
        int l=0;
        int r=list.size()-1;
        while(l<=r)
        {
            int mid=l+(r-l)/2;
            int comp=Global.compareTo(list.get(mid).getKey(),key);
            if(comp==0)
                return mid;
            if(comp<0)
            {
                l=mid+1;
                continue;

            }
            r=mid-1;
        }
        return r;
    }



    public List<IndexEntry> getEntries(FileChannel fileChannel,int size,long start,long end,long thisStart,String txt) throws IOException {
        if(size<=0)
            throw new CorruptionDetected("Nevalidan "+txt);
        List<IndexEntry> list=new ArrayList<>();
        byte[] oldKey=null;
        long oldOffset=0;
        for (int i = 0; i < size; i++) {
            if (end - fileChannel.position() < Integer.BYTES)
                throw new CorruptionDetected("Nevalidan "+txt);
            ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);
            if (!bufferRead(byteBuffer, fileChannel))
                throw new CorruptionDetected("Nevalidan "+txt);
            int length = byteBuffer.getInt();
            if (length<=0 || end - fileChannel.position() < length)
                throw new CorruptionDetected("Nevalidan "+txt);
            byteBuffer = ByteBuffer.allocate(length);
            if (!bufferRead(byteBuffer, fileChannel))
                throw new CorruptionDetected("Nevalidan "+txt);
            byte[] key1 = new byte[length];
            byteBuffer.get(key1);
            if(oldKey!=null && Global.compareTo(key1,oldKey)<=0)
            {
                throw new CorruptionDetected("Nevalidan "+txt);
            }
            if(end - fileChannel.position() < Long.BYTES)
                throw new CorruptionDetected("Nevalidan "+txt);
            byteBuffer=ByteBuffer.allocate(Long.BYTES);
            if (!bufferRead(byteBuffer, fileChannel))
                throw new CorruptionDetected("Nevalidan "+txt);
            long offset = byteBuffer.getLong();
            if(oldOffset>=offset)
                throw new CorruptionDetected("Nevalidan "+txt);
            if (offset >= thisStart || offset < start)
                throw new CorruptionDetected("Nevalidan "+txt);
            list.add(new IndexEntry(key1, offset));
            oldKey=key1;
            oldOffset=offset;
        }
        return list;
    }


    public byte[] ssTableRead(byte[] key)
    {
        for (TableHandle x : manifest.getSet()) {
            try (FileChannel fileChannel = FileChannel.open(sstPath.resolve(Path.of(x.getFileName())), StandardOpenOption.READ)) {

                fileChannel.position(x.getSparseIndexPos());
                List<IndexEntry> sparseIndex = getEntries(fileChannel,x.getSparseIndexSize(),headerSize,x.getBloomFilterPos(),x.getSparseIndexPos(),"sparseIndex");
                //todo ovo je nepotrebno ali za svaki slucaj
                if (fileChannel.position() != x.getBloomFilterPos())
                    throw new CorruptionDetected("Nevalidan sparseIndex");
                byte[] bloom = new byte[x.getBloomFilterSize()];
                ByteBuffer byteBuffer = ByteBuffer.allocate(x.getBloomFilterSize());
                if (!bufferRead(byteBuffer, fileChannel))
                    throw new CorruptionDetected("Nevalidan bloom");
                byteBuffer.get(bloom);
                if(!readBloom(bloom,key,bloom.length*8) || Global.compareTo(key, x.getMinKey())<0 || Global.compareTo(key,x.getMaxKey())>0)
                    continue;
                int index=binarySearch(sparseIndex,key);
                if(index<0)
                    continue;
                IndexEntry indexEntry=sparseIndex.get(index);
                if(indexEntry.getIndex()<headerSize||indexEntry.getIndex()>=x.getSparseIndexPos())
                    throw new CorruptionDetected("Nevalidan sst fajl");
                fileChannel.position(indexEntry.getIndex());
                long end=(index+1<sparseIndex.size())?sparseIndex.get(index+1).getIndex(): x.getSparseIndexPos();
                if(end<=indexEntry.getIndex() || end-indexEntry.getIndex()<=Integer.BYTES+Long.BYTES || end-indexEntry.getIndex()>Integer.MAX_VALUE)
                        throw new CorruptionDetected("Nevalidan sst fajl");
                byteBuffer=ByteBuffer.allocate((int)(end-indexEntry.getIndex()));
                if (!bufferRead(byteBuffer, fileChannel))
                    throw new CorruptionDetected("Nevalidan sst fajl");
                byte[] checksum=new byte[byteBuffer.capacity()-Integer.BYTES];
                byteBuffer.get(checksum);
                int chs;
                synchronized (crc32C) {
                    crc32C.update(checksum, 0, checksum.length);
                    chs = (int) crc32C.getValue();
                    crc32C.reset();
                }
                if(chs!=byteBuffer.getInt())
                    throw new CorruptionDetected("Nevalidan checksum");
                byteBuffer=ByteBuffer.wrap(checksum,checksum.length-Long.BYTES,Long.BYTES);
                long position=byteBuffer.getLong();
                if(position<indexEntry.getIndex()||position>=fileChannel.position())
                    throw new CorruptionDetected("Nevalidan sst fajl");

                fileChannel.position(position);
                byteBuffer=ByteBuffer.allocate(Integer.BYTES);
                if (!bufferRead(byteBuffer, fileChannel))
                    throw new CorruptionDetected("Nevalidan sst fajl");
                int restartPointsSize=byteBuffer.getInt();
                List<IndexEntry> checkPoints=getEntries(fileChannel,restartPointsSize,indexEntry.getIndex(),end,position,"checkPoints");
                if (fileChannel.position() != end-Integer.BYTES-Long.BYTES)
                    throw new CorruptionDetected("Nevalidan checkPoints");
                int index1=binarySearch(checkPoints,key);
                if(index1<0)
                    continue;
                IndexEntry found=checkPoints.get(index1);
                if(Global.compareTo(key, found.getKey())<0)
                    continue;
                fileChannel.position(found.getIndex());
                long end1=(index1+1<checkPoints.size())?checkPoints.get(index1+1).getIndex(): position;
                while(fileChannel.position()<end1)
                {
                    if(end1-fileChannel.position()<=Integer.BYTES)
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    byteBuffer=ByteBuffer.allocate(Integer.BYTES);
                    if (!bufferRead(byteBuffer, fileChannel))
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    int keySize=byteBuffer.getInt();
                    if(keySize<=0 || end1-fileChannel.position()<keySize)
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    byteBuffer=ByteBuffer.allocate(keySize);
                    if (!bufferRead(byteBuffer, fileChannel))
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    byte[] key1=new byte[keySize];
                    byteBuffer.get(key1);
                    if(end1-fileChannel.position()<=Integer.BYTES)
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    byteBuffer=ByteBuffer.allocate(Integer.BYTES);
                    if (!bufferRead(byteBuffer, fileChannel))
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    int valueSize=byteBuffer.getInt();
                    if(valueSize<0 || end1-fileChannel.position()<valueSize)
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    byte[] value=new byte[valueSize];
                    if(valueSize!=0)
                    {
                        byteBuffer=ByteBuffer.allocate(valueSize);
                        if (!bufferRead(byteBuffer, fileChannel))
                            throw new CorruptionDetected("Nevalidan sst fajl");
                        byteBuffer.get(value);
                    }
                    if(end1-fileChannel.position()<Long.BYTES+Byte.BYTES)
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    byteBuffer=ByteBuffer.allocate(Long.BYTES+Byte.BYTES);
                    if (!bufferRead(byteBuffer, fileChannel))
                        throw new CorruptionDetected("Nevalidan sst fajl");
                    long seqNo=byteBuffer.getLong();
                    byte tombstone=byteBuffer.get();
                    if(tombstone!=0 && tombstone!=1)
                        throw new CorruptionDetected("Nevalidan sst fajl (tombstone)");
                    if(Global.compareTo(key,key1)==0)
                    {
                        if(tombstone==1)
                            throw new NotFound();
                        return value;
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new NotFound();

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
        int checksum;
        synchronized (crc32C) {
            crc32C.update(arr, 0, arr.length);
            checksum = (int) crc32C.getValue();
            crc32C.reset();
        }
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
                                Files.deleteIfExists(file);
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
                        if (lsmImplementation.blockWrite && lsmImplementation.getMemtables().size() == config.getMaxImmutableTables()) {
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
