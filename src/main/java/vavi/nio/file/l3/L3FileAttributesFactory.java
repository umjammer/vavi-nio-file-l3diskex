/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.l3;

import com.github.fge.filesystem.driver.ExtendedFileSystemDriverBase.ExtendedFileAttributesFactory;
import l3diskex.basicfmt.DiskBasicDirItem;


/**
 * L3FileAttributesFactory.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2025/11/22 umjammer initial version <br>
 */
public final class L3FileAttributesFactory extends ExtendedFileAttributesFactory {

    public L3FileAttributesFactory() {
        setMetadataClass(DiskBasicDirItem.class);
        addImplementation("basic", L3BasicFileAttributesProvider.class);
    }
}
