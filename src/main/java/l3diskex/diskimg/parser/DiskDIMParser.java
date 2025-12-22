//
// Copyright (c) Sasaji. All rights reserved.
//

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.diskimg.DiskParam.diskTemplates;


/**
 *
 * @see "https://web.archive.org/web/20010617113151/http://www6.airnet.ne.jp/gun/x68k/difc/difc.html"
 * @see "https://stdkmd.net/xeij/source/FDMedia.htm"
 * DIFC.X DIM disk image parser
 */
public class DiskDIMParser extends DiskPlainParser {

    private static final String DISK_DIM_HEADER = "DIFC HEADER  \0\0";

    /**
     * DIM disk header
     */
    @Serdes
    public static class DimDskHeader {

        private static final int SIZE = 256;

        @Element(sequence = 1)
        byte type;
        @Element(sequence = 2)
        byte[] tracks = new byte[0xaa];
        @Element(sequence = 3)
        byte[] ident = new byte[15];
        @Element(sequence = 4)
        byte[] date = new byte[4];
        @Element(sequence = 5)
        byte[] time = new byte[4];
        @Element(sequence = 6)
        byte[] comments = new byte[0x3d];
        @Element(sequence = 7)
        byte overtrack;
    }

    @Override
    public boolean isSupported(String type) {
        return "difcdim".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
    }

    /**
     * Analyze DIM file
     *
     * @param iStream    Input disk image
     * @param diskNumber Disk number
     * @param diskParam  Disk parameters
     * @return Offset (last data position)
     */
    @Override
    public int parseDisk(InputStream iStream, int diskNumber, DiskParam diskParam) throws IOException {
        if (iStream.available() < DimDskHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        DimDskHeader header = new DimDskHeader();
        Serdes.Util.deserialize(iStream, header);

        ((SeekableDataInputStream) iStream).position(0x100);
        int fileLength = iStream.available() + 0x100;

        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // If calculated parameter value is twice the disk size
        // set data only on the front side
        int dummySide = -1;
        if (fileLength * 2 <= diskParam.calcDiskSize()) {
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
                boolean isDummyTrack = sideNum == dummySide;
                if (header.tracks[offsetPos] == 0) {
                    // Create dummy track because track information is missing
                    isDummyTrack = true;
                }
                offset += parseTrack(iStream, offsetPos, offset, diskNumber, diskParam, trackNum, sideNum, isDummyTrack, disk);
                offsetPos++;
            }
        }
        disk.setSize(offset);

        if (result.getValid() >= 0) {
            // Add disk
            DiskParam param = disk.calcMajorNumber();
            if (param != null) {
                disk.setDensity(param.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return offset;
    }

    /** Analyze DIM file */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        ((SeekableDataInputStream) iStream).position(0x100);

        parseDisk(iStream,
                0,
                diskParam);

        return result.getValid();
    }

    /** Check */
    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        int fileLength = iStream.available();
        if (fileLength < DimDskHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        DimDskHeader header = new DimDskHeader();
        Serdes.Util.deserialize(iStream, header);
        // Check header string
        if (!Arrays.equals(header.ident, DISK_DIM_HEADER.getBytes())) {
            // not disk
            return -1;
        }

        int streamSize = fileLength - DimDskHeader.SIZE;
        int sidesPerDisk = 2;
        int sectorSize = 1024;
        int sectorsPerTrack = 8;

        // Count number of tracks
        int maxTracks = 0;
        for (int n = 0; n < header.tracks.length; n++) {
            if (header.tracks[n] != 0) {
                maxTracks = n + 1;
            }
        }
        if (maxTracks == 0) {
            maxTracks = 144;
        }

        // Determine number of sectors
        int secsPerTrkMod = (streamSize / sectorSize) % maxTracks;
        if (secsPerTrkMod == 0) {
            // decide
            sectorsPerTrack = (streamSize / sectorSize) / maxTracks;
        } else {
            for (int i = 8; i < 10; i++) {
                if (((streamSize / sectorSize) % i) == 0) {
                    sectorsPerTrack = i;
                    break;
                }
            }
        }

        int tracksPerSide = maxTracks / sidesPerDisk;

        // Search from disk templates
        DiskParam dummy = new DiskParam();

        if (diskHints != null) {
            // Parameter hint exists
            for (int retry = 0; retry < 2 && diskParams.isEmpty(); retry++) {
                // Add candidates with higher priority
                for (DiskTypeHint diskHint : diskHints) {
                    int kind = diskHint.getKind();
                    if (kind != header.type) {
                        continue;
                    }
                    String hint = diskHint.getHint();
                    DiskParam param = diskTemplates.find(hint);
                    if (param != null) {
                        // File size matches
                        // or number of tracks matches
                        // or when retrying
                        if (streamSize == param.calcDiskSize() ||
                                tracksPerSide == param.getTracksPerSide() ||
                                retry > 0) {
                            diskParams.add(param);
                        }
                    }
                }
            }
        }

        // Add other candidates with same parameters
        diskTemplates.find(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize, diskParams, !diskParams.isEmpty());

        if (diskParams.isEmpty()) {
            manualParam.setDiskParam(
                    sidesPerDisk,
                    tracksPerSide,
                    sectorsPerTrack,
                    sectorSize,
                    0,
                    1,
                    dummy.getSingles(),
                    dummy.getParticularTracks());
            return 1;
        }

        return 0;
    }
}
