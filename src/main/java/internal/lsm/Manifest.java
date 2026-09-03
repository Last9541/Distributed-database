package internal.lsm;

import java.util.ArrayList;
import java.util.List;

public class Manifest {


    private List<TableHandle> list=new ArrayList<>();

    public Manifest(List<TableHandle> list) {
        this.list = list;
    }
    public Manifest()
    {

    }

    public synchronized void add(TableHandle tableHandle)
    {
        list.add(tableHandle);
    }

    public List<TableHandle> getList() {
        return list;
    }

    public void setList(List<TableHandle> list) {
        this.list = list;
    }
}
