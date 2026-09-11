package internal.lsm.implementation;

public class IQElement {
    private Memtable memtable;
    private long segmentId;

    public IQElement(Memtable memtable, long segmentId) {
        this.memtable = memtable;
        this.segmentId = segmentId;
    }

    public Memtable getMemtable() {
        return memtable;
    }

    public void setMemtable(Memtable memtable) {
        this.memtable = memtable;
    }

    public long getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(long segmentId) {
        this.segmentId = segmentId;
    }
}
