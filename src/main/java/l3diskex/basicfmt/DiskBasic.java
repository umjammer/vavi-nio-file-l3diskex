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

import l3diskex.CharCodes;
import l3diskex.Common;
import l3diskex.Parambase.MyAttribute;
import l3diskex.ResultInfo;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicParams;
import l3diskex.basicfmt.type.*;
import l3diskex.basicfmt.type.DiskBasicTypeTRSDOS.DiskBasicTypeTRSD13;
import l3diskex.basicfmt.type.DiskBasicTypeTRSDOS.DiskBasicTypeTRSD23;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import vavi.io.SeekableDataInputStream;
import vavi.io.SeekableDataOutputStream;

import static java.lang.System.getLogger;
import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.DiskBasicDirItem.DATETIME_ACCESS;
import static l3diskex.basicfmt.DiskBasicDirItem.DATETIME_CREATE;
import static l3diskex.basicfmt.DiskBasicDirItem.DATETIME_MODIFY;
import static l3diskex.basicfmt.DiskBasicTemplates.gDiskBasicTemplates;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_NEW;


/** DISK BASIC Structure analysis */
public class DiskBasic extends DiskParam {

    private static final Logger logger = getLogger(DiskBasic.class.getName());

    /** Formatter, dialogs */
    public static class DiskBasicIdentifiedData {

        /** ボリューム名 */
        private String volumeName;
        /** ボリューム名最大長 */
        private int volumeNameMaxLen;
        /** ボリューム番号 */
        private int volumeNumber;
        /** ボリューム番号が16進か */
        private boolean volumeNumberHex;
        /** ボリューム日付 */
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

    /** サイド(1S用) */
    private int selectedSide;

    /** グループ計算用 データ開始セクタ番号 */
    private int dataStartSector;
    /** グループ計算する際にスキップするトラック番号 */
    private int skippedTrack;

    /** ファイルなどの文字コード体系 */
    private String charCode;
    /** 文字コード変換 */
    private final CharCodes codes;

    /** エラー情報保存用 */
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
        dir = new DiskBasicDir(this);
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

    /** BASIC種類 */
    public DiskBasicFormat getFormatType() {
        return diskBasicParam.getFormatType();
    }

    /** BASIC種類 */
    public int getSectorsPerTrackOnBasic() {
        return diskBasicParam.getSectorsPerTrackOnBasic();
    }

    /** BASICで使用するセクタ数/トラック */
    public void setSectorsPerTrackOnBasic(int val) {
        diskBasicParam.setSectorsPerTrackOnBasic(val);
    }

    /** BASICで使用するトラック数/サイド */
    public int getTracksPerSideOnBasic() {
        return diskBasicParam.getTracksPerSideOnBasic();
    }

    /** BASICで使用するトラック数/サイド */
    public void setTracksPerSideOnBasic(int val) {
        diskBasicParam.setTracksPerSideOnBasic(val);
    }

    /** BASICが使用するサイド数 */
    public int getSidesPerDiskOnBasic() {
        return diskBasicParam.getSidesPerDiskOnBasic();
    }

    /** BASICが使用するサイド数 */
    public void setSidesPerDiskOnBasic(int val) {
        diskBasicParam.setSidesPerDiskOnBasic(val);
    }

    /** BASIC種類名 */
    public String getBasicTypeName() {
        return diskBasicParam.getBasicTypeName();
    }

    /** 説明 */
    public String getBasicDescription() {
        return diskBasicParam.getBasicDescription();
    }

    /** 開始終了セクタを計算 */
    public void calcDirStartEndSector(int sectorSize) {
        diskBasicParam.calcDirStartEndSector(sectorSize);
    }

    /** ファイル管理エリアのあるトラック番号 */
    public int getManagedTrackNumber() {
        return diskBasicParam.getManagedTrackNumber();
    }

    /** ファイル名比較時に大文字小文字区別しないか */
    public boolean isCompareCaseInsensitive() {
        return diskBasicParam.isCompareCaseInsensitive();
    }

    /** グループ(クラスタ)サイズ */
    public int getSectorsPerGroup() {
        return diskBasicParam.getSectorsPerGroup();
    }

    /** サブディレクトリの初期グループ数 */
    public int getSubDirGroupSize() {
        return diskBasicParam.getSubDirGroupSize();
    }

    /** FAT最大グループ番号 */
    public int getFatEndGroup() {
        return diskBasicParam.getFatEndGroup();
    }

    /** BASICで使用するセクタ番号基準 */
    public int getSectorNumberBaseOnBasic() {
        return diskBasicParam.getSectorNumberBaseOnBasic();
    }

    /** 有効な密度 */
    public int getValidDensityType() {
        return diskBasicParam.getValidDensityType();
    }

    /** 反転したサイド番号を返す */
    public int getReversedSideNumber(int sideNum) {
        return diskBasicParam.getReversedSideNumber(sideNum);
    }

    /** データビットが反転してるか */
    public boolean isDataInverted() {
        return diskBasicParam.isDataInverted();
    }

    /** バイトオーダ ビッグエンディアンか */
    public boolean isBigEndian() {
        return diskBasicParam.isBigEndian();
    }

    /** 片面のみ使用するOSで各面ごとに独立してアクセスできるか */
    public boolean canMountEachSides() {
        return diskBasicParam.canMountEachSides();
    }

    /** 最終グループのコード */
    public int getGroupFinalCode() {
        return diskBasicParam.getGroupFinalCode();
    }

    /** 未使用のコード */
    public int getGroupUnusedCode() {
        return diskBasicParam.getGroupUnusedCode();
    }

    /** システムで使用するコード */
    public int getGroupSystemCode() {
        return diskBasicParam.getGroupSystemCode();
    }

    /** 固有のパラメータ */
    public int getVariousIntegerParam(String key) {
        return diskBasicParam.getVariousIntegerParam(key);
    }

    /** ルートディレクトリ開始セクタ */
    public int getDirStartSector() {
        return diskBasicParam.getDirStartSector();
    }

    /** FAT最大グループ番号 */
    public void setFatEndGroup(int end) {
        diskBasicParam.setFatEndGroup(end);
    }

    /** ルートディレクトリ終了セクタ */
    public int getDirEndSector() {
        return diskBasicParam.getDirEndSector();
    }

    /** ファイル名と拡張子の間に付けるコード(' . ') */
    public byte getExtensionPreCode() {
        return diskBasicParam.getExtensionPreCode();
    }

    /** トラック当たりのグループ数 */
    public int getGroupsPerTrack() {
        return diskBasicParam.getGroupsPerTrack();
    }

    /** ファイル名ダイアログ入力後に大文字に変換するか */
    public boolean toUpperAfterRenamed() {
        return diskBasicParam.toUpperAfterRenamed();
    }

