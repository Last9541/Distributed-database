package internal.lsm;

import cmd.lsmkv.Main;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void setBloomFalsePositiveComputesExpectedBloomParameters() {
        Config config = new Config();

        config.setBloomFalsePositive(0.01);

        assertEquals(10, config.getBloomFilterSizePerKey());
        assertEquals(7, config.getBloomHashingFunctionNumber());
    }

    @Test
    void defaultConfigFileLoadsSnakeCaseFields() throws IOException {
        Config config = Main.mapper.readValue(Path.of("config/default.json").toFile(), Config.class);

        assertEquals("./data", config.getDataDir());
        assertEquals(67_108_864, config.getMemtableMaxBytes());
        assertEquals(8192, config.getBlockSize());
        assertEquals(134_217_728, config.getRollSize());
        assertEquals(4, config.getMaxImmutableTables());
        assertEquals(10, config.getRefreshN());
        assertEquals(64, config.getBlockCacheMb());
        assertTrue(config.isCacheIndexBlocks());
        assertEquals(1024, config.getMaxOpenFiles());
        assertEquals(4, config.getSizeTieredFanIn());
        assertEquals(2.0f, config.getSizeTieredSizeRatio());
        assertEquals(1, config.getCompactionMaxConcurrent());
        assertEquals(0, config.getCompactionIoMbPerS());
        assertEquals(8, config.getL0CompactionTrigger());
        assertEquals(20, config.getL0StopWrites());
    }
}
