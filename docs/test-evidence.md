# Test Evidence

## Automated JUnit Suite

Command path used: manual JDK 21 compilation and JUnit Launcher because `mvn` is not available in PATH.

Result:

```text
25 tests found
25 tests started
25 tests successful
0 tests failed
```

Covered by the current tests:

- CLI parser quoting and repeated spaces.
- Config JSON loading.
- CLI `init`, `put`, `get`, `del`, `stats`, `close`.
- CLI restart reads from WAL/SST state.
- CLI `list-sst`, `flush-now`, `manifest-info`, `version-info`, `sst-info`.
- Engine scenarios for WAL recovery, WAL tail garbage truncation, WAL rolling, basic KV lifecycle, SSTable read after restart, newest-wins, tombstone hiding, oversized input rejection.

## Persistent SST Evidence

Generated directory:

```text
target/evidence-sst/
```

Observed files:

```text
target/evidence-sst/manifest.json
target/evidence-sst/sst/000001.sst
target/evidence-sst/wal/000002.wal
```

Manifest shows one live SST:

```text
id=1
file_name=000001.sst
min_seq_no=1
max_seq_no=2
entry_count=2
level=0
```

## Persistent Compaction Evidence

Generated directory:

```text
target/evidence-compaction/
```

Observed SST files:

```text
000001.sst
000002.sst
000003.sst
000004.sst
000005.sst
000006.sst
000007.sst
000008.sst
000009.sst
000010.sst
000011.sst
000012.sst
```

Observed result:

- The scenario produced many SSTables.
- `manifest.json` reached `epoch=12`.
- All manifest entries remained `level=0`.
- No compacted replacement SST was observed in the manifest.
- The long compaction evidence process did not terminate cleanly before timeout; it had to be stopped.

Conclusion:

```text
SSTable flush is visibly exercised and leaves disk artifacts.
Compaction was attempted under SST pressure, but no successful compaction publication was observed.
```

## Crash / Hard-Kill Evidence

Crash harness location:

```text
target/crash-runner/CrashHarnessMain.java
```

The harness starts a separate JVM, writes keys in a loop, records an ack only after `LsmImplementation.put(...)` returns, then the parent process kills the JVM with no `close()`. A second JVM restarts the store and verifies every acked key.

### WAL-only hard-kill test

Generated directory:

```text
target/crash-evidence/wal-only/
```

Result:

```text
killed_iteration=1 verified_ack_count=0
killed_iteration=2 verified_ack_count=0
killed_iteration=3 verified_ack_count=9
killed_iteration=4 verified_ack_count=31
killed_iteration=5 verified_ack_count=65
WAL_ONLY_CRASH_TEST_PASSED
```

Observed files:

```text
target/crash-evidence/wal-only/data/manifest.json
target/crash-evidence/wal-only/data/wal/000001.wal
target/crash-evidence/wal-only/acked.log
```

### Flush-pressure hard-kill test

Generated directory:

```text
target/crash-evidence/flush-pressure/
```

Result:

```text
killed_iteration=1 verified_ack_count=15
killed_iteration=2 verified_ack_count=20
killed_iteration=3 verified_ack_count=25
killed_iteration=4 verified_ack_count=30
killed_iteration=5 verified_ack_count=35
FLUSH_PRESSURE_CRASH_TEST_PASSED
```

Observed files include:

```text
target/crash-evidence/flush-pressure/data/manifest.json
target/crash-evidence/flush-pressure/data/sst/000001.sst
...
target/crash-evidence/flush-pressure/data/sst/000031.sst
target/crash-evidence/flush-pressure/data/wal/000030.wal
target/crash-evidence/flush-pressure/acked.log
```

Crash-safety conclusion from this run:

```text
The engine recovered all acked writes across repeated hard kills in WAL-only mode and under flush pressure.
This is positive evidence for WAL replay and SST/manifest restart behavior.
It is not a full proof of crash safety at every publish point.
```

Still not proven by this harness:

- crash exactly between manifest temp write and manifest rename;
- crash exactly after SST rename but before manifest publication;
- crash exactly during compaction publication;
- directory fsync correctness after atomic rename;
- disk-full and short-write failures;
- successful compaction publication.

## Publish-Point Simulations

Generated directory:

```text
target/crash-evidence/publish-points/
```

These tests manipulate files on disk after creating a valid store, then restart the engine and verify `k1=v1` and `k2=v2`.

### Leftover `manifest.json.tmp`

Simulation:

```text
copy manifest.json -> manifest.json.tmp
append broken bytes to manifest.json.tmp
restart and verify reads
```

Result:

```text
MANIFEST_TMP_LEFTOVER_PASSED
```

Conclusion:

```text
The engine ignores a stale/broken manifest.json.tmp when manifest.json is valid.
```

### Orphan `.sst.tmp`

Simulation:

```text
copy sst/000001.sst -> sst/999999.sst.tmp
restart and verify reads
```

Result:

```text
ORPHAN_SST_TMP_VERIFY_PASSED tmp_exists_after_restart=False
```

Conclusion:

```text
The engine deletes orphan .sst.tmp files on startup and keeps existing data readable.
```

### Orphan `.sst` After Rename Before Manifest Publish

Simulation:

```text
copy sst/000001.sst -> sst/999998.sst
do not add it to manifest.json
restart and verify reads
```

Result:

```text
ORPHAN_SST_AFTER_RENAME_VERIFY_PASSED orphan_exists_after_restart=False
```

Conclusion:

```text
The engine deletes SST files not referenced by the manifest and keeps existing data readable.
```

### Corrupt Current `manifest.json`

Simulation:

```text
replace manifest.json with invalid JSON
restart and verify reads
```

Result:

```text
CURRENT_MANIFEST_CORRUPT_VERIFY_EXIT=1
JsonParseException while loading manifest.json
```

Conclusion:

```text
The engine does not recover from a corrupted current manifest.json.
This is a negative crash-safety result if the active manifest can be torn or corrupted.
```

## Compaction Publish Probe

Generated directory:

```text
target/crash-evidence/compaction-probe/
```

Observed result:

```text
compaction probe timed out
manifest.json stayed at epoch=12
manifest kept 12 level=0 SSTables
no compacted replacement SST was published
```

Conclusion:

```text
Compaction publication is not proven.
Under the tested SST pressure, the process did not complete cleanly and the manifest did not show a compacted replacement.
```
