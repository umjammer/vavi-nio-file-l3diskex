///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.DiskBasicDirItemTRSD13.DirectoryTrsD13;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.DiskBasicDirItemTRSD23.DirectoryTrsD23;
import l3diskex.basicfmt.type.DiskBasicTypeTRSDOS;
import l3diskex.basicfmt.type.DiskBasicTypeTRSDOS.DiskBasicTypeTRSD13;
import l3diskex.basicfmt.type.DiskBasicTypeTRSDOS.DiskBasicTypeTRSD23;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeL31S.FORMAT_TYPE_L3_1S;
import static l3diskex.basicfmt.type.DiskBasicTypeTRSDOS.DiskBasicTypeTRSD23.FORMAT_TYPE_TRSD23;


/**
 * Directory 1 item TRSDOS Base
 *
 * @see DiskBasicTypeTRSDOS
 */
public abstract class DiskBasicDirItemTRSDOS<T extends Directory> extends DiskBasicDirItem<T> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * TRSDOS gap
     */
    @Serdes
    public static class TrsDosGap {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte granules;
    }

    // TRSDOS attribute position
    public static final int FILETYPE_MASK_TRSDOS_ACCESS = 0x07;
    public static final int FILETYPE_MASK_TRSDOS_INVISIBLE = 0x08;
    public static final int FILETYPE_MASK_TRSDOS_INUSE = 0x10;
    public static final int FILETYPE_MASK_TRSDOS_SYSTEM = 0x40;
    public static final int FILETYPE_MASK_TRSDOS_OVERFLOW = 0x80;

    /** TRSDOS attribute values */
    static final Map<String, Object> typeNameTrsDos = new LinkedHashMap<>() {{
        put("Invisible", FILETYPE_MASK_TRSDOS_INVISIBLE);
        put("System", FILETYPE_MASK_TRSDOS_SYSTEM);
        put("Overflow", FILETYPE_MASK_TRSDOS_OVERFLOW);
    }};

    /** TRSDOS attribute names */
    static final Map<String, Object> typeNameTrsDos2 = new LinkedHashMap<>() {{
        put("SYS", FILETYPE_MASK_TRSDOS_SYSTEM);
    }};

    /** TRSDOS attribute position */
    static final int TYPE_NAME_2_TRSDOS_SYS = 0;

    /** Next entry (if overflow exists) */
    protected DiskBasicDirItemTRSDOS<T> nextItem;

    /** Position of HIT */
    protected int positionInHit;

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        positionInHit = -1;
        nextItem = null;
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        positionInHit = -1;
        nextItem = null;
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        positionInHit = -1;
        nextItem = null;
    }

    /**
     * Set pointer to item
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);
        positionInHit = -1;
    }

    /**
     * Whether it is a used item
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        boolean used = false;
        if (positionInHit >= 0) {
            used = (((DiskBasicTypeTRSDOS<T>) type).hit.getHI(positionInHit) != 0);
        }
        used &= ((getFileType1() & FILETYPE_MASK_TRSDOS_INUSE) != 0);
        return used;
    }

    /**
     * Extract addresses inside file
     */
    protected void takeAddressesInFile(DiskBasicGroups groupItems) {
        if (groupItems.size() == 0) {
            return;
        }
        //DiskBasicGroupItem item = groupItems.get(0);
        //DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        //if (sector == null) return;

        // Start address
        //startAddress = (int) sector.get16(0);
    }

    /**
     * Returns position in list from attribute
     */
    protected int convFileType1Pos(int type1) {
        return 0;
    }

    /**
     * Delete
     */
    @Override
    public boolean delete() throws IOException {
        // Delete
        used(false);
        setFileType1(0);
        // Delete GAT entry
        type.deleteGroups(groups);
        // Also delete HIT entry
        if (positionInHit >= 0) {
            ((DiskBasicTypeTRSDOS<T>) type).hit.deleteHI(positionInHit);
        }
        // When overflow exists
        if (nextItem != null) {
            nextItem.delete();
            nextItem = null;
        }
        return true;
    }

    /**
     * Set attribute
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // From same OS
            int t1 = fileType.getOrigin(0);

            setFileType1(t1);
        } else {
            // From different OS
            int t1 = FILETYPE_MASK_TRSDOS_INUSE;

            if ((fType & FILE_TYPE_HIDDEN_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_TRSDOS_INVISIBLE;
            }
            if ((fType & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_TRSDOS_SYSTEM;
            }

            setFileType1(t1);
        }
    }

    /**
     * Returns attribute
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int t1 = getFileType1();

        if ((t1 & FILETYPE_MASK_TRSDOS_INVISIBLE) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((t1 & FILETYPE_MASK_TRSDOS_SYSTEM) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    /**
     * Set position in HIT
     */
    public void setPositionInHIT(byte val) {
        positionInHit = val;
    }

    /**
     * Returns position in HIT
     */
    public byte getPositionInHIT() {
        return (byte) positionInHit;
    }

    /**
     * Set next item
     */
    public void setNextItem(DiskBasicDirItem<T> val) {
        nextItem = (DiskBasicDirItemTRSDOS<T>) val;
    }

    /**
     * Returns next item
     */
    public DiskBasicDirItemTRSDOS<T> getNextItem() {
        return nextItem;
    }

    /**
     * Returns attribute string (for file list display)
     */
    @Override
    public String getFileAttrStr() {
        StringBuilder str = new StringBuilder();
        int val = getFileType1();
        for (int i = 0; i < typeNameTrsDos.size(); i++) {
            if ((val & (int) Utils.valueAt(typeNameTrsDos, i)) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(Utils.keyAt(typeNameTrsDos, i)));
            }
        }
        return str.toString();
    }

    /**
     * Calculate size of the last sector and return file size
     */
    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) {
        return occupiedSize;
    }

    /**
     * Set the first group number
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        setGranulesOnGap(0, val, size);
    }

    /**
     * Returns the first group number
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return getGranulesOnGap(0, new int[1]);
    }

    /**
     * Set Overflow
     */
    public void setOverflow(byte val) {
    }

    /**
     * Returns Overflow
     */
    public int getOverflow() {
        return 0;
    }

    /**
     * Set Granule number of GAP
     */
    public void setGranulesOnGap(int pos, int val, int cnt) {
    }

    /**
     * Clear Granule number of GAP
     */
    public void clearGranulesOnGap(int pos, int track, int granule) {
    }

    /**
     * Returns Granule number of GAP
     */
    public int getGranulesOnGap(int pos, int[] cnt /* = {0} */) {
        return 0;
    }

    /**
     * Set as new file
     */
    public void setAsNewFile() {
    }

    /**
     * Set as Overflow file
     */
    public void setAsOverflowFile(byte positionInHit, byte hashCode) {
    }

    /**
     * Set as "BOOT/SYS"
     */
    public void setAsBootSysEntry() {
    }

    /**
     * Set as "DIR/SYS"
     */
    public void setAsDirSysEntry() {
    }

    /**
     * Whether EOF code needs to be checked
     */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /**
     * Processing required before importing data
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        // Replace '.' before extension with '/'
        int pos = filename[0].indexOf('.');
        if (pos != -1) {
            filename[0] = filename[0].substring(0, pos) + basic.getExtensionPreCode() + filename[0].substring(pos + 1);
        }
        return true;
    }

    /**
     * Determine attribute from file name
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        // Set attribute by extension
        isContainAttrByExtension(filename, typeNameTrsDos2, 0, TYPE_NAME_2_TRSDOS_SYS, null, t1, null);
        return t1[0];
    }

    /**
     * Whether item can be deleted
     */
    @Override
    public boolean isDeletable() {
        int sType = getFileType1();
        return sType != FILETYPE_MASK_TRSDOS_SYSTEM;
    }

    /**
     * Set sector to which item belongs as modified
     */
    @Override
    public void setModify() {
        //sdata.copyFrom(data.data());
    }

    /**
     * Returns attribute 1
     */
    @Override
    public abstract int getFileType1();

    /**
     * Set attribute 1
     */
    @Override
    protected abstract void setFileType1(int val);

    /**
     * Directory 1 item TRSDOS 2.x
     *
     * @see DiskBasicTypeTRSD23
     */
    public static class DiskBasicDirItemTRSD23 extends DiskBasicDirItemTRSDOS<DirectoryTrsD23> {

        /**
         * Directory entry TRSDOS 2.x (32bytes)
         */
        @Serdes
        public static class DirectoryTrsD23 implements Directory {

            @Element(sequence = 1)
            public byte accessControl;
            @Element(sequence = 2)
            public byte overflow;
            @Element(sequence = 3)
            public byte reserved1;
            @Element(sequence = 4)
            public byte eofByteOffset;
            @Element(sequence = 5)
            public byte recordLength;
            @Element(sequence = 6)
            public byte[] name = new byte[8];
            @Element(sequence = 7)
            public byte[] ext = new byte[3];
            @Element(sequence = 8)
            public short updatePassword;
            @Element(sequence = 9)
            public short accessPassword;
            @Element(sequence = 10)
            public short eofSector;
            @Element(sequence = 11)
            public TrsDosGap[] gap = new TrsDosGap[5];

            public DirectoryTrsD23() {
                for (int i = 0; i < 5; i++) {
                    gap[i] = new TrsDosGap();
                }
            }

            public static final int SIZE = 32;
        }

        /** Directory data */
        protected DiskBasicDirData<DirectoryTrsD23> data = new DiskBasicDirData<>();

        @Override
        public boolean isSupported(int formatType) {
            return formatType == FORMAT_TYPE_TRSD23;
        }

        @Override
        public void init(DiskBasic basic) throws IOException {
            super.init(basic);

            data.alloc(DirectoryTrsD23.class);
        }

        @Override
        public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
            super.init(basic, sector, sectorPos, data, dataP);

            this.data.attach(DirectoryTrsD23.class, data, dataP);
            if (sector != null) {
                positionInHit = DiskBasicTypeTRSD23.getHIPosition(sector.getSectorNumber() - basic.getSectorNumberBase(), sectorPos / getDataSize());
            }
        }

        @Override
        public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                         byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
            super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

            this.data.attach(DirectoryTrsD23.class, data, dataP);
            positionInHit = DiskBasicTypeTRSD23.getHIPosition(sector.getSectorNumber() - basic.getSectorNumberBase(), sectorPos / getDataSize());

            used(checkUsed(unuse[0]));
        }

        /**
         * Set pointer to item
         */
        @Override
        public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next) throws IOException {
            super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

            this.data.attach(DirectoryTrsD23.class, data, dataPos);
            positionInHit = DiskBasicTypeTRSD23.getHIPosition(sector.getSectorNumber() - basic.getSectorNumberBase(), sectorPos / getDataSize());
        }

        /**
         * Check directory item
         */
        @Override
        public boolean check(boolean[] last) {
            if (!data.isValid()) return false;

            int overflow = getOverflow() & 0xff;
            if (overflow > 0 && overflow < 254) {
                // Associate with reference item
                int[] overflowSectorNum = {0};
                int[] overflowSectorPos = {0};
                ((DiskBasicTypeTRSD23) type).getFromHIPosition(overflow, overflowSectorNum, overflowSectorPos);
                // Calculate serial number
                int num = (overflowSectorNum[0] - 2) * basic.getSectorSize() / getDataSize() + overflowSectorPos[0];
                int maxNum = (basic.getDirEndSector() - basic.getDirStartSector() + 1) * basic.getSectorSize() / getDataSize();
                if (num >= maxNum) {
                    // invalid chain
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns attribute 1
         */
        @Override
        public int getFileType1() {
            return data.data().accessControl & 0xff;
        }

        /**
         * Set attribute 1
         */
        @Override
        protected void setFileType1(int val) {
            data.data().accessControl = (byte) (val & 0xff);
        }

        /**
         * Set Overflow
         */
        @Override
        public void setOverflow(byte val) {
            data.data().overflow = (byte) (val & 0xff);
        }

        /**
         * Returns Overflow
         */
        @Override
        public int getOverflow() {
            return data.data().overflow & 0xff;
        }

        /**
         * Returns position where file name is stored
         */
        @Override
        public byte[] getFileNamePos(int num, int[] size, int[] len) {
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
        public byte[] getFileExtPos(int[] len) {
            len[0] = data.data().ext.length;
            return data.data().ext;
        }

        /**
         * Set as new file
         */
        @Override
        public void setAsNewFile() {
            used(true);
            setFileType1(getFileType1() | FILETYPE_MASK_TRSDOS_INUSE);
            // Register in HIT entry
            byte h = DiskBasicTypeTRSD23.computeHI(data.data().name);
            if (positionInHit >= 0) {
                ((DiskBasicTypeTRSDOS<DirectoryTrsD23>) type).hit.setHI(positionInHit, h);
            }
            // Password
            data.data().accessPassword = data.data().updatePassword = (short) 0x4296;

            // Clear entry
            for (int pos = 0; pos < data.data().gap.length; pos++) {
                clearGranulesOnGap(pos, 0xff, 0xff);
            }
        }

        /**
         * Set as Overflow file
         */
        @Override
        public void setAsOverflowFile(byte positionInHit, byte hashCode) {
            clearData();

            used(true);
            visible(false);

            setFileType1(FILETYPE_MASK_TRSDOS_OVERFLOW | FILETYPE_MASK_TRSDOS_INUSE);
            // Register in HIT entry
            if (this.positionInHit >= 0) {
                ((DiskBasicTypeTRSDOS<DirectoryTrsD23>) type).hit.setHI(this.positionInHit, hashCode);
            }
            setOverflow(positionInHit);
            // Password
            //data.data().accessPassword = data.data().updatePassword = 0x4296;

            // Clear entry
            for (int pos = 0; pos < data.data().gap.length; pos++) {
                clearGranulesOnGap(pos, 0xff, 0xff);
            }
        }

        /**
         * Set as "BOOT/SYS"
         */
        @Override
        public void setAsBootSysEntry() {
            setFileNameStr("BOOT/SYS");
            setAsNewFile();
            setFileType1(FILETYPE_MASK_TRSDOS_SYSTEM | FILETYPE_MASK_TRSDOS_INUSE | FILETYPE_MASK_TRSDOS_INVISIBLE | 6);
            setStartGroup(0, 1, 0);
            setFileSize(basic.getSectorSize());
        }

        /**
         * Set as "DIR/SYS"
         */
        @Override
        public void setAsDirSysEntry() {
            setFileNameStr("DIR/SYS");
            setAsNewFile();
            setFileType1(FILETYPE_MASK_TRSDOS_SYSTEM | FILETYPE_MASK_TRSDOS_INUSE | FILETYPE_MASK_TRSDOS_INVISIBLE | 5);
            setStartGroup(0, basic.getManagedTrackNumber() * basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic(), basic.getGroupsPerTrack());
            setFileSize(basic.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic() * basic.getSectorSize());
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
            int calcGroups = 0;
            int calcFileSize = 0;

            int sector_size = basic.getSectorSize();
            int block_size = sector_size * basic.getSectorsPerGroup();
            int maxGroup = basic.getFatEndGroup();

            int remainSize = getFileSize();

            for (int pos = 0; pos < data.data().gap.length; pos++) {
                int[] count = {0};
                int groupNum = getGranulesOnGap(pos, count);
                if (groupNum >= maxGroup) break;

                for (int i = 0; i < count[0]; i++) {
                    basic.getNumsFromGroup(groupNum, 0, sector_size, remainSize, groupItems);
                    groupNum++;
                    calcGroups++;
                    calcFileSize += block_size;
                    remainSize -= block_size;
                }
            }
            // When overflow exists
            if (nextItem != null) {
                nextItem.getUnitGroups(fileUnitNum, groupItems);
            }

            groupItems.addNums(calcGroups);
            groupItems.addSize(calcFileSize);
            groupItems.setSizePerGroup(block_size);

            // Get addresses inside file
            takeAddressesInFile(groupItems);
        }

        /**
         * Set file size
         */
        @Override
        public void setFileSize(int val) {
            int quotient = val / basic.getSectorSize();
            int remainder = val % basic.getSectorSize();
            if (remainder != 0) {
                quotient++;
            }
            data.data().eofSector = (short) quotient;
            data.data().eofByteOffset = (byte) (remainder & 0xff);
        }

        /**
         * Returns file size
         */
        @Override
        public int getFileSize() {
            int val = (data.data().eofSector & 0xffff) * basic.getSectorSize();
            if (data.data().eofByteOffset != 0) {
                val -= basic.getSectorSize();
                val += data.data().eofByteOffset & 0xff;
            }
            return val;
        }

        /**
         * Set Granule number of GAP
         */
        @Override
        public void setGranulesOnGap(int pos, int val, int cnt) {
            int block = basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
            int track = val / block;
            int start = val % block;
            data.data().gap[pos].track = (byte) (track & 0xff);
            data.data().gap[pos].granules = (byte) (((start << 5) & 0xe0) | ((cnt & 0x1f) - 1));
        }

        /**
         * Clear Granule number of GAP
         */
        @Override
        public void clearGranulesOnGap(int pos, int track, int granule) {
            data.data().gap[pos].track = (byte) (track & 0xff);
            data.data().gap[pos].granules = (byte) (granule & 0xff);
        }

        /**
         * Returns Granule number of GAP
         */
        @Override
        public int getGranulesOnGap(int pos, int[] cnt) {
            int val = data.data().gap[pos].track * basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
            int start = (data.data().gap[pos].granules & 0xe0) >> 5;
            val += start;
            if (cnt != null && cnt.length > 0) {
                cnt[0] = (data.data().gap[pos].granules & 0x1f) + 1;
            }
            return val;
        }

        /**
         * Size of directory item
         */
        @Override
        public int getDataSize() {
            return data.getDataSize();
        }

        /**
         * Returns item
         */
        @Override
        public DirectoryTrsD23 getData() {
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
         * Clear directory
         */
        @Override
        public void clearData() {
            data.fill(0);
        }

        /**
         * Set internal data displayed in properties
         */
        @Override
        public void setInternalDataInAttrDialog(KeyValArray vals) {
            vals.add("ACCESS_CONTROL", data.data().accessControl);
            vals.add("OVERFLOW", data.data().overflow);
            vals.add("EOF_BYTE_OFFSET", data.data().eofByteOffset);
            vals.add("RECORD_LENGTH", data.data().recordLength);
            vals.add("FILE_NAME", data.data().name, data.data().name.length);
            vals.add("EXTENSION", data.data().ext, data.data().ext.length);
            vals.add("UPDATE_PASSWORD", data.data().updatePassword);
            vals.add("ACCESS_PASSWORD", data.data().accessPassword);
            vals.add("EOF_SECTOR", data.data().eofSector);
            for (int i = 0; i < data.data().gap.length; i++) {
                vals.add(String.format("GAP%d TRACK", i + 1), data.data().gap[i].track);
                vals.add(String.format("GAP%d GRANULES", i + 1), data.data().gap[i].granules);
            }
        }
    }

    /**
     * Directory 1 item TRSDOS 1.3
     *
     * @see DiskBasicTypeTRSD13
     */
    public static class DiskBasicDirItemTRSD13 extends DiskBasicDirItemTRSDOS<DirectoryTrsD13> {

        /**
         * Directory entry TRSDOS 1.3 (48bytes)
         */
        public static class DirectoryTrsD13 implements Directory {

            public byte accessControl;
            public byte month; // 0x01 - 0x0c
            public byte year;
            public byte eofByteOffset;
            public byte recordLength;
            public byte[] name = new byte[8];
            public byte[] ext = new byte[3];
            public short updatePassword;
            public short accessPassword;
            public short eofSector;
            public TrsDosGap[] gap = new TrsDosGap[13];

            public DirectoryTrsD13() {
                for (int i = 0; i < 13; i++) {
                    gap[i] = new TrsDosGap();
                }
            }

            public static final int SIZE = 48;
        }

        /** Directory data */
        protected DiskBasicDirData<DirectoryTrsD13> data = new DiskBasicDirData<>();

        public int getHIPosition(int pos) {
            return pos & 0xff;
        }

        @Override
        public boolean isSupported(int formatType) {
            return formatType == FORMAT_TYPE_L3_1S;
        }

        @Override
        public void init(DiskBasic basic) throws IOException {
            super.init(basic);
        }

        @Override
        public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
            super.init(basic, sector, sectorPos, data, dataP);

            this.data.attach(DirectoryTrsD13.class, data, dataP);
            if (sector != null) {
                int n = (basic.getSectorSize() / getDataSize());
                positionInHit = getHIPosition((sector.getSectorNumber() - basic.getSectorNumberBase() - 2) * n + (sectorPos / getDataSize()));
            }
        }

        @Override
        public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                         byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
            super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

            this.data.attach(DirectoryTrsD13.class, data, dataP);
            positionInHit = getHIPosition(num);

            used(checkUsed(unuse[0]));
        }

        /**
         * Set pointer to item
         */
        @Override
        public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                            byte[] data, int dataPos, SectorParam next) throws IOException {
            super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

            this.data.attach(DirectoryTrsD13.class, data, dataPos);
            positionInHit = getHIPosition(num);
        }

        /**
         * Check directory item
         */
        @Override
        public boolean check(boolean[] last) {
            if (!data.isValid()) return false;

            return true;
        }

        /**
         * Whether item has creation date and time
         */
        @Override
        public boolean hasCreateDateTime() {
            return true;
        }

        /**
         * Whether item has creation date
         */
        @Override
        public boolean hasCreateDate() {
            return true;
        }

        /**
         * Get creation date
         *
         * @return
         */
        @Override
        public LocalDate getFileCreateDate(LocalDateTime tm) {
            int yy = data.data().year & 0xff;
            if (yy < 80) {
                yy += 100;
            }
            return LocalDate.of(
                    yy,
                    data.data().month - 1,
                    1);
        }

        /**
         * Returns creation date
         */
        @Override
        public String getFileCreateDateStr() {
            LocalDateTime tm = LocalDateTime.now();
            LocalDate ld = getFileCreateDate(tm);
            return Utils.formatYMDStr(ld);
        }

        /**
         * Set creation date
         */
        @Override
        public void setFileCreateDate(LocalDateTime tm) {
            data.data().year = (byte) (tm.getYear() & 0xff);
            data.data().month = (byte) ((tm.getMonth().ordinal() + 1) & 0xff);
        }

        /**
         * Returns position where file name is stored
         */
        @Override
        public byte[] getFileNamePos(int num, int[] size, int[] len) {
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
        public byte[] getFileExtPos(int[] len) {
            len[0] = data.data().ext.length;
            return data.data().ext;
        }

        /**
         * Returns attribute 1
         */
        @Override
        public int getFileType1() {
            return data.data().accessControl & 0xff;
        }

        /**
         * Set attribute 1
         */
        @Override
        protected void setFileType1(int val) {
            data.data().accessControl = (byte) (val & 0xff);
        }

        /**
         * Set as new file
         */
        @Override
        public void setAsNewFile() {
            used(true);
            setFileType1(getFileType1() | FILETYPE_MASK_TRSDOS_INUSE);
            // Register in HIT entry
            byte h = DiskBasicTypeTRSD23.computeHI(data.data().name);
            if (positionInHit >= 0) {
                ((DiskBasicTypeTRSDOS<DirectoryTrsD13>) type).hit.setHI(positionInHit, h);
            }
            // Password
            data.data().accessPassword = data.data().updatePassword = (short) 0x5cef;

            // Clear entry
            for (int pos = 0; pos < data.data().gap.length; pos++) {
                clearGranulesOnGap(pos, 0xff, 0xff);
            }
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
            int calcGroups = 0;
            int calcFileSize = 0;

            int sectorSize = basic.getSectorSize();
            int blockSize = sectorSize * basic.getSectorsPerGroup();
            int maxGroup = basic.getFatEndGroup();

            int remainSize = getFileSize();

            for (int pos = 0; pos < data.data().gap.length; pos++) {
                int[] count = {0};
                int groupNum = getGranulesOnGap(pos, count);
                if (groupNum >= maxGroup) break;

                for (int i = 0; i < count[0]; i++) {
                    basic.getNumsFromGroup(groupNum, 0, sectorSize, remainSize, groupItems);
                    groupNum++;
                    calcGroups++;
                    calcFileSize += blockSize;
                    remainSize -= blockSize;
                }
            }
            // When overflow exists
            if (nextItem != null) {
                nextItem.getUnitGroups(fileUnitNum, groupItems);
            }

            groupItems.addNums(calcGroups);
            groupItems.addSize(calcFileSize);
            groupItems.setSizePerGroup(blockSize);

            // Get addresses inside file
            takeAddressesInFile(groupItems);
        }

        /**
         * Set file size
         */
        @Override
        public void setFileSize(int val) {
            int quotient = (val >>> 8);
            data.data().eofSector = (short) quotient;
            data.data().eofByteOffset = (byte) (val & 0xff);
        }

        /**
         * Returns file size
         */
        @Override
        public int getFileSize() {
            int val = data.data().eofSector & 0xffff;
            val <<= 8;
            val |= data.data().eofByteOffset & 0xff;
            return val;
        }

        /**
         * Set Granule number of GAP
         */
        @Override
        public void setGranulesOnGap(int pos, int val, int cnt) {
            int block = basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
            int track = val / block;
            int start = val % block;
            data.data().gap[pos].track = (byte) (track & 0xff);
            data.data().gap[pos].granules = (byte) (((start << 5) & 0xe0) | (cnt & 0x1f));
        }

        /**
         * Clear Granule number of GAP
         */
        @Override
        public void clearGranulesOnGap(int pos, int track, int granule) {
            data.data().gap[pos].track = (byte) (track & 0xff);
            data.data().gap[pos].granules = (byte) (granule & 0xff);
        }

        /**
         * Returns Granule number of GAP
         */
        @Override
        public int getGranulesOnGap(int pos, int[] cnt) {
            int val = (data.data().gap[pos].track & 0xff) * basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
            int start = ((data.data().gap[pos].granules & 0xe0) >> 5);
            val += start;
            if (cnt != null && cnt.length > 0) {
                cnt[0] = data.data().gap[pos].granules & 0x1f;
            }
            return val;
        }

        /**
         * Size of directory item
         */
        @Override
        public int getDataSize() {
            return data.getDataSize();
        }

        /**
         * Returns item
         */
        @Override
        public DirectoryTrsD13 getData() {
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
         * Clear directory
         */
        @Override
        public void clearData() {
            data.fill(0);
        }

        /**
         * Set internal data displayed in properties
         */
        @Override
        public void setInternalDataInAttrDialog(KeyValArray vals) {
            vals.add("ACCESS_CONTROL", data.data().accessControl);
            vals.add("MONTH", data.data().month);
            vals.add("YEAR", data.data().year);
            vals.add("EOF_BYTE_OFFSET", data.data().eofByteOffset);
            vals.add("RECORD_LENGTH", data.data().recordLength);
            vals.add("FILE_NAME", data.data().name, data.data().name.length);
            vals.add("EXTENSION", data.data().ext, data.data().ext.length);
            vals.add("UPDATE_PASSWORD", data.data().updatePassword);
            vals.add("ACCESS_PASSWORD", data.data().accessPassword);
            vals.add("EOF_SECTOR", data.data().eofSector);
            for (int i = 0; i < data.data().gap.length; i++) {
                vals.add(String.format("GAP%d TRACK", i + 1), data.data().gap[i].track);
                vals.add(String.format("GAP%d GRANULES", i + 1), data.data().gap[i].granules);
            }
        }
    }
}
