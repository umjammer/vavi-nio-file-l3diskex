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
    public double checkFat(boolean isFormatting) throws IOException {
        // 重複チェック
        double validRatio = checkFatDuplicated(isFormatting, 1, 0x1fff);

        if (validRatio < 0.0) return validRatio;

        // FATの最初はシステム
        for (int pos = 0; pos < 2; pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum != basic.getGroupSystemCode()) {
                validRatio = -1.0;
                break;
            }
        }

        return validRatio;
    }

    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        // グループ数
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        super.getUsableDiskSize(diskSize, groupSize);
    }

    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return super.getStartSectorFromGroup(groupNum);
    }

    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return super.getEndSectorFromGroup(groupNum, nextGroup, sectorStart, sectorSize, remainSize);
    }

    @Override
    public int calcDataStartSectorPos() {
        return 0;
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));
            // Track 0 is reserved
            sector.fill((byte) 0xee,
                    basic.getSectorsPerTrackOnBasic() +
                            basic.getSectorsPerFat() +
                            basic.getDirEndSector() -
                            basic.getDirStartSector() + 1,
                    0);
        }
        return true;
    }

    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryMdos> item,
                                        InputStream iStream,
                                        OutputStream oStream,
                                        byte[] sectorBuffer,
                                        int sectorOffset, int sectorSize,
                                        int remainSize) {
        if (item.needCheckEofCode()) {
            // 終端コード($00)の1つ前までを出力
            byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
            for (int len = 0; len < remainSize; len++) {
                if (sectorBuffer[len] == eofCode) {
                    remainSize = len;
                    break;
                }
            }
        }
        return remainSize;
    }

    @Override
    public void deleteGroupNumber(int groupNum) {
        // 未使用にする
        setGroupNumber(groupNum, 0);
    }
}
