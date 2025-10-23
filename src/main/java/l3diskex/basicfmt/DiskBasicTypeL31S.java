/**
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicTypeFAT8.DiskBasicTypeFAT8F;
import l3diskex.diskimg.DiskImage.DiskImageSector;


public class DiskBasicTypeL31S extends DiskBasicTypeFAT8F {

    /**
     * Public constructor used by clients.
     *
     * @param basic DiskBasic instance.
     * @param fat   DiskBasicFat instance.
     * @param dir   DiskBasicDir instance.
     */
    public DiskBasicTypeL31S(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    //
    // Parameter parsing on disk
    //

    /**
     * Retrieves various parameters from the disk and computes any
     * necessary derived parameters.
     *
     * @param isFormatting {@code true} when formatting the disk.
     * @return 1.0 – success, 0.0–1.0 – warnings, <0.0 – error
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.diskBasicParam.getFatEndGroup() == 0) {
            int endGroup = basic.diskBasicParam.getTracksPerSideOnBasic()
                    * basic.diskBasicParam.getSidesPerDiskOnBasic()
                    * basic.diskBasicParam.getSectorsPerTrackOnBasic();
            // Subtract the tracks reserved for disk management
            endGroup -= basic.diskBasicParam.getSidesPerDiskOnBasic()
                    * basic.diskBasicParam.getSectorsPerTrackOnBasic();
            // Convert to group (cluster) units
            endGroup /= basic.diskBasicParam.getSectorsPerGroup();
            // Store the last usable FAT group (zero‑based)
            basic.diskBasicParam.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /* -- */
    /* FAT area check                                                        */
    /* -- */

    /**
     * Checks the FAT area.
     *
     * @param isFormatting {@code true} when formatting the disk.
     * @return 1.0   – success (no warning)
     * 0.0–1.0 – warnings exist
     * <0.0 – error
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);

        if (validRatio >= 0.0) {
            // Check the first sector of the FAT region
            DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
            if (sector == null) {
                validRatio = -1.0;
            } else {
                int byte0 = sector.get(0);
                int byte1 = sector.get(1);
                if (!((byte0 == 0 || byte0 == 0xFF) && byte1 == 0xFF)) {
                    validRatio = -1.0;
                }
            }
        }

        return validRatio;
    }
}
