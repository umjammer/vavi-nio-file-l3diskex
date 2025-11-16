package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.DirectoryProDos;
import l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.ProDOSDirPointer;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_PRODOS;
import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_ACCESS_ALL;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_CHANGE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SAPLING;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SUBDIR;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SUBVOL;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_TREE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_VOLUME;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_APPEND;


/**
 * Apple ProDos 8 / 16 の処理
 */
public class DiskBasicTypeProDOS extends DiskBasicType<DirectoryProDos> {

    private static final Logger logger = System.getLogger(DiskBasicTypeProDOS.class.getName());

    /**
     * Apple ProDos ビットマップ
     */
    static class ProDosBitmap extends DiskBasicBitMLMap {

        int groupNum;

        public ProDosBitmap() {
            super();
            groupNum = INVALID_GROUP_NUMBER;
        }

        /**
         * ポインタをセット
         */
        public void addBitmap(DiskImageSector sector) {
            super.addBuffer(
                    sector.getSectorBuffer(),
                    sector.getSectorSize()
            );
        }

        /**
         * 指定位置のビットを変更する
         *
         * @param groupNum 位置
         * @param use      セットする場合 {@code true}
         */
        @Override
        public void modify(int groupNum, boolean use) {
            super.modify(groupNum, !use); // 逆転
        }

        /**
         * 指定位置が空いているか
         *
         * @param group_num 位置
         * @return 空いている場合 {@code true}
         */
        public boolean isFree(int group_num) {
            return super.isSet(group_num); // 逆転
        }


        /** ブロック番号をセット */
        void setMyGroupNumber(int group_num) {
            this.groupNum = group_num;
        }

        /** ブロック番号を得る */
        int getMyGroupNumber() {
            return groupNum;
        }
    }

    private final DiskBasicSectorSkew sectorSkew = new DiskBasicSectorSkew();
    private final ProDosBitmap bitmap = new ProDosBitmap();
    private DirectoryProDos volume;
    private final DiskBasicSectorPosTrans sectorMap = new DiskBasicSectorPosTrans();

    @Override
    public boolean isSupported(DiskBasicFormatType typeNumber) {
        return typeNumber == FORMAT_TYPE_PRODOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryProDos> dir) {
        super.init(basic, fat, dir);
        this.volume = null;

        // ProDOS 8のときは、セクタ→ブロックマップを作成
        if (basic.getTracksPerSideOnBasic() <= 40) {
            // ProDOS 8
            sectorSkew.create(basic, basic.getSectorsPerTrackOnBasic());
        }
    }

