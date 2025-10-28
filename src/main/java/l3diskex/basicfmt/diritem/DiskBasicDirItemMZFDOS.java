/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryMzFdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.Parambase.MyAttributes.getTypeByValue;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_LIBRARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;


/// ディレクトリ１アイテム MZ Floppy DOS
public class DiskBasicDirItemMZFDOS extends DiskBasicDirItemMZBase<DirectoryMzFdos> {

    static final int TYPE_NAME_MZ_FDOS_UNKNOWN = 0;
    static final int TYPE_NAME_MZ_FDOS_OBJ = 1;
    static final int TYPE_NAME_MZ_FDOS_BTX = 2;
    static final int TYPE_NAME_MZ_FDOS_DAT = 3;
    static final int TYPE_NAME_MZ_FDOS_ASC = 4;
    static final int TYPE_NAME_MZ_FDOS_RB = 5;
    static final int TYPE_NAME_MZ_FDOS_FTN = 6;
    static final int TYPE_NAME_MZ_FDOS_LIB = 7;
    static final int TYPE_NAME_MZ_FDOS_PAS = 8;
    static final int TYPE_NAME_MZ_FDOS_TEM = 9;
    static final int TYPE_NAME_MZ_FDOS_SYS = 10;
    static final int TYPE_NAME_MZ_FDOS_GR = 11;
    static final int TYPE_NAME_MZ_FDOS_GRH = 12;

    // MZ Floppy DOS 属性
    static final int FILETYPE_MZ_FDOS_UNKNOWN = 0x0;
    static final int FILETYPE_MZ_FDOS_OBJ = 0x1;
    static final int FILETYPE_MZ_FDOS_BTX = 0x2;
    static final int FILETYPE_MZ_FDOS_DAT = 0x3;
    static final int FILETYPE_MZ_FDOS_ASC = 0x4;
    static final int FILETYPE_MZ_FDOS_RB = 0x5;
    static final int FILETYPE_MZ_FDOS_FTN = 0x6;
    static final int FILETYPE_MZ_FDOS_LIB = 0x7;
    static final int FILETYPE_MZ_FDOS_PAS = 0x8;
    static final int FILETYPE_MZ_FDOS_TEM = 0x9;
    static final int FILETYPE_MZ_FDOS_SYS = 0xa;
    static final int FILETYPE_MZ_FDOS_GR = 0xb;
    static final int FILETYPE_MZ_FDOS_GRH = 0xc;

    // FDOSチェイン情報
    static class mz_fdos_chain_t {

        public short sectors;
        public byte[] map = new byte[1];    // resizable
    }

    // MZ FDOS属性名
    static final Map<String, Object> gTypeNameMZFDOS = new LinkedHashMap<>() {{
        put("???", FILETYPE_MZ_FDOS_UNKNOWN);
        put("OBJ", FILETYPE_MZ_FDOS_OBJ);
        put("BTX", FILETYPE_MZ_FDOS_BTX);
        put("DAT", FILETYPE_MZ_FDOS_DAT);
        put("ASC", FILETYPE_MZ_FDOS_ASC);
        put("RB", FILETYPE_MZ_FDOS_RB);
        put("FTN", FILETYPE_MZ_FDOS_FTN);
        put("LIB", FILETYPE_MZ_FDOS_LIB);
        put("PAS", FILETYPE_MZ_FDOS_PAS);
        put("TEM", FILETYPE_MZ_FDOS_TEM);
        put("SYS", FILETYPE_MZ_FDOS_SYS);
        put("GR", FILETYPE_MZ_FDOS_GR);
        put("GRH", FILETYPE_MZ_FDOS_GRH);
    }};

    static final int MZ_FDOS_NO_PROTECT = 0x3053;    // "0S"

    // FDOSチェイン情報アクセス
    static class DiskBasicDirItemMZFDOSChain {

        private DiskBasic basic;
        private int secs_per_track;
        private DiskImageSector sector;
        private mz_fdos_chain_t chain;
        private int map_size;

        public DiskBasicDirItemMZFDOSChain() {
            basic = null;
            secs_per_track = 1;
            sector = null;
            chain = null;
            map_size = 0;
        }

