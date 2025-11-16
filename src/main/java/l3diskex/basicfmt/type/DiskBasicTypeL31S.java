/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFAT8.DiskBasicDirItemFAT8F.DirectoryFat8F;
import l3diskex.basicfmt.type.DiskBasicTypeFAT8.DiskBasicTypeFAT8F;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_L3_1S;


/**
 * LEVEL-3 BASIC 1S(片面・単密度)の処理
 */
public class DiskBasicTypeL31S extends DiskBasicTypeFAT8F {

    @Override
    public boolean isSupported(DiskBasicFormatType typeNumber) {
        return typeNumber == FORMAT_TYPE_L3_1S;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFat8F> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Retrieves various parameters from the disk and computes any
     * necessary derived parameters.
     *
     * @param isFormatting {@code true} when formatting the disk.
     * @return 1.0: success, 0.0–1.0: warnings, <0.0: error
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            // Subtract the tracks reserved for disk management
            endGroup -= basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            endGroup /= basic.getSectorsPerGroup();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /**
     * Checks the FAT area.
     *
     * @param isFormatting {@code true} when formatting the disk.
     * @return 1.0: success (no warning), 0.0–1.0: warnings exist, <0.0: error
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // Check the first sector of the FAT region
            DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
            if (sector == null) {
                validRatio = -1.0;
            } else {
                int byte0 = sector.get(0) & 0xff;
                int byte1 = sector.get(1) & 0xff;
                if (!((byte0 == 0 || byte0 == 0xff) && byte1 == 0xff)) {
                    validRatio = -1.0;
                }
            }
        }
        return validRatio;
    }
}
