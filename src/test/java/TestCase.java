/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import l3diskex.Common;
import l3diskex.diskimg.DiskD88.DiskD88Image;
import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskParam;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-07 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "test.d88")
    String d88 = "src/test/resources/test.d88";

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test1() throws Exception {
        Files.walk(Path.of(System.getProperty("user.dir"), "src/main/java"))
                .filter(p -> p.getFileName().toString().endsWith(".java")).forEach(p -> {
                    try {
                        if (Files.size(p) < 3) {
                            System.out.println(p.getFileName().toString().replace(".java", ","));
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    @Test
    void test2() throws Exception {
Debug.print("d88: " + d88);
        Common.init();

        DiskImage diskImage = new DiskD88Image();

        // Format type (e.g. "D88" for .d88 files)
        String fileFormat = "d88";

        // Create empty disk parameters as hint
        DiskParam paramHint = new DiskParam();

        // Try to open the disk image
        int result = diskImage.open(d88, fileFormat, paramHint);

        // Verify open was successful
        assertEquals(0, result, "Failed to open disk image: " + diskImage.getErrorMessage(-1));

        // Verify disk was loaded
        assertTrue(diskImage.countDisks() > 0, "No disks found in image");

        // Get first disk
        DiskImageDisk disk = diskImage.getDisk(0);
        assertNotNull(disk, "Could not get first disk: " + diskImage.getErrorMessage(-1));

        // Check disk properties
Debug.printf("name: \"%s\"", disk.getName(true));
        assertFalse(disk.getTracks().isEmpty(), "Disk has no tracks");
Debug.println("tracks: " + disk.getTracks().size());
    }
}
