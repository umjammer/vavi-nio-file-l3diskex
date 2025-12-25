///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.AppleDosChain;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.AppleDosPointer;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.DirectoryAppleDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.AppleDosChain.APLEDOS_TRACK_LIST_MAX;


/**
 * Processing for Apple DOS 3.x
 *
 * DiskBasicParam
 * <li>DirStartPositionOnSector Starting position of directory entry</li>
 */
public class DiskBasicTypeAppleDOS extends DiskBasicType<DirectoryAppleDos> {

    /** Apple DOS Volume table of contents (VToc) */
    @Serdes
    static class AppleDosVToc {

        @Element(sequence = 1)
        byte reserved0;
        @Element(sequence = 2)
        byte dirStartTrack;
        @Element(sequence = 3)
        byte dirStartSector;
        @Element(sequence = 4)
        byte releaseNumber;
        @Element(sequence = 5)
        byte[] reserved1 = new byte[2];
        @Element(sequence = 6)
        byte volumeNumber;
        @Element(sequence = 7)
        byte[] reserved2 = new byte[32];
        // max number of trk/sec list (normally 122)
        @Element(sequence = 8)
        byte chainSize;
        @Element(sequence = 9)
        byte[] reserved3 = new byte[8];
        @Element(sequence = 10)
        short[] trackBitMask = new short[] {0, 0}; // big endien
        @Element(sequence = 11)
        byte tracksPerDisk;
        @Element(sequence = 12)
        byte sectorsPerTrack;
        @Element(sequence = 13, bigEndian = "false")
        short sectorSize; // little endien
        @Element(sequence = 14)
        short[][] trackMap = new short[50][2]; // big endien
    }

    /** */
    private AppleDosVToc appleDosVToc;

