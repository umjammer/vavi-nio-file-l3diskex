///
/// @author Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg;

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
import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasic;
import vavi.io.SeekableDataInputStream;

import static l3diskex.basicfmt.DiskBasic.clearParseAndAssign;
import static l3diskex.diskimg.DiskParam.diskTemplates;


/** Disk image I/O */
public abstract class DiskImage {

    private static final Logger logger = System.getLogger(DiskImage.class.getName());

    /** Class that handles hashing */
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

    /** Class that passes the header part to the sector data */
    public static abstract class DiskImageSectorHeader {

        public DiskImageSectorHeader() {
        }

        public abstract int getHeaderType();
    }

    /** Class that holds a pointer to the sector data */
    public static abstract class DiskImageSector {

        /** sector number (same as ID R) */
        protected int num;

        public DiskImageSector(int num) {
            this.num = num;
        }

        /** Replace sector data */
        public boolean replace(DiskImageSector srcSector) {
            return false;
        }

        public boolean fill(byte code) {
            return fill(code, -1, 0);
        }

        /** Fill sector data */
        public boolean fill(byte code, int len /* = -1 */, int start /* = 0 */) {
            return false;
        }

        public boolean copy(byte[] buf, int len) {
            return copy(buf, len, 0);
        }

        /** Overwrite sector data */
        public boolean copy(byte[] buf, int len, int start) {
            return false;
        }

        /** Whether sector data has specified byte sequence */
        public int find(byte[] buf, int len) {
            return -1;
        }

        /** Returns sector data at specified position */
        public byte get(int pos) {
            return 0;
        }

        public short get16(int pos) {
            return get16(pos, false);
        }

        /** Returns sector data at specified position */
        public short get16(int pos, boolean bigEndian) {
            return 0;
        }

        public int modifySectorSize(int size) {
            return 0;
        }

        /** Returns sector number (same as ID R) */
        public int getSectorNumber() {
            return num;
        }

        /** Set sector number */
        public void setSectorNumber(int val) {
            num = val;
        }

        /** Whether deleted mark is attached */
        public boolean isDeleted() {
            return false;
        }

        /** Set deleted mark */
        public void setDeletedMark(boolean val) {
        }

        /** Whether it is the same sector */
        public boolean isSameSector(int sectorNumber, int density, boolean deletedMark) {
            return false;
        }

        /** Return header size */
        public int getHeaderSize() {
            return 0;
        }

        /** Return sector size */
        public int getSectorSize() {
            return 0;
        }

        /** Set sector size */
        public void setSectorSize(int val) {
        }

        /** Return sector size (buffer size) */
        public abstract int getSectorBufferSize();

        /** Return size (header + buffer size) */
        public int getSize() {
            return 0;
        }

        /** Return pointer to sector data */
        public abstract byte[] getSectorBuffer();

        /** Returns pointer to sector data */
        public byte[] getSectorBuffer(int offset) {
            return null;
        }

        /** for write back */
        public void setSectorBuffer(byte[] data, int ofs, int len) {
        }

        /** Return number of sectors */
        public short getSectorsPerTrack() {
            return 0;
        }

        /** Set number of sectors */
        public void setSectorsPerTrack(short val) {
        }

        /** Return sector status */
        public byte getSectorStatus() {
            return 0;
        }

        /** Set sector status */
        public void setSectorStatus(byte val) {
        }

        /** Return header */
        public DiskImageSectorHeader getHeader() {
            return null;
        }

        /** Return ID C */
        public byte getIDC() {
            return 0;
        }

        /** Return ID H */
        public byte getIDH() {
            return 0;
        }

        /** Return ID R */
        public byte getIDR() {
            return 0;
        }

        /** Return ID N */
        public byte getIDN() {
            return 0;
        }

        /** Set ID C */
        public void setIDC(byte val) {
        }

        /** Set ID H */
        public void setIDH(byte val) {
        }

        /** Set ID R */
        public void setIDR(byte val) {
        }

        /** Set ID N */
        public void setIDN(byte val) {
        }

        /** Whether single density */
        public boolean isSingleDensity() {
            return false;
        }

        /** Set whether single density */
        public void setSingleDensity(boolean val) {
        }

        /** Whether it has been modified */
        public boolean isModified() {
            return false;
        }

        /** Set as modified */
        public void setModify() {
        }

        /** Clear modified flag */
        public void clearModify() {
        }

        /** Compare sector contents */
        public static int compare(DiskImageSector item1, DiskImageSector item2) {
            return item1.num - item2.num;
        }

        /** Compare sector numbers */
        public static int compareIDR(DiskImageSector item1, DiskImageSector item2) {
            return item1.getIDR() - item2.getIDR();
        }

        /** Calculate sector size from ID N */
        public static int convIDNToSecSize(byte n) {
            int sec = 0;
            if (n <= 3) sec = gSectorSizes[n];
            return sec;
        }

        /** Calculate ID N from sector size */
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

        public static final int[] gSectorSizes = {128, 256, 512, 1024, 0};
    }

    /** Class that holds a pointer to track data */
    public static abstract class DiskImageTrack {

        protected DiskImageDisk parent;
        /** track number */
        protected int trackNum;
        /** side number */
        protected int sideNum;
        /** position of offset table in header */
        protected int offsetPos;
        /** track size */
        protected int size;
        /** interleave of sector */
        protected int interleave;

