package internal.lsm.implementation.lru;

import java.nio.channels.FileChannel;
import java.util.concurrent.locks.ReentrantLock;

public class LruValue {
    private FileChannel fileChannel;
    private ReentrantLock lock=new ReentrantLock();

    public LruValue(FileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public void setFileChannel(FileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public void setLock(ReentrantLock lock) {
        this.lock = lock;
    }
}
