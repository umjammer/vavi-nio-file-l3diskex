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

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTFDOS.DirectoryTfDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Utils.TEMP_DATA_SIZE;
import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_TFDOS;


/**
 * TF-DOSの処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 *
 * <li>IDString  セクタ1のIPL</li>
 * <li>VolumeString FAT領域にあるボリューム名</li>
 * <li>ReservedGroups 使用済みにするトラック</li>
 */
public class DiskBasicTypeTFDOS extends DiskBasicTypeMZBase<DirectoryTfDos> {

    /** TF-DOS IPLセクタ */
    @Serdes
    static class TfDosIpl {

        @Element(sequence = 1)
        public byte[] ipl = new byte[0xf0];
        // 起動時の自動実行コマンド
        // 0D以外の時、TF-DOSは+$00F0に格納された0D終端の文字列をコマンド
        // とみて自動実行します。
        @Element(sequence = 2)
        public byte[] autoStart = new byte[0x10];

        public static int SIZE = 0xf0 + 0x10;
    }

    /** TF-DOS FATセクタ */
    @Serdes
    static class TfDosFat {

        @Element(sequence = 1)
        public byte[] fat = new byte[0xc0];
        // ボリューム番号 (0の時、Master)
        @Element(sequence = 2)
        public byte volumeNum;
        @Element(sequence = 3)
        public byte reserved1;
        // ファイル管理番号 (TF-DOS V2.xでは必ず1)
        @Element(sequence = 4)
        public byte identNumber;
        // ディスク中のDOSシステムのVersion (TF-DOS V2.xでは必ず2)
        @Element(sequence = 5)
        public byte versionNumber;
        // マスターディスクには"TF-DOS MASTER"と記述されている。
        @Element(sequence = 6)
        public byte[] volumeName = new byte[12];
        @Element(sequence = 7)
        public byte[] reserved2 = new byte[0x2a];
        @Element(sequence = 8)
        public short x1SectorSize;
        @Element(sequence = 9)
        public byte[] reserved3 = new byte[4];
    }

    /** BASEコンパチファイルかどうか */
    private boolean isBaseCompatible;

    @Override
    public boolean isSupported(DiskBasicFormatType typeNumber) {
        return typeNumber == FORMAT_TYPE_TFDOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryTfDos> dir) {
        super.init(basic, fat, dir);

        this.isBaseCompatible = false;
    }

    /**
     * FATエリアをチェック
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        // グループサイズをトラックごとに調整
        basic.setSectorsPerGroup(basic.getSectorsPerTrack());

        // 最終グループ番号
        int maxGroup = basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() - 1;
        basic.setFatEndGroup(maxGroup);

        // FATエリア
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return -1.0;
        }

        // ファイル管理番号
        TfDosFat f = new TfDosFat();
        byte[] b = fatBuf.getBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), f);
        if (basic.invertUint8((byte) (f.identNumber & 0xff)) != 1) {
            return -1.0;
        }
        // ボリューム名
        byte[] volumeName = new byte[12];
        basic.invertMemory(f.volumeName, f.volumeName.length, volumeName);
        if (!new String(volumeName, 0, 6).equals("TF-DOS")) {
            return -1.0;
        }

        // 0 か 0xff 以外は無効
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup() && groupNum < fatBuf.getSize(); groupNum++) {
            int value = basic.invertUint8((byte) fatBuf.get(groupNum));
            if (value != basic.getGroupUnusedCode() && value != basic.getGroupSystemCode()) {
                validRatio = -1.0;
                break;
            }
        }

        return validRatio;
    }

    /**
     * FAT位置をセット
     */
    @Override
    public void setGroupNumber(int num, int val) {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return;
        }

        if (num < fatBuf.getSize()) {
            int value = val != 0 ? basic.getGroupSystemCode() : basic.getGroupUnusedCode();
            fatBuf.set(num, basic.invertUint8((byte) value));
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

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return true;
        }

