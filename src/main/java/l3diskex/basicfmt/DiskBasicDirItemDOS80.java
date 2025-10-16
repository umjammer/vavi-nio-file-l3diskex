/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;

import l3diskex.basicfmt.BasicCommon.DirectoryDos80;
import l3diskex.basicfmt.BasicCommon.DirectoryDos80_2;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;


/**
 *   Directory 1 item – PC‑8001 DOS
 *
 *  The class follows the same inheritance hierarchy as the C++ version.
 *  All methods are implemented exactly as in the original source,
 *  only syntax is changed to Java.
 */
public class DiskBasicDirItemDOS80 extends DiskBasicDirItemFAT8<DirectoryDos80> {

    static final int TYPE_NAME_DOS80_BASIC = 0;
    static final int TYPE_NAME_DOS80_MACHINE = 1;
    static final int TYPE_NAME_DOS80_BASIC_MACHINE = 2;
    static final int TYPE_NAME_DOS80_END = 3;

    /**
     *   Array of translated type names.
     *  In C++ this is defined as `extern final char *gTypeNameDOS80[];`.
     */
    static final String[] gTypeNameDOS80 = {
            "BASIC",
            "Machine",
            "BASIC + Machine"
    };

    /**
     *  Private data members
     **/
    private DiskBasicDirData<DirectoryDos80>        m_data;   //  directory_dos80_t
    private DiskBasicDirData<DirectoryDos80_2>      m_data2;  //  directory_dos80_2_t
    private final DiskBasicGroups[] m_file_unit = new DiskBasicGroups[2];
    private int m_cached_type = 0;

    /**
     *  Constructors
     **/
    public DiskBasicDirItemDOS80(DiskBasic basic) {
        super(basic);
        m_data = new DiskBasicDirData<>();
        m_data2 = new DiskBasicDirData<>();
        // file units are already zero‑initialised
    }

    public DiskBasicDirItemDOS80(DiskBasic basic,
                                 DiskImageSector sector,
                                 int secPos,
                                 byte[] data) {
        super(basic, sector, secPos, data);
        m_data = new DiskBasicDirData<>();
        m_data2 = new DiskBasicDirData<>();
    }

    public DiskBasicDirItemDOS80(DiskBasic basic,
                                 int num,
                                 DiskBasicGroupItem gitem,
                                 DiskImageSector sector,
                                 int secPos,
                                 byte[] data,
                                 SectorParam next,
                                 boolean[] unuse) {
        super(basic, num, gitem, sector, secPos, data, next, unuse);
        m_data = new DiskBasicDirData<>();
        m_data2 = new DiskBasicDirData<>();
    }

    /*
     *  Override of virtual functions
     */

    /**
     *   Get the position of the file name within the directory data.
     *  @param  num   The index of the element to return.
     *  @return       Byte array that points to the name position.
     *  In the original code a `byte*` is returned;
     *        here we return a slice of the underlying byte array.
     */
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

    /**
     *   Get file type (attribute #1).
     */
    @Override
    protected int getFileType1() {
        int val = 0;
        if (m_data2.isValid()) {
            // 属性は開始アドレスで判断する
            if (basic.orderUint16(m_data2.data().grps[0].a) == basic.diskBasicParam.getVariousIntegerParam("DefaultStartAddress")) {
                if (m_data2.data().grps[1].g == 0x01) {
                    val = TYPE_NAME_DOS80_BASIC;
                } else {
                    val = TYPE_NAME_DOS80_BASIC_MACHINE;
                }
            } else {
                val = TYPE_NAME_DOS80_MACHINE;
            }
        }
        return val;
    }

    /**
     *   Set file type (attribute #1).
     */
    @Override
    protected void setFileType1(int val) {
        if (!m_data2.isValid()) return;
        if ((val & 0xff00) == 0) return;

        // BASICの固定アドレス設定
        //
        // マシン語のアドレスはSetStartAddress(),SetEndAddress(),SetExecuteAddress()で
        // 設定する
        //
        switch(val & 0xff) {
            case TYPE_NAME_DOS80_BASIC:
                // BASICの場合、ロードアドレス、終了アドレス、実行アドレスを固定で設定
                m_data2.data().grps[0].a = basic.orderUint16((short) basic.diskBasicParam.getVariousIntegerParam("DefaultStartAddress"));
                m_data2.data().grps[1].g = 1;
                m_data2.data().grps[1].a = basic.orderUint16((short) 0);
                m_data2.data().grps[2].g = 0;
                m_data2.data().grps[2].a = basic.orderUint16((short) basic.diskBasicParam.getVariousIntegerParam("DefaultExecuteAddress"));
                break;
            case TYPE_NAME_DOS80_BASIC_MACHINE:
                // BASIC + マシン語の場合、ロードアドレス、実行アドレスを固定で設定
                m_data2.data().grps[0].a = basic.orderUint16((short) basic.diskBasicParam.getVariousIntegerParam("DefaultStartAddress"));
                m_data2.data().grps[3].g = 0;
                m_data2.data().grps[3].a = basic.orderUint16((short) basic.diskBasicParam.getVariousIntegerParam("DefaultExecuteAddress"));
                break;
        }
    }

