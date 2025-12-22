package l3diskex.basicfmt;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Parambase.TemplatesBase;
import l3diskex.Parambase.ValidNameRule;
import l3diskex.Utils;
import l3diskex.diskimg.DiskParam.DiskParamName;
import l3diskex.diskimg.DiskParam.NumSectorsParam;
import l3diskex.diskimg.DiskParam.SectorInterleave;

import static l3diskex.basicfmt.BasicCommon.FORMAT_TYPE_UNKNOWN;


/** Class that holds DISK BASIC parameters */
public class DiskBasicParam extends DiskBasicParamBase {

    private static final Logger logger = System.getLogger(DiskBasicParam.class.getName());

    public static class DiskBasicParamBases extends TemplatesBase {

        private boolean setVolumeRule;

        public DiskBasicParamBases() {
            setVolumeRule = false;
        }

        /** Load common parameters */
        public boolean load(Node node, String name, String value, String localeName, DiskBasicParamBase param, StringBuilder errMsgs) {
            boolean valid = true;
            if (name.equals("SectorsPerGroup")) {
                param.setSectorsPerGroup(Utils.toInt(value));
            } else if (name.equals("GroupFinalCode")) {
                param.setGroupFinalCode(Utils.toInt(value));
            } else if (name.equals("GroupSystemCode")) {
                param.setGroupSystemCode(Utils.toInt(value));
            } else if (name.equals("GroupUnusedCode")) {
                param.setGroupUnusedCode(Utils.toInt(value));
            } else if (name.equals("DirTerminateCode")) {
                param.setDirTerminateCode((byte) Utils.toInt(value));
            } else if (name.equals("DirSpaceCode")) {
                param.setDirSpaceCode((byte) Utils.toInt(value));
            } else if (name.equals("DirTrimmingCode")) {
                param.setDirTrimmingCode((byte) Utils.toInt(value));
            } else if (name.equals("DirStartPosition")) {
                param.setDirStartPos(Utils.toInt(value));
            } else if (name.equals("DirStartPositionOnRoot")) {
                param.setDirStartPosOnRoot(Utils.toInt(value));
            } else if (name.equals("DirStartPositionOnSector")) {
                param.setDirStartPosOnSector(Utils.toInt(value));
            } else if (name.equals("DirStartPositionOnGroup")) {
                param.setDirStartPosOnGroup(Utils.toInt(value));
            } else if (name.equals("SpecialAttributes")) {
                List<MyAttribute> attrs = new ArrayList<>();
                loadMyAttributesInTypes(node, localeName, errMsgs, attrs);
                param.setSpecialAttributes(attrs);
            } else if (name.equals("AttributesByExtension")) {
                List<MyAttribute> attrs = new ArrayList<>();
                loadMyAttributesInTypes(node, localeName, errMsgs, attrs);
                param.setAttributesByExtension(attrs);
            } else if (name.equals("FillCodeOnFormat")) {
                param.setFillCodeOnFormat((byte) Utils.toInt(value));
            } else if (name.equals("FillCodeOnFAT")) {
                param.setFillCodeOnFAT((byte) Utils.toInt(value));
            } else if (name.equals("FillCodeOnDir")) {
                param.setFillCodeOnDir((byte) Utils.toInt(value));
            } else if (name.equals("DeleteCodeOnDir")) {
                param.setDeleteCode((byte) Utils.toInt(value));
            } else if (name.equals("TextTerminateCode")) {
                param.setTextTerminateCode((byte) Utils.toInt(value));
            } else if (name.equals("ExtensionPreCode")) {
                param.setExtensionPreCode((byte) Utils.toInt(value));
            } else if (name.equals("FileNameCharacters")) {
                valid = loadValidChars(node, param.getValidFileNameForMod(), errMsgs);
                if (!setVolumeRule) {
                    param.setValidVolumeName(param.getValidFileName());
                }
            } else if (name.equals("VolumeNameCharacters")) {
                valid = loadValidChars(node, param.getValidVolumeNameForMod(), errMsgs);
                setVolumeRule = valid;
            } else if (name.equals("FileNameCompareCase")) {
                boolean[] val = new boolean[1];
                loadFileNameCompareCase(node, val);
                param.compareCaseInsense(val[0]);
            } else if (name.equals("ToUpperFileNameBeforeDialog")) {
                param.toUpperBeforeDialog(Utils.toBool(value));
            } else if (name.equals("ToUpperFileNameAfterRenamed")) {
                param.toUpperAfterRenamed(Utils.toBool(value));
            } else if (name.equals("RequireFileName")) {
                param.getValidFileNameForMod().requireName(Utils.toBool(value));
            } else if (name.equals("RequireVolumeName")) {
                param.getValidVolumeNameForMod().requireName(Utils.toBool(value));
            } else if (name.equals("VolumeNameMaxLength")) {
                param.getValidVolumeNameForMod().setMaxLength(Utils.toInt(value));
            } else if (name.equals("Endian")) {
                param.bigEndian(value.equalsIgnoreCase("BIG"));
            } else if (!name.isEmpty()) {
                Object[] nVal = new Object[1];
                param.getVariousParam(name, nVal);
                loadVariousParam(node, value, nVal);
                param.setVariousParam(name, nVal[0]);
            }
            return valid;
        }
    }

    /** DISK BASIC format type */
    public static class DiskBasicFormat extends DiskBasicParamBase {

        /** Format type number */
        private int typeNumber;
        /** Volume name */
        private boolean hasVolumeName;
        /** Volume number */
        private boolean hasVolumeNumber;
        /** Volume date */
        private boolean hasVolumeDate;

        /** Initialization */
        private void clearBasicFormatPrivate() {
            typeNumber = FORMAT_TYPE_UNKNOWN;
            hasVolumeName = false;
            hasVolumeNumber = false;
            hasVolumeDate = false;
        }

        public DiskBasicFormat() {
            clearBasicFormatPrivate();
        }

        /** Initialization */
        public void clearBasicFormat() {
            clearBasicParamBase();
            clearBasicFormatPrivate();
        }

        /** Format type number */
        public int getTypeNumber() {
            return typeNumber;
        }

        /** Volume name */
        public boolean hasVolumeName() {
            return hasVolumeName;
        }

        /** Volume number */
        public boolean hasVolumeNumber() {
            return hasVolumeNumber;
        }

        /** Volume date */
        public boolean hasVolumeDate() {
            return hasVolumeDate;
        }

//        /** Whether file name is required */
//        public bool isFileNameRequired() { return filenameRequire; }

        /** Format type number */
        public void setTypeNumber(int val) {
            typeNumber = val;
        }

        /** Volume name */
        public void hasVolumeName(boolean val) {
            hasVolumeName = val;
        }

        /** Volume number */
        public void hasVolumeNumber(boolean val) {
            hasVolumeNumber = val;
        }

        /** Volume date */
        public void hasVolumeDate(boolean val) {
            hasVolumeDate = val;
        }

//	      /** Whether file name is required */
//	      public void requireFileName(bool val) { filename_require = val; }
    }

    /** List of DiskBasicFormat */
    public static class DiskBasicFormats extends TemplatesBase {

