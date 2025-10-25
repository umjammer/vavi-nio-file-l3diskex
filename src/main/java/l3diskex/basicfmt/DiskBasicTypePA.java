/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;


import l3diskex.basicfmt.BasicCommon.DirectoryN88;


public class DiskBasicTypePA extends DiskBasicTypeN88 {

    /**
     * Public constructor used by clients.
     *
     * @param basic DiskBasic instance.
     * @param fat   DiskBasicFat instance.
     * @param dir   DiskBasicDir instance.
     */
    public DiskBasicTypePA(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super(basic, fat, dir);
    }

    /**
     * Calculates the starting sector number for a given group (cluster)
     * number.  The calculation converts a group number, which is stored
     * side‑ (surface‑) priority, into a sector number that is track‑
     * priority.
     *
     * @param groupNum The group (cluster) number.  It is treated as an
     *                 unsigned 32‑bit integer, so {@code long} is used
     *                 to avoid sign issues.
     * @return The starting sector number for the group.
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        int sides = basic.getSidesPerDiskOnBasic();                // # of surfaces
        int grpPerTrk = basic.getSectorsPerTrack() / basic.getSectorsPerGroup(); // groups per track
        int grpPerSid = sides * grpPerTrk;                         // groups per surface

        int ngrp = (groupNum / grpPerSid) * grpPerSid
                + (groupNum % sides) * grpPerTrk
                + ((groupNum % grpPerSid) / sides);

        return ngrp * basic.getSectorsPerGroup();
    }
}