    public static final int FORMAT_TYPE_APLEDOS = 15;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_APLEDOS;
    }

    /** */
    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryAppleDos> dir) {
        super.init(basic, fat, dir);

        if (basic.getSectorsPerGroup() <= 0) {
            basic.setGroupsPerTrack(basic.getGroupsPerSector() * basic.getSectorsPerTrackOnBasic());
        }
    }

    /** Set track map mask */
    private void setTrackMapMask(int val) {
        appleDosVToc.trackBitMask[0] = (short) (val & 0xffff);
        val >>= 16;
        appleDosVToc.trackBitMask[1] = (short) (val & 0xffff);
    }

    /** Modify a bit in the track map */
    private void modifyTrackMap(int trackNum, int sectorNum, boolean use) {
        int map = getTrackMap(trackNum);
        if (use) {
            map &= ~(1 << sectorNum);
        } else {
            map |= (1 << sectorNum);
        }
        setTrackMap(trackNum, map);
    }

    /** Is it free? */
    private boolean isFreeTrackMap(int trackNum, int sectorNum) {
        int map = getTrackMap(trackNum);
        return ((map & (1L << sectorNum)) != 0);
    }

    /** Set track map */
    private void setTrackMap(int trackNum, int val) {
        val <<= (16 - basic.getSectorsPerTrackOnBasic());
        appleDosVToc.trackMap[trackNum][0] = (short) (val & 0xffff);
        val >>= 16;
        appleDosVToc.trackMap[trackNum][1] = (short) (val & 0xffff);
    }

    /** Get track map */
    private int getTrackMap(int trackNum) {
        int val = appleDosVToc.trackMap[trackNum][1] & 0xffff;
        val <<= 16;
        val |= appleDosVToc.trackMap[trackNum][0] & 0xffff;
        val >>>= (16 - basic.getSectorsPerTrackOnBasic());
        return val;
    }

    /** Allocate a chain sector */
    private int allocChainSector(int index, DiskBasicDirItem<?> item, int currentGroup) throws IOException {
        int groupNum = (index == 0) ? getEmptyGroupNumber() : getNextEmptyGroupNumber(currentGroup);
        if (groupNum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // Sector
        DiskImageSector sector = basic.getSectorFromGroup(groupNum);
        if (sector == null) {
            return INVALID_GROUP_NUMBER;
        }
        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return INVALID_GROUP_NUMBER;
        }
        sector.fill((byte) 0);

        // Set sector in chain information
        item.setChainSector(sector, groupNum, buf, null);

        // Set start group
        if (index == 0) {
            item.setStartGroup(0, groupNum, 1);
        }

        // Reserve sector
        setGroupNumber(groupNum, 1);

        return groupNum;
    }

    /** Set FAT position */
    @Override
    public void setGroupNumber(int num, int val) {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        getNumFromSectorPosS(num, trackNum, sectorNum);
        trackNum[0] -= basic.getTrackNumberBaseOnDisk();
        sectorNum[0] -= basic.getSectorNumberBase();
        modifyTrackMap(trackNum[0], sectorNum[0], val != 0);
    }

    /** Return FAT offset */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /** Is the group number used? */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /** Get the next group number */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return INVALID_GROUP_NUMBER;
    }

    /** Return an empty FAT position */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;

        int manageTrackNum = basic.getManagedTrackNumber();
        int startTrack = 0;
        int endTrack = 0;
        int direction = 1;

        for (int i = 0; i < 2 && newNum == INVALID_GROUP_NUMBER; i++) {
            switch (i) {
                case 0:
                    // From track numbers larger than the managed area
                    startTrack = manageTrackNum + 1;
                    endTrack = basic.getTracksPerSideOnBasic();
                    direction = 1;
                    break;
                case 1:
                    // To track numbers smaller than the managed area
                    startTrack = manageTrackNum - 1;
                    endTrack = 2;
                    direction = -1;
                    break;
            }

            for (int sector = basic.getSectorsPerTrackOnBasic() - 1; sector >= 0; sector--) {
                for (int track = startTrack; track != endTrack && newNum == INVALID_GROUP_NUMBER; track += direction) {
                    // Prioritize searching the end of each track
                    if (isFreeTrackMap(track, sector)) {
                        // Free space found
                        newNum = track * basic.getSectorsPerTrackOnBasic() + sector;
                        break;
                    }
                }
            }
        }

        return newNum;
    }

    /** Return the next empty FAT position */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        // Candidate for the next empty position
        int newNum = INVALID_GROUP_NUMBER;

        int manageTrackNum = basic.getManagedTrackNumber();
        int currentTrack = currentGroup / basic.getSectorsPerTrackOnBasic();
        int startTrack = 0;
        int endTrack = 0;
        int direction = 1;

        for (int i = 0; i < 2 && newNum == INVALID_GROUP_NUMBER; i++) {
            switch (i) {
                case 0:
                    // From track numbers larger than the managed area
                    startTrack = currentTrack;
                    endTrack = basic.getTracksPerSideOnBasic();
                    direction = 1;
                    break;
                case 1:
                    // To track numbers smaller than the managed area
                    startTrack = currentTrack;
                    endTrack = 2;
                    direction = -1;
                    break;
            }

            for (int track = startTrack; track != endTrack && newNum == INVALID_GROUP_NUMBER; track += direction) {
                if (track == manageTrackNum) {
                    track += direction;
                }
                for (int sector = basic.getSectorsPerTrackOnBasic() - 1; sector >= 0; sector--) {
                    if (isFreeTrackMap(track, sector)) {
                        // Free space found
                        newNum = track * basic.getSectorsPerTrackOnBasic() + sector;
                        break;
                    }
                }
            }
        }

        return newNum;
    }

    /** Check FAT area */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        // VToc area
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            return -1.0;
        }
        AppleDosVToc vToc = new AppleDosVToc();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vToc);

        this.appleDosVToc = vToc;

        if (vToc.tracksPerDisk == 0 || vToc.sectorsPerTrack == 0) {
            return -1.0;
        }

        if (vToc.dirStartTrack < 3) {
            return -1.0;
        }

        // Each parameter
        basic.setSectorsPerGroup(1);
        basic.setTracksPerSideOnBasic(vToc.tracksPerDisk);
        basic.setSectorsPerTrackOnBasic(vToc.sectorsPerTrack);
        basic.setFatEndGroup(vToc.tracksPerDisk * vToc.sectorsPerTrack - 1);

        basic.setManagedTrackNumber(vToc.dirStartTrack);
        basic.setDirStartSector(vToc.dirStartSector);

        // Check directory
        // Normally proceeds from sector 15 to 1
        //int dirCount = 0;
        int dirStartLSector = basic.getDirStartSector();
        int dirEndLSector = 1;

        sector = basic.getManagedSector(dirStartLSector);
        for (int lSectorPos = dirStartLSector; lSectorPos >= dirEndLSector; lSectorPos--) {
            if (sector == null) {
                validRatio = -1.0;
                break;
            }

            AppleDosPointer p = new AppleDosPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), p);
            //dirCount++;

            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(
                    p.nextTrack + basic.getTrackNumberBaseOnDisk(), p.nextSector + basic.getSectorNumberBase()));
        }

        return validRatio;
    }

    /** Get each parameter from disk and calculate necessary parameters */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0;

        if (this.appleDosVToc == null) {
            DiskImageSector sector = basic.getManagedSector(0);
            byte[] b = sector.getSectorBuffer();
            AppleDosVToc vToc = new AppleDosVToc();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), vToc);
            this.appleDosVToc = vToc;

            // Each parameter
            basic.setTracksPerSideOnBasic(vToc.tracksPerDisk);
            basic.setSectorsPerTrackOnBasic(vToc.sectorsPerTrack);
            basic.setFatEndGroup((vToc.tracksPerDisk + 1) * vToc.sectorsPerTrack - 1);

            basic.setManagedTrackNumber(vToc.dirStartTrack);
            basic.setDirStartSector(vToc.dirStartSector);
        }

        //logger.log(Level.INFO, "AppleDos: vtoc.tracksPerDisk: %d".formatted((int) appleDosVToc.tracksPerDisk.get());
        //logger.log(Level.INFO, "AppleDos: vtoc.sectorsPerTrack: %d".formatted((int) appleDosVToc.sectorsPerTrack.get());

        return 1.0;
    }

    /** Calculate the sector list for the root directory */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;

        groupItems.clear();

        // Follow the directory chain
        int dirSize = 0;
        int limit = basic.getFatEndGroup() + 1;
        int[] trackNum = {0};
        int[] sideNum = {0};
        int sectorNum = 1;
        // Start sector
        DiskImageSector sector = basic.getManagedSector(startSector, trackNum, sideNum);
        while (limit >= 0) {
            if (sector == null) {
                valid = false;
                break;
            }
            sectorNum = sector.getSectorNumber();

            byte[] buffer = sector.getSectorBuffer();
            if (buffer == null) {
                valid = false;
                break;
            }

            groupItems.add(0, 0, trackNum[0], sideNum[0], sectorNum, sectorNum);

            dirSize += sector.getSectorSize();

            AppleDosPointer p = new AppleDosPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(buffer), p);

            // No next sector
            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            limit--;

            // Get the next sector
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(
                    p.nextTrack + basic.getTrackNumberBaseOnDisk(), p.nextSector + basic.getSectorNumberBase()), trackNum, sideNum);
        }
        groupItems.setSize(dirSize);

        if (limit < 0) {
            valid = false;
        }
        return valid;
    }

    /** Get usable disk size */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize() / basic.getGroupsPerSector();
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        // BITMAP table on VToc area

        // get BITMAP mask (value is often invalidate)
        //int mask = appleDosVToc.trackBitMask[1];
        //mask <<= 16;
        //mask |= appleDosVToc.trackBitMask[0];
        int managedTrackNum = basic.getManagedTrackNumber();

        // check BITMAP
        for (int track = 0; track < basic.getTracksPerSideOnBasic(); track++) {
            int useMap = getTrackMap(track);

            for (int sector = 0; sector < basic.getSectorsPerTrackOnBasic(); sector++) {
                if (track < 3 || track == managedTrackNum) {
                    fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
                } else if ((useMap & (1L << sector)) != 0) {
                    fatAvailability.add(FAT_AVAIL_FREE, basic.getSectorSize(), 1);
                } else {
                    fatAvailability.add(FAT_AVAIL_USED, 0, 0);
                }
            }
        }

        //freeDiskSize = (int) fSize;
        //freeGroups = (int) groups;
    }

    /** Prepare before saving file */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryAppleDos> pItem, DiskBasicDirItem<DirectoryAppleDos> nItem, DiskBasicError errInfo) throws IOException {
        // Clear chain sector (Clear chain sector)
        nItem.clearChainSector(null);

        return true;
    }

    /** Allocate groups for the data size */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryAppleDos> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        //logger.log(Level.TRACE, "DiskBasicTypeAppleDOS::AllocateGroups {");

        //int fileSize = 0;
        int groups = 0;

        int rc = 0;
        int sectorSize = basic.getSectorSize();
        int remain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        int chainIndex = 0;
        int groupNum = INVALID_GROUP_NUMBER;
        while (remain > 0 && limit >= 0) {
            // Allocate chain sector
            if ((chainIndex % APLEDOS_TRACK_LIST_MAX) == 0) {
                groupNum = allocChainSector(chainIndex, item, groupNum);
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // Error
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }
            }

            // Search for free space
            groupNum = getNextEmptyGroupNumber(groupNum);
            if (groupNum == INVALID_GROUP_NUMBER) {
                // No free space
                rc = groups > 0 ? -2 : -1;
                return rc;
            }

            // Mark as used
            basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
            setGroupNumber(groupNum, 1);

            // Update chain sector
            item.addChainGroupNumber(chainIndex, groupNum);
            chainIndex++;

            //file_size += sectorSize;
            groups++;

            remain -= sectorSize;

            limit--;
        }
        if (limit < 0) {
            // Infinite loop?
            rc = -2;
        }

        //logger.log(Level.TRACE, "rc: %d }".formatted(rc));
        return rc;
    }

