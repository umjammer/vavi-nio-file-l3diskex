/**
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParser.DiskImageParser;


/**
 * DiskDskParser implementation
 */
public class DiskDskParser extends DiskImageParser {

    /**/
    /*                     C++ struct equivalents                          */
    /**/
    static class CPCDSKHeader {

        final static int SIZE = 34 + 14 + 1 + 1 + 2 + 204;
        public byte[] ident = new byte[34];
        public byte[] creator = new byte[14];
        public byte num_of_tracks;
        public byte num_of_sides;
        public short track_size;          /* little‑endian 16‑bit */
        public byte[] track_sizes = new byte[204];

        public static CPCDSKHeader fromBytes(byte[] buf) {
            CPCDSKHeader h = new CPCDSKHeader();
            System.arraycopy(buf, 0, h.ident, 0, 34);
            System.arraycopy(buf, 34, h.creator, 0, 14);
            h.num_of_tracks = buf[48];
            h.num_of_sides = buf[49];
            h.track_size = (short) ((buf[50] & 0xFF) | ((buf[51] & 0xFF) << 8));
            System.arraycopy(buf, 52, h.track_sizes, 0, 204);
            return h;
        }
    }

    public static class CPCDSKSector {

        public byte C, H, R, N, fdc_status_1, fdc_status_2;
        public short data_length;   /* little‑endian 16‑bit */

        public static CPCDSKSector fromBytes(byte[] buf, int offset) {
            CPCDSKSector s = new CPCDSKSector();
            s.C = buf[offset];
            s.H = buf[offset + 1];
            s.R = buf[offset + 2];
            s.N = buf[offset + 3];
            s.fdc_status_1 = buf[offset + 4];
            s.fdc_status_2 = buf[offset + 5];
            s.data_length = (short) ((buf[offset + 6] & 0xFF) | ((buf[offset + 7] & 0xFF) << 8));
            return s;
        }
    }

    static class CPCDSKTrack {

        public byte[] ident = new byte[12];
        public int track_number;
        public int side_number;
        public int sector_size;
        public int num_of_sectors;
        public int gap3_length;
        public int filler_byte;
        public CPCDSKSector[] sectors = new CPCDSKSector[29];

        public static CPCDSKTrack fromBytes(byte[] buf) {
            CPCDSKTrack t = new CPCDSKTrack();
            System.arraycopy(buf, 0, t.ident, 0, 12);
            int idx = 12 + 4;                      /* skip unused1[4]   */
            t.track_number = buf[idx++] & 0xFF;
            t.side_number = buf[idx++] & 0xFF;
            idx += 2;                              /* skip unused2[2]   */
            t.sector_size = buf[idx++] & 0xFF;
            t.num_of_sectors = buf[idx++] & 0xFF;
            t.gap3_length = buf[idx++] & 0xFF;
            t.filler_byte = buf[idx++] & 0xFF;
            /* read 29 sectors, each 8 bytes */
            for (int i = 0; i < 29; i++) {
                int base = idx + i * 8;
                t.sectors[i] = CPCDSKSector.fromBytes(buf, base);
            }
            return t;
        }
    }

    /* Instance variables – mirroring the C++ class members */
    private final int m_is_extended;   /* 0 = normal, 1 = extended */

    /* ---------------------------------------------------------------- */
    /*                       Constructor & basic access                 */
    /* ---------------------------------------------------------------- */
    public DiskDskParser(DiskImage.DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
        this.m_is_extended = 0;     /* normal mode by default */
    }

    /* ---------------------------------------------------------------- */
    /*                       Public API methods                         */
    /* ---------------------------------------------------------------- */

    /**
     * Parse a sector from the given stream.
     *
     * @param in          Input stream containing the sector data
     * @param sector_nums Number of sectors (unused in this stub)
     * @param id          CPCDSKSector object describing the sector
     * @param track       DiskImageTrack to which the sector belongs
     * @return The size of the parsed sector
     */
    public int parseSector(InputStream in, int sector_nums,
                           CPCDSKSector id, DiskImageTrack track) throws IOException {
        int sectorSize = (128 << (id.N & 0xFF));
        if (sectorSize > 7) {          /* error condition – keep original logic */
            result.setError(0x01);   /* dummy error code */
            return 0;
        }
        int trackNum = id.C & 0xFF;
        int sideNum = id.H & 0xFF;
        int sectorNum = id.R & 0xFF;
        /* create sector object */
        DiskImageSector sector = track.newImageSector(trackNum, sideNum, sectorNum,
                sectorSize, sector_nums, false, 0);
        track.add(sector);
        byte[] buf = sector.getSectorBuffer();
        int len = in.read(buf, 0, sectorSize);
        if (len < sectorSize) {
            result.setError(0x02);   /* dummy error code for truncated read */
        }
        /* Skip padding if the disk is in extended mode */
        if (m_is_extended != 0 && id.data_length > sectorSize) {
            int skipLen = id.data_length - sectorSize;
            int skipped = (int) in.skip(skipLen);
            if (skipped != skipLen) {
                result.setError(0x03);   /* dummy error code for skip failure */
            }
        }
        sector.clearModify();
        return sector.getSize();
    }

