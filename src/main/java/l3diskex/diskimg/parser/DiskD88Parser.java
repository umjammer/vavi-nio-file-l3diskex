/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l3diskex.diskimg.DiskD88.D88Header;
import l3diskex.diskimg.DiskD88.D88SectorHeader;
import l3diskex.diskimg.DiskD88.DiskD88DiskHeader;
import l3diskex.diskimg.DiskD88.DiskD88SectorHeader;
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
import vavi.util.serdes.Serdes;

import static l3diskex.diskimg.DiskD88.DISKD88_MAX_TRACKS;


/**
 * D88 Disk Parser
 */
public class DiskD88Parser extends DiskImageParser {

    private static final Logger logger = System.getLogger(DiskD88Parser.class.getName());

    /**
     * Offsets for D88 Parsing
     */
    static class DiskD88ParseOffset {

        private final int num;
        private final int offset;
        private int size;

        public DiskD88ParseOffset() {
            num = 0;
            offset = 0;
            size = 0;
        }

        public DiskD88ParseOffset(DiskD88ParseOffset src) {
            num = src.num;
            offset = src.offset;
            size = src.size;
        }

        public DiskD88ParseOffset(int num, int offset, int size) {
            this.num = num;
            this.offset = offset;
            this.size = size;
        }

        public int getNum() {
            return num;
        }

        public int getOffset() {
            return offset;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int val) {
            size = val;
        }

        public static Comparator<DiskD88ParseOffset> CmpByNum = Comparator.comparingInt(item -> item.num);

        public static Comparator<DiskD88ParseOffset> CmpByOffset = Comparator.comparingInt(item -> item.offset);
    }

    public DiskD88Parser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    /**
     * Get parameters before sector data analysis
     */
    private void preParseSectors(InputStream iStream, int diskNumber, int[] trackNumber, int[] sideNumber, int[] sectorNums, int[] sectorSize) throws IOException {
        Map<Integer, Integer> trackNumberMap = new HashMap<>();
        Map<Integer, Integer> sideNumberMap = new HashMap<>();
        Map<Integer, Integer> sectorNumsMap = new HashMap<>();
        Map<Integer, Integer> sectorSizeMap = new HashMap<>();

        int ipos = (int) ((SeekableDataInputStream) iStream).position();

        if (sectorNums[0] == 0) {
            sectorNums[0] = 4;
        }

        for (int num = 0; num < sectorNums[0]; num++) {
            D88SectorHeader sectorHeader = new D88SectorHeader();
            Serdes.Util.deserialize(iStream, sectorHeader);

            IntHashMapUtil.increaseValue(trackNumberMap, sectorHeader.id.c & 0xff);
            IntHashMapUtil.increaseValue(sideNumberMap, sectorHeader.id.h & 0xff);
            IntHashMapUtil.increaseValue(sectorNumsMap, sectorHeader.numOfSectors & 0xff);

            int secnums_val = sectorHeader.numOfSectors & 0xffff;
            if (2 < secnums_val && secnums_val < sectorNums[0]) {
                sectorNums[0] = secnums_val;
            }

            int realSize = sectorHeader.size & 0xffff;
            IntHashMapUtil.increaseValue(sectorSizeMap, realSize);

            int pos = (int) ((SeekableDataInputStream) iStream).position();
            ((SeekableDataInputStream) iStream).position(pos + realSize); // wxFromCurrent
        }

        ((SeekableDataInputStream) iStream).position(ipos);

        trackNumber[0] = IntHashMapUtil.getMaxKeyOnMaxValue(trackNumberMap);
        sideNumber[0] = IntHashMapUtil.getMaxKeyOnMaxValue(sideNumberMap);
        sectorNums[0] = IntHashMapUtil.getMaxKeyOnMaxValue(sectorNumsMap);
        sectorSize[0] = IntHashMapUtil.getMaxKeyOnMaxValue(sectorSizeMap);
    }

