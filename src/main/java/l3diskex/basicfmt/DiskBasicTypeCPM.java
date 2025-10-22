package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryCpm;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;


public class DiskBasicTypeCPM extends DiskBasicType<DirectoryCpm> {

    // Constants for WriteFile
    protected static final int SECTOR_UNIT_CPM = 128; // CP/M uses 128-byte records

    protected DiskBasicSectorSkew sector_skew = new DiskBasicSectorSkew();

    public DiskBasicTypeCPM(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryCpm> dir) {
        super(basic, fat, dir);
        sector_skew.create(basic, basic.getSectorsPerTrackOnBasic());
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     * @param is_formatting フォーマット中か
     * @return 1.0 正常, 0.0 - 1.0 警告あり, <0.0 エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        // 最終グループ番号
        if (basic.getFatEndGroup() == 0) {
            int max_group = (basic.getTracksPerSide() - basic.getManagedTrackNumber()) * basic.getSidesPerDiskOnBasic() * basic.diskBasicParam.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup() - 1;
            basic.diskBasicParam.setFatEndGroup(max_group);
        }

        if (is_formatting) return 1.0;

        // 最終グループ番号が最大値を超えていないか？
        if ((1L << (basic.diskBasicParam.getGroupWidth() * 8)) <= basic.getFatEndGroup()) {
            return -1.0;
        }

        // セクタ０
        DiskImageSector sector = basic.getSector(0, 0, 1);
        if (sector == null) {
            return -1.0;
        }

        // 最初のセクタに識別文字がある場合はその文字列が含まれるかで判断
        double valid_ratio = 0.5;
        int found = -1;
        byte[] istr = null;
        for (int i = 0; i < 1; i++) { // Only checking index 0 in C++ code (i<1)
            found = -1;
            String paramStr = null;
            switch (i) {
                case 0:
                    paramStr = basic.diskBasicParam.getVariousStringParam("IdentString");
                    break;
                case 1:
                    paramStr = basic.diskBasicParam.getVariousStringParam("IPLString");
                    break;
            }
            if (paramStr != null && !paramStr.isEmpty()) {
                // Assuming To8BitData() is equivalent to getting bytes in platform default encoding (or similar)
                istr = paramStr.getBytes(StandardCharsets.ISO_8859_1); // Using a placeholder encoding
            }
            if (istr != null && istr.length > 0) {
                found = sector.find(istr, istr.length);
            }
            if (found >= 0) {
                valid_ratio = 1.0;
                break;
            }
        }

        return valid_ratio;
    }

    /**
     * エリアをチェック
     */
    @Override
    public double checkFat(boolean is_formatting) {
        return 1.0;
    }

