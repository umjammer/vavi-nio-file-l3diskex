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

import l3diskex.Common;
import l3diskex.ResultInfo;
import l3diskex.Utils;
import l3diskex.CharCodes;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
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
        private String mVolumeName;
        /** ボリューム名最大長 */
        private int mVolumeNameMaxlen;
        /** ボリューム番号 */
        private int mVolumeNumber;
        /** ボリューム番号が16進か */
        private boolean mVolumeNumberHexa;
        /** ボリューム日付 */
        private String mVolumeDate;

        public DiskBasicIdentifiedData() {
            mVolumeNameMaxlen = 0;
            mVolumeNumber = 0;
            mVolumeNumberHexa = false;
        }

        public DiskBasicIdentifiedData(String volumeName, int volumeNumber, String volumeDate) {
            mVolumeName = volumeName;
            mVolumeNameMaxlen = 0;
            mVolumeNumber = volumeNumber;
            mVolumeNumberHexa = false;
            mVolumeDate = volumeDate;
        }

        public String getVolumeName() {
            return mVolumeName;
        }

        public int getVolumeNameMaxLength() {
            return mVolumeNameMaxlen;
        }

        public int getVolumeNumber() {
            return mVolumeNumber;
        }

        public boolean isVolumeNumberHexa() {
            return mVolumeNumberHexa;
        }

        public String getVolumeDate() {
            return mVolumeDate;
        }

        public void setVolumeName(String val) {
            mVolumeName = val;
        }

        public void setVolumeNameMaxLength(int val) {
            mVolumeNameMaxlen = val;
        }

        public void setVolumeNumber(int val) {
            mVolumeNumber = val;
        }

        public void volumeNumberIsHexa(boolean val) {
            mVolumeNumberHexa = val;
        }

        public void setVolumeDate(String val) {
            mVolumeDate = val;
        }
    }

    //
    //
    //

    // DiskBasics
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

    public DiskBasicParam diskBasicParam; // For composition

    private DiskImageDisk pDisk;
    private boolean mFormatted;
    private boolean mParsed;
    private boolean mAssigned;
    private boolean mForcely;

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
    private final DiskBasicError errinfo;

    public DiskBasic() {
        this.diskBasicParam = new DiskBasicParam();

        pDisk = null;
        mFormatted = false;
        mParsed = false;
        mAssigned = false;
        mForcely = false;
        selectedSide = -1;
        dataStartSector = 0;
        skippedTrack = 0x7fff;

        fat = new DiskBasicFat(this);
        dir = new DiskBasicDir(this);
        type = null;

        codes = new CharCodes();
        errinfo = new DiskBasicError();
    }

    public void setDiskParam(DiskImageDisk disk) {
        super.setDiskParam(disk);
        this.pDisk = disk;
    }

    public void setBasicParam(DiskBasicParam param) {
        this.diskBasicParam.setBasicParam(param);
    }

    @Override
    public void clearDiskParam() {
        super.clearDiskParam();
        this.pDisk = null;
    }

    public void clearBasicParam() {
        this.diskBasicParam.clearBasicParam();
    }

