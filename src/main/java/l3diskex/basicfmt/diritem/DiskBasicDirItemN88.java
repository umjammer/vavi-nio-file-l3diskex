///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryN88;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ENCRYPTED_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;


/**
 ディレクトリ１アイテム N88-BASIC

 @li m_external_attr ランダムアクセスファイルの時 1
 */
public class DiskBasicDirItemN88 extends DiskBasicDirItemFAT8<DirectoryN88> {

    // N88-BASIC attribute names
    public static final String[] G_TYPE_NAME_N88_1 = {
            "Ascii",
            "Binary",
            "Machine",
            "Ascii(Random Access)",
    };

    // N88-BASIC

    public static final int TYPE_NAME_N88_ASCII = 0;
    public static final int TYPE_NAME_N88_BINARY = 1;
    public static final int TYPE_NAME_N88_MACHINE = 2;
    public static final int TYPE_NAME_N88_RANDOM = 3;

    public static final int FILETYPE_N88_ASCII = 0x00;
    public static final int FILETYPE_N88_BINARY = 0x80;
    public static final int FILETYPE_N88_MACHINE = 0x01;

    // N88-BASIC attribute names 2
    public static final String[] G_TYPE_NAME_N88_2 = {
            "Write Protected",
            "Read After Write",
            "Encrypted",
    };

    public static final int TYPE_NAME_N88_READ_ONLY = 0;
    public static final int TYPE_NAME_N88_READ_WRITE = 1;
    public static final int TYPE_NAME_N88_ENCRYPTED = 2;

    public static final int DATATYPE_MASK_N88_READ_ONLY = 0x10;
    public static final int DATATYPE_MASK_N88_READ_WRITE = 0x40;
    public static final int DATATYPE_MASK_N88_ENCRYPTED = 0x20;

    /** ディレクトリデータ */
    protected DiskBasicDirData<DirectoryN88> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemN88(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryN88.class);
    }

    public DiskBasicDirItemN88(DiskBasic basic, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP) {
        super(basic, nSector, nSecpos, nData, dataP);

        m_data.attach(DirectoryN88.class, nData, dataP);
    }

    public DiskBasicDirItemN88(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP, SectorParam nNext, boolean[] nUnuse) throws IOException {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, dataP, nNext, nUnuse);

        // n88
        m_data.attach(DirectoryN88.class, nData, dataP);
//Debug.printStackTrace(new Exception());

        used(checkUsed(nUnuse[0]));
        nUnuse[0] = (nUnuse[0] || (m_data.data().name[0] == (byte) 0xff));