    /**
     * Sector data analysis
     */
    private int parseSector(InputStream iStream, int diskNumber, int trackNumber, int sideNumber, int sectorNums, int sectorSize, DiskImageTrack track) throws IOException {
        DiskD88SectorHeader sectorHeader = new DiskD88SectorHeader();
        sectorHeader.alloc();
        int headerSize = sectorHeader.getHeaderSize();
        Serdes.Util.deserialize(iStream, sectorHeader.getHeader());

        // track number is same ?
        if (sectorHeader.getIDC() != trackNumber) {
            result.setWarn(DiskResult.ERRV_ID_TRACK, diskNumber, trackNumber, sectorHeader.getIDC(), sectorHeader.getIDH(), sectorHeader.getIDR());
        }
        // side number is valid ?
        if (sectorHeader.getIDH() != sideNumber) {
            result.setWarn(DiskResult.ERRV_ID_SIDE, diskNumber, sideNumber, trackNumber, sectorHeader.getIDC(), sectorHeader.getIDH(), sectorHeader.getIDR());
        }
        int sectorNumber = sectorHeader.getIDR();
        // sector number is valid ?
        if (sectorNumber <= 0) {
            result.setWarn(DiskResult.ERRV_ID_SECTOR, diskNumber, trackNumber, sectorHeader.getIDC(), sectorHeader.getIDH(), sectorHeader.getIDR(), sectorNums);
        }
        // invalid sector size
        if (sectorHeader.getSize() > 2048) {
            result.setWarn(DiskResult.ERRV_SECTOR_SIZE_SECTOR, diskNumber, sectorHeader.getIDC(), sectorHeader.getIDH(), sectorHeader.getIDR(), sectorHeader.getIDN(), sectorHeader.getSize());
        } else if (sectorHeader.getSize() == 0) {
            result.setWarn(DiskResult.ERRV_SECTOR_SIZE_SECTOR, diskNumber, sectorHeader.getIDC(), sectorHeader.getIDH(), sectorHeader.getIDR(), sectorHeader.getIDN(), sectorHeader.getSize());
        }

        // Add
        int dataSize = sectorHeader.getSize();
        if (result.getValid() >= 0) {
            byte[] sector_data = null;
            if (dataSize > 0) {
                sector_data = new byte[dataSize];
                iStream.read(sector_data, 0, dataSize);
            }
            DiskImageSector sector = track.newImageSector(sectorNumber, sectorHeader, sector_data);
            track.add(sector);

        } else {
            dataSize = 0;
        }

        // return the size of this sector data
        return headerSize + dataSize;
    }

    /**
     * Track data analysis
     */
    private int parseTrack(InputStream iStream, long startPos, int offsetPos, int offset, int diskNumber, int trackSize, DiskImageDisk disk) throws IOException {
        int[] trackNumber = {0};
        int[] sideNumber = {0};
        int[] sectorNums = {0};
        int[] sectorSize = {0};

        ((SeekableDataInputStream) iStream).position(startPos + offset); // wxFromStart

        preParseSectors(iStream, diskNumber, trackNumber, sideNumber, sectorNums, sectorSize);

        // too many sectors
        if (sectorNums[0] > 255) {
            result.setWarn(DiskResult.ERRV_TOO_MANY_SECTORS, diskNumber, 255, trackNumber, sideNumber, sectorNums);
            sectorNums[0] = 255;
        }
        // no sectors
        if (sectorNums[0] == 0) {
            trackNumber[0] = -1;
            sideNumber[0] = -1;
        }

        DiskImageTrack track = disk.newImageTrack(trackNumber[0], sideNumber[0], offsetPos, 1);
        disk.setMaxTrackNumber(trackNumber[0]);

        // sectors
        int sectorTotalSize = 0;
        for (int secPos = 0; secPos < sectorNums[0] && result.getValid() >= 0; secPos++) {
            sectorTotalSize += parseSector(iStream, diskNumber, trackNumber[0], sideNumber[0], sectorNums[0], sectorSize[0], track);
        }

        // sector number is valid ?
        List<DiskImageSector> sectors = track.getSectors();
        if (sectors != null && sectorNums[0] != sectors.size()) {
            result.setWarn(DiskResult.ERRV_ID_NUM_OF_SECTOR, diskNumber, trackNumber, sideNumber);
        }

        if (result.getValid() >= 0) {
            // calculate interleave
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // check for sector duplication and existence
            if (sectors != null) {
                List<Integer> arr = new ArrayList<>();
                for (DiskImageSector s : sectors) {
                    arr.add(s.getSectorNumber());
                }
                arr.sort(Integer::compareTo);
                int prev = -1;
                for (int curr : arr) {
                    if (prev >= 0) {
                        if (curr == prev) {
                            // duplicate
                            result.setWarn(DiskResult.ERRV_DUPLICATE_SECTOR, diskNumber, curr, trackNumber, sideNumber);
                        } else if (prev + 1 != curr) {
                            // non sequential
                            result.setWarn(DiskResult.ERRV_NO_SECTOR, diskNumber, prev + 1, trackNumber, sideNumber);
                        }
                    }
                    prev = curr;
                }
            }
        }

        if (result.getValid() >= 0 && trackNumber[0] >= 0) {
            // track duplication check
            List<DiskImageTrack> tracks = disk.getTracks();
            if (tracks != null) {
                boolean dup;
                do {
                    dup = false;
                    for (DiskImageTrack t : tracks) {
                        if (t.getTrackNumber() == trackNumber[0] && t.getSideNumber() == sideNumber[0]) {
                            // same track number and side number already exists
                            if (sectorSize[0] >= 256) {
                                // issue warning if sector size is 256 bytes or more.
                                result.setWarn(DiskResult.ERRV_DUPLICATE_TRACK, diskNumber, trackNumber, sideNumber, sideNumber[0] + 1);
                            }
                            // change side number
                            sideNumber[0]++;
                            track.setSideNumber(sideNumber[0]);
                            dup = true;
                            break;    // re-check
                        }
                    }
                } while (dup);
            }
        }

        if (result.getValid() >= 0) {
            // remaining data
            if (sectorTotalSize < trackSize) {
                int size = trackSize - sectorTotalSize;
                byte[] buf = new byte[size];
                iStream.readNBytes(buf, 0, size);
                track.setExtraData(buf, size);
            }

            // set track size
            track.setSize(trackSize);
            // set interleave
            if (disk.getInterleave() < track.getInterleave()) {
                disk.setInterleave(track.getInterleave());
            }
            // add to disk
            disk.add(track);
        }

        return trackSize;
    }

