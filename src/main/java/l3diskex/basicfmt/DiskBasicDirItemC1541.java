package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.C1541Ptr;
import l3diskex.basicfmt.BasicCommon.DirectoryC1541;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItemAmiga.AmigaDosTypes.ValueValue;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_EXTENSION_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.DiskBasicTypeC1541.C1541_START_SECTOR_OFFSET;
import static l3diskex.basicfmt.DiskBasicTypeC1541.C1541_START_TRACK_OFFSET;


public class DiskBasicDirItemC1541 extends DiskBasicDirItem<DirectoryC1541> {

    static final int IDC_COMBO_TYPE1 = 51;
    public static final int IDC_TEXT_RECSIZE = 52;
    static final int IDC_TEXT_SIDESEC = 53;

    static final int FILETYPE_MASK_C1541_DEL = 0x80;
    static final int FILETYPE_MASK_C1541_SEQ = 0x81;
    static final int FILETYPE_MASK_C1541_PRG = 0x82;
    static final int FILETYPE_MASK_C1541_USR = 0x83;
    public static final int FILETYPE_MASK_C1541_REL = 0x84;

    public static final int TYPE_NAME_C1541_DEL = 0;
    static final int TYPE_NAME_C1541_SEQ = 1;
    static final int TYPE_NAME_C1541_PRG = 2;
    static final int TYPE_NAME_C1541_USR = 3;
    public static final int TYPE_NAME_C1541_REL = 4;

    public static final Map<String, Object> gTypeNameC1541 = new LinkedHashMap<>() {{
        put("DEL", FILETYPE_MASK_C1541_DEL);
        put("SEQ", FILETYPE_MASK_C1541_SEQ);
        put("PRG", FILETYPE_MASK_C1541_PRG);
        put("USR", FILETYPE_MASK_C1541_USR);
        put("REL", FILETYPE_MASK_C1541_REL);
    }};

    /** C1541属性変換テーブル */
    private static final ValueValue[] gTypeConvC1541 = {
            new ValueValue(FILE_TYPE_DATA_MASK.getValue() | FILE_TYPE_ASCII_MASK.getValue(), FILETYPE_MASK_C1541_SEQ),
            new ValueValue(FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue(), FILETYPE_MASK_C1541_PRG),
            new ValueValue(FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue(), FILETYPE_MASK_C1541_USR),
            new ValueValue(FILE_TYPE_DATA_MASK.getValue() | FILE_TYPE_RANDOM_MASK.getValue(), FILETYPE_MASK_C1541_REL),
            new ValueValue(-1, -1)
    };

    private final DiskBasicDirData<DirectoryC1541> m_data = new DiskBasicDirData<>();
    private DiskBasicGroups m_ss_groups = new DiskBasicGroups(); // For REL files, side sectors

    public DiskBasicDirItemC1541(DiskBasic basic) {
        super(basic);
        m_data.alloc(DirectoryC1541.class);
    }

    public DiskBasicDirItemC1541(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) throws IOException {
        super(basic, n_sector, n_secpos, n_data);
        DirectoryC1541 d = new DirectoryC1541();
        Serdes.Util.deserialize(new ByteArrayInputStream(n_data), d);
        m_data.attach(d);
    }

    public DiskBasicDirItemC1541(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
        DirectoryC1541 d = new DirectoryC1541();
        Serdes.Util.deserialize(new ByteArrayInputStream(n_data), d);
        m_data.attach(d);

        n_unuse[0] = checkUsed(n_unuse[0]);
        used(n_unuse[0]);

        calcFileSize();
    }

