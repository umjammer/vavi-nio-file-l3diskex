/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.DirectorySDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeSDOS.FORMAT_TYPE_SDOS;


/**
 * ディレクトリ１アイテム S-DOS
 */
public class DiskBasicDirItemSDOS extends DiskBasicDirItem<DirectorySDos> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ S-DOS (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectorySDos implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[22];
        @Element(sequence = 2)
        public byte type;
        @Element(sequence = 3)
        public byte track;
        @Element(sequence = 4)
        public byte sector;
        @Element(sequence = 5)
        public byte size; // number of sector
        @Element(sequence = 6)
        public byte restSize;
        @Element(sequence = 7)
        public short loadAddress;
        @Element(sequence = 8)
        public short execAddress;
        @Element(sequence = 9)
        public byte reserved;

        public static final int SIZE = 32;
    }

    // S-DOS

    public static final int TYPE_NAME_SDOS_BAS1 = 0;
    public static final int TYPE_NAME_SDOS_BAS2 = 1;
    public static final int TYPE_NAME_SDOS_DAT = 2;
    public static final int TYPE_NAME_SDOS_OBJ = 3;
    public static final int TYPE_NAME_SDOS_UNKNOWN = 4;

    public static final int FILETYPE_SDOS_BAS1 = 0x00; // N mode
    public static final int FILETYPE_SDOS_BAS2 = 0x01; // n88 mode
    public static final int FILETYPE_SDOS_DAT = 0x02;  // except exec address
    public static final int FILETYPE_SDOS_OBJ = 0x0e;  // include exec address
    public static final int FILETYPE_SDOS_UNKNOWN = 0xff;

    /// S-DOS 属性名
    public static final Map<String, Object> typeNameSdos1 = new LinkedHashMap<>() {{
        put("BASIC (N)", FILETYPE_SDOS_BAS1);
        put("BASIC (n88)", FILETYPE_SDOS_BAS2);
        put("Binary", FILETYPE_SDOS_DAT);
        put("Machine", FILETYPE_SDOS_OBJ);
        put("???", FILETYPE_SDOS_UNKNOWN);
    }};

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectorySDos> data = new DiskBasicDirData<>();

    /** セクタ内部へのポインタ */
    private final DirItemSectorBoundary sectorData = new DirItemSectorBoundary();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_SDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectorySDos.class);
        allocateItem(null);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectorySDos.class, data, dataP);
        allocateItem(null);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectorySDos.class, data, dataP);
        allocateItem(next);

        used(checkUsed(unuse[0]));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /**
     * アイテムへのポインタを設定
     *
     * @param num    通し番号
     * @param groupItem  トラック番号などのデータ
     * @param sector セクタ
     * @param sectorPos セクタ内のディレクトリエントリの位置
     * @param data   ディレクトリアイテム
     * @param dataPos    offset of {@code n_data}
     * @param next   次のセクタ
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectorySDos.class, data, dataPos);
        allocateItem(next);
    }

    /**
     * ディレクトリエントリを確保
     *
     * @param next 次のセクタ
     * @return true
     */
    private boolean allocateItem(SectorParam next) throws IOException {
        sectorData.clear();
        boolean bound = sectorData.set(basic, sector, position, data.getRawData(), getDataSize(), next);

        if (bound) {
            // セクタをまたぐ場合、dataは内部で確保する
            data.alloc(DirectorySDos.class);
            data.fill(0, DirectorySDos.SIZE);
        }

        // コピー
        sectorData.copyTo(data.getRawData());

        return true;
    }

    /**
     * ファイル名を格納する位置を返す
     *
     * @param num  num
     * @param size size
     * @param len  len
     * @return ファイル名
     */
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
     * 属性１を返す
     *
     * @return 属性１
     */
    @Override
    protected int getFileType1() {
        return data.data().type & 0xff;
    }

    /**
     * 属性１を設定
     *
     * @param val 属性値
     */
    @Override
    protected void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    /**
     * 使用しているアイテムか
     *
     * @param unuse 未使用フラグ
     * @return true: 使用している
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (!unuse && this.data.data().name[0] != 0 && this.data.data().name[0] != (byte) 0xff);
    }

    /**
     * ファイル名を設定
     *
     * @param filename ファイル名
     * @param size     サイズ
     * @param length   長さ
     */
    @Override
    public void setNativeName(byte[] filename, int size, int length) {
        super.setNativeName(filename, size, length);
    }

    /**
     * ファイル名と拡張子を得る
     *
     * @param name ファイル名
     * @param nLen ファイル名長さ
     * @param ext  拡張子
     * @param eLen 拡張子長さ
     */
    @Override
    public void getNativeFileName(byte[] name, int[] nLen, byte[] ext, int[] eLen) {
        super.getNativeFileName(name, nLen, ext, eLen);
    }

    /**
     * ディレクトリアイテムのチェック
     *
     * @param last チェックを終了するか
     * @return チェックOK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        if (data.data().name[0] == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        return valid;
    }

    /**
     * 削除
     *
     * @return true
     */
    @Override
    public boolean delete() {
        data.data().name[0] = basic.getDeleteCode();
        used(false);
        return true;
    }

    /**
     * 属性を設定
     *
     * @param fileType ファイル属性
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = fileType.getOrigin();
        } else {
            if ((fType & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                if (basic.getFormatSubTypeNumber() != 0) {
                    t1 = FILETYPE_SDOS_BAS2;
                } else {
                    t1 = FILETYPE_SDOS_BAS1;
                }
            } else if ((fType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FILETYPE_SDOS_OBJ;
            } else {
                t1 = FILETYPE_SDOS_DAT;
            }
        }
        setFileType1(t1);

        setUnknownData();
    }

    /**
     * 属性を返す
     *
     * @return ファイル属性
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1) {
            case FILETYPE_SDOS_DAT:
                val = FILE_TYPE_DATA_MASK.getValue();    // data
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            case FILETYPE_SDOS_OBJ:
                val = FILE_TYPE_MACHINE_MASK.getValue(); // machine
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            case FILETYPE_SDOS_BAS1:
            case FILETYPE_SDOS_BAS2:
                val = FILE_TYPE_BASIC_MASK.getValue();   // basic
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            default:
                val = FILE_TYPE_DATA_MASK.getValue();    // data
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
        }
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     *
     * @return 属性文字列
     */
    @Override
    public String getFileAttrStr() {
        String attr = rb.getString(Utils.keyAt(typeNameSdos1, getFileType1Pos()));
        return attr;
    }

    /**
     * ファイルサイズをセット
     *
     * @param val ファイルサイズ (バイト)
     */
    @Override
    public void setFileSize(int val) {
        int size = val + basic.getSectorSize() - 1;

        data.data().size = (byte) (size / basic.getSectorSize());
        data.data().restSize = (byte) ((val - 1) % basic.getSectorSize());
    }

    /**
     * ファイルサイズを返す
     *
     * @return ファイルサイズ (バイト)
     */
    @Override
    public int getFileSize() {
        int size;
        size = data.data().size & 0xff;

        if (size > 0) size--;
        size *= basic.getSectorSize();

        size += data.data().restSize & 0xff;

        size++;
        return size;
    }

    /**
     * グループ数を返す
     *
     * @return グループ数
     */
    @Override
    public int getGroupSize() {
        int size;
        size = data.data().size & 0xff;
        return size;
    }

    /**
     * ファイルサイズを計算
     *
     * @param fileUnitNum ファイル番号
     */
    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     *
     * @param fileUnitNum ファイル番号
     * @param groupItems  グループリスト
     */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) {
        int calcGroups = 0;
        int calcFileSize = getFileSize();

        int groupNum = getStartGroup(fileUnitNum);
        int groupSize = getGroupSize();
        int limit = basic.getFatEndGroup() + 1;
        while (groupSize > 0 && limit >= 0) {
            addGroups(groupNum, 0, groupItems);
            groupNum++;
            calcGroups++;
            groupSize--;
            limit--;
        }

        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize());
    }

    /**
     * グループを追加する
     *
     * @param groupNum   グループ番号
     * @param nextGroup  次のグループ番号
     * @param groupItems グループリスト
     */
    private void addGroups(int groupNum, int nextGroup, DiskBasicGroups groupItems) {
        int[] track = {-1}, side = {-1}, sector = {-1}, div = {0}, divs = {0};
        basic.calcNumFromSectorPosForGroup(groupNum, track, side, sector, div, divs);
        groupItems.add(groupNum, nextGroup, track[0], side[0], sector[0], sector[0], div[0], divs[0]);
    }

    /**
     * 最初のグループ番号を設定
     *
     * @param fileUnitNum ファイル番号
     * @param val          グループ番号
     * @param size         グループサイズ
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int track = (val / basic.getSectorsPerTrackOnBasic()) & 0xff;
        int sector = ((val % basic.getSectorsPerTrackOnBasic()) + 1) & 0xff;
        data.data().track = (byte) track;
        data.data().sector = (byte) sector;
    }

    /**
     * 最初のグループ番号を返す
     *
     * @param fileUnitNum ファイル番号
     * @return 最初のグループ番号
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        int track = 0;
        int sector = 0;
        track = data.data().track & 0xff;
        sector = data.data().sector & 0xff;

        return track * basic.getSectorsPerTrackOnBasic() + sector - 1;
    }

    /**
     * 開始アドレスを返す
     *
     * @return 開始アドレス
     */
    @Override
    public int getStartAddress() {
        int address;
        address = data.data().loadAddress & 0xffff;

        return basic.orderUint16((short) address);
    }

    /**
     * 実行アドレスを返す
     *
     * @return 実行アドレス
     */
    @Override
    public int getExecuteAddress() {
        int address;
        address = data.data().execAddress & 0xffff;

        return basic.orderUint16((short) address);
    }

    /**
     * 開始アドレスをセット
     *
     * @param val 開始アドレス
     */
    @Override
    public void setStartAddress(int val) {
        int address = basic.orderUint16((short) val);
        data.data().loadAddress = (short) address;
    }

    /**
     * 実行アドレスをセット
     *
     * @param val 実行アドレス
     */
    @Override
    public void setExecuteAddress(int val) {
        int address = basic.orderUint16((short) val);
        data.data().execAddress = (short) address;
    }

    /**
     * 次のアイテムにENDマークを入れる
     *
     * @param nextItem 次のディレクトリアイテム
     */
    @Override
    public void setEndMark(DiskBasicDirItem<?> nextItem) throws IOException {
        if (nextItem == null) return;

        nextItem.delete();
    }

    /**
     * ファイルの終端コードをチェックする必要があるか
     *
     * @return true
     */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /**
     * ディレクトリサイズを返す
     *
     * @return サイズ
     */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /**
     * アイテムを返す
     *
     * @return アイテムデータ
     */
    @Override
    public DirectorySDos getData() {
        return data.data();
    }

    /**
     * アイテムをコピー
     *
     * @param val アイテム
     * @return true
     */
    @Override
    public boolean copyData(byte[] val) {
        data.copy(val, getDataSize());
        sectorData.copyFrom(data.getRawData());
        return true;
    }

    /**
     * ディレクトリをクリア
     */
    @Override
    public void clearData() {
        data.fill(basic.getFillCodeOnDir(), getDataSize());
    }

    /**
     * 未使用領域の設定
     */
    private void setUnknownData() {
        byte val = (byte) 0xff;
        data.data().reserved = val;
    }

    /**
     * インポート時のダイアログを出す前にファイルパスから内部ファイル名を生成する
     *
     * @param filename ファイル名
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

    /**
     * ファイル名から属性を決定する
     *
     * @param filename ファイル名
     * @return 属性
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = TYPE_NAME_SDOS_DAT;
        // 拡張子で属性を設定する
        MyAttribute attr = findUpperCase(basic.getAttributesByExtension(), Utils.getExt(filename));
        if (attr != null) {
            int saType = attr.getType();
            if ((saType & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
                if (basic.getFormatSubTypeNumber() != 0) {
                    t1 = TYPE_NAME_SDOS_BAS2;
                } else {
                    t1 = TYPE_NAME_SDOS_BAS1;
                }
            } else if ((saType & (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) == (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
                t1 = TYPE_NAME_SDOS_OBJ;
            }
        }
        return t1;
    }

    /**
     * アイテムの属するセクタを変更済みにする
     */
    @Override
    public void setModify() {
        sectorData.copyFrom(data.getRawData());
    }

    //
    // ダイアログ用
    //

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     *
     * @return リストの位置
     */
    public int getFileType1Pos() {
        int t1 = getFileType1();
        int pos = Utils.indexOf(typeNameSdos1, t1);
        if (pos < 0) {
            pos = TYPE_NAME_SDOS_UNKNOWN;
        }
        return pos;
    }

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     *
     * @return リストの位置
     */
    public int getFileType2Pos() {
        return getFileAttr().getType();
    }

    /**
     * プロパティで表示する内部データを設定
     *
     * @param vals 名前＆値のリスト
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("TYPE", data.data().type);
        vals.add("TRACK", data.data().track);
        vals.add("SECTOR", data.data().sector);
        vals.add("SIZE", data.data().size);
        vals.add("REST SIZE", data.data().restSize);
        vals.add("LOAD ADDR", (byte) data.data().loadAddress, basic.isBigEndian());
        vals.add("EXEC ADDR", (byte) data.data().execAddress, basic.isBigEndian());
        vals.add("RESERVED", data.data().reserved);
    }
}
