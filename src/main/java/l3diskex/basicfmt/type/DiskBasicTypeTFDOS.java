/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTFDOS.DirectoryTfdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Utils.TEMP_DATA_SIZE;


/**
 * TF-DOSの処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 *
 * @li IDString  セクタ1のIPL
 * @li VolumeString FAT領域にあるボリューム名
 * @li ReservedGroups 使用済みにするトラック
 */
public class DiskBasicTypeTFDOS extends DiskBasicTypeMZBase<DirectoryTfdos> {

    /** TF-DOS IPLセクタ */
    @Serdes
    static class st_ipl_tfdos {

        @Element(sequence = 1)
        public byte[] ipl = new byte[0xf0];
        // 起動時の自動実行コマンド
        // 0D以外の時、TF-DOSは+$00F0に格納された0D終端の文字列をコマンド
        // とみて自動実行します。
        @Element(sequence = 2)
        public byte[] auto_start = new byte[0x10];

        public static int SIZE = 0xf0 + 0x10;
    }

    /** TF-DOS FATセクタ */
    @Serdes
    static class st_fat_tfdos {

        @Element(sequence = 1)
        public byte[] fat = new byte[0xc0];
        // ボリューム番号 (0の時、Master)
        @Element(sequence = 2)
        public byte volume_num;
        @Element(sequence = 3)
        public byte reserved1;
        // ファイル管理番号 (TF-DOS V2.xでは必ず1)
        @Element(sequence = 4)
        public byte ident_number;
        // ディスク中のDOSシステムのVersion (TF-DOS V2.xでは必ず2)
        @Element(sequence = 5)
        public byte version_number;
        // マスターディスクには"TF-DOS MASTER"と記述されている。
        @Element(sequence = 6)
        public byte[] volume_name = new byte[12];
        @Element(sequence = 7)
        public byte[] reserved2 = new byte[0x2a];
        @Element(sequence = 8)
        public short x1_sector_size;
        @Element(sequence = 9)
        public byte[] reserved3 = new byte[4];
    }

    // BASEコンパチファイルかどうか
    private boolean is_base_compatible;

