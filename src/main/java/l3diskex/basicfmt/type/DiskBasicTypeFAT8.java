/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFAT8.DiskBasicDirItemFAT8F.DirectoryFat8F;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 * FAT8 processing
 */
public abstract class DiskBasicTypeFAT8<T extends Directory> extends DiskBasicType<T> {

    private static final Logger logger = System.getLogger(DiskBasicTypeFAT8.class.getName());

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super.init(basic, fat, dir);
    }

    /** Set FAT position */
    @Override
    public void setGroupNumber(int num, int val) {
        // 8bit FAT
        fat.getDiskBasicFatArea().setData8(num, val);
    }

    /** Returns FAT position */
    @Override
    public int getGroupNumber(int num) {
        // 8bit FAT
        return fat.getDiskBasicFatArea().getData8(0, num) & 0xff;
    }

    /** Check FAT area */
    @Override
    public double checkFat(boolean isFormatting) {
        int end = basic.getFatEndGroup() < 0xff ? basic.getFatEndGroup() : 0xff;
        int[] table = new int[end + 1];

        // Check whether the same group number is duplicated
        for (int pos = 0; pos <= end; pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum <= end) {
                table[groupNum]++;
            }
        }
        // Error if the same group number is duplicated
        double validRatio = 1.0;
        for (int pos = 0; pos <= end; pos++) {
            if (table[pos] > 4) {
                validRatio = -1.0;
logger.log(Level.TRACE, "too many references to group: " + table[pos]);
                break;
            }
        }

        return validRatio;
    }

    /** Fill sector data with specified code */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        if (track.getTrackNumber() == basic.getManagedTrackNumber()) {
            // In case of file management area
            sector.fill(basic.getFillCodeOnFAT());
        } else {
            // User area
            sector.fill(basic.getFillCodeOnFormat());
        }
    }

    /** Individual processing after filling sector data set FAT reserved */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // Put 0 at the beginning of FAT area
        fat.set(0, (byte) 0);

        return true;
    }

    /** Calculate the last group number when allocating groups */
    @Override
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        // Number of remaining sectors used
        int remainSecs = ((sizeRemain[0] - 1) / basic.getSectorSize());
        if (remainSecs >= basic.getSectorsPerGroup()) {
            remainSecs = basic.getSectorsPerGroup() - 1;
        }
        int lastGroupNum = remainSecs & 0xff;
        lastGroupNum += basic.getGroupFinalCode();
        return lastGroupNum;
    }

    /**
     * FAT8 specific implementation for F-BASIC / L3 1S
     */
    public abstract static class DiskBasicTypeFAT8F extends DiskBasicTypeFAT8<DirectoryFat8F> {

        @Override
        public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFat8F> dir) {
            super.init(basic, fat, dir);
        }

        /** Returns next free position */
        @Override
        public int getNextEmptyGroupNumber(int currentGroup) {
            int newNum = INVALID_GROUP_NUMBER;
            // Search in ascending order of numbers
            for (int num = currentGroup; num <= basic.getFatEndGroup(); num++) {
                int groupNum = getGroupNumber(num);
                if (groupNum == basic.getGroupUnusedCode()) {
                    newNum = num;
                    break;
                }
            }
            return newNum;
        }

        /** Track number to skip */
        @Override
        public int calcSkippedTrack() {
            return basic.getManagedTrackNumber();
        }

        /** Determine the data size of the last sector of the file */
        @Override
        public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryFat8F> item,
                                            InputStream iStream, OutputStream oStream,
                                            byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) {
            // File size is at sector size boundary, so calculation is required
            if (item.needCheckEofCode()) {
                // Output up to one byte before the termination code
                byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
                // Except when random access
                int len = sectorSize - 1;
                for (; len >= 0; len--) {
                    if (sectorBuffer[len] == eofCode) break;
                }
                if (len < 0) {
                    // No termination code?
                    len = sectorSize;
                }
                sectorSize = len;
            }
            return sectorSize;
        }
    }
}
