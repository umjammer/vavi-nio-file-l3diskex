///
/// @author Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l3diskex.ResultInfo;
import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasic;
import vavi.io.SeekableDataInputStream;

import static l3diskex.basicfmt.DiskBasic.clearParseAndAssign;
import static l3diskex.diskimg.DiskParam.gDiskTemplates;


/** ディスクイメージ入出力 */
public abstract class DiskImage {

    private static final Logger logger = System.getLogger(DiskImage.class.getName());

    /** ハッシュを扱うクラス */
    public static class IntHashMapUtil {

        public static void increaseValue(Map<Integer, Integer> hashMap, int key) {
            hashMap.put(key, hashMap.getOrDefault(key, 0) + 1);
        }

        public static int getMaxKeyOnMaxValue(Map<Integer, Integer> hashMap) {
            int keyResult = 0;
            int key2 = 0;
            int val = 0;
            for (Map.Entry<Integer, Integer> entry : hashMap.entrySet()) {
                if (val < entry.getValue()) {
                    val = entry.getValue();
                    keyResult = entry.getKey();
                } else if (val == entry.getValue()) {
                    key2 = entry.getKey();
                    if (keyResult < key2) {
                        keyResult = key2;
                    }
                }
            }
            return keyResult;
        }

        public static int getKeyCount(Map<Integer, Integer> hashMap) {
            return hashMap.size();
        }

        public static int maxValue(int src, int value) {
            return (src > value ? src : value);
        }

        public static int minValue(int src, int value) {
            return (src < value ? src : value);
        }
    }

    /** セクタデータへのヘッダ部分を渡すクラス */
    public static abstract class DiskImageSectorHeader {

        public DiskImageSectorHeader() {
        }

        public abstract int getHeaderType();
    }

    /** セクタデータへのポインタを保持するクラス */
    public static abstract class DiskImageSector {

        /** sector number(ID Rと同じ) */
        protected int num;

        public DiskImageSector(int num) {
            this.num = num;
        }

        /** セクタのデータを置き換える */
        public boolean replace(DiskImageSector srcSector) {
            return false;
        }

        public boolean fill(byte code) {
            return fill(code, -1, 0);
        }

        /** セクタのデータを埋める */
        public boolean fill(byte code, int len /* = -1 */, int start /* = 0 */) {
            return false;
        }

        public boolean copy(byte[] buf, int len) {
            return copy(buf, len, 0);
        }

        /** セクタのデータを上書き */
        public boolean copy(byte[] buf, int len, int start) {
            return false;
        }

        /** セクタのデータに指定したバイト列があるか */
        public int find(byte[] buf, int len) {
            return -1;
        }

        /** 指定位置のセクタデータを返す */
        public byte get(int pos) {
            return 0;
        }

        public short get16(int pos) {
            return get16(pos, false);
        }

        /** 指定位置のセクタデータを返す */
        public short get16(int pos, boolean bigEndian) {
            return 0;
        }

        public int modifySectorSize(int size) {
            return 0;
        }

        /** セクタ番号を返す(ID Rと同じ) */
        public int getSectorNumber() {
            return num;
        }

        /** セクタ番号を設定 */
        public void setSectorNumber(int val) {
            num = val;
        }

        /** 削除マークがついているか */
        public boolean isDeleted() {
            return false;
        }

        /** 削除マークの設定 */
        public void setDeletedMark(boolean val) {
        }

        /** 同じセクタか */
        public boolean isSameSector(int sectorNumber, int density, boolean deletedMark) {
            return false;
        }

        /** ヘッダサイズを返す */
        public int getHeaderSize() {
            return 0;
        }

        /** セクタサイズを返す */
        public int getSectorSize() {
            return 0;
        }

        /** セクタサイズを設定 */
        public void setSectorSize(int val) {
        }

        /** セクタサイズ（バッファのサイズ）を返す */
        public abstract int getSectorBufferSize();

        /** セクタサイズ（ヘッダ＋バッファのサイズ）を返す */
        public int getSize() {
            return 0;
        }

        /** セクタデータへのポインタを返す */
        public abstract byte[] getSectorBuffer();

        /** セクタデータへのポインタを返す */
        public byte[] getSectorBuffer(int offset) {
            return null;
        }

        /** for write back */
        public void setSectorBuffer(byte[] data, int ofs, int len) {
        }

        /** セクタ数を返す */
        public short getSectorsPerTrack() {
            return 0;
        }

        /** セクタ数を設定 */
        public void setSectorsPerTrack(short val) {
        }

        /** セクタのステータスを返す */
        public byte getSectorStatus() {
            return 0;
        }

        /** セクタのステータスを設定 */
        public void setSectorStatus(byte val) {
        }

        /** ヘッダを返す */
        public DiskImageSectorHeader getHeader() {
            return null;
        }

        /** ID Cを返す */
        public byte getIDC() {
            return 0;
        }

        /** ID Hを返す */
        public byte getIDH() {
            return 0;
        }

        /** ID Rを返す */
        public byte getIDR() {
            return 0;
        }

        /** ID Nを返す */
        public byte getIDN() {
            return 0;
        }

        /** ID Cを設定 */
        public void setIDC(byte val) {
        }

        /** ID Hを設定 */
        public void setIDH(byte val) {
        }

        /** ID Rを設定 */
        public void setIDR(byte val) {
        }

        /** ID Nを設定 */
        public void setIDN(byte val) {
        }

        /** 単密度か */
        public boolean isSingleDensity() {
            return false;
        }

        /** 単密度かを設定 */
        public void setSingleDensity(boolean val) {
        }

        /** 変更されているか */
        public boolean isModified() {
            return false;
        }

        /** 変更済みを設定 */
        public void setModify() {
        }

        /** 変更済みをクリア */
        public void clearModify() {
        }

        /** セクタ内容の比較 */
        public static int compare(DiskImageSector item1, DiskImageSector item2) {
            return item1.num - item2.num;
        }

        /** セクタ番号の比較 */
        public static int compareIDR(DiskImageSector item1, DiskImageSector item2) {
            return item1.getIDR() - item2.getIDR();
        }

        /** ID Nからセクタサイズを計算 */
        public static int convIDNToSecSize(byte n) {
            int sec = 0;
            if (n <= 3) sec = gSectorSizes[n];
            return sec;
        }

        /** セクタサイズからID Nを計算 */
        public static byte convSecSizeToIDN(int size) {
            byte n = 1;
            for (int i = 0; gSectorSizes[i] != 0; i++) {
                if (gSectorSizes[i] == size) {
                    n = (byte) i;
                    break;
                }
            }
            return n;
        }

        public static final int[] gSectorSizes = {128, 256, 512, 1024, 0};
    }

    /** トラックデータへのポインタを保持するクラス */
    public static abstract class DiskImageTrack {

        protected DiskImageDisk parent;
        /** track number */
        protected int trackNum;
        /** side number */
        protected int sideNum;
        /** position of offset table in header */
        protected int offsetPos;
        /** track size */
        protected int size;
        /** interleave of sector */
        protected int interleave;

        /** num of sectors (original / pre save) */
        protected List<DiskImageSector> sectors;

        /** extra data */
        protected int originalSectors;
        /** extra data size */
        protected byte[] extraData;

        protected int extraSize;

        public DiskImageTrack(DiskImageDisk disk) {
            parent = disk;
            trackNum = 0;
            sideNum = 0;
            sectors = null;
            size = 0;
            interleave = 1;
            originalSectors = 0;
            extraData = null;
            extraSize = 0;
        }

        /**
         * @param disk       ディスク
         * @param trackNum   トラック番号
         * @param sideNum    サイド番号
         * @param offsetPos  オフセットインデックス
         * @param interleave インターリーブ
         */
        public DiskImageTrack(DiskImageDisk disk, int trackNum, int sideNum, int offsetPos, int interleave) {
            parent = disk;
            this.trackNum = trackNum;
            this.sideNum = sideNum;
            this.offsetPos = offsetPos;
            sectors = null;
            size = 0;
            this.interleave = interleave;
            originalSectors = 0;
            extraData = null;
            extraSize = 0;
        }

