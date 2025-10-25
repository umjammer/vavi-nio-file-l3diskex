/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.diskimg.DiskImage.DiskImageSector;


public class DiskBasicTypeMAGICAL extends DiskBasicTypeXDOS {

    /* ------------------------------------------------------------------ */
    /* constants                                                         */
    /* ------------------------------------------------------------------ */
    private static final int INVALID_GROUP_NUMBER = -1;
    private static final int MAGICAL_FAT_START = 0xA8;   // 0xa8

    /* ------------------------------------------------------------------ */
    /* constructors                                                      */
    /* ------------------------------------------------------------------ */
    public DiskBasicTypeMAGICAL(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /* ------------------------------------------------------------------ */
    /* access to FAT area                                                */
    /* ------------------------------------------------------------------ */
    @Override
    public int getContinuousArea(int group_size) {
        int new_num = INVALID_GROUP_NUMBER;
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) return new_num;

        int cnt = 0;
        for (int gnum = 0;
             gnum <= basic.getFatEndGroup() && cnt < group_size;
             gnum++) {

            if (!isUsedGroupNumber(gnum)) {
                if (cnt == 0) new_num = gnum;
                cnt++;
            } else {
                cnt = 0;
                new_num = INVALID_GROUP_NUMBER;
            }
        }
        return new_num;
    }

    /* ------------------------------------------------------------------ */
    /* check / assign FAT area                                           */
    /* ------------------------------------------------------------------ */
    @Override
    public double checkFat(boolean is_formatting) {
        byte[] hed = new byte[2];
        hed[0] = 0;
        hed[1] = (byte) (basic.getSectorsPerTrackOnBasic() | 0x40);

        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector == null) return 1.0;          // nothing to check

        if (sector.find(hed, 2) < 0) return -1.0;

        double valid_ratio = 1.0;
        int trks = basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic();
        for (int i = 2; i < trks; i++) {
            if (sector.get(i) != hed[1]) {
                valid_ratio -= 0.5;
                if (valid_ratio < 0.0) break;
            }
        }
        return valid_ratio;
    }

    /* ------------------------------------------------------------------ */
    /* directory                                                        */
    /* ------------------------------------------------------------------ */
    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem item) {
        return true;
    }

    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem item,
                                                 DiskBasicGroups group_items,
                                                 DiskBasicDirItem parent_item) {
        if (group_items.size() == 0) return;
        DiskBasicGroupItem group = group_items.get(0);

        item.setStartGroup(0, group.group, basic.getSubDirGroupSize());

        DiskImageSector sector = basic.getSector(group.track, group.side, group.sectorStart);
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();
        if (buf == null) return;
        sector.fill((byte) 0, basic.diskBasicParam.getDirStartPos(), 0);

        byte[] name = new byte[32];
        int nlen = name.length;
        item.getFileName(name, nlen);
        sector.copy(name, nlen);

        int parent_group = INVALID_GROUP_NUMBER;
        if (parent_item != null) {
            parent_group = parent_item.getStartGroup(0);
        }
        if (parent_group == INVALID_GROUP_NUMBER) {
            parent_group = basic.diskBasicParam.getDirStartSector() - 1;
        }

        int[] trk = new int[1];
        int[] sid = new int[1];
        int[] sec = new int[1];
        basic.calcNumFromSectorPosForGroup(parent_group, trk, sid, sec, null, null);
        XdosSeg s = new XdosSeg();
        s.track = trk[0] * basic.getSidesPerDiskOnBasic() + sid[0];
        s.sector = sec[0];
        s.size = 1 + basic.diskBasicParam.getDirEndSector() - basic.diskBasicParam.getDirStartSector();
        sector.copy(s.toBytes(), 3, 0x71);

        s.track = group.track * basic.getSidesPerDiskOnBasic() + group.side;
        s.sector = group.sectorStart;
        sector.copy(s.toBytes(), 3, 0x74);
    }

    /* ------------------------------------------------------------------ */
    /* format                                                           */
    /* ------------------------------------------------------------------ */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        /* IPL */
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
            String ipl = basic.diskBasicParam.getVariousStringParam("IPLString");
            byte[] iplBytes = ipl.getBytes();
            int len = Math.min(iplBytes.length, 32);
            basic.invertMem(iplBytes, len);
            sector.copy(iplBytes, len);
        }

        /* FAT area */
        sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
            byte val = (byte) (0x40 | basic.getSectorsPerTrackOnBasic());
            int trks = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic();
            sector.fill(val, trks, 0);
            sector.fill((byte) (basic.getParamDensity() >> 4), 1, 0);
            sector.fill((byte) 0, 1, 1);

            int mapi = (1 << basic.getSectorsPerTrackOnBasic()) - 1;
            mapi <<= (16 - basic.getSectorsPerTrackOnBasic());
            byte[] map = new byte[2];
            map[0] = (byte) ((mapi >> 8) & 0xFF);
            map[1] = (byte) (mapi & 0xFF);

            sector.fill((byte) 0, 4, MAGICAL_FAT_START);   // track 0
            for (int i = 2; i < trks; i++) {
                sector.copy(map, 2, i * 2 + MAGICAL_FAT_START);
            }
        }

        /* DIR */
        for (int pos = basic.diskBasicParam.getDirStartSector();
             pos <= basic.diskBasicParam.getDirEndSector(); pos++) {
            sector = basic.getSectorFromSectorPos(pos - 1);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnDir()));
                if (pos == basic.diskBasicParam.getDirStartSector()) {
                    sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()),
                            basic.diskBasicParam.getDirStartPosOnRoot(), 0);
                    int[] trk = new int[1];
                    int[] sid = new int[1];
                    int[] sec = new int[1];
                    basic.calcNumFromSectorPosForGroup(pos - 1, trk, sid, sec, null, null);
                    XdosSeg s = new XdosSeg();
                    s.track = trk[0] * basic.getSidesPerDiskOnBasic() + sid[0];
                    s.sector = sec[0];
                    s.size = 1 + basic.diskBasicParam.getDirEndSector() - basic.diskBasicParam.getDirStartSector();
                    sector.copy(s.toBytes(), 3, 0x74);
                }
            }
        }

        /* Title label */
        setIdentifiedData(data);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* auxiliary types and stubs                                        */
    /* ------------------------------------------------------------------ */

    /** Simple representation of the XDOS segment structure */
    static class XdosSeg {

        int track;
        int sector;
        int size;

        /** Convert the segment to 3 bytes: track (2 bytes) + sector (1 byte) */
        public byte[] toBytes() {
            byte[] buf = new byte[3];
            buf[0] = (byte) ((track >> 8) & 0xFF);   // high byte
            buf[1] = (byte) (track & 0xFF);          // low byte
            buf[2] = (byte) (sector & 0xFF);
            return buf;
        }
    }
}
