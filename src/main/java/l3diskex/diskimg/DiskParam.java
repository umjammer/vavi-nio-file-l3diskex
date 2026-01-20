package l3diskex.diskimg;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import l3diskex.Common;
import l3diskex.Parambase.TemplatesBase;
import l3diskex.Utils;
import org.xml.sax.SAXException;


public class DiskParam {

    private static final Logger logger = System.getLogger(DiskParam.class.getName());

    public static DiskTemplates diskTemplates = new DiskTemplates();

    /** Holds track, side, and sector numbers */
    public static class SectorParam {

        protected int trackNum;
        protected int sideNum;
        protected int sectorNum;
        protected int sectorSize;

        /** Holds track, side, and sector numbers */
        public SectorParam() {
            this.trackNum = -1;
            this.sideNum = -1;
            this.sectorNum = -1;
            this.sectorSize = -1;
        }

        /**
         * Holds numbers
         *
         * @param trackNum   Track number
         * @param sideNum    Side number
         * @param sectorNum  Sector number
         * @param sectorSize Sector size
         */
        public SectorParam(int trackNum, int sideNum, int sectorNum, int sectorSize) {
            this.trackNum = trackNum;
            this.sideNum = sideNum;
            this.sectorNum = sectorNum;
            this.sectorSize = sectorSize;
        }

        /** Comparison */
        public boolean equals(SectorParam dst) {
            return (trackNum == dst.trackNum
                    && sideNum == dst.sideNum
                    && sectorNum == dst.sectorNum
                    && sectorSize == dst.sectorSize);
        }

        /**
         * Whether the specified track and side are special sectors (single density, etc.)
         *
         * @param trackNum   Track number
         * @param sideNum    Side number
         * @param sectorNum  Sector number
         * @param sectorSize Sector size; if < 0, it is excluded from the condition
         */
        public boolean match(int trackNum, int sideNum, int sectorNum, int sectorSize) {
            return ((this.trackNum < 0 || (this.trackNum == trackNum))
                    && (this.sideNum < 0 || (this.sideNum == sideNum))
                    && (this.sectorNum < 0 || (this.sectorNum == sectorNum))
                    && (this.sectorSize < 0 || sectorSize < 0 || (this.sectorSize == sectorSize)));
        }

        public void setTrackNumber(int val) {
            trackNum = val;
        }

        public void setSideNumber(int val) {
            sideNum = val;
        }

        public void setSectorNumber(int val) {
            sectorNum = val;
        }

        public int getTrackNumber() {
            return trackNum;
        }

        public int getSideNumber() {
            return sideNum;
        }

        public int getSectorNumber() {
            return sectorNum;
        }
    }

    /** Holds track, side, and sector numbers */
    public static class TrackParam extends SectorParam {

        public static final int ID_IS_VALID = 0x8000;

        protected int numOfTracks;
        protected int sectorsPerTrack;
        protected int[] id;

        /**
         * Holds special track and sector information such as single density
         */
        public TrackParam() {
            this.numOfTracks = 1;
            this.sectorsPerTrack = -1;
            this.id = new int[4];
        }

        /**
         * Register special tracks and sectors
         *
         * @param trackNum        Track number (-1: ALL)
         * @param sideNum         Side number (-1: ALL)
         * @param sectorNum       Sector number (only when specifying a special sector)
         * @param numOfTracks     Number of tracks (only when specifying special tracks)
         * @param sectorsPerTrack Number of sectors (only when specifying special tracks)
         * @param sectorSize      Sector size
         */
        public TrackParam(int trackNum, int sideNum, int sectorNum, int numOfTracks, int sectorsPerTrack, int sectorSize) {
            super(trackNum, sideNum, sectorNum, sectorSize);
            this.numOfTracks = numOfTracks;
            this.sectorsPerTrack = sectorsPerTrack;
            this.id = new int[4];
        }

        /** Comparison */
        public boolean equals(TrackParam dst) {
            return (trackNum == dst.trackNum
                    && sideNum == dst.sideNum
                    && sectorNum == dst.sectorNum
                    && numOfTracks == dst.numOfTracks
                    && (sectorsPerTrack < 0 || dst.sectorsPerTrack < 0 || sectorsPerTrack == dst.sectorsPerTrack)
                    && sectorSize == dst.sectorSize);
        }

        /** Set ID */
        public void setID(int idx, int val) {
            if (idx >= 0 && idx < 4) {
                id[idx] = val;
            }
        }

        /** Return ID */
        public int getID(int idx) {
            if (idx >= 0 && idx < 4) {
                return id[idx];
            } else {
                return 0;
            }
        }

        /** Set number of tracks */
        public void setNumberOfTracks(int val) {
            numOfTracks = val;
        }

        /** Set number of sectors */
        public void setSectorsPerTrack(int val) {
            sectorsPerTrack = val;
        }

        /** Set sector size */
        public void setSectorSize(int val) {
            sectorSize = val;
        }

        /** Return number of tracks */
        public int getNumberOfTracks() {
            return numOfTracks;
        }

        /** Return number of sectors */
        public int getSectorsPerTrack() {
            return sectorsPerTrack;
        }

        /** Return sector size */
        public int getSectorSize() {
            return sectorSize;
        }

        /** Return ID */
        public int[] getID() {
            return id;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", TrackParam.class.getSimpleName() + "[", "]")
                    .add("numOfTracks=" + numOfTracks)
                    .add("sectorsPerTrack=" + sectorsPerTrack)
                    .add("id=" + Arrays.toString(id))
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            return (this.numOfTracks == ((TrackParam) o).numOfTracks) &&
                    (this.sectorsPerTrack == ((TrackParam) o).sectorsPerTrack) &&
                    Arrays.equals(id, ((TrackParam) o).id);
        }
    }

    /** Holds special track and sector information such as single density */
    public static class DiskParticular extends TrackParam {

        private final List<TrackParam> excludes;

        /** Holds special track and sector information such as single density */
        public DiskParticular() {
            this.excludes = new ArrayList<>();
        }

        /**
         * Register special tracks and sectors
         *
         * @param trackNum        Track number (-1: ALL)
         * @param sideNum         Side number (-1: ALL)
         * @param sectorNum       Sector number (only when specifying a special sector)
         * @param numOfTracks     Number of tracks (only when specifying special tracks)
         * @param sectorsPerTrack Number of sectors (only when specifying special tracks)
         * @param sectorSize      Sector size
         */
        public DiskParticular(int trackNum, int sideNum, int sectorNum, int numOfTracks, int sectorsPerTrack, int sectorSize) {
            super(trackNum, sideNum, sectorNum, numOfTracks, sectorsPerTrack, sectorSize);
            this.excludes = new ArrayList<>();
        }

        /**
         * Add tracks to be excluded to the list
         *
         * @param param Parameters
         */
        public void addExclude(TrackParam param) {
            excludes.add(param);
        }

