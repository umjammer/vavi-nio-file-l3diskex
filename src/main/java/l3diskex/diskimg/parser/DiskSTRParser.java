/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;

import static java.lang.System.getLogger;


/**
 * X68000/PC9801用 DSK STR ディスクパーサ
 */
public class DiskSTRParser extends DiskImageParser {

    private static final Logger logger = getLogger(DiskSTRParser.class.getName());

    /**
     * DSKSTR 2次圧縮展開後バッファ
     */
    public static class Expand2FIFOBuffer extends Utils.FIFOBuffer {

        private long lastPos;
        private final long[] iStreamPos = new long[9];
        private final long[] eStreamPos = new long[9];

        public Expand2FIFOBuffer() {
            super();
            clear();
        }

        @Override
        public void clear() {
            super.clear();
            lastPos = 0;
            Arrays.fill(iStreamPos, 0);
            Arrays.fill(eStreamPos, 0);
        }

        public void setLastPos(long val) {
            lastPos = val;
        }

        public long getLastPos() {
            return lastPos;
        }

        public void setIStreamPos(int idx, long val) {
            iStreamPos[idx] = val;
        }

        public void setEStreamPos(int idx, long val) {
            eStreamPos[idx] = val;
        }

        public long getIStreamPos(int idx) {
            return iStreamPos[idx];
        }

        public long getEStreamPos(int idx) {
            return eStreamPos[idx];
        }
    }

    private int compressType;
    /** 入力データの圧縮形式 0:非圧縮 bit0:1次圧縮 bit1:2次圧縮 */
    private Expand2FIFOBuffer eStream = new Expand2FIFOBuffer();

    /**
     * DSKSTRヘッダ
     */
    private static class StrHeader {

        int dataSize; // BE
        byte[] reserved = new byte[28];

        public static final int SIZE = 4 + 28;

        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[SIZE];
            if (is.read(buf) != SIZE) {
                throw new IOException("Failed to read DSKSTR header");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
            dataSize = bb.getInt();
            bb.get(reserved);
        }
    }

    /**
     * DSKSTRトラックヘッダ
     */
    private static class StrTrackHeader {

        byte attr;
        byte secs;
        short off1; // BE
        short off2;
        short offD;
        short dat1;
        short dat2;
        byte[] reserved = new byte[4];

        public static final int SIZE = 1 + 1 + 2 + 2 + 2 + 2 + 2 + 4;

        // TODO Serdes
        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[SIZE];
            if (is.read(buf) != SIZE) {
                throw new IOException("Failed to read DSKSTR track header");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
            attr = bb.get();
            secs = bb.get();
            off1 = bb.getShort();
            off2 = bb.getShort();
            offD = bb.getShort();
            dat1 = bb.getShort();
            dat2 = bb.getShort();
            bb.get(reserved);
        }

        public short getOff1LE() {
            return Short.reverseBytes(off1);
        }

        public short getOff2LE() {
            return Short.reverseBytes(off2);
        }

        public short getOffdLE() {
            return Short.reverseBytes(offD);
        }
    }

    /**
     * セクタID
     */
    private static class StrSectorId {

        byte c;
        byte h;
        byte r;
        byte n;

        public static final int SIZE = 4;