    /**
     *   Check if this item is used.
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.m_data.data().name[0] != 0 && this.m_data.data().name[0] != 0xff && this.getStartGroup(0) != 0;
    }

    /**
     *   Convert attribute from file unit to DOS80 type.
     */
    private int convFileAttrFromTypePos(int t1) {
        int val = 0;
        switch(t1) {
            case TYPE_NAME_DOS80_MACHINE:
                // Machine
                val = FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue();
                break;
            case TYPE_NAME_DOS80_BASIC_MACHINE:
                // BASIC + Machine
                val = FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue();
                break;
            default:
                // BASIC
                val = FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue();
                break;
        }
        return val;
    }


    /**
     *   Convert DOS80 type to attribute.
     */
    private int convFileAttrToTypePos(int file_type) {
        int t1 = TYPE_NAME_DOS80_BASIC;
        int s = file_type & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue());
        if (s == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
            t1 = TYPE_NAME_DOS80_BASIC_MACHINE;
        } else if (s == (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
            // Machine
            t1 = TYPE_NAME_DOS80_MACHINE;
        }
        return t1;
    }

    /**
     *   Calculate the file size.
     */
    @Override
    public void calcFileSize() throws IOException {
        this.groups.empty();
        for(int fileunit_num = 0; fileunit_num < 4; fileunit_num++) {
            if (!isValidFileUnit(fileunit_num)) {
                break;
            }
            if (!isUsed()) {
                break;
            }
            DiskBasicGroups groups = new DiskBasicGroups();
            getUnitGroups(fileunit_num, groups);
            groups.add(groups);
            if (fileunit_num < 2) m_file_unit[fileunit_num] = groups;
        }
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        // Dump internal data for debugging
        vals.add("self", m_data.isSelf());
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
    }

