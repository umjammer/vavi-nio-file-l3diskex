package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFLEX.DirectoryFlex;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFLEX.FlexPtr;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_NEW;


/**
 * FLEXの処理
 * <p>
 * DiskBasicParam
 *
 * @li DirStartPositionOnSector : ディレクトリエントリの開始位置
 */
public class DiskBasicTypeFLEX extends DiskBasicType<DirectoryFlex> {

    private static final Logger logger = System.getLogger(DiskBasicTypeFLEX.class.getName());

    @Serdes
    public static class FlexSirT {

        @Element(sequence = 1)
        public byte[] reserved0 = new byte[16];
        @Element(sequence = 2)
        public byte[] volume_label = new byte[8];
        @Element(sequence = 3)
        public byte[] reserved1 = new byte[3];
        @Element(sequence = 4)
        public short volume_number;
        @Element(sequence = 5)
        public byte free_start_track;
        @Element(sequence = 6)
        public byte free_start_sector;
        @Element(sequence = 7)
        public byte free_last_track;
        @Element(sequence = 8)
        public byte free_last_sector;
        @Element(sequence = 9)
        public short free_sector_nums;
        @Element(sequence = 10)
        public byte cmonth;
        @Element(sequence = 11)
        public byte cday;
        @Element(sequence = 12)
        public byte cyear;
        @Element(sequence = 13)
        public byte max_track;
        @Element(sequence = 14)
        public byte max_sector;

        static final int SIZE = 16 + 8 + 3 + 2 + 1 + 1 + 1 + 1 + 2 + 1 + 1 + 1 + 1 + 1;
    }

    @Serdes
    static class StFlexFsm {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte count;

        static final int SIZE = 3;
    }

    /** SIRエリア */
    private FlexSirT flex_sir;

    /** */
    public DiskBasicTypeFLEX(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFlex> dir) {
        super(basic, fat, dir);

        flex_sir = null;

        if (basic.diskBasicParam.getGroupsPerTrack() <= 0) {
            basic.diskBasicParam.setGroupsPerTrack(basic.diskBasicParam.getGroupsPerSector() * basic.diskBasicParam.getSectorsPerTrackOnBasic());
        }
    }

    /** 論理セクタ番号からセクタ内の位置を得る */
    private int secBufOfs(int sector_number) {
        int pos = (sector_number - 1) % basic.diskBasicParam.getGroupsPerSector();
        return pos * basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector();
    }

    /** 論理セクタサイズ */
    private int logSecSiz(int sector_size) {
        return sector_size / basic.diskBasicParam.getGroupsPerSector();
    }

    /** FAT位置をセット (seq_numをセット) */
    @Override
    public void setGroupNumber(int num, int val) throws IOException {
        int div_num = 0;
        DiskImageSector sector = basic.getSectorFromSectorPos(num, new int[] {div_num});
        if (sector == null) {
            // why?
            return;
        }
        int[] div_num_holder = new int[] {div_num};
        FlexPtr p = new FlexPtr();
        byte[] b = sector.getSectorBuffer(secBufOfs(div_num_holder[0] + 1));
        Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
        if (p == null) {
            // why?
            return;
        }
        p.seqNum = (short) val;
        // TODO write back
    }

