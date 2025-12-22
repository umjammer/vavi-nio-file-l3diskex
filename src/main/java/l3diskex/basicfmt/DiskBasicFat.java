package l3diskex.basicfmt;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import vavi.util.StringUtil;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;


/**
 * FAT Access
 */
public class DiskBasicFat {

    private static final Logger logger = System.getLogger(DiskBasicFat.class.getName());

    /**
     * Usage Status Table
     */
    public static class DiskBasicAvailability {

        /**
         * Usage Status Table enum
         */
        public enum FatAvailability {
            FAT_AVAIL_FREE,
            FAT_AVAIL_SYSTEM,
            FAT_AVAIL_USED,
            FAT_AVAIL_USED_FIRST,
            FAT_AVAIL_USED_LAST,
            FAT_AVAIL_MISSING,
            FAT_AVAIL_LEAK,
            FAT_AVAIL_NULLEND
        }

        List<FatAvailability> list = new ArrayList<>();

        /** Free size */
        private int freeSize;
        /** Number of free groups */
        private int freeGroups;

        public DiskBasicAvailability() {
            super();
            freeSize = -1;
            freeGroups = -1;
        }

        /**
         * Initialization: set free size to 0
         */
        public void clear() {
            list.clear();
            freeSize = 0;
            freeGroups = 0;
        }

        /**
         * Initialization: set free size to 0
         */
        public void empty() {
            list.clear();
            freeSize = 0;
            freeGroups = 0;
        }

        /**
         * Initialization: set free size to -1
         */
        public void emptyInit() {
            list.clear();
            freeSize = -1;
            freeGroups = -1;
        }

        /**
         * @param val   Value
         * @param size  Free size
         * @param group Number of free groups
         *              Append
         */
        public void add(FatAvailability val, int size, int group) {
            list.add(val);
            freeSize += size;
            freeGroups += group;
        }

        /**
         * @param idx Position
         * @param val Value
         *            Set (Safety)
         */
        public void set(int idx, FatAvailability val) {
            if (idx < list.size()) {
                list.set(idx, val);
            }
        }

        /**
         * @param index Position
         * @return Value
         * Get (Safety)
         */
        public final FatAvailability get(int index) {
            FatAvailability val = FAT_AVAIL_FREE;
            if (index < list.size()) {
                val = list.get(index);
            }
            return val;
        }

        // Size is int in Java, but for internal use, we map Count() to size()
        public int count() {
            return list.size();
        }

        /**
         * Returns free size
         */
        public int getFreeSize() {
            return freeSize;
        }

        /**
         * Returns number of free groups
         */
        public int getFreeGroups() {
            return freeGroups;
        }

        /**
         * Set free size
         */
        public void setFreeSize(int val) {
            freeSize = val;
        }

        /**
         * Set number of free groups
         */
        public void setFreeGroups(int val) {
            freeGroups = val;
        }

        public int size() {
            return list.size();
        }
    }

    /**
     * Bit ON/OFF buffer (single)
     *
     * @see DiskBasicBitMLMap
     */
    public static class BitMLBuffer {

        protected byte[] buffer;
        protected int size;

        public BitMLBuffer() {
            buffer = null;
            size = 0;
        }

        public BitMLBuffer(byte[] buffer, int size) {
            this.buffer = buffer;
            this.size = size;
        }

        /**
         * Change bit at specified position
         *
         * @param num Bit position
         * @param val true: set / false: reset
         */
        public void modify(int num, boolean val) {
            int pos = num >> 3;
            int bit = num & 7;
            if (buffer != null && pos < size) {
                if (val) {
                    buffer[pos] |= (byte) (0x80 >> bit);
                } else {
                    buffer[pos] &= (byte) ~(0x80 >> bit);
                }
            }
        }

        /**
         * Whether bit at specified position is set
         *
         * @param num Bit position
         * @return true: set / false: reset
         */
        public boolean isSet(int num) {
            int pos = num >> 3;
            int bit = num & 7;
            if (buffer != null && pos < size) {
                return ((buffer[pos] & (0x80 >> bit)) != 0);
            }
            return false;
        }