    /**
     * エリアをチェック
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double valid_ratio = 1.0;

        // ビットマップ
        int groupNum = bitmap.getMyGroupNumber();
        int startPos = getStartSectorFromGroup(groupNum);
        int endPos = getEndSectorFromGroup(groupNum, INVALID_GROUP_NUMBER, startPos, 0, 0);
        for (int s = startPos; s <= endPos; s++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(s);
            if (sector == null) {
                return -1.0;
            }
            bitmap.addBitmap(sector);
        }
        basic.setSectorsPerFat(bitmap.size());

        return valid_ratio;
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 ~ 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0;

        double validRatio = 1.0;

        // 可変数セクタなのでトラックごとのセクタ数を集計
        sectorMap.create(basic);

        // セクタ数の合計
        int calcTotalBlocks = sectorMap.getTotalSectors() / basic.getSectorsPerGroup();
        basic.setFatEndGroup(calcTotalBlocks - 1);

        // ボリュームディレクトリ
        DiskImageSector sector = basic.getSectorFromGroup(basic.getDirStartSector() / basic.getSectorsPerGroup());
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer(4);
        DirectoryProDos vol = new DirectoryProDos();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vol);
        if ((vol.aux.v.entryLen & 0xff) != DirectoryProDos.SIZE) {
            return -1.0;
        }

        int storeTotalBlocks = vol.aux.v.totalBlocks & 0xffff;

        if (storeTotalBlocks > calcTotalBlocks) {
            validRatio -= 0.5;
        }

        basic.setFatEndGroup(storeTotalBlocks - 1);

        bitmap.setMyGroupNumber(vol.aux.v.bitmapPointer & 0xffff);

        this.volume = vol; // Store reference

        return validRatio;
    }

    /**
     * Allocation Mapの開始位置を得る（ダイアログ用）
     */
    @Override
    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int groupNum = bitmap.getMyGroupNumber();
        int startPos = getStartSectorFromGroup(groupNum);
        getNumFromSectorPos(startPos, trackNum, sideNum, sectorNum, null, null);
    }

    /**
     * Allocation Mapの終了位置を得る（ダイアログ用）
     */
    @Override
    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int groupNum = bitmap.getMyGroupNumber();
        int startPos = getStartSectorFromGroup(groupNum);
        int endPos = getEndSectorFromGroup(groupNum, INVALID_GROUP_NUMBER, startPos, 0, 0);
        getNumFromSectorPos(endPos, trackNum, sideNum, sectorNum, null, null);
    }

    /**
     * タイトル名（ダイアログ用）
     */
    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    /**
     * ルートディレクトリをアサイン
     */
    @Override
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryProDos> dirItem) throws IOException {
        boolean status = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

        // ボリュームヘッダの内容をコピーする
        DiskBasicGroupItem gItem = groupItems.get(0);
        DiskImageSector sector = basic.getSector(gItem.track, gItem.side, gItem.sectorStart);
        byte[] volume = sector.getSectorBuffer(4);
        dirItem.copyData(volume);
        // ディレクトリ属性にしておく
        dirItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);
        // ブロック番号を設定
        dirItem.setStartGroup(0, basic.getDirStartSector() / basic.getSectorsPerGroup());

        return status;
    }

    /**
     * ルートディレクトリのセクタリストを計算
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;

        groupItems.clear();

        // ディレクトリのチェインをたどる
        int dirSize = 0;
        int limit = basic.getDirEndSector() - basic.getDirStartSector() + 1;
        int[] trackNum = {0};
        int[] sideNum = {0};
        int sectorNum = 1;

        // 開始セクタ
        int sectorPos = basic.getDirStartSector();
        //volDir.Empty();

        while (valid && limit >= 0) {
            ProDOSDirPointer next = new ProDOSDirPointer();
            next.nextBlock = 0;

            int groupNum = sectorPos / basic.getSectorsPerGroup();
            //volDir.Add((int) groupNum);

            for (int ss = 0; ss < basic.getSectorsPerGroup(); ss++) {
                DiskImageSector sector = basic.getSectorFromSectorPos(sectorPos, trackNum, sideNum);
                if (sector == null) {
                    valid = false;
                    break;
                }
                sectorNum = sector.getSectorNumber();
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    valid = false;
                    break;
                }
                if (ss == 0) {
                    // 次のブロックへのポインタを保持
                    Serdes.Util.deserialize(new ByteArrayInputStream(buffer), next);
                }

                groupItems.add(groupNum, 0, trackNum[0], sideNum[0], sectorNum, sectorNum);

                dirSize += sector.getSectorSize();
                sectorPos++;
            }

            if (!valid) break;

            // 次のセクタなし
            if (next.nextBlock == 0) {
                break;
            }

            sectorPos = next.nextBlock * basic.getSectorsPerGroup();

            limit--;
        }
        groupItems.setSize(dirSize);

        if (limit < 0) {
            valid = false;
        }

        return valid;
    }

    /**
     * ディレクトリエリアのサイズに達したらアサイン終了するか
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        // サイズに達したら以降は未使用とする
        int blockSize = basic.getSectorSize() * basic.getSectorsPerGroup();
        return ((sizeRemain[0] % blockSize) < DirectoryProDos.SIZE ? -1 : 0);
    }

    /**
     * セクタをディレクトリとして初期化
     */
    @Override
    public int initializeSectorsAsDirectory(DiskBasicGroups groupItems, int[] fileSize, int[] sizeRemain, DiskBasicError errInfo) {
        fileSize[0] = groupItems.size() * basic.getSectorSize();
        sizeRemain[0] = 0;

        return 0;
    }

    /**
     * 使用可能なディスクサイズを得る
     */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * 残りディスクサイズを計算
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        //int fSize = 0;
        //int groups = 0;

        fatAvailability.clear();

        // BITMAP table
        for (int group = 0; group <= basic.getFatEndGroup(); group++) {
            if (group <= 2) {
                fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
            } else if (group == bitmap.getMyGroupNumber()) {
                fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
            } else if (bitmap.isFree(group)) {
                fatAvailability.add(FAT_AVAIL_FREE, basic.getSectorSize() * basic.getSectorsPerGroup(), 1);
            } else {
                fatAvailability.add(FAT_AVAIL_USED, 0, 0);
            }
        }
        // Volume directory
        DiskBasicDirItem<DirectoryProDos> root = dir.getRootItem();
        if (root != null) {
            DiskBasicGroups rootGroups = root.getGroups();
            if (rootGroups != null) {
                for (int i = 0; i < rootGroups.size(); i++) {
                    fatAvailability.set(i, FAT_AVAIL_SYSTEM);
                }
            }
        }

        //freeDiskSize = (int) fSize;
        //freeGroups = (int) groups;
    }

    /**
     * グループ番号を使用済みにする
     */
    @Override
    public void setGroupNumber(int num, int val) {
        bitmap.modify(num, val != 0);
    }

    /**
     * グループ番号を得る
     */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /**
     * FAT位置が使用されているか
     */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /**
     * 次のグループ番号を得る
     */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * 空き位置を返す
     */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;

        for (int group = 3; group <= basic.getFatEndGroup(); group++) {
            if (bitmap.isFree(group)) {
                newNum = group;
                break;
            }
        }

        return newNum;
    }

    /**
     * 次の空き位置を返す
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        // 次の空き位置候補
        return getEmptyGroupNumber();
    }

    /**
     * ファイルをセーブする前の準備を行う
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryProDos> pItem, DiskBasicDirItem<DirectoryProDos> nItem, DiskBasicError errInfo) throws IOException {
        // チェインセクタをクリア
        nItem.clearChainSector(null);

        return true;
    }

    /**
     * チェインセクタを確保する
     */
    private int allocChainSector(int index, DiskBasicDirItem<DirectoryProDos> item) {
        int groupNum = getEmptyGroupNumber();
        if (groupNum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // セクタ
        int startPos = getStartSectorFromGroup(groupNum);
        int ebdPos = getEndSectorFromGroup(groupNum, INVALID_GROUP_NUMBER, startPos, 0, 0);
        for (int sec = startPos; sec <= ebdPos; sec++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(sec);
            if (sector == null) {
                return INVALID_GROUP_NUMBER;
            }
            sector.fill((byte) 0);
        }

        // チェイン情報にセクタをセット
        item.setChainSector(groupNum, startPos, null, null);

        // 開始グループを設定
        if (index == 0) {
            item.setStartGroup(0, groupNum, 1);
        }
        // セクタを予約
        setGroupNumber(groupNum, 1);

        return groupNum;
    }

    /**
     * データサイズ分のグループを確保する
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryProDos> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        //logger.log(Level.TRACE, "DiskBasicTypeProDOS::AllocateGroups {");

        //int fileSize = 0;
        int groups = 0;

        int rc = 0;
        int sectorSize = basic.getSectorSize();
        int blockSize = sectorSize * basic.getSectorsPerGroup();
        int remain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        int chainIndex = 0;

        DiskBasicFileType attr = item.getFileAttr();
        int sType = (attr.getOrigin() >> 16) & 0xff;
        if (sType == FILETYPE_MASK_PRODOS_SUBDIR) {
            // サブディレクトリ
            while (remain > 0 && limit >= 0) {
                // 空きをさがす
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // 使用済みにする
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);

                if (chainIndex == 0 && flags != ALLOCATE_GROUPS_APPEND) {
                    item.setStartGroup(0, groupNum, 1);
                }
                chainIndex++;

                //file_size += blockSize;
                groups++;
                remain -= blockSize;
                limit--;
            }
            if (rc == 0 && flags == ALLOCATE_GROUPS_APPEND) {
                // 追加のときはチェインをつなぐ
                if (groupItems[0].size() > 0) {
                    rc = chainDirectoryGroups(item, groupItems);
                }
            }
        } else if (sType == FILETYPE_MASK_PRODOS_SAPLING) {
            // 131Kバイト未満はインデックス１つ
            if (allocChainSector(0, item) == INVALID_GROUP_NUMBER) {
                return -1;
            }
            while (remain > 0 && limit >= 0) {
                // 空きをさがす
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // 使用済みにする
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);

                // チェインセクタも更新
                item.addChainGroupNumber(chainIndex, groupNum);

                chainIndex++;

                //			file_size += blockSize;
                groups++;
                remain -= blockSize;
                limit--;
            }
        } else if (sType == FILETYPE_MASK_PRODOS_TREE) {
            // 131Kバイト以上 ツリー
            int chainPIndex = 0;
            chainIndex = 256;
            // チェインセクタを確保
            if (allocChainSector(0, item) == INVALID_GROUP_NUMBER) {
                return -1;
            }
            chainPIndex++;
            while (remain > 0 && limit >= 0) {
                // チェインセクタを確保
                if ((chainIndex % 256) == 0) {
                    int chainGroupNum = allocChainSector(chainPIndex, item);
                    if (chainGroupNum == INVALID_GROUP_NUMBER) {
                        return -1;
                    }
                    // ルートチェインセクタと結びつける
                    item.addChainGroupNumber(chainPIndex, chainGroupNum);
                    chainPIndex++;
                }
                // 空きをさがす
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // 使用済みにする
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);

                // チェインセクタも更新
                item.addChainGroupNumber(chainIndex, groupNum);

                chainIndex++;

                //fileSize += blockSize;
                groups++;
                remain -= blockSize;
                limit--;
            }
        } else {
            //　512バイト以下
            while (remain > 0 && limit >= 0) {
                // 空きをさがす
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }

                // 使用済みにする
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remain, groupItems[0]);
                setGroupNumber(groupNum, 1);

                if (chainIndex == 0) {
                    item.setStartGroup(0, groupNum, 1);
                }
                chainIndex++;

                //fileSize += blockSize;
                groups++;
                remain -= blockSize;
                limit--;
            }
        }

        if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }

        //logger.log(Level.TRACE, "rc: %d }".formatted(rc));
        return rc;
    }

    /**
     * グループをつなげる
     *
     * @return 0 正常
     */
    public int chainDirectoryGroups(DiskBasicDirItem<DirectoryProDos> item, DiskBasicGroups[] groupItems) throws IOException {
        DiskBasicGroups originalGroupItems = new DiskBasicGroups();
        item.getAllGroups(originalGroupItems);
        originalGroupItems.add(groupItems[0]);
        groupItems[0] = originalGroupItems;

        // ディレクトリチェインを再作成
        int groupNum = INVALID_GROUP_NUMBER;
        int prevGroupNum = INVALID_GROUP_NUMBER;
        ProDOSDirPointer prev = null;
        for (int i = 0; i < groupItems[0].size(); i++) {
            DiskBasicGroupItem gitem = groupItems[0].get(i);
            if (gitem.group != groupNum) {
                groupNum = gitem.group;
                DiskImageSector sector = basic.getSectorFromGroup(groupNum);
                ProDOSDirPointer curr = new ProDOSDirPointer();
                byte[] b = sector.getSectorBuffer();
                Serdes.Util.deserialize(new ByteArrayInputStream(b), curr);

                curr.prevBlock = prevGroupNum != INVALID_GROUP_NUMBER ? (short) prevGroupNum : 0;

                if (prev != null) {
                    prev.nextBlock = (short) groupNum;
                }

                prev = curr;
                prevGroupNum = groupNum;
            }
        }

        return 0;
    }

//    /** データの読み込み/比較処理 */
//    public int accessFile(...) {}

    /**
     * ファイルの最終セクタのデータサイズを求める
     */
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryProDos> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorSize, int remainSize) {
        return remainSize;
    }

    /**
     * グループ番号からセクタ番号を得る
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum * basic.getSectorsPerGroup();
    }

    /**
     * グループ番号から最終セクタ番号を得る
     */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return (groupNum + 1) * basic.getSectorsPerGroup() - 1;
    }

    /**
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る
     */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum /* = null */, int[] numOfDivs /* = null */) {
        //int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int[] sectorsPerTrack = {sidesPerDisk};

        // セクタ位置がどのトラックにあるか
        sectorMap.getNumFromSectorPos(sectorPos, trackNum, sectorNum, sectorsPerTrack);

        //if (selectedSide >= 0) {
        //    // 1S
        //	  trackNum[0] = sectorPos / sectorsPerTrack;
        //	  sideNum[0] = selected_side;
        //} else {
        //    // 2D, 2HD
        //	  trackNum[0] = sectorPos / sectorsPerTrack / sidesPerDisk;
        //	  sideNum[0] = (sectorPos / sectorsPerTrack) % sidesPerDisk;
        //}
        //sectorNum[0] = (sectorPos % sectorsPerTrack);

        // サイド番号
        sideNum[0] = sectorNum[0] * sidesPerDisk / sectorsPerTrack[0];

        // 連番でない場合
        if (numberingSector != 1) {
            sectorNum[0] = sectorNum[0] % (sectorsPerTrack[0] / sidesPerDisk);
        }

        // マッピング
        sectorNum[0] = sectorSkew.toPhysical(sectorNum[0]);

        // サイド番号を逆転するか
        sideNum[0] = basic.getReversedSideNumber(sideNum[0]);

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();

        if (divNum != null) divNum[0] = 0;
        if (numOfDivs != null) numOfDivs[0] = 1;
    }

    /**
     * 論理セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、セクタの各番号を得る
     */
    @Override
    public void getNumFromSectorPosS(int sectorPos, int[] trackNum, int[] sectorNum) {
        int[] sectorsPerTrack = {1};

        sectorMap.getNumFromSectorPos(sectorPos, trackNum, sectorNum, sectorsPerTrack);

        // マッピング
        sectorNum[0] = sectorSkew.toPhysical(sectorNum[0]);

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();
    }

    /**
     * トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        //int selectedSide = basic.getSelectedSide();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int numberingSector = basic.getNumberingSector();
        int[] sectorsPerTrack = {1};
        //int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        // マッピング
        sectorNum = sectorSkew.toLogical(sectorNum);

        // サイド番号を逆転するか
        sideNum = basic.getReversedSideNumber(sideNum);

        int sectorPos = sectorMap.getSectorPosFromNum(trackNum, sectorNum, sectorsPerTrack);

        // 連番でない場合
        if (numberingSector != 1) {
            sectorPos += sideNum * sectorsPerTrack[0] / sidesPerDisk;
        }

        //if (selectedSide >= 0) {
        //	  // 1S
        //	  sectorPos = trackNum * sectorsPerTrack + sectorNum;
        //} else {
        //	  // 2D, 2HD
        //	  sectorPos = trackNum * sectorsPerTrack * sidesPerDisk;
        //	  sectorPos += (sideNum % sidesPerDisk) * sectorsPerTrack;
        //	  sectorPos += sectorNum;
        //}
        return sectorPos;
    }

    /**
     * トラック、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     */
    @Override
    public int getSectorPosFromNumS(int trackNum, int sectorNum) {
        //int selectedSide = basic.getSelectedSide();
        //int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        //int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        int[] sectorsPerTrack = {1};

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        // マッピング
        sectorNum = sectorSkew.toLogical(sectorNum);

        sectorPos = sectorMap.getSectorPosFromNum(trackNum, sectorNum, sectorsPerTrack);

        return sectorPos;
    }

    /**
     * ルートディレクトリか
     */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return false;
    }

    /**
     * サブディレクトリを作成する前にディレクトリ名を編集する
     */
    @Override
    public boolean renameOnMakingDirectory(String[] dirName) {
        // 名前が空は作成不可
        if (dirName[0].isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * サブディレクトリを作成した後の個別処理
     */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryProDos> item, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryProDos> parentItem) throws IOException {
        if (groupItems.size() <= 0) return;

        int block_size = basic.getSectorSize() * basic.getSectorsPerGroup();

        DiskBasicDirItemProDOS dItem = (DiskBasicDirItemProDOS) item;
        DiskBasicDirItemProDOS parentDItem = (DiskBasicDirItemProDOS) parentItem;

        // ディレクトリの最初のブロックをセット
        item.setParentGroup(parentItem.getStartGroup(0));
        // バージョンはヘッダと合わせる
        dItem.setVersion(parentDItem.getVersion());

        // サブボリュームヘッダのエントリを作成する

        DiskBasicGroupItem gItem = groupItems.get(0);

        DiskImageSector sector = basic.getSector(gItem.track, gItem.side, gItem.sectorStart);

        byte[] buf = sector.getSectorBuffer(4);
        DiskBasicDirItem<DirectoryProDos> newitem = basic.createDirItem(sector, 0, buf, 0);
        DiskBasicDirItemProDOS newditem = (DiskBasicDirItemProDOS) newitem;

        newitem.copyData(item.getRawData());
        newitem.setFileAttr(FORMAT_TYPE_PRODOS, 0, (FILETYPE_MASK_PRODOS_SUBVOL << 16) | (0x75 << 8) | (FILETYPE_MASK_PRODOS_ACCESS_ALL & ~FILETYPE_MASK_PRODOS_CHANGE));
        newitem.setStartGroup(0, 0);
        newitem.setFileSize(0);
        // バージョン
        newditem.setVersion(parentDItem.getVersion());

        byte[] b = sector.getSectorBuffer(4);
        DirectoryProDos vol = new DirectoryProDos();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vol);

        // エントリのサイズ
        vol.aux.sv.entryLen = DirectoryProDos.SIZE;
        // ブロック内のファイルエントリ数
        vol.aux.sv.entriesPerBlock = (byte) ((block_size - 4) / DirectoryProDos.SIZE);
        // ファイルエントリ数
        vol.aux.sv.fileCount = 0;

        int parentStartBlock = parentItem.getStartGroup(0);
        int itemNumber = item.getNumber();
        int parentPointer = (itemNumber / (vol.aux.sv.entriesPerBlock & 0xff)) + parentStartBlock;
        int parentEntry = itemNumber % (vol.aux.sv.entriesPerBlock & 0xff);

        // 親のブロック番号
        vol.aux.sv.parentPointer = (short) parentPointer;
        // 親エントリ
        vol.aux.sv.parentEntry = (byte) parentEntry;

        // 親エントリのサイズ
        vol.aux.sv.parentEntryLen = DirectoryProDos.SIZE;
    }

