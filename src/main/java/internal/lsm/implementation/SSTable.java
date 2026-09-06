package internal.lsm.implementation;

import cmd.lsmkv.Main;
import internal.lsm.Config;
import internal.lsm.Manifest;
import internal.lsm.TableHandle;
import internal.lsm.errors.CorruptionDetected;
import internal.lsm.errors.IOFailure;
import internal.lsm.errors.InvalidArgument;

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

    private CRC32C crc32C=new CRC32C();

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
                    for (TableHandle x : manifest.getList()) {
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
                            fileChannel.position(sparsePos);
                            List<IndexEntry>list=new ArrayList<>();
                            for(int i=0;i<sparseSize;i++)
                            {
                                if(bloomPos - fileChannel.position()<Integer.BYTES)
                                    throw new CorruptionDetected("Nevalidan sparseIndex");
                                byteBuffer=ByteBuffer.allocate(Integer.BYTES);
                                if (!bufferRead(byteBuffer, fileChannel))
                                    throw new CorruptionDetected("Nevalidan sparseIndex");
                                int length=byteBuffer.getInt();
                                if(bloomPos - fileChannel.position()<length+Long.BYTES)
                                    throw new CorruptionDetected("Nevalidan sparseIndex");
                                byteBuffer=ByteBuffer.allocate(length+Long.BYTES);
                                if (!bufferRead(byteBuffer, fileChannel))
                                    throw new CorruptionDetected("Nevalidan sparseIndex");
                                byte[] key=new byte[length];
                                byteBuffer.get(key);
                                long offset=byteBuffer.getLong();
                                list.add(new IndexEntry(key,offset));
                            }
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
            manifest.add(new TableHandle(id,filename,memtable.getMemtable().firstKey().getBytes(),memtable.getMemtable().lastKey().getBytes(),minSeqNo,maxSeqNo,Files.readAttributes(filePath, BasicFileAttributes.class).creationTime().toInstant(),Files.size(filePath),config.getBloomFilterSizePerKey(),config.getBloomHashingFunctionNumber(),sparseIndex,sparseIndex.size(),bloomFilter));
            Main.mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFileTemp,manifest);
            Files.move(manifestFileTemp.toPath(),manifestFile.toPath(),StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public void ssTableRead()
    {

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
