/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils.TempData;
import l3diskex.basicfmt.BasicCommon.DirectoryMz;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.BasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;


public class DiskBasicTypeMZ extends DiskBasicTypeMZBase {

    // Equivalent to struct st_fat_mz
    @SuppressWarnings("unused")
    private static class st_fat_mz {
        public byte volume;     // ボリューム番号 (0)
        public byte offset;     // オフセット データ領域開始クラスタ (1)
        public short used;      // 使用クラスタ数 (2-3)
        public short all;       // 全体クラスタ数 (4-5)
        // public byte[] bits = new byte[0xf9]; // 使用状況 (6-250) - not strictly necessary to define the array here
        public byte mag;        // １クラスタのセクタ数-1 (251)
        // Total size is 252 bytes
    }

    private final TempData temp = new TempData(); // Assuming TempData is a utility class for handling sector data/inversion

    public DiskBasicTypeMZ(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /** FAT位置をセット */
    public void SetGroupNumber(int num, int val) {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        int pos = num - dataStartGroup;
        if (pos < 0) {
            return;
        }

        int mask = 0;
        int[] pos_arr = {pos}; // Use array to pass by reference
        int[] mask_arr = {mask};
        CalcUsedGroupPos(num, pos_arr, mask_arr);
        pos = pos_arr[0];
        mask = mask_arr[0];

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return;
        }
        // FATには未使用使用テーブルがある
        fatbuf.bit(pos, (byte)mask, val != 0, basic.isDataInverted());

        // FATの使用済み最終クラスタ数を更新
        byte[] buf = fatbuf.getBuffer();
        // Use ByteBuffer to access struct members
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN); // Assuming Little Endian from InvertAndOrderUint16 context

        // Offset 2 for 'used' short
        short used_group_short = bb.getShort(2);
        int used_group = basic.invertAndOrderUint16(used_group_short);  // invert

        if (val != 0) {
            // セットした時は最終クラスタ数を増やす
            used_group++;
        } else {
            // クリアした時は最終クラスタ数を減らす
            used_group--;
        }

