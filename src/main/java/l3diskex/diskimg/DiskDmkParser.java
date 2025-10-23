package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;


// TRS-80 DMKディスクパーサー
public class DiskDmkParser extends DiskImageParser {

    // Static nested classes for C++ structs
    private static class TrsDmkHeader {

        public byte write_protected;    // 0x00:no 0xff:yes
        public byte num_of_tracks;
        public short track_length;      // track length (little-endian assumed)
        public byte flags;
        public byte[] reserved = new byte[7];
        public int signature;           // 0x12345678:Real Disk, 0x00000000:Virtual Disk (little-endian assumed)

        public static final int SIZE = 16;

        public void read(InputStream is) throws IOException {
            byte[] buffer = new byte[SIZE];
            if (is.read(buffer) != SIZE) {
                throw new IOException("Failed to read DMK header");
            }
            ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
            write_protected = bb.get();
            num_of_tracks = bb.get();
            track_length = bb.getShort();
            flags = bb.get();
            bb.get(reserved);
            signature = bb.getInt();
        }
    }

    private static class TrsDmkTrack {

        public short[] ptr = new short[64]; // pointer to sector IDAMs (little-endian assumed)

        public static final int SIZE = 64 * 2;

        public void read(InputStream is) throws IOException {
            byte[] buffer = new byte[SIZE];
            if (is.read(buffer) != SIZE) {
                throw new IOException("Failed to read DMK track header");
            }
            ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 64; i++) {
                ptr[i] = bb.getShort();
            }
        }
    }

    private static class TrsDmkSectorId {

        public byte IDAM;
        public byte C; // Track number
        public byte H; // Side number
        public byte R; // Sector number
        public byte N; // Sector size code
        public short CRC; // (little-endian assumed)

        public static final int SIZE = 6;

        public void read(InputStream is) throws IOException {
            byte[] buffer = new byte[SIZE];
            if (is.read(buffer) != SIZE) {
                throw new IOException("Failed to read DMK sector ID");
            }
            ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
            IDAM = bb.get();
            C = bb.get();
            H = bb.get();
            R = bb.get();
            N = bb.get();
            CRC = bb.getShort();
        }
    }

    // Constants from C++
    private static final int DMK_IDAM_DENSITY = 0x8000;
    private static final int DMK_IDAM_OFFSET = 0x3fff;
    private static final int DMK_DISK_REAL = 0x12345678;
    private static final int DMK_DISK_VIRTUAL = 0x00000000;

    private static final class Amark {

        public final byte[] id;
        public final int deleted;

        public Amark(String id, int deleted) {
            this.id = id != null ? id.getBytes() : null;
            this.deleted = deleted;
        }
    }

    private static final Amark[][] AMARKS = {
            {
                    new Amark("\u0000\u0000\u0000\u00fb", 0),
                    new Amark("\u0000\u0000\u0000\u00f8", 0), // TRSDOS 1.3 SYSTEM
                    new Amark("\u0000\u0000\u0000\u00fa", 0), // TRSDOS DIR
                    new Amark(null, 0)
            },
            {
                    new Amark("\u00a1\u00a1\u00a1\u00fb", 0),
                    new Amark("\u00a1\u00a1\u00a1\u00f8", 0), // TRSDOS 1.3 SYSTEM
                    new Amark("\u00a1\u00a1\u00a1\u00fa", 0), // TRSDOS DIR
                    new Amark(null, 0)
            }
    };


    public DiskDmkParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    // No need for explicit destructor in Java

    // データマークをさがす
    private boolean findDataMark(InputStream istream, int sector_size, boolean double_density, int[] deleted) throws IOException {
        byte[] buf = new byte[64];
        int currentFileOffset = (int) ((SeekableDataInputStream) istream).position();
        int len = istream.read(buf, 0, buf.length);
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
        ((SeekableDataInputStream) istream).position(offset); // wxFromCurrent

        return true;
    }

    // セクタデータの作成
    private int parseSector(InputStream istream, int sector_nums, int flags, DiskImageTrack track) throws IOException {
        TrsDmkSectorId id = new TrsDmkSectorId();

        try {
            id.read(istream);
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_SECTORS_HEADER, 0);
            return 0;
        }

        int track_number = id.C & 0xFF;
        int side_number = id.H & 0xFF;
        int sector_number = id.R & 0xFF;
        int sector_size_code = id.N & 0xFF;

        if (sector_size_code > 7) {
            // セクタサイズが大きすぎる
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, track_number, side_number, sector_number, sector_size_code, sector_size_code);
            return 0;
        }

        int sector_size = (128 << sector_size_code);

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

        int len = istream.read(buf, 0, siz);
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
        TrsDmkTrack track_header = new TrsDmkTrack();

        int file_offset = (int) ((SeekableDataInputStream) istream).position();

        try {
            track_header.read(istream);
        } catch (IOException e) {
            result.setError(DiskResult.ERR_NO_TRACK, 0);
            return 0;
        }

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
            // short is little-endian, swap if needed for host byte order comparison, but assume host is LE or
            // the TrsDmkTrack reader already handled it. C++ code uses wxUINT16_SWAP_ON_BE.
            // Assuming Java implementation needs explicit swap if the host is BE.
            int ptr = track_header.ptr[pos] & 0xFFFF; // Short to unsigned int

            int next_offset = (ptr & DMK_IDAM_OFFSET);
            // move position in file
            ((SeekableDataInputStream) istream).position(file_offset + next_offset); // wxFromStart

            d88_track_size += parseSector(istream, num_of_sectors, (ptr & ~DMK_IDAM_OFFSET), track);
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

        TrsDmkHeader header = new TrsDmkHeader();
        try {
            header.read(istream);
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return 0;
        }

        int max_tracks = header.num_of_tracks & 0xFF;

        int d88_offset = disk.getOffsetStart(); // header size
        int d88_offset_pos = 0;
        int limit_offset_pos = disk.getCreatableTracks();

        // C++ code used wxUINT16_SWAP_ON_BE(header.track_length)
        int track_length = header.track_length & 0xFFFF;

        for (int pos = 0; pos < 204 && pos < max_tracks; pos++) {
            d88_offset += parseTrack(istream,
                    track_length,
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
            disk.setWriteProtect((header.write_protected & 0xFF) == 0xff);

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
    public int check(InputStream inputStream) throws IOException {
        if (!(inputStream instanceof SeekableDataInputStream)) {
            // Adapter needed if not SeekableInputStream
            return -1;
        }
        SeekableDataInputStream istream = (SeekableDataInputStream) inputStream;

        istream.position(0); // wxFromStart

        TrsDmkHeader header = new TrsDmkHeader();
        try {
            header.read(istream);
        } catch (IOException e) {
            // too short
            return -1;
        }

        istream.position(0); // wxFromStart

        // check header
        // first data is 0x00 or 0xff (write protected flag)
        if ((header.write_protected & 0xFF) != 0x00 && (header.write_protected & 0xFF) != 0xff) {
            return -1;
        }
        // signature - C++ uses wxUINT32_SWAP_ON_BE
        int signature = header.signature;
        if (signature != DMK_DISK_REAL && signature != DMK_DISK_VIRTUAL) {
            return -1;
        }
        // file size
        // C++ uses wxUINT16_SWAP_ON_BE(header.track_length)
        int track_length = header.track_length & 0xFFFF;
        int calculated_size = (header.num_of_tracks & 0xFF) * track_length + TrsDmkHeader.SIZE;
        if (calculated_size < istream.available()) {
            // too short / or too large? The C++ comment says "too short" but the condition is '<'
            // If the DMK format specifies the size in the header, then the file shouldn't be larger.
            return -1;
        }
        // C++ code might have an error in logic or the DMK format implies calculated_size should be <= istream.getLength()

        return 0;
    }

    // TRS-80 DMKファイルを解析
    @Override
    public int parse(InputStream inputStream, DiskParam disk_param) {
        if (!(inputStream instanceof SeekableDataInputStream)) {
            return -1;
        }
        SeekableDataInputStream istream = (SeekableDataInputStream) inputStream;

        try {
            parseDisk(istream);
        } catch (IOException e) {
            // Handle IO exception during parsing, setting a general error
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }
        return result.getValid();
    }
}
