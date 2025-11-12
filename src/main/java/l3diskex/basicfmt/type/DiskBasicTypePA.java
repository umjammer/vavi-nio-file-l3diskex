/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemN88.DirectoryN88;


/**
 * PASOPIA T-BASICの処理
 * <p>
 * DiskBasicParam
 *
 * <li>ReservedGroups : Group 予約済みにするグループ（クラスタ）番号</li>
 */
public class DiskBasicTypePA extends DiskBasicTypeN88 {

    public DiskBasicTypePA(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super(basic, fat, dir);
    }

    /**
     * グループ番号から開始セクタ番号を得る
     *
     * @param groupNum グループ番号
     * @return 開始セクタ番号
     */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        // グループ（クラスタ）番号はサイド（サーフェース）優先なので、
        // セクタ番号はトラック優先になるよう変換する。
        int sides = basic.getSidesPerDiskOnBasic();
        int groupPerTrack = basic.getSectorsPerTrack() / basic.getSectorsPerGroup();
        int groupPerSide = sides * groupPerTrack;
        int numOfGroups = (groupNum / groupPerSide) * groupPerSide + (groupNum % sides) * groupPerTrack + ((groupNum % groupPerSide) / sides);
        return numOfGroups * basic.getSectorsPerGroup();
    }
}
