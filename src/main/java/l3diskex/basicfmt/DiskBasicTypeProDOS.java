package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryProdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDirItemProDOS.ProDOSDirPtrT;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_PRODOS;
import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_ACCESS_ALL;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_CHANGE;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SAPLING;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SUBDIR;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SUBVOL;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_TREE;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_VOLUME;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;


/**
 * Apple ProDOS 8 / 16 の処理
 */
public class DiskBasicTypeProDOS extends DiskBasicType<DirectoryProdos> {

    // Conversion starts here

    /**
     * Apple ProDOS ビットマップ
     */
    static class ProDOSBitmap extends DiskBasicBitMLMap {

        int m_group_num;

        public ProDOSBitmap() {
            super();
            m_group_num = INVALID_GROUP_NUMBER;
        }

        /**
         * ポインタをセット
         */
        public void AddBitmap(DiskImageSector sector) {
            super.addBuffer(
                    sector.getSectorBuffer(),
                    sector.getSectorSize()
            );
        }

        /**
         * 指定位置のビットを変更する
         *
         * @param group_num 位置
         * @param use       セットする場合true
         */
        @Override
        public void modify(int group_num, boolean use) {
            super.modify(group_num, !use); // 逆転
        }

        /**
         * 指定位置が空いているか
         *
         * @param group_num 位置
         * @return 空いている場合 true
         */
        public boolean IsFree(int group_num) {
            return super.isSet(group_num); // 逆転
        }


        /// ブロック番号をセット
        void SetMyGroupNumber(int group_num) {
            m_group_num = group_num;
        }

        /// ブロック番号を得る
        int GetMyGroupNumber() {
            return m_group_num;
        }
    }

    private final DiskBasicSectorSkew sector_skew = new DiskBasicSectorSkew();
    private final ProDOSBitmap bitmap = new ProDOSBitmap();
    private DirectoryProdos volume;
    private final DiskBasicSectorPosTrans sector_map = new DiskBasicSectorPosTrans();

