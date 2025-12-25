///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFROST.DirectoryFrost;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeFROST.FORMAT_TYPE_FROST;


/** Directory 1 item Frost-DOS */
public class DiskBasicDirItemFROST extends DiskBasicDirItem<DirectoryFrost> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Directory entry Frost-DOS (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryFrost implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[6];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte track;
        @Element(sequence = 5)
        public byte sector;
        @Element(sequence = 6)
        public short loadAddress;
        @Element(sequence = 7)
        public short size;

        public static final int SIZE = 16;
    }

    // Frost-DOS

    public static final int FROST_GROUP_SIZE = 512;

    public static final int TYPE_NAME_FROST_BAS = 0;
    public static final int TYPE_NAME_FROST_BIN = 1;
    public static final int TYPE_NAME_FROST_RGB = 2;
    public static final int TYPE_NAME_FROST_UNKNOWN = 3;

    public static final int FILETYPE_FROST_BAS = 0x00;
    public static final int FILETYPE_FROST_BIN = 0x01;
    public static final int FILETYPE_FROST_RGB = 0x02;

    /// Frost-DOS attribute names
    public static final Map<String, Object> typeNameFROST1 = new LinkedHashMap<>() {{
        put("BAS", FILETYPE_FROST_BAS);
        put("BIN", FILETYPE_FROST_BIN);
        put("RGB", FILETYPE_FROST_RGB);
    }};

    //
    //
    //

    /** Directory data */
    private final DiskBasicDirData<DirectoryFrost> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_FROST;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryFrost.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);

        this.data.attach(DirectoryFrost.class, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        this.data.attach(DirectoryFrost.class, data, dataPos);

        used(checkUsed(unuse[0]));
        unuse[0] = (unuse[0] || (this.data.data().name[0] == (byte) 0xff));

        // Calculate file size and number of groups
        calcFileSize();
    }

    /**
     * Set pointer to item
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryFrost.class, data, dataPos);
    }

    /**
     * Returns position where file name is stored
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
     * Returns position where extension is stored
     */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().ext.length;
        return data.data().ext;
    }

    /**
     * Returns attribute 1
     */
    @Override
    public int getFileType1() {
        return data.data().type & 0xff;
    }

    /**
     * Set attribute 1
     */
    @Override
    protected void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    /**
     * Attribute 1 string
     */
    String convFileType1Str(int t1) {
        return rb.getString(Utils.keyAt(typeNameFROST1, convFileType1Pos(t1)));
    }

    /**
     * Whether it is a used item
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.data.data().name[0] != 0 && this.data.data().name[0] != (byte) 0xff;
    }

    /**
     * Check directory item
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        if (data.data().name[0] == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        // There is an invalid value in the attributes
        if ((getFileType1() & 0x0c) != 0) {
            valid = false;
        }
        return valid;
    }

    /**
     * Delete
     */
    @Override
    public boolean delete() {
        // Deletion is simply putting a code at the beginning of the entry
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /**
     * Whether there is an END mark (whether it has never been used)
     */
    @Override
    public boolean hasEndMark() {
        return ((data.data().name[0] & 0xff) == basic.getGroupUnusedCode());
    }

    /**
     * Put END mark in the next item
     */
    @Override
    public void setEndMark(DiskBasicDirItem<?> nextItem) {
        if (nextItem == null) return;

        if (hasEndMark()) ((DiskBasicDirItemFROST) nextItem).data.data().name[0] = (byte) basic.getGroupUnusedCode();
    }

    /**
     * Set attribute
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // Same OS
            t1 = fileType.getOrigin();
        } else {
            // Different OS
            if ((fType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FILETYPE_FROST_BIN;
            } else if ((fType & FILE_TYPE_DATA_MASK.getValue()) != 0) {
                t1 = FILETYPE_FROST_RGB;
            } else {
                t1 = FILETYPE_FROST_BAS;
            }
        }
        setFileType1(t1);

        // When BAS, set start address to 1
        if (getFileType1() == FILETYPE_FROST_BAS) {
            setStartAddress(1);
        }
    }

    /**
     * Returns attribute
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int type = 0;
        switch (t1) {
            case FILETYPE_FROST_BIN:
                type = FILE_TYPE_MACHINE_MASK.getValue();    // machine
                type |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case FILETYPE_FROST_RGB:
                type = FILE_TYPE_DATA_MASK.getValue();       // data
                type |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            default:
                type = FILE_TYPE_BASIC_MASK.getValue();      // basic
                type |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
        }

        if (isValidDirectory()) { // TODO ad-hoc if this is a root directory set directory type bit
            type |= FILE_TYPE_DIRECTORY_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), type, t1);
    }

    /**
     * Returns attribute string (for file list display)
     */
    @Override
    public String getFileAttrStr() {
        return convFileType1Str(getFileType1());
    }

    /**
     * Set file size
     */
    @Override
    public void setFileSize(int val) {
        data.data().size = basic.orderUint16((short) val);
        groups.setSize(val);
    }

    /**
     * Returns file size
     */
    @Override
    public int getFileSize() {
        int val = basic.orderUint16(data.data().size) & 0xffff;
        if (val == 0) val = groups.getSize();
        return val;
    }

    /**
     * Calculate file size and number of groups
     */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /**
     * Get all groups of specified directory
     */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        int calcFileSize = 0;
        int calcGroups = 0;

        // 16bit FAT (track & sector)
        boolean rc = true;
        int groupNum = getStartGroup(fileUnitNum);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int nextGroup = type.getGroupNumber(groupNum);
            if (nextGroup == groupNum) {
                // Error if same position
                rc = false;
            } else if (nextGroup >= basic.getGroupSystemCode()) {
                // System area is error (0xfefe)
                rc = false;
            } else if (nextGroup == basic.getGroupFinalCode()) {
                // Final group (0xfdfd)
                addGroups(groupNum, nextGroup, groupItems);
                calcFileSize += (basic.getSectorSize() / basic.getGroupsPerSector());
                calcGroups++;
                working = false;
            } else if (nextGroup > basic.getFatEndGroup()) {
                // Invalid group number
                rc = false;
            } else {
                addGroups(groupNum, nextGroup, groupItems);
                calcFileSize += (basic.getSectorSize() / basic.getGroupsPerSector());
                calcGroups++;
                groupNum = nextGroup;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        int interFileSize = getFileSize();
        if (interFileSize == 0) {
            interFileSize = calcFileSize;
        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(interFileSize);
        groupItems.setSizePerGroup(FROST_GROUP_SIZE);

        if (limit < 0) {
            // too large or infinite loop
            rc = false;
        }
    }

    /**
     * Add groups
     */
    private void addGroups(int groupNum, int nextGroup, DiskBasicGroups groupItems) {
        int[] track = {-1}, side = {-1}, sector = {-1}, div = {0}, divs = {0};
        basic.calcNumFromSectorPosForGroup(groupNum, track, side, sector, div, divs);
        groupItems.add(groupNum, nextGroup, track[0], side[0], sector[0], sector[0], div[0], divs[0]);
    }

    /**
     * Set the first group number
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        data.data().track = (byte) (val / basic.getGroupsPerTrack());
        data.data().sector = (byte) ((val % basic.getGroupsPerTrack()) + 1);
    }

    /**
     * Returns the first group number
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return (data.data().track & 0xff) * basic.getGroupsPerTrack() + (data.data().sector & 0xff) - 1;
    }

    /**
     * Whether the item has address
     */
    @Override
    public boolean hasAddress() {
        return true;
    }

    /**
     * Whether the item has execution address
     */
    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    /**
     * Returns start address
     */
    @Override
    public int getStartAddress() {
        return basic.orderUint16(data.data().loadAddress) & 0xffff;
    }

    /**
     * Set start address
     */
    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = basic.orderUint16((short) val);
    }

    /**
     * Whether EOF code needs to be checked
     */
    @Override
    public boolean needCheckEofCode() {
        // EOF code is needed when Asc format
        return false; //(((getFileType1() & (FILETYPE_FROST_MACHINE | FILETYPE_FROST_BINARY)) == 0) && (externalAttr == 0));
    }

    /**
     * Size of directory item
     */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /**
     * Returns item (internal data object)
     */
    @Override
    public DirectoryFrost getData() {
        return data.data();
    }

    /**
     * Copy item
     */
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val);
    }

    /**
     * Clear directory: during creation of a new file
     */
    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode());
    }

    /**
     * Processing required before exporting data
     */
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        // Attach extension
        if (!isDirectory()) {
            String ext = convFileType1Str(getFileType1());
            filename[0] += ".";
            if (Utils.isUpperString(filename[0])) {
                ext = ext.toUpperCase();
            } else {
                ext = ext.toLowerCase();
            }
            filename[0] += ext;
        }
        return true;
    }

    /**
     * Generate internal file name from file path before displaying import dialog
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], typeNameFROST1, TYPE_NAME_FROST_BAS, TYPE_NAME_FROST_RGB, filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * Determine attribute from file name
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        // Set attribute by extension
        if (!isContainAttrByExtension(filename, typeNameFROST1, TYPE_NAME_FROST_BAS, TYPE_NAME_FROST_RGB, null, t1, null)) {
            t1[0] = FILETYPE_FROST_BIN;
        }
        return t1[0];
    }

    //
    // For dialog
    //

    /**
     * Returns position in list from attribute (for property dialog)
     */
    int convFileType1Pos(int t1) {
        if (t1 < 0 || t1 > TYPE_NAME_FROST_UNKNOWN) {
            t1 = TYPE_NAME_FROST_UNKNOWN;
        }
        return t1;
    }

    /**
     * Set internal data displayed in properties
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXT", data.data().ext, data.data().ext.length);
        vals.add("TYPE", data.data().type & 0xff);
        vals.add("TRACK", data.data().track & 0xff);
        vals.add("SECTOR", data.data().sector & 0xff);
        vals.add("LOAD_ADDR", (byte) data.data().loadAddress, basic.isBigEndian());
        vals.add("SIZE", (byte) data.data().size, basic.isBigEndian());
    }
}
