/*
 * (c) Sasaji.  All rights reserved.
 */

package l3diskex;

import java.util.Arrays;


/*
 *  Public API container – all global functions are static members
 *  of this class.
 **/
public class Common {

    /**
     *  Constants – same names as in the C++ header.
     */
    public static final int _MAX_PATH = 260;
    public static final int DEFAULT_TEXTWIDTH = 160;

    /**
     *  Utility function – equivalent to snprintf / _snprintf.
     *  In Java we use String.format.
     */
    public static int mysnprintf(StringBuilder buf, int size, String format, Object... args) {
        String result = String.format(format, args);
        if (result.length() > size) {
            result = result.substring(0, size);
        }
        buf.setLength(0);
        buf.append(result);
        return result.length();
    }

    /*
     *  Low‑level byte buffer helpers
     **/

    /**  Right‑trim the buffer.
     *   @param buf  the byte array
     *   @param len  actual data length in the buffer
     *   @param ch   character to trim
     *   @return new length after trimming
     */
    public static int rtrim(byte[] buf, int len, byte ch) {
        int pos = len - 1;
        while (len > 0) {
            if (buf[pos] != 0 && buf[pos] != ch) break;
            buf[pos] = 0;
            len--;
            pos--;
        }
        return len;
    }

    /**  Find length up to specified character.
     *   @param buf  buffer
     *   @param len  buffer size
     *   @param ch   target character
     *   @return length up to ch (or len if not found)
     */
    public static int str_length(byte[] buf, int len, byte ch) {
        for (int i = 0; i < len; i++) {
            if (buf[i] == ch) {
                return i;
            }
        }
        return len;
    }

    /**  Invert each byte in the buffer.
     */
    public static void mem_invert(byte[] buf, int len) {
        for (int i = 0; i < len; i++) {
            buf[i] = (byte) (~buf[i]);
        }
    }

    /**  Shrink string at first null / line‑feed / carriage‑return.
     */
    public static int str_shrink(byte[] buf, int len) {
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

    /**  Copy source to destination, filling the rest with `fill`.
     */
    public static void mem_copy(byte[] src, int slen, byte fill, byte[] dst, int dlen) {
        int copyLen = Math.min(slen, dlen);
        // fill destination with fill
        Arrays.fill(dst, fill);
        // copy actual data
        System.arraycopy(src, 0, dst, 0, copyLen);
    }

    /**  Search buffer from end for character.
     *   @return index of the first occurrence from the end,
     *   or -1 if not found.
     */
    public static int mem_rchr(byte[] buf, int len, int ch) {
        for (int i = len - 1; i >= 0; i--) {
            if (buf[i] == (byte) ch) {
                return i;
            }
        }
        return -1;
    }

    /**  Pad trailing null bytes with `fill` until a non‑null byte is reached.
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

    /**  Convert ASCII lowercase to uppercase.
     */
    public static void to_upper(byte[] src, int len) {
        for (int i = 0; i < len; i++) {
            byte b = src[i];
            if (b >= 0x61 && b <= 0x7a) {            // 'a'..'z'
                src[i] = (byte) (b - 0x20);
            }
        }
    }
}
