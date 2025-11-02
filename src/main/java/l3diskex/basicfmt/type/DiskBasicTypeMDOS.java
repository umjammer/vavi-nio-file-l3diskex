package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMDOS.DirectoryMdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;


/**
 * MDOS の処理
 */
public class DiskBasicTypeMDOS extends DiskBasicTypeFAT16<DirectoryMdos> {

    public DiskBasicTypeMDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMdos> dir) {
        super(basic, fat, dir);
    }

    @Override
    public double checkFat(boolean is_formatting) throws IOException {
        // 重複チェック
        double valid_ratio = checkFatDuplicated(is_formatting, 1, 0x1fff);

        if (valid_ratio < 0.0) return valid_ratio;

        // FATの最初はシステム
        for (int pos = 0; pos < 2; pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum != basic.diskBasicParam.getGroupSystemCode()) {
                valid_ratio = -1.0;
                break;
            }
        }

        return valid_ratio;
    }

    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        // グループ数
        if (basic.getFatEndGroup() == 0) {
            int end_group = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            basic.diskBasicParam.setFatEndGroup(end_group - 1);
        }
        return 1.0;
    }

    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        super.getUsableDiskSize(disk_size, group_size);
    }

    @Override
    public int getStartSectorFromGroup(int group_num) {
        return super.getStartSectorFromGroup(group_num);
    }

    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return super.getEndSectorFromGroup(group_num, next_group, sector_start, sector_size, remain_size);
    }

    @Override
    public int calcDataStartSectorPos() {
        return 0;
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
            // Track 0 is reserved
            sector.fill((byte) 0xee,
                    basic.diskBasicParam.getSectorsPerTrackOnBasic() +
                            basic.diskBasicParam.getSectorsPerFat() +
                            basic.diskBasicParam.getDirEndSector() -
                            basic.diskBasicParam.getDirStartSector() + 1,
                    0);
        }
        return true;
    }

    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryMdos> item,
                                        InputStream istream,
                                        OutputStream ostream,
                                        byte[] sector_buffer,
                                        int sectorOffset, int sector_size,
                                        int remain_size) {
        if (item.needCheckEofCode()) {
            // 終端コード($00)の1つ前までを出力
            byte eof_code = basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
            for (int len = 0; len < remain_size; len++) {
                if (sector_buffer[len] == eof_code) {
                    remain_size = len;
                    break;
                }
            }
        }
        return remain_size;
    }

    @Override
    public void deleteGroupNumber(int group_num) {
        // 未使用にする
        setGroupNumber(group_num, 0);
    }
}
