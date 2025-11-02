/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;

import l3diskex.Parambase.MyAttribute;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.diritem.DiskBasicDirItemL32D.DirectoryL32d;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Parambase.MyAttributes.findType;
import static l3diskex.Parambase.MyAttributes.findUpperCase;


/**
 * DiskBasicDirItemL32D
 * <p>
 * 1-item directory for L3/S1 BASIC double‑density 2D/2HD
 */
public class DiskBasicDirItemL32D extends DiskBasicDirItemFAT8<DirectoryL32d> {

    /**
     * ディレクトリエントリ L3,S1 ５インチ,８インチ(倍密度)
     */
    @Serdes
    public static class DirectoryL32d implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte type2;
        @Element(sequence = 5)
        public byte startGroup;
        @Element(sequence = 6)
        public short endBytes; // used size of end cluster (big endian)

        @Element(sequence = 7)
        public byte[] reserved = new byte[16]; // char reserved[16]

        public static final int SIZE = 32;
    }

    /** Directory data */
    private final DiskBasicDirData<DirectoryL32d> m_data = new DiskBasicDirData<>();

    /** */
    public DiskBasicDirItemL32D(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryL32d.class);
    }

    /** */
    public DiskBasicDirItemL32D(DiskBasic basic, DiskImageSector sector,
                                int secPos, byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);

        m_data.attach(DirectoryL32d.class, data, dataP);
    }

    /** */
    public DiskBasicDirItemL32D(DiskBasic basic, int num,
                                DiskBasicGroupItem gItem, DiskImageSector sector,
                                int secPos, byte[] data, int dataP, SectorParam next,
                                boolean[] unuse) throws IOException {
        super(basic, num, gItem, sector, secPos, data, dataP, next, unuse);

        // L3 2D
        m_data.attach(DirectoryL32d.class, data, dataP);

        used(checkUsed(unuse[0]));

        // Calculate file size and group count
        calcFileSize();
    }

    @Override
    public void setDataPtr(int num, DiskBasicGroupItem gItem,
                           DiskImageSector sector, int secPos,
                           byte[] data, int dataP, SectorParam next) throws IOException {
        super.setDataPtr(num, gItem, sector, secPos, data, dataP, next);

        m_data.attach(DirectoryL32d.class, data, dataP);
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        if (m_data.data().name[0] == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        // Unexpected attribute value
        if (m_data.data().type2 != 0 && m_data.data().type2 != (byte) 0xff) {
            valid = false;
        }
        if (m_data.data().name[0] == (byte) 0xff) {
            last[0] = true;
        }
        return valid;
    }

    /** Override: Check used flag */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (m_data.data().name[0] != 0 && m_data.data().name[0] != (byte) 0xff);
    }

    /** Override: Delete item */
    @Override
    public boolean delete() {
        // Delete by writing delete code to first byte
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /** Position of file name */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // L3 2D
        if (num == 0) {
            size[0] = len[0] = m_data.data().name.length;
            return m_data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /** Position of file extension */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

    /** File type 1 getter */
    @Override
    protected int getFileType1() {
        return m_data.data().type & 0xff;
    }

    /** File type 2 getter */
    @Override
    public int getFileType2() {
        return m_data.data().type2 & 0xff;
    }

    /** File type 1 setter */
    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    /** File type 2 setter */
    @Override
    protected void setFileType2(int val) {
        m_data.data().type2 = (byte) (val & 0xff);
    }

    /** Set data size of last sector */
    private void setDataSizeOnLastSecotr(int val) {
        // L3/S1 2D/2HD
        m_data.data().endBytes = (short) val;
    }

    /** Get data size of last sector */
    private int getDataSizeOnLastSector() {
        // L3/S1 2D/2HD
        return m_data.data().endBytes & 0xffff;
    }

    /** Data size */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /** Return data pointer */
    @Override
    public DirectoryL32d getData() {
        return m_data.data();
    }

    /** Copy data */
    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val);
    }

    /** Clear data */
    @Override
    public void clearData() {
        m_data.fill(0);
    }

    /** Set file size */
    @Override
    public void setFileSize(int val) {
        super.setFileSize(val);
        // Set size of last sector
        setDataSizeOnLastSecotr(val % basic.getSectorSize());
    }

    /** Set start group number */
    @Override
    public void setStartGroup(int fileunitNum, int val, int size) {
        // L3/S1 2D/2HD
        m_data.data().startGroup = (byte) (val & 0xff);
    }

    /** Get start group number */
    @Override
    public int getStartGroup(int fileunitNum) {
        // L3/S1 2D/2HD
        return m_data.data().startGroup & 0xff;
    }

    /** Recalculate file size from groups */
    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) {
        if (isUsed() && occupiedSize >= 0) {
            occupiedSize = occupiedSize - basic.getSectorSize() + getDataSizeOnLastSector();
        }
        return occupiedSize;
    }

    /** Add file extension */
    @Override
    protected String addExtension(int fileType1, String name) {
        // L3/S1 BASIC
        // 拡張子を自動で付加する
        String newName = "";

        int len = name.length();
        String ext = name.length() >= 4 ? name.substring(name.length() - 4) : "";
        MyAttribute sa = findUpperCase(basic.diskBasicParam.getAttributesByExtension(), ext.length() >= 3 ? ext.substring(ext.length() - 3) : "");
        if (sa != null && ext.startsWith(".")) {
            len -= 4;
            if (len >= 0) newName = name.substring(0, len);
            else newName = "";
        } else {
            int dot = name.indexOf('.');
            if (dot >= 0) {
                return name;
            } else {
                newName = name;
            }
        }

        int val = fileType1 >= TYPE_NAME_1_BASIC && fileType1 <= TYPE_NAME_1_MACHINE ? 1 << fileType1 : 0;
        sa = findType(basic.diskBasicParam.getAttributesByExtension(), val, 0x7);
        if (sa != null) {
            newName += ".";
            newName += sa.getName();
        }

        return newName;
    }

    /**
     * Set internal data for attribute dialog
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXT", m_data.data().ext, m_data.data().ext.length);
        vals.add("TYPE", m_data.data().type);
        vals.add("TYPE2", m_data.data().type2);
        vals.add("START_GROUP", m_data.data().startGroup);
        vals.add("END_BYTES", (byte) m_data.data().endBytes, true);
        vals.add("RESERVED", m_data.data().reserved, m_data.data().reserved.length);
    }
}
