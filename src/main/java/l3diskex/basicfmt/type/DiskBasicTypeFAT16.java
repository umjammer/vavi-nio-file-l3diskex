///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.type;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;


/**
 * FAT16 processing
 */
public abstract class DiskBasicTypeFAT16<T extends Directory> extends DiskBasicTypeFATBase<T> {

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Sets a FAT entry.
     *
     * @param num Group (cluster) number (0…).
     * @param val Value to write (16‑bit little‑endian).
     */
    @Override
    public void setGroupNumber(int num, int val) {
        fat.getDiskBasicFatArea().setData16LE(num, val);
    }

    /**
     * Returns a FAT entry.
     *
     * @param num Group (cluster) number (0…).
     * @return The 16‑bit value stored at the FAT entry.
     */
    @Override
    public int getGroupNumber(int num) {
        return fat.getDiskBasicFatArea().getData16LE(0, num);
    }

    /**
     * Checks the FAT area.
     *
     * @param isFormatting {@code true} when formatting the disk.
     * @return 1.0: success, no warning, 0.0–1.0: warnings exist, <0.0: error
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        // Duplicate check: 2‑bit clusters, 0x1FFF maximum cluster number
        double validRatio = checkFatDuplicated(isFormatting, 2, 0x1fff);

        return validRatio;
    }

    /**
     * Calculates the remaining free size of the disk.
     *
     * @param wrote {@code true} if the disk has already been written.
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        calcDiskFreeSizeBase(wrote, 2, 0xfff8);
    }
}
