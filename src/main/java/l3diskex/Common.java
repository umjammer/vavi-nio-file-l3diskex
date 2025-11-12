/*
 * (c) Sasaji.  All rights reserved.
 */

package l3diskex;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import static l3diskex.basicfmt.DiskBasicTemplates.gDiskBasicTemplates;
import static l3diskex.diskimg.DiskParam.gDiskTemplates;
import static l3diskex.diskimg.FileParam.fileTypes;


/**
 * Public API container
 */
public class Common {

    /**
     * Right‑trim the buffer.
     *
     * @param buf the byte array
     * @param len actual data length in the buffer
     * @param ch  character to trim
     * @return new length after trimming
     */
    public static int trimRight(byte[] buf, int len, byte ch) {
        int pos = len - 1;
        while (len > 0) {
            if (buf[pos] != 0 && buf[pos] != ch) break;
            buf[pos] = 0;
            len--;
            pos--;
        }
        return len;
    }

    /**
     * Find length up to specified character.
     *
     * @param buf buffer
     * @param len buffer size
     * @param ch  target character
     * @return length up to ch (or len if not found)
     */
    public static int getStringLength(byte[] buf, int len, byte ch) {
        for (int i = 0; i < len; i++) {
            if (buf[i] == ch) {
                return i;
            }
        }
        return len;
    }

    /**
     * Invert each byte in the buffer.
     */
    public static void invertMemory(byte[] buf, int len) {
        for (int i = 0; i < len; i++) {
            buf[i] = (byte) (~buf[i]);
        }
    }

    /**
     * Shrink string at first null / line‑feed / carriage‑return.
     */
    public static int shrinkString(byte[] buf, int len) {
        int pos = 0;
        while (pos < len) {
            byte b = buf[pos];
            if (b == 0 || b == 0x0a || b == 0x0d) {
                buf[pos] = 0;
                break;
            }
            pos++;
        }
        return pos;
    }

    /**
     * Copy source to destination, filling the rest with `fill`.
     */
    public static void copyMemory(byte[] src, int slen, byte fill, byte[] dst, int dlen) {
        int copyLen = Math.min(slen, dlen);
        // fill destination with fill
        Arrays.fill(dst, fill);
        // copy actual data
        System.arraycopy(src, 0, dst, 0, copyLen);
    }

    /**
     * Search buffer from end for character.
     *
     * @return index of the first occurrence from the end,
     * or -1 if not found.
     */
    public static int mem_rchr(byte[] buf, int len, int ch) {
        for (int i = len - 1; i >= 0; i--) {
            if (buf[i] == (byte) ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Pad trailing null bytes with `fill` until a non‑null byte is reached.
     */
    public static int padding(byte[] buf, int len, byte fill) {
        int pos = len - 1;
        while (len > 0) {
            if (buf[pos] != 0) break;
            buf[pos] = fill;
            len--;
            pos--;
        }
        return len;
    }

    /**
     * Convert ASCII lowercase to uppercase.
     */
    public static void toUpper(byte[] src, int len) {
        for (int i = 0; i < len; i++) {
            byte b = src[i];
            if (b >= 0x61 && b <= 0x7a) { // 'a'..'z'
                src[i] = (byte) (b - 0x20);
            }
        }
    }

    // set locale search path and catalog name
    // TODO do it in its own class
    public static void init() {
        String resPath = System.getProperty("user.dir") + File.separator + "src/main/resources" + File.separator;

        String localeName = Locale.getDefault().getLanguage();

        // load xml
        StringBuilder errmsgs = new StringBuilder();
        if (!gDiskTemplates.load(resPath + "data/", localeName, errmsgs)) {
            throw new IllegalStateException("Cannot load disk types data file. " + errmsgs);
        }
        if (!gDiskBasicTemplates.load(resPath + "data/", localeName, errmsgs)) {
            throw new IllegalStateException("Cannot load disk basic types data file. " + errmsgs);
        }
        if (!CharCodes.load(resPath + "data/", localeName, errmsgs)) {
            throw new IllegalStateException("Cannot load char codes data file. " + errmsgs);
        }
        if (!fileTypes.load(resPath + "data/", localeName, errmsgs)) {
            throw new IllegalStateException("Cannot load file types data file. " + errmsgs);
        }
    }
}
