/*
 * Sasaji (translated automatically)
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFROST.DirectoryFrost;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemFROST.FROST_GROUP_SIZE;


/**
 * Frost-DOSの処理
 * <p>
 * DiskBasicParam
 *
 * <li>{@code ReservedGroups}: Group 予約済みにするグループ（クラスタ）番号</li>
 */
public class DiskBasicTypeFROST extends DiskBasicTypeFAT8<DirectoryFrost> {

    /** */
    public DiskBasicTypeFROST(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFrost> dir) {
        super(basic, fat, dir);
    }

    /**
     * FAT位置をセット
     *
     * @param num グループ番号(0...)
     * @param val 値
     */
    @Override
    public void setGroupNumber(int num, int val) {
        // 16bit
        fat.getDiskBasicFatArea().setData16BE(num, convTrackSectorFromSectorPos(val));
    }

    /**
     * FAT位置を返す
     *
     * @param num グループ番号(0...)
     */
    @Override
    public int getGroupNumber(int num) {
        // 16bit
        return convSectorPosFromTrackSector(fat.getDiskBasicFatArea().getData16BE(0, num));
    }

    /**
     * 次の空きFAT位置を返す
     *
     * @param currentGroup グループ番号(0...)
     * @return INVALID_GROUP_NUMBER 空きなし
     */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        int newNum = INVALID_GROUP_NUMBER;
        // 現在の番号と連続するように検索
        boolean found = false;
        for (int i = 0; i < 2 && !found; i++) {
            int startGroupNum = (i == 0 ? currentGroup + 1 : 0);
            for (int groupNum = startGroupNum; groupNum <= basic.getFatEndGroup(); groupNum++) {
                int nextGnum = getGroupNumber(groupNum);
                if (nextGnum == basic.getGroupUnusedCode()) {
                    newNum = groupNum;
                    found = true;
                    break;
                }
            }
        }
        return newNum;
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        // １トラック当たりのグループ数を計算する
        if (basic.getGroupsPerTrack() == 0) {
            // 512バイトを１グループとして計算する
            int count = 0;
            DiskImageTrack track = basic.getTrack(1, 0);
            if (track != null) {
                List<DiskImageSector> sectors = track.getSectors();
                if (sectors != null) {
                    for (DiskImageSector diskImageSector : sectors) {
                        int size = diskImageSector.getSectorSize();
                        count += (size / FROST_GROUP_SIZE);
                    }
                }
            }
            if (count == 0) {
                count = 11;
            }
            basic.setGroupsPerTrack(count);
        }
        // １セクタ当たりのグループ数
        int groupsPerSector = (basic.getGroupsPerTrack() + basic.getSectorsPerTrackOnBasic() - 1) / basic.getSectorsPerTrackOnBasic();
        basic.setGroupsPerSector(groupsPerSector);

