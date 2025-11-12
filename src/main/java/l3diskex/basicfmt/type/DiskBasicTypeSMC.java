/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.DirectoryCpm;


/**
 * disk basic type for SMC-777 Sony Filer
 */
public class DiskBasicTypeSMC extends DiskBasicTypeCPM {

    /** */
    public DiskBasicTypeSMC(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryCpm> dir) {
        super(basic, fat, dir);
    }
}