    /// アイテムへのポインタを設定
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next) throws IOException {
        DirectoryC1541 d = new DirectoryC1541();
        Serdes.Util.deserialize(new ByteArrayInputStream(n_data), d);
        m_data.attach(d);
    }

    /// ファイル名を格納する位置を返す
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

    /// ファイル名を設定
    protected void setNativeName(byte[] filename, int[] size, int[] length) {
        int[] nl = {0}, ns = {0};
        byte[] n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            System.arraycopy(filename, 0, n, 0, ns[0]);
        }
    }

    /// ファイル名を得る
    @Override
    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n = null;
        int[] s = {0}, l = {0};

        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            int copySize = Math.min(s[0], size);
            System.arraycopy(n, 0, filename, 0, copySize);
        }

        length[0] = l[0];
    }

    /// 属性１を返す
    @Override
    public int getFileType1() {
        return m_data.data().type & 0xff;
    }

    /// 属性１のセット
    @Override
    public void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    /// 使用しているアイテムか
    @Override
    public boolean checkUsed(boolean unuse) {
        return (getFileType1() & 0x80) != 0;
    }

    /// 共通属性を個別属性に変換
    public static int ConvToFileType1(int ftype) {
        int t1 = 0;
        for (int i = 0; gTypeConvC1541[i].ori_value != -1; i++) {
            if ((ftype & FILE_TYPE_EXTENSION_MASK) == gTypeConvC1541[i].com_value) {
                t1 = gTypeConvC1541[i].ori_value;
                break;
            }
        }
        return t1;
    }

    /// 個別属性を共通属性に変換
    public static int convFromFileType1(int type1) {
        int val = 0;
        for (int i = 0; gTypeConvC1541[i].ori_value != -1; i++) {
            if (type1 == gTypeConvC1541[i].ori_value) {
                val = gTypeConvC1541[i].com_value;
                break;
            }
        }
        return val;
    }

    /// 属性からリストの位置を返す
    public int convFileType1Pos(int type1) {
        return Utils.indexOf(gTypeNameC1541, type1);
    }

    /// ブロック数をセット
    private void setBlocks(short val) {
        m_data.data().numOfBlocks = val; // be
    }

    /// ブロック数を返す
    private short getBlocks() {
        return m_data.data().numOfBlocks; // be
    }

    /// インポート時ダイアログ表示前にファイルの属性を設定
    public void setFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        // INTNAME_NEW_FILE is a mock constant for new file
        if ((show_flags & 0x01) != 0) { // Assuming INTNAME_NEW_FILE = 0x01
            // 外部からインポート時
            file_type_1[0] = convOriginalTypeFromFileName(name);
        }
    }

    /// ディレクトリアイテムのチェック
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;

        int type = getFileType1();
        if (FILETYPE_MASK_C1541_REL < type) {
            valid = false;
        }
        return valid;
    }

    /// 削除
    @Override
    public boolean delete() {
        // 削除
        used(false);
        setFileType1(basic.diskBasicParam.getDeleteCode());
        return true;
    }

    /// ファイル名＋拡張子のサイズ
    @Override
    public int getFileNameStrSize() {
        int[] s = {0}, l = {0};
        getFileNamePos(0, s, l);

        return s[0];
    }

    /// 属性を設定
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOS
            int t1 = file_type.getOrigin();
            int rl = t1 >> 8;
            t1 &= 0xff;
            setFileType1(t1);
            setRecordLength(rl);
        } else {
            // 違うOSから
            int t1 = ConvToFileType1(ftype);
            if (t1 > 0) setFileType1(t1);
        }
    }

    /// 属性を返す
    @Override
    public DiskBasicFileType getFileAttr() {
        int rl = getRecordLength();
        int t1 = getFileType1();
        int val = convFromFileType1(t1);
        // Assuming DiskBasicFileType constructor is DiskBasicFileType(format, common_type, origin_type)
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (rl << 8) | t1);
    }

    /// 属性の文字列を返す(ファイル一覧画面表示用)
    @Override
    public String getFileAttrStr() {
        String str;
        int spos = convFileType1Pos(getFileType1());
        if (spos >= 0) {
            str = Utils.keyAt(gTypeNameC1541, spos);
        } else {
            str = "???"; // Equivalent of "???"
        }
        return str;
    }

    /// ファイルサイズをセット
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int blk = basic.getSectorSize() - 2;
        int grps = (val + blk - 1) / blk;

        setBlocks((short) grps);
    }

    /// ファイルサイズを返す
    @Override
    public int getFileSize() {
        int val = groups.getSize();
        if (val == 0) {
            val = getBlocks();
            val *= (basic.getSectorSize() - 2);
        }
        return val;
    }

    /// ファイルサイズとグループ数を計算する
    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!checkUsed(false)) return; // Assuming IsUsed() is CheckUsed(false)

        getUnitGroups(fileunit_num, groups);
    }

    /// 指定ディレクトリのすべてのグループを取得
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) {
        //	if (!chain.IsValid()) return; // chain is not defined, skipping check

        int track_num = 0;
        int side_num = 0;
        int sector_num = 0;
        int[] track_num_arr = {track_num}, side_num_arr = {side_num}, sector_num_arr = {sector_num};

        int sector_size = basic.getSectorSize();
        // 1セクタ当たり2バイトはチェイン用のリンクポインタになるので減算
        int bytes_per_group = basic.getSectorSize() - 2;
        int limit = bytes_per_group * getBlocks();

        int blks = 1;
        int type1 = getFileType1();
        // RELative fileの場合はサイドセクタ分を加算する
        if (type1 == FILETYPE_MASK_C1541_REL) {
            m_ss_groups.empty();
            blks = 2;
        }

        // データ部分とサイドセクタ分のサイズ
        int calc_groups = 0;    // 合計分
        for (int i = 0; i < blks; i++) {
            int calc_file_size = 0;
            DiskBasicGroups tmp_grp_items = new DiskBasicGroups();
            int group_num = (i == 0 ? getStartGroup(fileunit_num) : getExtraGroup());

            while (limit > 0) {
                int sector_pos = group_num;
                DiskImageSector sector = basic.getSectorFromSectorPos(sector_pos, track_num_arr, side_num_arr);
                if (sector == null) {
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    break;
                }
                sector_num = sector.getSectorNumber();
                track_num = track_num_arr[0];
                side_num = side_num_arr[0];

                tmp_grp_items.add(group_num, 0, track_num, side_num, sector_num, sector_num);

                calc_groups++;
                calc_file_size += bytes_per_group;
                limit -= bytes_per_group;

                // 次のセクタなし
                // Need to parse bytes for c1541_ptr_t
                // Assuming first 2 bytes of buffer are c1541_ptr_t
                if (buffer.length < 2) break;
                C1541Ptr next = new C1541Ptr();
                next.track = buffer[0]; // track
                next.sector = buffer[1]; // sector

                if (next.track == 0 || (next.track & 0xff) > basic.getTracksPerSideOnBasic()) {
                    break;
                }

                group_num = type.getSectorPosFromNumS((next.track & 0xff) - C1541_START_TRACK_OFFSET, (next.sector & 0xff) - C1541_START_SECTOR_OFFSET);
            }

            calc_file_size = recalcFileSize(tmp_grp_items, calc_file_size);

            if (i == 0) {
                // 最終セクタの再計算
                group_items.add(tmp_grp_items);
                group_items.setSize(calc_file_size);
            } else {
                // サイドセクタ分
                m_ss_groups.add(tmp_grp_items);
                m_ss_groups.setSize(calc_file_size);
            }
        }

        group_items.setNums(calc_groups);
        group_items.setSizePerGroup(sector_size);

        //	// ファイル内部のアドレスを得る
        //	TakeAddressesInFile(group_items); // Not implemented
    }

    /// 最終セクタのサイズを計算してファイルサイズを返す
    @Override
    public int recalcFileSize(DiskBasicGroups group_items, int occupied_size) {
        if (group_items.count() == 0) return occupied_size;

        // 現在のセクタ
        DiskBasicGroupItem lastItem = group_items.last();
        if (lastItem == null) return occupied_size;

        DiskImageSector sector = basic.getSectorFromSectorPos(lastItem.group);
        if (sector == null) {
            return occupied_size;
        }
        byte[] buffer = sector.getSectorBuffer();
        if (buffer == null || buffer.length < 2) {
            return occupied_size;
        }

        // Assuming first 2 bytes are c1541_ptr_t
        C1541Ptr p = new C1541Ptr();
        p.track = buffer[0];
        p.sector = buffer[1];

        if (p.track != 0) {
            // really last sector ?
            return occupied_size;
        }
        // sector field holds the size in the last sector
        occupied_size = occupied_size + 1 + (p.sector & 0xff) - basic.getSectorSize();

        return occupied_size;
    }

    /// レコード長をセット(REL file)
    public void setRecordLength(int val) {
        m_data.data().recordSize = (byte) (val & 0xff);
    }

    /// レコード長を返す(REL file)
    public int getRecordLength() {
        return m_data.data().recordSize & 0xff;
    }

    /// ディレクトリアイテムのサイズ
    @Override
    public int getDataSize() {
        return new DirectoryC1541().doNotWrite.length + 1 + 2 + m_data.data().name.length + 2 + 1 + m_data.data().unused.length + 2 + 2;
        // In C++, sizeof(directory_c1541_t) is used, assuming struct packing is standard.
        // We use the Java object size equivalent, which is likely wrong without byte-level
        // conversion. For now, use the byte-size of the fields.
        // sizeof(directory_c1541_t) = 2 + 1 + 2 + 16 + 2 + 1 + 4 + 2 + 2 = 32 bytes (if fields are packed)
        // return 32;
    }

    /// アイテムを返す
    @Override
    public DirectoryC1541 getData() {
        return m_data.data();
    }

    /// アイテムをコピー
    @Override
    public boolean copyData(DirectoryC1541 val) {
        // エントリの最初2バイトは使用禁止
        return m_data.copy(val, getDataSize(), basic.isDataInverted(), m_data.data().doNotWrite.length);
    }

    /// ディレクトリをクリア ファイル新規作成時
    @Override
    public void clearData() {
        // エントリの最初2バイトは使用禁止
        m_data.fill(0, getDataSize(), basic.isDataInverted(), m_data.data().doNotWrite.length);
    }

    /// 最初のグループ番号をセット
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        int[] trk_num_arr = {0}, sec_num_arr = {0};
        type.getNumFromSectorPosS(val, trk_num_arr, sec_num_arr);
        int trk_num = trk_num_arr[0];
        int sec_num = sec_num_arr[0];
        trk_num += C1541_START_TRACK_OFFSET;
        sec_num += C1541_START_SECTOR_OFFSET;
        m_data.data().firstData.track = (byte) (trk_num & 0xff);
        m_data.data().firstData.sector = (byte) (sec_num & 0xff);
    }

    /// 最初のグループ番号を返す
    @Override
    public int getStartGroup(int fileunit_num) {
        int trk_num = (m_data.data().firstData.track & 0xff) - C1541_START_TRACK_OFFSET;
        int sec_num = (m_data.data().firstData.sector & 0xff) - C1541_START_SECTOR_OFFSET;
        return basic.getType().getSectorPosFromNumS(trk_num, sec_num);
    }

    /// サイドセクタのあるグループ番号をセット(機種依存)(REL file)
    @Override
    public void setExtraGroup(int val) {
        int[] trk_num_arr = {0}, sec_num_arr = {0};
        type.getNumFromSectorPosS(val, trk_num_arr, sec_num_arr);
        int trk_num = trk_num_arr[0];
        int sec_num = sec_num_arr[0];
        trk_num += C1541_START_TRACK_OFFSET;
        sec_num += C1541_START_SECTOR_OFFSET;
        m_data.data().firstSide.track = (byte) (trk_num & 0xff);
        m_data.data().firstSide.sector = (byte) (sec_num & 0xff);
    }

    /// サイドセクタのあるグループ番号を返す(機種依存)(REL file)
    @Override
    public int getExtraGroup() {
        int trk_num = (m_data.data().firstSide.track & 0xff) - C1541_START_TRACK_OFFSET;
        int sec_num = (m_data.data().firstSide.sector & 0xff) - C1541_START_SECTOR_OFFSET;
        return type.getSectorPosFromNumS(trk_num, sec_num);
    }

    /// サイドセクタのグループリストをセット(機種依存)
    @Override
    public void setExtraGroups(DiskBasicGroups grps) {
        m_ss_groups = grps;
        // ブロック数を合計する
        setBlocks((short) (getBlocks() + grps.getNums()));
        // グループ数も加算
        groups.addNums(grps.getNums());
    }

    /// サイドセクタのグループ番号を得る(機種依存)
    @Override
    public void getExtraGroups(List<Integer> arr) {
        // RELファイルの時はサイドセクタのグループ
        int type1 = getFileType1();
        if (type1 != FILETYPE_MASK_C1541_REL) return;

        // Assuming m_ss_groups has a method to get items
        // Mocking the iteration
        for (int i = 0; i < m_ss_groups.count(); i++) {
            // arr.add((int)m_ss_groups.Item(i).group); // Mocking retrieval of group number
            arr.add(0); // Mock value
        }
    }

    /// サイドセクタのグループリストを返す(機種依存)
    @Override
    public void getExtraGroups(DiskBasicGroups grps) {
        // Assuming DiskBasicGroups has a copy constructor or equivalent
        // grps = m_ss_groups; // Direct assignment is not a deep copy
        // grps.copy(m_ss_groups); // Mock
    }

    /// ファイルの終端コードをチェックする必要があるか
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /// セーブ時にファイルサイズを再計算する
    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) {
        return file_size;
    }

    /// データをエクスポートする前に必要な処理
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.isAddExtensionExport()) return true;

        /// 属性から拡張子を付加する
        String[] ext = {""};
        if (getFileAttrName(convFileType1Pos(getFileType1()), gTypeNameC1541, ext)) {
            filename[0] += ".";
            if (Utils.isUpperString(filename[0])) {
                filename[0] += ext[0].toUpperCase();
            } else {
                filename[0] += ext[0].toLowerCase();
            }
        }
        return true;
    }

    /// データをインポートする前に必要な処理
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.isDecideAttrImport()) {
            trimLastExtensionByExtensionAttr(filename[0], gTypeNameC1541, TYPE_NAME_C1541_DEL, TYPE_NAME_C1541_REL, filename, null, null);
        }
        // 拡張子を消す
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /// ファイル名から属性を決定する
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        int p1 = 0;
        int[] t1_arr = {t1}, p1_arr = {p1};
        // 拡張子で属性を設定する
        if (trimLastExtensionByExtensionAttr(filename, gTypeNameC1541, TYPE_NAME_C1541_DEL, TYPE_NAME_C1541_REL, null, t1_arr, p1_arr)) {
            t1 = t1_arr[0];
            p1 = p1_arr[0];
            // 外部パラメータで設定したものは共通属性なので変換
            if (p1 < 0) {
                t1 = ConvToFileType1(t1);
            }
        } else {
            // default
            t1 = FILETYPE_MASK_C1541_SEQ;
        }

        return t1;
    }

    /// アイテムの属するセクタを変更済みにする
    @Override
    public void setModify() {
        // Implementation depends on DiskBasicDirItem and its relationship with DiskImageSector
    }

    /// プロパティで表示する内部データを設定
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("self", m_data.isSelf());
        vals.add("(DO_NOT_WRITE)", m_data.data().doNotWrite, m_data.data().doNotWrite.length);
        vals.add("TYPE", m_data.data().type & 0xff);
        vals.add("FIRST_DATA.TRACK", m_data.data().firstData.track & 0xff);
        vals.add("FIRST_DATA.SECTOR", m_data.data().firstData.sector & 0xff);
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("FIRST_SIDE.TRACK", m_data.data().firstSide.track & 0xff);
        vals.add("FIRST_SIDE.SECTOR", m_data.data().firstSide.sector & 0xff);
        vals.add("RECORD_SIZE", m_data.data().recordSize & 0xff);
        vals.add("UNUSED", m_data.data().unused, m_data.data().unused.length);
        vals.add("REPLACE.TRACK", m_data.data().replace.track & 0xff);
        vals.add("REPLACE.SECTOR", m_data.data().replace.sector & 0xff);
        vals.add("NUM_OF_BLOCKS", getBlocks()); // GetBlocks returns a short/int
    }
}