        /**
         * Whether the track is included in the exclusion list
         *
         * @param trackNum Track number
         * @param sideNum  Side number
         */
        public boolean findExclude(int trackNum, int sideNum) {
            TrackParam match = null;
            for (TrackParam item : excludes) {
                if (trackNum != item.getTrackNumber()) continue;
                if (sideNum != item.getSideNumber()) continue;
                match = item;
                break;
            }
            return match != null;
        }

        /**
         * Group items with the same sector
         *
         * @param sectors Number of sectors
         * @param result  [in,out] List
         */
        public static void uniqueSectors(int sectors, List<DiskParticular> result) {
            int count = result.size();
            if (count <= 1) return;

            if (sectors == count) {
                List<DiskParticular> newSd = new ArrayList<>();
                DiskParticular sd = result.getFirst();
                newSd.add(new DiskParticular(sd.getTrackNumber(), sd.getSideNumber(), -1, sd.getNumberOfTracks(), sd.getSectorsPerTrack(), sd.getSectorSize()));
                result.clear();
                result.addAll(newSd);
            }
        }

        /**
         * Group items with the same track or side
         *
         * @param tracks    Number of tracks
         * @param sides     Number of sides
         * @param bothSides Whether both sides are used
         * @param result    [in,out] List
         */
        public static void uniqueTracks(int tracks, int sides, boolean bothSides, List<DiskParticular> result) {
            int count = result.size();
            if (count <= 1) return;

            List<DiskParticular> newSd = new ArrayList<>();
            DiskParticular prevSd;

            prevSd = result.getFirst();
            // If parameters (single density) are the same for all sides on the same track number, group them together
            int sideCount = 1;
            boolean allSides = true;
            for (int idx = 1; idx <= count; idx++) {
                DiskParticular sd = idx < count ? result.get(idx) : null;
                if (prevSd.getSectorNumber() >= 0) {
                    newSd.add(prevSd);
                    allSides = false;
                    sideCount = 0;
                } else if (sd == null || prevSd.getTrackNumber() != sd.getTrackNumber()) {
                    if (sideCount >= sides || bothSides) {
                        newSd.add(new DiskParticular(prevSd.getTrackNumber(), -1, -1, prevSd.getNumberOfTracks(), prevSd.getSectorsPerTrack(), prevSd.getSectorSize()));
                    } else {
                        newSd.add(new DiskParticular(prevSd.getTrackNumber(), prevSd.getSideNumber(), -1, prevSd.getNumberOfTracks(), prevSd.getSectorsPerTrack(), prevSd.getSectorSize()));
                        allSides = false;
                    }
                    sideCount = 0;
                }
                sideCount++;
                prevSd = sd;
            }
            result.clear();
            result.addAll(newSd);

            count = result.size();
            newSd.clear();
            // If parameters (single density) are the same for all tracks, group them together
            if (allSides && count >= tracks) {
                DiskParticular sd = result.getFirst();
                newSd.add(new DiskParticular(-1, -1, -1, sd.getNumberOfTracks(), sd.getSectorsPerTrack(), sd.getSectorSize()));
                result.clear();
                result.addAll(newSd);
            }
        }

        //
        // DiskParticulars
        //

        /** Returns the minimum value in the sector/track list */
        public static int getMinSectorsPerTrack(List<DiskParticular> list, int defaultNumber) {
            int val = 0xffff;
            for (DiskParticular diskParticular : list) {
                if (diskParticular.getSectorsPerTrack() < val) {
                    val = diskParticular.getSectorsPerTrack();
                }
            }
            if (defaultNumber < val) {
                val = defaultNumber;
            }
            return val;
        }

        /** Returns the maximum value in the sector/track list */
        public static int getMaxSectorsPerTrack(List<DiskParticular> list, int defaultNumber) {
            int val = 0;
            for (DiskParticular diskParticular : list) {
                if (diskParticular.getSectorsPerTrack() > val) {
                    val = diskParticular.getSectorsPerTrack();
                }
            }
            if (defaultNumber > val) {
                val = defaultNumber;
            }
            return val;
        }

