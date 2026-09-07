package internal.lsm.implementation.lru;

import internal.lsm.implementation.ByteArray;

import java.util.LinkedHashMap;
import java.util.Map;

public class Lru<K,V> extends LinkedHashMap<K,V>{


   protected int maxSize;

    public Lru(int maxSize) {
        super(16, 0.75f, true);
        this.maxSize=maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size()>maxSize;
    }
}
