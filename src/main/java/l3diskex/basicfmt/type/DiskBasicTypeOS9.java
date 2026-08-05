package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDateTime;
import java.util.List;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DirectoryOs9;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DirectoryOs9Fd;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DiskBasicDirItemOS9FD;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.Os9Date;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.Os9Lsn;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_NEW;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_DIRECTORY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_EXEC;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_READ;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_WRITE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_EXEC;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_READ;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_WRITE;


/**
 * OS-9 processing
 * <p>
 * DiskBasicParam
 *
 * <li>SubDirGroupSize : Initial number of groups (LSN) for subdirectories</li>
 * <li>GroupWidth      : Number of sectors per 1 bitmap bit (dd_BIT)</li>
 */
public class DiskBasicTypeOS9 extends DiskBasicType<DirectoryOs9> {

    private static final Logger logger = System.getLogger(DiskBasicTypeOS9.class.getName());

    /** OS-9 Ident LSN = 0(track1, sector1) */
    @Serdes
    static class Os9Ident {

        /** total lsn */
        @Element(sequence = 1)
        public Os9Lsn totalLsn = new Os9Lsn();
        /** sectors per track */
        @Element(sequence = 2)
        public byte sectorsPerTrack = 0;
        /** allocation map length */
        @Element(sequence = 3)
        public short mapLen = 0;
        /** sectors per group */
        @Element(sequence = 4)
        public short bit = 0;
        /** rootdir lsn */
        @Element(sequence = 5)
        public Os9Lsn rootDirLen = new Os9Lsn();
        /** owner id */
        @Element(sequence = 6)
        public short owner = 0;
        /** disk attr */
        @Element(sequence = 7)
        public byte attr = 0;
        /** disk ident */
        @Element(sequence = 8)
        public short disk = 0;
        /** format, density, number of sides */
        @Element(sequence = 9)
        public byte format = 0;
        /** sector per track */
        @Element(sequence = 10)
        public short sectorPerTrack = 0;
        @Element(sequence = 11)
        public short reserved1 = 0;
        /** bootstrap lsn */
        @Element(sequence = 12)
        public Os9Lsn bootstrapLsn = new Os9Lsn();
        /** bootstrap size (in bytes) */
        @Element(sequence = 13)
        public short bootstrapSize = 0;
        /** creation date */
        @Element(sequence = 14)
        public Os9Date cDate = new Os9Date();
        /** volume label */
        @Element(sequence = 15)
        public byte[] name = new byte[32];
        /** option */
        @Element(sequence = 16)
        public byte[] option = new byte[32];
        @Element(sequence = 17)
        public byte reserved2 = 0;
        /** media integrity code */
        @Element(sequence = 18)
        public int sync = 0;
        /** bitmap starting sector number */
        @Element(sequence = 19)
        public int mapLsn = 0;
        /** media logical sector size */
        @Element(sequence = 20)
        public short lsnSize = 0;
        /** version id */
        @Element(sequence = 21)
        public short version = 0;
    }

    /** OS-9 Allocation Map */
    static class OS9AllocMap extends DiskBasicBitMLMap {

        /** Bitmap size (bytes) */
        private int mapBytes;
        /** Start LSN */
        private int mapStartLsn;
        /** End LSN */
        private int endLsn;
        /** Sector size */
        private int sectorSize;
        /** Sectors per bit */
        private int sectorsPerBit;

        public OS9AllocMap() {
            mapBytes = 0;
            mapStartLsn = 0;
            endLsn = 0;
            sectorSize = 0;
            sectorsPerBit = 0;
        }

        /**
         * Allocate Allocation Map
         *
         * @param basic       Disk Basic parameters
         * @param mapStartLsn LSN where Map exists
         * @param mapBytes    Number of bytes used by Map
         * @return false: No sector
         */
        public boolean allocMap(DiskBasic basic, int mapStartLsn, int mapBytes) {
            this.mapBytes = mapBytes;
            this.mapStartLsn = mapStartLsn;
            endLsn = basic.getFatEndGroup();
            sectorSize = basic.getSectorSize();
            sectorsPerBit = basic.getGroupWidth();

            if (sectorsPerBit <= 0) sectorsPerBit = 1;

            boolean valid = true;
            int bytes = 0;

            list.clear();

            int mapEndLsn = (endLsn / sectorSize / 8 / sectorsPerBit) + 1;
            for (int mapLsn = this.mapStartLsn; mapLsn <= mapEndLsn && mapLsn <= 32 && bytes < this.mapBytes; mapLsn++) {
                DiskImageSector sector = basic.getManagedSector(mapLsn);
                if (sector == null) {
                    // error
                    valid = false;
                    break;
                }
                byte[] buf = sector.getSectorBuffer();
                int size = sector.getSectorSize();

                addBuffer(buf, size);

                bytes += size;
            }
            return valid;
        }

