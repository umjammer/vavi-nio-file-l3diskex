package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDateTime;
import java.util.List;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DirectoryOs9;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DirectoryOs9Fd;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DiskBasicDirItemOS9FD;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.Os9Date;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.Os9Lsn;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_NEW;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_DIRECTORY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_EXEC;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_READ;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_PUBLIC_WRITE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_EXEC;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_READ;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.FILETYPE_MASK_OS9_USER_WRITE;


/**
 * OS-9の処理
 * <p>
 * DiskBasicParam
 *
 * <li>SubDirGroupSize : サブディレクトリの初期グループ(LSN)数</li>
 * <li>GroupWidth      : ビットマップ1ビットのセクタ数(dd_BIT)</li>
 */
public class DiskBasicTypeOS9 extends DiskBasicType<DirectoryOs9> {

    private static final Logger logger = System.getLogger(DiskBasicTypeOS9.class.getName());

    /** OS-9 Ident LSN = 0(track1, sector1) */
    @Serdes
    static class Os9Ident {

        /** total lsn */
        @Element(sequence = 1)
        public Os9Lsn totalLsn = new Os9Lsn();
        /** sectors per track */
        @Element(sequence = 2)
        public byte sectorsPerTrack = 0;
        /** allocation map length */
        @Element(sequence = 3)
        public short mapLen = 0;
        /** sectors per group */
        @Element(sequence = 4)
        public short bit = 0;
        /** rootdir lsn */
        @Element(sequence = 5)
        public Os9Lsn rootDirLen = new Os9Lsn();
        /** owner id */
        @Element(sequence = 6)
        public short owner = 0;
        /** disk attr */
        @Element(sequence = 7)
        public byte attr = 0;
        /** disk ident */
        @Element(sequence = 8)
        public short disk = 0;
        /** format, density, number of sides */
        @Element(sequence = 9)
        public byte format = 0;
        /** sector per track */
        @Element(sequence = 10)
        public short sectorPerTrack = 0;
        @Element(sequence = 11)
        public short reserved1 = 0;
        /** bootstrap lsn */
        @Element(sequence = 12)
        public Os9Lsn bootstrapLsn = new Os9Lsn();
        /** bootstrap size (in bytes) */
        @Element(sequence = 13)
        public short bootstrapSize = 0;
        /** creation date */
        @Element(sequence = 14)
        public Os9Date cDate = new Os9Date();
        /** volume label */
        @Element(sequence = 15)
        public byte[] name = new byte[32];
        /** option */
        @Element(sequence = 16)
        public byte[] option = new byte[32];
        @Element(sequence = 17)
        public byte reserved2 = 0;
        /** media integrity code */
        @Element(sequence = 18)
        public int sync = 0;
        /** bitmap starting sector number */
        @Element(sequence = 19)
        public int mapLsn = 0;
        /** media logical sector size */
        @Element(sequence = 20)
        public short lsnSize = 0;
        /** version id */
        @Element(sequence = 21)
        public short version = 0;
    }

    /** OS-9 Allocation Map */
    static class OS9AllocMap extends DiskBasicBitMLMap {

        /** ビットマップサイズ(bytes) */
        private int mapBytes;
        /** 開始LSN */
        private int mapStartLsn;
        /** 最終LSN */
        private int endLsn;
        /** セクタサイズ */
        private int sectorSize;
        /** 1ビット当たりのセクタ数 */
        private int sectorsPerBit;

        public OS9AllocMap() {
            mapBytes = 0;
            mapStartLsn = 0;
            endLsn = 0;
            sectorSize = 0;
            sectorsPerBit = 0;
        }

        /**
         * Allocation Map を割当てる
         *
         * @param basic       Disk Basic パラメータ
         * @param mapStartLsn MapのあるLSN
         * @param mapBytes    Mapで使用するバイト数
         * @return false: セクタなし
         */
        public boolean allocMap(DiskBasic basic, int mapStartLsn, int mapBytes) {
            this.mapBytes = mapBytes;
            this.mapStartLsn = mapStartLsn;
            endLsn = basic.getFatEndGroup();
            sectorSize = basic.getSectorSize();
            sectorsPerBit = basic.getGroupWidth();

            if (sectorsPerBit <= 0) sectorsPerBit = 1;

            boolean valid = true;
            int bytes = 0;

            list.clear();

            int mapEndLsn = (endLsn / sectorSize / 8 / sectorsPerBit) + 1;
            for (int mapLsn = this.mapStartLsn; mapLsn <= mapEndLsn && mapLsn <= 32 && bytes < this.mapBytes; mapLsn++) {
                DiskImageSector sector = basic.getManagedSector(mapLsn);
                if (sector == null) {
                    // error
                    valid = false;
                    break;
                }
                byte[] buf = sector.getSectorBuffer();
                int size = sector.getSectorSize();

                addBuffer(buf, size);

                bytes += size;
            }
            return valid;
        }

