///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemC1541.DirectoryC1541;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_EXTENSION_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeC1541.C1541_START_SECTOR_OFFSET;
import static l3diskex.basicfmt.type.DiskBasicTypeC1541.C1541_START_TRACK_OFFSET;
import static l3diskex.basicfmt.type.DiskBasicTypeC1541.FORMAT_TYPE_C1541;


/// ディレクトリ１アイテム Commodore 1541
public class DiskBasicDirItemC1541 extends DiskBasicDirItem<DirectoryC1541> {

    /**
     * トラック＆セクタ Commodore 1541
     */
    @Serdes
    public static class C1541Pointer {
        public static final int SIZE = 2;

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
    }

    /**
     * ディレクトリエントリ Commodore 1541 (32bytes)
     */
    @Serdes
    public static class DirectoryC1541 implements Directory {

        @Element(sequence = 1)
        public byte[] doNotWrite = new byte[2]; // first entry only on each sector
        @Element(sequence = 2)
        public byte type;
        @Element(sequence = 3)
        public C1541Pointer firstData = new C1541Pointer();
        @Element(sequence = 4)
        public byte[] name = new byte[16];
        @Element(sequence = 5)
        public C1541Pointer firstSide = new C1541Pointer(); // relative file only
        @Element(sequence = 6)
        public byte recordSize; // relative file only
        @Element(sequence = 7)
        public byte[] unused = new byte[4];
        @Element(sequence = 8)
        public C1541Pointer replace = new C1541Pointer();
        @Element(sequence = 9)
        public short numOfBlocks;

        public static final int SIZE = 32;
    }

    /// C1541属性値
    public static final int FILETYPE_MASK_C1541_DEL = 0x80;
    public static final int FILETYPE_MASK_C1541_SEQ = 0x81;
    public static final int FILETYPE_MASK_C1541_PRG = 0x82;
    public static final int FILETYPE_MASK_C1541_USR = 0x83;
    public static final int FILETYPE_MASK_C1541_REL = 0x84;

    /// C1541属性位置
    public static final int TYPE_NAME_C1541_DEL = 0;
    public static final int TYPE_NAME_C1541_SEQ = 1;
    public static final int TYPE_NAME_C1541_PRG = 2;
    public static final int TYPE_NAME_C1541_USR = 3;
    public static final int TYPE_NAME_C1541_REL = 4;

    /// C1541属性名
    public static final Map<String, Object> typeNameC1541 = new LinkedHashMap<>() {{
        put("DEL", FILETYPE_MASK_C1541_DEL);
        put("SEQ", FILETYPE_MASK_C1541_SEQ);
        put("PRG", FILETYPE_MASK_C1541_PRG);
        put("USR", FILETYPE_MASK_C1541_USR);
        put("REL", FILETYPE_MASK_C1541_REL);
    }};

    /** C1541属性変換テーブル */
    private static final Map<Integer, Integer> typeConvC1541 = new HashMap<>() {{
            put(FILE_TYPE_DATA_MASK.getValue() | FILE_TYPE_ASCII_MASK.getValue(), FILETYPE_MASK_C1541_SEQ);
            put(FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue(), FILETYPE_MASK_C1541_PRG);
            put(FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue(), FILETYPE_MASK_C1541_USR);
            put(FILE_TYPE_DATA_MASK.getValue() | FILE_TYPE_RANDOM_MASK.getValue(), FILETYPE_MASK_C1541_REL);
    }};

    //
    // ディレクトリ１アイテム Commodore 1541
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryC1541> data = new DiskBasicDirData<>();

