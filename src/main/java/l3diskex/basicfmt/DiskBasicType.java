package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam.NumSectorsParam;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.StringUtil;

import static l3diskex.basicfmt.DiskBasicError.ERRV_NO_SECTOR;
import static l3diskex.basicfmt.DiskBasicError.ERRV_NO_TRACK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * DISK BASIC model-dependent individual processing template
 * <p>
 * Abstract class
 */
public abstract class DiskBasicType<T extends Directory> {

    private static final Logger logger = System.getLogger(DiskBasicType.class.getName());

    public static final int INVALID_GROUP_NUMBER = -1;

    public abstract boolean isSupported(int typeNumber);

    /** Flags when allocating sectors */
    public enum AllocateGroupFlags {
        ALLOCATE_GROUPS_NEW,
        ALLOCATE_GROUPS_APPEND
    }

    /**
     * Sector skew map base
     *
     * @see SectorsPerTrack
     * @see DiskBasicSectorPosTrans
     */
    public static class SectorSkewBase implements Cloneable {

        /** Sector count */
        protected int numSecs;

        public SectorSkewBase() {
            numSecs = 0;
        }

        public SectorSkewBase(SectorSkewBase src) {
            numSecs = src.numSecs;
        }

        /** Used during clone copy */
        @Override
        public SectorSkewBase clone() {
            try {
                return (SectorSkewBase) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }

        /** Delete skew map */
        public void delete() {
        }

        /** Create skew map */
        public void create(DiskBasic basic, int numSecs) {
            this.numSecs = numSecs;
        }

        /** Map skew map */
        public void mapping(DiskBasic basic) {
        }

        /** Convert to physical number */
        public int toPhysical(int val) {
            return 0;
        }

        /** Convert to logical number */
        public int toLogical(int val) {
            return 0;
        }
    }

    /**
     * Sector skew map
     *
     * @see SectorsPerTrack
     * @see DiskBasicSectorPosTrans
     */
    public static class DiskBasicSectorSkew extends SectorSkewBase implements Cloneable {

        /** Logical sector number to physical sector number */
        protected int[] ltopMap;
        /** Physical sector number to logical sector number */
        protected int[] ptolMap;

        public DiskBasicSectorSkew() {
            super();
            ltopMap = null;
            ptolMap = null;
        }

        public DiskBasicSectorSkew(DiskBasicSectorSkew src) {
            super(src);
            if (src.ltopMap != null) {
                ltopMap = new int[src.numSecs];
                System.arraycopy(src.ltopMap, 0, ltopMap, 0, src.numSecs);
            }
            if (src.ptolMap != null) {
                ptolMap = new int[src.numSecs];
                System.arraycopy(src.ptolMap, 0, ptolMap, 0, src.numSecs);
            }
        }

        /** Used during clone copy */
        @Override
        public SectorSkewBase clone() {
            return new DiskBasicSectorSkew(this);
        }

        /** Delete skew map */
        @Override
        public void delete() {
            ltopMap = null;
            ptolMap = null;
        }

        /** Create skew map */
        @Override
        public void create(DiskBasic basic, int numSecs) {
            super.create(basic, numSecs);

            ltopMap = new int[this.numSecs];
            ptolMap = new int[this.numSecs];

            for (int i = 0; i < this.numSecs; i++) {
                ltopMap[i] = -1;
                ptolMap[i] = -1;
            }

            mapping(basic);
        }

        protected void mappingFromParam(DiskBasic basic) {
            int psec = 0;
            for (int lsec = 0; lsec < numSecs; lsec++) {
                psec = basic.getSectorSkewMap(lsec);
                ltopMap[lsec] = psec;
                if (psec < numSecs) ptolMap[psec] = lsec;
            }
        }

        protected void mappingFromCalc(DiskBasic basic, int skew) {
            if (skew < 1) skew = 1;

            int psec = 0;
            for (int lsec = 0; lsec < numSecs; lsec++) {
                ltopMap[lsec] = psec;
                ptolMap[psec] = lsec;
                psec += skew;
                if (psec >= numSecs) {
                    psec -= numSecs;
                    for (int limit = numSecs; psec < numSecs && ptolMap[psec] >= 0 && limit > 0; limit--) { // TODO vavi adds "psec < numSecs"
                        psec++;
                    }
                }
            }
        }

        /** Map skew map */
        @Override
        public void mapping(DiskBasic basic) {
            if (basic.hasSectorSkewMap()) {
                mappingFromParam(basic);
            } else {
                // The system has soft sector skew (virtual interleave)
                mappingFromCalc(basic, basic.getSectorSkew());
            }
        }

        /** Convert to physical number */
        @Override
        public int toPhysical(int val) {
            if (ltopMap != null) val = ltopMap[val];
            return val;
        }

        /** Convert to logical number */
        @Override
        public int toLogical(int val) {
            if (ptolMap != null) val = ptolMap[val];
            return val;
        }
    }

    /** Sector skew map: how to allocate free sectors during import */
    public static class DiskBasicSectorSkewForSave extends DiskBasicSectorSkew implements Cloneable {

        public DiskBasicSectorSkewForSave() {
            super();
        }

        public DiskBasicSectorSkewForSave(DiskBasicSectorSkewForSave src) {
            super(src);
        }

        /** Used during clone copy */
        @Override
        public SectorSkewBase clone() {
            return new DiskBasicSectorSkewForSave(this);
        }

        /** Map skew map */
        @Override
        public void mapping(DiskBasic basic) {
            mappingFromCalc(basic, basic.getVariousIntegerParam("SectorSkewForSave"));
        }
    }