        /**
         * Mapを元にして使用状況を作成する
         *
         * @param fat [out] 使用状況
         */
        public void makeAvailable(DiskBasicAvailability fat) {
            int bytes = 0;
            int lsn = 0;

            for (int mapIndex = 0; mapIndex < size() && bytes < mapBytes; mapIndex++) {
                byte[] buf = get(mapIndex).getBuffer();
                int size = get(mapIndex).getSize();

                for (int pos = 0; pos < size && lsn <= endLsn && bytes < mapBytes; pos++) {
                    for (int bit = 0; bit < 8 && lsn <= endLsn && bytes < mapBytes; bit++) {
                        boolean used = ((buf[pos] & (0x80 >> bit)) != 0);
                        for (int i = 0; i < sectorsPerBit && lsn <= endLsn; i++) {
                            if (!used) {
                                fat.add(FAT_AVAIL_FREE, sectorSize, 1);
                            } else {
                                fat.add(FAT_AVAIL_USED, 0, 0);
                            }
                            lsn++;
                        }
                    }
                    bytes++;
                }
            }
        }

        /**
         * LSNをMapにセット
         *
         * @param lsn LSN
         * @param val セット / リセット
         */
        public void setLSN(int lsn, boolean val) {
            lsn /= sectorsPerBit;

            modify(lsn, val);
        }

        /**
         * LSNを使用しているか
         *
         * @param lsn LSN
         * @return true 使用している
         */
        public boolean isUsedLSN(int lsn) {
            lsn /= sectorsPerBit;

            return isSet(lsn);
        }

        /**
         * 空いているLSNを得る
         *
         * @return LSN or INVALID_GROUP_NUMBER
         */
        public int findEmpty() {
            int matchLsn = INVALID_GROUP_NUMBER;
            int bytes = 0;
            int lsn = 0;
            for (int mapIndex = 0; mapIndex < size() && bytes < mapBytes && matchLsn == INVALID_GROUP_NUMBER; mapIndex++) {
                byte[] buf = get(mapIndex).getBuffer();
                int size = get(mapIndex).getSize();

                for (int pos = 0; pos < size && lsn <= endLsn && bytes < mapBytes && matchLsn == INVALID_GROUP_NUMBER; pos++) {
                    for (int bit = 0; bit < 8 && lsn <= endLsn && bytes < mapBytes && matchLsn == INVALID_GROUP_NUMBER; bit++) {
                        boolean used = ((buf[pos] & (0x80 >> bit)) != 0);
                        if (!used) {
                            matchLsn = lsn;
                            break;
                        }
                        lsn += sectorsPerBit;
                    }
                    bytes++;
                }
            }
            return matchLsn;
        }

        public int getMapStartLSN() {
            return mapStartLsn;
        }

        public int getMapBytes() {
            return mapBytes;
        }
    }

    /** Identification Sector */
    private Os9Ident os9Ident;

    /** Allocation Map */
    private final OS9AllocMap allocMap = new OS9AllocMap();

