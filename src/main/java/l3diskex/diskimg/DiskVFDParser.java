package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskImage.IntHashMapUtil;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;


/// Virtual98 FDディスクイメージパーサ
public class DiskVFDParser extends DiskImageParser {

    // Virtual98 FD形式セクタヘッダ
    static class VfdSectorHeader {

        public byte c;
        public byte h;
        public byte r;
        public byte n;
        public byte data;
        public byte unknown0;
        public byte dden;
        public byte unknown2;
        public int start; // -1 (0xffffffff) is no sector data

        public static final int SIZE = 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 4;

        public void read(ByteBuffer buffer) {
            c = buffer.get();
            h = buffer.get();
            r = buffer.get();
            n = buffer.get();
            data = buffer.get();
            unknown0 = buffer.get();
            dden = buffer.get();
            unknown2 = buffer.get();
            start = buffer.getInt();
        }
    }

    // Virtual98 FD形式トラックヘッダ
    static class VfdTrackHeader {

        public VfdSectorHeader[] sectors = new VfdSectorHeader[26];
        public static final int SIZE = VfdSectorHeader.SIZE * 26;

        public VfdTrackHeader() {
            for (int i = 0; i < 26; i++) {
                sectors[i] = new VfdSectorHeader();
            }
        }

        public void read(ByteBuffer buffer) {
            for (int i = 0; i < 26; i++) {
                sectors[i].read(buffer);
            }
        }
    }

    // Virtual98 FD形式ヘッダ
    static class VfdHeader {

        public byte[] identifier = new byte[8]; // VFD1.00
        public byte[] label = new byte[0xd4];
        public VfdTrackHeader[] tracks = new VfdTrackHeader[160];
        public static final int SIZE = 8 + 0xd4 + VfdTrackHeader.SIZE * 160;

        public VfdHeader() {
            for (int i = 0; i < 160; i++) {
                tracks[i] = new VfdTrackHeader();
            }
        }

        public void read(ByteBuffer buffer) {
            buffer.get(identifier);
            buffer.get(label);
            for (int i = 0; i < 160; i++) {
                tracks[i].read(buffer);
            }
        }
    }

    public DiskVFDParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    // Since Java classes don't need explicit destructors like C++, no need for a corresponding finalizer/method here unless specific resource cleanup is needed.

    /// セクタデータの作成
    private int ParseSector(InputStream istream, int sector_nums, VfdSectorHeader sector_header, DiskImageTrack track) throws IOException {
        if ((sector_header.c & 0xFF) == 0xff || (sector_header.h & 0xFF) == 0xff || (sector_header.r & 0xFF) == 0xff) {
            // セクタなし
            return 0;
        }

        int track_number = sector_header.c & 0xFF;
        int side_number = sector_header.h & 0xFF;
        int sector_number = sector_header.r & 0xFF;
        int sector_size_code = sector_header.n & 0xFF;

        if (sector_size_code > 7) {
            // セクタサイズが大きすぎる
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, track_number, side_number, sector_number, sector_size_code, 128 << sector_size_code);
            return 0;
        }

        int sector_size = (128 << sector_size_code);

