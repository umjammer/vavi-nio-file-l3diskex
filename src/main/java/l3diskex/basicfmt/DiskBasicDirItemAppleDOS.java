package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.ApledosPtr;
import l3diskex.basicfmt.BasicCommon.DirectoryApledos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_INTEGER_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;


//
// ディレクトリ１アイテム Apple DOS 3.x
//
public class DiskBasicDirItemAppleDOS extends DiskBasicDirItem<DirectoryApledos> {

    static final ResourceBundle rb = ResourceBundle.getBundle("message");

    /// Apple DOS属性名
    public static final Map<String, Object> gTypeNameAppleDOS = new LinkedHashMap<>() {{
        put("Text", en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_TEXT.getValue());
        put("Integer BASIC", en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_IBASIC.getValue());
        put("Applesoft BASIC", en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_ABASIC.getValue());
        put("Binary", en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_BINARY.getValue());
        put("Read Only", en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_READ_ONLY.getValue());
    }};

    /**
     * Apple DOS属性位置
     */
    public enum en_type_name_appledos {
        TYPE_NAME_APLEDOS_TEXT(0),
        TYPE_NAME_APLEDOS_IBASIC(1), // Integer BASIC
        TYPE_NAME_APLEDOS_ABASIC(2), // Applesoft BASIC
        TYPE_NAME_APLEDOS_BINARY(3),
        TYPE_NAME_APLEDOS_READ_ONLY(4);

        private final int value;

