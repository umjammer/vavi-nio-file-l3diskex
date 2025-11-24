///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupUserData;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.AmigaChain.Pointer;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.DirectoryAmiga;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HARDLINK_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SOFTLINK_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_UNDELETE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_WRITEONLY_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeAmiga.FORMAT_TYPE_AMIGA;


/**
 * ディレクトリ１アイテム Amiga DOS
 *
 * <li>FastFileSystem FFSかどうか(bool)</li
 */
public class DiskBasicDirItemAmiga extends DiskBasicDirItem<DirectoryAmiga> {

    /**
     * Amiga block structure head (all Big Endien)
     */
    @Serdes
    public static class AmigaBlockPre {

        public static final int SIZE = 4 + 4 + 4 + 4 + 4 + 4 + 4;

        /** starting block (T_HEADER:2 / T_LIST:16) */
        @Element(sequence = 1)
        public int type;
        /** self pointer (except Root) */
        @Element(sequence = 2)
        public int headerKey;
        /** number of data (File only) */
        @Element(sequence = 3)
        public int highSeq;
        /** hash table size (Root only) (72) */
        @Element(sequence = 4)
        public int tableSize;
        /** first data block pointer (File only) */
        @Element(sequence = 5)
        public int firstData;
        @Element(sequence = 6)
        public int checkSum;

        @Element(sequence = 7)
        public AmigaBlockPreUnion u = new AmigaBlockPreUnion();

        @Serdes
        public static class AmigaBlockPreUnion {

            /** block pointer (72 items) */
            public int[] table = new int[1];
            /** symbolic name (Soft link only) */
            @Element(sequence = 1)
            public byte[] symName = new byte[4];
        }
    }

    /**
     * Amiga Root Block (above hash_table)
     */
    @Serdes
    public static class AmigaRootBlockPost extends AmigaBlockPost {

        public static final int SIZE = 4 + 4 + 4 + 4 + 4 + 41 + 1 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4;

        // value is -1 if disk bitmap is valid
        @Element(sequence = 1)
        public int bmFlag;
        // blocks of disk bitmap
        @Element(sequence = 2)
        public int[] bmPages = new int[25];
        // ext blocks of disk bitmap
        @Element(sequence = 3)
        public int bmExt;
        // root dir modified date
        @Element(sequence = 4)
        public int rDays;
        // root dir modified time
        @Element(sequence = 5)
        public int rMins;
        // root dir modified seconds
        @Element(sequence = 6)
        public int rTicks;
        // in bytes
        @Element(sequence = 7)
        public byte diskNameLen;
        @Element(sequence = 8)
        public byte[] diskName = new byte[31];
        // set to 0
        @Element(sequence = 9)
        public int[] unused = new int[2];
        // disk modified date
        @Element(sequence = 10)
        public int vDays;
        // disk modified time
        @Element(sequence = 11)
        public int vMins;
        // disk modified seconds
        @Element(sequence = 12)
        public int vTicks;
        // disk creation date
        @Element(sequence = 12)
        public int cDays;
        // disk creation time
        @Element(sequence = 13)
        public int cMins;
        // disk creation seconds
        @Element(sequence = 14)
        public int cTicks;
        // always 0
        @Element(sequence = 15)
        public int nextHash;
        // always 0
        @Element(sequence = 16)
        public int parentDir;
        // always 0
        @Element(sequence = 17)
        public int extension;
        // always 1
        @Element(sequence = 18)
        public int secType;
    }

    /**
     * Amiga File / Directory Header Block (post table)
     */
    @Serdes
    public static class AmigaHeaderPost extends AmigaBlockPost {

        public static final int SIZE = 4 + 2 + 2 + 4 + 4 + 1 + 79 + 12 + 4 + 4 + 4 + 1 + 31 + 4 + 4 + 4 + 20 + 4 + 4 + 4 + 4;

        @Element(sequence = 1)
        public byte[] unused0 = new byte[4];
        // user id
        @Element(sequence = 2)
        public short uid;
        // user group
        @Element(sequence = 3)
        public short gid;
        @Element(sequence = 4)
        public int protect;
        // file size (File only)
        @Element(sequence = 5)
        public int byteSize;
        // in bytes
        @Element(sequence = 6)
        public byte commentLen;
        @Element(sequence = 7)
        public byte[] comment = new byte[79];
        @Element(sequence = 8)
        public byte[] unused1 = new byte[12];
        // modified date
        @Element(sequence = 9)
        public int days;
        // modified time
        @Element(sequence = 10)
        public int mins;
        // modified seconds
        @Element(sequence = 11)
        public int ticks;
        // in bytes
        @Element(sequence = 12)
        public byte nameLen;
        // 0 terminate
        @Element(sequence = 13)
        public byte[] name = new byte[31];
        @Element(sequence = 14)
        public byte[] unused2 = new byte[4];
        // FFS unused (File only)
        @Element(sequence = 15)
        public int realEntry;
        // FFS hardlinks chained list
        @Element(sequence = 16)
        public int nextLink;
        @Element(sequence = 17)
        public byte[] unused3 = new byte[20];
        // next entry with same hash
        @Element(sequence = 18)
        public Pointer hashChain;
        @Element(sequence = 19)
        public int parentDir;
        // 1st extension block / FFS: cache block
        @Element(sequence = 20)
        public int extension;
        // -2 (ST_USERDIR) / -3 (ST_FILE) / -4 (ST_LINKFILE) / 4 (ST_LINKDIR) / 3 (ST_SOFTLINK)
        @Element(sequence = 21)
        public int secType;
    }

