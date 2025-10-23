package l3diskex.diskimg;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l3diskex.ResultInfo;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasics;
import vavi.io.SeekableDataInputStream;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


public abstract class DiskImage {

    private static final Logger logger = System.getLogger(DiskImage.class.getName());

    public static class IntHashMapUtil {

        public static void increaseValue(Map<Integer, Integer> hashMap, int key) {
            hashMap.put(key, hashMap.getOrDefault(key, 0) + 1);
        }

        public static int getMaxKeyOnMaxValue(Map<Integer, Integer> hashMap) {
            int keyResult = 0;
            int key2 = 0;
            int val = 0;
            for (Map.Entry<Integer, Integer> entry : hashMap.entrySet()) {
                if (val < entry.getValue()) {
                    val = entry.getValue();
                    keyResult = entry.getKey();
                } else if (val == entry.getValue()) {
                    key2 = entry.getKey();
                    if (keyResult < key2) {
                        keyResult = key2;
                    }
                }
            }
            return keyResult;
        }

        public static int getKeyCount(Map<Integer, Integer> hashMap) {
            return hashMap.size();
        }

        public static int maxValue(int src, int value) {
            return (src > value ? src : value);
        }

        public static int minValue(int src, int value) {
            return (src < value ? src : value);
        }
    }

    public static abstract class DiskImageSectorHeader {

        public DiskImageSectorHeader() {
        }

        public abstract int getHeaderType();
    }

    public static abstract class DiskImageSector {

        protected int mNum;

        public DiskImageSector(int nNum) {
            mNum = nNum;
        }

        public boolean replace(DiskImageSector srcSector) {
            return false;
        }

        public boolean fill(byte code) {
            return fill(code, -1, 0);
        }

        public boolean fill(byte code, int len /* = -1 */, int start /* = 0 */) {
            return false;
        }

        public boolean copy(byte[] buf, int len) {
            return copy(buf, len, 0);
        }

        public boolean copy(byte[] buf, int len, int start) {
            return false;
        }

        public int find(byte[] buf, int len) {
            return -1;
        }

        public byte get(int pos) {
            return 0;
        }

        public short get16(int pos) {
            return get16(pos, false);
        }

        public short get16(int pos, boolean bigEndian) {
            return 0;
        }

        public int modifySectorSize(int size) {
            return 0;
        }

        public int getSectorNumber() {
            return mNum;
        }

        public void setSectorNumber(int val) {
            mNum = val;
        }

        public boolean isDeleted() {
            return false;
        }

        public void setDeletedMark(boolean val) {
        }

        public boolean isSameSector(int sectorNumber, int density, boolean deletedMark) {
            return false;
        }

        public int getHeaderSize() {
            return 0;
        }

        public int getSectorSize() {
            return 0;
        }

        public void setSectorSize(int val) {
        }

        public abstract int getSectorBufferSize();

        public int getSize() {
            return 0;
        }

        public abstract byte[] getSectorBuffer();

        public byte[] getSectorBuffer(int offset) {
            return null;
        }

        public short getSectorsPerTrack() {
            return 0;
        }

        public void setSectorsPerTrack(short val) {
        }

        public byte getSectorStatus() {
            return 0;
        }

        public void setSectorStatus(byte val) {
        }

        public DiskImageSectorHeader getHeader() {
            return null;
        }

        public byte getIDC() {
            return 0;
        }

        public byte getIDH() {
            return 0;
        }

        public byte getIDR() {
            return 0;
        }

        public byte getIDN() {
            return 0;
        }

        public void setIDC(byte val) {
        }

        public void setIDH(byte val) {
        }

        public void setIDR(byte val) {
        }

        public void setIDN(byte val) {
        }

        public boolean isSingleDensity() {
            return false;
        }

        public void setSingleDensity(boolean val) {
        }

        public boolean isModified() {
            return false;
        }

        public void setModify() {
        }

        public void clearModify() {
        }

        public static int compare(DiskImageSector item1, DiskImageSector item2) {
            return item1.mNum - item2.mNum;
        }

        public static int compareIDR(DiskImageSector item1, DiskImageSector item2) {
            return item1.getIDR() - item2.getIDR();
        }

        public static int convIDNToSecSize(byte n) {
            int sec = 0;
            if (n <= 3) sec = gSectorSizes[n];
            return sec;
        }

        public static byte convSecSizeToIDN(int size) {
            byte n = 1;
            for (int i = 0; gSectorSizes[i] != 0; i++) {
                if (gSectorSizes[i] == size) {
                    n = (byte) i;
                    break;
                }
            }
            return n;
        }

        public static int[] gSectorSizes = {128, 256, 512, 1024, 0};
    }

    public static abstract class DiskImageTrack {

        protected DiskImageDisk parent;
        protected int mTrkNum;
        protected int mSidNum;
        protected int mOffsetPos;
        protected int mSize;
        protected int mInterleave;
        protected List<DiskImageSector> sectors;
        protected int mOrigSectors;
        protected byte[] extraData;
        protected int extraSize;

        protected DiskImageTrack() {
        }

        protected DiskImageTrack(DiskImageTrack src) {
        }

        public DiskImageTrack(DiskImageDisk disk) {
            parent = disk;
            mTrkNum = 0;
            mSidNum = 0;
            sectors = null;
            mSize = 0;
            mInterleave = 1;
            mOrigSectors = 0;
            extraData = null;
            extraSize = 0;
        }

        public DiskImageTrack(DiskImageDisk disk, int nTrkNum, int nSidNum, int nOffsetPos, int nInterleave) {
            parent = disk;
            mTrkNum = nTrkNum;
            mSidNum = nSidNum;
            mOffsetPos = nOffsetPos;
            sectors = null;
            mSize = 0;
            mInterleave = nInterleave;
            mOrigSectors = 0;
            extraData = null;
            extraSize = 0;
        }

        public abstract DiskImageSector newImageSector(int nNum, DiskImageSectorHeader nHeader, byte[] nData);

        public abstract DiskImageSector newImageSector(int trackNumber, int sideNumber, int sectorNumber, int sectorSize, int numberOfSector, boolean singleDensity /* = false */, int status /* = 0 */);

        public int add(DiskImageSector newsec) {
            if (sectors == null) sectors = new ArrayList<>();
            sectors.add(newsec);
            mOrigSectors = sectors.size();
            return mOrigSectors;
        }

