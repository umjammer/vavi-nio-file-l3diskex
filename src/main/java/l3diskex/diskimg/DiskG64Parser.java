/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParser.DiskImageParser;


/**
 * Stub and helper classes that mimic the minimal behaviour of the
 * original C++ code.  The goal is a *naïve* literal translation, so
 * the implementation favours readability over optimal performance.
 */
public class DiskG64Parser extends DiskImageParser {

    /* -----------------------------------------------------------------
     *  Data structures
     * ----------------------------------------------------------------- */

    /** G64 header (packed, 1‑byte alignment) */
    private static final class G64Header {

        /** 8‑byte signature ("GCR-1541") */
        public byte[] sig = new byte[8];
        public byte version;
        public byte num_of_tracks;
        public short max_track_size;        // wxUint16

        /** Size in bytes (for reading) */
        public static final int SIZE = 8 + 1 + 1 + 2; // 12
    }

    /** G64 sector header (packed, 1‑byte alignment) */
    private static final class G64SectorHeader {

        public byte block_id;
        public byte format_id0;
        public byte format_id1;
        public byte track_number;
        public byte sector_number;
        public byte reserved1;
        public byte reserved2;
        // The original C++ struct had a 2‑byte reserved array
    }

    /** G64 sector data (packed, 1‑byte alignment) */
    private static final class G64SectorData {

        public byte block_id;
        public byte[] data = new byte[256];
        public byte chksum;
        public byte reserved1;
        public byte reserved2;
    }

    /* -----------------------------------------------------------------
     *  Fields
     * ----------------------------------------------------------------- */
    private final G64Header m_header = new G64Header();

