package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.DirectoryCpm;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.SECTOR_UNIT_CPM;


/**
 * CP/M processing
 *
 * <li>AttributesByExtension Extensions to be treated as binary</li>
 */
public class DiskBasicTypeCPM extends DiskBasicType<DirectoryCpm> {

    public static final int FORMAT_TYPE_CPM = 10;

    /** Soft sector skew */
    protected DiskBasicSectorSkew sectorSkew = new DiskBasicSectorSkew();

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_CPM;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryCpm> dir) {
        super.init(basic, fat, dir);

        sectorSkew.create(basic, basic.getSectorsPerTrackOnBasic());
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 ~ 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        // Final group number
        if (basic.getFatEndGroup() == 0) {
            int maxGroup = (basic.getTracksPerSide() - basic.getManagedTrackNumber()) * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup() - 1;
            basic.setFatEndGroup(maxGroup);
        }

        if (isFormatting) return 1.0;

        // Does the final group number not exceed the maximum value?
        if ((1L << (basic.getGroupWidth() * 8)) <= basic.getFatEndGroup()) {
            return -1.0;
        }

        // Sector 0
        DiskImageSector sector = basic.getSector(0, 0, 1);
        if (sector == null) {
            return -1.0;
        }

        // If there is an identification character in the first sector, judge whether that string is included
        double validRatio = 0.5;
        int found = -1;
        byte[] iStr = null;
        for (int i = 0; i < 1; i++) {
            found = -1;
            String paramStr = switch (i) {
                case 0 -> basic.getVariousStringParam("IdentString");
                case 1 -> basic.getVariousStringParam("IPLString");
                default -> null;
            };
            if (paramStr != null && !paramStr.isEmpty()) {
                iStr = paramStr.getBytes(StandardCharsets.ISO_8859_1);
            }
            if (iStr != null && iStr.length > 0) {
                found = sector.find(iStr, iStr.length);
            }
            if (found >= 0) {
                validRatio = 1.0;
                break;
            }
        }

        return validRatio;
    }

    /**
     * Check area
     */
    @Override
    public double checkFat(boolean is_formatting) {
        return 1.0;
    }

    /**
     * Assign root directory
     */
    @Override
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryCpm> dirItem) throws IOException {
        boolean sts = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

        // Associate extents with the same file name
        List<DiskBasicDirItem<DirectoryCpm>> sortItems = dirItem.getChildren();
        sortItems.sort(DiskBasicDirItemCPM::compare);
        DiskBasicDirItem<DirectoryCpm> prevItem = null;
        for (DiskBasicDirItem<DirectoryCpm> item : sortItems) {
            if (!item.isUsed()) continue;
            if (prevItem != null) {
                int cmparison = DiskBasicDirItemCPM.compareName(item, prevItem);
                if (cmparison == 0) {
                    // If same name as the previous one, set pointer
                    ((DiskBasicDirItemCPM) prevItem).setNextItem(item);
                    // Do not show this item in the list
                    item.visible(false);
                }
            }
            prevItem = item;
        }

        // Calculate file size
        for (DiskBasicDirItem<DirectoryCpm> sortItem : sortItems) {
            DiskBasicDirItemCPM cItem = (DiskBasicDirItemCPM) sortItem;
            if (cItem.isUsedAndVisible()) {
                cItem.calcFileUnitSize(0);
            }
        }

        return sts;
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
        fatAvailability.empty();
//        fatAvailability.SetCount(basic.getFatEndGroup() + 1, FAT_AVAIL_FREE);

        List<DiskBasicDirItem<DirectoryCpm>> items = dir.getCurrentItems(null);
        for (DiskBasicDirItem<DirectoryCpm> item : items) {
            if (item == null || !item.isUsed()) continue;

            // Examine map of group numbers
            DiskBasicGroups groups = item.getGroups();
            int count = groups.size();
            for (int n = 0; n < count; n++) {
                DiskBasicGroupItem group = groups.get(n);
                int groupNum = group.group;
                if (groupNum <= basic.getFatEndGroup()) {
                    if (n + 1 == count) {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED_LAST);
                    } else {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED);
                    }
                }
            }
        }

        // Check free space
        int groups = 0;
        long dirArea = ((basic.getDirEndSector() - basic.getDirStartSector() + 1L) / basic.getSectorsPerGroup());
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (pos < dirArea) {
                // Directory area is in use
                fatAvailability.set(pos, FAT_AVAIL_SYSTEM);
            } else if (fatAvailability.get(pos) == FAT_AVAIL_FREE) {
                groups++;
            }
        }

        int fSize = groups * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(fSize);
        fatAvailability.setFreeGroups(groups);
    }

    /**
     * Set FAT position
     */
    @Override
    public void setGroupNumber(int num, int val) {
        fatAvailability.set(num, val != 0 ? FAT_AVAIL_USED : FAT_AVAIL_FREE);
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
        int groupNum = INVALID_GROUP_NUMBER;
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (fatAvailability.get(pos) == FAT_AVAIL_FREE) {
                groupNum = pos;
                break;
            }
        }
        return groupNum;
    }

    /**
     * Returns next free position. Unused.
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * Allocate groups for data size
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryCpm> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int rc = 0;

        DiskBasicDirItemCPM dItem = (DiskBasicDirItemCPM) item;
        int groupEntries = dItem.getGroupEntries();

        int startGroupPos = 0;
        if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
            // Search for free entry during append
            while (dItem.getGroupNumber(startGroupPos) != 0) {
                startGroupPos++;
                if ((startGroupPos % groupEntries) == 0) {
                        // Move to next directory entry if group entry count is reached
                    dItem = dItem.getNextItem();
                    if (dItem == null) {
                        // No next one
                        break;
                    }
                }
            }
        }
        if (dItem == null) {
            return -1;
        }

        int groupPos = startGroupPos;
        int groupSize = basic.getSectorSize() * basic.getSectorsPerGroup();
        int remainSize = dataSize;
        int fileSize = 0;
        int limit = basic.getFatEndGroup() + 1;
        while (remainSize > 0 && limit >= 0 && rc == 0) {
            int groupNum = getEmptyGroupNumber();
            if (groupNum == INVALID_GROUP_NUMBER) {
                rc = -2;
                break;
            }
            basic.getNumsFromGroup(groupNum, 0, basic.getSectorSize(), remainSize, groupItems[0]);
            // Mark as used
            setGroupNumber(groupNum, 1);
            // Group entry
            dItem.setGroupNumber((groupPos % groupEntries), groupNum);

            fileSize += (remainSize < groupSize ? remainSize : groupSize);
            // Set extent number and record number
            dItem.calcExtentAndRecordNumber(fileSize);

            remainSize -= groupSize;
            limit--;

            groupPos++;
            if (remainSize > 0 && (groupPos % groupEntries) == 0) {
                    // Move to next directory entry if group entry count is reached
                dItem = dItem.getNextItem();
                if (dItem == null) {
                    // No next one!?
                    rc = -2;
                    break;
                }
            }
        }
        if (limit < 0) {
            rc = -2;
        }
        if (rc < 0) {
            deleteGroups(groupItems[0]);
            // Delete group entries
            dItem = (DiskBasicDirItemCPM) item;
            groupPos = 0;
            while (dItem.getGroupNumber(groupPos) != 0) {
                if (groupPos >= startGroupPos) {
                    dItem.setGroupNumber(groupPos, 0);
                }
                groupPos++;
                if ((groupPos % groupEntries) == 0) {
                        // Move to next directory entry if group entry count is reached
                    dItem = dItem.getNextItem();
                    if (dItem == null) {
                        break;
                    }
                }
            }
        }
        return rc;
    }

    /**
     * Determine data size of the last sector of file
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryCpm> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) throws IOException {
        // File size is sector size boundary, so calculation is required
        if (item.needCheckEofCode()) {
            // Output up to one byte before the termination code
            byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
            for (int len = 0; len < remainSize; len++) {
                if (sectorBuffer[len] == eofCode) {
                    remainSize = len;
                    break;
                }
            }
        } else {
            // No calculation method, so return remaining size as is
            if (iStream != null) {
                // When comparing, use target file size
                int streamLength = iStream.available() % sectorSize;
            }
        }
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
        int val = sectorStart;
        if (remainSize < (sectorSize * basic.getSectorsPerGroup())) {
            val += ((remainSize - 1) / sectorSize);
        } else {
            val += (basic.getSectorsPerGroup() - 1);
        }
        return val;
    }

    /**
     * Calculate start sector of data area
     */
    @Override
    public int calcDataStartSectorPos() {
        return getSectorPosFromNumS(basic.getManagedTrackNumber(), basic.getDirStartSector());
    }

    /**
     * Get track, side, sector numbers from sector position (serial number where track 0, side 0, sector 1 is 0)
     */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] numOfDivs) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();

        if (selectedSide >= 0) {
            // 1S
            trackNum[0] = sectorPos / sectorsPerTrack;
            sideNum[0] = selectedSide;
        } else {
            // 2D, 2HD
            trackNum[0] = sectorPos / sectorsPerTrack / sidesPerDisk;
            sideNum[0] = (sectorPos / sectorsPerTrack) % sidesPerDisk;
        }
        sectorNum[0] = sectorPos % sectorsPerTrack;

        // Mapping
        sectorNum[0] = sectorSkew.toPhysical(sectorNum[0]);

        if (numberingSector == 1) {
            // Case where it is sequential numbering per track
            sectorNum[0] += sideNum[0] * sectorsPerTrack;
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
     * Get sector position (serial number where track 0, side 0, sector 1 is 0) from track, side, sector numbers
     */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        // Whether to reverse side number?
        sideNum = basic.getReversedSideNumber(sideNum);

        // In case of sequential numbering
        if (numberingSector == 1) {
            sectorNum = sectorNum % sectorsPerTrack;
        }

        // Mapping
        sectorNum = sectorSkew.toLogical(sectorNum);

        if (selectedSide >= 0) {
            // 1S
            sectorPos = trackNum * sectorsPerTrack + sectorNum;
        } else {
            // 2D, 2HD
            sectorPos = trackNum * sectorsPerTrack * sidesPerDisk;
            sectorPos += (sideNum % sidesPerDisk) * sectorsPerTrack;
            sectorPos += sectorNum;
        }
        return sectorPos;
    }

    /**
     * Is it root directory?
     */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return true;
    }

    /**
     * Whether a subdirectory can be created
     */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /**
     * Fill sector data with specified code
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    /**
     * Individual processing after filling sector data
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // Directory area
        for (int sectorPos = basic.getDirStartSector(); sectorPos <= basic.getDirEndSector(); sectorPos++) {
            DiskImageSector sector = basic.getManagedSector(sectorPos - 1);
            if (sector != null) {
                sector.fill(basic.getFillCodeOnDir());
            }
        }
        return true;
    }

    /**
     * Prepare before saving file
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryCpm> pItem, DiskBasicDirItem<DirectoryCpm> nItem, DiskBasicError errInfo) throws IOException {
        DiskBasicDirItemCPM dItem = (DiskBasicDirItemCPM) nItem;
        // Number of group entries
        int groupEntries = dItem.getGroupEntries();
        // Determine the file size that can be set in one directory (32K)
        int limitSize = basic.getSectorSize() * basic.getSectorsPerGroup() * groupEntries;

        dItem.used(true);
        dItem.visible(true);

        int remainSize = iStream.available(); // TODO assume available as length

        DiskBasicDirItemCPM prevAItem = dItem;
        DiskBasicDirItemCPM aItem = null;
        while (limitSize < remainSize) {
            // Cannot fit in one directory, so allocate additional directory entry
            aItem = (DiskBasicDirItemCPM) dir.getEmptyItemOnCurrent(pItem, null);
            if (aItem == null) {
                errInfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
                return false;
            }
            aItem.copyData(prevAItem.getRawData());
            aItem.used(true);
            aItem.visible(false);

            prevAItem.setNextItem(aItem);

            remainSize -= limitSize;

            prevAItem = aItem;
        }

        return true;
    }

    /**
     * Data write process
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryCpm> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needEofCode = item.needCheckEofCode();

        int len = 0;
        if (remain <= size) {
            // Few left
            int term = 0;
            if (remain < 0) remain = 0;
            if (remain > 0) iStream.readNBytes(buffer, 0, remain);
            if (needEofCode && ((remain % SECTOR_UNIT_CPM) != 0)) {
                // For ASCII, suppress termination code
                term = basic.getTextTerminateCode();
            }
            // Round remainder to 128 bytes
            int size128 = ((remain + SECTOR_UNIT_CPM - 1) / SECTOR_UNIT_CPM) * SECTOR_UNIT_CPM;
            if (remain < size128) {
                // Suppress buffer remainder (up to 128-byte boundary)
                // Use byte array fill with the byte value of term
                byte termByte = (byte) term;
                for (int i = remain; i < size128 && i < buffer.length; i++) {
                    buffer[i] = termByte;
                }
                size = size128;
            }
            len = remain;
        } else {
            // Continuous
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        // Invert
        basic.invertMemory(buffer, size);

        return len;
    }

    /**
     * Delete FAT area
     */
    @Override
    public void deleteGroupNumber(int groupNum) {
        // Mark as unused
        setGroupNumber(groupNum, 0);
    }
}
