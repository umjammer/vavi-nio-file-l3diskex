///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParam.TrackParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;

import static l3diskex.diskimg.DiskParam.diskTemplates;


/**
 * Plain disk parser
 */
public class DiskPlainParser extends DiskImageParser {

    @Override
    public boolean isSupported(String type) {
        return "plain".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
    }

    /**
     * Interleave analysis
     *
     * @param track        Track
     * @param interleave   Interleave
     * @param sectorOffset Sector offset
     */
    protected void parseInterleave(DiskImageTrack track, int interleave, int sectorOffset) {
        if (track == null) return;

        List<DiskImageSector> sectors = track.getSectors();
        if (sectors == null) {
            return;
        }
        int count = sectors.size();

        List<Integer> sectorNums = new ArrayList<>();
        for (DiskImageSector sector : sectors) {
            sectorNums.add(sector.getSectorNumber());
        }

        List<Integer> sectorIdxs = new ArrayList<>();
        if (!DiskImageTrack.calcSectorNumbersForInterleave(interleave, count, sectorIdxs, 0)) {
            return;
        }

        for (int idx = 0; idx < count; idx++) {
            DiskImageSector sector = sectors.get(idx);
            if (sector != null) {
                sector.setSectorNumber(sectorNums.get(sectorIdxs.get(idx)));
            }
        }

        track.setInterleave(interleave);
    }

    /**
     * Sector data analysis
     *
     * @param istream      Input disk image
     * @param diskNumber   Disk number
     * @param diskParam    Disk parameter
     * @param trackNumber  Track number
     * @param sideNumber   Side number
     * @param sectorNumber Sector number
     * @param sectorNums   Number of sectors
     * @param sectorSize   Sector size
     * @param isDummy      Whether it is a dummy sector (whether to pad with 0)
     * @param track        [in,out] Track
     * @return Size of created sector (including header)
     */
    protected int parseSector(InputStream istream, int diskNumber, DiskParam diskParam, int trackNumber, int sideNumber, int sectorNumber, int[] sectorNums, int[] sectorSize, boolean isDummy, DiskImageTrack track) throws IOException {
        // Whether to make it a special sector
        int[][] sectorId = new int[1][];
        if (diskParam.findParticularSector(trackNumber, sideNumber, sectorNumber, sectorSize, sectorId)) {
            if ((sectorId[0][1] & TrackParam.ID_IS_VALID) != 0) {
                sideNumber = (sectorId[0][1] & ~TrackParam.ID_IS_VALID);
            }
            if ((sectorId[0][2] & TrackParam.ID_IS_VALID) != 0) {
                sectorNumber = (sectorId[0][2] & ~TrackParam.ID_IS_VALID);
            }
        }

        // Single density?
        boolean singleDensity = diskParam.findSingleDensity(trackNumber, sideNumber, sectorNumber, sectorSize[0]);

        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize[0], sectorNums[0], singleDensity, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();

        if (!isDummy) {
            int len = istream.readNBytes(buf, 0, siz);
            if (len == 0) {
                // Not enough file data
                //result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                // so zero padding
                isDummy = true;
            }
        }
        if (isDummy) {
            // Dummy sector or missing part
            sector.fill((byte) 0);
        }

        sector.clearModify();

        // Return size of this sector data
        return sector.getSize();
    }

