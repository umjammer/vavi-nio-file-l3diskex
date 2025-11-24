package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS.DirectoryMzFDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/**
 * MZ Floppy DOSの処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 * <li>IPLString : セクタ1のIPL</li>
 */
public class DiskBasicTypeMZFDOS extends DiskBasicTypeMZBase<DirectoryMzFDos> {

    /** 使用状況セクタ */
    @Serdes(bigEndian = false)
    static class MzFDosFat {

        @Element(sequence = 1)
        public byte[] reserved1 = new byte[32];
        // TODO: unknown
        @Element(sequence = 2)
        public byte sides;
        @Element(sequence = 3)
        public byte volumeNum;
        @Element(sequence = 4)
        public byte[] sign = new byte[17];
        // sector
        @Element(sequence = 5)
        public short emptyStart;
        @Element(sequence = 6)
        public byte[] map = new byte[203];
    }

    public static final int FORMAT_TYPE_MZ_FDOS = 73;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_MZ_FDOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMzFDos> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * 使用しているグループの位置を得る
     */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3);
    }

    /**
     * 次のグループ番号を得る
     * <p>
     * セクタ末尾に次のトラック＆セクタ番号がある
     */
    @Override
    public int getNextGroupNumber(int num, int sectorPos) {
        int[] trackNum = {0}, sideNum = {0}, sectorNum = {0};
        basic.calcNumFromSectorPosForGroup(sectorPos, trackNum, sideNum, sectorNum, null, null);
        DiskImageSector sector = basic.getSector(trackNum[0], sideNum[0], sectorNum[0]);
        if (sector == null) return 0;

        byte[] b = sector.getSectorBuffer();
        int s = sector.getSectorSize();
        byte nextTrack = basic.invertUint8(b[s - 2]);
        byte nextSector = basic.invertUint8(b[s - 1]);
        return basic.calcSectorPosFromNumTForGroup(nextTrack, nextSector);
    }

    /**
     * FATエリアをチェック
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        // FATエリア
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) {
            return -1.0;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return -1.0;
        }
        MzFDosFat f = new MzFDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);

        byte sides = basic.invertUint8(f.sides);
        if (sides == 0 || sides >= basic.getTracksPerSide()) {
            validRatio = -1.0;
        }
        //dataStartGroup = startTrack * basic.getSectorsPerTrackOnBasic();
        // 最終グループ番号
        if (basic.getFatEndGroup() == 0) {
            basic.setFatEndGroup(basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);
        }
        return validRatio;
    }

    /**
     * ファイルをセーブする前の準備を行う
     *
     * @param iStream  ストリームバッファ
     * @param fileSize [in,out] 出力サイズ
     * @param pItem    [in,out] ファイル名、属性を持っているディレクトリアイテム
     * @param nItem    [in,out] 確保したディレクトリアイテム
     * @param errInfo  [in,out] エラー情報
     */
    @Override
    public boolean prepareToSaveFile(InputStream iStream, int[] fileSize, DiskBasicDirItem<DirectoryMzFDos> pItem, DiskBasicDirItem<DirectoryMzFDos> nItem, DiskBasicError errInfo) throws IOException {
        // Chain用のセクタを確保する
        int groupNum = getEmptyGroupNumber();
        if (groupNum == INVALID_GROUP_NUMBER) {
            return false;
        }
        // セクタ
        DiskImageSector sector = basic.getSectorFromGroup(groupNum);
        if (sector == null) {
            return false;
        }
        byte[] c = sector.getSectorBuffer();
        if (c == null) {
            return false;
        }
        sector.fill(basic.invertUint8((byte) 0));
        // チェイン情報にセクタをセット
        nItem.setChainSector(sector, c, null);

        // 開始グループを設定
        nItem.setStartGroup(0, groupNum);

        // セクタを予約
        setGroupNumber(groupNum, 1);
        DiskBasicDirItemMZFDOS ditem = (DiskBasicDirItemMZFDOS) nItem;
        ditem.setChainUsedSector(groupNum, true);

        return true;
    }

    /**
     * データサイズ分のグループを確保する
     *
     * @param fileUnitNum ファイル番号
     * @param item        [in,out] ディレクトリアイテム
     * @param dataSize    確保するデータサイズ（バイト）
     * @param flags       新規か追加か
     * @param groupItems  [out] 確保したセクタリスト
     * @return >0:正常 -1:空きなし(開始グループ設定前) -2:空きなし(開始グループ設定後)
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int[] fileSize = {0};
        int[] groups = {0};

        int rc = 0;
        //int groupNum = 0;
        int remain = dataSize;
        boolean isChain = item.needChainInData();
        int sectorSize = basic.getSectorSize();
        if (isChain) {
            sectorSize -= 2;
        }

        // 必要なグループ数
        int groupSize = ((dataSize - 1) / sectorSize / basic.getSectorsPerGroup()) + 1;
        if (isChain) {
            groupSize = 1;
        }

        // 未使用が連続している位置をさがす
        int[] groupStart = {0};
        int count = findContinuousArea(groupSize, groupStart);
        if (count < groupSize) {
            // 十分な空きがない
            rc = -1;
            return rc;
        }

        // データの開始グループ決定
        DiskBasicDirItemMZFDOS dItem = (DiskBasicDirItemMZFDOS) item;
        dItem.setDataGroup(groupStart[0]);
        // シーケンス番号
        dItem.assignSeqNumber();

        // 領域を確保する
        rc = allocateGroupsSub(item, groupStart[0], remain, sectorSize, groupItems[0], fileSize, groups);

        // 確保したグループ数をセット
        dItem.setGroupSize(groups[0] + 1);

        // FATの空き位置を更新
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector != null) {
            byte[] sectorBuffer = sector.getSectorBuffer();
            if (sectorBuffer != null) {
                MzFDosFat f = new MzFDosFat();
                Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f); // TODO write back
                int groupEnd = groupItems[0].last().group + 1;
                int emptyStart = basic.invertAndOrderUint16(f.emptyStart);
                if (emptyStart < groupEnd) {
                    f.emptyStart = basic.invertAndOrderUint16((short) groupEnd);
                }
            }
        }

        return rc;
    }

    /**
     * グループを確保して使用中にする
     */
    @Override
    public int allocateGroupsSub(DiskBasicDirItem<DirectoryMzFDos> item, int groupStart, int remain, int secSize, DiskBasicGroups groupItems, int[] fileSize, int[] groups) throws IOException {
        int rc = 0;
        int groupNum = groupStart;
        int prevGroup = 0;

        DiskBasicDirItemMZFDOS dItem = (DiskBasicDirItemMZFDOS) item;

        int limit = basic.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            // 使用しているか
            boolean usedGroup = isUsedGroupNumber(groupNum);
            if (!usedGroup) {
                if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
                    // 使用済みにする
                    basic.getNumsFromGroup(prevGroup, groupNum, secSize, remain, groupItems);
                    setGroupNumber(prevGroup, 1);
                    dItem.setChainUsedSector(prevGroup, true);
                    fileSize[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
                    groups[0]++;
                }
                remain -= (secSize * basic.getSectorsPerGroup());
                prevGroup = groupNum;
            }
            // 次のグループ
            groupNum++;
            limit--;
        }
        if (prevGroup > 0 && prevGroup <= basic.getFatEndGroup()) {
            // 使用済みにする
            basic.getNumsFromGroup(prevGroup, 0, secSize, remain, groupItems);
            setGroupNumber(prevGroup, 1);
            dItem.setChainUsedSector(prevGroup, true);
            fileSize[0] += (basic.getSectorSize() * basic.getSectorsPerGroup());
            groups[0]++;
        }
        if (prevGroup > basic.getFatEndGroup()) {
            // ファイルがオーバフローしている
            rc = -2;
        } else if (limit < 0) {
            // 無限ループ？
            rc = -2;
        }
        return rc;
    }

    /**
     * データの読み込み/比較処理
     *
     * @param fileUnitNum  ファイル番号
     * @param item         ディレクトリアイテム
     * @param iStream      [in,out] 入力ストリーム ベリファイ時に使用 データ読み出し時は {@code null}
     * @param oStream      [in,out] 出力先 データ読み出し時に使用 ベリファイ時は {@code null}
     * @param sectorBuffer セクタバッファ
     * @param sectorSize   バッファサイズ
     * @param remainSize   残りサイズ
     * @param sectorNum    セクタ番号
     * @param sectorEnd    最終セクタ番号
     * @return >=0: 処理したサイズ, -1: 比較不一致, -2: セクタがおかしい
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryMzFDos> item, InputStream iStream, OutputStream oStream,
                          byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        boolean needChain = item.needChainInData();

        if (needChain) {
            // セクタの最終バイトはチェイン用セクタ番号がある
            sectorSize -= 2;
        }

        int size = remainSize < sectorSize ? remainSize : sectorSize;

        byte[] temp;
        if (oStream != null) {
            temp = Arrays.copyOfRange(sectorBuffer, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            oStream.write(temp, 0, temp.length);
        }
        if (iStream != null) {
            // 読み込んで比較
            temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, temp.length, sectorBuffer, 0, temp.length)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /**
     * セクタデータを埋めた後の個別処理
     * フォーマット FAT予約済みをセット
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        DiskImageSector sector;

        //
        // FATエリア
        //
        sector = basic.getSectorFromSectorPos(basic.getFatStartSector() - 1);
        if (sector == null) {
            return false;
        }
        sector.fill(basic.getFillCodeOnFAT());

        byte[] buf = sector.getSectorBuffer();
        if (buf == null) {
            return false;
        }
        int size = sector.getSectorBufferSize();

        MzFDosFat fdat = new MzFDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(buf), fdat);

        for (int i = 0; i < 32; i++) {
            fdat.reserved1[i] = (short) 0;
        }

        fdat.sides = (byte) basic.getSidesPerDiskOnBasic();

        fdat.volumeNum = (byte) data.getVolumeNumber();

        Arrays.fill(fdat.sign, 0, fdat.sign.length, basic.getDirSpaceCode());
        byte[] volumeName = data.getVolumeName().getBytes();
        if (volumeName.length > 0) {
            int len = volumeName.length;
            if (len >=  fdat.sign.length) len =  fdat.sign.length - 1;
            System.arraycopy(volumeName, 0, fdat.sign, 0, len);
        }

        // システムエリアは使用済みにする
        int groupNumStart = 0;
        int groupNumEnd = basic.getDirEndSector();

        fdat.emptyStart = basic.orderUint16((short) groupNumEnd);
        for (int groupNum = groupNumStart; groupNum < groupNumEnd; groupNum++) {
            int[] pos = {groupNum};
            int[] mask = {0};
            calcUsedGroupPos(groupNum, pos, mask);
            fdat.map[pos[0]] = (byte) (fdat.map[pos[0]] | mask[0]);
        }
        // TODO serialize

        // invert
        basic.invertMemory(buf, size);

        //
        // MZ DISK BASICが使用するFATエリアは使用済みとして初期化する
        //
        sector = basic.getSectorFromSectorPos(15);
        if (sector != null) {
            sector.fill(basic.invertUint8((byte) 0xff));
            sector.fill(basic.invertUint8((byte) 0), 4, 2);
        }
        sector = basic.getSectorFromSectorPos(13);
        if (sector != null) {
            sector.fill(basic.invertUint8((byte) 0xff));
            sector.fill(basic.invertUint8((byte) 0), 1, 0);
        }

        //
        // DIRエリア
        //
        int[] trackNum = {0}, sideNum = {0}, sectorNum = {0};
        //int index = 0;
        for (int sectorPos = basic.getDirStartSector(); sectorPos <= basic.getDirEndSector(); sectorPos++) {
            getNumFromSectorPos(sectorPos - 1, trackNum, sideNum, sectorNum);
            sector = basic.getSector(trackNum[0], sideNum[0], sectorNum[0]);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));
            }
        }

        return true;
    }

    /**
     * データの書き込み処理
     *
     * @param item       ディレクトリアイテム
     * @param iStream    ストリームデータ
     * @param buffer     [out] セクタ内の書き込み先バッファ
     * @param size       書き込み先バッファサイズ
     * @param remain     残りのデータサイズ
     * @param sectorNum セクタ番号
     * @param groupNum  現在のグループ番号
     * @param nextGroup 次のグループ番号
     * @param sectorEnd 最終セクタ番号
     * @param seqNum    通し番号(0...)
     * @return 書き込んだバイト数
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryMzFDos> item, InputStream iStream, byte[] buffer, int size, int remain,
                         int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needChain = item.needChainInData();

        int len = 0;
        if (needChain) {
            size -= 2;
        }

        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (remain > 0) {
                iStream.readNBytes(buffer, 0, remain);
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        // チェーン用のトラック＆セクタ番号を書く
        if (needChain) {
            int nextSector = groupNum * basic.getSectorsPerGroup();
            // 次のデータがあるセクタ番号を入れる
            if (sectorNum < sectorEnd) {
                nextSector++;
            } else {
                nextSector = (remain > size ? nextGroup * basic.getSectorsPerGroup() : 0);
            }
            nextSector /= basic.getSectorsPerGroup();
            if (nextSector > 0) {
                int[] track = {0};
                int[] side = {0};
                int[] sector = {0};
                basic.calcNumFromSectorPosForGroup(nextSector, track, side, sector, null, null);
                track[0] *= basic.getSidesPerDiskOnBasic();
                track[0] += basic.getReversedSideNumber(side[0]);
                buffer[size] = basic.invertUint8((byte) track[0]);
                buffer[size + 1] = basic.invertUint8((byte) sector[0]);
            } else {
                buffer[size] = basic.invertUint8((byte) 0);
                buffer[size + 1] = basic.invertUint8((byte) 0);
            }
        }

        // 反転
        basic.invertMemory(buffer, size);

        return len;
    }

    /**
     * データの書き込み終了後の処理
     */
    @Override
    public void additionalProcessOnSavedFile(DiskBasicDirItem<DirectoryMzFDos> item) {
        DiskBasicDirItemMZFDOS dItem = (DiskBasicDirItemMZFDOS) item;
        dItem.setUnknownData();
    }

    /**
     * IPLや管理エリアの属性を得る
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) {
            return;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return;
        }
        MzFDosFat f = new MzFDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);
        // ボリューム番号
        data.setVolumeNumber(basic.invertUint8(f.volumeNum));
        // サイン
        byte[] sign = new byte[17];
        basic.invertMemory(f.sign, 17, sign);
        data.setVolumeName(new String(sign));
        data.setVolumeNameMaxLength(17);
    }

    /**
     * IPLや管理エリアの属性をセット
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT
        DiskImageSector sector = basic.getManagedSector(basic.getFatStartSector() - 1);
        if (sector == null) {
            return;
        }
        byte[] sectorBuffer = sector.getSectorBuffer();
        if (sectorBuffer == null) {
            return;
        }
        MzFDosFat f = new MzFDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(sectorBuffer), f);

        // ボリューム番号
        f.volumeNum = basic.invertUint8((byte) data.getVolumeNumber());
        // サイン
        Arrays.fill(f.sign, basic.invertUint8(basic.getDirSpaceCode()));
        byte[] volumeName = data.getVolumeName().getBytes();
        if (volumeName.length > 0) {
            int len = volumeName.length;
            if (len >= f.sign.length) len = f.sign.length - 1;
            System.arraycopy(volumeName, 0, f.sign, 0, f.sign.length);
        }
        // TODO deserialize
    }
}