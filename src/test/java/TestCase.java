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
import java.util.ArrayList;
import java.util.List;

import l3diskex.Common;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskD88.DiskD88Image;
import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskParam;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;
import vavi.util.serdes.CachingDIContainer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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
@EnabledIf("localPropertiesExists")
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "test.d88")
    String d88 = "src/test/resources/test.d88";

    @Property(name = "test.type")
    String type = "";

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    @Test
    @Disabled("for c++ to java porting")
    @DisplayName("find java file in the directory")
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
    @EnabledIf("localPropertiesExists")
    void test2() throws Exception {
Debug.print("type: \"" + type + "\", file: " + d88 + ", " + Files.exists(Path.of(d88)));
        Common.init();

        DiskImage diskImage = new DiskD88Image();

        // Create empty disk parameters as hint

        // Try to open the disk image
        List<DiskParam> params = new ArrayList<>();
        DiskParam manualParam = new DiskParam();
        String[] type_ = {type};
        int r1 = diskImage.check(d88, type_, params, manualParam);
        type = type_[0];
Debug.printf("check: %d, type: %s, params: %d, manual param: %s", r1, type, params.size(), manualParam);

        // Verify open was successful
        assertEquals(0, r1, "Failed to check disk image: " + diskImage.getErrorMessage(-1));

        // Try to open the disk image
        int r2 = diskImage.open(d88, type, !params.isEmpty() ? params.getFirst() : manualParam);
Debug.printf("open: %d", r2);

        // Verify disk was loaded
        assertTrue(diskImage.countDisks() > 0, "No disks found in image");

        // Get first disk
        DiskImageDisk disk = diskImage.getDisk(0);
        assertNotNull(disk, "Could not get first disk: " + diskImage.getErrorMessage(-1));

        // Check disk properties
Debug.printf("name: \"%s\"", disk.getName(true));
        assertFalse(disk.getTracks() == null || disk.getTracks().isEmpty(), "Disk has no tracks");
Debug.println("tracks: " + disk.getTracks().size());
Debug.println("typeName: " + disk.getDiskTypeName());
        disk.setDiskParam(disk.calcMajorNumber());
Debug.println("sectorSize: " + disk.getSectorSize() + ", sidesPerDisk: " + disk.getSidesPerDisk());

        // Create a DiskBasic instance to handle the file system
        DiskBasic diskBasic = disk.getDiskBasic(0);
Debug.println("diskNumber: " + diskBasic.getDiskNumber() + ", sidesPerDisk: " + diskBasic.getSidesPerDisk());

        int r3 = diskBasic.parseBasic(disk, disk.getSidesPerDisk() == 2 ? -1 : disk.getSidesPerDisk(), null, false);
        assert r3 == 0 : "diskBasic.parseBasic: " + diskBasic.getErrorMessage(r3);
Debug.println("FORMAT: " + diskBasic.getFormatTypeNumber());

        // Assign FAT and directory
        boolean r = diskBasic.assignRootDirectory(); // w/o this diskBasic#getRootDirectory returns null
        assert r : "diskBasic.assignRootDirectory";
Debug.println("ASSIGN: done");
        diskBasic.setCharCode("ShiftJIS"); // TODO automate?

        DiskBasicDirItem<?> root = diskBasic.getRootDirectory();
        assert root != null : "root is null";
Debug.println("TYPE: " + root.getClass().getSimpleName());
Debug.printf("files at dir: %d, %s, %08x", root.getChildren().size(), root.isDirectory(), root.getFileAttr().getType());
        // List files and directories
        walk(diskBasic, "", root);
    }

    void walk(DiskBasic diskBasic, String path, DiskBasicDirItem<?> dir) throws IOException {
        for (DiskBasicDirItem<?> file : dir.getChildren()) {
            if (file.isUsed() && !file.getFileNameStr().equals(".") && !file.getFileNameStr().equals("..") && !file.getFileNameStr().isEmpty()) {
                if (file.isDirectory()) {
                    System.out.println(path + "/" + file.getFileNameStr() + "/");
                    diskBasic.reassignDirectory(file);
                    walk(diskBasic, path + "/" + file.getFileNameStr(), file);
                } else {
                    System.out.println(path + "/" + file.getFileNameStr());
                }
            }
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (Boolean.parseBoolean(System.getProperty("vavi.util.serdes.cache.statistics", "false")))
            CachingDIContainer.printCacheStatistics();
    }
}
