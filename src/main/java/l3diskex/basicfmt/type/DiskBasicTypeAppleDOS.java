///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.ApledosPtr;
import l3diskex.basicfmt.BasicCommon.DirectoryApledos;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.apledos_chain_t;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.apledos_chain_t.APLEDOS_TRACK_LIST_MAX;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;


/**
 * Processing for Apple DOS 3.x
 *
 * DiskBasicParam
 * @li DirStartPositionOnSector ディレクトリエントリの開始位置
 */
public class DiskBasicTypeAppleDOS extends DiskBasicType<DirectoryApledos> {

    /// Apple DOS Volume table of contents (VTOC)
    @Serdes
    static class ApledosVToc {

        @Element(sequence = 1)
        byte reserved0byte;
        @Element(sequence = 2)
        byte dir_start_trackbyte;
        @Element(sequence = 3)
        byte dir_start_sectorbyte;
        @Element(sequence = 4)
        byte release_numberbyte;
        @Element(sequence = 5)
        byte[] reserved1 = new byte[2];
        @Element(sequence = 6)
        byte volume_numberbyte;
        @Element(sequence = 7)
        byte[] reserved2 = new byte[32];
        // max number of trk/sec list (normally 122)
        @Element(sequence = 8)
        byte chain_sizebyte;
        @Element(sequence = 9)
        byte[] reserved3 = new byte[8];
        @Element(sequence = 10)
        short[] track_bit_mask = new short[] {0, 0}; // big endien
        @Element(sequence = 11)
        byte tracks_per_diskbyte;
        @Element(sequence = 12)
        byte sectors_per_trackbyte;
        @Element(sequence = 13, bigEndian = "false")
        short sector_size; // little endien
        @Element(sequence = 14)
        short[][] track_map = new short[50][2]; // big endien

        public ApledosVToc() {
            for (int i = 0; i < 50; i++) {
                track_map[i][0] = 0;
                track_map[i][1] = 0;
            }
        }
    }

    /** */
    private ApledosVToc apledos_vtoc;

