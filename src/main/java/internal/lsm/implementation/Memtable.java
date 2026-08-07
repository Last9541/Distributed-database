package internal.lsm.implementation;

import java.util.SortedMap;
import java.util.TreeMap;

public class Memtable {
    private SortedMap<ByteArray,MemtableEntry>memtable=new TreeMap<>();

    private long size;

    public SortedMap<ByteArray, MemtableEntry> getMemtable() {
        return memtable;
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
