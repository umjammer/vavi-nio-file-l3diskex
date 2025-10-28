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


/// Virtual98 FDディスクイメージパーサ
public class DiskVFDParser extends DiskImageParser {

    // Virtual98 FD形式セクタヘッダ
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

    // Virtual98 FD形式トラックヘッダ
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

    // Virtual98 FD形式ヘッダ
    @Serdes(bigEndian = false)
    public static class VfdHeader {

        public static final int SIZE = 8 + 0xd4 + VfdTrackHeader.SIZE * 160;

        // VFD1.00
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

    public DiskVFDParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    /** セクタデータの作成 */
    private int parseSector(InputStream istream, int sector_nums, Object user_data, DiskImageTrack track) throws IOException {
        VfdSectorHeader sector_header = (VfdSectorHeader) user_data;

        if ((sector_header.c & 0xff) == 0xff || (sector_header.h & 0xff) == 0xff || (sector_header.r & 0xff) == 0xff) {
            // セクタなし
            return 0;
        }

        int track_number = sector_header.c & 0xff;
        int side_number = sector_header.h & 0xff;
        int sector_number = sector_header.r & 0xff;
        int sector_size_code = sector_header.n & 0xff;

        if (sector_size_code > 7) {
            // セクタサイズが大きすぎる
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, track_number, side_number, sector_number, sector_size_code, 128 << sector_size_code);
            return 0;
        }

        int sector_size = 128 << sector_size_code;

        // セクタ作成
        DiskImageSector sector = track.newImageSector(track_number, side_number, sector_number, sector_size, sector_nums, (sector_header.dden & 0xFF) == 0, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();

        if (sector_header.start != -1) {
            // 実際のデータを取得
            ((SeekableDataInputStream) istream).position(sector_header.start);

            int result = istream.readNBytes(buf, 0, siz);
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

    /** トラックデータの作成 */
    private int parseTrack(InputStream istream, Object userData, int offset_pos, int offset, DiskImageDisk disk) throws IOException {
        VfdTrackHeader track_header = (VfdTrackHeader) userData;

        // メジャーな番号を調べる
        int num_of_sectors = 0;
        Map<Integer, Integer> track_number_map = new HashMap<>();
        Map<Integer, Integer> side_number_map = new HashMap<>();
        for (int sec = 0; sec < 26; sec++) {
            if ((track_header.sectors[sec].c & 0xff) == 0xff || (track_header.sectors[sec].h & 0xff) == 0xff || (track_header.sectors[sec].r & 0xff) == 0xff) {
                continue;
            }
            num_of_sectors++;
            IntHashMapUtil.increaseValue(track_number_map, track_header.sectors[sec].c & 0xff);
            IntHashMapUtil.increaseValue(side_number_map, track_header.sectors[sec].h & 0xff);
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
            d88_track_size += parseSector(istream,
                    num_of_sectors,
                    track_header.sectors[sec], track);
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
            disk.setOffset(offset_pos, offset);
        }

        return d88_track_size;
    }

    /** ディスクの解析 */
    private int parseDisk(InputStream istream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        int len = istream.available();
        if (len < VfdHeader.SIZE) {
            this.result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return 0;
        }
        VfdHeader header = new VfdHeader();
        Serdes.Util.deserialize(istream, header);

        disk.setName(header.label, header.label.length);

        // d88トラックの作成
        int d88_offset = disk.getOffsetStart(); // header size
        int d88_offset_pos = 0;
        for (int pos = 0; pos < 160; pos++) {
            d88_offset += parseTrack(istream, header.tracks[pos],
                    d88_offset_pos, d88_offset, disk);
            d88_offset_pos++;
            if (d88_offset_pos >= disk.getCreatableTracks()) {
                this.result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88_offset);
            }
        }
        disk.setSize(d88_offset);

        if (this.result.getValid() >= 0) {
            // ディスクを追加
            DiskParam disk_param = disk.calcMajorNumber();
            if (disk_param != null) {
                disk.setDensity(disk_param.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return d88_offset;
    }

    @Override
    public int check(InputStream istream, List<DiskTypeHint> disk_hints, DiskParam disk_param, List<DiskParam> disk_params, DiskParam manual_param) {
        return -1;
    }

    /**
     * チェック
     *
     * @param istream 解析対象データ
     * @return 0: 正常, -1: エラー
     */
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        int len = istream.available();
        if (len < VfdHeader.SIZE) {
            // too short
            return -1;
        }
        VfdHeader header = new VfdHeader();
        Serdes.Util.deserialize(istream, header);

        ((SeekableDataInputStream) istream).position(0);

        // check identifier
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
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    @Override
    public int parse(InputStream istream, DiskParam disk_param) throws IOException {
        parseDisk(istream);
        return result.getValid();
    }
}
