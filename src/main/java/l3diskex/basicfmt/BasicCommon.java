/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import l3diskex.Common;
import vavi.util.ByteUtil;


/** disk basic common */
public class BasicCommon {

    /**
     * Common attribute flags
     */
    public enum FileTypeMask {
        FILE_TYPE_BASIC_MASK(0x000001),
        FILE_TYPE_DATA_MASK(0x000002),
        FILE_TYPE_MACHINE_MASK(0x000004),
        FILE_TYPE_ASCII_MASK(0x000008),
        FILE_TYPE_BINARY_MASK(0x000010),
        FILE_TYPE_RANDOM_MASK(0x000020),
        FILE_TYPE_ENCRYPTED_MASK(0x000040),
        FILE_TYPE_READWRITE_MASK(0x000080),
        FILE_TYPE_READONLY_MASK(0x000100),
        FILE_TYPE_HIDDEN_MASK(0x000200),
        FILE_TYPE_SYSTEM_MASK(0x000400),
        FILE_TYPE_VOLUME_MASK(0x000800),
        FILE_TYPE_DIRECTORY_MASK(0x001000),
        FILE_TYPE_ARCHIVE_MASK(0x002000),
        FILE_TYPE_LIBRARY_MASK(0x004000),
        FILE_TYPE_NONSHARE_MASK(0x008000),
        FILE_TYPE_UNDELETE_MASK(0x010000),
        FILE_TYPE_WRITEONLY_MASK(0x020000),
        FILE_TYPE_TEMPORARY_MASK(0x040000),
        FILE_TYPE_INTEGER_MASK(0x080000),
        FILE_TYPE_HARDLINK_MASK(0x100000),
        FILE_TYPE_SOFTLINK_MASK(0x200000);

        private final int value;

        FileTypeMask(int value) {
            this.value = value;
        }

        public final int getValue() {
            return value;
        }

        public static final int FILE_TYPE_EXTENSION_MASK = FILE_TYPE_BASIC_MASK.getValue()
                | FILE_TYPE_DATA_MASK.getValue()
                | FILE_TYPE_MACHINE_MASK.getValue()
                | FILE_TYPE_ASCII_MASK.getValue()
                | FILE_TYPE_BINARY_MASK.getValue()
                | FILE_TYPE_RANDOM_MASK.getValue()
                | FILE_TYPE_INTEGER_MASK.getValue();
    }

    /**
     * Directory entry
     */
    public interface Directory {

    }

    /**
     * DISK BASIC type number
     */
    public static final int FORMAT_TYPE_UNKNOWN = -1;

    /**
     * Value passed when renaming a file in file properties
     */
    public static class DiskBasicFileName {

        /** File name */
        private String name;
        /** Extended attribute. Even if the file name is the same, if this attribute is different, it is treated as a different file */
        private int optional;

        public DiskBasicFileName() {
            this.name = "";
            this.optional = 0;
        }

        public DiskBasicFileName(String nName, int nOptional) {
            this.name = nName;
            this.optional = nOptional;
        }

        /** File name */
        public final String getName() {
            return name;
        }

        /** File name */
        public void setName(String val) {
            this.name = val;
        }

        /** Extended attribute. Even if the file name is the same, if this attribute is different, it is treated as a different file */
        public int getOptional() {
            return optional;
        }

        /** Extended attribute. Even if the file name is the same, if this attribute is different, it is treated as a different file */
        public void setOptional(int val) {
            this.optional = val;
        }
    }

    /**
     * Attribute storage class
     */
    public static class DiskBasicFileType {

        /** DISK BASIC type */
        private int format;
        /** Common attribute. Combination of enum #en_file_type_mask values */
        private int type;
        /** Original attribute */
        private final int[] origin = new int[3];

        public DiskBasicFileType() {
            format = FORMAT_TYPE_UNKNOWN;
            type = 0;
            Arrays.fill(origin, 0);
        }

        /**
         * @param format  Format
         * @param type    Combination of enum #en_file_type_mask values
         * @param origin0 Original attribute
         * @param origin1 Original attribute continued 1
         * @param origin2 Original attribute continued 2
         */
        public DiskBasicFileType(int format, int type, int origin0, int origin1, int origin2) {
            this.format = format;
            this.type = type;
            origin[0] = origin0;
            origin[1] = origin1;
            origin[2] = origin2;
        }

        public DiskBasicFileType(int format, int type, int origin0) {
            this(format, type, origin0, 0, 0);
        }