        // Write back 'used'
        bb.putShort(2, basic.invertAndOrderUint16((short)used_group)); // invert

    }

    /** FATオフセットを返す */
    public int GetGroupNumber(int num) {
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return INVALID_GROUP_NUMBER;
        }
        // FATのオフセットを得る
        byte[] buf = fatbuf.getBuffer();
        // Offset 1 for 'offset' byte
        byte offset_byte = buf[1];
        int offset = basic.invertUint8(offset_byte) & 0xFF; // invert, convert to unsigned int
        if (offset > num) num = offset;
        return num;
    }

    /** 使用しているグループの位置を得る */
    public void CalcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3) + 6;
    }

    /** 次のグループ番号を得る */
    public int GetNextGroupNumber(int num, int sector_pos) {
        int[] trk_num = new int[1];
        int[] sid_num = new int[1];
        int[] sec_num = new int[1];
        basic.calcNumFromSectorPosForGroup(sector_pos, trk_num, sid_num, sec_num, null, null);
        DiskImageSector sector = basic.getDisk().getSector(trk_num[0], sid_num[0], sec_num[0]);
        if (sector == null) return 0;

        byte[] sector_buffer = sector.getSectorBuffer();
        int sector_size = sector.getSectorSize();

        // Last 2 bytes are chain sector number
        short next_sec_short = ByteBuffer.wrap(sector_buffer, sector_size - 2, 2)
                .order(ByteOrder.LITTLE_ENDIAN) // Assuming LE
                .getShort();

        int next_sec = basic.invertAndOrderUint16(next_sec_short) & 0xFFFF; // invert, convert to unsigned int
        return next_sec / basic.getSectorsPerGroup();
    }
    /** 空きFAT位置を返す */
    public int GetEmptyGroupNumber() {
        int new_num = INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return new_num;
        }
        // FATの使用済み最終クラスタを得る
        byte[] buf = fatbuf.getBuffer();
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN); // Assuming LE

        // Offset 1 for 'offset' byte
        byte offset_byte = bb.get(1);
        int offset = basic.invertUint8(offset_byte) & 0xFF; // invert, convert to unsigned int

        // Offset 2 for 'used' short
        short used_group_short = bb.getShort(2);
        int used_group = basic.invertAndOrderUint16(used_group_short) & 0xFFFF;  // invert, convert to unsigned int

        // Offset 4 for 'all' short
        short all_group_short = bb.getShort(4);
        int all_group = basic.invertAndOrderUint16(all_group_short) & 0xFFFF; // invert, convert to unsigned int

        if (used_group < all_group) {
            new_num = used_group;
        }
        if (new_num < offset) {
            new_num = offset;
        }
        return new_num;
    }

    /** FATエリアをチェック */
    public double CheckFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return -1.0;
        }
        byte[] buf = fatbuf.getBuffer();
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN); // Assuming LE

        // Offset 1 for 'offset' byte
        byte offset_byte = bb.get(1);
        int offset = basic.invertUint8(offset_byte) & 0xFF; // invert, convert to unsigned int

        // Offset 251 for 'mag' byte
        byte mag_byte = bb.get(251);
        int mag = basic.invertUint8(mag_byte) & 0xFF; // invert, convert to unsigned int

        // オフセット(1バイト目)が16未満ならエラー
        if (offset < 16) {	// invert
            valid_ratio = -1.0;
        }
        if (valid_ratio >= 0.0) {
            dataStartGroup = offset;
        }

        // クラスタ倍率(255バイト目)が16以上ならエラー
        if (mag >= 16) {	// invert
            valid_ratio = -1.0;
        }
        if (valid_ratio >= 0.0) {
            // セクタ数/クラスタ
            basic.diskBasicParam.setSectorsPerGroup(mag + 1);

            // Offset 4 for 'all' short
            short all_short = bb.getShort(4);
            // クラスタ数
            basic.diskBasicParam.setFatEndGroup((basic.invertAndOrderUint16(all_short) & 0xFFFF) - 1); // invert, convert to unsigned int
        } else {
            // クラスタ数はトラック数から計算する
            int trks = basic.getTracksPerSideOnBasic();
            int secs = 1;
            if (trks > 44) {
                // 2DDのとき
                secs = 2;
                trks /= 2;
            }
            basic.diskBasicParam.setSectorsPerGroup(secs);
            basic.diskBasicParam.setFatEndGroup(trks * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() - 1);
        }

        return valid_ratio;
    }

    /** ルートディレクトリをアサイン */
    public boolean AssignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);

        // 開始グループ
        int sec_pos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + start_sector - 1;
        sec_pos /= basic.getSectorsPerGroup();
        dir_item.setStartGroup(0, sec_pos);

        return sts;
    }

    /** 残りディスクサイズを計算 */
    public void CalcDiskFreeSize(boolean wrote) {
        int used = 0;
        fatAvailability = new DiskBasicAvailability(); // Reset

        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return;
        }
        byte[] buf = fatbuf.getBuffer();
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN); // Assuming LE

        // Offset 2 for 'used' short
        short used_short = bb.getShort(2);
        int used_groups = basic.invertAndOrderUint16(used_short) & 0xFFFF;	// invert, convert to unsigned int

        // 使用済みかチェック
        int grps = 0;
        int fsts;
        for(int gnum = 0; gnum <= basic.getFatEndGroup(); gnum++) {
            if (gnum < dataStartGroup) {
                used++;
                fsts = FAT_AVAIL_SYSTEM.getValue();
            } else if (!IsUsedGroupNumber(gnum)) { // Assumed method on base class
                grps++;
                fsts = FAT_AVAIL_FREE.getValue();
            } else {
                used++;
                fsts = FAT_AVAIL_USED.getValue();
            }
            fatAvailability.Add(fsts, 0, 0);
        }

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem> items = dir.getCurrentItems(null);
        if (items != null) {
            for(int idx = 0; idx < items.size(); idx++) {
                DiskBasicDirItem item = items.get(idx);
                if (item == null || !item.isUsed()) continue;

                // グループ番号のマップを調べる
                int gcnt = item.getGroupCount();
                if (gcnt > 0) {
                    DiskBasicGroupItem gitem = item.getGroup(gcnt - 1);
                    int gnum = gitem.group;
                    if (gnum <= basic.getFatEndGroup()) {
                        fatAvailability.Set(gnum, FAT_AVAIL_USED_LAST.getValue());
                    }
                }
            }
        }

        int fsize = grps * basic.getSectorsPerGroup() * basic.getSectorSize();

        // 使用済みクラスタ数を更新
        if (wrote && used_groups != used) {
            bb.putShort(2, basic.invertAndOrderUint16((short)used));	// invert
        }

        fatAvailability.setFreeSize(fsize);
        fatAvailability.SetFreeGroups(grps);
    }
    //@}

    // Assumed to be defined in DiskBasicTypeMZBase
    private boolean IsUsedGroupNumber(int gnum) {
        return false;
    }

    /** @name file chain */
    //@{
    /** データサイズ分のグループを確保する */
    public int AllocateUnitGroups(int fileunit_num, DiskBasicDirItem item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) {
        int file_size = 0;
        int groups = 0;

        int rc = 0;
        int remain = data_size;
        boolean is_chain = item.needChainInData();
        boolean is_brd = item.getFileAttr().matchType(FILE_TYPE_RANDOM_MASK.getValue(), FILE_TYPE_RANDOM_MASK.getValue());
        int sec_size = basic.getSectorSize();
        if (is_chain) {
            sec_size -= 2;
        }

        // 必要なグループ数
        int group_size = ((data_size - 1) / sec_size / basic.getSectorsPerGroup()) + 1;
        if (is_chain || is_brd) {
            group_size = 1;
        }

        // 未使用が連続している位置をさがす (FindContinuousArea assumed to be a method on base class or utility)
        int[] group_start_arr = new int[1];
        int cnt = FindContinuousArea(group_size, group_start_arr);
        int group_start = group_start_arr[0];

        if (cnt < group_size) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // 開始グループ決定
        item.setStartGroup(fileunit_num, group_start);

        if (is_brd) {
            // BRD ランダムアクセス
            // マップ領域を確保
            DiskImageSector sector = basic.getSectorFromGroup(group_start);
            if (sector == null) {
                // セクタ無い？！
                rc = -1;
                return rc;
            }
            sector.fill(basic.invertUint8((byte)0));	// invert

            byte[] sector_buffer = sector.getSectorBuffer();
            // wxUint16 *brd_maps = NULL;
            // brd_maps = (wxUint16 *)sector->GetSectorBuffer(); // treat sector_buffer as array of short (wxUint16)

            SetGroupNumber(group_start, 1);

            int brd_pos = 0;

            do {
                // 連続した16セクタを確保できるかをさがす
                group_size = 16 / basic.getSectorsPerGroup();
                int[] bcnt_arr = new int[1];
                int bcnt = FindContinuousArea(group_size, bcnt_arr);
                group_start = bcnt_arr[0];

                if (bcnt < group_size) {
                    // 十分な空きがない
                    rc = -2;
                    return rc;
                }

                // 開始セクタ
                int sec_pos = group_start * basic.getSectorsPerGroup();
                short sec_pos_short = basic.invertAndOrderUint16((short)sec_pos);	// invert, convert to short

                // brd_maps[brd_pos] = wxUINT16_SWAP_ON_BE(sec_pos_short); // Equivalent to writing short at 2*brd_pos
                ByteBuffer.wrap(sector_buffer).order(ByteOrder.BIG_ENDIAN).putShort(brd_pos * 2, sec_pos_short);

                // 領域を確保する
                int block_remain = (16 * basic.getSectorSize());
                int block_size = 0;
                int[] block_size_arr = {block_size};
                int[] groups_arr = {groups};
                rc = AllocateGroupsSub(item, group_start, block_remain, basic.getSectorSize(), group_items, block_size_arr, groups_arr);
                block_size = block_size_arr[0];
                groups = groups_arr[0];

                if (block_remain >= remain) {
                    file_size += (((remain + 31) / 32) * 32);
                    break;
                }
                remain -= block_remain;
                file_size += block_remain;
                brd_pos++;
            } while((brd_pos * 2) < basic.getSectorSize());

        } else {
            // BRD 以外

            // 領域を確保する
            int[] file_size_arr = {file_size};
            int[] groups_arr = {groups};
            rc = AllocateGroupsSub(item, group_start, remain, sec_size, group_items, file_size_arr, groups_arr);
            file_size = file_size_arr[0];
            groups = groups_arr[0];
        }
        return rc;
    }

    // Assumed to be defined in DiskBasicTypeMZBase or utility
    private int FindContinuousArea(int group_size, int[] group_start) {
        // Placeholder implementation
        if (group_size > 0) {
            group_start[0] = dataStartGroup;
            return group_size;
        }
        group_start[0] = 0;
        return 0;
    }

    /** グループを確保して使用中にする */
    public int AllocateGroupsSub(DiskBasicDirItem item, int group_start, int remain, int sec_size, DiskBasicGroups group_items, int[] file_size, int[] groups) {
        int rc = 0;
        int group_num = group_start;
        int prev_group = 0;

        int limit = basic.getFatEndGroup() + 1;
        while(remain > 0 && limit >= 0) {
            // 使用しているか
            boolean used_group = IsUsedGroupNumber(group_num);
            if (!used_group) {
                if (prev_group > 0 && prev_group <= basic.getFatEndGroup()) {
                    // 使用済みにする
                    basic.getNumsFromGroup(prev_group, group_num, sec_size, remain, group_items);
                    SetGroupNumber(prev_group, 1);
                    file_size[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
                    groups[0]++;
                }
                remain -= (sec_size * basic.getSectorsPerGroup());
                prev_group = group_num;
            }
            // 次のグループ
            group_num++;
            limit--;
        }
        if (prev_group > 0 && prev_group <= basic.getFatEndGroup()) {
            // 使用済みにする
            basic.getNumsFromGroup(prev_group, 0, sec_size, remain, group_items);
            SetGroupNumber(prev_group, 1);
            file_size[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
            groups[0]++;
        }
        if (prev_group > basic.getFatEndGroup()) {
            // ファイルがオーバフローしている
            rc = -2;
        } else if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }
        return rc;
    }

    /** ルートディレクトリか */
    public boolean IsRootDirectory(int group_num) {
        // オフセット未満だったらルート
        // fat->Get(1) is the offset byte of the FAT structure (st_fat_mz.offset)
        return (basic.invertUint8((byte)fat.get(1)) & 0xFF) > group_num;	// invert
    }

    /** サブディレクトリを作成できるか */
    public boolean CanMakeDirectory() { return true; }

    /** サブディレクトリを作成する前にディレクトリ名を編集する */
    public boolean RenameOnMakingDirectory(String[] dir_name) { // Use array to simulate pass-by-reference for String
        String name = dir_name[0];
        // 空や"."で始まるディレクトリは作成不可
        if (name.isEmpty() || name.startsWith(".")) {
            return false;
        }
        return true;
    }
    /** サブディレクトリを作成した後の個別処理 */
    public void AdditionalProcessOnMadeDirectory(DiskBasicDirItem item, DiskBasicGroups group_items, DiskBasicDirItem parent_item) throws IOException {
        if (group_items.count() <= 0) return;

        // ボリューム番号、カレントと親ディレクトリのエントリを作成する
        DiskBasicGroupItem gitem = group_items.item(0);

        DiskImageSector sector = basic.getDisk().getSector(gitem.track, gitem.side, gitem.sectorStart);
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();
        int pos = 0;

        // Volume entry
        DiskBasicDirItem newitem = basic.createDirItem(sector, 0, buf);
        newitem.copyData(item.getData()); // Assuming GetData() exists and returns directory entry data
        newitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);

        pos += newitem.getDataSize();
        // newitem->SetDataPtr(0, NULL, sector, 0, buf); // SetDataPtr at current pos, not used for writing here, just moving buf ptr

        // Current directory ('.')
        // Re-use newitem pointer with new buffer offset
        newitem.setDataPtr(0, null, sector, 0, Arrays.copyOfRange(buf, pos, buf.length), null);
        newitem.copyData(item.getData());
        newitem.setFileNamePlain(".");
        newitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);

        pos += newitem.getDataSize();
        // newitem->SetDataPtr(0, NULL, sector, 0, buf);

        // Parent directory ('..')
        newitem.setDataPtr(0, null, sector, 0, Arrays.copyOfRange(buf, pos, buf.length), null);
        if (parent_item != null) {
            // 親がサブディレクトリ
            newitem.copyData(parent_item.getData());
        } else {
            // 親がルート
            newitem.copyData(item.getData());
            newitem.setStartGroup(0, 0); // Root directory is group 0
        }
        newitem.setFileNamePlain("..");
        newitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);

        // delete newitem; // The item is created by basic->CreateDirItem and manages memory in C++, but in Java it's garbage collected.
    }

    //@{
    /** セクタデータを埋めた後の個別処理 */
    public boolean AdditionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // IPL
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));	// invert
            byte[] buf = sector.getSectorBuffer();
            if (buf != null) {
                String ipl_string = basic.diskBasicParam.getVariousStringParam("IPLString");
                byte[] ipl = ipl_string.getBytes();
                int len = ipl.length;
                if (len > 0) {
                    if (len > 32) len = 32;
                    basic.invertMem(ipl, len, buf); // Invert ipl data into buf
                }
            }
        }

        byte volume_number = 1;
        String volume_string = basic.diskBasicParam.getVariousStringParam("VolumeString");
        int volume_length = volume_string.length();
        if (volume_length > 0) {
            volume_number = (byte)volume_string.charAt(0); // Assuming first char is the volume number byte
        }

        // FATエリア
        DiskBasicFatArea fats = fat.getDiskBasicFatArea();
        for(int n = 0; n < fats.size(); n++) {
            DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(n, 0);
            byte[] buf = fatbuf.getBuffer();
            int size = fatbuf.getSize();

            Arrays.fill(buf, basic.diskBasicParam.getFillCodeOnFAT());

            // Use ByteBuffer to access struct members
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN); // Assuming LE

            // struct st_fat_mz *fdat = (struct st_fat_mz *)buf;
            // Offset 0 for 'volume' byte
            bb.put(0, volume_number);
            // Offset 1 for 'offset' byte
            // トラック1 サイド1 から
            bb.put(1, (byte)(getSectorPosFromNum(1, 1, 1) / basic.getSectorsPerGroup()));
            // Offset 2 for 'used' short
            // 使用クラスタ数
            bb.putShort(2, bb.get(1)); // used = offset (assuming short casting is safe)
            // Offset 4 for 'all' short
            // 最大クラスタ数
            int all_groups = basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup();
            bb.putShort(4, (short)all_groups);
            // Offset 251 for 'mag' byte
            // セクタ数/グループ
            bb.put(251, (byte)(basic.getSectorsPerGroup() - 1));

            // invert
            basic.invertMem(buf, size);
        }

        // DIRエリア
        DirectoryMz ditv = new DirectoryMz();
        DirectoryMz ditm = new DirectoryMz();

        ditv.type = (byte)0x80;	// VOL
        Arrays.fill(ditv.name, (byte)0x0d);
        if (volume_length > 0) {
            byte[] vol_bytes = volume_string.getBytes();
            int copy_len = Math.min(volume_length, ditv.name.length);
            System.arraycopy(vol_bytes, 0, ditv.name, 0, copy_len);
        }
        Arrays.fill(ditm.name, (byte)0x0d);

        int[] trk_num = new int[1];
        int[] sid_num = new int[1];
        int[] sec_num = new int[1];
        int index = 0;
        int dir_item_size = 16; // Assuming directory item size is 16 based on C++ usage: sizeof(ditm)

        for (int sec_pos = basic.diskBasicParam.getDirStartSector(); sec_pos <= basic.diskBasicParam.getDirEndSector(); sec_pos++) {
            getNumFromSectorPos(sec_pos - 1, trk_num, sid_num, sec_num);
            sector = basic.getDisk().getSector(trk_num[0], sid_num[0], sec_num[0]);
            if (sector != null) {
                byte[] buf = sector.getSectorBuffer();
                int pos = 0;
                while(pos < sector.getSectorBufferSize()) {
                    if (index == 0) {
                        // 先頭にはボリューム番号を設定
                        // Copy ditv fields into buf[pos]
                        buf[pos] = ditv.type;
                        System.arraycopy(ditv.name, 0, buf, pos + 1, ditv.name.length);
                    } else {
                        // Copy ditm fields into buf[pos]
                        buf[pos] = ditm.type;
                        System.arraycopy(ditm.name, 0, buf, pos + 1, ditm.name.length);
                    }
                    pos += dir_item_size;
                    index++;
                }
                // invert
                basic.invertMem(buf, sector.getSectorBufferSize());
            }
        }

        return true;
    }

    /** データの読み込み/比較処理 */
    public int AccessFile(int fileunit_num, DiskBasicDirItem item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size_orig, int remain_size, int sector_num, int sector_end) {
        boolean need_chain = item.needChainInData();

        int sector_size = sector_size_orig;
        if (need_chain) {
            // セクタの最終バイトはチェイン用セクタ番号がある
            sector_size -= 2;
        }

        int size = (remain_size < sector_size ? remain_size : sector_size);

        if (ostream != null) {
            // 書き出し
            temp.setData(sector_buffer, size, basic.isDataInverted());

            try {
                ostream.write(temp.getData(), 0, temp.getSize());
            } catch (Exception e) {
                // Handle IOException
                return -2;
            }
        }
        if (istream != null) {
            // 読み込んで比較
            temp.setSize(size);
            byte[] temp_data = temp.getData();
            try {
                int bytesRead = istream.read(temp_data, 0, temp.getSize());
                if (bytesRead != temp.getSize()) {
                    // Handle incomplete read if necessary, though C++ stream behavior might differ
                }
            } catch (Exception e) {
                // Handle IOException
                return -2;
            }
            temp.invertData(basic.isDataInverted());

            if (!Arrays.equals(temp_data, 0, temp.getSize(), sector_buffer, 0, temp.getSize())) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /** データの書き込み処理 */
    public int WriteFile(DiskBasicDirItem item, InputStream istream, byte[] buffer, int size_orig, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) {
        boolean need_chain = item.needChainInData();

        int size = size_orig;
        int len = 0;
        if (need_chain) {
            size -= 2;
        }

        try {
            if (remain <= size) {
                // 残り少ない
                if (remain < 0) remain = 0;
                if (remain > 0) {
                    istream.read(buffer, 0, remain);
                }
                if (size > remain) {
                    // バッファの余りは0サプレス
                    Arrays.fill(buffer, remain, size, (byte)0);
                }
                len = remain;
            } else {
                // 継続
                istream.read(buffer, 0, size);
                len = size;
            }
        } catch (Exception e) {
            // Handle IOException
            return 0; // Assuming 0 is returned on stream error
        }

        // チェーン用のセクタ番号を書く
        if (need_chain) {
            int next_sector = group_num * basic.getSectorsPerGroup();
            // 次のデータがあるセクタ番号を入れる
            if (sector_num < sector_end) {
                next_sector++;
            } else {
                next_sector = (remain > size ? next_group * basic.getSectorsPerGroup() : 0);
            }

            short next_sector_short = basic.invertAndOrderUint16((short)next_sector);
            // *((wxUint16 *)&buffer[size]) = basic->InvertAndOrderUint16(next_sector);
            ByteBuffer.wrap(buffer, size, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(0, next_sector_short);
        }

        // 反転
        basic.invertMem(buffer, size_orig);

        return len;
    }

    /** データの書き込み終了後の処理 */
    public void AdditionalProcessOnSavedFile(DiskBasicDirItem item) throws IOException {
        if (item == null || !item.getFileAttr().isDirectory()) return;

        // ディレクトリの場合は、下位にあるボリューム名も変更する
        DiskImageSector sector = basic.getSectorFromGroup(item.getStartGroup(0));
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();

        // Create a temporary item for the volume name entry
        DiskBasicDirItem newitem = basic.createDirItem(sector, 0, buf);

        // ボリューム名をコピー
        if (!newitem.isSameFileName(item, basic.isCompareCaseInsense())) {
            newitem.copyFileName(item);
        }
    }

    /** ファイル名変更後の処理 */
    public void AdditionalProcessOnRenamedFile(DiskBasicDirItem item) throws IOException {
        AdditionalProcessOnSavedFile(item);
    }

    /** IPLや管理エリアの属性を得る */
    public void GetIdentifiedData(DiskBasicIdentifiedData data) {
        // ルートディレクトリのボリューム番号
        DiskBasicDirItem ditem = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(), FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
        if (ditem != null) {
            byte[] buf = new byte[8];
            buf[0] = 0;
            ditem.getFileName(buf, buf.length);
            data.setVolumeNumber(buf[0]);
        }
    }

    /** IPLや管理エリアの属性をセット */
    public void SetIdentifiedData(DiskBasicIdentifiedData data) {
        // No implementation in C++, so empty in Java
    }
}
