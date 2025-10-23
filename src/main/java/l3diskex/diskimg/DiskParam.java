package l3diskex.diskimg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import l3diskex.Parambase.TemplatesBase;
import l3diskex.Utils;
import org.xml.sax.SAXException;


public class DiskParam {

    public static DiskTemplates gDiskTemplates = new DiskTemplates();

    public static class SectorParam {

        protected int track_num;
        protected int side_num;
        protected int sector_num;
        protected int sector_size;

        //
        // トラック＆サイド＆セクタ番号を保持
        //
        public SectorParam() {
            this.track_num = -1;
            this.side_num = -1;
            this.sector_num = -1;
            this.sector_size = -1;
        }

        /// 番号を保持
        ///
        /// @param n_track_num   トラック番号
        /// @param n_side_num    サイド番号
        /// @param n_sector_num  セクタ番号
        /// @param n_sector_size セクタサイズ
        public SectorParam(int n_track_num, int n_side_num, int n_sector_num, int n_sector_size) {
            this.track_num = n_track_num;
            this.side_num = n_side_num;
            this.sector_num = n_sector_num;
            this.sector_size = n_sector_size;
        }

        /// 比較
        public boolean equals(SectorParam dst) {
            return (track_num == dst.track_num
                    && side_num == dst.side_num
                    && sector_num == dst.sector_num
                    && sector_size == dst.sector_size);
        }

        /// 指定したトラック、サイドが特殊なセクタ（単密度など）か
        ///
        /// @param n_track_num   トラック番号
        /// @param n_side_num    サイド番号
        /// @param n_sector_num  セクタ番号
        /// @param n_sector_size セクタサイズ
        /// @note セクタサイズが<0 の時 、 条件から除外する
        public boolean match(int n_track_num, int n_side_num, int n_sector_num, int n_sector_size) {
            return ((track_num < 0 || (track_num == n_track_num))
                    && (side_num < 0 || (side_num == n_side_num))
                    && (sector_num < 0 || (sector_num == n_sector_num))
                    && (sector_size < 0 || n_sector_size < 0 || (sector_size == n_sector_size)));
        }

        public void setTrackNumber(int val) {
            track_num = val;
        }

        public void setSideNumber(int val) {
            side_num = val;
        }

        public void setSectorNumber(int val) {
            sector_num = val;
        }

        public int getTrackNumber() {
            return track_num;
        }

        public int getSideNumber() {
            return side_num;
        }

        public int getSectorNumber() {
            return sector_num;
        }
    }

    public static class TrackParam extends SectorParam {

        public static final int ID_IS_VALID = 0x8000;

        protected int num_of_tracks;
        protected int sectors_per_track;
        protected int[] id;

        //
        // 単密度など特殊なトラックやセクタ情報を保持する
        //
        public TrackParam() {
            super();
            this.num_of_tracks = 1;
            this.sectors_per_track = -1;
            this.id = new int[4];
        }

        /// 特殊なトラックやセクタを登録
        ///
        /// @param n_track_num         トラック番号(-1:ALL)
        /// @param n_side_num          サイド番号(-1:ALL)
        /// @param n_sector_num        セクタ番号(特殊セクタ指定時のみ)
        /// @param n_num_of_tracks     トラック数(特殊トラック指定時のみ)
        /// @param n_sectors_per_track セクタ数(特殊トラック指定時のみ)
        /// @param n_sector_size       セクタサイズ
        public TrackParam(int n_track_num, int n_side_num, int n_sector_num, int n_num_of_tracks, int n_sectors_per_track, int n_sector_size) {
            super(n_track_num, n_side_num, n_sector_num, n_sector_size);
            this.num_of_tracks = n_num_of_tracks;
            this.sectors_per_track = n_sectors_per_track;
            this.id = new int[4];
        }

        /// 比較
        public boolean equals(TrackParam dst) {
            return (track_num == dst.track_num
                    && side_num == dst.side_num
                    && sector_num == dst.sector_num
                    && num_of_tracks == dst.num_of_tracks
                    && (sectors_per_track < 0 || dst.sectors_per_track < 0 || sectors_per_track == dst.sectors_per_track)
                    && sector_size == dst.sector_size);
        }

        /// IDをセット
        public void setID(int idx, int val) {
            if (idx >= 0 && idx < 4) {
                id[idx] = val;
            }
        }

        /// IDを返す
        public int getID(int idx) {
            if (idx >= 0 && idx < 4) {
                return id[idx];
            } else {
                return 0;
            }
        }

        public void setNumberOfTracks(int val) {
            num_of_tracks = val;
        }

        public void setSectorsPerTrack(int val) {
            sectors_per_track = val;
        }

        public void setSectorSize(int val) {
            sector_size = val;
        }

        public int getNumberOfTracks() {
            return num_of_tracks;
        }

        public int getSectorsPerTrack() {
            return sectors_per_track;
        }

        public int getSectorSize() {
            return sector_size;
        }

        public int[] getID() {
            return id;
        }
    }

    public static class DiskParticular extends TrackParam {

        private final List<TrackParam> excludes;

        //
        // 単密度など特殊なトラックやセクタ情報を保持する
        //
        public DiskParticular() {
            super();
            this.excludes = new ArrayList<>();
        }

        /// 特殊なトラックやセクタを登録
        ///
        /// @param n_track_num         トラック番号(-1:ALL)
        /// @param n_side_num          サイド番号(-1:ALL)
        /// @param n_sector_num        セクタ番号(特殊セクタ指定時のみ)
        /// @param n_num_of_tracks     トラック数(特殊トラック指定時のみ)
        /// @param n_sectors_per_track セクタ数(特殊トラック指定時のみ)
        /// @param n_sector_size       セクタサイズ
        public DiskParticular(int n_track_num, int n_side_num, int n_sector_num, int n_num_of_tracks, int n_sectors_per_track, int n_sector_size) {
            super(n_track_num, n_side_num, n_sector_num, n_num_of_tracks, n_sectors_per_track, n_sector_size);
            this.excludes = new ArrayList<>();
        }

        /// 除外するトラックをリストに追加
        ///
        /// @param n_param パラメータ
        public void addExclude(TrackParam n_param) {
            excludes.add(n_param);
        }

        /// トラックが除外リストに含まれるか
        ///
        /// @param n_track_num トラック番号
        /// @param n_side_num  サイド番号
        public boolean findExclude(int n_track_num, int n_side_num) {
            TrackParam match = null;
            for (int i = 0; i < excludes.size(); i++) {
                TrackParam item = excludes.get(i);
                if (n_track_num != item.getTrackNumber()) continue;
                if (n_side_num != item.getSideNumber()) continue;
                match = item;
                break;
            }
            return (match != null);
        }

