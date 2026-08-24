package internal.lsm.implementation;

import cmd.lsmkv.Main;
import internal.lsm.Config;
import internal.lsm.Lsm;
import internal.lsm.RecordType;
import internal.lsm.errors.IOFailure;
import internal.lsm.errors.InvalidArgument;
import internal.lsm.errors.NotFound;
import internal.lsm.errors.StoreClosed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.CRC32C;

//todo zameniti exceptione svuda za errors exceptione
public class LsmImplementation extends SSTable implements Lsm {


    private List<Memtable>memtables=new ArrayList<>();
    private CRC32C crc32C=new CRC32C();


    private long sequence=1;
    private long segmentId=1;
    private Object walLock=new Object();
    private final Object conditionMemtableLock=new Object();
    private final ReentrantReadWriteLock memtableListLock =new ReentrantReadWriteLock();
    private FileChannel channel;
    private Path dataPath;
    private Path walPath;
    private long size;
    private final int headerSize=8;
    private int n=1;
    private boolean closed;
    private int truncated;
    private boolean blockWrite;
    //todo prebaci u config
    private final long keySize=64000;
    private final long valueSize=16777216;
    private final long lenSize=keySize+valueSize+Byte.BYTES+Long.BYTES+Integer.BYTES*2;
    private long immutablesSize=0;

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

    private boolean bufferRead(ByteBuffer byteBuffer,FileChannel fileChannel) throws IOException, IOFailure {
        while (byteBuffer.hasRemaining()) {
            if (fileChannel.read(byteBuffer) == -1) {
                return false;
            }
        }
        byteBuffer.flip();
        return true;
    }


    private void trunc(FileChannel fileChannel,long start,Path file) throws IOException {
        fileChannel.truncate(start);
        truncated++;
        System.out.println("truncated_segment="+file.getFileName().toString() + " truncated_to="+start);
    }

