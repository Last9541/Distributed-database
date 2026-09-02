package internal.lsm;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Manifest {


    private List<ManifestEntry> list=new ArrayList<>();

    public Manifest(List<ManifestEntry> list) {
        this.list = list;
    }
    public Manifest()
    {

    }

    public synchronized void add(ManifestEntry manifestEntry)
    {
        list.add(manifestEntry);
    }

    public List<ManifestEntry> getList() {
        return list;
    }

    public void setList(List<ManifestEntry> list) {
        this.list = list;
    }
}