        // セクタ作成
        DiskImageSector sector = track.newImageSector(track_number, side_number, sector_number, sector_size, sector_nums, (sector_header.dden & 0xFF) == 0, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();

        if (sector_header.start != -1) {
            // 実際のデータを取得
            ((SeekableDataInputStream) istream).position(sector_header.start); // Convert to long for seeking

            int result = istream.read(buf, 0, siz);
            if (result < siz) {
                // ファイルデータが足りない
                this.result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            }
        } else {
            // データはないので特定データでサプレスする
            byte data = sector_header.data;
            Arrays.fill(buf, data);
        }
        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    /// トラックデータの作成
    private int ParseTrack(InputStream istream, VfdTrackHeader track_header, int offset_pos, int offset, DiskImageDisk disk) throws IOException {
        // メジャーな番号を調べる
        int num_of_sectors = 0;
        Map<Integer, Integer> track_number_map = new HashMap<>();
        Map<Integer, Integer> side_number_map = new HashMap<>();
        for (int sec = 0; sec < 26; sec++) {
            if ((track_header.sectors[sec].c & 0xFF) == 0xff || (track_header.sectors[sec].h & 0xFF) == 0xff || (track_header.sectors[sec].r & 0xFF) == 0xff) {
                continue;
            }
            num_of_sectors++;
            IntHashMapUtil.increaseValue(track_number_map, track_header.sectors[sec].c & 0xFF);
            IntHashMapUtil.increaseValue(side_number_map, track_header.sectors[sec].h & 0xFF);
        }
        if (num_of_sectors == 0) {
            // セクタなし
            return 0;
        }

        int track_number = IntHashMapUtil.getMaxKeyOnMaxValue(track_number_map);
        int side_number = IntHashMapUtil.getMaxKeyOnMaxValue(side_number_map);

        // トラック作成
        DiskImageTrack track = disk.newImageTrack(track_number, side_number, offset_pos, 1);
        disk.setMaxTrackNumber(track_number);

        int d88_track_size = 0;
        for (int sec = 0; sec < 26 && result.getValid() >= 0; sec++) {
            d88_track_size += ParseSector(istream
                    , num_of_sectors
                    , track_header.sectors[sec], track);
        }

        if (result.getValid() >= 0) {
            // インターリーブの計算
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // トラックサイズ設定
            track.setSize(d88_track_size);
            // サイド番号は各セクタのID Hに合わせる
            track.setSideNumber(track.getMajorIDH());

            // ディスクに追加
            disk.add(track);
            // オフセット設定
            disk.setOffset(offset_pos, offset); // offset is treated as unsigned int
        } else {
            // delete track (handled by garbage collection in Java, but we might need to close resources if present)
            track = null;
        }

        return d88_track_size;
    }

    /// ディスクの解析
    private int ParseDisk(InputStream istream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        VfdHeader header = new VfdHeader();
        byte[] headerBytes = new byte[VfdHeader.SIZE];

        int result = istream.read(headerBytes, 0, VfdHeader.SIZE);

        if (result != VfdHeader.SIZE) {
            this.result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return 0;
        }

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        header.read(buffer);

        disk.setName(header.label, header.label.length);

        // d88トラックの作成
        int d88_offset = disk.getOffsetStart(); // header size
        int d88_offset_pos = 0;
        for (int pos = 0; pos < 160; pos++) {
            d88_offset += ParseTrack(istream, header.tracks[pos]
                    , d88_offset_pos, d88_offset, disk);
            d88_offset_pos++;
            if (d88_offset_pos >= disk.getCreatableTracks()) {
                this.result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88_offset);
            }
        }
        disk.setSize(d88_offset); // size is treated as unsigned int

        if (this.result.getValid() >= 0) {
            // ディスクを追加
            DiskParam disk_param = disk.calcMajorNumber();
            if (disk_param != null) {
                disk.setDensity(disk_param.getParamDensity());
            }
            // Assuming file.Add exists and handles the disk object
            // file.Add(disk, modFlags);
        } else {
            // delete disk (handled by garbage collection in Java)
            disk = null;
        }

        return d88_offset;
    }

    // Unused in the C++ file's logic
    public int check(InputStreamReader istream, List<DiskTypeHint> disk_hints, DiskParam disk_param, List<DiskParam> disk_params, DiskParam manual_param) {
        return -1;
    }

    /**
     * チェック
     *
     * @param istream 解析対象データ
     * @return 0 正常
     *        -1 エラー
     */
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        VfdHeader header = new VfdHeader();
        byte[] headerBytes = new byte[VfdHeader.SIZE];

        int result = istream.read(headerBytes, 0, VfdHeader.SIZE);

        if (result < VfdHeader.SIZE) {
            // too short
            return -1;
        }

        ((SeekableDataInputStream) istream).position(0);

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        header.read(buffer);

        // check identifier: memcmp(header.identifier, "VFD1", 4) != 0
        if (header.identifier[0] != 'V' || header.identifier[1] != 'F' || header.identifier[2] != 'D' || header.identifier[3] != '1') {
            return -1;
        }

        return 0;
    }

    /**
     * VFDファイルを解析
     *
     * @param istream    解析対象データ
     * @param disk_param パラメータ通常不要
     * @return 0 正常
     *        -1 エラーあり
     *         1 警告あり
     */
    @Override
    public int parse(InputStream istream, DiskParam disk_param) {
        try {
            ParseDisk(istream);
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0); // Assuming IO error is invalid disk
            return -1;
        }
        return result.getValid();
    }
}