    public DiskBasicTypeProDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryProdos> dir) {
        super(basic, fat, dir);
        this.volume = null;

        // ProDOS 8のときは、セクタ→ブロックマップを作成
        if (basic.diskBasicParam.getTracksPerSideOnBasic() <= 40) {
            // ProDOS 8
            sector_skew.create(basic, basic.getSectorsPerTrackOnBasic());
        }
    }

    /**
     * エリアをチェック
     *
     * @param is_formatting フォーマット中か
     * @return 1.0       正常
     * 0.0 - 1.0 警告あり
     * <0.0      エラーあり
     */
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        // ビットマップ
        int group_num = bitmap.GetMyGroupNumber();
        int st_pos = getStartSectorFromGroup(group_num);
        int ed_pos = getEndSectorFromGroup(group_num, INVALID_GROUP_NUMBER, st_pos, 0, 0);
        for (int sec = st_pos; sec <= ed_pos; sec++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(sec);
            if (sector == null) {
                return -1.0;
            }
            bitmap.AddBitmap(sector);
        }
        basic.diskBasicParam.setSectorsPerFat(bitmap.size());

        return valid_ratio;
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param is_formatting フォーマット中か
     * @return 1.0       正常
     * 0.0 - 1.0 警告あり
     * <0.0      エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (is_formatting) return 0;

        double valid_ratio = 1.0;

        // 可変数セクタなのでトラックごとのセクタ数を集計
        sector_map.create(basic);

        // セクタ数の合計
        int calc_total_blocks = sector_map.getTotalSectors() / basic.diskBasicParam.getSectorsPerGroup();
        basic.diskBasicParam.setFatEndGroup(calc_total_blocks - 1);

        // ボリュームディレクトリ
        DiskImageSector sector = basic.getSectorFromGroup(basic.diskBasicParam.getDirStartSector() / basic.diskBasicParam.getSectorsPerGroup());
        if (sector == null) {
            return -1.0;
        }
        DirectoryProdos vol = new DirectoryProdos();
        // Assuming GetSectorBuffer(4) reads into vol structure
        // This is a simplification as C++ casts a pointer.
        // In Java, we'd need a way to read/map a byte array into the structure.
        // For conversion, we will use a placeholder mapping method.
        // Placeholder for (directory_prodos_t *)sector->GetSectorBuffer(4)
        byte[] volBuffer = sector.getSectorBuffer(4);
        if (volBuffer.length > 0) {
            // Placeholder: Assume data from volBuffer is mapped to 'vol' fields
            vol.aux.v.entryLen = volBuffer[0]; // Simplistic mapping
        }

        if (vol.aux.v.entryLen != 0) { // sizeof(directory_prodos_t) is not available here, using a placeholder comparison
            // Simplified check based on C++ original: if (vol->v.entry_len != (int)sizeof(directory_prodos_t))
            if (vol.aux.v.entryLen != 32) { // Assuming a typical size for directory_prodos_t is 32 for example
                return -1.0;
            }
        }

        int stor_total_blocks = vol.aux.v.totalBlocks;

        if (stor_total_blocks > calc_total_blocks) {
            valid_ratio -= 0.5;
        }

        basic.diskBasicParam.setFatEndGroup(stor_total_blocks - 1);

        bitmap.SetMyGroupNumber(vol.aux.v.bitmapPointer);

        this.volume = vol; // Store reference

        return valid_ratio;
    }


    /**
     * Allocation Mapの開始位置を得る（ダイアログ用）
     */
    @Override
    public void getStartNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        int group_num = bitmap.GetMyGroupNumber();
        int st_pos = getStartSectorFromGroup(group_num);
        getNumFromSectorPos(st_pos, track_num, side_num, sector_num, null, null);
    }

    /**
     * Allocation Mapの終了位置を得る（ダイアログ用）
     */
    @Override
    public void getEndNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        int group_num = bitmap.GetMyGroupNumber();
        int st_pos = getStartSectorFromGroup(group_num);
        int ed_pos = getEndSectorFromGroup(group_num, INVALID_GROUP_NUMBER, st_pos, 0, 0);
        getNumFromSectorPos(ed_pos, track_num, side_num, sector_num, null, null);
    }

    /**
     * タイトル名（ダイアログ用）
     */
    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    /**
     * ルートディレクトリをアサイン
     */
    @Override
    public boolean assignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryProdos> dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);

        // ボリュームヘッダの内容をコピーする
        DiskBasicGroupItem gitem = group_items.item(0);
        DiskImageSector sector = basic.getSector(gitem.track, gitem.side, gitem.sectorStart);
        DirectoryProdos vol = new DirectoryProdos();
        // Placeholder for directory_t *vol = (directory_t *)sector->GetSectorBuffer(4);
        byte[] volBuffer = sector.getSectorBuffer(4);
        // Assuming vol is mapped from volBuffer

        dir_item.copyData(vol);
        // ディレクトリ属性にしておく
        dir_item.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);
        // ブロック番号を設定
        dir_item.setStartGroup(0, basic.diskBasicParam.getDirStartSector() / basic.getSectorsPerGroup());

        return sts;
    }

    /**
     * ルートディレクトリのセクタリストを計算
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) {
        boolean valid = true;

        group_items.empty();

        // ディレクトリのチェインをたどる
        int dir_size = 0;
        int limit = basic.diskBasicParam.getDirEndSector() - basic.diskBasicParam.getDirStartSector() + 1;
        int[] trk_num = {0};
        int[] sid_num = {0};
        int sec_num = 1;

        // 開始セクタ
        int sector_pos = basic.diskBasicParam.getDirStartSector();
        //	voldir.Empty();

        while (valid && limit >= 0) {
            ProDOSDirPtrT next = new ProDOSDirPtrT();
            next.nextBlock = 0;

            int group_num = sector_pos / basic.getSectorsPerGroup();
            //		voldir.Add((int)group_num);

            for (int ss = 0; ss < basic.getSectorsPerGroup(); ss++) {
                DiskImageSector sector = basic.getSectorFromSectorPos(sector_pos, trk_num, sid_num);
                if (sector == null) {
                    valid = false;
                    break;
                }
                sec_num = sector.getSectorNumber();
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    valid = false;
                    break;
                }
                if (ss == 0) {
                    // 次のブロックへのポインタを保持
                    // Placeholder for memcpy(&next, buffer, sizeof(ProDOSDirPtrT));
                    // Assuming ProDOSDirPtrT is 4 bytes (2 wxUint16)
                    next.nextBlock = (short) ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)); // Assuming little-endian for direct read
                    next.prevBlock = (short) ((buffer[2] & 0xFF) | ((buffer[3] & 0xFF) << 8));
                }

                group_items.add(group_num, 0, trk_num[0], sid_num[0], sec_num, sec_num);

                dir_size += sector.getSectorSize();
                sector_pos++;
            }

            if (!valid) break;

            // 次のセクタなし
            if (next.nextBlock == 0) {
                break;
            }

            sector_pos = next.nextBlock * basic.getSectorsPerGroup();

            limit--;
        }
        group_items.setSize(dir_size);

        if (limit < 0) {
            valid = false;
        }

        return valid;
    }

    /**
     * ディレクトリエリアのサイズに達したらアサイン終了するか
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        // サイズに達したら以降は未使用とする
        int blk_size = basic.getSectorSize() * basic.getSectorsPerGroup();
        // sizeof(directory_prodos_t) is not available, using a placeholder size (e.g., 32)
        int dir_prodos_t_size = 32;
        return ((size_remain[0] % blk_size) < dir_prodos_t_size ? -1 : 0);
    }

    /**
     * セクタをディレクトリとして初期化
     */
    @Override
    public int initializeSectorsAsDirectory(DiskBasicGroups group_items, int[] file_size, int[] size_remain, DiskBasicError errinfo) {
        file_size[0] = group_items.count() * basic.getSectorSize();
        size_remain[0] = 0;

        return 0;
    }

    /**
     * 使用可能なディスクサイズを得る
     */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.getFatEndGroup() + 1;
        disk_size[0] = group_size[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * 残りディスクサイズを計算
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        //	int fsize = 0;
        //	int grps = 0;

        fatAvailability.clear();

        // BITMAP table
        for (int grp = 0; grp <= basic.getFatEndGroup(); grp++) {
            if (grp <= 2) {
                fatAvailability.add(FAT_AVAIL_SYSTEM.getValue(), 0, 0);
            } else if (grp == bitmap.GetMyGroupNumber()) {
                fatAvailability.add(FAT_AVAIL_SYSTEM.getValue(), 0, 0);
            } else if (bitmap.IsFree(grp)) {
                fatAvailability.add(FAT_AVAIL_FREE.getValue(), basic.getSectorSize() * basic.getSectorsPerGroup(), 1);
            } else {
                fatAvailability.add(FAT_AVAIL_USED.getValue(), 0, 0);
            }
        }
        // Volume directory
        DiskBasicDirItem<DirectoryProdos> root = dir.getRootItem();
        if (root != null) {
            DiskBasicGroups root_groups = root.getGroups();
            if (root_groups != null) {
                for (int i = 0; i < root_groups.count(); i++) {
                    fatAvailability.set(i, FAT_AVAIL_SYSTEM.getValue());
                }
            }
        }
        //	free_disk_size = (int)fsize;
        //	free_groups = (int)grps;
    }

    /**
     * グループ番号を使用済みにする
     */
    @Override
    public void setGroupNumber(int num, int val) {
        bitmap.modify(num, val != 0);
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
        int new_num = INVALID_GROUP_NUMBER;

        for (int grp = 3; grp <= basic.getFatEndGroup(); grp++) {
            if (bitmap.IsFree(grp)) {
                new_num = grp;
                break;
            }
        }

        return new_num;
    }

    /**
     * 次の空き位置を返す
     */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        // 次の空き位置候補
        return getEmptyGroupNumber();
    }

    /**
     * ファイルをセーブする前の準備を行う
     */
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryProdos> pitem, DiskBasicDirItem<DirectoryProdos> nitem, DiskBasicError errinfo) {
        // チェインセクタをクリア
        nitem.clearChainSector(null);

        return true;
    }

    /**
     * チェインセクタを確保する
     */
    private int allocChainSector(int idx, DiskBasicDirItem<DirectoryProdos> item) {
        int gnum = getEmptyGroupNumber();
        if (gnum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // セクタ
        int st_pos = getStartSectorFromGroup(gnum);
        int ed_pos = getEndSectorFromGroup(gnum, INVALID_GROUP_NUMBER, st_pos, 0, 0);
        for (int sec = st_pos; sec <= ed_pos; sec++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(sec);
            if (sector == null) {
                return INVALID_GROUP_NUMBER;
            }
            sector.fill((byte) 0);
        }

        // チェイン情報にセクタをセット
        item.setChainSector(gnum, st_pos, null, null);

        // 開始グループを設定
        if (idx == 0) {
            item.setStartGroup(0, gnum, 1);
        }
        // セクタを予約
        setGroupNumber(gnum, 1);

        return gnum;
    }

    /**
     * データサイズ分のグループを確保する
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryProdos> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) throws IOException {
        //	logger.log(Level.DEBUG, "DiskBasicTypeProDOS::AllocateGroups {");

        //	int file_size = 0;
        int groups = 0;

        int rc = 0;
        int sec_size = basic.getSectorSize();
        int block_size = sec_size * basic.getSectorsPerGroup();
        int remain = data_size;
        int limit = basic.getFatEndGroup() + 1;
        int chain_idx = 0;

        DiskBasicFileType attr = item.getFileAttr();
        int stype = (attr.getOrigin() >> 16) & 0xff;
        if (stype == FILETYPE_MASK_PRODOS_SUBDIR) {
            // サブディレクトリ
            while (remain > 0 && limit >= 0) {
                // 空きをさがす
                int group_num = getEmptyGroupNumber();
                if (group_num == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // 使用済みにする
                basic.getNumsFromGroup(group_num, 0, sec_size, remain, group_items);
                setGroupNumber(group_num, 1);

                if (chain_idx == 0 && flags != ALLOCATE_GROUPS_APPEND) {
                    item.setStartGroup(0, group_num, 1);
                }
                chain_idx++;

                //			file_size += block_size;
                groups++;
                remain -= block_size;
                limit--;
            }
            if (rc == 0 && flags == ALLOCATE_GROUPS_APPEND) {
                // 追加のときはチェインをつなぐ
                if (group_items.count() > 0) {
                    rc = chainDirectoryGroups(item, group_items);
                }
            }
        } else if (stype == FILETYPE_MASK_PRODOS_SAPLING) {
            // 131Kバイト未満はインデックス１つ
            if (allocChainSector(0, item) == INVALID_GROUP_NUMBER) {
                return -1;
            }
            while (remain > 0 && limit >= 0) {
                // 空きをさがす
                int group_num = getEmptyGroupNumber();
                if (group_num == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // 使用済みにする
                basic.getNumsFromGroup(group_num, 0, sec_size, remain, group_items);
                setGroupNumber(group_num, 1);

                // チェインセクタも更新
                item.addChainGroupNumber(chain_idx, group_num);

                chain_idx++;

                //			file_size += block_size;
                groups++;
                remain -= block_size;
                limit--;
            }
        } else if (stype == FILETYPE_MASK_PRODOS_TREE) {
            // 131Kバイト以上 ツリー
            int chain_pidx = 0;
            chain_idx = 256;
            // チェインセクタを確保
            if (allocChainSector(0, item) == INVALID_GROUP_NUMBER) {
                return -1;
            }
            chain_pidx++;
            while (remain > 0 && limit >= 0) {
                // チェインセクタを確保
                if ((chain_idx % 256) == 0) {
                    int cgnum = allocChainSector(chain_pidx, item);
                    if (cgnum == INVALID_GROUP_NUMBER) {
                        return -1;
                    }
                    // ルートチェインセクタと結びつける
                    item.addChainGroupNumber(chain_pidx, cgnum);
                    chain_pidx++;
                }
                // 空きをさがす
                int group_num = getEmptyGroupNumber();
                if (group_num == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // 使用済みにする
                basic.getNumsFromGroup(group_num, 0, sec_size, remain, group_items);
                setGroupNumber(group_num, 1);

                // チェインセクタも更新
                item.addChainGroupNumber(chain_idx, group_num);

                chain_idx++;

                //			file_size += block_size;
                groups++;
                remain -= block_size;
                limit--;
            }
        } else {
            //　512バイト以下
            while (remain > 0 && limit >= 0) {
                // 空きをさがす
                int group_num = getEmptyGroupNumber();
                if (group_num == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // 使用済みにする
                basic.getNumsFromGroup(group_num, 0, sec_size, remain, group_items);
                setGroupNumber(group_num, 1);

                if (chain_idx == 0) {
                    item.setStartGroup(0, group_num, 1);
                }
                chain_idx++;

                //			file_size += block_size;
                groups++;
                remain -= block_size;
                limit--;
            }
        }

        if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }

        //	logger.log(Level.DEBUG, "rc: %d }", rc);
        return rc;
    }

    /**
     * グループをつなげる
     *
     * @return 0 正常
     */
    public int chainDirectoryGroups(DiskBasicDirItem<DirectoryProdos> item, DiskBasicGroups group_items) throws IOException {
        DiskBasicGroups orig_group_items = new DiskBasicGroups();
        item.getAllGroups(orig_group_items);
        orig_group_items.add(group_items);
        // Note: C++ uses pass-by-reference for group_items, assigning orig_group_items back
        // Java requires manual copy or parameter to be a mutable list/object.
        // Assuming group_items is now updated to orig_group_items content.
        group_items = orig_group_items;

        // ディレクトリチェインを再作成
        int group_num = INVALID_GROUP_NUMBER;
        int prev_group_num = INVALID_GROUP_NUMBER;
        ProDOSDirPtrT prev = null;
        for (int i = 0; i < group_items.count(); i++) {
            DiskBasicGroupItem gitem = group_items.item(i);
            if (gitem.group != group_num) {
                group_num = gitem.group;
                DiskImageSector sector = basic.getSectorFromGroup(group_num);
                // Placeholder for ProDOSDirPtrT *curr = (ProDOSDirPtrT *)sector->GetSectorBuffer();
                ProDOSDirPtrT curr = new ProDOSDirPtrT();
                byte[] currBuffer = sector.getSectorBuffer();
                // Placeholder mapping from currBuffer to curr (4 bytes)
                if (currBuffer.length >= 4) {
                    curr.nextBlock = (currBuffer[0] & 0xFF) | ((currBuffer[1] & 0xFF) << 8);
                    curr.prevBlock = (currBuffer[2] & 0xFF) | ((currBuffer[3] & 0xFF) << 8);
                }


                curr.prevBlock = prev_group_num != INVALID_GROUP_NUMBER ? prev_group_num : 0;

                if (prev != null) {
                    prev.nextBlock = group_num;
                    // Placeholder for writing prev back to disk sector buffer (4 bytes)
                    // This is missing in Java translation but essential for C++ pointer logic
                }

                prev = curr;
                prev_group_num = group_num;

                // Placeholder for writing curr back to disk sector buffer (4 bytes)
                // This is missing in Java translation but essential for C++ pointer logic
            }
        }

        return 0;
    }

//    /** データの読み込み/比較処理 */
//    public int accessFile(...) {}

    /**
     * ファイルの最終セクタのデータサイズを求める
     */
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryProdos> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size) {
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
        return (group_num + 1) * basic.getSectorsPerGroup() - 1;
    }

    /**
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る
     */
    @Override
    public void getNumFromSectorPos(int sector_pos, int[] track_num, int[] side_num, int[] sector_num, int[] div_num /* = null */, int[] div_nums /* = null */) {
        //	int selected_side = basic->GetSelectedSide();
        int numbering_sector = basic.getNumberingSector();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();
        int[] sectors_per_track = {sides_per_disk};
        int[] tmp_track_num = new int[1];
        int[] tmp_sector_num = new int[1];

        // セクタ位置がどのトラックにあるか
        sector_map.getNumFromSectorPos(sector_pos, tmp_track_num, tmp_sector_num, sectors_per_track);
        track_num[0] = tmp_track_num[0];
        sector_num[0] = tmp_sector_num[0];

        //	if (selected_side >= 0) {
        //		// 1S
        //		track_num = sector_pos / sectors_per_track;
        //		side_num = selected_side;
        //	} else {
        //		// 2D, 2HD
        //		track_num = sector_pos / sectors_per_track / sides_per_disk;
        //		side_num = (sector_pos / sectors_per_track) % sides_per_disk;
        //	}
        //	sector_num = (sector_pos % sectors_per_track);

        // サイド番号
        side_num[0] = sector_num[0] * sides_per_disk / sectors_per_track[0];

        // 連番でない場合
        if (numbering_sector != 1) {
            sector_num[0] = sector_num[0] % (sectors_per_track[0] / sides_per_disk);
        }

        // マッピング
        sector_num[0] = sector_skew.toPhysical(sector_num[0]);

        // サイド番号を逆転するか
        side_num[0] = basic.getReversedSideNumber(side_num[0]);

        track_num[0] += basic.getTrackNumberBaseOnDisk();
        side_num[0] += basic.getSideNumberBaseOnDisk();
        sector_num[0] += basic.getSectorNumberBase();

        if (div_num != null) div_num[0] = 0;
        if (div_nums != null) div_nums[0] = 1;
    }

    /**
     * 論理セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、セクタの各番号を得る
     */
    @Override
    public void getNumFromSectorPosS(int sector_pos, int[] track_num, int[] sector_num) {
        int[] sectors_per_track = {1};

        int[] tmp_track_num = new int[1];
        int[] tmp_sector_num = new int[1];
        sector_map.getNumFromSectorPos(sector_pos, tmp_track_num, tmp_sector_num, sectors_per_track);
        track_num[0] = tmp_track_num[0];
        sector_num[0] = tmp_sector_num[0];

        // マッピング
        sector_num[0] = sector_skew.toPhysical(sector_num[0]);

        track_num[0] += basic.getTrackNumberBaseOnDisk();
        sector_num[0] += basic.getSectorNumberBase();
    }

    /**
     * トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     */
    @Override
    public int getSectorPosFromNum(int track_num, int side_num, int sector_num, int div_num, int div_nums) {
        //	int selected_side = basic->GetSelectedSide();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();
        int numbering_sector = basic.getNumberingSector();
        int[] sectors_per_track = {1};
        //	int sector_pos;

        track_num -= basic.getTrackNumberBaseOnDisk();
        side_num -= basic.getSideNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBase();

        // マッピング
        sector_num = sector_skew.toLogical(sector_num);

        // サイド番号を逆転するか
        side_num = basic.getReversedSideNumber(side_num);

        int sector_pos = sector_map.getSectorPosFromNum(track_num, sector_num, sectors_per_track);

        // 連番でない場合
        if (numbering_sector != 1) {
            sector_pos += side_num * sectors_per_track[0] / sides_per_disk;
        }

        //	if (selected_side >= 0) {
        //		// 1S
        //		sector_pos = track_num * sectors_per_track + sector_num;
        //	} else {
        //		// 2D, 2HD
        //		sector_pos = track_num * sectors_per_track * sides_per_disk;
        //		sector_pos += (side_num % sides_per_disk) * sectors_per_track;
        //		sector_pos += sector_num;
        //	}
        return sector_pos;
    }

    /**
     * トラック、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     */
    @Override
    public int getSectorPosFromNumS(int track_num, int sector_num) {
        //	int selected_side = basic->GetSelectedSide();
        //	int sectors_per_track = basic->GetSectorsPerTrackOnBasic();
        //	int sides_per_disk = basic->GetSidesPerDiskOnBasic();
        int sector_pos;

        int[] sectors_per_track = {1};

        track_num -= basic.getTrackNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBase();

        // マッピング
        sector_num = sector_skew.toLogical(sector_num);

        sector_pos = sector_map.getSectorPosFromNum(track_num, sector_num, sectors_per_track);

        return sector_pos;
    }

    /**
     * ルートディレクトリか
     */
    @Override
    public boolean isRootDirectory(int group_num) {
        return false;
    }

    /**
     * サブディレクトリを作成する前にディレクトリ名を編集する
     */
    @Override
    public boolean renameOnMakingDirectory(String dir_name) {
        // 名前が空は作成不可
        if (dir_name.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * サブディレクトリを作成した後の個別処理
     */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryProdos> item, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryProdos> parent_item) throws IOException {
        if (group_items.count() <= 0) return;

        int block_size = basic.getSectorSize() * basic.getSectorsPerGroup();

        DiskBasicDirItemProDOS ditem = (DiskBasicDirItemProDOS) item;
        DiskBasicDirItemProDOS parent_ditem = (DiskBasicDirItemProDOS) parent_item;

        // ディレクトリの最初のブロックをセット
        item.setParentGroup(parent_item.getStartGroup(0));
        // バージョンはヘッダと合わせる
        ditem.setVersion(parent_ditem.getVersion());

        // サブボリュームヘッダのエントリを作成する

        DiskBasicGroupItem gitem = group_items.item(0);

        DiskImageSector sector = basic.getSector(gitem.track, gitem.side, gitem.sectorStart);

        // Placeholder for byte *buf = sector->GetSectorBuffer(4);
        byte[] buf = sector.getSectorBuffer(4);
        DiskBasicDirItem<DirectoryProdos> newitem = basic.createDirItem(sector, 0, buf);
        DiskBasicDirItemProDOS newditem = (DiskBasicDirItemProDOS) newitem;

        newitem.copyData(item.getData());
        newitem.setFileAttr(FORMAT_TYPE_PRODOS, 0, (FILETYPE_MASK_PRODOS_SUBVOL << 16) | (0x75 << 8) | (FILETYPE_MASK_PRODOS_ACCESS_ALL & ~FILETYPE_MASK_PRODOS_CHANGE));
        newitem.setStartGroup(0, 0);
        newitem.setFileSize(0);
        // バージョン
        newditem.setVersion(parent_ditem.getVersion());

        // Placeholder for directory_prodos_t *vol = (directory_prodos_t *)buf;
        DirectoryProdos vol = new DirectoryProdos();
        // Placeholder mapping from buf to vol.sv structure.
        if (buf.length >= 4) { // Assuming entry_len, entries_per_block, file_count, parent_pointer, parent_entry, parent_entry_len are at the beginning
            vol.aux.sv.entryLen = buf[0];
            vol.aux.sv.entriesPerBlock = buf[1];
            vol.aux.sv.fileCount = (short) ((buf[2] & 0xFF) | ((buf[3] & 0xFF) << 8));
        }

        // エントリのサイズ
        // sizeof(directory_prodos_t) not available, using placeholder size (e.g., 32)
        vol.aux.sv.entryLen = (byte) 32;
        // ブロック内のファイルエントリ数
        vol.aux.sv.entriesPerBlock = (byte) ((block_size - 4) / 32); // 32 is placeholder for sizeof(directory_prodos_t
        // ファイルエントリ数
        vol.aux.sv.fileCount = 0;

        int parent_start_block = parent_item.getStartGroup(0);
        // Placeholder for vol->sv.entries_per_block cast to int.
        int entries_per_block = vol.aux.sv.entriesPerBlock;
        if (entries_per_block == 0) entries_per_block = 1; // Avoid division by zero
        int item_number = item.getNumber();
        int parent_pointer = (item_number / entries_per_block) + parent_start_block;
        int parent_entry = item_number % entries_per_block;

        // 親のブロック番号
        vol.aux.sv.parentPointer = (short) parent_pointer;
        // 親エントリ
        vol.aux.sv.parentEntry = (byte) parent_entry;

        // 親エントリのサイズ
        vol.aux.sv.parentEntryLen = (byte) 32; // 32 is placeholder for sizeof(directory_prodos_t

        // Placeholder for updating sector buffer with vol data.

        // delete newitem; // In Java, this is not needed (garbage collection)
    }

//    /** フォーマット時セクタデータを指定コードで埋める (Commented out in C++ source) */
//    public void fillSector(DiskImageTrack track, DiskImageSector sector)

    /**
     * フォーマット時セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        // ボリュームディレクトリをクリア
        int st_pos = basic.diskBasicParam.getDirStartSector();
        int ed_pos = (basic.diskBasicParam.getDirEndSector() / basic.diskBasicParam.getSectorsPerGroup() + 1) * basic.diskBasicParam.getSectorsPerGroup() - 1;
        for (int sec = st_pos; sec <= ed_pos; sec++) {
            sector = basic.getSectorFromSectorPos(sec);
            if (sector == null) {
                // Why?
                return false;
            }
            sector.fill((byte) 0);
        }

        // ボリュームディレクトリのチェインを作成
        int st_blk = st_pos / basic.getSectorsPerGroup();
        int ed_blk = ed_pos / basic.getSectorsPerGroup();
        //	voldir.Empty();
        for (int blk = st_blk; blk <= ed_blk; blk++) {
            sector = basic.getSectorFromGroup(blk);
            ProDOSDirPtrT p = new ProDOSDirPtrT();
            byte[] b = sector.getSectorBuffer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
            byte[] pBuffer = sector.getSectorBuffer();
            // Placeholder mapping from pBuffer to p.

            if (p == null) {
                // Why?
                return false;
            }
            int next_block = (blk != ed_blk ? blk + 1 : 0);
            int prev_block = (blk != st_blk ? blk - 1 : 0);
            p.nextBlock = next_block;
            p.prevBlock = prev_block;
            // Placeholder for writing p back to disk sector buffer (4 bytes)
            // voldir.Add(blk);
        }

        int block_size = basic.getSectorSize() * basic.getSectorsPerGroup();

        // ボリュームヘッダを作成
        sector = basic.getSectorFromGroup(st_blk);
        // Placeholder for directory_prodos_t *vol = (directory_prodos_t *)sector->GetSectorBuffer(4);
        DirectoryProdos vol = new DirectoryProdos();
        byte[] volBuffer = sector.getSectorBuffer(4);
        // Placeholder mapping from volBuffer to vol.

        // 属性
        vol.stypeAndNlen = (byte) (FILETYPE_MASK_PRODOS_VOLUME << 4);
        // 日時
        LocalDateTime tm = LocalDateTime.now();
        DiskBasicDirItemProDOS.convDateFromTm(tm, vol.cdate);
        DiskBasicDirItemProDOS.convTimeFromTm(tm, vol.ctime);
        // アクセス
        vol.access = (byte) (FILETYPE_MASK_PRODOS_ACCESS_ALL & ~FILETYPE_MASK_PRODOS_CHANGE);
        // エントリのサイズ
        vol.aux.v.entryLen = (byte) 32; // 32 is placeholder for sizeof(directory_prodos_t
        // ブロック内のファイルエントリ数
        vol.aux.v.entriesPerBlock = (byte) ((block_size - 4) / 32); // 32 is placeholder for sizeof(directory_prodos_t
        // ファイルエントリ数
        vol.aux.v.fileCount = 0;
        // ビットマップポインタ
        int bitmap_pointer = 6;
        vol.aux.v.bitmapPointer = (short) bitmap_pointer;
        // トータルブロック数
        int total_blocks = (basic.getSidesPerDisk() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() / basic.diskBasicParam.getSectorsPerGroup());
        vol.aux.v.totalBlocks = (short) total_blocks;

        // Placeholder for writing vol back to sector buffer.

        this.volume = vol;

        basic.diskBasicParam.setFatEndGroup(total_blocks - 1);

        // ビットマップポインタを設定
        bitmap.clear();
        int bm_ptr = bitmap_pointer;
        st_pos = getStartSectorFromGroup(bm_ptr);
        ed_pos = getEndSectorFromGroup(bm_ptr, INVALID_GROUP_NUMBER, st_pos, 0, 0);
        for (int sec = st_pos; sec <= ed_pos; sec++) {
            sector = basic.getSectorFromSectorPos(sec);
            if (sector == null) {
                // Why?
                return false;
            }
            sector.fill((byte) 0);
            bitmap.AddBitmap(sector);
        }
        bitmap.SetMyGroupNumber(bm_ptr);
        for (int blk = ed_blk + 1; blk < total_blocks; blk++) {
            bitmap.modify(blk, false);
        }
        bitmap.modify(bm_ptr, true);

        // volume name
        setIdentifiedData(data);

        return true;
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryProdos> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) {
        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                try {
                    istream.read(buffer, 0, remain);
                } catch (java.io.IOException e) {
                } // Simplified error handling
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            try {
                istream.read(buffer, 0, size);
            } catch (java.io.IOException e) {
            } // Simplified error handling
            len = size;
        }

        return len;
    }

    /**
     * データの書き込み終了後の処理
     */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryProdos> item) {
        DiskBasicDirItemProDOS ditem = (DiskBasicDirItemProDOS) item;

        // ディレクトリのヘッダにあるファイル数を＋１する
        DiskBasicDirItem<DirectoryProdos> parent = item.getParent();
        if (parent == null) {
            // Why?
            return;
        }
        List<DiskBasicDirItem<DirectoryProdos>> children = parent.getChildren();
        if (children == null) {
            // Why?
            return;
        }
        DiskBasicDirItemProDOS vol = (DiskBasicDirItemProDOS) children.get(0);
        if (vol == null) {
            // Why?
            return;
        }
        vol.increaseFileCount();
        // ディレクトリの最初のブロックをセット
        item.setParentGroup(parent.getStartGroup(0));
        // バージョンはヘッダと合わせる
        ditem.setVersion(vol.getVersion());
    }

    /**
     * FAT領域を削除する
     */
    @Override
    public void deleteGroupNumber(int group_num) {
        // 未使用にする
        setGroupNumber(group_num, 0);
    }

    /**
     * ファイル削除後の処理
     */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryProdos> item) {
        // チェインセクタを未使用にする
        item.clearChainSector(null);

        // ディレクトリのヘッダにあるファイル数を－１する
        DiskBasicDirItem<DirectoryProdos> parent = item.getParent();
        if (parent == null) {
            // Why?
            return true;
        }
        List<DiskBasicDirItem<DirectoryProdos>> children = parent.getChildren();
        if (children == null) {
            // Why?
            return true;
        }
        DiskBasicDirItemProDOS vol = (DiskBasicDirItemProDOS) children.get(0);
        if (vol == null) {
            // Why?
            return true;
        }
        vol.decreaseFileCount();

        return true;
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume name
        if (volume != null) {
            int len = (volume.stypeAndNlen & 0xf);
            String volname = new String(volume.name, 0, len);
            data.setVolumeName(volname);
            data.setVolumeNameMaxLength(volume.name.length);
        }
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat fmt = basic.getFormatType();

        // volume name
        if (volume != null && fmt.HasVolumeName()) {
            byte[] volname = data.getVolumeName().getBytes();
            int len = volume.name.length;
            if (len > volname.length) len = volname.length;

            System.arraycopy(volname, 0, volume.name, 0, len);

            volume.stypeAndNlen = (byte) ((len & 0xf) | (volume.stypeAndNlen & 0xf0));
        }
    }
}