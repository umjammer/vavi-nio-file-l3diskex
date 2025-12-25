///
/// Copyright (c) Sasaji. All rights reserved.
///

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
import vavi.util.serdes.Serdes;


/**
 * TRS-80 DMK disk parser
 *
 * @see "https://electrickery.hosting.philpem.me.uk/comp/trs80-4p/dmkeilImages/trstech.htm"
 */
public class DiskDmkParser extends DiskImageParser {

    /** TRS-80 DMK header */
    @Serdes(bigEndian = false)
    public static class TrsDmkHeader {

        /** 0x00: no, 0xff: yes */
        public byte writeProtected;
        public byte numOfTracks;
        /** track length */
        public short trackLength;
        public byte flags;
        public byte[] reserved = new byte[7];
        /** 0x12345678: Real Disk, 0x00000000: Virtual Disk */
        public int signature;

        public static final int SIZE = 16;
    }

    /** TRS-80 DMK track */
    @Serdes(bigEndian = false)
    public static class TrsDmkTrack {

        /** pointer to sector IDAMs */
        public short[] pointers = new short[64];

        public static final int SIZE = 64 * 2;
    }

    /** TRS-80 DMK sector id */
    @Serdes(bigEndian = false)
    public static class TrsDmkSectorId {

        public byte idam;
        public byte c;
        public byte h;
        public byte r;
        public byte n;
        public short crc;

        public static final int SIZE = 6;
    }

    private static final int DMK_IDAM_DENSITY = 0x8000;
    private static final int DMK_IDAM_OFFSET = 0x3fff;

    private static final int DMK_DISK_REAL = 0x1234_5678;
    private static final int DMK_DISK_VIRTUAL = 0x0000_0000;

    /** */
    private static final class AMark {

        public final byte[] id;
        public final int deleted;

        public AMark(String id, int deleted) {
            this.id = id != null ? id.getBytes() : null;
            this.deleted = deleted;
        }
    }

    /** */
    private static final AMark[][] AMARKS = {
            {
                    new AMark("\u0000\u0000\u0000\u00fb", 0),
                    new AMark("\u0000\u0000\u0000\u00f8", 0), // TRSDOS 1.3 SYSTEM
                    new AMark("\u0000\u0000\u0000\u00fa", 0), // TRSDOS DIR
            },
            {
                    new AMark("\u00a1\u00a1\u00a1\u00fb", 0),
                    new AMark("\u00a1\u00a1\u00a1\u00f8", 0), // TRSDOS 1.3 SYSTEM
                    new AMark("\u00a1\u00a1\u00a1\u00fa", 0), // TRSDOS DIR
            }
    };

    //
    // Convert TRS-80 DMK format to D88 format
    //

    @Override
    public boolean isSupported(String type) {
        return "dmk".equalsIgnoreCase(type);
    }

    @Override
    public boolean needsCheck() {
        return true;
    }

    @Override
    public boolean checkCondition(InputStream stream) throws IOException {
        return check(stream) >= 0;
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
    }

    /** Find data mark */
    private static boolean findDataMark(InputStream iStream, int sectorSize, boolean doubleDensity, int[] deleted) throws IOException {
        byte[] buf = new byte[64];

        int len = iStream.readNBytes(buf, 0, buf.length);
        if (len < buf.length) {
            return false;
        }

        boolean found = false;
        int pos;
        int den = doubleDensity ? 1 : 0;

        for (pos = 0; pos < len - 4; pos++) {
            for (int i = 0; AMARKS[den][i].id != null; i++) {
                if (Arrays.equals(Arrays.copyOfRange(buf, pos, pos + 4), AMARKS[den][i].id)) {
                    found = true;
                    deleted[0] = AMARKS[den][i].deleted;
                    break;
                }
            }
            if (found) break;
        }

        if (!found) {
            return false;
        }

        // adjust position
        int offset = pos + 4 - buf.length;
        int current = (int) ((SeekableDataInputStream) iStream).position();
        ((SeekableDataInputStream) iStream).position(current + offset); // wxFromCurrent

        return true;
    }

    /** Create sector data */
    private int parseSector(InputStream iStream, int sectorNums, int flags, DiskImageTrack track) throws IOException {
        int len = iStream.available();
        if (len != TrsDmkSectorId.SIZE) {
            result.setError(DiskResult.ERRV_SECTORS_HEADER, 0);
            return 0;
        }
        TrsDmkSectorId id = new TrsDmkSectorId();
        Serdes.Util.deserialize(iStream, id);

        int trackNumber = id.c & 0xff;
        int sideNumber = id.h & 0xff;
        int sectorNumber = id.r & 0xff;
        int sectorSizeCode = id.n & 0xff;

        if (sectorSizeCode > 7) {
            // Sector size is too large
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, trackNumber, sideNumber, sectorNumber, sectorSizeCode, sectorSizeCode);
            return 0;
        }

