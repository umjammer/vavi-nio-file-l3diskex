/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.parser.Disk2MGParser.TwomgHeader;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam.DiskParticular;
import l3diskex.diskimg.DiskParam.DiskParticulars;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/// Apple Disk Copyディスクパーサー
public class DiskADCParser extends DiskPlainParser {

    /// Apple Disk Copyヘッダ 84bytes
    @Serdes
    public static class AdcHeader {

        private static final int SIZE = 84;

        @Element(sequence = 1, value = "byte")
        int labelLength;
        @Element(sequence = 2)
        byte[] label = new byte[63];   // 63 bytes
        @Element(sequence = 3)
        int dataSize;         // big‑endian
        @Element(sequence = 4)
        int resourceSize;     // big‑endian
        @Element(sequence = 5)
        int createDate;
        @Element(sequence = 6)
        int modifyDate;
        @Element(sequence = 7)
        byte[] unknown = new byte[4];   // 4 bytes
    }

    public DiskADCParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
    }

    @Override
    public int parse(InputStream istream, DiskParam diskParam) throws IOException {
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        ((SeekableDataInputStream) istream).position(0);

        if (istream.available() < TwomgHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        AdcHeader header = new AdcHeader();
        Serdes.Util.deserialize(istream, header);

        return super.parse(istream, diskParam);
    }

    @Override
    public int check(InputStream istream,
                     List<DiskTypeHint> diskHints,
                     DiskParam diskParam,
                     List<DiskParam> diskParams,
                     DiskParam manualParam) throws IOException {
        ((SeekableDataInputStream) istream).position(0);
        int streamLen = istream.available();

        if (istream.available() < TwomgHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        AdcHeader header = new AdcHeader();
        Serdes.Util.deserialize(istream, header);

        // ラベル長の末尾が0かどうか
        if (header.labelLength > 63 || header.label[header.labelLength] != 0) {
            // not disk
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }
        // ファイルサイズが一致するか
        int dataSize = header.dataSize;
        int fileSize = AdcHeader.SIZE + dataSize + header.resourceSize;
        if (fileSize != streamLen) {
            // not disk
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        // infer disk parameters from data size
        int sidesPerDisk = 1;
        int tracksPerSide = 1;
        int sectorsPerTrack = 1;
        int sectorSize = 256;
        DiskParticulars sd = new DiskParticulars();
        DiskParticulars pt = new DiskParticulars();

        if (dataSize <= 143360) {  // 140K
            sidesPerDisk = 1;
            tracksPerSide = 35;
            sectorsPerTrack = 16;
            sectorSize = 256;
        } else if (dataSize <= 819200) {
            // Apple 2DD 800K
            sidesPerDisk = 2;
            tracksPerSide = 80;
            sectorsPerTrack = 12;
            sectorSize = 512;
            for (int i = 16, n = sectorsPerTrack - 1; i < tracksPerSide; i += 16, n--) {
                pt.add(new DiskParticular(i, -1, -1, 16, n, 512));
            }
        } else if (dataSize <= 1474560) {
            // 2HD 1440K
            sidesPerDisk = 2;
            tracksPerSide = 80;
            sectorsPerTrack = 18;
            sectorSize = 512;
        }

        // look up a matching template
        DiskParam param = gDiskTemplates.findStrict(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                1, 0, 0, 0, 0,
                sd, pt);
        if (param != null) {
            diskParams.add(param);
        }

        // no candidates → manual entry
        if (diskParams.isEmpty()) {
            manualParam.setDiskParam(
                    sidesPerDisk,
                    tracksPerSide,
                    sectorsPerTrack,
                    sectorSize,
                    0,
                    1,
                    sd,
                    pt);
            return 1;
        }

        return 0;
    }
}
