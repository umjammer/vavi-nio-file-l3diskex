/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.util.List;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.DirectoryX1Hu;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_MISSING;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_DEFAULT;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_SWORD;


/**
 * X1 Hu-BASICの処理
 * <p>
 * DiskBasicParam 固有パラメータ
 *
 * <li>IPLString : セクタ1のIPL</li>
 */
public class DiskBasicTypeX1HU extends DiskBasicType<DirectoryX1Hu> {

    /** Public constructor */
    public DiskBasicTypeX1HU(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryX1Hu> dir) {
        super(basic, fat, dir);
    }

    /** FAT位置をセット */
    @Override
    public void setGroupNumber(int num, int val) {
        DiskBasicFatArea fatArea = this.fat.getDiskBasicFatArea();
        for (int j = 0; j < fatArea.size(); j++) {
            List<DiskBasicFatBuffer> fatBufList = fatArea.get(j);
            // 8bit FAT + 8bit
            for (DiskBasicFatBuffer fatBuf : fatBufList) {
                int halfSize = fatBuf.getSize() >> 1;
                if (num < halfSize) {
                    fatBuf.set(num, basic.invertUint8((byte) val));
                    fatBuf.set(num + halfSize, basic.invertUint8((byte) ((val & 0xff00) >> 8)));
                    break;
                }
                num -= fatBuf.getSize();
            }
        }
    }

    /** FAT位置を返す */
    @Override
    public int getGroupNumber(int num) {
        int newNum = INVALID_GROUP_NUMBER;
        List<DiskBasicFatBuffer> fatBufList = fat.getDiskBasicFatBuffers(0);
        if (fatBufList == null) {
            return newNum;
        }
        // 8bit FAT + 8bit
        for (DiskBasicFatBuffer fatBuf : fatBufList) {
            int halfSize = fatBuf.getSize() >> 1;
            if (num < halfSize) {
                newNum = basic.invertUint8((byte) fatBuf.get(num));
                if (basic.getFatEndGroup() >= 0x80) {
                    newNum |= (int) basic.invertUint8((byte) fatBuf.get(num + halfSize)) << 8;
                }
                break;
            }
            num -= fatBuf.getSize();
        }
        return newNum;
    }

    /** 空きFAT位置を返す */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;
        // 若い番号順に検索
        for (int num = 0; num <= basic.getFatEndGroup(); num++) {
            if (num == basic.getGroupFinalCode()) num += basic.getGroupFinalCode();
            int groupNum = getGroupNumber(num);
            if (groupNum == basic.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    /** 次の空き位置を返す */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        int newNum = INVALID_GROUP_NUMBER;

        // グループが連続するように検索
        int groupMax = basic.getFatEndGroup() + 1;
        int groupStart = currentGroup;
        int groupEnd;
        int dir;
        boolean found = false;

        dir = 1; // 始めは+方向、なければ-方向に検索
        for (int i = 0; i < 2; i++) {
            groupEnd = (dir > 0 ? groupMax : -1);
            for(int group = groupStart; group != groupEnd; group += dir) {
                if (dir > 0 && group == basic.getGroupFinalCode()) group += basic.getGroupFinalCode();
                else if (dir < 0 && group == basic.getGroupSystemCode()) group -= basic.getGroupFinalCode();
                int groupNum = getGroupNumber(group);
                if (groupNum == basic.getGroupUnusedCode()) { // 0xff
                    newNum = group;
                    found = true;
                    break;
                }
            }
            if (found) break;
            dir = -dir;
        }
        return newNum;
    }

    /** FATエリアをチェック */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = 1.0;

        // FAT領域の先頭がFAT領域のセクタ数であるか
        DiskBasicFatArea fatArea = fat.getDiskBasicFatArea();
        if (fatArea.matchData8(0, basic.invertUint8((byte) 0x01)) != fatArea.size()) {
            return -1.0;
        }

        int end = basic.getFatEndGroup();
        int[] table = new int[end + 1];

        // 同じグループ番号が重複しているか
        for (int pos = 0; pos <= end; pos++) {
            int groupNum = getGroupNumber(pos);
            if (groupNum > 0 && groupNum <= end) {
                table[groupNum]++;
            }
        }
        // 同じグループ番号が重複している場合エラー
        for (int value : table) {
            if (value > 1) {
                validRatio = 0.0;
                break;
            }
        }

        // Hu-BASIC か S-OS SWORD か
        if (validRatio >= 0) {
            int aType = basic.getVariousIntegerParam("DefaultAsciiType");
            int point = 0;
            for (int i = 0; i < 2; i++) {
                DiskImageSector sector = switch (i) {
                    case 0 ->
                        // IPL領域
                            basic.getSector(0, 0, 1);
                    case 1 ->
                        // ディレクトリ領域
                            basic.getManagedSector(basic.getDirStartSector() - 1);
                    default -> null;
                };
                if (sector != null) {
                    int hFind = sector.find("CZ8F".getBytes(), 4);
                    int sFind = sector.find("SWORD".getBytes(), 5);
                    if (aType == EXTERNAL_X1_DEFAULT && hFind >= 0) {
                        point++;
                    } else if (aType == EXTERNAL_X1_SWORD && sFind >= 0) {
                        point++;
                    }
                }
            }

            if (point < 1) {
                validRatio /= 2.0;
            }
        }

        return validRatio;
    }

