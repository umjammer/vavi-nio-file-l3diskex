/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemL32D.DirectoryL32d;
import l3diskex.diskimg.DiskImage.DiskImageSector;


/**
 * LEVEL-3 BASIC 2D (double side, double density) processing
 */
public class DiskBasicTypeL32D extends DiskBasicTypeFAT8<DirectoryL32d> {

    public static final int FORMAT_TYPE_L3S1_2D = 1;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_L3S1_2D;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryL32d> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0~1.0: Warning present, <0.0: Error present
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            // Subtract track 0 and management track portions
            endGroup -= basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() * (basic.getManagedTrackNumber() == 0 ? 1 : 2);
            endGroup /= basic.getSectorsPerGroup();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /**
     * Check FAT area
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0 Normal, 0.0-1.0 Warning present, <0.0 Error present
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // Check FAT header area
            DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
            if (sector == null) {
                validRatio = -1.0;
            } else if (!(sector.get(0) == 0 || sector.get(0) == (byte) 0xff)) {
                validRatio = -1.0;
            }
        }
        return validRatio;
    }

    /**
     * Returns a free FAT position
     *
     * @return 0xff: No free space
     */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;
        // Search from positions close to the management area

        // Number of groups per track
        int groupsPerTrack = basic.getSectorsPerTrackOnBasic() * 2 / basic.getSectorsPerGroup();

        // Maximum number of groups
        int maxGroup = basic.getFatEndGroup() + 1 - managedStartGroup;
        if (maxGroup < managedStartGroup) maxGroup = managedStartGroup;
        maxGroup = maxGroup * 2 - 1;

        for (int i = 0; i <= maxGroup; i++) {
            int i2 = i / groupsPerTrack;
            int i4 = i / groupsPerTrack / 2;
            int num;
            if ((i2 & 1) == 0) {
                num = managedStartGroup - ((i4 + 1) * groupsPerTrack) + (i % groupsPerTrack);
            } else {
                num = managedStartGroup + (i4 * groupsPerTrack) + (i % groupsPerTrack);
            }
            if (basic.getFatEndGroup() < num) {
                continue;
            }
            int groupNum = getGroupNumber(num);
            if (groupNum == basic.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    /**
     * Calculate group number from track number of management area
     *
     * @return Computed group number
     */
    @Override
    public int calcManagedStartGroup() {
        int track = basic.getManagedTrackNumber();
        int side = basic.getFatSideNumber();
        int sides = basic.getSidesPerDiskOnBasic();
        int sectorsPerGroup = basic.getSectorsPerGroup();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        // Subtract 1 as it starts from track 1
        track--;
        managedStartGroup = (track * sides + side) * sectorsPerTrack / sectorsPerGroup;
        return managedStartGroup;
    }

    /**
     * Track number to skip
     *
     * @return Management track number
     */
    @Override
    public int calcSkippedTrack() {
        return basic.getManagedTrackNumber();
    }

    /**
     * Calculate start sector of data area
     *
     * @return Start sector position
     */
    @Override
    public int calcDataStartSectorPos() {
        // Exclude track 0
        return basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
    }

    /**
     * Calculate the last group number when allocating groups
     *
     * @param groupNum   Current group number
     * @param sizeRemain Remaining data size (reference implementation via array)
     * @return Final group number
     */
    @Override
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        if ((sizeRemain[0] % basic.getSectorSize()) == 0) {
            // If size is on a sector boundary, increment size by 1 to ensure next sector allocation.
            sizeRemain[0]++;
        }
        if (sizeRemain[0] > (basic.getSectorsPerGroup() * basic.getSectorSize())) {
            // Next group is required
            return groupNum;
        } else {
            // This is the final group
            return super.calcLastGroupNumber(groupNum, sizeRemain);
        }
    }
}
