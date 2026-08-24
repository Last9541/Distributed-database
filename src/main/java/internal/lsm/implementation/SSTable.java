package internal.lsm.implementation;

import internal.lsm.Config;
import internal.lsm.errors.InvalidArgument;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class SSTable {

    protected Path sstPath;

    private long segmentId=0;

    private long tempSegmentId=0;

    private LsmImplementation lsmImplementation;

    protected Config config;

    private void ssTableWrite(Memtable memtable)
    {
        List<SparseIndexEntry>sparseIndex=new ArrayList<>();
        //todo ovo ako se ne secam nece raditi ali da vidim da li barem pomaze u compile time
        assert lsmImplementation!=null;
        if(!memtable.isImmutable())
            throw new InvalidArgument("Ovo ne sme da se ikada desi ako se desilo proveri STO STO STO");
        //todo proveriti da li mi trebaju sve ove permisije
        try(FileChannel fileChannel = FileChannel.open(sstPath.resolve(Path.of(String.format("%06d.sst.tmp", tempSegmentId))), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)){
            //todo jednog dana ovde ce doci magic/version al me jako mrzi sada da se bakcem time, takodje i dalje fali provera toga u wal-u nemoj zaboraviti
//            long bytesSum=0;
            long blockSize=0;
            //todo posto je ovo int ima smisla da i memtable size i memtable entry size bude int
            //todo proveri da li ce ovo sa 0 da radi
            ByteBuffer block = ByteBuffer.allocate(0);

            //todo ne treba ti ovo sa blockom
            for (MemtableEntry memtableEntry : memtable.getMemtable().values()) {
                if(blockSize<memtableEntry.getSize()) {
                    block.flip();
                    while(block.hasRemaining())
                    {
                        fileChannel.write(block);
                    }
                    blockSize=config.getBlockSize();
                    block=ByteBuffer.allocate((int) blockSize);
                    //todo ne vidim svrhu da pisem ovo na pocetku
//                    block.putInt(memtableEntry.getKey().length);
//                    block.put(memtableEntry.getKey());
                    //todo proveri da li ovo uzimamo
                    sparseIndex.add(new SparseIndexEntry(memtableEntry.getKey(),fileChannel.position()));
                }
                    block.putInt(memtableEntry.getKey().length);
                    block.put(memtableEntry.getKey());
                    block.putInt(memtableEntry.getValue().length);
                    block.put(memtableEntry.getValue());
                    block.putLong(memtableEntry.getSeqNo());
                    block.put(memtableEntry.isTombstone()?(byte) 1:(byte) 0);
//                bytesSum+=memtableEntry.getSize();
            }
            //todo proveriti gde staviti ovo
            tempSegmentId++;
            //todo ovde mora nekako i atomicno menjanje imena fajla ovo videti
            fileChannel.force(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadSegmentsId()
    {
        try(DirectoryStream<Path> filesStream = Files.newDirectoryStream(sstPath)) {
            for(Path file:filesStream)
            {
                try {
                    String name = file.getFileName().toString();
                    if (Files.isRegularFile(file) && name.contains(".")) {
                        if (name.substring(name.indexOf('.')).equals(".tmp")) {
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

    public void ssTableWrite()
    {

        if(!(this instanceof LsmImplementation))
            throw new RuntimeException("Ako ikad dodje ovde onda nzm sta da kazem");
        lsmImplementation=(LsmImplementation) this;
        loadSegmentsId();
        //todo prebaci memtabele ovde
        int len=lsmImplementation.getMemtables().size()-1;
        for(int i=0;i<len;i++)
        {
            ssTableWrite(lsmImplementation.getMemtables().get(i));
        }
    }

}