    /** ディスクから各パラメータを取得＆必要なパラメータを計算 */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() - 1;
            if (endGroup >= 0x80) {
                endGroup += 0x80;
            }
            basic.setFatEndGroup(endGroup);
        }

        return 1.0;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        if (groupSize[0] >= basic.getGroupFinalCode()) groupSize[0] -= basic.getGroupFinalCode();
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        int fSize = 0;
        int groups = 0;
        FatAvailability fStats = FAT_AVAIL_USED;
        fatAvailability.empty();
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            int groupNum = getGroupNumber(pos);
            if (pos >= basic.getGroupFinalCode() && pos <= basic.getGroupSystemCode()) {
                fStats = FAT_AVAIL_MISSING;
            } else if (groupNum == basic.getGroupUnusedCode()) {
                fSize = basic.getSectorSize() * basic.getSectorsPerGroup();
                groups = 1;
                fStats = FAT_AVAIL_FREE;
            } else if (groupNum >= basic.getGroupFinalCode() && groupNum <= basic.getGroupSystemCode()) {
                fStats = FAT_AVAIL_USED_LAST;
            }
            fatAvailability.add(fStats, fSize, groups);
        }
    }

    /** グループ番号から開始セクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        if (groupNum > basic.getGroupSystemCode()) {
            // 0x100以降の番号は0x80減算
            groupNum -= basic.getGroupFinalCode();
        }
        return groupNum * basic.getSectorsPerGroup();
    }

    /** グループ番号から最終セクタ番号を得る */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int sectorEnd = sectorStart + basic.getSectorsPerGroup() - 1;
        if (nextGroup >= basic.getGroupFinalCode() && nextGroup <= basic.getGroupSystemCode()) {
            // 最終グループの場合指定したセクタまで
            sectorEnd = sectorStart + (nextGroup - basic.getGroupFinalCode());
        }
        return sectorEnd;
    }

    /** ディレクトリがルートかを判定 */
    @Override
    public boolean isRootDirectory(int groupNum) {
        int secNum = basic.getDirEndSector();
        int startGroup = secNum / basic.getSectorsPerGroup();
        return groupNum < startGroup;
    }

    /** ディレクトリ名の変更 */
    @Override
    public boolean renameOnMakingDirectory(String[] dirName) {
        // 空や"."で始まるディレクトリは作成不可
        if (dirName[0].isEmpty() || dirName[0].startsWith(".")) {
            return false;
        }
        int pos = dirName[0].indexOf(".");
        if (pos != -1) {
            // 拡張子以下を除く
            dirName[0] = dirName[0].substring(0, pos);
        }
        // 拡張子を付ける
        dirName[0] += ".DIR";

        return true;
    }
}
