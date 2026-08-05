/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.util.ResourceBundle;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemDOS80.DirectoryDos80;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeDOS80.FORMAT_TYPE_DOS80;


/**
 * Directory 1 item PC-8001 DOS
 *
 * <li>"DefaultStartAddress"   Default load address</li>
 * <li>"DefaultExecuteAddress" Default execution start address</li>
 */
public class DiskBasicDirItemDOS80 extends DiskBasicDirItemFAT8<DirectoryDos80> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Directory entry PC-8001 DOS (New PC.DOS) (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryDos80 implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[16];

        public static final int SIZE = 16;
    }

    /**
     * PC-8001 DOS (New PC.DOS) group entry
     */
    @Serdes(bigEndian = false)
    public static class DirectoryDos80Grp {

        @Element(sequence = 1)
        public byte g; // byte
        @Element(sequence = 2)
        public short a; // wxUint16
    }

    /**
     * Directory entry 2 PC-8001 DOS (New PC.DOS) (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryDos80_2 implements Directory {

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

    /// PC-8001 DOS attributes
    static final int TYPE_NAME_DOS80_BASIC = 0;
    static final int TYPE_NAME_DOS80_MACHINE = 1;
    static final int TYPE_NAME_DOS80_BASIC_MACHINE = 2;

    // PC-8001 DOS attribute names
    static final String[] typeNameDOS80 = {
            /*rb.getString(*/"BASIC"/*)*/,
            /*rb.getString(*/"Machine"/*)*/,
            /*rb.getString(*/"BASIC + Machine"/*)*/
    };

    //
    //
    //

    /** Directory data */
    private final DiskBasicDirData<DirectoryDos80> data = new DiskBasicDirData<>();
    private final DiskBasicDirData<DirectoryDos80_2> data2 = new DiskBasicDirData<>();

    private final DiskBasicGroups[] fileUnit = new DiskBasicGroups[2];
    private int cachedType = 0;

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_DOS80;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        cachedType = 0;
        data.alloc(DirectoryDos80.class);
        data2.alloc(DirectoryDos80_2.class);
        data2.fill(0);
    }

    @Override
    public void init(DiskBasic basic,
                     DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        cachedType = 0;
        this.data.attach(DirectoryDos80.class, data, dataP);
        data2.alloc(DirectoryDos80_2.class);
        data2.fill(0);
    }

    @Override
    public void init(DiskBasic basic,
                     int num,
                     DiskBasicGroupItem groupItem,
                     DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos,
                     SectorParam next,
                     boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        cachedType = 0;
        this.data.attach(DirectoryDos80.class, data, dataPos);

        // Attributes and others are 2 sectors later
        DiskImageSector sector2 = basic.getSector(groupItem.track, groupItem.side, sector.getSectorNumber() + 2);
        if (sector2 != null) {
            byte[] buffer2 = sector2.getSectorBuffer();
            data2.attach(DirectoryDos80_2.class, buffer2, sectorPos);
        }

        used(checkUsed(unuse[0]));

        // Calculate file size and number of groups
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
            size[0] = data.data().name.length;
            len[0] = size[0] - 1;
            return data.data().name;
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
        if (data2.isValid()) {
            // Attributes are determined by start address
            if (basic.orderUint16(data2.data().grps[0].a) == basic.getVariousIntegerParam("DefaultStartAddress")) {
                if (data2.data().grps[1].g == 0x01) {
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
        if (!data2.isValid()) return;
        if ((val & 0xff00) == 0) return;

        // Fixed address setting for BASIC
        //
        // Set addresses for machine language using SetStartAddress(), SetEndAddress(),
        // SetExecuteAddress()
        //
        switch (val & 0xff) {
            case TYPE_NAME_DOS80_BASIC:
                // For BASIC, set load address, end address, and execution address as fixed
                data2.data().grps[0].a = basic.orderUint16((short) basic.getVariousIntegerParam("DefaultStartAddress"));
                data2.data().grps[1].g = 1;
                data2.data().grps[1].a = basic.orderUint16((short) 0);
                data2.data().grps[2].g = 0;
                data2.data().grps[2].a = basic.orderUint16((short) basic.getVariousIntegerParam("DefaultExecuteAddress"));
                break;
            case TYPE_NAME_DOS80_BASIC_MACHINE:
                // For BASIC + Machine, set load address and execution address as fixed
                data2.data().grps[0].a = basic.orderUint16((short) basic.getVariousIntegerParam("DefaultStartAddress"));
                data2.data().grps[3].g = 0;
                data2.data().grps[3].a = basic.orderUint16((short) basic.getVariousIntegerParam("DefaultExecuteAddress"));
                break;
        }
    }

    /**
     * Check if this item is used.
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.data.data().name[0] != 0 && this.data.data().name[0] != (byte) 0xff && this.getStartGroup(0) != 0;
    }

    /**
     * Convert attribute from file unit to DOS80 type.
     */
    private int convFileAttrFromTypePos(int t1) {
        return switch (t1) {
            // Machine
            case TYPE_NAME_DOS80_MACHINE -> FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue();
            // BASIC + Machine
            case TYPE_NAME_DOS80_BASIC_MACHINE -> FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue();
            // BASIC
            default -> FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue();
        };
    }


    /**
     * Convert DOS80 type to attribute.
     */
    private int convFileAttrToTypePos(int fileType) {
        int t1 = TYPE_NAME_DOS80_BASIC;
        int s = fileType & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue());
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
        for (int fileUnitNum = 0; fileUnitNum < 4; fileUnitNum++) {
            if (!isValidFileUnit(fileUnitNum)) {
                break;
            }
            if (!isUsed()) {
                break;
            }
            DiskBasicGroups groups = new DiskBasicGroups();
            getUnitGroups(fileUnitNum, groups);
            groups.add(groups);
            if (fileUnitNum < 2) fileUnit[fileUnitNum] = groups;
        }
    }

    @Override
    public void setData(int num, DiskBasicGroupItem groupItem,
                        DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos,
                        SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryDos80.class, data, dataPos);

        // Attributes and others are 2 sectors later
        DiskImageSector sector_2 = basic.getSector(groupItem.track, groupItem.side, sector.getSectorNumber() + 2);
        if (sector_2 != null) {
            byte[] buffer2 = sector_2.getSectorBuffer();
            data2.attach(DirectoryDos80_2.class, buffer2, sectorPos);
        }
    }

    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        if (data.data().name[0] == 0) {
            //last = true;
            return valid;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        // Deletion is simply putting a code at the beginning of the entry
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int val = fileType.getType();
        int t1 = convFileAttrToTypePos(val);

        cachedType = (1 << 8 | t1);

        setFileType1(cachedType);
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
        return rb.getString(typeNameDOS80[file_type_1]);
    }

    @Override
    public void setFileSize(int val) {
        // Round file size to sector size boundary
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
    public void setStartGroup(int fileUnitNum, int val, int size) {
        if (data2.isValid()) data2.data().grps[fileUnitNum].g = (byte) (val & 0xff);
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        return (data2.isValid() ? data2.data().grps[fileUnitNum].g & 0xff : 0);
    }

    @Override
    public boolean hasAddress() {
        return data2.isValid();
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
        if (data2.isValid()) {
            switch (t1) {
                case TYPE_NAME_DOS80_BASIC_MACHINE:
                    val = basic.orderUint16(data2.data().grps[1].a);
                    break;
                default:
                    val = basic.orderUint16(data2.data().grps[0].a);
                    break;
            }
        }
        return val;
    }

    @Override
    public int getEndAddress() {
        int t1 = getFileType1();
        int val = 0;
        if (data2.isValid()) {
            val = switch (t1) {
                case TYPE_NAME_DOS80_BASIC_MACHINE -> basic.orderUint16(data2.data().grps[2].a);
                default -> basic.orderUint16(data2.data().grps[1].a);
            };
        }
        return val;
    }

    @Override
    public int getExecuteAddress() {
        int t1 = getFileType1();
        int val = 0;
        if (data2.isValid()) {
            val = switch (t1) {
                case TYPE_NAME_DOS80_BASIC_MACHINE -> basic.orderUint16(data2.data().grps[3].a);
                default -> basic.orderUint16(data2.data().grps[2].a);
            };
        }
        return val;
    }

    @Override
    public void setStartAddress(int val) {
        if (!data2.isValid()) return;
        if ((cachedType & 0xff00) == 0) return;

        switch (cachedType & 0xff) {
            case TYPE_NAME_DOS80_MACHINE:
                data2.data().grps[0].a = basic.orderUint16((short) val);
                break;
            case TYPE_NAME_DOS80_BASIC_MACHINE:
                data2.data().grps[1].a = basic.orderUint16((short) val);
                break;
        }
    }

    @Override
    public void setEndAddress(int val) {
        if (!data2.isValid()) return;
        if ((cachedType & 0xff00) == 0) return;

        switch (cachedType & 0xff) {
            case TYPE_NAME_DOS80_MACHINE:
                data2.data().grps[1].g = 0;
                data2.data().grps[1].a = basic.orderUint16((short) val);
                break;
            case TYPE_NAME_DOS80_BASIC_MACHINE:
                data2.data().grps[2].g = 0;
                data2.data().grps[2].a = basic.orderUint16((short) val);
                break;
        }
    }

    @Override
    public void setExecuteAddress(int val) {
        if (!data2.isValid()) return;
        if ((cachedType & 0xff00) == 0) return;

        switch (cachedType & 0xff) {
            case TYPE_NAME_DOS80_MACHINE:
                data2.data().grps[2].g = 0;
                data2.data().grps[2].a = basic.orderUint16((short) val);
                break;
        }
    }

    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) {
        return fileSize;
    }

    @Override
    public int getFileUnitSize(int fileUnitNum, InputStream iStream, int fileOffset) throws IOException {
        int fileType1 = getFileType1();
        int basicSize = iStream.available();
        int machineSize = -1;
        if (fileType1 == TYPE_NAME_DOS80_BASIC_MACHINE) {
            // Case of BASIC + Machine
            machineSize = getEndAddress() - getStartAddress();
            if (machineSize >= 0) {
                machineSize++;
                machineSize = ((machineSize + 255) & ~0xff);
            }

            basicSize -= machineSize;
        }

        if (fileUnitNum == 0) {
            return basicSize;
        }
        if (fileUnitNum == 1) {
            return machineSize;
        }
        return -1;
    }

    @Override
    public boolean isValidFileUnit(int fileUnitNum) {
        if (fileUnitNum == 0) {
            return true;
        }
        if (fileUnitNum == 1) {
            int file_type_1 = getFileType1();

            if (file_type_1 == TYPE_NAME_DOS80_BASIC_MACHINE) {
                // Case of BASIC + Machine
                return true;
            }
        }
        return false;
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryDos80 getData() {
        return data.data();
    }

    @Override
    public byte[] getRawData() {
        return data.getRawData();
    }

    @Override
    protected void flushData() throws IOException {
        data.flush();
        data2.flush();
    }

    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val);
    }

    @Override
    public void clearData() {
        data.fill(basic.getFillCodeOnDir());
    }

    @Override
    public void copyItem(DiskBasicDirItem src) {
        super.copyItem(src);

        DiskBasicDirItemDOS80 pSrc = (DiskBasicDirItemDOS80) src;

        if (data2.isValid() && pSrc.data2.isValid()) {
            data2.copy(pSrc.data2.getRawData());
        }
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }
}
