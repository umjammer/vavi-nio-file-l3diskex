/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DirectoryFalcom;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;


public class DiskBasicDirItemFalcom extends DiskBasicDirItem<DirectoryFalcom> {

    /** Directory data. */
    private final DiskBasicDirData<DirectoryFalcom> m_data = new DiskBasicDirData<>();

    private DiskBasicDirItemFalcom() {
        super();
    }

    private DiskBasicDirItemFalcom(DiskBasicDirItemFalcom src) {
        super();
    }

    /** Constructor with only DiskBasic. */
    public DiskBasicDirItemFalcom(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryFalcom.class);
    }

    /** Constructor with sector data. */
    public DiskBasicDirItemFalcom(DiskBasic basic,
                                  DiskImageSector n_sector,
                                  int n_secpos,
                                  byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryFalcom.class, n_data, dataP);
    }

    /** Full constructor. */
    public DiskBasicDirItemFalcom(DiskBasic basic,
                                  int n_num,
                                  DiskBasicGroupItem n_gitem,
                                  DiskImageSector n_sector,
                                  int n_secpos,
                                  byte[] n_data, int dataP,
                                  SectorParam n_next,
                                  boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryFalcom.class, n_data, dataP);

        used(checkUsed(n_unuse[0]));
        n_unuse[0] = (n_unuse[0] || (m_data.data().name[0] == (byte) 0xFF));

        calcFileSize();
    }

    @Override
    public void setDataPtr(int n_num,
                           DiskBasicGroupItem n_gitem,
                           DiskImageSector n_sector,
                           int n_secpos,
                           byte[] n_data,
                           int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryFalcom.class, n_data, dataP);
    }

    @Override
    public boolean check(boolean[] last) {
        DirectoryFalcom data = m_data.data();
        byte first = data.name[0];
        return (first == 0
                || first == (byte) 0xFF
                || (first >= 0x20 && first < 0x7F));
    }

    @Override
    public boolean delete() {
        // Delete is simply marking the first byte as the delete code.
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
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
        return (val < 0) ? 0 : val;
    }

    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) {
        int calc_file_size = 0;
        int calc_groups = 0;

        int start_group = getStartGroup(fileunit_num);
        int last_group = getLastGroup();
        for (int grp = start_group; grp <= last_group; grp++) {
            int next_grp = (grp < last_group) ? grp + 1 : 0xFFFF;
            basic.getNumsFromGroup(grp, next_grp, basic.getSectorSize(), 0, group_items);
            calc_file_size += (basic.getSectorSize() * basic.getSectorsPerGroup());
            calc_groups++;
        }

        group_items.addNums(calc_groups);
        int file_size = getFileSize();
        group_items.addSize((calc_file_size <= file_size && file_size > 0) ? file_size : calc_file_size);
        group_items.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());
    }

    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        int sub_type = basic.diskBasicParam.getFormatSubTypeNumber();
        DirectoryFalcom data = m_data.data();
        switch (sub_type) {
            case 1:
                data.startGroup.track = (byte) (val % 256);
                data.startGroup.sector = (byte) (val / 256);
                break;
            default:
                data.startGroup.track = (byte) (val / basic.diskBasicParam.getSectorsPerTrackOnBasic());
                data.startGroup.sector = (byte) ((val % basic.diskBasicParam.getSectorsPerTrackOnBasic()) + 1);
                break;
        }
    }

    @Override
    public int getStartGroup(int fileunit_num) {
        int sub_type = basic.diskBasicParam.getFormatSubTypeNumber();
        int val;
        switch (sub_type) {
            case 1:
                val = m_data.data().startGroup.sector * 256 + m_data.data().startGroup.track;
                break;
            default:
                val = m_data.data().startGroup.track;
                if (val >= basic.diskBasicParam.getTracksPerSideOnBasic() * basic.getSidesPerDisk()) {
                    val = basic.diskBasicParam.getTracksPerSideOnBasic() * basic.getSidesPerDisk();
                }
                val *= basic.diskBasicParam.getSectorsPerTrackOnBasic();
                val += (m_data.data().startGroup.sector - 1);
                break;
        }
        return (val < 0) ? 0 : val;
    }

    @Override
    public void setLastGroup(int val) {
        int sub_type = basic.diskBasicParam.getFormatSubTypeNumber();
        switch (sub_type) {
            case 1:
                m_data.data().endGroup.track = (byte) (val % 256);
                m_data.data().endGroup.sector = (byte) (val / 256);
                break;
            default:
                m_data.data().endGroup.track = (byte) (val / basic.diskBasicParam.getSectorsPerTrackOnBasic());
                m_data.data().endGroup.sector = (byte) ((val % basic.diskBasicParam.getSectorsPerTrackOnBasic()) + 1);
                break;
        }
    }

    @Override
    public int getLastGroup() {
        int sub_type = basic.diskBasicParam.getFormatSubTypeNumber();
        int val;
        switch (sub_type) {
            case 1:
                val = m_data.data().endGroup.sector * 256 + m_data.data().endGroup.track;
                break;
            default:
                val = m_data.data().endGroup.track;
                if (val >= basic.getTracksPerSideOnBasic() * basic.getSidesPerDisk()) {
                    val = 0;
                }
                val *= basic.getSectorsPerTrackOnBasic();
                val += (m_data.data().endGroup.sector - 1);
                break;
        }
        return (val < 0) ? 0 : val;
    }

    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    @Override
    public int recalcFileSizeOnSave(java.io.InputStream istream, int file_size) {
        return file_size;
    }

    @Override
    public int getStartAddress() {
        return m_data.data().startAddr;
    }

    @Override
    public int getEndAddress() {
        return m_data.data().endAddr;
    }

    @Override
    public int getExecuteAddress() {
        return m_data.data().execAddr;
    }

    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    @Override
    public DirectoryFalcom getData() {
        return m_data.data();
    }

    @Override
    public boolean copyData(DirectoryFalcom val) {
        return m_data.copy(val);
    }

    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir(), 1);
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
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXEC_ADDR", m_data.data().execAddr);
        vals.add("START_ADDR", m_data.data().startAddr);
        vals.add("END_ADDR", m_data.data().endAddr);
        vals.add("START_GROUP", m_data.data().startGroup.getBytes(), 4);
        vals.add("END_GROUP", m_data.data().endGroup.getBytes(), 4);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return (!unuse && m_data.data().name[0] != (byte) 0xFF);
    }

    @Override
    public void setFileType1(int val) {
    }
}
