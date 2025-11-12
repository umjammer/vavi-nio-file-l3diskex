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


/** ディレクトリ１アイテム L3 BASIC 単密度 1S */
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
     * @param basic  The disk basic object.
     * @param sector The sector containing the directory item.
     * @param secPos Position of the sector within the image.
     * @param data   Raw byte data of the directory entry.
     */
    public DiskBasicDirItemL31S(DiskBasic basic,
                                DiskImageSector sector,
                                int secPos,
                                byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);
    }

    /**
     * Construct with full group information.
     *
     * @param basic     The disk basic object.
     * @param num       The item number.
     * @param groupItem The group item describing the file chain.
     * @param sector    The sector containing the directory item.
     * @param secPos    Position of the sector within the image.
     * @param data      Raw byte data of the directory entry.
     * @param next      Parameter for the next sector (may be {@code null}).
     * @param unuse     Flag indicating whether the item is unused.
     */
    public DiskBasicDirItemL31S(DiskBasic basic,
                                int num,
                                DiskBasicGroupItem groupItem,
                                DiskImageSector sector,
                                int secPos,
                                byte[] data,
                                int dataP,
                                SectorParam next,
                                boolean[] unuse) throws IOException {
        super(basic, num, groupItem, sector, secPos, data, dataP, next, unuse);
    }
}