    public DiskBasicTypeTFDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryTfdos> dir) {
        super(basic, fat, dir);

        this.is_base_compatible = false;
    }

    /**
     * FATエリアをチェック
     */
    @Override
    public double checkFat(boolean is_formatting) throws IOException {
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
        byte[] fatBuf = fatbuf.getBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf), f);
        if (basic.invertUint8((byte) (f.ident_number & 0xff)) != 1) {
            return -1.0;
        }
        // ボリューム名
        byte[] volume_name = new byte[12];
        basic.invertMem(f.volume_name, f.volume_name.length, volume_name);
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

        // FATには未使用使用テーブルがある
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
    public int allocateUnitGroups(int fileunit_num, DiskBasicDirItem<DirectoryTfdos> item, int data_size, AllocateGroupFlags flags, DiskBasicGroups[] group_items) throws IOException {
        int[] file_size = {0};
        int[] groups = {0};

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

        // 領域を確保する
        rc = allocateGroupsSub(item, group_start[0], remain, sec_size, group_items[0], file_size, groups);

        return rc;
    }

    /**
     * データの読み込み/比較処理
     */
    @Override
    public int accessFile(int fileunit_num, DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, OutputStream ostream, byte[] sector_buffer, int sector_size, int remain_size, int sector_num, int sector_end) throws IOException {
        int size = (remain_size < sector_size ? remain_size : sector_size);

        if (ostream != null) {
            // 書き出し
            temp.setData(sector_buffer, size, basic.isDataInverted());

            ostream.write(temp.getData(), 0, temp.getSize());
        }
        if (istream != null) {
            // 読み込んで比較
            temp.setSize(size);
            istream.read(temp.getData(), 0, temp.getSize());

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
    public boolean convertDataForLoad(DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, OutputStream ostream) throws IOException {
        // BASEコンパチファイル
        is_base_compatible = (item.getFileAttr().isAscii() && item.getExternalAttr() == 1);

        int osize = istream.available();

        if (item.getFileAttr().isAscii() && item.getExternalAttr() > 0) {
            // BASEコンパチファイル 最終バイトが0かどうかチェック
            ((SeekableDataInputStream) istream).position(osize - 1);
            if (istream.read() == 0) {
                is_base_compatible = true;
                osize--;	// 最終データは出力しない
            }
            ((SeekableDataInputStream) istream).position(0);
        }
        if (is_base_compatible) {
            // BASEコンパチの場合、TABコード($14 -> $09)変換
            temp.setSize(TEMP_DATA_SIZE);
            while (osize > 0) {
                int len = istream.read(temp.getData(), 0, temp.getSize());
                if (len <= 0) break;
                // TABコード($14 -> $09)変換
                temp.replace((byte) 0x14, (byte) 0x09);
                ostream.write(temp.getData(), 0,  len > osize ? osize : len);
                osize -= len;
            }
        } else {
            // 変換しない
            byte[] buffer = new byte[4096];
            int len;
            while ((len = istream.read(buffer)) > 0) {
                ostream.write(buffer, 0, len);
            }
        }
        return true;
    }

    /**
     * エクスポートしたファイルをベリファイする際に内容を変換
     */
    @Override
    public boolean convertDataForVerify(DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, OutputStream ostream) throws IOException {
        int osize = istream.available();

        if (is_base_compatible) {
            // BASEコンパチの場合、TABコード($09 -> $14)変換
            temp.setSize(TEMP_DATA_SIZE);
            while (osize > 0) {
                int len = istream.read(temp.getData(), 0, temp.getSize());
                if (len <= 0) break;
                // TABコード($09 -> $14)変換
                temp.replace((byte) 0x09, (byte) 0x14);
                ostream.write(temp.getData(), 0,  len > osize ? osize : len);
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
        return (basic.invertUint8((byte) fat.get(1)) & 0xff) > group_num;	// invert
    }

    /**
     * セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // IPL
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));

            st_ipl_tfdos d_ipl = new st_ipl_tfdos();
            byte[] b = sector.getSectorBuffer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), d_ipl);
            if (st_ipl_tfdos.SIZE >= 0x100) {
                // IPL文字列を設定
                byte[] s_ipl = basic.diskBasicParam.getVariousStringParam("IDString").getBytes();
                int len = s_ipl.length;
                if (len > 0) {
                    if (len > st_ipl_tfdos.SIZE) len = st_ipl_tfdos.SIZE;
                    basic.invertMem(s_ipl, len, d_ipl.ipl); // Copy to 0x00
                }
                // 自動実行はなし (auto_start starts at 0xf0)
                for (int i = 0; i < 0x10; i++) {
                    d_ipl.auto_start[0xf0 + i] = basic.invertUint8((byte) 0x0d);
                }
            }
        }

        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        fatbuf.fill(basic.invertUint8(basic.diskBasicParam.getFillCodeOnFAT()));

        st_fat_tfdos f = new st_fat_tfdos ();
        byte[] b = fatbuf.getBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), f);

        // システムエリアは使用済みにする
        List<Integer> grps = basic.diskBasicParam.getReservedGroups();
        for (int grp : grps) {
            if (grp >= 0 && grp < 0xc0) {
                f.fat[grp] = basic.invertUint8((byte) basic.diskBasicParam.getGroupSystemCode());
            }
        }
        // オーバートラック部分は使用済みにする
        for (int pos = basic.getFatEndGroup() + 1; pos < 0xc0; pos++) {
            f.fat[pos] = basic.invertUint8((byte) basic.diskBasicParam.getGroupSystemCode());
        }

        // ボリューム番号を設定
        int vol_num = data.getVolumeNumber();
        f.volume_num = basic.invertUint8((byte) vol_num);
        // バージョン番号を設定
        f.ident_number = basic.invertUint8((byte) 1);
        f.version_number = basic.invertUint8((byte) 2);
        // ボリューム名を設定
        byte[] vol_name;
        if (!data.getVolumeName().isEmpty()) {
            vol_name = data.getVolumeName().getBytes();
        } else {
            vol_name = basic.diskBasicParam.getVariousStringParam("VolumeString").getBytes();
        }
        System.arraycopy(vol_name, 0, f.volume_name, 0, f.volume_name.length);
        basic.invertMem(f.volume_name, f.volume_name.length);

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

    /**
     * ファイルをセーブする前にデータを変換
     */
    @Override
    public boolean convertDataForSave(DiskBasicDirItem<DirectoryTfdos> item, InputStream istream, OutputStream ostream) throws IOException {
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
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        st_fat_tfdos f = new st_fat_tfdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), f);

        // volume label
        byte[] vol_name = new byte[12 + 1];
        basic.invertMem(f.volume_name, vol_name.length, vol_name);
        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(vol_name, 0, 12, sb, -1);
        String dst = sb.toString();
        data.setVolumeName(dst);
        // volume number
        data.setVolumeNumber(basic.invertUint8(f.volume_num) & 0xff);
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FATエリア
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        st_fat_tfdos f = new st_fat_tfdos();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), f);

        DiskBasicFormat fmt = basic.getFormatType();

        // volume label
        if (fmt.hasVolumeName()) {
            byte[] dst = new byte[f.volume_name.length + 1];
            int l = basic.getCharCodes().convToChars(data.getVolumeName(), dst, dst.length);
            if (l > 0) {
                System.arraycopy(dst, 0, f.volume_name, 0, f.volume_name.length);
                basic.invertMem(f.volume_name, f.volume_name.length);
            }
        }
        // volume number
        if (fmt.hasVolumeNumber()) {
            f.volume_num = basic.invertUint8((byte) data.getVolumeNumber());
        }
    }
}
