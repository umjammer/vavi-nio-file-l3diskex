/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMAGICAL.DirectoryMagical;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.XDosSeg;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Serdes;


/**
 Magical DOS processing

 DiskBasicParam
 <li>DirStartPositionOnRoot : Starting position of entry in root directory start sector</li>
 <li>DirStartPosition       : Starting position of entry in subdirectory start sector</li>
 <li>SubDirGroupSize        : Initial number of groups for subdirectory</li>
 */
public class DiskBasicTypeMAGICAL extends DiskBasicTypeXDOS<DirectoryMagical> {

    private static final int MAGICAL_FAT_START = 0xa8;

    public static final int FORMAT_TYPE_MAGICAL = 53;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_MAGICAL;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMagical> dir) {
        super.init(basic, fat, dir);
    }

    @Override
    public int getContinuousArea(int groupSize) {
        int newNum = DiskBasicType.INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return newNum;
        }
        int count = 0;
        for (int g = 0; g <= basic.getFatEndGroup() && count < groupSize; g++) {
            if (!isUsedGroupNumber(g)) {
                if (count == 0) newNum = g;
                count++;
            } else {
                newNum = DiskBasicType.INVALID_GROUP_NUMBER;
                count = 0;
            }
        }
        return newNum;
    }

    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = 1.0;

        byte[] hed = new byte[2];
        hed[0] = 0;
        hed[1] = (byte) basic.getSectorsPerTrackOnBasic();
        hed[1] |= 0x40;
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector != null) {
            if (sector.find(hed, 2) < 0) {
                return -1.0;
            }

            // Check sector count
            for (int i = 2; i < basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic(); i++) {
                if (sector.get(i) != hed[1]) {
                    validRatio -= 0.5;
                    if (validRatio < 0.0) break;
                }
            }
        }
        return validRatio;
    }

    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem<DirectoryMagical> item) {
        return true;
    }

    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryMagical> item,
                                                 DiskBasicGroups groupItems,
                                                 DiskBasicDirItem<DirectoryMagical> parentItem) throws IOException {
        if (groupItems.size() == 0) return;

        DiskBasicGroupItem group = groupItems.get(0);
        item.setStartGroup(0, group.group, basic.getSubDirGroupSize());

        DiskImageSector sector = basic.getSector(group.track, group.side, group.sectorStart);
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();
        if (buf == null) return;

        // Clear the beginning of sector
        sector.fill((byte) 0, basic.getDirStartPos(), 0);

        // Set sector name
        byte[] name = new byte[32];
        int nameLen = name.length;
        item.getFileName(name, nameLen);
        sector.copy(name, nameLen);

        int parentGroup = DiskBasicType.INVALID_GROUP_NUMBER;
        if (parentItem != null) {
            // Parent is subdirectory
            parentGroup = parentItem.getStartGroup(0);
        }
        if (parentGroup == DiskBasicType.INVALID_GROUP_NUMBER) {
            // Root
            parentGroup = basic.getDirStartSector() - 1;
        }

        // Size
        int[] track = new int[1], side = new int[1], sectorNum = new int[1];
        basic.calcNumFromSectorPosForGroup(parentGroup, track, side, sectorNum, null, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XDosSeg s = new XDosSeg();
        s.track = (byte) (track[0] * basic.getSidesPerDiskOnBasic() + side[0]);
        s.sector = (byte) sectorNum[0];
        s.size = (byte) (1 + basic.getDirEndSector() - basic.getDirStartSector());
        Serdes.Util.serialize(s, baos);
        baos.reset();
        sector.copy(baos.toByteArray(), 3, 0x71);
        s.track = (byte) (group.track * basic.getSidesPerDiskOnBasic() + group.side);
        s.sector = (byte) group.sectorStart;
        Serdes.Util.serialize(s, baos);
        sector.copy(baos.toByteArray(), 3, 0x74);
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // IPL
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));
            byte[] ipl = basic.getVariousStringParam("IPLString").getBytes();
            int len = Math.min(ipl.length, 32);
            basic.invertMemory(ipl, len);
            sector.copy(ipl, len);
        }

        // FAT area
        sector = basic.getSectorFromSectorPos(basic.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));
            // Number of sectors
            byte val = (byte) (0x40 | basic.getSectorsPerTrackOnBasic());
            int tracks = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic();
            sector.fill(val, tracks, 0);
            // Track 0 is reserved
            sector.fill((byte) (basic.getParamDensity() >> 4), 1, 0);
            sector.fill((byte) 0, 1, 1);
            // Usage status
            int mapi = (1 << basic.getSectorsPerTrackOnBasic()) - 1;
            mapi <<= (16 - basic.getSectorsPerTrackOnBasic());
            byte[] map = new byte[2];
            map[0] = (byte) ((mapi >> 8) & 0xFF);
            map[1] = (byte) (mapi & 0xFF);

            sector.fill((byte) 0, 4, MAGICAL_FAT_START);   // track 0
            for (int i = 2; i < tracks; i++) {
                sector.copy(map, 2, i * 2 + MAGICAL_FAT_START);
            }
        }

        // DIR
        for (int pos = basic.getDirStartSector(); pos <= basic.getDirEndSector(); pos++) {
            sector = basic.getSectorFromSectorPos(pos - 1);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.getFillCodeOnDir()));
                if (pos == basic.getDirStartSector()) {
                    sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()), basic.getDirStartPosOnRoot(), 0);
                    // Size
                    int[] track = new int[1], side = new int[1], sectorNum = new int[1];
                    basic.calcNumFromSectorPosForGroup(pos - 1, track, side, sectorNum, null, null);
                    XDosSeg s = new XDosSeg();
                    s.track = (byte) (track[0] * basic.getSidesPerDiskOnBasic() + side[0]);
                    s.sector = (byte) sectorNum[0];
                    s.size = (byte) (1 + basic.getDirEndSector() - basic.getDirStartSector());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Serdes.Util.serialize(s, baos);
                    sector.copy(baos.toByteArray(), 3, 0x74);
                }
            }
        }

        // Title label
        setIdentifiedData(data);

        return true;
    }
}
