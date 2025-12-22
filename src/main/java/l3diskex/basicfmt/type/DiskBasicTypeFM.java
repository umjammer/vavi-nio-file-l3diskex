/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFP.DirectoryFp;
import l3diskex.diskimg.DiskImage.DiskImageSector;


/**
 * F-BASIC processing
 * <p>
 * DiskBasicParam Specific parameters
 *
 * <li>IDSectorPosition  Logical sector number of ID sector</li>
 * <li>IDString          First string of ID sector</li>
 */
public class DiskBasicTypeFM extends DiskBasicTypeFAT8<DirectoryFp> {

    public static final int FORMAT_TYPE_FM = 2;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_FM;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFp> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Retrieve parameters from the disk and calculate
     * required parameters.
     *
     * @param isFormatting true: if formatting is in progress.
     * @return 1.0: normal, 0.0‑1.0: warning, <0.0: error
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            // subtract track 0 and the management track(s)
            endGroup -= basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() * (basic.getManagedTrackNumber() == 0 ? 1 : 2);
            endGroup /= basic.getSectorsPerGroup();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /**
     * Check FAT area.
     *
     * @param isFormatting true if formatting is in progress.
     * @return 1.0: normal, 0.0‑1.0: warning, <0.0: error
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // ID check
            byte id = basic.getVariousStringParam("IDString").getBytes()[0];
            DiskImageSector sector = basic.getSectorFromSectorPos(basic.getVariousIntegerParam("IDSectorPosition"));
            if (!(sector != null && id == sector.get(0))) {
                validRatio = -1.0;
            }
            // FAT header check
            sector = basic.getManagedSector(basic.getFatSideNumber() * basic.getSectorsPerTrackOnBasic() + basic.getFatStartSector() - 1);
            if (sector == null) {
                validRatio = -1.0;
            } else if (sector.get(0) != 0 || sector.get(1) != 0xff) {
                validRatio = -1.0;
            }
        }
        return validRatio;
    }

    /**
     * Calculate the start sector position of the data area.
     *
     * @return The sector number.
     */
    @Override
    public int calcDataStartSectorPos() {
        // exclude track 0
        return basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
    }

    /**
     * Determine the track number to skip.
     *
     * @return The track number, or 0x7fff if none.
     */
    @Override
    public int calcSkippedTrack() {
        int val = basic.getManagedTrackNumber();
        return val > 0 ? val : 0x7fff;
    }

    /**
     * After sector data is filled, perform additional
     * processing (format FAT, set ID).
     *
     * @param data Identified data (unused in this implementation).
     * @return true on success.
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // Set first FAT sector to zero
        fat.set(0, (byte) 0);

        // Initialize ID area
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.getVariousIntegerParam("IDSectorPosition"));
        if (sector != null) {
            sector.fill((byte) 0);
            byte[] id = basic.getVariousStringParam("IDString").getBytes();
            if (id.length > 0) {
                sector.copy(id, 0, id.length);
            }
        }

        return true;
    }
}
