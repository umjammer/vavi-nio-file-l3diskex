/*
 * @author Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ResourceBundle;

import l3diskex.Common;
import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryCpm;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.ByteUtil;

import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ARCHIVE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;


/**
 * ディレクトリ１アイテム CP/M
 *
 * @li m_external_attr バイナリ属性の時: FILE_TYPE_BINARY_MASK, アスキー属性の時: 0
 */
public class DiskBasicDirItemCPM extends DiskBasicDirItem<DirectoryCpm> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /// CP/M属性名
    public static final String[] gTypeNameCPM = {
            /*rb.getString(*/"Read Only"/*)*/,
            /*rb.getString(*/"System"/*)*/,
            /*rb.getString(*/"Archive"/*)*/,
    };

    /// CP/M属性名
    static final int TYPE_NAME_CPM_READ_ONLY = 0;
    static final int TYPE_NAME_CPM_SYSTEM = 1;
    static final int TYPE_NAME_CPM_ARCHIVE = 2;

    public static final String[] gTypeNameCPM_2 = {
            /*rb.getString(*/"Binary"/*)*/,
            /*rb.getString(*/"Ascii"/*)*/,
    };

    static final int TYPE_NAME_CPM_BINARY = 0;
    static final int TYPE_NAME_CPM_ASCII = 1;

    public static final int SECTOR_UNIT_CPM = 128;

    //
    //
    //

    /** ディレクトリデータ */
    protected DiskBasicDirData<DirectoryCpm> m_data = new DiskBasicDirData<>();

    /** グループ番号の幅(1 = 8ビット, 2 = 16ビット) */
    protected int group_width;
    /** グループ番号のエントリ数(8 or 16) */
    protected int group_entries;

    /** 次のエクステントがある場合 */
    protected DiskBasicDirItemCPM next_item;

    public DiskBasicDirItemCPM(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryCpm.class);
        // グループ番号の幅
        group_width = basic.diskBasicParam.getGroupWidth();
        group_entries = basic.diskBasicParam.getGroupsPerDirEntry() >= 8 ? basic.diskBasicParam.getGroupsPerDirEntry() : (16 / group_width);
        externalAttr = getFileTypeByExt(0, getFileExtPlainStr());

        next_item = null;
    }

    public DiskBasicDirItemCPM(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) throws IOException {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryCpm.class, n_data, dataP);
        // グループ番号の幅
        group_width = basic.diskBasicParam.getGroupWidth();
        group_entries = basic.diskBasicParam.getGroupsPerDirEntry() >= 8 ? basic.diskBasicParam.getGroupsPerDirEntry() : (16 / group_width);
        externalAttr = getFileTypeByExt(0, getFileExtPlainStr());

        next_item = null;
    }

    public DiskBasicDirItemCPM(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryCpm.class, n_data, dataP);
        // グループ番号の幅
        group_width = basic.diskBasicParam.getGroupWidth();
        group_entries = basic.diskBasicParam.getGroupsPerDirEntry() >= 8 ? basic.diskBasicParam.getGroupsPerDirEntry() : (16 / group_width);
        externalAttr = getFileTypeByExt(0, getFileExtPlainStr());

        next_item = null;

        used(checkUsed(n_unuse[0]));
    }

    /**
     * アイテムへのポインタを設定
     *
     * @param n_num    通し番号
     * @param n_gitem  トラック番号などのデータ
     * @param n_sector セクタ
     * @param n_secpos セクタ内のディレクトリエントリの位置
     * @param n_data   ディレクトリアイテム
     * @param dataP
     * @param n_next   次のセクタ
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryCpm.class, n_data, dataP);
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
     * 属性１を返す (User ID)
     */
    @Override
    public int getFileType1() {
        return basic.invertUint8((byte) (m_data.data().type & 0xff));
    }

    /**
     * 属性２を返す (R/S/A + Binary/ASCII)
     */
    @Override
    public int getFileType2() {
        int val = 0;
        byte[] ext = new byte[m_data.data().ext.length + 1];
        basic.invertMem(m_data.data().ext, m_data.data().ext.length, ext);

        val |= (ext[0] & 0x80) != 0 ? FILE_TYPE_READONLY_MASK.getValue() : 0; // read only
        val |= (ext[1] & 0x80) != 0 ? FILE_TYPE_SYSTEM_MASK.getValue() : 0;   // system
        val |= (ext[2] & 0x80) != 0 ? FILE_TYPE_ARCHIVE_MASK.getValue() : 0;  // archive

        String extstr = getFileExtPlainStr();

        val = getFileTypeByExt(val, extstr);

        val |= externalAttr;

        return val;
    }

    /**
     * 拡張子からアスキーorバイナリ属性を判断する
     */
    public int getFileTypeByExt(int val, String ext) {
        MyAttribute sa = findUpperCase(basic.diskBasicParam.getAttributesByExtension(), ext, FILE_TYPE_BINARY_MASK.getValue(), 0x3f);
        if (sa != null) {
            val |= FILE_TYPE_BINARY_MASK.getValue();
        }
        return val;
    }

    /**
     * 属性１を設定 (User ID)
     */
    @Override
    protected void setFileType1(int val) {
        m_data.data().type = basic.invertUint8((byte) val);
    }

    /**
     * 属性２を設定 (R/S/A + Binary/ASCII)
     */
    @Override
    protected void setFileType2(int val) {
        if (basic.isDataInverted()) Common.mem_invert(m_data.getRawData(), m_data.data().ext.length); // invert

        m_data.data().ext[0] = (byte) ((m_data.data().ext[0] & 0x7f) | ((val & FILE_TYPE_READONLY_MASK.getValue()) != 0 ? 0x80 : 0));
        m_data.data().ext[1] = (byte) ((m_data.data().ext[1] & 0x7f) | ((val & FILE_TYPE_SYSTEM_MASK.getValue()) != 0 ? 0x80 : 0));
        m_data.data().ext[2] = (byte) ((m_data.data().ext[2] & 0x7f) | ((val & FILE_TYPE_ARCHIVE_MASK.getValue()) != 0 ? 0x80 : 0));
        externalAttr = val & FILE_TYPE_BINARY_MASK.getValue();

        if (basic.isDataInverted()) Common.mem_invert(m_data.getRawData(), m_data.data().ext.length); // invert
    }

    /**
     * ファイル名を得る
     */
    @Override
    public void getNativeFileName(byte[] name, int[] nlen, byte[] ext, int[] elen) {
        super.getNativeFileName(name, nlen, ext, elen);

        // 拡張子部分のMSBは属性ビットなので除く
        for (int en = 0; en < elen[0]; en++) {
            ext[en] &= 0x7f;
        }
    }

    /**
     * 拡張子を返す
     *
     * @return 拡張子
     */
    @Override
    public String getFileExtPlainStr() {
        if (!m_data.isValid()) return "";

        byte[] ext = new byte[m_data.data().ext.length];
        basic.invertMem(m_data.data().ext, m_data.data().ext.length, ext);
        for (int i = 0; i < m_data.data().ext.length; i++) {
            ext[i] &= 0x7f;
        }
        return new String(ext);
    }

    /**
     * ファイル名を設定
     *
     * @param filename ファイル名
     * @param size     バッファサイズ
     * @param length   長さ
     *                 filename はデータビットが反転している場合あり
     */
    @Override
    protected void setNativeName(byte[] filename, int size, int length) {
        super.setNativeName(filename, size, length);

        // 複数ある時
        if (next_item != null) {
            next_item.setNativeName(filename, size, length);
        }
    }

    /**
     * 拡張子を設定
     *
     * @param fileext 拡張子
     * @param size    バッファサイズ
     * @param length  長さ
     *                fileext はデータビットが反転している場合あり
     */
    @Override
    protected void setNativeExt(byte[] fileext, int size, int length) {
        byte[] e;
        int[] el = {0};
        e = getFileExtPos(el);

        if (el[0] > size) el[0] = size;

        for (int i = 0; i < el[0]; i++) {
            // MSBは属性ビットなのでのこす
            e[i] = (byte) ((e[i] & 0x80) | (fileext[i] & 0x7f));
        }

        // 複数ある時
        if (next_item != null) {
            next_item.setNativeExt(fileext, size, length);
        }
    }

    /**
     * 使用しているアイテムか
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return getFileType1() != 0xe5;
    }

    /**
     * 削除
     */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        setFileType1(basic.diskBasicParam.getDeleteCode());
        used(false);

        // 複数ある時
        if (next_item != null) {
            next_item.delete();
        }
        return true;
    }

    /**
     * ディレクトリアイテムのチェック
     *
     * @param last チェックを終了するか
     * @return チェックOK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = false;
        // ユーザIDが0～15でファイル名がオール0ならダメ
        if (getFileType1() < 0x10) {
            byte[] name = new byte[m_data.data().name.length];
            basic.invertMem(m_data.data().name, m_data.data().name.length, name);
            for (int n = 0; n < m_data.data().name.length; n++) {
                if (name[n] != 0) {
                    valid = true;
                    break;
                }
            }
            if (valid && !last[0]) {
                valid = checkData(m_data.getRawData(), getDataSize(), last);
            }
            // グループ番号が超えていたらダメ
            if (valid) {
                for (int i = 0; i < group_entries; i++) {
                    if (getGroupNumber(i) > basic.getFatEndGroup()) {
                        valid = false;
                        break;
                    }
                }
            }
        } else {
            valid = !checkUsed(false);
        }
        return valid;
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        setFileType1(file_type.getFormat() == basic.getFormatTypeNumber() ? file_type.getOrigin() : 0);
        setFileType2(file_type.getType());

        // 複数ある場合
        if (next_item != null) {
            next_item.setFileAttr(file_type);
        }
    }

    /**
     * 属性を返す
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        return new DiskBasicFileType(basic.getFormatTypeNumber(), getFileType2(), getFileType1());
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     */
    @Override
    public String getFileAttrStr() {
        int val = getFileType2();
        StringBuilder str = new StringBuilder();

        if ((val & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            str.append(rb.getString(gTypeNameCPM_2[TYPE_NAME_CPM_BINARY]));
        } else {
            str.append(rb.getString(gTypeNameCPM_2[TYPE_NAME_CPM_ASCII]));
        }
        if ((val & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            if (!str.isEmpty()) str.append(", ");
            str.append(rb.getString(gTypeNameCPM[TYPE_NAME_CPM_READ_ONLY]));
        }
        if ((val & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
            if (!str.isEmpty()) str.append(", ");
            str.append(rb.getString(gTypeNameCPM[TYPE_NAME_CPM_SYSTEM]));
        }
        if ((val & FILE_TYPE_ARCHIVE_MASK.getValue()) != 0) {
            if (!str.isEmpty()) str.append(", ");
            str.append(rb.getString(gTypeNameCPM[TYPE_NAME_CPM_ARCHIVE]));
        }
        return str.toString();
    }

    /**
     * ファイルサイズをセット
     */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
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

        int bytes_per_group = basic.getSectorSize() * basic.getSectorsPerGroup();

        // グループ数を計算
        int map_size = getGroupEntries();
        int group_size = (bytes_per_group * map_size);
        int remain_size = (getExtentNumber() * SECTOR_UNIT_CPM + getRecordNumber()) * SECTOR_UNIT_CPM;
        // ファイルサイズは1エントリ分にする
        remain_size = ((remain_size + group_size - 1) % group_size) + 1;

        for (int map_pos = 0; map_pos < map_size; map_pos++) {
            int group_num = getGroupNumber(map_pos);
            if (group_num == 0) break;
            basic.getNumsFromGroup(group_num, 0, basic.getSectorSize(), remain_size, group_items);

            calc_file_size += (remain_size < bytes_per_group ? remain_size : bytes_per_group);
            calc_groups++;

            remain_size -= bytes_per_group;
        }
        group_items.addSize(calc_file_size);
        group_items.addNums(calc_groups);

        if (next_item != null) {
            // ファイルには続きがある
            next_item.getUnitGroups(fileunit_num, group_items);
        } else {
            // ファイル終り

            // グループサイズ
            group_items.setSizePerGroup(bytes_per_group);
            // 最終セクタのサイズを計算
            group_items.setSize(recalcFileSize(group_items, (int) group_items.getSize()));
        }
    }

    /**
     * 最終セクタのサイズを計算してファイルサイズを返す
     *
     * @param group_items   グループリスト
     * @param occupied_size 占有サイズ
     * @return 計算後のファイルサイズ
     */
    @Override
    public int recalcFileSize(DiskBasicGroups group_items, int occupied_size) throws IOException {
        if (group_items.size() == 0) return occupied_size;

        DiskBasicGroupItem litem = group_items.last();
        DiskImageSector sector = basic.getSector(litem.track, litem.side, litem.sectorEnd);
        if (sector == null) return occupied_size;

        int sector_size = sector.getSectorSize();
        int remain_size = ((occupied_size + SECTOR_UNIT_CPM - 1) % SECTOR_UNIT_CPM) + 1;
        int unit_pos = (((occupied_size + sector_size - 1) % sector_size) / SECTOR_UNIT_CPM);
        byte[] buf = sector.getSectorBuffer();
        int buf_offset = unit_pos * SECTOR_UNIT_CPM;
        remain_size = type.calcDataSizeOnLastSector(this, null, null, buf, buf_offset, SECTOR_UNIT_CPM, remain_size);

        occupied_size = occupied_size - SECTOR_UNIT_CPM + remain_size;

        return occupied_size;
    }

    /**
     * ディレクトリアイテムのサイズ
     */
    @Override
    public int getDataSize() {
        return DirectoryCpm.SIZE;
    }

    /**
     * アイテムを返す
     */
    @Override
    public DirectoryCpm getData() {
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
        m_data.fill(0, getDataSize(), basic.isDataInverted(), 0);
    }

    /**
     * ファイルの終端コードをチェックする必要があるか
     */
    @Override
    public boolean needCheckEofCode() {
        return externalAttr == 0;
    }

    /**
     * セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合
     */
    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) {
        if (needCheckEofCode()) {
            // ファイルの最終が終端記号で終わっているかを調べる
            // ただし、ファイルサイズが128バイトと合うなら終端記号は不要
            if ((file_size % SECTOR_UNIT_CPM) != 0) {
                file_size = checkEofCode(istream, file_size);
                file_size--;
            }
        }
        return file_size;
    }

    /**
     * 最初のグループ番号を設定
     */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
    }

    /**
     * 最初のグループ番号を返す
     */
    @Override
    public int getStartGroup(int fileunit_num) {
        int val = 0;
        val = group_width > 1 ? ByteUtil.readLeShort(m_data.data().mapBytes, 0) & 0xffff : m_data.data().mapBytes[0] & 0xff; // TODO check endian
        if (basic.isDataInverted()) val ^= (group_width > 1 ? 0xffff : 0xff); // invert
        return val;
    }

    /**
     * アイテムを削除できるか
     */
    @Override
    public boolean isDeletable() {
        return true;
    }

    /**
     * グループ番号をセット
     */
    public void setGroup(int pos, int val) {
        if (pos < 0 || pos >= group_entries) return;

        if (basic.isDataInverted()) val = ~val; // invert

        if (group_width > 1) {
            ByteUtil.writeLeShort((short) val, m_data.data().mapBytes, pos * 2); // TODO check endian
        } else {
            m_data.data().mapBytes[pos] = (byte) val;
        }
    }

    /**
     * グループ番号を返す (aka getGroup)
     */
    public int getGroupNumber(int pos) {
        int val = 0;
        if (pos < 0 || pos >= group_entries) return val;

        val = group_width > 1 ? ByteUtil.readLeShort(m_data.data().mapBytes, pos * 2) & 0xffff : m_data.data().mapBytes[pos] & 0xff; // TODO check endian
        if (basic.isDataInverted()) val ^= (group_width > 1 ? 0xffff : 0xff); // invert
        return val;
    }

    /**
     * エクステント番号を返す
     */
    public int getExtentNumber() {
        int num = m_data.data().extentNum & 0xff;
        if (basic.isDataInverted()) num ^= 0xff; // invert
        return num;
    }

    /**
     * レコード番号を返す
     */
    public int getRecordNumber() {
        int num = m_data.data().recordNum & 0xff;
        if (basic.isDataInverted()) num ^= 0xff; // invert
        return num;
    }

    /**
     * ファイルサイズからエクステント番号とレコード番号をセット
     */
    public void calcExtentAndRecordNumber(int val) {
        //int limit_size = (basic.getSectorSize() * basic.getSectorsPerGroup() * group_entries);

        if (val == 0) {
            m_data.data().extentNum = 0;
            m_data.data().recordNum = 0;
        } else {
            val = (val + SECTOR_UNIT_CPM - 1) / SECTOR_UNIT_CPM;
            if ((val % SECTOR_UNIT_CPM) == 0) {
                m_data.data().extentNum = (byte) ((val / SECTOR_UNIT_CPM) - 1);
                m_data.data().recordNum = (byte) SECTOR_UNIT_CPM;
            } else {
                m_data.data().extentNum = (byte) (val / SECTOR_UNIT_CPM);
                m_data.data().recordNum = (byte) (((val - 1) % SECTOR_UNIT_CPM) + 1);
            }
        }
        if (basic.isDataInverted()) {
            m_data.data().extentNum ^= 0xff; // invert
            m_data.data().recordNum ^= 0xff; // invert
        }
    }

    /**
     * 次のアイテムをセット
     */
    public void setNextItem(DiskBasicDirItem<DirectoryCpm> val) {
        next_item = (DiskBasicDirItemCPM) val;
    }

    /**
     * 次のアイテムを返す
     */
    public DiskBasicDirItemCPM getNextItem() {
        return next_item;
    }

    /**
     * アイテムソート用
     */
    public static int compare(DiskBasicDirItem<DirectoryCpm> item1, DiskBasicDirItem<DirectoryCpm> item2) {
        byte[] d1 = Arrays.copyOf(item1.getRawData(), DirectoryCpm.SIZE);
        byte[] d2 = Arrays.copyOf(item2.getRawData(), DirectoryCpm.SIZE);
        if (item1.getBasic().isDataInverted()) {
            Common.mem_invert(d1, DirectoryCpm.SIZE);
            Common.mem_invert(d2, DirectoryCpm.SIZE);
        }

        int cmp = 0;
        // ユーザID＋ファイル名＋拡張子＋エクステント番号
        cmp = Arrays.compare(d1, 0, 13, d2, 0, 13);
        // ＋レコード番号(逆)
        if (cmp == 0) cmp = Arrays.compare(d1, 15, 16, d2, 15, 16);
        // ＋マップ
        if (cmp == 0) cmp = Arrays.compare(d1, 16, 17, d2, 16, 17);
        return cmp;
    }

    /**
     * 名前比較
     */
    public static int compareName(DiskBasicDirItem<DirectoryCpm> item1, DiskBasicDirItem<DirectoryCpm> item2) {
        byte[] d1 = Arrays.copyOf(item1.getRawData(), DirectoryCpm.SIZE);
        byte[] d2 = Arrays.copyOf(item2.getRawData(), DirectoryCpm.SIZE);
        if (item1.getBasic().isDataInverted()) {
            Common.mem_invert(d1, DirectoryCpm.SIZE);
            Common.mem_invert(d2, DirectoryCpm.SIZE);
        }

        int cmp = 0;
        // ファイル名＋拡張子
        cmp = Arrays.compare(d1, 1, 12, d2, 1, 12);
        return cmp;
    }

    /**
     * ファイル名から属性を決定する
     */
    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = 0;
        // 拡張子で属性を設定する
        ftype = getFileTypeByExt(externalAttr, Utils.getExt(filename));
        return ftype;
    }

    /** グループ番号のエントリ数を返す */
    public int getGroupEntries() {
        return group_entries;
    }

    /**
     * プロパティで表示する内部データを設定
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TYPE", m_data.data().type & 0xff);
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXT", m_data.data().ext, m_data.data().ext.length);
        vals.add("EXTENT_NUM", m_data.data().extentNum & 0xff);
        vals.add("RESERVED", m_data.data().reserved, m_data.data().reserved.length);
        vals.add("RECORD_NUM", m_data.data().recordNum & 0xff);
        vals.add("MAP", m_data.data().mapBytes, m_data.data().mapBytes.length);
    }
}