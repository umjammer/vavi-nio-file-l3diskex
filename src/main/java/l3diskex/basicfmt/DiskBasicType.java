package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam.NumSectorsParam;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;


public abstract class DiskBasicType<T extends DirectoryT>  {

    public enum AllocateGroupFlags {
        ALLOCATE_GROUPS_NEW,
        ALLOCATE_GROUPS_APPEND
    }

    public static class DiskBasicTempData extends Utils.TempData {
        private byte[] data;
        private int size;

        @Override
        public void setData(byte[] data, int size, boolean inverted) {}
        @Override
        public byte[] getData() { return data; }
        @Override
        public int getSize() { return size; }
        @Override
        public void setSize(int size) { this.size = size; }
        @Override
        public void invertData(boolean inverted) {}
    }

    public static class SectorSkewBase implements Cloneable {
        protected int numSecs;

        public SectorSkewBase() {
            numSecs = 0;
        }

        public SectorSkewBase(SectorSkewBase src) {
            numSecs = src.numSecs;
        }

        @Override
        public SectorSkewBase clone() {
            try {
                return (SectorSkewBase) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }

        public void delete() {}

        public void create(DiskBasic basic, int numSecs) {
            this.numSecs = numSecs;
        }

        public void mapping(DiskBasic basic) {}

        public int toPhysical(int val) {
            return 0;
        }

        public int toLogical(int val) {
            return 0;
        }
    }

    public static class DiskBasicSectorSkew extends SectorSkewBase {
        protected int[] ltopMap;
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

        @Override
        public SectorSkewBase clone() {
            return new DiskBasicSectorSkew(this);
        }