    /** */
    public DiskBasicTypeAppleDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryApledos> dir) {
        super(basic, fat, dir);

        if (basic.getSectorsPerGroup() <= 0) {
            basic.diskBasicParam.setGroupsPerTrack(basic.diskBasicParam.getGroupsPerSector() * basic.diskBasicParam.getSectorsPerTrackOnBasic());
        }
    }

    /** Set track map mask */
    private void setTrackMapMask(int val) {
        apledos_vtoc.track_bit_mask[0] = (short) (val & 0xffff);
        val >>= 16;
        apledos_vtoc.track_bit_mask[1] = (short) (val & 0xffff);
    }

    /** Modify a bit in the track map */
    private void modifyTrackMap(int track_num, int sector_num, boolean use) {
        int map = getTrackMap(track_num);
        if (use) {
            map &= ~(1 << sector_num);
        } else {
            map |= (1 << sector_num);
        }
        setTrackMap(track_num, map);
    }

    /// Is it free?
    private boolean isFreeTrackMap(int track_num, int sector_num) {
        int map = getTrackMap(track_num);
        return ((map & (1L << sector_num)) != 0);
    }

    /** Set track map */
    private void setTrackMap(int track_num, int val) {
        val <<= (16 - basic.getSectorsPerTrackOnBasic());
        apledos_vtoc.track_map[track_num][0] = (short) (val & 0xffff);
        val >>= 16;
        apledos_vtoc.track_map[track_num][1] = (short) (val & 0xffff);
    }

    /** Get track map */
    private int getTrackMap(int track_num) {
        int val = apledos_vtoc.track_map[track_num][1] & 0xffff;
        val <<= 16;
        val |= apledos_vtoc.track_map[track_num][0] & 0xffff;
        val >>>= (16 - basic.diskBasicParam.getSectorsPerTrackOnBasic());
        return val;
    }

    /** Allocate a chain sector */
    private int allocChainSector(int idx, DiskBasicDirItem<?> item, int curr_group) throws IOException {
        int gnum = (idx == 0) ? getEmptyGroupNumber() : getNextEmptyGroupNumber(curr_group);
        if (gnum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // Sector
        DiskImageSector sector = basic.getSectorFromGroup(gnum);
        if (sector == null) {
            return INVALID_GROUP_NUMBER;
        }
        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return INVALID_GROUP_NUMBER;
        }
        sector.fill((byte) 0);

        // Set sector in chain information
        item.setChainSector(sector, gnum, buf, null);

        // Set start group
        if (idx == 0) {
            item.setStartGroup(0, gnum, 1);
        }

        // Reserve sector
        setGroupNumber(gnum, 1);

        return gnum;
    }

    /** Set FAT position */
    @Override
    public void setGroupNumber(int num, int val) {
        int[] track_num = {0};
        int[] sector_num = {0};
        getNumFromSectorPosS(num, track_num, sector_num);
        track_num[0] -= basic.getTrackNumberBaseOnDisk();
        sector_num[0] -= basic.getSectorNumberBase();
        modifyTrackMap(track_num[0], sector_num[0], val != 0);
    }

    /** Return FAT offset */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /// Is the group number used?
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /** Get the next group number */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /** Return an empty FAT position */
    @Override
    public int getEmptyGroupNumber() {
        int new_num = INVALID_GROUP_NUMBER;

        int manage_track_num = basic.getManagedTrackNumber();
        int sta_trk = 0;
        int end_trk = 0;
        int ndir = 1;

        for (int i = 0; i < 2 && new_num == INVALID_GROUP_NUMBER; i++) {
            switch (i) {
                case 0:
                    // From track numbers larger than the managed area
                    sta_trk = manage_track_num + 1;
                    end_trk = basic.getTracksPerSideOnBasic();
                    ndir = 1;
                    break;
                case 1:
                    // To track numbers smaller than the managed area
                    sta_trk = manage_track_num - 1;
                    end_trk = 2;
                    ndir = -1;
                    break;
            }

            for (int sec = basic.getSectorsPerTrackOnBasic() - 1; sec >= 0; sec--) {
                for (int trk = sta_trk; trk != end_trk && new_num == INVALID_GROUP_NUMBER; trk += ndir) {
                    // Prioritize searching the end of each track
                    if (isFreeTrackMap(trk, sec)) {
                        // Free space found
                        new_num = trk * basic.getSectorsPerTrackOnBasic() + sec;
                        break;
                    }
                }
            }
        }

        return new_num;
    }

    /** Return the next empty FAT position */
    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        // Candidate for the next empty position
        int new_num = INVALID_GROUP_NUMBER;

        int manage_track_num = basic.getManagedTrackNumber();
        int curr_trk = curr_group / basic.getSectorsPerTrackOnBasic();
        int sta_trk = 0;
        int end_trk = 0;
        int ndir = 1;

        for (int i = 0; i < 2 && new_num == INVALID_GROUP_NUMBER; i++) {
            switch (i) {
                case 0:
                    // From track numbers larger than the managed area
                    sta_trk = curr_trk;
                    end_trk = basic.getTracksPerSideOnBasic();
                    ndir = 1;
                    break;
                case 1:
                    // To track numbers smaller than the managed area
                    sta_trk = curr_trk;
                    end_trk = 2;
                    ndir = -1;
                    break;
            }

            for (int trk = sta_trk; trk != end_trk && new_num == INVALID_GROUP_NUMBER; trk += ndir) {
                if (trk == manage_track_num) {
                    trk += ndir;
                }
                for (int sec = basic.getSectorsPerTrackOnBasic() - 1; sec >= 0; sec--) {
                    if (isFreeTrackMap(trk, sec)) {
                        // Free space found
                        new_num = trk * basic.getSectorsPerTrackOnBasic() + sec;
                        break;
                    }
                }
            }
        }

        return new_num;
    }

    /** Check FAT area */
    @Override
    public double checkFat(boolean is_formatting) throws IOException {
        double valid_ratio = 1.0;

        // VTOC area
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            return -1.0;
        }
        ApledosVToc vtoc = new ApledosVToc();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vtoc);

        this.apledos_vtoc = vtoc;

        if (vtoc.tracks_per_diskbyte == 0 || vtoc.sectors_per_trackbyte == 0) {
            return -1.0;
        }

        if (vtoc.dir_start_trackbyte < 3) {
            return -1.0;
        }

        // Each parameter
        basic.diskBasicParam.setSectorsPerGroup(1);
        basic.diskBasicParam.setTracksPerSideOnBasic(vtoc.tracks_per_diskbyte);
        basic.diskBasicParam.setSectorsPerTrackOnBasic(vtoc.sectors_per_trackbyte);
        basic.diskBasicParam.setFatEndGroup(vtoc.tracks_per_diskbyte * vtoc.sectors_per_trackbyte - 1);

        basic.diskBasicParam.setManagedTrackNumber(vtoc.dir_start_trackbyte);
        basic.diskBasicParam.setDirStartSector(vtoc.dir_start_sectorbyte);

        // Check directory
        // Normally proceeds from sector 15 to 1
        //int dir_cnt = 0;
        int dir_sta_lsec = basic.diskBasicParam.getDirStartSector();
        int dir_end_lsec = 1;

        sector = basic.getManagedSector(dir_sta_lsec);
        for (int lsec_pos = dir_sta_lsec; lsec_pos >= dir_end_lsec; lsec_pos--) {
            if (sector == null) {
                valid_ratio = -1.0;
                break;
            }

            ApledosPtr p = new ApledosPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), p);
            //dir_cnt++;

            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            sector = basic.getSectorFromSectorPos(
                    getSectorPosFromNumS(p.nextTrack + basic.getTrackNumberBaseOnDisk(), p.nextSector + basic.getSectorNumberBase())
            );
        }

        return valid_ratio;
    }

    /** Get each parameter from disk and calculate necessary parameters */
    @Override
    public double parseParamOnDisk(boolean is_formatting) throws IOException {
        if (is_formatting) return 0;

        if (this.apledos_vtoc == null) {
            DiskImageSector sector = basic.getManagedSector(0);
            byte[] b = sector.getSectorBuffer();
            ApledosVToc vtoc = new ApledosVToc();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), vtoc);
            this.apledos_vtoc = vtoc;

            // Each parameter
            basic.diskBasicParam.setTracksPerSideOnBasic(vtoc.tracks_per_diskbyte);
            basic.diskBasicParam.setSectorsPerTrackOnBasic(vtoc.sectors_per_trackbyte);
            basic.diskBasicParam.setFatEndGroup((vtoc.tracks_per_diskbyte + 1) * vtoc.sectors_per_trackbyte - 1);

            basic.diskBasicParam.setManagedTrackNumber(vtoc.dir_start_trackbyte);
            basic.diskBasicParam.setDirStartSector(vtoc.dir_start_sectorbyte);
        }

        //logger.log(Level.INFO, "APPLEDOS: vtoc.tracks_per_disk: %d".formatted((int) apledos_vtoc.tracks_per_disk.get());
        //logger.log(Level.INFO, "APPLEDOS: vtoc.sectors_per_track: %d".formatted((int) apledos_vtoc.sectors_per_track.get());

        return 1.0;
    }

    /** Calculate the sector list for the root directory */
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) throws IOException {
        boolean valid = true;

        group_items.clear();

        // Follow the directory chain
        int dir_size = 0;
        int limit = basic.getFatEndGroup() + 1;
        int[] trk_num = {0};
        int[] sid_num = {0};
        int sec_num = 1;
        // Start sector
        DiskImageSector sector = basic.getManagedSector(start_sector, trk_num, sid_num);
        while (limit >= 0) {
            if (sector == null) {
                valid = false;
                break;
            }
            sec_num = sector.getSectorNumber();

            byte[] buffer = sector.getSectorBuffer();
            if (buffer == null) {
                valid = false;
                break;
            }

            group_items.add(0, 0, trk_num[0], sid_num[0], sec_num, sec_num);

            dir_size += sector.getSectorSize();

            ApledosPtr p = new ApledosPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(buffer), p);

            // No next sector
            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            limit--;

            // Get the next sector
            sector = basic.getSectorFromSectorPos(
                    getSectorPosFromNumS(p.nextTrack + basic.getTrackNumberBaseOnDisk(), p.nextSector + basic.getSectorNumberBase())
                    , trk_num, sid_num);
        }
        group_items.setSize(dir_size);

        if (limit < 0) {
            valid = false;
        }
        return valid;
    }

    /** Get usable disk size */
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.getFatEndGroup() + 1;
        disk_size[0] = group_size[0] * basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector();
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        // BITMAP table on VTOC area

        // get BITMAP mask (value is often invalidate)
        //int mask = apledos_vtoc.track_bit_mask[1];
        //mask <<= 16;
        //mask |= apledos_vtoc.track_bit_mask[0];
        int managed_track_num = basic.getManagedTrackNumber();

        // check BITMAP
        for (int trk = 0; trk < basic.getTracksPerSideOnBasic(); trk++) {
            int usemap = getTrackMap(trk);

            for (int sec = 0; sec < basic.getSectorsPerTrackOnBasic(); sec++) {
                if (trk < 3 || trk == managed_track_num) {
                    fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
                } else if ((usemap & (1L << sec)) != 0) {
                    fatAvailability.add(FAT_AVAIL_FREE, basic.getSectorSize(), 1);
                } else {
                    fatAvailability.add(FAT_AVAIL_USED, 0, 0);
                }
            }
        }

        //free_disk_size = (int) fsize;
        //free_groups = (int) grps;
    }

    /** Prepare before saving file */
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryApledos> pitem, DiskBasicDirItem<DirectoryApledos> nitem, DiskBasicError errinfo) throws IOException {
        // チェインセクタをクリア (Clear chain sector)
        nitem.clearChainSector(null);

        return true;
    }

    /** Allocate groups for the data size */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryApledos> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) throws IOException {
        //logger.log(Level.TRACE, "DiskBasicTypeAppleDOS::AllocateGroups {");

        //int file_size = 0;
        int groups = 0;

        int rc = 0;
        int sec_size = basic.getSectorSize();
        int remain = data_size;
        int limit = basic.getFatEndGroup() + 1;
        int chain_idx = 0;
        int group_num = INVALID_GROUP_NUMBER;
        while (remain > 0 && limit >= 0) {
            // Allocate chain sector
            if ((chain_idx % APLEDOS_TRACK_LIST_MAX) == 0) {
                group_num = allocChainSector(chain_idx, item, group_num);
                if (group_num == INVALID_GROUP_NUMBER) {
                    // Error
                    rc = groups > 0 ? -2 : -1;
                    return rc;
                }
            }

            // Search for free space
            group_num = getNextEmptyGroupNumber(group_num);
            if (group_num == INVALID_GROUP_NUMBER) {
                // No free space
                rc = groups > 0 ? -2 : -1;
                return rc;
            }

            // Mark as used
            basic.getNumsFromGroup(group_num, 0, sec_size, remain, group_items[0]);
            setGroupNumber(group_num, 1);

            // Update chain sector
            item.addChainGroupNumber(chain_idx, group_num);
            chain_idx++;

            //file_size += sec_size;
            groups++;

            remain -= sec_size;

            limit--;
        }
        if (limit < 0) {
            // Infinite loop?
            rc = -2;
        }

        //logger.log(Level.TRACE, "rc: %d }".formatted(rc));
        return rc;
    }

