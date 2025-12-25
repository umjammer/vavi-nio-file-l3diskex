/**
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/**
 *
 * @see "https://www.cpcmania.com/cpcdiskxp/cpcdiskxp.htm"
 * @see "https://github.com/muckypaws/AmstradDSKExplorer"
 * @see "https://archive.org/details/amstrad-cpc-cdt-collection"
 */
/** Amstrad CPC DSK disk parser */
public class DiskDskParser extends DiskImageParser {

    /** CPC DSK header */
    @Serdes(bigEndian = false)
    public static class CPCDSKHeader {

        final static int SIZE = 34 + 14 + 1 + 1 + 2 + 204;

        @Element(sequence = 1)
        public byte[] ident = new byte[34];
        @Element(sequence = 2)
        public byte[] creator = new byte[14];
        @Element(sequence = 3)
        public byte numOfTracks;
        @Element(sequence = 4)
        public byte numOfSides;
        // use only in normal disk
        @Element(sequence = 5)
        public short trackSize;
        // use only in extended disk
        @Element(sequence = 6)
        public byte[] trackSizes = new byte[204];
    }

    /** CPC DSK sector */
    @Serdes(bigEndian = false)
    public static class CPCDSKSector {

        public static final int SIZE = 1 + 1 + 1 + 1 + 1 + 1 + 2;

        @Element(sequence = 1)
        public byte c;
        @Element(sequence = 2)
        public byte h;
        @Element(sequence = 3)
        public byte r;
        @Element(sequence = 4)
        public byte n;
        @Element(sequence = 5)
        public byte fdcStatus1;
        @Element(sequence = 6)
        public byte fdcStatus2;
        // bytes // use only in extended disk
        @Element(sequence = 7)
        public short dataLength;
    }

    /** CPC DSK track */
    @Serdes(bigEndian = false)
    public static class CPCDSKTrack {

        public static final int SIZE = 12 + 4 + 1 + 1 + 2 + 1 + 1 + 1 + 1 + 29 * CPCDSKSector.SIZE;

        @Element(sequence = 1)
        public byte[] ident = new byte[12];
        @Element(sequence = 2)
        byte[] unused1 = new byte[4];
        @Element(sequence = 3, value = "unsigned byte")
        public int trackNumber;
        @Element(sequence = 4, value = "unsigned byte")
        public int sideNumber;
        @Element(sequence = 5)
        byte[] unused2 = new byte[2];
        @Element(sequence = 6, value = "unsigned byte")
        public int sectorSize;
        @Element(sequence = 7, value = "unsigned byte")
        public int numOfSectors;
        @Element(sequence = 8, value = "unsigned byte")
        public int gap3Length;
        @Element(sequence = 9, value = "unsigned byte")
        public int fillerByte;
        @Element(sequence = 10)
        public CPCDSKSector[] sectors = new CPCDSKSector[29];
    }

    //
    // Convert CPC DSK format to D88 format
    //

    /* 0 = normal, 1 = extended */
    private int isExtended;

    @Override
    public boolean isSupported(String type) {
        return "cpcdsk".equalsIgnoreCase(type);
    }

    @Override
    public boolean needsCheck() {
        return true;
    }

    @Override
    public boolean checkCondition(InputStream stream) throws IOException {
        return check(stream) != 0;
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
        this.isExtended = 0; // normal
    }

    /** Create sector data */
    public int parseSector(InputStream iStream, int numOfSectors, Object userData, DiskImageTrack track) throws IOException {
        CPCDSKSector id = (CPCDSKSector) userData;

        int trackNumber = id.c & 0xff;
        int sideNumber = id.h & 0xff;
        int sectorNumber = id.r & 0xff;
        int sectorSize = id.n & 0xff;

        if (sectorSize > 7) {
            // Sector size is too large
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, trackNumber, sideNumber, sectorNumber, sectorSize, id.dataLength);
            return 0;
        }

        sectorSize = 128 << sectorSize;

        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, numOfSectors, false, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int size = sector.getSectorBufferSize();

        int len = iStream.readNBytes(buf, 0, size);
        if (len < size) {
            // Not enough file data
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }
        if (isExtended != 0) {
            // Buffer is large, so skip
            if (id.dataLength > size) {
                int current = (int) ((SeekableDataInputStream) iStream).position();
                ((SeekableDataInputStream) iStream).position(current + (id.dataLength - size)); // wxFromCurrent
            }
        }

        sector.clearModify();

        return sector.getSize();
    }

    /** Create track data */
    public int parseTrack(InputStream iStream, int trackSize, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        int len = iStream.available();
        if (len != CPCDSKTrack.SIZE) {
            result.setError(DiskResult.ERR_NO_TRACK, 0);
            return 0;
        }
        CPCDSKTrack track_header = new CPCDSKTrack();
        Serdes.Util.deserialize(iStream, track_header);
        if (!Arrays.equals(track_header.ident, "Track-Info\r\n".getBytes())) {
            result.setError(DiskResult.ERR_NO_TRACK, 0);
            return 0;
        }

        DiskImageTrack track = disk.newImageTrack(track_header.trackNumber, track_header.sideNumber, offsetPos, 1);
        disk.setMaxTrackNumber(track_header.trackNumber);

        int d88TrackSize = 0;
        for (int pos = 0; pos < track_header.numOfSectors && result.getValid() >= 0; pos++) {
            d88TrackSize += parseSector(iStream,
                    track_header.numOfSectors,
                    track_header.sectors[pos], track);
        }

        if (result.getValid() >= 0) {
            // Calculate interleave
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // Set track size
            track.setSize(d88TrackSize);
            // Side number matches ID H of each sector
            track.setSideNumber(track.getMajorIDH());

            // Add to disk
            disk.add(track);
            // Set offset
            disk.setOffset(offsetPos, offset);
        }

        return d88TrackSize;
    }

    /** Analyze disk */
    public int parseDisk(InputStream iStream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        int len = iStream.available();
        if (len != CPCDSKHeader.SIZE) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return 0;
        }
        CPCDSKHeader header = new CPCDSKHeader();
        Serdes.Util.deserialize(iStream, header);

        disk.setName(header.creator, header.creator.length);
        int maxTracks = header.numOfTracks * header.numOfSides;

        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < 204 && pos < maxTracks; pos++) {
            d88Offset += parseTrack(iStream,
                    isExtended != 0 ? (int) header.trackSizes[pos] * 256 : header.trackSize,
                    d88OffsetPos, d88Offset, disk);
            d88OffsetPos++;
            if (d88OffsetPos >= disk.getCreatableTracks()) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88Offset);
            }
        }
        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            // Add disk
            DiskParam disk_param = disk.calcMajorNumber();
            if (disk_param != null) {
                disk.setDensity(disk_param.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return d88Offset;
    }

    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        int len = iStream.available();
        if (len < CPCDSKHeader.SIZE) {
            // too short
            return -1;
        }
        CPCDSKHeader header = new CPCDSKHeader();
        Serdes.Util.deserialize(iStream, header);

        ((SeekableDataInputStream) iStream).position(0);

        // check identifier
        int valid = -1;
        if (Arrays.equals(header.ident, "MV - CPCEMU Disk-File\r\nDisk-Info\r\n".getBytes())) {
            isExtended = 0;	// normal
            valid = 0;
        } else if (Arrays.equals(header.ident, "EXTENDED CPC DSK File\r\nDisk-Info\r\n".getBytes())) {
            isExtended = 1;	// extended
            valid = 0;
        }
        return valid;
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        parseDisk(iStream);
        return result.getValid();
    }
}