        /** num of sectors (original / pre save) */
        protected List<DiskImageSector> sectors;

        /** extra data */
        protected int originalSectors;
        /** extra data size */
        protected byte[] extraData;

        protected int extraSize;

        public DiskImageTrack(DiskImageDisk disk) {
            parent = disk;
            trackNum = 0;
            sideNum = 0;
            sectors = null;
            size = 0;
            interleave = 1;
            originalSectors = 0;
            extraData = null;
            extraSize = 0;
        }

        /**
         * @param disk       Disk
         * @param trackNum   Track number
         * @param sideNum    Side number
         * @param offsetPos  Offset index
         * @param interleave Interleave
         */
        public DiskImageTrack(DiskImageDisk disk, int trackNum, int sideNum, int offsetPos, int interleave) {
            parent = disk;
            this.trackNum = trackNum;
            this.sideNum = sideNum;
            this.offsetPos = offsetPos;
            sectors = null;
            size = 0;
            this.interleave = interleave;
            originalSectors = 0;
            extraData = null;
            extraSize = 0;
        }

        /** Create instance */
        public abstract DiskImageSector newImageSector(int num, DiskImageSectorHeader header, byte[] data);

        /** Create instance */
        public abstract DiskImageSector newImageSector(int trackNumber, int sideNumber, int sectorNumber, int sectorSize, int numberOfSector, boolean singleDensity /* = false */, int status /* = 0 */);

        /**
         * Add sector
         *
         * @return Number of sectors
         */
        public int add(DiskImageSector newsec) {
            if (sectors == null) sectors = new ArrayList<>();
            sectors.add(newsec);
            originalSectors = sectors.size();
            return originalSectors;
        }

        /**
         * Replace sector data within the track
         *
         * @param srcTrack target track
         * @return 0:Normal, -1:Error, 1:Contains non-replaceable sectors
         */
        public int replace(DiskImageTrack srcTrack) {
            int rc = 0;
            if (sectors == null) return -1;
            for (DiskImageSector tagSector : sectors) {
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

        /**
         * Add new sector to the track
         *
         * @param trackNum Track number of new sector (ID C)
         * @param sideNum  Side number of new sector (ID H)
         * @param secNum   Sector number of new sector (ID R)
         * @param secSize  Sector size of new sector (128,256,512,1024,2048)
         * @param sDensity Whether new sector is single density
         * @param status   Status of new sector (usually 0)
         * @return 0 Normal
         */
        public int addNewSector(int trackNum, int sideNum, int secNum, int secSize, boolean sDensity, int status) {
            int rc = 0;
            DiskImageSector newSector = newImageSector(trackNum, sideNum, secNum, secSize, 1, sDensity, status);
            add(newSector);
            decreaseExtraDataSize(newSector.getSize());
            shrinkAndCalcOffsets(false);
            return rc;
        }

        /**
         * Delete sector at specified position within the track
         *
         * @param pos Sector position
         */
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

        /**
         * Delete specified sectors within the track
         *
         * @param startSectorNum Starting sector number
         * @param endSectorNum   Ending sector number, -1 for all
         * @return 0: Normal, -1: Error
         */
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

        /** Recalculate track size */
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

        /** Recalculate track size & calculate offsets */
        public void shrinkAndCalcOffsets(boolean trimUnusedData) {
            shrink(trimUnusedData);
            parent.calcOffsets();
        }

        /** Increase size of remainder buffer area */
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

        /** Decrease size of remainder buffer area */
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
            return trackNum;
        }

        public void setTrackNumber(int val) {
            trackNum = val;
        }

        public int getSideNumber() {
            return sideNum;
        }

        public void setSideNumber(int val) {
            sideNum = val;
        }

        public int getOffsetPos() {
            return offsetPos;
        }

        /** Returns the minimum sector number in the track */
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

        /** Returns the maximum sector number in the track */
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

        /** Returns the maximum sector size in the track */
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
            return size;
        }

        public void setSize(int val) {
            size = val;
        }

        public int getInterleave() {
            return interleave;
        }

        public void setInterleave(int val) {
            interleave = val;
        }

        /** Calculate and set interleave */
        public void calcInterleave() {
            if (sectors == null) return;
            int count = sectors.size();
            if (count == 1) {
                setInterleave(1);
                return;
            }
            int start = sectors.getFirst().getSectorNumber();
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

        /** Returns number of sectors */
        public int getSectorsPerTrack() {
            int cnt = 0;
            if (sectors != null) {
                cnt = sectors.size();
            }
            return cnt;
        }

        /**
         * Returns sector with specified sector number
         *
         * @param sectorNumber Sector number
         * @param density      Filter by density 0:double density 1:single density -1:exclude from criteria
         * @return Sector or null
         */
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

        /** Returns sector at specified position */
        public DiskImageSector getSectorByIndex(int pos) {
            DiskImageSector sector = null;
            if (sectors != null && pos >= 0 && pos < sectors.size()) {
                sector = sectors.get(pos);
            }
            return sector;
        }

        /** Returns the plausible ID C in the track */
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

        /** Returns the plausible ID H in the track */
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

        /** Change all ID Cs in the track */
        public void setAllIDC(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDC(val);
                }
            }
        }

