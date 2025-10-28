///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import l3diskex.Common;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryProdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_UNDELETE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_WRITEONLY_MASK;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;


/// Apple ProDOS インデックス ProDOSOneIndex の配列
public class DiskBasicDirItemProDOS extends DiskBasicDirItem<DirectoryProdos> {

    // wxTRANSLATE("File"), wxTRANSLATE("<DIR>"), wxTRANSLATE("<VOL>"),
    public static final String[] G_TYPE_NAME_PRODOS1 = {
            "File",
            "<DIR>",
            "<VOL>",
    };

    // Apple ProDOS属性位置 STORAGE_TYPE
    public static final int TYPE_NAME_PRODOS_FILE = 0;
    public static final int TYPE_NAME_PRODOS_SUBDIR = 1;
    public static final int TYPE_NAME_PRODOS_VOLUME = 2;

    // Apple ProDOS属性値 STORAGE_TYPE
    public static final int FILETYPE_MASK_PRODOS_DELETED = 0x0;
    public static final int FILETYPE_MASK_PRODOS_SEEDING = 0x1;
    public static final int FILETYPE_MASK_PRODOS_SAPLING = 0x2;
    public static final int FILETYPE_MASK_PRODOS_TREE = 0x3;
    public static final int FILETYPE_MASK_PRODOS_SUBDIR = 0xd;
    public static final int FILETYPE_MASK_PRODOS_SUBVOL = 0xe;
    public static final int FILETYPE_MASK_PRODOS_VOLUME = 0xf;

    // Apple ProDOS属性位置 FILE_TYPE
    public static final int TYPE_NAME_PRODOS_NOT = 0;
    public static final int TYPE_NAME_PRODOS_BAD = 1;
    public static final int TYPE_NAME_PRODOS_TXT = 2;
    public static final int TYPE_NAME_PRODOS_BIN = 3;
    public static final int TYPE_NAME_PRODOS_DIR = 4;
    public static final int TYPE_NAME_PRODOS_ADB = 5;
    public static final int TYPE_NAME_PRODOS_AWP = 6;
    public static final int TYPE_NAME_PRODOS_ASP = 7;
    public static final int TYPE_NAME_PRODOS_PAS = 8;
    public static final int TYPE_NAME_PRODOS_CMD = 9;
    public static final int TYPE_NAME_PRODOS_INT = 10;
    public static final int TYPE_NAME_PRODOS_IVR = 11;
    public static final int TYPE_NAME_PRODOS_BAS = 12;
    public static final int TYPE_NAME_PRODOS_VAR = 13;
    public static final int TYPE_NAME_PRODOS_REL = 14;
    public static final int TYPE_NAME_PRODOS_SYS = 15;
    public static final int TYPE_NAME_PRODOS_END = 16;

    // Apple ProDOS属性値 FILE_TYPE
    public static final int FILETYPE_MASK_PRODOS_NOT = 0x00;
    public static final int FILETYPE_MASK_PRODOS_BAD = 0x01;
    public static final int FILETYPE_MASK_PRODOS_TXT = 0x04;
    public static final int FILETYPE_MASK_PRODOS_BIN = 0x06;
    public static final int FILETYPE_MASK_PRODOS_DIR = 0x0f;
    public static final int FILETYPE_MASK_PRODOS_ADB = 0x19;
    public static final int FILETYPE_MASK_PRODOS_AWP = 0x1a;
    public static final int FILETYPE_MASK_PRODOS_ASP = 0x1b;
    public static final int FILETYPE_MASK_PRODOS_PAS = 0xef;
    public static final int FILETYPE_MASK_PRODOS_CMD = 0xf0;
    public static final int FILETYPE_MASK_PRODOS_INT = 0xfa;
    public static final int FILETYPE_MASK_PRODOS_IVR = 0xfb;
    public static final int FILETYPE_MASK_PRODOS_BAS = 0xfc;
    public static final int FILETYPE_MASK_PRODOS_VAR = 0xfd;
    public static final int FILETYPE_MASK_PRODOS_REL = 0xfe;
    public static final int FILETYPE_MASK_PRODOS_SYS = 0xff;

