package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryMagical;
import l3diskex.basicfmt.BasicCommon.DirectoryN88;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;


public class DiskBasicDirItemMAGICAL extends DiskBasicDirItemXDOSBase<DirectoryMagical> {

    static final ResourceBundle rb = ResourceBundle.getBundle("message");

    // Enums and Constants from basicdiritem_magical.h
    public enum en_type_name_magical_1 {
        TYPE_NAME_MAGICAL_SYS,
        TYPE_NAME_MAGICAL_BAS,
        TYPE_NAME_MAGICAL_OBJ,
        TYPE_NAME_MAGICAL_ASC,
        TYPE_NAME_MAGICAL_DIR,
        TYPE_NAME_MAGICAL_CDT,
        TYPE_NAME_MAGICAL_PDT,
        TYPE_NAME_MAGICAL_GRA,
        TYPE_NAME_MAGICAL_GAK,
        TYPE_NAME_MAGICAL_SBA,
        TYPE_NAME_MAGICAL_SOB,
        TYPE_NAME_MAGICAL_REP,
        TYPE_NAME_MAGICAL_MDT,
        TYPE_NAME_MAGICAL_ARC,
        TYPE_NAME_MAGICAL_KTY,
        TYPE_NAME_MAGICAL_CGP,
        TYPE_NAME_MAGICAL_BGM,
        TYPE_NAME_MAGICAL_UNKNOWN,
    }

    public enum en_file_type_magical {
        FILETYPE_MAGICAL_SYS(0x01),
        FILETYPE_MAGICAL_BAS(0x22),
        FILETYPE_MAGICAL_OBJ(0x03),
        FILETYPE_MAGICAL_ASC(0x44),
        FILETYPE_MAGICAL_DIR(0x05),
        FILETYPE_MAGICAL_CDT(0x06),
        FILETYPE_MAGICAL_PDT(0x07),
        FILETYPE_MAGICAL_GRA(0x08),
        FILETYPE_MAGICAL_GAK(0x49),
        FILETYPE_MAGICAL_SBA(0x2a),
        FILETYPE_MAGICAL_SOB(0x0b),
        FILETYPE_MAGICAL_REP(0x4c),
        FILETYPE_MAGICAL_MDT(0x0d),
        FILETYPE_MAGICAL_ARC(0x4e),
        FILETYPE_MAGICAL_KTY(0x4f),
        FILETYPE_MAGICAL_CGP(0x50),
        FILETYPE_MAGICAL_BGM(0x51),
        FILETYPE_MAGICAL_UNKNOWN(0x80);
        final int v;

        en_file_type_magical(int v) {
            this.v = v;
        }
    }

    public enum en_type_name_magical_2 {
        TYPE_NAME_MAGICAL_READONLY,
        TYPE_NAME_MAGICAL_HIDDEN,
        TYPE_NAME_MAGICAL_SYSTEM,
        TYPE_NAME_MAGICAL_SUPER,
    }

    public enum en_data_type_magical {
        DATATYPE_MAGICAL_MASK_b(0x00),
        DATATYPE_MAGICAL_MASK_r(0x01),
        DATATYPE_MAGICAL_MASK_g(0x02),
        DATATYPE_MAGICAL_MASK_m(0x03),
        DATATYPE_MAGICAL_MASK_A(0x04),
        DATATYPE_MAGICAL_MASK_B(0x05),
        DATATYPE_MAGICAL_MASK_C(0x06),
        DATATYPE_MAGICAL_MASK_D(0x07),
        DATATYPE_MAGICAL_MASK_E(0x08),
        DATATYPE_MAGICAL_MASK_F(0x09),
        DATATYPE_MAGICAL_MASK_G(0x0a),
        DATATYPE_MAGICAL_MASK_H(0x0b),
        DATATYPE_MAGICAL_MASK_I(0x0c),
        DATATYPE_MAGICAL_MASK_J(0x0d),
        DATATYPE_MAGICAL_MASK_K(0x0e),
        DATATYPE_MAGICAL_MASK_L(0x0f),
        DATATYPE_MAGICAL_MASK_READONLY(0x10),
        DATATYPE_MAGICAL_MASK_HIDDEN(0x20),
        DATATYPE_MAGICAL_MASK_SYSTEM(0x40),
        DATATYPE_MAGICAL_MASK_SUPER(0x80);
        final int v;

        en_data_type_magical(int v) {
            this.v = v;
        }
    }

