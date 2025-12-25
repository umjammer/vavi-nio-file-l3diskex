/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import l3diskex.Utils;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import l3diskex.diskimg.FileParam.FileParamFormat;

import static l3diskex.diskimg.FileParam.fileTypes;


/**
 * Disk Parser
 */
public class DiskParser {

    private static final Logger logger = System.getLogger(DiskParser.class.getName());

    private final Path filepath;
    private final InputStream stream;
    private final DiskImageFile file;
    private final DiskResult result;
    private String imageType;

    /**
     * Constructor
     *
     * @param filepath Path of the file to analyze
     * @param stream   Stream of the above file
     * @param file     [in,out] Existing disk image
     * @param result   [out] Result
     */
    public DiskParser(String filepath, InputStream stream,
                      DiskImageFile file, DiskResult result) {
        this.filepath = Path.of(filepath);
        this.stream = stream;
        this.file = file;
        this.result = result;
        this.imageType = "";
    }

    /**
     * Newly analyze disk image
     *
     * @param fileFormat File format name ("d88", "plain" etc.)
     * @param paramHint  Disk parameter hint (only when "plain")
     */
    public int parse(String fileFormat, DiskParam paramHint) throws IOException {
        return parse(fileFormat, paramHint, DiskImageFile.MODIFY_NONE);
    }

    /**
     * Analyze specified disk and add it to existing disk image
     *
     * @param fileFormat File format name ("d88", "plain" etc.)
     * @param paramHint  Disk parameter hint (only when "plain")
     */
    public int parseAdd(String fileFormat, DiskParam paramHint) throws IOException {
        return parse(fileFormat, paramHint, DiskImageFile.MODIFY_ADD);
    }

    /**
     * Check disk image
     *
     * @param fileFormat  [in,out] File format name ("d88", "plain", "", etc.)
     * @param diskParams  [out] Candidates for disk parameters
     * @param manualParam [out] Parameter hint when there are no candidates
     */
    public int check(String[] fileFormat, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(fileFormat, diskParams, manualParam, DiskImageFile.MODIFY_NONE);
    }

    public String getImageType() {
        return imageType;
    }