        /**
         * 同じセクタのものをまとめる
         *
         * @param sectors セクタ数
         * @param arr     [in,out] リスト
         */
        public static void uniqueSectors(int sectors, DiskParticulars arr) {
            int count = arr.size();
            if (count <= 1) return;

            if (sectors == count) {
                DiskParticulars newarr = new DiskParticulars();
                DiskParticular sd = arr.get(0);
                newarr.add(new DiskParticular(sd.getTrackNumber(), sd.getSideNumber(), -1, sd.getNumberOfTracks(), sd.getSectorsPerTrack(), sd.getSectorSize()));
                arr.clear();
                arr.addAll(newarr);
            }
        }

        /**
         * 同じトラックやサイドのものをまとめる
         *
         * @param tracks     トラック数
         * @param sides      サイド数
         * @param both_sides 両面タイプか
         * @param arr        [in,out] リスト
         */
        public static void uniqueTracks(int tracks, int sides, boolean both_sides, DiskParticulars arr) {
            int count = arr.size();
            if (count <= 1) return;

            DiskParticulars newarr = new DiskParticulars();
            DiskParticular prev_sd;

            prev_sd = arr.get(0);
            // 同じトラック番号で全サイドが同じパラメータ(単密度)であればまとめる
            int side_count = 1;
            boolean all_sides = true;
            for (int idx = 1; idx <= count; idx++) {
                DiskParticular sd = idx < count ? arr.get(idx) : null;
                if (prev_sd.getSectorNumber() >= 0) {
                    newarr.add(prev_sd);
                    all_sides = false;
                    side_count = 0;
                } else if (sd == null || prev_sd.getTrackNumber() != sd.getTrackNumber()) {
                    if (side_count >= sides || both_sides) {
                        newarr.add(new DiskParticular(prev_sd.getTrackNumber(), -1, -1, prev_sd.getNumberOfTracks(), prev_sd.getSectorsPerTrack(), prev_sd.getSectorSize()));
                    } else {
                        newarr.add(new DiskParticular(prev_sd.getTrackNumber(), prev_sd.getSideNumber(), -1, prev_sd.getNumberOfTracks(), prev_sd.getSectorsPerTrack(), prev_sd.getSectorSize()));
                        all_sides = false;
                    }
                    side_count = 0;
                }
                side_count++;
                prev_sd = sd;
            }
            arr.clear();
            arr.addAll(newarr);

            count = arr.size();
            newarr.clear();
            // 全トラックが同じパラメータ(単密度)であればまとめる
            if (all_sides && count >= tracks) {
                DiskParticular sd = arr.get(0);
                newarr.add(new DiskParticular(-1, -1, -1, sd.getNumberOfTracks(), sd.getSectorsPerTrack(), sd.getSectorSize()));
                arr.clear();
                arr.addAll(newarr);
            }
        }
    }

    //
    // 単密度など特殊なトラックやセクタ情報を保持するリスト DiskParticular の配列
    //
    public static class DiskParticulars extends ArrayList<DiskParticular> {

        public DiskParticulars() {
            super();
        }

        /// セクタ/トラックのリスト内で最小値を返す
        public int getMinSectorsPerTrack(int default_number) {
            int val = 0xffff;
            for (int i = 0; i < size(); i++) {
                if (get(i).getSectorsPerTrack() < val) {
                    val = get(i).getSectorsPerTrack();
                }
            }
            if (default_number < val) {
                val = default_number;
            }
            return val;
        }

        /// セクタ/トラックのリスト内で最大値を返す
        public int getMaxSectorsPerTrack(int default_number) {
            int val = 0;
            for (int i = 0; i < size(); i++) {
                if (get(i).getSectorsPerTrack() > val) {
                    val = get(i).getSectorsPerTrack();
                }
            }
            if (default_number > val) {
                val = default_number;
            }
            return val;
        }

        /// 全ての値が一致するか
        public boolean equals(DiskParticulars dst) {
            boolean match = true;
            if (this.size() != dst.size()) {
                match = false;
            } else {
                for (int di = 0; di < dst.size(); di++) {
                    boolean sm = false;
                    for (int si = 0; si < this.size(); si++) {
                        DiskParticular ss = this.get(si);
                        DiskParticular ds = dst.get(di);
                        if (ss.equals(ds)) {
                            sm = true;
                            break;
                        }
                    }
                    if (!sm) {
                        match = false;
                        break;
                    }
                }
            }
            return match;
        }
    }

    public static class NumSectorsParam {

        protected int start_track_num;
        protected int num_of_tracks;
        protected int sectors_per_track;

        //
        // 各トラックのセクタ数を保持
        //
        public NumSectorsParam() {
            this.start_track_num = 0;
            this.num_of_tracks = 0;
            this.sectors_per_track = 1;
        }

        /// @param[in] n_start_track_num   開始トラック番号
        /// @param[in] n_num_of_tracks     トラック数
        /// @param[in] n_sectors_per_track セクタ数/トラック
        public NumSectorsParam(int n_start_track_num, int n_num_of_tracks, int n_sectors_per_track) {
            this.start_track_num = n_start_track_num;
            this.num_of_tracks = n_num_of_tracks;
            this.sectors_per_track = n_sectors_per_track;
        }

        public void setStartTrackNumber(int val) {
            start_track_num = val;
        }

        public void setNumberOfTracks(int val) {
            num_of_tracks = val;
        }

        public void setSectorsPerTrack(int val) {
            sectors_per_track = val;
        }

        public int getStartTrackNumber() {
            return start_track_num;
        }

        public int getNumberOfTracks() {
            return num_of_tracks;
        }

        public int getSectorsPerTrack() {
            return sectors_per_track;
        }
    }

    //
    // 各トラックのセクタ数を保持しているリスト
    //
    public static class NumSectorsParams extends ArrayList<NumSectorsParam> {

        public NumSectorsParams() {
            super();
        }

        /// セクタ/トラックのリスト内の最小値を返す
        public int getMinSectorOfTracks() {
            int val = 0xffff;
            for (int i = 0; i < size(); i++) {
                if (get(i).getSectorsPerTrack() < val) {
                    val = get(i).getSectorsPerTrack();
                }
            }
            return val;
        }

        public int getMaxSectorOfTracks() {
            int val = 0;
            for (int i = 0; i < size(); i++) {
                if (get(i).getSectorsPerTrack() > val) {
                    val = get(i).getSectorsPerTrack();
                }
            }
            return val;
        }
    }

    public static class DiskParamName {

        private String name;
        private int flags;

        public DiskParamName() {
            this.flags = 0;
        }

        public void setName(String val) {
            name = val;
        }

        public String getName() {
            return name;
        }

        public void setFlags(int val) {
            flags = val;
        }

        public int getFlags() {
            return flags;
        }
    }

    public static class SectorInterleave {

        private boolean has_map;
        private List<Integer> secs;

