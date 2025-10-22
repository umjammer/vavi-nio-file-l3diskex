package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;

import l3diskex.basicfmt.BasicCommon.DirectoryMdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;


/**
 * disk basic directory item for MDOS
 */
public class DiskBasicDirItemMDOS extends DiskBasicDirItem<DirectoryMdos> {

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryMdos> m_data = new DiskBasicDirData<>();

    /** ファイル名を格納する位置を返す */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            DirectoryMdos data = m_data.data();
            if (data != null && data.name != null) {
                size[0] = data.name.length;
                len[0] = data.name.length;
                return data.name;
            }
        }
        size[0] = 0;
        len[0] = 0;
        return null;
    }

    /** 拡張子を格納する位置を返す */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        DirectoryMdos data = m_data.data();
        if (data != null && data.ext != null) {
            len[0] = data.ext.length;
            return data.ext;
        }
        len[0] = 0;
        return null;
    }

//    /// 属性１を返す
//    int GetFileType1();

    /** 属性１のセット */
    @Override
    protected void setFileType1(int val) {
        // Implementation from .cpp is empty
    }

//    /// 属性を変換
//    int ConvFileType1(int file_type);

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (!unuse && this.m_data.data() != null && this.m_data.data().name != null && this.m_data.data().name[0] != 0);
    }

//    /// 属性からリストの位置を返す(プロパティダイアログ用)
//    int convFileType1Pos(int t1);
//    /// リストの位置から属性を返す(プロパティダイアログ用)
//    int calcFileTypeFromPos(int pos);
//    /// インポート時ダイアログ表示前にファイルの属性を設定
//    void setFileTypeForAttrDialog(int show_flags, final String name, int file_type_1, int file_type_2);

//    /// ファイル内部のアドレスを取り出す
//    void TakeAddressesInFile();

    public DiskBasicDirItemMDOS(DiskBasic basic) {
        super(basic);
        m_data.alloc(DirectoryMdos.class);
    }

    public DiskBasicDirItemMDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
        m_data.attach(n_data);
    }
    public DiskBasicDirItemMDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
        m_data.attach(n_data);
        boolean is_unuse = n_unuse[0];
        used(checkUsed(is_unuse));
        n_unuse[0] = (is_unuse || (m_data.data() != null && m_data.data().name != null && m_data.data().name[0] == 0));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, n_next);
        m_data.attach(n_data);
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

//    /// ENDマークがあるか(一度も使用していないか)
//    boolean			HasEndMark();
//    /// 次のアイテムにENDマークを入れる
//    void			SetEndMark(DiskBasicDirItem next_item);

//    /// 属性を設定
//    void			SetFileAttr(final DiskBasicFileType file_type);

    // Assuming FILE_TYPE_BINARY_MASK is defined elsewhere
    private static final int FILE_TYPE_BINARY_MASK = 0x0001; // Example value, needs actual definition

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = FILE_TYPE_BINARY_MASK; // Placeholder, assuming this is an integer constant

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
        // Assuming wxUINT16_SWAP_ON_LE is a byte swap for little-endian
        m_data.data().fileSize = (short)val; // le
    }

    /** ファイルサイズを返す */
    @Override
    public int getFileSize() {
        short val = m_data.data().fileSize;
        // Assuming wxUINT16_SWAP_ON_LE is a byte swap for little-endian
        return val /* le */ & 0xFFFF; // Return as int (unsigned short)
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    /** 指定ディレクトリのすべてのグループを取得 */
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) {
        int calc_file_size = 0;
        int calc_groups = 0;

        // 16bit FAT
        boolean rc = true;
        // GetStartGroup returns int (unsigned 32-bit in C++)
        int group_num = getStartGroup(fileunit_num);
        boolean working = true;
        // Assuming GetFatEndGroup returns int (unsigned 32-bit in C++)
        int limit = basic.getFatEndGroup() + 1;

        while(working) {
            // Assuming GetGroupNumber returns int (unsigned 32-bit in C++)
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
        // Assuming wxUINT16_SWAP_ON_BE is a byte swap for big-endian
        m_data.data().startGroup = (short)(val & 0xffff); // be
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileunit_num) {
        // Assuming wxUINT16_SWAP_ON_BE is a byte swap for big-endian
        return m_data.data().startGroup & 0xFFFF; // be
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
        // Assuming sizeof(DirectoryMdos) is implemented via a method on the class or known size
        return m_data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryMdos getData() {
        // Assuming DirectoryMdos extends directory_t or is castable
        return m_data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(DirectoryMdos val) {
        return m_data.copy(val);
    }

    /** ディレクトリをクリア ファイル新規作成時 */
    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir());
    }

    /** アイテムが実行アドレスを持っているか */
    @Override
    public boolean hasExecuteAddress() { return false; }

    /** ファイル名から属性を決定する */
    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = FILE_TYPE_BINARY_MASK;
        return ftype;
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        return t1;
    }

    //	/// @name プロパティダイアログ用
    //	//@{
//	/// ダイアログ内の属性部分のレイアウトを作成
//	void	CreateControlsForAttrDialog(IntNameBox parent, int show_flags, final String file_path, BoxSizer sizer, SizerFlags flags);
//	/// 属性を変更した際に呼ばれるコールバック
//	void	ChangeTypeInAttrDialog(IntNameBox parent);
//	/// 機種依存の属性を設定する
//	boolean	SetAttrInAttrDialog(final IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) const;
    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) { // Assuming KeyValArray is a Map
        DirectoryMdos data = m_data.data();
        vals.add("self", m_data.isSelf());
        vals.add("NAME", data.name, data.name.length); // Assuming byte[] is added
        vals.add("EXT", data.ext, data.ext.length); // Assuming byte[] is added
        vals.add("UNKNOWN", data.unknown);
        vals.add("START_GROUP", data.startGroup);
        vals.add("FILE_SIZE", data.fileSize);
    }
    //	//@}
}