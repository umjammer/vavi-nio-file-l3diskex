package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParser.DiskImageParser;


/**
 * TRS-80 JV3ディスクパーサー
 */
public class DiskJV3Parser extends DiskImageParser {

    // Constants from diskjv3parser.cpp
    private static final byte JV3_DENSITY = (byte) 0x80;  // 1=dden, 0=sden
    private static final byte JV3_SIDE = (byte) 0x10;  // 0=side 0, 1=side 1
    private static final byte JV3_SIZE = (byte) 0x03;  // in used sectors: 0=256,1=128,2=1024,3=512

    private static final byte JV3_FREE = (byte) 0xFF;  // in track and sector fields of free sectors

    private static final byte JV3_WRITABLE = (byte) 0xFF;
    private static final byte JV3_WPROTECT = (byte) 0x00;

    private static final short[] jv3_size_map = {1, 0, 3, 2};

    /**
     * セクタ番号情報 (Simulates st_jv3_sector_id struct)
     */
    private static class Jv3SectorId {
        public byte track_number;
        public byte sector_number;
        public byte flags;
        public static final int SIZE = 3;

        public void read(InputStream is) throws IOException {
            track_number = (byte) is.read();
            sector_number = (byte) is.read();
            flags = (byte) is.read();
        }
    }

    /**
     * ヘッダ情報 (Simulates st_jv3_header struct)
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

        public boolean read(InputStream is) throws IOException {
            for (int i = 0; i < 2901; i++) {
                ids[i].read(is);
            }
            int wp = is.read();
            if (wp == -1) return false;
            write_protected = (byte) wp;
            return true;
        }
    }

    public DiskJV3Parser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    /**
     * セクタデータの作成
     */
    private int ParseSector(InputStream istream, int track_number, int side_number, int sector_number, int sector_size, int sector_nums, boolean single_density, DiskImageTrack track) throws IOException {
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
     */
    private int ParseDisk(InputStream istream) throws IOException {
        Jv3Header header = new Jv3Header();

        DiskImageDisk disk = file.newImageDisk(0);
        int d88_offset = disk.getOffsetStart();    // header size
        int d88_offset_pos = 0;
        int max_track_number = -1;
        int limit_offset_pos = disk.getCreatableTracks();

        for (int disk_part = 0; disk_part < 2; disk_part++) {
            boolean read_success = header.read(istream);

            if (disk_part > 0 && !read_success) {
                break;
            } else if (!read_success) {
                // If the first header read fails entirely.
                break;
            }

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
                        // continue parsing data, but stop adding tracks
                    }
                }
                int track_size = track.getSize();
                // セクタ作成
                int sector_newsize = ParseSector(istream, track_number, side_number, sector_number, sector_size, 1, single_density, track);
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

        } else {
            // delete disk is not necessary in Java, rely on GC.
        }

        return d88_offset;
    }

    /**
     * ファイルを解析
     * @param istream    解析対象データ
     * @retval  0 正常
     * @retval -1 エラーあり
     * @retval  1 警告あり
     */
    public int Parse(InputStream istream) throws IOException {
        result.clear();
        // istream.SeekI(0); -> Not directly supported by base InputStream, assume it's at start or wrapped.

        ParseDisk(istream);

        return result.getValid();
    }

    /**
     * チェック
     * @param istream       解析対象データ
     * @retval 0 正常 -1 対象データではない
     */
    @Override
    public int check(InputStream istream) throws IOException {
        // istream.SeekI(0); -> Not directly supported by base InputStream, assume it's at start or wrapped.

        Jv3Header header = new Jv3Header();

        for (int disk_part = 0; disk_part < 2; disk_part++) {
            // Read header bytes into a temporary buffer to avoid multiple reads/seeks if not an IO exception
            byte[] headerBuffer = new byte[Jv3Header.SIZE];
            int len = istream.read(headerBuffer);

            if (disk_part > 0 && len == -1) {
                break;
            }
            if (len < Jv3Header.SIZE) {
                // too short
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }

            // Manually populate the header structure from the buffer
            ByteBuffer buffer = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 2901; i++) {
                header.ids[i].track_number = buffer.get();
                header.ids[i].sector_number = buffer.get();
                header.ids[i].flags = buffer.get();
            }
            header.write_protected = buffer.get();


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

                int current_track = Byte.toUnsignedInt(header.ids[i].track_number);
                int current_sector = Byte.toUnsignedInt(header.ids[i].sector_number);

                // トラック番号が連続しているか (Requires looking at the previous non-free entry, which the C++ code *incorrectly* does by checking i-1 regardless of JV3_FREE)
                // We'll follow the original C++ logic (which is likely flawed for interleaved tracks but required for direct conversion).
                if (i > 0) {
                    int prev_track = Byte.toUnsignedInt(header.ids[i - 1].track_number);
                    if (!(prev_track == current_track || prev_track + 1 == current_track)) {
                        err_trks++;
                    }
                }

                // トラック番号は80以内か
                if (current_track > 80) {
                    err_trks++;
                }
                // セクタ番号は32以内か
                if (current_sector > 32) {
                    err_secs++;
                }
                data_size += (128 << jv3_size_map[header.ids[i].flags & JV3_SIZE]);
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

            // Simulate istream.SeekI(data_size, wxFromCurrent) and check file bounds
            // This is complex for a generic InputStream. We will read/skip the data_size bytes.
            long skipped = istream.skip(data_size);

            if (skipped != data_size) {
                // ファイルサイズ足りない
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }
        }

        return 0;
    }
}