    /**
     * Analysis of disk image
     *
     * @param fileFormat File format name ("d88", "plain" etc.)
     * @param paramHint  Disk parameter hint (only when "plain")
     * @param modFlags   Open/Add DiskImageFile#add()
     * @return 0: normal, -1: error, 1: warning
     */
    private int parse(String fileFormat, DiskParam paramHint, short modFlags) throws IOException {
        boolean[] support = {false};
        int rc = -1;

        imageType = "";
        if (!fileFormat.isEmpty()) {
            // File format specified
            rc = selectParser(fileFormat, paramHint, modFlags, support);
            if (rc >= 0) {
                imageType = fileFormat;
            }
        }
        if (!support[0]) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }
        return rc;
    }

    /**
     * Check disk image
     *
     * @param fileFormat  [in,out] File format name ("d88", "plain", "", etc.)
     * @param diskParams  [out] Candidates for disk parameters
     * @param manualParam [out] Parameter hint when there are no candidates
     * @param modFlags    Open/Add DiskImageFile::Add()
     * @return 0: normal, -1: error
     */
    private int check(String[] fileFormat, List<DiskParam> diskParams,
                      DiskParam manualParam, short modFlags) throws IOException {
        boolean[] support = {false};
        int rc = -1;

        if (fileFormat[0].isEmpty()) {
            // Case where file format is not specified

            // Judge by extension
            String ext = Utils.getExt(filepath.getFileName().toString());

            // Whether it is a supported file
            FileParam fItem = fileTypes.findExt(ext);
            if (fItem == null) {
                result.setError(DiskResult.ERR_UNSUPPORTED);
                return result.getValid();
            }

            // Analyze with specified format
            List<FileParamFormat> formats = fItem.getFormats();
logger.log(Level.TRACE, "formats: %d, %s".formatted(formats.size(), formats));
            for (FileParamFormat format : formats) {
                rc = selectChecker(format.getType(), format.getHints(), null, diskParams, manualParam, modFlags, support);
logger.log(Level.TRACE, "selectChecker: %d, %s".formatted(rc, format.getType()));
                if (rc >= 0) {
                    fileFormat[0] = format.getType();
                    break;
                }
            }

        } else {
            // File format specified

            rc = selectChecker(fileFormat[0], null, null, diskParams, manualParam, modFlags, support);
        }
        if (!support[0]) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }
        return rc;
    }

    /**
     * Select file analysis method
     *
     * @param type      File format name ("d88", "plain" etc.)
     * @param diskParam Disk parameter (only when "plain")
     * @param modFlags  Open/Add DiskImageFile#add()
     * @param support   [out] Whether it is a supported file
     * @return 1: warning, 0: normal, -1: error
     */
    private int selectParser(String type, DiskParam diskParam,
                             short modFlags, boolean[] support) throws IOException {

        ServiceLoader<DiskImageParser> serviceLoader = ServiceLoader.load(DiskImageParser.class);
        for (DiskImageParser parser : serviceLoader) {
            if (parser.isSupported(type)) {
                parser.init(file, modFlags, result);
                int rc = -1;
                if (parser.needsCheck()) {
                    if (parser.check(stream) != 0) {
                        rc = parser.parse(stream, diskParam);
                    }
                } else {
                    rc = parser.parse(stream, diskParam);
                }
                support[0] = true;
                return rc;
            }
        }

        logger.log(Level.WARNING, type + " is not supported");
        return -1;
    }

    /**
     * Select file check method
     *
     * @param type        File format name ("d88", "plain" etc.)
     * @param diskHints   Disk parameter hint (only when "plain")
     * @param diskParam   Disk parameter (only when "plain")
     * @param diskParams  [out] Candidates for disk parameters
     * @param manualParam [out] Parameter hint when there are no candidates
     * @param modFlags    Open/Add DiskImageFile::Add()
     * @param support     [out] Whether it is a supported file
     * @return 1: ask user to select disk type again as there are no candidates, 0: normal with candidates, -1: error end
     */
    private int selectChecker(String type, List<DiskTypeHint> diskHints,
                              DiskParam diskParam, List<DiskParam> diskParams,
                              DiskParam manualParam, short modFlags,
                              boolean[] support) throws IOException {

        ServiceLoader<DiskImageParser> serviceLoader = ServiceLoader.load(DiskImageParser.class);
        for (DiskImageParser parser : serviceLoader) {
logger.log(Level.TRACE, type + " is supported: " + parser.isSupported(type));
            if (parser.isSupported(type)) {
                parser.init(file, modFlags, result);
                int rc = parser.check(stream, diskHints, diskParam, diskParams, manualParam);
                support[0] = true;
                return rc;
            }
        }

logger.log(Level.WARNING, type + " is not supported");
        return -1;
    }

    /** Disk Parser */
    public static abstract class DiskImageParser {

        protected DiskImageFile file;
        protected short modFlags;
        protected DiskResult result;

        /** type string is supported nor not */
        public abstract boolean isSupported(String type);

        /** does check need before parse */
        public boolean needsCheck() {
            return false;
        }

        /** returns teh condition if the check is needed */
        public boolean checkCondition(InputStream iStream) throws IOException {
            return false;
        }

        public void init(DiskImageFile file, short modFlags, DiskResult result) {
            this.file = file;
            this.modFlags = modFlags;
            this.result = result;
        }

        /**
         * Check
         *
         * @param iStream Data to be analyzed
         * @return 1: display selection dialog, 0: normal (display dialog when multiple candidates)
         */
        public int check(InputStream iStream) throws IOException {
            return result.getValid();
        }

        /**
         * Check
         *
         * @param iStream     Data to be analyzed
         * @param hints       Disk parameter hints (e.g. "2D")
         * @param diskParam   Disk parameter (Nullable when disk_hints specified)
         * @param diskParams  [out] Candidates for disk parameters
         * @param manualParam [out] Parameter hint when there are no candidates
         * @return 1: display selection dialog, 0: normal (display dialog when multiple candidates)
         */
        public int check(InputStream iStream, List<DiskTypeHint> hints,
                         DiskParam diskParam, List<DiskParam> diskParams,
                         DiskParam manualParam) throws IOException {
            return result.getValid();
        }

        /**
         * Analyze file image
         *
         * @param iStream   Data to be analyzed
         * @param diskParam Disk parameter
         * @return 0: normal, -1: error, 1: warning
         */
        public int parse(InputStream iStream, DiskParam diskParam /* = null */) throws IOException {
            return result.getValid();
        }
    }
}
