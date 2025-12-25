package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.DirectoryProDos;
import l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.ProDOSDirPointer;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_ACCESS_ALL;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_CHANGE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SAPLING;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SUBDIR;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SUBVOL;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_TREE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_VOLUME;


/**
 * Processing for Apple ProDos 8 / 16
 */
public class DiskBasicTypeProDOS extends DiskBasicType<DirectoryProDos> {

    private static final Logger logger = System.getLogger(DiskBasicTypeProDOS.class.getName());

    /**
     * Apple ProDos Bitmap
     */
    static class ProDosBitmap extends DiskBasicBitMLMap {

        int groupNum;

        public ProDosBitmap() {
            super();
            groupNum = INVALID_GROUP_NUMBER;
        }

        /**
         * Set pointer
         */
        public void addBitmap(DiskImageSector sector) {
            super.addBuffer(
                    sector.getSectorBuffer(),
                    sector.getSectorSize()
            );
        }

        /**
         * Change bit at specified position
         *
         * @param groupNum position
         * @param use      Set if {@code true}
         */
        @Override
        public void modify(int groupNum, boolean use) {
            super.modify(groupNum, !use); // Reverse
        }

        /**
         * Whether specified position is free
         *
         * @param group_num position
         * @return true if free
         */
        public boolean isFree(int group_num) {
            return super.isSet(group_num); // Reverse
        }


        /** Set block number */
        void setMyGroupNumber(int group_num) {
            this.groupNum = group_num;
        }

        /** Get block number */
        int getMyGroupNumber() {
            return groupNum;
        }
    }

    private final DiskBasicSectorSkew sectorSkew = new DiskBasicSectorSkew();
    private final ProDosBitmap bitmap = new ProDosBitmap();
    private DirectoryProDos volume;
    private final DiskBasicSectorPosTrans sectorMap = new DiskBasicSectorPosTrans();

