package internal.lsm.implementation;

import internal.lsm.Config;
import internal.lsm.Global;
import internal.lsm.Lsm;
import internal.lsm.RecordType;
import internal.lsm.errors.IOFailure;
import internal.lsm.errors.InvalidArgument;
import internal.lsm.errors.NotFound;
import internal.lsm.errors.StoreClosed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.CRC32C;

//todo zameniti exceptione svuda za errors exceptione
public class LsmImplementation implements Lsm {


    private List<Memtable>memtables=new ArrayList<>();
    private CRC32C crc32C=new CRC32C();


    private long sequence=1;
    private long segmentId=1;
    private Object walLock=new Object();
    private FileChannel channel;
    private Config config=new Config();
    private Path dataPath;
    private Path walPath;
    private long size;
    private final int headerSize=8;
    private int n=1;
    private boolean closed;
    private int truncated;

    private final long keySize=64000;
    private final long valueSize=16777216;
    private final long lenSize=keySize+valueSize+Byte.BYTES+Long.BYTES+Integer.BYTES*2;

    public LsmImplementation(Config config) throws IOException {
        this.config = config;
        init();
    }


    public LsmImplementation() throws IOException {
        init();
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
        System.out.println("truncated_segment="+file.getFileName().toString() + "truncated_to="+start);
    }

    private void init() throws IOException {
        dataPath=Path.of(config.getDataDir());
        Files.createDirectories(dataPath);
        walPath = dataPath.resolve(Path.of("wal"));
        Files.createDirectories(walPath);
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
                                if(len>lenSize)
                                    throw new RuntimeException();
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
                                    //todo proveriti da li ovako da castujem long ili samo jedan
                                    if(keyBytesLength<=0 || valueBytesLength<0 || (long)keyBytesLength+(long)valueBytesLength!=later.remaining())
                                    {
                                        throw new Exception();
                                    }
                                    //TODO napraviti novi exception za ovo
                                    if(keyBytesLength>keySize)
                                        throw new IOFailure();
                                    byte[] keyArray = new byte[keyBytesLength];
                                    later.get(keyArray);
//                                    String key = new String(keyArray, StandardCharsets.UTF_8);
//                                    String value = null;
                                    if(valueBytesLength>later.remaining())
                                        throw new Exception();
                                    if(valueBytesLength>valueSize)
                                        throw new IOFailure();
                                    if (valueBytesLength > 0) {
                                        byte[] valueArray = new byte[valueBytesLength];
                                        later.get(valueArray);
//                                        value = new String(valueArray, StandardCharsets.UTF_8);
                                    }
                                    sequence = Math.max(sequence, newSequence);
                                }
                                catch (IOFailure ioFailure) {
                                    throw ioFailure;
                                }
                                catch (Exception e)
                                {
                                    fileChannel.truncate(start);
                                    truncated++;
                                    System.out.println("truncated_segment="+file.getFileName().toString() + "truncated_to="+start);
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


    @Override
    public void put(byte[] keyBytes, byte[] valueBytes) {

        if(closed)
            throw new StoreClosed();
        if(keyBytes==null)
            throw new IllegalArgumentException("Kljuc ne moze biti null");
        if(valueBytes==null)
            throw new IllegalArgumentException("Value za sada ne moze biti null");
        if(keyBytes.length==0)
            throw new IllegalArgumentException("Kljuc ne moze biti prazan");
        if(keyBytes.length>keySize)
            throw new IllegalArgumentException("Preveliki key");
        if(valueBytes.length>valueSize)
            throw new IllegalArgumentException("Preveliki value");
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES+Long.BYTES+Integer.BYTES+Integer.BYTES+keyBytes.length+valueBytes.length);
        buffer.put(RecordType.PUT.value);
        buffer.putLong(sequence++);
        buffer.putInt(keyBytes.length);
        buffer.putInt(valueBytes.length);
        buffer.put(keyBytes);
        buffer.put(valueBytes);

        //todo provera da li je ostalo dovoljno mesta u fajlu tj da li otvaramo sledeci
        try {
            //todo dodajemo i uslov kada predjemo na sledeci fajl
            walWrite(buffer.array());
            MemtableEntry memtableEntry=new MemtableEntry(keyBytes,valueBytes,sequence,false);
            ByteArray byteArray=new ByteArray(keyBytes);
            MemtableEntry old=memtables.getLast().getMemtable().put(byteArray,memtableEntry);
            if(old!=null)
                memtables.getLast().decrementSize(old.getSize());
            memtables.getLast().incrementSize(memtableEntry.getSize());

        } catch (IOException | IOFailure e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] get(byte[] key) {
        //todo null checkovi ili samo try ako budes lenj
        for(int i=memtables.size()-1;i>=0;i--)
        {
            MemtableEntry entry=memtables.get(i).getMemtable().get(new ByteArray(key));
            if(entry!=null && !entry.isTombstone())
                return entry.getValue();
        }
        throw new NotFound();
    }

    @Override
    public void delete(byte[] keyBytes) {
        if(closed)
            throw new StoreClosed();
        if(keyBytes==null)
            throw new IllegalArgumentException("Kljuc ne moze biti null");
        if(keyBytes.length==0)
            throw new IllegalArgumentException("Kljuc ne moze biti prazan");
        if(keyBytes.length>keySize)
            throw new IllegalArgumentException("Preveliki key");
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES+Long.BYTES+Integer.BYTES+Integer.BYTES+keyBytes.length);
        buffer.put(RecordType.DELETE.value);
        buffer.putLong(sequence++);
        buffer.putInt(keyBytes.length);
        buffer.putInt(0);
        buffer.put(keyBytes);
        //todo provera da li je ostalo dovoljno mesta u fajlu tj da li otvaramo sledeci
        try {
            //todo dodajemo i uslov kada predjemo na sledeci fajl
            walWrite(buffer.array());
            MemtableEntry memtableEntry=new MemtableEntry(keyBytes,null,sequence,true);
            ByteArray byteArray=new ByteArray(keyBytes);
            MemtableEntry old=memtables.getLast().getMemtable().put(byteArray,memtableEntry);
            if(old!=null)
                memtables.getLast().decrementSize(old.getSize());
            memtables.getLast().incrementSize(memtableEntry.getSize());
        } catch (IOException | IOFailure e) {
            throw new RuntimeException(e);
        }
    }

    //todo pogledati da li ostati na try catch ili preci na throws
    @Override
    public void close() {
        try {
            if(closed)
                throw new StoreClosed();
            channel.force(true);
            channel.close();
            closed=true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }
}
