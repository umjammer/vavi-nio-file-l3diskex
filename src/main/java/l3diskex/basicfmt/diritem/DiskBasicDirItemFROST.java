///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
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
import l3diskex.basicfmt.diritem.DiskBasicDirItemFROST.DirectoryFrost;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeFROST.FORMAT_TYPE_FROST;


/** ディレクトリ１アイテム Frost-DOS */
public class DiskBasicDirItemFROST extends DiskBasicDirItem<DirectoryFrost> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ Frost-DOS (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryFrost implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[6];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte track;
        @Element(sequence = 5)
        public byte sector;
        @Element(sequence = 6)
        public short loadAddress;
        @Element(sequence = 7)
        public short size;

        public static final int SIZE = 16;
    }

    // Frost-DOS

    public static final int FROST_GROUP_SIZE = 512;

    public static final int TYPE_NAME_FROST_BAS = 0;
    public static final int TYPE_NAME_FROST_BIN = 1;
    public static final int TYPE_NAME_FROST_RGB = 2;
    public static final int TYPE_NAME_FROST_UNKNOWN = 3;

    public static final int FILETYPE_FROST_BAS = 0x00;
    public static final int FILETYPE_FROST_BIN = 0x01;
    public static final int FILETYPE_FROST_RGB = 0x02;

    /// Frost-DOS 属性名
    public static final Map<String, Object> typeNameFROST1 = new LinkedHashMap<>() {{
        put("BAS", FILETYPE_FROST_BAS);
        put("BIN", FILETYPE_FROST_BIN);
        put("RGB", FILETYPE_FROST_RGB);
    }};

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryFrost> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_FROST;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryFrost.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);

        this.data.attach(DirectoryFrost.class, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        this.data.attach(DirectoryFrost.class, data, dataPos);

        used(checkUsed(unuse[0]));
        unuse[0] = (unuse[0] || (this.data.data().name[0] == (byte) 0xff));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /**
     * アイテムへのポインタを設定
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryFrost.class, data, dataPos);
    }

    /**
     * ファイル名を格納する位置を返す
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
     * 拡張子を格納する位置を返す
     */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().ext.length;
        return data.data().ext;
    }

    /**
     * 属性１を返す
     */
    @Override
    public int getFileType1() {
        return data.data().type & 0xff;
    }

    /**
     * 属性１のセット
     */
    @Override
    protected void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    /**
     * 属性１の文字列
     */
    String convFileType1Str(int t1) {
        return rb.getString(Utils.keyAt(typeNameFROST1, convFileType1Pos(t1)));
    }

    /**
     * 使用しているアイテムか
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.data.data().name[0] != 0 && this.data.data().name[0] != (byte) 0xff;
    }

    /**
     * ディレクトリアイテムのチェック
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        if (data.data().name[0] == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        // 属性に不正な値がある
        if ((getFileType1() & 0x0c) != 0) {
            valid = false;
        }
        return valid;
    }

    /**
     * 削除
     */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /**
     * ENDマークがあるか(一度も使用していないか)
     */
    @Override
    public boolean hasEndMark() {
        return ((data.data().name[0] & 0xff) == basic.getGroupUnusedCode());
    }

    /**
     * 次のアイテムにENDマークを入れる
     */
    @Override
    public void setEndMark(DiskBasicDirItem<?> nextItem) {
        if (nextItem == null) return;

        if (hasEndMark()) ((DiskBasicDirItemFROST) nextItem).data.data().name[0] = (byte) basic.getGroupUnusedCode();
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOS
            t1 = fileType.getOrigin();
        } else {
            // 違うOS
            if ((fType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FILETYPE_FROST_BIN;
            } else if ((fType & FILE_TYPE_DATA_MASK.getValue()) != 0) {
                t1 = FILETYPE_FROST_RGB;
            } else {
                t1 = FILETYPE_FROST_BAS;
            }
        }
        setFileType1(t1);

        // BASのときは開始アドレスを1にする
        if (getFileType1() == FILETYPE_FROST_BAS) {
            setStartAddress(1);
        }
    }

    /**
     * 属性を返す
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int type = 0;
        switch (t1) {
            case FILETYPE_FROST_BIN:
                type = FILE_TYPE_MACHINE_MASK.getValue();    // machine
                type |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case FILETYPE_FROST_RGB:
                type = FILE_TYPE_DATA_MASK.getValue();       // data
                type |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            default:
                type = FILE_TYPE_BASIC_MASK.getValue();      // basic
                type |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
        }

        if (isValidDirectory()) { // TODO ad-hoc if this is a root directory set directory type bit
            type |= FILE_TYPE_DIRECTORY_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), type, t1);
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     */
    @Override
    public String getFileAttrStr() {
        return convFileType1Str(getFileType1());
    }

    /**
     * ファイルサイズをセット
     */
    @Override
    public void setFileSize(int val) {
        data.data().size = basic.orderUint16((short) val);
        groups.setSize(val);
    }

    /**
     * ファイルサイズを返す
     */
    @Override
    public int getFileSize() {
        int val = basic.orderUint16(data.data().size) & 0xffff;
        if (val == 0) val = groups.getSize();
        return val;
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
        int calcFileSize = 0;
        int calcGroups = 0;

        // 16bit FAT (track & sector)
        boolean rc = true;
        int groupNum = getStartGroup(fileUnitNum);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int nextGroup = type.getGroupNumber(groupNum);
            if (nextGroup == groupNum) {
                // 同じポジションならエラー
                rc = false;
            } else if (nextGroup >= basic.getGroupSystemCode()) {
                // システム領域はエラー(0xfefe)
                rc = false;
            } else if (nextGroup == basic.getGroupFinalCode()) {
                // 最終グループ(0xfdfd)
                addGroups(groupNum, nextGroup, groupItems);
                calcFileSize += (basic.getSectorSize() / basic.getGroupsPerSector());
                calcGroups++;
                working = false;
            } else if (nextGroup > basic.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            } else {
                addGroups(groupNum, nextGroup, groupItems);
                calcFileSize += (basic.getSectorSize() / basic.getGroupsPerSector());
                calcGroups++;
                groupNum = nextGroup;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        int interFileSize = getFileSize();
        if (interFileSize == 0) {
            interFileSize = calcFileSize;
        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(interFileSize);
        groupItems.setSizePerGroup(FROST_GROUP_SIZE);

        if (limit < 0) {
            // too large or infinite loop
            rc = false;
        }
    }

    /**
     * グループを追加する
     */
    private void addGroups(int groupNum, int nextGroup, DiskBasicGroups groupItems) {
        int[] track = {-1}, side = {-1}, sector = {-1}, div = {0}, divs = {0};
        basic.calcNumFromSectorPosForGroup(groupNum, track, side, sector, div, divs);
        groupItems.add(groupNum, nextGroup, track[0], side[0], sector[0], sector[0], div[0], divs[0]);
    }

    /**
     * 最初のグループ番号をセット
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        data.data().track = (byte) (val / basic.getGroupsPerTrack());
        data.data().sector = (byte) ((val % basic.getGroupsPerTrack()) + 1);
    }

    /**
     * 最初のグループ番号を返す
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return (data.data().track & 0xff) * basic.getGroupsPerTrack() + (data.data().sector & 0xff) - 1;
    }

    /**
     * アイテムがアドレスを持っているか
     */
    @Override
    public boolean hasAddress() {
        return true;
    }

    /**
     * アイテムが実行アドレスを持っているか
     */
    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    /**
     * 開始アドレスを返す
     */
    @Override
    public int getStartAddress() {
        return basic.orderUint16(data.data().loadAddress) & 0xffff;
    }

    /**
     * 開始アドレスをセット
     */
    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = basic.orderUint16((short) val);
    }

    /**
     * ファイルの終端コードをチェックする必要があるか
     */
    @Override
    public boolean needCheckEofCode() {
        // Asc形式のときはEOFコードが必要
        return false; //(((getFileType1() & (FILETYPE_FROST_MACHINE | FILETYPE_FROST_BINARY)) == 0) && (externalAttr == 0));
    }

    /**
     * ディレクトリアイテムのサイズ
     */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /**
     * アイテムを返す (Returns the internal data object)
     */
    @Override
    public DirectoryFrost getData() {
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
     * ディレクトリをクリア ファイル新規作成時
     */
    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode());
    }

    /**
     * データをエクスポートする前に必要な処理
     */
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        // 拡張子を付加する
        if (!isDirectory()) {
            String ext = convFileType1Str(getFileType1());
            filename[0] += ".";
            if (Utils.isUpperString(filename[0])) {
                ext = ext.toUpperCase();
            } else {
                ext = ext.toLowerCase();
            }
            filename[0] += ext;
        }
        return true;
    }

    /**
     * インポート時のダイアログを出す前にファイルパスから内部ファイル名を生成する
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], typeNameFROST1, TYPE_NAME_FROST_BAS, TYPE_NAME_FROST_RGB, filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * ファイル名から属性を決定する
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        // 拡張子で属性を設定する
        if (!isContainAttrByExtension(filename, typeNameFROST1, TYPE_NAME_FROST_BAS, TYPE_NAME_FROST_RGB, null, t1, null)) {
            t1[0] = FILETYPE_FROST_BIN;
        }
        return t1[0];
    }

    //
    // ダイアログ用
    //

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     */
    int convFileType1Pos(int t1) {
        if (t1 < 0 || t1 > TYPE_NAME_FROST_UNKNOWN) {
            t1 = TYPE_NAME_FROST_UNKNOWN;
        }
        return t1;
    }

    /**
     * プロパティで表示する内部データを設定
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("EXT", data.data().ext, data.data().ext.length);
        vals.add("TYPE", data.data().type & 0xff);
        vals.add("TRACK", data.data().track & 0xff);
        vals.add("SECTOR", data.data().sector & 0xff);
        vals.add("LOAD_ADDR", (byte) data.data().loadAddress, basic.isBigEndian());
        vals.add("SIZE", (byte) data.data().size, basic.isBigEndian());
    }
}
