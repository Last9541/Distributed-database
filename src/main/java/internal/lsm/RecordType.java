package internal.lsm;

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
        else
            return RecordType.DELETE;
    }
}
