package internal.lsm.implementation.lru;

import java.util.LinkedHashMap;

public class LruWithSize extends LinkedHashMap<LruSizeKey, byte[]> {
    private long size;
    private final long maxSize;
    public LruWithSize(long maxSize) {
        super(16, 0.75f, true);
        this.maxSize=maxSize;
    }

    @Override
    public synchronized byte[] put(LruSizeKey key, byte[] value) {
        if(containsKey(key))
            size-=get(key).length;
        byte[] val =super.put(key, value);
        size+=value.length;
        while(!isEmpty() && size>maxSize)
        {
             var entry=firstEntry();
             myRemove(entry.getKey());
        }
        return val;
    }


    public synchronized byte[] myRemove(LruSizeKey key) {
        byte[] value=super.remove(key);
        if(value!=null)
            size-=value.length;
        return value;
    }

    @Override
    public synchronized void clear() {
        super.clear();
        size=0;
    }

    @Override
    public synchronized byte[] get(Object key) {
        return super.get(key);
    }
}