//Debug.println("1): " + isUsed() + "\n" + StringUtil.getDump(m_data.getRawData(), DirectoryN88.SIZE));

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    /**
     * アイテムへのポインタを設定
     *
     * @param num    通し番号
     * @param gItem  トラック番号などのデータ
     * @param sector セクタ
     * @param secPos セクタ内のディレクトリエントリの位置
     * @param data   ディレクトリアイテム
     * @param next   [out] 次のセクタ
     * @see DiskBasicType#checkDirectory
     */
    @Override
    public void setDataPtr(int num, DiskBasicGroupItem gItem, DiskImageSector sector, int secPos, byte[] data, int dataP, SectorParam next) throws IOException {
        super.setDataPtr(num, gItem, sector, secPos, data, dataP, next);

        m_data.attach(DirectoryN88.class, data, dataP);
//Debug.println("2)\n" + StringUtil.getDump(m_data.getRawData(), DirectoryN88.SIZE));
    }

    /** ファイル名を格納する位置を返す */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // N88
        if (num == 0) {
            size[0] = len[0] = m_data.data().name.length;
            return m_data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /** 拡張子を格納する位置を返す */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = m_data.data().ext.length;
        return m_data.data().ext;
    }

    @Override
    public int getFileType1() {
        return m_data.data().type;
    }

    @Override
    protected void setFileType1(int val) {
        m_data.data().type = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return (!unuse && m_data.data().name[0] != 0x00 && m_data.data().name[0] != (byte) 0xff);
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        if (m_data.data().name[0] == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        // 属性に不正な値がある (0x0c = 00001100b, i.e., bits 2 and 3)
        if ((getFileType1() & 0x0c) != 0) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        m_data.fill(basic.diskBasicParam.getDeleteCode(), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int ftype = fileType.getType();
        if (ftype == -1) return;

        // n88
        int t1 = convFileType1(ftype);

        externalAttr = ((ftype & FILE_TYPE_RANDOM_MASK.getValue()) != 0 ? 1 : 0);

        if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            t1 |= DATATYPE_MASK_N88_READ_ONLY;
        }
        if ((ftype & FILE_TYPE_ENCRYPTED_MASK.getValue()) != 0) {
            t1 |= DATATYPE_MASK_N88_ENCRYPTED;
        }
        if ((ftype & FILE_TYPE_READWRITE_MASK.getValue()) != 0) {
            t1 |= DATATYPE_MASK_N88_READ_WRITE;
        }
        setFileType1(t1);
    }

    public int convFileType1(int fileType) {
        int t1 = 0;
        if ((fileType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
            t1 = FILETYPE_N88_MACHINE;
        } else if ((fileType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            t1 = FILETYPE_N88_BINARY;
        } else {
            t1 = FILETYPE_N88_ASCII;
        }
        return t1;
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        if ((t1 & FILETYPE_N88_MACHINE) != 0) {
            val = FILE_TYPE_MACHINE_MASK.getValue();     // machine
            val |= FILE_TYPE_BINARY_MASK.getValue();     // binary
        } else {
            val = FILE_TYPE_BASIC_MASK.getValue();       // basic
            if ((t1 & FILETYPE_N88_BINARY) != 0) {
                val |= FILE_TYPE_BINARY_MASK.getValue(); // binary
            } else {
                val |= FILE_TYPE_ASCII_MASK.getValue();  // ascii
            }
        }
        if ((t1 & DATATYPE_MASK_N88_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((t1 & DATATYPE_MASK_N88_ENCRYPTED) != 0) {
            val |= FILE_TYPE_ENCRYPTED_MASK.getValue();
        }
        if ((t1 & DATATYPE_MASK_N88_READ_WRITE) != 0) {
            val |= FILE_TYPE_READWRITE_MASK.getValue();
        }
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    @Override
    public String getFileAttrStr() {
        // n88
        String attr = G_TYPE_NAME_N88_1[convFileType1Pos(getFileType1())];
        //
        int t = getFileType1();
        if ((t & DATATYPE_MASK_N88_READ_ONLY) != 0) {
            attr += ", ";
            attr += G_TYPE_NAME_N88_2[TYPE_NAME_N88_READ_ONLY];
        }
        if ((t & DATATYPE_MASK_N88_READ_WRITE) != 0) {
            attr += ", ";
            attr += G_TYPE_NAME_N88_2[TYPE_NAME_N88_READ_WRITE];
        }
        if ((t & DATATYPE_MASK_N88_ENCRYPTED) != 0) {
            attr += ", ";
            attr += G_TYPE_NAME_N88_2[TYPE_NAME_N88_ENCRYPTED];
        }
        return attr;
    }

    @Override
    public void setFileSize(int val) {
        // ファイルサイズはセクタサイズ境界で丸める
        int sectorSize = basic.getSectorSize();
        groups.setSize((((val - 1) / sectorSize) + 1) * sectorSize);
    }

    // C++ method: TakeAddressesInFile
    @Override
    protected void takeAddressesInFile() {
        if (groups.size() == 0 || (getFileType1() & FILETYPE_N88_MACHINE) == 0) {
            m_start_address = -1;
            m_end_address = -1;
            m_exec_address = -1;
            return;
        }

        DiskBasicGroupItem item = groups.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;

        boolean isBigEndian = basic.isBigEndian();

        // 開始アドレス
        m_start_address = sector.get16(0, isBigEndian);
        // 終了アドレス
        m_end_address = sector.get16(2, isBigEndian);
    }

    @Override
    public void setStartGroup(int fileunitNum, int val, int size) {
        // n88
        m_data.data().startGroup = (byte) (val & 0xff);
    }

    @Override
    public int getStartGroup(int fileunitNum) {
        // n88
        return m_data.data().startGroup & 0xff;
    }

    @Override
    public boolean hasEndMark() {
        boolean val;
        val = ((m_data.data().name[0] & 0xff) == basic.diskBasicParam.getGroupUnusedCode());
        return val;
    }

    @Override
    public void setEndMark(DiskBasicDirItem nextItem) {
        if (nextItem == null) return;

        if (hasEndMark())
            ((DirectoryN88) nextItem.getData()).name[0] = (byte) basic.diskBasicParam.getGroupUnusedCode();
    }

    @Override
    public boolean needCheckEofCode() {
        // Asc形式のときはEOFコードが必要
        return (((getFileType1() & (FILETYPE_N88_MACHINE | FILETYPE_N88_BINARY)) == 0) && (externalAttr == 0));
    }

    @Override
    public int recalcFileSizeOnSave(InputStream istream, int fileSize) {
        if (needCheckEofCode()) {
            // ファイルの最終が終端記号で終わっているかを調べる
            fileSize = checkEofCode(istream, fileSize);
            // ただし、ファイルサイズがセクタサイズと合うなら終端記号は不要
            if ((fileSize % basic.getSectorSize()) == 1) {
                // 残り１バイトは終端コードのみなので不要
                fileSize--;
            }
        }
        return fileSize;
    }

    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    @Override
    public DirectoryN88 getData() {
        return m_data.data();
    }

    @Override
    public byte[] getRawData() {
        return m_data.getRawData();
    }

    @Override
    public boolean copyData(byte[] val) { // directory_t replaced by Object
        return m_data.copy(val, getDataSize());
    }

    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getFillCodeOnDir(), getDataSize());

        m_data.data().type = 0;
    }

    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = 0;
        // 拡張子で属性を設定する
        MyAttribute sa = findUpperCase(basic.diskBasicParam.getAttributesByExtension(), Utils.getExt(filename));
        if (sa != null) {
            ftype = sa.getType();
        } else {
            ftype = FILE_TYPE_ASCII_MASK.getValue();
        }
        return ftype;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        // 拡張子で属性を設定する
        MyAttribute sa = findUpperCase(basic.diskBasicParam.getAttributesByExtension(), Utils.getExt(filename));
        if (sa != null) {
            t1 = convFileType1(sa.getType());
        } else {
            t1 = TYPE_NAME_N88_ASCII;
        }
        return t1;
    }

    // 属性からリストの位置を返す(プロパティダイアログ用)
    public int convFileType1Pos(int t1) {
        int val = 0;
        if ((t1 & FILETYPE_N88_MACHINE) != 0) {
            val = TYPE_NAME_N88_MACHINE;
        } else {
            if ((t1 & FILETYPE_N88_BINARY) != 0) {
                val = TYPE_NAME_N88_BINARY;
            } else if (externalAttr != 0) {
                val = TYPE_NAME_N88_RANDOM;
            } else {
                val = TYPE_NAME_N88_ASCII;
            }
        }
        return val;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", m_data.data().name, m_data.data().name.length);
        vals.add("EXT", m_data.data().ext, m_data.data().ext.length);
        vals.add("TYPE", m_data.data().type);
        vals.add("START_GROUP", m_data.data().startGroup);
        vals.add("RESERVED", m_data.data().reserved, m_data.data().reserved.length);
    }
}