    public enum en_type_name_magical_3 {
        TYPE_NAME_MAGICAL_BANK_b,
        TYPE_NAME_MAGICAL_BANK_r,
        TYPE_NAME_MAGICAL_BANK_g,
        TYPE_NAME_MAGICAL_BANK_m,
        TYPE_NAME_MAGICAL_BANK_A,
        TYPE_NAME_MAGICAL_BANK_B,
        TYPE_NAME_MAGICAL_BANK_C,
        TYPE_NAME_MAGICAL_BANK_D,
        TYPE_NAME_MAGICAL_BANK_Unknown
    }

    // From basicdiritem_magical.cpp
    public static final Map<String, Object> gTypeNameMAGICAL_1 = new LinkedHashMap<>() {{
        put("SYS", en_file_type_magical.FILETYPE_MAGICAL_SYS.ordinal()); // 0x01
        put("BAS", en_file_type_magical.FILETYPE_MAGICAL_BAS.ordinal()); // 0x22
        put("OBJ", en_file_type_magical.FILETYPE_MAGICAL_OBJ.ordinal()); // 0x03
        put("ASC", en_file_type_magical.FILETYPE_MAGICAL_ASC.ordinal()); // 0x44
        put("DIR", en_file_type_magical.FILETYPE_MAGICAL_DIR.ordinal()); // 0x05
        put("CDT", en_file_type_magical.FILETYPE_MAGICAL_CDT.ordinal()); // 0x06
        put("PDT", en_file_type_magical.FILETYPE_MAGICAL_PDT.ordinal()); // 0x07
        put("GRA", en_file_type_magical.FILETYPE_MAGICAL_GRA.ordinal()); // 0x08
        put("GAK", en_file_type_magical.FILETYPE_MAGICAL_GAK.ordinal()); // 0x49
        put("SBA", en_file_type_magical.FILETYPE_MAGICAL_SBA.ordinal()); // 0x2a
        put("SOB", en_file_type_magical.FILETYPE_MAGICAL_SOB.ordinal()); // 0x0b
        put("REP", en_file_type_magical.FILETYPE_MAGICAL_REP.ordinal()); // 0x4c
        put("MDT", en_file_type_magical.FILETYPE_MAGICAL_MDT.ordinal()); // 0x0d
        put("ARC", en_file_type_magical.FILETYPE_MAGICAL_ARC.ordinal()); // 0x4e
        put("KTY", en_file_type_magical.FILETYPE_MAGICAL_KTY.ordinal()); // 0x4f
        put("CGP", en_file_type_magical.FILETYPE_MAGICAL_CGP.ordinal()); // 0x50
        put("BGM", en_file_type_magical.FILETYPE_MAGICAL_BGM.ordinal()); // 0x51
        put("???", en_file_type_magical.FILETYPE_MAGICAL_UNKNOWN.ordinal()); // 0x80
    }};

    public static final int[] gTypeNameMAGICALMap = {
            en_file_type_magical.FILETYPE_MAGICAL_SYS.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_BAS.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_OBJ.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_ASC.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_DIR.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_CDT.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_PDT.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_GRA.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_GAK.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_SBA.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_SOB.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_REP.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_MDT.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_ARC.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_KTY.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_CGP.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_BGM.ordinal(),
            en_file_type_magical.FILETYPE_MAGICAL_UNKNOWN.ordinal(),
    };

    public static final String[] gTypeNameMAGICAL_2 = {
            rb.getString("Write Protected"),
            rb.getString("Hidden"),
            rb.getString("System"),
            rb.getString("Super User"),
            null
    };

    private static final String gTypeNameMAGICAL_3s = "brgmABCDEFGHIJKL";

    public static final String[] gTypeNameMAGICAL_3 = {
            "GRAM blue",
            "GRAM red",
            "GRAM green",
            "main",
            "ERAM A",
            "ERAM B",
            "ERAM C",
            "ERAM D",
            rb.getString("Unknown"),
            null
    };


    private final DiskBasicDirData<DirectoryMagical> m_data = new DiskBasicDirData<>();
    private final DirItemSectorBoundary m_sdata = new DirItemSectorBoundary();

    public DiskBasicDirItemMAGICAL(DiskBasic basic) throws IOException {
        super(basic);

        m_data.alloc(DirectoryMagical.class);
        allocateItem(null);
    }