        /** インスタンス作成 */
        public abstract DiskImageSector newImageSector(int num, DiskImageSectorHeader header, byte[] data);

        /** インスタンス作成 */
        public abstract DiskImageSector newImageSector(int trackNumber, int sideNumber, int sectorNumber, int sectorSize, int numberOfSector, boolean singleDensity /* = false */, int status /* = 0 */);

        /**
         * セクタを追加する
         *
         * @return セクタ数
         */
        public int add(DiskImageSector newsec) {
            if (sectors == null) sectors = new ArrayList<>();
            sectors.add(newsec);
            originalSectors = sectors.size();
            return originalSectors;
        }

        /**
         * トラック内のセクタデータを置き換える
         *
         * @param srcTrack target track
         * @return 0:正常 -1:エラー 1:置換できないセクタあり
         */
        public int replace(DiskImageTrack srcTrack) {
            int rc = 0;
            if (sectors == null) return -1;
            for (DiskImageSector tagSector : sectors) {
                DiskImageSector srcSector = srcTrack.getSector(tagSector.getSectorNumber());
                if (srcSector == null) {
                    continue;
                }
                if (!tagSector.replace(srcSector)) {
                    rc = 1;
                }
            }
            return rc;
        }

        /**
         * トラックに新規セクタを追加する
         *
         * @param trackNum 新規セクタのトラック番号 (ID C)
         * @param sideNum  新規セクタのサイド番号 (ID H)
         * @param secNum   新規セクタのセクタ番号 (ID R)
         * @param secSize  新規セクタのセクタサイズ (128,256,512,1024,2048)
         * @param sDensity 新規セクタが単密度か
         * @param status   新規セクタのステータス (通常0)
         * @return 0 正常
         */
        public int addNewSector(int trackNum, int sideNum, int secNum, int secSize, boolean sDensity, int status) {
            int rc = 0;
            DiskImageSector newSector = newImageSector(trackNum, sideNum, secNum, secSize, 1, sDensity, status);
            add(newSector);
            decreaseExtraDataSize(newSector.getSize());
            shrinkAndCalcOffsets(false);
            return rc;
        }

        /**
         * トラック内の指定位置のセクタを削除する
         *
         * @param pos セクタ位置
         */
        public int deleteSectorByIndex(int pos) {
            int rc = 0;
            if (sectors == null || pos < 0 || pos >= sectors.size()) return -1;
            int removedSize = 0;
            DiskImageSector sector = sectors.get(pos);
            removedSize += sector.getSize();
            sectors.remove(pos);
            increaseExtraDataSize(removedSize);
            shrinkAndCalcOffsets(false);
            return rc;
        }

        /**
         * トラック内の指定セクタを削除する
         *
         * @param startSectorNum 開始セクタ番号
         * @param endSectorNum   終了セクタ番号 -1 なら全て
         * @return 0: 正常, -1: エラー
         */
        public int deleteSectors(int startSectorNum, int endSectorNum) {
            int rc = 0;
            if (sectors == null) return -1;
            boolean removed = false;
            int removedSize = 0;
            for (int i = 0; i < sectors.size(); i++) {
                DiskImageSector sector = sectors.get(i);
                int num = sector.getSectorNumber();
                if (startSectorNum <= num && (num <= endSectorNum || endSectorNum < 0)) {
                    removedSize += sector.getSize();
                    sectors.remove(i);
                    removed = true;
                    i--;
                }
            }
            if (removed) {
                increaseExtraDataSize(removedSize);
                shrinkAndCalcOffsets(false);
            }
            return rc;
        }

        /** トラックサイズの再計算 */
        public int shrink(boolean trimUnusedData) {
            int newsize = 0;
            int count = sectors != null ? sectors.size() : 0;
            for (int i = 0; i < count; i++) {
                DiskImageSector sector = sectors.get(i);
                sector.setSectorsPerTrack((short) count);
                newsize += sector.getHeaderSize();
                if (trimUnusedData) {
                    newsize += sector.getSectorSize();
                } else {
                    newsize += sector.getSectorBufferSize();
                }
            }
            if (!trimUnusedData) {
                newsize += extraSize;
            }
            setSize(newsize);
            return newsize;
        }

        /** トラックサイズの再計算&オフセット計算 */
        public void shrinkAndCalcOffsets(boolean trimUnusedData) {
            shrink(trimUnusedData);
            parent.calcOffsets();
        }

        /** 余りバッファ領域のサイズを増やす */
        public void increaseExtraDataSize(int size) {
            if (size == 0) return;
            byte[] newData = new byte[extraSize + size];
            Arrays.fill(newData, 0, size, (byte) 0);
            if (extraData != null) {
                System.arraycopy(extraData, 0, newData, size, extraSize);
            }
            extraData = newData;
            extraSize += size;
        }

        /** 余りバッファ領域のサイズを減らす */
        public void decreaseExtraDataSize(int size) {
            if (size == 0) return;
            int remainSize = (extraSize > size ? extraSize - size : 0);
            byte[] newData = null;
            if (remainSize > 0) {
                newData = new byte[remainSize];
                System.arraycopy(extraData, size, newData, 0, remainSize);
            }
            extraData = newData;
            extraSize = remainSize;
        }

        public int getTrackNumber() {
            return trackNum;
        }

        public void setTrackNumber(int val) {
            trackNum = val;
        }

        public int getSideNumber() {
            return sideNum;
        }

        public void setSideNumber(int val) {
            sideNum = val;
        }

        public int getOffsetPos() {
            return offsetPos;
        }

