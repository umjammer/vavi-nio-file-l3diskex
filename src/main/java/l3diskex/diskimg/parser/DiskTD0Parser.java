/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.prefs.Preferences;

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
 * Teledisk td0 disk parser
 *
 * @see "https://dn721605.ca.archive.org/0/items/td0-format-docs/TD0NOTES.TXT"
 * @see "http://dunfield.classiccmp.org/img42841/teledisk.htm"
 */
public class DiskTD0Parser extends DiskImageParser {

    /** Teledisk td0 disk image header */
    @Serdes
    public static class Td0ImageHeader {

        public static final int SIZE = 2 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 2;

        @Element(sequence = 1)
        public byte[] ident = new byte[2];
        @Element(sequence = 2)
        public byte sequence;
        @Element(sequence = 3)
        public byte checkSequence;
        @Element(sequence = 4)
        public byte teleDiskVersion;
        @Element(sequence = 5)
        public byte dataRate;
        @Element(sequence = 6)
        public byte diveType;
        @Element(sequence = 7)
        public byte stepping;
        @Element(sequence = 8)
        public byte dosAllocFlag;
        @Element(sequence = 9)
        public byte sidesPerDisk;
        @Element(sequence = 10)
        public short crc;
    }

    /** Teledisk td0 comment header */
    @Serdes
    public static class Td0CommentHeader {

        public static final int SIZE = 2 + 2 + 1 + 1 + 1 + 1 + 1 + 1 + 10;

        @Element(sequence = 1)
        public short crc;
        @Element(sequence = 2)
        public short dataLength;
        @Element(sequence = 3)
        public byte year;
        @Element(sequence = 4)
        public byte month;
        @Element(sequence = 5)
        public byte day;
        @Element(sequence = 6)
        public byte hour;
        @Element(sequence = 7)
        public byte minute;
        @Element(sequence = 8)
        public byte second;
        @Element(sequence = 9)
        public byte[] buf = new byte[10];
    }

    /** Teledisk td0 track header */
    @Serdes
    public static class Td0TrackHeader {

        public static final int SIZE = 1 + 1 + 1 + 1;

        @Element(sequence = 1)
        public byte numOfSectors;
        @Element(sequence = 2)
        public byte trackNum;
        @Element(sequence = 3)
        public byte sideNum;
        @Element(sequence = 4)
        public byte crc;
    }

    /** Teledisk td0 sector header */
    @Serdes
    public static class Td0SectorHeader {

        public static final int SIZE = 1 + 1 + 1 + 1 + 1;

        @Element(sequence = 1)
        public byte trackNum;
        @Element(sequence = 2)
        public byte sideNum;
        @Element(sequence = 3)
        public byte sectorNum;
        @Element(sequence = 4)
        public byte sectorSize;
        @Element(sequence = 5)
        public byte flags;
    }

    /** Teledisk td0 data header */
    @Serdes
    public static class Td0DataHeader {

        public static final int SIZE = 2;

        @Element(sequence = 1, value = "unsigned short")
        public int size;
    }

    // TODO Advanced compress version is not supported.
    private boolean isCompressed;

    /**
     * Create sector data
     */
    private int parseSector(InputStream iStream, int diskNumber, int numOfSectors, Object userData, DiskImageTrack track) throws IOException {
        int len = iStream.available();
        if (len < Td0SectorHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }
        Td0SectorHeader sectorHeader = new Td0SectorHeader();
        Serdes.Util.deserialize(iStream, sectorHeader);

        int trackNumber = sectorHeader.trackNum & 0xff;
        int sideNumber = sectorHeader.sideNum & 0xff;
        int sectorNumber = sectorHeader.sectorNum & 0xff;
        int sectorSize = sectorHeader.sectorSize & 0xff;

        if (sectorSize > 7) {
            // Sector size is too large
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, diskNumber, trackNumber, sideNumber, sectorNumber, sectorSize, 0);
            return 0;
        }

        sectorSize = 128 << sectorSize;