        /** DISK BASIC type */
        public int getFormat() {
            return format;
        }

        /** DISK BASIC type */
        public void setFormat(int val) {
            format = val;
        }

        /** Common attribute. Combination of enum #en_file_type_mask values */
        public int getType() {
            return type;
        }

        /** Common attribute. Combination of enum #en_file_type_mask values */
        public void setType(int val) {
            type = val;
        }

        /** Original attribute */
        public int getOrigin(int idx) {
            return origin[idx];
        }

        public int getOrigin() {
            return getOrigin(0);
        }

        /** Original attribute */
        public void setOrigin(int val) {
            origin[0] = val;
        }

        /** Original attribute */
        public void setOrigin(int idx, int val) {
            origin[idx] = val;
        }

        /** Whether common attributes match */
        public boolean matchType(int mask, int value) {
            return ((type & mask) == value);
        }

        /** Whether common attributes do not match */
        public boolean unmatchType(int mask, int value) {
            return ((type & mask) != value);
        }

        /** Whether common attribute is ASCII attribute */
        public boolean isAscii() {
            return ((type & FileTypeMask.FILE_TYPE_ASCII_MASK.getValue()) != 0);
        }

        /** Whether common attribute is volume attribute */
        public boolean isVolume() {
            return ((type & (FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue() | FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue())) == FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue());
        }

        /** Whether common attribute is directory attribute */
        public boolean isDirectory() {
            return ((type & (FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue() | FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue())) == FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue());
        }
    }

    /**
     * Holds model-dependent data corresponding to the group number
     *
     * @see DiskBasicGroupItem
     */
    public static class DiskBasicGroupUserData implements Cloneable {

        public DiskBasicGroupUserData() {
        }

