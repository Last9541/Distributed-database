package internal.lsm;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

public class Manifest {


    private volatile int manifestVersion=1;

    private volatile int epoch=0;

    private Set<TableHandle> set=new TreeSet<>();

    @JsonIgnore
    private Set<TableHandle> setSize=new TreeSet<>((a,b)->{
        if(a.getFileSize()==b.getFileSize())
            return Long.compare(b.getId(),a.getId());
        return Long.compare(a.getFileSize(),b.getFileSize());
    });

    @JsonIgnore
    private Set<TableHandle> setLevel=new TreeSet<>((a,b)->{
        if(a.getLevel()==b.getLevel())
            return Long.compare(b.getId(),a.getId());
        return Long.compare(a.getLevel(),b.getLevel());
    });

    public Manifest()
    {

    }

    public synchronized Manifest add(TableHandle tableHandle)
    {
        set.add(tableHandle);
        setSize.add(tableHandle);
        setLevel.add(tableHandle);
        epoch++;
        Manifest manifest=new Manifest();
        manifest.set.addAll(set);
        manifest.setSize.addAll(setSize);
        manifest.setLevel.addAll(setLevel);
        manifest.setEpoch(epoch);
        return manifest;
    }
    public synchronized Manifest addAndRemove(TableHandle tableHandle,List<TableHandle>remove)
    {
        set.add(tableHandle);
        setSize.add(tableHandle);
        int max=0;
        for(TableHandle x:remove)
        {
            if(x.getLevel()>max)
                    max=x.getLevel();
            set.remove(x);
            setSize.remove(x);
            setLevel.remove(x);
        }
        if(max<Integer.MAX_VALUE)
            max++;
        tableHandle.setLevel(max);
        setLevel.add(tableHandle);
        epoch++;
        Manifest manifest=new Manifest();
        manifest.set.addAll(set);
        manifest.setSize.addAll(setSize);
        manifest.setLevel.addAll(setLevel);
        manifest.setEpoch(epoch);
        return manifest;
    }

    @JsonIgnore
    public List<TableHandle> getSetSize() {
        return new ArrayList<>(setSize);
    }


    @JsonIgnore
    public List<TableHandle> getSetLevel() {
        return new ArrayList<>(setLevel);
    }

    public synchronized List<TableHandle> getSet() {
        return new ArrayList<>(set);
    }

    //todo koristi samo jackson pa je okej, inace bi morao clear za setLevel i setSize a to ne zelim svakako tkd bi moralo da se menja
    public synchronized void setSet(Set<TableHandle> set) {
        this.set.addAll(set);
        setLevel.addAll(set);
        setSize.addAll(set);
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
