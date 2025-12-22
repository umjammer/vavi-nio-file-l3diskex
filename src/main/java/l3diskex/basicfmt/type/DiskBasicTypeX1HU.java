/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.util.List;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.DirectoryX1Hu;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_MISSING;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_DEFAULT;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_SWORD;


/**
 * X1 Hu-BASIC processing
 * <p>
 * DiskBasicParam Specific parameters
 *
 * <li>IPLString: IPL string in sector 1</li>
 */
public class DiskBasicTypeX1HU extends DiskBasicType<DirectoryX1Hu> {

    public static final int FORMAT_TYPE_X1HU = 6;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_X1HU;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryX1Hu> dir) {
        super.init(basic, fat, dir);
    }

    /** Set FAT position */
    @Override
    public void setGroupNumber(int num, int val) {
        DiskBasicFatArea fatArea = this.fat.getDiskBasicFatArea();
        for (int j = 0; j < fatArea.size(); j++) {
            List<DiskBasicFatBuffer> fatBufList = fatArea.get(j);
            // 8bit FAT + 8bit
            for (DiskBasicFatBuffer fatBuf : fatBufList) {
                int halfSize = fatBuf.getSize() >> 1;
                if (num < halfSize) {
                    fatBuf.set(num, basic.invertUint8((byte) val));
                    fatBuf.set(num + halfSize, basic.invertUint8((byte) ((val & 0xff00) >> 8)));
                    break;
                }
                num -= fatBuf.getSize();
            }
        }
    }

    /** Returns FAT position */
    @Override
    public int getGroupNumber(int num) {
        int newNum = INVALID_GROUP_NUMBER;
        List<DiskBasicFatBuffer> fatBufList = fat.getDiskBasicFatBuffers(0);
        if (fatBufList == null) {
            return newNum;
        }
        // 8bit FAT + 8bit
        for (DiskBasicFatBuffer fatBuf : fatBufList) {
            int halfSize = fatBuf.getSize() >> 1;
            if (num < halfSize) {
                newNum = basic.invertUint8((byte) fatBuf.get(num));
                if (basic.getFatEndGroup() >= 0x80) {
                    newNum |= (int) basic.invertUint8((byte) fatBuf.get(num + halfSize)) << 8;
                }
                break;
            }
            num -= fatBuf.getSize();
        }
        return newNum;
    }

    /** Returns a free FAT position */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;
        // Search in ascending order of numbers
        for (int num = 0; num <= basic.getFatEndGroup(); num++) {
            if (num == basic.getGroupFinalCode()) num += basic.getGroupFinalCode();
            int groupNum = getGroupNumber(num);
            if (groupNum == basic.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    /** Returns next free position */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        int newNum = INVALID_GROUP_NUMBER;

        // Search so that groups are continuous
        int groupMax = basic.getFatEndGroup() + 1;
        int groupStart = currentGroup;
        int groupEnd;
        int dir;
        boolean found = false;

        dir = 1; // Initially search in + direction, if not found, search in - direction
        for (int i = 0; i < 2; i++) {
            groupEnd = (dir > 0 ? groupMax : -1);
            for(int group = groupStart; group != groupEnd; group += dir) {
                if (dir > 0 && group == basic.getGroupFinalCode()) group += basic.getGroupFinalCode();
                else if (dir < 0 && group == basic.getGroupSystemCode()) group -= basic.getGroupFinalCode();
                int groupNum = getGroupNumber(group);
                if (groupNum == basic.getGroupUnusedCode()) { // 0xff
                    newNum = group;
                    found = true;
                    break;
                }
            }
            if (found) break;
            dir = -dir;
        }
        return newNum;
    }

    /** Check FAT area */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = 1.0;

        // Whether the beginning of the FAT region is the number of sectors in the FAT region
        DiskBasicFatArea fatArea = fat.getDiskBasicFatArea();
        if (fatArea.matchData8(0, basic.invertUint8((byte) 0x01)) != fatArea.size()) {
            return -1.0;
        }

        int end = basic.getFatEndGroup();
        int[] table = new int[end + 1];

        // Check whether the same group number is duplicated
        for (int pos = 0; pos <= end; pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum > 0 && groupNum <= end) {
                table[groupNum]++;
            }
        }
        // Error if the same group number is duplicated
        for (int value : table) {
            if (value > 1) {
                validRatio = 0.0;
                break;
            }
        }

        // Hu-BASIC or S-OS SWORD?
        if (validRatio >= 0) {
            int aType = basic.getVariousIntegerParam("DefaultAsciiType");
            int point = 0;
            for (int i = 0; i < 2; i++) {
                DiskImageSector sector = switch (i) {
                    case 0 ->
                        // IPL area
                            basic.getSector(0, 0, 1);
                    case 1 ->
                        // Directory area
                            basic.getManagedSector(basic.getDirStartSector() - 1);
                    default -> null;
                };
                if (sector != null) {
                    int hFind = sector.find("CZ8F".getBytes(), 4);
                    int sFind = sector.find("SWORD".getBytes(), 5);
                    if (aType == EXTERNAL_X1_DEFAULT && hFind >= 0) {
                        point++;
                    } else if (aType == EXTERNAL_X1_SWORD && sFind >= 0) {
                        point++;
                    }
                }
            }

            if (point < 1) {
                validRatio /= 2.0;
            }
        }

        return validRatio;
    }

    /** Get each parameter from disk and calculate necessary parameters */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() - 1;
            if (endGroup >= 0x80) {
                endGroup += 0x80;
            }
            basic.setFatEndGroup(endGroup);
        }

        return 1.0;
    }

    /** Get usable disk size */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        if (groupSize[0] >= basic.getGroupFinalCode()) groupSize[0] -= basic.getGroupFinalCode();
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        int fSize = 0;
        int groups = 0;
        FatAvailability fStats = FAT_AVAIL_USED;
        fatAvailability.empty();
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int groupNum = getGroupNumber(pos);
            if (pos >= basic.getGroupFinalCode() && pos <= basic.getGroupSystemCode()) {
                fStats = FAT_AVAIL_MISSING;
            } else if (groupNum == basic.getGroupUnusedCode()) {
                fSize = basic.getSectorSize() * basic.getSectorsPerGroup();
                groups = 1;
                fStats = FAT_AVAIL_FREE;
            } else if (groupNum >= basic.getGroupFinalCode() && groupNum <= basic.getGroupSystemCode()) {
                fStats = FAT_AVAIL_USED_LAST;
            }
            fatAvailability.add(fStats, fSize, groups);
        }
    }

    /** Get start sector number from group number */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        if (groupNum > basic.getGroupSystemCode()) {
            // Subtract 0x80 for numbers 0x100 and after
            groupNum -= basic.getGroupFinalCode();
        }
        return groupNum * basic.getSectorsPerGroup();
    }

    /** Get final sector number from group number */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int sectorEnd = sectorStart + basic.getSectorsPerGroup() - 1;
        if (nextGroup >= basic.getGroupFinalCode() && nextGroup <= basic.getGroupSystemCode()) {
            // If it is the final group, up to the specified sector
            sectorEnd = sectorStart + (nextGroup - basic.getGroupFinalCode());
        }
        return sectorEnd;
    }

    /** Judge whether directory is root */
    @Override
    public boolean isRootDirectory(int groupNum) {
        int secNum = basic.getDirEndSector();
        int startGroup = secNum / basic.getSectorsPerGroup();
        return groupNum < startGroup;
    }

    /** Rename directory */
    @Override
    public boolean renameOnMakingDirectory(String[] dirName) {
        // Directories starting with empty or "." cannot be created
        if (dirName[0].isEmpty() || dirName[0].startsWith(".")) {
            return false;
        }
        int pos = dirName[0].indexOf(".");
        if (pos != -1) {
            // Remove extension and below
            dirName[0] = dirName[0].substring(0, pos);
        }
        // Add extension
        dirName[0] += ".DIR";

        return true;
    }
}