        /** Whether all values match */
        public static boolean equals(List<DiskParticular> list, List<DiskParticular> dst) {
            boolean match = true;
            if (list.size() != dst.size()) {
                match = false;
            } else {
                for (DiskParticular ds : dst) {
                    boolean sm = false;
                    for (DiskParticular ss : list) {
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

        @Override
        public String toString() {
            return new StringJoiner(", ", DiskParticular.class.getSimpleName() + "[", "]")
                    .add("excludes=" + excludes)
                    .toString();
        }
    }

    /** Holds the number of sectors for each track */
    public static class NumSectorsParam {

        protected int startTrackNum;
        protected int numOfTracks;
        protected int sectorsPerTrack;

        /**
         * // Holds the number of sectors for each track
         */
        public NumSectorsParam() {
            this.startTrackNum = 0;
            this.numOfTracks = 0;
            this.sectorsPerTrack = 1;
        }

        /**
         * @param startTrackNum   Start track number
         * @param numOfTracks     Number of tracks
         * @param sectorsPerTrack Number of sectors per track
         */
        public NumSectorsParam(int startTrackNum, int numOfTracks, int sectorsPerTrack) {
            this.startTrackNum = startTrackNum;
            this.numOfTracks = numOfTracks;
            this.sectorsPerTrack = sectorsPerTrack;
        }

        /** Set start track number */
        public void setStartTrackNumber(int val) {
            startTrackNum = val;
        }

        /** Set number of tracks */
        public void setNumberOfTracks(int val) {
            numOfTracks = val;
        }

        /** Set sectors per track */
        public void setSectorsPerTrack(int val) {
            sectorsPerTrack = val;
        }

        /** Return start track number */
        public int getStartTrackNumber() {
            return startTrackNum;
        }

        /** Return number of tracks */
        public int getNumberOfTracks() {
            return numOfTracks;
        }

        /** Return sectors per track */
        public int getSectorsPerTrack() {
            return sectorsPerTrack;
        }

        //
        // NumSectorsParams
        //

        /** Returns the minimum value in the sector/track list */
        public static int getMinSectorOfTracks(List<NumSectorsParam> list) {
            int val = 0xffff;
            for (NumSectorsParam numSectorsParam : list) {
                if (numSectorsParam.getSectorsPerTrack() < val) {
                    val = numSectorsParam.getSectorsPerTrack();
                }
            }
            return val;
        }

        /** Returns the maximum number of sectors in the list */
        public static int getMaxSectorOfTracks(List<NumSectorsParam> list) {
            int val = 0;
            for (NumSectorsParam numSectorsParam : list) {
                if (numSectorsParam.getSectorsPerTrack() > val) {
                    val = numSectorsParam.getSectorsPerTrack();
                }
            }
            return val;
        }
    }

    /** Save DISK BASIC name list */
    public static class DiskParamName {

        private String name;
        private int flags;

        public DiskParamName() {
            this.flags = 0;
        }

        /** Set name */
        public void setName(String val) {
            name = val;
        }

        /** Return name */
        public String getName() {
            return name;
        }

        /** Set flags */
        public void setFlags(int val) {
            flags = val;
        }

        /** Return flags */
        public int getFlags() {
            return flags;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", DiskParamName.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .add("flags=" + flags)
                    .toString();
        }
    }

    /**
     * Interleave/Sector Skew
     *
     * If there is no map, calculate from interval.
     */
    public static class SectorInterleave {

        /** True when using a specific map */
        private boolean hasMap;
        /** Sector number after conversion */
        private List<Integer> secs;

        public SectorInterleave() {
            this.hasMap = false;
            this.secs = new ArrayList<>();
            this.secs.add(0);
        }

        /** Return interleave interval */
        public int get() {
            return secs.getFirst();
        }

        /** Return interleave map */
        public int get(int idx) {
            if (idx < secs.size()) {
                return secs.get(idx);
            } else {
                return 0;
            }
        }

        /** Set interleave interval */
        public void set(int val) {
            hasMap = false;
            secs.set(0, val);
        }

        /** Set interleave map */
        public void set(List<Integer> val) {
            hasMap = true;
            secs = new ArrayList<>(val);
        }

        /** Whether it has a specific map */
        public boolean hasMap() {
            return hasMap;
        }
    }

    /** Disk type name "2D", "2HD", etc. */
    protected String diskTypeName;
    /** BASIC type (also used for matching with DiskBasicParam) */
    protected List<DiskParamName> basicTypes;
    /** Reversible with AB sides (e.g. 3-inch FD for L3) */
    protected boolean reversible;
    /** Number of sides */
    protected int sidesPerDisk;
    /** Number of tracks */
    protected int tracksPerSide;
    /** Number of sectors */
    protected int sectorsPerTrack;
    /** Sector size */
    protected int sectorSize;
    /** Sector numbering method (0: per side, 1: per track) */
    protected int numberingSector;
    /** 0x00: 2D, 0x10: 2DD, 0x20: 2HD */
    protected int diskDensity;
    /** Sector interval */
    protected int interleave;
    /** Start track number */
    protected int trackNumberBase;
    /** Start side number */
    protected int sideNumberBase;
    /** Start sector number */
    protected int sectorNumberBase;
    /** Number of sectors differs per track */
    protected boolean variableSecsPerTrack;
    /** Tracks to be single density */
    protected List<DiskParticular> singles;
    /** Define special tracks */
    protected List<DiskParticular> pTracks;
    /** Define special sectors */
    protected List<DiskParticular> pSectors;
    /** Density information (for description) */
    protected String densityName;
    /** Description */
    protected String description;

    public DiskParam() {
        this.clearDiskParam();
    }

    public DiskParam(DiskParam src) {
        this.setDiskParam(src);
    }

    public void setDiskParam(DiskParam src) {
        this.diskTypeName = src.diskTypeName;
        this.basicTypes = new ArrayList<>(src.basicTypes);
        this.reversible = src.reversible;
        this.sidesPerDisk = src.sidesPerDisk;
        this.tracksPerSide = src.tracksPerSide;
        this.sectorsPerTrack = src.sectorsPerTrack;
        this.sectorSize = src.sectorSize;
if (sectorSize == 0) {
 new Exception("sectorSize: " + sectorSize).printStackTrace();
}
        this.numberingSector = src.numberingSector;
        this.diskDensity = src.diskDensity;
        this.interleave = src.interleave;
        this.trackNumberBase = src.trackNumberBase;
        this.sideNumberBase = src.sideNumberBase;
        this.sectorNumberBase = src.sectorNumberBase;
        this.variableSecsPerTrack = src.variableSecsPerTrack;
        this.singles = new ArrayList<>();
        this.singles.addAll(src.singles);
        this.pTracks = new ArrayList<>();
        this.pTracks.addAll(src.pTracks);
        this.pSectors = new ArrayList<>();
        this.pSectors.addAll(src.pSectors);
        this.densityName = src.densityName;
        this.description = src.description;
    }

    /**
     * Set all parameters
     *
     * @param typeName             Disk type name "2D", "2HD", etc.
     * @param basicTypes           BASIC types (also used for matching with DiskBasicParam)
     * @param reversible           Reversible with AB sides (e.g. 3-inch FD for L3)
     * @param sidesPerDisk         Number of sides
     * @param tracksPerSide        Number of tracks
     * @param sectorsPerTrack      Number of sectors
     * @param sector_size          Sector size
     * @param numberingSector      Sector numbering method (0: per side, 1: per track)
     * @param disk_density         0x00:2D 0x10:2DD 0x20:2HD
     * @param interleave           Sector interval
     * @param trackNumberBase      Start track number
     * @param sideNumberBase       Start side number
     * @param sectorNumberBase     Start sector number
     * @param variableSecsPerTrack Whether number of sectors differs per track
     * @param singles              Tracks to be single density
     * @param pTracks              Define special tracks if number of sectors differs per track
     * @param pSectors             Define special sectors
     * @param densityName          Density information (for description)
     * @param desc                 Description
     */
    public void setDiskParam(String typeName, List<DiskParamName> basicTypes, boolean reversible,
                             int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sector_size,
                             int numberingSector, int disk_density, int interleave, int trackNumberBase,
                             int sideNumberBase, int sectorNumberBase, boolean variableSecsPerTrack,
                             List<DiskParticular> singles, List<DiskParticular> pTracks, List<DiskParticular> pSectors,
                             String densityName, String desc) {
        this.diskTypeName = typeName;
        this.basicTypes = new ArrayList<>(basicTypes);
        this.reversible = reversible;
        this.sidesPerDisk = sidesPerDisk;
        this.tracksPerSide = tracksPerSide;
        this.sectorsPerTrack = sectorsPerTrack;
        this.sectorSize = sector_size;
        this.numberingSector = numberingSector;
        this.diskDensity = disk_density;
        this.interleave = interleave;
        this.trackNumberBase = trackNumberBase;
        this.sideNumberBase = sideNumberBase;
        this.sectorNumberBase = sectorNumberBase;
        this.variableSecsPerTrack = variableSecsPerTrack;
        this.singles = new ArrayList<>();
        this.singles.addAll(singles);
        this.pTracks = new ArrayList<>();
        this.pTracks.addAll(pTracks);
        this.pSectors = new ArrayList<>();
        this.pSectors.addAll(pSectors);
        this.densityName = densityName;
        this.description = desc;
    }

    /**
     * Set only major parameters
     *
     * @param sidesPerDisk    Number of sides
     * @param tracksPerSide   Number of tracks
     * @param sectorsPerTrack Number of sectors
     * @param sectorSize      Sector size
     * @param diskDensity     0x00: 2D, 0x10: 2DD, 0x20: 2HD
     * @param interleave      Sector interval
     * @param singles         Tracks to be single density
     * @param pTracks         Define special tracks if number of sectors differs per track
     */
    public void setDiskParam(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack,
                             int sectorSize, int diskDensity, int interleave,
                             List<DiskParticular> singles, List<DiskParticular> pTracks) {
        this.sidesPerDisk = sidesPerDisk;
        this.tracksPerSide = tracksPerSide;
        this.sectorsPerTrack = sectorsPerTrack;
        this.sectorSize = sectorSize;
        this.diskDensity = diskDensity;
        this.interleave = interleave;
        this.singles = new ArrayList<>();
        this.singles.addAll(singles);
        this.pTracks = new ArrayList<>();
        this.pTracks.addAll(pTracks);
    }

    /** Initialization */
    public void clearDiskParam() {
        this.diskTypeName = "";
        this.basicTypes = new ArrayList<>();
        this.reversible = false;
        this.sidesPerDisk = 0;
        this.tracksPerSide = 0;
        this.sectorsPerTrack = 0;
        this.sectorSize = 0;
        this.numberingSector = 0;
        this.diskDensity = 0;
        this.interleave = 1;
        this.trackNumberBase = 0;
        this.sideNumberBase = 0;
        this.sectorNumberBase = 1;
        this.variableSecsPerTrack = false;
        this.singles = new ArrayList<>();
        this.pTracks = new ArrayList<>();
        this.pSectors = new ArrayList<>();
        this.densityName = "";
        this.description = "";
    }

    /**
     * Whether there is a match with the specified parameters
     *
     * @param sidesPerDisk     Side/Disk
     * @param tracksPerSide    Track/Side
     * @param sectorsPerTrack  Sector/Track
     * @param sectorSize       Sector size
     * @param interleave       Interleave
     * @param trackNumberBase  Start track number
     * @param sideNumberBase   Start side number
     * @param sectorNumberBase Start sector number
     * @param numberingSector  Whether sequential sectors
     * @param singles          Single density
     * @param pTracks          Special tracks
     * @return true: match
     */
    public boolean match(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sectorSize,
                         int interleave, int trackNumberBase, int sideNumberBase, int sectorNumberBase,
                         int numberingSector, List<DiskParticular> singles, List<DiskParticular> pTracks) {
//logger.log(Level.TRACE, "MATCH: %-16s".formatted(diskTypeName) +
//                ", sidesPerDisk: " + (this.sidesPerDisk == sidesPerDisk) +
//                ", tracksPerSide: " + (this.tracksPerSide == tracksPerSide) +
//                ", sectorsPerTrack: " + (this.sectorsPerTrack == sectorsPerTrack) +
//                ", sectorSize: " + (this.sectorSize == sectorSize) +
//                ", interleave: " + (this.interleave == interleave) +
//                ", trackNumberBase: " + (this.trackNumberBase == trackNumberBase) +
//                ", sideNumberBase: " + (this.sideNumberBase == sideNumberBase) +
//                ", sectorNumberBase: " + (this.sectorNumberBase == sectorNumberBase) +
//                ", numberingSector: " + (this.numberingSector = numberingSector) +
//                ", singles: " + (this.singles.equals(singles)) +
//                ", pTracks: " + (this.pTracks.equals(pTracks)) +
//                ", PT: " + this.pTracks + ", " + pTracks);
        boolean match = (this.sidesPerDisk == sidesPerDisk)
                && (this.tracksPerSide == tracksPerSide)
                && (this.sectorsPerTrack == sectorsPerTrack)
                && (this.sectorSize == sectorSize)
                && (this.interleave == interleave)
                && (this.trackNumberBase == trackNumberBase)
                && (this.sideNumberBase == sideNumberBase)
                && (this.sectorNumberBase == sectorNumberBase)
                && (this.numberingSector == numberingSector)
                && (this.singles.equals(singles))
                && (this.pTracks.equals(pTracks));
        return match;
    }

    /**
     * Whether there is a match with the specified parameters
     *
     * @param sidesPerDisk    Side/Disk
     * @param tracksPerSide   Track/Side
     * @param sectorsPerTrack Sector/Track
     * @param sectorSize      Sector size
     * @return true: match
     */
    public boolean match(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sectorSize) {
        boolean match = (this.sidesPerDisk == sidesPerDisk)
                && (this.tracksPerSide == tracksPerSide)
                && (this.sectorsPerTrack == sectorsPerTrack)
                && (this.sectorSize == sectorSize);
        return match;
    }

    /**
     * Whether there is a match with the specified parameters
     *
     * @param param Parameters
     * @return true: match
     */
    public boolean match(DiskParam param) {
        boolean match = (diskTypeName.equals(param.diskTypeName))
                && (reversible == param.reversible)
                && (sidesPerDisk == param.sidesPerDisk)
                && (tracksPerSide == param.tracksPerSide)
                && (sectorsPerTrack == param.sectorsPerTrack)
                && (sectorSize == param.sectorSize)
                && (interleave == param.interleave)
                && (trackNumberBase == param.trackNumberBase)
                && (sideNumberBase == param.sideNumberBase)
                && (sectorNumberBase == param.sectorNumberBase)
                && (numberingSector == param.numberingSector)
                && (singles.equals(param.singles));
        return match;
    }

    /**
     * Whether there is a match with the specified parameters
     *
     * @param param Parameters
     * @return true: match
     */
    public boolean matchExceptName(DiskParam param) {
        boolean match = (reversible == param.reversible)
                && (sidesPerDisk == param.sidesPerDisk)
                && (tracksPerSide == param.tracksPerSide)
                && (sectorsPerTrack == param.sectorsPerTrack)
                && (sectorSize == param.sectorSize)
                && (interleave == param.interleave)
                && (trackNumberBase == param.trackNumberBase)
                && (sideNumberBase == param.sideNumberBase)
                && (sectorNumberBase == param.sectorNumberBase)
                && (numberingSector == param.numberingSector)
                && (singles.equals(param.singles))
                && (pTracks.equals(param.pTracks));
        return match;
    }

    /**
     * Whether there is a match with values close to the specified parameters
     *
     * @param num             Phase number
     * @param sidesPerDisk    Side/Disk
     * @param tracksPerSide   Track/Side
     * @param sectorsPerTrack Sector/Track
     * @param sectorSize      Sector size
     * @param interleave      Interleave
     * @param numberingSector Whether sequential sectors
     * @param singles         Single density
     * @param last            [out] End of search
     * @return true: match
     */
    public boolean matchNear(int num, int sidesPerDisk, int tracksPerSide, int sectorsPerTrack,
                             int sectorSize, int interleave, int numberingSector,
                             List<DiskParticular> singles, boolean[] last) {
        boolean match = false;
        switch (num) {
            case 0:
                // Compare except for special tracks
                match = (this.sidesPerDisk == sidesPerDisk) // Number of sides matches
                        && (this.tracksPerSide == tracksPerSide)
                        && (this.sectorsPerTrack == sectorsPerTrack)
                        && (this.sectorSize == sectorSize)
                        && (this.interleave == interleave)
                        && (this.numberingSector == numberingSector)
                        && (this.singles.equals(singles));
                break;
            case 1:
                // compare without interleave
                match = (this.sidesPerDisk == sidesPerDisk) // Number of sides matches
                        && (this.tracksPerSide == tracksPerSide) // Number of tracks matches
                        && (this.sectorsPerTrack == sectorsPerTrack) // Number of sectors matches
                        && (this.sectorSize == sectorSize) // Sector size matches
                        && (this.numberingSector == numberingSector)    // Sector numbering method matches
                        && (this.singles.equals(singles));    // Single density tracks match
                break;
            case 2:
                // Including interleave,
                // compare with number of tracks within specified range
                match = (this.sidesPerDisk == sidesPerDisk) // Number of sides matches
                        && (this.sectorsPerTrack == sectorsPerTrack) // Number of sectors matches
                        && (this.sectorSize == sectorSize) // Sector size matches
                        && (this.numberingSector == numberingSector)    // Sector numbering method matches
                        && (this.singles.equals(singles))    // Single density tracks match
                        && (this.interleave == interleave) // Interleave matches
                        && ((tracksPerSide - 5) <= this.tracksPerSide && this.tracksPerSide <= tracksPerSide); // Number of tracks is in range -5 to 0
                break;
            case 3:
                // Excluding interleave,
                // compare with number of tracks within specified range
                match = (this.sidesPerDisk == sidesPerDisk) // Number of sides matches
                        && (this.sectorsPerTrack == sectorsPerTrack) // Number of sectors matches
                        && (this.sectorSize == sectorSize) // Sector size matches
                        && (this.numberingSector == numberingSector) // Sector numbering method matches
                        && (this.singles.equals(singles)) // Single density tracks match
                        && ((tracksPerSide - 5) <= this.tracksPerSide && this.tracksPerSide <= tracksPerSide); // Number of tracks is in range -5 to 0
                break;
            case 4:
                match = (this.sidesPerDisk == sidesPerDisk) // Number of sides matches
                        && (this.sectorsPerTrack == sectorsPerTrack) // Number of sectors matches
                        && (this.sectorSize == sectorSize) // Sector size matches
                        && (this.numberingSector == numberingSector) // Sector numbering method matches
                        && ((tracksPerSide - 5) <= this.tracksPerSide && this.tracksPerSide <= tracksPerSide); // Number of tracks is in range -5 to 0
                break;
            case 5:
                match = (this.sidesPerDisk == sidesPerDisk) // Number of sides matches
                        && (this.sectorSize == sectorSize) // Sector size matches
                        && (this.numberingSector == numberingSector) // Sector numbering method matches
                        && ((tracksPerSide - 5) <= this.tracksPerSide && this.tracksPerSide <= tracksPerSide) // Number of tracks is in range -5 to 0
                        && (this.sectorsPerTrack <= sectorsPerTrack); // OK if number of sectors is smaller
                break;
            default:
                last[0] = true;
                break;
        }
        return match;
    }

    /**
     * Whether the specified track and side are single density
     *
     * @param trackNum        Track number
     * @param sideNum         Side number
     * @param sectorsPerTrack [out] Number of sectors
     * @param sectorSize      [out] Sector size
     * @return true, false
     */
    public boolean findSingleDensity(int trackNum, int sideNum, int[] sectorsPerTrack, int[] sectorSize) {
        boolean match = false;
        for (DiskParticular sd : singles) {
            if (sd.match(trackNum, sideNum, -1, -1)) {
                match = true;
                if (sectorsPerTrack != null && sd.getSectorsPerTrack() > 0) {
                    sectorsPerTrack[0] = sd.getSectorsPerTrack();
                }
                if (sectorSize != null && sd.getSectorSize() > 0) {
                    sectorSize[0] = sd.getSectorSize();
                }
                break;
            }
        }
        return match;
    }

    /**
     * Whether the specified track, side, and sector are single density
     *
     * @param trackNum   Track number
     * @param sideNum    Side number
     * @param sectorNum  Sector number
     * @param sectorSize Sector size
     * @return true, false
     */
    public boolean findSingleDensity(int trackNum, int sideNum, int sectorNum, int sectorSize) {
        boolean match = false;
        for (DiskParticular sd : singles) {
            if (sd.match(trackNum, sideNum, sectorNum, sectorSize)) {
                match = true;
                break;
            }
        }
        return match;
    }

    /**
     * Whether it has single density
     *
     * @param sectorsPerTrack [out] Number of sectors
     * @param sectorSize      [out] Sector size
     * @return 0: None, 1: All tracks, 2: Track 0, side 0, 3: Track 0, both sides
     */
    public int hasSingleDensity(int[] sectorsPerTrack /* = null */, int[] sectorSize /* = null */) {
        int val = 0;
        DiskParticular sd = null;
        for (DiskParticular single : singles) {
            if (single.getTrackNumber() < 0 && single.getSideNumber() < 0) {
                sd = single;
                val = 1;
                break;
            } else if (single.getTrackNumber() == 0 && single.getSideNumber() < 0) {
                sd = single;
                val = 3;
                break;
            } else if (single.getTrackNumber() == 0 && single.getSideNumber() == 0) {
                sd = single;
                val = 2;
                break;
            }
        }
        int maxTracks = getTracksPerSide() * getSidesPerDisk();
        if (maxTracks > 0 && maxTracks == singles.size()) {
            sd = singles.getFirst();
            val = 1;
        }
        if (val > 0) {
            if (sectorsPerTrack != null) {
                sectorsPerTrack[0] = sd.getSectorsPerTrack();
                if (sectorsPerTrack[0] < 0) {
                    sectorsPerTrack[0] = getSectorsPerTrack();
                }
            }
            if (sectorSize != null) {
                sectorSize[0] = sd.getSectorSize();
            }
        }
        return val;
    }

    /** Calculate disk size (for plain disk) */
    public int calcDiskSize() {
        int diskSize = 0;
        int track = getTrackNumberBaseOnDisk();
        int tracks = getTracksPerSide() + track;
        for (; track < tracks; track++) {
            for (int sid = 0; sid < getSidesPerDisk(); sid++) {
                int[] secNums = {getSectorsPerTrack()};
                int[] secSize = {getSectorSize()};
                findParticularTrack(track, sid, secNums, secSize);
                findSingleDensity(track, sid, secNums, secSize);

                for (int sec = 0; sec < secNums[0]; sec++) {
                    findParticularSector(track, sid, sec, secSize, null);
                    diskSize += secSize[0];
                }
            }
        }
        return diskSize;
    }

    /**
     * Whether it is a special track
     *
     * @param trackNum        Track number
     * @param sideNum         Side number
     * @param sectorsPerTrack Sector/Track
     * @param sectorSize      Sector size
     * @return true, false
     */
    public boolean findParticularTrack(int trackNum, int sideNum, int[] sectorsPerTrack, int[] sectorSize) {
        DiskParticular match = null;
        for (DiskParam.DiskParticular pt : pTracks) {
            if (pt.getSectorsPerTrack() <= 0) continue;
            if (pt.getSectorNumber() >= 0) continue;
            if (trackNum < pt.getTrackNumber() || (pt.getTrackNumber() + pt.getNumberOfTracks()) <= trackNum)
                continue;
            if (pt.getSideNumber() >= 0 && pt.getSideNumber() != sideNum) continue;
            if (pt.findExclude(trackNum, sideNum)) continue;

            match = pt;
            sectorsPerTrack[0] = pt.getSectorsPerTrack();
            if (pt.getSectorSize() > 0) {
                sectorSize[0] = pt.getSectorSize();
            }
            break;
        }
        return (match != null);
    }

    /**
     * Whether it is a special sector
     *
     * @param trackNum   Track number
     * @param sideNum    Side number
     * @param sectorNum  Sector number
     * @param sectorSize Sector size
     * @param sectorId   [out] Returns array containing C, H, R, N
     * @return true, false
     */
    public boolean findParticularSector(int trackNum, int sideNum, int sectorNum, int[] sectorSize, int[][] sectorId) {
        DiskParticular match = null;
        for (DiskParticular ps : pSectors) {
            if (ps.getSectorSize() <= 0) continue;
            if (ps.getSectorsPerTrack() >= 0) continue;
            if (ps.getSectorNumber() >= 0 && ps.getSectorNumber() != sectorNum) continue;
            if (ps.getSideNumber() >= 0 && ps.getSideNumber() != sideNum) continue;
            if (ps.getTrackNumber() >= 0 && ps.getTrackNumber() != trackNum) continue;
            if (ps.findExclude(trackNum, sideNum)) continue;

            match = ps;
            sectorSize[0] = ps.getSectorSize();
            if (sectorId != null) {
                sectorId[0] = ps.getID();
            }
            break;
        }
        return match != null;
    }

    /**
     * Search for DISK BASIC
     *
     * @param typeName Type name
     * @param flags    Flags
     * @return Name
     */
    public DiskParamName findBasicType(String typeName, int flags) {
        DiskParamName match = null;
        for (DiskParamName item : basicTypes) {
            if (item.getName().equals(typeName) && (flags < 0 || item.getFlags() == flags)) {
                match = item;
                break;
            }
        }
        return match;
    }

    /**
     * Formats disk parameters as a string and returns it
     *
     * @return String
     */
    public String getDiskDescription() {
        StringBuilder str = new StringBuilder();

        if (!densityName.isEmpty()) {
            str.append(densityName);
            str.append("  ");
        }
        String format = "%d";
        format += tracksPerSide <= 1 ? "track" : "tracks";
        format += " ";
        format += "%d";
        format += sidesPerDisk <= 1 ? "side" : "sides";
        format += " ";
        if (variableSecsPerTrack) {
            format += "%d-%d";
            format += "sectors";
        } else {
            format += "%d";
            format += sectorsPerTrack <= 1 ? "sector" : "sectors";
        }
        format += " ";
        format += "%d";
        format += "bytes/sector";
        format += " ";
        format += "Interleave:";
        format += "%d";

        if (variableSecsPerTrack) {
            int minSectors = DiskParticular.getMinSectorsPerTrack(pTracks, sectorsPerTrack);
            int maxSectors = DiskParticular.getMaxSectorsPerTrack(pTracks, sectorsPerTrack);
            str.append(String.format(format, tracksPerSide, sidesPerDisk, minSectors, maxSectors, sectorSize, interleave));
        } else {
            str.append(String.format(format, tracksPerSide, sidesPerDisk, sectorsPerTrack, sectorSize, interleave));
        }

        if (!description.isEmpty()) {
            str.append(" ");
            str.append(description);
        }
        return str.toString();
    }

    /** Set disk type name "2D", "2HD", etc. */
    public void setDiskTypeName(String str) {
        diskTypeName = str;
    }

    /** Set BASIC types */
    public void setBasicTypes(List<DiskParamName> arr) {
        basicTypes = new ArrayList<>(arr);
    }

    /** Set whether it is reversible with AB sides */
    public void setReversible(boolean val) {
        reversible = val;
    }

    /** Set number of sides */
    public void setSidesPerDisk(int val) {
        sidesPerDisk = val;
    }

    /** Set number of tracks */
    public void setTracksPerSide(int val) {
        tracksPerSide = val;
    }

    /** Set number of sectors */
    public void setSectorsPerTrack(int val) {
        sectorsPerTrack = val;
    }

    /** Set sector size */
    public void setSectorSize(int val) {
        if (val == 0) {
            logger.log(Level.WARNING, "sectorSize = 0", new Exception("sectorSize = 0"));
        }
        sectorSize = val;
    }

    /** Set sector numbering method (0: per side, 1: per track) */
    public void setNumberingSector(int val) {
        numberingSector = val;
    }

    /** Set density (0x00: 2D, 0x10: 2DD, 0x20: 2HD) */
    public void setParamDensity(int val) {
        diskDensity = val;
    }

    /** Set sector interval */
    public void setInterleave(int val) {
        interleave = val;
    }

    /** Set start track number */
    public void setTrackNumberBaseOnDisk(int val) {
        trackNumberBase = val;
    }

    /** Set start side number */
    public void setSideNumberBaseOnDisk(int val) {
        sideNumberBase = val;
    }

    /** Set start sector number */
    public void setSectorNumberBaseOnDisk(int val) {
        sectorNumberBase = val;
    }

    /** Number of sectors differs per track */
    public void setVariableSectorsPerTrack(boolean val) {
        variableSecsPerTrack = val;
    }

    /** Set tracks to be single density */
    public void setSingles(List<DiskParticular> arr) {
        singles = new ArrayList<>();
        singles.addAll(arr);
    }

    /** Set special tracks */
    public void setParticularTracks(List<DiskParticular> arr) {
        pTracks = new ArrayList<>();
        pTracks.addAll(arr);
    }

    /** Set special sectors */
    public void setParticularSectors(List<DiskParticular> arr) {
        pSectors = new ArrayList<>();
        pSectors.addAll(arr);
    }

    /** Set density information (for description) */
    public void setDensityName(String str) {
        densityName = str;
    }

    /** Set description */
    public void setDescription(String str) {
        description = str;
    }

    /** Add tracks to be single density */
    public void addSingleDensity(DiskParticular val) {
        singles.add(val);
    }

    /** Add special track */
    public void addParticularTrack(DiskParticular val) {
        pTracks.add(val);
    }

    /** Add special sector */
    public void addParticularSector(DiskParticular val) {
        pSectors.add(val);
    }

    /** Return disk type name "2D", "2HD", etc. */
    public String getDiskTypeName() {
        return diskTypeName;
    }

    /** Return BASIC types */
    public List<DiskParamName> getBasicTypes() {
        return basicTypes;
    }

    /** Return whether it is reversible with AB sides */
    public boolean isReversible() {
        return reversible;
    }

    /** Return number of sides */
    public int getSidesPerDisk() {
        return sidesPerDisk;
    }

    /** Return number of tracks */
    public int getTracksPerSide() {
        return tracksPerSide;
    }

    /** Return number of sectors */
    public int getSectorsPerTrack() {
        return sectorsPerTrack;
    }

    /** Return sector size */
    public int getSectorSize() {
//logger.log(Level.INFO, "sectorSize: " + sectorSize);
        return sectorSize;
    }

    /** Return sector numbering method (0: per side, 1: per track) */
    public int getNumberingSector() {
        return numberingSector;
    }

    /** Return density (0x00: 2D, 0x10: 2DD, 0x20: 2HD) */
    public int getParamDensity() {
        return diskDensity;
    }

    /** Return sector interval */
    public int getInterleave() {
        return interleave;
    }

    /** Return start track number */
    public int getTrackNumberBaseOnDisk() {
        return trackNumberBase;
    }

    /** Return start side number */
    public int getSideNumberBaseOnDisk() {
        return sideNumberBase;
    }

    /** Return start sector number */
    public int getSectorNumberBaseOnDisk() {
        return sectorNumberBase;
    }

    /** Number of sectors differs per track */
    public boolean isVariableSectorsPerTrack() {
        return variableSecsPerTrack;
    }

    /** Return tracks to be single density */
    public List<DiskParticular> getSingles() {
        return singles;
    }

    /** Return special tracks */
    public List<DiskParticular> getParticularTracks() {
        return pTracks;
    }

    /** Return special sectors */
    public List<DiskParticular> getParticularSectors() {
        return pSectors;
    }

    /** Return density information (for description) */
    public String getDensityName() {
        return densityName;
    }

    /** Return description */
    public String getDescription() {
        return description;
    }

    /** Provides templates for disk parameters */
    public static class DiskTemplates extends TemplatesBase {

        private final List<DiskParam> params;

        /**
         * Provides templates for disk parameters
         */
        public DiskTemplates() {
            this.params = new ArrayList<>();
        }

        /**
         * Load from XML file
         *
         * @return true / false
         * @see ""disk_types.xml"
         * @param dataPath    Path where the input file is located
         * @param localeName  Locale name
         * @param errMessages [out] Error messages
         */
        public boolean load(String dataPath, String localeName, StringBuilder errMessages) {
            params.clear();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document doc;
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                doc = builder.parse(new File(dataPath + "disk_types.xml"));
            } catch (ParserConfigurationException | SAXException | IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
                return false;
            }

            // start processing the XML file
            if (!doc.getDocumentElement().getNodeName().equals("DiskTypes")) {
logger.log(Level.ERROR, "no DiskTypes element found");
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
                        switch (itemnode.getNodeName()) {
                            case "Reversible" -> p.setReversible(Utils.toBool(str));
                            case "SidesPerDisk" -> p.setSidesPerDisk(Utils.toInt(str));
                            case "TracksPerSide" -> p.setTracksPerSide(Utils.toInt(str));
                            case "SectorsPerTrack" -> p.setSectorsPerTrack(Utils.toInt(str));
                            case "SectorSize" -> p.setSectorSize(Utils.toInt(str));
                            case "NumberingSector" -> {
                                str = str.toLowerCase();
                                if (str.equals("track")) {
                                    p.setNumberingSector(1);
                                }
                            }
                            case "Density" -> p.setParamDensity(Utils.toInt(str));
                            case "Interleave" -> p.setInterleave(Utils.toInt(str));
                            case "TrackNumberBase" -> p.setTrackNumberBaseOnDisk(Utils.toInt(str));
                            case "SideNumberBase" -> p.setSideNumberBaseOnDisk(Utils.toInt(str));
                            case "SectorNumberBase" -> p.setSectorNumberBaseOnDisk(Utils.toInt(str));
                            case "VariableSectorsPerTrack" -> p.setVariableSectorsPerTrack(Utils.toBool(str));
                            case "DiskBasicTypes" -> {
                                List<DiskParamName> basic_types = new ArrayList<>();
                                if (!loadDiskBasicTypes(itemnode, basic_types, errMessages)) {
                                    return false;
                                }
                                p.setBasicTypes(basic_types);
                            }
                            case "SingleDensity" -> {
                                DiskParticular s = new DiskParticular();
                                if (!loadSingleDensity(itemnode, s, errMessages)) {
                                    return false;
                                }
                                p.addSingleDensity(s);
                            }
                            case "ParticularTrack" -> {
                                DiskParticular d = new DiskParticular();
                                if (!loadParticularTrack(itemnode, d, errMessages)) {
                                    return false;
                                }
                                p.addParticularTrack(d);
                            }
                            case "ParticularSector" -> {
                                DiskParticular d = new DiskParticular();
                                if (!loadParticularSector(itemnode, d, errMessages)) {
                                    return false;
                                }
                                p.addParticularSector(d);
                            }
                            case "DensityName" -> loadDescription(itemnode, localeName, den_name, den_name_locale);
                            case "Description" -> loadDescription(itemnode, localeName, desc, desc_locale);
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
                        errMessages.append("\n");
                        errMessages.append("Duplicate type name in DiskType : ");
                        errMessages.append(type_name);
                        return false;
                    }
                }
                item = item.getNextSibling();
            }
            assert !params.isEmpty();
logger.log(Level.INFO, "params: " + params.size());
            return true;
        }

        /**
         * Load DiskBasicTypes element
         *
         * @param node        Child node
         * @param basicTypes  [out] Loaded data
         * @param errMessages [out] Error messages
         * @return true
         */
        public boolean loadDiskBasicTypes(Node node, List<DiskParamName> basicTypes, StringBuilder errMessages) {
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
                    basicTypes.add(p);
                }
                citemnode = citemnode.getNextSibling();
            }
            return true;
        }

