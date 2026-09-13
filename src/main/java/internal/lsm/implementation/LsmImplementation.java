package internal.lsm.implementation;

import cmd.lsmkv.Main;
import internal.lsm.*;
import internal.lsm.errors.*;
import internal.lsm.implementation.lru.HitMiss;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32C;

//todo zameniti exceptione svuda za errors exceptione
public class LsmImplementation extends SSTable implements Lsm {



    private CRC32C crc32C=new CRC32C();



    private long sequence=1;
    private long segmentId=1;
    private Object walLock=new Object();

    private FileChannel channel;

    private long size;
    private final int headerSize=8;
    private int n=1;
    private boolean closed;
    private int truncated;

    private Future<?> future;
    //todo prebaci u config
    private final long keySize=64000;
    private final long valueSize=16777216;
    private final long lenSize=keySize+valueSize+Byte.BYTES+Long.BYTES+Integer.BYTES*2;
    private String magic="WAL1";
    private List<Future<?>> futures=new ArrayList<>();
    private volatile Exception ssException;

    public LsmImplementation(Config config) throws IOException {
        //this.config = config;
        init(config);
    }

//    @Override
//    public void loadConfig(Config config) {
//        this.config=config;
//    }

    public LsmImplementation()  {
        //init();
    }





//    public void walDelete(long segmentId)
//    {
//        for(int i=0;i<segmentId;i++)
//        {
//            try {
//                Files.deleteIfExists(walPath.resolve(Path.of(String.format("%06d.wal", i))));
//            }
//            catch (Exception e)
//            {
//                throw new IOFailure("IO Greska");
//            }
//        }
//    }


    private void trunc(FileChannel fileChannel,long start,Path file) throws IOException {
        fileChannel.truncate(start);
        truncated++;
        System.out.println("truncated_segment="+file.getFileName().toString() + " truncated_to="+start);
    }



