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
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemHU68K.DirectoryHu68k;
import l3diskex.basicfmt.diritem.DiskBasicDirItemLOSA.DirectoryLosa;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DirectoryMs;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DiskBasicDirItemVFAT.DirectoryMsLfn;
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
import static l3diskex.basicfmt.type.DiskBasicTypeHU68K.FORMAT_TYPE_HU68K;
import static l3diskex.basicfmt.type.DiskBasicTypeMSDOS.FORMAT_TYPE_CDOS2;
import static l3diskex.basicfmt.type.DiskBasicTypeMSDOS.FORMAT_TYPE_LOSA;
import static l3diskex.basicfmt.type.DiskBasicTypeMSDOS.FORMAT_TYPE_MSDOS;


/** ディレクトリ１アイテム MS-DOS */
public class DiskBasicDirItemMSDOS extends DiskBasicDirItem<DirectoryMs> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ MS-DOS FAT (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMsDos implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte ntRes;
        @Element(sequence = 5)
        public byte cTimeTenth;
        @Element(sequence = 6)
        public short cTime;
        @Element(sequence = 7)
        public short cDate;
        @Element(sequence = 8)
        public short aDate;
        @Element(sequence = 9)
        public short startGroupHi;
        @Element(sequence = 10)
        public short wTime;
        @Element(sequence = 11)
        public short wDate;
        @Element(sequence = 12)
        public short startGroup;
        @Element(sequence = 13)
        public int fileSize;

        public static final int SIZE = 32;
    }


    /**
     * ディレクトリエントリ MS-DOS compatible (32bytes)
     */
    public static class DirectoryMs implements Directory {

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
    static final Map<String, Object> typeNameMS = new LinkedHashMap<>() {{
        put("Read Only", FILE_TYPE_READONLY_MASK.getValue());
        put("Hidden", FILE_TYPE_HIDDEN_MASK.getValue());
        put("Sys", FILE_TYPE_SYSTEM_MASK.getValue());
        put("<VOL>", FILE_TYPE_VOLUME_MASK.getValue());
        put("<DIR>", FILE_TYPE_DIRECTORY_MASK.getValue());
        put("Arc", FILE_TYPE_ARCHIVE_MASK.getValue());
        put("(LFN)", FILE_TYPE_READONLY_MASK.getValue() | FILE_TYPE_HIDDEN_MASK.getValue() | FILE_TYPE_SYSTEM_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue());
    }};

    /// MS-DOS (MSX-DOS)
    static final Map<String, Object> typeNameMSl = new LinkedHashMap<>() {{
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
    protected DiskBasicDirData<DirectoryMs> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(DiskBasicFormatType formatType) {
        return formatType == FORMAT_TYPE_CDOS2;
    }

    //
    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryMs.class);
    }

    //
    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);

        this.data.attach(DirectoryMs.class, data, dataPos);
    }

    //
    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        // MS-DOS
        this.data.attach(DirectoryMs.class, data, dataPos);
        used(checkUsed(unuse[0]));
        visible((getFileType1() & FILETYPE_MASK_MS_LFN) != FILETYPE_MASK_MS_LFN);
        unuse[0] = (unuse[0] || (this.data.data().msdos.name[0] == 0));

        // グループ数を計算
        calcFileSize();

        // カレント or 親ディレクトリはツリーに表示しない
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && (name.equals(".") || name.equals(".."))));
    }

    /** アイテムへのポインタを設定 */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryMs.class, data, dataPos);
    }

    /** ファイル名を格納する位置を返す */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // MS-DOS
        int t1 = getFileType1();
        if (num == 0) {
            size[0] = len[0] = data.data().msdos.name.length;
            return data.data().msdos.name;
        } else if (num == 1) {
            if ((t1 & FILETYPE_MASK_MS_VOLUME) != 0) {
                // ボリュームラベルは拡張子もラベル名とする
                size[0] = len[0] = data.data().msdos.ext.length;
                return data.data().msdos.ext;
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
            len[0] = data.data().msdos.ext.length;
            p = data.data().msdos.ext;
        }
        return p;
    }

    /** 属性１を返す */
    @Override
    public int getFileType1() {
        return data.data().msdos.type & 0xff;
    }

    /** 属性１のセット */
    @Override
    public void setFileType1(int val) {
        data.data().msdos.type = (byte) (val & 0xff);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return data.data().msdos.name[0] != 0 && (data.data().msdos.name[0] & 0xff) != 0xe5;
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
    protected void getFileAttrStrSub(int fType, StringBuilder attr) {
        for (int i = 0; i <= TYPE_NAME_MS_ARCHIVE; i++) {
            if ((fType & (int) Utils.valueAt(typeNameMS, i)) != 0) {
                if (!attr.isEmpty()) attr.append(", ");
                attr.append(rb.getString(Utils.keyAt(typeNameMS, i)));
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
        if (!data.isValid()) return false;

        boolean valid = true;
        // ファイルサイズが16MBを超えている
        if (checkUsed(false) && data.data().msdos.fileSize > 0xff_ffff) {
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
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
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
    public void setFileAttr(DiskBasicFileType fileType) {
        int ftype = fileType.getType();
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
        int fType = getFileAttr().getType();
        // MS-DOS
        getFileAttrStrSub(fType, attr);
        if (attr.isEmpty()) {
            attr.append("---");
        }
        return attr.toString();
    }

    /** ファイルサイズをセット */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        data.data().msdos.fileSize = val;
    }

    /** ファイルサイズを返す */
    @Override
    public int getFileSize() {
        int val = data.data().msdos.fileSize;
        return val;
    }

    /**
     * ディレクトリサイズをセット
     * MS-DOS directories have a size of 0 in the entry
     */
    @Override
    public void setDirectorySize(int val) {
        setFileSize(0);
    }

    /** ファイルサイズとグループ数を計算する */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /** 指定ディレクトリのすべてのグループを取得 */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        int calcGroups = 0;
        int calcFileSize = getFileSize();

        // 12bit FAT
        boolean rc = true;
        int groupNum = getStartGroup(fileUnitNum);
        boolean working = groupNum >= 2;
        int remain = calcFileSize > 0 ? calcFileSize : 0x7ff_ffff;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int nextGroup = type.getGroupNumber(groupNum);
            if (nextGroup == groupNum) {
                // 同じポジションならエラー
                rc = false;
            } else if (nextGroup >= 0xff8) {
                // 最終グループ
                working = false;
            } else if (nextGroup > basic.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            }
            if (rc) {
                basic.getNumsFromGroup(groupNum, nextGroup, basic.getSectorSize(), remain, groupItems);
                //int fileSize = basic.getSectorSize() * basic.getSectorsPerGroup();
                remain -= basic.getSectorSize() * basic.getSectorsPerGroup();
                calcGroups++;
                groupNum = nextGroup;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        groupItems.setNums(calcGroups);
        // 元のファイルサイズが０ならグループ数から計算したサイズを格納
        groupItems.setSize(calcFileSize > 0 ? calcFileSize : basic.getSectorSize() * basic.getSectorsPerGroup() * calcGroups);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinite loop
            rc = false;
        }
    }

    /** 最初のグループ番号をセット */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        // MS-DOS
        data.data().msdos.startGroup = (short) val;
    }

    /** 最初のグループ番号を返す */
    @Override
    public int getStartGroup(int fileUnitNum) {
        // MS-DOS
        return data.data().msdos.startGroup & 0xffff;
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

    /**
     * 更新日付を得る
     *
     * @return date
     */
    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        // MS-DOS
        short wdate = data.data().msdos.wDate;
        return convDateToTm(wdate);
    }

    /**
     * 更新時間を得る
     *
     * @return time
     */
    @Override
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        // MS-DOS
        short wtime = data.data().msdos.wTime;
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
            data.data().msdos.wDate = wdate;
        }
    }

    /** 更新時間をセット */
    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            short wtime = convTmToTime(tm);
            data.data().msdos.wTime = wtime;
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
        return data.getDataSize();
    }

    /** アイテムを返す */
    @Override
    public DirectoryMs getData() {
        return data.data();
    }

    /** アイテムをコピー */
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val, getDataSize());
    }

    /** ディレクトリをクリア ファイル新規作成時 */
    @Override
    public void clearData() {
        data.fill((byte) 0, getDataSize(), basic.isDataInverted(), 0);
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
        MyAttribute attr = findUpperCase(basic.getAttributesByExtension(), getFileExtPlainStr());
        if (attr != null) {
            rc = ((attr.getType() & FILE_TYPE_ASCII_MASK.getValue()) != 0);
        }
        return rc;
    }

    /** セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合など */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        if (needCheckEofCode()) {
            // ファイル終端に終端文字があるか
            int curr_pos = (int) ((SeekableDataInputStream) iStream).position();
            ((SeekableDataInputStream) iStream).position(iStream.available());
            if (iStream.read() != basic.getTextTerminateCode()) {
                fileSize++;
            }
            ((SeekableDataInputStream) iStream).position(curr_pos);
        }
        return fileSize;
    }

    //
    // ダイアログ用
    //

    /** プロパティで表示する内部データを設定 */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        // Assume KeyValArray has Add methods for boolean, byte[], int
        DirectoryMsDos msdos = data.data().msdos;
        vals.add("NAME", msdos.name, msdos.name.length);
        vals.add("EXT", msdos.ext, msdos.ext.length);
        vals.add("TYPE", msdos.type & 0xff);
        vals.add("NTRES", msdos.ntRes & 0xff);
        vals.add("CTIME_TENTH", msdos.cTimeTenth & 0xff);
        vals.add("CTIME", msdos.cTime & 0xffff);
        vals.add("CDATE", msdos.cDate & 0xffff);
        vals.add("ADATE", msdos.aDate & 0xffff);
        vals.add("START_GROUP_HI", msdos.startGroupHi & 0xffff);
        vals.add("WTIME", msdos.wTime & 0xffff);
        vals.add("WDATE", msdos.wDate & 0xffff);
        vals.add("START_GROUP", msdos.startGroup & 0xffff);
        vals.add("FILE_SIZE", msdos.fileSize);
    }

    ///
    ///
    ///

    /** ディレクトリ１アイテム MS-DOS VFAT */
    public static class DiskBasicDirItemVFAT extends DiskBasicDirItemMSDOS {

        /**
         * ディレクトリエントリ MS-DOS LFN (32bytes)
         */
        @Serdes(bigEndian = false)
        public static class DirectoryMsLfn implements Directory {

            @Element(sequence = 1)
            public byte order;
            @Element(sequence = 2)
            public byte[] name = new byte[10];
            @Element(sequence = 3)
            public byte type;
            @Element(sequence = 4)
            public byte type2;
            @Element(sequence = 5)
            public byte checksum;
            @Element(sequence = 6)
            public byte[] name2 = new byte[12];
            @Element(sequence = 7)
            public short dummyGroup;
            @Element(sequence = 8)
            public byte[] name3 = new byte[4];

            public static final int SIZE = 32;
        }

        @Override
        public boolean isSupported(DiskBasicFormatType formatType) {
            return formatType == FORMAT_TYPE_MSDOS;
        }

        @Override
        public void init(DiskBasic basic) throws IOException {
            super.init(basic);
        }

        @Override
        public void init(DiskBasic basic, DiskImageSector sector, int sectorPos,
                         byte[] data, int dataPos) throws IOException {
            super.init(basic, sector, sectorPos, data, dataPos);
        }

        @Override
        public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem,
                         DiskImageSector sector, int sectorPos,
                         byte[] data, int dataPos,
                         SectorParam next, boolean[] unuse) throws IOException {
            super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);
        }

        /** ファイル名を格納する位置を返す */
        @Override
        protected byte[] getFileNamePos(int num, int[] size, int[] len) {
            // MS-DOS
            int t1 = getFileType1();
            DirectoryMs data = this.data.data();

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
            if (!data.isValid()) return false;

            boolean valid = true;
            // ファイルサイズが16MBを超えている
            if (checkUsed(false) && (data.data().msdos.type & FILETYPE_MASK_MS_LFN) != FILETYPE_MASK_MS_LFN && data.data().msdos.fileSize > 0xff_ffff) {
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
        public void setFileAttr(DiskBasicFileType fileType) {
            int fType = fileType.getType();
            if (fType == -1) return;

            // MS-DOS
            setFileType1((fType & 0xff00) >> 8);
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
            int fType = getFileAttr().getType();
            // MS-DOS
            if ((getFileAttr().getOrigin() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                if (!attr.isEmpty()) attr.append(", ");
                attr.append(Utils.keyAt(typeNameMS, TYPE_NAME_MS_LFN)); // long file name
            } else {
                getFileAttrStrSub(fType, attr);
            }
            if (attr.isEmpty()) {
                attr.append("---");
            }
            return attr.toString();
        }

        /** 最初のグループ番号をセット */
        @Override
        public void setStartGroup(int fileUnitNum, int val, int size) {
            // MS-DOS
            data.data().msdos.startGroup = (short) val;
        }

        /** 最初のグループ番号を返す */
        @Override
        public int getStartGroup(int fileUnitNum) {
            // MS-DOS
            return data.data().msdos.startGroup & 0xffff;
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

        /** アイテムの時間設定を無視できるか */
        @Override
        public int canIgnoreDateTime() {
            return DATETIME_CREATE_ACCESS;
        }

        /**
         * 作成日付を返す
         *
         * @return data
         */
        @Override
        public LocalDate getFileCreateDate(LocalDateTime tm) {
            short cDate = data.data().msdos.cDate;
            return convDateToTm(cDate);
        }

        /**
         * 作成時間を得る
         *
         * @return time
         */
        @Override
        public LocalTime getFileCreateTime(LocalDateTime tm) {
            short cTime = data.data().msdos.cTime;
            return convTimeToTm(cTime);
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
                data.data().msdos.cDate = cdate;
            }
        }

        /** 作成時間をセット */
        @Override
        public void setFileCreateTime(LocalDateTime tm) {
            if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
                short ctime = convTmToTime(tm);
                data.data().msdos.cTime = ctime;
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
            short aDate = data.data().msdos.aDate;
            return convDateToTm(aDate);
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
                data.data().msdos.aDate = adate;
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
                return basic.getCharCodes().convToChars(src, dst, len);
            }
        }

        /** バイト列を文字列に変換 文字コードは機種依存 */
        public void convCharsToString(byte[] src, int len, StringBuilder dst) {
            if ((getFileType1() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // ロングファイル名は常にUTF-16
                dst.append(new String(src, 0, len, StandardCharsets.UTF_16));
            } else {
                basic.getCharCodes().convToString(src, 0, len, dst, -1);
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
                DirectoryMsLfn mslfn = data.data().mslfn;
                vals.add("ORDER", mslfn.order & 0xff);
                vals.add("NAME", mslfn.name, mslfn.name.length);
                vals.add("TYPE", mslfn.type & 0xff);
                vals.add("TYPE2", mslfn.type2 & 0xff);
                vals.add("CHKSUM", mslfn.checksum & 0xff);
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
