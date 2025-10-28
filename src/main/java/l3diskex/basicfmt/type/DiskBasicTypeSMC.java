/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import l3diskex.basicfmt.BasicCommon.DirectoryCpm;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;


///
/// disk basic type for SMC-777 Sony Filer
///
public class DiskBasicTypeSMC extends DiskBasicTypeCPM {

    /**
     * Constructor that forwards the arguments to the superclass constructor.
     *
     * @param basic the DiskBasic instance
     * @param fat   the DiskBasicFat instance
     * @param dir   the DiskBasicDir instance
     */
    public DiskBasicTypeSMC(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryCpm> dir) {
        super(basic, fat, dir);
    }
}