        en_type_name_appledos(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Apple DOS属性値
     */
    public enum en_file_type_mask_appledos {
        FILETYPE_MASK_APLEDOS_TEXT(0x00),
        FILETYPE_MASK_APLEDOS_IBASIC(0x01),
        FILETYPE_MASK_APLEDOS_ABASIC(0x02),
        FILETYPE_MASK_APLEDOS_BINARY(0x04),
        FILETYPE_MASK_APLEDOS_READ_ONLY(0x80);

        private final int value;

        en_file_type_mask_appledos(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /// ///////////////////////////////////////////////////////////////////

    public static final int APLEDOS_TRACK_LIST_MAX = 122;

    static class apledos_chain_t {

        private final ByteBuffer buffer;
        private static final int SIZE = 256;

        public apledos_chain_t(byte[] buf) {
            this.buffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        }

        public apledos_chain_t() {
            this(new byte[SIZE]);
        }

        public ApledosPtr getNext() {
            ApledosPtr ptr = new ApledosPtr();
            ptr.nextTrack = buffer.get(0);
            ptr.nextSector = buffer.get(1);
            return ptr;
        }

        public void setNext(ApledosPtr ptr) {
            buffer.put(0, ptr.nextTrack);
            buffer.put(1, ptr.nextSector);
        }

        public short getNumber() {
            return buffer.getShort(4);
        }

        public byte getTrack(int idx) {
            if (idx < 0 || idx >= APLEDOS_TRACK_LIST_MAX) return 0;
            return buffer.get(12 + idx * 2);
        }

        public byte getSector(int idx) {
            if (idx < 0 || idx >= APLEDOS_TRACK_LIST_MAX) return 0;
            return buffer.get(12 + idx * 2 + 1);
        }

        public void setTrack(int idx, byte track) {
            if (idx < 0 || idx >= APLEDOS_TRACK_LIST_MAX) return;
            buffer.put(12 + idx * 2, track);
        }

        public void setSector(int idx, byte sector) {
            if (idx < 0 || idx >= APLEDOS_TRACK_LIST_MAX) return;
            buffer.put(12 + idx * 2 + 1, sector);
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }
    }

    //
    // Apple DOS トラックセクタリストの各セクタ
    //
    static class AppleDOSChains extends ArrayList<apledos_chain_t> {

        private boolean chain_ownmake;

        //
        // Apple DOS トラックセクタリストの各セクタ
        //
        public AppleDOSChains() {
            super();
            chain_ownmake = false;
        }

        public void Clear() {
            super.clear();
            chain_ownmake = false;
        }

        public void alloc() {
            if (chain_ownmake) {
                super.clear();
            }
            apledos_chain_t newitem = new apledos_chain_t();
            super.add(newitem);
            chain_ownmake = true;
        }
    }

    //
    // Apple DOS トラックセクタリスト
    //
    static class DiskBasicDirItemAppleDOSChain {

        private DiskBasic basic;
        private final AppleDOSChains chains;
        private apledos_chain_t chain;
        private DiskImageSector sector;
        private boolean chain_ownmake;

        private DiskBasicDirItemAppleDOSChain(DiskBasicDirItemAppleDOSChain src) {
            // Not implemented as per C++ private copy constructor
            chains = new AppleDOSChains();
        }

        public DiskBasicDirItemAppleDOSChain() {
            chains = new AppleDOSChains();
            basic = null;
        }

        // No explicit destructor needed in Java, rely on GC

        // COPYABLE_DIRITEM implementation in Java (simplified)
        public DiskBasicDirItemAppleDOSChain operatorAssignment(DiskBasicDirItemAppleDOSChain src) {
            this.dup(src);
            return this;
        }

        public void dup(DiskBasicDirItemAppleDOSChain src) {
            // Simplified: just copy over the reference/data structures
            // The original C++ had complex memory management
            this.chains.Clear();
            for (apledos_chain_t item : src.chains) {
                // Need a deep copy of apledos_chain_t's buffer if it's mutable
                apledos_chain_t newitem = new apledos_chain_t(item.getBuffer().array());
                this.chains.add(newitem);
            }
            this.chain_ownmake = src.chain_ownmake;
            this.basic = src.basic;
            // The sector and chain members in the C++ original seem to be for a different structure
        }

        /** BASICをセット */
        public void setBasic(DiskBasic n_basic) {
            basic = n_basic;
        }

        /** ポインタをセット */
        public void add(apledos_chain_t n_chain) {
            chains.add(n_chain);
        }

        /** メモリ確保 */
        public void alloc() {
            chains.alloc();
        }

        /** クリア */
        public void clear() {
            chains.Clear();
        }

        /** セクタ数を返す */
        public int count() {
            return chains.size();
        }

        /** 有効か */
        public boolean isValid() {
            return !chains.isEmpty();
        }

        /** トラック＆セクタを返す */
        public void getTrackAndSector(int idx, int[] track, int[] sector) {
            int max_idx = APLEDOS_TRACK_LIST_MAX;
            for (apledos_chain_t item : chains) {
                if (idx < max_idx) {
                    track[0] = item.getTrack(idx) & 0xFF;
                    sector[0] = item.getSector(idx) & 0xFF;
                    track[0] += basic.getTrackNumberBaseOnDisk();
                    sector[0] += basic.getSectorNumberBase();
                    break;
                }
                idx -= max_idx;
            }
        }

        /** トラック＆セクタを設定 */
        public void setTrackAndSector(int idx, int track, int sector) {
            int max_idx = APLEDOS_TRACK_LIST_MAX;
            for (apledos_chain_t item : chains) {
                if (idx < max_idx) {
                    track -= basic.getTrackNumberBaseOnDisk();
                    sector -= basic.getSectorNumberBase();
                    item.setTrack(idx, (byte) (track & 0xFF));
                    item.setSector(idx, (byte) (sector & 0xFF));
                    break;
                }
                idx -= max_idx;
            }
        }

        /** 次のセクタのあるセクタ番号を得る */
        public int getNext(int idx) {
            apledos_chain_t item = chains.get(idx);
            ApledosPtr next = item.getNext();
            return (next.nextTrack & 0xFF) * basic.diskBasicParam.getSectorsPerTrackOnBasic() + (next.nextSector & 0xFF);
        }

        /** 次のセクタのあるセクタ番号を設定 */
        public void setNext(int idx, int val) {
            apledos_chain_t item = chains.get(idx);
            ApledosPtr next = item.getNext();
            next.nextTrack = (byte) ((val / basic.getSectorsPerTrackOnBasic()) & 0xFF);
            next.nextSector = (byte) ((val % basic.getSectorsPerTrackOnBasic()) & 0xFF);
            item.setNext(next);
        }
    }

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryApledos> m_data = new DiskBasicDirData<>();

    /** ファイル内部で持っている開始アドレス */
    private int m_start_address;
    /** ファイル内部で持っているサイズ */
    private int m_data_length;

    /** トラック＆セクタリスト */
    private final DiskBasicDirItemAppleDOSChain chain = new DiskBasicDirItemAppleDOSChain();
    private final DiskBasicGroups m_groups = new DiskBasicGroups();

    public DiskBasicDirItemAppleDOS(DiskBasic basic) {
        super(basic);

        m_start_address = -1;
        m_data_length = -1;

        m_data.alloc(DirectoryApledos.class);
        chain.alloc();
    }

    public DiskBasicDirItemAppleDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) throws IOException {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_start_address = -1;
        m_data_length = -1;

        m_data.attach(DirectoryApledos.class, n_data);
    }

    public DiskBasicDirItemAppleDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_start_address = -1;
        m_data_length = -1;

        m_data.attach(DirectoryApledos.class, n_data, dataP);

        used(checkUsed(n_unuse[0]));

        // チェインセクタへのポインタをセット
        if (isUsed()) {
            chain.clear();
            chain.setBasic(basic);
            int grp = getStartGroup(0);
            while (grp != 0) {
                DiskImageSector sector = basic.getSectorFromGroup(grp);
                if (sector == null) break;

                byte[] buf = sector.getSectorBuffer();
                chain.add(new apledos_chain_t(buf));
                ApledosPtr p = new apledos_chain_t(buf).getNext();
                grp = type.getSectorPosFromNumS((p.nextTrack & 0xFF) + basic.getTrackNumberBaseOnDisk(), (p.nextSector & 0xFF) + basic.getSectorNumberBase());
            }
        }

        calcFileSize();
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
     * @param n_next   [out] 次のセクタ
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryApledos.class, n_data, dataP);
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

    /**
     * ファイル名を設定
     *
     * @param filename [in,out]  ファイル名
     * @param size     バッファサイズ
     * @param length   長さ
     *                 filename はデータビットが反転している場合あり
     */
    @Override
    protected void setNativeName(byte[] filename, int size, int[] length) {
        byte[] n;
        int[] nl = {0};
        int[] ns = {0};
        n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            int copySize = ns[0];
            if (copySize > size) copySize = size;
            // ファイル名はMSBをセット
            for (int i = 0; i < copySize; i++) {
                n[i] = (byte) (filename[i] | 0x80);
            }
        }
    }