        // TODO Serdes
        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[SIZE];
            if (is.read(buf) != SIZE) {
                throw new IOException("Failed to read DSKSTR sector ID");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf);
            c = bb.get();
            h = bb.get();
            r = bb.get();
            n = bb.get();
        }
    }

    @Override
    public boolean isSupported(String type) {
        return "dskstr".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
        compressType = 0;
    }

    /**
     * セクタデータの作成
     *
     * @param iStream       ディスクイメージ
     * @param diskNumber    ディスク番号
     * @param trackNumber   トラック番号
     * @param sideNumber    サイド番号
     * @param numOfSectors  セクタ数
     * @param sectorNumber  セクタ番号
     * @param sectorSize    セクタサイズ
     * @param singleDensity 単密度か
     * @param track         トラック
     * @return ヘッダ込みのセクタサイズ
     */
    private static int parseSector(InputStream iStream, int diskNumber, int trackNumber, int sideNumber, int numOfSectors,
                                   int sectorNumber, int sectorSize, boolean singleDensity, DiskImageTrack track) throws IOException {
        // セクタ作成
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, numOfSectors, false, 0);
        track.add(sector);

        byte[] buffer = sector.getSectorBuffer();

        // plain data
        iStream.readNBytes(buffer, 0, sectorSize);

        sector.setSingleDensity(singleDensity);
        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    /**
     * トラックデータの作成
     *
     * TODO check seekable
     *
     * @param iStream    ディスクイメージ
     * @param diskNumber ディスク番号
     * @param offsetPos  オフセット番号
     * @param offset     オフセット位置
     * @param disk       ディスク
     * @return -1: エラー or 終り, >0: トラックサイズ
     */
    private int parseTrack(InputStream iStream, int diskNumber, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        StrTrackHeader trackHeader = new DiskSTRParser.StrTrackHeader();

        // 圧縮データを展開
        ByteArrayOutputStream oestream = new ByteArrayOutputStream();
        int oeLimit = StrTrackHeader.SIZE;
        int rc = expandFirst(iStream, oestream, oeLimit);

        // ヘッダをチェック
        byte[] trackHeaderBytes = oestream.toByteArray();
        ByteArrayInputStream ieStreamHeader = new ByteArrayInputStream(trackHeaderBytes);
        trackHeader.read(ieStreamHeader);
        int len = trackHeaderBytes.length;

        if (rc < 0 || len < StrTrackHeader.SIZE || trackHeader.attr == 0) {
            // end of file
            return -1;
        }

        int sectorsPerTrack = trackHeader.secs & 0xFF;
        if (sectorsPerTrack <= 0) {
            result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
            return -1;
        }

        // データ開始位置
        oeLimit = trackHeader.getOffdLE() & 0xffff;
        // 圧縮データを展開つづき
        rc = expandNext(iStream, oestream, oeLimit);

        byte[] attr = new byte[256];
        Arrays.fill(attr, (byte) 0);

        DiskSTRParser.StrSectorId[] id = new StrSectorId[256];
        for (int i = 0; i < id.length; i++) id[i] = new StrSectorId();

        trackHeaderBytes = oestream.toByteArray();
        ByteArrayInputStream ieStream = new ByteArrayInputStream(trackHeaderBytes);

        // セクタ属性を得る
        ieStream.skipNBytes(trackHeader.getOff1LE() & 0xffff);

        for (int sec = 0; sec < sectorsPerTrack; sec += 4) {
            // 4バイト境界
            int readLen = ieStream.read(attr, sec, 4);
            if (readLen < 4) {
                result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
                return -1;
            }
        }

        // セクタIDを得る
        ieStream.skipNBytes(trackHeader.getOff2LE() & 0xffff);

        for (int sec = 0; sec < sectorsPerTrack; sec++) {
            // C H R N
            try {
                id[sec].read(ieStream);
                len = StrSectorId.SIZE;
            } catch (IOException e) {
                len = 0;
            }

            if (len < StrSectorId.SIZE) {
                result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
                return -1;
            }

            int sectorSize = (128 << (id[sec].n & 0xff));
            if ((id[sec].n & 0xff) > 5) {
                result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, diskNumber, id[sec].c & 0xff, id[sec].h & 0xFF, id[sec].r & 0xff, id[sec].n & 0xff, sectorSize);
                return -1;
            }
            oeLimit += sectorSize;
        }

        // 圧縮データを展開つづき
        rc = expandNext(iStream, oestream, oeLimit);
        trackHeaderBytes = oestream.toByteArray();

        DiskImageTrack track;
        int trackSize = 0;
        ieStream = new ByteArrayInputStream(trackHeaderBytes);
        ieStream.skipNBytes(trackHeader.getOffdLE() & 0xffff);

        // トラックの作成
        track = disk.newImageTrack(id[0].c & 0xff, id[0].h & 0xff, offsetPos, 1);
        disk.setMaxTrackNumber(id[0].c & 0xff);

        for (int pos = 0; pos < sectorsPerTrack && result.getValid() >= 0; pos++) {
            int sectorSize = (128 << (id[pos].n & 0xff));
            trackSize += parseSector(ieStream, diskNumber, id[pos].c & 0xff, id[pos].h & 0xff,
                    sectorsPerTrack, id[pos].r & 0xff, sectorSize, (attr[pos] & 0x40) == 0, track);
        }

        // 入力データの位置を補正
        adjustIStream(iStream);

        if (result.getValid() >= 0) {
            // インターリーブの計算
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // トラックサイズ設定
            track.setSize(trackSize);
            // サイド番号は各セクタのID Hに合わせる
            track.setSideNumber(track.getMajorIDH());

            // ディスクに追加
            disk.add(track);
            // オフセット設定
            disk.setOffset(offsetPos, offset);
        }

        return trackSize;
    }

    /**
     * ファイルを解析
     *
     * @param iStream    解析対象データ
     * @param diskNumber ディスク番号
     * @return -1: finish parsing, 0: parse next disk
     */
    private int parseDisk(InputStream iStream, int diskNumber) throws IOException {
        // skip header
        if (parseHeader(iStream, diskNumber) < 0) {
            return -1;
        }

        // ディスク作成
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // トラック解析
        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < 204; pos++) {
            int offset = parseTrack(iStream, diskNumber, d88OffsetPos, d88Offset, disk);
            if (offset == -1) {
                break;
            }
            d88Offset += offset;

            d88OffsetPos++;
            if (d88OffsetPos >= disk.getCreatableTracks()) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, diskNumber, d88Offset);
            }
        }
        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            // ディスクを追加
            DiskParam diskParam = disk.calcMajorNumber();
            if (diskParam != null) {
                disk.setDensity(diskParam.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return 0;
    }

    /**
     * ヘッダ解析
     *
     * @param iStream    解析対象データ
     * @param diskNumber ディスク番号
     * @return -1: エラー, 0:
     */
    private int parseHeader(InputStream iStream, int diskNumber) throws IOException {
        byte[] buf = new byte[16];
        int len = 1;

        // Skip until 0x1a
        do {
            if (iStream.read(buf, 0, buf.length) != buf.length) {
                // too short
                return -1;
            }
            len = buf.length;
            for (int i = 0; i < len; i++) {
                if (buf[i] == 0x1a) {
                    len = 0;
                    break;
                }
            }
        } while (len > 0);

        if (iStream.read(buf, 0, buf.length) != buf.length) {
            // too short
            return -1;
        }
        if (new String(buf, 0, 10).compareTo("DSKSTR ver") != 0) {
            // not a image
            return -1;
        }

        DiskSTRParser.StrHeader header = new DiskSTRParser.StrHeader();
        try {
            header.read(iStream);
            len = StrHeader.SIZE;
        } catch (IOException e) {
            len = 0;
        }

        if (len < StrHeader.SIZE) {
            // too short
            return -1;
        }

        return 0;
    }

    /**
     * 入力ストリームの位置を補正する
     *
     * @param iStream 元データ
     */
    private void adjustIStream(InputStream iStream) throws IOException {
        if ((compressType & 2) != 0) {
            // 2次圧縮の場合、入力データを読み過ぎている場合があるので位置を補正する
            int match = -1;
            long readPos = eStream.getReadPos();
            for (int i = 0; i < 8; i++) {
                if (eStream.getEStreamPos(i) < readPos && readPos <= eStream.getEStreamPos(i + 1)) {
                    match = i;
                    break;
                }
            }
            if (match >= 0) {
                long targetPos = eStream.getIStreamPos(match + 1);

                ((SeekableDataInputStream) iStream).position(targetPos);
            }
        }
    }

    /**
     * 圧縮データを判定して展開
     *
     * @param iStream 元データ
     * @param oStream 展開後データ
     * @param oLimit  出力バッファサイズ
     * @return -1: no data
     */
    private int expandFirst(InputStream iStream, OutputStream oStream, int oLimit) throws IOException {
        // データなし？
        if (iStream.available() == 0) {
            return -1;
        }

        // 最初のデータ
        compressType = 0;
        int pos = (int) ((SeekableDataInputStream) iStream).position();
        int ch = iStream.read();
        ((SeekableDataInputStream) iStream).position(pos);

        if (ch == 0x08 || ch == 0x0c) {
            compressType = 1;
        } else if (ch == 0xff) { // Using 0xff as a sentinel for 2nd compression in the original code, though -1 might be used for EOF
            compressType = 2;
        }

        eStream.clear();
        if ((compressType & 2) != 0) {
            // 2次圧縮データを展開
            expand2(iStream, oStream, oLimit, true);
        } else if ((compressType & 1) != 0) {
            // 1次圧縮データを展開
            expand1(iStream, oStream, oLimit);
        } else {
            // 非圧縮データ
            expand0(iStream, oStream, oLimit);
        }
        return 0;
    }

    /**
     * 圧縮データを展開つづき
     *
     * @param iStream 元データ
     * @param oStream 展開後データ
     * @param oLimit  出力バッファサイズ
     */
    private int expandNext(InputStream iStream, OutputStream oStream, int oLimit) throws IOException {
        if ((compressType & 2) != 0) {
            // 2次圧縮データを展開
            expand2(iStream, oStream, oLimit, false);
        } else if ((compressType & 1) != 0) {
            // 1次圧縮データとみなす
            expand1(iStream, oStream, oLimit);
        } else {
            // 非圧縮データ
            expand0(iStream, oStream, oLimit);
        }
        return 0;
    }

    /**
     * 2次圧縮データを展開
     *
     * @param iStream 元データ
     * @param oStream 展開後データ
     * @param oLimit  出力バッファサイズ
     * @param first   最初か
     */
    private void expand2(InputStream iStream, OutputStream oStream, int oLimit, boolean first) throws IOException {
        boolean cont;
        do {
            expand2Element(iStream);
            if (first) {
                // 1次圧縮しているか
                int ch = eStream.peekByte();
                if (ch == 0x08 || ch == 0x0c) {
                    compressType |= 1;
                }
                first = false;
            }
            if ((compressType & 1) != 0) {
                // 1次圧縮データを展開
                cont = expand1Element(oStream, oLimit);
            } else {
                // 非圧縮データ
                cont = expand0Element(oStream, oLimit);
            }
        } while (cont);
    }

    /**
     * 2次圧縮データを展開
     *
     * @param iStream 元データ
     * estreamを入力ストリームとする
     */
    private void expand2Element(InputStream iStream) throws IOException {
        byte[] iBuf = new byte[16];
        int iBufLen;
        byte[] buf = new byte[256];

        int ch = iStream.read();
        if (ch == -1) return; // EOF

        int ipos = (int) ((SeekableDataInputStream) iStream).position();
        eStream.setIStreamPos(0, ipos);
        eStream.setEStreamPos(0, eStream.getWritePos());

        int cmd = (ch & 0xff);

        iBufLen = 0;
        int tempCmd = cmd;
        for (int i = 0; i < 8; i++) {
            if ((tempCmd & 1) != 0) {
                iBufLen++;
                eStream.setLastPos(ipos + iBufLen);
            } else {
                iBufLen += 2;
            }
            eStream.setIStreamPos(i + 1, ipos + iBufLen);
            tempCmd >>= 1;
        }

        Arrays.fill(iBuf, (byte) 0);
        iStream.readNBytes(iBuf, 0, iBufLen);

        tempCmd = cmd;
        iBufLen = 0;
        for (int i = 0; i < 8; i++) {
            if ((tempCmd & 1) != 0) {
                // そのまま出力
                eStream.appendByte(iBuf[iBufLen++]);
                eStream.setEStreamPos(i + 1, eStream.getWritePos());
            } else {
                int d1 = iBuf[iBufLen++] & 0xFF;
                int d2 = iBuf[iBufLen++] & 0xFF;

                int len = (d2 & 0xf) + 3;
                int index = ((d2 & 0xf0) << 4) | d1;
                index += 18;

                // コピー元となるデータ位置を計算
                int eLen = eStream.getWritePos();
                if (eLen >= 0x2000) {
                    index += (eLen & ~0xfff) - 0x1000;
                } else if (eLen < index) {
                    index -= 0x1000;
                }

                // index: 展開後データの絶対位置となる
                if (index >= 0) {
                    // ポジション補正
                    if (!(eLen <= index + 0x1000 && index < eLen)) {
                        index += 0x1000;
                    }

                    // 元データを取得
                    byte[] data = eStream.getData();

                    int bLen = Math.min(len, eLen - index);
                    System.arraycopy(data, index, buf, 0, bLen);

                    int bpos = bLen;
                    while (bpos < len) {
                        // データを埋め合わせる
                        int copyLen = Math.min(len - bpos, bLen);
                        System.arraycopy(buf, 0, buf, bpos, copyLen);
                        bpos += copyLen;
                    }
                } else {
                    // 負になる場合は仮想的な位置で計算
                    int nLen = -index;
                    if (nLen > len) nLen = len;
                    if (nLen > 0) {
                        Arrays.fill(buf, 0, nLen, (byte) 0);
                    }
                    int plen = (len + index);
                    if (plen > 0) {
                        byte[] data = eStream.getData();
                        System.arraycopy(data, 0, buf, nLen, plen);
                    }
                }

                // 展開データに追記
                eStream.appendData(buf, len);
                eStream.setEStreamPos(i + 1, eStream.getWritePos());
            }
            tempCmd >>= 1;
        }
    }

    /**
     * 1次圧縮データを展開
     *
     * @param iStream 元データ
     * @param oStream 展開後データ
     * @param oLimit  出力バッファサイズ
     */
    private void expand1(InputStream iStream, OutputStream oStream, int oLimit) throws IOException {
        byte[] buf = new byte[16];

        boolean continuable = (eStream.remain() == 0);
        do {
            if (continuable) {
                int len = iStream.read(buf, 0, buf.length);
                if (len > 0) {
                    eStream.appendData(buf, len);
                }
            }
            continuable = expand1Element(oStream, oLimit);
        } while (continuable);

        if (eStream.remain() > 0) {
            int pos = (int) ((SeekableDataInputStream) iStream).position();
            ((SeekableDataInputStream) iStream).position(pos - eStream.remain());
            eStream.setWritePos(eStream.getReadPos());
        }
    }

    /**
     * 1次圧縮データを展開
     *
     * eStream を入力ストリームとする
     *
     * @param oStream 展開後データ
     * @param oLimit  出力バッファサイズ
     * @return 出力データサイズが oLimit に達したら false
     */
    private boolean expand1Element(OutputStream oStream, int oLimit) throws IOException {
        int size;
        int oSize = 0;
        if (oStream instanceof ByteArrayOutputStream) {
            oSize = ((ByteArrayOutputStream) oStream).size();
        }
        byte[] buf = new byte[128];

        do {
            // 先頭文字チェック
            int ch = eStream.peekByte();
            if (ch == -1) {
                break;
            }
            size = (ch & 0xff);
            if (ch < 0x80) {
                if (size == 0) size = 0x80;
            } else {
                size = 1;
            }
            if (eStream.remain() < (size + 1)) {
                break;
            }

            // 展開
            ch = eStream.getByte();
            size = (ch & 0xff);
            if (ch < 0x80) {
                if (size == 0) size = 0x80;
                size = eStream.getData(buf, size);
                oStream.write(buf, 0, size);
                oSize += size;
            } else {
                size = (ch & 0x7f);
                ch = eStream.getByte();
                if (size == 0) size = 0x80;
                Arrays.fill(buf, 0, size, (byte) ch);
                oStream.write(buf, 0, size);
                oSize += size;
            }
        } while (oSize < oLimit);

        return oSize < oLimit;
    }

    /**
     * 非圧縮データをそのまま展開
     *
     * @param iStream 元データ
     * @param oStream 展開後データ
     * @param oLimit  出力バッファサイズ
     */
    private static void expand0(InputStream iStream, OutputStream oStream, int oLimit) throws IOException {
        int size;
        int osize = 0;
        if (oStream instanceof ByteArrayOutputStream) {
            osize = ((ByteArrayOutputStream) oStream).size();
        }
        byte[] buf = new byte[128];

        while (osize < oLimit) {
            size = Math.min(buf.length, oLimit - osize);

            size = iStream.read(buf, 0, size);
            if (size <= 0) {
                break;
            }
            oStream.write(buf, 0, size);
            osize += size;
        }
    }

    /**
     * 非圧縮データをそのまま展開
     *
     * eStream を入力ストリームとする
     *
     * @param oStream 展開後データ
     * @param oLimit  出力バッファサイズ
     * @return 出力データサイズが oLimit に達したら false
     */
    private boolean expand0Element(OutputStream oStream, int oLimit) throws IOException {
        int siz;
        int osize = 0;
        if (oStream instanceof ByteArrayOutputStream) {
            osize = ((ByteArrayOutputStream) oStream).size();
        }
        byte[] buf = new byte[128];

        while (osize < oLimit) {
            siz = Math.min(buf.length, oLimit - osize);

            siz = eStream.getData(buf, siz);
            if (siz == 0) {
                break;
            }
            oStream.write(buf, 0, siz);
            osize += siz;
        }
        return (osize < oLimit);
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    /**
     * チェック
     *
     * @param iStream 解析対象データ
     * @return 1: 選択ダイアログ表示, 0: 正常（候補が複数ある時はダイアログ表示）
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        if (parseHeader(iStream, 0) < 0) {
            return -1;
        }

        return 0;
    }

    /**
     * ファイルを解析
     *
     * @param iStream   解析対象データ
     * @param diskParam パラメータ通常不要
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        for (int diskNumber = 0; diskNumber < 1; diskNumber++) {
            if (parseDisk(iStream, diskNumber) < 0) {
                break;
            }
        }
        return result.getValid();
    }
}
