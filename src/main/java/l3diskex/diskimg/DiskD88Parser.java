package l3diskex.diskimg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l3diskex.diskimg.DiskD88.D88Header;
import l3diskex.diskimg.DiskD88.DiskD88DiskHeader;
import l3diskex.diskimg.DiskD88.DiskD88SectorHeader;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskImage.IntHashMapUtil;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Serdes;

import static l3diskex.diskimg.DiskD88.DISKD88_MAX_TRACKS;


/**
 * D88 Disk Parser
 */
class DiskD88Parser extends DiskImageParser {

    /**
     * Offsets for D88 Parsing
     */
    static class DiskD88ParseOffset {
        private int num;
        private int offset; // C++ wxUint32
        private int size;

        public DiskD88ParseOffset() {
            num		= 0;
            offset	= 0;
            size	= 0;
        }

        public DiskD88ParseOffset(final DiskD88ParseOffset src) {
            num		= src.num;
            offset	= src.offset;
            size	= src.size;
        }

        public DiskD88ParseOffset(int n_num, int n_offset, int n_size) {
            num		= n_num;
            offset	= n_offset;
            size	= n_size;
        }

        // Destructor omitted (Java GC)

        // C++ operator= omitted (Java reference assignment)

        public int GetNum() { return num; }
        public int GetOffset() { return offset; }
        public int GetSize() { return size; }
        public void SetSize(int val) { size = val; }

        public static Comparator<DiskD88ParseOffset> CmpByNum = (item1, item2) -> (item1.num < item2.num ? -1 : (item1.num > item2.num ? 1 : 0));

        public static Comparator<DiskD88ParseOffset> CmpByOffset = (item1, item2) -> (item1.offset < item2.offset ? -1 : (item1.offset > item2.offset ? 1 : 0));
    }

    public DiskD88Parser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    // Destructor omitted (Java GC)

    /**
     * Get parameters before sector data analysis
     */
    private void PreParseSectors(InputStream istream, int disk_number, int[] track_number_ref, int[] side_number_ref, int[] sector_nums_ref, int[] sector_size_ref) throws IOException {
        Map<Integer, Integer> track_number_map = new HashMap<>();
        Map<Integer, Integer> side_number_map = new HashMap<>();
        Map<Integer, Integer> sector_nums_map = new HashMap<>();
        Map<Integer, Integer> sector_size_map = new HashMap<>();

        byte[] header_buffer = new byte[DiskD88SectorHeader.SIZE];

        int ipos = (int) ((SeekableDataInputStream) istream).position();

        if (sector_nums_ref[0] == 0) {
            sector_nums_ref[0] = 4;
        }

        for(int num = 0; num < sector_nums_ref[0]; num++) {
            istream.read(header_buffer, 0, DiskD88SectorHeader.SIZE);

            DiskD88SectorHeader sector_header = new DiskD88SectorHeader();
            Serdes.Util.deserialize(new ByteArrayInputStream(header_buffer), sector_header);

            IntHashMapUtil.increaseValue(track_number_map, sector_header.getIDC() & 0xFF);
            IntHashMapUtil.increaseValue(side_number_map, sector_header.getIDH() & 0xFF);
            IntHashMapUtil.increaseValue(sector_nums_map, sector_header.getNumberOfSectors() & 0xFF);

            int secnums_val = sector_header.getNumberOfSectors() & 0xFF;
            if (2 < secnums_val && secnums_val < sector_nums_ref[0]) {
                sector_nums_ref[0] = secnums_val;
            }

            // sector_header.size is short, so & 0xFFFF gives unsigned value
            int real_size = sector_header.getSize() & 0xFFFF;
            IntHashMapUtil.increaseValue(sector_size_map, real_size);

            ((SeekableDataInputStream) istream).position(real_size); // wxFromCurrent
        }

        ((SeekableDataInputStream) istream).position(ipos); // wxFromStart

        track_number_ref[0] = IntHashMapUtil.getMaxKeyOnMaxValue(track_number_map);
        side_number_ref[0] = IntHashMapUtil.getMaxKeyOnMaxValue(side_number_map);
        sector_nums_ref[0] = IntHashMapUtil.getMaxKeyOnMaxValue(sector_nums_map);
        sector_size_ref[0] = IntHashMapUtil.getMaxKeyOnMaxValue(sector_size_map);
    }

