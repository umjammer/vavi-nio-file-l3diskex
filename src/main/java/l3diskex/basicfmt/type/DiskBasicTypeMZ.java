/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryMz;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 MZ S-BASICの処理

 DiskBasicParam 固有パラメータ
 @li	IPLString         セクタ1のIPL
 @li	VolumeString      ディレクトリ先頭のボリューム番号
 @li	SpecialAttributes 特別な属性 Volume属性にする属性値を指定
 */
public class DiskBasicTypeMZ extends DiskBasicTypeMZBase<DirectoryMz> {

    /// MZ 使用状況セクタ
    @Serdes(bigEndian = false)
    private static class st_fat_mz {

        // ボリューム番号
        @Element(sequence = 1)
        public byte volume;
        // オフセット データ領域開始クラスタ
        @Element(sequence = 2)
        public byte offset;
        // 使用クラスタ数
        @Element(sequence = 3)
        public short used;
        // 全体クラスタ数
        @Element(sequence = 4)
        public short all;
        // 使用状況
        @Element(sequence = 5)
        public byte[] bits = new byte[0xf9];
        // １クラスタのセクタ数-1 (251)
        @Element(sequence = 6)
        public byte mag;

        public static final int SIZE = 252;
    }

    public DiskBasicTypeMZ(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMz> dir) {
        super(basic, fat, dir);
    }

    /** FAT位置をセット */
    @Override
    public void setGroupNumber(int num, int val) throws IOException {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        int[] pos = {num - dataStartGroup};
        if (pos[0] < 0) {
            return;
        }

        int[] mask = {0};
        calcUsedGroupPos(num, pos, mask);

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return;
        }
        // FATには未使用使用テーブルがある
        fatbuf.bit(pos[0], (byte) mask[0], val != 0, basic.isDataInverted());

        // FATの使用済み最終クラスタ数を更新
        st_fat_mz b = new st_fat_mz();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), b);
        int used_group = basic.invertAndOrderUint16(b.used);
        if (val != 0) {
            // セットした時は最終クラスタ数を増やす
            used_group++;
        } else {
            // クリアした時は最終クラスタ数を減らす
            used_group--;
        }
        basic.invertAndOrderUint16((short) used_group); // invert

        // TODO Write back