    /** ファイル名ダイアログ表示前に大文字に変換するか */
    public boolean toUpperBeforeDialog() {
        return diskBasicParam.toUpperBeforeDialog();
    }

    /** 拡張子と属性の関係 */
    public List<MyAttribute> getAttributesByExtension() {
        return diskBasicParam.getAttributesByExtension();
    }

    /** 特別な属性 */
    public List<MyAttribute> getSpecialAttributes() {
        return diskBasicParam.getSpecialAttributes();
    }

    /** ディレクトリエントリの空き領域に埋めるコード */
    public byte getDirSpaceCode() {
        return diskBasicParam.getDirSpaceCode();
    }

    /** ディレクトリエントリの終端コード */
    public byte getDirTerminateCode() {
        return diskBasicParam.getDirTerminateCode();
    }

    /** ディレクトリエントリの未使用領域に埋めるコード */
    public byte getDirTrimmingCode() {
        return diskBasicParam.getDirTrimmingCode();
    }

    /** テキストファイルの終端コード */
    public byte getTextTerminateCode() {
        return diskBasicParam.getTextTerminateCode();
    }

    /** ディレクトリエントリの開始位置(セクタ) */
    public int getDirStartPosOnSector() {
        return diskBasicParam.getDirStartPosOnSector();
    }

    /** ディレクトリエントリの開始位置(グループ) */
    public int getDirStartPosOnGroup() {
        return diskBasicParam.getDirStartPosOnGroup();
    }

    /** ファイル削除時にセットするコード */
    public byte getDeleteCode() {
        return diskBasicParam.getDeleteCode();
    }

    /** FAT開始セクタ */
    public int getFatStartSector() {
        return diskBasicParam.getFatStartSector();
    }

    /** FATのあるサイド番号 */
    public int getFatSideNumber() {
        return diskBasicParam.getFatSideNumber();
    }

    /** 予約セクタ数 */
    public int getReservedSectors() {
        return diskBasicParam.getReservedSectors();
    }

    /** FATの数 */
    public int getNumberOfFats() {
        return diskBasicParam.getNumberOfFats();
    }

    /** FATが使用するセクタ数 */
    public int getSectorsPerFat() {
        return diskBasicParam.getSectorsPerFat();
    }

    /** FATの開始位置(セクタ内) */
    public int getFatStartPos() {
        return diskBasicParam.getFatStartPos();
    }

    /** FATの有効数 */
    public int getValidNumberOfFats() {
        return diskBasicParam.getValidNumberOfFats();
    }

    /** セクタスキューマップ */
    public int getSectorSkewMap(int idx) {
        return diskBasicParam.getSectorSkewMap(idx);
    }

    /** セクタスキューマップを持つか */
    public boolean hasSectorSkewMap() {
        return diskBasicParam.hasSectorSkewMap();
    }

    /** セクタスキュー */
    public int getSectorSkew() {
        return diskBasicParam.getSectorSkew();
    }

    /** BASICで使用するセクタ数/トラック(可変長) */
    public List<NumSectorsParam> sectorsPerTrackOnBasicList() {
        return diskBasicParam.sectorsPerTrackOnBasicList();
    }

    /** ルートディレクトリの開始位置 */
    public int getDirStartPosOnRoot() {
        return diskBasicParam.getDirStartPosOnRoot();
    }

    /** ディレクトリの開始位置 */
    public int getDirStartPos() {
        return diskBasicParam.getDirStartPos();
    }

    /** フォーマット時に埋めるコード */
    public byte getFillCodeOnFormat() {
        return diskBasicParam.getFillCodeOnFormat();
    }

    /** トラック当たりのグループ数 */
    public void setGroupsPerTrack(int val) {
        diskBasicParam.setGroupsPerTrack(val);
    }

    /** FATが使用するセクタ数 */
    public void setSectorsPerFat(int val) {
        diskBasicParam.setSectorsPerFat(val);
    }

    /** FAT領域を埋めるコード */
    public byte getFillCodeOnFAT() {
        return diskBasicParam.getFillCodeOnFAT();
    }

    /** DIR領域を埋めるコード */
    public byte getFillCodeOnDir() {
        return diskBasicParam.getFillCodeOnDir();
    }

    /** FATのグループ番号を保持するビット幅(12 or 16) */
    public int getGroupWidth() {
        return diskBasicParam.getGroupWidth();
    }

    /** ディレクトリエントリが使用するグループ数 */
    public int getGroupsPerDirEntry() {
        return diskBasicParam.getGroupsPerDirEntry();
    }

    /** 予約済みグループ番号 */
    public List<Integer> getReservedGroups() {
        return diskBasicParam.getReservedGroups();
    }

    /** ルートディレクトリ開始セクタ */
    public void setDirStartSector(int val) {
        diskBasicParam.setDirStartSector(val);
    }

    /** ルートディレクトリ終了セクタ */
    public void setDirEndSector(int val) {
        diskBasicParam.setDirEndSector(val);
    }

    /** FATのグループ番号を保持するビット幅(12 or 16) */
    public void setGroupWidth(int val) {
        diskBasicParam.setGroupWidth(val);
    }

    /** セクタ当たりのグループ数 */
    public int getGroupsPerSector() {
        return diskBasicParam.getGroupsPerSector();
    }

    /** 固有のパラメータ */
    public String getVariousStringParam(String key) {
        return diskBasicParam.getVariousStringParam(key);
    }

    /** BASIC種類サブ番号 */
    public int getFormatSubTypeNumber() {
        return diskBasicParam.getFormatSubTypeNumber();
    }

    /** グループ(クラスタ)サイズ */
    public void setSectorsPerGroup(int val) {
        diskBasicParam.setSectorsPerGroup(val);
    }

    /** ファイル管理エリアのあるトラック番号 */
    public void setManagedTrackNumber(int val) {
        diskBasicParam.setManagedTrackNumber(val);
    }

    /** セクタ当たりのグループ数 */
    public void setGroupsPerSector(int val) {
        diskBasicParam.setGroupsPerSector(val);
    }

    /** 固有のパラメータ */
    public boolean getVariousBoolParam(String key) {
        return diskBasicParam.getVariousBoolParam(key);
    }

    /** 固有のパラメータ */
    public void setVariousParam(String key, boolean val) {
        diskBasicParam.setVariousParam(key, val);
    }

    /** メディアID */
    public byte getMediaId() {
        return diskBasicParam.getMediaId();
    }

    /** 予約セクタ数 */
    public void setReservedSectors(int val) {
        diskBasicParam.setReservedSectors(val);
    }

    /** FATの数 */
    public void setNumberOfFats(int val) {
        diskBasicParam.setNumberOfFats(val);
    }

    /** ルートディレクトリのディレクトリエントリ数 */
    public void setDirEntryCount(int val) {
        diskBasicParam.setDirEntryCount(val);
    }

