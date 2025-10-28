///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
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
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_NONSHARE_MASK;


/**
 * ディレクトリ１アイテム OS-9
 */
public class DiskBasicDirItemOS9 extends DiskBasicDirItem<DirectoryOs9> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    private static final int TYPE_NAME_OS9_DIRECTORY = 0;
    private static final int TYPE_NAME_OS9_NONSHARE = 1;

    public static final int FILETYPE_MASK_OS9_DIRECTORY = 0x80;
    public static final int FILETYPE_MASK_OS9_NONSHARE = 0x40;
    public static final int FILETYPE_MASK_OS9_PUBLIC_EXEC = 0x20;
    public static final int FILETYPE_MASK_OS9_PUBLIC_WRITE = 0x10;
    public static final int FILETYPE_MASK_OS9_PUBLIC_READ = 0x08;
    public static final int FILETYPE_MASK_OS9_USER_EXEC = 0x04;
    public static final int FILETYPE_MASK_OS9_USER_WRITE = 0x02;
    public static final int FILETYPE_MASK_OS9_USER_READ = 0x01;

    // OS-9属性名
    public static final String[] G_TYPE_NAME_OS9 = {
            "<DIR>",
            "Non-sharable",
    };

    public static final char[] G_TYPE_NAME_OS9_2 = {
            'X', 'W', 'R', 'x', 'w', 'r'
    };

    public static final String[] G_TYPE_NAME_OS9_2L = {
            "Execute",
            "Write",
            "Read",
    };

    /**
     * OS-9 File Descriptorエリアのポインタ
     */
    public static class DiskBasicDirItemOS9FD {

        private DiskBasic basic;
        private DiskImageSector sector;
        private DirectoryOs9Fd p_fd;
        private int m_mylsn;
        private boolean m_fd_ownmake;
        private final ZeroData zero_data = new ZeroData(); // union replacement

        private static class ZeroData {

            public Os9Date date = new Os9Date();
            public Os9Cdate cdate = new Os9Cdate();
        }

        public DiskBasicDirItemOS9FD() {
            basic = null;
            sector = null;
            p_fd = null;
            m_mylsn = -1;
            m_fd_ownmake = false;
        }

        /**
         * ポインタをセット
         */
        public void set(DiskBasic n_basic, DiskImageSector n_sector, int n_mylsn, DirectoryOs9Fd n_fd) {
            basic = n_basic;
            sector = n_sector;
            m_mylsn = n_mylsn;
            if (m_fd_ownmake) {
                p_fd = null;
            }
            p_fd = n_fd;
            m_fd_ownmake = false;
        }

        /**
         * FDのメモリ確保
         */
        public void alloc() {
            if (m_fd_ownmake) {
                p_fd = null;
            }
            p_fd = new DirectoryOs9Fd();
            m_fd_ownmake = true;
        }

        /**
         * FDをクリア
         */
        public void clear() {
            if (sector != null) {
                sector.fill((byte) 0);
            } else if (p_fd != null) {
                p_fd = new DirectoryOs9Fd();
            }
        }

        /**
         * 有効か
         */
        public boolean isValid() {
            return (p_fd != null);
        }

        /**
         * FDへのポインタを返す
         */
        public DirectoryOs9Fd getFD() {
            return p_fd;
        }

        /**
         * 自分のLSNを返す
         */
        public int getMyLSN() {
            return m_mylsn;
        }

        /**
         * 自分のLSNを設定
         */
        public void setMyLSN(int val) {
            m_mylsn = val;
        }

        /**
         * 属性を返す
         */
        public short getATT() {
            return p_fd != null ? p_fd.fdAtt : 0;
        }

        /**
         * 属性をセット
         */
        public void setATT(short val) {
            if (p_fd != null) {
                p_fd.fdAtt = (byte) val;
            }
        }

        /**
         * ユーザIDを返す
         */
        public int getOWN() {
            return p_fd != null ? p_fd.fdOwn : 0;
        }

        /**
         * ユーザIDをセット
         */
        public void setOWN(int val) {
            if (p_fd != null) {
                p_fd.fdOwn = (short) val;
            }
        }

        /**
         * セグメントのLSNを返す
         */
        public int getLSN(int idx) {
            return p_fd != null ? p_fd.fdSeg[idx].lsn.getOs9Lsn() : 0;
        }

        /**
         * セグメントのセクタ数を返す
         */
        public int getSIZ(int idx) {
            return p_fd != null ? p_fd.fdSeg[idx].siz : 0;
        }

        /**
         * セグメントにLSNを設定
         */
        public void SetLSN(int idx, int val) {
            if (p_fd != null) {
                Os9Lsn x = new Os9Lsn();
                x.setOs9Lsn(val);
                p_fd.fdSeg[idx].lsn = x;
            }
        }

        /**
         * セグメントにセクタ数を設定
         */
        public void setSIZ(int idx, int val) {
            if (p_fd != null) {
                p_fd.fdSeg[idx].siz = (short) val;
            }
        }

        /**
         * ファイルサイズを返す
         */
        public int getSIZ() {
            return p_fd != null ? p_fd.fdSiz : 0;
        }

        /**
         * ファイルサイズを設定
         */
        public void setSIZ(int val) {
            if (p_fd != null) {
                p_fd.fdSiz = val;
            }
        }

        /**
         * リンク数を返す
         */
        public short getLNK() {
            return p_fd != null ? p_fd.fdLnk : 0;
        }

        /**
         * リンク数を設定
         */
        public void setLNK(short val) {
            if (p_fd != null) {
                p_fd.fdLnk = (byte) val;
            }
        }

        /**
         * 更新日付を返す
         */
        public Os9Date getDAT() {
            return p_fd != null ? p_fd.fdDat : zero_data.date;
        }

        /**
         * 更新日付をセット
         */
        public void setDAT(Os9Date val) {
            if (p_fd != null) {
                p_fd.fdDat = val;
            }
        }

        /**
         * 更新日付をセット
         */
        public void setDAT(Os9Cdate val) {
            if (p_fd != null) {
                p_fd.fdDat.yy = val.yy;
                p_fd.fdDat.mm = val.mm;
                p_fd.fdDat.dd = val.dd;
            }
        }

        /**
         * 作成日付を返す
         */
        public Os9Cdate getDCR() {
            return p_fd != null ? p_fd.fdDcr : zero_data.cdate;
        }

        /**
         * 作成日付をセット
         */
        public void setDCR(Os9Cdate val) {
            if (p_fd != null) {
                p_fd.fdDcr = val;
            }
        }

        /**
         * 更新にする
         */
        public void setModify() {
            // No logic in C++, so no logic here.
        }
    }

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryOs9> m_data = new DiskBasicDirData<>();

    /** File Descriptorエリアのポインタ */
    private final DiskBasicDirItemOS9.DiskBasicDirItemOS9FD fd = new DiskBasicDirItemOS9FD();

    /** ユーザID(プロパティダイアログ用) */
    public int m_owner_id;

    /** グループID(プロパティダイアログ用) */
    public int m_group_id;

    public DiskBasicDirItemOS9(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryOs9.class);
        fd.alloc();
        m_owner_id = 0;
        m_group_id = 0;
    }

    public DiskBasicDirItemOS9(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryOs9.class, n_data, dataP);
        m_owner_id = 0;
        m_group_id = 0;
    }

    public DiskBasicDirItemOS9(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryOs9.class, n_data, dataP);
        m_owner_id = 0;
        m_group_id = 0;

        used(checkUsed(n_unuse[0]));

        // FDセクタへのポインタをセット
        if (isUsed()) {
            int lsn = m_data.data().deLsn.getOs9Lsn();
            if (lsn != 0) {
                DiskImageSector sector = basic.getSectorFromGroup(lsn);
                if (sector != null) {
                    DirectoryOs9Fd fd = new DirectoryOs9Fd();
                    Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), fd);
                    this.fd.set(basic, sector, lsn, fd);
                }
            }
        }

        calcFileSize();

        // カレント or 親ディレクトリはツリーに表示しない
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && (name.equals(".") || name.equals(".."))));
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

        m_data.attach(DirectoryOs9.class, n_data, dataP);
    }

    /**
     * ファイル名を格納する位置を返す
     */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
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
        return fd.getATT() & 0xffff;
    }

    /**
     * 属性１のセット
     */
    @Override
    protected void setFileType1(int val) {
        fd.setATT((short) (val & 0xff));
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
    public int getUserID() {
        return fd.getOWN();
    }

    /**
     * ユーザIDのセット
     */
    public void setUserID(int val) {
        fd.setOWN(val & 0xffff);
    }

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     */
    public int getFileType1Pos() {
        return getFileType1();
    }

    /**
     * ファイル名を設定
     */
    @Override
    protected void setNativeName(byte[] filename, int size, int length) {
        int[] s = {0}, l = {0};
        byte[] n = getFileNamePos(0, s, l);
        encodeString(n, l[0], new String(filename, 0, length), length);
    }

    /**
     * ファイル名を得る
     */
    @Override
    public void getNativeFileName(byte[] name, int[] nlen, byte[] ext, int[] elen) {
        super.getNativeFileName(name, nlen, ext, elen);

        // 文字列の最後はMSBがセットされているのでクリア
        nlen[0] = decodeString(name, nlen[0], name, nlen[0]);
    }

    /**
     * 日付を変換
     */
    public LocalDate convDateToTm(Os9Cdate date) {
        return LocalDate.of(
                (date.yy % 100) +
                        (date.yy % 100) < 80 ? 100 : 0,
                date.mm - 1,
                date.dd);
    }

    /**
     * 時間を変換
     */
    public LocalTime convTimeToTm(Os9Date time) {
        return LocalTime.of(
                time.hh,
                time.mi,
                0);
    }

    /**
     * 日付に変換
     */
    public void convTmToDate(LocalDateTime tm, Os9Cdate date) {
        date.yy = (byte) (tm.getYear() % 100);
        date.mm = (byte) (tm.getMonth().ordinal() + 1);
        date.dd = (byte) tm.getDayOfMonth();
    }

    /**
     * 時間に変換
     */
    public void convTmToTime(LocalDateTime tm, Os9Date time) {
        time.hh = (byte) tm.getHour();
        time.mi = (byte) tm.getMinute();
    }

    /**
     * ディレクトリアイテムのチェック
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        //if (m_data.data().DE_Reserved != 0) return false;

        return true;
    }

    /**
     * 削除
     */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        m_data.data().deNam[0] = basic.diskBasicParam.getDeleteCode();
        used(false);
        return true;
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int user_id = -1;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            t1 = file_type.getOrigin(0);
            user_id = file_type.getOrigin(1);
        } else {
            if ((ftype & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_OS9_DIRECTORY;
            }
            if ((ftype & FILE_TYPE_NONSHARE_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_OS9_NONSHARE;
            }
            //t1 |= ((ftype & FILETYPE_OS9_PERMISSION_MASK) >> FILETYPE_OS9_PERMISSION_POS);
            if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_OS9_PUBLIC_EXEC;
                t1 |= FILETYPE_MASK_OS9_USER_EXEC;
            }
            t1 |= FILETYPE_MASK_OS9_PUBLIC_READ;
            t1 |= FILETYPE_MASK_OS9_USER_WRITE;
            t1 |= FILETYPE_MASK_OS9_USER_READ;
        }
        setFileType1(t1);
        if (user_id >= 0) setUserID(user_id);
    }

    /**
     * 属性を返す
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int t1 = getFileType1();
        if ((t1 & FILETYPE_MASK_OS9_DIRECTORY) != 0) {
            val |= FILE_TYPE_DIRECTORY_MASK.getValue();
        }
        if ((t1 & FILETYPE_MASK_OS9_NONSHARE) != 0) {
            val |= FILE_TYPE_NONSHARE_MASK.getValue();
        }
        if ((t1 & (FILETYPE_MASK_OS9_PUBLIC_EXEC | FILETYPE_MASK_OS9_USER_EXEC)) != 0) {
            val |= FILE_TYPE_BINARY_MASK.getValue();
        }
        //val |= ((t1 << FILETYPE_OS9_PERMISSION_POS) & FILETYPE_OS9_PERMISSION_MASK);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1, getUserID(), 0);
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     */
    @Override
    public String getFileAttrStr() {
        StringBuilder str = new StringBuilder();
        if (fd.isValid()) {
            if ((fd.getATT() & FILETYPE_MASK_OS9_DIRECTORY) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(G_TYPE_NAME_OS9[TYPE_NAME_OS9_DIRECTORY]));
            }
            if ((fd.getATT() & FILETYPE_MASK_OS9_NONSHARE) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(G_TYPE_NAME_OS9[TYPE_NAME_OS9_NONSHARE]));
            }
            if (!str.isEmpty()) str.append(", ");
            for (int i = 0; i < 6; i++) {
                if ((fd.getATT() & (0x20 >> i)) != 0) {
                    str.append(G_TYPE_NAME_OS9_2[i]);
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
        fd.setSIZ(val);
        groups.setSize(val);
    }

    /**
     * ファイルサイズを返す
     */
    @Override
    public int getFileSize() {
        int size = fd.getSIZ();
        if (size == 0) size = groups.getSize();
        return size;
    }

    /**
     * ファイルサイズとグループ数を計算する
     */
    @Override
    public void calcFileUnitSize(int fileunit_num) {
        if (!isUsed() || !fd.isValid()) return;

        getUnitGroups(fileunit_num, groups);
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     */
    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) {
        if (!fd.isValid()) return;

        int calc_groups = 0;
        int calc_file_size = getFileSize();

        for (int i = 0; i < 48; i++) {
            int lsn = fd.getLSN(i);
            int siz = fd.getSIZ(i);
            if (siz == 0) {
                break;
            }

            if (i != 0) {
                if (group_items.size() > 0) {
                    DiskBasicGroupItem gitm = group_items.last();
                    gitm.next = lsn;
                }
            }
            for (int n = 0; n < siz; n++) {
                int[] track_num = {0};
                int[] side_num = {0};
                int[] sector_num = {1};
                int next_lsn = n + 1 != siz ? lsn + n + 1 : 0;
                basic.calcNumFromSectorPosForGroup(lsn + n, track_num, side_num, sector_num, null, null);
                group_items.add(lsn + n, next_lsn, track_num[0], side_num[0], sector_num[0], sector_num[0], 0, 1);
                calc_groups++;
                if (calc_groups >= basic.getFatEndGroup()) {
                    // too large block size
                    break;
                }
            }
        }
        group_items.setNums(calc_groups);
        group_items.setSize(calc_file_size);
        group_items.setSizePerGroup(basic.getSectorSize());
    }

    /**
     * 作成日付を得る
     */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        if (fd.isValid()) {
            return convDateToTm(fd.getDCR());
        } else {
            return LocalDate.of(1970, 1, 1);
        }
    }

    /**
     * 作成日付を文字列で返す
     */
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /**
     * 作成日付をセット
     */
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (fd.isValid() && tm.getYear() >= 0 && tm.getMonth().ordinal() >= 0) {
            Os9Cdate date = new Os9Cdate();
            convTmToDate(tm, date);
            fd.setDCR(date);
        }
    }

    /**
     * 更新日付を得る
     */
    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        if (fd.isValid()) {
            Os9Cdate cdate = fd.getDAT().toCdate();
            cdate.yy = fd.getDAT().yy;
            cdate.mm = fd.getDAT().mm;
            cdate.dd = fd.getDAT().dd;
            return convDateToTm(cdate);
        } else {
            return LocalDate.of(1970, 1, 1);
        }
    }

    /**
     * 更新時間を得る
     */
    @Override
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        if (fd.isValid()) {
            return convTimeToTm(fd.getDAT());
        } else {
            return LocalTime.of(0, 0, 0);
        }
    }

    /**
     * 更新日付を文字列で返す
     */
    @Override
    public String getFileModifyDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileModifyDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /**
     * 更新時間を文字列で返す
     */
    @Override
    public String getFileModifyTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileModifyTime(tm);
        return Utils.formatHMStr(lt);
    }

    /**
     * 更新日付を設定
     */
    @Override
    public void setFileModifyDate(LocalDateTime tm) {
        if (fd.isValid() && tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            Os9Cdate date = new Os9Cdate();
            convTmToDate(tm, date);
            fd.setDAT(date);
        }
    }

    /**
     * 更新時間を設定
     */
    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        if (fd.isValid() && tm.getHour() >= 0 && tm.getMinute() >= -1) {
            Os9Date time = fd.getDAT();
            convTmToTime(tm, time);
            fd.setDAT(time);
        }
    }

    /**
     * 日時の表示順序を返す（ダイアログ用）
     */
    @Override
    public int getFileDateTimeOrder(int idx) {
        return idx <= 1 ? 1 - idx : idx;
    }

    /**
     * 日時を返す（ファイルリスト用）
     */
    @Override
    public String getFileDateTimeStr() {
        return getFileModifyDateTimeStr();
    }

    /**
     * 最初のグループ番号を設定
     */
    @Override
    public void setStartGroup(int fileunit_num, int val, int size /* = 0 */) {
        m_data.data().deLsn.setOs9Lsn(val);
    }

    /**
     * 最初のグループ番号を返す
     */
    @Override
    public int getStartGroup(int fileunit_num) {
        return m_data.data().deLsn.getOs9Lsn();
    }

    /**
     * 追加のグループ番号をセット FDセクタへのLSNをセット
     */
    @Override
    public void setExtraGroup(int val) {
        m_data.data().deLsn.setOs9Lsn(val);
    }

    /**
     * 追加のグループ番号を返す FDセクタへのLSNを返す
     */
    @Override
    public int getExtraGroup() {
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
        DirectoryOs9Fd fd = new DirectoryOs9Fd();
        Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), fd);
        this.fd.set(basic, sector, lsn, fd);
        this.fd.clear();

        // 属性をコピー
        if (pitem != null) copyItem(pitem);

        // リンク数
        this.fd.setLNK((short) 1);
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
        boolean valid = ((t1 & FILETYPE_MASK_OS9_DIRECTORY) == 0);
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
    public boolean copyData(byte[] val) {
        return m_data.copy(val, getDataSize());
    }

    /**
     * ディレクトリをクリア
     */
    @Override
    public void clearData() {
        m_data.fill(basic.diskBasicParam.getDeleteCode(), getDataSize(), basic.isDataInverted(), 0);
    }

    /**
     * アイテムをコピー
     */
    @Override
    public void copyItem(DiskBasicDirItem<DirectoryOs9> src) {
        super.copyItem(src);
        DiskBasicDirItemOS9FD src_fd = ((DiskBasicDirItemOS9) src).getFD();
        fd.setATT(src_fd.getATT());
        fd.setOWN(src_fd.getOWN());
        fd.setSIZ(src_fd.getSIZ());
        fd.setDAT(src_fd.getDAT());
        fd.setDCR(src_fd.getDCR());
    }

    /**
     * FDセクタのポインタを返す
     */
    public DiskBasicDirItemOS9FD getFD() {
        return fd;
    }

    /**
     * アイテムの属するセクタを変更済みにする
     */
    @Override
    public void setModify() {
        super.setModify();
        fd.setModify();
    }

    /**
     * 文字列の最後のMSBをセット
     */
    public static int encodeString(byte[] dst, int dlen, String src, int slen) {
        Arrays.fill(dst, 0, dlen, (byte) 0);
        int len = dlen > slen ? slen : dlen;
        System.arraycopy(src, 0, dst, 0, slen);

        // 文字列の最後にMSBをセット
        for (int i = len - 1; i >= 0; i--) {
            if (dst[i] != 0) {
                dst[i] |= (byte) 0x80;
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
        if (!gConfig.isAddExtensionExport()) return true;

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
        if (gConfig.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * ファイル名から属性を決定する
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        final int FILE_TYPE_BINARY_MASK = 0x04;

        int t1 = 0;
        // -- --R -wr
        t1 |= FILETYPE_MASK_OS9_PUBLIC_READ;
        t1 |= FILETYPE_MASK_OS9_USER_WRITE;
        t1 |= FILETYPE_MASK_OS9_USER_READ;
        // 拡張子で実行属性を付ける
        MyAttribute sa = findUpperCase(basic.diskBasicParam.getAttributesByExtension(), Utils.getExt(filename), FILE_TYPE_BINARY_MASK, FILE_TYPE_BINARY_MASK);
        if (sa != null) {
            // 実行属性を付ける
            t1 |= FILETYPE_MASK_OS9_PUBLIC_EXEC;
            t1 |= FILETYPE_MASK_OS9_USER_EXEC;
        }

        return t1;
    }

    /**
     * その他の属性値を設定する
     */
    @Override
    public void setOptionalAttr(DiskBasicDirItemAttr attr) {
        //setCDate(attr.getCreateDateTime());
    }

    /**
     * プロパティで表示する内部データを設定
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) throws IOException {

        vals.add("DE_NAM", m_data.data().deNam, m_data.data().deNam.length);
        vals.add("DE_Reserved", m_data.data().deReserved);
        vals.add("DE_LSN", m_data.data().deLsn.getOs9Lsn());

        DiskBasicDirItemOS9FD cfd = getFD();
        if (!cfd.isValid()) return;

        DirectoryOs9Fd fd_data = cfd.getFD();

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
