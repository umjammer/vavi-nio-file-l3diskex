///
/// @author Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryFalcom;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * Falcom DOS の処理
 */
public class DiskBasicTypeFalcom extends DiskBasicType<DirectoryFalcom> {

    public DiskBasicTypeFalcom(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFalcom> dir) {
        super(basic, fat, dir);
    }

    /// FATエリアをチェック
    @Override
    public double checkFat(boolean is_formatting) {
        return 1.0;
    }

    /// ディスクから各パラメータを取得＆必要なパラメータを計算
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        // グループ数
        if (basic.getFatEndGroup() == 0) {
            int end_group = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            basic.diskBasicParam.setFatEndGroup(end_group - 1);
        }
        return 1.0;
    }

    /// 残りディスクサイズを計算
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();
//        fatAvailability.setCount(basic.getFatEndGroup() + 1, FAT_AVAIL_FREE);

        final List<DiskBasicDirItem<DirectoryFalcom>> items = dir.getCurrentItems(null);
        for (int idx = 0; items != null && idx < items.size(); idx++) {
            DiskBasicDirItem<DirectoryFalcom> item = items.get(idx);
            if (item == null || !item.isUsed()) continue;

            // グループ番号のマップを調べる
            final DiskBasicGroups groups = item.getGroups();
            int count = groups.count();
            for (int n = 0; n < count; n++) {
                final DiskBasicGroupItem group = groups.itemPtr(n);
                int gnum = group.group;
                if (gnum <= basic.getFatEndGroup()) {
                    if (n + 1 == count) {
                        fatAvailability.set(gnum, FAT_AVAIL_USED_LAST.getValue());
                    } else {
                        fatAvailability.set(gnum, FAT_AVAIL_USED.getValue());
                    }
                }
            }
        }

        // 空きをチェック
        int grps = 0;
        int dir_area = (basic.diskBasicParam.getDirEndSector() / basic.diskBasicParam.getSectorsPerGroup());
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (pos < dir_area) {
                // ディレクトリエリアは使用済み
                fatAvailability.set(pos, FAT_AVAIL_SYSTEM.getValue());
            } else if (fatAvailability.get(pos) == FAT_AVAIL_FREE.getValue()) {
//				fat_availability.Item(pos).set(FAT_AVAIL_FREE);
                grps++;
            }
        }

        int fsize = grps * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(fsize);
        fatAvailability.SetFreeGroups(grps);
    }

    /// フォーマットできるか
    @Override
    public boolean supportFormatting() {
        return false;
    }

    /// セクタデータを埋めた後の個別処理
    @Override
    public boolean additionalProcessOnFormatted(final DiskBasicIdentifiedData data) {
        return true;
    }

    /// ファイルの最終セクタのデータサイズを求める
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryFalcom> item, InputStream istream, OutputStream ostream, final byte[] sector_buffer, int sectorOffsrt, int sector_size, int remain_size) {
        return remain_size;
    }

    /// 書き込み可能か
    @Override
    public boolean supportWriting() {
        return false;
    }

    /// ファイルを削除できるか
    @Override
    public boolean supportDeleting() {
        return false;
    }

    /// 指定したグループ番号のFAT領域を削除する
    @Override
    public void deleteGroupNumber(int group_num) throws IOException {
        // 未使用にする
        setGroupNumber(group_num, 0);
    }
}