    /**
     * Amiga block structure post table
     */
    public static class AmigaBlockPost {
        public static final int SIZE = 200;
        public static class Union {

            public AmigaRootBlockPost r;
            public AmigaHeaderPost h;
        }
        public Union u;
    }

    /**
     * ディレクトリエントリ Amiga DOS
     * <p>
     * AmigaDOSは1セクタ分になるのでブロック番号だけを保持
     */
    @Serdes
    public static class DirectoryAmiga implements Directory {

        @Element(sequence = 1)
        public int blockNum; // int
        @Element(sequence = 2)
        public AmigaBlockPre pre; // pointer
        @Element(sequence = 3)
        public AmigaBlockPost post; // pointer

        public static final int SIZE = 9;
    }

    public static final String KEY_FAST_FILE_SYSTEM = "FastFileSystem";
    public static final String KEY_INTERNATIONAL = "International";

    /// AMIGA属性値0
    public static final int FILETYPE_MASK_AMIGA_HEADER = 2;
    public static final int FILETYPE_MASK_AMIGA_DATA = 8;
    public static final int FILETYPE_MASK_AMIGA_LIST = 16;

    /// AMIGA属性値１
    public static final int FILETYPE_MASK_AMIGA_ROOT = 1;
    public static final int FILETYPE_MASK_AMIGA_FILE = -3;
    public static final int FILETYPE_MASK_AMIGA_USERDIR = 2;
    public static final int FILETYPE_MASK_AMIGA_LINKFILE = -4;
    public static final int FILETYPE_MASK_AMIGA_LINKDIR = 4;
    public static final int FILETYPE_MASK_AMIGA_SOFTLINK = 3;

    /// AMIGA属性値２
    public static final int FILETYPE_MASK_AMIGA_U_NDEL = 0x00000001;
    public static final int FILETYPE_MASK_AMIGA_U_NEXEC = 0x00000002;
    public static final int FILETYPE_MASK_AMIGA_U_NWRITE = 0x00000004;
    public static final int FILETYPE_MASK_AMIGA_U_NREAD = 0x00000008;
    public static final int FILETYPE_MASK_AMIGA_ARCHIVE = 0x00000010;
    public static final int FILETYPE_MASK_AMIGA_PURE = 0x00000020;
    public static final int FILETYPE_MASK_AMIGA_SCRIPT = 0x00000040;
    public static final int FILETYPE_MASK_AMIGA_HOLD = 0x00000080;
    public static final int FILETYPE_MASK_AMIGA_G_NDEL = 0x00000100;
    public static final int FILETYPE_MASK_AMIGA_G_NEXEC = 0x00000200;
    public static final int FILETYPE_MASK_AMIGA_G_NWRITE = 0x00000400;
    public static final int FILETYPE_MASK_AMIGA_G_NREAD = 0x00000800;
    public static final int FILETYPE_MASK_AMIGA_O_NDEL = 0x00001000;
    public static final int FILETYPE_MASK_AMIGA_O_NEXEC = 0x00002000;
    public static final int FILETYPE_MASK_AMIGA_O_NWRITE = 0x00004000;
    public static final int FILETYPE_MASK_AMIGA_O_NREAD = 0x00008000;
    public static final int FILETYPE_MASK_AMIGA_SETUID = 0x80000000;

    /** Amiga属性名 1 */
    public static final Map<String, Object> TYPE_NAME_AMIGA1 = new LinkedHashMap<>() {{
        put("File", FILETYPE_MASK_AMIGA_FILE);
        put("Dir", FILETYPE_MASK_AMIGA_USERDIR);
        put("L.F.", FILETYPE_MASK_AMIGA_LINKFILE);
        put("L.D.", FILETYPE_MASK_AMIGA_LINKDIR);
        put("S.L.", FILETYPE_MASK_AMIGA_SOFTLINK);
    }};

