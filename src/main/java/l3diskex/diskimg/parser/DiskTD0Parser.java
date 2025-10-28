/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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


/**
 * Teledisk td0ディスクパーサ
 */
public class DiskTD0Parser extends DiskImageParser {

    /// Teledisk td0 ディスクイメージヘッダ
    @Serdes
    public static class Td0ImageHeader {

        public static final int SIZE = 2 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 2;

        @Element(sequence = 1)
        public byte[] ident = new byte[2];
        @Element(sequence = 2)
        public byte sequence;
        @Element(sequence = 3)
        public byte checkSequence;
        @Element(sequence = 4)
        public byte telediskVersion;
        @Element(sequence = 5)
        public byte dataRate;
        @Element(sequence = 6)
        public byte diveType;
        @Element(sequence = 7)
        public byte stepping;
        @Element(sequence = 8)
        public byte dosAllocFlag;
        @Element(sequence = 9)
        public byte sidesPerDisk;
        @Element(sequence = 10)
        public short crc;
    }

    /// Teledisk td0 コメントヘッダ
    @Serdes
    public static class Td0CommentHeader {

        public static final int SIZE = 2 + 2 + 1 + 1 + 1 + 1 + 1 + 1 + 10;

        @Element(sequence = 1)
        public short crc;
        @Element(sequence = 2)
        public short dataLength;
        @Element(sequence = 3)
        public byte year;
        @Element(sequence = 4)
        public byte month;
        @Element(sequence = 5)
        public byte day;
        @Element(sequence = 6)
        public byte hour;
        @Element(sequence = 7)
        public byte minute;
        @Element(sequence = 8)
        public byte second;
        @Element(sequence = 9)
        public byte[] buf = new byte[10];
    }

    /// Teledisk td0 トラックヘッダ
    @Serdes
    public static class Td0TrackHeader {

        public static final int SIZE = 1 + 1 + 1 + 1;

        @Element(sequence = 1)
        public byte numOfSectors;
        @Element(sequence = 2)
        public byte trackNum;
        @Element(sequence = 3)
        public byte sideNum;
        @Element(sequence = 4)
        public byte crc;
    }

    /// Teledisk td0 セクタヘッダ
    @Serdes
    public static class Td0SectorHeader {

        public static final int SIZE = 1 + 1 + 1 + 1 + 1;

        @Element(sequence = 1)
        public byte trackNum;
        @Element(sequence = 2)
        public byte sideNum;
        @Element(sequence = 3)
        public byte sectorNum;
        @Element(sequence = 4)
        public byte sectorSize;
        @Element(sequence = 5)
        public byte flags;
    }

    /// Teledisk td0 データヘッダ
    @Serdes
    public static class Td0DataHeader {

        public static final int SIZE = 2;

        @Element(sequence = 1, value = "unsigned short")
        public int size;
    }

    // TODO: Advanced compress version is not supported.
    private final boolean m_isCompressed;

    /**
     * セクタデータの作成
     */
    private int parseSector(InputStream istream, int diskNumber, int sectorNums, Object userData, DiskImageTrack track) throws IOException {
        int len = istream.available();
        if (len < Td0SectorHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }
        Td0SectorHeader hSector = new Td0SectorHeader();
        Serdes.Util.deserialize(istream, hSector);

        int trackNumber = hSector.trackNum & 0xff;
        int sideNumber = hSector.sideNum & 0xff;
        int sectorNumber = hSector.sectorNum & 0xff;
        int sectorSize = hSector.sectorSize & 0xff;

        if (sectorSize > 7) {
            // セクタサイズが大きすぎる
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, diskNumber, trackNumber, sideNumber, sectorNumber, sectorSize, 0);
            return 0;
        }

        sectorSize = 128 << sectorSize;