    /**
     * Track data analysis
     *
     * @param istream     Input disk image
     * @param offsetPos   Offset position (index)
     * @param offset      Offset bytes
     * @param diskNumber  Disk number
     * @param diskParam   Disk parameter
     * @param trackNumber Track number
     * @param sideNumber  Side number
     * @param isDummySide Whether it is a dummy side
     * @param disk        [in,out] Disk
     * @return Size of created track
     */
    protected int parseTrack(InputStream istream, int offsetPos, int offset, int diskNumber, DiskParam diskParam, int trackNumber, int sideNumber, boolean isDummySide, DiskImageDisk disk) throws IOException {
        DiskImageTrack track = disk.newImageTrack(trackNumber, sideNumber, offsetPos, 1);
        disk.setMaxTrackNumber(trackNumber);

        int[] sectorNums = {diskParam.getSectorsPerTrack()};
        int[] sectorSize = {diskParam.getSectorSize()};

        // Get sector number & size if it is a special track
        diskParam.findParticularTrack(trackNumber, sideNumber, sectorNums, sectorSize);

        // Get number of sectors and size if the entire track is single density
        diskParam.findSingleDensity(trackNumber, sideNumber, sectorNums, sectorSize);

        int trackSize = 0;
        int sectorOffset = diskParam.getSectorNumberBaseOnDisk();

        // Sector numbering method (0: per side, 1: per track)
        if (diskParam.getNumberingSector() != 0) {
            sectorOffset += sideNumber * sectorNums[0];
        }

        for (int sectorNumber = 0; sectorNumber < sectorNums[0] && result.getValid() >= 0; sectorNumber++) {
            trackSize += parseSector(istream, diskNumber, diskParam, trackNumber, sideNumber, sectorNumber + sectorOffset, sectorNums, sectorSize, isDummySide, track);
        }
        if (result.getValid() >= 0) {
            // Interleave
            parseInterleave(track, diskParam.getInterleave(), sectorOffset);
            // Set track size
            track.setSize(trackSize);
            // Add to disk
            disk.add(track);
            // Set offset
            disk.setOffset(offsetPos, offset);
        }

        return trackSize;
    }

