/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import l3diskex.Utils;
import l3diskex.diskimg.FileParam.FileParamFormat;
import l3diskex.diskimg.writer.DiskD88Writer;
import l3diskex.diskimg.writer.DiskPlainWriter;
import vavi.io.SeekableDataOutputStream;

import static l3diskex.diskimg.FileParam.fileTypes;


/**
 * Disk writer
 */
public class DiskWriter extends DiskWriteOptions {

    public static final String[] formatTypeNamesForSave = {
            "", "d88", "plain", null
    };

    private final String filePath;
    private final DiskImage image;
    private OutputStream oStream;
    private boolean ownStream;
    private final DiskResult result;

    /**
     * Search for extension
     * @param diskNumber Disk number
     * @param sideNumber Side number
     */
    private int canSaveDiskByExt(int diskNumber, int sideNumber) {
        int rc = 0;

        // If no file format is specified
        String fpath = filePath;

        // Judge by extension
        String ext = Utils.getExt(fpath);

        // Whether it is a supported file
        FileParam fItem = fileTypes.findExt(ext);
        if (fItem == null) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }

        // Output file in specified format
        List<FileParamFormat> formats = fItem.getFormats();
        for (FileParamFormat format : formats) {
            rc = selectCanSaveDisk(format.getType(), diskNumber, sideNumber);
            if (rc >= 0) {
                break;
            }
        }

