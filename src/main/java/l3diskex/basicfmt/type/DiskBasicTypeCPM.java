package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.DirectoryCpm;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_CPM;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.SECTOR_UNIT_CPM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * CP/Mの処理
 *
 * <li>AttributesByExtension バイナリとして扱う拡張子</li>
 */
public class DiskBasicTypeCPM extends DiskBasicType<DirectoryCpm> {

    /** ソフトセクタスキュー */
    protected DiskBasicSectorSkew sectorSkew = new DiskBasicSectorSkew();

    @Override
    public boolean isSupported(DiskBasicFormatType typeNumber) {
        return typeNumber == FORMAT_TYPE_CPM;
    }

    /** */
    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryCpm> dir) {
        super.init(basic, fat, dir);

        sectorSkew.create(basic, basic.getSectorsPerTrackOnBasic());
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 ~ 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        // 最終グループ番号
        if (basic.getFatEndGroup() == 0) {
            int maxGroup = (basic.getTracksPerSide() - basic.getManagedTrackNumber()) * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup() - 1;
            basic.setFatEndGroup(maxGroup);
        }

        if (isFormatting) return 1.0;

        // 最終グループ番号が最大値を超えていないか？
        if ((1L << (basic.getGroupWidth() * 8)) <= basic.getFatEndGroup()) {
            return -1.0;
        }

        // セクタ０
        DiskImageSector sector = basic.getSector(0, 0, 1);
        if (sector == null) {
            return -1.0;
        }

        // 最初のセクタに識別文字がある場合はその文字列が含まれるかで判断
        double validRatio = 0.5;
        int found = -1;
        byte[] iStr = null;
        for (int i = 0; i < 1; i++) {
            found = -1;
            String paramStr = switch (i) {
                case 0 -> basic.getVariousStringParam("IdentString");
                case 1 -> basic.getVariousStringParam("IPLString");
                default -> null;
            };
            if (paramStr != null && !paramStr.isEmpty()) {
                iStr = paramStr.getBytes(StandardCharsets.ISO_8859_1);
            }
            if (iStr != null && iStr.length > 0) {
                found = sector.find(iStr, iStr.length);
            }
            if (found >= 0) {
                validRatio = 1.0;
                break;
            }
        }

        return validRatio;
    }

    /**
     * エリアをチェック
     */
    @Override
    public double checkFat(boolean is_formatting) {
        return 1.0;
    }

    /**
     * ルートディレクトリをアサイン
     */
    @Override
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryCpm> dirItem) throws IOException {
        boolean sts = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

        // エクステント 同じファイル名 を関連付ける
        List<DiskBasicDirItem<DirectoryCpm>> sortItems = dirItem.getChildren();
        sortItems.sort(DiskBasicDirItemCPM::compare);
        DiskBasicDirItem<DirectoryCpm> prevItem = null;
        for (DiskBasicDirItem<DirectoryCpm> item : sortItems) {
            if (!item.isUsed()) continue;
            if (prevItem != null) {
                int cmparison = DiskBasicDirItemCPM.compareName(item, prevItem);
                if (cmparison == 0) {
                    // 一つ前と同じ名前なら、ポインタをセット
                    ((DiskBasicDirItemCPM) prevItem).setNextItem(item);
                    // このアイテムはリストに表示しない
                    item.visible(false);
                }
            }
            prevItem = item;
        }

        // ファイルサイズを計算
        for (DiskBasicDirItem<DirectoryCpm> sortItem : sortItems) {
            DiskBasicDirItemCPM cItem = (DiskBasicDirItemCPM) sortItem;
            if (cItem.isUsedAndVisible()) {
                cItem.calcFileUnitSize(0);
            }
        }

        return sts;
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
        fatAvailability.empty();
//        fatAvailability.SetCount(basic.getFatEndGroup() + 1, FAT_AVAIL_FREE);

        List<DiskBasicDirItem<DirectoryCpm>> items = dir.getCurrentItems(null);
        for (DiskBasicDirItem<DirectoryCpm> item : items) {
            if (item == null || !item.isUsed()) continue;

            // グループ番号のマップを調べる
            DiskBasicGroups groups = item.getGroups();
            int count = groups.size();
            for (int n = 0; n < count; n++) {
                DiskBasicGroupItem group = groups.get(n);
                int groupNum = group.group;
                if (groupNum <= basic.getFatEndGroup()) {
                    if (n + 1 == count) {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED_LAST);
                    } else {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED);
                    }
                }
            }
        }

        // 空きをチェック
        int groups = 0;
        long dirArea = ((basic.getDirEndSector() - basic.getDirStartSector() + 1L) / basic.getSectorsPerGroup());
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (pos < dirArea) {
                // ディレクトリエリアは使用済み
                fatAvailability.set(pos, FAT_AVAIL_SYSTEM);
            } else if (fatAvailability.get(pos) == FAT_AVAIL_FREE) {
                groups++;
            }
        }

        int fSize = groups * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(fSize);
        fatAvailability.setFreeGroups(groups);
    }

    /**
     * FAT位置をセット
     */
    @Override
    public void setGroupNumber(int num, int val) {
        fatAvailability.set(num, val != 0 ? FAT_AVAIL_USED : FAT_AVAIL_FREE);
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
        int groupNum = INVALID_GROUP_NUMBER;
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (fatAvailability.get(pos) == FAT_AVAIL_FREE) {
                groupNum = pos;
                break;
            }
        }
        return groupNum;
    }

    /**
     * 次の空き位置を返す 未使用
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * データサイズ分のグループを確保する
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryCpm> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int rc = 0;

        DiskBasicDirItemCPM dItem = (DiskBasicDirItemCPM) item;
        int groupEntries = dItem.getGroupEntries();

        int startGroupPos = 0;
        if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
            // 追加の時、空きエントリをさがす
            while (dItem.getGroupNumber(startGroupPos) != 0) {
                startGroupPos++;
                if ((startGroupPos % groupEntries) == 0) {
                    // グループエントリ数に達したら次のディレクトリエントリに移動
                    dItem = dItem.getNextItem();
                    if (dItem == null) {
                        // 次がない
                        break;
                    }
                }
            }
        }
        if (dItem == null) {
            return -1;
        }

        int groupPos = startGroupPos;
        int groupSize = basic.getSectorSize() * basic.getSectorsPerGroup();
        int remainSize = dataSize;
        int fileSize = 0;
        int limit = basic.getFatEndGroup() + 1;
        while (remainSize > 0 && limit >= 0 && rc == 0) {
            int groupNum = getEmptyGroupNumber();
            if (groupNum == INVALID_GROUP_NUMBER) {
                rc = -2;
                break;
            }
            basic.getNumsFromGroup(groupNum, 0, basic.getSectorSize(), remainSize, groupItems[0]);
            // 使用中にする
            setGroupNumber(groupNum, 1);
            // グループエントリ
            dItem.setGroupNumber((groupPos % groupEntries), groupNum);

            fileSize += (remainSize < groupSize ? remainSize : groupSize);
            // エクステント番号とレコード番号をセット
            dItem.calcExtentAndRecordNumber(fileSize);

            remainSize -= groupSize;
            limit--;

            groupPos++;
            if (remainSize > 0 && (groupPos % groupEntries) == 0) {
                // グループエントリ数に達したら次のディレクトリエントリに移動
                dItem = dItem.getNextItem();
                if (dItem == null) {
                    // 次がない！？
                    rc = -2;
                    break;
                }
            }
        }
        if (limit < 0) {
            rc = -2;
        }
        if (rc < 0) {
            deleteGroups(groupItems[0]);
            // グループエントリを削除
            dItem = (DiskBasicDirItemCPM) item;
            groupPos = 0;
            while (dItem.getGroupNumber(groupPos) != 0) {
                if (groupPos >= startGroupPos) {
                    dItem.setGroupNumber(groupPos, 0);
                }
                groupPos++;
                if ((groupPos % groupEntries) == 0) {
                    // グループエントリ数に達したら次のディレクトリエントリに移動
                    dItem = dItem.getNextItem();
                    if (dItem == null) {
                        break;
                    }
                }
            }
        }
        return rc;
    }

    /**
     * ファイルの最終セクタのデータサイズを求める
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryCpm> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) throws IOException {
        // ファイルサイズはセクタサイズ境界なので要計算
        if (item.needCheckEofCode()) {
            // 終端コードの1つ前までを出力
            byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
            for (int len = 0; len < remainSize; len++) {
                if (sectorBuffer[len] == eofCode) {
                    remainSize = len;
                    break;
                }
            }
        } else {
            // 計算手段がないので残りサイズをそのまま返す
            if (iStream != null) {
                // 比較時は、比較先のファイルサイズ
                int streamLength = iStream.available() % sectorSize;
            }
        }
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
        int val = sectorStart;
        if (remainSize < (sectorSize * basic.getSectorsPerGroup())) {
            val += ((remainSize - 1) / sectorSize);
        } else {
            val += (basic.getSectorsPerGroup() - 1);
        }
        return val;
    }

    /**
     * データ領域の開始セクタを計算
     */
    @Override
    public int calcDataStartSectorPos() {
        return getSectorPosFromNumS(basic.getManagedTrackNumber(), basic.getDirStartSector());
    }

    /**
     * セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る
     */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] numOfDivs) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();

        if (selectedSide >= 0) {
            // 1S
            trackNum[0] = sectorPos / sectorsPerTrack;
            sideNum[0] = selectedSide;
        } else {
            // 2D, 2HD
            trackNum[0] = sectorPos / sectorsPerTrack / sidesPerDisk;
            sideNum[0] = (sectorPos / sectorsPerTrack) % sidesPerDisk;
        }
        sectorNum[0] = sectorPos % sectorsPerTrack;

        // マッピング
        sectorNum[0] = sectorSkew.toPhysical(sectorNum[0]);

        if (numberingSector == 1) {
            // トラックごとに連番の場合
            sectorNum[0] += sideNum[0] * sectorsPerTrack;
        }

        // サイド番号を逆転するか
        sideNum[0] = basic.getReversedSideNumber(sideNum[0]);

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();

        if (divNum != null) divNum[0] = 0;
        if (numOfDivs != null) numOfDivs[0] = 1;
    }

    /**
     * トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る
     */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        // サイド番号を逆転するか
        sideNum = basic.getReversedSideNumber(sideNum);

        // 連番の場合
        if (numberingSector == 1) {
            sectorNum = sectorNum % sectorsPerTrack;
        }

        // マッピング
        sectorNum = sectorSkew.toLogical(sectorNum);

        if (selectedSide >= 0) {
            // 1S
            sectorPos = trackNum * sectorsPerTrack + sectorNum;
        } else {
            // 2D, 2HD
            sectorPos = trackNum * sectorsPerTrack * sidesPerDisk;
            sectorPos += (sideNum % sidesPerDisk) * sectorsPerTrack;
            sectorPos += sectorNum;
        }
        return sectorPos;
    }

    /**
     * ルートディレクトリか
     */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return true;
    }

    /**
     * サブディレクトリを作成できるか
     */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /**
     * セクタデータを指定コードで埋める
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    /**
     * セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // ディレクトリエリア
        for (int sectorPos = basic.getDirStartSector(); sectorPos <= basic.getDirEndSector(); sectorPos++) {
            DiskImageSector sector = basic.getManagedSector(sectorPos - 1);
            if (sector != null) {
                sector.fill(basic.getFillCodeOnDir());
            }
        }
        return true;
    }

    /**
     * ファイルをセーブする前の準備を行う
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryCpm> pItem, DiskBasicDirItem<DirectoryCpm> nItem, DiskBasicError errInfo) throws IOException {
        DiskBasicDirItemCPM dItem = (DiskBasicDirItemCPM) nItem;
        // グループエントリ数
        int groupEntries = dItem.getGroupEntries();
        // １ディレクトリで設定できるファイルサイズを求める (32K)
        int limitSize = basic.getSectorSize() * basic.getSectorsPerGroup() * groupEntries;

        dItem.used(true);
        dItem.visible(true);

        int remainSize = iStream.available(); // TODO assume available as length

        DiskBasicDirItemCPM prevAItem = dItem;
        DiskBasicDirItemCPM aItem = null;
        while (limitSize < remainSize) {
            // １ディレクトリで入りきらないので追加でディレクトリエントリを確保
            aItem = (DiskBasicDirItemCPM) dir.getEmptyItemOnCurrent(pItem, null);
            if (aItem == null) {
                errInfo.setError(DiskBasicError.ERR_DIRECTORY_FULL);
                return false;
            }
            aItem.copyData(prevAItem.getRawData());
            aItem.used(true);
            aItem.visible(false);

            prevAItem.setNextItem(aItem);

            remainSize -= limitSize;

            prevAItem = aItem;
        }

        return true;
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryCpm> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needEofCode = item.needCheckEofCode();

        int len = 0;
        if (remain <= size) {
            // 残り少ない
            int term = 0;
            if (remain < 0) remain = 0;
            if (remain > 0) iStream.readNBytes(buffer, 0, remain);
            if (needEofCode && ((remain % SECTOR_UNIT_CPM) != 0)) {
                // アスキーは終端コードをサプレス
                term = basic.getTextTerminateCode();
            }
            // 残りを128バイトで丸める
            int size128 = ((remain + SECTOR_UNIT_CPM - 1) / SECTOR_UNIT_CPM) * SECTOR_UNIT_CPM;
            if (remain < size128) {
                // バッファの余りはサプレス(128バイト境界まで)
                // Use byte array fill with the byte value of term
                byte termByte = (byte) term;
                for (int i = remain; i < size128 && i < buffer.length; i++) {
                    buffer[i] = termByte;
                }
                size = size128;
            }
            len = remain;
        } else {
            // 継続
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        // 反転
        basic.invertMemory(buffer, size);

        return len;
    }

    /**
     * FAT領域を削除する
     */
    @Override
    public void deleteGroupNumber(int groupNum) {
        // 未使用にする
        setGroupNumber(groupNum, 0);
    }
}
