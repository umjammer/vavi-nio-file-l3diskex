package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.DiskBasicDirItemTRSD13.DirectoryTrsD13;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.DiskBasicDirItemTRSD23.DirectoryTrsD23;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.FILETYPE_MASK_TRSDOS_SYSTEM;


public abstract class DiskBasicTypeTRSDOS<T extends Directory> extends DiskBasicType<T> {

    /**
     * TRSDos GAT (Granule Allocation Table)
     */
    public static class TrsDosGat {

        private final byte[] buffer;
        private final int size;
        private final int groupsPerTrack;

        public TrsDosGat() {
            buffer = null;
            size = 0;
            groupsPerTrack = 1;
        }

        public TrsDosGat(byte[] buffer, int size, int groupsPerTrack) {
            this.buffer = buffer;
            this.size = size;
            this.groupsPerTrack = groupsPerTrack;
        }

        public void modify(int num, boolean val) {
            int pos = num / groupsPerTrack;
            int bit = num % groupsPerTrack;
            if (val) {
                buffer[pos] = (byte) ((buffer[pos] | (1 << bit)));
            } else {
                buffer[pos] = (byte) ((buffer[pos] & ~(1 << bit)));
            }
        }

        public boolean isSet(int num) {
            int pos = num / groupsPerTrack;
            int bit = num % groupsPerTrack;
            return ((buffer[pos] & (1 << bit)) != 0);
        }

        public void getPos(int num, int[] pos, int[] bit) {
            pos[0] = num / groupsPerTrack;
            bit[0] = num % groupsPerTrack;
        }

        public byte[] getBuffer() {
            return buffer;
        }

        public int getSize() {
            return size;
        }
    }

    //
    // TRSDOS HIT (Hash Index Table)
    //
    public static class TrsDosHit {

        private byte[] hitBuffer;
        private int hitSize;

        public TrsDosHit() {
            hitBuffer = null;
            hitSize = 0;
        }

        public void assignHIT(byte[] buffer, int size) {
            hitBuffer = buffer;
            hitSize = size;
        }

        public byte getHI(int pos) {
            if (hitBuffer == null) return (byte) 0;
            if (hitSize <= pos) return (byte) 0;

            return hitBuffer[pos];
        }

        public void setHI(int pos, byte val) {
            if (hitBuffer == null) return;
            if (hitSize <= pos) return;

            hitBuffer[pos] = val;
        }

        public void deleteHI(int pos) {
            setHI(pos, (byte) 0);
        }
    }

    // GATエリア構造
    @Serdes
    static class TrsDosGatSector {

        @Element(sequence = 1)
        public byte[] gat = new byte[0x60];
        @Element(sequence = 2)
        public byte[] tlt = new byte[0x60];
        @Element(sequence = 3)
        public byte[] reserved1 = new byte[14];
        @Element(sequence = 4)
        public short password;
        @Element(sequence = 5)
        public byte[] name = new byte[8];
        @Element(sequence = 6)
        public byte[] date = new byte[8];
        @Element(sequence = 7)
        public byte[] apt = new byte[32];
    }

    /** GAT Granule Allocate Table */
    protected TrsDosGat gatTable;
    /** TLT Track Lock-out Table */
    protected TrsDosGat tltTable;

    /** Use composition for TRSDOS_HIT */
    public TrsDosHit hit;

    public DiskBasicTypeTRSDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        super(basic, fat, dir);
        this.hit = new TrsDosHit();

