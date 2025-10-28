package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.C1541Ptr;
import l3diskex.basicfmt.BasicCommon.DirectoryC1541;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Serdes;
import vavi.util.serdes.Serdes.Util;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;


/**
 Commodore 1541 の処理

 DiskBasicParam
 @li SectorSkewForSave ファイルインポート時の空きセクタの埋め方
 */
public class DiskBasicTypeC1541 extends DiskBasicType<DirectoryC1541> {

    /// C1541属性値
    public static final int FILETYPE_MASK_C1541_DEL = 0x80;
    public static final int FILETYPE_MASK_C1541_SEQ = 0x81;
    public static final int FILETYPE_MASK_C1541_PRG = 0x82;
    public static final int FILETYPE_MASK_C1541_USR = 0x83;
    public static final int FILETYPE_MASK_C1541_REL = 0x84;

    /// C1541属性位置
    public static final int TYPE_NAME_C1541_DEL = 0;
    public static final int TYPE_NAME_C1541_SEQ = 1;
    public static final int TYPE_NAME_C1541_PRG = 2;
    public static final int TYPE_NAME_C1541_USR = 3;
    public static final int TYPE_NAME_C1541_REL = 3;

    public static final int C1541_START_TRACK_OFFSET = 0;
    public static final int C1541_START_SECTOR_OFFSET = 0;

    /**
     * C1541 BITMAP
     */
    static class c1541_map_t {

        // free blocks
        public byte remain;
        // Little Endien : Byte0 LSB -> MSB -> byte 1 LSB -> MSB
        public byte[] bits = new byte[3];
    }

    /** C1541 BAM */
    static class c1541_bam_t {

        public C1541Ptr start_dir = new C1541Ptr();
        public byte format_type;
        public byte unused;
        public c1541_map_t[] map = new c1541_map_t[35];
        public byte[] disk_name = new byte[18];
        public short disk_id;
        public byte space0;
        public byte dos_version;
        public byte dos_format;
        public byte space1;
        public byte[] reserved = new byte[85];

        public c1541_bam_t() {
            for (int i = 0; i < map.length; i++) {
                map[i] = new c1541_map_t();
            }
        }
    }

    /**
     * C1541 side sector
     */
    static class c1541_side_sector_t {

        public C1541Ptr next = new C1541Ptr();
        public byte side_num;
        public byte record_length;
        public C1541Ptr[] side_pos = new C1541Ptr[6];
        public C1541Ptr[] data_pos = new C1541Ptr[120];

        public c1541_side_sector_t() {
            for (int i = 0; i < side_pos.length; i++) {
                side_pos[i] = new C1541Ptr();
            }
            for (int i = 0; i < data_pos.length; i++) {
                data_pos[i] = new C1541Ptr();
            }
        }
    }

    /**
     * C1541 BAM ビットマップ
     */
    static class C1541Bitmap {

        private int m_my_group_num;
        /** Block Availablity Map */
        private c1541_bam_t m_bam;

        public C1541Bitmap() {
            m_my_group_num = 0;
            m_bam = null;
        }

        public void setBitmap(c1541_bam_t bam) {
            m_bam = bam;
        }

        public void setMyGroupNumber(int val) {
            m_my_group_num = val;
        }

        public int getMyGroupNumber() {
            return m_my_group_num;
        }

        /**
         * 指定位置のビットを変更する
          @param track_num  トラック番号(0 ..)
          @param sector_num セクタ番号(0 ..)
          @param use セットする場合true
         */
        public void modify(int track_num, int sector_num, boolean use) {
            int pos = sector_num >> 3;
            int bit = sector_num & 7;

            int mask = 1 << bit;
            int currentByte = m_bam.map[track_num].bits[pos] & 0xFF;

            if (use) {
                m_bam.map[track_num].bits[pos] = (byte) (currentByte & ~mask);
                m_bam.map[track_num].remain--;
            } else {
                m_bam.map[track_num].bits[pos] = (byte) (currentByte | mask);
                m_bam.map[track_num].remain++;
            }
        }

