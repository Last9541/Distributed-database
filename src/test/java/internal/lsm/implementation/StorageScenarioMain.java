package internal.lsm.implementation;

import cmd.lsmkv.Main;
import internal.lsm.Config;
import internal.lsm.errors.InvalidArgument;
import internal.lsm.errors.NotFound;
import internal.lsm.errors.StoreClosed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class StorageScenarioMain {

    private StorageScenarioMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <scenario> <dataDir>");
        }

        Path dataDir = Path.of(args[1]);
        switch (args[0]) {
            case "default-init" -> defaultInit(dataDir);
            case "basic-kv" -> basicKv(dataDir);
            case "wal-write" -> walWrite(dataDir);
            case "wal-read" -> walRead(dataDir);
            case "wal-roll-write" -> walRollWrite(dataDir);
            case "wal-roll-read" -> walRollRead(dataDir);
            case "sstable-write" -> sstableWrite(dataDir);
            case "sstable-read" -> sstableRead(dataDir);
            case "sstable-overwrite-write" -> sstableOverwriteWrite(dataDir);
            case "sstable-overwrite-read" -> sstableOverwriteRead(dataDir);
            case "sstable-tombstone-write" -> sstableTombstoneWrite(dataDir);
            case "sstable-tombstone-read" -> sstableTombstoneRead(dataDir);
            case "compaction-write" -> compactionWrite(dataDir);
            case "oversized-inputs" -> oversizedInputs(dataDir);
            default -> throw new IllegalArgumentException("Unknown scenario: " + args[0]);
        }
    }

    private static void defaultInit(Path dataDir) throws IOException {
        Config config = new Config();
        config.setDataDir(dataDir.toString());

        LsmImplementation store = new LsmImplementation(config);
        store.close();
    }

    private static void basicKv(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(testConfig(dataDir));

        try {
            check(Files.isDirectory(dataDir.resolve("wal")), "wal directory missing");
            check(Files.isDirectory(dataDir.resolve("sst")), "sst directory missing");

            store.put(bytes("name"), bytes("first"));
            store.put(bytes("name"), bytes("second"));
            checkArray(bytes("second"), store.get(bytes("name")), "latest value not returned");

            store.put(bytes("empty-value"), new byte[0]);
            checkArray(new byte[0], store.get(bytes("empty-value")), "empty value not returned");

            expect(NotFound.class, () -> store.get(bytes("missing")), "missing key did not throw NotFound");

            store.put(bytes("deleted"), bytes("visible"));
            store.delete(bytes("deleted"));
            expect(NotFound.class, () -> store.get(bytes("deleted")), "deleted key did not throw NotFound");
            store.delete(bytes("never-written"));

            expect(InvalidArgument.class, () -> store.put(null, bytes("value")), "null put key accepted");
            expect(InvalidArgument.class, () -> store.put(new byte[0], bytes("value")), "empty put key accepted");
            expect(InvalidArgument.class, () -> store.get(null), "null get key accepted");
            expect(InvalidArgument.class, () -> store.get(new byte[0]), "empty get key accepted");
            expect(InvalidArgument.class, () -> store.delete(null), "null delete key accepted");
            expect(InvalidArgument.class, () -> store.delete(new byte[0]), "empty delete key accepted");

            String stats = store.stats();
            check(stats.contains("epoch="), "stats missing epoch");
            check(stats.contains("last_seqno="), "stats missing last_seqno");
            check(stats.contains("active_entries="), "stats missing active_entries");
            check(stats.contains("sst_live="), "stats missing sst_live");
        } finally {
            store.close();
        }

        expect(StoreClosed.class, () -> store.get(bytes("name")), "get after close did not throw StoreClosed");
        expect(StoreClosed.class, () -> store.put(bytes("name"), bytes("third")), "put after close did not throw StoreClosed");
        expect(StoreClosed.class, () -> store.delete(bytes("name")), "delete after close did not throw StoreClosed");
        expect(StoreClosed.class, store::stats, "stats after close did not throw StoreClosed");
    }

    private static void walWrite(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(testConfig(dataDir));

        store.put(bytes("survives"), bytes("restart"));
        store.put(bytes("deleted-after-restart"), bytes("old"));
        store.delete(bytes("deleted-after-restart"));
        store.close();
    }

    private static void walRead(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(testConfig(dataDir));
        try {
            checkArray(bytes("restart"), store.get(bytes("survives")), "WAL value was not recovered");
            expect(NotFound.class, () -> store.get(bytes("deleted-after-restart")), "WAL tombstone was not recovered");
        } finally {
            store.close();
        }
    }

    private static void sstableWrite(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(flushConfig(dataDir));

        store.put(bytes("k1"), bytes("v1"));
        store.put(bytes("k2"), bytes("v2"));
        store.close();
    }

    private static void sstableRead(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(flushConfig(dataDir));
        try {
            checkArray(bytes("v1"), store.get(bytes("k1")), "SSTable value k1 was not recovered");
            checkArray(bytes("v2"), store.get(bytes("k2")), "SSTable value k2 was not recovered");
        } finally {
            store.close();
        }
    }

    private static void walRollWrite(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(rollConfig(dataDir));

        for (int i = 0; i < 10; i++) {
            store.put(bytes("k" + i), bytes("v" + i));
        }
        store.close();
    }

    private static void walRollRead(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(rollConfig(dataDir));
        try {
            for (int i = 0; i < 10; i++) {
                checkArray(bytes("v" + i), store.get(bytes("k" + i)), "rolled WAL value k" + i + " was not recovered");
            }
        } finally {
            store.close();
        }
    }

    private static void oversizedInputs(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(smallLimitConfig(dataDir));
        try {
            expect(InvalidArgument.class, () -> store.put(bytes("12345678901"), bytes("value")), "oversized key accepted");
            expect(InvalidArgument.class, () -> store.delete(bytes("12345678901")), "oversized delete key accepted");
            expect(InvalidArgument.class, () -> store.put(bytes("ok"), bytes("123456789012345678901")), "oversized value accepted");
        } finally {
            store.close();
        }
    }

    private static void sstableOverwriteWrite(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(flushConfig(dataDir));

        store.put(bytes("dup"), bytes("old"));
        store.put(bytes("trigger1"), bytes("x"));
        store.put(bytes("dup"), bytes("new"));
        store.put(bytes("trigger2"), bytes("x"));
        store.close();
    }

    private static void sstableOverwriteRead(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(flushConfig(dataDir));
        try {
            checkArray(bytes("new"), store.get(bytes("dup")), "newer SSTable value did not win");
        } finally {
            store.close();
        }
    }

    private static void sstableTombstoneWrite(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(flushConfig(dataDir));

        store.put(bytes("gone"), bytes("old"));
        store.put(bytes("trigger1"), bytes("x"));
        store.delete(bytes("gone"));
        store.put(bytes("trigger2"), bytes("x"));
        store.close();
    }

    private static void sstableTombstoneRead(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation(flushConfig(dataDir));
        try {
            expect(NotFound.class, () -> store.get(bytes("gone")), "newer SSTable tombstone did not hide older value");
        } finally {
            store.close();
        }
    }

    private static void compactionWrite(Path dataDir) throws IOException {
        LsmImplementation store = new LsmImplementation();
        Main.compaction = new Compaction(store);
        store.init(compactionConfig(dataDir));

        try {
            for (int i = 0; i < 16; i++) {
                store.put(bytes("k" + i), bytes("v" + i));
            }
            for (int i = 0; i < 16; i++) {
                checkArray(bytes("v" + i), store.get(bytes("k" + i)), "value missing before close k" + i);
            }
        } finally {
            store.close();
        }

        String manifest = Files.readString(dataDir.resolve("manifest.json"), StandardCharsets.UTF_8);
        check(manifest.contains("\"level\" : 1"), "compaction did not publish a level-1 SSTable:" + System.lineSeparator() + manifest);
    }

    private static Config testConfig(Path dataDir) {
        Config config = new Config();
        config.setDataDir(dataDir.toString());
        config.setMemtableMaxBytes(20 * 1024 * 1024);
        config.setBlockSize(20 * 1024 * 1024);
        config.setRollSize(20 * 1024 * 1024);
        config.setMaxImmutableTables(4);
        config.setWalFsyncEveryN(1);
        config.setBloomFalsePositive(0.01);
        config.setCacheIndexBlocks(false);
        return config;
    }

    private static Config flushConfig(Path dataDir) {
        Config config = testConfig(dataDir);
        config.setBlockSize(8192);
        config.setMemtableMaxBytes(90);
        config.setRollSize(8192);
        return config;
    }

    private static Config compactionConfig(Path dataDir) {
        return flushConfig(dataDir);
    }

    private static Config rollConfig(Path dataDir) {
        Config config = testConfig(dataDir);
        config.setRollSize(64);
        return config;
    }

    private static Config smallLimitConfig(Path dataDir) {
        Config config = testConfig(dataDir);
        config.setBlockSize(80);
        config.setMemtableMaxBytes(20 * 1024 * 1024);
        config.setRollSize(20 * 1024 * 1024);
        return config;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkArray(byte[] expected, byte[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + Arrays.toString(expected) + " actual=" + Arrays.toString(actual));
        }
    }

    private static <T extends Throwable> void expect(Class<T> type, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (type.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": got " + throwable.getClass().getName(), throwable);
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