    /**
     * Disk data analysis
     *
     * @return disk size
     */
    private int parseDisk(InputStream iStream, long startPos, int diskNumber) throws IOException {
        DiskD88DiskHeader disk_header = new DiskD88DiskHeader();
        disk_header.alloc();

        int size;

        do {
            // seek
            ((SeekableDataInputStream) iStream).position(startPos);

            int headerSize = disk_header.getHeaderSize();
            size = headerSize;

            // skip if EOF(0x1a)
            byte[] p = iStream.readNBytes(D88Header.SIZE);
            boolean allEot = true;
            for (int pos = 0; pos < headerSize; pos++) {
                if (pos < p.length && p[pos] != 0x1a) { // Simplified check on the initial buffer part
                    allEot = false;
                    break;
                }
            }
            if (allEot) {
                break;
            }

            Serdes.Util.deserialize(new ByteArrayInputStream(p), disk_header.getHeader());

            // disk size is too small
            if (headerSize < disk_header.getHeaderSize()) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
                break;
            }

            int diskSize = disk_header.getDiskSize();
            // disk size is too small
            if (diskSize < disk_header.getHeaderSize()) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
                break;
            }

            int streamSize = iStream.available() + headerSize; // TODO depends available
logger.log(Level.TRACE, "streamSize: %d, diskSize: %d".formatted(streamSize, diskSize));
            // disk size is larger than file size or exceeds 4MB
            if (streamSize < diskSize || (1024 * 1024 * 4) < diskSize) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_LARGE, diskNumber);
                diskSize = streamSize;
            }

            size = diskSize;

            // 17th character of the name should be '\0'
            if (disk_header.getHeader().diskName[16] != '\0') {
                result.setWarn(DiskResult.ERRV_DISK_HEADER, diskNumber);
            }

            // find the minimum value of the offset part
            // -> old d88 has a small offset part
            int offsetStart = -1;
            for (int pos = 0; pos < (DISKD88_MAX_TRACKS - 16); pos++) {
                int offset = disk_header.getOffset(pos);
                if (offsetStart == -1 || (offset < offsetStart && offset > 0)) {
                    offsetStart = offset;
                }
            }
            // set initial value if no offset (no tracks)
            if (offsetStart == -1) {
                offsetStart = D88Header.SIZE;
            }

            // set the overflowing part to 0
            int maxTracks = DISKD88_MAX_TRACKS - (disk_header.getHeaderSize() - offsetStart) / 4;
            if (maxTracks < 0 || maxTracks > DISKD88_MAX_TRACKS) {
                // number of tracks is strange
                result.setWarn(DiskResult.ERRV_INVALID_DISK, diskNumber);
                break;
            }

            for (int pos = maxTracks; pos < DISKD88_MAX_TRACKS; pos++) {
                disk_header.setOffset(pos, 0);
            }

            // calculate the size of each track from the offset
            List<DiskD88ParseOffset> offsets = new ArrayList<>();
            for (int pos = 0; pos < maxTracks; pos++) {
                int offset = disk_header.getOffset(pos);
                if (offset >= offsetStart) {
                    offsets.add(new DiskD88ParseOffset(pos, offset, 0));
                }
            }
            offsets.sort(DiskD88ParseOffset.CmpByOffset);
            int offsetsCount = offsets.size();
            for (int pos = 0; pos < offsetsCount - 1; pos++) {
                DiskD88ParseOffset curr = offsets.get(pos);
                DiskD88ParseOffset next = offsets.get(pos + 1);
                curr.setSize(next.getOffset() - curr.getOffset());
            }
            if (offsetsCount >= 1) {
                DiskD88ParseOffset curr = offsets.get(offsetsCount - 1);
                curr.setSize(diskSize - curr.getOffset());
            }
            offsets.sort(DiskD88ParseOffset.CmpByNum);

            //
            // create disk
            //

            DiskImageDisk disk = file.newImageDisk(diskNumber, disk_header);

            disk.setOffsetStart(offsetStart);

            // parse tracks
            for (int pos = 0; pos < offsetsCount && result.getValid() >= 0; pos++) {
                DiskD88ParseOffset curr = offsets.get(pos);

                // does the offset exceed the disk size?
                if (curr.getOffset() >= diskSize) {
                    result.setWarn(DiskResult.ERRV_OVERFLOW_OFFSET, diskNumber, curr.getNum(), curr.getOffset(), diskSize);
                    disk.setOffset(curr.getNum(), 0);
                    continue;
                }

                parseTrack(iStream, startPos, curr.getNum(), curr.getOffset(), diskNumber, curr.getSize(), disk);
            }

            if (result.getValid() >= 0) {
                // add disk
                disk.calcMajorNumber();
                file.add(disk, modFlags);
                // check number of sectors
                List<DiskImageTrack> tracks = disk.getTracks();
                if (tracks != null) {
                    for (DiskImageTrack track : tracks) {
                        List<DiskImageSector> sectors = track.getSectors();
                        if (sectors != null) {
                            if (sectors.size() < disk.getSectorsPerTrack()) {
                                result.setWarn(DiskResult.ERRV_SHORT_SECTORS, diskNumber, disk.getSectorsPerTrack(), track.getTrackNumber(), track.getSideNumber(), sectors.size());
                            }
                        }
                    }
                }
            }
        } while (false);

        return size;
    }

    /**
     * Parse D88 file
     *
     * @param iStream   data to be parsed
     * @param diskParam parameters, usually not needed
     * @return 0: normal, -1: error, 1: warning
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        long readSize = 0;
        long streamSize = iStream.available();
        int diskNumber = file.count();
        // disk size is 0
        if (streamSize == 0) {
            result.setError(DiskResult.ERRV_DISK_SIZE_ZERO, diskNumber);
            return result.getValid();
        }
        // チェック
        if (check(iStream) != 0) {
            result.setError(DiskResult.ERRV_INVALID_DISK, diskNumber);
            return result.getValid();
        }
        for (; readSize < streamSize && result.getValid() >= 0; diskNumber++) {
            int size = parseDisk(iStream, readSize, diskNumber);
            if (size == 0) break;
            readSize += size;
        }
        return result.getValid();
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) {
        return -1;
    }

    /**
     * Checks.
     *
     * @return 0
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        D88Header header = new D88Header();
        int headerSizeMin = D88Header.SIZE - 32;
        int headerSizeMax = D88Header.SIZE + 16;

        Serdes.Util.deserialize(iStream, header);
logger.log(Level.TRACE, header);

        // check offset
        int valid = -1;
        int allZero = 0;
        for (int i = 0; i < DISKD88_MAX_TRACKS; i++) {
            int offset = header.offsets[i];
//logger.log(Level.TRACE, "[%d]: %d, %x".formatted(i, offset, offset));
            if (offset >= headerSizeMin && offset <= headerSizeMax && (offset & 0xf) == 0) {
                valid = 0;
                break;
            } else if (offset == 0) {
                allZero++;
            }
        }
        if (allZero == DISKD88_MAX_TRACKS) {
            // when all 0
            valid = 0;
        }
        if (valid < 0) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }
        return valid;
    }
}