    /**
     *  Data handling
     **/
    @Override
    public void setDataPtr(int num,
                           DiskBasicGroupItem gItem,
                           DiskImageSector sector,
                           int secPos,
                           byte[] data,
                           SectorParam next) throws IOException {
        super.setDataPtr(num, gItem, sector, secPos, data, next);

        m_data.attach(data);

        m_data2.delete();

        // 2セクタ後に属性などがある
        DiskImageSector sector_2 = basic.getSector(gItem.track, gItem.side, sector.getSectorNumber() + 2);
        if (sector_2 != null) {
            byte[] buffer2 = sector_2.getSectorBuffer();
            m_data2.attach(buffer2, secPos);
        }
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        if (m_data.data().name[0] == 0) {
//		last = true;
            return valid;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int val = fileType.getType();
        int t1 = convFileAttrToTypePos(val);

        m_cached_type = (1 << 8 | t1);

        setFileType1(m_cached_type);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = convFileAttrFromTypePos(t1);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    @Override
    public String getFileAttrStr() {
        int t = getFileType1();
        return gTypeNameDOS80[t];
    }

    @Override
    public void setFileSize(int val) {
        // ファイルサイズはセクタサイズ境界で丸める
        int sector_size = basic.getSectorSize();
        groups.setSize((((val - 1) / sector_size) + 1) * sector_size);
    }

    @Override
    public void getAllGroups(DiskBasicGroups groupItems) throws IOException {
        groupItems.empty();
        for(int fileunit_num = 0; fileunit_num < 4; fileunit_num++) {
            if (!isValidFileUnit(fileunit_num)) {
                break;
            }
            getUnitGroups(fileunit_num, groupItems);
        }
    }

    @Override
    public void setStartGroup(int fileUnit, int val, int size) {
        if (m_data2.isValid()) m_data2.data().grps[fileUnit].g = (byte) (val & 0xff);
    }

    @Override
    public int getStartGroup(int fileUnit) {
        return (m_data2.isValid() ? m_data2.data().grps[fileUnit].g : 0);
    }

    @Override
    public boolean hasAddress() { return m_data2.isValid(); }

    @Override
    public boolean isAddressEditable() {
        int t1 = getFileType1();
        return (t1 != TYPE_NAME_DOS80_BASIC);
    }

    @Override
    public int getStartAddress() {
        int t1 = getFileType1();
        int val = 0;
        if (m_data2.isValid()) {
            switch(t1) {
                case TYPE_NAME_DOS80_BASIC_MACHINE:
                    val = basic.orderUint16(m_data2.data().grps[1].a);
                    break;
                default:
                    val = basic.orderUint16(m_data2.data().grps[0].a);
                    break;
            }
        }
        return val;
    }

    @Override
    public int getEndAddress() {
        int t1 = getFileType1();
        int val = 0;
        if (m_data2.isValid()) {
            switch(t1) {
                case TYPE_NAME_DOS80_BASIC_MACHINE:
                    val = basic.orderUint16(m_data2.data().grps[2].a);
                    break;
                default:
                    val = basic.orderUint16(m_data2.data().grps[1].a);
                    break;
            }
        }
        return val;
    }

    @Override
    public int getExecuteAddress() {
        int t1 = getFileType1();
        int val = 0;
        if (m_data2.isValid()) {
            switch(t1) {
                case TYPE_NAME_DOS80_BASIC_MACHINE:
                    val = basic.orderUint16(m_data2.data().grps[3].a);
                    break;
                default:
                    val = basic.orderUint16(m_data2.data().grps[2].a);
                    break;
            }
        }
        return val;
    }

    @Override
    public void setStartAddress(int val) {
        if (!m_data2.isValid()) return;
        if ((m_cached_type & 0xff00) == 0) return;

        switch(m_cached_type & 0xff) {
            case TYPE_NAME_DOS80_MACHINE:
                m_data2.data().grps[0].a = basic.orderUint16((short) val);
                break;
            case TYPE_NAME_DOS80_BASIC_MACHINE:
                m_data2.data().grps[1].a = basic.orderUint16((short) val);
                break;
        }
    }

    @Override
    public void setEndAddress(int val) {
        if (!m_data2.isValid()) return;
        if ((m_cached_type & 0xff00) == 0) return;

        switch(m_cached_type & 0xff) {
            case TYPE_NAME_DOS80_MACHINE:
                m_data2.data().grps[1].g = 0;
                m_data2.data().grps[1].a = basic.orderUint16((short) val);
                break;
            case TYPE_NAME_DOS80_BASIC_MACHINE:
                m_data2.data().grps[2].g = 0;
                m_data2.data().grps[2].a = basic.orderUint16((short) val);
                break;
        }
    }

    @Override
    public void setExecuteAddress(int val) {
        if (!m_data2.isValid()) return;
        if ((m_cached_type & 0xff00) == 0) return;

        switch(m_cached_type & 0xff) {
            case TYPE_NAME_DOS80_MACHINE:
                m_data2.data().grps[2].g = 0;
                m_data2.data().grps[2].a = basic.orderUint16((short) val);
                break;
        }
    }

    @Override
    public boolean needCheckEofCode() { return false; }

    @Override
    public int recalcFileSizeOnSave(InputStream stream, int fileSize) {
        return fileSize;
    }

    @Override
    public int getFileUnitSize(int fileUnit,
                               InputStream stream,
                               int fileOffset) throws IOException {
        int file_type_1 = getFileType1();
        int basic_size = (int)stream.available();
        int machine_size = -1;
        if (file_type_1 == TYPE_NAME_DOS80_BASIC_MACHINE) {
            // BASIC + マシン語の場合
            machine_size = getEndAddress() - getStartAddress();
            if (machine_size >= 0) {
                machine_size++;
                machine_size = ((machine_size + 255) & ~0xff);
            }

            basic_size -= machine_size;
        }

        if (fileUnit == 0) {
            return basic_size;
        }
        if (fileUnit == 1) {
            return machine_size;
        }
        return -1;
    }

    @Override
    public boolean isValidFileUnit(int fileUnit) {
        if (fileUnit == 0) {
            return true;
        }
        if (fileUnit == 1) {
            int file_type_1 = getFileType1();

            if (file_type_1 == TYPE_NAME_DOS80_BASIC_MACHINE) {
                // BASIC + マシン語の場合
                return true;
            }
        }
        return false;
    }

    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    @Override
    public DirectoryDos80 getData() { return m_data.data(); }

    @Override
    public boolean copyData(DirectoryDos80 val) { return m_data.copy(val); }

    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir());
    }

    @Override
    public void copyItem(DiskBasicDirItem src) {
        super.copyItem(src);

        DiskBasicDirItemDOS80 psrc = (DiskBasicDirItemDOS80) src;

        if (m_data2.isValid() && psrc.m_data2.isValid()) {
            m_data2.copy(psrc.m_data2.data());
        }
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.IsDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }
}
