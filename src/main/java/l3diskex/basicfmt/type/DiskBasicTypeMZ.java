/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZ.DirectoryMz;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * MZ S-BASIC processing
 * <p>
 * DiskBasicParam Specific parameters
 *
 * <li>IPLString IPL in sector 1</li>
 * <li>VolumeString Volume number at the beginning of the directory</li>
 * <li>SpecialAttributes Special attributes Specify attribute values to be treated as Volume attribute</li>
 * @see "https://mzakd.cool.coocan.jp/starthp/mzt.html"
 * @see "https://old.sharpmz.org/mz-700/download/2z009man.pdf"
 */
public class DiskBasicTypeMZ extends DiskBasicTypeMZBase<DirectoryMz> {

    /** MZ usage sector */
    @Serdes(bigEndian = false)
    private static class MzFat {

        /** Volume number */
        @Element(sequence = 1)
        public byte volume;
        /** Offset Start cluster of data area */
        @Element(sequence = 2)
        public byte offset;
        /** Number of used clusters */
        @Element(sequence = 3)
        public short used;
        /** Total number of clusters */
        @Element(sequence = 4)
        public short all;
        /** Usage status */
        @Element(sequence = 5)
        public byte[] bits = new byte[0xf9];
        /** Number of sectors per cluster - 1 (251) */
        @Element(sequence = 6)
        public byte magnitude;

        public static final int SIZE = 252;
    }

    public static final int FORMAT_TYPE_MZ = 7;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_MZ;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMz> dir) {
        super.init(basic, fat, dir);
    }

    /** Set FAT position */
    @Override
    public void setGroupNumber(int num, int val) throws IOException {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        int[] pos = {num - dataStartGroup};
        if (pos[0] < 0) {
            return;
        }

        int[] mask = {0};
        calcUsedGroupPos(num, pos, mask);

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return;
        }
        // FAT has an unused/used table
        fatBuf.bit(pos[0], (byte) mask[0], val != 0, basic.isDataInverted());

        // Update the number of used final clusters in FAT
        MzFat b = new MzFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), b);
        int used_group = basic.invertAndOrderUint16(b.used);
        if (val != 0) {
            // Increase the number of final clusters when set
            used_group++;
        } else {
            // Decrease the number of final clusters when cleared
            used_group--;
        }
        basic.invertAndOrderUint16((short) used_group); // invert

        // TODO Write back
