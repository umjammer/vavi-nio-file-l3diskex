package l3diskex.basicfmt;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DirectoryTrsd13;
import l3diskex.basicfmt.BasicCommon.DirectoryTrsd23;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;


/**
 * ディレクトリ１アイテム TRSDOS Base
 */
public abstract class DiskBasicDirItemTRSDOS<T extends DirectoryT> extends DiskBasicDirItem<T> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    public static final int FILETYPE_MASK_TRSDOS_ACCESS = 0x07;
    public static final int FILETYPE_MASK_TRSDOS_INVISIBLE = 0x08;
    public static final int FILETYPE_MASK_TRSDOS_INUSE = 0x10;
    public static final int FILETYPE_MASK_TRSDOS_SYSTEM = 0x40;
    public static final int FILETYPE_MASK_TRSDOS_OVERFLOW = 0x80;

    static final Map<String, Object> gTypeNameTRSDOS = new LinkedHashMap<>() {{
        put("Invisible", FILETYPE_MASK_TRSDOS_INVISIBLE);
        put("System", FILETYPE_MASK_TRSDOS_SYSTEM);
        put("Overflow", FILETYPE_MASK_TRSDOS_OVERFLOW);
    }};

    /// TRSDOS属性名
    static final Map<String, Object> gTypeNameTRSDOS2 = new LinkedHashMap<>() {{
        put("SYS", FILETYPE_MASK_TRSDOS_SYSTEM);
    }};

    protected DiskBasicDirItemTRSDOS<T> next_item;
    protected int m_position_in_hit;

    public DiskBasicDirItemTRSDOS(DiskBasic basic) {
        super(basic);

        m_position_in_hit = -1;
        next_item = null;
    }

    public DiskBasicDirItemTRSDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_position_in_hit = -1;
        next_item = null;
    }

    public DiskBasicDirItemTRSDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_position_in_hit = -1;
        next_item = null;
    }

    /**
     * アイテムへのポインタを設定
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_position_in_hit = -1;
    }

    /**
     * 使用しているアイテムか
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        boolean used = false;
        if (m_position_in_hit >= 0) {
            used = (((DiskBasicTypeTRSDOS) type).getHI(m_position_in_hit) != 0);
        }
        used &= ((getFileType1() & FILETYPE_MASK_TRSDOS_INUSE) != 0);
        return used;
    }

    /**
     * ファイル内部のアドレスを取り出す
     */
    protected void takeAddressesInFile(DiskBasicGroups group_items) {
        if (group_items.size() == 0) {
            return;
        }
        //DiskBasicGroupItem item = group_items.get(0);
        //DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        //if (sector == null) return;

        // 開始アドレス
        //m_start_address = (int) sector.get16(0);
    }

    /**
     * 属性からリストの位置を返す
     */
    protected int convFileType1Pos(int type1) {
        return 0;
    }

    /**
     * 削除
     */
    @Override
    public boolean delete() throws IOException {
        // 削除
        used(false);
        setFileType1(0);
        // GATのエントリを削除
        type.deleteGroups(groups);
        // HITのエントリも削除
        if (m_position_in_hit >= 0) {
            ((DiskBasicTypeTRSDOS) type).deleteHI(m_position_in_hit);
        }
        // Overflowがあるとき
        if (next_item != null) {
            next_item.delete();
            next_item = null;
        }
        return true;
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        if (file_type.getFormat().getValue() == basic.getFormatTypeNumber().getValue()) {
            // 同じOSから
            int t1 = file_type.getOrigin(0);
            setFileType1(t1);
        } else {
            // 違うOSから
            int t1 = FILETYPE_MASK_TRSDOS_INUSE;

            if ((ftype & FILE_TYPE_HIDDEN_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_TRSDOS_INVISIBLE;
            }
            if ((ftype & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_TRSDOS_SYSTEM;
            }

            setFileType1(t1);
        }
    }

    /**
     * 属性を返す
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int t1 = getFileType1();

        if ((t1 & FILETYPE_MASK_TRSDOS_INVISIBLE) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((t1 & FILETYPE_MASK_TRSDOS_SYSTEM) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    /**
     * HITの位置をセット
     */
    public void setPositionInHIT(byte val) {
        m_position_in_hit = val;
    }

    /**
     * HITの位置を返す
     */
    public byte getPositionInHIT() {
        return (byte) m_position_in_hit;
    }

    /**
     * 次のアイテムをセット
     */
    public void setNextItem(DiskBasicDirItem<T> val) {
        next_item = (DiskBasicDirItemTRSDOS<T>) val;
    }

    /**
     * 次のアイテムを返す
     */
    public DiskBasicDirItemTRSDOS<T> getNextItem() {
        return next_item;
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     */
    @Override
    public String getFileAttrStr() {
        String str = "";
        int val = getFileType1();
        for (int i = 0; i < gTypeNameTRSDOS.size(); i++) {
            if ((val & (int) Utils.valueAt(gTypeNameTRSDOS, i)) != 0) {
                if (!str.isEmpty()) str += ", ";
                str += rb.getString(Utils.keyAt(gTypeNameTRSDOS, i));
            }
        }
        return str;
    }

    /**
     * 最終セクタのサイズを計算してファイルサイズを返す
     */
    @Override
    public int recalcFileSize(DiskBasicGroups group_items, int occupied_size) {
        return occupied_size;
    }

    /**
     * 最初のグループ番号をセット
     */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        setGranulesOnGap(0, val, size);
    }

    /**
     * 最初のグループ番号を返す
     */
    @Override
    public int getStartGroup(int fileunit_num) {
        return getGranulesOnGap(0, new int[1]);
    }

    /**
     * Overflowをセット
     */
    public void setOverflow(byte val) {
        // Default empty implementation
    }

    /**
     * Overflowを返す
     */
    public byte getOverflow() {
        return (byte) 0;
    }

    /**
     * GAPのGranule番号をセット
     */
    public void setGranulesOnGap(int pos, int val, int cnt) {
        // Default empty implementation
    }

    /**
     * GAPのGranule番号をクリア
     */
    public void clearGranulesOnGap(int pos, int track, int granule) {
        // Default empty implementation
    }

    /**
     * GAPのGranule番号を返す
     */
    public int getGranulesOnGap(int pos, int[] cnt /* = {0} */) {
        return 0;
    }

    /**
     * 新規ファイルとして設定
     */
    public void setAsNewFile() {
        // Default empty implementation
    }

    /**
     * Overflowファイルとして設定
     */
    public void setAsOverflowFile(byte position_in_hit, byte hash_code) {
        // Default empty implementation
    }

    /**
     * "BOOT/SYS"として設定
     */
    public void setAsBootSysEntry() {
        // Default empty implementation
    }

    /**
     * "DIR/SYS"として設定
     */
    public void setAsDirSysEntry() {
        // Default empty implementation
    }

    /**
     * ファイルの終端コードをチェックする必要があるか
     */
    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    /**
     * データをインポートする前に必要な処理
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        // 拡張子前の'.'を'/'に置き換える
        int pos = filename[0].indexOf('.');
        if (pos != -1) {
            filename[0] = filename[0].substring(0, pos) + (char) basic.diskBasicParam.getExtensionPreCode() + filename[0].substring(pos + 1);
        }
        return true;
    }

    /**
     * ファイル名から属性を決定する
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        // 拡張子で属性を設定する
        // Assuming IsContainAttrByExtension is a utility function
        // IsContainAttrByExtension(filename, gTypeNameTRSDOS2, 0, TYPE_NAME_2_TRSDOS_SYS, null, new int[]{t1}, null);
        return t1;
    }

    /**
     * アイテムを削除できるか
     */
    @Override
    public boolean isDeletable() {
        int stype = getFileType1();
        return (stype != FILETYPE_MASK_TRSDOS_SYSTEM);
    }

    /**
     * アイテムの属するセクタを変更済みにする
     */
    @Override
    public void setModify() {
        // if (m_data.IsSelf()) {
        // m_sdata.CopyFrom((directory_t)m_data.data());
        // }
    }

    /**
     * 属性１を返す
     */
    @Override
    public abstract int getFileType1();

    /**
     * 属性１を設定
     */
    @Override
    protected abstract void setFileType1(int val);
}

/**
 * ディレクトリ１アイテム TRSDOS 2.x
 */
class DiskBasicDirItemTRSD23 extends DiskBasicDirItemTRSDOS<DirectoryTrsd23> {

    protected DiskBasicDirData<DirectoryTrsd23> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemTRSD23(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryTrsd23.class);
    }

    public DiskBasicDirItemTRSD23(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryTrsd23.class, n_data, dataP);
        if (n_sector != null) {
            m_position_in_hit = DiskBasicTypeTRSD23.getHIPosition(n_sector.getSectorNumber() - basic.getSectorNumberBase(), n_secpos / getDataSize());
        }
    }

    public DiskBasicDirItemTRSD23(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryTrsd23.class, n_data, dataP);
        m_position_in_hit = DiskBasicTypeTRSD23.getHIPosition(n_sector.getSectorNumber() - basic.getSectorNumberBase(), n_secpos / getDataSize());

        used(checkUsed(n_unuse[0]));
    }

    /**
     * アイテムへのポインタを設定
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryTrsd23.class, n_data, dataP);
        m_position_in_hit = DiskBasicTypeTRSD23.getHIPosition(n_sector.getSectorNumber() - basic.getSectorNumberBase(), n_secpos / getDataSize());
    }

    /**
     * ディレクトリアイテムのチェック
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        int ov = getOverflow() & 0xff;
        if (ov > 0 && ov < 254) {
            // 参照元アイテムと関連付ける
            int[] ov_sec_num = {0};
            int[] ov_sec_pos = {0};
            ((DiskBasicTypeTRSD23) type).getFromHIPosition(ov, ov_sec_num, ov_sec_pos);
            // 通し番号を計算
            int num = (ov_sec_num[0] - 2) * basic.getSectorSize() / getDataSize() + ov_sec_pos[0];
            int mnum = (basic.diskBasicParam.getDirEndSector() - basic.diskBasicParam.getDirStartSector() + 1) * basic.getSectorSize() / getDataSize();
            if (num >= mnum) {
                // invalid chain
                return false;
            }
        }
        return true;
    }

    /**
     * 属性１を返す
     */
    @Override
    public int getFileType1() {
        return (m_data.data().accessControl);
    }

    /**
     * 属性１を設定
     */
    @Override
    protected void setFileType1(int val) {
        m_data.data().accessControl = (byte) (val & 0xff);
    }

    /**
     * Overflowをセット
     */
    @Override
    public void setOverflow(byte val) {
        m_data.data().overflow = (byte) (val & 0xff);
    }

    /**
     * Overflowを返す
     */
    @Override
    public byte getOverflow() {
        return m_data.data().overflow;
    }

    /**
     * ファイル名を格納する位置を返す
     */
    @Override
    public byte[] getFileNamePos(int num, int[] size, int[] len) {
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
    public byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

    /**
     * 新規ファイルとして設定
     */
    @Override
    public void setAsNewFile() {
        used(true);
        setFileType1(getFileType1() | FILETYPE_MASK_TRSDOS_INUSE);
        // HITエントリに登録
        byte h = DiskBasicTypeTRSD23.computeHI(m_data.data().name);
        if (m_position_in_hit >= 0) {
            ((DiskBasicTypeTRSDOS) type).setHI(m_position_in_hit, h);
        }
        // パスワード
        m_data.data().accessPassword = (short) 0x4296;
        m_data.data().updatePassword = (short) 0x4296;

        // エントリのクリア
        for (int pos = 0; pos < m_data.data().gap.length; pos++) {
            clearGranulesOnGap(pos, 0xff, 0xff);
        }
    }

    /**
     * Overflowファイルとして設定
     */
    @Override
    public void setAsOverflowFile(byte position_in_hit, byte hash_code) {
        clearData();

        used(true);
        visible(false);

        setFileType1(FILETYPE_MASK_TRSDOS_OVERFLOW | FILETYPE_MASK_TRSDOS_INUSE);
        // HITエントリに登録
        if (m_position_in_hit >= 0) {
            ((DiskBasicTypeTRSDOS) type).setHI(m_position_in_hit, hash_code);
        }
        setOverflow(position_in_hit);
        // パスワード (commented out in C++ but setting in SetAsNewFile suggests it might be intended)
        // m_data.data().access_password = wxUint16_SWAP_ON_BE(new wxUint16(0x4296));
        // m_data.data().update_password = wxUint16_SWAP_ON_BE(new wxUint16(0x4296));

        // エントリのクリア
        for (int pos = 0; pos < m_data.data().gap.length; pos++) {
            clearGranulesOnGap(pos, 0xff, 0xff);
        }
    }

    /**
     * "BOOT/SYS"として設定
     */
    @Override
    public void setAsBootSysEntry() {
        setFileNameStr("BOOT/SYS");
        setAsNewFile();
        setFileType1(FILETYPE_MASK_TRSDOS_SYSTEM | FILETYPE_MASK_TRSDOS_INUSE | FILETYPE_MASK_TRSDOS_INVISIBLE | 6);
        setStartGroup(0, 1, 0);
        setFileSize(basic.getSectorSize());
    }

    /**
     * "DIR/SYS"として設定
     */
    @Override
    public void setAsDirSysEntry() {
        setFileNameStr("DIR/SYS");
        setAsNewFile();
        setFileType1(FILETYPE_MASK_TRSDOS_SYSTEM | FILETYPE_MASK_TRSDOS_INUSE | FILETYPE_MASK_TRSDOS_INVISIBLE | 5);
        setStartGroup(0, basic.diskBasicParam.getManagedTrackNumber() * basic.diskBasicParam.getGroupsPerTrack() * basic.diskBasicParam.getSidesPerDiskOnBasic(), basic.diskBasicParam.getGroupsPerTrack());
        setFileSize(basic.getSectorsPerTrack() * basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.getSectorSize());
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
        int calc_groups = 0;
        int calc_file_size = 0;

        int sector_size = basic.getSectorSize();
        int block_size = sector_size * basic.diskBasicParam.getSectorsPerGroup();
        int max_group = basic.diskBasicParam.getFatEndGroup();

        int remain_size = getFileSize();

        for (int pos = 0; pos < m_data.data().gap.length; pos++) {
            int[] count = {0};
            int group_num = getGranulesOnGap(pos, count);
            if (group_num >= max_group) break;

            for (int i = 0; i < count[0]; i++) {
                basic.getNumsFromGroup(group_num, 0, sector_size, remain_size, group_items);
                group_num++;
                calc_groups++;
                calc_file_size += block_size;
                remain_size -= block_size;
            }
        }
        // overflowがあるとき
        if (next_item != null) {
            next_item.getUnitGroups(fileunit_num, group_items);
        }

        group_items.addNums(calc_groups);
        group_items.addSize(calc_file_size);
        group_items.setSizePerGroup(block_size);

        // ファイル内部のアドレスを得る
        takeAddressesInFile(group_items);
    }

    /**
     * ファイルサイズをセット
     */
    @Override
    public void setFileSize(int val) {
        int sector_size = basic.getSectorSize();
        int diva = (val / sector_size);
        int moda = (val % sector_size);
        if (moda != 0) {
            diva++;
        }
        m_data.data().eofSector = (short) diva;
        m_data.data().eofByteOffset = (byte) (moda & 0xff);
    }

    /**
     * ファイルサイズを返す
     */
    @Override
    public int getFileSize() {
        int sector_size = basic.getSectorSize();
        int val = m_data.data().eofSector * sector_size;
        if (m_data.data().eofByteOffset != 0) {
            val -= sector_size;
            val += m_data.data().eofByteOffset;
        }
        return val;
    }

    /**
     * GAPのGranule番号をセット
     */
    @Override
    public void setGranulesOnGap(int pos, int val, int cnt) {
        int blk = basic.diskBasicParam.getGroupsPerTrack() * basic.diskBasicParam.getSidesPerDiskOnBasic();
        int trk = val / blk;
        int sta = val % blk;
        m_data.data().gap[pos].track = (byte) (trk & 0xff);
        m_data.data().gap[pos].granules = (byte) (((sta << 5) & 0xe0) | ((cnt & 0x1f) - 1));
    }

    /**
     * GAPのGranule番号をクリア
     */
    @Override
    public void clearGranulesOnGap(int pos, int track, int granule) {
        m_data.data().gap[pos].track = (byte) (track & 0xff);
        m_data.data().gap[pos].granules = (byte) (granule & 0xff);
    }

    /**
     * GAPのGranule番号を返す
     */
    @Override
    public int getGranulesOnGap(int pos, int[] cnt) {
        int val = m_data.data().gap[pos].track * basic.diskBasicParam.getGroupsPerTrack() * basic.diskBasicParam.getSidesPerDiskOnBasic();
        int sta = ((m_data.data().gap[pos].granules & 0xe0) >> 5);
        val += sta;
        if (cnt != null && cnt.length > 0) {
            cnt[0] = (m_data.data().gap[pos].granules & 0x1f) + 1;
        }
        return val;
    }

    /**
     * ディレクトリアイテムのサイズ
     */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /**
     * アイテムを返す
     */
    @Override
    public DirectoryTrsd23 getData() {
        return m_data.data();
    }

    /**
     * アイテムをコピー
     */
    @Override
    public boolean copyData(DirectoryTrsd23 val) {
        return m_data.copy(val);
    }

    /**
     * ディレクトリをクリア
     */
    @Override
    public void clearData() {
        m_data.fill(0);
    }

    /**
     * プロパティで表示する内部データを設定
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("ACCESS_CONTROL", m_data.data().accessControl);
        vals.add("OVERFLOW", m_data.data().overflow);
        vals.add("EOF_BYTE_OFFSET", m_data.data().eofByteOffset);
        vals.add("RECORD_LENGTH", m_data.data().recordLength);
        vals.add("FILE_NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXTENSION", m_data.data().ext, m_data.data().ext.length);
        vals.add("UPDATE_PASSWORD", m_data.data().updatePassword);
        vals.add("ACCESS_PASSWORD", m_data.data().accessPassword);
        vals.add("EOF_SECTOR", m_data.data().eofSector);
        for (int i = 0; i < m_data.data().gap.length; i++) {
            vals.add(String.format("GAP%d TRACK", i + 1), m_data.data().gap[i].track);
            vals.add(String.format("GAP%d GRANULES", i + 1), m_data.data().gap[i].granules);
        }
    }
}

/**
 * ディレクトリ１アイテム TRSDOS 1.3
 */
class DiskBasicDirItemTRSD13 extends DiskBasicDirItemTRSDOS<DirectoryTrsd13> {

    protected DiskBasicDirData<DirectoryTrsd13> m_data = new DiskBasicDirData<>();

    public int getHIPosition(int pos) {
        return pos & 0xff;
    }

    public DiskBasicDirItemTRSD13(DiskBasic basic) {
        super(basic);
    }

    public DiskBasicDirItemTRSD13(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryTrsd13.class, n_data, dataP);
        if (n_sector != null) {
            int n = (basic.getSectorSize() / getDataSize());
            m_position_in_hit = getHIPosition((n_sector.getSectorNumber() - basic.getSectorNumberBase() - 2) * n + (n_secpos / getDataSize()));
        }
    }

    public DiskBasicDirItemTRSD13(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryTrsd13.class, n_data, dataP);
        m_position_in_hit = getHIPosition(n_num);

        used(checkUsed(n_unuse[0]));
    }

    /**
     * アイテムへのポインタを設定
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryTrsd13.class, n_data, dataP);
        m_position_in_hit = getHIPosition(n_num);
    }

    /**
     * ディレクトリアイテムのチェック
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        return true;
    }

    /**
     * アイテムが作成日時を持っているか
     */
    @Override
    public boolean hasCreateDateTime() {
        return true;
    }

    /**
     * アイテムが作成日付を持っているか
     */
    @Override
    public boolean hasCreateDate() {
        return true;
    }

    /**
     * 作成日付を得る
     *
     * @return
     */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int yy = m_data.data().year;
        if (yy < 80) {
            yy += 100;
        }
        return LocalDate.of(
                yy,
                m_data.data().month - 1,
                1);
    }

    /**
     * 作成日付を返す
     */
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateDate(tm);
        return Utils.formatYMDStr(tm);
    }

    /**
     * 作成日付をセット
     */
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        m_data.data().year = (byte) (tm.getYear() & 0xff);
        m_data.data().month = (byte) ((tm.getMonth().ordinal() + 1) & 0xff);
    }

    /**
     * ファイル名を格納する位置を返す
     */
    @Override
    public byte[] getFileNamePos(int num, int[] size, int[] len) {
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
    public byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

    /**
     * 属性１を返す
     */
    @Override
    public int getFileType1() {
        return m_data.data().accessControl;
    }

    /**
     * 属性１を設定
     */
    @Override
    protected void setFileType1(int val) {
        m_data.data().accessControl = (byte) (val & 0xff);
    }

    /**
     * 新規ファイルとして設定
     */
    @Override
    public void setAsNewFile() {
        used(true);
        setFileType1(getFileType1() | FILETYPE_MASK_TRSDOS_INUSE);
        // HITエントリに登録
        byte h = DiskBasicTypeTRSD23.computeHI(m_data.data().name);
        if (m_position_in_hit >= 0) {
            ((DiskBasicTypeTRSDOS) type).setHI(m_position_in_hit, h);
        }
        // パスワード
        m_data.data().accessPassword = (short) 0x5cef;
        m_data.data().updatePassword = (short) 0x5cef;

        // エントリのクリア
        for (int pos = 0; pos < m_data.data().gap.length; pos++) {
            clearGranulesOnGap(pos, 0xff, 0xff);
        }
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
        int calc_groups = 0;
        int calc_file_size = 0;

        int sector_size = basic.getSectorSize();
        int block_size = sector_size * basic.diskBasicParam.getSectorsPerGroup();
        int max_group = basic.getFatEndGroup();

        int remain_size = getFileSize();

        for (int pos = 0; pos < m_data.data().gap.length; pos++) {
            int[] count = {0};
            int group_num = getGranulesOnGap(pos, count);
            if (group_num >= max_group) break;

            for (int i = 0; i < count[0]; i++) {
                basic.getNumsFromGroup(group_num, 0, sector_size, remain_size, group_items);
                group_num++;
                calc_groups++;
                calc_file_size += block_size;
                remain_size -= block_size;
            }
        }
        // overflowがあるとき
        if (next_item != null) {
            next_item.getUnitGroups(fileunit_num, group_items);
        }

        group_items.addNums(calc_groups);
        group_items.addSize(calc_file_size);
        group_items.setSizePerGroup(block_size);

        // ファイル内部のアドレスを得る
        takeAddressesInFile(group_items);
    }

    /**
     * ファイルサイズをセット
     */
    @Override
    public void setFileSize(int val) {
        int diva = (val >> 8);
        m_data.data().eofSector = (short) diva;
        m_data.data().eofByteOffset = (byte) (val & 0xff);
    }

    /**
     * ファイルサイズを返す
     */
    @Override
    public int getFileSize() {
        int val = m_data.data().eofSector;
        val <<= 8;
        val |= m_data.data().eofByteOffset;
        return val;
    }

    /**
     * GAPのGranule番号をセット
     */
    @Override
    public void setGranulesOnGap(int pos, int val, int cnt) {
        int blk = basic.diskBasicParam.getGroupsPerTrack() * basic.getSidesPerDiskOnBasic();
        int trk = val / blk;
        int sta = val % blk;
        m_data.data().gap[pos].track = (byte) (trk & 0xff);
        m_data.data().gap[pos].granules = (byte) (((sta << 5) & 0xe0) | (cnt & 0x1f));
    }

    /**
     * GAPのGranule番号をクリア
     */
    @Override
    public void clearGranulesOnGap(int pos, int track, int granule) {
        m_data.data().gap[pos].track = (byte) (track & 0xff);
        m_data.data().gap[pos].granules = (byte) (granule & 0xff);
    }

    /**
     * GAPのGranule番号を返す
     */
    @Override
    public int getGranulesOnGap(int pos, int[] cnt) {
        int val = m_data.data().gap[pos].track * basic.diskBasicParam.getGroupsPerTrack() * basic.diskBasicParam.getSidesPerDiskOnBasic();
        int sta = ((m_data.data().gap[pos].granules & 0xe0) >> 5);
        val += sta;
        if (cnt != null && cnt.length > 0) {
            cnt[0] = m_data.data().gap[pos].granules & 0x1f;
        }
        return val;
    }

    /**
     * ディレクトリアイテムのサイズ
     */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /**
     * アイテムを返す
     */
    @Override
    public DirectoryTrsd13 getData() {
        return m_data.data();
    }

    /**
     * アイテムをコピー
     */
    @Override
    public boolean copyData(DirectoryTrsd13 val) {
        return m_data.copy(val);
    }

    /**
     * ディレクトリをクリア
     */
    @Override
    public void clearData() {
        m_data.fill(0);
    }

    /**
     * プロパティで表示する内部データを設定
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("ACCESS_CONTROL", m_data.data().accessControl);
        vals.add("MONTH", m_data.data().month);
        vals.add("YEAR", m_data.data().year);
        vals.add("EOF_BYTE_OFFSET", m_data.data().eofByteOffset);
        vals.add("RECORD_LENGTH", m_data.data().recordLength);
        vals.add("FILE_NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXTENSION", m_data.data().ext, m_data.data().ext.length);
        vals.add("UPDATE_PASSWORD", m_data.data().updatePassword);
        vals.add("ACCESS_PASSWORD", m_data.data().accessPassword);
        vals.add("EOF_SECTOR", m_data.data().eofSector);
        for (int i = 0; i < m_data.data().gap.length; i++) {
            vals.add(String.format("GAP%d TRACK", i + 1), m_data.data().gap[i].track);
            vals.add(String.format("GAP%d GRANULES", i + 1), m_data.data().gap[i].granules);
        }
    }
}