    /** FATオフセットを返す (FLEXではグループ番号=セクタ位置) */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /** 使用しているグループ番号か (FLEXでは常にtrue) */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /** 次のグループ番号を得る (FLEXではグループはFATでなくチェインでたどるため、通常はINVALID_GROUP_NUMBER) */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /** 空きFAT位置を返す */
    @Override
    public int getEmptyGroupNumber() throws IOException {
        DiskImageSector sector = null;
        int group_num = INVALID_GROUP_NUMBER;
        int sta_track_num = flex_sir.free_start_track & 0xff;
        int sta_lsector_num = flex_sir.free_start_sector & 0xff;
        if (sta_track_num == 0 && sta_lsector_num == 0) {
            // no free space ?
            return INVALID_GROUP_NUMBER;
        }
        int[] div_num = {0};
        // グループ番号を得る
        group_num = getSectorPosFromNumS(sta_track_num, sta_lsector_num);
        sector = basic.getSectorFromSectorPos(group_num, div_num);
        if (sector == null) {
            // no free space ?
            return INVALID_GROUP_NUMBER;
        }

        // 次のポインタをフリーセクタポインタに設定
        byte[] b = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));
        if (b == null) {
            // why?
            return INVALID_GROUP_NUMBER;
        }
        FlexPtr p = new FlexPtr();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
        // 空き領域の開始ポインタを更新
        flex_sir.free_start_track = p.nextTrack;
        flex_sir.free_start_sector = p.nextSector;
        int size = flex_sir.free_sector_nums;
        size--;
        if (size <= 0 || (flex_sir.free_start_track == 0 && flex_sir.free_start_sector == 0)) {
            // 空きがなくなった
            size = 0;
            flex_sir.free_start_track = 0;
            flex_sir.free_start_sector = 0;
            flex_sir.free_last_track = 0;
            flex_sir.free_last_sector = 0;
        }
        flex_sir.free_sector_nums = (short) size;
        // 予約済みにする
        p.nextTrack = 0;
        p.nextSector = 0;

        return group_num;
    }

    /** 次の空きFAT位置を返す */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) throws IOException {
        // 次の空き位置候補
        int next_group_num = getEmptyGroupNumber();
        if (next_group_num == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // 現在のセクタに次のセクタへのポインタをセット
        if (chainGroups(curr_group, next_group_num) < 0) {
            return INVALID_GROUP_NUMBER;
        }

        return next_group_num;
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
    public double checkFat(boolean is_formatting) throws IOException {
        // SIR area
        DiskImageSector sector = basic.getSectorFromSectorPos(2);
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer(secBufOfs(2 + 1));
        if (b == null) {
            return -1.0;
        }
        FlexSirT flex = new FlexSirT();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), flex);

        for (int i = 0; i < flex.reserved0.length; i++) {
            if (flex.reserved0[i] != 0) {
                return -1.0;
            }
        }
        if (flex.max_track == 0 || flex.max_sector == 0) {
            return -1.0;
        }

        // 最終グループ番号
        basic.diskBasicParam.setFatEndGroup(((flex.max_track & 0xff) + 1) * (flex.max_sector & 0xff) - 1);

        flex_sir = flex;

        double valid_ratio = 1.0;

        // DIRエリアのチェインをチェック
        int dir_cnt = 0;
        int dir_sta_lsec = basic.diskBasicParam.getDirStartSector(); // logical
        int dir_end_lsec = basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getGroupsPerSector(); // logical

        sector = basic.getSectorFromSectorPos(dir_sta_lsec - 1);
        int secofs = secBufOfs(dir_sta_lsec);
        for (int lsec_pos = dir_sta_lsec; lsec_pos <= dir_end_lsec; lsec_pos++) {
            if (sector == null) {
                valid_ratio = -1.0;
                break;
            }

            FlexPtr p = new FlexPtr();
            b = sector.getSectorBuffer(secofs);
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            dir_cnt++;

            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(p.nextTrack & 0xff, p.nextSector & 0xff));
            //sector = basic.getSector(p.nextTrack, phySecNum(p.nextSector));
            secofs = secBufOfs(p.nextSector & 0xff);
        }
        // DIRエリアの最終セクタ
        int dir_end = basic.diskBasicParam.getDirStartSector() + dir_cnt - 1;
        basic.diskBasicParam.setDirEndSector(dir_end); // logical

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
    public double parseParamOnDisk(boolean is_formatting) throws IOException {
        if (is_formatting) return 1.0;

        if (flex_sir == null) {
            DiskImageSector sector = basic.getSectorFromSectorPos(2);
            if (sector == null) {
                return -1.0;
            }
            FlexSirT flex = new FlexSirT();
            byte[] bb = sector.getSectorBuffer(secBufOfs(2 + 1));
            Serdes.Util.deserialize(new ByteArrayInputStream(bb), flex);
            flex_sir = flex;
        }

        logger.log(Level.TRACE, "FLEX: sir.max_track: %d".formatted(flex_sir.max_track & 0xff));
        if (flex_sir.max_track > 0) {
            basic.diskBasicParam.setTracksPerSideOnBasic((flex_sir.max_track & 0xff) + 1);
        }
        logger.log(Level.TRACE, "FLEX: sir.max_sector: %d".formatted( flex_sir.max_sector & 0xff));
        if (flex_sir.max_sector > 0) {
            basic.diskBasicParam.setSectorsPerTrackOnBasic((flex_sir.max_sector & 0xff) / basic.getSidesPerDiskOnBasic() / basic.diskBasicParam.getGroupsPerSector());
        }

        return 1.0;
    }

    /** ルートディレクトリのセクタリストを計算 */
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) throws IOException {
        boolean valid = true;

        group_items.clear();

        // ディレクトリのチェインをたどる
        int dir_size = 0;
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        int[] trk_num = {0};
        int[] sid_num = {0};
        int sec_num = 1;
        int[] div_num = {0};
        int[] div_nums = {1};
        // 開始セクタ
        DiskImageSector sector = basic.getManagedSector(start_sector - 1, trk_num, sid_num, null, div_num, div_nums);
        while (limit >= 0) {
            if (sector == null) {
                valid = false;
                break;
            }
            sec_num = sector.getSectorNumber();

            byte[] buffer = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));
            if (buffer == null) {
                valid = false;
                break;
            }
            byte[] b = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));

            group_items.add(0, 0, trk_num[0], sid_num[0], sec_num, sec_num, div_num[0], div_nums[0]);

            dir_size += logSecSiz(sector.getSectorSize());

            FlexPtr p = new FlexPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            // 次のセクタなし
            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            limit--;

            // 次のセクタを得る
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(p.nextTrack & 0xff, p.nextSector & 0xff), trk_num, sid_num, div_num, div_nums);
        }
        group_items.setSize(dir_size);

        // 最終セクタ番号を更新
        int sec_pos = getSectorPosFromNum(trk_num[0], sid_num[0], sec_num, 0, 1);
        sec_pos -= (basic.diskBasicParam.getManagedTrackNumber() * basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic());
        basic.diskBasicParam.setDirEndSector(sec_pos + 1); // logical

        if (limit < 0) {
            valid = false;
        }
        return valid;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.diskBasicParam.getFatEndGroup() + 1;
        disk_size[0] = group_size[0] * basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup() / basic.diskBasicParam.getGroupsPerSector();
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        int fsize = 0;
        int grps = 0;

        //logger.log(Level.TRACE, "DiskBasicTypeFLEX::CalcDiskFreeSize");

        fatAvailability.empty();