        /**
         * Calculate bit position of specified bit position
         *
         * @param num Bit position
         * @param pos Buffer position
         * @param bit Bit
         */
        public void getPos(int num, int[] pos, int[] bit) {
            pos[0] = num >> 3;
            bit[0] = num & 7;
        }

        /**
         * Returns buffer
         */
        public byte[] getBuffer() {
            return buffer;
        }

        /**
         * Returns size (number of bits)
         */
        public int getBitSize() {
            return size << 3;
        }

        /**
         * Returns buffer size
         */
        public int getSize() {
            return size;
        }
    }

    /**
     * Bit ON/OFF map: array of BitMLBuffer
     */
    public static class DiskBasicBitMLMap {

        public List<BitMLBuffer> list = new ArrayList<>();

        public DiskBasicBitMLMap() {
        }

        /**
         * Set pointer
         */
        public void addBuffer(byte[] buffer, int size) {
            list.add(new BitMLBuffer(buffer, size));
        }

        /**
         * @param group_num Position
         * @param val       true: set / false: reset
         *                  Change bit at specified position
         */
        public void modify(int group_num, boolean val) {
            for (int idx = 0; idx < size(); idx++) {
                BitMLBuffer item = get(idx);
                int size = item.getBitSize();
                if (group_num < size) {
                    item.modify(group_num, val);
                    break;
                }
                group_num -= size;
            }
        }

        /**
         * Whether specified position is free
         *
         * @param group_num Position
         * @return true: set / false: reset
         */
        public boolean isSet(int group_num) {
            boolean val = false;
            for (int idx = 0; idx < size(); idx++) {
                BitMLBuffer item = get(idx);
                int size = item.getBitSize();
                if (group_num < size) {
                    val = item.isSet(group_num);
                    break;
                }
                group_num -= size;
            }
            return val;
        }

        /**
         * Calculate position within buffer from specified position
         *
         * @param group_num Position
         * @param idx       MAP position
         * @param pos       Buffer position in MAP (byte)
         * @param bit       Bit position
         * @return true / false overflow
         */
        public boolean getPosInMap(int group_num, int[] idx, int[] pos, int[] bit) {
            boolean valid = false;
            for (idx[0] = 0; idx[0] < size(); idx[0]++) {
                BitMLBuffer item = get(idx[0]);
                int size = item.getBitSize();
                if (group_num < size) {
                    item.getPos(group_num, pos, bit);
                    valid = true;
                    break;
                }
                group_num -= size;
            }
            return valid;
        }

        public BitMLBuffer get(int i) {
            return list.get(i);

        }

        public int size() {
            return list.size();
        }
    }

    /**
     * Holds pointer to FAT area (sector)
     */
    public static class DiskBasicFatBuffer {

        /** Buffer size */
        private final int size;
        /** Buffer pointer (start pointer within sector) */
        private final byte[] buffer;
        /** Offset within buffer */
        private final int offset;

        public DiskBasicFatBuffer() {
            size = 0;
            buffer = null;
            offset = 0;
        }

        public DiskBasicFatBuffer(byte[] buffer, int offset, int size) {
            this.buffer = buffer;
            this.offset = offset;
            this.size = size;
        }

        /**
         * Returns buffer pointer
         */
        public byte[] getBuffer() {
            return buffer;
        }

        /**
         * Returns buffer size
         */
        public int getSize() {
            return size;
        }

        /**
         * Fill buffer with specified code
         *
         * @param code Code
         */
        public void fill(byte code) {
            if (buffer != null) {
                Arrays.fill(buffer, 0, size, code);
            }
        }

        /**
         * Copy to buffer
         *
         * @param buf Buffer
         * @param len Size
         */
        public void copy(byte[] buf, int len) {
            if (buffer != null) {
                int copyLen = Math.min(len, size);
                System.arraycopy(buf, 0, buffer, 0, copyLen);
            }
        }

