package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.AmigaBlockPost;
import l3diskex.basicfmt.BasicCommon.AmigaBlockPre;
import l3diskex.basicfmt.BasicCommon.AmigaRootBlockPost;
import l3diskex.basicfmt.BasicCommon.DirectoryAmiga;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;
import static l3diskex.basicfmt.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_DATA;
import static l3diskex.basicfmt.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_HEADER;
import static l3diskex.basicfmt.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_ROOT;
import static l3diskex.basicfmt.DiskBasicDirItemAmiga.KEY_FAST_FILE_SYSTEM;
import static l3diskex.basicfmt.DiskBasicDirItemAmiga.KEY_INTERNATIONAL;
import static l3diskex.basicfmt.DiskBasicTemplates.gDiskBasicTemplates;


//
public class DiskBasicTypeAmiga extends DiskBasicType<DirectoryAmiga> {

    // Structs from header
    static class AmigaBootBlock {

        public static final int SIZE = 512;
        private final ByteBuffer buffer;

        public AmigaBootBlock(ByteBuffer buffer) {
            this.buffer = buffer.order(ByteOrder.BIG_ENDIAN);
        }

        public byte[] getType() {
            return Arrays.copyOfRange(buffer.array(), buffer.arrayOffset() + 0, buffer.arrayOffset() + 4);
        }

        public int getCheckSum() {
            return buffer.getInt(4);
        }

        public int getRootBlock() {
            return buffer.getInt(8);
        }

        public void setCheckSum(int value) {
            buffer.putInt(4, value);
        }

        public void setRootBlock(int value) {
            buffer.putInt(8, value);
        }

        public void setType(byte[] type) {
            buffer.position(0);
            buffer.put(type);
        }
    }

    static class AmigaBitmapBlock {

        private final ByteBuffer buffer;
        private final int blockSize;

        public AmigaBitmapBlock(ByteBuffer buffer, int blockSize) {
            this.buffer = buffer.order(ByteOrder.BIG_ENDIAN);
            this.blockSize = blockSize;
        }

        public int getCheckSum() {
            return buffer.getInt(0);
        }

        public ByteBuffer getMapBuffer() {
            buffer.position(4);
            return buffer.slice().order(ByteOrder.BIG_ENDIAN);
        }

        public void setCheckSum(int value) {
            buffer.putInt(0, value);
        }
    }

    /// ///////////////////////////////////////////////////////////////////

    static class AmigaOneBitmap {

        private final int m_block_num;
        private final int m_block_size;
        private final AmigaBitmapBlock m_map;
        private final ByteBuffer m_map_buffer;

        public AmigaOneBitmap() {
            m_block_num = 0;
            m_block_size = 0;
            m_map = null;
            m_map_buffer = null;
        }

        public AmigaOneBitmap(int block_num, ByteBuffer mapBuffer, int block_size) {
            m_block_num = block_num;
            m_block_size = block_size;
            m_map_buffer = mapBuffer;
            m_map = new AmigaBitmapBlock(mapBuffer, block_size);
        }

        public void Modify(int block_num, boolean use) {
            int pos = block_num >> 5;
            int bit = block_num & 0x1f;
            int dat = (1 << bit);
            dat = Integer.reverseBytes(dat);

            ByteBuffer mapBuf = m_map.getMapBuffer();
            int currentVal = mapBuf.getInt(pos * 4);

            if (use) {
                currentVal &= ~dat;
            } else {
                currentVal |= dat;
            }
            mapBuf.putInt(pos * 4, currentVal);
        }

        public boolean IsFree(int block_num) {
            int pos = block_num >> 5;
            int bit = block_num & 0x1f;
            int dat = (1 << bit);
            dat = Integer.reverseBytes(dat);

            ByteBuffer mapBuf = m_map.getMapBuffer();
            int currentVal = mapBuf.getInt(pos * 4);

            return ((currentVal & dat) != 0);
        }

        public void FreeAll(int block_num) {
            if (block_num >= GetBlockNums()) {
                block_num = GetBlockNums() - 1;
            }
            int pos = block_num >> 5;
            int bit = block_num & 0x1f;

            ByteBuffer mapBuf = m_map.getMapBuffer();

            for (int p = 0; p < pos; p++) {
                mapBuf.putInt(p * 4, 0xFFFFFFFF);
            }

            int dat = ((1 << (bit + 1)) - 1);
            dat = Integer.reverseBytes(dat);

            mapBuf.putInt(pos * 4, dat);
        }

