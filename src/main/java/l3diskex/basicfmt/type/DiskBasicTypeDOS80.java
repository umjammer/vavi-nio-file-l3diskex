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
 * @li CanMountEachSides  表/裏面を別々に扱うか
 * @li ReservedGroups  Group 予約済みにするグループ（クラスタ）番号
 * @li DefaultStartAddress  BASIC指定時の開始アドレス
 * @li DefaultExecuteAddress  BASIC指定時の実行アドレス
 */
public class DiskBasicTypeDOS80 extends DiskBasicTypeFAT8<DirectoryDos80> {

    /** */
    public DiskBasicTypeDOS80(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryDos80> dir) {
        super(basic, fat, dir);
    }

    /**
     * 空きFAT位置を返す
     */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = DiskBasicType.INVALID_GROUP_NUMBER;
        // 管理エリアに近い位置から検索

        // トラック当たりのグループ数
        int grpsPerTrk = basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup();
        // 最大グループ数
        int maxGroup = basic.getFatEndGroup() - managedStartGroup;
        if (maxGroup < managedStartGroup) maxGroup = managedStartGroup;
        maxGroup = maxGroup * 2 - 1;

        for (int i = 0; i <= maxGroup; i++) {
            int i2 = i / grpsPerTrk;
            int i4 = i / grpsPerTrk / 2;
            int num;
            if ((i & 1) == 0) {
                num = managedStartGroup - ((i4 + 1) * grpsPerTrk) + (i % grpsPerTrk);
            } else {
                num = managedStartGroup + ((i4 + 1) * grpsPerTrk) + (i % grpsPerTrk);
            }
            if (basic.getFatEndGroup() < num) {
                continue;
            }
            int gnum = getGroupNumber(num);
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
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
        double valid_ratio = super.checkFat(isFormatting);
        if (valid_ratio >= 0.0) {
            // FAT,ディレクトリエリアはシステム予約となっているか
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

    /**
     * セクタデータを指定コードで埋める
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    /**
     * セクタデータを埋めた後の個別処理 – FAT予約済みをセット
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        boolean valid = true;

        DiskImageSector sector;

        // DIR
        for (int sec = basic.diskBasicParam.getDirStartSector(); sec <= basic.diskBasicParam.getDirEndSector(); sec++) {
            sector = basic.getManagedSector(sec - 1);
            if (sector == null) {
                valid = false;
                break;
            }
            sector.fill(basic.diskBasicParam.getFillCodeOnDir());

            sector = basic.getManagedSector(sec + 1);
            if (sector == null) {
                valid = false;
                break;
            }

            sector.fill(basic.diskBasicParam.getFillCodeOnDir());
        }

        // FAT
        if (valid) {
            for (int sec = 0; sec < basic.diskBasicParam.getSectorsPerFat(); sec++) {
                sector = basic.getManagedSector(sec + basic.diskBasicParam.getFatStartSector() - 1);
                if (sector == null) {
                    valid = false;
                    break;
                }
                sector.fill(basic.diskBasicParam.getFillCodeOnFAT());
            }
        }

        // トラック０は予約済みにする
        for (int gnum = 0; gnum < basic.getSectorsPerGroup(); gnum++) {
            setGroupNumber(gnum, basic.diskBasicParam.getGroupSystemCode());
        }

        // システムで使用している部分を予約済みにする
        List<Integer> grps = basic.diskBasicParam.getReservedGroups();
        for (int g : grps) {
            setGroupNumber(g, basic.diskBasicParam.getGroupSystemCode());
        }

        return valid;
    }

    /**
     * ファイルの最終セクタのデータサイズを求める
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryDos80> item,
                                        InputStream istream,
                                        OutputStream ostream,
                                        byte[] sector_buffer,
                                        int sectorOffset,
                                        int sector_size,
                                        int remain_size) throws IOException {
        // 直接計算できないので残りサイズそのまま返す
        if (istream != null) {
            sector_size = istream.available() % sector_size;
        } else {
            sector_size = remain_size;
        }
        return sector_size;
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryDos80> item,
                         InputStream istream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sector_num,
                         int group_num,
                         int next_group,
                         int sector_end,
                         int seq_num) throws IOException {
        int len = 0;
        if (remain <= size) {
            // 残りが少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                int r = istream.read(buffer, 0, remain);
                len = (r >= 0) ? r : 0;
            }
            if (size > remain) {
                // 余った領域は 0 で埋める
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            int r = istream.read(buffer, 0, size);
            len = (r >= 0) ? r : 0;
        }
        // 必要なら反転
        basic.invertMem(buffer, size);

        return len;
    }
}