        /**
         * Returns data at specified position (8 bits)
         *
         * @param pos Position (unit of 8 bits)
         * @return Value
         */
        public int get(int pos) {
            return buffer != null ? (buffer[offset + pos] & 0xff) : INVALID_GROUP_NUMBER;
        }

        /**
         * Set data at specified position (8 bits)
         *
         * @param pos Position (unit of 8 bits)
         * @param val Value
         */
        public void set(int pos, int val) {
            if (buffer != null) {
                buffer[offset + pos] = (byte) (val & 0xff);
            }
        }

        /**
         * Set/reset bit at specified position
         *
         * @param pos    Position (unit of 8 bits)
         * @param mask   Target bit
         * @param val    Set/reset
         * @param invert Whether to invert
         * @return Whether processed
         */
        public boolean bit(int pos, int mask, boolean val, boolean invert) {
            if (pos >= size) return false;

            int bit = get(pos); // Get returns 0-255
            if (invert) bit ^= 0xff;
            bit = (val ? (bit | (mask & 0xff)) : (bit & ~(mask & 0xff)));
            if (invert) bit ^= 0xff;
            set(pos, bit);
            return true;
        }

        /**
         * Whether bit at specified position is ON
         *
         * @param pos    Position (unit of 8 bits)
         * @param mask   Target bit
         * @param invert Whether to invert
         * @return Bit is ON
         */
        public boolean bitTest(int pos, byte mask, boolean invert) {
            if (pos >= size) return false;

            int bit = get(pos); // Get returns 0-255
            if (invert) bit ^= 0xff;
            return (bit & (mask & 0xff)) != 0;
        }

        /**
         * Returns data at specified position (16 bits, little endian)
         *
         * @param pos Position (unit of 8 bits)
         * @return Value
         */
        public int get16LE(int pos) {
            if (buffer == null || pos + 1 >= size) return INVALID_GROUP_NUMBER;
            int b0 = buffer[offset + pos] & 0xff;
            int b1 = buffer[offset + pos + 1] & 0xff;
            return (b1 << 8) | b0;
        }

        /**
         * Set data at specified position (16 bits, little endian)
         *
         * @param pos Position (unit of 8 bits)
         * @param val Value
         */
        public void set16LE(int pos, int val) {
            if (buffer != null && pos + 1 < size) {
                buffer[offset + pos] = (byte) (val & 0xff);
                buffer[offset + pos + 1] = (byte) ((val >> 8) & 0xff);
            }
        }

        /**
         * Returns data at specified position (16 bits, big endian)
         *
         * @param pos Position (unit of 8 bits)
         * @return Value
         */
        public int get16BE(int pos) {
            if (buffer == null || pos + 1 >= size) return INVALID_GROUP_NUMBER;
            int b0 = buffer[offset + pos] & 0xff;
            int b1 = buffer[offset + pos + 1] & 0xff;
            return (b0 << 8) | b1;
        }

        /**
         * Set data at specified position (16 bits, big endian)
         *
         * @param pos Position (unit of 8 bits)
         * @param val Value
         */
        public void Set16BE(int pos, int val) {
            if (buffer != null && pos + 1 < size) {
                buffer[offset + pos] = (byte) ((val >> 8) & 0xff);
                buffer[offset + pos + 1] = (byte) (val & 0xff);
            }
        }

        //
        // DiskBasicFatBuffers
        //