        // Sector creation
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, numOfSectors, false, 0);
        track.add(sector);

        if ((sectorHeader.flags & 0x04) != 0) {
            // deleted mark
            sector.setDeletedMark(true);
        }

        byte[] buffer = sector.getSectorBuffer();
        int bufLen = sector.getSectorBufferSize();

        decodeData(iStream, diskNumber, buffer, bufLen);

        sector.clearModify();

        // Return size of this sector data
        return sector.getSize();
    }

    /**
     * Create track data
     */
    private int parseTrack(InputStream iStream, int diskNumber, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        int len = iStream.available();
        if (len < Td0TrackHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return -1;
        }
        Td0TrackHeader trackHeader = new Td0TrackHeader();
        Serdes.Util.deserialize(iStream, trackHeader);
        if (trackHeader.numOfSectors == (byte) 0xff) {
            return -1;
        }

        // Track creation
        DiskImageTrack track = disk.newImageTrack(trackHeader.trackNum & 0xff, trackHeader.sideNum & 0xff, offsetPos, 1);
        disk.setMaxTrackNumber(trackHeader.trackNum & 0xff);

        int d88TrackSize = 0;
        for (int pos = 0; pos < (trackHeader.numOfSectors & 0xff) && result.getValid() >= 0; pos++) {
            d88TrackSize += parseSector(iStream, diskNumber, trackHeader.numOfSectors & 0xff, null, track);
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

    /**
     * Analyze TD0 file
     *
     * @param iStream    Data to be analyzed
     * @param diskNumber Disk number
     * @return -1: finish parsing, 0: parse next disk
     */
    private int parseDisk(InputStream iStream, int diskNumber) throws IOException {
        int len = iStream.available();
        if (len == 0) {
            // no disk: finish parsing
            return -1;
        }
        if (len < Td0ImageHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return result.getValid();
        }
        Td0ImageHeader imageHeader = new Td0ImageHeader();
        Serdes.Util.deserialize(iStream, imageHeader);

        len = iStream.available();
        if (len < Td0CommentHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return result.getValid();
        }
        Td0CommentHeader commentHeader = new Td0CommentHeader();
        Serdes.Util.deserialize(iStream, commentHeader);

        int commentLength = commentHeader.dataLength;
        iStream.skipNBytes(commentLength);

        // Create disk
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // Track analysis
        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < 204; pos++) {
            int offset = parseTrack(iStream, diskNumber, d88OffsetPos, d88Offset, disk);
            if (offset == -1) {
                break;
            }
            d88Offset += offset;

            d88OffsetPos++;
            if (d88OffsetPos >= disk.getCreatableTracks()) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, diskNumber, d88Offset);
            }
        }
        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            // Add disk
            DiskParam diskParam = disk.calcMajorNumber();
            if (diskParam != null) {
                disk.setDensity(diskParam.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return 0;
    }

    /** Expand repeated data */
    private int decodeRepeatedData(InputStream iStream, int diskNumber, int pos, int sLen, int repeat, byte[] buffer, int bufLen) throws IOException {
        byte[] pattern = new byte[sLen];

        int len = iStream.readNBytes(pattern, 0, sLen);
        if (len == 0) {
            return pos;
        }
        for (int j = 0; j < repeat && pos < bufLen; j++) {
            for (int i = 0; i < sLen && pos < bufLen; i++) {
                buffer[pos] = pattern[i];
                pos++;
            }
        }
        return pos;
    }

    /** Expand plain data */
    private int decodePlainData(InputStream iStream, int diskNumber, int pos, int sLen, byte[] buffer, int bufLen) throws IOException {
        for (int i = 0; i < sLen && pos < bufLen && iStream.available() > 0; i++) {
            int b = iStream.read();
            if (b == -1) break;
            buffer[pos] = (byte) b;
            pos++;
        }
        return pos;
    }

    /** Expand data and write to buffer */
    private int decodeData(InputStream iStream, int diskNumber, byte[] buffer, int bufLen) throws IOException {
        int len = iStream.available();
        if (len < Td0DataHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }
        Td0DataHeader dataHeader = new Td0DataHeader();
        Serdes.Util.deserialize(iStream, dataHeader);

        // Prefetch data block
        byte[] data = new byte[dataHeader.size];
        len = iStream.readNBytes(data, 0, dataHeader.size);
        if (len < dataHeader.size) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data);
        baos.write(0);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

        int pos = 0;
        int method = bais.read();
        if (method == 0) {
            // Plain data
            pos = decodePlainData(bais, diskNumber, pos, dataHeader.size, buffer, bufLen);
        } else if (method == 1) {
            // Repeated data
            int repeat = bais.read();
            repeat |= (bais.read() << 8);
            pos = decodeRepeatedData(bais, diskNumber, pos, 2, repeat, buffer, bufLen);
        } else if (method == 2) {
            do {
                int sub = bais.read();
                if (sub == 0) {
                    // Plain data
                    int slen = bais.read();
                    pos = decodePlainData(bais, diskNumber, pos, slen, buffer, bufLen);
                } else {
                    // Repeated data
                    int sLen = sub * 2;
                    int repeat = bais.read() | (bais.read() << 8);
                    pos = decodeRepeatedData(bais, diskNumber, pos, sLen, repeat, buffer, bufLen);
                }
            } while (pos < bufLen);
        }
        return pos;
    }

    @Override
    public boolean isSupported(String type) {
        return "teletd0".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);

        isCompressed = false;
    }

    /**
     * Check
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        int len = iStream.available();
        if (len < Td0ImageHeader.SIZE) {
            // too short
            return -1;
        }
        Td0ImageHeader header = new Td0ImageHeader();
        Serdes.Util.deserialize(iStream, header);
        if (header.ident[0] != 'T' || header.ident[1] != 'D') {
            // not TD0 image
            // note that "td" (lower) which advanced compress version is not supported.
            return -1;
        }
        if (header.teleDiskVersion != (byte) 0x15) {
            // support only 1.5 version
            return -1;
        }

        int sidesPerDisk = header.sidesPerDisk & 0xff;
        if (sidesPerDisk > 2) {
            return -1;
        }

        return 0;
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> hints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);
        for (int diskNumber = 0; ; diskNumber++) {
            if (parseDisk(iStream, diskNumber) < 0) {
                break;
            }
        }
        return result.getValid();
    }
}
