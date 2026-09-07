package internal.lsm.implementation.lru;

import internal.lsm.implementation.ByteArray;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LruWithSize extends LinkedHashMap<internal.lsm.implementation.lru.LruKey, internal.lsm.implementation.ByteArray> {
    private long size;
    private final long maxSize;
    public LruWithSize(long maxSize) {
        super(16, 0.75f, true);
        this.maxSize=maxSize;
    }

    @Override
    public ByteArray put(LruKey key, ByteArray value) {
        if(containsKey(key))
            size-=get(key).getBytes().length;
        ByteArray val =super.put(key, value);
        size+=value.getBytes().length;
        while(!isEmpty() && size>maxSize)
        {
             var entry=firstEntry();
             myRemove(entry.getKey());
        }
        return val;
    }


    public ByteArray myRemove(LruKey key) {
        ByteArray value=super.remove(key);
        if(value!=null)
            size-=value.getBytes().length;
        return value;
    }

    @Override
    public void clear() {
        super.clear();
        size=0;
    }
}
