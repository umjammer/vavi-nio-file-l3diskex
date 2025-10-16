package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DirectoryTrsd13;
import l3diskex.basicfmt.BasicCommon.DirectoryTrsd23;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;
import static l3diskex.basicfmt.DiskBasicDirItemTRSDOS.FILETYPE_MASK_TRSDOS_SYSTEM;


public class DiskBasicTypeTRSDOS<T extends DirectoryT> extends DiskBasicType<T> {

    public static class TRSDOS_GAT {
        private byte[] m_buffer;
        private int m_size;
        private int m_groups_per_track;

        public TRSDOS_GAT() {
            m_buffer = null;
            m_size = 0;
            m_groups_per_track = 1;
        }

        public TRSDOS_GAT(byte[] n_buffer, int n_size, int n_groups_per_track) {
            m_buffer = n_buffer;
            m_size = n_size;
            m_groups_per_track = n_groups_per_track;
        }

        public void modify(int num, boolean val) {
            int pos = (int)num / m_groups_per_track;
            int bit = (int)num % m_groups_per_track;
            if (val) {
                m_buffer[pos] = (byte)((m_buffer[pos] | (1 << bit)));
            } else {
                m_buffer[pos] = (byte)((m_buffer[pos] & ~(1 << bit)));
            }
        }

        public boolean isSet(int num) {
            int pos = (int)num / m_groups_per_track;
            int bit = (int)num % m_groups_per_track;
            return ((m_buffer[pos] & (1 << bit)) != 0);
        }

        public void getPos(int num, int[] pos, int[] bit) {
            pos[0] = (int)num / m_groups_per_track;
            bit[0] = (int)num % m_groups_per_track;
        }

        public byte[] getBuffer() {
            return m_buffer;
        }

        public int getSize() {
            return m_size;
        }
    }

    public static class TRSDOS_HIT {
        private byte[] m_hit_buffer;
        private int m_hit_size;

        public TRSDOS_HIT() {
            m_hit_buffer = null;
            m_hit_size = 0;
        }

        public void assignHIT(byte[] n_buffer, int n_size) {
            m_hit_buffer = n_buffer;
            m_hit_size = n_size;
        }

        public byte getHI(int pos) {
            if (m_hit_buffer == null) return (byte) 0;
            if (m_hit_size <= pos) return (byte) 0;

            return m_hit_buffer[pos];
        }

        public void setHI(int pos, byte val) {
            if (m_hit_buffer == null) return;
            if (m_hit_size <= pos) return;

            m_hit_buffer[pos] = val;
        }

        public void deleteHI(int pos) {
            setHI(pos, (byte) 0);
        }
    }

    static class trsdos_gat_t {
        public byte[] gat = new byte[0x60];
        public byte[] tlt = new byte[0x60];
        public byte[] reserved1 = new byte[14];
        public short password;
        public byte[] name = new byte[8];
        public byte[] date = new byte[8];
        public byte[] apt = new byte[32];
    }

    protected TRSDOS_GAT gat_table;
    protected TRSDOS_GAT tlt_table;
    protected TRSDOS_HIT hit_impl; // Use composition for TRSDOS_HIT

    protected DiskBasicAvailability fat_availability = new DiskBasicAvailability();

    public DiskBasicTypeTRSDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super(basic, fat, dir);
        this.hit_impl = new TRSDOS_HIT(); // Initialize TRSDOS_HIT
        if (basic.diskBasicParam.getGroupsPerTrack() <= 0) {
            basic.diskBasicParam.setGroupsPerTrack(basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup());
        }
    }

    // Proxy methods for TRSDOS_HIT
    protected void assignHIT(byte[] n_buffer, int n_size) {
        hit_impl.assignHIT(n_buffer, n_size);
    }
    protected byte getHI(int pos) {
        return hit_impl.getHI(pos);
    }
    protected void setHI(int pos, byte val) {
        hit_impl.setHI(pos, val);
    }
    protected void deleteHI(int pos) {
        hit_impl.deleteHI(pos);
    }

    public void getFromHIPosition(int pos, int[] sector_num, int[] pos_in_sector) {
        // Implementation in derived classes
    }

    @Override
    public void setGroupNumber(int num, int val) {
        gat_table.modify(num, val != INVALID_GROUP_NUMBER);
    }

    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    @Override
    public boolean isUsedGroupNumber(int num) {
        return gat_table.isSet(num) || tlt_table.isSet(num);
    }

    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    @Override
    public int getEmptyGroupNumber() {
        int new_num = INVALID_GROUP_NUMBER;

        int mng_grp_sta = basic.getManagedTrackNumber() * basic.diskBasicParam.getGroupsPerTrack();
        int mng_grp_end = mng_grp_sta + basic.diskBasicParam.getGroupsPerTrack() - 1;

        for (int grp= 0; grp <= basic.getFatEndGroup(); grp = grp + 1) {
            if ((grp < mng_grp_sta || mng_grp_end < grp) && !isUsedGroupNumber(grp)) {
                new_num = grp;
                break;
            }
        }
        return new_num;
    }

    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        return getEmptyGroupNumber();
    }

    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        int sec = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        DiskImageSector sector = basic.getSectorFromSectorPos(sec);
        if (sector == null) {
            return -1.0;
        }
        trsdos_gat_t gat_sector = (trsdos_gat_t)(Object)sector.getSectorBuffer(); // Type cast placeholder

        gat_table = new TRSDOS_GAT(gat_sector.gat, gat_sector.gat.length, basic.diskBasicParam.getGroupsPerTrack());
        tlt_table = new TRSDOS_GAT(gat_sector.tlt, gat_sector.tlt.length, basic.diskBasicParam.getGroupsPerTrack());

        sector = basic.getSectorFromSectorPos(sec + 1);
        if (sector == null) {
            return -1.0;
        }
        assignHIT(sector.getSectorBuffer(), sector.getSectorBufferSize());

        basic.diskBasicParam.setSectorsPerFat(1);

        return valid_ratio;
    }

    @Override
    public double parseParamOnDisk(boolean is_formatting) {
        if (is_formatting) return 1.0;

        double valid_ratio = 1.0;

        int sec = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        DiskImageSector sector = basic.getSectorFromSectorPos(sec);
        if (sector == null) {
            return -1.0;
        }
        trsdos_gat_t gat_sector = (trsdos_gat_t)(Object)sector.getSectorBuffer(); // Type cast placeholder
        String volname = new String(gat_sector.name, 0, gat_sector.name.length);
        if (!volname.chars().allMatch(ch -> ch < 128)) {
            return -1.0;
        }

        basic.getFatEndGroup(); // The C++ code uses this but doesn't set it in this function. Assuming it's derived elsewhere or that the -1 is meant to be handled by the caller.

        return valid_ratio;
    }

    @Override
    public void getStartNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        int sec = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        getNumFromSectorPos(sec, track_num, side_num, sector_num);
    }

    @Override
    public void getEndNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        int sec = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + 1;
        getNumFromSectorPos(sec, track_num, side_num, sector_num);
    }

    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.getFatEndGroup() + 1;
        disk_size[0] = group_size[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fat_availability.empty();

        if (gat_table == null || gat_table.getBuffer() == null) return;
        if (tlt_table == null || tlt_table.getBuffer() == null) return;

        int mng_trk = basic.getManagedTrackNumber();
        int mng_start = mng_trk * basic.diskBasicParam.getGroupsPerTrack();
        int mng_end = mng_trk * basic.diskBasicParam.getGroupsPerTrack() + basic.diskBasicParam.getGroupsPerTrack() - 1;

        int group_size = basic.getSectorsPerGroup() * basic.getSectorSize();
        int max_group = basic.getFatEndGroup();

        for (int grp= 0; grp <= max_group; grp = grp + 1) {
            if (mng_start <= grp && grp <= mng_end) {
                fat_availability.Add(FAT_AVAIL_SYSTEM.getValue(), 0, 0);
            } else if (tlt_table.isSet(grp)) {
                fat_availability.Add(FAT_AVAIL_USED.getValue(), 0, 0);
            } else {
                if (gat_table.isSet(grp)) {
                    fat_availability.Add(FAT_AVAIL_USED.getValue(), 0, 0);
                } else {
                    fat_availability.Add(FAT_AVAIL_FREE.getValue(), group_size, 1);
                }
            }
        }
    }

    public int calcDataSizeOnLastSector(DiskBasicDirItem<?> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size) {
        return remain_size;
    }

    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num * basic.getSectorsPerGroup();
    }

    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return (group_num + 1) * basic.getSectorsPerGroup() - 1;
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        int st_pos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + basic.diskBasicParam.getDirStartSector() - 2;
        sector = basic.getSectorFromSectorPos(st_pos);
        if (sector == null) {
            return false;
        }
        sector.fill(basic.diskBasicParam.getFillCodeOnFAT());

        trsdos_gat_t gat_sector = (trsdos_gat_t)(Object)sector.getSectorBuffer();

        int mnt_st_grp = basic.getManagedTrackNumber() * basic.diskBasicParam.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
        int mnt_ed_grp = mnt_st_grp + basic.diskBasicParam.getGroupsPerTrack() - 1;
        for (int grp= 0; grp <= basic.getFatEndGroup(); grp = grp + 1) {
            if (grp < mnt_st_grp || mnt_ed_grp < grp) {
                gat_table.modify(grp, false);
            }
        }

        DiskBasicIdentifiedData ndata = data;
        LocalDateTime tm = LocalDateTime.now();
        if (Utils.convDateStrToTm(data.getVolumeDate()) == null) {
            ndata.setVolumeDate(Utils.formatYMDStr(tm));
        }

        setIdentifiedData(ndata);

        Arrays.fill(gat_sector.apt, (byte) 0x20);
        gat_sector.apt[0] = (byte) 0x0d;

        st_pos++;
        int ed_pos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + basic.diskBasicParam.getDirEndSector();
        for (int sec = st_pos; sec <= ed_pos; sec++) {
            sector = basic.getSectorFromSectorPos(sec);
            if (sector == null) {
                return false;
            }
            sector.fill(basic.diskBasicParam.getFillCodeOnDir());
        }

        return true;
    }

    @Override
    public int writeFile(DiskBasicDirItem<T> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws IOException {
        int len = 0;
        if (remain <= size) {
            if (remain < 0) remain = 0;
            if (remain > 0) istream.read(buffer, 0, remain);
            if (size > remain) {
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            istream.read(buffer, 0, size);
            len = size;
        }

        return len;
    }

    @Override
    public void deleteGroupNumber(int group_num) {
        setGroupNumber(group_num, INVALID_GROUP_NUMBER);
    }

    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        int sec = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        DiskImageSector sector = basic.getSectorFromSectorPos(sec);
        if (sector == null) {
            return;
        }
        trsdos_gat_t gat_sector = (trsdos_gat_t)(Object)sector.getSectorBuffer();

        String wname = new String(gat_sector.name, basic.getCharCodes().charset());
        wname = wname.trim();
        data.setVolumeName(wname);
        data.setVolumeNameMaxLength(gat_sector.name.length);

        int yy = (gat_sector.date[6] & 0xf) * 10 + (gat_sector.date[7] & 0xf);
        if (yy < 70) yy += 100;
        LocalDateTime tm = LocalDateTime.of(
                yy,
                (gat_sector.date[0] & 0xf) * 10 + (gat_sector.date[1] & 0xf) - 1,
                (gat_sector.date[3] & 0xf) * 10 + (gat_sector.date[4] & 0xf), 0, 0);
        data.setVolumeDate(Utils.formatYMDStr(tm));
    }

    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        int sec = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        DiskImageSector sector = basic.getSectorFromSectorPos(sec);
        if (sector == null) {
            return;
        }
        trsdos_gat_t gat_sector = (trsdos_gat_t)(Object)sector.getSectorBuffer();

        byte[] volname = data.getVolumeName().toUpperCase().getBytes();
        int len = Math.min(gat_sector.name.length, volname.length);
        Arrays.fill(gat_sector.name, (byte) basic.diskBasicParam.getDirSpaceCode());
        System.arraycopy(volname, 0, gat_sector.name, 0, len);

        LocalDate tm = Utils.convDateStrToTm(data.getVolumeDate());
        gat_sector.date[0] = (byte) (((tm.getMonth().ordinal() + 1) / 10) + 0x30);
        gat_sector.date[1] = (byte) (((tm.getMonth().ordinal() + 1) % 10) + 0x30);
        gat_sector.date[2] = (byte) '/';
        gat_sector.date[3] = (byte) ((tm.getDayOfMonth() / 10) + 0x30);
        gat_sector.date[4] = (byte) ((tm.getDayOfMonth() % 10) + 0x30);
        gat_sector.date[5] = (byte) '/';
        gat_sector.date[6] = (byte) (((tm.getYear() / 10) % 10) + 0x30);
        gat_sector.date[7] = (byte) ((tm.getYear() % 10) + 0x30);
    }
}

