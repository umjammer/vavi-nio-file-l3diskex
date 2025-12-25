/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemN88.DirectoryN88;


/**
 * PASOPIA T-BASIC processing
 * <p>
 * DiskBasicParam
 *
 * <li>ReservedGroups : Group numbers (clusters) to be reserved</li>
 */
public class DiskBasicTypePA extends DiskBasicTypeN88 {

    public static final int FORMAT_TYPE_PA = 11;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_PA;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Get the starting sector number from the group number
     *
     * @param groupNum Group number
     * @return Starting sector number
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        // Since group (cluster) numbers are side (surface) prioritized,
        // convert sector numbers to be track prioritized.
        int sides = basic.getSidesPerDiskOnBasic();
        int groupPerTrack = basic.getSectorsPerTrack() / basic.getSectorsPerGroup();
        int groupPerSide = sides * groupPerTrack;
        int numOfGroups = (groupNum / groupPerSide) * groupPerSide + (groupNum % sides) * groupPerTrack + ((groupNum % groupPerSide) / sides);
        return numOfGroups * basic.getSectorsPerGroup();
    }
}