    /// Amiga 属性 1 変換テーブル
    public static final Map<Integer, Integer> TYPE_CONV_AMIGA1 = new HashMap<>() {{
            put(FILE_TYPE_DIRECTORY_MASK.getValue(), FILETYPE_MASK_AMIGA_ROOT);
            put(FILE_TYPE_DATA_MASK.getValue(), FILETYPE_MASK_AMIGA_FILE);
            put(FILE_TYPE_DIRECTORY_MASK.getValue(), FILETYPE_MASK_AMIGA_USERDIR);
            put(FILE_TYPE_HARDLINK_MASK.getValue() | FILE_TYPE_DATA_MASK.getValue(), FILETYPE_MASK_AMIGA_LINKFILE);
            put(FILE_TYPE_HARDLINK_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), FILETYPE_MASK_AMIGA_LINKDIR);
            put(FILE_TYPE_SOFTLINK_MASK.getValue(), FILETYPE_MASK_AMIGA_SOFTLINK);
    }};

    /** Amiga 属性名 2 リスト用 */
    public static final String TYPE_NAME_AMIGA2 = "dewrapsh";

    /** Amiga 属性変換 */
    public static final int[] TYPE_CONV_AMIGA2 = {
            FILETYPE_MASK_AMIGA_U_NDEL, FILETYPE_MASK_AMIGA_U_NEXEC,
            FILETYPE_MASK_AMIGA_U_NWRITE, FILETYPE_MASK_AMIGA_U_NREAD,
            FILETYPE_MASK_AMIGA_ARCHIVE, FILETYPE_MASK_AMIGA_PURE,
            FILETYPE_MASK_AMIGA_SCRIPT, FILETYPE_MASK_AMIGA_HOLD,
            FILETYPE_MASK_AMIGA_G_NDEL, FILETYPE_MASK_AMIGA_G_NEXEC,
            FILETYPE_MASK_AMIGA_G_NWRITE, FILETYPE_MASK_AMIGA_G_NREAD,
            FILETYPE_MASK_AMIGA_O_NDEL, FILETYPE_MASK_AMIGA_O_NEXEC,
            FILETYPE_MASK_AMIGA_O_NWRITE, FILETYPE_MASK_AMIGA_O_NREAD,
            FILETYPE_MASK_AMIGA_SETUID, -1
    };

    /** Amiga Hash Chain (last 16bytes on each block (sector)) */
    @Serdes
    public static class AmigaHashChain {

        public static final int SIZE = 4 + 4 + 4 + 4;

        /** next entry with same hash */
        @Element(sequence = 1)
        public Pointer hashChain;
        @Element(sequence = 2)
        public int parent;
        @Element(sequence = 3)
        public int extension;
        @Element(sequence = 4)
        public int sec_type;
    }

    /** Amiga File Data Block (all Big Endian) */
    @Serdes
    public static class AmigaFileDataPre {

        public static int SIZE = 25;

        @Serdes
        public static class Ofs {
            /** starting block (value: 8) */
            @Element(sequence = 1)
            public int type;
            /** file header block */
            @Element(sequence = 2)
            public int headerKey;
            /** data block seq number */
            @Element(sequence = 3)
            public int seqNum;
            @Element(sequence = 4)
            public int dataSize;
            /** next data block */
            @Element(sequence = 5)
            public int nextData;
            @Element(sequence = 6)
            public int checkSum;
            @Element(sequence = 7)
            public byte[] data = new byte[1];
        }

        @Element(sequence = 1)
        public Ofs o = new Ofs();
    }

    /// Amiga ユーザデータに渡すチェイン情報
    public static class AmigaChain extends DiskBasicGroupUserData {

        public static class Pointer {
            int[] buf;
            int index;
            Pointer(int[] buf, int index) {
                this.buf = buf;
                this.index = index;
            }
            int get() { return buf[index]; }
            void set(int value) { buf[index] = value; }
        }
        /** ハッシュテーブル内の位置 */
        public int index;
        /** ハッシュチェインのあるポインタ(前) */
        public Pointer prevChain;
        /** ハッシュチェインのあるポインタ(次) */
        public Pointer nextChain;

        public AmigaChain() {
            index = 0;
            prevChain = null;
            nextChain = null;
        }

        public AmigaChain(int index, Pointer prevChain, Pointer nextChain) {
            this.index = index;
            this.prevChain = prevChain;
            this.nextChain = nextChain;
        }

        @Override
        public DiskBasicGroupUserData clone() {
            return new AmigaChain(this.index, this.prevChain, this.nextChain);
        }

        public AmigaChain operator_assign(DiskBasicGroupUserData src) { // operator=
            AmigaChain src_amiga = (AmigaChain) src;
            index = src_amiga.index;
            prevChain = src_amiga.prevChain;
            nextChain = src_amiga.nextChain;
            return this;
        }
    }

    //
    // ディレクトリ１アイテム Amiga DOS
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryAmiga> data = new DiskBasicDirData<>();

    /** エクステンションブロック番号 */
    private final List<Integer> extensions = new ArrayList<>();

    /** インポート時などで一時的に作成する際のバッファ */
    private AmigaBlockPre tempPre;
    /** インポート時などで一時的に作成する際のバッファ */
    private AmigaBlockPost tempPost;

    /** チェイン情報 */
    private AmigaChain chain = new AmigaChain();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_AMIGA;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        tempPre = null;
        tempPost = null;

        allocData(null, null, 0);
        allocTemp();
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        tempPre = null;
        tempPost = null;

        allocData(sector, data, dataP);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem,
                     DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos,
                     SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        tempPre = null;
        tempPost = null;
        if (groupItem != null) chain = chain.operator_assign(groupItem.userData);

        allocData(sector, data, dataPos);

        used(checkUsed(unuse[0]));

        calcFileSize();
    }

    /** ディレクトリ情報をアロケート */
    private void allocData(DiskImageSector sector, byte[] data, int dataPos) throws IOException {
        this.data.alloc(DirectoryAmiga.class);

        if (sector != null && data != null) {
            int num = type.getSectorPosFromNum(sector.getIDC(), sector.getIDH(), sector.getIDR());
            this.data.data().blockNum = num;
            this.data.data().pre = new AmigaBlockPre();
            Serdes.Util.deserialize(new ByteArrayInputStream(data, dataPos, AmigaBlockPre.SIZE), this.data.data().pre);
            this.data.data().post = new AmigaBlockPost();
            Serdes.Util.deserialize(new ByteArrayInputStream(data, sector.getSectorSize() - AmigaBlockPost.SIZE, AmigaBlockPost.SIZE), this.data.data().post);
        } else {
            this.data.data().blockNum = 0;
            this.data.data().pre = null;
            this.data.data().post = null;
        }
    }

    /** バッファをアロケート */
    private void allocTemp() {
        tempPre = new AmigaBlockPre();
        tempPost = new AmigaBlockPost();

        data.data().pre = tempPre;
        data.data().post = tempPost;
    }

    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        if (groupItem != null) chain = chain.operator_assign(groupItem.userData); // TODO assign op
        allocData(sector, data, dataPos);
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        AmigaHeaderPost post = data.data().post != null ? data.data().post.u.h : null;
        if (post != null && num == 0) {
            size[0] = post.name.length;
            len[0] = post.name.length;
            len[0]--;
            return post.name;
        }
        size[0] = 0;
        len[0] = 0;
        return null;
    }

    @Override
    public void setNativeName(byte[] filename, int size, int length) {
        byte[] n;
        int[] nl = new int[1];
        int[] ns = new int[1];
        n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            System.arraycopy(filename, 0, n, 0, ns[0]);
            AmigaHeaderPost post = data.data().post != null ? data.data().post.u.h : null;
            if (post != null) post.nameLen = (byte) ((length - 1) & 0xff);
        }
    }

    @Override
    public void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n = null;
        int[] s = new int[1];
        int[] l = new int[1];
        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            int copy_size = s[0];
            if (copy_size > size) copy_size = size;
            System.arraycopy(n, 0, filename, 0, copy_size);
        }
        length[0] = l[0];
    }

    /**
     * 大文字にする
     * @param ch 文字
     * @param isI18n インターナショナルか
     * @return 変換後文字
     */
    public static byte upper(byte ch, boolean isI18n) {
        // a -> A
        if (ch >= 0x61 && ch <= 0x7a) {
            ch -= 0x20;
        }
        // i18n (iso-8859-1)
        if (isI18n) {
            if (ch >= (byte) 0xe0 && ch <= (byte) 0xfe && ch != (byte) 0xf7) {
                ch -= 0x20;
            }
        }
        return ch;
    }

    @Override
    public int getFileType1() {
        AmigaHeaderPost post = data.data().post != null ? data.data().post.u.h : null;
        if (post != null) return post.secType; // le
        return 0;
    }

    @Override
    public void setFileType1(int val) {
        AmigaHeaderPost post = data.data().post != null ? data.data().post.u.h : null;
        if (post != null) post.secType = val; // le
    }

    @Override
    public int getFileType2() {
        AmigaHeaderPost post = data.data().post != null ? data.data().post.u.h : null;
        if (post != null) return post.protect; // le
        return 0;
    }

    @Override
    public void setFileType2(int val) {
        AmigaHeaderPost post = data.data().post != null ? data.data().post.u.h : null;
        if (post != null) post.protect = val; // le
    }

    @Override
    protected int getFileType3() {
        return (getGroupID() << 16) | getUserID();
    }

    @Override
    protected void setFileType3(int val) {
        setGroupID((short) (val >> 16));
        setUserID((short) (val & 0xffff));
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            setFileType1(fileType.getOrigin(0));
            setFileType2(fileType.getOrigin(1));
            setFileType3(fileType.getOrigin(2));
        } else {
            int t1 = convToFileType1(fType);
            setFileType1(t1);
        }
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int t2 = getFileType2();
        int t3 = getFileType3();
        int val = convFromFileType1(t1);
        val |= convFromFileType2(t2);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1, t2, t3);
    }

    @Override
    public String getFileAttrStr() {
        StringBuilder str = new StringBuilder();
        int sPos = convFileType1Pos(getFileType1());
        str.append(sPos >= 0 ? TYPE_NAME_AMIGA1.get(sPos) : "???");
        str.append(" ,");
        int type2 = getFileType2();
        for (int i = 7; i >= 0; i--) {
            boolean high = (i >= 4);
            boolean bset = (((type2 & TYPE_CONV_AMIGA2[i]) != 0));
            str.append(bset == high ? TYPE_NAME_AMIGA2.charAt(i) : "-");
        }
        return str.toString();
    }

    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;
        getUnitGroups(fileUnitNum, groups);
    }

    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        extensions.clear();
        int limit = basic.getFatEndGroup() + 1;
        int[] tables = getBlockTable();
        int max_blks = getNumOfDataBlocks();
        int t1 = getFileType1();
        switch (t1) {
            case FILETYPE_MASK_AMIGA_FILE:
                getFileGroups(tables, max_blks, limit, groupItems, extensions);
                break;
            case FILETYPE_MASK_AMIGA_USERDIR:
                getDirectoryGroups(basic, tables, max_blks, limit, groupItems);
                break;
        }
    }

    @Override
    public boolean isDeletable() {
        AmigaHeaderPost post = data.data().post != null ? data.data().post.u.h : null;
        int val = (post != null ? post.nextLink /* le */ : 0);
        return val <= 0;
    }

    @Override
    public boolean delete() throws IOException {
        // extensionブロックを未使用にする
        // ディレクトリの場合、directory cacheを未使用にする
        for (int num : extensions) {
            type.deleteGroupNumber(num);
        }
        // ハードリンクの場合、リンクのつなぎ替えを行う
        deleteHardLink();

        // 自分を未使用にする
        type.deleteGroupNumber(data.data().blockNum);

        used(false);
        return true;
    }

    /** ハードリンクを削除 */
    public boolean deleteHardLink() throws IOException {
        // ハードリンクの場合、リンクのつなぎ替えを行う
        int t1 = getFileType1();
        if (t1 != FILETYPE_MASK_AMIGA_LINKFILE && t1 != FILETYPE_MASK_AMIGA_LINKDIR) {
            return true;
        }
        // real
        int myBlockNum = getStartGroup(0);
        AmigaHeaderPost prevPost = null;
        AmigaHeaderPost post = data.data().post.u.h;
        int blockNum = post.realEntry; // le
        while (blockNum >= 2) {
            // next link
            DiskImageSector sector = basic.getSectorFromGroup(blockNum);
            int nPos = sector.getSectorSize() - AmigaHeaderPost.SIZE;
            post = new AmigaHeaderPost();
            Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer(nPos)), post);

            if (prevPost != null && blockNum == myBlockNum) {
                prevPost.nextLink = post.nextLink;
                break;
            }
            blockNum = post.nextLink; // le
            prevPost = post;
        }
        return true;
    }

    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;

        return valid;
    }

    @Override
    public void setModify() {
        // 関連するセクタのチェックサムを更新する
        updateCheckSum();
    }

    /// header用にセクタを初期化
    public void initForHeaderBlock(int parentNum) {
        AmigaBlockPre pre = data.data().pre;
        if (pre == null) return;

        pre.type = FILETYPE_MASK_AMIGA_HEADER;
        pre.headerKey = data.data().blockNum; // le

        AmigaHeaderPost post = data.data().post.u.h;
        if (post == null) return;

        post.parentDir = parentNum; // le
    }

    /// header用にセクタを初期化
    public void initForExtensionBlock(int parent_num) {
        AmigaBlockPre pre = data.data().pre;
        if (pre == null) return;

        pre.type = FILETYPE_MASK_AMIGA_LIST;
        pre.headerKey = data.data().blockNum; // le

        AmigaHeaderPost post = data.data().post.u.h;
        if (post == null) return;

        post.parentDir = parent_num; // le
        post.secType = FILETYPE_MASK_AMIGA_FILE;
    }

    @Override
    public int getFileNameStrSize() {
        int[] s = {0};
        int[] l = {0};
        getFileNamePos(0, s, l);

        return l[0];
    }

    /** ファイルサイズを返す */
    public int getByteSize() {
        AmigaHeaderPost post = data.data().post != null ? data.data().post.u.h : null;
        if (post != null) return post.byteSize; // le
        return 0;
    }

    /** ストアしたブロック数をセット */
    public void setHighSeq(int val) {
        AmigaBlockPre pre = data.data().pre;
        if (pre != null) pre.highSeq = val; // le
    }

    /** ブロックテーブルを返す */
    public int[] getBlockTable() {
        AmigaBlockPre pre = data.data().pre;
        return pre != null ? pre.u.table : null;
    }

    /** ブロック番号を返す */
    public int getDataBlock(int idx) {
        AmigaBlockPre pre = data.data().pre;
        return pre != null ? pre.u.table[idx] : 0; // le
    }

    /** ブロック番号をセット */
    public void setDataBlock(int idx, int val) {
        AmigaBlockPre pre = data.data().pre;
        if (pre != null) pre.u.table[idx] = val; // le
    }

    /** 1ヘッダで格納できるブロック数を返す */
    public int getNumOfDataBlocks() {
        return (basic.getSectorSize() - AmigaBlockPre.SIZE - AmigaHeaderPost.SIZE) / 4 + 1;
    }

    /** ハッシュテーブルにセット（必要ならチェインをたどる）ディレクトリ用 */
    public boolean chainHashNumber(int idx, int val, DiskBasicDirItem<?> item) throws IOException {
        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        aitem.chain.index = idx;
        AmigaHeaderPost post = aitem.data.data().post.u.h;
        if (post != null) {
            aitem.chain.nextChain = post.hashChain;
        }

        int num = getDataBlock(idx);
        if (num < 2) {
            setDataBlock(idx, val);
            AmigaBlockPre pre = data.data().pre;
            if (pre != null) {
                aitem.chain.prevChain = new Pointer(pre.u.table, idx);
            }
            return true;
        }
        // チェインをたどる
        int limit = basic.getFatEndGroup() + 1;
        AmigaHashChain chain = null;
        while (num >= 2 && limit >= 0) {
            // セクタを得る
            DiskImageSector sector = basic.getSectorFromGroup(num);
            if (sector == null) return false;
            // 次のヘッダブロックがあるか
            int offset = sector.getSectorSize() - AmigaHashChain.SIZE;
            chain = new AmigaHashChain();
            byte[] b = sector.getSectorBuffer(offset);
            if (b == null) return false;
            Serdes.Util.deserialize(new ByteArrayInputStream(b), chain);
            num = chain.hashChain.get(); // le
            limit--;
        }
        if (limit >= 0) {
            chain.hashChain.set(val); // le
            aitem.chain.prevChain = chain.hashChain;
            return true;
        }
        return false;
    }

    /** ハッシュ番号を返す */
    public int getHashNumber() {
        return chain.index;
    }

    /** エクステンションをセット */
    public void setExtension(int val) {
        AmigaHeaderPost post = data.data().post.u.h;
        if (post != null) post.extension = val; // le
    }

    /** エクステンションを返す */
    public int getExtension() {
        AmigaHeaderPost post = data.data().post.u.h;
        if (post != null) return post.extension; // le
        return 0;
    }

    /** ユーザＩＤを返す */
    public int getUserID() {
        AmigaHeaderPost post = data.data().post.u.h;
        if (post != null) return post.uid & 0xffff; // le
        return 0;
    }

    /** ユーザＩＤをセット */
    public void setUserID(int val) {
        AmigaHeaderPost post = data.data().post.u.h;
        if (post != null) post.uid = (short) val; // le
    }

    /** グループＩＤを返す */
    public int getGroupID() {
        AmigaHeaderPost post = data.data().post.u.h;
        if (post != null) return post.gid & 0xffff; // le
        return 0;
    }

    /** グループＩＤをセット */
    public void setGroupID(int val) {
        AmigaHeaderPost post = data.data().post.u.h;
        if (post != null) post.gid = (short) val; // le
    }

    /** 共通属性を個別属性に変換 */
    public static int convToFileType1(int ftype) {
        int t1 = 0;
        if ((ftype & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
            t1 = FILETYPE_MASK_AMIGA_USERDIR;
        } else {
            t1 = FILETYPE_MASK_AMIGA_FILE;
        }
        return t1;
    }

    /** 個別属性を共通属性に変換 */
    public static int convFromFileType1(int type1) {
        int val = 0;
        for (Map.Entry<Integer, Integer> vv : TYPE_CONV_AMIGA1.entrySet()) {
            if (type1 == vv.getKey()) {
                val = vv.getValue();
                break;
            }
        }
        return val;
    }

    /** 個別属性を共通属性に変換 */
    public static int convFromFileType2(int type2) {
        int val = 0;
        if ((type2 & FILETYPE_MASK_AMIGA_U_NDEL) != 0) {
            val |= FILE_TYPE_UNDELETE_MASK.getValue();
        } else if ((type2 & FILETYPE_MASK_AMIGA_U_NWRITE) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        } else if ((type2 & FILETYPE_MASK_AMIGA_U_NREAD) != 0) {
            val |= FILE_TYPE_WRITEONLY_MASK.getValue();
        }
        return val;
    }

    /** 属性２からリストの位置を返す */
    public int convFileType1Pos(int type1) {
        return Utils.indexOf(TYPE_NAME_AMIGA1, type1);
    }

    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        AmigaHeaderPost post = data.data().post.u.h;
        if (post != null) post.byteSize = val; // le
    }

    @Override
    public int getFileSize() {
        int val = 0;
        AmigaHeaderPost post = data.data().post.u.h;
        if (post != null) val = post.byteSize; // le
        if (val == 0) val = groups.getSize();
        return val;
    }

    /** ファイルのすべてのグループを取得 */
    public boolean getFileGroups(int[] tables, int tableSize, int limit, DiskBasicGroups groupItems, List<Integer> extensionList) throws IOException {
        int[] trackNum = {0};
        int[] sideNum = {0};
        int sectorNum = 0;
        int prevNum = 0;
        int prevTrackNum = 0;
        int prevSideNum = 0;
        int prevSectorNum = 0;

        int calcGroups = 0;
        int calcFileSize = 0;

        AmigaBlockPre expre = null;
        AmigaHeaderPost expost = null;

        int[] table = tables;

        // データ部のブロックサイズ(OFSは24バイト減)
        int dataBlockSize = basic.getSectorSize();
        if (!basic.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            dataBlockSize -= 24;
        }

        // ファイルの時、data_block をたどる
        for (int extension = -1; ; extension++) {
            // 後ろから
            for (int i = tableSize - 1; i >= 0 && limit >= 0; i--) {
                int num = table[i]; // le
                if (num == 0) {
                    break;
                }
                DiskImageSector sector = basic.getSectorFromGroup(num, trackNum, sideNum);
                if (sector == null) {
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    break;
                }

                sectorNum = sector.getSectorNumber();
                if (groupItems != null && prevNum > 0) {
                    groupItems.add(prevNum, num, prevTrackNum, prevSideNum, prevSectorNum, sectorNum);
                }
                prevNum = num;
                prevTrackNum = trackNum[0];
                prevSideNum = sideNum[0];
                prevSectorNum = sectorNum;

                calcGroups++;
                calcFileSize += dataBlockSize;
                limit--;
            }
            // グループ数はヘッダブロック分も足す
            calcGroups++;
            // エクステンションがあるか
            int extNum = extension < 0 ? getExtension() : (expost != null ? expost.extension /* le */ : 0);
            if (extNum < 2) {
                break;
            }
            DiskImageSector sector = basic.getSectorFromGroup(extNum, trackNum, sideNum);
            if (sector == null) {
                break;
            }
            byte[] b = sector.getSectorBuffer();
            if (b == null) {
                break;
            }
            expre = new AmigaBlockPre();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), expre);
            if (expre.type != FILETYPE_MASK_AMIGA_LIST) {
                // エクステンションじゃない？
                break;
            }
            table = expre.u.table;
            expost = new AmigaHeaderPost();
            Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer(sector.getSectorSize() - AmigaHeaderPost.SIZE)), expost);
            if (extensionList != null) extensionList.add(extNum);
        }
        if (groupItems != null && prevNum > 0) {
            groupItems.add(prevNum, 0, prevTrackNum, prevSideNum, prevSectorNum, prevSectorNum);
        }

        // ファイルサイズ
        if (groupItems != null) {
            int realFileSize = getByteSize();
            if (calcFileSize < realFileSize) {
                groupItems.setSize(calcFileSize);
            } else {
                groupItems.setSize(realFileSize);
            }
            groupItems.setNums(calcGroups);
            groupItems.setSizePerGroup(basic.getSectorSize());
        }

        return limit < 0;
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     */
    public static boolean getDirectoryGroups(DiskBasic basic, int[] tables, int tableSize, int limit, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;

        int[] trackNum = {0};
        int[] sideNum = {0};
        int sectorNum = 0;

        // ディレクトリの時、hash_table をたどる
        for (int i = 0; i < tableSize && limit >= 0; i++) {
            int num = tables[i]; // le
            if (num < 2) {
                continue;
            }
            Pointer pPrevChain = new Pointer(tables, i);
            while (num >= 2 && limit >= 0) {
                limit--;
                DiskImageSector sector = basic.getSectorFromGroup(num, trackNum, sideNum);
                if (sector == null) {
                    // Why?
                    valid = false;
                    break;
                }
                // ヘッダ種類が2なら有効
                byte[] sectorBuffer = sector.getSectorBuffer();
                int type = ByteUtil.readLeInt(sectorBuffer, 0);
                if (type != FILETYPE_MASK_AMIGA_HEADER) {
                    valid = false;
                    break;
                }

                // 次のヘッダブロックがあるか
                int offset = sector.getSectorSize() - AmigaHashChain.SIZE;
                AmigaHashChain chain = new AmigaHashChain();
                Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer(offset)), chain);

                if (groupItems != null) {
                    sectorNum = sector.getSectorNumber();
                    groupItems.add(num, 0, trackNum[0], sideNum[0], sectorNum,
                            new DiskBasicDirItemAmiga.AmigaChain(i, pPrevChain, chain.hashChain)
                    );
                }

                pPrevChain = chain.hashChain;
                num = chain.hashChain.get(); // le
            }
        }

        // 常に１ブロック
        if (groupItems != null) {
            groupItems.setNums(1);
            groupItems.setSize(basic.getSectorSize());
        }

        return (limit >= 0) && valid;
    }

    /** インデックス番号をリナンバ */
    public static void renumberInDirectory(DiskBasic basic, List<DiskBasicDirItem<DirectoryAmiga>> items) {
        if (items == null) return;

        int prevIndex = -1;
        int chains = 0;
        for (DiskBasicDirItem<DirectoryAmiga> item : items) {
            DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
            int index = aitem.chain.index;
            if (prevIndex != index) {
                chains = 0;
            }
            aitem.setNumber(chains * 1000 + index);
            prevIndex = index;
            chains++;
        }
    }

    /**
     * アイテムリストにアイテムを挿入
     *
     * @param basic     DISK BASIC
     * @param tables    ディレクトリヘッダ内のハッシュテーブル
     * @param tableSize ハッシュテーブルのサイズ数
     * @param limit     ループ制限値
     * @param items     [in,out] ディレクトリの子供アイテムリスト
     * @param item      [in,out] 新たに追加するアイテム
     */
    public static boolean insertItemInDirectory(DiskBasic basic, int[] tables, int tableSize, int limit, List<DiskBasicDirItem<DirectoryAmiga>> items, DiskBasicDirItem<DirectoryAmiga> item) {
        boolean valid = true;

        if (!(items != null && item != null)) {
            return false;
        }

        valid = false;
        DiskBasicDirItemAmiga aItem = (DiskBasicDirItemAmiga) item;
        for (int i = 0; i < items.size(); i++) {
            DiskBasicDirItemAmiga aItem_ = (DiskBasicDirItemAmiga) items.get(i);
            // hash_tableのインデックス番号に沿ってインサート
            if (aItem_.chain.index > aItem.chain.index) {
                items.add(i, item);
                valid = true;
                break;
            }
        }
        if (!valid) {
            items.add(item);
            valid = true;
        }
        // リナンバー
        renumberInDirectory(basic, items);

        return (limit >= 0) && valid;
    }

    /** アイテムリストからアイテムを削除 */
    public static boolean deleteItemInDirectory(DiskBasic basic, int[] tables, int tableSize, int limit, List<DiskBasicDirItem<DirectoryAmiga>> items, DiskBasicDirItem<DirectoryAmiga> item) {
        //bool valid = true;

        if (!(items != null && item != null)) {
            return false;
        }

        DiskBasicDirItemAmiga aItem = (DiskBasicDirItemAmiga) item;
        aItem.chain.prevChain = aItem.chain.nextChain;

        items.remove(item);

        // リナンバー
        renumberInDirectory(basic, items);
        return true;
    }

    /** 日付を変換 */
    public static LocalDate convDateToTm(int days) {
        int year = 0;
        int month = 0;
        int day = 0;
        // 1978-01-01から
        for (int i = 0; i < 200; i++) {
            year = 1978 + i;
            int daysPerYear = (int) Utils.getDaysSince1978(LocalDate.of(i, 1, 1));
            if (days < daysPerYear) {
                month = days % daysPerYear;
                break;
            }
            days -= daysPerYear;
        }
        // month and day
        for (int i = 0; i < 12; i++) {
            int dayOfMonth = (int) Utils.getDaysSince1978(LocalDate.of(year, month, 1));
            if (month < dayOfMonth) {
                day = (month % dayOfMonth) + 1;
                month = i;
                break;
            }
            month -= dayOfMonth;
        }

        return LocalDate.of(
                year - 1900,
                month,
                day);
    }

    /** 時間を変換 */
    public static LocalTime convTimeToTm(int mins, int ticks) {
        // hour minute
        int hour = mins / 60;
        int minute = mins % 60;
        // second
        int second = ticks / 50;

        return LocalTime.of(
                hour,
                minute,
                second);
    }

    /** 日付を変換 */
    public static void convDateFromTm(LocalDate tm, int[] days) {
        days[0] = 0;

        int year = tm.getYear() + 1900;
        if (year < 1978) year = 1978;
        if (year > 2178) year = 2178;
        for (int i = 1978; i < year; i++) {
            days[0] += (int) Utils.getDaysSince1978(LocalDate.of(i, 1, 1));
        }

        int month = tm.getMonth().ordinal();
        for (int i = 0; i < month; i++) {
            days[0] += (int) Utils.getDaysSince1978(LocalDate.of(year, month, 1));
        }

        days[0] += tm.getDayOfMonth() - 1;
    }

    /** 時間を変換 */
    public static void convTimeFromTm(LocalDateTime tm, int[] mins, int[] ticks) {
        ticks[0] = tm.getSecond();
        ticks[0] *= 50;

        mins[0] = tm.getHour();
        mins[0] *= 60;
        mins[0] += tm.getMinute();
    }

    @Override
    public int getDataSize() {
        return 20;
    }

    /** アイテムを返す */
    @Override
    public DirectoryAmiga getData() {
        return data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(byte[] amiga) {
        if (!data.isValid()) return false;

        data.copy(amiga); // TODO why only this class does deep copy?
//        data.data().blockNum = amiga.blockNum;
//
//        // ポインタではなく内容をコピー
//        AmigaBlockPre blockPre = amiga.pre;
//        AmigaBlockPre dstPre = data.data().pre;
//        dstPre.type = blockPre.type;
//        dstPre.headerKey = blockPre.headerKey;
//
//        AmigaHeaderPost srcPost = amiga.post.h;
//        AmigaHeaderPost dstPost = data.data().post.h;
//
//        dstPost.nameLen = srcPost.nameLen;
//        System.arraycopy(srcPost.name, 0, dstPost.name, 0, dstPost.name.length);
//
//        dstPost.protect = srcPost.protect;
//
//        dstPost.uid = srcPost.uid;
//        dstPost.gid = srcPost.gid;
//
//        dstPost.days = srcPost.days;
//        dstPost.mins = srcPost.mins;
//        dstPost.ticks = srcPost.ticks;
//
//        dstPost.parentDir = srcPost.parentDir;
//        dstPost.secType = srcPost.secType;

        return true;
    }

    /** ディレクトリをクリア ファイル新規作成時 */
    @Override
    public void clearData() {
        data.data().blockNum = 0;

        // ポインタはクリアしない
    }

    /** 最初のグループ番号を設定 */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        data.data().blockNum = val;
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return data.data().blockNum;
    }

    public boolean preExportDataFile(String filename) {
        return false;
    }

    public boolean preImportDataFile(String filename) {
        return false;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        return 0;
    }

    public void updateCheckSumAll() {
    }

    public void updateCheckSum() {
    }
}
