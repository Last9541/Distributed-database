# LSMKV Engine API Contract

This document describes the behavior expected from `internal.lsm.Lsm`.

## Lifecycle

### `init(Config config)`

Initializes the store from the supplied config.

Required behavior:

- Create `data_dir` if it does not exist.
- Create `data_dir/wal` and `data_dir/sst` if they do not exist.
- Load `manifest.json` if present.
- Validate every SST listed in the manifest before serving reads.
- Replay WAL segments in numeric order.
- Truncate corrupted WAL tail records to the last valid offset.
- Set the next sequence number to `maxRecoveredSeqNo + 1`.
- Start background workers needed for flush and compaction.

Invalid cases:

- Calling `init` twice on the same store instance is invalid.
- Invalid config values should fail with `InvalidArgument`.
- IO failures should fail with `IOFailure` or a wrapped runtime failure.
- Corrupt persisted state should fail with `CorruptionDetected` or a wrapped runtime failure.

## Data Operations

### `put(byte[] key, byte[] value)`

Writes or replaces a value.

Required behavior:

- `key` must not be `null`.
- `key` must not be empty.
- `value` must not be `null`.
- Empty `value` is allowed.
- Oversized keys or values are rejected according to the active config limits.
- The mutation must be appended to the WAL before it is applied to the memtable.
- On success, a later `get(key)` must return this value unless a newer put/delete exists.
- If the key already exists, the newer value wins.

Expected errors:

- `InvalidArgument` for null, empty, or oversized input.
- `StoreClosed` after `close`.
- `IOFailure` or wrapped runtime failure for WAL/write failures.

### `get(byte[] key)`

Reads the latest visible value.

Lookup order:

1. Active memtable.
2. Immutable memtables, newest to oldest.
3. SSTables in version order, newest visible data first.

Required behavior:

- `key` must not be `null`.
- `key` must not be empty.
- If the latest record is a tombstone, the key is treated as missing.
- If no visible value exists, return `NotFound`.
- Reads must use a stable `Version` snapshot so flush/compaction cannot expose torn state.

Expected errors:

- `InvalidArgument` for null or empty key.
- `NotFound` when no visible value exists.
- `StoreClosed` after `close`.
- `CorruptionDetected` or wrapped runtime failure for invalid SST contents.

### `delete(byte[] key)`

Marks a key as deleted by writing a tombstone.

Required behavior:

- `key` must not be `null`.
- `key` must not be empty.
- Deleting a non-existent key is not an error.
- A delete must be appended to the WAL before it is applied to the memtable.
- After a successful delete, `get(key)` must return `NotFound` unless a newer put exists.
- Tombstones must hide older values in memtables and SSTables.

Expected errors:

- `InvalidArgument` for null, empty, or oversized key.
- `StoreClosed` after `close`.
- `IOFailure` or wrapped runtime failure for WAL/write failures.

## Shutdown

### `close()`

Finishes outstanding work and releases resources.

Required behavior:

- Wait for accepted writes/deletes to finish.
- Signal flush and compaction workers to stop.
- Wait for background workers to exit.
- Force and close the active WAL channel.
- Release cache/file resources.
- After `close`, every public operation should fail with `StoreClosed`.

Crash note:

- `close` is graceful shutdown behavior.
- Crash recovery must not depend on `close` having run.

## Inspection and Maintenance Operations

### `stats()`

Returns a human-readable summary of engine state.

Expected fields include:

- current epoch;
- last sequence number;
- active memtable entries and bytes;
- immutable memtable count and bytes;
- live SST count and total bytes;
- block cache hits and misses;
- Bloom filter checks and negatives;
- disk block reads.

### `flushNow()`

Attempts to flush one queued immutable memtable to an SSTable.

Required behavior:

- If no immutable memtable is queued, report that there is nothing to flush.
- If a flush succeeds, publish the new SST through manifest/version update.

### `listSst()`

Prints live SSTables from the current version.

Expected fields:

- `id`;
- `createdAt`;
- `fileSize`;
- `minSeqNo`;
- `maxSeqNo`;
- `minKey`;
- `maxKey`.

### `sstInfo(String fileName)`

Prints structural information for a live SSTable.

Required behavior:

- Only files referenced by the current version are valid targets.
- Validate block checksums.
- Report when the named SST does not exist in the current version.

### `manifestInfo()`

Prints the durable manifest state.

Expected fields:

- manifest epoch;
- live SST ids;
- SST sizes;
- min/max sequence numbers;
- min/max keys.

### `versionInfo()`

Prints the in-memory version state.

Expected fields:

- current epoch;
- immutable memtable sizes;
- live SST metadata.

## Durability Contract

Acknowledged writes:

- With `wal_fsync_every_n = 1`, every successful `put` or `delete` should survive process crash unless the underlying OS/storage violates fsync semantics.
- With `wal_fsync_every_n > 1`, only records that satisfy the configured sync policy are guaranteed.

Recovery:

- WAL replay must restore acknowledged records that were not flushed to an SSTable.
- Existing manifest-listed SSTables must be loaded on startup.
- Orphan `.sst.tmp` files may be deleted on startup.
- Orphan `.sst` files not referenced by the manifest may be deleted on startup.
- A leftover `manifest.json.tmp` must not override a valid `manifest.json`.

Corruption:

- Corrupted WAL tail records should be truncated to the last good record.
- Corrupted SST contents should fail with `CorruptionDetected`.
- A corrupted current `manifest.json` is a fatal startup error.

## Concurrency Contract

- Writes are serialized.
- Reads may run concurrently with background flush/compaction.
- Readers must observe a stable version snapshot.
- Background publication must use new immutable `Version` instances instead of mutating the visible version in place.

## Error Names

Standard error names:

- `InvalidArgument`
- `StoreClosed`
- `IOFailure`
- `CorruptionDetected`
- `NotFound`
- `NotImplemented`

These names should be used consistently in API behavior, CLI output, and tests.
