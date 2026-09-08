package internal.lsm.implementation.lru;

import internal.lsm.implementation.ByteArray;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;
import java.util.Map;

public class Lru extends LinkedHashMap<Long, LruValue>{


   protected int maxSize;



    public Lru(int maxSize) {
        super(16, 0.75f, true);
        this.maxSize=maxSize;
    }

    @Override
    protected synchronized boolean removeEldestEntry(Map.Entry<Long, LruValue> eldest) {
        boolean val=size()>maxSize;
        if(val) {
            eldest.getValue().getLock().lock();
            try {
                eldest.getValue().getFileChannel().close();
            } catch (IOException ignored) {

            }
            finally {
                eldest.getValue().getLock().unlock();
            }
        }
        return val;
    }

    @Override
    public synchronized LruValue put(Long key, LruValue value) {
        LruValue lruValue=super.put(key,value);
        if(lruValue!=null && lruValue!=value) {
            lruValue.getLock().lock();
            try {
                lruValue.getFileChannel().close();
            } catch (IOException ignored) {
            }
            finally {
                lruValue.getLock().unlock();
            }
        }
        return lruValue;
    }

    @Override
    public synchronized LruValue remove(Object key) {
        LruValue lruValue=super.remove(key);
        if(lruValue!=null) {
            lruValue.getLock().lock();
            try {
                lruValue.getFileChannel().close();
            } catch (IOException ignored) {
            }
            finally {
                lruValue.getLock().unlock();
            }
        }
        return lruValue;
    }

    @Override
    public synchronized void clear() {
        for(LruValue x:values()) {
            x.getLock().lock();
            try {
                x.getFileChannel().close();
            } catch (IOException ignored) {

            }
            finally {
                x.getLock().unlock();
            }
        }
        super.clear();
    }

    @Override
    public synchronized LruValue get(Object key) {
        LruValue lruValue = super.get(key);
        if(lruValue!=null)
            lruValue.getLock().lock();
        return lruValue;
    }
}