        List<DiskBasicFormat> list = new ArrayList<>();

        /**
         * Load DiskBasicFormat element
         *
         * @see "basicTypes.xml"
         */
        public boolean load(Node node, String localeName, StringBuilder errMsgs) {
            list.clear();

            boolean valid = false;
            while (node != null && !valid) {
                if (node.getNodeName().equals("DiskBasicFormats")) {
                    valid = true;
                    break;
                }
                node = node.getNextSibling();
            }
            if (!valid) {
                logger.log(Level.ERROR, "no DiskBasicFormats");
                return false;
            }

            valid = true;
            Node item = node.getFirstChild();
            while (item != null && valid) {
                if (item.getNodeName().equals("DiskBasicFormat")) {
                    DiskBasicFormat f = new DiskBasicFormat();
                    DiskBasicParamBases paramBases = new DiskBasicParamBases();
                    String sTypeNumber = ((Element) item).getAttribute("type");
                    int typeNumber = Utils.toInt(sTypeNumber);
                    f.setTypeNumber(typeNumber);

                    Node itemnode = item.getFirstChild();
                    while (itemnode != null) {
                        String name = itemnode.getNodeName();
                        String str = itemnode.getTextContent();
                        switch (name) {
                            case "HasVolumeName" -> f.hasVolumeName(Utils.toBool(str));
                            case "HasVolumeNumber" -> f.hasVolumeNumber(Utils.toBool(str));
                            case "HasVolumeDate" -> f.hasVolumeDate(Utils.toBool(str));
                            default -> {
                                boolean rc = paramBases.load(itemnode, name, str, localeName, f, errMsgs);
                                valid = (valid && rc);
                            }
                        }
                        itemnode = itemnode.getNextSibling();
                    }

                    if (find(typeNumber) == null) {
                        list.add(f);
                    } else {
                        errMsgs.append("\n");
                        errMsgs.append("Duplicate type number in DiskBasicFormat : ");
                        errMsgs.append("%d".formatted(typeNumber));
                        logger.log(Level.WARNING, "Duplicate type number in DiskBasicFormat : " + typeNumber);
                        valid = false;
                        break;
                    }
                }
                item = item.getNextSibling();
            }
            return valid;
        }

        /** @param formatType Format type */
        public DiskBasicFormat find(int formatType) {
            DiskBasicFormat match = null;
            for (DiskBasicFormat item : list) {
                if (item.getTypeNumber() == formatType) {
                    match = item;
                    break;
                }
            }
            return match;
        }
    }

    /** BASIC type name */
    private String basicTypeName;
    /** BASIC category name */
    private List<String> basicCategoryNames = new ArrayList<>();
    /** Format type */
    private DiskBasicFormat formatType;

    /** Format subtype number */
    private int formatSubtypeNumber;
    /** Number of sides used by BASIC */
    private int sidesOnBasic;
    /** Number of sectors/track used by BASIC */
    private int sectorsOnBasic;
    /** Number of sectors/track used by BASIC */
    private List<NumSectorsParam> sectorsOnBasicList = new ArrayList<>();
    /** Sector number base used by BASIC */
    private int sectorNumberBase;
    /** Number of tracks/side used by BASIC */
    private int tracksOnBasic;
    /** File management area */
    private int managedTrackNumber;
    /** Number of groups per track */
    private int groupsPerTrack;
    /** Number of groups per sector */
    private int groupsPerSector;
    /** Number of reserved sectors */
    private int reservedSectors;
    /** Number of file management areas */
    private int numberOfFats;
    /** Number of valid/used file management areas */
    private int validNumberOfFats;
    /** Number of sectors in FAT area */
    private int sectorsPerFat;
    /** FAT starting position (bytes) */
    private int fatStartPos;
    /** FAT maximum group number */
    private int fatEndGroup;
    /** Side number where FAT area is located */
    private int fatSideNumber;
    /** Reserved groups */
    private List<Integer> reservedGroups = new ArrayList<>();
    /** Root directory starting sector */
    private int dirStartSector;
    /** Root directory ending sector */
    private int dirEndSector;
    /** Number of root directory entries */
    private int dirEntryCount;
    /** Initial number of groups for subdirectories */
    private int subdirGroupSize;
    /** Group width (bytes) */
    private int groupWidth;
    /** Number of groups that can be specified in one directory entry */
    private int groupsPerDirEntry;
    /** Valid density 0:double 1:single */
    private int validDensityType;
    /** Software sector skew (sector interval) */
    private SectorInterleave sectorSkew = new SectorInterleave();
    /** Media ID */
    private byte mediaId;
    /** Whether data bits are inverted */
    private boolean dataInverted;
    /** Whether sides are reversed */
    private boolean sideReversed;
    /** Whether each side can be accessed independently in an OS that uses only one side */
    private boolean mountEachSides;
    /** Description */
    private String basicDescription;

    /** Initialization */
    private void clearBasicParamPrivate() {
        basicTypeName = "";
        basicCategoryNames.clear();
        formatType = null;
        formatSubtypeNumber = 0;
        sidesOnBasic = 0;
        sectorsOnBasic = -1;
        sectorsOnBasicList.clear();
        sectorNumberBase = -1;
        tracksOnBasic = -1;
        managedTrackNumber = 0;
        groupsPerTrack = 0;
        groupsPerSector = 1;
        reservedSectors = 0;
        numberOfFats = 1;
        validNumberOfFats = -1;
        sectorsPerFat = 0;
        fatStartPos = 0;
        fatEndGroup = 0;
        fatSideNumber = -1;
        reservedGroups.clear();
        dirStartSector = -1;
        dirEndSector = -1;
        dirEntryCount = -1;
        subdirGroupSize = 1;
        groupWidth = 1;
        groupsPerDirEntry = 0;
        validDensityType = -1;
        sectorSkew.set(1);
        mediaId = 0x00;
        dataInverted = false;
        sideReversed = false;
        mountEachSides = false;
        basicDescription = "";
    }

    public DiskBasicParam() {
        clearBasicParamPrivate();
    }

    /** Initialization */
    public void clearBasicParam() {
        clearBasicParamBase();
        clearBasicParamPrivate();
    }