    /** For REL files, side sectors */
    public DiskBasicGroups ssGroups = new DiskBasicGroups();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_C1541;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryC1541.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryC1541.class, data, dataP);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem,
                     DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos,
                     SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        this.data.attach(DirectoryC1541.class, data, dataPos);

        used(checkUsed(unuse[0]));

        calcFileSize();
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem,
                        DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos,
                        SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryC1541.class, data, dataPos);
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

    /** ファイル名を設定 */
    protected void setNativeName(byte[] filename, int[] size, int[] length) {
        byte[] n = getFileNamePos(0, size, length);
        if (n != null && size[0] > 0) {
            System.arraycopy(filename, 0, n, 0, size[0]);
        }
    }

    /** ファイル名を得る */
    @Override
    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n = null;
        int[] s = {0};
        int[] l = {0};

        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            int copySize = Math.min(s[0], size);
            System.arraycopy(n, 0, filename, 0, copySize);
        }

        length[0] = l[0];
    }

    /** 属性１を返す */
    @Override
    public int getFileType1() {
        return data.data().type & 0xff;
    }

    /** 属性１のセット */
    @Override
    public void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (getFileType1() & 0x80) != 0;
    }

    /** 共通属性を個別属性に変換 */
    public static int convToFileType1(int fType) {
        int t1 = 0;
        for (Map.Entry<Integer, Integer> e : typeConvC1541.entrySet()) {
            if ((fType & FILE_TYPE_EXTENSION_MASK) == e.getValue()) {
                t1 = e.getKey();
                break;
            }
        }
        return t1;
    }

    /** 個別属性を共通属性に変換 */
    public static int convFromFileType1(int type1) {
        int val = 0;
        for (Map.Entry<Integer, Integer> e : typeConvC1541.entrySet()) {
            if (type1 == e.getKey()) {
                val = e.getValue();
                break;
            }
        }
        return val;
    }

    /** 属性からリストの位置を返す */
    public int convFileType1Pos(int type1) {
        return Utils.indexOf(typeNameC1541, type1);
    }

    /** ブロック数をセット */
    private void setBlocks(short val) {
        data.data().numOfBlocks = val; // be
    }

    /** ブロック数を返す */
    private short getBlocks() {
        return data.data().numOfBlocks; // be
    }

    /** ディレクトリアイテムのチェック */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;

        int type = getFileType1();
        if (FILETYPE_MASK_C1541_REL < type) {
            valid = false;
        }
        return valid;
    }

    /** 削除 */
    @Override
    public boolean delete() {
        // 削除
        used(false);
        setFileType1(basic.getDeleteCode());
        return true;
    }

    /** ファイル名＋拡張子のサイズ */
    @Override
    public int getFileNameStrSize() {
        int[] s = {0};
        int[] l = {0};
        getFileNamePos(0, s, l);

        return s[0];
    }

    /** 属性を設定 */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOS
            int t1 = fileType.getOrigin();
            int rl = t1 >> 8;
            t1 &= 0xff;
            setFileType1(t1);
            setRecordLength(rl);
        } else {
            // 違うOSから
            int t1 = convToFileType1(fType);
            if (t1 > 0) setFileType1(t1);
        }
    }

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int rl = getRecordLength();
        int t1 = getFileType1();
        int val = convFromFileType1(t1);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (rl << 8) | t1);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        String str;
        int sPos = convFileType1Pos(getFileType1());
        if (sPos >= 0) {
            str = Utils.keyAt(typeNameC1541, sPos);
        } else {
            str = "???";
        }
        return str;
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int block = basic.getSectorSize() - 2;
        int groups = (val + block - 1) / block;

        setBlocks((short) groups);
    }

    /** ファイルサイズを返す */
    @Override
    public int getFileSize() {
        int val = groups.getSize();
        if (val == 0) {
            val = getBlocks();
            val *= (basic.getSectorSize() - 2);
        }
        return val;
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!checkUsed(false)) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /** 指定ディレクトリのすべてのグループを取得 */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        //if (!chain.isValid()) return;

        int[] trackNum = {0};
        int[] sideNum = {0};
        int[] sectorNum = {0};

        int sector_size = basic.getSectorSize();
        // 1セクタ当たり2バイトはチェイン用のリンクポインタになるので減算
        int bytesPerGroup = basic.getSectorSize() - 2;
        int limit = bytesPerGroup * getBlocks();

        int blocks = 1;
        int type1 = getFileType1();
        // RELative fileの場合はサイドセクタ分を加算する
        if (type1 == FILETYPE_MASK_C1541_REL) {
            ssGroups.clear();
            blocks = 2;
        }

        // データ部分とサイドセクタ分のサイズ
        int calcGroups = 0;    // 合計分
        for (int i = 0; i < blocks; i++) {
            int calcFileSize = 0;
            DiskBasicGroups tmpGroupItems = new DiskBasicGroups();
            int groupNum = (i == 0 ? getStartGroup(fileUnitNum) : getExtraGroup());

            while (limit > 0) {
                int sectorPos = groupNum;
                DiskImageSector sector = basic.getSectorFromSectorPos(sectorPos, trackNum, sideNum);
                if (sector == null) {
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    break;
                }
                sectorNum[0] = sector.getSectorNumber();

                tmpGroupItems.add(groupNum, 0, trackNum[0], sideNum[0], sectorNum[0], sectorNum[0]);

                calcGroups++;
                calcFileSize += bytesPerGroup;
                limit -= bytesPerGroup;

                // 次のセクタなし
                C1541Pointer next = new C1541Pointer();
                Serdes.Util.deserialize(new ByteArrayInputStream(buffer), next);
                if (next.track == 0 || (next.track & 0xff) > basic.getTracksPerSideOnBasic()) {
                    break;
                }

                groupNum = type.getSectorPosFromNumS((next.track & 0xff) - C1541_START_TRACK_OFFSET, (next.sector & 0xff) - C1541_START_SECTOR_OFFSET);
            }

            calcFileSize = recalcFileSize(tmpGroupItems, calcFileSize);

            if (i == 0) {
                // 最終セクタの再計算
                groupItems.add(tmpGroupItems);
                groupItems.setSize(calcFileSize);
            } else {
                // サイドセクタ分
                ssGroups.add(tmpGroupItems);
                ssGroups.setSize(calcFileSize);
            }
        }

        groupItems.setNums(calcGroups);
        groupItems.setSizePerGroup(sector_size);

        // ファイル内部のアドレスを得る
        //takeAddressesInFile(group_items);
    }

    /** 最終セクタのサイズを計算してファイルサイズを返す */
    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        if (groupItems.size() == 0) return occupiedSize;

        // 現在のセクタ
        DiskBasicGroupItem lastItem = groupItems.last();
        if (lastItem == null) return occupiedSize;

        DiskImageSector sector = basic.getSectorFromSectorPos(lastItem.group);
        if (sector == null) {
            return occupiedSize;
        }
        byte[] buffer = sector.getSectorBuffer();
        if (buffer == null) {
            return occupiedSize;
        }
        C1541Pointer p = new C1541Pointer();
        Serdes.Util.deserialize(new ByteArrayInputStream(buffer), p);
        if (p.track != 0) {
            // really last sector ?
            return occupiedSize;
        }
        occupiedSize = occupiedSize + 1 + (p.sector & 0xff) - basic.getSectorSize();

        return occupiedSize;
    }

    /** レコード長をセット(REL file) */
    public void setRecordLength(int val) {
        data.data().recordSize = (byte) (val & 0xff);
    }

    /** レコード長を返す(REL file) */
    public int getRecordLength() {
        return data.data().recordSize & 0xff;
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryC1541 getData() {
        return data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(byte[] val) {
        // エントリの最初2バイトは使用禁止
        return data.copy(val, getDataSize(), basic.isDataInverted(), data.data().doNotWrite.length);
    }

    /** ディレクトリをクリア ファイル新規作成時 */
    @Override
    public void clearData() {
        // エントリの最初2バイトは使用禁止
        data.fill(0, getDataSize(), basic.isDataInverted(), data.data().doNotWrite.length);
    }

    /** 最初のグループ番号をセット */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        type.getNumFromSectorPosS(val, trackNum, sectorNum);
        trackNum[0] += C1541_START_TRACK_OFFSET;
        sectorNum[0] += C1541_START_SECTOR_OFFSET;
        data.data().firstData.track = (byte) (trackNum[0] & 0xff);
        data.data().firstData.sector = (byte) (sectorNum[0] & 0xff);
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileUnitNum) {
        int trackNum = (data.data().firstData.track & 0xff) - C1541_START_TRACK_OFFSET;
        int sectorNum = (data.data().firstData.sector & 0xff) - C1541_START_SECTOR_OFFSET;
        return basic.getType().getSectorPosFromNumS(trackNum, sectorNum);
    }

    /** サイドセクタのあるグループ番号をセット(機種依存)(REL file) */
    @Override
    public void setExtraGroup(int val) {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        type.getNumFromSectorPosS(val, trackNum, sectorNum);
        trackNum[0] += C1541_START_TRACK_OFFSET;
        sectorNum[0] += C1541_START_SECTOR_OFFSET;
        data.data().firstSide.track = (byte) (trackNum[0] & 0xff);
        data.data().firstSide.sector = (byte) (sectorNum[0] & 0xff);
    }

    /** サイドセクタのあるグループ番号を返す(機種依存)(REL file) */
    @Override
    public int getExtraGroup() {
        int trackNum = (data.data().firstSide.track & 0xff) - C1541_START_TRACK_OFFSET;
        int sectorNum = (data.data().firstSide.sector & 0xff) - C1541_START_SECTOR_OFFSET;
        return type.getSectorPosFromNumS(trackNum, sectorNum);
    }

    /** サイドセクタのグループリストをセット(機種依存) */
    @Override
    public void setExtraGroups(DiskBasicGroups groups) {
        ssGroups = groups;
        // ブロック数を合計する
        setBlocks((short) (getBlocks() + groups.getNums()));
        // グループ数も加算
        this.groups.addNums(groups.getNums());
    }

    /** サイドセクタのグループ番号を得る(機種依存) */
    @Override
    public void getExtraGroups(List<Integer> groups) {
        // RELファイルの時はサイドセクタのグループ
        int type1 = getFileType1();
        if (type1 != FILETYPE_MASK_C1541_REL) return;

        for (int i = 0; i < ssGroups.size(); i++) {
            groups.add(ssGroups.get(i).group);
        }
    }

    /** サイドセクタのグループリストを返す(機種依存) */
    @Override
    public void getExtraGroups(DiskBasicGroups[] gsoups) {
        gsoups[0] = ssGroups;
    }

    /** ファイルの終端コードをチェックする必要があるか */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /** セーブ時にファイルサイズを再計算する */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) {
        return fileSize;
    }

    /** データをエクスポートする前に必要な処理 */
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        // 属性から拡張子を付加する
        String[] ext = {""};
        if (getFileAttrName(convFileType1Pos(getFileType1()), typeNameC1541, ext)) {
            filename[0] += ".";
            if (Utils.isUpperString(filename[0])) {
                filename[0] += ext[0].toUpperCase();
            } else {
                filename[0] += ext[0].toLowerCase();
            }
        }
        return true;
    }

    /** データをインポートする前に必要な処理 */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            trimLastExtensionByExtensionAttr(filename[0], typeNameC1541, TYPE_NAME_C1541_DEL, TYPE_NAME_C1541_REL, filename, null, null);
        }
        // 拡張子を消す
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        int[] p1 = {0};
        // 拡張子で属性を設定する
        if (trimLastExtensionByExtensionAttr(filename, typeNameC1541, TYPE_NAME_C1541_DEL, TYPE_NAME_C1541_REL, null, t1, p1)) {
            // 外部パラメータで設定したものは共通属性なので変換
            if (p1[0] < 0) {
                t1[0] = convToFileType1(t1[0]);
            }
        } else {
            // default
            t1[0] = FILETYPE_MASK_C1541_SEQ;
        }

        return t1[0];
    }

    /** アイテムの属するセクタを変更済みにする */
    @Override
    public void setModify() {
    }

    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("(DO_NOT_WRITE)", data.data().doNotWrite, data.data().doNotWrite.length);
        vals.add("TYPE", data.data().type & 0xff);
        vals.add("FIRST_DATA.TRACK", data.data().firstData.track & 0xff);
        vals.add("FIRST_DATA.SECTOR", data.data().firstData.sector & 0xff);
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("FIRST_SIDE.TRACK", data.data().firstSide.track & 0xff);
        vals.add("FIRST_SIDE.SECTOR", data.data().firstSide.sector & 0xff);
        vals.add("RECORD_SIZE", data.data().recordSize & 0xff);
        vals.add("UNUSED", data.data().unused, data.data().unused.length);
        vals.add("REPLACE.TRACK", data.data().replace.track & 0xff);
        vals.add("REPLACE.SECTOR", data.data().replace.sector & 0xff);
        vals.add("NUM_OF_BLOCKS", getBlocks()); // GetBlocks returns a short/int
    }
}
