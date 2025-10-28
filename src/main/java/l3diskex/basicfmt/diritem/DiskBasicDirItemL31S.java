/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFAT8.DiskBasicDirItemFAT8F;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;


/// ディレクトリ１アイテム L3 BASIC 単密度 1S
public class DiskBasicDirItemL31S extends DiskBasicDirItemFAT8F {

    /**
     * Construct with only the DiskBasic pointer.
     *
     * @param basic The disk basic object.
     */
    public DiskBasicDirItemL31S(DiskBasic basic) {
        super(basic);
    }

    /**
     * Construct with sector information and data buffer.
     *
     * @param basic    The disk basic object.
     * @param n_sector The sector containing the directory item.
     * @param n_secpos Position of the sector within the image.
     * @param n_data   Raw byte data of the directory entry.
     */
    public DiskBasicDirItemL31S(DiskBasic basic,
                                DiskImageSector n_sector,
                                int n_secpos,
                                byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);
    }

    /**
     * Construct with full group information.
     *
     * @param basic    The disk basic object.
     * @param n_num    The item number.
     * @param n_gitem  The group item describing the file chain.
     * @param n_sector The sector containing the directory item.
     * @param n_secpos Position of the sector within the image.
     * @param n_data   Raw byte data of the directory entry.
     * @param n_next   Parameter for the next sector (may be {@code null}).
     * @param n_unuse  Flag indicating whether the item is unused.
     */
    public DiskBasicDirItemL31S(DiskBasic basic,
                                int n_num,
                                DiskBasicGroupItem n_gitem,
                                DiskImageSector n_sector,
                                int n_secpos,
                                byte[] n_data,
                                int dataP,
                                SectorParam n_next,
                                boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);
    }
}