    public void walVerify()
    {
        long sequence=0;
        long segment=0;
        long truncated=0;
        long records=0;
        try(DirectoryStream<Path> filesStream = Files.newDirectoryStream(walPath)) {
            List<Path> files=new ArrayList<>();
            for(Path file:filesStream)
            {
                files.add(file);
            }
            files.sort(Comparator.naturalOrder());

            for(Path file:files) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && name.contains(".") && name.substring(name.indexOf('.')).equals(".wal")) {

                        try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                            headerCheck(fileChannel,headerSize,magic);
                            long size = fileChannel.size();
                            while (fileChannel.position() < size) {
                                long start = fileChannel.position();
                                ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);
                                if (!bufferRead(byteBuffer, fileChannel)) {
                                    trunc(fileChannel, start, file);
                                    truncated++;
                                    break;
                                }
                                int len = byteBuffer.getInt();
                                //mora biti >= konstantama + 1 zato sto kljuc ne sme biti prazan, a ne sme biti veci od size - velicina checksuma(4B TJ integer size)
                                if (len < Byte.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES + Byte.BYTES || fileChannel.position() + len + Integer.BYTES > size) {
                                    trunc(fileChannel, start, file);
                                    truncated++;
                                    break;
                                }
                                if (len > lenSize) {
                                    trunc(fileChannel, start, file);
                                    truncated++;
                                    break;
                                }
                                byteBuffer = ByteBuffer.allocate(len);
                                if (!bufferRead(byteBuffer, fileChannel)) {
                                    trunc(fileChannel, start, file);
                                    truncated++;
                                    break;
                                }
                                byte[] content = new byte[len];
                                byteBuffer.get(content);
                                crc32C.update(content, 0, len);
                                int newCheckSum = (int) crc32C.getValue();
                                crc32C.reset();
                                byteBuffer = ByteBuffer.allocate(Integer.BYTES);
                                if (!bufferRead(byteBuffer, fileChannel)) {
                                    trunc(fileChannel, start, file);
                                    truncated++;
                                    break;
                                }

                                int checksum = byteBuffer.getInt();
                                if (newCheckSum != checksum) {
                                    trunc(fileChannel, start, file);
                                    truncated++;
                                    break;
                                }
                                try {
                                    ByteBuffer later = ByteBuffer.wrap(content);
                                    RecordType recordType = RecordType.getByValue(later.get());
                                    long newSequence = later.getLong();
                                    int keyBytesLength = later.getInt();
                                    int valueBytesLength = later.getInt();
                                    //todo proveriti da li ovako da castujem long ili samo jedan, proveri takodje ovo za DELETE
                                    if (keyBytesLength <= 0 || valueBytesLength < 0 || (long) keyBytesLength + (long) valueBytesLength != later.remaining() || recordType == RecordType.DELETE && valueBytesLength > 0) {
                                        throw new Exception();
                                    }
                                    //TODO napraviti novi exception za ovo
                                    if (keyBytesLength > keySize)
                                        throw new Exception();
                                    byte[] keyArray = new byte[keyBytesLength];
                                    byte[] valueArray = null;
                                    later.get(keyArray);
//                                    String key = new String(keyArray, StandardCharsets.UTF_8);
//                                    String value = null;
                                    if (valueBytesLength > later.remaining())
                                        throw new Exception();
                                    if (valueBytesLength > valueSize)
                                        throw new Exception();
                                    if (valueBytesLength > 0) {
                                        valueArray = new byte[valueBytesLength];
                                        later.get(valueArray);
//                                        value = new String(valueArray, StandardCharsets.UTF_8);
                                    }
                                    records++;
                                    sequence = Math.max(sequence, newSequence);
                                }
//                                catch (IOFailure ioFailure) {
//                                    throw ioFailure;
//                                }
                                catch (Exception e) {
                                    trunc(fileChannel, start, file);
                                    truncated++;
                                    break;
                                }

                            }

                        }
                        segment++;
//                        catch (IOFailure e) {
//                            throw new RuntimeException(e);
//                        }

                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.printf(
                "recovery: segments=%d records=%d truncated=%d last_seqno=%d%n",
                segment, records, truncated, sequence
        );
    }




    public void init(Config config) throws IOException {
        if(this.config!=null)
            throw new InvalidArgument("Vec je uradjen init, moras ponovo");
        if(config.getBlockSize()<MemtableEntry.documentedSize+keySize+valueSize || config.getBlockSize()<config.getMemtableMaxBytes())
            throw new InvalidArgument("Premali blockSize u config");
        if(config.getL0CompactionTrigger()<=0)
            throw new InvalidArgument("l0compactiontrigger mora biti veci od 0");
        closed=false;
        this.config=config;
        if(headerSize<=magic.length()+1)
            throw new InvalidArgument();
        //memtables.add(new Memtable());
        active=new Memtable();
        dataPath=Path.of(config.getDataDir());
        Files.createDirectories(dataPath);
        walPath = dataPath.resolve(Path.of("wal"));
        Files.createDirectories(walPath);
        sstPath=dataPath.resolve(Path.of("sst"));
        Files.createDirectories(sstPath);
        super.init();

        long sequence=0;
        try(DirectoryStream<Path> filesStream = Files.newDirectoryStream(walPath)) {
            List<Path> files=new ArrayList<>();
            for(Path file:filesStream)
            {
                files.add(file);
            }
            files.sort(Comparator.naturalOrder());

            for(Path file:files) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && name.contains(".") && name.substring(name.indexOf('.')).equals(".wal")) {

                    try {
                        segmentId = Math.max(segmentId, Long.parseLong(name.substring(0, name.indexOf('.'))));
                        try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                            headerCheck(fileChannel,headerSize,magic);
                            long size = fileChannel.size();
                            while (fileChannel.position() < size) {
                                long start = fileChannel.position();
                                ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);
                                if (!bufferRead(byteBuffer, fileChannel)) {
                                    trunc(fileChannel, start, file);
                                    break;
                                }
                                int len = byteBuffer.getInt();
                                //mora biti >= konstantama + 1 zato sto kljuc ne sme biti prazan, a ne sme biti veci od size - velicina checksuma(4B TJ integer size)
                                if (len < Byte.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES + Byte.BYTES || fileChannel.position() + len + Integer.BYTES > size) {
                                    trunc(fileChannel, start, file);
                                    break;
                                }
                                if (len > lenSize) {
                                    trunc(fileChannel, start, file);
                                    break;
                                }
                                byteBuffer = ByteBuffer.allocate(len);
                                if (!bufferRead(byteBuffer, fileChannel)) {
                                    trunc(fileChannel, start, file);
                                    break;
                                }
                                byte[] content = new byte[len];
                                byteBuffer.get(content);
                                crc32C.update(content, 0, len);
                                int newCheckSum = (int) crc32C.getValue();
                                crc32C.reset();
                                byteBuffer = ByteBuffer.allocate(Integer.BYTES);
                                if (!bufferRead(byteBuffer, fileChannel)) {
                                    trunc(fileChannel, start, file);
                                    break;
                                }

                                int checksum = byteBuffer.getInt();
                                if (newCheckSum != checksum) {
                                    trunc(fileChannel, start, file);
                                    break;
                                }
                                try {
                                    ByteBuffer later = ByteBuffer.wrap(content);
                                    RecordType recordType = RecordType.getByValue(later.get());
                                    long newSequence = later.getLong();
                                    int keyBytesLength = later.getInt();
                                    int valueBytesLength = later.getInt();
                                    //todo proveriti da li ovako da castujem long ili samo jedan, proveri takodje ovo za DELETE
                                    if (keyBytesLength <= 0 || valueBytesLength < 0 || (long) keyBytesLength + (long) valueBytesLength != later.remaining() || recordType == RecordType.DELETE && valueBytesLength > 0) {
                                        throw new Exception();
                                    }
                                    //TODO napraviti novi exception za ovo
                                    if (keyBytesLength > keySize)
                                        throw new Exception();
                                    byte[] keyArray = new byte[keyBytesLength];
                                    byte[] valueArray = null;
                                    later.get(keyArray);
//                                    String key = new String(keyArray, StandardCharsets.UTF_8);
//                                    String value = null;
                                    if (valueBytesLength > later.remaining())
                                        throw new Exception();
                                    if (valueBytesLength > valueSize)
                                        throw new Exception();
                                    if (valueBytesLength > 0) {
                                        valueArray = new byte[valueBytesLength];
                                        later.get(valueArray);
//                                        value = new String(valueArray, StandardCharsets.UTF_8);
                                    }

                                    //todo dodaj racunanje velicine i rolling za size sad ti se spava bolje ne diraj
                                    //todo ovde ipak neces praviti nove instance nego ces flushovati kada se napuni pa prazniti stablo
                                    memtableWrite(new ByteArray(keyArray), new MemtableEntry(keyArray, valueArray, newSequence, recordType == RecordType.DELETE), true);
                                    sequence = Math.max(sequence, newSequence);
                                }
//                                catch (IOFailure ioFailure) {
//                                    throw ioFailure;
//                                }
                                catch (Exception e) {
                                    trunc(fileChannel, start, file);
                                    break;
                                }

                            }

                        }
                        //todo posle obrisati ovaj catch i sve u Mainu da se regulise
                        //TODO zakomentarisao sam, proveriti da li mi treba uopste
//                        catch (IOFailure e) {
//                            throw new RuntimeException(e);
//                        }
                    } catch (NumberFormatException ignored) {

                    }
                }
            }
            this.sequence=sequence+1;
            channelInit();
            for(var x:futures)
            {
                x.get();
            }
            version=new Version(active,new ArrayList<>(immutables.reversed()),manifest.getSet(),manifest.getSetSize(),manifest.getSetLevel(),manifest.getEpoch(),0,sequence);
            futures.clear();
            future=Main.ssTableWriter.submit(()-> {
                try {
                    ssTableWrite();
                } catch (Exception e) {
                    ssException=e;
                    memtableListLock.writeLock().lock();
                    try {
                        conditionMemtableLock.signalAll();
                    }
                    finally {
                        memtableListLock.writeLock().unlock();
                    }
                }
            });
            Main.compactionLoop.submit(()-> {
                try {
                    Main.compaction.loop();
                } catch (Exception e) {
                    ssException=e;
                }
            });
            Main.compactionWorker.scheduleAtFixedRate(()->{
                try {
                    Main.compaction.picker();
                } catch (InterruptedException e) {
                    ssException=e;
                }
            },0,5, TimeUnit.SECONDS);
        } catch (IOException e) {
            throw new IOFailure();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public String stats() {
        if(closed)
            throw new StoreClosed();
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(ssException!=null)
            throw new RuntimeException(ssException);

        Version current=version;
        Memtable memtable = current.getActive();
        int activeEntries;
        long activeBytes;
        synchronized (memtable) {
            activeEntries = memtable.getMemtable().size();
            activeBytes = memtable.getSize();
        }
        int immutablesCount = current.getImmutables().size();
        long sstTotalBytes=0;
        for(TableHandle x:current.getTableHandles())
        {
            sstTotalBytes+=x.getFileSize();
        }
        long immutablesBytesTotal = current.getImmutableSize();
        long lastSeqNo = sequence-1;
        HitMiss hitMiss=(lruWithSize==null)?new HitMiss(0,0):lruWithSize.getHitMiss();
        return String.format(
                "epoch=%d last_seqno=%d%n" +
                        "active_entries=%d active_bytes=%d%n" +
                        "immutables=%d immutables_bytes=%d%n" +
                        "sst_live=%d sst_total_bytes=%d%n" +
                        "block_cache_hits=%d block_cache_misses=%d%n" +
                        "blooms_checked=%d blooms_negative=%d%n" +
                        "disk_block_reads=%d%n",
                current.getEpoch(),
                lastSeqNo,
                activeEntries,
                activeBytes,
                immutablesCount,
                immutablesBytesTotal,
                current.getTableHandles().size(),
                sstTotalBytes,
                hitMiss.getHit(),
                hitMiss.getMiss(),
                bloomsCheck,
                bloomsNegative,
                diskBlockReads
        );
    }

    private void channelInit() throws IOException
    {

        //todo ako je dozvoljeno da pukne bez recovery onda je ovo nepotrebno
//        size=0;
        n=1;
        channel = FileChannel.open(walPath.resolve(Path.of(String.format("%06d.wal", segmentId))), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        size=channel.size();
        if(channel.size()==0)
        {
            ByteBuffer record = ByteBuffer.allocate(headerSize);
            record.put(magic.getBytes(StandardCharsets.US_ASCII));
            record.put((byte)1);
            record.put(new byte[headerSize-1-magic.length()]);
            record.flip();
            while (record.hasRemaining())
            {
                channel.write(record);
            }
            size=headerSize;
            channel.force(true);
        }
        else
        {
            //todo ovde se proverava header, mozda ne mora
        }
    }

    private void walWrite(byte[] keyBytes,byte[] valueBytes,byte rt) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES+Long.BYTES+Integer.BYTES+Integer.BYTES+keyBytes.length+valueBytes.length);
        buffer.put(rt);
        buffer.putLong(sequence);
        buffer.putInt(keyBytes.length);
        buffer.putInt(valueBytes.length);
        buffer.put(keyBytes);
        buffer.put(valueBytes);
        byte[] bytes=buffer.array();

        crc32C.update(bytes,0,bytes.length);
        ByteBuffer fullRecord = ByteBuffer.allocate(Integer.BYTES+bytes.length+Integer.BYTES);
        fullRecord.putInt(bytes.length);
        fullRecord.put(bytes);
        fullRecord.putInt((int)crc32C.getValue());
        fullRecord.flip();
        crc32C.reset();
        int fullRecordSize=fullRecord.remaining();

        while(size+fullRecordSize>config.getRollSize())
        {
            if(size==headerSize)
                throw new IOFailure();
            channel.force(true);
            channel.close();
            segmentId++;
            channelInit();
        }
        while (fullRecord.hasRemaining()) {
            channel.write(fullRecord);
        }
        size+=fullRecordSize;
        if(n==config.getWalFsyncEveryN()) {
            channel.force(true);
            n=1;
        }
        else
        {
            n++;
        }
    }