class DiskBasicTypeTRSD23 extends DiskBasicTypeTRSDOS<DirectoryTrsd23> {

    public DiskBasicTypeTRSD23(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryTrsd23> dir) {
        super(basic, fat, dir);
    }

    @Override
    public void getFromHIPosition(int pos, int[] sector_num, int[] pos_in_sector) {
        sector_num[0] = (pos & 0xf) + 2;
        pos_in_sector[0] = ((pos & 0xf0) >> 4) / 2;
    }

    public static int getHIPosition(int sector_num, int pos_in_sector) {
        int pos = (sector_num - 2);
        pos |= ((pos_in_sector * 2) << 4);
        return pos & 0xff;
    }

    public static byte computeHI(byte[] name) {
        int a;
        int c = 0;
        for (int b = 0; b < 11; b++) {
            a = name[b];
            a = (a ^ c);
            a <<= 1;
            c = (a & 0xff) | ((a & 0x100) >> 8);
        }
        if (c == 0) c = 1;
        return (byte) c;
    }

    @Override
    public boolean assignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryTrsd23> dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);

        List<DiskBasicDirItem<DirectoryTrsd23>> citems = dir_item.getChildren();
        for (int i = 0; i < citems.size(); i++) {
            DiskBasicDirItemTRSDOS<DirectoryTrsd23> citem = (DiskBasicDirItemTRSDOS<DirectoryTrsd23>)citems.get(i);
            int ov = citem.getOverflow() & 0xff;
            if (ov > 0 && ov < 254) {
                citem.visible(false);
                int[] ov_sec_num = new int[1];
                int[] ov_sec_pos = new int[1];
                getFromHIPosition(ov, ov_sec_num, ov_sec_pos);

                int num = (ov_sec_num[0] - 2) * basic.getSectorSize() / citem.getDataSize() + ov_sec_pos[0];
                if (num >= citems.size()) {
                    sts = false;
                    return sts;
                }
                DiskBasicDirItemTRSDOS<DirectoryTrsd23> pitem = (DiskBasicDirItemTRSDOS<DirectoryTrsd23>)citems.get(num);
                pitem.setNextItem(citem);
            }
        }

        for (int i = 0; i < citems.size(); i++) {
            DiskBasicDirItemTRSDOS<DirectoryTrsd23> citem = (DiskBasicDirItemTRSDOS<DirectoryTrsd23>)citems.get(i);
            citem.calcFileSize();
        }
        return sts;
    }

    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryTrsd23> item, int data_size, int flags, DiskBasicGroups group_items) throws IOException {
        int rc = 0;
        int sizeremain = data_size;

        int bytes_per_group = basic.diskBasicParam.getSectorsPerGroup() * basic.getSectorSize();
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        int pos_max = 4;
        while (rc >= 0 && limit >= 0 && sizeremain > 0) {
            int group_num = getEmptyGroupNumber();
            if (group_num == INVALID_GROUP_NUMBER) {
                rc = -1;
                break;
            }
            setGroupNumber(group_num, 1);

            basic.getNumsFromGroup(group_num, 0, basic.getSectorSize(), sizeremain, group_items);

            sizeremain -= bytes_per_group;
            limit--;
        }
        if (limit < 0) {
            rc = -1;
        }

        if (rc >= 0) {
            DiskBasicDirItemTRSDOS<?> titem = (DiskBasicDirItemTRSDOS<?>)item;
            titem.setAsNewFile();

            int pos = 0;
            int pre_grp= 0;
            int sta_grp= 0;
            int cnt = 0;
            int max_idx = group_items.count();
            for (int idx = 0; idx < max_idx; idx++) {
                int grp = group_items.item(idx).group;
                if (idx == 0) {
                    sta_grp = grp;
                } else if (cnt > 32 || pre_grp + 1 != grp) {
                    int[] pos_arr = new int[]{pos};
                    DiskBasicDirItemTRSDOS<DirectoryTrsd23>[] titem_arr = new DiskBasicDirItemTRSDOS[]{titem};
                    if (pos >= pos_max && createOverflowEntry(titem_arr, pos_arr) < 0) {
                        rc = -1;
                        break;
                    }
                    pos = pos_arr[0];
                    titem = titem_arr[0];
                    titem.setGranulesOnGap(pos, sta_grp, cnt);
                    cnt = 0;
                    sta_grp = grp;
                    pos++;
                }
                cnt++;
                pre_grp = grp;
            }
            if (rc >= 0 && cnt > 0) {
                int[] pos_arr = new int[]{pos};
                DiskBasicDirItemTRSDOS<DirectoryTrsd23>[] titem_arr = new DiskBasicDirItemTRSDOS[]{titem};
                if (pos >= pos_max && createOverflowEntry(titem_arr, pos_arr) < 0) {
                    rc = -1;
                }
                pos = pos_arr[0];
                titem = titem_arr[0];
                if (rc >= 0) {
                    titem.setGranulesOnGap(pos, sta_grp, cnt);
                    pos++;
                }
            }
        }

        if (rc < 0) {
            // DeleteGroups is not defined here, assuming it's an external method or on basic
            // basic.DeleteGroups(group_items);
        }

        return rc;
    }

    private int createOverflowEntry(DiskBasicDirItemTRSDOS<DirectoryTrsd23>[] ptitem, int[] pos) throws IOException {
        int rc = 0;
        DiskBasicDirItemTRSDOS<DirectoryTrsd23> new_titem = (DiskBasicDirItemTRSDOS<DirectoryTrsd23>)dir.getEmptyItemOnCurrent(ptitem[0], null);
        if (new_titem == null) {
            rc = -1;
            return rc;
        } else {
            int[] ccnt_arr = new int[1];
            int cgrp = ptitem[0].getGranulesOnGap(4, ccnt_arr);
            ptitem[0].clearGranulesOnGap(4, 0xfe, new_titem.getPositionInHIT());

            byte pos_in_hit = ptitem[0].getPositionInHIT();
            new_titem.setAsOverflowFile(pos_in_hit, getHI(pos_in_hit));

            pos[0] = 0;
            new_titem.setGranulesOnGap(pos[0], cgrp, ccnt_arr[0]);
            pos[0]++;

            ptitem[0].setNextItem(new_titem);

            ptitem[0] = new_titem;
        }
        return rc;
    }

    @Override
    public DiskBasicDirItem<DirectoryTrsd23> getEmptyDirectoryItem(DiskBasicDirItem<DirectoryTrsd23> parent, List<DiskBasicDirItem<DirectoryTrsd23>> items, DiskBasicDirItem<DirectoryTrsd23> pitem, DiskBasicDirItem<DirectoryTrsd23>[] next_item) {
        boolean is_sys = ((pitem.getFileAttr().getOrigin() & FILETYPE_MASK_TRSDOS_SYSTEM) != 0);

        DiskBasicDirItem<DirectoryTrsd23> match_item = null;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                DiskBasicDirItem<DirectoryTrsd23> item = items.get(i);
                if (!item.isUsed()) {
                    if ((is_sys && item.getPosition() < 0x40)
                            || (!is_sys && item.getPosition() >= 0x40)) {
                        match_item = item;
                        if (next_item != null) {
                            i++;
                            if (i < items.size() && !items.get(i).isUsed()) {
                                next_item[0] = items.get(i);
                            } else {
                                next_item[0] = null;
                            }
                        }
                        break;
                    }
                }
            }
        }
        return match_item;
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        if (!super.additionalProcessOnFormatted(data)) {
            return false;
        }

        DiskImageSector sector;

        int st_pos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + basic.diskBasicParam.getDirStartSector() - 2;
        sector = basic.getSectorFromSectorPos(st_pos);
        if (sector == null) {
            return false;
        }

        for (int grp= 0; grp <= basic.getFatEndGroup(); grp = grp + 1) {
            tlt_table.modify(grp, false);
        }

        trsdos_gat_t gat_sector = (trsdos_gat_t)(Object)sector.getSectorBuffer();

        // Assuming wxUINT16_SWAP_ON_BE is a byte-swapping utility
        // gat_sector.password = wxUINT16_SWAP_ON_BE(0x4296);
        gat_sector.password = (short) 0x4296; // Placeholder for byte-swapping

        DiskBasicDirItemTRSDOS<?> titem;

        st_pos += 2;
        sector = basic.getSectorFromSectorPos(st_pos);
        titem = (DiskBasicDirItemTRSDOS<?>)dir.newItem(sector, 0, sector.getSectorBuffer(0));
        titem.setAsBootSysEntry();
        // Assume delete titem is not needed in Java due to garbage collection
        // delete titem;

        st_pos++;
        sector = basic.getSectorFromSectorPos(st_pos);
        titem = (DiskBasicDirItemTRSDOS<?>)dir.newItem(sector, 0, sector.getSectorBuffer(0));
        titem.setAsDirSysEntry();
        // delete titem;

        sector = basic.getSectorFromSectorPos(0);
        if (sector == null) {
            return false;
        }
        sector.copy("\u0000\u00fe\u0011\u00f3".getBytes(), 4);
        gat_table.modify(0, true);

        return true;
    }
}