        if (basic.getGroupsPerTrack() <= 0) {
            basic.setGroupsPerTrack(basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup());
        }
    }

    /** ハッシュの格納位置からセクタ番号を得る */
    public abstract void getFromHIPosition(int pos, int[] sectorNum, int[] posInSector);

    @Override
    public void setGroupNumber(int num, int val) {
        gatTable.modify(num, val != INVALID_GROUP_NUMBER);
    }

    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    @Override
    public boolean isUsedGroupNumber(int num) {
        return gatTable.isSet(num) || tltTable.isSet(num);
    }

    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return INVALID_GROUP_NUMBER;
    }

    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;

        int managedGroupStart = basic.getManagedTrackNumber() * basic.getGroupsPerTrack();
        int managedGroupEnd = managedGroupStart + basic.getGroupsPerTrack() - 1;

        for (int group = 0; group <= basic.getFatEndGroup(); group = group + 1) {
            if ((group < managedGroupStart || managedGroupEnd < group) && !isUsedGroupNumber(group)) {
                newNum = group;
                break;
            }
        }
        return newNum;
    }

    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        return getEmptyGroupNumber();
    }

    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        // GATエリア
        int s = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        DiskImageSector sector = basic.getSectorFromSectorPos(s);
        if (sector == null) {
            return -1.0;
        }
        TrsDosGatSector gatSector = new TrsDosGatSector();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), gatSector);

        gatTable = new TrsDosGat(gatSector.gat, gatSector.gat.length, basic.getGroupsPerTrack());
        tltTable = new TrsDosGat(gatSector.tlt, gatSector.tlt.length, basic.getGroupsPerTrack());

        sector = basic.getSectorFromSectorPos(s + 1);
        if (sector == null) {
            return -1.0;
        }
        hit.assignHIT(sector.getSectorBuffer(), sector.getSectorBufferSize());

        basic.setSectorsPerFat(1);

        return validRatio;
    }

    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;

        double validRatio = 1.0;

        // GATエリアにあるボリューム名
        int s = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        DiskImageSector sector = basic.getSectorFromSectorPos(s);
        if (sector == null) {
            return -1.0;
        }
        TrsDosGatSector gatSector = new TrsDosGatSector();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), gatSector);
        String volumeName = new String(gatSector.name);
        if (!volumeName.chars().allMatch(ch -> ch < 128)) {
            return -1.0;
        }

        basic.setFatEndGroup(basic.getSidesPerDiskOnBasic() * basic.getTracksPerSideOnBasic() * basic.getGroupsPerTrack() - 1);

        return validRatio;
    }

    @Override
    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int s = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        getNumFromSectorPos(s, trackNum, sideNum, sectorNum);
    }

    @Override
    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int s = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + 1;
        getNumFromSectorPos(s, trackNum, sideNum, sectorNum);
    }

    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup();
    }

    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();

        if (gatTable == null || gatTable.getBuffer() == null) return;
        if (tltTable == null || tltTable.getBuffer() == null) return;

        int managedTrack = basic.getManagedTrackNumber();
        int managedStart = managedTrack * basic.getGroupsPerTrack();
        int managedEnd = managedTrack * basic.getGroupsPerTrack() + basic.getGroupsPerTrack() - 1;

        int groupSize = basic.getSectorsPerGroup() * basic.getSectorSize();
        int maxGroup = basic.getFatEndGroup();

        for (int group = 0; group <= maxGroup; group = group + 1) {
            if (managedStart <= group && group <= managedEnd) {
                fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
            } else if (tltTable.isSet(group)) {
                fatAvailability.add(FAT_AVAIL_USED, 0, 0);
            } else {
                if (gatTable.isSet(group)) {
                    fatAvailability.add(FAT_AVAIL_USED, 0, 0);
                } else {
                    fatAvailability.add(FAT_AVAIL_FREE, groupSize, 1);
                }
            }
        }
    }

    public int calcDataSizeOnLastSector(DiskBasicDirItem<?> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorSize, int remainSize) {
        return remainSize;
    }

    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum * basic.getSectorsPerGroup();
    }

    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return (groupNum + 1) * basic.getSectorsPerGroup() - 1;
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        // GAT
        int startPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + basic.getDirStartSector() - 2;
        sector = basic.getSectorFromSectorPos(startPos);
        if (sector == null) {
            // Why?
            return false;
        }
        sector.fill(basic.getFillCodeOnFAT());

        TrsDosGatSector gatSector = new TrsDosGatSector();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), gatSector);

        // GAT set free area
        int mountStartGroup = basic.getManagedTrackNumber() * basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
        int mountEndGroup = mountStartGroup + basic.getGroupsPerTrack() - 1;
        for (int group = 0; group <= basic.getFatEndGroup(); group = group + 1) {
            if (group < mountStartGroup || mountEndGroup < group) {
                gatTable.modify(group, false);
            }
        }

        // 日付
        DiskBasicIdentifiedData nData = data;
        LocalDate tm = LocalDate.now();
        if (Utils.convDateStrToTm(data.getVolumeDate()) == null) {
            // 現在日付をセット
            nData.setVolumeDate(Utils.formatYMDStr(tm));
        }

        // ボリューム名を設定
        setIdentifiedData(nData);

        // APT
        Arrays.fill(gatSector.apt, (byte) 0x20);
        gatSector.apt[0] = (byte) 0x0d;

        // HIT, FDE ディレクトリ
        startPos++;
        int endPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + basic.getDirEndSector();
        for (int sectorPos = startPos; sectorPos <= endPos; sectorPos++) {
            sector = basic.getSectorFromSectorPos(sectorPos);
            if (sector == null) {
                return false;
            }
            sector.fill(basic.getFillCodeOnDir());
        }

        return true;
    }

    @Override
    public int writeFile(DiskBasicDirItem<T> item, InputStream iStream, byte[] buffer, int size, int remain,
                         int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;
        if (remain <= size) {
            if (remain < 0) remain = 0;
            if (remain > 0) iStream.readNBytes(buffer, 0, remain);
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        return len;
    }

    @Override
    public void deleteGroupNumber(int groupNum) {
        setGroupNumber(groupNum, INVALID_GROUP_NUMBER);
    }

    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // GATエリア
        int sectorPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        DiskImageSector sector = basic.getSectorFromSectorPos(sectorPos);
        if (sector == null) {
            return;
        }
        TrsDosGatSector gatSector = new TrsDosGatSector();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), gatSector);

        // volume name
        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(gatSector.name, 0, gatSector.name.length, sb, -1);
        String wName = sb.toString().trim();
        data.setVolumeName(wName);
        data.setVolumeNameMaxLength(gatSector.name.length);
        // volume date
        int yy = (gatSector.date[6] & 0xf) * 10 + (gatSector.date[7] & 0xf);
        if (yy < 70) yy += 100;
        LocalDate tm = LocalDate.of(
                yy,
                (gatSector.date[0] & 0xf) * 10 + (gatSector.date[1] & 0xf) - 1,
                (gatSector.date[3] & 0xf) * 10 + (gatSector.date[4] & 0xf));
        data.setVolumeDate(Utils.formatYMDStr(tm));
    }

    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // GATエリア
        int sectorPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        DiskImageSector sector = basic.getSectorFromSectorPos(sectorPos);
        if (sector == null) {
            return;
        }
        TrsDosGatSector gatSector = new TrsDosGatSector();
        byte[] b = sector.getSectorBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), gatSector);

        // volume name
        byte[] volname = data.getVolumeName().toUpperCase().getBytes();
        int len = Math.min(gatSector.name.length, volname.length);
        Arrays.fill(gatSector.name, basic.getDirSpaceCode());
        System.arraycopy(volname, 0, gatSector.name, 0, len);
        // volume date
        LocalDate tm = Utils.convDateStrToTm(data.getVolumeDate());
        gatSector.date[0] = (byte) (((tm.getMonth().ordinal() + 1) / 10) + 0x30);
        gatSector.date[1] = (byte) (((tm.getMonth().ordinal() + 1) % 10) + 0x30);
        gatSector.date[2] = (byte) '/';
        gatSector.date[3] = (byte) ((tm.getDayOfMonth() / 10) + 0x30);
        gatSector.date[4] = (byte) ((tm.getDayOfMonth() % 10) + 0x30);
        gatSector.date[5] = (byte) '/';
        gatSector.date[6] = (byte) (((tm.getYear() / 10) % 10) + 0x30);
        gatSector.date[7] = (byte) ((tm.getYear() % 10) + 0x30);
    }

    //
    // TRSDOS 2.x の処理
    //
    public static class DiskBasicTypeTRSD23 extends DiskBasicTypeTRSDOS<DirectoryTrsD23> {

        /** */
        public DiskBasicTypeTRSD23(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryTrsD23> dir) {
            super(basic, fat, dir);
        }

        @Override
        public void getFromHIPosition(int pos, int[] sectorNum, int[] posInSector) {
            sectorNum[0] = (pos & 0xf) + 2;
            posInSector[0] = ((pos & 0xf0) >> 4) / 2;
        }

        /** ハッシュの格納位置を得る */
        public static int getHIPosition(int sectorNum, int posInSector) {
            // 下位4バイトがセクタ
            int pos = (sectorNum - 2);
            // 上位4バイトが位置
            pos |= ((posInSector * 2) << 4);

            return pos & 0xff;
        }

        /**
         * ハッシュを計算
         * @param name ファイル名＋拡張子 11バイト
         * @return ハッシュ値
         */
        public static byte computeHI(byte[] name) {
            int a;
            int c = 0;		// HR.
            for (int b = 0; b < 11; b++) {
                a = name[b];	// get one char
                a = (a ^ c);	// a = (HR. XOR new char)
                a <<= 1;		// a = a * 2
                c = (a & 0xff) | ((a & 0x100) >> 8);	// new HR.
            }
            if (c == 0) c = 1;	// HR don't allow zero.

            return (byte) c;
        }

        @Override
        public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryTrsD23> dirItem) throws IOException {
            boolean status = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

            List<DiskBasicDirItem<DirectoryTrsD23>> cItems = dirItem.getChildren();
            for (int i = 0; i < cItems.size(); i++) {
                DiskBasicDirItemTRSDOS<DirectoryTrsD23> cItem = (DiskBasicDirItemTRSDOS<DirectoryTrsD23>) cItems.get(i);
                int overflow = cItem.getOverflow() & 0xff;
                if (overflow > 0 && overflow < 254) {
                    cItem.visible(false);
                    int[] overflowSectorNum = new int[1];
                    int[] overflowSectorPos = new int[1];
                    getFromHIPosition(overflow, overflowSectorNum, overflowSectorPos);

                    int num = (overflowSectorNum[0] - 2) * basic.getSectorSize() / cItem.getDataSize() + overflowSectorPos[0];
                    if (num >= cItems.size()) {
                        status = false;
                        return status;
                    }
                    DiskBasicDirItemTRSDOS<DirectoryTrsD23> pItem = (DiskBasicDirItemTRSDOS<DirectoryTrsD23>) cItems.get(num);
                    pItem.setNextItem(cItem);
                }
            }

            for (DiskBasicDirItem<DirectoryTrsD23> cItem : cItems) {
                DiskBasicDirItemTRSDOS<DirectoryTrsD23> citem = (DiskBasicDirItemTRSDOS<DirectoryTrsD23>) cItem;
                citem.calcFileSize();
            }
            return status;
        }

        public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryTrsD23> item, int dataSize, int flags, DiskBasicGroups groupItems) throws IOException {
            int rc = 0;
            int sizeremain = dataSize;

            int bytesPerGroup = basic.getSectorsPerGroup() * basic.getSectorSize();
            int limit = basic.getFatEndGroup() + 1;
            int posMax = 4;
            while (rc >= 0 && limit >= 0 && sizeremain > 0) {
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = -1;
                    break;
                }
                // 位置を予約
                setGroupNumber(groupNum, 1);

                basic.getNumsFromGroup(groupNum, 0, basic.getSectorSize(), sizeremain, groupItems);

                sizeremain -= bytesPerGroup;

                limit--;
            }
            if (limit < 0) {
                // too large or infinite loop
                rc = -1;
            }

            if (rc >= 0) {
                DiskBasicDirItemTRSDOS[] tItem = {(DiskBasicDirItemTRSDOS<DirectoryTrsD23>) item};

                // 使用中にする
                tItem[0].setAsNewFile();

                // ディレクトリエントリに追加
                int[] pos = {0};
                int preGroup = 0;
                int startGroup = 0;
                int count = 0;
                int maxIndex = groupItems.size();
                for (int index = 0; index < maxIndex; index++) {
                    int group = groupItems.get(index).group;
                    if (index == 0) {
                        startGroup = group;
                    } else if (count > 32 || preGroup + 1 != group) {
                        if (pos[0] >= posMax && createOverflowEntry(tItem, pos) < 0) {
                            rc = -1;
                            break;
                        }
                        tItem[0].setGranulesOnGap(pos[0], startGroup, count);
                        count = 0;
                        startGroup = group;
                        pos[0]++;
                    }
                    count++;
                    preGroup = group;
                }
                if (rc >= 0 && count > 0) {
                    if (pos[0] >= posMax && createOverflowEntry(tItem, pos) < 0) {
                        rc = -1;
                    }
                    if (rc >= 0) {
                        tItem[0].setGranulesOnGap(pos[0], startGroup, count);
                        pos[0]++;
                    }
                }
            }

            if (rc < 0) {
                // グループを削除
                deleteGroups(groupItems);
            }

            return rc;
        }

        private int createOverflowEntry(DiskBasicDirItemTRSDOS<DirectoryTrsD23>[] ptItem, int[] pos) throws IOException {
            int rc = 0;
            DiskBasicDirItemTRSDOS<DirectoryTrsD23> newTItem = (DiskBasicDirItemTRSDOS<DirectoryTrsD23>) dir.getEmptyItemOnCurrent(ptItem[0], null);
            if (newTItem == null) {
                rc = -1;
                return rc;
            } else {
                int[] cCount = new int[1];
                int cGroup = ptItem[0].getGranulesOnGap(4, cCount);
                ptItem[0].clearGranulesOnGap(4, 0xfe, newTItem.getPositionInHIT());

                byte posInHit = ptItem[0].getPositionInHIT();
                newTItem.setAsOverflowFile(posInHit, hit.getHI(posInHit));

                pos[0] = 0;
                newTItem.setGranulesOnGap(pos[0], cGroup, cCount[0]);
                pos[0]++;

                ptItem[0].setNextItem(newTItem);

                ptItem[0] = newTItem;
            }
            return rc;
        }

        @Override
        public DiskBasicDirItem<DirectoryTrsD23> getEmptyDirectoryItem(DiskBasicDirItem<DirectoryTrsD23> parent, List<DiskBasicDirItem<DirectoryTrsD23>> items, DiskBasicDirItem<DirectoryTrsD23> pItem, DiskBasicDirItem<DirectoryTrsD23>[] nextItem) {
            boolean isSystem = ((pItem.getFileAttr().getOrigin() & FILETYPE_MASK_TRSDOS_SYSTEM) != 0);

            DiskBasicDirItem<DirectoryTrsD23> matchItem = null;
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    DiskBasicDirItem<DirectoryTrsD23> item = items.get(i);
                    if (!item.isUsed()) {
                        if ((isSystem && item.getPosition() < 0x40) || (!isSystem && item.getPosition() >= 0x40)) {
                            matchItem = item;
                            if (nextItem != null) {
                                i++;
                                if (i < items.size() && !items.get(i).isUsed()) {
                                    nextItem[0] = items.get(i);
                                } else {
                                    nextItem[0] = null;
                                }
                            }
                            break;
                        }
                    }
                }
            }
            return matchItem;
        }

        @Override
        public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
            if (!super.additionalProcessOnFormatted(data)) {
                return false;
            }

            DiskImageSector sector;

            // GAT
            int startPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + basic.getDirStartSector() - 2;
            sector = basic.getSectorFromSectorPos(startPos);
            if (sector == null) {
                return false;
            }

            // TLT no lock out
            for (int group = 0; group <= basic.getFatEndGroup(); group = group + 1) {
                tltTable.modify(group, false);
            }

            byte[] b = sector.getSectorBuffer();
            TrsDosGatSector gatSector = new TrsDosGatSector();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), gatSector);

            // ボリュームパスワード
            gatSector.password = (short) 0x4296;

            DiskBasicDirItemTRSDOS<?> tItem;

            // "BOOT/SYS"エントリを作る
            startPos += 2;
            sector = basic.getSectorFromSectorPos(startPos);
            tItem = (DiskBasicDirItemTRSDOS<?>) dir.newItem(sector, 0, sector.getSectorBuffer(0), 0);
            tItem.setAsBootSysEntry();

            // "DIR/SYS"エントリを作る
            startPos++;
            sector = basic.getSectorFromSectorPos(startPos);
            tItem = (DiskBasicDirItemTRSDOS<?>) dir.newItem(sector, 0, sector.getSectorBuffer(0), 0);
            tItem.setAsDirSysEntry();

            // セクタ 0
            sector = basic.getSectorFromSectorPos(0);
            if (sector == null) {
                // Why?
                return false;
            }
            sector.copy("\u0000\u00fe\u0011\u00f3".getBytes(), 4);
            gatTable.modify(0, true);

            return true;
        }
    }

    /**
     * TRSDOS 1.3 の処理
     */
    public static class DiskBasicTypeTRSD13 extends DiskBasicTypeTRSDOS<DirectoryTrsD13> {

        public DiskBasicTypeTRSD13(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryTrsD13> dir) {
            super(basic, fat, dir);
        }

        /** ハッシュの格納位置を得る */
        static int getHIPosition(int pos) {
            return (pos & 0xff);
        }

        @Override
        public void getFromHIPosition(int pos, int[] sectorNum, int[] posInSector) {
            int n = basic.getSectorSize() / DirectoryTrsD13.SIZE;
            sectorNum[0] = pos / n;
            posInSector[0] = (pos % n);
        }

        @Override
        public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryTrsD13> dirItem) throws IOException {
            boolean sts = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);

            // ファイルサイズを再計算
            List<DiskBasicDirItem<DirectoryTrsD13>> cItems = dirItem.getChildren();
            for (DiskBasicDirItem<DirectoryTrsD13> cItem : cItems) {
                cItem.calcFileSize();
            }
            return sts;
        }

        @Override
        public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
            if (pos[0] + DirectoryTrsD13.SIZE > size[0]) {
                return -1;
            }
            return 0;
        }

        @Override
        public int adjustPositionAssigningDirectory(int pos) {
            return 0;
        }

        public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryTrsD13> item, int dataSize, int flags, DiskBasicGroups groupItems) throws IOException {
            int rc = 0;
            int sizeRemain = dataSize;

            int bytesPerGroup = basic.getSectorsPerGroup() * basic.getSectorSize();
            int limit = basic.getFatEndGroup() + 1;
            int posMax = 13;
            while (rc >= 0 && limit >= 0 && sizeRemain > 0) {
                int groupNum = getEmptyGroupNumber();
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = -1;
                    break;
                }
                // 位置を予約
                setGroupNumber(groupNum, 1);

                basic.getNumsFromGroup(groupNum, 0, basic.getSectorSize(), sizeRemain, groupItems);

                sizeRemain -= bytesPerGroup;
                limit--;
            }
            if (limit < 0) {
                // too large or infinite loop
                rc = -1;
            }

            if (rc >= 0) {
                DiskBasicDirItemTRSDOS<DirectoryTrsD13> tItem = (DiskBasicDirItemTRSDOS<DirectoryTrsD13>) item;

                // 使用中にする
                tItem.setAsNewFile();

                // ディレクトリエントリに追加
                int pos = 0;
                int preGroup = 0;
                int startGroup = 0;
                int count = 0;
                int maxIndex = groupItems.size();
                for (int index = 0; index < maxIndex; index++) {
                    int group = groupItems.get(index).group;
                    if (index == 0) {
                        startGroup = group;
                    } else if (count > 32 || preGroup + 1 != group) {
                        if (pos >= posMax) {
                            rc = -1;
                            break;
                        }
                        tItem.setGranulesOnGap(pos, startGroup, count);
                        count = 0;
                        startGroup = group;
                        pos++;
                    }
                    count++;
                    preGroup = group;
                }
                if (rc >= 0 && count > 0) {
                    if (pos >= posMax) {
                        rc = -1;
                    }
                    if (rc >= 0) {
                        tItem.setGranulesOnGap(pos, startGroup, count);
                        pos++;
                    }
                }
            }

            if (rc < 0) {
                // グループを削除
                deleteGroups(groupItems);
            }

            return rc;
        }

        @Override
        public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
            if (!super.additionalProcessOnFormatted(data)) {
                return false;
            }

            DiskImageSector sector;

            int startPos = basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() + basic.getDirStartSector() - 2;
            sector = basic.getSectorFromSectorPos(startPos);
            if (sector == null) {
                // Why?
                return false;
            }

            byte[] b = sector.getSectorBuffer();
            TrsDosGatSector gatSector = new TrsDosGatSector();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), gatSector);

            // ボリュームパスワード
            gatSector.password = (short) 0x5cef;

            // セクタ 0
            sector = basic.getSectorFromSectorPos(0);
            if (sector == null) {
                // Why?
                return false;
            }
            sector.copy("\u00fe\u0011\u003e\u00d0".getBytes(), 4, 0);
            gatTable.modify(0, true);

            return true;
        }
    }
}