        /**
         * Create usage status based on Map
         *
         * @param fat [out] Usage status
         */
        public void makeAvailable(DiskBasicAvailability fat) {
            int bytes = 0;
            int lsn = 0;

            for (int mapIndex = 0; mapIndex < size() && bytes < mapBytes; mapIndex++) {
                byte[] buf = get(mapIndex).getBuffer();
                int size = get(mapIndex).getSize();

                for (int pos = 0; pos < size && lsn <= endLsn && bytes < mapBytes; pos++) {
                    for (int bit = 0; bit < 8 && lsn <= endLsn && bytes < mapBytes; bit++) {
                        boolean used = ((buf[pos] & (0x80 >> bit)) != 0);
                        for (int i = 0; i < sectorsPerBit && lsn <= endLsn; i++) {
                            if (!used) {
                                fat.add(FAT_AVAIL_FREE, sectorSize, 1);
                            } else {
                                fat.add(FAT_AVAIL_USED, 0, 0);
                            }
                            lsn++;
                        }
                    }
                    bytes++;
                }
            }
        }

        /**
         * Set LSN in Map
         *
         * @param lsn LSN
         * @param val Set / Reset
         */
        public void setLSN(int lsn, boolean val) {
            lsn /= sectorsPerBit;

            modify(lsn, val);
        }

        /**
         * Whether LSN is used
         *
         * @param lsn LSN
         * @return true if used
         */
        public boolean isUsedLSN(int lsn) {
            lsn /= sectorsPerBit;

            return isSet(lsn);
        }

        /**
         * Get a free LSN
         *
         * @return LSN or INVALID_GROUP_NUMBER
         */
        public int findEmpty() {
            int matchLsn = INVALID_GROUP_NUMBER;
            int bytes = 0;
            int lsn = 0;
            for (int mapIndex = 0; mapIndex < size() && bytes < mapBytes && matchLsn == INVALID_GROUP_NUMBER; mapIndex++) {
                byte[] buf = get(mapIndex).getBuffer();
                int size = get(mapIndex).getSize();

                for (int pos = 0; pos < size && lsn <= endLsn && bytes < mapBytes && matchLsn == INVALID_GROUP_NUMBER; pos++) {
                    for (int bit = 0; bit < 8 && lsn <= endLsn && bytes < mapBytes && matchLsn == INVALID_GROUP_NUMBER; bit++) {
                        boolean used = ((buf[pos] & (0x80 >> bit)) != 0);
                        if (!used) {
                            matchLsn = lsn;
                            break;
                        }
                        lsn += sectorsPerBit;
                    }
                    bytes++;
                }
            }
            return matchLsn;
        }

        public int getMapStartLSN() {
            return mapStartLsn;
        }

        public int getMapBytes() {
            return mapBytes;
        }
    }

    /** Identification Sector */
    private Os9Ident os9Ident;
    /** the sector {@link #os9Ident} was read from, used to write it back */
    private DiskImageSector os9IdentSector;

    /** Write the in memory identification sector back onto the sector it came from */
    private void writeIdent() throws IOException {
        if (os9IdentSector == null || os9Ident == null) return;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Serdes.Util.serialize(os9Ident, baos);
        os9IdentSector.copy(baos.toByteArray(), baos.size());
    }

    /** Allocation Map */
    private final OS9AllocMap allocMap = new OS9AllocMap();

