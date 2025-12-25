/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.DirectoryXDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 X-DOS for X1 processing

 DiskBasicParam
 <li>DirStartPositionOnRoot : Starting position of entry in root directory start sector</li>
 <li>DirStartPosition       : Starting position of entry in subdirectory start sector</li>
 <li>SubDirGroupSize        : Initial number of groups for subdirectory</li>
 */
public class DiskBasicTypeXDOS<T extends DirectoryXDos> extends DiskBasicType<T> {

    /** FAT information structure used by X-DOS */
    public static class XDosFat {

        public byte[] use = new byte[0x0a8];
        public byte[] map = new byte[0x158];
    }

    private static final int XDOS_FAT_START = 0xa8;
    private static final int VOLUME_NAME_LENGTH = 80;

    public static final int FORMAT_TYPE_XDOS = 61;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_XDOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super.init(basic, fat, dir);
    }

    /** Set a FAT entry at position 'num' to value 'val' */
    @Override
    public void setGroupNumber(int num, int val) {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        int pos = num / basic.getSectorsPerTrackOnBasic();   // round
        pos *= 2;
        int mask = 0x8000 >> (num % basic.getSectorsPerTrackOnBasic());
        if (mask >= 0x100) {
            mask >>= 8;
        } else {
            pos++;
        }

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return;
        }
        // FAT has a usage table
        pos += XDOS_FAT_START;
        fatBuf.bit(pos, (byte) mask, val == 0, basic.isDataInverted());
    }

    /** Is the group number 'num' used? */
    @Override
    public boolean isUsedGroupNumber(int num) {
        boolean exist = false;

        if (num > basic.getFatEndGroup()) {
            return false;
        }

        int pos = num / basic.getSectorsPerTrackOnBasic();   // round
        pos *= 2;
        int mask = (0x8000 >> (num % basic.getSectorsPerTrackOnBasic()));
        if (mask >= 0x100) {
            mask >>= 8;
        } else {
            pos++;
        }

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return true;
        }
        // FAT has a usage table
        pos += XDOS_FAT_START;
        exist = !fatbuf.bitTest(pos, (byte) mask, basic.isDataInverted());
        return exist;
    }

    /** Return the first free group number, or INVALID_GROUP_NUMBER */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return newNum;
        }
        // Search for free position
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup(); groupNum++) {
            if (!isUsedGroupNumber(groupNum)) {
                newNum = groupNum;
                break;
            }
        }
        return newNum;
    }

    /** Find a contiguous area of 'group_size' free groups */
    public int getContinuousArea(int group_size) {
        int newNum = INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return newNum;
        }

        int step = basic.getSectorsPerTrackOnBasic();
        int count = 0;
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup() && count < group_size;) {
            if (!isUsedGroupNumber(groupNum)) {
                if (count == 0) {
                    newNum = groupNum;
                }
                count++;
                groupNum++;
            } else {
                newNum = INVALID_GROUP_NUMBER;
                count = 0;
                // Skip to the beginning of each track
                groupNum = ((groupNum + step) / step) * step;
            }
        }
        return newNum;
    }

    /** Return the next free group number after 'curr_group' */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        return getEmptyGroupNumber();
    }

    /** Check the FAT area; return a status value */
    @Override
    public double checkFat(boolean is_formatting) {
        double validRatio = 1.0;

        byte[] hed = new byte[2];
        hed[0] = 1;
        hed[1] = 0x0a; // (int) basic.getSectorsPerTrackOnBasic();
        hed[1] |= 0x40;
        DiskImageSector sector = basic.getManagedSector(basic.getDirStartSector() - 1);
        if (sector != null) {
            if (sector.find(hed, 2) < 0) {
                return -1.0;
            }

            // Check sector count
            for (int i = 2; i < basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic(); i++) {
                if (sector.get(i) != hed[1]) {
                    validRatio -= 0.5;
                    if (validRatio < 0.0) break;
                }
            }
        }
        return validRatio;
    }

    /** Parse parameters on disk and compute any required values */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        // Number of groups
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /** Get usable disk size */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1 - basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        fatAvailability.empty();
//        fatAvailability.setCount(basic.getFatEndGroup() + 1, FAT_AVAIL_USED.ordinal());

        // Examine Allocation Map
        int groups = 0;
        int groupNum = 0;
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) return;
        for (int pos = XDOS_FAT_START; pos < fatBuf.getSize(); pos += 2) {
            short dat = (short)((fatBuf.get(pos) << 8) | fatBuf.get(pos+1));
            for(int bit = 0; bit < basic.getSectorsPerTrackOnBasic() && groupNum <= basic.getFatEndGroup(); bit++) {
                boolean used = ((dat & (0x8000 >> bit)) == 0);
                if (!used) {
                    fatAvailability.set(groupNum, FAT_AVAIL_FREE);
                    groups++;
                }
                groupNum++;
            }
        }

        // Groups of directory entries
        List<DiskBasicDirItem<T>> items = dir.getCurrentItems(null);
        if (items == null) return;
        for (DiskBasicDirItem<T> item : items) {
            if (item == null || !item.isUsed()) continue;
            int groupCount = item.getGroupCount();
            if (groupCount > 0) {
                DiskBasicGroupItem groupItem = item.getGroup(groupCount - 1);
                groupNum = groupItem.group;
                if (groupNum <= basic.getFatEndGroup()) {
                    fatAvailability.set(groupNum, FAT_AVAIL_USED_LAST);
                }
            }
        }

        // Check free space
        int groupEnd = basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        for(int pos = 0; pos < groupEnd; pos++) {
            // Directory area is used
            fatAvailability.set(pos, FAT_AVAIL_SYSTEM);
        }

        int freeSize = groups * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(freeSize);
        fatAvailability.setFreeGroups(groups);
    }

    /** Prepare for saving a file */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] file_size,
                                     DiskBasicDirItem<T> pItem, DiskBasicDirItem<T> nItem,
                                     DiskBasicError errInfo) throws IOException {
        // Allocate sector for chain
        int groupNum = getEmptyGroupNumber();
        if (groupNum == INVALID_GROUP_NUMBER) {
            return false;
        }
        // Sector
        DiskImageSector sector = basic.getSectorFromGroup(0);
        if (sector == null) {
            return false;
        }
        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return false;
        }
        sector.fill(basic.invertUint8((byte) 0));

        // Set sector in chain information
        nItem.setChainSector(sector, buf, null);

        // Set start group
        nItem.setStartGroup(0, groupNum, 1);

        // Reserve sector
        setGroupNumber(groupNum, 1);

        return true;
    }

    /** Allocate groups for a data block of size 'dataSize' */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<T> item,
                                  int dataSize, AllocateGroupFlags flags,
                                  DiskBasicGroups[] groupItems) {
        //int fileSize = 0;
        //int groups = 0;

        int rc = 0;
        int sectorSize = basic.getSectorSize();
        int remain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        int chainIndex = -1;
        int prevTrack = -1;
        int groupNum = INVALID_GROUP_NUMBER;

        if (item.getFileAttr().isDirectory()) {
            // In case of directory creation, search for contiguous free area
            groupNum = getContinuousArea(basic.getSubDirGroupSize());
        } else {
            groupNum = getEmptyGroupNumber();
        }
        if (groupNum == INVALID_GROUP_NUMBER) {
            // No free space
            rc = -1;
            return rc;
        }
        while(remain > 0 && limit >= 0) {
            // Whether it is used
            boolean used = isUsedGroupNumber(groupNum);
            if (!used) {
                // Mark as used
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);
                // Update chain sector as well
                int track = groupNum / basic.getSectorsPerTrackOnBasic();
                if (track != prevTrack) {
                    chainIndex++;
                }
                prevTrack = track;
                item.addChainGroupNumber(chainIndex, groupNum);

//                fileSize += sectorSize * basic.getSectorsPerGroup();
//                groups++;

                remain -= sectorSize * basic.getSectorsPerGroup();
            }
            // Set group numbers to be as continuous as possible
            groupNum++;
            limit--;
        }
        if (limit < 0) {
            // Infinite loop?
            rc = -2;
        }
        return rc;
    }

    /** Is the given group number part of the root directory? */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return groupNum < basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
    }

    /** Rename directory before creation */
    @Override
    public boolean renameOnMakingDirectory(String[] dirName) {
        if (dirName[0].equals("!")) {
            return false;
        }
        return true;
    }

    /** Prepare to create a directory (no-op for X-DOS) */
    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem<T> item) {
        return true;
    }

    /** Additional processing after creating a directory */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<T> item,
                                                 DiskBasicGroups groupItems,
                                                 DiskBasicDirItem<T> parentItem) throws IOException {
        if (groupItems.size() == 0) return;

        DiskBasicGroupItem group = groupItems.get(0);
        item.setStartGroup(0, group.group, basic.getSubDirGroupSize());

        DiskImageSector sector = basic.getSector(group.track, group.side, group.sectorStart);
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();
        if (buf == null) return;
        int bufOffset = 0;

        // Clear the beginning of sector
        sector.fill((byte) 0, basic.getDirStartPos(), 0);
        // Set sector name
        byte[] name = new byte[32];
        int nameLen = name.length;
        Arrays.fill(name, 0, nameLen, (byte) 0);
        item.getFileName(name, nameLen);
        sector.copy(name, nameLen);
        // Size is cleared
        item.setFileSize(0);

        // Create parent
        bufOffset += basic.getDirStartPos();
        DiskBasicDirItem<DirectoryXDos> newItem = basic.createDirItem(sector, basic.getDirStartPos(), buf, bufOffset);
        newItem.clearData();
        newItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);

        int parentGroup = INVALID_GROUP_NUMBER;
        if (parentItem != null) {
            // Parent is subdirectory
            parentGroup = parentItem.getStartGroup(0);
        }
        if (parentGroup == INVALID_GROUP_NUMBER) {
            // Root
            parentGroup = basic.getDirStartSector() - 1;
        }
        newItem.setStartGroup(0, parentGroup);
        newItem.setFileNamePlain("!");
    }

    /** Additional processing after formatting the disk */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // IPL
        DiskImageSector sector = null;
        sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnDir()));
            byte[] ipl = basic.getVariousStringParam("IPLString").getBytes();
            int len = ipl.length;
            if (len > 0) {
                if (len > 32) len = 32;
                basic.invertMemory(ipl, len);
                sector.copy(ipl, len);
            }
        }

        // FAT
        sector = basic.getSectorFromSectorPos(basic.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));
            // Number of sectors
            byte val = 0x4a; // | (basic.getSectorsPerTrackOnBasic());
            int tracks = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic();
            sector.fill(val, tracks, 0);
            // Track 0 is reserved
            sector.fill((byte) (basic.getParamDensity() >> 4), 1, 0);
            sector.fill((byte) 1, 1, 1);
            // Usage status
            int mapI = (1 << basic.getSectorsPerTrackOnBasic()) - 1;
            mapI <<= (16 - basic.getSectorsPerTrackOnBasic());
            byte[] map = new byte[2];
            map[0] = (byte) ((mapI >> 8) & 0xff);
            map[1] = (byte) (mapI & 0xff);

            sector.fill((byte) 0, 4, XDOS_FAT_START);	// Track 0
            for (int i = 2; i < tracks; i++) {
                sector.copy(map, 2, i * 2 + XDOS_FAT_START);
            }
        }

        // DIR
        for(int pos = basic.getDirStartSector(); pos <= basic.getDirEndSector(); pos++) {
            sector = basic.getSectorFromSectorPos(pos - 1);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.getFillCodeOnDir()));
                if (pos == basic.getDirStartSector()) {
                    sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()), basic.getDirStartPosOnRoot(), 0);
                }
            }
        }

        // Title label
        setIdentifiedData(data);

        return true;
    }

    /** Calculate data size in the last sector of a file */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<T> item,
                                        InputStream iStream,
                                        OutputStream oStream,
                                        byte[] sectorBuffer,
                                        int sectorOffset, int sectorSize,
                                        int remainSize) {
        if (item.needCheckEofCode()) {
            // Output up to one byte before the termination code ($00)
            byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
            for (int len = 0; len < remainSize; len++) {
                if (sectorBuffer[len] == eofCode) {
                    remainSize = len;
                    break;
                }
            }
        }
        return remainSize;
    }

    /** Delete the FAT entry for group 'groupNum' */
    @Override
    public void deleteGroupNumber(int groupNum) {
        // Mark as unused
        setGroupNumber(groupNum, 0);
    }

    /** Additional processing after a file is deleted */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<T> item) {
        // Mark chain sector as unused
        setGroupNumber(item.getStartGroup(0), 0);
        return true;
    }

    /** Retrieve IPL and volume name information */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // Title name at the beginning of DIR area
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.getDirStartSector() - 1);
        if (sector != null) {
            StringBuilder sb = new StringBuilder();
            basic.getCharCodes().convToString(sector.getSectorBuffer(), 0, VOLUME_NAME_LENGTH, sb, 0);
            data.setVolumeName(sb.toString());
            data.setVolumeNameMaxLength(VOLUME_NAME_LENGTH);
        }
    }

    /** Set IPL and volume name information */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        // Title name at the beginning of DIR area
        if (basic.getFormatType().hasVolumeName()) {
            DiskImageSector sector = basic.getSectorFromSectorPos(basic.getDirStartSector() - 1);
            if (sector != null) {
                byte[] dst = new byte[VOLUME_NAME_LENGTH + 1];
                int l = basic.getCharCodes().convToChars(data.getVolumeName(), dst, dst.length);
                if (l > 0) {
                    if (l > VOLUME_NAME_LENGTH) {
                        l = VOLUME_NAME_LENGTH;
                    }
                    sector.copy(dst, VOLUME_NAME_LENGTH);
                }
            }
        }
    }
}
