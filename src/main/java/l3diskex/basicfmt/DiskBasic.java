///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

import l3diskex.CharCodes;
import l3diskex.Common;
import l3diskex.Parambase.MyAttribute;
import l3diskex.ResultInfo;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicParams;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import vavi.io.SeekableDataInputStream;
import vavi.io.SeekableDataOutputStream;

import static java.lang.System.getLogger;
import static l3diskex.basicfmt.BasicCommon.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.DiskBasicDirItem.DATETIME_ACCESS;
import static l3diskex.basicfmt.DiskBasicDirItem.DATETIME_CREATE;
import static l3diskex.basicfmt.DiskBasicDirItem.DATETIME_MODIFY;
import static l3diskex.basicfmt.DiskBasicTemplates.diskBasicTemplates;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_NEW;


/** DISK BASIC Structure analysis */
public class DiskBasic extends DiskParam {

    private static final Logger logger = getLogger(DiskBasic.class.getName());

    /** Formatter, dialogs */
    public static class DiskBasicIdentifiedData {

        /** Volume name */
        private String volumeName;
        /** Maximum volume name length */
        private int volumeNameMaxLen;
        /** Volume number */
        private int volumeNumber;
        /** Whether the volume number is hexadecimal */
        private boolean volumeNumberHex;
        /** Volume date */
        private String mVolumeDate;

        public DiskBasicIdentifiedData() {
            volumeNameMaxLen = 0;
            volumeNumber = 0;
            volumeNumberHex = false;
        }

        public DiskBasicIdentifiedData(String volumeName, int volumeNumber, String volumeDate) {
            this.volumeName = volumeName;
            volumeNameMaxLen = 0;
            this.volumeNumber = volumeNumber;
            volumeNumberHex = false;
            mVolumeDate = volumeDate;
        }

        public String getVolumeName() {
            return volumeName;
        }

        public int getVolumeNameMaxLength() {
            return volumeNameMaxLen;
        }

        public int getVolumeNumber() {
            return volumeNumber;
        }

        public boolean isVolumeNumberHex() {
            return volumeNumberHex;
        }

        public String getVolumeDate() {
            return mVolumeDate;
        }

        public void setVolumeName(String val) {
            volumeName = val;
        }

        public void setVolumeNameMaxLength(int val) {
            volumeNameMaxLen = val;
        }

        public void setVolumeNumber(int val) {
            volumeNumber = val;
        }

        public void volumeNumberIsHexa(boolean val) {
            volumeNumberHex = val;
        }

        public void setVolumeDate(String val) {
            mVolumeDate = val;
        }
    }

    //
    // DiskBasics
    //

    /** */
    public static void clearParseAndAssign(List<DiskBasic> basics, int idx) {
        if (basics == null) return;

        for (int i = 0; i < basics.size(); i++) {
            DiskBasic basic = basics.get(i);
            if (idx < 0 || i == idx) {
                basic.clearParseAndAssign(false);
            }
        }
    }

    //
    //
    //

    private final DiskBasicParam diskBasicParam; // For composition

    private DiskImageDisk disk;
    private boolean formatted;
    private boolean parsed;
    private boolean assigned;
    private boolean forcefully;

    private final DiskBasicFat fat;
    private final DiskBasicDir dir;
    private DiskBasicType type;

    /** Side (for 1S) */
    private int selectedSide;

    /** Starting sector number of data for group calculation */
    private int dataStartSector;
    /** Track number to skip when calculating groups */
    private int skippedTrack;

    /** Character encoding scheme for files, etc. */
    private String charCode;
    /** Character encoding conversion */
    private final CharCodes codes;

    /** For storing error information */
    private final DiskBasicError errInfo;

    public DiskBasic() {
        this.diskBasicParam = new DiskBasicParam();

        disk = null;
        formatted = false;
        parsed = false;
        assigned = false;
        forcefully = false;
        selectedSide = -1;
        dataStartSector = 0;
        skippedTrack = 0x7fff;

        fat = new DiskBasicFat(this);
        dir = new DiskBasicDir<>(this);
        type = null;

        codes = new CharCodes();
        errInfo = new DiskBasicError();
    }

    public void setDiskParam(DiskImageDisk disk) {
        super.setDiskParam(disk);
        this.disk = disk;
    }

    public void setBasicParam(DiskBasicParam param) {
        this.diskBasicParam.setBasicParam(param);
    }

    @Override
    public void clearDiskParam() {
        super.clearDiskParam();
        this.disk = null;
    }

    public void clearBasicParam() {
        this.diskBasicParam.clearBasicParam();
    }

//#region Delegate methods for DiskBasicParam

    /** BASIC type */
    public DiskBasicFormat getFormatType() {
        return diskBasicParam.getFormatType();
    }

    /** BASIC type */
    public int getSectorsPerTrackOnBasic() {
        return diskBasicParam.getSectorsPerTrackOnBasic();
    }

    /** Number of sectors per track used by BASIC */
    public void setSectorsPerTrackOnBasic(int val) {
        diskBasicParam.setSectorsPerTrackOnBasic(val);
    }

    /** Number of tracks per side used by BASIC */
    public int getTracksPerSideOnBasic() {
        return diskBasicParam.getTracksPerSideOnBasic();
    }

    /** Number of tracks per side used by BASIC */
    public void setTracksPerSideOnBasic(int val) {
        diskBasicParam.setTracksPerSideOnBasic(val);
    }

    /** Number of sides used by BASIC */
    public int getSidesPerDiskOnBasic() {
        return diskBasicParam.getSidesPerDiskOnBasic();
    }

    /** Number of sides used by BASIC */
    public void setSidesPerDiskOnBasic(int val) {
        diskBasicParam.setSidesPerDiskOnBasic(val);
    }

    /** BASIC type name */
    public String getBasicTypeName() {
        return diskBasicParam.getBasicTypeName();
    }

    /** Description */
    public String getBasicDescription() {
        return diskBasicParam.getBasicDescription();
    }

    /** Calculate starting and ending sectors */
    public void calcDirStartEndSector(int sectorSize) {
        diskBasicParam.calcDirStartEndSector(sectorSize);
    }

    /** Track number where the file management area is located */
    public int getManagedTrackNumber() {
        return diskBasicParam.getManagedTrackNumber();
    }

    /** Whether to distinguish between uppercase and lowercase when comparing file names */
    public boolean isCompareCaseInsensitive() {
        return diskBasicParam.isCompareCaseInsensitive();
    }

    /** Group (cluster) size */
    public int getSectorsPerGroup() {
        return diskBasicParam.getSectorsPerGroup();
    }

    /** Initial number of groups for subdirectory */
    public int getSubDirGroupSize() {
        return diskBasicParam.getSubDirGroupSize();
    }

    /** Maximum FAT group number */
    public int getFatEndGroup() {
        return diskBasicParam.getFatEndGroup();
    }

    /** Sector number base used by BASIC */
    public int getSectorNumberBaseOnBasic() {
        return diskBasicParam.getSectorNumberBaseOnBasic();
    }

    /** Valid density */
    public int getValidDensityType() {
        return diskBasicParam.getValidDensityType();
    }

    /** Returns the reversed side number */
    public int getReversedSideNumber(int sideNum) {
        return diskBasicParam.getReversedSideNumber(sideNum);
    }

    /** Whether data bits are inverted */
    public boolean isDataInverted() {
        return diskBasicParam.isDataInverted();
    }

    /** Whether byte order is big endian */
    public boolean isBigEndian() {
        return diskBasicParam.isBigEndian();
    }

    /** Whether each side can be accessed independently in an OS that uses only one side */
    public boolean canMountEachSides() {
        return diskBasicParam.canMountEachSides();
    }

    /** Code for final group */
    public int getGroupFinalCode() {
        return diskBasicParam.getGroupFinalCode();
    }

    /** Code for unused */
    public int getGroupUnusedCode() {
        return diskBasicParam.getGroupUnusedCode();
    }

    /** Code used by the system */
    public int getGroupSystemCode() {
        return diskBasicParam.getGroupSystemCode();
    }

    /** Specific parameters */
    public int getVariousIntegerParam(String key) {
        return diskBasicParam.getVariousIntegerParam(key);
    }

    /** Root directory starting sector */
    public int getDirStartSector() {
        return diskBasicParam.getDirStartSector();
    }

    /** Maximum FAT group number */
    public void setFatEndGroup(int end) {
        diskBasicParam.setFatEndGroup(end);
    }

    /** Root directory ending sector */
    public int getDirEndSector() {
        return diskBasicParam.getDirEndSector();
    }

    /** Code put between file name and extension (' . ') */
    public byte getExtensionPreCode() {
        return diskBasicParam.getExtensionPreCode();
    }

    /** Number of groups per track */
    public int getGroupsPerTrack() {
        return diskBasicParam.getGroupsPerTrack();
    }

    /** Whether to convert to uppercase after file name dialog input */
    public boolean toUpperAfterRenamed() {
        return diskBasicParam.toUpperAfterRenamed();
    }

    /** Whether to convert to uppercase before file name dialog display */
    public boolean toUpperBeforeDialog() {
        return diskBasicParam.toUpperBeforeDialog();
    }

    /** Relationship between extension and attributes */
    public List<MyAttribute> getAttributesByExtension() {
        return diskBasicParam.getAttributesByExtension();
    }

    /** Special attributes */
    public List<MyAttribute> getSpecialAttributes() {
        return diskBasicParam.getSpecialAttributes();
    }

    /** Code to fill free area of directory entries */
    public byte getDirSpaceCode() {
        return diskBasicParam.getDirSpaceCode();
    }

    /** Termination code of directory entries */
    public byte getDirTerminateCode() {
        return diskBasicParam.getDirTerminateCode();
    }

    /** Code to fill unused area of directory entries */
    public byte getDirTrimmingCode() {
        return diskBasicParam.getDirTrimmingCode();
    }

    /** Termination code of text files */
    public byte getTextTerminateCode() {
        return diskBasicParam.getTextTerminateCode();
    }