        /** Change all ID Hs in the track */
        public void setAllIDH(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDH(val);
                }
            }
        }

        /** Change all ID Rs in the track */
        public void setAllIDR(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDR(val);
                }
            }
        }

        /** Change all ID Ns in the track */
        public void setAllIDN(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDN(val);
                }
            }
        }

        /** Change all densities in the track */
        public void setAllSingleDensity(boolean val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setSingleDensity(val);
                }
            }
        }

        /** Change number of all sectors in the track */
        public void setAllSectorsPerTrack(int val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setSectorsPerTrack((short) val);
                }
            }
        }

        /** Change size of all sectors in the track */
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

        /** Set extra data */
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

        /** Whether it has been modified */
        public boolean isModified() {
            if (sectors == null) return false;
            if (originalSectors != sectors.size()) return true;
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

        /** Clear modified flag */
        public void clearModify() {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector == null) continue;
                sector.clearModify();
            }
            originalSectors = sectors.size();
        }

        /** Comparison of track number and side number */
        public static int compare(DiskImageTrack item1, DiskImageTrack item2) {
            return ((item1.trackNum - item2.trackNum) | (item1.sideNum - item2.sideNum));
        }

        /**
         * Returns sector number list considering interleave
         *
         * <pre>
         * When interleave = 2
         * numOfSectors[0] = sector_offset, numOfSectors[2] = sector_offset + 1, numOfSectors[4] = sector_offset + 2, ... 
         * </pre>
         *
         * @param interleave   Interleave(1...)
         * @param sectorsCount Number of sectors
         * @param sectorOffset Offset
         * @param sectorNums   [out] Array
         */
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

    /** Class that passes the header of one disk */
    public static abstract class DiskImageDiskHeader {

        public DiskImageDiskHeader() {
        }

        public abstract int getHeaderType();

        /** Returns disk name */
        public String getName(boolean real) {
            return "";
        }

        /** Returns whether it is write protected */
        public boolean isWriteProtected() {
            return false;
        }
    }

    /** Class that holds a pointer to one disk */
    public static abstract class DiskImageDisk extends DiskParam {

        protected DiskImageFile parent;
        /** disk number */
        protected int num;
        /** disk name */
        protected String name;
        /** write protected ? */
        protected boolean writeProtect;

        /** usually header size */
        protected int offsetStart;

        protected List<DiskImageTrack> tracks;
        protected int maxTrackNumber;

        /** Analyzed parameters */
        protected DiskParam origParam;
        /** Whether disk parameters have been changed */
        protected boolean paramChanged;

        protected List<DiskBasic> basics;

        /**
         * @param file File image
         * @param num  Disk number
         */
        public DiskImageDisk(DiskImageFile file, int num) {
            parent = file;
            this.num = num;
            writeProtect = false;
            tracks = null;
            offsetStart = 0;
            paramChanged = false;
            basics = new ArrayList<>();
        }

        /**
         * @param file         File image
         * @param num          Disk number
         * @param param        Disk parameters
         * @param diskName     Disk name
         * @param writeProtect Whether write protected
         */
        public DiskImageDisk(DiskImageFile file, int num, DiskParam param, String diskName, boolean writeProtect) {
            super(param);
            parent = file;
            this.num = num;
            name = diskName;
            this.writeProtect = writeProtect;
            tracks = null;
            offsetStart = 0;
            paramChanged = false;
            basics = new ArrayList<>();
        }

        /**
         * @param file   File image
         * @param num    Disk number
         * @param header Disk header
         */
        public DiskImageDisk(DiskImageFile file, int num, DiskImageDiskHeader header) {
            super();
            parent = file;
            this.num = num;
            name = header.getName(false);
            writeProtect = header.isWriteProtected();
            tracks = null;
            offsetStart = 0;
            paramChanged = false;
            basics = new ArrayList<>();
        }

        public abstract DiskImageTrack newImageTrack();

        public abstract DiskImageTrack newImageTrack(int trackNum, int sideNum, int offsetPos, int interleave);

        /**
         * Add track to disk
         *
         * @return Number of tracks
         */
        public int add(DiskImageTrack newTrack) {
            if (tracks == null) tracks = new ArrayList<>();
            tracks.add(newTrack);
            return tracks.size();
        }

        /**
         * Replace disk content
         *
         * @param sideNumber    Side number
         * @param srcDisk       Source disk image
         * @param srcSideNumber Side number of source disk image
         */
        public int replace(int sideNumber, DiskImageDisk srcDisk, int srcSideNumber) {
            int rc = 0;
            if (tracks == null) return -1;
            for (DiskImageTrack tagTrack : tracks) {
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

        /**
         * Add track to disk
         *
         * @param sideNumber Side number, -1 for double sides
         */
        public int addNewTrack(int sideNumber) {
            int rc = 0;
            DiskImageTrack srcTrack = null;
            int trackNum = -1;
            int sideNum = -1;
            int maxSideNum = -1;
            for (DiskImageTrack track : tracks) {
                if (track.getTrackNumber() > trackNum && (sideNumber < 0 || sideNumber == track.getSideNumber())) {
                    trackNum = track.getTrackNumber();
                    sideNum = track.getSideNumber();
                    if (sideNum > maxSideNum) {
                        maxSideNum = sideNum;
                    }
                    srcTrack = track;
                } else if (sideNumber < 0 && track.getSideNumber() > sideNum) {
                    sideNum = track.getSideNumber();
                    if (sideNum > maxSideNum) {
                        maxSideNum = sideNum;
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
            if (sideNumber < 0 && sideNum < maxSideNum) {
                sideNum++;
            } else {
                trackNum++;
                sideNum = (sideNumber < 0 ? 0 : sideNumber);
            }
            int limitPos = getCreatableTracks();
            int offsetPos = 0;
            for (int pos = (sideNumber < 0 ? 0 : sideNumber); pos < limitPos; pos += (sideNumber < 0 ? 1 : maxSideNum + 1)) {
                if (getOffset(pos) == 0) {
                    offsetPos = pos;
                    break;
                }
            }
            if (offsetPos == 0) {
                return -1;
            }
            DiskImageTrack newTrack = newImageTrack(trackNum, sideNum, offsetPos, srcTrack.getInterleave());
            int trackSize = 0;
            for (int pos = 0; pos < sectors.size(); pos++) {
                DiskImageSector sector = sectors.get(pos);
                int sectorNum = sector.getIDR();
                int sectorSize = sector.getSectorSize();
                boolean sdensity = sector.isSingleDensity();
                DiskImageSector newSector = newTrack.newImageSector(trackNum, sideNum, sectorNum, sectorSize, sectors.size(), sdensity, 0);
                newTrack.add(newSector);
                trackSize += newSector.getSize();
            }
            newTrack.increaseExtraDataSize(srcTrack.getExtraDataSize());
            trackSize += newTrack.getExtraDataSize();
            newTrack.setSize(trackSize);
            add(newTrack);
            calcOffsets();
            return rc;
        }

        /**
         * Delete track
         *
         * @param startOffsetPos Starting track position to delete(0 ... 163)
         * @param endOffsetPos   Ending track position to delete(0 ... 163)
         * @param sideNumber     >= 0 for specific side only, -1 for all sides
         */
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

        /** Recalculate track size & offsets & change disk size */
        public int shrinkTracks(boolean trimUnusedData) {
            if (tracks != null) {
                for (DiskImageTrack track : tracks) {
                    if (track == null) continue;
                    track.shrink(trimUnusedData);
                }
            }
            return calcOffsets();
        }

        /** Recalculate offsets & change disk size */
        public int calcOffsets() {
            int newSize = 0;
            if (tracks == null) return newSize;
            int limitPos = getCreatableTracks();
            for (int pos = 0; pos < limitPos; pos++) {
                setOffset(pos, 0);
            }
            int maxOffset = offsetStart;
            for (DiskImageTrack track : tracks) {
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

        /** Disk size calculation (excluding disk header) */
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
            return num;
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

        /**
         * Returns specified track
         *
         * @param trackNumber Track number (cylinder)
         * @param sideNumber  Side number (head)
         * @return Track
         */
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

        /**
         * Returns specified track
         *
         * @param index Position
         * @return Track
         */
        public DiskImageTrack getTrack(int index) {
            DiskImageTrack track = null;
            if (tracks != null && index < tracks.size()) {
                track = tracks.get(index);
            }
            return track;
        }

        /**
         * Returns track from specified offset value
         *
         * @param offset Offset position
         * @return Track
         */
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
            return getSector(trackNumber, sideNumber, sectorNumber, -1);
        }

        /**
         * Returns specified sector
         *
         * @param trackNumber  Track number (cylinder)
         * @param sideNumber   Side number (head)
         * @param sectorNumber Sector number (record)
         * @param density      Filter by density 0: Double density, 1: Single density, -1: Exclude from criteria
         * @return Sector
         */
        public DiskImageSector getSector(int trackNumber, int sideNumber, int sectorNumber, int density) {
            DiskImageTrack track = getTrack(trackNumber, sideNumber);
            if (track == null) return null;
            return track.getSector(sectorNumber, density);
        }

        /**
         * Set plausible parameters within the disk
         * <p>
         * system property
         * <li>`l3diskex.normalize.enabled` ... do normalize after fetch params or not default {@code true}</li>
         * This is used for cases where the analysis incorrectly identifies 3 surfaces,
         * which can prevent a suitable template from being found.
         *
         * @return Parameters
         */
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

            int sectorMaxSize;
            int interleaveMax;
            List<DiskParticular> singles = new ArrayList<>();

            if (tracks != null) {
                for (DiskImageTrack t : tracks) {
                    int trackNum = t.getTrackNumber();
                    int sideNum = t.getSideNumber();
//logger.log(Level.TRACE, "trackNum: " + trackNum + ", sideNum: " + sideNum);

                    trackNumberMin = IntHashMapUtil.minValue(trackNumberMin, trackNum);
                    trackNumberMax = IntHashMapUtil.maxValue(trackNumberMax, trackNum);

                    if (trackNum > 0) {
                        sideNumberMin = IntHashMapUtil.minValue(sideNumberMin, sideNum);
                        sideNumberMax = IntHashMapUtil.maxValue(sideNumberMax, sideNum);
                    }

                    IntHashMapUtil.increaseValue(sectorSizeMap, t.getMaxSectorSize());
                    IntHashMapUtil.increaseValue(interleaveMap, t.getInterleave());

                    sideNum &= 0x7f;

                    if (sideNum >= 0 && sideNum < 2) {
                        IntHashMapUtil.increaseValue(sectorNumbersMap[sideNum], t.getSectorsPerTrack());
                    }

                    if (trackNum > 0 && sideNum < 2) {
                        int sectorNumMax = t.getMaxSectorNumber();
                        int sectorNumMin = t.getMinSectorNumber();
                        if (sideNum == 0) {
                            sectorNumberMaxSide0 = IntHashMapUtil.maxValue(sectorNumberMaxSide0, sectorNumMax);
                            sectorNumberMinSide0 = IntHashMapUtil.minValue(sectorNumberMinSide0, sectorNumMin);
                        } else {
                            sectorNumberMinSide1 = IntHashMapUtil.minValue(sectorNumberMinSide1, sectorNumMin);
                        }
                    }

                    List<DiskImageSector> sectors = t.getSectors();
                    if (sectors != null) {
                        List<DiskParticular> sis = new ArrayList<>();
                        for (DiskImageSector s : sectors) {
                            if (s != null && s.isSingleDensity()) {
                                DiskParticular sd = new DiskParticular(t.getTrackNumber(), t.getSideNumber(), s.getSectorNumber(), 1, s.getSectorsPerTrack(), s.getSectorSize());
                                sis.add(sd);
                            }
                        }
                        DiskParticular.uniqueSectors(t.getSectorsPerTrack(), sis);
                        singles.addAll(sis);
                    }
                }
            }
            sectorMaxSize = IntHashMapUtil.getMaxKeyOnMaxValue(sectorSizeMap);
            interleaveMax = IntHashMapUtil.getMaxKeyOnMaxValue(interleaveMap);

            sidesPerDisk = sideNumberMax + 1 - sideNumberMin;

            if (Boolean.parseBoolean(System.getProperty("l3diskex.normalize.enabled", "true"))) {
                // normalize a parameter
                if (sidesPerDisk > 2) {
logger.log(Level.TRACE, "NORMALIZE: sidesPerDisk: 2 <- " + sidesPerDisk);
                    sidesPerDisk = Math.min(sidesPerDisk, 2);
                }
            }
logger.log(Level.TRACE, "sidesPerDisk: " + sidesPerDisk + ", sideNumberMax: " + sideNumberMax + ", sideNumberMin: " + sideNumberMin);

            if (tracks != null) {
                int trackCount = (tracks.size() + sidesPerDisk - 1) / sidesPerDisk;
                if (trackNumberMax > (trackCount + 4)) {
                    trackNumberMax = trackCount - 1;
                }
            }

            boolean diskSingleType = false;
            if (tracks != null) {
                if (sectorMaxSize == 128 && sideNumberMax == 0 && maxTrackNumber > trackNumberMax) {
                    diskSingleType = true;
                    sideNumberMax++;
                    sidesPerDisk++;
logger.log(Level.TRACE, "sidesPerDisk: " + sidesPerDisk);
                    for (DiskImageTrack t : tracks) {
                        if ((t.getOffsetPos() & 1) != 0) {
                            t.setSideNumber(1);
                        }
                    }
                }
            }

            DiskParticular.uniqueTracks(trackNumberMax - trackNumberMin + 1, sidesPerDisk, diskSingleType, singles);

            tracksPerSide = tracks != null ? (trackNumberMax - trackNumberMin + 1) : 0;
            sectorSize = sectorMaxSize;
            interleave = interleaveMax;

            if (sidesPerDisk > 1 && sectorNumberMinSide1 != 0x7fff_ffff && sectorNumberMaxSide0 < sectorNumberMinSide1) {
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

            List<DiskParticular> pTracks = new ArrayList<>();
            if (tracks != null) {
                for (DiskImageTrack t : tracks) {
                    if (t == null) continue;
                    List<DiskImageSector> ss = t.getSectors();
                    if (ss == null) continue;
                    if (ss.size() != sectorsPerTrack) {
                        pTracks.add(new DiskParticular(t.getTrackNumber(), t.getSideNumber(), -1, 1, ss.size(), t.getMaxSectorSize()));
                    }
                }
                DiskParticular.uniqueTracks(tracksPerSide, sidesPerDisk, false, pTracks);
            }

            DiskParam diskParam = diskTemplates.find(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize, interleave, trackNumberMin, sideNumberMin, sectorNumberMinSide0, numberingSector, singles, pTracks);
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

logger.log(Level.TRACE, "diskParam: " + diskParam);
            return diskParam;
        }

        /**
         * Initialize disk content (0 padding)
         *
         * @param selectedSide >=0 initialize only specified side
         */
        public boolean initialize(int selectedSide) {
            if (tracks == null) {
                return false;
            }

            boolean rc = true;
            for (DiskImageTrack track : tracks) {
                if (selectedSide >= 0) {
                    if (selectedSide != track.getSideNumber()) {
                        continue;
                    }
                }

                List<DiskImageSector> sectors = track.getSectors();
                if (sectors == null) {
                    continue;
                }

                for (DiskImageSector sector : sectors) {
                    if (sector != null) {
                        sector.fill((byte) 0, -1, 0);
                    }
                }
            }
            return rc;
        }

        /**
         * Recreate tracks of the disk
         *
         * @param param        Parameters
         * @param selectedSide >= 0 initialize only specified side
         */
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
            int trackNum = param.getTrackNumberBaseOnDisk();
            int numOfTracks = param.getTracksPerSide() + trackNum;
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
                int trackSize = cr.createTrack(trackNum, sid, pos, offset, this);
                setOffset(pos, offset);
                setSize(offset + trackSize);

                sid++;
                if (sid >= sides || selectedSide >= 0) {
                    trackNum++;
                    sid = 0;
                }
                if (trackNum >= numOfTracks) {
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
            return offsetStart;
        }

        public void setOffsetStart(int val) {
            offsetStart = val;
        }

        public int getMaxTrackNumber() {
            return maxTrackNumber;
        }

        public void setMaxTrackNumber(int pos) {
            maxTrackNumber = pos;
        }

        public int getCreatableTracks() {
            return 0;
        }

        /** Set as modified */
        public void setModify() {
        }

        /** Whether it has been modified */
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

        /** Clear modified flag */
        public void clearModify() {
            if (tracks != null) {
                for (DiskImageTrack track : tracks) {
                    if (track == null) continue;
                    track.clearModify();
                }
            }
        }

        /** Whether track exists */
        public boolean existTrack(int sideNumber) {
            boolean found = false;
            List<DiskImageTrack> tracks = getTracks();
            if (tracks != null) {
                for (DiskImageTrack trk : tracks) {
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
            paramChanged = val;
        }

        public boolean getParamChanged() {
            return paramChanged;
        }

        /** Allocate DISK BASIC area */
        public void allocDiskBasics() {
            DiskBasic nullDiskBasic = new DiskBasic();
            basics.add(nullDiskBasic);
            if (reversible) basics.add(nullDiskBasic);
        }

        /** Returns DISK BASIC */
        public DiskBasic getDiskBasic(int idx) {
            if (idx < 0) idx = 0;
            return basics.get(idx);
        }

        public List<DiskBasic> getDiskBasics() {
            return basics;
        }

        /** Clear DISK BASIC */
        public void clearDiskBasics() {
            if (basics == null) return;
            for (int index = 0; index < basics.size(); index++) {
                DiskBasic basic = getDiskBasic(index);
                if (basic == null) continue;
                basic.clearParseAndAssign(false);
            }
        }

        /** Set character code map number */
        public void setCharCode(String name) {
            if (basics == null) return;
            for (int index = 0; index < basics.size(); index++) {
                DiskBasic basic = getDiskBasic(index);
                if (basic == null) continue;
                basic.setCharCode(name);
            }
        }

        /** Compare disk numbers */
        public static int compare(DiskImageDisk item1, DiskImageDisk item2) {
            return item1.num - item2.num;
        }

        protected int sidesPerDisk;
        protected int tracksPerSide;
        protected int sectorsPerTrack;
        protected int sectorSize;
        protected int interleave;
        protected int numberingSector;
        protected boolean reversible;
    }

    /** Class that holds a pointer to the disk image */
    public static abstract class DiskImageFile {

        /** Image */
        protected DiskImage image;
        /** Disk */
        protected List<DiskImageDisk> disks;
        /** Modification flag: whether added or not */
        protected List<Short> mods;

        /** BASIC type hint */
        protected String basicTypeHint = "";

        public DiskImageFile(DiskImage image) {
            this.image = image;
            disks = null;
            mods = null;
        }

        /**
         * Create instance
         *
         * @param num Disk number
         */
        public abstract DiskImageDisk newImageDisk(int num);

        /**
         * Create instance
         *
         * @param num          Disk number
         * @param param        Disk parameters
         * @param diskName     Disk name
         * @param writeProtect Whether write protected
         */
        public abstract DiskImageDisk newImageDisk(int num, DiskParam param, String diskName, boolean writeProtect);

        /**
         * Create instance
         *
         * @param num    Disk number
         * @param header Disk header
         */
        public abstract DiskImageDisk newImageDisk(int num, DiskImageDiskHeader header);

        // Modification flag: whether added or not
        public static final short MODIFY_NONE = 0;
        public static final short MODIFY_ADD = 1;

        /** Add disk */
        public int add(DiskImageDisk newDisk, short modFlags) {
            if (disks == null) disks = new ArrayList<>();
            if (mods == null) mods = new ArrayList<>();
            disks.add(newDisk);
            mods.add(modFlags);
            return disks.size();
        }

        /** Delete all disks */
        public void clear() {
            if (disks != null) {
                disks.clear();
                disks = null;
            }
            if (mods != null) {
                mods = null;
            }
        }

        /** Returns number of disks */
        public int count() {
            if (disks == null) return 0;
            return disks.size();
        }

        /** Delete disk */
        public boolean delete(int index) {
            DiskImageDisk disk = getDisk(index);
            if (disk == null) return false;
            disks.remove(index);
            mods.remove(index);
            return true;
        }

        /** Returns disk */
        public List<DiskImageDisk> getDisks() {
            return disks;
        }

        /** Returns disk */
        public DiskImageDisk getDisk(int index) {
            if (disks == null) return null;
            if (index >= disks.size()) return null;
            return disks.get(index);
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
            return basicTypeHint;
        }

        public void setBasicTypeHint(String val) {
            basicTypeHint = val;
        }

        /** Return the image */
        public DiskImage getImage() {
            return image;
        }
    }

    protected Path filename;
    protected DiskImageFile file;
    protected DiskResult result = new DiskResult();
    protected String formatType;

    /** Create a file structure */
    protected void newFile(String filepath) {
        if (file != null) {
            file = null;
        }
        file = newImageFile();
        filename = Path.of(filepath);
    }

    /** Clear the file structure */
    protected void clearFile() {
        file = null;
    }

    public DiskImage() {
        file = null;
    }

    /** Instance creation */
    public abstract DiskImageFile newImageFile();

    /**
     * Create New
     *
     * @param diskName     Disk Name
     * @param param        Disk Parameters
     * @param writeProtect Write-protected
     * @param basicHint    DISK BASIC type hints
     * @return 0: normal, -1: There is an error, 1: With warning
     */
    public int create(String diskName, DiskParam param, boolean writeProtect, String basicHint) {
        result.clear();

        newFile("");
        file.setBasicTypeHint(basicHint);
        DiskImageCreator cr = new DiskImageCreator(diskName, param, writeProtect, file, result);
        int validDisk = cr.create();

        // There is an error
        if (validDisk < 0) {
            clearFile();
        }
        return validDisk;
    }

    /**
     * Add new
     *
     * @param diskName     Disk Name
     * @param param        Disk Parameters
     * @param writeProtect Write-protected
     * @param basicHint    DISK BASIC type hints
     * @return 0: normal, -1: There is an error, 1: With warning
     */
    public int add(String diskName, DiskParam param, boolean writeProtect, String basicHint) {
        if (file == null) return 0;

        result.clear();

        file.setBasicTypeHint(basicHint);
        DiskImageCreator cr = new DiskImageCreator(diskName, param, writeProtect, file, result);
        int validDisk = cr.add();

        return validDisk;
    }

    /**
     * Add File
     *
     * @param filepath   File Path
     * @param fileFormat File format name (e.g. "d88", "plain")
     * @param paramHint  Disk parameter hints (only for "plain")
     * @return 0: normal, -1: There is an error, 1: With warning
     */
    public int add(String filepath, String fileFormat, DiskParam paramHint) throws IOException {
        // File not open
        if (file == null) return 0;

        result.clear();

        SeekableDataInputStream fstream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));

        DiskParser ps = new DiskParser(filepath, fstream, file, result);
        int validDisk = ps.parseAdd(fileFormat, paramHint);

        return validDisk;
    }

    /**
     * Open a file
     *
     * @param filepath   File Path
     * @param fileFormat File format name (e.g. "d88", "plain")
     * @param paramHint  Disk parameter hints (only for "plain")
     * @return 0: normal, -1: There is an error, 1: With warning
     */
    public int open(String filepath, String fileFormat, DiskParam paramHint) throws IOException {
        result.clear();

        // Open a file
        SeekableDataInputStream fStream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));

        newFile(filepath);
        DiskParser parser = new DiskParser(filepath, fStream, file, result);
        int validDisk = parser.parse(fileFormat, paramHint);

        if (validDisk < 0) {
            // There is an error
            clearFile();
        } else {
            setFormatType(fileFormat);
        }

        return validDisk;
    }

    /**
     * Check before opening file
     *
     * @param filepath    File path
     * @param fileFormat  [in,out] File format name ("d88", "plain", "" etc.)
     * @param params      [out]  Disk parameter candidates
     * @param manualParam [out]  Parameter hint when there are no candidates
     * @return 0: No problem, -1: With error, 1: With warning
     */
    public int check(String filepath, String[] fileFormat, List<DiskParam> params, DiskParam manualParam) throws IOException {
        result.clear();

        // Open file
        SeekableDataInputStream fStream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));

        DiskParser parser = new DiskParser(filepath, fStream, file, result);
        return parser.check(fileFormat, params, manualParam);
    }

    /** Close */
    public void close() {
        clearFile();
        filename = null;
    }

    /**
     * Whether stream content can be saved to file
     *
     * @param fileFormat Format of saved file
     */
    public int canSave(String fileFormat) {
        DiskWriter dw = new DiskWriter(this, result);
        return dw.canSave(fileFormat);
    }

    /**
     * Save stream content to file
     *
     * @param filepath   Destination file path
     * @param fileFormat Format of saved file
     * @param options    Options at save
     * @return 0: Normal, -1: Error
     */
    public int save(String filepath, String fileFormat, DiskWriteOptions options) throws IOException {
        DiskWriter writer = new DiskWriter(this, filepath, options, result);
        return writer.save(fileFormat);
    }

    /**
     * Save stream content to file
     *
     * @param diskNumber Disk number
     * @param sideNumber Side number
     * @param filepath   Destination file path
     * @param fileFormat Format of saved file
     * @param options    Options at save
     * @return 0: Normal, -1: Error
     */
    public int saveDisk(int diskNumber, int sideNumber, String filepath, String fileFormat, DiskWriteOptions options) throws IOException {
        DiskWriter writer = new DiskWriter(this, filepath, options, result);
        return writer.saveDisk(diskNumber, sideNumber, fileFormat);
    }

    /**
     * Delete disk
     *
     * @param diskNumber Disk number
     * @return true
     */
    public boolean delete(int diskNumber) {
        if (file == null) return false;
        file.delete(diskNumber);
        return true;
    }

    /**
     * Analyze source disk for replacement
     *
     * @param diskNumber Disk number
     * @param sideNumber Side number
     * @param filepath   File path
     * @param fileFormat File format name ("d88", "plain", etc.)
     * @param paramHint  Disk parameter hint (only for "plain")
     * @param srcFile    [out] Source disk
     * @param targetDisk [out] Target disk
     * @return 0: normal, -1: error exists, 1: warning exists
     */
    public int parseForReplace(int diskNumber, int sideNumber, String filepath, String fileFormat, DiskParam paramHint, DiskImageFile srcFile, DiskImageDisk[] targetDisk) throws IOException {
        if (file == null) return 0;

        result.clear();

        SeekableDataInputStream fstream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));

        DiskParser parser = new DiskParser(filepath, fstream, srcFile, result);
        int validDisk = parser.parse(fileFormat, paramHint);

        // error exists
        if (validDisk < 0) {
            return validDisk;
        }

        // Select disk
        targetDisk[0] = file.getDisk(diskNumber);
        if (targetDisk[0] == null) {
            result.setError(DiskResult.ERR_NO_DATA);
            return result.getValid();
        }

        return 0;
    }

    /**
     * Replace disk with file
     *
     * @param diskNumber    Disk number
     * @param sideNumber    Side number
     * @param srcDisk       Source disk
     * @param srcSideNumber Side number on source side
     * @param tagDisk       Target disk
     * @return 0: Normal, -1: With error, 1: With warning
     */
    public int replaceDisk(int diskNumber, int sideNumber, DiskImage.DiskImageDisk srcDisk, int srcSideNumber, DiskImageDisk tagDisk) {
        if (file == null) return 0;

        result.clear();

        int validDisk = tagDisk.replace(sideNumber, srcDisk, srcSideNumber);
        if (validDisk != 0) {
            result.setError(DiskResult.ERR_REPLACE);
        }

        return validDisk;
    }

    /** Set disk name */
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

    /** Returns disk name */
    public String getDiskName(int diskNumber, boolean real) {
        DiskImageDisk disk = getDisk(diskNumber);
        if (disk == null) return "";
        return disk.getName(real);
    }

    /** Whether disk was changed */
    public boolean isModified() {
        boolean modified = false;
        if (file != null) {
            modified = file.isModified();
        }
        return modified;
    }

    /** Returns disk file */
    public DiskImageFile getFile() {
        return file;
    }

    /** Number of disks */
    public int countDisks() {
        if (file == null) return 0;
        return file.count();
    }

    /** Returns disk list */
    public List<DiskImageDisk> getDisks() {
        if (file == null) return null;
        return file.getDisks();
    }

    /** Returns disk at specified position */
    public DiskImageDisk getDisk(int index) {
        if (file == null) return null;
        return file.getDisk(index);
    }

    /** Type of disk at specified position */
    public int getDiskTypeNumber(int index) {
        if (file == null) return -1;
        DiskImageDisk disk = file.getDisk(index);
        if (disk == null) return -1;
        return diskTemplates.indexOf(disk.getDiskTypeName());
    }

    /** Returns number of tracks that can be created */
    public int getCreatableTracks() {
        return 0;
    }

    /** Returns file name */
    public String getFileName() {
        return filename.getFileName().toString();
    }

    /** Returns file extension */
    public String getFileExt() {
        return Utils.getExt(getFileName());
    }

    /** Returns file name base */
    public String getFileNameBase() {
        String name = getFileName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    /** Returns file path */
    public String getFilePath() {
        return filename.toAbsolutePath().toString();
    }

    /** Returns path */
    public String getPath() {
        return filename.getParent().toString();
    }

    /** Set file name */
    public void setFileName(String path) {
        filename = Path.of(path);
    }

    /** Set file extension */
    public void setFileExt(String ext) {
        filename = filename.getParent().resolve(getFileNameBase() + "." + ext);
    }

    /** Returns file format type */
    public String getFormatType() {
        return formatType;
    }

    /** Set file format type */
    public void setFormatType(String formatType) {
        this.formatType = formatType;
    }

    /** Whether DISK BASIC matches */
    public boolean matchDiskBasic(DiskBasic target) {
        boolean match = false;
        List<DiskImageDisk> disks = getDisks();
        if (disks == null) return false;
        for (DiskImageDisk disk : disks) {
            List<DiskBasic> basics = disk.getDiskBasics();
            if (basics == null) return false;
            for (DiskBasic basic : basics) {
                if (target == basic) {
                    match = true;
                    break;
                }
            }
        }
        return match;
    }

    /** Clear parsing status of DISK BASIC */
    public void clearDiskBasicParseAndAssign(int diskNumber, int sideNumber) {
        DiskImageDisk disk = getDisk(diskNumber);
        if (disk == null) return;

        if (file != null) {
            file.setBasicTypeHint("");
        }

        List<DiskBasic> basics = disk.getDiskBasics();
        if (basics == null) return;
        clearParseAndAssign(basics, sideNumber);
    }

    /** Character code map number setting */
    public void setCharCode(String name) {
        List<DiskImageDisk> disks = getDisks();
        if (disks == null) return;
        for (DiskImageDisk disk : disks) {
            disk.setCharCode(name);
        }
    }

    /** Returns density string */
    public int getDensityNames(List<String> arr) {
        return 0;
    }

    /** Search density list */
    public int findDensity(int val) {
        return -1;
    }

    /** Search density list */
    public int findDensityByIndex(int idx) {
        return -1;
    }

    /** Returns value at specified position in density list */
    public byte getDensity(int idx) {
        return 0;
    }

    /** Error message */
    public List<String> getErrorMessage(int maxrow) {
        return result.getMessages(maxrow);
    }

    /** Show error message */
    public void showErrorMessage() {
        ResultInfo.showMessage(result.getValid(), result.getMessages(-1));
    }

    /** Show error warning message */
    public int showErrWarnMessage() {
        return ResultInfo.showErrWarnMessage(result.getValid(), result.getMessages(-1));
    }
}
