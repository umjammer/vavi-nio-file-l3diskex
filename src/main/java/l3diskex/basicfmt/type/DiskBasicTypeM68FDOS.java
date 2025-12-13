//
// Copyright (c) Sasaji. All rights reserved.
//

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicBitMLMap;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemM68FDOS.DirectoryM68FDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.ByteUtil;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * Sord M68 FDos (KDos) の処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 *
 * <li>IPLString : セクタ1のIPL</li>
 */
public class DiskBasicTypeM68FDOS extends DiskBasicTypeMZBase<DirectoryM68FDos> {

    /** 使用状況テーブル */
    private final DiskBasicBitMLMap bitmap = new DiskBasicBitMLMap();

    public static final int FORMAT_TYPE_M68FDOS = 81;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_M68FDOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryM68FDos> dir) {
        super.init(basic, fat, dir);
    }

    /** 使用しているグループの位置を得る */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3);
    }

    /** FAT位置をセット */
    @Override
    public void setGroupNumber(int num, int val) {
        bitmap.modify(num, val != 0);
    }

    /** FAT位置を返す */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /** 使用しているグループ番号か */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return bitmap.isSet(num);
    }

    /** 次のグループ番号を得る */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return num + 1;
    }

    /** 空きFAT位置を返す */
    @Override
    public int getEmptyGroupNumber() {
        int found = DiskBasicType.INVALID_GROUP_NUMBER;
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup(); groupNum++) {
            if (!isUsedGroupNumber(groupNum)) {
                found = groupNum;
                break;
            }
        }
        return found;
    }

    /**
     * FATエリアをチェック
     *
     * @param is_formatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double checkFat(boolean is_formatting) {
        double validRatio = 1.0;

        // FATエリア
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) {
            return -1.0;
        }
        if (sector.get16(0, basic.isBigEndian()) != 0x003f) {
            validRatio = -1.0;
        }

        // トラック１
        sector = basic.getSector(1, 0, 1);
        if (sector == null) {
            return -1.0;
        }
        if (sector.find("FDDOS".getBytes(), 5) < 0 && sector.find("SORD".getBytes(), 4) < 0) {
            validRatio = -1.0;
        }

        // 使用状況エリア 2セクタ
        for (int i = 0; i < 2; i++) {
            sector = basic.getManagedSector(basic.getFatStartSector() + i);
            if (sector == null) {
                return -1.0;
            }
            bitmap.addBuffer(sector.getSectorBuffer(), sector.getSectorBufferSize());
        }

        // 最終グループ番号
        if (basic.getFatEndGroup() == 0) {
            basic.setFatEndGroup(basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);
        }

        return validRatio;
    }

    /**
     * ルートディレクトリのセクタリストを計算
     *
     * @param startSector ディレクトリ開始セクタ番号
     * @param endSector   ディレクトリ終了セクタ番号 (unused in logic)
     * @param groupItems  [out] セクタリスト
     * @return true
     */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) {
        groupItems.clear();
        int dirSize = 0;
        int[] trackNum = {0};
        int[] sideNum = {0};
        int[] sectorNum = {1};
        int[] divNum = {0};
        int[] numOfDivs = {1};
        int secPos = startSector - 1;
        int endSectorPos = basic.getFatEndGroup() * basic.getSectorsPerGroup();
        int maxDirSize = endSectorPos * basic.getSectorSize();
        DiskImageSector sector = basic.getManagedSector(secPos, trackNum, sideNum, sectorNum, divNum, numOfDivs);
        if (sector == null) return false;
        while (dirSize < maxDirSize) {
            groupItems.add(secPos, 0, trackNum[0], sideNum[0], sectorNum[0], sectorNum[0], divNum[0], numOfDivs[0]);
            dirSize += (sector.getSectorSize() / numOfDivs[0]);

            // 次のセクタ番号を得る
            secPos = sector.get16(sector.getSectorSize() - 2, true) & 0xffff; // TODO -2
            if (secPos <= 0 || secPos > endSectorPos) break;
            sector = basic.getSectorFromSectorPos(secPos, trackNum, sideNum, divNum, numOfDivs);
            if (sector == null) break;
            sectorNum[0] = sector.getSectorNumber();
        }

        groupItems.setSize(dirSize);
        return true;
    }

    /**
     * ディレクトリエリアのサイズに達したらアサイン終了するか
     *
     * @param pos         [in,out] ディレクトリの位置
     * @param size        [in,out] ディレクトリのセクタサイズ
     * @param size_remain [in,out] ディレクトリの残りサイズ
     * @return 0: 終了しない, 1: 強制的に未使用とする アサインは継続, -1: 現グループでアサイン終了。次のグループから継続, -2: 強制的にアサイン終了する
     */
    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        return pos[0] + DirectoryM68FDos.SIZE > size[0] ? -1 : 0;
    }

    /**
     * ディレクトリアサインでセクタ毎に位置を調整する
     *
     * @return 調整後のディレクトリの位置
     * @param pos ディレクトリの位置
     */
    @Override
    public int adjustPositionAssigningDirectory(int pos) {
        return 0;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1 - dataStartGroup;
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /**
     * 残りディスクサイズを計算
     *
     * @param wrote 書込み操作を行った後か
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        //int used = 0;
        fatAvailability.empty();

        // 使用済みかチェック
        int groups = 0;
        FatAvailability fatStatus;
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup(); groupNum++) {
            if (groupNum < dataStartGroup) {
                //used++;
                fatStatus = FAT_AVAIL_SYSTEM;
            } else if (!isUsedGroupNumber(groupNum)) {
                groups++;
                fatStatus = FAT_AVAIL_FREE;
            } else {
                //used++;
                fatStatus = FAT_AVAIL_USED;
            }
            fatAvailability.add(fatStatus, 0, 0);
        }

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectoryM68FDos>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (DiskBasicDirItem<DirectoryM68FDos> item : items) {
                if (item == null || !item.isUsed()) continue;

                // グループ番号のマップを調べる
                int groupCount = item.getGroupCount();
                if (groupCount > 0) {
                    DiskBasicGroupItem groupItem = item.getGroup(groupCount - 1);
                    int groupNum = groupItem.group;
                    if (groupNum <= basic.getFatEndGroup()) {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        int fSize = groups * basic.getSectorsPerGroup() * basic.getSectorSize();

        fatAvailability.setFreeSize(fSize);
        fatAvailability.setFreeGroups(groups);
    }

    /**
     * ファイルをセーブする前の準備を行う
     *
     * @param iStream  ストリームバッファ
     * @param fileSize [in,out] 出力サイズ
     * @param pItem    [in,out] ファイル名、属性を持っているディレクトリアイテム
     * @param nItem    [in,out] 確保したディレクトリアイテム
     * @param errInfo  [in,out] エラー情報
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryM68FDos> pItem, DiskBasicDirItem<DirectoryM68FDos> nItem, DiskBasicError errInfo) {
        return true;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileUnitNum ファイル番号
     * @param item        [in,out] ディレクトリアイテム
     * @param size        確保するデータサイズ（バイト）
     * @param flags       新規か追加か
     * @param groupItems  [out] 確保したセクタリスト
     * @return >0: 正常 -1: 空きなし (開始グループ設定前) -2: 空きなし (開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryM68FDos> item, int size, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int[] fileSize = {0};
        int[] groups = {0};

        int rc = 0;
        int remain = size;
        // セクタ末尾に次のセクタ番号を入れるか
        boolean isChain = item.needChainInData();
        int sectorSize = basic.getSectorSize();
        if (isChain) {
            sectorSize -= 2;
        }

        // 必要なグループ数
        int groupSize = ((size - 1) / sectorSize / basic.getSectorsPerGroup()) + 1;
        if (isChain) {
            groupSize = 1;
        }

        // 未使用が連続している位置をさがす
        int[] groupStart = {DiskBasicType.INVALID_GROUP_NUMBER};
        int count = findContinuousArea(groupSize, groupStart);
        if (count < groupSize) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // データの開始グループ決定
        item.setStartGroup(fileUnitNum, groupStart[0]);

        // 領域を確保する
        rc = allocateGroupsSub(item, groupStart[0], remain, sectorSize, groupItems[0], fileSize, groups);

        // 確保したグループ数をセット
        item.setGroupSize(groups[0]);
        // 最終グループをセット
        item.setExtraGroup(groupItems[0].last().group);

        return rc;
    }

    /** グループを確保して使用中にする */
    @Override
    public int allocateGroupsSub(DiskBasicDirItem<DirectoryM68FDos> item, int group_start, int remain, int sectorSize, DiskBasicGroups groupItems, int[] fileSize, int[] groups) {
        int rc = 0;
        int groupNum = group_start;
        int prevGroup = 0;

        //DiskBasicDirItemM68FDOS dItem = (DiskBasicDirItemM68FDOS)item;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // 使用しているか
            boolean usedGroup = isUsedGroupNumber(groupNum);
            if (!usedGroup) {
                if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
                    // 使用済みにする
                    basic.getNumsFromGroup(prevGroup, groupNum, sectorSize, remain, groupItems);
                    setGroupNumber(prevGroup, 1);
                    fileSize[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
                    groups[0]++;
                }
                remain -= (sectorSize * basic.getSectorsPerGroup());
                prevGroup = groupNum;
            }
            // 次のグループ
            groupNum++;
            limit--;
        }
        if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
            // 使用済みにする
            basic.getNumsFromGroup(prevGroup, 0, sectorSize, remain, groupItems);
            setGroupNumber(prevGroup, 1);
            fileSize[0] += basic.getSectorSize() * basic.getSectorsPerGroup();
            groups[0]++;
        }
        if (prevGroup > basic.getFatEndGroup()) {
            // ファイルがオーバフローしている
            rc = -2;
        } else if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }
        return rc;
    }

    /**
     * セクタデータを埋めた後の個別処理
     * フォーマット FAT予約済みをセット
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        DiskImageSector sector;

        //
        // FATエリア
        //
        int managedSectorPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        for (int s = basic.getFatStartSector() - 1, pos = 0; s < basic.getDirStartSector() - 1; s++, pos++) {
            sector = basic.getSectorFromSectorPos(managedSectorPos + s);
            if (sector == null) {
                return false;
            }
            sector.fill(basic.getFillCodeOnFAT());
            switch (pos) {
                case 0 -> {
                    // first sector
                    sector.copy(new byte[] {0x00, 0x3f}, 2);
                }
                case 1 -> {
                    // set bits

                    int n = managedSectorPos + basic.getDirEndSector();
                    int len = (n >> 3);
                    sector.fill((byte) 0xff, len, 0);
                    int mod = (n & 7);
                    sector.fill((byte) ((0xff00 >> mod) & 0xff), 1, len);
                }
                case 2 -> {
                    // set bits

                    int n = basic.getSidesPerDiskOnBasic() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic();
                    n -= (sector.getSectorSize() * (pos - 1) * 8);
                    if (n >= 0 && n < (sector.getSectorSize() * 8)) {
                        int len = (n >> 3);
                        sector.fill((byte) 0xff, -1, len);
                        int mod = (n & 7);
                        sector.fill((byte) (0x00ff >> mod), 1, len);
                    }
                }
                default -> {}
            }
        }

        //
        // DIRエリア
        //
        for (int s = basic.getDirStartSector() - 1, pos = 0; s < basic.getDirEndSector() - 1; s++, pos++) {
            sector = basic.getSectorFromSectorPos(managedSectorPos + s);
            if (sector == null) {
                return false;
            }
            sector.fill(basic.getFillCodeOnFAT());
            if (s < basic.getDirEndSector() - 2) {
                short next = basic.orderUint16((short) ((managedSectorPos + s + 1) & 0xffff));
                sector.copy(ByteUtil.getBeBytes(next), 2, basic.getSectorSize() - 2);
            }
            if (pos == 0) {
                // エントリ１つ
                sector.copy(new byte[] {
                        (byte) 0x62, (byte) 0x56, (byte) 0xc1, (byte) 0xc0, (byte) 0xa7, (byte) 0x30, (byte) 0xf9, (byte) 0x80,
                        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x5c, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                        (byte) 0x00, (byte) 0x0d, (byte) 0xa4}, 19);
            }
        }

        //
        sector = basic.getSectorFromSectorPos(managedSectorPos);
        if (sector == null) {
            return false;
        }
        sector.copy("\u00f3\u0076NOT FDDOS MEDIA\u0000".getBytes(), 18);

        return true;
    }

    /**
     * データの読み込み/比較処理
     *
     * @param fileUnitNum  ファイル番号
     * @param item         ディレクトリアイテム
     * @param iStream      [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時はnull
     * @param oStream      [in,out] 出力先 データ読み出し時に使用 ベリファイ時はnull
     * @param sectorBuffer セクタバッファ
     * @param sectorSize   バッファサイズ
     * @param remainSize   残りサイズ
     * @param sectorNum    セクタ番号
     * @param sectorEnd    最終セクタ番号
     * @return >=0: 処理したサイズ, -1: 比較不一致, -2: セクタがおかしい
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryM68FDos> item, InputStream iStream, OutputStream oStream,
                          byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        boolean needChain = item.needChainInData();

        if (needChain) {
            // セクタの最終バイトはチェイン用セクタ番号がある
            sectorSize -= 2;
        }

        int size = remainSize < sectorSize ? remainSize : sectorSize;

        byte[] temp;
        if (oStream != null) {
            // Write to output stream
            temp = Arrays.copyOfRange(sectorBuffer, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);
            oStream.write(temp, 0, size);
        }
        if (iStream != null) {
            // Read from input stream and compare
            temp = new byte[size];
            iStream.readNBytes(temp, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, temp.length, sectorBuffer, 0, size)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /**
     * データの書き込み処理
     *
     * @param item      ディレクトリアイテム
     * @param iStream   ストリームデータ
     * @param buffer    [out] セクタ内の書き込み先バッファ
     * @param size      書き込み先バッファサイズ
     * @param remain    残りのデータサイズ
     * @param sectorNum セクタ番号
     * @param groupNum  現在のグループ番号
     * @param nextGroup 次のグループ番号
     * @param sectorEnd 最終セクタ番号
     * @param seqNum    通し番号(0...)
     * @return 書き込んだバイト数
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryM68FDos> item, InputStream iStream, byte[] buffer, int size, int remain,
                         int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needChain = item.needChainInData();

        int len = 0;
        if (needChain) {
            size -= 2;
        }

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

        // 次のセクタ番号を書く
        if (needChain) {
            int next_sector = nextGroup * basic.getSectorsPerGroup();
            if (next_sector >= 0) {
                // bigendien
                buffer[size] = (byte) ((next_sector >> 8) & 0xff);
                buffer[size + 1] = (byte) (next_sector & 0xff);
            }
        }

        return len;
    }

    /** データの書き込み終了後の処理 */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryM68FDos> item) {
//        DiskBasicDirItemM68FDOS dItem = (DiskBasicDirItemM68FDOS) item;
//        dItem.setUnknownData();
    }

    /** IPLや管理エリアの属性を得る */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
    }

    /** IPLや管理エリアの属性をセット */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
    }
}