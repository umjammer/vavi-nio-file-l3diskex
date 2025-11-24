package l3diskex.basicfmt;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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


/** DISK BASICのパラメータを保持するクラス */
public class DiskBasicParam extends DiskBasicParamBase {

    private static final Logger logger = System.getLogger(DiskBasicParam.class.getName());

    public static class DiskBasicParamBases extends TemplatesBase {

        private boolean setVolumeRule;

        public DiskBasicParamBases() {
            setVolumeRule = false;
        }

        /** 共通パラメータ関連のロード */
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

    /** DISK BASICのフォーマットタイプ */
    public static class DiskBasicFormat extends DiskBasicParamBase {

        /** フォーマットタイプ番号 */
        private int typeNumber;
        /** ボリューム名 */
        private boolean hasVolumeName;
        /** ボリューム番号 */
        private boolean hasVolumeNumber;
        /** ボリューム日付 */
        private boolean hasVolumeDate;

        /** 初期化 */
        private void clearBasicFormatPrivate() {
            typeNumber = FORMAT_TYPE_UNKNOWN;
            hasVolumeName = false;
            hasVolumeNumber = false;
            hasVolumeDate = false;
        }

        public DiskBasicFormat() {
            clearBasicFormatPrivate();
        }

        /** 初期化 */
        public void clearBasicFormat() {
            clearBasicParamBase();
            clearBasicFormatPrivate();
        }

        /** フォーマットタイプ番号 */
        public int getTypeNumber() {
            return typeNumber;
        }

        /** ボリューム名 */
        public boolean hasVolumeName() {
            return hasVolumeName;
        }

        /** ボリューム番号 */
        public boolean hasVolumeNumber() {
            return hasVolumeNumber;
        }

        /** ボリューム日付 */
        public boolean hasVolumeDate() {
            return hasVolumeDate;
        }

//        /** ファイル名が必須か */
//        public bool isFileNameRequired() { return filenameRequire; }

        /** フォーマットタイプ番号 */
        public void setTypeNumber(int val) {
            typeNumber = val;
        }

        /** ボリューム名 */
        public void hasVolumeName(boolean val) {
            hasVolumeName = val;
        }

        /** ボリューム番号 */
        public void hasVolumeNumber(boolean val) {
            hasVolumeNumber = val;
        }

        /** ボリューム日付 */
        public void hasVolumeDate(boolean val) {
            hasVolumeDate = val;
        }

//	      /** ファイル名が必須か */
//	      public void requireFileName(bool val) { filename_require = val; }
    }

    /** DiskBasicFormat のリスト */
    public static class DiskBasicFormats extends TemplatesBase {

        List<DiskBasicFormat> list = new ArrayList<>();

