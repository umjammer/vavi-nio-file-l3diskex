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

        public DiskD88ParseOffset(int n_num, int n_offset, int n_size) {
            num = n_num;
            offset = n_offset;
            size = n_size;
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

        public static Comparator<DiskD88ParseOffset> CmpByNum = (item1, item2) -> (item1.num < item2.num ? -1 : (item1.num > item2.num ? 1 : 0));

        public static Comparator<DiskD88ParseOffset> CmpByOffset = (item1, item2) -> (item1.offset < item2.offset ? -1 : (item1.offset > item2.offset ? 1 : 0));
    }

    public DiskD88Parser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    /**
     * Get parameters before sector data analysis
     */
    private void preParseSectors(InputStream istream, int disk_number, int[] track_number, int[] side_number, int[] sector_nums, int[] sector_size) throws IOException {
        Map<Integer, Integer> track_number_map = new HashMap<>();
        Map<Integer, Integer> side_number_map = new HashMap<>();
        Map<Integer, Integer> sector_nums_map = new HashMap<>();
        Map<Integer, Integer> sector_size_map = new HashMap<>();

        int ipos = (int) ((SeekableDataInputStream) istream).position();

        if (sector_nums[0] == 0) {
            sector_nums[0] = 4;
        }

        for (int num = 0; num < sector_nums[0]; num++) {
            D88SectorHeader sector_header = new D88SectorHeader();
            Serdes.Util.deserialize(istream, sector_header);

            IntHashMapUtil.increaseValue(track_number_map, sector_header.id.c & 0xff);
            IntHashMapUtil.increaseValue(side_number_map, sector_header.id.h & 0xff);
            IntHashMapUtil.increaseValue(sector_nums_map, sector_header.secnums & 0xff);

            int secnums_val = sector_header.secnums & 0xffff;
            if (2 < secnums_val && secnums_val < sector_nums[0]) {
                sector_nums[0] = secnums_val;
            }

            int real_size = sector_header.size & 0xffff;
            IntHashMapUtil.increaseValue(sector_size_map, real_size);

            int pos = (int) ((SeekableDataInputStream) istream).position();
            ((SeekableDataInputStream) istream).position(pos + real_size); // wxFromCurrent
        }

        ((SeekableDataInputStream) istream).position(ipos);

        track_number[0] = IntHashMapUtil.getMaxKeyOnMaxValue(track_number_map);
        side_number[0] = IntHashMapUtil.getMaxKeyOnMaxValue(side_number_map);
        sector_nums[0] = IntHashMapUtil.getMaxKeyOnMaxValue(sector_nums_map);
        sector_size[0] = IntHashMapUtil.getMaxKeyOnMaxValue(sector_size_map);
    }

    /**
     * Sector data analysis
     */
    private int parseSector(InputStream istream, int disk_number, int track_number, int side_number, int sector_nums, int sector_size, DiskImageTrack track) throws IOException {
        DiskD88SectorHeader sector_header = new DiskD88SectorHeader();
        sector_header.alloc();
        int header_size = sector_header.getHeaderSize();
        Serdes.Util.deserialize(istream, sector_header.getHeader());

        // track number is same ?
        if (sector_header.getIDC() != track_number) {
            result.setWarn(DiskResult.ERRV_ID_TRACK, disk_number, track_number, sector_header.getIDC(), sector_header.getIDH(), sector_header.getIDR());
        }
        // side number is valid ?
        if (sector_header.getIDH() != side_number) {
            result.setWarn(DiskResult.ERRV_ID_SIDE, disk_number, side_number, track_number, sector_header.getIDC(), sector_header.getIDH(), sector_header.getIDR());
        }
        int sector_number = sector_header.getIDR();
        // sector number is valid ?
        if (sector_number <= 0) {
            result.setWarn(DiskResult.ERRV_ID_SECTOR, disk_number, track_number, sector_header.getIDC(), sector_header.getIDH(), sector_header.getIDR(), sector_nums);
        }
        // invalid sector size
        if (sector_header.getSize() > 2048) {
            result.setWarn(DiskResult.ERRV_SECTOR_SIZE_SECTOR, disk_number, sector_header.getIDC(), sector_header.getIDH(), sector_header.getIDR(), sector_header.getIDN(), sector_header.getSize());
        } else if (sector_header.getSize() == 0) {
            result.setWarn(DiskResult.ERRV_SECTOR_SIZE_SECTOR, disk_number, sector_header.getIDC(), sector_header.getIDH(), sector_header.getIDR(), sector_header.getIDN(), sector_header.getSize());
        }

        // Add
        int data_size = sector_header.getSize();
        if (result.getValid() >= 0) {
            byte[] sector_data = null;
            if (data_size > 0) {
                sector_data = new byte[data_size];
                istream.read(sector_data, 0, data_size);
            }
            DiskImageSector sector = track.newImageSector(sector_number, sector_header, sector_data);
            track.add(sector);

        } else {
            data_size = 0;
        }

        // return the size of this sector data
        return header_size + data_size;
    }

    /**
     * Track data analysis
     */
    private int parseTrack(InputStream istream, long start_pos, int offset_pos, int offset, int disk_number, int track_size, DiskImageDisk disk) throws IOException {
        int[] track_number = {0};
        int[] side_number = {0};
        int[] sector_nums = {0};
        int[] sector_size = {0};

        ((SeekableDataInputStream) istream).position(start_pos + offset); // wxFromStart

        preParseSectors(istream, disk_number, track_number, side_number, sector_nums, sector_size);

        // too many sectors
        if (sector_nums[0] > 255) {
            result.setWarn(DiskResult.ERRV_TOO_MANY_SECTORS, disk_number, 255, track_number, side_number, sector_nums);
            sector_nums[0] = 255;
        }
        // no sectors
        if (sector_nums[0] == 0) {
            track_number[0] = -1;
            side_number[0] = -1;
        }

        DiskImageTrack track = disk.newImageTrack(track_number[0], side_number[0], offset_pos, 1);
        disk.setMaxTrackNumber(track_number[0]);

        // sectors
        int sector_total_size = 0;
        for (int sec_pos = 0; sec_pos < sector_nums[0] && result.getValid() >= 0; sec_pos++) {
            sector_total_size += parseSector(istream, disk_number, track_number[0], side_number[0], sector_nums[0], sector_size[0], track);
        }

        // sector number is valid ?
        List<DiskImageSector> sectors = track.getSectors();
        if (sectors != null && sector_nums[0] != sectors.size()) {
            result.setWarn(DiskResult.ERRV_ID_NUM_OF_SECTOR, disk_number, track_number, side_number);
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
                            result.setWarn(DiskResult.ERRV_DUPLICATE_SECTOR, disk_number, curr, track_number, side_number);
                        } else if (prev + 1 != curr) {
                            // non sequential
                            result.setWarn(DiskResult.ERRV_NO_SECTOR, disk_number, prev + 1, track_number, side_number);
                        }
                    }
                    prev = curr;
                }
            }
        }

        if (result.getValid() >= 0 && track_number[0] >= 0) {
            // track duplication check
            List<DiskImageTrack> tracks = disk.getTracks();
            if (tracks != null) {
                boolean dup;
                do {
                    dup = false;
                    for (DiskImageTrack t : tracks) {
                        if (t.getTrackNumber() == track_number[0] && t.getSideNumber() == side_number[0]) {
                            // same track number and side number already exists
                            if (sector_size[0] >= 256) {
                                // issue warning if sector size is 256 bytes or more.
                                result.setWarn(DiskResult.ERRV_DUPLICATE_TRACK, disk_number, track_number, side_number, side_number[0] + 1);
                            }
                            // change side number
                            side_number[0]++;
                            track.setSideNumber(side_number[0]);
                            dup = true;
                            break;    // re-check
                        }
                    }
                } while (dup);
            }
        }

        if (result.getValid() >= 0) {
            // remaining data
            if (sector_total_size < track_size) {
                int size = track_size - sector_total_size;
                byte[] buf = new byte[size];
                istream.readNBytes(buf, 0, size);
                track.setExtraData(buf, size);
            }

            // set track size
            track.setSize(track_size);
            // set interleave
            if (disk.getInterleave() < track.getInterleave()) {
                disk.setInterleave(track.getInterleave());
            }
            // add to disk
            disk.add(track);
        }

        return track_size;
    }

    /**
     * Disk data analysis
     *
     * @return disk size
     */
    private int parseDisk(InputStream istream, long start_pos, int disk_number) throws IOException {
        DiskD88DiskHeader disk_header = new DiskD88DiskHeader();
        disk_header.alloc();

        int size;

        do {
            // seek
            ((SeekableDataInputStream) istream).position(start_pos);

            int header_size = disk_header.getHeaderSize();
            size = header_size;

            // skip if EOF(0x1a)
            byte[] p = istream.readNBytes(D88Header.SIZE);
            boolean all_eot = true;
            for (int pos = 0; pos < header_size; pos++) {
                if (pos < p.length && p[pos] != 0x1a) { // Simplified check on the initial buffer part
                    all_eot = false;
                    break;
                }
            }
            if (all_eot) {
                break;
            }

            Serdes.Util.deserialize(new ByteArrayInputStream(p), disk_header.getHeader());

            // disk size is too small
            if (header_size < disk_header.getHeaderSize()) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_SMALL, disk_number);
                break;
            }

            int disk_size = disk_header.getDiskSize();
            // disk size is too small
            if (disk_size < disk_header.getHeaderSize()) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_SMALL, disk_number);
                break;
            }

            int stream_size = istream.available() + header_size; // TODO depends available
