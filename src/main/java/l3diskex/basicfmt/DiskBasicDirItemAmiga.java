package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.AmigaBlockPost;
import l3diskex.basicfmt.BasicCommon.AmigaBlockPre;
import l3diskex.basicfmt.BasicCommon.AmigaHeaderPost;
import l3diskex.basicfmt.BasicCommon.DirectoryAmiga;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupUserData;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.ByteUtil;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HARDLINK_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SOFTLINK_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_UNDELETE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_WRITEONLY_MASK;


/// ディレクトリ１アイテム Amiga DOS
public class DiskBasicDirItemAmiga extends DiskBasicDirItem<DirectoryAmiga> {

    public static final String KEY_FAST_FILE_SYSTEM = "FastFileSystem";
    public static final String KEY_INTERNATIONAL = "International";

    // AMIGA attributes from basicdiritem_amiga.h
    public static final int FILETYPE_MASK_AMIGA_HEADER = 2;
    public static final int FILETYPE_MASK_AMIGA_DATA = 8;
    public static final int FILETYPE_MASK_AMIGA_LIST = 16;

    public static final int FILETYPE_MASK_AMIGA_FILE = -3;
    public static final int FILETYPE_MASK_AMIGA_USERDIR = 2;
    public static final int FILETYPE_MASK_AMIGA_LINKFILE = -4;
    public static final int FILETYPE_MASK_AMIGA_LINKDIR = 4;
    public static final int FILETYPE_MASK_AMIGA_SOFTLINK = 3;

    public static final int FILETYPE_MASK_AMIGA_U_NDEL = 0x00000001;
    public static final int FILETYPE_MASK_AMIGA_U_NEXEC = 0x00000002;
    public static final int FILETYPE_MASK_AMIGA_U_NWRITE = 0x00000004;
    public static final int FILETYPE_MASK_AMIGA_U_NREAD = 0x00000008;
    public static final int FILETYPE_MASK_AMIGA_ARCHIVE = 0x00000010;
    public static final int FILETYPE_MASK_AMIGA_PURE = 0x00000020;
    public static final int FILETYPE_MASK_AMIGA_SCRIPT = 0x00000040;
    public static final int FILETYPE_MASK_AMIGA_HOLD = 0x00000080;
    public static final int FILETYPE_MASK_AMIGA_G_NDEL = 0x00000100;
    public static final int FILETYPE_MASK_AMIGA_G_NEXEC = 0x00000200;
    public static final int FILETYPE_MASK_AMIGA_G_NWRITE = 0x00000400;
    public static final int FILETYPE_MASK_AMIGA_G_NREAD = 0x00000800;
    public static final int FILETYPE_MASK_AMIGA_O_NDEL = 0x00001000;
    public static final int FILETYPE_MASK_AMIGA_O_NEXEC = 0x00002000;
    public static final int FILETYPE_MASK_AMIGA_O_NWRITE = 0x00004000;
    public static final int FILETYPE_MASK_AMIGA_O_NREAD = 0x00008000;
    public static final int FILETYPE_MASK_AMIGA_SETUID = 0x80000000;

    public static final int FILETYPE_MASK_AMIGA_ROOT = 1;

    public static final Map<String, Object> G_TYPE_NAME_AMIGA1 = new LinkedHashMap<>() {{
        put("File", FILETYPE_MASK_AMIGA_FILE);
        put("Dir", FILETYPE_MASK_AMIGA_USERDIR);
        put("L.F.", FILETYPE_MASK_AMIGA_LINKFILE);
        put("L.D.", FILETYPE_MASK_AMIGA_LINKDIR);
        put("S.L.", FILETYPE_MASK_AMIGA_SOFTLINK);
    }};