//looger.log(Level.TRACE, "DiskBasicTypeMZ::SetGroupNumber: g:%d v:%d pos:%d msk:%d used:%d".formatted(num, val, pos, mask, used_group));
    }

    /** FATオフセットを返す */
    @Override
    public int getGroupNumber(int num) throws IOException {
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return DiskBasicType.INVALID_GROUP_NUMBER;
        }
        st_fat_mz b = new st_fat_mz();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), b);
        int offset = basic.invertUint8(b.offset) & 0xff; // invert
        if (offset > num) num = offset;
        return num;
    }

    /** 使用しているグループの位置を得る */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3) + 6;
    }

    /** 次のグループ番号を得る */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        int[] trk_num = new int[1], sid_num = new int[1], sec_num = new int[1];
        basic.calcNumFromSectorPosForGroup(sector_pos, trk_num, sid_num, sec_num, null, null);
        DiskImageSector sector = basic.getDisk().getSector(trk_num[0], sid_num[0], sec_num[0]);
        if (sector == null) return 0;
        short next_sec = ByteUtil.readBeShort(sector.getSectorBuffer(), sector.getSectorSize() - 2); // TODO be?
        next_sec = (short) (basic.invertAndOrderUint16(next_sec) & 0xFFFF); // invert, convert to unsigned int
        return next_sec / basic.getSectorsPerGroup();
    }

    /** 空きFAT位置を返す */
    @Override
    public int getEmptyGroupNumber() throws IOException {
        int new_num = DiskBasicType.INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return new_num;
        }
        // FATの使用済み最終クラスタを得る
        st_fat_mz b = new st_fat_mz();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), b);
        int offset = basic.invertUint8(b.offset) & 0xff; // invert
        int used_group = basic.invertAndOrderUint16(b.used) & 0xffff;  // invert
        int all_group = basic.invertAndOrderUint16(b.all) & 0xffff; // invert

        if (used_group < all_group) {
            new_num = used_group;
        }
        if (new_num < offset) {
            new_num = offset;
        }
        return new_num;
    }

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean is_formatting) throws IOException {
        double valid_ratio = 1.0;

        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return -1.0;
        }
        st_fat_mz b = new st_fat_mz();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), b);
        int offset = basic.invertUint8(b.offset) & 0xff; // invert
        int mag = basic.invertUint8(b.mag) & 0xff; // invert
        // オフセット(1バイト目)が16未満ならエラー
        if (offset < 16) {    // invert
            valid_ratio = -1.0;
        }
        if (valid_ratio >= 0.0) {
            dataStartGroup = offset;
        }

        // クラスタ倍率(255バイト目)が16以上ならエラー
        if (mag >= 16) {    // invert
            valid_ratio = -1.0;
        }
        if (valid_ratio >= 0.0) {
            // セクタ数/クラスタ
            basic.diskBasicParam.setSectorsPerGroup(mag + 1);
            // クラスタ数
            basic.diskBasicParam.setFatEndGroup((basic.invertAndOrderUint16(b.all) & 0xffff) - 1); // invert, convert to unsigned int
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
    @Override
    public boolean assignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryMz> dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);

        // 開始グループ
        int sec_pos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + start_sector - 1;
        sec_pos /= basic.getSectorsPerGroup();
        dir_item.setStartGroup(0, sec_pos);

        return sts;
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        int used = 0;
        fatAvailability.clear();

        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return;
        }
        st_fat_mz b = new st_fat_mz();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), b);
        int used_groups = basic.invertAndOrderUint16(b.used) & 0xffff;    // invert

        // 使用済みかチェック
        int grps = 0;
        FatAvailability fsts;
        for (int gnum = 0; gnum <= basic.getFatEndGroup(); gnum++) {
            if (gnum < dataStartGroup) {
                used++;
                fsts = FAT_AVAIL_SYSTEM;
            } else if (!isUsedGroupNumber(gnum)) {
                grps++;
                fsts = FAT_AVAIL_FREE;
            } else {
                used++;
                fsts = FAT_AVAIL_USED;
            }
            fatAvailability.add(fsts, 0, 0);
        }

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectoryMz>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (int idx = 0; idx < items.size(); idx++) {
                DiskBasicDirItem<DirectoryMz> item = items.get(idx);
                if (item == null || !item.isUsed()) continue;

                // グループ番号のマップを調べる
                int gcnt = item.getGroupCount();
                if (gcnt > 0) {
                    DiskBasicGroupItem gitem = item.getGroup(gcnt - 1);
                    int gnum = gitem.group;
                    if (gnum <= basic.getFatEndGroup()) {
                        fatAvailability.set(gnum, FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        int fsize = grps * basic.getSectorsPerGroup() * basic.getSectorSize();

        // 使用済みクラスタ数を更新
        if (wrote && used_groups != used) {
            b.used = basic.invertAndOrderUint16((short) used);    // invert
        }

        fatAvailability.setFreeSize(fsize);
        fatAvailability.setFreeGroups(grps);

        // TODO write back
    }

    // Assumed to be defined in DiskBasicTypeMZBase
    @Override
    public boolean isUsedGroupNumber(int gnum) {
        return false;
    }

    /** データサイズ分のグループを確保する */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryMz> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) throws IOException {
        int[] file_size = {0};
        int[] groups = {0};

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

        // 未使用が連続している位置をさがす
        int[] group_start = new int[1];
        int cnt = findContinuousArea(group_size, group_start);
        if (cnt < group_size) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // 開始グループ決定
        item.setStartGroup(fileunit_num, group_start[0]);

        if (is_brd) {
            // BRD ランダムアクセス
            // マップ領域を確保
            DiskImageSector sector = basic.getSectorFromGroup(group_start[0]);
            if (sector == null) {
                // セクタ無い？！
                rc = -1;
                return rc;
            }
            sector.fill(basic.invertUint8((byte) 0));    // invert

            ByteBuffer bb = ByteBuffer.wrap(sector.getSectorBuffer()).order(ByteOrder.BIG_ENDIAN);
            ShortBuffer brd_maps = bb.asShortBuffer();

            setGroupNumber(group_start[0], 1);

            int brd_pos = 0;

            do {
                // 連続した16セクタを確保できるかをさがす
                group_size = 16 / basic.getSectorsPerGroup();
                int bcnt = findContinuousArea(group_size, group_start);
                if (bcnt < group_size) {
                    // 十分な空きがない
                    rc = -2;
                    return rc;
                }

                // 開始セクタ
                int sec_pos = group_start[0] * basic.getSectorsPerGroup();
                sec_pos = basic.invertAndOrderUint16((short) sec_pos);    // invert
                brd_maps.put(brd_pos * 2, (short) sec_pos); // TODO chaek write back

                // 領域を確保する
                int block_remain = 16 * basic.getSectorSize();
                int[] block_size = {0};
                rc = allocateGroupsSub(item, group_start[0], block_remain, basic.getSectorSize(), group_items[0], block_size, groups);

                if (block_remain >= remain) {
                    file_size[0] += (((remain + 31) / 32) * 32);
                    break;
                }
                remain -= block_remain;
                file_size[0] += block_remain;
                brd_pos++;
            } while ((brd_pos * 2) < basic.getSectorSize());

        } else {
            // BRD 以外

            // 領域を確保する
            rc = allocateGroupsSub(item, group_start[0], remain, sec_size, group_items[0], file_size, groups);
        }
        return rc;
    }

    /** グループを確保して使用中にする */
    @Override
    public int allocateGroupsSub(DiskBasicDirItem<DirectoryMz> item, int group_start, int remain, int sec_size, DiskBasicGroups group_items, int[] file_size, int[] groups) throws IOException {
        int rc = 0;
        int group_num = group_start;
        int prev_group = 0;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // 使用しているか
            boolean used_group = isUsedGroupNumber(group_num);
            if (!used_group) {
                if (prev_group > 0 && prev_group <= basic.getFatEndGroup()) {
                    // 使用済みにする
                    basic.getNumsFromGroup(prev_group, group_num, sec_size, remain, group_items);
                    setGroupNumber(prev_group, 1);
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
            setGroupNumber(prev_group, 1);
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
    @Override
    public boolean isRootDirectory(int group_num) {
        // オフセット未満だったらルート
        return (basic.invertUint8((byte) fat.get(1)) & 0xFF) > group_num;    // invert
    }

    /** サブディレクトリを作成できるか */
    @Override
    public boolean canMakeDirectory() {
        return true;
    }

    /** サブディレクトリを作成する前にディレクトリ名を編集する */
    @Override
    public boolean renameOnMakingDirectory(String[] dir_name) {
        // 空や"."で始まるディレクトリは作成不可
        if (dir_name[0].isEmpty() || dir_name[0].startsWith(".")) {
            return false;
        }
        return true;
    }

    /** サブディレクトリを作成した後の個別処理 */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryMz> item, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryMz> parent_item) throws IOException {
        if (group_items.size() <= 0) return;

        // ボリューム番号、カレントと親ディレクトリのエントリを作成する
        DiskBasicGroupItem gitem = group_items.get(0);

        DiskImageSector sector = basic.getDisk().getSector(gitem.track, gitem.side, gitem.sectorStart);

        byte[] buf = sector.getSectorBuffer();
        int bufOffset = 0;
        DiskBasicDirItem<DirectoryMz> newitem = basic.createDirItem(sector, 0, buf, bufOffset);

        // Volume entry
        newitem.copyData(item.getRawData());
        newitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);

        bufOffset += newitem.getDataSize();
        newitem.setDataPtr(0, null, sector, 0, buf, bufOffset, null);

        // Current directory ('.')
        newitem.copyData(item.getRawData());
        newitem.setFileNamePlain(".");
        newitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);

        bufOffset += newitem.getDataSize();
        newitem.setDataPtr(0, null, sector, 0, buf, bufOffset, null);

        // Parent directory ('..')
        if (parent_item != null) {
            // 親がサブディレクトリ
            newitem.copyData(parent_item.getRawData());
        } else {
            // 親がルート
            newitem.copyData(item.getRawData());
            newitem.setStartGroup(0, 0);
        }
        newitem.setFileNamePlain("..");
        newitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_READONLY_MASK.getValue(), 0);
    }

    /** セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // IPL
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));    // invert
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
            volume_number = (byte) volume_string.charAt(0); // Assuming first char is the volume number byte
        }

        // FATエリア
        DiskBasicFatArea fats = fat.getDiskBasicFatArea();
        for (int n = 0; n < fats.size(); n++) {
            DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(n, 0);
            byte[] buf = fatbuf.getBuffer();
            int size = fatbuf.getSize();

            Arrays.fill(buf, basic.diskBasicParam.getFillCodeOnFAT());

            st_fat_mz fdat = new st_fat_mz();
            Serdes.Util.deserialize(new ByteArrayInputStream(buf), fdat);

            fdat.volume = volume_number;
            // トラック1 サイド1 から
            fdat.offset = (byte) (getSectorPosFromNum(1, 1, 1) / basic.getSectorsPerGroup());
            // 使用クラスタ数
            fdat.used = fdat.offset;
            // 最大クラスタ数
            //fdat.all = basic.getFatEndGroup() + 1;
            fdat.all = (short) (basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup());
            // セクタ数/グループ
            fdat.mag = (byte) (basic.getSectorsPerGroup() - 1);

            // invert
            basic.invertMem(buf, size);

            // TODO write back fdat
        }

        // DIRエリア
        DirectoryMz ditv = new DirectoryMz(), ditm = new DirectoryMz();
        ditv.type = (byte) 0x80;    // VOL
        Arrays.fill(ditv.name, (byte) 0x0d);
        if (volume_length > 0) {
            System.arraycopy(volume_string.getBytes(), 0, ditv.name, 0, volume_length);
        }
        Arrays.fill(ditm.name, (byte) 0x0d);

        int[] trk_num = new int[1], sid_num = new int[1], sec_num = new int[1];
        int index = 0;
        for (int sec_pos = basic.diskBasicParam.getDirStartSector(); sec_pos <= basic.diskBasicParam.getDirEndSector(); sec_pos++) {
            getNumFromSectorPos(sec_pos - 1, trk_num, sid_num, sec_num);
            sector = basic.getDisk().getSector(trk_num[0], sid_num[0], sec_num[0]);
            if (sector != null) {
                byte[] buf = sector.getSectorBuffer();
                int pos = 0;
                while (pos < sector.getSectorBufferSize()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    if (index == 0) {
                        // 先頭にはボリューム番号を設定
                        Serdes.Util.serialize(ditv, baos);
                        System.arraycopy(baos.toByteArray(), 0, buf, pos, DirectoryMz.SIZE); // TODO this may not write back, we need setSectorBuffer method
                    } else {
                        Serdes.Util.serialize(ditm, baos);
                        System.arraycopy(baos.toByteArray(), 0, buf, pos, DirectoryMz.SIZE); // TODO this may not write back
                    }
                    pos += DirectoryMz.SIZE;
                    index++;
                }
                // invert
                basic.invertMem(buf, sector.getSectorBufferSize());
            }
        }

        return true;
    }

    /** データの読み込み/比較処理 */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryMz> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size_orig, int remain_size, int sector_num, int sector_end) throws IOException {
        boolean need_chain = item.needChainInData();

        int sector_size = sector_size_orig;
        if (need_chain) {
            // セクタの最終バイトはチェイン用セクタ番号がある
            sector_size -= 2;
        }

        int size = remain_size < sector_size ? remain_size : sector_size;

        if (ostream != null) {
            // 書き出し
            temp.setData(sector_buffer, size, basic.isDataInverted());

            ostream.write(temp.getData(), 0, temp.getSize());
        }
        if (istream != null) {
            // 読み込んで比較
            temp.setSize(size);
            int bytesRead = istream.read(temp.getData(), 0, temp.getSize());
            temp.invertData(basic.isDataInverted());

            if (!Arrays.equals(temp.getData(), 0, temp.getSize(), sector_buffer, 0, temp.getSize())) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /** データの書き込み処理 */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryMz> item, InputStream istream, byte[] buffer, int size_orig, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        boolean need_chain = item.needChainInData();

        int size = size_orig;
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

        // チェーン用のセクタ番号を書く
        if (need_chain) {
            int next_sector = group_num * basic.getSectorsPerGroup();
            // 次のデータがあるセクタ番号を入れる
            if (sector_num < sector_end) {
                next_sector++;
            } else {
                next_sector = (remain > size ? next_group * basic.getSectorsPerGroup() : 0);
            }
            next_sector = basic.invertAndOrderUint16((short) next_sector);
            ByteBuffer.wrap(buffer, size, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(0, (short) next_sector);
        }

        // 反転
        basic.invertMem(buffer, size_orig);

        return len;
    }

    /** データの書き込み終了後の処理 */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryMz> item) throws IOException {
        if (item == null || !item.getFileAttr().isDirectory()) return;

        // ディレクトリの場合は、下位にあるボリューム名も変更する
        DiskImageSector sector = basic.getSectorFromGroup(item.getStartGroup(0));
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();
        DiskBasicDirItem<DirectoryMz> newitem = basic.createDirItem(sector, 0, buf, 0);

        // ボリューム名をコピー
        if (!newitem.isSameFileName(item, basic.isCompareCaseInsense())) {
            newitem.copyFileName(item);
        }
    }

    /** ファイル名変更後の処理 */
    @Override
    public void additionalProcessOnRenamedFile(DiskBasicDirItem<DirectoryMz> item) throws IOException {
        additionalProcessOnSavedFile(item);
    }

    /** IPLや管理エリアの属性を得る */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // ルートディレクトリのボリューム番号
        DiskBasicDirItem<DirectoryMz> ditem = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(), FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
        if (ditem != null) {
            byte[] buf = new byte[8];
            buf[0] = 0;
            ditem.getFileName(buf, buf.length);
            data.setVolumeNumber(buf[0]);
        }
    }

    /** IPLや管理エリアの属性をセット */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
    }
}
