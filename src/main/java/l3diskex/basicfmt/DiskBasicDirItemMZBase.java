/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;


//
// ディレクトリ１アイテム MZ Base
//
public abstract class DiskBasicDirItemMZBase<T extends DirectoryT> extends DiskBasicDirItem<T> {

    /** Constructor – only a reference to the disk. */
    public DiskBasicDirItemMZBase(DiskBasic basic) {
        super(basic);
    }

    /** Constructor – initialize from sector data. */
    public DiskBasicDirItemMZBase(DiskBasic basic,
                                  DiskImageSector n_sector,
                                  int n_secpos,
                                  byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);
    }

    /** Full constructor – all parameters. */
    public DiskBasicDirItemMZBase(DiskBasic basic,
                                  int n_num,
                                  DiskBasicGroupItem n_gitem,
                                  DiskImageSector n_sector,
                                  int n_secpos,
                                  byte[] n_data, int dataP,
                                  SectorParam n_next,
                                  boolean[] n_unuse) {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);
    }

    protected void setFileSizeBase(int val) {
    }

    protected int getFileSizeBase() {
        return 0;
    }

    protected void preCalcFileSize() {
    }

    protected void calcAllGroups(int calcFlags,
                                 int[] groupNum,
                                 int[] remain,
                                 int[] secSize,
                                 int[] endSec,
                                 Object userData) {
        groupNum[0]++;
    }

    protected void postCalcAllGroups(Object userData) {
    }

    @Override
    public boolean delete() throws IOException {
        // エントリの先頭にコードを入れる
        setFileType1(basic.diskBasicParam.getDeleteCode());
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
    public void calcFileUnitSize(int fileunitNum) {
        if (!isUsed()) return;

        getUnitGroups(fileunitNum, groups);
    }

    protected void preCalcAllGroups(int[] calcFlags,
                                    int[] groupNum,
                                    int[] remain,
                                    int[] secSize,
                                    Object[] userData) {
    }

    @Override
    public void getUnitGroups(int fileunitNum, DiskBasicGroups groupItems) {
        // ファイルサイズ
        int calcFileSize = getFileSizeBase();
        preCalcFileSize();;

        int[] groupNum = {getStartGroup(fileunitNum)};
        int[] remain = {calcFileSize};
        int[] secSize = {basic.getSectorSize()};
        int[] calcFlags = {0};
        int calcGroups = 0;
        Object[] userData = new Object[1];

        preCalcAllGroups(calcFlags, groupNum, remain, secSize, userData);

        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        while (remain[0] > 0 && limit >= 0) {
            // 使用しているか
            boolean usedGroup = type.isUsedGroupNumber(groupNum[0]);
            if (usedGroup) {
                int[] endSec = {-1};
                basic.getNumsFromGroup(groupNum[0], 0, secSize[0], remain[0], groupItems, (int[]) userData[0]);
                calcAllGroups(calcFlags[0], groupNum, remain, secSize, endSec, userData[0]);
                calcGroups++;
                remain[0] -= (secSize[0] * basic.getSectorsPerGroup());
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
