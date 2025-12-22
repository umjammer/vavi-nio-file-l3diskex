///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskImage.IntHashMapUtil;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/** Virtual98 FD disk image parser */
public class DiskVFDParser extends DiskImageParser {

    /** Virtual98 FD format sector header */
    @Serdes(bigEndian = false)
    public static class VfdSectorHeader {

        public static final int SIZE = 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 4;

        @Element(sequence = 1)
        public byte c;
        @Element(sequence = 2)
        public byte h;
        @Element(sequence = 3)
        public byte r;
        @Element(sequence = 4)
        public byte n;
        // if all data is same in sector, set this.
        @Element(sequence = 5)
        public byte data;
        @Element(sequence = 6)
        public byte unknown0;
        @Element(sequence = 7)
        public byte dden;
        @Element(sequence = 8)
        public byte unknown2;
        // -1 (0xffffffff) is no sector data
        @Element(sequence = 9)
        public int start;
    }

    /** Virtual98 FD format track header */
    @Serdes(bigEndian = false)
    public static class VfdTrackHeader {

        public static final int SIZE = VfdSectorHeader.SIZE * 26;

        @Element(sequence = 1)
        public VfdSectorHeader[] sectors = new VfdSectorHeader[26];

        public VfdTrackHeader() {
            for (int i = 0; i < 26; i++) {
                sectors[i] = new VfdSectorHeader();
            }
        }
    }

    /** Virtual98 FD format header */
    @Serdes(bigEndian = false)
    public static class VfdHeader {

        public static final int SIZE = 8 + 0xd4 + VfdTrackHeader.SIZE * 160;

        /** VFD1.00 */
        @Element(sequence = 1)
        public byte[] identifier = new byte[8];
        @Element(sequence = 2)
        public byte[] label = new byte[0xd4];
        @Element(sequence = 3)
        public VfdTrackHeader[] tracks = new VfdTrackHeader[160];

        public VfdHeader() {
            for (int i = 0; i < 160; i++) {
                tracks[i] = new VfdTrackHeader();
            }
        }
    }

    //
    //
    //

    @Override
    public boolean isSupported(String type) {
        return "v98fdd".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
    }

    /** Create sector data */
    private int parseSector(InputStream iStream, int numOfSectors, Object userData, DiskImageTrack track) throws IOException {
        VfdSectorHeader sectorHeader = (VfdSectorHeader) userData;

        if ((sectorHeader.c & 0xff) == 0xff || (sectorHeader.h & 0xff) == 0xff || (sectorHeader.r & 0xff) == 0xff) {
            // No sector
            return 0;
        }

        int trackNumber = sectorHeader.c & 0xff;
        int sideNumber = sectorHeader.h & 0xff;
        int sectorNumber = sectorHeader.r & 0xff;
        int sectorSizeCode = sectorHeader.n & 0xff;

        if (sectorSizeCode > 7) {
            // Sector size is too large
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, trackNumber, sideNumber, sectorNumber, sectorSizeCode, 128 << sectorSizeCode);
            return 0;
        }

        int sector_size = 128 << sectorSizeCode;

        // Sector creation
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sector_size, numOfSectors, (sectorHeader.dden & 0xFF) == 0, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int size = sector.getSectorBufferSize();

        if (sectorHeader.start != -1) {
            // Get actual data
            ((SeekableDataInputStream) iStream).position(sectorHeader.start);

            int result = iStream.readNBytes(buf, 0, size);
            if (result < size) {
                // Not enough file data
                this.result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            }
        } else {
            // Suppress with specific data since there is no data
            byte data = sectorHeader.data;
            Arrays.fill(buf, data);
        }
        sector.clearModify();

        // Return size of this sector data
        return sector.getSize();
    }

    /** Create track data */
    private int parseTrack(InputStream iStream, Object userData, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        VfdTrackHeader trackHeader = (VfdTrackHeader) userData;

        // Check major numbers
        int numOfSectors = 0;
        Map<Integer, Integer> trackNumberMap = new HashMap<>();
        Map<Integer, Integer> sideNumberMap = new HashMap<>();
        for (int sec = 0; sec < 26; sec++) {
            if ((trackHeader.sectors[sec].c & 0xff) == 0xff || (trackHeader.sectors[sec].h & 0xff) == 0xff || (trackHeader.sectors[sec].r & 0xff) == 0xff) {
                continue;
            }
            numOfSectors++;
            IntHashMapUtil.increaseValue(trackNumberMap, trackHeader.sectors[sec].c & 0xff);
            IntHashMapUtil.increaseValue(sideNumberMap, trackHeader.sectors[sec].h & 0xff);
        }
        if (numOfSectors == 0) {
            // No sectors
            return 0;
        }

        int trackNumber = IntHashMapUtil.getMaxKeyOnMaxValue(trackNumberMap);
        int sideNumber = IntHashMapUtil.getMaxKeyOnMaxValue(sideNumberMap);

        // Track creation
        DiskImageTrack track = disk.newImageTrack(trackNumber, sideNumber, offsetPos, 1);
        disk.setMaxTrackNumber(trackNumber);

        int d88TrackSize = 0;
        for (int sec = 0; sec < 26 && result.getValid() >= 0; sec++) {
            d88TrackSize += parseSector(iStream, numOfSectors, trackHeader.sectors[sec], track);
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
    private int parseDisk(InputStream iStream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        int len = iStream.available();
        if (len < VfdHeader.SIZE) {
            this.result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return 0;
        }
        VfdHeader header = new VfdHeader();
        Serdes.Util.deserialize(iStream, header);

        disk.setName(header.label, header.label.length);

        // Create d88 tracks
        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < 160; pos++) {
            d88Offset += parseTrack(iStream, header.tracks[pos], d88OffsetPos, d88Offset, disk);
            d88OffsetPos++;
            if (d88OffsetPos >= disk.getCreatableTracks()) {
                this.result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88Offset);
            }
        }
        disk.setSize(d88Offset);

        if (this.result.getValid() >= 0) {
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
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    /**
     * Check
     *
     * @param iStream Data to be analyzed
     * @return 0: Normal, -1: Error
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        int len = iStream.available();
        if (len < VfdHeader.SIZE) {
            // too short
            return -1;
        }
        VfdHeader header = new VfdHeader();
        Serdes.Util.deserialize(iStream, header);

        ((SeekableDataInputStream) iStream).position(0);

        // check identifier
        if (header.identifier[0] != 'V' || header.identifier[1] != 'F' || header.identifier[2] != 'D' || header.identifier[3] != '1') {
            return -1;
        }

        return 0;
    }

    /**
     * Analyze VFD file
     *
     * @param iStream    Data to be analyzed
     * @param diskParam Parameters usually unnecessary
     * @return 0: normal, -1: error, 1: warning
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        parseDisk(iStream);
        return result.getValid();
    }
}