        public SectorInterleave() {
            this.has_map = false;
            this.secs = new ArrayList<>();
            this.secs.add(0);
        }

        public int get() {
            return secs.get(0);
        }

        public int get(int idx) {
            if (idx < secs.size()) {
                return secs.get(idx);
            } else {
                return 0;
            }
        }

        public void set(int val) {
            has_map = false;
            secs.set(0, val);
        }

        public void set(List<Integer> val) {
            has_map = true;
            secs = new ArrayList<>(val);
        }

        public boolean hasMap() {
            return has_map;
        }
    }

    protected String disk_type_name;
    protected List<DiskParamName> basic_types;
    protected boolean reversible;
    protected int sides_per_disk;
    protected int tracks_per_side;
    protected int sectors_per_track;
    protected int sector_size;
    protected int numbering_sector;
    protected int disk_density;
    protected int interleave;
    protected int track_number_base;
    protected int side_number_base;
    protected int sector_number_base;
    protected boolean variable_secs_per_trk;
    protected DiskParticulars singles;
    protected DiskParticulars ptracks;
    protected DiskParticulars psectors;
    protected String density_name;
    protected String description;

    public DiskParam() {
        this.clearDiskParam();
    }

    public DiskParam(DiskParam src) {
        this.setDiskParam(src);
    }

    public void setDiskParam(DiskParam src) {
        this.disk_type_name = src.disk_type_name;
        this.basic_types = new ArrayList<>(src.basic_types);
        this.reversible = src.reversible;
        this.sides_per_disk = src.sides_per_disk;
        this.tracks_per_side = src.tracks_per_side;
        this.sectors_per_track = src.sectors_per_track;
        this.sector_size = src.sector_size;
        this.numbering_sector = src.numbering_sector;
        this.disk_density = src.disk_density;
        this.interleave = src.interleave;
        this.track_number_base = src.track_number_base;
        this.side_number_base = src.side_number_base;
        this.sector_number_base = src.sector_number_base;
        this.variable_secs_per_trk = src.variable_secs_per_trk;
        this.singles = new DiskParticulars();
        this.singles.addAll(src.singles);
        this.ptracks = new DiskParticulars();
        this.ptracks.addAll(src.ptracks);
        this.psectors = new DiskParticulars();
        this.psectors.addAll(src.psectors);
        this.density_name = src.density_name;
        this.description = src.description;
    }

    /// 全パラメータを設定
    ///
    /// @param n_type_name             ディスク種類名 "2D" "2HD" など
    /// @param n_basic_types           BASIC種類（DiskBasicParamとのマッチングにも使用）
    /// @param n_reversible            裏返し可能 AB面あり（L3用3インチFDなど）
    /// @param n_sides_per_disk        サイド数
    /// @param n_tracks_per_side       トラック数
    /// @param n_sectors_per_track     セクタ数
    /// @param n_sector_size           セクタサイズ
    /// @param n_numbering_sector      セクタ番号の付番方法(0:サイド毎、1:トラック毎)
    /// @param n_disk_density          0x00:2D 0x10:2DD 0x20:2HD
    /// @param n_interleave            セクタの間隔
    /// @param n_track_number_base     開始トラック番号
    /// @param n_side_number_base      開始サイド番号
    /// @param n_sector_number_base    開始セクタ番号
    /// @param n_variable_secs_per_trk セクタ数がトラックごとに異なるか
    /// @param n_singles               単密度にするトラック
    /// @param n_ptracks               特殊なトラックを定義 セクタ数がトラックごとに異なる場合
    /// @param n_psectors              特殊なセクタを定義
    /// @param n_density_name          密度情報（説明用）
    /// @param n_desc                  説明
    public void setDiskParam(String n_type_name, List<DiskParamName> n_basic_types, boolean n_reversible,
                             int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size,
                             int n_numbering_sector, int n_disk_density, int n_interleave, int n_track_number_base,
                             int n_side_number_base, int n_sector_number_base, boolean n_variable_secs_per_trk,
                             DiskParticulars n_singles, DiskParticulars n_ptracks, DiskParticulars n_psectors,
                             String n_density_name, String n_desc) {
        this.disk_type_name = n_type_name;
        this.basic_types = new ArrayList<>(n_basic_types);
        this.reversible = n_reversible;
        this.sides_per_disk = n_sides_per_disk;
        this.tracks_per_side = n_tracks_per_side;
        this.sectors_per_track = n_sectors_per_track;
        this.sector_size = n_sector_size;
        this.numbering_sector = n_numbering_sector;
        this.disk_density = n_disk_density;
        this.interleave = n_interleave;
        this.track_number_base = n_track_number_base;
        this.side_number_base = n_side_number_base;
        this.sector_number_base = n_sector_number_base;
        this.variable_secs_per_trk = n_variable_secs_per_trk;
        this.singles = new DiskParticulars();
        this.singles.addAll(n_singles);
        this.ptracks = new DiskParticulars();
        this.ptracks.addAll(n_ptracks);
        this.psectors = new DiskParticulars();
        this.psectors.addAll(n_psectors);
        this.density_name = n_density_name;
        this.description = n_desc;
    }

    /// 主要パラメータだけ設定
    ///
    /// @param n_sides_per_disk    サイド数
    /// @param n_tracks_per_side   トラック数
    /// @param n_sectors_per_track セクタ数
    /// @param n_sector_size       セクタサイズ
    /// @param n_disk_density      0x00:2D 0x10:2DD 0x20:2HD
    /// @param n_interleave        セクタの間隔
    /// @param n_singles           単密度にするトラック
    /// @param n_ptracks           特殊なトラックを定義 セクタ数がトラックごとに異なる場合
    public void setDiskParam(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track,
                             int n_sector_size, int n_disk_density, int n_interleave,
                             DiskParticulars n_singles, DiskParticulars n_ptracks) {
        this.sides_per_disk = n_sides_per_disk;
        this.tracks_per_side = n_tracks_per_side;
        this.sectors_per_track = n_sectors_per_track;
        this.sector_size = n_sector_size;
        this.disk_density = n_disk_density;
        this.interleave = n_interleave;
        this.singles = new DiskParticulars();
        this.singles.addAll(n_singles);
        this.ptracks = new DiskParticulars();
        this.ptracks.addAll(n_ptracks);
    }

    /// 初期化
    public void clearDiskParam() {
        this.disk_type_name = "";
        this.basic_types = new ArrayList<>();
        this.reversible = false;
        this.sides_per_disk = 0;
        this.tracks_per_side = 0;
        this.sectors_per_track = 0;
        this.sector_size = 0;
        this.numbering_sector = 0;
        this.disk_density = 0;
        this.interleave = 1;
        this.track_number_base = 0;
        this.side_number_base = 0;
        this.sector_number_base = 1;
        this.variable_secs_per_trk = false;
        this.singles = new DiskParticulars();
        this.ptracks = new DiskParticulars();
        this.psectors = new DiskParticulars();
        this.density_name = "";
        this.description = "";
    }

