///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;

import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMDOS.DirectoryMdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;


/**
 * ディレクトリ１アイテム MDOS
 */
public class DiskBasicDirItemMDOS extends DiskBasicDirItem<DirectoryMdos> {

    /**
     * ディレクトリエントリ MDOS (16bytes)
     */
    @Serdes
    public static class DirectoryMdos implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte unknown; // byte
        @Element(sequence = 4, bigEndian = "false")
        public short startGroup; // little endien
        @Element(sequence = 5)
        public short fileSize; // big endien

        public static final int SIZE = 16;
    }

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryMdos> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemMDOS(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryMdos.class);
    }

    public DiskBasicDirItemMDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryMdos.class, n_data, dataP);
    }

    public DiskBasicDirItemMDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryMdos.class, n_data, dataP);
        used(checkUsed(n_unuse[0]));
        n_unuse[0] = (n_unuse[0] || (m_data.data() != null && m_data.data().name != null && m_data.data().name[0] == 0));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryMdos.class, n_data, dataP);
    }

    /** ディレクトリアイテムのチェック */
    @Override
    public boolean check(boolean[] last) {
        byte[] data = m_data.getRawData();
        return DiskBasicDirItem.checkData((data != null) ? data : null, getDataSize(), last);
    }

    /** 削除 */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /** ファイル名を格納する位置を返す */
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

    /** 拡張子を格納する位置を返す */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

//    /** 属性１を返す */
//    public int getFileType1();

    /** 属性１のセット */
    @Override
    protected void setFileType1(int val) {
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.m_data.data().name[0] != 0;
    }

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = FILE_TYPE_BINARY_MASK.getValue();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, 0);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        return "";
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        m_data.data().fileSize = (short) val; // le
    }

    /** ファイルサイズを返す */
    @Override
    public int getFileSize() {
        short val = m_data.data().fileSize;
        return val /* le */ & 0xffff;
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileunit_num) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    /** 指定ディレクトリのすべてのグループを取得 */
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) throws IOException {
        int calc_file_size = 0;
        int calc_groups = 0;

        // 16bit FAT
        boolean rc = true;
        int group_num = getStartGroup(fileunit_num);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int next_group = basic.getType().getGroupNumber(group_num);
            if (next_group == group_num) {
                // 同じポジションならエラー
                rc = false;
            } else if (next_group == basic.diskBasicParam.getGroupFinalCode()) {
                // 最終グループ(0xffff)
                working = false;
            } else if (next_group > basic.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            } else if (next_group >= basic.diskBasicParam.getGroupSystemCode()) {
                // システム領域はエラー(0xeeee)
                rc = false;
            }
            if (rc) {
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), 0, group_items);
                calc_file_size += (basic.getSectorSize() * basic.getSectorsPerGroup());
                calc_groups++;
                group_num = next_group;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        group_items.addNums(calc_groups);
        group_items.addSize(calc_file_size >= getFileSize() ? getFileSize() : calc_file_size);
        group_items.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinit loop
            rc = false;
        }
    }

    /** 最初のグループ番号をセット */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        m_data.data().startGroup = (short) (val & 0xffff); // be
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileunit_num) {
        return m_data.data().startGroup & 0xffff; // be
    }

    /** ファイルの終端コードをチェックする必要があるか */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /** セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合など */
    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) {
        return file_size;
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryMdos getData() {
        return m_data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val);
    }

    /** ディレクトリをクリア ファイル新規作成時 */
    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir());
    }

    /** アイテムが実行アドレスを持っているか */
    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = FILE_TYPE_BINARY_MASK.getValue();
        return ftype;
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        return t1;
    }

    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXT", m_data.data().ext, m_data.data().ext.length);
        vals.add("UNKNOWN", m_data.data().unknown);
        vals.add("START_GROUP", m_data.data().startGroup);
        vals.add("FILE_SIZE", m_data.data().fileSize);
    }
}
