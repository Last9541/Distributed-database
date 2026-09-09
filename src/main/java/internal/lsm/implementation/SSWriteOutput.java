package internal.lsm.implementation;

import internal.lsm.Manifest;
import internal.lsm.TableHandle;

public class SSWriteOutput {
    private Manifest manifest;
    private TableHandle tableHandle;

    public SSWriteOutput(Manifest manifest, TableHandle tableHandle) {
        this.manifest = manifest;
        this.tableHandle = tableHandle;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public TableHandle getTableHandle() {
        return tableHandle;
    }
}
