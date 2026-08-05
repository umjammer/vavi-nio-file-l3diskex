package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemC1541.C1541Pointer;
import l3diskex.basicfmt.diritem.DiskBasicDirItemC1541.DirectoryC1541;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;
import vavi.util.serdes.Serdes.Util;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;


/**
 * Processing for Commodore 1541
 * <p>
 * DiskBasicParam
 *
 * <li>SectorSkewForSave How to fill free sectors when importing files</li>
 */
public class DiskBasicTypeC1541 extends DiskBasicType<DirectoryC1541> {

    // C1541 attribute values
    public static final int FILETYPE_MASK_C1541_DEL = 0x80;
    public static final int FILETYPE_MASK_C1541_SEQ = 0x81;
    public static final int FILETYPE_MASK_C1541_PRG = 0x82;
    public static final int FILETYPE_MASK_C1541_USR = 0x83;
    public static final int FILETYPE_MASK_C1541_REL = 0x84;

    // C1541 attribute positions
    public static final int TYPE_NAME_C1541_DEL = 0;
    public static final int TYPE_NAME_C1541_SEQ = 1;
    public static final int TYPE_NAME_C1541_PRG = 2;
    public static final int TYPE_NAME_C1541_USR = 3;
    public static final int TYPE_NAME_C1541_REL = 3;

    public static final int C1541_START_TRACK_OFFSET = 0;
    public static final int C1541_START_SECTOR_OFFSET = 0;

    /**
     * C1541 BITMAP
     */
    static class C1541Map {

        /** free blocks */
        public byte remain;
        /** Little Endian: Byte0 LSB -> MSB -> byte 1 LSB -> MSB */
        public byte[] bits = new byte[3];
    }

    /** C1541 BAM */
    static class C1541Bam {

        public C1541Pointer startDir = new C1541Pointer();
        public byte formatType;
        public byte unused;
        public C1541Map[] map = new C1541Map[35];
        public byte[] diskName = new byte[18];
        public short diskId;
        public byte space0;
        public byte dosVersion;
        public byte dosFormat;
        public byte space1;
        public byte[] reserved = new byte[85];

        public C1541Bam() {
            for (int i = 0; i < map.length; i++) {
                map[i] = new C1541Map();
            }
        }
    }

    /**
     * C1541 side sector
     */
    @Serdes
    static class C1541SideSector {

        public static final int SIZE = 2 + 1 + 1 + 6 * 2 + 120 * 2;

        @Element(sequence = 1)
        public C1541Pointer next = new C1541Pointer();
        @Element(sequence = 2)
        public byte sideNum;
        @Element(sequence = 3)
        public byte recordLength;
        @Element(sequence = 4)
        public C1541Pointer[] sidePos = new C1541Pointer[6];
        @Element(sequence = 5)
        public C1541Pointer[] dataPos = new C1541Pointer[120];

        public C1541SideSector() { // TODO needed?
            for (int i = 0; i < sidePos.length; i++) {
                sidePos[i] = new C1541Pointer();
            }
            for (int i = 0; i < dataPos.length; i++) {
                dataPos[i] = new C1541Pointer();
            }
        }
    }

    /**
     * C1541 BAM bitmap
     */
    static class C1541Bitmap {

        private int myGroupNum;
        /** Block Availability Map */
        private C1541Bam bam;
        /** the sector {@link #bam} was read from, used to write it back */
        private DiskImageSector sector;

        public C1541Bitmap() {
            myGroupNum = 0;
            bam = null;
        }

        public void setBitmap(DiskImageSector sector, C1541Bam bam) {
            this.sector = sector;
            this.bam = bam;
        }

        /** Write the in memory BAM back onto the sector it came from */
        public void writeBack() throws IOException {
            if (sector == null || bam == null) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Util.serialize(bam, baos);
            sector.copy(baos.toByteArray(), baos.size());
        }

        public void setMyGroupNumber(int val) {
            myGroupNum = val;
        }

        public int getMyGroupNumber() {
            return myGroupNum;
        }