        /** トラック内の最小セクタ番号を返す */
        public int getMinSectorNumber() {
            int sectorNumber = 0x7fffffff;
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    if (sectorNumber > s.getSectorNumber()) {
                        sectorNumber = s.getSectorNumber();
                    }
                }
            }
            return sectorNumber;
        }

        /** トラック内の最大セクタ番号を返す */
        public int getMaxSectorNumber() {
            int sectorNumber = 0;
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    if (sectorNumber < s.getSectorNumber()) {
                        sectorNumber = s.getSectorNumber();
                    }
                }
            }
            return sectorNumber;
        }

        /** トラック内の最大セクタサイズを返す */
        public int getMaxSectorSize() {
            int sectorSize = 0;
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    if (sectorSize < s.getSectorSize()) {
                        sectorSize = s.getSectorSize();
                    }
                }
            }
            return sectorSize;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int val) {
            size = val;
        }

        public int getInterleave() {
            return interleave;
        }

        public void setInterleave(int val) {
            interleave = val;
        }

        /** インターリーブを計算して設定 */
        public void calcInterleave() {
            if (sectors == null) return;
            int count = sectors.size();
            if (count == 1) {
                setInterleave(1);
                return;
            }
            int start = sectors.getFirst().getSectorNumber();
            int next = start + 1;
            int state = 0;
            int intl = 0;
            for (int secPos = 0; secPos < count; secPos++) {
                DiskImageSector s = sectors.get(secPos);
                switch (state) {
                    case 1:
                        intl++;
                        if (s.getSectorNumber() == next) {
                            state = 2;
                            secPos = count;
                        }
                        break;
                    default:
                        if (s.getSectorNumber() == start) {
                            state = 1;
                            intl = 0;
                        }
                        break;
                }
            }
            if (intl <= 0) {
                intl = 1;
            }
            setInterleave(intl);
        }

        public List<DiskImageSector> getSectors() {
            return sectors;
        }

        /** セクタ数を返す */
        public int getSectorsPerTrack() {
            int cnt = 0;
            if (sectors != null) {
                cnt = sectors.size();
            }
            return cnt;
        }

        /**
         * 指定セクタ番号のセクタを返す
         *
         * @param sectorNumber セクタ番号
         * @param density      密度で絞る 0:倍密度 1:単密度 -1:条件から除外
         * @return セクタ or null
         */
        public DiskImageSector getSector(int sectorNumber, int density) {
            DiskImageSector sector = null;
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    if (s.isSameSector(sectorNumber, density, false)) {
                        sector = s;
                        break;
                    }
                }
            }
            return sector;
        }

        public DiskImageSector getSector(int sectorNumber) {
            return getSector(sectorNumber, -1);
        }

        /** 指定位置のセクタを返す */
        public DiskImageSector getSectorByIndex(int pos) {
            DiskImageSector sector = null;
            if (sectors != null && pos >= 0 && pos < sectors.size()) {
                sector = sectors.get(pos);
            }
            return sector;
        }

        /** トラック内のもっともらしいID Cを返す */
        public byte getMajorIDC() {
            byte id = 0;
            Map<Integer, Integer> map = new HashMap<>();
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    IntHashMapUtil.increaseValue(map, s.getIDC());
                }
                id = (byte) IntHashMapUtil.getMaxKeyOnMaxValue(map);
            }
            return id;
        }

        /** トラック内のもっともらしいID Hを返す */
        public byte getMajorIDH() {
            byte id = 0;
            Map<Integer, Integer> map = new HashMap<>();
            if (sectors != null) {
                for (DiskImageSector s : sectors) {
                    IntHashMapUtil.increaseValue(map, s.getIDH());
                }
                id = (byte) IntHashMapUtil.getMaxKeyOnMaxValue(map);
            }
            return id;
        }

        /** トラック内のすべてのID Cを変更 */
        public void setAllIDC(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDC(val);
                }
            }
        }

        /** トラック内のすべてのID Hを変更 */
        public void setAllIDH(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDH(val);
                }
            }
        }

        /** トラック内のすべてのID Rを変更 */
        public void setAllIDR(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDR(val);
                }
            }
        }

        /** トラック内のすべてのID Nを変更 */
        public void setAllIDN(byte val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setIDN(val);
                }
            }
        }

        /** トラック内のすべての密度を変更 */
        public void setAllSingleDensity(boolean val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setSingleDensity(val);
                }
            }
        }

        /** トラック内のすべてのセクタ数を変更 */
        public void setAllSectorsPerTrack(int val) {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sector.setSectorsPerTrack((short) val);
                }
            }
        }

        /** トラック内のすべてのセクタサイズを変更 */
        public void setAllSectorSize(int val) {
            if (sectors == null) return;
            int sum = 0;
            for (DiskImageSector sector : sectors) {
                if (sector != null) {
                    sum += sector.modifySectorSize(val);
                }
            }
            if (sum > 0) {
                increaseExtraDataSize(sum);
            } else if (sum < 0) {
                decreaseExtraDataSize(-sum);
            }
            shrinkAndCalcOffsets(false);
        }

        /** 余分なデータを設定する */
        public void setExtraData(byte[] buf, int size) {
            extraData = buf;
            extraSize = size;
        }

        public byte[] getExtraData() {
            return extraData;
        }

        public int getExtraDataSize() {
            return extraSize;
        }

        /** 変更されているか */
        public boolean isModified() {
            if (sectors == null) return false;
            if (originalSectors != sectors.size()) return true;
            boolean modified = false;
            for (DiskImageSector sector : sectors) {
                if (sector == null) continue;
                modified = sector.isModified();
                if (modified) {
                    break;
                }
            }
            return modified;
        }

        /** 変更済みをクリア */
        public void clearModify() {
            if (sectors == null) return;
            for (DiskImageSector sector : sectors) {
                if (sector == null) continue;
                sector.clearModify();
            }
            originalSectors = sectors.size();
        }

        /** トラック番号とサイド番号の比較 */
        public static int compare(DiskImageTrack item1, DiskImageTrack item2) {
            return ((item1.trackNum - item2.trackNum) | (item1.sideNum - item2.sideNum));
        }

        /**
         * インターリーブを考慮したセクタ番号リストを返す
         *
         * <pre>
         * interleave = 2 の時
         * numOfSectors[0] = sector_offset, numOfSectors[2] = sector_offset + 1, numOfSectors[4] = sector_offset + 2, ... となる
         * </pre>
         *
         * @param interleave   インターリーブ(1...)
         * @param sectorsCount セクタ数
         * @param sectorOffset オフセット
         * @param sectorNums   [out] 配列
         */
        public static boolean calcSectorNumbersForInterleave(int interleave, int sectorsCount, List<Integer> sectorNums, int sectorOffset) {
            sectorNums.clear();
            for (int i = 0; i < sectorsCount; i++) {
                sectorNums.add(-1);
            }
            int sectorPos = 0;
            boolean err = false;
            for (int sectorNumber = 0; sectorNumber < sectorsCount && !err; sectorNumber++) {
                if (sectorPos >= sectorsCount) {
                    sectorPos -= sectorsCount;
                    while (sectorNums.get(sectorPos) >= 0) {
                        sectorPos++;
                        if (sectorPos >= sectorsCount) {
                            err = true;
                            break;
                        }
                    }
                }
                sectorNums.set(sectorPos, sectorNumber + sectorOffset);
                sectorPos += interleave;
            }
            return !err;
        }
    }

    /** １ディスクのヘッダを渡すクラス */
    public static abstract class DiskImageDiskHeader {

        public DiskImageDiskHeader() {
        }

        public abstract int getHeaderType();

        /** ディスク名を返す */
        public String getName(boolean real) {
            return "";
        }

        /** 書き込み禁止かを返す */
        public boolean isWriteProtected() {
            return false;
        }
    }

    /** １ディスクへのポインタを保持するクラス */
    public static abstract class DiskImageDisk extends DiskParam {

        protected DiskImageFile parent;
        /** disk number */
        protected int num;
        /** disk name */
        protected String name;
        /** write protected ? */
        protected boolean writeProtect;

        /** usually header size */
        protected int offsetStart;

        protected List<DiskImageTrack> tracks;
        protected int maxTrackNumber;

        /** 解析したパラメータ */
        protected DiskParam origParam;
        /** ディスクパラメータを変更したか */
        protected boolean paramChanged;

        protected List<DiskBasic> basics;

        /**
         * @param file ファイルイメージ
         * @param num ディスク番号
         */
        public DiskImageDisk(DiskImageFile file, int num) {
            parent = file;
            this.num = num;
            writeProtect = false;
            tracks = null;
            offsetStart = 0;
            paramChanged = false;
            basics = new ArrayList<>();
        }

        /**
         * @param file         ファイルイメージ
         * @param num          ディスク番号
         * @param param        ディスクパラメータ
         * @param diskName     ディスク名
         * @param writeProtect 書き込み禁止か
         */
        public DiskImageDisk(DiskImageFile file, int num, DiskParam param, String diskName, boolean writeProtect) {
            super(param);
            parent = file;
            this.num = num;
            name = diskName;
            this.writeProtect = writeProtect;
            tracks = null;
            offsetStart = 0;
            paramChanged = false;
            basics = new ArrayList<>();
        }

        /**
         * @param file   ファイルイメージ
         * @param num    ディスク番号
         * @param header ディスクヘッダ
         */
        public DiskImageDisk(DiskImageFile file, int num, DiskImageDiskHeader header) {
            super();
            parent = file;
            this.num = num;
            name = header.getName(false);
            writeProtect = header.isWriteProtected();
            tracks = null;
            offsetStart = 0;
            paramChanged = false;
            basics = new ArrayList<>();
        }

        public abstract DiskImageTrack newImageTrack();

        public abstract DiskImageTrack newImageTrack(int trackNum, int sideNum, int offsetPos, int interleave);

        /**
         * ディスクにトラックを追加
         *
         * @return トラック数
         */
        public int add(DiskImageTrack newTrack) {
            if (tracks == null) tracks = new ArrayList<>();
            tracks.add(newTrack);
            return tracks.size();
        }

        /**
         * ディスクの内容を置き換える
         *
         * @param sideNumber    サイド番号
         * @param srcDisk       置換元のディスクイメージ
         * @param srcSideNumber 置換元のディスクイメージのサイド番号
         */
        public int replace(int sideNumber, DiskImageDisk srcDisk, int srcSideNumber) {
            int rc = 0;
            if (tracks == null) return -1;
            for (DiskImageTrack tagTrack : tracks) {
                int tagSideNumber = tagTrack.getSideNumber();
                if (sideNumber >= 0) {
                    if (tagSideNumber != sideNumber) {
                        continue;
                    }
                    if (srcSideNumber >= 0) {
                        tagSideNumber = srcSideNumber;
                    }
                    if (srcDisk.getSidesPerDisk() <= tagSideNumber) {
                        tagSideNumber = srcDisk.getSidesPerDisk() - 1;
                    }
                } else {
                    if (getSidesPerDisk() <= 1) {
                        tagSideNumber = srcSideNumber;
                    }
                }
                DiskImageTrack srcTrack = srcDisk.getTrack(tagTrack.getTrackNumber(), tagSideNumber);
                if (srcTrack == null) {
                    continue;
                }
                int rct = tagTrack.replace(srcTrack);
                if (rct != 0) rc = rct;
            }
            return rc;
        }

        /**
         * ディスクにトラックを追加
         *
         * @param sideNumber サイド番号 両面なら -1
         */
        public int addNewTrack(int sideNumber) {
            int rc = 0;
            DiskImageTrack srcTrack = null;
            int trackNum = -1;
            int sideNum = -1;
            int maxSideNum = -1;
            for (DiskImageTrack track : tracks) {
                if (track.getTrackNumber() > trackNum && (sideNumber < 0 || sideNumber == track.getSideNumber())) {
                    trackNum = track.getTrackNumber();
                    sideNum = track.getSideNumber();
                    if (sideNum > maxSideNum) {
                        maxSideNum = sideNum;
                    }
                    srcTrack = track;
                } else if (sideNumber < 0 && track.getSideNumber() > sideNum) {
                    sideNum = track.getSideNumber();
                    if (sideNum > maxSideNum) {
                        maxSideNum = sideNum;
                    }
                    srcTrack = track;
                }
            }
            if (srcTrack == null) {
                return -1;
            }
            List<DiskImageSector> sectors = srcTrack.getSectors();
            if (sectors == null) {
                return -1;
            }
            if (sideNumber < 0 && sideNum < maxSideNum) {
                sideNum++;
            } else {
                trackNum++;
                sideNum = (sideNumber < 0 ? 0 : sideNumber);
            }
            int limitPos = getCreatableTracks();
            int offsetPos = 0;
            for (int pos = (sideNumber < 0 ? 0 : sideNumber); pos < limitPos; pos += (sideNumber < 0 ? 1 : maxSideNum + 1)) {
                if (getOffset(pos) == 0) {
                    offsetPos = pos;
                    break;
                }
            }
            if (offsetPos == 0) {
                return -1;
            }
            DiskImageTrack newTrack = newImageTrack(trackNum, sideNum, offsetPos, srcTrack.getInterleave());
            int trackSize = 0;
            for (int pos = 0; pos < sectors.size(); pos++) {
                DiskImageSector sector = sectors.get(pos);
                int sectorNum = sector.getIDR();
                int sectorSize = sector.getSectorSize();
                boolean sdensity = sector.isSingleDensity();
                DiskImageSector newSector = newTrack.newImageSector(trackNum, sideNum, sectorNum, sectorSize, sectors.size(), sdensity, 0);
                newTrack.add(newSector);
                trackSize += newSector.getSize();
            }
            newTrack.increaseExtraDataSize(srcTrack.getExtraDataSize());
            trackSize += newTrack.getExtraDataSize();
            newTrack.setSize(trackSize);
            add(newTrack);
            calcOffsets();
            return rc;
        }

        /**
         * トラックを削除する
         *
         * @param startOffsetPos 削除開始トラック位置(0 ... 163)
         * @param endOffsetPos   削除終了トラック位置(0 ... 163)
         * @param sideNumber     特定のサイドのみ削除する場合 >= 0 , 全サイドの場合 = -1
         */
        public void deleteTracks(int startOffsetPos, int endOffsetPos, int sideNumber) {
            if (tracks == null) return;
            boolean removed = false;
            for (int i = 0; i < tracks.size(); i++) {
                DiskImageTrack track = tracks.get(i);
                if (track == null) continue;
                int pos = track.getOffsetPos();
                if (pos < startOffsetPos) continue;
                if (endOffsetPos >= startOffsetPos && pos > endOffsetPos) continue;
                if (sideNumber >= 0 && track.getSideNumber() != sideNumber) continue;
                tracks.remove(track);
                setOffset(pos, 0);
                removed = true;
                i--;
            }
            if (removed) {
                calcOffsets();
            }
        }

        /** トラックサイズ＆オフセットの再計算＆ディスクサイズ変更 */
        public int shrinkTracks(boolean trimUnusedData) {
            if (tracks != null) {
                for (DiskImageTrack track : tracks) {
                    if (track == null) continue;
                    track.shrink(trimUnusedData);
                }
            }
            return calcOffsets();
        }

        /** オフセットの再計算＆ディスクサイズ変更 */
        public int calcOffsets() {
            int newSize = 0;
            if (tracks == null) return newSize;
            int limitPos = getCreatableTracks();
            for (int pos = 0; pos < limitPos; pos++) {
                setOffset(pos, 0);
            }
            int maxOffset = offsetStart;
            for (DiskImageTrack track : tracks) {
                if (track == null) continue;
                int pos = track.getOffsetPos();
                if (pos < 0 || pos >= limitPos) continue;
                int size = track.getSize();
                if (size > 0) {
                    setOffset(pos, maxOffset);
                } else {
                    setOffset(pos, 0);
                }
                setModify();
                maxOffset += size;
                newSize += size;
            }
            setSize(newSize);
            return newSize;
        }

        /** ディスクサイズ計算（ディスクヘッダ分を除く） */
        public int calcSizeWithoutHeader() {
            int newSize = 0;
            if (tracks == null) return newSize;
            for (DiskImageTrack track : tracks) {
                if (track == null) continue;
                newSize += track.getSize();
            }
            return newSize;
        }

        public int getNumber() {
            return num;
        }

        public String getName(boolean real) {
            return "";
        }

        public void setName(String val) {
        }

        public void setName(byte[] buf, int len) {
        }

        public DiskImageDiskHeader getHeader() {
            return null;
        }

        public DiskImageFile getFile() {
            return parent;
        }

        public List<DiskImageTrack> getTracks() {
            return tracks;
        }

        /**
         * 指定トラックを返す
         *
         * @param trackNumber トラック番号（シリンダ）
         * @param sideNumber  サイド番号（ヘッド）
         * @return トラック
         */
        public DiskImageTrack getTrack(int trackNumber, int sideNumber) {
            DiskImageTrack track = null;
            if (tracks != null) {
                for (DiskImageTrack t : tracks) {
                    if (t.getTrackNumber() == trackNumber && t.getSideNumber() == sideNumber) {
                        track = t;
                        break;
                    }
                }
            }
            return track;
        }

        /**
         * 指定トラックを返す
         *
         * @param index 位置
         * @return トラック
         */
        public DiskImageTrack getTrack(int index) {
            DiskImageTrack track = null;
            if (tracks != null && index < tracks.size()) {
                track = tracks.get(index);
            }
            return track;
        }

        /**
         * 指定オフセット値からトラックを返す
         *
         * @param offset オフセット位置
         * @return トラック
         */
        public DiskImageTrack getTrackByOffset(int offset) {
            DiskImageTrack track = null;
            if (tracks != null) {
                for (DiskImageTrack t : tracks) {
                    if (t == null) continue;
                    int pos = t.getOffsetPos();
                    if (getOffset(pos) == offset) {
                        track = t;
                        break;
                    }
                }
            }
            return track;
        }

        public DiskImageSector getSector(int trackNumber, int sideNumber, int sectorNumber) {
            return getSector(trackNumber, sideNumber, sectorNumber, -1);
        }

        /**
         * 指定セクタを返す
         *
         * @param trackNumber  トラック番号（シリンダ）
         * @param sideNumber   サイド番号（ヘッド）
         * @param sectorNumber セクタ番号（レコード）
         * @param density      密度で絞る 0: 倍密度, 1: 単密度, -1: 条件から除外
         * @return セクタ
         */
        public DiskImageSector getSector(int trackNumber, int sideNumber, int sectorNumber, int density) {
            DiskImageTrack track = getTrack(trackNumber, sideNumber);
            if (track == null) return null;
            return track.getSector(sectorNumber, density);
        }

        /**
         * ディスクの中でもっともらしいパラメータを設定
         * <p>
         * system property
         * <li>`l3diskex.normalize.enabled` ... do normalize after fetch params or not default {@code true}</li>
         * This is used for cases where the analysis incorrectly identifies 3 surfaces,
         * which can prevent a suitable template from being found.
         *
         * @return パラメータ
         */
        public DiskParam calcMajorNumber() {
            Map<Integer, Integer>[] sectorNumbersMap = new HashMap[2];
            sectorNumbersMap[0] = new HashMap<>();
            sectorNumbersMap[1] = new HashMap<>();
            Map<Integer, Integer> sectorSizeMap = new HashMap<>();
            Map<Integer, Integer> interleaveMap = new HashMap<>();

            int trackNumberMin = 0x7fff_ffff;
            int trackNumberMax = 0;
            int sideNumberMin = 0x7fff_ffff;
            int sideNumberMax = 0;

            int sectorNumberMaxSide0 = 0;
            int sectorNumberMinSide0 = 0x7fff_ffff;
            int sectorNumberMinSide1 = 0x7fff_ffff;

            int sectorMaxSize;
            int interleaveMax;
            List<DiskParticular> singles = new ArrayList<>();

            if (tracks != null) {
                for (DiskImageTrack t : tracks) {
                    int trackNum = t.getTrackNumber();
                    int sideNum = t.getSideNumber();
//logger.log(Level.TRACE, "trackNum: " + trackNum + ", sideNum: " + sideNum);

                    trackNumberMin = IntHashMapUtil.minValue(trackNumberMin, trackNum);
                    trackNumberMax = IntHashMapUtil.maxValue(trackNumberMax, trackNum);

                    if (trackNum > 0) {
                        sideNumberMin = IntHashMapUtil.minValue(sideNumberMin, sideNum);
                        sideNumberMax = IntHashMapUtil.maxValue(sideNumberMax, sideNum);
                    }

                    IntHashMapUtil.increaseValue(sectorSizeMap, t.getMaxSectorSize());
                    IntHashMapUtil.increaseValue(interleaveMap, t.getInterleave());

                    sideNum &= 0x7f;

                    if (sideNum >= 0 && sideNum < 2) {
                        IntHashMapUtil.increaseValue(sectorNumbersMap[sideNum], t.getSectorsPerTrack());
                    }

                    if (trackNum > 0 && sideNum < 2) {
                        int sectorNumMax = t.getMaxSectorNumber();
                        int sectorNumMin = t.getMinSectorNumber();
                        if (sideNum == 0) {
                            sectorNumberMaxSide0 = IntHashMapUtil.maxValue(sectorNumberMaxSide0, sectorNumMax);
                            sectorNumberMinSide0 = IntHashMapUtil.minValue(sectorNumberMinSide0, sectorNumMin);
                        } else {
                            sectorNumberMinSide1 = IntHashMapUtil.minValue(sectorNumberMinSide1, sectorNumMin);
                        }
                    }

                    List<DiskImageSector> sectors = t.getSectors();
                    if (sectors != null) {
                        List<DiskParticular> sis = new ArrayList<>();
                        for (DiskImageSector s : sectors) {
                            if (s != null && s.isSingleDensity()) {
                                DiskParticular sd = new DiskParticular(t.getTrackNumber(), t.getSideNumber(), s.getSectorNumber(), 1, s.getSectorsPerTrack(), s.getSectorSize());
                                sis.add(sd);
                            }
                        }
                        DiskParticular.uniqueSectors(t.getSectorsPerTrack(), sis);
                        singles.addAll(sis);
                    }
                }
            }
            sectorMaxSize = IntHashMapUtil.getMaxKeyOnMaxValue(sectorSizeMap);
            interleaveMax = IntHashMapUtil.getMaxKeyOnMaxValue(interleaveMap);

            sidesPerDisk = sideNumberMax + 1 - sideNumberMin;

            if (Boolean.parseBoolean(System.getProperty("l3diskex.normalize.enabled", "true"))) {
                // normalize a parameter
                if (sidesPerDisk > 2) {
logger.log(Level.TRACE, "NORMALIZE: sidesPerDisk: 2 <- " + sidesPerDisk);
                    sidesPerDisk = Math.min(sidesPerDisk, 2);
                }
            }
logger.log(Level.TRACE, "sidesPerDisk: " + sidesPerDisk + ", sideNumberMax: " + sideNumberMax + ", sideNumberMin: " + sideNumberMin);

            if (tracks != null) {
                int trackCount = (tracks.size() + sidesPerDisk - 1) / sidesPerDisk;
                if (trackNumberMax > (trackCount + 4)) {
                    trackNumberMax = trackCount - 1;
                }
            }

            boolean diskSingleType = false;
            if (tracks != null) {
                if (sectorMaxSize == 128 && sideNumberMax == 0 && maxTrackNumber > trackNumberMax) {
                    diskSingleType = true;
                    sideNumberMax++;
                    sidesPerDisk++;
logger.log(Level.TRACE, "sidesPerDisk: " + sidesPerDisk);
                    for (DiskImageTrack t : tracks) {
                        if ((t.getOffsetPos() & 1) != 0) {
                            t.setSideNumber(1);
                        }
                    }
                }
            }

            DiskParticular.uniqueTracks(trackNumberMax - trackNumberMin + 1, sidesPerDisk, diskSingleType, singles);

            tracksPerSide = tracks != null ? (trackNumberMax - trackNumberMin + 1) : 0;
            sectorSize = sectorMaxSize;
            interleave = interleaveMax;

            if (sidesPerDisk > 1 && sectorNumberMinSide1 != 0x7fff_ffff && sectorNumberMaxSide0 < sectorNumberMinSide1) {
                numberingSector = 1;
                int secNumMaj = 0;
                secNumMaj = IntHashMapUtil.getMaxKeyOnMaxValue(sectorNumbersMap[0]);
                sectorsPerTrack = secNumMaj;
            } else {
                numberingSector = 0;
                int[] secNumMaj = new int[2];
                for (int i = 0; i < 2; i++) {
                    secNumMaj[i] = IntHashMapUtil.getMaxKeyOnMaxValue(sectorNumbersMap[i]);
                }
                sectorsPerTrack = (secNumMaj[0] > secNumMaj[1] ? secNumMaj[0] : secNumMaj[1]);
            }

            List<DiskParticular> pTracks = new ArrayList<>();
            if (tracks != null) {
                for (DiskImageTrack t : tracks) {
                    if (t == null) continue;
                    List<DiskImageSector> ss = t.getSectors();
                    if (ss == null) continue;
                    if (ss.size() != sectorsPerTrack) {
                        pTracks.add(new DiskParticular(t.getTrackNumber(), t.getSideNumber(), -1, 1, ss.size(), t.getMaxSectorSize()));
                    }
                }
                DiskParticular.uniqueTracks(tracksPerSide, sidesPerDisk, false, pTracks);
            }

            DiskParam diskParam = gDiskTemplates.find(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize, interleave, trackNumberMin, sideNumberMin, sectorNumberMinSide0, numberingSector, singles, pTracks);
            if (diskParam != null) {
                setDiskTypeName(diskParam.getDiskTypeName());
                setReversible(diskParam.isReversible());
                setBasicTypes(diskParam.getBasicTypes());
                setSingles(singles);
                setTrackNumberBaseOnDisk(diskParam.getTrackNumberBaseOnDisk());
                setSideNumberBaseOnDisk(diskParam.getSideNumberBaseOnDisk());
                setSectorNumberBaseOnDisk(diskParam.getSectorNumberBaseOnDisk());
                setVariableSectorsPerTrack(diskParam.isVariableSectorsPerTrack());
                setParamDensity(diskParam.getParamDensity());
                setParticularTracks(diskParam.getParticularTracks());
                setDensityName(diskParam.getDensityName());
                setDescription(diskParam.getDescription());
            }

            allocDiskBasics();
            setOriginalParam(this);

logger.log(Level.TRACE, "diskParam: " + diskParam);
            return diskParam;
        }

        /**
         * ディスクの内容を初期化する(0パディング)
         *
         * @param selectedSide >=0なら指定サイドのみ初期化
         */
        public boolean initialize(int selectedSide) {
            if (tracks == null) {
                return false;
            }

            boolean rc = true;
            for (DiskImageTrack track : tracks) {
                if (selectedSide >= 0) {
                    if (selectedSide != track.getSideNumber()) {
                        continue;
                    }
                }

                List<DiskImageSector> sectors = track.getSectors();
                if (sectors == null) {
                    continue;
                }

                for (DiskImageSector sector : sectors) {
                    if (sector != null) {
                        sector.fill((byte) 0, -1, 0);
                    }
                }
            }
            return rc;
        }

        /**
         * ディスクのトラックを作り直す
         *
         * @param param        パラメータ
         * @param selectedSide >= 0 なら指定サイドのみ初期化
         */
        public boolean rebuild(DiskParam param, int selectedSide) {
            if (selectedSide >= 0) {
                setDiskParam(param.getSidesPerDisk(), param.getTracksPerSide(), param.getSectorsPerTrack(), param.getSectorSize(), param.getParamDensity(), param.getInterleave(), param.getSingles(), param.getParticularTracks());
            } else {
                setDiskParam(param);
            }

            DiskResult result = new DiskResult();
            String diskname = "";
            DiskImageCreator cr = new DiskImageCreator(diskname, param, false, null, result);
            boolean rc = true;
            int trackNum = param.getTrackNumberBaseOnDisk();
            int numOfTracks = param.getTracksPerSide() + trackNum;
            int sid = 0;
            int sides = param.getSidesPerDisk();
            for (int pos = 0; pos < getCreatableTracks(); pos++) {
                if (selectedSide >= 0) {
                    sid = selectedSide;
                    if (selectedSide != (pos % sides)) {
                        continue;
                    }
                }

                int offset = getOffset(pos);
                DiskImageTrack track = getTrackByOffset(offset);
                if (tracks != null && track != null) {
                    tracks.remove(track);
                }
                if (offset == 0) {
                    offset = getSize();
                    if (offset < getOffsetStart()) {
                        offset = getOffsetStart();
                    }
                }
                int trackSize = cr.createTrack(trackNum, sid, pos, offset, this);
                setOffset(pos, offset);
                setSize(offset + trackSize);

                sid++;
                if (sid >= sides || selectedSide >= 0) {
                    trackNum++;
                    sid = 0;
                }
                if (trackNum >= numOfTracks) {
                    setMaxTrackNumber(pos);
                    break;
                }
            }
            return rc;
        }

        public boolean isWriteProtected() {
            return true;
        }

        public void setWriteProtect(boolean val) {
        }

        public String getDensityText() {
            return "";
        }

        public int getDensity() {
            return 0;
        }

        public void setDensity(int val) {
        }

        public int getSize() {
            return 0;
        }

        public void setSize(int val) {
        }

        public int getSizeWithoutHeader() {
            return 0;
        }

        public void setSizeWithoutHeader(int val) {
        }

        public int getOffset(int num) {
            return 0;
        }

        public void setOffset(int num, int offset) {
        }

        public void setOffsetWithoutHeader(int num, int offset) {
        }

        public int getOffsetStart() {
            return offsetStart;
        }

        public void setOffsetStart(int val) {
            offsetStart = val;
        }

        public int getMaxTrackNumber() {
            return maxTrackNumber;
        }

        public void setMaxTrackNumber(int pos) {
            maxTrackNumber = pos;
        }

        public int getCreatableTracks() {
            return 0;
        }

        /** 変更済みに設定 */
        public void setModify() {
        }

        /** 変更されているか */
        public boolean isModified() {
            boolean modified = false;
            if (tracks != null) {
                for (int trackNum = 0; trackNum < tracks.size() && !modified; trackNum++) {
                    DiskImageTrack track = tracks.get(trackNum);
                    if (track == null) continue;
                    modified = track.isModified();
                    if (modified) {
                        break;
                    }
                }
            }
            return modified;
        }

        /** 変更済みをクリア */
        public void clearModify() {
            if (tracks != null) {
                for (DiskImageTrack track : tracks) {
                    if (track == null) continue;
                    track.clearModify();
                }
            }
        }

        /** トラックが存在するか */
        public boolean existTrack(int sideNumber) {
            boolean found = false;
            List<DiskImageTrack> tracks = getTracks();
            if (tracks != null) {
                for (DiskImageTrack trk : tracks) {
                    if (trk == null) continue;
                    if (sideNumber >= 0) {
                        if (sideNumber != trk.getSideNumber()) continue;
                    }
                    found = true;
                    break;
                }
            }
            return found;
        }

        public void setOriginalParam(DiskParam val) {
            origParam = val;
        }

        public DiskParam getOriginalParam() {
            return origParam;
        }

        public void setParamChanged(boolean val) {
            paramChanged = val;
        }

        public boolean getParamChanged() {
            return paramChanged;
        }

        /// DISK BASIC領域を確保 */
        public void allocDiskBasics() {
            DiskBasic nullDiskBasic = new DiskBasic();
            basics.add(nullDiskBasic);
            if (reversible) basics.add(nullDiskBasic);
        }

        /// DISK BASICを返す */
        public DiskBasic getDiskBasic(int idx) {
            if (idx < 0) idx = 0;
            return basics.get(idx);
        }

        public List<DiskBasic> getDiskBasics() {
            return basics;
        }

        /// DISK BASICをクリア */
        public void clearDiskBasics() {
            if (basics == null) return;
            for (int index = 0; index < basics.size(); index++) {
                DiskBasic basic = getDiskBasic(index);
                if (basic == null) continue;
                basic.clearParseAndAssign(false);
            }
        }

        /** キャラクターコードマップ番号設定 */
        public void setCharCode(String name) {
            if (basics == null) return;
            for (int index = 0; index < basics.size(); index++) {
                DiskBasic basic = getDiskBasic(index);
                if (basic == null) continue;
                basic.setCharCode(name);
            }
        }

        /** ディスク番号を比較 */
        public static int compare(DiskImageDisk item1, DiskImageDisk item2) {
            return item1.num - item2.num;
        }

        protected int sidesPerDisk;
        protected int tracksPerSide;
        protected int sectorsPerTrack;
        protected int sectorSize;
        protected int interleave;
        protected int numberingSector;
        protected boolean reversible;
    }

    /** ディスクイメージへのポインタを保持するクラス */
    public static abstract class DiskImageFile {

        /** イメージ */
        protected DiskImage image;
        /** ディスク */
        protected List<DiskImageDisk> disks;
        /** 変更フラグ 追加したかどうか */
        protected List<Short> mods;

        /** BASIC種類ヒント */
        protected String basicTypeHint = "";

        public DiskImageFile(DiskImage image) {
            this.image = image;
            disks = null;
            mods = null;
        }

        /**
         * インスタンス作成
         *
         * @param num ディスク番号
         */
        public abstract DiskImageDisk newImageDisk(int num);

        /**
         * インスタンス作成
         *
         * @param num          ディスク番号
         * @param param        ディスクパラメータ
         * @param diskName     ディスク名
         * @param writeProtect 書き込み禁止か
         */
        public abstract DiskImageDisk newImageDisk(int num, DiskParam param, String diskName, boolean writeProtect);

        /**
         * インスタンス作成
         *
         * @param num    ディスク番号
         * @param header ディスクヘッダ
         */
        public abstract DiskImageDisk newImageDisk(int num, DiskImageDiskHeader header);

        // 変更フラグ 追加したかどうか
        public static final short MODIFY_NONE = 0;
        public static final short MODIFY_ADD = 1;

        /** ディスクを追加 */
        public int add(DiskImageDisk newDisk, short modFlags) {
            if (disks == null) disks = new ArrayList<>();
            if (mods == null) mods = new ArrayList<>();
            disks.add(newDisk);
            mods.add(modFlags);
            return disks.size();
        }

        /** 全ディスクを削除 */
        public void clear() {
            if (disks != null) {
                disks.clear();
                disks = null;
            }
            if (mods != null) {
                mods = null;
            }
        }

        /** ディスク数を返す */
        public int count() {
            if (disks == null) return 0;
            return disks.size();
        }

        /** ディスクを削除 */
        public boolean delete(int index) {
            DiskImageDisk disk = getDisk(index);
            if (disk == null) return false;
            disks.remove(index);
            mods.remove(index);
            return true;
        }

        /** ディスクを返す */
        public List<DiskImageDisk> getDisks() {
            return disks;
        }

        /** ディスクを返す */
        public DiskImageDisk getDisk(int index) {
            if (disks == null) return null;
            if (index >= disks.size()) return null;
            return disks.get(index);
        }

        public boolean isModified() {
            boolean modified = false;
            if (disks != null) {
                for (int diskNum = 0; diskNum < disks.size() && !modified; diskNum++) {
                    modified = (mods.get(diskNum) != 0);
                    if (modified) break;

                    DiskImageDisk disk = disks.get(diskNum);
                    if (disk == null) continue;

                    modified = disk.isModified();
                    if (modified) break;
                }
            }
            return modified;
        }

        public void clearModify() {
            if (disks != null) {
                for (int diskNum = 0; diskNum < disks.size(); diskNum++) {
                    mods.set(diskNum, MODIFY_NONE);

                    DiskImageDisk disk = disks.get(diskNum);
                    if (disk == null) continue;

                    disk.clearModify();
                }
            }
        }

        public String getBasicTypeHint() {
            return basicTypeHint;
        }

        public void setBasicTypeHint(String val) {
            basicTypeHint = val;
        }

        /** イメージを返す */
        public DiskImage getImage() {
            return image;
        }
    }

    protected Path filename;
    protected DiskImageFile file;
    protected DiskResult result = new DiskResult();
    protected String formatType;

    /** ファイル構造体を作成 */
    protected void newFile(String filepath) {
        if (file != null) {
            file = null;
        }
        file = newImageFile();
        filename = Path.of(filepath);
    }

    /** ファイル構造体をクリア */
    protected void clearFile() {
        file = null;
    }

    public DiskImage() {
        file = null;
    }

    /** インスタンス作成 */
    public abstract DiskImageFile newImageFile();

    /**
     * 新規作成
     *
     * @param diskName     ディスク名
     * @param param        ディスクパラメータ
     * @param writeProtect 書き込み禁止
     * @param basicHint    DISK BASIC種類のヒント
     * @return 0 正常
     * @return -1 エラーあり
     * @return 1 警告あり
     */
    public int create(String diskName, DiskParam param, boolean writeProtect, String basicHint) {
        result.clear();

        newFile("");
        file.setBasicTypeHint(basicHint);
        DiskImageCreator cr = new DiskImageCreator(diskName, param, writeProtect, file, result);
        int validDisk = cr.create();

        // エラーあり
        if (validDisk < 0) {
            clearFile();
        }
        return validDisk;
    }

    /**
     * 追加で新規作成
     *
     * @param diskName     ディスク名
     * @param param        ディスクパラメータ
     * @param writeProtect 書き込み禁止
     * @param basicHint    DISK BASIC種類のヒント
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    public int add(String diskName, DiskParam param, boolean writeProtect, String basicHint) {
        if (file == null) return 0;

        result.clear();

        file.setBasicTypeHint(basicHint);
        DiskImageCreator cr = new DiskImageCreator(diskName, param, writeProtect, file, result);
        int validDisk = cr.add();

        return validDisk;
    }

    /**
     * ファイルを追加
     *
     * @param filepath   ファイルパス
     * @param fileFormat ファイルの形式名 ("d88", "plain"など)
     * @param paramHint  ディスクパラメータヒント ("plain" 時のみ)
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    public int add(String filepath, String fileFormat, DiskParam paramHint) throws IOException {
        // ファイル開いていない
        if (file == null) return 0;

        result.clear();

        SeekableDataInputStream fstream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));

        DiskParser ps = new DiskParser(filepath, fstream, file, result);
        int validDisk = ps.parseAdd(fileFormat, paramHint);

        return validDisk;
    }

    /**
     * ファイルを開く
     *
     * @param filepath   ファイルパス
     * @param fileFormat ファイルの形式名 ("d88", "plain"など)
     * @param paramHint  ディスクパラメータヒント ("plain" 時のみ)
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    public int open(String filepath, String fileFormat, DiskParam paramHint) throws IOException {
        result.clear();

        // ファイルを開く
        SeekableDataInputStream fStream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));

        newFile(filepath);
        DiskParser parser = new DiskParser(filepath, fStream, file, result);
        int validDisk = parser.parse(fileFormat, paramHint);

        if (validDisk < 0) {
            // エラーあり
            clearFile();
        } else {
            setFormatType(fileFormat);
        }

        return validDisk;
    }

    /**
     * ファイルを開く前のチェック
     *
     * @param filepath    ファイルパス
     * @param fileFormat  [in,out] ファイルの形式名 ("d88", "plain", "" など)
     * @param params      [out]  ディスクパラメータの候補
     * @param manualParam [out]  候補がないときのパラメータヒント
     * @return 0: 問題なし, -1: エラーあり, 1: 警告あり
     */
    public int check(String filepath, String[] fileFormat, List<DiskParam> params, DiskParam manualParam) throws IOException {
        result.clear();

        // ファイルを開く
        SeekableDataInputStream fStream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));

        DiskParser parser = new DiskParser(filepath, fStream, file, result);
        return parser.check(fileFormat, params, manualParam);
    }

    /** 閉じる */
    public void close() {
        clearFile();
        filename = null;
    }

    /**
     * ストリームの内容をファイルに保存できるか
     *
     * @param fileFormat 保存ファイルのフォーマット
     */
    public int canSave(String fileFormat) {
        DiskWriter dw = new DiskWriter(this, result);
        return dw.canSave(fileFormat);
    }

    /**
     * ストリームの内容をファイルに保存
     *
     * @param filepath   保存先ファイルパス
     * @param fileFormat 保存ファイルのフォーマット
     * @param options    保存時のオプション
     * @return 0: 正常, -1: エラー
     */
    public int save(String filepath, String fileFormat, DiskWriteOptions options) throws IOException {
        DiskWriter writer = new DiskWriter(this, filepath, options, result);
        return writer.save(fileFormat);
    }

    /**
     * ストリームの内容をファイルに保存
     *
     * @param diskNumber ディスク番号
     * @param sideNumber サイド番号
     * @param filepath   保存先ファイルパス
     * @param fileFormat 保存ファイルのフォーマット
     * @param options    保存時のオプション
     * @return 0: 正常, -1: エラー
     */
    public int saveDisk(int diskNumber, int sideNumber, String filepath, String fileFormat, DiskWriteOptions options) throws IOException {
        DiskWriter writer = new DiskWriter(this, filepath, options, result);
        return writer.saveDisk(diskNumber, sideNumber, fileFormat);
    }

    /**
     * ディスクを削除
     *
     * @param diskNumber ディスク番号
     * @return true
     */
    public boolean delete(int diskNumber) {
        if (file == null) return false;
        file.delete(diskNumber);
        return true;
    }

    /**
     * 置換元のディスクを解析
     *
     * @param diskNumber ディスク番号
     * @param sideNumber サイド番号
     * @param filepath   ファイルパス
     * @param fileFormat ファイルの形式名 ("d88", "plain" など)
     * @param paramHint  ディスクパラメータヒント ("plain" 時のみ)
     * @param srcFile    [out] ソースディスク
     * @param targetDisk [out] ターゲットディスク
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    public int parseForReplace(int diskNumber, int sideNumber, String filepath, String fileFormat, DiskParam paramHint, DiskImageFile srcFile, DiskImageDisk[] targetDisk) throws IOException {
        if (file == null) return 0;

        result.clear();

        SeekableDataInputStream fstream = new SeekableDataInputStream(Files.newByteChannel(Path.of(filepath)));

        DiskParser parser = new DiskParser(filepath, fstream, srcFile, result);
        int validDisk = parser.parse(fileFormat, paramHint);

        // エラーあり
        if (validDisk < 0) {
            return validDisk;
        }

        // ディスクを選択
        targetDisk[0] = file.getDisk(diskNumber);
        if (targetDisk[0] == null) {
            result.setError(DiskResult.ERR_NO_DATA);
            return result.getValid();
        }

        return 0;
    }

    /**
     * ファイルでディスクを置換
     *
     * @param diskNumber    ディスク番号
     * @param sideNumber    サイド番号
     * @param srcDisk       ソースディスク
     * @param srcSideNumber ソース側のサイド番号
     * @param tagDisk       ターゲットディスク
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    public int replaceDisk(int diskNumber, int sideNumber, DiskImage.DiskImageDisk srcDisk, int srcSideNumber, DiskImageDisk tagDisk) {
        if (file == null) return 0;

        result.clear();

        int validDisk = tagDisk.replace(sideNumber, srcDisk, srcSideNumber);
        if (validDisk != 0) {
            result.setError(DiskResult.ERR_REPLACE);
        }

        return validDisk;
    }

    /** ディスク名を設定 */
    public boolean setDiskName(int diskNumber, String newname) {
        DiskImageDisk disk = getDisk(diskNumber);
        if (disk == null) return false;

        if (!disk.getName(false).equals(newname)) {
            disk.setName(newname);
            disk.setModify();
            return true;
        }
        return false;
    }

    /** ディスク名を返す */
    public String getDiskName(int diskNumber, boolean real) {
        DiskImageDisk disk = getDisk(diskNumber);
        if (disk == null) return "";
        return disk.getName(real);
    }

    /** ディスクを変更したか */
    public boolean isModified() {
        boolean modified = false;
        if (file != null) {
            modified = file.isModified();
        }
        return modified;
    }

    /** ディスクファイルを返す */
    public DiskImageFile getFile() {
        return file;
    }

    /** ディスク枚数 */
    public int countDisks() {
        if (file == null) return 0;
        return file.count();
    }

    /** ディスク一覧を返す */
    public List<DiskImageDisk> getDisks() {
        if (file == null) return null;
        return file.getDisks();
    }

    /** 指定した位置のディスクを返す */
    public DiskImageDisk getDisk(int index) {
        if (file == null) return null;
        return file.getDisk(index);
    }

    /** 指定した位置のディスクのタイプ */
    public int getDiskTypeNumber(int index) {
        if (file == null) return -1;
        DiskImageDisk disk = file.getDisk(index);
        if (disk == null) return -1;
        return gDiskTemplates.indexOf(disk.getDiskTypeName());
    }

    /** 作成可能なトラック数を返す */
    public int getCreatableTracks() {
        return 0;
    }

    /** ファイル名を返す */
    public String getFileName() {
        return filename.getFileName().toString();
    }

    /** ファイル拡張子を返す */
    public String getFileExt() {
        return Utils.getExt(getFileName());
    }

    /** ファイル名ベースを返す */
    public String getFileNameBase() {
        String name = getFileName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    /** ファイルパスを返す */
    public String getFilePath() {
        return filename.toAbsolutePath().toString();
    }

    /** パスを返す */
    public String getPath() {
        return filename.getParent().toString();
    }

    /** ファイル名を設定 */
    public void setFileName(String path) {
        filename = Path.of(path);
    }

    /** ファイル拡張子を設定 */
    public void setFileExt(String ext) {
        filename = filename.getParent().resolve(getFileNameBase() + "." + ext);
    }

    /** ファイルのフォーマット種類を返す */
    public String getFormatType() {
        return formatType;
    }

    /** ファイルのフォーマット種類を設定 */
    public void setFormatType(String formatType) {
        this.formatType = formatType;
    }

    /** DISK BASICが一致するか */
    public boolean matchDiskBasic(DiskBasic target) {
        boolean match = false;
        List<DiskImageDisk> disks = getDisks();
        if (disks == null) return false;
        for (DiskImageDisk disk : disks) {
            List<DiskBasic> basics = disk.getDiskBasics();
            if (basics == null) return false;
            for (DiskBasic basic : basics) {
                if (target == basic) {
                    match = true;
                    break;
                }
            }
        }
        return match;
    }

    /** DISK BASICの解析状態をクリア */
    public void clearDiskBasicParseAndAssign(int diskNumber, int sideNumber) {
        DiskImageDisk disk = getDisk(diskNumber);
        if (disk == null) return;

        if (file != null) {
            file.setBasicTypeHint("");
        }

        List<DiskBasic> basics = disk.getDiskBasics();
        if (basics == null) return;
        clearParseAndAssign(basics, sideNumber);
    }

    /** キャラクターコードマップ番号設定 */
    public void setCharCode(String name) {
        List<DiskImageDisk> disks = getDisks();
        if (disks == null) return;
        for (DiskImageDisk disk : disks) {
            disk.setCharCode(name);
        }
    }

    /** 密度文字列を返す */
    public int getDensityNames(List<String> arr) {
        return 0;
    }

    /** 密度リストを検索 */
    public int findDensity(int val) {
        return -1;
    }

    /** 密度リストを検索 */
    public int findDensityByIndex(int idx) {
        return -1;
    }

    /** 密度リストの指定位置の値を返す */
    public byte getDensity(int idx) {
        return 0;
    }

    /** エラーメッセージ */
    public List<String> getErrorMessage(int maxrow) {
        return result.getMessages(maxrow);
    }

    /** エラーメッセージを表示 */
    public void showErrorMessage() {
        ResultInfo.showMessage(result.getValid(), result.getMessages(-1));
    }

    /** エラー警告メッセージを表示 */
    public int showErrWarnMessage() {
        return ResultInfo.showErrWarnMessage(result.getValid(), result.getMessages(-1));
    }
}
