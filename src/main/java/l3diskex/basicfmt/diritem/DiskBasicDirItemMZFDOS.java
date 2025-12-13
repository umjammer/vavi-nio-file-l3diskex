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
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS.DirectoryMzFDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.Parambase.MyAttributes.getTypeByValue;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_LIBRARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeMZFDOS.FORMAT_TYPE_MZ_FDOS;


/// ディレクトリ１アイテム MZ Floppy DOS
public class DiskBasicDirItemMZFDOS extends DiskBasicDirItemMZBase<DirectoryMzFDos> {

    /**
     * ディレクトリエントリ MZ Floppy DOS (64bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMzFDos implements Directory {

        @Element(sequence = 1)
        public byte type;
        // file name has $0D on the end of string
        @Element(sequence = 2)
        public byte[] name = new byte[17];
        @Element(sequence = 3)
        public short fileSize;
        @Element(sequence = 4)
        public short loadAddress;
        @Element(sequence = 5)
        public short execAddress;
        @Element(sequence = 6)
        public short groups;
        // 0x30 0x53
        @Element(sequence = 7)
        public byte[] attr = new byte[2];
        // 0x00 0x00
        @Element(sequence = 8)
        public byte[] password = new byte[2];
        @Element(sequence = 9)
        public short dummySector; // 0x01 0x02

        @Element(sequence = 10)
        public byte[] mmddyy = new byte[7];
        @Element(sequence = 11)
        public byte[] reserved2 = new byte[13];
        @Element(sequence = 12)
        public byte track;
        @Element(sequence = 13)
        public byte sector;
        @Element(sequence = 14)
        public byte[] reserved3 = new byte[5];
        @Element(sequence = 15)
        public byte seqNum;
        @Element(sequence = 16)
        public byte unknown1; // 0x9x - 0xax
        @Element(sequence = 17)
        public byte unknown2; // 0x15
        @Element(sequence = 18)
        public byte dataTrack;
        @Element(sequence = 19)
        public byte dataSector;

        public static final int SIZE = 64;
    }

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
    static class MzFDosChain {

        public short sectors;
        public byte[] map = new byte[1];    // resizable
    }

    // MZ FDOS属性名
    static final Map<String, Object> typeNameMzFDos = new LinkedHashMap<>() {{
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
    static class DiskBasicDirItemMzFDosChain {

        private DiskBasic basic;
        private int sectorsPerTrack;
        private DiskImageSector sector;
        private MzFDosChain chain;
        private int mapSize;

        public DiskBasicDirItemMzFDosChain() {
            basic = null;
            sectorsPerTrack = 1;
            sector = null;
            chain = null;
            mapSize = 0;
        }

        // ポインタをセット
        public void set(DiskBasic basic, DiskImageSector sector, MzFDosChain chain) {
            this.basic = basic;
            this.sector = sector;
            this.chain = chain;
        }

        // メモリ確保
        public void alloc() {
            chain = new MzFDosChain();
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
        public boolean isUsedSector(int sectorPos) {
            if (chain == null || basic == null || sectorPos >= mapSize) return true;

            int mask = 1 << (sectorPos & 7);
            int idx = (sectorPos >> 3);

            int bits = basic.invertUint8(chain.map[idx]) & 0xff;
            return (bits & mask) != 0;
        }

        // セクタ数を返す
        public short getSectors() {
            return chain != null ? (basic != null ? basic.invertAndOrderUint16(chain.sectors) : chain.sectors) : 0;
        }

        // セクタ位置の使用状態を設定
        public void usedSector(int sectorPos, boolean val) {
            if (chain == null || basic == null || sectorPos >= mapSize) return;

            int mask = 1 << (sectorPos & 7);
            int idx = (sectorPos >> 3);

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
            sectorsPerTrack = val;
        }

        public void setMapSize(int val) {
            mapSize = val;
        }
    }

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryMzFDos> data = new DiskBasicDirData<>();

    /** チェイン情報 */
    private final DiskBasicDirItemMzFDosChain chain = new DiskBasicDirItemMzFDosChain();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_MZ_FDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryMzFDos.class);
        chain.setSectorsPerTrack(basic.getSectorsPerTrackOnBasic());
        chain.setMapSize(basic.getFatEndGroup());
        chain.alloc();
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryMzFDos.class, data, dataP);
        chain.setSectorsPerTrack(basic.getSectorsPerTrackOnBasic());
        chain.setMapSize(basic.getFatEndGroup());
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectoryMzFDos.class, data, dataP);
        chain.setSectorsPerTrack(basic.getSectorsPerTrackOnBasic());
        chain.setMapSize(basic.getFatEndGroup());

        used(checkUsed(unuse[0]));

        // チェインセクタへのポインタをセット
        if (isUsed()) {
            int group = getStartGroup(0);
            if (group != 0) {
                DiskImageSector targetSector = basic.getSectorFromGroup(group);
                if (targetSector != null) {
                    MzFDosChain chainData = new MzFDosChain();
                    Serdes.Util.deserialize(new ByteArrayInputStream(targetSector.getSectorBuffer()), chainData);
                    chain.set(basic, targetSector, chainData);
                }
            }
        }

        calcFileSize();
    }

    // アイテムへのポインタを設定
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryMzFDos.class, data, dataPos);
    }

    // ファイル名を格納する位置を返す
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = data.data().name.length;
            len[0] = size[0] - 1;
            return data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    // 属性１を返す
    @Override
    public int getFileType1() {
        return basic.invertUint8(data.data().type) & 0xff; // invert;
    }

    // 属性２を返す
    @Override
    public int getFileType2() {
        int attr = basic.invertUint8(data.data().attr[0]) & 0xff; // invert;
        attr <<= 8;
        attr |= basic.invertUint8(data.data().attr[1]) & 0xff; // invert;
        return attr;
    }

    // 属性１のセット
    @Override
    protected void setFileType1(int val) {
        data.data().type = basic.invertUint8((byte) val); // invert
    }

    // 属性２のセット
    @Override
    protected void setFileType2(int val) {
        data.data().attr[0] = basic.invertUint8((byte) ((val >> 8) & 0xff)); // invert
        data.data().attr[1] = basic.invertUint8((byte) (val & 0xff)); // invert
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
        if (!data.isValid()) return false;

        boolean valid = true;
        int t = getFileType1();
        if (t != 0xfe && (t & 0x60) != 0) {
            valid = false;
        }
        if (valid && !last[0]) {
            valid = DiskBasicDirItem.checkData(data.getRawData(), getDataSize(), last);
        }
        return valid;
    }

    // 属性を設定
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        int t2 = 0;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = (fileType.getOrigin() & 0xff);
            t2 = (fileType.getOrigin() >> 8);
        } else {
            t1 = convToNativeType(fType);
            t2 = MZ_FDOS_NO_PROTECT;
        }
        setFileType1(t1);
        setFileType2(t2);
    }

    // 属性を変換
    private int convToNativeType(int fileType) {
        // MZ
        int val = 0;
        if ((fileType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            if ((fileType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                val = FILETYPE_MZ_FDOS_OBJ;
            } else {
                val = FILETYPE_MZ_FDOS_RB;
            }
        } else if ((fileType & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_FDOS_SYS;
        } else if ((fileType & FILE_TYPE_LIBRARY_MASK.getValue()) != 0) {
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
                val = getTypeByValue(basic.getSpecialAttributes(), t1);
                break;
        }
        int t2 = getFileType2();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t2 << 8 | t1);
    }

    // 属性の文字列を返す(ファイル一覧画面表示用)
    @Override
    public String getFileAttrStr() {
        String[] attr = new String[1];
        getFileAttrName(convFileType1Pos(getFileType1()), typeNameMzFDos, attr, TYPE_NAME_MZ_FDOS_UNKNOWN);
        return attr[0];
    }

    // データ内にファイルサイズをセット
    @Override
    public void setFileSizeBase(int val) {
        data.data().fileSize = basic.invertAndOrderUint16((short) val); // invert
    }

    // データ内のファイルサイズを返す
    @Override
    public int getFileSizeBase() {
        return basic.invertAndOrderUint16(data.data().fileSize); // invert
    }

    // ファイルサイズとグループ数を計算する
    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed()) return;

        // ファイルサイズ
        //m_file_size = basic.invertAndOrderUint16(data.data().file_size); // invert

        getUnitGroups(fileUnitNum, groups);
    }

    // グループ数をセット
    @Override
    public void setGroupSize(int val) {
        groups.setNums(val);
        data.data().groups = basic.invertAndOrderUint16((short) val);
        chain.setSectors((short) val);
    }

    // グループ数を返す
    @Override
    public int getGroupSize() {
        return basic.invertAndOrderUint16(data.data().groups);
    }

    // グループ取得計算前処理
    @Override
    protected void preCalcAllGroups(int[] calcFlags, int[] groupNum, int[] remain, int[] sectorSize, Object[] userData) {
        sectorSize[0] -= 2;

        groupNum[0] = getDataGroup();
    }

    // グループ取得計算中処理
    @Override
    protected void calcAllGroups(int calcFlags, int[] groupNum, int[] remain, int[] sectorSize, int[] endSector, Object userData) {
        groupNum[0] = type.getNextGroupNumber(groupNum[0], endSector[0]);
    }

    private static final IntBinaryOperator getDigit = (b1, b2) -> '0' <= b1 && b1 <= '9' && '0' <= b2 && b2 <= '9' ? (b1 & 0xf) * 10 + (b2 & 0xf) : -1;

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        byte[] mmddyy = new byte[data.data().mmddyy.length];
        basic.invertMemory(data.data().mmddyy, data.data().mmddyy.length, mmddyy);

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
        byte[] mmddyy = new byte[data.data().mmddyy.length];
        int year = tm.getYear() % 100;
        mmddyy[4] = (tm.getYear() >= 0 ? getBCDChar.apply(year) : (byte) '?');
        mmddyy[5] = (tm.getYear() >= 0 ? getBCDCharMod.apply(year) : (byte) '?');
        int mon = tm.getMonth().ordinal() + 1;
        mmddyy[0] = (tm.getMonth().ordinal() >= 0 ? getBCDChar.apply(mon) : (byte) '?');
        mmddyy[1] = (tm.getMonth().ordinal() >= 0 ? getBCDCharMod.apply(mon) : (byte) '?');
        mmddyy[2] = (tm.getDayOfMonth() >= 0 ? getBCDChar.apply(tm.getDayOfMonth()) : (byte) '?');
        mmddyy[3] = (tm.getDayOfMonth() >= 0 ? getBCDCharMod.apply(tm.getDayOfMonth()) : (byte) '?');
        mmddyy[6] = 0x0d;
        basic.invertMemory(mmddyy, data.data().mmddyy.length, data.data().mmddyy);
    }

    // 開始アドレスを返す
    @Override
    public int getStartAddress() {
        return basic.invertAndOrderUint16(data.data().loadAddress) & 0xffff; // invert
    }

    // 実行アドレスを返す
    @Override
    public int getExecuteAddress() {
        return basic.invertAndOrderUint16(data.data().execAddress) & 0xffff; // invert
    }

    // 開始アドレスをセット
    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = basic.invertAndOrderUint16((short) val);    // invert
    }

    // 実行アドレスをセット
    @Override
    public void setExecuteAddress(int val) {
        data.data().execAddress = basic.invertUint16((short) val); // invert
    }

    // ディレクトリアイテムのサイズ
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    // アイテムを返す
    @Override
    public DirectoryMzFDos getData() {
        return data.data();
    }

    // アイテムをコピー
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val, getDataSize());
    }

    // ディレクトリをクリア ファイル新規作成時
    @Override
    public void clearData() {
        if (!data.isValid()) return;

        data.fill(0);

        byte sp = basic.getDirSpaceCode();
        // 名前は初期値
        Arrays.fill(data.data().name, sp);
        // 日付は初期値
        Arrays.fill(data.data().mmddyy, (byte) '?');
        data.data().mmddyy[data.data().mmddyy.length - 1] = sp;
        // 反転
        basic.invertMemory(data.getRawData(), DirectoryMzFDos.SIZE); // invert
    }

    // 最初のグループ番号をセット
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int[] track = {0};
        int[] sector = {1};
        basic.calcNumFromSectorPosTForGroup(val * basic.getSectorsPerGroup(), track, sector);
        data.data().track = basic.invertUint8((byte) track[0]); // invert
        data.data().sector = basic.invertUint8((byte) sector[0]); // invert
    }

    // 最初のグループ番号を返す
    @Override
    public int getStartGroup(int fileUnitNum) {
        int track = basic.invertUint8(data.data().track) & 0xff; // invert
        int sec = basic.invertUint8(data.data().sector) & 0xff; // invert
        return basic.calcSectorPosFromNumTForGroup(track, sec);
    }

    // 追加のグループ番号をセット
    @Override
    public void setExtraGroup(int val) {
        int[] track = {0};
        int[] sector = {1};
        basic.calcNumFromSectorPosTForGroup(val * basic.getSectorsPerGroup(), track, sector);
        data.data().track = basic.invertUint8((byte) track[0]); // invert
        data.data().sector = basic.invertUint8((byte) sector[0]); // invert
    }

    // 追加のグループ番号を返す
    @Override
    public int getExtraGroup() {
        int track = basic.invertUint8(data.data().track) & 0xff; // invert
        int sector = basic.invertUint8(data.data().sector) & 0xff; // invert
        return basic.calcSectorPosFromNumTForGroup(track, sector);
    }

    // 追加のグループ番号を得る
    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    // データのあるグループ番号をセット
    public void setDataGroup(int val) {
        int[] track = {0};
        int[] sector = {1};
        basic.calcNumFromSectorPosTForGroup(val * basic.getSectorsPerGroup(), track, sector);
        data.data().dataTrack = basic.invertUint8((byte) track[0]); // invert
        data.data().dataSector = basic.invertUint8((byte) sector[0]); // invert
    }

    // データのあるグループ番号を返す
    public int getDataGroup() {
        int track = basic.invertUint8(data.data().dataTrack) & 0xff; // invert
        int sector = basic.invertUint8(data.data().dataSector) & 0xff; // invert
        return basic.calcSectorPosFromNumTForGroup(track, sector);
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
        data.data().unknown1 = basic.invertUint8((byte) 0x9f);
        data.data().unknown2 = basic.invertUint8((byte) 0x15);
    }

    // データをチェインする必要があるか（非連続データか）
    @Override
    public boolean needChainInData() {
        return true;
    }

    // データをエクスポートする前に必要な処理
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        // 属性から拡張子を付加する
        String[] ext = new String[1];
        if (getFileAttrName(convFileType1Pos(getFileType1()), typeNameMzFDos, ext)) {
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
        data.data().seqNum = basic.invertUint8((byte) num);
    }

    /** チェイン情報にセクタをセット */
    @Override
    public void setChainSector(DiskImageSector sector, byte[] data, DiskBasicDirItem<DirectoryMzFDos> pItem) throws IOException {
        MzFDosChain chain_data = new MzFDosChain();
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
        if (config.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], typeNameMzFDos, TYPE_NAME_MZ_FDOS_OBJ, TYPE_NAME_MZ_FDOS_GRH, filename, null, null);
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
        if (!isContainAttrByExtension(filename, typeNameMzFDos, TYPE_NAME_MZ_FDOS_OBJ, TYPE_NAME_MZ_FDOS_GRH, null, t1, null)) {
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
    public int convFileType1Pos(int nativeType) {
        int val = -1;
        int i = 0;
        for (Object v : typeNameMzFDos.values()) {
            if (nativeType == (int) v) {
                val = i++;
                break;
            }
        }
        if (val < 0) {
            val = -nativeType;
        }
        return val;
    }

    // プロパティで表示する内部データを設定
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("inverted", basic.isDataInverted());

        vals.add("TYPE", data.data().type, basic.isDataInverted());
        vals.add("NAME", data.data().name, data.data().name.length, basic.isDataInverted());
        vals.add("FILE_SIZE", data.data().fileSize, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", data.data().loadAddress, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", data.data().execAddress, basic.isBigEndian(), basic.isDataInverted());
        vals.add("GROUPS", data.data().groups, basic.isBigEndian(), basic.isDataInverted());
        vals.add("ATTR", data.data().attr, data.data().attr.length, basic.isDataInverted());
        vals.add("PASSWORD", data.data().password, data.data().password.length, basic.isDataInverted());
        vals.add("DUMMY_SECTOR", data.data().dummySector, basic.isBigEndian(), basic.isDataInverted());

        vals.add("MMDDYY", data.data().mmddyy, data.data().mmddyy.length, basic.isDataInverted());
        vals.add("RESERVED2", data.data().reserved2, data.data().reserved2.length, basic.isDataInverted());
        vals.add("TRACK", data.data().track, basic.isDataInverted());
        vals.add("SECTOR", data.data().sector, basic.isDataInverted());
        vals.add("RESERVED3", data.data().reserved3, data.data().reserved3.length, basic.isDataInverted());
        vals.add("SEQ_NUM", data.data().seqNum, basic.isDataInverted());
        vals.add("UNKNOWN1", data.data().unknown1, basic.isDataInverted());
        vals.add("UNKNOWN2", data.data().unknown2, basic.isDataInverted());
        vals.add("DATA_TRACK", data.data().dataTrack, basic.isDataInverted());
        vals.add("DATA_SECTOR", data.data().dataSector, basic.isDataInverted());
    }
}