        int sectorSize = 128 << sectorSizeCode;

        // Find start position of data
        int[] deleted = new int[1];
        if (!findDataMark(iStream, sectorSize, (flags & DMK_IDAM_DENSITY) != 0, deleted)) {
            result.setError(DiskResult.ERRV_NO_SECTOR, 0, sectorNumber, trackNumber, sideNumber);
            return 0;
        }

        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, sectorNums, false, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();

        len = iStream.readNBytes(buf, 0, siz);
        if (len < siz) {
            // Not enough file data
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }

        sector.setDeletedMark(deleted[0] != 0);
        sector.clearModify();

        // Return size of this sector data
        return sector.getSize();
    }

    /** Create track data */
    private int parseTrack(InputStream iStream, int trackSize, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        int file_offset = (int) ((SeekableDataInputStream) iStream).position();

        int len = iStream.available();
        if (len != TrsDmkTrack.SIZE) {
            result.setError(DiskResult.ERR_NO_TRACK, 0);
            return 0;
        }
        TrsDmkTrack trackHeader = new TrsDmkTrack();
        Serdes.Util.deserialize(iStream, trackHeader);

        // Calculate number of sectors
        int numOfSectors = 0;
        for (int pos = 0; pos < 64; pos++) {
            if (trackHeader.pointers[pos] == 0) {
                break;
            }
            numOfSectors++;
        }

        DiskImageTrack track = disk.newImageTrack(0, 0, offsetPos, 1);

        int d88TrackSize = 0;
        for (int pos = 0; pos < numOfSectors && result.getValid() >= 0; pos++) {
            int ptr = trackHeader.pointers[pos] & 0xffff;
            int nextOffset = (ptr & DMK_IDAM_OFFSET);
            // move position in file
            ((SeekableDataInputStream) iStream).position(file_offset + nextOffset); // wxFromStart

            d88TrackSize += parseSector(iStream, numOfSectors, (ptr & ~DMK_IDAM_OFFSET), track);
        }

        if (result.getValid() >= 0) {
            // Calculate interleave
            track.calcInterleave();

            // Set track size
            track.setSize(d88TrackSize);
            // Track number matches ID C of each sector
            int track_number = track.getMajorIDC();
            track.setTrackNumber(track_number);
            // Side number matches ID H of each sector
            track.setSideNumber(track.getMajorIDH());

            // Add to disk
            disk.add(track);
            // Set offset
            disk.setOffset(offsetPos, offset);
            // Set maximum track number
            disk.setMaxTrackNumber(track_number);

            disk.clearModify();
        }

        // To the start of the next track data
        ((SeekableDataInputStream) iStream).position(file_offset + trackSize); // wxFromStart

        return d88TrackSize;
    }

    /** Analyze disk */
    private int parseDisk(InputStream iStream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        int len = iStream.available();
        if (len != TrsDmkHeader.SIZE) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return 0;
        }
        TrsDmkHeader header = new TrsDmkHeader();
        Serdes.Util.deserialize(iStream, header);

        int max_tracks = header.numOfTracks & 0xff;

        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        int limitOffsetPos = disk.getCreatableTracks();
        for (int pos = 0; pos < 204 && pos < max_tracks; pos++) {
            d88Offset += parseTrack(iStream, header.trackLength & 0xffff, d88OffsetPos, d88Offset, disk);
            d88OffsetPos++;
            if (d88OffsetPos >= limitOffsetPos) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88Offset);
            }
        }
        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            // Add disk
            DiskParam diskParam = disk.calcMajorNumber();
            if (diskParam != null) {
                disk.setDensity(diskParam.getParamDensity());
            }
            disk.setWriteProtect((header.writeProtected & 0xff) == 0xff);

            file.add(disk, modFlags);
        }

        return d88Offset;
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    /** Check if it is TRS-80 DMK file */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        int len = iStream.available();
        if (len < TrsDmkHeader.SIZE) {
            // too short
            return -1;
        }
        TrsDmkHeader header = new TrsDmkHeader();
        Serdes.Util.deserialize(iStream, header);

        ((SeekableDataInputStream) iStream).position(0);

        // check header
        // first data is 0x00 or 0xff (write protected flag)
        if ((header.writeProtected & 0xff) != 0x00 && (header.writeProtected & 0xff) != 0xff) {
            return -1;
        }
        // signature
        if (header.signature != DMK_DISK_REAL && header.signature != DMK_DISK_VIRTUAL) {
            return -1;
        }
        // file size
        if ((header.numOfTracks & 0xff) * (header.trackLength & 0xffff) + TrsDmkHeader.SIZE < len /* file size */) {
            // too short
            return -1;
        }

        return 0;
    }

    /** Analyze TRS-80 DMK file */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        parseDisk(iStream);
        return result.getValid();
    }
}