    /**
     * Holds the number of sectors per track
     *
     * @see DiskBasicSectorPosTrans
     */
    public static class SectorsPerTrack {

        /** Number of tracks */
        private final int numOfTracks;
        /** Number of sectors (across both sides) */
        private final int numOfSectors;
        /** Total number of sectors */
        private final int totalSectors;
        /** Sector skew */
        private SectorSkewBase ssmap;

        public SectorsPerTrack() {
            numOfTracks = 0;
            numOfSectors = 0;
            totalSectors = 0;
            ssmap = null;
        }

        public SectorsPerTrack(SectorsPerTrack src) {
            numOfTracks = src.numOfTracks;
            numOfSectors = src.numOfSectors;
            totalSectors = src.totalSectors;
            if (src.ssmap != null) {
                ssmap = src.ssmap.clone();
            } else {
                ssmap = null;
            }
        }

        public SectorsPerTrack(int numOfTracks, int numOfSectors, int totalSectors) {
            this.numOfTracks = numOfTracks;
            this.numOfSectors = numOfSectors;
            this.totalSectors = totalSectors;
            this.ssmap = null;
        }

        /** Returns the number of tracks */
        public int getNumOfTracks() {
            return numOfTracks;
        }

        /** Returns the number of sectors */
        public int getNumOfSectors() {
            return numOfSectors;
        }

        /** Returns the total number of sectors */
        public int getTotalSectors() {
            return totalSectors;
        }

        /** Set skew map */
        public void setSectorSkewMap(SectorSkewBase map) {
            ssmap = map;
        }

        /** Returns skew map */
        public SectorSkewBase getSectorSkewMap() {
            return ssmap;
        }
    }

    /**
     * Sector position conversion map list Array of SectorsPerTrack
     * <p>
     * If the number of sectors per track is different, use this list to
     * determine the sector position.
     */
    public static class DiskBasicSectorPosTrans {

        protected List<SectorsPerTrack> list = new ArrayList<>();

        public void create(DiskBasic basic) {
            list.clear();
            List<NumSectorsParam> sp = basic.sectorsPerTrackOnBasicList();
            if (!sp.isEmpty()) {
                for (NumSectorsParam p : sp) {
                    list.add(new SectorsPerTrack(p.getNumberOfTracks(), p.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic(), p.getNumberOfTracks() * p.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic()));
                }
            } else if (basic.isVariableSectorsPerTrack()) {
                int numOfTracks = 0;
                int prevNumOfSectors = 0;
                int totalSectors = 0;
                int trk = basic.getTrackNumberBaseOnDisk();
                int trks = basic.getTracksPerSideOnBasic() + trk;
                for (; trk < trks; trk++) {
                    for (int sid = 0; sid < basic.getSidesPerDiskOnBasic(); sid++) {
                        DiskImageTrack track = basic.getTrack(trk, sid);
                        if (track == null) {
                            continue;
                        }
                        int numOfSectors = track.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic();
                        if (prevNumOfSectors == 0) {
                            totalSectors = 0;
                            prevNumOfSectors = numOfSectors;
                            numOfTracks = 0;
                        } else if (prevNumOfSectors != numOfSectors) {
                            list.add(new SectorsPerTrack(numOfTracks, prevNumOfSectors, totalSectors));
                            totalSectors = 0;
                            prevNumOfSectors = numOfSectors;
                            numOfTracks = 0;
                        }
                        totalSectors += numOfSectors;
                    }
                    numOfTracks++;
                }
                list.add(new SectorsPerTrack(numOfTracks, prevNumOfSectors, totalSectors));
            } else {
                list.add(new SectorsPerTrack(basic.getTracksPerSideOnBasic(),
                        basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic(),
                        basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic()));
            }
        }

        /**
         * Create sector skew map
         * <p>
         * Create a derived class when using this.
         */
        public void createSectorSkewMap(DiskBasic basic) {
        }

        /** Returns item of the specified track */
        public SectorsPerTrack findByTrackNum(int trackNum) {
            SectorsPerTrack match = null;
            for (SectorsPerTrack item : list) {
                if (trackNum < item.getNumOfTracks()) {
                    match = item;
                    break;
                }
                trackNum -= item.getNumOfTracks();
            }
            return match;
        }

        /** Returns the total number of sectors for all tracks */
        public int getTotalSectors() {
            int val = 0;
            for (SectorsPerTrack item : list) {
                val += item.getTotalSectors();
            }
            return val;
        }

        /** Get track and sector numbers from logical sector position (serial number starting from 0 for the first track & sector). Side number is converted to a serial sector number. */
        public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sectorNum, int[] numOfSectors) {
            trackNum[0] = 0;
            for (SectorsPerTrack item : list) {
                if (sectorPos < item.getTotalSectors()) {
                    numOfSectors[0] = item.getNumOfSectors();
                    trackNum[0] += (sectorPos / numOfSectors[0]);
                    sectorNum[0] = sectorPos % numOfSectors[0];
                    break;
                }
                sectorPos -= item.getTotalSectors();
                trackNum[0] += item.getNumOfTracks();
            }
        }

        /** Get sector position (serial number starting from 0 for the first track & sector) from track and sector numbers. Side number is converted to a serial sector number. */
        public int getSectorPosFromNum(int trackNum, int sectorNum, int[] numOfSectors) {
            int sectorPos = 0;
            for (SectorsPerTrack item : list) {
                if (trackNum < item.getNumOfTracks()) {
                    numOfSectors[0] = item.getNumOfSectors();
                    sectorPos += trackNum * numOfSectors[0] + sectorNum;
                    break;
                }
                trackNum -= item.getNumOfTracks();
                sectorPos += item.getTotalSectors();
            }
            return sectorPos;
        }