    /**
     * ファイル名を得る
     *
     * @param filename [in,out] ファイル名
     * @param size     バッファサイズ
     * @param length   [out] 長さ
     */
    @Override
    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n = null;
        int[] s = {0};
        int[] l = {0};

        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            int copySize = s[0];
            if (copySize > size) copySize = size;
            // ファイル名はMSBをはずす
            for (int i = 0; i < copySize; i++) {
                filename[i] = (byte) (n[i] & 0x7f);
            }
            length[0] = l[0];
        } else {
            length[0] = 0;
        }
    }

    /** 属性１を返す */
    @Override
    public int getFileType1() {
        return m_data.data().type & 0xFF;
    }

    /** 属性１を設定 */
    @Override
    public void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !(m_data.data().track == (byte) 0xff || (m_data.data().track == 0 && m_data.data().sector == 0));
    }

    /** 削除 */
    @Override
    public boolean delete() {
        // 削除
        used(false);
        // ここで属性は更新しない
        return true;
    }

    /**
     * ディレクトリアイテムのチェック
     *
     * @param last [in,out]  チェックを終了するか
     * @return チェックOK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;

        if (m_data.data().track == 0 && m_data.data().sector == 0) {
            last[0] = true;
            return valid;
        }
        // 属性 3-6bitはゼロ
        if ((m_data.data().type & 0x78) != 0) {
            valid = false;
        }
        return valid;
    }

    /** 共通属性を個別属性に変換 */
    public static int convToFileType1(int ftype) {
        int type1 = 0;
        if ((ftype & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
            type1 = en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_ABASIC.getValue();
        } else if ((ftype & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) {
            type1 = en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_IBASIC.getValue();
        } else if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            type1 = en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_BINARY.getValue();
        } else {
            type1 = en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_TEXT.getValue();
        }
        if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            type1 |= en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_READ_ONLY.getValue();
        }
        return type1;
    }

    /** 個別属性を共通属性に変換 */
    public static int convFromFileType1(int type1) {
        int val = 0;
        if ((type1 & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_ABASIC.getValue()) != 0) {
            val = (FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_BASIC_MASK.getValue());
        } else if ((type1 & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_IBASIC.getValue()) != 0) {
            val = (FILE_TYPE_INTEGER_MASK.getValue() | FILE_TYPE_BASIC_MASK.getValue());
        } else if ((type1 & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_BINARY.getValue()) != 0) {
            val = (FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue());
        } else {
            val = (FILE_TYPE_ASCII_MASK.getValue() | FILE_TYPE_DATA_MASK.getValue());
        }
        if ((type1 & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_READ_ONLY.getValue()) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        return val;
    }

    /** 属性を設定 */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int type1 = convToFileType1(ftype);

        setFileType1(type1);
    }

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int type1 = getFileType1();
        int val = convFromFileType1(type1);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, type1);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        String str = "";
        int oval = getFileType1();

        if ((oval & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_IBASIC.getValue()) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(gTypeNameAppleDOS, en_type_name_appledos.TYPE_NAME_APLEDOS_IBASIC.getValue()));
        } else if ((oval & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_ABASIC.getValue()) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(gTypeNameAppleDOS, en_type_name_appledos.TYPE_NAME_APLEDOS_ABASIC.getValue()));
        } else if ((oval & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_BINARY.getValue()) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(gTypeNameAppleDOS, en_type_name_appledos.TYPE_NAME_APLEDOS_BINARY.getValue()));
        } else {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(gTypeNameAppleDOS, en_type_name_appledos.TYPE_NAME_APLEDOS_TEXT.getValue()));
        }
        if ((oval & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_READ_ONLY.getValue()) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(gTypeNameAppleDOS, en_type_name_appledos.TYPE_NAME_APLEDOS_READ_ONLY.getValue()));
        }
        return str;
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        m_groups.setSize(val);
        int sec_size = basic.getSectorSize();
        val = (val + sec_size - 1) / sec_size;
        setSectorCount(val + chain.count());
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileSize() throws IOException {
        calcFileUnitSize(0);
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileunit_num) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, m_groups);
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     *
     * @param fileunit_num ファイル番号
     * @param group_items  [out] グループリスト
     */
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) throws IOException {
        if (!chain.isValid()) return;

        int calc_groups = 0;
        int calc_file_size = 0;

        for (int i = 0; ; i++) {
            int[] track_num = {0};
            int[] sector_num = {0};
            chain.getTrackAndSector(i, track_num, sector_num);
            if (track_num[0] == 0 && sector_num[0] == 0) {
                break;
            }
            int group_num = type.getSectorPosFromNumS(track_num[0], sector_num[0]);
            int[] side_num = {0};
            int[] sector_num_out = {0};
            type.getNumFromSectorPos(group_num, track_num, side_num, sector_num_out);
            group_items.add(group_num, 0, track_num[0], side_num[0], sector_num_out[0], sector_num_out[0]);
            calc_groups++;
            calc_file_size += basic.getSectorSize();
            if (calc_groups >= basic.getFatEndGroup()) {
                // too large block size
                break;
            }
        }
        calc_groups += chain.count();
        if (getSectorCount() != calc_groups) {
            calc_groups = getSectorCount();
            calc_file_size = calc_groups * basic.getSectorSize();
        }
        group_items.setNums(calc_groups);
        group_items.setSize(calc_file_size);
        group_items.setSizePerGroup(basic.getSectorSize());

        // 最終セクタの再計算
        group_items.setSize(recalcFileSize(group_items, (int) group_items.getSize()));

        // ファイル内部のアドレスを得る
        takeAddressesInFile(group_items);
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
        byte[] buf = sector.getSectorBuffer();
        int remain_size = type.calcDataSizeOnLastSector(this, null, null, buf, 0, sector_size, sector_size);

        occupied_size = occupied_size - sector_size + remain_size;

        return occupied_size;
    }

    /** ファイル内部のアドレスを取り出す */
    public void takeAddressesInFile(DiskBasicGroups groupItems) {
        m_start_address = -1;
        m_data_length = -1;

        if (groupItems.size() == 0) {
            return;
        }

        DiskBasicGroupItem item = groupItems.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;

        int t1 = getFileType1();

        if ((t1 & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_BINARY.getValue()) != 0) {
            // バイナリ
            // 開始アドレス
            m_start_address = sector.get16(0);
            // データサイズ
            m_data_length = sector.get16(2);
            // 実際のサイズを設定
            if (m_data_length + 5 <= groupItems.getSize()) groupItems.setSize(m_data_length + 5);
        } else if ((t1 & (en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_IBASIC.getValue() | en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_ABASIC.getValue())) != 0) {
            // BASICファイルサイズ
            // データサイズ => 最終データ位置みたい
            m_data_length = sector.get16(0);
            // 実際のサイズを設定
            if (m_data_length + 3 <= groupItems.getSize()) groupItems.setSize(m_data_length + 3);
        }
    }

    /**
     * 最初のグループ番号を設定
     *
     * @param fileunit_num ファイル番号 (未使用)
     * @param val          グループ番号
     * @param size         ファイルサイズ (未使用)
     */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        int[] track_num = {0};
        int[] sector_num = {0};
        type.getNumFromSectorPosS(val, track_num, sector_num);
        m_data.data().track = (byte) ((track_num[0] - basic.getTrackNumberBaseOnDisk()) & 0xFF);
        m_data.data().sector = (byte) ((sector_num[0] - basic.getSectorNumberBase()) & 0xFF);
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileunit_num) {
        int val = type.getSectorPosFromNumS((m_data.data().track & 0xFF) + basic.getTrackNumberBaseOnDisk(), (m_data.data().sector & 0xFF) + basic.getSectorNumberBase());
        return val;
    }

    /** 追加のグループ番号を返す(機種依存) */
    @Override
    public int getExtraGroup() {
        return getStartGroup(0);
    }

    /** 追加のグループ番号を得る(機種依存) */
    @Override
    public void getExtraGroups(List<Integer> arr) {
        int gnum = getExtraGroup();
        for (int i = 0; i < chain.count(); i++) {
            arr.add(gnum);
            gnum = chain.getNext(i);
            if (gnum == 0) break;
        }
    }

    /**
     * チェイン用のセクタをクリア(機種依存)
     *
     * @param pitem コピー元のアイテム
     */
    @Override
    public void clearChainSector(DiskBasicDirItem pitem) {
        chain.clear();
        chain.setBasic(basic);
    }

    /**
     * チェイン用のセクタをセット
     *
     * @param sector セクタ
     * @param gnum   グループ番号
     * @param data   セクタ内のバッファ
     * @param pitem  コピー元のアイテム
     */
    @Override
    public void setChainSector(DiskImageSector sector, int gnum, byte[] data, DiskBasicDirItem pitem) {
        chain.add(new apledos_chain_t(data));
        if (chain.count() > 1) {
            int i = chain.count() - 2;
            chain.setNext(i, gnum);
        }
    }

    /**
     * チェイン用のセクタにグループ番号をセット(機種依存)
     *
     * @param idx インデックス
     * @param val グループ番号
     */
    @Override
    public void addChainGroupNumber(int idx, int val) {
        int[] track_num = {0};
        int[] sector_num = {0};
        type.getNumFromSectorPosS(val, track_num, sector_num);
        chain.setTrackAndSector(idx, track_num[0], sector_num[0]);
    }

    /** セクタカウントをセット(機種依存) */
    public void setSectorCount(int val) {
        m_data.data().sectorCount = (short) val; // be
    }

    /**
     * セクタカウントを返す(機種依存)
     * <p>
     * セクタカウントはトラックセクタリストで占有しているセクタ数も含んでいる
     */
    public int getSectorCount() {
        return m_data.data().sectorCount & 0xFFFF; // be
    }

    /** ファイルの終端コードをチェックする必要があるか */
    @Override
    public boolean needCheckEofCode() {
        return ((getFileType1() & 0x7f) == 0);
    }

    /**
     * セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合
     *
     * @param istream   入力ストリーム
     * @param file_size ファイルサイズ
     * @return 再計算後のファイルサイズ
     */
    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) {
        if (needCheckEofCode()) {
            // ファイルの最終が終端記号で終わっているかを調べる
            // ただし、ファイルサイズがセクタサイズで割り切れるなら終端記号は不要
            if ((file_size % basic.getSectorSize()) != 0) {
                file_size = checkEofCode(istream, file_size);
                file_size--;
            }
        }
        return file_size;
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryApledos getData() {
        return m_data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(DirectoryApledos val) {
        return m_data.copy(val, getDataSize());
    }

    /** ディレクトリをクリア */
    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getDeleteCode(), getDataSize());
    }

    /**
     * データをインポートする前に必要な処理
     *
     * @param filename [in,out] ファイル名
     * @return false このファイルは対象外とする
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /** アイテムを削除できるか */
    @Override
    public boolean isDeletable() {
        return true;
    }

    /** 最初のトラック番号をセット */
    public void setStartTrack(byte val) {
        m_data.data().track = val;
    }

    /** 最初のセクタ番号をセット */
    public void setStartSector(byte val) {
        m_data.data().sector = val;
    }

    /** 最初のトラック番号を返す */
    public byte getStartTrack() {
        return m_data.data().track;
    }

    /** 最初のセクタ番号を返す */
    public byte getStartSector() {
        return m_data.data().sector;
    }

    /** アイテムがアドレスを持っているか */
    @Override
    public boolean hasAddress() {
        return true;
    }

    /** アイテムが実行アドレスを持っているか */
    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    /** アドレスを編集できるか */
    @Override
    public boolean isAddressEditable() {
        return false;
    }

    /** 開始アドレスを返す */
    @Override
    public int getStartAddress() {
        return m_start_address;
    }

    /** 終了アドレスを返す */
    @Override
    public int getEndAddress() {
        return (m_start_address >= 0 && m_data_length > 0) ? m_start_address + m_data_length - 1 : -1;
    }

    /**
     * プロパティで表示する内部データを設定
     *
     * @param vals [in,out] 名前＆値のリスト
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TRACK", m_data.data().track);
        vals.add("SECTOR", m_data.data().sector);
        vals.add("TYPE", m_data.data().type);
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("SECTOR_COUNT", m_data.data().sectorCount);
    }
}