        /**
         * 指定位置が空いているか
         *
          @param track_num  トラック番号(0 ..)
          @param sector_num セクタ番号(0 ..)
         * @return 空いている場合 true
         */
        public boolean isFree(int track_num, int sector_num) {
            int pos = sector_num >> 3;
            int bit = sector_num & 7;
            return ((m_bam.map[track_num].bits[pos] & (1 << bit)) != 0);
        }

        /**
         * 指定トラックをすべて未使用にする
          @param track_num  トラック番号(0 ..)
          @param num_of_sector セクタ数
         */
        public void freeTrack(int track_num, int num_of_sector) {
            int val = (1 << num_of_sector) - 1;
            for (int pos = 0; pos < 3; pos++) {
                m_bam.map[track_num].bits[pos] = (byte) (val & 0xff);
                val >>= 8;
            }
            m_bam.map[track_num].remain = (byte) num_of_sector;
        }

        /**
         * ディスク名を返す
         */
        public int getDiskName(byte[] buf, int len) {
            int copyLen = Math.min(len, m_bam.disk_name.length);
            System.arraycopy(m_bam.disk_name, 0, buf, 0, copyLen);
            return m_bam.disk_name.length;
        }

        /**
         * ディスク名を設定
         */
        public void setDiskName(byte[] buf, int len) {
            int copyLen = Math.min(len, m_bam.disk_name.length);
            System.arraycopy(buf, 0, m_bam.disk_name, 0, copyLen);
        }

        /**
         * ディスク名サイズを返す
         */
        public int getDiskNameSize() {
            return m_bam.disk_name.length;
        }

        /**
         * ディスクIDを返す
         */
        public int getDiskID() {
            return m_bam.disk_id & 0xffff;
        }

        /**
         * ディスクIDを設定
         */
        public void setDiskID(int val) {
            m_bam.disk_id = (short) val;
        }
    }

    /**
     * C1541 セクタ位置変換マップリスト
     */
    static class C1541SectorPosTrans extends DiskBasicSectorPosTrans {

        @Override
        public void createSectorSkewMap(DiskBasic basic) {
            // インポート時の空きセクタの探し方をセット
            for (int i = 0; i < size(); i++) {
                SectorsPerTrack item = get(i);
                DiskBasicSectorSkewForSave map = new DiskBasicSectorSkewForSave();
                map.create(basic, item.getNumOfSectors());
                item.setSectorSkewMap(map);
            }
        }
    }

    /** Block Availablity Map */
    private final C1541Bitmap c1541_bam = new C1541Bitmap();
    /** 可変数セクタマップ */
    private final C1541SectorPosTrans sector_map = new C1541SectorPosTrans();

    /** */
    public DiskBasicTypeC1541(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryC1541> dir) {
        super(basic, fat, dir);
    }

