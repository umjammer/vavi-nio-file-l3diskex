package l3diskex.diskimg;

import java.util.ArrayList;
import java.util.List;


public class DiskParam {

    public static DiskTemplates gDiskTemplates = new DiskTemplates();

    public static class SectorParam {
        protected int track_num;
        protected int side_num;
        protected int sector_num;
        protected int sector_size;

        public SectorParam() {
            this.track_num = -1;
            this.side_num = -1;
            this.sector_num = -1;
            this.sector_size = -1;
        }

        public SectorParam(int n_track_num, int n_side_num, int n_sector_num, int n_sector_size) {
            this.track_num = n_track_num;
            this.side_num = n_side_num;
            this.sector_num = n_sector_num;
            this.sector_size = n_sector_size;
        }

        public boolean equals(SectorParam dst) {
            return (track_num == dst.track_num
                    && side_num == dst.side_num
                    && sector_num == dst.sector_num
                    && sector_size == dst.sector_size);
        }

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

        public TrackParam() {
            super();
            this.num_of_tracks = 1;
            this.sectors_per_track = -1;
            this.id = new int[4];
        }

        public TrackParam(int n_track_num, int n_side_num, int n_sector_num, int n_num_of_tracks, int n_sectors_per_track, int n_sector_size) {
            super(n_track_num, n_side_num, n_sector_num, n_sector_size);
            this.num_of_tracks = n_num_of_tracks;
            this.sectors_per_track = n_sectors_per_track;
            this.id = new int[4];
        }

        public boolean equals(TrackParam dst) {
            return (track_num == dst.track_num
                    && side_num == dst.side_num
                    && sector_num == dst.sector_num
                    && num_of_tracks == dst.num_of_tracks
                    && (sectors_per_track < 0 || dst.sectors_per_track < 0 || sectors_per_track == dst.sectors_per_track)
                    && sector_size == dst.sector_size);
        }

        public void setID(int idx, int val) {
            if (idx >= 0 && idx < 4) {
                id[idx] = val;
            }
        }

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
        private List<TrackParam> excludes;

        public DiskParticular() {
            super();
            this.excludes = new ArrayList<>();
        }

        public DiskParticular(int n_track_num, int n_side_num, int n_sector_num, int n_num_of_tracks, int n_sectors_per_track, int n_sector_size) {
            super(n_track_num, n_side_num, n_sector_num, n_num_of_tracks, n_sectors_per_track, n_sector_size);
            this.excludes = new ArrayList<>();
        }

        public void addExclude(TrackParam n_param) {
            excludes.add(n_param);
        }

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

        public static void uniqueTracks(int tracks, int sides, boolean both_sides, DiskParticulars arr) {
            int count = arr.size();
            if (count <= 1) return;

            DiskParticulars newarr = new DiskParticulars();
            DiskParticular prev_sd;

            prev_sd = arr.get(0);
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
            if (all_sides && count >= tracks) {
                DiskParticular sd = arr.get(0);
                newarr.add(new DiskParticular(-1, -1, -1, sd.getNumberOfTracks(), sd.getSectorsPerTrack(), sd.getSectorSize()));
                arr.clear();
                arr.addAll(newarr);
            }
        }
    }

    public static class DiskParticulars extends ArrayList<DiskParticular> {
        public DiskParticulars() {
            super();
        }

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

        public NumSectorsParam() {
            this.start_track_num = 0;
            this.num_of_tracks = 0;
            this.sectors_per_track = 1;
        }

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

    public static class NumSectorsParams extends ArrayList<NumSectorsParam> {
        public NumSectorsParams() {
            super();
        }

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

    public boolean match(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size) {
        boolean match = (sides_per_disk == n_sides_per_disk)
                && (tracks_per_side == n_tracks_per_side)
                && (sectors_per_track == n_sectors_per_track)
                && (sector_size == n_sector_size);
        return match;
    }

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

