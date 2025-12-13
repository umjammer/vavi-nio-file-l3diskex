/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.l3;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;
import vavi.util.serdes.Serdes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * SpiTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-11-23 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class SpiTest {

    static boolean localPropertiesExists() {
        return java.nio.file.Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "test.d88")
    String disk = "src/test/resources/test.d88";

    @Property(name = "encoding")
    String encoding = "Ascii8";

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    static String formattedLMT(Path p) throws IOException {
        return Files.getLastModifiedTime(p).toInstant()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
    }

    // ⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️
    // ⚠️⚠️⚠️ if list is only root dir, check the DiskBasicDirItem subclass type and method #getFileAttr ⚠️⚠️⚠️
    // ⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️
    @Test
    @DisplayName("list")
    void test1() throws Exception {
Debug.print("disk: " + disk);
        URI subUri = Path.of(disk).toUri();
Debug.print("subUri: " + subUri);
Debug.print("subUri.path: " + subUri.getPath());
        URI uri = URI.create("l3:" + subUri);
Debug.print("uri: " + uri);

        FileSystem fs = FileSystems.newFileSystem(uri, Map.of("encoding", encoding));

        Path root = fs.getRootDirectories().iterator().next();
        Files.walk(root).forEach(p -> {try { System.err.printf("%-32s  %s%n", p, formattedLMT(p)); } catch (
                Exception e) { Debug.printStackTrace(e); }});
//        assertEquals(100, Files.walk(root).count());

        fs.close();
    }

    @Test
    @DisplayName("download")
    void test2() throws Exception {
Debug.print("disk: " + disk);
        URI subUri = Path.of(disk).toUri();
Debug.print("subUri: " + subUri);
Debug.print("subUri.path: " + subUri.getPath());
        URI uri = URI.create("l3:" + subUri);
Debug.print("uri: " + uri);

        FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());

        Path root = fs.getRootDirectories().iterator().next();
        if (!Files.exists(Path.of("tmp"))) Files.createDirectory(Path.of("tmp"));
        Path out = Path.of("tmp", "test2.download");
        Files.copy(root.resolve("DATA/TEXT"), out, StandardCopyOption.REPLACE_EXISTING);
        assertEquals(289, Files.size(out));

        fs.close();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (Boolean.parseBoolean(System.getProperty("vavi.util.serdes.cache.statistics", "false")))
            Serdes.Cacher.printCacheStatistics();
    }
}