        public SectorsPerTrack get(int i) {
            return list.get(i);
        }

        public int size() {
            return list.size();
        }
    }

    protected DiskBasic basic;
    protected DiskBasicFat fat;
    protected DiskBasicDir<T> dir;

    /** Starting group number of management area */
    protected int managedStartGroup;
    /** Starting group number of data area */
    protected int dataStartGroup;

    /** Usage status (FAT, unit of groups) */
    protected DiskBasicAvailability fatAvailability = new DiskBasicAvailability();

    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        this.basic = basic;
        this.fat = fat;
        this.dir = dir;
        this.dataStartGroup = 0;
    }

    /** Set FAT position */
    public void setGroupNumber(int num, int val) throws IOException {
    }

    /** Returns FAT position */
    public int getGroupNumber(int num) throws IOException {
        return 0;
    }

    /** Whether it is a used group number */
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /** Get next group number */
    public int getNextGroupNumber(int num, int sectorPos) {
        return 0;
    }

    /** Returns free position */
    public int getEmptyGroupNumber() throws IOException {
        int newNum = INVALID_GROUP_NUMBER;
        for (int num = 0; num <= basic.getFatEndGroup(); num++) {
            int gnum = getGroupNumber(num);
            if (gnum == basic.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    /** Returns the next free position */
    public int getNextEmptyGroupNumber(int currGroup) throws IOException {
        int newNum = INVALID_GROUP_NUMBER;
        int secsPerGrp = basic.getSectorsPerGroup();
        int secsPerTrk = basic.getSectorsPerTrackOnBasic();
        int sides = basic.getSidesPerDiskOnBasic();

        int sed = secsPerTrk * sides / secsPerGrp;
        if (sed == 0) return INVALID_GROUP_NUMBER;
        int groupMax = (basic.getFatEndGroup() / sed) + 1;
        int groupManage = managedStartGroup / sed;
        int groupStart = currGroup / sed;
        int groupEnd;
        int dir;
        int sst = 0;
        boolean found = false;

        dir = (groupStart >= groupManage ? 1 : -1);

        for (int i = 0; i < 2; i++) {
            groupEnd = (dir > 0 ? groupMax : -1);
            for (int g = groupStart; g != groupEnd; g += dir) {
                for (int s = sst; s < sed; s++) {
                    int num = g * sed + s;
                    if (num > basic.getFatEndGroup()) {
                        break;
                    }
                    int gnum = getGroupNumber(num);
                    if (gnum == basic.getGroupUnusedCode()) {
                        newNum = num;
                        found = true;
                        break;
                    }
                }
                if (found) break;
                sst = 0;
            }
            if (found) break;
            dir = -dir;
            groupStart = groupManage;
        }
        return newNum;
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     *
     * @param isFormatting Whether formatting is in progress
     */
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        return 1.0;
    }

    /**
     * Check FAT area
     *
     * @param isFormatting Whether formatting is in progress
     */
    public double checkFat(boolean isFormatting) throws IOException {
        return 1.0;
    }

    /** Get starting position of FAT (for dialog) */
    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int secPos = basic.getFatStartSector() - 1;
        int secFat = basic.getSectorsPerFat();
        DiskImageTrack track = null;
        if (secPos >= 0 && secFat > 0) {
            if (basic.getFatSideNumber() >= 0)
                secPos += basic.getFatSideNumber() * basic.getSectorsPerTrackOnBasic();
            track = basic.getManagedTrack(secPos, sideNum, sectorNum);
        }
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        } else {
            trackNum[0] = -1;
        }
    }

    /** Get end position of FAT (for dialog) */
    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int secSta = basic.getFatStartSector() - 1;
        int secPos = secSta + basic.getSectorsPerFat() * basic.getNumberOfFats() - 1;
        int secFat = basic.getSectorsPerFat();
        DiskImageTrack track = null;
        if (secSta >= 0 && secFat > 0) {
            if (basic.getFatSideNumber() >= 0)
                secPos += basic.getFatSideNumber() * basic.getSectorsPerTrackOnBasic();
            track = basic.getManagedTrack(secPos, sideNum, sectorNum);
        }
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        } else {
            trackNum[0] = -1;
        }
    }

    /** Title name such as "FAT" (for dialog) */
    public String getTitleForFat() {
        return "FAT";
    }

    /** Calculate group number from track number of management area */
    public int calcManagedStartGroup() {
        int trk = basic.getManagedTrackNumber();
        int sid = basic.getFatSideNumber();
        if (sid < 0) sid = 0;
        int sides = basic.getSidesPerDiskOnBasic();
        int secsPerGrp = basic.getSectorsPerGroup();
        int secsPerTrk = basic.getSectorsPerTrackOnBasic();
        managedStartGroup = (trk * sides + sid) * secsPerTrk / secsPerGrp;
        return managedStartGroup;
    }

    /** Calculate sector list for root directory */
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        groupItems.clear();
        int dirSize = 0;
        int sectorBase = basic.getSectorNumberBase();
        for (int secPos = startSector - sectorBase; secPos <= endSector - sectorBase; secPos++) {
            int[] trkNum = new int[1], sidNum = new int[1], secNum = new int[1], divNum = new int[1], divNums = new int[1];
            secNum[0] = 1;
            divNums[0] = 1;
            DiskImageSector sector = basic.getManagedSector(secPos, trkNum, sidNum, secNum, divNum, divNums);
            if (sector == null) continue;
            groupItems.add(secPos, 0, trkNum[0], sidNum[0], secNum[0], secNum[0], divNum[0], divNums[0]);
            dirSize += (sector.getSectorSize() / divNums[0]);
        }
        groupItems.setSize(dirSize);
        return true;
    }

    /** Check root directory */
    public double checkRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;
        double validRatio = -1.0;
        if (calcGroupsOnRootDirectory(startSector, endSector, groupItems)) {
            validRatio = checkDirectory(true, groupItems);
        }
        return validRatio;
    }

    /** Assign root directory */
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<T> dirItem) throws IOException {
        calcGroupsOnRootDirectory(startSector, endSector, groupItems);
        return assignDirectory(true, groupItems, dirItem);
    }

    /** Check directory */
    public double checkDirectory(boolean isRoot, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;
        boolean[] last = {false};
        int usedItems = 0;
        double normals = 0.0;

        int indexNumber = 0;
        int[] pos = {0};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        DiskBasicDirItem<T> nItem = dir.newItem(null, 0, null, 0);
        for (int idx = 0; idx < groupItems.size() && finish >= -1; idx++) {
            DiskBasicGroupItem gItem = groupItems.get(idx);
            int grpNum = gItem.group;
            int trkNum = gItem.track;
            int sidNum = gItem.side;
            int divNum = gItem.divNum; // Division number
            int mumDivs = gItem.numOfDivs; // Number of divisions
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                valid = false;
                break;
            }
            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.size() ? groupItems.get(idx + 1) : null;

            for (int secNum = gItem.sectorStart; secNum <= gItem.sectorEnd && finish >= -1; secNum++) {
                DiskImageSector sector = track.getSector(secNum);
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

                int[] size = {sector.getSectorSize() / mumDivs};

                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gItem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);

                // Add offset
                bufferOffset += (size[0] * divNum);
                bufferOffset += pos[0];

                if (grpNum != prevGrpNum) {
                    // Position to skip when group number changes
                    bufferOffset += basic.getDirStartPosOnGroup();
                    pos[0] += basic.getDirStartPosOnGroup();
                    sizeRemain[0] -= basic.getDirStartPosOnGroup();
                    prevGrpNum = grpNum;
                }

                if (idx == 0 && secNum == gItem.sectorStart) {
                    // Position to skip the beginning of directory area
                    int skip = isRoot ? basic.getDirStartPosOnRoot() : basic.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }

                // Position to skip the beginning of each sector in directory area
                bufferOffset += basic.getDirStartPosOnSector();
                pos[0] += basic.getDirStartPosOnSector();
                sizeRemain[0] -= basic.getDirStartPosOnSector();

                while (valid && !last[0] && pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        // End. Position is at the beginning of next group.
                        sizeRemain[0] = size[0] - pos[0];
                        pos[0] = size[0];
                        break;
                    }
//logger.log(Level.DEBUG, "sector buffer: " + bufferOffset + " / " + buffer.length + "\n" + StringUtil.getDump(buffer, bufferOffset, 32));
                    nItem.setData(indexNumber, gItem, sector, pos[0], buffer, bufferOffset, nextSec);
                    valid = nItem.check(last);
                    if (valid) {
                        if (nItem.checkUsed(false)) {
                            normals += nItem.normalCodesInFileName();
                            usedItems++;
                        }
                    }
                    pos[0] += nItem.getDataSize();
                    bufferOffset += nItem.getDataSize();
                    sizeRemain[0] -= nItem.getDataSize();
                    indexNumber++;
                }

                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }

