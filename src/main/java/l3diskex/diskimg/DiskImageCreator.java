/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.util.ArrayList;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam.TrackParam;


/**
 * ディスクイメージの新規作成
 */
public class DiskImageCreator {

    /* ------------------------------------------------------------------ */
    /*  メンバ変数                                                          */
    /* ------------------------------------------------------------------ */
    private final String m_diskname;                 /* ディスク名          */
    private final DiskParam p_param;                 /* パラメータ        */
    private final boolean m_write_protect;           /* 書込保護フラグ    */
    private final DiskImageFile p_file;              /* ディスクイメージファイル */
    private final DiskResult p_result;               /* 実行結果          */

    /* ------------------------------------------------------------------ */
    /*  コンストラクタ / デストラクタ                                       */
    /* ------------------------------------------------------------------ */
    public DiskImageCreator(String diskname, DiskParam param,
                            boolean write_protect,
                            DiskImageFile file, DiskResult result) {
        this.m_diskname = diskname;
        this.p_param = param;
        this.m_write_protect = write_protect;
        this.p_file = file;
        this.p_result = result;
    }

    /* ------------------------------------------------------------------ */
    /*  セクタデータの作成                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * @param trackNumber     トラック番号
     * @param sideNumber      サイド番号
     * @param sectorNumber    セクタ番号
     * @param sectorSize      セクタサイズ
     * @param sectorsPerTrack セクタ数
     * @param track           トラック
     * @return 作成したセクタのサイズ（ヘッダ含む）
     */
    private int createSector(int trackNumber, int sideNumber,
                             int sectorNumber, int[] sectorSize,
                             int sectorsPerTrack, DiskImageTrack track) {

        // 特殊なセクタにするか
        int[][] sectorId = new int[1][];
        if (p_param.findParticularSector(trackNumber, sideNumber,
                sectorNumber, sectorSize, sectorId)) {
            if ((sectorId[0][1] & TrackParam.ID_IS_VALID) != 0) {
                sideNumber = sectorId[0][1] & ~TrackParam.ID_IS_VALID;
            }
            if ((sectorId[0][2] & TrackParam.ID_IS_VALID) != 0) {
                sectorNumber = sectorId[0][2] & ~TrackParam.ID_IS_VALID;
            }
        }

        // 単密度にするか
        boolean singleDensity = p_param.findSingleDensity(
                trackNumber, sideNumber, new int[] {sectorNumber}, sectorSize);

        DiskImageSector sector = track.newImageSector(
                trackNumber, sideNumber, sectorNumber,
                sectorSize[0], sectorsPerTrack, singleDensity, 0);
        track.add(sector);

        return sector.getSize();
    }

    /* ------------------------------------------------------------------ */
    /*  トラックデータの作成                                                */
    /* ------------------------------------------------------------------ */