        // FATには未使用使用テーブルがある
        if (num < fatBuf.getSize()) {
            if (basic.invertUint8((byte) fatBuf.get(num)) != basic.getGroupUnusedCode()) {
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
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryTfDos> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int[] fileSize = {0};
        int[] groups = {0};

        int rc = 0;
        int remain = dataSize;
        int sectorSize = basic.getSectorSize();

        // 必要なグループ数
        int groupSize = ((dataSize - 1) / sectorSize / basic.getSectorsPerGroup()) + 1;

        // 未使用が連続している位置をさがす
        int[] groupStart = new int[1]; // Simulate pass-by-reference
        int count = findContinuousArea(groupSize, groupStart);
        if (count < groupSize) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // 開始グループ決定
        item.setStartGroup(fileUnitNum, groupStart[0]);

        // 領域を確保する
        rc = allocateGroupsSub(item, groupStart[0], remain, sectorSize, groupItems[0], fileSize, groups);

        return rc;
    }

    /**
     * データの読み込み/比較処理
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, OutputStream oStream,
                          byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        int size = (remainSize < sectorSize ? remainSize : sectorSize);

        byte[] temp;
        if (oStream != null) {
            // 書き出し
            temp = Arrays.copyOfRange(sectorBuffer, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            oStream.write(temp, 0, temp.length);
        }
        if (iStream != null) {
            // 読み込んで比較
            temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, temp.length, sectorBuffer, 0, size)) {
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
    public boolean convertDataForLoad(DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        // BASEコンパチファイル
        isBaseCompatible = (item.getFileAttr().isAscii() && item.getExternalAttr() == 1);

        int oSize = iStream.available();

        if (item.getFileAttr().isAscii() && item.getExternalAttr() > 0) {
            // BASEコンパチファイル 最終バイトが0かどうかチェック
            ((SeekableDataInputStream) iStream).position(oSize - 1);
            if (iStream.read() == 0) {
                isBaseCompatible = true;
                oSize--;	// 最終データは出力しない
            }
            ((SeekableDataInputStream) iStream).position(0);
        }
        if (isBaseCompatible) {
            // BASEコンパチの場合、TABコード($14 -> $09)変換
            byte[] temp = new byte[TEMP_DATA_SIZE];
            while (oSize > 0) {
                int len = iStream.readNBytes(temp, 0, temp.length);
                if (len <= 0) break;
                // TABコード($14 -> $09)変換
                for (int pos = 0; pos < temp.length; pos++) {
                    if (temp[pos] == 0x14) temp[pos] = 0x09;
                }
                oStream.write(temp, 0,  len > oSize ? oSize : len);
                oSize -= len;
            }
        } else {
            // 変換しない
            byte[] buffer = new byte[4096];
            int len;
            while ((len = iStream.read(buffer)) > 0) {
                oStream.write(buffer, 0, len);
            }
        }
        return true;
    }

    /**
     * エクスポートしたファイルをベリファイする際に内容を変換
     */
    @Override
    public boolean convertDataForVerify(DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        int oSize = iStream.available();

        if (isBaseCompatible) {
            // BASEコンパチの場合、TABコード($09 -> $14)変換
            byte[] temp = new byte[TEMP_DATA_SIZE];
            while (oSize > 0) {
                int len = iStream.read(temp, 0, temp.length);
                if (len <= 0) break;
                // TABコード($09 -> $14)変換
                for (int pos = 0; pos < temp.length; pos++) {
                    if (temp[pos] == 0x09) temp[pos] = 0x14;
                }
                oStream.write(temp, 0,  len > oSize ? oSize : len);
                oSize -= len;
            }
            // 最後に$00を出力
            oStream.write(0);
        } else {
            // 変換しない
            byte[] buffer = new byte[4096];
            int len;
            while ((len = iStream.read(buffer)) > 0) {
                oStream.write(buffer, 0, len);
            }
        }
        return true;
    }

    /**
     * グループ番号から最終セクタ番号を得る
     */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int endSector = sectorStart;
        int groupSize = basic.getSectorsPerGroup() * sectorSize;
        if (remainSize < groupSize) {
            endSector += ((remainSize + sectorSize - 1) / sectorSize) - 1;
        } else {
            endSector += basic.getSectorsPerGroup() - 1;
        }
        return endSector;
    }

    /**
     * ルートディレクトリか
     */
    @Override
    public boolean isRootDirectory(int groupNum) {
        // オフセット未満だったらルート
        return (basic.invertUint8((byte) fat.get(1)) & 0xff) > groupNum;	// invert
    }