        /**
         * Change bit at specified position
         *
         * @param trackNum  Track number (0 ..)
         * @param sectorNum Sector number (0 ..)
         * @param use       Set if true
         */
        public void modify(int trackNum, int sectorNum, boolean use) throws IOException {
            int pos = sectorNum >> 3;
            int bit = sectorNum & 7;

            int mask = 1 << bit;
            int currentByte = bam.map[trackNum].bits[pos] & 0xFF;

            if (use) {
                bam.map[trackNum].bits[pos] = (byte) (currentByte & ~mask);
                bam.map[trackNum].remain--;
            } else {
                bam.map[trackNum].bits[pos] = (byte) (currentByte | mask);
                bam.map[trackNum].remain++;
            }
            writeBack();
        }

        /**
         * Whether specified position is free
         *
         * @param trackNum  Track number (0 ..)
         * @param sectorNum Sector number (0 ..)
         * @return true if free
         */
        public boolean isFree(int trackNum, int sectorNum) {
            int pos = sectorNum >> 3;
            int bit = sectorNum & 7;
            return (bam.map[trackNum].bits[pos] & (1 << bit)) != 0;
        }

        /**
         * Mark all sectors in specified track as unused
         * @param trackNum  Track number (0 ..)
         * @param numOfSector Number of sectors
         */
        public void freeTrack(int trackNum, int numOfSector) throws IOException {
            int val = (1 << numOfSector) - 1;
            for (int pos = 0; pos < 3; pos++) {
                bam.map[trackNum].bits[pos] = (byte) (val & 0xff);
                val >>= 8;
            }
            bam.map[trackNum].remain = (byte) numOfSector;
            writeBack();
        }

        /**
         * Returns disk name
         */
        public int getDiskName(byte[] buf, int len) {
            int copyLen = Math.min(len, bam.diskName.length);
            System.arraycopy(bam.diskName, 0, buf, 0, copyLen);
            return bam.diskName.length;
        }

        /**
         * Set disk name
         */
        public void setDiskName(byte[] buf, int len) throws IOException {
            int copyLen = Math.min(len, bam.diskName.length);
            System.arraycopy(buf, 0, bam.diskName, 0, copyLen);
            writeBack();
        }

        /**
         * Returns disk name size
         */
        public int getDiskNameSize() {
            return bam.diskName.length;
        }

        /**
         * Returns disk ID
         */
        public int getDiskId() {
            return bam.diskId & 0xffff;
        }

        /**
         * Set disk ID
         */
        public void setDiskId(int val) throws IOException {
            bam.diskId = (short) val;
            writeBack();
        }
    }

    /**
     * C1541 sector position conversion map list
     */
    static class C1541SectorPosTrans extends DiskBasicSectorPosTrans {

        @Override
        public void createSectorSkewMap(DiskBasic basic) {
            // Set method for searching free sectors during import
            for (int i = 0; i < size(); i++) {
                SectorsPerTrack item = get(i);
                DiskBasicSectorSkewForSave map = new DiskBasicSectorSkewForSave();
                map.create(basic, item.getNumOfSectors());
                item.setSectorSkewMap(map);
            }
        }
    }

    /** Block Availability Map */
    private final C1541Bitmap c1541Bam = new C1541Bitmap();
    /** Variable sector map */
    private final C1541SectorPosTrans sectorMap = new C1541SectorPosTrans();

    public static final int FORMAT_TYPE_C1541 = 20;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_C1541;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryC1541> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Check area
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = 1.0;

        return validRatio;
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0.0;

        double validRatio = 1.0;

        // Since it is variable sectors, aggregate the number of sectors per track
        sectorMap.create(basic);

        // Total number of sectors
        basic.setFatEndGroup(sectorMap.getTotalSectors() - 1);

        // Set how to find free sectors during import
        sectorMap.createSectorSkewMap(basic);