        // ポインタをセット
        public void set(DiskBasic n_basic, DiskImageSector n_sector, mz_fdos_chain_t n_chain) {
            basic = n_basic;
            sector = n_sector;
            chain = n_chain;
        }

        // メモリ確保
        public void alloc() {
            chain = new mz_fdos_chain_t();
        }

        // クリア
        public void clear() {
            if (sector != null) sector.fill((byte) 0);
            else if (chain != null) {
                chain.sectors = 0;
                Arrays.fill(chain.map, (byte) 0);
            }
        }

        // 有効か
        public boolean isValid() {
            return chain != null;
        }

        // セクタ位置の使用状態を返す
        public boolean isUsedSector(int sector_pos) {
            if (chain == null || basic == null || sector_pos >= map_size) return true;

            int mask = 1 << (sector_pos & 7);
            int idx = (sector_pos >> 3);

            int bits = basic.invertUint8(chain.map[idx]) & 0xff;
            return (bits & mask) != 0;
        }

        // セクタ数を返す
        public short getSectors() {
            return chain != null ? (basic != null ? basic.invertAndOrderUint16(chain.sectors) : chain.sectors) : 0;
        }

        // セクタ位置の使用状態を設定
        public void usedSector(int sector_pos, boolean val) {
            if (chain == null || basic == null || sector_pos >= map_size) return;

            int mask = 1 << (sector_pos & 7);
            int idx = (sector_pos >> 3);

            int bits = basic.invertUint8(chain.map[idx]) & 0xff;
            bits = (val ? bits | mask : bits & ~mask);
            chain.map[idx] = basic.invertUint8((byte) bits);
        }

        // セクタ数を設定
        public void setSectors(short val) {
            if (chain != null) {
                chain.sectors = basic != null ? basic.invertAndOrderUint16(val) : val;
            }
        }

        public void setSectorsPerTrack(int val) {
            secs_per_track = val;
        }

        public void setMapSize(int val) {
            map_size = val;
        }
    }

    //
    //
    //

    // ディレクトリデータ
    private final DiskBasicDirData<DirectoryMzFdos> m_data = new DiskBasicDirData<>();

    // チェイン情報
    private final DiskBasicDirItemMZFDOS.DiskBasicDirItemMZFDOSChain chain = new DiskBasicDirItemMZFDOSChain();

    public DiskBasicDirItemMZFDOS(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryMzFdos.class);
        chain.setSectorsPerTrack(basic.diskBasicParam.getSectorsPerTrackOnBasic());
        chain.setMapSize(basic.diskBasicParam.getFatEndGroup());
        chain.alloc();
    }

    public DiskBasicDirItemMZFDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryMzFdos.class, n_data, dataP);
        chain.setSectorsPerTrack(basic.diskBasicParam.getSectorsPerTrackOnBasic());
        chain.setMapSize(basic.diskBasicParam.getFatEndGroup());
    }

    public DiskBasicDirItemMZFDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryMzFdos.class, n_data, dataP);
        chain.setSectorsPerTrack(basic.diskBasicParam.getSectorsPerTrackOnBasic());
        chain.setMapSize(basic.diskBasicParam.getFatEndGroup());

        used(checkUsed(n_unuse[0]));

        // チェインセクタへのポインタをセット
        if (isUsed()) {
            int grp = getStartGroup(0);
            if (grp != 0) {
                DiskImageSector sector = basic.getSectorFromGroup(grp);
                if (sector != null) {
                    DiskBasicDirItemMZFDOS.mz_fdos_chain_t chain_data = new mz_fdos_chain_t();
                    Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), chain_data);
                    chain.set(basic, sector, chain_data);
                }
            }
        }

        calcFileSize();
    }

    // アイテムへのポインタを設定
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryMzFdos.class, n_data, dataP);
    }

    // ファイル名を格納する位置を返す
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = m_data.data().name.length;
            len[0] = size[0] - 1;
            return m_data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    // 属性１を返す
    @Override
    public int getFileType1() {
        return basic.invertUint8(m_data.data().type) & 0xff; // invert;
    }

    // 属性２を返す
    @Override
    public int getFileType2() {
        int attr = basic.invertUint8(m_data.data().attr[0]) & 0xff; // invert;
        attr <<= 8;
        attr |= basic.invertUint8(m_data.data().attr[1]) & 0xff; // invert;
        return attr;
    }

    // 属性１のセット
    @Override
    protected void setFileType1(int val) {
        m_data.data().type = basic.invertUint8((byte) val); // invert
    }

    // 属性２のセット
    @Override
    protected void setFileType2(int val) {
        m_data.data().attr[0] = basic.invertUint8((byte) ((val >> 8) & 0xff)); // invert
        m_data.data().attr[1] = basic.invertUint8((byte) (val & 0xff)); // invert
    }

    // 使用しているアイテムか
    @Override
    public boolean checkUsed(boolean unuse) {
        int typ1 = getFileType1();
        return (typ1 != 0 && typ1 != 0xfe);
    }

