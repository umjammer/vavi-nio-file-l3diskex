/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemHU68K.DirectoryHu68k;
import l3diskex.basicfmt.diritem.DiskBasicDirItemLOSA.DirectoryLosa;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DirectoryMs;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ARCHIVE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;


/// ディレクトリ１アイテム MS-DOS
public class DiskBasicDirItemMSDOS extends DiskBasicDirItem<DirectoryMs> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ MS-DOS FAT (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMsDos implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte ntres;
        @Element(sequence = 5)
        public byte ctimeTenth;
        @Element(sequence = 6)
        public short ctime;
        @Element(sequence = 7)
        public short cdate;
        @Element(sequence = 8)
        public short adate;
        @Element(sequence = 9)
        public short startGroupHi;
        @Element(sequence = 10)
        public short wtime;
        @Element(sequence = 11)
        public short wdate;
        @Element(sequence = 12)
        public short startGroup;
        @Element(sequence = 13)
        public int fileSize;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MS-DOS LFN (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMsLfn implements DirectoryT {

        @Element(sequence = 1)
        public byte order;
        @Element(sequence = 2)
        public byte[] name = new byte[10];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte type2;
        @Element(sequence = 5)
        public byte chksum;
        @Element(sequence = 6)
        public byte[] name2 = new byte[12];
        @Element(sequence = 7)
        public short dummyGroup;
        @Element(sequence = 8)
        public byte[] name3 = new byte[4];

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MS-DOS compatible (32bytes)
     */
    public static class DirectoryMs implements DirectoryT {

        public DirectoryMsDos msdos = new DirectoryMsDos();
        public DirectoryMsLfn mslfn = new DirectoryMsLfn();
        public DirectoryHu68k hu68k = new DirectoryHu68k();
        public DirectoryLosa losa = new DirectoryLosa();

        public static final int SIZE = 32;
    }

    /// MS-DOS 属性名

    public static final int TYPE_NAME_MS_READ_ONLY = 0;
    public static final int TYPE_NAME_MS_HIDDEN = 1;
    public static final int TYPE_NAME_MS_SYSTEM = 2;
    public static final int TYPE_NAME_MS_VOLUME = 3;
    public static final int TYPE_NAME_MS_DIRECTORY = 4;
    public static final int TYPE_NAME_MS_ARCHIVE = 5;
    public static final int TYPE_NAME_MS_LFN = 6;
    public static final int TYPE_NAME_MS_END = 7;

    public static final int FILETYPE_MASK_MS_READ_ONLY = 0x01;
    public static final int FILETYPE_MASK_MS_HIDDEN = 0x02;
    public static final int FILETYPE_MASK_MS_SYSTEM = 0x04;
    public static final int FILETYPE_MASK_MS_VOLUME = 0x08;
    public static final int FILETYPE_MASK_MS_DIRECTORY = 0x10;
    public static final int FILETYPE_MASK_MS_ARCHIVE = 0x20;
    public static final int FILETYPE_MASK_MS_LFN = 0x0f; // long file name

    /// MS-DOS (MSX-DOS)
    static final Map<String, Object> gTypeNameMS = new LinkedHashMap<>() {{
        put("Read Only", FILE_TYPE_READONLY_MASK.getValue());
        put("Hidden", FILE_TYPE_HIDDEN_MASK.getValue());
        put("Sys", FILE_TYPE_SYSTEM_MASK.getValue());
        put("<VOL>", FILE_TYPE_VOLUME_MASK.getValue());
        put("<DIR>", FILE_TYPE_DIRECTORY_MASK.getValue());
        put("Arc", FILE_TYPE_ARCHIVE_MASK.getValue());
        put("(LFN)", FILE_TYPE_READONLY_MASK.getValue() | FILE_TYPE_HIDDEN_MASK.getValue() | FILE_TYPE_SYSTEM_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue());
    }};

    /// MS-DOS (MSX-DOS)
    static final Map<String, Object> gTypeNameMS_l = new LinkedHashMap<>() {{
        put("Read Only", FILE_TYPE_READONLY_MASK.getValue());
        put("Hidden", FILE_TYPE_HIDDEN_MASK.getValue());
        put("System", FILE_TYPE_SYSTEM_MASK.getValue());
        put("Volume Label", FILE_TYPE_VOLUME_MASK.getValue());
        put("Directory", FILE_TYPE_DIRECTORY_MASK.getValue());
        put("Archive", FILE_TYPE_ARCHIVE_MASK.getValue());
        put("int File Name", FILE_TYPE_READONLY_MASK.getValue() | FILE_TYPE_HIDDEN_MASK.getValue() | FILE_TYPE_SYSTEM_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue());
    }};

    //
    //
    //

    /** ディレクトリデータ */
    protected DiskBasicDirData<DirectoryMs> m_data = new DiskBasicDirData<>();

    //
    public DiskBasicDirItemMSDOS(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryMs.class);
    }

    //
    public DiskBasicDirItemMSDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryMs.class, n_data, dataP);
    }

    //
    public DiskBasicDirItemMSDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        // MS-DOS
        m_data.attach(DirectoryMs.class, n_data, dataP);
        used(checkUsed(n_unuse[0]));
        visible((getFileType1() & FILETYPE_MASK_MS_LFN) != FILETYPE_MASK_MS_LFN);
        n_unuse[0] = (n_unuse[0] || (m_data.data().msdos.name[0] == 0));

        // グループ数を計算
        calcFileSize();

        // カレント or 親ディレクトリはツリーに表示しない
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && (name.equals(".") || name.equals(".."))));
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryMs.class, n_data, dataP);
    }

    /** ファイル名を格納する位置を返す */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // MS-DOS
        int t1 = getFileType1();
        if (num == 0) {
            size[0] = len[0] = m_data.data().msdos.name.length;
            return m_data.data().msdos.name;
        } else if (num == 1) {
            if ((t1 & FILETYPE_MASK_MS_VOLUME) != 0) {
                // ボリュームラベルは拡張子もラベル名とする
                size[0] = len[0] = m_data.data().msdos.ext.length;
                return m_data.data().msdos.ext;
            }
        }
        size[0] = len[0] = 0;
        return null;
    }

    /** 拡張子を格納する位置を返す */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        byte[] p = null;
        len[0] = 0;
        // ボリュームラベルは拡張子なしにする
        if ((getFileType1() & FILETYPE_MASK_MS_VOLUME) == 0) {
            len[0] = m_data.data().msdos.ext.length;
            p = m_data.data().msdos.ext;
        }
        return p;
    }

    /** 属性１を返す */
    @Override
    public int getFileType1() {
        return m_data.data().msdos.type & 0xff;
    }

    /** 属性１のセット */
    @Override
    public void setFileType1(int val) {
        m_data.data().msdos.type = (byte) (val & 0xff);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return m_data.data().msdos.name[0] != 0 && (m_data.data().msdos.name[0] & 0xff) != 0xe5;
    }

    /** ファイル名を設定 */
    @Override
    public void setNativeName(byte[] filename, int size, int length) {
        if (length > 0) {
            // 0xe5は削除コードなので0x05に変換(Shift JISなど2バイト系文字など)
            if ((filename[0] & 0xff) == 0xe5) filename[0] = 0x05;
        }

        byte[] n;
        int nl = 0;
        int[] s = {0}, l = {0};

        int num = 0;
        do {
            s[0] = l[0] = 0;
            n = getFileNamePos(num, s, l);
            if (n == null || s[0] == 0) {
                break;
            }
            if (num > 0) {
                int pl = nl;
                int ps = s[0];
                if (nl < length) {
                    pl = length;
                    ps = s[0] + nl - length;
                }
                if (s[0] + nl > length) {
                    Arrays.fill(filename, pl, pl + ps, (byte) 0);
                }
            }

            if (s[0] > size) s[0] = size;
            System.arraycopy(filename, nl, n, 0, s[0]);

            nl += s[0];
            size -= s[0];
            num++;
        } while (num <= 1);
    }

    /** ファイル名を得る */
    public void getNativeName(byte[] filename, int[] size, int[] length) {
        byte[] n;
        int nl = 0;
        int num = 0;
        do {
            int[] s = {0}, l = {0};

            n = getFileNamePos(num, s, l);
            if (n == null || s[0] == 0) {
                break;
            }
            if (s[0] > size[0]) s[0] = size[0];
            System.arraycopy(n, 0, filename, nl, s[0]);

            nl += s[0];
            size[0] -= s[0];
            num++;
        } while (num <= 1);

        if (nl > 0) {
            // 0x05を0xe5に変換(Shift JISなど2バイト系文字など)
            if (filename[0] == 0x05) filename[0] = (byte) 0xe5;
        }

        length[0] = nl;
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    protected void getFileAttrStrSub(int ftype, StringBuilder attr) {
        for (int i = 0; i <= TYPE_NAME_MS_ARCHIVE; i++) {
            if ((ftype & (int) Utils.valueAt(gTypeNameMS, i)) != 0) {
                if (!attr.isEmpty()) attr.append(", ");
                attr.append(rb.getString(Utils.keyAt(gTypeNameMS, i)));
            }
        }
    }

    /** 日付を変換 */
    protected static LocalDate convDateToTm(short date) {
        int yy = ((date & 0xfe00) >> 9) + 80;
        return LocalDate.of(
                yy,
                ((date & 0x01e0) >> 5) - 1, // 0-11 for DateTime month
                date & 0x001f);
    }

    /** 時間を変換 */
    protected static LocalTime convTimeToTm(short time) {
        return LocalTime.of(
                (time & 0xf800) >> 11,
                (time & 0x07e0) >> 5,
                (time & 0x001f) << 1);
    }

    /** 日付に変換 */
    protected static short convTmToDate(LocalDateTime tm) {
        int yy = tm.getYear();
        int month = tm.getMonth().ordinal() + 1; // DateTime month is 0-11
        int date = (
                (((yy - 80) & 0x7f) << 9) |
                        ((month & 0xf) << 5) |
                        (tm.getDayOfMonth() & 0x1f)
        ) & 0xffff;
        return (short) date;
    }

    /** 時間に変換 */
    protected static short convTmToTime(LocalDateTime tm) {
        int time = (
                ((tm.getHour() & 0x1f) << 11) |
                        ((tm.getMinute() & 0x3f) << 5) |
                        ((tm.getSecond() & 0x3f) >> 1)
        ) & 0xffff;
        return (short) time;
    }

    /** ディレクトリアイテムのチェック */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        // ファイルサイズが16MBを超えている
        if (checkUsed(false) && m_data.data().msdos.fileSize > 0xff_ffff) {
            valid = false;
        }
        return valid;
    }

    /** アイテムを削除できるか */
    @Override
    public boolean isDeletable() {
        // ".", ".."は不可
        boolean valid = true;
        String name = getFileNamePlainStr();
        if (name.equals(".") || name.equals("..")) {
            valid = false;
        }
        return valid;
    }

    /** 削除 */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ (0xe5)
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /** ファイル名を編集できるか */
    @Override
    public boolean isFileNameEditable() {
        // ".", ".."は不可
        return isDeletable();
    }

    /** アイテムをロード・エクスポートできるか */
    @Override
    public boolean isLoadable() {
        // ボリュームラベルは不可
        int t1 = getFileType1();
        boolean valid = ((t1 & (FILETYPE_MASK_MS_DIRECTORY | FILETYPE_MASK_MS_VOLUME)) != FILETYPE_MASK_MS_VOLUME);
        // ".", ".."は不可
        valid &= isDeletable();
        return valid;
    }

    /// アイテムをコピー(内部でDnD)できるか
    @Override
    public boolean isCopyable() {
        // ".", ".."は不可
        return isDeletable();
    }

    /** アイテムを上書きできるか */
    @Override
    public boolean isOverWritable() {
        // ディレクトリ、ボリュームラベルは不可
        int t1 = getFileType1();
        boolean valid = ((t1 & (FILETYPE_MASK_MS_DIRECTORY | FILETYPE_MASK_MS_VOLUME)) == 0);
        // ".", ".."は不可
        valid &= isDeletable();
        return valid;
    }

    /** 属性を設定 */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        // MS-DOS
        setFileType1((ftype & 0xff00) >> 8);
    }

    /** 属性を返す */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        return new DiskBasicFileType(basic.getFormatTypeNumber(), t1 << 8, t1);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder();
        int ftype = getFileAttr().getType();
        // MS-DOS
        getFileAttrStrSub(ftype, attr);
        if (attr.isEmpty()) {
            attr.append("---");
        }
        return attr.toString();
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        m_data.data().msdos.fileSize = val;
    }

    /** ファイルサイズを返す */
    @Override
    public int getFileSize() {
        int val = m_data.data().msdos.fileSize;
        return val;
    }

    /// ディレクトリサイズをセット
    /// MS-DOS directories have a size of 0 in the entry
    @Override
    public void setDirectorySize(int val) {
        setFileSize(0);
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
        int calc_groups = 0;
        int calc_file_size = getFileSize();

        // 12bit FAT
        boolean rc = true;
        int group_num = getStartGroup(fileunit_num);
        boolean working = group_num >= 2;
        int remain = calc_file_size > 0 ? calc_file_size : 0x7ff_ffff;
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        while (working) {
            int next_group = type.getGroupNumber(group_num);
            if (next_group == group_num) {
                // 同じポジションならエラー
                rc = false;
            } else if (next_group >= 0xff8) {
                // 最終グループ
                working = false;
            } else if (next_group > basic.diskBasicParam.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            }
            if (rc) {
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), remain, group_items);
                //int m_file_size = basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup();
                remain -= basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup();
                calc_groups++;
                group_num = next_group;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        group_items.setNums(calc_groups);
        // 元のファイルサイズが０ならグループ数から計算したサイズを格納
        group_items.setSize(calc_file_size > 0 ? calc_file_size : basic.getSectorSize() * basic.getSectorsPerGroup() * calc_groups);
        group_items.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinit loop
            rc = false;
        }
    }

    /** 最初のグループ番号をセット */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        // MS-DOS
        m_data.data().msdos.startGroup = (short) val;
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileunit_num) {
        // MS-DOS
        return m_data.data().msdos.startGroup & 0xffff;
    }

    /** アイテムが更新日時を持っているか */
    @Override
    public boolean hasModifyDateTime() {
        return true;
    }

    @Override
    public boolean hasModifyDate() {
        return true;
    }

    @Override
    public boolean hasModifyTime() {
        return true;
    }

    /// 更新日付を得る
    ///
    /// @return date
    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        // MS-DOS
        short wdate = m_data.data().msdos.wdate;
        return convDateToTm(wdate);
    }

    /// 更新時間を得る
    ///
    /// @return time
    @Override
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        // MS-DOS
        short wtime = m_data.data().msdos.wtime;
        return convTimeToTm(wtime);
    }

    /** 更新日付を文字列で返す */
    @Override
    public String getFileModifyDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileModifyDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /** 更新時間を文字列で返す */
    @Override
    public String getFileModifyTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileModifyTime(tm);
        return Utils.formatHMSStr(lt);
    }

    /** 更新日付をセット */
    @Override
    public void setFileModifyDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            short wdate = convTmToDate(tm);
            m_data.data().msdos.wdate = wdate;
        }
    }

    /** 更新時間をセット */
    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            short wtime = convTmToTime(tm);
            m_data.data().msdos.wtime = wtime;
        }
    }

    /** 更新日付のタイトル名（ダイアログ用） */
    @Override
    public String getFileModifyDateTimeTitle() {
        return "Updated Date";
    }

    /** 日時の表示順序を返す（ダイアログ用） */
    @Override
    public int getFileDateTimeOrder(int idx) {
        return idx <= 1 ? 1 - idx : idx;
    }

    /** 日時を返す（ファイルリスト用） */
    @Override
    public String getFileDateTimeStr() {
        return getFileModifyDateTimeStr();
    }

    /** ディレクトリアイテムのサイズ */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryMs getData() {
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
        m_data.fill((byte) 0, getDataSize(), basic.isDataInverted(), 0);
    }

    /** ファイル名から属性を決定する */
    @Override
    public int convFileTypeFromFileName(String filename) {
        return FILE_TYPE_ARCHIVE_MASK.getValue();
    }

    /** ファイルの終端コードをチェックする必要があるか */
    @Override
    public boolean needCheckEofCode() {
        // テキストファイルかは拡張子で判断する
        boolean rc = false;
        MyAttribute sa = findUpperCase(basic.diskBasicParam.getAttributesByExtension(), getFileExtPlainStr());
        if (sa != null) {
            rc = ((sa.getType() & FILE_TYPE_ASCII_MASK.getValue()) != 0);
        }
        return rc;
    }

    /** セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合など */
    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) throws IOException {
        if (needCheckEofCode()) {
            // ファイル終端に終端文字があるか
            int curr_pos = (int) ((SeekableDataInputStream) istream).position();
            ((SeekableDataInputStream) istream).position(istream.available());
            if (istream.read() != basic.diskBasicParam.getTextTerminateCode()) {
                file_size++;
            }
            ((SeekableDataInputStream) istream).position(curr_pos);
        }
        return file_size;
    }

    //
    // ダイアログ用
    //

    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        // Assume KeyValArray has Add methods for boolean, byte[], int
        DirectoryMsDos msdos = m_data.data().msdos;
        vals.add("NAME", msdos.name, msdos.name.length);
        vals.add("EXT", msdos.ext, msdos.ext.length);
        vals.add("TYPE", msdos.type & 0xff);
        vals.add("NTRES", msdos.ntres & 0xff);
        vals.add("CTIME_TENTH", msdos.ctimeTenth & 0xff);
        vals.add("CTIME", msdos.ctime & 0xffff);
        vals.add("CDATE", msdos.cdate & 0xffff);
        vals.add("ADATE", msdos.adate & 0xffff);
        vals.add("START_GROUP_HI", msdos.startGroupHi & 0xffff);
        vals.add("WTIME", msdos.wtime & 0xffff);
        vals.add("WDATE", msdos.wdate & 0xffff);
        vals.add("START_GROUP", msdos.startGroup & 0xffff);
        vals.add("FILE_SIZE", msdos.fileSize);
    }

    ///
    ///
    ///

    /// ディレクトリ１アイテム MS-DOS VFAT
    public static class DiskBasicDirItemVFAT extends DiskBasicDirItemMSDOS {

        public DiskBasicDirItemVFAT(DiskBasic basic) {
            super(basic);
        }

        public DiskBasicDirItemVFAT(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
            super(basic, n_sector, n_secpos, n_data, dataP);
        }

        public DiskBasicDirItemVFAT(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
            super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);
        }

        /** ファイル名を格納する位置を返す */
        @Override
        protected byte[] getFileNamePos(int num, int[] size, int[] len) {
            // MS-DOS
            int t1 = getFileType1();
            DirectoryMs data = m_data.data();

            if (num == 0) {
                if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                    // ロングファイルネーム
                    size[0] = len[0] = data.mslfn.name.length;
                    return data.mslfn.name;
                } else {
                    // Short file name (SFN entry structure: msdos)
                    return data.msdos.name;
                }
            } else if (num == 1) {
                if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                    // ロングファイルネーム
                    size[0] = len[0] = data.mslfn.name2.length;
                    return data.mslfn.name2;
                } else if ((t1 & FILETYPE_MASK_MS_VOLUME) != 0) {
                    // ボリュームラベルは拡張子もラベル名とする
                    size[0] = len[0] = data.msdos.ext.length;
                    return data.msdos.ext;
                }
            } else if (num == 2) {
                if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                    // ロングファイルネーム
                    size[0] =len[0] =  data.mslfn.name3.length;
                    return data.mslfn.name3;
                }
            }
            size[0] = len[0] = 0;
            return null;
        }

        /** ファイル名を設定 */
        protected void setNativeName(byte[] filename, int[] size, int length) {
            if (length > 0) {
                // 0xe5は削除コードなので0x05に変換(Shift JISなど2バイト系文字など)
                if ((filename[0] & 0xff) == 0xe5) filename[0] = 0x05;
            }

            byte[] n;
            int nl = 0;
            int[] s = {0}, l = {0};

            int num = 0;
            do {
                s[0] = l[0] = 0;
                n = getFileNamePos(num, s, l);
                if (n == null || s[0] == 0) {
                    break;
                }
                if (num > 0) {
                    int pl = nl;
                    int ps = s[0];
                    if (nl < length) {
                        pl = length;
                        ps = s[0] + nl - length;
                    }
                    Arrays.fill(filename, pl, pl + ps, (byte) 0);
                }

                if (s[0] > size[0]) s[0] = size[0];
                System.arraycopy(filename, nl, n, 0, s[0]);

                nl += s[0];
                size[0] -= s[0];
                num++;
            } while (num <= 4);
        }

        /** ファイル名を得る */
        @Override
        protected void getNativeName(byte[] filename, int size, int[] length) {
            byte[] n = null;
            int nl = 0;
            int num = 0;
            do {
                int[] s = {0}, l = {0};

                n = getFileNamePos(num, s, l);
                if (n == null || s[0] == 0) {
                    break;
                }
                if (s[0] > size) s[0] = size;
                System.arraycopy(n, 0, filename, nl, s[0]);

                nl += s[0];
                size -= s[0];
                num++;
            } while (num <= 4);

            if (nl > 0) {
                // 0x05を0xe5に変換(Shift JISなど2バイト系文字など)
                if (filename[0] == 0x05) filename[0] = (byte) 0xe5;
            }

            length[0] = nl;
        }

        /** ディレクトリアイテムのチェック */
        @Override
        public boolean check(boolean[] last) {
            if (!m_data.isValid()) return false;

            boolean valid = true;
            // ファイルサイズが16MBを超えている
            if (checkUsed(false) && (m_data.data().msdos.type & FILETYPE_MASK_MS_LFN) != FILETYPE_MASK_MS_LFN && m_data.data().msdos.fileSize > 0xff_ffff) {
                valid = false;
            }
            return valid;
        }

        /** アイテムを削除できるか */
        @Override
        public boolean isDeletable() {
            boolean valid = true;
            int t1 = getFileType1();
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                valid = false;
            } else if ((t1 & FILETYPE_MASK_MS_DIRECTORY) != 0) {
                String name = getFileNamePlainStr();
                if (name.equals(".") || name.equals("..")) {
                    // ディレクトリ ".", ".."は削除不可
                    valid = false;
                }
            }
            return valid;
        }

        /** ファイル名を編集できるか */
        @Override
        public boolean isFileNameEditable() {
            boolean valid = true;
            int t1 = getFileType1();
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                valid = false;
            } else if ((t1 & FILETYPE_MASK_MS_DIRECTORY) != 0) {
                String name = getFileNamePlainStr();
                if (name.equals(".") || name.equals("..")) {
                    // ディレクトリ ".", ".."は編集不可
                    valid = false;
                }
            }
            return valid;
        }

        /** 属性を設定 */
        @Override
        public void setFileAttr(DiskBasicFileType file_type) {
            int ftype = file_type.getType();
            if (ftype == -1) return;

            // MS-DOS
            setFileType1((ftype & 0xff00) >> 8);
        }

        /** 属性を返す */
        @Override
        public DiskBasicFileType getFileAttr() {
            int t1 = getFileType1();
            return new DiskBasicFileType(basic.getFormatTypeNumber(), t1 << 8, t1);
        }

        /** 属性の文字列を返す(ファイル一覧画面表示用) */
        @Override
        public String getFileAttrStr() {
            StringBuilder attr = new StringBuilder();
            int ftype = getFileAttr().getType();
            // MS-DOS
            if ((getFileAttr().getOrigin() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                if (!attr.isEmpty()) attr.append(", ");
                attr.append(Utils.keyAt(gTypeNameMS, TYPE_NAME_MS_LFN)); // long file name
            } else {
                getFileAttrStrSub(ftype, attr);
            }
            if (attr.isEmpty()) {
                attr.append("---");
            }
            return attr.toString();
        }

        /** 最初のグループ番号をセット */
        @Override
        public void setStartGroup(int fileunit_num, int val, int size) {
            // MS-DOS
            m_data.data().msdos.startGroup = (short) val;
        }

        /** 最初のグループ番号を返す */
        @Override
        public int getStartGroup(int fileunit_num) {
            // MS-DOS
            return m_data.data().msdos.startGroup & 0xffff;
        }

        /** アイテムが作成日時を持っているか */
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
            return true;
        }

        /// アイテムの時間設定を無視できるか
        @Override
        public int canIgnoreDateTime() {
            return DATETIME_CREATE_ACCESS;
        }

        /// 作成日付を返す
        ///
        /// @return data
        @Override
        public LocalDate getFileCreateDate(LocalDateTime tm) {
            short cdate = m_data.data().msdos.cdate;
            return convDateToTm(cdate);
        }

        /// 作成時間を得る
        ///
        /// @return time
        @Override
        public LocalTime getFileCreateTime(LocalDateTime tm) {
            short ctime = m_data.data().msdos.ctime;
            return convTimeToTm(ctime);
        }

        /** 作成日付を文字列で返す */
        @Override
        public String getFileCreateDateStr() {
            LocalDateTime tm = LocalDateTime.now();
            LocalDate ld = getFileCreateDate(tm);
            return Utils.formatYMDStr(ld);
        }

        /** 作成時間を文字列で返す */
        @Override
        public String getFileCreateTimeStr() {
            LocalDateTime tm = LocalDateTime.now();
            LocalTime lt = getFileCreateTime(tm);
            return Utils.formatHMSStr(lt);
        }

        /** 作成日付をセット */
        @Override
        public void setFileCreateDate(LocalDateTime tm) {
            if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
                short cdate = convTmToDate(tm);
                m_data.data().msdos.cdate = cdate;
            }
        }

        /** 作成時間をセット */
        @Override
        public void setFileCreateTime(LocalDateTime tm) {
            if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
                short ctime = convTmToTime(tm);
                m_data.data().msdos.ctime = ctime;
            }
        }

        /** アイテムがアクセス日時を持っているか */
        @Override
        public boolean hasAccessDateTime() {
            return true;
        }

        @Override
        public boolean hasAccessDate() {
            return true;
        }

        @Override
        public boolean hasAccessTime() {
            return false;
        }

        /** アクセス日付を返す */
        @Override
        public LocalDate getFileAccessDate() {
            short adate = m_data.data().msdos.adate;
            return convDateToTm(adate);
        }

        /** アクセス日付を返す */
        @Override
        public String getFileAccessDateStr() {
            LocalDateTime tm = LocalDateTime.now();
            LocalDate ld = getFileAccessDate();
            return Utils.formatYMDStr(ld);
        }

        /** アクセス日付をセット */
        @Override
        public void setFileAccessDate(LocalDateTime tm) {
            if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
                short adate = convTmToDate(tm);
                m_data.data().msdos.adate = adate;
            }
        }

        /** 文字列をバイト列に変換 文字コードは機種依存 */
        @Override
        public int convStringToChars(String src, byte[] dst, int len) {
            if ((getFileType1() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // ロングファイル名は常にUTF-16
                byte[] buf = src.getBytes(StandardCharsets.UTF_16);
                if (buf.length > 0) {
                    int l = Math.min(buf.length, len);
                    System.arraycopy(buf, 0, dst, 0, l);
                    return l;
                } else {
                    return 0;
                }
            } else {
                dst = src.getBytes(basic.getCharCodes().charset());
                return 0;
            }
        }

        /** バイト列を文字列に変換 文字コードは機種依存 */
        public void convCharsToString(byte[] src, int len, String[] dst) {
            if ((getFileType1() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // ロングファイル名は常にUTF-16
                dst[0] = new String(src, 0, len, StandardCharsets.UTF_16);
            } else {
                dst[0] = new String(src, 0, len, basic.getCharCodes().charset());
            }
        }

        //
        // ダイアログ用
        //

        /** その他の属性値を設定する */
        @Override
        public void setOptionalAttr(DiskBasicDirItemAttr attr) {
        }

        /** プロパティで表示する内部データを設定 */
        @Override
        public void setInternalDataInAttrDialog(KeyValArray vals) {
            int t1 = getFileType1();
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // int File Name entry
                DirectoryMsLfn mslfn = m_data.data().mslfn;
                vals.add("ORDER", mslfn.order & 0xff);
                vals.add("NAME", mslfn.name, mslfn.name.length);
                vals.add("TYPE", mslfn.type & 0xff);
                vals.add("TYPE2", mslfn.type2 & 0xff);
                vals.add("CHKSUM", mslfn.chksum & 0xff);
                vals.add("NAME2", mslfn.name2, mslfn.name2.length);
                vals.add("DUMMY_GROUP", mslfn.dummyGroup & 0xffff);
                vals.add("NAME3", mslfn.name3, mslfn.name3.length);
            } else {
                // Short File Name entry
                super.setInternalDataInAttrDialog(vals);
            }
        }
    }
}