        /**
         * DiskBasicFormatエレメントのロード
         *
         * @see "basicTypes.xml"
         */
        public boolean load(Node node, String localeName, StringBuilder errMsgs) {
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

        /** @param formatType フォーマット種類 */
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

    /** BASIC種類名 */
    private String basicTypeName;
    /** BASICカテゴリ名 */
    private List<String> basicCategoryNames = new ArrayList<>();
    /** フォーマット種類 */
    private DiskBasicFormat formatType;

    /** フォーマットサブタイプ番号 */
    private int formatSubtypeNumber;
    /** BASICが使用するサイド数 */
    private int sidesOnBasic;
    /** BASICで使用するセクタ数/トラック */
    private int sectorsOnBasic;
    /** BASICで使用するセクタ数/トラック */
    private List<NumSectorsParam> sectorsOnBasicList = new ArrayList<>();
    /** BASICで使用するセクタ番号基準 */
    private int sectorNumberBase;
    /** BASICで使用するトラック数/サイド */
    private int tracksOnBasic;
    /** ファイル管理エリア */
    private int managedTrackNumber;
    /** トラック当たりのグループ数 */
    private int groupsPerTrack;
    /** セクタ当たりのグループ数 */
    private int groupsPerSector;
    /** 予約済みセクタ数 */
    private int reservedSectors;
    /** ファイル管理エリアの数 */
    private int numberOfFats;
    /** 有効・使用しているファイル管理エリアの数 */
    private int validNumberOfFats;
    /** FAT領域のセクタ数 */
    private int sectorsPerFat;
    /** FAT開始位置（バイト） */
    private int fatStartPos;
    /** FAT最大グループ番号 */
    private int fatEndGroup;
    /** FAT領域のあるサイド番号 */
    private int fatSideNumber;
    /** 予約済みグループ */
    private List<Integer> reservedGroups = new ArrayList<>();
    /** ルートディレクトリ開始セクタ */
    private int dirStartSector;
    /** ルートディレクトリ終了セクタ */
    private int dirEndSector;
    /** ルートディレクトリエントリ数 */
    private int dirEntryCount;
    /** サブディレクトリの初期グループ数 */
    private int subdirGroupSize;
    /** グループ幅（バイト） */
    private int groupWidth;
    /** １ディレクトリエントリで指定できるグループ数 */
    private int groupsPerDirEntry;
    /** 有効な密度 0:倍密度 1:単密度 */
    private int validDensityType;
    /** ソフトウェアセクタスキュー(セクタ間隔) */
    private SectorInterleave sectorSkew = new SectorInterleave();
    /** メディアID */
    private byte mediaId;
    /** データビットが反転してるか */
    private boolean dataInverted;
    /** サイドが反転してるか */
    private boolean sideReversed;
    /** 片面のみ使用するOSで各面ごとに独立してアクセスできるか */
    private boolean mountEachSides;
    /** 説明 */
    private String basicDescription;

    /** 初期化 */
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

    /** 初期化 */
    public void clearBasicParam() {
        clearBasicParamBase();
        clearBasicParamPrivate();
    }

    /** 設定 */
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

    /** 開始終了セクタを計算 */
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

    /** BASIC種類名 */
    public String getBasicTypeName() {
        return basicTypeName;
    }

    /** BASICカテゴリ名 */
    public List<String> getBasicCategoryNames() {
        return basicCategoryNames;
    }

    /** BASICカテゴリ名 */
    public List<String> getBasicCategoryNamesForMod() {
        return basicCategoryNames; // Changed name
    }

    /** BASIC種類 */
    public DiskBasicFormat getFormatType() {
        return formatType;
    }

    /** サブタイプ番号 */
    public int getFormatSubTypeNumber() {
        return formatSubtypeNumber;
    }

    /** BASICが使用するサイド数 */
    public int getSidesPerDiskOnBasic() {
        return sidesOnBasic;
    }

    /** BASIC種類 */
    public int getSectorsPerTrackOnBasic() {
        return sectorsOnBasic;
    }

    /** BASICで使用するセクタ番号基準 */
    public int getSectorNumberBaseOnBasic() {
        return sectorNumberBase;
    }

    /** BASICで使用するトラック数/サイド */
    public int getTracksPerSideOnBasic() {
        return tracksOnBasic;
    }

    /** ファイル管理エリアのあるトラック番号 */
    public int getManagedTrackNumber() {
        return managedTrackNumber;
    }

    /** トラック当たりのグループ数 */
    public int getGroupsPerTrack() {
        return groupsPerTrack;
    }

    /** セクタ当たりのグループ数 */
    public int getGroupsPerSector() {
        return groupsPerSector;
    }

    /** 予約済みセクタ数 */
    public int getReservedSectors() {
        return reservedSectors;
    }

    /** ファイル管理エリアの数 */
    public int getNumberOfFats() {
        return numberOfFats;
    }

    /** 有効・使用しているファイル管理エリアの数 */
    public int getValidNumberOfFats() {
        return validNumberOfFats;
    }

    /** FAT領域のセクタ数 */
    public int getSectorsPerFat() {
        return sectorsPerFat;
    }

    /** FAT開始セクタ */
    public int getFatStartSector() {
        return (reservedSectors + 1);
    }

    /** FAT開始位置（バイト） */
    public int getFatStartPos() {
        return fatStartPos;
    }

    /** FAT最大グループ番号 */
    public int getFatEndGroup() {
        return fatEndGroup;
    }

    /** FAT領域のあるサイド番号 */
    public int getFatSideNumber() {
        return fatSideNumber;
    }

    /** 予約済みグループ番号 */
    public List<Integer> getReservedGroups() {
        return reservedGroups;
    }

    /** ルートディレクトリ開始セクタ */
    public int getDirStartSector() {
        return dirStartSector;
    }

    /** ルートディレクトリ終了セクタ */
    public int getDirEndSector() {
        return dirEndSector;
    }

    /** ルートディレクトリエントリ数 */
    public int getDirEntryCount() {
        return dirEntryCount;
    }

    /** サブディレクトリの初期グループ数 */
    public int getSubDirGroupSize() {
        return subdirGroupSize;
    }

    /** グループ幅（バイト） */
    public int getGroupWidth() {
        return groupWidth;
    }

    /** １ディレクトリエントリで指定できるグループ数 */
    public int getGroupsPerDirEntry() {
        return groupsPerDirEntry;
    }

    /** 有効な密度 */
    public int getValidDensityType() {
        return validDensityType;
    }

    /** ソフトウェアセクタスキュー(セクタ間隔) */
    public int getSectorSkew() {
        return sectorSkew.get();
    }

    /** ソフトウェアセクタスキュー(セクタ間隔) 固有のマップ */
    public int getSectorSkewMap(int idx) {
        return sectorSkew.get(idx);
    }

    /** ソフトウェアセクタスキュー(セクタ間隔) 固有のマップを持っているか */
    public boolean hasSectorSkewMap() {
        return sectorSkew.hasMap();
    }

    /** メディアID */
    public byte getMediaId() {
        return mediaId;
    }

//    /** ファイル名が必須か */
//    public boolean isFileNameRequired() { return filenameRequire; }

    /** データビットが反転してるか */
    public boolean isDataInverted() {
        return dataInverted;
    }

    /** サイドが反転してるか */
    public boolean isSideReversed() {
        return sideReversed;
    }

    /** 反転したサイド番号を返す */
    public int getReversedSideNumber(int side_num) {
        return (sideReversed && 0 <= side_num && side_num < sidesOnBasic ? sidesOnBasic - side_num - 1 : side_num);
    }

    /** 片面のみ使用するOSで各面ごとに独立してアクセスできるか */
    public boolean canMountEachSides() {
        return (mountEachSides && sidesOnBasic == 1);
    }

    /** 説明 */
    public String getBasicDescription() {
        return basicDescription;
    }

    /** BASIC種類名 */
    public void setBasicTypeName(String str) {
        basicTypeName = str;
    }

    /** BASICカテゴリ名 */
    public void setBasicCategoryNames(ArrayList<String> arr) {
        basicCategoryNames = arr;
    }

    /** BASIC種類 */
    public void setFormatType(DiskBasicFormat val) {
        formatType = val;
    }

    /** サブタイプ番号 */
    public void setFormatSubTypeNumber(int val) {
        formatSubtypeNumber = val;
    }

    /** BASICが使用するサイド数 */
    public void setSidesPerDiskOnBasic(int val) {
        sidesOnBasic = val;
    }

    /** BASICで使用するセクタ数/トラック */
    public void setSectorsPerTrackOnBasic(int val) {
        sectorsOnBasic = val;
    }

    /** BASICで使用するセクタ番号基準 */
    public void setSectorNumberBaseOnBasic(int val) {
        sectorNumberBase = val;
    }

    /** BASICで使用するトラック数/サイド */
    public void setTracksPerSideOnBasic(int val) {
        tracksOnBasic = val;
    }

    /** ファイル管理エリアのあるトラック番号 */
    public void setManagedTrackNumber(int val) {
        managedTrackNumber = val;
    }

    /** トラック当たりのグループ数 */
    public void setGroupsPerTrack(int val) {
        groupsPerTrack = val;
    }

    /** セクタ当たりのグループ数 */
    public void setGroupsPerSector(int val) {
        groupsPerSector = val;
    }

    /** 予約済みセクタ数 */
    public void setReservedSectors(int val) {
        reservedSectors = val;
    }

    /** ファイル管理エリアの数 */
    public void setNumberOfFats(int val) {
        numberOfFats = val;
    }

    /** 有効・使用しているファイル管理エリアの数 */
    public void setValidNumberOfFats(int val) {
        validNumberOfFats = val;
    }

    /** FAT領域のセクタ数 */
    public void setSectorsPerFat(int val) {
        sectorsPerFat = val;
    }

    /** FAT開始位置（バイト） */
    public void setFatStartPos(int val) {
        fatStartPos = val;
    }

    /** FAT最大グループ番号 */
    public void setFatEndGroup(int val) {
        fatEndGroup = val;
    }

    /** FAT領域のあるサイド番号 */
    public void setFatSideNumber(int val) {
        fatSideNumber = val;
    }

    /** 予約済みグループ番号 */
    public void setReservedGroups(List<Integer> arr) {
        reservedGroups = arr;
    }

    /** ルートディレクトリ開始セクタ */
    public void setDirStartSector(int val) {
        dirStartSector = val;
    }

    /** グループ幅（バイト） */
    public void setGroupWidth(int val) {
        groupWidth = val;
    }

    /** １ディレクトリエントリで指定できるグループ数 */
    public void setGroupsPerDirEntry(int val) {
        groupsPerDirEntry = val;
    }

    /** 有効な密度 */
    public void setValidDensityType(int val) {
        validDensityType = val;
    }

    /** ソフトウェアセクタスキュー(セクタ間隔) */
    public void setSectorSkew(int val) {
        sectorSkew.set(val);
    }

    /** ソフトウェアセクタスキュー(セクタ間隔) */
    public void setSectorSkewMap(List<Integer> arr) {
        sectorSkew.set(arr);
    }

    /** ルートディレクトリ終了セクタ */
    public void setDirEndSector(int val) {
        dirEndSector = val;
    }

    /** ルートディレクトリエントリ数 */
    public void setDirEntryCount(int val) {
        dirEntryCount = val;
    }

    /** サブディレクトリの初期グループ数 */
    public void setSubDirGroupSize(int val) {
        subdirGroupSize = val;
    }

    /** メディアID */
    public void setMediaId(byte val) {
        mediaId = val;
    }

//    /** ファイル名が必須か */
//    public void requireFileName(bool val) { filename_require = val; }

    /** データビットが反転してるか */
    public void dataInverted(boolean val) {
        dataInverted = val;
    }

    /** サイドが反転してるか */
    public void sideReversed(boolean val) {
        sideReversed = val;
    }

    /** 片面のみ使用するOSで各面ごとに独立してアクセスできるか */
    public void mountEachSides(boolean val) {
        mountEachSides = val;
    }

    /** 説明 */
    public void setBasicDescription(String str) {
        basicDescription = str;
    }

    /** BASICで使用するセクタ数/トラック */
    public List<NumSectorsParam> sectorsPerTrackOnBasicList() {
        return sectorsOnBasicList;
    }

    /** BASICカテゴリ名を追加 */
    public void addBasicCategoryName(String str) {
        if (!basicCategoryNames.contains(str)) {
            basicCategoryNames.add(str);
        }
    }

    /** BASICカテゴリ名を返す */
    public String getBasicCategoryName() {
        if (basicCategoryNames.isEmpty()) {
            return "";
        } else {
            return basicCategoryNames.getFirst();
        }
    }

    /** BASICカテゴリ名が存在するか */
    public boolean findBasicCategoryName(String str) {
        return (basicCategoryNames.contains(str));
    }

    /** ReservedGroupsエレメントをロード */
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

    /** SectorSkewMapエレメントをロード */
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

    /** SectorsPerTrackエレメントをロード */
    public boolean loadNumSectorsMap(Node node, String val) {
        // sec_param  セクタ数/トラック(全トラック同じの場合)
        int secParam = getSectorsPerTrackOnBasic();
        // sec_params セクタ数/トラック(トラック毎に異なる場合)
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

    /** Categoriesエレメントのロード */
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

    /** 説明文でソート */
    public static int sortByDescription(DiskBasicParam item1, DiskBasicParam item2) {
        return item1.getBasicDescription().compareTo(item2.getBasicDescription());
    }

    /** DiskBasicParam のリスト */
    public static class DiskBasicParams extends TemplatesBase {

        List<DiskBasicParam> list = new ArrayList<>();

        /**
         * DiskBasicTypeエレメントのロード
         *
         * @see "basicTypes.xml"
         */
        public boolean load(Node node, String localeName, DiskBasicFormats formats, StringBuilder errMsgs) {
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
                        // フォーマットパラメータを初期値とする
                        p.setFormatType(formatType);
                        //p.RequireFileName(formatType.isFileNameRequired());
                        p.setBasicParamBase(formatType);
                    } else {
                        // フォーマットタイプがない
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
                    } else {
                        // タイプ名が重複している
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
         * カテゴリとタイプに一致するパラメータを検索
         *
         * @param category  カテゴリ名 空文字列の場合は検索条件からはずす
         * @param basicType タイプ名
         * @return 一致したパラメータ
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
         * カテゴリが一致し、タイプリストに含まれるパラメータを検索
         *
         * @param category   カテゴリ名 空文字列の場合は検索条件からはずす
         * @param basicTypes タイプ名リスト
         * @return 一致したパラメータ
         */
        public DiskBasicParam find(String category, List<DiskParamName> basicTypes) {
            DiskBasicParam matchItem = null;
            for (int i = 0; i < basicTypes.size() && matchItem == null; i++) {
                matchItem = find(category, basicTypes.get(i).getName());
            }
            return matchItem;
        }

        /**
         * カテゴリ、タイプ、サイド数とセクタ数が一致するパラメータを検索
         * まず、カテゴリ＆タイプで検索し、なければカテゴリ＆サイド数＆セクタ数で検索
         *
         * @param category  カテゴリ名 必須
         * @param basicType タイプ名 必須
         * @param sides     サイド数
         * @param sectors   セクタ数/トラック -1の場合は検索条件からはずす
         * @return 一致したパラメータ
         */
        public DiskBasicParam find(String category, String basicType, int sides, int sectors) {
            DiskBasicParam match_item = null;
            // カテゴリ、タイプで一致するか
            match_item = find(category, basicType);
            // カテゴリ、サイド数、セクタ数で一致するか
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
         * DISK BASICフォーマット種類に一致するタイプを検索
         *
         * @param formatTypes DISK BASICフォーマット種類
         * @param types       [out] 一致したタイプリスト
         * @return リストの数
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
         * カテゴリ名に一致するタイプ名リストを検索
         *
         * @param categoryName カテゴリ名
         * @param typeNames    [out] タイプ名リスト
         * @return リストの数
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
}

/** DISK BASICの共通パラメータ */
class DiskBasicParamBase {

    private static final Logger logger = System.getLogger(DiskBasicParamBase.class.getName());

    /** グループ(クラスタ)サイズ */
    protected int sectorsPerGroup;
    /** 最終グループのコード(0xc0 - ) */
    protected int groupFinalCode;
    /** システムで使用するコード(0xfe) */
    protected int groupSystemCode;
    /** 未使用のコード(0xff) */
    protected int groupUnusedCode;
    /** ディレクトリ名の終端コード */
    protected byte dirTerminateCode;
    /** ディレクトリ名の空白コード */
    protected byte dirSpaceCode;
    /** ディレクトリ名の空白コード（とり除くコード） */
    protected byte dirTrimmingCode;
    /** サブディレクトリの開始位置（バイト） */
    protected int dirStartPos;
    /** ルートディレクトリの開始位置（バイト） */
    protected int dirStartPosOnRoot;
    /** ディレクトリのセクタ毎の開始位置 */
    protected int dirStartPosOnSec;
    /** ディレクトリのグループ毎の開始位置 */
    protected int dirStartPosOnGroup;
    /** 特別な属性 */
    protected List<MyAttribute> specialAttrs = new ArrayList<>();
    /** 拡張子と属性の関係 */
    protected List<MyAttribute> attrsByExtension = new ArrayList<>();
    /** フォーマット時に埋めるコード */
    protected byte fillcodeOnFormat;
    /** フォーマット時にFAT領域を埋めるコード */
    protected byte fillcodeOnFat;
    /** フォーマット時にディレクトリ領域を埋めるコード */
    protected byte fillcodeOnDir;
    /** ファイル削除時にセットするコード */
    protected byte deleteCode;
    /** テキストの終端コード */
    protected byte textTerminateCode;
    /** ファイル名と拡張子の間に付けるコード('.') */
    protected byte extensionPreCode;
    /** ファイル名に設定できるルール */
    protected ValidNameRule validFileName = new ValidNameRule();
    /** ボリューム名に設定できるルール */
    protected ValidNameRule validVolumeName = new ValidNameRule();
    /** ファイル名比較時に大文字小文字区別しないか */
    protected boolean compareCaseInsensitive;
    /** ファイル名ダイアログ表示前に大文字に変換するか */
    protected boolean toUpperBeforeDialog;
    /** ファイル名ダイアログ入力後に大文字に変換するか */
    protected boolean toUpperAfterRenamed;
    /** バイトオーダ ビッグエンディアンか */
    protected boolean bigEndian;
    /** その他固有のパラメータ */
    protected Map<String, Object> variousParams = new HashMap<>();

    /** 初期化 */
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

    /** 設定 */
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

    /** グループ(クラスタ)サイズ */
    public int getSectorsPerGroup() {
        return sectorsPerGroup;
    }

    /** 最終グループのコード */
    public int getGroupFinalCode() {
        return groupFinalCode;
    }

    /** システムで使用するコード */
    public int getGroupSystemCode() {
        return groupSystemCode;
    }

    /** 未使用のコード */
    public int getGroupUnusedCode() {
        return groupUnusedCode;
    }

    /** ディレクトリ名の終端コード */
    public byte getDirTerminateCode() {
        return dirTerminateCode;
    }

    /** ディレクトリ名の空白コード */
    public byte getDirSpaceCode() {
        return dirSpaceCode;
    }

    /** ディレクトリ名の空白コード（とり除くコード） */
    public byte getDirTrimmingCode() {
        return dirTrimmingCode;
    }

    /** ディレクトリの開始位置（バイト） */
    public int getDirStartPos() {
        return dirStartPos;
    }

    /** ルートディレクトリの開始位置（バイト） */
    public int getDirStartPosOnRoot() {
        return dirStartPosOnRoot;
    }

    /** ディレクトリのセクタ毎の開始位置 */
    public int getDirStartPosOnSector() {
        return dirStartPosOnSec;
    }

    /** ディレクトリのグループ毎の開始位置 */
    public int getDirStartPosOnGroup() {
        return dirStartPosOnGroup;
    }

    /** 特別な属性 */
    public List<MyAttribute> getSpecialAttributes() {
        return specialAttrs;
    }

    /** 拡張子と属性の関係 */
    public List<MyAttribute> getAttributesByExtension() {
        return attrsByExtension;
    }

    /** フォーマット時に埋めるコード */
    public byte getFillCodeOnFormat() {
        return fillcodeOnFormat;
    }

    /** フォーマット時にFAT領域を埋めるコード */
    public byte getFillCodeOnFAT() {
        return fillcodeOnFat;
    }

    /** フォーマット時にディレクトリ領域を埋めるコード */
    public byte getFillCodeOnDir() {
        return fillcodeOnDir;
    }

    /** ファイル削除時にセットするコード */
    public byte getDeleteCode() {
        return deleteCode;
    }

    /** テキストの終端コード */
    public byte getTextTerminateCode() {
        return textTerminateCode;
    }

    /** ファイル名と拡張子の間に付けるコード(' . ') */
    public byte getExtensionPreCode() {
        return extensionPreCode;
    }

    /** ファイル名に設定できるルール */
    public ValidNameRule getValidFileName() {
        return validFileName;
    }

    /** ファイル名に設定できるルール */
    public ValidNameRule getValidFileNameForMod() {
        return validFileName;
    } // Changed name to avoid conflict with final version

    /** ボリューム名に設定できるルール */
    public ValidNameRule getValidVolumeName() {
        return validVolumeName;
    }

    /** ボリューム名に設定できるルール */
    public ValidNameRule getValidVolumeNameForMod() {
        return validVolumeName;
    } // Changed name

    /** ファイル名比較時に大文字小文字区別しないか */
    public boolean isCompareCaseInsensitive() {
        return compareCaseInsensitive;
    }

    /** ファイル名ダイアログ表示前に大文字に変換するか */
    public boolean toUpperBeforeDialog() {
        return toUpperBeforeDialog;
    }

    /** ファイル名ダイアログ入力後に大文字に変換するか */
    public boolean toUpperAfterRenamed() {
        return toUpperAfterRenamed;
    }

    /** バイトオーダ ビッグエンディアンか */
    public boolean isBigEndian() {
        return bigEndian;
    }

    /** 固有のパラメータ */
    public Map<String, Object> getVariousParams() {
        return variousParams;
    }

    /** 固有のパラメータ */
    public void getVariousParam(String key, Object[] val) {
        Object value = variousParams.get(key);
        if (value != null) {
            val[0] = value;
        }
    }

    /** 固有のパラメータ */
    public int getVariousIntegerParam(String key) {
        Object value = variousParams.get(key);
logger.log(Level.TRACE, "key: " + key + ", value: " + value);
        if (value != null) {
            return (int) value;
        } else {
            return 0;
        }
    }

    /** 固有のパラメータ */
    public boolean getVariousBoolParam(String key) {
        Object value = variousParams.get(key);
        if (value != null) {
            return (boolean) value;
        } else {
            return false;
        }
    }

    /** 固有のパラメータ */
    public String getVariousStringParam(String key) {
        Object value = variousParams.get(key);
        if (value != null) {
            return (String) value;
        } else {
            return "";
        }
    }

    /** グループ(クラスタ)サイズ */
    public void setSectorsPerGroup(int val) {
        sectorsPerGroup = val;
    }

    /** 最終グループのコード */
    public void setGroupFinalCode(int val) {
        groupFinalCode = val;
    }

    /** システムで使用するコード */
    public void setGroupSystemCode(int val) {
        groupSystemCode = val;
    }

    /** 未使用のコード */
    public void setGroupUnusedCode(int val) {
        groupUnusedCode = val;
    }

    /** ディレクトリ名の終端コード */
    public void setDirTerminateCode(byte val) {
        dirTerminateCode = val;
    }

    /** ディレクトリ名の空白コード */
    public void setDirSpaceCode(byte val) {
        dirSpaceCode = val;
    }

    /** ディレクトリ名の空白コード（とり除くコード） */
    public void setDirTrimmingCode(byte val) {
        dirTrimmingCode = val;
    }

    /** ディレクトリの開始位置（バイト） */
    public void setDirStartPos(int val) {
        dirStartPos = val;
    }

    /** ルートディレクトリの開始位置（バイト） */
    public void setDirStartPosOnRoot(int val) {
        dirStartPosOnRoot = val;
    }

    /** ディレクトリのセクタ毎の開始位置 */
    public void setDirStartPosOnSector(int val) {
        dirStartPosOnSec = val;
    }

    /** ディレクトリのグループ毎の開始位置 */
    public void setDirStartPosOnGroup(int val) {
        dirStartPosOnGroup = val;
    }

    /** 特別な属性 */
    public void setSpecialAttributes(List<MyAttribute> arr) {
        specialAttrs = arr;
    }

    /** 拡張子と属性の関係 */
    public void setAttributesByExtension(List<MyAttribute> arr) {
        attrsByExtension = arr;
    }

    /** フォーマット時に埋めるコード */
    public void setFillCodeOnFormat(byte val) {
        fillcodeOnFormat = val;
    }

    /** フォーマット時にFAT領域を埋めるコード */
    public void setFillCodeOnFAT(byte val) {
        fillcodeOnFat = val;
    }

    /** フォーマット時にディレクトリ領域を埋めるコード */
    public void setFillCodeOnDir(byte val) {
        fillcodeOnDir = val;
    }

    /** ファイル削除時にセットするコード */
    public void setDeleteCode(byte val) {
        deleteCode = val;
    }

    /** テキストの終端コード */
    public void setTextTerminateCode(byte val) {
        textTerminateCode = val;
    }

    /** ファイル名と拡張子の間に付けるコード(' . ') */
    public void setExtensionPreCode(byte val) {
        extensionPreCode = val;
    }

    /** ファイル名に設定できるルール */
    public void setValidFileName(ValidNameRule str) {
        validFileName = str;
    }

    /** ボリューム名に設定できるルール */
    public void setValidVolumeName(ValidNameRule str) {
        validVolumeName = str;
    }

    /** ファイル名比較時に大文字小文字区別しないか */
    public void compareCaseInsense(boolean val) {
        compareCaseInsensitive = val;
    }

    /** ファイル名ダイアログ表示前に大文字に変換するか */
    public void toUpperBeforeDialog(boolean val) {
        toUpperBeforeDialog = val;
    }

    /** ファイル名ダイアログ入力後に大文字に変換するか */
    public void toUpperAfterRenamed(boolean val) {
        toUpperAfterRenamed = val;
    }

    /** バイトオーダ ビッグエンディアンか */
    public void bigEndian(boolean val) {
        bigEndian = val;
    }

    /** 固有のパラメータ */
    public void setVariousParam(String key, Object val) {
        variousParams.put(key, val);
    }

    /** 固有のパラメータ */
    public void setVariousParams(HashMap<String, Object> val) {
        variousParams = val;
    }
}