    /// 指定したパラメータで一致するものがあるか
    ///
    /// @param n_sides_per_disk     サイド/ディスク
    /// @param n_tracks_per_side    トラック/サイド
    /// @param n_sectors_per_track  セクタ/トラック
    /// @param n_sector_size        セクタサイズ
    /// @param n_interleave         インターリーブ
    /// @param n_track_number_base  開始トラック番号
    /// @param n_side_number_base   開始サイド番号
    /// @param n_sector_number_base 開始セクタ番号
    /// @param n_numbering_sector   連番セクタか
    /// @param n_singles            単密度
    /// @param n_ptracks            特殊なトラック
    /// @return true:一致する
    public boolean match(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size,
                         int n_interleave, int n_track_number_base, int n_side_number_base, int n_sector_number_base,
                         int n_numbering_sector, DiskParticulars n_singles, DiskParticulars n_ptracks) {
        boolean match = (sides_per_disk == n_sides_per_disk)
                && (tracks_per_side == n_tracks_per_side)
                && (sectors_per_track == n_sectors_per_track)
                && (sector_size == n_sector_size)
                && (interleave == n_interleave)
                && (track_number_base == n_track_number_base)
                && (side_number_base == n_side_number_base)
                && (sector_number_base == n_sector_number_base)
                && (numbering_sector == n_numbering_sector)
                && (singles.equals(n_singles))
                && (ptracks.equals(n_ptracks));
        return match;
    }

    /// 指定したパラメータで一致するものがあるか
    ///
    /// @param n_sides_per_disk    サイド/ディスク
    /// @param n_tracks_per_side   トラック/サイド
    /// @param n_sectors_per_track セクタ/トラック
    /// @param n_sector_size       セクタサイズ
    /// @return true:一致する
    public boolean match(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size) {
        boolean match = (sides_per_disk == n_sides_per_disk)
                && (tracks_per_side == n_tracks_per_side)
                && (sectors_per_track == n_sectors_per_track)
                && (sector_size == n_sector_size);
        return match;
    }

    /// 指定したパラメータで一致するものがあるか
    ///
    /// @param param パラメータ
    /// @return true:一致する
    public boolean match(DiskParam param) {
        boolean match = (disk_type_name.equals(param.disk_type_name))
                && (reversible == param.reversible)
                && (sides_per_disk == param.sides_per_disk)
                && (tracks_per_side == param.tracks_per_side)
                && (sectors_per_track == param.sectors_per_track)
                && (sector_size == param.sector_size)
                && (interleave == param.interleave)
                && (track_number_base == param.track_number_base)
                && (side_number_base == param.side_number_base)
                && (sector_number_base == param.sector_number_base)
                && (numbering_sector == param.numbering_sector)
                && (singles.equals(param.singles));
        return match;
    }

    /// 指定したパラメータで一致するものがあるか
    ///
    /// @param param パラメータ
    /// @return true:一致する
    public boolean matchExceptName(DiskParam param) {
        boolean match = (reversible == param.reversible)
                && (sides_per_disk == param.sides_per_disk)
                && (tracks_per_side == param.tracks_per_side)
                && (sectors_per_track == param.sectors_per_track)
                && (sector_size == param.sector_size)
                && (interleave == param.interleave)
                && (track_number_base == param.track_number_base)
                && (side_number_base == param.side_number_base)
                && (sector_number_base == param.sector_number_base)
                && (numbering_sector == param.numbering_sector)
                && (singles.equals(param.singles))
                && (ptracks.equals(param.ptracks));
        return match;
    }

    /**
     * 指定したパラメータに近い値で一致するものがあるか
     *
     * @param num                 フェーズ番号
     * @param n_sides_per_disk    サイド/ディスク
     * @param n_tracks_per_side   トラック/サイド
     * @param n_sectors_per_track セクタ/トラック
     * @param n_sector_size       セクタサイズ
     * @param n_interleave        インターリーブ
     * @param n_numbering_sector  連番セクタか
     * @param n_singles           単密度
     * @param last                [out] 検索終わり
     * @return true:一致する
     */
    public boolean matchNear(int num, int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track,
                             int n_sector_size, int n_interleave, int n_numbering_sector,
                             DiskParticulars n_singles, boolean[] last) {
        boolean match = false;
        switch (num) {
            case 0:
                // 特殊なトラックを除いて比較
                match = (sides_per_disk == n_sides_per_disk) // サイド数は一致
                        && (tracks_per_side == n_tracks_per_side)
                        && (sectors_per_track == n_sectors_per_track)
                        && (sector_size == n_sector_size)
                        && (interleave == n_interleave)
                        && (numbering_sector == n_numbering_sector)
                        && (singles.equals(n_singles));
                break;
            case 1:
                // compare without interleave
                match = (sides_per_disk == n_sides_per_disk) // サイド数は一致
                        && (tracks_per_side == n_tracks_per_side) // トラック数は一致
                        && (sectors_per_track == n_sectors_per_track) // セクタ数は一致
                        && (sector_size == n_sector_size) // セクタサイズは一致
                        && (numbering_sector == n_numbering_sector)    // セクタ番号の付番方法は一致
                        && (singles.equals(n_singles));    // 単密度のトラックは一致
                break;
            case 2:
                // インターリーブを入れて
                // トラック数が指定範囲内で比較
                match = (sides_per_disk == n_sides_per_disk) // サイド数は一致
                        && (sectors_per_track == n_sectors_per_track) // セクタ数は一致
                        && (sector_size == n_sector_size) // セクタサイズは一致
                        && (numbering_sector == n_numbering_sector)    // セクタ番号の付番方法は一致
                        && (singles.equals(n_singles))    // 単密度のトラックは一致
                        && (interleave == n_interleave) // インターリーブは一致
                        && ((n_tracks_per_side - 5) <= tracks_per_side && tracks_per_side <= n_tracks_per_side); // トラック数は-5 - 0の範囲
                break;
            case 3:
                // インターリーブを除いて、
                // トラック数が指定範囲内で比較
                match = (sides_per_disk == n_sides_per_disk) // サイド数は一致
                        && (sectors_per_track == n_sectors_per_track) // セクタ数は一致
                        && (sector_size == n_sector_size) // セクタサイズは一致
                        && (numbering_sector == n_numbering_sector)    // セクタ番号の付番方法は一致
                        && (singles.equals(n_singles))    // 単密度のトラックは一致
                        && ((n_tracks_per_side - 5) <= tracks_per_side && tracks_per_side <= n_tracks_per_side); // トラック数は-5 - 0の範囲
                break;
            case 4:
                match = (sides_per_disk == n_sides_per_disk) // サイド数は一致
                        && (sectors_per_track == n_sectors_per_track) // セクタ数は一致
                        && (sector_size == n_sector_size) // セクタサイズは一致
                        && (numbering_sector == n_numbering_sector)    // セクタ番号の付番方法は一致
                        && ((n_tracks_per_side - 5) <= tracks_per_side && tracks_per_side <= n_tracks_per_side); // トラック数は-5 - 0の範囲
                break;
            case 5:
                match = (sides_per_disk == n_sides_per_disk) // サイド数は一致
                        && (sector_size == n_sector_size) // セクタサイズは一致
                        && (numbering_sector == n_numbering_sector)    // セクタ番号の付番方法は一致
                        && ((n_tracks_per_side - 5) <= tracks_per_side && tracks_per_side <= n_tracks_per_side) // トラック数は-5 - 0の範囲
                        && (sectors_per_track <= n_sectors_per_track); // セクタ数は小さければよし
                break;
            default:
                last[0] = true;
                break;
        }
        return match;
    }

