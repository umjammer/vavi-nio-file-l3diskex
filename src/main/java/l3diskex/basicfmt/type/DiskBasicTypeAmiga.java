///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicParam;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.AmigaBlockPre;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.AmigaFileDataPre;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.AmigaRootBlockPost;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.DirectoryAmiga;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicTemplates.gDiskBasicTemplates;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_DATA;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_HEADER;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_ROOT;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.KEY_FAST_FILE_SYSTEM;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.KEY_INTERNATIONAL;


/**
 Amiga DOS の処理

 DiskBasicParam
 @li FastFileSystem FFSかどうか(bool)
 */
public class DiskBasicTypeAmiga extends DiskBasicType<DirectoryAmiga> {

    private static final Logger logger = System.getLogger(DiskBasicTypeAmiga.class.getName());

    /** Amiga Boot Block (all Big Endien) */
    @Serdes
    static class AmigaBootBlock {

        public static final int SIZE = 512;

        /** "DOS", byte4 is type: bit0 0:OFS 1:FFS */
        @Element(sequence = 1)
        byte[] type = new byte[4];
        @Element(sequence = 2)
        int check_sum;
        /** number of root block */
        @Element(sequence = 3)
        int root_block;
        /** boot program */
        @Element(sequence = 4)
        byte[] program = new byte[500];
    }

    /** Amiga Bitmap Block (all Big Endien) */
    @Serdes
    static class AmigaBitmapBlock {

        @Element(sequence = 1)
        int check_sum;
        /** block size - 4 */
        @Element(sequence = 2)
        int[] map;
    }

    /** AMIGA ビットマップ 1つ */
    static class AmigaOneBitmap {

        int m_block_num;
        int m_block_size;
        AmigaBitmapBlock m_map;

        /// 指定位置のビットを変更する
        /// @param block_num  ブロック番号(2..)
        /// @param use セットする場合true
        public void modify(int block_num, boolean use) {
            int pos = block_num >> 5;
            int bit = block_num & 0x1f;
            int dat = (1 << bit);
            if (use) {
                m_map.map[pos] &= ~dat;
            } else {
                m_map.map[pos] |= dat;
            }
        }

        /// 指定位置が空いているか
        /// @param block_num  ブロック番号(2..)
        /// @return 空いている場合 true
        public boolean isFree(int block_num) {
            int pos = block_num >> 5;
            int bit = block_num & 0x1f;
            int dat = (1 << bit);
            return ((m_map.map[pos] & dat) != 0);
        }

        /// 指定ブロックまですべて未使用にする
        /// @param block_num 最終ブロック番号
        public void freeAll(int block_num) {
            if (block_num >= getBlockNums()) {
                block_num = getBlockNums() - 1;
            }
            int pos = block_num >> 5;
            int bit = block_num & 0x1f;
            for (int p = 0; p < pos; p++) {
                m_map.map[p] = 0xffff_ffff;
            }
            int dat = ((1 << (bit + 1)) - 1);
            m_map.map[pos] = dat;
        }

        /** ブロック数を返す */
        public int getBlockNums() {
            return (m_block_size - 4) * 8;
        }

        /** チェックサムの更新 */
        public void updateCheckSum() {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Serdes.Util.serialize(m_map, baos);
                m_map.check_sum = DiskBasicTypeAmiga.calcCheckSumOnBootBlock(baos.toByteArray(), m_block_size);
            } catch (Exception e) {
logger.log(Level.TRACE, e.getMessage());
            }
        }