//    /** Chain groups */
//	  public int chainGroups(int group_num, int append_group_num) { return 0; }

    /** Get start sector number from group number */
    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num;
    }

    /** Get end sector number from group number */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return group_num;
    }

    /// Get track, side, sector numbers from sector position
    @Override
    public void getNumFromSectorPos(int sector_pos, int[] track_num, int[] side_num, int[] sector_num, int[] div_num, int[] div_nums) {
        int selected_side = basic.getSelectedSide();
        int numbering_sector = basic.getNumberingSector();
        int sectors_per_track = basic.getSectorsPerTrackOnBasic();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();
        int groups_per_sector = basic.diskBasicParam.getGroupsPerSector();
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();

        int trksid_num = sector_pos / groups_per_track;
        if (selected_side >= 0) {
            // 1S
            track_num[0] = trksid_num;
            side_num[0] = selected_side;
        } else {
            // 2D, 2HD
            track_num[0] = trksid_num / sides_per_disk;
            side_num[0] = trksid_num % sides_per_disk;
        }
        sector_num[0] = ((sector_pos % groups_per_track) / groups_per_sector);
        if (div_num != null) div_num[0] = ((sector_pos % groups_per_track) % groups_per_sector);

        if (numbering_sector == 1) {
            // トラックごとに連番の場合 (If numbering is sequential per track)
            sector_num[0] += (side_num[0] * sectors_per_track);
        }

        // サイド番号を逆転するか (Reverse side number?)
        side_num[0] = basic.diskBasicParam.getReversedSideNumber(side_num[0]);

        track_num[0] += basic.getTrackNumberBaseOnDisk();
        side_num[0] += basic.getSideNumberBaseOnDisk();
        sector_num[0] += basic.getSectorNumberBase();

        if (div_nums != null) div_nums[0] = groups_per_sector;
    }

    /// Get track, sector numbers from sector position
    @Override
    public void getNumFromSectorPosS(int sector_pos, int[] track_num, int[] sector_num) {
        int selected_side = basic.getSelectedSide();
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();

        if (selected_side >= 0) {
            // 1S
            track_num[0] = sector_pos / groups_per_track;
            sector_num[0] = (sector_pos % groups_per_track);
        } else {
            // 2D, 2HD
            track_num[0] = sector_pos / (groups_per_track * sides_per_disk);
            sector_num[0] = (sector_pos % (groups_per_track * sides_per_disk));
        }

        track_num[0] += basic.getTrackNumberBaseOnDisk();
        sector_num[0] += basic.getSectorNumberBase();
    }

    /// Get sector position from track, side, sector numbers
    @Override
    public int getSectorPosFromNum(int track_num, int side_num, int sector_num, int div_num, int div_nums) {
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();
        int selected_side = basic.getSelectedSide();
        int numbering_sector = basic.getNumberingSector();
        int sides_per_disk = basic.getSidesPerDiskOnBasic();
        int sector_pos;

        track_num -= basic.getTrackNumberBaseOnDisk();
        side_num -= basic.getSideNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBase();

        // Reverse side number?
        side_num = basic.getReversedSideNumber(side_num);

        if (selected_side >= 0) {
            // 1S
            sector_pos = track_num * groups_per_track;
            sector_pos += (sector_num * div_nums + div_num);
        } else {
            // 2D, 2HD
            sector_pos = track_num * sides_per_disk * groups_per_track;
            if (numbering_sector == 1) {
                sector_pos += (sector_num * div_nums + div_num);
            } else {
                sector_pos += (side_num % sides_per_disk) * groups_per_track;
                sector_pos += (sector_num * div_nums + div_num);
            }
        }

        return sector_pos;
    }

    /// Get sector position from track, sector numbers
    @Override
    public int getSectorPosFromNumS(int track_num, int sector_num) {
        int selected_side = basic.getSelectedSide();
        int groups_per_track = basic.diskBasicParam.getGroupsPerTrack();
        int sides_per_disk = basic.diskBasicParam.getSidesPerDiskOnBasic();
        int sector_pos;

        track_num -= basic.getTrackNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBase();

        if (selected_side >= 0) {
            // 1S
            sector_pos = track_num * groups_per_track + sector_num;
        } else {
            // 2D, 2HD
            sector_pos = track_num * groups_per_track * sides_per_disk + sector_num;
        }
        return sector_pos;
    }

    /// Is it the root directory?
    @Override
    public boolean isRootDirectory(int group_num) {
        return false;
    }

    /// Can a subdirectory be created?
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /** Specific processing after filling sector data during formatting */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // VTOC area
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) return false;
        byte[] b = sector.getSectorBuffer();
        if (b == null) return false;
        ApledosVToc vtoc = new ApledosVToc();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), vtoc);

        this.apledos_vtoc = vtoc;

        sector.fill((byte) 0);

        // Set VTOC area

        vtoc.dir_start_trackbyte = (byte) basic.getManagedTrackNumber();
        vtoc.dir_start_sectorbyte = (byte) basic.diskBasicParam.getDirStartSector();

        vtoc.release_numberbyte = 3;

        vtoc.chain_sizebyte = APLEDOS_TRACK_LIST_MAX;

        vtoc.tracks_per_diskbyte = (byte) basic.getTracksPerSide();
        vtoc.sectors_per_trackbyte = (byte) basic.getSectorsPerTrackOnBasic();

        int map = 0;
        for (int sec = 0; sec < basic.getSectorsPerTrackOnBasic(); sec++) {
            map |= (1L << sec);
        }
        setTrackMapMask(map);

        for (int trk = 3; trk < basic.getTracksPerSideOnBasic(); trk++) {
            setTrackMap(trk, map);
        }

        // volume number
        setIdentifiedData(data);

        // DIRエリア (DIR area)
        int dir_sta_lsec = basic.diskBasicParam.getDirStartSector();
        int dir_end_lsec = 2;
        for (int lsec_pos = dir_sta_lsec; lsec_pos >= dir_end_lsec; lsec_pos--) {
            sector = basic.getManagedSector(lsec_pos);
            if (sector == null) {
                continue;
            }
            sector.fill((byte) 0);
            ApledosPtr p = new ApledosPtr();
            Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), p);
            p.nextTrack = apledos_vtoc.dir_start_trackbyte;
            p.nextSector = (byte) (lsec_pos - 1);
