package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryN88;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ENCRYPTED_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;


// DiskBasicDirItemN88.java
public class DiskBasicDirItemN88 extends DiskBasicDirItemFAT8<DirectoryN88> {

    String[] G_TYPE_NAME_N88_1 = {
            "Ascii",
            "Binary",
            "Machine",
            "Ascii(Random Access)",
    };

    int TYPE_NAME_N88_ASCII = 0;
    int TYPE_NAME_N88_BINARY = 1;
    int TYPE_NAME_N88_MACHINE = 2;
    int TYPE_NAME_N88_RANDOM = 3;

    int FILETYPE_N88_ASCII = 0x00;
    int FILETYPE_N88_BINARY = 0x80;
    int FILETYPE_N88_MACHINE = 0x01;

    // N88-BASIC attribute names 2
    String[] G_TYPE_NAME_N88_2 = {
            "Write Protected",
            "Read After Write", // Corresponds to DATATYPE_MASK_N88_READ_WRITE
            "Encrypted",
    };

    int TYPE_NAME_N88_READ_ONLY = 0;
    int TYPE_NAME_N88_READ_WRITE = 1;
    int TYPE_NAME_N88_ENCRYPTED = 2;

    int DATATYPE_MASK_N88_READ_ONLY = 0x10;
    int DATATYPE_MASK_N88_READ_WRITE = 0x40;
    int DATATYPE_MASK_N88_ENCRYPTED = 0x20;

    public interface DialogIDs {
        int IDC_RADIO_TYPE1 = 51;
        int IDC_CHECK_READONLY = 52;
        int IDC_CHECK_READWRITE = 53;
        int IDC_CHECK_ENCRYPT = 54;
        int IDC_RADIO_TYPE2 = 55;
    }

    private final DiskBasicDirData<DirectoryN88> mData = new DiskBasicDirData<>();

    public DiskBasicDirItemN88(DiskBasic basic) {
        super(basic);
        mData.alloc(DirectoryN88.class);
    }

    public DiskBasicDirItemN88(DiskBasic basic, DiskImageSector nSector, int nSecpos, byte[] nData) {
        super(basic, nSector, nSecpos, nData);
        mData.attach(nData);
    }

    public DiskBasicDirItemN88(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, SectorParam nNext, boolean[] nUnuse) throws IOException {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, nNext, nUnuse);
        // n88
        mData.attach(nData);

        boolean unuse = nUnuse[0];
        used(checkUsed(unuse));
        nUnuse[0] = (unuse || (mData.data().name[0] == (byte)0xff)); // C++ byte is signed

        // ファイルサイズとグループ数を計算
        calcFileSize();
    }

    // C++ method: SetDataPtr
    @Override
    public void setDataPtr(int num, DiskBasicGroupItem gItem, DiskImageSector sector, int secPos, byte[] data, SectorParam next) throws IOException {
        super.setDataPtr(num, gItem, sector, secPos, data, next);
        mData.attach(data);
    }

    // C++ method: GetFileNamePos
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // N88
        if (num == 0) {
            size[0] = len[0] = mData.data().name.length;
            return mData.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = mData.data().ext.length;
        return mData.data().ext;
    }

    @Override
    public int getFileType1() {
        return mData.data().type;
    }

    @Override
    protected void setFileType1(int val) {
        mData.data().type = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        // C++ wxUint8 is unsigned, so (byte)0xff is -1.
        return (!unuse && mData.data().name[0] != 0x00 && mData.data().name[0] != (byte)0xff);
    }

    @Override
    public boolean check(boolean[] last) {
        if (!mData.isValid()) return false;

        boolean valid = true;
        if (mData.data().name[0] == (byte)0xff) {
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
        mData.fill(basic.diskBasicParam.getDeleteCode(), 1);
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
            val = FILE_TYPE_MACHINE_MASK.getValue();		// machine
            val |= FILE_TYPE_BINARY_MASK.getValue();		// binary
        } else {
            val = FILE_TYPE_BASIC_MASK.getValue();			// basic
            if ((t1 & FILETYPE_N88_BINARY) != 0) {
                val |= FILE_TYPE_BINARY_MASK.getValue();	// binary
            } else {
                val |= FILE_TYPE_ASCII_MASK.getValue();	// ascii
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
        if (groups.count() == 0 || (getFileType1() & FILETYPE_N88_MACHINE) == 0) {
            m_start_address = -1;
            m_end_address = -1;
            m_exec_address = -1;
            return;
        }

        DiskBasicGroupItem item = groups.item(0);
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
        mData.data().startGroup = (byte) (val & 0xff);
    }

    @Override
    public int getStartGroup(int fileunitNum) {
        // n88
        return mData.data().startGroup;
    }

    @Override
    public boolean hasEndMark() {
        boolean val;
        val = ((mData.data().name[0] & 0xFF) == basic.diskBasicParam.getGroupUnusedCode());
        return val;
    }

    @Override
    public void setEndMark(DiskBasicDirItem nextItem) {
        if (nextItem == null) return;

        if (hasEndMark()) ((DirectoryN88) nextItem.getData()).name[0] = (byte)basic.diskBasicParam.getGroupUnusedCode();
    }

    @Override
    public boolean needCheckEofCode() {
        // Asc形式のときはEOFコードが必要
        return (((getFileType1() & (FILETYPE_N88_MACHINE | FILETYPE_N88_BINARY)) == 0) && (externalAttr == 0));
    }

    @Override
    public int recalcFileSizeOnSave(InputStream istream, int fileSize) { // wxInputStream replaced by Object
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
        return mData.getDataSize();
    }

    @Override
    public DirectoryN88 getData() {
        return mData.data();
    }

    @Override
    public boolean copyData(DirectoryN88 val) { // directory_t replaced by Object
        return mData.copy(val, getDataSize());
    }

    @Override
    public void clearData() {
        mData.fill(basic.diskBasicParam.getFillCodeOnDir(), getDataSize());
        mData.data().type = 0;
    }

    @Override
    public boolean hasExecuteAddress() { return false; }

    @Override
    public int convFileTypeFromFileName(String filename) {
        int ftype = 0;
        // 拡張子で属性を設定する
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(Utils.getExt(filename));
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
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(Utils.getExt(filename));
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
        vals.add("self", mData.isSelf());
        vals.add("NAME", mData.data().name, mData.data().name.length);
        vals.add("EXT", mData.data().ext, mData.data().ext.length);
        vals.add("TYPE", mData.data().type);
        vals.add("START_GROUP", mData.data().startGroup);
        vals.add("RESERVED", mData.data().reserved, mData.data().reserved.length);
    }
}
