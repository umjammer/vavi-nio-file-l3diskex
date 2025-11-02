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

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/// 2MGディスクパーサー
public class Disk2MGParser extends DiskPlainParser {

    private static final String DISK_2MG_HEADER = "2IMG";

    /// 2MGヘッダ
    @Serdes(bigEndian = false)
    public static class TwomgHeader {

        static final int SIZE = 80;

        @Element(sequence = 1)
        byte[] ident = new byte[4];
        @Element(sequence = 2)
        byte[] creator = new byte[4];
        @Element(sequence = 3)
        short header_size; // LE
        @Element(sequence = 4)
        short version;
        @Element(sequence = 5)
        int format_type;

        // for DOS3.3
        @Element(sequence = 6)
        int flags;
        // for ProDOS
        @Element(sequence = 7)
        int blocks;
        // start position of data
        @Element(sequence = 8)
        int offset_data;
        @Element(sequence = 9)
        int data_size;

        // start position of comment
        @Element(sequence = 10)
        int offset_comm;
        @Element(sequence = 11)
        int comm_size;
        // start position of creator data
        @Element(sequence = 12)
        int offset_creat;
        @Element(sequence = 13)
        int creat_size;

        @Element(sequence = 1)
        byte[] reserved = new byte[16];
    }

    //
    //
    //

    public Disk2MGParser(DiskImageFile file, short modFlags, DiskResult result) {
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
        TwomgHeader header = new TwomgHeader();
        Serdes.Util.deserialize(istream, header);

        int offsetData = header.offset_data;

        ((SeekableDataInputStream) istream).position(offsetData);
        int rc = super.parse(istream, diskParam);
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
    public int check(InputStream istream, List<DiskTypeHint> hints, DiskParam diskParam,
                     List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        if (istream.available() < TwomgHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        TwomgHeader header = new TwomgHeader();
        Serdes.Util.deserialize(istream, header);

        // Header string check
        if (!Arrays.equals(header.ident, DISK_2MG_HEADER.getBytes(StandardCharsets.US_ASCII))) {
            // not disk
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }
        // フォーマットタイプ
        int format_type = header.format_type;
        if (format_type == 2) {
            // unsupported format
            result.setError(DiskResult.ERRV_UNSUPPORTED_TYPE, 0, "NIB");
            return result.getValid();
        } else if (format_type > 2) {
            // invalid
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        int data_size = header.data_size;

        // データサイズからディスクのパラメータを算出
        int sides_per_disk = 1;
        int tracks_per_side = 1;
        int sectors_per_track = 1;
        int sector_size = 256;
        List<DiskParticular> sd = new ArrayList<>();
        List<DiskParticular> pt = new ArrayList<>();

        if (data_size <= 143360) {
            sides_per_disk = 1;
            tracks_per_side = 35;
            sectors_per_track = 16;
            sector_size = 256;
        } else if (data_size <= 819200) {
            sides_per_disk = 2;
            tracks_per_side = 80;
            sectors_per_track = 12;
            sector_size = 512;
            for (int i = 16, n = sectors_per_track - 1; i < tracks_per_side; i += 16, n--) {
                pt.add(new DiskParticular(i, -1, -1, 16, n, 512));
            }
        }

        // ディスクテンプレートから探す
        DiskParam param = gDiskTemplates.findStrict(sides_per_disk, tracks_per_side, sectors_per_track, sector_size,
                1, 0, 0, 0, 0,
                sd, pt);
        if (param != null) {
            diskParams.add(param);
        }

        // 候補がないとき手動設定
        if (diskParams.isEmpty()) {
            manualParam.setDiskParam(
                    sides_per_disk,
                    tracks_per_side,
                    sectors_per_track,
                    sector_size,
                    0,
                    1,
                    sd,
                    pt);
            return 1;
        }

        return 0;
    }
}