    public boolean matchNear(int num, int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track,
                             int n_sector_size, int n_interleave, int n_numbering_sector,
                             DiskParticulars n_singles, boolean[] last) {
        boolean match = false;
        switch (num) {
            case 0:
                match = (sides_per_disk == n_sides_per_disk)
                        && (tracks_per_side == n_tracks_per_side)
                        && (sectors_per_track == n_sectors_per_track)
                        && (sector_size == n_sector_size)
                        && (interleave == n_interleave)
                        && (numbering_sector == n_numbering_sector)
                        && (singles.equals(n_singles));
                break;
            case 1:
                match = (sides_per_disk == n_sides_per_disk)
                        && (tracks_per_side == n_tracks_per_side)
                        && (sectors_per_track == n_sectors_per_track)
                        && (sector_size == n_sector_size)
                        && (numbering_sector == n_numbering_sector)
                        && (singles.equals(n_singles));
                break;
            case 2:
                match = (sides_per_disk == n_sides_per_disk)
                        && (sectors_per_track == n_sectors_per_track)
                        && (sector_size == n_sector_size)
                        && (numbering_sector == n_numbering_sector)
                        && (singles.equals(n_singles))
                        && (interleave == n_interleave)
                        && ((n_tracks_per_side - 5) <= tracks_per_side && tracks_per_side <= n_tracks_per_side);
                break;
            case 3:
                match = (sides_per_disk == n_sides_per_disk)
                        && (sectors_per_track == n_sectors_per_track)
                        && (sector_size == n_sector_size)
                        && (numbering_sector == n_numbering_sector)
                        && (singles.equals(n_singles))
                        && ((n_tracks_per_side - 5) <= tracks_per_side && tracks_per_side <= n_tracks_per_side);
                break;
            case 4:
                match = (sides_per_disk == n_sides_per_disk)
                        && (sectors_per_track == n_sectors_per_track)
                        && (sector_size == n_sector_size)
                        && (numbering_sector == n_numbering_sector)
                        && ((n_tracks_per_side - 5) <= tracks_per_side && tracks_per_side <= n_tracks_per_side);
                break;
            case 5:
                match = (sides_per_disk == n_sides_per_disk)
                        && (sector_size == n_sector_size)
                        && (numbering_sector == n_numbering_sector)
                        && ((n_tracks_per_side - 5) <= tracks_per_side && tracks_per_side <= n_tracks_per_side)
                        && (sectors_per_track <= n_sectors_per_track);
                break;
            default:
                last[0] = true;
                break;
        }
        return match;
    }

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

    public int calcDiskSize() {
        int disk_size = 0;
        int trk = getTrackNumberBaseOnDisk();
        int trks = getTracksPerSide() + trk;
        for (; trk < trks; trk++) {
            for (int sid = 0; sid < getSidesPerDisk(); sid++) {
                int sec_nums = getSectorsPerTrack();
                int sec_size = getSectorSize();
                findParticularTrack(trk, sid, new int[]{sec_nums}, new int[]{sec_size});
                findSingleDensity(trk, sid, new int[]{sec_nums}, new int[]{sec_size});

                for (int sec = 0; sec < sec_nums; sec++) {
                    findParticularSector(trk, sid, sec, new int[]{sec_size}, null);
                    disk_size += sec_size;
                }
            }
        }
        return disk_size;
    }

    public boolean findParticularTrack(int track_num, int side_num, int[] sectors_per_track, int[] sector_size) {
        DiskParticular match = null;
        for (int i = 0; i < ptracks.size(); i++) {
            DiskParticular pt = ptracks.get(i);
            if (pt.getSectorsPerTrack() <= 0) continue;
            if (pt.getSectorNumber() >= 0) continue;
            if (track_num < pt.getTrackNumber() || (pt.getTrackNumber() + pt.getNumberOfTracks()) <= track_num) continue;
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

    public static class DiskTemplates {
        private final List<DiskParam> params;

        public DiskTemplates() {
            this.params = new ArrayList<>();
        }

        public boolean load(String data_path, String locale_name, StringBuilder errmsgs) {
            return false;
        }

        public boolean loadDiskBasicTypes(Object node, List<DiskParamName> basic_types, StringBuilder errmsgs) {
            return true;
        }

        public boolean loadSingleDensity(Object node, DiskParticular s, StringBuilder errmsgs) {
            return true;
        }

        public boolean loadParticularTrack(Object node, DiskParticular d, StringBuilder errmsgs) {
            return true;
        }

        public boolean loadParticularSector(Object node, DiskParticular d, StringBuilder errmsgs) {
            return true;
        }

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

        public DiskParam findStrict(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size,
                                    int n_interleave, int n_track_number_base, int n_side_number_base, int n_sector_number_base,
                                    int n_numbering_sector, DiskParam.DiskParticulars n_singles, DiskParam.DiskParticulars n_ptracks) {
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

        public DiskParam find(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size,
                              int n_interleave, int n_track_number_base, int n_side_number_base, int n_sector_number_base,
                              int n_numbering_sector, DiskParticulars n_singles, DiskParticulars n_ptracks) {
            DiskParam match_item = findStrict(n_sides_per_disk, n_tracks_per_side, n_sectors_per_track, n_sector_size,
                    n_interleave, n_track_number_base, n_side_number_base, n_sector_number_base, n_numbering_sector, n_singles, n_ptracks);
            if (match_item == null) {
                boolean[] last = {false};
                boolean m = false;
                for (int num = 0; !last[0] && !m; num++) {
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

        public int find(int n_sides_per_disk, int n_tracks_per_side, int n_sectors_per_track, int n_sector_size,
                        List<DiskParam> n_list, boolean n_separator) {
            for (int i = 0; i < params.size(); i++) {
                DiskParam item = params.get(i);
                if (item.match(n_sides_per_disk, n_tracks_per_side, n_sectors_per_track, n_sector_size)) {
                    if (!n_list.contains(item)) {
                        if (n_separator) {
                            n_list.add(null);
                            n_separator = false;
                        }
                        n_list.add(item);
                    }
                }
            }
            return n_list.size();
        }

        public DiskParam itemPtr(int index) {
            return params.get(index);
        }

        public DiskParam item(int index) {
            return params.get(index);
        }

        public int count() {
            return params.size();
        }
    }
}
