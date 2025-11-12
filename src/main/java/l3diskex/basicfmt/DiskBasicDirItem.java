package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import l3diskex.Common;
import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.Parambase.MyAttributes.find;
import static l3diskex.Parambase.MyAttributes.findType;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.Parambase.MyAttributes.findValue;
import static l3diskex.Parambase.MyAttributes.getIndexByValue;
import static l3diskex.Parambase.MyAttributes.getTypeByIndex;
import static l3diskex.Parambase.MyAttributes.getValueByIndex;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_EXTENSION_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;


/** ディレクトリ１アイテム */
public abstract class DiskBasicDirItem<T extends Directory> {

    private static final Logger logger = System.getLogger(DiskBasicDirItem.class.getName());

    /** ディレクトリデータ */
    protected static class DiskBasicDirData<TYPE extends Directory> {

        private Class<TYPE> clazz;
        private TYPE data;
        private byte[] raw;
        private int size;

        public DiskBasicDirData() {
            data = null;
        }

        /**
         * メモリ確保
         * メモリの初期化は行わない
         */
        public void alloc(Class<TYPE> clazz) {
            try {
                this.clazz = clazz;
                this.size = (int) clazz.getDeclaredField("SIZE").get(null);
                this.raw = new byte[this.size];
            } catch (Exception e) {
                logger.log(Level.ERROR, e.getMessage(), e);
            }
        }

        /**
         * ポインタ割り当て
         *
         * @param data ポインタ
         */
        public void attach(Class<TYPE> clazz, byte[] data, int offset) {
            if (this.raw == null) alloc(clazz);
            if (data != null) {
                System.arraycopy(data, offset, raw, 0, Math.min(this.size, data.length - offset));
            }
        }

        public boolean copy(byte[] srcData) {
            return copy(srcData, 0xff_fffff, false, 0);
        }

        public boolean copy(byte[] srcData, int len) {
            return copy(srcData, len, false, 0);
        }

        /**
         * コピー
         *
         * @param srcData 元データ
         * @param len     データサイズ
         * @param invert  反転するか
         * @param start   コピー開始位置
         */
        public boolean copy(byte[] srcData, int len, boolean invert, int start) {
            if (data == null) return false;
            System.arraycopy(srcData, start, raw, 0, len);
            if (invert) invert(len, start);
            return true;
        }

        public boolean fill(int ch) {
            return fill(ch, 0xff_fffff, false, 0);
        }

        public boolean fill(int ch, int len) {
            return fill(ch, len, false, 0);
        }

        /**
         * 埋める
         *
         * @param ch     埋める文字
         * @param len    データサイズ
         * @param invert 反転するか
         * @param start  コピー開始位置
         */
        public boolean fill(int ch, int len, boolean invert, int start /* = 0 */) {
            if (raw == null) return false;
            byte[] dst = raw;
            if (len > dst.length) len = dst.length;
            Arrays.fill(dst, start, len, (byte) ch);
            if (invert) invert(len, start);
            return true;
        }

        /**
         * 反転
         *
         * @param len   データサイズ
         * @param start コピー開始位置
         */
        public void invert(int len, int start) {
            byte[] arr = raw;
            if (len > arr.length) len = arr.length;
            for (int i = start; i < len; i++) {
                arr[i] = (byte) (~arr[i]);
            }
        }

        /** データを返す */
        public TYPE data() {
            if (data == null) {
                try {
                    this.data = clazz.getDeclaredConstructor().newInstance();
                    Serdes.Util.deserialize(new ByteArrayInputStream(raw), this.data);
                } catch (Exception e) {
                    logger.log(Level.ERROR, e.getMessage(), e);
                }
            }
            return data;
        }

        /** データが有効か */
        public boolean isValid() {
            return raw != null;
        }

        public int getDataSize() {
            return size;
        }

        public byte[] getRawData() {
            return raw;
        }
    }

    /** bit0:使用しているか */
    public static final int USED_ITEM = 0x0001;
    /** bit1:リストに表示するか */
    public static final int VISIBLE_LIST = 0x0002;
    /** bit2:ツリーに表示するか */
    public static final int VISIBLE_TREE = 0x0004;

    protected DiskBasic basic;
    protected DiskBasicType<T> type;

    /** 親ディレクトリ */
    protected DiskBasicDirItem<T> parent;
    /** 子ディレクトリ */
    protected List<DiskBasicDirItem<T>> children;
    /** 上記ディレクトリツリーが確定しているか */
    protected boolean validDir;

    /** 通し番号 */
    protected int num;
    /** セクタ内の位置 (バイト) */
    protected int position;
    /** フラグ bit0: 使用しているか, bit1: リストに表示するか, bit2: ツリーに表示するか */
    protected int flags;
    /** 占有グループ */
    protected DiskBasicGroups groups;
    /** ディレクトリのあるセクタ */
    protected DiskImageSector sector;
    /** ディレクトリエントリ内に持たない属性を保持する(機種依存) */
    public int externalAttr;

    public DiskBasicDirItem() {
        this.basic = null;
        this.type = null;
        parent = null;
        children = null;
        validDir = false;
        num = 0;
        position = 0;
        sector = null;
        externalAttr = 0;
        flags = VISIBLE_LIST | VISIBLE_TREE;
        groups = new DiskBasicGroups();
    }


    /**
     * ディレクトリアイテムを作成 DATAは内部で確保
     *
     * @param basic DISK BASIC
     */
    public DiskBasicDirItem(DiskBasic basic) {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        num = 0;
        position = 0;
        sector = null;
        externalAttr = 0;
        flags = VISIBLE_LIST | VISIBLE_TREE;
        groups = new DiskBasicGroups();
    }

    /**
     * ディレクトリアイテムを作成 DATAはディスクイメージをアサイン
     *
     * @param basic   DISK BASIC
     * @param sector セクタ
     * @param secPos セクタ内の位置
     * @param data   セクタ内のディレクトリエントリ
     */
    public DiskBasicDirItem(DiskBasic basic, DiskImageSector sector, int secPos, byte[] data, int dataP) {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        num = 0;
        position = secPos;
        this.sector = sector;
        externalAttr = 0;
        flags = (VISIBLE_LIST | VISIBLE_TREE);
        groups = new DiskBasicGroups();
    }

    /**
     * ディレクトリアイテムを作成 DATAはディスクイメージをアサイン
     *
     * @param basic   DISK BASIC
     * @param num    通し番号
     * @param gItem  トラック番号などのデータ
     * @param sector セクタ
     * @param secPos セクタ内のディレクトリエントリの位置
     * @param data   セクタ内のディレクトリエントリ
     * @param next   次のセクタ
     * @param unuse  [out] 未使用か
     */
    public DiskBasicDirItem(DiskBasic basic, int num, DiskBasicGroupItem gItem, DiskImageSector sector, int secPos, byte[] data, int dataP, SectorParam next, boolean[] unuse) {
        this.basic = basic;
        this.type = basic.getType();
        parent = null;
        children = null;
        validDir = false;
        this.num = num;
        position = secPos;
        this.sector = sector;
        externalAttr = 0;
        flags = (VISIBLE_LIST | VISIBLE_TREE);
        groups = new DiskBasicGroups();
    }

    public void setData(int nNum, DiskBasicGroupItem gItem,
                        DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos,
                        SectorParam next /* = null */) throws IOException {
        num = nNum;
        position = sectorPos;
        this.sector = sector; // no duplicate
    }

