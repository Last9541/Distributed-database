package internal.lsm.implementation;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(OrderAnnotation.class)
class LsmImplementationTest {

    @Test
    @Order(1)
    void initAcceptsBuiltInDefaults(@TempDir Path dataDir) throws IOException {
        runScenario("default-init", dataDir);
    }

    @Test
    @Order(2)
    void supportsBasicKvLifecycle(@TempDir Path dataDir) throws IOException {
        runScenario("basic-kv", dataDir);
    }

    @Test
    @Order(3)
    void recoversWalRecordsAfterRestart(@TempDir Path dataDir) throws IOException {
        runScenario("wal-write", dataDir);
        runScenario("wal-read", dataDir);
    }

    @Test
    @Order(4)
    void readsValuesFromSstableAfterMemtableFlushAndRestart(@TempDir Path dataDir) throws IOException {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            runScenario("sstable-write", dataDir);

            try (Stream<Path> sstFiles = Files.list(dataDir.resolve("sst"))) {
                assertTrue(sstFiles.anyMatch(path -> path.getFileName().toString().endsWith(".sst")));
            }

            try (Stream<Path> walFiles = Files.list(dataDir.resolve("wal"))) {
                for (Path walFile : walFiles.toList()) {
                    Files.deleteIfExists(walFile);
                }
            }

            runScenario("sstable-read", dataDir);
        });
    }

    @Test
    @Order(5)
    void recoversWalAfterTrailingGarbage(@TempDir Path dataDir) throws IOException {
        runScenario("wal-write", dataDir);

        Path lastWal = lastWal(dataDir);
        Files.write(lastWal, new byte[]{1, 2, 3, 4, 5}, StandardOpenOption.APPEND);

        runScenario("wal-read", dataDir);
    }

    @Test
    @Order(6)
    void recoversRecordsAcrossRolledWalSegments(@TempDir Path dataDir) throws IOException {
        runScenario("wal-roll-write", dataDir);

        try (Stream<Path> walFiles = Files.list(dataDir.resolve("wal"))) {
            long count = walFiles.filter(path -> path.getFileName().toString().endsWith(".wal")).count();
            assertTrue(count > 1, "expected more than one WAL segment after rolling");
        }

        runScenario("wal-roll-read", dataDir);
    }

    @Test
    @Order(7)
    void rejectsOversizedKeysAndValues(@TempDir Path dataDir) throws IOException {
        runScenario("oversized-inputs", dataDir);
    }

    @Test
    @Order(8)
    void newestValueWinsAcrossSstables(@TempDir Path dataDir) throws IOException {
        runScenario("sstable-overwrite-write", dataDir);
        deleteWalFiles(dataDir);

        runScenario("sstable-overwrite-read", dataDir);
    }

    @Test
    @Order(9)
    void newerSstableTombstoneHidesOlderValue(@TempDir Path dataDir) throws IOException {
        runScenario("sstable-tombstone-write", dataDir);
        deleteWalFiles(dataDir);

        runScenario("sstable-tombstone-read", dataDir);
    }

    @Test
    @Order(10)
    void compactionPublishesReplacementSstable(@TempDir Path dataDir) {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> runScenario("compaction-write", dataDir));
    }

    private static void runScenario(String scenario, Path dataDir) throws IOException {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                StorageScenarioMain.class.getName(),
                scenario,
                dataDir.toString()
        ).redirectErrorStream(true).start();

        String output = new String(process.getInputStream().readAllBytes());
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                fail("Scenario " + scenario + " failed with exit code " + exitCode + System.lineSeparator() + output);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for scenario " + scenario);
        }
    }

    private static Path lastWal(Path dataDir) throws IOException {
        try (Stream<Path> walFiles = Files.list(dataDir.resolve("wal"))) {
            return walFiles
                    .filter(path -> path.getFileName().toString().endsWith(".wal"))
                    .max(Comparator.naturalOrder())
                    .orElseThrow(() -> new AssertionError("No WAL file found"));
        }
    }

    private static void deleteWalFiles(Path dataDir) throws IOException {
        try (Stream<Path> walFiles = Files.list(dataDir.resolve("wal"))) {
            for (Path walFile : walFiles.toList()) {
                Files.deleteIfExists(walFile);
            }
        }
    }
}
