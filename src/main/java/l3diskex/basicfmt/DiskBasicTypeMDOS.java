package l3diskex.basicfmt;

import java.io.InputStream;
import java.io.OutputStream;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;


/**
 * MDOS の処理
 */
public class DiskBasicTypeMDOS extends DiskBasicTypeFAT16 {

    /**
     * Constructor
     */
    public DiskBasicTypeMDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /*
     * access to FAT area
     */
    // (none defined in the original header)

    /**
     * check / assign FAT area
     */
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = checkFatDuplicated(is_formatting, 1, 0x1fff);
        // check that the first two FAT entries are the system code
        int group_system = basic.diskBasicParam.getGroupSystemCode();
        for (int pos = 0; pos < 2; pos++) {
            if (getGroupNumber(pos) != group_system) {
                return 0.0;
            }
        }
        return valid_ratio;
    }

    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        int end_group = basic.getTracksPerSideOnBasic()
                * basic.getSidesPerDiskOnBasic()
                * basic.getSectorsPerTrackOnBasic();
        basic.diskBasicParam.setFatEndGroup(end_group - 1);
        return 1.0;
    }

    /**
     * disk size
     */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        super.getUsableDiskSize(disk_size, group_size);
    }

    /**
     * file chain
     */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        return super.getStartSectorFromGroup(group_num);
    }

    @Override
    public int getEndSectorFromGroup(int group_num, int next_group,
                                     int sector_start, int sector_size,
                                     int remain_size) {
        return super.getEndSectorFromGroup(group_num, next_group,
                sector_start, sector_size, remain_size);
    }

    @Override
    public int calcDataStartSectorPos() {
        return 0;
    }

    /**
     * format
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
            // Track 0 is reserved
            int count = basic.diskBasicParam.getSectorsPerTrackOnBasic()
                    + basic.diskBasicParam.getSectorsPerFat()
                    + basic.diskBasicParam.getDirEndSector()
                    - basic.diskBasicParam.getDirStartSector() + 1;
            sector.fill((byte) 0xee, count, 0);
        }
        return true;
    }

    /**
     * data access (read / verify)
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem item,
                                        InputStream istream,
                                        OutputStream ostream,
                                        byte[] sector_buffer,
                                        int sectorOffsrt, int sector_size,
                                        int remain_size) {
        if (item.needCheckEofCode()) {
            byte eof_code = (byte) basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
            for (int len = 0; len < remain_size; len++) {
                if (sector_buffer[len] == eof_code) {
                    remain_size = len;
                    break;
                }
            }
        }
        return remain_size;
    }

    /**
     * delete
     */
    @Override
    public void deleteGroupNumber(int group_num) {
        // Unused
        setGroupNumber(group_num, 0);
    }

    /*
     * property
     */
    // (none defined in the original header)
}
