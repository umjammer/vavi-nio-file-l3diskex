///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Serdes;


/**
 * TRS-80 JV3ディスクパーサー
 */
public class DiskJV3Parser extends DiskImageParser {

    // 1=dden, 0=sden
    private static final byte JV3_DENSITY = (byte) 0x80;
    // 0=side 0, 1=side 1
    private static final byte JV3_SIDE = (byte) 0x10;
    // in used sectors: 0=256,1=128,2=1024,3=512
    private static final byte JV3_SIZE = (byte) 0x03;

    // in track and sector fields of free sectors
    private static final byte JV3_FREE = (byte) 0xFF;

    private static final byte JV3_WRITABLE = (byte) 0xFF;
    private static final byte JV3_WPROTECT = (byte) 0x00;

    private static final short[] jv3_size_map = {1, 0, 3, 2};

    /**
     * セクタ番号情報
     */
    private static class Jv3SectorId {

        public byte track_number;
        public byte sector_number;
        public byte flags;

        public static final int SIZE = 3;
    }

    /**
     * ヘッダ情報
     */
    private static class Jv3Header {

        public Jv3SectorId[] ids = new Jv3SectorId[2901];
        public byte write_protected;
        // Total size: 2901 * 3 + 1
        public static final int SIZE = 2901 * Jv3SectorId.SIZE + 1;

        public Jv3Header() {
            for (int i = 0; i < 2901; i++) {
                ids[i] = new Jv3SectorId();
            }
        }
    }

    //
    //
    //

