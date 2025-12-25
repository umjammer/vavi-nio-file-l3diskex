/*
 * Author: Sasaji (original author)
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * FAT handling
 */
public abstract class DiskBasicTypeFATBase<T extends Directory> extends DiskBasicType<T> {

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Returns the next free FAT position
     *
     * @return INVALID_GROUP_NUMBER: No free space
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) throws IOException {
        int newNum = INVALID_GROUP_NUMBER;

        // Search for continuous groups
        int groupStart = currentGroup;
        int groupEnd = basic.getFatEndGroup();
        boolean found = false;

        for (int i = 0; i < 2; i++) {
            for (int g = groupStart; g <= groupEnd; g++) {
                int groupNum = getGroupNumber(g);
                //logger.log(Level.TRACE, "DiskBasicTypeFATBase::GetNextEmptyGroupNumber: g: %d groupNum: %d".formatted(g, groupNum));
                if (groupNum == basic.getGroupUnusedCode()) {
                    newNum = g;
                    found = true;
                    break;
                }
            }
            if (found) break;
            // If not found, search from the beginning
            groupStart = 2;
        }
        return newNum;
    }

    /**
     * Check for duplication in FAT area
     *
     * @param isFormatting Whether formatting is in progress
     * @param startGroup   Group number to start duplicate check
     * @param maxGroup     Maximum group number to perform duplicate check
     * @return 1.0: Normal, 0.0-1.0: Warning present, <0.0: Error present
     */
    public double checkFatDuplicated(boolean isFormatting, int startGroup, int maxGroup) throws IOException {
        int end = Math.min(basic.getFatEndGroup(), maxGroup);
        int[] table = new int[end + 1];
        Arrays.fill(table, 0);

        // Check whether the same group number is duplicated
        for (int pos = 0; pos <= end; pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum <= end) {
                table[groupNum]++;
            }
        }
        // Error if the same group number is duplicated
        double validRatio = 1.0;
        for (int pos = startGroup; pos <= end; pos++) {
            if (table[pos] > 4) {
                validRatio = -1.0;
                break;
            }
        }

        return validRatio;
    }

    /**
     * Calculate group number from track number of management area
     */
    @Override
    public int calcManagedStartGroup() {
        managedStartGroup = 0;
        return managedStartGroup;
    }

    /**
     * Get usable disk size (for MS-DOS)
     */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() - 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * Calculate remaining disk size (for MS-DOS)
     *
     * @param wrote      Whether after writing
     * @param startGroup Start group number
     * @param usedGroup  In-use group number
     */
    public void calcDiskFreeSizeBase(boolean wrote, int startGroup, int usedGroup) throws IOException {
        fatAvailability.clear();

        // System area
        for (int pos = 0; pos < startGroup; pos++) {
            fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
        }

        // Clusters start from 2 (MS-DOS)
        for (int pos = startGroup; pos <= basic.getFatEndGroup(); pos++) {
            int fSize = 0;
            int groups = 0;
            int groupNum = getGroupNumber(pos);
            FatAvailability fsts = FAT_AVAIL_USED;
            if (groupNum == basic.getGroupUnusedCode()) {
                fSize = basic.getSectorSize() * basic.getSectorsPerGroup();
                groups = 1;
                fsts = FAT_AVAIL_FREE;
            } else if (groupNum >= usedGroup) { // 0xff8, 0xfff8, 0xfff_ffff8
                fsts = FAT_AVAIL_USED_LAST;
            }
            fatAvailability.add(fsts, fSize, groups);
            //logger.log(Level.TRACE, "DiskBasicTypeFATBase::CalcDiskFreeSizeBase: pos:%d groupNum:%d size: %d groups: %d".formatted(pos, groupNum, fSize, groups));
        }
    }

    /**
     * Get starting sector number from group number
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        // Starts from 2 (0, 1 are reserved)
        if (groupNum < 2) {
            return -1;
        }
        return (groupNum - 2) * basic.getSectorsPerGroup();
    }

    /**
     * Get final sector number from group number
     */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int sectorEnd = sectorStart + basic.getSectorsPerGroup() - 1;
        return sectorEnd;
    }

    /**
     * Calculate start sector of data area
     */
    @Override
    public int calcDataStartSectorPos() {
        return basic.getDirEndSector(); // Calculate starting from 0 after the directory
    }

    /**
     * Fill sector data with specified code
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        int sectorPos = basic.calcSectorPosFromNumForGroup(track.getTrackNumber(), track.getSideNumber(), sector.getSectorNumber(), 0, 1);
        if (sectorPos < 0) {
            // In case of file management area
            sector.fill(basic.getFillCodeOnFAT());
        } else {
            // User area
            sector.fill(basic.getFillCodeOnFormat());
        }
    }

    /**
     * Calculate the last group number when allocating groups
     */
    @Override
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        return (groupNum != INVALID_GROUP_NUMBER) ? basic.getGroupFinalCode() : groupNum;
    }
}
