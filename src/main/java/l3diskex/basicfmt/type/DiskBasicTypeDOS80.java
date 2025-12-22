/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemDOS80.DirectoryDos80;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 * PC-8001 DOS processing
 * <p>
 * DiskBasicParam
 *
 * <li>CanMountEachSides  Whether to treat front/back sides separately</li>
 * <li>ReservedGroups  Group numbers (clusters) to be reserved</li>
 * <li>DefaultStartAddress  Starting address when BASIC is specified</li>
 * <li>DefaultExecuteAddress  Execution address when BASIC is specified</li>
 */
public class DiskBasicTypeDOS80 extends DiskBasicTypeFAT8<DirectoryDos80> {

    public static final int FORMAT_TYPE_DOS80 = 51;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_DOS80;
    }

    /** */
    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryDos80> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Returns a free FAT position
     */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = DiskBasicType.INVALID_GROUP_NUMBER;
        // Search from positions close to the management area

        // Number of groups per track
        int groupsPerTrack = basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup();
        // Maximum number of groups
        int maxGroup = basic.getFatEndGroup() - managedStartGroup;
        if (maxGroup < managedStartGroup) maxGroup = managedStartGroup;
        maxGroup = maxGroup * 2 - 1;

        for (int i = 0; i <= maxGroup; i++) {
            int i2 = i / groupsPerTrack;
            int i4 = i / groupsPerTrack / 2;
            int num;
            if ((i & 1) == 0) {
                num = managedStartGroup - ((i4 + 1) * groupsPerTrack) + (i % groupsPerTrack);
            } else {
                num = managedStartGroup + ((i4 + 1) * groupsPerTrack) + (i % groupsPerTrack);
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
     * Check FAT area
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // Check whether FAT and directory area are system reserved
            List<Integer> groups = basic.getReservedGroups();
            for (int group : groups) {
                int group_ = getGroupNumber(group);
                if (group_ != basic.getGroupSystemCode()) {
                    validRatio = -1.0;
                    break;
                }
            }
        }
        return validRatio;
    }

    /**
     * Fill sector data with specified code
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    /**
     * Individual processing after filling sector data – set FAT reserved
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        boolean valid = true;

        DiskImageSector sector;

        // DIR
        for (int s = basic.getDirStartSector(); s <= basic.getDirEndSector(); s++) {
            sector = basic.getManagedSector(s - 1);
            if (sector == null) {
                valid = false;
                break;
            }
            sector.fill(basic.getFillCodeOnDir());

            sector = basic.getManagedSector(s + 1);
            if (sector == null) {
                valid = false;
                break;
            }

            sector.fill(basic.getFillCodeOnDir());
        }

        // FAT
        if (valid) {
            for (int s = 0; s < basic.getSectorsPerFat(); s++) {
                sector = basic.getManagedSector(s + basic.getFatStartSector() - 1);
                if (sector == null) {
                    valid = false;
                    break;
                }
                sector.fill(basic.getFillCodeOnFAT());
            }
        }

        // Mark track 0 as reserved
        for (int groupNum = 0; groupNum < basic.getSectorsPerGroup(); groupNum++) {
            setGroupNumber(groupNum, basic.getGroupSystemCode());
        }

        // Mark parts used by the system as reserved
        List<Integer> groups = basic.getReservedGroups();
        for (int group : groups) {
            setGroupNumber(group, basic.getGroupSystemCode());
        }

        return valid;
    }

    /**
     * Determine the data size of the last sector of the file
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryDos80> item,
                                        InputStream iStream,
                                        OutputStream oStream,
                                        byte[] sectorBuffer,
                                        int sectorOffset,
                                        int sectorSize,
                                        int remainSize) throws IOException {
        // Cannot calculate directly, so return remaining size as is
        if (iStream != null) {
            sectorSize = iStream.available() % sectorSize;
        } else {
            sectorSize = remainSize;
        }
        return sectorSize;
    }

    /**
     * Data write process
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryDos80> item,
                         InputStream iStream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sectorNum,
                         int groupNum,
                         int nextGroup,
                         int sectorEnd,
                         int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            // Few left
            if (remain < 0) remain = 0;
            if (remain > 0) {
                int r = iStream.read(buffer, 0, remain);
                len = (r >= 0) ? r : 0;
            }
            if (size > remain) {
                // Fill the remaining area with 0
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // Continuous
            int r = iStream.read(buffer, 0, size);
            len = (r >= 0) ? r : 0;
        }
        // Invert if necessary
        basic.invertMemory(buffer, size);

        return len;
    }
}
