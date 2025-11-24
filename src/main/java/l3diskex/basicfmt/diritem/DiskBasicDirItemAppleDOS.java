///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.DirectoryAppleDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_INTEGER_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.AppleDosChain.APLEDOS_TRACK_LIST_MAX;
import static l3diskex.basicfmt.type.DiskBasicTypeAppleDOS.FORMAT_TYPE_APLEDOS;


/**
 * ディレクトリ１アイテム Apple DOS 3.x
 */
public class DiskBasicDirItemAppleDOS extends DiskBasicDirItem<DirectoryAppleDos> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ Apple DOS (35bytes)
     */
    @Serdes
    public static class DirectoryAppleDos implements Directory {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte[] name = new byte[30];
        @Element(sequence = 5)
        public short sectorCount; // size (little endien)

        public static final int SIZE = 35;
    }

    /**
     * Apple DOS top of each sector
     */
    @Serdes
    public static class AppleDosPointer {

        public static final int SIZE = 3;

        @Element(sequence = 1)
        public byte reserved;
        @Element(sequence = 2)
        public byte nextTrack;
        @Element(sequence = 3)
        public byte nextSector;

        public byte[] serialize() {
            return new byte[]{reserved, nextTrack, nextSector};
        }
    }

    /**
     * ディレクトリエントリ Apple ProDOS (39bytes)
     */
    @Serdes
    public static class DirectoryProDos implements Directory {

        @Element(sequence = 1)
        public byte sTypeAndNLen;
        @Element(sequence = 2)
        public byte[] name = new byte[15];
        @Element(sequence = 3)
        public byte fileType; // file only
        @Element(sequence = 4)
        public short keyPointer; // file only
        @Element(sequence = 5)
        public short blocksUsed; // file only
        @Element(sequence = 6)
        public byte[] eof = new byte[3]; // file only
        @Element(sequence = 7)
        public byte[] cDate = new byte[2];
        @Element(sequence = 8)
        public byte[] cTime = new byte[2];
        @Element(sequence = 9)
        public byte version; // byte
        @Element(sequence = 10)
        public byte minVersion; // byte
        @Element(sequence = 11)
        public byte access; // byte

        // Union for the variant part
        public ProdosAux aux = new ProdosAux();

        public static class ProdosAux {

            public V v = new V();
            public Sv sv = new Sv();
            public F f = new F();
        }

        public static class V {

            public byte entryLen;
            public byte entriesPerBlock;
            public short fileCount;
            public short bitmapPointer;
            public short totalBlocks;
        }

        public static class Sv {

            public byte entryLen;
            public byte entriesPerBlock;
            public short fileCount;
            public short parentPointer;
            public byte parentEntry;
            public byte parentEntryLen;
        }

        public static class F {

            public short auxType; // aux type
            public byte[] mDate = new byte[2];
            public byte[] mTime = new byte[2];
            public short headerPointer;
        }

        public static final int SIZE = 39;
    }

    /// Apple DOS属性名
    public static final Map<String, Object> typeNameAppleDOS = new LinkedHashMap<>() {{
        put("Text", FILETYPE_MASK_APLEDOS_TEXT);
        put("Integer BASIC", FILETYPE_MASK_APLEDOS_IBASIC);
        put("Applesoft BASIC", FILETYPE_MASK_APLEDOS_ABASIC);
        put("Binary", FILETYPE_MASK_APLEDOS_BINARY);
        put("Read Only", FILETYPE_MASK_APLEDOS_READ_ONLY);
    }};

    /*
     * Apple DOS属性位置
     */
    static final int TYPE_NAME_APLEDOS_TEXT = 0;
    // Integer BASIC
    static final int TYPE_NAME_APLEDOS_IBASIC = 1;
    // Applesoft BASIC
    static final int TYPE_NAME_APLEDOS_ABASIC = 2;
    static final int TYPE_NAME_APLEDOS_BINARY = 3;
    static final int TYPE_NAME_APLEDOS_READ_ONLY = 4;

    /**
     * Apple DOS属性値
     */
    static final int FILETYPE_MASK_APLEDOS_TEXT = 0x00;
    static final int FILETYPE_MASK_APLEDOS_IBASIC = 0x01;
    static final int FILETYPE_MASK_APLEDOS_ABASIC = 0x02;
    static final int FILETYPE_MASK_APLEDOS_BINARY = 0x04;
    static final int FILETYPE_MASK_APLEDOS_READ_ONLY = 0x80;

    /**
     * Apple DOS トラックセクタリスト情報 256bytes
     */
    @Serdes
    public static class AppleDosChain {

        private static final int SIZE = 256;

        public static final int APLEDOS_TRACK_LIST_MAX = 122;

        @Element(sequence = 1)
        public AppleDosPointer next;
        @Element(sequence = 2)
        byte[] reserved1 = new byte[2];
        @Element(sequence = 3)
        short number;
        @Element(sequence = 4)
        byte[] reserved2 = new byte[5];
        static class TrackList {
            byte track;
            byte sector;
        }
        @Element(sequence = 5)
        TrackList[] list = new TrackList[APLEDOS_TRACK_LIST_MAX];
    }

    //
    // Apple DOS トラックセクタリスト
    //
    static class DiskBasicDirItemAppleDosChain {

        private DiskBasic basic;
        private final List<AppleDosChain> chains;
        private AppleDosChain chain;
        private DiskImageSector sector;

        public DiskBasicDirItemAppleDosChain() {
            chains = new ArrayList<>();
            basic = null;
        }

        /** BASICをセット */
        public void setBasic(DiskBasic n_basic) {
            basic = n_basic;
        }

        /** ポインタをセット */
        public void add(AppleDosChain n_chain) {
            chains.add(n_chain);
        }

        /** クリア */
        public void clear() {
            chains.clear();
        }

        /** セクタ数を返す */
        public int count() {
            return chains.size();
        }

        /** 有効か */
        public boolean isValid() {
            return !chains.isEmpty();
        }

        /** トラック＆セクタを返す */
        public void getTrackAndSector(int idx, int[] track, int[] sector) {
            int max_idx = APLEDOS_TRACK_LIST_MAX;
            for (AppleDosChain item : chains) {
                if (idx < max_idx) {
                    track[0] = item.list[idx].track & 0xff;
                    sector[0] = item.list[idx].sector & 0xFF;
                    track[0] += basic.getTrackNumberBaseOnDisk();
                    sector[0] += basic.getSectorNumberBase();
                    break;
                }
                idx -= max_idx;
            }
        }

        /** トラック＆セクタを設定 */
        public void setTrackAndSector(int idx, int track, int sector) {
            int max_idx = APLEDOS_TRACK_LIST_MAX;
            for (AppleDosChain item : chains) {
                if (idx < max_idx) {
                    track -= basic.getTrackNumberBaseOnDisk();
                    sector -= basic.getSectorNumberBase();
                    item.list[idx].track = (byte) (track & 0xff);
                    item.list[idx].sector = (byte) (sector & 0xff);
                    break;
                }
                idx -= max_idx;
            }
        }

        /** 次のセクタのあるセクタ番号を得る */
        public int getNext(int idx) {
            AppleDosChain item = chains.get(idx);
            AppleDosPointer next = item.next;
            return (next.nextTrack & 0xFF) * basic.getSectorsPerTrackOnBasic() + (next.nextSector & 0xFF);
        }

        /** 次のセクタのあるセクタ番号を設定 */
        public void setNext(int idx, int val) {
            AppleDosChain item = chains.get(idx);
            AppleDosPointer next = item.next;
            next.nextTrack = (byte) ((val / basic.getSectorsPerTrackOnBasic()) & 0xFF);
            next.nextSector = (byte) ((val % basic.getSectorsPerTrackOnBasic()) & 0xFF);
            item.next = next;
        }
    }

    //
    // ディレクトリ１アイテム Apple DOS 3.x
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryAppleDos> data = new DiskBasicDirData<>();

    /** ファイル内部で持っている開始アドレス */
    private int startAddress;
    /** ファイル内部で持っているサイズ */
    private int dataLength;

    /** トラック＆セクタリスト */
    private final DiskBasicDirItemAppleDosChain chain = new DiskBasicDirItemAppleDosChain();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_APLEDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        startAddress = -1;
        dataLength = -1;

        data.alloc(DirectoryAppleDos.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);

        startAddress = -1;
        dataLength = -1;

        this.data.attach(DirectoryAppleDos.class, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        startAddress = -1;
        dataLength = -1;

        this.data.attach(DirectoryAppleDos.class, data, dataPos);

        used(checkUsed(unuse[0]));

        // チェインセクタへのポインタをセット
        if (isUsed()) {
            chain.clear();
            chain.setBasic(basic);
            int gourp = getStartGroup(0);
            while (gourp != 0) {
                DiskImageSector sectorNum = basic.getSectorFromGroup(gourp);
                if (sectorNum == null) break;

                byte[] buf = sectorNum.getSectorBuffer();
                AppleDosChain c = new AppleDosChain();
                Serdes.Util.deserialize(new ByteArrayInputStream(buf), c);
                chain.add(c);
                AppleDosPointer p = new AppleDosPointer();
                Serdes.Util.deserialize(new ByteArrayInputStream(buf), p);
                gourp = type.getSectorPosFromNumS((p.nextTrack & 0xff) + basic.getTrackNumberBaseOnDisk(), (p.nextSector & 0xff) + basic.getSectorNumberBase());
            }
        }

        calcFileSize();
    }

    /**
     * アイテムへのポインタを設定
     *
     * @param num    通し番号
     * @param gItem  トラック番号などのデータ
     * @param sector セクタ
     * @param sectorPos セクタ内のディレクトリエントリの位置
     * @param data   ディレクトリアイテム
     * @param dataPos  データポインタ
     * @param next   [out] 次のセクタ
     */
    @Override
    public void setData(int num, DiskBasicGroupItem gItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, gItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryAppleDos.class, data, dataPos);
    }

    /** ファイル名を格納する位置を返す */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = data.data().name.length;
            return data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /**
     * ファイル名を設定
     *
     * filename はデータビットが反転している場合あり
     * @param filename [in,out] ファイル名
     * @param size     バッファサイズ
     * @param length   長さ
     */
    @Override
    protected void setNativeName(byte[] filename, int size, int length) {
        byte[] n;
        int[] nl = {0};
        int[] ns = {0};
        n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            int copySize = ns[0];
            if (copySize > size) copySize = size;
            // ファイル名はMSBをセット
            for (int i = 0; i < copySize; i++) {
                n[i] = (byte) (filename[i] | 0x80);
            }
        }
    }

    /**
     * ファイル名を得る
     *
     * @param filename [in,out] ファイル名
     * @param size     バッファサイズ
     * @param length   [out] 長さ
     */
    @Override
    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n = null;
        int[] s = {0};
        int[] l = {0};

        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            if (s[0] > size) s[0] = size;
            // ファイル名はMSBをはずす
            for (int i = 0; i < s[0]; i++) {
                filename[i] = (byte) (n[i] & 0x7f);
            }
        }
    }

    /** 属性１を返す */
    @Override
    public int getFileType1() {
        return data.data().type & 0xff;
    }

    /** 属性１を設定 */
    @Override
    public void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !(data.data().track == (byte) 0xff || (data.data().track == 0 && data.data().sector == 0));
    }

    /** 削除 */
    @Override
    public boolean delete() {
        // 削除
        used(false);
        // ここで属性は更新しない
        return true;
    }

    /**
     * ディレクトリアイテムのチェック
     *
     * @param last [in,out] チェックを終了するか
     * @return チェックOK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;

        if (data.data().track == 0 && data.data().sector == 0) {
            last[0] = true;
            return valid;
        }
        // 属性 3-6bitはゼロ
        if ((data.data().type & 0x78) != 0) {
            valid = false;
        }
        return valid;
    }

    /** 共通属性を個別属性に変換 */
    public static int convToFileType1(int ftype) {
        int type1 = 0;
        if ((ftype & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
            type1 = FILETYPE_MASK_APLEDOS_ABASIC;
        } else if ((ftype & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) {
            type1 = FILETYPE_MASK_APLEDOS_IBASIC;
        } else if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            type1 = FILETYPE_MASK_APLEDOS_BINARY;
        } else {
            type1 = FILETYPE_MASK_APLEDOS_TEXT;
        }
        if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            type1 |= FILETYPE_MASK_APLEDOS_READ_ONLY;
        }
        return type1;
    }

    /** 個別属性を共通属性に変換 */
    public static int convFromFileType1(int type1) {
        int val = 0;
        if ((type1 & FILETYPE_MASK_APLEDOS_ABASIC) != 0) {
            val = (FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_BASIC_MASK.getValue());
        } else if ((type1 & FILETYPE_MASK_APLEDOS_IBASIC) != 0) {
            val = (FILE_TYPE_INTEGER_MASK.getValue() | FILE_TYPE_BASIC_MASK.getValue());
        } else if ((type1 & FILETYPE_MASK_APLEDOS_BINARY) != 0) {
            val = (FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue());
        } else {
            val = (FILE_TYPE_ASCII_MASK.getValue() | FILE_TYPE_DATA_MASK.getValue());
        }
        if ((type1 & FILETYPE_MASK_APLEDOS_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        return val;
    }

    /** 属性を設定 */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int type1 = convToFileType1(fType);

        setFileType1(type1);
    }

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int type1 = getFileType1();
        int val = convFromFileType1(type1);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, type1);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        String str = "";
        int oval = getFileType1();
        if ((oval & FILETYPE_MASK_APLEDOS_IBASIC) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_IBASIC));
        } else if ((oval & FILETYPE_MASK_APLEDOS_ABASIC) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_ABASIC));
        } else if ((oval & FILETYPE_MASK_APLEDOS_BINARY) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_BINARY));
        } else {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_TEXT));
        }
        if ((oval & FILETYPE_MASK_APLEDOS_READ_ONLY) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_READ_ONLY));
        }
        return str;
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int sectorSize = basic.getSectorSize();
        val = (val + sectorSize - 1) / sectorSize;
        setSectorCount(val + chain.count());
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     *
     * @param fileUnitNum ファイル番号
     * @param groupItems  [out] グループリスト
     */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        if (!chain.isValid()) return;

        int calcGroups = 0;
        int calcFileSize = 0;

        for (int i = 0; ; i++) {
            int[] trackNum = {0};
            int[] sectorNum = {0};
            chain.getTrackAndSector(i, trackNum, sectorNum);
            if (trackNum[0] == 0 && sectorNum[0] == 0) {
                break;
            }
            int groupNum = type.getSectorPosFromNumS(trackNum[0], sectorNum[0]);
            int[] sideNum = {0};
            type.getNumFromSectorPos(groupNum, trackNum, sideNum, sectorNum);
            groupItems.add(groupNum, 0, trackNum[0], sideNum[0], sectorNum[0], sectorNum[0]);
            calcGroups++;
            calcFileSize += basic.getSectorSize();
            if (calcGroups >= basic.getFatEndGroup()) {
                // too large block size
                break;
            }
        }
        calcGroups += chain.count();
        if (getSectorCount() != calcGroups) {
            calcGroups = getSectorCount();
            calcFileSize = calcGroups * basic.getSectorSize();
        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize());

        // 最終セクタの再計算
        groupItems.setSize(recalcFileSize(groupItems, (int) groupItems.getSize()));

        // ファイル内部のアドレスを得る
        takeAddressesInFile(groupItems);
    }

    /**
     * 最終セクタのサイズを計算してファイルサイズを返す
     *
     * @param groupItems   グループリスト
     * @param occupiedSize 占有サイズ
     * @return 計算後のファイルサイズ
     */
    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        if (groupItems.size() == 0) return occupiedSize;

        DiskBasicGroupItem lastItem = groupItems.last();
        DiskImageSector sector = basic.getSector(lastItem.track, lastItem.side, lastItem.sectorEnd);
        if (sector == null) return occupiedSize;

        int sectorSize = sector.getSectorSize();
        byte[] buf = sector.getSectorBuffer();
        int remainSize = type.calcDataSizeOnLastSector(this, null, null, buf, 0, sectorSize, sectorSize);

        occupiedSize = occupiedSize - sectorSize + remainSize;

        return occupiedSize;
    }

    /** ファイル内部のアドレスを取り出す */
    public void takeAddressesInFile(DiskBasicGroups groupItems) {
        startAddress = -1;
        dataLength = -1;

        if (groupItems.size() == 0) {
            return;
        }

        DiskBasicGroupItem item = groupItems.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;

        int t1 = getFileType1();

        if ((t1 & FILETYPE_MASK_APLEDOS_BINARY) != 0) {
            // バイナリ
            // 開始アドレス
            startAddress = sector.get16(0);
            // データサイズ
            dataLength = sector.get16(2);
            // 実際のサイズを設定
            if (dataLength + 5 <= groupItems.getSize()) groupItems.setSize(dataLength + 5);
        } else if ((t1 & (FILETYPE_MASK_APLEDOS_IBASIC | FILETYPE_MASK_APLEDOS_ABASIC)) != 0) {
            // BASICファイルサイズ
            // データサイズ => 最終データ位置みたい
            dataLength = sector.get16(0);
            // 実際のサイズを設定
            if (dataLength + 3 <= groupItems.getSize()) groupItems.setSize(dataLength + 3);
        }
    }

    /**
     * 最初のグループ番号を設定
     *
     * @param fileUnitNum ファイル番号 (未使用)
     * @param val          グループ番号
     * @param size         ファイルサイズ (未使用)
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        type.getNumFromSectorPosS(val, trackNum, sectorNum);
        data.data().track = (byte) ((trackNum[0] - basic.getTrackNumberBaseOnDisk()) & 0xff);
        data.data().sector = (byte) ((sectorNum[0] - basic.getSectorNumberBase()) & 0xff);
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileUnitNum) {
        int val = type.getSectorPosFromNumS((data.data().track & 0xff) + basic.getTrackNumberBaseOnDisk(), (data.data().sector & 0xff) + basic.getSectorNumberBase());
        return val;
    }

    /** 追加のグループ番号を返す(機種依存) */
    @Override
    public int getExtraGroup() {
        return getStartGroup(0);
    }

    /** 追加のグループ番号を得る(機種依存) */
    @Override
    public void getExtraGroups(List<Integer> result) {
        int groupNum = getExtraGroup();
        for (int i = 0; i < chain.count(); i++) {
            result.add(groupNum);
            groupNum = chain.getNext(i);
            if (groupNum == 0) break;
        }
    }

    /**
     * チェイン用のセクタをクリア(機種依存)
     *
     * @param pItem コピー元のアイテム
     */
    @Override
    public void clearChainSector(DiskBasicDirItem<DirectoryAppleDos> pItem) {
        chain.clear();
        chain.setBasic(basic);
    }

    /**
     * チェイン用のセクタをセット
     *
     * @param sector   セクタ
     * @param groupNum グループ番号
     * @param data     セクタ内のバッファ
     * @param pItem    コピー元のアイテム
     */
    @Override
    public void setChainSector(DiskImageSector sector, int groupNum, byte[] data, DiskBasicDirItem<DirectoryAppleDos> pItem) throws IOException {
        AppleDosChain c = new AppleDosChain();
        Serdes.Util.deserialize(new ByteArrayInputStream(data), c);
        chain.add(c);
        if (chain.count() > 1) {
            int i = chain.count() - 2;
            chain.setNext(i, groupNum);
        }
    }

    /**
     * チェイン用のセクタにグループ番号をセット(機種依存)
     *
     * @param idx インデックス
     * @param val グループ番号
     */
    @Override
    public void addChainGroupNumber(int idx, int val) {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        type.getNumFromSectorPosS(val, trackNum, sectorNum);
        chain.setTrackAndSector(idx, trackNum[0], sectorNum[0]);
    }

    /** セクタカウントをセット(機種依存) */
    public void setSectorCount(int val) {
        data.data().sectorCount = (short) val; // be
    }

    /**
     * セクタカウントを返す(機種依存)
     * <p>
     * セクタカウントはトラックセクタリストで占有しているセクタ数も含んでいる
     */
    public int getSectorCount() {
        return data.data().sectorCount & 0xffff; // be
    }

    /** ファイルの終端コードをチェックする必要があるか */
    @Override
    public boolean needCheckEofCode() {
        return ((getFileType1() & 0x7f) == 0);
    }

    /**
     * セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合
     *
     * @param iStream  入力ストリーム
     * @param fileSize ファイルサイズ
     * @return 再計算後のファイルサイズ
     */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        if (needCheckEofCode()) {
            // ファイルの最終が終端記号で終わっているかを調べる
            // ただし、ファイルサイズがセクタサイズで割り切れるなら終端記号は不要
            if ((fileSize % basic.getSectorSize()) != 0) {
                fileSize = checkEofCode(iStream, fileSize);
                fileSize--;
            }
        }
        return fileSize;
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryAppleDos getData() {
        return data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val, getDataSize());
    }

    /** ディレクトリをクリア */
    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode(), getDataSize());
    }

    /**
     * データをインポートする前に必要な処理
     *
     * @param filename [in,out] ファイル名
     * @return false このファイルは対象外とする
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /** アイテムを削除できるか */
    @Override
    public boolean isDeletable() {
        return true;
    }

    /** 最初のトラック番号をセット */
    public void setStartTrack(byte val) {
        data.data().track = val;
    }

    /** 最初のセクタ番号をセット */
    public void setStartSector(byte val) {
        data.data().sector = val;
    }

    /** 最初のトラック番号を返す */
    public byte getStartTrack() {
        return data.data().track;
    }

    /** 最初のセクタ番号を返す */
    public byte getStartSector() {
        return data.data().sector;
    }

    /** アイテムがアドレスを持っているか */
    @Override
    public boolean hasAddress() {
        return true;
    }

    /** アイテムが実行アドレスを持っているか */
    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    /** アドレスを編集できるか */
    @Override
    public boolean isAddressEditable() {
        return false;
    }

    /** 開始アドレスを返す */
    @Override
    public int getStartAddress() {
        return startAddress;
    }

    /** 終了アドレスを返す */
    @Override
    public int getEndAddress() {
        return (startAddress >= 0 && dataLength > 0) ? startAddress + dataLength - 1 : -1;
    }

    //
    // ダイアログ用
    //

    /**
     * プロパティで表示する内部データを設定
     *
     * @param vals [in,out] 名前＆値のリスト
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TRACK", data.data().track);
        vals.add("SECTOR", data.data().sector);
        vals.add("TYPE", data.data().type);
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("SECTOR_COUNT", data.data().sectorCount);
    }
}