    /** Configuration */
    public void setBasicParam(DiskBasicParam src) {
        setBasicParamBase(src);
        this.basicTypeName = src.basicTypeName;
        this.basicCategoryNames.clear();
        this.basicCategoryNames.addAll(src.basicCategoryNames);
        this.formatType = src.formatType;
        this.formatSubtypeNumber = src.formatSubtypeNumber;
        this.sidesOnBasic = src.sidesOnBasic;
        this.sectorsOnBasic = src.sectorsOnBasic;
        this.sectorsOnBasicList = src.sectorsOnBasicList; // Deep copy may be needed
        this.sectorNumberBase = src.sectorNumberBase;
        this.tracksOnBasic = src.tracksOnBasic;
        this.managedTrackNumber = src.managedTrackNumber;
        this.groupsPerTrack = src.groupsPerTrack;
        this.groupsPerSector = src.groupsPerSector;
        this.reservedSectors = src.reservedSectors;
        this.numberOfFats = src.numberOfFats;
        this.validNumberOfFats = src.validNumberOfFats;
        this.sectorsPerFat = src.sectorsPerFat;
        this.fatStartPos = src.fatStartPos;
        this.fatEndGroup = src.fatEndGroup;
        this.fatSideNumber = src.fatSideNumber;
        this.reservedGroups.clear();
        this.reservedGroups.addAll(src.reservedGroups);
        this.dirStartSector = src.dirStartSector;
        this.dirEndSector = src.dirEndSector;
        this.dirEntryCount = src.dirEntryCount;
        this.subdirGroupSize = src.subdirGroupSize;
        this.groupWidth = src.groupWidth;
        this.groupsPerDirEntry = src.groupsPerDirEntry;
        this.validDensityType = src.validDensityType;
        this.sectorSkew = src.sectorSkew; // Deep copy may be needed
        this.mediaId = src.mediaId;
        this.dataInverted = src.dataInverted;
        this.sideReversed = src.sideReversed;
        this.mountEachSides = src.mountEachSides;
        this.basicDescription = src.basicDescription;
    }

    /** Calculate start/end sectors */
    public void calcDirStartEndSector(int sector_size) {
        if (dirStartSector < 0) {
            dirStartSector = reservedSectors + numberOfFats * sectorsPerFat + 1;
        }
        if (dirEndSector < 0) {
            // Assuming 32 is the fixed size of a directory entry
            dirEndSector = dirStartSector + dirEntryCount * 32 / sector_size - 1;
        }
        if (dirEntryCount < 0) {
            dirEntryCount = (dirEndSector - dirStartSector + 1) * sector_size / 32;
        }
    }

    /** BASIC type name */
    public String getBasicTypeName() {
        return basicTypeName;
    }

    /** BASIC category name */
    public List<String> getBasicCategoryNames() {
        return basicCategoryNames;
    }

    /** BASIC category name */
    public List<String> getBasicCategoryNamesForMod() {
        return basicCategoryNames; // Changed name
    }

    /** BASIC type */
    public DiskBasicFormat getFormatType() {
        return formatType;
    }

    /** Subtype number */
    public int getFormatSubTypeNumber() {
        return formatSubtypeNumber;
    }

    /** Number of sides used by BASIC */
    public int getSidesPerDiskOnBasic() {
        return sidesOnBasic;
    }

    /** BASIC type */
    public int getSectorsPerTrackOnBasic() {
        return sectorsOnBasic;
    }

    /** Sector number base used by BASIC */
    public int getSectorNumberBaseOnBasic() {
        return sectorNumberBase;
    }

    /** Number of tracks/side used by BASIC */
    public int getTracksPerSideOnBasic() {
        return tracksOnBasic;
    }

    /** Track number where file management area is located */
    public int getManagedTrackNumber() {
        return managedTrackNumber;
    }

    /** Number of groups per track */
    public int getGroupsPerTrack() {
        return groupsPerTrack;
    }

    /** Number of groups per sector */
    public int getGroupsPerSector() {
        return groupsPerSector;
    }

    /** Number of reserved sectors */
    public int getReservedSectors() {
        return reservedSectors;
    }

    /** Number of file management areas */
    public int getNumberOfFats() {
        return numberOfFats;
    }

    /** Number of valid/used file management areas */
    public int getValidNumberOfFats() {
        return validNumberOfFats;
    }

    /** Number of sectors in FAT area */
    public int getSectorsPerFat() {
        return sectorsPerFat;
    }

    /** FAT starting sector */
    public int getFatStartSector() {
        return (reservedSectors + 1);
    }

    /** FAT starting position (bytes) */
    public int getFatStartPos() {
        return fatStartPos;
    }

    /** FAT maximum group number */
    public int getFatEndGroup() {
        return fatEndGroup;
    }

    /** Side number where FAT area is located */
    public int getFatSideNumber() {
        return fatSideNumber;
    }

    /** Reserved group numbers */
    public List<Integer> getReservedGroups() {
        return reservedGroups;
    }

    /** Root directory starting sector */
    public int getDirStartSector() {
        return dirStartSector;
    }

    /** Root directory ending sector */
    public int getDirEndSector() {
        return dirEndSector;
    }

    /** Number of root directory entries */
    public int getDirEntryCount() {
        return dirEntryCount;
    }

    /** Initial number of groups for subdirectories */
    public int getSubDirGroupSize() {
        return subdirGroupSize;
    }

    /** Group width (bytes) */
    public int getGroupWidth() {
        return groupWidth;
    }

    /** Number of groups that can be specified in one directory entry */
    public int getGroupsPerDirEntry() {
        return groupsPerDirEntry;
    }

    /** Valid density */
    public int getValidDensityType() {
        return validDensityType;
    }

    /** Software sector skew (sector interval) */
    public int getSectorSkew() {
        return sectorSkew.get();
    }

    /** Software sector skew (sector interval) specific map */
    public int getSectorSkewMap(int idx) {
        return sectorSkew.get(idx);
    }

    /** Whether it has a specific map for software sector skew (sector interval) */
    public boolean hasSectorSkewMap() {
        return sectorSkew.hasMap();
    }

    /** Media ID */
    public byte getMediaId() {
        return mediaId;
    }

//    /** Whether file name is required */
//    public boolean isFileNameRequired() { return filenameRequire; }

    /** Whether data bits are inverted */
    public boolean isDataInverted() {
        return dataInverted;
    }

    /** Whether sides are reversed */
    public boolean isSideReversed() {
        return sideReversed;
    }

    /** Returns the reversed side number */
    public int getReversedSideNumber(int side_num) {
        return (sideReversed && 0 <= side_num && side_num < sidesOnBasic ? sidesOnBasic - side_num - 1 : side_num);
    }

    /** Whether each side can be accessed independently in an OS that uses only one side */
    public boolean canMountEachSides() {
        return (mountEachSides && sidesOnBasic == 1);
    }

    /** Description */
    public String getBasicDescription() {
        return basicDescription;
    }

    /** BASIC type name */
    public void setBasicTypeName(String str) {
        basicTypeName = str;
    }

    /** BASIC category name */
    public void setBasicCategoryNames(ArrayList<String> arr) {
        basicCategoryNames = arr;
    }

    /** BASIC type */
    public void setFormatType(DiskBasicFormat val) {
        formatType = val;
    }

    /** Subtype number */
    public void setFormatSubTypeNumber(int val) {
        formatSubtypeNumber = val;
    }

    /** Number of sides used by BASIC */
    public void setSidesPerDiskOnBasic(int val) {
        sidesOnBasic = val;
    }

    /** Number of sectors/track used by BASIC */
    public void setSectorsPerTrackOnBasic(int val) {
        sectorsOnBasic = val;
    }

    /** Sector number base used by BASIC */
    public void setSectorNumberBaseOnBasic(int val) {
        sectorNumberBase = val;
    }

    /** Number of tracks/side used by BASIC */
    public void setTracksPerSideOnBasic(int val) {
        tracksOnBasic = val;
    }

