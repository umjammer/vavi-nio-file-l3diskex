///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;

import l3diskex.Parambase;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryFat8f;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;


/// ディレクトリ１アイテム FAT8ビット
public abstract class DiskBasicDirItemFAT8<T extends DirectoryT> extends DiskBasicDirItem<T> {

    private static final Logger logger = System.getLogger(DiskBasicDirItemFAT8.class.getName());

    /// L3/S1/F BASIC タイプ1 0...BASIC 1...DATA 2...MACHINE
    public static final String[] G_TYPE_NAME_1 = {
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
    public static final String[] G_TYPE_NAME_2 = {
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
    protected int m_start_address;
    /** ファイル内部で持っている終了アドレス */
    protected int m_end_address;
    /** ファイル内部で持っている実行アドレス */
    protected int m_exec_address;

    public DiskBasicDirItemFAT8(DiskBasic basic) {
        super(basic);

        m_start_address = -1;
        m_end_address = -1;
        m_exec_address = -1;
    }

    public DiskBasicDirItemFAT8(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_start_address = -1;
        m_end_address = -1;
        m_exec_address = -1;
    }

    public DiskBasicDirItemFAT8(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_start_address = -1;
        m_end_address = -1;
        m_exec_address = -1;

        used(super.checkUsed(n_unuse[0]));
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
            m_start_address = -1;
            m_end_address = -1;
            m_exec_address = -1;
            return;
        }

        DiskBasicGroupItem item = groups.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;

        boolean is_bigendian = basic.isBigEndian();

        // 開始アドレス
        m_start_address = sector.get16(3, is_bigendian);
        // 終了アドレス
        m_end_address = (int) sector.get16(1, is_bigendian) + m_start_address - 1;

        item = groups.last();
        sector = basic.getSector(item.track, item.side, item.sectorEnd);
        if (sector == null) return;
        // 実行アドレス
        int remain_size = groups.getSize() % sector.getSectorSize();
        if (remain_size >= 2) {
            m_exec_address = sector.get16(remain_size - 2, is_bigendian);
        } else {
            DiskImageSector psector = basic.getSector(item.track, item.side, item.sectorEnd - 1);
            if (psector != null) {
                if (remain_size >= 1) {
                    m_exec_address = (sector.get(0) & 0xff) | ((psector.get(psector.getSectorSize() - 1) & 0xff) << 8);
                } else {
                    m_exec_address = psector.get16(psector.getSectorSize() - 2, is_bigendian);
                }
            }
        }
    }

    /** ファイル名に拡張子を付ける */
    protected String addExtension(int file_type_1, String name) {
        return name;
    }

    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        setFileType1(
                (ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0 ? TYPE_NAME_1_BASIC : (
                (ftype & FILE_TYPE_DATA_MASK.getValue()) != 0 ? TYPE_NAME_1_DATA : (
                (ftype & FILE_TYPE_MACHINE_MASK.getValue()) != 0 ? TYPE_NAME_1_MACHINE : (
                0))));

        setFileType3(0);
        if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            setFileType2(0);
        } else if ((ftype & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            setFileType2(0xff);
        } else if ((ftype & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
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
        attr = G_TYPE_NAME_1[getFileType1Pos()];
        attr += " - ";
        attr += G_TYPE_NAME_2[getFileType2Pos()];
        return attr;
    }

    @Override
    public int recalcFileSize(DiskBasicGroups group_items, int occupied_size) throws IOException {
        if (group_items.size() == 0) return occupied_size;

        DiskBasicGroupItem litem = group_items.last();
        DiskImageSector sector = basic.getSector(litem.track, litem.side, litem.sectorEnd);
        if (sector == null) return occupied_size;

        int sector_size = sector.getSectorSize();
        int remain_size = ((occupied_size + sector_size - 1) % sector_size) + 1;
        remain_size = type.calcDataSizeOnLastSector(this, null, null, sector.getSectorBuffer(), 0, sector_size, remain_size);

        occupied_size = occupied_size - sector_size + remain_size;
        return occupied_size;
    }

    @Override
    public void calcFileUnitSize(int fileunit_num) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) throws IOException {
        int calc_file_size = 0;
        int calc_groups = 0;

        // 8bit FAT
        boolean rc = true;
        int group_num = getStartGroup(fileunit_num);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;

        while (working) {
            int next_group = type.getGroupNumber(group_num);
            if (next_group == group_num) {
                // 同じポジションならエラー
                rc = false;
            } else if (next_group >= basic.diskBasicParam.getGroupSystemCode()) {
                // システム領域はエラー(0xfe - )
                rc = false;
            } else if (next_group >= basic.diskBasicParam.getGroupFinalCode()) {
                // 最終グループ(0xc1 - )
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), 0, group_items);
                calc_file_size += basic.getSectorSize() * (next_group - basic.diskBasicParam.getGroupFinalCode() + 1);
                calc_groups++;
                calc_file_size = recalcFileSize(group_items, calc_file_size);
                working = false;
            } else if (next_group > basic.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            } else {
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), 0, group_items);
                calc_file_size += basic.getSectorSize() * basic.getSectorsPerGroup();
                calc_groups++;
                group_num = next_group;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        group_items.addNums(calc_groups);
        group_items.addSize(calc_file_size);
        group_items.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

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
        return m_start_address;
    }

