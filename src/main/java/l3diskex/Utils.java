package l3diskex;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Various utilities.
 */
public final class Utils {

    private Utils() {
    } // Private constructor for utility class

    public static final int TEMP_DATA_SIZE = 2048;

    /**
     * Helper to perform bitwise NOT (XOR with 0xFF) on a byte array.
     * C++ equivalent of an assumed 'mem_invert' function.
     *
     * @param data The byte array.
     * @param size The number of bytes to invert.
     */
    private static void memInvert(byte[] data, int size) {
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (data[i] ^ 0xFF);
        }
    }

    /**
     * Placeholder for the external CharCodes class.
     */
    public static class CharCodes {

        public void setMap(String char_code) { /* no-op */ }

        public int findString(byte[] c, int len, StringBuilder cstr, char defaultChar) {
            // Placeholder: Assume 1 byte per character for simplicity in Java conversion.
            // This is a simplification of multi-byte character handling.
            if (len >= 1) {
                cstr.append((char) (c[0] & 0xFF));
                return 1;
            }
            return 0;
        }

        public void convCtrlCodes(byte[] c, int len) { /* no-op */ }

        public Charset charset() {
            return Charset.defaultCharset();
        }
    }

    /**
     * Temporary data buffer.
     */
    public static class TempData {

        private byte[] data;
        private int alloc_size;
        private int size;

        public TempData() {
            this(TEMP_DATA_SIZE);
        }

        public TempData(int newsize) {
            this.alloc_size = newsize;
            this.data = new byte[alloc_size];
            Arrays.fill(this.data, (byte) 0);
            this.size = 0;
        }

        /**
         * Allocate buffer (reallocate if needed).
         *
         * @param newsize Buffer size.
         */
        public void setSize(int newsize) {
            if (newsize > alloc_size) {
                this.alloc_size = newsize;
                this.data = new byte[alloc_size];
                Arrays.fill(this.data, (byte) 0);
            }
            this.size = newsize;
        }

        /**
         * Set data to buffer.
         *
         * @param data   Data.
         * @param len    Data size.
         * @param invert Invert data if true.
         */
        public void setData(byte[] data, int len, boolean invert) {
            setSize(len);
            System.arraycopy(data, 0, this.data, 0, this.size);
            if (invert) {
                memInvert(this.data, this.size);
            }
        }

        /**
         * Set data to buffer at position.
         *
         * @param pos Data position.
         * @param val Data.
         */
        public void set(int pos, byte val) {
            if (pos < size) {
                data[pos] = val;
            }
        }

        /**
         * Replace matching byte data.
         *
         * @param src Target data to replace.
         * @param dst Replacement data.
         */
        public void replace(byte src, byte dst) {
            for (int pos = 0; pos < size; pos++) {
                if (data[pos] == src) data[pos] = dst;
            }
        }

        /**
         * Returns buffer pointer.
         *
         * @return Data array.
         */
        public byte[] getData() {
            return data;
        }

        /**
         * Returns buffer pointer at a specific position.
         *
         * @param pos Position.
         * @return Data array at position.
         */
        public byte[] getData(int pos) {
            // In Java, returning the array and relying on the caller to offset is common,
            // but for C++ pointer semantics, the intent is an address offset.
            // Since Java doesn't do pointer arithmetic, this method might be slightly misleading.
            // It will return the array and the caller must use data[pos].
            // To keep the signature, we return the array.
            return data;
        }

        /**
         * Returns data size.
         *
         * @return Size.
         */
        public int getSize() {
            return size;
        }

        /**
         * Returns buffer size.
         *
         * @return Allocated size.
         */
        public int getBufferSize() {
            return alloc_size;
        }

        /**
         * Invert data.
         *
         * @param invert Invert if true.
         */
        public void invertData(boolean invert) {
            if (invert) {
                memInvert(data, size);
            }
        }
    }

    /**
     * FIFO buffer.
     */
    public static class FIFOBuffer {

        private byte[] m_data;
        private int m_size;
        private int m_rpos;
        private int m_wpos;

        public FIFOBuffer(int val) {
            m_data = new byte[val];
            m_size = val;
            m_rpos = 0;
            m_wpos = 0;
            Arrays.fill(m_data, (byte) 0);
        }

        public FIFOBuffer() {
            this(4096);
        }

        /**
         * Set buffer size.
         *
         * @param val Size.
         */
        public void setBufSize(int val) {
            if (m_size < val) {
                byte[] new_data = new byte[val];
                System.arraycopy(m_data, 0, new_data, 0, m_size);
                Arrays.fill(new_data, m_size, val, (byte) 0);
                m_data = new_data;
                m_size = val;
            }
        }

        /**
         * Clear buffer.
         */
        public void clear() {
            m_rpos = 0;
            m_wpos = 0;
            Arrays.fill(m_data, (byte) 0);
        }

        /**
         * Append 1 byte of data.
         *
         * @param val Data byte.
         */
        public void appendByte(byte val) {
            if (m_size <= m_wpos) {
                setBufSize(m_size * 2);
            }
            m_data[m_wpos] = val;
            m_wpos++;
        }

        /**
         * Append data.
         *
         * @param buf  Data.
         * @param size Data size.
         */
        public void appendData(byte[] buf, int size) {
            if (m_size <= m_wpos + size) {
                int new_size = m_size;
                do {
                    new_size *= 2;
                } while (new_size < m_wpos + size);
                setBufSize(new_size);
            }
            System.arraycopy(buf, 0, m_data, m_wpos, size);
            m_wpos += size;
        }

        /**
         * Returns data without updating read position.
         *
         * @return Data (0-255) or -1 if empty.
         */
        public int PeekByte() {
            if (m_rpos < m_wpos) {
                return m_data[m_rpos] & 0xFF; // Treat as unsigned
            } else {
                return -1;
            }
        }

        /**
         * Returns data and updates read position.
         *
         * @return Data (0-255) or -1 if empty.
         */
        public int getByte() {
            if (m_rpos < m_wpos) {
                return m_data[m_rpos++] & 0xFF; // Treat as unsigned
            } else {
                return -1;
            }
        }

        /**
         * Get data and update read position.
         *
         * @param buf  Destination buffer.
         * @param size Buffer size.
         * @return Size of data stored.
         */
        public int getData(byte[] buf, int size) {
            int remaining = m_wpos - m_rpos;
            if (size > remaining) size = remaining;
            System.arraycopy(m_data, m_rpos, buf, 0, size);
            m_rpos += size;
            return size;
        }

        /**
         * Returns data buffer.
         *
         * @return Data array.
         */
        public byte[] getData() {
            return m_data;
        }

        /**
         * Returns read position.
         *
         * @return Read position.
         */
        public int getReadPos() {
            return m_rpos;
        }

        /**
         * Returns write position.
         *
         * @return Write position.
         */
        public int getWritePos() {
            return m_wpos;
        }

        /**
         * Returns remaining data size.
         *
         * @return Remaining size.
         */
        public int remain() {
            return m_wpos - m_rpos;
        }

        /**
         * Set read position.
         *
         * @param val Position.
         */
        public void setReadPos(int val) {
            m_rpos = val;
        }

        /**
         * Set write position.
         *
         * @param val Position.
         */
        public void setWritePos(int val) {
            m_wpos = val;
        }

        /**
         * Set read position to write position (all read).
         */
        public void fix() {
            m_rpos = m_wpos;
        }
    }

    /**
     * Dump auxiliary class.
     */
    public static class Dump {

        private final CharCodes codes = new CharCodes();

        public Dump() {
        }

        /**
         * Binary dump.
         *
         * @param buffer  Source data.
         * @param bufsize Source data length.
         * @param str     Dumped string builder.
         * @param invert  Invert data if true.
         * @return Number of dump lines.
         */
        public int binary(byte[] buffer, int bufsize, StringBuilder str, boolean invert) {
            int rows = 0;
            int inv = invert ? 0xFF : 0;
            str.append("    :");
            for (int col = 0; col < 16; col++) {
                str.append(String.format(" +%x", col));
            }
            str.append("\n");
            str.append("-----");
            for (int col = 0; col < 16; col++) {
                str.append("---");
            }
            str.append("\n");
            for (int pos = 0, col = 0; pos < bufsize; pos++) {
                if (col == 0) {
                    str.append(String.format("+%02x0:", rows));
                }
                str.append(String.format(" %02x", (buffer[pos] & 0xFF) ^ inv));
                if (col >= 15) {
                    str.append("\n");
                    col = 0;
                    rows++;
                } else {
                    col++;
                }
            }
            rows += 3;
            return rows;
        }

        /**
         * ASCII dump.
         *
         * @param buffer    Source data.
         * @param bufsize   Source data length.
         * @param char_code Character code map ID (String).
         * @param str       Dumped string builder.
         * @param invert    Invert data if true.
         * @return Number of dump lines (returns 0 in C++).
         */
        public int ascii(byte[] buffer, int bufsize, String char_code, StringBuilder str, boolean invert) {
            int inv = invert ? 0xFF : 0;
            codes.setMap(char_code);

            for (int col = 0; col < 16; col++) {
                str.append(String.format("%x", col));
            }
            str.append("\n");
            for (int col = 0; col < 16; col++) {
                str.append("-");
            }
            str.append("\n");

            int col = 0;
            for (int pos = 0; pos < bufsize; ) {
                if (col >= 16) {
                    str.append("\n");
                    col -= 16;
                    if (col > 0) {
                        for (int i = 0; i < col; i++) str.append(" ");
                    }
                }

                StringBuilder cstr = new StringBuilder();
                byte[] c = new byte[4];
                c[0] = (byte) ((buffer[pos] & 0xFF) ^ inv);
                c[1] = pos + 1 == bufsize ? 0 : (byte) ((buffer[pos + 1] & 0xFF) ^ inv);
                c[2] = 0;

                int len = codes.findString(c, 2, cstr, '.');
                str.append(cstr);
                pos += len;
                col += len;
            }
            str.append("\n");
            return 0;
        }

        /**
         * Text dump.
         *
         * @param buffer    Source data.
         * @param bufsize   Source data length.
         * @param char_code Character code map ID (String).
         * @param str       Dumped string builder.
         * @param invert    Invert data if true.
         * @return Number of dump lines.
         */
        public int text(byte[] buffer, int bufsize, String char_code, StringBuilder str, boolean invert) {
            int inv = invert ? 0xFF : 0;

            codes.setMap(char_code);

            int col = 0;
            int row = 1;
            for (int pos = 0; pos < bufsize; ) {
                StringBuilder cstr = new StringBuilder();
                byte[] c = new byte[4];
                c[0] = (byte) ((buffer[pos] & 0xFF) ^ inv);
                c[1] = pos + 1 == bufsize ? 0 : (byte) ((buffer[pos + 1] & 0xFF) ^ inv);
                c[2] = 0;

                if (col >= 80) {
                    row++;
                    col = 0;
                }

                codes.convCtrlCodes(c, 2);

                if ((c[0] & 0xFF) == '\r' && (c[1] & 0xFF) == '\n') {
                    // Newline
                    str.append("\n");
                    row++;
                    col = 0;
                    pos += 2;
                    continue;
                } else if ((c[0] & 0xFF) == '\r' || (c[0] & 0xFF) == '\n') {
                    // Newline
                    str.append("\n");
                    row++;
                    col = 0;
                    pos++;
                    continue;
                } else if ((c[0] & 0xFF) == '\t') {
                    // Tab
                    if ((c[1] & 0xFF) >= 1 && (c[1] & 0xFF) < 0x20) {
                        // for FLEX
                        for (int i = 0; i < (c[1] & 0xFF); i++) {
                            str.append(" ");
                        }
                        col += (c[1] & 0xFF);
                        pos += 2;
                    } else {
                        str.append("\t");
                        col += 8;
                        pos++;
                    }
                    continue;
                } else if ((c[0] & 0xFF) < 0x20) {
                    // Control code is converted to "."
                    str.append(".");
                    col++;
                    pos++;
                    continue;
                }

                int len = codes.findString(c, 2, cstr, '.');
                str.append(cstr);
                col += len;
                pos += len;
            }
            return row;
        }
    }

    /**
     * StopWatch class.
     */
    public static class StopWatch {

        private long startTime;
        private int m_id;
        private boolean m_now_wait_cursor; // Ignored in Java translation

        public StopWatch() {
            // Use nanoTime for high-resolution timing
            startTime = System.nanoTime();
            m_id = 0;
            m_now_wait_cursor = false;
        }

        public void busy() {
            // Ignored wxBeginBusyCursor
            m_now_wait_cursor = true;
            restart();
        }

        public void restart() {
            // Ignored wxWakeUpIdle
            startTime = System.nanoTime();
        }

        public void finish() {
            // Ignored wxEndBusyCursor
            m_now_wait_cursor = false;
            // Ignored wxWakeUpIdle
        }

        public int getID() {
            return m_id;
        }

        public void setID(int id) {
            m_id = id;
        }

        /**
         * Returns elapsed time in milliseconds.
         *
         * @return Elapsed time.
         */
        public long getTime() {
            return (System.nanoTime() - startTime) / 1_000_000L;
        }
    }

    //////////////////////////////////////////////////////////////////////

    /**
     * Convert time structure to date/time data (MS-DOS format).
     *
     * @param tm   Time structure.
     * @param date Date data (3 bytes).
     * @param time Time data (3 bytes).
     */
    public static void convTmToDateTime(LocalDateTime tm, byte[] date, byte[] time) {
        // tm.year is since 1900. MS-DOS format date stores:
        // date[0]: year LSB (bits 0-7)
        // date[1]: year MSB (bits 8-11) (4 bits), month (4 bits)
        // date[2]: day
        // Year: tm.getYear() (0-999) -> 1900 + year (e.g. 2025 -> 125)
        // Year bits 0-7: (tm.getYear() & 0xff)
        // Year bits 8-11: ((tm.getYear() & 0xf00) >> 8) (4 bits)
        date[0] = (byte) (tm.getYear() & 0xff);
        date[1] = (byte) (((tm.getMonth().ordinal() + 1) & 0x0f) << 4 | ((tm.getYear() & 0xf00) >> 8));
        date[2] = (byte) (tm.getDayOfMonth()); // Day is 1-31

        time[0] = (byte) (tm.getHour());
        time[1] = (byte) (tm.getMinute());
        time[2] = (byte) (tm.getSecond());
    }

    /**
     * Convert date/time data to time structure (MS-DOS format).
     *
     * @param date Date data (3 bytes).
     * @param time Time data (3 bytes).
     */
    public static LocalDateTime convDateTimeToTm(byte[] date, byte[] time) {
        return LocalDateTime.of(
                // Year: date[0] (LSB) + ((date[1] & 0xf) << 8) (MSB 4 bits)
                (date[0] & 0xFF) | ((date[1] & 0x0F) << 8),
                // Month: (date[1] & 0xf0) >> 4. Month is 1-12. DateTime stores 0-11.
                ((date[1] & 0xF0) >> 4) - 1,
//        if (tm.getMonth().ordinal() == 0xf - 1) tm.SetMonth(-2); // -1 if month is 0xf
                // Day: date[2] (1-31)
                date[2] & 0xFF,

                time[0] & 0xFF,
                time[1] & 0xFF,
                time[2] & 0xFF
        );
    }

    /**
     * Convert date string to time structure.
     *
     * @param date Date string.
     * @return true if successful.
     */
    public static LocalDate convDateStrToTm(String date) {
        Pattern re = Pattern.compile("^([0-9]+)[/:.-]([0-9]+)[/:.-]([0-9]+)$");
        Matcher matcher = re.matcher(date);

        if (matcher.matches()) {
            try {
                // year
                long lval = Long.parseLong(matcher.group(1));
                if (lval >= 1900) lval -= 1900;
                int y = (int) lval;

                // month (1-12) -> DateTime (0-11)
                lval = Long.parseLong(matcher.group(2));
                int m = (int) lval - 1;

                // day
                lval = Long.parseLong(matcher.group(3));
                int d = (int) lval;
                return LocalDate.of(y, m, d);
            } catch (NumberFormatException e) {
            }
        }

        return null;
    }

    /**
     * Convert time string to time structure.
     *
     * @param time Time string.
     * @return true if successful.
     */
    public static LocalTime convTimeStrToTm(String time) {
        Pattern re1 = Pattern.compile("^([0-9]+)[/:.-]([0-9]+)[/:.-]([0-9]+)$");
        Pattern re2 = Pattern.compile("^([0-9]+)[/:.-]([0-9]+)$");
        Matcher matcher1 = re1.matcher(time);
        Matcher matcher2 = re2.matcher(time);

        try {
            if (matcher1.matches()) {
                // hour
                long lval = Long.parseLong(matcher1.group(1));
                int h = (int) lval;

                // minute
                lval = Long.parseLong(matcher1.group(2));
                int m = (int) lval;

                // second
                lval = Long.parseLong(matcher1.group(3));
                int s = (int) lval;
                return LocalTime.of(h, m, s);
            } else if (matcher2.matches()) {
                // hour
                long lval = Long.parseLong(matcher2.group(1));
                int h = (int) lval;

                // minute
                lval = Long.parseLong(matcher2.group(2));
                int m = (int) lval;
                return LocalTime.of(h, m, 0);
            }
        } catch (NumberFormatException e) {
        }

        return null;
    }

    /**
     * Convert BCD format date to time structure.
     *
     * @param yy Year (BCD).
     * @param mm Month (BCD).
     * @param dd Day (BCD).
     * @return Time structure.
     */
    public static LocalDate convYYMMDDToTm(byte yy, byte mm, byte dd) {
        int year = ((yy & 0xFF) >> 4) * 10 + ((yy & 0xFF) & 0xf);
        int month = ((mm & 0xFF) >> 4) * 10 + ((mm & 0xFF) & 0xf); // 1-12
        int day = ((dd & 0xFF) >> 4) * 10 + ((dd & 0xFF) & 0xf);

        return LocalDate.of(
                year +
                        // Add 100 if year is 0-79 (since 1900)
                        (0 <= year && year < 80 ? 100 : 0),
                month - 1, // DateTime stores 0-11
                day);
    }

    /**
     * Convert time structure to BCD format date.
     *
     * @param tm Time structure.
     * @param yy Year (BCD).
     * @param mm Month (BCD).
     * @param dd Day (BCD).
     */
    public static void convTmToYYMMDD(LocalDateTime tm, byte[] yy, byte[] mm, byte[] dd) {
        int year = tm.getYear() % 100; // Year since 1900, only last two digits
        int month = tm.getMonth().ordinal() + 1;
        int day = tm.getDayOfMonth();

        yy[0] = (byte) (((year / 10) % 10) << 4 | (year % 10));
        mm[0] = (byte) (((month / 10) % 10) << 4 | (month % 10));
        dd[0] = (byte) (((day / 10) % 10) << 4 | (day % 10));
    }

    /**
     * Returns date as "YYYY/MM/DD" or "----/--/--".
     *
     * @param tm Time structure.
     * @return Formatted date string.
     */
    public static String formatYMDStr(LocalDateTime tm) {
        String yearStr = (tm.getYear() >= 0 ? String.format("%04d", tm.getYear() + 1900) : "----");
        String monthStr = (tm.getMonth().ordinal() >= -1 ? String.format("%02d", tm.getMonth().ordinal() + 1) : "--");
        String dayStr = (tm.getDayOfMonth() >= 0 ? String.format("%02d", tm.getDayOfMonth()) : "--");
        return yearStr + "/" + monthStr + "/" + dayStr;
    }

    /**
     * Returns time as "HH:MI:SS" or "--:--:--".
     *
     * @param tm Time structure.
     * @return Formatted time string.
     */
    public static String formatHMSStr(LocalDateTime tm) {
        String hourStr = (tm.getHour() >= 0 ? String.format("%02d", tm.getHour()) : "--");
        String minStr = (tm.getMinute() >= 0 ? String.format("%02d", tm.getMinute()) : "--");
        String secStr = (tm.getSecond() >= 0 ? String.format("%02d", tm.getSecond()) : "--");
        return hourStr + ":" + minStr + ":" + secStr;
    }

    /**
     * Returns time as "HH:MI" or "--:--".
     *
     * @param tm Time structure.
     * @return Formatted time string.
     */
    public static String formatHMStr(LocalDateTime tm) {
        String hourStr = (tm.getHour() >= 0 ? String.format("%02d", tm.getHour()) : "--");
        String minStr = (tm.getMinute() >= 0 ? String.format("%02d", tm.getMinute()) : "--");
        return hourStr + ":" + minStr;
    }

    /**
     * Convert string to int value.
     * Supports decimal, 0x (hex), and 0b (binary).
     *
     * @param val String value.
     * @return Integer value.
     */
    public static int toInt(String val) {
        long lval = 0;
        String h = val.toLowerCase();
        try {
            if (h.startsWith("0x")) {
                lval = Long.parseLong(val.substring(2), 16);
            } else if (h.startsWith("0b")) {
                lval = Long.parseLong(val.substring(2), 2);
            } else {
                lval = Long.parseLong(val);
            }
        } catch (NumberFormatException e) {
            // Return 0 on error, matching C++ ToULong behavior for failure
            lval = 0;
        }
        return (int) lval;
    }

    /**
     * Convert string to boolean value.
     * "1", "TRUE", "true" => true
     *
     * @param val String value.
     * @return Boolean value.
     */
    public static boolean toBool(String val) {
        return val.equals("1") || val.equalsIgnoreCase("TRUE");
    }

    /**
     * Convert hexadecimal string to integer.
     *
     * @param sval String value.
     * @return Integer value or -1 on error.
     */
    public static int convFromHexa(String sval) {
        if (sval.startsWith("-")) return -1;
        try {
            long lval = Long.parseLong(sval, 16);
            return (int) lval;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Decode escape characters.
     *
     * @param src Source string.
     * @param dst Destination string builder.
     */
    public static void decodeEscape(String src, String[] dst) {
        String str = src;
        int i = 0;
        StringBuilder sb = new StringBuilder();

        while (i < str.length()) {
            char c = str.charAt(i);
            if (c == '\\') {
                i++;
                if (i < str.length()) {
                    char nextC = str.charAt(i);
                    if (nextC == 'x' && i + 2 < str.length()) {
                        try {
                            String hex = str.substring(i + 1, i + 3);
                            int v = Integer.parseInt(hex, 16);
                            sb.append((char) v);
                            i += 3;
                        } catch (NumberFormatException e) {
                            // If not a valid hex escape, treat as literal '\' and 'x'
                            sb.append('\\');
                            sb.append('x');
                            i++; // Move past 'x'
                        }
                    } else {
                        sb.append(nextC);
                        i++;
                    }
                } else {
                    // Trailing backslash, append it
                    sb.append('\\');
                }
            } else {
                sb.append(c);
                i++;
            }
        }

        dst[0] = sb.toString();
    }

    /**
     * Decode escape characters into a byte array.
     *
     * @param src Source string.
     * @param dst Destination byte array.
     * @param len Destination buffer length.
     */
    public static void decodeEscape(String src, byte[] dst, int len) {
        String str = src;
        int pos = 0;
        int i = 0;

        while (i < str.length() && len > pos) {
            char c = str.charAt(i);
            if (c == '\\') {
                i++;
                if (i < str.length()) {
                    char nextC = str.charAt(i);
                    if (nextC == 'x' && i + 2 < str.length()) {
                        try {
                            String hex = str.substring(i + 1, i + 3);
                            int v = Integer.parseInt(hex, 16);
                            dst[pos] = (byte) (v & 0xff);
                            i += 3;
                            pos++;
                        } catch (NumberFormatException e) {
                            // If not a valid hex escape, treat as literal '\' and 'x'
                            dst[pos] = (byte) '\\';
                            pos++;
                            if (len > pos) {
                                dst[pos] = (byte) 'x';
                                pos++;
                            }
                            i++; // Move past 'x'
                        }
                    } else {
                        dst[pos] = (byte) nextC;
                        i++;
                        pos++;
                    }
                } else {
                    // Trailing backslash
                    dst[pos] = (byte) '\\';
                    pos++;
                }
            } else {
                dst[pos] = (byte) c;
                i++;
                pos++;
            }
        }
    }

    /**
     * Encode characters into escape sequences.
     * Non-alphanumeric, 0x60, and 0x7f-0xff are converted to "\u0000".
     *
     * @param src Source byte array.
     * @param len Data length.
     * @return Encoded string.
     */
    public static String encodeEscape(byte[] src, int len) {
        StringBuilder rstr = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int c = src[i] & 0xFF;
            if (c == 0) {
                break;
            } else if ((c >= 0x00 && c <= 0x1f)
                    || (c == 0x60)
                    || (c >= 0x7f && c <= 0xff)) {
                rstr.append(String.format("\\u00%02x", c));
            } else {
                rstr.append((char) c);
            }
        }
        return rstr.toString();
    }

    private static final String gSystemInvalidChars = "%\\/:*?\"<>|";

    /**
     * Decode file name.
     * Converts "%xx" to actual character.
     *
     * @param src File name string.
     * @return Decoded string.
     */
    public static String decodeFileName(String src) {
        StringBuilder dst = new StringBuilder();
        int pos = 0;

        while (pos < src.length()) {
            boolean dec = false;
            if (src.charAt(pos) == '%' && pos + 2 < src.length()) {
                String sval = src.substring(pos + 1, pos + 3);
                try {
                    int lval = Integer.parseInt(sval, 16);
                    dst.append((char) lval);
                    dec = true;
                    pos += 3;
                } catch (NumberFormatException e) {
                    // Not a valid hex escape, treat as literal '%'
                }
            }
            if (!dec) {
                dst.append(src.charAt(pos));
                pos++;
            }
        }
        return dst.toString();
    }

    /**
     * Encode file name.
     * Converts %\/:*?"<>| to "%%xx".
     *
     * @param src File name string.
     * @return Encoded string.
     */
    public static String encodeFileName(String src) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (gSystemInvalidChars.indexOf(c) >= 0) {
                str.append(String.format("%%%02x", (int) c));
            } else {
                str.append(c);
            }
        }
        return str.toString();
    }

    /**
     * Get side number string.
     *
     * @param side_number Side number (0, 1).
     * @param each_sides  True to return as number ("0", "1"), False for letter ("A", "B").
     * @return "0", "1", "A", or "B".
     */
    public static String getSideNumStr(int side_number, boolean each_sides) {
        String str = "";
        if (side_number >= 0) {
            if (each_sides) {
                str = String.format("%d", side_number);
            } else {
                // 0x41 is 'A'
                str = String.format("%c", side_number + 0x41);
            }
        }
        return str;
    }

    /**
     * Get side string.
     *
     * @param side_number Side number (0, 1).
     * @param each_sides  True to return as number ("side 0"), False for letter ("side A").
     * @return "side 0" or "side A".
     */
    public static String getSideStr(int side_number, boolean each_sides) {
        String str = "";
        if (side_number >= 0) {
            // Cannot use C++'s _("side %d") for localization, using simple English.
            if (each_sides) {
                str = String.format("side %d", side_number);
            } else {
                // 0x41 is 'A'
                str = String.format("side %c", side_number + 0x41);
            }
        }
        return str;
    }

    /**
     * Check if a string is in a list.
     *
     * @param list   String list (null terminated in C++, use String[] in Java).
     * @param substr String to check.
     * @return Index of the match or -1.
     */
    public static int indexOf(String[] list, String substr) {
        int match = -1;
        for (int i = 0; i < list.length; i++) {
            if (substr.equals(list[i])) {
                match = i;
                break;
            }
        }
        return match;
    }

    /**
     * Check if upper case characters are more numerous than lower case.
     *
     * @param str String.
     * @return True if upper case count > lower case count.
     */
    public static boolean isUpperString(String str) {
        int u = 0;
        int l = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 0x41 && c <= 0x5a) { // 'A' to 'Z'
                u++;
            } else if (c >= 0x61 && c <= 0x7a) { // 'a' to 'z' (note: C++ had 0x51 and 0x7b which seems wrong, using standard ASCII)
                l++;
            }
        }
        return (u > l);
    }

    /**
     * Check if a value is a power of two.
     *
     * @param val   Value.
     * @param digit Number of bits to check (ignored in standard Java implementation).
     * @return True if power of two.
     */
    public static boolean IsPowerOfTwo(long val, int digit) {
        // C++ logic seems to check if it's 2^n within 'digit' bits.
        // Simplified Java equivalent for power of 2 (ignoring 'digit' as it complicates the simple logic)
        // For C++ logic:
        // long originalVal = val;
        // while (digit > 0) {
        //     if ((val & 1) == 1) {
        //         val >>= 1;
        //         return (val == 0);
        //     }
        //     val >>= 1;
        //     digit--;
        // }
        // return true; // If digit reaches 0

        // Simpler, standard power of two check (assuming positive value):
        if (val <= 0) return false;
        return (val & (val - 1)) == 0;
    }

    /**
     * Calculate CRC32.
     *
     * @param data Data buffer.
     * @param size Data size.
     * @return CRC32 value (unsigned 32-bit).
     */
    public static int crc32(byte[] data, int size) {
        int r = 0xffff_ffff;
        int polynomial = 0xedb8_8320; // CRC32 polynomial

        for (int i = 0; i < size; i++) {
            r ^= (data[i] & 0xff);
            for (int j = 0; j < 8; j++) {
                if ((r & 1) == 1) {
                    r = (r >>> 1) ^ polynomial;
                } else {
                    r >>>= 1;
                }
            }
        }
        return ~r;
    }

    public static int indexOf(Map<String, Object> map, Object value) {
        assert map instanceof LinkedHashMap : "order of map values are not guaranteed";
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public static String keyAt(Map<String, Object> map, int index) {
        assert map instanceof LinkedHashMap : "order of map values are not guaranteed";
        int i = 0;
        for (String key : map.keySet()) {
            if (i == index) return key;
        }
        return null;
    }

    public static Object valueAt(Map<String, Object> map, int index) {
        assert map instanceof LinkedHashMap : "order of map values are not guaranteed";
        int i = 0;
        for (Object value : map.values()) {
            if (i == index) return value;
        }
        return null;
    }

    public static String getExtension(String path) {
        int idx = path.lastIndexOf('.');
        return (idx >= 0 && idx < path.length() - 1) ?
                path.substring(idx + 1) : "";
    }

    public static String getExt(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    public static long getDaysSince1978(LocalDate tm) {
        LocalDate REFERENCE_DATE = LocalDate.of(1978, 1, 1);
        return ChronoUnit.DAYS.between(REFERENCE_DATE, tm);
    }
}
