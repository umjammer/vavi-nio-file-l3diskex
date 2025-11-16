///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;

import l3diskex.Parambase;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFAT8.DiskBasicDirItemFAT8F.DirectoryFat8F;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;


/** ディレクトリ１アイテム FAT8ビット */
public abstract class DiskBasicDirItemFAT8<T extends Directory> extends DiskBasicDirItem<T> {

    private static final Logger logger = System.getLogger(DiskBasicDirItemFAT8.class.getName());

    /// L3/S1/F BASIC タイプ1 0...BASIC 1...DATA 2...MACHINE
    public static final String[] TYPE_NAME_1 = {
            "BASIC",
            "Data",
            "Machine",
            "???"
    };

    /// L3 BASIC and F-BASIC タイプ1 0...BASIC 1...DATA 2...MACHINE
    public static final int TYPE_NAME_1_BASIC = 0;
    public static final int TYPE_NAME_1_DATA = 1;
    public static final int TYPE_NAME_1_MACHINE = 2;
    static final int TYPE_NAME_1_UNKNOWN = 3;

    /// L3/S1/F BASIC タイプ2 0...Binary 1...Ascii 2...Random Access
    public static final String[] TYPE_NAME_2 = {
            "Binary",
            "Ascii",
            "Random Access"
    };

    /// L3 BASIC and F-BASIC タイプ2 0...Binary 1...Ascii 2...Random Access
    public static final int TYPE_NAME_2_BINARY = 0;
    public static final int TYPE_NAME_2_ASCII = 1;
    public static final int TYPE_NAME_2_RANDOM = 2;

    //
    //
    //

    /** ファイル内部で持っている開始アドレス */
    protected int startAddress;
    /** ファイル内部で持っている終了アドレス */
    protected int endAddress;
    /** ファイル内部で持っている実行アドレス */
    protected int execAddress;

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        startAddress = -1;
        endAddress = -1;
        execAddress = -1;
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        startAddress = -1;
        endAddress = -1;
        execAddress = -1;
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector,
                     int sectorPos, byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        startAddress = -1;
        endAddress = -1;
        execAddress = -1;

