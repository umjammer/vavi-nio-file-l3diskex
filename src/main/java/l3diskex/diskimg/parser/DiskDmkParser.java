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


// TRS-80 DMKディスクパーサー
public class DiskDmkParser extends DiskImageParser {

    /// TRS-80 DMK header
    @Serdes(bigEndian = false)
    public static class TrsDmkHeader {

        // 0x00:no 0xff:yes
        public byte write_protected;
        public byte num_of_tracks;
        // track length
        public short track_length;
        public byte flags;
        public byte[] reserved = new byte[7];
        // 0x12345678:Real Disk, 0x00000000:Virtual Disk
        public int signature;

        public static final int SIZE = 16;
    }

    /// TRS-80 DMK track
    @Serdes(bigEndian = false)
    public static class TrsDmkTrack {

        // pointer to sector IDAMs
        public short[] ptr = new short[64];

        public static final int SIZE = 64 * 2;
    }

    /// TRS-80 DMK sector id
    @Serdes(bigEndian = false)
    public static class TrsDmkSectorId {

        public byte IDAM;
        public byte C;
        public byte H;
        public byte R;
        public byte N;
        public short CRC;

        public static final int SIZE = 6;
    }

    private static final int DMK_IDAM_DENSITY = 0x8000;
    private static final int DMK_IDAM_OFFSET = 0x3fff;

    private static final int DMK_DISK_REAL = 0x12345678;
    private static final int DMK_DISK_VIRTUAL = 0x00000000;

    /** */
    private static final class Amark {

        public final byte[] id;
        public final int deleted;

        public Amark(String id, int deleted) {
            this.id = id != null ? id.getBytes() : null;
            this.deleted = deleted;
        }
    }

    /** */
    private static final Amark[][] AMARKS = {
            {
                    new Amark("\u0000\u0000\u0000\u00fb", 0),
                    new Amark("\u0000\u0000\u0000\u00f8", 0), // TRSDOS 1.3 SYSTEM
                    new Amark("\u0000\u0000\u0000\u00fa", 0), // TRSDOS DIR
            },
            {
                    new Amark("\u00a1\u00a1\u00a1\u00fb", 0),
                    new Amark("\u00a1\u00a1\u00a1\u00f8", 0), // TRSDOS 1.3 SYSTEM
                    new Amark("\u00a1\u00a1\u00a1\u00fa", 0), // TRSDOS DIR
            }
    };

    //
    // TRS-80 DMK形式をD88形式にする
    //
    public DiskDmkParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    // データマークをさがす
    private boolean findDataMark(InputStream istream, int sector_size, boolean double_density, int[] deleted) throws IOException {
        byte[] buf = new byte[64];

        int len = istream.readNBytes(buf, 0, buf.length);
        if (len < buf.length) {
            return false;
        }

        boolean found = false;
        int pos;
        int den = double_density ? 1 : 0;

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
        int current = (int) ((SeekableDataInputStream) istream).position();
        ((SeekableDataInputStream) istream).position(current + offset); // wxFromCurrent

        return true;
    }

    // セクタデータの作成
    private int parseSector(InputStream istream, int sector_nums, int flags, DiskImageTrack track) throws IOException {
        int len = istream.available();
        if (len != TrsDmkSectorId.SIZE) {
            result.setError(DiskResult.ERRV_SECTORS_HEADER, 0);
            return 0;
        }
        TrsDmkSectorId id = new TrsDmkSectorId();
        Serdes.Util.deserialize(istream, id);

        int track_number = id.C & 0xff;
        int side_number = id.H & 0xff;
        int sector_number = id.R & 0xff;
        int sector_size_code = id.N & 0xff;

        if (sector_size_code > 7) {
            // セクタサイズが大きすぎる
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, track_number, side_number, sector_number, sector_size_code, sector_size_code);
            return 0;
        }

        int sector_size = 128 << sector_size_code;

        // データの開始位置をさがす
        int[] deleted = new int[1];
        if (!findDataMark(istream, sector_size, (flags & DMK_IDAM_DENSITY) != 0, deleted)) {
            result.setError(DiskResult.ERRV_NO_SECTOR, 0, sector_number, track_number, side_number);
            return 0;
        }