//    /** Chain groups */
//	  public int chainGroups(int groupNum, int appendGroupNum) { return 0; }

    /** Get start sector number from group number */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    /** Get end sector number from group number */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return groupNum;
    }

    /** Get track, side, sector numbers from sector position */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] divNums) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int groupsPerSector = basic.getGroupsPerSector();
        int groupsPerTrack = basic.getGroupsPerTrack();

        int trackSideNum = sectorPos / groupsPerTrack;
        if (selectedSide >= 0) {
            // 1S
            trackNum[0] = trackSideNum;
            sideNum[0] = selectedSide;
        } else {
            // 2D, 2HD
            trackNum[0] = trackSideNum / sidesPerDisk;
            sideNum[0] = trackSideNum % sidesPerDisk;
        }
        sectorNum[0] = (sectorPos % groupsPerTrack) / groupsPerSector;
        if (divNum != null) divNum[0] = (sectorPos % groupsPerTrack) % groupsPerSector;

        if (numberingSector == 1) {
            // Case of sequential numbering per track (If numbering is sequential per track)
            sectorNum[0] += (sideNum[0] * sectorsPerTrack);
        }

        // Whether to reverse side number? (Reverse side number?)
        sideNum[0] = basic.getReversedSideNumber(sideNum[0]);

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();

        if (divNums != null) divNums[0] = groupsPerSector;
    }

    /** Get track, sector numbers from sector position */
    @Override
    public void getNumFromSectorPosS(int sectorPos, int[] trackNum, int[] sectorNum) {
        int selectedSide = basic.getSelectedSide();
        int groupsPerTrack = basic.getGroupsPerTrack();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();

        if (selectedSide >= 0) {
            // 1S
            trackNum[0] = sectorPos / groupsPerTrack;
            sectorNum[0] = (sectorPos % groupsPerTrack);
        } else {
            // 2D, 2HD
            trackNum[0] = sectorPos / (groupsPerTrack * sidesPerDisk);
            sectorNum[0] = (sectorPos % (groupsPerTrack * sidesPerDisk));
        }

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();
    }

    /** Get sector position from track, side, sector numbers */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        int groupsPerTrack = basic.getGroupsPerTrack();
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        // Reverse side number?
        sideNum = basic.getReversedSideNumber(sideNum);

        if (selectedSide >= 0) {
            // 1S
            sectorPos = trackNum * groupsPerTrack;
            sectorPos += sectorNum * numOfDivs + divNum;
        } else {
            // 2D, 2HD
            sectorPos = trackNum * sidesPerDisk * groupsPerTrack;
            if (numberingSector == 1) {
                sectorPos += sectorNum * numOfDivs + divNum;
            } else {
                sectorPos += (sideNum % sidesPerDisk) * groupsPerTrack;
                sectorPos += sectorNum * numOfDivs + divNum;
            }
        }

        return sectorPos;
    }

    /** Get sector position from track, sector numbers */
    @Override
    public int getSectorPosFromNumS(int trackNum, int sectorNum) {
        int selectedSide = basic.getSelectedSide();
        int groupsPerTrack = basic.getGroupsPerTrack();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        if (selectedSide >= 0) {
            // 1S
            sectorPos = trackNum * groupsPerTrack + sectorNum;
        } else {
            // 2D, 2HD
            sectorPos = trackNum * groupsPerTrack * sidesPerDisk + sectorNum;
        }
        return sectorPos;
    }

    /** Is it the root directory? */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return false;
    }

    /** Can a subdirectory be created? */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /** Specific processing after filling sector data during formatting */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // VTOC area
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) return false;
        byte[] b = sector.getSectorBuffer();
        if (b == null) return false;
        AppleDosVToc vToc = new AppleDosVToc();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vToc);

        this.appleDosVToc = vToc;

        sector.fill((byte) 0);

        // Set VTOC area

        vToc.dirStartTrack = (byte) basic.getManagedTrackNumber();
        vToc.dirStartSector = (byte) basic.getDirStartSector();

        vToc.releaseNumber = 3;

        vToc.chainSize = APLEDOS_TRACK_LIST_MAX;

        vToc.tracksPerDisk = (byte) basic.getTracksPerSide();
        vToc.sectorsPerTrack = (byte) basic.getSectorsPerTrackOnBasic();

        int map = 0;
        for (int s = 0; s < basic.getSectorsPerTrackOnBasic(); s++) {
            map |= (1 << s);
        }
        setTrackMapMask(map);

        for (int track = 3; track < basic.getTracksPerSideOnBasic(); track++) {
            setTrackMap(track, map);
        }

        // volume number
        setIdentifiedData(data);

        // DIR area (DIR area)
        int dirStartLSector = basic.getDirStartSector();
        int dirEndLSector = 2;
        for (int lSectorPos = dirStartLSector; lSectorPos >= dirEndLSector; lSectorPos--) {
            sector = basic.getManagedSector(lSectorPos);
            if (sector == null) {
                continue;
            }
            sector.fill((byte) 0);
            AppleDosPointer p = new AppleDosPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), p);
            p.nextTrack = appleDosVToc.dirStartTrack;
            p.nextSector = (byte) (lSectorPos - 1);
            sector.copy(p.serialize(), 0, AppleDosPointer.SIZE);
        }

        return true;
    }

    /** Determine the data size of the file's last sector */
    public int calcDataSizeOnLastSector(DiskBasicDirItem<?> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorSize, int remainSize) throws IOException {
        // File size is sector size boundary, so calculation is needed
        if (item.needCheckEofCode()) {
            // Output up to one byte before the termination code
            int eofCode = basic.invertUint8(basic.getTextTerminateCode());
            for (int len = 0; len < remainSize; len++) {
                if (sectorBuffer[len] == (byte) eofCode) {
                    remainSize = len;
                    break;
                }
            }
        } else {
            // No calculation method, so return remaining size as is
            if (iStream != null) {
                // When comparing, use the target file size
                remainSize = iStream.available() % sectorSize;
            }
        }
        return remainSize;
    }

    /** Data writing process */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryAppleDos> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            // Little left
            if (remain < 0) remain = 0;
            if (remain > 0) iStream.readNBytes(buffer, 0, remain);
            if (size > len) {
                // Remaining buffer is zero-suppressed
                Arrays.fill(buffer, len, size, (byte) 0);
            }
            len = remain;
        } else {
            // Continue
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        return len;
    }

    /** Delete FAT area for the specified group number */
    @Override
    public void deleteGroupNumber(int groupNum) {
        // Mark as unused
        setGroupNumber(groupNum, 0);
    }

    /** Processing after file deletion */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryAppleDos> item) throws IOException {
        // Mark chain sectors as unused
        int groupNum = item.getStartGroup(0);
        while (groupNum != 0) {
            setGroupNumber(groupNum, 0);
            DiskImageSector sector = basic.getSectorFromGroup(groupNum);
            if (sector == null) break;
            byte[] b = sector.getSectorBuffer();
            if (b == null) break;
            AppleDosChain p = new AppleDosChain();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
            groupNum = getSectorPosFromNumS((p.next.nextTrack & 0xff) + basic.getTrackNumberBaseOnDisk(), (p.next.nextSector & 0xff) + basic.getSectorNumberBase());
        }

        DiskBasicDirItemAppleDOS dItem = (DiskBasicDirItemAppleDOS) item;
        // Put delete code (0xff) at the start of the directory
        dItem.setStartTrack(basic.getDeleteCode());

        return true;
    }

    /** Get attributes of IPL and managed area */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume number
        data.setVolumeNumber(appleDosVToc.volumeNumber);
    }

    /** Set attributes of IPL and managed area */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat format = basic.getFormatType();

        // volume number
        if (format.hasVolumeNumber()) {
            appleDosVToc.volumeNumber = (byte) data.getVolumeNumber();
            if (appleDosVToc.volumeNumber == 0) {
                appleDosVToc.volumeNumber = (byte) 0xfe;    // default
            }
        }
    }
}