//    private void submit(boolean delete)
//    {
//        long copySegmentId=segmentId;
//        Main.ssTableWriter.submit(()->
//        {
//            try {
//                ssTableWrite();
//                if (delete)
//                    walDelete(copySegmentId);
//            }
//            catch (Exception e)
//            {
//                ssException=e;
//                memtableListLock.writeLock().lock();
//                try {
//                    conditionMemtableLock.signalAll();
//                }
//                finally {
//                    memtableListLock.writeLock().unlock();
//                }
//            }
//        });
//    }



    //todo dodaj da memtabela ne sme da predje int_max
    private void memtableWrite(ByteArray byteArray,MemtableEntry memtableEntry,boolean recovery) throws IOFailure, InterruptedException, IOException {
        boolean write=true;
        while(write && (memtableEntry.getSize() + active.getSize() >= config.getMemtableMaxBytes() || active.getMemtable().size() == Integer.MAX_VALUE))
        {


            //todo obrisati write kao condition i generalno i >= check i ovo ako ne treba da se strogo proverava ?=
            if(memtableEntry.getSize() + active.getSize() == config.getMemtableMaxBytes() || active.getMemtable().size() == Integer.MAX_VALUE)
            {
                active.put(byteArray,memtableEntry);
                write=false;
            }
            else
            {
                //todo proveriti da li je IOFailure, takodje za sad je 0 a mozda ce biti nesto drugo ako se doda header
                if(active.getSize()==0)
                    throw new IOFailure();
            }


            if(!recovery)
            {
                channel.force(true);
                channel.close();
                segmentId++;
                channelInit();
                if(write)
                    walWrite(memtableEntry.getKey(),memtableEntry.getValue(),memtableEntry.isTombstone()?RecordType.DELETE.value : RecordType.PUT.value);
            }

            if(recovery || config.getMaxImmutableTables()==0) {
                Memtable copy=new Memtable(active);
                long copySegmentId=segmentId;
                if(recovery) {
                    futures.add(Main.ssTableWriter.submit(() -> {
                        try {
                            manifestWrite(manifest.add(ssTableWrite(copy)));
                        } catch (Exception e) {
                            ssException=e;
                            return;
                        }
                        walDelete(copySegmentId);
                    }));
                    active.getMemtable().clear();
                    active.setSize(0);
                }
                else
                {
                    synchronized (Global.manifestLock) {
                        try {
                            Manifest manifest1 = manifest.add(ssTableWrite(copy));
                            manifestWrite(manifest1);
                            active=new Memtable();
                            synchronized (Global.versionLock) {
                                Version old = version;
                                //todo ovaj version radi kako treba zato sto je u pitanju single writer, inace bi morali da napravimo sinhronu metodu u manifestu koja generise version, a prosledimo parametre koji se tu ne nalaze
                                //todo sada radi i za single writer, ali se prave bespotrebne nove instance prilikom get-a, napraviti novu metodu koja ce vracati samo pokazivac, al je bitno da je da se ne koristi van ovog (i mozda jos kojeg) case-a
                                version = new Version(active, new ArrayList<>(immutables.reversed()), manifest1.getSet(), manifest1.getSetSize(), manifest1.getSetLevel(), manifest1.getEpoch(), immutablesSize, sequence);
                                old.decrement();
                            }
                        }
                        catch (IOException ioException)
                        {
                            throw new RuntimeException(ioException);
                        }
                    }
                    walDelete(copySegmentId);

                }
            }
            else {

//                memtableListLock.readLock().lock();
//                List<Memtable> old=new ArrayList<>(immutables);
//                List<Memtable> new1;
//                memtableListLock.readLock().unlock();
//                if(old.size() >= config.getMaxImmutableTables())
//                {
//                    submit(old,false);
//                }
                memtableListLock.writeLock().lock();
                try {
                    while (immutables.size() >= config.getMaxImmutableTables() && ssException==null) {
                        blockWrite = true; //todo trenutno mi ovo ne treba jer je write 1 thread al za slucaj da se to ikad promeni
                        conditionMemtableLock.await();
                    }
                    if (ssException != null)
                        throw new RuntimeException(ssException);
                    blockWrite=false;
                    active.setImmutable(true);
                    immutablesSize+=active.getSize();
                    immutables.add(active);
                    immutableQueue.put(new IQElement(active,segmentId));
                    active=new Memtable();
//                    new1=new ArrayList<>(immutables);
                    synchronized (Global.versionLock) {
                        Version oldVersion = version;
                        version = new Version(active, new ArrayList<>(immutables.reversed()), new ArrayList<>(oldVersion.getTableHandles()), new ArrayList<>(oldVersion.getTableHandlesBySize()), new ArrayList<>(oldVersion.getTableHandlesByLevel()),oldVersion.getEpoch(), immutablesSize, sequence);
                        oldVersion.decrement();
                    }
                }
                finally {
                    memtableListLock.writeLock().unlock();
                }
            }
        }
        if(write)
            active.put(byteArray,memtableEntry);
    }


    @Override
    public void put(byte[] keyBytes, byte[] valueBytes) {

        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(closed)
            throw new StoreClosed();
        if(ssException!=null)
            throw new RuntimeException(ssException);
        if(blockWrite)
            throw new OutOfMemoryError("Presao si limit imutabilnih memtabela");
        if(keyBytes==null)
            throw new InvalidArgument("Kljuc ne moze biti null");
        if(valueBytes==null)
            throw new InvalidArgument("Value za sada ne moze biti null");
        if(keyBytes.length==0)
            throw new InvalidArgument("Kljuc ne moze biti prazan");
        if(keyBytes.length>keySize)
            throw new InvalidArgument("Preveliki key");
        if(valueBytes.length>valueSize)
            throw new InvalidArgument("Preveliki value");


        //todo provera da li je ostalo dovoljno mesta u fajlu tj da li otvaramo sledeci
        try {
            //todo dodajemo i uslov kada predjemo na sledeci fajl
            walWrite(keyBytes,valueBytes,RecordType.PUT.value);
            memtableWrite(new ByteArray(keyBytes),new MemtableEntry(keyBytes,valueBytes,sequence,false),false);
            sequence++;
        } catch (IOException  | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] get(byte[] key) {
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(closed)
            throw new StoreClosed();
        if(key==null)
            throw new InvalidArgument("Kljuc ne moze biti null");
        if(key.length==0)
            throw new InvalidArgument("Kljuc ne moze biti prazan");
        if(ssException!=null)
            throw new RuntimeException(ssException);
        //todo proveri da li NotFound staviti unutar synchronized (vise nije synchronized sada je lock i unlock) ili ostaviti van

        Version current=acquireVersion();
        try{
            MemtableEntry entry = current.getActive().getMemtable().get(new ByteArray(key));
            if (entry != null) {
                if (entry.isTombstone())
                    throw new NotFound();
                return entry.getValue();
            }

            //todo null checkovi ili samo try ako budes lenj
            for (Memtable memtable : current.getImmutables()) {
                entry = memtable.getMemtable().get(new ByteArray(key));
                if (entry != null) {
                    if (entry.isTombstone())
                        throw new NotFound();
                    return entry.getValue();
                }
            }
            return ssTableRead(key,current);
        }
        finally {
            current.decrement();
        }
    }

    @Override
    public void delete(byte[] keyBytes) {
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(closed)
            throw new StoreClosed();
        if(ssException!=null)
            throw new RuntimeException(ssException);
        if(blockWrite)
            throw new OutOfMemoryError("Presao si limit imutabilnih memtabela");
        if(keyBytes==null)
            throw new InvalidArgument("Kljuc ne moze biti null");
        if(keyBytes.length==0)
            throw new InvalidArgument("Kljuc ne moze biti prazan");
        if(keyBytes.length>keySize)
            throw new InvalidArgument("Preveliki key");


        //todo provera da li je ostalo dovoljno mesta u fajlu tj da li otvaramo sledeci
        try {
            //todo dodajemo i uslov kada predjemo na sledeci fajl
            walWrite(keyBytes,new byte[0],RecordType.DELETE.value);
            memtableWrite(new ByteArray(keyBytes),new MemtableEntry(keyBytes,null,sequence,true),false);
            sequence++;
//            MemtableEntry old=memtables.getLast().getMemtable().put(byteArray,memtableEntry);
//            if(old!=null)
//                memtables.getLast().decrementSize(old.getSize());
//            memtables.getLast().incrementSize(memtableEntry.getSize());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    //todo pogledati da li ostati na try catch ili preci na throws
    @Override
    public void close() {
        //todo dodaj pored ovoga da se mora sacekati da se zavrsi metoda
        if(closed)
            throw new StoreClosed();
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        try {
            immutableQueue.put(new IQElement(null,-1));
            Main.compaction.compactionQueue.put(new CWElement(null,-1,-1,null));
            future.get();
            Main.ssTableWriter.close();
            Main.compactionWorker.close();
            Main.compactionLoop.close();
            lru.clear();
            if(lruWithSize!=null)
                lruWithSize.clear();
            channel.force(true);
            channel.close();
            closed=true;
        } catch (IOException | ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(ssException!=null)
            throw new RuntimeException(ssException);


    }

    List<Memtable> getMemtables() {
        return immutables;
    }

    //todo ova cela metoda moze na ssworker thread, ne sme na main bas, ovo vrv ne treba uopste ovo da radi
    @Override
    public void flushNow() {
        if (closed)
            throw new StoreClosed();
        if (config == null)
            throw new RuntimeException("Nisi uradio init");
        if (ssException != null)
            throw new RuntimeException(ssException);
        TableHandle tableHandle = super.flush();
        if (tableHandle == null)
            System.out.println("Nema immutable");
        else
            System.out.println(tableHandle.getId() + " " + tableHandle.getFileSize());

    }

    @Override
    public void listSst() {
        if(closed)
            throw new StoreClosed();
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(ssException!=null)
            throw new RuntimeException(ssException);
        Version current=version;
        for(TableHandle x:current.getTableHandles())
        {
            System.out.printf("id=%d, createdAt=%s, fileSize=%d, minsSeqNo=%d, maxSeqNo=%d, minKey=%s, maxKey=%s%n",x.getId(),x.getCreatedAt(),x.getFileSize(),x.getMinSeqNo(),x.getMaxSeqNo(),Arrays.toString(x.getMinKey()),Arrays.toString(x.getMaxKey()));
        }
    }

    //todo dodati statistike po bloku i  jos neki info iz  handle
    @Override
    public void sstInfo(String fileName) {
        if (closed)
            throw new StoreClosed();
        if (config == null)
            throw new RuntimeException("Nisi uradio init");
        if (ssException != null)
            throw new RuntimeException(ssException);
        Version current = acquireVersion();
        try {
            TableHandle tableHandle = current.getMapTableHandles().get(fileName);
            if (tableHandle == null) {
                System.out.println("Ne postoji sst sa ovim fileName");
                return;
            }
            super.sstInfo(tableHandle);
        }
        finally {
            current.decrement();
        }
    }

    @Override
    public void manifestInfo() {
        if(closed)
            throw new StoreClosed();
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(ssException!=null)
            throw new RuntimeException(ssException);
        List<TableHandle> set=null;
        synchronized (manifest)
        {
            System.out.println(manifest.getEpoch());
            set=manifest.getSet();
        }
        for(TableHandle x:set)
        {
            System.out.printf("id=%d, fileSize=%d, minsSeqNo=%d, maxSeqNo=%d, minKey=%s, maxKey=%s%n",x.getId(),x.getFileSize(),x.getMinSeqNo(),x.getMaxSeqNo(),Arrays.toString(x.getMinKey()),Arrays.toString(x.getMaxKey()));

        }
    }

    @Override
    public void versionInfo() {
        if(closed)
            throw new StoreClosed();
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(ssException!=null)
            throw new RuntimeException(ssException);
        Version current=version;
        System.out.println("epoch: "+current.getEpoch());
        for(Memtable x:current.getImmutables())
        {
            System.out.println("size: "+x.getSize());
        }
        for(TableHandle x:current.getTableHandles())
        {
            System.out.printf("id=%d, fileSize=%d, minsSeqNo=%d, maxSeqNo=%d, minKey=%s, maxKey=%s%n",x.getId(),x.getFileSize(),x.getMinSeqNo(),x.getMaxSeqNo(),Arrays.toString(x.getMinKey()),Arrays.toString(x.getMaxKey()));
        }

    }
}
