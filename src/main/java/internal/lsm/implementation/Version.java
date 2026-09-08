package internal.lsm.implementation;

import internal.lsm.TableHandle;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Version {
    private Memtable active;
    private List<Memtable> immutables;
    private List<TableHandle> tableHandles;
    private volatile int epoch;
    private AtomicInteger refCount=new AtomicInteger();

    public Version(Memtable active, List<Memtable> immutables, List<TableHandle> tableHandles, int epoch) {
        this.active = active;
        this.immutables = immutables;
        this.tableHandles = tableHandles;
        this.epoch = epoch;
    }

    public Memtable getActive() {
        return active;
    }

    public void setActive(Memtable active) {
        this.active = active;
    }

    public List<Memtable> getImmutables() {
        return immutables;
    }

    public void setImmutables(List<Memtable> immutables) {
        this.immutables = immutables;
    }

    public List<TableHandle> getTableHandles() {
        return tableHandles;
    }

    public void setTableHandles(List<TableHandle> tableHandles) {
        this.tableHandles = tableHandles;
    }

    public int getEpoch() {
        return epoch;
    }

    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    public AtomicInteger getRefCount() {
        return refCount;
    }

    public void setRefCount(AtomicInteger refCount) {
        this.refCount = refCount;
    }
}