    @Override
    public int getEndAddress() {
        return m_end_address;
    }

    @Override
    public int getExecuteAddress() {
        return m_exec_address;
    }

    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = 0;
        // 拡張子で属性を設定する
        Parambase.MyAttribute sa = findUpperCase(basic.diskBasicParam.getAttributesByExtension(), Utils.getExt(filename));
        if (sa != null) {
            ftype = sa.getType();
        }
        return ftype;
    }

    //
    //
    //

    /// ディレクトリ１アイテム FAT8ビット(F-BASIC, L3 1S)
    public static class DiskBasicDirItemFAT8F extends DiskBasicDirItemFAT8<DirectoryFat8f> {

        /** ディレクトリデータ */
        protected DiskBasicDirData<DirectoryFat8f> m_data = new DiskBasicDirData<>();

        public DiskBasicDirItemFAT8F(DiskBasic basic) {
            super(basic);

            m_data.alloc(DirectoryFat8f.class);
        }

        public DiskBasicDirItemFAT8F(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
            super(basic, n_sector, n_secpos, n_data, dataP);

            m_data.attach(DirectoryFat8f.class, n_data, dataP);
        }

        public DiskBasicDirItemFAT8F(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
            super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

            m_data.attach(DirectoryFat8f.class, n_data, dataP);

            used(checkUsed(n_unuse[0]));

            // ファイルサイズとグループ数を計算
            calcFileSize();
        }

        @Override
        public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
            super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

            m_data.attach(DirectoryFat8f.class, n_data, dataP);
        }

        @Override
        public boolean check(boolean[] last) {
            if (!m_data.isValid()) return false;

            boolean valid = true;
            DirectoryFat8f p = m_data.data();
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
            return m_data.data().name[0] != 0 && (m_data.data().name[0] & 0xFF) != 0xff;
        }

        @Override
        public boolean delete() {
            // 削除はエントリの先頭にコードを入れるだけ
            m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
            used(false);
            return true;
        }

        @Override
        protected byte[] getFileNamePos(int num, int[] size, int[] len) {
            // 8chars
            if (num == 0) {
                size[0] = len[0] = m_data.data().name.length;
                return m_data.data().name;
            } else {
                size[0] = len[0] = 0;
                return null;
            }
        }

        @Override
        protected int getFileType1() {
            return m_data.data().type & 0xff;
        }

        @Override
        public int getFileType2() {
            return m_data.data().type2 & 0xff;
        }

        @Override
        protected int getFileType3() {
            return m_data.data().type3 & 0xff;
        }

        @Override
        protected void setFileType1(int val) {
            m_data.data().type = (byte) (val & 0xff);
        }

        @Override
        protected void setFileType2(int val) {
            m_data.data().type2 = (byte) (val & 0xff);
        }

        @Override
        protected void setFileType3(int val) {
            m_data.data().type3 = (byte) (val & 0xff);
        }

        @Override
        public void setStartGroup(int fileunit_num, int val, int size) {
            m_data.data().startGroup = (byte) (val & 0xff);
        }

        @Override
        public int getStartGroup(int fileunit_num) {
            return m_data.data().startGroup & 0xff;
        }

        @Override
        public void setFileSize(int val) {
            groups.setSize(val);
        }

        @Override
        public int getDataSize() {
            return m_data.getDataSize();
        }

        @Override
        public DirectoryFat8f getData() {
            return m_data.data();
        }

        @Override
        public boolean copyData(byte[] val) {
            return m_data.copy(val);
        }

        @Override
        public void clearData() {
            m_data.fill((byte) 0);
        }

        @Override
        public boolean preImportDataFile(String[] filename) {
            if (gConfig.isDecideAttrImport()) {
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
        public int recalcFileSizeOnSave(InputStream istream, int file_size) {
            if (needCheckEofCode()) {
                // ファイルの最終が終端記号で終わっているかを調べる
                file_size = checkEofCode(istream, file_size);
            }
            return file_size;
        }

        @Override
        public void setInternalDataInAttrDialog(KeyValArray vals) {
            vals.add("NAME", m_data.data().name, m_data.data().name.length);
            vals.add("(EXT)", m_data.data().ext, m_data.data().ext.length);
            vals.add("TYPE", m_data.data().type & 0xFF);
            vals.add("TYPE2", m_data.data().type2 & 0xFF);
            vals.add("TYPE3", m_data.data().type3 & 0xFF);
            vals.add("START_GROUP", m_data.data().startGroup & 0xFF);
            vals.add("RESERVED", m_data.data().reserved, m_data.data().reserved.length);
        }
    }
}