    // gTypeNameProDOS2 from basicdiritem_prodos.cpp
    public static final Map<String, Object> G_TYPE_NAME_PRODOS2 = new LinkedHashMap<>() {{
        put("<no type>", FILETYPE_MASK_PRODOS_NOT);
        put("BAD", FILETYPE_MASK_PRODOS_BAD);
        put("TXT", FILETYPE_MASK_PRODOS_TXT);
        put("BIN", FILETYPE_MASK_PRODOS_BIN);
        put("DIR", FILETYPE_MASK_PRODOS_DIR);
        put("ADB", FILETYPE_MASK_PRODOS_ADB);
        put("AWB", FILETYPE_MASK_PRODOS_AWP);
        put("ASP", FILETYPE_MASK_PRODOS_ASP);
        put("PAS", FILETYPE_MASK_PRODOS_PAS);
        put("CMD", FILETYPE_MASK_PRODOS_CMD);
        put("INT", FILETYPE_MASK_PRODOS_INT);
        put("IVR", FILETYPE_MASK_PRODOS_IVR);
        put("BAS", FILETYPE_MASK_PRODOS_BAS);
        put("VAR", FILETYPE_MASK_PRODOS_VAR);
        put("REL", FILETYPE_MASK_PRODOS_REL);
        put("SYS", FILETYPE_MASK_PRODOS_SYS);
    }};

    // Apple ProDOS属性値 ACCESS
    public static final int FILETYPE_MASK_PRODOS_DESTROY = 0x80;
    public static final int FILETYPE_MASK_PRODOS_RENAME = 0x40;
    public static final int FILETYPE_MASK_PRODOS_CHANGE = 0x20;
    public static final int FILETYPE_MASK_PRODOS_WRITE = 0x02;
    public static final int FILETYPE_MASK_PRODOS_READ = 0x01;
    public static final int FILETYPE_MASK_PRODOS_ACCESS_ALL = 0xe3;

    // gTypeNameProDOS3 from basicdiritem_prodos.cpp
    public static final Map<String, Object> G_TYPE_NAME_PRODOS3 = new LinkedHashMap<>() {{
        put("Readable", FILETYPE_MASK_PRODOS_READ);
        put("Writable", FILETYPE_MASK_PRODOS_WRITE);
        put("Changed", FILETYPE_MASK_PRODOS_CHANGE);
        put("Can Rename", FILETYPE_MASK_PRODOS_RENAME);
        put("Can Destroy", FILETYPE_MASK_PRODOS_DESTROY);
    }};

    // gTypeNameProDOS3S from basicdiritem_prodos.cpp
    public static final String G_TYPE_NAME_PRODOS3S = "rwcnd";

    /** Apple ProDOS ディレクトリブロックのチェイン */
    @Serdes
    public static class ProDOSDirPtrT {
        @Element(sequence = 1)
        public short prevBlock;
        @Element(sequence = 2)
        public short nextBlock;
    }

    // Apple ProDOS インデックス１つ
    static class ProDOSOneIndex {

        private final byte[][] mBuf = new byte[2][];
        private int mSize;
        private final int mGroupNum;

        public ProDOSOneIndex() {
            mSize = 0;
            mGroupNum = -1;
        }

        /** セクタバッファを割当て */
        public void attachBuffer(DiskBasic basic, int groupNum, int stPos) {
            DiskImageSector sector = null;
            int buf_idx = 0;
            for(int i=0; i < basic.getSectorsPerGroup() && buf_idx < 2; i++) {
                sector = basic.getSectorFromSectorPos(stPos + i);
                if (sector == null) break;
                mSize = sector.getSectorSize();
                for(int buf_pos = 0; buf_pos < mSize && buf_idx < 2; buf_pos += 256) {
                    setBuffer(buf_idx, sector.getSectorBuffer(buf_pos));
                    buf_idx++;
                }
            }
        }

        public void setBuffer(int idx, byte[] buffer) {
            mBuf[idx] = buffer;
        }

        public short getGroupNumber(int pos) {
            return (short) (((mBuf[1][pos] & 0xffff) << 8) | (mBuf[0][pos] & 0xffff));
        }

        public void setGroupNumber(int pos, short val) {
            mBuf[1][pos] = (byte) (val >>> 8);
            mBuf[0][pos] = (byte) (val & 0xff);
        }

        public int getSize() {
            return mSize;
        }

        public int getMyGroupNumber() {
            return mGroupNum;
        }
    }

    // Apple ProDOS インデックス ProDOSOneIndex の配列
    static class ProDOSIndex {

        List<ProDOSOneIndex> list = new ArrayList<>();

        /// 指定位置のブロック番号を得る
        /// @param pos 位置 インデックスすべての通し番号
        /// @return ブロック番号
        public short getGroupNumber(int pos) {
            short groupNum = (short) 0xffff;

            for (int idx = 0; idx < size(); idx++) {
                ProDOSOneIndex item = get(idx);
                int size = item.getSize();
                if (pos < size) {
                    groupNum = item.getGroupNumber(pos);
                    break;
                }
                pos -= size;
            }
            return groupNum;
        }

        /// 指定位置のブロック番号をセット
        /// @param pos 位置 インデックスすべての通し番号
        /// @param val ブロック番号
        public void setGroupNumber(int pos, short val) {
            for (int idx = 0; idx < size(); idx++) {
                ProDOSOneIndex item = get(idx);
                int size = item.getSize();
                if (pos < size) {
                    item.setGroupNumber(pos, val);
                    break;
                }
                pos -= size;
            }
        }

