package internal.lsm.implementation;

import java.util.SortedMap;
import java.util.TreeMap;

public class Memtable {
    private SortedMap<ByteArray,MemtableEntry>memtable=new TreeMap<>();

    //todo pocetni i krajni seqno se vrv mogu uzeti iz memtabele

    private long size;

    private boolean immutable;

    //todo promeniti i videti da li ovaj atribut postoji negde vec sada se ne secam
    private static long maxSize=999999;


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

    public static long getMaxSize() {
        return maxSize;
    }

    public static void setMaxSize(long maxSize) {
        Memtable.maxSize = maxSize;
    }

    public long getSize() {
        return size;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    public void incrementSize(long increment)
    {
        size+=increment;
    }
    public void decrementSize(long decrement)
    {
        size-=decrement;
    }
}
