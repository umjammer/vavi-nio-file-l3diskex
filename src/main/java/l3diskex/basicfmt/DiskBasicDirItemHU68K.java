/*
 * Sasaji (original C++ source)
 */

package l3diskex.basicfmt;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;


/**
 * Directory item for Human68k.
 *
 * This class extends {@link DiskBasicDirItemMSDOS} and provides the
 * Human68k-specific implementation of several virtual functions.
 */
public class DiskBasicDirItemHU68K extends DiskBasicDirItemMSDOS {

    /*------------------------------------------------------------------
     *  Data structure that represents a directory entry on a Human68k disk.
     *------------------------------------------------------------------*/
    private static class DirectoryHu68kT {
        // File name
        byte[] name   = new byte[9];
        // Secondary name
        byte[] name2  = new byte[9];
        // Extension
        byte[] ext    = new byte[4];
        // Type (8-bit)
        int  type     = 0;
        // Write time (2 bytes)
        int  wtime    = 0;
        // Write date (2 bytes)
        int  wdate    = 0;
        // Starting group
        int  start_group = 0;
        // File size (4 bytes)
        int  file_size   = 0;
    }

    /*------------------------------------------------------------------
     *  Container for the directory data.
     *------------------------------------------------------------------*/
    private static class BasicDirItemData {
        DirectoryHu68kT hu68k = new DirectoryHu68kT();

        /** Return true if this data represents the current file itself. */
        boolean isSelf() { return false; }   // Stub: actual logic omitted
    }

    /** The underlying data of this directory item. */
    private final BasicDirItemData m_data = new BasicDirItemData();

    /*------------------------------------------------------------------
     *  Constructors
     *------------------------------------------------------------------*/
    public DiskBasicDirItemHU68K(DiskBasic basic) {
        super(basic);
    }

    public DiskBasicDirItemHU68K(DiskBasic basic,
                                DiskImageSector n_sector,
                                int n_secpos,
                                byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
    }

    public DiskBasicDirItemHU68K(DiskBasic basic,
                                 int n_num,
                                 DiskBasicGroupItem n_gitem,
                                 DiskImageSector n_sector,
                                 int n_secpos,
                                 byte[] n_data,
                                 SectorParam n_next,
                                 boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
    }

    /*------------------------------------------------------------------
     *  Override: Set the data pointer for the directory item.
     *------------------------------------------------------------------*/
    @Override
    public void setDataPtr(int n_num,
                           DiskBasicGroupItem n_gitem,
                           DiskImageSector n_sector,
                           int n_secpos,
                           byte[] n_data,
                           SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, n_next);
    }

    /*------------------------------------------------------------------
     *  Override: Return the position of the file name.
     *------------------------------------------------------------------*/
    @Override
    public byte[] getFileNamePos(int num, int[] sizeLen) {
        /* The C++ code returns a pointer to the internal byte array
         * and also sets the size and length of the field.  In Java
         * we return a copy of the array (or the original array if
         * we wish to expose the internal buffer).  The caller must
         * interpret the sizeLen array accordingly.
         */
        switch (num) {
            case 0:
                sizeLen[0] = sizeLen[1] = m_data.hu68k.name.length;
                return m_data.hu68k.name;
            case 1:
                sizeLen[0] = sizeLen[1] = m_data.hu68k.name2.length;
                return m_data.hu68k.name2;
            default:
                sizeLen[0] = sizeLen[1] = 0;
                return null;
        }
    }

    /*------------------------------------------------------------------
     *  Override: Return the position of the file extension.
     *------------------------------------------------------------------*/
    @Override
    public byte[] getFileExtPos(int[] len) {
        len[0] = m_data.hu68k.ext.length;
        return m_data.hu68k.ext;
    }

    /*------------------------------------------------------------------
     *  Override: Return the total size of the directory item.
     *------------------------------------------------------------------*/
    @Override
    public int getDataSize() {
        // Size of the directory_hu68k_t structure.
        return new DirectoryHu68kT().name.length   // 9
             + new DirectoryHu68kT().name2.length  // 9
             + new DirectoryHu68kT().ext.length    // 4
             + 1   // type (int)
             + 2   // wtime (int)
             + 2   // wdate (int)
             + 2   // start_group (int)
             + 4;  // file_size (int)
    }

    /*------------------------------------------------------------------
     *  Override: Populate the attribute dialog with internal data.
     *------------------------------------------------------------------*/
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("self", m_data.isSelf() ? 1 : 0);
        vals.add("NAME", m_data.hu68k.name,  m_data.hu68k.name.length);
        vals.add("EXT", m_data.hu68k.ext, m_data.hu68k.ext.length);
        vals.add("TYPE", m_data.hu68k.type);
        vals.add("NAME2", m_data.hu68k.name2, m_data.hu68k.name2.length);
        vals.add("WTIME", m_data.hu68k.wtime);
        vals.add("WDATE", m_data.hu68k.wdate);
        vals.add("START_GROUP", m_data.hu68k.start_group);
        vals.add("FILE_SIZE", m_data.hu68k.file_size);
    }
}
