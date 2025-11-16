/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemL32D.DirectoryL32d;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_L3S1_2D;


/**
 * LEVEL-3 BASIC 2D(両面・倍密度)の処理
 */
public class DiskBasicTypeL32D extends DiskBasicTypeFAT8<DirectoryL32d> {

    @Override
    public boolean isSupported(DiskBasicFormatType typeNumber) {
        return typeNumber == FORMAT_TYPE_L3S1_2D;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryL32d> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0~1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            // トラック０と管理トラック分を引く
            endGroup -= basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() * (basic.getManagedTrackNumber() == 0 ? 1 : 2);
            endGroup /= basic.getSectorsPerGroup();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /**
     * FATエリアをチェック
     *
     * @param isFormatting フォーマット中か
     * @return 1.0 正常, 0.0‑1.0 警告あり, <0.0 エラーあり
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // FAT先頭エリアのチェック
            DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
            if (sector == null) {
                validRatio = -1.0;
            } else if (!(sector.get(0) == 0 || sector.get(0) == (byte) 0xff)) {
                validRatio = -1.0;
            }
        }
        return validRatio;
    }

    /**
     * 空きFAT位置を返す
     *
     * @return 0xff: 空きなし
     */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;
        // 管理エリアに近い位置から検索

        // トラック当たりのグループ数
        int groupsPerTrack = basic.getSectorsPerTrackOnBasic() * 2 / basic.getSectorsPerGroup();

        // 最大グループ数
        int maxGroup = basic.getFatEndGroup() + 1 - managedStartGroup;
        if (maxGroup < managedStartGroup) maxGroup = managedStartGroup;
        maxGroup = maxGroup * 2 - 1;

        for (int i = 0; i <= maxGroup; i++) {
            int i2 = i / groupsPerTrack;
            int i4 = i / groupsPerTrack / 2;
            int num;
            if ((i2 & 1) == 0) {
                num = managedStartGroup - ((i4 + 1) * groupsPerTrack) + (i % groupsPerTrack);
            } else {
                num = managedStartGroup + (i4 * groupsPerTrack) + (i % groupsPerTrack);
            }
            if (basic.getFatEndGroup() < num) {
                continue;
            }
            int groupNum = getGroupNumber(num);
            if (groupNum == basic.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    /**
     * 管理エリアのトラック番号からグループ番号を計算
     *
     * @return 計算されたグループ番号
     */
    @Override
    public int calcManagedStartGroup() {
        int track = basic.getManagedTrackNumber();
        int side = basic.getFatSideNumber();
        int sides = basic.getSidesPerDiskOnBasic();
        int sectorsPerGroup = basic.getSectorsPerGroup();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        // トラック1から開始するので-1する
        track--;
        managedStartGroup = (track * sides + side) * sectorsPerTrack / sectorsPerGroup;
        return managedStartGroup;
    }

    /**
     * スキップするトラック番号
     *
     * @return 管理トラック番号
     */
    @Override
    public int calcSkippedTrack() {
        return basic.getManagedTrackNumber();
    }

    /**
     * データ領域の開始セクタを計算
     *
     * @return 開始セクタ位置
     */
    @Override
    public int calcDataStartSectorPos() {
        // トラック0を除く
        return basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
    }

    /**
     * グループ確保時に最後のグループ番号を計算する
     *
     * @param groupNum   現在のグループ番号
     * @param sizeRemain 残りのデータサイズ（参照渡しを配列で実装）
     * @return 最後のグループ番号
     */
    @Override
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        if ((sizeRemain[0] % basic.getSectorSize()) == 0) {
            // サイズがセクタ境界になる場合はサイズを+1する。→次のセクタも確保させる
            sizeRemain[0]++;
        }
        if (sizeRemain[0] > (basic.getSectorsPerGroup() * basic.getSectorSize())) {
            // 次のグループが必要
            return groupNum;
        } else {
            // ここが最終グループ
            return super.calcLastGroupNumber(groupNum, sizeRemain);
        }
    }
}