    /** Starting position of directory entries (sector) */
    public int getDirStartPosOnSector() {
        return diskBasicParam.getDirStartPosOnSector();
    }

    /** Starting position of directory entries (group) */
    public int getDirStartPosOnGroup() {
        return diskBasicParam.getDirStartPosOnGroup();
    }

    /** Code to set when deleting a file */
    public byte getDeleteCode() {
        return diskBasicParam.getDeleteCode();
    }

    /** FAT starting sector */
    public int getFatStartSector() {
        return diskBasicParam.getFatStartSector();
    }

    /** Side number where FAT is located */
    public int getFatSideNumber() {
        return diskBasicParam.getFatSideNumber();
    }

    /** Number of reserved sectors */
    public int getReservedSectors() {
        return diskBasicParam.getReservedSectors();
    }

    /** Number of FATs */
    public int getNumberOfFats() {
        return diskBasicParam.getNumberOfFats();
    }

    /** Number of sectors used by FAT */
    public int getSectorsPerFat() {
        return diskBasicParam.getSectorsPerFat();
    }

    /** FAT starting position (within sector) */
    public int getFatStartPos() {
        return diskBasicParam.getFatStartPos();
    }

    /** Valid number of FATs */
    public int getValidNumberOfFats() {
        return diskBasicParam.getValidNumberOfFats();
    }

    /** Sector skew map */
    public int getSectorSkewMap(int idx) {
        return diskBasicParam.getSectorSkewMap(idx);
    }

    /** Whether it has a sector skew map */
    public boolean hasSectorSkewMap() {
        return diskBasicParam.hasSectorSkewMap();
    }

    /** Sector skew */
    public int getSectorSkew() {
        return diskBasicParam.getSectorSkew();
    }

    /** Number of sectors per track used by BASIC (variable length) */
    public List<NumSectorsParam> sectorsPerTrackOnBasicList() {
        return diskBasicParam.sectorsPerTrackOnBasicList();
    }

    /** Root directory starting position */
    public int getDirStartPosOnRoot() {
        return diskBasicParam.getDirStartPosOnRoot();
    }

    /** Directory starting position */
    public int getDirStartPos() {
        return diskBasicParam.getDirStartPos();
    }

    /** Code to fill during formatting */
    public byte getFillCodeOnFormat() {
        return diskBasicParam.getFillCodeOnFormat();
    }

    /** Number of groups per track */
    public void setGroupsPerTrack(int val) {
        diskBasicParam.setGroupsPerTrack(val);
    }

    /** Number of sectors used by FAT */
    public void setSectorsPerFat(int val) {
        diskBasicParam.setSectorsPerFat(val);
    }

    /** Code to fill FAT area */
    public byte getFillCodeOnFAT() {
        return diskBasicParam.getFillCodeOnFAT();
    }

    /** Code to fill DIR area */
    public byte getFillCodeOnDir() {
        return diskBasicParam.getFillCodeOnDir();
    }

    /** Bit width to hold FAT group number (12 or 16) */
    public int getGroupWidth() {
        return diskBasicParam.getGroupWidth();
    }

    /** Number of groups used by a directory entry */
    public int getGroupsPerDirEntry() {
        return diskBasicParam.getGroupsPerDirEntry();
    }

    /** Reserved group numbers */
    public List<Integer> getReservedGroups() {
        return diskBasicParam.getReservedGroups();
    }

    /** Root directory starting sector */
    public void setDirStartSector(int val) {
        diskBasicParam.setDirStartSector(val);
    }

    /** Root directory ending sector */
    public void setDirEndSector(int val) {
        diskBasicParam.setDirEndSector(val);
    }

    /** Bit width to hold FAT group number (12 or 16) */
    public void setGroupWidth(int val) {
        diskBasicParam.setGroupWidth(val);
    }

    /** Number of groups per sector */
    public int getGroupsPerSector() {
        return diskBasicParam.getGroupsPerSector();
    }

    /** Specific parameters */
    public String getVariousStringParam(String key) {
        return diskBasicParam.getVariousStringParam(key);
    }

    /** BASIC type sub-number */
    public int getFormatSubTypeNumber() {
        return diskBasicParam.getFormatSubTypeNumber();
    }

    /** Group (cluster) size */
    public void setSectorsPerGroup(int val) {
        diskBasicParam.setSectorsPerGroup(val);
    }

    /** Track number where the file management area is located */
    public void setManagedTrackNumber(int val) {
        diskBasicParam.setManagedTrackNumber(val);
    }

    /** Number of groups per sector */
    public void setGroupsPerSector(int val) {
        diskBasicParam.setGroupsPerSector(val);
    }

    /** Specific parameters */
    public boolean getVariousBoolParam(String key) {
        return diskBasicParam.getVariousBoolParam(key);
    }

    /** Specific parameters */
    public void setVariousParam(String key, boolean val) {
        diskBasicParam.setVariousParam(key, val);
    }

    /** Media ID */
    public byte getMediaId() {
        return diskBasicParam.getMediaId();
    }

    /** Number of reserved sectors */
    public void setReservedSectors(int val) {
        diskBasicParam.setReservedSectors(val);
    }

    /** Number of FATs */
    public void setNumberOfFats(int val) {
        diskBasicParam.setNumberOfFats(val);
    }

    /** Number of directory entries in root directory */
    public void setDirEntryCount(int val) {
        diskBasicParam.setDirEntryCount(val);
    }

    /** Media ID */
    public void setMediaId(byte val) {
        diskBasicParam.setMediaId(val);
    }

    /** BASIC category name */
    public String getBasicCategoryName() {
        return diskBasicParam.getBasicCategoryName();
    }

    /** Description */
    public void setBasicDescription(String str) {
        diskBasicParam.setBasicDescription(str);
    }

    /** Number of directory entries in root directory */
    public int getDirEntryCount() {
        return diskBasicParam.getDirEntryCount();
    }

//#endregion

    /** Set BASIC type */
    private void createType() {
        type = null;

        DiskBasicFormat format = getFormatType();
logger.log(Level.TRACE, "format: " + format.getTypeNumber());
        if (format == null) return;

        ServiceLoader<DiskBasicType> serviceLoader = ServiceLoader.load(DiskBasicType.class);
        for (DiskBasicType<?> diskBasicType : serviceLoader) {
            if (diskBasicType.isSupported(format.getTypeNumber())) {
                type = diskBasicType;
                type.init(this, fat, dir);
                return;
            }
        }

        logger.log(Level.WARNING, "Unknown type is defined in basic_type.xml.");
    }

    /** Returns the index of the highest value */
    private static int maxRatio(List<Double> values) {
        int idx = -1;
        double maxRatio = -1.0;
        for (int i = 0; i < values.size(); i++) {
            double ratio = values.get(i);
            if (ratio > maxRatio) {
                idx = i;
                maxRatio = ratio;
            }
        }
        return idx;
    }

    /**
     * Analyze if the specified disk is DISK BASIC
     *
     * @param newDisk      New disk
     * @param newSide      Side number. Only for single side. For double side, -1.
     * @param match        Parameters. Set if already known.
     * @param isFormatting Whether formatting is being executed.
     * @return >0: Warning, 0: Normal, <0: Error present
     */
    public int parseBasic(DiskImageDisk newDisk, int newSide, DiskBasicParam match, boolean isFormatting) throws IOException {
        errInfo.clear();

        selectedSide = newSide;

        if (assigned) return 0;

        disk = newDisk;
        formatted = false;

        logger.log(Level.INFO, "Parsing Disk #%d ...".formatted(newDisk.getNumber()));

        String hint = newDisk.getFile().getBasicTypeHint();
        List<DiskParamName> types = newDisk.getBasicTypes();

        // BASIC hint in the new disk
        DiskBasicParams validParams = new DiskBasicParams();
        // List of BASIC types in the new disk
        List<Double> validRatios = new ArrayList<>();

        double validRatio = 0.0;
        if (match == null) {
            boolean support = false;
            // Whether there is a supported DISK BASIC
            // When setting manually, hint and types are empty, so
            // search for matching parameters in the template
            match = diskBasicTemplates.findType(hint, types);
            if (match != null) {
                support = true;
            }
            if (!support) {
                // Cannot be used as DISK BASIC
                clear();
                errInfo.setError(DiskBasicError.ERR_SUPPORTED);
                return errInfo.getValid();
            }

            for (DiskParamName diskParamName : types) {
                match = diskBasicTemplates.findType(hint, diskParamName.getName());
                if (match != null) {
                    // Is it formatted?
                    logger.log(Level.INFO, "Parsing format: %s".formatted(match.getBasicTypeName()));
try {
                    validRatio = parseFormattedDisk(newDisk, match, isFormatting);
} catch (Exception e) {
 logger.log(Level.TRACE, e.getMessage(), e);
 logger.log(Level.TRACE, "Result => error");
 continue;
}
                    logger.log(Level.INFO, "Result => %.2f".formatted(validRatio));
                    if (validRatio >= 0.0) {
                        // Make it a candidate
                        validParams.list.add(match);
                        validRatios.add(validRatio);
                    }
                }
            }

            errInfo.clear();
            if (!validParams.list.isEmpty()) {
                // Make likely ones candidates
                int idx = maxRatio(validRatios);
                if (idx < 0) idx = 0;
                match = validParams.list.get(idx);
                // Check again
                logger.log(Level.INFO, "Decided format: %s\t\t\t\t\t🎉🎉🎉".formatted(match.getBasicTypeName()));
                validRatio = parseFormattedDisk(newDisk, match, isFormatting);
                logger.log(Level.INFO, "  Result => %.2f".formatted(validRatio));
            }
        } else {
            // Already formatted
            logger.log(Level.INFO, "Known format: %s".formatted(match.getBasicTypeName()));
            validRatio = parseFormattedDisk(newDisk, match, isFormatting);
            logger.log(Level.INFO, "  Result => %.2f".formatted(validRatio));
        }

        if (validRatio >= 0.6) {
            errInfo.clear();
            parsed = true;
        }
        if (forcefully) {
            parsed = true;
        }
        if (!formatted) {
            errInfo.setInfo(DiskBasicError.ERR_FORMATTED);
        }
        return forcefully ? 0 : errInfo.getValid();
    }