        /**
         * Load SingleDensity element
         *
         * @param node        Child node
         * @param s           [out] Loaded data
         * @param errMessages [out] Error messages
         * @return true
         */
        public boolean loadSingleDensity(Node node, DiskParticular s, StringBuilder errMessages) {
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
         * Load ParticularTrack element
         *
         * @param node        Child node
         * @param d           [out] Loaded data
         * @param errMessages [out] Error messages
         * @return true
         */
        public boolean loadParticularTrack(Node node, DiskParticular d, StringBuilder errMessages) {
            String str;
            str = ((Element) node).getAttribute("track");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setTrackNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("tracks");
            if (str.equalsIgnoreCase("ALL")) {
                errMessages.append("\n");
                errMessages.append("Cannot set \"ALL\" on tracks attribute in ParticularTrack element.");
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
                    DiskParam.TrackParam e = new DiskParam.TrackParam();
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
         * Load ParticularSector element
         *
         * @param node        Child node
         * @param d           [out] Loaded data
         * @param errMessages [out] Error messages
         * @return true
         */
        public boolean loadParticularSector(Node node, DiskParticular d, StringBuilder errMessages) {
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
         * Returns the number of the template matching the type name
         *
         * @param typeName Type name
         * @return Disk template position / -1 if none
         */
        public int indexOf(String typeName) {
            int match = -1;
            for (int i = 0; i < params.size(); i++) {
                DiskParam item = params.get(i);
                if (typeName.equals(item.getDiskTypeName())) {
                    match = i;
                    break;
                }
            }
            return match;
        }

        /** Returns the matching template */
        public DiskParam find(DiskParam n_param) {
            DiskParam match = null;
            for (DiskParam item : params) {
                if (item.matchExceptName(n_param)) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        /** Returns the template matching the type name */
        public DiskParam find(String n_type_name) {
            DiskParam match = null;
            for (DiskParam item : params) {
                if (n_type_name.equals(item.getDiskTypeName())) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        /**
         * Returns the template matching the parameters
         *
         * @param sidesPerDisk     Number of sides
         * @param tracksPerSide    Number of tracks
         * @param sectorsPerTrack  Number of sectors
         * @param sectorSize       Sector size
         * @param interleave       Interleave
         * @param trackNumberBase  Start track number
         * @param sideNumberBase   Start side number
         * @param sectorNumberBase Start sector number
         * @param numberingSector  Sector numbering method
         * @param singles          Single density information
         * @param pTracks          Special tracks
         * @return Disk parameter or null
         */
        public DiskParam findStrict(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sectorSize,
                                    int interleave, int trackNumberBase, int sideNumberBase, int sectorNumberBase,
                                    int numberingSector, List<DiskParticular> singles, List<DiskParticular> pTracks) {
            DiskParam matchItem = null;
            for (DiskParam item : params) {
                boolean m = item.match(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize, interleave,
                        trackNumberBase, sideNumberBase, sectorNumberBase, numberingSector, singles, pTracks);
//logger.log(Level.TRACE, "TOTAL MATCH: " + item.diskTypeName + ", " + m);
                if (m) {
                    matchItem = item;
                    break;
                }
            }
            return matchItem;
        }

        /**
         * Returns the template matching or close to the parameters
         *
         * @param sidesPerDisk     Number of sides
         * @param tracksPerSide    Number of tracks
         * @param sectorsPerTrack  Number of sectors
         * @param sectorSize       Sector size
         * @param interleave       Interleave
         * @param trackNumberBase  Start track number
         * @param sideNumberBase   Start side number
         * @param sectorNumberBase Start sector number
         * @param numberingSector  Sector numbering method
         * @param singles          Single density information
         * @param pTracks          Special tracks
         * @return Disk parameter or null
         */
        public DiskParam find(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sectorSize,
                              int interleave, int trackNumberBase, int sideNumberBase, int sectorNumberBase,
                              int numberingSector, List<DiskParticular> singles, List<DiskParticular> pTracks) {
logger.log(Level.TRACE, new StringJoiner(", ", "PARAM: ", "")
//                .add(diskTypeName)
//                .add(reversible + "")
                .add(sidesPerDisk + "")
                .add(tracksPerSide + "")
                .add(sectorsPerTrack + "")
                .add(sectorSize + "")
                .add(numberingSector + "")
//                .add(diskDensity + "")
                .add(interleave + "")
                .add(trackNumberBase + "")
                .add(sideNumberBase + "")
                .add(sectorNumberBase + "")
//                .add(variableSecsPerTrack + "")
                .add("\"" + singles + "\"")
                .add("\"" + pTracks + "\"")
//                .add("\"" + pSectors + "\"")
//                .add("\"" + densityName + "\"")
//                .add("\"" + description + "\"")
//                .add("\"" + basicTypes + "\"")
                .toString());
            DiskParam matchItem = findStrict(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                    interleave, trackNumberBase, sideNumberBase, sectorNumberBase, numberingSector, singles, pTracks);
            if (matchItem == null) {
logger.log(Level.TRACE, "no strict match");
                boolean[] last = {false};
                boolean m = false;
                for (int num = 0; !last[0] && !m; num++) {
                    // If parameters do not match, use parameters close to the arguments
                    for (DiskParam item : params) {
                        m = item.matchNear(num, sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                                interleave, numberingSector, singles, last);
                        if (last[0]) {
                            break;
                        }
                        if (m) {
                            matchItem = item;
                            break;
                        }
                    }
                }
            }
            return matchItem;
        }

        /**
         * Returns a list of templates matching the parameters
         *
         * @param sidesPerDisk    Number of sides
         * @param tracksPerSide   Number of tracks
         * @param sectorsPerTrack Number of sectors
         * @param sector_size     Sector size
         * @param list            [out] Candidate list
         * @param separator       Whether to add a separator (null) at the beginning of the list
         * @return Number of items in the list
         */
        public int find(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sector_size,
                        List<DiskParam> list, boolean separator) {
            for (DiskParam item : params) {
                if (item.match(sidesPerDisk, tracksPerSide, sectorsPerTrack, sector_size)) {
                    // Add if not duplicated
                    if (!list.contains(item)) {
                        if (separator) {
                            // Add a separator before the first candidate
                            list.add(null);
                            separator = false;
                        }
                        list.add(item);
                    }
                }
            }
            return list.size();
        }

        public DiskParam get(int index) {
            return params.get(index);
        }

        public int size() {
            return params.size();
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DiskParam.class.getSimpleName() + "[", "]")
                .add("diskTypeName='" + diskTypeName + "'")
                .add("reversible=" + reversible)
                .add("sidesPerDisk=" + sidesPerDisk)
                .add("tracksPerSide=" + tracksPerSide)
                .add("sectorsPerTrack=" + sectorsPerTrack)
                .add("sectorSize=" + sectorSize)
                .add("numberingSector=" + numberingSector)
                .add("diskDensity=" + diskDensity)
                .add("interleave=" + interleave)
                .add("trackNumberBase=" + trackNumberBase)
                .add("sideNumberBase=" + sideNumberBase)
                .add("sectorNumberBase=" + sectorNumberBase)
                .add("variableSecsPerTrack=" + variableSecsPerTrack)
                .add("singles=" + singles)
                .add("pTracks=" + pTracks)
                .add("pSectors=" + pSectors)
                .add("densityName='" + densityName + "'")
                .add("description='" + description + "'")
                .add("basicTypes=" + basicTypes)
                .toString();
        // csv
//        return new StringJoiner(", ", "", "")
//                .add(diskTypeName)
//                .add(reversible + "")
//                .add(sidesPerDisk + "")
//                .add(tracksPerSide + "")
//                .add(sectorsPerTrack + "")
//                .add(sectorSize + "")
//                .add(numberingSector + "")
//                .add(diskDensity + "")
//                .add(interleave + "")
//                .add(trackNumberBase + "")
//                .add(sideNumberBase + "")
//                .add(sectorNumberBase + "")
//                .add(variableSecsPerTrack + "")
//                .add("\"" + singles + "\"")
//                .add("\"" + pTracks + "\"")
//                .add("\"" + pSectors + "\"")
//                .add("\"" + densityName + "\"")
//                .add("\"" + description + "\"")
//                .add("\"" + basicTypes + "\"")
//                .toString();
    }
}
