package internal.lsm.implementation;

import internal.lsm.Global;
import internal.lsm.TableHandle;
import internal.lsm.errors.CorruptionDetected;
import internal.lsm.errors.InvalidArgument;
import internal.lsm.errors.NotFound;
import internal.lsm.implementation.lru.Lru;
import internal.lsm.implementation.lru.LruSizeKey;
import internal.lsm.implementation.lru.LruValue;
import internal.lsm.implementation.lru.LruWithSize;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Compaction {

    private SSTable lsmImplementation;


    public Compaction(SSTable lsmImplementation) {
        this.lsmImplementation = lsmImplementation;
    }

    protected BlockingDeque<CWElement> compactionQueue=new LinkedBlockingDeque<>();


    public boolean group(int start,int end,List<TableHandle> tableHandles)
    {
        boolean proceed=true;
        int i=start;
        try {
            for (; i <= end; i++) {
                TableHandle tableHandle = tableHandles.get(i);
                if (tableHandle.isCompacted() || !tableHandle.getCompaction().compareAndSet(false, true)) {
                    proceed = false;
                    break;
                }
            }
            if (proceed) {
                try {
                    lsmImplementation.ssTableCompact(tableHandles, start, end);
                    return true;
                } catch (InvalidArgument ignored) {

                }
            }
        }
        finally {
            for (int j = start; j < i; j++) {
                TableHandle tableHandle = tableHandles.get(j);
                tableHandle.getCompaction().set(false);
            }
        }
        return  false;
    }


   public void loop() throws InterruptedException {
       while (true)
       {

           CWElement cwElement=compactionQueue.take();
           if(cwElement.getStart()==-1)
               break;
           try
           {
               group(cwElement.getStart(),cwElement.getEnd(),cwElement.getList());
           }
           finally {
               cwElement.getVersion().decrement();
           }
       }
   }



    public void picker() throws InterruptedException {
        if (lsmImplementation.config.getSizeTieredFanIn() <= 1)
            throw new InvalidArgument("Ne sme da bude <=1 sizeTieredFanIn");
        Version current = lsmImplementation.acquireVersion();
        try {
            boolean b = true;
            for (int i = 0, k = i + lsmImplementation.config.getSizeTieredFanIn() - 1; k < current.getTableHandlesBySize().size(); i++, k++) {
                long val = current.getTableHandlesBySize().get(k).getFileSize() / current.getTableHandlesBySize().get(i).getFileSize();
                if (current.getTableHandlesBySize().get(k).getFileSize() % current.getTableHandlesBySize().get(i).getFileSize() != 0)
                    val++;
                if (val > 2)
                    continue;
                b = false;
                compactionQueue.put(new CWElement(current, i, k, current.getTableHandlesBySize()));
                break;
//            if (group(current, i, k, current.getTableHandlesBySize()))
//                return;
            }
            if (b && lsmImplementation.config.getSizeTieredFanIn() - 1 < current.getTableHandlesByLevel().size()) {
                compactionQueue.put(new CWElement(current, 0, lsmImplementation.config.getSizeTieredFanIn() - 1, current.getTableHandlesByLevel()));
                //group(current, 0, lsmImplementation.config.getSizeTieredFanIn() - 1, current.getTableHandlesByLevel());;
                b=false;
            }
            if(b)
                current.decrement();
        }
        catch (Exception e)
        {
            current.decrement();
            throw e;
        }

    }
}