        public int replace(DiskImageTrack srcTrack) {
            int rc = 0;
            if (sectors == null) return -1;
            for (int i = 0; i < sectors.size(); i++) {
                DiskImageSector tagSector = sectors.get(i);
                DiskImageSector srcSector = srcTrack.getSector(tagSector.getSectorNumber());
                if (srcSector == null) {
                    continue;
                }
                if (!tagSector.replace(srcSector)) {
                    rc = 1;
                }
            }
            return rc;
        }

        public int addNewSector(int trknum, int sidnum, int secnum, int secsize, boolean sdensity, int status) {
            int rc = 0;
            DiskImageSector newSector = newImageSector(trknum, sidnum, secnum, secsize, 1, sdensity, status);
            add(newSector);
            decreaseExtraDataSize(newSector.getSize());
            shrinkAndCalcOffsets(false);
            return rc;
        }

        public int deleteSectorByIndex(int pos) {
            int rc = 0;
            if (sectors == null || pos < 0 || pos >= sectors.size()) return -1;
            int removedSize = 0;
            DiskImageSector sector = sectors.get(pos);
            removedSize += sector.getSize();
            sectors.remove(pos);
            increaseExtraDataSize(removedSize);
            shrinkAndCalcOffsets(false);
            return rc;
        }

        public int deleteSectors(int startSectorNum, int endSectorNum) {
            int rc = 0;
            if (sectors == null) return -1;
            boolean removed = false;
            int removedSize = 0;
            for (int i = 0; i < sectors.size(); i++) {
                DiskImageSector sector = sectors.get(i);
                int num = sector.getSectorNumber();
                if (startSectorNum <= num && (num <= endSectorNum || endSectorNum < 0)) {
                    removedSize += sector.getSize();
                    sectors.remove(i);
                    removed = true;
                    i--;
                }
            }
            if (removed) {
                increaseExtraDataSize(removedSize);
                shrinkAndCalcOffsets(false);
            }
            return rc;
        }

        public int shrink(boolean trimUnusedData) {
            int newsize = 0;
            int count = sectors != null ? sectors.size() : 0;
            for (int i = 0; i < count; i++) {
                DiskImageSector sector = sectors.get(i);
                sector.setSectorsPerTrack((short) count);
                newsize += sector.getHeaderSize();
                if (trimUnusedData) {
                    newsize += sector.getSectorSize();
                } else {
                    newsize += sector.getSectorBufferSize();
                }
            }
            if (!trimUnusedData) {
                newsize += extraSize;
            }
            setSize(newsize);
            return newsize;
        }

        public void shrinkAndCalcOffsets(boolean trimUnusedData) {
            shrink(trimUnusedData);
            parent.calcOffsets();
        }

        public void increaseExtraDataSize(int size) {
            if (size == 0) return;
            byte[] newData = new byte[extraSize + size];
            Arrays.fill(newData, 0, size, (byte) 0);
            if (extraData != null) {
                System.arraycopy(extraData, 0, newData, size, extraSize);
            }
            extraData = newData;
            extraSize += size;
        }

        public void decreaseExtraDataSize(int size) {
            if (size == 0) return;
            int remainSize = (extraSize > size ? extraSize - size : 0);
            byte[] newData = null;
            if (remainSize > 0) {
                newData = new byte[remainSize];
                System.arraycopy(extraData, size, newData, 0, remainSize);
            }
            extraData = newData;
            extraSize = remainSize;
        }

        public int getTrackNumber() {
            return mTrkNum;
        }

        public void setTrackNumber(int val) {
            mTrkNum = val;
        }

        public int getSideNumber() {
            return mSidNum;
        }

        public void setSideNumber(int val) {
            mSidNum = val;
        }

        public int getOffsetPos() {
            return mOffsetPos;
        }

