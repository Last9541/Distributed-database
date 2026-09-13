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
