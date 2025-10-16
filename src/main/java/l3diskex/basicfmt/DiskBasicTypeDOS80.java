/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;


/**
 * public class DiskBasicTypeDOS80
 */
public class DiskBasicTypeDOS80 extends DiskBasicTypeFAT8 {

    public DiskBasicTypeDOS80(DiskBasic basic, DiskBasicFat fat,
                              DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /**
     * 空きFAT位置を返す
     */
    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;

        int grpsPerTrk = basic.getSectorsPerTrackOnBasic() /
                          basic.getSectorsPerGroup();
        int maxGroup = basic.getFatEndGroup() - managedStartGroup;

        if (maxGroup < 0) {
            return newNum;                      /* no free groups */
        }

        for (int i = 0; i <= maxGroup; i++) {
            int i2 = i / grpsPerTrk;
            int i4 = i2 / 2;
            int num;

            if ((i & 1) == 0) {                /* even sector */
                num = i * 2;
                if ((i2 & 1) == 0) {
                    num += i4;
                } else {
                    num -= i4;
                }
            } else {                           /* odd sector */
                num = (i + 1) * 2;
                if ((i2 & 1) == 0) {
                    num -= i4;
                } else {
                    num += i4;
                }
            }

            if (basic.getFatEndGroup() < num) {
                continue;                       /* past the last FAT group */
            }

            int gnum = getGroupNumber(num);
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }

        return newNum;
    }

    /*-----------------------  check / assign FAT area  -------------------*/
    /**
     * FATエリアをチェック
     */
    @Override
    public double checkFat(boolean isFormatting) {
        /* base class implementation is assumed to be sufficient */
        double baseCheck = super.checkFat(isFormatting);

        /* additional checks specific to DOS80 */
        List<Integer> grps = basic.diskBasicParam.getReservedGroups();
        for (int i = 0; i < grps.size(); i++) {
            int code = getGroupNumber(grps.get(i));
            if (code == basic.diskBasicParam.getGroupUnusedCode()) {
                /* nothing to do – placeholder */
            }
        }
        return baseCheck;
    }

    /*-----------------------  format operations  ------------------------*/
    /**
     * セクタデータを指定コードで埋める
     */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        /* In the original code the sector is filled with the code used on format */
        byte fillCode = basic.diskBasicParam.getFillCodeOnFormat();
        sector.fill(fillCode);
    }

    /**
     * セクタデータを埋めた後の個別処理 – FAT予約済みをセット
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        boolean valid = true;
        DiskImageSector sector;

        /* -------- DIR ---------------------------------------------------*/
        for (int sec = (int) basic.diskBasicParam.getDirStartSector();
             sec <= basic.diskBasicParam.getDirEndSector(); sec++) {

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

        /* -------- FAT ---------------------------------------------------*/
        if (valid) {
            for (int sec = 0; sec < basic.diskBasicParam.getSectorsPerFat(); sec++) {
                sector = basic.getManagedSector(
                        sec + (int) basic.diskBasicParam.getFatStartSector() - 1);
                if (sector == null) {
                    valid = false;
                    break;
                }
                sector.fill(basic.diskBasicParam.getFillCodeOnFAT());
            }
        }

        /* -------- 予約済みにする ---------------------------------------*/
        /* トラック０のクラスタをシステム領域に設定 */
        for (int gnum = 0; gnum < basic.getSectorsPerGroup(); gnum++) {
            setGroupNumber(gnum, basic.diskBasicParam.getGroupSystemCode());
        }

        /* システムで使用している部分を予約済みにする */
        List<Integer> grps = basic.diskBasicParam.getReservedGroups();
        for (int g : grps) {
            setGroupNumber(g, basic.diskBasicParam.getGroupSystemCode());
        }

        return valid;
    }

    /**/
    /*  data access (read / verify)                                        */
    /**/
    /**
     * ファイルの最終セクタのデータサイズを求める
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem item,
                                        InputStream istream,
                                        OutputStream ostream,
                                        byte[] sector_buffer,
                                        int sector_pos,
                                        int sector_size,
                                        int remain_size) {
        /* 直接計算できないので残りサイズそのまま返す */
        if (istream != null) {
            try {
                sector_size = istream.available() % sector_size;
            } catch (Exception e) {
                /* ignore – keep original size */
            }
        } else {
            sector_size = remain_size;
        }
        return sector_size;
    }

    /**/
    /*  save / write                                                       */
    /**/
    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem item,
                         InputStream istream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sector_num,
                         int group_num,
                         int next_group,
                         int sector_end,
                         int seq_num) {
        int len = 0;

        if (remain <= size) {                       /* 残りが少ない */
            if (remain < 0) remain = 0;
            if (remain > 0) {
                try {
                    int r = istream.read(buffer, 0, remain);
                    len = (r >= 0) ? r : 0;
                } catch (Exception e) {
                    len = 0;
                }
            }
            if (size > remain) {
                /* 余った領域は 0 で埋める */
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {                                    /* 継続書き込み */
            try {
                int r = istream.read(buffer, 0, size);
                len = (r >= 0) ? r : 0;
            } catch (Exception e) {
                len = 0;
            }
        }

        /* 必要ならメモリを反転 */
        basic.invertMem(buffer, size);
        return len;
    }
}