    public static final int FORMAT_TYPE_OS9 = 9;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_OS9;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryOs9> dir) {
        super.init(basic, fat, dir);

        os9Ident = null;
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 ~ 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;

        double validRatio = 1.0;

        // Ident
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) {
            return -1.0;
        }
        os9Ident = new Os9Ident();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), os9Ident);
        if (os9Ident == null) {
            return -1.0;
        }

        int iVal;

        // total groups
        int lVal = os9Ident.totalLsn.l;
        iVal = lVal;
        logger.log(Level.TRACE, "OS9: top: Total Sectors: %d".formatted(iVal));
        if (iVal < 1) {
            return -1.0;
        }
        iVal--;
        // 最終グループ番号
        basic.setFatEndGroup(iVal);

        // sectors per track
        iVal = os9Ident.sectorPerTrack;
        logger.log(Level.TRACE, "OS9: dd_SPT: Sectors per Track: %d".formatted(iVal));
        if (iVal == 0) {
            return -1.0;
        }
        if (iVal > basic.getSectorsPerTrack()) {
            logger.log(Level.TRACE, "OS9: %d > %d".formatted(iVal, basic.getSectorsPerTrack()));
            validRatio = 0.5;
        } else {
            basic.setSectorsPerTrackOnBasic(iVal);
        }

        // sectors per bit on bitmap table
        iVal = os9Ident.bit;
        logger.log(Level.TRACE, "OS9: dd_BIT: Sectors per Bit on Bitmap: %d".formatted(iVal));
        if (iVal == 0 || !Utils.IsPowerOfTwo(iVal, 16)) {
            return -1.0;
        }
        basic.setGroupWidth(iVal);

        // disk format
        iVal = os9Ident.format;
        String sVal = "";
        sVal = sVal + ((iVal & 1) != 0 ? "double side" : "single side");
        sVal = sVal + ((iVal & 2) != 0 ? ", double density" : ", single density");
        if ((iVal & 4) != 0) sVal = sVal + (", double track (96/135TPI)");
        if ((iVal & 8) != 0) sVal = sVal + (", quad track density (192TPI)");
        if ((iVal & 16) != 0) sVal = sVal + (", octal track density (384TPI)");
        logger.log(Level.TRACE, "OS9: dd_FMT: 0x%x (%s)".formatted(iVal, sVal));

        // tracks per side
        iVal = (basic.getFatEndGroup() + 1) / basic.getSectorsPerTrackOnBasic() / basic.getSidesPerDiskOnBasic();
        basic.setTracksPerSideOnBasic(iVal);

        // root directory
        int dirFdLsn = os9Ident.rootDirLen.getOs9Lsn();
        logger.log(Level.TRACE, "OS9: dd_DIR: LSN on Root Directory: %d".formatted(dirFdLsn));
        if (dirFdLsn > basic.getFatEndGroup()) {
            return -1.0;
        }
        sector = basic.getManagedSector(dirFdLsn);
        if (sector == null) {
            return -1.0;
        }
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);

        for (int i = 0; i < 48; i++) {
            int startLsn = fdd.segments[i].lsn.getOs9Lsn();
            int endLsn = fdd.segments[i].siz; // block size (in sectors)
            if (startLsn == 0 && endLsn == 0) {
                break;
            }
            endLsn += startLsn;

            if (i == 0) basic.setDirStartSector(startLsn);
            basic.setDirEndSector(endLsn);
        }

        // Allocation Map
        int mapLsn = os9Ident.mapLsn;
        int mapBytes = os9Ident.mapLen;
        logger.log(Level.TRACE, "OS9: dd_MapLSN: %d".formatted(mapLsn));
        if (!allocMap.allocMap(basic, mapLsn > 0 ? mapLsn : 1, mapBytes)) {
            return -1.0;
        }

        basic.setSectorsPerFat(mapBytes / basic.getSectorSize() + 1);

        return validRatio;
    }

    /** Allocation Mapの開始位置を得る（ダイアログ用) */
    @Override
    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int map_lsn = allocMap.getMapStartLSN();
        getNumFromSectorPos(map_lsn, trackNum, sideNum, sectorNum);
    }

    /** Allocation Mapの終了位置を得る（ダイアログ用) */
    @Override
    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int mapLsn = allocMap.getMapStartLSN();
        mapLsn += (allocMap.getMapBytes() / basic.getSectorSize());
        getNumFromSectorPos(mapLsn, trackNum, sideNum, sectorNum);
    }

    /** タイトル名（ダイアログ用） */
    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    /**
     * エリアをチェック
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double checkFat(boolean isFormatting) {
        return 1.0;
    }

    /**
     * ルートディレクトリをアサイン
     *
     * @param startSector 開始セクタ番号
     * @param endSector   終了セクタ番号
     * @param groupItems  [out] セクタリスト
     * @param dirItem     [in,out] ルートディレクトリアイテム
     */
    @Override
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryOs9> dirItem) throws IOException {
        boolean sts = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

        // FDセクタへのポインタをルートアイテムに設定
        DiskBasicDirItemOS9 dItem = (DiskBasicDirItemOS9) dirItem;

        int dirFdLsn = os9Ident.rootDirLen.getOs9Lsn();
        dItem.setStartGroup(0, dirFdLsn, 0);
        DiskBasicDirItemOS9FD fd = dItem.getFd();
        DiskImageSector sector = basic.getManagedSector(dirFdLsn);
        if (sector == null) return false;
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), fdd);
        fd.set(basic, sector, dirFdLsn, fdd);

        return sts;
    }

    /**
     * ルートディレクトリのセクタリストを計算
     *
     * @param startSector ディレクトリ開始セクタ番号
     * @param endSector   ディレクトリ終了セクタ番号
     * @param groupItems  [out] セクタリスト
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;

        // root directory
        int dirFdLsn = os9Ident.rootDirLen.getOs9Lsn();
        DiskImageSector sector = basic.getManagedSector(dirFdLsn);
        if (sector == null) {
            return false;
        }
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);
        if (fdd == null) {
            return false;
        }

        groupItems.clear();

        int dirSize = 0;
        for (int i = 0; i < 48 && valid; i++) {
            int startLsn = fdd.segments[i].lsn.getOs9Lsn();
            int endLsn = fdd.segments[i].siz; // block size (in sectors)
            if (startLsn == 0 && endLsn == 0) {
                break;
            }
            endLsn += startLsn;

            for (int lsn = startLsn; lsn < endLsn; lsn++) {
                int[] trackNum = {0};
                int[] sideNum = {0};
                sector = basic.getSectorFromGroup(lsn, trackNum, sideNum);
                if (sector == null) {
                    valid = false;
                    break;
                }
                groupItems.add(lsn, 0, trackNum[0], sideNum[0], sector.getSectorNumber(), sector.getSectorNumber());
                dirSize += sector.getSectorSize();
            }
        }
        groupItems.setSize(dirSize);

        return valid;
    }

    /** ディレクトリが空か */
    @Override
    public boolean isEmptyDirectory(boolean isRoot, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;
        boolean last = false;

        int indexNumber = 0;
        DiskBasicDirItem<DirectoryOs9> nitem = dir.newItem();
        for (int index = 0; index < groupItems.size(); index++) {
            DiskBasicGroupItem gItem = groupItems.get(index);
            int trackNum = gItem.track;
            int sideNum = gItem.side;
            DiskImageTrack track = basic.getTrack(trackNum, sideNum);
            if (track == null) {
                valid = false;
                break;
            }
            for (int sectorNum = gItem.sectorStart; sectorNum <= gItem.sectorEnd && valid && !last; sectorNum++) {
                DiskImageSector sector = track.getSector(sectorNum);
                //nItem.setSector(sector);
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

                int pos = 0;
                int size = sector.getSectorSize();

                // ディレクトリにファイルがないかのチェック
                while (valid && !last && pos < size) {
                    nitem.setData(indexNumber, gItem, sector, pos, buffer, bufferOffset, null);
                    // FDセクタを調べる
                    int startNsl = nitem.getStartGroup(0);
                    DiskImageSector fdSector = basic.getSectorFromGroup(startNsl);
                    if (fdSector != null) {
                        DirectoryOs9Fd fdBuf = new DirectoryOs9Fd();
                        byte[] b = fdSector.getSectorBuffer();
                        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdSector);
                        DiskBasicDirItemOS9FD fd = ((DiskBasicDirItemOS9) nitem).getFd();
                        fd.set(basic, fdSector, startNsl, fdBuf);
                        if (nitem.isNormalFile()) {
                            valid = !nitem.checkUsed(last);
                        }
                    }
                    pos += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    indexNumber++;
                }
            }
        }

        return valid;
    }

    /**
     * ディレクトリエリアのサイズに達したらアサイン終了するか
     *
     * @param pos        [in,out] ディレクトリの位置
     * @param size       [in,out] ディレクトリのセクタサイズ
     * @param sizeRemain [in,out] ディレクトリの残りサイズ
     * @return 0: 終了しない, 1: 強制的に未使用とする アサインは継続
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        // サイズに達したら以降は未使用とする
        return sizeRemain[0] <= 0 ? 1 : 0;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize();
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        // Allocation Mapを調べる
        allocMap.makeAvailable(fatAvailability);

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectoryOs9>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (DiskBasicDirItem<DirectoryOs9> item : items) {
                if (item == null || !item.isUsed()) continue;

                // グループ番号のマップを調べる
                int groupCount = item.getGroupCount();
                if (groupCount > 0) {
                    DiskBasicGroupItem groupItem = item.getGroup(groupCount - 1);
                    int groupNum = groupItem.group;
                    if (groupNum <= basic.getFatEndGroup()) {
                        fatAvailability.set(groupNum, DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        //freeDiskSize = fSize;
        //freeGroups = groups;
    }

    /**
     * 使用状態を設定する
     *
     * @param num グループ番号(0...)
     * @param val 1:使用中, 0:空きにする
     */
    @Override
    public void setGroupNumber(int num, int val) {
        allocMap.setLSN(num, val != 0);
    }

    /** グループ番号を得る */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /**
     * FAT位置が使用されているか
     *
     * @param num グループ番号(0...)
     */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return allocMap.isUsedLSN(num);
    }

    /** 次のグループ番号を得る */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * 空き位置を返す
     *
     * @return INVALID_GROUP_NUMBER: 空きなし
     */
    @Override
    public int getEmptyGroupNumber() {
        // Allocation Mapを調べる
        return allocMap.findEmpty();
    }

    /**
     * 次の空き位置を返す 未使用
     *
     * @return INVALID_GROUP_NUMBER: 空きなし
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * ファイルをセーブする前の準備を行う
     *
     * @param iStream   ストリームバッファ
     * @param fileSize [in,out] 出力サイズ
     * @param pItem     [in,out] ファイル名、属性を持っているディレクトリアイテム
     * @param nItem     [in,out] 確保したディレクトリアイテム
     * @param errInfo   [in,out] エラー情報
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryOs9> pItem, DiskBasicDirItem<DirectoryOs9> nItem, DiskBasicError errInfo) throws IOException {
        // FDセクタを確保する
        int lsn = getEmptyGroupNumber();
        if (lsn == INVALID_GROUP_NUMBER) {
            return false;
        }
        DiskImageSector sector = basic.getSectorFromGroup(lsn);
        if (sector == null) {
            return false;
        }
        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return false;
        }
        // FDセクタをセット
        nItem.setChainSector(sector, lsn, buf, pItem);

        // 開始LSNを設定
        nItem.setStartGroup(0, lsn);

        // セクタを予約
        setGroupNumber(lsn, 1);

        return true;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileUnitNum ファイル番号
     * @param item        [in,out] ディレクトリアイテム
     * @param dataSize    確保するデータサイズ（バイト）
     * @param flags       新規か追加か
     * @param groupItems  [out] 確保したセクタリスト
     * @return >0: 正常 -1: 空きなし (開始グループ設定前) -2: 空きなし (開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryOs9> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) {
        DiskBasicDirItemOS9 ditem = (DiskBasicDirItemOS9) item;
        DiskBasicDirItemOS9FD fd = ditem.getFd();

        int segmentIndex = -1;
        if (flags == ALLOCATE_GROUPS_APPEND) {
            // 追加の場合、既にあるセグメントを計算
            segmentIndex = 48;
            for (int index = 0; index < 48; index++) {
                if (fd.getLsn(index) == 0 && fd.getSize(index) == 0) {
                    segmentIndex = index - 1;
                    break;
                }
            }
            if (segmentIndex >= 48) {
                // セグメントに空きなし
                return -1;
            }
        }
        int startSegmentIndex = segmentIndex + 1;

        int fileSize = fd.getSize();
        dataSize += fileSize;

        // 新規作成で dd_BIT が 2 以上のとき
        boolean isFirstLsn = (flags == ALLOCATE_GROUPS_NEW && basic.getGroupWidth() > 1);

        // データ用のセクタを確保する
        int rc = 0;
        int lsn = 0;
        int prevLsn = 0;
        int segmentCount = 0;
        int limit = basic.getFatEndGroup() + 1;
        while (fileSize < dataSize && limit >= 0 && rc == 0) {
            int start = 0;
            if (isFirstLsn) {
                // 新規作成で dd_BIT が 2 以上のときはFDセクタの空きからデータを書き込んでいく
                lsn = fd.getMyLSN() + 1;
                start++;
                isFirstLsn = false;
            } else {
                lsn = getEmptyGroupNumber();
            }
            if (lsn == INVALID_GROUP_NUMBER) {
                // 空きなし？
                rc = -2;
                break;
            }
            if (prevLsn == 0 || (prevLsn + 1) != lsn) {
                // LSN が連続していない
                segmentIndex++;
                if (segmentIndex >= 48) {
                    // セグメント限界
                    rc = -2;
                    break;
                }
                fd.setLsn(segmentIndex, lsn);
                segmentCount = (basic.getGroupWidth() - start);
                fd.setSize(segmentIndex, segmentCount);
            } else {
                // LSN が連続しているなら、同じセグメントでセクタ数を増やす
                segmentCount += basic.getGroupWidth();
                fd.setSize(segmentIndex, segmentCount);
            }
            // セクタを予約
            setGroupNumber(lsn, 1);
            // グループ追加
            for (int i = start; i < basic.getGroupWidth(); i++) {
                basic.getNumsFromGroup(lsn, 0, basic.getSectorSize(), 0, groupItems[0]);
                lsn++;
            }
            // LSNを保持
            prevLsn = lsn - 1;

            if (fileSize + basic.getSectorSize() * (basic.getGroupWidth() - start) > dataSize) {
                fileSize = dataSize;
            } else {
                fileSize += basic.getSectorSize() * (basic.getGroupWidth() - start);
            }
            // ファイルサイズ
            fd.setSize(fileSize);

            limit--;
        }
        if (limit < 0) {
            rc = -2;
        }

        // エラーの場合、確保したエリアを開放
        if (rc < 0) {
            for (int index = startSegmentIndex; index < 48; index++) {
                int segmentLsn = fd.getLsn(index);
                int segmentSize = fd.getSize(index);
                if (segmentLsn == 0 && segmentSize == 0) {
                    break;
                }
                for (int size = 0; size < segmentSize; size++) {
                    // セクタを未使用にする
                    if ((segmentLsn / basic.getGroupWidth()) != (fd.getMyLSN() / basic.getGroupWidth()))
                        setGroupNumber(segmentLsn, 0);
                    segmentLsn++;
                }
                fd.setLsn(index, 0);
                fd.setSize(index, 0);
            }
        }

        return rc;
    }

    /** グループ番号からセクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    /** グループ番号から最終セクタ番号を得る */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return groupNum;
    }

    /** データ領域の開始セクタを計算 */
    @Override
    public int calcDataStartSectorPos() {
        return basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
    }

    /** ルートディレクトリか */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return ((groupNum + 1) <= basic.getDirStartSector());
    }

    /** サブディレクトリを作成できるか */
    @Override
    public boolean canMakeDirectory() {
        return true;
    }

    /** ルートディレクトリのサイズを拡張できるか */
    @Override
    public boolean canExpandRootDirectory() {
        return true;
    }

    /** サブディレクトリのサイズを拡張できるか */
    @Override
    public boolean canExpandDirectory() {
        return true;
    }

    /**
     * サブディレクトリを作成する前の準備を行う
     *
     * @param item 確保したディレクトリアイテム
     */
    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem<DirectoryOs9> item) throws IOException {
        // FDセクタを確保する
        int lsn = getEmptyGroupNumber();
        if (lsn == INVALID_GROUP_NUMBER) {
            return false;
        }
        DiskBasicDirItemOS9 dItem = (DiskBasicDirItemOS9) item;
        DiskImageSector sector = basic.getSectorFromGroup(lsn);

        DiskBasicDirItemOS9FD fd = dItem.getFd();
        DirectoryOs9Fd fdd = new DirectoryOs9Fd();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), fdd);
        fd.set(basic, sector, lsn, fdd);
        fd.clear();

        // 開始LSNを設定
        dItem.setStartGroup(0, lsn, 0);
        // 日付を設定
        LocalDateTime tm = LocalDateTime.now();
        dItem.setFileCreateDateTime(tm);
        dItem.setFileModifyDateTime(tm);
        // セクタを予約
        setGroupNumber(lsn, 1);

        return true;
    }

    /** サブディレクトリを作成した後の個別処理 */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryOs9> item, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryOs9> parentItem) throws IOException {
        if (groupItems.size() <= 0) return;

        // ディレクトリ属性
        item.setFileAttr(basic.getFormatTypeNumber(), 0,
                FILETYPE_MASK_OS9_DIRECTORY |
                        FILETYPE_MASK_OS9_PUBLIC_EXEC |
                        FILETYPE_MASK_OS9_PUBLIC_WRITE |
                        FILETYPE_MASK_OS9_PUBLIC_READ |
                        FILETYPE_MASK_OS9_USER_EXEC |
                        FILETYPE_MASK_OS9_USER_WRITE |
                        FILETYPE_MASK_OS9_USER_READ
        );

        // ファイルサイズはエントリ２つ分
        item.setFileSize(item.getDataSize() * 2);

        // カレントと親ディレクトリのエントリを作成する
        DiskBasicGroupItem grouItem = groupItems.get(0);

        DiskImageSector sector = basic.getTrack(grouItem.track, grouItem.side).getSector(grouItem.sectorStart); // Simplified access

        byte[] buf = sector.getSectorBuffer();
        int bufOffset = 0;
        DiskBasicDirItem<DirectoryOs9> newItem = basic.createDirItem(sector, 0, buf, bufOffset);

        // 親をつくる
        newItem.clearData();
        if (parentItem != null) {
            // 親がサブディレクトリ
            newItem.setStartGroup(0, parentItem.getStartGroup(0));
        } else {
            // 親がルート
            newItem.setStartGroup(0, os9Ident.rootDirLen.getOs9Lsn());
        }
        newItem.setFileNamePlain("..");
//        newItem.setFileAttr(FILE_TYPE_DIRECTORY_MASK);

        // カレント
        bufOffset += newItem.getDataSize();
        newItem.setData(0, null, sector, 0, buf, bufOffset, null);

        newItem.clearData();
        newItem.setStartGroup(0, item.getStartGroup(0));
        newItem.setFileNamePlain(".");
//        newItem.setFileAttr(FILE_TYPE_DIRECTORY_MASK);

        // ディレクトリサイズを更新
        int dirSize = dir.calcSize();
        DiskBasicDirItem<DirectoryOs9> dirItem = item.getParent();
        if (dirItem != null) {
            dirItem.setFileSize(dirSize);
        }
    }

    /** セクタデータを指定コードで埋める */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    /** セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // Ident
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) return false;
        os9Ident = new Os9Ident();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), os9Ident);
        if (os9Ident == null) return false;

        //int totalLsn = basic.getFatEndGroup() + 1;
        int totalLsn = (basic.getTracksPerSide() - basic.getManagedTrackNumber()) * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
        // 最終グループ番号
        basic.setFatEndGroup(totalLsn - 1);

        int mapLsn = 1;
        int rootStartLsn = basic.getDirStartSector() - 1;
        int rootEndLsn = basic.getDirEndSector() - 1;
        int iVal;

        //
        // OS9 Identifier をセット
        //

        sector.fill((byte) 0);

        // total lsn
        os9Ident.totalLsn.setOs9Lsn(totalLsn);
        // sectors per track
        os9Ident.sectorsPerTrack = (byte) basic.getSectorsPerTrackOnBasic();
        // allocation map length
        iVal = (totalLsn + 7) / 8;
        os9Ident.mapLen = (short) iVal;
        // sectors per group
        iVal = basic.getGroupWidth();
        os9Ident.bit = (short) iVal;
        // rootdir lsn
        os9Ident.rootDirLen.setOs9Lsn(rootStartLsn);
        // owner id
        os9Ident.owner = 0;
        // disk attr
        os9Ident.attr = 0;
        // disk ident
        os9Ident.disk = (short) 0;

        // format, density, number of sides
        iVal = ((basic.getSidesPerDiskOnBasic() - 1) & 0x01);
        iVal |= (basic.hasSingleDensity(null ,null) == 1 ? 0 : 0x02);
        os9Ident.format = (byte) iVal;

        // sector per track
        iVal = basic.getSectorsPerTrackOnBasic();
        os9Ident.sectorPerTrack = (short) iVal;

        // bootstrap lsn
        os9Ident.bootstrapLsn.setOs9Lsn(0);
        // bootstrap size (in bytes)
        os9Ident.bootstrapSize = (short) 0;

        // creation date
        LocalDateTime tm = LocalDateTime.now();
        os9Ident.cDate.yy = (byte) (tm.getYear() % 100);
        os9Ident.cDate.mm = (byte) (tm.getMonth().ordinal() + 1);
        os9Ident.cDate.dd = (byte) tm.getDayOfMonth();
        os9Ident.cDate.hh = (byte) tm.getHour();
        os9Ident.cDate.mi = (byte) tm.getMinute();

        // bitmap starting sector number
        os9Ident.mapLsn = mapLsn - 1;

        // volume label
        setIdentifiedData(data);

        // 固有パラメータ設定
        basic.assignParameter();

        //
        // Allocation Mapを作成
        //

        for (int lsn = mapLsn; lsn < rootStartLsn; lsn++) {
            sector = basic.getManagedSector(lsn);
            if (sector == null) return false;
            sector.fill((byte) 0xff);
        }
        for (int lsn = rootEndLsn + 1; lsn < totalLsn; lsn++) {
            setGroupNumber(lsn, 0);
        }

        //
        // ルートディレクトリを作成
        //

        sector = basic.getManagedSector(rootStartLsn);
        if (sector == null) return false;

        DiskBasicDirItemOS9 rootItem = (DiskBasicDirItemOS9) dir.newItem();
        DiskBasicDirItemOS9FD rootFd = rootItem.getFd();

        DirectoryOs9Fd fdd = new DirectoryOs9Fd(); // Mock
        rootFd.set(basic, sector, rootStartLsn, fdd);
        rootFd.clear();

        rootItem.setStartGroup(0, rootStartLsn, 0);
        // 日付を設定
        rootItem.setFileCreateDateTime(tm);
        rootItem.setFileModifyDateTime(tm);
        // セグメント設定
        rootFd.setLsn(0, rootStartLsn + 1);
        rootFd.setSize(0, rootEndLsn - rootStartLsn);
        // リンクの数
        rootFd.setLinkCount((short) 2);
        // セクタを予約
        setGroupNumber(rootStartLsn, 1);

        for (int lsn = rootStartLsn + 1; lsn <= rootEndLsn; lsn++) {
            sector = basic.getManagedSector(lsn);
            if (sector == null) {
                continue;
            }
            sector.fill((byte) 0);
        }
        DiskBasicGroups groupItems = new DiskBasicGroups();
        basic.getNumsFromGroup(rootStartLsn + 1, 0, basic.getSectorSize(), basic.getSectorSize(), groupItems);
        additionalProcessOnMadeDirectory(rootItem, groupItems, null);

        return true;
    }

    /** データの書き込み終了後の処理 */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryOs9> item) {
        // ディレクトリサイズを更新
        int dirSize = dir.calcSize();
        //DiskBasicDirItem dirItem = dir.findName(".", null, null);
        DiskBasicDirItem<DirectoryOs9> dirItem = item.getParent();
        if (dirItem == null) {
            // Why?
            return;
        }
        dirItem.setFileSize(dirSize);
    }

    /** FAT領域を削除する */
    @Override
    public void deleteGroupNumber(int groupNum) {
        // 未使用にする
        setGroupNumber(groupNum, 0);
    }

    /** ファイル削除後の処理 */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryOs9> item) {
        // FDセクタを未使用にする
        setGroupNumber(item.getStartGroup(0), 0);

        // ディレクトリサイズを更新
        int dirSize = dir.calcSize();
        //DiskBasicDirItem dirItem = dir.findName(".", null, null);
        DiskBasicDirItem<DirectoryOs9> dirItem = item.getParent();
        if (dirItem == null) {
            // Why?
            return true;
        }
        dirItem.setFileSize(dirSize);

        return true;
    }

    /** IPLや管理エリアの属性を得る */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume label
        byte[] buf = new byte[os9Ident.name.length + 1];
        DiskBasicDirItemOS9.decodeString(buf, os9Ident.name.length, os9Ident.name, os9Ident.name.length);
        String volume = new String(buf, 0, os9Ident.name.length);
        data.setVolumeName(volume);
        data.setVolumeNameMaxLength(os9Ident.name.length);
    }

    /** IPLや管理エリアの属性をセット */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat format = basic.getFormatType();

        // volume label
        if (format.hasVolumeName()) {
            String vol = data.getVolumeName();
            DiskBasicDirItemOS9.encodeString(os9Ident.name, os9Ident.name.length, vol, vol.length());
        }
    }
}
