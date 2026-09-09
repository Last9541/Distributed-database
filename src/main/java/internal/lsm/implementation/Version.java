package internal.lsm.implementation;

import internal.lsm.TableHandle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Version {
    private final Memtable active;
    private final List<Memtable> immutables;
    private final Map<String,TableHandle> mapTableHandles=new HashMap<>();
    private final List<TableHandle> tableHandles;
    private final List<TableHandle> tableHandlesBySize;
    private final int epoch;
    private final AtomicInteger refCount=new AtomicInteger();
    private final long immutableSize;
    private final long lastSeqNo;
    //todo versionId

    public Version(Memtable active, List<Memtable> immutables, List<TableHandle> tableHandles,List<TableHandle>tableHandlesBySize, int epoch, long immutableSize, long lastSeqNo) {
        this.active = active;
        this.immutables = immutables;
        this.tableHandles = tableHandles;
        this.tableHandlesBySize=tableHandlesBySize;
        for(TableHandle x:tableHandles)
        {
            mapTableHandles.put(x.getFileName(),x);
        }
        this.epoch = epoch;
        this.immutableSize = immutableSize;
        this.lastSeqNo = lastSeqNo;
    }

    public List<TableHandle> getTableHandlesBySize() {
        return tableHandlesBySize;
    }

    public Map<String, TableHandle> getMapTableHandles() {
        return mapTableHandles;
    }

    public long getLastSeqNo() {
        return lastSeqNo;
    }


    public long getImmutableSize() {
        return immutableSize;
    }


    public Memtable getActive() {
        return active;
    }


    public List<Memtable> getImmutables() {
        return immutables;
    }


    public List<TableHandle> getTableHandles() {
        return tableHandles;
    }


    public int getEpoch() {
        return epoch;
    }



    public AtomicInteger getRefCount() {
        return refCount;
    }

}