    public static final int FORMAT_TYPE_OS9 = 9;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_OS9;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryOs9> dir) {
        super.init(basic, fat, dir);

        os9Ident = null;
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 ~ 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;

        double validRatio = 1.0;

        // Ident
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) {
            return -1.0;
        }
        os9Ident = new Os9Ident();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), os9Ident);
        os9IdentSector = sector;

        int iVal;

        // total groups
        int lVal = os9Ident.totalLsn.l;
        iVal = lVal;
        logger.log(Level.TRACE, "OS9: top: Total Sectors: %d".formatted(iVal));
        if (iVal < 1) {
            return -1.0;
        }
        iVal--;
        // Final group number
        basic.setFatEndGroup(iVal);

        // sectors per track
        iVal = os9Ident.sectorPerTrack;
        logger.log(Level.TRACE, "OS9: dd_SPT: Sectors per Track: %d".formatted(iVal));
        if (iVal == 0) {
            return -1.0;
        }
        if (iVal > basic.getSectorsPerTrack()) {
            logger.log(Level.TRACE, "OS9: %d > %d".formatted(iVal, basic.getSectorsPerTrack()));
            validRatio = 0.5;
        } else {
            basic.setSectorsPerTrackOnBasic(iVal);
        }

        // sectors per bit on bitmap table
        iVal = os9Ident.bit;
        logger.log(Level.TRACE, "OS9: dd_BIT: Sectors per Bit on Bitmap: %d".formatted(iVal));
        if (iVal == 0 || !Utils.IsPowerOfTwo(iVal, 16)) {
            return -1.0;
        }
        basic.setGroupWidth(iVal);

        // disk format
        iVal = os9Ident.format;
        String sVal = "";
        sVal = sVal + ((iVal & 1) != 0 ? "double side" : "single side");
        sVal = sVal + ((iVal & 2) != 0 ? ", double density" : ", single density");
        if ((iVal & 4) != 0) sVal = sVal + (", double track (96/135TPI)");
        if ((iVal & 8) != 0) sVal = sVal + (", quad track density (192TPI)");
        if ((iVal & 16) != 0) sVal = sVal + (", octal track density (384TPI)");
        logger.log(Level.TRACE, "OS9: dd_FMT: 0x%x (%s)".formatted(iVal, sVal));

        // tracks per side
        iVal = (basic.getFatEndGroup() + 1) / basic.getSectorsPerTrackOnBasic() / basic.getSidesPerDiskOnBasic();
        basic.setTracksPerSideOnBasic(iVal);

        // root directory
        int dirFdLsn = os9Ident.rootDirLen.getOs9Lsn();
        logger.log(Level.TRACE, "OS9: dd_DIR: LSN on Root Directory: %d".formatted(dirFdLsn));
        if (dirFdLsn > basic.getFatEndGroup()) {
            return -1.0;
        }
        sector = basic.getManagedSector(dirFdLsn);
        if (sector == null) {
            return -1.0;
        }
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);

        for (int i = 0; i < 48; i++) {
            int startLsn = fdd.segments[i].lsn.getOs9Lsn();
            int endLsn = fdd.segments[i].siz; // block size (in sectors)
            if (startLsn == 0 && endLsn == 0) {
                break;
            }
            endLsn += startLsn;

            if (i == 0) basic.setDirStartSector(startLsn);
            basic.setDirEndSector(endLsn);
        }

        // Allocation Map
        int mapLsn = os9Ident.mapLsn;
        int mapBytes = os9Ident.mapLen;
        logger.log(Level.TRACE, "OS9: dd_MapLSN: %d".formatted(mapLsn));
        if (!allocMap.allocMap(basic, mapLsn > 0 ? mapLsn : 1, mapBytes)) {
            return -1.0;
        }

        basic.setSectorsPerFat(mapBytes / basic.getSectorSize() + 1);

        return validRatio;
    }

    /** Get starting position of Allocation Map (for dialog) */
    @Override
    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int map_lsn = allocMap.getMapStartLSN();
        getNumFromSectorPos(map_lsn, trackNum, sideNum, sectorNum);
    }

    /** Get end position of Allocation Map (for dialog) */
    @Override
    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int mapLsn = allocMap.getMapStartLSN();
        mapLsn += (allocMap.getMapBytes() / basic.getSectorSize());
        getNumFromSectorPos(mapLsn, trackNum, sideNum, sectorNum);
    }

    /** Title name (for dialog) */
    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    /**
     * Check area
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 - 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double checkFat(boolean isFormatting) {
        return 1.0;
    }

    /**
     * Assign root directory
     *
     * @param startSector Start sector number
     * @param endSector   End sector number
     * @param groupItems  [out] Sector list
     * @param dirItem     [in,out] Root directory item
     */
    @Override
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryOs9> dirItem) throws IOException {
        boolean sts = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

        // Set pointer to FD sector in root item
        DiskBasicDirItemOS9 dItem = (DiskBasicDirItemOS9) dirItem;

        int dirFdLsn = os9Ident.rootDirLen.getOs9Lsn();
        dItem.setStartGroup(0, dirFdLsn, 0);
        DiskBasicDirItemOS9FD fd = dItem.getFd();
        DiskImageSector sector = basic.getManagedSector(dirFdLsn);
        if (sector == null) return false;
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), fdd);
        fd.set(basic, sector, dirFdLsn, fdd);

        return sts;
    }

    /**
     * Calculate sector list for root directory
     *
     * @param startSector Starting sector number of directory
     * @param endSector   End sector number of directory
     * @param groupItems  [out] Sector list
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;

        // root directory
        int dirFdLsn = os9Ident.rootDirLen.getOs9Lsn();
        DiskImageSector sector = basic.getManagedSector(dirFdLsn);
        if (sector == null) {
            return false;
        }
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);
        if (fdd == null) {
            return false;
        }

        groupItems.clear();

        int dirSize = 0;
        for (int i = 0; i < 48 && valid; i++) {
            int startLsn = fdd.segments[i].lsn.getOs9Lsn();
            int endLsn = fdd.segments[i].siz; // block size (in sectors)
            if (startLsn == 0 && endLsn == 0) {
                break;
            }
            endLsn += startLsn;

            for (int lsn = startLsn; lsn < endLsn; lsn++) {
                int[] trackNum = {0};
                int[] sideNum = {0};
                sector = basic.getSectorFromGroup(lsn, trackNum, sideNum);
                if (sector == null) {
                    valid = false;
                    break;
                }
                groupItems.add(lsn, 0, trackNum[0], sideNum[0], sector.getSectorNumber(), sector.getSectorNumber());
                dirSize += sector.getSectorSize();
            }
        }
        groupItems.setSize(dirSize);

        return valid;
    }

    /** Whether directory is empty */
    @Override
    public boolean isEmptyDirectory(boolean isRoot, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;
        boolean last = false;

        int indexNumber = 0;
        DiskBasicDirItem<DirectoryOs9> nitem = dir.newItem();
        for (int index = 0; index < groupItems.size(); index++) {
            DiskBasicGroupItem gItem = groupItems.get(index);
            int trackNum = gItem.track;
            int sideNum = gItem.side;
            DiskImageTrack track = basic.getTrack(trackNum, sideNum);
            if (track == null) {
                valid = false;
                break;
            }
            for (int sectorNum = gItem.sectorStart; sectorNum <= gItem.sectorEnd && valid && !last; sectorNum++) {
                DiskImageSector sector = track.getSector(sectorNum);
                //nItem.setSector(sector);
                if (sector == null) {
                    valid = false;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                int bufferOffset = 0;
                if (buffer == null) {
                    valid = false;
                    break;
                }

                int pos = 0;
                int size = sector.getSectorSize();

                // Check if there are no files in the directory
                while (valid && !last && pos < size) {
                    nitem.setData(indexNumber, gItem, sector, pos, buffer, bufferOffset, null);
                    // Examine FD sector
                    int startNsl = nitem.getStartGroup(0);
                    DiskImageSector fdSector = basic.getSectorFromGroup(startNsl);
                    if (fdSector != null) {
                        DirectoryOs9Fd fdBuf = new DirectoryOs9Fd();
                        byte[] b = fdSector.getSectorBuffer();
                        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdSector);
                        DiskBasicDirItemOS9FD fd = ((DiskBasicDirItemOS9) nitem).getFd();
                        fd.set(basic, fdSector, startNsl, fdBuf);
                        if (nitem.isNormalFile()) {
                            valid = !nitem.checkUsed(last);
                        }
                    }
                    pos += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    indexNumber++;
                }
            }
        }

        return valid;
    }

    /**
     * Whether to end assigning if directory area size is reached
     *
     * @param pos        [in,out] Position of directory
     * @param size       [in,out] Sector size of directory
     * @param sizeRemain [in,out] Remaining size of directory
     * @return 0: Do not end, 1: Force unused, continue assign
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        // If size is reached, subsequent ones are unused
        return sizeRemain[0] <= 0 ? 1 : 0;
    }

    /** Get usable disk size */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize();
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        // Examine Allocation Map
        allocMap.makeAvailable(fatAvailability);

        // Groups of directory entries
        List<DiskBasicDirItem<DirectoryOs9>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (DiskBasicDirItem<DirectoryOs9> item : items) {
                if (item == null || !item.isUsed()) continue;

                // Examine map of group numbers
                int groupCount = item.getGroupCount();
                if (groupCount > 0) {
                    DiskBasicGroupItem groupItem = item.getGroup(groupCount - 1);
                    int groupNum = groupItem.group;
                    if (groupNum <= basic.getFatEndGroup()) {
                        fatAvailability.set(groupNum, DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        //freeDiskSize = fSize;
        //freeGroups = groups;
    }

    /**
     * Set usage status
     *
     * @param num Group number (0...)
     * @param val 1: In use, 0: Make free
     */
    @Override
    public void setGroupNumber(int num, int val) {
        allocMap.setLSN(num, val != 0);
    }

    /** Get group number */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /**
     * Whether FAT position is used
     *
     * @param num Group number (0...)
     */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return allocMap.isUsedLSN(num);
    }

    /** Get next group number */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * Returns free position
     *
     * @return INVALID_GROUP_NUMBER: No free space
     */
    @Override
    public int getEmptyGroupNumber() {
        // Examine Allocation Map
        return allocMap.findEmpty();
    }

    /**
     * Returns next free position. Unused.
     *
     * @return INVALID_GROUP_NUMBER: No free space
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * Prepare before saving file
     *
     * @param iStream  Stream buffer
     * @param fileSize [in,out] Output size
     * @param pItem    [in,out] Directory item with file name and attributes
     * @param nItem    [in,out] Allocated directory item
     * @param errInfo  [in,out] Error information
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryOs9> pItem, DiskBasicDirItem<DirectoryOs9> nItem, DiskBasicError errInfo) throws IOException {
        // Allocate FD sector
        int lsn = getEmptyGroupNumber();
        if (lsn == INVALID_GROUP_NUMBER) {
            return false;
        }
        DiskImageSector sector = basic.getSectorFromGroup(lsn);
        if (sector == null) {
            return false;
        }
        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return false;
        }
        // Set FD sector
        nItem.setChainSector(sector, lsn, buf, pItem);

        // Set start LSN
        nItem.setStartGroup(0, lsn);

        // Reserve sector
        setGroupNumber(lsn, 1);

        return true;
    }

    /**
     * Allocate groups for data size
     *
     * @param fileUnitNum File number
     * @param item        [in,out] Directory item
     * @param dataSize    Data size to allocate (bytes)
     * @param flags       New or append
     * @param groupItems  [out] Sector list
     * @return >0: Normal, -1: No free space (before setting start group), -2: No free space (after setting start group)
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryOs9> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) {
        DiskBasicDirItemOS9 ditem = (DiskBasicDirItemOS9) item;
        DiskBasicDirItemOS9FD fd = ditem.getFd();

        int segmentIndex = -1;
        if (flags == ALLOCATE_GROUPS_APPEND) {
            // In case of addition, calculate existing segments
            segmentIndex = 48;
            for (int index = 0; index < 48; index++) {
                if (fd.getLsn(index) == 0 && fd.getSize(index) == 0) {
                    segmentIndex = index - 1;
                    break;
                }
            }
            if (segmentIndex >= 48) {
                // No free space in segments
                return -1;
            }
        }
        int startSegmentIndex = segmentIndex + 1;

        int fileSize = fd.getSize();
        dataSize += fileSize;

        // When creating new and dd_BIT is 2 or more
        boolean isFirstLsn = (flags == ALLOCATE_GROUPS_NEW && basic.getGroupWidth() > 1);

        // Allocate sectors for data
        int rc = 0;
        int lsn = 0;
        int prevLsn = 0;
        int segmentCount = 0;
        int limit = basic.getFatEndGroup() + 1;
        while (fileSize < dataSize && limit >= 0 && rc == 0) {
            int start = 0;
            if (isFirstLsn) {
                // When creating new and dd_BIT is 2 or more, write data from the free space of FD sector
                lsn = fd.getMyLSN() + 1;
                start++;
                isFirstLsn = false;
            } else {
                lsn = getEmptyGroupNumber();
            }
            if (lsn == INVALID_GROUP_NUMBER) {
                // No free space?
                rc = -2;
                break;
            }
            if (prevLsn == 0 || (prevLsn + 1) != lsn) {
                // LSN is not continuous
                segmentIndex++;
                if (segmentIndex >= 48) {
                    // Segment limit
                    rc = -2;
                    break;
                }
                fd.setLsn(segmentIndex, lsn);
                segmentCount = (basic.getGroupWidth() - start);
                fd.setSize(segmentIndex, segmentCount);
            } else {
                // If LSN is continuous, increase the number of sectors in the same segment
                segmentCount += basic.getGroupWidth();
                fd.setSize(segmentIndex, segmentCount);
            }
            // Reserve sector
            setGroupNumber(lsn, 1);
            // Add groups
            for (int i = start; i < basic.getGroupWidth(); i++) {
                basic.getNumsFromGroup(lsn, 0, basic.getSectorSize(), 0, groupItems[0]);
                lsn++;
            }
            // Hold LSN
            prevLsn = lsn - 1;

            if (fileSize + basic.getSectorSize() * (basic.getGroupWidth() - start) > dataSize) {
                fileSize = dataSize;
            } else {
                fileSize += basic.getSectorSize() * (basic.getGroupWidth() - start);
            }
            // File size
            fd.setSize(fileSize);

            limit--;
        }
        if (limit < 0) {
            rc = -2;
        }

        // In case of error, release allocated area
        if (rc < 0) {
            for (int index = startSegmentIndex; index < 48; index++) {
                int segmentLsn = fd.getLsn(index);
                int segmentSize = fd.getSize(index);
                if (segmentLsn == 0 && segmentSize == 0) {
                    break;
                }
                for (int size = 0; size < segmentSize; size++) {
                    // Make sector unused
                    if ((segmentLsn / basic.getGroupWidth()) != (fd.getMyLSN() / basic.getGroupWidth()))
                        setGroupNumber(segmentLsn, 0);
                    segmentLsn++;
                }
                fd.setLsn(index, 0);
                fd.setSize(index, 0);
            }
        }

        return rc;
    }

    /** Get sector number from group number */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    /** Get final sector number from group number */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return groupNum;
    }

    /** Calculate start sector of data area */
    @Override
    public int calcDataStartSectorPos() {
        return basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
    }

    /** Whether it is the root directory */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return ((groupNum + 1) <= basic.getDirStartSector());
    }

    /** Whether a subdirectory can be created */
    @Override
    public boolean canMakeDirectory() {
        return true;
    }

    /** Whether root directory size can be expanded */
    @Override
    public boolean canExpandRootDirectory() {
        return true;
    }

    /** Whether subdirectory size can be expanded */
    @Override
    public boolean canExpandDirectory() {
        return true;
    }

    /**
     * Prepare before creating a subdirectory
     *
     * @param item Allocated directory item
     */
    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem<DirectoryOs9> item) throws IOException {
        // Allocate FD sector
        int lsn = getEmptyGroupNumber();
        if (lsn == INVALID_GROUP_NUMBER) {
            return false;
        }
        DiskBasicDirItemOS9 dItem = (DiskBasicDirItemOS9) item;
        DiskImageSector sector = basic.getSectorFromGroup(lsn);

        DiskBasicDirItemOS9FD fd = dItem.getFd();
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);
        fd.set(basic, sector, lsn, fdd);
        fd.clear();

        // Set start LSN
        dItem.setStartGroup(0, lsn, 0);
        // Set date
        LocalDateTime tm = LocalDateTime.now();
        dItem.setFileCreateDateTime(tm);
        dItem.setFileModifyDateTime(tm);
        // Reserve sector
        setGroupNumber(lsn, 1);

        return true;
    }

    /** Individual processing after creating subdirectory */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryOs9> item, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryOs9> parentItem) throws IOException {
        if (groupItems.size() <= 0) return;

        // Directory attribute
        item.setFileAttr(basic.getFormatTypeNumber(), 0,
                FILETYPE_MASK_OS9_DIRECTORY |
                        FILETYPE_MASK_OS9_PUBLIC_EXEC |
                        FILETYPE_MASK_OS9_PUBLIC_WRITE |
                        FILETYPE_MASK_OS9_PUBLIC_READ |
                        FILETYPE_MASK_OS9_USER_EXEC |
                        FILETYPE_MASK_OS9_USER_WRITE |
                        FILETYPE_MASK_OS9_USER_READ
        );

        // File size is for two entries
        item.setFileSize(item.getDataSize() * 2);

        // Create entries for current and parent directory
        DiskBasicGroupItem grouItem = groupItems.get(0);

        DiskImageSector sector = basic.getTrack(grouItem.track, grouItem.side).getSector(grouItem.sectorStart); // Simplified access

        byte[] buf = sector.getSectorBuffer();
        int bufOffset = 0;
        DiskBasicDirItem<DirectoryOs9> newItem = basic.createDirItem(sector, 0, buf, bufOffset);

        // Create parent
        newItem.clearData();
        if (parentItem != null) {
            // Parent is subdirectory
            newItem.setStartGroup(0, parentItem.getStartGroup(0));
        } else {
            // Parent is root
            newItem.setStartGroup(0, os9Ident.rootDirLen.getOs9Lsn());
        }
        newItem.setFileNamePlain("..");
//        newItem.setFileAttr(FILE_TYPE_DIRECTORY_MASK);

        // Current
        bufOffset += newItem.getDataSize();
        newItem.setData(0, null, sector, 0, buf, bufOffset, null);

        newItem.clearData();
        newItem.setStartGroup(0, item.getStartGroup(0));
        newItem.setFileNamePlain(".");
//        newItem.setFileAttr(FILE_TYPE_DIRECTORY_MASK);

        // Update directory size
        int dirSize = dir.calcSize();
        DiskBasicDirItem<DirectoryOs9> dirItem = item.getParent();
        if (dirItem != null) {
            dirItem.setFileSize(dirSize);
        }
    }

    /** Fill sector data with specified code */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    /** Individual processing after filling sector data */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // Ident
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) return false;
        os9Ident = new Os9Ident();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), os9Ident);
        os9IdentSector = sector;

        //int totalLsn = basic.getFatEndGroup() + 1;
        int totalLsn = (basic.getTracksPerSide() - basic.getManagedTrackNumber()) * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
        // Final group number
        basic.setFatEndGroup(totalLsn - 1);

        int mapLsn = 1;
        int rootStartLsn = basic.getDirStartSector() - 1;
        int rootEndLsn = basic.getDirEndSector() - 1;
        int iVal;

        //
        // Set OS9 Identifier
        //

        sector.fill((byte) 0);

        // total lsn
        os9Ident.totalLsn.setOs9Lsn(totalLsn);
        // sectors per track
        os9Ident.sectorsPerTrack = (byte) basic.getSectorsPerTrackOnBasic();
        // allocation map length
        iVal = (totalLsn + 7) / 8;
        os9Ident.mapLen = (short) iVal;
        // sectors per group
        iVal = basic.getGroupWidth();
        os9Ident.bit = (short) iVal;
        // rootdir lsn
        os9Ident.rootDirLen.setOs9Lsn(rootStartLsn);
        // owner id
        os9Ident.owner = 0;
        // disk attr
        os9Ident.attr = 0;
        // disk ident
        os9Ident.disk = (short) 0;

        // format, density, number of sides
        iVal = ((basic.getSidesPerDiskOnBasic() - 1) & 0x01);
        iVal |= (basic.hasSingleDensity(null ,null) == 1 ? 0 : 0x02);
        os9Ident.format = (byte) iVal;

        // sector per track
        iVal = basic.getSectorsPerTrackOnBasic();
        os9Ident.sectorPerTrack = (short) iVal;

        // bootstrap lsn
        os9Ident.bootstrapLsn.setOs9Lsn(0);
        // bootstrap size (in bytes)
        os9Ident.bootstrapSize = (short) 0;

        // creation date
        LocalDateTime tm = LocalDateTime.now();
        os9Ident.cDate.yy = (byte) (tm.getYear() % 100);
        os9Ident.cDate.mm = (byte) (tm.getMonth().ordinal() + 1);
        os9Ident.cDate.dd = (byte) tm.getDayOfMonth();
        os9Ident.cDate.hh = (byte) tm.getHour();
        os9Ident.cDate.mi = (byte) tm.getMinute();

        // bitmap starting sector number
        os9Ident.mapLsn = mapLsn - 1;

        // volume label
        setIdentifiedData(data);

        // Specific parameter setting
        basic.assignParameter();

        //
        // Create Allocation Map
        //

        for (int lsn = mapLsn; lsn < rootStartLsn; lsn++) {
            sector = basic.getManagedSector(lsn);
            if (sector == null) return false;
            sector.fill((byte) 0xff);
        }
        for (int lsn = rootEndLsn + 1; lsn < totalLsn; lsn++) {
            setGroupNumber(lsn, 0);
        }

        //
        // Create root directory
        //

        sector = basic.getManagedSector(rootStartLsn);
        if (sector == null) return false;

        DiskBasicDirItemOS9 rootItem = (DiskBasicDirItemOS9) dir.newItem();
        DiskBasicDirItemOS9FD rootFd = rootItem.getFd();

        DirectoryOs9Fd fdd = new DirectoryOs9Fd(); // Mock
        rootFd.set(basic, sector, rootStartLsn, fdd);
        rootFd.clear();

        rootItem.setStartGroup(0, rootStartLsn, 0);
        // Set date
        rootItem.setFileCreateDateTime(tm);
        rootItem.setFileModifyDateTime(tm);
        // Segment setting
        rootFd.setLsn(0, rootStartLsn + 1);
        rootFd.setSize(0, rootEndLsn - rootStartLsn);
        // Number of links
        rootFd.setLinkCount((short) 2);
        // Reserve sector
        setGroupNumber(rootStartLsn, 1);

        for (int lsn = rootStartLsn + 1; lsn <= rootEndLsn; lsn++) {
            sector = basic.getManagedSector(lsn);
            if (sector == null) {
                continue;
            }
            sector.fill((byte) 0);
        }
        DiskBasicGroups groupItems = new DiskBasicGroups();
        basic.getNumsFromGroup(rootStartLsn + 1, 0, basic.getSectorSize(), basic.getSectorSize(), groupItems);
        additionalProcessOnMadeDirectory(rootItem, groupItems, null);

        return true;
    }

    /** Processing after data writing completion */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryOs9> item) {
        // Update directory size
        int dirSize = dir.calcSize();
        //DiskBasicDirItem dirItem = dir.findName(".", null, null);
        DiskBasicDirItem<DirectoryOs9> dirItem = item.getParent();
        if (dirItem == null) {
            // Why?
            return;
        }
        dirItem.setFileSize(dirSize);
    }

    /** Delete FAT area */
    @Override
    public void deleteGroupNumber(int groupNum) {
        // Make it unused
        setGroupNumber(groupNum, 0);
    }

    /** Processing after file deletion */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryOs9> item) {
        // Make FD sector unused
        setGroupNumber(item.getStartGroup(0), 0);

        // Update directory size
        int dirSize = dir.calcSize();
        //DiskBasicDirItem dirItem = dir.findName(".", null, null);
        DiskBasicDirItem<DirectoryOs9> dirItem = item.getParent();
        if (dirItem == null) {
            // Why?
            return true;
        }
        dirItem.setFileSize(dirSize);

        return true;
    }

    /** Get attributes of IPL and managed area */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume label
        byte[] buf = new byte[os9Ident.name.length + 1];
        DiskBasicDirItemOS9.decodeString(buf, os9Ident.name.length, os9Ident.name, os9Ident.name.length);
        String volume = new String(buf, 0, os9Ident.name.length);
        data.setVolumeName(volume);
        data.setVolumeNameMaxLength(os9Ident.name.length);
    }

    /** Set attributes of IPL and managed area */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        DiskBasicFormat format = basic.getFormatType();

        // volume label
        if (format.hasVolumeName()) {
            String vol = data.getVolumeName();
            DiskBasicDirItemOS9.encodeString(os9Ident.name, os9Ident.name.length, vol, vol.length());
        }
        writeIdent();
    }
}
