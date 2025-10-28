/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/// FDIディスクパーサー
public class DiskFDIParser extends DiskPlainParser {

    /// FDI形式ヘッダ
    @Serdes
    public static class FdiDskHeader {

        public static final int SIZE = 10 + 4 + 4 + 4 + 4 + 0xfe0 + 1;

        @Element(sequence = 1)
        byte[] unknown = new byte[0x10];
        @Element(sequence = 2)
        int sectorSize;
        @Element(sequence = 3)
        int sectorsPerTrack;
        @Element(sequence = 4)
        int sidesPerDisk;
        @Element(sequence = 5)
        int tracksPerSide;
        @Element(sequence = 6)
        byte[] reserved = new byte[0xfe0];
        @Element(sequence = 7)
        byte[] data = new byte[1];
    }

    //
    //
    //

    public DiskFDIParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
    }

    @Override
    public int check(InputStream istream,
                     List<DiskTypeHint> diskHints,
                     DiskParam diskParam,
                     List<DiskParam> diskParams,
                     DiskParam manualParam) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        int len = istream.available();
        if (len < FdiDskHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        FdiDskHeader header = new FdiDskHeader();
        Serdes.Util.deserialize(istream, header);
        int sectorSize = header.sectorSize;
        if (sectorSize <= 0 || sectorSize > 4096) {
            // invalid
            result.setError(DiskResult.ERRV_SECTOR_SIZE_HEADER, 0, sectorSize);
            return result.getValid();
        }
        int sectorsPerTrack = header.sectorsPerTrack;
        if (sectorsPerTrack <= 0) {
            // invalid
            result.setError(DiskResult.ERRV_SECTORS_HEADER, 0, sectorsPerTrack);
            return result.getValid();
        }
        int sidesPerDisk = header.sidesPerDisk;
        if (sidesPerDisk <= 0 || sidesPerDisk > 16) {
            // invalid
            result.setError(DiskResult.ERRV_SIDES_HEADER, 0, sidesPerDisk);
            return result.getValid();
        }
        int tracksPerSide = header.tracksPerSide;
        if (tracksPerSide <= 0 || tracksPerSide > 1024) {    // arbitrary upper limit
            // invalid
            result.setError(DiskResult.ERRV_TRACKS_HEADER, 0, tracksPerSide);
            return result.getValid();
        }

        // ディスクテンプレートから探す
        DiskParam dummy = new DiskParam();
        DiskParam param = gDiskTemplates.findStrict(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                1, dummy.getTrackNumberBaseOnDisk(), dummy.getSideNumberBaseOnDisk(), dummy.getSectorNumberBaseOnDisk(), 0,
                dummy.getSingles(), dummy.getParticularTracks());
        if (param != null) {
            diskParams.add(param);
        }

        // 候補がないとき、手動設定
        if (diskParams.isEmpty()) {
            manualParam.setDiskParam(
                    sidesPerDisk,
                    tracksPerSide,
                    sectorsPerTrack,
                    sectorSize,
                    0,
                    1,
                    dummy.getSingles(),
                    dummy.getParticularTracks()
            );
            return 1;
        }

        return 0;
    }

    @Override
    public int parse(InputStream istream, DiskParam diskParam) throws IOException {
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        ((SeekableDataInputStream) istream).position(0);

        int len = istream.available();
        if (len < FdiDskHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        FdiDskHeader header = new FdiDskHeader();
        Serdes.Util.deserialize(istream, header);

        ((SeekableDataInputStream) istream).position(0x1000);

        return super.parse(istream, diskParam);
    }
}
