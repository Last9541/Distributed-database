package internal.lsm.implementation.lru;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.ReentrantLock;

public class LruValue {
    private FileChannel fileChannel;

    private int refCount=1;
    public LruValue(FileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public void setFileChannel(FileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }


    public synchronized void increment()
    {
        refCount++;
    }
    public synchronized void decrement()
    {
        if (refCount <= 0)
            throw new IllegalStateException("Invalid refCount");
        refCount--;
        if(refCount==0) {
            try {
                fileChannel.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
