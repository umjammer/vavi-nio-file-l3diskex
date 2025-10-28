///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryFrost;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;


/// ディレクトリ１アイテム Frost-DOS
public class DiskBasicDirItemFROST extends DiskBasicDirItem<DirectoryFrost> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

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
    public static final Map<String, Object> gTypeNameFROST_1 = new LinkedHashMap<>() {{
        put("BAS", FILETYPE_FROST_BAS);
        put("BIN", FILETYPE_FROST_BIN);
        put("RGB", FILETYPE_FROST_RGB);
    }};

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryFrost> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemFROST(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryFrost.class);
    }

    public DiskBasicDirItemFROST(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryFrost.class, n_data, dataP);
    }

    public DiskBasicDirItemFROST(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryFrost.class, n_data, dataP);

        used(checkUsed(n_unuse[0]));
        n_unuse[0] = (n_unuse[0] || (m_data.data().name[0] == (byte) 0xff));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /**
     * アイテムへのポインタを設定
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryFrost.class, n_data, dataP);
    }

    /**
     * ファイル名を格納する位置を返す
     */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = m_data.data().name.length;
            return m_data.data().name;
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
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

    /**
     * 属性１を返す
     */
    @Override
    public int getFileType1() {
        return m_data.data().type & 0xff;
    }

    /**
     * 属性１のセット
     */
    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    /**
     * 属性１の文字列
     */
    String convFileType1Str(int t1) {
        return rb.getString(Utils.keyAt(gTypeNameFROST_1, convFileType1Pos(t1)));
    }

    /**
     * 使用しているアイテムか
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.m_data.data().name[0] != 0 && this.m_data.data().name[0] != (byte) 0xff;
    }

    /**
     * ディレクトリアイテムのチェック
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        if (m_data.data().name[0] == (byte) 0xff) {
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
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /**
     * ENDマークがあるか(一度も使用していないか)
     */
    @Override
    public boolean hasEndMark() {
        return ((m_data.data().name[0] & 0xff) == basic.diskBasicParam.getGroupUnusedCode());
    }

    /**
     * 次のアイテムにENDマークを入れる
     */
    @Override
    public void setEndMark(DiskBasicDirItem<?> next_item) {
        if (next_item == null) return;

        if (hasEndMark()) ((DiskBasicDirItemFROST) next_item).m_data.data().name[0] = (byte) basic.diskBasicParam.getGroupUnusedCode();
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOS
            t1 = file_type.getOrigin();
        } else {
            // 違うOS
            if ((ftype & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FILETYPE_FROST_BIN;
            } else if ((ftype & FILE_TYPE_DATA_MASK.getValue()) != 0) {
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
        int val = 0;
        switch (t1) {
            case FILETYPE_FROST_BIN:
                val = FILE_TYPE_MACHINE_MASK.getValue();    // machine
                val |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case FILETYPE_FROST_RGB:
                val = FILE_TYPE_DATA_MASK.getValue();       // data
                val |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            default:
                val = FILE_TYPE_BASIC_MASK.getValue();      // basic
                val |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
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
        m_data.data().size = basic.orderUint16((short) val);
        groups.setSize(val);
    }

    /**
     * ファイルサイズを返す
     */
    @Override
    public int getFileSize() {
        int val = basic.orderUint16(m_data.data().size) & 0xffff;
        if (val == 0) val = groups.getSize();
        return val;
    }

    /**
     * ファイルサイズとグループ数を計算する
     */
    @Override
    public void calcFileUnitSize(int fileunit_num) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     */
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) throws IOException {
        int calc_file_size = 0;
        int calc_groups = 0;

        // 16bit FAT (track & sector)
        boolean rc = true;
        int group_num = getStartGroup(fileunit_num);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int next_group = type.getGroupNumber(group_num);
            if (next_group == group_num) {
                // 同じポジションならエラー
                rc = false;
            } else if (next_group >= basic.diskBasicParam.getGroupSystemCode()) {
                // システム領域はエラー(0xfefe)
                rc = false;
            } else if (next_group == basic.diskBasicParam.getGroupFinalCode()) {
                // 最終グループ(0xfdfd)
                addGroups(group_num, next_group, group_items);
                calc_file_size += (basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector());
                calc_groups++;
                working = false;
            } else if (next_group > basic.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            } else {
                addGroups(group_num, next_group, group_items);
                calc_file_size += (basic.getSectorSize() / basic.diskBasicParam.getGroupsPerSector());
                calc_groups++;
                group_num = next_group;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        int inter_file_size = getFileSize();
        if (inter_file_size == 0) {
            inter_file_size = calc_file_size;
        }
        group_items.setNums(calc_groups);
        group_items.setSize(inter_file_size);
        group_items.setSizePerGroup(FROST_GROUP_SIZE);

        if (limit < 0) {
            // too large or infinit loop
            rc = false;
        }
    }

    /**
     * グループを追加する
     */
    private void addGroups(int group_num, int next_group, DiskBasicGroups group_items) {
        int[] trk = {-1}, sid = {-1}, sec = {-1}, div = {0}, divs = {0};
        basic.calcNumFromSectorPosForGroup(group_num, trk, sid, sec, div, divs);
        group_items.add(group_num, next_group, trk[0], sid[0], sec[0], sec[0], div[0], divs[0]);
    }

    /**
     * 最初のグループ番号をセット
     */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        m_data.data().track = (byte) (val / basic.diskBasicParam.getGroupsPerTrack());
        m_data.data().sector = (byte) ((val % basic.diskBasicParam.getGroupsPerTrack()) + 1);
    }

    /**
     * 最初のグループ番号を返す
     */
    @Override
    public int getStartGroup(int fileunit_num) {
        return (m_data.data().track & 0xff) * basic.diskBasicParam.getGroupsPerTrack() + (m_data.data().sector & 0xff) - 1;
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
        return basic.orderUint16(m_data.data().loadAddr);
    }

    /**
     * 開始アドレスをセット
     */
    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = basic.orderUint16((short) val);
    }

    /**
     * ファイルの終端コードをチェックする必要があるか
     */
    @Override
    public boolean needCheckEofCode() {
        // Asc形式のときはEOFコードが必要
        return false; //(((GetFileType1() & (FILETYPE_FROST_MACHINE | FILETYPE_FROST_BINARY)) == 0) && (external_attr == 0));
    }

    /**
     * ディレクトリアイテムのサイズ
     */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /**
     * アイテムを返す (Returns the internal data object)
     */
    @Override
    public DirectoryFrost getData() {
        return m_data.data();
    }

    /**
     * アイテムをコピー
     */
    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val);
    }

    /**
     * ディレクトリをクリア ファイル新規作成時
     */
    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getDeleteCode());
    }

    /**
     * データをエクスポートする前に必要な処理
     */
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.isAddExtensionExport()) return true;

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
        if (gConfig.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], gTypeNameFROST_1, TYPE_NAME_FROST_BAS, TYPE_NAME_FROST_RGB, filename, null, null);
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
        if (!isContainAttrByExtension(filename, gTypeNameFROST_1, TYPE_NAME_FROST_BAS, TYPE_NAME_FROST_RGB, null, t1, null)) {
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
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXT", m_data.data().ext, m_data.data().ext.length);
        vals.add("TYPE", m_data.data().type & 0xff);
        vals.add("TRACK", m_data.data().track & 0xff);
        vals.add("SECTOR", m_data.data().sector & 0xff);
        vals.add("LOAD_ADDR", (byte) m_data.data().loadAddr, basic.isBigEndian());
        vals.add("SIZE", (byte) m_data.data().size, basic.isBigEndian());
    }
}