    /** Track number where file management area is located */
    public void setManagedTrackNumber(int val) {
        managedTrackNumber = val;
if (basicCategoryNames.contains("N88")) {
 logger.log(Level.TRACE, "managedTrackNumber: " + managedTrackNumber);
}
    }

    /** Number of groups per track */
    public void setGroupsPerTrack(int val) {
        groupsPerTrack = val;
    }

    /** Number of groups per sector */
    public void setGroupsPerSector(int val) {
        groupsPerSector = val;
    }

    /** Number of reserved sectors */
    public void setReservedSectors(int val) {
        reservedSectors = val;
    }

    /** Number of file management areas */
    public void setNumberOfFats(int val) {
        numberOfFats = val;
    }

    /** Number of valid/used file management areas */
    public void setValidNumberOfFats(int val) {
        validNumberOfFats = val;
    }

    /** Number of sectors in FAT area */
    public void setSectorsPerFat(int val) {
        sectorsPerFat = val;
    }

    /** FAT starting position (bytes) */
    public void setFatStartPos(int val) {
        fatStartPos = val;
    }

    /** FAT maximum group number */
    public void setFatEndGroup(int val) {
        fatEndGroup = val;
    }

    /** Side number where FAT area is located */
    public void setFatSideNumber(int val) {
        fatSideNumber = val;
    }

    /** Reserved group numbers */
    public void setReservedGroups(List<Integer> arr) {
        reservedGroups = arr;
    }

    /** Root directory starting sector */
    public void setDirStartSector(int val) {
        dirStartSector = val;
    }

    /** Group width (bytes) */
    public void setGroupWidth(int val) {
        groupWidth = val;
    }

    /** Number of groups that can be specified in one directory entry */
    public void setGroupsPerDirEntry(int val) {
        groupsPerDirEntry = val;
    }

    /** Valid density */
    public void setValidDensityType(int val) {
        validDensityType = val;
    }

    /** Software sector skew (sector interval) */
    public void setSectorSkew(int val) {
        sectorSkew.set(val);
    }

    /** Software sector skew (sector interval) */
    public void setSectorSkewMap(List<Integer> arr) {
        sectorSkew.set(arr);
    }

    /** Root directory ending sector */
    public void setDirEndSector(int val) {
        dirEndSector = val;
    }

    /** Number of root directory entries */
    public void setDirEntryCount(int val) {
        dirEntryCount = val;
    }

    /** Initial number of groups for subdirectories */
    public void setSubDirGroupSize(int val) {
        subdirGroupSize = val;
    }

    /** Media ID */
    public void setMediaId(byte val) {
        mediaId = val;
    }

//    /** Whether file name is required */
//    public void requireFileName(bool val) { filename_require = val; }

    /** Whether data bits are inverted */
    public void dataInverted(boolean val) {
        dataInverted = val;
    }

    /** Whether sides are reversed */
    public void sideReversed(boolean val) {
        sideReversed = val;
    }

    /** Whether each side can be accessed independently in an OS that uses only one side */
    public void mountEachSides(boolean val) {
        mountEachSides = val;
    }

    /** Description */
    public void setBasicDescription(String str) {
        basicDescription = str;
    }

    /** Number of sectors/track used by BASIC */
    public List<NumSectorsParam> sectorsPerTrackOnBasicList() {
        return sectorsOnBasicList;
    }

    /** Add BASIC category name */
    public void addBasicCategoryName(String str) {
        if (!basicCategoryNames.contains(str)) {
            basicCategoryNames.add(str);
        }
    }

    /** Returns BASIC category name */
    public String getBasicCategoryName() {
        if (basicCategoryNames.isEmpty()) {
            return "";
        } else {
            return basicCategoryNames.getFirst();
        }
    }

    /** Whether BASIC category name exists */
    public boolean findBasicCategoryName(String str) {
        return (basicCategoryNames.contains(str));
    }

    /** Load ReservedGroups element */
    public boolean loadReservedGroupsInTypes(Node node, String localeName, StringBuilder errMsgs) {
        Node citeMNode = node.getFirstChild();
        while (citeMNode != null) {
            if (citeMNode.getNodeName().equals("Group")) {
                String first = ((Element) citeMNode).getAttribute("first");
                String last = ((Element) citeMNode).getAttribute("last");
                if (!first.isEmpty() && !last.isEmpty()) {
                    int fval = Utils.toInt(first);
                    int lval = Utils.toInt(last);
                    for (int i = fval; i <= lval; i++) {
                        reservedGroups.add(i);
                    }
                }
                String str = citeMNode.getTextContent();
                if (!str.isEmpty()) {
                    int reserved_group = Utils.toInt(str);
                    reservedGroups.add(reserved_group);
                }
            }
            citeMNode = citeMNode.getNextSibling();
        }
        return true;
    }

    /** Load SectorSkewMap element */
    public boolean loadSectorSkewMap(Node node) {
        List<Integer> map = new ArrayList<>();
        Node cNode = node.getFirstChild();
        while (cNode != null) {
            String name = cNode.getNodeName();
            if (name.equals("Value")) {
                map.add(Utils.toInt(cNode.getTextContent()));
            }
            cNode = cNode.getNextSibling();
        }

        sectorSkew.set(map);
        return true;
    }

    /** Load SectorsPerTrack element */
    public boolean loadNumSectorsMap(Node node, String val) {
        // sec_param  Number of sectors/track (if the same for all tracks)
        int secParam = getSectorsPerTrackOnBasic();
        // sec_params Number of sectors/track (if different for each track)
        List<NumSectorsParam> sec_params = sectorsPerTrackOnBasicList();

        String str = "";
        int startTrack = -1;
        int numOfTracks = -1;
        int secPerTrk = 1;

        if (!(str = ((Element) node).getAttribute("start")).isEmpty()) {
            startTrack = Utils.toInt(str);
        }
        if (!(str = ((Element) node).getAttribute("tracks")).isEmpty()) {
            numOfTracks = Utils.toInt(str);
        }
        secPerTrk = Utils.toInt(val);

        if (startTrack < 0 && numOfTracks < 0) {
            secParam = secPerTrk;
        } else {
            sec_params.add(new NumSectorsParam(startTrack, numOfTracks, secPerTrk));
        }

        setSectorsPerTrackOnBasic(secParam);
        return true;
    }

    /** Load Categories element */
    public boolean loadCategories(Node node, String localeName, StringBuilder errMsgs) {
        boolean valid = true;
        Node item = node.getFirstChild();
        while (item != null && valid) {
            if (item.getNodeName().equals("Category")) {
                Node itemnode = item.getFirstChild();
                while (itemnode != null) {
                    String name = item.getTextContent();
                    if (!name.isEmpty() && !basicCategoryNames.contains(name)) {
                        basicCategoryNames.add(name);
                    }
                    itemnode = itemnode.getNextSibling();
                }
            }
            item = item.getNextSibling();
        }
        return valid;
    }

    /** Sort by description */
    public static int sortByDescription(DiskBasicParam item1, DiskBasicParam item2) {
        return item1.getBasicDescription().compareTo(item2.getBasicDescription());
    }

    /** List of DiskBasicParam */
    public static class DiskBasicParams extends TemplatesBase {

        List<DiskBasicParam> list = new ArrayList<>();