        public int getBlockNumber() {
            return m_block_num;
        }
    }

    static class AmigaBitmap {

        List<AmigaOneBitmap> list = new ArrayList<>();

        /// ビットマップを追加
        /// @param block_num  ブロック番号
        /// @param mapBuffer  マップのあるバッファ
        /// @param block_size バッファサイズ
        public void addBitmap(int block_num, byte[] mapBuffer, int block_size) throws IOException {
            AmigaBitmapBlock b = new AmigaBitmapBlock();
            Serdes.Util.deserialize(new ByteArrayInputStream(mapBuffer), mapBuffer);
            AmigaOneBitmap o = new AmigaOneBitmap();
            o.m_block_num = block_num;
            o.m_block_size = block_size;
            o.m_map = b;
            list.add(o);
        }

        /// 指定位置のビットを変更する
        /// @param block_num  ブロック番号(2..)
        /// @param use セットする場合true
        public void modify(int block_num, boolean use) {
            if (block_num < 2) return;

            block_num -= 2;
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                int itemBlockNums = item.getBlockNums();
                if (block_num < itemBlockNums) {
                    item.modify(block_num, use);
                    break;
                }
                block_num -= itemBlockNums;
            }
        }

        /// 指定位置が空いているか
        /// @param block_num  ブロック番号(2..)
        /// @return 空いている場合 true
        public boolean isFree(int block_num) {
            if (block_num < 2) return false;

            block_num -= 2;
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                int itemBlockNums = item.getBlockNums();
                if (block_num < itemBlockNums) {
                    return item.isFree(block_num);
                }
                block_num -= itemBlockNums;
            }
            return false;
        }

        /// 指定ブロックまですべて未使用にする
        /// @param block_num 最終ブロック番号
        public void freeAll(int block_num) {
            if (block_num < 2) return;

            block_num -= 2;
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                int itemBlockNums = item.getBlockNums();
                if (block_num < itemBlockNums) {
                    item.freeAll(block_num);
                } else {
                    item.freeAll(0xffff_ffff);
                }
                block_num -= itemBlockNums;
            }
        }

        /** ブロック数を返す */
        public int getBlockNums() {
            int block_nums = 0;
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                block_nums += item.getBlockNums();
            }
            return block_nums;
        }

        /** チェックサムの更新 */
        public void updateCheckSum() {
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                item.updateCheckSum();
            }
        }

        public AmigaOneBitmap get(int i) {
            return list.get(i);
        }

        public int size() {
            return list.size();
        }
    }

    //
    // Amiga DOS の処理
    //

    /** Root Block */
    private final DirectoryAmiga m_root = new DirectoryAmiga();
    /** Bitmap Blocks */
    private final AmigaBitmap m_bitmap = new AmigaBitmap();

    public DiskBasicTypeAmiga(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryAmiga> dir) {
        super(basic, fat, dir);

        m_root.blockNum = 0;
        m_root.pre = null;
        m_root.post = null;
    }

    @Override
    public void setGroupNumber(int num, int val) {
        m_bitmap.modify(num, val != 0);
    }

    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    @Override
    public boolean isUsedGroupNumber(int num) {
        return !m_bitmap.isFree(num);
    }

    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    @Override
    public int getEmptyGroupNumber() {
        return getEmptyGroupNumberM();
    }

    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        int next_group_num = getEmptyGroupNumber();
        if (next_group_num == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }

        if (chainGroups(curr_group, next_group_num) < 0) {
            return INVALID_GROUP_NUMBER;
        }

        return next_group_num;
    }

    // check / assign FAT area
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;
        return valid_ratio;
    }

    @Override
    public double parseParamOnDisk(boolean is_formatting) throws IOException {
        if (is_formatting) return 0;

        double valid_ratio = 1.0;

        //
        // boot block
        //
        DiskImageSector sector = basic.getSectorFromGroup(0);
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            return -1.0;
        }
        AmigaBootBlock bb = new AmigaBootBlock();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), bb);

        // チェック
        if ((!Arrays.equals(bb.type, 0, 3, "DOS".getBytes(), 0, 3)) &&
                (!Arrays.equals(bb.type, 0, 4, "KICK".getBytes(), 0, 4))) {
            return -1.0;
        }
        // Fast File System か
        boolean disk_is_fast = (bb.type[3] != 'K' && (bb.type[3] & 1) != 0);
        boolean param_is_fast = basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM);
        if (disk_is_fast != param_is_fast) {
            return -1.0;
        }
        basic.diskBasicParam.setVariousParam(KEY_INTERNATIONAL, (bb.type[3] & 6) == 2 || (bb.type[3] & 6) == 4);

        //
        // root block
        //
        m_root.blockNum = bb.root_block;
        if (m_root.blockNum < 2 || m_root.blockNum > basic.diskBasicParam.getFatEndGroup()) {
            // ブート領域かディスクをオーバしている
            if (valid_ratio >= 0.0) valid_ratio *= 0.8;
            m_root.blockNum = basic.diskBasicParam.getManagedTrackNumber() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() + basic.diskBasicParam.getDirStartSector() - 1;
        }
        int[] root_track = new int[1];
        int[] root_side = new int[1];
        sector = basic.getSectorFromGroup(m_root.blockNum, root_track, root_side);
        if (sector == null) {
            return -1.0;
        }
        b = sector.getSectorBuffer();
        if (b == null) {
            return -1.0;
        }
        m_root.pre = new AmigaBlockPre();
        Serdes.Util.deserialize(new ByteArrayInputStream(b, 0, b.length), m_root.pre);
        // hash_tableはセクタサイズ（ブロックサイズ）で可変
        int offset = basic.getSectorSize() - AmigaRootBlockPost.SIZE;
        if (offset < 0) {
            return -1.0;
        }
        b = sector.getSectorBuffer(offset);
        if (b == null) {
            return -1.0;
        }
        m_root.post = new AmigaRootBlockPost();
        Serdes.Util.deserialize(new ByteArrayInputStream(b, offset, b.length - offset), m_root.post);
        basic.diskBasicParam.setManagedTrackNumber(root_track[0]);
        basic.diskBasicParam.setDirStartSector(sector.getSectorNumber());

        //
        // ビットマップ
        //
        if (m_root.post.u.r.bmFlag == -1) {
            for (int i = 0; i < 25; i++) {
                int num = m_root.post.u.r.bmPages[i];
                if (num < 2) {
                    continue;
                }
                sector = basic.getSectorFromGroup(num);
                if (sector == null) {
                    // Why?
                    valid_ratio = 0.2;
                    break;
                }
                m_bitmap.addBitmap(num, sector.getSectorBuffer(), sector.getSectorSize());
            }
        } else {
            valid_ratio = 0.2;
        }
        basic.diskBasicParam.setSectorsPerFat(m_bitmap.size());
        basic.diskBasicParam.setFatEndGroup(basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);

        //
        // ディレクトリエリア
        //

        // root block の hash table を追跡
        int max_ht = m_root.pre.tableSize;
        int calc_max_ht = (basic.getSectorSize() - AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;
        if (max_ht > calc_max_ht) {
            valid_ratio = -1.0;
            max_ht = calc_max_ht;
        }

        for (int ht = 0; ht < max_ht; ht++) {
            int num = m_root.pre.u.table[ht];
            if (num > 0 && num < 2) {
                // boot領域にある？
                if (valid_ratio > 0.0) valid_ratio *= 0.5;
            } else if (num > basic.getFatEndGroup()) {
                // 範囲外
                if (valid_ratio > 0.0) valid_ratio *= 0.5;
            }
        }

        return valid_ratio;
    }

    @Override
    public void getStartNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        if (!m_bitmap.list.isEmpty()) {
            AmigaOneBitmap item = m_bitmap.get(0);
            getNumFromSectorPos(item.getBlockNumber(), track_num, side_num, sector_num);
        }
    }

    @Override
    public void getEndNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        if (!m_bitmap.list.isEmpty()) {
            AmigaOneBitmap item = m_bitmap.get(m_bitmap.size() - 1);
            getNumFromSectorPos(item.getBlockNumber(), track_num, side_num, sector_num);
        }
    }

    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    // check / assign directory area
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) throws IOException {
        group_items.clear();

        if (m_root.pre == null) {
            return false;
        }
        boolean valid = true;

        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        int max_blks = m_root.pre.tableSize;

        valid = DiskBasicDirItemAmiga.getDirectoryGroups(basic, m_root.pre.u.table, max_blks, limit, group_items);

        return valid;
    }

    @Override
    public boolean assignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryAmiga> dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);
        if (dir_item != null) {
            int[] trk = new int[1];
            int[] sid = new int[1];
            DiskImageSector sector = basic.getSectorFromGroup(m_root.blockNum, trk, sid);
            dir_item.setDataPtr(0, null, sector, 0, sector.getSectorBuffer(), 0, null);
        }
        DiskBasicDirItemAmiga.renumberInDirectory(basic, dir_item.getChildren());
        return sts;
    }

    @Override
    public boolean assignDirectory(boolean is_root, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryAmiga> dir_item) throws IOException {
        boolean sts = super.assignDirectory(is_root, group_items, dir_item);
        DiskBasicDirItemAmiga.renumberInDirectory(basic, dir_item.getChildren());
        return sts;
    }

    @Override
    public int initializeSectorsAsDirectory(DiskBasicGroups group_items, int[] file_size, int[] size_remain, DiskBasicError errinfo) {
        size_remain[0] = 0;
        return 0;
    }

    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        if (pos[0] > 0) {
            size_remain[0] += size[0];
            return -1;
        }
        return 0;
    }

    // disk size
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.diskBasicParam.getFatEndGroup() - 1;
        group_size[0] -= m_bitmap.size();
        disk_size[0] = group_size[0] * basic.getSectorSize();
    }

    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.clear();

        int block_size = basic.getSectorSize();
        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            block_size -= AmigaFileDataPre.SIZE - 1;
        }

        // BITMAP table
        for (int num = 0; num <= basic.diskBasicParam.getFatEndGroup(); num++) {
            if (num < 2) {
                fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
            } else if (m_bitmap.isFree(num)) {
                fatAvailability.add(FAT_AVAIL_FREE, block_size, 1);
            } else {
                fatAvailability.add(FAT_AVAIL_USED, 0, 0);
            }
        }

        fatAvailability.set(m_root.blockNum, FAT_AVAIL_SYSTEM);

        for (int i = 0; i < m_bitmap.size(); i++) {
            AmigaOneBitmap item = m_bitmap.get(i);
            fatAvailability.set(item.getBlockNumber(), FAT_AVAIL_SYSTEM);
        }
    }

    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryAmiga> pitem, DiskBasicDirItem<DirectoryAmiga> nitem, DiskBasicError errinfo) {
        return true;
    }

    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryAmiga> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) throws IOException {
        int groups = 0;

        int rc = 0;

        // ディレクトリ新規作成の時は何もしない
        if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW && item.isDirectory()) {
            return 0;
        }

        int block_size = basic.getSectorSize();
        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            // OFSなら24バイト減らす
            block_size -= AmigaFileDataPre.SIZE - 1;
        }
        int remain = data_size;
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        int group_num = INVALID_GROUP_NUMBER;
        int prev_group_num = 0;
        int prev_remain = 0;

        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        int block_nums = aitem.getDataBlockNums();
        int block_idx = block_nums - 1;
        int extension = -1;
        int header_block_num = aitem.getStartGroup(fileunit_num);

        while (remain > 0 && limit >= 0 && rc >= 0) {
            if (block_idx < 0) {
                // データテーブルがいっぱいになったので
                // extensionブロックを新たに確保する
                int ex_num = getEmptyGroupNumber();
                if (ex_num == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = -2;
                    break;
                }
                DiskImageSector sector = basic.getSectorFromGroup(ex_num);
                if (sector == null) {
                    rc = -2;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    rc = -2;
                    break;
                }
                // 使用済みにする
                setGroupNumber(ex_num, 1);

                // extensionブロックへのリンクを作成
                aitem.setExtension(ex_num);
                aitem.setHighSeq(block_nums);

                // aitem切替
                aitem = (DiskBasicDirItemAmiga) dir.newItem(sector, extension, buffer, 0);

                sector.fill((byte) 0);
                aitem.initForExtensionBlock(header_block_num);
                block_nums = aitem.getDataBlockNums();
                block_idx = block_nums - 1;

                extension++;
            }

            // 空きをさがす
            group_num = getEmptyGroupNumber();
            if (group_num == INVALID_GROUP_NUMBER) {
                rc = groups > 0 ? -2 : -1;
                break;
            }

            // 使用済みにする
            if (prev_group_num > 0) {
                basic.getNumsFromGroup(prev_group_num, group_num, basic.getSectorSize(), prev_remain, group_items[0]);
            }
            prev_group_num = group_num;
            prev_remain = remain;

            setGroupNumber(group_num, 1);
            aitem.setDataBlock(block_idx, group_num);

            groups++;
            remain -= block_size;
            limit--;

            block_idx--;
        }

        if (prev_group_num > 0) {
            basic.getNumsFromGroup(prev_group_num, 0, basic.getSectorSize(), prev_remain, group_items[0]);
        }

        aitem.setHighSeq(block_nums - block_idx - 1);

        if (limit < 0) {
            // 無限ループ？
            rc = groups > 0 ? -2 : -1;
        }

        return rc;
    }

    @Override
    public int chainGroups(int group_num, int append_group_num) {
        return 0;
    }

    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num;
    }

    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return group_num;
    }

    @Override
    public DiskBasicDirItem<DirectoryAmiga> getEmptyDirectoryItem(DiskBasicDirItem<DirectoryAmiga> parent, List<DiskBasicDirItem<DirectoryAmiga>> items, DiskBasicDirItem<DirectoryAmiga> pitem, DiskBasicDirItem<DirectoryAmiga>[] next_item) throws IOException {
        DiskBasicDirItem<DirectoryAmiga> match_item = null;
        byte[] name = new byte[32];
        int[] len = {name.length};
        int[] elen = new int[1];

        // ファイル名からハッシュ番号を算出し、ハッシュテーブルに関連付ける
        if (parent != null && pitem != null) {
            if (!parent.getFileAttr().isDirectory()) {
                return match_item;
            }

            // ファイル名を得る
            pitem.getNativeFileName(name, len, null, elen);
            // ハッシュ番号を計算
            int hash = createHashNumberFromName(name, len[0]);

            // 新しいセクタを確保
            int new_num = getEmptyGroupNumber();
            if (new_num == INVALID_GROUP_NUMBER) {
                // 空きなし
                return match_item;
            }

            // 実際にセクタがあるか
            DiskImageSector sector = basic.getSectorFromGroup(new_num);
            if (sector == null) {
                return match_item;
            }
            byte[] buffer = sector.getSectorBuffer();
            if (buffer == null) {
                return match_item;
            }

            // ヘッダ情報をセット
            DiskBasicDirItemAmiga apitem = (DiskBasicDirItemAmiga) pitem;
            apitem.setStartGroup(0, new_num);
            apitem.initForHeaderBlock(parent.getStartGroup(0));

            // アイテムを新規作成
            match_item = dir.newItem(sector, 0, buffer, 0);

            // セクタにヘッダ情報をセット
            sector.fill((byte) 0);

            // セクタ使用中にする
            setGroupNumber(new_num, 1);

            // 親ディレクトリをセット
            match_item.setParent(parent);

            // ハッシュ番号を登録
            DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;
            aparent.chainHashNumber(hash, new_num, match_item);

            // ディレクトリリストに追加する
            int limit = basic.getFatEndGroup() + 1;
            int[] tables = aparent.getBlockTable();
            int nums = aparent.getDataBlockNums();

            if (items == null) {
                parent.createChildren();
                items = parent.getChildren();
            }
            DiskBasicDirItemAmiga.insertItemInDirectory(basic, tables, nums, limit, items, match_item);

            return match_item;
        }
        return match_item;
    }

    // directory
    @Override
    public boolean isRootDirectory(int group_num) {
        return (group_num == m_root.blockNum);
    }

    @Override
    public boolean canMakeDirectory() {
        return true;
    }

    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryAmiga> item, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryAmiga> parent_item) {
        // ビットマップのチェックサムを更新する
        m_bitmap.updateCheckSum();

        // 日時
        LocalDateTime tm = LocalDateTime.now();
        item.setFileModifyDateTime(tm);

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        // 親ディレクトリの日時を更新する（ルートディレクトリの場合も含む）
        aparent.setFileModifyDateTime(tm);

        // ルートディレクトリのボリューム日時を更新する
        setVolumeDateTime(tm);

        // 親ディレクトリのチェックサムを更新する
        aparent.updateCheckSum();

        // ルートディレクトリのチェックサムを更新する
        if (aparent.getParent() != null) updateCheckSumOnRoot();
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        boolean is_ffs = basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM);
        boolean is_intr = false;
        boolean is_dirc = false;

        int track_num;
        int sector_num;

        //
        // Boot Block を作成
        //
        int blk = 0;
        sector = basic.getSectorFromGroup(blk);
        sector.fill((byte) 0);
        byte[] b = sector.getSectorBuffer();
        AmigaBootBlock boot = new AmigaBootBlock();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), boot);
        boot.type[0] = (byte) 'D';
        boot.type[1] = (byte) 'O';
        boot.type[2] = (byte) 'S';
        if (is_ffs) {
            boot.type[3] |= 1;
        }
        if (is_intr) {
            boot.type[3] |= 2;
            if (is_dirc) boot.type[3] += 2;
        }

        DiskBasicParam default_param = gDiskBasicTemplates.findType("", basic.getBasicTypeName());
        track_num = default_param.getManagedTrackNumber();
        sector_num = default_param.getDirStartSector();
        basic.diskBasicParam.setManagedTrackNumber(track_num);
        basic.diskBasicParam.setDirStartSector(sector_num);

        int root_block = getSectorPosFromNumS(track_num, sector_num);
        boot.root_block = root_block;

        //
        // bitmapブロックを確保
        //
        basic.diskBasicParam.setFatEndGroup(basic.getSidesPerDiskOnBasic() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);
        m_bitmap.list.clear();
        blk = root_block;
        do {
            blk++;
            sector = basic.getSectorFromGroup(blk);
            m_bitmap.addBitmap(blk, sector.getSectorBuffer(), sector.getSectorSize());
        } while (m_bitmap.getBlockNums() < basic.getFatEndGroup() + 1);

        m_bitmap.freeAll(basic.getFatEndGroup());

        basic.diskBasicParam.setSectorsPerFat(m_bitmap.size());

        //
        // Root Block を作成
        //
        sector = basic.getSectorFromGroup(root_block);
        sector.fill((byte) 0);
        m_root.blockNum = root_block;
        byte[] rootBuffer = sector.getSectorBuffer();
        m_root.pre = new AmigaBlockPre();
        Serdes.Util.deserialize(new ByteArrayInputStream(rootBuffer), m_root.pre);
        m_root.pre.type = FILETYPE_MASK_AMIGA_HEADER;

        int val = (sector.getSectorSize() - AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;
        m_root.pre.tableSize = val;

        m_root.post = new AmigaRootBlockPost();
        Serdes.Util.deserialize(new ByteArrayInputStream(rootBuffer, AmigaBlockPre.SIZE + val * 4 - 4, rootBuffer.length - (AmigaBlockPre.SIZE + val * 4 - 4)), m_root.post);
        m_root.post.u.r.bmFlag = -1;
        for (int i = 0; i < m_bitmap.size(); i++) {
            val = m_bitmap.get(i).getBlockNumber();
            m_root.post.u.r.bmPages[i] = val;
            m_bitmap.modify(val, true);
        }

        m_root.post.u.r.secType = FILETYPE_MASK_AMIGA_ROOT;

        if (is_ffs && is_dirc) {
            // block number to first directory cache block
            m_root.post.u.r.extension = 0;
        }

        // 日時
        LocalDateTime tm = LocalDateTime.now();
        setCreateDateTime(tm);
        setVolumeDateTime(tm);
        setModifyDateTime(tm);

        // volume name
        setIdentifiedData(data);

        // bitmapをセット
        m_bitmap.modify(root_block, true);

        // チェックサムを計算
        m_bitmap.updateCheckSum();
        m_root.pre.checkSum = calcCheckSumOnBootBlock(rootBuffer, basic.getSectorSize());

        return true;
    }

    /// チェックサムを計算
    ///
    /// @param data ブロックデータ
    /// @param size ブロックサイズ
    static int calcCheckSum(byte[] data, int size) {
        IntBuffer dp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int newsum = 0;
        size >>= 2;
        for(int i=0; i<size; i++) {
            newsum += dp.get(i);
        }
        newsum = ~newsum;
        newsum++;

        return newsum;
    }

    /**
     * チェックサムを計算
     *
     * @param data ブロックデータ
     * @param size ブロックサイズ
     */
    public static int calcCheckSumOnBootBlock(byte[] data, int size) {
        return calcCheckSum(data, size);
    }

    /**
     * データの読み込み/比較処理
     *
     * @param fileunit_num  ファイル番号
     * @param item          ディレクトリアイテム
     * @param istream       [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時は null
     * @param ostream       [in,out] 出力先 データ読み出し時に使用 ベリファイ時は null
     * @param sector_buffer セクタバッファ
     * @param sector_size   バッファサイズ
     * @param remain_size   残りサイズ
     * @param sector_num    セクタ番号
     * @param sector_end    最終セクタ番号
     * @return >=0: 処理したサイズ, -1: 比較不一致
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryAmiga> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws IOException {
        int bufferOffset = 0;
        int size = sector_size;

        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            // OFSなら24バイト減らす
            size -= AmigaFileDataPre.SIZE - 1;
            bufferOffset += AmigaFileDataPre.SIZE - 1;
        }
        if (remain_size < size) {
            size = remain_size;
        }

        byte[] temp;
        if (ostream != null) {
            // 書き出し
            temp = Arrays.copyOfRange(sector_buffer, bufferOffset, bufferOffset + size);
            if (basic.isDataInverted()) Common.mem_invert(temp, temp.length);
            ostream.write(temp, 0, temp.length);
        }
        if (istream != null) {
            // 読み込んで比較
            temp = new byte[size];
            istream.readNBytes(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.mem_invert(temp, temp.length);

            if (!Arrays.equals(temp, 0, size, sector_buffer, bufferOffset, bufferOffset + size)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryAmiga> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sectorOffset, int sector_size, int remain_size) {
        return remain_size;
    }

    @Override
    public boolean isEnoughFileSize(int size) {
        int block_size = basic.getSectorSize();
        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            // OFSなら24バイト減らす
            block_size -= AmigaFileDataPre.SIZE - 1;
        }

        int table_cnt = (basic.getSectorSize() -
                AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;

        int data_cnt = (size + block_size - 1) / block_size;
        int header_cnt = 1 + data_cnt / table_cnt;

        return (getFreeGroupSize() >= (header_cnt + data_cnt));
    }

    @Override
    public int writeFile(DiskBasicDirItem<DirectoryAmiga> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        int len = 0;
        AmigaFileDataPre pre = null;
        int bufferOffset = 0;

        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            // OFSなら24バイト減らす
            pre = new AmigaFileDataPre();
            Serdes.Util.deserialize(new ByteArrayInputStream(buffer), pre);
            size -= AmigaFileDataPre.SIZE - 1;
            bufferOffset += AmigaFileDataPre.SIZE - 1;
        }

        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) istream.readNBytes(buffer, bufferOffset, remain);
            if (size > remain) {
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            istream.readNBytes(buffer, bufferOffset, size);
            len = size;
        }

        if (pre != null) {
            // OFSの場合、パラメータをセット
            DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
            int val = FILETYPE_MASK_AMIGA_DATA;
            pre.o.type = val;
            val = aitem.getStartGroup(0);
            pre.o.header_key = val;
            val = seq_num + 1;
            pre.o.seq_num = val;
            val = (remain > size ? size : remain);
            pre.o.data_size = val;
            pre.o.next_data = next_group;
        }

        return len;
    }

    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryAmiga> item) {
        // ビットマップのチェックサムを更新する
        m_bitmap.updateCheckSum();

        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        aitem.updateCheckSumAll();

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        // 親ディレクトリの日時を更新する（ルートディレクトリの場合も含む）
        LocalDateTime tm = LocalDateTime.now();
        aparent.setFileModifyDateTime(tm);

        // ルートディレクトリのボリューム日時を更新する
        setVolumeDateTime(tm);

        // 親ディレクトリのチェックサムを更新する
        aparent.updateCheckSum();

        // ルートディレクトリのチェックサムを更新する
        if (aparent.getParent() != null) updateCheckSumOnRoot();
    }

    @Override
    public void additionalProcessOnRenamedFile(DiskBasicDirItem<DirectoryAmiga> item) throws IOException {
        byte[] name = new byte[32];
        int[] len = {name.length};
        int[] elen = new int[1];

        // ファイル名を得る
        item.getNativeFileName(name, len, null, elen);
        // ハッシュ番号を計算
        int hash_num = createHashNumberFromName(name, len[0]);

        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        if (hash_num == aitem.getHashNumber()) {
            // ハッシュ番号が同じなら変更なし
            return;
        }

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        int limit = basic.getFatEndGroup() + 1;
        int[] tables = aparent.getBlockTable();
        int nums = aparent.getDataBlockNums();

        // ディレクトリリストから削除する
        DiskBasicDirItemAmiga.deleteItemInDirectory(basic, tables, nums, limit, parent.getChildren(), item);

        // ハッシュ番号を登録
        int block_num = item.getStartGroup(0);
        aparent.chainHashNumber(hash_num, block_num, item);

        // ディレクトリリストに追加する
        DiskBasicDirItemAmiga.insertItemInDirectory(basic, tables, nums, limit, parent.getChildren(), item);
    }

    @Override
    public void deleteGroupNumber(int group_num) {
        // 未使用にする
        setGroupNumber(group_num, 0);
    }

    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryAmiga> item) {
        // ビットマップのチェックサムを更新する
        m_bitmap.updateCheckSum();

        // ディレクトリリストから削除する
        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return false;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        int limit = basic.getFatEndGroup() + 1;
        int[] tables = aparent.getBlockTable();
        int nums = aparent.getDataBlockNums();

        DiskBasicDirItemAmiga.deleteItemInDirectory(basic, tables, nums, limit, parent.getChildren(), item);

        // 親ディレクトリの日時を更新する（ルートディレクトリの場合も含む）
        LocalDateTime tm = LocalDateTime.now();
        aparent.setFileModifyDateTime(tm);

        // ルートディレクトリのボリューム日時を更新する
        setVolumeDateTime(tm);

        // 親ディレクトリのチェックサムを更新する
        aparent.updateCheckSum();

        // ルートディレクトリのチェックサムを更新する
        if (aparent.getParent() != null) updateCheckSumOnRoot();

        return true;
    }

    @Override
    public void releaseDirectoryItem(DiskBasicDirItem<DirectoryAmiga> item) {
        super.releaseDirectoryItem(item);
    }

    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        if (m_root.post == null) return;

        // volume name in root
        byte[] name = new byte[m_root.post.u.r.diskName.length + 1];
        int len = m_root.post.u.r.diskNameLen & 0xff;
        System.arraycopy(m_root.post.u.r.diskName, 0, name, 0, len);

        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(name, 0, len, sb, -1);
        String wname = sb.toString();
        data.setVolumeName(wname);
        data.setVolumeNameMaxLength(name.length);

        // volume date in root
        LocalDateTime tm = DiskBasicDirItemAmiga.convDateToTm(m_root.post.u.r.cDays).atTime(
                DiskBasicDirItemAmiga.convTimeToTm(m_root.post.u.r.cMins, m_root.post.u.r.cTicks));
        String datetime = Utils.formatYMDStr(tm.toLocalDate());
        datetime += " ";
        datetime += Utils.formatHMSStr(tm.toLocalTime());
        data.setVolumeDate(datetime);
    }

    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        if (m_root.post == null) return;

        DiskBasicFormat fmt = basic.getFormatType();

        if (fmt.hasVolumeName()) {
            byte[] name = new byte[m_root.post.u.r.diskName.length + 1];
            basic.getCharCodes().convToChars(data.getVolumeName(), name, name.length);
            System.arraycopy(name, 0, m_root.post.u.r.diskName, 0, name.length + 1);
            m_root.post.u.r.diskNameLen = (byte) (name.length & 0xff);
        }
    }

    /** ルートのチェックサムを計算 */
    public void updateCheckSumOnRoot() {
        DiskBasicDirItem<DirectoryAmiga> aroot = dir.getRootItem();
        ((DiskBasicDirItemAmiga) aroot).updateCheckSum();
    }

    /// ルートの更新日時をセット
    /// @param tm 日時
    public void setModifyDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = m_root.post.u.r;
        r.rDays = days[0];
        r.rMins = mins[0];
        r.rTicks = ticks[0];
    }

    /// ルートのボリューム日時をセット
    /// @param tm 日時
    public void setVolumeDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = m_root.post.u.r;
        r.vDays = days[0];
        r.vMins = mins[0];
        r.vTicks = ticks[0];
    }

    /// ルートの作成日時をセット
    /// @param tm 日時
    public void setCreateDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = m_root.post.u.r;
        r.cDays = days[0];
        r.cMins = mins[0];
        r.cTicks = ticks[0];
    }

    /// 空き位置を返す
    /// @return INVALID_GROUP_NUMBER: 空きなし
    private int getEmptyGroupNumberM() {
        int new_num = INVALID_GROUP_NUMBER;
        int num_of_secs = basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        int sta_trk = 0;
        int end_trk = 0;
        int ndir = 1;

        for (int i = 0; i < 2; i++) {
            switch (i) {
                case 0:
                    // 外側へ検索
                    sta_trk = basic.getManagedTrackNumber();
                    end_trk = basic.getTracksPerSideOnBasic() + basic.getTrackNumberBaseOnDisk();
                    ndir = 1;
                    break;
                case 1:
                    // 内側へ検索
                    sta_trk = basic.getManagedTrackNumber() - 1;
                    end_trk = basic.getTrackNumberBaseOnDisk() - 1;
                    ndir = -1;
                    break;
            }

            for (int trk_num = sta_trk; trk_num != end_trk && new_num == INVALID_GROUP_NUMBER; trk_num += ndir) {
                for (int sec_num = 0; sec_num < num_of_secs; sec_num++) {
                    int num = getSectorPosFromNumS(trk_num, sec_num + basic.getSectorNumberBase());
                    if (m_bitmap.isFree(num)) {
                        new_num = num;
                        break;
                    }
                }
            }
        }
        return new_num;
    }

    /**
     * ファイル名からハッシュ番号を生成する
     *
     * @param name [in,out] ファイル名
     * @param size サイズ
     * @return ハッシュ番号
     */
    private int createHashNumberFromName(byte[] name, int size) {
        int hash;
        int bsize = (basic.getSectorSize() - AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;
        boolean is_intr = basic.diskBasicParam.getVariousBoolParam(KEY_INTERNATIONAL);

        int l = hash = Common.str_length(name, size, (byte) 0);
        for (int i = 0; i < l; i++) {
            hash *= 13;
            hash += DiskBasicDirItemAmiga.upper(name[i], is_intr);
            hash &= 0x7ff;
        }
        hash %= bsize;

        return hash;
    }
}