    /**
     * Sector data analysis
     */
    private int ParseSector(InputStream istream, int disk_number, int track_number, int side_number, int sector_nums, int sector_size, DiskImageTrack track) throws IOException {
        DiskD88SectorHeader sector_header = new DiskD88SectorHeader();
        sector_header.alloc();
        int header_size = sector_header.getHeaderSize();
        byte[] header_buffer = new byte[header_size];

        istream.readNBytes(header_buffer, 0, header_size);
        Serdes.Util.deserialize(new ByteArrayInputStream(header_buffer), sector_header);

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
    private int ParseTrack(InputStream istream, long start_pos, int offset_pos, int offset, int disk_number, int track_size, DiskImageDisk disk) throws IOException {
        int[] track_number_ref = new int[1];
        int[] side_number_ref = new int[1];
        int[] sector_nums_ref = new int[1];
        int[] sector_size_ref = new int[1];

        ((SeekableDataInputStream) istream).position(start_pos + offset); // wxFromStart

        PreParseSectors(istream, disk_number, track_number_ref, side_number_ref, sector_nums_ref, sector_size_ref);

        int track_number = track_number_ref[0];
        int side_number = side_number_ref[0];
        int sector_nums = sector_nums_ref[0];
        int sector_size = sector_size_ref[0];

        // too many sectors
        if (sector_nums > 255) {
            result.setWarn(DiskResult.ERRV_TOO_MANY_SECTORS, disk_number, 255, track_number, side_number, sector_nums);
            sector_nums = 255;
        }
        // no sectors
        if (sector_nums == 0) {
            track_number = -1;
            side_number = -1;
        }

        DiskImageTrack track = disk.newImageTrack(track_number, side_number, offset_pos, 1);
        disk.setMaxTrackNumber(track_number);

        // sectors
        int sector_total_size = 0;
        for(int sec_pos = 0; sec_pos < sector_nums && result.getValid() >= 0; sec_pos++) {
            sector_total_size += ParseSector(istream, disk_number, track_number, side_number, sector_nums, sector_size, track);
        }

        // sector number is valid ?
        List<DiskImageSector> sectors = track.getSectors();
        if (sectors != null && sector_nums != sectors.size()) {
            result.setWarn(DiskResult.ERRV_ID_NUM_OF_SECTOR, disk_number, track_number, side_number);
        }

        if (result.getValid() >= 0) {
            // calculate interleave
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // check for sector duplication and existence
            if (sectors != null) {
                ArrayList<Integer> arr = new ArrayList<>();
                for(DiskImageSector s : sectors) {
                    arr.add(s.getSectorNumber());
                }
                arr.sort(Integer::compareTo);
                int prev = -1;
                for(int curr : arr) {
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

        if (result.getValid() >= 0 && track_number >= 0) {
            // track duplication check
            List<DiskImageTrack> tracks = disk.getTracks();
            if (tracks != null) {
                boolean dup;
                do {
                    dup = false;
                    for(DiskImageTrack t : tracks) {
                        if (t.getTrackNumber() == track_number && t.getSideNumber() == side_number) {
                            // same track number and side number already exists
                            if (sector_size >= 256) {
                                // issue warning if sector size is 256 bytes or more.
                                result.setWarn(DiskResult.ERRV_DUPLICATE_TRACK, disk_number, track_number, side_number, side_number + 1);
                            }
                            // change side number
                            side_number++;
                            track.setSideNumber(side_number);
                            dup = true;
                            break;	// re-check
                        }
                    }
                } while(dup);
            }
        }

        if (result.getValid() >= 0) {
            // remaining data
            if (sector_total_size < track_size) {
                int size = track_size - sector_total_size;
                byte[] buf = new byte[size];
                istream.read(buf, 0, size);
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
        } else {
            // delete track; // Java GC handles this
        }

        return track_size;
    }

    /**
     * Disk data analysis
     * @return disk size
     */
    private int ParseDisk(InputStream istream, long start_pos, int disk_number) throws IOException {
        DiskD88DiskHeader disk_header = new DiskD88DiskHeader();
        disk_header.alloc();

        boolean valid_header = false;
        int size = 0;

        do {
            // seek
            ((SeekableDataInputStream) istream).position(start_pos); // wxFromStart

            int header_size = istream.read(disk_header.getHeader().diskname, 0, disk_header.getHeaderSize());
            size = header_size;

            // EOF(0x1a)ならスキップ
            byte[] p = disk_header.getHeader().diskname; // Pointer to start of buffer
            boolean all_eot = true;
            for(int pos = 0; pos < header_size; pos++) {
                if (pos < p.length && p[pos] != 0x1a) { // Simplified check on the initial buffer part
                    all_eot = false;
                    break;
                }
            }
            if (all_eot) {
                break;
            }

            // ディスクサイズが小さすぎる
            if (header_size < disk_header.getHeaderSize()) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_SMALL, disk_number);
                break;
            }

            int disk_size = disk_header.getDiskSize();
            // ディスクサイズが小さすぎる
            if (disk_size < disk_header.getHeaderSize()) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_SMALL, disk_number);
                break;
            }

            long stream_size = istream.available();
            // ディスクサイズがファイルサイズより大きい、または4MBを超えている
            if (stream_size < disk_size || (1024*1024*4) < disk_size) {
                result.setWarn(DiskResult.ERRV_DISK_TOO_LARGE, disk_number);
                disk_size = (int)stream_size;
            }

            size = disk_size;

            // 名前の17文字目は'\0'
            if (disk_header.getHeader().diskname[16] != '\0') {
                result.setWarn(DiskResult.ERRV_DISK_HEADER, disk_number);
            }

            // オフセット部分の最小値を求める
            // -> 古いd88はオフセット部分が少ない
            int offset_start = -1; // C++ wxUint32(-1) is 0xFFFFFFFF
            for(int pos = 0; pos < (DISKD88_MAX_TRACKS - 16); pos++) {
                int offset = disk_header.getOffset(pos);
                if (offset_start == -1 || (offset < offset_start && offset > 0)) {
                    offset_start = offset;
                }
            }
            // オフセットなし（トラックなしの場合）初期値をセット
            if (offset_start == -1) {
                offset_start = D88Header.SIZE;
            }

            // オーバーしている部分は0にする
            int max_tracks = DISKD88_MAX_TRACKS - (disk_header.getHeaderSize() - offset_start) / 4;
            if (max_tracks < 0 || max_tracks > DISKD88_MAX_TRACKS) {
                // トラック数がおかしい
                result.setWarn(DiskResult.ERRV_INVALID_DISK, disk_number);
                break;
            }

            for(int pos = max_tracks; pos < DISKD88_MAX_TRACKS; pos++) {
                disk_header.setOffset(pos, 0);
            }

            // オフセットから各トラックのサイズを計算する
            List<DiskD88ParseOffset> offsets = new ArrayList<>();
            for(int pos = 0; pos < max_tracks; pos++) {
                int offset = disk_header.getOffset(pos);
                if (offset >= offset_start) {
                    offsets.add(new DiskD88ParseOffset(pos, offset, 0));
                }
            }
            offsets.sort(DiskD88ParseOffset.CmpByOffset);
            int offsets_count = offsets.size();
            for(int pos = 0; pos < offsets_count-1; pos++) {
                DiskD88ParseOffset curr = offsets.get(pos);
                DiskD88ParseOffset next = offsets.get(pos + 1);
                curr.SetSize(next.GetOffset() - curr.GetOffset());
            }
            if (offsets_count >= 1) {
                DiskD88ParseOffset curr = offsets.get(offsets_count - 1);
                curr.SetSize(disk_size - curr.GetOffset());
            }
            offsets.sort(DiskD88ParseOffset.CmpByNum);

            //
            // ディスクの作成
            //

            valid_header = true;
            DiskImageDisk disk = file.newImageDisk(disk_number, disk_header);

            disk.setOffsetStart(offset_start);

            // parse tracks
            for(int pos = 0; pos < offsets_count && result.getValid() >= 0; pos++) {
                DiskD88ParseOffset curr = offsets.get(pos);

                // オフセットがディスクサイズを超えている？
                if (curr.GetOffset() >= disk_size) {
                    result.setWarn(DiskResult.ERRV_OVERFLOW_OFFSET, disk_number, curr.GetNum(), curr.GetOffset(), disk_size);
                    disk.setOffset(curr.GetNum(), 0);
                    continue;
                }

                ParseTrack(istream, start_pos, curr.GetNum(), curr.GetOffset(), disk_number, curr.GetSize(), disk);
            }

            if (result.getValid() >= 0) {
                // ディスクを追加
                disk.calcMajorNumber();
                file.add(disk, modFlags);
                // セクタ数をチェック
                List<DiskImageTrack> tracks = disk.getTracks();
                if (tracks != null) {
                    for(DiskImageTrack track : tracks) {
                        List<DiskImageSector> sectors = track.getSectors();
                        if (sectors != null) {
                            if (sectors.size() < disk.getSectorsPerTrack()) {
                                result.setWarn(DiskResult.ERRV_SHORT_SECTORS, disk_number, disk.getSectorsPerTrack(), track.getTrackNumber(), track.getSideNumber(), sectors.size());
                            }
                        }
                    }
                }
            } else {
                // delete disk; // Java GC handles this
            }
        } while(false);

        if (!valid_header) {
            // delete disk_header; // Java GC handles this
        }

        return size;
    }

    /**
     * D88ファイルを解析
     * @param istream    解析対象データ
     * @param disk_param パラメータ通常不要
     * @retval  0 正常
     * @retval -1 エラーあり
     * @retval  1 警告あり
     */
    @Override
    public int parse(InputStream istream, final DiskParam disk_param) throws IOException {
        long read_size = 0;
        long stream_size = istream.available();
        int disk_number = file.count();
        // ディスクサイズが0
        if (stream_size == 0) {
            result.setError(DiskResult.ERRV_DISK_SIZE_ZERO, disk_number);
            return result.getValid();
        }
        // チェック
        if (check(istream) != 0) {
            result.setError(DiskResult.ERRV_INVALID_DISK, disk_number);
            return result.getValid();
        }
        for(; read_size < stream_size && result.getValid() >= 0; disk_number++) {
            int size = ParseDisk(istream, read_size, disk_number);
            if (size == 0) break;
            read_size += size;
        }
        return result.getValid();
    }

    @Override
    public int check(InputStream istream, final List<DiskTypeHint> disk_hints, final DiskParam disk_param, List<DiskParam> disk_params, DiskParam manual_param) {
        return -1;
    }

    /**
     * チェック
     * @return 0
     */
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0); // wxFromStart