        /**
         * Load DiskBasicType element
         *
         * @see "basicTypes.xml"
         */
        public boolean load(Node node, String localeName, DiskBasicFormats formats, StringBuilder errMsgs) {
            list.clear();

            boolean valid = false;
            while (node != null && !valid) {
                if (node.getNodeName().equals("DiskBasicTypes")) {
                    valid = true;
                    break;
                }
                node = node.getNextSibling();
            }
            if (!valid) return false;

            valid = true;
            Node item = node.getFirstChild();
            while (item != null && valid) {
                if (item.getNodeName().equals("DiskBasicType")) {
                    DiskBasicParam p = new DiskBasicParam();
                    DiskBasicParamBases param_bases = new DiskBasicParamBases();

                    String typeName = ((Element) item).getAttribute("name");
                    p.setBasicTypeName(typeName);

                    String formatName = ((Element) item).getAttribute("type");
                    DiskBasicFormat formatType = formats.find(Utils.toInt(formatName));
                    if (!formatName.isEmpty() && formatType != null) {
                        // Use format parameters as initial values
                        p.setFormatType(formatType);
                        //p.RequireFileName(formatType.isFileNameRequired());
                        p.setBasicParamBase(formatType);
                    } else {
                        // No format type
                        errMsgs.append("\n");
                        errMsgs.append("Unknown format type in DiskBasicType : ");
                        errMsgs.append(typeName);
                        return false;
                    }

                    p.addBasicCategoryName(((Element) item).getAttribute("category"));

                    int reservedSectors = -2;
                    int fatStartSector = 0;

                    int sectorsPerFat = 0;
                    int fatEndSector = 0;

                    String[] desc = {""}, descLocale = {""};

                    Node itemnode = item.getFirstChild();
                    while (itemnode != null) {
                        String name = itemnode.getNodeName();
                        String str = itemnode.getTextContent();
//                        if (itemnode.getName().equals("FormatType")) {
//                            DiskBasicFormat formatType = findFormat((DiskBasicFormatType) Utils.toInt(str));
//                            p.SetFormatType(formatType);
                        switch (name) {
                            case "FormatSubType" -> p.setFormatSubTypeNumber(Utils.toInt(str));
                            case "SidesPerDisk" -> p.setSidesPerDiskOnBasic(Utils.toInt(str));
                            case "SectorsPerTrack" -> p.loadNumSectorsMap(itemnode, str);
                            case "SectorNumberBase" -> p.setSectorNumberBaseOnBasic(Utils.toInt(str));
                            case "TracksPerSide" -> p.setTracksPerSideOnBasic(Utils.toInt(str));
                            case "ManagedTrackNumber" -> p.setManagedTrackNumber(Utils.toInt(str));
                            case "GroupsPerTrack" -> p.setGroupsPerTrack(Utils.toInt(str));
                            case "GroupsPerSector" -> p.setGroupsPerSector(Utils.toInt(str));
                            case "ReservedSectors" -> reservedSectors = Utils.toInt(str);
                            case "NumberOfFATs" -> p.setNumberOfFats(Utils.toInt(str));
                            case "ValidNumberOfFATs" -> p.setValidNumberOfFats(Utils.toInt(str));
                            case "SectorsPerFAT" -> sectorsPerFat = Utils.toInt(str);
                            case "FATStartSector" -> fatStartSector = Utils.toInt(str);
                            case "FATEndSector" -> fatEndSector = Utils.toInt(str);
                            case "FATStartPosition" -> p.setFatStartPos(Utils.toInt(str));
                            case "FATEndGroup" -> p.setFatEndGroup(Utils.toInt(str));
                            case "FATSideNumber" -> p.setFatSideNumber(Utils.toInt(str));
                            case "ReservedGroups" -> p.loadReservedGroupsInTypes(itemnode, localeName, errMsgs);
                            case "DirStartSector" -> p.setDirStartSector(Utils.toInt(str));
                            case "DirEndSector" -> p.setDirEndSector(Utils.toInt(str));
                            case "DirEntryCount" -> p.setDirEntryCount(Utils.toInt(str));
                            case "GroupWidth" -> p.setGroupWidth(Utils.toInt(str));
                            case "GroupsPerDirEntry" -> p.setGroupsPerDirEntry(Utils.toInt(str));
                            case "ValidDensityType" -> p.setValidDensityType(Utils.toInt(str));
                            case "SectorSkew" -> p.setSectorSkew(Utils.toInt(str));
                            case "SectorSkewMap" -> p.loadSectorSkewMap(itemnode);
                            case "SubDirGroupSize" -> p.setSubDirGroupSize(Utils.toInt(str));
                            case "MediaID" -> p.setMediaId((byte) Utils.toInt(str));
                            case "DataInverted" -> p.dataInverted(Utils.toBool(str));
                            case "SideReversed" -> p.sideReversed(Utils.toBool(str));
                            case "CanMountEachSides" -> p.mountEachSides(Utils.toBool(str));
                            case "Description" -> loadDescription(itemnode, localeName, desc, descLocale);
                            case "Categories" -> p.loadCategories(itemnode, localeName, errMsgs);
                            default -> {
                                boolean rc = param_bases.load(itemnode, name, str, localeName, p, errMsgs);
                                valid = (valid && rc);
                            }
                        }
                        itemnode = itemnode.getNextSibling();
                    }
                    if (fatStartSector > 0 && reservedSectors <= 0) {
                        reservedSectors = fatStartSector - 1;
                    }
                    p.setReservedSectors(reservedSectors);

                    if (fatEndSector > 0 && sectorsPerFat <= 0) {
                        sectorsPerFat = fatEndSector - fatStartSector + 1;
                    }
                    p.setSectorsPerFat(sectorsPerFat);

                    if (!descLocale[0].isEmpty()) {
                        desc = descLocale;
                    }
                    p.setBasicDescription(desc[0]);

                    if (find("", typeName) == null) {
                        list.add(p);
//System.out.println(p);
                    } else {
                        // Duplicate type name
                        errMsgs.append("\n");
                        errMsgs.append("Duplicate type name in DiskBasicType : ");
                        errMsgs.append(typeName);
                        valid = false;
                        break;
                    }
                }
                item = item.getNextSibling();
            }
            return valid;
        }

        /**
         * Search for parameters matching category and type
         *
         * @param category  Category name. If empty string, exclude from search criteria.
         * @param basicType Type name
         * @return Matching parameters
         */
        public DiskBasicParam find(String category, String basicType) {
            DiskBasicParam matchItem = null;
            for (DiskBasicParam item : list) {
                if (category.isEmpty() || item.findBasicCategoryName(category)) {
                    if (basicType.equals(item.getBasicTypeName())) {
                        matchItem = item;
                        break;
                    }
                }
            }
            return matchItem;
        }

        /**
         * Search for parameters matching category and included in type list
         *
         * @param category   Category name. If empty string, exclude from search criteria.
         * @param basicTypes Type name list
         * @return Matching parameters
         */
        public DiskBasicParam find(String category, List<DiskParamName> basicTypes) {
            DiskBasicParam matchItem = null;
            for (int i = 0; i < basicTypes.size() && matchItem == null; i++) {
                matchItem = find(category, basicTypes.get(i).getName());
            }
            return matchItem;
        }

