/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.BasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.BasicFat.DiskBasicFatBuffers;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_MISSING;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;
import static l3diskex.basicfmt.DiskBasicDirItemX1HU.EXTERNAL_X1_DEFAULT;
import static l3diskex.basicfmt.DiskBasicDirItemX1HU.EXTERNAL_X1_SWORD;


/**
 * X1 Hu-BASICの処理
 *
 * DiskBasicParam 固有パラメータ
 * @li IPLString : セクタ1のIPL
 */
public class DiskBasicTypeX1HU extends DiskBasicType {

    /** Public constructor */
    public DiskBasicTypeX1HU(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /** FAT位置をセット */
    @Override
    public void setGroupNumber(int num, int val) {
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        for(int j=0; j<bufs.size(); j++) {
            DiskBasicFatBuffers fatbufs = bufs.get(j);
            // 8bit FAT + 8bit
            for(int i=0; i<fatbufs.size(); i++) {
                DiskBasicFatBuffer fatbuf = fatbufs.get(i);
                int half_size = fatbuf.getSize() >> 1;
                if (num < half_size) {
                    fatbuf.set(num, basic.invertUint8((byte) val));
                    fatbuf.set(num + half_size, basic.invertUint8((byte) ((val & 0xff00) >> 8)));
                    break;
                }
                num -= fatbuf.getSize();
            }
        }
    }

    /** FAT位置を返す */
    @Override
    public int getGroupNumber(int num) {
        int new_num = INVALID_GROUP_NUMBER;
        DiskBasicFatBuffers fatbufs = fat.getDiskBasicFatBuffers(0);
        if (fatbufs == null) {
            return new_num;
        }
        // 8bit FAT + 8bit
        for(int i=0; i<fatbufs.size(); i++) {
            DiskBasicFatBuffer fatbuf = fatbufs.get(i);
            int half_size = (int)(fatbuf.getSize() >> 1);
            if (num < half_size) {
                new_num = basic.invertUint8((byte) fatbuf.get(num));
                if (basic.getFatEndGroup() >= 0x80) {
                    new_num |= ((int)basic.invertUint8((byte) fatbuf.get(num + half_size)) << 8);
                }
                break;
            }
            num -= fatbuf.getSize();
        }
        return new_num;
    }

    /** 空きFAT位置を返す */
    @Override
    public int getEmptyGroupNumber() {
        for (int i = 0; i <= basic.getFatEndGroup(); i++) {
            int gnum = getGroupNumber(i);
            if (gnum == INVALID_GROUP_NUMBER) {
                return i;
            }
        }
        return INVALID_GROUP_NUMBER;
    }

    /** 次の空き位置を返す */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        for (int i = 0; i <= basic.getFatEndGroup(); i++) {
            if (getGroupNumber(i) == INVALID_GROUP_NUMBER) {
                return i;
            }
        }
        return INVALID_GROUP_NUMBER;
    }

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        if (bufs.matchData8(0, basic.invertUint8((byte) 0x01)) != bufs.size()) {
            return -1.0;
        }

        int end = basic.getFatEndGroup();
        int[] tbl = new int[(int) end + 1];
        for (int i = 0; i < tbl.length; i++) tbl[i] = 0;
        for (int pos = 0; pos <= end; pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum > 0 && gnum <= end) {
                tbl[(int) gnum]++;
            }
        }
        for (int i = 0; i < tbl.length; i++) {
            if (tbl[i] > 1) {
                valid_ratio = 0.0;
                break;
            }
        }

        // Hu-BASIC か S-OS SWORD か
        if (valid_ratio >= 0) {
            int atype = basic.diskBasicParam.getVariousIntegerParam("DefaultAsciiType");
            int pt = 0;
            for (int i = 0; i < 2; i++) {
                DiskImageSector sector = null;
                switch(i) {
                    case 0:
                        // IPL領域
                        sector = basic.getSector(0, 0, 1);
                        break;
                    case 1:
                        // ディレクトリ領域
                        sector = basic.getManagedSector(basic.diskBasicParam.getDirStartSector() - 1);
                        break;
                }
                if (sector != null) {
                    int hfind = sector.find("CZ8F".getBytes(), 4);
                    int sfind = sector.find("SWORD".getBytes(), 5);
                    if (atype == EXTERNAL_X1_DEFAULT && hfind >= 0) {
                        pt++;
                    } else if (atype == EXTERNAL_X1_SWORD && sfind >= 0) {
                        pt++;
                    }
                }
            }

            if (pt < 1) valid_ratio /= 2.0;
        }

        return valid_ratio;
    }

    /** ディスクから各パラメータを取得＆必要なパラメータを計算 */
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (basic.getFatEndGroup() == 0) {
            int end = basic.getFatEndGroup();
            // calculate
            basic.diskBasicParam.setFatEndGroup((int) end);
        }
        return 1.0;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        int groupSize = basic.getFatEndGroup() + 1;
        if (groupSize >= basic.diskBasicParam.getGroupFinalCode()) {
            groupSize -= basic.diskBasicParam.getGroupFinalCode();
        }
        int diskSize = groupSize * basic.getSectorSize() * basic.getSectorsPerGroup();
        disk_size[0] = diskSize;
        group_size[0] = groupSize;
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        int fsize = 0;
        int grps = 0;
        int fsts = FAT_AVAIL_USED.getValue();
        fatAvailability.empty();
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum == FAT_AVAIL_USED.getValue()) {
                fsize = 0;
                grps = 0;
            }
            if (gnum == FAT_AVAIL_MISSING.getValue()) {
                fsts = FAT_AVAIL_MISSING.getValue();
                fsize = 0;
                grps = 0;
            }
            fatAvailability.Add(fsts, fsize, grps);
        }
    }

    /** グループ番号から開始セクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        if (group_num > basic.diskBasicParam.getGroupSystemCode()) {
            group_num -= basic.diskBasicParam.getGroupFinalCode();
        }
        return (int) (group_num * basic.getSectorsPerGroup());
    }

    /** グループ番号から最終セクタ番号を得る */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return 0;
    }

    /** ディレクトリがルートかを判定 */
    @Override
    public boolean isRootDirectory(int group_num) {
        return false;
    }

    /** ディレクトリ名の変更 */
    public boolean renameOnMakingDirectory(StringBuilder dir_name) {
        // placeholder
        return true;
    }
}