//#region Delegate methods for DiskBasicParam

    public DiskBasicFormat getFormatType() {
        return diskBasicParam.getFormatType();
    }

    public int getSectorsPerTrackOnBasic() {
        return diskBasicParam.getSectorsPerTrackOnBasic();
    }

    public void setSectorsPerTrackOnBasic(int val) {
        diskBasicParam.setSectorsPerTrackOnBasic(val);
    }

    public int getTracksPerSideOnBasic() {
        return diskBasicParam.getTracksPerSideOnBasic();
    }

    public void setTracksPerSideOnBasic(int val) {
        diskBasicParam.setTracksPerSideOnBasic(val);
    }

    public int getSidesPerDiskOnBasic() {
        return diskBasicParam.getSidesPerDiskOnBasic();
    }

    public void setSidesPerDiskOnBasic(int val) {
        diskBasicParam.setSidesPerDiskOnBasic(val);
    }

    public String getBasicTypeName() {
        return diskBasicParam.getBasicTypeName();
    }

    public String getBasicDescription() {
        return diskBasicParam.getBasicDescription();
    }

    public void calcDirStartEndSector(int sectorSize) {
        diskBasicParam.calcDirStartEndSector(sectorSize);
    }

    public int getManagedTrackNumber() {
        return diskBasicParam.getManagedTrackNumber();
    }

    public boolean isCompareCaseInsense() {
        return diskBasicParam.isCompareCaseInsense();
    }

    public int getSectorsPerGroup() {
        return diskBasicParam.getSectorsPerGroup();
    }

    public int getSubDirGroupSize() {
        return diskBasicParam.getSubDirGroupSize();
    }

    public int getFatEndGroup() {
        return diskBasicParam.getFatEndGroup();
    }

    public int getSectorNumberBaseOnBasic() {
        return diskBasicParam.getSectorNumberBaseOnBasic();
    }

    public int getValidDensityType() {
        return diskBasicParam.getValidDensityType();
    }

    public int getReversedSideNumber(int sideNum) {
        return diskBasicParam.getReversedSideNumber(sideNum);
    }

    public boolean isDataInverted() {
        return diskBasicParam.isDataInverted();
    }

    public boolean isBigEndian() {
        return diskBasicParam.isBigEndian();
    }

    public boolean canMountEachSides() {
        return diskBasicParam.canMountEachSides();
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
    private int maxRatio(List<Double> values) {
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
        errinfo.clear();

        selectedSide = newSide;

        if (mAssigned) return 0;

        pDisk = newDisk;
        mFormatted = false;

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
                errinfo.setError(DiskBasicError.ERR_SUPPORTED);
                return errinfo.getValid();
            }

            for (int n = 0; n < types.size(); n++) {
                match = gDiskBasicTemplates.findType(hint, types.get(n).getName());
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

            errinfo.clear();
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
            errinfo.clear();
            mParsed = true;
        }
        if (mForcely) {
            mParsed = true;
        }
        if (!mFormatted) {
            errinfo.setInfo(DiskBasicError.ERR_FORMATTED);
        }
        return mForcely ? 0 : errinfo.getValid();
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
            logger.log(Level.WARNING, "sector_size is 0");
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
            errinfo.setError(DiskBasicError.ERR_IN_PARAMETER_AREA);
        } else if (prmValidRatio < 1.0) {
            errinfo.setInfo(DiskBasicError.ERR_INVALID_IN_PARAMETER_AREA);
        }
        validRatio += prmValidRatio;

        double fatValidRatio = 0.0;
        if (validRatio >= 0.0) {
            fatValidRatio = assignFat(isFormatting);
            if (!isFormatting && fatValidRatio < 0.0) {
                errinfo.setInfo(DiskBasicError.ERR_IN_FAT_AREA);
            }
            validRatio += fatValidRatio;
        }

        double dirValidRatio = 0.0;
        if (validRatio >= 0.0) {
            dirValidRatio = checkRootDirectory(isFormatting);
            if (!isFormatting && dirValidRatio < 0.0) {
                errinfo.setInfo(DiskBasicError.ERR_IN_DIRECTORY_AREA);
            }
            validRatio += dirValidRatio;
        }

        if ((prmValidRatio >= 0.0 && fatValidRatio >= 0.0 && dirValidRatio >= 0.0) || isFormatting || mForcely) {
            mFormatted = true;
        }

        validRatio /= 3.0;

        return validRatio;
    }

    /** Clear parameters */
    public void clear() {
        pDisk = null;
        mFormatted = false;
        mParsed = false;
        mAssigned = false;
        mForcely = false;
        selectedSide = -1;

        clearDiskParam();
        clearBasicParam();

        if (type != null) type.clearDiskFreeSize();
    }

    /** DISKイメージの番号を返す */
    public int getDiskNumber() {
        return pDisk != null ? pDisk.getNumber() : -1;
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
        if (!mParsed) {
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
        return dir.itemPtr(pos);
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
        return pDisk.getTrack(trackNum, side0Num[0]);
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
     * @param divNums   [out] 分割数 Nullable
     * @return セクタデータ
     */
    public DiskImageSector getManagedSector(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] divNums) {
        int[] secNum = {0};
        DiskImageTrack track = getManagedTrack(sectorPos, sideNum, secNum, divNum, divNums);
        if (track == null) return null;
        if (trackNum != null) trackNum[0] = track.getTrackNumber();
        if (sectorNum != null) sectorNum[0] = secNum[0];
        return track.getSector(secNum[0]);
    }

    /// DISK BASICで使用できる残りディスクサイズに足りるか
    ///
    /// @param size 指定サイズ
    /// @return true 足りる / false 足りない
    public boolean hasFreeDiskSize(int size) {
        boolean enough = true;
        if (size > pDisk.getSizeWithoutHeader()) {
            errinfo.setError(DiskBasicError.ERR_FILE_TOO_LARGE);
            enough = false;
        } else if (size > type.getFreeDiskSize()) {
            errinfo.setError(DiskBasicError.ERR_NOT_ENOUGH_FREE);
            enough = false;
        } else if (!type.isEnoughFileSize(size)) {
            errinfo.setError(DiskBasicError.ERR_NOT_ENOUGH_FREE);
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
        if (pDisk == null) return -1.0;
        if (mAssigned) return 0.0;

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
        if (pDisk == null) return -1.0;
        if (mAssigned) return 1.0;
        return dir.checkRoot(type, diskBasicParam.getDirStartSector(), diskBasicParam.getDirEndSector(), isFormatting);
    }

    /// 現在選択しているディスクのルートディレクトリをアサイン
    ///
    /// @return false: ディレクトリにエラーあり
    public boolean assignRootDirectory() throws IOException {
        if (pDisk == null) return false;

        boolean valid = true;
        if (!mAssigned) {
            valid = dir.assignRoot(type, diskBasicParam.getDirStartSector(), diskBasicParam.getDirEndSector());
        } else {
            dir.setCurrentAsRoot();
        }
        if (valid) {
            type.calcDiskFreeSize(false);
        } else {
            type.clearDiskFreeSize();
        }
        mAssigned = valid;
        return valid;
    }

    /// 現在選択しているディスクのFATとルートディレクトリをアサイン
    ///
    /// @return true, false: エラーあり
    public boolean assignFatAndDirectory() throws IOException {
        boolean valid = (assignFat(false) >= 0.0);
        valid = valid && assignRootDirectory();
        return valid;
    }

    /** 解析済みをクリア */
    public void clearParseAndAssign(boolean forcely) {
        mParsed = false;
        mAssigned = false;
        mForcely = forcely;
        dir.releaseRoot(type);
        dir.setCurrentAsRoot();
    }

    /** 解析済みか */
    public boolean isParsed() {
        return mParsed;
    }

    /** アサイン済みか */
    public boolean isAssigned() {
        return mAssigned;
    }

    /** 解析エラーを無視するか */
    public boolean isForcely() {
        return mForcely;
    }

    /// ロードできるか
    ///
    /// @param item ディレクトリのアイテム
    public boolean isLoadableFile(DiskBasicDirItem<?> item) {
        if (item == null || !item.isLoadable() || !item.isUsed()) {
            errinfo.setError(DiskBasicError.ERRV_CANNOT_EXPORT, item.getFileNameStr());
            return false;
        }
        return true;
    }

    /// 指定したディレクトリ位置のファイルをロード
    ///
    /// @param itemNumber ディレクトリの位置
    /// @param dstPath    出力先パス
    public boolean loadFile(int itemNumber, String dstPath) {
        DiskBasicDirItem<?> item = dir.itemPtr(itemNumber);
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
            errinfo.setError(DiskBasicError.ERR_CANNOT_EXPORT);
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
            errinfo.setError(DiskBasicError.ERR_CANNOT_VERIFY);
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
            errinfo.setError(DiskBasicError.ERR_FILE_NOT_FOUND);
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

        if (!type.prepareToAccessFile(fileunitNum, item, istream, ostream, remain, gitems, errinfo)) {
            return -1;
        }

        int gidxEnd = gitems.size() - 1;
        for (int gidx = 0; gidx <= gidxEnd && remain[0] > 0 && rc == 0; gidx++) {
            DiskBasicGroupItem gitem = gitems.get(gidx);
            trackNum = gitem.track;
            sideNum = gitem.side;
            sectorStart = gitem.sectorStart;
            sectorEnd = gitem.sectorEnd;
            DiskImageTrack track = pDisk.getTrack(trackNum, sideNum);
            if (track == null) {
                errinfo.setError(DiskBasicError.ERRV_NO_TRACK, String.valueOf(gitem.group), java.lang.String.valueOf(trackNum), java.lang.String.valueOf(sideNum));
                rc = -1;
                break;
            }

            for (int sectorNum = sectorStart; sectorNum <= sectorEnd && remain[0] > 0; sectorNum++) {
                DiskImageSector sector = track.getSector(sectorNum);
                if (sector == null) {
                    errinfo.setError(DiskBasicError.ERRV_NO_SECTOR, String.valueOf(gitem.group), java.lang.String.valueOf(trackNum), java.lang.String.valueOf(sideNum), java.lang.String.valueOf(sectorNum));
                    rc = -1;
                    continue;
                }
                int bufsize = sector.getSectorSize();
                bufsize /= gitem.divNums;
                byte[] buf = sector.getSectorBuffer();
                int offset = bufsize * gitem.divNum;
                byte[] segment = Arrays.copyOfRange(buf, offset, offset + bufsize);

                bufsize = type.accessFile(fileunitNum, item, istream, ostream, segment, bufsize, remain[0], sectorNum, sectorEnd);
                if (bufsize < 0) {
                    if (bufsize == -2) {
                        errinfo.setError(DiskBasicError.ERRV_INVALID_SECTOR, String.valueOf(gitem.group), java.lang.String.valueOf(trackNum), java.lang.String.valueOf(sideNum), java.lang.String.valueOf(sectorNum), java.lang.String.valueOf(bufsize));
                        rc = -1;
                    } else {
                        errinfo.setError(DiskBasicError.ERRV_VERIFY_FILE, String.valueOf(gitem.group), java.lang.String.valueOf(trackNum), java.lang.String.valueOf(sideNum), java.lang.String.valueOf(sectorNum));
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
        DiskBasicDirItem<?> item = dir.findFile(dirItem, filename, isCompareCaseInsense(), excludeItem, nextItem);
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
        DiskBasicDirItem<?> item = dir.findFile(dirItem, targetItem, isCompareCaseInsense(), excludeItem, nextItem);
        if (item == null) {
            return 0;
        }
        return (item.isOverWritable() ? 1 : -1);
    }

    /** 書き込みできるか */
    public boolean isWritableIntoDisk() {
        errinfo.clear();
        if (pDisk == null) {
            errinfo.setError(DiskBasicError.ERR_UNSELECT_DISK);
            return false;
        }
        if (!type.supportWriting()) {
            errinfo.setError(DiskBasicError.ERR_WRITE_UNSUPPORTED);
            return false;
        }
        if (pDisk.isWriteProtected()) {
            errinfo.setError(DiskBasicError.ERR_WRITE_PROTECTED);
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
            errinfo.setError(DiskBasicError.ERR_CANNOT_IMPORT_DIRECTORY);
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
            errinfo.setError(DiskBasicError.ERR_CANNOT_IMPORT);
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
    public <T extends DirectoryT> boolean saveFile(String srcPath, DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nitem) {
        if (!isWritableIntoDisk()) return false;

        try (FileInputStream infile = new FileInputStream(srcPath)) {
            return saveFile(infile, dirItem, pitem, nitem);
        } catch (IOException e) {
            errinfo.setError(DiskBasicError.ERR_CANNOT_IMPORT);
            return false;
        }
    }

    /**
     * バッファデータをディスクイメージにセーブ
     *
     * @param buffer  データ
     * @param dirItem [in,out] セーブ先ディレクトリアイテム
     * @param pitem   [in,out] セーブ用のファイル名、属性を持っているディレクトリアイテム
     * @param nitem   [out] 確保したディレクトリアイテム
     * @return false: エラーあり
     */
    public <T extends DirectoryT> boolean saveFile(byte[] buffer, DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nitem) throws IOException {
        if (!isWritableIntoDisk()) return false;

        ByteArrayInputStream indata = new ByteArrayInputStream(buffer);
        return saveFile(indata, dirItem, pitem, nitem);
    }

    /**
     * ストリームデータをディスクイメージにセーブ
     *
     * @param istream ストリームバッファ
     * @param dirItem [in,out] セーブ先ディレクトリアイテム
     * @param pitem   [in,out] セーブ用のファイル名、属性を持っている仮ディレクトリアイテム
     * @param nitem   [out] 確保したディレクトリアイテム
     * @return false: エラーあり
     */
    public <T extends DirectoryT> boolean saveFile(InputStream istream, DiskBasicDirItem<T> dirItem, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nitem) throws IOException {
        DiskBasicDirItem[] nextItemArr = {null};
        DiskBasicDirItem<T> item = dir.findFile(dirItem, pitem, isCompareCaseInsense(), null, nextItemArr);

        boolean valid = true;

        if (item == null) {
            while ((item = dir.getEmptyItem(dirItem, pitem, nextItemArr)) == null) {
                if (dir.canExpand(dirItem)) {
                    valid = dir.expand(dirItem);
                } else {
                    valid = false;
                }
                if (!valid) {
                    errinfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
                    return false;
                }
            }
            item.setEndMark(nextItemArr[0]);
        } else {
            if (!this.deleteFile(item, false)) {
                return false;
            }
        }

        if (nitem != null) nitem[0] = item;

        item.clearData();
        item.copyItem(pitem);

        ByteArrayOutputStream otemp = new ByteArrayOutputStream();
        if (!type.convertDataForSave(item, istream, otemp)) {
            this.deleteFile(item, false);
            return false;
        }

        int[] fileSize = {0};
        DiskBasicGroups groupItems = new DiskBasicGroups();
        InputStream itemp = null;

        if (otemp.size() > 0) {
            itemp = new ByteArrayInputStream(otemp.toByteArray());
        } else {
            itemp = istream;
        }

        try {
            valid = saveData(itemp, pitem, item, groupItems, fileSize);
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
     * @param istream    ストリームバッファ
     * @param pitem      [in,out] ファイル名、属性を持っている仮ディレクトリアイテム
     * @param item       [in,out] 確保したディレクトリアイテム
     * @param groupItems [out] グループリスト
     * @param fileSize   [out] セーブしたファイルのサイズ
     */
    public <T extends DirectoryT> boolean saveData(InputStream istream, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T> item, DiskBasicGroups groupItems, int[] fileSize) throws IOException {
        boolean valid = true;
        int fileOffset = 0;

        for (int fileunitNum = 0; valid; fileunitNum++) {
            int[] isize = {item.getFileUnitSize(fileunitNum, istream, fileOffset)};
            if (isize[0] < 0) {
                break;
            }
            valid = saveUnitData(fileunitNum, istream, isize, pitem, item, groupItems, fileSize);
            fileOffset += isize[0];
        }
        return valid;
    }

    /**
     * ストリームデータをディスクイメージにセーブ
     *
     * @param fileunitNum ファイル番号
     * @param istream     ストリームバッファ
     * @param isize       バッファ内のセーブ対象データサイズ
     * @param pitem       [in,out] ファイル名、属性を持っている仮ディレクトリアイテム
     * @param item        [in,out] 確保したディレクトリアイテム
     * @param groupItems  [out] グループリスト
     * @param fileSize    [out] セーブしたファイルのサイズ
     */
    public <T extends DirectoryT> boolean saveUnitData(int fileunitNum, InputStream istream, int[] isize, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T> item, DiskBasicGroups groupItems, int[] fileSize) throws IOException {
        if (!type.prepareToSaveFile(istream, isize, pitem, item, errinfo)) {
            return false;
        }

        isize[0] = item.recalcFileSizeOnSave(istream, isize[0]);

//#ifndef DEBUG_DISK_FULL_TEST
        if (!hasFreeDiskSize(isize[0])) {
            return false;
        }
//#endif

        DiskBasicGroups[] gitems = {new DiskBasicGroups()};
        int rc = type.allocateUnitGroups(fileunitNum, item, isize[0], ALLOCATE_GROUPS_NEW, gitems);
        groupItems.add(gitems[0]);
        if (rc < 0) {
            errinfo.setError(DiskBasicError.ERR_DISK_FULL);
            return false;
        }

        int seqNum = 0;
        for (DiskBasicGroupItem gitem : gitems[0].getItems()) {
            for (int sectorNum = gitem.sectorStart; sectorNum <= gitem.sectorEnd; sectorNum++) {
                DiskImageSector sector = pDisk.getSector(gitem.track, gitem.side, sectorNum);
                if (sector == null) {
                    errinfo.setError(DiskBasicError.ERRV_NO_SECTOR, String.valueOf(gitem.group), java.lang.String.valueOf(gitem.track), java.lang.String.valueOf(gitem.side), java.lang.String.valueOf(sectorNum));
                    rc = -2;
                    continue;
                }
                int bufsize = sector.getSectorSize();
                bufsize /= gitem.divNums;
                byte[] buf = sector.getSectorBuffer();
                int offset = bufsize * gitem.divNum;
                // Since Java passes by value, we can't directly modify the sector buffer.
                // Assuming type.writeFile takes a buffer to write from and the caller handles updating the actual sector.
                byte[] segment = Arrays.copyOfRange(buf, offset, offset + buf.length);


                int lastSize = type.writeFile(item, istream, segment, bufsize, isize[0], sectorNum, gitem.group, gitem.next, gitem.sectorEnd, seqNum);

                // Here we would copy the modified 'segment' back into the original sector buffer.
                System.arraycopy(segment, 0, buf, offset, segment.length);

                isize[0] -= lastSize;
                fileSize[0] += lastSize;
                seqNum++;
            }
        }
        return (rc >= 0);
    }

    /** ファイルを削除できるか */
    public boolean isDeletableFiles() {
        errinfo.clear();
        if (type == null || !type.supportDeleting()) {
            errinfo.setError(DiskBasicError.ERR_DELETE_UNSUPPORTED);
            return false;
        }
        if (pDisk.isWriteProtected()) {
            errinfo.setError(DiskBasicError.ERR_WRITE_PROTECTED);
            return false;
        }
        return true;
    }

    /// 指定したファイルを削除できるか
    ///
    /// @param item     ディレクトリアイテム
    /// @param clearmsg エラーメッセージのバッファをクリアするか
    /// @return -1: エラー継続不可, 1: エラー継続可能
    public int isDeletableFile(DiskBasicDirItem<?> item, boolean clearmsg) {
        if (clearmsg) errinfo.clear();

        if (pDisk.isWriteProtected()) {
            errinfo.setError(DiskBasicError.ERR_WRITE_PROTECTED);
            return -1;
        }

        if (item == null) return 0;

        if (!item.isDeletable()) {
            errinfo.setError(DiskBasicError.ERRV_CANNOT_DELETE, item.getFileNameStr());
            return 1;
        }
        return 0;
    }

    /**
     * 指定したディレクトリが空か
     *
     * @param item       ディレクトリアイテム
     * @param groupItems [out] グループ番号一覧
     * @param clearmsg   エラーメッセージのバッファをクリアするか
     */
    public boolean isEmptyDirectory(DiskBasicDirItem<?> item, DiskBasicGroups groupItems, boolean clearmsg) throws IOException {
        if (clearmsg) errinfo.clear();

        item.getAllGroups(groupItems);

        if (item.isDirectory()) {
            if (!type.isEmptyDirectory(false, groupItems)) {
                errinfo.setError(DiskBasicError.ERRV_CANNOT_DELETE_DIRECTORY, item.getFileNameStr());
                return false;
            }
        }
        return true;
    }

    /// ファイルを削除
    ///
    /// @param item     ディレクトリアイテム
    /// @param clearmsg エラーメッセージのバッファをクリアするか
    /// @return true: 成功, false: 失敗
    public boolean deleteFile(DiskBasicDirItem<?> item, boolean clearmsg) throws IOException {
        if (item == null) return false;
        if (clearmsg) errinfo.clear();
        DiskBasicGroups groupItems = new DiskBasicGroups();
        item.getAllGroups(groupItems);
        return deleteFile(item, groupItems);
    }

    /// ファイルを削除
    ///
    /// @param item       ディレクトリアイテム
    /// @param groupItems グループ番号一覧
    /// @return true: 成功, false: 失敗
    public boolean deleteFile(DiskBasicDirItem<?> item, DiskBasicGroups groupItems) throws IOException {
        if (pDisk == null) return false;

        if (item == null) {
            errinfo.setError(DiskBasicError.ERR_FILE_NOT_FOUND);
            return false;
        }

        type.deleteGroups(groupItems);
        item.delete();

        if (!type.additionalProcessOnDeletedFile(item)) {
            errinfo.setError(DiskBasicError.ERRV_CANNOT_DELETE, item.getFileNameStr());
            return false;
        }

        item.emptyChildren();
        item.refresh();
        item.setModify();

        type.calcDiskFreeSize(true);
        type.releaseDirectoryItem(item);

        return true;
    }

    /// ファイル名や属性を更新できるか
    ///
    /// @param item    ディレクトリアイテム
    /// @param showmsg エラーメッセージをセットするか
    /// @return true: できる, false: できない
    public boolean canRenameFile(DiskBasicDirItem<?> item, boolean showmsg) {
        errinfo.clear();
        if (item == null) {
            if (showmsg) errinfo.setError(DiskBasicError.ERR_FILE_NOT_FOUND);
            return false;
        }
        if (pDisk.isWriteProtected()) {
            if (showmsg) errinfo.setError(DiskBasicError.ERR_WRITE_PROTECTED);
            return false;
        }
        if (!item.isFileNameEditable()) {
            if (showmsg) {
                String filename = item.getFileNameStr();
                errinfo.setError(DiskBasicError.ERRV_CANNOT_EDIT_NAME, filename);
            }
            return false;
        }
        return true;
    }

    /// ファイル名を更新
    ///
    /// @param item    ディレクトリアイテム
    /// @param newname ファイル名
    /// @return true
    public boolean renameFile(DiskBasicDirItem<?> item, String newname) throws IOException {
        if (item.isFileNameEditable()) {
            item.setFileNameStr(newname);
        }
        item.refresh();
        item.setModify();
        type.additionalProcessOnRenamedFile(item);
        return true;
    }

    /// 属性を更新
    ///
    /// @param item ディレクトリアイテム
    /// @param attr 属性値
    public boolean changeAttr(DiskBasicDirItem<?> item, DiskBasicDirItemAttr attr) throws IOException {
        if (attr.isRenameable()) {
            item.setOptionalName(attr.getFileName().getOptional());
            boolean sts = renameFile(item, attr.getFileName().getName());
            if (!sts) return sts;
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
        return (pDisk != null && mFormatted);
    }

    /** DISK BASIC用にフォーマットできるか */
    public boolean isFormattable() {
        errinfo.clear();
        boolean enable = (type != null);
        if (!enable) {
            errinfo.setError(DiskBasicError.ERR_CANNOT_FORMAT);
            return enable;
        }
        enable = type.supportFormatting();
        if (!enable) {
            errinfo.setError(DiskBasicError.ERR_FORMAT_UNSUPPORTED);
        }
        return enable;
    }

    /// ディスクを論理フォーマット
    ///
    /// @param data 機種依存データ（ボリューム名など）
    /// @return >0: ワーニング, 0: 正常, <0: エラーあり
    public int formatDisk(DiskBasicIdentifiedData data) throws IOException {
        errinfo.clear();
        if (pDisk == null) {
            errinfo.setError(DiskBasicError.ERR_CANNOT_FORMAT);
            return errinfo.getValid();
        }

        List<DiskImageTrack> tracks = pDisk.getTracks();
        if (tracks == null) {
            errinfo.setError(DiskBasicError.ERR_CANNOT_FORMAT);
            return errinfo.getValid();
        }

        mParsed = true;
        mAssigned = false;
        mForcely = false;

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
                    errinfo.setWarn(DiskBasicError.ERRV_NOTHING_IN_TRACK, track.getTrackNumber(), track.getSideNumber());
                }
                continue;
            }
            if (!isVariableSectorsPerTrack() && sectors.size() < getSectorsPerTrackOnBasic()) {
                errinfo.setWarn(DiskBasicError.ERRV_NUM_OF_SECTORS_IN_TRACK, track.getTrackNumber(), track.getSideNumber());
            }
            for (DiskImageSector sector : sectors) {
                type.fillSector(track, sector);
            }
        }

        if (!type.additionalProcessOnFormatted(data)) {
            errinfo.setError(DiskBasicError.ERR_FORMATTING);
            rc = false;
        }

        if (rc) {
            assignFatAndDirectory();
            mFormatted = true;
        }
        return errinfo.getValid();
    }

    /** ルートディレクトリを返す */
    public <T extends DirectoryT> DiskBasicDirItem<T> getRootDirectory() {
        return dir.getRootItem();
    }

    /// ルートディレクトリ内の一覧を返す
    ///
    /// @param dirItem nullable
    public <T extends DirectoryT> List<DiskBasicDirItem<T>> getRootDirectoryItems(DiskBasicDirItem<T>[] dirItem) {
        return dir.getRootItems(dirItem);
    }

    /** カレントディレクトリを返す */
    public <T extends DirectoryT> DiskBasicDirItem<T> getCurrentDirectory() {
        return dir.getCurrentItem();
    }

    /** カレントディレクトリ内の一覧を返す */
    public <T extends DirectoryT> List<DiskBasicDirItem<T>> getCurrentDirectoryItems(DiskBasicDirItem<T>[] dirItem) {
        return dir.getCurrentItems(dirItem);
    }

    /// ディレクトリをアサイン
    ///
    /// @param dirItem ディレクトリのアイテム
    public <T extends DirectoryT> boolean assignDirectory(DiskBasicDirItem<T> dirItem) throws IOException {
        if (pDisk == null) return false;
        return dir.assign(dirItem);
    }

    /// ディレクトリを読み直す
    ///
    /// @param dirItem ディレクトリのアイテム
    public <T extends DirectoryT> boolean reassignDirectory(DiskBasicDirItem<T> dirItem) throws IOException {
        if (pDisk == null) return false;
        return dir.reassign(dirItem);
    }

    /**
     * ディレクトリを変更
     *
     * @param dstItem [in,out] 移動先ディレクトリのアイテム
     */
    public <T extends DirectoryT> boolean changeDirectory(DiskBasicDirItem<T>[] dstItem) throws IOException {
        if (pDisk == null) return false;
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
    public <T extends DirectoryT> int makeDirectory(DiskBasicDirItem<T> dirItem, String filename, boolean ignoreDatetime, DiskBasicDirItem<T>[] nitem) throws IOException {
        if (!isWritableIntoDisk()) {
            return -1;
        }

        if (!canMakeDirectory()) {
            errinfo.setError(DiskBasicError.ERR_CANNOT_MAKE_DIRECTORY);
            return -1;
        }

        DiskBasicFileName dirName = new DiskBasicFileName();
        String[] filenameRef = {filename};
        // サブディレクトリを作成する前にディレクトリ名を編集する
        if (!type.renameOnMakingDirectory(filenameRef)) {
            errinfo.setError(DiskBasicError.ERR_CANNOT_MAKE_DIRECTORY);
            return -1;
        }
        dirName.setName(filenameRef[0]);

        // 同じファイル名があるか
        DiskBasicDirItem[] nextItem = {null};
        DiskBasicDirItem<T> item = dir.findFile(dirItem, dirName, isCompareCaseInsense(), null, nextItem);
        if (item != null) {
            errinfo.setError(DiskBasicError.ERR_FILE_ALREADY_EXIST);
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
                errinfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
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

        int[] sizeremain = {getSectorsPerGroup() * getSectorSize() * getSubDirGroupSize()};
        // 空きがあるか
        if (sizeremain[0] > getFreeDiskSize()) {
            // 空きが足りない
            errinfo.setError(DiskBasicError.ERR_DISK_FULL);
            // アイテムに削除マークを入れる
            this.deleteFile(item, false);
            return -1;
        }

        int[] fileSize = {0};

        int rc;

        // 必要なディスク領域を確保する
        DiskBasicGroups[] groupItems = {new DiskBasicGroups()};
        rc = type.allocateGroups(item, sizeremain[0], ALLOCATE_GROUPS_NEW, groupItems);
        if (rc < 0) {
            // 空きが足りない
            errinfo.setError(DiskBasicError.ERR_DISK_FULL);
            // 確保した領域を削除
            this.deleteFile(item, false);
            return -1;
        }

        // セクタに書き込む
        rc = type.initializeSectorsAsDirectory(groupItems[0], fileSize, sizeremain, errinfo);

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

    /// ディレクトリのサイズを拡張
    ///
    /// @param dirItem ディレクトリのエントリ
    public boolean expandDirectory(DiskBasicDirItem<?> dirItem) throws IOException {
        int[] sizeremain = {getSubDirGroupSize() * getSectorsPerGroup() * getSectorSize()};
        DiskBasicGroups[] groupItems = {new DiskBasicGroups()};
        int rc = type.allocateGroups(dirItem, sizeremain[0], ALLOCATE_GROUPS_APPEND, groupItems);
        if (rc < 0) {
            errinfo.setError(DiskBasicError.ERR_DISK_FULL);
            return false;
        }

        int[] fileSize = {dirItem.getFileSize()};
        rc = type.initializeSectorsAsDirectory(groupItems[0], fileSize, sizeremain, errinfo);
        if (rc < 0) {
            return false;
        }

        dirItem.setDirectorySize(fileSize[0]);
        DiskBasicDirItem<?> curItem = dir.findName(dirItem, ".", isCompareCaseInsense(), null, null);
        if (curItem != null) {
            curItem.setDirectorySize(fileSize[0]);
        }

        if (!type.additionalProcessOnExpandedDirectory(dirItem, groupItems[0], dir.getParentItem(dirItem))) {
            errinfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
            return false;
        }

        return true;
    }

    /** ディレクトリアイテムの作成 */
    public DiskBasicDirItem<?> createDirItem() throws IOException {
        return dir.newItem();
    }

    /// ディレクトリアイテムの作成
    ///
    /// @param sector セクタデータ
    /// @param secpos セクタ内の位置
    /// @param data   ディレクトリデータ
    /// @param dataP  ディレクトリデータのポインタ
    /// @return ディレクトリアイテム
    public <T extends DirectoryT> DiskBasicDirItem<T> createDirItem(DiskImageSector sector, int secpos, byte[] data, int dataP) throws IOException {
        return dir.newItem(sector, secpos, data, dataP);
    }

    /** ディレクトリアイテムの位置から開始セクタを返す */
    public DiskImageSector getSectorFromPosition(int position, int[] startGroup) {
        DiskBasicDirItem<?> item = dir.itemPtr(position);
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
        return pDisk.getSector(trackNum[0], sideNum[0], sectorStart[0]);
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
            itm.divNums = divNums[0];
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

    /// トラック、セクタの各番号からセクタ位置(トラック0セクタ1を0とした通し番号)を計算(グループ計算用)
    /// サイド番号はトラック番号に変換、トラック番号はサイド数の倍数となる
    ///
    /// 管理エリアがあれば飛ばす、開始グループ番号のオフセット分を引く などの機種依存を考慮
    ///
    /// @param trackNum  トラック番号
    /// @param sectorNum セクタ番号(サイド1のときは+トラック数)
    /// @return セクタ位置(トラック0のセクタ1を0とした位置)
    public int calcSectorPosFromNumTForGroup(int trackNum, int sectorNum) {
        if (trackNum >= skippedTrack) trackNum--;
        int sectorPos = type.getSectorPosFromNumT(trackNum, sectorNum);
        sectorPos -= dataStartSector;
        return sectorPos;
    }

    /// トラックを返す
    ///
    /// @param trackNum トラック番号
    /// @param sideNum  サイド番号
    /// @return トラックデータ
    public DiskImageTrack getTrack(int trackNum, int sideNum) {
        return pDisk.getTrack(trackNum, sideNum);
    }

    /// セクタ返す
    ///
    /// @param trackNum  トラック番号
    /// @param sideNum   サイド番号
    /// @param sectorNum セクタ番号
    /// @return セクタデータ
    public DiskImageSector getSector(int trackNum, int sideNum, int sectorNum) {
        return pDisk.getSector(trackNum, sideNum, sectorNum);
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
        if (numbering_sector == 1) sectorNum += (getSectorsPerTrackOnBasic() * sidNum);
        sidNum += getSideNumberBaseOnDisk();
        if (sideNum != null) sideNum[0] = sidNum;
        return pDisk.getSector(trackNum, sidNum, sectorNum);
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
        return pDisk.getTrack(trackNum[0], sideNum[0]);
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
        return pDisk.getSector(trackNum[0], sideNum[0], sectorNum[0], density);
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

    /// 開始セクタ番号を返す
    ///
    /// DiskBasicParamを優先
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
        return (pDisk != null);
    }

    /** DISKイメージを返す */
    public DiskImageDisk getDisk() {
        return pDisk;
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
    public <T extends DirectoryT> DiskBasicDir<T> getDir() {
        return dir;
    }

    /** TYPEクラス */
    public <T extends DirectoryT> DiskBasicType<T> getType() {
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
    public void invertMem(byte[] data, int len) {
        if (isDataInverted()) Common.mem_invert(data, len);
    }

    /** 必要ならデータを反転する */
    public void invertMem(byte[] src, int len, byte[] dst) {
        System.arraycopy(src, 0, dst, 0, len);
        if (isDataInverted()) Common.mem_invert(dst, len);
    }

    /** エンディアンを考慮した値を返す */
    public short orderUint16(short val) {
        return isBigEndian() ? Short.reverseBytes(val) : val;
    }

    /** エンディアンを考慮した値を返す */
    public int orderUint32(int val) {
        return isBigEndian() ? Integer.reverseBytes(val) : val;
    }

    /// DISK BASIC種類番号を返す
    public DiskBasicFormatType getFormatTypeNumber() {
        return getFormatType().getTypeNumber();
    }

    /** エラーメッセージ */
    public List<String> getErrorMessage(int maxrow) {
        return errinfo.getMessages(maxrow);
    }

    /// エラー有無
    ///
    /// @return <0: エラー, 0: 正常, 0>: ワーニング
    public int getErrorLevel() {
        return errinfo.getValid();
    }

    public DiskBasicError getErrinfo() {
        return errinfo;
    }

    /** エラーメッセージを表示 */
    public void showErrorMessage() {
        ResultInfo.showMessage(getErrorLevel(), getErrorMessage(20));
    }

    /** エラーメッセージをクリア */
    public void clearErrorMessage() {
        errinfo.clear();
    }
}