        /**
         * Returns 8-bit data
         *
         * @param pos Position (unit of 8 bits)
         * @return Value
         */
        public static int getData8(List<DiskBasicFatBuffer> list, int pos) {
            int val = INVALID_GROUP_NUMBER;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    val = buf.get(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * Set 8-bit data
         *
         * @param pos Position (unit of 8 bits)
         * @param val Value
         */
        public static void setData8(List<DiskBasicFatBuffer> list, int pos, int val) {
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    buf.set(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }

        /**
         * Whether 8-bit data matches
         *
         * @param pos Position (unit of 8 bits)
         * @param val Value
         * @return true: match
         */
        public static boolean matchData8(List<DiskBasicFatBuffer> list, int pos, int val) {
            boolean match = false;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    match = (buf.get(pos) == val);
                    break;
                }
                pos -= buf.getSize();
            }
            return match;
        }

        /**
         * Set/reset bit of 8-bit data
         *
         * @param pos    Position (unit of 8 bits)
         * @param mask   Target bit
         * @param val    Set/reset
         * @param invert Whether to invert
         * @return Whether processed
         */
        public static boolean bitData8(List<DiskBasicFatBuffer> list, int pos, byte mask, boolean val, boolean invert) {
            boolean processed = false;
            for (DiskBasicFatBuffer buf : list) {
                processed = buf.bit(pos, mask, val, invert);
                if (processed) break;
                pos -= buf.getSize();
            }
            return processed;
        }

        /**
         * Returns 12-bit data (little endian)
         *
         * @param pos Position (unit of 12 bits)
         * @return Value
         */
        public static int getData12LE(List<DiskBasicFatBuffer> list, int pos) {
            int val = INVALID_GROUP_NUMBER;
            boolean odd = (pos & 1) != 0;
            pos = pos * 3 / 2;
            int cnt = 0;
            for (int i = 0; i < list.size() && cnt < 2; i++) {
                DiskBasicFatBuffer buf = list.get(i);
                while (pos < buf.getSize() && cnt < 2) {
                    int tmp = buf.get(pos);
                    if (cnt == 0) {
                        val = odd ? tmp >> 4 : tmp;
                    } else {
                        val |= odd ? tmp << 4 : (tmp & 0x0f) << 8;
                    }
                    pos++;
                    cnt++;
                }
                pos -= buf.getSize();
            }
            if (cnt != 2) val = INVALID_GROUP_NUMBER;
            return val & 0xfff;
        }

        /**
         * Set 12-bit data (little endian)
         *
         * @param pos Position (unit of 12 bits)
         * @param val Value
         */
        public static void setData12LE(List<DiskBasicFatBuffer> list, int pos, int val) {
            boolean odd = ((pos & 1) != 0);
            pos = pos * 3 / 2;
            int cnt = 0;
            for (int i = 0; i < list.size() && cnt < 2; i++) {
                DiskBasicFatBuffer buf = list.get(i);
                while (pos < buf.getSize() && cnt < 2) {
                    int tmp = buf.get(pos);
                    if (cnt == 0) {
                        tmp = odd ? ((val & 0x0f) << 4) | (tmp & 0x0f) : (val & 0xff);
                    } else {
                        tmp = odd ? (val >> 4) & 0xff : ((val >> 8) & 0x0f) | (tmp & 0xf0);
                    }
                    buf.set(pos, tmp);
                    pos++;
                    cnt++;
                }
                pos -= buf.getSize();
            }
        }

        /**
         * Returns 16-bit data (little endian)
         *
         * @param pos Position (unit of 16 bits)
         * @return Value
         */
        public static int getData16LE(List<DiskBasicFatBuffer> list, int pos) {
            int val = INVALID_GROUP_NUMBER;
            pos *= 2;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    val = buf.get16LE(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * Set 16-bit data (little endian)
         *
         * @param pos Position (unit of 16 bits)
         * @param val Value
         */
        public static void setData16LE(List<DiskBasicFatBuffer> list, int pos, int val) {
            pos *= 2;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    buf.set16LE(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }

        /**
         * Returns 16-bit data (big endian)
         *
         * @param pos Position (unit of 16 bits)
         * @return Value
         */
        public static int getData16BE(List<DiskBasicFatBuffer> list, int pos) {
            int val = INVALID_GROUP_NUMBER;
            pos *= 2;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    val = buf.get16BE(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * Set 16-bit data (big endian)
         *
         * @param pos Position (unit of 16 bits)
         * @param val Value
         */
        public static void setData16BE(List<DiskBasicFatBuffer> list, int pos, int val) {
            pos *= 2;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    buf.Set16BE(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }
    }

    /**
     * FAT buffer (including mirroring): array of DiskBasicFatBuffers
     */
    public static class DiskBasicFatArea {

        List<List<DiskBasicFat.DiskBasicFatBuffer>> list = new ArrayList<>();

        /** Number of valid buffers */
        private int validCount;

        public DiskBasicFatArea() {
            validCount = list.size();
        }

        /**
         * Assignment
         */
        public DiskBasicFatArea operatorAssign(DiskBasicFatArea src) {
            list.clear();
            list.addAll(src.list);
            validCount = src.validCount;
            return this;
        }

        /**
         * Clear
         */
        public void empty() {
            list.clear();
            validCount = 0;
        }

        /**
         * Append
         *
         * @param lItem   Item to append
         * @param nInsert Number to append
         */
        public void add(List<DiskBasicFatBuffer> lItem, int nInsert) {
            for (int i = 0; i < nInsert; i++) {
                list.add(lItem);
            }
            validCount = list.size();
        }

        public void add(List<DiskBasicFatBuffer> lItem) {
            add(lItem, 1);
        }

        /**
         * Set number of valid buffers
         */
        public void setValidCount(int val) {
            validCount = val;
        }

        /**
         * Returns number of valid buffers
         */
        public int getValidCount() {
            return validCount;
        }

        /**
         * Returns 8-bit data
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 8 bits)
         * @return Value
         */
        public int getData8(int idx, int pos) {
            int val = INVALID_GROUP_NUMBER;
            if (idx >= list.size()) return val;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            val = DiskBasicFatBuffer.getData8(bufs, pos);
            return val;
        }

        /**
         * Set 8-bit data
         *
         * @param pos Position (unit of 8 bits)
         * @param val Value
         */
        public void setData8(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData8(n, pos, val);
            }
        }

        /**
         * Set 8-bit data
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 8 bits)
         * @param val Value
         */
        public void setData8(int idx, int pos, int val) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.setData8(bufs, pos, val);
        }

        /**
         * Whether 8-bit data matches
         *
         * @param pos Position (unit of 8 bits)
         * @param val Value
         * @return Number of matches (for multiple mirroring)
         */
        public int matchData8(int pos, int val) {
            int matchCount = 0;
            for (int n = 0; n < getValidCount(); n++) {
                if (matchData8(n, pos, val)) matchCount++;
            }
            return matchCount;
        }

        /**
         * Whether 8-bit data matches
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 8 bits)
         * @param val Value
         * @return true: matched
         */
        public boolean matchData8(int idx, int pos, int val) {
            if (idx >= list.size()) return false;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            return DiskBasicFatBuffer.matchData8(bufs, pos, val);
        }

        /**
         * Set/reset bit of 8-bit data
         *
         * @param idx    Mirroring position
         * @param pos    Position (unit of 8 bits)
         * @param mask   Target bit
         * @param val    Set/reset
         * @param invert Whether to invert
         */
        public void bitData8(int idx, int pos, byte mask, boolean val, boolean invert) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.bitData8(bufs, pos, mask, val, invert);
        }

        /**
         * Returns 12-bit data (little endian)
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 12 bits)
         * @return Value
         */
        public int getData12LE(int idx, int pos) {
            int val = INVALID_GROUP_NUMBER;
            if (idx >= list.size()) return val;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            val = DiskBasicFatBuffer.getData12LE(bufs, pos);
            return val;
        }

        /**
         * Set 12-bit data (little endian)
         *
         * @param pos Position (unit of 12 bits)
         * @param val Value
         */
        public void setData12LE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData12LE(n, pos, val);
            }
        }

        /**
         * Set 12-bit data (little endian)
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 12 bits)
         * @param val Value
         */
        public void setData12LE(int idx, int pos, int val) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.setData12LE(bufs, pos, val);
        }

        /**
         * Returns 16-bit data (little endian)
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 16 bits)
         * @return Value
         */
        public int getData16LE(int idx, int pos) {
            int val = INVALID_GROUP_NUMBER;
            if (idx >= list.size()) return val;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            val = DiskBasicFatBuffer.getData16LE(bufs, pos);
            return val;
        }

        /**
         * Set 16-bit data (little endian)
         *
         * @param pos Position (unit of 16 bits)
         * @param val Value
         */
        public void setData16LE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData16LE(n, pos, val);
            }
        }

