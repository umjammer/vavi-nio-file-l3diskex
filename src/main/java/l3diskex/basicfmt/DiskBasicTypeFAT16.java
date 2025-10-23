package l3diskex.basicfmt;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;


/**
 * @file DiskBasicTypeFAT16.java
 * <p>
 * Disk BASIC FAT type implementation (FAT‑16)
 * <p>
 * This file is a direct, literal translation of the original C++ sources
 * (basictype_fat16.h and basictype_fat16.cpp) into Java.  The class
 * hierarchy and public API remain unchanged.
 */
public class DiskBasicTypeFAT16 extends DiskBasicTypeFATBase {

    /**
     * Public constructor used by clients.
     *
     * @param basic DiskBasic instance.
     * @param fat   DiskBasicFat instance.
     * @param dir   DiskBasicDir instance.
     */
    public DiskBasicTypeFAT16(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /*
     * Access to FAT area
     */

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

    /*
     * FAT area check / assignment
     */

    /**
     * Checks the FAT area.
     *
     * @param isFormatting {@code true} when formatting the disk.
     * @return 1.0   – success, no warning
     * 0.0–1.0 – warnings exist
     * <0.0 – error
     */
    @Override
    public double checkFat(boolean isFormatting) {
        // Duplicate check: 2‑bit clusters, 0x1FFF maximum cluster number
        double validRatio = checkFatDuplicated(isFormatting, 2, 0x1fff);
        return validRatio;
    }

    /*
     * Disk size
     */

    /**
     * Calculates the remaining free size of the disk.
     *
     * @param wrote {@code true} if the disk has already been written.
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        calcDiskFreeSizeBase(wrote, 2, 0xfff8);
    }

    /*
     * File size / file chain / directory / format / save / write
     * (not implemented in the original C++ source – left empty)
     */

    /* No additional methods needed – the base class provides the default
       implementations where required. */
}
