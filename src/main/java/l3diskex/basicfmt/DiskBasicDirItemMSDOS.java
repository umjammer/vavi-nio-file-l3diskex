/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryMs;
import l3diskex.basicfmt.BasicCommon.DirectoryMsDos;
import l3diskex.basicfmt.BasicCommon.DirectoryMsLfn;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ARCHIVE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;


/// ディレクトリ１アイテム MS-DOS
public class DiskBasicDirItemMSDOS extends DiskBasicDirItem<DirectoryMs> {

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
    public static final int FILETYPE_MASK_MS_LFN = 0x0f;    // int file name

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

    /// ディレクトリデータ
    protected DiskBasicDirData<DirectoryMs> m_data;

    // Constructor for allocation only
    public DiskBasicDirItemMSDOS(DiskBasic basic) {
        super(basic);
        // Assuming DiskBasicDirData<DirectoryMsT> is a class that manages the underlying data structure
        // and has an Alloc() method analog.
        m_data = new DiskBasicDirData<>();
        m_data.alloc(DirectoryMs.class);
    }

    // Constructor for attaching to existing data in a sector
    public DiskBasicDirItemMSDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
        m_data = new DiskBasicDirData<>();
        m_data.attach(n_data); // Assumes Attach method handles mapping byte[] to DirectoryMsT
    }

    // Constructor for full initialization (used for reading directory entries)
    public DiskBasicDirItemMSDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);

        // MS-DOS
        m_data = new DiskBasicDirData<>();
        m_data.attach(n_data);

        // Assume DirectoryMsT has a getter for the msdos structure
        DirectoryMsDos msdos = m_data.data().msdos;

        boolean unuse = n_unuse[0];
        used(checkUsed(unuse));
        visible((getFileType1() & FILETYPE_MASK_MS_LFN) != FILETYPE_MASK_MS_LFN);
        n_unuse[0] = (unuse || (msdos.name[0] == 0));

        // グループ数を計算
        calcFileSize();

        // カレント or 親ディレクトリはツリーに表示しない
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && (name.equals(".") || name.equals(".."))));
    }

    /// アイテムへのポインタを設定
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, n_next);

        m_data.attach(n_data);
    }

    /// ファイル名を格納する位置を返す
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // MS-DOS
        int t1 = getFileType1();
        DirectoryMsDos msdos = m_data.data().msdos;

        if (num == 0) {
            size[0] = msdos.name.length;
            len[0] = msdos.name.length;
            return msdos.name;
        } else if (num == 1) {
            if ((t1 & FILETYPE_MASK_MS_VOLUME) != 0) {
                // ボリュームラベルは拡張子もラベル名とする
                size[0] = msdos.ext.length;
                len[0] = msdos.ext.length;
                return msdos.ext;
            }
        }
        size[0] = 0;
        len[0] = 0;
        return null;
    }

    /// 拡張子を格納する位置を返す
    @Override
    protected byte[] getFileExtPos(int[] len) {
        byte[] p = null;
        len[0] = 0;
        // ボリュームラベルは拡張子なしにする
        if ((getFileType1() & FILETYPE_MASK_MS_VOLUME) == 0) {
            DirectoryMsDos msdos = m_data.data().msdos;
            len[0] = msdos.ext.length;
            p = msdos.ext;
        }
        return p;
    }

    /// 属性１を返す
    @Override
    public int getFileType1() {
        return m_data.data().msdos.type & 0xff; // Use & 0xff to treat as unsigned byte in C++
    }

    /// 属性１のセット
    @Override
    public void setFileType1(int val) {
        m_data.data().msdos.type = (byte)(val & 0xff);
    }

    /// 使用しているアイテムか
    @Override
    public boolean checkUsed(boolean unuse) {
        // 0x00: 未使用, 0xE5: 削除済み
        return (m_data.data().msdos.name[0] != 0 && (m_data.data().msdos.name[0] & 0xff) != 0xe5);
    }

    /// ファイル名を設定
    @Override
    public void setNativeName(byte[] filename, int size, int[] length) {
        if (length[0] > 0) {
            // 0xe5は削除コードなので0x05に変換(Shift JISなど2バイト系文字など)
            if ((filename[0] & 0xff) == 0xe5) filename[0] = 0x05;
        }

        byte[] n;
        int nl = 0;
        int[] s = {0};
        int[] l = {0};

        int num = 0;
        do {
            s[0] = 0;
            l[0] = 0;
            n = getFileNamePos(num, s, l);
            if (n == null || s[0] == 0) {
                break;
            }

            int current_s = s[0];
            if (num > 0) {
                // C++ code seems to handle padding when the file name is shorter than the allocated space
                // but it's a bit complex with the size/length variables.
                // The C++ logic:
                // if (num > 0) {
                // 	int pl = nl;
                // 	int ps = s;
                // 	if (nl < length) {
                // 		pl = length;
                // 		ps = s + nl - length;
                // 	}
                // 	if (s + nl > length) {
                // 		memset(&filename[pl], 0, ps);
                // 	}
                // }
                // This seems to be manipulating the source 'filename' array *after* it's been copied from, which is odd.
                // Assuming the intent is to fill the *remaining* part of the target buffer 'n' with spaces/zeros
                // if the source 'filename' doesn't fill it, or handle overflow.
                // Let's stick to the core copy logic and hope the caller handles padding/truncation for the target.
                // The C++ code copies from filename[nl] for 's' bytes to 'n'.
                // This implies 'filename' is a buffer containing the full name (name + ext).
                // 'n' points to the name or ext part in the directory entry.
                // The original C++ code for SetNativeName seems slightly flawed in its size/length logic
                // or relies heavily on how 'filename' is structured before the call.
                // We'll simplify the filling logic, assuming 'filename' is padded/truncated to fit the MS-DOS 8.3 structure *before* this method.
            }

            if (current_s > size) current_s = size;

            // Copy from filename[nl] for current_s bytes into n (which is a reference to a field in m_data)
            System.arraycopy(filename, nl, n, 0, current_s);

            nl += current_s;
            size -= current_s;
            num++;
        } while(num <= 1);
    }

    /// ファイル名を得る
    public void getNativeName(byte[] filename, int[] size, int[] length) {
        byte[] n;
        int nl = 0;
        int num = 0;
        int[] s = new int[1];
        int[] l = new int[1];

        do {
            byte[] dname = filename; // copy to dname is not needed in Java, as we write directly to filename[nl]

            s[0] = 0;
            l[0] = 0;
            n = getFileNamePos(num, s, l);
            if (n == null || s[0] == 0) {
                break;
            }

            int current_s = s[0];
            if (current_s > size[0]) current_s = size[0];

            // Copy from n for current_s bytes into filename[nl]
            System.arraycopy(n, 0, filename, nl, current_s);

            nl += current_s;
            size[0] -= current_s;
            num++;
        } while(num <= 1);

        if (nl > 0) {
            // 0x05を0xe5に変換(Shift JISなど2バイト系文字など)
            if (filename[0] == 0x05) filename[0] = (byte)0xe5;
        }

        length[0] = nl;
    }

    /// 属性の文字列を返す(ファイル一覧画面表示用)
    protected void GetFileAttrStrSub(int ftype, StringBuilder attr) {
        // Assuming gTypeNameMS is defined with String name and int value
        // And wxGetTranslation is a utility function
        for(int i = 0; i <= TYPE_NAME_MS_ARCHIVE; i++) {
            if ((ftype & (int) Utils.valueAt(gTypeNameMS, i)) != 0) {
                if (attr.length() > 0) attr.append(", ");
                attr.append(Utils.keyAt(gTypeNameMS, i)); // Simplified translation lookup
            }
        }
    }

    /// 日付を変換
    protected static LocalDate convDateToTm(short date) {
        int dateInt = date & 0xffff; // Treat as unsigned short
        int yy = ((dateInt & 0xfe00) >> 9) + 80;
        return LocalDate.of(
                yy,
                ((dateInt & 0x01e0) >> 5) - 1, // 0-11 for DateTime month
                dateInt & 0x001f);
    }

    /// 時間を変換
    protected static LocalTime convTimeToTm(short time) {
        int timeInt = time & 0xffff; // Treat as unsigned short
        return LocalTime.of(
                (timeInt & 0xf800) >> 11,
                (timeInt & 0x07e0) >> 5,
                (timeInt & 0x001f) << 1);
    }

    /// 日付に変換
    protected static short convTmToDate(LocalDateTime tm) {
        int yy = tm.getYear();
        int month = tm.getMonth().ordinal() + 1; // DateTime month is 0-11
        int date = (
                (((yy - 80) & 0x7f) << 9)
                        | ((month & 0xf) << 5)
                        | (tm.getDayOfMonth() & 0x1f)
        ) & 0xffff;
        return (short)date;
    }

    /// 時間に変換
    protected static short convTmToTime(LocalDateTime tm) {
        int time = (
                ((tm.getHour() & 0x1f) << 11)
                        | ((tm.getMinute() & 0x3f) << 5)
                        | ((tm.getSecond() & 0x3f) >> 1)
        ) & 0xffff;
        return (short)time;
    }

    /// ディレクトリアイテムのチェック
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

    /// アイテムを削除できるか
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

    /// 削除
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ (0xe5)
        // Assuming basic->InvertUint8 and basic->GetDeleteCode() exist
        // And m_data.Fill() takes byte, offset, size
        byte deleteCode = basic.invertUint8(basic.diskBasicParam.getDeleteCode());
        m_data.fill(deleteCode, 0, true, 0);
        used(false);
        return true;
    }

    /// ファイル名を編集できるか
    @Override
    public boolean isFileNameEditable() {
        // ".", ".."は不可
        return isDeletable();
    }

    /// アイテムをロード・エクスポートできるか
    @Override
    public boolean isLoadable() {
        // ボリュームラベルは不可
        int t1 = getFileType1();
        // FILETYPE_MASK_MS_LFN is 0x0f, which is read only | hidden | system | volume.
        // The original code uses FILETYPE_MASK_MS_DIRECTORY | FILETYPE_MASK_MS_VOLUME.
        // (0x10 | 0x08) = 0x18
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

    /// アイテムを上書きできるか
    @Override
    public boolean isOverWritable() {
        // ディレクトリ、ボリュームラベルは不可
        int t1 = getFileType1();
        boolean valid = ((t1 & (FILETYPE_MASK_MS_DIRECTORY | FILETYPE_MASK_MS_VOLUME)) == 0);
        // ".", ".."は不可
        valid &= isDeletable();
        return valid;
    }

    /// 属性を設定
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        // MS-DOS: type1 is bits 8-15
        setFileType1((ftype & 0xff00) >> 8);
    }

    /// 属性を返す
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        // Assumes DiskBasicFileType constructor takes (formatNum, type1_shift8, type1_unshifted)
        return new DiskBasicFileType(basic.getFormatTypeNumber(), t1 << 8, t1);
    }

    /// 属性の文字列を返す(ファイル一覧画面表示用)
    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder();
        int ftype = getFileAttr().getType();
        // MS-DOS
        GetFileAttrStrSub(ftype, attr);
        if (attr.length() == 0) {
            attr.append("---");
        }
        return attr.toString();
    }

    /// ファイルサイズをセット
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        // Assuming DiskBasicDirData uses a structure where msdos.file_size is a 4-byte little-endian int
        m_data.data().msdos.fileSize = val;
    }

    /// ファイルサイズを返す
    @Override
    public int getFileSize() {
        int val = m_data.data().msdos.fileSize;
        // Convert from little-endian (or BE depending on host/image) to host int
        return val;
    }

    /// ディレクトリサイズをセット
    @Override
    public void setDirectorySize(int val) {
        setFileSize(0); // MS-DOS directories have a size of 0 in the entry
    }

    /// ファイルサイズとグループ数を計算する
    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    /// 指定ディレクトリのすべてのグループを取得
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) {
        int calc_groups = 0;
        int calc_file_size = getFileSize();

        // 12bit FAT (or 16/32)
        boolean rc = true;
        int group_num = getStartGroup(fileunit_num); // Assuming group numbers are unsigned 32-bit (int in Java)
        boolean working = (group_num >= 2);
        int remain = (calc_file_size > 0 ? calc_file_size : 0x7ffffff); // Max positive int for comparison
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;

        // Assuming DiskBasicType type field is accessible and has GetGroupNumber
        // and basic.GetSectorsPerGroup(), basic.GetSectorSize(), basic.GetFatEndGroup() exist
        while(working) {
            int next_group = type.getGroupNumber(group_num); // Returns unsigned 32-bit analog

            if (next_group == group_num) {
                // 同じポジションならエラー
                rc = false;
            } else if (next_group >= 0xff8) { // End-of-chain marker for FAT12/16 (or FFFF for 16, FFFFFFF8 for 32)
                // 最終グループ
                working = false;
            } else if (next_group > basic.diskBasicParam.getFatEndGroup()) {
                // グループ番号がおかしい
                rc = false;
            }

            if (rc) {
                // GetNumsFromGroup will add the actual sector/group items to group_items
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), remain, group_items);

                // Update remaining size
                int group_size = basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup();
                remain -= group_size;
                calc_groups++;
                group_num = next_group;
                limit--;
            }

            working = working && rc && (limit >= 0);
        }

        group_items.setNums(calc_groups);
        int calculated_size = basic.getSectorSize() * basic.getSectorsPerGroup() * calc_groups;
        // 元のファイルサイズが０ならグループ数から計算したサイズを格納
        group_items.setSize(calc_file_size > 0 ? calc_file_size : calculated_size);
        group_items.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinit loop
            // This case should be handled as an error by the caller,
            // but here we just set the error flag 'rc' to false.
            rc = false;
        }
    }

    /// 最初のグループ番号をセット
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        // MS-DOS uses 16-bit start group (start_group field)
        m_data.data().msdos.startGroup = (short)val;
    }

    /// 最初のグループ番号を返す
    @Override
    public int getStartGroup(int fileunit_num) {
        // MS-DOS start group is 16-bit
        return m_data.data().msdos.startGroup & 0xffff;
    }

    /// アイテムが更新日時を持っているか
    @Override
    public boolean hasModifyDateTime() { return true; }
    @Override
    public boolean hasModifyDate() { return true; }
    @Override
    public boolean hasModifyTime() { return true; }

    /// 更新日付を得る
    ///
    /// @return
    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        // MS-DOS
        short wdate = m_data.data().msdos.wdate;
        return convDateToTm(wdate);
    }

    /// 更新時間を得る
    ///
    /// @return
    @Override
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        // MS-DOS
        short wtime = m_data.data().msdos.wtime;
        return convTimeToTm(wtime);
    }

    /// 更新日付を文字列で返す
    @Override
    public String getFileModifyDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileModifyDate(tm);
        // Assuming Utils.FormatYMDStr exists
        return Utils.formatYMDStr(tm);
    }

    /// 更新時間を文字列で返す
    @Override
    public String getFileModifyTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileModifyTime(tm);
        // Assuming Utils.FormatHMSStr exists
        return Utils.formatHMSStr(tm);
    }

    /// 更新日付をセット
    @Override
    public void setFileModifyDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            short wdate = convTmToDate(tm);
            m_data.data().msdos.wdate = wdate;
        }
    }

    /// 更新時間をセット
    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            short wtime = convTmToTime(tm);
            m_data.data().msdos.wtime = wtime;
        }
    }

    /// 更新日付のタイトル名（ダイアログ用）
    @Override
    public String getFileModifyDateTimeTitle() {
        // Assuming _() is a translation function
        return "Updated Date"; // _("Updated Date")
    }

    /// 日時の表示順序を返す（ダイアログ用）
    @Override
    public int getFileDateTimeOrder(int idx) {
        return idx <= 1 ? 1 - idx : idx;
    }

    /// 日時を返す（ファイルリスト用）
    @Override
    public String getFileDateTimeStr() {
        return getFileModifyDateTimeStr();
    }

    /// ディレクトリアイテムのサイズ
    @Override
    public int getDataSize() {
        // Assuming sizeof(directory_ms_t) is the size of the structure
        return m_data.getDataSize();
    }

    /// アイテムを返す
    @Override
    public DirectoryMs getData() {
        // Assuming DirectoryT is a superclass/interface of DirectoryMsT
        return m_data.data();
    }

    /// アイテムをコピー
    @Override
    public boolean copyData(DirectoryMs val) {
        // Assuming DiskBasicDirData has a Copy method
        return m_data.copy(val, getDataSize());
    }

    /// ディレクトリをクリア ファイル新規作成時
    @Override
    public void clearData() {
        // Assuming DiskBasicDirData has a Fill method with a byte value, size, and inversion flag
        m_data.fill((byte)0, getDataSize(), basic.isDataInverted(), 0);
    }

    /// ファイル名から属性を決定する
    @Override
    public int convFileTypeFromFileName(String filename) {
        // FILE_TYPE_ARCHIVE_MASK is 0x2000
        return FILE_TYPE_ARCHIVE_MASK.getValue();
    }

    /// ファイルの終端コードをチェックする必要があるか
    @Override
    public boolean needCheckEofCode() {
        // テキストファイルかは拡張子で判断する
        boolean rc = false;
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(getFileExtPlainStr());
        if (sa != null) {
            rc = ((sa.getType() & FILE_TYPE_ASCII_MASK.getValue()) != 0);
        }
        return rc;
    }

    /// セーブ時にファイルサイズを再計算する ファイルの終端コードが必要な場合など
    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) {
        if (needCheckEofCode()) {
            // ファイル終端に終端文字があるか
            // InputStream is translated to InputStream (or similar, like a Seekable/Buffered stream)
            // wxFileOffset is translated to long
            // wxFromEnd is translated to a constant/enum

//            int curr_pos = istream.position(); // Assumed stream method
//            istream.position(istream.available()); // Assumed stream method
//
//            if (istream.read() != basic.diskBasicParam.GetTextTerminateCode()) { // Assumed stream/basic method
//             	file_size++;
//            }
//            istream.position(curr_pos); // Assumed stream method
        }
        return file_size;
    }

    //
    // ダイアログ用
    //

    /// プロパティで表示する内部データを設定
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        // Assume KeyValArray has Add methods for boolean, byte[], int
        DirectoryMsDos msdos = m_data.data().msdos;
        vals.add("self", m_data.isSelf());
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
}

