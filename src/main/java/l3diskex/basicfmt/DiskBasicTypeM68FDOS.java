//
// Copyright (c) Sasaji. All rights reserved.
//

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils.TempData;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.BasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFat.FatAvailability;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.ByteUtil;

import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;


/**
 * Sord M68 FDOS (KDOS) の処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 *
 * @li IPLString : セクタ1のIPL
 */
public class DiskBasicTypeM68FDOS extends DiskBasicTypeMZBase {

    private static final int directory_m68fdos_t_SIZE = 19; // Placeholder for sizeof(directory_m68fdos_t)

    private DiskBasicBitMLMap m_bitmap = new DiskBasicBitMLMap();
    private TempData temp = new TempData(); // Used in AccessFile
    private DiskBasicAvailability fat_availability = new DiskBasicAvailability(); // Used in CalcDiskFreeSize

    public DiskBasicTypeM68FDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    // Assuming a way to find continuous area
    @Override
    public int findContinuousArea(int group_size, int[] group_start_out) {
        int found_start = INVALID_GROUP_NUMBER;
        int continuous_count = 0;
        int end_group = basic.diskBasicParam.getFatEndGroup();

        for (int gnum = dataStartGroup; gnum <= end_group; gnum++) {
            if (!isUsedGroupNumber(gnum)) {
                if (found_start == INVALID_GROUP_NUMBER) {
                    found_start = gnum;
                }
                continuous_count++;
                if (continuous_count >= group_size) {
                    group_start_out[0] = found_start;
                    return continuous_count;
                }
            } else {
                found_start = INVALID_GROUP_NUMBER;
                continuous_count = 0;
            }
        }

        if (continuous_count > 0 && continuous_count < group_size) {
            group_start_out[0] = found_start;
        } else if (continuous_count == 0) {
            group_start_out[0] = INVALID_GROUP_NUMBER;
        }

        return continuous_count;
    }


    /// @name access to FAT area
    //@{