    public DiskBasicDirItemMAGICAL(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) throws IOException {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryMagical.class, n_data, dataP);
        allocateItem(null);
    }

    public DiskBasicDirItemMAGICAL(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse, true);

        m_data.attach(DirectoryMagical.class, n_data, dataP);
        allocateItem(n_next);

        used(checkUsed(n_unuse[0]));

        // チェインセクタへのポインタをセット
        if (isUsed()) {
            attachChain(getStartGroup(0));
        }

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryMagical.class, n_data);
        allocateItem(n_next);
    }

    @Override
    protected boolean allocateItem(SectorParam next) throws IOException {
        m_sdata.clear();
        boolean bound = m_sdata.set(basic, sector, position, m_data.getRawData(), getDataSize(), next);

        if (bound) {
            // セクタをまたぐ場合、dataは内部で確保する
            m_data.fill((byte) 0);
        }

        // コピー
        m_sdata.copyTo(m_data.getRawData());

        return true;
    }

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

    @Override
    public int getFileType1() {
        return m_data.data().type & 0xff;
    }

    protected String convFileType1Str(int t1) {
        return rb.getString(Utils.keyAt(gTypeNameMAGICAL_1, convFileType1Pos(t1)));
    }

    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    @Override
    public int getFileType2() {
        return m_data.data().type2 & 0xff;
    }

    @Override
    protected void setFileType2(int val) {
        m_data.data().type2 = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        int t1 = getFileType1();
        return (!unuse && t1 != 0 && t1 != 0xff);
    }

    @Override
    public void setNativeName(byte[] filename, int size, int[] length) {
        super.setNativeName(filename, size, length);
    }

    @Override
    public void getNativeFileName(byte[] name, int[] nlen, byte[] ext, int[] elen) {
        super.getNativeFileName(name, nlen, ext, elen);
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        if (getFileType1() == 0xff) {
            last[0] = true;
            return valid;
        }
        return valid;
    }

    @Override
    public boolean isDeletable() {
        return true;
    }

    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) {
        if (needCheckEofCode()) {
            // ファイルの最終が終端記号で終わっているかを調べる
            // ただし、ファイルサイズがクラスタサイズと合うなら終端記号は不要
            if ((file_size % (basic.getSectorSize() * basic.getSectorsPerGroup())) != 0) {
                file_size = checkEofCode(istream, file_size);
            }
        }
        return file_size;
    }

    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int t2 = en_data_type_magical.DATATYPE_MAGICAL_MASK_m.ordinal();
        if (file_type.isDirectory()) {
            // ディレクトリの場合
            t1 = en_file_type_magical.FILETYPE_MAGICAL_DIR.ordinal();
        } else if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOSの場合
            t1 = file_type.getOrigin();
            t2 = t1 >> 8;
            t1 &= 0xff;
        } else {
            // 違うOSの場合
            if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                if ((ftype & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
                    t1 = en_file_type_magical.FILETYPE_MAGICAL_SYS.ordinal();
                } else if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                    t1 = en_file_type_magical.FILETYPE_MAGICAL_BAS.ordinal();
                } else {
                    t1 = en_file_type_magical.FILETYPE_MAGICAL_OBJ.ordinal();
                }
            } else {
                t1 = en_file_type_magical.FILETYPE_MAGICAL_ASC.ordinal();
            }
        }
        setFileType1(t1);
        setFileType2(t2);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1) {
            case 0x01: // FILETYPE_MAGICAL_SYS
                val = FILE_TYPE_SYSTEM_MASK.getValue();        // system
                val |= FILE_TYPE_BINARY_MASK.getValue();        // binary
                break;
            case 0x03: // FILETYPE_MAGICAL_OBJ
                val = FILE_TYPE_MACHINE_MASK.getValue();        // machine
                val |= FILE_TYPE_BINARY_MASK.getValue();        // binary
                break;
            case 0x22: // FILETYPE_MAGICAL_BAS
                val = FILE_TYPE_BASIC_MASK.getValue();            // basic
                val |= FILE_TYPE_BINARY_MASK.getValue();        // binary
                break;
            case 0x44: // FILETYPE_MAGICAL_ASC
                val = FILE_TYPE_DATA_MASK.getValue();            // data
                val |= FILE_TYPE_ASCII_MASK.getValue();        // ascii
                break;
            case 0x05: // FILETYPE_MAGICAL_DIR
                val = FILE_TYPE_DIRECTORY_MASK.getValue();        // directory
                break;
            default:
                val = FILE_TYPE_DATA_MASK.getValue();            // data
                val |= FILE_TYPE_BINARY_MASK.getValue();        // binary
                break;
        }

        int t2 = getFileType2();
        if ((t2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_READONLY.ordinal()) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((t2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_HIDDEN.ordinal()) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((t2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_SYSTEM.ordinal()) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }
        if ((t2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_SUPER.ordinal()) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (t2 << 8) | t1);
    }

    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder(convFileType1Str(getFileType1()));
        //
        int t2 = getFileType2();

        attr.append(", ");
        attr.append(gTypeNameMAGICAL_3s.charAt(t2 & 0xf));

        if ((t2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_READONLY.ordinal()) != 0) {
            attr.append(", ");
            attr.append(rb.getString(gTypeNameMAGICAL_2[en_type_name_magical_2.TYPE_NAME_MAGICAL_READONLY.ordinal()]));
        }
        if ((t2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_HIDDEN.ordinal()) != 0) {
            attr.append(", ");
            attr.append(rb.getString(gTypeNameMAGICAL_2[en_type_name_magical_2.TYPE_NAME_MAGICAL_HIDDEN.ordinal()]));
        }
        if ((t2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_SYSTEM.ordinal()) != 0) {
            attr.append(", ");
            attr.append(rb.getString(gTypeNameMAGICAL_2[en_type_name_magical_2.TYPE_NAME_MAGICAL_SYSTEM.ordinal()]));
        }
        if ((t2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_SUPER.ordinal()) != 0) {
            attr.append(", ");
            attr.append(rb.getString(gTypeNameMAGICAL_2[en_type_name_magical_2.TYPE_NAME_MAGICAL_SUPER.ordinal()]));
        }
        return attr.toString();
    }

    @Override
    public void setFileSize(int val) {
        short size = basic.orderUint16((short) val);
        m_data.data().fileSize = size;
    }

    @Override
    public int getFileSize() {
        short size = m_data.data().fileSize;
        return basic.orderUint16(size);
    }

    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        byte track = (byte) ((val / basic.getSectorsPerTrackOnBasic()) & 0xff);
        byte sector = (byte) (((val % basic.getSectorsPerTrackOnBasic()) + 1) & 0xff);
        m_data.data().start.track = track;
        m_data.data().start.sector = sector;
        m_data.data().start.size = (byte) size;
    }

    @Override
    public int getStartGroup(int fileunit_num) {
        int track = m_data.data().start.track & 0xff;
        int sector = m_data.data().start.sector & 0xff;
        return track * basic.getSectorsPerTrackOnBasic() + sector - 1;
    }

    @Override
    public void setExtraGroup(int val) {
        byte track = (byte) ((val / basic.getSectorsPerTrackOnBasic()) & 0xff);
        byte sector = (byte) (((val % basic.getSectorsPerTrackOnBasic()) + 1) & 0xff);
        m_data.data().start.track = track;
        m_data.data().start.sector = sector;
        m_data.data().start.size = 1;
    }

    @Override
    public int getExtraGroup() {
        int track = m_data.data().start.track & 0xff;
        int sector = m_data.data().start.sector & 0xff;
        return track * basic.getSectorsPerTrackOnBasic() + sector - 1;
    }

    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int d0 = m_data.data().date[0] & 0xff;
        int d1 = m_data.data().date[1] & 0xff;

        int date = (d0 << 8) | d1;
        return convDateToTm((short) date);
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        int t0 = m_data.data().time[0] & 0xff;
        int t1 = m_data.data().time[1] & 0xff;

        int time = (t0 << 8) | t1;
        return convTimeToTm((short) time);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            short date = convTmToDate(tm);
            m_data.data().date[0] = (byte) (date >> 8);
            m_data.data().date[1] = (byte) (date & 0xff);
        }
    }

    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            short time = convTmToTime(tm);
            m_data.data().time[0] = (byte) (time >> 8);
            m_data.data().time[1] = (byte) (time & 0xff);
        }
    }

    @Override
    public String getFileCreateDateTimeTitle() {
        return "Created Date";
    }

    private static LocalDate convDateToTm(short date) {
        int d = date & 0xffff;
        int yy = ((d & 0xfe00) >> 9) + 80;
        if (yy >= 128) yy -= 28;
        return LocalDate.of(
                yy,
                ((d & 0x01e0) >> 5) - 1,
                d & 0x001f);
    }

    private static LocalTime convTimeToTm(short time) {
        int t = time & 0xffff;
        return LocalTime.of(
                (t & 0xf800) >> 11,
                (t & 0x07e0) >> 5,
                (t & 0x001f) << 1);
    }

    private static short convTmToDate(LocalDateTime tm) {
        int yy = tm.getYear();
        if (yy >= 100) yy += 28;
        return (short)
                ((((yy - 80) & 0x7f) << 9)
                        | (((tm.getMonth().ordinal() + 1) & 0xf) << 5)
                        | (tm.getDayOfMonth() & 0x1f));
    }

    private static short convTmToTime(LocalDateTime tm) {
        return (short) (
                ((tm.getHour() & 0x1f) << 11)
                        | ((tm.getMinute() & 0x3f) << 5)
                        | ((tm.getSecond() & 0x3f) >> 1));
    }

    @Override
    public int getStartAddress() {
        short addr = m_data.data().loadAddr;
        return basic.orderUint16(addr);
    }

    @Override
    public int getExecuteAddress() {
        short addr = m_data.data().execAddr;
        return basic.orderUint16(addr);
    }

    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = basic.orderUint16((short) val);
    }

    @Override
    public void setExecuteAddress(int val) {
        m_data.data().execAddr = basic.orderUint16((short) val);
    }

    @Override
    public int getDataSize() {
        return 32; // sizeof(directory_magical_t)
    }

    @Override
    public DirectoryMagical getData() {
        return m_data.data();
    }

    @Override
    public boolean copyData(DirectoryMagical val) {
        boolean sts = m_data.copy(val);
        m_sdata.copyFrom(m_data.getRawData());
        return sts;
    }

    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getDeleteCode());
        m_sdata.copyFrom(m_data.getRawData());
    }

    @Override
    public void initialData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir());
            m_sdata.copyFrom(m_data.getRawData());
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.isAddExtensionExport()) return true;

        // 拡張子を付加する
        if (!isDirectory()) {
            String ext = convFileType1Str(getFileType1());
            filename[0] += ".";
            if (Utils.isUpperString(filename[0])) {
                ext = ext.toUpperCase();
            } else {
                ext = ext.toLowerCase();
            }
            filename[0] += ext;
        }
        return true;
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], gTypeNameMAGICAL_1, en_type_name_magical_1.TYPE_NAME_MAGICAL_SYS.ordinal(), en_type_name_magical_1.TYPE_NAME_MAGICAL_BGM.ordinal(), filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        // 拡張子で属性を設定する
        // Simplified external function call:
        if (!isContainAttrByExtension(filename, gTypeNameMAGICAL_1, en_type_name_magical_1.TYPE_NAME_MAGICAL_SYS.ordinal(), en_type_name_magical_1.TYPE_NAME_MAGICAL_BGM.ordinal(), null, new int[] {t1}, null)) {
            // 不明の拡張子
            t1 = en_file_type_magical.FILETYPE_MAGICAL_ASC.v;
        }
