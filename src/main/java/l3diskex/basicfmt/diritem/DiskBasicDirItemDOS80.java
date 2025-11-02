/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.util.ResourceBundle;

import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemDOS80.DirectoryDos80;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;


/// ディレクトリ１アイテム PC-8001 DOS
///
/// "DefaultStartAddress"   デフォルトロードアドレス
/// "DefaultExecuteAddress" デフォルト実行開始アドレス
public class DiskBasicDirItemDOS80 extends DiskBasicDirItemFAT8<DirectoryDos80> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ PC-8001 DOS (New PC.DOS) (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryDos80 implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[16];

        public static final int SIZE = 16;
    }

    /**
     * PC-8001 DOS (New PC.DOS) グループエントリ
     */
    @Serdes(bigEndian = false)
    public static class DirectoryDos80Grp {

        @Element(sequence = 1)
        public byte g; // byte
        @Element(sequence = 2)
        public short a; // wxUint16
    }

    /**
     * ディレクトリエントリ2 PC-8001 DOS (New PC.DOS) (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryDos80_2 implements DirectoryT {

        @Element(sequence = 1)
        public DirectoryDos80Grp[] grps = new DirectoryDos80Grp[5]; // 3 x 5
        @Element(sequence = 2)
        public byte reserved; // byte

        public DirectoryDos80_2() {
            for (int i = 0; i < 5; i++) {
                grps[i] = new DirectoryDos80Grp();
            }
        }

        public static final int SIZE = 16;
    }

    /// PC-8001 DOS 属性
    static final int TYPE_NAME_DOS80_BASIC = 0;
    static final int TYPE_NAME_DOS80_MACHINE = 1;
    static final int TYPE_NAME_DOS80_BASIC_MACHINE = 2;

    // PC-8001 DOS 属性名
    static final String[] gTypeNameDOS80 = {
            /*rb.getString(*/"BASIC"/*)*/,
            /*rb.getString(*/"Machine"/*)*/,
            /*rb.getString(*/"BASIC + Machine"/*)*/
    };

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryDos80> m_data = new DiskBasicDirData<>();
    private final DiskBasicDirData<DirectoryDos80_2> m_data2 = new DiskBasicDirData<>();

    private final DiskBasicGroups[] m_file_unit = new DiskBasicGroups[2];
    private int m_cached_type = 0;

    public DiskBasicDirItemDOS80(DiskBasic basic) {
        super(basic);

        m_cached_type = 0;
        m_data.alloc(DirectoryDos80.class);
        m_data2.alloc(DirectoryDos80_2.class);
        m_data2.fill(0);
    }

    public DiskBasicDirItemDOS80(DiskBasic basic,
                                 DiskImageSector sector,
                                 int secPos,
                                 byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);

        m_cached_type = 0;
        m_data.attach(DirectoryDos80.class, data, dataP);
        m_data2.alloc(DirectoryDos80_2.class);
        m_data2.fill(0);
    }

    public DiskBasicDirItemDOS80(DiskBasic basic,
                                 int num,
                                 DiskBasicGroupItem gitem,
                                 DiskImageSector sector,
                                 int secPos,
                                 byte[] data, int dataP,
                                 SectorParam next,
                                 boolean[] unuse) throws IOException {
        super(basic, num, gitem, sector, secPos, data, dataP, next, unuse);

        m_cached_type = 0;
        m_data.attach(DirectoryDos80.class, data, dataP);

        // 2セクタ後に属性などがある
        DiskImageSector sector_2 = basic.getSector(gitem.track, gitem.side, sector.getSectorNumber() + 2);
        if (sector_2 != null) {
            byte[] buffer2 = sector_2.getSectorBuffer();
            m_data2.attach(DirectoryDos80_2.class, buffer2, secPos);
        }

        used(checkUsed(unuse[0]));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /**
     * Get the position of the file name within the directory data.
     *
     * @param num The index of the element to return.
     * @return Byte array that points to the name position.
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
     * Get file type (attribute #1).
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
     * Set file type (attribute #1).
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
        switch (val & 0xff) {
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
     * Check if this item is used.
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.m_data.data().name[0] != 0 && this.m_data.data().name[0] != (byte) 0xff && this.getStartGroup(0) != 0;
    }

    /**
     * Convert attribute from file unit to DOS80 type.
     */
    private int convFileAttrFromTypePos(int t1) {
        int val = 0;
        switch (t1) {
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
     * Convert DOS80 type to attribute.
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
     * Calculate the file size.
     */
    @Override
    public void calcFileSize() throws IOException {
        this.groups.clear();
        for (int fileunit_num = 0; fileunit_num < 4; fileunit_num++) {
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
    public void setDataPtr(int num,
                           DiskBasicGroupItem gItem,
                           DiskImageSector sector,
                           int secPos,
                           byte[] data,
                           int dataP, SectorParam next) throws IOException {
        super.setDataPtr(num, gItem, sector, secPos, data, dataP, next);

        m_data.attach(DirectoryDos80.class, data, dataP);

        // 2セクタ後に属性などがある
        DiskImageSector sector_2 = basic.getSector(gItem.track, gItem.side, sector.getSectorNumber() + 2);
        if (sector_2 != null) {
            byte[] buffer2 = sector_2.getSectorBuffer();
            m_data2.attach(DirectoryDos80_2.class, buffer2, secPos);
        }
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        if (m_data.data().name[0] == 0) {
            //last = true;
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
        int file_type_1 = getFileType1();
        return rb.getString(gTypeNameDOS80[file_type_1]);
    }

    @Override
    public void setFileSize(int val) {
        // ファイルサイズはセクタサイズ境界で丸める
        int sector_size = basic.getSectorSize();
        groups.setSize((((val - 1) / sector_size) + 1) * sector_size);
    }

    @Override
    public void getAllGroups(DiskBasicGroups groupItems) throws IOException {
        groupItems.clear();
        for (int fileunit_num = 0; fileunit_num < 4; fileunit_num++) {
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
        return (m_data2.isValid() ? m_data2.data().grps[fileUnit].g & 0xff : 0);
    }

    @Override
    public boolean hasAddress() {
        return m_data2.isValid();
    }

    @Override
    public boolean isAddressEditable() {
        int t1 = getFileType1();
        return t1 != TYPE_NAME_DOS80_BASIC;
    }

    @Override
    public int getStartAddress() {
        int t1 = getFileType1();
        int val = 0;
        if (m_data2.isValid()) {
            switch (t1) {
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
            switch (t1) {
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
            switch (t1) {
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

        switch (m_cached_type & 0xff) {
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

        switch (m_cached_type & 0xff) {
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

        switch (m_cached_type & 0xff) {
            case TYPE_NAME_DOS80_MACHINE:
                m_data2.data().grps[2].g = 0;
                m_data2.data().grps[2].a = basic.orderUint16((short) val);
                break;
        }
    }

    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    @Override
    public int recalcFileSizeOnSave(InputStream stream, int fileSize) {
        return fileSize;
    }

    @Override
    public int getFileUnitSize(int fileUnit, InputStream stream, int fileOffset) throws IOException {
        int file_type_1 = getFileType1();
        int basic_size = stream.available();
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
    public DirectoryDos80 getData() {
        return m_data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val);
    }

    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir());
    }

    @Override
    public void copyItem(DiskBasicDirItem src) {
        super.copyItem(src);

        DiskBasicDirItemDOS80 psrc = (DiskBasicDirItemDOS80) src;

        if (m_data2.isValid() && psrc.m_data2.isValid()) {
            m_data2.copy(psrc.m_data2.getRawData());
        }
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    //
    // ダイアログ用
    //

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", m_data.data().name, m_data.data().name.length);

        if (!m_data2.isValid()) return;

        // TODO serialize?
//        vals.add("GRPS", m_data2.data().grps, m_data2.data().grps.length);
//        vals.add("RESERVED", m_data2.data().reserved, m_data2.data().reserved));
    }
}
