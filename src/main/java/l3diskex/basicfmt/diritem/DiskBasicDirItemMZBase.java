/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;


/**
 * ディレクトリ１アイテム MZ Base
 */
public abstract class DiskBasicDirItemMZBase<T extends Directory> extends DiskBasicDirItem<T> {

    /** */
    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);
    }

    /** */
    @Override
    public void init(DiskBasic basic,
                     DiskImageSector sector,
                     int sectorPos,
                     byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);
    }

    /** */
    @Override
    public void init(DiskBasic basic,
                     int num,
                     DiskBasicGroupItem groupItem,
                     DiskImageSector sector,
                     int sectorPos,
                     byte[] data, int dataP,
                     SectorParam next,
                     boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);
    }

    /** データ内にファイルサイズをセット */
    protected void setFileSizeBase(int val) {
    }

    /** データ内のファイルサイズを返す */
    protected int getFileSizeBase() {
        return 0;
    }

    /** グループ取得計算前処理 */
    protected void preCalcFileSize() {
    }

    // グループ取得計算前処理
    protected void preCalcAllGroups(int[] calcFlags, int[] groupNum, int[] remain, int[] secSize, Object[] userData) {
    }

    /** グループ取得計算中処理 */
    protected void calcAllGroups(int calcFlags, int[] groupNum, int[] remain, int[] secSize, int[] endSec, Object userData) {
        groupNum[0]++;
    }

    /** グループ取得計算後処理 */
    protected void postCalcAllGroups(Object userData) {
    }

    @Override
    public boolean delete() throws IOException {
        // エントリの先頭にコードを入れる
        setFileType1(basic.getDeleteCode());
        used(false);
        // 開始グループを未使用にする
        type.setGroupNumber(getStartGroup(0), 0);
        return true;
    }

    @Override
    public void initialData() {
        clearData();
    }

    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        setFileSizeBase(val);
    }

    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) {
        // ファイルサイズ
        int calcFileSize = getFileSizeBase();
        preCalcFileSize();

        int[] groupNum = {getStartGroup(fileUnitNum)};
        int[] remain = {calcFileSize};
        int[] secSize = {basic.getSectorSize()};
        int[] calcFlags = {0};
        int calcGroups = 0;
        Object[] userData = new Object[1];

        preCalcAllGroups(calcFlags, groupNum, remain, secSize, userData);

        int limit = basic.getFatEndGroup() + 1;
        while (remain[0] > 0 && limit >= 0) {
            // 使用しているか
            boolean usedGroup = type.isUsedGroupNumber(groupNum[0]);
            if (usedGroup) {
                int[] endSector = {-1};
                basic.getNumsFromGroup(groupNum[0], 0, secSize[0], remain[0], groupItems, (int[]) userData[0]);
                calcAllGroups(calcFlags[0], groupNum, remain, secSize, endSector, userData[0]);
                calcGroups++;
                remain[0] -= secSize[0] * basic.getSectorsPerGroup();
            } else {
                limit = 0;
            }
            limit--;
        }

        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        postCalcAllGroups(userData[0]);
    }

    @Override
    public boolean isDeletable() {
        boolean valid = true;
        DiskBasicFileType attr = getFileAttr();

        if (attr.isVolume()) {
            // Volume numbers cannot be deleted.
            valid = false;
        } else if (attr.isDirectory()) {
            String name = getFileNamePlainStr();
            if (".".equals(name) || "..".equals(name)) {
                // Directories "." and ".." cannot be deleted.
                valid = false;
            }
        }
        return valid;
    }

    @Override
    public boolean isFileNameEditable() {
        boolean valid = true;
        DiskBasicFileType attr = getFileAttr();

        if (attr.isVolume()) {
            // Volume numbers cannot be edited.
            valid = false;
        } else if (attr.isDirectory()) {
            String name = getFileNamePlainStr();
            if (".".equals(name) || "..".equals(name)) {
                // Directories "." and ".." cannot be edited.
                valid = false;
            }
        }
        return valid;
    }
}
