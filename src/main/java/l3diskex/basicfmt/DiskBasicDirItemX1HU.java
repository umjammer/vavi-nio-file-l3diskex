package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryX1Hu;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Common.mem_invert;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ENCRYPTED_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;


public class DiskBasicDirItemX1HU extends DiskBasicDirItem<DirectoryX1Hu> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    // en_type_name_x1hu_1
    public static final int TYPE_NAME_X1HU_BINARY = 0;
    public static final int TYPE_NAME_X1HU_BASIC = 1;
    public static final int TYPE_NAME_X1HU_ASCII = 2;
    public static final int TYPE_NAME_X1HU_SWORD = 3;
    public static final int TYPE_NAME_X1HU_RANDOM = 4;
    public static final int TYPE_NAME_X1HU_DIRECTORY = 5;
    public static final int TYPE_NAME_X1HU_END = 6;

    // en_file_type_mask_x1hu
    public static final int FILETYPE_X1HU_BINARY = 0x01;
    public static final int FILETYPE_X1HU_BASIC = 0x02;
    public static final int FILETYPE_X1HU_ASCII = 0x04;
    public static final int FILETYPE_X1HU_DIRECTORY = 0x80;
    public static final int FILETYPE_X1HU_MASK = (FILETYPE_X1HU_BINARY | FILETYPE_X1HU_BASIC | FILETYPE_X1HU_ASCII | FILETYPE_X1HU_DIRECTORY);

    // en_external_type_x1
    public static final int EXTERNAL_X1_DEFAULT = 0;
    public static final int EXTERNAL_X1_RANDOM = 1;
    public static final int EXTERNAL_X1_SWORD = 2;

    // en_type_name_x1hu_2
    public static final int TYPE_NAME_X1HU_HIDDEN = 0;
    public static final int TYPE_NAME_X1HU_READ_WRITE = 1;
    public static final int TYPE_NAME_X1HU_READ_ONLY = 2;
    public static final int TYPE_NAME_X1HU_PASSWORD = 3;

    // en_data_type_mask_x1hu
    public static final int DATATYPE_X1HU_HIDDEN = 0x10;
    public static final int DATATYPE_X1HU_READ_WRITE = 0x20;
    public static final int DATATYPE_X1HU_READ_ONLY = 0x40;
    public static final int DATATYPE_X1HU_RESERVED = 0x08;
    public static final int DATATYPE_X1HU_MASK = (DATATYPE_X1HU_RESERVED | DATATYPE_X1HU_HIDDEN | DATATYPE_X1HU_READ_WRITE | DATATYPE_X1HU_READ_ONLY);

    public static final int DATATYPE_X1HU_PASSWORD_NONE = 0x20;
    public static final int DATATYPE_X1HU_PASSWORD_MASK = 0xff;

    // enDateTime (Assumed enum)
    public static final int DATETIME_ALL = 0;

    // NameValueT arrays (from .cpp)
    public static final Map<String, Object> gTypeNameX1HU_1 = new HashMap<>() {{
        put("Bin", FILETYPE_X1HU_BINARY);
        put("Bas", FILETYPE_X1HU_BASIC);
        put("Asc(Hu)", FILETYPE_X1HU_ASCII);
        put("Asc(S-OS)", FILETYPE_X1HU_ASCII);
        put("Asc(Random Access)", FILETYPE_X1HU_ASCII);
        put("<DIR>", FILETYPE_X1HU_DIRECTORY);
    }};
    public static final Map<String, Object> gTypeNameX1HU_2 = new HashMap<>() {{
        put("Hidden", DATATYPE_X1HU_HIDDEN);
        put("Read After Write", DATATYPE_X1HU_READ_WRITE);
        put("Write Protected", DATATYPE_X1HU_READ_ONLY);
        put("Password", DATATYPE_X1HU_RESERVED);
    }};

    private final DiskBasicDirData<DirectoryX1Hu> m_data = new DiskBasicDirData<>();
    private int m_external_attr; // Used for external type (EXTERNAL_X1_*)

    public DiskBasicDirItemX1HU(DiskBasic basic) {
        super(basic);
        m_data.alloc(DirectoryX1Hu.class);
        m_external_attr = basic.diskBasicParam.getVariousIntegerParam("DefaultAsciiType");
    }

    public DiskBasicDirItemX1HU(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
        m_data.attach(n_data);
        m_external_attr = basic.diskBasicParam.getVariousIntegerParam("DefaultAsciiType");
    }

    public DiskBasicDirItemX1HU(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
        m_data.attach(n_data);
        m_external_attr = basic.diskBasicParam.getVariousIntegerParam("DefaultAsciiType");

        used(checkUsed(n_unuse[0]));

        // グループ数を計算
        calcFileSize();
    }

    // Item pointer setting
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, n_next);
        m_data.attach(n_data);
    }

    // File name position
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

    // File extension position
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

    // Attribute 1 (Type)
    @Override
    public int getFileType1() {
        return basic.invertUint8(m_data.data().type);
    }

    // Attribute 2 (Password/Flags)
    @Override
    public int getFileType2() {
        return basic.invertUint8(m_data.data().password);
    }

    // Set Attribute 1
    @Override
    protected void setFileType1(int val) {
        m_data.data().type = basic.invertUint8((byte) val);
    }

    // Set Attribute 2
    @Override
    protected void setFileType2(int val) {
        m_data.data().password = basic.invertUint8((byte) val);
    }

    // Check if item is used
    @Override
    public boolean checkUsed(boolean unuse) {
        int type1 = getFileType1();
        return (type1 != 0 && type1 != 0xff);
    }

    // Directory item check
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        int type1 = getFileType1();
        boolean valid = true;
        // 属性が不正 (Attribute invalid)
        if (type1 != 0xff && (type1 & 0x08) != 0) {
            valid = false;
        }
        return valid;
    }

    // Delete item
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ (Deletion only puts a code at the beginning of the entry)
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    // Set file attributes
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int passwd = DATATYPE_X1HU_PASSWORD_NONE;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            t1 = file_type.getOrigin();
            passwd = ((t1 >> 8) & 0xff);
        } else {
            t1 = convToNativeType(ftype, getFileType1());

            t1 &= ~DATATYPE_X1HU_MASK;
            t1 |= (ftype & FILE_TYPE_HIDDEN_MASK.getValue()) != 0 ? DATATYPE_X1HU_HIDDEN : 0;
            t1 |= (ftype & FILE_TYPE_READWRITE_MASK.getValue()) != 0 ? DATATYPE_X1HU_READ_WRITE : 0;
            t1 |= (ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0 ? DATATYPE_X1HU_READ_ONLY : 0;

            // password
            if ((ftype & FILE_TYPE_ENCRYPTED_MASK.getValue()) != 0) {
                passwd = file_type.getOrigin();
            }
        }
        m_external_attr = (t1 >> 16);
        t1 &= 0xff;

        setFileType1(t1);
        setFileType2(passwd);
    }

    // Convert to native type
    private int convToNativeType(int file_type, int val) {
        // X1 Hu
        val &= ~FILETYPE_X1HU_MASK;
        if ((file_type & (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) == (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
            // bin
            val |= FILETYPE_X1HU_BINARY;
        } else if ((file_type & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
            // bas
            val |= FILETYPE_X1HU_BASIC;
        } else if ((file_type & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            // asc
            val |= FILETYPE_X1HU_ASCII;
        } else if ((file_type & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            // random
            val |= (FILETYPE_X1HU_ASCII | (EXTERNAL_X1_RANDOM << 16));
        } else if ((file_type & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
            // sub directory
            val |= FILETYPE_X1HU_DIRECTORY;
        }
        return val;
    }

    // Get file attributes
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        if ((t1 & FILETYPE_X1HU_BINARY) != 0) {
            val = FILE_TYPE_MACHINE_MASK.getValue();    // bin
            val |= FILE_TYPE_BINARY_MASK.getValue();
        } else if ((t1 & FILETYPE_X1HU_BASIC) != 0) {
            val = FILE_TYPE_BASIC_MASK.getValue();        // bas
            val |= FILE_TYPE_BINARY_MASK.getValue();
        } else if ((t1 & FILETYPE_X1HU_ASCII) != 0) {
            val = FILE_TYPE_ASCII_MASK.getValue();        // asc
        } else if ((t1 & FILETYPE_X1HU_DIRECTORY) != 0) {
            val = FILE_TYPE_DIRECTORY_MASK.getValue();    // sub directory
        }

        int passwd = getFileType2();
        if (passwd != DATATYPE_X1HU_PASSWORD_NONE) {
            val |= FILE_TYPE_ENCRYPTED_MASK.getValue();
        }
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (m_external_attr << 16) | (passwd << 8) | t1);
    }

    // Get attribute string for display
    @Override
    public String getFileAttrStr() {
        int t = (getFileType1() | (m_external_attr << 16));
        String attr = rb.getString(Utils.keyAt(gTypeNameX1HU_1, getFileType1Pos(t)));

        for (int i = 0; i <= TYPE_NAME_X1HU_READ_ONLY; i++) {
            if ((t & (int) Utils.valueAt(gTypeNameX1HU_2, i)) != 0) {
                attr = attr + ", " + rb.getString(Utils.keyAt(gTypeNameX1HU_2, i));
            }
        }
        if (getFileType2() != DATATYPE_X1HU_PASSWORD_NONE) {
            attr = attr + ", " + rb.getString(Utils.keyAt(gTypeNameX1HU_2, TYPE_NAME_X1HU_PASSWORD));    // password
        }
        return attr;
    }

    // Set file size
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);

        if ((getFileType1() & FILETYPE_X1HU_ASCII) != 0) {
            // Ascファイルの場合 (Asc file case)
            // ディレクトリ内のファイルサイズは0 (File size in directory is 0)
            m_data.data().fileSize = 0;
        } else {
            m_data.data().fileSize = basic.invertAndOrderUint16((short) val);
        }
    }

    // Get file size
    @Override
    public int getFileSize() {
        if ((getFileType1() & FILETYPE_X1HU_ASCII) != 0) {
            // Ascファイルの場合 (Asc file case)
            return groups.getSize();
        } else {
            return basic.invertAndOrderUint16(m_data.data().fileSize);
        }
    }

    // Calculate file unit size and group count
    @Override
    public void calcFileUnitSize(int fileunit_num) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileunit_num, groups);
    }

    // Get all groups for a directory
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) throws IOException {
        boolean rc = true;
        int calc_file_size = 0;
        int calc_groups = 0;

        // 8bit FAT
        int group_num = getStartGroup(fileunit_num);
        boolean working = true;
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        while (working) {
            int next_group = type.getGroupNumber(group_num);
            if (next_group == group_num) {
                // 同じポジションならエラー (Error if same position)
                rc = false;
            } else if (next_group >= basic.diskBasicParam.getGroupFinalCode() && next_group <= basic.diskBasicParam.getGroupSystemCode()) {
                // 最終グループ(0x80 - 0xff) (Final group)
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), 0, group_items);
                calc_file_size += (basic.getSectorSize() * (next_group - basic.diskBasicParam.getGroupFinalCode() + 1));
                calc_groups++;
                calc_file_size = recalcFileSize(group_items, calc_file_size);
                working = false;
            } else if (next_group <= basic.getFatEndGroup()) {
                // 次グループ (Next group)
                basic.getNumsFromGroup(group_num, next_group, basic.getSectorSize(), 0, group_items);
                calc_file_size += (basic.getSectorSize() * basic.getSectorsPerGroup());
                calc_groups++;
                group_num = next_group;
                limit--;
            } else {
                // グループ番号がおかしい (Group number is strange)
                rc = false;
            }
            working = working && rc && (limit >= 0);
        }

        group_items.setNums(calc_groups);
        group_items.setSize(calc_file_size);
        group_items.setSizePerGroup(basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinit loop
            rc = false;
        }
    }

    // Recalculate file size based on last sector
    @Override
    public int recalcFileSize(DiskBasicGroups group_items, int occupied_size) throws IOException {
        if (group_items.count() == 0) return occupied_size;

        DiskBasicGroupItem litem = group_items.last();
        DiskImageSector sector = basic.getSector(litem.track, litem.side, litem.sectorEnd);
        if (sector == null) return occupied_size;

        int sector_size = sector.getSectorSize();
        int remain_size = ((occupied_size + sector_size - 1) % sector_size) + 1;
        remain_size = type.calcDataSizeOnLastSector(this, null, null, sector.getSectorBuffer(), 0, sector_size, remain_size);

        occupied_size = occupied_size - sector_size + remain_size;
        return occupied_size;
    }

    // Get file creation date
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        byte[] date = new byte[m_data.data().date.length + 1];
        basic.invertMem(m_data.data().date, m_data.data().date.length, date);
        return LocalDate.of(
                ((date[0] & 0xff) <= 0x99 ? ((date[0] & 0xf0) >> 4) * 10 + (date[0] & 0x0f) : -1) +    // BCD
                        (tm.getYear() >= 0 && tm.getYear() < 80 ? 100 : 0),    // 2000 - 2079
                ((date[1] & 0xf0) >> 4) - 1,
                (date[2] & 0xff) <= 0x99 ? ((date[2] & 0xf0) >> 4) * 10 + (date[2] & 0x0f) : -1);    // BCD
    }

    // Get file creation time
    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        byte[] time = new byte[m_data.data().time.length + 1];
        basic.invertMem(m_data.data().time, m_data.data().time.length, time);
        return LocalTime.of(
                (time[0] & 0xff) <= 0x99 ? ((time[0] & 0xf0) >> 4) * 10 + (time[0] & 0x0f) : -1,    // BCD
                (time[1] & 0xff) <= 0x99 ? ((time[1] & 0xf0) >> 4) * 10 + (time[1] & 0x0f) : -1,    // BCD
                0);
    }

    // Get file creation date string
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateDate(tm);
        return Utils.formatYMDStr(tm);
    }

    // Get file creation time string
    @Override
    public String getFileCreateTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateTime(tm);
        return Utils.formatHMStr(tm);
    }

    // Set file creation date
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() < 0 || tm.getMonth().ordinal() < -1 || tm.getDayOfMonth() < 0) return;

        m_data.data().date[0] = (byte) (((tm.getYear() / 10) % 10) << 4 | (tm.getYear() % 10));    // year BCD
        m_data.data().date[1] = (byte) (((tm.getMonth().ordinal() + 1) & 0xf) << 4);    // month
        m_data.data().date[2] = (byte) (((tm.getDayOfMonth() / 10) << 4) | (tm.getDayOfMonth() % 10));    // day BCD

        // 日付から曜日を計算 (Calculate day of week from date)
        int wk = 0;
        LocalDateTime dt; // Placeholder for date object
        String sdate = String.format("%04d-%02d-%02d"
                , tm.getYear() + 1900
                , tm.getMonth().ordinal() + 1
                , tm.getDayOfMonth()
        );
        try {
            dt = LocalDateTime.parse(sdate); // TODO check parsable
            wk = dt.getDayOfWeek().getValue();
        } catch (DateTimeParseException ignore) {
        }
        m_data.data().date[1] |= (wk & 0xf);    // day of week

        if (basic.isDataInverted()) mem_invert(m_data.data().date, m_data.data().date.length);
    }

    // Set file creation time
    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() < 0 || tm.getMinute() < 0) return;

        m_data.data().time[0] = (byte) (((tm.getHour() / 10) << 4) | (tm.getHour() % 10));    // hour BCD
        m_data.data().time[1] = (byte) (((tm.getMinute() / 10) << 4) | (tm.getMinute() % 10));    // minute BCD

        if (basic.isDataInverted()) mem_invert(m_data.data().time, m_data.data().time.length);
    }

    // Has date/time
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

    // Can ignore date/time
    @Override
    public int canIgnoreDateTime() {
        return DATETIME_ALL;
    } // enum enDateTime

    // Has address
    @Override
    public boolean hasAddress() {
        return true;
    }

    // Get start address
    @Override
    public int getStartAddress() {
        return basic.invertAndOrderUint16(m_data.data().loadAddr);
    }

    // Get execute address
    @Override
    public int getExecuteAddress() {
        return basic.invertAndOrderUint16(m_data.data().execAddr);
    }

    // Set start address
    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = basic.invertAndOrderUint16((short) val);
    }

    // Set execute address
    @Override
    public void setExecuteAddress(int val) {
        m_data.data().execAddr = basic.invertAndOrderUint16((short) val);
    }

    // Directory item size
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    // Get data structure
    @Override
    public DirectoryX1Hu getData() {
        return m_data.data();
    }

    // Copy data
    @Override
    public boolean copyData(DirectoryX1Hu val) {
        return m_data.copy(val, getDataSize());
    }

    // Clear data
    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getDeleteCode(), getDataSize(), basic.isDataInverted(), 0);
    }

    // Initial data (set unused)
    @Override
    public void initialData() {
        if (!m_data.isValid()) return;
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir(), getDataSize(), basic.isDataInverted(), 0);
    }

    // Set start group number
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        // X1 Hu-BASIC
        m_data.data().startGroupH = basic.invertUint8((byte) ((val & 0xff0000) >> 16));
        m_data.data().startGroupL = basic.invertAndOrderUint16((short) (val & 0xffff));
    }

    // Get start group number
    @Override
    public int getStartGroup(int fileunit_num) {
        // X1 Hu-BASIC
        return basic.invertUint8(m_data.data().startGroupH) << 16 | basic.invertAndOrderUint16(m_data.data().startGroupL);
    }

    // Need check EOF code
    @Override
    public boolean needCheckEofCode() {
        // EOF code is needed for Asc format
        return ((getFileType1() & FILETYPE_X1HU_ASCII) != 0 && (m_external_attr != EXTERNAL_X1_RANDOM));
    }

    // Get EOF code
    @Override
    public byte getEofCode() {
        return m_external_attr != EXTERNAL_X1_SWORD ? basic.diskBasicParam.getTextTerminateCode() : 0;
    }

    // Recalculate file size on save
    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) {
        if (needCheckEofCode()) {
            // Check if file ends with termination code
            file_size = checkEofCode(istream, file_size);
        }
        return file_size;
    }

    // Determine original type from file name
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        // Set attribute by extension
        MyAttribute sa = findUpperCase(basic.diskBasicParam.getAttributesByExtension(), Utils.getExt(filename));
        if (sa != null) {
            t1 = convToNativeType(sa.getType(), t1);
            t1 |= (m_external_attr << 16);
        } else {
            t1 = FILETYPE_X1HU_ASCII;
        }

        // No password
        t1 |= (DATATYPE_X1HU_PASSWORD_NONE << 8);
        return t1;
    }

    //
    // Dialog methods
    //

    // Get file type 1 position in list
    public int getFileType1Pos(int native_type) {
        int val = 0;
        if ((native_type & FILETYPE_X1HU_BINARY) != 0) {
            val = TYPE_NAME_X1HU_BINARY;         // bin
        } else if ((native_type & FILETYPE_X1HU_BASIC) != 0) {
            val = TYPE_NAME_X1HU_BASIC;          // bas
        } else if ((native_type & FILETYPE_X1HU_ASCII) != 0) {
            switch (native_type >> 16) {
                case EXTERNAL_X1_RANDOM:
                    val = TYPE_NAME_X1HU_RANDOM; // asc
                    break;
                case EXTERNAL_X1_SWORD:
                    val = TYPE_NAME_X1HU_SWORD;  // asc
                    break;
                default:
                    val = TYPE_NAME_X1HU_ASCII;  // asc
                    break;
            }
        } else if ((native_type & FILETYPE_X1HU_DIRECTORY) != 0) {
            val = TYPE_NAME_X1HU_DIRECTORY;     // sub directory
        }
        return val;
    }

    // Set internal data for property dialog
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("self", m_data.isSelf());
        vals.add("inverted", basic.isDataInverted());

        vals.add("TYPE", m_data.data().type, basic.isDataInverted());
        vals.add("NAME", m_data.data().name, m_data.data().name.length, basic.isDataInverted());
        vals.add("EXT", m_data.data().ext, m_data.data().ext.length, basic.isDataInverted());
        vals.add("PASSWORD", m_data.data().password, basic.isDataInverted());
        vals.add("FILE_SIZE", m_data.data().fileSize, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", m_data.data().loadAddr, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", m_data.data().execAddr, basic.isBigEndian(), basic.isDataInverted());
        vals.add("DATE", m_data.data().date, m_data.data().date.length, basic.isDataInverted());
        vals.add("TIME", m_data.data().time, m_data.data().time.length, basic.isDataInverted());
        vals.add("START_GROUP_H", m_data.data().startGroupH, basic.isDataInverted());
        vals.add("START_GROUP_L", m_data.data().startGroupL, basic.isBigEndian(), basic.isDataInverted());
    }
}