logger.log(Level.INFO, "normals: %.2f, usedItems: %d".formatted(normals, usedItems));
        double validRatio = 0.0;
        if (!valid) {
            validRatio = -1.0;
        } else if (usedItems > 0) {
            validRatio = normals / (double) usedItems;
        }

        return validRatio;
    }

    /** Whether directory is empty */
    public boolean isEmptyDirectory(boolean isRoot, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;
        boolean last = false;
        int indexNumber = 0;
        int[] pos = {0};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        DiskBasicDirItem<T> nItem = dir.newItem(null, 0, null, 0);

        for (int idx = 0; idx < groupItems.size() && finish >= -1; idx++) {
            DiskBasicGroupItem gItem = groupItems.get(idx);
            int grpNum = gItem.group;
            int trkNum = gItem.track;
            int sidNum = gItem.side;
            int divNum = gItem.divNum;
            int numOfDivs = gItem.numOfDivs;
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                valid = false;
                break;
            }
            DiskBasicGroupItem nextGitem;
            nextGitem = idx + 1 < groupItems.size() ? groupItems.get(idx + 1) : null;

            for (int secNum = gItem.sectorStart; secNum <= gItem.sectorEnd && valid && !last && finish >= -1; secNum++) {
                DiskImageSector sector = track.getSector(secNum);
                if (sector == null) {
                    valid = false;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    valid = false;
                    break;
                }
                int[] size = {sector.getSectorSize() / numOfDivs};
                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gItem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);

                int bufferOffset = (size[0] * divNum) + pos[0];
                if (grpNum != prevGrpNum) {
                    int skip = basic.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = grpNum;
                }
                if (idx == 0 && secNum == gItem.sectorStart) {
                    int skip = isRoot ? basic.getDirStartPosOnRoot() : basic.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }
                int skip = basic.getDirStartPosOnSector();
                bufferOffset += skip;
                pos[0] += skip;
                sizeRemain[0] -= skip;

                while (valid && !last && pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        sizeRemain[0] = size[0] - pos[0];
                        pos = size;
                        break;
                    }
                    nItem.setData(indexNumber, gItem, sector, pos[0], buffer, bufferOffset, nextSec);
                    if (nItem.isNormalFile()) {
                        valid = !nItem.checkUsed(last);
                    }
                    pos[0] += nItem.getDataSize();
                    bufferOffset += nItem.getDataSize();
                    sizeRemain[0] -= nItem.getDataSize();
                    indexNumber++;
                }
                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }
        return valid;
    }

    /** Assign directory */
    public boolean assignDirectory(boolean isRoot, DiskBasicGroups groupItems, DiskBasicDirItem<T> dirItem) throws IOException {
        int indexNumber = 0;
        int[] pos = {0};
        boolean[] unuse = {false};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        for (int idx = 0; idx < groupItems.size() && finish >= -1; idx++) {
            DiskBasicGroupItem gitem = groupItems.get(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum; // Division number
            int divNums = gitem.numOfDivs; // Number of divisions
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                continue;
            }
            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.size() ? groupItems.get(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && finish >= -1; secNum++) {
                DiskImageSector sector = track.getSector(secNum);
                if (sector == null) continue;

                byte[] buffer = sector.getSectorBuffer();
                int bufferOffset = 0;
                if (buffer == null) continue;

                int[] size = {sector.getSectorSize() / divNums};
                SectorParam nextSec = new SectorParam(trkNum, sidNum,
                        secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1),
                        -1);

                // Add offset
                bufferOffset += (size[0] * divNum);
                bufferOffset += pos[0];

                if (grpNum != prevGrpNum) {
                    // Position to skip when group number changes
                    int skip = basic.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = grpNum;
                }

                if (idx == 0 && secNum == gitem.sectorStart) {
                    // Position to skip the beginning of directory area
                    int skip = isRoot ? basic.getDirStartPosOnRoot() : basic.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }

                // Position to skip the beginning of each sector in directory area
                int skip = basic.getDirStartPosOnSector();
                bufferOffset += skip;
                pos[0] += skip;
                sizeRemain[0] -= skip;

                while (pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        sizeRemain[0] -= (size[0] - pos[0]);
                        // End. Position is at the beginning of next group.
                        pos[0] = size[0];
                        break;
                    }
                    DiskBasicDirItem<T> nitem = dir.newItem(indexNumber, gitem, sector, pos[0], buffer, bufferOffset, nextSec, unuse);
                    if (finish > 0) {
                        nitem.used(false);
                    }
                    // Set parent directory
                    nitem.setParent(dirItem);
                    // Add to child directory
                    dirItem.addChild(nitem);

                    pos[0] += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    sizeRemain[0] -= nitem.getDataSize();
                    indexNumber++;
                }

                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }

        return true;
    }

    /**
     * Initialize sectors as directory
     *
     * @param groupItems Allocated sector list
     * @param fileSize   [in,out] Size. Added to existing size when expanding directory.
     * @param sizeRemain [in,out] Remaining size
     * @param errInfo    [in,out] Error information
     * @return 0: Normal, <0: Error
     */
    public int initializeSectorsAsDirectory(DiskBasicGroups groupItems, int[] fileSize, int[] sizeRemain, DiskBasicError errInfo) throws IOException {
        int rc = 0;

        DiskBasicDirItem<?> newitem = basic.createDirItem(null, 0, null, 0);
        int dirSize = newitem.getDataSize();
        int indexNumber = 0;
        int[] pos = new int[1];
        int finish = 0;
        int prevGrpNum = -1;
        for (int idx = 0; idx < groupItems.size() && finish >= -1 && rc >= 0; idx++) {
            DiskBasicGroupItem gitem = groupItems.get(idx);
            int groupNum = gitem.group;
            int trackNum = gitem.track;
            int sideNum = gitem.side;
            int divNum = gitem.divNum; // Division number
            int numOfDivs = gitem.numOfDivs; // Number of divisions
            DiskImageTrack track = basic.getTrack(trackNum, sideNum);
            if (track == null) {
                // No track!
                errInfo.setError(ERRV_NO_TRACK, groupNum, trackNum, sideNum);
                rc = -2;
                break;
            }

            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.size() ? groupItems.get(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && finish >= -1 && rc >= 0; secNum++) {
                DiskImageSector sector = basic.getSector(trackNum, sideNum, secNum);
                if (sector == null) {
                    errInfo.setError(ERRV_NO_SECTOR, groupNum, trackNum, sideNum, secNum);
                    // No sector!
                    rc = -2;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                int bufferOffset = 0;
                if (buffer == null) {
                    errInfo.setError(ERRV_NO_SECTOR, groupNum, trackNum, sideNum, secNum);
                    // No sector!
                    rc = -2;
                    break;
                }

                int[] size = new int[] {sector.getSectorSize() / numOfDivs};

                SectorParam nextSec = new SectorParam(trackNum, sideNum, secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);
                // Add offset
                bufferOffset += (size[0] * divNum);
                bufferOffset += pos[0];

                if (groupNum != prevGrpNum) {
                    // Position to skip when group number changes
                    int skip = basic.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = groupNum;
                }

                if (idx == 0 && secNum == gitem.sectorStart) {
                    // Position to skip the beginning of directory area
                    int skip = basic.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }

                // Position to skip the beginning of each sector in directory area
                int skip = basic.getDirStartPosOnSector();
                bufferOffset += skip;
                pos[0] += skip;
                sizeRemain[0] -= skip;

                while (pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        // End. Position is at the beginning of next group.
                        fileSize[0] += (size[0] - pos[0]);
                        sizeRemain[0] -= (size[0] - pos[0]);
                        pos = size;
                        break;
                    }
                    // Write to disk
                    newitem.setData(indexNumber, gitem, sector, pos[0], buffer, bufferOffset, nextSec);
                    // Insert initial value
                    newitem.initialData();

                    pos[0] += dirSize;
                    bufferOffset += dirSize;
                    fileSize[0] += dirSize;
                    sizeRemain[0] -= dirSize;
                    indexNumber++;
                }

                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }

        return rc;
    }

    /**
     * Whether to end assigning if directory area size is reached
     *
     * @param pos        [in,out] Position of directory
     * @param size       [in,out] Sector size of directory
     * @param sizeRemain [in,out] Remaining size of directory
     * @return 0: Do not end, 1: Force unused, continue assign, -1: End assign at current group. Continue from next group, -2: Force end assign
     */
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        return 0;
    }

    /**
     * Adjust position for each sector during directory assignment
     *
     * @param pos Directory position
     * @return Adjusted directory position
     */
    public int adjustPositionAssigningDirectory(int pos) {
        return pos;
    }

    /** Get starting position of root directory */
    public void getStartNumOnRootDirectory(int[] trackNum, int[] sideNum, int[] sectorNum) {
        DiskImageTrack track = basic.getManagedTrack(basic.getDirStartSector() - basic.getSectorNumberBase(), sideNum, sectorNum);
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        }
    }

    /** Get ending position of root directory */
    public void getEndNumOnRootDirectory(int[] trackNum, int[] sideNum, int[] sectorNum) {
        DiskImageTrack track = basic.getManagedTrack(basic.getDirEndSector() - basic.getSectorNumberBase(), sideNum, sectorNum);
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        }
    }

    /** Get usable disk size */
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) throws IOException {
        groupSize[0] = 0;
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum != basic.getGroupSystemCode()) groupSize[0]++;
        }
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /** Calculate remaining disk size */
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        fatAvailability.empty();
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int fSize = 0;
            int grps = 0;
            int gNum = getGroupNumber(pos);
            FatAvailability fsts = FAT_AVAIL_USED;
            if (gNum == basic.getGroupUnusedCode()) {
                fSize = (basic.getSectorSize() * basic.getSectorsPerGroup());
                grps = 1;
                fsts = FAT_AVAIL_FREE;
            } else if (gNum == basic.getGroupSystemCode()) {
                fsts = FAT_AVAIL_SYSTEM;
            } else if (gNum >= basic.getGroupFinalCode()) {
                fsts = FAT_AVAIL_USED_LAST;
            }
            fatAvailability.add(fsts, fSize, grps);
        }
    }

    /** Clear remaining disk size */
    public void clearDiskFreeSize() {
        fatAvailability.emptyInit();
    }

    /** Get remaining disk size (result of calculation by calcDiskFreeSize()) */
    public void getFreeDiskSize(int[] diskSize, int[] groupSize) {
        diskSize[0] = fatAvailability.getFreeSize();
        groupSize[0] = fatAvailability.getFreeGroups();
    }

    /** Get remaining disk size (result of calculation by calcDiskFreeSize()) */
    public int getFreeDiskSize() {
        return fatAvailability.getFreeSize();
    }

    /** Get remaining group count (result of calculation by calcDiskFreeSize()) */
    public int getFreeGroupSize() {
        return fatAvailability.getFreeGroups();
    }

    /** Returns the free space status of FAT as an array */
    public void getFatAvailability(int[] offset, List<FatAvailability>[] result) {
        offset[0] = 0;
        result[0] = fatAvailability.list;
    }

    /** Allocate groups for data size */
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<T> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int groups = 0;
        int rc = 0;
        boolean firstGroup = flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW;
        int[] sizeRemain = {dataSize};
        int bytesPerGroup = basic.getSectorsPerGroup() * basic.getSectorSize();
        int groupNum = getEmptyGroupNumber();
        int limit = basic.getFatEndGroup() + 1;
        while (rc >= 0 && limit >= 0 && sizeRemain[0] > 0) {
            if (groupNum == INVALID_GROUP_NUMBER) {
                rc = firstGroup ? -1 : -2;
                break;
            }
            setGroupNumber(groupNum, basic.getGroupFinalCode());
            if (firstGroup) {
                item.setStartGroup(fileUnitNum, groupNum);
                firstGroup = false;
            }
            int nextGroupNum = getNextEmptyGroupNumber(groupNum);
            if (nextGroupNum == INVALID_GROUP_NUMBER || sizeRemain[0] <= bytesPerGroup) {
                nextGroupNum = calcLastGroupNumber(nextGroupNum, sizeRemain);
            }
            basic.getNumsFromGroup(groupNum, nextGroupNum, basic.getSectorSize(), sizeRemain[0], groupItems[0]);
            setGroupNumber(groupNum, nextGroupNum);
            groupNum = nextGroupNum;
            sizeRemain[0] -= bytesPerGroup;
            groups++;
            limit--;
        }
        if (limit < 0) {
            rc = firstGroup ? -1 : -2;
        }
        if (rc >= 0) {
            if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
                if (groupItems[0].size() > 0) {
                    rc = chainGroups(item.getStartGroup(0), groupItems[0].get(0).group);
                }
            }
        } else {
            deleteGroups(groupItems[0]);
            rc = -1;
        }
        return rc;
    }

    /** Allocate groups for data size */
    public int allocateGroups(DiskBasicDirItem<T> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        return allocateUnitGroups(0, item, dataSize, flags, groupItems);
    }

    /** Connect groups */
    public int chainGroups(int groupNum, int appendGroupNum) throws IOException {
        int limit = basic.getFatEndGroup() + 1;
        while (limit >= 0) {
            int nextGroupNum = getGroupNumber(groupNum);
            if (nextGroupNum >= basic.getGroupFinalCode()) {
                setGroupNumber(groupNum, appendGroupNum);
                break;
            }
            groupNum = nextGroupNum;
            limit--;
        }
        return (limit >= 0 ? 0 : -1);
    }

    /** Get starting sector number from group number */
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum * basic.getSectorsPerGroup();
    }

    /** Get final sector number from group number */
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int sectorEnd = sectorStart + basic.getSectorsPerGroup() - 1;
        if (nextGroup >= basic.getGroupFinalCode()) {
            sectorEnd = sectorStart + (nextGroup - basic.getGroupFinalCode());
        }
        return sectorEnd;
    }

    /** Calculate start sector of data area */
    public int calcDataStartSectorPos() {
        return 0;
    }

    /** Track number to skip */
    public int calcSkippedTrack() {
        return 0x7fff;
    }

    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum) {
        getNumFromSectorPos(sectorPos, trackNum, sideNum, sectorNum, null, null);
    }

    /** Get track, side, and sector numbers from sector position (serial number where track 0, side 0, sector 1 is 0) */
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] divNums) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();

        if (selectedSide >= 0) {
            trackNum[0] = sectorPos / sectorsPerTrack;
            sideNum[0] = selectedSide;
        } else {
            trackNum[0] = sectorPos / sectorsPerTrack / sidesPerDisk;
            sideNum[0] = (sectorPos / sectorsPerTrack) % sidesPerDisk;
        }
        sectorNum[0] = (sectorPos % sectorsPerTrack);

        if (numberingSector == 1) {
            sectorNum[0] += (sideNum[0] * sectorsPerTrack);
        }

        sideNum[0] = basic.getReversedSideNumber(sideNum[0]);
        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();

        if (divNum != null) divNum[0] = 0;
        if (divNums != null) divNums[0] = 1;
    }

    /** Get track and sector numbers from sector position (serial number where track 0, side 0, sector 1 is 0) */
    public void getNumFromSectorPosS(int sectorPos, int[] trackNum, int[] sectorNum) {
        int selectedSide = basic.getSelectedSide();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        if (selectedSide >= 0) {
            trackNum[0] = sectorPos / sectorsPerTrack;
            sectorNum[0] = (sectorPos % sectorsPerTrack);
        } else {
            trackNum[0] = sectorPos / (sectorsPerTrack * sidesPerDisk);
            sectorNum[0] = (sectorPos % (sectorsPerTrack * sidesPerDisk));
        }
        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();
    }

    /** Get sector position (serial number where track 0, side 0, sector 1 is 0) from track and sector numbers */
    public void getNumFromSectorPosT(int sectorPos, int[] trackNum, int[] sectorNum) {
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        trackNum[0] = sectorPos / sectorsPerTrack;
        sectorNum[0] = (sectorPos % sectorsPerTrack);
        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();
    }

    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum) {
        return getSectorPosFromNum(trackNum, sideNum, sectorNum, 0, 0);
    }

    /** Get sector position (serial number where track 0, side 0, sector 1 is 0) from track, side, and sector numbers */
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        sideNum = basic.getReversedSideNumber(sideNum);

        if (selectedSide >= 0) {
            sectorPos = trackNum * sectorsPerTrack + sectorNum;
        } else {
            sectorPos = trackNum * sectorsPerTrack * sidesPerDisk;
            sectorPos += (sideNum % sidesPerDisk) * sectorsPerTrack;
            if (numberingSector == 1) {
                sectorPos += (sectorNum % sectorsPerTrack);
            } else {
                sectorPos += sectorNum;
            }
        }
        return sectorPos;
    }

    /** Get sector position (serial number where track 0, side 0, sector 1 is 0) from track and sector numbers */
    public int getSectorPosFromNumS(int trackNum, int sectorNum) {
        int selectedSide = basic.getSelectedSide();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        if (selectedSide >= 0) {
            sectorPos = trackNum * sectorsPerTrack + sectorNum;
        } else {
            sectorPos = trackNum * sectorsPerTrack * sidesPerDisk + sectorNum;
        }
        return sectorPos;
    }

    /** Get sector position (serial number starting from 0 for the first track & sector) from track and sector numbers */
    public int getSectorPosFromNumT(int trackNum, int sectorNum) {
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();
        return trackNum * sectorsPerTrack + sectorNum;
    }

    /** Whether it is the root directory */
    public boolean isRootDirectory(int groupNum) {
        return true;
    }

    /** Whether a subdirectory can be created */
    public boolean canMakeDirectory() {
        return false;
    }

    /** Whether root directory size can be expanded */
    public boolean canExpandRootDirectory() {
        return false;
    }

    /** Whether subdirectory size can be expanded */
    public boolean canExpandDirectory() {
        return false;
    }

    /** Edit directory name before creating subdirectory */
    public boolean renameOnMakingDirectory(String[] dirName) {
        return true;
    }

    /** Prepare before creating subdirectory */
    public boolean prepareToMakeDirectory(DiskBasicDirItem<T> item) throws IOException {
        return true;
    }

    /** Returns an unused directory item */
    public DiskBasicDirItem<T> getEmptyDirectoryItem(DiskBasicDirItem<T> parent, List<DiskBasicDirItem<T>> items, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        DiskBasicDirItem<T> matchItem = null;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                DiskBasicDirItem<T> item = items.get(i);
                if (!item.isUsed()) {
                    matchItem = item;
                    if (nextItem != null) {
                        i++;
                        if (i < items.size() && !items.get(i).isUsed()) {
                            nextItem[0] = items.get(i);
                        } else {
                            nextItem[0] = null;
                        }
                    }
                    break;
                }
            }
        }
        return matchItem;
    }

    /** Release directory item (model dependent) */
    public void releaseDirectoryItem(DiskBasicDirItem<T> item) {
    }

    /** Individual processing after creating subdirectory */
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<T> item, DiskBasicGroups groupItems, DiskBasicDirItem<T> parentItem) throws IOException {
    }

    /** Individual processing after directory expansion */
    public boolean additionalProcessOnExpandedDirectory(DiskBasicDirItem<T> item, DiskBasicGroups groupItems, DiskBasicDirItem<T> parentItem) {
        return true;
    }

    /** Whether formatting is possible */
    public boolean supportFormatting() {
        return true;
    }

    /** Fill sector data with specified code */
    public void fillSector(DiskImageTrack track, DiskImageSector sector) throws IOException {
        sector.fill(basic.getFillCodeOnFormat());
    }

    /** Individual processing after filling sector data */
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        return true;
    }

    /** Determine the data size of the last sector of the file */
    public int calcDataSizeOnLastSector(DiskBasicDirItem<T> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) throws IOException {
        return remainSize;
    }

    /** Pre-processing for data read/comparison */
    public boolean prepareToAccessFile(int fileUnitNum, DiskBasicDirItem<T> item, InputStream iStream, OutputStream oStream, int[] fileSize, DiskBasicGroups groupItems, DiskBasicError errInfo) {
        return true;
    }

    /** Data read/comparison processing */
    public int accessFile(int fileUnitNum, DiskBasicDirItem<T> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        int modifiedSize = sectorSize;
        if (remainSize <= sectorSize) {
            // Last sector of the file
            modifiedSize = calcDataSizeOnLastSector(item, iStream, oStream, sectorBuffer, 0, sectorSize, remainSize);
        }
        if (modifiedSize < 0) {
            // No sector
            return -2;
        }

        if (modifiedSize > 0) {
            byte[] temp;
            if (oStream != null) {
                // Writing out
                temp = Arrays.copyOfRange(sectorBuffer, 0, modifiedSize);
                if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);
                oStream.write(temp, 0, temp.length);
            }
            if (iStream != null) {
                // Read and compare
                temp = new byte[modifiedSize];
                iStream.readNBytes(temp, 0, temp.length);
                if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

                if (!Arrays.equals(Arrays.copyOf(temp, temp.length), Arrays.copyOf(sectorBuffer, temp.length))) {
                    // Data is different
                    return -1;
                }
            }
        }

        return sectorSize;
    }

    /** Convert contents when exporting an internal file */
    public boolean convertDataForLoad(DiskBasicDirItem<T> item, InputStream iStream, OutputStream oStream) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = iStream.read(buffer)) != -1) {
            oStream.write(buffer, 0, len);
        }
        return true;
    }

    /** Convert contents when verifying an exported file */
    public boolean convertDataForVerify(DiskBasicDirItem<T> item, InputStream iStream, OutputStream oStream) throws IOException {
        return convertDataForLoad(item, iStream, oStream);
    }

    /** Whether writing is possible */
    public boolean supportWriting() {
        return true;
    }

    /** Whether the specified size can be sufficiently written */
    public boolean isEnoughFileSize(int size) {
        return true;
    }

    /** Convert data before saving file */
    public boolean convertDataForSave(DiskBasicDirItem<T> item, InputStream iStream, OutputStream oStream) throws IOException {
        return convertDataForLoad(item, iStream, oStream);
    }

    /** Calculate the last group number when allocating groups */
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        return groupNum;
    }

    /** Prepare before saving file */
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T> nItem, DiskBasicError errInfo) throws IOException {
        return true;
    }

    /** Data write process */
    public int writeFile(DiskBasicDirItem<T> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needEofCode = item.needCheckEofCode();
        int len = 0;
        if (remain <= size) {
            // Few left
            if (remain < 0) remain = 0;
            if (needEofCode) {
                // Final is termination code
                if (remain > 1) iStream.read(buffer, 0, remain - 1);
                if (remain > 0) buffer[remain - 1] = item.getEofCode();
            } else {
                if (remain > 0) iStream.read(buffer, 0, remain);
            }
            if (size > remain) {
                // Remaining buffer is zero-suppressed
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // Continuous
            iStream.read(buffer, 0, size);
            len = size;
        }

        // Invert
        basic.invertMemory(buffer, size);

        return len;
    }

    /** Processing after data write completion */
    public void additionalProcessOnSavedFile(DiskBasicDirItem<T> item) throws IOException {
    }

    /** Processing after file name change */
    public void additionalProcessOnRenamedFile(DiskBasicDirItem<T> item) throws IOException {
    }

    /** Processing after attribute change */
    public void additionalProcessOnChangedAttr(DiskBasicDirItem<T> item) {
    }

    /** Whether file can be deleted */
    public boolean supportDeleting() {
        return true;
    }

    /** Delete FAT area for specified group number */
    public void deleteGroups(DiskBasicGroups groupItems) throws IOException {
        for (DiskBasicGroupItem item : groupItems.getItems()) {
            // Delete FAT entry
            deleteGroupNumber(item.group);
        }
    }

    /** Delete FAT area for specified group number */
    public void deleteGroupNumber(int groupNum) throws IOException {
        // Set unused code in FAT
        setGroupNumber(groupNum, basic.getGroupUnusedCode());
    }

    /** Processing after file deletion */
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<T> item) throws IOException {
        return true;
    }

    /** Get attributes of IPL and managed area */
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
    }

    /** Set attributes of IPL and managed area */
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
    }

    /** Set start group of management area */
    public void setManagedStartGroup(int val) {
        managedStartGroup = val;
    }
}
