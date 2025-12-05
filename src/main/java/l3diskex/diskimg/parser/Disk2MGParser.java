///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParam.DiskParticular;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.diskimg.DiskParam.diskTemplates;


/**
 * 2MG Disk parser
 *
 * XGS (Apple][ GS emulator)
 */
public class Disk2MGParser extends DiskPlainParser {

    /** magic */
    private static final String DISK_2MG_HEADER = "2IMG";

    /** 2MGヘッダ */
    @Serdes(bigEndian = false)
    public static class TwoMgHeader {

        static final int SIZE = 80;

        @Element(sequence = 1)
        byte[] ident = new byte[4];
        @Element(sequence = 2)
        byte[] creator = new byte[4];
        @Element(sequence = 3)
        short headerSize; // LE
        @Element(sequence = 4)
        short version;
        @Element(sequence = 5)
        int formatType;

        // for DOS3.3
        @Element(sequence = 6)
        int flags;
        // for ProDOS
        @Element(sequence = 7)
        int blocks;
        // start position of data
        @Element(sequence = 8)
        int dataOffset;
        @Element(sequence = 9)
        int dataSize;

        // start position of comment
        @Element(sequence = 10)
        int commentOffset;
        @Element(sequence = 11)
        int commentSize;
        // start position of creator data
        @Element(sequence = 12)
        int createOffset;
        @Element(sequence = 13)
        int createSize;

        @Element(sequence = 1)
        byte[] reserved = new byte[16];
    }

    //
    //
    //

    @Override
    public boolean isSupported(String type) {
        return "2mg".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
    }

    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        ((SeekableDataInputStream) iStream).position(0);

        if (iStream.available() < TwoMgHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        TwoMgHeader header = new TwoMgHeader();
        Serdes.Util.deserialize(iStream, header);

        int offsetData = header.dataOffset;

        ((SeekableDataInputStream) iStream).position(offsetData);
        int rc = super.parse(iStream, diskParam);
        if (rc >= 0) {
            DiskImageDisk disk = file.getDisk(0);
            if (disk != null) {
                int flags = header.flags;

                // write protected ?
                if ((flags & 0x8000_0000) != 0) {
                    disk.setWriteProtect(true);
                }
            }
        }
        return rc;
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> hints, DiskParam diskParam,
                     List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        if (iStream.available() < TwoMgHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        TwoMgHeader header = new TwoMgHeader();
        Serdes.Util.deserialize(iStream, header);

        // Header string check
        if (!Arrays.equals(header.ident, DISK_2MG_HEADER.getBytes(StandardCharsets.US_ASCII))) {
            // not disk
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }
        // フォーマットタイプ
        int formatType = header.formatType;
        if (formatType == 2) {
            // unsupported format
            result.setError(DiskResult.ERRV_UNSUPPORTED_TYPE, 0, "NIB");
            return result.getValid();
        } else if (formatType > 2) {
            // invalid
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        int dataSize = header.dataSize;

        // データサイズからディスクのパラメータを算出
        int sidesPerDisk = 1;
        int tracksPerSide = 1;
        int sectorsPerTrack = 1;
        int sectorSize = 256;
        List<DiskParticular> sd = new ArrayList<>();
        List<DiskParticular> pt = new ArrayList<>();

        if (dataSize <= 143360) {
            sidesPerDisk = 1;
            tracksPerSide = 35;
            sectorsPerTrack = 16;
            sectorSize = 256;
        } else if (dataSize <= 819200) {
            sidesPerDisk = 2;
            tracksPerSide = 80;
            sectorsPerTrack = 12;
            sectorSize = 512;
            for (int i = 16, n = sectorsPerTrack - 1; i < tracksPerSide; i += 16, n--) {
                pt.add(new DiskParticular(i, -1, -1, 16, n, 512));
            }
        }

        // ディスクテンプレートから探す
        DiskParam param = diskTemplates.findStrict(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                1, 0, 0, 0, 0,
                sd, pt);
        if (param != null) {
            diskParams.add(param);
        }

        // 候補がないとき手動設定
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
