package internal.lsm;

import java.util.Set;
import java.util.TreeSet;

public class Manifest {


    private Set<TableHandle> set=new TreeSet<>((a,b)->b.getFileName().compareTo(a.getFileName()));

    public Manifest()
    {

    }

    public synchronized void add(TableHandle tableHandle)
    {
        set.add(tableHandle);
    }

    public Set<TableHandle> getSet() {
        return set;
    }


}
