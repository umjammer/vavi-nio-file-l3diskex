package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDateTime;
import java.util.List;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DirectoryOs9;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DirectoryOs9Fd;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DiskBasicDirItemOS9FD;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.Os9Date;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.Os9Lsn;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_DIRECTORY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_EXEC;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_READ;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_WRITE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_EXEC;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_READ;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_WRITE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_NEW;


/**
 OS-9の処理

 DiskBasicParam
 @li SubDirGroupSize : サブディレクトリの初期グループ(LSN)数
 @li GroupWidth      : ビットマップ1ビットのセクタ数(dd_BIT)
 */
public class DiskBasicTypeOS9 extends DiskBasicType<DirectoryOs9> {

    private static final Logger logger = System.getLogger(DiskBasicTypeOS9.class.getName());

    /// OS-9 Ident LSN = 0(track1, sector1)
    @Serdes
    static class os9_ident_t {

        /** total lsn */
        @Element(sequence = 1)
        public Os9Lsn dd_TOT = new Os9Lsn();
        /** sectors per track */
        @Element(sequence = 2)
        public byte dd_TKS = 0;
        /** allocation map length */
        @Element(sequence = 3)
        public short dd_MAP = 0;
        /** sectors per group */
        @Element(sequence = 4)
        public short dd_BIT = 0;
        /** rootdir lsn */
        @Element(sequence = 5)
        public Os9Lsn dd_DIR = new Os9Lsn();
        /** owner id */
        @Element(sequence = 6)
        public short dd_OWN = 0;
        /** disk attr */
        @Element(sequence = 7)
        public byte dd_ATT = 0;
        /** disk ident */
        @Element(sequence = 8)
        public short dd_DSK = 0;
        /** format, density, number of sides */
        @Element(sequence = 9)
        public byte dd_FMT = 0;
        /** sector per track */
        @Element(sequence = 10)
        public short dd_SPT = 0;
        @Element(sequence = 11)
        public short reserved1 = 0;
        /** bootstrap lsn */
        @Element(sequence = 12)
        public Os9Lsn dd_BT = new Os9Lsn();
        /** bootstrap size (in bytes) */
        @Element(sequence = 13)
        public short dd_BSZ = 0;
        /** creation date */
        @Element(sequence = 14)
        public Os9Date dd_DAT = new Os9Date();
        /** volume label */
        @Element(sequence = 15)
        public byte[] dd_NAM = new byte[32];
        /** option */
        @Element(sequence = 16)
        public byte[] dd_OPT = new byte[32];
        @Element(sequence = 17)
        public byte reserved2 = 0;
        /** media inegrity code */
        @Element(sequence = 18)
        public int dd_SYNC = 0;
        /** bitmap starting sector number */
        @Element(sequence = 19)
        public int dd_MapLSN = 0;
        /** media logical sector size */
        @Element(sequence = 20)
        public short dd_LSNSize = 0;
        /** version id */
        @Element(sequence = 21)
        public short dd_VersID = 0;
    }

    /// OS-9 Allocation Map
    static class OS9AllocMap extends DiskBasicBitMLMap {

        /** ビットマップサイズ(bytes) */
        private int map_bytes;
        /** 開始LSN */
        private int map_start_lsn;
        /** 最終LSN */
        private int end_lsn;
        /** セクタサイズ */
        private int sector_size;
        /** 1ビット当たりのセクタ数 */
        private int secs_per_bit;

        public OS9AllocMap() {
            map_bytes = 0;
            map_start_lsn = 0;
            end_lsn = 0;
            sector_size = 0;
            secs_per_bit = 0;
        }

        /**
         * Allocation Map を割当てる
         *
         * @param basic           Disk Basic パラメータ
         * @param n_map_start_lsn MapのあるLSN
         * @param n_map_bytes     Mapで使用するバイト数
         * @return false: セクタなし
         */
        public boolean allocMap(DiskBasic basic, int n_map_start_lsn, int n_map_bytes) {
            map_bytes = n_map_bytes;
            map_start_lsn = n_map_start_lsn;
            end_lsn = basic.getFatEndGroup();
            sector_size = basic.getSectorSize();
            secs_per_bit = basic.diskBasicParam.getGroupWidth();

            if (secs_per_bit <= 0) secs_per_bit = 1;

            boolean valid = true;
            int bytes = 0;

            list.clear();

            int map_end_lsn = (end_lsn / sector_size / 8 / secs_per_bit) + 1;
            for (int map_lsn = map_start_lsn; map_lsn <= map_end_lsn && map_lsn <= 32 && bytes < map_bytes; map_lsn++) {
                DiskImageSector sector = basic.getManagedSector(map_lsn);
                if (sector == null) {
                    // error
                    valid = false;
                    break;
                }
                byte[] buf = sector.getSectorBuffer();
                int size = sector.getSectorSize();

                addBuffer(buf, size);

                bytes += size;
            }
            return valid;
        }

