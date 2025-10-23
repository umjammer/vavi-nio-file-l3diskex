/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import l3diskex.basicfmt.BasicCommon.DirectoryXdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;


//
// basictype_xdos.h
//
public class DiskBasicTypeXDOS extends DiskBasicType<DirectoryXdos> {

    /** FAT information structure used by X-DOS */
    public static class StFATXDOS {

        public byte[] use = new byte[0x0a8];
        public byte[] map = new byte[0x158];
    }

    private static final int XDOS_FAT_START = 0xa8;
    private static final int VOLUME_NAME_LENGTH = 80;

    /** Public constructor used by the factory */
    public DiskBasicTypeXDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryXdos> dir) {
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

        pos += XDOS_FAT_START;
        fatbuf.bit(pos, (byte) mask, val == 0, basic.isDataInverted());
    }

    /** Is the group number 'num' used? */
    @Override
    public boolean isUsedGroupNumber(int num) {
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

        pos += XDOS_FAT_START;
        return !fatbuf.BitTest(pos, (byte) mask, basic.isDataInverted());
    }

    /** Return the first free group number, or INVALID_GROUP_NUMBER */
    @Override
    public int getEmptyGroupNumber() {
        int new_num = INVALID_GROUP_NUMBER;

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return new_num;
        }

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
        int count = 0;

        for (int gnum = 0; gnum <= basic.getFatEndGroup(); gnum++) {
            if (!isUsedGroupNumber(gnum)) {
                count++;
                if (count == group_size) {
                    new_num = gnum - group_size + 1;
                    break;
                }
            } else {
                count = 0;
            }
        }
        return new_num;
    }

    /** Return the next free group number after 'curr_group' */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        // For X-DOS we simply return the first free group number
        return getEmptyGroupNumber();
    }

    /** Check the FAT area; return a status value */
    @Override
    public double checkFat(boolean is_formatting) {
        double validRatio = 1.0;

        byte[] hed = new byte[2];
        hed[0] = 1;
        hed[1] = 0x0a; // (wxUint8)basic->GetSectorsPerTrackOnBasic();
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
        // Java has no reference parameters, so we use one‑element arrays.
        group_size[0] = basic.getFatEndGroup() + 1;
        disk_size[0] = (basic.getParamDensity() / 2) * (basic.diskBasicParam.getDirEndSector() - basic.diskBasicParam.getDirStartSector() + 1);
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        // Implementation omitted – in the original code this updates
        // internal state related to free space.  The stub simply calls
        // the base implementation if it exists.
        super.calcDiskFreeSize(wrote);
    }

    /** Prepare for saving a file */
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size,
                                     DiskBasicDirItem<DirectoryXdos> pitem, DiskBasicDirItem<DirectoryXdos> nitem,
                                     DiskBasicError errinfo) {
        // The original code uses a disk image sector buffer.
        DiskImageSector sector = basic.getSectorFromGroup(0);
        if (sector == null) {
            return false;
        }
        return true;
    }

    /** Allocate groups for a data block of size 'data_size' */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryXdos> item,
                                  int data_size, AllocateGroupFlags flags,
                                  DiskBasicGroups group_items) {
        int fileSize = 0;
        // Simplified: we just return 0 to indicate success.
        return 0;
    }

    /** Is the given group number part of the root directory? */
    @Override
    public boolean isRootDirectory(int group_num) {
        return group_num == basic.diskBasicParam.getDirStartSector() - 1;
    }

    /** Rename directory before creation */
    @Override
    public boolean renameOnMakingDirectory(String dir_name) {
        // No rename logic needed for X-DOS.
        return true;
    }

    /** Prepare to create a directory (no-op for X-DOS) */
    @Override
    public boolean prepareToMakeDirectory(DiskBasicDirItem<DirectoryXdos> item) {
        return true;
    }

    /** Additional processing after creating a directory */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryXdos> item,
                                                 DiskBasicGroups group_items,
                                                 DiskBasicDirItem<DirectoryXdos> parent_item) {
        // No special handling for X-DOS
    }

    /** Additional processing after formatting the disk */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        setIdentifiedData(data);
        return true;
    }

    /** Calculate data size in the last sector of a file */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryXdos> item,
                                        InputStream istream,
                                        OutputStream ostream,
                                        byte[] sector_buffer,
                                        int sectorOffsrt, int sector_size,
                                        int remain_size) {
        if (item.needCheckEofCode()) {
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
        setGroupNumber(group_num, 0);
    }

    /** Additional processing after a file is deleted */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryXdos> item) {
        setGroupNumber(item.getStartGroup(0), 0);
        return true;
    }

    /** Retrieve IPL and volume name information */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
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
        if (basic.getFormatType().HasVolumeName()) {
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
