/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import l3diskex.ResultInfo;


public class DiskBasicError extends ResultInfo {

    /*
     * Constants – error codes
     */
    public static final int ERR_NONE = 0;
    public static final int ERR_SUPPORTED = 1;
    public static final int ERR_FORMATTED = 2;
    public static final int ERR_UNSELECT_DISK = 3;
    public static final int ERR_WRITE_PROTECTED = 4;
    public static final int ERR_WRITE_UNSUPPORTED = 5;
    public static final int ERR_FILE_NOT_FOUND = 6;
    public static final int ERR_FILE_ALREADY_EXIST = 7;
    public static final int ERR_DIRECTORY_FULL = 8;
    public static final int ERR_DISK_FULL = 9;
    public static final int ERR_FILE_TOO_LARGE = 10;
    public static final int ERR_NOT_ENOUGH_FREE = 11;
    public static final int ERR_CANNOT_EXPORT = 12;
    public static final int ERR_CANNOT_IMPORT = 13;
    public static final int ERR_CANNOT_VERIFY = 14;
    public static final int ERR_CANNOT_FORMAT = 15;
    public static final int ERR_FORMATTING = 16;
    public static final int ERR_FORMAT_UNSUPPORTED = 17;
    public static final int ERR_DELETE_UNSUPPORTED = 18;
    public static final int ERR_CANNOT_IMPORT_DIRECTORY = 19;
    public static final int ERR_CANNOT_MAKE_DIRECTORY = 20;
    public static final int ERR_MAKING_DIRECTORY = 21;
    public static final int ERR_IN_FAT_AREA = 22;
    public static final int ERR_IN_DIRECTORY_AREA = 23;
    public static final int ERR_IN_PARAMETER_AREA = 24;
    public static final int ERR_INVALID_IN_PARAMETER_AREA = 25;
    public static final int ERR_FILENAME_EMPTY = 26;
    public static final int ERR_FILEEXT_EMPTY = 27;
    public static final int ERR_END_ADDR_TOO_SMALL = 28;
    public static final int ERR_PATH_TOO_DEEP = 29;
    public static final int ERR_NO_FOUND_TRACK = 30;

    public static final int ERRV_START = 31;
    public static final int ERRV_VERIFY_FILE = 32;
    public static final int ERRV_MISMATCH_FILESIZE = 33;
    public static final int ERRV_NO_TRACK = 34;
    public static final int ERRV_NO_SECTOR = 35;
    public static final int ERRV_INVALID_SECTOR = 36;
    public static final int ERRV_NOTHING_IN_TRACK = 37;
    public static final int ERRV_NUM_OF_SECTORS_IN_TRACK = 38;
    public static final int ERRV_NO_SECTOR_IN_TRACK = 39;
    public static final int ERRV_INVALID_VALUE_IN = 40;
    public static final int ERRV_CANNOT_EDIT_NAME = 41;
    public static final int ERRV_CANNOT_SET_NAME = 42;
    public static final int ERRV_CANNOT_EXPORT = 43;
    public static final int ERRV_CANNOT_DELETE = 44;
    public static final int ERRV_CANNOT_DELETE_DIRECTORY = 45;
    public static final int ERRV_ALREADY_DELETED = 46;
    public static final int ERRV_ALREADY_EXISTS = 47;
    public static final int ERRV_END = 48;

    /*
     * Global translation table (literal translation of C++ array)
     */
    public static final String[] gDiskBasicErrorMsgs = {
            /* 0 */                     "",
            /* 1 */                     "Unsupported disk for DISK BASIC.",
            /* 2 */                     "It may be unformatted disk for DISK BASIC.",
            /* 3 */                     "Disk not selected.",
            /* 4 */                     "Write protected.",
            /* 5 */                     "Write operation unsupported.",
            /* 6 */                     "File not found.",
            /* 7 */                     "File already exists.",
            /* 8 */                     "Directory full.",
            /* 9 */                     "Disk full.",
            /*10 */                     "File too large.",
            /*11 */                     "Not enough free space.",
            /*12 */                     "Cannot export.",
            /*13 */                     "Cannot import.",
            /*14 */                     "Cannot verify.",
            /*15 */                     "Cannot format.",
            /*16 */                     "Formatting in progress.",
            /*17 */                     "Format unsupported.",
            /*18 */                     "Delete unsupported.",
            /*19 */                     "Cannot import directory.",
            /*20 */                     "Cannot create directory.",
            /*21 */                     "Creating directory.",
            /*22 */                     "Error in FAT area.",
            /*23 */                     "Error in directory area.",
            /*24 */                     "Error in parameter area.",
            /*25 */                     "Invalid parameter value.",
            /*26 */                     "Filename empty.",
            /*27 */                     "File extension empty.",
            /*28 */                     "End address too small.",
            /*29 */                     "Path too deep.",
            /*30 */                     "Track not found.",
            /*31 */                     "Unknown error. code:%d",
            /*32 */                     "File verification failed: %s",
            /*33 */                     "File size mismatch: %s",
            /*34 */                     "No track: %s",
            /*35 */                     "No sector: %s",
            /*36 */                     "Invalid sector: %s",
            /*37 */                     "Nothing in track: %s",
            /*38 */                     "Short sectors in track: %s",
            /*39 */                     "No sector in track: %s",
            /*40 */                     "Invalid value: %s",
            /*41 */                     "Cannot edit the name '%s'.",
            /*42 */                     "Cannot set the name '%s'.",
            /*43 */                     "Cannot export '%s'.",
            /*44 */                     "Cannot delete '%s'.",
            /*45 */                     "Cannot delete '%s' which is not empty.",
            /*46 */                     "File '%s' is already deleted.",
            /*47 */                     "File '%s' already exists.",
            /*48 */                     "Unknown error. code:%d"
    };

    /**
     * Constructor
     */
    public DiskBasicError() {
        super();
    }

    /*
     * Message formatting helper
     */

    /**
     * Translates a string.  In the original C++ code this was
     * performed by the {@code wxTRANSLATE} / {@code wxGetTranslation}
     * macros.  Here we simply return the string unchanged – the
     * infrastructure for real internationalisation would normally
     * live elsewhere.
     *
     * @param msg the string to translate
     * @return the translated string
     */
    private static String translate(String msg) {
        return msg;
    }

    /**
     * Formats an error message that requires arguments (var‑args in C++).
     *
     * @param errorNumber the error code
     * @param args        the arguments for the format string
     */
    @Override
    public void setMessageV(int errorNumber, Object... args) {
        if (errorNumber <= 0) {
            return;                           // nothing to do
        }

        String msg;

        if (errorNumber < ERRV_START) {         // no‑argument messages
            msg = translate(gDiskBasicErrorMsgs[errorNumber]);
        } else if (errorNumber < ERRV_END) {    // argument‑dependent messages
            msg = String.format(translate(gDiskBasicErrorMsgs[errorNumber]), args);
        } else {                               // unknown error
            msg = String.format(translate(gDiskBasicErrorMsgs[ERRV_END]), errorNumber);
        }

        // add to the collection only if it is not already present
        if (!msgs.contains(msg)) {
            msgs.add(msg);
        }
    }
}