        /**
         * Mapを元にして使用状況を作成する
         *
         * @param fat [out] 使用状況
         */
        public void makeAvailable(DiskBasicAvailability fat) {
            int bytes = 0;
            int lsn = 0;

            for (int map_idx = 0; map_idx < size() && bytes < map_bytes; map_idx++) {
                byte[] buf = get(map_idx).getBuffer();
                int size = get(map_idx).getSize();

                for (int pos = 0; pos < size && lsn <= end_lsn && bytes < map_bytes; pos++) {
                    for (int bit = 0; bit < 8 && lsn <= end_lsn && bytes < map_bytes; bit++) {
                        boolean used = ((buf[pos] & (0x80 >> bit)) != 0);
                        for (int i = 0; i < secs_per_bit && lsn <= end_lsn; i++) {
                            if (!used) {
                                fat.add(FAT_AVAIL_FREE, sector_size, 1);
                            } else {
                                fat.add(FAT_AVAIL_USED, 0, 0);
                            }
                            lsn++;
                        }
                    }
                    bytes++;
                }
            }
        }

        /// LSNをMapにセット
        ///
        /// @param lsn LSN
        /// @param val セット / リセット
        public void setLSN(int lsn, boolean val) {
            lsn /= secs_per_bit;

            modify(lsn, val);
        }

        /// LSNを使用しているか
        ///
        /// @param lsn LSN
        /// @return true 使用している
        public boolean isUsedLSN(int lsn) {
            lsn /= secs_per_bit;

            return isSet(lsn);
        }

        /// 空いているLSNを得る
        ///
        /// @return LSN or INVALID_GROUP_NUMBER
        public int findEmpty() {
            int match_lsn = INVALID_GROUP_NUMBER;
            int bytes = 0;
            int lsn = 0;
            for (int map_idx = 0; map_idx < size() && bytes < map_bytes && match_lsn == INVALID_GROUP_NUMBER; map_idx++) {
                byte[] buf = get(map_idx).getBuffer();
                int size = get(map_idx).getSize();

                for (int pos = 0; pos < size && lsn <= end_lsn && bytes < map_bytes && match_lsn == INVALID_GROUP_NUMBER; pos++) {
                    for (int bit = 0; bit < 8 && lsn <= end_lsn && bytes < map_bytes && match_lsn == INVALID_GROUP_NUMBER; bit++) {
                        boolean used = ((buf[pos] & (0x80 >> bit)) != 0);
                        if (!used) {
                            match_lsn = lsn;
                            break;
                        }
                        lsn += secs_per_bit;
                    }
                    bytes++;
                }
            }
            return match_lsn;
        }

        public int getMapStartLSN() {
            return map_start_lsn;
        }

        public int getMapBytes() {
            return map_bytes;
        }
    }

    /** Identification Sector */
    private os9_ident_t os9_ident;

    /** Allocation Map */
    private final OS9AllocMap alloc_map = new OS9AllocMap();

    public DiskBasicTypeOS9(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryOs9> dir) {
        super(basic, fat, dir);

        os9_ident = null;
    }