    /** メディアID */
    public void setMediaId(byte val) {
        diskBasicParam.setMediaId(val);
    }

    /** BASICカテゴリ名 */
    public String getBasicCategoryName() {
        return diskBasicParam.getBasicCategoryName();
    }

    /** 説明 */
    public void setBasicDescription(String str) {
        diskBasicParam.setBasicDescription(str);
    }

    /** ルートディレクトリのディレクトリエントリ数 */
    public int getDirEntryCount() {
        return diskBasicParam.getDirEntryCount();
    }

//#endregion

    /** Set BASIC type */
    private void createType() {
        type = null;

        DiskBasicFormat fmt = getFormatType();
        if (fmt == null) return;

        switch (fmt.getTypeNumber()) {
            case FORMAT_TYPE_L3_1S:
                type = new DiskBasicTypeL31S(this, fat, dir);
                break;
            case FORMAT_TYPE_L3S1_2D:
                type = new DiskBasicTypeL32D(this, fat, dir);
                break;
            case FORMAT_TYPE_FM:
                type = new DiskBasicTypeFM(this, fat, dir);
                break;
            case FORMAT_TYPE_MSDOS:
            case FORMAT_TYPE_LOSA:
            case FORMAT_TYPE_CDOS2:
                type = new DiskBasicTypeMSDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_MSX:
                type = new DiskBasicTypeMSX(this, fat, dir);
                break;
            case FORMAT_TYPE_N88:
                type = new DiskBasicTypeN88(this, fat, dir);
                break;
            case FORMAT_TYPE_X1HU:
                type = new DiskBasicTypeX1HU(this, fat, dir);
                break;
            case FORMAT_TYPE_MZ:
                type = new DiskBasicTypeMZ(this, fat, dir);
                break;
            case FORMAT_TYPE_FLEX:
                type = new DiskBasicTypeFLEX(this, fat, dir);
                break;
            case FORMAT_TYPE_OS9:
                type = new DiskBasicTypeOS9(this, fat, dir);
                break;
            case FORMAT_TYPE_CPM:
                type = new DiskBasicTypeCPM(this, fat, dir);
                break;
            case FORMAT_TYPE_PA:
                type = new DiskBasicTypePA(this, fat, dir);
                break;
            case FORMAT_TYPE_SMC:
                type = new DiskBasicTypeSMC(this, fat, dir);
                break;
            case FORMAT_TYPE_FP:
                type = new DiskBasicTypeFP(this, fat, dir);
                break;
            case FORMAT_TYPE_DOS80:
                type = new DiskBasicTypeDOS80(this, fat, dir);
                break;
            case FORMAT_TYPE_FROST:
                type = new DiskBasicTypeFROST(this, fat, dir);
                break;
            case FORMAT_TYPE_MAGICAL:
                type = new DiskBasicTypeMAGICAL(this, fat, dir);
                break;
            case FORMAT_TYPE_SDOS:
                type = new DiskBasicTypeSDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_MDOS:
                type = new DiskBasicTypeMDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_XDOS:
                type = new DiskBasicTypeXDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_TFDOS:
                type = new DiskBasicTypeTFDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_CDOS:
                type = new DiskBasicTypeCDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_MZ_FDOS:
                type = new DiskBasicTypeMZFDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_HU68K:
                type = new DiskBasicTypeHU68K(this, fat, dir);
                break;
            case FORMAT_TYPE_FALCOM:
                type = new DiskBasicTypeFalcom(this, fat, dir);
                break;
            case FORMAT_TYPE_APLEDOS:
                type = new DiskBasicTypeAppleDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_PRODOS:
                type = new DiskBasicTypeProDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_C1541:
                type = new DiskBasicTypeC1541(this, fat, dir);
                break;
            case FORMAT_TYPE_AMIGA:
                type = new DiskBasicTypeAmiga(this, fat, dir);
                break;
            case FORMAT_TYPE_M68FDOS:
                type = new DiskBasicTypeM68FDOS(this, fat, dir);
                break;
            case FORMAT_TYPE_TRSD23:
                type = new DiskBasicTypeTRSD23(this, fat, dir);
                break;
            case FORMAT_TYPE_TRSD13:
                type = new DiskBasicTypeTRSD13(this, fat, dir);
                break;
            default:
                logger.log(Level.INFO, "Unknown type is defined in basic_type.xml.");
                break;
        }
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
     * @param newDisk      新しいディスク
     * @param newSide      サイド番号 片面の場合のみ 両面なら -1
     * @param match        パラメータ 既に分かっている場合にセット
     * @param isFormatting フォーマット実行時 true
     * @return >0: ワーニング, 0: 正常, <0: エラーあり
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

        // 新しいディスクにあるBASICヒント
        DiskBasicParams validParams = new DiskBasicParams();
        // 新しいディスクにあるBASIC種類一覧
        List<Double> validRatios = new ArrayList<>();

        double validRatio = 0.0;
        if (match == null) {
            boolean support = false;
            // サポートしているDISK BASICがあるかどうか
            // 手動設定の時はhintもtypesも何も入っていないので
            // テンプレートの中にパラメータが一致するものがあるかをさがす
            match = gDiskBasicTemplates.findType(hint, types);
            if (match != null) {
                support = true;
            }
            if (!support) {
                // DISK BASICとして使用不可
                clear();
                errInfo.setError(DiskBasicError.ERR_SUPPORTED);
                return errInfo.getValid();
            }

            for (DiskParamName diskParamName : types) {
                match = gDiskBasicTemplates.findType(hint, diskParamName.getName());
                if (match != null) {
                    // フォーマットされているか？
                    logger.log(Level.INFO, "Parsing format: %s".formatted(match.getBasicTypeName()));
                    validRatio = parseFormattedDisk(newDisk, match, isFormatting);
                    logger.log(Level.INFO, "Result => %.2f".formatted(validRatio));
                    if (validRatio >= 0.0) {
                        // 候補にする
                        validParams.list.add(match);
                        validRatios.add(validRatio);
                    }
                }
            }

            errInfo.clear();
            if (!validParams.list.isEmpty()) {
                // それらしいものを候補とする
                int idx = maxRatio(validRatios);
                if (idx < 0) idx = 0;
                match = validParams.list.get(idx);
                // 再度チェックする
                logger.log(Level.INFO, "Decided format: %s".formatted(match.getBasicTypeName()));
                validRatio = parseFormattedDisk(newDisk, match, isFormatting);
                logger.log(Level.INFO, "  Result => %.2f".formatted(validRatio));
            }
        } else {
            // すでにフォーマット済み
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
     * @param newDisk      新しいディスク
     * @param match        DISK BASICのパラメータ
     * @param isFormatting フォーマット実行時true
     * @return <0.0: エラーあり
     */
    private double parseFormattedDisk(DiskImageDisk newDisk, DiskBasicParam match, boolean isFormatting) throws IOException {
        double validRatio = 0.0;

        setBasicParam(match);
        setDiskParam(newDisk);

        dir.setFormatType(getFormatType());

        if (getSectorsPerTrackOnBasic() < 0) setSectorsPerTrackOnBasic(getSectorsPerTrack());
        if (getTracksPerSideOnBasic() < 0) setTracksPerSideOnBasic(getTracksPerSide());
        if (getSidesPerDiskOnBasic() <= 0) setSidesPerDiskOnBasic(getSidesPerDisk());

        if (getSectorSize() <= 0) {
            logger.log(Level.WARNING, "sectorSize is 0");
            return -1.0;
        }
        calcDirStartEndSector(getSectorSize());
        createType();
        if (type == null) {
            return -1.0;
        }

        assignParameter();

        double prmValidRatio = type.parseParamOnDisk(isFormatting);
        if (prmValidRatio < 0.0) {
            errInfo.setError(DiskBasicError.ERR_IN_PARAMETER_AREA);
        } else if (prmValidRatio < 1.0) {
            errInfo.setInfo(DiskBasicError.ERR_INVALID_IN_PARAMETER_AREA);
        }
        validRatio += prmValidRatio;

        double fatValidRatio = 0.0;
        if (validRatio >= 0.0) {
            fatValidRatio = assignFat(isFormatting);
            if (!isFormatting && fatValidRatio < 0.0) {
                errInfo.setInfo(DiskBasicError.ERR_IN_FAT_AREA);
            }
            validRatio += fatValidRatio;
        }

        double dirValidRatio = 0.0;
        if (validRatio >= 0.0) {
            dirValidRatio = checkRootDirectory(isFormatting);
            if (!isFormatting && dirValidRatio < 0.0) {
                errInfo.setInfo(DiskBasicError.ERR_IN_DIRECTORY_AREA);
            }
            validRatio += dirValidRatio;
        }

        if ((prmValidRatio >= 0.0 && fatValidRatio >= 0.0 && dirValidRatio >= 0.0) || isFormatting || forcefully) {
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

        if (type != null) type.clearDiskFreeSize();
    }

    /** DISKイメージの番号を返す */
    public int getDiskNumber() {
        return disk != null ? disk.getNumber() : -1;
    }

    /** 選択中のサイド文字列を返す */
    public String getSelectedSideStr() {
        return Utils.getSideStr(selectedSide, canMountEachSides());
    }

    /** DISK BASICの説明を取得 */
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

    /** FATエリアの空き状況を取得 */
    public void getFatAvailability(int[] offset, List<Integer>[] arr) {
        if (type != null) {
            type.getFatAvailability(offset, arr);
        }
    }

    /** ディレクトリのアイテムを取得 */
    public DiskBasicDirItem<?> getDirItem(int pos) {
        return dir.item(pos);
    }

    public DiskImageTrack getManagedTrack(int sectorPos, int[] sideNum, int[] sectorNum) {
        return getManagedTrack(sectorPos, sideNum, sectorNum, null, null);
    }

    /**
     * 管理エリアのサイド番号、セクタ番号、トラックを得る
     *
     * @param sectorPos セクタ位置(管理トラックの最初のサイド＆最初のセクタを 0 した通し番号)
     * @param sideNum   [out] サイド番号 Nullable
     * @param sectorNum [out] セクタ番号 Nullable
     * @param divNum    [out] 分割番号 Nullable
     * @param divNums   [out] 分割数 Nullable
     * @return トラックデータ
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
     * 管理エリアのトラック番号、サイド番号、セクタ番号、セクタポインタを得る
     *
     * @param sectorPos セクタ位置(管理トラックの最初のサイド＆最初のセクタを 0 した通し番号)
     * @param trackNum  [out] トラック番号 Nullable
     * @param sideNum   [out] サイド番号 Nullable
     * @param sectorNum [out] セクタ番号 Nullable
     * @param divNum    [out] 分割番号 Nullable
     * @param numOfDivs   [out] 分割数 Nullable
     * @return セクタデータ
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
     * DISK BASICで使用できる残りディスクサイズに足りるか
     *
     * @param size 指定サイズ
     * @return true: 足りる, false: 足りない
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

    /** DISK BASICで使用できる残りディスクサイズを返す */
    public int getFreeDiskSize() {
        return type.getFreeDiskSize();
    }

    /** 固有のパラメータをセット */
    public void assignParameter() {
        dataStartSector = type.calcDataStartSectorPos();
        skippedTrack = type.calcSkippedTrack();
    }

    /**
     * 現在選択しているディスクのFAT領域をアサイン
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, <1.0: 警告あり, <0.0: エラーあり
     */
    public double assignFat(boolean isFormatting) throws IOException {
        if (disk == null) return -1.0;
        if (assigned) return 0.0;

        fat.empty();
        assignParameter();
        return fat.assign(isFormatting);
    }

    /**
     * 現在選択しているディスクのルートディレクトリ構造をチェック
     *
     * @param isFormatting フォーマット中か
     * @return <0.0: ディレクトリにエラーあり
     */
    public double checkRootDirectory(boolean isFormatting) throws IOException {
        if (disk == null) return -1.0;
        if (assigned) return 1.0;
        return dir.checkRoot(type, diskBasicParam.getDirStartSector(), diskBasicParam.getDirEndSector(), isFormatting);
    }

    /**
     * 現在選択しているディスクのルートディレクトリをアサイン
     *
     * @return false: ディレクトリにエラーあり
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
     * 現在選択しているディスクのFATとルートディレクトリをアサイン
     *
     * @return true, false: エラーあり
     */
    public boolean assignFatAndDirectory() throws IOException {
        boolean valid = (assignFat(false) >= 0.0);
        valid = valid && assignRootDirectory();
        return valid;
    }

    /** 解析済みをクリア */
    public void clearParseAndAssign(boolean forcely) {
        parsed = false;
        assigned = false;
        forcefully = forcely;
        dir.releaseRoot(type);
        dir.setCurrentAsRoot();
    }

    /** 解析済みか */
    public boolean isParsed() {
        return parsed;
    }

    /** アサイン済みか */
    public boolean isAssigned() {
        return assigned;
    }

    /** 解析エラーを無視するか */
    public boolean isForcely() {
        return forcefully;
    }

    /// ロードできるか
    ///
    /// @param item ディレクトリのアイテム
    public boolean isLoadableFile(DiskBasicDirItem<?> item) {
        if (item == null || !item.isLoadable() || !item.isUsed()) {
            errInfo.setError(DiskBasicError.ERRV_CANNOT_EXPORT, item.getFileNameStr());
            return false;
        }
        return true;
    }

    /// 指定したディレクトリ位置のファイルをロード
    ///
    /// @param itemNumber ディレクトリの位置
    /// @param dstPath    出力先パス
    public boolean loadFile(int itemNumber, String dstPath) {
        DiskBasicDirItem<?> item = dir.item(itemNumber);
        return loadFile(item, dstPath);
    }

    /// 指定したディレクトリアイテムのファイルをロード
    ///
    /// @param item    ディレクトリのアイテム
    /// @param dstPath 出力先パス
    public boolean loadFile(DiskBasicDirItem<?> item, String dstPath) {
        try (FileOutputStream file = new FileOutputStream(dstPath)) {
            return loadFile(item, file);
        } catch (IOException e) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_EXPORT);
            return false;
        }
    }

    /**
     * 指定したストリームにファイルをロード
     *
     * @param item    ディレクトリのアイテム
     * @param ostream [in,out] 出力先ストリーム
     */
    public boolean loadFile(DiskBasicDirItem<?> item, OutputStream ostream) throws IOException {
        ByteArrayOutputStream otemp = new ByteArrayOutputStream();
        boolean sts = loadData(item, otemp, null);
        if (!sts) {
            return false;
        }
        if (otemp.size() == 0) {
            return true;
        }
        ByteArrayInputStream itemp = new ByteArrayInputStream(otemp.toByteArray());
        sts = type.convertDataForLoad(item, itemp, ostream);
        return sts;
    }

    /**
     * 指定したアイテムのファイルをベリファイ
     *
     * @param item    ディレクトリのアイテム
     * @param srcPath 比較するファイルのパス
     * @return 0: 差異なし, 1:差異あり, -1: エラー
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
     * 指定したストリームにファイルをロード
     *
     * @param item    [in,out] ディレクトリアイテム
     * @param ostream [in,out] エクスポート時指定
     * @param outsize [out] 実際に出力したサイズ(ostreamを指定した時のみ有効)
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
     * 指定したアイテムのファイルをベリファイ
     *
     * @param item    [in,out] ディレクトリアイテム
     * @param istream [in,out] ベリファイ時指定
     * @return 0: 差異なし, 1: 差異あり, -1: エラー
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
     * ディスクデータにアクセス（ロード/ベリファイで使用）
     *
     * @param fileunitNum ファイル番号
     * @param item        [in,out] ディレクトリアイテム
     * @param istream     [in,out] ベリファイ時指定
     * @param ostream     [in,out] エクスポート時指定
     * @param outsize     [out] 実際に出力したサイズ(ostreamを指定した時のみ有効)
     * @return 0: 差異なし, 1:差異あり, -1: エラー
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
     * 同じファイル名が既に存在して上書き可能か
     *
     * @param dirItem     検索するディレクトリ
     * @param filename    ファイル名
     * @param excludeItem 検索対象から除くアイテム
     * @param nextItem    [out] 一致したアイテムの次位置にあるアイテム
     * @return 0: なし, 1: あり 通常ファイル, -1 あり 上書き不可（ディレクトリ or ボリュームラベル）
     */
    public int isFileNameDuplicated(DiskBasicDirItem<?> dirItem, DiskBasicFileName filename, DiskBasicDirItem<?> excludeItem, DiskBasicDirItem[] nextItem) {
        DiskBasicDirItem<?> item = dir.findFile(dirItem, filename, isCompareCaseInsensitive(), excludeItem, nextItem);
        if (item == null) {
            return 0;
        }
        return (item.isOverWritable() ? 1 : -1);
    }

    /**
     * 同じファイル名が既に存在して上書き可能か
     *
     * @param dirItem     検索するディレクトリ
     * @param targetItem  アイテム
     * @param excludeItem 検索対象から除くアイテム
     * @param nextItem    [out] 一致したアイテムの次位置にあるアイテム
     * @return 0: なし, 1: あり 通常ファイル, -1: あり 上書き不可（ディレクトリ or ボリュームラベル）
     */
    public int isFileNameDuplicated(DiskBasicDirItem<?> dirItem, DiskBasicDirItem<?> targetItem, DiskBasicDirItem<?> excludeItem, DiskBasicDirItem[] nextItem) {
        DiskBasicDirItem<?> item = dir.findFile(dirItem, targetItem, isCompareCaseInsensitive(), excludeItem, nextItem);
        if (item == null) {
            return 0;
        }
        return (item.isOverWritable() ? 1 : -1);
    }

    /** 書き込みできるか */
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
     * 指定ファイルのサイズでディスクに書き込めるかをチェック
     *
     * @param srcPath  ファイルパス
     * @param fileSize [out] ファイルのサイズを返す
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
     * 指定ファイルをディスクイメージにセーブ
     *
     * @param srcPath 元ファイルのあるパス
     * @param dirItem [in,out] セーブ先ディレクトリアイテム
     * @param pitem   [in,out] セーブ用のファイル名、属性を持っているディレクトリアイテム
     * @param nitem   [out] 確保したディレクトリアイテム
     * @return false: エラーあり
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
     * バッファデータをディスクイメージにセーブ
     *
     * @param buffer  データ
     * @param dirItem [in,out] セーブ先ディレクトリアイテム
     * @param pItem   [in,out] セーブ用のファイル名、属性を持っているディレクトリアイテム
     * @param nitem   [out] 確保したディレクトリアイテム
     * @return false: エラーあり
     */
    public <T extends Directory> boolean saveFile(byte[] buffer, DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pItem, DiskBasicDirItem<T>[] nitem) throws IOException {
        if (!isWritableIntoDisk()) return false;

        ByteArrayInputStream inData = new ByteArrayInputStream(buffer);
        return saveFile(inData, dirItem, pItem, nitem);
    }

    /**
     * ストリームデータをディスクイメージにセーブ
     *
     * @param iStream ストリームバッファ
     * @param dirItem [in,out] セーブ先ディレクトリアイテム
     * @param pItem   [in,out] セーブ用のファイル名、属性を持っている仮ディレクトリアイテム
     * @param nItem   [out] 確保したディレクトリアイテム
     * @return false: エラーあり
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
     * ストリームデータをディスクイメージにセーブ
     *
     * @param iStream    ストリームバッファ
     * @param pItem      [in,out] ファイル名、属性を持っている仮ディレクトリアイテム
     * @param item       [in,out] 確保したディレクトリアイテム
     * @param groupItems [out] グループリスト
     * @param fileSize   [out] セーブしたファイルのサイズ
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
     * ストリームデータをディスクイメージにセーブ
     *
     * @param fileUnitNum ファイル番号
     * @param iStream     ストリームバッファ
     * @param iSize       バッファ内のセーブ対象データサイズ
     * @param pItem       [in,out] ファイル名、属性を持っている仮ディレクトリアイテム
     * @param item        [in,out] 確保したディレクトリアイテム
     * @param groupItems  [out] グループリスト
     * @param fileSize    [out] セーブしたファイルのサイズ
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

    /** ファイルを削除できるか */
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
     * 指定したファイルを削除できるか
     *
     * @param item     ディレクトリアイテム
     * @param clearMsg エラーメッセージのバッファをクリアするか
     * @return -1: エラー継続不可, 1: エラー継続可能
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
     * 指定したディレクトリが空か
     *
     * @param item       ディレクトリアイテム
     * @param groupItems [out] グループ番号一覧
     * @param clearMsg   エラーメッセージのバッファをクリアするか
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
     * ファイルを削除
     *
     * @param item     ディレクトリアイテム
     * @param clearMsg エラーメッセージのバッファをクリアするか
     * @return true: 成功, false: 失敗
     */
    public boolean deleteFile(DiskBasicDirItem<?> item, boolean clearMsg) throws IOException {
        if (item == null) return false;
        if (clearMsg) errInfo.clear();
        DiskBasicGroups groupItems = new DiskBasicGroups();
        item.getAllGroups(groupItems);
        return deleteFile(item, groupItems);
    }

    /**
     * ファイルを削除
     *
     * @param item       ディレクトリアイテム
     * @param groupItems グループ番号一覧
     * @return true: 成功, false: 失敗
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
     * ファイル名や属性を更新できるか
     *
     * @param item    ディレクトリアイテム
     * @param showMsg エラーメッセージをセットするか
     * @return true: できる, false: できない
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
     * ファイル名を更新
     *
     * @param item    ディレクトリアイテム
     * @param newName ファイル名
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
     * 属性を更新
     *
     * @param item ディレクトリアイテム
     * @param attr 属性値
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

    /** DISK BASIC用にフォーマットされているか */
    public boolean isFormatted() {
        return (disk != null && formatted);
    }

    /** DISK BASIC用にフォーマットできるか */
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
     * ディスクを論理フォーマット
     *
     * @param data 機種依存データ（ボリューム名など）
     * @return >0: ワーニング, 0: 正常, <0: エラーあり
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

    /** ルートディレクトリを返す */
    public <T extends Directory> DiskBasicDirItem<T> getRootDirectory() {
        return dir.getRootItem();
    }

    /**
    /// ルートディレクトリ内の一覧を返す
    ///
    /// @param dirItem nullable
     */
    public <T extends Directory> List<DiskBasicDirItem<T>> getRootDirectoryItems(DiskBasicDirItem<T>[] dirItem) {
        return dir.getRootItems(dirItem);
    }

    /** カレントディレクトリを返す */
    public <T extends Directory> DiskBasicDirItem<T> getCurrentDirectory() {
        return dir.getCurrentItem();
    }

    /** カレントディレクトリ内の一覧を返す */
    public <T extends Directory> List<DiskBasicDirItem<T>> getCurrentDirectoryItems(DiskBasicDirItem<T>[] dirItem) {
        return dir.getCurrentItems(dirItem);
    }

    /**
     * ディレクトリをアサイン
     *
     * @param dirItem ディレクトリのアイテム
     */
    public <T extends Directory> boolean assignDirectory(DiskBasicDirItem<T> dirItem) throws IOException {
        if (disk == null) return false;
        return dir.assign(dirItem);
    }

    /**
     * ディレクトリを読み直す
     *
     * @param dirItem ディレクトリのアイテム
     */
    public <T extends Directory> boolean reassignDirectory(DiskBasicDirItem<T> dirItem) throws IOException {
        if (disk == null) return false;
        return dir.reassign(dirItem);
    }

    /**
     * ディレクトリを変更
     *
     * @param dstItem [in,out] 移動先ディレクトリのアイテム
     */
    public <T extends Directory> boolean changeDirectory(DiskBasicDirItem<T>[] dstItem) throws IOException {
        if (disk == null) return false;
        boolean valid = dir.change(dstItem);
        if (valid) {
            // 残りサイズ計算
            type.calcDiskFreeSize(false);
        }
        return valid;
    }

    /** サブディレクトリの作成できるか */
    public boolean canMakeDirectory() {
        return (type.canMakeDirectory()) && (getSubDirGroupSize() > 0);
    }

    /**
     * サブディレクトリの作成
     *
     * @param dirItem        [in,out] 作成先のディレクトリ(親ディレクトリ)
     * @param filename       ディレクトリ名
     * @param ignoreDatetime 日時は設定しないか
     * @param nitem          [out] 作成したディレクトリアイテム
     * @return 1: 同じ名前がある, -1: その他エラー
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
        // サブディレクトリを作成する前にディレクトリ名を編集する
        if (!type.renameOnMakingDirectory(filenameRef)) {
            errInfo.setError(DiskBasicError.ERR_CANNOT_MAKE_DIRECTORY);
            return -1;
        }
        dirName.setName(filenameRef[0]);

        // 同じファイル名があるか
        DiskBasicDirItem[] nextItem = {null};
        DiskBasicDirItem<T> item = dir.findFile(dirItem, dirName, isCompareCaseInsensitive(), null, nextItem);
        if (item != null) {
            errInfo.setError(DiskBasicError.ERR_FILE_ALREADY_EXIST);
            return 1;
        }

        // 仮アイテムを作成
        DiskBasicDirItem<T> pitem = dir.newItem();
        // エントリをクリア
        pitem.clearData();
        // ファイル名＆属性を設定
        pitem.setFileNameStr(dirName.getName());
        pitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);

        // 日時設定
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

        // 新しいディレクトリアイテムを確保
        while ((item = dir.getEmptyItem(dirItem, pitem, nextItem)) == null) {
            // 確保できない時
            boolean valid = false;
            // ディレクトリエリアを拡張する
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

        // ファイル名属性をクリア
        item.clearData();
        // ファイル名属性を設定
        item.copyItem(pitem);

//        item.setFileNameStr(dir_name.getName());
//        item.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK, 0);
//        item.setFileCreateDateTime(tm.now());

        // ディレクトリを作成する前の準備を行う
        if (!type.prepareToMakeDirectory(item)) {
            this.deleteFile(item, false);
            return -1;
        }

        int[] sizeRemain = {getSectorsPerGroup() * getSectorSize() * getSubDirGroupSize()};
        // 空きがあるか
        if (sizeRemain[0] > getFreeDiskSize()) {
            // 空きが足りない
            errInfo.setError(DiskBasicError.ERR_DISK_FULL);
            // アイテムに削除マークを入れる
            this.deleteFile(item, false);
            return -1;
        }

        int[] fileSize = {0};

        int rc;

        // 必要なディスク領域を確保する
        DiskBasicGroups[] groupItems = {new DiskBasicGroups()};
        rc = type.allocateGroups(item, sizeRemain[0], ALLOCATE_GROUPS_NEW, groupItems);
        if (rc < 0) {
            // 空きが足りない
            errInfo.setError(DiskBasicError.ERR_DISK_FULL);
            // 確保した領域を削除
            this.deleteFile(item, false);
            return -1;
        }

        // セクタに書き込む
        rc = type.initializeSectorsAsDirectory(groupItems[0], fileSize, sizeRemain, errInfo);

        // ディレクトリサイズ
        item.setDirectorySize(fileSize[0]);

        if (rc < 0) {
            // エラーの場合は消す
            this.deleteFile(item, false);
            return -1;
        }

        // 変更された
        item.refresh();
        item.setModify();

        // ディレクトリ作成後の個別処理
        type.additionalProcessOnMadeDirectory(item, groupItems[0], dirItem);

        // グループ数を計算
        item.calcFileSize();
        // 空きサイズを計算
        type.calcDiskFreeSize(true);

        return 0;
    }

    /**
     * ディレクトリのサイズを拡張
     *
     * @param dirItem ディレクトリのエントリ
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

    /** ディレクトリアイテムの作成 */
    public DiskBasicDirItem<?> createDirItem() throws IOException {
        return dir.newItem();
    }

    /**
     * ディレクトリアイテムの作成
     *
     * @param sector セクタデータ
     * @param secPos セクタ内の位置
     * @param data   ディレクトリデータ
     * @param dataP  ディレクトリデータのポインタ
     * @return ディレクトリアイテム
     */
    public <T extends Directory> DiskBasicDirItem<T> createDirItem(DiskImageSector sector, int secPos, byte[] data, int dataP) throws IOException {
        return dir.newItem(sector, secPos, data, dataP);
    }

    /** ディレクトリアイテムの位置から開始セクタを返す */
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
     * グループ番号から開始セクタを返す
     *
     * @param groupNum グループ番号
     * @param divNum   [out] 分割番号
     * @param divNums  [out] 分割数
     */
    public DiskImageSector getSectorFromGroup(int groupNum, int[] divNum, int[] divNums) {
        int[] trackNum = {0};
        int[] sideNum = {0};
        return getSectorFromGroup(groupNum, trackNum, sideNum, divNum, divNums);
    }

    /**
     * グループ番号から開始セクタを返す
     *
     * @param groupNum グループ番号
     * @param trackNum [out] トラック番号
     * @param sideNum  [out] サイド番号
     * @param divNum   [out] 分割番号
     * @param divNums  [out] 分割数
     * @return セクタ
     * 管理エリアがあれば飛ばす、開始グループ番号のオフセット分を引く などの機種依存を考慮
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
     * グループ番号からトラック番号、サイド番号、セクタ番号を計算してリストに入れる
     * <p>
     * 管理エリアがあれば飛ばす、開始グループ番号のオフセット分を引く などの機種依存を考慮
     *
     * @param groupNum   グループ番号
     * @param nextGroup  次のグループ番号
     * @param sectorSize セクタサイズ
     * @param remainSize 残りデータサイズ
     * @param items      [out] トラック、サイド、セクタの各番号が入ったリスト
     * @param endSector  [out] このグループの最終セクタ番号
     * @return false: グループ番号が範囲外
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
     * グループ番号からトラック、サイド、セクタの各番号を計算(グループ計算用)
     * <p>
     * 管理エリアがあれば飛ばす、開始グループ番号のオフセット分を引く などの機種依存を考慮
     *
     * @param groupNum    グループ番号
     * @param trackStart  [out] トラック番号
     * @param sideStart   [out] サイド番号
     * @param sectorStart [out] セクタ番号
     * @param divNum      [out] 分割番号
     * @param divNums     [out] 分割数
     * @return false: グループ番号が範囲外
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
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を計算(グループ計算用)
     * <p>
     * 管理エリアがあれば飛ばす、開始グループ番号のオフセット分を引く などの機種依存を考慮
     *
     * @param sectorPos セクタ位置(トラック0,サイド0のセクタを0とした位置)
     * @param trackNum  [out] トラック番号
     * @param sideNum   [out] サイド番号
     * @param sectorNum [out] セクタ番号
     * @param divNum    [out] 分割番号
     * @param divNums   [out] 分割数
     */
    public void calcNumFromSectorPosForGroup(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum /* = null */, int[] divNums /* = null */) {
        sectorPos += dataStartSector;
        type.getNumFromSectorPos(sectorPos, trackNum, sideNum, sectorNum, divNum, divNums);
        if (trackNum[0] >= skippedTrack) trackNum[0]++;
    }

    /**
     * セクタ位置(トラック0,セクタ1を0とした通し番号)からトラック、セクタの各番号を計算(グループ計算用)
     * サイド番号はトラック番号に変換、トラック番号はサイド数の倍数となる
     * <p>
     * 管理エリアがあれば飛ばす、開始グループ番号のオフセット分を引く などの機種依存を考慮
     *
     * @param sectorPos セクタ位置(トラック0のセクタ1を0とした位置)
     * @param trackNum  [out] トラック番号
     * @param sectorNum [out] セクタ番号(サイド1のときは+トラック数となる)
     */
    public void calcNumFromSectorPosTForGroup(int sectorPos, int[] trackNum, int[] sectorNum) {
        sectorPos += dataStartSector;
        type.getNumFromSectorPosT(sectorPos, trackNum, sectorNum);
        if (trackNum[0] >= skippedTrack) trackNum[0]++;
    }

    /**
     * トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を計算(グループ計算用)
     * <p>
     * 管理エリアがあれば飛ばす、開始グループ番号のオフセット分を引く などの機種依存を考慮
     *
     * @param trackNum  トラック番号
     * @param sideNum   サイド番号
     * @param sectorNum セクタ番号
     * @param divNum    分割番号
     * @param divNums   分割数
     * @return セクタ位置(トラック0,サイド0のセクタを0とした位置)
     */
    public int calcSectorPosFromNumForGroup(int trackNum, int sideNum, int sectorNum, int divNum /* = 0 */, int divNums /* = 1 */) {
        sideNum = getReversedSideNumber(sideNum);
        if (trackNum >= skippedTrack) trackNum--;

        int sectorPos = type.getSectorPosFromNum(trackNum, sideNum, sectorNum, divNum, divNums);
        sectorPos -= dataStartSector;
        return sectorPos;
    }

    /**
     * トラック、セクタの各番号からセクタ位置(トラック0セクタ1を0とした通し番号)を計算(グループ計算用)
     * サイド番号はトラック番号に変換、トラック番号はサイド数の倍数となる
     *
     * 管理エリアがあれば飛ばす、開始グループ番号のオフセット分を引く などの機種依存を考慮
     *
     * @param trackNum  トラック番号
     * @param sectorNum セクタ番号(サイド1のときは+トラック数)
     * @return セクタ位置(トラック0のセクタ1を0とした位置)
     */
    public int calcSectorPosFromNumTForGroup(int trackNum, int sectorNum) {
        if (trackNum >= skippedTrack) trackNum--;
        int sectorPos = type.getSectorPosFromNumT(trackNum, sectorNum);
        sectorPos -= dataStartSector;
        return sectorPos;
    }

    /**
     * トラックを返す
     *
     * @param trackNum トラック番号
     * @param sideNum  サイド番号
     * @return トラックデータ
     */
    public DiskImageTrack getTrack(int trackNum, int sideNum) {
        return disk.getTrack(trackNum, sideNum);
    }

    /**
     * セクタ返す
     *
     * @param trackNum  トラック番号
     * @param sideNum   サイド番号
     * @param sectorNum セクタ番号
     * @return セクタデータ
     */
    public DiskImageSector getSector(int trackNum, int sideNum, int sectorNum) {
        return disk.getSector(trackNum, sideNum, sectorNum);
    }

    /**
     * セクタ返す
     *
     * @param trackNum  トラック番号
     * @param sectorNum セクタ番号(サイド0～1の通し番号)
     * @param sideNum   [out] サイド番号
     * @return セクタデータ
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
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラックを返す
     * <p>
     * セクタ位置は、機種によらずトラック0,サイド0,セクタ1を0とした通し番号
     *
     * @param sectorPos セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)
     * @param sectorNum [out] セクタ番号
     * @param divNum    [out] 分割番号
     * @param divNums   [out] 分割数
     * @return トラックデータ
     */
    public DiskImageTrack getTrackFromSectorPos(int sectorPos, int[] sectorNum, int[] divNum, int[] divNums) {
        int[] trackNum = {0};
        int[] sideNum = {0};
        type.getNumFromSectorPos(sectorPos, trackNum, sideNum, sectorNum, divNum, divNums);
        return disk.getTrack(trackNum[0], sideNum[0]);
    }

    /**
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からセクタを返す
     * <p>
     * セクタ位置は、機種によらずトラック0,サイド0,セクタ1を0とした通し番号
     *
     * @param sectorPos セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)
     * @param trackNum  [out] トラック番号
     * @param sideNum   [out] サイド番号
     * @param divNum    [out] 分割番号
     * @param divNums   [out] 分割数
     * @return セクタデータ
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
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からセクタを返す
     * <p>
     * セクタ位置は、機種によらずトラック0,サイド0,セクタ1を0とした通し番号
     *
     * @param sectorPos セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)
     * @param divNum    [out] 分割番号
     * @param divNums   [out] 分割数
     * @return セクタデータ
     */
    public DiskImageSector getSectorFromSectorPos(int sectorPos, int[] divNum, int[] divNums) {
        int[] trackNum = {0};
        int[] sideNum = {0};
        return getSectorFromSectorPos(sectorPos, trackNum, sideNum, divNum, divNums);
    }

    /**
     * 開始セクタ番号を返す
     *
     * DiskBasicParamを優先
     */
    public int getSectorNumberBase() {
        int val = getSectorNumberBaseOnBasic();
        if (val < 0) val = getSectorNumberBaseOnDisk();
        return val;
    }

    /** キャラクターコードの文字体系を設定 */
    public void setCharCode(String name) {
        if (name.equals(charCode)) return;
        charCode = name;
        codes.setMap(name);
    }

    /** 現在のキャラクターコードの文字体系を返す */
    public String getCharCode() {
        return charCode;
    }

    /** キャラクターコードの文字体系 */
    public CharCodes getCharCodes() {
        return codes;
    }

    /** DISK使用可能か */
    public boolean canUse() {
        return (disk != null);
    }

    /** DISKイメージを返す */
    public DiskImageDisk getDisk() {
        return disk;
    }

    /** 選択中のサイドを設定 */
    public void setSelectedSide(int val) {
        selectedSide = val;
    }

    /** 選択中のサイドを返す */
    public int getSelectedSide() {
        return selectedSide;
    }

    /** FATクラス */
    public DiskBasicFat getFat() {
        return fat;
    }

    /** DIRクラス */
    public <T extends Directory> DiskBasicDir<T> getDir() {
        return dir;
    }

    /** TYPEクラス */
    public <T extends Directory> DiskBasicType<T> getType() {
        return type;
    }

    /** 必要ならデータを反転する */
    public byte invertUint8(byte val) {
        return isDataInverted() ? (byte) (val ^ 0xff) : val;
    }

    /** 必要ならデータを反転する */
    public short invertUint16(short val) {
        return isDataInverted() ? (short) (val ^ 0xffff) : val;
    }

    /** 必要ならデータを反転する＆エンディアンを考慮 */
    public short invertAndOrderUint16(short val) {
        val = isDataInverted() ? (short) (val ^ 0xffff) : val;
        return orderUint16(val);
    }

    /** 必要ならデータを反転する */
    public int invertUint32(int val) {
        return isDataInverted() ? ~val : val;
    }

    /** 必要ならデータを反転する＆エンディアンを考慮 */
    public int invertAndOrderUint32(int val) {
        val = isDataInverted() ? ~val : val;
        return orderUint32(val);
    }

    /** 必要ならデータを反転する */
    public void invertMemory(byte[] data, int len) {
        if (isDataInverted()) Common.invertMemory(data, len);
    }

    /** 必要ならデータを反転する */
    public void invertMemory(byte[] src, int len, byte[] dst) {
        System.arraycopy(src, 0, dst, 0, len);
        if (isDataInverted()) Common.invertMemory(dst, len);
    }

    /** エンディアンを考慮した値を返す */
    public short orderUint16(short val) {
        return isBigEndian() ? Short.reverseBytes(val) : val;
    }

    /** エンディアンを考慮した値を返す */
    public int orderUint32(int val) {
        return isBigEndian() ? Integer.reverseBytes(val) : val;
    }

    /** DISK BASIC種類番号を返す */
    public DiskBasicFormatType getFormatTypeNumber() {
        return getFormatType().getTypeNumber();
    }

    /** エラーメッセージ */
    public List<String> getErrorMessage(int maxRow) {
        return errInfo.getMessages(maxRow);
    }

    /**
     * エラー有無
     *
     * @return <0: エラー, 0: 正常, 0>: ワーニング
     */
    public int getErrorLevel() {
        return errInfo.getValid();
    }

    public DiskBasicError getErrInfo() {
        return errInfo;
    }

    /** エラーメッセージを表示 */
    public void showErrorMessage() {
        ResultInfo.showMessage(getErrorLevel(), getErrorMessage(20));
    }

    /** エラーメッセージをクリア */
    public void clearErrorMessage() {
        errInfo.clear();
    }
}