        // BAM
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            return -1.0;
        }
        C1541Bam bam = new C1541Bam();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), bam);

        // Check
        if (bam.space0 != (byte) 0xa0 || bam.space1 != (byte) 0xa0) {
            validRatio = 0.0;
        } else if (bam.dosVersion != '2' || bam.dosFormat != 'A') {
            validRatio = 0.5;
        }

        c1541Bam.setBitmap(sector, bam);

        int trackNum = basic.getManagedTrackNumber();
        int sectorNum = basic.getSectorNumberBase();
        int bamSectorPos = getSectorPosFromNumS(trackNum, sectorNum);
        c1541Bam.setMyGroupNumber(bamSectorPos);

        // Directory Area
        if ((bam.startDir.track & 0xff) >= (basic.getTracksPerSideOnBasic() + basic.getTrackNumberBaseOnDisk()) ||
                (bam.startDir.sector & 0xff) > basic.getSectorsPerTrack()) {
            return -1.0;
        }
        trackNum = (bam.startDir.track & 0xff) - C1541_START_TRACK_OFFSET;
        sectorNum = (bam.startDir.sector & 0xff) - C1541_START_SECTOR_OFFSET;

        int dirSectorNum = getSectorPosFromNumS(trackNum, sectorNum);

        // Directory start is relative to BAM sector
        dirSectorNum = dirSectorNum - bamSectorPos + basic.getSectorNumberBase();
        basic.setDirStartSector(dirSectorNum);

        basic.setSectorsPerFat(1);

        return validRatio;
    }

    /**
     * Calculate the sector list for the root directory
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;

        groupItems.clear();

        // Follow the directory chain
        int dirSize = 0;
        int limit = basic.getSectorsPerTrackOnBasic();
        int managedTrackNum = basic.getManagedTrackNumber();
        int[] trkNum = {0};
        int[] sidNum = {0};
        int secNum = 0;

        // Start sector
        int sectorPos = getSectorPosFromNumS(managedTrackNum, basic.getDirStartSector());

        while (valid && limit >= 0) {
            DiskImageSector sector = basic.getSectorFromSectorPos(sectorPos, trkNum, sidNum);
            if (sector == null) {
                valid = false;
                break;
            }
            secNum = sector.getSectorNumber();
            byte[] buffer = sector.getSectorBuffer();
            if (buffer == null) {
                valid = false;
                break;
            }
            int groupNum = sectorPos;
            groupItems.add(groupNum, 0, trkNum[0], sidNum[0], secNum, secNum);

            dirSize += sector.getSectorSize();

            // Next sector
            C1541Pointer next = new C1541Pointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(buffer), next);
            if ((next.track & 0xff) == 0 || (next.sector & 0xff) > basic.getTracksPerSideOnBasic()) {
                break;
            }

            sectorPos = getSectorPosFromNumS((next.track & 0xff) - C1541_START_TRACK_OFFSET, (next.sector & 0xff) - C1541_START_SECTOR_OFFSET);

            limit--;
        }
        groupItems.setSize(dirSize);

        if (limit < 0) {
            valid = false;
        }

        int staSectorNum = getSectorPosFromNumS(managedTrackNum, 1);
        int endSectorNum = getSectorPosFromNumS(trkNum[0], secNum);
        basic.setDirEndSector(endSectorNum - staSectorNum + 1);

        return valid;
    }

    /**
     * Initialize sector as directory
     */
    @Override
    public int initializeSectorsAsDirectory(DiskBasicGroups groupItems, int[] fileSize, int[] sizeRemain, DiskBasicError errInfo) {
        for (int i = 0; i < groupItems.getSize(); i++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(groupItems.get(i).group);
            sector.fill((byte) 0, sector.getSectorSize() - 2, 2);
        }

        fileSize[0] += groupItems.size() * (basic.getSectorSize() - 2);

        sizeRemain[0] = 0;
        return 0;
    }

    /**
     * Get usable disk size
     */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize();
    }

    /**
     * Calculate remaining disk size
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        // BITMAP table
        for (int secPos = 0; secPos <= basic.getFatEndGroup(); secPos++) {
            int[] trackNum = {0};
            int[] sectorNum = {0};
            getNumFromSectorPosS(secPos, trackNum, sectorNum);
            int trackANum = trackNum[0] - basic.getTrackNumberBaseOnDisk();
            int sectorANum = sectorNum[0] - basic.getSectorNumberBase();
            if (c1541Bam.isFree(trackANum, sectorANum)) {
                fatAvailability.add(FAT_AVAIL_FREE, basic.getSectorSize(), 1);
            } else {
                fatAvailability.add(FAT_AVAIL_USED, 0, 0);
            }
        }
        fatAvailability.set(c1541Bam.getMyGroupNumber(), FAT_AVAIL_SYSTEM);
        DiskBasicDirItem<DirectoryC1541> root = dir.getRootItem();
        if (root != null) {
            DiskBasicGroups basicGroups = root.getGroups();
            for (int i = 0; i < basicGroups.size(); i++) {
                fatAvailability.set(basicGroups.get(i).group, FAT_AVAIL_SYSTEM);
            }
        }
    }

    /**
     * Mark group number as used
     */
    @Override
    public void setGroupNumber(int num, int val) throws IOException {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        getNumFromSectorPosS(num, trackNum, sectorNum);
        int trackANum = trackNum[0] - basic.getTrackNumberBaseOnDisk();
        int sectorANum = sectorNum[0] - basic.getSectorNumberBase();
        c1541Bam.modify(trackANum, sectorANum, val != 0);
    }

    /** Get group number */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /** Whether FAT position is used */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /** Get next group number */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /** Returns free position */
    @Override
    public int getEmptyGroupNumber() {
        return getEmptyGroupNumberM(0);
    }

    private static final int[][] findTrackMap = {
            {0, 1, 2},
            {2, 0, 1}
    };

    /** Returns free position */
    private int getEmptyGroupNumberM(int method) {
        int newNum = INVALID_GROUP_NUMBER;

        int startTrack = 0;
        int endTrack = 0;
        int direction = 1;

        for (int n = 0; n < 2; n++) {
            int i = findTrackMap[method][n];
            switch (i) {
                case 0:
                    // Search from inside
                    startTrack = basic.getManagedTrackNumber() - 1;
                    endTrack = basic.getTrackNumberBaseOnDisk() - 1;
                    direction = -1;
                    break;
                case 1:
                    // Search outwards
                    startTrack = basic.getManagedTrackNumber() + 1;
                    endTrack = basic.getTracksPerSideOnBasic() + basic.getTrackNumberBaseOnDisk();
                    direction = 1;
                    break;
                case 2:
                    // Management track
                    startTrack = basic.getManagedTrackNumber();
                    endTrack = startTrack + 1;
                    direction = 1;
                    break;
            }

            for (int trackNum = startTrack; trackNum != endTrack && newNum == INVALID_GROUP_NUMBER; trackNum += direction) {
                int trackANum = trackNum - basic.getTrackNumberBaseOnDisk();
                SectorsPerTrack item = sectorMap.findByTrackNum(trackANum);
                int numOfSectors = item.getNumOfSectors();
                for (int secPos = 0; secPos < numOfSectors; secPos++) {
                    SectorSkewBase ss = item.getSectorSkewMap();
                    int sectorNum = ss.toPhysical(secPos);
                    int sectorANum = sectorNum - basic.getSectorNumberBase();
                    if (c1541Bam.isFree(trackANum, sectorANum)) {
                        newNum = getSectorPosFromNumS(trackNum, sectorNum);
                        break;
                    }
                }
            }
        }

        return newNum;
    }

    /**
     * Returns next free position
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        // Candidate for next free position
        int nextGroupNum = getEmptyGroupNumberM(0);
        if (nextGroupNum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // Set pointer to next sector in current sector
        if (chainGroups(currentGroup, nextGroupNum) < 0) {
            return INVALID_GROUP_NUMBER;
        }

        return nextGroupNum;
    }

    /**
     * Returns next empty FAT position
     */
    private int getDirNextEmptyGroupNumber(int currentGroup) {
        // Candidate for next free position
        int nextGroupNum = getEmptyGroupNumberM(1);
        if (nextGroupNum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // Set pointer to next sector in current sector
        if (chainGroups(currentGroup, nextGroupNum) < 0) {
            return INVALID_GROUP_NUMBER;
        }

        return nextGroupNum;
    }

    /**
     * Allocate groups for the data size
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryC1541> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        //int fileSize = 0;
        int groups = 0;

        int rc = 0;
        // Subtract 2 bytes per sector for link pointers used for chaining
        int bytesPerGroup = basic.getSectorSize() - 2;
        int remain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        int chainIndex = 0;
        int groupNum = INVALID_GROUP_NUMBER;
        if (flags == ALLOCATE_GROUPS_APPEND) {
            // During directory expansion
            remain = bytesPerGroup;
            groupNum = item.getGroups().last().group;
        }
        while (remain > 0 && limit >= 0) {
            // Search for free space
            if (flags != ALLOCATE_GROUPS_APPEND) {
                groupNum = (chainIndex == 0) ? getEmptyGroupNumber() : getNextEmptyGroupNumber(groupNum);
            } else {
                groupNum = getDirNextEmptyGroupNumber(groupNum);
            }

            if (groupNum == INVALID_GROUP_NUMBER) {
                // No free space
                rc = groups > 0 ? -2 : -1;
                return rc;
            }

            // Mark as used
            basic.getNumsFromGroup(groupNum, 0, basic.getSectorSize(), remain, groupItems[0]);
            setGroupNumber(groupNum, 1);

            if (flags != ALLOCATE_GROUPS_APPEND && chainIndex == 0) {
                item.setStartGroup(0, groupNum, 1);
            }
            chainIndex++;

            //fileSize += bytesPerGroup;
            groups++;
            remain -= bytesPerGroup;
            limit--;
        }

        if (groups > 0) {
            // Set remaining size for last sector
            remain += bytesPerGroup;
            chainLastGroup(groupNum, remain);
        }

        if (limit < 0) {
            // Infinite loop?
            rc = groups > 0 ? -2 : -1;
        }

        return rc;
    }

    /**
     * Chaining groups
     */
    @Override
    public int chainGroups(int groupNum, int appendGroupNum) {
        // Set pointer to next sector in current sector
        DiskImageSector sector = basic.getSectorFromSectorPos(groupNum);
        if (sector == null) {
            // why?
            return -1;
        }
        //C1541Pointer p = (C1541Pointer) sector.getSectorBuffer();
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            // why?
            return -1;
        }

        int[] nextTrackNum = {0};
        int[] nextSectorNum = {0};
        getNumFromSectorPosS(appendGroupNum, nextTrackNum, nextSectorNum);
        b[0] = (byte) (nextTrackNum[0] + C1541_START_TRACK_OFFSET);
        b[1] = (byte) (nextSectorNum[0] + C1541_START_SECTOR_OFFSET);

        return 0;
    }

    /**
     * Chaining last group
     */
    private int chainLastGroup(int groupNum, int remain) {
        // Set remaining size in current sector
        DiskImageSector sector = basic.getSectorFromSectorPos(groupNum);
        if (sector == null) {
            // why?
            return -1;
        }

        //C1541Pointer p = (C1541Pointer) sector.getSectorBuffer();
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            // why?
            return -1;
        }
        b[0] = 0; // track
        b[1] = (byte) (remain + 1); // sector
        return 0;
    }

    /**
     * Data read/comparison processing
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryC1541> item, InputStream iStream, OutputStream oStream,
                          byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        byte[] buf = Arrays.copyOfRange(sectorBuffer, 2, sectorSize);
        int size = (sectorSize - 2) < remainSize ? (sectorSize - 2) : remainSize;

        byte[] temp;
        if (oStream != null) {
            // Writing out
            temp = Arrays.copyOfRange(buf, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);
            oStream.write(temp, 0, temp.length);
        }

        if (iStream != null) {
            // Read and compare
            temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, temp.length, buf, 0, size)) {
                // Data is different
                return -1;
            }
        }
        return size;
    }

    /**
     * Determine data size of the last sector of file
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryC1541> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) {
        return remainSize;
    }

    /**
     * Get sector number from group number
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    /**
     * Get the final sector number from the group number
     */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return groupNum;
    }

    /**
     * Get track, side, sector numbers from sector position (serial number where track 0, side 0, sector 1 is 0)
     */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] numOfDivs) {
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int numberingSector = basic.getNumberingSector();
        int[] sectorsPerTrack = {basic.getSectorsPerTrackOnBasic()};

        // In which track is the sector position located?
        sectorMap.getNumFromSectorPos(sectorPos, trackNum, sectorNum, sectorsPerTrack);

        // Side number
        sideNum[0] = sectorNum[0] * sidesPerDisk / sectorsPerTrack[0];

        // Case where it is not sequential numbering
        if (numberingSector != 1) {
            sectorNum[0] = sectorNum[0] % (sectorsPerTrack[0] / sidesPerDisk);
        }

        // Whether to reverse side number?
        sideNum[0] = basic.getReversedSideNumber(sideNum[0]);

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();

        if (divNum != null) divNum[0] = 0;
        if (numOfDivs != null) numOfDivs[0] = 1;
    }

    /**
     * Get track and sector numbers from logical sector position (serial number where track 0, side 0, sector 1 is 0)
     */
    @Override
    public void getNumFromSectorPosS(int sectorPos, int[] trackNum, int[] sectorNum) {
        int[] sectorsPerTrack = {1};

        sectorMap.getNumFromSectorPos(sectorPos, trackNum, sectorNum, sectorsPerTrack);

        sectorNum[0] += basic.getSectorNumberBase();
        trackNum[0] += basic.getTrackNumberBaseOnDisk();
    }

    /**
     * Get sector position (serial number where track 0, side 0, sector 1 is 0) from track, side, sector numbers
     */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int numberingSector = basic.getNumberingSector();
        int[] sectorsPerTrack = {basic.getSectorsPerTrackOnBasic()};

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        int sector_pos = sectorMap.getSectorPosFromNum(trackNum, sectorNum, sectorsPerTrack);

        // Whether to reverse side number?
        sideNum = basic.getReversedSideNumber(sideNum);

        // Case where it is not sequential numbering
        if (numberingSector != 1) {
            sector_pos += sideNum * sectorsPerTrack[0] / sidesPerDisk;
        }

        return sector_pos;
    }

    /**
     * Get sector position (serial number where track 0, side 0, sector 1 is 0) from track and sector numbers
     */
    @Override
    public int getSectorPosFromNumS(int trackNum, int sectorNum) {
        int[] sectorsPerTrack = {1};

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        return sectorMap.getSectorPosFromNum(trackNum, sectorNum, sectorsPerTrack);
    }

    /**
     * Is it root directory? (The C++ method returns 'false')
     */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return false;
    }

    /**
     * Whether root directory size can be expanded
     */
    @Override
    public boolean canExpandRootDirectory() {
        return true;
    }

    /**
     * Specific processing after filling sector data during formatting
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        // Since sectors are variable, aggregate number of sectors per track
        sectorMap.create(basic);

        // Total number of sectors
        basic.setFatEndGroup(sectorMap.getTotalSectors() - 1);

        // Set how to find free sectors during import
        sectorMap.createSectorSkewMap(basic);

        // BAM creation
        int trackNum = basic.getManagedTrackNumber();
        int sectorNum = basic.getSectorNumberBase() - C1541_START_SECTOR_OFFSET;
        sector = basic.getSector(trackNum, sectorNum, null);
        if (sector == null) {
            // Why?
            return false;
        }
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            // Why?
            return false;
        }
        C1541Bam bam = new C1541Bam();
        Util.deserialize(new ByteArrayInputStream(b), bam);
        sector.fill((byte) 0);

        c1541Bam.setBitmap(sector, bam);
        int sectorPos = getSectorPosFromNumS(trackNum, sectorNum);
        c1541Bam.setMyGroupNumber(sectorPos);

        // directory
        bam.startDir.track = (byte) (trackNum + C1541_START_TRACK_OFFSET);
        bam.startDir.sector = (byte) (sectorNum + 1 + C1541_START_SECTOR_OFFSET);

        bam.formatType = 'A';	// 4040format
        bam.diskId = 0x3030;	// "00"
        bam.space0 = (byte) 0xa0;
        bam.dosVersion = '2';
        bam.dosFormat = 'A';
        bam.space1 = (byte) 0xa0;

        c1541Bam.writeBack();

        // Bitmap clear
        for (int track = 0; track < basic.getTracksPerSide(); track++) {
            SectorsPerTrack item = sectorMap.findByTrackNum(track);
            if (item != null) {
                c1541Bam.freeTrack(track, item.getNumOfSectors());
            }
        }
        int trackNPos = trackNum - basic.getTrackNumberBaseOnDisk();
        int sectorNPos = sectorNum - basic.getSectorNumberBase();
        c1541Bam.modify(trackNPos, sectorNPos, true);
        c1541Bam.modify(trackNPos, sectorNPos + 1, true);

        // Directory
        sectorNum++;
        sector = basic.getSector(trackNum, sectorNum, null);
        if (sector == null) {
            // Why?
            return false;
        }
        b = sector.getSectorBuffer();
        if (b == null) {
            // Why?
            return false;
        }
        //C1541Pointer next = (C1541Pointer) sector.getSectorBuffer();
        sector.fill((byte) 0);
        b[1] = (byte) 0xff; // next.sector

        basic.setDirStartSector(sectorNum);

        // volume name
        setIdentifiedData(data);

        return true;
    }

    /**
     * Data write process
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryC1541> item, InputStream iStream, byte[] buffer, int size, int remain,
                         int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;

        // From the second byte of the sector
        int buf_offset = 2;
        size -= 2;

        if (remain <= size) {
            // Few left
            if (remain < 0) remain = 0;
            if (remain > 0) iStream.readNBytes(buffer, buf_offset, remain);
            if (size > remain) {
                // Remaining buffer is zero-suppressed
                Arrays.fill(buffer, buf_offset + remain, buf_offset + size, (byte) 0);
            }
            len = remain;
        } else {
            // Continuous
            iStream.readNBytes(buffer, buf_offset, size);
            len = size;
        }

        return len;
    }

    /**
     * Post-processing after data writing
     */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryC1541> item) throws IOException {
        // Whether it is a REL file
        int type1 = item.getFileAttr().getOrigin();
        int rec_len = (type1 >> 8);
        type1 &= 0xff;
        if (type1 != FILETYPE_MASK_C1541_REL || rec_len == 0) {
            return;
        }

        // Create side sector if it is a REL file
        int bytesPerGroup = basic.getSectorSize() - 2;
        DiskBasicGroups dataGroups = item.getGroups();

        int blocks = dataGroups.size();
        int ssMax = blocks / 120;
        if (ssMax >= 6) {
            return;
        }

        DiskBasicGroups sideGroups = new DiskBasicGroups();
        int groupNum = INVALID_GROUP_NUMBER;
        int ssSize = 0;
        int dataPos = 0;
        C1541SideSector[] sideSectors = new C1541SideSector[6];
        for (int ssIndex = 0; ssIndex <= ssMax; ssIndex++) {
            // Find free space
            groupNum = (ssIndex == 0) ? getEmptyGroupNumber() : getNextEmptyGroupNumber(groupNum);
            if (groupNum == INVALID_GROUP_NUMBER) {
                // No free space
                return;
            }
            int[] trackNum = {0};
            int[] sideNum = {0};
            DiskImageSector sector = basic.getSectorFromSectorPos(groupNum, trackNum, sideNum);
            if (sector == null) {
                return;
            }

            byte[] b = sector.getSectorBuffer();
            if (b == null) {
                return;
            }
            C1541SideSector sideSector = new C1541SideSector();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), sideSector);
            sideSectors[ssIndex] = sideSector;

            sector.fill((byte) 0);
            setGroupNumber(groupNum, 1);
            sideGroups.add(groupNum, 0, trackNum[0], sideNum[0], sector.getSectorNumber(), sector.getSectorNumber());
            ssSize += bytesPerGroup;
            if (ssIndex == 0) {
                // Set side sector start pointer
                item.setExtraGroup(groupNum);
            } else {
                // Copy pointer to side sector
                System.arraycopy(sideSectors[ssIndex - 1].sidePos, 0, sideSectors[ssIndex].sidePos, 0, sideSectors[ssIndex].sidePos.length);
            }

            sideSector.sideNum = (byte) (ssIndex & 0xff);
            sideSector.recordLength = (byte) (rec_len & 0xff);

            int[] sectorNum = {0};
            getNumFromSectorPosS(groupNum, trackNum, sectorNum);
            trackNum[0] += C1541_START_TRACK_OFFSET;
            sectorNum[0] += C1541_START_SECTOR_OFFSET;

            // Set pointer to side sector
            for (int i = 0; i <= ssIndex; i++) {
                C1541SideSector ss = sideSectors[i];
                ss.sidePos[ssIndex].track = (byte) (trackNum[0] & 0xff);
                ss.sidePos[ssIndex].sector = (byte) (sectorNum[0] & 0xff);
            }

            // Set pointer to data
            for (int i = 0; i < 120 && dataPos < blocks; i++) {
                getNumFromSectorPosS(dataGroups.get(dataPos).group, trackNum, sectorNum);
                trackNum[0] += C1541_START_TRACK_OFFSET;
                sectorNum[0] += C1541_START_SECTOR_OFFSET;

                sideSector.dataPos[i].track = (byte) (trackNum[0] & 0xff);
                sideSector.dataPos[i].sector = (byte) (sectorNum[0] & 0xff);

                dataPos++;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Serdes.Util.serialize(sideSector, baos);
            sector.copy(baos.toByteArray(), C1541SideSector.SIZE);
        }

        // Set remaining size for last sector
        chainLastGroup(groupNum, (blocks % 120) * 2 + 14);

        // Set block size
        ssSize = ssSize - bytesPerGroup + (blocks % 120) * 2 + 14;
        sideGroups.setSize(ssSize);
        sideGroups.setNums(ssMax + 1);
        sideGroups.setSizePerGroup(basic.getSectorSize());
        item.setExtraGroups(sideGroups);
    }

    /**
     * Delete FAT area
     */
    @Override
    public void deleteGroupNumber(int group_num) throws IOException {
        // Mark as unused
        setGroupNumber(group_num, 0);
    }

    /**
     * Post-processing after file deletion
     */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryC1541> item) throws IOException {
        // Mark side sector as unused
        DiskBasicGroups[] groups = new DiskBasicGroups[1];
        item.getExtraGroups(groups);

        deleteGroups(groups[0]);

        return true;
    }

    /**
     * Get attributes of IPL and managed area
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume name
        byte[] name = new byte[c1541Bam.getDiskNameSize() + 1];
        Arrays.fill(name, (byte) 0);
        int len = c1541Bam.getDiskName(name, name.length);

        Common.trimRight(name, name.length, basic.getDirSpaceCode());

        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(name, 0, len, sb, -1);
        String wName = sb.toString();
        data.setVolumeName(wName);
        data.setVolumeNameMaxLength(len);

        // volume id
        data.setVolumeNumber(c1541Bam.getDiskId());
        data.volumeNumberIsHexa(true);
    }

    /**
     * Set attributes of IPL and managed area
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        DiskBasicFormat format = basic.getFormatType();

        // volume name
        if (format.hasVolumeName()) {
            byte[] name = new byte[c1541Bam.getDiskNameSize() + 1];
            basic.getCharCodes().convToChars(data.getVolumeName(), name, name.length);
            Common.padding(name, name.length, basic.getDirSpaceCode());
            c1541Bam.setDiskName(name, c1541Bam.getDiskNameSize());
        }
        // volume id
        if (format.hasVolumeNumber()) {
            c1541Bam.setDiskId(data.getVolumeNumber());
        }
    }
}
