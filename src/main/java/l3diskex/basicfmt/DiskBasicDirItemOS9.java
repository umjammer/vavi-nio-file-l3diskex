package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.ResourceBundle;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryOs9;
import l3diskex.basicfmt.BasicCommon.DirectoryOs9Fd;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicCommon.Os9Cdate;
import l3diskex.basicfmt.BasicCommon.Os9Date;
import l3diskex.basicfmt.BasicCommon.Os9Lsn;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItemOS9.DiskBasicDirItemOS9FD.DiskBasicDirItemOS9Util;
import l3diskex.basicfmt.DiskBasicDirItemOS9.DiskBasicDirItemOS9FD.EnFileTypeMaskOs9;
import l3diskex.basicfmt.DiskBasicDirItemOS9.DiskBasicDirItemOS9FD.EnTypeNameOs9;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_NONSHARE_MASK;


/**
 * ディレクトリ１アイテム OS-9
 */
public class DiskBasicDirItemOS9 extends DiskBasicDirItem<DirectoryOs9> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * OS-9 File Descriptorエリアのポインタ
     */
    public static class DiskBasicDirItemOS9FD {

        static class DiskBasicDirItemOS9Util {
            // OS-9属性名
            public static final String[] G_TYPE_NAME_OS9 = {
                    "<DIR>", // wxTRANSLATE("<DIR>")
                    "Non-sharable", // wxTRANSLATE("Non-sharable")
            };
            public static final char[] G_TYPE_NAME_OS9_2 = {
                    'X','W','R','x','w','r',0
            };
            public static final String[] G_TYPE_NAME_OS9_2L = {
                    "Execute", // wxTRANSLATE("Execute")
                    "Write", // wxTRANSLATE("Write")
                    "Read", // wxTRANSLATE("Read")
            };
        }

        enum EnTypeNameOs9 {
            TYPE_NAME_OS9_DIRECTORY(0),
            TYPE_NAME_OS9_NONSHARE(1);

            private final int value;
            EnTypeNameOs9(int value) { this.value = value; }
            public int getValue() { return value; }
        }

        public enum EnFileTypeMaskOs9 {
            FILETYPE_MASK_OS9_DIRECTORY(0x80),
            FILETYPE_MASK_OS9_NONSHARE(0x40),
            FILETYPE_MASK_OS9_PUBLIC_EXEC(0x20),
            FILETYPE_MASK_OS9_PUBLIC_WRITE(0x10),
            FILETYPE_MASK_OS9_PUBLIC_READ(0x08),
            FILETYPE_MASK_OS9_USER_EXEC(0x04),
            FILETYPE_MASK_OS9_USER_WRITE(0x02),
            FILETYPE_MASK_OS9_USER_READ(0x01);

            private final int value;
            EnFileTypeMaskOs9(int value) { this.value = value; }
            public int getValue() { return value; }
        }

        private DiskBasic basic;
        private DiskImageSector sector;
        private DirectoryOs9Fd p_fd;
        private int m_mylsn; // int
        private boolean m_fd_ownmake;
        private final ZeroData zero_data = new ZeroData(); // union replacement

        private static class ZeroData {
            public Os9Date date = new Os9Date();
            public Os9Cdate cdate = new Os9Cdate();
        }

        private DiskBasicDirItemOS9FD(final DiskBasicDirItemOS9FD src) {
            // Private copy constructor
        }

        public DiskBasicDirItemOS9FD() {
            basic = null;
            sector = null;
            p_fd = null;
            m_mylsn = -1;
            m_fd_ownmake = false;
            // memset(&zero_data, 0, sizeof(zero_data)) is effectively done by default construction in Java
        }

        // No direct operator= in Java, implement as Dup
        // public DiskBasicDirItemOS9FD operator=(final DiskBasicDirItemOS9FD src) { this.Dup(src); return this; }
        /**
         * 複製
         */
        public void Dup(final DiskBasicDirItemOS9FD src) {
            sector = src.sector;
            m_mylsn = src.m_mylsn;
            if (src.m_fd_ownmake) {
                p_fd = new DirectoryOs9Fd();
                // Assuming a utility for memcpy
                System.arraycopy(src.p_fd, 0, p_fd, 0, 1); // Simplified copy
                // memcpy(&fd, src.fd, sizeof(directory_os9_fd_t));
            } else {
                p_fd = src.p_fd;
            }
            m_fd_ownmake = src.m_fd_ownmake;
        }

        /**
         * ポインタをセット
         */
        public void Set(DiskBasic n_basic, DiskImageSector n_sector, int n_mylsn, DirectoryOs9Fd n_fd) {
            basic = n_basic;
            sector = n_sector;
            m_mylsn = n_mylsn;
            if (m_fd_ownmake) {
                p_fd = null; // simulate delete p_fd;
            }
            p_fd = n_fd;
            m_fd_ownmake = false;
        }

        /**
         * FDのメモリ確保
         */
        public void Alloc() {
            if (m_fd_ownmake) {
                p_fd = null; // simulate delete p_fd;
            }
            p_fd = new DirectoryOs9Fd();
            m_fd_ownmake = true;
            // memset(p_fd, 0, sizeof(directory_os9_fd_t)); - Done by Java new
        }

        /**
         * FDをクリア
         */
        public void Clear() {
            if (sector != null) {
                // sector->Fill(0)
            } else if (p_fd != null) {
                // memset(p_fd, 0, sizeof(directory_os9_fd_t)) - Reinitialize
                p_fd = new DirectoryOs9Fd();
            }
        }

        /**
         * 有効か
         */
        public boolean IsValid() {
            return (p_fd != null);
        }

        /**
         * FDへのポインタを返す
         */
        public DirectoryOs9Fd GetFD() {
            return p_fd;
        }

        /**
         * 自分のLSNを返す
         */
        public int GetMyLSN() {
            return m_mylsn;
        }

        /**
         * 自分のLSNを設定
         */
        public void SetMyLSN(int val) {
            m_mylsn = val;
        }

        /**
         * 属性を返す
         */
        public short GetATT() {
            return p_fd != null ? p_fd.fdAtt : 0;
        }

        /**
         * 属性をセット
         */
        public void SetATT(short val) {
            if (p_fd != null) {
                p_fd.fdAtt = (byte) val;
            }
        }

        /**
         * ユーザIDを返す
         */
        public int GetOWN() {
            // wxUINT16_SWAP_ON_LE(p_fd->FD_OWN) - Assumed utility method
            return p_fd != null ? p_fd.fdOwn : 0;
        }

        /**
         * ユーザIDをセット
         */
        public void SetOWN(int val) {
            // p_fd->FD_OWN = wxUINT16_SWAP_ON_LE(val);
            if (p_fd != null) {
                p_fd.fdOwn = (short) val;
            }
        }

        /**
         * セグメントのLSNを返す
         */
        public int GetLSN(int idx) {
            // GET_OS9_LSN(p_fd->FD_SEG[idx].LSN) - Assumed utility method
            return p_fd != null ? p_fd.fdSeg[idx].lsn.getOs9Lsn() : 0;
        }

        /**
         * セグメントのセクタ数を返す
         */
        public int GetSIZ(int idx) {
            // wxUINT16_SWAP_ON_LE(p_fd->FD_SEG[idx].SIZ) - Assumed utility method
            return p_fd != null ? p_fd.fdSeg[idx].siz : 0;
        }

        /**
         * セグメントにLSNを設定
         */
        public void SetLSN(int idx, int val) {
            // SET_OS9_LSN(p_fd->FD_SEG[idx].LSN, val); - Assumed utility method
            if (p_fd != null) {
                Os9Lsn x = new Os9Lsn();
                x.setOs9Lsn(val);
                p_fd.fdSeg[idx].lsn = x;
            }
        }

        /**
         * セグメントにセクタ数を設定
         */
        public void SetSIZ(int idx, int val) {
            // p_fd->FD_SEG[idx].SIZ = wxUINT16_SWAP_ON_LE(val); - Assumed utility method
            if (p_fd != null) {
                p_fd.fdSeg[idx].siz = (short) val;
            }
        }

        /**
         * ファイルサイズを返す
         */
        public int GetSIZ() {
            // int_SWAP_ON_LE(p_fd->FD_SIZ) - Assumed utility method
            return p_fd != null ? p_fd.fdSiz : 0;
        }

        /**
         * ファイルサイズを設定
         */
        public void SetSIZ(int val) {
            // p_fd->FD_SIZ = int_SWAP_ON_LE(val); - Assumed utility method
            if (p_fd != null) {
                p_fd.fdSiz = val;
            }
        }

        /**
         * リンク数を返す
         */
        public short GetLNK() {
            return p_fd != null ? p_fd.fdLnk : 0;
        }

        /**
         * リンク数を設定
         */
        public void SetLNK(short val) {
            if (p_fd != null) {
                p_fd.fdLnk = (byte) val;
            }
        }

        /**
         * 更新日付を返す
         */
        public Os9Date GetDAT() {
            return p_fd != null ? p_fd.fdDat : zero_data.date;
        }

        /**
         * 更新日付をセット
         */
        public void SetDAT(final Os9Date val) {
            if (p_fd != null) {
                p_fd.fdDat = val;
            }
        }

        /**
         * 更新日付をセット
         */
        public void SetDAT(final Os9Cdate val) {
            if (p_fd != null) {
                p_fd.fdDat.yy = val.yy;
                p_fd.fdDat.mm = val.mm;
                p_fd.fdDat.dd = val.dd;
            }
        }

        /**
         * 作成日付を返す
         */
        public Os9Cdate GetDCR() {
            return p_fd != null ? p_fd.fdDcr : zero_data.cdate;
        }

        /**
         * 作成日付をセット
         */
        public void SetDCR(final Os9Cdate val) {
            if (p_fd != null) {
                p_fd.fdDcr = val;
            }
        }

        /**
         * 更新にする
         */
        public void SetModify() {
            // No logic in C++, so no logic here.
        }
    }

    //////////////////////////////////////////////////////////////////////

