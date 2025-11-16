/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFAT8.DiskBasicDirItemFAT8F.DirectoryFat8F;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 * FAT8の処理
 */
public abstract class DiskBasicTypeFAT8<T extends Directory> extends DiskBasicType<T> {

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super.init(basic, fat, dir);
    }

    /** FAT位置をセット */
    @Override
    public void setGroupNumber(int num, int val) {
        // 8bit FAT
        fat.getDiskBasicFatArea().setData8(num, val);
    }

    /** FAT位置を返す */
    @Override
    public int getGroupNumber(int num) {
        // 8bit FAT
        return fat.getDiskBasicFatArea().getData8(0, num) & 0xff;
    }

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean isFormatting) {
        int end = basic.getFatEndGroup() < 0xff ? basic.getFatEndGroup() : 0xff;
        int[] table = new int[end + 1];
        Arrays.fill(table, 0);

        // 同じグループ番号が重複しているか
        for (int pos = 0; pos <= end; pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum <= end) {
                table[groupNum]++;
            }
        }
        // 同じグループ番号が重複している場合エラー
        double validRatio = 1.0;
        for (int pos = 0; pos <= end; pos++) {
            if (table[pos] > 4) {
                validRatio = -1.0;
                break;
            }
        }

        return validRatio;
    }

    /** セクタデータを指定コードで埋める */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        if (track.getTrackNumber() == basic.getManagedTrackNumber()) {
            // ファイル管理エリアの場合
            sector.fill(basic.getFillCodeOnFAT());
        } else {
            // ユーザーエリア
            sector.fill(basic.getFillCodeOnFormat());
        }
    }

    /** セクタデータを埋めた後の個別処理 FAT予約済みをセット */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FATエリア先頭に0を入れる
        fat.set(0, (byte) 0);

        return true;
    }

    /** グループ確保時に最後のグループ番号を計算する */
    @Override
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        // 残り使用セクタ数
        int remainSecs = ((sizeRemain[0] - 1) / basic.getSectorSize());
        if (remainSecs >= basic.getSectorsPerGroup()) {
            remainSecs = basic.getSectorsPerGroup() - 1;
        }
        int lastGroupNum = remainSecs & 0xff;
        lastGroupNum += basic.getGroupFinalCode();
        return lastGroupNum;
    }

    /**
     * FAT8 specific implementation for F-BASIC / L3 1S
     */
    public abstract static class DiskBasicTypeFAT8F extends DiskBasicTypeFAT8<DirectoryFat8F> {

        @Override
        public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFat8F> dir) {
            super.init(basic, fat, dir);
        }

        /** 次の空き位置を返す */
        @Override
        public int getNextEmptyGroupNumber(int currentGroup) {
            int newNum = INVALID_GROUP_NUMBER;
            // 若い番号順に検索
            for (int num = currentGroup; num <= basic.getFatEndGroup(); num++) {
                int groupNum = getGroupNumber(num);
                if (groupNum == basic.getGroupUnusedCode()) {
                    newNum = num;
                    break;
                }
            }
            return newNum;
        }

        /** スキップするトラック番号 */
        @Override
        public int calcSkippedTrack() {
            return basic.getManagedTrackNumber();
        }

        /** ファイルの最終セクタのデータサイズを求める */
        @Override
        public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryFat8F> item,
                                            InputStream iStream, OutputStream oStream,
                                            byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) {
            // ファイルサイズはセクタサイズ境界なので要計算
            if (item.needCheckEofCode()) {
                // 終端コードの1つ前までを出力
                byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
                // ランダムアクセス時は除く
                int len = sectorSize - 1;
                for (; len >= 0; len--) {
                    if (sectorBuffer[len] == eofCode) break;
                }
                if (len < 0) {
                    // 終端コードがない？
                    len = sectorSize;
                }
                sectorSize = len;
            }
            return sectorSize;
        }
    }
}
