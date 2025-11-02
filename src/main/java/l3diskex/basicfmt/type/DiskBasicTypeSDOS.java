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
import l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.DirectorySdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_LEAK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/** */
public class DiskBasicTypeSDOS extends DiskBasicType<DirectorySdos> {

    // 空き開始グループ
    private int mEmptyGroupNum;

    /** Public constructor used by the system. */
    public DiskBasicTypeSDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectorySdos> dir) {
        super(basic, fat, dir);

        this.mEmptyGroupNum = 0;
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

        if (mEmptyGroupNum <= basic.diskBasicParam.getFatEndGroup()) {
            newNum = mEmptyGroupNum;
        }

        return newNum;
    }

    /** 次の空きFAT位置を返す */
    @Override
    public int getNextEmptyGroupNumber(int currGroup) {
        int newNum = INVALID_GROUP_NUMBER;

        currGroup++;
        if (currGroup <= basic.diskBasicParam.getFatEndGroup()) {
            newNum = currGroup;
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
            byte[] id = basic.diskBasicParam.getVariousStringParam("IPLCompareString").getBytes();
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
        if (basic.diskBasicParam.getFatEndGroup() == 0) {
            int endGroup = basic.diskBasicParam.getTracksPerSideOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getSectorsPerTrackOnBasic();
            basic.diskBasicParam.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /** ディレクトリエリアのサイズに達したらアサイン終了するか */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        // サイズに達したら終了
        return (sizeRemain[0] < DirectorySdos.SIZE) ? -2 : 0;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = 0;
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum != basic.diskBasicParam.getGroupSystemCode()) {
                groupSize[0]++;
            }
        }
        diskSize[0] = groupSize[0] * basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector();
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();
//        fatAvailability.setCount(basic.getFatEndGroup() + 1, FAT_AVAIL_FREE);

        int gnum;
        int maxGnum = 0;

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectorySdos>> items = dir.getCurrentItems(null);
        for (int idx = 0; idx < items.size(); idx++) {
            DiskBasicDirItem<DirectorySdos> item = items.get(idx);
            if (item == null || !item.isUsed()) continue;
            // 開始グループ
            int sgnum = item.getStartGroup(0);

            // グループ番号のマップを調べる
            int gcnt = item.getGroupCount();
            for (int gidx = 0; gidx < gcnt; gidx++) {
                DiskBasicGroupItem gitem = item.getGroup(gidx);
                gnum = gitem.group;
                fatAvailability.set(gnum, gidx == gcnt - 1 ? FAT_AVAIL_USED_LAST : FAT_AVAIL_USED);
            }

            if (sgnum + gcnt > maxGnum) {
                maxGnum = sgnum + gcnt;
            }
        }

        // 空きをチェック
        int grps = 0;
        int gend = basic.diskBasicParam.getReservedSectors();
        for(int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (pos < gend) {
                // ディレクトリエリアは使用済み
                fatAvailability.set(pos, FAT_AVAIL_SYSTEM);
            } else if (fatAvailability.get(pos) == FAT_AVAIL_FREE) {
                if (pos < maxGnum) {
                    // 削除されたエリア
                    fatAvailability.set(pos, FAT_AVAIL_LEAK);
                } else {
                    // 空き
                    grps++;
                }
            }
        }

        mEmptyGroupNum = maxGnum;

        int fsize = grps * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(fsize);
        fatAvailability.setFreeGroups(grps);
    }

    /** データサイズ分のグループを確保する */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectorySdos> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) {

        int file_size = 0;
        int groups = 0;

        int rc = 0;
        int sec_size = basic.getSectorSize();
        int remain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        int group_num = getEmptyGroupNumber();
        while(remain > 0 && limit >= 0 && group_num != INVALID_GROUP_NUMBER) {
            basic.getNumsFromGroup(group_num, 0, sec_size, remain, groupItems[0]);

            file_size += (sec_size * basic.getSectorsPerGroup());
            groups++;
            remain -= (sec_size * basic.getSectorsPerGroup());

            group_num = getNextEmptyGroupNumber(group_num);

            limit--;
        }
        if (group_num == INVALID_GROUP_NUMBER || limit < 0) {
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
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    /** セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        return true;
    }

    /** データの書き込み処理 */
    @Override
    public int writeFile(DiskBasicDirItem<DirectorySdos> item,
                         InputStream istream,
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
                if (remain > 1) istream.read(buffer, 0, remain - 1);
                if (remain > 0) buffer[remain - 1]=basic.diskBasicParam.getTextTerminateCode();
            } else {
                if (remain > 0) istream.read(buffer, 0, remain);
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            istream.read(buffer, 0, size);
            len = size;
            if (need_eof_code && remain == size + 1) {
                // のこりが終端コードだけなら終端コードを出さずここで終了
                len++;
            }
        }
        // 反転
        basic.invertMem(buffer, size);

        return len;
    }

    /** ファイル削除後の処理 */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectorySdos> item) {
        // 削除したディレクトリエントリ以降をシフトする
        DiskBasicDirItem<DirectorySdos> parent = item.getParent();
        if (parent == null) return true;

        List<DiskBasicDirItem<DirectorySdos>> children = parent.getChildren();
        if (children == null) return true;

        boolean shift = false;
        DiskBasicDirItem<DirectorySdos> prev = null;
        for(int i=0; i<children.size(); i++) {
            DiskBasicDirItem<DirectorySdos> curr = children.get(i);
            if (shift && prev != null && curr != null) {
                prev.copyItem(curr);
            }
            if (curr == item) {
                shift = true;
            }
            prev = curr;
        }

        return true;
    }
}
