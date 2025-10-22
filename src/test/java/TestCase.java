/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser;
import l3diskex.diskimg.DiskResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-07 nsano initial version <br>
 */
public class TestCase {

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
        DiskImage diskImage =
        DiskImageFile file = null;
        DiskResult result = new DiskResult();
        DiskParser parser = new DiskParser("example.d88", null, file, result);

        // Parsing an image (the concrete parser classes do nothing)
        int rc = parser.parse("d88", new DiskParam());
        System.out.println("Parse returned: " + rc);
    }
}
