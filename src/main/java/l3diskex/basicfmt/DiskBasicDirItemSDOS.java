/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectorySdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;


/**
 * ディレクトリ１アイテム S-DOS
 */
public class DiskBasicDirItemSDOS extends DiskBasicDirItem<DirectorySdos> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    public static final int TYPE_NAME_SDOS_BAS1   = 0;
    public static final int TYPE_NAME_SDOS_BAS2   = 1;
    public static final int TYPE_NAME_SDOS_DAT    = 2;
    public static final int TYPE_NAME_SDOS_OBJ    = 3;
    public static final int TYPE_NAME_SDOS_UNKNOWN = 4;

    public static final int FILETYPE_SDOS_BAS1    = 0x00;   // N mode
    public static final int FILETYPE_SDOS_BAS2    = 0x01;   // n88 mode
    public static final int FILETYPE_SDOS_DAT     = 0x02;   // except exec address
    public static final int FILETYPE_SDOS_OBJ     = 0x0e;   // include exec address
    public static final int FILETYPE_SDOS_UNKNOWN = 0xff;

    // Global data from basicdiritem_sdos.cpp
    public static final Map<String, Object> gTypeNameSDOS_1 = new LinkedHashMap<>() {{
            put("BASIC (N)", FILETYPE_SDOS_BAS1);
            put("BASIC (n88)", FILETYPE_SDOS_BAS2);
            put("Binary", FILETYPE_SDOS_DAT);
            put("Machine", FILETYPE_SDOS_OBJ);
            put("???", FILETYPE_SDOS_UNKNOWN);
    }};
  
    private DiskBasicDirData<DirectorySdos> m_data = new DiskBasicDirData<>();
    private DirItemSectorBoundary m_sdata = new DirItemSectorBoundary();

    // Constants for GUI component IDs (from cpp)
    public static final int IDC_COMBO_TYPE1 = 51;

    private DiskBasicDirItemSDOS() {}
    // private DiskBasicDirItemSDOS(const DiskBasicDirItemSDOS &src) {} // Not needed in Java typically

    public DiskBasicDirItemSDOS(DiskBasic basic) {
        super(basic);
        m_data.alloc(DirectorySdos.class);
        AllocateItem(null);
    }
    public DiskBasicDirItemSDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
        m_data.attach(n_data);
        AllocateItem(null);
    }
    public DiskBasicDirItemSDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
        m_data.attach(n_data);
        AllocateItem(n_next);

        // CheckUsed updates the m_used internal field of DiskBasicDirItem
        used(checkUsed(n_unuse[0]));

        // ファイルサイズとグループ数を計算 (CalcFileSize is in base class, called here)
        calcFileSize();
    }

    /**
     * アイテムへのポインタを設定
     * @param n_num 通し番号
     * @param n_gitem トラック番号などのデータ
     * @param n_sector セクタ
     * @param n_secpos セクタ内のディレクトリエントリの位置
     * @param n_data ディレクトリアイテム
     * @param n_next 次のセクタ
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, n_next);
        m_data.attach(n_data);
        AllocateItem(n_next);
    }

    /**
     * ディレクトリエントリを確保
     * @param next 次のセクタ
     * @return true
     */
    private boolean AllocateItem(SectorParam next) {
        m_sdata.clear();
        boolean bound = m_sdata.set(basic, sector, position, m_data.data(), getDataSize(), next);

        if (!m_data.isSelf() && bound) {
            // セクタをまたぐ場合、dataは内部で確保する
            m_data.alloc(DirectorySdos.class);
            m_data.fill(0, getDataSize());
        }

        // コピー
        if (m_data.isSelf()) {
            m_sdata.copyTo(m_data.data());
        }

        return true;
    }

    /**
     * ファイル名を格納する位置を返す
     * @param num num
     * @param size size
     * @param len len
     * @return ファイル名
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
     * 属性１を返す
     * @return 属性１
     */
    @Override
    protected int getFileType1() {
        return m_data.data().type;
    }

    /**
     * 属性１を設定
     * @param val 属性値
     */
    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    /**
     * 使用しているアイテムか
     * @param unuse 未使用フラグ
     * @return true: 使用している
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (!unuse && this.m_data.data().name[0] != 0 && this.m_data.data().name[0] != (byte)0xff);
    }

    /**
     * ファイル名を設定
     * @param filename ファイル名
     * @param size サイズ
     * @param length 長さ
     */
    @Override
    public void setNativeName(byte[] filename, int size, int[] length) {
        super.setNativeName(filename, size, length);
    }

    /**
     * ファイル名と拡張子を得る
     * @param name ファイル名
     * @param nlen ファイル名長さ
     * @param ext 拡張子
     * @param elen 拡張子長さ
     */
    @Override
    public void getNativeFileName(byte[] name, int[] nlen, byte[] ext, int[] elen) {
        super.getNativeFileName(name, nlen, ext, elen);
    }

    /**
     * ディレクトリアイテムのチェック
     * @param last チェックを終了するか
     * @return チェックOK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        if (m_data.data().name[0] == (byte)0xff) {
            last[0] = true;
            return valid;
        }
        return valid;
    }

    /**
     * 削除
     * @return true
     */
    @Override
    public boolean delete() {
        m_data.data().name[0] = basic.diskBasicParam.getDeleteCode();
        used(false);
        return true;
    }

    /**
     * 属性を設定
     * @param file_type ファイル属性
     */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            t1 = file_type.getOrigin();
        } else {
            if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                if (basic.diskBasicParam.getFormatSubTypeNumber() != 0) {
                    t1 = FILETYPE_SDOS_BAS2;
                } else {
                    t1 = FILETYPE_SDOS_BAS1;
                }
            } else if ((ftype & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FILETYPE_SDOS_OBJ;
            } else {
                t1 = FILETYPE_SDOS_DAT;
            }
        }
        setFileType1(t1);

        SetUnknownData();
    }

    /**
     * 属性を返す
     * @return ファイル属性
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1) {
            case FILETYPE_SDOS_DAT:
                val = FILE_TYPE_DATA_MASK.getValue(); // data
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            case FILETYPE_SDOS_OBJ:
                val = FILE_TYPE_MACHINE_MASK.getValue(); // machine
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            case FILETYPE_SDOS_BAS1:
            case FILETYPE_SDOS_BAS2:
                val = FILE_TYPE_BASIC_MASK.getValue(); // basic
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
            default:
                val = FILE_TYPE_DATA_MASK.getValue(); // data
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
                break;
        }
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     * @return 属性文字列
     */
    @Override
    public String getFileAttrStr() {
        String attr = rb.getString(Utils.keyAt(gTypeNameSDOS_1, GetFileType1Pos()));
        return attr;
    }

    /**
     * ファイルサイズをセット
     * @param val ファイルサイズ (バイト)
     */
    @Override
    public void setFileSize(int val) {
        int sectorSize = basic.getSectorSize();
        // size = ceil(val / sectorSize)
        int size = (val + sectorSize - 1);

        m_data.data().size = (byte) (size / sectorSize);
        m_data.data().restSize = (byte) ((val - 1) % sectorSize);
    }

    /**
     * ファイルサイズを返す
     * @return ファイルサイズ (バイト)
     */
    @Override
    public int getFileSize() {
        int size;
        size = m_data.data().size;

        if (size > 0) size--;
        size *= basic.getSectorSize();

        size += m_data.data().restSize;

        size++;
        return size;
    }

    /**
     * グループ数を返す
     * @return グループ数
     */
    @Override
    public int getGroupSize() {
        int size;
        size = m_data.data().size;
        return size;
    }

    /**
     * ファイルサイズを計算
     * @param fileunit_num ファイル番号
     */
    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     * @param fileunit_num ファイル番号
     * @param group_items グループリスト
     */
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) {
        int calc_groups = 0;
        int calc_file_size = getFileSize();

        int group_num = getStartGroup(fileunit_num);
        int gsize = getGroupSize();
        int limit = basic.getFatEndGroup() + 1;
        while(gsize > 0 && limit >= 0) {
            AddGroups(group_num, 0, group_items);
            group_num++;
            calc_groups++;
            gsize--;
            limit--;
        }

        group_items.setNums(calc_groups);
        group_items.setSize(calc_file_size);
        group_items.setSizePerGroup(basic.getSectorSize());
    }

    /**
     * グループを追加する
     * @param group_num グループ番号
     * @param next_group 次のグループ番号
     * @param group_items グループリスト
     */
    private void AddGroups(int group_num, int next_group, DiskBasicGroups group_items) {
        int[] trk = {-1};
        int[] sid = {-1};
        int[] sec = {-1};
        int[] div = {-1};
        int[] divs = {-1};
        basic.calcNumFromSectorPosForGroup(group_num, trk, sid, sec, div, divs);

        group_items.add(group_num, next_group, trk[0], sid[0], sec[0], sec[0], div[0], divs[0]);
    }

    /**
     * 最初のグループ番号を設定
     * @param fileunit_num ファイル番号
     * @param val グループ番号
     * @param size グループサイズ
     */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        int track = (val /  basic.getSectorsPerTrackOnBasic()) & 0xff;
        int sector = ((val %  basic.getSectorsPerTrackOnBasic()) + 1) & 0xff;
        m_data.data().track = (byte) track;
        m_data.data().sector = (byte) sector;
    }

    /**
     * 最初のグループ番号を返す
     * @param fileunit_num ファイル番号
     * @return 最初のグループ番号
     */
    @Override
    public int getStartGroup(int fileunit_num) {
        int track = 0;
        int sector = 0;
        track = m_data.data().track;
        sector = m_data.data().sector;

        return track * basic.getSectorsPerTrackOnBasic() + sector - 1;
    }

    /**
     * 開始アドレスを返す
     * @return 開始アドレス
     */
    @Override
    public int getStartAddress() {
        int addr;
        addr = m_data.data().loadAddr;

        return basic.orderUint16((short) addr);
    }

    /**
     * 実行アドレスを返す
     * @return 実行アドレス
     */
    @Override
    public int getExecuteAddress() {
        int addr;
        addr = m_data.data().execAddr;

        return basic.orderUint16((short) addr);
    }

    /**
     * 開始アドレスをセット
     * @param val 開始アドレス
     */
    @Override
    public void setStartAddress(int val) {
        int addr = basic.orderUint16((short) val);
        m_data.data().loadAddr = (short) addr;
    }

    /**
     * 実行アドレスをセット
     * @param val 実行アドレス
     */
    @Override
    public void setExecuteAddress(int val) {
        int addr = basic.orderUint16((short) val);
        m_data.data().execAddr = (short) addr;
    }

    /**
     * 次のアイテムにENDマークを入れる
     * @param next_item 次のディレクトリアイテム
     */
    @Override
    public void setEndMark(DiskBasicDirItem<?> next_item) throws IOException {
        if (next_item == null) return;

        next_item.delete();
    }

    /**
     * ファイルの終端コードをチェックする必要があるか
     * @return true
     */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /**
     * ディレクトリサイズを返す
     * @return サイズ
     */
    @Override
    public int getDataSize() {
        // Placeholder for sizeof(DirectorySdos)
        return 17; // Assuming 8(name) + 1(type) + 1(track) + 1(sector) + 2(size) + 1(rest) + 2(load) + 2(exec) + 1(reserved) = 19, adjust as needed
    }

    /**
     * アイテムを返す
     * @return アイテムデータ
     */
    @Override
    public DirectorySdos getData() {
        return m_data.data();
    }

    /**
     * アイテムをコピー
     * @param val アイテム
     * @return true
     */
    @Override
    public boolean copyData(DirectorySdos val) {
        m_data.copy(val, getDataSize());
        if (m_data.isSelf()) {
            m_sdata.copyFrom(m_data.data());
        }
        return true;
    }

    /**
     * ディレクトリをクリア
     */
    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir(), getDataSize());
    }

    /**
     * 未使用領域の設定
     */
    private void SetUnknownData() {
        byte val = (byte)0xff;
        m_data.data().reserved = val;
    }

    /**
     * インポート時のダイアログを出す前にファイルパスから内部ファイル名を生成する
     * @param filename ファイル名
     * @return false このファイルは対象外とする
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.IsDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * ファイル名から属性を決定する
     * @param filename ファイル名
     * @return 属性
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = TYPE_NAME_SDOS_DAT;
        // 拡張子で属性を設定する
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(Utils.getExt(filename)); // Placeholder for FindUpperCase

        if (sa != null) {
            int saType = sa.getType();
            if ((saType & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
                if (basic.diskBasicParam.getFormatSubTypeNumber() != 0) {
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
        if (m_data.isSelf()) {
            m_sdata.copyFrom(m_data.data());
        }
    }

    //
    // ダイアログ用
    //

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     * @return リストの位置
     */
    public int GetFileType1Pos() {
        int t1 = getFileType1();
        // Assuming name_value_t has an IndexOf method as defined in the C++ snippet (conceptually)
        int pos = Utils.indexOf(gTypeNameSDOS_1, t1);
        if (pos < 0) {
            pos = TYPE_NAME_SDOS_UNKNOWN;
        }
        return pos;
    }

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     * @return リストの位置
     */
    public int GetFileType2Pos() {
        return getFileAttr().getType();
    }

    /**
     * プロパティで表示する内部データを設定
     * @param vals 名前＆値のリスト
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        // Placeholder for KeyValArray.Add methods
        vals.add("self", m_data.isSelf());
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("TYPE", m_data.data().type);
        vals.add("TRACK", m_data.data().track);
        vals.add("SECTOR", m_data.data().sector);
        vals.add("SIZE", m_data.data().size);
        vals.add("REST SIZE", m_data.data().restSize);
        vals.add("LOAD ADDR", (byte) m_data.data().loadAddr, basic.isBigEndian());
        vals.add("EXEC ADDR", (byte) m_data.data().execAddr, basic.isBigEndian());
        vals.add("RESERVED", m_data.data().reserved);
    }
}