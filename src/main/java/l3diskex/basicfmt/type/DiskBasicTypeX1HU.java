/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.BasicCommon.DirectoryX1Hu;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffers;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_DEFAULT;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_SWORD;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_MISSING;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;


/**
 * X1 Hu-BASICの処理
 * <p>
 * DiskBasicParam 固有パラメータ
 *
 * @li IPLString : セクタ1のIPL
 */
public class DiskBasicTypeX1HU extends DiskBasicType<DirectoryX1Hu> {

    /** Public constructor */
    public DiskBasicTypeX1HU(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryX1Hu> dir) {
        super(basic, fat, dir);
    }

    /** FAT位置をセット */
    @Override
    public void setGroupNumber(int num, int val) {
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        for (int j = 0; j < bufs.size(); j++) {
            DiskBasicFatBuffers fatbufs = bufs.get(j);
            // 8bit FAT + 8bit
            for (int i = 0; i < fatbufs.size(); i++) {
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
        for (int i = 0; i < fatbufs.size(); i++) {
            DiskBasicFatBuffer fatbuf = fatbufs.get(i);
            int half_size = fatbuf.getSize() >> 1;
            if (num < half_size) {
                new_num = basic.invertUint8((byte) fatbuf.get(num));
                if (basic.getFatEndGroup() >= 0x80) {
                    new_num |= ((int) basic.invertUint8((byte) fatbuf.get(num + half_size)) << 8);
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
        int new_num = INVALID_GROUP_NUMBER;
        // 若い番号順に検索
        for (int num = 0; num <= basic.getFatEndGroup(); num++) {
            if (num == basic.diskBasicParam.getGroupFinalCode()) num += basic.diskBasicParam.getGroupFinalCode();
            int gnum = getGroupNumber(num);
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                new_num = num;
                break;
            }
        }
        return new_num;
    }

    /** 次の空き位置を返す */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        int new_num = INVALID_GROUP_NUMBER;

        // グループが連続するように検索
        int group_max = basic.getFatEndGroup() + 1;
        int group_start = curr_group;
        int group_end;
        int dir;
        boolean found = false;

        dir = 1; // 始めは+方向、なければ-方向に検索
        for(int i=0; i<2; i++) {
            group_end = (dir > 0 ? group_max : -1);
            for(int g = group_start; g != group_end; g += dir) {
                if (dir > 0 && g == basic.diskBasicParam.getGroupFinalCode()) g += basic.diskBasicParam.getGroupFinalCode();
                else if (dir < 0 && g == basic.diskBasicParam.getGroupSystemCode()) g -= basic.diskBasicParam.getGroupFinalCode();
                int gnum = getGroupNumber(g);
                if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {	// 0xff
                    new_num = g;
                    found = true;
                    break;
                }
            }
            if (found) break;
            dir = -dir;
        }
        return new_num;
    }

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        // FAT領域の先頭がFAT領域のセクタ数であるか
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        if (bufs.matchData8(0, basic.invertUint8((byte) 0x01)) != bufs.size()) {
            return -1.0;
        }

        int end = basic.getFatEndGroup();
        int[] tbl = new int[end + 1];

        // 同じグループ番号が重複しているか
        for (int pos = 0; pos <= end; pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum > 0 && gnum <= end) {
                tbl[gnum]++;
            }
        }
        // 同じグループ番号が重複している場合エラー
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
                switch (i) {
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

            if (pt < 1) {
                valid_ratio /= 2.0;
            }
        }

        return valid_ratio;
    }

    /** ディスクから各パラメータを取得＆必要なパラメータを計算 */
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (basic.getFatEndGroup() == 0) {
            int end = basic.getFatEndGroup();
            // calculate
            basic.diskBasicParam.setFatEndGroup(end);
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
        FatAvailability fsts = FAT_AVAIL_USED;
        fatAvailability.empty();
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum == FAT_AVAIL_USED.ordinal()) {
                fsize = 0;
                grps = 0;
            }
            if (gnum == FAT_AVAIL_MISSING.ordinal()) {
                fsts = FAT_AVAIL_MISSING;
                fsize = 0;
                grps = 0;
            }
            fatAvailability.add(fsts, fsize, grps);
        }
    }

    /** グループ番号から開始セクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        if (group_num > basic.diskBasicParam.getGroupSystemCode()) {
            group_num -= basic.diskBasicParam.getGroupFinalCode();
        }
        return group_num * basic.getSectorsPerGroup();
    }

    /** グループ番号から最終セクタ番号を得る */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        int sector_end = sector_start + basic.getSectorsPerGroup() - 1;
        if (next_group >= basic.diskBasicParam.getGroupFinalCode() && next_group <= basic.diskBasicParam.getGroupSystemCode()) {
            // 最終グループの場合指定したセクタまで
            sector_end = sector_start + (next_group - basic.diskBasicParam.getGroupFinalCode());
        }
        return sector_end;
    }

    /** ディレクトリがルートかを判定 */
    @Override
    public boolean isRootDirectory(int group_num) {
        int sec_num = basic.diskBasicParam.getDirEndSector();
        int start_group = sec_num / basic.getSectorsPerGroup();
        return group_num < start_group;
    }

    /** ディレクトリ名の変更 */
    @Override
    public boolean renameOnMakingDirectory(String[] dir_name) {
        int pos;
        // 空や"."で始まるディレクトリは作成不可
        if (dir_name[0].isEmpty() || dir_name[0].startsWith(".")) {
            return false;
        }
        if ((pos = dir_name[0].indexOf(".")) != -1) {
            // 拡張子以下を除く
            dir_name[0] = dir_name[0].substring(0, pos);
        }
        // 拡張子を付ける
        dir_name[0] += ".DIR";

        return true;
    }
}
