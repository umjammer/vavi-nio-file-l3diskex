/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;


/**
 * MZ Base の処理を実装するクラス。
 */
public class DiskBasicTypeMZBase<T extends DirectoryT> extends DiskBasicType<T> {

    // データ開始グループ番号（例：0 から）
    protected int dataStartGroup = 0;
    protected DiskBasicAvailability fatAvailability = new DiskBasicAvailability();

    /* 本クラスの実際のコンストラクタ */
    public DiskBasicTypeMZBase(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super(basic, fat, dir);
    }

    /** FAT 位置をセット */
    @Override
    public void setGroupNumber(int num, int val) {
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
        fatBuf.bit(pos[0], mask[0], val != 0, basic.isDataInverted());
    }

    /** FAT オフセットを返す */
    @Override
    public int getGroupNumber(int num) {
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return INVALID_GROUP_NUMBER;
        }
        return num;
    }

    /** 使用しているグループ番号か */
    @Override
    public boolean isUsedGroupNumber(int num) {
        if (num > basic.getFatEndGroup()) {
            return false;
        }

        int[] pos = {num - dataStartGroup};
        if (pos[0] < 0) {
            /* システムエリアは使用済み */
            return true;
        }

        int[] mask = {0};
        calcUsedGroupPos(num, pos, mask);

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return true;
        }
        return fatBuf.BitTest(pos[0], (byte) mask[0], basic.isDataInverted());
    }

    /** 使用しているグループの位置を得る */
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3) + 6;
    }

    /** 空き位置を返す */
    @Override
    public int getEmptyGroupNumber() {
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return INVALID_GROUP_NUMBER;
        }

        for (int gnum = 0; gnum <= basic.getFatEndGroup(); gnum++) {
            if (gnum >= dataStartGroup && !isUsedGroupNumber(gnum)) {
                return gnum;
            }
        }
        return INVALID_GROUP_NUMBER;
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
    public void calcDiskFreeSize(boolean wrote) {
        int used = 0;
        int grps = 0;

        fatAvailability.empty();

        for (int gnum = 0; gnum <= basic.getFatEndGroup(); gnum++) {
            if (isUsedGroupNumber(gnum)) {
                used++;
            } else if (gnum >= dataStartGroup) {
                grps++;
            }
        }

        fatAvailability.Add(FAT_AVAIL_FREE.getValue(), 0, 0);
        fatAvailability.setFreeSize(0);
        fatAvailability.SetFreeGroups(0);
    }

    /** 未使用が連続している位置をさがす */
    public int findContinuousArea(int groupSize, int[] groupStart) {
        int cnt = 0;
        for (int gnum = getGroupNumber(0); gnum <= basic.getFatEndGroup() && cnt < groupSize; gnum++) {
            if (!isUsedGroupNumber(gnum)) {
                if (cnt == 0) {
                    groupStart[0] = gnum;
                }
                cnt++;
            } else {
                cnt = 0;
            }
        }
        return cnt;
    }

    /** グループを確保して使用中にする */
    public int allocateGroupsSub(DiskBasicDirItem<T> item, int groupStart, int remain,
                                 int secSize, DiskBasicGroups groupItems,
                                 int[] fileSize, int[] groups) {
        int rc = 0;
        int groupNum = groupStart;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            /* すでに使用しているかどうか */
            boolean usedGroup = isUsedGroupNumber(groupNum);
            if (!usedGroup) {
                /* 使用済みにする */
                basic.getNumsFromGroup(groupNum, 0, secSize, remain, groupItems);
                setGroupNumber(groupNum, 1);
                fileSize[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
                groups[0]++;

                remain -= (secSize * basic.getSectorsPerGroup());
            }
            /* 次のグループへ */
            groupNum++;
            limit--;
        }

        if (groupNum > basic.getFatEndGroup()) {
            /* ファイルがオーバーフローしている */
            rc = -2;
        } else if (limit < 0) {
            /* 無限ループの疑い */
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
        sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFormat()));
    }

    /** 指定したグループ番号の FAT 領域を削除する */
    @Override
    public void deleteGroupNumber(int groupNum) {
        /* FAT を未使用にする */
        setGroupNumber(groupNum, 0);
    }
}