        /**
         * Search for parameters matching category, type, side count, and sector count
         * First, search by category & type, and if not found, search by category & side count & sector count
         *
         * @param category  Category name (required)
         * @param basicType Type name (required)
         * @param sides     Side count
         * @param sectors   Sector count/track. If -1, exclude from search criteria.
         * @return Matching parameters
         */
        public DiskBasicParam find(String category, String basicType, int sides, int sectors) {
            DiskBasicParam match_item = null;
            // Whether it matches by category and type
            match_item = find(category, basicType);
            // Whether it matches by category, side count, and sector count
            if (match_item == null) {
                for (DiskBasicParam item : list) {
                    if (category.equals(item.getBasicCategoryName())) {
                        if (sides == item.getSidesPerDiskOnBasic()) {
                            if (sectors < 0 || item.getSectorsPerTrackOnBasic() < 0 || sectors == item.getSectorsPerTrackOnBasic()) {
                                match_item = item;
                                break;
                            }
                        }
                    }
                }
            }
            return match_item;
        }

        /**
         * Search for types matching DISK BASIC format type
         *
         * @param formatTypes DISK BASIC format type
         * @param types       [out] Matching type list
         * @return List count
         */
        public int findTypes(List<Integer> formatTypes, DiskBasicParams types) {
            types.list.clear();
            for (DiskBasicParam item : this.list) {
                for (int formatTypeVal : formatTypes) {
                    DiskBasicFormat fmt = item.getFormatType();
                    if (fmt != null && formatTypeVal == fmt.getTypeNumber()) {
                        types.list.add(item);
                    }
                }
            }
            return types.list.size();
        }

        /**
         * Search for type name list matching category name
         *
         * @param categoryName Category name
         * @param typeNames    [out] Type name list
         * @return List count
         */
        public int findNames(String categoryName, List<String> typeNames) {
            typeNames.clear();
            for (DiskBasicParam item : this.list) {
                if (item.findBasicCategoryName(categoryName)) {
                    typeNames.add(item.getBasicTypeName());
                }
            }
            return typeNames.size();
        }
    }

    @Override
    public String toString() {
//        return new StringJoiner(", ", DiskBasicParam.class.getSimpleName() + "[", "]")
//                .add("basicTypeName='" + basicTypeName + "'")
//                .add("basicCategoryNames=" + basicCategoryNames)
//                .add("formatType=" + formatType)
//                .add("formatSubtypeNumber=" + formatSubtypeNumber)
//                .add("sidesOnBasic=" + sidesOnBasic)
//                .add("sectorsOnBasic=" + sectorsOnBasic)
//                .add("sectorsOnBasicList=" + sectorsOnBasicList)
//                .add("sectorNumberBase=" + sectorNumberBase)
//                .add("tracksOnBasic=" + tracksOnBasic)
//                .add("managedTrackNumber=" + managedTrackNumber)
//                .add("groupsPerTrack=" + groupsPerTrack)
//                .add("groupsPerSector=" + groupsPerSector)
//                .add("reservedSectors=" + reservedSectors)
//                .add("numberOfFats=" + numberOfFats)
//                .add("validNumberOfFats=" + validNumberOfFats)
//                .add("sectorsPerFat=" + sectorsPerFat)
//                .add("fatStartPos=" + fatStartPos)
//                .add("fatEndGroup=" + fatEndGroup)
//                .add("fatSideNumber=" + fatSideNumber)
//                .add("reservedGroups=" + reservedGroups)
//                .add("dirStartSector=" + dirStartSector)
//                .add("dirEndSector=" + dirEndSector)
//                .add("dirEntryCount=" + dirEntryCount)
//                .add("subdirGroupSize=" + subdirGroupSize)
//                .add("groupWidth=" + groupWidth)
//                .add("groupsPerDirEntry=" + groupsPerDirEntry)
//                .add("validDensityType=" + validDensityType)
//                .add("sectorSkew=" + sectorSkew)
//                .add("mediaId=" + mediaId)
//                .add("dataInverted=" + dataInverted)
//                .add("sideReversed=" + sideReversed)
//                .add("mountEachSides=" + mountEachSides)
//                .add("basicDescription='" + basicDescription + "'")
//                .toString();
        return new StringJoiner(", ", "", "")
                .add(basicTypeName)
                .add("" + basicCategoryNames)
//                .add("" + formatType)
                .add("" + formatSubtypeNumber)
                .add("" + sidesOnBasic)
                .add("" + sectorsOnBasic)
                .add("" + sectorsOnBasicList)
                .add("" + sectorNumberBase)
                .add("" + tracksOnBasic)
                .add("" + managedTrackNumber)
                .add("" + groupsPerTrack)
                .add("" + groupsPerSector)
                .add("" + reservedSectors)
                .add("" + numberOfFats)
                .add("" + validNumberOfFats)
                .add("" + sectorsPerFat)
                .add("" + fatStartPos)
                .add("" + fatEndGroup)
                .add("" + fatSideNumber)
                .add("" + reservedGroups)
                .add("" + dirStartSector)
                .add("" + dirEndSector)
                .add("" + dirEntryCount)
                .add("" + subdirGroupSize)
                .add("" + groupWidth)
                .add("" + groupsPerDirEntry)
                .add("" + validDensityType)
                .add("" + sectorSkew)
                .add("" + mediaId)
                .add("" + dataInverted)
                .add("" + sideReversed)
                .add("" + mountEachSides)
                .add(basicDescription)
                .add(super.toString())
                .toString();
    }
}

/** Common parameters for DISK BASIC */
class DiskBasicParamBase {

    private static final Logger logger = System.getLogger(DiskBasicParamBase.class.getName());

    /** Group (cluster) size */
    protected int sectorsPerGroup;
    /** Code for final group (0xc0 - ) */
    protected int groupFinalCode;
    /** Code used by system (0xfe) */
    protected int groupSystemCode;
    /** Unused code (0xff) */
    protected int groupUnusedCode;
    /** Termination code for directory name */
    protected byte dirTerminateCode;
    /** Space code for directory name */
    protected byte dirSpaceCode;
    /** Space code for directory name (trimming code) */
    protected byte dirTrimmingCode;
    /** Starting position of subdirectory (bytes) */
    protected int dirStartPos;
    /** Starting position of root directory (bytes) */
    protected int dirStartPosOnRoot;
    /** Starting position per sector of directory */
    protected int dirStartPosOnSec;
    /** Starting position per group of directory */
    protected int dirStartPosOnGroup;
    /** Special attributes */
    protected List<MyAttribute> specialAttrs = new ArrayList<>();
    /** Relationship between extension and attributes */
    protected List<MyAttribute> attrsByExtension = new ArrayList<>();
    /** Code to fill during formatting */
    protected byte fillcodeOnFormat;
    /** Code to fill FAT area during formatting */
    protected byte fillcodeOnFat;
    /** Code to fill directory area during formatting */
    protected byte fillcodeOnDir;
    /** Code set when deleting a file */
    protected byte deleteCode;
    /** Termination code for text */
    protected byte textTerminateCode;
    /** Code put between file name and extension ('.') */
    protected byte extensionPreCode;
    /** Rules that can be set for file name */
    protected ValidNameRule validFileName = new ValidNameRule();
    /** Rules that can be set for volume name */
    protected ValidNameRule validVolumeName = new ValidNameRule();
    /** Whether to ignore case during file name comparison */
    protected boolean compareCaseInsensitive;
    /** Whether to convert to uppercase before displaying file name dialog */
    protected boolean toUpperBeforeDialog;
    /** Whether to convert to uppercase after file name dialog input */
    protected boolean toUpperAfterRenamed;
    /** Whether byte order is big endian */
    protected boolean bigEndian;
    /** Other specific parameters */
    protected Map<String, Object> variousParams = new HashMap<>();