        public int getMinSectorNumber() {
            int sectorNumber = 0x7fffffff;
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    if (sectorNumber > s.getSectorNumber()) {
                        sectorNumber = s.getSectorNumber();
                    }
                }
            }
            return sectorNumber;
        }

        public int getMaxSectorNumber() {
            int sectorNumber = 0;
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    if (sectorNumber < s.getSectorNumber()) {
                        sectorNumber = s.getSectorNumber();
                    }
                }
            }
            return sectorNumber;
        }

        public int getMaxSectorSize() {
            int sectorSize = 0;
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    if (sectorSize < s.getSectorSize()) {
                        sectorSize = s.getSectorSize();
                    }
                }
            }
            return sectorSize;
        }

        public int getSize() {
            return mSize;
        }

        public void setSize(int val) {
            mSize = val;
        }

        public int getInterleave() {
            return mInterleave;
        }

        public void setInterleave(int val) {
            mInterleave = val;
        }

        public void calcInterleave() {
            if (sectors == null) return;
            int count = sectors.size();
            if (count == 1) {
                setInterleave(1);
                return;
            }
            int start = sectors.get(0).getSectorNumber();
            int next = start + 1;
            int state = 0;
            int intl = 0;
            for (int secPos = 0; secPos < count; secPos++) {
                DiskImageSector s = sectors.get(secPos);
                switch (state) {
                    case 1:
                        intl++;
                        if (s.getSectorNumber() == next) {
                            state = 2;
                            secPos = count;
                        }
                        break;
                    default:
                        if (s.getSectorNumber() == start) {
                            state = 1;
                            intl = 0;
                        }
                        break;
                }
            }
            if (intl <= 0) {
                intl = 1;
            }
            setInterleave(intl);
        }

        public List<DiskImageSector> getSectors() {
            return sectors;
        }

        public int getSectorsPerTrack() {
            int cnt = 0;
            if (sectors != null) {
                cnt = sectors.size();
            }
            return cnt;
        }

        public DiskImageSector getSector(int sectorNumber, int density) {
            DiskImageSector sector = null;
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    if (s.isSameSector(sectorNumber, density, false)) {
                        sector = s;
                        break;
                    }
                }
            }
            return sector;
        }

        public DiskImageSector getSector(int sectorNumber) {
            return getSector(sectorNumber, -1);
        }

        public DiskImageSector getSectorByIndex(int pos) {
            DiskImageSector sector = null;
            if (sectors != null && pos >= 0 && pos < sectors.size()) {
                sector = sectors.get(pos);
            }
            return sector;
        }

        public byte getMajorIDC() {
            byte id = 0;
            Map<Integer, Integer> map = new HashMap<>();
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    IntHashMapUtil.increaseValue(map, s.getIDC());
                }
                id = (byte) IntHashMapUtil.getMaxKeyOnMaxValue(map);
            }
            return id;
        }

        public byte getMajorIDH() {
            byte id = 0;
            Map<Integer, Integer> map = new HashMap<>();
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    IntHashMapUtil.increaseValue(map, s.getIDH());
                }
                id = (byte) IntHashMapUtil.getMaxKeyOnMaxValue(map);
            }
            return id;
        }

        public void setAllIDC(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDC(val);
                }
            }
        }

        public void setAllIDH(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDH(val);
                }
            }
        }

        public void setAllIDR(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDR(val);
                }
            }
        }

        public void setAllIDN(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDN(val);
                }
            }
        }

        public void setAllSingleDensity(boolean val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setSingleDensity(val);
                }
            }
        }

        public void setAllSectorsPerTrack(int val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setSectorsPerTrack((short) val);
                }
            }
        }

        public void setAllSectorSize(int val) {
            if (sectors == null) return;
            int sum = 0;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sum += sector.modifySectorSize(val);
                }
            }
            if (sum > 0) {
                increaseExtraDataSize(sum);
            } else if (sum < 0) {
                decreaseExtraDataSize(-sum);
            }
            shrinkAndCalcOffsets(false);
        }

        public void setExtraData(byte[] buf, int size) {
            extraData = buf;
            extraSize = size;
        }

        public byte[] getExtraData() {
            return extraData;
        }

        public int getExtraDataSize() {
            return extraSize;
        }

        public boolean isModified() {
            if (sectors == null) return false;
            if (mOrigSectors != sectors.size()) return true;
            boolean modified = false;
            for (DiskImageSector sector : sectors) {
                if (sector == null) continue;
                modified = sector.isModified();
                if (modified) {
                    break;
                }
            }
            return modified;
        }

        public void clearModify() {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector == null) continue;
                sector.clearModify();
            }
            mOrigSectors = sectors.size();
        }

        public static int compare(DiskImageTrack item1, DiskImageTrack item2) {
            return ((item1.mTrkNum - item2.mTrkNum) | (item1.mSidNum - item2.mSidNum));
        }

        public static boolean calcSectorNumbersForInterleave(int interleave, int sectorsCount, List<Integer> sectorNums, int sectorOffset) {
            sectorNums.clear();
            for (int i = 0; i < sectorsCount; i++) {
                sectorNums.add(-1);
            }
            int sectorPos = 0;
            boolean err = false;
            for (int sectorNumber = 0; sectorNumber < sectorsCount && !err; sectorNumber++) {
                if (sectorPos >= sectorsCount) {
                    sectorPos -= sectorsCount;
                    while (sectorNums.get(sectorPos) >= 0) {
                        sectorPos++;
                        if (sectorPos >= sectorsCount) {
                            err = true;
                            break;
                        }
                    }
                }
                sectorNums.set(sectorPos, sectorNumber + sectorOffset);
                sectorPos += interleave;
            }
            return !err;
        }
    }

    public static abstract class DiskImageDiskHeader {

        public DiskImageDiskHeader() {
        }

        public abstract int getHeaderType();

        public String getName(boolean real) {
            return "";
        }

        public boolean isWriteProtected() {
            return false;
        }
    }

    public static abstract class DiskImageDisk extends DiskParam {

        protected DiskImageFile parent;
        protected int mNum;
        protected String mName;
        protected boolean mWriteProtect;
        protected int mOffsetStart;
        protected List<DiskImageTrack> tracks;
        protected int mMaxTrackNumber;
        protected DiskParam origParam;
        protected boolean mParamChanged;
        protected DiskBasics basics;

        protected DiskImageDisk() {
            super();
        }

        protected DiskImageDisk(DiskImageDisk src) {
            super(src);
        }

        public DiskImageDisk(DiskImageFile file, int nNum) {
            super();
            parent = file;
            mNum = nNum;
            mWriteProtect = false;
            tracks = null;
            mOffsetStart = 0;
            mParamChanged = false;
            basics = new DiskBasics();
        }

        public DiskImageDisk(DiskImageFile file, int nNum, DiskParam nParam, String nDiskname, boolean nWriteProtect) {
            super(nParam);
            parent = file;
            mNum = nNum;
            mName = nDiskname;
            mWriteProtect = nWriteProtect;
            tracks = null;
            mOffsetStart = 0;
            mParamChanged = false;
            basics = new DiskBasics();
        }

        public DiskImageDisk(DiskImageFile file, int nNum, DiskImageDiskHeader nHeader) {
            super();
            parent = file;
            mNum = nNum;
            mName = nHeader.getName(false);
            mWriteProtect = nHeader.isWriteProtected();
            tracks = null;
            mOffsetStart = 0;
            mParamChanged = false;
            basics = new DiskBasics();
        }

        public abstract DiskImageTrack newImageTrack();

        public abstract DiskImageTrack newImageTrack(int nTrkNum, int nSidNum, int nOffsetPos, int nInterleave);

        public int add(DiskImageTrack newtrk) {
            if (tracks == null) tracks = new ArrayList<>();
            tracks.add(newtrk);
            return tracks.size();
        }

        public int replace(int sideNumber, DiskImageDisk srcDisk, int srcSideNumber) {
            int rc = 0;
            if (tracks == null) return -1;
            for (int i = 0; i < tracks.size(); i++) {
                DiskImageTrack tagTrack = tracks.get(i);
                int tagSideNumber = tagTrack.getSideNumber();
                if (sideNumber >= 0) {
                    if (tagSideNumber != sideNumber) {
                        continue;
                    }
                    if (srcSideNumber >= 0) {
                        tagSideNumber = srcSideNumber;
                    }
                    if (srcDisk.getSidesPerDisk() <= tagSideNumber) {
                        tagSideNumber = srcDisk.getSidesPerDisk() - 1;
                    }
                } else {
                    if (getSidesPerDisk() <= 1) {
                        tagSideNumber = srcSideNumber;
                    }
                }
                DiskImageTrack srcTrack = srcDisk.getTrack(tagTrack.getTrackNumber(), tagSideNumber);
                if (srcTrack == null) {
                    continue;
                }
                int rct = tagTrack.replace(srcTrack);
                if (rct != 0) rc = rct;
            }
            return rc;
        }

        public int addNewTrack(int sideNumber) {
            int rc = 0;
            DiskImageTrack srcTrack = null;
            int trkNum = -1;
            int sidNum = -1;
            int maxSidNum = -1;
            for (int pos = 0; pos < tracks.size(); pos++) {
                DiskImageTrack track = tracks.get(pos);
                if (track.getTrackNumber() > trkNum && (sideNumber < 0 || sideNumber == track.getSideNumber())) {
                    trkNum = track.getTrackNumber();
                    sidNum = track.getSideNumber();
                    if (sidNum > maxSidNum) {
                        maxSidNum = sidNum;
                    }
                    srcTrack = track;
                } else if (sideNumber < 0 && track.getSideNumber() > sidNum) {
                    sidNum = track.getSideNumber();
                    if (sidNum > maxSidNum) {
                        maxSidNum = sidNum;
                    }
                    srcTrack = track;
                }
            }
            if (srcTrack == null) {
                return -1;
            }
            List<DiskImageSector> sectors = srcTrack.getSectors();
            if (sectors == null) {
                return -1;
            }
            if (sideNumber < 0 && sidNum < maxSidNum) {
                sidNum++;
            } else {
                trkNum++;
                sidNum = (sideNumber < 0 ? 0 : sideNumber);
            }
            int limitPos = getCreatableTracks();
            int offsetPos = 0;
            for (int pos = (sideNumber < 0 ? 0 : sideNumber); pos < limitPos; pos += (sideNumber < 0 ? 1 : maxSidNum + 1)) {
                if (getOffset(pos) == 0) {
                    offsetPos = pos;
                    break;
                }
            }
            if (offsetPos == 0) {
                return -1;
            }
            DiskImageTrack newTrack = newImageTrack(trkNum, sidNum, offsetPos, srcTrack.getInterleave());
            int trkSize = 0;
            for (int pos = 0; pos < sectors.size(); pos++) {
                DiskImageSector sector = sectors.get(pos);
                int secNum = sector.getIDR();
                int secSize = sector.getSectorSize();
                boolean sdensity = sector.isSingleDensity();
                DiskImageSector newSector = newTrack.newImageSector(trkNum, sidNum, secNum, secSize, sectors.size(), sdensity, 0);
                newTrack.add(newSector);
                trkSize += newSector.getSize();
            }
            newTrack.increaseExtraDataSize(srcTrack.getExtraDataSize());
            trkSize += newTrack.getExtraDataSize();
            newTrack.setSize(trkSize);
            add(newTrack);
            calcOffsets();
            return rc;
        }

        public void deleteTracks(int startOffsetPos, int endOffsetPos, int sideNumber) {
            if (tracks == null) return;
            boolean removed = false;
            for (int i = 0; i < tracks.size(); i++) {
                DiskImageTrack track = tracks.get(i);
                if (track == null) continue;
                int pos = track.getOffsetPos();
                if (pos < startOffsetPos) continue;
                if (endOffsetPos >= startOffsetPos && pos > endOffsetPos) continue;
                if (sideNumber >= 0 && track.getSideNumber() != sideNumber) continue;
                tracks.remove(track);
                setOffset(pos, 0);
                removed = true;
                i--;
            }
            if (removed) {
                calcOffsets();
            }
        }

        public int shrinkTracks(boolean trimUnusedData) {
            if (tracks != null) {
                for (DiskImageTrack track : tracks) {
                    if (track == null) continue;
                    track.shrink(trimUnusedData);
                }
            }
            return calcOffsets();
        }

        public int calcOffsets() {
            int newSize = 0;
            if (tracks == null) return newSize;
            int limitPos = getCreatableTracks();
            for (int pos = 0; pos < limitPos; pos++) {
                setOffset(pos, 0);
            }
            int maxOffset = mOffsetStart;
            for (int i = 0; i < tracks.size(); i++) {
                DiskImageTrack track = tracks.get(i);
                if (track == null) continue;
                int pos = track.getOffsetPos();
                if (pos < 0 || pos >= limitPos) continue;
                int size = track.getSize();
                if (size > 0) {
                    setOffset(pos, maxOffset);
                } else {
                    setOffset(pos, 0);
                }
                setModify();
                maxOffset += size;
                newSize += size;
            }
            setSize(newSize);
            return newSize;
        }

        public int calcSizeWithoutHeader() {
            int newSize = 0;
            if (tracks == null) return newSize;
            for (DiskImageTrack track : tracks) {
                if (track == null) continue;
                newSize += track.getSize();
            }
            return newSize;
        }

        public int getNumber() {
            return mNum;
        }

        public String getName(boolean real) {
            return "";
        }

        public void setName(String val) {
        }

        public void setName(byte[] buf, int len) {
        }

        public DiskImageDiskHeader getHeader() {
            return null;
        }

        public DiskImageFile getFile() {
            return parent;
        }

        public List<DiskImageTrack> getTracks() {
            return tracks;
        }

        public DiskImageTrack getTrack(int trackNumber, int sideNumber) {
            DiskImageTrack track = null;
            if (tracks != null) {
                for (DiskImageTrack t : tracks) {
                    if (t.getTrackNumber() == trackNumber && t.getSideNumber() == sideNumber) {
                        track = t;
                        break;
                    }
                }
            }
            return track;
        }

        public DiskImageTrack getTrack(int index) {
            DiskImageTrack track = null;
            if (tracks != null && index < tracks.size()) {
                track = tracks.get(index);
            }
            return track;
        }

        public DiskImageTrack getTrackByOffset(int offset) {
            DiskImageTrack track = null;
            if (tracks != null) {
                for (DiskImageTrack t : tracks) {
                    if (t == null) continue;
                    int pos = t.getOffsetPos();
                    if (getOffset(pos) == offset) {
                        track = t;
                        break;
                    }
                }
            }
            return track;
        }

        public DiskImageSector getSector(int trackNumber, int sideNumber, int sectorNumber) {
            return getSector(trackNumber, sideNumber, sideNumber, -1);
        }

        public DiskImageSector getSector(int trackNumber, int sideNumber, int sectorNumber, int density) {
            DiskImageTrack trk = getTrack(trackNumber, sideNumber);
            if (trk == null) return null;
            return trk.getSector(sectorNumber, density);
        }

        public DiskParam calcMajorNumber() {
            Map<Integer, Integer>[] sectorNumbersMap = new HashMap[2];
            sectorNumbersMap[0] = new HashMap<>();
            sectorNumbersMap[1] = new HashMap<>();
            Map<Integer, Integer> sectorSizeMap = new HashMap<>();
            Map<Integer, Integer> interleaveMap = new HashMap<>();

            int trackNumberMin = 0x7fff_ffff;
            int trackNumberMax = 0;
            int sideNumberMin = 0x7fff_ffff;
            int sideNumberMax = 0;

            int sectorNumberMaxSide0 = 0;
            int sectorNumberMinSide0 = 0x7fff_ffff;
            int sectorNumberMinSide1 = 0x7fff_ffff;

            int sectorMasize = 0;
            int interleaveMax = 0;
            DiskParticulars singles = new DiskParticulars();

            if (tracks != null) {
                for (int ti = 0; ti < tracks.size(); ti++) {
                    DiskImageTrack t = tracks.get(ti);

                    int trkNum = t.getTrackNumber();
                    int sidNum = t.getSideNumber();

                    trackNumberMin = IntHashMapUtil.minValue(trackNumberMin, trkNum);
                    trackNumberMax = IntHashMapUtil.maxValue(trackNumberMax, trkNum);

                    if (trkNum > 0) {
                        sideNumberMin = IntHashMapUtil.minValue(sideNumberMin, sidNum);
                        sideNumberMax = IntHashMapUtil.maxValue(sideNumberMax, sidNum);
                    }

                    IntHashMapUtil.increaseValue(sectorSizeMap, t.getMaxSectorSize());
                    IntHashMapUtil.increaseValue(interleaveMap, t.getInterleave());

                    sidNum &= 0x7f;

                    if (sidNum >= 0 && sidNum < 2) {
                        IntHashMapUtil.increaseValue(sectorNumbersMap[sidNum], t.getSectorsPerTrack());
                    }

                    if (trkNum > 0 && sidNum < 2) {
                        int secNumMax = t.getMaxSectorNumber();
                        int secNumMin = t.getMinSectorNumber();
                        if (sidNum == 0) {
                            sectorNumberMaxSide0 = IntHashMapUtil.maxValue(sectorNumberMaxSide0, secNumMax);
                            sectorNumberMinSide0 = IntHashMapUtil.minValue(sectorNumberMinSide0, secNumMin);
                        } else {
                            sectorNumberMinSide1 = IntHashMapUtil.minValue(sectorNumberMinSide1, secNumMin);
                        }
                    }

                    List<DiskImageSector> sectors = t.getSectors();
                    if (sectors != null) {
                        DiskParticulars sis = new DiskParticulars();
                        for (int si = 0; si < sectors.size(); si++) {
                            DiskImageSector s = sectors.get(si);
                            if (s != null && s.isSingleDensity()) {
                                DiskParticular sd = new DiskParticular(t.getTrackNumber(), t.getSideNumber(), s.getSectorNumber(), 1, s.getSectorsPerTrack(), s.getSectorSize());
                                sis.add(sd);
                            }
                        }
                        DiskParticular.uniqueSectors(t.getSectorsPerTrack(), sis);
                        for (int si = 0; si < sis.size(); si++) {
                            singles.add(sis.get(si));
                        }
                    }
                }
            }
            sectorMasize = IntHashMapUtil.getMaxKeyOnMaxValue(sectorSizeMap);
            interleaveMax = IntHashMapUtil.getMaxKeyOnMaxValue(interleaveMap);

            sidesPerDisk = sideNumberMax + 1 - sideNumberMin;

            if (tracks != null) {
                int trackCount = (tracks.size() + sidesPerDisk - 1) / sidesPerDisk;
                if (trackNumberMax > (trackCount + 4)) {
                    trackNumberMax = trackCount - 1;
                }
            }

            boolean diskSingleType = false;
            if (tracks != null) {
                if (sectorMasize == 128 && sideNumberMax == 0 && mMaxTrackNumber > trackNumberMax) {
                    diskSingleType = true;
                    sideNumberMax++;
                    sidesPerDisk++;
                    for (int ti = 0; ti < tracks.size(); ti++) {
                        DiskImageTrack t = tracks.get(ti);
                        if ((t.getOffsetPos() & 1) != 0) {
                            t.setSideNumber(1);
                        }
                    }
                }
            }

            DiskParticular.uniqueTracks(trackNumberMax - trackNumberMin + 1, sidesPerDisk, diskSingleType, singles);

            tracksPerSide = tracks != null ? (trackNumberMax - trackNumberMin + 1) : 0;
            sectorSize = sectorMasize;
            interleave = interleaveMax;

            if (sidesPerDisk > 1 && sectorNumberMinSide1 != 0x7fffffff && sectorNumberMaxSide0 < sectorNumberMinSide1) {
                numberingSector = 1;
                int secNumMaj = 0;
                secNumMaj = IntHashMapUtil.getMaxKeyOnMaxValue(sectorNumbersMap[0]);
                sectorsPerTrack = secNumMaj;
            } else {
                numberingSector = 0;
                int[] secNumMaj = new int[2];
                for (int i = 0; i < 2; i++) {
                    secNumMaj[i] = IntHashMapUtil.getMaxKeyOnMaxValue(sectorNumbersMap[i]);
                }
                sectorsPerTrack = (secNumMaj[0] > secNumMaj[1] ? secNumMaj[0] : secNumMaj[1]);
            }

            DiskParticulars ptracks = new DiskParticulars();
            if (tracks != null) {
                for (int ti = 0; ti < tracks.size(); ti++) {
                    DiskImageTrack t = tracks.get(ti);
                    if (t == null) continue;
                    List<DiskImageSector> ss = t.getSectors();
                    if (ss == null) continue;
                    if (ss.size() != sectorsPerTrack) {
                        ptracks.add(new DiskParticular(t.getTrackNumber(), t.getSideNumber(), -1, 1, ss.size(), t.getMaxSectorSize()));
                    }
                }
                DiskParticular.uniqueTracks(tracksPerSide, sidesPerDisk, false, ptracks);
            }

            DiskParam diskParam = gDiskTemplates.find(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize, interleave, trackNumberMin, sideNumberMin, sectorNumberMinSide0, numberingSector, singles, ptracks);
            if (diskParam != null) {
                setDiskTypeName(diskParam.getDiskTypeName());
                setReversible(diskParam.isReversible());
                setBasicTypes(diskParam.getBasicTypes());
                setSingles(singles);
                setTrackNumberBaseOnDisk(diskParam.getTrackNumberBaseOnDisk());
                setSideNumberBaseOnDisk(diskParam.getSideNumberBaseOnDisk());
                setSectorNumberBaseOnDisk(diskParam.getSectorNumberBaseOnDisk());
                setVariableSectorsPerTrack(diskParam.isVariableSectorsPerTrack());
                setParamDensity(diskParam.getParamDensity());
                setParticularTracks(diskParam.getParticularTracks());
                setDensityName(diskParam.getDensityName());
                setDescription(diskParam.getDescription());
            }

            allocDiskBasics();
            setOriginalParam(this);

            return diskParam;
        }

        public boolean initialize(int selectedSide) {
            if (tracks == null) {
                return false;
            }

            boolean rc = true;
            for (int trackPos = 0; trackPos < tracks.size(); trackPos++) {
                DiskImageTrack track = tracks.get(trackPos);
                if (selectedSide >= 0) {
                    if (selectedSide != track.getSideNumber()) {
                        continue;
                    }
                }

                List<DiskImageSector> secs = track.getSectors();
                if (secs == null) {
                    continue;
                }

                for (int secPos = 0; secPos < secs.size(); secPos++) {
                    DiskImageSector sec = secs.get(secPos);
                    if (sec != null) {
                        sec.fill((byte) 0, -1, 0);
                    }
                }
            }
            return rc;
        }

        public boolean rebuild(DiskParam param, int selectedSide) {
            if (selectedSide >= 0) {
                setDiskParam(param.getSidesPerDisk(), param.getTracksPerSide(), param.getSectorsPerTrack(), param.getSectorSize(), param.getParamDensity(), param.getInterleave(), param.getSingles(), param.getParticularTracks());
            } else {
                setDiskParam(param);
            }

            DiskResult result = new DiskResult();
            String diskname = "";
            DiskImageCreator cr = new DiskImageCreator(diskname, param, false, null, result);
            boolean rc = true;
            int trk = param.getTrackNumberBaseOnDisk();
            int trks = param.getTracksPerSide() + trk;
            int sid = 0;
            int sides = param.getSidesPerDisk();
            for (int pos = 0; pos < getCreatableTracks(); pos++) {
                if (selectedSide >= 0) {
                    sid = selectedSide;
                    if (selectedSide != (pos % sides)) {
                        continue;
                    }
                }

                int offset = getOffset(pos);
                DiskImageTrack track = getTrackByOffset(offset);
                if (tracks != null && track != null) {
                    tracks.remove(track);
                }
                if (offset == 0) {
                    offset = getSize();
                    if (offset < getOffsetStart()) {
                        offset = getOffsetStart();
                    }
                }
                int trackSize = cr.createTrack(trk, sid, pos, offset, this);
                setOffset(pos, offset);
                setSize(offset + trackSize);

                sid++;
                if (sid >= sides || selectedSide >= 0) {
                    trk++;
                    sid = 0;
                }
                if (trk >= trks) {
                    setMaxTrackNumber(pos);
                    break;
                }
            }
            return rc;
        }

        public boolean isWriteProtected() {
            return true;
        }

        public void setWriteProtect(boolean val) {
        }

        public String getDensityText() {
            return "";
        }

        public int getDensity() {
            return 0;
        }

        public void setDensity(int val) {
        }

        public int getSize() {
            return 0;
        }

        public void setSize(int val) {
        }

        public int getSizeWithoutHeader() {
            return 0;
        }

        public void setSizeWithoutHeader(int val) {
        }

        public int getOffset(int num) {
            return 0;
        }

        public void setOffset(int num, int offset) {
        }

        public void setOffsetWithoutHeader(int num, int offset) {
        }

        public int getOffsetStart() {
            return mOffsetStart;
        }

        public void setOffsetStart(int val) {
            mOffsetStart = val;
        }

        public int getMaxTrackNumber() {
            return mMaxTrackNumber;
        }

        public void setMaxTrackNumber(int pos) {
            mMaxTrackNumber = pos;
        }

        public int getCreatableTracks() {
            return 0;
        }

        public void setModify() {
        }

        public boolean isModified() {
            boolean modified = false;
            if (tracks != null) {
                for (int trackNum = 0; trackNum < tracks.size() && !modified; trackNum++) {
                    DiskImageTrack track = tracks.get(trackNum);
                    if (track == null) continue;
                    modified = track.isModified();
                    if (modified) {
                        break;
                    }
                }
            }
            return modified;
        }

        public void clearModify() {
            if (tracks != null) {
                for (int trackNum = 0; trackNum < tracks.size(); trackNum++) {
                    DiskImageTrack track = tracks.get(trackNum);
                    if (track == null) continue;
                    track.clearModify();
                }
            }
        }

        public boolean existTrack(int sideNumber) {
            boolean found = false;
            List<DiskImageTrack> tracks = getTracks();
            if (tracks != null) {
                for (int num = 0; num < tracks.size(); num++) {
                    DiskImageTrack trk = tracks.get(num);
                    if (trk == null) continue;
                    if (sideNumber >= 0) {
                        if (sideNumber != trk.getSideNumber()) continue;
                    }
                    found = true;
                    break;
                }
            }
            return found;
        }

        public void setOriginalParam(DiskParam val) {
            origParam = val;
        }

        public DiskParam getOriginalParam() {
            return origParam;
        }

        public void setParamChanged(boolean val) {
            mParamChanged = val;
        }

        public boolean getParamChanged() {
            return mParamChanged;
        }

        public void allocDiskBasics() {
            basics.add(null);
            if (reversible) basics.add(null);
        }

        public DiskBasic getDiskBasic(int idx) {
            if (idx < 0) idx = 0;
            return basics.item(idx);
        }

        public DiskBasics getDiskBasics() {
            return basics;
        }

        public void clearDiskBasics() {
            if (basics == null) return;
            for (int idx = 0; idx < basics.count(); idx++) {
                DiskBasic basic = getDiskBasic(idx);
                if (basic == null) continue;
                basic.clearParseAndAssign(false);
            }
        }

        public void setCharCode(String name) {
            if (basics == null) return;
            for (int idx = 0; idx < basics.count(); idx++) {
                DiskBasic basic = getDiskBasic(idx);
                if (basic == null) continue;
                basic.setCharCode(name);
            }
        }

        public static int compare(DiskImageDisk item1, DiskImageDisk item2) {
            return item1.mNum - item2.mNum;
        }

        protected int sidesPerDisk;
        protected int tracksPerSide;
        protected int sectorsPerTrack;
        protected int sectorSize;
        protected int interleave;
        protected int numberingSector;
        protected boolean reversible;
    }

    public static abstract class DiskImageFile {

        protected DiskImage pImage;
        protected List<DiskImageDisk> disks;
        protected List<Short> mods;
        protected String mBasicTypeHint;

        protected DiskImageFile() {
        }

        protected DiskImageFile(DiskImageFile src) {
        }

        public DiskImageFile(DiskImage image) {
            pImage = image;
            disks = null;
            mods = null;
        }

        public abstract DiskImageDisk newImageDisk(int nNum);

        public abstract DiskImageDisk newImageDisk(int nNum, DiskParam nParam, String nDiskname, boolean nWriteProtect);

        public abstract DiskImageDisk newImageDisk(int nNum, DiskImageDiskHeader nHeader);

        public static final short MODIFY_NONE = 0;
        public static final short MODIFY_ADD = 1;

        public int add(DiskImageDisk newdsk, short modFlags) {
            if (disks == null) disks = new ArrayList<>();
            if (mods == null) mods = new ArrayList<>();
            disks.add(newdsk);
            mods.add(modFlags);
            return disks.size();
        }

        public void clear() {
            if (disks != null) {
                disks.clear();
                disks = null;
            }
            if (mods != null) {
                mods = null;
            }
        }

        public int count() {
            if (disks == null) return 0;
            return disks.size();
        }

        public boolean delete(int idx) {
            DiskImageDisk disk = getDisk(idx);
            if (disk == null) return false;
            disks.remove(idx);
            mods.remove(idx);
            return true;
        }

        public List<DiskImageDisk> getDisks() {
            return disks;
        }

        public DiskImageDisk getDisk(int idx) {
            if (disks == null) return null;
            if (idx >= disks.size()) return null;
            return disks.get(idx);
        }

        public boolean isModified() {
            boolean modified = false;
            if (disks != null) {
                for (int diskNum = 0; diskNum < disks.size() && !modified; diskNum++) {
                    modified = (mods.get(diskNum) != 0);
                    if (modified) break;

                    DiskImageDisk disk = disks.get(diskNum);
                    if (disk == null) continue;

                    modified = disk.isModified();
                    if (modified) break;
                }
            }
            return modified;
        }

        public void clearModify() {
            if (disks != null) {
                for (int diskNum = 0; diskNum < disks.size(); diskNum++) {
                    mods.set(diskNum, MODIFY_NONE);

                    DiskImageDisk disk = disks.get(diskNum);
                    if (disk == null) continue;

                    disk.clearModify();
                }
            }
        }

        public String getBasicTypeHint() {
            return mBasicTypeHint;
        }

        public void setBasicTypeHint(String val) {
            mBasicTypeHint = val;
        }

        public DiskImage getImage() {
            return pImage;
        }
    }

    protected String mFilename;
    protected DiskImageFile pFile;
    protected DiskResult mResult = new DiskResult();
    protected String mFormatType;

    protected void newFile(String filepath) {
        if (pFile != null) {
            pFile = null;
        }
        pFile = newImageFile();
        mFilename = filepath;
    }

    protected void clearFile() {
        pFile = null;
    }

    public DiskImage() {
        pFile = null;
    }

    public abstract DiskImageFile newImageFile();

    public int create(String diskname, DiskParam param, boolean writeProtect, String basicHint) {
        mResult.clear();
        newFile("");
        pFile.setBasicTypeHint(basicHint);
        DiskImageCreator cr = new DiskImageCreator(diskname, param, writeProtect, pFile, mResult);
        int validDisk = cr.create();
        if (validDisk < 0) {
            clearFile();
        }
        return validDisk;
    }

    public int add(String diskname, DiskParam param, boolean writeProtect, String basicHint) {
        if (pFile == null) return 0;
        mResult.clear();
        pFile.setBasicTypeHint(basicHint);
        DiskImageCreator cr = new DiskImageCreator(diskname, param, writeProtect, pFile, mResult);
        int validDisk = cr.add();
        return validDisk;
    }

    public int add(String filepath, String fileFormat, DiskParam paramHint) {
        if (pFile == null) return 0;
        mResult.clear();
        try {
            FileInputStream fstream = new FileInputStream(filepath);
            DiskParser ps = new DiskParser(filepath, fstream, pFile, mResult);
            int validDisk = ps.parseAdd(fileFormat, paramHint);
            return validDisk;
        } catch (IOException e) {
            mResult.setError(DiskResult.ERR_CANNOT_OPEN);
            return -1;
        }
    }

    public int open(String filepath, String fileFormat, DiskParam paramHint) {
        mResult.clear();
        try {
            Path p = Path.of(filepath);
            SeekableDataInputStream fstream = new SeekableDataInputStream(Files.newByteChannel(p));
            newFile(filepath);
            DiskParser ps = new DiskParser(filepath, fstream, pFile, mResult);
            int validDisk = ps.parse(fileFormat, paramHint);
            if (validDisk < 0) {
                clearFile();
            } else {
                setFormatType(fileFormat);
            }
            return validDisk;
        } catch (IOException e) {
            logger.log(Level.ERROR, e.getMessage(), e);
            mResult.setError(DiskResult.ERR_CANNOT_OPEN);
            return -1;
        }
    }

    public int check(String filepath, String fileFormat, List<DiskParam> params, DiskParam manualParam) {
        mResult.clear();
        try {
            SeekableDataInputStream fstream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));
            DiskParser ps = new DiskParser(filepath, fstream, pFile, mResult);
            return ps.check(fileFormat, params, manualParam);
        } catch (IOException e) {
            mResult.setError(DiskResult.ERR_CANNOT_OPEN);
            return -1;
        }
    }

    public void close() {
        clearFile();
        mFilename = "";
    }

    public int canSave(String fileFormat) {
        DiskWriter dw = new DiskWriter(this, mResult);
        return dw.CanSave(fileFormat);
    }

    public int save(String filepath, String fileFormat, DiskWriteOptions options) {
        DiskWriter dw = new DiskWriter(this, filepath, options, mResult);
        return dw.Save(fileFormat);
    }

    public int saveDisk(int diskNumber, int sideNumber, String filepath, String fileFormat, DiskWriteOptions options) {
        DiskWriter dw = new DiskWriter(this, filepath, options, mResult);
        return dw.SaveDisk(diskNumber, sideNumber, fileFormat);
    }

    public boolean delete(int diskNumber) {
        if (pFile == null) return false;
        pFile.delete(diskNumber);
        return true;
    }

    public int parseForReplace(int diskNumber, int sideNumber, String filepath, String fileFormat, DiskParam paramHint, DiskImageFile srcFile, DiskImageDisk[] tagDisk) {
        if (pFile == null) return 0;
        mResult.clear();
        try {
            FileInputStream fstream = new FileInputStream(filepath);
            DiskParser ps = new DiskParser(filepath, fstream, srcFile, mResult);
            int validDisk = ps.parse(fileFormat, paramHint);
            if (validDisk < 0) {
                return validDisk;
            }
            tagDisk[0] = pFile.getDisk(diskNumber);
            if (tagDisk[0] == null) {
                mResult.setError(DiskResult.ERR_NO_DATA);
                return mResult.getValid();
            }
            return 0;
        } catch (IOException e) {
            mResult.setError(DiskResult.ERR_CANNOT_OPEN);
            return -1;
        }
    }

    public int replaceDisk(int diskNumber, int sideNumber, DiskImageDisk srcDisk, int srcSideNumber, DiskImageDisk tagDisk) {
        if (pFile == null) return 0;
        mResult.clear();
        int validDisk = tagDisk.replace(sideNumber, srcDisk, srcSideNumber);
        if (validDisk != 0) {
            mResult.setError(DiskResult.ERR_REPLACE);
        }
        return validDisk;
    }

    public boolean setDiskName(int diskNumber, String newname) {
        DiskImageDisk disk = getDisk(diskNumber);
        if (disk == null) return false;
        if (!disk.getName(false).equals(newname)) {
            disk.setName(newname);
            disk.setModify();
            return true;
        }
        return false;
    }

    public String getDiskName(int diskNumber, boolean real) {
        DiskImageDisk disk = getDisk(diskNumber);
        if (disk == null) return "";
        return disk.getName(real);
    }

    public boolean isModified() {
        boolean modified = false;
        if (pFile != null) {
            modified = pFile.isModified();
        }
        return modified;
    }

    public DiskImageFile getFile() {
        return pFile;
    }

    public int countDisks() {
        if (pFile == null) return 0;
        return pFile.count();
    }

    public List<DiskImageDisk> getDisks() {
        if (pFile == null) return null;
        return pFile.getDisks();
    }

    public DiskImageDisk getDisk(int index) {
        if (pFile == null) return null;
        return pFile.getDisk(index);
    }

    public int getDiskTypeNumber(int index) {
        if (pFile == null) return -1;
        DiskImageDisk disk = pFile.getDisk(index);
        if (disk == null) return -1;
        return gDiskTemplates.indexOf(disk.getDiskTypeName());
    }

    public int getCreatableTracks() {
        return 0;
    }

    public String getFileName() {
        return new File(mFilename).getName();
    }

    public String getFileExt() {
        String name = new File(mFilename).getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }

    public String getFileNameBase() {
        String name = new File(mFilename).getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    public String getFilePath() {
        return mFilename;
    }

    public String getPath() {
        return new File(mFilename).getParent();
    }

    public void setFileName(String path) {
        mFilename = path;
    }

    public void setFileExt(String ext) {
        String base = getFileNameBase();
        String dir = getPath();
        mFilename = (dir != null ? dir + File.separator : "") + base + "." + ext;
    }

    public String getFormatType() {
        return mFormatType;
    }

    public void setFormatType(String formatType) {
        mFormatType = formatType;
    }

    public boolean matchDiskBasic(DiskBasic target) {
        boolean match = false;
        List<DiskImageDisk> disks = getDisks();
        if (disks == null) return false;
        for (int i = 0; i < disks.size(); i++) {
            DiskImageDisk disk = disks.get(i);
            DiskBasics basics = disk.getDiskBasics();
            if (basics == null) return false;
            for (int j = 0; j < basics.count(); j++) {
                if (target == basics.item(j)) {
                    match = true;
                    break;
                }
            }
        }
        return match;
    }

    public void clearDiskBasicParseAndAssign(int diskNumber, int sideNumber) {
        DiskImageDisk disk = getDisk(diskNumber);
        if (disk == null) return;
        if (pFile != null) {
            pFile.setBasicTypeHint("");
        }
        DiskBasics basics = disk.getDiskBasics();
        if (basics == null) return;
        basics.clearParseAndAssign(sideNumber);
    }

    public void setCharCode(String name) {
        List<DiskImageDisk> disks = getDisks();
        if (disks == null) return;
        for (int i = 0; i < disks.size(); i++) {
            DiskImageDisk disk = disks.get(i);
            disk.setCharCode(name);
        }
    }

    public int getDensityNames(List<String> arr) {
        return 0;
    }

    public int findDensity(int val) {
        return -1;
    }

    public int findDensityByIndex(int idx) {
        return -1;
    }

    public byte getDensity(int idx) {
        return 0;
    }

    public List<String> getErrorMessage(int maxrow) {
        return mResult.getMessages(maxrow);
    }

    public void showErrorMessage() {
        ResultInfo.showMessage(mResult.getValid(), mResult.getMessages(-1));
    }

    public int showErrWarnMessage() {
        return ResultInfo.showErrWarnMessage(mResult.getValid(), mResult.getMessages(-1));
    }
}