    public DiskJV3Parser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    /**
     * セクタデータの作成
     */
    private int parseSector(InputStream istream, int track_number, int side_number, int sector_number, int sector_size, int sector_nums, boolean single_density, DiskImageTrack track) throws IOException {
        DiskImageSector sector = track.newImageSector(track_number, side_number, sector_number, sector_size, sector_nums, single_density, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();

        int len = istream.read(buf, 0, siz);
        if (len < siz) {
            // ファイルデータが足りない
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }

        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    /**
     * ディスクの解析
     *
     * @param istream 解析対象データ
     * @return サイズ
     */
    private int parseDisk(InputStream istream) throws IOException {
        Jv3Header header = new Jv3Header();

        DiskImageDisk disk = file.newImageDisk(0);
        int d88_offset = disk.getOffsetStart(); // header size
        int d88_offset_pos = 0;
        int max_track_number = -1;
        int limit_offset_pos = disk.getCreatableTracks();
        for (int disk_part = 0; disk_part < 2; disk_part++) {
            int len = istream.available();
            if (len < Jv3Header.SIZE) {
                break;
            }
            Serdes.Util.deserialize(istream, header);

            for (int i = 0; i < 2901; i++) {
                if (header.ids[i].track_number == JV3_FREE || header.ids[i].sector_number == JV3_FREE) {
                    continue;
                }
                int track_number = Byte.toUnsignedInt(header.ids[i].track_number);
                int side_number = (header.ids[i].flags & JV3_SIDE) != 0 ? 1 : 0;
                int sector_number = Byte.toUnsignedInt(header.ids[i].sector_number);
                int sector_size = (128 << jv3_size_map[header.ids[i].flags & JV3_SIZE]);
                boolean single_density = (header.ids[i].flags & JV3_DENSITY) == 0;

                // トラックが存在するか
                DiskImageTrack track = disk.getTrack(track_number, side_number);
                if (track == null) {
                    // 新規トラック
                    track = disk.newImageTrack(track_number, side_number, d88_offset_pos, 1);
                    disk.add(track);
                    d88_offset_pos++;

                    if (d88_offset_pos >= limit_offset_pos) {
                        result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88_offset);
                    }
                }
                int track_size = track.getSize();
                // セクタ作成
                int sector_newsize = parseSector(istream, track_number, side_number, sector_number, sector_size, 1, single_density, track);
                // トラックサイズ更新
                track.setSize(track_size + sector_newsize);
                d88_offset += sector_newsize;

                max_track_number = Math.max(max_track_number, track_number);
            }
        }
        // ディスクサイズ設定
        disk.setSize(d88_offset);
        // 最大トラック番号設定
        disk.setMaxTrackNumber(max_track_number);

        if (result.getValid() >= 0) {
            d88_offset = disk.getOffsetStart();  // header size
            List<DiskImageTrack> tracks = disk.getTracks();
            for (int pos = 0; pos < tracks.size(); pos++) {
                DiskImageTrack track = tracks.get(pos);

                // インターリーブの計算
                track.calcInterleave();
                // サイズの再計算
                track.shrink(false);
                // オフセットの設定
                disk.setOffset(pos, d88_offset);

                d88_offset += track.getSize();
            }

            // ディスクを追加
            DiskParam disk_param = disk.calcMajorNumber();
            if (disk_param != null) {
                disk.setDensity(disk_param.getParamDensity());
            }
            disk.setWriteProtect(header.write_protected == JV3_WPROTECT);
            disk.clearModify();

            file.add(disk, modFlags);
        }

        return d88_offset;
    }

    /**
     * ファイルを解析
     *
     * @param istream 解析対象データ
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    public int parse(InputStream istream) throws IOException {
        result.clear();
        ((SeekableDataInputStream) istream).position(0);

        parseDisk(istream);

        return result.getValid();
    }

    /**
     * チェック
     *
     * @param istream 解析対象データ
     * @return 0: 正常, -1: 対象データではない
     */
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        Jv3Header header = new Jv3Header();

        for (int disk_part = 0; disk_part < 2; disk_part++) {
            int len = istream.available();
            if (disk_part > 0 && len == 0) {
                break;
            }
            if (len < Jv3Header.SIZE) {
                // too short
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }
            Serdes.Util.deserialize(istream, header);
            // 書き込み禁止エリア
            if (header.write_protected != JV3_WRITABLE && header.write_protected != JV3_WPROTECT) {
                result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                return result.getValid();
            }
            // ディスクサイズを計算
            int data_size = 0;
            int err_zero = 0;
            int err_trks = 0;
            int err_secs = 0;
            for (int i = 0; i < 2901; i++) {
                if (header.ids[i].track_number == JV3_FREE || header.ids[i].sector_number == JV3_FREE) {
                    continue;
                }
                // 全て０か
                if (header.ids[i].track_number == 0 && header.ids[i].sector_number == 0 && header.ids[i].flags == 0) {
                    err_zero++;
                }
                // トラック番号が連続しているか
                if (i > 0) {
                    if (!((header.ids[i - 1].track_number & 0xff) == (header.ids[i].track_number & 0xff) || (header.ids[i - 1].track_number & 0xff) + 1 == (header.ids[i].track_number & 0xff))) {
                        err_trks++;
                    }
                }
                // トラック番号は80以内か
                if ((header.ids[i].track_number & 0xff) > 80) {
                    err_trks++;
                }
                // セクタ番号は32以内か
                if ((header.ids[i].sector_number & 0xff) > 32) {
                    err_secs++;
                }
                data_size += 128 << jv3_size_map[header.ids[i].flags & JV3_SIZE];
            }

            if (err_zero >= 40) {
                // ゼロパディングなのでJV3形式ではない
                result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                return result.getValid();
            }
            if (err_trks >= 20) {
                // トラック番号がJV3形式ではない
                result.setError(DiskResult.ERRV_ID_TRACK, 0);
                return result.getValid();
            }
            if (err_secs >= 20) {
                // セクタ番号がJV3形式ではない
                result.setError(DiskResult.ERRV_ID_SECTOR, 0);
                return result.getValid();
            }

            int current = (int) ((SeekableDataInputStream) istream).position();
            int fileOffset = current + data_size;
            ((SeekableDataInputStream) istream).position(fileOffset);
            if (fileOffset != data_size || fileOffset < Jv3Header.SIZE + data_size) {
                // ファイルサイズ足りない
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }
        }

        return 0;
    }
}
