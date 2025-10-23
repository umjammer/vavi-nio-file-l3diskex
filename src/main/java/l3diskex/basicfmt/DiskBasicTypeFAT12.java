package l3diskex.basicfmt;

import java.io.InputStream;
import java.io.OutputStream;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;


/**
 * Concrete implementation of a FAT12/16 driver.
 */
public class DiskBasicTypeFAT12 extends DiskBasicTypeFATBase {

    /*-----  Public constructor used by client code  -----*/
    public DiskBasicTypeFAT12(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);        // initialise base with the three arguments
    }

    /*-----  FAT specific operations  -----*/
    @Override
    public void setGroupNumber(int num, int val) {
        /* Store a 12‑bit cluster value in the FAT area. */
        fat.getDiskBasicFatArea().setData12LE(num, val);
    }

    @Override
    public int getGroupNumber(int num) {
        /* Retrieve a 12‑bit cluster value from the FAT area. */
        return fat.getDiskBasicFatArea().getData12LE(0, num);
    }

    /*-----  High level FAT checks  -----*/
    @Override
    public double checkFat(boolean isFormatting) {
        /* In a real implementation this would check for duplicate
           clusters.  Here we simply return 1.0 (no duplicates). */
        double validRatio = checkFatDuplicated(isFormatting, 2, 0x1FF);
        return validRatio;
    }

    /*-----  Free space calculation  -----*/
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        /* Delegate to the base implementation. */
        calcDiskFreeSizeBase(wrote, 2, 0xFF8);
    }

    /*-----  Adjust remaining size for an EOF marker  -----*/
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem item,
                                        InputStream istream, OutputStream ostream,
                                        byte[] sectorBuffer, int offset, int sectorSize,
                                        int remainSize) {

        if (istream != null) {
            if (item.needCheckEofCode()) {
                byte eofCode = basic.diskBasicParam.getTextTerminateCode();
                int len = remainSize - 1;
                if (len >= 0 && sectorBuffer[len] == eofCode) {
                    remainSize = len;            // cut off the EOF marker
                }
            }
        }
        return remainSize;
    }
}