    /**
     * @param trackNumber トラック番号
     * @param sideNumber  サイド番号
     * @param offsetPos   オフセット番号
     * @param offset      トラックのあるオフセット位置
     * @param disk        ディスク
     * @return 作成したトラックサイズ
     */
    public int createTrack(int trackNumber, int sideNumber,
                           int offsetPos, int offset,
                           DiskImageDisk disk) {

        /* トラック作成 */
        DiskImageTrack track = disk.newImageTrack(
                trackNumber, sideNumber, offsetPos,
                p_param.getInterleave());

        int[] sectorMax = {p_param.getSectorsPerTrack()};
        int[] sectorSize = {p_param.getSectorSize()};

        /* 特殊なトラックにするか */
        p_param.findParticularTrack(trackNumber, sideNumber,
                sectorMax, sectorSize);

        /* トラック全体が単密度の場合セクタ数とサイズを得る */
        p_param.findSingleDensity(trackNumber, sideNumber,
                sectorMax, sectorSize);

        /* interleave の並び順を計算 */
        List<Integer> sectorNums = new ArrayList<>();
        if (!DiskImageTrack.calcSectorNumbersForInterleave(
                p_param.getInterleave(), sectorMax[0],
                sectorNums, p_param.getSectorNumberBaseOnDisk())) {
            p_result.setError(DiskResult.ERR_INTERLEAVE);
        }

        /* create sectors */
        int trackSize = 0;
        for (int sectorPos = 0; sectorPos < sectorMax[0] &&
                p_result.getValid() >= 0; sectorPos++) {

            int sectorOffset = 0;
            if (p_param.isReversible()) {
                /* 裏返しできる(AB面あり)場合 */
                sideNumber = 0;
            }
            if (p_param.getNumberingSector() == 1) {
                /* 連番にする場合 */
                sectorOffset = sideNumber * sectorMax[0];
            }

            trackSize += createSector(trackNumber, sideNumber,
                    sectorNums.get(sectorPos) + sectorOffset,
                    sectorSize, sectorMax[0], track);
        }

        if (p_result.getValid() >= 0) {
            /* トラックを追加 */
            track.setSize(trackSize);
            disk.add(track);
        } else {
            /* 失敗時はオブジェクトを破棄 */
            track = null;
        }

        return trackSize;
    }

    /* ------------------------------------------------------------------ */
    /*  ディスクデータの作成                                                */
    /* ------------------------------------------------------------------ */

    /**
     * @param diskNumber ディスク番号
     * @param modFlags   新規 or 追加？(DiskImageFile::MODIFY_NONE/MODIFY_ADD)
     * @return 作成したディスクサイズ
     */
    private int createDisk(int diskNumber, short modFlags) {

        DiskImageDisk disk = p_file.newImageDisk(
                diskNumber, p_param, m_diskname, m_write_protect);

        /* create tracks */
        int createSize = 0;
        int trackNum = p_param.getTrackNumberBaseOnDisk();
        int sideNum = p_param.getSideNumberBaseOnDisk();
        int tracksPerSide = p_param.getTracksPerSide() + trackNum;
        int sidesPerDisk = p_param.getSidesPerDisk() + sideNum;

        for (int pos = 0; p_result.getValid() >= 0; pos++) {
            disk.setOffsetWithoutHeader(pos, createSize);
            disk.setMaxTrackNumber(pos);

            createSize += createTrack(
                    trackNum, sideNum, pos,
                    disk.getOffset(pos), disk);

            sideNum++;
            if (sideNum >= sidesPerDisk) {
                trackNum++;
                sideNum = p_param.getSideNumberBaseOnDisk();
            }
            if (trackNum >= tracksPerSide) {
                break;
            }
        }

        if (p_result.getValid() >= 0) {
            /* ディスクを追加 */
            if (p_param.getBasicTypes().isEmpty()) {
                /* パラメータが手動設定のときはそれらしいテンプレートをさがす */
                disk.calcMajorNumber();
            } else {
                /* テンプレートから設定 */
                disk.setDiskParam(p_param);
                /* DISKBASICの準備 */
                disk.allocDiskBasics();
            }
            disk.setSizeWithoutHeader(createSize);
            p_file.add(disk, modFlags);
        } else {
            disk = null;
        }

        return createSize;
    }

    /* ------------------------------------------------------------------ */
    /*  ディスクイメージの新規作成                                           */
    /* ------------------------------------------------------------------ */
    public int create() {
        createDisk(0, DiskImageFile.MODIFY_NONE);
        return p_result.getValid();
    }

    /* ------------------------------------------------------------------ */
    /*  新規作成して既存のイメージに追加                                  */
    /* ------------------------------------------------------------------ */
    public int add() {
        int diskNumber = 0;
        List<DiskImageDisk> disks = p_file.getDisks();
        if (disks != null) {
            diskNumber = disks.size();
        }
        createDisk(diskNumber, DiskImageFile.MODIFY_ADD);
        return p_result.getValid();
    }
}
