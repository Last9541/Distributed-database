package internal.lsm;

import internal.lsm.errors.InvalidArgument;

public enum RecordType {
    PUT(1),DELETE( 2);
    public final byte value;

    RecordType(int value) {
        this.value = (byte) value;
    }

    public static RecordType getByValue(byte value)
    {
        if(value==1)
            return RecordType.PUT;
        if(value==2)
            return RecordType.DELETE;
        throw new InvalidArgument("Value mora biti 1 ili 2");
    }
}
