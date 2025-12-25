/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.DirectorySDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeSDOS.FORMAT_TYPE_SDOS;


/**
 * Directory 1 item S-DOS
 */
public class DiskBasicDirItemSDOS extends DiskBasicDirItem<DirectorySDos> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Directory entry S-DOS (32 bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectorySDos implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[22];
        @Element(sequence = 2)
        public byte type;
        @Element(sequence = 3)
        public byte track;
        @Element(sequence = 4)
        public byte sector;
        @Element(sequence = 5)
        public byte size; // number of sector
        @Element(sequence = 6)
        public byte restSize;
        @Element(sequence = 7)
        public short loadAddress;
        @Element(sequence = 8)
        public short execAddress;
        @Element(sequence = 9)
        public byte reserved;

        public static final int SIZE = 32;
    }

    // S-DOS

    public static final int TYPE_NAME_SDOS_BAS1 = 0;
    public static final int TYPE_NAME_SDOS_BAS2 = 1;
    public static final int TYPE_NAME_SDOS_DAT = 2;
    public static final int TYPE_NAME_SDOS_OBJ = 3;
    public static final int TYPE_NAME_SDOS_UNKNOWN = 4;

    public static final int FILETYPE_SDOS_BAS1 = 0x00; // N mode
    public static final int FILETYPE_SDOS_BAS2 = 0x01; // n88 mode
    public static final int FILETYPE_SDOS_DAT = 0x02;  // except exec address
    public static final int FILETYPE_SDOS_OBJ = 0x0e;  // include exec address
    public static final int FILETYPE_SDOS_UNKNOWN = 0xff;

    /** S-DOS attribute names */
    public static final Map<String, Object> typeNameSdos1 = new LinkedHashMap<>() {{
        put("BASIC (N)", FILETYPE_SDOS_BAS1);
        put("BASIC (n88)", FILETYPE_SDOS_BAS2);
        put("Binary", FILETYPE_SDOS_DAT);
        put("Machine", FILETYPE_SDOS_OBJ);
        put("???", FILETYPE_SDOS_UNKNOWN);
    }};

    /** Directory data */
    private final DiskBasicDirData<DirectorySDos> data = new DiskBasicDirData<>();

    /** Pointer inside sector */
    private final DirItemSectorBoundary sectorData = new DirItemSectorBoundary();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_SDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectorySDos.class);
        allocateItem(null);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectorySDos.class, data, dataP);
        allocateItem(null);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectorySDos.class, data, dataP);
        allocateItem(next);

        used(checkUsed(unuse[0]));

        // Calculate file size and number of groups
        calcFileSize();
    }

    /**
     * Set pointer to item
     *
     * @param num    Serial number
     * @param groupItem  Data such as track number
     * @param sector Sector
     * @param sectorPos Position of directory entry within sector
     * @param data   Directory item
     * @param dataPos    offset of {@code n_data}
     * @param next   Next sector
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectorySDos.class, data, dataPos);
        allocateItem(next);
    }

    /**
     * Allocate directory entry
     *
     * @param next Next sector
     * @return true
     */
    private boolean allocateItem(SectorParam next) throws IOException {
        sectorData.clear();
        boolean bound = sectorData.set(basic, sector, position, data.getRawData(), getDataSize(), next);

        if (bound) {
            // If spanning across sectors, data is allocated internally
            data.alloc(DirectorySDos.class);
            data.fill(0, DirectorySDos.SIZE);
        }

        // Copy
        sectorData.copyTo(data.getRawData());

        return true;
    }

    /**
     * Returns the position to store the file name
     *
     * @param num  num
     * @param size size
     * @param len  len
     * @return File name
     */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = data.data().name.length;
            return data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /**
     * Returns attribute 1
     *
     * @return Attribute 1
     */
    @Override
    protected int getFileType1() {
        return data.data().type & 0xff;
    }

    /**
     * Set attribute 1
     *
     * @param val Attribute value
     */
    @Override
    protected void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    /**
     * Whether it is a used item
     *
     * @param unuse Unused flag
     * @return true: being used
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (!unuse && this.data.data().name[0] != 0 && this.data.data().name[0] != (byte) 0xff);
    }

    /**
     * Set file name
     *
     * @param filename File name
     * @param size     Size
     * @param length   Length
     */
    @Override
    public void setNativeName(byte[] filename, int size, int length) {
        super.setNativeName(filename, size, length);
    }

    /**
     * Get file name and extension
     *
     * @param name File name
     * @param nLen File name length
     * @param ext  Extension
     * @param eLen Extension length
     */
    @Override
    public void getNativeFileName(byte[] name, int[] nLen, byte[] ext, int[] eLen) {
        super.getNativeFileName(name, nLen, ext, eLen);
    }

    /**
     * Check directory item
     *
     * @param last Whether to terminate the check
     * @return Check OK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        if (data.data().name[0] == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        return valid;
    }

    /**
     * Delete
     *
     * @return true
     */
    @Override
    public boolean delete() {
        data.data().name[0] = basic.getDeleteCode();
        used(false);
        return true;
    }

    /**
     * Set attribute
     *
     * @param fileType File attribute
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = fileType.getOrigin();
        } else {
            if ((fType & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                if (basic.getFormatSubTypeNumber() != 0) {
                    t1 = FILETYPE_SDOS_BAS2;
                } else {
                    t1 = FILETYPE_SDOS_BAS1;
                }
            } else if ((fType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FILETYPE_SDOS_OBJ;
            } else {
                t1 = FILETYPE_SDOS_DAT;
            }
        }
        setFileType1(t1);

        setUnknownData();
    }

    /**
     * Return attribute
     *
     * @return File attribute
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1) {
            case FILETYPE_SDOS_DAT:
                val = FILE_TYPE_DATA_MASK.getValue();    // data
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            case FILETYPE_SDOS_OBJ:
                val = FILE_TYPE_MACHINE_MASK.getValue(); // machine
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            case FILETYPE_SDOS_BAS1:
            case FILETYPE_SDOS_BAS2:
                val = FILE_TYPE_BASIC_MASK.getValue();   // basic
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            default:
                val = FILE_TYPE_DATA_MASK.getValue();    // data
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
        }
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    /**
     * Returns the attribute string (for file list screen display)
     *
     * @return Attribute string
     */
    @Override
    public String getFileAttrStr() {
        String attr = rb.getString(Utils.keyAt(typeNameSdos1, getFileType1Pos()));
        return attr;
    }

    /**
     * Set file size
     *
     * @param val File size (bytes)
     */
    @Override
    public void setFileSize(int val) {
        int size = val + basic.getSectorSize() - 1;

        data.data().size = (byte) (size / basic.getSectorSize());
        data.data().restSize = (byte) ((val - 1) % basic.getSectorSize());
    }

    /**
     * Return file size
     *
     * @return File size (bytes)
     */
    @Override
    public int getFileSize() {
        int size;
        size = data.data().size & 0xff;

        if (size > 0) size--;
        size *= basic.getSectorSize();

        size += data.data().restSize & 0xff;

        size++;
        return size;
    }

    /**
     * Return number of groups
     *
     * @return Number of groups
     */
    @Override
    public int getGroupSize() {
        int size;
        size = data.data().size & 0xff;
        return size;
    }

    /**
     * Calculate file size
     *
     * @param fileUnitNum File number
     */
    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /**
     * Get all groups of the specified directory
     *
     * @param fileUnitNum File number
     * @param groupItems  Group list
     */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) {
        int calcGroups = 0;
        int calcFileSize = getFileSize();

        int groupNum = getStartGroup(fileUnitNum);
        int groupSize = getGroupSize();
        int limit = basic.getFatEndGroup() + 1;
        while (groupSize > 0 && limit >= 0) {
            addGroups(groupNum, 0, groupItems);
            groupNum++;
            calcGroups++;
            groupSize--;
            limit--;
        }

        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize());
    }

    /**
     * Add groups
     *
     * @param groupNum   Group number
     * @param nextGroup  Next group number
     * @param groupItems Group list
     */
    private void addGroups(int groupNum, int nextGroup, DiskBasicGroups groupItems) {
        int[] track = {-1}, side = {-1}, sector = {-1}, div = {0}, divs = {0};
        basic.calcNumFromSectorPosForGroup(groupNum, track, side, sector, div, divs);
        groupItems.add(groupNum, nextGroup, track[0], side[0], sector[0], sector[0], div[0], divs[0]);
    }

    /**
     * Set the first group number
     *
     * @param fileUnitNum File number
     * @param val          Group number
     * @param size         Group size
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int track = (val / basic.getSectorsPerTrackOnBasic()) & 0xff;
        int sector = ((val % basic.getSectorsPerTrackOnBasic()) + 1) & 0xff;
        data.data().track = (byte) track;
        data.data().sector = (byte) sector;
    }

    /**
     * Returns the first group number
     *
     * @param fileUnitNum File number
     * @return First group number
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        int track = 0;
        int sector = 0;
        track = data.data().track & 0xff;
        sector = data.data().sector & 0xff;

        return track * basic.getSectorsPerTrackOnBasic() + sector - 1;
    }

    /**
     * Returns start address
     *
     * @return Start address
     */
    @Override
    public int getStartAddress() {
        int address;
        address = data.data().loadAddress & 0xffff;

        return basic.orderUint16((short) address);
    }

    /**
     * Returns the execution address
     *
     * @return Execution address
     */
    @Override
    public int getExecuteAddress() {
        int address;
        address = data.data().execAddress & 0xffff;

        return basic.orderUint16((short) address);
    }

    /**
     * Set start address
     *
     * @param val Start address
     */
    @Override
    public void setStartAddress(int val) {
        int address = basic.orderUint16((short) val);
        data.data().loadAddress = (short) address;
    }

    /**
     * Set execution address
     *
     * @param val Execution address
     */
    @Override
    public void setExecuteAddress(int val) {
        int address = basic.orderUint16((short) val);
        data.data().execAddress = (short) address;
    }

    /**
     * Insert END mark into next item
     *
     * @param nextItem Next directory item
     */
    @Override
    public void setEndMark(DiskBasicDirItem<?> nextItem) throws IOException {
        if (nextItem == null) return;

        nextItem.delete();
    }

    /**
     *
     * @return true
     * Whether it is necessary to check the end of file code
     */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /**
     * Returns directory size
     *
     * @return Size
     */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /**
     * Returns item
     *
     * @return Item data
     */
    @Override
    public DirectorySDos getData() {
        return data.data();
    }

    /**
     * Copy item
     *
     * @param val Item
     * @return true
     */
    @Override
    public boolean copyData(byte[] val) {
        data.copy(val, getDataSize());
        sectorData.copyFrom(data.getRawData());
        return true;
    }

    /**
     * Clear directory
     */
    @Override
    public void clearData() {
        data.fill(basic.getFillCodeOnDir(), getDataSize());
    }

    /**
     * Setting of unused area
     */
    private void setUnknownData() {
        byte val = (byte) 0xff;
        data.data().reserved = val;
    }

    /**
     * Generate internal file name from file path before issuing dialog on import
     *
     * @param filename File name
     * @return false Exclude this file
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * Determine attribute from file name
     *
     * @param filename File name
     * @return Attribute
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = TYPE_NAME_SDOS_DAT;
        // Set attributes by extension
        MyAttribute attr = findUpperCase(basic.getAttributesByExtension(), Utils.getExt(filename));
        if (attr != null) {
            int saType = attr.getType();
            if ((saType & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
                if (basic.getFormatSubTypeNumber() != 0) {
                    t1 = TYPE_NAME_SDOS_BAS2;
                } else {
                    t1 = TYPE_NAME_SDOS_BAS1;
                }
            } else if ((saType & (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) == (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
                t1 = TYPE_NAME_SDOS_OBJ;
            }
        }
        return t1;
    }

    /**
     * Set the sector to which the item belongs as changed
     */
    @Override
    public void setModify() {
        sectorData.copyFrom(data.getRawData());
    }

    //
    // For dialog
    //

    /**
     * Returns the position in the list from the attribute (for property dialog)
     *
     * @return Position in list
     */
    public int getFileType1Pos() {
        int t1 = getFileType1();
        int pos = Utils.indexOf(typeNameSdos1, t1);
        if (pos < 0) {
            pos = TYPE_NAME_SDOS_UNKNOWN;
        }
        return pos;
    }

    /**
     * Returns the position in the list from the attribute (for property dialog)
     *
     * @return Position in list
     */
    public int getFileType2Pos() {
        return getFileAttr().getType();
    }

    /**
     * Set internal data to be displayed in properties
     *
     * @param vals List of names & values
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("TYPE", data.data().type);
        vals.add("TRACK", data.data().track);
        vals.add("SECTOR", data.data().sector);
        vals.add("SIZE", data.data().size);
        vals.add("REST SIZE", data.data().restSize);
        vals.add("LOAD ADDR", (byte) data.data().loadAddress, basic.isBigEndian());
        vals.add("EXEC ADDR", (byte) data.data().execAddress, basic.isBigEndian());
        vals.add("RESERVED", data.data().reserved);
    }
}
