/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemDOS80.DirectoryDos80;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 * PC-8001 DOSの処理
 * <p>
 * DiskBasicParam
 *
 * <li>CanMountEachSides  表/裏面を別々に扱うか</li>
 * <li>ReservedGroups  Group 予約済みにするグループ（クラスタ）番号</li>
 * <li>DefaultStartAddress  BASIC指定時の開始アドレス</li>
 * <li>DefaultExecuteAddress  BASIC指定時の実行アドレス</li>
 */
public class DiskBasicTypeDOS80 extends DiskBasicTypeFAT8<DirectoryDos80> {

    public static final int FORMAT_TYPE_DOS80 = 51;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_DOS80;
    }

    /** */
    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryDos80> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * 空きFAT位置を返す
     */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = DiskBasicType.INVALID_GROUP_NUMBER;
        // 管理エリアに近い位置から検索

        // トラック当たりのグループ数
        int groupsPerTrack = basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup();
        // 最大グループ数
        int maxGroup = basic.getFatEndGroup() - managedStartGroup;
        if (maxGroup < managedStartGroup) maxGroup = managedStartGroup;
        maxGroup = maxGroup * 2 - 1;

        for (int i = 0; i <= maxGroup; i++) {
            int i2 = i / groupsPerTrack;
            int i4 = i / groupsPerTrack / 2;
            int num;
            if ((i & 1) == 0) {
                num = managedStartGroup - ((i4 + 1) * groupsPerTrack) + (i % groupsPerTrack);
            } else {
                num = managedStartGroup + ((i4 + 1) * groupsPerTrack) + (i % groupsPerTrack);
            }
            if (basic.getFatEndGroup() < num) {
                continue;
            }
            int groupNum = getGroupNumber(num);
            if (groupNum == basic.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }

        return newNum;
    }

    /**
     * FATエリアをチェック
     */
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // FAT,ディレクトリエリアはシステム予約となっているか
            List<Integer> groups = basic.getReservedGroups();
            for (int group : groups) {
                int group_ = getGroupNumber(group);
                if (group_ != basic.getGroupSystemCode()) {
                    validRatio = -1.0;
                    break;
                }
            }
        }
        return validRatio;
    }

    /**
     * セクタデータを指定コードで埋める
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    /**
     * セクタデータを埋めた後の個別処理 – FAT予約済みをセット
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        boolean valid = true;

        DiskImageSector sector;

        // DIR
        for (int s = basic.getDirStartSector(); s <= basic.getDirEndSector(); s++) {
            sector = basic.getManagedSector(s - 1);
            if (sector == null) {
                valid = false;
                break;
            }
            sector.fill(basic.getFillCodeOnDir());

            sector = basic.getManagedSector(s + 1);
            if (sector == null) {
                valid = false;
                break;
            }

            sector.fill(basic.getFillCodeOnDir());
        }

        // FAT
        if (valid) {
            for (int s = 0; s < basic.getSectorsPerFat(); s++) {
                sector = basic.getManagedSector(s + basic.getFatStartSector() - 1);
                if (sector == null) {
                    valid = false;
                    break;
                }
                sector.fill(basic.getFillCodeOnFAT());
            }
        }

        // トラック０は予約済みにする
        for (int groupNum = 0; groupNum < basic.getSectorsPerGroup(); groupNum++) {
            setGroupNumber(groupNum, basic.getGroupSystemCode());
        }

        // システムで使用している部分を予約済みにする
        List<Integer> groups = basic.getReservedGroups();
        for (int group : groups) {
            setGroupNumber(group, basic.getGroupSystemCode());
        }

        return valid;
    }

    /**
     * ファイルの最終セクタのデータサイズを求める
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryDos80> item,
                                        InputStream iStream,
                                        OutputStream oStream,
                                        byte[] sectorBuffer,
                                        int sectorOffset,
                                        int sectorSize,
                                        int remainSize) throws IOException {
        // 直接計算できないので残りサイズそのまま返す
        if (iStream != null) {
            sectorSize = iStream.available() % sectorSize;
        } else {
            sectorSize = remainSize;
        }
        return sectorSize;
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryDos80> item,
                         InputStream iStream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sectorNum,
                         int groupNum,
                         int nextGroup,
                         int sectorEnd,
                         int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            // 残りが少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                int r = iStream.read(buffer, 0, remain);
                len = (r >= 0) ? r : 0;
            }
            if (size > remain) {
                // 余った領域は 0 で埋める
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            int r = iStream.read(buffer, 0, size);
            len = (r >= 0) ? r : 0;
        }
        // 必要なら反転
        basic.invertMemory(buffer, size);

        return len;
    }
}
