/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DirectoryXdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 X-DOS for X1の処理

 DiskBasicParam
 @li DirStartPositionOnRoot ルートディレクトリ開始セクタのエントリの開始位置
 @li DirStartPosition       サブディレクトリ開始セクタのエントリの開始位置
 @li SubDirGroupSize        サブディレクトリの初期グループ数
 */
public class DiskBasicTypeXDOS<T extends DirectoryXdos> extends DiskBasicType<T> {

    /** FAT information structure used by X-DOS */
    public static class StFATXDOS {

        public byte[] use = new byte[0x0a8];
        public byte[] map = new byte[0x158];
    }

    private static final int XDOS_FAT_START = 0xa8;
    private static final int VOLUME_NAME_LENGTH = 80;

    /** Public constructor used by the factory */
    public DiskBasicTypeXDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super(basic, fat, dir);
    }

    /** Set a FAT entry at position 'num' to value 'val' */
    @Override
    public void setGroupNumber(int num, int val) {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        int pos = num / basic.getSectorsPerTrackOnBasic();   // round
        pos *= 2;
        int mask = (0x8000 >> (num % basic.getSectorsPerTrackOnBasic()));
        if (mask >= 0x100) {
            mask >>= 8;
        } else {
            pos++;
        }

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return;
        }
        // FATには使用状況テーブルがある
        pos += XDOS_FAT_START;
        fatbuf.bit(pos, (byte) mask, val == 0, basic.isDataInverted());
    }

    /** Is the group number 'num' used? */
    @Override
    public boolean isUsedGroupNumber(int num) {
        boolean exist = false;

        if (num > basic.getFatEndGroup()) {
            return false;
        }

        int pos = num / basic.getSectorsPerTrackOnBasic();   // round
        pos *= 2;
        int mask = (0x8000 >> (num % basic.getSectorsPerTrackOnBasic()));
        if (mask >= 0x100) {
            mask >>= 8;
        } else {
            pos++;
        }

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return true;
        }
        // FATには使用状況テーブルがある
        pos += XDOS_FAT_START;
        exist = !fatbuf.bitTest(pos, (byte) mask, basic.isDataInverted());
        return exist;
    }

    /** Return the first free group number, or INVALID_GROUP_NUMBER */
    @Override
    public int getEmptyGroupNumber() {
        int new_num = INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return new_num;
        }
        // 空き位置をさがす
        for (int gnum = 0; gnum <= basic.getFatEndGroup(); gnum++) {
            if (!isUsedGroupNumber(gnum)) {
                new_num = gnum;
                break;
            }
        }
        return new_num;
    }

    /** Find a contiguous area of 'group_size' free groups */
    public int getContinuousArea(int group_size) {
        int new_num = INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return new_num;
        }

        int step = basic.getSectorsPerTrackOnBasic();
        int count = 0;
        for (int gnum = 0; gnum <= basic.getFatEndGroup() && count < group_size;) {
            if (!isUsedGroupNumber(gnum)) {
                if (count == 0) {
                    new_num = gnum;
                }
                count++;
                gnum++;
            } else {
                new_num = INVALID_GROUP_NUMBER;
                count = 0;
                // 各トラックの先頭までスキップ
                gnum = ((gnum + step) / step) * step;
            }
        }
        return new_num;
    }

    /** Return the next free group number after 'curr_group' */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        return getEmptyGroupNumber();
    }

    /** Check the FAT area; return a status value */
    @Override
    public double checkFat(boolean is_formatting) {
        double validRatio = 1.0;

        byte[] hed = new byte[2];
        hed[0] = 1;
        hed[1] = 0x0a; // (int) basic.getSectorsPerTrackOnBasic();
        hed[1] |= 0x40;
        DiskImageSector sector = basic.getManagedSector(basic.diskBasicParam.getDirStartSector() - 1);
        if (sector != null) {
            if (sector.find(hed, 2) < 0) {
                return -1.0;
            }

            // セクタ数チェック
            for (int i = 2; i < basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic(); i++) {
                if (sector.get(i) != hed[1]) {
                    validRatio -= 0.5;
                    if (validRatio < 0.0) break;
                }
            }
        }
        return validRatio;
    }

    /** Parse parameters on disk and compute any required values */
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        // グループ数
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            basic.diskBasicParam.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    /** Get usable disk size */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.getFatEndGroup() + 1 - basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        disk_size[0] = group_size[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        fatAvailability.empty();
//        fatAvailability.setCount(basic.getFatEndGroup() + 1, FAT_AVAIL_USED.ordinal());

        // Allocation Mapを調べる
        int grps = 0;
        int gnum = 0;
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) return;
        for (int pos = XDOS_FAT_START; pos < fatbuf.getSize(); pos += 2) {
            short dat = (short)((fatbuf.get(pos) << 8) | fatbuf.get(pos+1));
            for(int bit = 0; bit < basic.getSectorsPerTrackOnBasic() && gnum <= basic.getFatEndGroup(); bit++) {
                boolean used = ((dat & (0x8000 >> bit)) == 0);
                if (!used) {
                    fatAvailability.set(gnum, FAT_AVAIL_FREE);
                    grps++;
                }
                gnum++;
            }
        }

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<T>> items = dir.getCurrentItems(null);
        if (items == null) return;
        for (int idx = 0; idx < items.size(); idx++) {
            DiskBasicDirItem<T> item = items.get(idx);
            if (item == null || !item.isUsed()) continue;
            int gcnt = item.getGroupCount();
            if (gcnt > 0) {
			DiskBasicGroupItem gitem = item.getGroup(gcnt - 1);
                gnum = gitem.group;
                if (gnum <= basic.getFatEndGroup()) {
                    fatAvailability.set(gnum, FAT_AVAIL_USED_LAST);
                }
            }
        }

        // 空きをチェック
        int gend = basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        for(int pos = 0; pos < gend; pos++) {
            // ディレクトリエリアは使用済み
            fatAvailability.set(pos, FAT_AVAIL_SYSTEM);
        }

        int fsize = grps * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(fsize);
        fatAvailability.setFreeGroups(grps);
    }

    /** Prepare for saving a file */
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size,
                                     DiskBasicDirItem<T> pitem, DiskBasicDirItem<T> nitem,
                                     DiskBasicError errinfo) throws IOException {
        // Chain用のセクタを確保する
        int gnum = getEmptyGroupNumber();
        if (gnum == INVALID_GROUP_NUMBER) {
            return false;
        }
        // セクタ
        DiskImageSector sector = basic.getSectorFromGroup(0);
        if (sector == null) {
            return false;
        }
        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return false;
        }
        sector.fill(basic.invertUint8((byte) 0));

        // チェイン情報にセクタをセット
        nitem.setChainSector(sector, buf, null);

        // 開始グループを設定
        nitem.setStartGroup(0, gnum, 1);

        // セクタを予約
        setGroupNumber(gnum, 1);

        return true;
    }

    /** Allocate groups for a data block of size 'data_size' */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<T> item,
                                  int data_size, AllocateGroupFlags flags,
                                  DiskBasicGroups[] group_items) {
        //int file_size = 0;
        //int groups = 0;

        int rc = 0;
        int sec_size = basic.getSectorSize();
        int remain = data_size;
        int limit = basic.getFatEndGroup() + 1;
        int chain_idx = -1;
        int prev_trk = -1;
        int group_num = INVALID_GROUP_NUMBER;

        if (item.getFileAttr().isDirectory()) {
            // ディレクトリ作成の場合、連続した空き領域をさがす
            group_num = getContinuousArea(basic.getSubDirGroupSize());
        } else {
            group_num = getEmptyGroupNumber();
        }
        if (group_num == INVALID_GROUP_NUMBER) {
            // 空きなし
            rc = -1;
            return rc;
        }
        while(remain > 0 && limit >= 0) {
            // 使用しているか
            boolean used = isUsedGroupNumber(group_num);
            if (!used) {
                // 使用済みにする
                basic.getNumsFromGroup(group_num, 0, sec_size, remain, group_items[0]);
                setGroupNumber(group_num, 1);
                // チェインセクタも更新
                int trk = group_num / basic.getSectorsPerTrackOnBasic();
                if (trk != prev_trk) {
                    chain_idx++;
                }
                prev_trk = trk;
                item.addChainGroupNumber(chain_idx, group_num);

//                file_size += (sec_size * basic.getSectorsPerGroup());
//                groups++;

                remain -= (sec_size * basic.getSectorsPerGroup());
            }
            // グループ番号はなるべく連続するように設定
            group_num++;
            limit--;
        }
        if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }
        return rc;
    }

    /** Is the given group number part of the root directory? */
    @Override
    public boolean isRootDirectory(int group_num) {
        return group_num < basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
    }

    /** Rename directory before creation */
    @Override
    public boolean renameOnMakingDirectory(String[] dir_name) {
        if (dir_name.equals("!")) {
            return false;
        }
        return true;
    }

    /** Prepare to create a directory (no-op for X-DOS) */
    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem<T> item) {
        return true;
    }

    /** Additional processing after creating a directory */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<T> item,
                                                 DiskBasicGroups group_items,
                                                 DiskBasicDirItem<T> parent_item) throws IOException {
        if (group_items.size() == 0) return;

        DiskBasicGroupItem group = group_items.get(0);
        item.setStartGroup(0, group.group, basic.getSubDirGroupSize());

        DiskImageSector sector = basic.getSector(group.track, group.side, group.sectorStart);
        if (sector == null) return;

        byte[] buf = sector.getSectorBuffer();
        if (buf == null) return;
        int bufOffset = 0;

        // セクタの先頭をクリア
        sector.fill((byte) 0, basic.diskBasicParam.getDirStartPos(), 0);
        // セクタ名を設定
        byte[] name = new byte[32];
        int nlen = name.length;
        Arrays.fill(name, 0, nlen, (byte) 0);
        item.getFileName(name, nlen);
        sector.copy(name, nlen);
        // サイズはクリア
        item.setFileSize(0);

        // 親をつくる
        bufOffset += basic.diskBasicParam.getDirStartPos();
        DiskBasicDirItem<DirectoryXdos> newitem = basic.createDirItem(sector, basic.diskBasicParam.getDirStartPos(), buf, bufOffset);
        newitem.clearData();
        newitem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);

        int parent_group = INVALID_GROUP_NUMBER;
        if (parent_item != null) {
            // 親がサブディレクトリ
            parent_group = parent_item.getStartGroup(0);
        }
        if (parent_group == INVALID_GROUP_NUMBER) {
            // ルート
            parent_group = basic.diskBasicParam.getDirStartSector() - 1;
        }
        newitem.setStartGroup(0, parent_group);
        newitem.setFileNamePlain("!");
    }

    /** Additional processing after formatting the disk */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // IPL
        DiskImageSector sector = null;
        sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnDir()));
            byte[] ipl = basic.diskBasicParam.getVariousStringParam("IPLString").getBytes();
            int len = ipl.length;
            if (len > 0) {
                if (len > 32) len = 32;
                basic.invertMem(ipl, len);
                sector.copy(ipl, len);
            }
        }

        // FAT
        sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getFatStartSector() - 1);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));
            // セクタ数
            byte val = 0x4a; // | (basic.getSectorsPerTrackOnBasic());
            int trks = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic();
            sector.fill(val, trks, 0);
            // トラック0は予約
            sector.fill((byte) (basic.getParamDensity() >> 4), 1, 0);
            sector.fill((byte) 1, 1, 1);
            // 使用状況
            int mapi = (1 << basic.getSectorsPerTrackOnBasic()) - 1;
            mapi <<= (16 - basic.getSectorsPerTrackOnBasic());
            byte[] map = new byte[2];
            map[0] = (byte) ((mapi >> 8) & 0xff);
            map[1] = (byte) (mapi & 0xff);

            sector.fill((byte) 0, 4, XDOS_FAT_START);	// トラック0
            for(int i=2; i<trks; i++) {
                sector.copy(map, 2, i * 2 + XDOS_FAT_START);
            }
        }

        // DIR
        for(int pos = basic.diskBasicParam.getDirStartSector(); pos <= basic.diskBasicParam.getDirEndSector(); pos++) {
            sector = basic.getSectorFromSectorPos(pos - 1);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnDir()));
                if (pos == basic.diskBasicParam.getDirStartSector()) {
                    sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()), basic.diskBasicParam.getDirStartPosOnRoot(), 0);
                }
            }
        }

        // タイトルラベル
        setIdentifiedData(data);

        return true;
    }

    /** Calculate data size in the last sector of a file */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<T> item,
                                        InputStream istream,
                                        OutputStream ostream,
                                        byte[] sector_buffer,
                                        int sectorOffset, int sector_size,
                                        int remain_size) {
        if (item.needCheckEofCode()) {
            // 終端コード($00)の1つ前までを出力
            byte eof_code = basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
            for (int len = 0; len < remain_size; len++) {
                if (sector_buffer[len] == eof_code) {
                    remain_size = len;
                    break;
                }
            }
        }
        return remain_size;
    }

    /** Delete the FAT entry for group 'group_num' */
    @Override
    public void deleteGroupNumber(int group_num) {
        // 未使用にする
        setGroupNumber(group_num, 0);
    }

    /** Additional processing after a file is deleted */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<T> item) {
        // チェインセクタを未使用にする
        setGroupNumber(item.getStartGroup(0), 0);
        return true;
    }

    /** Retrieve IPL and volume name information */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // タイトル名 DIRエリアの最初
        DiskImageSector sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getDirStartSector() - 1);
        if (sector != null) {
            String dst = new String(sector.getSectorBuffer(), 0,
                    VOLUME_NAME_LENGTH, basic.getCharCodes().charset());
            data.setVolumeName(dst);
            data.setVolumeNameMaxLength(VOLUME_NAME_LENGTH);
        }
    }

    /** Set IPL and volume name information */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        // タイトル名 DIRエリアの最初
        if (basic.getFormatType().hasVolumeName()) {
            DiskImageSector sector = basic.getSectorFromSectorPos(basic.diskBasicParam.getDirStartSector() - 1);
            if (sector != null) {
                byte[] dst = new byte[VOLUME_NAME_LENGTH + 1];
                byte[] src = data.getVolumeName().getBytes(basic.getCharCodes().charset());
                System.arraycopy(src, 0, dst, 0, Math.min(src.length, VOLUME_NAME_LENGTH));
                if (src.length > 0) {
                    sector.copy(dst, VOLUME_NAME_LENGTH);
                }
            }
        }
    }
}
