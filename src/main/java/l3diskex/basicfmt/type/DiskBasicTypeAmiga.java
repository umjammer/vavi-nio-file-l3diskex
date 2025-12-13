///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicParam;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.AmigaBlockPre;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.AmigaFileDataPre;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.AmigaRootBlockPost;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.DirectoryAmiga;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicTemplates.diskBasicTemplates;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_DATA;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_HEADER;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.FILETYPE_MASK_AMIGA_ROOT;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.KEY_FAST_FILE_SYSTEM;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.KEY_INTERNATIONAL;
import static l3diskex.basicfmt.type.DiskBasicTypeAmiga.AmigaOneBitmap.addBitmap;


/**
 Amiga DOS の処理

 DiskBasicParam
 <li>FastFileSystem FFSかどうか(bool)</li>
 */
public class DiskBasicTypeAmiga extends DiskBasicType<DirectoryAmiga> {

    private static final Logger logger = System.getLogger(DiskBasicTypeAmiga.class.getName());

    /** Amiga Boot Block (all Big Endian) */
    @Serdes
    static class AmigaBootBlock {

        public static final int SIZE = 512;

        /** "DOS", byte4 is type: bit0 0:OFS 1:FFS */
        @Element(sequence = 1)
        byte[] type = new byte[4];
        @Element(sequence = 2)
        int checkSum;
        /** number of root block */
        @Element(sequence = 3)
        int rootBlock;
        /** boot program */
        @Element(sequence = 4)
        byte[] program = new byte[500];
    }

    /** Amiga Bitmap Block (all Big Endien) */
    @Serdes
    static class AmigaBitmapBlock {

        @Element(sequence = 1)
        int checkSum;
        /** block size - 4 */
        @Element(sequence = 2)
        int[] map;
    }

    /** AMIGA ビットマップ 1つ */
    static class AmigaOneBitmap {

        int blockNum;
        int blockSize;
        AmigaBitmapBlock map;

        /**
         * 指定位置のビットを変更する
         * @param blockNum  ブロック番号(2..)
         * @param use セットする場合true
         */
        public void modify(int blockNum, boolean use) {
            int pos = blockNum >> 5;
            int bit = blockNum & 0x1f;
            int data = (1 << bit);
            if (use) {
                map.map[pos] &= ~data;
            } else {
                map.map[pos] |= data;
            }
        }

        /**
         * 指定位置が空いているか
         * @param blockNum  ブロック番号(2..)
         * @return 空いている場合 true
         */
        public boolean isFree(int blockNum) {
            int pos = blockNum >> 5;
            int bit = blockNum & 0x1f;
            int data = (1 << bit);
            return ((map.map[pos] & data) != 0);
        }

        /**
         * 指定ブロックまですべて未使用にする
         * @param blockNum 最終ブロック番号
         */
        public void freeAll(int blockNum) {
            if (blockNum >= getNumOfBlocks()) {
                blockNum = getNumOfBlocks() - 1;
            }
            int pos = blockNum >> 5;
            int bit = blockNum & 0x1f;
            for (int p = 0; p < pos; p++) {
                map.map[p] = 0xffff_ffff;
            }
            int data = ((1 << (bit + 1)) - 1);
            map.map[pos] = data;
        }

        /** ブロック数を返す */
        public int getNumOfBlocks() {
            return (blockSize - 4) * 8;
        }

        /** チェックサムの更新 */
        public void updateCheckSum() {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Serdes.Util.serialize(map, baos);
                map.checkSum = DiskBasicTypeAmiga.calcCheckSumOnBootBlock(baos.toByteArray(), blockSize);
            } catch (Exception e) {
logger.log(Level.TRACE, e.getMessage());
            }
        }

        public int getBlockNumber() {
            return blockNum;
        }

        //
        // AmigaBitmap
        //

        /**
         * ビットマップを追加
         *
         * @param blockNum  ブロック番号
         * @param mapBuffer マップのあるバッファ
         * @param blockSize バッファサイズ
         */
        public static void addBitmap(List<AmigaOneBitmap> list, int blockNum, byte[] mapBuffer, int blockSize) throws IOException {
            AmigaBitmapBlock b = new AmigaBitmapBlock();
            Serdes.Util.deserialize(new ByteArrayInputStream(mapBuffer), mapBuffer);
            AmigaOneBitmap o = new AmigaOneBitmap();
            o.blockNum = blockNum;
            o.blockSize = blockSize;
            o.map = b;
            list.add(o);
        }

