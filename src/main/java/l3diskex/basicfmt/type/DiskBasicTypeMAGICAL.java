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
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.XdosSeg;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Serdes;


/**
 Magical DOSの処理

 DiskBasicParam
 @li DirStartPositionOnRoot : ルートディレクトリ開始セクタのエントリの開始位置
 @li DirStartPosition       : サブディレクトリ開始セクタのエントリの開始位置
 @li SubDirGroupSize        : サブディレクトリの初期グループ数
 */
public class DiskBasicTypeMAGICAL extends DiskBasicTypeXDOS<DirectoryMagical> {

    private static final int MAGICAL_FAT_START = 0xA8;   // 0xa8

    public DiskBasicTypeMAGICAL(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMagical> dir) {
        super(basic, fat, dir);
    }

    @Override
    public int getContinuousArea(int group_size) {
        int new_num = DiskBasicType.INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return new_num;
        }
        int cnt = 0;
        for (int gnum = 0; gnum <= basic.getFatEndGroup() && cnt < group_size; gnum++) {
            if (!isUsedGroupNumber(gnum)) {
                if (cnt == 0) new_num = gnum;
                cnt++;
            } else {
                new_num = DiskBasicType.INVALID_GROUP_NUMBER;
                cnt = 0;
            }
        }
        return new_num;
    }

    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        byte[] hed = new byte[2];
        hed[0] = 0;
        hed[1] = (byte) basic.getSectorsPerTrackOnBasic();
        hed[1] |= 0x40;
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            if (sector.find(hed, 2) < 0) {
                return -1.0;
            }

            // セクタ数チェック
            for (int i = 2; i < basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic(); i++) {
                if (sector.get(i) != hed[1]) {
                    valid_ratio -= 0.5;
                    if (valid_ratio < 0.0) break;
                }
            }
        }
        return valid_ratio;
    }

    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem<DirectoryMagical> item) {
        return true;
    }

    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryMagical> item,
                                                 DiskBasicGroups group_items,
                                                 DiskBasicDirItem<DirectoryMagical> parent_item) throws IOException {
        if (group_items.size() == 0) return;

        DiskBasicGroupItem group = group_items.get(0);
        item.setStartGroup(0, group.group, basic.getSubDirGroupSize());

        DiskImageSector sector = basic.getSector(group.track, group.side, group.sectorStart);
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();
        if (buf == null) return;

        // セクタの先頭をクリア
        sector.fill((byte) 0, basic.diskBasicParam.getDirStartPos(), 0);

        // セクタ名を設定
        byte[] name = new byte[32];
        int nlen = name.length;
        item.getFileName(name, nlen);
        sector.copy(name, nlen);

        int parent_group = DiskBasicType.INVALID_GROUP_NUMBER;
        if (parent_item != null) {
            // 親がサブディレクトリ
            parent_group = parent_item.getStartGroup(0);
        }
        if (parent_group == DiskBasicType.INVALID_GROUP_NUMBER) {
            // ルート
            parent_group = basic.diskBasicParam.getDirStartSector() - 1;
        }

        // サイズ
        int[] trk = new int[1], sid = new int[1], sec = new int[1];
        basic.calcNumFromSectorPosForGroup(parent_group, trk, sid, sec, null, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XdosSeg s = new XdosSeg();
        s.track = (byte) (trk[0] * basic.getSidesPerDiskOnBasic() + sid[0]);
        s.sector = (byte) sec[0];
        s.size = (byte) (1 + basic.diskBasicParam.getDirEndSector() - basic.diskBasicParam.getDirStartSector());
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
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
            byte[] ipl = basic.diskBasicParam.getVariousStringParam("IPLString").getBytes();
            int len = Math.min(ipl.length, 32);
            basic.invertMem(ipl, len);
            sector.copy(ipl, len);
        }

        // FAT area
        sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
            // セクタ数
            byte val = (byte) (0x40 | basic.getSectorsPerTrackOnBasic());
            int trks = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic();
            sector.fill(val, trks, 0);
            // トラック0は予約
            sector.fill((byte) (basic.getParamDensity() >> 4), 1, 0);
            sector.fill((byte) 0, 1, 1);
            // 使用状況
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

        // DIR
        for (int pos = basic.diskBasicParam.getDirStartSector();
             pos <= basic.diskBasicParam.getDirEndSector(); pos++) {
            sector = basic.getSectorFromSectorPos(pos - 1);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnDir()));
                if (pos == basic.diskBasicParam.getDirStartSector()) {
                    sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()), basic.diskBasicParam.getDirStartPosOnRoot(), 0);
                    // サイズ
                    int[] trk = new int[1], sid = new int[1], sec = new int[1];
                    basic.calcNumFromSectorPosForGroup(pos - 1, trk, sid, sec, null, null);
                    XdosSeg s = new XdosSeg();
                    s.track = (byte) (trk[0] * basic.getSidesPerDiskOnBasic() + sid[0]);
                    s.sector = (byte) sec[0];
                    s.size = (byte) (1 + basic.diskBasicParam.getDirEndSector() - basic.diskBasicParam.getDirStartSector());
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
