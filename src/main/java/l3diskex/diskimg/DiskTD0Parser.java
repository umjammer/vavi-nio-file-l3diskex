/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import vavi.util.serdes.Serdes;


/**
 * Teledisk td0ディスクパーサ
 */
public class DiskTD0Parser extends DiskImageParser {

    /* --- */
    /*  Header structs (converted from C++ structs)                            */
    /* --- */
    public static class Td0ImageHeader {

        public byte[] ident = new byte[2];           // 2 bytes
        public byte sequence;                        // 1 byte
        public byte checkSequence;                   // 1 byte
        public byte telediskVersion;                 // 1 byte
        public byte dataRate;                        // 1 byte
        public byte diveType;                        // 1 byte
        public byte stepping;                        // 1 byte
        public byte dosAllocFlag;                    // 1 byte
        public byte sidesPerDisk;                    // 1 byte
        public short crc;                            // 2 bytes
    }

    public static class Td0TrackHeader {

        public byte numOfSectors;     // 1 byte
        public byte trackNum;         // 1 byte
        public byte sideNum;          // 1 byte
        public byte crc;              // 1 byte
    }

    public static class Td0SectorHeader {

        public byte trackNum;         // 1 byte
        public byte sideNum;          // 1 byte
        public byte sectorNum;        // 1 byte
        public byte sectorSize;       // 1 byte
        public byte flags;            // 1 byte
    }

    public static class Td0DataHeader {

        public int size;              // 2 bytes (unsigned)
    }

    /* --- */
    /*  Private data members                                                   */
    /* --- */
    private final boolean m_isCompressed;     // TODO: Advanced compress version is not supported.

    /* --- */
    /*  Helper methods (private)                                               */
    /* --- */

    /**
     * セクタデータの作成
     */
    private int parseSector(InputStream istream, int diskNumber, int sectorNums,
                            Object userData, DiskImageTrack track) throws IOException {
        Td0SectorHeader hSector = new Td0SectorHeader();
        Serdes.Util.deserialize(istream, hSector);
//        if (len != hSector.buf.length) {
//            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
//            return 0;
//        }
//        byte[] buf = hSector.buf;
//        int idx = 0;
//        hSector.trackNum = buf[idx++];
//        hSector.sideNum = buf[idx++];
//        hSector.sectorNum = buf[idx++];
//        hSector.sectorSize = buf[idx++];
//        hSector.flags = buf[idx++];
        int trackNumber = hSector.trackNum & 0xFF;
        int sideNumber = hSector.sideNum & 0xFF;
        int sectorNumber = hSector.sectorNum & 0xFF;
        int sectorSize = hSector.sectorSize & 0xFF;

        if (sectorSize > 7) {
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, diskNumber,
                    trackNumber, sideNumber, sectorNumber,
                    sectorSize, 0);
            return 0;
        }

        sectorSize = 128 << sectorSize;

