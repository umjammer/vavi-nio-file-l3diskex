/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.nio.file.l3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.ParametersAreNonnullByDefault;

import com.github.fge.filesystem.driver.ExtendedFileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import vavi.nio.file.Util;

import static vavi.nio.file.Util.isAppleDouble;


/**
 * L3FileSystemDriver.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2025/11/22 umjammer initial version <br>
 */
@ParametersAreNonnullByDefault
public final class L3FileSystemDriver extends ExtendedFileSystemDriver<DiskBasicDirItem<? extends Directory>> {

    private static final Logger logger = System.getLogger(L3FileSystemDriver.class.getName());

    private final DiskBasic disk;

    /**
     * @param disk disk object
     * @param env  { "ignoreAppleDouble": boolean }
     */
    public L3FileSystemDriver(FileStore fileStore,
                              FileSystemFactoryProvider provider,
                              DiskBasic disk, Map<String, ?> env) throws IOException {

        super(fileStore, provider);

        this.disk = disk;
        setEnv(env);
    }

    @Override
    protected String getFilenameString(DiskBasicDirItem entry) {
        return entry.getFileNameStr();
    }

    @Override
    protected boolean isFolder(DiskBasicDirItem entry) throws IOException {
        return entry.isDirectory();
    }

    @Override
    protected boolean exists(DiskBasicDirItem entry) throws IOException {
        return !entry.isUsed();
    }

    /** finds a dir item of the given {@code name} in the {@code dir} list */
    private final BiFunction<Path, DiskBasicDirItem<? extends Directory>, DiskBasicDirItem> findNameOfDir = (name, dir) ->
            dir.getChildren().stream().filter(e -> e.getFileNameStr().equals(name.toString())).findFirst().orElseThrow();

    @Override
    protected DiskBasicDirItem<? extends Directory> getEntry(Path path) throws IOException {
        if (ignoreAppleDouble && path.getFileName() != null && isAppleDouble(path)) {
            throw new NoSuchFileException("ignore apple double file: " + path);
        }

        try {
            DiskBasicDirItem<? extends Directory> currentDir = disk.getRootDirectory();

            if (path.getNameCount() == 0) {
                return currentDir;
            }
            for (int i = 0; i < path.getNameCount(); i++) {
                Path name = path.getName(i);
                if (i == path.getNameCount() - 1) {
                    var file = findNameOfDir.apply(name, currentDir);
                    if (file.isDirectory()) disk.reassignDirectory(file);
                    return file;
                } else {
                    currentDir = findNameOfDir.apply(name, currentDir);
                    if (!currentDir.isDirectory()) break;
                }
            }
        } catch (NoSuchElementException ignore) {
logger.log(Level.TRACE, "not found: " + path);
        }

        throw new NoSuchFileException(path.toString());
    }

    @Override
    protected InputStream downloadEntry(DiskBasicDirItem<? extends Directory> entry, Path path, Set<? extends OpenOption> options) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        disk.loadData(entry, baos, null);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    @Override
    protected OutputStream uploadEntry(DiskBasicDirItem<? extends Directory> parentEntry, Path path, Set<? extends OpenOption> options) throws IOException {
        return new Util.OutputStreamForUploading(new ByteArrayOutputStream()) {
            @Override
            protected void onClosed() throws IOException {
                DiskBasicDirItem fileEntry = disk.createDirItem();
                fileEntry.setFileNameStr(path.getFileName().toString());
                DiskBasicDirItem[] newEntry = new DiskBasicDirItem[1];
                disk.saveFile(getInputStream(), parentEntry, fileEntry, newEntry);
            }
        };
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected List<DiskBasicDirItem<? extends Directory>> getDirectoryEntries(DiskBasicDirItem<? extends Directory> dirEntry, Path dir) throws IOException {
        List<DiskBasicDirItem<? extends Directory>> list = (List) dirEntry.getChildren().stream()
                .filter(DiskBasicDirItem::isUsed)
                .filter(i -> !i.getFileNameStr().equals(".") && !i.getFileNameStr().equals(".."))
                .toList();
        return list;
    }

    @Override
    protected DiskBasicDirItem<? extends Directory> createDirectoryEntry(DiskBasicDirItem<? extends Directory> parentEntry, Path dir) throws IOException {
        if (!disk.canMakeDirectory()) {
            throw new UnsupportedOperationException("doesn't support directory creation.");
        } else {
            var dirItem = parentEntry.getBasic().createDirItem();
            dirItem.setFileNameStr(dir.getFileName().toString());
            return dirItem;
        }
    }

    @Override
    protected boolean hasChildren(DiskBasicDirItem<? extends Directory> dirEntry, Path dir) throws IOException {
        return !dirEntry.getChildren().isEmpty();
    }

    @Override
    protected void removeEntry(DiskBasicDirItem<? extends Directory> entry, Path path) throws IOException {
        entry.delete();
    }

    @Override
    protected DiskBasicDirItem<? extends Directory> copyEntry(DiskBasicDirItem<? extends Directory> sourceEntry, DiskBasicDirItem<? extends Directory> targetParentEntry, Path source, Path target, Set<CopyOption> options) throws IOException {
        throw new UnsupportedOperationException("not implemented yet");
//        FileEntry targetEntry = getEntry(target, false);
//        Files.copy(sourceEntry.toPath(), targetEntry.toPath());
//        return targetEntry;
    }

    @Override
    protected DiskBasicDirItem<? extends Directory> moveEntry(DiskBasicDirItem<? extends Directory> sourceEntry, DiskBasicDirItem<? extends Directory> targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        throw new UnsupportedOperationException("not implemented yet");
//        FileEntry targetEntry = getEntry(targetIsParent ? target.resolve(toFilenameString(source)) : target, false);
//        Files.move(sourceEntry.toPath(), targetEntry.toPath());
//        return targetEntry;
    }

    @Override
    protected DiskBasicDirItem<? extends Directory> moveFolderEntry(DiskBasicDirItem<? extends Directory> sourceEntry, DiskBasicDirItem<? extends Directory> targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        return moveEntry(sourceEntry, targetParentEntry, source, target, targetIsParent);
    }

    @Override
    protected DiskBasicDirItem<? extends Directory> renameEntry(DiskBasicDirItem<? extends Directory> sourceEntry, DiskBasicDirItem<? extends Directory> targetParentEntry, Path source, Path target) throws IOException {
        return moveEntry(sourceEntry, targetParentEntry, source, target, false);
    }
}