        used(super.checkUsed(unuse[0]));
    }

    // 属性からリストの位置を返す(プロパティダイアログ用)
    public int getFileType1Pos() {
        int t1 = getFileType1();
        if (t1 < TYPE_NAME_1_BASIC || t1 > TYPE_NAME_1_MACHINE) {
            t1 = TYPE_NAME_1_UNKNOWN;
        }
        return t1;
    }

    // 属性からリストの位置を返す(プロパティダイアログ用)
    public int getFileType2Pos() {
        int t2 = getFileType2();
        int t3 = getFileType3();
        t2 = ((t2 & 1) != 0 ? ((t3 & 1) != 0 ? TYPE_NAME_2_RANDOM : TYPE_NAME_2_ASCII) : TYPE_NAME_2_BINARY);
        return t2;
    }

    /** ファイル内部のアドレスを取り出す */
    protected void takeAddressesInFile() {
        if (groups.getSize() == 0 || getFileType1() != TYPE_NAME_1_MACHINE) {
            startAddress = -1;
            endAddress = -1;
            execAddress = -1;
            return;
        }

        DiskBasicGroupItem item = groups.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;

        boolean is_bigendian = basic.isBigEndian();

        // 開始アドレス
        startAddress = sector.get16(3, is_bigendian);
        // 終了アドレス
        endAddress = (int) sector.get16(1, is_bigendian) + startAddress - 1;

        item = groups.last();
        sector = basic.getSector(item.track, item.side, item.sectorEnd);
        if (sector == null) return;
        // 実行アドレス
        int remain_size = groups.getSize() % sector.getSectorSize();
        if (remain_size >= 2) {
            execAddress = sector.get16(remain_size - 2, is_bigendian);
        } else {
            DiskImageSector psector = basic.getSector(item.track, item.side, item.sectorEnd - 1);
            if (psector != null) {
                if (remain_size >= 1) {
                    execAddress = (sector.get(0) & 0xff) | ((psector.get(psector.getSectorSize() - 1) & 0xff) << 8);
                } else {
                    execAddress = psector.get16(psector.getSectorSize() - 2, is_bigendian);
                }
            }
        }
    }

    /** ファイル名に拡張子を付ける */
    protected String addExtension(int file_type_1, String name) {
        return name;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        setFileType1(
                (fType & FILE_TYPE_BASIC_MASK.getValue()) != 0 ? TYPE_NAME_1_BASIC : (
                (fType & FILE_TYPE_DATA_MASK.getValue()) != 0 ? TYPE_NAME_1_DATA : (
                (fType & FILE_TYPE_MACHINE_MASK.getValue()) != 0 ? TYPE_NAME_1_MACHINE : (
                0))));

        setFileType3(0);
        if ((fType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            setFileType2(0);
        } else if ((fType & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            setFileType2(0xff);
        } else if ((fType & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            setFileType2(0xff);
            setFileType3(0xff);
        }
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = (t1 >= TYPE_NAME_1_BASIC && t1 <= TYPE_NAME_1_MACHINE ? 1 << t1 : 0);
        int t2 = getFileType2();
        int t3 = getFileType3();
        val |= ((t2 & 1) != 0 ? ((t3 & 1) != 0 ? FILE_TYPE_RANDOM_MASK.getValue() : FILE_TYPE_ASCII_MASK.getValue()) : FILE_TYPE_BINARY_MASK.getValue());
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (t3 << 16) | (t2 << 8) | t1);
    }

    @Override
    public String getFileAttrStr() {
        String attr;
        attr = TYPE_NAME_1[getFileType1Pos()];
        attr += " - ";
        attr += TYPE_NAME_2[getFileType2Pos()];
        return attr;
    }

    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        if (groupItems.size() == 0) return occupiedSize;

        DiskBasicGroupItem litem = groupItems.last();
        DiskImageSector sector = basic.getSector(litem.track, litem.side, litem.sectorEnd);
        if (sector == null) return occupiedSize;

        int sectorSize = sector.getSectorSize();
        int remainSize = ((occupiedSize + sectorSize - 1) % sectorSize) + 1;
        remainSize = type.calcDataSizeOnLastSector(this, null, null, sector.getSectorBuffer(), 0, sectorSize, remainSize);

        occupiedSize = occupiedSize - sectorSize + remainSize;
        return occupiedSize;
    }

    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        int calcFileSize = 0;
        int calcGroups = 0;

        // 8bit FAT
        boolean rc = true;
        int groupNum = getStartGroup(fileUnitNum);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;

        while (working) {
            int nextGroup = type.getGroupNumber(groupNum);
            if (nextGroup == groupNum) {
                // 同じポジションならエラー
                rc = false;
            } else if (nextGroup >= basic.getGroupSystemCode()) {
                // システム領域はエラー(0xfe - )
                rc = false;
            } else if (nextGroup >= basic.getGroupFinalCode()) {
                // 最終グループ(0xc1 - )
                basic.getNumsFromGroup(groupNum, nextGroup, basic.getSectorSize(), 0, groupItems);
                calcFileSize += basic.getSectorSize() * (nextGroup - basic.getGroupFinalCode() + 1);
                calcGroups++;
                calcFileSize = recalcFileSize(groupItems, calcFileSize);
                working = false;
            } else if (nextGroup > basic.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            } else {
                basic.getNumsFromGroup(groupNum, nextGroup, basic.getSectorSize(), 0, groupItems);
                calcFileSize += basic.getSectorSize() * basic.getSectorsPerGroup();
                calcGroups++;
                groupNum = nextGroup;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        groupItems.addNums(calcGroups);
        groupItems.addSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinit loop
            rc = false;
        }
        if (rc) {
            // ファイル内部のアドレスを得る
            takeAddressesInFile();
        }
    }

    @Override
    public boolean hasAddress() {
        return true;
    }

    @Override
    public boolean isAddressEditable() {
        return false;
    }

    @Override
    public int getStartAddress() {
        return startAddress;
    }

    @Override
    public int getEndAddress() {
        return endAddress;
    }

    @Override
    public int getExecuteAddress() {
        return execAddress;
    }

    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = 0;
        // 拡張子で属性を設定する
        Parambase.MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), Utils.getExt(filename));
        if (sa != null) {
            ftype = sa.getType();
        }
        return ftype;
    }

    //
    //
    //

    /// ディレクトリ１アイテム FAT8ビット(F-BASIC, L3 1S)
    public static abstract class DiskBasicDirItemFAT8F extends DiskBasicDirItemFAT8<DirectoryFat8F> {

        /**
         * ディレクトリエントリ L3 ３インチ(単密度) / F-BASIC 倍密度
         */
        @Serdes(bigEndian = false)
        public static class DirectoryFat8F implements Directory {
            @Element(sequence = 1)
            public byte[] name = new byte[8];
            @Element(sequence = 2)
            public byte[] ext = new byte[3]; // not used.
            @Element(sequence = 3)
            public byte type;
            @Element(sequence = 4)
            public byte type2;
            @Element(sequence = 5)
            public byte type3;
            @Element(sequence = 6)
            public byte startGroup;

            @Element(sequence = 7)
            public byte[] reserved = new byte[17];

            public static final int SIZE = 32;
        }

        /** ディレクトリデータ */
        protected DiskBasicDirData<DirectoryFat8F> data = new DiskBasicDirData<>();

        @Override
        public void init(DiskBasic basic) throws IOException {
            super.init(basic);

            data.alloc(DirectoryFat8F.class);
        }

        @Override
        public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
            super.init(basic, sector, sectorPos, data, dataP);

            this.data.attach(DirectoryFat8F.class, data, dataP);
        }

        @Override
        public void init(DiskBasic basic, int num, DiskBasicGroupItem gropItem, DiskImageSector sector,
                         int sectorPos, byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
            super.init(basic, num, gropItem, sector, sectorPos, data, dataP, next, unuse);

            this.data.attach(DirectoryFat8F.class, data, dataP);

            used(checkUsed(unuse[0]));

            // ファイルサイズとグループ数を計算
            calcFileSize();
        }

        @Override
        public void setData(int n_num, DiskBasicGroupItem gItem, DiskImageSector sector, int sectorPos,
                            byte[] data, int dataPos, SectorParam next) throws IOException {
            super.setData(n_num, gItem, sector, sectorPos, data, dataPos, next);

            this.data.attach(DirectoryFat8F.class, data, dataPos);
        }

        @Override
        public boolean check(boolean[] last) {
            if (!data.isValid()) return false;

            boolean valid = true;
            DirectoryFat8F p = data.data();
            if (p.name[0] == (byte) 0xff) {
                last[0] = true;
                return valid;
            }
            // 属性に想定外の値がある場合はエラー
            if (p.type2 != 0 && p.type2 != (byte) 0xff) {
                valid = false;
            } else if (p.type3 != 0 && p.type3 != (byte) 0xff) {
                valid = false;
            }
            return valid;
        }

        @Override
        public boolean checkUsed(boolean unuse) {
            return data.data().name[0] != 0 && (data.data().name[0] & 0xFF) != 0xff;
        }

        @Override
        public boolean delete() {
            // 削除はエントリの先頭にコードを入れるだけ
            data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
            used(false);
            return true;
        }

        @Override
        protected byte[] getFileNamePos(int num, int[] size, int[] len) {
            // 8chars
            if (num == 0) {
                size[0] = len[0] = data.data().name.length;
                return data.data().name;
            } else {
                size[0] = len[0] = 0;
                return null;
            }
        }

        @Override
        protected int getFileType1() {
            return data.data().type & 0xff;
        }

        @Override
        public int getFileType2() {
            return data.data().type2 & 0xff;
        }

        @Override
        protected int getFileType3() {
            return data.data().type3 & 0xff;
        }

        @Override
        protected void setFileType1(int val) {
            data.data().type = (byte) (val & 0xff);
        }

        @Override
        protected void setFileType2(int val) {
            data.data().type2 = (byte) (val & 0xff);
        }

        @Override
        protected void setFileType3(int val) {
            data.data().type3 = (byte) (val & 0xff);
        }

        @Override
        public void setStartGroup(int fileUnitNum, int val, int size) {
            data.data().startGroup = (byte) (val & 0xff);
        }

        @Override
        public int getStartGroup(int fileUnitNum) {
            return data.data().startGroup & 0xff;
        }

        @Override
        public void setFileSize(int val) {
            groups.setSize(val);
        }

        @Override
        public int getDataSize() {
            return data.getDataSize();
        }

        @Override
        public DirectoryFat8F getData() {
            return data.data();
        }

        @Override
        public boolean copyData(byte[] val) {
            return data.copy(val);
        }

        @Override
        public void clearData() {
            data.fill((byte) 0);
        }

        @Override
        public boolean preImportDataFile(String[] filename) {
            if (config.isDecideAttrImport()) {
                trimExtensionByExtensionAttr(filename);
            }
            filename[0] = remakeFileNameAndExtStr(filename[0]);
            return true;
        }

        @Override
        public boolean needCheckEofCode() {
            // ランダムアクセス時は除く
            return getFileType3() != 0xff;
        }

        @Override
        public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
            if (needCheckEofCode()) {
                // ファイルの最終が終端記号で終わっているかを調べる
                fileSize = checkEofCode(iStream, fileSize);
            }
            return fileSize;
        }

        @Override
        public void setInternalDataInAttrDialog(KeyValArray vals) {
            vals.add("NAME", data.data().name, data.data().name.length);
            vals.add("(EXT)", data.data().ext, data.data().ext.length);
            vals.add("TYPE", data.data().type & 0xFF);
            vals.add("TYPE2", data.data().type2 & 0xFF);
            vals.add("TYPE3", data.data().type3 & 0xFF);
            vals.add("START_GROUP", data.data().startGroup & 0xFF);
            vals.add("RESERVED", data.data().reserved, data.data().reserved.length);
        }
    }
}
