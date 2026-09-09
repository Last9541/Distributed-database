package internal.lsm;

import java.util.*;

public class Manifest {


    private volatile int manifestVersion=1;

    private volatile int epoch=0;

    private Set<TableHandle> set=new TreeSet<>();

    private Set<TableHandle> setSize=new TreeSet<>((a,b)->{
        if(a.getFileSize()==b.getFileSize())
            return Long.compare(b.getId(),a.getId());
        return Long.compare(a.getFileSize(),b.getFileSize());
    });

    public Manifest()
    {

    }

    public synchronized Manifest add(TableHandle tableHandle)
    {
        set.add(tableHandle);
        setSize.add(tableHandle);
        epoch++;
        Manifest manifest=new Manifest();
        manifest.set.addAll(set);
        manifest.setSize.addAll(setSize);
        return manifest;
    }

    public List<TableHandle> getSetSize() {
        return new ArrayList<>(setSize);
    }

    public synchronized List<TableHandle> getSet() {
        return new ArrayList<>(set);
    }

    public synchronized void setSet(Set<TableHandle> set) {
        this.set = set;
    }

    public synchronized int getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(int manifestVersion) {
        this.manifestVersion = manifestVersion;
    }

    public synchronized int getEpoch() {
        return epoch;
    }

    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }
}
