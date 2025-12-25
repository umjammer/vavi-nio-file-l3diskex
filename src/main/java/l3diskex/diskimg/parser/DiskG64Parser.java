/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/** Commodore G64 disk parser */
public class DiskG64Parser extends DiskImageParser {

    /** G64 header */
    @Serdes(bigEndian = false)
    private static final class G64Header {

        /** 8‑byte signature ("GCR-1541") */
        @Element(sequence = 1)
        public byte[] sig = new byte[8];
        @Element(sequence = 2)
        public byte version;
        @Element(sequence = 3)
        public byte numOfTracks;
        @Element(sequence = 4)
        public short maxTrackSize;

        public static final int SIZE = 8 + 1 + 1 + 2; // 12
    }

    /** G64 sector header */
    @Serdes(bigEndian = false)
    private static final class G64SectorHeader {

        @Element(sequence = 1)
        public byte blockId;
        @Element(sequence = 2)
        public byte formatId0;
        @Element(sequence = 3)
        public byte formatId1;
        @Element(sequence = 4)
        public byte trackNumber;
        @Element(sequence = 5)
        public byte sectorNumber;
        @Element(sequence = 6)
        public byte reserved1;
        @Element(sequence = 7)
        public byte reserved2;
        // The original C++ struct had a 2‑byte reserved array
    }

    /** G64 sector data */
    @Serdes(bigEndian = false)
    private static final class G64SectorData {

        @Element(sequence = 1)
        public byte blockId;
        @Element(sequence = 2)
        public byte[] data = new byte[256];
        @Element(sequence = 3)
        public byte checksum;
        @Element(sequence = 4)
        public byte reserved1;
        @Element(sequence = 5)
        public byte reserved2;
    }

    //
    //
    //

    /** */
    private G64Header header;

    @Override
    public boolean isSupported(String type) {
        return "g64".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);

