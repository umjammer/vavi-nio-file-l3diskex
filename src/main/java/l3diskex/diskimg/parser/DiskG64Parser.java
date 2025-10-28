/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/// Commodore G64 ディスクパーサ
public class DiskG64Parser extends DiskImageParser {

    /** G64 header */
    @Serdes(bigEndian = false)
    private static final class G64Header {

        /** 8‑byte signature ("GCR-1541") */
        @Element(sequence = 1)
        public byte[] sig = new byte[8];
        @Element(sequence = 2)
        public byte version;
        @Element(sequence = 3)
        public byte num_of_tracks;
        @Element(sequence = 4)
        public short max_track_size;

        public static final int SIZE = 8 + 1 + 1 + 2; // 12
    }

    /** G64 sector header */
    @Serdes(bigEndian = false)
    private static final class G64SectorHeader {

        @Element(sequence = 1)
        public byte block_id;
        @Element(sequence = 2)
        public byte format_id0;
        @Element(sequence = 3)
        public byte format_id1;
        @Element(sequence = 4)
        public byte track_number;
        @Element(sequence = 5)
        public byte sector_number;
        @Element(sequence = 6)
        public byte reserved1;
        @Element(sequence = 7)
        public byte reserved2;
        // The original C++ struct had a 2‑byte reserved array
    }

    /** G64 sector data */
    @Serdes(bigEndian = false)
    private static final class G64SectorData {

        @Element(sequence = 1)
        public byte block_id;
        @Element(sequence = 2)
        public byte[] data = new byte[256];
        @Element(sequence = 3)
        public byte chksum;
        @Element(sequence = 4)
        public byte reserved1;
        @Element(sequence = 5)
        public byte reserved2;
    }

    //
    //
    //

    /** */
    private final G64Header m_header;

