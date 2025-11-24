package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.DirectorySDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_LEAK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/** */
public class DiskBasicTypeSDOS extends DiskBasicType<DirectorySDos> {

    // 空き開始グループ
    private int emptyGroupNum;

    public static final int FORMAT_TYPE_SDOS = 54;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_SDOS;
    }

    /** Public constructor used by the system. */
    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectorySDos> dir) {
        super.init(basic, fat, dir);

        this.emptyGroupNum = 0;
    }

    /** FAT位置をセット（現在は実装なし） */
    @Override
    public void setGroupNumber(int num, int val) {
    }

    /** FAT位置を返す */
    @Override
    public int getGroupNumber(int num) {
        return INVALID_GROUP_NUMBER;
    }

    /** 空きFAT位置を返す */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;

        if (emptyGroupNum <= basic.getFatEndGroup()) {
            newNum = emptyGroupNum;
        }

        return newNum;
    }

    /** 次の空きFAT位置を返す */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        int newNum = INVALID_GROUP_NUMBER;

        currentGroup++;
        if (currentGroup <= basic.getFatEndGroup()) {
            newNum = currentGroup;
        }

        return newNum;
    }

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = -1.0;

        // 最初のセクタにある文字列で判断
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            byte[] id = basic.getVariousStringParam("IPLCompareString").getBytes();
            if (id.length > 0) {
                if (sector.find(id, id.length) >= 0) {
                    validRatio = 1.0;
                }
            }
        }
        return validRatio;
    }

    /** ディスクから各パラメータを取得＆必要なパラメータを計算 */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        // グループ数
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /** ディレクトリエリアのサイズに達したらアサイン終了するか */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        // サイズに達したら終了
        return (sizeRemain[0] < DirectorySDos.SIZE) ? -2 : 0;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = 0;
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum != basic.getGroupSystemCode()) {
                groupSize[0]++;
            }
        }
        diskSize[0] = groupSize[0] * basic.getSectorSize() / basic.getGroupsPerSector();
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();
//        fatAvailability.setCount(basic.getFatEndGroup() + 1, FAT_AVAIL_FREE);

        int groupNum;
        int maxGroupNum = 0;

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectorySDos>> items = dir.getCurrentItems(null);
        for (DiskBasicDirItem<DirectorySDos> item : items) {
            if (item == null || !item.isUsed()) continue;
            // 開始グループ
            int startGroupNum = item.getStartGroup(0);

            // グループ番号のマップを調べる
            int groupCount = item.getGroupCount();
            for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                DiskBasicGroupItem groupItem = item.getGroup(groupIndex);
                groupNum = groupItem.group;
                fatAvailability.set(groupNum, groupIndex == groupCount - 1 ? FAT_AVAIL_USED_LAST : FAT_AVAIL_USED);
            }

            if (startGroupNum + groupCount > maxGroupNum) {
                maxGroupNum = startGroupNum + groupCount;
            }
        }

        // 空きをチェック
        int groups = 0;
        int groupEnd = basic.getReservedSectors();
        for(int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (pos < groupEnd) {
                // ディレクトリエリアは使用済み
                fatAvailability.set(pos, FAT_AVAIL_SYSTEM);
            } else if (fatAvailability.get(pos) == FAT_AVAIL_FREE) {
                if (pos < maxGroupNum) {
                    // 削除されたエリア
                    fatAvailability.set(pos, FAT_AVAIL_LEAK);
                } else {
                    // 空き
                    groups++;
                }
            }
        }

        emptyGroupNum = maxGroupNum;

        int freeSize = groups * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(freeSize);
        fatAvailability.setFreeGroups(groups);
    }

    /** データサイズ分のグループを確保する */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectorySDos> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) {

        int fileSize = 0;
        int groups = 0;

        int rc = 0;
        int sectorSize = basic.getSectorSize();
        int remain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        int groupNum = getEmptyGroupNumber();
        while(remain > 0 && limit >= 0 && groupNum != INVALID_GROUP_NUMBER) {
            basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);

            fileSize += (sectorSize * basic.getSectorsPerGroup());
            groups++;
            remain -= (sectorSize * basic.getSectorsPerGroup());

            groupNum = getNextEmptyGroupNumber(groupNum);

            limit--;
        }
        if (groupNum == INVALID_GROUP_NUMBER || limit < 0) {
            // 空きなし or 無限ループ？
            rc = -1;
        }

        if (rc == 0) {
            // 最初のグループをセット
            if (groupItems[0].size() > 0) {
                item.setStartGroup(fileUnitNum, groupItems[0].get(0).group);
            }
        }
        return rc;
    }

    /** グループ番号から開始セクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    /** フォーマットできるか – S‑DOS ではサポートしない */
    @Override
    public boolean supportFormatting() {
        return false;
    }

    /** セクタデータを指定コードで埋める */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    /** セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        return true;
    }

    /** データの書き込み処理 */
    @Override
    public int writeFile(DiskBasicDirItem<DirectorySDos> item,
                         InputStream iStream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sectorNum,
                         int groupNum,
                         int nextGroup,
                         int sectorEnd,
                         int seqNum) throws IOException {

        boolean need_eof_code = item.needCheckEofCode();

        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (need_eof_code) {
                // 最終は終端コード
                if (remain > 1) iStream.readNBytes(buffer, 0, remain - 1);
                if (remain > 0) buffer[remain - 1]=basic.getTextTerminateCode();
            } else {
                if (remain > 0) iStream.readNBytes(buffer, 0, remain);
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            iStream.readNBytes(buffer, 0, size);
            len = size;
            if (need_eof_code && remain == size + 1) {
                // のこりが終端コードだけなら終端コードを出さずここで終了
                len++;
            }
        }
        // 反転
        basic.invertMemory(buffer, size);

        return len;
    }

    /** ファイル削除後の処理 */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectorySDos> item) {
        // 削除したディレクトリエントリ以降をシフトする
        DiskBasicDirItem<DirectorySDos> parent = item.getParent();
        if (parent == null) return true;

        List<DiskBasicDirItem<DirectorySDos>> children = parent.getChildren();
        if (children == null) return true;

        boolean shift = false;
        DiskBasicDirItem<DirectorySDos> prev = null;
        for (DiskBasicDirItem<DirectorySDos> child : children) {
            if (shift && prev != null && child != null) {
                prev.copyItem(child);
            }
            if (child == item) {
                shift = true;
            }
            prev = child;
        }

        return true;
    }
}