        /**
         * 指定位置のビットを変更する
         *
         * @param blockNum ブロック番号(2..)
         * @param use      セットする場合true
         */
        public static void modify(List<AmigaOneBitmap> list, int blockNum, boolean use) {
            if (blockNum < 2) return;

            blockNum -= 2;
            for (AmigaOneBitmap item : list) {
                int itemBlockNums = item.getNumOfBlocks();
                if (blockNum < itemBlockNums) {
                    item.modify(blockNum, use);
                    break;
                }
                blockNum -= itemBlockNums;
            }
        }

        /**
         * 指定位置が空いているか
         *
         * @param blockNum ブロック番号(2..)
         * @return 空いている場合 true
         */
        public static boolean isFree(List<AmigaOneBitmap> list, int blockNum) {
            if (blockNum < 2) return false;

            blockNum -= 2;
            for (AmigaOneBitmap item : list) {
                int itemBlockNums = item.getNumOfBlocks();
                if (blockNum < itemBlockNums) {
                    return item.isFree(blockNum);
                }
                blockNum -= itemBlockNums;
            }
            return false;
        }

        /**
         * 指定ブロックまですべて未使用にする
         *
         * @param blockNum 最終ブロック番号
         */
        public static void freeAll(List<AmigaOneBitmap> list, int blockNum) {
            if (blockNum < 2) return;

            blockNum -= 2;
            for (AmigaOneBitmap item : list) {
                int itemBlockNums = item.getNumOfBlocks();
                if (blockNum < itemBlockNums) {
                    item.freeAll(blockNum);
                } else {
                    item.freeAll(0xffff_ffff);
                }
                blockNum -= itemBlockNums;
            }
        }

        /** ブロック数を返す */
        public static int getNumOfBlocks(List<AmigaOneBitmap> list) {
            int blockNums = 0;
            for (AmigaOneBitmap item : list) {
                blockNums += item.getNumOfBlocks();
            }
            return blockNums;
        }

