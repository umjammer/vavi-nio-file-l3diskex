/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javax.swing.JWindow;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryMz;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_TEMPORARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.DATATYPE_MZ_READ_ONLY;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.DATATYPE_MZ_SEAMLESS;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.DATATYPE_MZ_SEAMLESS_POS;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_BRD;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_BSD;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_BTX;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_DIR;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_OBJ;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_VOL;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_VOLSWAP;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ2_READ_ONLY;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_BRD;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_BSD;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_BTX;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_DIR;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_OBJ;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_UNKNOWN;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_VOL;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_VOLSWAP;
import static l3diskex.basicfmt.DiskBasicError.ERR_FILENAME_EMPTY;
import static l3diskex.basicfmt.DiskBasicError.gDiskBasicErrorMsgs;


public class DiskBasicDirItemMZ extends DiskBasicDirItemMZBase<DirectoryMz> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    // MZ specific constants
    interface MZConstants {
        int FILETYPE_MZ_OBJ = 1;
        int FILETYPE_MZ_BTX = 2;
        int FILETYPE_MZ_BSD = 3;
        int FILETYPE_MZ_BRD = 4;
        int FILETYPE_MZ_DIR = 0xf;
        int FILETYPE_MZ_VOL = 0x80;
        int FILETYPE_MZ_VOLSWAP = 0x81;

        int DATATYPE_MZ_READ_ONLY = 0x01;
        int DATATYPE_MZ_SEAMLESS = 0x80;

        int DATATYPE_MZ_SEAMLESS_POS = 20;

        int TYPE_NAME_MZ_UNKNOWN = 0;
        int TYPE_NAME_MZ_OBJ = 1;
        int TYPE_NAME_MZ_BTX = 2;
        int TYPE_NAME_MZ_BSD = 3;
        int TYPE_NAME_MZ_BRD = 4;
        int TYPE_NAME_MZ_DIR = 5;
        int TYPE_NAME_MZ_VOL = 6;
        int TYPE_NAME_MZ_VOLSWAP = 7;
        int TYPE_NAME_MZ_END = 8;

        int TYPE_NAME_MZ2_READ_ONLY = 0;
        int TYPE_NAME_MZ2_SEAMLESS = 1;

        enum DateTimeFlags { DATETIME_ALL }
    }

    public static class Globals implements MZConstants {
        public static final Map<String, Object> gTypeNameMZ = new HashMap<>() {{
            put("???", TYPE_NAME_MZ_UNKNOWN);
            put("OBJ", FILETYPE_MZ_OBJ);
            put("BTX", FILETYPE_MZ_BTX);
            put("BSD", FILETYPE_MZ_BSD);
            put("BRD", FILETYPE_MZ_BRD);
            put("DIR", FILETYPE_MZ_DIR);
            put(rb.getString("<VOL>"), FILETYPE_MZ_VOL);
            put(rb.getString("<VOL> SWAP"), FILETYPE_MZ_VOLSWAP);
        }};

        public static final String[] gTypeNameMZ2 = {
                rb.getString("Write Protected"),
                rb.getString("Seamless"),
                null
        };
    }

    private DiskBasicDirData<DirectoryMz> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemMZ(DiskBasic basic) {
        super(basic);
        m_data.alloc(DirectoryMz.class);
    }

    public DiskBasicDirItemMZ(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
        m_data.attach(n_data);
    }

    public DiskBasicDirItemMZ(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
        m_data.attach(n_data);

        used(checkUsed(n_unuse[0]));

        calcFileSize();

        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && (name.equals(".") || name.equals(".."))));
    }

    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, n_next);
        m_data.attach(n_data);
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = m_data.data().name.length;
            len[0] = size[0] - 1;
            return m_data.data().name;
        } else {
            size[0] = 0;
            len[0] = 0;
            return null;
        }
    }

    @Override
    public int getFileType1() {
        return basic.invertUint8(m_data.data().type);
    }

    @Override
    public int getFileType2() {
        return basic.invertUint8(m_data.data().type2);
    }

    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte)basic.invertUint8((byte) val);
    }

    @Override
    protected void setFileType2(int val) {
        m_data.data().type2 = (byte)basic.invertUint8((byte) val);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return (getFileType1() != 0);
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        int t = getFileType1();
        if ((t & 0x70) != 0 && basic.diskBasicParam.getSpecialAttributes().findValue(t) == null) {
            valid = false;
        }
        return valid;
    }

