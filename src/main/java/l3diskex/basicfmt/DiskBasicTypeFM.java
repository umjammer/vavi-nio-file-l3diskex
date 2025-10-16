/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;


import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;


public class DiskBasicTypeFM extends DiskBasicTypeFAT8 {

    public DiskBasicTypeFM(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /**
     * Retrieve parameters from the disk and calculate
     * required parameters.
     *
     * @param is_formatting true if formatting is in progress.
     * @return 1.0       normal
     * 0.0‑1.0   warning
     * <0.0      error
     */
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (basic.diskBasicParam.getFatEndGroup() == 0) {
            int end_group =
                    basic.diskBasicParam.getTracksPerSideOnBasic() *
                            basic.diskBasicParam.getSidesPerDiskOnBasic() *
                            basic.diskBasicParam.getSectorsPerTrackOnBasic();

            // subtract track 0 and the management track(s)
            int skip = basic.diskBasicParam.getSidesPerDiskOnBasic() *
                    basic.diskBasicParam.getSectorsPerTrackOnBasic() *
                    (basic.diskBasicParam.getManagedTrackNumber() == 0 ? 1 : 2);
            end_group -= skip;

            end_group /= basic.diskBasicParam.getSectorsPerGroup();
            basic.diskBasicParam.setFatEndGroup(end_group - 1);
        }
        return 1.0;
    }

    /**
     * Check FAT area.
     *
     * @param is_formatting true if formatting is in progress.
     * @return 1.0       normal
     *         0.0‑1.0   warning
     *         <0.0      error
     */
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = super.checkFat(is_formatting);
        if (valid_ratio >= 0.0) {
            // ID check
            byte id = basic.diskBasicParam.getVariousStringParam("IDString").getBytes()[0];
            DiskImageSector sector =
                    basic.getSectorFromSectorPos(
                            basic.diskBasicParam.getVariousIntegerParam("IDSectorPosition"));
            if (!(sector != null && id == sector.get(0))) {
                valid_ratio = -1.0;
            }

            // FAT header check
            sector = basic.getManagedSector(
                    basic.diskBasicParam.getFatSideNumber() * basic.diskBasicParam.getSectorsPerTrackOnBasic()
                            + basic.diskBasicParam.getFatStartSector() - 1);
            if (sector == null) {
                valid_ratio = -1.0;
            } else if (sector.get(0) != 0 || sector.get(1) != 0xff) {
                valid_ratio = -1.0;
            }
        }
        return valid_ratio;
    }

    /**
     * Calculate the start sector position of the data area.
     *
     * @return The sector number.
     */
    @Override
    public int calcDataStartSectorPos() {
        // exclude track 0
        return basic.diskBasicParam.getSectorsPerTrackOnBasic() *
                basic.diskBasicParam.getSidesPerDiskOnBasic();
    }

    /**
     * Determine the track number to skip.
     *
     * @return The track number, or 0x7fff if none.
     */
    @Override
    public int calcSkippedTrack() {
        int val = basic.diskBasicParam.getManagedTrackNumber();
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
        DiskImageSector sector =
                basic.getSectorFromSectorPos(
                        basic.diskBasicParam.getVariousIntegerParam("IDSectorPosition"));
        if (sector != null) {
            sector.fill((byte) 0);
            byte[] id = basic.diskBasicParam.getVariousStringParam("IDString").getBytes();
            if (id.length > 0) {
                sector.copy(id, 0, id.length);
            }
        }

        return true;
    }
}
