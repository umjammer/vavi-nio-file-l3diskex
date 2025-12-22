/*
 * Sasaji (translated automatically)
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFROST.DirectoryFrost;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemFROST.FROST_GROUP_SIZE;


/**
 * Frost-DOS processing
 * <p>
 * DiskBasicParam
 *
 * <li>{@code ReservedGroups}: Group numbers (clusters) to be reserved</li>
 */
public class DiskBasicTypeFROST extends DiskBasicTypeFAT8<DirectoryFrost> {

    public static final int FORMAT_TYPE_FROST = 52;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_FROST;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFrost> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Set FAT position
     *
     * @param num Group number (0...)
     * @param val Value
     */
    @Override
    public void setGroupNumber(int num, int val) {
        // 16bit
        fat.getDiskBasicFatArea().setData16BE(num, convTrackSectorFromSectorPos(val));
    }

    /**
     * Returns FAT position
     *
     * @param num Group number (0...)
     */
    @Override
    public int getGroupNumber(int num) {
        // 16bit
        return convSectorPosFromTrackSector(fat.getDiskBasicFatArea().getData16BE(0, num));
    }

    /**
     * Returns the next free FAT position
     *
     * @param currentGroup Group number (0...)
     * @return INVALID_GROUP_NUMBER No free space
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        int newNum = INVALID_GROUP_NUMBER;
        // Search to be continuous with the current number
        boolean found = false;
        for (int i = 0; i < 2 && !found; i++) {
            int startGroupNum = (i == 0 ? currentGroup + 1 : 0);
            for (int groupNum = startGroupNum; groupNum <= basic.getFatEndGroup(); groupNum++) {
                int nextGnum = getGroupNumber(groupNum);
                if (nextGnum == basic.getGroupUnusedCode()) {
                    newNum = groupNum;
                    found = true;
                    break;
                }
            }
        }
        return newNum;
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 - 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        // Calculate the number of groups per track
        if (basic.getGroupsPerTrack() == 0) {
            // Calculate assuming 512 bytes per 1 group
            int count = 0;
            DiskImageTrack track = basic.getTrack(1, 0);
            if (track != null) {
                List<DiskImageSector> sectors = track.getSectors();
                if (sectors != null) {
                    for (DiskImageSector diskImageSector : sectors) {
                        int size = diskImageSector.getSectorSize();
                        count += (size / FROST_GROUP_SIZE);
                    }
                }
            }
            if (count == 0) {
                count = 11;
            }
            basic.setGroupsPerTrack(count);
        }
        // Number of groups per sector
        int groupsPerSector = (basic.getGroupsPerTrack() + basic.getSectorsPerTrackOnBasic() - 1) / basic.getSectorsPerTrackOnBasic();
        basic.setGroupsPerSector(groupsPerSector);

        // Number of groups
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getGroupsPerTrack();
            basic.setFatEndGroup(endGroup - 1);
        }

        return 1.0;
    }

    /**
     * Check FAT area
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 - 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // Check whether FAT and directory area are system reserved
            List<Integer> groups = basic.getReservedGroups();
            for (int group : groups) {
                int groupNum = getGroupNumber(group);
                if (groupNum != basic.getGroupSystemCode()) {
                    validRatio = -1.0;
                    break;
                }
            }
        }
        return validRatio;
    }

    /**
     * Get usable disk size
     *
     * @param diskSize  Disk size
     * @param groupSize Number of groups
     */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = 0;
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum != basic.getGroupSystemCode()) groupSize[0]++;
        }
        diskSize[0] = groupSize[0] * basic.getSectorSize() / basic.getGroupsPerSector();
    }

    /**
     * Calculate remaining disk size
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.clear();

        // Check if used
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int fSize = 0;
            int groups = 0;
            int groupNum = getGroupNumber(pos);
            FatAvailability fsts = FAT_AVAIL_USED;
            if (groupNum == basic.getGroupUnusedCode()) {
                fSize = (basic.getSectorSize() / basic.getGroupsPerSector());
                groups = 1;
                fsts = FAT_AVAIL_FREE;
            } else if (groupNum == basic.getGroupSystemCode()) {
                fsts = FAT_AVAIL_SYSTEM;
            } else if (groupNum >= basic.getGroupFinalCode()) {
                fsts = FAT_AVAIL_USED_LAST;
            }
            fatAvailability.add(fsts, fSize, groups);
        }

        //freeDiskSize = (int) fSize;
        //freeGroups = (int) groups;
    }

    /**
     * Find a position where unused are continuous
     */
    public int findContinuousArea(int groupSize) {
        // Search for a position where unused are continuous
        int group = INVALID_GROUP_NUMBER;
        int groupStart = INVALID_GROUP_NUMBER;
        int count = 0;
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup() && count < groupSize; groupNum++) {
            if (getGroupNumber(groupNum) == basic.getGroupUnusedCode()) {
                if (count == 0) {
                    groupStart = groupNum;
                }
                count++;
            } else {
                count = 0;
            }
        }
        if (count == groupSize) {
            group = groupStart;
        }
        return group;
    }

    /**
     * Allocate groups for the data size
     *
     * @param fileUnitNum File number
     * @param item        Directory item
     * @param dataSize    Data size to allocate (bytes)
     * @param flags       New or append
     * @param groupItems  List of allocated sectors
     * @return >0: Normal, -1: No free space (before setting start group), -2: No free space (after setting start group)
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryFrost> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int groups = 0;

        // FAT
        int rc = 0;
        boolean firstGroup = flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW;
        int[] sizeRemain = {dataSize};

        int bytesPerGroup = basic.getSectorSize() / basic.getGroupsPerSector();
        int groupSize = (dataSize + bytesPerGroup - 1) / bytesPerGroup;
        // Area that can be allocated continuously
        int groupNum = findContinuousArea(groupSize);
        if (groupNum == INVALID_GROUP_NUMBER) {
            groupNum = getEmptyGroupNumber();
        }
        int limit = basic.getFatEndGroup() + 1;
        while (rc >= 0 && limit >= 0 && sizeRemain[0] > 0) {
            if (groupNum == INVALID_GROUP_NUMBER) {
                // No free space
                rc = firstGroup ? -1 : -2;
                break;
            }
            // Reserve position
            setGroupNumber(groupNum, basic.getGroupFinalCode());

            // Write group number
            if (firstGroup) {
                item.setStartGroup(fileUnitNum, groupNum);
                firstGroup = false;
            }

            // Search for next free group
            int nextGroupNum = getNextEmptyGroupNumber(groupNum);

            // If there is no next free space or the remaining size fits in this group
            if (nextGroupNum == INVALID_GROUP_NUMBER || sizeRemain[0] <= bytesPerGroup) {
                // Final group number
                nextGroupNum = calcLastGroupNumber(nextGroupNum, sizeRemain);
            }

            basic.getNumsFromGroup(groupNum, nextGroupNum, basic.getSectorSize(), sizeRemain[0], groupItems[0]);

            // Set group number
            setGroupNumber(groupNum, nextGroupNum);

            groupNum = nextGroupNum;

            sizeRemain[0] -= bytesPerGroup;
            groups++;

            limit--;
        }
        if (limit < 0) {
            // too large or infinite loop
            rc = firstGroup ? -1 : -2;
        }

        if (rc >= 0) {
            if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
                // For append, connect chain
                if (groupItems[0].size() > 0) {
                    rc = chainGroups(item.getStartGroup(0), groupItems[0].get(0).group);
                }
            }
        } else {
            // Delete groups
            deleteGroups(groupItems[0]);
            rc = -1;
        }

        return rc;
    }

    /**
     * Get starting sector number from group number
     *
     * @param groupNum Group number
     * @return Starting sector number
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    /**
     * Get logical sector number from track + sector number
     */
    public int convSectorPosFromTrackSector(int trkSec) {
        if (trkSec == basic.getGroupUnusedCode() || trkSec == basic.getGroupFinalCode() || trkSec == basic.getGroupSystemCode()) {
            return trkSec;
        }

        return (trkSec >> 8) * basic.getGroupsPerTrack() + (trkSec & 0xff) - 1;
    }

    /**
     * Get track + sector number from logical sector number
     */
    public int convTrackSectorFromSectorPos(int pos) {
        if (pos == basic.getGroupUnusedCode() || pos == basic.getGroupFinalCode() || pos == basic.getGroupSystemCode()) {
            return pos;
        }

        return ((pos / basic.getGroupsPerTrack()) << 8) + (pos % basic.getGroupsPerTrack()) + 1;
    }

    /**
     * Get track, side, sector numbers from sector position (serial number where track 0, side 0, sector 1 is 0)
     * The sector position is a serial number where track 0, side 0, sector 1 is 0, regardless of the model
     *
     * @param sectorPos Sector position (serial number where track 0, side 0, sector 1 is 0)
     * @param trackNum  Track number
     * @param sideNum   Side number
     * @param sectorNum Sector number
     * @param divNum    Division number (can be null)
     * @param numOfDivs Number of divisions (can be null)
     */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] numOfDivs) {
        int groupsPerTrack = basic.getGroupsPerTrack();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();

        int grpsPerSec = basic.getGroupsPerSector();

        // 2D, 2HD
        int trackSideNum = sectorPos / groupsPerTrack;
        trackNum[0] = trackSideNum / sidesPerDisk;
        sideNum[0] = trackSideNum % sidesPerDisk;
        sectorNum[0] = ((sectorPos % groupsPerTrack) / grpsPerSec);
        if (divNum != null) divNum[0] = ((sectorPos % groupsPerTrack) % grpsPerSec);

        if (sectorNum[0] * grpsPerSec > groupsPerTrack) {
            grpsPerSec = grpsPerSec - (sectorNum[0] * grpsPerSec - groupsPerTrack);
        }

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBaseOnDisk();

        if (numOfDivs != null) numOfDivs[0] = grpsPerSec;
    }

    /**
     * Get sector position (serial number where track 0, side 0, sector 1 is 0) from track, side, sector numbers
     * The sector position is a serial number where track 0, side 0, sector 1 is 0, regardless of the model
     *
     * @param trackNum  Track number
     * @param sideNum   Side number
     * @param sectorNum Sector number
     * @param divNum    Division number
     * @param numOfDivs Number of divisions
     * @return Sector position (serial number where track 0, side 0, sector 1 is 0)
     */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        int groupsPerTrack = basic.getGroupsPerTrack();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBaseOnDisk();

        // 2D, 2HD
        sectorPos = (trackNum * sidesPerDisk + sideNum) * groupsPerTrack;
        sectorPos += sectorNum * numOfDivs + divNum;

        return sectorPos;
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
     * Format Set FAT reserved
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT track 0 is system
        int endGroupNum = basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic() - 1;
        for (int groupNum = 0; groupNum < endGroupNum; groupNum++) {
            setGroupNumber(groupNum, basic.getGroupSystemCode());
        }
        // FAT FAT, DIR area is system
        List<Integer> groups = basic.getReservedGroups();
        for (int groupNum : groups) {
            setGroupNumber(groupNum, basic.getGroupSystemCode());
        }
        return true;
    }

    /**
     * Calculate the last group number when allocating groups
     *
     * @param groupNum   Current group number
     * @param sizeRemain Remaining data size (mutable via int array)
     * @return Final group number
     */
    @Override
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        return basic.getGroupFinalCode();
    }

    /**
     * Data writing processing
     *
     * @param item      Directory item
     * @param iStream   Stream data
     * @param buffer    Buffer to write within sector
     * @param size      Buffer size to write
     * @param remain    Remaining data size
     * @param sectorNum Sector number
     * @param groupNum  Current group number
     * @param nextGroup Next group number
     * @param sectorEnd Final sector number
     * @param seqNum    Serial number (0...)
     * @return Number of bytes written
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryFrost> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            // Few left
            if (remain < 0) remain = 0;
            if (remain > 0) {
                // Read up to 'remain' bytes
                len = iStream.readNBytes(buffer, 0, remain);
                if (len < 0) len = 0; // Handle end of stream unexpectedly
            }
            if (size > len) {
                // Fill remaining with 0s
                Arrays.fill(buffer, len, size, (byte) 0);
            }
        } else {
            // Continuous
            len = iStream.readNBytes(buffer, 0, size);
            if (len < 0) len = 0;
        }
        // Invert
        basic.invertMemory(buffer, size);

        return len;
    }

    /**
     * Get attributes of IPL and managed area
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // Title name FAT area
        int[] divNum = new int[1], numOfDivs = new int[1];
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1 + 3, null, null, null, divNum, numOfDivs);
        if (sector != null) {
            byte[] buf = sector.getSectorBuffer();
            int offset = sector.getSectorSize() * divNum[0] / numOfDivs[0] + 0x140;
            if (buf[offset] >= 0x20 && (buf[offset] & 0xff) < 0xff) {
                StringBuilder sb = new StringBuilder();
                basic.getCharCodes().convToString(buf, offset, 64, sb, 0);
                String dst = sb.toString();
                data.setVolumeName(dst);
            }
        }
    }

    /**
     * Set attributes of IPL and managed area
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
    }
}