    /**
     * Parse and check if formatted with the specified DISK BASIC
     *
     * @param newDisk      New disk
     * @param match        DISK BASIC parameters
     * @param isFormatting Whether formatting is in progress.
     * @return <0.0: Error present
     */
    private double parseFormattedDisk(DiskImageDisk newDisk, DiskBasicParam match, boolean isFormatting) throws IOException {
        double validRatio = 0.0;

        setBasicParam(match);
        setDiskParam(newDisk);

        dir.setFormatType(getFormatType());

        // Prioritize sector counts specified in BASIC
        if (getSectorsPerTrackOnBasic() < 0) setSectorsPerTrackOnBasic(getSectorsPerTrack());
        //sectorsOnBasic = getSectorsOnBasic() >= 0 ? getSectorsOnBasic() : getSectorsPerTrack();
        // Prioritize track counts specified in BASIC
        if (getTracksPerSideOnBasic() < 0) setTracksPerSideOnBasic(getTracksPerSide());
        // Prioritize side counts specified in BASIC
        if (getSidesPerDiskOnBasic() <= 0) setSidesPerDiskOnBasic(getSidesPerDisk());

//        if (getSectorSize() <= 0) { // TODO what this (by me)???
//            logger.log(Level.WARNING, "sectorSize is 0");
//            return -1.0;
//        }
        calcDirStartEndSector(getSectorSize());
        createType();
        if (type == null) {
            return -1.0;
        }
logger.log(Level.TRACE, "type: " + type.getClass().getSimpleName());

        assignParameter();

        // Analyze parameters on disk if necessary
        double prmValidRatio = type.parseParamOnDisk(isFormatting);
        if (prmValidRatio < 0.0) {
            errInfo.setError(DiskBasicError.ERR_IN_PARAMETER_AREA);
        } else if (prmValidRatio < 1.0) {
            errInfo.setInfo(DiskBasicError.ERR_INVALID_IN_PARAMETER_AREA);
        }
logger.log(Level.TRACE, "prmValidRatio: " + prmValidRatio);
        validRatio += prmValidRatio;

        // Check FAT
        double fatValidRatio = 0.0;
        if (validRatio >= 0.0) {
            fatValidRatio = assignFat(isFormatting);
            if (!isFormatting && fatValidRatio < 0.0) {
                errInfo.setInfo(DiskBasicError.ERR_IN_FAT_AREA);
            }
logger.log(Level.TRACE, "fatValidRatio: " + fatValidRatio);
            validRatio += fatValidRatio;
        }

        // Check directory
        double dirValidRatio = 0.0;
        if (validRatio >= 0.0) {
            dirValidRatio = checkRootDirectory(isFormatting);
            if (!isFormatting && dirValidRatio < 0.0) {
                errInfo.setInfo(DiskBasicError.ERR_IN_DIRECTORY_AREA);
            }
logger.log(Level.TRACE, "dirValidRatio: " + dirValidRatio);
            validRatio += dirValidRatio;
        }

        if ((prmValidRatio >= 0.0 && fatValidRatio >= 0.0 && dirValidRatio >= 0.0) || isFormatting || forcefully) {
            // Formatting complete
            formatted = true;
        }

        validRatio /= 3.0;

        return validRatio;
    }

    /** Clear parameters */
    public void clear() {
        disk = null;
        formatted = false;
        parsed = false;
        assigned = false;
        forcefully = false;
        selectedSide = -1;

        clearDiskParam();
        clearBasicParam();

        //dir.clear();

        if (type != null) type.clearDiskFreeSize();
    }

    /** Returns disk image number */
    public int getDiskNumber() {
        return disk != null ? disk.getNumber() : -1;
    }

    /** Returns selected side string */
    public String getSelectedSideStr() {
        return Utils.getSideStr(selectedSide, canMountEachSides());
    }

    /** Get DISK BASIC description */
    public String getDescriptionDetails() {
        String desc = getBasicDescription();
        int freeSize = type != null ? type.getFreeDiskSize() : -1;
        int freeGroups = type != null ? type.getFreeGroupSize() : -1;
        if (!parsed) {
            desc += " ?";
        }
        NumberFormat formatter = NumberFormat.getInstance();
        desc += String.format(" [Free:%sbytes(%sgroups)]",
                freeSize >= 0 ? formatter.format(freeSize) : "---",
                freeGroups >= 0 ? formatter.format(freeGroups) : "---"
        );
        return desc;
    }

    /** Get free space status of FAT area */
    public void getFatAvailability(int[] offset, List<Integer>[] arr) {
        if (type != null) {
            type.getFatAvailability(offset, arr);
        }
    }

    /** Get directory item */
    public DiskBasicDirItem<?> getDirItem(int pos) {
        return dir.item(pos);
    }

    public DiskImageTrack getManagedTrack(int sectorPos, int[] sideNum, int[] sectorNum) {
        return getManagedTrack(sectorPos, sideNum, sectorNum, null, null);
    }

    /**
     * Get side number, sector number, and track of management area
     *
     * @param sectorPos Sector position (serial number starting from the first side & first sector of management track as 0)
     * @param sideNum   [out] Side number. Nullable
     * @param sectorNum [out] Sector number. Nullable
     * @param divNum    [out] Division number. Nullable
     * @param divNums   [out] Number of divisions. Nullable
     * @return Track data
     */
    public DiskImageTrack getManagedTrack(int sectorPos, int[] sideNum, int[] sectorNum, int[] divNum, int[] divNums) {
        int[] track0Num = {0}, side0Num = {0}, secNum = {0};
        type.getNumFromSectorPos(sectorPos, track0Num, side0Num, secNum, divNum, divNums);

        int trackNum = getManagedTrackNumber();
        trackNum += track0Num[0];
        trackNum -= getTrackNumberBaseOnDisk();

        if (sideNum != null) sideNum[0] = side0Num[0];
        if (sectorNum != null) sectorNum[0] = secNum[0];
        return disk.getTrack(trackNum, side0Num[0]);
    }

    public DiskImageSector getManagedSector(int sectorPos) {
        return getManagedSector(sectorPos, null, null, null, null, null);
    }

    public DiskImageSector getManagedSector(int sectorPos, int[] trackNum, int[] sideNum) {
        return getManagedSector(sectorPos, trackNum, sideNum, null, null, null);
    }

