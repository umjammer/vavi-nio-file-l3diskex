/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryM68fdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_WRITEONLY_MASK;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;


/**
 * ディレクトリ１アイテム Sord M68 FDOS (KDOS)
 */
public class DiskBasicDirItemM68FDOS extends DiskBasicDirItemMZBase<DirectoryM68fdos> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    public static final int FILETYPE_M68_FDOS_D = 0x0001;
    public static final int FILETYPE_M68_FDOS_C = 0x0080;
    public static final int FILETYPE_M68_FDOS_S = 0x0100;
    public static final int FILETYPE_M68_FDOS_X = 0x0800;
    public static final int FILETYPE_M68_FDOS_R = 0x1000;
    public static final int FILETYPE_M68_FDOS_W = 0x2000;
    public static final int FILETYPE_M68_FDOS_P = 0x4000;
    public static final int FILETYPE_M68_FDOS_A = 0x8000;

    public static final int EXT_NAME_M68_FDOS_SAV = 0;
    public static final int EXT_NAME_M68_FDOS_END = 1;

    /// M68 FDOS属性名
    public static final Map<String, Object> gTypeNameM68FDOS = new HashMap<>() {{
        put("A - Attribute Protected", FILETYPE_M68_FDOS_A);
        put("P - Permanent", FILETYPE_M68_FDOS_P);
        put("W - Write Protected", FILETYPE_M68_FDOS_W);
        put("R - Read Protected", FILETYPE_M68_FDOS_R);
        put("X - Xfer Protected", FILETYPE_M68_FDOS_X);
        put("S - Saved Memory Image", FILETYPE_M68_FDOS_S);
        put("C - Continuous", FILETYPE_M68_FDOS_C);
        put("D - Device", FILETYPE_M68_FDOS_D);
    }};

    /// M68 FDOS属性名(リスト用)
    public static final Map<String, Object> gTypeNameShortM68FDOS = new HashMap<>() {{
        put("A", FILETYPE_M68_FDOS_A);
        put("P", FILETYPE_M68_FDOS_P);
        put("W", FILETYPE_M68_FDOS_W);
        put("R", FILETYPE_M68_FDOS_R);
        put("X", FILETYPE_M68_FDOS_X);
        put("S", FILETYPE_M68_FDOS_S);
        put("C", FILETYPE_M68_FDOS_C);
        put("D", FILETYPE_M68_FDOS_D);
    }};

    /// M68 FDOS ファイル名マッピングテーブル
    public static final char[] sM68FDOS_CharMap = {
            ' ', '?', '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
            'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '+', '-',
    };

    /// M68 FDOS拡張子から属性を設定
    public static final Map<String, Object> gExtNameM68FDOS = new LinkedHashMap<>() {{
        put("SAV", FILETYPE_M68_FDOS_C);
    }};

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryM68fdos> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemM68FDOS(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryM68fdos.class);
    }

    public DiskBasicDirItemM68FDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryM68fdos.class, n_data, dataP);
    }

    public DiskBasicDirItemM68FDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryM68fdos.class, n_data, dataP);

        used(checkUsed(n_unuse[0]));

        calcFileSize();
    }

    /** ファイル名を格納する位置を返す */
    @Override
    public byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = 6;
            return m_data.data().name.b;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /** 拡張子を格納する位置を返す */
    @Override
    public byte[] getFileExtPos(int[] len) {
        len[0] = 3;
        return m_data.data().ext.b;
    }

    /** ファイル名を設定 */
    @Override
    public void setNativeName(byte[] filename, int size, int length) {
        int[] nl = {0};
        int[] ns = {0};
        byte[] n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            m_data.data().name.w[0] = basic.orderUint16(encodeName(Arrays.copyOfRange(filename, 0, size), 0, size));
            m_data.data().name.w[1] = basic.orderUint16(encodeName(Arrays.copyOfRange(filename, 3, size), 0, size - 3));
        }
    }

    /** 拡張子を設定 */
    @Override
    public void setNativeExt(byte[] fileext, int size, int length) {
        int[] el = {0};
        byte[] e = getFileExtPos(el);
        if (e != null && el[0] > 0) {
            m_data.data().ext.w = basic.orderUint16(encodeName(fileext, 0, size));
        }
    }

    /** ファイル名を得る */
    @Override
    public void getNativeName(byte[] filename, int size, int[] length) {
        int[] s = {0};
        int[] l = {0};

        byte[] n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            int copySize = s[0];
            if (copySize > size) copySize = size;
            decodeName(basic.orderUint16(m_data.data().name.w[0]), filename, 0, size);
            decodeName(basic.orderUint16(m_data.data().name.w[1]), filename, 3, size - 3);
        }

        length[0] = l[0];
    }

    /** 拡張子を得る */
    @Override
    public void getNativeExt(byte[] fileext, int size, int[] length) {
        int[] l = {0};

        byte[] e = getFileExtPos(l);
        if (e != null && l[0] > 0) {
            decodeName(basic.orderUint16(m_data.data().ext.w), fileext, 0, size);
        }

        length[0] = l[0];
    }

    /** 属性１を返す */
    @Override
    public int getFileType1() {
        return basic.orderUint16(m_data.data().attr1) & 0xffff;
    }

    /** 属性２を返す */
    @Override
    public int getFileType2() {
        return basic.orderUint16(m_data.data().attr2) & 0xffff;
    }

    /** 属性３を返す */
    @Override
    public int getFileType3() {
        return m_data.data().attr3 & 0xff;
    }

    /** 属性１のセット */
    @Override
    public void setFileType1(int val) {
        m_data.data().attr1 = basic.orderUint16((short) val);
    }

    /** 属性２のセット */
    @Override
    public void setFileType2(int val) {
        m_data.data().attr2 = basic.orderUint16((short) val);
    }

    /** 属性３のセット */
    @Override
    public void setFileType3(int val) {
        m_data.data().attr3 = (byte) (val & 0xff);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !(m_data.data().name.w[0] == 0 && m_data.data().name.w[1] == 0 && m_data.data().ext.w == 0);
    }

    /** リビジョンを返す */
    private String getRevisionStr() {
        String str = "";
        short rev = getRevision();
        if (rev != 0) {
            byte[] revstr = new byte[4];
            for (int i = 0; i < 4; i++) revstr[i] = 0;
            decodeName(rev, revstr, 0, 3);
            str = new String(revstr);
        }
        return str;
    }

    /** リビジョンを返す */
    public short getRevision() {
        return basic.orderUint16(m_data.data().rev.w);
    }

    /** リビジョンをセット */
    private void setRevision(short val) {
        m_data.data().rev.w = basic.orderUint16(val);
    }

    /** 属性を変換 */
    private int convToNativeType(int file_type) {
        int val = 0;
        if ((file_type & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
            val = FILETYPE_M68_FDOS_S;
        }
        if ((file_type & FILE_TYPE_HIDDEN_MASK.getValue()) != 0) {
            val |= FILETYPE_M68_FDOS_P;
        }
        if ((file_type & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            val |= FILETYPE_M68_FDOS_W;
        }
        if ((file_type & FILE_TYPE_WRITEONLY_MASK.getValue()) != 0) {
            val |= FILETYPE_M68_FDOS_R;
        }
        return val;
    }

    /** データ内にファイルサイズをセット */
    @Override
    public void setFileSizeBase(int val) {
        int sec_size = basic.getSectorSize();
        if (needChainInData()) {
            sec_size -= 2;
        }
        int sec = val / sec_size;
        int eof = val % sec_size;
        if (eof > 0) {
            sec++;
        }
        m_data.data().blockSize = basic.orderUint16((short) sec);
        m_data.data().eofInSector = (byte) (eof & 0xff);
    }

    /** データ内のファイルサイズを返す */
    @Override
    public int getFileSizeBase() {
        int sec_size = basic.getSectorSize();

        int val = basic.orderUint16(m_data.data().blockSize) & 0xffff;
        int eof = m_data.data().eofInSector & 0xff;
        val *= sec_size;
        if (eof > 0) {
            val += eof;
            val -= sec_size;
        }
        return val;
    }

    /** グループ取得計算中処理 */
    private void calcAllGroups(int calc_flags, int[] group_num, int[] remain, int[] sec_size, int[] end_sec, Object[] user_data) {
        if (needChainInData()) {
            // セクタ末尾にある次のセクタ番号を得る
            DiskImageSector sector = basic.getSectorFromGroup(group_num[0]);
            if (sector == null) {
                // Why?
                group_num[0] = INVALID_GROUP_NUMBER;
                return;
            }
            short nextGroupNum = sector.get16(-2, basic.isBigEndian());
            if (nextGroupNum == 0 || (nextGroupNum & 0xffff) > basic.diskBasicParam.getFatEndGroup()) {
                group_num[0] = INVALID_GROUP_NUMBER;
            } else {
                group_num[0] = nextGroupNum & 0xffff;
            }
        } else {
            // 連続している
            group_num[0]++;
        }
    }

    /** ファイル名をデコード */
    private static void decodeName(short code_val, byte[] name, int offset, int size) {
        int code = code_val & 0xffff;
        int sta = size > 2 ? 2 : size;
        for (int i = sta; i >= 0; i--) {
            int c = (code % 40);
            name[offset + i] = (byte) sM68FDOS_CharMap[c];
            code /= 40;
        }
    }

    /** ファイル名をエンコード */
    private static short encodeName(byte[] name, int offset, int size) {
        short code = 0;
        int fin = size < 3 ? size : 3;
        for (int i = 0; i < fin; i++) {
            code *= 40;

            int match = -1;
            for (int c = 0; c < 40; c++) {
                if ((name[offset + i] & 0xff) == (sM68FDOS_CharMap[c] & 0xff)) {
                    match = c;
                    break;
                }
            }
            if (match >= 0) {
                code += (short) (match % 40);
            }
        }
        return code;
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryM68fdos.class, n_data, dataP);
    }

    /** ディレクトリアイテムのチェック */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        short val = basic.orderUint16(m_data.data().name.w[0]);
        if ((val & 0xffff) >= 0xed80) {
            return false;
        }
        return true;
    }

    /** 削除 */
    @Override
    public boolean delete() throws IOException {
        // ファイル名をクリア
        m_data.data().name.w[0] = 0;
        m_data.data().name.w[1] = 0;
        m_data.data().ext.w = 0;
        used(false);
        // 開始グループを未使用にする
        type.setGroupNumber(getStartGroup(0), 0);
        return true;
    }

    /** 属性を設定 */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int t2 = 0;
        int t3 = 0;
        short rev = 0;
        if (file_type.getFormat().getValue() == basic.getFormatTypeNumber().getValue()) {
            t1 = file_type.getOrigin(0);
            t2 = file_type.getOrigin(1);
            int t3_rev = file_type.getOrigin(2);
            rev = (short) (t3_rev & 0xffff);
            t3 = t3_rev >> 16;
        } else {
            t1 = convToNativeType(ftype);
        }
        setFileType1(t1);
        setFileType2(t2);
        setFileType3(t3);
        setRevision(rev);
    }

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        if ((t1 & FILETYPE_M68_FDOS_S) != 0) {
            val = FILE_TYPE_SYSTEM_MASK.getValue();
        }
        if ((t1 & FILETYPE_M68_FDOS_P) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((t1 & FILETYPE_M68_FDOS_R) != 0) {
            val |= FILE_TYPE_WRITEONLY_MASK.getValue();
        }
        if ((t1 & FILETYPE_M68_FDOS_W) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        int t2 = getFileType2();
        int t3 = getFileType3();
        short rev = getRevision();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1, t2, (t3 << 16) | (rev & 0xffff));
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder();
        int ftype = getFileType1();
        for (Map.Entry<String, Object> e : gTypeNameShortM68FDOS.entrySet()) {
            if ((ftype & (int) e.getValue()) != 0) {
                if (!attr.isEmpty()) attr.append(", ");
                attr.append(rb.getString(e.getKey()));
            }
        }

        return attr.toString();
    }

    /** ファイルサイズを返す */
    @Override
    public int getFileSize() {
        int sec_size = basic.getSectorSize();
        if (needChainInData()) {
            sec_size -= 2;
        }
        int val = basic.orderUint16(m_data.data().blockSize) & 0xffff;
        int eof = m_data.data().eofInSector & 0xff;
        val *= sec_size;
        if (eof > 0) {
            val += eof;
            val -= sec_size;
        }
        return val;
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    /** 最初のグループ番号をセット */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        short start_sector = (short) (val & 0xffff);
        m_data.data().startSector = basic.orderUint16(start_sector);
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileunit_num) {
        return basic.orderUint16(m_data.data().startSector) & 0xffff;
    }

    /** 追加のグループ番号をセット */
    @Override
    public void setExtraGroup(int val) {
        short end_sector = (short) (val & 0xffff);
        m_data.data().endSector = basic.orderUint16(end_sector);
    }

    /** 追加のグループ番号を返す */
    @Override
    public int getExtraGroup() {
        return basic.orderUint16(m_data.data().endSector) & 0xffff;
    }

    /** 追加のグループ番号を得る */
    public void getExtraGroups(int[] arr) {
    }

    /** アイテムが日時を持っているか */
    @Override
    public boolean hasCreateDateTime() {
        return true;
    }

    @Override
    public boolean hasCreateDate() {
        return true;
    }

    @Override
    public boolean hasCreateTime() {
        return false;
    }

    /** アイテムの時間設定を無視することができるか */
    @Override
    public int canIgnoreDateTime() {
        return DiskBasicDirItem.DATETIME_ALL;
    }

    /** 日付を返す */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int date = basic.orderUint16(m_data.data().date) & 0xffff;

        int yy = (date >> 9) & 0x7f;
        if (yy < 70) yy += 100;
        return LocalDate.of(
                yy,
                ((date & 0x01e0) >> 5) - 1,
                date & 0x001f);
    }

    /** 日付を返す */
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /** 日付をセット */
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        int yy = (tm.getYear() & 0x007f);
        int mm = ((tm.getMonth().ordinal() + 1) & 0x000f);
        int dd = (tm.getDayOfMonth() & 0x001f);

        short date = (short) ((yy << 9) | (mm << 5) | dd);
        m_data.data().date = basic.orderUint16(date);
    }

    /** アイテムがアドレスを持っているか */
    @Override
    public boolean hasAddress() {
        return true;
    }

    /** 開始アドレスを返す */
    @Override
    public int getStartAddress() {
        return basic.orderUint16(m_data.data().loadAddr) & 0xffff;
    }

    /** 終了アドレスを返す */
    @Override
    public int getEndAddress() {
        return getStartAddress() + getFileSize() - 1;
    }

    /** 実行アドレスを返す */
    @Override
    public int getExecuteAddress() {
        return basic.orderUint16(m_data.data().execAddr) & 0xffff;
    }

    /** 開始アドレスをセット */
    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = basic.orderUint16((short) val);
    }

    /** 実行アドレスをセット */
    @Override
    public void setExecuteAddress(int val) {
        m_data.data().execAddr = basic.orderUint16((short) val);
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryM68fdos getData() {
        return m_data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val, getDataSize());
    }

    /** ディレクトリをクリア ファイル新規作成時 */
    @Override
    public void clearData() {
        if (!m_data.isValid()) return;

        m_data.fill(0);
    }

    /** データをチェインする必要があるか（非連続データか） */
    @Override
    public boolean needChainInData() {
        return ((getFileType1() & FILETYPE_M68_FDOS_C) == 0);
    }

    /** データをエクスポートする前に必要な処理 */
    public boolean preExportDataFile(String filename) {
        return true;
    }

    /** インポート時のダイアログを出す前にファイルパスから内部ファイル名を生成する */
    public boolean preImportDataFile(String filename) {
        return true;
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};

        isContainAttrByExtension(filename, gExtNameM68FDOS, 0, EXT_NAME_M68_FDOS_END - 1, null, t1, null);

        return t1[0];
    }

    /** 属性値を加工する */
    @Override
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        return true;
    }

    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", m_data.data().name.b, m_data.data().name.b.length);
        vals.add("EXT", m_data.data().ext.b, m_data.data().ext.b.length);
        vals.add("ATTR1", (byte) m_data.data().attr1, basic.isBigEndian());
        vals.add("ATTR2", (byte) m_data.data().attr2, basic.isBigEndian());
        vals.add("BLOCK_SIZE", (byte) m_data.data().blockSize, basic.isBigEndian());
        vals.add("EOF_IN_SEC", m_data.data().eofInSector);
        vals.add("DATE", (byte) m_data.data().date, basic.isBigEndian());
        vals.add("TIME?", (byte) m_data.data().time, basic.isBigEndian());
        vals.add("REV", m_data.data().rev.b, m_data.data().rev.b.length);
        vals.add("START_SECTOR", (byte) m_data.data().startSector, basic.isBigEndian());
        vals.add("ATTR3", m_data.data().attr3);
        vals.add("END_SECTOR?", (byte) m_data.data().endSector, basic.isBigEndian());
        vals.add("LOAD_ADDR", (byte) m_data.data().loadAddr, basic.isBigEndian());
        vals.add("EXEC_ADDR", (byte) m_data.data().execAddr, basic.isBigEndian());
        vals.add("UNKNOWN2", m_data.data().unknown2, m_data.data().unknown2.length);
    }
}
