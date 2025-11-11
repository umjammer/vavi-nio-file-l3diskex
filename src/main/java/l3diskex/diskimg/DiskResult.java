/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;

import l3diskex.ResultInfo;


/**
 * Disk analysis result.
 */
public class DiskResult extends ResultInfo {

    private static final Logger logger = System.getLogger(DiskResult.class.getName());

    public static final int ERR_NONE = 0;
    // 引数なしのメッセージ
    public static final int ERR_CANNOT_OPEN = ERR_NONE + 1;
    public static final int ERR_CANNOT_SAVE = ERR_CANNOT_OPEN + 1;
    public static final int ERR_NO_DATA = ERR_CANNOT_SAVE + 1;
    public static final int ERR_NO_DISK = ERR_NO_DATA + 1;
    public static final int ERR_NO_TRACK = ERR_NO_DISK + 1;
    public static final int ERR_REPLACE = ERR_NO_TRACK + 1;
    public static final int ERR_FILE_ONLY_1S = ERR_REPLACE + 1;
    public static final int ERR_FILE_SAME = ERR_FILE_ONLY_1S + 1;
    public static final int ERR_INTERLEAVE = ERR_FILE_SAME + 1;
    public static final int ERR_TOO_LARGE = ERR_INTERLEAVE + 1;
    public static final int ERR_WRITE_PROTECTED = ERR_TOO_LARGE + 1;
    public static final int ERR_UNSUPPORTED = ERR_WRITE_PROTECTED + 1;

    public static final int ERRV_START = ERR_UNSUPPORTED + 1;
    // 引数あり（フォーマットあり）のメッセージ
    public static final int ERRV_INVALID_DISK = ERRV_START + 1;
    public static final int ERRV_DISK_SIZE_ZERO = ERRV_INVALID_DISK + 1;
    public static final int ERRV_DISK_TOO_SMALL = ERRV_DISK_SIZE_ZERO + 1;
    public static final int ERRV_DISK_TOO_LARGE = ERRV_DISK_TOO_SMALL + 1;
    public static final int ERRV_DISK_HEADER = ERRV_DISK_TOO_LARGE + 1;
    public static final int ERRV_OVERFLOW_OFFSET = ERRV_DISK_HEADER + 1;
    public static final int ERRV_OVERFLOW_SIZE = ERRV_OVERFLOW_OFFSET + 1;
    public static final int ERRV_ID_TRACK = ERRV_OVERFLOW_SIZE + 1;
    public static final int ERRV_ID_SIDE = ERRV_ID_TRACK + 1;
    public static final int ERRV_ID_SECTOR = ERRV_ID_SIDE + 1;
    public static final int ERRV_TRACKS_HEADER = ERRV_ID_SECTOR + 1;
    public static final int ERRV_SIDES_HEADER = ERRV_TRACKS_HEADER + 1;
    public static final int ERRV_SECTORS_HEADER = ERRV_SIDES_HEADER + 1;
    public static final int ERRV_ID_NUM_OF_SECTOR = ERRV_SECTORS_HEADER + 1;
    public static final int ERRV_TOO_MANY_SECTORS = ERRV_ID_NUM_OF_SECTOR + 1;
    public static final int ERRV_SHORT_SECTORS = ERRV_TOO_MANY_SECTORS + 1;
    public static final int ERRV_SECTOR_SIZE_HEADER = ERRV_SHORT_SECTORS + 1;
    public static final int ERRV_SECTOR_SIZE_SECTOR = ERRV_SECTOR_SIZE_HEADER + 1;
    public static final int ERRV_DUPLICATE_TRACK = ERRV_SECTOR_SIZE_SECTOR + 1;
    public static final int ERRV_DUPLICATE_SECTOR = ERRV_DUPLICATE_TRACK + 1;
    public static final int ERRV_NO_SECTOR = ERRV_DUPLICATE_SECTOR + 1;
    public static final int ERRV_IGNORE_DATA = ERRV_NO_SECTOR + 1;
    public static final int ERRV_TOO_MANY_TRACKS = ERRV_IGNORE_DATA + 1;
    public static final int ERRV_UNSUPPORTED_TYPE = ERRV_TOO_MANY_TRACKS + 1;
    public static final int ERRV_END = ERRV_UNSUPPORTED_TYPE + 1;

