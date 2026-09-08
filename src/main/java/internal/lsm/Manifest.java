package internal.lsm;

import java.util.Set;
import java.util.TreeSet;

public class Manifest {


    private int manifestVersion=1;

    private int epoch=0;

    private Set<TableHandle> set=new TreeSet<>();

    public Manifest()
    {

    }

    public synchronized void add(TableHandle tableHandle)
    {
        set.add(tableHandle);
        epoch++;
    }

    public synchronized Set<TableHandle> getSet() {
        return new TreeSet<>(set);
    }

    public synchronized void setSet(Set<TableHandle> set) {
        this.set = set;
    }
}
