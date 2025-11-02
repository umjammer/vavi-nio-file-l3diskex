//
// Copyright (c) Sasaji. All rights reserved.
//

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemM68FDOS.DirectoryM68fdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.ByteUtil;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * Sord M68 FDOS (KDOS) の処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 *
 * @li IPLString : セクタ1のIPL
 */
public class DiskBasicTypeM68FDOS extends DiskBasicTypeMZBase<DirectoryM68fdos> {

    /** 使用状況テーブル */
    private final DiskBasicBitMLMap m_bitmap = new DiskBasicBitMLMap();

    public DiskBasicTypeM68FDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryM68fdos> dir) {
        super(basic, fat, dir);
    }

    /** 使用しているグループの位置を得る */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3);
    }

    /// FAT位置をセット
    @Override
    public void setGroupNumber(int num, int val) {
        m_bitmap.modify(num, val != 0);
    }

    /// FAT位置を返す
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /** 使用しているグループ番号か */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return m_bitmap.isSet(num);
    }

    /** 次のグループ番号を得る */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return num + 1;
    }

    /// 空きFAT位置を返す
    @Override
    public int getEmptyGroupNumber() {
        int found = DiskBasicType.INVALID_GROUP_NUMBER;
        for (int grp_num = 0; grp_num <= basic.getFatEndGroup(); grp_num++) {
            if (!isUsedGroupNumber(grp_num)) {
                found = grp_num;
                break;
            }
        }
        return found;
    }

    /// FATエリアをチェック
    ///
    /// @param is_formatting フォーマット中か
    /// @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
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
        group_items.clear();
        int dir_size = 0;
        int[] trk_num = {0};
        int[] sid_num = {0};
        int[] sec_num = {1};
        int[] div_num = {0};
        int[] div_nums = {1};
        int sec_pos = start_sector - 1;
        int end_sec_pos = basic.diskBasicParam.getFatEndGroup() * basic.diskBasicParam.getSectorsPerGroup();
        int max_dir_size = end_sec_pos * basic.getSectorSize();
        DiskImageSector sector = basic.getManagedSector(sec_pos, trk_num, sid_num, sec_num, div_num, div_nums);
        if (sector == null) return false;
        while (dir_size < max_dir_size) {
            group_items.add(sec_pos, 0, trk_num[0], sid_num[0], sec_num[0], sec_num[0], div_num[0], div_nums[0]);
            dir_size += (sector.getSectorSize() / div_nums[0]);

            // 次のセクタ番号を得る
            sec_pos = sector.get16(sector.getSectorSize() - 2, true) & 0xffff; // TODO -2
            if (sec_pos <= 0 || sec_pos > end_sec_pos) break;
            sector = basic.getSectorFromSectorPos(sec_pos, trk_num, sid_num, div_num, div_nums);
            if (sector == null) break;
            sec_num[0] = sector.getSectorNumber();
        }

        group_items.setSize(dir_size);
        return true;
    }

    /**
     * ディレクトリエリアのサイズに達したらアサイン終了するか
     *
     * @param pos         [in,out] ディレクトリの位置
     * @param size        [in,out] ディレクトリのセクタサイズ
     * @param size_remain [in,out] ディレクトリの残りサイズ
     * @return 0: 終了しない, 1: 強制的に未使用とする アサインは継続, -1: 現グループでアサイン終了。次のグループから継続, -2: 強制的にアサイン終了する
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        return pos[0] + DirectoryM68fdos.SIZE > size[0] ? -1 : 0;
    }

    /// ディレクトリアサインでセクタ毎に位置を調整する
    ///
    /// @return 調整後のディレクトリの位置
    /// @param pos ディレクトリの位置
    @Override
    public int adjustPositionAssigningDirectory(int pos) {
        return 0;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.diskBasicParam.getFatEndGroup() + 1 - dataStartGroup;
        disk_size[0] = group_size[0] * basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup();
    }

    /// 残りディスクサイズを計算
    ///
    /// @param wrote 書込み操作を行った後か
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        //int used = 0;
        fatAvailability.empty();

        // 使用済みかチェック
        int grps = 0;
        FatAvailability fsts;
        for (int gnum = 0; gnum <= basic.diskBasicParam.getFatEndGroup(); gnum++) {
            if (gnum < dataStartGroup) {
                //used++;
                fsts = FAT_AVAIL_SYSTEM;
            } else if (!isUsedGroupNumber(gnum)) {
                grps++;
                fsts = FAT_AVAIL_FREE;
            } else {
                //used++;
                fsts = FAT_AVAIL_USED;
            }
            fatAvailability.add(fsts, 0, 0);
        }

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectoryM68fdos>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (int idx = 0; idx < items.size(); idx++) {
                DiskBasicDirItem<DirectoryM68fdos> item = items.get(idx);
                if (item == null || !item.isUsed()) continue;

                // グループ番号のマップを調べる
                int gcnt = item.getGroupCount();
                if (gcnt > 0) {
                    DiskBasicGroupItem gitem = item.getGroup(gcnt - 1);
                    int gnum = gitem.group;
                    if (gnum <= basic.diskBasicParam.getFatEndGroup()) {
                        fatAvailability.set(gnum, FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        int fsize = grps * basic.diskBasicParam.getSectorsPerGroup() * basic.getSectorSize();

        fatAvailability.setFreeSize(fsize);
        fatAvailability.setFreeGroups(grps);
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
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryM68fdos> pitem, DiskBasicDirItem<DirectoryM68fdos> nitem, DiskBasicError errinfo) {
        return true;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileunit_num ファイル番号
     * @param item         [in,out] ディレクトリアイテム
     * @param size    確保するデータサイズ（バイト）
     * @param flags        新規か追加か
     * @param group_items  [out] 確保したセクタリスト
     * @return >0:正常 -1:空きなし(開始グループ設定前) -2:空きなし(開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryM68fdos> item, int size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) throws IOException {
        int[] file_size = {0};
        int[] groups = {0};

        int rc = 0;
        int remain = size;
        // セクタ末尾に次のセクタ番号を入れるか
        boolean is_chain = item.needChainInData();
        int sec_size = basic.getSectorSize();
        if (is_chain) {
            sec_size -= 2;
        }

        // 必要なグループ数
        int group_size = ((size - 1) / sec_size / basic.diskBasicParam.getSectorsPerGroup()) + 1;
        if (is_chain) {
            group_size = 1;
        }

        // 未使用が連続している位置をさがす
        int[] group_start = {DiskBasicType.INVALID_GROUP_NUMBER};
        int cnt = findContinuousArea(group_size, group_start);
        if (cnt < group_size) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // データの開始グループ決定
        item.setStartGroup(fileunit_num, group_start[0]);

        // 領域を確保する
        rc = allocateGroupsSub(item, group_start[0], remain, sec_size, group_items[0], file_size, groups);

        // 確保したグループ数をセット
        item.setGroupSize(groups[0]);
        // 最終グループをセット
        item.setExtraGroup(group_items[0].last().group);

        return rc;
    }

    /** グループを確保して使用中にする */
    @Override
    public int allocateGroupsSub(DiskBasicDirItem<DirectoryM68fdos> item, int group_start, int remain, int sec_size, DiskBasicGroups group_items, int[] file_size, int[] groups) {
        int rc = 0;
        int group_num = group_start;
        int prev_group = 0;

        //	DiskBasicDirItemM68FDOS ditem = (DiskBasicDirItemM68FDOS)item;

        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
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
                    file_size[0] += (basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup());
                    groups[0]++;
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
            basic.getNumsFromGroup(prev_group, 0, sec_size, remain, group_items);
            setGroupNumber(prev_group, 1);
            file_size[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
            groups[0]++;
        }
        if (prev_group > basic.diskBasicParam.getFatEndGroup()) {
            // ファイルがオーバフローしている
            rc = -2;
        } else if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }
        return rc;
    }

    /// セクタデータを埋めた後の個別処理
    /// フォーマット FAT予約済みをセット
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
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
                case 0 -> {
                    // first sector
                    sector.copy(new byte[] {0x00, 0x3f}, 2);
                }
                case 1 -> {
                    // set bits

                    int n = mng_sec_pos + basic.diskBasicParam.getDirEndSector();
                    int len = (n >> 3);
                    sector.fill((byte) 0xff, len, 0);
                    int mod = (n & 7);
                    sector.fill((byte) ((0xff00 >> mod) & 0xff), 1, len);
                }
                case 2 -> {
                    // set bits

                    int n = basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getTracksPerSideOnBasic() * basic.diskBasicParam.getSectorsPerTrackOnBasic();
                    n -= (sector.getSectorSize() * (pos - 1) * 8);
                    if (n >= 0 && n < (sector.getSectorSize() * 8)) {
                        int len = (n >> 3);
                        sector.fill((byte) 0xff, -1, len);
                        int mod = (n & 7);
                        sector.fill((byte) (0x00ff >> mod), 1, len);
                    }
                }
                default -> {}
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
     * @param istream       [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時はnull
     * @param ostream       [in,out] 出力先 データ読み出し時に使用 ベリファイ時はnull
     * @param sector_buffer セクタバッファ
     * @param sector_size   バッファサイズ
     * @param remain_size   残りサイズ
     * @param sector_num    セクタ番号
     * @param sector_end    最終セクタ番号
     * @return >=0: 処理したサイズ, -1:比較不一致, -2:セクタがおかしい
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryM68fdos> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws IOException {
        boolean need_chain = item.needChainInData();

        if (need_chain) {
            // セクタの最終バイトはチェイン用セクタ番号がある
            sector_size -= 2;
        }

        int size = remain_size < sector_size ? remain_size : sector_size;

        if (ostream != null) {
            // Write to output stream
            temp.setData(sector_buffer, size, basic.isDataInverted());
            ostream.write(temp.getData(), 0, size);
        }
        if (istream != null) {
            // Read from input stream and compare
            temp.setSize(size);
            istream.read(temp.getData(), 0, size);
            temp.invertData(basic.isDataInverted());

            if (!Arrays.equals(temp.getData(), 0, size, sector_buffer, 0, size)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /**
     * データの書き込み処理
     *
     * @param item       ディレクトリアイテム
     * @param istream    ストリームデータ
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
    public int writeFile(DiskBasicDirItem<DirectoryM68fdos> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        boolean need_chain = item.needChainInData();

        int len = 0;
        if (need_chain) {
            size -= 2;
        }

        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                istream.read(buffer, 0, remain);
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
        }

        // 次のセクタ番号を書く
        if (need_chain) {
            int next_sector = next_group * basic.getSectorsPerGroup();
            if (next_sector >= 0) {
                // bigendien
                buffer[size] = (byte) ((next_sector >> 8) & 0xff);
                buffer[size + 1] = (byte) (next_sector & 0xff);
            }
        }

        return len;
    }

    /** データの書き込み終了後の処理 */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryM68fdos> item) {
//        DiskBasicDirItemM68FDOS ditem = (DiskBasicDirItemM68FDOS) item;
//        ditem.setUnknownData();
    }

    /// IPLや管理エリアの属性を得る
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
    }

    /// IPLや管理エリアの属性をセット
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
    }
}