        @Override
        public DiskBasicGroupUserData clone() {
            try {
                return (DiskBasicGroupUserData) super.clone();
            } catch (CloneNotSupportedException e) {
                // Should not happen as we implement Cloneable
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Holds parameters corresponding to the group number
     *
     * @see DiskBasicGroups
     */
    public static class DiskBasicGroupItem {

        /** Group number int */
        public int group;
        /** Next group number int */
        public int next;
        /** Track number */
        public int track;
        /** Side number */
        public int side;
        /** Start sector number in group */
        public int sectorStart;
        /** End sector number in group */
        public int sectorEnd;
        /** Division position when there are multiple groups in a sector */
        public int divNum;
        /** Number of divisions when there are multiple groups in a sector */
        public int numOfDivs;
        /** Model dependent data */
        public DiskBasicGroupUserData userData;

        public DiskBasicGroupItem() {
            group = 0;
            next = 0;
            track = 0;
            side = 0;
            sectorStart = 0;
            sectorEnd = 0;
            divNum = 0;
            numOfDivs = 1;
            userData = null;
        }

        public DiskBasicGroupItem(DiskBasicGroupItem src) {
            group = src.group;
            next = src.next;
            track = src.track;
            side = src.side;
            sectorStart = src.sectorStart;
            sectorEnd = src.sectorEnd;
            divNum = src.divNum;
            numOfDivs = src.numOfDivs;
            userData = null;
            if (src.userData != null) {
                userData = src.userData.clone();
            }
        }

        /**
         * Assignment
         */
        public DiskBasicGroupItem set(DiskBasicGroupItem src) {
            group = src.group;
            next = src.next;
            track = src.track;
            side = src.side;
            sectorStart = src.sectorStart;
            sectorEnd = src.sectorEnd;
            divNum = src.divNum;
            numOfDivs = src.numOfDivs;
            if (userData != null) {
                // Delete old data before assigning new
                userData = null;
            }
            if (src.userData != null) {
                userData = src.userData.clone();
            }
            return this;
        }

        /**
         * @param group Group number
         * @param next  Next group number (optional)
         * @param track Track number
         * @param side  Side number
         * @param start Start sector number in group
         * @param end   End sector number in group
         * @param div   Division position when there are multiple groups in a sector
         * @param divs  Number of divisions when there are multiple groups in a sector
         * @param user  Model dependent data
         */
        public DiskBasicGroupItem(int group, int next, int track, int side, int start, int end, int div, int divs, DiskBasicGroupUserData user) {
            this.set(group, next, track, side, start, end, div, divs, user);
        }

        public DiskBasicGroupItem(int group, int next, int track, int side, int start, int end) {
            this(group, next, track, side, start, end, 0, 1, null);
        }

        /**
         * @param group Group number
         * @param next  Next group number (optional)
         * @param track Track number
         * @param side  Side number
         * @param start Start sector number in group
         * @param user  Model dependent data
         */
        public DiskBasicGroupItem(int group, int next, int track, int side, int start, DiskBasicGroupUserData user) {
            this.set(group, next, track, side, start, user);
        }

        /**
         * Dataset
         *
         * @param group Group number
         * @param next  Next group number (optional)
         * @param track Track number
         * @param side  Side number
         * @param start Start sector number in group
         * @param end   End sector number in group
         * @param div   Division position when there are multiple groups in a sector
         * @param divs  Number of divisions when there are multiple groups in a sector
         * @param user  Model dependent data
         */
        public void set(int group, int next, int track, int side, int start, int end, int div, int divs, DiskBasicGroupUserData user) {
            this.group = group;
            this.next = next;
            this.track = track;
            this.side = side;
            sectorStart = start;
            sectorEnd = end;
            divNum = div;
            numOfDivs = divs;
            userData = user;
        }

        /**
         * Dataset
         *
         * @param group Group number
         * @param next  Next group number (optional)
         * @param track Track number
         * @param side  Side number
         * @param start Start sector number in group
         * @param user  Model dependent data
         */
        public void set(int group, int next, int track, int side, int start, DiskBasicGroupUserData user) {
            this.group = group;
            this.next = next;
            this.track = track;
            this.side = side;
            sectorStart = start;
            sectorEnd = start;
            divNum = 0;
            numOfDivs = 1;
            userData = user;
        }

        /**
         * Comparison for sorting by group number
         */
        public static int compare(DiskBasicGroupItem item1, DiskBasicGroupItem item2) {
            return Integer.compare(item1.group, item2.group);
        }
    }

    /**
     * Holds a list of group numbers
     * <p>
     * Holds the chain of files in the disk in this list
     *
     * @see DiskBasicGroupItem
     * @see DiskBasicDirItem
     */
    public static class DiskBasicGroups {

        /** List of group numbers */
        private final List<DiskBasicGroupItem> items;
        /** Number of groups */
        private int nums;
        /** Occupied size in group (int) */
        private int size;
        /** Size of 1 group (int) */
        private int sizePerGroup;

        public DiskBasicGroups() {
            items = new ArrayList<>();
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        public DiskBasicGroups(List<DiskBasicGroupItem> items) {
            this.items = items;
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        /**
         * @param group Group number
         * @param next  Next group number (optional)
         * @param track Track number
         * @param side  Side number
         * @param start Start sector number in group
         * @param end   End sector number in group
         * @param div   Division position when there are multiple groups in a sector
         * @param divs  Number of divisions when there are multiple groups in a sector
         * @param user  Model dependent data
         *               Add
         */
        public void add(int group, int next, int track, int side, int start, int end, int div, int divs, DiskBasicGroupUserData user) {
            items.add(new DiskBasicGroupItem(group, next, track, side, start, end, div, divs, user));
        }

        public void add(int group, int next, int track, int side, int start, int end) {
            add(group, next, track, side, start, end, 0, 1, null);
        }

        public void add(int group, int next, int track, int side, int start, int end, int div, int divs) {
            add(group, next, track, side, start, end, div, divs, null);
        }

        /**
         * Add
         * @param group Group number
         * @param next  Next group number (optional)
         * @param track Track number
         * @param side  Side number
         * @param start Start sector number in group
         * @param user  Model dependent data
         */
        public void add(int group, int next, int track, int side, int start, DiskBasicGroupUserData user) {
            items.add(new DiskBasicGroupItem(group, next, track, side, start, user));
        }

        /**
         * Add
         * @param item Item
         */
        public void add(DiskBasicGroupItem item) {
            items.add(new DiskBasicGroupItem(item)); // Add a copy to maintain ownership semantics
        }

        /**
         * Add
         * @param items Item list
         */
        public void add(DiskBasicGroups items) {
            for (int i = 0; i < items.size(); i++) {
                this.items.add(new DiskBasicGroupItem(items.get(i)));
            }
            nums += items.nums;
            size += items.size;
        }

        /** Clear list */
        public void clear() {
            items.clear();
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        /** Returns the number of lists */
        public int size() {
            return items.size();
        }

        /** Returns the last of the list */
        public DiskBasicGroupItem last() {
            return items.getLast();
        }

        /** Returns list item */
        public DiskBasicGroupItem get(int idx) {
            return items.get(idx);
        }

        /** Returns list */
        public final List<DiskBasicGroupItem> getItems() {
            return items;
        }

        /** Returns number of groups */
        public int getNums() {
            return nums;
        }

        /** Returns occupied size */
        public int getSize() {
            return size;
        }

        /** Returns the size of 1 group */
        public int getSizePerGroup() {
            return sizePerGroup;
        }

        /** Set number of groups */
        public void setNums(int val) {
            nums = val;
        }

        /** Set occupied size */
        public void setSize(int val) {
            size = val;
        }

        /** Set size of 1 group */
        public void setSizePerGroup(int val) {
            sizePerGroup = val;
        }

        /** Add number of groups */
        public int addNums(int val) {
            nums += val;
            return nums;
        }

        /** Add occupied size */
        public int addSize(int val) {
            size += val;
            return size;
        }

        /** Sort by group number */
        public void sortItems() {
            items.sort(DiskBasicGroupItem::compare);
        }
    }

    /**
     * Item for general purpose list
     *
     * @see KeyValArray
     */
    public static class KeyValItem {

        public enum Type {
            TYPE_UNKNOWN(0),
            TYPE_INTEGER(1),
            TYPE_UINT8(2),
            TYPE_UINT16(3),
            TYPE_UINT32(4),
            TYPE_STRING(5),
            TYPE_BOOL(6);

            private final int value;

            Type(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        private String key;
        private byte[] value;
        private int size;
        private Type type;

        public KeyValItem() {
            value = null;
            size = 0;
            type = Type.TYPE_UNKNOWN;
        }

        public KeyValItem(String key, int val) {
            set(key, val);
        }

        public KeyValItem(String key, byte val, boolean invert) {
            set(key, val, invert);
        }

        public KeyValItem(String key, short val, boolean bigEndian, boolean invert) {
            set(key, val, bigEndian, invert);
        }

        public KeyValItem(String key, int val, boolean bigEndian, boolean invert) {
            set(key, val, bigEndian, invert);
        }

        public KeyValItem(String key, Object val, int size, boolean invert) {
            if (val instanceof byte[]) {
                set(key, (byte[]) val, size, invert);
            } else {
                throw new IllegalArgumentException("Unsupported type for raw data set.");
            }
        }

        public KeyValItem(String key, boolean val) {
            set(key, val);
        }

        public void clear() {
            value = null;
            size = 0;
            type = Type.TYPE_UNKNOWN;
        }

        /**
         * Set integer
         *
         * @param key Key name
         * @param val Value
         */
        public void set(String key, int val) {
            clear();
            this.key = key;
            value = new byte[Integer.BYTES];
            ByteUtil.writeLeInt(val, value, 0);
            size = Integer.BYTES;
            type = Type.TYPE_INTEGER;
        }

        /**
         * Set 8bit
         *
         * @param key    Key name
         * @param val    Value (byte)
         * @param invert Whether to invert value
         */
        public void set(String key, byte val, boolean invert) {
            clear();
            this.key = key;
            value = new byte[Byte.BYTES];
            value[0] = val;
            size = Byte.BYTES;
            type = Type.TYPE_UINT8;
            if (invert) Common.invertMemory(value, size);
        }

        /**
         * Set 16bit
         *
         * @param key       Key name
         * @param val       Value (wxUint16)
         * @param bigEndian Whether value is big endian
         * @param invert    Whether to invert value
         */
        public void set(String key, short val, boolean bigEndian, boolean invert) {
            clear();
            this.key = key;
            value = new byte[Short.BYTES];
            if (bigEndian)
                ByteUtil.writeBeShort(val, value, 0);
            else
                ByteUtil.writeLeShort(val, value, 0);
            size = Short.BYTES;
            type = Type.TYPE_UINT16;
            if (invert) Common.invertMemory(value, size);
        }

        /**
         * Set 32bit
         *
         * @param key       Key name
         * @param val       Value (int)
         * @param bigEndian Whether value is big endian
         * @param invert    Whether to invert value
         */
        public void set(String key, int val, boolean bigEndian, boolean invert) {
            clear();
            this.key = key;
            value = new byte[Integer.BYTES];
            if (bigEndian)
                ByteUtil.writeBeInt(val, value, 0);
            else
                ByteUtil.writeLeInt(val, value, 0);
            size = Integer.BYTES;
            type = Type.TYPE_UINT32;
            if (invert) Common.invertMemory(value, size);
        }

        /**
         * Set byte array
         *
         * @param key    Key name
         * @param val    Byte array (final void*)
         * @param size   Array size
         * @param invert Whether to invert value
         */
        public void set(String key, byte[] val, int size, boolean invert) {
            clear();
            this.key = key;
            value = new byte[size + 1];
            System.arraycopy(val, 0, value, 0, size);
            value[size] = 0;
            this.size = size;
            type = Type.TYPE_STRING;
            if (invert) Common.invertMemory(value, this.size);
        }

        /**
         * Set bool
         *
         * @param key Key name
         * @param val Value
         */
        public void set(String key, boolean val) {
            clear();
            this.key = key;
            value = new byte[1];
            value[0] = val ? (byte) 1 : (byte) 0;
            size = 1;
            type = Type.TYPE_BOOL;
        }

        /**
         * Returns the value as a string
         */
        public String getValueString() {
            if (value == null) return "";

            switch (type) {
                case TYPE_INTEGER:
                    int intVal = ((value[3] & 0xff) << 24) | ((value[2] & 0xff) << 16) | ((value[1] & 0xff) << 8) | (value[0] & 0xff);
                    return String.format("%d", intVal);
                case TYPE_UINT8:
                    return String.format("0x%02x", value[0] & 0xFF);
                case TYPE_UINT16:
                    short shortVal = (short) (((value[1] & 0xff) << 8) | (value[0] & 0xff));
                    return String.format("0x%04x", shortVal & 0xffff);
                case TYPE_UINT32:
                    int uint32Val = ((value[3] & 0xff) << 24) | ((value[2] & 0xff) << 16) | ((value[1] & 0xff) << 8) | (value[0] & 0xff);
                    return String.format("0x%08x", uint32Val); // Format as 8 hex digits
                case TYPE_STRING:
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < size; i++) {
                        if (i > 0) sb.append(" ");
                        sb.append(String.format("%02x", value[i] & 0xff));
                    }
                    return sb.toString();
                case TYPE_BOOL:
                    return value[0] != 0 ? "true" : "false";
                default:
                    return "";
            }
        }

        public final String getKey() {
            return key;
        }

        /**
         * Key name comparison
         */
        public static int compare(KeyValItem item1, KeyValItem item2) {
            return item1.key.compareTo(item2.key);
        }

        /**
         * Comparator for sorting a list/array of KeyValItem
         */
        public static class KeyComparator implements Comparator<KeyValItem> {

            @Override
            public int compare(KeyValItem item1, KeyValItem item2) {
                return KeyValItem.compare(item1, item2);
            }
        }
    }

    /**
     * General purpose list KeyValItem array
     */
    public static class KeyValArray {

        List<KeyValItem> contents = new ArrayList<>();

        public KeyValArray() {
            super();
        }

        /** Clear list */
        public void clear() {
            contents.clear();
        }

        /** Clear list */
        public void empty() {
            contents.clear();
        }

        /**
         * Add integer
         *
         * @param key Key name
         * @param val Value
         */
        public void add(String key, int val) {
            contents.add(new KeyValItem(key, val));
        }

        /**
         * Add 8bit
         *
         * @param key    Key name
         * @param val    Value (byte)
         * @param invert Whether to invert value
         */
        public void add(String key, byte val, boolean invert) {
            contents.add(new KeyValItem(key, val, invert));
        }

        /**
         * Add 16bit
         *
         * @param key       Key name
         * @param val       Value (wxUint16)
         * @param bigEndian Whether value is big endian
         * @param invert    Whether to invert value
         */
        public void add(String key, short val, boolean bigEndian, boolean invert) {
            contents.add(new KeyValItem(key, val, bigEndian, invert));
        }

        /**
         * Add 32bit
         *
         * @param key       Key name
         * @param val       Value (int)
         * @param bigEndian Whether value is big endian
         * @param invert    Whether to invert value
         */
        public void add(String key, int val, boolean bigEndian, boolean invert) {
            contents.add(new KeyValItem(key, val, bigEndian, invert));
        }

        /**
         * Add byte array
         *
         * @param key    Key name
         * @param val    Byte array (final void*)
         * @param size   Array size
         * @param invert Whether to invert value
         */
        public void add(String key, byte[] val, int size, boolean invert) {
            contents.add(new KeyValItem(key, val, size, invert));
        }

        public void add(String key, byte[] val, int size) {
            add(key, val, size, false);
        }

        /**
         * Add bool
         *
         * @param key Key name
         * @param val Value
         */
        public void add(String key, boolean val) {
            contents.add(new KeyValItem(key, val));
        }
    }
}
