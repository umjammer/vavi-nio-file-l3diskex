/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.DirectoryCpm;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_SMC;


/**
 * disk basic type for SMC-777 Sony Filer
 */
public class DiskBasicTypeSMC extends DiskBasicTypeCPM {

    @Override
    public boolean isSupported(DiskBasicFormatType typeNumber) {
        return typeNumber == FORMAT_TYPE_SMC;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryCpm> dir) {
        super.init(basic, fat, dir);
    }
}