//        int extType = getFileTypeFromExtension(filename); // Mock method
//        if (extType != -1) {
//            t1 = extType;
//        } else {
//            // 不明の拡張子
//            t1 = en_file_type_magical.FILETYPE_MAGICAL_ASC.ordinal();
//        }
        t1 |= (en_data_type_magical.DATATYPE_MAGICAL_MASK_m.ordinal() << 8);
        return t1;
    }

    // Mock implementation for getFileTypeFromExtension
    private int getFileTypeFromExtension(String filename) {
        // Basic mock: check for a few extensions
        if (filename.endsWith(".SYS")) return en_file_type_magical.FILETYPE_MAGICAL_SYS.ordinal();
        if (filename.endsWith(".BAS")) return en_file_type_magical.FILETYPE_MAGICAL_BAS.ordinal();
        if (filename.endsWith(".OBJ")) return en_file_type_magical.FILETYPE_MAGICAL_OBJ.ordinal();
        if (filename.endsWith(".ASC")) return en_file_type_magical.FILETYPE_MAGICAL_ASC.ordinal();
        return -1;
    }

    @Override
    public void setModify() {
        m_sdata.copyFrom(m_data.getRawData());
    }

    private int convFileType1Pos(int t1) {
        int pos = en_type_name_magical_1.TYPE_NAME_MAGICAL_UNKNOWN.ordinal();
        for (int i = 0; ; i++) {
            if (i >= gTypeNameMAGICALMap.length || gTypeNameMAGICALMap[i] == 0) {
                break;
            }
            int val = gTypeNameMAGICALMap[i];
            if (t1 == val) {
                pos = i;
                break;
            }
        }
        return pos;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TYPE", m_data.data().type);
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("TYPE2", m_data.data().type2);
        vals.add("LOAD_ADDR", (byte) m_data.data().loadAddr, basic.isBigEndian());
        vals.add("FILE_SIZE", (byte) m_data.data().fileSize, basic.isBigEndian());
        vals.add("EXEC_ADDR", (byte) m_data.data().execAddr, basic.isBigEndian());
        vals.add("DATE", m_data.data().date, m_data.data().date.length);
        vals.add("TIME", m_data.data().time, m_data.data().time.length);
        vals.add("RESERVED", m_data.data().reserved, m_data.data().reserved.length);
        vals.add("START.TRACK", m_data.data().start.track);
        vals.add("START.SECTOR", m_data.data().start.sector);
        vals.add("START.SIZE", m_data.data().start.size);
    }
}