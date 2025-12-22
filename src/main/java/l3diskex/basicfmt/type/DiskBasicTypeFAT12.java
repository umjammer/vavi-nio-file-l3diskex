package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;


/**
 * FAT12 processing
 */
public abstract class DiskBasicTypeFAT12<T extends Directory> extends DiskBasicTypeFATBase<T> {

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super.init(basic, fat, dir);
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
        // Duplication check
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
                                        InputStream iStream, OutputStream oStream,
                                        byte[] sectorBuffer, int sectorOffset, int sectorSize,
                                        int remainSize) {

        if (iStream != null) {
            // EOF may be automatically added only during verify
            if (item.needCheckEofCode()) {
                // Output up to one byte before the termination code
                byte eofCode = basic.getTextTerminateCode();
                int len = remainSize - 1;
                if (sectorBuffer[len] == eofCode) {
                    remainSize = len;
                }
            }
        }
        return remainSize;
    }
}
