/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemN88.DirectoryN88;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 * N88-BASICの処理
 * <p>
 * DiskBasicParam
 *
 * <li>ReservedGroups : Group 予約済みにするグループ（クラスタ）番号</li>
 */
public class DiskBasicTypeN88 extends DiskBasicTypeFAT8<DirectoryN88> {

    public static final int FORMAT_TYPE_N88 = 5;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_N88;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super.init(basic, fat, dir);
    }

    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;
        // 管理エリアに近い位置から検索

        // トラック当たりのグループ数
        int grpsPerTrk = basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup();
        // 最大グループ数
        int maxGroup = basic.getFatEndGroup() - managedStartGroup;
        if (maxGroup < managedStartGroup) maxGroup = managedStartGroup;
        maxGroup = maxGroup * 2 - 1;

        for (int i = 0; i <= maxGroup; i++) {
            int i2 = i / grpsPerTrk;
            int i4 = i / grpsPerTrk / 2;
            int num;
            if ((i2 & 1) == 0) {
                num = managedStartGroup - ((i4 + 1) * grpsPerTrk) + (i % grpsPerTrk);
            } else {
                num = managedStartGroup + ((i4 + 1) * grpsPerTrk) + (i % grpsPerTrk);
            }
            if (basic.getFatEndGroup() < num)
                continue;
            int groupNum = getGroupNumber(num);
            if (groupNum == basic.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            endGroup /= basic.getSectorsPerGroup();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

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

    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT,DIRエリア
        DiskImageTrack track = basic.getTrack(basic.getManagedTrackNumber(), basic.getFatSideNumber());
        if (track == null) return false;
        List<DiskImageSector> sectors = track.getSectors();
        if (sectors == null) return false;
        int idSector = (basic.getDirEndSector() + 1) % basic.getSectorsPerTrackOnBasic();
        for (DiskImageSector sector : sectors) {
            if (sector != null) {
                // ファイル管理エリアをクリア IDエリアは0でクリア
                sector.fill(sector.getSectorNumber() != idSector ? basic.getFillCodeOnFAT() : 0);
            }
        }

        // システムで使用している部分のクラスタ位置を予約済みにする
        List<Integer> groups = basic.getReservedGroups();
        for (int group : groups) {
            setGroupNumber(group, basic.getGroupSystemCode());
        }

        return true;
    }

    /**
     * ファイルの最終セクタのデータサイズを求める
     *
     * @param item          ディレクトリアイテム
     * @param iStream       [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時は {@code null}
     * @param oStream       [in,out] 出力先 データ読み出し時に使用 ベリファイ時は {@code null}
     * @param sectorBuffer セクタバッファ
     * @param sectorSize   バッファサイズ
     * @param remainSize   残りサイズ
     * @return 残りサイズ
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryN88> item,
                                        InputStream iStream,
                                        OutputStream oStream,
                                        byte[] sectorBuffer,
                                        int sectorOffset,
                                        int sectorSize,
                                        int remainSize) throws IOException {
        // ファイルサイズはセクタサイズ境界なので要計算
        if (item.needCheckEofCode()) {
            // 終端コードの1つ前までを出力
            byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
            byte nullCode = basic.invertUint8((byte) 0);
            // ランダムアクセス時は除く
            int len = sectorSize - 1;
            for (; len >= 0; len--) {
                if (sectorBuffer[len] != eofCode && sectorBuffer[len] != nullCode)
                    break;
            }
            if (len >= 0)
                sectorSize = len + 1;
        } else {
            // 計算手段がないので残りサイズをそのまま返す
            if (iStream != null) {
                sectorSize = iStream.available() % sectorSize; // TODO assume available as length
            } else {
                sectorSize = remainSize;
            }
        }
        return sectorSize;
    }

    @Override
    public int writeFile(DiskBasicDirItem<DirectoryN88> item,
                         InputStream iStream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sectorNum,
                         int groupNum,
                         int nextGroup,
                         int sectorEnd,
                         int seqNum) throws IOException {
        boolean needEofCode = item.needCheckEofCode();

        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                if (needEofCode) {
                    int bytesRead = iStream.read(buffer, 0, remain);
                    // 最終は終端コードを入れる
                    // ただし、残りサイズが丁度セクタサイズなら入れない
                    if (bytesRead + 1 == remain) {
                        buffer[remain - 1] = basic.getTextTerminateCode();
                    }
                } else {
                    iStream.readNBytes(buffer, 0, remain);
                }
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
        // 反転
        basic.invertMemory(buffer, size);

        return len;
    }
}
