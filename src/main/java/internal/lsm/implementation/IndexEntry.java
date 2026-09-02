package internal.lsm.implementation;

public class IndexEntry {
    private byte[] key;
    private long index;
    private int size=Long.BYTES;

    public IndexEntry(byte[] key, long index) {
        this.key = key;
        this.index = index;
        this.size+=key.length;
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

    public int getSize() {
        return size;
    }
}