        public ProDOSOneIndex get(int i) {
            return list.get(i);
        }

        public int size() {
            return list.size();
        }
    }

    //
    // ディレクトリ１アイテム Apple ProDOS 8 / 16
    //

    /** ディレクトリデータ */
    DiskBasicDirData<DirectoryProdos> m_data = new DiskBasicDirData<>();

    /** セクタ内部へのポインタ */
    DirItemSectorBoundary m_sdata;

    /** このアイテムの属するグループ番号 */
    public int mDirGroupNum;

    private final ProDOSIndex mIndex = new ProDOSIndex();

    public DiskBasicDirItemProDOS(DiskBasic basic) throws IOException {
        super(basic);

        m_data.alloc(DirectoryProdos.class);
        allocateItem(null);

        mDirGroupNum = 0;
    }

    public DiskBasicDirItemProDOS(DiskBasic basic, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP) throws IOException {
        super(basic, nSector, nSecpos, nData, dataP);

        m_data.attach(DirectoryProdos.class, nData, dataP);
        allocateItem(null);

        mDirGroupNum = 0;
    }

    public DiskBasicDirItemProDOS(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP, SectorParam nNext, boolean[] nUnuse) throws IOException {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, dataP, nNext, nUnuse);

        m_data.attach(DirectoryProdos.class, nData, dataP);
        allocateItem(nNext);

        mDirGroupNum = type.getSectorPosFromNum(nGitem.track, nGitem.side, nSector.getSectorNumber());
        mDirGroupNum = mDirGroupNum / basic.getSectorsPerGroup();

        used(checkUsed(nUnuse[0]));

        // チェインセクタへのポインタをセット
        if (isUsed()) {
            mIndex.list.clear();
            //mIndex.setBasic(basic);
            int stype = getFileType1();
            switch(stype) {
                case FILETYPE_MASK_PRODOS_SAPLING:
                case FILETYPE_MASK_PRODOS_TREE:
                    int grp_num = getStartGroup(0);

			        //int sector_size = basic.getSectorSize();
                    int st_pos = type.getStartSectorFromGroup(grp_num);

                    ProDOSOneIndex item = new ProDOSOneIndex();
                    item.attachBuffer(basic, grp_num, st_pos);
                    mIndex.list.add(item);

                    if (stype == FILETYPE_MASK_PRODOS_TREE) {
                        // tree
                        int size = 256;
                        for(int i = 0; i < size; i++) {
                            grp_num = mIndex.getGroupNumber(i);
                            if (grp_num == 0) {
                                break;
                            }
                            st_pos = type.getStartSectorFromGroup(grp_num);

                            item = new ProDOSOneIndex();
                            item.attachBuffer(basic, grp_num, st_pos);
                            mIndex.list.add(item);
                        }
                    }
                    break;
            }
        }