//        fatAvailability.setCount(basic.diskBasicParam.getFatEndGroup() + 1, FAT_AVAIL_USED.getValue());

        // SIR area
        //DiskImageDisk disk = basic.getDisk();
        DiskImageSector sector;
        //flex_sir_t flex = (flex_sir_t)sector.getSectorBuffer();

        int track_num = flex_sir.free_start_track & 0xff;
        int lsector_num = flex_sir.free_start_sector & 0xff;
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        int[] div_num = {0};
        int[] div_nums = {1};
        while ((track_num != 0 || lsector_num != 0) && limit >= 0) {
            int sector_pos = getSectorPosFromNumS(track_num, lsector_num);
            sector = basic.getSectorFromSectorPos(sector_pos, div_num, div_nums);
            //sector = basic.getSector(track_num, sector_num);
            if (sector == null) {
                // error
                break;
            }
            if (sector_pos < fatAvailability.count()) {
                if (fatAvailability.get(sector_pos) == FAT_AVAIL_FREE) {
                    // 既に空きエリアにしているのに同じセクタにきている
                    // 無限ループしている？
                    break;
                }
                fatAvailability.set(sector_pos, FAT_AVAIL_FREE);
            }

            // セクタ先頭4バイトは除く
            fsize += (logSecSiz(sector.getSectorSize()) - 4);
            grps++;

            //logger.log(Level.TRACE, "trk:%d sec:%d size:%d".formatted(track_num, sector_num, fsize));

            byte[] b = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));
            FlexPtr p = new FlexPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
            track_num = p.nextTrack & 0xff;
            lsector_num = p.nextSector & 0xff;
            limit--;
        }

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectoryFlex>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (int idx = 0; idx < items.size(); idx++) {
                DiskBasicDirItem<DirectoryFlex> item = items.get(idx);
                if (item == null || !item.isUsed()) continue;

                // 最後のグループ
                int gcnt = item.getGroupCount();
                if (gcnt > 0) {
                    int gnum = item.getGroup(gcnt - 1).group;
                    if (gnum <= basic.diskBasicParam.getFatEndGroup()) {
                        fatAvailability.set(gnum, FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        fatAvailability.setFreeSize(fsize);
        fatAvailability.setFreeGroups(grps);
    }

    /** データサイズ分のグループを確保する */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryFlex> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) throws IOException {
        //logger.log(Level.TRACE, "DiskBasicTypeFLEX::AllocateGroups {");

        //int file_size = data_size;
        int groups = 0;

        // FAT
        int rc = 0;
        boolean first_group = flags == ALLOCATE_GROUPS_NEW;
        int group_num = flags == ALLOCATE_GROUPS_NEW ? INVALID_GROUP_NUMBER : item.getLastGroup();

        // 1セクタ当たり4バイトはチェイン用のリンクポインタになるので減算
        int bytes_per_group = basic.diskBasicParam.getSectorsPerGroup() * (logSecSiz(basic.getSectorSize()) - 4);
        // ランダムアクセスファイルか
        int random_file = item.getFileAttr().getOrigin();
        if (flags == ALLOCATE_GROUPS_NEW && random_file > 0) {
            // ランダムアクセスファイル
            // インデックス(FSM)セクタを確保
            for (int idx = 0; idx < random_file; idx++) {
                group_num = first_group ? getEmptyGroupNumber() : getNextEmptyGroupNumber(group_num);
                if (group_num == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = first_group ? -1 : -2;
                    break;
                }
                // セクタをクリア
                int div_num = 0;
                int[] div_num_holder = new int[] {div_num};
                DiskImageSector sector = basic.getSectorFromGroup(group_num, div_num_holder, null);
                div_num = div_num_holder[0];
                if (sector != null) {
                    sector.fill((byte) 0, logSecSiz(basic.getSectorSize()), secBufOfs(div_num + 1));
                }
                // グループ番号の書き込み
                if (first_group) {
                    item.setStartGroup(fileunit_num, group_num);
                    first_group = false;
                }
            }
        }

        int sizeremain = data_size;
        int limit = basic.getFatEndGroup() + 1;
        while (rc >= 0 && limit >= 0 && sizeremain > 0) {
            group_num = first_group ? getEmptyGroupNumber() : getNextEmptyGroupNumber(group_num);
            if (group_num == INVALID_GROUP_NUMBER) {
                // 空きなし
                rc = first_group ? -1 : -2;
                break;
            }
            // グループ番号の書き込み
            if (first_group) {
                item.setStartGroup(fileunit_num, group_num);
                first_group = false;
            }

            //logger.log(Level.TRACE, "  group_num:0x%03x".formatted(group_num));

            basic.getNumsFromGroup(group_num, 0, logSecSiz(basic.getSectorSize()), sizeremain, group_items[0]);

            // シーケンス番号設定
            setGroupNumber(group_num, groups + 1);

            sizeremain -= bytes_per_group;
            groups++;

            limit--;
        }
        if (limit < 0) {
            // too large or infinit loop
            rc = first_group ? -1 : -2;
        }
        if (rc == 0) {
            // 最終グループ番号
            item.setLastGroup(group_num);

            // ランダムアクセスファイルの場合は、インデックスを作成する
            if (random_file > 0) {
                DiskBasicGroups random_groups = new DiskBasicGroups();
                int prev_trk = -1;
                int prev_grp = 0xfff_ffff;
                int idx_count = 0;
                int idx_start = 0;
                for (int i = 0; i < group_items[0].size(); i++) {
                    DiskBasicGroupItem gitm = group_items[0].get(i);
                    if (prev_grp == gitm.group) {
                        // 同じならスキップ
                        continue;
                    }
                    if (prev_trk == gitm.track && prev_grp + 1 == gitm.group) {
                        // グループ番号が連続している
                        idx_count++;
                    } else {
                        // グループ番号が連続していない
                        if (idx_count > 0) {
                            random_groups.add(idx_start, idx_count, 0, 0, 0, 0, 0, basic.diskBasicParam.getGroupsPerSector());
                        }
                        idx_start = gitm.group;
                        idx_count = 1;
                    }
                    prev_trk = gitm.track;
                    prev_grp = gitm.group;
                }
                if (idx_count > 0) {
                    random_groups.add(idx_start, idx_count, 0, 0, 0, 0, 0, basic.diskBasicParam.getGroupsPerSector());
                }
                // インデックス(FSM)セクタに書き込む
                DiskImageSector isector = null;
                int current_idx_start = item.getStartGroup(fileunit_num);
                int idx = 0;
                boolean finished = false;
                for (int sec = 0; sec < random_file && !finished; sec++) {
                    int div_num = 0;
                    int[] div_num_holder = new int[] {div_num};
                    isector = basic.getSectorFromGroup(current_idx_start, div_num_holder, null);
                    div_num = div_num_holder[0];
                    if (isector == null) break;
                    byte[] buf = isector.getSectorBuffer(secBufOfs(div_num + 1));
                    if (buf == null) break;
                    for (int pos = 4; pos < logSecSiz(isector.getSectorSize()); pos += StFlexFsm.SIZE) {
                        DiskBasicGroupItem ritem = random_groups.get(idx);
                        int[] trk_num = {0}, sec_num = {0};
                        getNumFromSectorPosS(ritem.group, trk_num, sec_num);
                        StFlexFsm fsm = new StFlexFsm();
                        Serdes.Util.deserialize(new ByteArrayInputStream(buf, pos, buf.length - pos), fsm);
                        fsm.track = (byte) trk_num[0];
                        fsm.sector = (byte) sec_num[0];
                        fsm.count = (byte) ritem.next;
                        // TODO write back
                        idx++;
                        if (idx >= random_groups.size()) {
                            finished = true;
                            break;
                        }
                    }
                    FlexPtr p = new FlexPtr();
                    Serdes.Util.deserialize(new ByteArrayInputStream(buf), p);
                    current_idx_start = getSectorPosFromNumS(p.nextTrack & 0xff, p.nextSector & 0xff);
                }
            }
        } else {
            // エラー時
            // 確保した領域を削除
            deleteGroups(group_items[0]);
            // 空き領域をチェインする
            int last_group = item.getLastGroup();
            if (last_group != 0) {
                int div_num = 0;
                int[] div_num_holder = new int[] {div_num};
                DiskImageSector sector = basic.getSectorFromGroup(last_group, div_num_holder, null);
                div_num = div_num_holder[0];
                if (sector != null) {
                    byte[] b = sector.getSectorBuffer(secBufOfs(div_num + 1));
                    if (b != null) {
                        FlexPtr p = new FlexPtr();
                        Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
                        flex_sir.free_last_track = p.nextTrack;
                        flex_sir.free_last_sector = p.nextSector;
                        p.nextTrack = 0;
                        p.nextSector = 0;
                        p.seqNum = 0;

                        remakeChainOnFreeArea();

                        rc = -1;
                    }
                }
            }
        }

        //logger.log(Level.TRACE, "rc: %d }".formatted(rc));
        return rc;
    }

    /** グループをつなげる */
    @Override
    public int chainGroups(int group_num, int append_group_num) throws IOException {
        // 現在のセクタに次のセクタへのポインタをセット
        int[] div_num = {0};
        DiskImageSector sector = basic.getSectorFromSectorPos(group_num, div_num);
        if (sector == null) {
            // why?
            return -1;
        }
        byte[] b = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));
        if (b == null) {
            // why?
            return -1;
        }
        FlexPtr p = new FlexPtr();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
        int[] next_track_num = {0};
        int[] next_sector_num = {0};
        getNumFromSectorPosS(append_group_num, next_track_num, next_sector_num);
        p.nextTrack = (byte) next_track_num[0];
        p.nextSector = (byte) next_sector_num[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Serdes.Util.serialize(p, baos);
        sector.copy(baos.toByteArray(), baos.size(), secBufOfs(div_num[0] + 1)); // TODO is write back?

        return 0;
    }

    /** グループ番号から開始セクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num;
    }

    /** グループ番号から最終セクタ番号を得る */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return group_num;
    }

    /** セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る */
    @Override
    public void getNumFromSectorPos(int sector_pos, int[] track_num, int[] side_num, int[] sector_num, int[] div_num, int[] div_nums) {
        int selected_side = basic.getSelectedSide();
        int numbering_sector = basic.getNumberingSector();
        int sectors_per_track = basic.diskBasicParam.getSectorsPerTrackOnBasic();
        int sides_per_disk = basic.diskBasicParam.getSidesPerDiskOnBasic();
        int groups_per_sector = basic.diskBasicParam.getGroupsPerSector();
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();

        int trksid_num = sector_pos / groups_per_track;
        if (selected_side >= 0) {
            // 1S
            track_num[0] = trksid_num;
            side_num[0] = selected_side;
        } else {
            // 2D, 2HD
            track_num[0] = trksid_num / sides_per_disk;
            side_num[0] = trksid_num % sides_per_disk;
        }
        sector_num[0] = ((sector_pos % groups_per_track) / groups_per_sector);
        if (div_num != null) div_num[0] = (sector_pos % groups_per_track) % groups_per_sector;

        if (numbering_sector == 1) {
            // トラックごとに連番の場合
            sector_num[0] += (side_num[0] * sectors_per_track);
        }

        // サイド番号を逆転するか
        side_num[0] = basic.diskBasicParam.getReversedSideNumber(side_num[0]);

        track_num[0] += basic.getTrackNumberBaseOnDisk();
        side_num[0] += basic.getSideNumberBaseOnDisk();
        sector_num[0] += basic.getSectorNumberBase();

        if (div_nums != null) div_nums[0] = groups_per_sector;
    }

    /** セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、セクタの各番号を得る */
    @Override
    public void getNumFromSectorPosS(int sector_pos, int[] track_num, int[] sector_num) {
        int selected_side = basic.getSelectedSide();
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();
        int sides_per_disk = basic.diskBasicParam.getSidesPerDiskOnBasic();

        if (selected_side >= 0) {
            // 1S
            track_num[0] = sector_pos / groups_per_track;
            sector_num[0] = (sector_pos % groups_per_track) + 1;
        } else {
            // 2D, 2HD
            track_num[0] = sector_pos / (groups_per_track * sides_per_disk);
            sector_num[0] = (sector_pos % (groups_per_track * sides_per_disk)) + 1;
        }
    }

    /** トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る */
    @Override
    public int getSectorPosFromNum(int track_num, int side_num, int sector_num, int div_num, int div_nums) {
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();
        int selected_side = basic.getSelectedSide();
        int numbering_sector = basic.getNumberingSector();
        //int sectors_per_track = basic.getSectorsPerTrackOnBasic();
        int sides_per_disk = basic.diskBasicParam.getSidesPerDiskOnBasic();
        int sector_pos;

        track_num -= basic.getTrackNumberBaseOnDisk();
        side_num -= basic.getSideNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBaseOnDisk();

        // サイド番号を逆転するか
        side_num = basic.diskBasicParam.getReversedSideNumber(side_num);

        if (selected_side >= 0) {
            // 1S
            sector_pos = track_num * groups_per_track;
            sector_pos += (sector_num * div_nums + div_num);
        } else {
            // 2D, 2HD
            sector_pos = track_num * sides_per_disk * groups_per_track;
            if (numbering_sector == 1) {
                sector_pos += (sector_num * div_nums + div_num);
            } else {
                sector_pos += (side_num % sides_per_disk) * groups_per_track;
                sector_pos += (sector_num * div_nums + div_num);
            }
        }

        return sector_pos;
    }

    /** トラック、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る */
    @Override
    public int getSectorPosFromNumS(int track_num, int sector_num) {
        int selected_side = basic.getSelectedSide();
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();
        int sides_per_disk = basic.diskBasicParam.getSidesPerDiskOnBasic();
        int sector_pos;

        if (selected_side >= 0) {
            // 1S
            sector_pos = track_num * groups_per_track + sector_num - 1;
        } else {
            // 2D, 2HD
            sector_pos = track_num * groups_per_track * sides_per_disk + sector_num - 1;
        }
        return sector_pos;
    }

    /** ルートディレクトリか */
    @Override
    public boolean isRootDirectory(int group_num) {
        return false;
    }

    /** サブディレクトリを作成できるか */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /** フォーマット時セクタデータを指定コードで埋める */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) throws IOException {
        for (int div_num = 0; div_num < basic.diskBasicParam.getGroupsPerSector(); div_num++) {
            sector.fill(basic.diskBasicParam.getFillCodeOnFormat(), logSecSiz(basic.getSectorSize()), secBufOfs(div_num + 1));

            if (track.getTrackNumber() > 0 && track.getSideNumber() < basic.diskBasicParam.getSidesPerDiskOnBasic()) {
                // セクタの先頭にリンクを作成
                byte[] b = sector.getSectorBuffer(secBufOfs(div_num + 1));
                if (b != null) {
                    FlexPtr p = new FlexPtr();
                    Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

                    int next_track = track.getTrackNumber();
                    int next_sector = (sector.getSectorNumber() - 1) * basic.diskBasicParam.getGroupsPerSector() + 2 + div_num;
                    if (next_sector >= (basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getGroupsPerSector() + 1)) {
                        next_track++;
                        next_sector = 1;
                    }
                    if (next_track < basic.getTracksPerSide()) {
                        p.nextTrack = (byte) next_track;
                        p.nextSector = (byte) next_sector;
                    } else {
                        p.nextTrack = 0;
                        p.nextSector = 0;
                    }
                    p.seqNum = 0;

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Serdes.Util.serialize(p, baos);
                    sector.copy(baos.toByteArray(), baos.size(), secBufOfs(div_num + 1)); // TODO check is write back
                }
            }
        }
    }

    /** フォーマット時セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // SIR area
        DiskImageSector sector = basic.getSectorFromSectorPos(2);
        if (sector == null) return false;
        byte[] b = sector.getSectorBuffer(secBufOfs(2 + 1));
        if (b == null) return false;
        FlexSirT flex = new FlexSirT();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), flex);

        flex_sir = flex;

        sector.fill((byte) 0, logSecSiz(basic.getSectorSize()), secBufOfs(2 + 1));

        // SIRエリアを設定

        flex_sir.free_start_track = 1;
        flex_sir.free_start_sector = 1;
        flex_sir.free_last_track = (byte) (basic.getTracksPerSide() - 1);
        flex_sir.free_last_sector = (byte) (basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getGroupsPerSector());

        int sector_nums = (basic.getTracksPerSide() - 1) * basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getGroupsPerSector();

        flex_sir.free_sector_nums = (short) sector_nums;

        flex_sir.max_track = (byte) (basic.getTracksPerSide() - 1);
        flex_sir.max_sector = (byte) (basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getGroupsPerSector());

        LocalDateTime tm = LocalDateTime.now();
        flex_sir.cmonth = (byte) (tm.getMonth().ordinal() + 1);
        flex_sir.cday = (byte) tm.getDayOfMonth();
        flex_sir.cyear = (byte) (tm.getYear() % 100);

        // volume name and number
        setIdentifiedData(data);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Serdes.Util.serialize(flex_sir, baos);
        sector.copy(baos.toByteArray(), secBufOfs(2 + 1), baos.size()); // TODO check is write back

        // DIRエリア

        int dir_sta_lsec = basic.diskBasicParam.getDirStartSector() - 1; // logical
        int dir_end_lsec = basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getGroupsPerSector() - 1; // logical
        DiskImageSector prev_sector = null;
        int prev_lsec_pos = 0;
        int next_track = 0;
        int next_sector = 0;
        for (int lsec_pos = dir_sta_lsec; lsec_pos <= dir_end_lsec; lsec_pos++) {
            DiskImageSector current_sector = basic.getSectorFromSectorPos(lsec_pos);
            if (prev_sector != null) {
                b = prev_sector.getSectorBuffer(secBufOfs(prev_lsec_pos + 1));
                if (b != null) {
                    FlexPtr p = new FlexPtr();
                    Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

                    next_sector = lsec_pos + 1;
                    p.nextTrack = (byte) next_track;
                    p.nextSector = (byte) next_sector;
                    p.seqNum = 0;

                    baos = new ByteArrayOutputStream();
                    Serdes.Util.deserialize(p, baos);
                    prev_sector.copy(baos.toByteArray(), baos.size(), secBufOfs(prev_lsec_pos + 1)); // TODO check is write back
                }
            }
            if (current_sector == null) {
                // トラック0のセクタ数はほかのトラックより少ない場合がある
                continue;
            }
            prev_sector = current_sector;
            prev_lsec_pos = lsec_pos;
            current_sector.fill((byte) 0, logSecSiz(basic.getSectorSize()), secBufOfs(lsec_pos + 1));
        }
        if (prev_sector != null) {
            b = prev_sector.getSectorBuffer(secBufOfs(prev_lsec_pos + 1));
            if (b != null) {
                FlexPtr p = new FlexPtr();
                Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

                p.nextTrack = 0;
                p.nextSector = 0;
                p.seqNum = 0;

                baos = new ByteArrayOutputStream();
                Serdes.Util.deserialize(p, baos);
                prev_sector.copy(baos.toByteArray(), baos.size(), secBufOfs(prev_lsec_pos + 1));
            }
        }

        return true;
    }

    /** データの読み込み/比較処理 */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryFlex> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws IOException {
        byte[] buf = Arrays.copyOfRange(sector_buffer, 4, sector_buffer.length);
        int size = (sector_size - 4) < remain_size ? (sector_size - 4) : remain_size;

        if (ostream != null) {
            // 書き出し
            temp.setData(buf, size, basic.isDataInverted());
            ostream.write(temp.getData(), 0, temp.getSize());
        }
        if (istream != null) {
            // 読み込んで比較
            temp.setSize(size);
            istream.read(temp.getData(), 0, temp.getSize());
            temp.invertData(basic.isDataInverted());

            if (!Arrays.equals(temp.getData(), 0, size, buf, 0, size)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /** データの書き込み処理 */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryFlex> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        boolean need_eof_code = item.needCheckEofCode();

        // セクタの4バイト目から
        buffer = Arrays.copyOfRange(buffer, 4, buffer.length);
        size -= 4;

        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (need_eof_code) {
                // 最終は終端コード
                if (remain > 1) istream.read(buffer, 0, remain - 1);
                if (remain > 0) buffer[remain - 1] = basic.diskBasicParam.getTextTerminateCode();
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
        }

        // 反転
        basic.invertMem(buffer, size);

        return len;
    }

    /** 指定したグループ番号のFAT領域を削除する (FLEXでは何もしない) */
    @Override
    public void deleteGroupNumber(int group_num) {
    }

    /** ファイル削除後の処理 */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryFlex> item) throws IOException {
        DirectoryFlex d = item.getData();

        DiskImageSector sector;
        FlexPtr p;

        int start_track_num = d.startTrack & 0xff;
        int start_sector_num = d.startSector & 0xff;
        int last_track_num = d.lastTrack & 0xff;
        int last_sector_num = d.lastSector & 0xff;
        int[] div_num = {0};

        // 削除した領域を空き領域の最後につなげる

        if ((flex_sir.free_start_track == 0 && flex_sir.free_start_sector == 0)
                || (flex_sir.free_last_track == 0 && flex_sir.free_last_sector == 0)) {
            // 空きがない
            flex_sir.free_start_track = (byte) start_track_num;
            flex_sir.free_start_sector = (byte) start_sector_num;
            flex_sir.free_last_track = (byte) last_track_num;
            flex_sir.free_last_sector = (byte) last_sector_num;
        } else {
            // チェインする
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(flex_sir.free_last_track & 0xff, flex_sir.free_last_sector & 0xff), div_num);
            if (sector == null) return false;
            byte[] b = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));
            if (b == null) return false;

            p = new FlexPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            p.nextTrack = (byte) start_track_num;
            p.nextSector = (byte) start_sector_num;
            flex_sir.free_last_track = (byte) last_track_num;
            flex_sir.free_last_sector = (byte) last_sector_num;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Serdes.Util.serialize(p, baos);
            sector.copy(baos.toByteArray(), baos.size(), secBufOfs(div_num[0] + 1)); // TODO check is this write back?
        }

        // 空き領域のチェインを作り直す

        remakeChainOnFreeArea();

        return true;
    }

    /** 空きエリアのチェインを作り直す */
    public void remakeChainOnFreeArea() throws IOException {
        DiskImageSector sector;
        FlexPtr p;

        // 空き領域のリスト
        int free_track_num = flex_sir.free_start_track & 0xff;
        int free_sector_num = flex_sir.free_start_sector & 0xff;
        DiskBasicGroups group_items = new DiskBasicGroups();
        while (free_track_num != 0 && free_sector_num != 0) {
            int free_group_num = getSectorPosFromNumS(free_track_num, free_sector_num);
            group_items.add(free_group_num, 0, free_track_num, 0, free_sector_num, 0);
            int[] div_num = {0};
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(free_track_num, free_sector_num), div_num);
            if (sector == null) break;
            byte[] b = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));
            if (b == null) break;

            p = new FlexPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            free_track_num = p.nextTrack & 0xff;
            free_sector_num = p.nextSector & 0xff;
        }

        // 空き領域をソートしてチェインを作り直す
        group_items.sortItems();
        int group_items_count = group_items.size();
        for (int idx = 0; idx < group_items_count; idx++) {
            DiskBasicGroupItem gitem = group_items.get(idx);
            int[] div_num ={0};
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(gitem.track, gitem.sectorStart), div_num);
            if (sector == null) break;
            byte[] b = sector.getSectorBuffer(secBufOfs(div_num[0] + 1));
            if (b == null) break;

            p = new FlexPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            if (idx == 0) {
                // first
                flex_sir.free_start_track = (byte) gitem.track;
                flex_sir.free_start_sector = (byte) gitem.sectorStart;
            }
            if ((idx + 1) != group_items_count) {
                DiskBasicGroupItem next_gitem = group_items.get(idx + 1);
                p.nextTrack = (byte) next_gitem.track;
                p.nextSector = (byte) next_gitem.sectorStart;
                //p.seq_num = idx + 1;
                p.seqNum = 0;
            } else {
                // last
                p.nextTrack = 0;
                p.nextSector = 0;
                p.seqNum = 0;

                flex_sir.free_last_track = (byte) gitem.track;
                flex_sir.free_last_sector = (byte) gitem.sectorStart;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Serdes.Util.serialize(p, baos);
            sector.copy(baos.toByteArray(), baos.size(), secBufOfs(div_num[0] + 1)); // TODO check is write back
        }
        flex_sir.free_sector_nums = (short) group_items_count;
    }

    /** IPLや管理エリアの属性を得る */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume label
        String vol = new String(flex_sir.volume_label);
        data.setVolumeName(vol);
        data.setVolumeNameMaxLength(flex_sir.volume_label.length);
        // volume number
        data.setVolumeNumber(flex_sir.volume_number);
        // volume date
        int y = (flex_sir.cyear & 0xff) % 100;
        LocalDate tm = LocalDate.of(y + (y >= 0 && y < 80 ? 100 : 0),
                (flex_sir.cmonth & 0xff) - 1,
                flex_sir.cday & 0xff);
        data.setVolumeDate(Utils.formatYMDStr(tm));
    }

    /** IPLや管理エリアの属性をセット */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat fmt = basic.getFormatType();

        // volume label
        if (fmt.hasVolumeName()) {
            byte[] vol = data.getVolumeName().getBytes();
            System.arraycopy(vol, vol.length, flex_sir.volume_label, 0, flex_sir.volume_label.length);
        }
        // volume number
        if (fmt.hasVolumeNumber()) {
            flex_sir.volume_number = (short) data.getVolumeNumber();
        }
    }
}