        // セクタ作成（ダミー）
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber,
                sectorNumber, sectorSize,
                sectorNums, false, 0);
        track.add(sector);

        if ((hSector.flags & 0x04) != 0) {
            sector.setDeletedMark(true);
        }

        byte[] buffer = sector.getSectorBuffer();
        int buflen = sector.getSectorBufferSize();

        decodeData(istream, diskNumber, buffer, buflen);

        sector.clearModify();

        return sector.getSize();
    }

    /**
     * トラックデータの作成
     */
    private int parseTrack(InputStream istream, int diskNumber, int offsetPos,
                           int offset, DiskImageDisk disk) throws IOException {
        Td0TrackHeader hTrack = new Td0TrackHeader();
        Serdes.Util.deserialize(istream, hTrack);
//        int len = readFully(istream, hTrack.buf, 0, hTrack.buf.length);
//        if (len != hTrack.buf.length) {
//            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
//            return -1;
//        }
        if (hTrack.numOfSectors == (byte) 0xFF) {
            return -1;
        }

        // トラックの作成（ダミー）
        DiskImageTrack track = disk.newImageTrack(hTrack.trackNum & 0xFF,
                hTrack.sideNum & 0xFF,
                offsetPos, 1);
        disk.setMaxTrackNumber(hTrack.trackNum & 0xFF);

        int d88TrackSize = 0;
        for (int pos = 0; pos < (hTrack.numOfSectors & 0xFF)
                && result.getValid() >= 0; pos++) {
            d88TrackSize += parseSector(istream, diskNumber,
                    hTrack.numOfSectors & 0xFF,
                    null, track);
        }

        if (result.getValid() >= 0) {
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            track.setSize(d88TrackSize);
            track.setSideNumber(track.getMajorIDH());
            disk.add(track);
            disk.setOffset(offsetPos, offset);
        } else {
            // ignore
        }

        return d88TrackSize;
    }

    /**
     * ディスクの解析
     */
    private int parseDisk(InputStream istream, int diskNumber) throws IOException {
        Td0ImageHeader hImage = new Td0ImageHeader();
        Serdes.Util.deserialize(istream, hImage);
//        int len = readFully(istream, hImage.buf, 0, hImage.buf.length);
//        if (len == 0) {
//            return -1;
//        }
//        if (len < hImage.buf.length) {
//            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
//            return result.getValid();
//        }

        Td0CommentHeader hComment = new Td0CommentHeader();
        Serdes.Util.deserialize(istream, hComment);
//        len = readFully(istream, hComment.buf, 0, hComment.buf.length);
//        if (len != hComment.buf.length) {
//            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
//            return result.getValid();
//        }

        int commentLength = hComment.dataLength;
        istream.skipNBytes(commentLength);

        DiskImageDisk disk = file.newImageDisk(diskNumber);

        int d88Offset = disk.getOffsetStart();   // header size
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
            DiskParam diskParam = disk.calcMajorNumber();
            if (diskParam != null) {
                disk.setDensity(diskParam.getParamDensity());
            }
            file.add(disk, modFlags);
        } else {
            // ignore
        }

        return 0;
    }

    /* --- */
    /*  Data decoding methods                                                 */
    /* --- */

    private int decodeRepeatedData(InputStream istream, int diskNumber,
                                   int pos, int slen, int repeat,
                                   byte[] buffer, int buflen) throws IOException {
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

    private int decodePlainData(InputStream istream, int diskNumber,
                                int pos, int slen,
                                byte[] buffer, int buflen) throws IOException {
        int i = 0;
        while (i < slen && pos < buflen && istream.available() > 0) {
            int b = istream.read();
            if (b == -1) break;
            buffer[pos] = (byte) b;
            pos++;
            i++;
        }
        return pos;
    }

    private int decodeData(InputStream istream, int diskNumber,
                           byte[] buffer, int buflen) throws IOException {
        Td0DataHeader hData = new Td0DataHeader();
        Serdes.Util.deserialize(istream, hData);
//        if (len != hData.buf.length) {
//            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
//            return 0;
//        }

        // データブロックを先読みする
        byte[] dataBlock = new byte[hData.size];
        int len = istream.readNBytes(dataBlock, 0, hData.size);
        if (len < hData.size) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, diskNumber);
            return 0;
        }

        // Append a zero byte (not really needed in Java)
        byte[] data = new byte[len + 1];
        System.arraycopy(dataBlock, 0, data, 0, len);
        data[len] = 0;

        ByteArrayInputStream itemp = new ByteArrayInputStream(data);
        int pos = 0;
        int method = itemp.read();
        if (method == 0) {
            pos = decodePlainData(itemp, diskNumber, pos, hData.size, buffer, buflen);
        } else if (method == 1) {
            int repeat = itemp.read() | (itemp.read() << 8);
            pos = decodeRepeatedData(itemp, diskNumber, pos, 2, repeat, buffer, buflen);
        } else if (method == 2) {
            while (pos < buflen) {
                int sub = itemp.read();
                if (sub == 0) {
                    int slen = itemp.read();
                    pos = decodePlainData(itemp, diskNumber, pos, slen, buffer, buflen);
                } else {
                    int slen = sub * 2;
                    int repeat = itemp.read() | (itemp.read() << 8);
                    pos = decodeRepeatedData(itemp, diskNumber, pos, slen, repeat, buffer, buflen);
                }
            }
        }
        return pos;
    }

    /* --- */
    /*  Check / Parse methods (public)                                         */
    /* --- */

    /**
     * チェック
     */
    @Override
    public int check(InputStream istream) throws IOException {
        istream.skipNBytes(0);

        Td0ImageHeader header = new Td0ImageHeader();
        Serdes.Util.deserialize(istream, header);
//        if (len < header.buf.length) {
//            return -1;
//        }
        if (header.ident[0] != 'T' || header.ident[1] != 'D') {
            return -1;
        }
        if (header.telediskVersion != (byte) 0x15) {
            return -1;
        }
        int sidesPerDisk = header.sidesPerDisk & 0xFF;
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
        istream.skip(0);
        for (int diskNumber = 0; ; diskNumber++) {
            if (parseDisk(istream, diskNumber) < 0) {
                break;
            }
        }
        return result.getValid();
    }

    /* --- */
    /*  Constructor / Destructor                                              */
    /* --- */

    public DiskTD0Parser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
        m_isCompressed = false;
    }

    public void close() {
        // nothing to do
    }

    /* --- */
    /*  Data structures (converted from C++ structs)                          */
    /* --- */

    public static class Td0CommentHeader {

        public short crc;
        public short dataLength;
        public byte year;
        public byte month;
        public byte day;
        public byte hour;
        public byte minute;
        public byte second;
        public byte[] buf = new byte[10];
    }
}
