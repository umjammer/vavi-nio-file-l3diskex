/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryN88;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;


/**
 * DiskBasicTypeFP – C82‑BASIC の処理
 * 
 * DiskBasicParam
 *  @li ReservedGroups : Group 予約済みにするグループ（クラスタ）番号
 */
public class DiskBasicTypeFP extends DiskBasicTypeN88 {

    /* public constructor (used by the program) */
    public DiskBasicTypeFP(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super(basic, fat, dir);
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
        sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) return false;
        sector.fill(basic.diskBasicParam.getFillCodeOnFAT(), basic.diskBasicParam.getFatEndGroup() + 1, 1);
        sector.fill((byte)(basic.diskBasicParam.getFatEndGroup() + 1), 1, 0);

        // DIR area
        int staSec = basic.diskBasicParam.getDirStartSector();
        int endSec = basic.diskBasicParam.getDirEndSector();
        for (int sec = staSec; sec <= endSec; sec++) {
            sector = basic.getManagedSector(sec - 1);
            if (sector == null) return false;
            sector.fill((byte)basic.diskBasicParam.getFillCodeOnDir());
        }

        // system used group reservation
        List<Integer> grps = basic.diskBasicParam.getReservedGroups();
        for (int i = 0; i < grps.size(); i++) {
            setGroupNumber(grps.get(i), basic.diskBasicParam.getGroupSystemCode());
        }

        return true;
    }

    /** ファイルの最終セクタのデータサイズを求める */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryN88> item, InputStream istream,
                                        OutputStream ostream, byte[] sectorBuffer,
                                        int sectorOffsrt, int sectorSize, int remainSize) throws IOException {
        /* ファイルサイズはセクタサイズ境界なので要計算 */
        if (item.needCheckEofCode()) {
            /* アスキーファイルのとき終端コードの1つ前までを出力 */
            byte eofCode = basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
            for (int len = 0; len < sectorSize; len++) {
                if (sectorBuffer[len] == eofCode) {
                    sectorSize = len;
                    break;
                }
            }
        } else {
            /* 計算手段がないので残りサイズをそのまま返す */
            if (istream != null) {
                /* 比較時は、比較先のファイルサイズ */
                if (istream instanceof InputStream) {
                    sectorSize = (int)((InputStream)istream).available() % sectorSize;
                }
            } else {
                sectorSize = remainSize;
            }
        }
        return sectorSize;
    }

    /* ---- save / write ---- */

    /** データの書き込み処理 */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryN88> item, InputStream istream, byte[] buffer,
                         int size, int remain, int sectorNum, int groupNum,
                         int nextGroup, int sectorEnd, int seqNum) {
        int len = 0;
        if (remain <= size) {
            /* 残り少ない */
            if (remain < 0) remain = 0;
            int tmpRemain = remain;
            if (tmpRemain > 0) {
                try {
                    istream.read(buffer, 0, tmpRemain);
                } catch (Exception e) {
                    /* ignore for this translation */
                }
                /* 最終は終端コードを入れる */
                /* ただしランダムアクセスか、残りサイズが丁度セクタサイズなら入れない */
                if (item.getFileAttr().unmatchType(FILE_TYPE_RANDOM_MASK.getValue(), FILE_TYPE_RANDOM_MASK.getValue())
                        && size > tmpRemain) {
                    buffer[tmpRemain] = (byte)basic.diskBasicParam.getTextTerminateCode();
                    tmpRemain++;
                }
            }
            if (size > tmpRemain) {
                /* バッファの余りは0サプレス */
                Arrays.fill(buffer, tmpRemain, size, (byte)0);
            }
            len = remain;
        } else {
            /* 継続 */
            try {
                istream.read(buffer, 0, size);
            } catch (Exception e) {
                /* ignore for this translation */
            }
            len = size;
        }
        /* 必要なら反転 */
        basic.invertMem(buffer, size);
        return len;
    }
}
