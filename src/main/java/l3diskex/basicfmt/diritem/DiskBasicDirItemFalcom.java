/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFalcom.DirectoryFalcom;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;


/** ディレクトリ１アイテム Falcom DOS */
public class DiskBasicDirItemFalcom extends DiskBasicDirItem<DirectoryFalcom> {

    /** Directory data. */
    private final DiskBasicDirData<DirectoryFalcom> data = new DiskBasicDirData<>();

    /**
     * ディレクトリエントリ Falcom (16bytes)
     */
    @Serdes
    public static class DirectoryFalcom implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[6];
        @Element(sequence = 2)
        public short execAddress;
        @Element(sequence = 3)
        public short startAddress;
        @Element(sequence = 4)
        public short endAddress;
        @Element(sequence = 5)
        public GroupPointer startGroup = new GroupPointer();
        @Element(sequence = 6)
        public GroupPointer endGroup = new GroupPointer();

        public static class GroupPointer {

            @Element(sequence = 1)
            public byte track;
            @Element(sequence = 2)
            public byte sector;

            public byte[] getBytes() {
                return new byte[0];
            }
        }

        public static final int SIZE = 16;
    }

    /** */
    public DiskBasicDirItemFalcom(DiskBasic basic) {
        super(basic);

        data.alloc(DirectoryFalcom.class);
    }

    /** */
    public DiskBasicDirItemFalcom(DiskBasic basic,
                                  DiskImageSector sector,
                                  int secPos,
                                  byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);

        this.data.attach(DirectoryFalcom.class, data, dataP);
    }

    /** */
    public DiskBasicDirItemFalcom(DiskBasic basic,
                                  int num,
                                  DiskBasicGroupItem groupItem,
                                  DiskImageSector sector,
                                  int secPos,
                                  byte[] data, int dataP,
                                  SectorParam next,
                                  boolean[] unuse) throws IOException {
        super(basic, num, groupItem, sector, secPos, data, dataP, next, unuse);

        this.data.attach(DirectoryFalcom.class, data, dataP);

        used(checkUsed(unuse[0]));
        unuse[0] = (unuse[0] || (this.data.data().name[0] == (byte) 0xff));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    @Override
    public void setData(int n_num,
                        DiskBasicGroupItem groupItem,
                        DiskImageSector sector,
                        int sectorPos,
                        byte[] data,
                        int dataPos, SectorParam next) throws IOException {
        super.setData(n_num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryFalcom.class, data, dataPos);
    }

    @Override
    public boolean check(boolean[] last) {
        DirectoryFalcom data = this.data.data();
        byte first = data.name[0];
        return (first == 0 ||
                first == (byte) 0xff ||
                (first >= 0x20 && first < 0x7f));
    }

    @Override
    public boolean delete() {
        // Delete is simply marking the first byte as the delete code.
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && data.data().name[0] != (byte) 0xff;
    }

    @Override
    public void setFileType1(int val) {
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int val = FILE_TYPE_BINARY_MASK.getValue();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, 0);
    }

    @Override
    public String getFileAttrStr() {
        return "";
    }

    @Override
    public int getFileSize() {
        int val = getEndAddress() - getStartAddress() + 1;
        if (val < 0) val = 0;
        return val;
    }

    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) {
        int calcFileSize = 0;
        int calcGroups = 0;

        int startGroup = getStartGroup(fileUnitNum);
        int lastGroup = getLastGroup();
        for (int group = startGroup; group <= lastGroup; group++) {
            int nextGroup = (group < lastGroup) ? group + 1 : 0xFFFF;
            basic.getNumsFromGroup(group, nextGroup, basic.getSectorSize(), 0, groupItems);
            calcFileSize += (basic.getSectorSize() * basic.getSectorsPerGroup());
            calcGroups++;
        }

        groupItems.addNums(calcGroups);
        int fileSize = getFileSize();
        groupItems.addSize((calcFileSize <= fileSize && fileSize > 0) ? fileSize : calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());
    }

    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int subType = basic.getFormatSubTypeNumber();
        switch (subType) {
            case 1:
                data.data().startGroup.track = (byte) (val % 256);
                data.data().startGroup.sector = (byte) (val / 256);
                break;
            default:
                data.data().startGroup.track = (byte) (val / basic.getSectorsPerTrackOnBasic());
                data.data().startGroup.sector = (byte) ((val % basic.getSectorsPerTrackOnBasic()) + 1);
                break;
        }
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        int subType = basic.getFormatSubTypeNumber();
        int val;
        switch (subType) {
            case 1:
                val = (data.data().startGroup.sector & 0xff) * 256 + (data.data().startGroup.track & 0xff);
                break;
            default:
                val = data.data().startGroup.track & 0xff;
                if (val >= basic.getTracksPerSideOnBasic() * basic.getSidesPerDisk()) {
                    val = basic.getTracksPerSideOnBasic() * basic.getSidesPerDisk();
                }
                val *= basic.getSectorsPerTrackOnBasic();
                val += (data.data().startGroup.sector & 0xff) - 1;
                break;
        }
        if (val < 0) {
            val = 0;
        }
        return val;
    }

    @Override
    public void setLastGroup(int val) {
        int subType = basic.getFormatSubTypeNumber();
        switch (subType) {
            case 1:
                data.data().endGroup.track = (byte) (val % 256);
                data.data().endGroup.sector = (byte) (val / 256);
                break;
            default:
                data.data().endGroup.track = (byte) (val / basic.getSectorsPerTrackOnBasic());
                data.data().endGroup.sector = (byte) ((val % basic.getSectorsPerTrackOnBasic()) + 1);
                break;
        }
    }

    @Override
    public int getLastGroup() {
        int subType = basic.getFormatSubTypeNumber();
        int val;
        switch (subType) {
            case 1:
                val = (data.data().endGroup.sector & 0xff) * 256 + (data.data().endGroup.track & 0xff);
                break;
            default:
                val = data.data().endGroup.track & 0xff;
                if (val >= basic.getTracksPerSideOnBasic() * basic.getSidesPerDisk()) {
                    val = 0;
                }
                val *= basic.getSectorsPerTrackOnBasic();
                val += (data.data().endGroup.sector & 0xff) - 1;
                break;
        }
        if (val < 0) {
            val = 0;
        }
        return val;
    }

    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    @Override
    public int recalcFileSizeOnSave(java.io.InputStream iStream, int fileSize) {
        return fileSize;
    }

    @Override
    public int getStartAddress() {
        return data.data().startAddress & 0xffff;
    }

    @Override
    public int getEndAddress() {
        return data.data().endAddress & 0xffff;
    }

    @Override
    public int getExecuteAddress() {
        return data.data().execAddress & 0xffff;
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryFalcom getData() {
        return data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val);
    }

    @Override
    public void clearData() {
        data.fill(basic.getFillCodeOnDir(), 1);
    }

    @Override
    public int convFileTypeFromFileName(String filename) {
        return FILE_TYPE_BINARY_MASK.getValue();
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        return 0;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXEC_ADDR", data.data().execAddress);
        vals.add("START_ADDR", data.data().startAddress);
        vals.add("END_ADDR", data.data().endAddress);
        vals.add("START_GROUP", data.data().startGroup.getBytes(), 4);
        vals.add("END_GROUP", data.data().endGroup.getBytes(), 4);
    }
}
