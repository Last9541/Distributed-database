package internal.lsm.implementation;

public class SparseIndexEntry {
    private byte[] key;
    private long index;

    public SparseIndexEntry(byte[] key, long index) {
        this.key = key;
        this.index = index;
    }

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }
}