        @Override
        public void delete() {
            ltopMap = null;
            ptolMap = null;
        }

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
                psec = basic.diskBasicParam.getSectorSkewMap(lsec);
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
                    for (int limit = numSecs; ptolMap[psec] >= 0 && limit > 0; limit--) {
                        psec++;
                    }
                }
            }
        }

        @Override
        public void mapping(DiskBasic basic) {
            if (basic.diskBasicParam.hasSectorSkewMap()) {
                mappingFromParam(basic);
            } else {
                mappingFromCalc(basic, basic.diskBasicParam.getSectorSkew());
            }
        }

        @Override
        public int toPhysical(int val) {
            if (ltopMap != null) val = ltopMap[val];
            return val;
        }

        @Override
        public int toLogical(int val) {
            if (ptolMap != null) val = ptolMap[val];
            return val;
        }
    }

    public static class DiskBasicSectorSkewForSave extends DiskBasicSectorSkew {
        public DiskBasicSectorSkewForSave() {
            super();
        }

        public DiskBasicSectorSkewForSave(DiskBasicSectorSkewForSave src) {
            super(src);
        }

        @Override
        public SectorSkewBase clone() {
            return new DiskBasicSectorSkewForSave(this);
        }

        @Override
        public void mapping(DiskBasic basic) {
            mappingFromCalc(basic, basic.diskBasicParam.getVariousIntegerParam("SectorSkewForSave"));
        }
    }

    public static class SectorsPerTrack {
        private final int numOfTracks;
        private final int numOfSectors;
        private final int totalSectors;
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

        public int getNumOfTracks() { return numOfTracks; }
        public int getNumOfSectors() { return numOfSectors; }
        public int getTotalSectors() { return totalSectors; }
        public void setSectorSkewMap(SectorSkewBase map) { ssmap = map; }
        public SectorSkewBase getSectorSkewMap() { return ssmap; }
    }

    public static class DiskBasicSectorPosTrans extends ArrayList<SectorsPerTrack> {
        public void create(DiskBasic basic) {
            clear();
            List<NumSectorsParam> sp = basic.diskBasicParam.sectorsPerTrackOnBasicList();
            if (!sp.isEmpty()) {
                for (NumSectorsParam p : sp) {
                    add(new SectorsPerTrack(p.getNumberOfTracks(), p.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic(), p.getNumberOfTracks() * p.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic()));
                }
            } else if (basic.isVariableSectorsPerTrack()) {
                int numOfTracks = 0;
                int prevNumOfSectors = 0;
                int totalSectors = 0;
                int trk = basic.getTrackNumberBaseOnDisk();
                int trks = basic.diskBasicParam.getTracksPerSideOnBasic() + trk;
                for (; trk < trks; trk++) {
                    for (int sid = 0; sid < basic.diskBasicParam.getSidesPerDiskOnBasic(); sid++) {
                        DiskImageTrack track = basic.getTrack(trk, sid);
                        if (track == null) {
                            continue;
                        }
                        int numOfSectors = track.getSectorsPerTrack() * basic.diskBasicParam.getSidesPerDiskOnBasic();
                        if (prevNumOfSectors == 0) {
                            totalSectors = 0;
                            prevNumOfSectors = numOfSectors;
                            numOfTracks = 0;
                        } else if (prevNumOfSectors != numOfSectors) {
                            add(new SectorsPerTrack(numOfTracks, prevNumOfSectors, totalSectors));
                            totalSectors = 0;
                            prevNumOfSectors = numOfSectors;
                            numOfTracks = 0;
                        }
                        totalSectors += numOfSectors;
                    }
                    numOfTracks++;
                }
                add(new SectorsPerTrack(numOfTracks, prevNumOfSectors, totalSectors));
            } else {
                add(new SectorsPerTrack(basic.diskBasicParam.getTracksPerSideOnBasic(), basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic(), basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic()));
            }
        }

        public void createSectorSkewMap(DiskBasic basic) {}

        public SectorsPerTrack findByTrackNum(int trackNum) {
            SectorsPerTrack match = null;
            for (SectorsPerTrack item : this) {
                if (trackNum < item.getNumOfTracks()) {
                    match = item;
                    break;
                }
                trackNum -= item.getNumOfTracks();
            }
            return match;
        }

        public int getTotalSectors() {
            int val = 0;
            for (SectorsPerTrack item : this) {
                val += item.getTotalSectors();
            }
            return val;
        }

        public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sectorNum, int[] numOfSectors) {
            trackNum[0] = 0;
            for (SectorsPerTrack item : this) {
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

        public int getSectorPosFromNum(int trackNum, int sectorNum, int[] numOfSectors) {
            int sectorPos = 0;
            for (SectorsPerTrack item : this) {
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
    }

    protected DiskBasic basic;
    protected DiskBasicFat fat;
    protected DiskBasicDir dir;

    protected int managedStartGroup;
    protected int dataStartGroup;

    protected DiskBasicAvailability fatAvailability = new DiskBasicAvailability();
    protected DiskBasicTempData temp = new DiskBasicTempData();

    protected DiskBasicType() {}

    public DiskBasicType(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        this.basic = basic;
        this.fat = fat;
        this.dir = dir;
        this.dataStartGroup = 0;
    }

    public void setGroupNumber(int num, int val) throws IOException {}

    public int getGroupNumber(int num) { return 0; }

    public boolean isUsedGroupNumber(int num) { return true; }

    public int getNextGroupNumber(int num, int sectorPos) { return 0; }

    public int getEmptyGroupNumber() throws IOException {
        int newNum = INVALID_GROUP_NUMBER;
        for (int num = 0; num <= basic.diskBasicParam.getFatEndGroup(); num++) {
            int gnum = getGroupNumber(num);
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    public int getNextEmptyGroupNumber(int currGroup) throws IOException {
        int newNum = INVALID_GROUP_NUMBER;
        int secsPerGrp = basic.diskBasicParam.getSectorsPerGroup();
        int secsPerTrk = basic.diskBasicParam.getSectorsPerTrackOnBasic();
        int sides = basic.diskBasicParam.getSidesPerDiskOnBasic();

        int sed = secsPerTrk * sides / secsPerGrp;
        if (sed == 0) return INVALID_GROUP_NUMBER;
        int groupMax = (basic.diskBasicParam.getFatEndGroup() / sed) + 1;
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
                    if (num > basic.diskBasicParam.getFatEndGroup()) {
                        break;
                    }
                    int gnum = getGroupNumber(num);
                    if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
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

    public double parseParamOnDisk(boolean isFormatting) throws IOException { return 1.0; }

    public double checkFat(boolean isFormatting) throws IOException { return 1.0; }

    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int secPos = basic.diskBasicParam.getFatStartSector() - 1;
        int secFat = basic.diskBasicParam.getSectorsPerFat();
        DiskImageTrack track = null;
        if (secPos >= 0 && secFat > 0) {
            if (basic.diskBasicParam.getFatSideNumber() >= 0) secPos += basic.diskBasicParam.getFatSideNumber() * basic.diskBasicParam.getSectorsPerTrackOnBasic();
            track = basic.getManagedTrack(secPos, sideNum, sectorNum);
        }
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        } else {
            trackNum[0] = -1;
        }
    }

    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int secSta = basic.diskBasicParam.getFatStartSector() - 1;
        int secPos = secSta + basic.diskBasicParam.getSectorsPerFat() * basic.diskBasicParam.getNumberOfFats() - 1;
        int secFat = basic.diskBasicParam.getSectorsPerFat();
        DiskImageTrack track = null;
        if (secSta >= 0 && secFat > 0) {
            if (basic.diskBasicParam.getFatSideNumber() >= 0) secPos += basic.diskBasicParam.getFatSideNumber() * basic.diskBasicParam.getSectorsPerTrackOnBasic();
            track = basic.getManagedTrack(secPos, sideNum, sectorNum);
        }
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        } else {
            trackNum[0] = -1;
        }
    }

    public String getTitleForFat() { return "FAT"; }

    public int calcManagedStartGroup() {
        int trk = basic.getManagedTrackNumber();
        int sid = basic.diskBasicParam.getFatSideNumber();
        if (sid < 0) sid = 0;
        int sides = basic.diskBasicParam.getSidesPerDiskOnBasic();
        int secsPerGrp = basic.diskBasicParam.getSectorsPerGroup();
        int secsPerTrk = basic.diskBasicParam.getSectorsPerTrackOnBasic();
        managedStartGroup = (trk * sides + sid) * secsPerTrk / secsPerGrp;
        return managedStartGroup;
    }

    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        groupItems.empty();
        int dirSize = 0;
        int sectorBase = basic.getSectorNumberBase();
        for (int secPos = startSector - sectorBase; secPos <= endSector - sectorBase; secPos++) {
            int[] trkNum = new int[1], sidNum = new int[1], secNum = new int[1], divNum = new int[1], divNums = new int[1];
            secNum[0] = 1; divNums[0] = 1;
            DiskImageSector sector = basic.getManagedSector(secPos, trkNum, sidNum, secNum, divNum, divNums);
            if (sector == null) continue;
            groupItems.add(secPos, 0, trkNum[0], sidNum[0], secNum[0], secNum[0], divNum[0], divNums[0]);
            dirSize += (sector.getSectorSize() / divNums[0]);
        }
        groupItems.setSize(dirSize);
        return true;
    }

    public double checkRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;
        double validRatio = -1.0;
        if (calcGroupsOnRootDirectory(startSector, endSector, groupItems)) {
            validRatio = checkDirectory(true, groupItems);
        }
        return validRatio;
    }

    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<T> dirItem) throws IOException {
        calcGroupsOnRootDirectory(startSector, endSector, groupItems);
        return assignDirectory(true, groupItems, dirItem);
    }

    public double checkDirectory(boolean isRoot, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;
        boolean[] last = {false};
        int nUsedItems = 0;
        double nNormals = 0.0;
        int indexNumber = 0;
        int[] pos = {0};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        DiskBasicDirItem<T> nitem = dir.newItem(null, 0, null);

        for (int idx = 0; idx < groupItems.count() && finish >= -1; idx++) {
            DiskBasicGroupItem gitem = groupItems.itemPtr(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum;
            int divNums = gitem.divNums;
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                valid = false;
                break;
            }
            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.count() ? groupItems.itemPtr(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && finish >= -1; secNum++) {
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

                int[] size = {sector.getSectorSize() / divNums};
                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);

                int bufferOffset = (size[0] * divNum) + pos[0];

                if (grpNum != prevGrpNum) {
                    bufferOffset += basic.diskBasicParam.getDirStartPosOnGroup();
                    pos[0] += basic.diskBasicParam.getDirStartPosOnGroup();
                    sizeRemain[0] -= basic.diskBasicParam.getDirStartPosOnGroup();
                    prevGrpNum = grpNum;
                }

                if (idx == 0 && secNum == gitem.sectorStart) {
                    int skip = isRoot ? basic.diskBasicParam.getDirStartPosOnRoot() : basic.diskBasicParam.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }

                bufferOffset += basic.diskBasicParam.getDirStartPosOnSector();
                pos[0] += basic.diskBasicParam.getDirStartPosOnSector();
                sizeRemain[0] -= basic.diskBasicParam.getDirStartPosOnSector();

                while (valid && !last[0] && pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        sizeRemain[0] = size[0] - pos[0];
                        pos[0] = size[0];
                        break;
                    }
                    // This logic is tricky to translate without the full context of SetDataPtr.
                    // Assuming it needs buffer and offset.
                    nitem.setDataPtr(indexNumber, gitem, sector, pos[0], buffer, nextSec); // Buffer should be passed with offset
                    valid = nitem.check(last);
                    if (valid) {
                        if (nitem.checkUsed(false)) {
                            nNormals += nitem.normalCodesInFileName();
                            nUsedItems++;
                        }
                    }
                    pos[0] += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    sizeRemain[0] -= nitem.getDataSize();
                    indexNumber++;
                }
                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }
        double validRatio = 0.0;
        if (!valid) {
            validRatio = -1.0;
        } else if (nUsedItems > 0) {
            validRatio = nNormals / (double) nUsedItems;
        }
        return validRatio;
    }

    public boolean isEmptyDirectory(boolean isRoot, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;
        boolean last = false;
        int indexNumber = 0;
        int[] pos = {0};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        DiskBasicDirItem<T> nitem = dir.newItem(null, 0, null);

        for (int idx = 0; idx < groupItems.count() && finish >= -1; idx++) {
            DiskBasicGroupItem gitem = groupItems.itemPtr(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum;
            int divNums = gitem.divNums;
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                valid = false;
                break;
            }
            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.count() ? groupItems.itemPtr(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && valid && !last && finish >= -1; secNum++) {
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
                int[] size = {sector.getSectorSize() / divNums};
                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);

                int bufferOffset = (size[0] * divNum) + pos[0];
                if (grpNum != prevGrpNum) {
                    int skip = basic.diskBasicParam.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = grpNum;
                }
                if (idx == 0 && secNum == gitem.sectorStart) {
                    int skip = isRoot ? basic.diskBasicParam.getDirStartPosOnRoot() : basic.diskBasicParam.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }
                int skip = basic.diskBasicParam.getDirStartPosOnSector();
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
                    nitem.setDataPtr(indexNumber, gitem, sector, pos[0], buffer, nextSec); // Buffer with offset
                    if (nitem.isNormalFile()) {
                        valid = !nitem.checkUsed(last);
                    }
                    pos[0] += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    sizeRemain[0] -= nitem.getDataSize();
                    indexNumber++;
                }
                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }
        return valid;
    }

    public boolean assignDirectory(boolean isRoot, DiskBasicGroups groupItems, DiskBasicDirItem<T> dirItem) throws IOException {
        int indexNumber = 0;
        int[] pos = {0};
        boolean[] unuse = {false};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        for (int idx = 0; idx < groupItems.count() && finish >= -1; idx++) {
            DiskBasicGroupItem gitem = groupItems.itemPtr(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum;
            int divNums = gitem.divNums;
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                continue;
            }
            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.count() ? groupItems.itemPtr(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && finish >= -1; secNum++) {
                DiskImageSector sector = track.getSector(secNum);
                if (sector == null) continue;

                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) continue;

                int[] size = {sector.getSectorSize() / divNums};
                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);

                int bufferOffset = (size[0] * divNum) + pos[0];

                if (grpNum != prevGrpNum) {
                    int skip = basic.diskBasicParam.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = grpNum;
                }

                if (idx == 0 && secNum == gitem.sectorStart) {
                    int skip = isRoot ? basic.diskBasicParam.getDirStartPosOnRoot() : basic.diskBasicParam.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }

                int skip = basic.diskBasicParam.getDirStartPosOnSector();
                bufferOffset += skip;
                pos[0] += skip;
                sizeRemain[0] -= skip;

                while (pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        sizeRemain[0] -= (size[0] - pos[0]);
                        pos[0] = size[0];
                        break;
                    }
                    DiskBasicDirItem<T> nitem = dir.newItem(indexNumber, gitem, sector, pos[0], buffer, nextSec, unuse);
                    if (finish > 0) {
                        nitem.used(false);
                    }
                    nitem.setParent(dirItem);
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

    public int initializeSectorsAsDirectory(DiskBasicGroups groupItems, int[] fileSize, int[] sizeRemain, DiskBasicError errinfo) throws IOException {
        int rc = 0;
        DiskBasicDirItem<?> newitem = basic.createDirItem(null, 0, null);
        int dirSize = newitem.getDataSize();
        int indexNumber = 0;
        int[] pos = new int[1];
        int finish = 0;
        int prevGrpNum = -1;
        for (int idx = 0; idx < groupItems.count() && finish >= -1 && rc >= 0; idx++) {
            DiskBasicGroupItem gitem = groupItems.item(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum;
            int divNums = gitem.divNums;
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                errinfo.setError(0, grpNum, trkNum, sidNum); // ERRV_NO_TRACK
                rc = -2;
                break;
            }
            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.count() ? groupItems.itemPtr(idx + 1) : null;
            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && finish >= -1 && rc >= 0; secNum++) {
                DiskImageSector sector = basic.getSector(trkNum, sidNum, secNum);
                if (sector == null) {
                    errinfo.setError(1, grpNum, trkNum, sidNum, secNum); // ERRV_NO_SECTOR
                    rc = -2;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    errinfo.setError(1, grpNum, trkNum, sidNum, secNum); // ERRV_NO_SECTOR
                    rc = -2;
                    break;
                }
                int[] size = new int[] {sector.getSectorSize() / divNums};
                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);

                int bufferOffset = (size[0] * divNum) + pos[0];

                if (grpNum != prevGrpNum) {
                    int skip = basic.diskBasicParam.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = grpNum;
                }
                if (idx == 0 && secNum == gitem.sectorStart) {
                    int skip = basic.diskBasicParam.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }
                int skip = basic.diskBasicParam.getDirStartPosOnSector();
                bufferOffset += skip;
                pos[0] += skip;
                sizeRemain[0] -= skip;

                while (pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        fileSize[0] += (size[0] - pos[0]);
                        sizeRemain[0] -= (size[0] - pos[0]);
                        pos = size;
                        break;
                    }
                    newitem.setDataPtr(indexNumber, gitem, sector, pos[0], buffer, nextSec); // Pass buffer with offset
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

    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) { return 0; }

    public int adjustPositionAssigningDirectory(int pos) { return pos; }

    public void getStartNumOnRootDirectory(int[] trackNum, int[] sideNum, int[] sectorNum) {
        DiskImageTrack track = basic.getManagedTrack(basic.diskBasicParam.getDirStartSector() - basic.getSectorNumberBase(), sideNum, sectorNum);
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        }
    }

    public void getEndNumOnRootDirectory(int[] trackNum, int[] sideNum, int[] sectorNum) {
        DiskImageTrack track = basic.getManagedTrack(basic.diskBasicParam.getDirEndSector() - basic.getSectorNumberBase(), sideNum, sectorNum);
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        }
    }

    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = 0;
        for (int pos = 0; pos <= basic.diskBasicParam.getFatEndGroup(); pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum != basic.diskBasicParam.getGroupSystemCode()) groupSize[0]++;
        }
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup();
    }

    public void calcDiskFreeSize(boolean wrote) throws IOException {
        fatAvailability.empty();
        for (int pos = 0; pos <= basic.diskBasicParam.getFatEndGroup(); pos++) {
            int fsize = 0;
            int grps = 0;
            int gnum = getGroupNumber(pos);
            int fsts = FAT_AVAIL_USED.getValue();
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                fsize = (basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup());
                grps = 1;
                fsts = FAT_AVAIL_FREE.getValue();
            } else if (gnum == basic.diskBasicParam.getGroupSystemCode()) {
                fsts = FAT_AVAIL_SYSTEM.getValue();
            } else if (gnum >= basic.diskBasicParam.getGroupFinalCode()) {
                fsts = FAT_AVAIL_USED_LAST.getValue();
            }
            fatAvailability.Add(fsts, fsize, grps);
        }
    }

    public void clearDiskFreeSize() { fatAvailability.emptyInit(); }

    public void getFreeDiskSize(int[] diskSize, int[] groupSize) {
        diskSize[0] = fatAvailability.getFreeSize();
        groupSize[0] = fatAvailability.getFreeGroups();
    }

    public int getFreeDiskSize() { return fatAvailability.getFreeSize(); }

    public int getFreeGroupSize() { return fatAvailability.getFreeGroups(); }

    public void getFatAvailability(int[] offset, List<Integer>[] arr) {
        offset[0] = 0;
        arr[0] = fatAvailability;
    }

    public int allocateUnitGroups(int fileunitNum, DiskBasicDirItem<T> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups groupItems) throws IOException {
        int groups = 0;
        int rc = 0;
        boolean firstGroup = (flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW);
        int[] sizeremain = {dataSize};
        int bytesPerGroup = basic.getSectorsPerGroup() * basic.getSectorSize();
        int groupNum = getEmptyGroupNumber();
        int limit = basic.getFatEndGroup() + 1;
        while (rc >= 0 && limit >= 0 && sizeremain[0] > 0) {
            if (groupNum == INVALID_GROUP_NUMBER) {
                rc = firstGroup ? -1 : -2;
                break;
            }
            setGroupNumber(groupNum, basic.diskBasicParam.getGroupFinalCode());
            if (firstGroup) {
                item.setStartGroup(fileunitNum, groupNum);
                firstGroup = false;
            }
            int nextGroupNum = getNextEmptyGroupNumber(groupNum);
            if (nextGroupNum == INVALID_GROUP_NUMBER || sizeremain[0] <= bytesPerGroup) {
                nextGroupNum = calcLastGroupNumber(nextGroupNum, sizeremain);
            }
            basic.getNumsFromGroup(groupNum, nextGroupNum, basic.getSectorSize(), sizeremain[0], groupItems);
            setGroupNumber(groupNum, nextGroupNum);
            groupNum = nextGroupNum;
            sizeremain[0] -= bytesPerGroup;
            groups++;
            limit--;
        }
        if (limit < 0) {
            rc = firstGroup ? -1 : -2;
        }
        if (rc >= 0) {
            if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
                if (groupItems.count() > 0) {
                    rc = chainGroups(item.getStartGroup(0), groupItems.item(0).group);
                }
            }
        } else {
            deleteGroups(groupItems);
            rc = -1;
        }
        return rc;
    }

    public int allocateGroups(DiskBasicDirItem<T> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups groupItems) throws IOException {
        return allocateUnitGroups(0, item, dataSize, flags, groupItems);
    }

    public int chainGroups(int groupNum, int appendGroupNum) throws IOException {
        int limit = basic.getFatEndGroup() + 1;
        while (limit >= 0) {
            int nextGroupNum = getGroupNumber(groupNum);
            if (nextGroupNum >= basic.diskBasicParam.getGroupFinalCode()) {
                setGroupNumber(groupNum, appendGroupNum);
                break;
            }
            groupNum = nextGroupNum;
            limit--;
        }
        return (limit >= 0 ? 0 : -1);
    }

    public int getStartSectorFromGroup(int groupNum) {
        return groupNum * basic.getSectorsPerGroup();
    }

    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int sectorEnd = sectorStart + basic.getSectorsPerGroup() - 1;
        if (nextGroup >= basic.diskBasicParam.getGroupFinalCode()) {
            sectorEnd = sectorStart + (nextGroup - basic.diskBasicParam.getGroupFinalCode());
        }
        return sectorEnd;
    }

    public int calcDataStartSectorPos() { return 0; }

    public int calcSkippedTrack() { return 0x7fff; }

    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum) {
        getNumFromSectorPos(sectorPos, trackNum, sideNum, sectorNum, null, null);
    }

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

    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int divNums) {
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

    public int getSectorPosFromNumT(int trackNum, int sectorNum) {
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();
        return trackNum * sectorsPerTrack + sectorNum;
    }

    public boolean isRootDirectory(int groupNum) { return true; }

    public boolean canMakeDirectory() { return false; }

    public boolean canExpandRootDirectory() { return false; }

    public boolean canExpandDirectory() { return false; }

    public boolean renameOnMakingDirectory(String dirName) { return true; }

    public boolean prepareToMakeDirectory(DiskBasicDirItem<T> item) throws IOException { return true; }

    public DiskBasicDirItem<T> getEmptyDirectoryItem(DiskBasicDirItem<T> parent, List<DiskBasicDirItem<T>> items, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nextItem) throws IOException {
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

    public void releaseDirectoryItem(DiskBasicDirItem<T> item) {}

    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<T> item, DiskBasicGroups groupItems, DiskBasicDirItem<T> parentItem) throws IOException {}

    public boolean additionalProcessOnExpandedDirectory(DiskBasicDirItem<T> item, DiskBasicGroups groupItems, DiskBasicDirItem<T> parentItem) { return true; }

    public boolean supportFormatting() { return true; }

    public void fillSector(DiskImageTrack track, DiskImageSector sector) throws IOException {
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException { return true; }

    public int calcDataSizeOnLastSector(DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream, byte[] sectorBuffer, int sectorOffsrt, int sectorSize, int remainSize) throws IOException {
        return remainSize;
    }

    public boolean prepareToAccessFile(int fileunitNum, DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream, int[] fileSize, DiskBasicGroups groupItems, DiskBasicError errinfo) {
        return true;
    }

    public int accessFile(int fileunitNum, DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream, byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        int modifiedSize = sectorSize;
        if (remainSize <= sectorSize) {
            modifiedSize = calcDataSizeOnLastSector(item, istream, ostream, sectorBuffer, 0, sectorSize, remainSize);
        }
        if (modifiedSize < 0) {
            return -2;
        }
        if (modifiedSize > 0) {
            if (ostream != null) {
                temp.setData(sectorBuffer, modifiedSize, basic.isDataInverted());
                ostream.write(temp.getData(), 0, temp.getSize());
            }
            if (istream != null) {
                temp.setSize(modifiedSize);
                istream.read(temp.getData(), 0, temp.getSize());
                temp.invertData(basic.isDataInverted());
                if (!Arrays.equals(Arrays.copyOf(temp.getData(), temp.getSize()), Arrays.copyOf(sectorBuffer, temp.getSize()))) {
                    return -1;
                }
            }
        }
        return sectorSize;
    }

    public boolean convertDataForLoad(DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream) {
        try {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = istream.read(buffer)) != -1) {
                ostream.write(buffer, 0, len);
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean convertDataForVerify(DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream) {
        return convertDataForLoad(item, istream, ostream);
    }

    public boolean supportWriting() { return true; }

    public boolean isEnoughFileSize(int size) { return true; }

    public boolean convertDataForSave(DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream) {
        return convertDataForLoad(item, istream, ostream);
    }

    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        return groupNum;
    }

    public boolean prepareToSaveFile(InputStream istream, int[] fileSize, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T> nitem, DiskBasicError errinfo) throws IOException { return true; }

    public int writeFile(DiskBasicDirItem<T> item, InputStream istream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needEofCode = item.needCheckEofCode();
        int len = 0;
        try {
            if (remain <= size) {
                if (remain < 0) remain = 0;
                if (needEofCode) {
                    if (remain > 1) istream.read(buffer, 0, remain - 1);
                    if (remain > 0) buffer[remain - 1] = item.getEofCode();
                } else {
                    if (remain > 0) istream.read(buffer, 0, remain);
                }
                if (size > remain) {
                    Arrays.fill(buffer, remain, size, (byte) 0);
                }
                len = remain;
            } else {
                istream.read(buffer, 0, size);
                len = size;
            }
        } catch (Exception e) {
            // handle exception
        }
        basic.invertMem(buffer, size);
        return len;
    }

    public void additionalProcessOnSavedFile(DiskBasicDirItem<T> item) {}
    public void additionalProcessOnRenamedFile(DiskBasicDirItem<T> item) throws IOException {}
    public void additionalProcessOnChangedAttr(DiskBasicDirItem<T> item) {}

    public boolean supportDeleting() { return true; }

    public void deleteGroups(DiskBasicGroups groupItems) throws IOException {
        for (DiskBasicGroupItem item : groupItems.getItems()) {
            deleteGroupNumber(item.group);
        }
    }

    public void deleteGroupNumber(int groupNum) throws IOException {
        setGroupNumber(groupNum, basic.diskBasicParam.getGroupUnusedCode());
    }

    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<T> item) throws IOException { return true; }

    public void getIdentifiedData(DiskBasicIdentifiedData data) {}

    public void setIdentifiedData(DiskBasicIdentifiedData data) {}

    public void setManagedStartGroup(int val) { managedStartGroup = val; }
}
