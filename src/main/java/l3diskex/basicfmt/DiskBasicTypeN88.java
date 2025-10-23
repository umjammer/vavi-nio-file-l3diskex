/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryN88;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 * DiskBasicTypeN88
 */
public class DiskBasicTypeN88 extends DiskBasicTypeFAT8<DirectoryN88> {

    public DiskBasicTypeN88(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super(basic, fat, dir);
    }

    @Override
    public int getEmptyGroupNumber() {
        int new_num = INVALID_GROUP_NUMBER;
        // 管理エリアに近い位置から検索

        // トラック当たりのグループ数
        int grps_per_trk = basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup();
        // 最大グループ数
        int max_group = basic.getFatEndGroup() - managedStartGroup;
        if (max_group < managedStartGroup)
            max_group = managedStartGroup;
        max_group = max_group * 2 - 1;

        for (int i = 0; i <= max_group; i++) {
            int i2 = i / grps_per_trk;
            int i4 = i / grps_per_trk / 2;
            int num;
            if ((i2 & 1) == 0) {   // parity check
                num = (i2 & 1) == 0
                        ? (i4 * grps_per_trk * 2) + i2 * grps_per_trk + i % grps_per_trk
                        : (i4 * grps_per_trk * 2) + i2 * grps_per_trk + i % grps_per_trk;
            } else {
                num = (i2 & 1) == 0
                        ? (i4 * grps_per_trk * 2) + i2 * grps_per_trk + i % grps_per_trk
                        : (i4 * grps_per_trk * 2) + i2 * grps_per_trk + i % grps_per_trk;
            }
            if (basic.getFatEndGroup() < num)
                continue;
            int gnum = getGroupNumber(num);
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                new_num = num;
                break;
            }
        }
        return new_num;
    }

    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (basic.diskBasicParam.getFatEndGroup() == 0) {
            int end_group = basic.getTracksPerSideOnBasic()
                    * basic.getSidesPerDiskOnBasic()
                    * basic.getSectorsPerTrackOnBasic();
            end_group /= basic.getSectorsPerGroup();
            basic.diskBasicParam.setFatEndGroup(end_group - 1);
        }
        return 1.0;
    }

    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = super.checkFat(is_formatting);
        if (valid_ratio >= 0.0) {
            List<Integer> groups = basic.diskBasicParam.getReservedGroups();
            for (int idx = 0; idx < groups.size(); idx++) {
                int grp = getGroupNumber(groups.get(idx));
                if (grp != basic.diskBasicParam.getGroupSystemCode()) {
                    valid_ratio = -1.0;
                    break;
                }
            }
        }
        return valid_ratio;
    }

    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        DiskImageTrack track = basic.getTrack(basic.getManagedTrackNumber(), basic.diskBasicParam.getFatSideNumber());
        if (track == null)
            return false;
        List<DiskImageSector> sectors = track.getSectors();
        if (sectors == null)
            return false;

        int id_sec = (basic.diskBasicParam.getDirEndSector() + 1) % basic.getSectorsPerTrackOnBasic();
        for (int idx = 0; idx < sectors.size(); idx++) {
            DiskImageSector sector = sectors.get(idx);
            if (sector != null) {
                sector.fill(sector.getSectorNumber() != id_sec
                        ? basic.diskBasicParam.getFillCodeOnFAT()
                        : 0);
            }
        }

        List<Integer> grps = basic.diskBasicParam.getReservedGroups();
        for (int i = 0; i < grps.size(); i++) {
            setGroupNumber(grps.get(i), basic.diskBasicParam.getGroupSystemCode());
        }
        return true;
    }

    public int CalcDataSizeOnLastSector(DiskBasicDirItem<DirectoryN88> item,
                                        InputStream istream,
                                        OutputStream ostream,
                                        byte[] sector_buffer,
                                        int sector_size,
                                        int remain_size) {
        if (item.needCheckEofCode()) {
            byte eof_code = basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
            byte null_code = basic.invertUint8((byte) 0);
            int len = sector_size - 1;
            for (; len >= 0; len--) {
                if (sector_buffer[len] != eof_code && sector_buffer[len] != null_code)
                    break;
            }
            if (len >= 0)
                sector_size = len + 1;
        } else {
            if (istream != null) {
                // In a real implementation we would use istream.getLength()
                // but this is a placeholder that simply returns the
                // original sector size.
                sector_size = sector_size;
            } else {
                sector_size = remain_size;
            }
        }
        return sector_size;
    }

    @Override
    public int writeFile(DiskBasicDirItem<DirectoryN88> item,
                         InputStream istream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sector_num,
                         int group_num,
                         int next_group,
                         int sector_end,
                         int seq_num) throws IOException {
        boolean need_eof_code = item.needCheckEofCode();
        int len = 0;

        if (remain <= size) {
            if (remain < 0)
                remain = 0;
            if (remain > 0) {
                if (need_eof_code) {
                    int bytesRead = istream.read(buffer, 0, remain);
                    if (bytesRead + 1 == remain) {
                        buffer[remain - 1] = basic.diskBasicParam.getTextTerminateCode();
                    }
                } else {
                    istream.read(buffer, 0, remain);
                }
            }
            if (size > remain) {
                java.util.Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            istream.read(buffer, 0, size);
            len = size;
        }
        basic.invertMem(buffer, size);

        return len;
    }
}