        DiskImageSector sector = track.newImageSector(track_number, side_number, sector_number, sector_size, sector_nums, false, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();

        len = istream.readNBytes(buf, 0, siz);
        if (len < siz) {
            // ファイルデータが足りない
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }

        sector.setDeletedMark(deleted[0] != 0);
        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    // トラックデータの作成
    private int parseTrack(InputStream istream, int track_size, int offset_pos, int offset, DiskImageDisk disk) throws IOException {
        int file_offset = (int) ((SeekableDataInputStream) istream).position();

        int len = istream.available();
        if (len != TrsDmkTrack.SIZE) {
            result.setError(DiskResult.ERR_NO_TRACK, 0);
            return 0;
        }
        TrsDmkTrack track_header = new TrsDmkTrack();
        Serdes.Util.deserialize(istream, track_header);

        // セクタ数を計算
        int num_of_sectors = 0;
        for (int pos = 0; pos < 64; pos++) {
            if (track_header.ptr[pos] == 0) {
                break;
            }
            num_of_sectors++;
        }

        DiskImageTrack track = disk.newImageTrack(0, 0, offset_pos, 1);

        int d88_track_size = 0;
        for (int pos = 0; pos < num_of_sectors && result.getValid() >= 0; pos++) {
            int ptr = track_header.ptr[pos] & 0xffff;
            int next_offset = (ptr & DMK_IDAM_OFFSET);
            // move position in file
            ((SeekableDataInputStream) istream).position(file_offset + next_offset); // wxFromStart

            d88_track_size += parseSector(istream,
                    num_of_sectors,
                    (ptr & ~DMK_IDAM_OFFSET), track);
        }

        if (result.getValid() >= 0) {
            // インターリーブの計算
            track.calcInterleave();

            // トラックサイズ設定
            track.setSize(d88_track_size);
            // トラック番号は各セクタのID Cに合わせる
            int track_number = track.getMajorIDC();
            track.setTrackNumber(track_number);
            // サイド番号は各セクタのID Hに合わせる
            track.setSideNumber(track.getMajorIDH());

            // ディスクに追加
            disk.add(track);
            // オフセット設定
            disk.setOffset(offset_pos, offset);
            // 最大トラック番号設定
            disk.setMaxTrackNumber(track_number);

            disk.clearModify();
        }

        // 次のトラックデータの先頭へ
        ((SeekableDataInputStream) istream).position(file_offset + track_size); // wxFromStart

        return d88_track_size;
    }

    // ディスクの解析
    private int parseDisk(InputStream istream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        int len = istream.available();
        if (len != TrsDmkHeader.SIZE) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return 0;
        }
        TrsDmkHeader header = new TrsDmkHeader();
        Serdes.Util.deserialize(istream, header);

        int max_tracks = header.num_of_tracks & 0xff;

        int d88_offset = disk.getOffsetStart(); // header size
        int d88_offset_pos = 0;
        int limit_offset_pos = disk.getCreatableTracks();
        for (int pos = 0; pos < 204 && pos < max_tracks; pos++) {
            d88_offset += parseTrack(istream,
                    header.track_length & 0xffff,
                    d88_offset_pos, d88_offset, disk);
            d88_offset_pos++;
            if (d88_offset_pos >= limit_offset_pos) {
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
            disk.setWriteProtect((header.write_protected & 0xff) == 0xff);

            file.add(disk, modFlags);
        }

        return d88_offset;
    }

    @Override
    public int check(InputStream istream, List<DiskTypeHint> disk_hints, DiskParam disk_param, List<DiskParam> disk_params, DiskParam manual_param) {
        return -1;
    }

    // TRS-80 DMKファイルかどうかをチェック
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        int len = istream.available();
        if (len < TrsDmkHeader.SIZE) {
            // too short
            return -1;
        }
        TrsDmkHeader header = new TrsDmkHeader();
        Serdes.Util.deserialize(istream, header);

        ((SeekableDataInputStream) istream).position(0);

        // check header
        // first data is 0x00 or 0xff (write protected flag)
        if ((header.write_protected & 0xff) != 0x00 && (header.write_protected & 0xff) != 0xff) {
            return -1;
        }
        // signature
        if (header.signature != DMK_DISK_REAL && header.signature != DMK_DISK_VIRTUAL) {
            return -1;
        }
        // file size
        if ((header.num_of_tracks & 0xff) * (header.track_length & 0xffff) + TrsDmkHeader.SIZE < len /* file size */) {
            // too short
            return -1;
        }

        return 0;
    }

    // TRS-80 DMKファイルを解析
    @Override
    public int parse(InputStream istream, DiskParam disk_param) throws IOException {
        parseDisk(istream);
        return result.getValid();
    }
}