//            sector.copy(sector.getSectorBuffer(), p); TODO write back
        }

        return true;
    }

    /// Determine the data size of the file's last sector
    public int calcDataSizeOnLastSector(DiskBasicDirItem<?> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size) throws IOException {
        // File size is sector size boundary, so calculation is needed
        if (item.needCheckEofCode()) {
            // Output up to one byte before the termination code
            int eof_code = basic.invertUint8(basic.diskBasicParam.getTextTerminateCode());
            for (int len = 0; len < remain_size; len++) {
                if (sector_buffer[len] == (byte) eof_code) {
                    remain_size = len;
                    break;
                }
            }
        } else {
            // No calculation method, so return remaining size as is
            if (istream != null) {
                // When comparing, use the target file size
                remain_size = istream.available() % sector_size;
            }
        }
        return remain_size;
    }

    /** Data writing process */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryApledos> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        int len = 0;
        if (remain <= size) {
            // Little left
            if (remain < 0) remain = 0;
            if (remain > 0) istream.readNBytes(buffer, 0, remain);
            if (size > len) {
                // Remaining buffer is zero-suppressed
                Arrays.fill(buffer, len, size, (byte) 0);
            }
            len = remain;
        } else {
            // Continue
            istream.readNBytes(buffer, 0, size);
            len = size;
        }

        return len;
    }

    /** Delete FAT area for the specified group number */
    @Override
    public void deleteGroupNumber(int group_num) {
        // Mark as unused
        setGroupNumber(group_num, 0);
    }

    /** Processing after file deletion */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryApledos> item) throws IOException {
        // Mark chain sectors as unused
        int gnum = item.getStartGroup(0);
        while (gnum != 0) {
            setGroupNumber(gnum, 0);
            DiskImageSector sector = basic.getSectorFromGroup(gnum);
            if (sector == null) break;
            byte[] b = sector.getSectorBuffer();
            if (b == null) break;
            apledos_chain_t p = new apledos_chain_t();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
            gnum = getSectorPosFromNumS((p.next.nextTrack & 0xff) + basic.getTrackNumberBaseOnDisk(), (p.next.nextSector & 0xff) + basic.getSectorNumberBase());
        }

        DiskBasicDirItemAppleDOS ditem = (DiskBasicDirItemAppleDOS) item;
        // Put delete code (0xff) at the start of the directory
        ditem.setStartTrack(basic.diskBasicParam.getDeleteCode());

        return true;
    }

    /** Get attributes of IPL and managed area */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume number
        data.setVolumeNumber(apledos_vtoc.volume_numberbyte);
    }

    /** Set attributes of IPL and managed area */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat fmt = basic.getFormatType();

        // volume number
        if (fmt.hasVolumeNumber()) {
            apledos_vtoc.volume_numberbyte = (byte) data.getVolumeNumber();
            if (apledos_vtoc.volume_numberbyte == 0) {
                apledos_vtoc.volume_numberbyte = (byte) 0xfe;    // default
            }
        }
    }
}
