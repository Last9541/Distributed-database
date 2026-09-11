package internal.lsm.implementation;

import internal.lsm.TableHandle;

import java.util.List;

public class CWElement  {
    private Version version;
    private List<TableHandle> list;
    private int start;
    private int end;


    public CWElement(Version version, int start, int end, List<TableHandle> list) {
        this.version = version;
        this.list = list;
        this.start=start;
        this.end=end;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public List<TableHandle> getList() {
        return list;
    }

    public void setList(List<TableHandle> list) {
        this.list = list;
    }



}