        /**
         * Set 16-bit data (little endian)
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 16 bits)
         * @param val Value
         */
        public void setData16LE(int idx, int pos, int val) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.setData16LE(bufs, pos, val);
        }

        /**
         * Returns 16-bit data (big endian)
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 16 bits)
         * @return Value
         */
        public int getData16BE(int idx, int pos) {
            int val = INVALID_GROUP_NUMBER;
            if (idx >= list.size()) return val;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            val = DiskBasicFatBuffer.getData16BE(bufs, pos);
            return val;
        }

        /**
         * Set 16-bit data (big endian)
         *
         * @param pos Position (unit of 16 bits)
         * @param val Value
         */
        public void setData16BE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData16BE(n, pos, val);
            }
        }

        /**
         * Set 16-bit data (big endian)
         *
         * @param idx Mirroring position
         * @param pos Position (unit of 16 bits)
         * @param val Value
         */
        public void setData16BE(int idx, int pos, int val) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.setData16BE(bufs, pos, val);
        }

        public int size() {
            return list.size();
        }

        public List<DiskBasicFatBuffer> get(int i) {
            return list.get(i);
        }
    }

    private DiskBasic basic;
    private DiskBasicType type;
    /** Number of FATs */
    private int count;
    /** Number of FATs being used */
    private int vCount;
    /** FAT size (number of sectors) */
    private int size;
    /** Start sector number */
    private int start;
    /** Start position */
    private int startPos;

    private final DiskBasicFatArea bufs = new DiskBasicFatArea();

    public DiskBasicFat(DiskBasic basic) {
        this.basic = basic;
        type = null;
        clear();
    }

    /**
     * Assign FAT area
     *
     * @param isFormatting Whether during formatting
     * @return 1.0: normal, <1.0: warning exists, <0.0: error exists
     */
    public double assign(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        int sectorNum = basic.getFatStartSector();
        int sideNum = basic.getReversedSideNumber(basic.getFatSideNumber());

        bufs.empty();

        type = basic.getType();

        if (sectorNum >= 0) {
            DiskImageTrack managed_track;
            int[] sideNums = new int[] {sideNum};
            int[] sectorNums = new int[] {sectorNum};

            if (sideNum >= 0) {
                // Calculate from track and side numbers
                managed_track = basic.getTrack(basic.getManagedTrackNumber(), sideNum);
            } else {
                // Calculate by serial number of sector numbers
                managed_track = basic.getManagedTrack(basic.getReservedSectors(), sideNums, sectorNums);
            }
            if (managed_track == null) {
                return -1.0;
            }

            sideNum = sideNums[0];
            sectorNum = sectorNums[0];

            // Get sector position
            start = type.getSectorPosFromNum(basic.getManagedTrackNumber(), sideNum, sectorNum, 0, 1);

            count = basic.getNumberOfFats();
            size = basic.getSectorsPerFat();
            startPos = basic.getFatStartPos();
            vCount = basic.getValidNumberOfFats();
            if (vCount < 0) {
                vCount = count;
            }

            type.calcManagedStartGroup();

            // set buffer pointer for useful accessing
            int startSector = start;
            int endSector = start + size - 1;
            for (int fatNum = 0; fatNum < count && validRatio >= 0.0; fatNum++) {
                List<DiskBasicFatBuffer> fatBufs = new ArrayList<>();
                for (int secNum = startSector; secNum <= endSector; secNum++) {
                    int[] divNum = new int[] {0};
                    int[] divNums = new int[] {1};
                    DiskImageSector sector = basic.getSectorFromSectorPos(secNum, divNum, divNums);
//logger.log(Level.TRACE, "fat sector: " + secNum/* + "\n" + StringUtil.getDump(sector.getSectorBuffer(0), sector.getSize())*/);
                    if (sector == null) {
                        validRatio = -1.0;
logger.log(Level.TRACE, "sector is null");
                        break;
                    }

                    int sSize = sector.getSectorSize();
                    byte[] buf = sector.getSectorBuffer();
                    int offset = (sSize / divNums[0]) * divNum[0];
                    sSize /= divNums[0];

//                    int sectorSize = sSize;
                    if (secNum == startSector) {
                        // Starting position is shifted only for the first sector
                        offset += startPos;
                        sSize -= startPos;
                    }
                    DiskBasicFatBuffer fatbuf = new DiskBasicFatBuffer(buf, offset, sSize);
                    fatBufs.add(fatbuf);
                }
                bufs.add(fatBufs);

                startSector += size;
                endSector += size;
            }

            bufs.setValidCount(vCount);
        }

        if (validRatio >= 0.0) {
            validRatio = type.checkFat(isFormatting);
        }

        return validRatio;
    }

    /** Release assignment of FAT area */
    public void clear() {
        count = 0;
        vCount = 0;
        size = 0;
        start = 0;
        startPos = 0;

        bufs.list.clear();
    }

    /** Release assignment of FAT area */
    public void empty() {
        clear();
    }

    /**
     * Get data at specified position in the first sector of the FAT area
     *
     * @param pos Position
     */
    public int get(int pos) {
        int code = 0;
        DiskImageSector sector = basic.getSectorFromSectorPos(start);
        if (sector != null) {
            byte[] buf = sector.getSectorBuffer();
            int size = sector.getSectorBufferSize();
            if (buf != null && pos < size) {
                code = buf[pos] & 0xFF;
            }
        }
        return code;
    }

    /**
     * Write data to specified position in the first sector of the FAT area
     *
     * @param pos  Position
     * @param code Code
     */
    public void set(int pos, byte code) {
        int start_sector = start;
        for (int fat_num = 0; fat_num < vCount; fat_num++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(start_sector);
            if (sector != null) {
                byte[] buf = sector.getSectorBuffer();
                int size = sector.getSectorBufferSize();
                if (buf != null && pos < size) {
                    buf[pos] = code;
                }
            }
            start_sector += size;
        }
    }

    /**
     * Write data to the first sector of the FAT area
     *
     * @param buf Buffer
     * @param len Size
     */
    public void copy(byte[] buf, int len) {
        int startSector = start;
        for (int fatNum = 0; fatNum < vCount; fatNum++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(startSector);
            if (sector != null) {
                sector.copy(buf, len);
            }
            startSector += size;
        }
    }

    /**
     * Fill the FAT area with specified code
     *
     * @param code Code
     */
    public void fill(byte code) {
        int startSector = start;
        int endSector = start + size - 1;
        for (int fatNum = 0; fatNum < count; fatNum++) {
            for (int secNum = startSector; secNum <= endSector; secNum++) {
                DiskImageSector sector = basic.getSectorFromSectorPos(secNum);
                if (sector != null) {
                    sector.fill(code);
                }
            }
            startSector += size;
            endSector += size;
        }
    }

    /**
     * Returns FAT area
     */
    public DiskBasicFatArea getDiskBasicFatArea() {
        return bufs;
    }

    /**
     * Returns FAT buffer
     *
     * @param idx Index when mirrored
     */
    public List<DiskBasicFatBuffer> getDiskBasicFatBuffers(int idx) {
        if (idx >= bufs.list.size()) {
            return null;
        }
        return bufs.list.get(idx);
    }

    /**
     * Returns FAT buffer (sector)
     *
     * @param idx    Index when mirrored
     * @param subidx Buffer position
     */
    public DiskBasicFatBuffer getDiskBasicFatBuffer(int idx, int subidx) {
        List<DiskBasicFatBuffer> fatBufs = getDiskBasicFatBuffers(idx);
        if (fatBufs == null || subidx >= fatBufs.size()) {
            return null;
        }
        return fatBufs.get(subidx);
    }
}