    /**
     * Get track number, side number, sector number, and sector pointer of management area
     *
     * @param sectorPos Sector position (serial number starting from the first side & first sector of management track as 0)
     * @param trackNum  [out] Track number. Nullable
     * @param sideNum   [out] Side number. Nullable
     * @param sectorNum [out] Sector number. Nullable
     * @param divNum    [out] Division number. Nullable
     * @param numOfDivs   [out] Number of divisions. Nullable
     * @return Sector data
     */
    public DiskImageSector getManagedSector(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] numOfDivs) {
        int[] secNum = {0};
        DiskImageTrack track = getManagedTrack(sectorPos, sideNum, secNum, divNum, numOfDivs);
        if (track == null) return null;
        if (trackNum != null) trackNum[0] = track.getTrackNumber();
        if (sectorNum != null) sectorNum[0] = secNum[0];
        return track.getSector(secNum[0]);
    }

    /**
     * Whether it is enough for the remaining disk size that can be used by DISK BASIC
     *
     * @param size Specified size
     * @return true: Enough, false: Not enough
     */
    public boolean hasFreeDiskSize(int size) {
        boolean enough = true;
        if (size > disk.getSizeWithoutHeader()) {
            errInfo.setError(DiskBasicError.ERR_FILE_TOO_LARGE);
            enough = false;
        } else if (size > type.getFreeDiskSize()) {
            errInfo.setError(DiskBasicError.ERR_NOT_ENOUGH_FREE);
            enough = false;
        } else if (!type.isEnoughFileSize(size)) {
            errInfo.setError(DiskBasicError.ERR_NOT_ENOUGH_FREE);
            enough = false;
        }
        return enough;
    }

    /** Returns remaining disk size that can be used by DISK BASIC */
    public int getFreeDiskSize() {
        return type.getFreeDiskSize();
    }

    /** Set specific parameters */
    public void assignParameter() {
        dataStartSector = type.calcDataStartSectorPos();
        skippedTrack = type.calcSkippedTrack();
    }

    /**
     * Assign FAT area of the currently selected disk
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, <1.0: Warning present, <0.0: Error present
     */
    public double assignFat(boolean isFormatting) throws IOException {
        if (disk == null) {
logger.log(Level.TRACE, "fat: disk is null");
            return -1.0;
        }
        if (assigned) {
logger.log(Level.TRACE, "fat: assigned");
            return 0.0;
        }

        fat.empty();
        assignParameter();
        return fat.assign(isFormatting);
    }

    /**
     * Check root directory structure of the currently selected disk
     *
     * @param isFormatting Whether formatting is in progress
     * @return <0.0: Error in directory
     */
    public double checkRootDirectory(boolean isFormatting) throws IOException {
        if (disk == null) return -1.0;
        if (assigned) return 1.0;
        return dir.checkRoot(type, diskBasicParam.getDirStartSector(), diskBasicParam.getDirEndSector(), isFormatting);
    }

    /**
     * Assign root directory of the currently selected disk
     *
     * @return false: Error in directory
     */
    public boolean assignRootDirectory() throws IOException {
        if (disk == null) return false;

        boolean valid = true;
        if (!assigned) {
            valid = dir.assignRoot(type, diskBasicParam.getDirStartSector(), diskBasicParam.getDirEndSector());
        } else {
            dir.setCurrentAsRoot();
        }
        if (valid) {
            type.calcDiskFreeSize(false);
        } else {
            type.clearDiskFreeSize();
        }
        assigned = valid;
        return valid;
    }

    /**
     * Assign FAT and root directory of the currently selected disk
     *
     * @return true, false: Error present
     */
    public boolean assignFatAndDirectory() throws IOException {
        boolean valid = (assignFat(false) >= 0.0);
        valid = valid && assignRootDirectory();
        return valid;
    }

    /** Clear parsed status */
    public void clearParseAndAssign(boolean forcely) {
        parsed = false;
        assigned = false;
        forcefully = forcely;
        dir.releaseRoot(type);
        dir.setCurrentAsRoot();
    }

    /** Whether it is parsed */
    public boolean isParsed() {
        return parsed;
    }

    /** Whether it is assigned */
    public boolean isAssigned() {
        return assigned;
    }

    /** Whether to ignore parsing errors */
    public boolean isForcely() {
        return forcefully;
    }

    /**
     * Whether it can be loaded
     *
     * @param item Directory item
     */
    public boolean isLoadableFile(DiskBasicDirItem<?> item) {
        if (item == null || !item.isLoadable() || !item.isUsed()) {
            errInfo.setError(DiskBasicError.ERRV_CANNOT_EXPORT, item.getFileNameStr());
            return false;
        }
        return true;
    }

    /**
     * Load file at the specified directory position
     *
     * @param itemNumber Directory position
     * @param dstPath    Output path
     */
    public boolean loadFile(int itemNumber, String dstPath) {
        DiskBasicDirItem<?> item = dir.item(itemNumber);
        return loadFile(item, dstPath);
    }

    /**
     * Load file of the specified directory item
     *
     * @param item    Directory item
     * @param dstPath Output path
     */
    public boolean loadFile(DiskBasicDirItem<?> item, String dstPath) {
        try (FileOutputStream file = new FileOutputStream(dstPath)) {
            return loadFile(item, file);
        } catch (IOException e) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_EXPORT);
            return false;
        }
    }

    /**
     * Load file to the specified stream
     *
     * @param item    Directory item
     * @param oStream [in,out] Output stream
     */
    public boolean loadFile(DiskBasicDirItem<?> item, OutputStream oStream) throws IOException {
        ByteArrayOutputStream otemp = new ByteArrayOutputStream();
        boolean sts = loadData(item, otemp, null);
        if (!sts) {
            return false;
        }
        if (otemp.size() == 0) {
            return true;
        }
        ByteArrayInputStream itemp = new ByteArrayInputStream(otemp.toByteArray());
        sts = type.convertDataForLoad(item, itemp, oStream);
        return sts;
    }

    /**
     * Verify file of the specified item
     *
     * @param item    Directory item
     * @param srcPath Path of the file to compare
     * @return 0: No difference, 1: Difference present, -1: Error
     */
    public int verifyFile(DiskBasicDirItem<?> item, String srcPath) {
        try (FileInputStream file = new FileInputStream(srcPath)) {
            ByteArrayOutputStream otemp = new ByteArrayOutputStream();
            if (!type.convertDataForVerify(item, file, otemp)) {
                return -1;
            }
            ByteArrayInputStream itemp = new ByteArrayInputStream(otemp.toByteArray());
            return verifyData(item, itemp);
        } catch (IOException e) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_VERIFY);
            return -1;
        }
    }

    /**
     * Load file to the specified stream
     *
     * @param item    [in,out] Directory item
     * @param ostream [in,out] Specified during export
     * @param outsize [out] Size actually output (valid only when ostream is specified)
     */
    public boolean loadData(DiskBasicDirItem<?> item, OutputStream ostream, int[] outsize) throws IOException {
        int sts = 0;
        for (int fileunitNum = 0; sts == 0; fileunitNum++) {
            if (!item.isValidFileUnit(fileunitNum)) {
                break;
            }
            sts = accessUnitData(fileunitNum, item, null, ostream, outsize);
        }
        return (sts == 0);
    }

    /**
     * Verify file of the specified item
     *
     * @param item    [in,out] Directory item
     * @param istream [in,out] Specified during verify
     * @return 0: No difference, 1: Difference present, -1: Error
     */
    public int verifyData(DiskBasicDirItem<?> item, InputStream istream) throws IOException {
        int sts = 0;
        int fileOffset = 0;

        istream.mark(Integer.MAX_VALUE);
        try {
            istream.reset();
        } catch (IOException ignore) { /* should not happen with ByteArrayInputStream */ }

        for (int fileunitNum = 0; sts == 0; fileunitNum++) {
            int sizeRemain = item.getFileUnitSize(fileunitNum, istream, fileOffset);
            if (sizeRemain < 0) {
                break;
            }
            sts = accessUnitData(fileunitNum, item, istream, null, null);
            fileOffset += sizeRemain;
        }
        return sts;
    }

    /**
     * Access disk data (used in load/verify)
     *
     * @param fileunitNum File number
     * @param item        [in,out] Directory item
     * @param istream     [in,out] Specified during verify
     * @param ostream     [in,out] Specified during export
     * @param outsize     [out] Size actually output (valid only when ostream is specified)
     * @return 0: No difference, 1: Difference present, -1: Error
     */
    public int accessUnitData(int fileunitNum, DiskBasicDirItem<?> item, InputStream istream, OutputStream ostream, int[] outsize) throws IOException {
        if (item == null) {
            errInfo.setError(DiskBasicError.ERR_FILE_NOT_FOUND);
            return -1;
        }

        int trackNum, sideNum, sectorStart, sectorEnd;
        int rc = 0;
        int osize = 0;

        if (ostream != null) {
            osize = (int) ((SeekableDataInputStream) istream).position();
        }

        DiskBasicGroups gitems = new DiskBasicGroups();
        item.getUnitGroups(fileunitNum, gitems);

        int[] remain = {item.getFileSize()};
        if (remain[0] == 0) {
            remain[0] = gitems.getSize();
        }

        if (!type.prepareToAccessFile(fileunitNum, item, istream, ostream, remain, gitems, errInfo)) {
            return -1;
        }

        int gidxEnd = gitems.size() - 1;
        for (int gidx = 0; gidx <= gidxEnd && remain[0] > 0 && rc == 0; gidx++) {
            DiskBasicGroupItem gitem = gitems.get(gidx);
            trackNum = gitem.track;
            sideNum = gitem.side;
            sectorStart = gitem.sectorStart;
            sectorEnd = gitem.sectorEnd;
            DiskImageTrack track = disk.getTrack(trackNum, sideNum);
            if (track == null) {
                errInfo.setError(DiskBasicError.ERRV_NO_TRACK, String.valueOf(gitem.group), java.lang.String.valueOf(trackNum), java.lang.String.valueOf(sideNum));
                rc = -1;
                break;
            }

            for (int sectorNum = sectorStart; sectorNum <= sectorEnd && remain[0] > 0; sectorNum++) {
                DiskImageSector sector = track.getSector(sectorNum);
                if (sector == null) {
                    errInfo.setError(DiskBasicError.ERRV_NO_SECTOR, String.valueOf(gitem.group), java.lang.String.valueOf(trackNum), java.lang.String.valueOf(sideNum), java.lang.String.valueOf(sectorNum));
                    rc = -1;
                    continue;
                }
                int bufsize = sector.getSectorSize();
                bufsize /= gitem.numOfDivs;
                byte[] buf = sector.getSectorBuffer();
                int offset = bufsize * gitem.divNum;
                byte[] segment = Arrays.copyOfRange(buf, offset, offset + bufsize);

                bufsize = type.accessFile(fileunitNum, item, istream, ostream, segment, bufsize, remain[0], sectorNum, sectorEnd);
                if (bufsize < 0) {
                    if (bufsize == -2) {
                        errInfo.setError(DiskBasicError.ERRV_INVALID_SECTOR, String.valueOf(gitem.group), java.lang.String.valueOf(trackNum), java.lang.String.valueOf(sideNum), java.lang.String.valueOf(sectorNum), java.lang.String.valueOf(bufsize));
                        rc = -1;
                    } else {
                        errInfo.setError(DiskBasicError.ERRV_VERIFY_FILE, String.valueOf(gitem.group), java.lang.String.valueOf(trackNum), java.lang.String.valueOf(sideNum), java.lang.String.valueOf(sectorNum));
                        rc = 1;
                    }
                    break;
                }
                remain[0] -= bufsize;
            }
        }

        if (ostream != null) osize += (int) ((SeekableDataOutputStream) ostream).position() - osize;

        if (outsize != null) outsize[0] += osize;

        return rc;
    }

    /**
     * Whether the same file name already exists and can be overwritten
     *
     * @param dirItem     Directory to search
     * @param filename    File name
     * @param excludeItem Item to exclude from search targets
     * @param nextItem    [out] Item at the next position of the matching item
     * @return 0: None, 1: Present (normal file), -1: Present (cannot overwrite, directory or volume label)
     */
    public int isFileNameDuplicated(DiskBasicDirItem<?> dirItem, DiskBasicFileName filename, DiskBasicDirItem<?> excludeItem, DiskBasicDirItem[] nextItem) {
        DiskBasicDirItem<?> item = dir.findFile(dirItem, filename, isCompareCaseInsensitive(), excludeItem, nextItem);
        if (item == null) {
            return 0;
        }
        return (item.isOverWritable() ? 1 : -1);
    }

    /**
     * Whether the same file name already exists and can be overwritten
     *
     * @param dirItem     Directory to search
     * @param targetItem  Item
     * @param excludeItem Item to exclude from search targets
     * @param nextItem    [out] Item at the next position of the matching item
     * @return 0: None, 1: Present (normal file), -1: Present (cannot overwrite, directory or volume label)
     */
    public int isFileNameDuplicated(DiskBasicDirItem<?> dirItem, DiskBasicDirItem<?> targetItem, DiskBasicDirItem<?> excludeItem, DiskBasicDirItem[] nextItem) {
        DiskBasicDirItem<?> item = dir.findFile(dirItem, targetItem, isCompareCaseInsensitive(), excludeItem, nextItem);
        if (item == null) {
            return 0;
        }
        return (item.isOverWritable() ? 1 : -1);
    }

    /** Whether writing is possible */
    public boolean isWritableIntoDisk() {
        errInfo.clear();
        if (disk == null) {
            errInfo.setError(DiskBasicError.ERR_UNSELECT_DISK);
            return false;
        }
        if (!type.supportWriting()) {
            errInfo.setError(DiskBasicError.ERR_WRITE_UNSUPPORTED);
            return false;
        }
        if (disk.isWriteProtected()) {
            errInfo.setError(DiskBasicError.ERR_WRITE_PROTECTED);
            return false;
        }
        return true;
    }

    /**
     * Check if the specified file size can be written to the disk
     *
     * @param srcPath  File path
     * @param fileSize [out] Returns file size
     */
    public boolean checkFile(String srcPath, int[] fileSize) {
        if (!isWritableIntoDisk()) {
            return false;
        }
        if (!Files.exists(Paths.get(srcPath)) || Files.isDirectory(Paths.get(srcPath))) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_IMPORT_DIRECTORY);
            return false;
        }

        try (FileInputStream infile = new FileInputStream(srcPath)) {
            int size = (int) infile.getChannel().size();
            if (fileSize != null) fileSize[0] = size;

//#ifndef DEBUG_DISK_FULL_TEST
            if (!hasFreeDiskSize(size)) {
                return false;
            }
//#endif

            return true;
        } catch (IOException e) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_IMPORT);
            return false;
        }
    }

    /**
     * Save specified file to disk image
     *
     * @param srcPath Path where original file exists
     * @param dirItem [in,out] Destination directory item
     * @param pitem   [in,out] Directory item with file name and attributes for saving
     * @param nitem   [out] Allocated directory item
     * @return false: Error present
     */
    public <T extends Directory> boolean saveFile(String srcPath, DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nitem) {
        if (!isWritableIntoDisk()) return false;

        try (FileInputStream infile = new FileInputStream(srcPath)) {
            return saveFile(infile, dirItem, pitem, nitem);
        } catch (IOException e) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_IMPORT);
            return false;
        }
    }

    /**
     * Save buffer data to disk image
     *
     * @param buffer  Data
     * @param dirItem [in,out] Destination directory item
     * @param pItem   [in,out] Directory item with file name and attributes for saving
     * @param nitem   [out] Allocated directory item
     * @return false: Error present
     */
    public <T extends Directory> boolean saveFile(byte[] buffer, DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T>[] nitem) throws IOException {
        if (!isWritableIntoDisk()) return false;

        ByteArrayInputStream inData = new ByteArrayInputStream(buffer);
        return saveFile(inData, dirItem, pItem, nitem);
    }

    /**
     * Save stream data to disk image
     *
     * @param iStream Stream buffer
     * @param dirItem [in,out] Destination directory item
     * @param pItem   [in,out] Temporary directory item with file name and attributes for saving
     * @param nItem   [out] Allocated directory item
     * @return false: Error present
     */
    public <T extends Directory> boolean saveFile(InputStream iStream, DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T>[] nItem) throws IOException {
        DiskBasicDirItem[] nextItemArr = {null};
        DiskBasicDirItem<T> item = dir.findFile(dirItem, pItem, isCompareCaseInsensitive(), null, nextItemArr);

        boolean valid = true;

        if (item == null) {
            while ((item = dir.getEmptyItem(dirItem, pItem, nextItemArr)) == null) {
                if (dir.canExpand(dirItem)) {
                    valid = dir.expand(dirItem);
                } else {
                    valid = false;
                }
                if (!valid) {
                    errInfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
                    return false;
                }
            }
            item.setEndMark(nextItemArr[0]);
        } else {
            if (!this.deleteFile(item, false)) {
                return false;
            }
        }

        if (nItem != null) nItem[0] = item;

        item.clearData();
        item.copyItem(pItem);

        ByteArrayOutputStream otemp = new ByteArrayOutputStream();
        if (!type.convertDataForSave(item, iStream, otemp)) {
            this.deleteFile(item, false);
            return false;
        }

        int[] fileSize = {0};
        DiskBasicGroups groupItems = new DiskBasicGroups();
        InputStream itemp = null;

        if (otemp.size() > 0) {
            itemp = new ByteArrayInputStream(otemp.toByteArray());
        } else {
            itemp = iStream;
        }

        try {
            valid = saveData(itemp, pItem, item, groupItems, fileSize);
            item.setFileSize(fileSize[0]);
            if (!valid) {
                this.deleteFile(item, groupItems);
            } else {
                item.refresh();
                item.setModify();
                item.calcFileSize();

                int sts = verifyData(item, itemp);
                valid = (sts == 0);

                type.additionalProcessOnSavedFile(item);
                type.calcDiskFreeSize(true);
            }
        } finally {
            if (otemp.size() > 0 && itemp != null) {
                try {
                    itemp.close();
                } catch (IOException e) {
                }
            }
        }

        return valid;
    }

    /**
     * Save stream data to disk image
     *
     * @param iStream    Stream buffer
     * @param pItem      [in,out] Temporary directory item with file name and attributes for saving
     * @param item       [in,out] Allocated directory item
     * @param groupItems [out] Group list
     * @param fileSize   [out] Size of saved file
     */
    public <T extends Directory> boolean saveData(InputStream iStream, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T> item, DiskBasicGroups groupItems, int[] fileSize) throws IOException {
        boolean valid = true;
        int fileOffset = 0;

        for (int fileunitNum = 0; valid; fileunitNum++) {
            int[] iSize = {item.getFileUnitSize(fileunitNum, iStream, fileOffset)};
            if (iSize[0] < 0) {
                break;
            }
            valid = saveUnitData(fileunitNum, iStream, iSize, pItem, item, groupItems, fileSize);
            fileOffset += iSize[0];
        }
        return valid;
    }

    /**
     * Save stream data to disk image
     *
     * @param fileUnitNum File number
     * @param iStream     Stream buffer
     * @param iSize       Data size to be saved in buffer
     * @param pItem       [in,out] Temporary directory item with file name and attributes for saving
     * @param item        [in,out] Allocated directory item
     * @param groupItems  [out] Group list
     * @param fileSize    [out] Size of saved file
     */
    public <T extends Directory> boolean saveUnitData(int fileUnitNum, InputStream iStream, int[] iSize, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T> item, DiskBasicGroups groupItems, int[] fileSize) throws IOException {
        if (!type.prepareToSaveFile(iStream, iSize, pItem, item, errInfo)) {
            return false;
        }

        iSize[0] = item.recalcFileSizeOnSave(iStream, iSize[0]);

//#ifndef DEBUG_DISK_FULL_TEST
        if (!hasFreeDiskSize(iSize[0])) {
            return false;
        }
//#endif

        DiskBasicGroups[] gItems = {new DiskBasicGroups()};
        int rc = type.allocateUnitGroups(fileUnitNum, item, iSize[0], ALLOCATE_GROUPS_NEW, gItems);
        groupItems.add(gItems[0]);
        if (rc < 0) {
            errInfo.setError(DiskBasicError.ERR_DISK_FULL);
            return false;
        }

        int seqNum = 0;
        for (DiskBasicGroupItem gItem : gItems[0].getItems()) {
            for (int sectorNum = gItem.sectorStart; sectorNum <= gItem.sectorEnd; sectorNum++) {
                DiskImageSector sector = disk.getSector(gItem.track, gItem.side, sectorNum);
                if (sector == null) {
                    errInfo.setError(DiskBasicError.ERRV_NO_SECTOR, String.valueOf(gItem.group), java.lang.String.valueOf(gItem.track), java.lang.String.valueOf(gItem.side), java.lang.String.valueOf(sectorNum));
                    rc = -2;
                    continue;
                }
                int bufSize = sector.getSectorSize();
                bufSize /= gItem.numOfDivs;
                byte[] buf = sector.getSectorBuffer();
                int offset = bufSize * gItem.divNum;
                byte[] segment = Arrays.copyOfRange(buf, offset, offset + buf.length);


                int lastSize = type.writeFile(item, iStream, segment, bufSize, iSize[0], sectorNum, gItem.group, gItem.next, gItem.sectorEnd, seqNum);

                // Here we would copy the modified 'segment' back into the original sector buffer.
                System.arraycopy(segment, 0, buf, offset, segment.length);

                iSize[0] -= lastSize;
                fileSize[0] += lastSize;
                seqNum++;
            }
        }
        return rc >= 0;
    }

    /** Whether files can be deleted */
    public boolean isDeletableFiles() {
        errInfo.clear();
        if (type == null || !type.supportDeleting()) {
            errInfo.setError(DiskBasicError.ERR_DELETE_UNSUPPORTED);
            return false;
        }
        if (disk.isWriteProtected()) {
            errInfo.setError(DiskBasicError.ERR_WRITE_PROTECTED);
            return false;
        }
        return true;
    }

    /**
     * Whether the specified file can be deleted
     *
     * @param item     Directory item
     * @param clearMsg Whether to clear the error message buffer
     * @return -1: Error, cannot continue, 1: Error, can continue
     */
    public int isDeletableFile(DiskBasicDirItem<?> item, boolean clearMsg) {
        if (clearMsg) errInfo.clear();

        if (disk.isWriteProtected()) {
            errInfo.setError(DiskBasicError.ERR_WRITE_PROTECTED);
            return -1;
        }

        if (item == null) return 0;

        if (!item.isDeletable()) {
            errInfo.setError(DiskBasicError.ERRV_CANNOT_DELETE, item.getFileNameStr());
            return 1;
        }
        return 0;
    }

    /**
     * Whether the specified directory is empty
     *
     * @param item       Directory item
     * @param groupItems [out] Group number list
     * @param clearMsg   Whether to clear the error message buffer
     */
    public boolean isEmptyDirectory(DiskBasicDirItem<?> item, DiskBasicGroups groupItems, boolean clearMsg) throws IOException {
        if (clearMsg) errInfo.clear();

        item.getAllGroups(groupItems);

        if (item.isDirectory()) {
            if (!type.isEmptyDirectory(false, groupItems)) {
                errInfo.setError(DiskBasicError.ERRV_CANNOT_DELETE_DIRECTORY, item.getFileNameStr());
                return false;
            }
        }
        return true;
    }

    /**
     * Delete file
     *
     * @param item     Directory item
     * @param clearMsg Whether to clear the error message buffer
     * @return true: Success, false: Failure
     */
    public boolean deleteFile(DiskBasicDirItem<?> item, boolean clearMsg) throws IOException {
        if (item == null) return false;
        if (clearMsg) errInfo.clear();
        DiskBasicGroups groupItems = new DiskBasicGroups();
        item.getAllGroups(groupItems);
        return deleteFile(item, groupItems);
    }

    /**
     * Delete file
     *
     * @param item       Directory item
     * @param groupItems Group number list
     * @return true: Success, false: Failure
     */
    public boolean deleteFile(DiskBasicDirItem<?> item, DiskBasicGroups groupItems) throws IOException {
        if (disk == null) return false;

        if (item == null) {
            errInfo.setError(DiskBasicError.ERR_FILE_NOT_FOUND);
            return false;
        }

        type.deleteGroups(groupItems);
        item.delete();

        if (!type.additionalProcessOnDeletedFile(item)) {
            errInfo.setError(DiskBasicError.ERRV_CANNOT_DELETE, item.getFileNameStr());
            return false;
        }

        item.emptyChildren();
        item.refresh();
        item.setModify();

        type.calcDiskFreeSize(true);
        type.releaseDirectoryItem(item);

        return true;
    }

    /**
     * Whether file name or attributes can be updated
     *
     * @param item    Directory item
     * @param showMsg Whether to set error message
     * @return true: Can, false: Cannot
     */
    public boolean canRenameFile(DiskBasicDirItem<?> item, boolean showMsg) {
        errInfo.clear();
        if (item == null) {
            if (showMsg) errInfo.setError(DiskBasicError.ERR_FILE_NOT_FOUND);
            return false;
        }
        if (disk.isWriteProtected()) {
            if (showMsg) errInfo.setError(DiskBasicError.ERR_WRITE_PROTECTED);
            return false;
        }
        if (!item.isFileNameEditable()) {
            if (showMsg) {
                String filename = item.getFileNameStr();
                errInfo.setError(DiskBasicError.ERRV_CANNOT_EDIT_NAME, filename);
            }
            return false;
        }
        return true;
    }

    /**
     * Update file name
     *
     * @param item    Directory item
     * @param newName File name
     * @return true
     */
    public boolean renameFile(DiskBasicDirItem<?> item, String newName) throws IOException {
        if (item.isFileNameEditable()) {
            item.setFileNameStr(newName);
        }
        item.refresh();
        item.setModify();
        type.additionalProcessOnRenamedFile(item);
        return true;
    }

    /**
     * Update attributes
     *
     * @param item Directory item
     * @param attr Attribute value
     */
    public boolean changeAttr(DiskBasicDirItem<?> item, DiskBasicDirItemAttr attr) throws IOException {
        if (attr.isRenameable()) {
            item.setOptionalName(attr.getFileName().getOptional());
            boolean status = renameFile(item, attr.getFileName().getName());
            if (!status) return status;
        }
        if (!attr.doesIgnoreFileAttr()) {
            item.setFileAttr(attr.getFileAttr());
        }
        if (attr.getStartAddress() >= 0) {
            item.setStartAddress(attr.getStartAddress());
        }
        if (attr.getEndAddress() >= 0) {
            item.setEndAddress(attr.getEndAddress());
        }
        if (attr.getExecuteAddress() >= 0) {
            item.setExecuteAddress(attr.getExecuteAddress());
        }

        boolean ignoreDatetime = attr.doesIgnoreDateTime();
        int ignoreType = item.canIgnoreDateTime();
        if (!(ignoreDatetime && (ignoreType == DATETIME_CREATE))) {
            item.setFileCreateDateTime(attr.getCreateDateTime());
        }
        if (!(ignoreDatetime && (ignoreType == DATETIME_MODIFY))) {
            item.setFileModifyDateTime(attr.getModifyDateTime());
        }
        if (!(ignoreDatetime && (ignoreType == DATETIME_ACCESS))) {
            item.setFileAccessDateTime(attr.getAccessDateTime());
        }

        item.setOptionalAttr(attr);
        item.refresh();
        item.setModify();
        type.additionalProcessOnChangedAttr(item);
        return true;
    }

    /** Whether formatted for DISK BASIC */
    public boolean isFormatted() {
        return (disk != null && formatted);
    }

    /** Whether it can be formatted for DISK BASIC */
    public boolean isFormattable() {
        errInfo.clear();
        boolean enable = (type != null);
        if (!enable) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_FORMAT);
            return enable;
        }
        enable = type.supportFormatting();
        if (!enable) {
            errInfo.setError(DiskBasicError.ERR_FORMAT_UNSUPPORTED);
        }
        return enable;
    }

    /**
     * Logically format disk
     *
     * @param data Model dependent data (volume name, etc.)
     * @return >0: Warning, 0: Normal, <0: Error present
     */
    public int formatDisk(DiskBasicIdentifiedData data) throws IOException {
        errInfo.clear();
        if (disk == null) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_FORMAT);
            return errInfo.getValid();
        }

        List<DiskImageTrack> tracks = disk.getTracks();
        if (tracks == null) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_FORMAT);
            return errInfo.getValid();
        }

        parsed = true;
        assigned = false;
        forcefully = false;

        boolean rc = true;
        for (DiskImageTrack track : tracks) {
            if (selectedSide >= 0) {
                if (selectedSide != track.getSideNumber()) {
                    continue;
                }
            }

            List<DiskImageSector> sectors = track.getSectors();
            if (sectors == null) {
                if (track.getTrackNumber() >= 0 && track.getSideNumber() >= 0) {
                    errInfo.setWarn(DiskBasicError.ERRV_NOTHING_IN_TRACK, track.getTrackNumber(), track.getSideNumber());
                }
                continue;
            }
            if (!isVariableSectorsPerTrack() && sectors.size() < getSectorsPerTrackOnBasic()) {
                errInfo.setWarn(DiskBasicError.ERRV_NUM_OF_SECTORS_IN_TRACK, track.getTrackNumber(), track.getSideNumber());
            }
            for (DiskImageSector sector : sectors) {
                type.fillSector(track, sector);
            }
        }

        if (!type.additionalProcessOnFormatted(data)) {
            errInfo.setError(DiskBasicError.ERR_FORMATTING);
            rc = false;
        }

        if (rc) {
            assignFatAndDirectory();
            formatted = true;
        }
        return errInfo.getValid();
    }

    /** Returns root directory */
    public <T extends Directory> DiskBasicDirItem<T> getRootDirectory() {
        return dir.getRootItem();
    }

    /**
     * Returns a list in the root directory
     *
     * @param dirItem nullable
     */
    public <T extends Directory> List<DiskBasicDirItem<T>> getRootDirectoryItems(DiskBasicDirItem<T>[] dirItem) {
        return dir.getRootItems(dirItem);
    }

    /** Returns current directory */
    public <T extends Directory> DiskBasicDirItem<T> getCurrentDirectory() {
        return dir.getCurrentItem();
    }

    /** Returns a list in the current directory */
    public <T extends Directory> List<DiskBasicDirItem<T>> getCurrentDirectoryItems(DiskBasicDirItem<T>[] dirItem) {
        return dir.getCurrentItems(dirItem);
    }

    /**
     * Assign directory
     *
     * @param dirItem Directory item
     */
    public <T extends Directory> boolean assignDirectory(DiskBasicDirItem<T> dirItem) throws IOException {
        if (disk == null) return false;
        return dir.assign(dirItem);
    }

    /**
     * Re-read directory
     *
     * @param dirItem Directory item
     */
    public <T extends Directory> boolean reassignDirectory(DiskBasicDirItem<T> dirItem) throws IOException {
        if (disk == null) return false;
        return dir.reassign(dirItem);
    }

    /**
     * Change directory
     *
     * @param dstItem [in,out] Destination directory item
     */
    public <T extends Directory> boolean changeDirectory(DiskBasicDirItem<T>[] dstItem) throws IOException {
        if (disk == null) return false;
        boolean valid = dir.change(dstItem);
        if (valid) {
            // Calculate remaining size
            type.calcDiskFreeSize(false);
        }
        return valid;
    }

    /** Whether a subdirectory can be created */
    public boolean canMakeDirectory() {
        return (type.canMakeDirectory()) && (getSubDirGroupSize() > 0);
    }

    /**
     * Create a subdirectory
     *
     * @param dirItem        [in,out] Destination directory (parent directory)
     * @param filename       Directory name
     * @param ignoreDatetime Whether not to set date and time
     * @param nitem          [out] Created directory item
     * @return 1: Same name exists, -1: Other error
     */
    public <T extends Directory> int makeDirectory(DiskBasicDirItem<T> dirItem, String filename, boolean ignoreDatetime, DiskBasicDirItem<T>[] nitem) throws IOException {
        if (!isWritableIntoDisk()) {
            return -1;
        }

        if (!canMakeDirectory()) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_MAKE_DIRECTORY);
            return -1;
        }

        DiskBasicFileName dirName = new DiskBasicFileName();
        String[] filenameRef = {filename};
        // Edit directory name before creating subdirectory
        if (!type.renameOnMakingDirectory(filenameRef)) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_MAKE_DIRECTORY);
            return -1;
        }
        dirName.setName(filenameRef[0]);

        // Whether the same file name exists
        DiskBasicDirItem[] nextItem = {null};
        DiskBasicDirItem<T> item = dir.findFile(dirItem, dirName, isCompareCaseInsensitive(), null, nextItem);
        if (item != null) {
            errInfo.setError(DiskBasicError.ERR_FILE_ALREADY_EXIST);
            return 1;
        }

        // Create temporary item
        DiskBasicDirItem<T> pitem = dir.newItem();
        // Clear entry
        pitem.clearData();
        // Set file name & attributes
        pitem.setFileNameStr(dirName.getName());
        pitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);

        // Date and time setting
        LocalDateTime tm = LocalDateTime.now();
        int ignoreType = pitem.canIgnoreDateTime();
        if (!(ignoreDatetime && (ignoreType == DATETIME_CREATE))) {
            pitem.setFileCreateDateTime(tm);
        }
        if (!(ignoreDatetime && (ignoreType == DATETIME_MODIFY))) {
            pitem.setFileModifyDateTime(tm);
        }
        if (!(ignoreDatetime && (ignoreType == DATETIME_ACCESS))) {
            pitem.setFileAccessDateTime(tm);
        }

        // Allocate a new directory item
        while ((item = dir.getEmptyItem(dirItem, pitem, nextItem)) == null) {
            // When it cannot be allocated
            boolean valid = false;
            // Expand directory area
            if (dir.canExpand(dirItem)) {
                valid = dir.expand(dirItem);
            }
            if (!valid) {
                errInfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
                return -1;
            }
        }
        item.setEndMark(nextItem[0]);

        if (nitem != null) nitem[0] = item;

        // Clear file name attributes
        item.clearData();
        // Set file name attributes
        item.copyItem(pitem);