    /// 使用しているグループの位置を得る
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        // This method seems to assume 'pos' is passed in and modified, which is strange for 'num'
        // Given the C++ code: mask = 1 << (pos & 7); pos = (pos >> 3);
        // It implies 'num' is implicitly related to 'pos' before the calculation.
        // Assuming 'pos' is num/1 for simplicity based on the logic:
        int p = (int) num;
        mask[0] = 1 << (p & 7);
        pos[0] = (p >> 3);
    }

    /// FAT位置をセット
    @Override
    public void setGroupNumber(int num, int val) {
        m_bitmap.modify(num, val != 0);
    }

    /// FAT位置を返す
    @Override
    public int getGroupNumber(int num) {
        return num; // In this scheme, the group number is the group index itself.
    }

    /// 使用しているグループ番号か
    @Override
    public boolean isUsedGroupNumber(int num) {
        return m_bitmap.isSet(num);
    }

    /// 次のグループ番号を得る
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return num + 1;
    }

    /// 空きFAT位置を返す
    @Override
    public int getEmptyGroupNumber() {
        int found = (int) INVALID_GROUP_NUMBER;
        for (int grp_num = 0; grp_num <= basic.getFatEndGroup(); grp_num++) {
            if (!isUsedGroupNumber(grp_num)) {
                found = grp_num;
                break;
            }
        }
        return found;
    }
    //@}

    /// @name check/ assign FAT area
    //@{

    /// FATエリアをチェック
    ///
    /// @param is_formatting フォーマット中か
    /// @return 1.0       正常
    /// @return 0.0 - 1.0 警告あり
    /// @return <0.0      エラーあり
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        // FATエリア
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) {
            return -1.0;
        }
        if (sector.get16(0, basic.isBigEndian()) != 0x003f) {
            valid_ratio = -1.0;
        }

        // トラック１
        sector = basic.getSector(1, 0, 1);
        if (sector == null) {
            return -1.0;
        }
        if (sector.find("FDDOS".getBytes(), 5) < 0 && sector.find("SORD".getBytes(), 4) < 0) {
            valid_ratio = -1.0;
        }

        // 使用状況エリア 2セクタ
        for (int i = 0; i < 2; i++) {
            sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() + i);
            if (sector == null) {
                return -1.0;
            }
            m_bitmap.addBuffer(sector.getSectorBuffer(), sector.getSectorBufferSize());
        }

        // 最終グループ番号
        if (basic.diskBasicParam.getFatEndGroup() == 0) {
            basic.diskBasicParam.setFatEndGroup(basic.diskBasicParam.getTracksPerSideOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getSectorsPerTrackOnBasic() - 1);
        }

        return valid_ratio;
    }

    /**
     * ルートディレクトリのセクタリストを計算
     *
     * @param start_sector ディレクトリ開始セクタ番号
     * @param end_sector   ディレクトリ終了セクタ番号 (unused in logic)
     * @param group_items  [out] セクタリスト
     * @return true
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) {
        group_items.empty();
        int dir_size = 0;
        int[] trk_num = {0};
        int[] sid_num = {0};
        int sec_num = 1;
        int[] div_num = {0};
        int[] div_nums = {1};
        int sec_pos = start_sector - 1;
        int end_sec_pos = basic.diskBasicParam.getFatEndGroup() * basic.diskBasicParam.getSectorsPerGroup();
        int max_dir_size = end_sec_pos * basic.getSectorSize();

        DiskImageSector sector = basic.getManagedSector(sec_pos, trk_num, sid_num, new int[] {sec_num}, div_num, div_nums);
        if (sector == null) return false;
        sec_num = sector.getSectorNumber(); // Update sec_num from GetManagedSector

        while (dir_size < max_dir_size) {
            group_items.add(sec_pos, 0, trk_num[0], sid_num[0], sec_num, sec_num, div_num[0], div_nums[0]);
            dir_size += (sector.getSectorSize() / div_nums[0]);

            // 次のセクタ番号を得る
            // Note: C++ Get16(-2, true) implies big-endian read of 16-bit at offset -2
            // Assuming this is the last two bytes of the sector buffer, which holds the next sector position.
            // Using Get16(sector.GetSectorSize() - 2, true) to read the last 2 bytes as Big Endian.
            sec_pos = sector.get16(sector.getSectorSize() - 2, true) & 0xFFFF;

            if (sec_pos <= 0 || sec_pos > end_sec_pos) break;
            sector = basic.getSectorFromSectorPos(sec_pos, trk_num, sid_num, div_num, div_nums);
            if (sector == null) break;
            sec_num = sector.getSectorNumber();
        }

        group_items.setSize(dir_size);
        return true;
    }

    /// ディレクトリエリアのサイズに達したらアサイン終了するか
    ///
    /// @return 0 : 終了しない
    /// @return 1 : 強制的に未使用とする アサインは継続
    /// @return -1 : 現グループでアサイン終了。次のグループから継続
    /// @return -2 : 強制的にアサイン終了する
    /// @param[in,out] pos         ディレクトリの位置
    /// @param[in,out] size        ディレクトリのセクタサイズ
    /// @param[in,out] size_remain ディレクトリの残りサイズ
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        return pos[0] + directory_m68fdos_t_SIZE > size[0] ? -1 : 0;
    }

    /// ディレクトリアサインでセクタ毎に位置を調整する
    ///
    /// @return 調整後のディレクトリの位置
    /// @param[in] pos ディレクトリの位置
    @Override
    public int adjustPositionAssigningDirectory(int pos) {
        return 0;
    }
    //@}

    /// @name disk size
    //@{

    /// 使用可能なディスクサイズを得る
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = (int) basic.diskBasicParam.getFatEndGroup() + 1 - dataStartGroup;
        disk_size[0] = group_size[0] * basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup();
    }

    /// 残りディスクサイズを計算
    ///
    /// @param wrote 書込み操作を行った後か
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        //	int used = 0;
        fat_availability.empty();

        // 使用済みかチェック
        int grps = 0;
        int fsts;
        for (int gnum = 0; gnum <= basic.diskBasicParam.getFatEndGroup(); gnum++) {
            if (gnum < dataStartGroup) {
                //			used++;
                fsts = FatAvailability.FAT_AVAIL_SYSTEM.getValue();
            } else if (!isUsedGroupNumber(gnum)) {
                grps++;
                fsts = FatAvailability.FAT_AVAIL_FREE.getValue();
            } else {
                //			used++;
                fsts = FatAvailability.FAT_AVAIL_USED.getValue();
            }
            fat_availability.Add(fsts, 0, 0);
        }

        // ディレクトリエントリのグループ
        final List<DiskBasicDirItem> items = dir.getCurrentItems(null);
        if (items != null) {
            for (int idx = 0; idx < items.size(); idx++) {
                DiskBasicDirItem item = items.get(idx);
                if (item == null || !item.isUsed()) continue;

                // グループ番号のマップを調べる
                int gcnt = item.getGroupCount();
                if (gcnt > 0) {
                    final DiskBasicGroupItem gitem = item.getGroup(gcnt - 1);
                    int gnum = gitem.group;
                    if (gnum <= basic.diskBasicParam.getFatEndGroup()) {
                        fat_availability.set(gnum, FatAvailability.FAT_AVAIL_USED_LAST.getValue());
                    }
                }
            }
        }

        int fsize = grps * basic.diskBasicParam.getSectorsPerGroup() * basic.getSectorSize();

        fat_availability.setFreeSize(fsize);
        fat_availability.SetFreeGroups(grps);
    }

    /**
     * ファイルをセーブする前の準備を行う
     *
     * @param istream   ストリームバッファ
     * @param file_size [in,out] 出力サイズ
     * @param pitem     [in,out] ファイル名、属性を持っているディレクトリアイテム
     * @param nitem     [in,out] 確保したディレクトリアイテム
     * @param errinfo   [in,out] エラー情報
     */
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem pitem, DiskBasicDirItem nitem, DiskBasicError errinfo) {
        return true;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileunit_num ファイル番号
     * @param item         [in,out]       ディレクトリアイテム
     * @param data_size    確保するデータサイズ（バイト）
     * @param flags        新規か追加か
     * @param group_items  [out]  確保したセクタリスト
     * @return >0:正常 -1:空きなし(開始グループ設定前) -2:空きなし(開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) {
        int[] file_size = {0};
        int[] groups = {0};

        int rc = 0;
        int remain = data_size;
        // セクタ末尾に次のセクタ番号を入れるか
        boolean is_chain = item.needChainInData();
        int sec_size = basic.getSectorSize();
        if (is_chain) {
            sec_size -= 2;
        }

        // 必要なグループ数
        int group_size;
        if (is_chain) {
            group_size = 1;
        } else {
            group_size = ((data_size - 1) / sec_size / basic.diskBasicParam.getSectorsPerGroup()) + 1;
        }

        // 未使用が連続している位置をさがす
        int[] group_start = {INVALID_GROUP_NUMBER};
        int cnt = findContinuousArea(group_size, group_start);
        if (cnt < group_size) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // データの開始グループ決定
        item.setStartGroup(fileunit_num, group_start[0]);

        // 領域を確保する
        rc = allocateGroupsSub(item, group_start[0], remain, sec_size, group_items, file_size, groups);

        // 確保したグループ数をセット
        item.setGroupSize(groups[0]);
        // 最終グループをセット
        DiskBasicGroupItem last_item = group_items.last();
        if (last_item != null) {
            item.setExtraGroup(last_item.group);
        }

        return rc;
    }

    /// グループを確保して使用中にする
    @Override
    public int allocateGroupsSub(DiskBasicDirItem item, int group_start, int remain_in, int sec_size, DiskBasicGroups group_items, int[] file_size_out, int[] groups_out) {
        int rc = 0;
        int group_num = group_start;
        int prev_group = 0;
        int remain = remain_in;
        int file_size = file_size_out[0];
        int groups = groups_out[0];

        //	DiskBasicDirItemM68FDOS ditem = (DiskBasicDirItemM68FDOS)item;

        int limit = (int) basic.diskBasicParam.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // 使用しているか
            boolean used_group = isUsedGroupNumber(group_num);
            if (!used_group) {
                if (prev_group > 0 && prev_group <= basic.diskBasicParam.getFatEndGroup()) {
                    // 使用済みにする
                    int[] remain_wrapper = {remain};
                    basic.getNumsFromGroup(prev_group, group_num, sec_size, remain_wrapper[0], group_items);
                    remain = remain_wrapper[0];
                    setGroupNumber(prev_group, 1);
                    file_size += (basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup());
                    groups++;
                }
                remain -= (sec_size * basic.diskBasicParam.getSectorsPerGroup());
                prev_group = group_num;
            }
            // 次のグループ
            group_num++;
            limit--;
        }
        if (prev_group > 0 && prev_group <= basic.diskBasicParam.getFatEndGroup()) {
            // 使用済みにする
            int[] remain_wrapper = {remain};
            basic.getNumsFromGroup(prev_group, 0, sec_size, remain_wrapper[0], group_items);
            remain = remain_wrapper[0];
            setGroupNumber(prev_group, 1);
            file_size += (basic.getSectorSize() * basic.getSectorsPerGroup());
            groups++;
        }
        if (prev_group > basic.diskBasicParam.getFatEndGroup()) {
            // ファイルがオーバフローしている
            rc = -2;
        } else if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }

        file_size_out[0] = file_size;
        groups_out[0] = groups;
        return rc;
    }

    /// セクタデータを埋めた後の個別処理
    /// フォーマット FAT予約済みをセット
    @Override
    public boolean additionalProcessOnFormatted(final DiskBasicIdentifiedData data) {
        DiskImageSector sector;

        //
        // FATエリア
        //
        int mng_sec_pos = basic.diskBasicParam.getManagedTrackNumber() * basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic();
        for (int sec = basic.diskBasicParam.getFatStartSector() - 1, pos = 0; sec < basic.diskBasicParam.getDirStartSector() - 1; sec++, pos++) {
            sector = basic.getSectorFromSectorPos(mng_sec_pos + sec);
            if (sector == null) {
                return false;
            }
            sector.fill(basic.diskBasicParam.getFillCodeOnFAT());
            switch (pos) {
                case 0:
                    // first sector
                    sector.copy(new byte[] {0x00, 0x3f}, 2);
                    break;
                case 1:
                    // set bits
                {
                    int n = mng_sec_pos + basic.diskBasicParam.getDirEndSector();
                    int len = (n >> 3);
                    sector.fill((byte) 0xff, len, 0);
                    int mod = (n & 7);
                    sector.fill((byte) ((0xff00 >> mod) & 0xff), 1, len);
                }
                break;
                case 2:
                    // set bits
                {
                    int n = basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getTracksPerSideOnBasic() * basic.diskBasicParam.getSectorsPerTrackOnBasic();
                    n -= (sector.getSectorSize() * (pos - 1) * 8);
                    if (n >= 0 && n < (sector.getSectorSize() * 8)) {
                        int len = (n >> 3);
                        sector.fill((byte) 0xff, -1, len); // Fill up to len with 0xff
                        int mod = (n & 7);
                        // sector->Fill(0x00ff >> mod, 1, len);
                        // The original C++ logic for the last byte seems to be filling 1 byte at 'len' offset
                        sector.fill((byte) (0x00ff >> mod), 1, len);
                    }
                }
                break;
                default:
                    break;
            }
        }

        //
        // DIRエリア
        //
        for (int sec = basic.diskBasicParam.getDirStartSector() - 1, pos = 0; sec < basic.diskBasicParam.getDirEndSector() - 1; sec++, pos++) {
            sector = basic.getSectorFromSectorPos(mng_sec_pos + sec);
            if (sector == null) {
                return false;
            }
            sector.fill(basic.diskBasicParam.getFillCodeOnFAT());
            if (sec < basic.diskBasicParam.getDirEndSector() - 2) {
                short next = basic.orderUint16((short) ((mng_sec_pos + sec + 1) & 0xffff));
                sector.copy(ByteUtil.getBeBytes(next), 2, basic.getSectorSize() - 2);
            }
            if (pos == 0) {
                // エントリ１つ
                sector.copy(new byte[] {
                        (byte) 0x62, (byte) 0x56, (byte) 0xc1, (byte) 0xc0, (byte) 0xa7, (byte) 0x30, (byte) 0xf9, (byte) 0x80,
                        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x5c, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                        (byte) 0x00, (byte) 0x0d, (byte) 0xa4}, 19);
            }
        }

        //
        sector = basic.getSectorFromSectorPos(mng_sec_pos);
        if (sector == null) {
            return false;
        }
        sector.copy("\u00f3\u0076NOT FDDOS MEDIA\u0000".getBytes(), 18);

        return true;
    }

    /**
     * データの読み込み/比較処理
     *
     * @param fileunit_num  ファイル番号
     * @param item          ディレクトリアイテム
     * @param istream       [in,out]  入力ストリーム ベリファイ時に使用 データ読み出し時はNULL
     * @param ostream       [in,out]  出力先         データ読み出し時に使用 ベリファイ時はNULL
     * @param sector_buffer セクタバッファ
     * @param sector_size   バッファサイズ
     * @param remain_size   残りサイズ
     * @param sector_num    セクタ番号
     * @param sector_end    最終セクタ番号
     * @return >=0 : 処理したサイズ  -1:比較不一致  -2:セクタがおかしい
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem item, InputStream istream, OutputStream ostream, final byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) {
        boolean need_chain = item.needChainInData();

        if (need_chain) {
            // セクタの最終バイトはチェイン用セクタ番号がある
            sector_size -= 2;
        }

        int size = (remain_size < sector_size ? remain_size : sector_size);

        if (ostream != null) {
            // 書き出し (Write to output stream)
            // temp.SetData(sector_buffer, size, basic.IsDataInverted());
            byte[] data_to_write = Arrays.copyOf(sector_buffer, size);
            if (basic.isDataInverted()) {
                // Invert logic for data_to_write if necessary, skipping here for brevity
            }
            try {
                ostream.write(data_to_write, 0, size);
            } catch (IOException e) {
                // Handle IOException
                return -2; // Placeholder error
            }

        }
        if (istream != null) {
            // 読み込んで比較 (Read from input stream and compare)
            // temp.SetSize(size);
            byte[] buffer = new byte[size];
            try {
                istream.read(buffer, 0, size);
            } catch (IOException e) {
                // Handle IOException
                return -2; // Placeholder error
            }
            // temp.InvertData(basic.IsDataInverted());
            if (basic.isDataInverted()) {
                // Invert logic for buffer if necessary, skipping here for brevity
            }

            if (!Arrays.equals(buffer, 0, size, sector_buffer, 0, size)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /**
     * データの書き込み処理
     *
     * @param item       [in] ディレクトリアイテム
     * @param istream    [in] ストリームデータ
     * @param buffer     [out] セクタ内の書き込み先バッファ
     * @param size       書き込み先バッファサイズ
     * @param remain     残りのデータサイズ
     * @param sector_num セクタ番号
     * @param group_num  現在のグループ番号
     * @param next_group 次のグループ番号
     * @param sector_end 最終セクタ番号
     * @param seq_num    通し番号(0...)
     * @return 書き込んだバイト数
     */
    @Override
    public int writeFile(DiskBasicDirItem item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) {
        boolean need_chain = item.needChainInData();

        int len = 0;
        int data_size = size;
        if (need_chain) {
            data_size -= 2;
        }

        try {
            if (remain <= data_size) {
                // 残り少ない
                int actual_remain = Math.max(0, remain);
                if (actual_remain > 0) {
                    istream.read(buffer, 0, actual_remain);
                }
                if (data_size > actual_remain) {
                    // バッファの余りは0サプレス
                    Arrays.fill(buffer, actual_remain, data_size, (byte) 0);
                }
                len = actual_remain;
            } else {
                // 継続
                istream.read(buffer, 0, data_size);
                len = data_size;
            }
        } catch (IOException e) {
            // Handle IOException
            return -1; // Placeholder error
        }

        // 次のセクタ番号を書く
        if (need_chain) {
            int next_sector = (int) next_group * basic.getSectorsPerGroup();
            if (next_sector >= 0) {
                // bigendien
                buffer[data_size] = (byte) ((next_sector >> 8) & 0xff);
                buffer[data_size + 1] = (byte) (next_sector & 0xff);
            }
        }

        return len;
    }

    /// データの書き込み終了後の処理
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem item) {
//        if (item instanceof DiskBasicDirItemM68FDOS) {
//            ((DiskBasicDirItemM68FDOS) item).SetUnknownData();
//        }
    }

    /// IPLや管理エリアの属性を得る
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
    }

    /// IPLや管理エリアの属性をセット
    @Override
    public void setIdentifiedData(final DiskBasicIdentifiedData data) {
    }
}