        // セクタ作成
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, sectorNums, false, 0);
        track.add(sector);

        if ((hSector.flags & 0x04) != 0) {
            // deleted mark
            sector.setDeletedMark(true);
        }

        byte[] buffer = sector.getSectorBuffer();
        int buflen = sector.getSectorBufferSize();

        decodeData(istream, diskNumber, buffer, buflen);

        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    /**
     * トラックデータの作成
     */
    private int parseTrack(InputStream istream, int diskNumber, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        int len = istream.available();
        if (len < Td0TrackHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return -1;
        }
        Td0TrackHeader hTrack = new Td0TrackHeader();
        Serdes.Util.deserialize(istream, hTrack);
        if (hTrack.numOfSectors == (byte) 0xff) {
            return -1;
        }

        // トラックの作成
        DiskImageTrack track = disk.newImageTrack(hTrack.trackNum & 0xff, hTrack.sideNum & 0xff, offsetPos, 1);
        disk.setMaxTrackNumber(hTrack.trackNum & 0xff);

        int d88TrackSize = 0;
        for (int pos = 0; pos < (hTrack.numOfSectors & 0xff) && result.getValid() >= 0; pos++) {
            d88TrackSize += parseSector(istream, diskNumber, hTrack.numOfSectors & 0xff, null, track);
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

    /**
     * TD0ファイルを解析
     *
     * @param istream    解析対象データ
     * @param diskNumber ディスク番号
     * @return -1: finish parsing, 0: parse next disk
     */
    private int parseDisk(InputStream istream, int diskNumber) throws IOException {
        int len = istream.available();
        if (len == 0) {
            // no disk 解析終り
            return -1;
        }
        if (len < Td0ImageHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return result.getValid();
        }
        Td0ImageHeader hImage = new Td0ImageHeader();
        Serdes.Util.deserialize(istream, hImage);

        len = istream.available();
        if (len < Td0CommentHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return result.getValid();
        }
        Td0CommentHeader hComment = new Td0CommentHeader();
        Serdes.Util.deserialize(istream, hComment);

        int commentLength = hComment.dataLength;
        istream.skipNBytes(commentLength);

        // ディスク作成
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // トラック解析
        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < 204; pos++) {
            int offset = parseTrack(istream, diskNumber, d88OffsetPos, d88Offset, disk);
            if (offset == -1) {
                break;
            }
            d88Offset += offset;

            d88OffsetPos++;
            if (d88OffsetPos >= disk.getCreatableTracks()) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, diskNumber, d88Offset);
            }
        }
        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            // ディスクを追加
            DiskParam diskParam = disk.calcMajorNumber();
            if (diskParam != null) {
                disk.setDensity(diskParam.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return 0;
    }

    /** 繰り返しデータを展開 */
    private int decodeRepeatedData(InputStream istream, int diskNumber, int pos, int slen, int repeat, byte[] buffer, int buflen) throws IOException {
        byte[] ptn = new byte[slen];

        int len = istream.readNBytes(ptn, 0, slen);
        if (len == 0) {
            return pos;
        }
        for (int j = 0; j < repeat && pos < buflen; j++) {
            for (int i = 0; i < slen && pos < buflen; i++) {
                buffer[pos] = ptn[i];
                pos++;
            }
        }
        return pos;
    }

    /** ベタデータを展開 */
    private int decodePlainData(InputStream istream, int diskNumber, int pos, int slen, byte[] buffer, int buflen) throws IOException {
        for (int i = 0; i < slen && pos < buflen && istream.available() > 0; i++) {
            int b = istream.read();
            if (b == -1) break;
            buffer[pos] = (byte) b;
            pos++;
        }
        return pos;
    }

    /** データを展開してバッファに書き込む */
    private int decodeData(InputStream istream, int diskNumber, byte[] buffer, int buflen) throws IOException {
        int len = istream.available();
        if (len < Td0DataHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }
        Td0DataHeader hData = new Td0DataHeader();
        Serdes.Util.deserialize(istream, hData);

        // データブロックを先読みする
        byte[] data = new byte[hData.size];
        len = istream.readNBytes(data, 0, hData.size);
        if (len < hData.size) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data);
        baos.write(0);

        ByteArrayInputStream itemp = new ByteArrayInputStream(baos.toByteArray());

        int pos = 0;
        int method = itemp.read();
        if (method == 0) {
            // ベタ
            pos = decodePlainData(itemp, diskNumber, pos, hData.size, buffer, buflen);
        } else if (method == 1) {
            // 繰り返しデータ
            int repeat = itemp.read();
            repeat |= (itemp.read() << 8);
            pos = decodeRepeatedData(itemp, diskNumber, pos, 2, repeat, buffer, buflen);
        } else if (method == 2) {
            do {
                int sub = itemp.read();
                if (sub == 0) {
                    // ベタデータ
                    int slen = itemp.read();
                    pos = decodePlainData(itemp, diskNumber, pos, slen, buffer, buflen);
                } else {
                    // 繰り返しデータ
                    int slen = sub * 2;
                    int repeat = itemp.read() | (itemp.read() << 8);
                    pos = decodeRepeatedData(itemp, diskNumber, pos, slen, repeat, buffer, buflen);
                }
            } while (pos < buflen);
        }
        return pos;
    }

    /**
     * チェック
     */
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        int len = istream.available();
        if (len < Td0ImageHeader.SIZE) {
            // too short
            return -1;
        }
        Td0ImageHeader header = new Td0ImageHeader();
        Serdes.Util.deserialize(istream, header);
        if (header.ident[0] != 'T' || header.ident[1] != 'D') {
            // not TD0 image
            // note that "td" (lower) which advanced compress version is not supported.
            return -1;
        }
        if (header.telediskVersion != (byte) 0x15) {
            // support only 1.5 version
            return -1;
        }

        int sidesPerDisk = header.sidesPerDisk & 0xff;
        if (sidesPerDisk > 2) {
            return -1;
        }

        return 0;
    }

    /**
     * 解析
     */
    @Override
    public int parse(InputStream istream, DiskParam diskParam) throws IOException {
        ((SeekableDataInputStream) istream).position(0);
        for (int diskNumber = 0; ; diskNumber++) {
            if (parseDisk(istream, diskNumber) < 0) {
                break;
            }
        }
        return result.getValid();
    }

    public DiskTD0Parser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);

        m_isCompressed = false;
    }
}