logger.log(Level.TRACE, "stream_size: %d, disk_size: %d".formatted(stream_size, disk_size));
            // disk size is larger than file size or exceeds 4MB
            if (stream_size < disk_size || (1024 * 1024 * 4) < disk_size) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_LARGE, disk_number);
                disk_size = stream_size;
            }

            size = disk_size;

            // 17th character of the name should be '\0'
            if (disk_header.getHeader().diskname[16] != '\0') {
                result.setWarn(DiskResult.ERRV_DISK_HEADER, disk_number);
            }

            // find the minimum value of the offset part
            // -> old d88 has a small offset part
            int offset_start = -1; // C++ wxUint32(-1) is 0xFFFFFFFF
            for (int pos = 0; pos < (DISKD88_MAX_TRACKS - 16); pos++) {
                int offset = disk_header.getOffset(pos);
                if (offset_start == -1 || (offset < offset_start && offset > 0)) {
                    offset_start = offset;
                }
            }
            // set initial value if no offset (no tracks)
            if (offset_start == -1) {
                offset_start = D88Header.SIZE;
            }

            // set the overflowing part to 0
            int max_tracks = DISKD88_MAX_TRACKS - (disk_header.getHeaderSize() - offset_start) / 4;
            if (max_tracks < 0 || max_tracks > DISKD88_MAX_TRACKS) {
                // number of tracks is strange
                result.setWarn(DiskResult.ERRV_INVALID_DISK, disk_number);
                break;
            }

            for (int pos = max_tracks; pos < DISKD88_MAX_TRACKS; pos++) {
                disk_header.setOffset(pos, 0);
            }

            // calculate the size of each track from the offset
            List<DiskD88ParseOffset> offsets = new ArrayList<>();
            for (int pos = 0; pos < max_tracks; pos++) {
                int offset = disk_header.getOffset(pos);
                if (offset >= offset_start) {
                    offsets.add(new DiskD88ParseOffset(pos, offset, 0));
                }
            }
            offsets.sort(DiskD88ParseOffset.CmpByOffset);
            int offsets_count = offsets.size();
            for (int pos = 0; pos < offsets_count - 1; pos++) {
                DiskD88ParseOffset curr = offsets.get(pos);
                DiskD88ParseOffset next = offsets.get(pos + 1);
                curr.setSize(next.getOffset() - curr.getOffset());
            }
            if (offsets_count >= 1) {
                DiskD88ParseOffset curr = offsets.get(offsets_count - 1);
                curr.setSize(disk_size - curr.getOffset());
            }
            offsets.sort(DiskD88ParseOffset.CmpByNum);

            //
            // create disk
            //

            DiskImageDisk disk = file.newImageDisk(disk_number, disk_header);

            disk.setOffsetStart(offset_start);

            // parse tracks
            for (int pos = 0; pos < offsets_count && result.getValid() >= 0; pos++) {
                DiskD88ParseOffset curr = offsets.get(pos);

                // does the offset exceed the disk size?
                if (curr.getOffset() >= disk_size) {
                    result.setWarn(DiskResult.ERRV_OVERFLOW_OFFSET, disk_number, curr.getNum(), curr.getOffset(), disk_size);
                    disk.setOffset(curr.getNum(), 0);
                    continue;
                }

                parseTrack(istream, start_pos, curr.getNum(), curr.getOffset(), disk_number, curr.getSize(), disk);
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
                                result.setWarn(DiskResult.ERRV_SHORT_SECTORS, disk_number, disk.getSectorsPerTrack(), track.getTrackNumber(), track.getSideNumber(), sectors.size());
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
     * @param istream    data to be parsed
     * @param disk_param parameters, usually not needed
     * @return 0: normal, -1: error, 1: warning
     */
    @Override
    public int parse(InputStream istream, DiskParam disk_param) throws IOException {
        long read_size = 0;
        long stream_size = istream.available();
        int disk_number = file.count();
        // disk size is 0
        if (stream_size == 0) {
            result.setError(DiskResult.ERRV_DISK_SIZE_ZERO, disk_number);
            return result.getValid();
        }
        // チェック
        if (check(istream) != 0) {
            result.setError(DiskResult.ERRV_INVALID_DISK, disk_number);
            return result.getValid();
        }
        for (; read_size < stream_size && result.getValid() >= 0; disk_number++) {
            int size = parseDisk(istream, read_size, disk_number);
            if (size == 0) break;
            read_size += size;
        }
        return result.getValid();
    }

    @Override
    public int check(InputStream istream, List<DiskTypeHint> disk_hints, DiskParam disk_param, List<DiskParam> disk_params, DiskParam manual_param) {
        return -1;
    }

    /**
     * Checks.
     *
     * @return 0
     */
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        D88Header header = new D88Header();
        int header_size_min = D88Header.SIZE - 32;
        int header_size_max = D88Header.SIZE + 16;

        Serdes.Util.deserialize(istream, header);
logger.log(Level.TRACE, header);

        // check offset
        int valid = -1;
        int all_zero = 0;
        for (int i = 0; i < DISKD88_MAX_TRACKS; i++) {
            int offset = header.offsets[i];
logger.log(Level.TRACE, "[%d]: %d, %x".formatted(i, offset, offset));
            if (offset >= header_size_min && offset <= header_size_max && (offset & 0xf) == 0) {
                valid = 0;
                break;
            } else if (offset == 0) {
                all_zero++;
            }
        }
        if (all_zero == DISKD88_MAX_TRACKS) {
            // when all 0
            valid = 0;
        }
        if (valid < 0) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }
        return valid;
    }
}