    /**
     * セクタデータを埋めた後の個別処理
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // IPL
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));

            TfDosIpl ipl = new TfDosIpl();
            byte[] b = sector.getSectorBuffer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), ipl);
            if (TfDosIpl.SIZE >= 0x100) {
                // IPL文字列を設定
                byte[] iplBytes = basic.getVariousStringParam("IDString").getBytes();
                int len = iplBytes.length;
                if (len > 0) {
                    if (len > TfDosIpl.SIZE) len = TfDosIpl.SIZE;
                    basic.invertMemory(iplBytes, len, ipl.ipl); // Copy to 0x00
                }
                // 自動実行はなし (autoStart starts at 0xf0)
                for (int i = 0; i < 0x10; i++) {
                    ipl.autoStart[0xf0 + i] = basic.invertUint8((byte) 0x0d);
                }
            }
        }

        // FATエリア
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        fatBuf.fill(basic.invertUint8(basic.getFillCodeOnFAT()));

        TfDosFat f = new TfDosFat();
        byte[] b = fatBuf.getBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), f);

        // システムエリアは使用済みにする
        List<Integer> groups = basic.getReservedGroups();
        for (int group : groups) {
            if (group >= 0 && group < 0xc0) {
                f.fat[group] = basic.invertUint8((byte) basic.getGroupSystemCode());
            }
        }
        // オーバートラック部分は使用済みにする
        for (int pos = basic.getFatEndGroup() + 1; pos < 0xc0; pos++) {
            f.fat[pos] = basic.invertUint8((byte) basic.getGroupSystemCode());
        }

        // ボリューム番号を設定
        int volumeNumber = data.getVolumeNumber();
        f.volumeNum = basic.invertUint8((byte) volumeNumber);
        // バージョン番号を設定
        f.identNumber = basic.invertUint8((byte) 1);
        f.versionNumber = basic.invertUint8((byte) 2);
        // ボリューム名を設定
        byte[] volumeName;
        if (!data.getVolumeName().isEmpty()) {
            volumeName = data.getVolumeName().getBytes();
        } else {
            volumeName = basic.getVariousStringParam("VolumeString").getBytes();
        }
        System.arraycopy(volumeName, 0, f.volumeName, 0, f.volumeName.length);
        basic.invertMemory(f.volumeName, f.volumeName.length);

        // DIRエリア
        int[] trackNum = new int[1], sideNum = new int[1], sectorNum = new int[1];
        for (int sectorPos = basic.getDirStartSector(); sectorPos <= basic.getDirEndSector(); sectorPos++) {
            getNumFromSectorPos(sectorPos - 1, trackNum, sideNum, sectorNum);
            sector = basic.getSector(trackNum[0], sideNum[0], sectorNum[0]);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.getFillCodeOnDir()));
            }
        }

        return true;
    }

    /**
     * ファイルをセーブする前にデータを変換
     */
    @Override
    public boolean convertDataForSave(DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        // BASEコンパチファイル
        isBaseCompatible = item.getFileAttr().isAscii() && item.getExternalAttr() == 1;

        // 処理はベリファイと同じ
        return convertDataForVerify(item, iStream, oStream);
    }

    /**
     * データの書き込み処理
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, byte[] buffer, int size, int remain,
                         int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;

        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                byte[] temp = new byte[remain];
                iStream.readNBytes(temp, 0, temp.length);

                System.arraycopy(temp, 0, buffer, 0, temp.length);
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            byte[] temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);

            System.arraycopy(temp, 0, buffer, 0, temp.length);

            len = size;
        }

        // 反転
        basic.invertMemory(buffer, size);

        return len;
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FATエリア
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        DiskBasicTypeTFDOS.TfDosFat f = new TfDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), f);

        // volume label
        byte[] volumeName = new byte[12 + 1];
        basic.invertMemory(f.volumeName, volumeName.length, volumeName);
        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(volumeName, 0, 12, sb, -1);
        data.setVolumeName(sb.toString());
        // volume number
        data.setVolumeNumber(basic.invertUint8(f.volumeNum) & 0xff);
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FATエリア
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        TfDosFat f = new TfDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), f);

        DiskBasicFormat format = basic.getFormatType();

        // volume label
        if (format.hasVolumeName()) {
            byte[] dst = new byte[f.volumeName.length + 1];
            int l = basic.getCharCodes().convToChars(data.getVolumeName(), dst, dst.length);
            if (l > 0) {
                System.arraycopy(dst, 0, f.volumeName, 0, f.volumeName.length);
                basic.invertMemory(f.volumeName, f.volumeName.length);
            }
        }
        // volume number
        if (format.hasVolumeNumber()) {
            f.volumeNum = basic.invertUint8((byte) data.getVolumeNumber());
        }
    }
}
