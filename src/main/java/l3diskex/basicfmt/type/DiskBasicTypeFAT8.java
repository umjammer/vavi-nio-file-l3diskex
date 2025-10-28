/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.DirectoryFat8f;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 * FAT8の処理
 */
public class DiskBasicTypeFAT8<T extends DirectoryT> extends DiskBasicType<T> {

    /** */
    public DiskBasicTypeFAT8(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super(basic, fat, dir);
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
        return fat.getDiskBasicFatArea().getData8(0, num);
    }

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean isFormatting) {
        int end = basic.getFatEndGroup() < 0xff ? basic.getFatEndGroup() : 0xff;
        int[] tbl = new int[end + 1];
        Arrays.fill(tbl, 0);

        // 同じグループ番号が重複しているか
        for (int pos = 0; pos <= end; pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum <= end) {
                tbl[gnum]++;
            }
        }
        // 同じグループ番号が重複している場合エラー
        double validRatio = 1.0;
        for (int pos = 0; pos <= end; pos++) {
            if (tbl[pos] > 4) {
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
            sector.fill(basic.diskBasicParam.getFillCodeOnFAT());
        } else {
            // ユーザーエリア
            sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
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
        int gnum = remainSecs & 0xff;
        gnum += basic.diskBasicParam.getGroupFinalCode();
        return gnum;
    }

    /**
     * FAT8 specific implementation for F-BASIC / L3 1S
     */
    public static class DiskBasicTypeFAT8F extends DiskBasicTypeFAT8<DirectoryFat8f> {

        /** */
        public DiskBasicTypeFAT8F(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFat8f> dir) {
            super(basic, fat, dir);
        }

        /** 次の空き位置を返す */
        @Override
        public int getNextEmptyGroupNumber(int currGroup) {
            int newNum = INVALID_GROUP_NUMBER;
            // 若い番号順に検索
            for (int num = currGroup; num <= basic.getFatEndGroup(); num++) {
                int gnum = getGroupNumber(num);
                if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
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
        public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryFat8f> item,
                                            InputStream istream, OutputStream ostream,
                                            byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) {
            // ファイルサイズはセクタサイズ境界なので要計算
            if (item.needCheckEofCode()) {
                // 終端コードの1つ前までを出力
                byte eofCode = basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
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
