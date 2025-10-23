/*
 * Author: Sasaji (original author)
 */

package l3diskex.basicfmt;

import java.util.Arrays;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * FAT handling
 */
public class DiskBasicTypeFATBase extends DiskBasicType {

    /**
     * constructor
     */
    public DiskBasicTypeFATBase(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /**
     * 次の空きFAT位置を返す
     *
     * @return INVALID_GROUP_NUMBER: 空きなし
     */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        int new_num = INVALID_GROUP_NUMBER;

        // グループが連続するように検索
        int group_start = curr_group;
        int group_end = basic.getFatEndGroup();
        boolean found = false;

        for (int i = 0; i < 2; i++) {
            for (int g = group_start; g <= group_end; g++) {
                int gnum = getGroupNumber(g);
                //logger.log(Level.DEBUG, "DiskBasicTypeFATBase::GetNextEmptyGroupNumber: g:%d gnum:%d", g, gnum);
                if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                    new_num = g;
                    found = true;
                    break;
                }
            }
            if (found) break;
            // ないときは最初からさがす
            group_start = 2;
        }
        return new_num;
    }

    /**
     * FATエリアの重複チェック
     *
     * @param is_formatting フォーマット中か
     * @param start_group   重複チェックを開始するグループ番号
     * @param max_group     重複チェックを行う最大グループ番号
     * @return 1.0 正常, 0.0-1.0 警告あり, <0.0 エラーあり
     */
    public double checkFatDuplicated(boolean is_formatting, int start_group, int max_group) {
        int end = Math.min(basic.getFatEndGroup(), max_group);
        int[] tbl = new int[end + 1];
        Arrays.fill(tbl, 0);

        // 同じグループ番号が重複しているか
        for (int pos = 0; pos <= end; pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum <= end) {
                tbl[gnum]++;
            }
        }
        // 同じグループ番号が重複している場合エラー
        double valid_ratio = 1.0;
        for (int pos = start_group; pos <= end; pos++) {
            if (tbl[pos] > 4) {
                valid_ratio = -1.0;
                break;
            }
        }
        return valid_ratio;
    }

    /**
     * 管理エリアのトラック番号からグループ番号を計算
     */
    @Override
    public int calcManagedStartGroup() {
        managedStartGroup = 0;
        return managedStartGroup;
    }

    /**
     * 使用可能なディスクサイズを得る (MS-DOS用)
     */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.getFatEndGroup() - 1;
        disk_size[0] = group_size[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * 残りディスクサイズを計算 (MS-DOS用)
     *
     * @param wrote       書き込んだ後か
     * @param start_group 開始グループ番号
     * @param used_group  使用中グループ番号
     */
    public void calcDiskFreeSizeBase(boolean wrote, int start_group, int used_group) {
        fatAvailability.clear();

        // システム領域
        for (int pos = 0; pos < start_group; pos++) {
            fatAvailability.add(FAT_AVAIL_SYSTEM.getValue(), 0, 0);
        }

        // クラスタは2から始まる(MS-DOS)
        for (int pos = start_group; pos <= basic.getFatEndGroup(); pos++) {
            int fsize = 0;
            int grps = 0;
            int gnum = getGroupNumber(pos);
            int fsts = FAT_AVAIL_USED.getValue();
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                fsize = basic.getSectorSize() * basic.getSectorsPerGroup();
                grps = 1;
                fsts = FAT_AVAIL_FREE.getValue();
            } else if (gnum >= used_group) {          // 0xff8 0xfff8 0xfffffff8
                fsts = FAT_AVAIL_USED_LAST.getValue();
            }
            fatAvailability.add(fsts, fsize, grps);
            //logger.log(Level.DEBUG, "DiskBasicTypeFATBase::CalcDiskFreeSizeBase: pos:%d gnum:%d size:%d grps:%d", pos, gnum, fsize, grps);
        }
    }

    /**
     * グループ番号から開始セクタ番号を得る
     */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        if (group_num < 2) return -1;
        return (group_num - 2) * basic.diskBasicParam.getSectorsPerGroup();
    }

    /**
     * グループ番号から最終セクタ番号を得る
     */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        int sector_end = sector_start + basic.diskBasicParam.getSectorsPerGroup() - 1;
        return sector_end;
    }

    /**
     * データ領域の開始セクタを計算
     */
    @Override
    public int calcDataStartSectorPos() {
        return basic.diskBasicParam.getDirEndSector();     // ディレクトリの次が0始まりで計算する
    }

    /**
     * セクタデータを指定コードで埋める
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        int sector_pos = basic.calcSectorPosFromNumForGroup(track.getTrackNumber(), track.getSideNumber(), sector.getSectorNumber(), 0, 1);
        if (sector_pos < 0) {
            // ファイル管理エリアの場合
            sector.fill(basic.diskBasicParam.getFillCodeOnFAT());
        } else {
            // ユーザーエリア
            sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
        }
    }

    /**
     * グループ確保時に最後のグループ番号を計算する
     */
    @Override
    public int calcLastGroupNumber(int group_num, int[] size_remain) {
        return (group_num != INVALID_GROUP_NUMBER) ? basic.diskBasicParam.getGroupFinalCode() : group_num;
    }
}