    /**
     * Disk data analysis
     *
     * @param iStream    Input disk image
     * @param diskNumber Disk number
     * @param diskParam  Disk parameter
     * @return Offset
     */
    protected int parseDisk(InputStream iStream, int diskNumber, DiskParam diskParam) throws IOException {
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // If calculated parameter value is twice the disk size
        // set data only on the front side
        int dummySide = -1;
        int streamLength = iStream.available();
        if (streamLength * 2 <= diskParam.calcDiskSize()) {
            dummySide = diskParam.getSideNumberBaseOnDisk() + 1;
        }

        int offset = disk.getOffsetStart();
        int offsetPos = 0;
        int trackNum = diskParam.getTrackNumberBaseOnDisk();
        int tracksPerSide = diskParam.getTracksPerSide() + trackNum;
        int sideNumSt = diskParam.getSideNumberBaseOnDisk();
        int sideNumEd = diskParam.getSidesPerDisk() + sideNumSt;
        for (; trackNum < tracksPerSide && result.getValid() >= 0; trackNum++) {
            for (int sideNum = sideNumSt; sideNum < sideNumEd && result.getValid() >= 0; sideNum++) {
                // Create track
                offset += parseTrack(iStream, offsetPos, offset, diskNumber, diskParam, trackNum, sideNum, sideNum == dummySide, disk);
                offsetPos++;
            }
        }
        disk.setSize(offset);

        if (result.getValid() >= 0) {
            // Add disk
            DiskParam majorDiskParam = disk.calcMajorNumber();
            if (majorDiskParam != null) {
                disk.setDensity(majorDiskParam.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return offset;
    }

    /**
     * Analyze plain file
     *
     * @param iStream   Data to be analyzed
     * @param diskParam Disk parameter
     * @return 0: normal, -1: error, 1: warning
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        // Parameters
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        parseDisk(iStream, 0, diskParam);

        return result.getValid();
    }

    @Override
    public int check(InputStream iStream) {
        return -1;
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        int rc = 0;
        int streamSize = iStream.available(); // TODO assume available as length

        // Judge by parameters
        if (diskParam != null) {
            // Identified
            diskParams.add(diskParam);
            return rc;
        }

        if (diskHints != null) {
            // Parameter hint exists

            // Candidates with high priority
            for (DiskTypeHint diskHint : diskHints) {
                String hint = diskHint.getHint();
                DiskParam param = diskTemplates.find(hint);
                if (param != null) {
                    int diskSizeHint = param.calcDiskSize();
                    if (streamSize == diskSizeHint) {
                        // File size matches
                        diskParams.add(param);
                    }
                }
            }
        }

        // Search from all disk templates
        for (int mag = 1; mag <= 2; mag++) {
            boolean separator = (diskParams.isEmpty());
            for (int i = 0; i < diskTemplates.size(); i++) {
                DiskParam param = diskTemplates.get(i);
                if (param != null) {
                    // Skip if same candidate exists
                    if (diskParams.contains(param)) {
                        continue;
                    }

                    int diskSizeHint = param.calcDiskSize();
                    if (streamSize * mag == diskSizeHint) {
                        if (!separator) {
                            diskParams.add(null);
                            separator = true;
                        }
                        // File size matches
                        diskParams.add(param);
                    }
                }
            }
        }

        // If no candidate, calculate parameters from disk size
        if (diskParams.isEmpty()) {
            calcParamFromSize(streamSize, manualParam);
        }

        // Show selection dialog by GUI
        rc = 1;

        return rc;
    }

    // Sector size hint
    private static final int[] secSizeHints = {
            256, 128, 0
    };
    // Number of sectors hint
    private static final int[] secs256 = {10, 16, 18, 0};
    private static final int[] secs512 = {9, 10, 0};
    private static final int[] secs1024 = {4, 5, 0};
    private static final int[][] secsHint = {
            secs256,
            secs512,
            secs1024,
    };
    private static final int[] tracks = {80, 77, 40, 35, 512, 511, 256, 255, 128, 127, 64, 63, 0};

    /** Calculate plausible parameters from disk size */
    protected void calcParamFromSize(int diskSize, DiskParam diskParam) {
        // Number of tracks by disk size
        int maxTracks = 41;
        int minTracks = 40;
        if (diskSize > 1_000_000) {
            // 2HD?
            maxTracks = 82;
            minTracks = 80;
        } else if (diskSize > 500_000) {
            // 2DD?
            maxTracks = 82;
            minTracks = 80;
        }

        int value = 0;
        int desidedSecSizeIdx = -1;
        int desidedAllSectors = 0;

        for (int secSizeIdx = 0; secSizeHints[secSizeIdx] != 0; secSizeIdx++) {
            // Divide by sector size
            value = diskSize % secSizeHints[secSizeIdx]; // Remainder
            if (value == 0) {
                desidedSecSizeIdx = secSizeIdx;
                desidedAllSectors = diskSize / secSizeHints[secSizeIdx];
                break;
            }
        }
        if (desidedSecSizeIdx < 0) {
            // No sector size candidates
            return;
        }

        int desidedTracks = 0;
        int desidedSides = 0;
        int desidedSectors = 0;
        boolean desided = false;
        int[] sectors = secsHint[desidedSecSizeIdx];
        if (sectors != null) {
            for (int sides = 1; sides <= 2 && !desided; sides++) {
                for (int tracks = minTracks; tracks <= maxTracks && !desided; tracks++) {
                    for (int ss = 0; sectors[ss] != 0 && !desided; ss++) {
                        value = (tracks * sides * sectors[ss]);
                        if (desidedAllSectors <= value) {
                            desidedTracks = tracks;
                            desidedSides = sides;
                            desidedSectors = sectors[ss];
                            desided = true;
                            break;
                        }
                    }
                }
            }
        }

        // No candidates
        if (!desided) {
            // Make values divisible by disk size candidates
            for (int sides = 2; sides >= 1; sides--) {
                value = desidedAllSectors % sides;
                if (value == 0) {
                    desidedSides = sides;
                    desidedAllSectors = desidedAllSectors / sides;
                    break;
                }
            }
            // Divide by number of tracks
            for (int t = 0; tracks[t] != 0; t++) {
                value = desidedAllSectors % tracks[t];
                if (value == 0) {
                    desidedTracks = tracks[t];
                    desidedSectors = desidedAllSectors / tracks[t];
                    break;
                }
            }
            if (value != 0) {
                // Divide by number of sectors
                for (int s = 32; s >= 2; s--) {
                    value = desidedAllSectors % s;
                    if (value == 0) {
                        desidedTracks = desidedAllSectors / s;
                        desidedSectors = s;
                        break;
                    }
                }
            }
            if (value != 0) {
                for (int s = 1; s <= 32; s++) {
                    if ((desidedAllSectors / s) < 10000) {
                        desidedTracks = desidedAllSectors / s;
                        desidedSectors = s;
                        break;
                    }
                }
            }
        }

        diskParam.setDiskParam(
                desidedSides,
                desidedTracks,
                desidedSectors,
                secSizeHints[desidedSecSizeIdx],
                0,
                1,
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