    /**
     * 指定したトラック、サイドが単密度か
     *
     * @param track_num         トラック番号
     * @param side_num          サイド番号
     * @param sectors_per_track [out] セクタ数
     * @param sector_size       [out] セクタサイズ
     * @return true/false
     */
    public boolean findSingleDensity(int track_num, int side_num, int[] sectors_per_track, int[] sector_size) {
        boolean match = false;
        for (int i = 0; i < singles.size(); i++) {
            DiskParticular sd = singles.get(i);
            if (sd.match(track_num, side_num, -1, -1)) {
                match = true;
                if (sectors_per_track != null && sd.getSectorsPerTrack() > 0) {
                    sectors_per_track[0] = sd.getSectorsPerTrack();
                }
                if (sector_size != null && sd.getSectorSize() > 0) {
                    sector_size[0] = sd.getSectorSize();
                }
                break;
            }
        }
        return match;
    }

    /// 指定したトラック、サイド、セクタが単密度か
    ///
    /// @param track_num   トラック番号
    /// @param side_num    サイド番号
    /// @param sector_num  セクタ番号
    /// @param sector_size セクタサイズ
    /// @return true/false
    public boolean findSingleDensity(int track_num, int side_num, int sector_num, int sector_size) {
        boolean match = false;
        for (int i = 0; i < singles.size(); i++) {
            DiskParticular sd = singles.get(i);
            if (sd.match(track_num, side_num, sector_num, sector_size)) {
                match = true;
                break;
            }
        }
        return match;
    }

    /**
     * 単密度を持っているか
     *
     * @param sectors_per_track [out] セクタ数
     * @param sector_size       [out] セクタサイズ
     * @return 0 なし
     * @return 1 全トラック
     * @return 2 トラック0,サイド0
     * @return 3 トラック0,両面
     */
    public int hasSingleDensity(int[] sectors_per_track, int[] sector_size) {
        int val = 0;
        DiskParticular sd = null;
        for (int i = 0; i < singles.size(); i++) {
            if (singles.get(i).getTrackNumber() < 0 && singles.get(i).getSideNumber() < 0) {
                sd = singles.get(i);
                val = 1;
                break;
            } else if (singles.get(i).getTrackNumber() == 0 && singles.get(i).getSideNumber() < 0) {
                sd = singles.get(i);
                val = 3;
                break;
            } else if (singles.get(i).getTrackNumber() == 0 && singles.get(i).getSideNumber() == 0) {
                sd = singles.get(i);
                val = 2;
                break;
            }
        }
        int max_tracks = getTracksPerSide() * getSidesPerDisk();
        if (max_tracks > 0 && max_tracks == singles.size()) {
            sd = singles.get(0);
            val = 1;
        }
        if (val > 0) {
            if (sectors_per_track != null) {
                sectors_per_track[0] = sd.getSectorsPerTrack();
                if (sectors_per_track[0] < 0) {
                    sectors_per_track[0] = getSectorsPerTrack();
                }
            }
            if (sector_size != null) {
                sector_size[0] = sd.getSectorSize();
            }
        }
        return val;
    }

    /// ディスクサイズを計算する（ベタディスク用）
    public int calcDiskSize() {
        int disk_size = 0;
        int trk = getTrackNumberBaseOnDisk();
        int trks = getTracksPerSide() + trk;
        for (; trk < trks; trk++) {
            for (int sid = 0; sid < getSidesPerDisk(); sid++) {
                int sec_nums = getSectorsPerTrack();
                int sec_size = getSectorSize();
                findParticularTrack(trk, sid, new int[] {sec_nums}, new int[] {sec_size});
                findSingleDensity(trk, sid, new int[] {sec_nums}, new int[] {sec_size});

                for (int sec = 0; sec < sec_nums; sec++) {
                    findParticularSector(trk, sid, sec, new int[] {sec_size}, null);
                    disk_size += sec_size;
                }
            }
        }
        return disk_size;
    }

    /// 特殊なトラックか
    ///
    /// @return true / false
    /// @param[in] track_num         トラック番号
    /// @param[in] side_num          サイド番号
    /// @param[in] sectors_per_track セクタ/トラック
    /// @param[in] sector_size       セクタサイズ
    public boolean findParticularTrack(int track_num, int side_num, int[] sectors_per_track, int[] sector_size) {
        DiskParticular match = null;
        for (int i = 0; i < ptracks.size(); i++) {
            DiskParticular pt = ptracks.get(i);
            if (pt.getSectorsPerTrack() <= 0) continue;
            if (pt.getSectorNumber() >= 0) continue;
            if (track_num < pt.getTrackNumber() || (pt.getTrackNumber() + pt.getNumberOfTracks()) <= track_num)
                continue;
            if (pt.getSideNumber() >= 0 && pt.getSideNumber() != side_num) continue;
            if (pt.findExclude(track_num, side_num)) continue;

            match = pt;
            sectors_per_track[0] = pt.getSectorsPerTrack();
            if (pt.getSectorSize() > 0) {
                sector_size[0] = pt.getSectorSize();
            }
            break;
        }
        return (match != null);
    }

