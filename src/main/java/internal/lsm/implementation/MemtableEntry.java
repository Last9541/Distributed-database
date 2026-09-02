package internal.lsm.implementation;

import java.util.Arrays;

//todo da li treba da se racuna i velicina samog pokazivaca za key i value (pointer size)
public class MemtableEntry {
    private byte[] key=new byte[0];
    private byte[] value=new byte[0];
    private long seqNo;
    private boolean isTombstone;
    public static final long documentedSize=Long.BYTES+Byte.BYTES+32;
    private long size=documentedSize;

    public MemtableEntry(byte[] key, byte[] value, long seqNo, boolean isTombstone) {
        if(key!=null) {
            this.key = Arrays.copyOf(key, key.length);
            size+=key.length;
        }
        if(value!=null) {
            this.value = Arrays.copyOf(value, value.length);
            size += value.length;
        }
        this.seqNo = seqNo;
        this.isTombstone = isTombstone;
    }

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public long getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(long seqNo) {
        this.seqNo = seqNo;
    }

    public boolean isTombstone() {
        return isTombstone;
    }

    public void setTombstone(boolean tombstone) {
        isTombstone = tombstone;
    }

    public long getSize() {
        return size;
    }
}
