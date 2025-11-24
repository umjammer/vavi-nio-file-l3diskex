///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.DiskBasicDirItemTRSD13.DirectoryTrsD13;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.DiskBasicDirItemTRSD23.DirectoryTrsD23;
import l3diskex.basicfmt.type.DiskBasicTypeTRSDOS;
import l3diskex.basicfmt.type.DiskBasicTypeTRSDOS.DiskBasicTypeTRSD13;
import l3diskex.basicfmt.type.DiskBasicTypeTRSDOS.DiskBasicTypeTRSD23;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeL31S.FORMAT_TYPE_L3_1S;
import static l3diskex.basicfmt.type.DiskBasicTypeTRSDOS.DiskBasicTypeTRSD23.FORMAT_TYPE_TRSD23;


/**
 * ディレクトリ１アイテム TRSDOS Base
 *
 * @see DiskBasicTypeTRSDOS
 */
public abstract class DiskBasicDirItemTRSDOS<T extends Directory> extends DiskBasicDirItem<T> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * TRSDOS gap
     */
    @Serdes
    public static class TrsDosGap {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte granules;
    }

    // TRSDOS属性位置
    public static final int FILETYPE_MASK_TRSDOS_ACCESS = 0x07;
    public static final int FILETYPE_MASK_TRSDOS_INVISIBLE = 0x08;
    public static final int FILETYPE_MASK_TRSDOS_INUSE = 0x10;
    public static final int FILETYPE_MASK_TRSDOS_SYSTEM = 0x40;
    public static final int FILETYPE_MASK_TRSDOS_OVERFLOW = 0x80;

    /** TRSDOS属性値 */
    static final Map<String, Object> typeNameTrsDos = new LinkedHashMap<>() {{
        put("Invisible", FILETYPE_MASK_TRSDOS_INVISIBLE);
        put("System", FILETYPE_MASK_TRSDOS_SYSTEM);
        put("Overflow", FILETYPE_MASK_TRSDOS_OVERFLOW);
    }};

    /** TRSDOS属性名 */
    static final Map<String, Object> typeNameTrsDos2 = new LinkedHashMap<>() {{
        put("SYS", FILETYPE_MASK_TRSDOS_SYSTEM);
    }};

    /** TRSDOS属性位置 */
    static final int TYPE_NAME_2_TRSDOS_SYS = 0;

    /** 次のエントリ(overflowアリの場合) */
    protected DiskBasicDirItemTRSDOS<T> nextItem;

    /** HITの位置 */
    protected int positionInHit;

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        positionInHit = -1;
        nextItem = null;
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        positionInHit = -1;
        nextItem = null;
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        positionInHit = -1;
        nextItem = null;
    }

    /**
     * アイテムへのポインタを設定
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);
        positionInHit = -1;
    }

    /**
     * 使用しているアイテムか
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        boolean used = false;
        if (positionInHit >= 0) {
            used = (((DiskBasicTypeTRSDOS<T>) type).hit.getHI(positionInHit) != 0);
        }
        used &= ((getFileType1() & FILETYPE_MASK_TRSDOS_INUSE) != 0);
        return used;
    }

    /**
     * ファイル内部のアドレスを取り出す
     */
    protected void takeAddressesInFile(DiskBasicGroups groupItems) {
        if (groupItems.size() == 0) {
            return;
        }
        //DiskBasicGroupItem item = groupItems.get(0);
        //DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        //if (sector == null) return;

        // 開始アドレス
        //startAddress = (int) sector.get16(0);
    }

    /**
     * 属性からリストの位置を返す
     */
    protected int convFileType1Pos(int type1) {
        return 0;
    }

    /**
     * 削除
     */
    @Override
    public boolean delete() throws IOException {
        // 削除
        used(false);
        setFileType1(0);
        // GATのエントリを削除
        type.deleteGroups(groups);
        // HITのエントリも削除
        if (positionInHit >= 0) {
            ((DiskBasicTypeTRSDOS<T>) type).hit.deleteHI(positionInHit);
        }
        // Overflowがあるとき
        if (nextItem != null) {
            nextItem.delete();
            nextItem = null;
        }
        return true;
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOSから
            int t1 = fileType.getOrigin(0);

            setFileType1(t1);
        } else {
            // 違うOSから
            int t1 = FILETYPE_MASK_TRSDOS_INUSE;

            if ((fType & FILE_TYPE_HIDDEN_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_TRSDOS_INVISIBLE;
            }
            if ((fType & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_TRSDOS_SYSTEM;
            }

            setFileType1(t1);
        }
    }

    /**
     * 属性を返す
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int t1 = getFileType1();

        if ((t1 & FILETYPE_MASK_TRSDOS_INVISIBLE) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((t1 & FILETYPE_MASK_TRSDOS_SYSTEM) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    /**
     * HITの位置をセット
     */
    public void setPositionInHIT(byte val) {
        positionInHit = val;
    }

    /**
     * HITの位置を返す
     */
    public byte getPositionInHIT() {
        return (byte) positionInHit;
    }

    /**
     * 次のアイテムをセット
     */
    public void setNextItem(DiskBasicDirItem<T> val) {
        nextItem = (DiskBasicDirItemTRSDOS<T>) val;
    }

    /**
     * 次のアイテムを返す
     */
    public DiskBasicDirItemTRSDOS<T> getNextItem() {
        return nextItem;
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     */
    @Override
    public String getFileAttrStr() {
        StringBuilder str = new StringBuilder();
        int val = getFileType1();
        for (int i = 0; i < typeNameTrsDos.size(); i++) {
            if ((val & (int) Utils.valueAt(typeNameTrsDos, i)) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(Utils.keyAt(typeNameTrsDos, i)));
            }
        }
        return str.toString();
    }

    /**
     * 最終セクタのサイズを計算してファイルサイズを返す
     */
    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) {
        return occupiedSize;
    }

    /**
     * 最初のグループ番号をセット
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        setGranulesOnGap(0, val, size);
    }

    /**
     * 最初のグループ番号を返す
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return getGranulesOnGap(0, new int[1]);
    }

    /**
     * Overflowをセット
     */
    public void setOverflow(byte val) {
    }

    /**
     * Overflowを返す
     */
    public int getOverflow() {
        return 0;
    }

    /**
     * GAPのGranule番号をセット
     */
    public void setGranulesOnGap(int pos, int val, int cnt) {
    }

    /**
     * GAPのGranule番号をクリア
     */
    public void clearGranulesOnGap(int pos, int track, int granule) {
    }

    /**
     * GAPのGranule番号を返す
     */
    public int getGranulesOnGap(int pos, int[] cnt /* = {0} */) {
        return 0;
    }

    /**
     * 新規ファイルとして設定
     */
    public void setAsNewFile() {
    }

    /**
     * Overflowファイルとして設定
     */
    public void setAsOverflowFile(byte positionInHit, byte hashCode) {
    }

    /**
     * "BOOT/SYS"として設定
     */
    public void setAsBootSysEntry() {
    }

    /**
     * "DIR/SYS"として設定
     */
    public void setAsDirSysEntry() {
    }

    /**
     * ファイルの終端コードをチェックする必要があるか
     */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /**
     * データをインポートする前に必要な処理
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        // 拡張子前の'.'を'/'に置き換える
        int pos = filename[0].indexOf('.');
        if (pos != -1) {
            filename[0] = filename[0].substring(0, pos) + basic.getExtensionPreCode() + filename[0].substring(pos + 1);
        }
        return true;
    }

    /**
     * ファイル名から属性を決定する
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        // 拡張子で属性を設定する
        isContainAttrByExtension(filename, typeNameTrsDos2, 0, TYPE_NAME_2_TRSDOS_SYS, null, t1, null);
        return t1[0];
    }

    /**
     * アイテムを削除できるか
     */
    @Override
    public boolean isDeletable() {
        int sType = getFileType1();
        return sType != FILETYPE_MASK_TRSDOS_SYSTEM;
    }

    /**
     * アイテムの属するセクタを変更済みにする
     */
    @Override
    public void setModify() {
        //sdata.copyFrom(data.data());
    }

    /**
     * 属性１を返す
     */
    @Override
    public abstract int getFileType1();

    /**
     * 属性１を設定
     */
    @Override
    protected abstract void setFileType1(int val);

    /**
     * ディレクトリ１アイテム TRSDOS 2.x
     *
     * @see DiskBasicTypeTRSD23
     */
    public static class DiskBasicDirItemTRSD23 extends DiskBasicDirItemTRSDOS<DirectoryTrsD23> {

        /**
         * ディレクトリエントリ TRSDOS 2.x (32bytes)
         */
        @Serdes
        public static class DirectoryTrsD23 implements Directory {

            @Element(sequence = 1)
            public byte accessControl;
            @Element(sequence = 2)
            public byte overflow;
            @Element(sequence = 3)
            public byte reserved1;
            @Element(sequence = 4)
            public byte eofByteOffset;
            @Element(sequence = 5)
            public byte recordLength;
            @Element(sequence = 6)
            public byte[] name = new byte[8];
            @Element(sequence = 7)
            public byte[] ext = new byte[3];
            @Element(sequence = 8)
            public short updatePassword;
            @Element(sequence = 9)
            public short accessPassword;
            @Element(sequence = 10)
            public short eofSector;
            @Element(sequence = 11)
            public TrsDosGap[] gap = new TrsDosGap[5];

            public DirectoryTrsD23() {
                for (int i = 0; i < 5; i++) {
                    gap[i] = new TrsDosGap();
                }
            }

            public static final int SIZE = 32;
        }

        /** ディレクトリデータ */
        protected DiskBasicDirData<DirectoryTrsD23> data = new DiskBasicDirData<>();

        @Override
        public boolean isSupported(int formatType) {
            return formatType == FORMAT_TYPE_TRSD23;
        }

        @Override
        public void init(DiskBasic basic) throws IOException {
            super.init(basic);

            data.alloc(DirectoryTrsD23.class);
        }

        @Override
        public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
            super.init(basic, sector, sectorPos, data, dataP);

            this.data.attach(DirectoryTrsD23.class, data, dataP);
            if (sector != null) {
                positionInHit = DiskBasicTypeTRSD23.getHIPosition(sector.getSectorNumber() - basic.getSectorNumberBase(), sectorPos / getDataSize());
            }
        }

        @Override
        public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                         byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
            super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

            this.data.attach(DirectoryTrsD23.class, data, dataP);
            positionInHit = DiskBasicTypeTRSD23.getHIPosition(sector.getSectorNumber() - basic.getSectorNumberBase(), sectorPos / getDataSize());

            used(checkUsed(unuse[0]));
        }

        /**
         * アイテムへのポインタを設定
         */
        @Override
        public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next) throws IOException {
            super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

            this.data.attach(DirectoryTrsD23.class, data, dataPos);
            positionInHit = DiskBasicTypeTRSD23.getHIPosition(sector.getSectorNumber() - basic.getSectorNumberBase(), sectorPos / getDataSize());
        }

        /**
         * ディレクトリアイテムのチェック
         */
        @Override
        public boolean check(boolean[] last) {
            if (!data.isValid()) return false;

            int overflow = getOverflow() & 0xff;
            if (overflow > 0 && overflow < 254) {
                // 参照元アイテムと関連付ける
                int[] overflowSectorNum = {0};
                int[] overflowSectorPos = {0};
                ((DiskBasicTypeTRSD23) type).getFromHIPosition(overflow, overflowSectorNum, overflowSectorPos);
                // 通し番号を計算
                int num = (overflowSectorNum[0] - 2) * basic.getSectorSize() / getDataSize() + overflowSectorPos[0];
                int maxNum = (basic.getDirEndSector() - basic.getDirStartSector() + 1) * basic.getSectorSize() / getDataSize();
                if (num >= maxNum) {
                    // invalid chain
                    return false;
                }
            }
            return true;
        }

        /**
         * 属性１を返す
         */
        @Override
        public int getFileType1() {
            return data.data().accessControl & 0xff;
        }

        /**
         * 属性１を設定
         */
        @Override
        protected void setFileType1(int val) {
            data.data().accessControl = (byte) (val & 0xff);
        }

        /**
         * Overflowをセット
         */
        @Override
        public void setOverflow(byte val) {
            data.data().overflow = (byte) (val & 0xff);
        }

        /**
         * Overflowを返す
         */
        @Override
        public int getOverflow() {
            return data.data().overflow & 0xff;
        }

        /**
         * ファイル名を格納する位置を返す
         */
        @Override
        public byte[] getFileNamePos(int num, int[] size, int[] len) {
            if (num == 0) {
                size[0] = len[0] = data.data().name.length;
                return data.data().name;
            } else {
                size[0] = len[0] = 0;
                return null;
            }
        }

        /**
         * 拡張子を格納する位置を返す
         */
        @Override
        public byte[] getFileExtPos(int[] len) {
            len[0] = data.data().ext.length;
            return data.data().ext;
        }

        /**
         * 新規ファイルとして設定
         */
        @Override
        public void setAsNewFile() {
            used(true);
            setFileType1(getFileType1() | FILETYPE_MASK_TRSDOS_INUSE);
            // HITエントリに登録
            byte h = DiskBasicTypeTRSD23.computeHI(data.data().name);
            if (positionInHit >= 0) {
                ((DiskBasicTypeTRSDOS<DirectoryTrsD23>) type).hit.setHI(positionInHit, h);
            }
            // パスワード
            data.data().accessPassword = data.data().updatePassword = (short) 0x4296;

            // エントリのクリア
            for (int pos = 0; pos < data.data().gap.length; pos++) {
                clearGranulesOnGap(pos, 0xff, 0xff);
            }
        }

        /**
         * Overflowファイルとして設定
         */
        @Override
        public void setAsOverflowFile(byte positionInHit, byte hashCode) {
            clearData();

            used(true);
            visible(false);

            setFileType1(FILETYPE_MASK_TRSDOS_OVERFLOW | FILETYPE_MASK_TRSDOS_INUSE);
            // HITエントリに登録
            if (this.positionInHit >= 0) {
                ((DiskBasicTypeTRSDOS<DirectoryTrsD23>) type).hit.setHI(this.positionInHit, hashCode);
            }
            setOverflow(positionInHit);
            // パスワード
            //data.data().accessPassword = data.data().updatePassword = 0x4296;

            // エントリのクリア
            for (int pos = 0; pos < data.data().gap.length; pos++) {
                clearGranulesOnGap(pos, 0xff, 0xff);
            }
        }

        /**
         * "BOOT/SYS"として設定
         */
        @Override
        public void setAsBootSysEntry() {
            setFileNameStr("BOOT/SYS");
            setAsNewFile();
            setFileType1(FILETYPE_MASK_TRSDOS_SYSTEM | FILETYPE_MASK_TRSDOS_INUSE | FILETYPE_MASK_TRSDOS_INVISIBLE | 6);
            setStartGroup(0, 1, 0);
            setFileSize(basic.getSectorSize());
        }

        /**
         * "DIR/SYS"として設定
         */
        @Override
        public void setAsDirSysEntry() {
            setFileNameStr("DIR/SYS");
            setAsNewFile();
            setFileType1(FILETYPE_MASK_TRSDOS_SYSTEM | FILETYPE_MASK_TRSDOS_INUSE | FILETYPE_MASK_TRSDOS_INVISIBLE | 5);
            setStartGroup(0, basic.getManagedTrackNumber() * basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic(), basic.getGroupsPerTrack());
            setFileSize(basic.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic() * basic.getSectorSize());
        }

        /**
         * ファイルサイズとグループ数を計算する
         */
        @Override
        public void calcFileUnitSize(int fileUnitNum) throws IOException {
            if (!isUsed()) return;

            getUnitGroups(fileUnitNum, groups);
        }

        /**
         * 指定ディレクトリのすべてのグループを取得
         */
        @Override
        public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
            int calcGroups = 0;
            int calcFileSize = 0;

            int sector_size = basic.getSectorSize();
            int block_size = sector_size * basic.getSectorsPerGroup();
            int maxGroup = basic.getFatEndGroup();

            int remainSize = getFileSize();

            for (int pos = 0; pos < data.data().gap.length; pos++) {
                int[] count = {0};
                int groupNum = getGranulesOnGap(pos, count);
                if (groupNum >= maxGroup) break;

                for (int i = 0; i < count[0]; i++) {
                    basic.getNumsFromGroup(groupNum, 0, sector_size, remainSize, groupItems);
                    groupNum++;
                    calcGroups++;
                    calcFileSize += block_size;
                    remainSize -= block_size;
                }
            }
            // overflowがあるとき
            if (nextItem != null) {
                nextItem.getUnitGroups(fileUnitNum, groupItems);
            }

            groupItems.addNums(calcGroups);
            groupItems.addSize(calcFileSize);
            groupItems.setSizePerGroup(block_size);

            // ファイル内部のアドレスを得る
            takeAddressesInFile(groupItems);
        }

        /**
         * ファイルサイズをセット
         */
        @Override
        public void setFileSize(int val) {
            int quotient = val / basic.getSectorSize();
            int remainder = val % basic.getSectorSize();
            if (remainder != 0) {
                quotient++;
            }
            data.data().eofSector = (short) quotient;
            data.data().eofByteOffset = (byte) (remainder & 0xff);
        }

        /**
         * ファイルサイズを返す
         */
        @Override
        public int getFileSize() {
            int val = (data.data().eofSector & 0xffff) * basic.getSectorSize();
            if (data.data().eofByteOffset != 0) {
                val -= basic.getSectorSize();
                val += data.data().eofByteOffset & 0xff;
            }
            return val;
        }

        /**
         * GAPのGranule番号をセット
         */
        @Override
        public void setGranulesOnGap(int pos, int val, int cnt) {
            int block = basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
            int track = val / block;
            int start = val % block;
            data.data().gap[pos].track = (byte) (track & 0xff);
            data.data().gap[pos].granules = (byte) (((start << 5) & 0xe0) | ((cnt & 0x1f) - 1));
        }

        /**
         * GAPのGranule番号をクリア
         */
        @Override
        public void clearGranulesOnGap(int pos, int track, int granule) {
            data.data().gap[pos].track = (byte) (track & 0xff);
            data.data().gap[pos].granules = (byte) (granule & 0xff);
        }

        /**
         * GAPのGranule番号を返す
         */
        @Override
        public int getGranulesOnGap(int pos, int[] cnt) {
            int val = data.data().gap[pos].track * basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
            int start = (data.data().gap[pos].granules & 0xe0) >> 5;
            val += start;
            if (cnt != null && cnt.length > 0) {
                cnt[0] = (data.data().gap[pos].granules & 0x1f) + 1;
            }
            return val;
        }

        /**
         * ディレクトリアイテムのサイズ
         */
        @Override
        public int getDataSize() {
            return data.getDataSize();
        }

        /**
         * アイテムを返す
         */
        @Override
        public DirectoryTrsD23 getData() {
            return data.data();
        }

        /**
         * アイテムをコピー
         */
        @Override
        public boolean copyData(byte[] val) {
            return data.copy(val);
        }

        /**
         * ディレクトリをクリア
         */
        @Override
        public void clearData() {
            data.fill(0);
        }

        /**
         * プロパティで表示する内部データを設定
         */
        @Override
        public void setInternalDataInAttrDialog(KeyValArray vals) {
            vals.add("ACCESS_CONTROL", data.data().accessControl);
            vals.add("OVERFLOW", data.data().overflow);
            vals.add("EOF_BYTE_OFFSET", data.data().eofByteOffset);
            vals.add("RECORD_LENGTH", data.data().recordLength);
            vals.add("FILE_NAME", data.data().name, data.data().name.length);
            vals.add("EXTENSION", data.data().ext, data.data().ext.length);
            vals.add("UPDATE_PASSWORD", data.data().updatePassword);
            vals.add("ACCESS_PASSWORD", data.data().accessPassword);
            vals.add("EOF_SECTOR", data.data().eofSector);
            for (int i = 0; i < data.data().gap.length; i++) {
                vals.add(String.format("GAP%d TRACK", i + 1), data.data().gap[i].track);
                vals.add(String.format("GAP%d GRANULES", i + 1), data.data().gap[i].granules);
            }
        }
    }

    /**
     * ディレクトリ１アイテム TRSDOS 1.3
     *
     * @see DiskBasicTypeTRSD13
     */
    public static class DiskBasicDirItemTRSD13 extends DiskBasicDirItemTRSDOS<DirectoryTrsD13> {

        /**
         * ディレクトリエントリ TRSDOS 1.3 (48bytes)
         */
        public static class DirectoryTrsD13 implements Directory {

            public byte accessControl;
            public byte month; // 0x01 - 0x0c
            public byte year;
            public byte eofByteOffset;
            public byte recordLength;
            public byte[] name = new byte[8];
            public byte[] ext = new byte[3];
            public short updatePassword;
            public short accessPassword;
            public short eofSector;
            public TrsDosGap[] gap = new TrsDosGap[13];

            public DirectoryTrsD13() {
                for (int i = 0; i < 13; i++) {
                    gap[i] = new TrsDosGap();
                }
            }

            public static final int SIZE = 48;
        }

        /** ディレクトリデータ */
        protected DiskBasicDirData<DirectoryTrsD13> data = new DiskBasicDirData<>();

        public int getHIPosition(int pos) {
            return pos & 0xff;
        }

        @Override
        public boolean isSupported(int formatType) {
            return formatType == FORMAT_TYPE_L3_1S;
        }

        @Override
        public void init(DiskBasic basic) throws IOException {
            super.init(basic);
        }

        @Override
        public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
            super.init(basic, sector, sectorPos, data, dataP);

            this.data.attach(DirectoryTrsD13.class, data, dataP);
            if (sector != null) {
                int n = (basic.getSectorSize() / getDataSize());
                positionInHit = getHIPosition((sector.getSectorNumber() - basic.getSectorNumberBase() - 2) * n + (sectorPos / getDataSize()));
            }
        }

        @Override
        public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                         byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
            super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

            this.data.attach(DirectoryTrsD13.class, data, dataP);
            positionInHit = getHIPosition(num);

            used(checkUsed(unuse[0]));
        }

        /**
         * アイテムへのポインタを設定
         */
        @Override
        public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                            byte[] data, int dataPos, SectorParam next) throws IOException {
            super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

            this.data.attach(DirectoryTrsD13.class, data, dataPos);
            positionInHit = getHIPosition(num);
        }

        /**
         * ディレクトリアイテムのチェック
         */
        @Override
        public boolean check(boolean[] last) {
            if (!data.isValid()) return false;

            return true;
        }

        /**
         * アイテムが作成日時を持っているか
         */
        @Override
        public boolean hasCreateDateTime() {
            return true;
        }

        /**
         * アイテムが作成日付を持っているか
         */
        @Override
        public boolean hasCreateDate() {
            return true;
        }

        /**
         * 作成日付を得る
         *
         * @return
         */
        @Override
        public LocalDate getFileCreateDate(LocalDateTime tm) {
            int yy = data.data().year & 0xff;
            if (yy < 80) {
                yy += 100;
            }
            return LocalDate.of(
                    yy,
                    data.data().month - 1,
                    1);
        }

        /**
         * 作成日付を返す
         */
        @Override
        public String getFileCreateDateStr() {
            LocalDateTime tm = LocalDateTime.now();
            LocalDate ld = getFileCreateDate(tm);
            return Utils.formatYMDStr(ld);
        }

        /**
         * 作成日付をセット
         */
        @Override
        public void setFileCreateDate(LocalDateTime tm) {
            data.data().year = (byte) (tm.getYear() & 0xff);
            data.data().month = (byte) ((tm.getMonth().ordinal() + 1) & 0xff);
        }

        /**
         * ファイル名を格納する位置を返す
         */
        @Override
        public byte[] getFileNamePos(int num, int[] size, int[] len) {
            if (num == 0) {
                size[0] = len[0] = data.data().name.length;
                return data.data().name;
            } else {
                size[0] = len[0] = 0;
                return null;
            }
        }

        /**
         * 拡張子を格納する位置を返す
         */
        @Override
        public byte[] getFileExtPos(int[] len) {
            len[0] = data.data().ext.length;
            return data.data().ext;
        }

        /**
         * 属性１を返す
         */
        @Override
        public int getFileType1() {
            return data.data().accessControl & 0xff;
        }

        /**
         * 属性１を設定
         */
        @Override
        protected void setFileType1(int val) {
            data.data().accessControl = (byte) (val & 0xff);
        }

        /**
         * 新規ファイルとして設定
         */
        @Override
        public void setAsNewFile() {
            used(true);
            setFileType1(getFileType1() | FILETYPE_MASK_TRSDOS_INUSE);
            // HITエントリに登録
            byte h = DiskBasicTypeTRSD23.computeHI(data.data().name);
            if (positionInHit >= 0) {
                ((DiskBasicTypeTRSDOS<DirectoryTrsD13>) type).hit.setHI(positionInHit, h);
            }
            // パスワード
            data.data().accessPassword = data.data().updatePassword = (short) 0x5cef;

            // エントリのクリア
            for (int pos = 0; pos < data.data().gap.length; pos++) {
                clearGranulesOnGap(pos, 0xff, 0xff);
            }
        }

        /**
         * ファイルサイズとグループ数を計算する
         */
        @Override
        public void calcFileUnitSize(int fileUnitNum) throws IOException {
            if (!isUsed()) return;

            getUnitGroups(fileUnitNum, groups);
        }

        /**
         * 指定ディレクトリのすべてのグループを取得
         */
        @Override
        public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
            int calcGroups = 0;
            int calcFileSize = 0;

            int sectorSize = basic.getSectorSize();
            int blockSize = sectorSize * basic.getSectorsPerGroup();
            int maxGroup = basic.getFatEndGroup();

            int remainSize = getFileSize();

            for (int pos = 0; pos < data.data().gap.length; pos++) {
                int[] count = {0};
                int groupNum = getGranulesOnGap(pos, count);
                if (groupNum >= maxGroup) break;

                for (int i = 0; i < count[0]; i++) {
                    basic.getNumsFromGroup(groupNum, 0, sectorSize, remainSize, groupItems);
                    groupNum++;
                    calcGroups++;
                    calcFileSize += blockSize;
                    remainSize -= blockSize;
                }
            }
            // overflowがあるとき
            if (nextItem != null) {
                nextItem.getUnitGroups(fileUnitNum, groupItems);
            }

            groupItems.addNums(calcGroups);
            groupItems.addSize(calcFileSize);
            groupItems.setSizePerGroup(blockSize);

            // ファイル内部のアドレスを得る
            takeAddressesInFile(groupItems);
        }

        /**
         * ファイルサイズをセット
         */
        @Override
        public void setFileSize(int val) {
            int quotient = (val >>> 8);
            data.data().eofSector = (short) quotient;
            data.data().eofByteOffset = (byte) (val & 0xff);
        }

        /**
         * ファイルサイズを返す
         */
        @Override
        public int getFileSize() {
            int val = data.data().eofSector & 0xffff;
            val <<= 8;
            val |= data.data().eofByteOffset & 0xff;
            return val;
        }

        /**
         * GAPのGranule番号をセット
         */
        @Override
        public void setGranulesOnGap(int pos, int val, int cnt) {
            int block = basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
            int track = val / block;
            int start = val % block;
            data.data().gap[pos].track = (byte) (track & 0xff);
            data.data().gap[pos].granules = (byte) (((start << 5) & 0xe0) | (cnt & 0x1f));
        }

        /**
         * GAPのGranule番号をクリア
         */
        @Override
        public void clearGranulesOnGap(int pos, int track, int granule) {
            data.data().gap[pos].track = (byte) (track & 0xff);
            data.data().gap[pos].granules = (byte) (granule & 0xff);
        }

        /**
         * GAPのGranule番号を返す
         */
        @Override
        public int getGranulesOnGap(int pos, int[] cnt) {
            int val = (data.data().gap[pos].track & 0xff) * basic.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
            int start = ((data.data().gap[pos].granules & 0xe0) >> 5);
            val += start;
            if (cnt != null && cnt.length > 0) {
                cnt[0] = data.data().gap[pos].granules & 0x1f;
            }
            return val;
        }

        /**
         * ディレクトリアイテムのサイズ
         */
        @Override
        public int getDataSize() {
            return data.getDataSize();
        }

        /**
         * アイテムを返す
         */
        @Override
        public DirectoryTrsD13 getData() {
            return data.data();
        }

        /**
         * アイテムをコピー
         */
        @Override
        public boolean copyData(byte[] val) {
            return data.copy(val);
        }

        /**
         * ディレクトリをクリア
         */
        @Override
        public void clearData() {
            data.fill(0);
        }

        /**
         * プロパティで表示する内部データを設定
         */
        @Override
        public void setInternalDataInAttrDialog(KeyValArray vals) {
            vals.add("ACCESS_CONTROL", data.data().accessControl);
            vals.add("MONTH", data.data().month);
            vals.add("YEAR", data.data().year);
            vals.add("EOF_BYTE_OFFSET", data.data().eofByteOffset);
            vals.add("RECORD_LENGTH", data.data().recordLength);
            vals.add("FILE_NAME", data.data().name, data.data().name.length);
            vals.add("EXTENSION", data.data().ext, data.data().ext.length);
            vals.add("UPDATE_PASSWORD", data.data().updatePassword);
            vals.add("ACCESS_PASSWORD", data.data().accessPassword);
            vals.add("EOF_SECTOR", data.data().eofSector);
            for (int i = 0; i < data.data().gap.length; i++) {
                vals.add(String.format("GAP%d TRACK", i + 1), data.data().gap[i].track);
                vals.add(String.format("GAP%d GRANULES", i + 1), data.data().gap[i].granules);
            }
        }
    }
}