    public void init(Config config) throws IOException {
        if(config.getBlockSize()<MemtableEntry.documentedSize+keySize+valueSize)
            throw new InvalidArgument("Premali blockSize u config");
        closed=false;
        this.config=config;
        memtables.add(new Memtable());
        dataPath=Path.of(config.getDataDir());
        Files.createDirectories(dataPath);
        walPath = dataPath.resolve(Path.of("wal"));
        Files.createDirectories(walPath);
        sstPath=dataPath.resolve(Path.of("sst"));
        Files.createDirectories(sstPath);
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
                if (Files.isRegularFile(file) && name.contains(".") && name.substring(name.indexOf('.')).equals(".wal"))
                    try {
                        segmentId = Math.max(segmentId, Long.parseLong(name.substring(0, name.indexOf('.'))));
                        try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ,StandardOpenOption.WRITE))
                        {
                            ByteBuffer byteBuffer = ByteBuffer.allocate(headerSize);
                            bufferRead(byteBuffer,fileChannel);
                            //todo ovaj header mora da se validira, ne zaboravi to
                            byte[] header=new byte[headerSize];
                            byteBuffer.get(header);
                            long size=fileChannel.size();
                            while(fileChannel.position()<size) {
                                long start=fileChannel.position();
                                byteBuffer = ByteBuffer.allocate(Integer.BYTES);
                                if(!bufferRead(byteBuffer,fileChannel)) {
                                    trunc(fileChannel,start,file);
                                    break;
                                }
                                int len=byteBuffer.getInt();
                                //mora biti >= konstantama + 1 zato sto kljuc ne sme biti prazan, a ne sme biti veci od size - velicina checksuma(4B TJ integer size)
                                if(len<Byte.BYTES+Long.BYTES+ Integer.BYTES+Integer.BYTES+Byte.BYTES || fileChannel.position()+len+Integer.BYTES>size)
                                {
                                    trunc(fileChannel,start,file);
                                    break;
                                }
                                if(len>lenSize) {
                                    trunc(fileChannel,start,file);
                                    throw new RuntimeException();
                                }
                                byteBuffer = ByteBuffer.allocate(len);
                                if(!bufferRead(byteBuffer,fileChannel)) {
                                    trunc(fileChannel,start,file);
                                    break;
                                }
                                byte[] content=new byte[len];
                                byteBuffer.get(content);
                                crc32C.update(content,0,len);
                                int newCheckSum=(int)crc32C.getValue();
                                crc32C.reset();
                                byteBuffer=ByteBuffer.allocate(Integer.BYTES);
                                if(!bufferRead(byteBuffer,fileChannel))
                                {
                                    trunc(fileChannel,start,file);
                                    break;
                                }

                                int checksum=byteBuffer.getInt();
                                if(newCheckSum!=checksum)
                                {
                                    trunc(fileChannel,start,file);
                                    break;
                                }
                                try {
                                    ByteBuffer later = ByteBuffer.wrap(content);
                                    RecordType recordType = RecordType.getByValue(later.get());
                                    long newSequence=later.getLong();
                                    int keyBytesLength = later.getInt();
                                    int valueBytesLength = later.getInt();
                                    //todo proveriti da li ovako da castujem long ili samo jedan, proveri takodje ovo za DELETE
                                    if(keyBytesLength<=0 || valueBytesLength<0 || (long)keyBytesLength+(long)valueBytesLength!=later.remaining() || recordType==RecordType.DELETE && valueBytesLength>0)
                                    {
                                        throw new Exception();
                                    }
                                    //TODO napraviti novi exception za ovo
                                    if(keyBytesLength>keySize)
                                        throw new IOFailure();
                                    byte[] keyArray = new byte[keyBytesLength];
                                    byte[] valueArray=null;
                                    later.get(keyArray);
//                                    String key = new String(keyArray, StandardCharsets.UTF_8);
//                                    String value = null;
                                    if(valueBytesLength>later.remaining())
                                        throw new Exception();
                                    if(valueBytesLength>valueSize)
                                        throw new IOFailure();
                                    if (valueBytesLength > 0) {
                                        valueArray = new byte[valueBytesLength];
                                        later.get(valueArray);
//                                        value = new String(valueArray, StandardCharsets.UTF_8);
                                    }

                                    //todo dodaj racunanje velicine i rolling za size sad ti se spava bolje ne diraj
                                    //todo ovde ipak neces praviti nove instance nego ces flushovati kada se napuni pa prazniti stablo
                                    memtableWrite(new ByteArray(keyArray),new MemtableEntry(keyArray,valueArray,newSequence,recordType==RecordType.DELETE),true);
                                    sequence = Math.max(sequence, newSequence);
                                }
                                catch (IOFailure ioFailure) {
                                    throw ioFailure;
                                }
                                catch (Exception e)
                                {
                                    trunc(fileChannel,start,file);
                                    break;
                                }

                            }
                          //todo posle obrisati ovaj catch i sve u Mainu da se regulise
                        } catch (IOFailure e) {
                            throw new RuntimeException(e);
                        }
                    }
                    catch (NumberFormatException ignored)
                    {

                    }
            }
            this.sequence=sequence+1;
            channelInit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String stats() {
        //todo dodaj lock ovde
        memtableListLock.readLock().lock();
        Memtable memtable=memtables.getLast();
        int activeEntries=memtable.getMemtable().size();
        long activeBytes=memtable.getSize();
        int immutablesCount=memtables.size()-1;
        long immutablesBytesTotal=immutablesSize;
        long lastSeqNo=sequence;
        memtableListLock.readLock().unlock();
        return String.format("%d %d %d %d %d",activeEntries,activeBytes,immutablesCount,immutablesBytesTotal,lastSeqNo);
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
            record.put("ABCD".getBytes());
            record.put((byte)1);
            record.put(new byte[3]);
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

    private void walWrite(byte[] bytes) throws IOException, IOFailure {
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


    //todo dodaj da memtabela ne sme da predje int_max
    private void memtableWrite(ByteArray byteArray,MemtableEntry memtableEntry,boolean recovery) throws IOFailure, InterruptedException {
        boolean write=true;
        while(write && (memtableEntry.getSize() + memtables.getLast().getSize() >= config.getMemtableMaxBytes() || memtables.getLast().getMemtable().size() == Integer.MAX_VALUE))
        {
            //todo proveriti da li je IOFailure, takodje za sad je 0 a mozda ce biti nesto drugo ako se doda header
            //todo moze da se napise i da je  memtableEntry.getSize() >= config.getMemtableMaxBytes()
            if(memtables.getLast().getSize()==0)
                throw new IOFailure();

            //todo obrisati write kao condition i generalno i >= check i ovo ako ne treba da se strogo proverava ?=
            if(memtableEntry.getSize() + memtables.getLast().getSize() == config.getMemtableMaxBytes() || memtables.getLast().getMemtable().size() == Integer.MAX_VALUE)
            {
                memtables.getLast().put(byteArray,memtableEntry);
                write=false;
            }

            //todo ovde ce ici upisivanje u SSTable
            Main.ssTableWriter.submit(this::ssTableWrite);
            if(recovery) {
                memtables.getLast().getMemtable().clear();
                memtables.getLast().setSize(0);
            }
            else {
                //todo kompletiraj u sstabeli ovaj condition lock
                synchronized (conditionMemtableLock) {
                    while (memtables.size() > config.getMaxImmutableTables()) {
                        blockWrite = true; //todo trenutno mi ovo ne treba jer je write 1 thread al za slucaj da se to ikad promeni
                        conditionMemtableLock.wait();
                    }
                    blockWrite=false;
                }

                memtableListLock.writeLock().lock();
                    memtables.getLast().setImmutable(true);
                    //todo racunanje ne mora da bude unutar locka sa obzirom da smo sigurni da cemo imati 1 writera, al ako se to promeni onda je korisno
                    immutablesSize+=memtables.getLast().getSize();
                    memtables.add(new Memtable());
                memtableListLock.writeLock().unlock();
            }
        }
        if(write)
            memtables.getLast().put(byteArray,memtableEntry);
    }


    @Override
    public void put(byte[] keyBytes, byte[] valueBytes) {

        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(closed)
            throw new StoreClosed();
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
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES+Long.BYTES+Integer.BYTES+Integer.BYTES+keyBytes.length+valueBytes.length);
        buffer.put(RecordType.PUT.value);
        buffer.putLong(sequence);
        buffer.putInt(keyBytes.length);
        buffer.putInt(valueBytes.length);
        buffer.put(keyBytes);
        buffer.put(valueBytes);

        //todo provera da li je ostalo dovoljno mesta u fajlu tj da li otvaramo sledeci
        try {
            //todo dodajemo i uslov kada predjemo na sledeci fajl
            walWrite(buffer.array());
            memtableWrite(new ByteArray(keyBytes),new MemtableEntry(keyBytes,valueBytes,sequence,false),false);
            sequence++;
        } catch (IOException | IOFailure | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] get(byte[] key) {
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(closed)
            throw new StoreClosed();
        //todo proveri da li NotFound staviti unutar synchronized (vise nije synchronized sada je lock i unlock) ili ostaviti van
        memtableListLock.readLock().lock();
            //todo null checkovi ili samo try ako budes lenj
            for (int i = memtables.size() - 1; i >= 0; i--) {
                MemtableEntry entry = memtables.get(i).getMemtable().get(new ByteArray(key));
                if (entry != null) {
                    if(entry.isTombstone())
                        break;
                    //todo proveriti da li ovde staviti
                    memtableListLock.readLock().unlock();
                    return entry.getValue();
                }
            }
        memtableListLock.readLock().unlock();
        throw new NotFound();
    }

    @Override
    public void delete(byte[] keyBytes) {
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        if(closed)
            throw new StoreClosed();
        if(blockWrite)
            throw new OutOfMemoryError("Presao si limit imutabilnih memtabela");
        if(keyBytes==null)
            throw new InvalidArgument("Kljuc ne moze biti null");
        if(keyBytes.length==0)
            throw new InvalidArgument("Kljuc ne moze biti prazan");
        if(keyBytes.length>keySize)
            throw new InvalidArgument("Preveliki key");
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES+Long.BYTES+Integer.BYTES+Integer.BYTES+keyBytes.length);
        buffer.put(RecordType.DELETE.value);
        buffer.putLong(sequence);
        buffer.putInt(keyBytes.length);
        buffer.putInt(0);
        buffer.put(keyBytes);
        //todo provera da li je ostalo dovoljno mesta u fajlu tj da li otvaramo sledeci
        try {
            //todo dodajemo i uslov kada predjemo na sledeci fajl
            walWrite(buffer.array());
            memtableWrite(new ByteArray(keyBytes),new MemtableEntry(keyBytes,null,sequence,true),false);
            sequence++;
//            MemtableEntry old=memtables.getLast().getMemtable().put(byteArray,memtableEntry);
//            if(old!=null)
//                memtables.getLast().decrementSize(old.getSize());
//            memtables.getLast().incrementSize(memtableEntry.getSize());
        } catch (IOException | IOFailure | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    //todo pogledati da li ostati na try catch ili preci na throws
    @Override
    public void close() {
        if(closed)
            throw new StoreClosed();
        if(config==null)
            throw new RuntimeException("Nisi uradio init");
        try {
            channel.force(true);
            channel.close();
            closed=true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    List<Memtable> getMemtables() {
        return memtables;
    }
}
