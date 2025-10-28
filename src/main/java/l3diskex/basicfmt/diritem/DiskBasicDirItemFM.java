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


/// ディレクトリ１アイテム F-BASIC
public class DiskBasicDirItemFM extends DiskBasicDirItemFAT8F {

    /**
     * @param basic the disk basic object
     */
    public DiskBasicDirItemFM(DiskBasic basic) {
        super(basic);
    }

    /**
     * @param basic  the disk basic object
     * @param sector the sector that contains this directory item
     * @param secPos the position within the sector
     * @param data   the raw data of the directory item
     */
    public DiskBasicDirItemFM(DiskBasic basic,
                              DiskImageSector sector,
                              int secPos,
                              byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);
    }

    /**
     * @param basic  the disk basic object
     * @param num    the item number
     * @param gItem  the group item that this directory item refers to
     * @param sector the sector that contains this directory item
     * @param secPos the position within the sector
     * @param data   the raw data of the directory item
     * @param next   parameters for the next sector (may be {@code null})
     * @param unuse  a flag indicating whether the item is unused; passed
     *               by reference in C++ – here represented by a {@code boolean[]}
     */
    public DiskBasicDirItemFM(DiskBasic basic,
                              int num,
                              DiskBasicGroupItem gItem,
                              DiskImageSector sector,
                              int secPos,
                              byte[] data,
                              int dataP,
                              SectorParam next,
                              boolean[] unuse) throws IOException {
        super(basic, num, gItem, sector, secPos, data, dataP, next, unuse);
    }
}
