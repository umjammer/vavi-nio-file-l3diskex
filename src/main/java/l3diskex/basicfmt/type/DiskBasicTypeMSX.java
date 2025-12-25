/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DirectoryMsDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;


/**
 * MSX BASIC / MSX-DOS processing
 * <p>
 * DiskBasicParam
 *
 * <li>MediaID : Media ID</li>
 */
public class DiskBasicTypeMSX extends DiskBasicTypeMSDOS {

    private static final String[] EXCLUDE_KEYWORDS = {
            "IO      SYS",
            "IBMDOS",
            "MSDOS",
            "IBM",
    };

    public static final int FORMAT_TYPE_MSX = 4;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_MSX;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMsDos> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0: Normal, 0.0 - 1.0: Warning present, <0.0: Error present
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0.0;

        double validRatio = parseMSDOSParamOnDisk(basic.getDisk(), isFormatting);
        if (validRatio >= 0.0) {
            DiskImageSector sector = basic.getSector(0, 0, 1);
            if (sector == null) return -1.0;
            byte[] data = sector.getSectorBuffer();
            if (data == null) return -1.0;
            // If there is a string "MSXDOS", it is certain
            if (sector.find("MSXDOS".getBytes(), 6) >= 0) {
                validRatio += 0.8;
            } else if (sector.find("MSX".getBytes(), 3) >= 0) {
                validRatio += 0.4;
            }
            // Keywords to exclude
            for (String excludeKeyword : EXCLUDE_KEYWORDS) {
                if (sector.find(excludeKeyword.getBytes(), excludeKeyword.length()) >= 0) {
                    validRatio -= 0.5;
                    break;
                }
            }
        }
        if (validRatio > 1.0) validRatio = 1.0;
        else if (validRatio < -1.0) validRatio = -1.0;

        return validRatio;
    }

    /**
     * Whether a subdirectory can be created
     */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /**
     * Individual processing after filling sector data
     * Format Write IPL
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        byte[][] buf = new byte[1][];
        if (!createBiosParameterBlock("\u00eb\u00fe\u0090", "MSX", buf)) {
            return false;
        }

        // Execution code at boot time
        if (buf[0] != null) {
            buf[0][0x1e] = (byte) 0xd0;   // RET NC
        }

        return true;
    }
}