        header = new G64Header();
    }

    /**
     * Create sector data
     *
     * @param inData        Sector data
     * @param diskNumber    Disk number
     * @param trackNumber   Track number
     * @param sideNumber    Side number
     * @param numOfSectors  Number of sectors
     * @param sectorNumber  Sector number
     * @param sectorSize    Sector size
     * @param singleDensity Whether single density
     * @param track         [in,out] Track
     * @return Sector size including header
     */
    private int parseSector(byte[] inData, int diskNumber, int trackNumber, int sideNumber, int numOfSectors,
                            int sectorNumber, int sectorSize, boolean singleDensity, DiskImageTrack track) {

        // Create a new image sector
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, numOfSectors, false, 0);
        track.add(sector);

        // Copy plain data
        sector.copy(inData, sectorSize);

        sector.setSingleDensity(singleDensity);
        sector.clearModify();

        // Return sector size (with header)
        return sector.getSize();
    }

    /**
     * GCR (5‑bit) -> HEX (4‑bit) map
     */
    private static final int[] GCR_BIN_MAP = {
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0x08, 0x00, 0x01, 0xff, 0x0c, 0x04, 0x05,
            0xff, 0xff, 0x02, 0x03, 0xff, 0x0f, 0x06, 0x07,
            0xff, 0x09, 0x0a, 0x0b, 0xff, 0x0d, 0x0e, 0xff
    };

    /**
     * Decode GCR data
     *
     * @param inData   Input data
     * @param inBitLen Input data length (in bits)
     * @param outData  [out] Output data
     * @param outLen   Output data buffer size
     */
    private static int decodeGCR(byte[] inData, int bitPos, int inBitLen, byte[] outData, int outPos, int outLen) {
        while (bitPos < inBitLen && outPos < outLen) {
            int adat = 0;
            for (int i = 0; i < 2; i++) {
                int pos = bitPos >> 3;
                int bit = bitPos & 7;

                int dat = ((inData[pos] & 0xff) << 8) | ((inData[pos + 1] & 0xff));

                int idat = (dat >> (11 - bit));
                idat &= 0x1f;

                // 5‑bit to 4‑bit conversion
                int ndat = GCR_BIN_MAP[idat] & 0xff;

                adat = adat << 4;
                adat |= ndat;

                bitPos += 5;
            }
            outData[outPos] = (byte) adat;
            outPos++;
        }
        return outPos;
    }

    /**
     * Create track data
     *
     * @param iStream    Disk image
     * @param diskNumber Disk number
     * @param sideNumber Side number
     * @param offsetPos  Offset number
     * @param offset     Offset position
     * @param disk       [in,out] Disk
     * @return -1: Error or end, >0: Track size
     */
    private int parseTrack(InputStream iStream, int diskNumber, int sideNumber, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        // Read track size
        byte[] buf = new byte[2];
        if (iStream.readNBytes(buf, 0, 2) != 2) {
            return -1;
        }

        int trackSize = ByteUtil.readBeShort(buf, 0) & 0xffff;
        if (trackSize == 0) {
            return -1;
        }

        byte[] inData = new byte[trackSize + 32];
        int inSize = inData.length;
        byte[] outData = new byte[trackSize];
        int outSize = outData.length;

        int len = iStream.readNBytes(inData, 0, inSize);

        List<byte[]> sectorHeaders = new ArrayList<>();
        List<byte[]> sectorData = new ArrayList<>();

        int inPos = 0;
        int outPos = 0;
        while (inPos < trackSize) {
            // Skip header sync ($ff) – usually 4–5 bytes
            while (inData[inPos] == (byte) 0xff && inPos < trackSize) {
                inPos++;
            }
            if (inPos >= trackSize) {
                break;
            }

            // Header GCR – usually 10 bytes
            len = decodeGCR(inData, inPos, 80, outData, outPos, outSize - outPos);
            sectorHeaders.add(Arrays.copyOfRange(outData, outPos, outPos + len));
            inPos += 10;
            outPos += len;

            // Skip header gap
            while (inData[inPos] != (byte) 0xff && inPos < trackSize) {
                inPos++;
            }
            if (inPos >= trackSize) break;

            // Skip data sync ($ff) – usually 4–5 bytes
            while (inData[inPos] == (byte) 0xff && inPos < trackSize) {
                inPos++;
            }
            if (inPos >= trackSize) break;

            // Data GCR – usually 325 bytes
            len = decodeGCR(inData, inPos, 2600, outData, outPos, outSize - outPos);
            sectorData.add(Arrays.copyOfRange(outData, outPos, outPos + len));
            inPos += 325;
            outPos += len;

            // Skip data gap
            while (inData[inPos] != (byte) 0xff && inPos < trackSize) {
                inPos++;
            }
            if (inPos >= trackSize) break;
        }

        // Determine track number
        G64SectorHeader sectorHeader = new G64SectorHeader();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorHeaders.getFirst()), sectorHeader);
        int trackNumber = sectorHeader.trackNumber;

        // Create track
        int d88TrackSize = 0;
        DiskImageTrack track = disk.newImageTrack(trackNumber, sideNumber, offsetPos, 1);
        disk.setMaxTrackNumber(trackNumber);

        for (int i = 0; i < sectorData.size(); i++) {
            // Create a sector
            sectorHeader = new G64SectorHeader();
            Serdes.Util.deserialize(new ByteArrayInputStream(sectorHeaders.get(i)), sectorHeader);
            G64SectorData sd = new G64SectorData();
            Serdes.Util.deserialize(new ByteArrayInputStream(sectorData.get(i)), sectorHeader);

            d88TrackSize = parseSector(
                    sd.data,
                    diskNumber,
                    sectorHeader.trackNumber,
                    sideNumber,
                    sectorHeaders.size(),
                    sectorHeader.sectorNumber,
                    256,
                    false,
                    track);
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
     * Analyze file
     *
     * @param iStream    Data to be analyzed
     * @param diskNumber Disk number
     * @return -1: finish parsing, 0: parse next disk
     */
    private int parseDisk(InputStream iStream, int diskNumber) throws IOException {
        // Parse header
        if (parseHeader(iStream, diskNumber) < 0) {
            return -1;
        }

        // Read offsets to track data
        int len = 0;
        List<Integer> offsets = new ArrayList<>();
        boolean hasHalfTrack = false;
        for (int pos = 0; pos < header.numOfTracks; pos++) {
            byte[] buf = new byte[4];
            len = iStream.readNBytes(buf, 0, 4);
            if (len < 4) {
                return -1;
            }
            int offset = ByteUtil.readLeShort(buf, 0) & 0xffff;

            offsets.add(offset);
            // Whether it has a half track
            hasHalfTrack |= offset != 0 && (pos & 1) != 0;
        }

        // Skip speed zone data
        int size = header.numOfTracks * 4;
        int current = (int) ((SeekableDataInputStream) iStream).position();
        ((SeekableDataInputStream) iStream).position(current + size); // wxFromCurrent

        // Create disk image
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // Parse tracks
        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < header.numOfTracks; pos++) {
            size = offsets.get(pos);
            if (size == 0) {
                // skip empty track
                continue;
            }

            ((SeekableDataInputStream) iStream).position(size);
            int offset = parseTrack(iStream, diskNumber, hasHalfTrack ? pos & 1 : 0, d88OffsetPos, d88Offset, disk);
            if (offset == -1) {
                return -1;
            }
            d88Offset += offset;
            d88OffsetPos++;
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

        return 0;
    }

    /**
     * Header analysis
     *
     * @param iStream    Data to be analyzed
     * @param diskNumber Disk number
     * @return -1: Error, 0:
     */
    private int parseHeader(InputStream iStream, int diskNumber) throws IOException {
        int len = iStream.available();
        if (len < G64Header.SIZE) {
            // too short
            return -1;
        }
        Serdes.Util.deserialize(iStream, header);
        if (!Arrays.equals(header.sig, "GCR-1541".getBytes())) {
            // not an image
            return -1;
        }
        if (header.numOfTracks == 0) {
            return -1;
        }

        return 0;
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    /**
     * Check
     *
     * @param iStream Data to be analyzed
     * @return 1: Display selection dialog, 0: Normal (display selection dialog when there are multiple candidates)
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        if (parseHeader(iStream, 0) < 0) {
            return -1;
        }

        return 0;
    }

    /**
     * Analyze file
     *
     * @param iStream   Data to be analyzed
     * @param diskParam Parameters usually unnecessary
     * @return 0: normal, -1: error exists, 1: warning exists
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        for (int diskNumber = 0; diskNumber < 1; diskNumber++) {
            if (parseDisk(iStream, diskNumber) < 0) {
                break;
            }
        }
        return result.getValid();
    }
}