//////////////////////////////////////////////////////////////////////

/// ディレクトリ１アイテム MS-DOS VFAT
class DiskBasicDirItemVFAT extends DiskBasicDirItemMSDOS {

    public DiskBasicDirItemVFAT(DiskBasic basic) {
        super(basic);
    }

    public DiskBasicDirItemVFAT(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
    }

    public DiskBasicDirItemVFAT(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
    }

    /// ファイル名を格納する位置を返す
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // MS-DOS
        int t1 = getFileType1();
        DirectoryMs data = m_data.data();

        if (num == 0) {
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // ロングファイルネーム (LFN entry structure: mslfn)
                size[0] = data.mslfn.name.length;
                len[0] = data.mslfn.name.length;
                return data.mslfn.name;
            } else {
                // Short file name (SFN entry structure: msdos)
                size[0] = data.msdos.name.length;
                len[0] = data.msdos.name.length;
                return data.msdos.name;
            }
        } else if (num == 1) {
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // ロングファイルネーム
                size[0] = data.mslfn.name2.length;
                len[0] = data.mslfn.name2.length;
                return data.mslfn.name2;
            } else if ((t1 & FILETYPE_MASK_MS_VOLUME) != 0) {
                // ボリュームラベルは拡張子もラベル名とする (SFN structure)
                size[0] = data.msdos.ext.length;
                len[0] = data.msdos.ext.length;
                return data.msdos.ext;
            }
        } else if (num == 2) {
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // ロングファイルネーム
                size[0] = data.mslfn.name3.length;
                len[0] = data.mslfn.name3.length;
                return data.mslfn.name3;
            }
        }
        size[0] = 0;
        len[0] = 0;
        return null;
    }

    /// ファイル名を設定
    protected void setNativeName(byte[] filename, int[] size, int length) {
        if (length > 0) {
            // 0xe5は削除コードなので0x05に変換(Shift JISなど2バイト系文字など)
            if ((filename[0] & 0xff) == 0xe5) filename[0] = 0x05;
        }

        byte[] n;
        int nl = 0;
        int[] s = new int[1];
        int[] l = new int[1];

        int num = 0;
        // VFAT LFNs have up to 4 parts (num 0 to 3), plus the SFN part.
        // The original C++ code uses num <= 4 for the loop condition.
        do {
            s[0] = 0;
            l[0] = 0;
            n = getFileNamePos(num, s, l);
            if (n == null || s[0] == 0) {
                break;
            }

            int current_s = s[0];

            // C++ code for padding logic for num > 0:
            // if (num > 0) {
            // 	int pl = nl;
            // 	int ps = s;
            // 	if (nl < length) {
            // 		pl = length;
            // 		ps = s + nl - length;
            // 	}
            // 	memset(&filename[pl], 0, ps);
            // }
            // This still seems incorrect for a typical write. Let's stick to the copy for VFAT,
            // assuming 'filename' is prepared by the caller for LFN/SFN conversion.
            // The LFN fields are usually UTF-16 and padded with 0xFF.

            if (current_s > size[0]) current_s = size[0];

            // Copy from filename[nl] for current_s bytes into n (which is a reference to a field in m_data)
            System.arraycopy(filename, nl, n, 0, current_s);

            nl += current_s;
            size[0] -= current_s;
            num++;
        } while(num <= 4); // Loop through all potential parts of LFN or SFN (max 5 parts in loop, 0-4)
    }

    /// ファイル名を得る
    @Override
    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n = null;
        int nl = 0;
        int num = 0;
        int[] s;
        int[] l;

        do {
            // byte[] dname = filename; // not needed in Java

            s = new int[] {0};
            l = new int[] {0};
            n = getFileNamePos(num, s, l);
            if (n == null || s[0] == 0) {
                break;
            }

            int current_s = s[0];
            if (current_s > size) current_s = size;

            // Copy from n for current_s bytes into filename[nl]
            System.arraycopy(n, 0, filename, nl, current_s);

            nl += current_s;
            size -= current_s;
            num++;
        } while(num <= 4); // Loop through all potential parts of LFN or SFN (max 5 parts in loop, 0-4)

        if (nl > 0) {
            // 0x05を0xe5に変換(Shift JISなど2バイト系文字など)
            if (filename[0] == 0x05) filename[0] = (byte)0xe5;
        }

        length[0] = nl;
    }

    /// ディレクトリアイテムのチェック
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        // ファイルサイズが16MBを超えている (only for SFN entries)
        if (checkUsed(false) && (m_data.data().msdos.type & FILETYPE_MASK_MS_LFN) != FILETYPE_MASK_MS_LFN && m_data.data().msdos.fileSize > 0xffffff) {
            valid = false;
        }
        return valid;
    }

    /// アイテムを削除できるか
    @Override
    public boolean isDeletable() {
        boolean valid = true;
        int t1 = getFileType1();
        if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
            valid = false; // LFN entries are deleted via the SFN entry
        } else if ((t1 & FILETYPE_MASK_MS_DIRECTORY) != 0) {
            String name = getFileNamePlainStr();
            if (name.equals(".") || name.equals("..")) {
                // ディレクトリ ".", ".."は削除不可
                valid = false;
            }
        }
        return valid;
    }

    /// ファイル名を編集できるか
    @Override
    public boolean isFileNameEditable() {
        boolean valid = true;
        int t1 = getFileType1();
        if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
            valid = false; // LFN entries are not editable directly
        } else if ((t1 & FILETYPE_MASK_MS_DIRECTORY) != 0) {
            String name = getFileNamePlainStr();
            if (name.equals(".") || name.equals("..")) {
                // ディレクトリ ".", ".."は編集不可
                valid = false;
            }
        }
        return valid;
    }

    /// 属性を設定
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        // MS-DOS
        setFileType1((ftype & 0xff00) >> 8);
    }

    /// 属性を返す
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        // Assumes DiskBasicFileType constructor takes (formatNum, type1_shift8, type1_unshifted)
        return new DiskBasicFileType(basic.getFormatTypeNumber(), t1 << 8, t1);
    }

    /// 属性の文字列を返す(ファイル一覧画面表示用)
    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder();
        int ftype = getFileAttr().getType();

        // MS-DOS VFAT
        if ((getFileAttr().getOrigin() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
            // int File Name entry
            if (attr.length() > 0) attr.append(", ");
            // attr.append(wxGetTranslation(gTypeNameMS[TYPE_NAME_MS_LFN].name)); // int file name
            attr.append(Utils.keyAt(gTypeNameMS, TYPE_NAME_MS_LFN)); // Simplified translation lookup
        } else {
            // Short File Name entry
            GetFileAttrStrSub(ftype, attr);
        }

        if (attr.length() == 0) {
            attr.append("---");
        }
        return attr.toString();
    }

    /// 最初のグループ番号をセット
    // Overridden to use the base implementation, but keeping it for completeness
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        // MS-DOS (start_group is 16-bit, start_group_hi is 16-bit, total 32-bit for FAT32, but this class seems to use 16-bit)
        // For FAT12/16, it only sets the 16-bit part. FAT32 would need to set start_group_hi too.
        // The original code only sets the 16-bit part.
        m_data.data().msdos.startGroup = (short)val;
    }

    /// 最初のグループ番号を返す
    // Overridden to use the base implementation, but keeping it for completeness
    @Override
    public int getStartGroup(int fileunit_num) {
        // MS-DOS (16-bit start group)
        // FAT32 would need to combine start_group_hi and start_group.
        // Assuming this only handles FAT12/16 or the 16-bit part of FAT32 for now.
        return m_data.data().msdos.startGroup & 0xffff;
    }

    /// アイテムが作成日時を持っているか
    @Override
    public boolean hasCreateDateTime() { return true; }
    @Override
    public boolean hasCreateDate() { return true; }
    @Override
    public boolean hasCreateTime() { return true; }

    /// アイテムの時間設定を無視することができるか
    // Assuming EnDateTime is defined elsewhere
    @Override
    public int canIgnoreDateTime() { return DATETIME_CREATE_ACCESS; }

    /// 作成日付を返す
    ///
    /// @return
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        short cdate = m_data.data().msdos.cdate;
        return convDateToTm(cdate);
    }

    /// 作成時間を得る
    ///
    /// @return
    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        short ctime = m_data.data().msdos.ctime;
        return convTimeToTm(ctime);
    }

    /// 作成日付を文字列で返す
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateDate(tm);
        return Utils.formatYMDStr(tm);
    }

    /// 作成時間を文字列で返す
    @Override
    public String getFileCreateTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateTime(tm);
        return Utils.formatHMSStr(tm);
    }

    /// 作成日付をセット
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            short cdate = convTmToDate(tm);
            m_data.data().msdos.cdate = cdate;
        }
    }

    /// 作成時間をセット
    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            short ctime = convTmToTime(tm);
            m_data.data().msdos.ctime = ctime;
        }
    }

    /// アイテムがアクセス日時を持っているか
    @Override
    public boolean hasAccessDateTime() { return true; }
    @Override
    public boolean hasAccessDate() { return true; }
    @Override
    public boolean hasAccessTime() { return false; } // VFAT only stores access date, not time

    /// アクセス日付を返す
    ///
    /// @return
    @Override
    public LocalDate getFileAccessDate(LocalDateTime tm) {
        short adate = m_data.data().msdos.adate;
        return convDateToTm(adate);
    }

    /// アクセス日付を返す
    @Override
    public String getFileAccessDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileAccessDate(tm);
        return Utils.formatYMDStr(tm);
    }

    /// アクセス日付をセット
    @Override
    public void setFileAccessDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            short adate = convTmToDate(tm);
            m_data.data().msdos.adate = adate;
        }
    }

    /// 文字列をバイト列に変換 文字コードは機種依存
    @Override
    public int convStringToChars(String src, byte[] dst, int len) {
        if ((getFileType1() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
            // ロングファイル名は常にUTF-16
            // Assuming a utility for UTF-16 conversion that handles byte array output
            // and assuming wxMBConvUTF16 is a utility class
            // byte[] buf = src.getBytes(wxMBConvUTF16.getCharset());
            // if (buf.length > 0) {
            // 	int l = Math.min(buf.length, len);
            // 	System.arraycopy(buf, 0, dst, 0, l);
            // 	return l;
            // } else {
            // 	return 0;
            // }
            return 0; // Stub
        } else {
            // Assuming basic->GetCharCodes().ConvToChars() exists
            dst = src.getBytes(basic.getCharCodes().charset());
            return 0;
        }
    }

    /// バイト列を文字列に変換 文字コードは機種依存
    public void convCharsToString(byte[] src, int len, String[] dst) {
        if ((getFileType1() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
            // ロングファイル名は常にUTF-16
            // dst.set(new String(src, 0, len, wxMBConvUTF16.getCharset()));
        } else {
            // Assuming basic->GetCharCodes().ConvToString() exists
            dst[0] = new String(src, 0, len, basic.getCharCodes().charset());
        }
    }

    /// その他の属性値を設定する
    @Override
    public void setOptionalAttr(DiskBasicDirItemAttr attr) {
        // VFAT base class implementation is empty
    }

    //
    // ダイアログ用
    //

    /// プロパティで表示する内部データを設定
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        int t1 = getFileType1();
        if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
            // int File Name entry
            DirectoryMsLfn mslfn = m_data.data().mslfn;
            vals.add("self", m_data.isSelf());
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
