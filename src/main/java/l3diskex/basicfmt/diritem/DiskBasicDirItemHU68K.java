/*
 * Sasaji (original C++ source)
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DirectoryHu68k;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;


/**
 * Directory item for Human68k.
 */
public class DiskBasicDirItemHU68K extends DiskBasicDirItemMSDOS {

    public DiskBasicDirItemHU68K(DiskBasic basic) {
        super(basic);
    }

    public DiskBasicDirItemHU68K(DiskBasic basic,
                                 DiskImageSector n_sector,
                                 int n_secpos,
                                 byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);
    }

    public DiskBasicDirItemHU68K(DiskBasic basic,
                                 int n_num,
                                 DiskBasicGroupItem n_gitem,
                                 DiskImageSector n_sector,
                                 int n_secpos,
                                 byte[] n_data, int dataP,
                                 SectorParam n_next,
                                 boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);
    }

    @Override
    public void setDataPtr(int n_num,
                           DiskBasicGroupItem n_gitem,
                           DiskImageSector n_sector,
                           int n_secpos,
                           byte[] n_data,
                           int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);
    }

    @Override
    public byte[] getFileNamePos(int num, int[] size, int[] len) {
        switch (num) {
            case 0:
                size[0] = len[0] = m_data.data().hu68k.name.length;
                return m_data.data().hu68k.name;
            case 1:
                size[0] = len[0] = m_data.data().hu68k.name2.length;
                return m_data.data().hu68k.name2;
            default:
                size[0] = len[0] = 0;
                return null;
        }
    }

    @Override
    public byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().hu68k.ext.length;
        return m_data.data().hu68k.ext;
    }

    @Override
    public int getDataSize() {
        return DirectoryHu68k.SIZE;
    }

    //
    // ダイアログ用
    //

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", m_data.data().hu68k.name, m_data.data().hu68k.name.length);
        vals.add("EXT", m_data.data().hu68k.ext, m_data.data().hu68k.ext.length);
        vals.add("TYPE", m_data.data().hu68k.type);
        vals.add("NAME2", m_data.data().hu68k.name2, m_data.data().hu68k.name2.length);
        vals.add("WTIME", m_data.data().hu68k.wtime);
        vals.add("WDATE", m_data.data().hu68k.wdate);
        vals.add("START_GROUP", m_data.data().hu68k.startGroup);
        vals.add("FILE_SIZE", m_data.data().hu68k.fileSize);
    }
}
