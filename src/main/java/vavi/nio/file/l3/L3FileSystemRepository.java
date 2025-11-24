/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.l3;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.github.fge.filesystem.driver.FileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemRepositoryBase;
import l3diskex.Common;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.diskimg.DiskD88.DiskD88Image;
import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskParam;

import static java.lang.System.getLogger;


/**
 * L3FileSystemRepository.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2025/11/22 umjammer initial version <br>
 */
@ParametersAreNonnullByDefault
public final class L3FileSystemRepository extends FileSystemRepositoryBase {

    private static final Logger logger = getLogger(L3FileSystemRepository.class.getName());

    public L3FileSystemRepository() {
        super("l3", new L3FileSystemFactoryProvider());
    }

    static {
        Common.init();
    }

    /**
     * @param uri "l3:file:///foo/bar.buz"
     * @param env { "volumeNumber": specify number of multiple disk images in a file }
     */
    @Nonnull
    @Override
    public FileSystemDriver createDriver(URI uri, Map<String, ?> env) throws IOException {
        String[] rawSchemeSpecificParts = uri.getRawSchemeSpecificPart().split("!");
        URI file = URI.create(rawSchemeSpecificParts[0]);
        if (!"file".equals(file.getScheme())) {
            // currently only support "file"
            throw new IllegalArgumentException(file.toString());
        }
        if (!file.getRawSchemeSpecificPart().startsWith("/")) {
            file = URI.create(file.getScheme() + ":" + System.getProperty("user.dir") + "/" + file.getRawSchemeSpecificPart());
        }

        int volumeNumber = 0;
        if (env.containsKey("volumeNumber")) {
            volumeNumber = (int) env.get("volumeNumber");
        }

        DiskImage diskImage = new DiskD88Image();

        String type = "";
logger.log(Level.DEBUG, "path: " + file);
        List<DiskParam> params = new ArrayList<>();
        DiskParam manualParam = new DiskParam();
        String[] types = {type};
        int r1 = diskImage.check(file.getPath(), types, params, manualParam);
        type = types[0];
logger.log(Level.TRACE, "check: %d, %s, %d, %s".formatted(r1, type, params.size(), manualParam));
        if (r1 != 0)
            throw new IllegalArgumentException("Failed to check disk image: " + diskImage.getErrorMessage(-1));

        // Try to open the disk image
        int r2 = diskImage.open(file.getPath(), type, manualParam);
        if (r2 > 0 && diskImage.countDisks() < 1)
            throw new IllegalArgumentException("No disks found in image");

        // Get first disk
        DiskImageDisk disk = diskImage.getDisk(0);
        if (disk == null)
            throw new IllegalArgumentException("Could not get first disk: " + diskImage.getErrorMessage(-1));

        // Check disk properties
logger.log(Level.TRACE, "name: \"%s\"".formatted(disk.getName(true)));
        if (disk.getTracks().isEmpty())
            throw new IllegalArgumentException("Disk has no tracks");
logger.log(Level.TRACE, "tracks: " + disk.getTracks().size());
logger.log(Level.TRACE, "typeName: " + disk.getDiskTypeName());
        disk.setDiskParam(disk.calcMajorNumber());
logger.log(Level.TRACE, "sectorSize: " + disk.getSectorSize());

        // Create a DiskBasic instance to handle the file system
        DiskBasic diskBasic = disk.getDiskBasic(0);
logger.log(Level.TRACE, "diskBasic.diskNumber: " + diskBasic.getDiskNumber());

        int r3 = diskBasic.parseBasic(disk, 0, null, false);
        if (r3 != 0)
            throw new IllegalArgumentException("diskBasic.parseBasic: " + diskBasic.getErrorMessage(r3));
logger.log(Level.DEBUG, "FORMAT: " + diskBasic.getType().getClass().getSimpleName());

        // Assign FAT and directory
        boolean r = diskBasic.assignRootDirectory(); // w/o this diskBasic#getRootDirectory returns null
        if (!r)
            throw new IllegalStateException("diskBasic.assignRootDirectory");
logger.log(Level.TRACE, "ASSIGN: root: %s, children: %d, isDir: %s, attr: %08x".formatted(diskBasic.getRootDirectory().getClass().getSimpleName(), diskBasic.getRootDirectory().getChildren().size(), diskBasic.getRootDirectory().isDirectory(), diskBasic.getRootDirectory().getFileAttr().getType()));

        L3FileStore fileStore = new L3FileStore(diskBasic, factoryProvider.getAttributesFactory());
        return new L3FileSystemDriver(fileStore, factoryProvider, diskBasic, env);
    }

    /* ad-hoc hack for ignoring checking opacity */
    @Override
    protected void checkURI(@Nullable URI uri) {
        Objects.requireNonNull(uri);
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("uri is not absolute");
        }
        if (!getScheme().equals(uri.getScheme())) {
            throw new IllegalArgumentException("bad scheme");
        }
    }
}
