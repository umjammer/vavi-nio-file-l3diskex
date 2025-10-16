//
// Copyright (c) Sasaji. All rights reserved.
//

package l3diskex.diskimg;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.FileParam.DiskTypeHint;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/**
 *  DIFC.X DIMディスクイメージパーサ
 */
public class DiskDIMParser extends DiskPlainParser {

    /*  ------------------  DIMヘッダ構造体  ------------------ */
    /** DIFC.X DIMヘッダ 256バイト */
    private static final int DIM_HEADER_SIZE = 256;
    /** "DIFC HEADER  \0\0"（15バイト） */
    private static final byte[] DISK_DIM_HEADER = new byte[] {
            'D','I','F','C',' ','H','E','A','D','E','R',' ',' ','\0','\0'
    };

    /**
     * DIMディスクヘッダ
     */
    private static class DimDskHeader {
        /** 1 byte */
        byte type;
        /** 0xaa bytes (170) */
        byte[] tracks = new byte[0xaa];
        /** 15 bytes */
        byte[] ident  = new byte[15];
        /** 4 bytes */
        byte[] date   = new byte[4];
        /** 4 bytes */
        byte[] time   = new byte[4];
        /** 0x3d bytes (61) */
        byte[] comments = new byte[0x3d];
        /** 1 byte */
        byte overtrack;

        /** 取得用ヘッダ全体（256バイト）からパース */
        static DimDskHeader parse(byte[] raw) {
            DimDskHeader h = new DimDskHeader();
            int pos = 0;
            h.type = raw[pos++];
            System.arraycopy(raw, pos, h.tracks, 0, h.tracks.length);
            pos += h.tracks.length;
            System.arraycopy(raw, pos, h.ident, 0, h.ident.length);
            pos += h.ident.length;
            System.arraycopy(raw, pos, h.date, 0, h.date.length);
            pos += h.date.length;
            System.arraycopy(raw, pos, h.time, 0, h.time.length);
            pos += h.time.length;
            System.arraycopy(raw, pos, h.comments, 0, h.comments.length);
            pos += h.comments.length;
            h.overtrack = raw[pos];
            return h;
        }
    }

    /*  ------------------  コンストラクタ / デストラクタ  ------------------ */
    public DiskDIMParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    /*  ------------------  ディスクデータの解析  ------------------ */

    /**
     * DIMファイルの解析
     *
     * @param  istream  入力ディスクイメージ
     * @param  disk_number  ディスク番号
     * @param  disk_param   ディスクパラメータ
     * @return  オフセット（最後のデータ位置）
     */
    @Override
    public int parseDisk(InputStream istream, int disk_number, DiskParam disk_param) {
        try {
            byte[] data = readAllBytes(istream);

            if (data.length < DIM_HEADER_SIZE) {
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return 0;
            }

            DimDskHeader header = DimDskHeader.parse(Arrays.copyOfRange(data, 0, DIM_HEADER_SIZE));

            /* data starts at 0x100 (256) */
            final int DATA_START = 0x100;

            DiskImageDisk disk = file.newImageDisk(disk_number);

            /* パラメータの計算値がディスクサイズの２倍なら表面にのみデータをセット */
            int dummySide = -1;
            if ((int) data.length * 2 <= disk_param.calcDiskSize()) {
                dummySide = disk_param.getSideNumberBaseOnDisk() + 1;
            }

            int offset = disk.getOffsetStart();      /* ここからデータを書き込む */
            int offsetPos = 0;

            int trackNum = disk_param.getTrackNumberBaseOnDisk();
            int tracksPerSide = disk_param.getTracksPerSide() + trackNum;
            int sideNumSt = disk_param.getSideNumberBaseOnDisk();
            int sideNumEd = disk_param.getSidesPerDisk() + sideNumSt;

            for (; trackNum < tracksPerSide && result.getValid() >= 0; trackNum++) {
                for (int sideNum = sideNumSt; sideNum < sideNumEd && result.getValid() >= 0; sideNum++) {
                    boolean isDummyTrack = (sideNum == dummySide);
                    if (header.tracks[offsetPos] == 0) {
                        /* トラック情報がないのでダミーのトラックを作成 */
                        isDummyTrack = true;
                    }
                    offset += parseTrack(data, offsetPos, offset, disk_number,
                            disk_param, trackNum, sideNum, isDummyTrack, disk);
                    offsetPos++;
                }
            }
            disk.setSize(offset);

            if (result.getValid() >= 0) {
                DiskParam param = disk.calcMajorNumber();
                if (param != null) {
                    disk.setDensity(param.getParamDensity());
                }
                file.add(disk, modFlags);
            } else {
                disk = null;   /* delete */
            }

            return offset;
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return 0;
        }
    }

