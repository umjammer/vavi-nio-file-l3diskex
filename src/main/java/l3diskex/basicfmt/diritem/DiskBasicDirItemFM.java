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

import static l3diskex.basicfmt.type.DiskBasicTypeFM.FORMAT_TYPE_FM;


/** ディレクトリ１アイテム F-BASIC */
public class DiskBasicDirItemFM extends DiskBasicDirItemFAT8F {

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_FM;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);
    }

    @Override
    public void init(DiskBasic basic,
                     DiskImageSector sector,
                     int sectorPos,
                     byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic,
                     int num,
                     DiskBasicGroupItem gropItem,
                     DiskImageSector sector,
                     int sectorPos,
                     byte[] data,
                     int dataPos,
                     SectorParam next,
                     boolean[] unuse) throws IOException {
        super.init(basic, num, gropItem, sector, sectorPos, data, dataPos, next, unuse);
    }
}
