/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.time.LocalDateTime;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DirectoryMsDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;


/**
 Human68k processing

 DiskBasicParam Specific parameters
 <li>IPLString: IPL string</li>
 <li>IPLCompareString: Used for OS judgment</li>
 <li>IgnoreParameter: Whether to ignore the parameters in sector 1</li>
 <li>MediaID: Media ID</li>
 */
public class DiskBasicTypeHU68K extends DiskBasicTypeMSDOS {

    public static final int FORMAT_TYPE_HU68K = 14;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_HU68K;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMsDos> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Get each parameter from disk and calculate necessary parameters
     *
     * @param isFormatting Whether formatting is in progress
     * @return 1.0 Normal, <1.0 Warning present, <0.0 Error present
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0.0;

        double validRatio = 1.0;
        if (!basic.getVariousBoolParam("IgnoreParameter")) {
            validRatio = parseMSDOSParamOnDisk(basic.getDisk(), isFormatting);
        }
        if (validRatio >= 0.0) {
            // Sector 0
            DiskImageSector sector = basic.getSector(0, basic.getSideNumberBaseOnDisk(), 1);
            if (sector == null) {
                return -1.0;
            }

            // Whether "X68IPL" is included in IPL
            // Whether "Human" is included
            int found = -1;
            String istr = null;
            for (int i = 0; i < 3; i++) {
                found = -1;
                istr = switch (i) {
                    case 0 -> basic.getVariousStringParam("IPLString");
                    case 1 -> basic.getVariousStringParam("IPLCompareString");
                    case 2 -> "Human";
                    default -> istr;
                };
                if (istr != null && !istr.isEmpty()) {
                    found = sector.find(istr.getBytes(), istr.length());
                }
                if (found >= 0) {
                    validRatio = 1.0;
                    break;
                }
            }
            if (found < 0) {
                validRatio = 0.1;
            }
        }

        return validRatio;
    }

    /**
     * Individual processing after filling sector data
     *
     * Format Writing IPL
     * @param data Data obtained during format judgment
     * @return true/false
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        if (!createBiosParameterBlock("\u0060\u003c\u0090", "X68IPL30", null)) {
            return false;
        }

        // Set volume label
        int dirStart = basic.getReservedSectors() + basic.getNumberOfFats() * basic.getSectorsPerFat();
        DiskImageSector sector = basic.getSectorFromSectorPos(dirStart);
        DiskBasicDirItem<DirectoryMsDos> dItem = dir.newItem(sector, 0, sector.getSectorBuffer(), 0);

        dItem.setFileNameStr(data.getVolumeName());
        dItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue(), 0);
        LocalDateTime tm = LocalDateTime.now();
        dItem.setFileCreateDateTime(tm);

        return true;
    }
}
