package internal.lsm.implementation;

import java.util.Arrays;
import java.util.Objects;

public class ByteArray implements Comparable<ByteArray> {

    private byte[] bytes;

    public ByteArray(byte[] bytes) {
        this.bytes = Arrays.copyOf(bytes,bytes.length);
    }



    @Override
    public int compareTo(ByteArray o) {
        int len=Math.min(bytes.length,o.bytes.length);
        for(int i=0;i<len;i++)
        {
            if(bytes[i]!=o.bytes[i])
                return Byte.compareUnsigned(bytes[i], o.bytes[i]);
        }
        return bytes.length-o.bytes.length;
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
