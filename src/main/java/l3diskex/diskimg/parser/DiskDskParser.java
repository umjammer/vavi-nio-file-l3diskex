/**
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/// CPC DSKディスクパーサー
public class DiskDskParser extends DiskImageParser {

    /** CPC DSK header */
    @Serdes(bigEndian = false)
    public static class CPCDSKHeader {

        final static int SIZE = 34 + 14 + 1 + 1 + 2 + 204;

        @Element(sequence = 1)
        public byte[] ident = new byte[34];
        @Element(sequence = 2)
        public byte[] creator = new byte[14];
        @Element(sequence = 3)
        public byte num_of_tracks;
        @Element(sequence = 4)
        public byte num_of_sides;
        // use only in normal disk
        @Element(sequence = 5)
        public short track_size;
        // use only in extended disk
        @Element(sequence = 6)
        public byte[] track_sizes = new byte[204];
    }

    /** CPC DSK sector */
    @Serdes(bigEndian = false)
    public static class CPCDSKSector {

        public static final int SIZE = 1 + 1 + 1 + 1 + 1 + 1 + 2;

        @Element(sequence = 1)
        public byte C;
        @Element(sequence = 2)
        public byte H;
        @Element(sequence = 3)
        public byte R;
        @Element(sequence = 4)
        public byte N;
        @Element(sequence = 5)
        public byte fdc_status_1;
        @Element(sequence = 6)
        public byte fdc_status_2;
        // bytes // use only in extended disk
        @Element(sequence = 7)
        public short data_length;
    }

    /** CPC DSK track */
    @Serdes(bigEndian = false)
    public static class CPCDSKTrack {

        public static final int SIZE = 12 + 4 + 1 + 1 + 2 + 1 + 1 + 1 + 1 + 29 * CPCDSKSector.SIZE;

        @Element(sequence = 1)
        public byte[] ident = new byte[12];
        @Element(sequence = 2)
        byte[] unused1 = new byte[4];
        @Element(sequence = 3, value = "unsigned byte")
        public int track_number;
        @Element(sequence = 4, value = "unsigned byte")
        public int side_number;
        @Element(sequence = 5)
        byte[] unused2 = new byte[2];
        @Element(sequence = 6, value = "unsigned byte")
        public int sector_size;
        @Element(sequence = 7, value = "unsigned byte")
        public int num_of_sectors;
        @Element(sequence = 8, value = "unsigned byte")
        public int gap3_length;
        @Element(sequence = 9, value = "unsigned byte")
        public int filler_byte;
        @Element(sequence = 10)
        public CPCDSKSector[] sectors = new CPCDSKSector[29];
    }

    /* 0 = normal, 1 = extended */
    private int m_is_extended;

    //
    // CPC DSK形式をD88形式にする
    //
    public DiskDskParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
        this.m_is_extended = 0; // normal
    }

    /** セクタデータの作成 */
    public int parseSector(InputStream istream, int sector_nums, Object user_data, DiskImageTrack track) throws IOException {
        CPCDSKSector id = (CPCDSKSector) user_data;

        int track_number = id.C & 0xff;
        int side_number = id.H & 0xff;
        int sector_number = id.R & 0xff;
        int sector_size = id.N & 0xff;

        if (sector_size > 7) {
            // セクタサイズが大きすぎる
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, track_number, side_number, sector_number, sector_size, id.data_length);
            return 0;
        }

        sector_size = 128 << sector_size;

        DiskImageSector sector = track.newImageSector(track_number, side_number, sector_number, sector_size, sector_nums, false, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();

        int len = istream.readNBytes(buf, 0, siz);
        if (len < siz) {
            // ファイルデータが足りない
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
        }
        if (m_is_extended != 0) {
            // バッファが大きいのでスキップ
            if (id.data_length > siz) {
                int current = (int) ((SeekableDataInputStream) istream).position();
                ((SeekableDataInputStream) istream).position(current + (id.data_length - siz)); // wxFromCurrent
            }
        }

        sector.clearModify();

        return sector.getSize();
    }

    /** トラックデータの作成 */
    public int parseTrack(InputStream istream, int trackSize, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        int len = istream.available();
        if (len != CPCDSKTrack.SIZE) {
            result.setError(DiskResult.ERR_NO_TRACK, 0);
            return 0;
        }
        CPCDSKTrack track_header = new CPCDSKTrack();
        Serdes.Util.deserialize(istream, track_header);
        if (!Arrays.equals(track_header.ident, "Track-Info\r\n".getBytes())) {
            result.setError(DiskResult.ERR_NO_TRACK, 0);
            return 0;
        }

        DiskImageTrack track = disk.newImageTrack(track_header.track_number, track_header.side_number, offsetPos, 1);
        disk.setMaxTrackNumber(track_header.track_number);

        int d88TrackSize = 0;
        for (int pos = 0; pos < track_header.num_of_sectors && result.getValid() >= 0; pos++) {
            d88TrackSize += parseSector(istream,
                    track_header.num_of_sectors,
                    track_header.sectors[pos], track);
        }

        if (result.getValid() >= 0) {
            // インターリーブの計算
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // トラックサイズ設定
            track.setSize(d88TrackSize);
            // サイド番号は各セクタのID Hに合わせる
            track.setSideNumber(track.getMajorIDH());

            // ディスクに追加
            disk.add(track);
            // オフセット設定
            disk.setOffset(offsetPos, offset);
        }

        return d88TrackSize;
    }

    /** ディスクの解析 */
    public int parseDisk(InputStream istream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        int len = istream.available();
        if (len != CPCDSKHeader.SIZE) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return 0;
        }
        CPCDSKHeader header = new CPCDSKHeader();
        Serdes.Util.deserialize(istream, header);

        disk.setName(header.creator, header.creator.length);
        int max_tracks = header.num_of_tracks * header.num_of_sides;

        int d88_offset = disk.getOffsetStart(); // header size
        int d88_offset_pos = 0;
        for (int pos = 0; pos < 204 && pos < max_tracks; pos++) {
            d88_offset += parseTrack(istream,
                    m_is_extended != 0 ? (int) header.track_sizes[pos] * 256 : header.track_size,
                    d88_offset_pos, d88_offset, disk);
            d88_offset_pos++;
            if (d88_offset_pos >= disk.getCreatableTracks()) {
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
            file.add(disk, modFlags);
        }

        return d88_offset;
    }

    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        int len = istream.available();
        if (len < CPCDSKHeader.SIZE) {
            // too short
            return -1;
        }
        CPCDSKHeader header = new CPCDSKHeader();
        Serdes.Util.deserialize(istream, header);

        ((SeekableDataInputStream) istream).position(0);

        // check identifier
        int valid = -1;
        if (!Arrays.equals(header.ident, "MV - CPCEMU Disk-File\r\nDisk-Info\r\n".getBytes())) {
            m_is_extended = 0;	// normal
            valid = 0;
        } else if (!Arrays.equals(header.ident, "EXTENDED CPC DSK File\r\nDisk-Info\r\n".getBytes())) {
            m_is_extended = 1;	// extended
            valid = 0;
        }
        return valid;
    }

    @Override
    public int parse(InputStream istream, DiskParam disk_param) throws IOException {
        parseDisk(istream);
        return result.getValid();
    }
}