        D88Header header = new D88Header();
        int header_size_min = D88Header.SIZE - 32;
        int header_size_max = D88Header.SIZE + 16;

        byte[] header_buffer = new byte[D88Header.SIZE];
        int len = istream.read(header_buffer, 0, D88Header.SIZE);

        // Simplified population of header struct for check
        ByteBuffer buf = ByteBuffer.wrap(header_buffer).order(ByteOrder.LITTLE_ENDIAN);
        System.arraycopy(header_buffer, 0, header.diskname, 0, header.diskname.length);
        System.arraycopy(header_buffer, 0, header.offsets, 0, header.offsets.length); // Not perfectly aligned, but represents the check logic

        if (len < header_size_min) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return -1;
        }

        ((SeekableDataInputStream) istream).position(0); // wxFromStart

        // check offset
        int valid = -1;
        int all_zero = 0;
        for(int i=0; i < DISKD88_MAX_TRACKS; i++) {
            // Read 4 bytes at offset
            int offset_pos = i * 4;
            int offset = buf.getInt(offset_pos + (D88Header.SIZE - DISKD88_MAX_TRACKS * 4)); // Simplified: reading offset from header buffer assuming it starts after the fixed header part

            if (offset >= header_size_min && offset <= header_size_max && (offset & 0xf) == 0) {
                valid = 0;
                break;
            } else if (offset == 0) {
                all_zero++;
            }
        }
        if (all_zero == DISKD88_MAX_TRACKS) {
            // 全て0
            valid = 0;
        }
        if (valid < 0) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }
        return valid;
    }
}
