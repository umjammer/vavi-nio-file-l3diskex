/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.l3;

import com.github.fge.filesystem.provider.FileSystemFactoryProvider;


/**
 * L3FileSystemFactoryProvider.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2025/11/22 umjammer initial version <br>
 */
public final class L3FileSystemFactoryProvider extends FileSystemFactoryProvider {

    public L3FileSystemFactoryProvider() {
        setAttributesFactory(new L3FileAttributesFactory());
        setOptionsFactory(new L3FileSystemOptionsFactory());
    }
}