    /*  ------------------  DIMファイルの解析  ------------------ */
    @Override
    public int parse(InputStream istream, DiskParam disk_param) {
        if (disk_param == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        try {
            byte[] data = readAllBytes(istream);
            parseDisk(new ByteArrayInputStream(data), 0, disk_param);
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
        }

        return result.getValid();
    }

    /*  ------------------  チェック  ------------------ */
    @Override
    public int check(InputStream istream, List<DiskTypeHint> disk_hints,
                     DiskParam disk_param, List<DiskParam> disk_params,
                     DiskParam manual_param) {
        try {
            byte[] data = readAllBytes(istream);

            if (data.length < DIM_HEADER_SIZE) {
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }

            DimDskHeader header = DimDskHeader.parse(Arrays.copyOfRange(data, 0, DIM_HEADER_SIZE));

            /* ヘッダ文字列チェック (ident 15 バイト) */
            if (!Arrays.equals(header.ident, DISK_DIM_HEADER)) {
                return -1;   /* not disk */
            }

            int streamSize = data.length - DIM_HEADER_SIZE;
            int sidesPerDisk = 2;
            int sectorSize = 1024;
            int sectorsPerTrack = 8;

            /* トラック数を数える */
            int maxTracks = 0;
            for (int n = 0; n < header.tracks.length; n++) {
                if (header.tracks[n] != 0) {
                    maxTracks = n + 1;
                }
            }
            if (maxTracks == 0) maxTracks = 144;

            /* セクタ数決定 */
            int secsPerTrkMod = (streamSize / sectorSize) % maxTracks;
            if (secsPerTrkMod == 0) {
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

            /* ディスクテンプレートから探す（簡易） */
            if (disk_hints != null) {
                for (int retry = 0; retry < 2 && disk_params.size() == 0; retry++) {
                    for (int i = 0; i < disk_hints.size(); i++) {
                        DiskTypeHint hint = disk_hints.get(i);
                        if (hint.getKind() != header.type) continue;
                        DiskParam param = gDiskTemplates.find(hint.getHint());
                        if (param != null &&
                            (streamSize == param.calcDiskSize() ||
                             tracksPerSide == param.getTracksPerSide() ||
                             retry > 0)) {
                            disk_params.add(param);
                        }
                    }
                }
            }

            /* その他に同じパラメータの候補を追加 */
            gDiskTemplates.find(sidesPerDisk, tracksPerSide, sectorsPerTrack,
                    sectorSize, disk_params, disk_params.size() > 0);

            if (disk_params.size() == 0) {
                manual_param.setDiskParam(
                        sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                        0, 1, new DiskParam().getSingles(),
                        new DiskParam().getParticularTracks());
                return 1;
            }

            return 0;

        } catch (IOException e) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
    }

    /*  ------------------  ヘルパー  ------------------ */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    /*  ------------------  ParseTrack（ダミー実装）  ------------------ */
    /**
     * 1トラックの解析（簡易）
     */
    protected int parseTrack(byte[] data, int offsetPos, int offset, int diskNumber,
                             DiskParam diskParam, int trackNum, int sideNum,
                             boolean isDummyTrack, DiskImageDisk disk) {
        /* ダミー実装：各トラック 512 バイト分書き込むと仮定 */
        return offset + 512;
    }
}