    public static final int FORMAT_TYPE_PRODOS = 16;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_PRODOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryProDos> dir) {
        super.init(basic, fat, dir);
        this.volume = null;

        // In case of ProDOS 8, create sector -> block map
        if (basic.getTracksPerSideOnBasic() <= 40) {
            // ProDOS 8
            sectorSkew.create(basic, basic.getSectorsPerTrackOnBasic());
        }
    }

    /**
     * Check area
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 - 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double valid_ratio = 1.0;

        // Bitmap
        int groupNum = bitmap.getMyGroupNumber();
        int startPos = getStartSectorFromGroup(groupNum);
        int endPos = getEndSectorFromGroup(groupNum, INVALID_GROUP_NUMBER, startPos, 0, 0);
        for (int s = startPos; s <= endPos; s++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(s);
            if (sector == null) {
                return -1.0;
            }
            bitmap.addBitmap(sector);
        }
        basic.setSectorsPerFat(bitmap.size());

        return valid_ratio;
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 ~ 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0;

        double validRatio = 1.0;

        // Since sectors are variable, aggregate number of sectors per track
        sectorMap.create(basic);

        // Total number of sectors
        int calcTotalBlocks = sectorMap.getTotalSectors() / basic.getSectorsPerGroup();
        basic.setFatEndGroup(calcTotalBlocks - 1);

        // Volume directory
        DiskImageSector sector = basic.getSectorFromGroup(basic.getDirStartSector() / basic.getSectorsPerGroup());
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer(4);
        DirectoryProDos vol = new DirectoryProDos();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vol);
        if ((vol.aux.v.entryLen & 0xff) != DirectoryProDos.SIZE) {
            return -1.0;
        }

        int storeTotalBlocks = vol.aux.v.totalBlocks & 0xffff;

        if (storeTotalBlocks > calcTotalBlocks) {
            validRatio -= 0.5;
        }

        basic.setFatEndGroup(storeTotalBlocks - 1);

        bitmap.setMyGroupNumber(vol.aux.v.bitmapPointer & 0xffff);

        this.volume = vol; // Store reference

        return validRatio;
    }

    /**
     * Get starting position of Allocation Map (for dialog)
     */
    @Override
    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int groupNum = bitmap.getMyGroupNumber();
        int startPos = getStartSectorFromGroup(groupNum);
        getNumFromSectorPos(startPos, trackNum, sideNum, sectorNum, null, null);
    }

    /**
     * Get end position of Allocation Map (for dialog)
     */
    @Override
    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int groupNum = bitmap.getMyGroupNumber();
        int startPos = getStartSectorFromGroup(groupNum);
        int endPos = getEndSectorFromGroup(groupNum, INVALID_GROUP_NUMBER, startPos, 0, 0);
        getNumFromSectorPos(endPos, trackNum, sideNum, sectorNum, null, null);
    }

    /**
     * Title name (for dialog)
     */
    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    /**
     * Assign root directory
     */
    @Override
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryProDos> dirItem) throws IOException {
        boolean status = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

        // Copy content of volume header
        DiskBasicGroupItem gItem = groupItems.get(0);
        DiskImageSector sector = basic.getSector(gItem.track, gItem.side, gItem.sectorStart);
        byte[] volume = sector.getSectorBuffer(4);
        dirItem.copyData(volume);
        // Keep as directory attribute
        dirItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);
        // Set block number
        dirItem.setStartGroup(0, basic.getDirStartSector() / basic.getSectorsPerGroup());

        return status;
    }

    /**
     * Calculate sector list for root directory
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;

        groupItems.clear();

        // Follow the directory chain
        int dirSize = 0;
        int limit = basic.getDirEndSector() - basic.getDirStartSector() + 1;
        int[] trackNum = {0};
        int[] sideNum = {0};
        int sectorNum = 1;

        // Start sector
        int sectorPos = basic.getDirStartSector();
        //volDir.Empty();

        while (valid && limit >= 0) {
            ProDOSDirPointer next = new ProDOSDirPointer();
            next.nextBlock = 0;

            int groupNum = sectorPos / basic.getSectorsPerGroup();
            //volDir.Add((int) groupNum);

            for (int ss = 0; ss < basic.getSectorsPerGroup(); ss++) {
                DiskImageSector sector = basic.getSectorFromSectorPos(sectorPos, trackNum, sideNum);
                if (sector == null) {
                    valid = false;
                    break;
                }
                sectorNum = sector.getSectorNumber();
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    valid = false;
                    break;
                }
                if (ss == 0) {
                    // Hold pointer to next block
                    Serdes.Util.deserialize(new ByteArrayInputStream(buffer), next);
                }

                groupItems.add(groupNum, 0, trackNum[0], sideNum[0], sectorNum, sectorNum);

                dirSize += sector.getSectorSize();
                sectorPos++;
            }

            if (!valid) break;

            // No next sector
            if (next.nextBlock == 0) {
                break;
            }

            sectorPos = next.nextBlock * basic.getSectorsPerGroup();

            limit--;
        }
        groupItems.setSize(dirSize);

        if (limit < 0) {
            valid = false;
        }

        return valid;
    }

    /**
     * Whether to end assigning if directory area size is reached
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        // If size is reached, subsequent ones are unused
        int blockSize = basic.getSectorSize() * basic.getSectorsPerGroup();
        return ((sizeRemain[0] % blockSize) < DirectoryProDos.SIZE ? -1 : 0);
    }

    /**
     * Initialize sector as directory
     */
    @Override
    public int initializeSectorsAsDirectory(DiskBasicGroups groupItems, int[] fileSize, int[] sizeRemain, DiskBasicError errInfo) {
        fileSize[0] = groupItems.size() * basic.getSectorSize();
        sizeRemain[0] = 0;

        return 0;
    }

    /**
     * Get usable disk size
     */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * Calculate remaining disk size
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        //int fSize = 0;
        //int groups = 0;

        fatAvailability.clear();

        // BITMAP table
        for (int group = 0; group <= basic.getFatEndGroup(); group++) {
            if (group <= 2) {
                fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
            } else if (group == bitmap.getMyGroupNumber()) {
                fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
            } else if (bitmap.isFree(group)) {
                fatAvailability.add(FAT_AVAIL_FREE, basic.getSectorSize() * basic.getSectorsPerGroup(), 1);
            } else {
                fatAvailability.add(FAT_AVAIL_USED, 0, 0);
            }
        }
        // Volume directory
        DiskBasicDirItem<DirectoryProDos> root = dir.getRootItem();
        if (root != null) {
            DiskBasicGroups rootGroups = root.getGroups();
            if (rootGroups != null) {
                for (int i = 0; i < rootGroups.size(); i++) {
                    fatAvailability.set(i, FAT_AVAIL_SYSTEM);
                }
            }
        }

        //freeDiskSize = (int) fSize;
        //freeGroups = (int) groups;
    }

    /**
     * Mark group number as used
     */
    @Override
    public void setGroupNumber(int num, int val) {
        bitmap.modify(num, val != 0);
    }

    /**
     * Get group number
     */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /**
     * Whether FAT position is used
     */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /**
     * Get next group number
     */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * Returns free position
     */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;

        for (int group = 3; group <= basic.getFatEndGroup(); group++) {
            if (bitmap.isFree(group)) {
                newNum = group;
                break;
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
        return getEmptyGroupNumber();
    }

    /**
     * Prepare before saving file
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryProDos> pItem, DiskBasicDirItem<DirectoryProDos> nItem, DiskBasicError errInfo) throws IOException {
        // Clear chain sector
        nItem.clearChainSector(null);

        return true;
    }

    /**
     * Allocate chain sector
     */
    private int allocChainSector(int index, DiskBasicDirItem<DirectoryProDos> item) {
        int groupNum = getEmptyGroupNumber();
        if (groupNum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // Sector
        int startPos = getStartSectorFromGroup(groupNum);
        int ebdPos = getEndSectorFromGroup(groupNum, INVALID_GROUP_NUMBER, startPos, 0, 0);
        for (int sec = startPos; sec <= ebdPos; sec++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(sec);
            if (sector == null) {
                return INVALID_GROUP_NUMBER;
            }
            sector.fill((byte) 0);
        }

        // Set sector in chain information
        item.setChainSector(groupNum, startPos, null, null);

        // Set start group
        if (index == 0) {
            item.setStartGroup(0, groupNum, 1);
        }
        // Reserve sector
        setGroupNumber(groupNum, 1);

        return groupNum;
    }

    /**
     * Allocate groups for data size
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryProDos> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        //logger.log(Level.TRACE, "DiskBasicTypeProDOS::AllocateGroups {");

        //int fileSize = 0;
        int groups = 0;

        int rc = 0;
        int sectorSize = basic.getSectorSize();
        int blockSize = sectorSize * basic.getSectorsPerGroup();
        int remain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        int chainIndex = 0;

        DiskBasicFileType attr = item.getFileAttr();
        int sType = (attr.getOrigin() >> 16) & 0xff;
        if (sType == FILETYPE_MASK_PRODOS_SUBDIR) {
            // Subdirectory
            while (remain > 0 && limit >= 0) {
                // Find free space
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // No free space
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // Mark as used
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);

                if (chainIndex == 0 && flags != ALLOCATE_GROUPS_APPEND) {
                    item.setStartGroup(0, groupNum, 1);
                }
                chainIndex++;

                //file_size += blockSize;
                groups++;
                remain -= blockSize;
                limit--;
            }
            if (rc == 0 && flags == ALLOCATE_GROUPS_APPEND) {
                // For append, connect chain
                if (groupItems[0].size() > 0) {
                    rc = chainDirectoryGroups(item, groupItems);
                }
            }
        } else if (sType == FILETYPE_MASK_PRODOS_SAPLING) {
            // Less than 131K bytes, one index
            if (allocChainSector(0, item) == INVALID_GROUP_NUMBER) {
                return -1;
            }
            while (remain > 0 && limit >= 0) {
                // Find free space
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // No free space
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // Mark as used
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);

                // Update chain sector as well
                item.addChainGroupNumber(chainIndex, groupNum);

                chainIndex++;

                //			file_size += blockSize;
                groups++;
                remain -= blockSize;
                limit--;
            }
        } else if (sType == FILETYPE_MASK_PRODOS_TREE) {
            // 131K bytes or more, tree
            int chainPIndex = 0;
            chainIndex = 256;
            // Allocate chain sector
            if (allocChainSector(0, item) == INVALID_GROUP_NUMBER) {
                return -1;
            }
            chainPIndex++;
            while (remain > 0 && limit >= 0) {
                // Allocate chain sector
                if ((chainIndex % 256) == 0) {
                    int chainGroupNum = allocChainSector(chainPIndex, item);
                    if (chainGroupNum == INVALID_GROUP_NUMBER) {
                        return -1;
                    }
                    // Link with root chain sector
                    item.addChainGroupNumber(chainPIndex, chainGroupNum);
                    chainPIndex++;
                }
                // Find free space
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // No free space
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // Mark as used
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);

                // Update chain sector as well
                item.addChainGroupNumber(chainIndex, groupNum);

                chainIndex++;

                //fileSize += blockSize;
                groups++;
                remain -= blockSize;
                limit--;
            }
        } else {
            // 512 bytes or less
            while (remain > 0 && limit >= 0) {
                // Find free space
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // No free space
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // Mark as used
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);

                if (chainIndex == 0) {
                    item.setStartGroup(0, groupNum, 1);
                }
                chainIndex++;

                //fileSize += blockSize;
                groups++;
                remain -= blockSize;
                limit--;
            }
        }

        if (limit < 0) {
            // Infinite loop?
            rc = -2;
        }

        //logger.log(Level.TRACE, "rc: %d }".formatted(rc));
        return rc;
    }

    /**
     * Connect groups
     *
     * @return 0 Normal
     */
    public int chainDirectoryGroups(DiskBasicDirItem<DirectoryProDos> item, DiskBasicGroups[] groupItems) throws IOException {
        DiskBasicGroups originalGroupItems = new DiskBasicGroups();
        item.getAllGroups(originalGroupItems);
        originalGroupItems.add(groupItems[0]);
        groupItems[0] = originalGroupItems;

        // Recreate directory chain
        int groupNum = INVALID_GROUP_NUMBER;
        int prevGroupNum = INVALID_GROUP_NUMBER;
        ProDOSDirPointer prev = null;
        for (int i = 0; i < groupItems[0].size(); i++) {
            DiskBasicGroupItem gitem = groupItems[0].get(i);
            if (gitem.group != groupNum) {
                groupNum = gitem.group;
                DiskImageSector sector = basic.getSectorFromGroup(groupNum);
                ProDOSDirPointer curr = new ProDOSDirPointer();
                byte[] b = sector.getSectorBuffer();
                Serdes.Util.deserialize(new ByteArrayInputStream(b), curr);

                curr.prevBlock = prevGroupNum != INVALID_GROUP_NUMBER ? (short) prevGroupNum : 0;

                if (prev != null) {
                    prev.nextBlock = (short) groupNum;
                }

                prev = curr;
                prevGroupNum = groupNum;
            }
        }

        return 0;
    }

//    /** Data read/comparison processing */
//    public int accessFile(...) {}

    /**
     * Determine data size of the last sector of file
     */
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryProDos> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorSize, int remainSize) {
        return remainSize;
    }

    /**
     * Get sector number from group number
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum * basic.getSectorsPerGroup();
    }

    /**
     * Get final sector number from group number
     */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return (groupNum + 1) * basic.getSectorsPerGroup() - 1;
    }

    /**
     * Get track, side, sector numbers from sector position (serial number where track 0, side 0, sector 1 is 0)
     */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum /* = null */, int[] numOfDivs /* = null */) {
        //int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int[] sectorsPerTrack = {sidesPerDisk};

        // In which track is the sector position located?
        sectorMap.getNumFromSectorPos(sectorPos, trackNum, sectorNum, sectorsPerTrack);

        //if (selectedSide >= 0) {
        //    // 1S
        //	  trackNum[0] = sectorPos / sectorsPerTrack;
        //	  sideNum[0] = selected_side;
        //} else {
        //    // 2D, 2HD
        //	  trackNum[0] = sectorPos / sectorsPerTrack / sidesPerDisk;
        //	  sideNum[0] = (sectorPos / sectorsPerTrack) % sidesPerDisk;
        //}
        //sectorNum[0] = (sectorPos % sectorsPerTrack);

        // Side number
        sideNum[0] = sectorNum[0] * sidesPerDisk / sectorsPerTrack[0];

        // Case where it is not sequential numbering
        if (numberingSector != 1) {
            sectorNum[0] = sectorNum[0] % (sectorsPerTrack[0] / sidesPerDisk);
        }

        // Mapping
        sectorNum[0] = sectorSkew.toPhysical(sectorNum[0]);

        // Whether to reverse side number?
        sideNum[0] = basic.getReversedSideNumber(sideNum[0]);

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();

        if (divNum != null) divNum[0] = 0;
        if (numOfDivs != null) numOfDivs[0] = 1;
    }

    /**
     * Get track, sector numbers from logical sector position (serial number where track 0, side 0, sector 1 is 0)
     */
    @Override
    public void getNumFromSectorPosS(int sectorPos, int[] trackNum, int[] sectorNum) {
        int[] sectorsPerTrack = {1};

        sectorMap.getNumFromSectorPos(sectorPos, trackNum, sectorNum, sectorsPerTrack);

        // Mapping
        sectorNum[0] = sectorSkew.toPhysical(sectorNum[0]);

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();
    }

    /**
     * Get sector position (serial number where track 0, side 0, sector 1 is 0) from track, side, sector numbers
     */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        //int selectedSide = basic.getSelectedSide();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int numberingSector = basic.getNumberingSector();
        int[] sectorsPerTrack = {1};
        //int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        // Mapping
        sectorNum = sectorSkew.toLogical(sectorNum);

        // Whether to reverse side number?
        sideNum = basic.getReversedSideNumber(sideNum);

        int sectorPos = sectorMap.getSectorPosFromNum(trackNum, sectorNum, sectorsPerTrack);

        // Case where it is not sequential numbering
        if (numberingSector != 1) {
            sectorPos += sideNum * sectorsPerTrack[0] / sidesPerDisk;
        }

        //if (selectedSide >= 0) {
        //	  // 1S
        //	  sectorPos = trackNum * sectorsPerTrack + sectorNum;
        //} else {
        //	  // 2D, 2HD
        //	  sectorPos = trackNum * sectorsPerTrack * sidesPerDisk;
        //	  sectorPos += (sideNum % sidesPerDisk) * sectorsPerTrack;
        //	  sectorPos += sectorNum;
        //}
        return sectorPos;
    }

    /**
     * Get sector position (serial number where track 0, side 0, sector 1 is 0) from track, sector numbers
     */
    @Override
    public int getSectorPosFromNumS(int trackNum, int sectorNum) {
        //int selectedSide = basic.getSelectedSide();
        //int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        //int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        int[] sectorsPerTrack = {1};

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        // Mapping
        sectorNum = sectorSkew.toLogical(sectorNum);

        sectorPos = sectorMap.getSectorPosFromNum(trackNum, sectorNum, sectorsPerTrack);

        return sectorPos;
    }

    /**
     * Is it the root directory?
     */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return false;
    }

    /**
     * Edit directory name before creating subdirectory
     */
    @Override
    public boolean renameOnMakingDirectory(String[] dirName) {
        // Empty name cannot be created
        if (dirName[0].isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Individual processing after creating subdirectory
     */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryProDos> item, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryProDos> parentItem) throws IOException {
        if (groupItems.size() <= 0) return;

        int block_size = basic.getSectorSize() * basic.getSectorsPerGroup();

        DiskBasicDirItemProDOS dItem = (DiskBasicDirItemProDOS) item;
        DiskBasicDirItemProDOS parentDItem = (DiskBasicDirItemProDOS) parentItem;

        // Set first block of directory
        item.setParentGroup(parentItem.getStartGroup(0));
        // Align version with header
        dItem.setVersion(parentDItem.getVersion());

        // Create entry for sub-volume header

        DiskBasicGroupItem gItem = groupItems.get(0);

        DiskImageSector sector = basic.getSector(gItem.track, gItem.side, gItem.sectorStart);

        byte[] buf = sector.getSectorBuffer(4);
        DiskBasicDirItem<DirectoryProDos> newitem = basic.createDirItem(sector, 0, buf, 0);
        DiskBasicDirItemProDOS newditem = (DiskBasicDirItemProDOS) newitem;

        newitem.copyData(item.getRawData());
        newitem.setFileAttr(FORMAT_TYPE_PRODOS, 0, (FILETYPE_MASK_PRODOS_SUBVOL << 16) | (0x75 << 8) | (FILETYPE_MASK_PRODOS_ACCESS_ALL & ~FILETYPE_MASK_PRODOS_CHANGE));
        newitem.setStartGroup(0, 0);
        newitem.setFileSize(0);
        // Version
        newditem.setVersion(parentDItem.getVersion());

        byte[] b = sector.getSectorBuffer(4);
        DirectoryProDos vol = new DirectoryProDos();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vol);

        // Entry size
        vol.aux.sv.entryLen = DirectoryProDos.SIZE;
        // Number of file entries in block
        vol.aux.sv.entriesPerBlock = (byte) ((block_size - 4) / DirectoryProDos.SIZE);
        // Number of file entries
        vol.aux.sv.fileCount = 0;

        int parentStartBlock = parentItem.getStartGroup(0);
        int itemNumber = item.getNumber();
        int parentPointer = (itemNumber / (vol.aux.sv.entriesPerBlock & 0xff)) + parentStartBlock;
        int parentEntry = itemNumber % (vol.aux.sv.entriesPerBlock & 0xff);

        // Parent block number
        vol.aux.sv.parentPointer = (short) parentPointer;
        // Parent entry
        vol.aux.sv.parentEntry = (byte) parentEntry;

        // Parent entry size
        vol.aux.sv.parentEntryLen = DirectoryProDos.SIZE;
    }

