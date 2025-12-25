/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.util.ArrayList;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam.TrackParam;


/**
 * Create a new disk image
 */
public class DiskImageCreator {

    /** Disk name */
    private final String diskName;
    /** Parameters */
    private final DiskParam param;
    /** Write protection flag */
    private final boolean writeProtect;
    /** Disk image file */
    private final DiskImageFile file;
    /** Execution result */
    private final DiskResult result;

    //
    //
    //
    public DiskImageCreator(String diskName, DiskParam param, boolean writeProtect,
                            DiskImageFile file, DiskResult result) {
        this.diskName = diskName;
        this.param = param;
        this.writeProtect = writeProtect;
        this.file = file;
        this.result = result;
    }

    /**
     * Create sector data
     * @param trackNumber     Track number
     * @param sideNumber      Side number
     * @param sectorNumber    Sector number
     * @param sectorSize      Sector size
     * @param sectorsPerTrack Number of sectors per track
     * @param track           Track
     * @return Size of created sector (including header)
     */
    private int createSector(int trackNumber, int sideNumber,
                             int sectorNumber, int sectorSize,
                             int sectorsPerTrack, DiskImageTrack track) {

        // Whether to make it a special sector
        int[][] sectorId = new int[1][];
        int[] sectorSize_ = {sectorSize};
        if (param.findParticularSector(trackNumber, sideNumber, sectorNumber, sectorSize_, sectorId)) {
            if ((sectorId[0][1] & TrackParam.ID_IS_VALID) != 0) {
                sideNumber = sectorId[0][1] & ~TrackParam.ID_IS_VALID;
            }
            if ((sectorId[0][2] & TrackParam.ID_IS_VALID) != 0) {
                sectorNumber = sectorId[0][2] & ~TrackParam.ID_IS_VALID;
            }
        }

        // Whether to make it single density
        boolean singleDensity = param.findSingleDensity(trackNumber, sideNumber, sectorNumber, sectorSize_[0]);

        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize_[0], sectorsPerTrack, singleDensity, 0);
        track.add(sector);

        // Return size of this sector data
        return sector.getSize();
    }

    /**
     * Create track data
     * @param trackNumber Track number
     * @param sideNumber  Side number
     * @param offsetPos   Offset number
     * @param offset      Offset position where the track is located
     * @param disk        Disk
     * @return Created track size
     */
    public int createTrack(int trackNumber, int sideNumber, int offsetPos, int offset, DiskImageDisk disk) {

        // Track creation
        DiskImageTrack track = disk.newImageTrack(trackNumber, sideNumber, offsetPos, param.getInterleave());

        int[] sectorMax = {param.getSectorsPerTrack()};
        int[] sectorSize = {param.getSectorSize()};

        // Whether to make it a special track
        param.findParticularTrack(trackNumber, sideNumber, sectorMax, sectorSize);
        // Get number of sectors and size if entire track is single density
        param.findSingleDensity(trackNumber, sideNumber, sectorMax, sectorSize);

        // Calculate interleave order
        List<Integer> sectorNums = new ArrayList<>();
        if (!DiskImageTrack.calcSectorNumbersForInterleave(param.getInterleave(), sectorMax[0], sectorNums, param.getSectorNumberBaseOnDisk())) {
            result.setError(DiskResult.ERR_INTERLEAVE);
        }

        // create sectors
        int trackSize = 0;
        for (int sectorPos = 0; sectorPos < sectorMax[0] && result.getValid() >= 0; sectorPos++) {
            int sectorOffset = 0;
            if (param.isReversible()) {
                // Case where it can be reversed (has AB sides)
                sideNumber = 0;
            }
            if (param.getNumberingSector() == 1) {
                // Case for sequential numbering
                sectorOffset = sideNumber * sectorMax[0];
            }
            trackSize += createSector(trackNumber, sideNumber, sectorNums.get(sectorPos) + sectorOffset, sectorSize[0], sectorMax[0], track);
        }

        if (result.getValid() >= 0) {
            // Add track
            track.setSize(trackSize);
            disk.add(track);
        }

        return trackSize;
    }

    /**
     * Create disk data
     * @param diskNumber Disk number
     * @param modFlags   New or Append? (DiskImageFile#MODIFY_NONE/MODIFY_ADD)
     * @return Created disk size
     */
    private int createDisk(int diskNumber, short modFlags) {

        DiskImageDisk disk = file.newImageDisk(diskNumber, param, diskName, writeProtect);

        // create tracks
        int createSize = 0;
        int trackNum = param.getTrackNumberBaseOnDisk();
        int sideNum = param.getSideNumberBaseOnDisk();
        int tracksPerSide = param.getTracksPerSide() + trackNum;
        int sidesPerDisk = param.getSidesPerDisk() + sideNum;

        for (int pos = 0; result.getValid() >= 0; pos++) {
            disk.setOffsetWithoutHeader(pos, createSize);
            disk.setMaxTrackNumber(pos);

            createSize += createTrack(trackNum, sideNum, pos, disk.getOffset(pos), disk);

            sideNum++;
            if (sideNum >= sidesPerDisk) {
                trackNum++;
                sideNum = param.getSideNumberBaseOnDisk();
            }
            if (trackNum >= tracksPerSide) {
                break;
            }
        }

        if (result.getValid() >= 0) {
            // Add disk
            if (param.getBasicTypes().isEmpty()) {
                // When parameters are manually set, search for a plausible template
                disk.calcMajorNumber();
            } else {
                // Set from template
                disk.setDiskParam(param);
                // Prepare DISKBASIC
                disk.allocDiskBasics();
            }
            disk.setSizeWithoutHeader(createSize);
            file.add(disk, modFlags);
        }

        return createSize;
    }

    /**
     * Create a new disk image
     */
    public int create() {
        createDisk(0, DiskImageFile.MODIFY_NONE);
        return result.getValid();
    }

    /**
     * Create new and add to existing image
     */
    public int add() {
        int diskNumber = 0;
        List<DiskImageDisk> disks = file.getDisks();
        if (disks != null) {
            diskNumber = disks.size();
        }

        createDisk(diskNumber, DiskImageFile.MODIFY_ADD);

        return result.getValid();
    }
}