    /** Messages corresponding to the error codes above. */
    private static final String[] gDiskResultMsgs = new String[] {
            /* ERR_NONE                     */ "",
            /* ERR_CANNOT_OPEN              */ "Cannot open file.",
            /* ERR_CANNOT_SAVE              */ "Cannot save file.",
            /* ERR_NO_DATA                  */ "No data exists.",
            /* ERR_NO_DISK                  */ "No disk exists.",
            /* ERR_NO_TRACK                 */ "No track exists.",
            /* ERR_REPLACE                  */ "Couldn't replace a part of sector.",
            /* ERR_FILE_ONLY_1S             */ "Supported file is only single side and single density (1S).",
            /* ERR_FILE_SAME                */ "Must be the same disk image type.",
            /* ERR_INTERLEAVE               */ "Couldn't create the disk specified interleave.",
            /* ERR_TOO_LARGE                */ "The file is too large.",
            /* ERR_WRITE_PROTECTED          */ "Write protected.",
            /* ERR_UNSUPPORTED              */ "Unsupported file.",

            /* ERRV_START                   */ "Unknown error. code:%d",
            /* ERRV_INVALID_DISK            */ "[Disk%d] This is invalid or non supported disk.",
            /* ERRV_DISK_SIZE_ZERO          */ "[Disk%d] Disk size is zero.",
            /* ERRV_DISK_TOO_SMALL          */ "[Disk%d] Disk size is too small.",
            /* ERRV_DISK_TOO_LARGE          */ "[Disk%d] Disk size is invalid or too large.",
            /* ERRV_DISK_HEADER             */ "[Disk%d] Invalid parameter may exists in the disk header.",
            /* ERRV_OVERFLOW_OFFSET         */ "[Disk%d] Overflow offset. This track is ignored. position:%d offset:%d disk size:%d",
            /* ERRV_OVERFLOW_SIZE           */ "[Disk%d] Overflow disk size. size:%d",
            /* ERRV_ID_TRACK                */ "[Disk%d] Unmatch id C and track number %d. id[C:%d H:%d R:%d]",
            /* ERRV_ID_SIDE                 */ "[Disk%d] Unmatch id H and side %d in track %d. id[C:%d H:%d R:%d]",
            /* ERRV_ID_SECTOR               */ "[Disk%d] Invalid id R in track %d. id[C:%d H:%d R:%d] num of sector:%d",
            /* ERRV_TRACKS_HEADER           */ "[Disk%d] Number of track is too large in header. tracks:%d",
            /* ERRV_SIDES_HEADER            */ "[Disk%d] Number of side is too large in header. sides:%d",
            /* ERRV_SECTORS_HEADER          */ "[Disk%d] Invalid number of sector in header. num of sector:%d",
            /* ERRV_ID_NUM_OF_SECTOR        */ "[Disk%d] Mismatch number of sector in track %d and side %d.",
            /* ERRV_TOO_MANY_SECTORS        */ "[Disk%d] Too many sectors. Ignore sectors over %d. id[C:%d H:%d] num of sector:%d",
            /* ERRV_SHORT_SECTORS           */ "[Disk%d] Number of sector is less than %d. [track:%d side:%d] num of sector:%d",
            /* ERRV_SECTOR_SIZE_HEADER      */ "[Disk%d] Invalid sector size in header. sector size:%d",
            /* ERRV_SECTOR_SIZE_SECTOR      */ "[Disk%d] Invalid sector size in sector. id[C:%d H:%d R:%d N:%d] sector size:%d",
            /* ERRV_DUPLICATE_TRACK         */ "[Disk%d] Duplicate track %s and side %s. Side number change to %d.",
            /* ERRV_DUPLICATE_SECTOR        */ "[Disk%d] Duplicate sector %d. [track:%s side:%s]",
            /* ERRV_NO_SECTOR               */ "[Disk%d] No found sector %d. [track:%s side:%s]",
            /* ERRV_IGNORE_DATA             */ "[Disk%d] Deleted data found. This sector is ignored. id[C:%d H:%d R:%d]",
            /* ERRV_TOO_MANY_TRACKS         */ "[Disk%d] Too many tracks. Ignore tracks after %dth.",
            /* ERRV_UNSUPPORTED_TYPE        */ "[Disk%d] Data type %s is unsupported.",
            /* ERRV_END                     */ "Unknown error. code:%d"
    };

    /**
     * Formats and stores an error message.
     *
     * @param errorNumber the error code (index into {@code gDiskResultMsgs})
     * @param args        arguments used for {@link String#format}
     */
    @Override
    public void setMessageV(int errorNumber, Object... args) {
try {
        String msg;

        args = wrapArrayToString(args);

        if (errorNumber <= 0) {
            return;
        } else if (errorNumber < ERRV_START) {
            msg = gDiskResultMsgs[errorNumber];
        } else if (errorNumber < ERRV_END) {
            msg = String.format(gDiskResultMsgs[errorNumber], args);
        } else {
            msg = String.format(gDiskResultMsgs[ERRV_END], errorNumber);
        }
        if (!msg.isEmpty()) {
//logger.log(Level.TRACE, msg, new Exception("MESSAGE: " + msg));
            msgs.add(msg);
        }
} catch (Exception e) {
 logger.log(Level.ERROR, gDiskResultMsgs[errorNumber] + ", " + Arrays.toString(args));
 logger.log(Level.ERROR, e.getMessage(), e);
}
    }

    private static String anyArrayToString(Object array) {
        Class<?> c = array.getClass();
        if (!c.isArray()) return String.valueOf(array);

        if (c == int[].class) return Arrays.toString((int[]) array);
        if (c == long[].class) return Arrays.toString((long[]) array);
        if (c == double[].class) return Arrays.toString((double[]) array);
        if (c == float[].class) return Arrays.toString((float[]) array);
        if (c == char[].class) return Arrays.toString((char[]) array);
        if (c == byte[].class) return Arrays.toString((byte[]) array);
        if (c == short[].class) return Arrays.toString((short[]) array);
        if (c == boolean[].class) return Arrays.toString((boolean[]) array);

        // Object[] (may contain nested arrays → deepToString to be safe)
        return Arrays.deepToString((Object[]) array);
    }

    private static Object[] wrapArrayToString(Object... args) {
        return Arrays.stream(args).map(o -> o.getClass().isArray() ? anyArrayToString(o) : o).toArray();
    }
}
