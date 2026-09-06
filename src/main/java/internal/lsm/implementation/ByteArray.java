package internal.lsm.implementation;

import internal.lsm.Global;

import java.util.Arrays;
import java.util.Objects;

public class ByteArray implements Comparable<ByteArray> {

    private byte[] bytes;

    public ByteArray(byte[] bytes) {
        this.bytes = Arrays.copyOf(bytes,bytes.length);
    }



    @Override
    public int compareTo(ByteArray o) {
       return Global.compareTo(bytes,o.bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ByteArray byteArray = (ByteArray) o;
        return Objects.deepEquals(bytes, byteArray.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes,bytes.length);
    }

    public void setBytes(byte[] bytes) {
        this.bytes = Arrays.copyOf(bytes,bytes.length);
    }
}