//    public boolean Delete() {
//        m_data.Fill(basic.InvertUint8(basic.GetDeleteCode()), 1);
//        Used(false);
//        return true;
//    }

    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int t2 = 0;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            t1 = file_type.getOrigin() & 0xff;
            t2 = (file_type.getOrigin() >> 8) & 0xff;
        } else {
            t1 = convToNativeType(ftype);
            if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
                t2 |= DATATYPE_MZ_READ_ONLY;
            }
        }
        setFileType1(t1);
        setFileType2(t2);
    }

    private int convToNativeType(int file_type) {
        int val = 0;
        if ((file_type & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_OBJ;
        } else if ((file_type & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_BTX;
        } else if ((file_type & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_BSD;
        } else if ((file_type & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_BRD;
        } else if ((file_type & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_DIR;
        } else if ((file_type & FILE_TYPE_VOLUME_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_VOL;
            if ((file_type & FILE_TYPE_TEMPORARY_MASK.getValue()) != 0) val = FILETYPE_MZ_VOLSWAP;
        }
        return val;
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch(t1) {
            case FILETYPE_MZ_OBJ:
                val = FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue();
                break;
            case FILETYPE_MZ_BTX:
                val = FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue(); // Changed from FILE_TYPE_BASIC_MASK based on C++ comment
                break;
            case FILETYPE_MZ_BSD:
                val = FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_ASCII_MASK.getValue(); // Changed from FILE_TYPE_BASIC_MASK
                break;
            case FILETYPE_MZ_BRD:
                val = FILE_TYPE_RANDOM_MASK.getValue() | FILE_TYPE_RANDOM_MASK.getValue(); // Changed from FILE_TYPE_DATA_MASK
                break;
            case FILETYPE_MZ_DIR:
                val = FILE_TYPE_DIRECTORY_MASK.getValue();
                break;
            case FILETYPE_MZ_VOL:
                val = FILE_TYPE_VOLUME_MASK.getValue();
                break;
            case FILETYPE_MZ_VOLSWAP:
                val = FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_TEMPORARY_MASK.getValue();
                break;
            default:
                val = basic.diskBasicParam.getSpecialAttributes().getTypeByValue(t1);
                break;
        }
        int t2 = getFileType2();
        if ((t2 & DATATYPE_MZ_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        val |= ((t2 & DATATYPE_MZ_SEAMLESS) << DATATYPE_MZ_SEAMLESS_POS);

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (t2 << 8) | t1);
    }

    @Override
    public String getFileAttrStr() {
        String[] attr = new String[1];
        getFileAttrName(convFileType1Pos(getFileType1()), Globals.gTypeNameMZ, attr, TYPE_NAME_MZ_UNKNOWN);

        int t2 = getFileType2();
        if ((t2 & DATATYPE_MZ_READ_ONLY) != 0) {
            attr[0] += ", ";
            attr[0] += rb.getString(Globals.gTypeNameMZ2[TYPE_NAME_MZ2_READ_ONLY]);
        }

        return attr[0];
    }

    @Override
    protected void setFileSizeBase(int val) {
        m_data.data().fileSize = (short)basic.invertAndOrderUint16((short) val);
    }

    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        if (getFileType1() == FILETYPE_MZ_BRD) {
            val = ((val + 31) / 32);
        }
        setFileSizeBase(val);
    }

    @Override
    protected int getFileSizeBase() {
        int val = basic.invertAndOrderUint16(m_data.data().fileSize);
        if (getFileType1() == FILETYPE_MZ_BRD) {
            val *= 32;
        }
        return val;
    }

    private static class StBrdParams {
        int pos;
        int cnt;
        short[] maps; // wxUint16
    }

    @Override
    protected void preCalcFileSize() {
//        if (GetFileType1() == FILETYPE_MZ_BRD) {
//            m_file_size *= 32;
//        }
    }

    @Override
    protected void preCalcAllGroups(int[] calc_flags, int[] group_num, int[] remain, int[] sec_size, Object[] user_data) {
        boolean is_chain = needChainInData();
        boolean is_brd = (getFileType1() == FILETYPE_MZ_BRD);
        calc_flags[0] = ((is_chain ? 1 : 0) | (is_brd ? 2 : 0));

        StBrdParams brd = new StBrdParams();
        brd.pos = 0;
        brd.cnt = 0;
        brd.maps = null;

        if (is_chain) {
            sec_size[0] -= 2;
        }
        if (is_brd) {
            DiskImageSector sector = basic.getSectorFromGroup(group_num[0]);
            if (sector != null) {
                // This is the pointer map to each start sector
                byte[] buffer = sector.getSectorBuffer();
                brd.maps = new short[buffer.length / 2];
                // Need to mock data reading and byte order
                for (int i = 0; i < brd.maps.length; i++) {
                    // Assuming basic.InvertAndOrderUint16 handles byte order and inversion
                    brd.maps[i] = (short)basic.invertAndOrderUint16((short) ((buffer[i*2+1] & 0xFF) | ((buffer[i*2] & 0xFF) << 8)));
                }

                group_num[0] = basic.invertAndOrderUint16(brd.maps[brd.pos]);
                group_num[0] /= basic.getSectorsPerGroup();

                int block_size = (16 * basic.getSectorSize());
                remain[0] = ((remain[0] + block_size - 1) / block_size) * block_size;
            }
        }

        user_data[0] = brd;
    }

    @Override
    protected void calcAllGroups(int calc_flags, int[] group_num, int[] remain, int[] sec_size, int[] end_sec, Object user_data) {
        boolean is_chain = ((calc_flags & 1) != 0);
        boolean is_brd = ((calc_flags & 2) != 0);
        StBrdParams brd = (StBrdParams)user_data;

        if (is_chain) {
            // BSD
             group_num[0] = type.getNextGroupNumber(group_num[0], end_sec[0]); // type is undefined here, assuming it's accessible via basic
        } else {
            // BTX,OBJ
            group_num[0]++;
            if (is_brd) {
                // BRD
                brd.cnt += basic.getSectorsPerGroup();
                if (brd.cnt >= 16) {
                    brd.cnt = 0;
                    if (((brd.pos + 1) * 2) < basic.getSectorSize()) {
                        brd.pos++;
                    }
                    if (brd.maps != null && brd.pos < brd.maps.length) {
                        group_num[0] = basic.invertAndOrderUint16(brd.maps[brd.pos]);
                        group_num[0] /= basic.getSectorsPerGroup();
                    }
                }
            }
        }
    }

    @Override
    protected void postCalcAllGroups(Object user_data) {
        // In Java, StBrdParams will be garbage collected, no explicit delete needed.
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int ymd;
        ymd = (m_data.data().dateTime[0] & 0xFF) << 16 | (m_data.data().dateTime[1] & 0xFF) << 8 | (m_data.data().dateTime[2] & 0xFF);
        int inverted_ymd = basic.invertUint32(ymd);

        // BCD decoding
        return LocalDate.of(
                (int) (((inverted_ymd >> 20) & 0x0f) * 10 + ((inverted_ymd >> 16) & 0x0f) +
                                     (tm.getYear() < 80 ? 100 : 0)),
                (int) (((inverted_ymd >> 15) & 1) * 10 + ((inverted_ymd >> 11) & 0x0f) - 1),
                (int) (((inverted_ymd >> 9) & 3) * 10 + ((inverted_ymd >> 5) & 0x0f))
        );
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        int hms;
        hms = (m_data.data().dateTime[2] & 0xFF) << 8 | (m_data.data().dateTime[3] & 0xFF);
        int inverted_hms = basic.invertUint32(hms);

        return LocalTime.of(
                (int)(((inverted_hms >> 11) & 3) * 10 + ((inverted_hms >> 7) & 0x0f)),
                (int)(((inverted_hms >> 4) & 0x7) * 10 + (inverted_hms & 0x0f)),
                0);
    }

    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateDate(tm);
        return Utils.formatYMDStr(tm);
    }

    @Override
    public String getFileCreateTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateTime(tm);
        return Utils.formatHMStr(tm);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() < 0 || tm.getMonth().ordinal() < -1) return;

        int tmp = (m_data.data().dateTime[0] & 0xFF) << 16 | (m_data.data().dateTime[1] & 0xFF) << 8 | (m_data.data().dateTime[2] & 0xFF);
        int inverted_tmp = (int)basic.invertUint32(tmp);
        inverted_tmp &= 0x1f;
        inverted_tmp |= (((tm.getYear() / 10) % 10) << 20) | ((tm.getYear() % 10) << 16);
        inverted_tmp |= ((((tm.getMonth().ordinal() + 1) / 10) & 1) << 15) | (((tm.getMonth().ordinal() + 1) % 10) << 11);
        inverted_tmp |= (((tm.getDayOfMonth() / 10) & 3) << 9) | ((tm.getDayOfMonth() % 10) << 5);
        int final_tmp = (int)basic.invertUint32(inverted_tmp);

        m_data.data().dateTime[0] = (byte)(final_tmp >> 16);
        m_data.data().dateTime[1] = (byte)(final_tmp >> 8);
        m_data.data().dateTime[2] = (byte)(final_tmp & 0xff);
    }

    @Override
    public void setFileCreateTime(final LocalDateTime tm) {
        if (tm.getHour() < 0 || tm.getMinute() < 0) return;

        int tmp = (m_data.data().dateTime[2] & 0xFF) << 8 | (m_data.data().dateTime[3] & 0xFF);
        int inverted_tmp = (int)basic.invertUint32(tmp & 0xFFFF_FFFF);
        inverted_tmp &= ~0x1fff;
        inverted_tmp |= (((tm.getHour() / 10) & 3) << 11) | ((tm.getHour() % 10) << 7);
        inverted_tmp |= (((tm.getMinute() / 10) & 7) << 4) | (tm.getMinute() % 10);
        int final_tmp = (int)basic.invertUint32(inverted_tmp & 0xFFFF_FFFF);

        m_data.data().dateTime[2] = (byte)(final_tmp >> 8);
        m_data.data().dateTime[3] = (byte)(final_tmp & 0xff);
    }

    @Override
    public int getStartAddress() {
        return basic.invertAndOrderUint16(m_data.data().loadAddr);
    }

    @Override
    public int getExecuteAddress() {
        return basic.invertAndOrderUint16(m_data.data().execAddr);
    }

    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = (short)basic.invertAndOrderUint16((short) val);
    }

    @Override
    public void setExecuteAddress(int val) {
        m_data.data().execAddr = (short)basic.invertAndOrderUint16((short) val);
    }

    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    @Override
    public DirectoryMz getData() {
        return m_data.data();
    }

    @Override
    public boolean copyData(final DirectoryMz val) {
        return m_data.copy(val, getDataSize());
    }

    @Override
    public void clearData() {
        if (!m_data.isValid()) return;
        m_data.fill(0);
        Arrays.fill(m_data.data().name, (byte)0x0d);
        // Note: Java doesn't use the direct memory inversion like C++ often does,
        // so this method call is a placeholder for the logic that applies the MZ-specific inversion.
        basic.invertMem(m_data.getRawData(), m_data.getDataSize());
    }

    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        int sval = (int)(val * basic.getSectorsPerGroup());
        sval = basic.invertAndOrderUint16((short) sval);
        m_data.data().startSector = (short)sval;
    }

    @Override
    public int getStartGroup(int fileunit_num) {
        int sval = basic.invertAndOrderUint16(m_data.data().startSector);
        return (sval / basic.getSectorsPerGroup());
    }

    @Override
    public int getExtraGroup() {
        int val = INVALID_GROUP_NUMBER;
        if (getFileType1() == FILETYPE_MZ_BRD) {
            val = getStartGroup(0);
        }
        return val;
    }

    @Override
    public void getExtraGroups(List<Integer> arr) {
        if (getFileType1() == FILETYPE_MZ_BRD) {
            arr.add((int)getStartGroup(0));
        }
    }

    @Override
    public boolean isSameFileName(DiskBasicFileName filename, boolean icase) {
        int t1 = getFileType1();
        if (t1 == 0 || t1 == FILETYPE_MZ_VOL) return false;

        return super.isSameFileName(filename, icase);
    }

    @Override
    public boolean isSameFileName(DiskBasicDirItem src, boolean icase) {
        int t1 = getFileType1();
        if (t1 == 0 || t1 == FILETYPE_MZ_VOL) return false;

        return super.isSameFileName(src, icase);
    }

    @Override
    public boolean needChainInData() {
        return getFileAttr().matchType(FILE_TYPE_ASCII_MASK.getValue(), FILE_TYPE_ASCII_MASK.getValue());
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.IsAddExtensionExport()) return true;

        if (!isDirectory()) {
            String[] ext = new String[1];
            if (getFileAttrName(convFileType1Pos(getFileType1()), Globals.gTypeNameMZ, ext, TYPE_NAME_MZ_UNKNOWN)) {
                filename[0] += ".";
                if (Utils.isUpperString(filename[0])) {
                    filename[0] += ext[0].toUpperCase();
                } else {
                    filename[0] += ext[0].toLowerCase();
                }
            }
        }
        return true;
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.IsDecideAttrImport()) {
            isContainAttrByExtension(filename[0], Globals.gTypeNameMZ, TYPE_NAME_MZ_OBJ, TYPE_NAME_MZ_DIR, filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(final String filename) {
        int[] t1 = new int[1];
        if (!isContainAttrByExtension(filename, Globals.gTypeNameMZ, TYPE_NAME_MZ_OBJ, TYPE_NAME_MZ_DIR, null, t1, null)) {
            t1[0] = FILETYPE_MZ_BSD;
        }
        return t1[0];
    }

    public int convFileType1Pos(int native_type) {
        int pos = TYPE_NAME_MZ_UNKNOWN;
        switch(native_type) {
            case FILETYPE_MZ_OBJ: pos = TYPE_NAME_MZ_OBJ; break;
            case FILETYPE_MZ_BTX: pos = TYPE_NAME_MZ_BTX; break;
            case FILETYPE_MZ_BSD: pos = TYPE_NAME_MZ_BSD; break;
            case FILETYPE_MZ_BRD: pos = TYPE_NAME_MZ_BRD; break;
            case FILETYPE_MZ_DIR: pos = TYPE_NAME_MZ_DIR; break;
            case FILETYPE_MZ_VOL: pos = TYPE_NAME_MZ_VOL; break;
            case FILETYPE_MZ_VOLSWAP: pos = TYPE_NAME_MZ_VOLSWAP; break;
            default: pos = -native_type; break;
        }
        return pos;
    }

    public int convFileType2Pos(int native_type) {
        int val = 0;
        if ((native_type & DATATYPE_MZ_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        val |= ((native_type & DATATYPE_MZ_SEAMLESS) << DATATYPE_MZ_SEAMLESS_POS);
        return val;
    }

    public void setFileTypeForAttrDialog(int show_flags, final String name, int[] file_type_1, int[] file_type_2) {
        if ((show_flags & 1) != 0) { // INTNAME_NEW_FILE is assumed to be 1
            file_type_1[0] = convOriginalTypeFromFileName(name);
        }
    }

    public boolean validateFileName(final JWindow parent, final String filename, String[] errormsg) {
        if (filename.isEmpty()) {
            errormsg[0] = rb.getString(gDiskBasicErrorMsgs[ERR_FILENAME_EMPTY]);
            return false;
        }
        return true;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("self", m_data.isSelf());
        vals.add("inverted", basic.isDataInverted());

        vals.add("TYPE", (byte) (m_data.data().type & 0xFF), basic.isDataInverted());
        vals.add("NAME", m_data.data().name, m_data.data().name.length, basic.isDataInverted());
        vals.add("TYPE2", (byte) (m_data.data().type2 & 0xFF), basic.isDataInverted());
        vals.add("RESERVED", (byte) (m_data.data().reserved & 0xFF), basic.isDataInverted());
        vals.add("FILE_SIZE", m_data.data().fileSize & 0xFFFF, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", m_data.data().loadAddr & 0xFFFF, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", m_data.data().execAddr & 0xFFFF, basic.isBigEndian(), basic.isDataInverted());
        vals.add("DATE_TIME", m_data.data().dateTime, m_data.data().dateTime.length, basic.isDataInverted());
        vals.add("START_SECTOR", m_data.data().startSector & 0xFFFF, basic.isBigEndian(), basic.isDataInverted());
    }
}