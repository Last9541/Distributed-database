package cmd.lsmkv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MainCliTest {

    @Test
    void initThenCloseExits(@TempDir Path tempDir) throws IOException {
        CliResult result = runCli(tempDir, "lsmkv init --config " + config(tempDir, "cli-init.json", "data-init"), "lsmkv close");

        assertEquals(0, result.exitCode(), result.output());
    }

    @Test
    void initAcceptsQuotedConfigPathWithSpaces(@TempDir Path tempDir) throws IOException {
        Path config = config(tempDir, "cli config with spaces.json", "data-spaces");

        CliResult result = runCli(tempDir, "lsmkv init --config \"" + config + "\"", "lsmkv close");

        assertEquals(0, result.exitCode(), result.output());
    }

    @Test
    void putThenCloseExits(@TempDir Path tempDir) throws IOException {
        CliResult result = runCli(
                tempDir,
                "lsmkv init --config " + config(tempDir, "cli-put.json", "data-put"),
                "lsmkv put --key alpha --value beta",
                "lsmkv close"
        );

        assertEquals(0, result.exitCode(), result.output());
    }

    @Test
    void getMissingPrintsErrorAndStillAllowsClose(@TempDir Path tempDir) throws IOException {
        CliResult result = runCli(
                tempDir,
                "lsmkv init --config " + config(tempDir, "cli-missing.json", "data-missing"),
                "lsmkv get --key missing",
                "lsmkv close"
        );

        assertEquals(0, result.exitCode(), result.output());
    }

    @Test
    void statsPrintsEngineCounters(@TempDir Path tempDir) throws IOException {
        CliResult result = runCli(
                tempDir,
                "lsmkv init --config " + config(tempDir, "cli-stats.json", "data-stats"),
                "lsmkv stats",
                "lsmkv close"
        );

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("epoch="), result.output());
        assertTrue(result.output().contains("last_seqno="), result.output());
        assertTrue(result.output().contains("active_entries="), result.output());
        assertTrue(result.output().contains("sst_live="), result.output());
    }

    @Test
    void listSstPrintsFlushedTableInfo(@TempDir Path tempDir) throws IOException {
        Path config = config(tempDir, "cli-list-sst.json", "data-list-sst", 90);
        CliResult writeResult = runCli(
                tempDir,
                "lsmkv init --config " + config,
                "lsmkv put --key k1 --value v1",
                "lsmkv put --key k2 --value v2",
                "lsmkv close"
        );
        assertEquals(0, writeResult.exitCode(), writeResult.output());

        CliResult result = runCli(
                tempDir,
                "lsmkv init --config " + config,
                "lsmkv list-sst",
                "lsmkv close"
        );

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("id="), result.output());
        assertTrue(result.output().contains("fileSize="), result.output());
        assertTrue(result.output().contains("minKey="), result.output());
        assertTrue(result.output().contains("maxKey="), result.output());
    }

    @Test
    void getReadsValueAfterCliRestart(@TempDir Path tempDir) throws IOException {
        Path config = config(tempDir, "cli-get.json", "data-get");
        CliResult writeResult = runCli(
                tempDir,
                "lsmkv init --config " + config,
                "lsmkv put --key alpha --value beta",
                "lsmkv close"
        );
        assertEquals(0, writeResult.exitCode(), writeResult.output());

        CliResult result = runCli(
                tempDir,
                "lsmkv init --config " + config,
                "lsmkv get --key alpha",
                "lsmkv close"
        );

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("[98, 101, 116, 97]"), result.output());
    }

    @Test
    void deleteHidesValueAfterCliRestart(@TempDir Path tempDir) throws IOException {
        Path config = config(tempDir, "cli-delete.json", "data-delete");
        CliResult writeResult = runCli(
                tempDir,
                "lsmkv init --config " + config,
                "lsmkv put --key alpha --value beta",
                "lsmkv del --key alpha",
                "lsmkv close"
        );
        assertEquals(0, writeResult.exitCode(), writeResult.output());

        CliResult result = runCli(
                tempDir,
                "lsmkv init --config " + config,
                "lsmkv get --key alpha",
                "lsmkv close"
        );

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("null"), result.output());
        assertTrue(!result.output().contains("[98, 101, 116, 97]"), result.output());
    }

    @Test
    void inspectionCommandsPrintExistingEngineState(@TempDir Path tempDir) throws IOException {
        Path config = config(tempDir, "cli-inspect.json", "data-inspect", 90);
        CliResult writeResult = runCli(
                tempDir,
                "lsmkv init --config " + config,
                "lsmkv put --key k1 --value v1",
                "lsmkv put --key k2 --value v2",
                "lsmkv close"
        );
        assertEquals(0, writeResult.exitCode(), writeResult.output());

        CliResult result = runCli(
                tempDir,
                "lsmkv init --config " + config,
                "lsmkv flush-now",
                "lsmkv manifest-info",
                "lsmkv version-info",
                "lsmkv sst-info --filename 000001.sst",
                "lsmkv close"
        );

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("epoch:"), result.output());
        assertTrue(result.output().contains("Checksum OK"), result.output());
    }

    private static CliResult runCli(Path workingDir, String... lines) throws IOException {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                absoluteClasspath(),
                Main.class.getName()
        ).directory(workingDir.toFile()).redirectErrorStream(true).start();

        String input = String.join(System.lineSeparator(), lines) + System.lineSeparator();
        process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();

        try {
            boolean finished = process.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                fail("CLI did not exit within timeout. Output:" + System.lineSeparator() + output);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CliResult(process.exitValue(), output);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for CLI", e);
        }
    }

    private static Path config(Path tempDir, String fileName, String dataDirName) throws IOException {
        return config(tempDir, fileName, dataDirName, 20_971_520);
    }

    private static Path config(Path tempDir, String fileName, String dataDirName, int memtableMaxBytes) throws IOException {
        Path dataDir = tempDir.resolve(dataDirName);
        Path config = tempDir.resolve(fileName);
        String dataDirValue = dataDir.toString().replace("\\", "/");
        String json = """
                {
                  "data_dir": "%s",
                  "memtable_max_bytes": %d,
                  "block_size": 8192,
                  "bloom_false_positive": 0.01,
                  "wal_fsync_every_n": 1,
                  "compression": "off",
                  "log_level": "info",
                  "roll_size": 20971520,
                  "max_immutable_tables": 4,
                  "refresh_n": 10,
                  "block_cache_mb": 64,
                  "cache_index_blocks": false,
                  "max_open_files": 1024,
                  "size_tiered_fan_in": 4,
                  "size_tiered_size_ratio": 2.0,
                  "compaction_max_concurrent": 1,
                  "compaction_io_mb_per_s": 0,
                  "l0_compaction_trigger": 8,
                  "l0_stop_writes": 20
                }
                """.formatted(dataDirValue, memtableMaxBytes);
        Files.writeString(config, json, StandardCharsets.UTF_8);
        return config;
    }

    private static String absoluteClasspath() {
        Path base = Path.of(System.getProperty("user.dir"));
        String[] entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
        for (int i = 0; i < entries.length; i++) {
            Path entry = Path.of(entries[i]);
            entries[i] = (entry.isAbsolute() ? entry : base.resolve(entry)).normalize().toString();
        }
        return String.join(java.io.File.pathSeparator, entries);
    }

    private record CliResult(int exitCode, String output) {
    }
}