        /** チェックサムの更新 */
        public static void updateCheckSum(List<AmigaOneBitmap> list) {
            for (AmigaOneBitmap item : list) {
                item.updateCheckSum();
            }
        }
    }

    //
    // Amiga DOS の処理
    //

    /** Root Block */
    private final DirectoryAmiga root = new DirectoryAmiga();
    /** Bitmap Blocks */
    private final List<AmigaOneBitmap> bitmap = new ArrayList<>();

    public static final int FORMAT_TYPE_AMIGA = 21;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_AMIGA;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryAmiga> dir) {
        super.init(basic, fat, dir);

        root.blockNum = 0;
        root.pre = null;
        root.post = null;
    }

    @Override
    public void setGroupNumber(int num, int val) {
        AmigaOneBitmap.modify(bitmap, num, val != 0);
    }

    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    @Override
    public boolean isUsedGroupNumber(int num) {
        return !AmigaOneBitmap.isFree(bitmap, num);
    }

    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        return INVALID_GROUP_NUMBER;
    }

    @Override
    public int getEmptyGroupNumber() {
        return getEmptyGroupNumberM();
    }

    @Override
    public int getNextEmptyGroupNumber(int currentGroup) {
        int nextGroupNum = getEmptyGroupNumber();
        if (nextGroupNum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }

        if (chainGroups(currentGroup, nextGroupNum) < 0) {
            return INVALID_GROUP_NUMBER;
        }

        return nextGroupNum;
    }

    // check / assign FAT area
    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = 1.0;
        return validRatio;
    }

    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0;

        double validRatio = 1.0;

        //
        // boot block
        //
        DiskImageSector sector = basic.getSectorFromGroup(0);
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer();
        if (b == null) {
            return -1.0;
        }
        AmigaBootBlock bb = new AmigaBootBlock();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), bb);

        // チェック
        if ((!Arrays.equals(bb.type, 0, 3, "DOS".getBytes(), 0, 3)) &&
                (!Arrays.equals(bb.type, 0, 4, "KICK".getBytes(), 0, 4))) {
            return -1.0;
        }
        // Fast File System か
        boolean diskIsFast = (bb.type[3] != 'K' && (bb.type[3] & 1) != 0);
        boolean paramIsFast = basic.getVariousBoolParam(KEY_FAST_FILE_SYSTEM);
        if (diskIsFast != paramIsFast) {
            return -1.0;
        }
        basic.setVariousParam(KEY_INTERNATIONAL, (bb.type[3] & 6) == 2 || (bb.type[3] & 6) == 4);

        //
        // root block
        //
        root.blockNum = bb.rootBlock;
        if (root.blockNum < 2 || root.blockNum > basic.getFatEndGroup()) {
            // ブート領域かディスクをオーバしている
            if (validRatio >= 0.0) validRatio *= 0.8;
            root.blockNum = basic.getManagedTrackNumber() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() + basic.getDirStartSector() - 1;
        }
        int[] rootTrack = new int[1];
        int[] rootSide = new int[1];
        sector = basic.getSectorFromGroup(root.blockNum, rootTrack, rootSide);
        if (sector == null) {
            return -1.0;
        }
        b = sector.getSectorBuffer();
        if (b == null) {
            return -1.0;
        }
        root.pre = new AmigaBlockPre();
        Serdes.Util.deserialize(new ByteArrayInputStream(b, 0, b.length), root.pre);
        // hash_tableはセクタサイズ（ブロックサイズ）で可変
        int offset = basic.getSectorSize() - AmigaRootBlockPost.SIZE;
        if (offset < 0) {
            return -1.0;
        }
        b = sector.getSectorBuffer(offset);
        if (b == null) {
            return -1.0;
        }
        root.post = new AmigaRootBlockPost();
        Serdes.Util.deserialize(new ByteArrayInputStream(b, offset, b.length - offset), root.post);
        basic.setManagedTrackNumber(rootTrack[0]);
        basic.setDirStartSector(sector.getSectorNumber());

        //
        // ビットマップ
        //
        if (root.post.u.r.bmFlag == -1) {
            for (int i = 0; i < 25; i++) {
                int num = root.post.u.r.bmPages[i];
                if (num < 2) {
                    continue;
                }
                sector = basic.getSectorFromGroup(num);
                if (sector == null) {
                    // Why?
                    validRatio = 0.2;
                    break;
                }
                addBitmap(bitmap, num, sector.getSectorBuffer(), sector.getSectorSize());
            }
        } else {
            validRatio = 0.2;
        }
        basic.setSectorsPerFat(bitmap.size());
        basic.setFatEndGroup(basic.getSidesPerDiskOnBasic() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);

        //
        // ディレクトリエリア
        //

        // root block の hash table を追跡
        int maxHashtable = root.pre.tableSize;
        int calcMaxHashtable = (basic.getSectorSize() - AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;
        if (maxHashtable > calcMaxHashtable) {
            validRatio = -1.0;
            maxHashtable = calcMaxHashtable;
        }

        for (int hashtable = 0; hashtable < maxHashtable; hashtable++) {
            int num = root.pre.u.table[hashtable];
            if (num > 0 && num < 2) {
                // boot領域にある？
                if (validRatio > 0.0) validRatio *= 0.5;
            } else if (num > basic.getFatEndGroup()) {
                // 範囲外
                if (validRatio > 0.0) validRatio *= 0.5;
            }
        }

        return validRatio;
    }

    @Override
    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        if (!bitmap.isEmpty()) {
            AmigaOneBitmap item = bitmap.getFirst();
            getNumFromSectorPos(item.getBlockNumber(), trackNum, sideNum, sectorNum);
        }
    }

    @Override
    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        if (!bitmap.isEmpty()) {
            AmigaOneBitmap item = bitmap.getLast();
            getNumFromSectorPos(item.getBlockNumber(), trackNum, sideNum, sectorNum);
        }
    }

    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    // check / assign directory area
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        groupItems.clear();

        if (root.pre == null) {
            return false;
        }
        boolean valid = true;

        int limit = basic.getFatEndGroup() + 1;
        int maxBlocks = root.pre.tableSize;

        valid = DiskBasicDirItemAmiga.getDirectoryGroups(basic, root.pre.u.table, maxBlocks, limit, groupItems);

        return valid;
    }

    @Override
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryAmiga> dirItem) throws IOException {
        boolean sts = super.assignRootDirectory(startSector, endSector, groupItems, dirItem);
        if (dirItem != null) {
            int[] track = new int[1];
            int[] side = new int[1];
            DiskImageSector sector = basic.getSectorFromGroup(root.blockNum, track, side);
            dirItem.setData(0, null, sector, 0, sector.getSectorBuffer(), 0, null);
        }
        DiskBasicDirItemAmiga.renumberInDirectory(basic, dirItem.getChildren());
        return sts;
    }

    @Override
    public boolean assignDirectory(boolean isRoot, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryAmiga> dirItem) throws IOException {
        boolean status = super.assignDirectory(isRoot, groupItems, dirItem);
        DiskBasicDirItemAmiga.renumberInDirectory(basic, dirItem.getChildren());
        return status;
    }

    @Override
    public int initializeSectorsAsDirectory(DiskBasicGroups groupItems, int[] fileSize, int[] sizeRemain, DiskBasicError errInfo) {
        sizeRemain[0] = 0;
        return 0;
    }

    @Override
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        if (pos[0] > 0) {
            sizeRemain[0] += size[0];
            return -1;
        }
        return 0;
    }

    // disk size
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() - 1;
        groupSize[0] -= bitmap.size();
        diskSize[0] = groupSize[0] * basic.getSectorSize();
    }

    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.clear();

        int blockSize = basic.getSectorSize();
        if (!basic.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            blockSize -= AmigaFileDataPre.SIZE - 1;
        }

        // BITMAP table
        for (int num = 0; num <= basic.getFatEndGroup(); num++) {
            if (num < 2) {
                fatAvailability.add(FAT_AVAIL_SYSTEM, 0, 0);
            } else if (AmigaOneBitmap.isFree(bitmap, num)) {
                fatAvailability.add(FAT_AVAIL_FREE, blockSize, 1);
            } else {
                fatAvailability.add(FAT_AVAIL_USED, 0, 0);
            }
        }

        fatAvailability.set(root.blockNum, FAT_AVAIL_SYSTEM);

        for (AmigaOneBitmap item : bitmap) {
            fatAvailability.set(item.getBlockNumber(), FAT_AVAIL_SYSTEM);
        }
    }

    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryAmiga> pItem, DiskBasicDirItem<DirectoryAmiga> nItem, DiskBasicError errInfo) {
        return true;
    }

    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryAmiga> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int groups = 0;

        int rc = 0;

        // ディレクトリ新規作成の時は何もしない
        if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW && item.isDirectory()) {
            return 0;
        }

        int blockSize = basic.getSectorSize();
        if (!basic.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            // OFSなら24バイト減らす
            blockSize -= AmigaFileDataPre.SIZE - 1;
        }
        int remain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        int groupNum = INVALID_GROUP_NUMBER;
        int prevGroupNum = 0;
        int prevRemain = 0;

        DiskBasicDirItemAmiga aItem = (DiskBasicDirItemAmiga) item;
        int blockNums = aItem.getNumOfDataBlocks();
        int blockIndex = blockNums - 1;
        int extension = -1;
        int headerBlockNum = aItem.getStartGroup(fileUnitNum);

        while (remain > 0 && limit >= 0 && rc >= 0) {
            if (blockIndex < 0) {
                // データテーブルがいっぱいになったので
                // extensionブロックを新たに確保する
                int extentionNum = getEmptyGroupNumber();
                if (extentionNum == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = -2;
                    break;
                }
                DiskImageSector sector = basic.getSectorFromGroup(extentionNum);
                if (sector == null) {
                    rc = -2;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    rc = -2;
                    break;
                }
                // 使用済みにする
                setGroupNumber(extentionNum, 1);

                // extensionブロックへのリンクを作成
                aItem.setExtension(extentionNum);
                aItem.setHighSeq(blockNums);

                // aitem切替
                aItem = (DiskBasicDirItemAmiga) dir.newItem(sector, extension, buffer, 0);

                sector.fill((byte) 0);
                aItem.initForExtensionBlock(headerBlockNum);
                blockNums = aItem.getNumOfDataBlocks();
                blockIndex = blockNums - 1;

                extension++;
            }

            // 空きをさがす
            groupNum = getEmptyGroupNumber();
            if (groupNum == INVALID_GROUP_NUMBER) {
                rc = groups > 0 ? -2 : -1;
                break;
            }

            // 使用済みにする
            if (prevGroupNum > 0) {
                basic.getNumsFromGroup(prevGroupNum, groupNum, basic.getSectorSize(), prevRemain, groupItems[0]);
            }
            prevGroupNum = groupNum;
            prevRemain = remain;

            setGroupNumber(groupNum, 1);
            aItem.setDataBlock(blockIndex, groupNum);

            groups++;
            remain -= blockSize;
            limit--;

            blockIndex--;
        }

        if (prevGroupNum > 0) {
            basic.getNumsFromGroup(prevGroupNum, 0, basic.getSectorSize(), prevRemain, groupItems[0]);
        }

        aItem.setHighSeq(blockNums - blockIndex - 1);

        if (limit < 0) {
            // 無限ループ？
            rc = groups > 0 ? -2 : -1;
        }

        return rc;
    }

    @Override
    public int chainGroups(int groupNum, int appendGroupNum) {
        return 0;
    }

    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return groupNum;
    }

    @Override
    public DiskBasicDirItem<DirectoryAmiga> getEmptyDirectoryItem(
            DiskBasicDirItem<DirectoryAmiga> parent, List<DiskBasicDirItem<DirectoryAmiga>> items,
            DiskBasicDirItem<DirectoryAmiga> pItem, DiskBasicDirItem<DirectoryAmiga>[] nextItem) throws IOException {

        DiskBasicDirItem<DirectoryAmiga> matchItem = null;
        byte[] name = new byte[32];
        int[] len = {name.length};
        int[] eLen = new int[1];

        // ファイル名からハッシュ番号を算出し、ハッシュテーブルに関連付ける
        if (parent != null && pItem != null) {
            if (!parent.getFileAttr().isDirectory()) {
                return matchItem;
            }

            // ファイル名を得る
            pItem.getNativeFileName(name, len, null, eLen);
            // ハッシュ番号を計算
            int hash = createHashNumberFromName(name, len[0]);

            // 新しいセクタを確保
            int new_num = getEmptyGroupNumber();
            if (new_num == INVALID_GROUP_NUMBER) {
                // 空きなし
                return matchItem;
            }

            // 実際にセクタがあるか
            DiskImageSector sector = basic.getSectorFromGroup(new_num);
            if (sector == null) {
                return matchItem;
            }
            byte[] buffer = sector.getSectorBuffer();
            if (buffer == null) {
                return matchItem;
            }

            // ヘッダ情報をセット
            DiskBasicDirItemAmiga apitem = (DiskBasicDirItemAmiga) pItem;
            apitem.setStartGroup(0, new_num);
            apitem.initForHeaderBlock(parent.getStartGroup(0));

            // アイテムを新規作成
            matchItem = dir.newItem(sector, 0, buffer, 0);

            // セクタにヘッダ情報をセット
            sector.fill((byte) 0);

            // セクタ使用中にする
            setGroupNumber(new_num, 1);

            // 親ディレクトリをセット
            matchItem.setParent(parent);

            // ハッシュ番号を登録
            DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;
            aparent.chainHashNumber(hash, new_num, matchItem);

            // ディレクトリリストに追加する
            int limit = basic.getFatEndGroup() + 1;
            int[] tables = aparent.getBlockTable();
            int nums = aparent.getNumOfDataBlocks();

            if (items == null) {
                parent.createChildren();
                items = parent.getChildren();
            }
            DiskBasicDirItemAmiga.insertItemInDirectory(basic, tables, nums, limit, items, matchItem);

            return matchItem;
        }
        return matchItem;
    }

    // directory
    @Override
    public boolean isRootDirectory(int groupNum) {
        return groupNum == root.blockNum;
    }

    @Override
    public boolean canMakeDirectory() {
        return true;
    }

    @Override
    public void additionalProcessOnMadeDirectory(
            DiskBasicDirItem<DirectoryAmiga> item, DiskBasicGroups groupItems, DiskBasicDirItem<DirectoryAmiga> parentItem) {

        // ビットマップのチェックサムを更新する
        AmigaOneBitmap.updateCheckSum(bitmap);

        // 日時
        LocalDateTime tm = LocalDateTime.now();
        item.setFileModifyDateTime(tm);

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        // 親ディレクトリの日時を更新する（ルートディレクトリの場合も含む）
        aparent.setFileModifyDateTime(tm);

        // ルートディレクトリのボリューム日時を更新する
        setVolumeDateTime(tm);

        // 親ディレクトリのチェックサムを更新する
        aparent.updateCheckSum();

        // ルートディレクトリのチェックサムを更新する
        if (aparent.getParent() != null) updateCheckSumOnRoot();
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        boolean isFfs = basic.getVariousBoolParam(KEY_FAST_FILE_SYSTEM);
        boolean isI18n = false;
        boolean isDirectory = false;

        int trackNum;
        int sectorNum;

        //
        // Boot Block を作成
        //
        int block = 0;
        sector = basic.getSectorFromGroup(block);
        sector.fill((byte) 0);
        byte[] b = sector.getSectorBuffer();
        AmigaBootBlock boot = new AmigaBootBlock();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), boot);
        boot.type[0] = (byte) 'D';
        boot.type[1] = (byte) 'O';
        boot.type[2] = (byte) 'S';
        if (isFfs) {
            boot.type[3] |= 1;
        }
        if (isI18n) {
            boot.type[3] |= 2;
            if (isDirectory) boot.type[3] += 2;
        }

        DiskBasicParam defaultParam = diskBasicTemplates.findType("", basic.getBasicTypeName());
        trackNum = defaultParam.getManagedTrackNumber();
        sectorNum = defaultParam.getDirStartSector();
        basic.setManagedTrackNumber(trackNum);
        basic.setDirStartSector(sectorNum);

        int rootBlock = getSectorPosFromNumS(trackNum, sectorNum);
        boot.rootBlock = rootBlock;

        //
        // bitmapブロックを確保
        //
        basic.setFatEndGroup(basic.getSidesPerDiskOnBasic() * basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);
        bitmap.clear();
        block = rootBlock;
        do {
            block++;
            sector = basic.getSectorFromGroup(block);
            addBitmap(bitmap, block, sector.getSectorBuffer(), sector.getSectorSize());
        } while (AmigaOneBitmap.getNumOfBlocks(bitmap) < basic.getFatEndGroup() + 1);

        AmigaOneBitmap.freeAll(bitmap, basic.getFatEndGroup());

        basic.setSectorsPerFat(bitmap.size());

        //
        // Root Block を作成
        //
        sector = basic.getSectorFromGroup(rootBlock);
        sector.fill((byte) 0);
        root.blockNum = rootBlock;
        byte[] rootBuffer = sector.getSectorBuffer();
        root.pre = new AmigaBlockPre();
        Serdes.Util.deserialize(new ByteArrayInputStream(rootBuffer), root.pre);
        root.pre.type = FILETYPE_MASK_AMIGA_HEADER;

        int val = (sector.getSectorSize() - AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;
        root.pre.tableSize = val;

        root.post = new AmigaRootBlockPost();
        Serdes.Util.deserialize(new ByteArrayInputStream(rootBuffer, AmigaBlockPre.SIZE + val * 4 - 4, rootBuffer.length - (AmigaBlockPre.SIZE + val * 4 - 4)), root.post);
        root.post.u.r.bmFlag = -1;
        for (int i = 0; i < bitmap.size(); i++) {
            val = bitmap.get(i).getBlockNumber();
            root.post.u.r.bmPages[i] = val;
            AmigaOneBitmap.modify(bitmap, val, true);
        }

        root.post.u.r.secType = FILETYPE_MASK_AMIGA_ROOT;

        if (isFfs && isDirectory) {
            // block number to first directory cache block
            root.post.u.r.extension = 0;
        }

        // 日時
        LocalDateTime tm = LocalDateTime.now();
        setCreateDateTime(tm);
        setVolumeDateTime(tm);
        setModifyDateTime(tm);

        // volume name
        setIdentifiedData(data);

        // bitmapをセット
        AmigaOneBitmap.modify(bitmap, rootBlock, true);

        // チェックサムを計算
        AmigaOneBitmap.updateCheckSum(bitmap);
        root.pre.checkSum = calcCheckSumOnBootBlock(rootBuffer, basic.getSectorSize());

        return true;
    }

    /**
     * チェックサムを計算
     *
     * @param data ブロックデータ
     * @param size ブロックサイズ
     */
    static int calcCheckSum(byte[] data, int size) {
        IntBuffer dp = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int newsum = 0;
        size >>= 2;
        for(int i=0; i<size; i++) {
            newsum += dp.get(i);
        }
        newsum = ~newsum;
        newsum++;

        return newsum;
    }

    /**
     * チェックサムを計算
     *
     * @param data ブロックデータ
     * @param size ブロックサイズ
     */
    public static int calcCheckSumOnBootBlock(byte[] data, int size) {
        return calcCheckSum(data, size);
    }

    /**
     * データの読み込み/比較処理
     *
     * @param fileUnitNum  ファイル番号
     * @param item         ディレクトリアイテム
     * @param iStream      [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時は {@code null}
     * @param oStream      [in,out] 出力先 データ読み出し時に使用 ベリファイ時は {@code null}
     * @param sectorBuffer セクタバッファ
     * @param sectorSize   バッファサイズ
     * @param remainSize   残りサイズ
     * @param sectorNum    セクタ番号
     * @param sectorEnd    最終セクタ番号
     * @return >=0: 処理したサイズ, -1: 比較不一致
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryAmiga> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        int bufferOffset = 0;
        int size = sectorSize;

        if (!basic.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            // OFSなら24バイト減らす
            size -= AmigaFileDataPre.SIZE - 1;
            bufferOffset += AmigaFileDataPre.SIZE - 1;
        }
        if (remainSize < size) {
            size = remainSize;
        }

        byte[] temp;
        if (oStream != null) {
            // 書き出し
            temp = Arrays.copyOfRange(sectorBuffer, bufferOffset, bufferOffset + size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);
            oStream.write(temp, 0, temp.length);
        }
        if (iStream != null) {
            // 読み込んで比較
            temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, size, sectorBuffer, bufferOffset, bufferOffset + size)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryAmiga> item, InputStream iStream, OutputStream oStream, byte[] sector_buffer, int sectorOffset, int sector_size, int remain_size) {
        return remain_size;
    }

    @Override
    public boolean isEnoughFileSize(int size) {
        int blockSize = basic.getSectorSize();
        if (!basic.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            // OFSなら24バイト減らす
            blockSize -= AmigaFileDataPre.SIZE - 1;
        }

        int tableCount = (basic.getSectorSize() -
                AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;

        int dataCount = (size + blockSize - 1) / blockSize;
        int headerCount = 1 + dataCount / tableCount;

        return getFreeGroupSize() >= (headerCount + dataCount);
    }

    @Override
    public int writeFile(DiskBasicDirItem<DirectoryAmiga> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;
        AmigaFileDataPre pre = null;
        int bufferOffset = 0;

        if (!basic.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            // OFSなら24バイト減らす
            pre = new AmigaFileDataPre();
            Serdes.Util.deserialize(new ByteArrayInputStream(buffer), pre);
            size -= AmigaFileDataPre.SIZE - 1;
            bufferOffset += AmigaFileDataPre.SIZE - 1;
        }

        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) iStream.readNBytes(buffer, bufferOffset, remain);
            if (size > remain) {
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            iStream.readNBytes(buffer, bufferOffset, size);
            len = size;
        }

        if (pre != null) {
            // OFSの場合、パラメータをセット
            DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
            int val = FILETYPE_MASK_AMIGA_DATA;
            pre.o.type = val;
            val = aitem.getStartGroup(0);
            pre.o.headerKey = val;
            val = seqNum + 1;
            pre.o.seqNum = val;
            val = remain > size ? size : remain;
            pre.o.dataSize = val;
            pre.o.nextData = nextGroup;
        }

        return len;
    }

    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryAmiga> item) {
        // ビットマップのチェックサムを更新する
        AmigaOneBitmap.updateCheckSum(bitmap);

        DiskBasicDirItemAmiga aItem = (DiskBasicDirItemAmiga) item;
        aItem.updateCheckSumAll();

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aParent = (DiskBasicDirItemAmiga) parent;

        // 親ディレクトリの日時を更新する（ルートディレクトリの場合も含む）
        LocalDateTime tm = LocalDateTime.now();
        aParent.setFileModifyDateTime(tm);

        // ルートディレクトリのボリューム日時を更新する
        setVolumeDateTime(tm);

        // 親ディレクトリのチェックサムを更新する
        aParent.updateCheckSum();

        // ルートディレクトリのチェックサムを更新する
        if (aParent.getParent() != null) updateCheckSumOnRoot();
    }

    @Override
    public void additionalProcessOnRenamedFile(DiskBasicDirItem<DirectoryAmiga> item) throws IOException {
        byte[] name = new byte[32];
        int[] len = {name.length};
        int[] eLen = new int[1];

        // ファイル名を得る
        item.getNativeFileName(name, len, null, eLen);
        // ハッシュ番号を計算
        int hashNum = createHashNumberFromName(name, len[0]);

        DiskBasicDirItemAmiga aItem = (DiskBasicDirItemAmiga) item;
        if (hashNum == aItem.getHashNumber()) {
            // ハッシュ番号が同じなら変更なし
            return;
        }

        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return;

        DiskBasicDirItemAmiga aParent = (DiskBasicDirItemAmiga) parent;

        int limit = basic.getFatEndGroup() + 1;
        int[] tables = aParent.getBlockTable();
        int tableSize = aParent.getNumOfDataBlocks();

        // ディレクトリリストから削除する
        DiskBasicDirItemAmiga.deleteItemInDirectory(basic, tables, tableSize, limit, parent.getChildren(), item);

        // ハッシュ番号を登録
        int blockNum = item.getStartGroup(0);
        aParent.chainHashNumber(hashNum, blockNum, item);

        // ディレクトリリストに追加する
        DiskBasicDirItemAmiga.insertItemInDirectory(basic, tables, tableSize, limit, parent.getChildren(), item);
    }

    @Override
    public void deleteGroupNumber(int groupNum) {
        // 未使用にする
        setGroupNumber(groupNum, 0);
    }

    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryAmiga> item) {
        // ビットマップのチェックサムを更新する
        AmigaOneBitmap.updateCheckSum(bitmap);

        // ディレクトリリストから削除する
        DiskBasicDirItem<DirectoryAmiga> parent = item.getParent();
        if (parent == null) return false;

        DiskBasicDirItemAmiga aparent = (DiskBasicDirItemAmiga) parent;

        int limit = basic.getFatEndGroup() + 1;
        int[] tables = aparent.getBlockTable();
        int tableSize = aparent.getNumOfDataBlocks();

        DiskBasicDirItemAmiga.deleteItemInDirectory(basic, tables, tableSize, limit, parent.getChildren(), item);

        // 親ディレクトリの日時を更新する（ルートディレクトリの場合も含む）
        LocalDateTime tm = LocalDateTime.now();
        aparent.setFileModifyDateTime(tm);

        // ルートディレクトリのボリューム日時を更新する
        setVolumeDateTime(tm);

        // 親ディレクトリのチェックサムを更新する
        aparent.updateCheckSum();

        // ルートディレクトリのチェックサムを更新する
        if (aparent.getParent() != null) updateCheckSumOnRoot();

        return true;
    }

    @Override
    public void releaseDirectoryItem(DiskBasicDirItem<DirectoryAmiga> item) {
        super.releaseDirectoryItem(item);
    }

    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        if (root.post == null) return;

        // volume name in root
        byte[] name = new byte[root.post.u.r.diskName.length + 1];
        int len = root.post.u.r.diskNameLen & 0xff;
        System.arraycopy(root.post.u.r.diskName, 0, name, 0, len);

        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(name, 0, len, sb, -1);
        String wName = sb.toString();
        data.setVolumeName(wName);
        data.setVolumeNameMaxLength(name.length);

        // volume date in root
        LocalDateTime tm = DiskBasicDirItemAmiga.convDateToTm(root.post.u.r.cDays).atTime(
                DiskBasicDirItemAmiga.convTimeToTm(root.post.u.r.cMins, root.post.u.r.cTicks));
        String datetime = Utils.formatYMDStr(tm.toLocalDate());
        datetime += " ";
        datetime += Utils.formatHMSStr(tm.toLocalTime());
        data.setVolumeDate(datetime);
    }

    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        if (root.post == null) return;

        DiskBasicFormat format = basic.getFormatType();

        if (format.hasVolumeName()) {
            byte[] name = new byte[root.post.u.r.diskName.length + 1];
            basic.getCharCodes().convToChars(data.getVolumeName(), name, name.length);
            System.arraycopy(name, 0, root.post.u.r.diskName, 0, name.length + 1);
            root.post.u.r.diskNameLen = (byte) (name.length & 0xff);
        }
    }

    /** ルートのチェックサムを計算 */
    public void updateCheckSumOnRoot() {
        DiskBasicDirItem<DirectoryAmiga> aRoot = dir.getRootItem();
        ((DiskBasicDirItemAmiga) aRoot).updateCheckSum();
    }

    /**
     * ルートの更新日時をセット
     *
     * @param tm 日時
     */
    public void setModifyDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = root.post.u.r;
        r.rDays = days[0];
        r.rMins = mins[0];
        r.rTicks = ticks[0];
    }

    /**
     * ルートのボリューム日時をセット
     *
     * @param tm 日時
     */
    public void setVolumeDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = root.post.u.r;
        r.vDays = days[0];
        r.vMins = mins[0];
        r.vTicks = ticks[0];
    }

    /**
     * ルートの作成日時をセット
     *
     * @param tm 日時
     */
    public void setCreateDateTime(LocalDateTime tm) {
        int[] days = new int[1];
        int[] mins = new int[1];
        int[] ticks = new int[1];
        DiskBasicDirItemAmiga.convDateFromTm(tm.toLocalDate(), days);
        DiskBasicDirItemAmiga.convTimeFromTm(tm, mins, ticks);

        AmigaRootBlockPost r = root.post.u.r;
        r.cDays = days[0];
        r.cMins = mins[0];
        r.cTicks = ticks[0];
    }

    /**
     * 空き位置を返す
     *
     * @return INVALID_GROUP_NUMBER: 空きなし
     */
    private int getEmptyGroupNumberM() {
        int newNum = INVALID_GROUP_NUMBER;
        int numOfSectors = basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        int startTrack = 0;
        int endTrack = 0;
        int direction = 1;

        for (int i = 0; i < 2; i++) {
            switch (i) {
                case 0:
                    // 外側へ検索
                    startTrack = basic.getManagedTrackNumber();
                    endTrack = basic.getTracksPerSideOnBasic() + basic.getTrackNumberBaseOnDisk();
                    direction = 1;
                    break;
                case 1:
                    // 内側へ検索
                    startTrack = basic.getManagedTrackNumber() - 1;
                    endTrack = basic.getTrackNumberBaseOnDisk() - 1;
                    direction = -1;
                    break;
            }

            for (int trackNum = startTrack; trackNum != endTrack && newNum == INVALID_GROUP_NUMBER; trackNum += direction) {
                for (int sectorNum = 0; sectorNum < numOfSectors; sectorNum++) {
                    int num = getSectorPosFromNumS(trackNum, sectorNum + basic.getSectorNumberBase());
                    if (AmigaOneBitmap.isFree(bitmap, num)) {
                        newNum = num;
                        break;
                    }
                }
            }
        }
        return newNum;
    }

    /**
     * ファイル名からハッシュ番号を生成する
     *
     * @param name [in,out] ファイル名
     * @param size サイズ
     * @return ハッシュ番号
     */
    private int createHashNumberFromName(byte[] name, int size) {
        int hash;
        int bSize = (basic.getSectorSize() - AmigaBlockPre.SIZE - AmigaRootBlockPost.SIZE + 4) / 4;
        boolean isI18n = basic.getVariousBoolParam(KEY_INTERNATIONAL);

        int l = hash = Common.getStringLength(name, size, (byte) 0);
        for (int i = 0; i < l; i++) {
            hash *= 13;
            hash += DiskBasicDirItemAmiga.upper(name[i], isI18n);
            hash &= 0x7ff;
        }
        hash %= bSize;

        return hash;
    }
}
