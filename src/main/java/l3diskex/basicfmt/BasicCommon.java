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
     * 共通属性フラグ
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
     * ディレクトリエントリ
     */
    public interface Directory {

    }

    /**
     * DISK BASIC種類 番号
     */
    public static final int FORMAT_TYPE_UNKNOWN = -1;

    /**
     * ファイルプロパティでファイル名変更した時に渡す値
     */
    public static class DiskBasicFileName {

        /** ファイル名 */
        private String name;
        /** 拡張属性 ファイル名が同じでも、この属性が異なれば違うファイルとして扱う */
        private int optional;

        public DiskBasicFileName() {
            this.name = "";
            this.optional = 0;
        }

        public DiskBasicFileName(String nName, int nOptional) {
            this.name = nName;
            this.optional = nOptional;
        }

        /** ファイル名 */
        public final String getName() {
            return name;
        }

        /** ファイル名 */
        public void setName(String val) {
            this.name = val;
        }

        /** 拡張属性 ファイル名が同じでも、この属性が異なれば違うファイルとして扱う */
        public int getOptional() {
            return optional;
        }

        /** 拡張属性 ファイル名が同じでも、この属性が異なれば違うファイルとして扱う */
        public void setOptional(int val) {
            this.optional = val;
        }
    }

    /**
     * 属性保存クラス
     */
    public static class DiskBasicFileType {

        /** DISK BASIC種類 */
        private int format;
        /** 共通属性 enum #en_file_type_mask の値の組み合わせ */
        private int type;
        /** 本来の属性 */
        private final int[] origin = new int[3];

        public DiskBasicFileType() {
            format = FORMAT_TYPE_UNKNOWN;
            type = 0;
            Arrays.fill(origin, 0);
        }

        /**
         * @param format  フォーマット
         * @param type    enum #en_file_type_mask の値の組み合わせ
         * @param origin0 本来の属性
         * @param origin1 本来の属性 つづき1
         * @param origin2 本来の属性 つづき2
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

        /** DISK BASIC種類 */
        public int getFormat() {
            return format;
        }

        /** DISK BASIC種類 */
        public void setFormat(int val) {
            format = val;
        }

        /** 共通属性 enum #en_file_type_mask の値の組み合わせ */
        public int getType() {
            return type;
        }

        /** 共通属性 enum #en_file_type_mask の値の組み合わせ */
        public void setType(int val) {
            type = val;
        }

        /** 本来の属性 */
        public int getOrigin(int idx) {
            return origin[idx];
        }

        public int getOrigin() {
            return getOrigin(0);
        }

        /** 本来の属性 */
        public void setOrigin(int val) {
            origin[0] = val;
        }

        /** 本来の属性 */
        public void setOrigin(int idx, int val) {
            origin[idx] = val;
        }

        /** 共通属性が一致するか */
        public boolean matchType(int mask, int value) {
            return ((type & mask) == value);
        }

        /** 共通属性が一致しないか */
        public boolean unmatchType(int mask, int value) {
            return ((type & mask) != value);
        }

        /** 共通属性がアスキー属性か */
        public boolean isAscii() {
            return ((type & FileTypeMask.FILE_TYPE_ASCII_MASK.getValue()) != 0);
        }

        /** 共通属性がボリューム属性か */
        public boolean isVolume() {
            return ((type & (FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue() | FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue())) == FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue());
        }

        /** 共通属性がディレクトリ属性か */
        public boolean isDirectory() {
            return ((type & (FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue() | FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue())) == FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue());
        }
    }

    /**
     * グループ番号に対応する機種依存データを保持
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
     * グループ番号に対応するパラメータを保持
     *
     * @see DiskBasicGroups
     */
    public static class DiskBasicGroupItem {

        /** グループ番号 int */
        public int group;
        /** 次のグループ番号 int */
        public int next;
        /** トラック番号 */
        public int track;
        /** サイド番号 */
        public int side;
        /** グループ内の開始セクタ番号 */
        public int sectorStart;
        /** グループ内の終了セクタ番号 */
        public int sectorEnd;
        /** １グループがセクタ内に複数ある時の分割位置 */
        public int divNum;
        /** １グループがセクタ内に複数ある時の分割数 */
        public int numOfDivs;
        /** 機種依存データ */
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
         * 代入
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
         * @param group グループ番号
         * @param next  次のグループ番号（任意）
         * @param track トラック番号
         * @param side  サイド番号
         * @param start グループ内の開始セクタ番号
         * @param end   グループ内の終了セクタ番号
         * @param div   １グループがセクタ内に複数ある時の分割位置
         * @param divs  １グループがセクタ内に複数ある時の分割数
         * @param user  機種依存データ
         */
        public DiskBasicGroupItem(int group, int next, int track, int side, int start, int end, int div, int divs, DiskBasicGroupUserData user) {
            this.set(group, next, track, side, start, end, div, divs, user);
        }

        public DiskBasicGroupItem(int group, int next, int track, int side, int start, int end) {
            this(group, next, track, side, start, end, 0, 1, null);
        }

        /**
         * @param group グループ番号
         * @param next  次のグループ番号（任意）
         * @param track トラック番号
         * @param side  サイド番号
         * @param start グループ内の開始セクタ番号
         * @param user  機種依存データ
         */
        public DiskBasicGroupItem(int group, int next, int track, int side, int start, DiskBasicGroupUserData user) {
            this.set(group, next, track, side, start, user);
        }

        /**
         * データセット
         *
         * @param group グループ番号
         * @param next  次のグループ番号（任意）
         * @param track トラック番号
         * @param side  サイド番号
         * @param start グループ内の開始セクタ番号
         * @param end   グループ内の終了セクタ番号
         * @param div   １グループがセクタ内に複数ある時の分割位置
         * @param divs  １グループがセクタ内に複数ある時の分割数
         * @param user  機種依存データ
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
         * データセット
         *
         * @param group グループ番号
         * @param next  次のグループ番号（任意）
         * @param track トラック番号
         * @param side  サイド番号
         * @param start グループ内の開始セクタ番号
         * @param user  機種依存データ
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
         * グループ番号でソートする際の比較
         */
        public static int compare(DiskBasicGroupItem item1, DiskBasicGroupItem item2) {
            return Integer.compare(item1.group, item2.group);
        }
    }

    /**
     * グループ番号のリストを保持
     * <p>
     * ディスク内ファイルのチェインをこのリストに保持する
     *
     * @see DiskBasicGroupItem
     * @see DiskBasicDirItem
     */
    public static class DiskBasicGroups {

        /** グループ番号のリスト */
        private final List<DiskBasicGroupItem> items;
        /** グループ数 */
        private int nums;
        /** グループ内の占有サイズ (int) */
        private int size;
        /** １グループのサイズ (int) */
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
         * @param group グループ番号
         * @param next  次のグループ番号（任意）
         * @param track トラック番号
         * @param side  サイド番号
         * @param start グループ内の開始セクタ番号
         * @param end   グループ内の終了セクタ番号
         * @param div   １グループがセクタ内に複数ある時の分割位置
         * @param divs  １グループがセクタ内に複数ある時の分割数
         * @param user  機種依存データ
         *               追加
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
         * 追加
         * @param group グループ番号
         * @param next  次のグループ番号（任意）
         * @param track トラック番号
         * @param side  サイド番号
         * @param start グループ内の開始セクタ番号
         * @param user  機種依存データ
         */
        public void add(int group, int next, int track, int side, int start, DiskBasicGroupUserData user) {
            items.add(new DiskBasicGroupItem(group, next, track, side, start, user));
        }

        /**
         * 追加
         * @param item アイテム
         */
        public void add(DiskBasicGroupItem item) {
            items.add(new DiskBasicGroupItem(item)); // Add a copy to maintain ownership semantics
        }

        /**
         * 追加
         * @param items アイテムリスト
         */
        public void add(DiskBasicGroups items) {
            for (int i = 0; i < items.size(); i++) {
                this.items.add(new DiskBasicGroupItem(items.get(i)));
            }
            nums += items.nums;
            size += items.size;
        }

        /** リストをクリア */
        public void clear() {
            items.clear();
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        /** リストの数を返す */
        public int size() {
            return items.size();
        }

        /** リストの最後を返す */
        public DiskBasicGroupItem last() {
            return items.getLast();
        }

        /** リストアイテムを返す */
        public DiskBasicGroupItem get(int idx) {
            return items.get(idx);
        }

        /** リストを返す */
        public final List<DiskBasicGroupItem> getItems() {
            return items;
        }

        /** グループ数を返す */
        public int getNums() {
            return nums;
        }

        /** 占有サイズを返す */
        public int getSize() {
            return size;
        }

        /** 1グループのサイズを返す */
        public int getSizePerGroup() {
            return sizePerGroup;
        }

        /** グループ数を設定 */
        public void setNums(int val) {
            nums = val;
        }

        /** 占有サイズを設定 */
        public void setSize(int val) {
            size = val;
        }

        /** 1グループのサイズを設定 */
        public void setSizePerGroup(int val) {
            sizePerGroup = val;
        }

        /** グループ数を足す */
        public int addNums(int val) {
            nums += val;
            return nums;
        }

        /** 占有サイズを足す */
        public int addSize(int val) {
            size += val;
            return size;
        }

        /** グループ番号でソート */
        public void sortItems() {
            items.sort(DiskBasicGroupItem::compare);
        }
    }

    /**
     * 汎用リスト用アイテム
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
         * 設定 integer
         *
         * @param key キー名
         * @param val 値
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
         * 設定 8bit
         *
         * @param key    キー名
         * @param val    値 (byte)
         * @param invert 値を反転するか
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
         * 設定 16bit
         *
         * @param key       キー名
         * @param val       値 (wxUint16)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
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
         * 設定 32bit
         *
         * @param key       キー名
         * @param val       値 (int)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
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
         * 設定 byte array
         *
         * @param key    キー名
         * @param val    バイト配列 (final void*)
         * @param size   配列サイズ
         * @param invert 値を反転するか
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
         * 設定 bool
         *
         * @param key キー名
         * @param val 値
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
         * 値を文字列にして返す
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
         * キー名の比較
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
     * 汎用リスト KeyValItem の配列
     */
    public static class KeyValArray {

        List<KeyValItem> contents = new ArrayList<>();

        public KeyValArray() {
            super();
        }

        /** リストをクリア */
        public void clear() {
            contents.clear();
        }

        /** リストをクリア */
        public void empty() {
            contents.clear();
        }

        /**
         * 追加 integer
         *
         * @param key キー名
         * @param val 値
         */
        public void add(String key, int val) {
            contents.add(new KeyValItem(key, val));
        }

        /**
         * 追加 8bit
         *
         * @param key    キー名
         * @param val    値 (byte)
         * @param invert 値を反転するか
         */
        public void add(String key, byte val, boolean invert) {
            contents.add(new KeyValItem(key, val, invert));
        }

        /**
         * 追加 16bit
         *
         * @param key       キー名
         * @param val       値 (wxUint16)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void add(String key, short val, boolean bigEndian, boolean invert) {
            contents.add(new KeyValItem(key, val, bigEndian, invert));
        }

        /**
         * 追加 32bit
         *
         * @param key       キー名
         * @param val       値 (int)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void add(String key, int val, boolean bigEndian, boolean invert) {
            contents.add(new KeyValItem(key, val, bigEndian, invert));
        }

        /**
         * 追加 byte array
         *
         * @param key    キー名
         * @param val    バイト配列 (final void*)
         * @param size   配列サイズ
         * @param invert 値を反転するか
         */
        public void add(String key, byte[] val, int size, boolean invert) {
            contents.add(new KeyValItem(key, val, size, invert));
        }

        public void add(String key, byte[] val, int size) {
            add(key, val, size, false);
        }

        /**
         * 追加 bool
         *
         * @param key キー名
         * @param val 値
         */
        public void add(String key, boolean val) {
            contents.add(new KeyValItem(key, val));
        }
    }
}
