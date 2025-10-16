package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;

import l3diskex.Parambase;
import l3diskex.basicfmt.BasicCommon.DirectoryFat8f;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;


// Java equivalent of DiskBasicDirItemFAT8
abstract class DiskBasicDirItemFAT8<T extends DirectoryT> extends DiskBasicDirItem<T> {

    public static final String[] G_TYPE_NAME_1 = {
            "BASIC",
            "Data",
            "Machine",
            "???"
    };

    public static final int TYPE_NAME_1_BASIC = 0;
    public static final int TYPE_NAME_1_DATA = 1;
    public static final int TYPE_NAME_1_MACHINE = 2;
    static final int TYPE_NAME_1_UNKNOWN = 3;

    // Type 2 Names (Binary, Ascii, Random Access)
    public static final String[] G_TYPE_NAME_2 = {
            "Binary",
            "Ascii",
            "Random Access"
    };

    public static final int TYPE_NAME_2_BINARY = 0;
    public static final int TYPE_NAME_2_ASCII = 1;
    public static final int TYPE_NAME_2_RANDOM = 2;

    // UI Control IDs equivalent
    public static final int ATTR_DIALOG_IDC_RADIO_TYPE1 = 51;
    public static final int ATTR_DIALOG_IDC_RADIO_TYPE2 = 52;

    protected int m_start_address;
    protected int m_end_address;
    protected int m_exec_address;

    // Assuming constructors
    public DiskBasicDirItemFAT8(DiskBasic basic) {
        super(basic);
        m_start_address = -1;
        m_end_address = -1;
        m_exec_address = -1;
    }

    public DiskBasicDirItemFAT8(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
        m_start_address = -1;
        m_end_address = -1;
        m_exec_address = -1;
    }

    // Assuming constructor with more parameters for initialization
    public DiskBasicDirItemFAT8(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
        m_start_address = -1;
        m_end_address = -1;
        m_exec_address = -1;

        boolean unuseValue = n_unuse[0];
        used(checkUsed(unuseValue));
        n_unuse[0] = unuseValue; // Update back if CheckUsed modifies unuse in C++ (though unlikely here)
    }

    // Protected methods
    public int getFileType1Pos() {
        int t1 = getFileType1(); // Assumed virtual method from base class, implemented in FAT8F
        if (t1 < TYPE_NAME_1_BASIC || t1 > TYPE_NAME_1_MACHINE) {
            t1 = TYPE_NAME_1_UNKNOWN;
        }
        return t1;
    }

    public int getFileType2Pos() {
        int t2 = getFileType2(); // Assumed virtual method from base class, implemented in FAT8F
        int t3 = getFileType3(); // Assumed virtual method from base class, implemented in FAT8F
        t2 = ((t2 & 1) != 0 ? ((t3 & 1) != 0 ? TYPE_NAME_2_RANDOM : TYPE_NAME_2_ASCII) : TYPE_NAME_2_BINARY);
        return t2;
    }