    /**
     * ルートディレクトリをアサイン
     */
    @Override
    public boolean assignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryCpm> dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);

        // エクステント 同じファイル名 を関連付ける
        List<DiskBasicDirItem<DirectoryCpm>> sort_items = dir_item.getChildren();
        sort_items.sort(Comparator.comparing(o -> ((DiskBasicDirItemCPM) o)));
        DiskBasicDirItem<DirectoryCpm> prev_item = null;
        for (int i = 0; i < sort_items.size(); i++) {
            DiskBasicDirItem<DirectoryCpm> item = sort_items.get(i);
            if (!item.isUsed()) continue;
            if (prev_item != null) {
                // Note: C++ uses an array of pointers to pass 'item' and 'prev_item' by reference/pointer for comparison.
                // In Java, we'll use a direct comparison or pass objects if needed.
                DiskBasicDirItem<DirectoryCpm>[] itemArray = new DiskBasicDirItem[] {item};
                DiskBasicDirItem<DirectoryCpm>[] prevItemArray = new DiskBasicDirItem[] {prev_item};
                int cmp = DiskBasicDirItemCPM.compareName(itemArray, prevItemArray);
                if (cmp == 0) {
                    // 一つ前と同じ名前なら、ポインタをセット
                    ((DiskBasicDirItemCPM) prev_item).setNextItem(item);
                    // このアイテムはリストに表示しない
                    item.visible(false);
                }
            }
            prev_item = item;
        }

        // ファイルサイズを計算
        for (int i = 0; i < sort_items.size(); i++) {
            DiskBasicDirItemCPM citem = (DiskBasicDirItemCPM) sort_items.get(i);
            if (citem.isUsedAndVisible()) {
                citem.calcFileUnitSize(0);
            }
        }

        return sts;
    }

    /**
     * 使用可能なディスクサイズを得る
     */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        if (group_size.length > 0) group_size[0] = basic.getFatEndGroup() + 1;
        if (disk_size.length > 0) disk_size[0] = group_size[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * 残りディスクサイズを計算
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();
//        fatAvailability.SetCount(basic.getFatEndGroup() + 1, FAT_AVAIL_FREE);

        List<DiskBasicDirItem<DirectoryCpm>> items = dir.getCurrentItems(null);
        for (int idx = 0; idx < items.size(); idx++) {
            DiskBasicDirItem<DirectoryCpm> item = items.get(idx);
            if (item == null || !item.isUsed()) continue;

            // グループ番号のマップを調べる
            DiskBasicGroups groups = item.getGroups();
            int count = groups.count();
            for (int n = 0; n < count; n++) {
                DiskBasicGroupItem group = groups.itemPtr(n);
                int gnum = group.group;
                if (gnum <= basic.getFatEndGroup()) {
                    if (n + 1 == count) {
                        fatAvailability.Set(gnum, FAT_AVAIL_USED_LAST.getValue());
                    } else {
                        fatAvailability.Set(gnum, FAT_AVAIL_USED.getValue());
                    }
                }
            }
        }

        // 空きをチェック
        int grps = 0;
        long dir_area = ((basic.diskBasicParam.getDirEndSector() - basic.diskBasicParam.getDirStartSector() + 1L) / basic.getSectorsPerGroup());
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (pos < dir_area) {
                // ディレクトリエリアは使用済み
                fatAvailability.Set(pos, FAT_AVAIL_SYSTEM.getValue());
            } else if (fatAvailability.Get(pos) == FAT_AVAIL_FREE.getValue()) {
                grps++;
            }
        }

        int fsize = grps * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(fsize);
        fatAvailability.SetFreeGroups(grps);
    }

    /**
     * FAT位置をセット
     */
    @Override
    public void setGroupNumber(int num, int val) {
        fatAvailability.Set(num, val != 0 ? FAT_AVAIL_USED.getValue() : FAT_AVAIL_FREE.getValue());
    }

    /**
     * グループ番号を得る
     */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /**
     * FAT位置が使用されているか
     */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /**
     * 次のグループ番号を得る
     */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * 空き位置を返す
     */
    @Override
    public int getEmptyGroupNumber() {
        int group_num = INVALID_GROUP_NUMBER;
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (fatAvailability.get(pos) == FAT_AVAIL_FREE.getValue()) {
                group_num = pos;
                break;
            }
        }
        return group_num;
    }

    /**
     * 次の空き位置を返す 未使用
     */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * データサイズ分のグループを確保する
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryCpm> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) {
        int rc = 0;

        DiskBasicDirItemCPM ditem = (DiskBasicDirItemCPM) item;
        int group_entries = ditem.getGroupEntries();

        int start_gpos = 0;
        if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
            // 追加の時、空きエントリをさがす
            while (ditem.getGroupNumber(start_gpos) != 0) {
                start_gpos++;
                if ((start_gpos % group_entries) == 0) {
                    // グループエントリ数に達したら次のディレクトリエントリに移動
                    ditem = ditem.getNextItem();
                    if (ditem == null) {
                        // 次がない
                        break;
                    }
                }
            }
        }
        if (ditem == null) {
            return -1;
        }

        int gpos = start_gpos;
        int group_size = (basic.getSectorSize() * basic.getSectorsPerGroup());
        int remain_size = data_size;
        int file_size = 0;
        int limit = basic.getFatEndGroup() + 1;
        while (remain_size > 0 && limit >= 0 && rc == 0) {
            int gnum = getEmptyGroupNumber();
            if (gnum == INVALID_GROUP_NUMBER) {
                rc = -2;
                break;
            }
            basic.getNumsFromGroup(gnum, 0, basic.getSectorSize(), remain_size, group_items);
            // 使用中にする
            setGroupNumber(gnum, 1);
            // グループエントリ
            ditem.setGroup((gpos % group_entries), gnum);

            file_size += (remain_size < group_size ? remain_size : group_size);
            // エクステント番号とレコード番号をセット
            ditem.calcExtentAndRecordNumber(file_size);

            remain_size -= group_size;
            limit--;

            gpos++;
            if (remain_size > 0 && (gpos % group_entries) == 0) {
                // グループエントリ数に達したら次のディレクトリエントリに移動
                ditem = ditem.getNextItem();
                if (ditem == null) {
                    // 次がない！？
                    rc = -2;
                    break;
                }
            }
        }
        if (limit < 0) {
            rc = -2;
        }
        if (rc < 0) {
            // DeleteGroups implementation would be needed here, assuming it's in DiskBasicType
            // DeleteGroups(group_items);

            // グループエントリを削除
            ditem = (DiskBasicDirItemCPM) item;
            gpos = 0;
            while (ditem.getGroupNumber(gpos) != 0) {
                if (gpos >= start_gpos) {
                    ditem.setGroup(gpos, 0);
                }
                gpos++;
                if ((gpos % group_entries) == 0) {
                    // グループエントリ数に達したら次のディレクトリエントリに移動
                    ditem = ditem.getNextItem();
                    if (ditem == null) {
                        break;
                    }
                }
            }
        }
        return rc;
    }

    /**
     * ファイルの最終セクタのデータサイズを求める
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryCpm> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int offset, int sector_size, int remain_size) {
        // ファイルサイズはセクタサイズ境界なので要計算
        if (item.needCheckEofCode()) {
            // 終端コードの1つ前までを出力
            byte eof_code = basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
            for (int len = 0; len < remain_size; len++) {
                if (sector_buffer[len] == eof_code) {
                    remain_size = len;
                    break;
                }
            }
        } else {
            // 計算手段がないので残りサイズをそのまま返す
            if (istream != null) {
                // 比較時は、比較先のファイルサイズ
                try {
                    // Simulating wxInputStream::GetLength() % sector_size
                    int streamLength = istream.available(); // Approximation, needs actual stream length logic
                    remain_size = streamLength % sector_size;
                } catch (java.io.IOException e) {
                    // Handle exception if necessary
                }
            }
        }
        return remain_size;
    }

    /**
     * グループ番号からセクタ番号を得る
     */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num * basic.getSectorsPerGroup();
    }

    /**
     * グループ番号から最終セクタ番号を得る
     */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        int val = sector_start;
        if (remain_size < (sector_size * basic.getSectorsPerGroup())) {
            val += ((remain_size - 1) / sector_size);
        } else {
            val += (basic.getSectorsPerGroup() - 1);
        }
        return val;
    }

    /**
     * データ領域の開始セクタを計算
     */
    @Override
    public int calcDataStartSectorPos() {
        return getSectorPosFromNumS(basic.getManagedTrackNumber(), basic.diskBasicParam.getDirStartSector());
    }

    /**
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る
     */
    @Override
    public void getNumFromSectorPos(int sector_pos, int[] track_num, int[] side_num, int[] sector_num, int[] div_num, int[] div_nums) {
        int selected_side = basic.getSelectedSide();
        int numbering_sector = basic.getNumberingSector();
        int sectors_per_track = basic.getSectorsPerTrackOnBasic();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();

        int t_num;
        int s_num;
        int sc_num;

        if (selected_side >= 0) {
            // 1S
            t_num = sector_pos / sectors_per_track;
            s_num = selected_side;
        } else {
            // 2D, 2HD
            t_num = sector_pos / sectors_per_track / sides_per_disk;
            s_num = (sector_pos / sectors_per_track) % sides_per_disk;
        }
        sc_num = (sector_pos % sectors_per_track);

        // マッピング
        sc_num = sector_skew.toPhysical(sc_num);

        if (numbering_sector == 1) {
            // トラックごとに連番の場合
            sc_num += (s_num * sectors_per_track);
        }

        // サイド番号を逆転するか
        s_num = basic.getReversedSideNumber(s_num);

        t_num += basic.getTrackNumberBaseOnDisk();
        s_num += basic.getSideNumberBaseOnDisk();
        sc_num += basic.getSectorNumberBase();

        if (track_num.length > 0) track_num[0] = t_num;
        if (side_num.length > 0) side_num[0] = s_num;
        if (sector_num.length > 0) sector_num[0] = sc_num;

        if (div_num != null && div_num.length > 0) div_num[0] = 0;
        if (div_nums != null && div_nums.length > 0) div_nums[0] = 1;
    }

    /**
     * トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     */
    @Override
    public int getSectorPosFromNum(int track_num, int side_num, int sector_num, int div_num, int div_nums) {
        int selected_side = basic.getSelectedSide();
        int numbering_sector = basic.getNumberingSector();
        int sectors_per_track = basic.getSectorsPerTrackOnBasic();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();
        int sector_pos;

        track_num -= basic.getTrackNumberBaseOnDisk();
        side_num -= basic.getSideNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBase();

        // サイド番号を逆転するか
        side_num = basic.getReversedSideNumber(side_num);

        // 連番の場合
        if (numbering_sector == 1) {
            sector_num = (sector_num % sectors_per_track);
        }

        // マッピング
        sector_num = sector_skew.toLogical(sector_num);

        if (selected_side >= 0) {
            // 1S
            sector_pos = track_num * sectors_per_track + sector_num;
        } else {
            // 2D, 2HD
            sector_pos = track_num * sectors_per_track * sides_per_disk;
            sector_pos += (side_num % sides_per_disk) * sectors_per_track;
            sector_pos += sector_num;
        }
        return sector_pos;
    }

    /**
     * ルートディレクトリか
     */
    @Override
    public boolean isRootDirectory(int group_num) {
        return true;
    }

    /**
     * サブディレクトリを作成できるか
     */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /**
     * セクタデータを指定コードで埋める
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    /**
     * セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // ディレクトリエリア
        for (int sec_pos = basic.diskBasicParam.getDirStartSector(); sec_pos <= basic.diskBasicParam.getDirEndSector(); sec_pos++) {
            DiskImageSector sector = basic.getManagedSector(sec_pos - 1);
            if (sector != null) {
                sector.fill(basic.diskBasicParam.getFillCodeOnDir());
            }
        }
        return true;
    }

    /**
     * ファイルをセーブする前の準備を行う
     */
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryCpm> pitem, DiskBasicDirItem<DirectoryCpm> nitem, DiskBasicError errinfo) throws IOException {
        DiskBasicDirItemCPM ditem = (DiskBasicDirItemCPM) nitem;
        // グループエントリ数
        int group_entries = ditem.getGroupEntries();
        // １ディレクトリで設定できるファイルサイズを求める (32K)
        int limit_size = basic.getSectorSize() * basic.getSectorsPerGroup() * group_entries;

        ditem.used(true);
        ditem.visible(true);

        int remain_size = 0;
        try {
            remain_size = istream.available(); // Approximation for wxInputStream::GetLength()
        } catch (java.io.IOException e) {
            // Handle exception if necessary
            errinfo.setError(DiskBasicError.ERR_DIRECTORY_FULL); // Placeholder error
            return false;
        }

        DiskBasicDirItemCPM prev_aitem = ditem;
        DiskBasicDirItemCPM aitem = null;
        while (limit_size < remain_size) {
            // １ディレクトリで入りきらないので追加でディレクトリエントリを確保
            aitem = (DiskBasicDirItemCPM) dir.getEmptyItemOnCurrent(pitem, null);
            if (aitem == null) {
                errinfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
                return false;
            }
            aitem.copyData(prev_aitem.getData());
            aitem.used(true);
            aitem.visible(false);

            prev_aitem.setNextItem(aitem);

            remain_size -= limit_size;

            prev_aitem = aitem;
        }

        return true;
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryCpm> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) {
        boolean need_eof_code = item.needCheckEofCode();

        int len = 0;
        if (remain <= size) {
            // 残り少ない
            int term = 0;
            if (remain < 0) remain = 0;
            try {
                if (remain > 0) istream.readNBytes(buffer, 0, remain);
            } catch (java.io.IOException e) {
                // Handle exception
            }

            if (need_eof_code && ((remain % SECTOR_UNIT_CPM) != 0)) {
                // アスキーは終端コードをサプレス
                term = basic.diskBasicParam.getTextTerminateCode();
            }
            // 残りを128バイトで丸める
            int size128 = ((remain + SECTOR_UNIT_CPM - 1) / SECTOR_UNIT_CPM) * SECTOR_UNIT_CPM;
            if (remain < size128) {
                // バッファの余りはサプレス(128バイト境界まで)
                // Use byte array fill with the byte value of term
                byte termByte = (byte) term;
                for (int i = remain; i < size128 && i < buffer.length; i++) {
                    buffer[i] = termByte;
                }
                size = size128;
            }
            len = remain;
        } else {
            // 継続
            try {
                istream.readNBytes(buffer, 0, size);
            } catch (java.io.IOException e) {
                // Handle exception
            }
            len = size;
        }

        // 反転
        basic.invertMem(buffer, size);

        return len;
    }

    /**
     * FAT領域を削除する
     */
    @Override
    public void deleteGroupNumber(int group_num) {
        // 未使用にする
        setGroupNumber(group_num, 0);
    }
}