        public int GetBlockNums() {
            return (m_block_size - 4) * 8;
        }

        public void UpdateCheckSum() {
            m_map.setCheckSum(0);

            // Recalculate the checksum over the whole block
            m_map_buffer.rewind();
            int calculatedCheckSum = DiskBasicTypeAmiga.calcCheckSumOnBootBlock(0, m_map_buffer, m_block_size);
            calculatedCheckSum = ~calculatedCheckSum;

            m_map.setCheckSum(calculatedCheckSum);
        }

        public int GetBlockNumber() {
            return m_block_num;
        }
    }

    /// ///////////////////////////////////////////////////////////////////

    static class AmigaBitmap extends ArrayList<AmigaOneBitmap> {

        public AmigaBitmap() {
            super();
        }

        public void AddBitmap(int block_num, ByteBuffer mapBuffer, int block_size) {
            add(new AmigaOneBitmap(block_num, mapBuffer, block_size));
        }

        public void Modify(int block_num, boolean use) {
            if (block_num < 2) return;

            block_num -= 2;
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                int itemBlockNums = item.GetBlockNums();
                if (block_num < itemBlockNums) {
                    item.Modify(block_num, use);
                    break;
                }
                block_num -= itemBlockNums;
            }
        }

        public boolean IsFree(int block_num) {
            if (block_num < 2) return false;

            block_num -= 2;
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                int itemBlockNums = item.GetBlockNums();
                if (block_num < itemBlockNums) {
                    return item.IsFree(block_num);
                }
                block_num -= itemBlockNums;
            }
            return false;
        }

        public void FreeAll(int block_num) {
            if (block_num < 2) return;

            block_num -= 2;
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                int itemBlockNums = item.GetBlockNums();
                if (block_num < itemBlockNums) {
                    item.FreeAll(block_num);
                } else {
                    item.FreeAll(0xffff_ffff);
                }
                block_num -= itemBlockNums;
            }
        }

        public int GetBlockNums() {
            int block_nums = 0;
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                block_nums += item.GetBlockNums();
            }
            return block_nums;
        }

        public void UpdateCheckSum() {
            for (int i = 0; i < size(); i++) {
                AmigaOneBitmap item = get(i);
                item.UpdateCheckSum();
            }
        }
    }


    private DirectoryAmiga m_root;
    private AmigaBitmap m_bitmap;

    private void initMembers() {
        m_root = new DirectoryAmiga();
        m_root.blockNum = 0;
        m_root.pre = null;
        m_root.post = null;
        m_bitmap = new AmigaBitmap();
    }

    public DiskBasicTypeAmiga(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryAmiga> dir) {
        super(basic, fat, dir);
        initMembers();
    }

    // access to FAT area
    @Override
    public void setGroupNumber(int num, int val) {
        m_bitmap.Modify(num, val != 0);
    }

    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    @Override
    public boolean isUsedGroupNumber(int num) {
        return !m_bitmap.IsFree(num);
    }

    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    @Override
    public int getEmptyGroupNumber() {
        return getEmptyGroupNumberM();
    }

    @Override
    public int getNextEmptyGroupNumber(int curr_group) {
        int next_group_num = getEmptyGroupNumber();
        if (next_group_num == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }

        if (chainGroups(curr_group, next_group_num) < 0) {
            return INVALID_GROUP_NUMBER;
        }

        return next_group_num;
    }

    // check / assign FAT area
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;
        return valid_ratio;
    }

    @Override
    public double parseParamOnDisk(boolean is_formatting) throws IOException {
        if (is_formatting) return 0;

        double valid_ratio = 1.0;

        DiskImageSector sector = basic.getSectorFromGroup(0);
        if (sector == null) {
            return -1.0;
        }
        byte[] buffer = sector.getSectorBuffer();
        if (buffer == null) {
            return -1.0;
        }
        AmigaBootBlock bb = new AmigaBootBlock(ByteBuffer.wrap(buffer));

        byte[] type = bb.getType();
        if ((!Arrays.equals(type, 0, 3, "DOS".getBytes(), 0, 3)) &&
                (!Arrays.equals(type, 0, 4, "KICK".getBytes(), 0, 4))) {
            return -1.0;
        }

        boolean disk_is_fast = (type[3] != 'K' && (type[3] & 1) != 0);
        boolean param_is_fast = basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM);
        if (disk_is_fast != param_is_fast) {
            return -1.0;
        }
        basic.diskBasicParam.setVariousParam(KEY_INTERNATIONAL, (type[3] & 6) == 2 || (type[3] & 6) == 4);

        m_root.blockNum = bb.getRootBlock();
        if (m_root.blockNum < 2 || m_root.blockNum > basic.diskBasicParam.getFatEndGroup()) {
            if (valid_ratio >= 0.0) valid_ratio *= 0.8;
            m_root.blockNum = basic.diskBasicParam.getManagedTrackNumber() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() + basic.diskBasicParam.getDirStartSector() - 1;
        }

        int[] root_track = new int[1];
        int[] root_side = new int[1];
        sector = basic.getSectorFromGroup(m_root.blockNum, root_track, root_side);
        if (sector == null) {
            return -1.0;
        }

        buffer = sector.getSectorBuffer();
        if (buffer == null) {
            return -1.0;
        }
        m_root.pre = new AmigaBlockPre();
        Serdes.Util.deserialize(new ByteArrayInputStream(buffer, 0, buffer.length), m_root.pre);

        int offset = basic.getSectorSize() - AmigaRootBlockPost.SIZE;
        if (offset < 0) {
            return -1.0;
        }
        buffer = sector.getSectorBuffer(offset);
        if (buffer == null) {
            return -1.0;
        }
        m_root.post = new AmigaBlockPost();
        buffer = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(buffer, offset, buffer.length - offset), m_root.post);

        basic.diskBasicParam.setManagedTrackNumber(root_track[0]);
        basic.diskBasicParam.setDirStartSector(sector.getSectorNumber());

        m_bitmap.clear();
        if (m_root.post.r.bmFlag == 0xffff_ffff) {
            for (int i = 0; i < 25; i++) {
                int num = m_root.post.r.bmPages[i];
                if (num < 2) {
                    continue;
                }
                sector = basic.getSectorFromGroup(num);
                if (sector == null) {
                    valid_ratio = 0.2;
                    break;
                }
                m_bitmap.AddBitmap(num, ByteBuffer.wrap(sector.getSectorBuffer()), sector.getSectorSize());
            }
        } else {
            valid_ratio = 0.2;
        }
        basic.diskBasicParam.setSectorsPerFat(m_bitmap.size());
        basic.diskBasicParam.setFatEndGroup(basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.diskBasicParam.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);

        int max_ht = m_root.pre.tableSize;
        int calc_max_ht = (basic.getSectorSize() -
                AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;
        if (max_ht > calc_max_ht) {
            valid_ratio = -1.0;
            max_ht = calc_max_ht;
        }

        for (int ht = 0; ht < max_ht; ht++) {
            int num = m_root.pre.u.table[ht];
            if (num > 0 && num < 2) {
                if (valid_ratio > 0.0) valid_ratio *= 0.5;
            } else if (num > basic.getFatEndGroup()) {
                if (valid_ratio > 0.0) valid_ratio *= 0.5;
            }
        }

        return valid_ratio;
    }

    @Override
    public void getStartNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        if (!m_bitmap.isEmpty()) {
            AmigaOneBitmap item = m_bitmap.get(0);
            getNumFromSectorPos(item.GetBlockNumber(), track_num, side_num, sector_num);
        }
    }

    @Override
    public void getEndNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        if (!m_bitmap.isEmpty()) {
            AmigaOneBitmap item = m_bitmap.get(m_bitmap.size() - 1);
            getNumFromSectorPos(item.GetBlockNumber(), track_num, side_num, sector_num);
        }
    }

    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    // check / assign directory area
    @Override
    public boolean calcGroupsOnRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items) throws IOException {
        group_items.empty();

        if (m_root.pre == null) {
            return false;
        }
        boolean valid = true;

        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        int max_blks = m_root.pre.tableSize;

        valid = DiskBasicDirItemAmiga.getDirectoryGroups(basic, m_root.pre.u.table, max_blks, limit, group_items);

        return valid;
    }

    @Override
    public boolean assignRootDirectory(int start_sector, int end_sector, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryAmiga> dir_item) throws IOException {
        boolean sts = super.assignRootDirectory(start_sector, end_sector, group_items, dir_item);
        if (dir_item != null) {
            int[] trk = new int[1];
            int[] sid = new int[1];
            DiskImageSector sector = basic.getSectorFromGroup(m_root.blockNum, trk, sid);
            dir_item.setDataPtr(0, null, sector, 0, sector.getSectorBuffer(), null);
        }
        DiskBasicDirItemAmiga.renumberInDirectory(basic, dir_item.getChildren());
        return sts;
    }

    @Override
    public boolean assignDirectory(boolean is_root, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryAmiga> dir_item) throws IOException {
        boolean sts = super.assignDirectory(is_root, group_items, dir_item);
        DiskBasicDirItemAmiga.renumberInDirectory(basic, dir_item.getChildren());
        return sts;
    }

    @Override
    public int initializeSectorsAsDirectory(DiskBasicGroups group_items, int[] file_size, int[] size_remain, DiskBasicError errinfo) {
        size_remain[0] = 0;
        return 0;
    }

    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] size_remain) {
        if (pos[0] > 0) {
            size_remain[0] += size[0];
            return -1;
        }
        return 0;
    }

    // disk size
    @Override
    public void getUsableDiskSize(int[] disk_size, int[] group_size) {
        group_size[0] = basic.diskBasicParam.getFatEndGroup() - 1;
        group_size[0] -= m_bitmap.size();
        disk_size[0] = group_size[0] * basic.getSectorSize();
    }

    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.clear();

        int block_size = basic.getSectorSize();
        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            block_size -= 24;
        }

        for (int num = 0; num <= basic.diskBasicParam.getFatEndGroup(); num++) {
            if (num < 2) {
                fatAvailability.Add(FAT_AVAIL_SYSTEM.getValue(), 0, 0);
            } else if (m_bitmap.IsFree(num)) {
                fatAvailability.Add(FAT_AVAIL_FREE.getValue(), block_size, 1);
            } else {
                fatAvailability.Add(FAT_AVAIL_USED.getValue(), 0, 0);
            }
        }

        if (m_root.blockNum < fatAvailability.size()) {
            fatAvailability.set(m_root.blockNum, FAT_AVAIL_SYSTEM.getValue());
        }

        for (int i = 0; i < m_bitmap.size(); i++) {
            AmigaOneBitmap item = m_bitmap.get(i);
            if (item.GetBlockNumber() < fatAvailability.size()) {
                fatAvailability.set(item.GetBlockNumber(), FAT_AVAIL_SYSTEM.getValue());
            }
        }
    }

    // file chain
    @Override
    public boolean prepareToSaveFile(InputStream istream, int[] file_size, DiskBasicDirItem<DirectoryAmiga> pitem, DiskBasicDirItem<DirectoryAmiga> nitem, DiskBasicError errinfo) {
        return true;
    }

    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryAmiga> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) throws IOException {
        int rc = 0;

        if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW && item.isDirectory()) {
            return 0;
        }

        int block_size = basic.getSectorSize();
        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            block_size -= 24;
        }
        int remain = data_size;
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        int group_num = INVALID_GROUP_NUMBER;
        int prev_group_num = 0;
        int prev_remain = 0;
        int groups = 0;

        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        int block_nums = aitem.getDataBlockNums();
        int block_idx = block_nums - 1;
        int extension = -1;
        int header_block_num = aitem.getStartGroup(fileunit_num);

        while (remain > 0 && limit >= 0 && rc >= 0) {
            if (block_idx < 0) {
                int ex_num = getEmptyGroupNumber();
                if (ex_num == INVALID_GROUP_NUMBER) {
                    rc = -2;
                    break;
                }
                DiskImageSector sector = basic.getSectorFromGroup(ex_num);
                if (sector == null) {
                    rc = -2;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    rc = -2;
                    break;
                }

                setGroupNumber(ex_num, 1);

                aitem.setExtension(ex_num);
                aitem.setHighSeq(block_nums);

                if (extension >= 0) {
                    // C++ delete not needed in Java
                }
                aitem = (DiskBasicDirItemAmiga) dir.newItem(sector, extension, buffer);

                sector.fill((byte) 0);
                aitem.InitForExtensionBlock(header_block_num);
                block_nums = aitem.getDataBlockNums();
                block_idx = block_nums - 1;

                extension++;
            }

            group_num = getEmptyGroupNumber();
            if (group_num == INVALID_GROUP_NUMBER) {
                rc = groups > 0 ? -2 : -1;
                break;
            }

            if (prev_group_num > 0) {
                basic.getNumsFromGroup(prev_group_num, group_num, basic.getSectorSize(), prev_remain, group_items);
            }
            prev_group_num = group_num;
            prev_remain = remain;

            setGroupNumber(group_num, 1);
            aitem.setDataBlock(block_idx, group_num);

            groups++;
            remain -= block_size;
            limit--;

            block_idx--;
        }

        if (prev_group_num > 0) {
            basic.getNumsFromGroup(prev_group_num, 0, basic.getSectorSize(), prev_remain, group_items);
        }

        aitem.setHighSeq(block_nums - block_idx - 1);

        if (limit < 0) {
            rc = groups > 0 ? -2 : -1;
        }

        // aitem switch

        return rc;
    }

    @Override
    public int chainGroups(int group_num, int append_group_num) {
        return 0;
    }

    @Override
    public int getStartSectorFromGroup(int group_num) {
        return group_num;
    }

    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        return group_num;
    }

    @Override
    public DiskBasicDirItem<DirectoryAmiga> getEmptyDirectoryItem(DiskBasicDirItem<DirectoryAmiga> parent, List<DiskBasicDirItem<DirectoryAmiga>> items, DiskBasicDirItem<DirectoryAmiga> pitem, DiskBasicDirItem<DirectoryAmiga>[] next_item) throws IOException {
        DiskBasicDirItem<DirectoryAmiga> match_item = null;
        byte[] name = new byte[32];
        int[] len = {name.length};
        int[] elen = new int[1];

        if (parent != null && pitem != null) {
            if (!parent.getFileAttr().isDirectory()) {
                return match_item;
            }

            pitem.getNativeFileName(name, len, null, elen);
            int hash = createHashNumberFromName(name, len[0]);

            int new_num = getEmptyGroupNumber();
            if (new_num == INVALID_GROUP_NUMBER) {
                return match_item;
            }

            DiskImageSector sector = basic.getSectorFromGroup(new_num);
            if (sector == null) {
                return match_item;
            }
            byte[] buffer = sector.getSectorBuffer();
            if (buffer == null) {
                return match_item;
            }

            DiskBasicDirItemAmiga apitem = (DiskBasicDirItemAmiga) pitem;
            apitem.setStartGroup(0, new_num);
            apitem.InitForHeaderBlock(parent.getStartGroup(0));

            match_item = (DiskBasicDirItem<DirectoryAmiga>) dir.newItem(sector, 0, buffer);

            sector.fill((byte) 0);
            setGroupNumber(new_num, 1);
            match_item.setParent(parent);

            DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;
            aparent.chainHashNumber(hash, new_num, match_item);

            int limit = basic.getFatEndGroup() + 1;
            int[] tables = aparent.getBlockTable();
            int nums = aparent.getDataBlockNums();

            if (items == null) {
                parent.createChildren();
                items = parent.getChildren();
            }
            DiskBasicDirItemAmiga.InsertItemInDirectory(basic, tables, nums, limit, items, match_item);

            return match_item;
        }
        return match_item;
    }

    // directory
    @Override
    public boolean isRootDirectory(int group_num) {
        return (group_num == m_root.blockNum);
    }

    @Override
    public boolean canMakeDirectory() {
        return true;
    }

    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryAmiga> item, DiskBasicGroups group_items, DiskBasicDirItem<DirectoryAmiga> parent_item) {
        m_bitmap.UpdateCheckSum();
        LocalDateTime tm = LocalDateTime.now();
        item.setFileModifyDateTime(tm);

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        aparent.setFileModifyDateTime(tm);
        setVolumeDateTime(tm);
        aparent.updateCheckSum();
        if (aparent.getParent() != null) updateCheckSumOnRoot();
    }

    // format
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        boolean is_ffs = basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM);
        boolean is_intr = false;
        boolean is_dirc = false;

        int track_num;
        int sector_num;

        int blk = 0;
        sector = basic.getSectorFromGroup(blk);
        sector.fill((byte) 0);
        byte[] bootBuffer = sector.getSectorBuffer();
        AmigaBootBlock boot = new AmigaBootBlock(ByteBuffer.wrap(bootBuffer));

        byte[] type = new byte[4];
        type[0] = (byte) 'D';
        type[1] = (byte) 'O';
        type[2] = (byte) 'S';
        if (is_ffs) {
            type[3] |= 1;
        }
        if (is_intr) {
            type[3] |= 2;
            if (is_dirc) type[3] += 2;
        }
        boot.setType(type);

        DiskBasicParam default_param = gDiskBasicTemplates.findType("", basic.getBasicTypeName());
        track_num = default_param.getManagedTrackNumber();
        sector_num = default_param.getDirStartSector();
        basic.diskBasicParam.setManagedTrackNumber(track_num);
        basic.diskBasicParam.setDirStartSector(sector_num);

        int root_block = getSectorPosFromNumS(track_num, sector_num);
        boot.setRootBlock(root_block);

        basic.diskBasicParam.setFatEndGroup(basic.getSidesPerDiskOnBasic() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);
        m_bitmap.clear();
        blk = root_block;
        do {
            blk++;
            sector = basic.getSectorFromGroup(blk);
            m_bitmap.AddBitmap(blk, ByteBuffer.wrap(sector.getSectorBuffer()), sector.getSectorSize());
        } while (m_bitmap.GetBlockNums() < basic.getFatEndGroup() + 1);

        m_bitmap.FreeAll(basic.getFatEndGroup());

        basic.diskBasicParam.setSectorsPerFat(m_bitmap.size());

        sector = basic.getSectorFromGroup(root_block);
        sector.fill((byte) 0);
        m_root.blockNum = root_block;

        byte[] rootBuffer = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(rootBuffer), m_root.pre);
        m_root.pre.type = FILETYPE_MASK_AMIGA_HEADER;

        int val = (sector.getSectorSize() - AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;
        m_root.pre.tableSize = val;

        int postOffset = sector.getSectorSize() - AmigaRootBlockPost.SIZE;
        Serdes.Util.deserialize(new ByteArrayInputStream(rootBuffer, postOffset, rootBuffer.length - postOffset), m_root.post);

        AmigaRootBlockPost r = m_root.post.r;

        r.bmFlag = 0xffff_ffff;
        for (int i = 0; i < m_bitmap.size(); i++) {
            val = m_bitmap.get(i).GetBlockNumber();
            r.bmPages[i] = val;
            m_bitmap.Modify(val, true);
        }

        r.secType = FILETYPE_MASK_AMIGA_ROOT;

        if (is_ffs && is_dirc) {
            r.extension = 0;
        }

        LocalDateTime tm = LocalDateTime.now();
        setCreateDateTime(tm);
        setVolumeDateTime(tm);
        setModifyDateTime(tm);

        setIdentifiedData(data);

        m_bitmap.Modify(root_block, true);

        m_bitmap.UpdateCheckSum();

        m_root.pre.checkSum = 0;
        int rootCheckSum = calcCheckSumOnBootBlock(0, ByteBuffer.wrap(rootBuffer), basic.getSectorSize());
        rootCheckSum = ~rootCheckSum;
        m_root.pre.checkSum = rootCheckSum;

        return true;
    }

    public static int calcCheckSumOnBootBlock(int sum, ByteBuffer data, int size) {
        data.rewind();
        data.limit(size);
        data.order(ByteOrder.BIG_ENDIAN);

        for (int i = 0; i < size; i += 4) {
            if (data.remaining() >= 4) {
                int d = data.getInt();
                int prev = sum;
                sum += d;
                if (sum < prev) sum++;
            }
        }
        return sum;
    }

    // data access (read / verify)
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryAmiga> item, InputStream istream, OutputStream ostream, ByteBuffer sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws java.io.IOException {
        ByteBuffer buffer = sector_buffer;
        int size = sector_size;
        int offset = 0;
        final int OFS_PRE_SIZE = 24;

        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            size -= OFS_PRE_SIZE;
            offset += OFS_PRE_SIZE;
        }

        buffer.position(offset);
        ByteBuffer dataBuffer = buffer.slice().order(buffer.order());

        if (remain_size < size) {
            size = remain_size;
        }

        byte[] data = new byte[size];
        dataBuffer.get(data, 0, size);

        if (ostream != null) {
            temp.setData(data, size, basic.isDataInverted());
            ostream.write(temp.getData(), 0, temp.getSize());
        }
        if (istream != null) {
            temp.setSize(size);
            istream.read(temp.getData(), 0, temp.getSize());
            temp.invertData(basic.isDataInverted());

            if (!Arrays.equals(temp.getData(), 0, size, data, 0, size)) {
                return -1;
            }
        }
        return size;
    }

    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryAmiga> item, InputStream istream, OutputStream ostream, ByteBuffer sector_buffer, int sector_size, int remain_size) {
        return remain_size;
    }

    // save / write
    @Override
    public boolean isEnoughFileSize(int size) {
        int block_size = basic.getSectorSize();
        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            block_size -= 24;
        }

        int table_cnt = (basic.getSectorSize() -
                AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;

        int data_cnt = (size + block_size - 1) / block_size;
        int header_cnt = 1 + data_cnt / table_cnt;

        return (getFreeGroupSize() >= (header_cnt + data_cnt));
    }

    public int writeFile(DiskBasicDirItem<DirectoryAmiga> item, InputStream istream, ByteBuffer buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) throws java.io.IOException {
        int len = 0;
        final int OFS_PRE_SIZE = 24;

        ByteBuffer preBuffer = null;
        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            preBuffer = buffer.slice().order(ByteOrder.BIG_ENDIAN);
            size -= OFS_PRE_SIZE;
            buffer.position(OFS_PRE_SIZE);
        } else {
            buffer.position(0);
        }

        byte[] dataToWrite = new byte[size];

        if (remain <= size) {
            if (remain < 0) remain = 0;
            if (remain > 0) {
                istream.read(dataToWrite, 0, remain);
            }
            if (size > remain) {
                Arrays.fill(dataToWrite, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            istream.read(dataToWrite, 0, size);
            len = size;
        }

        buffer.put(dataToWrite, 0, size);

        if (preBuffer != null) {
            DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;

            preBuffer.putInt(0, FILETYPE_MASK_AMIGA_DATA);
            preBuffer.putInt(8, aitem.getStartGroup(0));
            preBuffer.putInt(12, seq_num + 1);
            int val = (remain > size ? size : remain);
            preBuffer.putInt(16, val);
            preBuffer.putInt(20, next_group);
        }

        return len;
    }

    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryAmiga> item) {
        m_bitmap.UpdateCheckSum();

        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        aitem.updateCheckSumAll();

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        LocalDateTime tm = LocalDateTime.now();
        aparent.setFileModifyDateTime(tm);
        setVolumeDateTime(tm);
        aparent.updateCheckSum();
        if (aparent.getParent() != null) updateCheckSumOnRoot();
    }

    @Override
    public void additionalProcessOnRenamedFile(DiskBasicDirItem<DirectoryAmiga> item) throws IOException {
        byte[] name = new byte[32];
        int[] len = {name.length};
        int[] elen = new int[1];

        item.getNativeFileName(name, len, null, elen);
        int hash_num = createHashNumberFromName(name, len[0]);

        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        if (hash_num == aitem.getHashNumber()) {
            return;
        }

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        int limit = basic.getFatEndGroup() + 1;
        int[] tables = aparent.getBlockTable();
        int nums = aparent.getDataBlockNums();

        DiskBasicDirItemAmiga.deleteItemInDirectory(basic, tables, nums, limit, parent.getChildren(), item);

        int block_num = item.getStartGroup(0);
        aparent.chainHashNumber(hash_num, block_num, item);

        DiskBasicDirItemAmiga.InsertItemInDirectory(basic, tables, nums, limit, parent.getChildren(), item);
    }

    // delete
    @Override
    public void deleteGroupNumber(int group_num) {
        setGroupNumber(group_num, 0);
    }

    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryAmiga> item) {
        m_bitmap.UpdateCheckSum();

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return false;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        int limit = basic.getFatEndGroup() + 1;
        int[] tables = aparent.getBlockTable();
        int nums = aparent.getDataBlockNums();

        DiskBasicDirItemAmiga.deleteItemInDirectory(basic, tables, nums, limit, parent.getChildren(), item);

        LocalDateTime tm = LocalDateTime.now();
        aparent.setFileModifyDateTime(tm);
        setVolumeDateTime(tm);
        aparent.updateCheckSum();
        if (aparent.getParent() != null) updateCheckSumOnRoot();

        return true;
    }

    @Override
    public void releaseDirectoryItem(DiskBasicDirItem<DirectoryAmiga> item) {
        super.releaseDirectoryItem(item);
    }

    // property
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        if (m_root.post == null) return;

        AmigaRootBlockPost r = m_root.post.r;
        byte[] disk_name = r.diskName;
        int len = r.diskNameLen & 0xFF;

        String wname = new String(disk_name, 0, len, basic.getCharCodes().charset());
        data.setVolumeName(wname);
        data.setVolumeNameMaxLength(disk_name.length);

        LocalDateTime tm = DiskBasicDirItemAmiga.convDateToTm(r.cDays).atTime(
                DiskBasicDirItemAmiga.convTimeToTm(r.cMins, r.cTicks));

        String datetime = Utils.formatYMDStr(tm);
        datetime += " ";
        datetime += Utils.formatHMSStr(tm);
        data.setVolumeDate(datetime);
    }

    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        if (m_root.post == null) return;

        DiskBasicFormat fmt = basic.getFormatType();
        AmigaRootBlockPost r = m_root.post.r;

        if (fmt.HasVolumeName()) {
            byte[] name = new byte[r.diskName.length];
            byte[] src = data.getVolumeName().getBytes(basic.getCharCodes().charset());
            System.arraycopy(src, 0, name, 0, Math.min(src.length, name.length));
            if (src.length >= 0) {
                r.diskName = name;
                r.diskNameLen = (byte) (src.length & 0xff);
            }
        }
    }

    public void updateCheckSumOnRoot() {
        DiskBasicDirItem<DirectoryAmiga> aroot = (DiskBasicDirItem<DirectoryAmiga>) dir.getRootItem();
        ((DiskBasicDirItemAmiga) aroot).updateCheckSum();
    }

    public void setModifyDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = m_root.post.r;
        r.rDays = days[0];
        r.rMins = mins[0];
        r.rTicks = ticks[0];
    }

    public void setVolumeDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = m_root.post.r;
        r.vDays = days[0];
        r.vMins = mins[0];
        r.vTicks = ticks[0];
    }

    public void setCreateDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = m_root.post.r;
        r.cDays = days[0];
        r.cMins = mins[0];
        r.cTicks = ticks[0];
    }

    // private helper method
    private int getEmptyGroupNumberM() {
        int new_num = INVALID_GROUP_NUMBER;
        int num_of_secs = basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        int sta_trk = 0;
        int end_trk = 0;
        int ndir = 1;

        for (int i = 0; i < 2; i++) {
            switch (i) {
                case 0:
                    sta_trk = basic.getManagedTrackNumber();
                    end_trk = basic.getTracksPerSideOnBasic() + basic.getTrackNumberBaseOnDisk();
                    ndir = 1;
                    break;
                case 1:
                    sta_trk = basic.getManagedTrackNumber() - 1;
                    end_trk = basic.getTrackNumberBaseOnDisk() - 1;
                    ndir = -1;
                    break;
            }

            for (int trk_num = sta_trk; trk_num != end_trk && new_num == INVALID_GROUP_NUMBER; trk_num += ndir) {
                for (int sec_num = 0; sec_num < num_of_secs; sec_num++) {
                    int num = getSectorPosFromNumS(trk_num, sec_num + basic.getSectorNumberBase());
                    if (m_bitmap.IsFree(num)) {
                        new_num = num;
                        break;
                    }
                }
            }
        }
        return new_num;
    }

    // private helper method
    private int createHashNumberFromName(byte[] name, int size) {
        int hash;
        int bsize = (basic.getSectorSize() -
                AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;

        boolean is_intr = basic.diskBasicParam.getVariousBoolParam(KEY_INTERNATIONAL);

        int l = 0;
        while (l < size && name[l] != 0) {
            l++;
        }
        hash = l;

        for (int i = 0; i < l; i++) {
            hash *= 13;
            hash += Character.toUpperCase(name[i] & 0xFF);
            hash &= 0x7ff;
        }
        hash %= bsize;

        return hash;
    }
}