    /** Initialization */
    protected void clearBasicParamBase() {
        sectorsPerGroup = 0;
        groupFinalCode = 0;
        groupSystemCode = 0;
        groupUnusedCode = 0;
        dirTerminateCode = 0x20;
        dirSpaceCode = 0x20;
        dirTrimmingCode = 0;
        dirStartPos = 0;
        dirStartPosOnRoot = 0;
        dirStartPosOnSec = 0;
        dirStartPosOnGroup = 0;
        specialAttrs.clear();
        attrsByExtension.clear();
        fillcodeOnFormat = 0;
        fillcodeOnFat = 0;
        fillcodeOnDir = 0;
        deleteCode = 0;
        textTerminateCode = 0x1a;
        extensionPreCode = 0x2e; // '.'
        validFileName.empty();
        validVolumeName.empty();
        compareCaseInsensitive = false;
        toUpperBeforeDialog = false;
        toUpperAfterRenamed = false;
        bigEndian = false;
        variousParams.clear();
    }

    public DiskBasicParamBase() {
        clearBasicParamBase();
    }

    /** Configuration */
    public void setBasicParamBase(DiskBasicParamBase src) {
        this.sectorsPerGroup = src.sectorsPerGroup;
        this.groupFinalCode = src.groupFinalCode;
        this.groupSystemCode = src.groupSystemCode;
        this.groupUnusedCode = src.groupUnusedCode;
        this.dirTerminateCode = src.dirTerminateCode;
        this.dirSpaceCode = src.dirSpaceCode;
        this.dirTrimmingCode = src.dirTrimmingCode;
        this.dirStartPos = src.dirStartPos;
        this.dirStartPosOnRoot = src.dirStartPosOnRoot;
        this.dirStartPosOnSec = src.dirStartPosOnSec;
        this.dirStartPosOnGroup = src.dirStartPosOnGroup;
        this.specialAttrs = src.specialAttrs; // Deep copy may be needed depending on MyAttributes
        this.attrsByExtension = src.attrsByExtension; // Deep copy may be needed
        this.fillcodeOnFormat = src.fillcodeOnFormat;
        this.fillcodeOnFat = src.fillcodeOnFat;
        this.fillcodeOnDir = src.fillcodeOnDir;
        this.deleteCode = src.deleteCode;
        this.textTerminateCode = src.textTerminateCode;
        this.extensionPreCode = src.extensionPreCode;
        this.validFileName = src.validFileName;
        this.validVolumeName = src.validVolumeName;
        this.compareCaseInsensitive = src.compareCaseInsensitive;
        this.toUpperBeforeDialog = src.toUpperBeforeDialog;
        this.toUpperAfterRenamed = src.toUpperAfterRenamed;
        this.bigEndian = src.bigEndian;
        this.variousParams.clear();
        this.variousParams.putAll(src.variousParams);
    }

    /** Group (cluster) size */
    public int getSectorsPerGroup() {
        return sectorsPerGroup;
    }

    /** Code for final group */
    public int getGroupFinalCode() {
        return groupFinalCode;
    }

    /** Code used by system */
    public int getGroupSystemCode() {
        return groupSystemCode;
    }

    /** Unused code */
    public int getGroupUnusedCode() {
        return groupUnusedCode;
    }

    /** Termination code for directory name */
    public byte getDirTerminateCode() {
        return dirTerminateCode;
    }

    /** Space code for directory name */
    public byte getDirSpaceCode() {
        return dirSpaceCode;
    }

    /** Space code for directory name (trimming code) */
    public byte getDirTrimmingCode() {
        return dirTrimmingCode;
    }

    /** Starting position of directory (bytes) */
    public int getDirStartPos() {
        return dirStartPos;
    }

    /** Starting position of root directory (bytes) */
    public int getDirStartPosOnRoot() {
        return dirStartPosOnRoot;
    }

    /** Starting position per sector of directory */
    public int getDirStartPosOnSector() {
        return dirStartPosOnSec;
    }

    /** Starting position per group of directory */
    public int getDirStartPosOnGroup() {
        return dirStartPosOnGroup;
    }

    /** Special attributes */
    public List<MyAttribute> getSpecialAttributes() {
        return specialAttrs;
    }

    /** Relationship between extension and attributes */
    public List<MyAttribute> getAttributesByExtension() {
        return attrsByExtension;
    }

    /** Code to fill during formatting */
    public byte getFillCodeOnFormat() {
        return fillcodeOnFormat;
    }

    /** Code to fill FAT area during formatting */
    public byte getFillCodeOnFAT() {
        return fillcodeOnFat;
    }

    /** Code to fill directory area during formatting */
    public byte getFillCodeOnDir() {
        return fillcodeOnDir;
    }

    /** Code set when deleting a file */
    public byte getDeleteCode() {
        return deleteCode;
    }

    /** Termination code for text */
    public byte getTextTerminateCode() {
        return textTerminateCode;
    }

    /** Code put between file name and extension (' . ') */
    public byte getExtensionPreCode() {
        return extensionPreCode;
    }

    /** Rules that can be set for file name */
    public ValidNameRule getValidFileName() {
        return validFileName;
    }

    /** Rules that can be set for file name */
    public ValidNameRule getValidFileNameForMod() {
        return validFileName;
    } // Changed name to avoid conflict with final version

    /** Rules that can be set for volume name */
    public ValidNameRule getValidVolumeName() {
        return validVolumeName;
    }

    /** Rules that can be set for volume name */
    public ValidNameRule getValidVolumeNameForMod() {
        return validVolumeName;
    } // Changed name

    /** Whether to ignore case during file name comparison */
    public boolean isCompareCaseInsensitive() {
        return compareCaseInsensitive;
    }

    /** Whether to convert to uppercase before displaying file name dialog */
    public boolean toUpperBeforeDialog() {
        return toUpperBeforeDialog;
    }

    /** Whether to convert to uppercase after file name dialog input */
    public boolean toUpperAfterRenamed() {
        return toUpperAfterRenamed;
    }

    /** Whether byte order is big endian */
    public boolean isBigEndian() {
        return bigEndian;
    }

    /** Specific parameters */
    public Map<String, Object> getVariousParams() {
        return variousParams;
    }

