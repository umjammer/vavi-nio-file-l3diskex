package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;


/**
 * FAT12の処理
 */
public class DiskBasicTypeFAT12<T extends DirectoryT> extends DiskBasicTypeFATBase<T> {

    /** */
    public DiskBasicTypeFAT12(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super(basic, fat, dir);
    }

    @Override
    public void setGroupNumber(int num, int val) {
        fat.getDiskBasicFatArea().setData12LE(num, val);
    }

    @Override
    public int getGroupNumber(int num) {
        return fat.getDiskBasicFatArea().getData12LE(0, num);
    }

    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        // 重複チェック
        double validRatio = checkFatDuplicated(isFormatting, 2, 0x1ff);

        return validRatio;
    }

    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        calcDiskFreeSizeBase(wrote, 2, 0xff8);
    }

    /*  Adjust remaining size for an EOF marker  */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<T> item,
                                        InputStream istream, OutputStream ostream,
                                        byte[] sectorBuffer, int sectorOffset, int sectorSize,
                                        int remainSize) {

        if (istream != null) {
            // ベリファイ時のみEOFが自動で付加されることがある
            if (item.needCheckEofCode()) {
                // 終端コードの1つ前までを出力
                byte eofCode = basic.diskBasicParam.getTextTerminateCode();
                int len = remainSize - 1;
                if (sectorBuffer[len] == eofCode) {
                    remainSize = len;
                }
            }
        }
        return remainSize;
    }
}
