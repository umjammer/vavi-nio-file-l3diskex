///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ResourceBundle;

import l3diskex.basicfmt.BasicCommon.DirectoryFp;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;


/// ディレクトリ１アイテム Casio FP-1100 C82-BASIC
public class DiskBasicDirItemFP extends DiskBasicDirItemFAT8<DirectoryFp> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    // Casio FP-1100 C82-BASIC file types
    public static final int FILETYPE_FP_BASIC = 0x10;	// BASIC
    public static final int FILETYPE_FP_DATA = 0x04;	// DATA
    public static final int FILETYPE_FP_MACHINE = 0x0d;	// MACHINE
    public static final int FILETYPE_FP_ASCII = 0x20;	// ascii
    public static final int FILETYPE_FP_RANDOM = 0x80;	// random

    // C82-BASIC attributes
    public static final String[] G_TYPE_NAME_FP_2 = {
            "Write Protected",
            "Read After Write",
    };
    public static final int TYPE_NAME_FP_READ_ONLY = 0;
    public static final int TYPE_NAME_FP_READ_WRITE = 1;
    public static final int TYPE_NAME_FP_UNKNOWN = 2;

    public static final int DATATYPE_MASK_FP_READ_ONLY = 0xf0;
    public static final int DATATYPE_MASK_FP_READ_WRITE = 0x0f;

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryFp> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemFP(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryFp.class);
    }

    public DiskBasicDirItemFP(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryFp.class, n_data, dataP);
    }

    public DiskBasicDirItemFP(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryFp.class, n_data, dataP);

        used(checkUsed(n_unuse[0]));
        n_unuse[0] = (n_unuse[0] || m_data.data().name[0] == (byte) 0xff);

        // ファイルサイズとグループ数を計算
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
     * @param n_next   [out] 次のセクタ
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryFp.class, n_data, dataP);
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
    protected byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

    @Override
    protected int getFileType1() {
        return m_data.data().type & 0xff;
    }

    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    @Override
    public int getFileType2() {
        return m_data.data().attr & 0xff;
    }

    @Override
    protected void setFileType2(int val) {
        m_data.data().attr = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return !unuse && this.m_data.data().type != 0;
    }

    /** エントリデータの未使用部分を設定 */
    private void setTerminate(int val) {
        if (!m_data.isValid()) return;

        int c;
        c = (val != 0 ? 0xff : 0);
        m_data.data().term = (byte) (c & 0xff);

        c = ((val & FILETYPE_FP_BASIC) != 0 ? 0xff : 0);
        Arrays.fill(m_data.data().unknown, 0, m_data.data().unknown.length, (byte) c);
    }

    @Override
    public int getFileType1Pos() {
        int t1 = getFileType1();
        int val = TYPE_NAME_1_UNKNOWN;
        if ((t1 & FILETYPE_FP_MACHINE) == FILETYPE_FP_MACHINE) {
            val = TYPE_NAME_1_MACHINE;
        } else if ((t1 & FILETYPE_FP_BASIC) == FILETYPE_FP_BASIC) {
            val = TYPE_NAME_1_BASIC;
        } else if ((t1 & FILETYPE_FP_DATA) == FILETYPE_FP_DATA) {
            val = TYPE_NAME_1_DATA;
        }
        return val;
    }

    @Override
    public int getFileType2Pos() {
        int t1 = getFileType1();
        int val = 0;
        if ((t1 & FILETYPE_FP_RANDOM) != 0) {
            val = TYPE_NAME_2_RANDOM;
        } else if ((t1 & FILETYPE_FP_ASCII) != 0) {
            val = TYPE_NAME_2_ASCII;
        } else {
            val = TYPE_NAME_2_BINARY;
        }
        return val;
    }

    @Override
    public void takeAddressesInFile() {
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;

        if (((m_data.data().type & 0x02) != 0) || (m_data.data().term != 0 && m_data.data().term != 0xff)) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        clearData();
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int t2 = 0;
        if ((ftype & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
            t1 = FILETYPE_FP_MACHINE;
        } else if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
            t1 = FILETYPE_FP_BASIC;
        } else if ((ftype & FILE_TYPE_DATA_MASK.getValue()) != 0) {
            t1 = FILETYPE_FP_DATA;
        }
        if ((ftype & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            t1 |= FILETYPE_FP_RANDOM;
        } else if ((ftype & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            t1 |= FILETYPE_FP_ASCII;
        }

        if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            t2 |= DATATYPE_MASK_FP_READ_ONLY;
        }
        if ((ftype & FILE_TYPE_READWRITE_MASK.getValue()) != 0) {
            t2 |= DATATYPE_MASK_FP_READ_WRITE;
        }

        setFileType1(t1);
        setFileType2(t2);

        setTerminate(t1);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int t2 = getFileType2();
        int val = 0;
        if ((t1 & FILETYPE_FP_MACHINE) == FILETYPE_FP_MACHINE) {
            val = FILE_TYPE_MACHINE_MASK.getValue();
        } else if ((t1 & FILETYPE_FP_BASIC) == FILETYPE_FP_BASIC) {
            val = FILE_TYPE_BASIC_MASK.getValue();
        } else if ((t1 & FILETYPE_FP_DATA) == FILETYPE_FP_DATA) {
            val = FILE_TYPE_DATA_MASK.getValue();
        }

        if ((t1 & FILETYPE_FP_RANDOM) != 0) {
            val |= FILE_TYPE_RANDOM_MASK.getValue();
        } else if ((t1 & FILETYPE_FP_ASCII) != 0) {
            val |= FILE_TYPE_ASCII_MASK.getValue();
        } else {
            val |= FILE_TYPE_BINARY_MASK.getValue();
        }

        if ((t2 & DATATYPE_MASK_FP_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((t2 & DATATYPE_MASK_FP_READ_WRITE) != 0) {
            val |= FILE_TYPE_READWRITE_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t2 << 8 | t1);
    }

    @Override
    public String getFileAttrStr() {
        int p1 = getFileType1Pos();
        String attr = rb.getString(G_TYPE_NAME_1[p1]);
        //
        int p2 = getFileType2Pos();
        attr += " - ";
        attr += G_TYPE_NAME_2[p2];
        //
        int t2 = getFileType2();
        if ((t2 & DATATYPE_MASK_FP_READ_ONLY) != 0) {
            attr += ", ";
            attr += G_TYPE_NAME_FP_2[TYPE_NAME_FP_READ_ONLY];
        }
        if ((t2 & DATATYPE_MASK_FP_READ_WRITE) != 0) {
            attr += ", ";
            attr += G_TYPE_NAME_FP_2[TYPE_NAME_FP_READ_WRITE];
        }
        return attr;
    }

    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int t1 = getFileType1();
        if (t1 == FILETYPE_FP_BASIC) {
            // BASIC - binary
            m_data.data().fileSize = (short) val;
            m_data.data().endAddr = (short) val;
        } else if (t1 == FILETYPE_FP_MACHINE) {
            // Machine
            m_data.data().fileSize = (short) val;
            int end_addr = m_data.data().loadAddr + val - 1;
            m_data.data().endAddr = (short) end_addr;
        }
        // アスキーファイルはファイルサイズをセットしない
    }

    @Override
    public int getFileSize() {
        int val = m_data.data().fileSize & 0xffff;
        if (val == 0) {
            val = groups.getSize();
        }
        return val;
    }

    @Override
    public int getStartAddress() {
        return m_data.data().loadAddr & 0xffff;
    }

    @Override
    public int getEndAddress() {
        return m_data.data().endAddr & 0xffff;
    }

    @Override
    public int getExecuteAddress() {
        return m_data.data().execAddr & 0xffff;
    }

    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = (short) val;
    }

    @Override
    public void setEndAddress(int val) {
        m_data.data().endAddr = (short) (val & 0xffff);
    }

    @Override
    public void setExecuteAddress(int val) {
        m_data.data().execAddr = (short) (val & 0xffff);
    }

    @Override
    public boolean hasAddress() {
        return true;
    }

    @Override
    public boolean hasExecuteAddress() {
        return hasAddress();
    }

    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        // fp
        m_data.data().startGroup = (byte) (val & 0xff);
    }

    @Override
    public int getStartGroup(int fileunit_num) {
        // fp
        return m_data.data().startGroup & 0xff;
    }

    @Override
    public boolean needCheckEofCode() {
        // アスキー形式のときはEOFコードでサイズを計算
        return (getFileType1() & (FILETYPE_FP_RANDOM | FILETYPE_FP_ASCII)) == FILETYPE_FP_ASCII;
    }

    @Override
    public int recalcFileSizeOnSave(InputStream istream, int file_size) {
        // ファイルの最終が終端記号で終わっているかを調べる
        // ただし、ファイルサイズがクラスタサイズと合うなら終端記号は不要
        if ((file_size % (basic.getSectorSize() * basic.getSectorsPerGroup())) != 0) {
            file_size = checkEofCode(istream, file_size) - 1;
        }
        return file_size;
    }

    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    @Override
    public DirectoryFp getData() {
        return m_data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val);
    }

    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir());
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    //
    // ダイアログ用
    //

    @Override
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int ftype = attr.getFileType();
        if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            // バイナリ
            if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                // BASICならアドレスは固定
                attr.setStartAddress(0);
                attr.setEndAddress(getFileSize());
                attr.setExecuteAddress(0);
            }
        } else {
            // アスキー、ランダムアクセス
            // アドレスは固定
            attr.setStartAddress(0);
            attr.setEndAddress(0);
            attr.setExecuteAddress(0);
        }
        return true;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TYPE", m_data.data().type);
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXT", m_data.data().ext, m_data.data().ext.length);
        vals.add("TERM", m_data.data().term);
        vals.add("UNKNOWN", m_data.data().unknown, m_data.data().unknown.length);
        vals.add("LOAD_ADDR", m_data.data().loadAddr);
        vals.add("END_ADDR", m_data.data().endAddr);
        vals.add("EXEC_ADDR", m_data.data().execAddr);
        vals.add("FILE_SIZE", m_data.data().fileSize);
        vals.add("START_GROUP", m_data.data().startGroup);
        vals.add("ATTR", m_data.data().attr);
        vals.add("RESERVED", m_data.data().reserved, m_data.data().reserved.length);
    }
}
