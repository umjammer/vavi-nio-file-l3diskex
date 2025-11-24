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

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;


/**
 * C82‑BASIC の処理
 * <p>
 * DiskBasicParam
 *
 * <li>ReservedGroups Group 予約済みにするグループ（クラスタ）番号</li>
 */
public class DiskBasicTypeFP extends DiskBasicTypeN88 {

    public static final int FORMAT_TYPE_FP = 13;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_FP;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super.init(basic, fat, dir);
    }

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean isFormatting) {
        return super.checkFat(isFormatting);
    }

    /** セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        DiskImageSector sector = null;

        // FAT area
        sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) return false;
        sector.fill(basic.getFillCodeOnFAT(), basic.getFatEndGroup() + 1, 1);
        // FAT先頭
        sector.fill((byte) (basic.getFatEndGroup() + 1), 1, 0);

        // DIR area
        int staSec = basic.getDirStartSector();
        int endSec = basic.getDirEndSector();
        for (int sec = staSec; sec <= endSec; sec++) {
            sector = basic.getManagedSector(sec - 1);
            if (sector == null) return false;
            sector.fill(basic.getFillCodeOnDir());
        }

        // system used group reservation
        List<Integer> grps = basic.getReservedGroups();
        for (Integer grp : grps) {
            setGroupNumber(grp, basic.getGroupSystemCode());
        }

        return true;
    }

    /** ファイルの最終セクタのデータサイズを求める */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryN88> item, InputStream iStream,
                                        OutputStream oStream, byte[] sectorBuffer,
                                        int sectorOffset, int sectorSize, int remainSize) throws IOException {
        // ファイルサイズはセクタサイズ境界なので要計算
        if (item.needCheckEofCode()) {
            // アスキーファイルのとき終端コードの1つ前までを出力
            byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
            for (int len = 0; len < sectorSize; len++) {
                if (sectorBuffer[len] == eofCode) {
                    sectorSize = len;
                    break;
                }
            }
        } else {
            // 計算手段がないので残りサイズをそのまま返す
            if (iStream != null) {
                // 比較時は、比較先のファイルサイズ
                sectorSize = iStream.available() % sectorSize; // TODO assume available as stream length
            } else {
                sectorSize = remainSize;
            }
        }
        return sectorSize;
    }

    /** データの書き込み処理 */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryN88> item, InputStream iStream, byte[] buffer,
                         int size, int remain, int sectorNum, int groupNum,
                         int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            int tmpRemain = remain;
            if (tmpRemain > 0) {
                iStream.readNBytes(buffer, 0, tmpRemain);
                // 最終は終端コードを入れる
                // ただしランダムアクセスか、残りサイズが丁度セクタサイズなら入れない
                if (item.getFileAttr().unmatchType(FILE_TYPE_RANDOM_MASK.getValue(), FILE_TYPE_RANDOM_MASK.getValue()) && size > tmpRemain) {
                    buffer[tmpRemain] = basic.getTextTerminateCode();
                    tmpRemain++;
                }
            }
            if (size > tmpRemain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, tmpRemain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }
        // 必要なら反転
        basic.invertMemory(buffer, size);

        return len;
    }
}
