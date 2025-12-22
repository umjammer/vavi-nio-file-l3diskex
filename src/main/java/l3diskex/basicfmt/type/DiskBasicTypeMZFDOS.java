package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS.DirectoryMzFDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/**
 * MZ Floppy DOS processing
 * <p>
 * DiskBasicParam Specific parameters
 * <li>IPLString: IPL in sector 1</li>
 */
public class DiskBasicTypeMZFDOS extends DiskBasicTypeMZBase<DirectoryMzFDos> {

    /** Usage status sector */
    @Serdes(bigEndian = false)
    static class MzFDosFat {

        @Element(sequence = 1)
        public byte[] reserved1 = new byte[32];
        // TODO: unknown
        @Element(sequence = 2)
        public byte sides;
        @Element(sequence = 3)
        public byte volumeNum;
        @Element(sequence = 4)
        public byte[] sign = new byte[17];
        // sector
        @Element(sequence = 5)
        public short emptyStart;
        @Element(sequence = 6)
        public byte[] map = new byte[203];
    }

    public static final int FORMAT_TYPE_MZ_FDOS = 73;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_MZ_FDOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMzFDos> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Get the position of used groups
     */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3);
    }

    /**
     * Get next group number
     * <p>
     * The next track and sector number are at the end of the sector
     */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        int[] trackNum = {0}, sideNum = {0}, sectorNum = {0};
        basic.calcNumFromSectorPosForGroup(sectorPos, trackNum, sideNum, sectorNum, null, null);
        DiskImageSector sector = basic.getSector(trackNum[0], sideNum[0], sectorNum[0]);
        if (sector == null) return 0;

        byte[] b = sector.getSectorBuffer();
        int s = sector.getSectorSize();
        byte nextTrack = basic.invertUint8(b[s - 2]);
        byte nextSector = basic.invertUint8(b[s - 1]);
        return basic.calcSectorPosFromNumTForGroup(nextTrack, nextSector);
    }

    /**
     * Check FAT area
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 - 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        // FAT area
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) {
            return -1.0;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return -1.0;
        }
        MzFDosFat f = new MzFDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);

        byte sides = basic.invertUint8(f.sides);
        if (sides == 0 || sides >= basic.getTracksPerSide()) {
            validRatio = -1.0;
        }
        //dataStartGroup = startTrack * basic.getSectorsPerTrackOnBasic();
        // Final group number
        if (basic.getFatEndGroup() == 0) {
            basic.setFatEndGroup(basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);
        }
        return validRatio;
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
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryMzFDos> pItem, DiskBasicDirItem<DirectoryMzFDos> nItem, DiskBasicError errInfo) throws IOException {
        // Allocate sector for chain
        int groupNum = getEmptyGroupNumber();
        if (groupNum == INVALID_GROUP_NUMBER) {
            return false;
        }
        // Sector
        DiskImageSector sector = basic.getSectorFromGroup(groupNum);
        if (sector == null) {
            return false;
        }
        byte[] c = sector.getSectorBuffer();
        if (c == null) {
            return false;
        }
        sector.fill(basic.invertUint8((byte) 0));
        // Set sector in chain information
        nItem.setChainSector(sector, c, null);

        // Set start group
        nItem.setStartGroup(0, groupNum);

        // Reserve sector
        setGroupNumber(groupNum, 1);
        DiskBasicDirItemMZFDOS ditem = (DiskBasicDirItemMZFDOS) nItem;
        ditem.setChainUsedSector(groupNum, true);

        return true;
    }

    /**
     * Allocate groups for the data size
     *
     * @param fileUnitNum File number
     * @param item        [in,out] Directory item
     * @param dataSize    Data size to allocate (bytes)
     * @param flags       New or append
     * @param groupItems  [out] List of allocated sectors
     * @return >0: Normal, -1: No free space (before setting start group), -2: No free space (after setting start group)
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int[] fileSize = {0};
        int[] groups = {0};

        int rc = 0;
        //int groupNum = 0;
        int remain = dataSize;
        boolean isChain = item.needChainInData();
        int sectorSize = basic.getSectorSize();
        if (isChain) {
            sectorSize -= 2;
        }

        // Required number of groups
        int groupSize = ((dataSize - 1) / sectorSize / basic.getSectorsPerGroup()) + 1;
        if (isChain) {
            groupSize = 1;
        }

        // Find a position where unused are continuous
        int[] groupStart = {0};
        int count = findContinuousArea(groupSize, groupStart);
        if (count < groupSize) {
            // Not enough free space
            rc = -1;
            return rc;
        }

        // Decide data start group
        DiskBasicDirItemMZFDOS dItem = (DiskBasicDirItemMZFDOS) item;
        dItem.setDataGroup(groupStart[0]);
        // Sequence number
        dItem.assignSeqNumber();

        // Allocate area
        rc = allocateGroupsSub(item, groupStart[0], remain, sectorSize, groupItems[0], fileSize, groups);

        // Set number of allocated groups
        dItem.setGroupSize(groups[0] + 1);

        // Update FAT free position
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector != null) {
            byte[] sectorBuffer = sector.getSectorBuffer();
            if (sectorBuffer != null) {
                MzFDosFat f = new MzFDosFat();
                Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f); // TODO write back
                int groupEnd = groupItems[0].last().group + 1;
                int emptyStart = basic.invertAndOrderUint16(f.emptyStart);
                if (emptyStart < groupEnd) {
                    f.emptyStart = basic.invertAndOrderUint16((short) groupEnd);
                }
            }
        }

        return rc;
    }

    /**
     * Allocate groups and mark as used
     */
    @Override
    public int allocateGroupsSub(DiskBasicDirItem<DirectoryMzFDos> item, int groupStart, int remain, int secSize, DiskBasicGroups groupItems, int[] fileSize, int[] groups) throws IOException {
        int rc = 0;
        int groupNum = groupStart;
        int prevGroup = 0;

        DiskBasicDirItemMZFDOS dItem = (DiskBasicDirItemMZFDOS) item;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // Whether it is used
            boolean usedGroup = isUsedGroupNumber(groupNum);
            if (!usedGroup) {
                if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
                    // Mark as used
                    basic.getNumsFromGroup(prevGroup, groupNum, secSize, remain, groupItems);
                    setGroupNumber(prevGroup, 1);
                    dItem.setChainUsedSector(prevGroup, true);
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
            dItem.setChainUsedSector(prevGroup, true);
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

    /**
     * Data read/comparison processing
     *
     * @param fileUnitNum  File number
     * @param item         Directory item
     * @param iStream      [in,out] Input stream. Used during verify. {@code null} when reading data.
     * @param oStream      [in,out] Output destination. Used when reading data. {@code null} during verify.
     * @param sectorBuffer Sector buffer
     * @param sectorSize   Buffer size
     * @param remainSize   Remaining size
     * @param sectorNum    Sector number
     * @param sectorEnd    Final sector number
     * @return >=0: Processed size, -1: Comparison mismatch, -2: Sector is invalid
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryMzFDos> item, InputStream iStream, OutputStream oStream,
                          byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        boolean needChain = item.needChainInData();

        if (needChain) {
            // The final byte of the sector has the sector number for chaining
            sectorSize -= 2;
        }

        int size = remainSize < sectorSize ? remainSize : sectorSize;

        byte[] temp;
        if (oStream != null) {
            temp = Arrays.copyOfRange(sectorBuffer, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            oStream.write(temp, 0, temp.length);
        }
        if (iStream != null) {
            // Read and compare
            temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, temp.length, sectorBuffer, 0, temp.length)) {
                // Data is different
                return -1;
            }
        }
        return size;
    }

    /**
     * Individual processing after filling sector data
     * Format Set FAT reserved
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        //
        // FAT area
        //
        sector = basic.getSectorFromSectorPos(basic.getFatStartSector() - 1);
        if (sector == null) {
            return false;
        }
        sector.fill(basic.getFillCodeOnFAT());

        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return false;
        }
        int size = sector.getSectorBufferSize();

        MzFDosFat fdat = new MzFDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(buf), fdat);

        for (int i = 0; i < 32; i++) {
            fdat.reserved1[i] = (short) 0;
        }

        fdat.sides = (byte) basic.getSidesPerDiskOnBasic();

        fdat.volumeNum = (byte) data.getVolumeNumber();

        Arrays.fill(fdat.sign, 0, fdat.sign.length, basic.getDirSpaceCode());
        byte[] volumeName = data.getVolumeName().getBytes();
        if (volumeName.length > 0) {
            int len = volumeName.length;
            if (len >=  fdat.sign.length) len =  fdat.sign.length - 1;
            System.arraycopy(volumeName, 0, fdat.sign, 0, len);
        }

        // Mark system area as used
        int groupNumStart = 0;
        int groupNumEnd = basic.getDirEndSector();

        fdat.emptyStart = basic.orderUint16((short) groupNumEnd);
        for (int groupNum = groupNumStart; groupNum < groupNumEnd; groupNum++) {
            int[] pos = {groupNum};
            int[] mask = {0};
            calcUsedGroupPos(groupNum, pos, mask);
            fdat.map[pos[0]] = (byte) (fdat.map[pos[0]] | mask[0]);
        }
        // TODO serialize

        // invert
        basic.invertMemory(buf, size);

        //
        // Initialize the FAT area used by MZ DISK BASIC as used
        //
        sector = basic.getSectorFromSectorPos(15);
        if (sector != null) {
            sector.fill(basic.invertUint8((byte) 0xff));
            sector.fill(basic.invertUint8((byte) 0), 4, 2);
        }
        sector = basic.getSectorFromSectorPos(13);
        if (sector != null) {
            sector.fill(basic.invertUint8((byte) 0xff));
            sector.fill(basic.invertUint8((byte) 0), 1, 0);
        }

        //
        // DIR area
        //
        int[] trackNum = {0}, sideNum = {0}, sectorNum = {0};
        //int index = 0;
        for (int sectorPos = basic.getDirStartSector(); sectorPos <= basic.getDirEndSector(); sectorPos++) {
            getNumFromSectorPos(sectorPos - 1, trackNum, sideNum, sectorNum);
            sector = basic.getSector(trackNum[0], sideNum[0], sectorNum[0]);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));
            }
        }

        return true;
    }

    /**
     * Data writing processing
     *
     * @param item       Directory item
     * @param iStream    Stream data
     * @param buffer     [out] Buffer to write to in sector
     * @param size       Buffer size to write to
     * @param remain     Remaining data size
     * @param sectorNum  Sector number
     * @param groupNum   Current group number
     * @param nextGroup  Next group number
     * @param sectorEnd  Final sector number
     * @param seqNum     Serial number (0...)
     * @return Number of bytes written
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryMzFDos> item, InputStream iStream, byte[] buffer, int size, int remain,
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

        // Write track and sector number for chain
        if (needChain) {
            int nextSector = groupNum * basic.getSectorsPerGroup();
            // Put the sector number where the next data exists
            if (sectorNum < sectorEnd) {
                nextSector++;
            } else {
                nextSector = (remain > size ? nextGroup * basic.getSectorsPerGroup() : 0);
            }
            nextSector /= basic.getSectorsPerGroup();
            if (nextSector > 0) {
                int[] track = {0};
                int[] side = {0};
                int[] sector = {0};
                basic.calcNumFromSectorPosForGroup(nextSector, track, side, sector, null, null);
                track[0] *= basic.getSidesPerDiskOnBasic();
                track[0] += basic.getReversedSideNumber(side[0]);
                buffer[size] = basic.invertUint8((byte) track[0]);
                buffer[size + 1] = basic.invertUint8((byte) sector[0]);
            } else {
                buffer[size] = basic.invertUint8((byte) 0);
                buffer[size + 1] = basic.invertUint8((byte) 0);
            }
        }

        // Invert
        basic.invertMemory(buffer, size);

        return len;
    }

    /**
     * Processing after completion of data writing
     */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryMzFDos> item) {
        DiskBasicDirItemMZFDOS dItem = (DiskBasicDirItemMZFDOS) item;
        dItem.setUnknownData();
    }

    /**
     * Get attributes of IPL and managed area
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) {
            return;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return;
        }
        MzFDosFat f = new MzFDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);
        // Volume number
        data.setVolumeNumber(basic.invertUint8(f.volumeNum));
        // Sign
        byte[] sign = new byte[17];
        basic.invertMemory(f.sign, 17, sign);
        data.setVolumeName(new String(sign));
        data.setVolumeNameMaxLength(17);
    }

    /**
     * Set attributes of IPL and managed area
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) {
            return;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return;
        }
        MzFDosFat f = new MzFDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);

        // Volume number
        f.volumeNum = basic.invertUint8((byte) data.getVolumeNumber());
        // Sign
        Arrays.fill(f.sign, basic.invertUint8(basic.getDirSpaceCode()));
        byte[] volumeName = data.getVolumeName().getBytes();
        if (volumeName.length > 0) {
            int len = volumeName.length;
            if (len >= f.sign.length) len = f.sign.length - 1;
            System.arraycopy(volumeName, 0, f.sign, 0, f.sign.length);
        }
        // TODO deserialize
    }
}