//    /** 削除 */
//    @Override
//    public boolean delete() throws IOException {
//        // エントリの先頭にコードを入れる
//        setFileType1(basic.diskBasicParam.getDeleteCode());
//        used(false);
//        // 開始グループを未使用にする
//        type.setGroupNumber(getStartGroup(0), 0);
//        return true;
//    }

    /** ディレクトリアイテムのチェック */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        int t = getFileType1();
        if (t != 0xfe && (t & 0x60) != 0) {
            valid = false;
        }
        if (valid && !last[0]) {
            valid = DiskBasicDirItem.checkData(m_data.getRawData(), getDataSize(), last);
        }
        return valid;
    }

    // 属性を設定
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int t2 = 0;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            t1 = (file_type.getOrigin() & 0xff);
            t2 = (file_type.getOrigin() >> 8);
        } else {
            t1 = convToNativeType(ftype);
            t2 = MZ_FDOS_NO_PROTECT;
        }
        setFileType1(t1);
        setFileType2(t2);
    }

    // 属性を変換
    private int convToNativeType(int file_type) {
        // MZ
        int val = 0;
        if ((file_type & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            if ((file_type & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                val = FILETYPE_MZ_FDOS_OBJ;
            } else {
                val = FILETYPE_MZ_FDOS_RB;
            }
        } else if ((file_type & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_FDOS_SYS;
        } else if ((file_type & FILE_TYPE_LIBRARY_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_FDOS_LIB;
        } else {
            val = FILETYPE_MZ_FDOS_ASC;
        }
        return val;
    }

    // 属性を返す
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1) {
            case 0x1: // FILETYPE_MZ_FDOS_OBJ
                val = FILE_TYPE_BINARY_MASK.getValue();    // binary
                val |= FILE_TYPE_MACHINE_MASK.getValue();  // machine
                break;
            case 0x4: // FILETYPE_MZ_FDOS_ASC
                val = FILE_TYPE_ASCII_MASK.getValue();
                break;
            case 0x5: // FILETYPE_MZ_FDOS_RB
                val = FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case 0xa: // FILETYPE_MZ_FDOS_SYS
                val = FILE_TYPE_SYSTEM_MASK.getValue();    // system
                break;
            case 0x7: // FILETYPE_MZ_FDOS_LIB
                val = FILE_TYPE_LIBRARY_MASK.getValue();   // library
                break;
            default:
                val = getTypeByValue(basic.diskBasicParam.getSpecialAttributes(), t1);
                break;
        }
        int t2 = getFileType2();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t2 << 8 | t1);
    }

    // 属性の文字列を返す(ファイル一覧画面表示用)
    @Override
    public String getFileAttrStr() {
        String[] attr = new String[1];
        getFileAttrName(convFileType1Pos(getFileType1()), gTypeNameMZFDOS, attr, TYPE_NAME_MZ_FDOS_UNKNOWN);
        return attr[0];
    }

    // データ内にファイルサイズをセット
    @Override
    public void setFileSizeBase(int val) {
        m_data.data().fileSize = basic.invertAndOrderUint16((short) val); // invert
    }

    // データ内のファイルサイズを返す
    @Override
    public int getFileSizeBase() {
        return basic.invertAndOrderUint16(m_data.data().fileSize); // invert
    }

    // ファイルサイズとグループ数を計算する
    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!isUsed()) return;

        // ファイルサイズ
        //m_file_size = basic.invertAndOrderUint16(m_data.data().file_size); // invert

        getUnitGroups(fileunit_num, groups);
    }

    // グループ数をセット
    @Override
    public void setGroupSize(int val) {
        groups.setNums(val);
        m_data.data().groups = basic.invertAndOrderUint16((short) val);
        chain.setSectors((short) val);
    }

    // グループ数を返す
    @Override
    public int getGroupSize() {
        return basic.invertAndOrderUint16(m_data.data().groups);
    }

    // グループ取得計算前処理
    @Override
    protected void preCalcAllGroups(int[] calc_flags, int[] group_num, int[] remain, int[] sec_size, Object[] user_data) {
        sec_size[0] -= 2;

        group_num[0] = getDataGroup();
    }

    // グループ取得計算中処理
    @Override
    protected void calcAllGroups(int calc_flags, int[] group_num, int[] remain, int[] sec_size, int[] end_sec, Object user_data) {
        group_num[0] = type.getNextGroupNumber(group_num[0], end_sec[0]);
    }

    private static IntBinaryOperator getDigit = (b1, b2) -> '0' <= b1 && b1 <= '9' && '0' <= b2 && b2 <= '9' ? (b1 & 0xf) * 10 + (b2 & 0xf) : -1;

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        byte[] mmddyy = new byte[m_data.data().mmddyy.length];
        basic.invertMem(m_data.data().mmddyy, m_data.data().mmddyy.length, mmddyy);

        int year = getDigit.applyAsInt(mmddyy[4], mmddyy[5]);
        int month = getDigit.applyAsInt(mmddyy[0], mmddyy[1]);
        int day = getDigit.applyAsInt(mmddyy[2], mmddyy[3]);
        return LocalDate.of(
                year +
                        (0 <= tm.getYear() && tm.getYear() < 80 ? 100 : 0),    // 2000 - 2079
                month != -1 ? month - 1 : -2,
                day);
    }

    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    private static IntFunction<Byte> getBCDChar = (val) -> (byte) (((val / 10) + 0x30));
    private static IntFunction<Byte> getBCDCharMod = (val) -> (byte) (((val % 10) + 0x30));

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        byte[] mmddyy = new byte[m_data.data().mmddyy.length];
        int year = tm.getYear() % 100;
        mmddyy[4] = (tm.getYear() >= 0 ? getBCDChar.apply(year) : (byte) '?');
        mmddyy[5] = (tm.getYear() >= 0 ? getBCDCharMod.apply(year) : (byte) '?');
        int mon = tm.getMonth().ordinal() + 1;
        mmddyy[0] = (tm.getMonth().ordinal() >= 0 ? getBCDChar.apply(mon) : (byte) '?');
        mmddyy[1] = (tm.getMonth().ordinal() >= 0 ? getBCDCharMod.apply(mon) : (byte) '?');
        mmddyy[2] = (tm.getDayOfMonth() >= 0 ? getBCDChar.apply(tm.getDayOfMonth()) : (byte) '?');
        mmddyy[3] = (tm.getDayOfMonth() >= 0 ? getBCDCharMod.apply(tm.getDayOfMonth()) : (byte) '?');
        mmddyy[6] = 0x0d;
        basic.invertMem(mmddyy, m_data.data().mmddyy.length, m_data.data().mmddyy);
    }

    // 開始アドレスを返す
    @Override
    public int getStartAddress() {
        return basic.invertAndOrderUint16(m_data.data().loadAddr) & 0xffff; // invert
    }

    // 実行アドレスを返す
    @Override
    public int getExecuteAddress() {
        return basic.invertAndOrderUint16(m_data.data().execAddr) & 0xffff; // invert
    }

    // 開始アドレスをセット
    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = basic.invertAndOrderUint16((short) val);    // invert
    }

    // 実行アドレスをセット
    @Override
    public void setExecuteAddress(int val) {
        m_data.data().execAddr = basic.invertUint16((short) val); // invert
    }

    // ディレクトリアイテムのサイズ
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    // アイテムを返す
    @Override
    public DirectoryMzFdos getData() {
        return m_data.data();
    }

    // アイテムをコピー
    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val, getDataSize());
    }

    // ディレクトリをクリア ファイル新規作成時
    @Override
    public void clearData() {
        if (!m_data.isValid()) return;

        m_data.fill(0);

        byte sp = basic.diskBasicParam.getDirSpaceCode();
        // 名前は初期値
        java.util.Arrays.fill(m_data.data().name, sp);
        // 日付は初期値
        java.util.Arrays.fill(m_data.data().mmddyy, (byte) '?');
        m_data.data().mmddyy[m_data.data().mmddyy.length - 1] = sp;
        // 反転
        basic.invertMem(m_data.getRawData(), DirectoryMzFdos.SIZE); // invert
    }

    // 最初のグループ番号をセット
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        int[] trk = {0};
        int[] sec = {1};
        basic.calcNumFromSectorPosTForGroup(val * basic.getSectorsPerGroup(), trk, sec);
        m_data.data().track = basic.invertUint8((byte) trk[0]); // invert
        m_data.data().sector = basic.invertUint8((byte) sec[0]); // invert
    }

    // 最初のグループ番号を返す
    @Override
    public int getStartGroup(int fileunit_num) {
        int trk = basic.invertUint8(m_data.data().track) & 0xff; // invert
        int sec = basic.invertUint8(m_data.data().sector) & 0xff; // invert
        return basic.calcSectorPosFromNumTForGroup(trk, sec);
    }

    // 追加のグループ番号をセット
    @Override
    public void setExtraGroup(int val) {
        int[] trk = {0};
        int[] sec = {1};
        basic.calcNumFromSectorPosTForGroup(val * basic.getSectorsPerGroup(), trk, sec);
        m_data.data().track = basic.invertUint8((byte) trk[0]); // invert
        m_data.data().sector = basic.invertUint8((byte) sec[0]); // invert
    }

    // 追加のグループ番号を返す
    @Override
    public int getExtraGroup() {
        int trk = basic.invertUint8(m_data.data().track) & 0xff; // invert
        int sec = basic.invertUint8(m_data.data().sector) & 0xff; // invert
        return basic.calcSectorPosFromNumTForGroup(trk, sec);
    }

    // 追加のグループ番号を得る
    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    // データのあるグループ番号をセット
    public void setDataGroup(int val) {
        int[] trk = {0};
        int[] sec = {1};
        basic.calcNumFromSectorPosTForGroup(val * basic.getSectorsPerGroup(), trk, sec);
        m_data.data().dataTrack = basic.invertUint8((byte) trk[0]); // invert
        m_data.data().dataSector = basic.invertUint8((byte) sec[0]); // invert
    }

    // データのあるグループ番号を返す
    public int getDataGroup() {
        int trk = basic.invertUint8(m_data.data().dataTrack) & 0xff; // invert
        int sec = basic.invertUint8(m_data.data().dataSector) & 0xff; // invert
        return basic.calcSectorPosFromNumTForGroup(trk, sec);
    }

    // ファイル名に設定できない文字を文字列にして返す
    public String invalidateChars() {
        return "\"\\:*?";
    }

    // ファイル名に付随する拡張属性を設定
    @Override
    public void setOptionalName(int val) {
        val &= 0xf;
        val |= (getFileType1() & 0xf0);
        setFileType1(val);
    }

    // ファイル名に付随する拡張属性を返す
    @Override
    public int getOptionalName() {
        return getFileType1() & 0xf;
    }

    // エントリデータの不明部分を設定
    public void setUnknownData() {
        m_data.data().unknown1 = basic.invertUint8((byte) 0x9f);
        m_data.data().unknown2 = basic.invertUint8((byte) 0x15);
    }

    // データをチェインする必要があるか（非連続データか）
    @Override
    public boolean needChainInData() {
        return true;
    }

    // データをエクスポートする前に必要な処理
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.isAddExtensionExport()) return true;

        // 属性から拡張子を付加する
        String[] ext = new String[1];
        if (getFileAttrName(convFileType1Pos(getFileType1()), gTypeNameMZFDOS, ext)) {
            filename[0] += ".";
            if (Utils.isUpperString(filename[0])) {
                filename[0] += ext[0].toUpperCase();
            } else {
                filename[0] += ext[0].toLowerCase();
            }
        }
        return true;
    }

    /** シーケンス番号 */
    public void assignSeqNumber() {
        m_data.data().seqNum = basic.invertUint8((byte) num);
    }

    /** チェイン情報にセクタをセット */
    @Override
    public void setChainSector(DiskImageSector sector, byte[] data, DiskBasicDirItem<DirectoryMzFdos> pitem) throws IOException {
        mz_fdos_chain_t chain_data = new mz_fdos_chain_t();
        Serdes.Util.deserialize(new ByteArrayInputStream(data), chain_data);
        chain.set(basic, sector, chain_data);
    }

    /** チェイン情報にセクタをセット */
    public void setChainUsedSector(int sector_pos, boolean val) {
        chain.usedSector(sector_pos, val);
    }

    /** インポート時のダイアログを出す前にファイルパスから内部ファイル名を生成する */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], gTypeNameMZFDOS, TYPE_NAME_MZ_FDOS_OBJ, TYPE_NAME_MZ_FDOS_GRH, filename, null, null);
        }
        // 拡張子を消す
        filename[0] = remakeFileNameOnlyStr(filename[0]);
        return true;
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        // 拡張子で属性を設定する
        if (!isContainAttrByExtension(filename, gTypeNameMZFDOS, TYPE_NAME_MZ_FDOS_OBJ, TYPE_NAME_MZ_FDOS_GRH, null, t1, null)) {
            t1[0] = FILETYPE_MZ_FDOS_ASC;
        }

        // プロテクト
        t1[0] |= (MZ_FDOS_NO_PROTECT << 8);

        return t1[0];
    }

    /** ファイル名から拡張属性を決定する */
    public int ConvOptionalNameFromFileName(String filename) {
        return (convOriginalTypeFromFileName(filename) & 0xff);
    }

    //
    // ダイアログ用
    //

    // 属性からリストの位置を返す(プロパティダイアログ用)
    public int convFileType1Pos(int native_type) {
        int val = -1;
        int i = 0;
        for (Object v : gTypeNameMZFDOS.values()) {
            if (native_type == (int) v) {
                val = i++;
                break;
            }
        }
        if (val < 0) {
            val = -native_type;
        }
        return val;
    }

    // プロパティで表示する内部データを設定
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("inverted", basic.isDataInverted());

        vals.add("TYPE", m_data.data().type, basic.isDataInverted());
        vals.add("NAME", m_data.data().name, m_data.data().name.length, basic.isDataInverted());
        vals.add("FILE_SIZE", m_data.data().fileSize, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", m_data.data().loadAddr, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", m_data.data().execAddr, basic.isBigEndian(), basic.isDataInverted());
        vals.add("GROUPS", m_data.data().groups, basic.isBigEndian(), basic.isDataInverted());
        vals.add("ATTR", m_data.data().attr, m_data.data().attr.length, basic.isDataInverted());
        vals.add("PASSWORD", m_data.data().password, m_data.data().password.length, basic.isDataInverted());
        vals.add("DUMMY_SECTOR", m_data.data().dummySector, basic.isBigEndian(), basic.isDataInverted());

        vals.add("MMDDYY", m_data.data().mmddyy, m_data.data().mmddyy.length, basic.isDataInverted());
        vals.add("RESERVED2", m_data.data().reserved2, m_data.data().reserved2.length, basic.isDataInverted());
        vals.add("TRACK", m_data.data().track, basic.isDataInverted());
        vals.add("SECTOR", m_data.data().sector, basic.isDataInverted());
        vals.add("RESERVED3", m_data.data().reserved3, m_data.data().reserved3.length, basic.isDataInverted());
        vals.add("SEQ_NUM", m_data.data().seqNum, basic.isDataInverted());
        vals.add("UNKNOWN1", m_data.data().unknown1, basic.isDataInverted());
        vals.add("UNKNOWN2", m_data.data().unknown2, basic.isDataInverted());
        vals.add("DATA_TRACK", m_data.data().dataTrack, basic.isDataInverted());
        vals.add("DATA_SECTOR", m_data.data().dataSector, basic.isDataInverted());
    }
}