    /// 特殊なセクタか
    ///
    /// @return true / false
    /// @param[in] track_num   トラック番号
    /// @param[in] side_num    サイド番号
    /// @param[in] sector_num  セクタ番号
    /// @param[in] sector_size セクタサイズ
    /// @param[out] sector_id   C,H,R,Nの入った配列を返す
    public boolean findParticularSector(int track_num, int side_num, int sector_num, int[] sector_size, int[][] sector_id) {
        DiskParticular match = null;
        for (int i = 0; i < psectors.size(); i++) {
            DiskParticular ps = psectors.get(i);
            if (ps.getSectorSize() <= 0) continue;
            if (ps.getSectorsPerTrack() >= 0) continue;
            if (ps.getSectorNumber() >= 0 && ps.getSectorNumber() != sector_num) continue;
            if (ps.getSideNumber() >= 0 && ps.getSideNumber() != side_num) continue;
            if (ps.getTrackNumber() >= 0 && ps.getTrackNumber() != track_num) continue;
            if (ps.findExclude(track_num, side_num)) continue;

            match = ps;
            sector_size[0] = ps.getSectorSize();
            if (sector_id != null) {
                sector_id[0] = ps.getID();
            }
            break;
        }
        return (match != null);
    }

    /// DISK BASICをさがす
    ///
    /// @return 名前
    /// @param[in] type_name タイプ名
    /// @param[in] flags     フラグ
    public DiskParamName findBasicType(String type_name, int flags) {
        DiskParamName match = null;
        for (int i = 0; i < basic_types.size(); i++) {
            DiskParamName item = basic_types.get(i);
            if (item.getName().equals(type_name) && (flags < 0 || item.getFlags() == flags)) {
                match = item;
                break;
            }
        }
        return match;
    }

    /// ディスクパラメータを文字列にフォーマットして返す
    ///
    /// @return 文字列
    public String getDiskDescription() {
        StringBuilder str = new StringBuilder();

        if (!density_name.isEmpty()) {
            str.append(density_name);
            str.append("  ");
        }
        String fmt = "%d";
        fmt += tracks_per_side <= 1 ? "track" : "tracks";
        fmt += " ";
        fmt += "%d";
        fmt += sides_per_disk <= 1 ? "side" : "sides";
        fmt += " ";
        if (variable_secs_per_trk) {
            fmt += "%d-%d";
            fmt += "sectors";
        } else {
            fmt += "%d";
            fmt += sectors_per_track <= 1 ? "sector" : "sectors";
        }
        fmt += " ";
        fmt += "%d";
        fmt += "bytes/sector";
        fmt += " ";
        fmt += "Interleave:";
        fmt += "%d";

        if (variable_secs_per_trk) {
            int min_sectors = ptracks.getMinSectorsPerTrack(sectors_per_track);
            int max_sectors = ptracks.getMaxSectorsPerTrack(sectors_per_track);
            str.append(String.format(fmt, tracks_per_side, sides_per_disk, min_sectors, max_sectors, sector_size, interleave));
        } else {
            str.append(String.format(fmt, tracks_per_side, sides_per_disk, sectors_per_track, sector_size, interleave));
        }

        if (!description.isEmpty()) {
            str.append(" ");
            str.append(description);
        }
        return str.toString();
    }

    public void setDiskTypeName(String str) {
        disk_type_name = str;
    }

    public void setBasicTypes(List<DiskParamName> arr) {
        basic_types = new ArrayList<>(arr);
    }

    public void setReversible(boolean val) {
        reversible = val;
    }

    public void setSidesPerDisk(int val) {
        sides_per_disk = val;
    }

    public void setTracksPerSide(int val) {
        tracks_per_side = val;
    }

    public void setSectorsPerTrack(int val) {
        sectors_per_track = val;
    }

    public void setSectorSize(int val) {
        sector_size = val;
    }

    public void setNumberingSector(int val) {
        numbering_sector = val;
    }

    public void setParamDensity(int val) {
        disk_density = val;
    }

    public void setInterleave(int val) {
        interleave = val;
    }

    public void setTrackNumberBaseOnDisk(int val) {
        track_number_base = val;
    }

    public void setSideNumberBaseOnDisk(int val) {
        side_number_base = val;
    }

    public void setSectorNumberBaseOnDisk(int val) {
        sector_number_base = val;
    }

    public void setVariableSectorsPerTrack(boolean val) {
        variable_secs_per_trk = val;
    }

    public void setSingles(DiskParticulars arr) {
        singles = new DiskParticulars();
        singles.addAll(arr);
    }

    public void setParticularTracks(DiskParticulars arr) {
        ptracks = new DiskParticulars();
        ptracks.addAll(arr);
    }

    public void setParticularSectors(DiskParticulars arr) {
        psectors = new DiskParticulars();
        psectors.addAll(arr);
    }

    public void setDensityName(String str) {
        density_name = str;
    }

    public void setDescription(String str) {
        description = str;
    }

    public void addSingleDensity(DiskParticular val) {
        singles.add(val);
    }

    public void addParticularTrack(DiskParticular val) {
        ptracks.add(val);
    }

    public void addParticularSector(DiskParticular val) {
        psectors.add(val);
    }

    public String getDiskTypeName() {
        return disk_type_name;
    }

    public List<DiskParamName> getBasicTypes() {
        return basic_types;
    }

    public boolean isReversible() {
        return reversible;
    }

    public int getSidesPerDisk() {
        return sides_per_disk;
    }

    public int getTracksPerSide() {
        return tracks_per_side;
    }

    public int getSectorsPerTrack() {
        return sectors_per_track;
    }

    public int getSectorSize() {
        return sector_size;
    }

    public int getNumberingSector() {
        return numbering_sector;
    }

    public int getParamDensity() {
        return disk_density;
    }

    public int getInterleave() {
        return interleave;
    }

    public int getTrackNumberBaseOnDisk() {
        return track_number_base;
    }

    public int getSideNumberBaseOnDisk() {
        return side_number_base;
    }

    public int getSectorNumberBaseOnDisk() {
        return sector_number_base;
    }

    public boolean isVariableSectorsPerTrack() {
        return variable_secs_per_trk;
    }

    public DiskParticulars getSingles() {
        return singles;
    }

    public DiskParticulars getParticularTracks() {
        return ptracks;
    }

    public DiskParticulars getParticularSectors() {
        return psectors;
    }

    public String getDensityName() {
        return density_name;
    }

    public String getDescription() {
        return description;
    }

    public static class DiskTemplates extends TemplatesBase {

        private final List<DiskParam> params;

        /**
         * ディスクパラメータのテンプレートを提供する
         */
        public DiskTemplates() {
            this.params = new ArrayList<>();
        }

        /**
         * XMLファイルから読み込み
         *
         * @param data_path   入力ファイルのあるパス
         * @param locale_name ローケル名
         * @param errmsgs     [out] エラーメッセージ
         * @return true / false
         */
        public boolean load(String data_path, String locale_name, StringBuilder errmsgs) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document doc;
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                doc = builder.parse(new File(data_path + "disk_types.xml"));
            } catch (ParserConfigurationException | SAXException | IOException e) {
                return false;
            }

            // start processing the XML file
            if (!doc.getDocumentElement().getNodeName().equals("DiskTypes")) {
                return false;
            }