        return rc;
    }

    /**
     * Determine saving format by extension and whether it can be saved
     * @param fileFormat File format
     * @param diskNumber Disk number
     * @param sideNumber Side number
     */
    private int selectCanSaveDisk(String fileFormat, int diskNumber, int sideNumber) {
        ServiceLoader<DiskImageWriter> serviceLoader = ServiceLoader.load(DiskImageWriter.class);
        for (DiskImageWriter writer : serviceLoader) {
            if (writer.isSupported(fileFormat)) {
                writer.init(this, result);
                int rc = writer.validateDisk(image, diskNumber, sideNumber);
                return rc;
            }
        }

        return -1;
    }

    /**
     * Search for extension
     *
     * @param diskNumber Disk number
     * @param sideNumber Side number
     * @param support    [out] true if supported format
     */
    private int saveDiskByExt(int diskNumber, int sideNumber, boolean[] support) throws IOException {
        int rc = 0;

        // If no file format is specified
        String fpath = filePath;

        // Judge by extension
        String ext = Utils.getExt(fpath);

        // Whether it is a supported file
        FileParam fitem = fileTypes.findExt(ext);
        if (fitem == null) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }

        // Output file in specified format
        List<FileParamFormat> formats = fitem.getFormats();
        for (FileParamFormat format : formats) {
            rc = selectSaveDisk(format.getType(), diskNumber, sideNumber, support);
            if (rc >= 0) {
                break;
            }
        }

        return rc;
    }

    /** Determine saving format by extension */
    private int selectSaveDisk(String fileFormat, int diskNumber, int sideNumber, boolean[] support) throws IOException {
        ServiceLoader<DiskImageWriter> serviceLoader = ServiceLoader.load(DiskImageWriter.class);
        for (DiskImageWriter writer : serviceLoader) {
            if (writer.isSupported(fileFormat)) {
                writer.init(this, result);
                int rc = writer.saveDisk(image, diskNumber, sideNumber, oStream);
                support[0] = true;
                if (rc >= 0) {
                    // Keep the saved file name
                    image.setFileName(filePath);
                }
                return rc;
            }
        }

        return -1;
    }

    //
    // Save by each format
    //

    /**
     * @param image   Disk image
     * @param path    File path
     * @param options Options when outputting
     * @param result  Result
     */
    public DiskWriter(DiskImage image, String path, DiskWriteOptions options, DiskResult result) throws IOException {
        super(options.trimUnusedData);
        this.image = image;
        filePath = path;
        this.result = result;
        oStream = null;
        open(path);
    }

    /**
     * @param image  Disk image
     * @param result Result
     */
    public DiskWriter(DiskImage image, DiskResult result) {
        this.image = image;
        filePath = "";
        this.result = result;
        oStream = null;
        ownStream = false;
    }

    /**
     * Open destination
     *
     * @param path Destination file path
     * @return Result
     */
    public int open(String path) throws IOException {
        OutputStream fStream = new SeekableDataOutputStream(Files.newByteChannel(Path.of(path)));
        oStream = fStream;
        ownStream = true;
        return result.getValid();
    }

    /**
     * Whether the output destination is open
     *
     * @return true if open and ready, false otherwise
     */
    public boolean isOk() {
        return oStream != null;
    }

    /**
     * Whether it is a supported disk image
     *
     * @param fileFormat File format
     * @return true if supported, false otherwise
     */
    public static boolean supportedFormat(String fileFormat) {
        boolean match = false;
        for (int i = 1; formatTypeNamesForSave[i] != null; i++) {
            if (fileFormat.equals(formatTypeNamesForSave[i])) {
                match = true;
                break;
            }
        }
        return match;
    }

    /**
     * Whether disk image can be saved
     *
     * @param fileFormat File format
     * @return 0: possible, 1: warning exists (>=0: success, <0: error)
     */
    public int canSave(String fileFormat) {
        return canSaveDisk(-1, -1, fileFormat);
    }

    /**
     * Whether content of stream can be saved to file
     *
     * @param diskNumber Disk number
     * @param sideNumber Side number
     * @param fileFormat File format
     * @return 0: possible, 1: warning exists (>=0: success, <0: error)
     */
    public int canSaveDisk(int diskNumber, int sideNumber, String fileFormat) {
        int rc = 0;
        if (fileFormat.isEmpty()) {
            // If no file format is specified
            rc = canSaveDiskByExt(diskNumber, sideNumber);
        } else {
            // With file format specified
            rc = selectCanSaveDisk(fileFormat, diskNumber, sideNumber);
        }
        return rc;
    }

    /**
     * Saving of disk image
     *
     * @param fileFormat File format
     * @return Result
     */
    public int save(String fileFormat) throws IOException {
        return saveDisk(-1, -1, fileFormat);
    }

    /**
     * Save content of stream to file
     *
     * @param diskNumber Disk number
     * @param sideNumber Side number
     * @param fileFormat File format
     * @return Result
     */
    public int saveDisk(int diskNumber, int sideNumber, String fileFormat) throws IOException {
        int rc = 0;
        boolean[] support = {false};

        if (!isOk()) {
            result.setError(DiskResult.ERR_CANNOT_SAVE);
            return result.getValid();
        }

        if (fileFormat.isEmpty()) {
            // If no file format is specified
            rc = saveDiskByExt(diskNumber, sideNumber, support);
        } else {
            // With file format specified
            rc = selectSaveDisk(fileFormat, diskNumber, sideNumber, support);
        }
        if (!support[0]) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }
        return rc;
    }

    /** Disk writer for each format */
    public abstract static class DiskImageWriter {

        protected DiskWriter writer;
        protected DiskResult result;

        /** type string is supported nor not */
        public abstract boolean isSupported(String type);

        public void init(DiskWriter writer, DiskResult result) {
            this.writer = writer;
            this.result = result;
        }

        /**
         * Whether content of stream can be saved to file
         *
         * @param image       Disk image
         * @param diskNumber 0~: Disk number, -1: all
         * @param sideNumber 0~: Side number, -1: both sides
         * @return 0: Normal
         */
        public int validateDisk(DiskImage image, int diskNumber, int sideNumber) {
            result.clear();

            return 0;
        }

        /**
         * Save content of stream to file
         *
         * @param image       Disk image
         * @param diskNumber 0~: Disk number, -1: all
         * @param sideNumber 0~: Side number, -1: both sides
         * @param oStream     Output destination
         * @return 0: Normal
         */
        public int saveDisk(DiskImage image, int diskNumber, int sideNumber, OutputStream oStream) throws IOException {
            result.clear();

            return 0;
        }
    }
}

/**
 * Options when writing disk
 */
class DiskWriteOptions {

    protected boolean trimUnusedData;

    public DiskWriteOptions() {
        trimUnusedData = false;
    }

    public DiskWriteOptions(boolean trimUnusedData) {
        this.trimUnusedData = trimUnusedData;
    }

    public boolean isTrimUnusedData() {
        return trimUnusedData;
    }
}

