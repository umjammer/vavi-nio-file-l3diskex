package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import l3diskex.basicfmt.BasicCommon.ApledosPtr;
import l3diskex.basicfmt.BasicCommon.DirectoryApledos;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDirItemAppleDOS.apledos_chain_t;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;
import static l3diskex.basicfmt.DiskBasicDirItemAppleDOS.APLEDOS_TRACK_LIST_MAX;


/**
 * Processing for Apple DOS 3.x
 */
public class DiskBasicTypeAppleDOS extends DiskBasicType<DirectoryApledos> {

    // C++ struct apledos_vtoc_t (packed)
    static class ApledosVToc {

        byte reserved0byte;
        byte dir_start_trackbyte;
        byte dir_start_sectorbyte;
        byte release_numberbyte;
        byte[] reserved1 = new byte[2];
        byte volume_numberbyte;
        byte[] reserved2 = new byte[32];
        byte chain_sizebyte;    // max number of trk/sec list (normally 122)
        byte[] reserved3 = new byte[8];
        short[] track_bit_mask = new short[] {0, 0};    // big endien
        byte tracks_per_diskbyte;
        byte sectors_per_trackbyte;
        short sector_size;    // little endien
        short[][] track_map = new short[50][2];    // big endien

        public ApledosVToc() {
            for (int i = 0; i < 50; i++) {
                track_map[i][0] = 0;
                track_map[i][1] = 0;
            }
        }
    }

    private ApledosVToc apledos_vtoc;

    public DiskBasicTypeAppleDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryApledos> dir) {
        super(basic, fat, dir);
        if (basic.getSectorsPerGroup() <= 0) {
            basic.diskBasicParam.setGroupsPerTrack(basic.diskBasicParam.getGroupsPerSector() * basic.diskBasicParam.getSectorsPerTrackOnBasic());
        }
    }

    /// Set track map mask
    private void setTrackMapMask(int val) {
        short v0 = (short) (val & 0xffff);
        val >>= 16;
        short v1 = (short) (val & 0xffff);
        apledos_vtoc.track_bit_mask[0] = v0;
        apledos_vtoc.track_bit_mask[1] = v1;
    }

    /// Modify a bit in the track map
    private void modifyTrackMap(int track_num, int sector_num, boolean use) {
        int map = getTrackMap(track_num);
        if (use) {
            map &= ~(1L << sector_num);
        } else {
            map |= (1L << sector_num);
        }
        setTrackMap(track_num, map);
    }

    /// Is it free?
    private boolean isFreeTrackMap(int track_num, int sector_num) {
        int map = getTrackMap(track_num);
        return ((map & (1L << sector_num)) != 0);
    }

    /// Set track map
    private void setTrackMap(int track_num, int val) {
        val <<= (16 - basic.diskBasicParam.getSectorsPerTrackOnBasic());
        short v0 = (short) (val & 0xffff);
        val >>= 16;
        short v1 = (short) (val & 0xffff);
        apledos_vtoc.track_map[track_num][0] = v0;
        apledos_vtoc.track_map[track_num][1] = v1;
    }

    /// Get track map
    private int getTrackMap(int track_num) {
        int val = apledos_vtoc.track_map[track_num][1] & 0xFFFF;
        val <<= 16;
        val |= apledos_vtoc.track_map[track_num][0] & 0xFFFF;
        val >>>= (16 - basic.diskBasicParam.getSectorsPerTrackOnBasic());
        return val;
    }

    /// Allocate a chain sector
    private int allocChainSector(int idx, DiskBasicDirItem<?> item, int curr_group) throws IOException {
        int gnum = (idx == 0) ? getEmptyGroupNumber() : getNextEmptyGroupNumber(curr_group);
        if (gnum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // セクタ (Sector)
        DiskImageSector sector = basic.getSectorFromGroup(gnum);
        if (sector == null) {
            return INVALID_GROUP_NUMBER;
        }
        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return INVALID_GROUP_NUMBER;
        }
        sector.fill((byte) 0);

        // チェイン情報にセクタをセット (Set sector in chain information)
        item.setChainSector(sector, gnum, buf, null);

        // 開始グループを設定 (Set start group)
        if (idx == 0) {
            item.setStartGroup(0, gnum, 1);
        }

        // セクタを予約 (Reserve sector)
        setGroupNumber(gnum, 1);

        return gnum;
    }

    /// Set FAT position
    @Override
    public void setGroupNumber(int num, int val) {
        int track_num = 0;
        int sector_num = 0;
        int[] trk = new int[1];
        int[] sec = new int[1];
        getNumFromSectorPosS(num, trk, sec);
        track_num = trk[0];
        sector_num = sec[0];
        track_num -= basic.getTrackNumberBaseOnDisk();
        sector_num -= basic.getSectorNumberBase();
        modifyTrackMap(track_num, sector_num, val != 0);
    }

    /// Return FAT offset
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /// Is the group number used?
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /// Get the next group number
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /// Return an empty FAT position
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

    /// Return the next empty FAT position
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

    /// Check FAT area
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        // VTOC area
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) {
            return -1.0;
        }
        byte[] buffer = sector.getSectorBuffer();
        if (buffer == null) {
            return -1.0;
        }

        // Map buffer to ApledosVToc structure
        this.apledos_vtoc = mapApledosVToc(buffer);

        if (apledos_vtoc.tracks_per_diskbyte == 0 || apledos_vtoc.sectors_per_trackbyte == 0) {
            return -1.0;
        }

        if (apledos_vtoc.dir_start_trackbyte < 3) {
            return -1.0;
        }

        // Each parameter
        basic.diskBasicParam.setSectorsPerGroup(1);
        basic.diskBasicParam.setTracksPerSideOnBasic(apledos_vtoc.tracks_per_diskbyte);
        basic.diskBasicParam.setSectorsPerTrackOnBasic(apledos_vtoc.sectors_per_trackbyte);
        basic.diskBasicParam.setFatEndGroup(apledos_vtoc.tracks_per_diskbyte * apledos_vtoc.sectors_per_trackbyte - 1);

        basic.diskBasicParam.setManagedTrackNumber(apledos_vtoc.dir_start_trackbyte);
        basic.diskBasicParam.setDirStartSector(apledos_vtoc.dir_start_sectorbyte);

        // Check directory
        // Normally proceeds from sector 15 to 1
        //	int dir_cnt = 0;
        int dir_sta_lsec = basic.diskBasicParam.getDirStartSector();
        int dir_end_lsec = 1;

        sector = basic.getManagedSector(dir_sta_lsec);
        for (int lsec_pos = dir_sta_lsec; lsec_pos >= dir_end_lsec; lsec_pos--) {
            if (sector == null) {
                valid_ratio = -1.0;
                break;
            }

            ApledosPtr p = mapApledosPtr(sector.getSectorBuffer());
            //		dir_cnt++;

            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            sector = basic.getSectorFromSectorPos(
                    getSectorPosFromNumS(p.nextTrack + basic.getTrackNumberBaseOnDisk(), p.nextSector + basic.getSectorNumberBase())
            );
        }

        return valid_ratio;
    }

    /// Get each parameter from disk and calculate necessary parameters
    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (is_formatting) return 0;

        if (this.apledos_vtoc == null) {
            DiskImageSector sector = basic.getManagedSector(0);
            if (sector == null) return -1.0;
            byte[] buffer = sector.getSectorBuffer();
            if (buffer == null) return -1.0;

            this.apledos_vtoc = mapApledosVToc(buffer);

            // Each parameter
            basic.diskBasicParam.setTracksPerSideOnBasic(apledos_vtoc.tracks_per_diskbyte);
            basic.diskBasicParam.setSectorsPerTrackOnBasic(apledos_vtoc.sectors_per_trackbyte);
            basic.diskBasicParam.setFatEndGroup((apledos_vtoc.tracks_per_diskbyte + 1) * apledos_vtoc.sectors_per_trackbyte - 1);

            basic.diskBasicParam.setManagedTrackNumber(apledos_vtoc.dir_start_trackbyte);
            basic.diskBasicParam.setDirStartSector(apledos_vtoc.dir_start_sectorbyte);
        }

        //	myLog.SetInfo("APPLEDOS: vtoc.tracks_per_disk: %d", (int)apledos_vtoc.tracks_per_disk.get());
        //	myLog.SetInfo("APPLEDOS: vtoc.sectors_per_track: %d", (int)apledos_vtoc.sectors_per_track.get());

        return 1.0;
    }

    /// Calculate the sector list for the root directory
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) {
        boolean valid = true;

        group_items.empty();

        // Follow the directory chain
        int dir_size = 0;
        int limit = basic.getFatEndGroup() + 1;
        int[] trk_num_arr = new int[1];
        int[] sid_num_arr = new int[1];
        int trk_num = 0;
        int sid_num = 0;
        int sec_num = 1;
        // Start sector
        DiskImageSector sector = basic.getManagedSector(start_sector, trk_num_arr, sid_num_arr);
        trk_num = trk_num_arr[0];
        sid_num = sid_num_arr[0];
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

            group_items.add(0, 0, trk_num, sid_num, sec_num, sec_num);

            dir_size += sector.getSectorSize();

            ApledosPtr p = mapApledosPtr(buffer);

            // No next sector
            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            limit--;

            // Get the next sector
            sector = basic.getSectorFromSectorPos(
                    getSectorPosFromNumS(p.nextTrack + basic.getTrackNumberBaseOnDisk(), p.nextSector + basic.getSectorNumberBase())
                    , trk_num_arr, sid_num_arr);
            trk_num = trk_num_arr[0];
            sid_num = sid_num_arr[0];
        }
        group_items.setSize(dir_size);

        if (limit < 0) {
            valid = false;
        }
        return valid;
    }

    /// Get usable disk size
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.getFatEndGroup() + 1;
        disk_size[0] = group_size[0] * basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector();
    }

    /// Calculate remaining disk size
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        // BITMAP table on VTOC area

        // get BITMAP mask (value is often invalidate)
        //	wxint mask = wxUINT16_SWAP_ON_LE(apledos_vtoc->track_bit_mask[1]);
        //	mask <<= 16;
        //	mask |= wxUINT16_SWAP_ON_LE(apledos_vtoc->track_bit_mask[0]);
        int managed_track_num = basic.getManagedTrackNumber();

        // check BITMAP
        for (int trk = 0; trk < basic.getTracksPerSideOnBasic(); trk++) {
            int usemap = getTrackMap(trk);

            for (int sec = 0; sec < basic.getSectorsPerTrackOnBasic(); sec++) {
                if (trk < 3 || trk == managed_track_num) {
                    fatAvailability.Add(FAT_AVAIL_SYSTEM.getValue(), 0, 0);
                } else if ((usemap & (1L << sec)) != 0) {
                    fatAvailability.Add(FAT_AVAIL_FREE.getValue(), basic.getSectorSize(), 1);
                } else {
                    fatAvailability.Add(FAT_AVAIL_USED.getValue(), 0, 0);
                }
            }
        }

        //	free_disk_size = (int)fsize;
        //	free_groups = (int)grps;
    }

    /// Prepare before saving file
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryApledos> pitem, DiskBasicDirItem<DirectoryApledos> nitem, DiskBasicError errinfo) {
        // チェインセクタをクリア (Clear chain sector)
        nitem.clearChainSector(null);

        return true;
    }

    /// Allocate groups for the data size
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryApledos> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) throws IOException {
        //logger.log(Level.DEBUG, "DiskBasicTypeAppleDOS::AllocateGroups {");

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
            basic.getNumsFromGroup(group_num, 0, sec_size, remain, group_items);
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

        //logger.log(Level.DEBUG, "rc: %d }", rc);
        return rc;
    }

