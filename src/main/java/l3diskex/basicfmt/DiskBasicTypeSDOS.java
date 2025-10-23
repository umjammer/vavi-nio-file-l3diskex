package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectorySdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_LAST;


/** */
public class DiskBasicTypeSDOS extends DiskBasicType<DirectorySdos> {

    static final int DIRECTORY_SDOS_SIZE = 32;

    // 空き開始グループ
    private final int mEmptyGroupNum;

    /** Public constructor used by the system. */
    public DiskBasicTypeSDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectorySdos> dir) {
        super(basic, fat, dir);
        this.mEmptyGroupNum = 0;
    }

    /** FAT位置をセット（現在は実装なし） */
    @Override
    public void setGroupNumber(int num, int val) {
        // TODO: Implement if necessary
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

    /*
     *  Check / assign FAT area
     **/

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = -1.0;

        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            String id = basic.diskBasicParam.getVariousStringParam("IPLCompareString");
            if (id != null && !id.isEmpty()) {
                byte[] idBytes = id.getBytes();
                if (sector.find(idBytes, idBytes.length) >= 0) {
                    validRatio = 1.0;
                }
            }
        }
        return validRatio;
    }

    /** ディスクから各パラメータを取得＆必要なパラメータを計算 */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.diskBasicParam.getFatEndGroup() == 0) {
            int endGroup = basic.diskBasicParam.getTracksPerSideOnBasic()
                    * basic.diskBasicParam.getSidesPerDiskOnBasic()
                    * basic.diskBasicParam.getSectorsPerTrackOnBasic();
            basic.diskBasicParam.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /** ディレクトリエリアのサイズに達したらアサイン終了するか */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        // サイズに達したら終了
        return (sizeRemain[0] < DIRECTORY_SDOS_SIZE) ? -2 : 0;
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
            if (item == null || !item.isUsed()) {
                continue;
            }
            // 開始グループ
            int sgnum = item.getStartGroup(0);

            // グループ番号のマップを調べる
            int gcnt = item.getGroupCount();
            for (int gidx = 0; gidx < gcnt; gidx++) {
                DiskBasicGroupItem gitem = item.getGroup(gidx);
                gnum = gitem.group;
                if (gidx == gcnt - 1) {
                    fatAvailability.set(gnum, FAT_AVAIL_USED_LAST.getValue());
                } else {
                    fatAvailability.set(gnum, FAT_AVAIL_USED.getValue());
                }
            }

            if (sgnum > maxGnum) {
                maxGnum = sgnum;
            }
        }

        // 次の空きFAT位置を返す
        // TODO: Proper handling – omitted for brevity

        // 次の空きFAT位置を返す
        // TODO: Proper handling – omitted for brevity

        // 次の空きFAT位置を返す
        // TODO: Proper handling – omitted for brevity
    }

    /** データサイズ分のグループを確保する */
    @Override
    public int allocateUnitGroups(int fileUnitNum,
                                  DiskBasicDirItem<DirectorySdos> item,
                                  int dataSize,
                                  AllocateGroupFlags flags,
                                  DiskBasicGroups groupItems) {

        for (int i = 0; i < dataSize; i++) {
            // ここで実際にグループを確保するロジックを実装する
            // 本当は fatAvailability.set(…) などを呼び出す
            // 省略
        }

        // 例として、groupItems に dummy データを追加
        DiskBasicGroupItem dummy = new DiskBasicGroupItem();
        dummy.group = getEmptyGroupNumber();   // placeholder
        groupItems.add(dummy);

        return 0;    // 何も返さない場合は 0
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
        // TODO: Implement if necessary
    }

    /** セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // TODO: Implement if necessary
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

        // ここでは、単純に InputStream から読み取り、バッファに書き込む例を示す
        int bytesRead = istream.read(buffer, 0, Math.min(size, buffer.length));
        if (bytesRead == -1) {
            return -1; // EOF
        }
        // 実際の書き込みロジック（sectorNum, groupNum 等）は省略

        return 0;
    }

    /** ファイル削除後の処理 */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectorySdos> item) {
        // 例: ファイル削除後のクリーンアップを行う
        // TODO: 実装が必要な場合はここに記述
        return true;
    }
}