//    /** フォーマット時セクタデータを指定コードで埋める */
//    public void fillSector(DiskImageTrack track, DiskImageSector sector)

    /**
     * フォーマット時セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        // ボリュームディレクトリをクリア
        int startPos = basic.getDirStartSector();
        int endPos = (basic.getDirEndSector() / basic.getSectorsPerGroup() + 1) * basic.getSectorsPerGroup() - 1;
        for (int s = startPos; s <= endPos; s++) {
            sector = basic.getSectorFromSectorPos(s);
            if (sector == null) {
                // Why?
                return false;
            }
            sector.fill((byte) 0);
        }

        // ボリュームディレクトリのチェインを作成
        int startBlock = startPos / basic.getSectorsPerGroup();
        int endBlock = endPos / basic.getSectorsPerGroup();
        //volDir.empty();
        for (int block = startBlock; block <= endBlock; block++) {
            sector = basic.getSectorFromGroup(block);
            ProDOSDirPointer p = new ProDOSDirPointer();
            byte[] b = sector.getSectorBuffer();
            try {
                Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
            } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
                // Why?
                return false;
            }
            int nextBlock = (block != endBlock ? block + 1 : 0);
            int prevBlock = (block != startBlock ? block - 1 : 0);
            p.nextBlock = (short) nextBlock;
            p.prevBlock = (short) prevBlock;
            //volDir.add(block);
        }

        int blockSize = basic.getSectorSize() * basic.getSectorsPerGroup();

        // ボリュームヘッダを作成
        sector = basic.getSectorFromGroup(startBlock);
        DirectoryProDos vol = new DirectoryProDos();
        byte[] b = sector.getSectorBuffer(4);
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vol);
        // 属性
        vol.sTypeAndNLen = (byte) (FILETYPE_MASK_PRODOS_VOLUME << 4);
        // 日時
        LocalDateTime tm = LocalDateTime.now();
        DiskBasicDirItemProDOS.convDateFromTm(tm, vol.cDate);
        DiskBasicDirItemProDOS.convTimeFromTm(tm, vol.cTime);
        // アクセス
        vol.access = (byte) (FILETYPE_MASK_PRODOS_ACCESS_ALL & ~FILETYPE_MASK_PRODOS_CHANGE);
        // エントリのサイズ
        vol.aux.v.entryLen = (byte) DirectoryProDos.SIZE;
        // ブロック内のファイルエントリ数
        vol.aux.v.entriesPerBlock = (byte) ((blockSize - 4) / DirectoryProDos.SIZE);
        // ファイルエントリ数
        vol.aux.v.fileCount = 0;
        // ビットマップポインタ
        int bitmapPointer = 6;
        vol.aux.v.bitmapPointer = (short) bitmapPointer;
        // トータルブロック数
        int totalBlocks = (basic.getSidesPerDisk() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup());
        vol.aux.v.totalBlocks = (short) totalBlocks;

        this.volume = vol;

        basic.setFatEndGroup(totalBlocks - 1);

        // ビットマップポインタを設定
        bitmap.list.clear();
        startPos = getStartSectorFromGroup(bitmapPointer);
        endPos = getEndSectorFromGroup(bitmapPointer, INVALID_GROUP_NUMBER, startPos, 0, 0);
        for (int s = startPos; s <= endPos; s++) {
            sector = basic.getSectorFromSectorPos(s);
            if (sector == null) {
                // Why?
                return false;
            }
            sector.fill((byte) 0);
            bitmap.addBitmap(sector);
        }
        bitmap.setMyGroupNumber(bitmapPointer);
        for (int block = endBlock + 1; block < totalBlocks; block++) {
            bitmap.modify(block, false);
        }
        bitmap.modify(bitmapPointer, true);

        // volume name
        setIdentifiedData(data);

        return true;
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryProDos> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                iStream.readNBytes(buffer, 0, remain);
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        return len;
    }

    /**
     * データの書き込み終了後の処理
     */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryProDos> item) {
        DiskBasicDirItemProDOS dItem = (DiskBasicDirItemProDOS) item;

        // ディレクトリのヘッダにあるファイル数を＋１する
        DiskBasicDirItem<DirectoryProDos> parent = item.getParent();
        if (parent == null) {
            // Why?
            return;
        }
        List<DiskBasicDirItem<DirectoryProDos>> children = parent.getChildren();
        if (children == null) {
            // Why?
            return;
        }
        DiskBasicDirItemProDOS vol = (DiskBasicDirItemProDOS) children.getFirst();
        if (vol == null) {
            // Why?
            return;
        }
        vol.increaseFileCount();
        // ディレクトリの最初のブロックをセット
        item.setParentGroup(parent.getStartGroup(0));
        // バージョンはヘッダと合わせる
        dItem.setVersion(vol.getVersion());
    }

    /**
     * FAT領域を削除する
     */
    @Override
    public void deleteGroupNumber(int groupNum) {
        // 未使用にする
        setGroupNumber(groupNum, 0);
    }

    /**
     * ファイル削除後の処理
     */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryProDos> item) throws IOException {
        // チェインセクタを未使用にする
        item.clearChainSector(null);

        // ディレクトリのヘッダにあるファイル数を－１する
        DiskBasicDirItem<DirectoryProDos> parent = item.getParent();
        if (parent == null) {
            // Why?
            return true;
        }
        List<DiskBasicDirItem<DirectoryProDos>> children = parent.getChildren();
        if (children == null) {
            // Why?
            return true;
        }
        DiskBasicDirItemProDOS vol = (DiskBasicDirItemProDOS) children.getFirst();
        if (vol == null) {
            // Why?
            return true;
        }
        vol.decreaseFileCount();

        return true;
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume name
        if (volume != null) {
            int len = (volume.sTypeAndNLen & 0xf);
            String volumeName = new String(volume.name, 0, len);
            data.setVolumeName(volumeName);
            data.setVolumeNameMaxLength(volume.name.length);
        }
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat format = basic.getFormatType();

        // volume name
        if (volume != null && format.hasVolumeName()) {
            byte[] volumeName = data.getVolumeName().getBytes();
            int len = volume.name.length;
            if (len > volumeName.length) len = volumeName.length;

            System.arraycopy(volumeName, 0, volume.name, 0, len);

            volume.sTypeAndNLen = (byte) ((len & 0xf) | (volume.sTypeAndNLen & 0xf0));
        }
    }
}