        // グループ数
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getGroupsPerTrack();
            basic.setFatEndGroup(endGroup - 1);
        }

        return 1.0;
    }

    /**
     * FATエリアをチェック
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // FAT,ディレクトリエリアはシステム予約となっているか
            List<Integer> groups = basic.getReservedGroups();
            for (int group : groups) {
                int groupNum = getGroupNumber(group);
                if (groupNum != basic.getGroupSystemCode()) {
                    validRatio = -1.0;
                    break;
                }
            }
        }
        return validRatio;
    }

    /**
     * 使用可能なディスクサイズを得る
     *
     * @param diskSize  ディスクサイズ
     * @param groupSize グループ数
     */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = 0;
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum != basic.getGroupSystemCode()) groupSize[0]++;
        }
        diskSize[0] = groupSize[0] * basic.getSectorSize() / basic.getGroupsPerSector();
    }

    /**
     * 残りディスクサイズを計算
     */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.clear();

        // 使用済みかチェック
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int fSize = 0;
            int groups = 0;
            int groupNum = getGroupNumber(pos);
            FatAvailability fsts = FAT_AVAIL_USED;
            if (groupNum == basic.getGroupUnusedCode()) {
                fSize = (basic.getSectorSize() / basic.getGroupsPerSector());
                groups = 1;
                fsts = FAT_AVAIL_FREE;
            } else if (groupNum == basic.getGroupSystemCode()) {
                fsts = FAT_AVAIL_SYSTEM;
            } else if (groupNum >= basic.getGroupFinalCode()) {
                fsts = FAT_AVAIL_USED_LAST;
            }
            fatAvailability.add(fsts, fSize, groups);
        }

        //freeDiskSize = (int) fSize;
        //freeGroups = (int) groups;
    }

    /**
     * 未使用が連続している位置をさがす
     */
    public int findContinuousArea(int groupSize) {
        // 未使用が連続している位置をさがす
        int group = INVALID_GROUP_NUMBER;
        int groupStart = INVALID_GROUP_NUMBER;
        int count = 0;
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup() && count < groupSize; groupNum++) {
            if (getGroupNumber(groupNum) == basic.getGroupUnusedCode()) {
                if (count == 0) {
                    groupStart = groupNum;
                }
                count++;
            } else {
                count = 0;
            }
        }
        if (count == groupSize) {
            group = groupStart;
        }
        return group;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileUnitNum ファイル番号
     * @param item        ディレクトリアイテム
     * @param dataSize    確保するデータサイズ（バイト）
     * @param flags       新規か追加か
     * @param groupItems  確保したセクタリスト
     * @return >0: 正常, -1: 空きなし (開始グループ設定前), -2: 空きなし (開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryFrost> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int groups = 0;

        // FAT
        int rc = 0;
        boolean firstGroup = flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW;
        int[] sizeRemain = {dataSize};

        int bytesPerGroup = basic.getSectorSize() / basic.getGroupsPerSector();
        int groupSize = (dataSize + bytesPerGroup - 1) / bytesPerGroup;
        // 連続して確保できる領域
        int groupNum = findContinuousArea(groupSize);
        if (groupNum == INVALID_GROUP_NUMBER) {
            groupNum = getEmptyGroupNumber();
        }
        int limit = basic.getFatEndGroup() + 1;
        while (rc >= 0 && limit >= 0 && sizeRemain[0] > 0) {
            if (groupNum == INVALID_GROUP_NUMBER) {
                // 空きなし
                rc = firstGroup ? -1 : -2;
                break;
            }
            // 位置を予約
            setGroupNumber(groupNum, basic.getGroupFinalCode());

            // グループ番号の書き込み
            if (firstGroup) {
                item.setStartGroup(fileUnitNum, groupNum);
                firstGroup = false;
            }

            // 次の空きグループをさがす
            int nextGroupNum = getNextEmptyGroupNumber(groupNum);

            // 次の空きがない場合 or 残りサイズがこのグループで収まる場合
            if (nextGroupNum == INVALID_GROUP_NUMBER || sizeRemain[0] <= bytesPerGroup) {
                // 最後のグループ番号
                nextGroupNum = calcLastGroupNumber(nextGroupNum, sizeRemain);
            }

            basic.getNumsFromGroup(groupNum, nextGroupNum, basic.getSectorSize(), sizeRemain[0], groupItems[0]);

            // グループ番号設定
            setGroupNumber(groupNum, nextGroupNum);

            groupNum = nextGroupNum;

            sizeRemain[0] -= bytesPerGroup;
            groups++;

            limit--;
        }
        if (limit < 0) {
            // too large or infinite loop
            rc = firstGroup ? -1 : -2;
        }

        if (rc >= 0) {
            if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
                // 追加のときはチェインをつなぐ
                if (groupItems[0].size() > 0) {
                    rc = chainGroups(item.getStartGroup(0), groupItems[0].get(0).group);
                }
            }
        } else {
            // グループを削除
            deleteGroups(groupItems[0]);
            rc = -1;
        }

        return rc;
    }

    /**
     * グループ番号から開始セクタ番号を得る
     *
     * @param groupNum グループ番号
     * @return 開始セクタ番号
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    /**
     * トラック＋セクタ番号から論理セクタ番号を得る
     */
    public int convSectorPosFromTrackSector(int trkSec) {
        if (trkSec == basic.getGroupUnusedCode() || trkSec == basic.getGroupFinalCode() || trkSec == basic.getGroupSystemCode()) {
            return trkSec;
        }

        return (trkSec >> 8) * basic.getGroupsPerTrack() + (trkSec & 0xff) - 1;
    }

    /**
     * 論理セクタ番号からトラック＋セクタ番号を得る
     */
    public int convTrackSectorFromSectorPos(int pos) {
        if (pos == basic.getGroupUnusedCode() || pos == basic.getGroupFinalCode() || pos == basic.getGroupSystemCode()) {
            return pos;
        }

        return ((pos / basic.getGroupsPerTrack()) << 8) + (pos % basic.getGroupsPerTrack()) + 1;
    }

    /**
     * セクタ位置 (トラック0,サイド0,セクタ 1 を 0 とした通し番号) からトラック、サイド、セクタの各番号を得る
     * セクタ位置は、機種によらずトラック 0, サイド 0, セクタ 1 を 0 とした通し番号
     *
     * @param sectorPos セクタ位置 (トラック 0, サイド 0, セクタ 1 を 0 とした通し番号)
     * @param trackNum  トラック番号
     * @param sideNum   サイド番号
     * @param sectorNum セクタ番号
     * @param divNum    分割番号 (can be null)
     * @param numOfDivs 分割数 (can be null)
     */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] numOfDivs) {
        int groupsPerTrack = basic.getGroupsPerTrack();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();

        int grpsPerSec = basic.getGroupsPerSector();

        // 2D, 2HD
        int trackSideNum = sectorPos / groupsPerTrack;
        trackNum[0] = trackSideNum / sidesPerDisk;
        sideNum[0] = trackSideNum % sidesPerDisk;
        sectorNum[0] = ((sectorPos % groupsPerTrack) / grpsPerSec);
        if (divNum != null) divNum[0] = ((sectorPos % groupsPerTrack) % grpsPerSec);

        if (sectorNum[0] * grpsPerSec > groupsPerTrack) {
            grpsPerSec = grpsPerSec - (sectorNum[0] * grpsPerSec - groupsPerTrack);
        }

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBaseOnDisk();

        if (numOfDivs != null) numOfDivs[0] = grpsPerSec;
    }

    /**
     * トラック、サイド、セクタの各番号からセクタ位置 (トラック 0, サイド 0, セクタ 1 を 0 とした通し番号)を得る
     * セクタ位置は、機種によらずトラック 0, サイド 0, セクタ 1 を 0 とした通し番号
     *
     * @param trackNum  トラック番号
     * @param sideNum   サイド番号
     * @param sectorNum セクタ番号
     * @param divNum    分割番号
     * @param numOfDivs 分割数
     * @return セクタ位置 (トラック 0, サイド 0, セクタ 1 を 0 とした通し番号)
     */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        int groupsPerTrack = basic.getGroupsPerTrack();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBaseOnDisk();

        // 2D, 2HD
        sectorPos = (trackNum * sidesPerDisk + sideNum) * groupsPerTrack;
        sectorPos += sectorNum * numOfDivs + divNum;

        return sectorPos;
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
     * フォーマット FAT予約済みをセット
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT トラック０はシステム
        int endGroupNum = basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic() - 1;
        for (int groupNum = 0; groupNum < endGroupNum; groupNum++) {
            setGroupNumber(groupNum, basic.getGroupSystemCode());
        }
        // FAT FAT, DIRエリアはシステム
        List<Integer> groups = basic.getReservedGroups();
        for (int groupNum : groups) {
            setGroupNumber(groupNum, basic.getGroupSystemCode());
        }
        return true;
    }

    /**
     * グループ確保時に最後のグループ番号を計算する
     *
     * @param groupNum   現在のグループ番号
     * @param sizeRemain 残りのデータサイズ (mutable via int array)
     * @return 最後のグループ番号
     */
    @Override
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        return basic.getGroupFinalCode();
    }

    /**
     * データの書き込み処理
     *
     * @param item      ディレクトリアイテム
     * @param iStream   ストリームデータ
     * @param buffer    セクタ内の書き込み先バッファ
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
    public int writeFile(DiskBasicDirItem<DirectoryFrost> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                // Read up to 'remain' bytes
                len = iStream.readNBytes(buffer, 0, remain);
                if (len < 0) len = 0; // Handle end of stream unexpectedly
            }
            if (size > len) {
                // Fill remaining with 0s
                Arrays.fill(buffer, len, size, (byte) 0);
            }
        } else {
            // 継続
            len = iStream.readNBytes(buffer, 0, size);
            if (len < 0) len = 0;
        }
        // 反転
        basic.invertMemory(buffer, size);

        return len;
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // タイトル名 FATエリア
        int[] divNum = new int[1], numOfDivs = new int[1];
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1 + 3, null, null, null, divNum, numOfDivs);
        if (sector != null) {
            byte[] buf = sector.getSectorBuffer();
            int offset = sector.getSectorSize() * divNum[0] / numOfDivs[0] + 0x140;
            if (buf[offset] >= 0x20 && (buf[offset] & 0xff) < 0xff) {
                StringBuilder sb = new StringBuilder();
                basic.getCharCodes().convToString(buf, offset, 64, sb, 0);
                String dst = sb.toString();
                data.setVolumeName(dst);
            }
        }
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
    }
}
