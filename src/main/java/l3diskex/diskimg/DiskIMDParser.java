/*
 * Author: Sasaji (translated to Java)
 */

package l3diskex.diskimg;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.util.serdes.Serdes;


// ------------------------------------------------------------------
// The translated parser
// ------------------------------------------------------------------

/**
 * IMageDisk IMD format disk image parser.
 */
public class DiskIMDParser extends DiskImageParser {
    /* ----------------------------------------------------------------
     *  Data types translated from the header
     * ----------------------------------------------------------------
     */

    /** IMD track header (packed 1 byte each) */
    private static class ImdTrackHeader {

        int mode;
        int track_num;
        int head_num_n_flg;
        int num_of_sectors;
        int sector_size_n;

        public static final int SIZE = 5;
    }

    /* ----------------------------------------------------------------
     *  Constructor / Destructor
     * ----------------------------------------------------------------
     */
    public DiskIMDParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
    }

    public void destroy() { /* no‑op */ } // mimics C++ destructor

    /* ----------------------------------------------------------------
     *  Helper methods for reading from InputStream
     * ----------------------------------------------------------------
     */
    private int readUnsignedByte(InputStream is) throws IOException {
        int b = is.read();
        if (b == -1) throw new EOFException();
        return b & 0xFF;
    }

    private void readFully(InputStream is, byte[] buf, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int r = is.read(buf, off + n, len - n);
            if (r == -1) throw new EOFException();
            n += r;
        }
    }

    /* ----------------------------------------------------------------
     *  ParseSector
     * ----------------------------------------------------------------
     */
    private int parseSector(InputStream istream,
                            int diskNumber,
                            int trackNumber,
                            int sideNumber,
                            int sectorNums,
                            int sectorNumber,
                            int sectorSize,
                            boolean singleDensity,
                            DiskImageTrack track) throws IOException {

        // Read header byte
        int h_sector;
        try {
            h_sector = readUnsignedByte(istream);
        } catch (EOFException e) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }

        // Create sector
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber,
                sectorNumber, sectorSize,
                sectorNums, false, 0);
        track.add(sector);
        byte[] buffer = sector.getSectorBuffer();

        if (h_sector == 0) {
            // missing data
            sector.fill((byte) 0);
        } else {
            h_sector--;                          // first byte was "1" for data
            if ((h_sector & 1) != 0) {           // compressed data
                int ch = istream.read();
                if (ch == -1) ch = 0;
                sector.fill((byte) (ch & 0xFF));
            } else {                             // plain data
                readFully(istream, buffer, 0, sectorSize);
            }
            if ((h_sector & 0x02) != 0) {
                sector.setDeletedMark(true);
            }
            // 0x04 bit (bad sector) is ignored
        }

        sector.setSingleDensity(singleDensity);
        sector.clearModify();

        // Return sector size (including header byte)
        return sector.getSize();
    }

    /* ----------------------------------------------------------------
     *  ParseTrack
     * ----------------------------------------------------------------
     */
    private int parseTrack(InputStream istream,
                           int diskNumber,
                           int offsetPos,
                           int offset,
                           DiskImageDisk disk) throws IOException {

        // Read track header
        byte[] headerBuf = new byte[ImdTrackHeader.SIZE];
        try {
            readFully(istream, headerBuf, 0, ImdTrackHeader.SIZE);
        } catch (EOFException e) {
            return -1; // end of file
        }
        ImdTrackHeader hTrack = new ImdTrackHeader();
        Serdes.Util.deserialize(new ByteArrayInputStream(headerBuf), hTrack);

        if (hTrack.mode > 5) {
            result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
            return -1;
        }
        if ((hTrack.head_num_n_flg & 0x0F) > 1) {
            result.setError(DiskResult.ERRV_ID_SIDE, diskNumber,
                    hTrack.track_num, hTrack.track_num,
                    hTrack.head_num_n_flg & 0x0F, 1);
            return -1;
        }
        if (hTrack.sector_size_n > 6) {
            result.setError(DiskResult.ERRV_SECTOR_SIZE_HEADER, diskNumber,
                    hTrack.sector_size_n);
            return -1;
        }

        int sectorSize = 128 << hTrack.sector_size_n;

        // Sector, track and head maps
        byte[] sectorMap = null;
        byte[] trackMap = null;
        byte[] headMap = null;

        if (hTrack.num_of_sectors > 0) {
            sectorMap = new byte[hTrack.num_of_sectors];
            trackMap = new byte[hTrack.num_of_sectors];
            headMap = new byte[hTrack.num_of_sectors];

            // sector map
            readFully(istream, sectorMap, 0, hTrack.num_of_sectors);

            // cylinder map (track map)
            if ((hTrack.head_num_n_flg & 0x80) != 0) {
                readFully(istream, trackMap, 0, hTrack.num_of_sectors);
            } else {
                Arrays.fill(trackMap, (byte) hTrack.track_num);
            }

            // head map
            if ((hTrack.head_num_n_flg & 0x40) != 0) {
                readFully(istream, headMap, 0, hTrack.num_of_sectors);
            } else {
                Arrays.fill(headMap, (byte) (hTrack.head_num_n_flg & 0x0F));
            }
        }

        if (sectorSize * hTrack.num_of_sectors > 32768) {
            result.setError(DiskResult.ERRV_DISK_TOO_LARGE, diskNumber);
            return -1;
        }

        // Create track
        DiskImageTrack track = disk.newImageTrack(hTrack.track_num,
                hTrack.head_num_n_flg & 0x0F,
                offsetPos, 1);
        disk.setMaxTrackNumber(hTrack.track_num);

        int d88TrackSize = 0;
        for (int pos = 0; pos < hTrack.num_of_sectors && result.getValid() >= 0; pos++) {
            d88TrackSize += parseSector(istream,
                    diskNumber,
                    trackMap[pos] & 0xFF,
                    headMap[pos] & 0xFF,
                    hTrack.num_of_sectors,
                    sectorMap[pos] & 0xFF,
                    sectorSize,
                    hTrack.mode <= 2,
                    track);
        }

        if (result.getValid() >= 0) {
            track.calcInterleave();
        }
        if (result.getValid() >= 0) {
            track.setSize(d88TrackSize);
            track.setSideNumber(track.getMajorIDH());
            disk.add(track);
            disk.setOffset(offsetPos, offset);
        } else {
            // track would be discarded (garbage‑collected)
        }

        return d88TrackSize;
    }

    /* ----------------------------------------------------------------
     *  ParseDisk
     * ----------------------------------------------------------------
     */
    private int parseDisk(InputStream istream, int diskNumber) throws IOException {
        // skip comment line at the start of the stream
        int ch = 0;
        while (ch != 0x1A && ch != -1) {
            ch = istream.read();
        }
        if (ch == -1) return -1;

        DiskImageDisk disk = file.newImageDisk(diskNumber);
        int d88Offset = disk.getOffsetStart();   // header size
        int d88OffsetPos = 0;
        int limitOffsetPos = disk.getCreatableTracks();

        for (int pos = 0; pos < 204; pos++) {
            int offset = parseTrack(istream, diskNumber, d88OffsetPos,
                    d88Offset, disk);
            if (offset == -1) break;
            d88Offset += offset;
            d88OffsetPos++;
            if (d88OffsetPos >= limitOffsetPos) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE,
                        diskNumber, d88Offset);
            }
        }

        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            DiskParam diskParam = disk.calcMajorNumber();
            if (diskParam != null) disk.setDensity(diskParam.getParamDensity());
            file.add(disk, modFlags);
        }

        return 0;
    }

    /* ----------------------------------------------------------------
     *  Check (simple version, no hints/parameters used)
     * ----------------------------------------------------------------
     */
    @Override
    public int check(InputStream istream,
                     List<DiskTypeHint> hints,
                     DiskParam diskParam,
                     List<DiskParam> params,
                     DiskParam manualParam) {
        return -1;  // not implemented
    }

    /* ----------------------------------------------------------------
     *  Public interface
     * ----------------------------------------------------------------
     */

    /**
     * Check if the stream contains a valid IMD image.
     *
     * @return 1 if a dialog should be shown (unused here)
     * @return 0 if everything is OK
     */
    @Override
    public int check(InputStream istream) throws IOException {
        istream.reset(); // must be a markable stream

        byte[] header = new byte[31];
        int len = istream.read(header);
        if (len < header.length) return -1;
        if (!Arrays.equals(Arrays.copyOfRange(header, 0, 4), "IMD ".getBytes())) return -1;
        if (header[4] != '1' || header[5] != '.') return -1;

        // skip comment line
        int ch = 0;
        while (ch != 0x1A && ch != -1) ch = istream.read();
        if (ch == -1) return -1;

        return 0;
    }

    /**
     * Parse the IMD file.
     *
     * @param istream   the input stream
     * @param diskParam optional disk parameters (ignored here)
     * @return 0 on success, -1 on error, 1 on warning
     */
    @Override
    public int parse(InputStream istream, DiskParam diskParam) throws IOException {
        istream.reset();

        for (int diskNumber = 0; diskNumber < 1; diskNumber++) {
            if (parseDisk(istream, diskNumber) < 0) break;
        }

        return result.getValid();
    }
}