class DiskBasicTypeTRSD13 extends DiskBasicTypeTRSDOS<DirectoryTrsd13> {

    public DiskBasicTypeTRSD13(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryTrsd13> dir) {
        super(basic, fat, dir);
    }

    int getDataSize() {
        DiskBasicDirItemTRSD13 tmp = new DiskBasicDirItemTRSD13(basic, null, 0, null);
        return tmp.getDataSize();
    }

    @Override
    public void getFromHIPosition(int pos, int[] sector_num, int[] pos_in_sector) {
        int n = basic.getSectorSize() / getDataSize(); // Assuming a getSize method
        sector_num[0] = pos / n;
        pos_in_sector[0] = (pos % n);
    }

    @Override
    public boolean assignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryTrsd13> dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);

        List<DiskBasicDirItem<DirectoryTrsd13>> citems = dir_item.getChildren();
        for (int i = 0; i < citems.size(); i++) {
            DiskBasicDirItemTRSDOS<?> citem = (DiskBasicDirItemTRSDOS<?>)citems.get(i);
            citem.calcFileSize();
        }
        return sts;
    }

    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        if (pos[0] + getDataSize() > size[0]) {
            return -1;
        }
        return 0;
    }

    @Override
    public int adjustPositionAssigningDirectory(int pos) {
        return 0;
    }

    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<?> item, int data_size, int flags, DiskBasicGroups group_items) throws IOException {
        int rc = 0;
        int sizeremain = data_size;

        int bytes_per_group = basic.getSectorsPerGroup() * basic.getSectorSize();
        int limit = basic.getFatEndGroup() + 1;
        int pos_max = 13;
        while (rc >= 0 && limit >= 0 && sizeremain > 0) {
            int group_num = getEmptyGroupNumber();
            if (group_num == INVALID_GROUP_NUMBER) {
                rc = -1;
                break;
            }
            setGroupNumber(group_num, 1);

            basic.getNumsFromGroup(group_num, 0, basic.getSectorSize(), sizeremain, group_items);

            sizeremain -= bytes_per_group;
            limit--;
        }
        if (limit < 0) {
            rc = -1;
        }

        if (rc >= 0) {
            DiskBasicDirItemTRSDOS<?> titem = (DiskBasicDirItemTRSDOS<?>)item;
            titem.setAsNewFile();

            int pos = 0;
            int pre_grp= 0;
            int sta_grp= 0;
            int cnt = 0;
            int max_idx = group_items.count();
            for (int idx = 0; idx < max_idx; idx++) {
                int grp = group_items.item(idx).group;
                if (idx == 0) {
                    sta_grp = grp;
                } else if (cnt > 32 || pre_grp + 1 != grp) {
                    if (pos >= pos_max) {
                        rc = -1;
                        break;
                    }
                    titem.setGranulesOnGap(pos, sta_grp, cnt);
                    cnt = 0;
                    sta_grp = grp;
                    pos++;
                }
                cnt++;
                pre_grp = grp;
            }
            if (rc >= 0 && cnt > 0) {
                if (pos >= pos_max) {
                    rc = -1;
                }
                if (rc >= 0) {
                    titem.setGranulesOnGap(pos, sta_grp, cnt);
                    pos++;
                }
            }
        }

        if (rc < 0) {
            // DeleteGroups is not defined here, assuming it's an external method or on basic
            deleteGroups(group_items);
        }

        return rc;
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        if (!super.additionalProcessOnFormatted(data)) {
            return false;
        }

        DiskImageSector sector;

        int st_pos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + basic.diskBasicParam.getDirStartSector() - 2;
        sector = basic.getSectorFromSectorPos(st_pos);
        if (sector == null) {
            return false;
        }

        trsdos_gat_t gat_sector = (trsdos_gat_t)(Object)sector.getSectorBuffer();

        gat_sector.password = (short) 0x5cef; // be

        sector = basic.getSectorFromSectorPos(0);
        if (sector == null) {
            return false;
        }
        sector.copy("\u00fe\u0011\u003e\u00d0".getBytes(), 4, 0);
        gat_table.modify(0, true);

        return true;
    }
}
