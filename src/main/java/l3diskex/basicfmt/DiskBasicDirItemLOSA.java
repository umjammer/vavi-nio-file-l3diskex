/*---
 * File   : BasicDirItemLOSA.java
 *
 * Purpose: Java translation of the C++ classes that implement a disk
 *          basic directory item for the L‑os Angeles format.
 *
 * Note   : The translation is literal and therefore contains many
 *          placeholders and stubs.  Only the API surface that is used
 *          by the original code is reproduced.  GUI widgets, wxWidgets
 *          types and some CP/M specific helpers are replaced by
 *          simple Java equivalents or empty stubs.
 *
 * Author : (C) Sasaji – translated by ChatGPT (2025‑10‑06)
 *--*/

package l3diskex.basicfmt;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DirectoryLosa;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;


// --
// Public API – the class that corresponds to DiskBasicDirItemLOSA
// --
public class DiskBasicDirItemLOSA extends DiskBasicDirItemMSDOS {

    public static final int FILE_TYPE_LOSA_BINARY = 0xA0;          // 0xa0

    public static final String TYPE_NAME_LOSA_BINARY = "LA binary";

    public DiskBasicDirItemLOSA(DiskBasic basic) {
        super(basic);
    }

    public DiskBasicDirItemLOSA(DiskBasic basic,
                                DiskImageSector sector,
                                int secpos,
                                byte[] data, int dataP) {
        super(basic, sector, secpos, data, dataP);
    }

    public DiskBasicDirItemLOSA(DiskBasic basic,
                                int num,
                                DiskBasicGroupItem gitem,
                                DiskImageSector sector,
                                int secpos,
                                byte[] data, int dataP,
                                SectorParam next,
                                boolean[] unuse) throws IOException {
        super(basic, num, gitem, sector, secpos, data, dataP, next, unuse);
    }

    /** File name / extension handling */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        switch (num) {
            case 0:
                size[0] = len[0] = m_data.data().losa.name.length;
                return m_data.data().losa.name;
            default:
                size[0] = len[0] = 0;
                return null;
        }
    }

    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().losa.ext.length;
        return m_data.data().losa.ext;
    }

    /** File type (attribute 2) */
    @Override
    public int getFileType2() {
        return m_data.data().losa.binaryType & 0xFF;
    }

    @Override
    protected void setFileType2(int val) {
        m_data.data().losa.binaryType = (byte) (val & 0xFF);
    }

    /** Generic attribute handling */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) {
            return;
        }

        // MS‑DOS: type 1 is the high byte of the type field
        setFileType1((ftype & 0xFF00) >> 8);

        // type 2 is derived from the origin field
        int t2 = file_type.getOrigin() >> 16;
        setFileType2(t2);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int t2 = getFileType2();

        DiskBasicFileType ft = new DiskBasicFileType();
        ft.setType((t2 << 8) | t1);
        ft.setOrigin(t2 << 16);
        ft.setFormat(basic.getFormatTypeNumber());
        return ft;
    }

    @Override
    public String getFileAttrStr() {
        String attr = "";
        int ftype = getFileAttr().getType();
        int t2 = getFileAttr().getOrigin() >> 8;

        if (t2 == FILE_TYPE_LOSA_BINARY) {
            attr = TYPE_NAME_LOSA_BINARY;
        }

        // The original code calls GetFileAttrStrSub – we skip it here
        if (attr.isEmpty()) {
            attr = "---";
        }
        return attr;
    }

    /** Address handling */
    @Override
    public boolean hasAddress() {
        return true;
    }

    @Override
    public int getStartAddress() {
        String addr = new String(m_data.data().losa.startAddr);
        int lval = Integer.parseInt(addr, 16);
        return lval;
    }

    @Override
    public int getExecuteAddress() {
        String addr = new String(m_data.data().losa.execAddr);
        int lval = Integer.parseInt(addr, 16);
        return lval;
    }

    @Override
    public void setStartAddress(int val) {
        String addr = String.format("%04X", val);
        byte[] b = addr.getBytes();
        System.arraycopy(b, 0, m_data.data().losa.startAddr, 0, 4);
    }

    @Override
    public void setExecuteAddress(int val) {
        String addr = String.format("%04X", val);
        byte[] b = addr.getBytes();
        System.arraycopy(b, 0, m_data.data().losa.execAddr, 0, 4);
    }

    @Override
    public int getDataSize() {
        return DirectoryLosa.SIZE;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", m_data.data().losa.name, m_data.data().losa.name.length);
        vals.add("EXT", m_data.data().losa.ext, m_data.data().losa.ext.length);
        vals.add("TYPE", m_data.data().losa.type);
        vals.add("START_ADDR", m_data.data().losa.startAddr, m_data.data().losa.startAddr.length);
        vals.add("BINARY_TYPE", m_data.data().losa.binaryType);
        vals.add("EXEC_ADDR", m_data.data().losa.execAddr, m_data.data().losa.execAddr.length);
        vals.add("RESERVED", m_data.data().losa.reserved);
        vals.add("WTIME", m_data.data().losa.wtime);
        vals.add("WDATE", m_data.data().losa.wdate);
        vals.add("START_GROUP", m_data.data().losa.startGroup);
        vals.add("FILE_SIZE", m_data.data().losa.fileSize);
    }
}
