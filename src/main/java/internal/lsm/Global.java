package internal.lsm;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

public class Global {

    public static final Object versionLock = new Object();

    public static final Object manifestLock=new Object();

    public static final Logger logger=Logger.getLogger("LSM");


    public static int compareTo(byte[] arr1,byte[] arr2) {
        int len=Math.min(arr1.length,arr2.length);
        for(int i=0;i<len;i++)
        {
            if(arr1[i]!=arr2[i])
                return Byte.compareUnsigned(arr1[i], arr2[i]);
        }
        return arr1.length-arr2.length;
    }


}
