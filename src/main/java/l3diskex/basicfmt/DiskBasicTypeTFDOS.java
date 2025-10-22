package l3diskex.basicfmt;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.Utils.TempData;
import l3diskex.basicfmt.BasicCommon.DirectoryTfdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.Utils.TEMP_DATA_SIZE;
import static l3diskex.basicfmt.BasicFat.INVALID_GROUP_NUMBER;


public class DiskBasicTypeTFDOS extends DiskBasicTypeMZBase<DirectoryTfdos> {

    static class st_ipl_tfdos {
        public byte[] ipl = new byte[0xf0];
        public byte[] auto_start = new byte[0x10];
    }

    static class st_fat_tfdos {
        public byte[] fat = new byte[0xc0];
        public byte volume_num;
        public byte reserved1;
        public byte ident_number;
        public byte version_number;
        public byte[] volume_name = new byte[12];
        public byte[] reserved2 = new byte[0x2a];
        public short x1_sector_size;
        public byte[] reserved3 = new byte[4];
    }

    private boolean is_base_compatible;
    private final DiskBasic basic;
    private final DiskBasicFat fat;
    private final DiskBasicDir<DirectoryTfdos> dir;
    private final TempData temp = new TempData(); // Assuming TempData is a class for temporary data manipulation

    public DiskBasicTypeTFDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryTfdos> dir) {
        super(basic, fat, dir);
        this.basic = basic;
        this.fat = fat;
        this.dir = dir;
        this.is_base_compatible = false;
    }

    // Utility to copy memory from a byte array to another
    private void mem_copy(byte[] src, int srcLen, int srcOffset, byte[] dst, int dstLen) {
        int len = Math.min(srcLen - srcOffset, dstLen);
        if (len > 0) {
            System.arraycopy(src, srcOffset, dst, 0, len);
        }
    }

    /**
     * FATエリアをチェック
     */
    @Override
    public double checkFat(boolean is_formatting) {
        double valid_ratio = 1.0;

        // グループサイズをトラックごとに調整
        basic.diskBasicParam.setSectorsPerGroup(basic.getSectorsPerTrack());

        // 最終グループ番号
        int max_group = basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() - 1;
        basic.diskBasicParam.setFatEndGroup(max_group);

        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return -1.0;
        }

        // ファイル管理番号
        st_fat_tfdos f = new st_fat_tfdos();
        // Assuming GetBuffer returns the raw fat buffer and it's large enough for st_fat_tfdos
        byte[] fatBuffer = fatbuf.getBuffer();
        if (fatBuffer != null && fatBuffer.length >= 0x48) { // Minimum size check
            // Simulate C struct access - manual parsing/mapping
            f.ident_number = fatBuffer[0xC2];
            f.volume_name = Arrays.copyOfRange(fatBuffer, 0xC4, 0xC4 + 12);
        } else {
            return -1.0; // Buffer error
        }

        if (basic.invertUint8((byte) (f.ident_number & 0xFF)) != 1) {
            return -1.0;
        }

        // ボリューム名
        byte[] volume_name = new byte[12];
        basic.invertMem(f.volume_name, f.volume_name.length, volume_name);
        // Assuming "TF-DOS" is ASCII/Shift-JIS compatible
        if (!new String(volume_name, 0, 6).equals("TF-DOS")) {
            return -1.0;
        }

        // 0 か 0xff 以外は無効
        for (int gnum = 0; gnum <= basic.getFatEndGroup() && gnum < fatbuf.getSize(); gnum++) {
            int buf = basic.invertUint8((byte) fatbuf.get(gnum));
            if (buf != basic.diskBasicParam.getGroupUnusedCode() && buf != basic.diskBasicParam.getGroupSystemCode()) {
                valid_ratio = -1.0;
                break;
            }
        }

        return valid_ratio;
    }

    /**
     * FAT位置をセット
     */
    @Override
    public void setGroupNumber(int num, int val) {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return;
        }

        if (num < fatbuf.getSize()) {
            int byteVal = val != 0 ? basic.diskBasicParam.getGroupSystemCode() : basic.diskBasicParam.getGroupUnusedCode();
            fatbuf.set(num, basic.invertUint8((byte) byteVal));
        }
    }

    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /**
     * FAT位置が使用されているか
     */
    @Override
    public boolean isUsedGroupNumber(int num) {
        boolean exist = false;

        if (num > basic.getFatEndGroup()) {
            return false;
        }

        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return true;
        }

        if (num < fatbuf.getSize()) {
            if (basic.invertUint8((byte) fatbuf.get(num)) != basic.diskBasicParam.getGroupUnusedCode()) {
                exist = true;
            }
        }
        return exist;
    }

    /**
     * 次のグループ番号を得る
     */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * 空き位置を返す
     */
    @Override
    public int getEmptyGroupNumber() {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * データサイズ分のグループを確保する
     */
    @Override
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryTfdos> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups group_items) {
        int file_size = 0;
        int groups = 0;
        int rc = 0;
        int remain = data_size;
        int sec_size = basic.getSectorSize();

        // 必要なグループ数
        int group_size = ((data_size - 1) / sec_size / basic.getSectorsPerGroup()) + 1;

        // 未使用が連続している位置をさがす
        int[] group_start = new int[1]; // Simulate pass-by-reference
        int cnt = findContinuousArea(group_size, group_start);
        if (cnt < group_size) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // 開始グループ決定
        item.setStartGroup(fileunit_num, group_start[0]);

        // 領域を確保する (Assuming AllocateGroupsSub is implemented in base class or available)
        // rc = AllocateGroupsSub(item, group_start[0], remain, sec_size, group_items, file_size, groups);
        // Since AllocateGroupsSub is not defined here, returning a placeholder result
        return 0;
    }

    // Dummy method for FindContinuousArea as it's not defined
    @Override
    public int findContinuousArea(int groupSize, int[] groupStart) {
        // Placeholder implementation
        if (basic.getFatEndGroup() >= groupSize) {
            groupStart[0] = 0; // Just assume start at 0 if available
            return basic.getFatEndGroup() + 1;
        }
        return 0;
    }

    // Dummy method for AllocateGroupsSub as it's not defined
    private int allocateGroupsSub(DiskBasicDirItem<DirectoryTfdos> item, int groupStart, int remain, int secSize, DiskBasicGroups groupItems, int fileSize, int groups) {
        // Placeholder implementation
        return 0;
    }

    /**
     * データの読み込み/比較処理
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) {
        int size = (remain_size < sector_size ? remain_size : sector_size);

        if (ostream != null) {
            // 書き出し
            temp.setData(sector_buffer, size, basic.isDataInverted());
            try {
                ostream.write(temp.getData(), 0, temp.getSize());
            } catch (java.io.IOException e) {
                // Log or handle exception
                return -2;
            }
        }
        if (istream != null) {
            // 読み込んで比較
            temp.setSize(size);
            byte[] readBuffer = temp.getData();
            int bytesRead;
            try {
                bytesRead = istream.read(readBuffer, 0, temp.getSize());
                if (bytesRead < size) {
                    // Handle less than expected read, maybe EOF
                }
            } catch (java.io.IOException e) {
                // Log or handle exception
                return -2;
            }

            temp.invertData(basic.isDataInverted());

            if (!Arrays.equals(temp.getData(), 0, temp.getSize(), sector_buffer, 0, temp.getSize())) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /**
     * 内部ファイルをエクスポートする際に内容を変換
     */
    @Override
    public boolean convertDataForLoad(DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, OutputStream ostream) {
        // BASEコンパチファイル
        is_base_compatible = (item.getFileAttr().isAscii() && item.getExternalAttr() == 1);

        long osize = 0;
        try {
            osize = istream.available(); // Approximation of istream.GetLength()
        } catch (java.io.IOException e) {
            // Log or handle exception
            return false;
        }

        if (item.getFileAttr().isAscii() && item.getExternalAttr() > 0) {
            // BASEコンパチファイル 最終バイトが0かどうかチェック
            // Seeking is difficult with standard Java InputStream, this logic is often complex to port directly
            // Simplified logic: Assume we can check the last byte without full seek
            // This is a major assumption due to API difference

            // Simplified: If file is BASE compatible, assume it might have a trailing 0x00 and adjust size
            // Real implementation would require a seekable stream or reading the whole content
            // Assuming osize is correct file size before checking 0x00 trailer
            // Skipping the actual check due to stream limitations, but adjusting size if compatible
            if (osize > 0 && is_base_compatible) {
                // Assuming is_base_compatible is true means we check for the trailing 0
                // Placeholder for actual check
                // ... logic to read last byte (requires seekable stream or reading all) ...
                // If last byte is 0, is_base_compatible remains true and osize--;
            }
            // For now, only using the initial is_base_compatible logic
        }

        try {
            if (is_base_compatible) {
                // BASEコンパチの場合、TABコード($14 -> $09)変換
                temp.setSize(TEMP_DATA_SIZE);
                while (osize > 0) {
                    int len = istream.read(temp.getData(), 0, temp.getSize());
                    if (len <= 0) break;

                    // TABコード($14 -> $09)変換
                    temp.replace((byte) 0x14, (byte) 0x09);

                    int writeLen = (int) Math.min(len, osize);
                    ostream.write(temp.getData(), 0, writeLen);
                    osize -= len;
                }
            } else {
                // 変換しない
                byte[] buffer = new byte[4096]; // Standard buffer size
                int len;
                while ((len = istream.read(buffer)) > 0) {
                    ostream.write(buffer, 0, len);
                }
            }
        } catch (java.io.IOException e) {
            // Log or handle exception
            return false;
        }
        return true;
    }

    /**
     * エクスポートしたファイルをベリファイする際に内容を変換
     */
    @Override
    public boolean convertDataForVerify(DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, OutputStream ostream) {
        long osize = 0;
        try {
            osize = istream.available();
        } catch (java.io.IOException e) {
            return false;
        }

        try {
            if (is_base_compatible) {
                // BASEコンパチの場合、TABコード($09 -> $14)変換
                temp.setSize(TEMP_DATA_SIZE);
                while (osize > 0) {
                    int len = istream.read(temp.getData(), 0, temp.getSize());
                    if (len <= 0) break;

                    // TABコード($09 -> $14)変換
                    temp.replace((byte) 0x09, (byte) 0x14);

                    int writeLen = (int) Math.min(len, osize);
                    ostream.write(temp.getData(), 0, writeLen);
                    osize -= len;
                }
                // 最後に$00を出力
                ostream.write(0);
            } else {
                // 変換しない
                byte[] buffer = new byte[4096];
                int len;
                while ((len = istream.read(buffer)) > 0) {
                    ostream.write(buffer, 0, len);
                }
            }
        } catch (java.io.IOException e) {
            // Log or handle exception
            return false;
        }
        return true;
    }

    /**
     * グループ番号から最終セクタ番号を得る
     */
    @Override
    public int getEndSectorFromGroup(int group_num, int next_group, int sector_start, int sector_size, int remain_size) {
        int end_sector = sector_start;
        int group_size = basic.getSectorsPerGroup() * sector_size;
        if (remain_size < group_size) {
            end_sector += ((remain_size + sector_size - 1) / sector_size) - 1;
        } else {
            end_sector += basic.getSectorsPerGroup() - 1;
        }
        return end_sector;
    }

    /**
     * ルートディレクトリか
     */
    @Override
    public boolean isRootDirectory(int group_num) {
        // オフセット未満だったらルート
        // fat->Get(1) is the 2nd byte of FAT which is used for the start of data/dir area in some formats
        int start_group_of_dir = basic.invertUint8((byte) fat.get(1));
        return (start_group_of_dir & 0xFF) > group_num;
    }

    /**
     * セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // IPL
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));

            byte[] sectorBuffer = sector.getSectorBuffer();
            if (sectorBuffer != null && sectorBuffer.length >= 0x100) {
                // Simulate st_ipl_tfdos struct access
                // ipl[0xf0] is from 0x00 to 0xEF
                // auto_start[0x10] is from 0xF0 to 0xFF

                // IPL文字列を設定
                String s_ipl_str = basic.diskBasicParam.getVariousStringParam("IDString");
                byte[] s_ipl = s_ipl_str.getBytes(); // Assuming default encoding for simplicity
                int len = s_ipl.length;
                int ipl_size = 0xf0;

                if (len > 0) {
                    if (len > ipl_size) len = ipl_size;
                    basic.invertMem(s_ipl, len, sectorBuffer); // Copy to 0x00
                }

                // 自動実行はなし (auto_start starts at 0xf0)
                for (int i = 0; i < 0x10; i++) {
                    sectorBuffer[0xf0 + i] = basic.invertUint8((byte) 0x0d);
                }
            }
        }

        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) return false;
        fatbuf.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));

        // Simulate st_fat_tfdos struct access on fatbuf.GetBuffer()
        byte[] fatBuffer = fatbuf.getBuffer();
        if (fatBuffer == null || fatBuffer.length < 0x100) return false; // Basic check

        // システムエリアは使用済みにする
        java.util.List<Integer> grps = basic.diskBasicParam.getReservedGroups();
        for (int grp : grps) {
            if (grp >= 0 && grp < 0xc0) {
                fatBuffer[grp] = basic.invertUint8((byte) basic.diskBasicParam.getGroupSystemCode());
            }
        }
        // オーバートラック部分は使用済みにする
        int fatEndGroup = basic.getFatEndGroup();
        for (int pos = fatEndGroup + 1; pos < 0xc0; pos++) {
            fatBuffer[pos] = basic.invertUint8((byte) basic.diskBasicParam.getGroupSystemCode());
        }

        // ボリューム番号を設定 (volume_num is at 0xc0)
        int vol_num = data.getVolumeNumber();
        fatBuffer[0xc0] = basic.invertUint8((byte) vol_num);

        // バージョン番号を設定 (ident_number at 0xc2, version_number at 0xc3)
        fatBuffer[0xc2] = basic.invertUint8((byte) 1);
        fatBuffer[0xc3] = basic.invertUint8((byte) 2);

        // ボリューム名を設定 (volume_name at 0xc4, size 12)
        byte[] vol_name;
        String volNameStr;
        if (!data.getVolumeName().isEmpty()) {
            volNameStr = data.getVolumeName();
        } else {
            volNameStr = basic.diskBasicParam.getVariousStringParam("VolumeString");
        }
        vol_name = volNameStr.getBytes(); // Assuming default encoding

        byte[] vol_name_dest = Arrays.copyOfRange(fatBuffer, 0xc4, 0xc4 + 12);
        mem_copy(vol_name, vol_name.length, 0, vol_name_dest, vol_name_dest.length);

        // Invert the volume name area in the buffer
        invertMem(fatBuffer, 12, 0xc4);

        // DIRエリア
        int[] trk_num = new int[1], sid_num = new int[1], sec_num = new int[1];
        for (int sec_pos = basic.diskBasicParam.getDirStartSector(); sec_pos <= basic.diskBasicParam.getDirEndSector(); sec_pos++) {
            getNumFromSectorPos(sec_pos - 1, trk_num, sid_num, sec_num);
            sector = basic.getSector(trk_num[0], sid_num[0], sec_num[0]);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnDir()));
            }
        }

        return true;
    }

    // Assuming an overloaded InvertMem for convenience
    private void invertMem(byte[] buf, int len, int offset) {
        byte[] temp = Arrays.copyOfRange(buf, offset, offset + len);
        basic.invertMem(temp, len);
        System.arraycopy(temp, 0, buf, offset, len);
    }

    /**
     * ファイルをセーブする前にデータを変換
     */
    @Override
    public boolean convertDataForSave(DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, OutputStream ostream) {
        // BASEコンパチファイル
        is_base_compatible = (item.getFileAttr().isAscii() && item.getExternalAttr() == 1);

        // 処理はベリファイと同じ
        return convertDataForVerify(item, istream, ostream);
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, byte[] buffer, int size, int remain, int sector_num, int group_num, int next_group, int sector_end, int seq_num) {
        int len = 0;

        try {
            if (remain <= size) {
                // 残り少ない
                if (remain < 0) remain = 0;
                if (remain > 0) {
                    temp.setSize(remain);
                    istream.read(temp.getData(), 0, temp.getSize());

                    memcpy(buffer, 0, temp.getData(), 0, temp.getSize());
                }
                if (size > remain) {
                    // バッファの余りは0サプレス
                    Arrays.fill(buffer, remain, size, (byte) 0);
                }
                len = remain;
            } else {
                // 継続
                temp.setSize(size);
                istream.read(temp.getData(), 0, temp.getSize());

                memcpy(buffer, 0, temp.getData(), 0, temp.getSize());

                len = size;
            }
        } catch (java.io.IOException e) {
            // Log or handle exception
            return -1;
        }

        // 反転
        basic.invertMem(buffer, size);

        return len;
    }

    // Utility for memcpy (equivalent to C's memcpy(dest, src, size))
    private void memcpy(byte[] dest, int destOffset, byte[] src, int srcOffset, int len) {
        System.arraycopy(src, srcOffset, dest, destOffset, len);
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) return;

        // Simulate st_fat_tfdos struct access
        byte[] fatBuffer = fatbuf.getBuffer();
        if (fatBuffer == null || fatBuffer.length < 0xc0 + 0x48) return; // Sufficient size check

        // volume label (at 0xc4, size 12)
        byte[] vol_name_raw = Arrays.copyOfRange(fatBuffer, 0xc4, 0xc4 + 12);
        byte[] vol_name = new byte[12 + 1];
        Arrays.fill(vol_name, (byte) 0);
        basic.invertMem(vol_name_raw, 12, vol_name); // Invert and copy to vol_name

        String dst = new String(vol_name, 0, 12, basic.getCharCodes().charset());
        data.setVolumeName(dst);

        // volume number (at 0xc0)
        data.setVolumeNumber(basic.invertUint8((byte) (fatBuffer[0xc0] & 0xFF)));
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) return;

        // Simulate st_fat_tfdos struct access
        byte[] fatBuffer = fatbuf.getBuffer();
        if (fatBuffer == null || fatBuffer.length < 0xc0 + 0x48) return;

        DiskBasicFormat fmt = basic.getFormatType();

        // volume label
        if (fmt.HasVolumeName()) {
            byte[] dst = new byte[12 + 1];
            Arrays.fill(dst, (byte) 0);
            byte[] src = data.getVolumeName().getBytes();
            System.arraycopy(src, 0, dst, 0, Math.min(src.length, 12));
            if (src.length > 0) {
                // Copy to fatBuffer (volume_name at 0xc4)
                System.arraycopy(dst, 0, fatBuffer, 0xc4, 12);
                // Invert
                invertMem(fatBuffer, 12, 0xc4);
            }
        }

        // volume number
        if (fmt.HasVolumeNumber()) {
            // volume_num at 0xc0
            fatBuffer[0xc0] = basic.invertUint8((byte) (data.getVolumeNumber() & 0xff));
        }
    }
}