    /** */
    public DiskG64Parser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);

        m_header = new G64Header();
    }

    /**
     * セクタデータの作成
     *
     * @param indata         セクタデータ
     * @param disk_number    ディスク番号
     * @param track_number   トラック番号
     * @param side_number    サイド番号
     * @param sector_nums    セクタ数
     * @param sector_number  セクタ番号
     * @param sector_size    セクタサイズ
     * @param single_density 単密度か
     * @param track          [in,out] トラック
     * @return ヘッダ込みのセクタサイズ
     */
    private int parseSector(byte[] indata, int disk_number, int track_number, int side_number, int sector_nums,
                            int sector_number, int sector_size, boolean single_density, DiskImageTrack track) {

        // Create a new image sector
        DiskImageSector sector = track.newImageSector(track_number, side_number, sector_number, sector_size, sector_nums, false, 0);
        track.add(sector);

        // Copy plain data
        sector.copy(indata, sector_size);

        sector.setSingleDensity(single_density);
        sector.clearModify();

        // Return sector size (with header)
        return sector.getSize();
    }

    /**
     * GCR (5‑bit) -> HEX (4‑bit) map
     */
    private static final int[] GCR_BIN_MAP = {
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0x08, 0x00, 0x01, 0xff, 0x0c, 0x04, 0x05,
            0xff, 0xff, 0x02, 0x03, 0xff, 0x0f, 0x06, 0x07,
            0xff, 0x09, 0x0a, 0x0b, 0xff, 0x0d, 0x0e, 0xff
    };

    /**
     * GCRデータをデコード
     *
     * @param indata   入力データ
     * @param inbitlen 入力データ長さ(bit単位)
     * @param outdata  [out] 出力データ
     * @param outlen   出力データバッファサイズ
     */
    private static int decodeGCR(byte[] indata, int bitpos, int inbitlen, byte[] outdata, int outpos, int outlen) {
        while (bitpos < inbitlen && outpos < outlen) {
            int adat = 0;
            for (int i = 0; i < 2; i++) {
                int pos = bitpos >> 3;
                int bit = bitpos & 7;

                int dat = ((indata[pos] & 0xff) << 8) | ((indata[pos + 1] & 0xff));

                int idat = (dat >> (11 - bit));
                idat &= 0x1f;

                // 5‑bit to 4‑bit conversion
                int ndat = GCR_BIN_MAP[idat] & 0xff;

                adat = adat << 4;
                adat |= ndat;

                bitpos += 5;
            }
            outdata[outpos] = (byte) adat;
            outpos++;
        }
        return outpos;
    }

    /**
     * トラックデータの作成
     *
     * @param istream     ディスクイメージ
     * @param disk_number ディスク番号
     * @param side_number サイド番号
     * @param offset_pos  オフセット番号
     * @param offset      オフセット位置
     * @param disk        [in,out] ディスク
     * @return -1: エラー or 終り, >0: トラックサイズ
     */
    private int parseTrack(InputStream istream, int disk_number, int side_number, int offset_pos, int offset, DiskImageDisk disk) throws IOException {
        // Read track size
        byte[] buffer2 = new byte[2];
        if (istream.readNBytes(buffer2, 0, 2) != 2) {
            return -1;
        }

        int trackSize = ByteUtil.readBeShort(buffer2, 0) & 0xffff;
        if (trackSize == 0) {
            return -1;
        }

        byte[] indata = new byte[trackSize + 32];
        int insize = indata.length;
        byte[] outdata = new byte[trackSize];
        int outsize = outdata.length;

        int len = istream.readNBytes(indata, 0, insize);

        List<byte[]> sectorHeaders = new ArrayList<>();
        List<byte[]> sectorDatas = new ArrayList<>();

        int inpos = 0;
        int outpos = 0;
        while (inpos < trackSize) {
            // Skip header sync ($ff) – usually 4–5 bytes
            while (indata[inpos] == (byte) 0xff && inpos < trackSize) {
                inpos++;
            }
            if (inpos >= trackSize) {
                break;
            }

            // Header GCR – usually 10 bytes
            len = decodeGCR(indata, inpos, 80, outdata, outpos, outsize - outpos);
            sectorHeaders.add(Arrays.copyOfRange(outdata, outpos, outpos + len));
            inpos += 10;
            outpos += len;

            // Skip header gap
            while (indata[inpos] != (byte) 0xff && inpos < trackSize) {
                inpos++;
            }
            if (inpos >= trackSize) break;

            // Skip data sync ($ff) – usually 4–5 bytes
            while (indata[inpos] == (byte) 0xff && inpos < trackSize) {
                inpos++;
            }
            if (inpos >= trackSize) break;

            // Data GCR – usually 325 bytes
            len = decodeGCR(indata, inpos, 2600, outdata, outpos, outsize - outpos);
            sectorDatas.add(Arrays.copyOfRange(outdata, outpos, outpos + len));
            inpos += 325;
            outpos += len;

            // Skip data gap
            while (indata[inpos] != (byte) 0xff && inpos < trackSize) {
                inpos++;
            }
            if (inpos >= trackSize) break;
        }

        // Determine track number
        G64SectorHeader sh = new G64SectorHeader();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorHeaders.getFirst()), sh);
        int trackNumber = sh.track_number;

        // Create track
        int d88TrackSize = 0;
        DiskImageTrack track = disk.newImageTrack(trackNumber, side_number, offset_pos, 1);
        disk.setMaxTrackNumber(trackNumber);

        for (int i = 0; i < sectorDatas.size(); i++) {
            // Create a sector
            sh = new G64SectorHeader();
            Serdes.Util.deserialize(new ByteArrayInputStream(sectorHeaders.get(i)), sh);
            G64SectorData sd = new G64SectorData();
            Serdes.Util.deserialize(new ByteArrayInputStream(sectorDatas.get(i)), sh);

            d88TrackSize = parseSector(
                    sd.data,
                    disk_number,
                    sh.track_number,
                    side_number,
                    sectorHeaders.size(),
                    sh.sector_number,
                    256,
                    false,
                    track);
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
            disk.setOffset(offset_pos, offset);
        }

        return d88TrackSize;
    }

    /// ファイルを解析
    ///
    /// @param istream     解析対象データ
    /// @param disk_number ディスク番号
    /// @return -1: finish parsing, 0: parse next disk
    private int parseDisk(InputStream istream, int disk_number) throws IOException {
        // Parse header
        if (parseHeader(istream, disk_number) < 0) {
            return -1;
        }

        // Read offsets to track data
        int len = 0;
        List<Integer> offsets = new ArrayList<>();
        boolean hasHalfTrack = false;
        for (int pos = 0; pos < m_header.num_of_tracks; pos++) {
            byte[] buf4 = new byte[4];
            len = istream.readNBytes(buf4, 0, 4);
            if (len < 4) {
                return -1;
            }
            int offset = ByteUtil.readLeShort(buf4, 0) & 0xffff;

            offsets.add(offset);
            // ハーフトラックを持っているか
            hasHalfTrack |= offset != 0 && (pos & 1) != 0;
        }

        // Skip speed zone data
        int size = m_header.num_of_tracks * 4;
        int current = (int) ((SeekableDataInputStream) istream).position();
        ((SeekableDataInputStream) istream).position(current + size); // wxFromCurrent

        // Create disk image
        DiskImageDisk disk = file.newImageDisk(disk_number);

        // Parse tracks
        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < m_header.num_of_tracks; pos++) {
            size = offsets.get(pos);
            if (size == 0) {
                // skip empty track
                continue;
            }

            ((SeekableDataInputStream) istream).position(size);
            int offset = parseTrack(istream, disk_number, hasHalfTrack ? pos & 1 : 0, d88OffsetPos, d88Offset, disk);
            if (offset == -1) {
                return -1;
            }
            d88Offset += offset;
            d88OffsetPos++;
        }
        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            // ディスクを追加
            DiskParam disk_param = disk.calcMajorNumber();
            if (disk_param != null) {
                disk.setDensity(disk_param.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return 0;
    }

    /// ヘッダ解析
    ///
    /// @param istream     解析対象データ
    /// @param disk_number ディスク番号
    /// @return -1: エラー, 0:
    private int parseHeader(InputStream istream, int disk_number) throws IOException {
        int len = istream.available();
        if (len < G64Header.SIZE) {
            // too short
            return -1;
        }
        Serdes.Util.deserialize(istream, m_header);
        if (!Arrays.equals(m_header.sig, "GCR-1541".getBytes())) {
            // not an image
            return -1;
        }
        if (m_header.num_of_tracks == 0) {
            return -1;
        }

        return 0;
    }

    @Override
    public int check(InputStream istream, List<DiskTypeHint> disk_hints, DiskParam disk_param, List<DiskParam> disk_params, DiskParam manual_param) {
        return -1;
    }

    /// チェック
    ///
    /// @param istream 解析対象データ
    /// @return 1 選択ダイアログ表示
    ///  0 正常（候補が複数ある時はダイアログ表示）
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        if (parseHeader(istream, 0) < 0) {
            return -1;
        }

        return 0;
    }

    /// ファイルを解析
    ///
    /// @param istream    解析対象データ
    /// @param disk_param パラメータ通常不要
    /// @return 0: 正常, -1: エラーあり, 1: 警告あり
    @Override
    public int parse(InputStream istream, DiskParam disk_param) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        for (int disk_number = 0; disk_number < 1; disk_number++) {
            if (parseDisk(istream, disk_number) < 0) {
                break;
            }
        }
        return result.getValid();
    }
}