    /* -----------------------------------------------------------------
     *  Constructor / Destructor
     * ----------------------------------------------------------------- */
    public DiskG64Parser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
        Arrays.fill(this.m_header.sig, (byte) 0);
        this.m_header.version = 0;
        this.m_header.num_of_tracks = 0;
        this.m_header.max_track_size = 0;
    }

    /* -----------------------------------------------------------------
     *  Parse sector (creates a plain sector)
     * ----------------------------------------------------------------- */
    private int parseSector(
            byte[] indata,
            int disk_number,
            int track_number,
            int side_number,
            int sector_nums,
            int sector_number,
            int sector_size,
            boolean single_density,
            DiskImageTrack track) {

        // 1. Create a new image sector
        DiskImageSector sector = track.newImageSector(
                track_number, side_number, sector_number, sector_size, sector_nums, false, 0);
        track.add(sector);

        // 2. Copy plain data
        sector.copy(indata, sector_size);

        sector.setSingleDensity(single_density);
        sector.clearModify();

        // 3. Return sector size (with header)
        return sector.getSize();
    }

    /* -----------------------------------------------------------------
     *  GCR (5‑bit) -> HEX (4‑bit) map
     * ----------------------------------------------------------------- */
    private static final int[] GCR_BIN_MAP = {
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0x08, 0x00, 0x01, 0xff, 0x0c, 0x04, 0x05,
            0xff, 0xff, 0x02, 0x03, 0xff, 0x0f, 0x06, 0x07,
            0xff, 0x09, 0x0a, 0x0b, 0xff, 0x0d, 0x0e, 0xff
    };

    /* -----------------------------------------------------------------
     *  Decode GCR data
     * ----------------------------------------------------------------- */
    private static int decodeGCR(
            byte[] indata,
            int bitpos,
            int inbitlen,
            byte[] outdata,
            int outpos,
            int outlen) {

        while (bitpos < inbitlen && outpos < outlen) {
            int adat = 0;
            for (int i = 0; i < 2; i++) {
                int pos = bitpos >> 3;          // byte offset
                int bit = bitpos & 7;           // bit offset

                // Build a 16‑bit value from two consecutive bytes
                int dat = ((indata[pos] & 0xFF) << 8) | ((indata[pos + 1] & 0xFF));

                // Extract 5 bits starting at (11 - bit)
                int idat = (dat >> (11 - bit)) & 0x1F;

                // 5‑bit to 4‑bit conversion
                int ndat = GCR_BIN_MAP[idat] & 0xFF;

                adat = (adat << 4) | ndat;

                bitpos += 5;
            }
            outdata[outpos] = (byte) adat;
            outpos++;
        }
        return outpos;
    }

    /* -----------------------------------------------------------------
     *  Parse a single track
     * ----------------------------------------------------------------- */
    private int parseTrack(
            RandomAccessFile raf,
            int disk_number,
            int side_number,
            int offset_pos,
            int offset,
            DiskImageDisk disk) throws IOException {

        /* 1. Read track size (2 bytes, big‑endian) */
        byte[] buffer2 = new byte[2];
        if (raf.read(buffer2) != 2) {
            return -1;
        }
        int trackSize = Short.toUnsignedInt(
                ByteBuffer.wrap(buffer2).order(ByteOrder.BIG_ENDIAN).getShort());

        if (trackSize == 0) {
            return -1;
        }

        /* 2. Allocate temporary buffers */
        byte[] indata = new byte[trackSize + 32];
        int insize = indata.length;
        byte[] outdata = new byte[trackSize];
        int outsize = outdata.length;

        /* 3. Read whole track into memory */
        if (raf.read(indata, 0, insize) != insize) {
            return -1;
        }

        /* 4. Prepare lists for headers / data */
        List<byte[]> sectorHeaders = new ArrayList<>();
        List<byte[]> sectorDatas = new ArrayList<>();

        /* 5. Parse the track */
        int inpos = 0;
        int outpos = 0;
        while (inpos < trackSize) {
            /* Skip header sync ($ff) – usually 4–5 bytes */
            while (indata[inpos] == (byte) 0xFF && inpos < trackSize) {
                inpos++;
            }
            if (inpos >= trackSize) {
                break;
            }

            /* Header GCR – usually 10 bytes (80 bits) */
            int len = decodeGCR(indata, inpos, 80, outdata, outpos, outsize - outpos);
            sectorHeaders.add(Arrays.copyOfRange(outdata, outpos, outpos + len));
            inpos += 10;
            outpos += len;

            /* Skip header gap until next $ff */
            while (indata[inpos] != (byte) 0xFF && inpos < trackSize) {
                inpos++;
            }
            if (inpos >= trackSize) {
                break;
            }

            /* Skip data sync ($ff) – usually 4–5 bytes */
            while (indata[inpos] == (byte) 0xFF && inpos < trackSize) {
                inpos++;
            }
            if (inpos >= trackSize) {
                break;
            }

            /* Data GCR – usually 325 bytes (2600 bits) */
            len = decodeGCR(indata, inpos, 2600, outdata, outpos, outsize - outpos);
            sectorDatas.add(Arrays.copyOfRange(outdata, outpos, outpos + len));
            inpos += 325;
            outpos += len;

            /* Skip data gap until next $ff */
            while (indata[inpos] != (byte) 0xFF && inpos < trackSize) {
                inpos++;
            }
            if (inpos >= trackSize) {
                break;
            }
        }

        /* 6. Determine track number from first sector header */
        if (sectorHeaders.isEmpty()) {
            return -1;
        }
        byte[] shBytes = sectorHeaders.get(0);
        G64SectorHeader sh = new G64SectorHeader();
        sh.block_id = shBytes[0];
        sh.track_number = shBytes[3]; // 4th byte in header

        int trackNumber = sh.track_number;

        /* 7. Create track in the disk image */
        DiskImageTrack track = disk.newImageTrack(
                trackNumber, side_number, offset_pos, 1);
        disk.setMaxTrackNumber(trackNumber);

        /* 8. Add all sectors to the track */
        int d88TrackSize = 0;
        for (int i = 0; i < sectorDatas.size(); i++) {
            byte[] headerBytes = sectorHeaders.get(i);
            byte[] dataBytes = sectorDatas.get(i);

            /* Decode header fields from the 10‑byte GCR header */
            G64SectorHeader sh2 = new G64SectorHeader();
            sh2.block_id = headerBytes[0];
            sh2.track_number = headerBytes[3];
            sh2.sector_number = headerBytes[4];

            /* Decode data (actual sector data) */
            G64SectorData sd = new G64SectorData();
            sd.block_id = dataBytes[0];
            System.arraycopy(dataBytes, 1, sd.data, 0, 256);

            d88TrackSize = parseSector(
                    sd.data,
                    disk_number,
                    sh2.track_number,
                    side_number,
                    sectorHeaders.size(),
                    sh2.sector_number,
                    256,
                    false,
                    track);
        }

        /* 9. Finish track */
        if (result.getValid() >= 0) {
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            track.setSize(d88TrackSize);
            track.setSideNumber(track.getMajorIDH());
            disk.add(track);
            disk.setOffset(offset_pos, offset);
        } else {
            // track will be garbage collected
        }

        return d88TrackSize;
    }

    /* -----------------------------------------------------------------
     *  Parse a disk
     * ----------------------------------------------------------------- */
    private int parseDisk(RandomAccessFile raf, int disk_number) throws IOException {
        /* 1. Parse header */
        if (parseHeader(raf, disk_number) < 0) {
            return -1;
        }

        /* 2. Read offsets to track data */
        List<Integer> offsets = new ArrayList<>();
        boolean hasHalfTrack = false;
        for (int pos = 0; pos < m_header.num_of_tracks; pos++) {
            byte[] buf4 = new byte[4];
            if (raf.read(buf4) != 4) {
                return -1;
            }
            int offset = ByteBuffer.wrap(buf4).order(ByteOrder.BIG_ENDIAN).getInt();
            offsets.add(offset);
            hasHalfTrack |= (offset != 0) && ((pos & 1) != 0);
        }

        /* 3. Skip speed zone data (header size + offsets) */
        int headerSize = (int) (raf.getFilePointer() - (raf.length() - raf.getFilePointer()));
        raf.seek(headerSize + m_header.num_of_tracks * 4);

        /* 4. Create disk image */
        DiskImageDisk disk = file.newImageDisk(disk_number);

        /* 5. Parse tracks */
        int d88OffsetPos = 0;
        int d88Offset = disk.getOffsetStart();
        for (int pos = 0; pos < m_header.num_of_tracks; pos++) {
            int size = offsets.get(pos);
            if (size == 0) {
                continue;                 // empty track
            }
            raf.seek(size);
            int offset = parseTrack(
                    raf,
                    disk_number,
                    hasHalfTrack ? (pos & 1) : 0,
                    d88OffsetPos,
                    d88Offset,
                    disk);
            if (offset < 0) {
                return -1;
            }
            d88Offset += offset;
            d88OffsetPos++;
        }

        /* 6. Finalise disk image */
        if (result.getValid() >= 0) {
            DiskParam major = disk.calcMajorNumber();
            // ... more code could be added here if needed
        }

        return 0;
    }

    /* -----------------------------------------------------------------
     *  Parse header
     * ----------------------------------------------------------------- */
    private int parseHeader(RandomAccessFile raf, int disk_number) throws IOException {
        byte[] headerBytes = new byte[G64Header.SIZE];
        if (raf.read(headerBytes) != G64Header.SIZE) {
            return -1;
        }

        System.arraycopy(headerBytes, 0, m_header.sig, 0, 8);
        m_header.version = headerBytes[8];
        m_header.num_of_tracks = headerBytes[9];
        m_header.max_track_size = ByteBuffer.wrap(headerBytes, 10, 2)
                .order(ByteOrder.BIG_ENDIAN).getShort();

        return 0;
    }

    /* -----------------------------------------------------------------
     *  Check (overloads)
     * ----------------------------------------------------------------- */
    /* 1. Check with RandomAccessFile (used by the parser) */
    private int check(RandomAccessFile raf, int disk_number) throws IOException {
        return parseDisk(raf, disk_number);
    }

    /* -----------------------------------------------------------------
     *  Public API – called by the framework
     * ----------------------------------------------------------------- */
    public void parse(RandomAccessFile raf, int diskNumber) throws IOException {
        if (parseDisk(raf, diskNumber) < 0) {
            result.setValid(-1);
        }
    }
}
