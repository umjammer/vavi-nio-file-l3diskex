//
// Copyright (c) Sasaji. All rights reserved.
//

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemM68FDOS.DirectoryM68FDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.ByteUtil;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * Sord M68 FDos (KDos) processing
 * <p>
 * DiskBasicParam Specific parameters
 *
 * <li>IPLString: IPL string in sector 1</li>
 */
public class DiskBasicTypeM68FDOS extends DiskBasicTypeMZBase<DirectoryM68FDos> {

    /** Usage status table */
    private final DiskBasicBitMLMap bitmap = new DiskBasicBitMLMap();

    public static final int FORMAT_TYPE_M68FDOS = 81;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_M68FDOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryM68FDos> dir) {
        super.init(basic, fat, dir);
    }

    /** Get position of used groups */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3);
    }

    /** Set FAT position */
    @Override
    public void setGroupNumber(int num, int val) {
        bitmap.modify(num, val != 0);
    }

    /** Returns FAT position */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /** Whether it is a used group number */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return bitmap.isSet(num);
    }

    /** Get next group number */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return num + 1;
    }

    /** Returns free FAT position */
    @Override
    public int getEmptyGroupNumber() {
        int found = DiskBasicType.INVALID_GROUP_NUMBER;
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup(); groupNum++) {
            if (!isUsedGroupNumber(groupNum)) {
                found = groupNum;
                break;
            }
        }
        return found;
    }

    /**
     * Check FAT area
     *
     * @param is_formatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 - 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double checkFat(boolean is_formatting) {
        double validRatio = 1.0;

        // FAT area
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) {
            return -1.0;
        }
        if (sector.get16(0, basic.isBigEndian()) != 0x003f) {
            validRatio = -1.0;
        }

        // Track 1
        sector = basic.getSector(1, 0, 1);
        if (sector == null) {
            return -1.0;
        }
        if (sector.find("FDDOS".getBytes(), 5) < 0 && sector.find("SORD".getBytes(), 4) < 0) {
            validRatio = -1.0;
        }

        // Usage area 2 sectors
        for (int i = 0; i < 2; i++) {
            sector = basic.getManagedSector(basic.getFatStartSector() + i);
            if (sector == null) {
                return -1.0;
            }
            bitmap.addBuffer(sector.getSectorBuffer(), sector.getSectorBufferSize());
        }

        // Final group number
        if (basic.getFatEndGroup() == 0) {
            basic.setFatEndGroup(basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);
        }

        return validRatio;
    }

    /**
     * Calculate sector list for root directory
     *
     * @param startSector Starting sector number of directory
     * @param endSector   Ending sector number of directory (unused in logic)
     * @param groupItems  [out] Sector list
     * @return true
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) {
        groupItems.clear();
        int dirSize = 0;
        int[] trackNum = {0};
        int[] sideNum = {0};
        int[] sectorNum = {1};
        int[] divNum = {0};
        int[] numOfDivs = {1};
        int secPos = startSector - 1;
        int endSectorPos = basic.getFatEndGroup() * basic.getSectorsPerGroup();
        int maxDirSize = endSectorPos * basic.getSectorSize();
        DiskImageSector sector = basic.getManagedSector(secPos, trackNum, sideNum, sectorNum, divNum, numOfDivs);
        if (sector == null) return false;
        while (dirSize < maxDirSize) {
            groupItems.add(secPos, 0, trackNum[0], sideNum[0], sectorNum[0], sectorNum[0], divNum[0], numOfDivs[0]);
            dirSize += (sector.getSectorSize() / numOfDivs[0]);

            // Get next sector number
            secPos = sector.get16(sector.getSectorSize() - 2, true) & 0xffff; // TODO -2
            if (secPos <= 0 || secPos > endSectorPos) break;
            sector = basic.getSectorFromSectorPos(secPos, trackNum, sideNum, divNum, numOfDivs);
            if (sector == null) break;
            sectorNum[0] = sector.getSectorNumber();
        }

        groupItems.setSize(dirSize);
        return true;
    }

    /**
     * Whether to end assigning if directory area size is reached
     *
     * @param pos         [in,out] Position of directory
     * @param size        [in,out] Sector size of directory
     * @param size_remain [in,out] Remaining size of directory
     * @return 0: Do not end, 1: Force unused, continue assign, -1: End assign at current group. Continue from next group, -2: Force end assign
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        return pos[0] + DirectoryM68FDos.SIZE > size[0] ? -1 : 0;
    }

    /**
     * Adjust position for each sector during directory assignment
     *
     * @return Adjusted directory position
     * @param pos Directory position
     */
    @Override
    public int adjustPositionAssigningDirectory(int pos) {
        return 0;
    }

    /** Get usable disk size */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1 - dataStartGroup;
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * Calculate remaining disk size
     *
     * @param wrote Whether after write operation
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        //int used = 0;
        fatAvailability.empty();

        // Check if used
        int groups = 0;
        FatAvailability fatStatus;
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup(); groupNum++) {
            if (groupNum < dataStartGroup) {
                //used++;
                fatStatus = FAT_AVAIL_SYSTEM;
            } else if (!isUsedGroupNumber(groupNum)) {
                groups++;
                fatStatus = FAT_AVAIL_FREE;
            } else {
                //used++;
                fatStatus = FAT_AVAIL_USED;
            }
            fatAvailability.add(fatStatus, 0, 0);
        }

        // Groups of directory entries
        List<DiskBasicDirItem<DirectoryM68FDos>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (DiskBasicDirItem<DirectoryM68FDos> item : items) {
                if (item == null || !item.isUsed()) continue;

                // Examine map of group numbers
                int groupCount = item.getGroupCount();
                if (groupCount > 0) {
                    DiskBasicGroupItem groupItem = item.getGroup(groupCount - 1);
                    int groupNum = groupItem.group;
                    if (groupNum <= basic.getFatEndGroup()) {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        int fSize = groups * basic.getSectorsPerGroup() * basic.getSectorSize();

        fatAvailability.setFreeSize(fSize);
        fatAvailability.setFreeGroups(groups);
    }

    /**
     * Prepare before saving file
     *
     * @param iStream  Stream buffer
     * @param fileSize [in,out] Output size
     * @param pItem    [in,out] Directory item with file name and attributes
     * @param nItem    [in,out] Allocated directory item
     * @param errInfo  [in,out] Error information
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryM68FDos> pItem, DiskBasicDirItem<DirectoryM68FDos> nItem, DiskBasicError errInfo) {
        return true;
    }

    /**
     * Allocate groups for data size
     *
     * @param fileUnitNum File number
     * @param item        [in,out] Directory item
     * @param size        Data size to allocate (bytes)
     * @param flags       New or append
     * @param groupItems  [out] Allocated sector list
     * @return >0: Normal, -1: No free space (before setting start group), -2: No free space (after setting start group)
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryM68FDos> item, int size, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int[] fileSize = {0};
        int[] groups = {0};

        int rc = 0;
        int remain = size;
        // Whether to put next sector number at the end of sector
        boolean isChain = item.needChainInData();
        int sectorSize = basic.getSectorSize();
        if (isChain) {
            sectorSize -= 2;
        }

        // Required number of groups
        int groupSize = ((size - 1) / sectorSize / basic.getSectorsPerGroup()) + 1;
        if (isChain) {
            groupSize = 1;
        }

        // Find a position where unused are continuous
        int[] groupStart = {DiskBasicType.INVALID_GROUP_NUMBER};
        int count = findContinuousArea(groupSize, groupStart);
        if (count < groupSize) {
            // Not enough free space
            rc = -1;
            return rc;
        }

        // Decide data start group
        item.setStartGroup(fileUnitNum, groupStart[0]);

        // Allocate area
        rc = allocateGroupsSub(item, groupStart[0], remain, sectorSize, groupItems[0], fileSize, groups);

        // Set number of allocated groups
        item.setGroupSize(groups[0]);
        // Set final group
        item.setExtraGroup(groupItems[0].last().group);

        return rc;
    }

    /** Allocate groups and mark as used */
    @Override
    public int allocateGroupsSub(DiskBasicDirItem<DirectoryM68FDos> item, int group_start, int remain, int sectorSize, DiskBasicGroups groupItems, int[] fileSize, int[] groups) {
        int rc = 0;
        int groupNum = group_start;
        int prevGroup = 0;

        //DiskBasicDirItemM68FDOS dItem = (DiskBasicDirItemM68FDOS)item;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // Whether it is used
            boolean usedGroup = isUsedGroupNumber(groupNum);
            if (!usedGroup) {
                if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
                                        // Mark as used
                    basic.getNumsFromGroup(prevGroup, groupNum, sectorSize, remain, groupItems);
                    setGroupNumber(prevGroup, 1);
                    fileSize[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
                    groups[0]++;
                }
                remain -= (sectorSize * basic.getSectorsPerGroup());
                prevGroup = groupNum;
            }
            // Next group
            groupNum++;
            limit--;
        }
        if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
            // Mark as used
            basic.getNumsFromGroup(prevGroup, 0, sectorSize, remain, groupItems);
            setGroupNumber(prevGroup, 1);
            fileSize[0] += basic.getSectorSize() * basic.getSectorsPerGroup();
            groups[0]++;
        }
        if (prevGroup > basic.getFatEndGroup()) {
            // File is overflowing
            rc = -2;
        } else if (limit < 0) {
            // Infinite loop?
            rc = -2;
        }
        return rc;
    }

    /**
     * Individual processing after filling sector data
     * Format Set FAT reserved
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        DiskImageSector sector;

        //
        // FAT area
        //
        int managedSectorPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        for (int s = basic.getFatStartSector() - 1, pos = 0; s < basic.getDirStartSector() - 1; s++, pos++) {
            sector = basic.getSectorFromSectorPos(managedSectorPos + s);
            if (sector == null) {
                return false;
            }
            sector.fill(basic.getFillCodeOnFAT());
            switch (pos) {
                case 0 -> {
                    // first sector
                    sector.copy(new byte[] {0x00, 0x3f}, 2);
                }
                case 1 -> {
                    // set bits

                    int n = managedSectorPos + basic.getDirEndSector();
                    int len = (n >> 3);
                    sector.fill((byte) 0xff, len, 0);
                    int mod = (n & 7);
                    sector.fill((byte) ((0xff00 >> mod) & 0xff), 1, len);
                }
                case 2 -> {
                    // set bits

                    int n = basic.getSidesPerDiskOnBasic() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic();
                    n -= (sector.getSectorSize() * (pos - 1) * 8);
                    if (n >= 0 && n < (sector.getSectorSize() * 8)) {
                        int len = (n >> 3);
                        sector.fill((byte) 0xff, -1, len);
                        int mod = (n & 7);
                        sector.fill((byte) (0x00ff >> mod), 1, len);
                    }
                }
                default -> {}
            }
        }

        //
        // DIR area
        //
        for (int s = basic.getDirStartSector() - 1, pos = 0; s < basic.getDirEndSector() - 1; s++, pos++) {
            sector = basic.getSectorFromSectorPos(managedSectorPos + s);
            if (sector == null) {
                return false;
            }
            sector.fill(basic.getFillCodeOnFAT());
            if (s < basic.getDirEndSector() - 2) {
                short next = basic.orderUint16((short) ((managedSectorPos + s + 1) & 0xffff));
                sector.copy(ByteUtil.getBeBytes(next), 2, basic.getSectorSize() - 2);
            }
            if (pos == 0) {
                // One entry
                sector.copy(new byte[] {
                        (byte) 0x62, (byte) 0x56, (byte) 0xc1, (byte) 0xc0, (byte) 0xa7, (byte) 0x30, (byte) 0xf9, (byte) 0x80,
                        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x5c, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                        (byte) 0x00, (byte) 0x0d, (byte) 0xa4}, 19);
            }
        }

        //
        sector = basic.getSectorFromSectorPos(managedSectorPos);
        if (sector == null) {
            return false;
        }
        sector.copy("\u00f3\u0076NOT FDDOS MEDIA\u0000".getBytes(), 18);

        return true;
    }

    /**
     * Data read/comparison processing
     *
     * @param fileUnitNum  File number
     * @param item         Directory item
     * @param iStream      [in,out] Input stream. Used during verify. null when reading data.
     * @param oStream      [in,out] Output destination. Used when reading data. null during verify.
     * @param sectorBuffer Sector buffer
     * @param sectorSize   Buffer size
     * @param remainSize   Remaining size
     * @param sectorNum    Sector number
     * @param sectorEnd    Final sector number
     * @return >=0: Processed size, -1: Comparison mismatch, -2: Sector is invalid
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryM68FDos> item, InputStream iStream, OutputStream oStream,
                          byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        boolean needChain = item.needChainInData();

        if (needChain) {
            // The final byte of the sector has the sector number for chaining
            sectorSize -= 2;
        }

        int size = remainSize < sectorSize ? remainSize : sectorSize;

        byte[] temp;
        if (oStream != null) {
            // Write to output stream
            temp = Arrays.copyOfRange(sectorBuffer, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);
            oStream.write(temp, 0, size);
        }
        if (iStream != null) {
            // Read from input stream and compare
            temp = new byte[size];
            iStream.readNBytes(temp, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, temp.length, sectorBuffer, 0, size)) {
                // Data is different
                return -1;
            }
        }
        return size;
    }

    /**
     * Data writing processing
     *
     * @param item       Directory item
     * @param iStream    Stream data
     * @param buffer     [out] Buffer to write within sector
     * @param size       Buffer size to write
     * @param remain     Remaining data size
     * @param sectorNum  Sector number
     * @param groupNum   Current group number
     * @param nextGroup  Next group number
     * @param sectorEnd  Final sector number
     * @param seqNum     Serial number (0...)
     * @return Number of bytes written
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryM68FDos> item, InputStream iStream, byte[] buffer, int size, int remain,
                         int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needChain = item.needChainInData();

        int len = 0;
        if (needChain) {
            size -= 2;
        }

        if (remain <= size) {
            // Few left
            if (remain < 0) remain = 0;
            if (remain > 0) {
                iStream.readNBytes(buffer, 0, remain);
            }
            if (size > remain) {
                // Remaining buffer is zero-suppressed
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // Continuous
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        // Write next sector number
        if (needChain) {
            int next_sector = nextGroup * basic.getSectorsPerGroup();
            if (next_sector >= 0) {
                // bigendien
                buffer[size] = (byte) ((next_sector >> 8) & 0xff);
                buffer[size + 1] = (byte) (next_sector & 0xff);
            }
        }

        return len;
    }

    /** Processing after data writing completion */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryM68FDos> item) {
//        DiskBasicDirItemM68FDOS dItem = (DiskBasicDirItemM68FDOS) item;
//        dItem.setUnknownData();
    }

    /** Get attributes of IPL and managed area */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
    }

    /** Set attributes of IPL and managed area */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
    }
}