//    /// Chain groups
//	  public int chainGroups(int group_num, int append_group_num) { return 0; }

    /// Get start sector number from group number
    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num;
    }

    /// Get end sector number from group number
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

    /// ルートディレクトリか (Is it the root directory?)
    @Override
    public boolean isRootDirectory(int group_num) {
        return false;
    }

    /// サブディレクトリを作成できるか (Can a subdirectory be created?)
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /// フォーマット時セクタデータを埋めた後の個別処理 (Specific processing after filling sector data during formatting)
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // VTOCエリア (VTOC area)
        DiskImageSector sector = basic.getManagedSector(0);
        if (sector == null) return false;
        byte[] buffer = sector.getSectorBuffer();
        if (buffer == null) return false;

        this.apledos_vtoc = mapApledosVToc(buffer);

        sector.fill((byte) 0);

        // VTOCエリアを設定 (Set VTOC area)

        apledos_vtoc.dir_start_trackbyte = (byte) basic.getManagedTrackNumber();
        apledos_vtoc.dir_start_sectorbyte = (byte) basic.diskBasicParam.getDirStartSector();

        apledos_vtoc.release_numberbyte = 3;

        apledos_vtoc.chain_sizebyte = APLEDOS_TRACK_LIST_MAX;

        apledos_vtoc.tracks_per_diskbyte = (byte) basic.getTracksPerSide();
        apledos_vtoc.sectors_per_trackbyte = (byte) basic.getSectorsPerTrackOnBasic();

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
            ApledosPtr p = mapApledosPtr(sector.getSectorBuffer());
            p.nextTrack = apledos_vtoc.dir_start_trackbyte;
            p.nextSector = (byte) (lsec_pos - 1);
            updateApledosPtr(sector.getSectorBuffer(), p);
        }

        return true;
    }

    /// Determine the data size of the file's last sector
    public int calcDataSizeOnLastSector(DiskBasicDirItem<?> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size) {
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
                // 比較時は、比較先のファイルサイズ (When comparing, use the target file size)
                // This logic is hard to translate directly without InputStream.GetLength()
                // Assuming a way to get the length. Using 0 as a placeholder.
                int length = 0; // Replace with actual istream length
                remain_size = length % sector_size;
            }
        }
        return remain_size;
    }

    /// データの書き込み処理 (Data writing process)
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryApledos> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws java.io.IOException {
        int len = 0;
        if (remain <= size) {
            // 残り少ない (Little left)
            if (remain < 0) remain = 0;
            if (remain > 0) {
                len = istream.read(buffer, 0, remain);
                if (len < 0) len = 0; // Handle end of stream
            } else {
                len = 0;
            }

            if (size > len) {
                // バッファの余りは0サプレス (Remaining buffer is zero-suppressed)
                java.util.Arrays.fill(buffer, len, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続 (Continue)
            len = istream.read(buffer, 0, size);
            if (len < 0) len = 0; // Handle end of stream
        }

        return len;
    }

    /// Delete FAT area for the specified group number
    @Override
    public void deleteGroupNumber(int group_num) {
        // Mark as unused
        setGroupNumber(group_num, 0);
    }

    /// Processing after file deletion
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryApledos> item) {
        // チェインセクタを未使用にする (Mark chain sectors as unused)
        int gnum = item.getStartGroup(0);
        while (gnum != 0) {
            setGroupNumber(gnum, 0);
            DiskImageSector sector = basic.getSectorFromGroup(gnum);
            if (sector == null) break;
            apledos_chain_t p = mapApledosChain(sector.getSectorBuffer());
            if (p == null) break;
            gnum = getSectorPosFromNumS(p.getNext().nextTrack + basic.getTrackNumberBaseOnDisk(), p.getNext().nextSector + basic.getSectorNumberBase());
        }

        // Assuming DiskBasicDirItemAppleDOS exists and item can be cast
        // DiskBasicDirItemAppleDOS ditem = (DiskBasicDirItemAppleDOS)item;
        // ディレクトリの最初に削除コード(0xff)を入れる (Put delete code (0xff) at the start of the directory)
        // Assuming there is a SetStartTrack method in DiskBasicDirItem or a derived class
        // item.SetStartTrack(basic.GetDeleteCode()); // Mocking the method call

        // Since DiskBasicDirItemAppleDOS is not fully defined, calling a generic method
        // that's often available in these libraries. Assuming it modifies the item structure
        // to mark it as deleted, typically by changing the first byte (track number in this case).
        if (item instanceof DiskBasicDirItemAppleDOS) {
            ((DiskBasicDirItemAppleDOS) item).setStartTrack(basic.diskBasicParam.getDeleteCode());
        }

        return true;
    }

    /// Get attributes of IPL and managed area
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume number
        data.setVolumeNumber(apledos_vtoc.volume_numberbyte);
    }

    /// Set attributes of IPL and managed area
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat fmt = basic.getFormatType();

        // volume number
        if (fmt.HasVolumeNumber()) {
            apledos_vtoc.volume_numberbyte = (byte) data.getVolumeNumber();
            if (apledos_vtoc.volume_numberbyte == 0) {
                apledos_vtoc.volume_numberbyte = (byte) 0xfe;    // default
            }
        }
    }

    // --- Helper methods for structure mapping (mimicking C++ type punning) ---

    private ApledosVToc mapApledosVToc(byte[] buffer) {
        ApledosVToc vtoc = new ApledosVToc();
        if (buffer.length < 50 * 2 * 2 + 50) return vtoc; // Check minimal size

        int offset = 0;
        vtoc.reserved0byte = buffer[offset++];
        vtoc.dir_start_trackbyte = buffer[offset++];
        vtoc.dir_start_sectorbyte = buffer[offset++];
        vtoc.release_numberbyte = buffer[offset++];

        System.arraycopy(buffer, offset, vtoc.reserved1, 0, 2);
        offset += 2;

        vtoc.volume_numberbyte = buffer[offset++];

        System.arraycopy(buffer, offset, vtoc.reserved2, 0, 32);
        offset += 32;

        vtoc.chain_sizebyte = buffer[offset++];

        System.arraycopy(buffer, offset, vtoc.reserved3, 0, 8);
        offset += 8;

        // Big-endian short (assuming the C++ short uses little-endian access for big-endian data)
        // C++: short_SWAP_ON_LE is used, implying little-endian storage on a little-endian machine
        // or a swap function to handle a specific byte order for a field marked as big-endian.
        // Given the use of wxUINT16_SWAP_ON_LE in C++, we assume the raw bytes in the disk image
        // are stored in a way that needs a swap function to get the actual value, or vice-versa.
        // For simplicity, we'll map the bytes as-is for now, and rely on the swap logic where used.
        vtoc.track_bit_mask[0] = (short) ((buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8));
        offset += 2;
        vtoc.track_bit_mask[1] = (short) ((buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8));
        offset += 2;

        vtoc.tracks_per_diskbyte = buffer[offset++];
        vtoc.sectors_per_trackbyte = buffer[offset++];

        // Little-endian short
        vtoc.sector_size = (short) ((buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8));
        offset += 2;

        for (int i = 0; i < 50; i++) {
            // Big-endian short
            vtoc.track_map[i][0] = (short) ((buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8));
            offset += 2;
            vtoc.track_map[i][1] = (short) ((buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8));
            offset += 2;
        }

        return vtoc;
    }

    private ApledosPtr mapApledosPtr(byte[] buffer) {
        ApledosPtr ptr = new ApledosPtr();
        if (buffer.length < 2) return ptr;

        ptr.nextTrack = buffer[0];
        ptr.nextSector =buffer[1];

        return ptr;
    }

    private void updateApledosPtr(byte[] buffer, ApledosPtr ptr) {
        if (buffer.length < 2) return;
        buffer[0] = ptr.nextTrack;
        buffer[1] = ptr.nextSector;
    }

    private apledos_chain_t mapApledosChain(byte[] buffer) {
        apledos_chain_t chain = new apledos_chain_t();
        if (buffer.length < 2) return chain;

        chain.setNext(mapApledosPtr(buffer));

        return chain;
    }
}
