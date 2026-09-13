# Missing Features by Priority

This is a feature gap list against `Big data Spec (1).docx`. It intentionally does not list bugs or test failures.

## P0 - Project Contract and Operator Basics

- `README.md` with one-command build/run instructions.
- `internal/lsm/api.md` describing `Put`, `Get`, `Delete`, and `Close` behavior in plain English.
- `internal/lsm/errors.md` documenting standard error names and when each is used.
- Config layering beyond config file defaults:
  - environment overrides such as `LSMKV_*`;
  - CLI flag overrides;
  - warning for unknown config keys.
- CLI success output contract for writes:
  - `put` should print success and assigned `seqNo`;
  - `del` should print success and assigned `seqNo`.
- Clear CLI output for `NotFound` and other typed errors instead of relying on exception messages.

## P1 - WAL Tooling and Recovery UX

- CLI command `wal-verify` that scans WAL segments and prints the Recovery Report.
- CLI command `wal-truncate --segment ... --offset ...` or equivalent guarded manual repair tool.
- Startup Recovery Report with full required shape:
  - `segments`;
  - `records`;
  - `truncated`;
  - `last_seqno`;
  - `status=OK`.
- WAL stats in `stats`:
  - active segment id;
  - bytes written in active segment;
  - total WAL segments;
  - fsync policy.
- Documented WAL roll policy in README or docs.

## P2 - Background Control and Shutdown Features

- CLI command `bg-status` for worker states, queue lengths, and last job summaries.
- CLI command `compaction-run` to run one compaction job on demand.
- CLI commands `compaction-pause` and `compaction-resume`.
- Runtime tuning command such as `set --compaction-io-mb 20`.
- Explicit shutdown modes:
  - graceful shutdown;
  - fast shutdown;
  - configurable `shutdown_timeout_ms`.
- Config key `bg_tick_ms`.
- Enforcement/reporting for `l0_stop_writes` as a visible stop-write threshold.

## P3 - Observability

- `docs/observability.md` with the observability charter.
- Prometheus-style metrics catalogue and implementation, including:
  - write/WAL counters;
  - memtable gauges;
  - flush counters and timings;
  - read/cache/Bloom counters;
  - compaction counters and timings;
  - version/epoch health metrics;
  - write stall and error counters.
- Structured logs with stable event names:
  - `rotate_memtable`;
  - `flush_start`;
  - `flush_publish`;
  - `compaction_start`;
  - `compaction_done`;
  - `version_publish`;
  - `stall_write`;
  - `wal_truncate_tail`;
  - `corruption_detected`.
- HTTP endpoints:
  - `GET /healthz`;
  - `GET /readyz`;
  - `GET /metrics`;
  - `GET /stats`.
- `doctor --bundle out.zip` support bundle command.
- Observability config:
  - `metrics_enabled`;
  - `http_listen_addr`;
  - `stats_sampling_interval_ms`;
  - `doctor_bundle_max_mb`.

## P4 - Testing and Fault Injection Infrastructure

- `TESTING.md` with suite descriptions and run instructions.
- Property/model-based tests using an oracle map.
- Randomized/fuzz tests with printed seeds.
- Fault injection hooks for named crash points:
  - `CP_WAL_AFTER_APPEND_BEFORE_FSYNC`;
  - `CP_WAL_AFTER_FSYNC_BEFORE_ACK`;
  - `CP_ROTATE_BEFORE_ENQUEUE_IMMUTABLE`;
  - `CP_FLUSH_AFTER_DATABLOCKS_BEFORE_INDEX`;
  - `CP_FLUSH_AFTER_INDEX_BEFORE_RENAME`;
  - `CP_FLUSH_AFTER_RENAME_BEFORE_MANIFEST`;
  - `CP_MANIFEST_AFTER_WRITE_BEFORE_RENAME`;
  - `CP_COMPACTION_AFTER_OUTPUTS_BEFORE_MANIFEST`.
- Scripted chaos drills:
  - power cut during write;
  - crash during flush;
  - crash during compaction;
  - corrupted WAL tail;
  - disk full simulation.
- CI script outline that runs unit, property, and fault suites and preserves failure artifacts.
- Coverage target/reporting for storage code.

## P5 - Optional or Later Scope

- Compression implementations for `snappy` and `lz4`; current config has the knob but the feature is not implemented.
- HTTP/JSON stats output variant.
- Full Dynamo-style Phase B:
  - consistent hashing ring;
  - replication factor;
  - read/write quorums;
  - hinted handoff;
  - anti-entropy repair;
  - conflict resolution/version vectors or equivalent.
- Stretch LSM features:
  - leveled compaction;
  - prefix Bloom filters;
  - range iterators;
  - prefix seeks;
  - TTL/compaction filters;
  - whole-file digests.