    protected void takeAddressesInFile() {
        if (groups.getSize() == 0 || getFileType1() != TYPE_NAME_1_MACHINE) {
            m_start_address = -1;
            m_end_address = -1;
            m_exec_address = -1;
            return;
        }

        // Assuming m_groups has Item(0) and Last() methods
        DiskBasicGroupItem item = groups.item(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;

        boolean is_bigendian = basic.isBigEndian();

        // 開始アドレス
        m_start_address = sector.get16(3, is_bigendian);
        // 終了アドレス
        m_end_address = (int)sector.get16(1, is_bigendian) + m_start_address - 1;

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
                    // Assuming Get() returns byte/int
                    int byte0 = sector.get(0) & 0xFF;
                    int byte1 = psector.get(psector.getSectorSize() - 1) & 0xFF;
                    m_exec_address = byte0 | (byte1 << 8);
                } else {
                    m_exec_address = psector.get16(psector.getSectorSize() - 2, is_bigendian);
                }
            } else {
                // If psector is null, m_exec_address is undefined by the logic. Keeping previous value or setting to -1.
                // Based on C++ logic, if sector is not null, m_exec_address is likely not set if psector is null.
                // No explicit logic to set to -1 here, relying on constructor initialization or logic flow.
            }
        }
    }

    protected String addExtension(int file_type_1, String name) {
        return name;
    }

    // Public methods

    // The constructors are already defined above.

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
        // Constants are assumed to be defined in DiskBasic or a global constants class
        val |= ((t2 & 1) != 0 ? ((t3 & 1) != 0 ? FILE_TYPE_RANDOM_MASK.getValue() : FILE_TYPE_ASCII_MASK.getValue()) : FILE_TYPE_BINARY_MASK.getValue());
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (t3 << 16) | (t2 << 8) | t1);
    }

    @Override
    public String getFileAttrStr() {
        String attr;
        // wxGetTranslation replaced with direct array access for simplicity
        attr = G_TYPE_NAME_1[getFileType1Pos()];
        attr += " - ";
        attr += G_TYPE_NAME_2[getFileType2Pos()];
        return attr;
    }

    @Override
    public int recalcFileSize(DiskBasicGroups group_items, int occupied_size) throws IOException {
        if (group_items.count() == 0) return occupied_size;

        DiskBasicGroupItem litem = group_items.last();
        DiskImageSector sector = basic.getSector(litem.track, litem.side, litem.sectorEnd);
        if (sector == null) return occupied_size;

        int sector_size = sector.getSectorSize();
        // C++: ((occupied_size + sector_size - 1) % sector_size) + 1;
        // This calculates the size of the last occupied sector. occupied_size is size up to the end of the last group.
        int remain_size = ((occupied_size - 1) % sector_size) + 1; // Assuming occupied_size is total size including incomplete last sector

        // This method seems to calculate the *actual* size of the data in the last sector.
        // It relies on a 'type' object which is not defined in the C++ provided but is accessed via the base class 'DiskBasicDirItem'
        // Assuming 'type' is accessible and has the method.
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
        // Assuming GetStartGroup returns int (mapped to int or int in Java, using int here if group numbers are small)
        int group_num = (int)getStartGroup(fileunit_num);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1; // Assuming GetFatEndGroup returns int

        while(working) {
            // Assuming type has GetGroupNumber
            int next_group = type.getGroupNumber(group_num); // Assumed to return int

            if (next_group == group_num) {
                // 同じポジションならエラー
                rc = false;
            } else if (next_group >= basic.diskBasicParam.getGroupSystemCode()) {
                // システム領域はエラー(0xfe - )
                rc = false;
            } else if (next_group >= basic.diskBasicParam.getGroupFinalCode()) {
                // 最終グループ(0xc1 - )
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), 0, group_items);
                // C++: calc_file_size += (basic->GetSectorSize() * (next_group - basic->GetGroupFinalCode() + 1));
                calc_file_size += (basic.getSectorSize() * (next_group - basic.diskBasicParam.getGroupFinalCode() + 1));
                calc_groups++;
                calc_file_size = recalcFileSize(group_items, calc_file_size);
                working = false;
            } else if (next_group > basic.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            } else {
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), 0, group_items);
                // C++: calc_file_size += (basic->GetSectorSize() * basic->GetSectorsPerGroup());
                calc_file_size += (basic.getSectorSize() * basic.getSectorsPerGroup());
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
        String ext = getExtension(filename); // Assuming getExtension helper method
        Parambase.MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(ext);
        if (sa != null) {
            ftype = sa.getType();
        }
        return ftype;
    }

    // Helper method assumed for extension extraction, as Path is used in C++
    public String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }
}

// File: DiskBasicDirItemFAT8F.java (part of the same file/package in C++, maintaining structure)

// Assuming directory_fat8f_t and directory_t structs are mapped to Java classes
// directory_fat8f_t is a structure for the directory entry data.
// DiskBasicDirData is a utility class for managing directory data buffer.

public class DiskBasicDirItemFAT8F extends DiskBasicDirItemFAT8<DirectoryFat8f> {

    // Assuming DiskBasicDirData is a generic class/interface
    protected DiskBasicDirData<DirectoryFat8f> m_data;

    public DiskBasicDirItemFAT8F(DiskBasic basic) {
        super(basic);
        // Assuming Alloc() initializes the data structure inside m_data
        m_data = new DiskBasicDirData<>();
        m_data.alloc(DirectoryFat8f.class);
    }

    public DiskBasicDirItemFAT8F(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
        // Assuming Attach() links the byte array to the data structure
        m_data = new DiskBasicDirData<>();
        m_data.attach(n_data);
    }

