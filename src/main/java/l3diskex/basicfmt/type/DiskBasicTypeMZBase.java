/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * MZ Baseの処理
 */
public abstract class DiskBasicTypeMZBase<T extends Directory> extends DiskBasicType<T> {

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super.init(basic, fat, dir);
    }

    /** FAT 位置をセット */
    @Override
    public void setGroupNumber(int num, int val) throws IOException {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        int[] pos = {num - dataStartGroup};
        if (pos[0] < 0) {
            return;
        }

        int[] mask = {0};
        calcUsedGroupPos(num, pos, mask);

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return;
        }
        // FATには未使用使用テーブルがある
        fatBuf.bit(pos[0], mask[0], val != 0, basic.isDataInverted());
    }

    /** FAT オフセットを返す */
    @Override
    public int getGroupNumber(int num) throws IOException {
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return INVALID_GROUP_NUMBER;
        }
        return num;
    }

    /** 使用しているグループ番号か */
    @Override
    public boolean isUsedGroupNumber(int num) {
        boolean exist = false;

        if (num > basic.getFatEndGroup()) {
            return false;
        }

        int[] pos = {num - dataStartGroup};
        if (pos[0] < 0) {
            // システムエリアは使用済み
            return true;
        }

        int[] mask = {0};
        calcUsedGroupPos(num, pos, mask);

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return true;
        }
        // FATには未使用使用テーブルがある
        exist = fatBuf.bitTest(pos[0], (byte) mask[0], basic.isDataInverted());
        return exist;
    }

    /** 使用しているグループの位置を得る */
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3) + 6;
    }

    /** 空き位置を返す */
    @Override
    public int getEmptyGroupNumber() throws IOException {
        int new_num = INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return new_num;
        }
        // 空き位置をさがす
        for (int gnum = 0; gnum <= basic.getFatEndGroup(); gnum++) {
            if (gnum >= dataStartGroup && !isUsedGroupNumber(gnum)) {
                new_num = gnum;
                break;
            }
        }
        return new_num;
    }

    /** 次の空きFAT位置を返す（未使用） */
    @Override
    public int getNextEmptyGroupNumber(int currGroup) {
        return INVALID_GROUP_NUMBER;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getSectorsPerGroup() * (basic.getFatEndGroup() + 1 - dataStartGroup);
        diskSize[0] = basic.getSectorSize() * groupSize[0];
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        int used = 0;
        fatAvailability.empty();

        // 使用済みかチェック
        int grps = 0;
        FatAvailability fatStatus = FAT_AVAIL_FREE;
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup(); groupNum++) {
            if (groupNum < dataStartGroup) {
                used++;
                fatStatus = FAT_AVAIL_SYSTEM;
            } else if (isUsedGroupNumber(groupNum)) {
                used++;
                fatStatus = FAT_AVAIL_FREE;
            } else if (groupNum >= dataStartGroup) {
                grps++;
                fatStatus = FAT_AVAIL_USED;
            }
            fatAvailability.add(fatStatus, 0, 0);
        }

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<T>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (DiskBasicDirItem<T> item : items) {
                if (item == null || !item.isUsed()) continue;

                // グループ番号のマップを調べる
                int groupCount = item.getGroupCount();
                if (groupCount > 0) {
                    DiskBasicGroupItem groupItem = item.getGroup(groupCount - 1);
                    int gnum = groupItem.group;
                    if (gnum <= basic.getFatEndGroup()) {
                        fatAvailability.set(gnum, FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        int fSize = grps * basic.getSectorsPerGroup() * basic.getSectorSize();

        fatAvailability.setFreeSize(0);
        fatAvailability.setFreeGroups(0);
    }

    /** 未使用が連続している位置をさがす */
    public int findContinuousArea(int groupSize, int[] groupStart) throws IOException {
        int count = 0;
        for (int groupNum = getGroupNumber(0); groupNum <= basic.getFatEndGroup() && count < groupSize; groupNum++) {
            if (!isUsedGroupNumber(groupNum)) {
                if (count == 0) {
                    groupStart[0] = groupNum;
                }
                count++;
            } else {
                count = 0;
            }
        }
        return count;
    }

    /** グループを確保して使用中にする */
    public int allocateGroupsSub(DiskBasicDirItem<T> item, int groupStart, int remain,
                                 int secSize, DiskBasicGroups groupItems,
                                 int[] fileSize, int[] groups) throws IOException {
        int rc = 0;
        int groupNum = groupStart;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // すでに使用しているかどうか
            boolean usedGroup = isUsedGroupNumber(groupNum);
            if (!usedGroup) {
                // 使用済みにする
                basic.getNumsFromGroup(groupNum, 0, secSize, remain, groupItems);
                setGroupNumber(groupNum, 1);
                fileSize[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
                groups[0]++;

                remain -= (secSize * basic.getSectorsPerGroup());
            }
            // 次のグループへ
            groupNum++;
            limit--;
        }

        if (groupNum > basic.getFatEndGroup()) {
            // ファイルがオーバーフローしている
            rc = -2;
        } else if (limit < 0) {
            // 無限ループ?
            rc = -2;
        }
        return rc;
    }

    /** グループ番号から開始セクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum * basic.getSectorsPerGroup();
    }

    /** グループ番号から最終セクタ番号を得る */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup,
                                     int sectorStart, int sectorSize, int remainSize) {
        return sectorStart + basic.getSectorsPerGroup() - 1;
    }

    /** セクタデータを指定コードで埋める */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.invertUint8(basic.getFillCodeOnFormat()));
    }

    /** 指定したグループ番号の FAT 領域を削除する */
    @Override
    public void deleteGroupNumber(int groupNum) throws IOException {
        /* FAT を未使用にする */
        setGroupNumber(groupNum, 0);
    }
}