//        item.setFileNameStr(dir_name.getName());
//        item.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK, 0);
//        item.setFileCreateDateTime(tm.now());

        // Prepare before creating directory
        if (!type.prepareToMakeDirectory(item)) {
            this.deleteFile(item, false);
            return -1;
        }

        int[] sizeRemain = {getSectorsPerGroup() * getSectorSize() * getSubDirGroupSize()};
        // Whether there is free space
        if (sizeRemain[0] > getFreeDiskSize()) {
            // Not enough free space
            errInfo.setError(DiskBasicError.ERR_DISK_FULL);
            // Put delete mark on item
            this.deleteFile(item, false);
            return -1;
        }

        int[] fileSize = {0};

        int rc;

        // Allocate required disk area
        DiskBasicGroups[] groupItems = {new DiskBasicGroups()};
        rc = type.allocateGroups(item, sizeRemain[0], ALLOCATE_GROUPS_NEW, groupItems);
        if (rc < 0) {
            // Not enough free space
            errInfo.setError(DiskBasicError.ERR_DISK_FULL);
            // Delete allocated area
            this.deleteFile(item, false);
            return -1;
        }

        // Write to sector
        rc = type.initializeSectorsAsDirectory(groupItems[0], fileSize, sizeRemain, errInfo);

        // Directory size
        item.setDirectorySize(fileSize[0]);

        if (rc < 0) {
            // Delete in case of error
            this.deleteFile(item, false);
            return -1;
        }

        // Modified
        item.refresh();
        item.setModify();

        // Individual processing after directory creation
        type.additionalProcessOnMadeDirectory(item, groupItems[0], dirItem);

        // Calculate group count
        item.calcFileSize();
        // Calculate free size
        type.calcDiskFreeSize(true);

        return 0;
    }

    /**
     * Expand directory size
     *
     * @param dirItem Directory entry
     */
    public boolean expandDirectory(DiskBasicDirItem<?> dirItem) throws IOException {
        int[] sizeRemain = {getSubDirGroupSize() * getSectorsPerGroup() * getSectorSize()};
        DiskBasicGroups[] groupItems = {new DiskBasicGroups()};
        int rc = type.allocateGroups(dirItem, sizeRemain[0], ALLOCATE_GROUPS_APPEND, groupItems);
        if (rc < 0) {
            errInfo.setError(DiskBasicError.ERR_DISK_FULL);
            return false;
        }

        int[] fileSize = {dirItem.getFileSize()};
        rc = type.initializeSectorsAsDirectory(groupItems[0], fileSize, sizeRemain, errInfo);
        if (rc < 0) {
            return false;
        }

        dirItem.setDirectorySize(fileSize[0]);
        DiskBasicDirItem<?> curItem = dir.findName(dirItem, ".", isCompareCaseInsensitive(), null, null);
        if (curItem != null) {
            curItem.setDirectorySize(fileSize[0]);
        }

        if (!type.additionalProcessOnExpandedDirectory(dirItem, groupItems[0], dir.getParentItem(dirItem))) {
            errInfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
            return false;
        }

        return true;
    }

    /** Create directory item */
    public DiskBasicDirItem<?> createDirItem() throws IOException {
        return dir.newItem();
    }

    /**
     * Create directory item
     *
     * @param sector  Sector data
     * @param secPos  Position within sector
     * @param data    Directory data
     * @param dataP   Pointer to directory data
     * @return Directory item
     */
    public <T extends Directory> DiskBasicDirItem<T> createDirItem(DiskImageSector sector, int secPos, byte[] data, int dataP) throws IOException {
        return dir.newItem(sector, secPos, data, dataP);
    }

    /** Returns starting sector from directory item position */
    public DiskImageSector getSectorFromPosition(int position, int[] startGroup) {
        DiskBasicDirItem<?> item = dir.item(position);
        if (item == null) return null;

        int gnum = item.getStartGroup(0);
        if (startGroup != null) startGroup[0] = gnum;
        return getSectorFromGroup(gnum, null, null);
    }

    public DiskImageSector getSectorFromGroup(int groupNum) {
        return getSectorFromGroup(groupNum, null, null);
    }

    /**
     * Returns starting sector from group number
     *
     * @param groupNum Group number
     * @param divNum   [out] Division number
     * @param divNums  [out] Number of divisions
     */
    public DiskImageSector getSectorFromGroup(int groupNum, int[] divNum, int[] divNums) {
        int[] trackNum = {0};
        int[] sideNum = {0};
        return getSectorFromGroup(groupNum, trackNum, sideNum, divNum, divNums);
    }

    /**
     * Returns starting sector from group number
     *
     * @param groupNum Group number
     * @param trackNum [out] Track number
     * @param sideNum  [out] Side number
     * @param divNum   [out] Division number
     * @param divNums  [out] Number of divisions
     * @return Sector
     * Consideration of model dependencies such as skipping management area, subtracting start group number offset, etc.
     */
    public DiskImageSector getSectorFromGroup(int groupNum, int[] trackNum, int[] sideNum, int[] divNum, int[] divNums) {
        int[] sectorStart = {1};
        if (!calcStartNumFromGroupNum(groupNum, trackNum, sideNum, sectorStart, divNum, divNums)) {
            return null;
        }
        return disk.getSector(trackNum[0], sideNum[0], sectorStart[0]);
    }

    public boolean getNumsFromGroup(int groupNum, int nextGroup, int sectorSize, int remainSize, DiskBasicGroups items) {
        return getNumsFromGroup(groupNum, nextGroup, sectorSize, remainSize, items, null);
    }

    /**
     * Calculate track number, side number, and sector number from group number and add to list
     * <p>
     * Consideration of model dependencies such as skipping management area, subtracting start group number offset, etc.
     *
     * @param groupNum   Group number
     * @param nextGroup  Next group number
     * @param sectorSize Sector size
     * @param remainSize Remaining data size
     * @param items      [out] List containing track, side, and sector numbers
     * @param endSector  [out] Final sector number of this group
     * @return false: Group number is out of range
     */
    public boolean getNumsFromGroup(int groupNum, int nextGroup, int sectorSize, int remainSize, DiskBasicGroups items, int[] endSector) {
        int sectorStart = type.getStartSectorFromGroup(groupNum);
        if (sectorStart < 0) {
            return false;
        }

        int sectorEnd = type.getEndSectorFromGroup(groupNum, nextGroup, sectorStart, sectorSize, remainSize);

        int[] track = {0}, side = {0}, sector = {0}, divNum = {0}, divNums = {0};
        boolean first = true;
        DiskBasicGroupItem itm = new DiskBasicGroupItem(groupNum, nextGroup, -1, -1, sectorStart, sectorStart);
        for (int seq = sectorStart; seq <= sectorEnd; seq++) {
            calcNumFromSectorPosForGroup(seq, track, side, sector, divNum, divNums);
            if (itm.track != track[0] || itm.side != side[0] || itm.sectorEnd + 1 != sector[0]) {
                if (!first) {
                    items.add(new DiskBasicGroupItem(itm.group, itm.next, itm.track, itm.side, itm.sectorStart, itm.sectorEnd));
                }
                itm.sectorStart = sector[0];
                first = false;
            }
            itm.track = track[0];
            itm.side = side[0];
            itm.sectorEnd = sector[0];
            itm.divNum = divNum[0];
            itm.numOfDivs = divNums[0];
        }
        items.add(itm);

        if (endSector != null) endSector[0] = sectorEnd;

        return true;
    }

    /**
     * Calculate track, side, and sector numbers from group number (for group calculation)
     * <p>
     * Consideration of model dependencies such as skipping management area, subtracting start group number offset, etc.
     *
     * @param groupNum    Group number
     * @param trackStart  [out] Track number
     * @param sideStart   [out] Side number
     * @param sectorStart [out] Sector number
     * @param divNum      [out] Division number
     * @param divNums     [out] Number of divisions
     * @return false: Group number is out of range
     */
    public boolean calcStartNumFromGroupNum(int groupNum, int[] trackStart, int[] sideStart, int[] sectorStart, int[] divNum, int[] divNums) {
        if (groupNum > getFatEndGroup()) {
            return false;
        }
        int seq = type.getStartSectorFromGroup(groupNum);
        if (seq < 0) {
            return false;
        }
        calcNumFromSectorPosForGroup(seq, trackStart, sideStart, sectorStart, divNum, divNums);
        return true;
    }

    /**
     * Calculate track, side, and sector numbers from sector position (serial number where track 0, side 0, sector 1 is 0) (for group calculation)
     * <p>
     * Consideration of model dependencies such as skipping management area, subtracting start group number offset, etc.
     *
     * @param sectorPos Sector position (position starting from track 0, side 0 sector as 0)
     * @param trackNum  [out] Track number
     * @param sideNum   [out] Side number
     * @param sectorNum [out] Sector number
     * @param divNum    [out] Division number
     * @param divNums   [out] Number of divisions
     */
    public void calcNumFromSectorPosForGroup(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum /* = null */, int[] divNums /* = null */) {
        sectorPos += dataStartSector;
        type.getNumFromSectorPos(sectorPos, trackNum, sideNum, sectorNum, divNum, divNums);
        if (trackNum[0] >= skippedTrack) trackNum[0]++;
    }

    /**
     * Calculate track and sector numbers from sector position (serial number where track 0, sector 1 is 0) (for group calculation)
     * Side number is converted to track number, track number is a multiple of number of sides
     * <p>
     * Consideration of model dependencies such as skipping management area, subtracting start group number offset, etc.
     *
     * @param sectorPos Sector position (position starting from track 0, sector 1 as 0)
     * @param trackNum  [out] Track number
     * @param sectorNum [out] Sector number (becomes +number of tracks for side 1)
     */
    public void calcNumFromSectorPosTForGroup(int sectorPos, int[] trackNum, int[] sectorNum) {
        sectorPos += dataStartSector;
        type.getNumFromSectorPosT(sectorPos, trackNum, sectorNum);
        if (trackNum[0] >= skippedTrack) trackNum[0]++;
    }

    /**
     * Calculate sector position (serial number where track 0, side 0, sector 1 is 0) from track, side, and sector numbers (for group calculation)
     * <p>
     * Consideration of model dependencies such as skipping management area, subtracting start group number offset, etc.
     *
     * @param trackNum  Track number
     * @param sideNum   Side number
     * @param sectorNum Sector number
     * @param divNum    Division number
     * @param divNums   Number of divisions
     * @return Sector position (position starting from track 0, side 0 sector as 0)
     */
    public int calcSectorPosFromNumForGroup(int trackNum, int sideNum, int sectorNum, int divNum /* = 0 */, int divNums /* = 1 */) {
        sideNum = getReversedSideNumber(sideNum);
        if (trackNum >= skippedTrack) trackNum--;

        int sectorPos = type.getSectorPosFromNum(trackNum, sideNum, sectorNum, divNum, divNums);
        sectorPos -= dataStartSector;
        return sectorPos;
    }

    /**
     * Calculate sector position (serial number where track 0, sector 1 is 0) from track and sector numbers (for group calculation)
     * Side number is converted to track number, track number is a multiple of number of sides
     *
     * Consideration of model dependencies such as skipping management area, subtracting start group number offset, etc.
     *
     * @param trackNum  Track number
     * @param sectorNum Sector number (+number of tracks for side 1)
     * @return Sector position (position starting from track 0 sector 1 as 0)
     */
    public int calcSectorPosFromNumTForGroup(int trackNum, int sectorNum) {
        if (trackNum >= skippedTrack) trackNum--;
        int sectorPos = type.getSectorPosFromNumT(trackNum, sectorNum);
        sectorPos -= dataStartSector;
        return sectorPos;
    }

    /**
     * Returns track
     *
     * @param trackNum Track number
     * @param sideNum  Side number
     * @return Track data
     */
    public DiskImageTrack getTrack(int trackNum, int sideNum) {
        return disk.getTrack(trackNum, sideNum);
    }

    /**
     * Returns sector
     *
     * @param trackNum  Track number
     * @param sideNum   Side number
     * @param sectorNum Sector number
     * @return Sector data
     */
    public DiskImageSector getSector(int trackNum, int sideNum, int sectorNum) {
        return disk.getSector(trackNum, sideNum, sectorNum);
    }

    /**
     * Returns sector
     *
     * @param trackNum  Track number
     * @param sectorNum Sector number (serial number for side 0 to 1)
     * @param sideNum   [out] Side number
     * @return Sector data
     */
    public DiskImageSector getSector(int trackNum, int sectorNum, int[] sideNum /* = null */) {
        int sidNum = (sectorNum - 1) / getSectorsPerTrackOnBasic();
        sectorNum = ((sectorNum - 1) % getSectorsPerTrackOnBasic()) + 1;
        if (numberingSector == 1) sectorNum += (getSectorsPerTrackOnBasic() * sidNum);
        sidNum += getSideNumberBaseOnDisk();
        if (sideNum != null) sideNum[0] = sidNum;
        return disk.getSector(trackNum, sidNum, sectorNum);
    }

    /**
     * Returns track from sector position (serial number where track 0, side 0, sector 1 is 0)
     * <p>
     * The sector position is a serial number where track 0, side 0, sector 1 is 0, regardless of the model
     *
     * @param sectorPos Sector position (serial number where track 0, side 0, sector 1 is 0)
     * @param sectorNum [out] Sector number
     * @param divNum    [out] Division number
     * @param divNums   [out] Number of divisions
     * @return Track data
     */
    public DiskImageTrack getTrackFromSectorPos(int sectorPos, int[] sectorNum, int[] divNum, int[] divNums) {
        int[] trackNum = {0};
        int[] sideNum = {0};
        type.getNumFromSectorPos(sectorPos, trackNum, sideNum, sectorNum, divNum, divNums);
        return disk.getTrack(trackNum[0], sideNum[0]);
    }

    /**
     * Returns sector from sector position (serial number where track 0, side 0, sector 1 is 0)
     * <p>
     * The sector position is a serial number where track 0, side 0, sector 1 is 0, regardless of the model
     *
     * @param sectorPos Sector position (serial number where track 0, side 0, sector 1 is 0)
     * @param trackNum  [out] Track number
     * @param sideNum   [out] Side number
     * @param divNum    [out] Division number
     * @param divNums   [out] Number of divisions
     * @return Sector data
     */
    public DiskImageSector getSectorFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] divNum, int[] divNums) {
        int[] sectorNum = {1};
        type.getNumFromSectorPos(sectorPos, trackNum, sideNum, sectorNum, divNum, divNums);
        int density = getValidDensityType();
        return disk.getSector(trackNum[0], sideNum[0], sectorNum[0], density);
    }

    public DiskImageSector getSectorFromSectorPos(int sectorPos) {
        return getSectorFromSectorPos(sectorPos, null, null);
    }

    public DiskImageSector getSectorFromSectorPos(int sectorPos, int[] divNum) {
        return getSectorFromSectorPos(sectorPos, divNum, null);
    }

    /**
     * Returns sector from sector position (serial number where track 0, side 0, sector 1 is 0)
     * <p>
     * The sector position is a serial number where track 0, side 0, sector 1 is 0, regardless of the model
     *
     * @param sectorPos Sector position (serial number where track 0, side 0, sector 1 is 0)
     * @param divNum    [out] Division number
     * @param numOfDivs [out] Number of divisions
     * @return Sector data
     */
    public DiskImageSector getSectorFromSectorPos(int sectorPos, int[] divNum, int[] numOfDivs) {
        int[] trackNum = {0};
        int[] sideNum = {0};
        return getSectorFromSectorPos(sectorPos, trackNum, sideNum, divNum, numOfDivs);
    }

    /**
     * Returns starting sector number
     *
     * Prioritize DiskBasicParam
     */
    public int getSectorNumberBase() {
        int val = getSectorNumberBaseOnBasic();
        if (val < 0) val = getSectorNumberBaseOnDisk();
        return val;
    }

    /** Set character encoding scheme */
    public void setCharCode(String name) {
        if (name.equals(charCode)) return;
        charCode = name;
        codes.setMap(name);
    }

    /** Returns the current character encoding scheme */
    public String getCharCode() {
        return charCode;
    }

    /** Character encoding scheme */
    public CharCodes getCharCodes() {
        return codes;
    }

    /** Whether disk is available */
    public boolean canUse() {
        return (disk != null);
    }

    /** Returns disk image */
    public DiskImageDisk getDisk() {
        return disk;
    }

    /** Set selected side */
    public void setSelectedSide(int val) {
        selectedSide = val;
    }

    /** Returns selected side */
    public int getSelectedSide() {
        return selectedSide;
    }

    /** FAT class */
    public DiskBasicFat getFat() {
        return fat;
    }

    /** DIR class */
    public <T extends Directory> DiskBasicDir<T> getDir() {
        return dir;
    }

    /** TYPE class */
    public <T extends Directory> DiskBasicType<T> getType() {
        return type;
    }

    /** Invert data if necessary */
    public byte invertUint8(byte val) {
        return isDataInverted() ? (byte) (val ^ 0xff) : val;
    }

    /** Invert data if necessary */
    public short invertUint16(short val) {
        return isDataInverted() ? (short) (val ^ 0xffff) : val;
    }

    /** Invert data if necessary and consider endianness */
    public short invertAndOrderUint16(short val) {
        val = isDataInverted() ? (short) (val ^ 0xffff) : val;
        return orderUint16(val);
    }

    /** Invert data if necessary */
    public int invertUint32(int val) {
        return isDataInverted() ? ~val : val;
    }

    /** Invert data if necessary and consider endianness */
    public int invertAndOrderUint32(int val) {
        val = isDataInverted() ? ~val : val;
        return orderUint32(val);
    }

    /** Invert data if necessary */
    public void invertMemory(byte[] data, int len) {
        if (isDataInverted()) Common.invertMemory(data, len);
    }

    /** Invert data if necessary */
    public void invertMemory(byte[] src, int len, byte[] dst) {
        System.arraycopy(src, 0, dst, 0, len);
        if (isDataInverted()) Common.invertMemory(dst, len);
    }

    /** Returns value considering endianness */
    public short orderUint16(short val) {
        return isBigEndian() ? Short.reverseBytes(val) : val;
    }

    /** Returns value considering endianness */
    public int orderUint32(int val) {
        return isBigEndian() ? Integer.reverseBytes(val) : val;
    }

    /** Returns DISK BASIC type number */
    public int getFormatTypeNumber() {
        return getFormatType().getTypeNumber();
    }

    /** Error message */
    public List<String> getErrorMessage(int maxRow) {
        return errInfo.getMessages(maxRow);
    }

    /**
     * Whether error exists
     *
     * @return <0: Error, 0: Normal, >0: Warning
     */
    public int getErrorLevel() {
        return errInfo.getValid();
    }

    public DiskBasicError getErrInfo() {
        return errInfo;
    }

    /** Show error message */
    public void showErrorMessage() {
        ResultInfo.showMessage(getErrorLevel(), getErrorMessage(20));
    }

    /** Clear error message */
    public void clearErrorMessage() {
        errInfo.clear();
    }
}