    /** 子ディレクトリを作成 */
    public void createChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
    }

    /**
     * 子ディレクトリを追加
     *
     * @param newItem 新しいアイテム
     */
    public void addChild(DiskBasicDirItem<T> newItem) {
        createChildren();
        children.add(newItem);
    }

    public DiskBasicDirItem<T> getParent() {
        return parent;
    }

    public void setParent(DiskBasicDirItem<T> newitem) {
        parent = newitem;
    }

    public List<DiskBasicDirItem<T>> getChildren() {
        return children;
    }

    /** 子ディレクトリ一覧をクリア */
    public void emptyChildren() {
        if (children != null) {
            children.clear();
            children = null;
        }
        validDir = false;
    }

    public boolean isValidDirectory() {
        return validDir;
    }

    public void validDirectory(boolean val) {
        validDir = val;
    }

    public abstract boolean check(boolean[] last);

    /** ディレクトリアイテムのチェック */
    public static boolean checkData(byte[] buf, int len, boolean[] last) {
        byte prev = 0;
        boolean valid = false;
        for (int i = 0; i < len; i++) {
            if (buf[i] == 0 || buf[i] == (byte) 0xff) {
                valid = true;
                break;
            }
            if (i != 0 && buf[i] != prev) {
                valid = true;
                break;
            }
            prev = buf[i];
        }
        return valid;
    }

    /**
     * アイテムを削除できるか
     *
     * @return true 削除できる
     */
    public boolean isDeletable() {
        return getFileAttr().unmatchType(FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue(), FILE_TYPE_VOLUME_MASK.getValue());
    }

    public abstract boolean delete() throws IOException;

    public boolean hasEndMark() {
        return false;
    }

    public void setEndMark(DiskBasicDirItem<?> nextItem) throws IOException {
    }

    /** 内部変数などを再設定 */
    public void refresh() throws IOException {
        // フラグを更新
        used(checkUsed(false));

        // ファイルサイズを再計算
        calcFileSize();
    }

    /**
     * アイテムをロード・エクスポートできるか
     *
     * @return true ロードできる
     */
    public boolean isLoadable() {
        return isDeletable();
    }

    /**
     * アイテムをコピーできるか
     *
     * @return true コピーできる
     */
    public boolean isCopyable() {
        // ボリュームラベルは不可
        return getFileAttr().matchType(FILE_TYPE_DIRECTORY_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue(), 0);
    }

    /**
     * アイテムを上書きできるか
     *
     * @return true 上書きできる
     */
    public boolean isOverWritable() {
        // ディレクトリ、ボリュームラベルは不可
        return isCopyable();
    }

    /**
     * ファイル名を格納する位置を返す
     *
     * @param num  名前バッファ番号
     * @param size [out] バッファサイズ
     * @param len  [out] ファイル名として使えるサイズ
     * @return 格納先バッファポインタ
     */
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        size[0] = 0;
        len[0] = 0;
        return null;
    }

    /**
     * ファイル名を格納する位置を返す
     *
     * @param num 名前バッファ番号
     * @param len [out] ファイル名として使えるサイズ
     * @return 格納先バッファポインタ
     */
    protected byte[] getFileNamePos(int num, int[] len) {
        int[] size = new int[1];
        return getFileNamePos(num, size, len);
    }

    /**
     * 拡張子を格納する位置を返す
     *
     * @param len [out] バッファサイズ
     * @return 格納先バッファポインタ
     */
    protected byte[] getFileExtPos(int[] len) {
        len[0] = 0;
        return null;
    }

    protected int getFileType1() {
        return 0;
    }

    public int getFileType2() {
        return 0;
    }

    protected int getFileType3() {
        return 0;
    }

    protected abstract void setFileType1(int val);

    protected void setFileType2(int val) {
    }

    protected void setFileType3(int val) {
    }

    /**
     * ファイル名を設定
     * <p>
     * filename はデータビットが反転している場合あり
     *
     * @param filename [in,out] ファイル名
     * @param size     バッファサイズ
     * @param length   長さ
     */
    protected void setNativeName(byte[] filename, int size, int length) {
        int[] nl = new int[1];
        int[] ns = new int[1];
        byte[] n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            if (ns[0] > size) ns[0] = size;
            System.arraycopy(filename, 0, n, 0, ns[0]);
        }
    }

    /**
     * 拡張子を設定
     * <p>
     * fileext はデータビットが反転している場合あり
     *
     * @param fileExt [in,out] 拡張子
     * @param size    バッファサイズ
     * @param length  長さ
     */
    protected void setNativeExt(byte[] fileExt, int size, int length) {
        int[] el = new int[1];
        byte[] e = getFileExtPos(el);
        if (e != null && el[0] > 0) {
            if (el[0] > size) el[0] = size;
            System.arraycopy(fileExt, 0, e, 0, el[0]);
        }
    }

    /**
     * ファイル名(拡張子除く)を返す
     *
     * @return ファイル名
     */
    public String getFileNamePlainStr() {
        byte[] name = new byte[256];
        int[] nl = new int[1];
        int[] el = new int[1];
        nl[0] = name.length;
        el[0] = 0;
        getNativeFileName(name, nl, null, el);
        String dst = "";
        convCharsToString(name, nl[0]);
        return dst;
    }

    /**
     * 拡張子を返す
     *
     * @return 拡張子
     */
    protected String getFileExtPlainStr() {
        byte[] ext = new byte[256];
        int[] nl = new int[1];
        int[] el = new int[1];
        nl[0] = 0;
        el[0] = ext.length;
        getNativeFileName(null, nl, ext, el);
        String dst = "";
        convCharsToString(ext, el[0]);
        return dst;
    }

    /**
     * ファイル名を変換して内部ファイル名にする 検索用
     *
     * @param filename ファイル名 (Unicode)
     * @param name     [out] 内部ファイル名バッファ
     * @param nLen     [in,out] 上記バッファサイズ / 文字列長さを返す
     * @param ext      [out] 内部拡張子名バッファ
     * @param elen     [in,out] 上記バッファサイズ / 文字列長さを返す
     * @return true: OK, false: 変換できない文字がある
     */
    protected boolean toNativeFileName(String filename, byte[] name, int[] nLen, byte[] ext, int[] elen) {
        byte[] tmp = new byte[256];
        int[] nl = new int[1];
        int[] el = new int[1];
        String namestr = filename;
        if (basic.toUpperAfterRenamed()) {
            namestr = namestr.toUpperCase();
        }
        int tmplen = convStringToChars(namestr, tmp, tmp.length);
        if (tmplen < 0) return false;
        if (ext != null && elen[0] > 0) {
            int[] extLen = new int[1];
            getFileExtPos(extLen);
            elen[0] = extLen[0];
        }
        splitFileName(tmp, tmp.length, tmplen, name, nLen[0], nl, ext, elen[0], el, basic.getExtensionPreCode());
        nLen[0] = nl[0];
        elen[0] = el[0];
        return true;
    }

    /**
     * ファイルパスから内部ファイル名を生成する
     *
     * インポート時のダイアログを出す前
     * 拡張子がない機種では拡張子部分をファイル名としてそのまま設定
     *
     * @param filepath ファイルパス
     * @return ファイル名
     */
    protected String remakeFileNameAndExtStr(String filepath) {
        String newName = "";
        Path path = Paths.get(filepath);
        String filename = path.getFileName().toString();
        int[] nl = new int[1];
        int[] el = new int[1];
        for (int num = 0; num < 2; num++) {
            int[] n = new int[1];
            if (getFileNamePos(num, n) == null || n[0] == 0) {
                break;
            }
            nl[0] += n[0];
        }
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        el[0] = extLen[0];
        int lastDot = filename.lastIndexOf('.');
        if (el[0] == 0) {
            newName = filename.substring(0, Math.min(filename.length(), nl[0]));
        } else {
            String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
            String ext = lastDot >= 0 ? filename.substring(lastDot + 1) : "";
            newName = name.substring(0, Math.min(name.length(), nl[0]));
            if (!ext.isEmpty()) {
                newName += ".";
                newName += ext.substring(0, Math.min(ext.length(), el[0]));
            }
        }
        if (basic.toUpperBeforeDialog()) {
            newName = newName.toUpperCase();
        }
        convertFileNameBeforeImportDialog(newName);
        return newName;
    }

    /**
     * ファイルパスから内部ファイル名を生成する
     *
     * インポート時のダイアログを出す前
     * 拡張子がない機種では拡張子はとり除かれる
     *
     * @param filepath ファイルパス
     * @return ファイル名
     */
    protected String remakeFileNameOnlyStr(String filepath) {
        String newName = "";
        Path path = Paths.get(filepath);
        String filename = path.getFileName().toString();
        int[] nl = new int[1];
        int[] el = new int[1];
        int[] nameLen = new int[1];
        getFileNamePos(0, nameLen);
        nl[0] = nameLen[0];
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        el[0] = extLen[0];
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        String ext = lastDot >= 0 ? filename.substring(lastDot + 1) : "";
        newName = name.substring(0, Math.min(name.length(), nl[0]));
        if (el[0] > 0) {
            if (!ext.isEmpty()) {
                newName += (char) basic.getExtensionPreCode();
                newName += ext.substring(0, Math.min(ext.length(), el[0]));
            }
        }
        if (basic.toUpperBeforeDialog()) {
            newName = newName.toUpperCase();
        }
        convertFileNameBeforeImportDialog(newName);
        return newName;
    }

    /**
     * 属性から拡張子を付加する
     * <p>
     * エクスポートするファイルのファイル名に適用
     *
     * @param fileType ファイル属性
     * @param mask     ファイル属性ビットマスク
     * @param filename [in,out] ファイル名
     * @param dupli    同じ拡張子名の重複を許すか
     */
    protected void addExtensionByFileAttr(int fileType, int mask, String[] filename, boolean dupli /* = false */) {
        Path path = Paths.get(filename[0]);
        String ext = getFileExtension(path.getFileName().toString());
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext, fileType, mask);
        if (sa == null) {
            sa = findType(basic.getAttributesByExtension(), fileType, mask);
        }
        if (sa != null) {
            if (dupli || !ext.equalsIgnoreCase(sa.getName())) {
                filename[0] += ".";
                if (Utils.isUpperString(filename[0])) {
                    filename[0] += sa.getName().toUpperCase();
                } else {
                    filename[0] += sa.getName().toLowerCase();
                }
            }
        }
    }

    /**
     * 属性から拡張子を付加する
     * <p>
     * エクスポートするファイルのファイル名に適用
     *
     * @param fileType ファイル属性
     * @param mask     ファイル属性ビットマスク
     * @param filename [in,out] ファイル名
     * @param external 拡張属性 valueと比較
     * @param dupli    同じ拡張子名の重複を許すか
     */
    protected void addExtensionByFileAttr(int fileType, int mask, String filename, int external, boolean dupli) {
        Path path = Paths.get(filename);
        String ext = getFileExtension(path.getFileName().toString());
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext, fileType, mask);
        if (sa == null) {
            sa = find(basic.getAttributesByExtension(), fileType, mask, external);
        }
        if (sa == null) {
            sa = findType(basic.getAttributesByExtension(), fileType, mask);
        }
        if (sa != null) {
            if (dupli || !ext.equalsIgnoreCase(sa.getName())) {
                filename += ".";
                if (Utils.isUpperString(filename)) {
                    filename += sa.getName().toUpperCase();
                } else {
                    filename += sa.getName().toLowerCase();
                }
            }
        }
    }

    protected boolean getFileAttrName(int pos, Map<String, Object> list, String[] attr) {
        return getFileAttrName(pos, list, attr, -1);
    }

    /**
     * 属性の文字列を返す
     *
     * @param pos        位置(負のときは、外部定義からさがす)
     * @param list       名＆値リスト
     * @param defaultPos 一致しないとき設定するデフォルト名のある位置(-1のときは設定しない)
     * @param attr       [out] 一致した名前
     * @return リストに一致したらtrue
     */
    protected boolean getFileAttrName(int pos, Map<String, Object> list, String[] attr, int defaultPos) {
        boolean match = true;
        if (pos >= 0) {
            attr[0] = Utils.keyAt(list, pos);
        } else {
            MyAttribute sa = findValue(basic.getSpecialAttributes(), -pos);
            if (sa != null) {
                attr[0] = sa.getName();
            } else {
                match = false;
                if (defaultPos >= 0) {
                    attr[0] = Utils.keyAt(list, defaultPos);
                }
            }
        }
        return match;
    }

    /**
     * 拡張子から属性を決定できるか 決定できる場合、末尾の拡張子をとり除く
     *
     * インポートするファイルのファイル名に適用
     *
     * <pre> For Example:
     *  foobar.aaa.bas (is basic file) -> foobar.aaa (trimming last extension)
     *  foobar.bas (is basic file) -> foobar (trimming last extension)
     *  foobar.bbb.ccc (is unknown attr) -> foobar.bbb.ccc (no change filename)
     * </pre>
     */
    protected boolean trimExtensionByExtensionAttr(String[] filename) {
        String ext = getFileExtension(filename[0]);
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext);
        if (sa != null) {
            filename[0] = removeExtension(filename[0]);
        }
        return sa != null;
    }

    /**
     * 拡張子から属性を決定できるか
     *
     * 決定できる場合、拡張子が２つつながっている場合は末尾の拡張子をとり除く
     *
     * インポートするファイルのファイル名に適用
     *
     * <pre>
     *  For Example:
     *  foobar.aaa.bas (is basic file) -> foobar.aaa (trimming last extension)
     *  foobar.bas (is basic file) -> foobar.bas (no change filename)
     *  foobar.bbb.ccc (is unknown attr) -> foobar.bbb.ccc (no change filename)
     * </pre>
     */
    protected boolean trimLastExtensionByExtensionAttr(String filename) {
        String ext = getFileExtension(filename);
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext);
        if (sa != null) {
            String name = removeExtension(filename);
            String subext = getFileExtension(name);
            if (!subext.isEmpty()) {
                filename = name;
            }
        }
        return sa != null;
    }

    /**
     * 拡張子から属性を決定できるか
     * <p>
     * 決定できる場合、拡張子が２つつながっている場合は末尾の拡張子をとり除く
     * <p>
     * インポートするファイルのファイル名に適用
     * <pre>
     *  For Example:
     *  foobar.aaa.bas (is basic file) -> foobar.aaa (trimming last extension)
     *  foobar.bas (is basic file) -> foobar.bas (no change filename)
     *  foobar.bbb.ccc (is unknown attr) -> foobar.bbb.ccc (no change filename)
     * </pre>
     *
     * @param filename  ファイル名
     * @param list      拡張子名＆値リスト
     * @param listFirst リストの最初の位置
     * @param listLast  リストの最後の位置
     * @param outfile   [out] 編集後のファイル名
     * @param attr      [out] 属性
     * @param pos       [out] リストの位置 (ユーザ指定属性の場合はマイナスになる)
     */
    protected boolean trimLastExtensionByExtensionAttr(String filename, Map<String, Object> list, int listFirst, int listLast, String[] outfile, int[] attr, int[] pos) {
        boolean match = false;
        int t1 = 0;
        int p1 = -1;
        String ext = getFileExtension(filename).toUpperCase();
        int idx = Utils.indexOf(list, ext);
        if (idx >= listFirst && idx <= listLast) {
            match = true;
            t1 = (int) list.get(idx);
            p1 = idx;
        } else {
            MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), ext);
            if (sa != null) {
                match = true;
                t1 = sa.getType();
            }
        }
        if (match) {
            if (outfile != null) outfile[0] = removeExtension(filename);
            if (attr != null) attr[0] = t1;
            if (pos != null) pos[0] = p1;
        }
        return match;
    }

    /**
     * 拡張子から属性を決定できるか 決定できる場合、末尾の拡張子をとり除く
     * <p>
     * インポートするファイルのファイル名に適用
     *
     * <pre> For Example:
     *  foobar.aaa.bas (is basic file) -> foobar.aaa (trimming last extension)
     *  foobar.bas (is basic file) -> foobar (trimming last extension)
     *  foobar.bbb.ccc (is unknown attr) -> foobar.bbb.ccc (no change filename)
     * </pre>
     *
     * @param filename  ファイル名
     * @param list      拡張子名＆値リスト
     * @param listFirst リストの最初の位置
     * @param listLast  リストの最後の位置
     * @param outfile   [out] 編集後のファイル名
     * @param attr      [out] 属性
     * @param pos       [out] リストの位置 (ユーザ指定属性の場合はマイナスになる)
     */
    protected boolean isContainAttrByExtension(String filename, Map<String, Object> list, int listFirst, int listLast, String[] outfile, int[] attr, int[] pos) {
        boolean match = false;
        int t1 = 0;
        int p1 = -1;
        String ext = getFileExtension(filename).toUpperCase();
        int idx = Utils.indexOf(list, ext);
        if (idx >= listFirst && idx <= listLast) {
            match = true;
            t1 = (int) Utils.valueAt(list, idx);
            p1 = idx;
        } else {
            MyAttribute sa = findUpperCase(basic.getSpecialAttributes(), ext);
            if (sa != null) {
                match = true;
                t1 = sa.getValue();
            } else {
                sa = findUpperCase(basic.getAttributesByExtension(), ext);
                if (sa != null) {
                    match = true;
                    t1 = sa.getValue();
                }
            }
        }
        if (match) {
            if (outfile != null) outfile[0] = removeExtension(filename);
            if (attr != null) attr[0] = t1;
            if (pos != null) pos[0] = p1;
        }
        return match;
    }

    /**
     * 属性の選択肢を作成する（プロパティダイアログ用）
     *
     * @param basic    DiskBasic
     * @param list     拡張子名＆値リスト
     * @param endPos   リストの最終位置
     * @param fileType 属性値(optional)
     *                 属性値を設定した場合、属性値がリストに一致しなければそれを選択肢に追加する。
     * @param types    [out] 選択肢リスト
     */
    public static void createChoiceForAttrDialog(DiskBasic basic, Map<String, Object> list, int endPos, List<String> types, int fileType) {
        boolean matchValue = false;
        for (Map.Entry<String, Object> e : list.entrySet()) {
            types.add(e.getKey());
            if (fileType >= 0 && (int) e.getValue() == fileType) {
                matchValue = true;
            }
        }
        List<MyAttribute> attrs = basic.getSpecialAttributes();
        for (MyAttribute attr : attrs) {
            String str = attr.getName() + String.format(" 0x%02x", attr.getValue());
            if (!attr.getDescription().isEmpty()) {
                str += " (" + attr.getDescription() + ")";
            }
            types.add(str);
            if (fileType >= 0 && attr.getValue() == fileType) {
                matchValue = true;
            }
        }
        if (fileType >= 0 && !matchValue) {
            types.add(String.format("0x%02x", fileType));
        }
    }

    /**
     * 属性の選択肢を選ぶ（プロパティダイアログ用）
     *
     * @param basic      DiskBasic
     * @param selPos     選択位置(負の場合は、外部設定の属性位置)
     * @param endPos     リストの最終位置
     * @param unknownPos "不明"の位置
     * @return 選択肢の位置
     */
    public static int selectChoiceForAttrDialog(DiskBasic basic, int selPos, int endPos, int unknownPos) {
        if (selPos < 0) {
            List<MyAttribute> attrs = basic.getSpecialAttributes();
            int nType = getIndexByValue(attrs, -selPos);
            if (nType >= 0) {
                selPos = nType + endPos;
            }
        }
        if (selPos < 0) {
            selPos = unknownPos;
        }
        return selPos;
    }

    /** リストの位置から属性を返す(プロパティダイアログ用) */
    public static int calcSpecialOriginalTypeFromPos(DiskBasic basic, int pos, int endPos) {
        int val = -1;
        if (pos >= endPos) {
            pos -= endPos;
            List<MyAttribute> attrs = basic.getSpecialAttributes();
            int count = attrs.size();
            if (pos < count) {
                val = getValueByIndex(attrs, pos);
            }
        }
        return val;
    }

    /** リストの位置から属性を返す(プロパティダイアログ用) */
    public static int calcSpecialFileTypeFromPos(DiskBasic basic, int pos, int endPos) {
        int t = 0;
        if (pos >= endPos) {
            pos -= endPos;
            List<MyAttribute> attrs = basic.getSpecialAttributes();
            int count = attrs.size();
            if (pos < count) {
                t = getTypeByIndex(attrs, pos);
            }
        }
        return t;
    }

    /** ファイル名の通常コードの割合(0.0-1.0) */
    public double normalCodesInFileName() {
        int count = 0;
        boolean invert = basic.isDataInverted();
        int[] l = new int[1];
        byte[] n = getFileNamePos(0, l);
        for (int i = 0; i < l[0]; i++) {
            byte c = n[i];
            if (invert) {
                c = (byte) (~c);
            }
            if (c >= 0x20) {
                count++;
            }
        }
        return l[0] > 0 ? (double) count / (double) l[0] : 0.0;
    }

    public boolean isFileNameEditable() {
        return true;
    }

    /**
     * ファイル名を設定 "."で拡張子と分離
     *
     * @param filename ファイル名(Unicode)
     */
    public void setFileNameStr(String filename) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nlen = new int[] {name.length};
        int[] elen = new int[] {ext.length};
        toNativeFileName(filename, name, nlen, ext, elen);
        setNativeFileName(name, name.length, nlen[0], ext, ext.length, elen[0]);
    }

    /**
     * ファイル名をそのまま設定
     * 拡張子部分はクリアする
     *
     * @param filename ファイル名(Unicode)
     */
    public void setFileNamePlain(String filename) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nlen = new int[] {name.length};
        int[] elen = new int[] {0};
        toNativeFileName(filename, name, nlen, null, elen);
        setNativeFileName(name, name.length, nlen[0], ext, ext.length, 0);
    }

    /**
     * 拡張子を設定
     *
     * @param fileExt 拡張子(Unicode)
     */
    public void setFileExtPlain(String fileExt) {
        byte[] ext = new byte[256];
        int[] nlen = new int[] {0};
        int[] elen = new int[] {ext.length};
        toNativeFileName(fileExt, ext, elen, null, nlen);
        setNativeFileName(null, 0, 0, ext, ext.length, elen[0]);
    }

    /**
     * ファイル名と拡張子を設定
     *
     * @param name  [in,out] ファイル名
     * @param nSize ファイル名バッファサイズ
     * @param nLen  ファイル名長さ
     * @param ext   [in,out] 拡張子
     * @param eSize 拡張子バッファサイズ
     * @param eLen  拡張子長さ
     *              データビットはこの関数で反転させる
     */
    public void setNativeFileName(byte[] name, int nSize, int nLen, byte[] ext, int eSize, int eLen) {
        boolean invert = basic.isDataInverted();
        char space = (char) basic.getDirSpaceCode(); // 空白コード
        char term = (char) basic.getDirTerminateCode(); // 終端コード
        if (name != null) {
            if (nSize > nLen) {
                name[nLen] = (byte) term;
                nLen++;
            }
            if (nSize > nLen) {
                Arrays.fill(name, nLen, nSize, (byte) space);
            }
            if (invert) {
                Common.invertMemory(name, nSize);
            }
            setNativeName(name, nSize, nLen);
        }
        if (ext != null) {
            if (eSize > eLen) {
                ext[eLen] = (byte) term;
                eLen++;
            }
            if (eSize > eLen) {
                Arrays.fill(ext, eLen, eSize, (byte) space);
            }
            if (invert) {
                Common.invertMemory(ext, eSize);
            }
            setNativeExt(ext, eSize, eLen);
        }
    }

    /**
     * ファイル名をコピー
     *
     * @param src ディレクトリアイテム
     */
    public void copyFileName(DiskBasicDirItem<T> src) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nLen = new int[] {name.length};
        int[] eLen = new int[1];
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        eLen[0] = extLen[0];
        src.getNativeFileName(name, nLen, ext, eLen);
        setNativeFileName(name, name.length, nLen[0], ext, ext.length, eLen[0]);
    }

    /**
     * ファイル名を返す 名前 + "." + 拡張子
     *
     * @return ファイル名
     */
    public String getFileNameStr() {
        byte[] name = new byte[256], ext = new byte[256];
        int[] nl = new int[] {name.length};
        int[] el = new int[] {ext.length};

        getNativeFileName(name, nl, ext, el);

        String dst = convCharsToString(name, nl[0]);

        if (el[0] > 0) {
            dst += (char) basic.getExtensionPreCode();
            dst += convCharsToString(ext, el[0]);
        }

        return dst;
    }

    /**
     * ファイル名を返す 名前 + "." + 拡張子 エクスポート時
     *
     * @return ファイル名
     */
    public String getFileNameStrForExport() {
        byte[] name = new byte[256], ext = new byte[256];
        int[] nl = new int[] {name.length};
        int[] el = new int[] {ext.length};

        getNativeFileName(name, nl, ext, el);

        String dst = convCharsToString(name, nl[0]);

        if (el[0] > 0) {
            dst += ".";
            dst += convCharsToString(ext, el[0]);
        }

        return dst;
    }

    /**
     * ファイル名を得る 名前 + "." + 拡張子
     * バッファを超える場合は拡張子を追加しない
     *
     * @param filename 出力先バッファ
     * @param length   出力先バッファのサイズ
     */
    public void getFileName(byte[] filename, int length) {
        byte[] name = new byte[256];
        byte[] ext = new byte[256];
        int[] nl = new int[] {name.length};
        int[] el = new int[] {ext.length};
        getNativeFileName(name, nl, ext, el);
        System.arraycopy(name, 0, filename, 0, Math.min(length, nl[0]));
        if (el[0] > 0 && (nl[0] + el[0] + 1) < length) {
            filename[nl[0]] = basic.getExtensionPreCode();
            nl[0]++;
            System.arraycopy(ext, 0, filename, nl[0], el[0]);
        }
    }

    /**
     * ファイル名を得る
     * <p>
     * データビットは反転させたまま
     *
     * @param filename [in,out] ファイル名
     * @param size     バッファサイズ
     * @param length   [out] 長さ
     */
    protected void getNativeName(byte[] filename, int size, int[] length) {
        int[] s = new int[1];
        int[] l = new int[1];
        byte[] n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            if (s[0] > size) s[0] = size;
            System.arraycopy(n, 0, filename, 0, s[0]);
        }
        length[0] = l[0];
    }

    /**
     * 拡張子を得る
     * <p>
     * データビットは反転させたまま
     *
     * @param fileExt [in,out] 拡張子
     * @param size    バッファサイズ
     * @param length  [out] 長さ
     */
    protected void getNativeExt(byte[] fileExt, int size, int[] length) {
        int[] l = new int[1];
        byte[] e = getFileExtPos(l);
        if (e != null && l[0] > 0) {
            System.arraycopy(e, 0, fileExt, 0, l[0]);
        }
        length[0] = l[0];
    }

    /**
     * ファイル名と拡張子を得る
     * それぞれ空白は右トリミング
     *
     * @param name [out] ファイル名バッファ
     * @param nLen [in,out] 上記バッファサイズ / 文字列長さを返す
     * @param ext  [out] 拡張子名バッファ
     * @param eLen [in,out] 上記バッファサイズ / 文字列長さを返す
     *             データビットはこの関数で反転させる
     */
    public void getNativeFileName(byte[] name, int[] nLen, byte[] ext, int[] eLen) {
        int[] nl = new int[1];
        int[] el = new int[1];
        boolean invert = basic.isDataInverted();
        char trim = (char) basic.getDirTrimmingCode(); // とり除くコード
        char space = (char) basic.getDirSpaceCode(); // 空白コード
        char term = (char) basic.getDirTerminateCode(); // 終端コード
        if (name != null && nLen[0] > 0) {
            Arrays.fill(name, 0, nLen[0], (byte) 0);
            getNativeName(name, nLen[0], nl);
            if (invert) {
                Common.invertMemory(name, nl[0]);
            }
            nl[0] = Common.trimRight(name, nl[0], (byte) trim);
            nl[0] = Common.trimRight(name, nl[0], (byte) space);
            nl[0] = Common.trimRight(name, nl[0], (byte) term);
            if (isUsed()) nLen[0] = Common.shrinkString(name, nl[0]);
            else nLen[0] = nl[0];
        }
        if (ext != null && eLen[0] > 0) {
            Arrays.fill(ext, 0, eLen[0], (byte) 0);
            getNativeExt(ext, eLen[0], el);
            if (invert) {
                Common.invertMemory(ext, el[0]);
            }
            el[0] = Common.trimRight(ext, el[0], (byte) trim);
            el[0] = Common.trimRight(ext, el[0], (byte) space);
            el[0] = Common.trimRight(ext, el[0], (byte) term);
            if (isUsed()) eLen[0] = Common.shrinkString(ext, el[0]);
            else eLen[0] = el[0];
        }
    }

    /**
     * ファイル名(拡張子除く)が一致するか
     *
     * @param name  ファイル名
     * @param caseInsensitive 大文字小文字を区別しないか(case insensitive)
     */
    public boolean isSameName(String name, boolean caseInsensitive) {
        if (!isUsedAndVisible()) return false;
        byte[] sName = new byte[256];
        byte[] dName = new byte[256];
        int[] sNLen = new int[] {sName.length};
        int[] dNLen = new int[] {dName.length};
        int[] sELen = new int[] {0};
        int[] dELen = new int[] {0};
        toNativeFileName(name, dName, dNLen, null, dELen);
        getNativeFileName(sName, sNLen, null, sELen);
        if (caseInsensitive) {
            toUpper(sName, sNLen[0]);
            toUpper(dName, dNLen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sName, Math.max(sNLen[0], dNLen[0])), Arrays.copyOf(dName, Math.max(sNLen[0], dNLen[0])));
    }

    /**
     * 同じファイル名か
     * ファイル名＋拡張子＋拡張属性で一致するかどうか
     *
     * @param filename ファイル名
     * @param caseInsensitive    大文字小文字を区別しないか(case insensitive)
     * @see #getOptionalName()
     */
    public boolean isSameFileName(DiskBasicFileName filename, boolean caseInsensitive) {
        if (!isUsedAndVisible()) return false;
        byte[] sName = new byte[256];
        byte[] sExt = new byte[256];
        byte[] dName = new byte[256];
        byte[] dExt = new byte[256];
        int[] sNLen = new int[] {sName.length};
        int[] ELen = new int[] {sExt.length};
        int[] dNLen = new int[] {dName.length};
        int[] dELen = new int[] {dExt.length};
        toNativeFileName(filename.getName(), dName, dNLen, dExt, dELen);
        getNativeFileName(sName, sNLen, sExt, ELen);
        if (caseInsensitive) {
            toUpper(sName, sNLen[0]);
            toUpper(sExt, ELen[0]);
            toUpper(dName, dNLen[0]);
            toUpper(dExt, dELen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sName, Math.max(sNLen[0], dNLen[0])), Arrays.copyOf(dName, Math.max(sNLen[0], dNLen[0]))) &&
                ((dELen[0] == 0 && ELen[0] == 0) || Arrays.equals(Arrays.copyOf(sExt, Math.max(ELen[0], dELen[0])), Arrays.copyOf(dExt, Math.max(ELen[0], dELen[0])))) &&
                (getOptionalName() == filename.getOptional());
    }

    /**
     * 同じファイル名か
     * ファイル名＋拡張子＋拡張属性で一致するかどうか
     *
     * @param src   比較対象
     * @param iCase 大文字小文字を区別しないか(case insensitive)
     * @see #getOptionalName()
     */
    public boolean isSameFileName(DiskBasicDirItem<T> src, boolean iCase) {
        if (!isUsedAndVisible()) return false;
        byte[] sName = new byte[256];
        byte[] sExt = new byte[256];
        byte[] dName = new byte[256];
        byte[] dExt = new byte[256];
        int[] sNLen = new int[] {sName.length};
        int[] sELen = new int[] {sExt.length};
        int[] dNLen = new int[] {dName.length};
        int[] dELen = new int[] {dExt.length};
        src.getNativeFileName(dName, dNLen, dExt, dELen);
        getNativeFileName(sName, sNLen, sExt, sELen);
        if (iCase) {
            toUpper(sName, sNLen[0]);
            toUpper(sExt, sELen[0]);
            toUpper(dName, dNLen[0]);
            toUpper(dExt, dELen[0]);
        }
        return Arrays.equals(Arrays.copyOf(sName, Math.max(sNLen[0], dNLen[0])), Arrays.copyOf(dName, Math.max(sNLen[0], dNLen[0]))) &&
                ((dELen[0] == 0 && sELen[0] == 0) || Arrays.equals(Arrays.copyOf(sExt, Math.max(sELen[0], dELen[0])), Arrays.copyOf(dExt, Math.max(sELen[0], dELen[0])))) &&
                (src.getOptionalName() == getOptionalName());
    }

    /**
     * 小文字を大文字にする
     *
     * @param str  [in,out] 文字列
     * @param size 長さ
     */
    public static void toUpper(byte[] str, int size) {
        Common.toUpper(str, size);
    }

    /**
     * ファイル名＋拡張子のサイズ
     *
     * @return サイズ
     */
    public int getFileNameStrSize() {
        int nl = 0;
        int el = 0;
        int[] l = new int[1];
        int num = 0;
        do {
            l[0] = 0;
            getFileNamePos(num, l);
            if (l[0] == 0) break;
            nl += l[0];
            num++;
        } while (l[0] > 0);
        int[] extLen = new int[1];
        getFileExtPos(extLen);
        el = extLen[0];
        if (el > 0) el++;
        return nl + el;
    }

    public void convertFileNameBeforeImportDialog(String filename) {
    }

    public void setOptionalName(int val) {
    }

    public int getOptionalName() {
        return 0;
    }

    /**
     * 文字列をバッファにコピー
     *
     * @param src   ファイル名
     * @param sSize ファイル名サイズ
     * @param sLen  ファイル名長さ
     * @param dst   [out] 出力先バッファ
     * @param dSize 上記バッファサイズ
     * @param dLen  [out] 上記長さ
     */
    public static void memoryCopy(byte[] src, int sSize, int sLen, byte[] dst, int dSize, int[] dLen) {
        if (dst == null || dSize == 0) {
            dLen[0] = 0;
            return;
        }
        if (dSize > sLen) Arrays.fill(dst, sLen, dSize, (byte) 0);
        if (sLen > 0) System.arraycopy(src, 0, dst, 0, sLen);
        dLen[0] = sLen;
    }

    /**
     * 文字列をバッファにコピー spChr(通常".")で拡張子とを分ける
     *
     * @param src    ファイル名
     * @param sSize  ファイル名サイズ
     * @param sLen   ファイル名長さ
     * @param dName  [out] 出力先ファイル名バッファ
     * @param dNSize 上記ファイル名バッファサイズ
     * @param dNLen  [out] 上記ファイル名長さ
     * @param dExt   [out] 出力先拡張子バッファ
     * @param dESize 上記拡張子バッファサイズ
     * @param dELen  [out] 上記拡張子長さ
     * @param spChr  分割に使用する文字
     */
    public static void splitFileName(byte[] src, int sSize, int sLen, byte[] dName, int dNSize, int[] dNLen, byte[] dExt, int dESize, int[] dELen, byte spChr) {
        int pos = -1;
        for (int i = sLen - 1; i >= 0; i--) {
            if (src[i] == spChr) {
                pos = i;
                break;
            }
        }
        if (dESize > 0 && pos >= 0) {
            memoryCopy(src, sSize, pos, dName, dNSize, dNLen);
            pos++;
            memoryCopy(Arrays.copyOfRange(src, pos, src.length), sSize - pos, sLen - pos, dExt, dESize, dELen);
        } else {
            memoryCopy(src, sSize, sLen, dName, dNSize, dNLen);
            memoryCopy(null, 0, 0, dExt, dESize, dELen);
        }
    }

    /**
     * 属性を設定
     *
     * @param fileType 属性
     */
    public void setFileAttr(DiskBasicFileType fileType) {
    }

    public void setFileAttr(DiskBasicFormatType formatType, int fileType, int originalType0) {
        setFileAttr(formatType, fileType, originalType0, 0, 0);
    }

    public void setFileAttr(DiskBasicFormatType formatType, int fileType, int originalType0, int originalType1) {
        setFileAttr(formatType, fileType, originalType0, originalType1, 0);
    }

    /**
     * 属性を設定
     *
     * @param formatType    フォーマット種類 DiskBasicFormatType
     * @param fileType      共通属性 file_type_mask の組み合わせ
     * @param originalType0 本来の属性
     * @param originalType1 本来の属性 つづき1
     * @param originalType2 本来の属性 つづき2
     */
    public void setFileAttr(DiskBasicFormatType formatType, int fileType, int originalType0, int originalType1, int originalType2) {
        setFileAttr(new DiskBasicFileType(formatType, fileType, originalType0, originalType1, originalType2));
    }

    /**
     * 属性を返す
     *
     * @return 属性
     */
    public DiskBasicFileType getFileAttr() {
        return new DiskBasicFileType();
    }

    /**
     * 属性の文字列を返す(ファイル一覧画面表示用)
     *
     * @return 文字列
     */
    public String getFileAttrStr() {
        return "";
    }


    public void setExternalAttr(int val) {
        externalAttr = val;
    }

    public int getExternalAttr() {
        return externalAttr;
    }

    /**
     * 通常のファイルか ディレクトリ削除でのチェックで使用
     *
     * @return true 通常のファイル
     */
    public boolean isNormalFile() {
        DiskBasicFileType attr = getFileAttr();
        if (attr.isVolume()) {
            return false;
        } else if (attr.isDirectory()) {
            String name = getFileNameStr();
            if (name.equals(".") || name.equals("..")) {
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    /**
     * ディレクトリか
     *
     * @return true ディレクトリ
     */
    public boolean isDirectory() {
        return getFileAttr().isDirectory();
    }

    /**
     * ファイルサイズをセット
     *
     * @param val サイズ
     */
    public void setFileSize(int val) {
        groups.setSize(val);
    }

    /**
     * ファイルサイズを返す
     *
     * @return サイズ
     */
    public int getFileSize() {
        return groups.getSize();
    }

    /**
     * ディレクトリサイズをセット
     *
     * @param val サイズ
     */
    public void setDirectorySize(int val) {
        setFileSize(val);
    }

    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     *
     * @param groupItems [out] グループリスト
     */
    public void getAllGroups(DiskBasicGroups groupItems) throws IOException {
        groupItems.clear();
        getUnitGroups(0, groupItems);
    }

    /**
     * グループ数をセット
     *
     * @param val 数
     */
    public void setGroupSize(int val) {
        groups.setNums(val);
    }

    /**
     * グループ数を返す
     *
     * @return 数
     */
    public int getGroupSize() {
        return groups.getNums();
    }


    public void setStartGroup(int fileunitNum, int val) {
        setStartGroup(fileunitNum, val, 0);
    }

    /**
     * 最初のグループ番号をセット
     *
     * @param fileUnitNum ファイル番号
     * @param val         番号
     * @param size        サイズ(機種依存)
     */
    public void setStartGroup(int fileUnitNum, int val, int size) {
    }

    /**
     * 最初のグループ番号をセット
     *
     * @param fileUnitNum ファイル番号
     */
    public int getStartGroup(int fileUnitNum) {
        return 0;
    }

    /**
     * 追加のグループ番号をセット(機種依存)
     *
     * @param val 番号
     */
    public void setExtraGroup(int val) {
    }

    /**
     * 追加のグループ番号を返す(機種依存)
     *
     * @return 番号
     */
    public int getExtraGroup() {
        return INVALID_GROUP_NUMBER;
    }

    /** 追加のグループリストをセット(機種依存) */
    public void setExtraGroups(DiskBasicGroups grps) {
    }

    /** 追加のグループ番号を得る(機種依存) */
    public void getExtraGroups(List<Integer> arr) {
    }

    /** 追加のグループリストを返す(機種依存) */
    public void getExtraGroups(DiskBasicGroups[] grps) {
    }

    /**
     * 次のグループ番号をセット(機種依存)
     *
     * @param val 番号
     */
    public void setNextGroup(int val) {
    }

    /** 次のグループ番号を返す(機種依存) */
    public int getNextGroup() {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * 最後のグループ番号をセット(機種依存)
     *
     * @param val 番号
     */
    public void setLastGroup(int val) {
    }

    /**
     * 最後のグループ番号を返す(機種依存)
     *
     * @return 番号
     */
    public int getLastGroup() {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * 親のグループ番号をセット(機種依存)
     *
     * @param val 番号
     */
    public void setParentGroup(int val) {
    }

    /**
     * 親のグループ番号を返す(機種依存)
     *
     * @return 番号
     */
    public int getParentGroup() {
        return INVALID_GROUP_NUMBER;
    }

    /** グループリストの数を返す */
    public int getGroupCount() {
        return groups.size();
    }

    /** グループリストを返す */
    public DiskBasicGroups getGroups() {
        return groups;
    }

    /** グループリストを設定 */
    public void setGroups(DiskBasicGroups vals) {
        groups = vals;
    }

    /** グループリストのアイテムを返す */
    public DiskBasicGroupItem getGroup(int index) {
        return groups.get(index);
    }

    public void clearChainSector(DiskBasicDirItem<T> pItem /* = null */) throws IOException {
    }

    public void setChainSector(DiskImageSector sector, byte[] data, DiskBasicDirItem<T> pItem /* = null */) throws IOException {
    }

    public void setChainSector(DiskImageSector sector, int num, byte[] data, DiskBasicDirItem<T> pItem /* = null */) throws IOException {
    }

    public void setChainSector(int num, int pos, byte[] data, DiskBasicDirItem<T> pitem /* = null */) {
    }

    public void addChainGroupNumber(int idx, int val) {
    }

    public boolean hasCreateDateTime() {
        return false;
    }

    public boolean hasCreateDate() {
        return false;
    }

    public boolean hasCreateTime() {
        return false;
    }

    public boolean hasModifyDateTime() {
        return false;
    }

    public boolean hasModifyDate() {
        return false;
    }

    public boolean hasModifyTime() {
        return false;
    }

    public boolean hasAccessDateTime() {
        return false;
    }

    public boolean hasAccessDate() {
        return false;
    }

    public boolean hasAccessTime() {
        return false;
    }

    public static final int DATETIME_NONE = 0;
    public static final int DATETIME_CREATE = 0x01;
    public static final int DATETIME_MODIFY = 0x02;
    public static final int DATETIME_CREATE_MODIFY = 0x03;
    public static final int DATETIME_ACCESS = 0x04;
    public static final int DATETIME_CREATE_ACCESS = 0x05;
    public static final int DATETIME_MODIFY_ACCESS = 0x06;
    public static final int DATETIME_ALL = 0x07;

    public int canIgnoreDateTime() {
        return DATETIME_NONE;
    }

    /**
     * 作成日付を得る
     *
     * @param tm [out] 日付
     */
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        return tm.toLocalDate();
    }

    /**
     * 作成時間を得る
     *
     * @param tm [out] 時間
     */
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        return tm.toLocalTime();
    }

    /**
     * 作成日時を得る
     *
     * @param tm [out] 日時
     */
    public void getFileCreateDateTime(LocalDateTime tm) {
        getFileCreateDate(tm);
        getFileCreateTime(tm);
    }

    /** 作成日時を返す */
    public LocalDateTime getFileCreateDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateDateTime(tm);
        return tm;
    }

    /**
     * ファイルの作成日付を文字列にして返す
     *
     * @return 日付文字列
     */
    @Deprecated
    public String getFileCreateDateStr() {
        return "";
    }

    /**
     * ファイルの作成時間を文字列にして返す
     *
     * @return 時間文字列
     */
    @Deprecated
    public String getFileCreateTimeStr() {
        return "";
    }

    /**
     * ファイルの作成日時を文字列にして返す
     *
     * @return 日時文字列 ない場合"---"
     */
    @Deprecated
    public String getFileCreateDateTimeStr() {
        String str = getFileCreateDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileCreateTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    /**
     * 変更日付を得る
     *
     * @param tm [out] 日付
     */
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        return tm.toLocalDate();
    }

    /**
     * 変更時間を得る
     *
     * @param tm [out] 時間
     */
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        return tm.toLocalTime();
    }

    /**
     * 変更日時を得る
     *
     * @param tm [out] 日時
     */
    public void getFileModifyDateTime(LocalDateTime tm) {
        getFileModifyDate(tm);
        getFileModifyTime(tm);
    }

    /** 変更日時を返す */
    public LocalDateTime getFileModifyDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileModifyDateTime(tm);
        return tm;
    }

    /**
     * 変更日付のタイトル名（ダイアログ用）
     *
     * @return タイトル文字列
     */
    @Deprecated
    public String getFileModifyDateStr() {
        return "";
    }

    /**
     * ファイルの変更日付を文字列にして返す
     *
     * @return 日付文字列
     */
    @Deprecated
    public String getFileModifyTimeStr() {
        return "";
    }

    /**
     * ファイルの変更日時を文字列にして返す
     *
     * @return 日時文字列 ない場合"---"
     */
    @Deprecated
    public String getFileModifyDateTimeStr() {
        String str = getFileModifyDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileModifyTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    /**
     * アクセス日付を得る
     *
     * @return 日付
     */
    public LocalDate getFileAccessDate() {
        return LocalDate.now();
    }

    /**
     * アクセス時間を得る
     *
     * @return 時間
     */
    public LocalTime getFileAccessTime() {
        return LocalTime.now();
    }

    /**
     * アクセス日時を得る
     *
     * @param tm [out] 日時
     */
    public void getFileAccessDateTime(LocalDateTime tm) {
        getFileAccessDate();
        getFileAccessTime();
    }

    /** アクセス日時を返す */
    public LocalDateTime getFileAccessDateTime() {
        LocalDateTime tm = LocalDateTime.now();
        getFileAccessDateTime(tm);
        return tm;
    }

    /**
     * ファイルのアクセス日付を文字列にして返す
     *
     * @return 日付文字列
     */
    @Deprecated
    public String getFileAccessDateStr() {
        return "";
    }

    /**
     * ファイルのアクセス時間を文字列にして返す
     *
     * @return 時間文字列
     */
    @Deprecated
    public String getFileAccessTimeStr() {
        return "";
    }

    /**
     * ファイルのアクセス日時を文字列にして返す
     *
     * @return 日時文字列 ない場合"---"
     */
    @Deprecated
    public String getFileAccessDateTimeStr() {
        String str = getFileAccessDateStr();
        if (!str.isEmpty()) str += " ";
        str += getFileAccessTimeStr();
        if (str.isEmpty()) str += "---";
        return str;
    }

    /**
     * 作成日時をセット
     *
     * @param tm 日時
     */
    public void setFileCreateDate(LocalDateTime tm) {
    }

    public void setFileCreateTime(LocalDateTime tm) {
    }

    public void setFileCreateDateTime(LocalDateTime tm) {
        setFileCreateDate(tm);
        setFileCreateTime(tm);
    }

    /**
     * 作成日付のタイトル名（ダイアログ用）
     *
     * @return タイトル文字列
     */
    public String getFileCreateDateTimeTitle() {
        return "Created Date";
    }

    /**
     * 変更日時をセット
     *
     * @param tm 日時
     */
    public void setFileModifyDate(LocalDateTime tm) {
    }

    public void setFileModifyTime(LocalDateTime tm) {
    }

    public void setFileModifyDateTime(LocalDateTime tm) {
        setFileModifyDate(tm);
        setFileModifyTime(tm);
    }

    public String getFileModifyDateTimeTitle() {
        return "Modified Date";
    }

    public void setFileAccessDate(LocalDateTime tm) {
    }

    public void setFileAccessTime(LocalDateTime tm) {
    }

    /**
     * アクセス日時をセット
     *
     * @param tm 日時
     */
    public void setFileAccessDateTime(LocalDateTime tm) {
        setFileAccessDate(tm);
        setFileAccessTime(tm);
    }

    /**
     * アクセス日付のタイトル名（ダイアログ用）
     *
     * @return タイトル文字列
     */
    @Deprecated
    public String getFileAccessDateTimeTitle() {
        return "Accessed Date";
    }

    /** 日時の表示順序を返す（ダイアログ用） */
    public int getFileDateTimeOrder(int idx) {
        return idx;
    }

    /** 日時を返す（ファイルリスト用） */
    @Deprecated
    public String getFileDateTimeStr() {
        return getFileCreateDateTimeStr();
    }

    /** 日時を指定ファイルに書き込む */
    public void writeFileDateTime(String path) {
        if (!(hasCreateDateTime() || hasModifyDateTime() || hasAccessDateTime())) {
            return;
        }
        if (!Files.exists(Paths.get(path))) {
            return;
        }
        // Implementation depends on file system capabilities
    }

    /** 指定ファイルから日時を読み込む */
    public void readFileDateTime(String path, DiskBasicDirItemAttr dateTime) {
        LocalDateTime tm = LocalDateTime.now();
        if (!(hasCreateDateTime() || hasModifyDateTime() || hasAccessDateTime())) {
            return;
        }
        dateTime.setCreateDateTime(tm);
        dateTime.setModifyDateTime(tm);
        dateTime.setAccessDateTime(tm);
    }

    public boolean hasAddress() {
        return false;
    }

    public boolean hasExecuteAddress() {
        return hasAddress();
    }

    public boolean isAddressEditable() {
        return hasAddress();
    }

    public int getStartAddress() {
        return -1;
    }

    public int getEndAddress() {
        return -1;
    }

    public int getExecuteAddress() {
        return -1;
    }

    public void setStartAddress(int val) {
    }

    public void setEndAddress(int val) {
    }

    public void setExecuteAddress(int val) {
    }

    public boolean checkUsed(boolean unuse) {
        return false;
    }

    /** 使用中のアイテムか */
    public boolean isUsed() {
        return (flags & USED_ITEM) != 0;
    }

    /** 使用中かをセット */
    public void used(boolean val) {
        flags = val ? (flags | USED_ITEM) : (flags & ~USED_ITEM);
    }

    /** リストに表示するアイテムか */
    public boolean isVisible() {
        return (flags & VISIBLE_LIST) != 0;
    }

    /** リストに表示するかをセット */
    public void visible(boolean val) {
        flags = val ? (flags | VISIBLE_LIST) : (flags & ~VISIBLE_LIST);
    }

    /** 使用中かつリストに表示するアイテムか */
    public boolean isUsedAndVisible() {
        return (flags & (USED_ITEM | VISIBLE_LIST)) == (USED_ITEM | VISIBLE_LIST);
    }

    /** ツリーに表示するアイテムか */
    public boolean isVisibleOnTree() {
        return (flags & VISIBLE_TREE) != 0;
    }

    /** ツリーに表示するかをセット */
    public void visibleOnTree(boolean val) {
        flags = val ? (flags | VISIBLE_TREE) : (flags & ~VISIBLE_TREE);
    }

    /**
     * ファイル名、属性をコピー
     *
     * @param src ディレクトリアイテム
     */
    public void copyItem(DiskBasicDirItem<T> src) {
        // データはコピーする
        copyData(src.getRawData());
        // グループ
        groups = src.groups;
        // サイズ
//	    fileSize = src.fileSize;
        // フラグ
        flags = src.flags;
        // その他の属性
        externalAttr = src.externalAttr;
    }

    public abstract int getDataSize();

    public abstract T getData();

    // vavi for debug
    public byte[] getRawData() {
        return null;
    }

    public abstract boolean copyData(byte[] val);

    public abstract void clearData();

    /** ディレクトリを初期化 未使用にする */
    public void initialData() {
        clearData();
    }

    public boolean needCheckEofCode() {
        return false;
    }

    /** ファイルの終端コードを返す */
    public byte getEofCode() {
        return basic.getTextTerminateCode();
    }

    public boolean needChainInData() {
        return false;
    }

    /**
     * データをエクスポートする前に必要な処理
     *
     * @param filename [in,out] ファイル名
     * @return false このファイルは対象外とする
     */
    public boolean preExportDataFile(String[] filename) {
        addExtensionByFileAttr(getFileAttr().getType(), FILE_TYPE_EXTENSION_MASK, filename, false);
        return true;
    }

    /**
     * データをインポートする前に必要な処理
     *
     * @param filename [in,out] ファイル名
     * @return false このファイルは対象外とする
     */
    public boolean preImportDataFile(String[] filename) {
        trimLastExtensionByExtensionAttr(filename[0]);
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * ファイル番号のファイルサイズを得る
     * １つのエントリで複数のファイルを管理している場合は要オーバーライド
     *
     * @param fileUnitNum ファイル番号
     * @param iStream     [in,out] 入力ストリーム
     * @param fileOffset  ストリーム内のオフセット
     * @return ファイルサイズ / ない場合 -1
     */
    public int getFileUnitSize(int fileUnitNum, InputStream iStream, int fileOffset) throws IOException {
        if (fileUnitNum == 0) {
            return iStream.available();
        } else {
            return -1;
        }
    }

    /**
     * ファイル番号のファイルへアクセスできるか
     * １つのエントリで複数のファイルを管理している場合は要オーバーライド
     *
     * @param fileUnitNum ファイル番号
     */
    public boolean isValidFileUnit(int fileUnitNum) {
        return fileUnitNum == 0;
    }

    /** ファイル名から属性を決定する */
    public int convFileTypeFromFileName(String filename) {
        return 0;
    }

    /** ファイル名から属性を決定する */
    public int convOriginalTypeFromFileName(String filename) {
        return 0;
    }

    /** ファイル名から拡張属性を決定する */
    public int convOptionalNameFromFileName(String filename) {
        return 0;
    }

    /**
     * 属性値を加工する
     * <p>
     * インポートダイアログ入力後に必要なら入力した属性値の加工を行う
     */
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        return true;
    }

    /** その他の属性値を設定する */
    public void setOptionalAttr(DiskBasicDirItemAttr attr) {
    }

    /**
     * 文字列をバイト列に変換 文字コードは機種依存
     *
     * @return >=0 バイト数
     */
    public int convStringToChars(String src, byte[] dst, int len) {
        System.arraycopy(src.getBytes(), 0, dst, 0, len);
        return len;
    }

    /** バイト列を文字列に変換 文字コードは機種依存 */
    public String convCharsToString(byte[] src, int len) {
        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(src, 0, len, sb, -1);
        return sb.toString();
    }

    /**
     * ファイル属性をXMLで出力
     *
     * @param path 出力先XMLファイルパス
     */
    public boolean writeFileAttrToXml(String path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("FileInformation");
            doc.appendChild(root);

            DiskBasicFileType fileType = getFileAttr();

            Element external1 = doc.createElement("ExternalAttribute");
            external1.setTextContent(String.format("0x%x", getExternalAttr()));
            root.appendChild(external1);

            LocalDateTime ctm = LocalDateTime.now();
            getFileCreateDateTime(ctm);
            Element time1 = doc.createElement("CreateTime");
            time1.setTextContent(Utils.formatHMSStr(ctm.toLocalTime()));
            root.appendChild(time1);
            Element date1 = doc.createElement("CreateDate");
            date1.setTextContent(Utils.formatYMDStr(ctm.toLocalDate()));
            root.appendChild(date1);

            LocalDateTime mtm = LocalDateTime.now();
            getFileModifyDateTime(mtm);
            time1 = doc.createElement("ModifyTime");
            time1.setTextContent(Utils.formatHMSStr(mtm.toLocalTime()));
            root.appendChild(time1);
            date1 = doc.createElement("ModifyDate");
            date1.setTextContent(Utils.formatYMDStr(mtm.toLocalDate()));
            root.appendChild(date1);

            LocalDateTime atm = LocalDateTime.now();
            getFileAccessDateTime(atm);
            time1 = doc.createElement("AccessTime");
            time1.setTextContent(Utils.formatHMSStr(atm.toLocalTime()));
            root.appendChild(time1);
            date1 = doc.createElement("AccessDate");
            date1.setTextContent(Utils.formatYMDStr(atm.toLocalDate()));
            root.appendChild(date1);

            int val = getExecuteAddress();
            if (val >= 0) {
                Element exec1 = doc.createElement("ExecuteAddress");
                exec1.setTextContent(String.format("0x%x", val));
                root.appendChild(exec1);
            }

            val = getEndAddress();
            if (val >= 0) {
                Element end1 = doc.createElement("EndAddress");
                end1.setTextContent(String.format("0x%x", val));
                root.appendChild(end1);
            }

            val = getStartAddress();
            if (val >= 0) {
                Element start1 = doc.createElement("StartAddress");
                start1.setTextContent(String.format("0x%x", val));
                root.appendChild(start1);
            }

            Element size1 = doc.createElement("Size");
            size1.setTextContent(String.format("%d", getFileSize()));
            root.appendChild(size1);

            for (int i = 2; i >= 0; i--) {
                String label = String.format("OriginalType%d", i);
                Element orig1 = doc.createElement(label);
                orig1.setTextContent(String.format("0x%x", fileType.getOrigin(i)));
                root.appendChild(orig1);
            }

            Element type1 = doc.createElement("Type");
            type1.setTextContent(String.format("0x%x", fileType.getType()));
            root.appendChild(type1);

            byte[] fname = new byte[256];
            byte[] fext = new byte[256];
            int[] fnl = new int[] {fname.length};
            int[] fel = new int[] {fext.length};
            getNativeFileName(fname, fnl, fext, fel);

            Element ext1 = doc.createElement("Ext");
            ext1.setTextContent(Utils.encodeEscape(fext, fel[0]));
            root.appendChild(ext1);

            Element name1 = doc.createElement("Name");
            name1.setTextContent(Utils.encodeEscape(fname, fnl[0]));
            root.appendChild(name1);

            Element fmt1 = doc.createElement("Format");
            fmt1.setTextContent(String.format("%s", fileType.getFormat()));
            root.appendChild(fmt1);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(path));
            transformer.transform(source, result);

            return true;
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
            return false;
        }
    }

    /**
     * ファイル属性をXMLから読み込む
     *
     * @param path XMLファイルパス
     * @param attr [out] アイテム内の属性(日時保持用)
     * @return true: 正常, false: ファイル読み込めない
     */
    public boolean readFileAttrFromXml(String path, DiskBasicDirItemAttr attr) {
        try {
            if (!Files.exists(Paths.get(path))) return false;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(path));

            Element root = doc.getDocumentElement();
            if (root == null) return false;

            int formatType = 0;
            int fileType = 0;
            int originalType0 = 0;
            int originalType1 = 0;
            int originalType2 = 0;
            int fileSize = 0;
            int startAddr = -1;
            int endAddr = -1;
            int execAddr = -1;
            int externalAttr = 0;
            LocalDateTime tmCTmp = LocalDateTime.now();
            LocalDateTime tmMTmp = LocalDateTime.now();
            LocalDateTime tmATmp = LocalDateTime.now();

            byte[] fileName = new byte[256];
            byte[] fileExt = new byte[256];
            int fileNameLen = fileName.length;
            int fileExtLen = fileExt.length;

            NodeList nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    String name = node.getNodeName();
                    String content = node.getTextContent();

                    switch (name) {
                        case "Format" -> formatType = Integer.parseInt(content);
                        case "Name" -> Utils.decodeEscape(content, fileName, fileNameLen);
                        case "Ext" -> Utils.decodeEscape(content, fileExt, fileExtLen);
                        case "Type" ->
                        fileType = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "OriginalType0" ->
                        originalType0 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "OriginalType1" ->
                        originalType1 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "OriginalType2" ->
                        originalType2 = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "Size" -> fileSize = Integer.parseInt(content);
                        case "StartAddress" ->
                        startAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "EndAddress" ->
                        endAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "ExecuteAddress" ->
                        execAddr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                        case "ExternalAttribute" ->
                        externalAttr = Integer.parseInt(content.startsWith("0x") ? content.substring(2) : content, content.startsWith("0x") ? 16 : 10);
                    }
                }
            }

            fileNameLen = Common.getStringLength(fileName, fileNameLen, (byte) 0);
            fileExtLen = Common.getStringLength(fileExt, fileExtLen, (byte) 0);
            setNativeFileName(fileName, fileName.length, fileNameLen, fileExt, fileExt.length, fileExtLen);
            setFileAttr(DiskBasicFormatType.valueOf(formatType), fileType, originalType0, originalType1, originalType2);
            setFileSize(fileSize);
            setStartAddress(startAddr);
            setEndAddress(endAddr);
            setExecuteAddress(execAddr);
            setExternalAttr(externalAttr);
            if (attr != null) {
                attr.setCreateDateTime(tmCTmp);
                attr.setModifyDateTime(tmMTmp);
                attr.setAccessDateTime(tmATmp);
            }

            return true;
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
            return false;
        }
    }

    public void setSector(DiskImageSector val) {
        sector = val;
    }

    /** アイテムの属するセクタを変更済みにする（未実装） */
    public void setModify() {
    }

    public DiskBasic getBasic() {
        return basic;
    }

    public int getNumber() {
        return num;
    }

    public void setNumber(int val) {
        num = val;
    }

    public int getPosition() {
        return position;
    }

    public void setInternalDataInAttrDialog(KeyValArray vals) throws IOException {
    }

    public void calcFileUnitSize(int fileUnitNum) throws IOException {
    }

    /** ファイルサイズとグループ数を計算する */
    public void calcFileSize() throws IOException {
        groups.clear();
        calcFileUnitSize(0);
    }

    /**
     * ファイルの終端コードをチェックして必要なサイズを返す
     *
     * @param iStream  入力ストリーム
     * @param fileSize 入力ストリームの元のデータサイズ
     * @return 終端コードを付加したサイズ（元のサイズ+1）
     */
    public int checkEofCode(InputStream iStream, int fileSize) throws IOException {
        iStream.skipNBytes(fileSize - 1);
        byte[] c = new byte[1];
        iStream.read(c);
        if (c[0] != getEofCode()) {
            fileSize++;
        }
        iStream.reset();
        return fileSize;
    }

    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        return occupiedSize;
    }

    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        return fileSize;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1) : "";
    }

    private String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(0, lastDot) : filename;
    }

    /** 属性値を一時的に集めておくクラス */
    public static class DiskBasicDirItemAttr {

        private boolean rename;

        private DiskBasicFileName name;
        private boolean ignoreType;

        private DiskBasicFileType type;

        private int startAddr;
        private int endAddr;
        private int execAddr;

        private boolean ignoreDateTime;

        private LocalDateTime createDateTime;
        private LocalDateTime modifyDateTime;
        private LocalDateTime accessDateTime;
        private LocalDateTime externDateTime;

        public DiskBasicDirItemAttr() {
            rename = false;
            name = new DiskBasicFileName();
            ignoreType = false;
            type = new DiskBasicFileType();
            startAddr = -1;
            endAddr = -1;
            execAddr = -1;
            ignoreDateTime = false;
            createDateTime = LocalDateTime.now();
            modifyDateTime = LocalDateTime.now();
            accessDateTime = LocalDateTime.now();
            externDateTime = LocalDateTime.now();
        }

        public void renameable(boolean val) {
            rename = val;
        }

        public void setFileName(DiskBasicFileName val) {
            name = val;
        }

        /**
         * ファイル名をセット
         *
         * @param nName     ファイル名
         * @param nOptional ファイル名の属性
         */
        public void setFileName(String nName, int nOptional) {
            name.setName(nName);
            name.setOptional(nOptional);
        }

        public void ignoreFileAttr(boolean val) {
            ignoreType = val;
        }

        public void setFileAttr(DiskBasicFileType val) {
            type = val;
        }

        public void setFileAttr(DiskBasicFormatType nFormat, int nType, int nOrigin0 /* = 0 */) {
            setFileAttr(nFormat, nType, nOrigin0, 0, 0);
        }

        /**
         * 属性をセット
         *
         * @param nFormat  フォーマットタイプ
         * @param nType    共通属性
         * @param nOrigin0 独自属性
         * @param nOrigin1 独自属性 つづき1
         * @param nOrigin2 独自属性 つづき2
         */
        public void setFileAttr(DiskBasicFormatType nFormat, int nType, int nOrigin0, int nOrigin1, int nOrigin2 /* = 0 */) {
            type.setFormat(nFormat);
            type.setType(nType);
            type.setOrigin(0, nOrigin0);
            type.setOrigin(1, nOrigin1);
            type.setOrigin(2, nOrigin2);
        }

        /**
         * 共通属性をセット
         *
         * @param nType 共通属性
         */
        public void setFileType(int nType) {
            type.setType(nType);
        }

        /**
         * 独自属性をセット
         *
         * @param idx     0..2
         * @param nOrigin 独自属性
         */
        public void setFileOriginAttr(int idx, int nOrigin) {
            type.setOrigin(idx, nOrigin);
        }

        /**
         * 独自属性をセット
         *
         * @param nOrigin 独自属性
         */
        public void setFileOriginAttr(int nOrigin) {
            type.setOrigin(nOrigin);
        }

        public void setStartAddress(int val) {
            startAddr = val;
        }

        public void setEndAddress(int val) {
            endAddr = val;
        }

        public void setExecuteAddress(int val) {
            execAddr = val;
        }

        public void ignoreDateTime(boolean val) {
            ignoreDateTime = val;
        }

        public void setCreateDateTime(LocalDateTime tm) {
            createDateTime = tm;
        }

        public void setModifyDateTime(LocalDateTime tm) {
            modifyDateTime = tm;
        }

        public void setAccessDateTime(LocalDateTime tm) {
            accessDateTime = tm;
        }

        public void setExternDateTime(LocalDateTime tm) {
            externDateTime = tm;
        }

        public boolean isRenameable() {
            return rename;
        }

        public DiskBasicFileName getFileName() {
            return name;
        }

        public boolean doesIgnoreFileAttr() {
            return ignoreType;
        }

        public DiskBasicFileType getFileAttr() {
            return type;
        }

        /** 共通属性を得る */
        public int getFileType() {
            return type.getType();
        }

        /**
         * 独自属性を得る
         *
         * @param idx 0..2
         */
        public int getFileOriginAttr(int idx /* = 0 */) {
            return type.getOrigin(idx);
        }

        public int getStartAddress() {
            return startAddr;
        }

        public int getEndAddress() {
            return endAddr;
        }

        public int getExecuteAddress() {
            return execAddr;
        }

        public boolean doesIgnoreDateTime() {
            return ignoreDateTime;
        }

        public LocalDateTime getCreateDateTime() {
            return createDateTime;
        }

        public LocalDateTime getModifyDateTime() {
            return modifyDateTime;
        }

        public LocalDateTime getAccessDateTime() {
            return accessDateTime;
        }

        public LocalDateTime getExternDateTime() {
            return externDateTime;
        }
    }

    /** セクタをまたぐディレクトリアイテムを処理 */
    public static class DirItemSectorBoundary {

        private static class SectorInfo {

            byte[] data;
            int size;
            int pos;

            SectorInfo() {
                data = null;
                size = 0;
                pos = 0xffff;
            }
        }

        private final SectorInfo[] s;

        public DirItemSectorBoundary() {
            s = new SectorInfo[2];
            for (int i = 0; i < 2; i++) {
                s[i] = new SectorInfo();
            }
            clear();
        }

        /** 初期化 */
        public void clear() {
            for (int i = 0; i < 2; i++) {
                s[i].data = null;
                s[i].size = 0;
                s[i].pos = 0xffff;
            }
        }

        /**
         * アイテムの位置情報をセット
         *
         * @param basic    Disk Basic
         * @param sector   セクタ
         * @param position アイテムの位置
         * @param itemData アイテムデータ
         * @param itemSize アイテムサイズ
         * @param next     次のセクタ
         * @return セクタまたぎがある場合true
         */
        public <T extends Directory> boolean set(DiskBasic basic, DiskImageSector sector, int position, byte[] itemData, int itemSize, SectorParam next) throws IOException {
            if (sector == null) return false;

            int sPos = position;
            int sSize = sector.getSectorSize();
            byte[] sptr = itemData;
            if (itemData != null) {
                s[0].data = sptr;
                s[0].size = itemSize;
                s[0].pos = 0;
            }
//logger.log(Level.DEBUG, "spos: " + spos + ", itemSize: " + itemSize + ", ssize: " + ssize);
            //               +- spos             +- ssize
            //               |<---- s[1].pos --->|<- s[1].size ->|
            // --------------+-------------------+---------------+-------
            //           curr sector             |           next sector
            // --------------+-------------------+---------------+-------
            //               |<----------- itemSize ------------>|
            //               |<---- s[0].size -->|
            //               +- s[0].data
            //
            if (sPos + itemSize >= sSize) {
                // セクタまたぎ
                s[1].pos = sSize - sPos;
                s[1].size = s[0].size - s[1].pos;
                s[0].size = s[1].pos;

                // 次のセクタ
                if (next != null) {
                    DiskBasicType<Directory> type = basic.getType();
                    int nSectorPos = type.getSectorPosFromNum(next.getTrackNumber(), next.getSideNumber(), next.getSectorNumber());
                    int[] side = {next.getSideNumber()}, sec = {next.getSectorNumber()};
                    DiskImageSector nsector = basic.getManagedSector(next.getTrackNumber(), side, sec);
                    if (nsector != null) {
                        sptr = nsector.getSectorBuffer();
                        sSize = nsector.getSectorSize();
                        sPos = basic.getDirStartPosOnSector();
                        sPos += (nSectorPos % basic.getSectorsPerGroup()) == 0 ? basic.getDirStartPosOnGroup() : 0;
                        s[1].data = Arrays.copyOfRange(sptr, 0, s[1].size);
                        s[1].pos = 0;
                    }
                }
                return true;
            }
            return false;
        }

        /**
         * コピー
         *
         * @param dstItem [out] アイテムデータ
         */
        public void copyTo(byte[] dstItem) {
            for (int i = 0; i < 2; i++) {
                byte[] src = s[i].data;
                if (dstItem != null && src != null) {
                    System.arraycopy(src, s[i].pos, dstItem, s[i].pos, s[i].size);
                }
            }
        }

        /**
         * コピー
         *
         * @param srcItem アイテムデータ
         */
        public void copyFrom(byte[] srcItem) {
            for (int i = 0; i < 2; i++) {
                byte[] dst = s[i].data;
                if (dst != null && srcItem != null) {
                    System.arraycopy(srcItem, s[i].pos, dst, s[i].pos, s[i].size);
                }
            }
        }
    }
}