//    /** Fill sector data with specified code during format */
//    public void fillSector(DiskImageTrack track, DiskImageSector sector)

    /**
     * Individual processing after filling sector data during formatting
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        // Clear volume directory
        int startPos = basic.getDirStartSector();
        int endPos = (basic.getDirEndSector() / basic.getSectorsPerGroup() + 1) * basic.getSectorsPerGroup() - 1;
        for (int s = startPos; s <= endPos; s++) {
            sector = basic.getSectorFromSectorPos(s);
            if (sector == null) {
                // Why?
                return false;
            }
            sector.fill((byte) 0);
        }

        // Create chain for volume directory
        int startBlock = startPos / basic.getSectorsPerGroup();
        int endBlock = endPos / basic.getSectorsPerGroup();
        //volDir.empty();
        for (int block = startBlock; block <= endBlock; block++) {
            sector = basic.getSectorFromGroup(block);
            ProDOSDirPointer p = new ProDOSDirPointer();
            byte[] b = sector.getSectorBuffer();
            try {
                Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
            } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
                // Why?
                return false;
            }
            int nextBlock = (block != endBlock ? block + 1 : 0);
            int prevBlock = (block != startBlock ? block - 1 : 0);
            p.nextBlock = (short) nextBlock;
            p.prevBlock = (short) prevBlock;
            //volDir.add(block);
        }

        int blockSize = basic.getSectorSize() * basic.getSectorsPerGroup();

        // Create volume header
        sector = basic.getSectorFromGroup(startBlock);
        DirectoryProDos vol = new DirectoryProDos();
        byte[] b = sector.getSectorBuffer(4);
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vol);
        // Attribute
        vol.sTypeAndNLen = (byte) (FILETYPE_MASK_PRODOS_VOLUME << 4);
        // Date and time
        LocalDateTime tm = LocalDateTime.now();
        DiskBasicDirItemProDOS.convDateFromTm(tm, vol.cDate);
        DiskBasicDirItemProDOS.convTimeFromTm(tm, vol.cTime);
        // Access
        vol.access = (byte) (FILETYPE_MASK_PRODOS_ACCESS_ALL & ~FILETYPE_MASK_PRODOS_CHANGE);
        // Entry size
        vol.aux.v.entryLen = (byte) DirectoryProDos.SIZE;
        // Number of file entries in block
        vol.aux.v.entriesPerBlock = (byte) ((blockSize - 4) / DirectoryProDos.SIZE);
        // Number of file entries
        vol.aux.v.fileCount = 0;
        // Bitmap pointer
        int bitmapPointer = 6;
        vol.aux.v.bitmapPointer = (short) bitmapPointer;
        // Total number of blocks
        int totalBlocks = (basic.getSidesPerDisk() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup());
        vol.aux.v.totalBlocks = (short) totalBlocks;

        this.volume = vol;

        basic.setFatEndGroup(totalBlocks - 1);

        // Set bitmap pointer
        bitmap.list.clear();
        startPos = getStartSectorFromGroup(bitmapPointer);
        endPos = getEndSectorFromGroup(bitmapPointer, INVALID_GROUP_NUMBER, startPos, 0, 0);
        for (int s = startPos; s <= endPos; s++) {
            sector = basic.getSectorFromSectorPos(s);
            if (sector == null) {
                // Why?
                return false;
            }
            sector.fill((byte) 0);
            bitmap.addBitmap(sector);
        }
        bitmap.setMyGroupNumber(bitmapPointer);
        for (int block = endBlock + 1; block < totalBlocks; block++) {
            bitmap.modify(block, false);
        }
        bitmap.modify(bitmapPointer, true);

        // volume name
        setIdentifiedData(data);

        return true;
    }

    /**
     * Data write process
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryProDos> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            // Few left
            if (remain < 0) remain = 0;
            if (remain > 0) {
                iStream.readNBytes(buffer, 0, remain);
            }
            if (size > remain) {
                // Remaining buffer is zero-suppressed
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // Continuous
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        return len;
    }

    /**
     * Processing after data write completion
     */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryProDos> item) {
        DiskBasicDirItemProDOS dItem = (DiskBasicDirItemProDOS) item;

        // Increment the number of files in the directory header by 1
        DiskBasicDirItem<DirectoryProDos> parent = item.getParent();
        if (parent == null) {
            // Why?
            return;
        }
        List<DiskBasicDirItem<DirectoryProDos>> children = parent.getChildren();
        if (children == null) {
            // Why?
            return;
        }
        DiskBasicDirItemProDOS vol = (DiskBasicDirItemProDOS) children.getFirst();
        if (vol == null) {
            // Why?
            return;
        }
        vol.increaseFileCount();
        // Set first block of directory
        item.setParentGroup(parent.getStartGroup(0));
        // Align version with header
        dItem.setVersion(vol.getVersion());
    }

    /**
     * Delete FAT area
     */
    @Override
    public void deleteGroupNumber(int groupNum) {
        // Mark as unused
        setGroupNumber(groupNum, 0);
    }

    /**
     * Processing after file deletion
     */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryProDos> item) throws IOException {
        // Mark chain sector as unused
        item.clearChainSector(null);

        // Decrement the number of files in the directory header by 1
        DiskBasicDirItem<DirectoryProDos> parent = item.getParent();
        if (parent == null) {
            // Why?
            return true;
        }
        List<DiskBasicDirItem<DirectoryProDos>> children = parent.getChildren();
        if (children == null) {
            // Why?
            return true;
        }
        DiskBasicDirItemProDOS vol = (DiskBasicDirItemProDOS) children.getFirst();
        if (vol == null) {
            // Why?
            return true;
        }
        vol.decreaseFileCount();

        return true;
    }

    /**
     * Get attributes of IPL and managed area
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume name
        if (volume != null) {
            int len = (volume.sTypeAndNLen & 0xf);
            String volumeName = new String(volume.name, 0, len);
            data.setVolumeName(volumeName);
            data.setVolumeNameMaxLength(volume.name.length);
        }
    }

    /**
     * Set attributes of IPL and managed area
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat format = basic.getFormatType();

        // volume name
        if (volume != null && format.hasVolumeName()) {
            byte[] volumeName = data.getVolumeName().getBytes();
            int len = volume.name.length;
            if (len > volumeName.length) len = volumeName.length;

            System.arraycopy(volumeName, 0, volume.name, 0, len);

            volume.sTypeAndNLen = (byte) ((len & 0xf) | (volume.sTypeAndNLen & 0xf0));
        }
    }
}