    public static final Map<Integer, Integer> G_TYPE_CONV_AMIGA1 = new HashMap<>() {{
            put(FILE_TYPE_DIRECTORY_MASK.getValue(), FILETYPE_MASK_AMIGA_ROOT);
            put(FILE_TYPE_DATA_MASK.getValue(), FILETYPE_MASK_AMIGA_FILE);
            put(FILE_TYPE_DIRECTORY_MASK.getValue(), FILETYPE_MASK_AMIGA_USERDIR);
            put(FILE_TYPE_HARDLINK_MASK.getValue() | FILE_TYPE_DATA_MASK.getValue(), FILETYPE_MASK_AMIGA_LINKFILE);
            put(FILE_TYPE_HARDLINK_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), FILETYPE_MASK_AMIGA_LINKDIR);
            put(FILE_TYPE_SOFTLINK_MASK.getValue(), FILETYPE_MASK_AMIGA_SOFTLINK);
    }};

    public static final String G_TYPE_NAME_AMIGA2 = "dewrapsh";

    public static final int[] G_TYPE_CONV_AMIGA2 = {
            FILETYPE_MASK_AMIGA_U_NDEL, FILETYPE_MASK_AMIGA_U_NEXEC,
            FILETYPE_MASK_AMIGA_U_NWRITE, FILETYPE_MASK_AMIGA_U_NREAD,
            FILETYPE_MASK_AMIGA_ARCHIVE, FILETYPE_MASK_AMIGA_PURE,
            FILETYPE_MASK_AMIGA_SCRIPT, FILETYPE_MASK_AMIGA_HOLD,
            FILETYPE_MASK_AMIGA_G_NDEL, FILETYPE_MASK_AMIGA_G_NEXEC,
            FILETYPE_MASK_AMIGA_G_NWRITE, FILETYPE_MASK_AMIGA_G_NREAD,
            FILETYPE_MASK_AMIGA_O_NDEL, FILETYPE_MASK_AMIGA_O_NEXEC,
            FILETYPE_MASK_AMIGA_O_NWRITE, FILETYPE_MASK_AMIGA_O_NREAD,
            FILETYPE_MASK_AMIGA_SETUID, -1
    };

    // amiga_hash_chain_t
    public static class AmigaHashChain {

        public int hash_chain;
        public int parent;
        public int extension;
        public int sec_type;
    }

    // amiga_file_data_pre_t (Union placeholder)
    public static class AmigaFileDataPre {

        public static class Ofs {

            public int type;
            public int header_key;
            public int seq_num;
            public int data_size;
            public int next_data;
            public int check_sum;
            public byte[] data = new byte[1];
        }

        public Ofs o = new Ofs();
    }

    /// Amiga ユーザデータに渡すチェイン情報
    public static class AmigaChain extends DiskBasicGroupUserData {

        public int m_idx;
        // TODO int *
        public int p_prev_chain; // Pointer placeholder
        // TODO int *
        public int p_next_chain; // Pointer placeholder

        public AmigaChain() {
            super();
            m_idx = 0;
            p_prev_chain = -1;
            p_next_chain = -1;
        }

        public AmigaChain(int idx, int prev_chain, int next_chain) {
            m_idx = idx;
            p_prev_chain = prev_chain;
            p_next_chain = next_chain;
        }

        public DiskBasicGroupUserData Clone() {
            return new AmigaChain(this.m_idx, this.p_prev_chain, this.p_next_chain);
        }

        public AmigaChain operator_assign(DiskBasicGroupUserData src) { // operator=
            AmigaChain src_amiga = (AmigaChain) src;
            m_idx = src_amiga.m_idx;
            p_prev_chain = src_amiga.p_prev_chain;
            p_next_chain = src_amiga.p_next_chain;
            return this;
        }
    }

    private final DiskBasicDirData<DirectoryAmiga> m_data = new DiskBasicDirData<>();
    private final List<Integer> m_extension_list = new ArrayList<>(); // int[]
    private AmigaBlockPre m_temp_pre;
    private AmigaBlockPost m_temp_post;
    private AmigaChain m_chain = new AmigaChain();

    // Public Constructors
    public DiskBasicDirItemAmiga(DiskBasic basic) throws IOException {
        super(basic);

        m_temp_pre = null;
        m_temp_post = null;

        allocData(null, null, 0);
        allocTemp();
    }

    public DiskBasicDirItemAmiga(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) throws IOException {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_temp_pre = null;
        m_temp_post = null;

        allocData(n_sector, n_data, dataP);
    }

    public DiskBasicDirItemAmiga(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_temp_pre = null;
        m_temp_post = null;
        if (n_gitem != null) m_chain = m_chain.operator_assign(n_gitem.userData);

        allocData(n_sector, n_data, dataP);

        used(checkUsed(n_unuse[0]));

        calcFileSize();
    }

    private void allocData(DiskImageSector n_sector, byte[] n_data, int dataP) throws IOException {
        m_data.alloc(DirectoryAmiga.class);

        if (n_sector != null && n_data != null) {
            int num = type.getSectorPosFromNum(n_sector.getIDC(), n_sector.getIDH(), n_sector.getIDR());
            m_data.data().blockNum = num;
            m_data.data().pre = new AmigaBlockPre();
            Serdes.Util.deserialize(new ByteArrayInputStream(n_data, dataP, AmigaBlockPre.SIZE), m_data.data().pre);
            m_data.data().post = new AmigaBlockPost();
            Serdes.Util.deserialize(new ByteArrayInputStream(n_data, n_sector.getSectorSize() - AmigaBlockPost.SIZE, AmigaBlockPost.SIZE), m_data.data().post);
        } else {
            m_data.data().blockNum = 0;
            m_data.data().pre = null;
            m_data.data().post = null;
        }
    }

    private void allocTemp() {
        m_temp_pre = new AmigaBlockPre();
        m_temp_post = new AmigaBlockPost();
        m_data.data().pre = m_temp_pre;
        m_data.data().post = m_temp_post;
    }

    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        if (n_gitem != null) m_chain = m_chain.operator_assign(n_gitem.userData); // TODO serdes
        allocData(n_sector, n_data, dataP);
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) { // int -> int[]
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null && num == 0) {
            size[0] = post.name.length;
            len[0] = post.name.length;
            len[0]--;
            return post.name;
        }
        size[0] = 0;
        len[0] = 0;
        return null;
    }

    public void setNativeName(byte[] filename, int size, int length) { // int -> int
        byte[] n;
        int[] nl = new int[1];
        int[] ns = new int[1];
        n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            System.arraycopy(filename, 0, n, 0, ns[0]);
            AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
            if (post != null) post.nameLen = (byte) ((length - 1) & 0xff);
        }
    }

    @Override
    public void getNativeName(byte[] filename, int size, int[] length) { // int -> int
        byte[] n = null;
        int[] s = new int[1];
        int[] l = new int[1];
        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            int copy_size = s[0];
            if (copy_size > size) copy_size = size;
            System.arraycopy(n, 0, filename, 0, copy_size);
        }
        length[0] = l[0];
    }

    @Override
    public int getFileType1() { // virtual int GetFileType1() const
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) return post.secType; // le
        return 0;
    }

    @Override
    public void setFileType1(int val) { // virtual void SetFileType1(int val)
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) post.secType = val; // le
    }

    @Override
    public int getFileType2() { // virtual int GetFileType2() const
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) return post.protect; // le
        return 0;
    }

    @Override
    public void setFileType2(int val) { // virtual void SetFileType2(int val)
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) post.protect = val; // le
    }

    @Override
    protected int getFileType3() { // protected virtual int GetFileType3() const
        return (getGroupID() << 16) | getUserID();
    }

    @Override
    protected void setFileType3(int val) { // protected virtual void SetFileType3(int val)
        setGroupID((short) (val >> 16));
        setUserID((short) (val & 0xffff));
    }

    @Override
    public boolean checkUsed(boolean unuse) { // virtual bool CheckUsed(bool unuse)
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType file_type) { // virtual void SetFileAttr(final DiskBasicFileType &file_type)
        int ftype = file_type.getType();
        if (ftype == -1) return;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            setFileType1(file_type.getOrigin(0));
            setFileType2(file_type.getOrigin(1));
            setFileType3(file_type.getOrigin(2));
        } else {
            int t1 = convToFileType1(ftype);
            setFileType1(t1);
        }
    }

    @Override
    public DiskBasicFileType getFileAttr() { // virtual DiskBasicFileType GetFileAttr() const
        int t1 = getFileType1();
        int t2 = getFileType2();
        int t3 = getFileType3();
        int val = convFromFileType1(t1);
        val |= convFromFileType2(t2);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1, t2, t3);
    }

    @Override
    public String getFileAttrStr() { // virtual wxString GetFileAttrStr() const
        StringBuilder str = new StringBuilder();
        int spos = convFileType1Pos(getFileType1());
        str.append(spos >= 0 ? G_TYPE_NAME_AMIGA1.get(spos) : "???");
        str.append(" ,");
        int type2 = getFileType2();
        for (int i = 7; i >= 0; i--) {
            boolean high = (i >= 4);
            boolean bset = (((type2 & G_TYPE_CONV_AMIGA2[i]) != 0));
            str.append(bset == high ? G_TYPE_NAME_AMIGA2.charAt(i) : "-");
        }
        return str.toString();
    }

    @Override
    public void calcFileUnitSize(int fileunit_num) throws IOException {
        if (!isUsed()) return;
        getUnitGroups(fileunit_num, groups);
    }

    @Override
    public void getUnitGroups(int fileunit_num, DiskBasicGroups group_items) throws IOException { // virtual void GetUnitGroups(...)
        m_extension_list.clear();
        int limit = basic.getFatEndGroup() + 1;
        int[] tables = getBlockTable();
        int max_blks = getDataBlockNums();
        int t1 = getFileType1();
        switch (t1) {
            case FILETYPE_MASK_AMIGA_FILE:
                getFileGroups(tables, max_blks, limit, group_items, m_extension_list);
                break;
            case FILETYPE_MASK_AMIGA_USERDIR:
                getDirectoryGroups(basic, tables, max_blks, limit, group_items);
                break;
        }
    }

    @Override
    public boolean isDeletable() { // virtual bool IsDeletable() const
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        int val = (post != null ? post.nextLink /* le */ : 0);
        return val <= 0;
    }

    @Override
    public boolean delete() throws IOException {
        // extensionブロックを未使用にする
        // ディレクトリの場合、directory cacheを未使用にする
        for (int i = 0; i < m_extension_list.size(); i++) {
            int num = m_extension_list.get(i);
            type.deleteGroupNumber(num);
        }
        // ハードリンクの場合、リンクのつなぎ替えを行う
        deleteHardLink();

        // 自分を未使用にする
        type.deleteGroupNumber(m_data.data().blockNum);

        used(false);
        return true;
    }

    public boolean deleteHardLink() throws IOException { // virtual bool DeleteHardLink()
        int t1 = getFileType1();
        if (t1 != FILETYPE_MASK_AMIGA_LINKFILE && t1 != FILETYPE_MASK_AMIGA_LINKDIR)
            return true;
        int my_block_num = getStartGroup(0);
        AmigaHeaderPost prev_post = null;
        AmigaHeaderPost post = m_data.data().post.h;
        int block_num = post.realEntry; // le
        while (block_num >= 2) {
            DiskImageSector sector = basic.getSectorFromGroup(block_num);
            if (sector == null) return false;
            int npos = sector.getSectorSize() - 100; // Placeholder size
            byte[] b = sector.getSectorBuffer(npos);
            post = new AmigaHeaderPost();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), post);
            if (prev_post != null && block_num == my_block_num) {
                prev_post.nextLink = post.nextLink;
                break;
            }
            block_num = post.nextLink; // le
            prev_post = post;
        }
        return true;
    }

    @Override
    public boolean check(boolean[] last) { // virtual bool Check(bool &last)
        if (!m_data.isValid()) return false;
        return true;
    }

    @Override
    public void setModify() {
    } // virtual void SetModify()

    // Public methods
    public void InitForHeaderBlock(int parent_num) { // wxint -> int
        AmigaBlockPre pre = m_data.data().pre;
        if (pre == null) return;
        pre.type = FILETYPE_MASK_AMIGA_HEADER;
        pre.headerKey = m_data.data().blockNum; // le
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post == null) return;
        post.parentDir = parent_num; // le
    }

    public void InitForExtensionBlock(int parent_num) { // wxint -> int
        AmigaBlockPre pre = m_data.data().pre;
        if (pre == null) return;
        pre.type = FILETYPE_MASK_AMIGA_LIST;
        pre.headerKey = m_data.data().blockNum; // le
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post == null) return;
        post.parentDir = parent_num; // le
        post.secType = FILETYPE_MASK_AMIGA_FILE;
    }

    @Override
    public int getFileNameStrSize() {
        int[] s = new int[1];
        int[] l = new int[1];
        getFileNamePos(0, s, l);
        return l[0];
    }

    public int getByteSize() { // wxint -> int
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) return post.byteSize; // le
        return 0;
    }

    public void setHighSeq(int val) { // wxint -> int
        AmigaBlockPre pre = m_data.data().pre;
        if (pre != null) pre.highSeq = val; // le
    }

    public int[] getBlockTable() { // wxint * -> int[]
        AmigaBlockPre pre = m_data.data().pre;
        return pre != null ? pre.u.table : null;
    }

    public int getDataBlock(int idx) { // wxint -> int
        AmigaBlockPre pre = m_data.data().pre;
        if (pre != null && pre.u.table.length > idx) {
            return pre.u.table[idx]; // le
        }
        return 0;
    }

    public void setDataBlock(int idx, int val) { // wxint -> int
        AmigaBlockPre pre = m_data.data().pre;
        if (pre != null && pre.u.table.length > idx) {
            pre.u.table[idx] = val; // le
        }
    }

    public int getDataBlockNums() {
        return (basic.getSectorSize() - 100 - 100) / 4 + 1; // Placeholder for sizeof()
    }

    public boolean chainHashNumber(int idx, int val, DiskBasicDirItem<?> item) throws IOException { // wxint -> int
        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        aitem.m_chain.m_idx = idx;
        AmigaHeaderPost post = aitem.m_data.data().post.h;
        // if (post != null) aitem.m_chain.p_next_chain = null; // Pointer placeholder skip
        int num = getDataBlock(idx);
        if (num < 2) {
            setDataBlock(idx, val); /* if (m_data.data().pre != null) aitem.m_chain.p_prev_chain = null; */
            return true;
        } // Pointer placeholder skip
        int limit = basic.getFatEndGroup() + 1;
        AmigaHashChain chain = null;
        while (num >= 2 && limit >= 0) {
            DiskImageSector sector = basic.getSectorFromGroup(num);
            if (sector == null) return false;
            int offset = sector.getSectorSize() - 16; // sizeof(amiga_hash_chain_t) is 16
            byte[] b = sector.getSectorBuffer(offset);
            chain = new AmigaHashChain();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), chain);
            if (chain == null) return false;
            num = chain.hash_chain; // le
            limit--;
        }
        if (limit >= 0) {
            chain.hash_chain = val; // le
            // aitem.m_chain.p_prev_chain = null; // Pointer placeholder skip
            return true;
        }
        return false;
    }

    public int getHashNumber() {
        return m_chain.m_idx;
    }

    public void setExtension(int val) { // wxint -> int
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) post.extension = val; // le
    }

    public int getExtension() { // wxint -> int
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) return post.extension; // le
        return 0;
    }

    public int getUserID() { // wxUint16 -> int
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) return post.uid; // le
        return 0;
    }

    public void setUserID(int val) { // wxUint16 -> int
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) post.uid = (short) val; // le
    }

    public int getGroupID() { // wxUint16 -> int
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) return post.gid; // le
        return 0;
    }

    public void setGroupID(int val) { // wxUint16 -> int
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) post.gid = (short) val; // le
    }

    public static int convToFileType1(int ftype) { // static int ConvToFileType1(int ftype)
        return (ftype & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0 ? FILETYPE_MASK_AMIGA_USERDIR : FILETYPE_MASK_AMIGA_FILE;
    }

    public static int convFromFileType1(int type1) { // static int ConvFromFileType1(int type1)
        int val = 0;
        for (Map.Entry<Integer, Integer> vv : G_TYPE_CONV_AMIGA1.entrySet()) {
            if (type1 == vv.getKey()) {
                val = vv.getValue();
                break;
            }
        }
        return val;
    }

    public static int convFromFileType2(int type2) { // static int ConvFromFileType2(int type2)
        int val = 0;
        if ((type2 & FILETYPE_MASK_AMIGA_U_NDEL) != 0) {
            val |= FILE_TYPE_UNDELETE_MASK.getValue();
        } else if ((type2 & FILETYPE_MASK_AMIGA_U_NWRITE) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        } else if ((type2 & FILETYPE_MASK_AMIGA_U_NREAD) != 0) {
            val |= FILE_TYPE_WRITEONLY_MASK.getValue();
        }
        return val;
    }

    public int convFileType1Pos(int type1) {
        return Utils.indexOf(G_TYPE_NAME_AMIGA1, type1);
    }

    @Override
    public void setFileSize(int val) {
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) post.byteSize = val; // le
    }

    @Override
    public int getFileSize() {
        int val = 0;
        AmigaHeaderPost post = m_data.data().post != null ? m_data.data().post.h : null;
        if (post != null) val = post.byteSize; // le
        return val;
    }

    public boolean getFileGroups(int[] tables, int table_size, int limit, DiskBasicGroups group_items, List<Integer> extension_list) throws IOException {
        int[] track_num = {0};
        int[] side_num = {0};
        int sector_num = 0;
        int prev_num = 0;
        int prev_track_num = 0;
        int prev_side_num = 0;
        int prev_sector_num = 0;

        int calc_groups = 0;
        int calc_file_size = 0;

        AmigaBlockPre expre = null;
        AmigaHeaderPost expost = null;

        int[] table = tables;

        int data_block_size = basic.getSectorSize();
        if (!basic.diskBasicParam.getVariousBoolParam(KEY_FAST_FILE_SYSTEM)) {
            data_block_size -= 24;
        }

        for (int extension = -1; ; extension++) {
            for (int i = table_size - 1; i >= 0 && limit >= 0; i--) {
                int num = table[i]; // le
                if (num == 0) break;
                DiskImageSector sector = basic.getSectorFromGroup(num, track_num, side_num);
                if (sector == null) break;
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) break;

                sector_num = sector.getSectorNumber();
                if (group_items != null && prev_num > 0) {
                    group_items.add(prev_num, num, prev_track_num, prev_side_num, prev_sector_num, sector_num);
                }
                prev_num = num;
                prev_track_num = track_num[0];
                prev_side_num = side_num[0];
                prev_sector_num = sector_num;

                calc_groups++;
                calc_file_size += data_block_size;
                limit--;
            }
            calc_groups++;
            int exnum = extension < 0 ? getExtension() : (expost != null ? expost.extension /* le */ : 0);
            if (exnum < 2 || limit < 0) break;
            DiskImageSector sector = basic.getSectorFromGroup(exnum);
            if (sector == null) break;
            byte[] b = sector.getSectorBuffer();
            expre = new AmigaBlockPre();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), expre);
            b = sector.getSectorBuffer(sector.getSectorSize() - 100);
            expost = new AmigaHeaderPost();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), expost);
            if (extension_list != null) extension_list.add(exnum);
            table = expre.u.table;
            limit--;
        }
        return true;
    }

    /**
     * 指定ディレクトリのすべてのグループを取得
     */
    public static boolean getDirectoryGroups(DiskBasic basic, int[] tables, int table_size, int limit, DiskBasicGroups group_items) throws IOException {
        boolean valid = true;

        int[] track_num = {0};
        int[] side_num = {0};
        int sector_num = 0;

        // ディレクトリの時、hash_table をたどる
        for (int i = 0; i < table_size && limit >= 0; i++) {
            int num = tables[i]; // le
            if (num < 2) {
                continue;
            }

            int p_prev_chain = i;

            while (num >= 2 && limit >= 0) {
                limit--;
                DiskImageSector sector = basic.getSectorFromGroup(num, track_num, side_num);
                if (sector == null) {
                    // Why?
                    valid = false;
                    break;
                }

                // ヘッダ種類が2なら有効
                byte[] sectorBuffer = sector.getSectorBuffer();
                int type = ByteUtil.readLeInt(sectorBuffer, 0);
                if (type != FILETYPE_MASK_AMIGA_HEADER) {
                    valid = false;
                    break;
                }

                // 次のヘッダブロックがあるか
                final int SIZE_OF_AMIGA_HASH_CHAIN_T = 4; // Placeholder
                int offset = sector.getSectorSize() - SIZE_OF_AMIGA_HASH_CHAIN_T;
                byte[] b = sector.getSectorBuffer(offset);
                AmigaHashChain chain = new AmigaHashChain();
                Serdes.Util.deserialize(new ByteArrayInputStream(b), chain);

                if (group_items != null) {
                    sector_num = sector.getSectorNumber();
                    group_items.add(num, 0, track_num[0], side_num[0], sector_num,
                            new AmigaChain(i, p_prev_chain, chain.hash_chain)
                    );
                }

                p_prev_chain = chain.hash_chain;
                num = chain.hash_chain; // le
            }
        }

        // 常に１ブロック
        if (group_items != null) {
            group_items.setNums(1);
            group_items.setSize(basic.getSectorSize());
        }

        return (limit >= 0) && valid;
    }

    /// インデックス番号をリナンバ
    public static void renumberInDirectory(DiskBasic basic, List<DiskBasicDirItem<DirectoryAmiga>> items) {
        if (items == null) return;

        int prev_idx = -1;
        int chains = 0;
        for (int i = 0; i < items.size(); i++) {
            DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) items.get(i);
            int idx = aitem.m_chain.m_idx;
            if (prev_idx != idx) {
                chains = 0;
            }
            aitem.setNumber(chains * 1000 + idx);
            prev_idx = idx;
            chains++;
        }
    }

    /**
     * アイテムリストにアイテムを挿入
     *
     * @param basic      DISK BASIC
     * @param tables     ディレクトリヘッダ内のハッシュテーブル
     * @param table_size ハッシュテーブルのサイズ数
     * @param limit      ループ制限値
     * @param items      [in,out]  ディレクトリの子供アイテムリスト
     * @param item       [in,out]  新たに追加するアイテム
     */
    public static boolean InsertItemInDirectory(DiskBasic basic, int[] tables, int table_size, int limit, List<DiskBasicDirItem<DirectoryAmiga>> items, DiskBasicDirItem<DirectoryAmiga> item) {
        boolean valid = true;

        if (!(items == null && item == null)) {
            return false;
        }

        valid = false;
        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        for (int i = 0; i < items.size(); i++) {
            DiskBasicDirItemAmiga ad = (DiskBasicDirItemAmiga) items.get(i);
            // hash_tableのインデックス番号に沿ってインサート
            if (ad.m_chain.m_idx > aitem.m_chain.m_idx) {
                items.add(i, item);
                valid = true;
                break;
            }
        }
        if (!valid) {
            items.add(item);
            valid = true;
        }
        // リナンバー
        renumberInDirectory(basic, items);

        return (limit >= 0) && valid;
    }

    /// アイテムリストからアイテムを削除
    public static boolean deleteItemInDirectory(DiskBasic basic, int[] tables, int table_size, int limit, List<DiskBasicDirItem<DirectoryAmiga>> items, DiskBasicDirItem<DirectoryAmiga> item) {
        // bool valid = true;

        if (!(items == null && item == null)) {
            return false;
        }

        DiskBasicDirItemAmiga aitem = (DiskBasicDirItemAmiga) item;
        aitem.m_chain.p_prev_chain = aitem.m_chain.p_next_chain;

        items.remove(item);

        // リナンバー
        renumberInDirectory(basic, items);
        return true;
    }

    /// 日付を変換
    public static LocalDate convDateToTm(int days) {
        int year = 0;
        int month = 0;
        int day = 0;
        // 1978-01-01から
        for (int i = 0; i < 200; i++) {
            year = 1978 + i;
            int days_per_year = (int) Utils.getDaysSince1978(LocalDate.of(i, 1, 1));
            if (days < days_per_year) {
                month = days % days_per_year;
                break;
            }
            days -= days_per_year;
        }
        // month and day
        for (int i = 0; i < 12; i++) {
            int day_of_month = (int) Utils.getDaysSince1978(LocalDate.of(year, month, 1));
            if (month < day_of_month) {
                day = (month % day_of_month) + 1;
                month = i;
                break;
            }
            month -= day_of_month;
        }

        return LocalDate.of(
                year - 1900,
                month,
                day);
    }

    /// 時間を変換
    static LocalTime convTimeToTm(int mins, int ticks) {
        // hour minute
        int hour = mins / 60;
        int minute = mins % 60;
        // second
        int second = ticks / 50;

        return LocalTime.of(
                hour,
                minute,
                second);
    }

    /// 日付を変換
    public static void convDateFromTm(LocalDate tm, int[] days) {
        days[0] = 0;

        int year = tm.getYear() + 1900;
        if (year < 1978) year = 1978;
        if (year > 2178) year = 2178;
        for (int i = 1978; i < year; i++) {
            days[0] += (int) Utils.getDaysSince1978(LocalDate.of(i, 1, 1));
        }

        int month = tm.getMonth().ordinal();
        for (int i = 0; i < month; i++) {
            days[0] += (int) Utils.getDaysSince1978(LocalDate.of(year, month, 1));
        }

        days[0] += tm.getDayOfMonth() - 1;
    }

    /// 時間を変換
    public static void convTimeFromTm(LocalDateTime tm, int[] mins, int[] ticks) {
        ticks[0] = tm.getSecond();
        ticks[0] *= 50;

        mins[0] = tm.getHour();
        mins[0] *= 60;
        mins[0] += tm.getMinute();
    }

    @Override
    public int getDataSize() {
        return 20;
    }

    /// アイテムを返す
    @Override
    public DirectoryAmiga getData() {
        return m_data.data();
    }

    /// アイテムをコピー
    @Override
    public boolean copyData(DirectoryAmiga amiga) {
        if (!m_data.isValid()) return false;

        m_data.data().blockNum = amiga.blockNum;

        // ポインタではなく内容をコピー
        AmigaBlockPre src_pre = amiga.pre;
        AmigaBlockPre dst_pre = m_data.data().pre;
        dst_pre.type = src_pre.type;
        dst_pre.headerKey = src_pre.headerKey;

        AmigaHeaderPost src_post = amiga.post.h;
        AmigaHeaderPost dst_post = m_data.data().post.h;

        dst_post.nameLen = src_post.nameLen;
        System.arraycopy(src_post.name, 0, dst_post.name, 0, dst_post.name.length);

        dst_post.protect = src_post.protect;

        dst_post.uid = src_post.uid;
        dst_post.gid = src_post.gid;

        dst_post.days = src_post.days;
        dst_post.mins = src_post.mins;
        dst_post.ticks = src_post.ticks;

        dst_post.parentDir = src_post.parentDir;
        dst_post.secType = src_post.secType;

        return true;
    }

    /// ディレクトリをクリア ファイル新規作成時
    @Override
    public void clearData() {
        m_data.data().blockNum = 0;

        // ポインタはクリアしない
    }

    /// 最初のグループ番号を設定
    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        m_data.data().blockNum = val;
    }

    /// 最初のグループ番号を返す
    @Override
    public int getStartGroup(int fileunit_num) {
        return m_data.data().blockNum;
    }

    public boolean preExportDataFile(String filename) {
        return false;
    } // wxString . String

    public boolean preImportDataFile(String filename) {
        return false;
    } // wxString . String

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        return 0;
    } // wxString . String

    public void updateCheckSumAll() {
    }

    public void updateCheckSum() {
    }

    public static int calcCheckSum(Object data, int size) {
        return 0;
    } // final void * -> Object

    public static void calcCheckSum(Object data, int size, int[] sum_data) {
    } // final void * -> Object, wxint & -> int[]
}
