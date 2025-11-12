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

    /** ディスク名 */
    private final String diskName;
    /** パラメータ */
    private final DiskParam param;
    /** 書込保護フラグ */
    private final boolean writeProtect;
    /** ディスクイメージファイル */
    private final DiskImageFile file;
    /** 実行結果 */
    private final DiskResult result;

    //
    //
    //
    public DiskImageCreator(String diskName, DiskParam param, boolean writeProtect,
                            DiskImageFile file, DiskResult result) {
        this.diskName = diskName;
        this.param = param;
        this.writeProtect = writeProtect;
        this.file = file;
        this.result = result;
    }

    /**
     * セクタデータの作成
     * @param trackNumber     トラック番号
     * @param sideNumber      サイド番号
     * @param sectorNumber    セクタ番号
     * @param sectorSize      セクタサイズ
     * @param sectorsPerTrack セクタ数
     * @param track           トラック
     * @return 作成したセクタのサイズ（ヘッダ含む）
     */
    private int createSector(int trackNumber, int sideNumber,
                             int sectorNumber, int sectorSize,
                             int sectorsPerTrack, DiskImageTrack track) {

        // 特殊なセクタにするか
        int[][] sectorId = new int[1][];
        int[] sectorSize_ = {sectorSize};
        if (param.findParticularSector(trackNumber, sideNumber, sectorNumber, sectorSize_, sectorId)) {
            if ((sectorId[0][1] & TrackParam.ID_IS_VALID) != 0) {
                sideNumber = sectorId[0][1] & ~TrackParam.ID_IS_VALID;
            }
            if ((sectorId[0][2] & TrackParam.ID_IS_VALID) != 0) {
                sectorNumber = sectorId[0][2] & ~TrackParam.ID_IS_VALID;
            }
        }

        // 単密度にするか
        boolean singleDensity = param.findSingleDensity(trackNumber, sideNumber, sectorNumber, sectorSize_[0]);

        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize_[0], sectorsPerTrack, singleDensity, 0);
        track.add(sector);

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    /**
     * トラックデータの作成
     * @param trackNumber トラック番号
     * @param sideNumber  サイド番号
     * @param offsetPos   オフセット番号
     * @param offset      トラックのあるオフセット位置
     * @param disk        ディスク
     * @return 作成したトラックサイズ
     */
    public int createTrack(int trackNumber, int sideNumber, int offsetPos, int offset, DiskImageDisk disk) {

        // トラック作成
        DiskImageTrack track = disk.newImageTrack(trackNumber, sideNumber, offsetPos, param.getInterleave());

        int[] sectorMax = {param.getSectorsPerTrack()};
        int[] sectorSize = {param.getSectorSize()};

        // 特殊なトラックにするか
        param.findParticularTrack(trackNumber, sideNumber, sectorMax, sectorSize);
        // トラック全体が単密度の場合セクタ数とサイズを得る
        param.findSingleDensity(trackNumber, sideNumber, sectorMax, sectorSize);

        // interleave の並び順を計算
        List<Integer> sectorNums = new ArrayList<>();
        if (!DiskImageTrack.calcSectorNumbersForInterleave(param.getInterleave(), sectorMax[0], sectorNums, param.getSectorNumberBaseOnDisk())) {
            result.setError(DiskResult.ERR_INTERLEAVE);
        }

        // create sectors
        int trackSize = 0;
        for (int sectorPos = 0; sectorPos < sectorMax[0] && result.getValid() >= 0; sectorPos++) {
            int sectorOffset = 0;
            if (param.isReversible()) {
                // 裏返しできる(AB面あり)場合
                sideNumber = 0;
            }
            if (param.getNumberingSector() == 1) {
                // 連番にする場合
                sectorOffset = sideNumber * sectorMax[0];
            }
            trackSize += createSector(trackNumber, sideNumber, sectorNums.get(sectorPos) + sectorOffset, sectorSize[0], sectorMax[0], track);
        }

        if (result.getValid() >= 0) {
            // トラックを追加
            track.setSize(trackSize);
            disk.add(track);
        }

        return trackSize;
    }

    /**
     * ディスクデータの作成
     * @param diskNumber ディスク番号
     * @param modFlags   新規 or 追加？(DiskImageFile#MODIFY_NONE/MODIFY_ADD)
     * @return 作成したディスクサイズ
     */
    private int createDisk(int diskNumber, short modFlags) {

        DiskImageDisk disk = file.newImageDisk(diskNumber, param, diskName, writeProtect);

        // create tracks
        int createSize = 0;
        int trackNum = param.getTrackNumberBaseOnDisk();
        int sideNum = param.getSideNumberBaseOnDisk();
        int tracksPerSide = param.getTracksPerSide() + trackNum;
        int sidesPerDisk = param.getSidesPerDisk() + sideNum;

        for (int pos = 0; result.getValid() >= 0; pos++) {
            disk.setOffsetWithoutHeader(pos, createSize);
            disk.setMaxTrackNumber(pos);

            createSize += createTrack(trackNum, sideNum, pos, disk.getOffset(pos), disk);

            sideNum++;
            if (sideNum >= sidesPerDisk) {
                trackNum++;
                sideNum = param.getSideNumberBaseOnDisk();
            }
            if (trackNum >= tracksPerSide) {
                break;
            }
        }

        if (result.getValid() >= 0) {
            // ディスクを追加
            if (param.getBasicTypes().isEmpty()) {
                // パラメータが手動設定のときはそれらしいテンプレートをさがす
                disk.calcMajorNumber();
            } else {
                // テンプレートから設定
                disk.setDiskParam(param);
                // DISKBASICの準備
                disk.allocDiskBasics();
            }
            disk.setSizeWithoutHeader(createSize);
            file.add(disk, modFlags);
        }

        return createSize;
    }

    /**
     * ディスクイメージの新規作成
     */
    public int create() {
        createDisk(0, DiskImageFile.MODIFY_NONE);
        return result.getValid();
    }

    /**
     * 新規作成して既存のイメージに追加
     */
    public int add() {
        int diskNumber = 0;
        List<DiskImageDisk> disks = file.getDisks();
        if (disks != null) {
            diskNumber = disks.size();
        }

        createDisk(diskNumber, DiskImageFile.MODIFY_ADD);

        return result.getValid();
    }
}
