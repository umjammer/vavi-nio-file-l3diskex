/*
 * Author: Sasaji (original author)
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * FAT handling
 */
public abstract class DiskBasicTypeFATBase<T extends Directory> extends DiskBasicType<T> {

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * 次の空きFAT位置を返す
     *
     * @return INVALID_GROUP_NUMBER: 空きなし
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) throws IOException {
        int newNum = INVALID_GROUP_NUMBER;

        // グループが連続するように検索
        int groupStart = currentGroup;
        int groupEnd = basic.getFatEndGroup();
        boolean found = false;

        for (int i = 0; i < 2; i++) {
            for (int g = groupStart; g <= groupEnd; g++) {
                int groupNum = getGroupNumber(g);
                //logger.log(Level.TRACE, "DiskBasicTypeFATBase::GetNextEmptyGroupNumber: g: %d groupNum: %d".formatted(g, groupNum));
                if (groupNum == basic.getGroupUnusedCode()) {
                    newNum = g;
                    found = true;
                    break;
                }
            }
            if (found) break;
            // ないときは最初からさがす
            groupStart = 2;
        }
        return newNum;
    }

    /**
     * FATエリアの重複チェック
     *
     * @param isFormatting フォーマット中か
     * @param startGroup   重複チェックを開始するグループ番号
     * @param maxGroup     重複チェックを行う最大グループ番号
     * @return 1.0: 正常, 0.0-1.0: 警告あり, <0.0: エラーあり
     */
    public double checkFatDuplicated(boolean isFormatting, int startGroup, int maxGroup) throws IOException {
        int end = Math.min(basic.getFatEndGroup(), maxGroup);
        int[] table = new int[end + 1];
        Arrays.fill(table, 0);

        // 同じグループ番号が重複しているか
        for (int pos = 0; pos <= end; pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum <= end) {
                table[groupNum]++;
            }
        }
        // 同じグループ番号が重複している場合エラー
        double validRatio = 1.0;
        for (int pos = startGroup; pos <= end; pos++) {
            if (table[pos] > 4) {
                validRatio = -1.0;
                break;
            }
        }

        return validRatio;
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
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() - 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * 残りディスクサイズを計算 (MS-DOS用)
     *
     * @param wrote      書き込んだ後か
     * @param startGroup 開始グループ番号
     * @param usedGroup  使用中グループ番号
     */
    public void calcDiskFreeSizeBase(boolean wrote, int startGroup, int usedGroup) throws IOException {
        fatAvailability.clear();

        // システム領域
        for (int pos = 0; pos < startGroup; pos++) {
            fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
        }

        // クラスタは2から始まる(MS-DOS)
        for (int pos = startGroup; pos <= basic.getFatEndGroup(); pos++) {
            int fSize = 0;
            int groups = 0;
            int groupNum = getGroupNumber(pos);
            FatAvailability fsts = FAT_AVAIL_USED;
            if (groupNum == basic.getGroupUnusedCode()) {
                fSize = basic.getSectorSize() * basic.getSectorsPerGroup();
                groups = 1;
                fsts = FAT_AVAIL_FREE;
            } else if (groupNum >= usedGroup) { // 0xff8, 0xfff8, 0xfff_ffff8
                fsts = FAT_AVAIL_USED_LAST;
            }
            fatAvailability.add(fsts, fSize, groups);
            //logger.log(Level.TRACE, "DiskBasicTypeFATBase::CalcDiskFreeSizeBase: pos:%d groupNum:%d size: %d groups: %d".formatted(pos, groupNum, fSize, groups));
        }
    }

    /**
     * グループ番号から開始セクタ番号を得る
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        // 2から始まる (0,1は予約)
        if (groupNum < 2) {
            return -1;
        }
        return (groupNum - 2) * basic.getSectorsPerGroup();
    }

    /**
     * グループ番号から最終セクタ番号を得る
     */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int sectorEnd = sectorStart + basic.getSectorsPerGroup() - 1;
        return sectorEnd;
    }

    /**
     * データ領域の開始セクタを計算
     */
    @Override
    public int calcDataStartSectorPos() {
        return basic.getDirEndSector(); // ディレクトリの次が0始まりで計算する
    }

    /**
     * セクタデータを指定コードで埋める
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        int sectorPos = basic.calcSectorPosFromNumForGroup(track.getTrackNumber(), track.getSideNumber(), sector.getSectorNumber(), 0, 1);
        if (sectorPos < 0) {
            // ファイル管理エリアの場合
            sector.fill(basic.getFillCodeOnFAT());
        } else {
            // ユーザーエリア
            sector.fill(basic.getFillCodeOnFormat());
        }
    }

    /**
     * グループ確保時に最後のグループ番号を計算する
     */
    @Override
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        return (groupNum != INVALID_GROUP_NUMBER) ? basic.getGroupFinalCode() : groupNum;
    }
}
