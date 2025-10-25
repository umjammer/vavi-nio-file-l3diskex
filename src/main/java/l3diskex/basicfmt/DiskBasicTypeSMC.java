/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import l3diskex.basicfmt.BasicCommon.DirectoryCpm;


// SMC-777 Sony Filerの処理
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
