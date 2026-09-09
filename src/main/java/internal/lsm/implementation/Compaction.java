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

public class Compaction {

    private SSTable lsmImplementation;


    public Compaction(SSTable lsmImplementation) {
        this.lsmImplementation = lsmImplementation;
    }




    public boolean group(Version current,int start,int end)
    {
        boolean proceed=true;
        int i=start;
        try {
            for (; i <= end; i++) {
                TableHandle tableHandle = current.getTableHandlesBySize().get(i);
                if (!tableHandle.getCompaction().compareAndSet(false, true)) {
                    proceed = false;
                    break;
                }
            }
            if (proceed) {
                try {
                    lsmImplementation.ssTableCompact(current, start, end);
                    return true;
                } catch (InvalidArgument ignored) {

                }
            }
        }
        finally {
            for (int j = start; j < i; j++) {
                TableHandle tableHandle = current.getTableHandlesBySize().get(j);
                tableHandle.getCompaction().set(false);
            }
        }
        return  false;
    }

    public void picker()
    {
        if(lsmImplementation.config.getSizeTieredFanIn()<=1)
            throw new InvalidArgument("Ne sme da bude <=1 sizeTieredFanIn");
        Version current=lsmImplementation.version;
        boolean b=true;
        for(int i=0,k=i+lsmImplementation.config.getSizeTieredFanIn()-1;k<current.getTableHandlesBySize().size();i++,k++)
        {
            long val=current.getTableHandlesBySize().get(k).getFileSize()/current.getTableHandlesBySize().get(i).getFileSize();
            if(current.getTableHandlesBySize().get(k).getFileSize()%current.getTableHandlesBySize().get(i).getFileSize()!=0)
                val++;
            if(val>2)
                continue;
            b=false;
            if(group(current,i,k))
                return;
        }
        if(b && lsmImplementation.config.getSizeTieredFanIn()-1 < current.getTableHandlesBySize().size())
            group(current,0,lsmImplementation.config.getSizeTieredFanIn()-1);
    }
}