//looger.log(Level.TRACE, "DiskBasicTypeMZ::SetGroupNumber: g:%d v:%d pos:%d msk:%d used:%d".formatted(num, val, pos, mask, used_group));
    }

    /** Returns FAT offset */
    @Override
    public int getGroupNumber(int num) throws IOException {
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return DiskBasicType.INVALID_GROUP_NUMBER;
        }
        MzFat b = new MzFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), b);
        int offset = basic.invertUint8(b.offset) & 0xff; // invert
        if (offset > num) num = offset;
        return num;
    }

    /** Get position of used groups */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3) + 6;
    }

    /** Get next group number */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        int[] trackNum = new int[1], sideNum = new int[1], sectorNum = new int[1];
        basic.calcNumFromSectorPosForGroup(sectorPos, trackNum, sideNum, sectorNum, null, null);
        DiskImageSector sector = basic.getDisk().getSector(trackNum[0], sideNum[0], sectorNum[0]);
        if (sector == null) return 0;
        short nextSector = ByteUtil.readBeShort(sector.getSectorBuffer(), sector.getSectorSize() - 2); // TODO be?
        nextSector = (short) (basic.invertAndOrderUint16(nextSector) & 0xffff); // invert, convert to unsigned int
        return nextSector / basic.getSectorsPerGroup();
    }

    /** Returns a free FAT position */
    @Override
    public int getEmptyGroupNumber() throws IOException {
        int newNum = DiskBasicType.INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return newNum;
        }
        // Get the number of used final clusters in FAT
        MzFat b = new MzFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), b);
        int offset = basic.invertUint8(b.offset) & 0xff; // invert
        int usedGroup = basic.invertAndOrderUint16(b.used) & 0xffff;  // invert
        int allGroup = basic.invertAndOrderUint16(b.all) & 0xffff; // invert

        if (usedGroup < allGroup) {
            newNum = usedGroup;
        }
        if (newNum < offset) {
            newNum = offset;
        }
        return newNum;
    }

    /** Check FAT area */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        // FAT area
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return -1.0;
        }
        MzFat b = new MzFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), b);
        int offset = basic.invertUint8(b.offset) & 0xff; // invert
        int mag = basic.invertUint8(b.magnitude) & 0xff; // invert
        // Error if offset (1st byte) is less than 16
        if (offset < 16) {    // invert
            validRatio = -1.0;
        }
        if (validRatio >= 0.0) {
            dataStartGroup = offset;
        }

        // Error if cluster multiplier (255th byte) is 16 or more
        if (mag >= 16) {    // invert
            validRatio = -1.0;
        }
        if (validRatio >= 0.0) {
            // Sectors per cluster
            basic.setSectorsPerGroup(mag + 1);
            // Number of clusters
            basic.setFatEndGroup((basic.invertAndOrderUint16(b.all) & 0xffff) - 1); // invert
        } else {
            // Number of clusters is calculated from the number of tracks
            int tracks = basic.getTracksPerSideOnBasic();
            int sectors = 1;
            if (tracks > 44) {
                // In case of 2DD
                sectors = 2;
                tracks /= 2;
            }
            basic.setSectorsPerGroup(sectors);
            basic.setFatEndGroup(tracks * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() - 1);
        }

        return validRatio;
    }

    /** Assign root directory */
    @Override
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryMz> dirItem) throws IOException {
        boolean sts = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

        // Start group
        int sectorPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + startSector - 1;
        sectorPos /= basic.getSectorsPerGroup();
        dirItem.setStartGroup(0, sectorPos);

        return sts;
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        int used = 0;
        fatAvailability.clear();

        // FAT area
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return;
        }
        MzFat b = new MzFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), b);
        int usedGroups = basic.invertAndOrderUint16(b.used) & 0xffff;    // invert

        // Check if used
        int groups = 0;
        FatAvailability fatStatus;
        for (int gnum = 0; gnum <= basic.getFatEndGroup(); gnum++) {
            if (gnum < dataStartGroup) {
                used++;
                fatStatus = FAT_AVAIL_SYSTEM;
            } else if (!isUsedGroupNumber(gnum)) {
                groups++;
                fatStatus = FAT_AVAIL_FREE;
            } else {
                used++;
                fatStatus = FAT_AVAIL_USED;
            }
            fatAvailability.add(fatStatus, 0, 0);
        }

        // Groups of directory entries
        List<DiskBasicDirItem<DirectoryMz>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (DiskBasicDirItem<DirectoryMz> item : items) {
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

        // Update the number of used clusters
        if (wrote && usedGroups != used) {
            b.used = basic.invertAndOrderUint16((short) used);    // invert
        }

        fatAvailability.setFreeSize(fSize);
        fatAvailability.setFreeGroups(groups);

        // TODO write back
    }

    // Assumed to be defined in DiskBasicTypeMZBase
    @Override
    public boolean isUsedGroupNumber(int groupNum) {
        return false;
    }

    /** Allocate groups for data size */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryMz> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int[] fileSize = {0};
        int[] groups = {0};

        int rc = 0;
        int remain = dataSize;
        boolean isChain = item.needChainInData();
        boolean isBRD = item.getFileAttr().matchType(FILE_TYPE_RANDOM_MASK.getValue(), FILE_TYPE_RANDOM_MASK.getValue());
        int sectorSize = basic.getSectorSize();
        if (isChain) {
            sectorSize -= 2;
        }

        // Required number of groups
        int groupSize = ((dataSize - 1) / sectorSize / basic.getSectorsPerGroup()) + 1;
        if (isChain || isBRD) {
            groupSize = 1;
        }

        // Find a position where unused are continuous
        int[] groupStart = new int[1];
        int count = findContinuousArea(groupSize, groupStart);
        if (count < groupSize) {
            // Not enough free space
            rc = -1;
            return rc;
        }

        // Start group decided
        item.setStartGroup(fileUnitNum, groupStart[0]);

        if (isBRD) {
            // BRD random access
            // Allocate map area
            DiskImageSector sector = basic.getSectorFromGroup(groupStart[0]);
            if (sector == null) {
                // No sector?!
                rc = -1;
                return rc;
            }
            sector.fill(basic.invertUint8((byte) 0));    // invert

            ByteBuffer bb = ByteBuffer.wrap(sector.getSectorBuffer()).order(ByteOrder.BIG_ENDIAN);
            ShortBuffer brdMaps = bb.asShortBuffer();

            setGroupNumber(groupStart[0], 1);

            int brdPos = 0;

            do {
                // Search whether 16 contiguous sectors can be allocated
                groupSize = 16 / basic.getSectorsPerGroup();
                int bCount = findContinuousArea(groupSize, groupStart);
                if (bCount < groupSize) {
                    // Not enough free space
                    rc = -2;
                    return rc;
                }

                // Start sector
                int sectorPos = groupStart[0] * basic.getSectorsPerGroup();
                sectorPos = basic.invertAndOrderUint16((short) sectorPos);    // invert
                brdMaps.put(brdPos * 2, (short) sectorPos); // TODO chaek write back

                // Allocate area
                int blockRemain = 16 * basic.getSectorSize();
                int[] blockSize = {0};
                rc = allocateGroupsSub(item, groupStart[0], blockRemain, basic.getSectorSize(), groupItems[0], blockSize, groups);

                if (blockRemain >= remain) {
                    fileSize[0] += (((remain + 31) / 32) * 32);
                    break;
                }
                remain -= blockRemain;
                fileSize[0] += blockRemain;
                brdPos++;
            } while ((brdPos * 2) < basic.getSectorSize());

        } else {
            // Other than BRD

            // Allocate area
            rc = allocateGroupsSub(item, groupStart[0], remain, sectorSize, groupItems[0], fileSize, groups);
        }
        return rc;
    }

    /** Allocate groups and mark as used */
    @Override
    public int allocateGroupsSub(DiskBasicDirItem<DirectoryMz> item, int groupStart, int remain, int secSize, DiskBasicGroups groupItems, int[] fileSize, int[] groups) throws IOException {
        int rc = 0;
        int groupNum = groupStart;
        int prevGroup = 0;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // Whether it is used
            boolean usedGroup = isUsedGroupNumber(groupNum);
            if (!usedGroup) {
                if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
                    // Mark as used
                    basic.getNumsFromGroup(prevGroup, groupNum, secSize, remain, groupItems);
                    setGroupNumber(prevGroup, 1);
                    fileSize[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
                    groups[0]++;
                }
                remain -= (secSize * basic.getSectorsPerGroup());
                prevGroup = groupNum;
            }
            // Next group
            groupNum++;
            limit--;
        }
        if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
            // Mark as used
            basic.getNumsFromGroup(prevGroup, 0, secSize, remain, groupItems);
            setGroupNumber(prevGroup, 1);
            fileSize[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
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

    /** Whether it is the root directory */
    @Override
    public boolean isRootDirectory(int groupNum) {
        // If it is less than the offset, it is root
        return (basic.invertUint8((byte) fat.get(1)) & 0xFF) > groupNum;    // invert
    }

    /** Whether a subdirectory can be created */
    @Override
    public boolean canMakeDirectory() {
        return true;
    }

    /** Edit directory name before creating subdirectory */
    @Override
    public boolean renameOnMakingDirectory(String[] dirName) {
        // Directories starting with empty or "." cannot be created
        if (dirName[0].isEmpty() || dirName[0].startsWith(".")) {
            return false;
        }
        return true;
    }

    /** Individual processing after creating subdirectory */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryMz> item, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryMz> parentItem) throws IOException {
        if (groupItems.size() <= 0) return;

        // Create entries for volume number, current, and parent directory
        DiskBasicGroupItem gItem = groupItems.get(0);

        DiskImageSector sector = basic.getDisk().getSector(gItem.track, gItem.side, gItem.sectorStart);

        byte[] buf = sector.getSectorBuffer();
        int bufOffset = 0;
        DiskBasicDirItem<DirectoryMz> newItem = basic.createDirItem(sector, 0, buf, bufOffset);

        // Volume entry
        newItem.copyData(item.getRawData());
        newItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);

        bufOffset += newItem.getDataSize();
        newItem.setData(0, null, sector, 0, buf, bufOffset, null);

        // Current directory ('.')
        newItem.copyData(item.getRawData());
        newItem.setFileNamePlain(".");
        newItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);

        bufOffset += newItem.getDataSize();
        newItem.setData(0, null, sector, 0, buf, bufOffset, null);

        // Parent directory ('..')
        if (parentItem != null) {
            // Parent is subdirectory
            newItem.copyData(parentItem.getRawData());
        } else {
            // Parent is root
            newItem.copyData(item.getRawData());
            newItem.setStartGroup(0, 0);
        }
        newItem.setFileNamePlain("..");
        newItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);
    }

    /** Individual processing after filling sector data */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // IPL
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));    // invert
            byte[] buf = sector.getSectorBuffer();
            if (buf != null) {
                String iplString = basic.getVariousStringParam("IPLString");
                byte[] ipl = iplString.getBytes();
                int len = ipl.length;
                if (len > 0) {
                    if (len > 32) len = 32;
                    basic.invertMemory(ipl, len, buf); // Invert ipl data into buf
                }
            }
        }

        byte volumeNumber = 1;
        String volumeString = basic.getVariousStringParam("VolumeString");
        int volumeLength = volumeString.length();
        if (volumeLength > 0) {
            volumeNumber = (byte) volumeString.charAt(0);
        }

        // FAT area
        DiskBasicFatArea fats = fat.getDiskBasicFatArea();
        for (int n = 0; n < fats.size(); n++) {
            DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(n, 0);
            byte[] buf = fatBuf.getBuffer();
            int size = fatBuf.getSize();

            Arrays.fill(buf, basic.getFillCodeOnFAT());

            MzFat fDat = new MzFat();
            Serdes.Util.deserialize(new ByteArrayInputStream(buf), fDat);

            fDat.volume = volumeNumber;
            // From track 1 side 1
            fDat.offset = (byte) (getSectorPosFromNum(1, 1, 1) / basic.getSectorsPerGroup());
            // Number of used clusters
            fDat.used = fDat.offset;
            // Maximum number of clusters
            //fDat.all = basic.getFatEndGroup() + 1;
            fDat.all = (short) (basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup());
            // Number of sectors per group
            fDat.magnitude = (byte) (basic.getSectorsPerGroup() - 1);

            // invert
            basic.invertMemory(buf, size);

            // TODO write back fDat
        }

        // DIR area
        DirectoryMz ditV = new DirectoryMz(), ditM = new DirectoryMz();
        ditV.type = (byte) 0x80;    // VOL
        Arrays.fill(ditV.name, (byte) 0x0d);
        if (volumeLength > 0) {
            System.arraycopy(volumeString.getBytes(), 0, ditV.name, 0, volumeLength);
        }
        Arrays.fill(ditM.name, (byte) 0x0d);

        int[] trackNum = new int[1], sideNum = new int[1], sectorNum = new int[1];
        int index = 0;
        for (int sectorPos = basic.getDirStartSector(); sectorPos <= basic.getDirEndSector(); sectorPos++) {
            getNumFromSectorPos(sectorPos - 1, trackNum, sideNum, sectorNum);
            sector = basic.getDisk().getSector(trackNum[0], sideNum[0], sectorNum[0]);
            if (sector != null) {
                byte[] buf = sector.getSectorBuffer();
                int pos = 0;
                while (pos < sector.getSectorBufferSize()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    if (index == 0) {
                        // Set volume number at the beginning
                        Serdes.Util.serialize(ditV, baos);
                        System.arraycopy(baos.toByteArray(), 0, buf, pos, DirectoryMz.SIZE); // TODO this may not write back, we need setSectorBuffer method
                    } else {
                        Serdes.Util.serialize(ditM, baos);
                        System.arraycopy(baos.toByteArray(), 0, buf, pos, DirectoryMz.SIZE); // TODO this may not write back
                    }
                    pos += DirectoryMz.SIZE;
                    index++;
                }
                // invert
                basic.invertMemory(buf, sector.getSectorBufferSize());
            }
        }

        return true;
    }

    /** Data read/comparison processing */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryMz> item, InputStream iStream, OutputStream oStream,
                          byte[] sectorBuffer, int originalSectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        boolean needChain = item.needChainInData();

        int sectorSize = originalSectorSize;
        if (needChain) {
            // The final byte of the sector has the sector number for chaining
            sectorSize -= 2;
        }

        int size = remainSize < sectorSize ? remainSize : sectorSize;

        byte[] temp;
        if (oStream != null) {
            // Writing out
            temp = Arrays.copyOfRange(sectorBuffer, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            oStream.write(temp, 0, temp.length);
        }
        if (iStream != null) {
            // Read and compare
            temp = new byte[size];
            int bytesRead = iStream.read(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, temp.length, sectorBuffer, 0, size)) {
                // Data is different
                return -1;
            }
        }
        return size;
    }

    /** Data write process */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryMz> item, InputStream iStream, byte[] buffer, int originalSize,
                         int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needChain = item.needChainInData();

        int size = originalSize;
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

        // Write sector number for chain
        if (needChain) {
            int nextSector = groupNum * basic.getSectorsPerGroup();
            // Put the sector number where the next data exists
            if (sectorNum < sectorEnd) {
                nextSector++;
            } else {
                nextSector = (remain > size ? nextGroup * basic.getSectorsPerGroup() : 0);
            }
            nextSector = basic.invertAndOrderUint16((short) nextSector);
            ByteBuffer.wrap(buffer, size, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(0, (short) nextSector);
        }

        // Invert
        basic.invertMemory(buffer, originalSize);

        return len;
    }

    /** Processing after data write completion */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryMz> item) throws IOException {
        if (item == null || !item.getFileAttr().isDirectory()) return;

        // In case of directory, also change the volume name below
        DiskImageSector sector = basic.getSectorFromGroup(item.getStartGroup(0));
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();
        DiskBasicDirItem<DirectoryMz> newitem = basic.createDirItem(sector, 0, buf, 0);

        // Copy volume name
        if (!newitem.isSameFileName(item, basic.isCompareCaseInsensitive())) {
            newitem.copyFileName(item);
        }
    }

    /** Processing after file rename */
    @Override
    public void additionalProcessOnRenamedFile(DiskBasicDirItem<DirectoryMz> item) throws IOException {
        additionalProcessOnSavedFile(item);
    }

    /** Get attributes of IPL and managed area */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // Volume number of root directory
        DiskBasicDirItem<DirectoryMz> dItem = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(), FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
        if (dItem != null) {
            byte[] buf = new byte[8];
            buf[0] = 0;
            dItem.getFileName(buf, buf.length);
            data.setVolumeNumber(buf[0]);
        }
    }

    /** Set attributes of IPL and managed area */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
    }
}