        calcFileSize();
    }

    /// ディレクトリエントリを確保
    /// data  は内部で確保したメモリ
    /// sdata がセクタ内部へのポインタとなる
    private boolean allocateItem(SectorParam next) throws IOException {
        m_sdata.clear();
        boolean bound = m_sdata.set(basic, sector, position, m_data.getRawData(), getDataSize(), next);

        if (bound) {
            // セクタをまたぐ場合、dataは内部で確保する
            m_data.alloc(DirectoryProdos.class);
            m_data.fill(0);
        }

        // コピー
        m_sdata.copyTo(m_data.getRawData());

        return true;
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = m_data.data().name.length;
            return m_data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    @Override
    protected void setNativeName(byte[] filename, int size, int length) {
        byte[] n;
        int[] nl = {0};
        int[] ns = {0};
        n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            System.arraycopy(n, 0, filename, 0, ns[0]);
        }

        nl[0] = Common.rtrim(n, length, basic.diskBasicParam.getDirTerminateCode());

        // ファイル名長さ
        m_data.data().stypeAndNlen = (byte) ((nl[0] & 0xf) | (m_data.data().stypeAndNlen & 0xf0));
    }

    @Override
    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n;
        int[] s = {0};
        int[] l = {0};

        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            if (s[0] > size) s[0] = size;
            System.arraycopy(n, 0, filename, 0, s[0]);
            int nlen = m_data.data().stypeAndNlen & 0xf;
            if (nlen < size) filename[nlen] = 0;
        }
        length[0] = l[0];
    }

    @Override
    public int getFileNameStrSize() {
        int[] s = new int[1];
        int[] l = new int[1];
        getFileNamePos(0, s, l);

        return s[0];
    }

    /// 属性１を返す STORAGE_TYPE
    @Override
    public int getFileType1() {
        return (m_data.data().stypeAndNlen >> 4) & 0xf;
    }

    /// 属性１を設定 STORAGE_TYPE
    @Override
    public void setFileType1(int val) {
        m_data.data().stypeAndNlen = (byte) ((val << 4) | (m_data.data().stypeAndNlen & 0x0f));
    }

    /// 属性２を返す FILE_TYPE
    @Override
    public int getFileType2() {
        return m_data.data().fileType & 0xff;
    }

    /// 属性２を設定 FILE_TYPE
    @Override
    public void setFileType2(int val) {
        m_data.data().fileType = (byte) (val & 0xff);
    }

    /// 属性３を返す ACCESS
    @Override
    public int getFileType3() {
        return m_data.data().access & 0xff;
    }

    /// 属性３のセット ACCESS
    @Override
    public void setFileType3(int val) {
        m_data.data().access = (byte) (val & 0xff);
    }

    /// AUX_TYPEを返す
    public int getAuxType() {
        return m_data.data().aux.f.auxType & 0xffff;
    }

    /// AUX_TYPEのセット
    public void setAuxType(int val) {
        m_data.data().aux.f.auxType = (short) (val & 0xffff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return getFileType1() != 0;
    }

    /// バージョンを返す(VERSION,MIN_VERSION)
    public int getVersion() {
        return (m_data.data().version & 0xff) << 8 | (m_data.data().minVersion & 0xff);
    }

    /// バージョンをセット(VERSION,MIN_VERSION)
    public void setVersion(int val) {
        m_data.data().minVersion = (byte) (val & 0xff);
        val >>= 8;
        m_data.data().version = (byte) (val & 0xff);
    }

    /** 使用ブロック数を返す */
    public int getBlocksUsed() {
        return m_data.data().blocksUsed & 0xffff;
    }

    /** 使用ブロック数をセット */
    public void setBlocksUsed(int val) {
        m_data.data().blocksUsed = (short) (val & 0xffff);
    }

    @Override
    public boolean delete() {
        // 削除
        used(false);
        m_data.data().stypeAndNlen = 0;
        return true;
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;

        int stype = getFileType1();
        if (0x3 < stype && stype < 0xd) {
            valid = false;
        }
        return valid;
    }

    /// 固有属性の意味: STORAGE_TYPE,FILE_TYPE,ACCESS
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int ftype = fileType.getType();
        if (ftype == -1) return;

        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOSから
            int t3 = fileType.getOrigin();
            int t2 = t3 >> 8;
            int t1 = t2 >> 8;
            t2 &= 0xff;
            t1 &= 0xff;

            setFileType1(t1);
            if (t1 != FILETYPE_MASK_PRODOS_VOLUME) {
                setFileType2(t2);
            }
            setFileType3(t3);

            int type4 = fileType.getOrigin(1);
            int version = fileType.getOrigin(2);
            setAuxType(type4);
            setVersion(version);

        } else {
            // 違うOSから
            int t1 = 0;
            int t2 = FILETYPE_MASK_PRODOS_NOT;
            int t3 = FILETYPE_MASK_PRODOS_ACCESS_ALL;
            if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                t2 = FILETYPE_MASK_PRODOS_BAS;
            } else if ((ftype & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 = FILETYPE_MASK_PRODOS_SUBDIR;
                t2 = FILETYPE_MASK_PRODOS_DIR;
            }
            if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
                t3 &= ~FILETYPE_MASK_PRODOS_WRITE;
            }
            if ((ftype & FILE_TYPE_UNDELETE_MASK.getValue()) != 0) {
                t3 &= ~FILETYPE_MASK_PRODOS_DESTROY;
            }
            if ((ftype & FILE_TYPE_WRITEONLY_MASK.getValue()) != 0) {
                t3 &= ~FILETYPE_MASK_PRODOS_READ;
            }
            if ((ftype & (FILE_TYPE_SYSTEM_MASK.getValue() | FILE_TYPE_HIDDEN_MASK.getValue())) != 0) {
                t3 &= ~(FILETYPE_MASK_PRODOS_WRITE | FILETYPE_MASK_PRODOS_RENAME | FILETYPE_MASK_PRODOS_DESTROY);
            }

            if (t1 > 0) setFileType1(t1);
            setFileType2(t2);
            setFileType3(t3);
        }
    }

    /// 属性を返す
    /// @note 固有属性の意味: STORAGE_TYPE,FILE_TYPE,ACCESS
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int stype = getFileType1();
        int ftype = getFileType2();
        int access = getFileType3();

        switch (stype) {
            case FILETYPE_MASK_PRODOS_SUBDIR:
                val |= FILE_TYPE_DIRECTORY_MASK.getValue();
                break;
            case FILETYPE_MASK_PRODOS_VOLUME:
            case FILETYPE_MASK_PRODOS_SUBVOL:
                val |= FILE_TYPE_VOLUME_MASK.getValue();
                ftype = 0;
                break;
            default:
                break;
        }

        int type4 = getAuxType();
        int version = getVersion();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (stype << 16) | (ftype << 8) | access, type4, version);
    }

    @Override
    public String getFileAttrStr() {
        String str = "";
        int spos = convFileType1Pos(getFileType1());

        if (spos != TYPE_NAME_PRODOS_FILE) {
            // DIR or VOL
            str = G_TYPE_NAME_PRODOS1[spos];
        } else {
            // FILE TYPE
            int type2 = getFileType2();
            int fpos = convFileType2Pos(type2);
            if (fpos >= 0) {
                str = Utils.keyAt(G_TYPE_NAME_PRODOS2, fpos);
            } else {
                str = String.format("0x%02x", type2);
            }
        }

        // ACCESS
        str += " ,";

        int access = getFileType3();
        for (int i = 0; i < G_TYPE_NAME_PRODOS3.size(); i++) {
            if ((access & (int) Utils.valueAt(G_TYPE_NAME_PRODOS3, i)) != 0) {
                str += Utils.keyAt(G_TYPE_NAME_PRODOS3, i);
            } else {
                str += "-";
            }
        }

        return str;
    }

    /** 属性からリストの位置を返す */
    private int convFileType1Pos(int type1) {
        int pos;
        switch (type1) {
            case FILETYPE_MASK_PRODOS_SUBDIR:
                pos = TYPE_NAME_PRODOS_SUBDIR;
                break;
            case FILETYPE_MASK_PRODOS_SUBVOL:
            case FILETYPE_MASK_PRODOS_VOLUME:
                pos = TYPE_NAME_PRODOS_VOLUME;
                break;
            default:
                pos = TYPE_NAME_PRODOS_FILE;
                break;
        }
        return pos;
    }

    /** 属性からリストの位置を返す */
    private int convFileType2Pos(int type2) {
        return Utils.indexOf(G_TYPE_NAME_PRODOS2, type2 & 0xff);
    }

    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int blk = basic.getSectorSize() * basic.getSectorsPerGroup();
        int grps = (val + blk - 1) / blk;
        grps += mIndex.size();

        m_data.data().eof[0] = (byte) (val & 0xff);
        val >>= 8;
        m_data.data().eof[1] = (byte) (val & 0xff);
        val >>= 8;
        m_data.data().eof[2] = (byte) (val & 0xff);

        setBlocksUsed(grps);
    }

    @Override
    public int getFileSize() {
        int val = (m_data.data().eof[0] & 0xff) +
                ((m_data.data().eof[1] & 0xff) << 8) +
                ((m_data.data().eof[2] & 0xff) << 16);

        int stype = getFileType1();
        switch (stype) {
            case FILETYPE_MASK_PRODOS_SUBVOL:
            case FILETYPE_MASK_PRODOS_VOLUME:
                val = 0;
                break;
        }
        return val;
    }

    @Override
    public void calcFileUnitSize(int fileunitNum) throws IOException {
        if (!checkUsed(false)) return;

        getUnitGroups(fileunitNum, groups);
    }

    @Override
    public void getUnitGroups(int fileunitNum, DiskBasicGroups groupItems) throws IOException {
        //if (!chain.isValid()) return;

        int calc_groups = 0;
        int calc_file_size = 0;

        int[] track_num = {0};
        int[] side_num = {0};
        int sector_num = 0;

        int sector_size = basic.getSectorSize();
        int block_size = sector_size * basic.getSectorsPerGroup();

        int remain_size = getFileSize();
        int group_num = getStartGroup(fileunitNum);
        int sector_pos = group_num * basic.getSectorsPerGroup();

        int stype = getFileType1();
        if (stype == FILETYPE_MASK_PRODOS_SEEDING) {
            // 1ブロックで収まるファイル
            basic.getNumsFromGroup(group_num, 0, sector_size, remain_size, groupItems);
            calc_groups++;
            calc_file_size += getFileSize();
        } else if (stype == FILETYPE_MASK_PRODOS_SAPLING) {
            // インデックスを参照するファイル
            for (int i = 0; i < getBlocksUsed(); i++) {
                group_num = mIndex.getGroupNumber(i);
                if (group_num == 0) {
                    break;
                }
                basic.getNumsFromGroup(group_num, 0, sector_size, remain_size, groupItems);
                calc_groups++;
            }
            calc_file_size += getFileSize();
        } else if (stype == FILETYPE_MASK_PRODOS_TREE) {
            // インデックスを参照するファイル ツリー形式
            for (int i = 0; i < getBlocksUsed(); i++) {
                group_num = mIndex.getGroupNumber(i + sector_size);
                if (group_num == 0) {
                    break;
                }
                basic.getNumsFromGroup(group_num, 0, sector_size, remain_size, groupItems);
                calc_groups++;
            }
            calc_file_size += getFileSize();
        } else if (stype == FILETYPE_MASK_PRODOS_SUBDIR) {
            // サブディレクトリ
            for (int i = 0; i < getBlocksUsed(); i++) {
                ProDOSDirPtrT next = new ProDOSDirPtrT();
                next.nextBlock = 0;

                for (int ss = 0; ss < basic.getSectorsPerGroup(); ss++) {
                    DiskImageSector sector = basic.getSectorFromSectorPos(sector_pos, track_num, side_num);
                    if (sector == null) {
                        break;
                    }
                    byte[] buffer = sector.getSectorBuffer();
                    if (buffer == null) {
                        break;
                    }
                    if (ss == 0) {
                        // 次のブロックへのポインタを保持
                        Serdes.Util.deserialize(new ByteArrayInputStream(buffer), next);
                    }

                    sector_num = sector.getSectorNumber();

                    groupItems.add(group_num, 0, track_num[0], side_num[0], sector_num, sector_num);

                    sector_pos++;
                }

                calc_groups++;

                // 次のセクタなし
                if (next.nextBlock == 0) {
                    break;
                }

                group_num = next.nextBlock & 0xffff;
                sector_pos = group_num * basic.getSectorsPerGroup();
            }
            calc_file_size += getFileSize();
        } else {

        }
        groupItems.setNums(calc_groups);
        groupItems.setSize(calc_file_size);
        groupItems.setSizePerGroup(block_size);

        // 最終セクタの再計算
        //group_items.setSize(recalcFileSize(group_items, group_items.getSize()));

        // ファイル内部のアドレスを得る
        takeAddressesInFile(groupItems);
    }

    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) {
        return occupiedSize;
    }

    // ファイル内部のアドレスを取り出す
    private void takeAddressesInFile(DiskBasicGroups groupItems) {
        if (groupItems.size() == 0) {
            return;
        }

        DiskBasicGroupItem item = groupItems.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;
    }

    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    @Override
    public DirectoryProdos getData() {
        return m_data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val);
    }

    @Override
    public void clearData() {
        m_data.fill(0);
    }

    @Override
    public void setStartGroup(int fileunitNum, int val, int size) {
        m_data.data().keyPointer = (short) val;
    }

    @Override
    public int getStartGroup(int fileunitNum) {
        int val = m_data.data().keyPointer & 0xffff;

        int stype = getFileType1();
        switch (stype) {
            case FILETYPE_MASK_PRODOS_SUBVOL:
            case FILETYPE_MASK_PRODOS_VOLUME:
                val = mDirGroupNum;
                break;
        }
        return val;
    }

    @Override
    public void setParentGroup(int val) {
        int stype = getFileType1();
        switch (stype) {
            case FILETYPE_MASK_PRODOS_SUBVOL:
            case FILETYPE_MASK_PRODOS_VOLUME:
                break;
            default:
                m_data.data().aux.f.headerPointer = (short) val;
                break;
        }
    }

    @Override
    public int getParentGroup() {
        int val;
        int stype = getFileType1();
        switch (stype) {
            case FILETYPE_MASK_PRODOS_SUBVOL:
            case FILETYPE_MASK_PRODOS_VOLUME:
                val = INVALID_GROUP_NUMBER;
                break;
            default:
                val = m_data.data().aux.f.headerPointer & 0xffff;
                break;
        }
        return val;
    }

    @Override
    public int getExtraGroup() {
        int stype = getFileType1();
        switch (stype) {
            case FILETYPE_MASK_PRODOS_SAPLING:
            case FILETYPE_MASK_PRODOS_TREE:
                return getStartGroup(0);
        }
        return INVALID_GROUP_NUMBER;
    }

    @Override
    public void getExtraGroups(java.util.List<Integer> arr) {
        int stype = getFileType1();
        switch (stype) {
            case FILETYPE_MASK_PRODOS_SAPLING:
            case FILETYPE_MASK_PRODOS_TREE:
                arr.add(getStartGroup(0));
                break;
        }
    }

    @Override
    public void clearChainSector(DiskBasicDirItem<DirectoryProdos> pitem) throws IOException {
        for(int i=0; i < mIndex.size(); i++) {
            ProDOSOneIndex item = mIndex.get(i);
            int gnum = item.getMyGroupNumber();
            type.deleteGroupNumber(gnum);
        }
        mIndex.list.clear();
        //mIndex.setBasic(basic);
    }

    @Override
    public void setChainSector(int num, int pos, byte[] data, DiskBasicDirItem<DirectoryProdos> pitem) {
        ProDOSOneIndex item = new ProDOSOneIndex();
        item.attachBuffer(basic, num, pos);
        mIndex.list.add(item);
    }

    @Override
    public void addChainGroupNumber(int idx, int val) {
        mIndex.setGroupNumber(idx, (short) val);
    }

    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    @Override
    public int recalcFileSizeOnSave(InputStream istream, int fileSize) {
        int stype;
        if (fileSize < 0x200) {
            // 512バイト未満はインデックスなし
            stype = FILETYPE_MASK_PRODOS_SEEDING;
        } else if (fileSize < 0x20000) {
            // 131Kバイト未満はインデックス１つ
            stype = FILETYPE_MASK_PRODOS_SAPLING;
        } else {
            // 131Kバイト以上 ツリー
            stype = FILETYPE_MASK_PRODOS_TREE;
        }
        setFileType1(stype);

        return fileSize;
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.isAddExtensionExport()) return true;

        // 属性から拡張子を付加する
        if (!isDirectory()) {
            String[] ext = new String[1];
            if (getFileAttrName(convFileType2Pos(getFileType2()), G_TYPE_NAME_PRODOS2, ext)) {
                filename[0] += ".";
                if (Utils.isUpperString(filename[0])) {
                    filename[0] += ext[0].toUpperCase();
                } else {
                    filename[0] += ext[0].toLowerCase();
                }
            }
        }
        return true;
    }

    /**
     * データをインポートする前に必要な処理
     *
     * @param filename [in,out] ファイル名
     * @return false このファイルは対象外とする
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], G_TYPE_NAME_PRODOS2, TYPE_NAME_PRODOS_NOT, TYPE_NAME_PRODOS_SYS, filename, null, null);
        }
        // 拡張子を消す
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t2 = {0};
        // 拡張子で属性を設定する
        if (!isContainAttrByExtension(filename, G_TYPE_NAME_PRODOS2, TYPE_NAME_PRODOS_NOT, TYPE_NAME_PRODOS_SYS, null, t2, null)) {
            t2[0] = FILETYPE_MASK_PRODOS_TXT;
        }

        return t2[0];
    }

    @Override
    public boolean isDeletable() {
        int stype = getFileType1();
        return (stype != FILETYPE_MASK_PRODOS_SUBVOL && stype != FILETYPE_MASK_PRODOS_VOLUME);
    }

    @Override
    public boolean hasModifyDateTime() {
        int stype = getFileType1();
        return (stype != FILETYPE_MASK_PRODOS_SUBVOL && stype != FILETYPE_MASK_PRODOS_VOLUME);
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        return convDateToTm(m_data.data().cdate);
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        return convTimeToTm(m_data.data().ctime);
    }

    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    @Override
    public String getFileCreateTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileCreateTime(tm);
        return Utils.formatHMStr(lt);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        convDateFromTm(tm, m_data.data().cdate);
    }

    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        convTimeFromTm(tm, m_data.data().ctime);
    }

    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        int stype = getFileType1();
        switch (stype) {
            case FILETYPE_MASK_PRODOS_SUBVOL:
            case FILETYPE_MASK_PRODOS_VOLUME:
                return tm.toLocalDate();
            default:
                return convDateToTm(m_data.data().cdate);
        }
    }

    @Override
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        int stype = getFileType1();
        switch (stype) {
            case FILETYPE_MASK_PRODOS_SUBVOL:
            case FILETYPE_MASK_PRODOS_VOLUME:
                return tm.toLocalTime();
            default:
                return convTimeToTm(m_data.data().ctime);
        }
    }

    @Override
    public String getFileModifyDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileModifyDate(tm);
        return Utils.formatYMDStr(ld);
    }

    @Override
    public String getFileModifyTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileModifyTime(tm);
        return Utils.formatHMStr(lt);
    }

    @Override
    public void setFileModifyDate(LocalDateTime tm) {
        convDateFromTm(tm, m_data.data().cdate);
    }

    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        convTimeFromTm(tm, m_data.data().ctime);
    }

    // Static conversion methods
    public static LocalDate convDateToTm(byte[] data) {
        int ymd = (data[1] & 0xff) << 8 | (data[0] & 0xff);
        return LocalDate.of(
                (ymd >> 9) +
                        ((ymd >> 9) < 70 ? 100 : 0),
                ((ymd >> 5) & 0xf) - 1,
                ymd & 0x1f);
    }

    public static LocalTime convTimeToTm(byte[] data) {
        return LocalTime.of(
                data[1] & 0x1f,
                data[0] & 0x3f,
                0);
    }

    public static void convDateFromTm(LocalDateTime tm, byte[] data) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            data[1] = (byte) (((tm.getYear() & 0x7f) % 100) << 1 | (((tm.getMonth().ordinal() + 1) & 0x8) >> 3));
            data[0] = (byte) ((((tm.getMonth().ordinal() + 1) & 0x7) << 5) | (tm.getDayOfMonth() & 0x1f));
        }
    }

    public static void convTimeFromTm(LocalDateTime tm, byte[] data) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            data[1] = (byte) (tm.getHour() & 0x1f);
            data[0] = (byte) (tm.getMinute() & 0x3f);
        }
    }

    @Override
    public int getStartAddress() {
        int ftype = getFileType2();
        switch (ftype) {
            case FILETYPE_MASK_PRODOS_BIN:
            case FILETYPE_MASK_PRODOS_BAS:
            case FILETYPE_MASK_PRODOS_SYS:
                return getAuxType();
        }
        return -1;
    }

    @Override
    public int getEndAddress() {
        int val = -1;
        int ftype = getFileType2();
        switch (ftype) {
            case FILETYPE_MASK_PRODOS_BIN:
            case FILETYPE_MASK_PRODOS_BAS:
            case FILETYPE_MASK_PRODOS_SYS:
                val = getAuxType() + getFileSize() - 1;
                break;
        }
        return val;
    }

    @Override
    public void setStartAddress(int val) {
        int ftype = getFileType2();
        switch (ftype) {
            case FILETYPE_MASK_PRODOS_BIN:
            case FILETYPE_MASK_PRODOS_BAS:
            case FILETYPE_MASK_PRODOS_SYS:
                setAuxType(val);
                break;
        }
    }

    public void increaseFileCount() {
         short val = 0;
         int stype = getFileType1();
         switch (stype) {
         case FILETYPE_MASK_PRODOS_SUBVOL:
         case FILETYPE_MASK_PRODOS_VOLUME:
             val = m_data.data().aux.v.fileCount;
             val++;
             m_data.data().aux.v.fileCount = val;
         default:
             break;
         }
    }

    public void decreaseFileCount() {
         short val = 0;
         int stype = getFileType1();
         switch (stype) {
         case FILETYPE_MASK_PRODOS_SUBVOL:
         case FILETYPE_MASK_PRODOS_VOLUME:
             val = m_data.data().aux.v.fileCount;
             val--;
             m_data.data().aux.v.fileCount = val;
             break;
         default:
             break;
         }
    }

    /** アイテムの属するセクタを変更済みにする */
    @Override
    public void setModify() {
        m_sdata.copyFrom(m_data.getRawData());
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        int stype = getFileType1();
        vals.add("STORAGE_TYPE", m_data.data().stypeAndNlen);
        vals.add("FILE_NAME", m_data.data().name, m_data.data().name.length);
        vals.add("FILE_TYPE", m_data.data().fileType);
        vals.add("KEY_POINTER", m_data.data().keyPointer);
        vals.add("BLOCKS_USED", m_data.data().blocksUsed);
        vals.add("EOF", m_data.data().eof, m_data.data().eof.length);
        vals.add("CREATION_DATE", m_data.data().cdate, m_data.data().cdate.length);
        vals.add("CREATION_TIME", m_data.data().ctime, m_data.data().ctime.length);
        vals.add("VERSION", m_data.data().version);
        vals.add("MIN_VERSION", m_data.data().minVersion);
        vals.add("ACCESS", m_data.data().access);
        switch (stype) {
            case FILETYPE_MASK_PRODOS_VOLUME:
                vals.add("ENTRY_LENGTH", m_data.data().aux.v.entryLen);
                vals.add("ENTRIES_PER_BLOCK", m_data.data().aux.v.entriesPerBlock);
                vals.add("FILE_COUNT", m_data.data().aux.v.fileCount);
                vals.add("BITMAP_POINTER", m_data.data().aux.v.bitmapPointer);
                vals.add("TOTAL_BLOCKS", m_data.data().aux.v.totalBlocks);
                break;
            case FILETYPE_MASK_PRODOS_SUBVOL:
                vals.add("ENTRY_LENGTH", m_data.data().aux.sv.entryLen);
                vals.add("ENTRIES_PER_BLOCK", m_data.data().aux.sv.entriesPerBlock);
                vals.add("FILE_COUNT", m_data.data().aux.sv.fileCount);
                vals.add("PARENT_POINTER", m_data.data().aux.sv.parentPointer);
                vals.add("PARENT_ENTRY", m_data.data().aux.sv.parentEntry);
                vals.add("PARENT_ENTRY_LENGTH", m_data.data().aux.sv.parentEntryLen);
                break;
            default:
                vals.add("AUX_TYPE", m_data.data().aux.f.auxType);
                vals.add("LASTMOD_DATE", m_data.data().aux.f.mdate, m_data.data().aux.f.mdate.length);
                vals.add("LASTMOD_TIME", m_data.data().aux.f.mtime, m_data.data().aux.f.mtime.length);
                vals.add("HEADER_POINTER", m_data.data().aux.f.headerPointer);
                break;
        }
    }
}
