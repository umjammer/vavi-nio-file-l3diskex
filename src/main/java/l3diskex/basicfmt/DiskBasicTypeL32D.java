/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;


public class DiskBasicTypeL32D extends DiskBasicTypeFAT8 {

    /* -- */
    /*  member variables                                                    */
    /* -- */
    private int managed_start_group = 0;                 // equivalent to int

    /* -- */
    /*  constructor                                                        */
    /* -- */
    public DiskBasicTypeL32D(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /* -- */
    /*  access to FAT area                                                 */
    /* -- */

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param is_formatting フォーマット中か
     * @return 1.0 正常, 0.0‑1.0 警告あり, <0.0 エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (basic.diskBasicParam.getFatEndGroup() == 0) {
            int end_group = basic.diskBasicParam.getTracksPerSideOnBasic()
                    * basic.diskBasicParam.getSidesPerDiskOnBasic()
                    * basic.diskBasicParam.getSectorsPerTrackOnBasic();

            /* トラック０と管理トラック分を引く */
            end_group -= basic.diskBasicParam.getSidesPerDiskOnBasic()
                    * basic.diskBasicParam.getSectorsPerTrackOnBasic()
                    * (basic.diskBasicParam.getManagedTrackNumber() == 0 ? 1 : 2);

            end_group /= basic.diskBasicParam.getSectorsPerGroup();
            basic.diskBasicParam.setFatEndGroup(end_group - 1);
        }
        return 1.0;
    }

    /**
     * FATエリアをチェック
     *
     * @param is_formatting フォーマット中か
     * @return 1.0 正常, 0.0‑1.0 警告あり, <0.0 エラーあり
     */
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = super.checkFat(is_formatting);
        if (valid_ratio >= 0.0) {
            /* FAT先頭エリアのチェック */
            DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
            if (sector == null) {
                valid_ratio = -1.0;
            } else if (!(sector.get(0) == 0 || sector.get(0) == (byte) 0xff)) {
                valid_ratio = -1.0;
            }
        }
        return valid_ratio;
    }

    /**
     * 空きFAT位置を返す
     *
     * @return 0xffffffff : 空きなし
     */
    @Override
    public int getEmptyGroupNumber() {
        int new_num = INVALID_GROUP_NUMBER;

        /* トラック当たりのグループ数 */
        int grps_per_trk = basic.diskBasicParam.getSectorsPerTrackOnBasic() * 2 / basic.diskBasicParam.getSectorsPerGroup();

        /* 最大グループ数 */
        int max_group = basic.diskBasicParam.getFatEndGroup() + 1 - managed_start_group;
        if (max_group < managed_start_group) {
            max_group = managed_start_group;
        }
        max_group = max_group * 2 - 1;

        for (int i = 0; i <= max_group; i++) {
            int i2 = i / grps_per_trk;
            int i4 = i / grps_per_trk / 2;
            int num;
            if ((i2 & 1) == 0) {
                num = managed_start_group - ((i4 + 1) * grps_per_trk) + (i % grps_per_trk);
            } else {
                num = managed_start_group + (i4 * grps_per_trk) + (i % grps_per_trk);
            }
            if (basic.diskBasicParam.getFatEndGroup() < num) {
                continue;
            }
            int gnum = getGroupNumber(num);
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                new_num = num;
                break;
            }
        }
        return new_num;
    }

    /* -- */
    /*  check / assign FAT area                                            */
    /* -- */

    /**
     * 管理エリアのトラック番号からグループ番号を計算
     *
     * @return 計算されたグループ番号
     */
    @Override
    public int calcManagedStartGroup() {
        int trk = basic.diskBasicParam.getManagedTrackNumber();
        int sid = basic.diskBasicParam.getFatSideNumber();
        int sides = basic.diskBasicParam.getSidesPerDiskOnBasic();
        int secs_per_grp = basic.diskBasicParam.getSectorsPerGroup();
        int secs_per_trk = basic.diskBasicParam.getSectorsPerTrackOnBasic();

        trk--;                                            // トラック1から開始するので-1する
        managed_start_group = (trk * sides + sid) * secs_per_trk / secs_per_grp;
        return managed_start_group;
    }

    /* -- */
    /*  file chain                                                         */
    /* -- */

    /**
     * スキップするトラック番号
     *
     * @return 管理トラック番号
     */
    @Override
    public int calcSkippedTrack() {
        return basic.diskBasicParam.getManagedTrackNumber();
    }

    /**
     * データ領域の開始セクタを計算
     *
     * @return 開始セクタ位置
     */
    @Override
    public int calcDataStartSectorPos() {
        return basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic();
    }

    /* -- */
    /*  save / write                                                       */
    /* -- */

    /**
     * グループ確保時に最後のグループ番号を計算する
     *
     * @param group_num   現在のグループ番号
     * @param size_remain 残りのデータサイズ（参照渡しを配列で実装）
     * @return 最後のグループ番号
     */
    @Override
    public int calcLastGroupNumber(int group_num, int[] size_remain) {
        if ((size_remain[0] % basic.getSectorSize()) == 0) {
            /* サイズがセクタ境界になる場合はサイズを+1する。→次のセクタも確保させる */
            size_remain[0]++;
        }
        if (size_remain[0] > (basic.diskBasicParam.getSectorsPerGroup() * basic.getSectorSize())) {
            /* 次のグループが必要 */
            return group_num;
        } else {
            /* ここが最終グループ */
            return super.calcLastGroupNumber(group_num, size_remain);
        }
    }
}