//#ifdef COPYABLE_DIRITEM

    /**
     * 複製
     */
    @Override
    public void dup(final DiskBasicDirItem src) {
        super.dup(src);
        // Cast to the derived type
        if (src instanceof DiskBasicDirItemOS9) {
            DiskBasicDirItemOS9 psrc = (DiskBasicDirItemOS9) src;
            fd.Dup(psrc.fd);
        }
    }

//#endif

    /**
     * ディレクトリデータ
     */
    private final DiskBasicDirData<DirectoryOs9> m_data = new DiskBasicDirData<>();

    /**
     * File Descriptorエリアのポインタ
     */
    private final DiskBasicDirItemOS9FD fd = new DiskBasicDirItemOS9FD();

    /**
     * ユーザID(プロパティダイアログ用)
     */
    private short m_owner_id; // byte
    /**
     * グループID(プロパティダイアログ用)
     */
    private short m_group_id; // byte

    /**
     * ファイル名を格納する位置を返す
     */
    protected byte[] GetFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = m_data.data().deNam.length;
            return m_data.data().deNam;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /**
     * 属性１を返す
     */
    @Override
    protected int getFileType1() {
        return fd.GetATT();
    }

    /**
     * 属性１のセット
     */
    @Override
    protected void setFileType1(int val) {
        fd.SetATT((short) (val & 0xff));
    }

    /**
     * 使用しているアイテムか
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (m_data.data().deNam[0] != 0);
    }

    /**
     * ユーザIDを返す
     */
    public int GetUserID() {
        return fd.GetOWN();
    }

    /**
     * ユーザIDのセット
     */
    public void setUserID(int val) {
        fd.SetOWN((short) (val & 0xffff));
    }

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     */
    public int getFileType1Pos() {
        return getFileType1();
    }

    /**
     * インポート時ダイアログ表示前にファイルの属性を設定
     */
    public void setFileTypeForAttrDialog(int show_flags, final String name, int[] file_type_1) {
        // Assuming INTNAME_NEW_FILE is a defined constant
        final int INTNAME_NEW_FILE = 0x01;
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時
            file_type_1[0] = convOriginalTypeFromFileName(name);
        }
    }

    /**
     * ファイル名を設定
     */
    protected void setNativeName(byte[] filename, int size, int length) {
        int[] s = new int[1];
        int[] l = new int[1];
        byte[] n = GetFileNamePos(0, s, l);
        encodeString(n, l[0], new String(filename, 0, length), length);
    }

    /**
     * ファイル名を得る
     */
    protected void GetNativeFileName(byte[] name, int[] nlen, byte[] ext, int[] elen) {
        super.getNativeFileName(name, nlen, ext, elen);

        // 文字列の最後はMSBがセットされているのでクリア
        nlen[0] = decodeString(name, nlen[0], name, nlen[0]);
    }

    /**
     * 日付を変換
     */
    public LocalDate convDateToTm(final Os9Cdate date) {
        return LocalDate.of(
                (date.yy % 100) +
                        (date.yy % 100) < 80 ? 100 : 0,
                date.mm - 1,
                date.dd);
    }

    /**
     * 時間を変換
     */
    public LocalTime convTimeToTm(final Os9Date time) {
        return LocalTime.of(
                time.hh,
                time.mi,
                0);
    }

    /**
     * 日付に変換
     */
    public void convTmToDate(final LocalDateTime tm, Os9Cdate date) {
        date.yy = (byte) (tm.getYear() % 100);
        date.mm = (byte) (tm.getMonth().ordinal() + 1);
        date.dd = (byte) tm.getDayOfMonth();
    }

    /**
     * 時間に変換
     */
    public void convTmToTime(final LocalDateTime tm, Os9Date time) {
        time.hh = (byte) tm.getHour();
        time.mi = (byte) tm.getMinute();
    }

    // Constructor 1
    public DiskBasicDirItemOS9(DiskBasic basic) {
        super(basic);
        m_data.alloc(DirectoryOs9.class); // Assuming Alloc() exists on DiskBasicDirData
        fd.Alloc();
        m_owner_id = 0;
        m_group_id = 0;
    }

    // Constructor 2
    public DiskBasicDirItemOS9(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
        m_data.attach(n_data);
        m_owner_id = 0;
        m_group_id = 0;
    }

    // Constructor 3
    public DiskBasicDirItemOS9(DiskBasic basic, int n_num, final DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, final SectorParam n_next, boolean[] n_unuse) {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
        m_data.attach(n_data);
        m_owner_id = 0;
        m_group_id = 0;

        used(checkUsed(n_unuse[0])); // Assuming Used and CheckUsed are defined in superclass

        // FDセクタへのポインタをセット
        if (isUsed()) {
            // int lsn = GET_OS9_LSN(((directory_os9_t *)n_data)->DE_LSN);
            int lsn = m_data.data().deLsn.getOs9Lsn();
            if (lsn != 0) {
                // DiskImageSector *sector = basic->GetSectorFromGroup(lsn);
                DiskImageSector sector = null; // Placeholder for basic.GetSectorFromGroup(lsn)
                if (sector != null) {
                    // fd.Set(basic, sector, lsn, (directory_os9_fd_t *)sector->GetSectorBuffer());
                    fd.Set(basic, sector, lsn, null); // Placeholder
                }
            }
        }

        // CalcFileSize(); // Placeholder
        int fileSize = 0; // Placeholder for CalcFileSize()

        // カレント or 親ディレクトリはツリーに表示しない
        // String name = GetFileNamePlainStr(); // Placeholder for GetFileNamePlainStr()
        String name = null; // Placeholder
        // VisibleOnTree(!(IsDirectory() && (name == "." || name == ".."))); // Placeholder
    }

    /**
     * アイテムへのポインタを設定
     */
    public void SetDataPtr(int n_num, final DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, final SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, n_next);

        m_data.attach(n_data);
    }

    // ... (Other methods like CheckUsed, GetUserID, SetUserID, etc., are in the header but implemented in the cpp)

    /**
     * ディレクトリアイテムのチェック
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        // if (m_data.data()->DE_Reserved != 0) return false;

        return true;
    }

    /**
     * 削除
     */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        // m_data.data()->DE_NAM[0] = basic->GetDeleteCode();
        m_data.data().deNam[0] = 0; // Placeholder for basic.GetDeleteCode()
        used(false);
        return true;
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(final DiskBasicFileType file_type) {

        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int user_id = -1;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            t1 = file_type.getOrigin(0);
            user_id = file_type.getOrigin(1);
        } else {
            if ((ftype & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_DIRECTORY.getValue();
            }
            if ((ftype & FILE_TYPE_NONSHARE_MASK.getValue()) != 0) {
                t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_NONSHARE.getValue();
            }
            // t1 |= ((ftype & FILETYPE_OS9_PERMISSION_MASK) >> FILETYPE_OS9_PERMISSION_POS);
            if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_PUBLIC_EXEC.getValue();
                t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_USER_EXEC.getValue();
            }
            t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_PUBLIC_READ.getValue();
            t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_USER_WRITE.getValue();
            t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_USER_READ.getValue();
        }
        setFileType1(t1);
        if (user_id >= 0) setUserID(user_id);
    }

    /**
     * 属性を返す
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        // Placeholder values for file types
        final int FILE_TYPE_DIRECTORY_MASK = 0x01;
        final int FILE_TYPE_NONSHARE_MASK = 0x02;
        final int FILE_TYPE_BINARY_MASK = 0x04;

        int val = 0;
        int t1 = getFileType1();
        if ((t1 & EnFileTypeMaskOs9.FILETYPE_MASK_OS9_DIRECTORY.getValue()) != 0) {
            val |= FILE_TYPE_DIRECTORY_MASK;
        }
        if ((t1 & EnFileTypeMaskOs9.FILETYPE_MASK_OS9_NONSHARE.getValue()) != 0) {
            val |= FILE_TYPE_NONSHARE_MASK;
        }
        if ((t1 & (EnFileTypeMaskOs9.FILETYPE_MASK_OS9_PUBLIC_EXEC.getValue() | EnFileTypeMaskOs9.FILETYPE_MASK_OS9_USER_EXEC.getValue())) != 0) {
            val |= FILE_TYPE_BINARY_MASK;
        }
        // val |= ((t1 << FILETYPE_OS9_PERMISSION_POS) & FILETYPE_OS9_PERMISSION_MASK);
        // Assuming DiskBasicFileType has a constructor like (int formatType, int type, int t1, int userId)
        return new DiskBasicFileType(); // Placeholder
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     */
    @Override
    public String getFileAttrStr() {
        StringBuilder str = new StringBuilder(); // Placeholder
        if (fd.IsValid()) {
            if ((fd.GetATT() & EnFileTypeMaskOs9.FILETYPE_MASK_OS9_DIRECTORY.getValue()) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(DiskBasicDirItemOS9Util.G_TYPE_NAME_OS9[EnTypeNameOs9.TYPE_NAME_OS9_DIRECTORY.getValue()]));
            }
            if ((fd.GetATT() & EnFileTypeMaskOs9.FILETYPE_MASK_OS9_NONSHARE.getValue()) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(DiskBasicDirItemOS9Util.G_TYPE_NAME_OS9[EnTypeNameOs9.TYPE_NAME_OS9_NONSHARE.getValue()]));
            }
            if (!str.isEmpty()) str.append(", ");
            for (int i = 0; i < 6; i++) {
                if ((fd.GetATT() & (0x20 >> i)) != 0) {
                    str.append(DiskBasicDirItemOS9Util.G_TYPE_NAME_OS9_2[i]);
                } else {
                    str.append('-');
                }
            }
        }
        return str.toString();
    }

    /**
     * ファイルサイズをセット
     */
    @Override
    public void setFileSize(int val) {
        fd.SetSIZ(val);
        groups.setSize(val); // Assuming m_groups is a field from superclass
    }

    /**
     * ファイルサイズを返す
     */
    @Override
    public int getFileSize() {
        int size = fd.GetSIZ();
        if (size == 0) size = groups.getSize(); // Assuming m_groups is a field from superclass
        return size;
    }

    /**
     * ファイルサイズとグループ数を計算する
     */
    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!isUsed() || !fd.IsValid()) return; // Assuming IsUsed is defined in superclass

        getUnitGroups(fileunit_num, groups); // Assuming m_groups is a field from superclass
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     */
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) {
        if (!fd.IsValid()) return;

        int calc_groups = 0;
        int calc_file_size = getFileSize();

        for (int i = 0; i < 48; i++) {
            int lsn = fd.GetLSN(i);
            int siz = fd.GetSIZ(i);
            if (siz == 0) {
                break;
            }

            if (i != 0) {
                if (group_items.count() > 0) {
                    DiskBasicGroupItem gitm = group_items.last();
                    gitm.next = lsn;
                }
            }
            for (int n = 0; n < siz; n++) {
                int track_num = 0;
                int side_num = 0;
                int sector_num = 1;
                int next_lsn = (n + 1 != siz ? lsn + n + 1 : 0);
                // basic->CalcNumFromSectorPosForGroup(lsn + n, track_num, side_num, sector_num); // Placeholder
                // group_items.Add(lsn + n, next_lsn, track_num, side_num, sector_num, sector_num); // Placeholder
                calc_groups++;
                // if (calc_groups >= (int)basic.GetFatEndGroup()) { // Placeholder
                if (calc_groups >= 0) { // Placeholder
                    // too large block size
                    break;
                }
            }
        }
        group_items.setNums(calc_groups);
        group_items.setSize(calc_file_size);
        // group_items.SetSizePerGroup(basic.GetSectorSize()); // Placeholder
    }

    /**
     * 作成日付を得る
     */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        if (fd.IsValid()) {
            return convDateToTm(fd.GetDCR());
        } else {
            return LocalDate.of(1970, 0, 1);
        }
    }

    /**
     * 作成日付を文字列で返す
     */
    public String GetFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateDate(tm);
        return Utils.formatYMDStr(tm);
    }

    /**
     * 作成日付をセット
     */
    public void SetFileCreateDate(final LocalDateTime tm) {
        if (fd.IsValid() && tm.getYear() >= 0 && tm.getMonth().ordinal() >= 0) {
            Os9Cdate date = new Os9Cdate();
            convTmToDate(tm, date);
            fd.SetDCR(date);
        }
    }

    /**
     * 更新日付を得る
     */
    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        if (fd.IsValid()) {
            // (final Os9Cdate &)fd.GetDAT() - C++ cast
            Os9Cdate cdate = new Os9Cdate();
            cdate.yy = fd.GetDAT().yy;
            cdate.mm = fd.GetDAT().mm;
            cdate.dd = fd.GetDAT().dd;
            return convDateToTm(cdate);
        } else {
            return LocalDate.of(1970, 0, 1);
        }
    }

    /**
     * 更新時間を得る
     */
    public LocalTime GetFileModifyTime(LocalDateTime tm) {
        if (fd.IsValid()) {
            return convTimeToTm(fd.GetDAT());
        } else {
            return LocalTime.of(1970, 0, 1);
        }
    }

    /**
     * 更新日付を文字列で返す
     */
    @Override
    public String getFileModifyDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileModifyDate(tm);
        return Utils.formatYMDStr(tm);
    }

    /**
     * 更新時間を文字列で返す
     */
    @Override
    public String getFileModifyTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        GetFileModifyTime(tm);
        return Utils.formatHMStr(tm);
    }

    /**
     * 更新日付を設定
     */
    @Override
    public void setFileModifyDate(final LocalDateTime tm) {
        if (fd.IsValid() && tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            Os9Cdate date = new Os9Cdate();
            convTmToDate(tm, date);
            fd.SetDAT(date);
        }
    }

    /**
     * 更新時間を設定
     */
    @Override
    public void setFileModifyTime(final LocalDateTime tm) {
        if (fd.IsValid() && tm.getHour() >= 0 && tm.getMinute() >= -1) {
            Os9Date time = fd.GetDAT();
            convTmToTime(tm, time);
            fd.SetDAT(time);
        }
    }

    /**
     * 日時の表示順序を返す（ダイアログ用）
     */
    public int GetFileDateTimeOrder(int idx) {
        return idx <= 1 ? 1 - idx : idx;
    }

    /**
     * 日時を返す（ファイルリスト用）
     */
    public String GetFileDateTimeStr() {
        return getFileModifyDateTimeStr(); // Assuming GetFileModifyDateTimeStr() is in superclass
    }

    /**
     * 最初のグループ番号を設定
     */
    public void SetStartGroup(int fileunit_num, int val, int size /* = 0 */) {
        // SET_OS9_LSN(m_data.data()->DE_LSN, val);
        Os9Lsn x = new Os9Lsn();
        x.setOs9Lsn(val);
        m_data.data().deLsn = x;
    }

    /**
     * 最初のグループ番号を返す
     */
    public int GetStartGroup(int fileunit_num) {
        // return GET_OS9_LSN(m_data.data()->DE_LSN);
        return m_data.data().deLsn.getOs9Lsn();
    }

    /**
     * 追加のグループ番号をセット FDセクタへのLSNをセット
     */
    @Override
    public void setExtraGroup(int val) {
        // SET_OS9_LSN(m_data.data()->DE_LSN, val);
        Os9Lsn x = new Os9Lsn();
        x.setOs9Lsn(val);
        m_data.data().deLsn = x;
    }

    /**
     * 追加のグループ番号を返す FDセクタへのLSNを返す
     */
    @Override
    public int getExtraGroup() {
        // return GET_OS9_LSN(m_data.data()->DE_LSN);
        return m_data.data().deLsn.getOs9Lsn();
    }

    /**
     * 追加のグループ番号を得る
     */
    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    /**
     * チェイン用のセクタをセット
     */
    @Override
    public void setChainSector(DiskImageSector sector, int lsn, byte[] data, DiskBasicDirItem<DirectoryOs9> pitem) throws IOException {
        DirectoryOs9Fd x = new DirectoryOs9Fd();
        Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), x);
        fd.Set(basic, sector, lsn, x);
        fd.Clear();

        // 属性をコピー
        if (pitem != null) copyItem(pitem);

        // リンク数
        fd.SetLNK((short) 1);
    }

    /**
     * アイテムを削除できるか
     */
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

    /**
     * ファイル名を編集できるか
     */
    @Override
    public boolean isFileNameEditable() {
        // ".", ".."は不可
        return isDeletable();
    }

    /**
     * アイテムをロード・エクスポートできるか
     */
    @Override
    public boolean isLoadable() {
        // ".", ".."は不可
        return isDeletable();
    }

    /**
     * アイテムをコピー(内部でDnD)できるか
     */
    @Override
    public boolean isCopyable() {
        // ".", ".."は不可
        return isDeletable();
    }

    /**
     * アイテムを上書きできるか
     */
    @Override
    public boolean isOverWritable() {
        // ディレクトリは不可
        int t1 = getFileType1();
        // Assuming FILETYPE_MASK_OS9_DIRECTORY is 0x80
        boolean valid = ((t1 & EnFileTypeMaskOs9.FILETYPE_MASK_OS9_DIRECTORY.getValue()) == 0);
        // ".", ".."は不可
        valid &= isDeletable();
        return valid;
    }

    /**
     * ディレクトリアイテムのサイズ
     */
    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    /**
     * アイテムを返す
     */
    @Override
    public DirectoryOs9 getData() {
        return m_data.data();
    }

    /**
     * アイテムをコピー
     */
    @Override
    public boolean copyData(final DirectoryOs9 val) {
        return m_data.copy(val, getDataSize());
    }

    /**
     * ディレクトリをクリア
     */
    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getDeleteCode(), getDataSize(), basic.isDataInverted(), 0); // Placeholder
    }

    /**
     * アイテムをコピー
     */
    @Override
    public void copyItem(final DiskBasicDirItem<DirectoryOs9> src) {
        super.copyItem(src);
        final DiskBasicDirItemOS9 srcOs9 = (DiskBasicDirItemOS9) src;
        final DiskBasicDirItemOS9FD src_fd = srcOs9.getFD();
        fd.SetATT(src_fd.GetATT());
        fd.SetOWN(src_fd.GetOWN());
        fd.SetSIZ(src_fd.GetSIZ());
        fd.SetDAT(src_fd.GetDAT());
        fd.SetDCR(src_fd.GetDCR());
    }

    /**
     * FDセクタのポインタを返す
     */
    public DiskBasicDirItemOS9FD getFD() {
        return fd;
    }

    /**
     * FDセクタのポインタを返す
     */
    public DiskBasicDirItemOS9FD getFDConst() {
        return fd;
    }

    /**
     * アイテムの属するセクタを変更済みにする
     */
    @Override
    public void setModify() {
        super.setModify();
        fd.SetModify();
    }

    /**
     * 文字列の最後のMSBをセット
     */
    public static int encodeString(byte[] dst, int dlen, final String src, int slen) {
        // memset(dst, 0, dlen);
        // int len = dlen > slen ? slen : dlen;
        // memcpy(dst, src, len);
        int len = 0; // Placeholder

        // 文字列の最後にMSBをセット
        for (int i = len - 1; i >= 0; i--) {
            if (dst[i] != 0) {
                dst[i] |= 0x80;
                break;
            }
        }
        return len;
    }

    /**
     * 文字列の最後のMSBをクリア
     */
    public static int decodeString(byte[] dst, int dlen, byte[] src, int slen) {
        int len = dlen > slen ? slen : dlen;

        // 文字列のMSBをクリア
        boolean last = false;
        for (int i = 0; i < len; i++) {
            last = ((src[i] & 0x80) != 0);
            dst[i] = (byte) (src[i] & 0x7f);
            if (last) {
                dlen = i + 1;
                break;
            }
        }
        for (int i = dlen; i < len; i++) {
            dst[i] = 0;
        }
        return dlen;
    }

    /**
     * データをエクスポートする前に必要な処理
     */
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.IsAddExtensionExport()) return true;

        if (!isDirectory()) {
            addExtensionByFileAttr(getFileAttr().getType(), 0x3f, filename, false);
        }
        return true;
    }

    /**
     * データをインポートする前に必要な処理
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.IsDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * ファイル名から属性を決定する
     */
    @Override
    public int convOriginalTypeFromFileName(final String filename) {
        final int FILE_TYPE_BINARY_MASK = 0x04;

        int t1 = 0;
        // -- --R -wr
        t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_PUBLIC_READ.getValue();
        t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_USER_WRITE.getValue();
        t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_USER_READ.getValue();
        // 拡張子で実行属性を付ける
        MyAttribute sa = basic.diskBasicParam.getAttributesByExtension().findUpperCase(Utils.getExt(filename), FILE_TYPE_BINARY_MASK, FILE_TYPE_BINARY_MASK);
        if (sa != null) {
            // 実行属性を付ける
            t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_PUBLIC_EXEC.getValue();
            t1 |= EnFileTypeMaskOs9.FILETYPE_MASK_OS9_USER_EXEC.getValue();
        }

        return t1;
    }

    /**
     * その他の属性値を設定する
     */
    @Override
    public void setOptionalAttr(DiskBasicDirItemAttr attr) {
//        setCDate(attr.getCreateDateTime());
    }

    /**
     * プロパティで表示する内部データを設定
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) throws IOException {
        vals.add("self", m_data.isSelf());

        vals.add("DE_NAM", m_data.data().deNam,m_data.data().deNam.length);
        vals.add("DE_Reserved", m_data.data().deReserved);
        vals.add("DE_LSN", m_data.data().deLsn.getOs9Lsn());

        final DiskBasicDirItemOS9FD cfd = getFDConst();
        if (!cfd.IsValid()) return;

        final DirectoryOs9Fd fd_data = cfd.GetFD();

        vals.add("FD_ATT", fd_data.fdAtt);
        vals.add("FD_OWN", (byte) fd_data.fdOwn, true);
        ByteArrayOutputStream x = new ByteArrayOutputStream();
        Serdes.Util.serialize(fd_data.fdDat, x);
        vals.add("FD_DAT", x.toByteArray(), x.size());
        vals.add("FD_LNK", fd_data.fdLnk);
        vals.add("FD_SIZ", (byte) fd_data.fdSiz, true);
        x = new ByteArrayOutputStream();
        Serdes.Util.serialize(fd_data.fdDcr, x);
        vals.add("FD_DCR", x.toByteArray(), x.size());
    }
}