    /** Specific parameters */
    public void getVariousParam(String key, Object[] val) {
        Object value = variousParams.get(key);
        if (value != null) {
            val[0] = value;
        }
    }

    /** Specific parameters */
    public int getVariousIntegerParam(String key) {
        Object value = variousParams.get(key);
logger.log(Level.TRACE, "key: " + key + ", value: " + value);
        if (value != null) {
            return (int) value;
        } else {
            return 0;
        }
    }

    /** Specific parameters */
    public boolean getVariousBoolParam(String key) {
        Object value = variousParams.get(key);
        if (value != null) {
            return (boolean) value;
        } else {
            return false;
        }
    }

    /** Specific parameters */
    public String getVariousStringParam(String key) {
        Object value = variousParams.get(key);
        if (value != null) {
            return (String) value;
        } else {
            return "";
        }
    }

    /** Group (cluster) size */
    public void setSectorsPerGroup(int val) {
        sectorsPerGroup = val;
    }

    /** Code for final group */
    public void setGroupFinalCode(int val) {
        groupFinalCode = val;
    }

    /** Code used by system */
    public void setGroupSystemCode(int val) {
        groupSystemCode = val;
    }

    /** Unused code */
    public void setGroupUnusedCode(int val) {
        groupUnusedCode = val;
    }

    /** Termination code for directory name */
    public void setDirTerminateCode(byte val) {
        dirTerminateCode = val;
    }

    /** Space code for directory name */
    public void setDirSpaceCode(byte val) {
        dirSpaceCode = val;
    }

    /** Space code for directory name (trimming code) */
    public void setDirTrimmingCode(byte val) {
        dirTrimmingCode = val;
    }

    /** Starting position of directory (bytes) */
    public void setDirStartPos(int val) {
        dirStartPos = val;
    }

    /** Starting position of root directory (bytes) */
    public void setDirStartPosOnRoot(int val) {
        dirStartPosOnRoot = val;
    }

    /** Starting position per sector of directory */
    public void setDirStartPosOnSector(int val) {
        dirStartPosOnSec = val;
    }

    /** Starting position per group of directory */
    public void setDirStartPosOnGroup(int val) {
        dirStartPosOnGroup = val;
    }

    /** Special attributes */
    public void setSpecialAttributes(List<MyAttribute> arr) {
        specialAttrs = arr;
    }

    /** Relationship between extension and attributes */
    public void setAttributesByExtension(List<MyAttribute> arr) {
        attrsByExtension = arr;
    }

    /** Code to fill during formatting */
    public void setFillCodeOnFormat(byte val) {
        fillcodeOnFormat = val;
    }

    /** Code to fill FAT area during formatting */
    public void setFillCodeOnFAT(byte val) {
        fillcodeOnFat = val;
    }

    /** Code to fill directory area during formatting */
    public void setFillCodeOnDir(byte val) {
        fillcodeOnDir = val;
    }

    /** Code set when deleting a file */
    public void setDeleteCode(byte val) {
        deleteCode = val;
    }

    /** Termination code for text */
    public void setTextTerminateCode(byte val) {
        textTerminateCode = val;
    }

    /** Code put between file name and extension (' . ') */
    public void setExtensionPreCode(byte val) {
        extensionPreCode = val;
    }

    /** Rules that can be set for file name */
    public void setValidFileName(ValidNameRule str) {
        validFileName = str;
    }

    /** Rules that can be set for volume name */
    public void setValidVolumeName(ValidNameRule str) {
        validVolumeName = str;
    }

    /** Whether to ignore case during file name comparison */
    public void compareCaseInsense(boolean val) {
        compareCaseInsensitive = val;
    }

    /** Whether to convert to uppercase before displaying file name dialog */
    public void toUpperBeforeDialog(boolean val) {
        toUpperBeforeDialog = val;
    }

    /** Whether to convert to uppercase after file name dialog input */
    public void toUpperAfterRenamed(boolean val) {
        toUpperAfterRenamed = val;
    }

    /** Whether byte order is big endian */
    public void bigEndian(boolean val) {
        bigEndian = val;
    }

    /** Specific parameters */
    public void setVariousParam(String key, Object val) {
        variousParams.put(key, val);
    }

    /** Specific parameters */
    public void setVariousParams(HashMap<String, Object> val) {
        variousParams = val;
    }

    @Override
    public String toString() {
//        return new StringJoiner(", ", DiskBasicParamBase.class.getSimpleName() + "[", "]")
//                .add("sectorsPerGroup=" + sectorsPerGroup)
//                .add("groupFinalCode=" + groupFinalCode)
//                .add("groupSystemCode=" + groupSystemCode)
//                .add("groupUnusedCode=" + groupUnusedCode)
//                .add("dirTerminateCode=" + dirTerminateCode)
//                .add("dirSpaceCode=" + dirSpaceCode)
//                .add("dirTrimmingCode=" + dirTrimmingCode)
//                .add("dirStartPos=" + dirStartPos)
//                .add("dirStartPosOnRoot=" + dirStartPosOnRoot)
//                .add("dirStartPosOnSec=" + dirStartPosOnSec)
//                .add("dirStartPosOnGroup=" + dirStartPosOnGroup)
//                .add("specialAttrs=" + specialAttrs)
//                .add("attrsByExtension=" + attrsByExtension)
//                .add("fillcodeOnFormat=" + fillcodeOnFormat)
//                .add("fillcodeOnFat=" + fillcodeOnFat)
//                .add("fillcodeOnDir=" + fillcodeOnDir)
//                .add("deleteCode=" + deleteCode)
//                .add("textTerminateCode=" + textTerminateCode)
//                .add("extensionPreCode=" + extensionPreCode)
//                .add("validFileName=" + validFileName)
//                .add("validVolumeName=" + validVolumeName)
//                .add("compareCaseInsensitive=" + compareCaseInsensitive)
//                .add("toUpperBeforeDialog=" + toUpperBeforeDialog)
//                .add("toUpperAfterRenamed=" + toUpperAfterRenamed)
//                .add("bigEndian=" + bigEndian)
//                .add("variousParams=" + variousParams)
//                .toString();
        return new StringJoiner(", ", "", "")
                .add("" + sectorsPerGroup)
                .add("" + groupFinalCode)
                .add("" + groupSystemCode)
                .add("" + groupUnusedCode)
                .add("" + dirTerminateCode)
                .add("" + dirSpaceCode)
                .add("" + dirTrimmingCode)
                .add("" + dirStartPos)
                .add("" + dirStartPosOnRoot)
                .add("" + dirStartPosOnSec)
                .add("" + dirStartPosOnGroup)
                .add("" + specialAttrs)
                .add("" + attrsByExtension)
                .add("" + fillcodeOnFormat)
                .add("" + fillcodeOnFat)
                .add("" + fillcodeOnDir)
                .add("" + deleteCode)
                .add("" + textTerminateCode)
                .add("" + extensionPreCode)
                .add("" + validFileName)
                .add("" + validVolumeName)
                .add("" + compareCaseInsensitive)
                .add("" + toUpperBeforeDialog)
                .add("" + toUpperAfterRenamed)
                .add("" + bigEndian)
                .add("" + variousParams)
                .toString();
    }
}
