///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMDOS.DirectoryMdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeMDOS.FORMAT_TYPE_MDOS;


/**
 * Directory 1 item MDOS
 */
public class DiskBasicDirItemMDOS extends DiskBasicDirItem<DirectoryMdos> {

    /**
     * Directory entry MDOS (16bytes)
     */
    @Serdes
    public static class DirectoryMdos implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte unknown; // byte
        @Element(sequence = 4, bigEndian = "false")
        public short startGroup; // little endian
        @Element(sequence = 5)
        public short fileSize; // big endian

        public static final int SIZE = 16;
    }

    /** Directory data */
    private final DiskBasicDirData<DirectoryMdos> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_MDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryMdos.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryMdos.class, data, dataP);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectoryMdos.class, data, dataP);
        used(checkUsed(unuse[0]));
        unuse[0] = (unuse[0] || (this.data.data() != null && this.data.data().name != null && this.data.data().name[0] == 0));

        // Calculate file size and number of groups
        calcFileSize();
    }

    /** Set pointer to item */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryMdos.class, data, dataPos);
    }

    /** Check directory item */
    @Override
    public boolean check(boolean[] last) {
        byte[] data = this.data.getRawData();
        return DiskBasicDirItem.checkData((data != null) ? data : null, getDataSize(), last);
    }

    /** Delete */
    @Override
    public boolean delete() {
        // Deletion is simply putting a code at the beginning of the entry
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /** Returns position where file name is stored */
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

    /** Returns position where extension is stored */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().ext.length;
        return data.data().ext;
    }

//    /** Returns attribute 1 */
//    public int getFileType1();

    /** Set attribute 1 */
    @Override
    protected void setFileType1(int val) {
    }

    /** Whether it is a used item */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.data.data().name[0] != 0;
    }

    /** Returns attribute */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = FILE_TYPE_BINARY_MASK.getValue();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, 0);
    }

    /** Returns attribute string (for file list display) */
    @Override
    public String getFileAttrStr() {
        return "";
    }

    /** Set file size */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        data.data().fileSize = (short) val; // le
    }

    /** Returns file size */
    @Override
    public int getFileSize() {
        short val = data.data().fileSize;
        return val /* le */ & 0xffff;
    }

    /** Calculate file size and number of groups */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /** Get all groups of specified directory */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        int calcFileSize = 0;
        int calcGroups = 0;

        // 16bit FAT
        boolean rc = true;
        int groupNum = getStartGroup(fileUnitNum);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int nextGroup = basic.getType().getGroupNumber(groupNum);
            if (nextGroup == groupNum) {
                // Error if same position
                rc = false;
            } else if (nextGroup == basic.getGroupFinalCode()) {
                // Final group (0xffff)
                working = false;
            } else if (nextGroup > basic.getFatEndGroup()) {
                // Invalid group number
                rc = false;
            } else if (nextGroup >= basic.getGroupSystemCode()) {
                // System area is error (0xeeee)
                rc = false;
            }
            if (rc) {
                basic.getNumsFromGroup(groupNum, nextGroup, basic.getSectorSize(), 0, groupItems);
                calcFileSize += (basic.getSectorSize() * basic.getSectorsPerGroup());
                calcGroups++;
                groupNum = nextGroup;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        groupItems.addNums(calcGroups);
        groupItems.addSize(calcFileSize >= getFileSize() ? getFileSize() : calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinit loop
            rc = false;
        }
    }

    /** Set the first group number */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        data.data().startGroup = (short) (val & 0xffff); // be
    }

    /** Returns the first group number */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return data.data().startGroup & 0xffff; // be
    }

    /** Whether EOF code needs to be checked */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /** Recalculate file size on save: when EOF code is needed, etc. */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) {
        return fileSize;
    }

    /** Size of directory item */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** Returns item */
    @Override
    public DirectoryMdos getData() {
        return data.data();
    }

    /** Copy item */
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val);
    }

    /** Clear directory: during creation of a new file */
    @Override
    public void clearData() {
        data.fill(basic.getFillCodeOnDir());
    }

    /** Whether the item has execution address */
    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    /** Determine attribute from file name */
    @Override
    public int convFileTypeFromFileName(String filename) {
        int fType = FILE_TYPE_BINARY_MASK.getValue();
        return fType;
    }

    /** Determine attribute from file name */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        return t1;
    }

    /** Set internal data displayed in properties */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXT", data.data().ext, data.data().ext.length);
        vals.add("UNKNOWN", data.data().unknown);
        vals.add("START_GROUP", data.data().startGroup);
        vals.add("FILE_SIZE", data.data().fileSize);
    }
}