    /**
     * Parse a track from the stream.
     *
     * @param in        Input stream containing the track data
     * @param offsetPos Offset position in the disk image
     * @param trackSize Size of the track in the original DSK format
     * @return The size of the parsed track
     */
    public int parseTrack(InputStream in, int trackSize, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        CPCDSKTrack trk = readTrackHeader(in);
        /* Validate ident */
        String identStr = new String(trk.ident, StandardCharsets.US_ASCII);
        if (!identStr.equals("Track-Info\r\n")) {
            result.setError(0x04);   /* dummy error code for wrong ident */
            return 0;
        }

        DiskImageTrack track = disk.newImageTrack(trk.track_number, trk.side_number, offsetPos, 1);
        disk.setMaxTrackNumber(trk.track_number);

        int d88TrackSize = 0;
        for (int i = 0; i < trk.num_of_sectors; i++) {
            CPCDSKSector sector = trk.sectors[i];
            d88TrackSize += parseSector(in, trk.num_of_sectors,
                    sector, track);
        }

        if (result.getValid() >= 0) {
            track.calcInterleave();

            track.setSize(d88TrackSize);
            track.setSideNumber(track.getMajorIDH());
            disk.add(track);
            disk.setOffset(offsetPos, offset);
        }

        return d88TrackSize;
    }

    /**
     * Parse an entire DSK image.
     *
     * @param in Input stream containing the DSK image
     * @return True if parsing succeeded, false otherwise
     */
    public int parseDisk(InputStream in) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        CPCDSKHeader header = readHeader(in);
//        if (len != CPCDSKHeader.SIZE) {
//            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
//            return 0;
//        }

        disk.setName(header.creator, header.creator.length);
        int max_tracks = header.num_of_tracks * header.num_of_sides;

        int d88_offset = disk.getOffsetStart();    // header size
        int d88_offset_pos = 0;
        for (int pos = 0; pos < 204 && pos < max_tracks; pos++) {
            d88_offset += parseTrack(in
                    , m_is_extended != 0 ? (int) header.track_sizes[pos] * 256 : header.track_size
                    , d88_offset_pos, d88_offset, disk);
            d88_offset_pos++;
            if (d88_offset_pos >= disk.getCreatableTracks()) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88_offset);
            }
        }
        disk.setSize(d88_offset);

        if (result.getValid() >= 0) {
            // ディスクを追加
            DiskParam disk_param = disk.calcMajorNumber();
            if (disk_param != null) {
                disk.setDensity(disk_param.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return d88_offset;
    }

    /**
     * Check whether the DSK image is valid (overloaded version).
     *
     * @param in Input stream to check
     * @return true if valid, false otherwise
     */
    @Override
    public int check(InputStream in) throws IOException {
        /* Reset the result to a clean state */
        result = new DiskResult();
        return parseDisk(in);
    }

    /**
     * Overloaded check – returns the same as above but also outputs a string.
     *
     * @param in Input stream to check
     * @return String description of the result
     */
    public String checkDetailed(InputStream in) throws IOException {
        int ok = check(in);
        return ok != -1 ? "DSK image is valid." : "DSK image is invalid.";
    }

    /**
     * Parse the DSK image from the stream.
     *
     * @param in Input stream containing the DSK image
     * @return Validity code from DiskResult
     */
    public int parse(InputStream in) throws IOException {
        parseDisk(in);
        return result.getValid();
    }

    /* ---------------------------------------------------------------- */
    /*                         Header parsing helpers                    */
    /* ---------------------------------------------------------------- */

    private CPCDSKHeader readHeader(InputStream in) throws IOException {
        byte[] buf = new byte[256];
        int read = in.read(buf);
        if (read != 256) throw new IOException("Cannot read full header");
        return CPCDSKHeader.fromBytes(buf);
    }

    private CPCDSKTrack readTrackHeader(InputStream in) throws IOException {
        byte[] buf = new byte[256];
        int read = in.read(buf);
        if (read != 256) throw new IOException("Cannot read full track header");
        return CPCDSKTrack.fromBytes(buf);
    }
}