    /**
     * エリアをチェック
     */
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        return valid_ratio;
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     */
    @Override
    public double parseParamOnDisk(boolean is_formatting) throws IOException {
        if (is_formatting) return 0.0;

        double valid_ratio = 1.0;

        // 可変数セクタなのでトラックごとのセクタ数を集計
        sector_map.create(basic);

        // セクタ数の合計
        basic.diskBasicParam.setFatEndGroup(sector_map.getTotalSectors() - 1);

        // インポート時の空きセクタの探し方を設定
        sector_map.createSectorSkewMap(basic);

        // BAM
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            return -1.0;
        }
        c1541_bam_t bam = new c1541_bam_t();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), bam);

        // チェック
        if (bam.space0 != (byte) 0xa0 || bam.space1 != (byte) 0xa0) {
            valid_ratio = 0.0;
        } else if (bam.dos_version != '2' || bam.dos_format != 'A') {
            valid_ratio = 0.5;
        }

        c1541_bam.setBitmap(bam);

        int track_num = basic.getManagedTrackNumber();
        int sector_num = basic.getSectorNumberBase();
        int bam_sector_pos = getSectorPosFromNumS(track_num, sector_num);
        c1541_bam.setMyGroupNumber(bam_sector_pos);

        // Directory Area
        if ((bam.start_dir.track & 0xff) >= (basic.getTracksPerSideOnBasic() + basic.getTrackNumberBaseOnDisk()) ||
                (bam.start_dir.sector & 0xff) > basic.getSectorsPerTrack()) {
            return -1.0;
        }
        track_num = (bam.start_dir.track & 0xff) - C1541_START_TRACK_OFFSET;
        sector_num = (bam.start_dir.sector & 0xff) - C1541_START_SECTOR_OFFSET;

        int dir_sector_num = getSectorPosFromNumS(track_num, sector_num);

        // ディレクトリ開始はBAMセクタからの相対位置とする
        dir_sector_num = dir_sector_num - bam_sector_pos + basic.getSectorNumberBase();
        basic.diskBasicParam.setDirStartSector(dir_sector_num);

        basic.diskBasicParam.setSectorsPerFat(1);

        return valid_ratio;
    }

    /**
     * ルートディレクトリのセクタリストを計算
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) throws IOException {
        boolean valid = true;

        group_items.clear();

        // ディレクトリのチェインをたどる
        int dir_size = 0;
        int limit = basic.getSectorsPerTrackOnBasic();
        int mng_trk_num = basic.getManagedTrackNumber();
        int[] trk_num = {0};
        int[] sid_num = {0};
        int sec_num = 0;

        // 開始セクタ
        int sector_pos = getSectorPosFromNumS(mng_trk_num, basic.diskBasicParam.getDirStartSector());

        while (valid && limit >= 0) {
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
            int group_num = sector_pos;
            group_items.add(group_num, 0, trk_num[0], sid_num[0], sec_num, sec_num);

            dir_size += sector.getSectorSize();

            // 次のセクタ
            C1541Ptr next = new C1541Ptr();
            Serdes.Util.deserialize(new ByteArrayInputStream(buffer), next);
            if ((next.track & 0xff) == 0 || (next.sector & 0xff) > basic.getTracksPerSideOnBasic()) {
                break;
            }

            sector_pos = getSectorPosFromNumS((next.track & 0xff) - C1541_START_TRACK_OFFSET, (next.sector & 0xff) - C1541_START_SECTOR_OFFSET);

            limit--;
        }
        group_items.setSize(dir_size);

        if (limit < 0) {
            valid = false;
        }

        int sta_sector_num = getSectorPosFromNumS(mng_trk_num, 1);
        int end_sector_num = getSectorPosFromNumS(trk_num[0], sec_num);
        basic.diskBasicParam.setDirEndSector(end_sector_num - sta_sector_num + 1);

        return valid;
    }

    /**
     * セクタをディレクトリとして初期化
     */
    @Override
    public int initializeSectorsAsDirectory(DiskBasicGroups group_items, int[] file_size, int[] size_remain, DiskBasicError errinfo) {
        for (int i = 0; i < group_items.getSize(); i++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(group_items.get(i).group);
            sector.fill((byte) 0, sector.getSectorSize() - 2, 2);
        }

        file_size[0] += group_items.size() * (basic.getSectorSize() - 2);

        size_remain[0] = 0;
        return 0;
    }

    /**
     * 使用可能なディスクサイズを得る
     */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.getFatEndGroup() + 1;
        disk_size[0] = group_size[0] * basic.getSectorSize();
    }

    /**
     * 残りディスクサイズを計算
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        // BITMAP table
        for (int sec_pos = 0; sec_pos <= basic.getFatEndGroup(); sec_pos++) {
            int[] trk_num = {0};
            int[] sec_num = {0};
            getNumFromSectorPosS(sec_pos, trk_num, sec_num);
            int trk_anum = trk_num[0] - basic.getTrackNumberBaseOnDisk();
            int sec_anum = sec_num[0] - basic.getSectorNumberBase();
            if (c1541_bam.isFree(trk_anum, sec_anum)) {
                fatAvailability.add(FAT_AVAIL_FREE, basic.getSectorSize(), 1);
            } else {
                fatAvailability.add(FAT_AVAIL_USED, 0, 0);
            }
        }
        fatAvailability.set(c1541_bam.getMyGroupNumber(), FAT_AVAIL_SYSTEM);
        DiskBasicDirItem<DirectoryC1541> root = dir.getRootItem();
        if (root != null) {
            DiskBasicGroups root_groups = root.getGroups();
            for (int i = 0; i < root_groups.size(); i++) {
                fatAvailability.set(root_groups.get(i).group, FAT_AVAIL_SYSTEM);
            }
        }
    }

    /**
     * グループ番号を使用済みにする
     */
    @Override
    public void setGroupNumber(int num, int val) {
        int[] trk_num = {0};
        int[] sec_num = {0};
        getNumFromSectorPosS(num, trk_num, sec_num);
        int trk_anum = trk_num[0] - basic.getTrackNumberBaseOnDisk();
        int sec_anum = sec_num[0] - basic.getSectorNumberBase();
        c1541_bam.modify(trk_anum, sec_anum, val != 0);
    }

    /** グループ番号を得る */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /** FAT位置が使用されているか */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /** 次のグループ番号を得る */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /** 空き位置を返す */
    @Override
    public int getEmptyGroupNumber() {
        return getEmptyGroupNumberM(0);
    }

    /** 空き位置を返す */
    private int getEmptyGroupNumberM(int method) {
        int[][] findtrkmap = {
                {0, 1, 2},
                {2, 0, 1}
        };

        int new_num = INVALID_GROUP_NUMBER;

        int sta_trk = 0;
        int end_trk = 0;
        int ndir = 1;

        for (int n = 0; n < 2; n++) {
            int i = findtrkmap[method][n];
            switch (i) {
                case 0:
                    // 内側から検索
                    sta_trk = basic.getManagedTrackNumber() - 1;
                    end_trk = basic.getTrackNumberBaseOnDisk() - 1;
                    ndir = -1;
                    break;
                case 1:
                    // 外側へ検索
                    sta_trk = basic.getManagedTrackNumber() + 1;
                    end_trk = basic.getTracksPerSideOnBasic() + basic.getTrackNumberBaseOnDisk();
                    ndir = 1;
                    break;
                case 2:
                    // 管理トラック
                    sta_trk = basic.getManagedTrackNumber();
                    end_trk = sta_trk + 1;
                    ndir = 1;
                    break;
            }

            for (int trk_num = sta_trk; trk_num != end_trk && new_num == INVALID_GROUP_NUMBER; trk_num += ndir) {
                int trk_anum = trk_num - basic.getTrackNumberBaseOnDisk();
                SectorsPerTrack item = sector_map.findByTrackNum(trk_anum);
                int num_of_secs = item.getNumOfSectors();
                for (int sec_pos = 0; sec_pos < num_of_secs; sec_pos++) {
                    SectorSkewBase ss = item.getSectorSkewMap();
                    int sec_num = ss.toPhysical(sec_pos);
                    int sec_anum = sec_num - basic.getSectorNumberBase();
                    if (c1541_bam.isFree(trk_anum, sec_anum)) {
                        new_num = getSectorPosFromNumS(trk_num, sec_num);
                        break;
                    }
                }
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
        int next_group_num = getEmptyGroupNumberM(0);
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
     * 次の空きFAT位置を返す
     */
    private int getDirNextEmptyGroupNumber(int curr_group) {
        // 次の空き位置候補
        int next_group_num = getEmptyGroupNumberM(1);
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
     * データサイズ分のグループを確保する
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryC1541> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) {
        //int file_size = 0;
        int groups = 0;

        int rc = 0;
        // 1セクタ当たり2バイトはチェイン用のリンクポインタになるので減算
        int bytes_per_group = basic.getSectorSize() - 2;
        int remain = data_size;
        int limit = basic.getFatEndGroup() + 1;
        int chain_idx = 0;
        int group_num = INVALID_GROUP_NUMBER;
        if (flags == ALLOCATE_GROUPS_APPEND) {
            // ディレクトリ拡張時
            remain = bytes_per_group;
            group_num = item.getGroups().last().group;
        }
        while (remain > 0 && limit >= 0) {
            // 空きをさがす
            if (flags != ALLOCATE_GROUPS_APPEND) {
                group_num = (chain_idx == 0) ? getEmptyGroupNumber() : getNextEmptyGroupNumber(group_num);
            } else {
                group_num = getDirNextEmptyGroupNumber(group_num);
            }

            if (group_num == INVALID_GROUP_NUMBER) {
                // 空きなし
                rc = groups > 0 ? -2 : -1;
                return rc;
            }

            // 使用済みにする
            basic.getNumsFromGroup(group_num, 0, basic.getSectorSize(), remain, group_items[0]);
            setGroupNumber(group_num, 1);

            if (flags != ALLOCATE_GROUPS_APPEND && chain_idx == 0) {
                item.setStartGroup(0, group_num, 1);
            }
            chain_idx++;

            //file_size += bytes_per_group;
            groups++;
            remain -= bytes_per_group;
            limit--;
        }

        if (groups > 0) {
            // 最終セクタは残りサイズを設定
            remain += bytes_per_group;
            chainLastGroup(group_num, remain);
        }

        if (limit < 0) {
            // 無限ループ？
            rc = groups > 0 ? -2 : -1;
        }

        return rc;
    }

    /**
     * グループをつなげる
     */
    @Override
    public int chainGroups(int group_num, int append_group_num) {
        // 現在のセクタに次のセクタへのポインタをセット
        DiskImageSector sector = basic.getSectorFromSectorPos(group_num);
        if (sector == null) {
            // why?
            return -1;
        }
        // C1541Ptr p = (C1541Ptr) sector.getSectorBuffer(); // TODO Serdes
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            // why?
            return -1;
        }

        int[] next_track_num = {0};
        int[] next_sector_num = {0};
        getNumFromSectorPosS(append_group_num, next_track_num, next_sector_num);
        b[0] = (byte) (next_track_num[0] + C1541_START_TRACK_OFFSET);
        b[1] = (byte) (next_sector_num[0] + C1541_START_SECTOR_OFFSET);

        return 0;
    }

    /**
     * 最終グループをつなげる
     */
    private int chainLastGroup(int group_num, int remain) {
        // 現在のセクタに残りサイズをセット
        DiskImageSector sector = basic.getSectorFromSectorPos(group_num);
        if (sector == null) {
            // why?
            return -1;
        }

        // C1541Ptr p = (C1541Ptr) sector.getSectorBuffer(); // TODO Serdes
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            // why?
            return -1;
        }
        b[0] = 0; // track
        b[1] = (byte) (remain + 1); // sector
        return 0;
    }

    /**
     * データの読み込み/比較処理
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryC1541> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws IOException {
        byte[] buf = Arrays.copyOfRange(sector_buffer, 2, sector_size);
        int size = (sector_size - 2) < remain_size ? (sector_size - 2) : remain_size;

        if (ostream != null) {
            // 書き出し
            temp.setData(buf, size, basic.isDataInverted());
            ostream.write(temp.getData(), 0, temp.getSize());
        }

        if (istream != null) {
            // 読み込んで比較
            temp.setSize(size);
            int readLen = istream.read(temp.getData(), 0, temp.getSize());
            temp.invertData(basic.isDataInverted());

            if (!Arrays.equals(temp.getData(), buf)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /**
     * ファイルの最終セクタのデータサイズを求める
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryC1541> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sectorOffset, int sector_size, int remain_size) {
        return remain_size;
    }

    /**
     * グループ番号からセクタ番号を得る (The C++ method just returns 'group_num')
     */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num;
    }

    /**
     * グループ番号から最終セクタ番号を得る (The C++ method just returns 'group_num')
     */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return group_num;
    }

    /**
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る
     */
    @Override
    public void getNumFromSectorPos(int sector_pos, int[] track_num, int[] side_num, int[] sector_num, int[] div_num, int[] div_nums) {
        int sides_per_disk = basic.getSidesPerDiskOnBasic();
        int numbering_sector = basic.getNumberingSector();
        int[] sectors_per_track = {basic.getSectorsPerTrackOnBasic()};

        // セクタ位置がどのトラックにあるか
        sector_map.getNumFromSectorPos(sector_pos, track_num, sector_num, sectors_per_track);

        // サイド番号
        side_num[0] = sector_num[0] * sides_per_disk / sectors_per_track[0];

        // 連番でない場合
        if (numbering_sector != 1) {
            sector_num[0] = sector_num[0] % (sectors_per_track[0] / sides_per_disk);
        }

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

        sector_map.getNumFromSectorPos(sector_pos, track_num, sector_num, sectors_per_track);

        sector_num[0] += basic.getSectorNumberBase();
        track_num[0] += basic.getTrackNumberBaseOnDisk();
    }

    /**
     * トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     */
    @Override
    public int getSectorPosFromNum(int track_num, int side_num, int sector_num, int div_num, int div_nums) {
        int sides_per_disk = basic.getSidesPerDiskOnBasic();
        int numbering_sector = basic.getNumberingSector();
        int[] sectors_per_track = {basic.getSectorsPerTrackOnBasic()};

        track_num -= basic.getTrackNumberBaseOnDisk();
        side_num -= basic.getSideNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBase();

        int sector_pos = sector_map.getSectorPosFromNum(track_num, sector_num, sectors_per_track);

        // サイド番号を逆転するか
        side_num = basic.getReversedSideNumber(side_num);

        // 連番でない場合
        if (numbering_sector != 1) {
            sector_pos += side_num * sectors_per_track[0] / sides_per_disk;
        }

        return sector_pos;
    }

    /**
     * トラック、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     */
    @Override
    public int getSectorPosFromNumS(int track_num, int sector_num) {
        int[] sectors_per_track = {1};

        track_num -= basic.getTrackNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBase();

        return sector_map.getSectorPosFromNum(track_num, sector_num, sectors_per_track);
    }

    /**
     * ルートディレクトリか (The C++ method returns 'false')
     */
    @Override
    public boolean isRootDirectory(int group_num) {
        return false;
    }

    /**
     * ルートディレクトリのサイズを拡張できるか
     */
    @Override
    public boolean canExpandRootDirectory() {
        return true;
    }

    /**
     * フォーマット時セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        // 可変数セクタなのでトラックごとのセクタ数を集計
        sector_map.create(basic);

        // セクタ数の合計
        basic.diskBasicParam.setFatEndGroup(sector_map.getTotalSectors() - 1);

        // インポート時の空きセクタの探し方を設定
        sector_map.createSectorSkewMap(basic);

        // BAMの作成
        int trk_num = basic.getManagedTrackNumber();
        int sec_num = basic.getSectorNumberBase() - C1541_START_SECTOR_OFFSET;
        sector = basic.getSector(trk_num, sec_num, null);
        if (sector == null) {
            // Why?
            return false;
        }
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            // Why?
            return false;
        }
        c1541_bam_t bam = new c1541_bam_t();
        Util.deserialize(new ByteArrayInputStream(b), bam);
        sector.fill((byte) 0);

        c1541_bam.setBitmap(bam);
        int sector_pos = getSectorPosFromNumS(trk_num, sec_num);
        c1541_bam.setMyGroupNumber(sector_pos);

        // directory
        bam.start_dir.track = (byte) (trk_num + C1541_START_TRACK_OFFSET);
        bam.start_dir.sector = (byte) (sec_num + 1 + C1541_START_SECTOR_OFFSET);

        bam.format_type = 'A';	// 4040format
        bam.disk_id = 0x3030;	// "00"
        bam.space0 = (byte) 0xa0;
        bam.dos_version = '2';
        bam.dos_format = 'A';
        bam.space1 = (byte) 0xa0;

        // bitmap クリア
        for (int trk = 0; trk < basic.getTracksPerSide(); trk++) {
            SectorsPerTrack item = sector_map.findByTrackNum(trk);
            if (item != null) {
                c1541_bam.freeTrack(trk, item.getNumOfSectors());
            }
        }
        int trk_npos = trk_num - basic.getTrackNumberBaseOnDisk();
        int sec_npos = sec_num - basic.getSectorNumberBase();
        c1541_bam.modify(trk_npos, sec_npos, true);
        c1541_bam.modify(trk_npos, sec_npos + 1, true);

        // ディレクトリ
        sec_num++;
        sector = basic.getSector(trk_num, sec_num, null);
        if (sector == null) {
            // Why?
            return false;
        }
        b = sector.getSectorBuffer();
        if (b == null) {
            // Why?
            return false;
        }
        // C1541Ptr next = (C1541Ptr) sector.getSectorBuffer(); // TODO Serdes
        sector.fill((byte) 0);
        b[1] = (byte) 0xff; // next.sector

        basic.diskBasicParam.setDirStartSector(sec_num);

        // volume name
        setIdentifiedData(data);

        return true;
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryC1541> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        int len = 0;

        // セクタの2バイト目から
        int buf_offset = 2;
        size -= 2;

        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) istream.readNBytes(buffer, buf_offset, remain);
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, buf_offset + remain, buf_offset + size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            istream.readNBytes(buffer, buf_offset, size);
            len = size;
        }

        return len;
    }

    /**
     * データの書き込み終了後の処理
     */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryC1541> item) throws IOException {
        // RELファイルか
        int type1 = item.getFileAttr().getOrigin();
        int rec_len = (type1 >> 8);
        type1 &= 0xff;
        if (type1 != FILETYPE_MASK_C1541_REL || rec_len == 0) {
            return;
        }

        // RELファイルの時は、サイドセクタを作成する
        int bytes_per_group = basic.getSectorSize() - 2;
        DiskBasicGroups data_groups = item.getGroups();

        int blocks = data_groups.size();
        int ss_max = blocks / 120;
        if (ss_max >= 6) {
            return;
        }

        DiskBasicGroups side_groups = new DiskBasicGroups();
        int group_num = INVALID_GROUP_NUMBER;
        int ss_size = 0;
        int data_pos = 0;
        c1541_side_sector_t[] side_sectors = new c1541_side_sector_t[6];
        for (int ss_idx = 0; ss_idx <= ss_max; ss_idx++) {
            // 空きをさがす
            group_num = (ss_idx == 0) ? getEmptyGroupNumber() : getNextEmptyGroupNumber(group_num);
            if (group_num == INVALID_GROUP_NUMBER) {
                // 空きなし
                return;
            }
            int[] track_num = {0};
            int[] side_num = {0};
            DiskImageSector sector = basic.getSectorFromSectorPos(group_num, track_num, side_num);
            if (sector == null) {
                return;
            }

            byte[] b = sector.getSectorBuffer();
            if (b == null) {
                return;
            }
            c1541_side_sector_t side_sector = new c1541_side_sector_t();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), side_sector);
            side_sectors[ss_idx] = side_sector;

            sector.fill((byte) 0);
            setGroupNumber(group_num, 1);
            side_groups.add(group_num, 0, track_num[0], side_num[0], sector.getSectorNumber(), sector.getSectorNumber());
            ss_size += bytes_per_group;
            if (ss_idx == 0) {
                // サイドセクタ開始ポインタを設定
                item.setExtraGroup(group_num);
            } else {
                // サイドセクタへのポインタをコピー
                System.arraycopy(side_sectors[ss_idx - 1].side_pos, 0, side_sectors[ss_idx].side_pos, 0, side_sectors[ss_idx].side_pos.length);
            }

            side_sector.side_num = (byte) (ss_idx & 0xff);
            side_sector.record_length = (byte) (rec_len & 0xff);

            int[] sec_num = {0};
            getNumFromSectorPosS(group_num, track_num, sec_num);
            track_num[0] += C1541_START_TRACK_OFFSET;
            sec_num[0] += C1541_START_SECTOR_OFFSET;

            // サイドセクタへのポインタを設定
            for (int i = 0; i <= ss_idx; i++) {
                c1541_side_sector_t ss = side_sectors[i];
                ss.side_pos[ss_idx].track = (byte) (track_num[0] & 0xff);
                ss.side_pos[ss_idx].sector = (byte) (sec_num[0] & 0xff);
            }

            // データへのポインタを設定
            for (int i = 0; i < 120 && data_pos < blocks; i++) {
                getNumFromSectorPosS(data_groups.get(data_pos).group, track_num, sec_num);
                track_num[0] += C1541_START_TRACK_OFFSET;
                sec_num[0] += C1541_START_SECTOR_OFFSET;

                side_sector.data_pos[i].track = (byte) (track_num[0] & 0xff);
                side_sector.data_pos[i].sector = (byte) (sec_num[0] & 0xff);

                data_pos++;
            }

            // Note: After setting fields on 'side_sector', the sector buffer 'sectorBuffer'
            // must be updated with the contents of 'side_sector'.
        }

        // 最終セクタは残りサイズを設定
        chainLastGroup(group_num, (blocks % 120) * 2 + 14);

        // ブロックサイズを設定
        ss_size = ss_size - bytes_per_group + (blocks % 120) * 2 + 14;
        side_groups.setSize(ss_size);
        side_groups.setNums(ss_max + 1);
        side_groups.setSizePerGroup(basic.getSectorSize());
        item.setExtraGroups(side_groups);
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
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryC1541> item) throws IOException {
        // サイドセクタを未使用にする
        DiskBasicGroups[] grps = new DiskBasicGroups[1];
        item.getExtraGroups(grps);

        deleteGroups(grps[0]);

        return true;
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume name
        byte[] name = new byte[c1541_bam.getDiskNameSize() + 1];
        Arrays.fill(name, (byte) 0);
        int len = c1541_bam.getDiskName(name, name.length);

        Common.rtrim(name, name.length, basic.diskBasicParam.getDirSpaceCode());

        String wname = new String(name, 0, len, basic.getCharCodes().charset());
        data.setVolumeName(wname);
        data.setVolumeNameMaxLength(len);

        // volume id
        data.setVolumeNumber(c1541_bam.getDiskID());
        data.volumeNumberIsHexa(true);
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat fmt = basic.getFormatType();

        // volume name
        if (fmt.hasVolumeName()) {
            byte[] name = new byte[c1541_bam.getDiskNameSize() + 1];
            System.arraycopy(data.getVolumeName().getBytes(basic.getCharCodes().charset()), 0, name, 0, Math.min(data.getVolumeName().length(), c1541_bam.getDiskNameSize()));
            Common.padding(name, name.length, basic.diskBasicParam.getDirSpaceCode());
            c1541_bam.setDiskName(name, c1541_bam.getDiskNameSize());
        }
        // volume id
        if (fmt.hasVolumeNumber()) {
            c1541_bam.setDiskID(data.getVolumeNumber());
        }
    }
}
