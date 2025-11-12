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
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DirectoryOs9;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_NONSHARE_MASK;


/**
 * ディレクトリ１アイテム OS-9
 */
public class DiskBasicDirItemOS9 extends DiskBasicDirItem<DirectoryOs9> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * OS-9 LSN
     */
    @Serdes
    public static class Os9Lsn {

        @Element(sequence = 1)
        public byte h;
        @Element(sequence = 2)
        public byte m;
        @Element(sequence = 3)
        public byte l;

        public int getOs9Lsn() {
            return (((h & 0xff) << 16) | ((m & 0xff) << 8) | (l & 0xff));
        }

        public void setOs9Lsn(int val) {
            h = (byte) ((val & 0xff0000) >> 16);
            m = (byte) ((val & 0xff00) >> 8);
            l = (byte) (val & 0xff);
        }
    }

    /**
     * OS-9 Segment
     */
    @Serdes
    public static class Os9Segment {

        @Element(sequence = 1)
        public Os9Lsn lsn = new Os9Lsn();
        @Element(sequence = 2)
        public short siz;
    }

    /**
     * OS-9 Date Format
     */
    @Serdes
    public static class Os9Date {

        @Element(sequence = 1)
        public byte yy;
        @Element(sequence = 2)
        public byte mm;
        @Element(sequence = 3)
        public byte dd;
        @Element(sequence = 4)
        public byte hh;
        @Element(sequence = 5)
        public byte mi;

        public Os9CDate toCDate() {
            Os9CDate cDate = new Os9CDate();
            cDate.yy = this.yy;
            cDate.mm = this.mm;
            cDate.dd = this.dd;
            return cDate;
        }
    }

    /**
     * OS-9 Created Date
     */
    @Serdes
    public static class Os9CDate {

        @Element(sequence = 1)
        public byte yy;
        @Element(sequence = 2)
        public byte mm;
        @Element(sequence = 3)
        public byte dd;
    }

    /**
     * ディレクトリエントリ OS-9 (32bytes)
     */
    @Serdes
    public static class DirectoryOs9 implements Directory {

        @Element(sequence = 1)
        public byte[] deNam = new byte[28];
        @Element(sequence = 2)
        public byte deReserved;
        @Element(sequence = 3)
        public Os9Lsn deLsn = new Os9Lsn(); // link to FD

        public static final int SIZE = 32;
    }

    /**
     * OS-9 File Descriptor
     */
    @Serdes
    public static class DirectoryOs9Fd implements Directory {

        @Element(sequence = 1)
        public byte attr; // 1 fdAtt
        @Element(sequence = 2)
        public short ownerId; // 2 fdOwn
        @Element(sequence = 3)
        public Os9Date date = new Os9Date(); // 5 fdDat
        @Element(sequence = 4)
        public byte linkCount; // 1 fdLnk
        @Element(sequence = 5)
        public int size; // 4 in bytes fdSiz
        @Element(sequence = 6)
        public Os9CDate cDate = new Os9CDate(); // 3 cDate
        @Element(sequence = 7)
        public Os9Segment[] segments = new Os9Segment[48]; // 5*48=240 fdSeg

        public DirectoryOs9Fd() {
            for (int i = 0; i < 48; i++) {
                segments[i] = new Os9Segment();
            }
        }

        public static final int SIZE = 256;
    }

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

    public static final char[] TYPE_NAME_OS9_2 = {
            'X', 'W', 'R', 'x', 'w', 'r'
    };

    public static final String[] TYPE_NAME_OS9_2L = {
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
        private DirectoryOs9Fd fd;
        private int myLsn;
        private final ZeroData zeroData = new ZeroData(); // union replacement

        private static class ZeroData {

            public Os9Date date = new Os9Date();
            public Os9CDate cDate = new Os9CDate();
        }

        public DiskBasicDirItemOS9FD() {
            basic = null;
            sector = null;
            fd = null;
            myLsn = -1;
        }

        /** ポインタをセット */
        public void set(DiskBasic basic, DiskImageSector sector, int myLsn, DirectoryOs9Fd fd) {
            this.basic = basic;
            this.sector = sector;
            this.myLsn = myLsn;
            this.fd = fd;
        }

        /** FDのメモリ確保 */
        public void alloc() {
            fd = null;
            fd = new DirectoryOs9Fd();
        }

        /** FDをクリア */
        public void clear() {
            if (sector != null) {
                sector.fill((byte) 0);
            } else if (fd != null) {
                fd = new DirectoryOs9Fd();
            }
        }

        /** 有効か */
        public boolean isValid() {
            return (fd != null);
        }

        /** FDへのポインタを返す */
        public DirectoryOs9Fd getFD() {
            return fd;
        }

        /** 自分のLSNを返す */
        public int getMyLSN() {
            return myLsn;
        }

        /** 自分のLSNを設定 */
        public void setMyLSN(int val) {
            myLsn = val;
        }

        /** 属性を返す */
        public short getAttr() {
            return fd != null ? fd.attr : 0;
        }

        /** 属性をセット */
        public void setAttr(short val) {
            if (fd != null) {
                fd.attr = (byte) val;
            }
        }

        /** ユーザIDを返す */
        public int getOwnerId() {
            return fd != null ? fd.ownerId : 0;
        }

        /** ユーザIDをセット */
        public void setOwnerId(int val) {
            if (fd != null) {
                fd.ownerId = (short) val;
            }
        }

        /** セグメントのLSNを返す */
        public int getLsn(int idx) {
            return fd != null ? fd.segments[idx].lsn.getOs9Lsn() : 0;
        }

        /** セグメントのセクタ数を返す */
        public int getSize(int idx) {
            return fd != null ? fd.segments[idx].siz : 0;
        }

        /** セグメントにLSNを設定 */
        public void setLsn(int idx, int val) {
            if (fd != null) {
                Os9Lsn x = new Os9Lsn();
                x.setOs9Lsn(val);
                fd.segments[idx].lsn = x;
            }
        }

        /** セグメントにセクタ数を設定 */
        public void setSize(int idx, int val) {
            if (fd != null) {
                fd.segments[idx].siz = (short) val;
            }
        }

        /** ファイルサイズを返す */
        public int getSize() {
            return fd != null ? fd.size : 0;
        }

        /** ファイルサイズを設定 */
        public void setSize(int val) {
            if (fd != null) {
                fd.size = val;
            }
        }

        /** リンク数を返す */
        public short getLinkCount() {
            return fd != null ? fd.linkCount : 0;
        }

        /** リンク数を設定 */
        public void setLinkCount(short val) {
            if (fd != null) {
                fd.linkCount = (byte) val;
            }
        }

        /** 更新日付を返す */
        public Os9Date getDate() {
            return fd != null ? fd.date : zeroData.date;
        }

        /** 更新日付をセット */
        public void setDate(Os9Date val) {
            if (fd != null) {
                fd.date = val;
            }
        }

        /** 更新日付をセット */
        public void setDate(Os9CDate val) {
            if (fd != null) {
                fd.date.yy = val.yy;
                fd.date.mm = val.mm;
                fd.date.dd = val.dd;
            }
        }

        /** 作成日付を返す */
        public Os9CDate getCDate() {
            return fd != null ? fd.cDate : zeroData.cDate;
        }

        /** 作成日付をセット */
        public void setCDate(Os9CDate val) {
            if (fd != null) {
                fd.cDate = val;
            }
        }

        /** 更新にする */
        public void setModify() {
        }
    }

    //
    //
    //

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryOs9> data = new DiskBasicDirData<>();

    /** File Descriptorエリアのポインタ */
    private final DiskBasicDirItemOS9.DiskBasicDirItemOS9FD fd = new DiskBasicDirItemOS9FD();

    /** ユーザID(プロパティダイアログ用) */
    public int ownerId;

    /** グループID(プロパティダイアログ用) */
    public int groupId;

    public DiskBasicDirItemOS9(DiskBasic basic) {
        super(basic);

        data.alloc(DirectoryOs9.class);
        fd.alloc();
        ownerId = 0;
        groupId = 0;
    }

    public DiskBasicDirItemOS9(DiskBasic basic, DiskImageSector sector, int secPos, byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);

        this.data.attach(DirectoryOs9.class, data, dataP);
        ownerId = 0;
        groupId = 0;
    }

    public DiskBasicDirItemOS9(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int secPos,
                               byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super(basic, num, groupItem, sector, secPos, data, dataP, next, unuse);

        this.data.attach(DirectoryOs9.class, data, dataP);
        ownerId = 0;
        groupId = 0;

        used(checkUsed(unuse[0]));

        // FDセクタへのポインタをセット
        if (isUsed()) {
            int lsn = this.data.data().deLsn.getOs9Lsn();
            if (lsn != 0) {
                DiskImageSector targetSector = basic.getSectorFromGroup(lsn);
                if (targetSector != null) {
                    DirectoryOs9Fd fd = new DirectoryOs9Fd();
                    Serdes.Util.deserialize(new ByteArrayInputStream(targetSector.getSectorBuffer()), fd);
                    this.fd.set(basic, targetSector, lsn, fd);
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
     * @param num    通し番号
     * @param groupItem  トラック番号などのデータ
     * @param sector セクタ
     * @param sectorPos セクタ内のディレクトリエントリの位置
     * @param data   ディレクトリアイテム
     * @param next   [out] 次のセクタ
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryOs9.class, data, dataPos);
    }

    /**
     * ファイル名を格納する位置を返す
     */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = data.data().deNam.length;
            return data.data().deNam;
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
        return fd.getAttr() & 0xffff;
    }

    /**
     * 属性１のセット
     */
    @Override
    protected void setFileType1(int val) {
        fd.setAttr((short) (val & 0xff));
    }

    /**
     * 使用しているアイテムか
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (data.data().deNam[0] != 0);
    }

    /**
     * ユーザIDを返す
     */
    public int getUserID() {
        return fd.getOwnerId();
    }

    /**
     * ユーザIDのセット
     */
    public void setUserID(int val) {
        fd.setOwnerId(val & 0xffff);
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
    public void getNativeFileName(byte[] name, int[] nLen, byte[] ext, int[] eLen) {
        super.getNativeFileName(name, nLen, ext, eLen);

        // 文字列の最後はMSBがセットされているのでクリア
        nLen[0] = decodeString(name, nLen[0], name, nLen[0]);
    }

    /**
     * 日付を変換
     */
    public LocalDate convDateToTm(Os9CDate date) {
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
    public void convTmToDate(LocalDateTime tm, Os9CDate date) {
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
        if (!data.isValid()) return false;

        //if (data.data().DE_Reserved != 0) return false;

        return true;
    }

    /**
     * 削除
     */
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        data.data().deNam[0] = basic.getDeleteCode();
        used(false);
        return true;
    }

    /**
     * 属性を設定
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        int user_id = -1;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = fileType.getOrigin(0);
            user_id = fileType.getOrigin(1);
        } else {
            if ((fType & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_OS9_DIRECTORY;
            }
            if ((fType & FILE_TYPE_NONSHARE_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_OS9_NONSHARE;
            }
            //t1 |= ((fType & FILETYPE_OS9_PERMISSION_MASK) >> FILETYPE_OS9_PERMISSION_POS);
            if ((fType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
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
            if ((fd.getAttr() & FILETYPE_MASK_OS9_DIRECTORY) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(G_TYPE_NAME_OS9[TYPE_NAME_OS9_DIRECTORY]));
            }
            if ((fd.getAttr() & FILETYPE_MASK_OS9_NONSHARE) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(G_TYPE_NAME_OS9[TYPE_NAME_OS9_NONSHARE]));
            }
            if (!str.isEmpty()) str.append(", ");
            for (int i = 0; i < 6; i++) {
                if ((fd.getAttr() & (0x20 >> i)) != 0) {
                    str.append(TYPE_NAME_OS9_2[i]);
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
        fd.setSize(val);
        groups.setSize(val);
    }

    /**
     * ファイルサイズを返す
     */
    @Override
    public int getFileSize() {
        int size = fd.getSize();
        if (size == 0) size = groups.getSize();
        return size;
    }

    /**
     * ファイルサイズとグループ数を計算する
     */
    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed() || !fd.isValid()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) {
        if (!fd.isValid()) return;

        int calcGroups = 0;
        int calcFileSize = getFileSize();

        for (int i = 0; i < 48; i++) {
            int lsn = fd.getLsn(i);
            int size = fd.getSize(i);
            if (size == 0) {
                break;
            }

            if (i != 0) {
                if (groupItems.size() > 0) {
                    DiskBasicGroupItem groupItem = groupItems.last();
                    groupItem.next = lsn;
                }
            }
            for (int n = 0; n < size; n++) {
                int[] trackNum = {0};
                int[] sideNum = {0};
                int[] sectorNum = {1};
                int nextLsn = n + 1 != size ? lsn + n + 1 : 0;
                basic.calcNumFromSectorPosForGroup(lsn + n, trackNum, sideNum, sectorNum, null, null);
                groupItems.add(lsn + n, nextLsn, trackNum[0], sideNum[0], sectorNum[0], sectorNum[0], 0, 1);
                calcGroups++;
                if (calcGroups >= basic.getFatEndGroup()) {
                    // too large block size
                    break;
                }
            }
        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize());
    }

    /**
     * 作成日付を得る
     */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        if (fd.isValid()) {
            return convDateToTm(fd.getCDate());
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
            Os9CDate date = new Os9CDate();
            convTmToDate(tm, date);
            fd.setCDate(date);
        }
    }

    /**
     * 更新日付を得る
     */
    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        if (fd.isValid()) {
            Os9CDate cDate = fd.getDate().toCDate();
            cDate.yy = fd.getDate().yy;
            cDate.mm = fd.getDate().mm;
            cDate.dd = fd.getDate().dd;
            return convDateToTm(cDate);
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
            return convTimeToTm(fd.getDate());
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
            Os9CDate date = new Os9CDate();
            convTmToDate(tm, date);
            fd.setDate(date);
        }
    }

    /**
     * 更新時間を設定
     */
    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        if (fd.isValid() && tm.getHour() >= 0 && tm.getMinute() >= -1) {
            Os9Date time = fd.getDate();
            convTmToTime(tm, time);
            fd.setDate(time);
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
    public void setStartGroup(int fileUnitNum, int val, int size /* = 0 */) {
        data.data().deLsn.setOs9Lsn(val);
    }

    /**
     * 最初のグループ番号を返す
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return data.data().deLsn.getOs9Lsn();
    }

    /**
     * 追加のグループ番号をセット FDセクタへのLSNをセット
     */
    @Override
    public void setExtraGroup(int val) {
        data.data().deLsn.setOs9Lsn(val);
    }

    /**
     * 追加のグループ番号を返す FDセクタへのLSNを返す
     */
    @Override
    public int getExtraGroup() {
        return data.data().deLsn.getOs9Lsn();
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
    public void setChainSector(DiskImageSector sector, int lsn, byte[] data, DiskBasicDirItem<DirectoryOs9> pItem) throws IOException {
        DirectoryOs9Fd fd = new DirectoryOs9Fd();
        Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), fd);
        this.fd.set(basic, sector, lsn, fd);
        this.fd.clear();

        // 属性をコピー
        if (pItem != null) copyItem(pItem);

        // リンク数
        this.fd.setLinkCount((short) 1);
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
        return data.getDataSize();
    }

    /**
     * アイテムを返す
     */
    @Override
    public DirectoryOs9 getData() {
        return data.data();
    }

    /**
     * アイテムをコピー
     */
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val, getDataSize());
    }

    /**
     * ディレクトリをクリア
     */
    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode(), getDataSize(), basic.isDataInverted(), 0);
    }

    /**
     * アイテムをコピー
     */
    @Override
    public void copyItem(DiskBasicDirItem<DirectoryOs9> src) {
        super.copyItem(src);
        DiskBasicDirItemOS9FD srcFd = ((DiskBasicDirItemOS9) src).getFd();
        fd.setAttr(srcFd.getAttr());
        fd.setOwnerId(srcFd.getOwnerId());
        fd.setSize(srcFd.getSize());
        fd.setDate(srcFd.getDate());
        fd.setCDate(srcFd.getCDate());
    }

    /**
     * FDセクタのポインタを返す
     */
    public DiskBasicDirItemOS9FD getFd() {
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
    public static int encodeString(byte[] dst, int dLen, String src, int sLen) {
        Arrays.fill(dst, 0, dLen, (byte) 0);
        int len = dLen > sLen ? sLen : dLen;
        System.arraycopy(src, 0, dst, 0, sLen);

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
    public static int decodeString(byte[] dst, int dLen, byte[] src, int sLen) {
        int len = dLen > sLen ? sLen : dLen;

        // 文字列のMSBをクリア
        boolean last = false;
        for (int i = 0; i < len; i++) {
            last = ((src[i] & 0x80) != 0);
            dst[i] = (byte) (src[i] & 0x7f);
            if (last) {
                dLen = i + 1;
                break;
            }
        }
        for (int i = dLen; i < len; i++) {
            dst[i] = 0;
        }
        return dLen;
    }

    /**
     * データをエクスポートする前に必要な処理
     */
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

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
        if (config.isDecideAttrImport()) {
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
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), Utils.getExt(filename), FILE_TYPE_BINARY_MASK, FILE_TYPE_BINARY_MASK);
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

        vals.add("DE_NAM", data.data().deNam, data.data().deNam.length);
        vals.add("DE_Reserved", data.data().deReserved);
        vals.add("DE_LSN", data.data().deLsn.getOs9Lsn());

        DiskBasicDirItemOS9FD cfd = getFd();
        if (!cfd.isValid()) return;

        DirectoryOs9Fd fd_data = cfd.getFD();

        vals.add("FD_ATT", fd_data.attr);
        vals.add("FD_OWN", (byte) fd_data.ownerId, true);
        ByteArrayOutputStream x = new ByteArrayOutputStream();
        Serdes.Util.serialize(fd_data.date, x);
        vals.add("FD_DAT", x.toByteArray(), x.size());
        vals.add("FD_LNK", fd_data.linkCount);
        vals.add("FD_SIZ", (byte) fd_data.size, true);
        x = new ByteArrayOutputStream();
        Serdes.Util.serialize(fd_data.cDate, x);
        vals.add("FD_DCR", x.toByteArray(), x.size());
    }
}