    public DiskBasicDirItemFAT8F(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
        // Assuming Attach() links the byte array to the data structure
        m_data = new DiskBasicDirData<>();
        m_data.attach(n_data);

        boolean unuseValue = n_unuse[0];
        used(checkUsed(unuseValue));
        n_unuse[0] = unuseValue; // Update back

        // ファイルサイズとグループ数を計算
        calcFileSize(); // Assuming CalcFileSize is a base class method that calls CalcFileUnitSize
    }

    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, n_next);
        // Re-attach data pointer if necessary
        if (m_data == null) {
            m_data = new DiskBasicDirData<>();
        }
        m_data.attach(n_data);
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        DirectoryFat8f p = m_data.data();
        // Assuming byte array element access
        if ((p.name[0] & 0xFF) == 0xff) {
            last[0] = true;
            return valid;
        }
        // 属性に想定外の値がある場合はエラー
        // Type2/Type3 are bytes, so & 0xFF is needed for unsigned comparison
        if ((p.type2 & 0xFF) != 0 && (p.type2 & 0xFF) != 0xff) {
            valid = false;
        } else if ((p.type3 & 0xFF) != 0 && (p.type3 & 0xFF) != 0xff) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        // Assuming byte array element access
        return (m_data.data().name[0] != 0 && (m_data.data().name[0] & 0xFF) != 0xff);
    }

    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        // Assuming Fill(value, count) sets the first 'count' bytes of the underlying data.
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1); // InvertUint8 assumed to exist in DiskBasic
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
        return m_data.data().type & 0xFF;
    }

    @Override
    public int getFileType2() {
        return m_data.data().type2 & 0xFF;
    }

    @Override
    protected int getFileType3() {
        return m_data.data().type3 & 0xFF;
    }

    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte)(val & 0xff);
    }

    @Override
    protected void setFileType2(int val) {
        m_data.data().type2 = (byte)(val & 0xff);
    }

    @Override
    protected void setFileType3(int val) {
        m_data.data().type3 = (byte)(val & 0xff);
    }

    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        // Assuming int maps to int for group numbers
        m_data.data().startGroup = (byte)(val & 0xff);
    }

    @Override
    public int getStartGroup(int fileunit_num) {
        // Assuming int maps to int for group numbers, returning as unsigned int (0-255)
        return m_data.data().startGroup & 0xFF;
    }

    @Override
    public void setFileSize(int val) {
        //	m_file_size = val; // In C++, the size is stored in the base class member
        groups.setSize(val);
    }

    @Override
    public int getDataSize() {
        return 19; // sizeof(directory_fat8f_t) = 19
    }

    // Assuming DirectoryT is the base/generic directory item structure
    @Override
    public DirectoryFat8f getData() {
        // C++ returns (directory_t *)m_data.data();
        return m_data.data(); // Requires DirectoryFat8f to inherit from/be castable to DirectoryT
    }

    @Override
    public boolean copyData(DirectoryFat8f val) {
        // Assuming Copy(val) copies the data from val to the underlying buffer.
        return m_data.copy(val);
    }

    @Override
    public void clearData() {
        m_data.fill((byte)0);
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        // Assuming gConfig.IsDecideAttrImport() and other helper methods are available
        if (gConfig.IsDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename); // Assuming helper takes String[] reference
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
            // Assuming CheckEofCode is a base class method
            file_size = checkEofCode(istream, file_size);
        }
        return file_size;
    }

    // Assuming CheckEofCode implementation details are in the base class, taking Object for InputStream
    // private int CheckEofCode(Object istream, int file_size) { /* ... */ return file_size; }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        // Assuming KeyValArray has Add methods for various types
        vals.add("self", m_data.isSelf());
        // Add(name, byte[], size)
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("(EXT)", m_data.data().ext, m_data.data().ext.length);
        // Add(name, byte) - The C++ uses int, but the field is byte, so passing byte/int
        vals.add("TYPE", m_data.data().type & 0xFF);
        vals.add("TYPE2", m_data.data().type2 & 0xFF);
        vals.add("TYPE3", m_data.data().type3 & 0xFF);
        vals.add("START_GROUP", m_data.data().startGroup & 0xFF);
        // Add(name, byte[], size)
        vals.add("RESERVED", m_data.data().reserved, m_data.data().reserved.length);
    }
}
