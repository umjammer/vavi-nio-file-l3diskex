//
// Copyright (c) Sasaji. All rights reserved.
//

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/**
 * DIFC.X DIMディスクイメージパーサ
 */
public class DiskDIMParser extends DiskPlainParser {

    private static final String DISK_DIM_HEADER = "DIFC HEADER  \0\0";

    /**
     * DIMディスクヘッダ
     */
    @Serdes
    public static class DimDskHeader {

        private static final int SIZE = 256;

        @Element(sequence = 1)
        byte type;
        @Element(sequence = 2)
        byte[] tracks = new byte[0xaa];
        @Element(sequence = 3)
        byte[] ident = new byte[15];
        @Element(sequence = 4)
        byte[] date = new byte[4];
        @Element(sequence = 5)
        byte[] time = new byte[4];
        @Element(sequence = 6)
        byte[] comments = new byte[0x3d];
        @Element(sequence = 7)
        byte overtrack;
    }

    public DiskDIMParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
    }

    /**
     * DIMファイルの解析
     *
     * @param iStream    入力ディスクイメージ
     * @param diskNumber ディスク番号
     * @param diskParam  ディスクパラメータ
     * @return オフセット（最後のデータ位置）
     */
    @Override
    public int parseDisk(InputStream iStream, int diskNumber, DiskParam diskParam) throws IOException {
        if (iStream.available() < DimDskHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        DimDskHeader header = new DimDskHeader();
        Serdes.Util.deserialize(iStream, header);

        ((SeekableDataInputStream) iStream).position(0x100);
        int fileLength = iStream.available() + 0x100;

        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // パラメータの計算値がディスクサイズの２倍なら
        // 表面にのみデータをセット
        int dummySide = -1;
        if (fileLength * 2 <= diskParam.calcDiskSize()) {
            dummySide = diskParam.getSideNumberBaseOnDisk() + 1;
        }

        int offset = disk.getOffsetStart();
        int offsetPos = 0;
        int trackNum = diskParam.getTrackNumberBaseOnDisk();
        int tracksPerSide = diskParam.getTracksPerSide() + trackNum;
        int sideNumSt = diskParam.getSideNumberBaseOnDisk();
        int sideNumEd = diskParam.getSidesPerDisk() + sideNumSt;
        for (; trackNum < tracksPerSide && result.getValid() >= 0; trackNum++) {
            for (int sideNum = sideNumSt; sideNum < sideNumEd && result.getValid() >= 0; sideNum++) {
                boolean isDummyTrack = sideNum == dummySide;
                if (header.tracks[offsetPos] == 0) {
                    // トラック情報がないのでダミーのトラックを作成
                    isDummyTrack = true;
                }
                offset += parseTrack(iStream, offsetPos, offset, diskNumber, diskParam, trackNum, sideNum, isDummyTrack, disk);
                offsetPos++;
            }
        }
        disk.setSize(offset);

        if (result.getValid() >= 0) {
            // ディスクを追加
            DiskParam param = disk.calcMajorNumber();
            if (param != null) {
                disk.setDensity(param.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return offset;
    }

    /** DIMファイルの解析 */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        ((SeekableDataInputStream) iStream).position(0x100);

        parseDisk(iStream,
                0,
                diskParam);

        return result.getValid();
    }

    /** チェック */
    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        int fileLength = iStream.available();
        if (fileLength < DimDskHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        DimDskHeader header = new DimDskHeader();
        Serdes.Util.deserialize(iStream, header);
        // ヘッダ文字列チェック
        if (!Arrays.equals(header.ident, DISK_DIM_HEADER.getBytes())) {
            // not disk
            return -1;
        }

        int streamSize = fileLength - DimDskHeader.SIZE;
        int sidesPerDisk = 2;
        int sectorSize = 1024;
        int sectorsPerTrack = 8;

        // トラック数を数える
        int maxTracks = 0;
        for (int n = 0; n < header.tracks.length; n++) {
            if (header.tracks[n] != 0) {
                maxTracks = n + 1;
            }
        }
        if (maxTracks == 0) {
            maxTracks = 144;
        }

        // セクタ数決定
        int secsPerTrkMod = (streamSize / sectorSize) % maxTracks;
        if (secsPerTrkMod == 0) {
            // decide
            sectorsPerTrack = (streamSize / sectorSize) / maxTracks;
        } else {
            for (int i = 8; i < 10; i++) {
                if (((streamSize / sectorSize) % i) == 0) {
                    sectorsPerTrack = i;
                    break;
                }
            }
        }

        int tracksPerSide = maxTracks / sidesPerDisk;

        // ディスクテンプレートから探す
        DiskParam dummy = new DiskParam();

        if (diskHints != null) {
            // パラメータヒントあり
            for (int retry = 0; retry < 2 && diskParams.isEmpty(); retry++) {
                // 優先順位の高い候補を追加
                for (DiskTypeHint diskHint : diskHints) {
                    int kind = diskHint.getKind();
                    if (kind != header.type) {
                        continue;
                    }
                    String hint = diskHint.getHint();
                    DiskParam param = gDiskTemplates.find(hint);
                    if (param != null) {
                        // ファイルサイズが一致
                        // or トラック数が一致
                        // or リトライ時
                        if (streamSize == param.calcDiskSize() ||
                                tracksPerSide == param.getTracksPerSide() ||
                                retry > 0) {
                            diskParams.add(param);
                        }
                    }
                }
            }
        }

        // その他に同じパラメータの候補を追加
        gDiskTemplates.find(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize, diskParams, !diskParams.isEmpty());

        if (diskParams.isEmpty()) {
            manualParam.setDiskParam(
                    sidesPerDisk,
                    tracksPerSide,
                    sectorsPerTrack,
                    sectorSize,
                    0,
                    1,
                    dummy.getSingles(),
                    dummy.getParticularTracks());
            return 1;
        }

        return 0;
    }
}
