package internal.lsm.implementation;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class Memtable {
    private SortedMap<ByteArray,MemtableEntry>memtable=new ConcurrentSkipListMap<>();

    //todo pocetni i krajni seqno se vrv mogu uzeti iz memtabele

    private volatile long size;

    private boolean immutable;
    private boolean read=false;

//    //todo promeniti i videti da li ovaj atribut postoji negde vec sada se ne secam
//    private static long maxSize=999999;

    public Memtable()
    {

    }

    public Memtable(Memtable mem)
    {
        memtable=new ConcurrentSkipListMap<>(mem.memtable);
        size=mem.size;
        immutable=true;

    }

    public SortedMap<ByteArray, MemtableEntry> getMemtable() {
        return memtable;
    }




    //todo napisi wrapper za put ako treba da se koristi boolean immutable
    public void put(ByteArray byteArray,MemtableEntry memtableEntry)
    {
        if(immutable)
            throw new RuntimeException("NAPRAVI NOVI EXCEPTION ZA OVO POSLE");
       MemtableEntry old = memtable.put(byteArray,memtableEntry);
        if(old!=null)
            decrementSize(old.getSize());
        incrementSize(memtableEntry.getSize());
    }

//    public static long getMaxSize() {
//        return maxSize;
//    }
//
//    public static void setMaxSize(long maxSize) {
//        Memtable.maxSize = maxSize;
//    }


    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public synchronized long getSize() {
        return size;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    public synchronized void incrementSize(long increment)
    {
        size+=increment;
    }
    public synchronized void decrementSize(long decrement)
    {
        size-=decrement;
    }
}