    /// ディスクから各パラメータを取得＆必要なパラメータを計算
    ///
    /// @param is_formatting フォーマット中か
    /// @return 1.0: 正常, 0.0 ~ 1.0: 警告あり, <0.0: エラーあり
    @Override
    public double parseParamOnDisk(boolean is_formatting) throws IOException {
        if (is_formatting) return 1.0;

        double valid_ratio = 1.0;

        // Ident
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) {
            return -1.0;
        }
        os9_ident = new os9_ident_t();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), os9_ident);
        if (os9_ident == null) {
            return -1.0;
        }

        int ival;

        // total groups
        int lval = os9_ident.dd_TOT.l;
        ival = lval;
        logger.log(Level.TRACE, "OS9: dd_TOT: Total Sectors: %d".formatted(ival));
        if (ival < 1) {
            return -1.0;
        }
        ival--;
        // 最終グループ番号
        basic.diskBasicParam.setFatEndGroup(ival);

        // sectors per track
        ival = os9_ident.dd_SPT;
        logger.log(Level.TRACE, "OS9: dd_SPT: Sectors per Track: %d".formatted(ival));
        if (ival == 0) {
            return -1.0;
        }
        if (ival > basic.getSectorsPerTrack()) {
            logger.log(Level.TRACE, "OS9: %d > %d".formatted(ival, basic.getSectorsPerTrack()));
            valid_ratio = 0.5;
        } else {
            basic.diskBasicParam.setSectorsPerTrackOnBasic(ival);
        }

        // sectors per bit on bitmap table
        ival = os9_ident.dd_BIT;
        logger.log(Level.TRACE, "OS9: dd_BIT: Sectors per Bit on Bitmap: %d".formatted(ival));
        if (ival == 0 || !Utils.IsPowerOfTwo(ival, 16)) {
            return -1.0;
        }
        basic.diskBasicParam.setGroupWidth(ival);

        // disk format
        ival = os9_ident.dd_FMT;
        String sval = "";
        sval = sval + ((ival & 1) != 0 ? "double side" : "single side");
        sval = sval + ((ival & 2) != 0 ? ", double density" : ", single density");
        if ((ival & 4) != 0) sval = sval + (", double track (96/135TPI)");
        if ((ival & 8) != 0) sval = sval + (", quad track density (192TPI)");
        if ((ival & 16) != 0) sval = sval + (", octal track density (384TPI)");
        logger.log(Level.TRACE, "OS9: dd_FMT: 0x%x (%s)".formatted(ival, sval));

        // tracks per side
        ival = (basic.getFatEndGroup() + 1) / basic.getSectorsPerTrackOnBasic() / basic.getSidesPerDiskOnBasic();
        basic.setTracksPerSideOnBasic(ival);

        // root directory
        int dir_fd_lsn = os9_ident.dd_DIR.getOs9Lsn();
        logger.log(Level.TRACE, "OS9: dd_DIR: LSN on Root Directory: %d".formatted(dir_fd_lsn));
        if (dir_fd_lsn > basic.getFatEndGroup()) {
            return -1.0;
        }
        sector = basic.getManagedSector(dir_fd_lsn);
        if (sector == null) {
            return -1.0;
        }
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);

        for (int i = 0; i < 48; i++) {
            int start_lsn = fdd.fdSeg[i].lsn.getOs9Lsn();
            int end_lsn = fdd.fdSeg[i].siz; // block size (in sectors)
            if (start_lsn == 0 && end_lsn == 0) {
                break;
            }
            end_lsn += start_lsn;

            if (i == 0) basic.diskBasicParam.setDirStartSector(start_lsn);
            basic.diskBasicParam.setDirEndSector(end_lsn);
        }

        // Allocation Map
        int map_lsn = os9_ident.dd_MapLSN;
        int map_bytes = os9_ident.dd_MAP;
        logger.log(Level.TRACE, "OS9: dd_MapLSN: %d".formatted(map_lsn));
        if (!alloc_map.allocMap(basic, map_lsn > 0 ? map_lsn : 1, map_bytes)) {
            return -1.0;
        }

        basic.diskBasicParam.setSectorsPerFat(map_bytes / basic.getSectorSize() + 1);

        return valid_ratio;
    }

    /// Allocation Mapの開始位置を得る（ダイアログ用）
    @Override
    public void getStartNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        int map_lsn = alloc_map.getMapStartLSN();
        getNumFromSectorPos(map_lsn, track_num, side_num, sector_num);
    }

    /// Allocation Mapの終了位置を得る（ダイアログ用）
    @Override
    public void getEndNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        int map_lsn = alloc_map.getMapStartLSN();
        map_lsn += (alloc_map.getMapBytes() / basic.getSectorSize());
        getNumFromSectorPos(map_lsn, track_num, side_num, sector_num);
    }

    /** タイトル名（ダイアログ用） */
    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    /**
     * エリアをチェック
     *
     * @param is_formatting フォーマット中か
     * @return 1.0       正常
     * @return 0.0 - 1.0 警告あり
     * @return <0.0      エラーあり
     */
    @Override
    public double checkFat(boolean is_formatting) {
        return 1.0;
    }

    /**
     * ルートディレクトリをアサイン
     *
     * @param start_sector 開始セクタ番号
     * @param end_sector   終了セクタ番号
     * @param group_items  [out] セクタリスト
     * @param dir_item     [in,out] ルートディレクトリアイテム
     */
    @Override
    public boolean assignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryOs9> dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);

        // FDセクタへのポインタをルートアイテムに設定
        DiskBasicDirItemOS9 ditem = (DiskBasicDirItemOS9) dir_item;

        int dir_fd_lsn = os9_ident.dd_DIR.getOs9Lsn();
        ditem.setStartGroup(0, dir_fd_lsn, 0);
        DiskBasicDirItemOS9FD fd = ditem.getFD();
        DiskImageSector sector = basic.getManagedSector(dir_fd_lsn);
        if (sector == null) return false;
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), fdd);
        fd.set(basic, sector, dir_fd_lsn, fdd);

        return sts;
    }

    /**
     * ルートディレクトリのセクタリストを計算
     *
     * @param start_sector ディレクトリ開始セクタ番号
     * @param end_sector   ディレクトリ終了セクタ番号
     * @param group_items  [out] セクタリスト
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) throws IOException {
        boolean valid = true;

        // root directory
        int dir_fd_lsn = os9_ident.dd_DIR.getOs9Lsn();
        DiskImageSector sector = basic.getManagedSector(dir_fd_lsn);
        if (sector == null) {
            return false;
        }
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        byte[] b  = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);
        if (fdd == null) {
            return false;
        }

        group_items.clear();

        int dir_size = 0;
        for (int i = 0; i < 48 && valid; i++) {
            int start_lsn = fdd.fdSeg[i].lsn.getOs9Lsn();
            int end_lsn = fdd.fdSeg[i].siz; // block size (in sectors)
            if (start_lsn == 0 && end_lsn == 0) {
                break;
            }
            end_lsn += start_lsn;

            for (int lsn = start_lsn; lsn < end_lsn; lsn++) {
                int[] trk_num = {0};
                int[] sid_num = {0};
                sector = basic.getSectorFromGroup(lsn, trk_num, sid_num);
                if (sector == null) {
                    valid = false;
                    break;
                }
                group_items.add(lsn, 0, trk_num[0], sid_num[0], sector.getSectorNumber(), sector.getSectorNumber());
                dir_size += sector.getSectorSize();
            }
        }
        group_items.setSize(dir_size);

        return valid;
    }

    /** ディレクトリが空か */
    @Override
    public boolean isEmptyDirectory(boolean is_root, DiskBasicGroups group_items) throws IOException {
        boolean valid = true;
        boolean last = false;

        int index_number = 0;
        DiskBasicDirItem<DirectoryOs9> nitem = dir.newItem();
        for (int idx = 0; idx < group_items.size(); idx++) {
            DiskBasicGroupItem gitem = group_items.get(idx);
            int trk_num = gitem.track;
            int sid_num = gitem.side;
            DiskImageTrack track = basic.getTrack(trk_num, sid_num);
            if (track == null) {
                valid = false;
                break;
            }
            for (int sec_num = gitem.sectorStart; sec_num <= gitem.sectorEnd && valid && !last; sec_num++) {
                DiskImageSector sector = track.getSector(sec_num);
                // nitem->SetSector(sector);
                if (sector == null) {
                    valid = false;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                int bufferOffset = 0;
                if (buffer == null) {
                    valid = false;
                    break;
                }

                int pos = 0;
                int size = sector.getSectorSize();

                // ディレクトリにファイルがないかのチェック
                while (valid && !last && pos < size) {
                    nitem.setDataPtr(index_number, gitem, sector, pos, buffer, bufferOffset, null);
                    // FDセクタを調べる
                    int start_nsl = nitem.getStartGroup(0);
                    DiskImageSector fd_sector = basic.getSectorFromGroup(start_nsl);
                    if (fd_sector != null) {
                        DirectoryOs9Fd fd_buf = new DirectoryOs9Fd();
                        byte[] b = fd_sector.getSectorBuffer();
                        Serdes.Util.deserialize(new ByteArrayInputStream(b), fd_sector);
                        DiskBasicDirItemOS9FD fd = ((DiskBasicDirItemOS9) nitem).getFD();
                        fd.set(basic, fd_sector, start_nsl, fd_buf);
                        if (nitem.isNormalFile()) {
                            valid = !nitem.checkUsed(last);
                        }
                    }
                    pos += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    index_number++;
                }
            }
        }

        return valid;
    }

    /**
     * ディレクトリエリアのサイズに達したらアサイン終了するか
     *
     * @param pos         [in,out] ディレクトリの位置
     * @param size        [in,out] ディレクトリのセクタサイズ
     * @param size_remain [in,out] ディレクトリの残りサイズ
     * @return 0: 終了しない, 1: 強制的に未使用とする アサインは継続
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        // サイズに達したら以降は未使用とする
        return size_remain[0] <= 0 ? 1 : 0;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.getFatEndGroup() + 1;
        disk_size[0] = group_size[0] * basic.getSectorSize();
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        // Allocation Mapを調べる
        alloc_map.makeAvailable(fatAvailability);

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectoryOs9>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (int idx = 0; idx < items.size(); idx++) {
                DiskBasicDirItem<DirectoryOs9> item = items.get(idx);
                if (item == null || !item.isUsed()) continue;

                // グループ番号のマップを調べる
                int gcnt = item.getGroupCount();
                if (gcnt > 0) {
                    DiskBasicGroupItem gitem = item.getGroup(gcnt - 1);
                    int gnum = gitem.group;
                    if (gnum <= basic.getFatEndGroup()) {
                        fatAvailability.set(gnum, DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        //free_disk_size = fsize;
        //free_groups = grps;
    }

    /// 使用状態を設定する
    ///
    /// @param num グループ番号(0...)
    /// @param val 1:使用中, 0:空きにする
    @Override
    public void setGroupNumber(int num, int val) {
        alloc_map.setLSN(num, val != 0);
    }

    /** グループ番号を得る */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /// FAT位置が使用されているか
    ///
    /// @param num グループ番号(0...)
    @Override
    public boolean isUsedGroupNumber(int num) {
        return alloc_map.isUsedLSN(num);
    }

    /** 次のグループ番号を得る */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /// 空き位置を返す
    ///
    /// @return INVALID_GROUP_NUMBER: 空きなし
    @Override
    public int getEmptyGroupNumber() {
        // Allocation Mapを調べる
        return alloc_map.findEmpty();
    }

    /// 次の空き位置を返す 未使用
    ///
    /// @return INVALID_GROUP_NUMBER: 空きなし
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        return INVALID_GROUP_NUMBER;
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
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryOs9> pitem, DiskBasicDirItem<DirectoryOs9> nitem, DiskBasicError errinfo) throws IOException {
        // FDセクタを確保する
        int lsn = getEmptyGroupNumber();
        if (lsn == INVALID_GROUP_NUMBER) {
            return false;
        }
        DiskImageSector sector = basic.getSectorFromGroup(lsn);
        if (sector == null) {
            return false;
        }
        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return false;
        }
        // FDセクタをセット
        nitem.setChainSector(sector, lsn, buf, pitem);

        // 開始LSNを設定
        nitem.setStartGroup(0, lsn);

        // セクタを予約
        setGroupNumber(lsn, 1);

        return true;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileunit_num ファイル番号
     * @param item         [in,out] ディレクトリアイテム
     * @param data_size    確保するデータサイズ（バイト）
     * @param flags        新規か追加か
     * @param group_items  [out] 確保したセクタリスト
     * @return >0:正常 -1:空きなし(開始グループ設定前) -2:空きなし(開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryOs9> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) {
        DiskBasicDirItemOS9 ditem = (DiskBasicDirItemOS9) item;
        DiskBasicDirItemOS9FD fd = ditem.getFD();

        int seg_idx = -1;
        if (flags == ALLOCATE_GROUPS_APPEND) {
            // 追加の場合、既にあるセグメントを計算
            seg_idx = 48;
            for (int idx = 0; idx < 48; idx++) {
                if (fd.getLSN(idx) == 0 && fd.getSIZ(idx) == 0) {
                    seg_idx = idx - 1;
                    break;
                }
            }
            if (seg_idx >= 48) {
                // セグメントに空きなし
                return -1;
            }
        }
        int start_seg_idx = seg_idx + 1;

        int file_size = fd.getSIZ();
        data_size += file_size;

        // 新規作成でdd_BITが2以上のとき
        boolean is_first_lsn = (flags == ALLOCATE_GROUPS_NEW && basic.diskBasicParam.getGroupWidth() > 1);

        // データ用のセクタを確保する
        int rc = 0;
        int lsn = 0;
        int prev_lsn = 0;
        int seg_cnt = 0; // wxUint16
        int limit = basic.getFatEndGroup() + 1;
        while (file_size < data_size && limit >= 0 && rc == 0) {
            int start = 0;
            if (is_first_lsn) {
                // 新規作成でdd_BITが2以上のときはFDセクタの空きからデータを書き込んでいく
                lsn = fd.getMyLSN() + 1;
                start++;
                is_first_lsn = false;
            } else {
                lsn = getEmptyGroupNumber();
            }
            if (lsn == INVALID_GROUP_NUMBER) {
                // 空きなし？
                rc = -2;
                break;
            }
            if (prev_lsn == 0 || (prev_lsn + 1) != lsn) {
                // LSNが連続していない
                seg_idx++;
                if (seg_idx >= 48) {
                    // セグメント限界
                    rc = -2;
                    break;
                }
                fd.SetLSN(seg_idx, lsn);
                seg_cnt = (basic.diskBasicParam.getGroupWidth() - start);
                fd.setSIZ(seg_idx, seg_cnt);
            } else {
                // LSNが連続しているなら、同じセグメントでセクタ数を増やす
                seg_cnt += basic.diskBasicParam.getGroupWidth();
                fd.setSIZ(seg_idx, seg_cnt);
            }
            // セクタを予約
            setGroupNumber(lsn, 1);
            // グループ追加
            for (int i = start; i < basic.diskBasicParam.getGroupWidth(); i++) {
                basic.getNumsFromGroup(lsn, 0, basic.getSectorSize(), 0, group_items[0]);
                lsn++;
            }
            // LSNを保持
            prev_lsn = lsn - 1;

            if (file_size + basic.getSectorSize() * (basic.diskBasicParam.getGroupWidth() - start) > data_size) {
                file_size = data_size;
            } else {
                file_size += basic.getSectorSize() * (basic.diskBasicParam.getGroupWidth() - start);
            }
            // ファイルサイズ
            fd.setSIZ(file_size);

            limit--;
        }
        if (limit < 0) {
            rc = -2;
        }

        // エラーの場合、確保したエリアを開放
        if (rc < 0) {
            for (int idx = start_seg_idx; idx < 48; idx++) {
                int seg_lsn = fd.getLSN(idx);
                int seg_siz = fd.getSIZ(idx);
                if (seg_lsn == 0 && seg_siz == 0) {
                    break;
                }
                for (int siz = 0; siz < seg_siz; siz++) {
                    // セクタを未使用にする
                    if ((seg_lsn / basic.diskBasicParam.getGroupWidth()) != (fd.getMyLSN() / basic.diskBasicParam.getGroupWidth()))
                        setGroupNumber(seg_lsn, 0);
                    seg_lsn++;
                }
                fd.SetLSN(idx, 0);
                fd.setSIZ(idx, 0);
            }
        }

        return rc;
    }

    /** グループ番号からセクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num;
    }

    /** グループ番号から最終セクタ番号を得る */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return group_num;
    }

    /** データ領域の開始セクタを計算 */
    @Override
    public int calcDataStartSectorPos() {
        return basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
    }

    /** ルートディレクトリか */
    @Override
    public boolean isRootDirectory(int group_num) {
        return ((group_num + 1) <= basic.diskBasicParam.getDirStartSector());
    }

    /** サブディレクトリを作成できるか */
    @Override
    public boolean canMakeDirectory() {
        return true;
    }

    /** ルートディレクトリのサイズを拡張できるか */
    @Override
    public boolean canExpandRootDirectory() {
        return true;
    }

    /** サブディレクトリのサイズを拡張できるか */
    @Override
    public boolean canExpandDirectory() {
        return true;
    }

    /// サブディレクトリを作成する前の準備を行う
    ///
    /// @param item 確保したディレクトリアイテム
    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem<DirectoryOs9> item) throws IOException {
        // FDセクタを確保する
        int lsn = getEmptyGroupNumber();
        if (lsn == INVALID_GROUP_NUMBER) {
            return false;
        }
        DiskBasicDirItemOS9 ditem = (DiskBasicDirItemOS9) item;
        DiskImageSector sector = basic.getSectorFromGroup(lsn);

        DiskBasicDirItemOS9FD fd = ditem.getFD();
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);
        fd.set(basic, sector, lsn, fdd);
        fd.clear();

        // 開始LSNを設定
        ditem.setStartGroup(0, lsn, 0);
        // 日付を設定
        LocalDateTime tm = LocalDateTime.now();
        ditem.setFileCreateDateTime(tm);
        ditem.setFileModifyDateTime(tm);
        // セクタを予約
        setGroupNumber(lsn, 1);

        return true;
    }

    /** サブディレクトリを作成した後の個別処理 */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryOs9> item, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryOs9> parent_item) throws IOException {
        if (group_items.size() <= 0) return;

        // ディレクトリ属性
        item.setFileAttr(basic.getFormatTypeNumber(), 0,
                FILETYPE_MASK_OS9_DIRECTORY |
                        FILETYPE_MASK_OS9_PUBLIC_EXEC |
                        FILETYPE_MASK_OS9_PUBLIC_WRITE |
                        FILETYPE_MASK_OS9_PUBLIC_READ |
                        FILETYPE_MASK_OS9_USER_EXEC |
                        FILETYPE_MASK_OS9_USER_WRITE |
                        FILETYPE_MASK_OS9_USER_READ
        );

        // ファイルサイズはエントリ２つ分
        item.setFileSize(item.getDataSize() * 2);

        // カレントと親ディレクトリのエントリを作成する
        DiskBasicGroupItem gitem = group_items.get(0);

        DiskImageSector sector = basic.getTrack(gitem.track, gitem.side).getSector(gitem.sectorStart); // Simplified access

        byte[] buf = sector.getSectorBuffer();
        int bufOffset = 0;
        DiskBasicDirItem<DirectoryOs9> newitem = basic.createDirItem(sector, 0, buf, bufOffset);

        // 親をつくる
        newitem.clearData();
        if (parent_item != null) {
            // 親がサブディレクトリ
            newitem.setStartGroup(0, parent_item.getStartGroup(0));
        } else {
            // 親がルート
            newitem.setStartGroup(0, os9_ident.dd_DIR.getOs9Lsn());
        }
        newitem.setFileNamePlain("..");
//        newitem.setFileAttr(FILE_TYPE_DIRECTORY_MASK);

        // カレント
        bufOffset += newitem.getDataSize();
        newitem.setDataPtr(0, null, sector, 0, buf, bufOffset, null);

        newitem.clearData();
        newitem.setStartGroup(0, item.getStartGroup(0));
        newitem.setFileNamePlain(".");
//        newitem.setFileAttr(FILE_TYPE_DIRECTORY_MASK);

        // ディレクトリサイズを更新
        int dir_size = dir.calcSize();
        DiskBasicDirItem<DirectoryOs9> dir_item = item.getParent();
        if (dir_item != null) {
            dir_item.setFileSize(dir_size);
        }
    }

    /** セクタデータを指定コードで埋める */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    /** セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // Ident
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) return false;
        os9_ident = new DiskBasicTypeOS9.os9_ident_t();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), os9_ident);
        if (os9_ident == null) return false;

        //int total_lsn = basic.getFatEndGroup() + 1;
        int total_lsn = (basic.getTracksPerSide() - basic.getManagedTrackNumber()) * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
        // 最終グループ番号
        basic.diskBasicParam.setFatEndGroup(total_lsn - 1);

        int map_lsn = 1;
        int root_start_lsn = basic.diskBasicParam.getDirStartSector() - 1;
        int root_end_lsn = basic.diskBasicParam.getDirEndSector() - 1;
        int ival;

        //
        // OS9 Identifier をセット
        //

        sector.fill((byte) 0);

        // total lsn
        os9_ident.dd_TOT.setOs9Lsn(total_lsn);
        // sectors per track
        os9_ident.dd_TKS = (byte) basic.getSectorsPerTrackOnBasic();
        // allocation map length
        ival = (total_lsn + 7) / 8;
        os9_ident.dd_MAP = (short) ival;
        // sectors per group
        ival = basic.diskBasicParam.getGroupWidth();
        os9_ident.dd_BIT = (short) ival;
        // rootdir lsn
        os9_ident.dd_DIR.setOs9Lsn(root_start_lsn);
        // owner id
        os9_ident.dd_OWN = 0;
        // disk attr
        os9_ident.dd_ATT = 0;
        // disk ident
        os9_ident.dd_DSK = (short) 0;

        // format, density, number of sides
        ival = ((basic.getSidesPerDiskOnBasic() - 1) & 0x01);
        ival |= (basic.hasSingleDensity(null ,null) == 1 ? 0 : 0x02);
        os9_ident.dd_FMT = (byte) ival;

        // sector per track
        ival = basic.getSectorsPerTrackOnBasic();
        os9_ident.dd_SPT = (short) ival;

        // bootstrap lsn
        os9_ident.dd_BT.setOs9Lsn(0);
        // bootstrap size (in bytes)
        os9_ident.dd_BSZ = (short) 0;

        // creation date
        LocalDateTime tm = LocalDateTime.now();
        os9_ident.dd_DAT.yy = (byte) (tm.getYear() % 100);
        os9_ident.dd_DAT.mm = (byte) (tm.getMonth().ordinal() + 1);
        os9_ident.dd_DAT.dd = (byte) tm.getDayOfMonth();
        os9_ident.dd_DAT.hh = (byte) tm.getHour();
        os9_ident.dd_DAT.mi = (byte) tm.getMinute();

        // bitmap starting sector number
        os9_ident.dd_MapLSN = map_lsn - 1;

        // volume label
        setIdentifiedData(data);

        // 固有パラメータ設定
        basic.assignParameter();

        //
        // Allocation Mapを作成
        //

        for (int lsn = map_lsn; lsn < root_start_lsn; lsn++) {
            sector = basic.getManagedSector(lsn);
            if (sector == null) return false;
            sector.fill((byte) 0xff);
        }
        for (int lsn = root_end_lsn + 1; lsn < total_lsn; lsn++) {
            setGroupNumber(lsn, 0);
        }

        //
        // ルートディレクトリを作成
        //

        sector = basic.getManagedSector(root_start_lsn);
        if (sector == null) return false;

        DiskBasicDirItemOS9 root_item = (DiskBasicDirItemOS9) dir.newItem();
        DiskBasicDirItemOS9FD root_fd = root_item.getFD();

        DirectoryOs9Fd fdd = new DirectoryOs9Fd(); // Mock
        root_fd.set(basic, sector, root_start_lsn, fdd);
        root_fd.clear();

        root_item.setStartGroup(0, root_start_lsn, 0);
        // 日付を設定
        root_item.setFileCreateDateTime(tm);
        root_item.setFileModifyDateTime(tm);
        // セグメント設定
        root_fd.SetLSN(0, root_start_lsn + 1);
        root_fd.setSIZ(0, root_end_lsn - root_start_lsn);
        // リンクの数
        root_fd.setLNK((short) 2);
        // セクタを予約
        setGroupNumber(root_start_lsn, 1);

        for (int lsn = root_start_lsn + 1; lsn <= root_end_lsn; lsn++) {
            sector = basic.getManagedSector(lsn);
            if (sector == null) {
                continue;
            }
            sector.fill((byte) 0);
        }
        DiskBasicGroups group_items = new DiskBasicGroups();
        basic.getNumsFromGroup(root_start_lsn + 1, 0, basic.getSectorSize(), basic.getSectorSize(), group_items);
        additionalProcessOnMadeDirectory(root_item, group_items, null);

        return true;
    }

    /** データの書き込み終了後の処理 */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryOs9> item) {
        // ディレクトリサイズを更新
        int dir_size = dir.calcSize();
        //DiskBasicDirItem dir_item = dir.findName(".", null, null);
        DiskBasicDirItem<DirectoryOs9> dir_item = item.getParent();
        if (dir_item == null) {
            // Why?
            return;
        }
        dir_item.setFileSize(dir_size);
    }

    /// FAT領域を削除する
    @Override
    public void deleteGroupNumber(int group_num) {
        // 未使用にする
        setGroupNumber(group_num, 0);
    }

    /** ファイル削除後の処理 */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryOs9> item) {
        // FDセクタを未使用にする
        setGroupNumber(item.getStartGroup(0), 0);

        // ディレクトリサイズを更新
        int dir_size = dir.calcSize();
        //DiskBasicDirItem dir_item = dir.findName(".", null, null);
        DiskBasicDirItem<DirectoryOs9> dir_item = item.getParent();
        if (dir_item == null) {
            // Why?
            return true;
        }
        dir_item.setFileSize(dir_size);

        return true;
    }

    /// IPLや管理エリアの属性を得る
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume label
        byte[] buf = new byte[os9_ident.dd_NAM.length + 1];
        DiskBasicDirItemOS9.decodeString(buf, os9_ident.dd_NAM.length, os9_ident.dd_NAM, os9_ident.dd_NAM.length);
        String vol = new String(buf, 0, os9_ident.dd_NAM.length);
        data.setVolumeName(vol);
        data.setVolumeNameMaxLength(os9_ident.dd_NAM.length);
    }

    /// IPLや管理エリアの属性をセット
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat fmt = basic.getFormatType();

        // volume label
        if (fmt.hasVolumeName()) {
            String vol = data.getVolumeName();
            DiskBasicDirItemOS9.encodeString(os9_ident.dd_NAM, os9_ident.dd_NAM.length, vol, vol.length());
        }
    }
}