            Node item = doc.getDocumentElement().getFirstChild();
            while (item != null) {
                if (item.getNodeName().equals("DiskType")) {
                    DiskParam p = new DiskParam();

                    String type_name = ((Element) item).getAttribute("name");
                    p.setDiskTypeName(type_name);

                    Node itemnode = item.getFirstChild();
                    String[] den_name = {""}, den_name_locale = {""};
                    String[] desc = {""}, desc_locale = {""};
                    while (itemnode != null) {
                        String str = itemnode.getTextContent();
                        if (itemnode.getNodeName().equals("Reversible")) {
                            p.setReversible(Utils.toBool(str));
                        } else if (itemnode.getNodeName().equals("SidesPerDisk")) {
                            p.setSidesPerDisk(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("TracksPerSide")) {
                            p.setTracksPerSide(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("SectorsPerTrack")) {
                            p.setSectorsPerTrack(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("SectorSize")) {
                            p.setSectorSize(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("NumberingSector")) {
                            str = str.toLowerCase();
                            if (str.equals("track")) {
                                p.setNumberingSector(1);
                            }
                        } else if (itemnode.getNodeName().equals("Density")) {
                            p.setParamDensity(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("Interleave")) {
                            p.setInterleave(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("TrackNumberBase")) {
                            p.setTrackNumberBaseOnDisk(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("SideNumberBase")) {
                            p.setSideNumberBaseOnDisk(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("SectorNumberBase")) {
                            p.setSectorNumberBaseOnDisk(Utils.toInt(str));
                        } else if (itemnode.getNodeName().equals("VariableSectorsPerTrack")) {
                            p.setVariableSectorsPerTrack(Utils.toBool(str));
                        } else if (itemnode.getNodeName().equals("DiskBasicTypes")) {
                            List<DiskParamName> basic_types = new ArrayList<>();
                            if (!loadDiskBasicTypes(itemnode, basic_types, errmsgs)) {
                                return false;
                            }
                            p.setBasicTypes(basic_types);
                        } else if (itemnode.getNodeName().equals("SingleDensity")) {
                            DiskParticular s = new DiskParticular();
                            if (!loadSingleDensity(itemnode, s, errmsgs)) {
                                return false;
                            }
                            p.addSingleDensity(s);
                        } else if (itemnode.getNodeName().equals("ParticularTrack")) {
                            DiskParticular d = new DiskParticular();
                            if (!loadParticularTrack(itemnode, d, errmsgs)) {
                                return false;
                            }
                            p.addParticularTrack(d);
                        } else if (itemnode.getNodeName().equals("ParticularSector")) {
                            DiskParticular d = new DiskParticular();
                            if (!loadParticularSector(itemnode, d, errmsgs)) {
                                return false;
                            }
                            p.addParticularSector(d);
                        } else if (itemnode.getNodeName().equals("DensityName")) {
                            loadDescription(itemnode, locale_name, den_name, den_name_locale);
                        } else if (itemnode.getNodeName().equals("Description")) {
                            loadDescription(itemnode, locale_name, desc, desc_locale);
                        }
                        itemnode = itemnode.getNextSibling();
                    }
                    if (!den_name_locale[0].isEmpty()) {
                        den_name = den_name_locale;
                    }
                    p.setDensityName(den_name[0]);
                    if (!desc_locale[0].isEmpty()) {
                        desc[0] = desc_locale[0];
                    }
                    p.setDescription(desc[0]);

                    if (find(type_name) == null) {
                        params.add(p);
                    } else {
                        errmsgs.append("\n");
                        errmsgs.append("Duplicate type name in DiskType : ");
                        errmsgs.append(type_name);
                        return false;
                    }
                }
                item = item.getNextSibling();
            }
            assert !params.isEmpty();
            return true;
        }

        /**
         * DiskBasicTypesエレメントをロード
         *
         * @param node        子ノード
         * @param basic_types [out] ロードしたデータ
         * @param errmsgs     [out] エラーメッセージ
         * @return true
         */
        public boolean loadDiskBasicTypes(Node node, List<DiskParamName> basic_types, StringBuilder errmsgs) {
            Node citemnode = node.getFirstChild();
            while (citemnode.getNodeType() != Node.ELEMENT_NODE) {
                citemnode = citemnode.getNextSibling();
            }
            while (citemnode != null) {
                String str = citemnode.getTextContent();
                if (citemnode.getNodeName().equals("Type")) {
                    DiskParamName p = new DiskParamName();
                    str = str.trim();
                    if (!str.isEmpty()) {
                        p.setName(str);
                    }
                    str = ((Element) citemnode).getAttribute("p");
                    if (str.equalsIgnoreCase("major")) {
                        p.setFlags(1);
                    } else if (str.equalsIgnoreCase("minor")) {
                        p.setFlags(2);
                    }
                    basic_types.add(p);
                }
                citemnode = citemnode.getNextSibling();
            }
            return true;
        }

        /**
         * SingleDensityエレメントをロード
         *
         * @param node    子ノード
         * @param s       [out] ロードしたデータ
         * @param errmsgs [out] エラーメッセージ
         * @return true
         */
        public boolean loadSingleDensity(Node node, DiskParticular s, StringBuilder errmsgs) {
            String str;
            str = ((Element) node).getAttribute("track");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                s.setTrackNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("side");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                s.setSideNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("sector");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                s.setSectorNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("sectors");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                s.setSectorsPerTrack(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("size");
            if (!str.isEmpty()) {
                s.setSectorSize(Utils.toInt(str));
            } else {
                // default size
                s.setSectorSize(128);
            }
            return true;
        }

        /**
         * ParticularTrackエレメントをロード
         *
         * @param node    子ノード
         * @param d       [out] ロードしたデータ
         * @param errmsgs [out] エラーメッセージ
         * @return true
         */
        public boolean loadParticularTrack(Node node, DiskParticular d, StringBuilder errmsgs) {
            String str;
            str = ((Element) node).getAttribute("track");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setTrackNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("tracks");
            if (str.equalsIgnoreCase("ALL")) {
                errmsgs.append("\n");
                errmsgs.append("Cannot set \"ALL\" on tracks attribute in ParticularTrack element.");
                return false;
            }
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setNumberOfTracks(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("side");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setSideNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("sectors");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setSectorsPerTrack(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("size");
            if (!str.isEmpty()) {
                d.setSectorSize(Utils.toInt(str));
            }
            Node cnode = node.getFirstChild();
            while (cnode != null) {
                if (cnode.getNodeName().equals("Exclude")) {
                    TrackParam e = new TrackParam();
                    str = ((Element) cnode).getAttribute("track");
                    if (!str.isEmpty()) {
                        e.setTrackNumber(Utils.toInt(str));
                    }
                    str = ((Element) cnode).getAttribute("side");
                    if (!str.isEmpty()) {
                        e.setSideNumber(Utils.toInt(str));
                    }
                    d.addExclude(e);
                }
                cnode = cnode.getNextSibling();
            }
            return true;
        }

        /**
         * ParticularSectorエレメントをロード
         *
         * @param node    子ノード
         * @param d       [out]      ロードしたデータ
         * @param errmsgs [out] エラーメッセージ
         * @return true
         */
        public boolean loadParticularSector(Node node, DiskParticular d, StringBuilder errmsgs) {
            String str;
            str = ((Element) node).getAttribute("track");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setTrackNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("side");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setSideNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("sector");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setSectorNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("size");
            if (!str.isEmpty()) {
                d.setSectorSize(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("id_h");
            if (!str.isEmpty()) {
                d.setID(1, (short) Utils.toInt(str) | TrackParam.ID_IS_VALID);
            }
            str = ((Element) node).getAttribute("id_r");
            if (!str.isEmpty()) {
                d.setID(2, (short) Utils.toInt(str) | TrackParam.ID_IS_VALID);
            }
            Node cnode = node.getFirstChild();
            while (cnode != null) {
                if (cnode.getNodeName().equals("Exclude")) {
                    TrackParam e = new TrackParam();
                    str = ((Element) cnode).getAttribute("track");
                    if (!str.isEmpty()) {
                        e.setTrackNumber(Utils.toInt(str));
                    }
                    str = ((Element) cnode).getAttribute("side");
                    if (!str.isEmpty()) {
                        e.setSideNumber(Utils.toInt(str));
                    }
                    d.addExclude(e);
                }
                cnode = cnode.getNextSibling();
            }
            return true;
        }

        /**
         * タイプ名に一致するテンプレートの番号を返す
         *
         * @param n_type_name タイプ名
         * @return ディスクテンプレートの位置 / ないとき-1
         */
        public int indexOf(String n_type_name) {
            int match = -1;
            for (int i = 0; i < params.size(); i++) {
                DiskParam item = params.get(i);
                if (n_type_name.equals(item.getDiskTypeName())) {
                    match = i;
                    break;
                }
            }
            return match;
        }

        public DiskParam find(DiskParam n_param) {
            DiskParam match = null;
            for (int i = 0; i < params.size(); i++) {
                DiskParam item = params.get(i);
                if (item.matchExceptName(n_param)) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        public DiskParam find(String n_type_name) {
            DiskParam match = null;
            for (int i = 0; i < params.size(); i++) {
                DiskParam item = params.get(i);
                if (n_type_name.equals(item.getDiskTypeName())) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        /**
         * パラメータに一致するテンプレートを返す
         *
         * @param n_sides_per_disk     サイド数
         * @param n_tracks_per_side    トラック数
         * @param n_sectors_per_track  セクタ数
         * @param n_sector_size        セクタサイズ
         * @param n_interleave         インターリーブ
         * @param n_track_number_base  開始トラック番号
         * @param n_side_number_base   開始サイド番号
         * @param n_sector_number_base 開始セクタ番号
         * @param n_numbering_sector   セクタ採番方法
         * @param n_singles            単密度情報
         * @param n_ptracks            特殊トラック
         * @return ディスクパラメータ or null
         */
        public DiskParam findStrict(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size,
                                    int n_interleave, int n_track_number_base, int n_side_number_base, int n_sector_number_base,
                                    int n_numbering_sector, DiskParticulars n_singles, DiskParticulars n_ptracks) {
            DiskParam match_item = null;
            boolean m = false;
            for (int i = 0; i < params.size(); i++) {
                DiskParam item = params.get(i);
                m = item.match(n_sides_per_disk, n_tracks_per_side, n_sectors_per_track, n_sector_size, n_interleave,
                        n_track_number_base, n_side_number_base, n_sector_number_base, n_numbering_sector, n_singles, n_ptracks);
                if (m) {
                    match_item = item;
                    break;
                }
            }
            return match_item;
        }

        /**
         * パラメータに一致するあるいは近い物のテンプレートを返す
         *
         * @param n_sides_per_disk     サイド数
         * @param n_tracks_per_side    トラック数
         * @param n_sectors_per_track  セクタ数
         * @param n_sector_size        セクタサイズ
         * @param n_interleave         インターリーブ
         * @param n_track_number_base  開始トラック番号
         * @param n_side_number_base   開始サイド番号
         * @param n_sector_number_base 開始セクタ番号
         * @param n_numbering_sector   セクタ採番方法
         * @param n_singles            単密度情報
         * @param n_ptracks            特殊トラック
         * @return ディスクパラメータ or null
         */
        public DiskParam find(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size,
                              int n_interleave, int n_track_number_base, int n_side_number_base, int n_sector_number_base,
                              int n_numbering_sector, DiskParticulars n_singles, DiskParticulars n_ptracks) {
            DiskParam match_item = findStrict(n_sides_per_disk, n_tracks_per_side, n_sectors_per_track, n_sector_size,
                    n_interleave, n_track_number_base, n_side_number_base, n_sector_number_base, n_numbering_sector, n_singles, n_ptracks);
            if (match_item == null) {
                boolean[] last = {false};
                boolean m = false;
                for (int num = 0; !last[0] && !m; num++) {
                    // パラメータが一致しないときは、引数に近いパラメータ
                    for (int i = 0; i < params.size(); i++) {
                        DiskParam item = params.get(i);
                        m = item.matchNear(num, n_sides_per_disk, n_tracks_per_side, n_sectors_per_track, n_sector_size,
                                n_interleave, n_numbering_sector, n_singles, last);
                        if (last[0]) {
                            break;
                        }
                        if (m) {
                            match_item = item;
                            break;
                        }
                    }
                }
            }
            return match_item;
        }

        /**
         * パラメータに一致するテンプレートのリストを返す
         *
         * @param n_sides_per_disk    サイド数
         * @param n_tracks_per_side   トラック数
         * @param n_sectors_per_track セクタ数
         * @param n_sector_size       セクタサイズ
         * @param n_list              [out] 候補リスト
         * @param n_separator         リストの最初にセパレータ(null)を追加するか
         * @return リスト内のアイテム数
         */
        public int find(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size,
                        List<DiskParam> n_list, boolean n_separator) {
            for (int i = 0; i < params.size(); i++) {
                DiskParam item = params.get(i);
                if (item.match(n_sides_per_disk, n_tracks_per_side, n_sectors_per_track, n_sector_size)) {
                    // 重複してなければ追加
                    if (!n_list.contains(item)) {
                        if (n_separator) {
                            // 最初の候補の前にセパレータを追加
                            n_list.add(null);
                            n_separator = false;
                        }
                        n_list.add(item);
                    }
                }
            }
            return n_list.size();
        }

        public DiskParam get(int index) {
            return params.get(index);
        }

        public int size() {
            return params.size();
        }
    }
}
