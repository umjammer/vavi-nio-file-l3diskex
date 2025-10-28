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
 * X68000/PC9801用DSKSTR ディスクパーサ
 */
public class DiskSTRParser extends DiskImageParser {

    private static final Logger logger = getLogger(DiskSTRParser.class.getName());

    /**
     * DSKSTR 2次圧縮展開後バッファ
     */
    public static class Expand2FIFOBuffer extends Utils.FIFOBuffer {

        private long mLastPos;
        private long[] mIstrPos = new long[9];
        private long[] mEstrPos = new long[9];

        public Expand2FIFOBuffer() {
            super();
            clear();
        }

        @Override
        public void clear() {
            super.clear();
            mLastPos = 0;
            Arrays.fill(mIstrPos, 0);
            Arrays.fill(mEstrPos, 0);
        }

        public void setLastPos(long val) {
            mLastPos = val;
        }

        public long getLastPos() {
            return mLastPos;
        }

        public void setIStreamPos(int idx, long val) {
            mIstrPos[idx] = val;
        }

        public void setEStreamPos(int idx, long val) {
            mEstrPos[idx] = val;
        }

        public long getIStreamPos(int idx) {
            return mIstrPos[idx];
        }

        public long getEStreamPos(int idx) {
            return mEstrPos[idx];
        }
    }

    private int mCompressType;
    /** 入力データの圧縮形式 0:非圧縮 bit0:1次圧縮 bit1:2次圧縮 */
    private Expand2FIFOBuffer mEstream = new Expand2FIFOBuffer();

    /**
     * DSKSTRヘッダ
     */
    private static class str_header_t {

        int datasize; // BE
        byte[] reserved = new byte[28];

        public int getSize() {
            return 4 + 28;
        }

        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[getSize()];
            if (is.read(buf) != getSize()) {
                throw new IOException("Failed to read DSKSTR header");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
            datasize = bb.getInt();
            bb.get(reserved);
        }
    }

    /**
     * DSKSTRトラックヘッダ
     */
    private static class str_track_header_t {

        byte attr;
        byte secs;
        short off1; // BE
        short off2;
        short offd;
        short dat1;
        short dat2;
        byte[] reserved = new byte[4];

        public int getSize() {
            return 1 + 1 + 2 + 2 + 2 + 2 + 2 + 4;
        }

        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[getSize()];
            if (is.read(buf) != getSize()) {
                throw new IOException("Failed to read DSKSTR track header");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
            attr = bb.get();
            secs = bb.get();
            off1 = bb.getShort();
            off2 = bb.getShort();
            offd = bb.getShort();
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
            return Short.reverseBytes(offd);
        }
    }

    /**
     * セクタID
     */
    private static class str_sector_id_t {

        byte c;
        byte h;
        byte r;
        byte n;

        public int getSize() {
            return 4;
        }

        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[getSize()];
            if (is.read(buf) != getSize()) {
                throw new IOException("Failed to read DSKSTR sector ID");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf);
            c = bb.get();
            h = bb.get();
            r = bb.get();
            n = bb.get();
        }
    }

    public DiskSTRParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
        mCompressType = 0;
    }

    // デストラクタはJavaにはないので不要

    /**
     * セクタデータの作成
     *
     * @param istream       ディスクイメージ
     * @param diskNumber    ディスク番号
     * @param trackNumber   トラック番号
     * @param sideNumber    サイド番号
     * @param sectorNums    セクタ数
     * @param sectorNumber  セクタ番号
     * @param sectorSize    セクタサイズ
     * @param singleDensity 単密度か
     * @param track         トラック
     * @return ヘッダ込みのセクタサイズ
     */
    private int parseSector(InputStream istream, int diskNumber, int trackNumber, int sideNumber, int sectorNums, int sectorNumber, int sectorSize, boolean singleDensity, DiskImageTrack track) throws IOException {
        // セクタ作成
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, sectorNums, false, 0);
        track.add(sector);

        byte[] buffer = sector.getSectorBuffer();

        // plain data
        int readLen = istream.read(buffer, 0, sectorSize);
        if (readLen != sectorSize) {
            // throw new IOException("Failed to read sector data"); // EOFでなければエラーだが、ここでは省略
        }

        sector.setSingleDensity(singleDensity);
        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }

    /**
     * トラックデータの作成
     *
     * @param istream    ディスクイメージ
     * @param diskNumber ディスク番号
     * @param offsetPos  オフセット番号
     * @param offset     オフセット位置
     * @param disk       ディスク
     * @return -1:エラー or 終り >0:トラックサイズ
     */
    private int parseTrack(InputStream istream, int diskNumber, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        str_track_header_t hTrack = new str_track_header_t();

        // 圧縮データを展開
        ByteArrayOutputStream oestream = new ByteArrayOutputStream();
        int oelimit = hTrack.getSize();
        int rc = expandFirst(istream, oestream, oelimit);

        // ヘッダをチェック
        byte[] trackHeaderBytes = oestream.toByteArray();
        ByteArrayInputStream iestreamHeader = new ByteArrayInputStream(trackHeaderBytes);
        int len = 0;
        try {
            hTrack.read(iestreamHeader);
            len = trackHeaderBytes.length;
        } catch (IOException e) {
            // Error during reading header
            len = 0;
        }

        if (rc < 0 || len < hTrack.getSize() || hTrack.attr == 0) {
            // end of file
            return -1;
        }

        int sectorsPerTrack = hTrack.secs & 0xFF;
        if (sectorsPerTrack <= 0) {
            result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
            return -1;
        }

        // データ開始位置
        oelimit = hTrack.getOffdLE() & 0xFFFF;
        // 圧縮データを展開つづき
        rc = expandNext(istream, oestream, oelimit);

        byte[] attr = new byte[256];
        Arrays.fill(attr, (byte) 0);

        str_sector_id_t[] id = new str_sector_id_t[256];
        for (int i = 0; i < id.length; i++) id[i] = new str_sector_id_t();

        trackHeaderBytes = oestream.toByteArray();
        ByteArrayInputStream iestream = new ByteArrayInputStream(trackHeaderBytes);

        // セクタ属性を得る
        iestream.skip(hTrack.getOff1LE() & 0xFFFF);

        for (int sec = 0; sec < sectorsPerTrack; sec += 4) {
            // 4バイト境界
            int readLen = iestream.read(attr, sec, 4);
            if (readLen < 4) {
                result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
                return -1;
            }
        }

        // セクタIDを得る
        iestream.skip(hTrack.getOff2LE() & 0xFFFF);

        for (int sec = 0; sec < sectorsPerTrack; sec++) {
            // C H R N
            try {
                id[sec].read(iestream);
                len = id[sec].getSize();
            } catch (IOException e) {
                len = 0;
            }

            if (len < id[sec].getSize()) {
                result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
                return -1;
            }

            int sectorSize = (128 << (id[sec].n & 0xFF));
            if ((id[sec].n & 0xFF) > 5) {
                result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, diskNumber, id[sec].c & 0xFF, id[sec].h & 0xFF, id[sec].r & 0xFF, id[sec].n & 0xFF, sectorSize);
                return -1;
            }
            oelimit += sectorSize;
        }

        // 圧縮データを展開つづき
        rc = expandNext(istream, oestream, oelimit);
        trackHeaderBytes = oestream.toByteArray();

        DiskImageTrack track = null;
        int d88TrackSize = 0;
        iestream = new ByteArrayInputStream(trackHeaderBytes);
        iestream.skip(hTrack.getOffdLE() & 0xFFFF);

        // トラックの作成
        track = disk.newImageTrack(id[0].c & 0xFF, id[0].h & 0xFF, offsetPos, 1);
        disk.setMaxTrackNumber(id[0].c & 0xFF);

        for (int pos = 0; pos < sectorsPerTrack && result.getValid() >= 0; pos++) {
            int sectorSize = (128 << (id[pos].n & 0xFF));
            d88TrackSize += parseSector(iestream, diskNumber, id[pos].c & 0xFF, id[pos].h & 0xFF, sectorsPerTrack, id[pos].r & 0xFF, sectorSize, (attr[pos] & 0x40) == 0, track);
        }

        // 入力データの位置を補正
        adjustIStream(istream);

        if (result.getValid() >= 0) {
            // インターリーブの計算
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // トラックサイズ設定
            track.setSize(d88TrackSize);
            // サイド番号は各セクタのID Hに合わせる
            track.setSideNumber(track.getMajorIDH());

            // ディスクに追加
            disk.add(track);
            // オフセット設定
            disk.setOffset(offsetPos, offset);
        } else {
            // track is auto-deleted in java garbage collection if not referenced
        }

        return d88TrackSize;
    }

    /**
     * ファイルを解析
     *
     * @param istream    解析対象データ
     * @param diskNumber ディスク番号
     * @return -1: finish parsing
     * @return 0: parse next disk
     */
    private int parseDisk(InputStream istream, int diskNumber) throws IOException {
        // skip header
        if (parseHeader(istream, diskNumber) < 0) {
            return -1;
        }

        // ディスク作成
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // トラック解析
        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < 204; pos++) {
            int offset = parseTrack(istream, diskNumber, d88OffsetPos, d88Offset, disk);
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
     * @param istream    解析対象データ
     * @param diskNumber ディスク番号
     * @return -1: エラー
     * @return 0:
     */
    private int parseHeader(InputStream istream, int diskNumber) throws IOException {
        byte[] buf = new byte[16];
        int len = 1;

        // Skip until 0x1a
        do {
            if (istream.read(buf, 0, buf.length) != buf.length) {
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

        if (istream.read(buf, 0, buf.length) != buf.length) {
            // too short
            return -1;
        }
        if (new String(buf, 0, 10).compareTo("DSKSTR ver") != 0) {
            // not a image
            return -1;
        }

        str_header_t header = new str_header_t();
        try {
            header.read(istream);
            len = header.getSize();
        } catch (IOException e) {
            len = 0;
        }

        if (len < header.getSize()) {
            // too short
            return -1;
        }

        return 0;
    }

    /**
     * 入力ストリームの位置を補正する
     *
     * @param istream 元データ
     */
    private void adjustIStream(InputStream istream) throws IOException {
        if ((mCompressType & 2) != 0) {
            // 2次圧縮の場合、入力データを読み過ぎている場合があるので位置を補正する
            int match = -1;
            long readPos = mEstream.getReadPos();
            for (int i = 0; i < 8; i++) {
                if (mEstream.getEStreamPos(i) < readPos && readPos <= mEstream.getEStreamPos(i + 1)) {
                    match = i;
                    break;
                }
            }
            if (match >= 0) {
                long targetPos = mEstream.getIStreamPos(match + 1);

                if (istream instanceof SeekableDataInputStream) {
                    ((SeekableDataInputStream) istream).position(targetPos);
                }
            }
        }
    }

    /**
     * 圧縮データを判定して展開
     *
     * @param istream 元データ
     * @param ostream 展開後データ
     * @param olimit  出力バッファサイズ
     * @return -1:no data
     */
    private int expandFirst(InputStream istream, OutputStream ostream, int olimit) throws IOException {
        // データなし？
        if (istream.available() == 0) {
            return -1;
        }

        // 最初のデータ
        mCompressType = 0;
        int pos = (int) ((SeekableDataInputStream) istream).position();
        int ch = istream.read();
        ((SeekableDataInputStream) istream).position(pos);

        if (ch == 0x08 || ch == 0x0c) {
            mCompressType = 1;
        } else if (ch == 0xff) { // Using 0xff as a sentinel for 2nd compression in the original code, though -1 might be used for EOF
            mCompressType = 2;
        }

        mEstream.clear();
        if ((mCompressType & 2) != 0) {
            // 2次圧縮データを展開
            expand2(istream, ostream, olimit, true);
        } else if ((mCompressType & 1) != 0) {
            // 1次圧縮データを展開
            expand1(istream, ostream, olimit);
        } else {
            // 非圧縮データ
            expand0(istream, ostream, olimit);
        }
        return 0;
    }

    /**
     * 圧縮データを展開つづき
     *
     * @param istream 元データ
     * @param ostream 展開後データ
     * @param olimit  出力バッファサイズ
     */
    private int expandNext(InputStream istream, OutputStream ostream, int olimit) throws IOException {
        if ((mCompressType & 2) != 0) {
            // 2次圧縮データを展開
            expand2(istream, ostream, olimit, false);
        } else if ((mCompressType & 1) != 0) {
            // 1次圧縮データとみなす
            expand1(istream, ostream, olimit);
        } else {
            // 非圧縮データ
            expand0(istream, ostream, olimit);
        }
        return 0;
    }

    /**
     * 2次圧縮データを展開
     *
     * @param istream 元データ
     * @param ostream 展開後データ
     * @param olimit  出力バッファサイズ
     * @param first   最初か
     */
    private void expand2(InputStream istream, OutputStream ostream, int olimit, boolean first) throws IOException {
        boolean cont;
        do {
            expand2Element(istream);
            if (first) {
                // 1次圧縮しているか
                int ch = mEstream.peekByte();
                if (ch == 0x08 || ch == 0x0c) {
                    mCompressType |= 1;
                }
                first = false;
            }
            if ((mCompressType & 1) != 0) {
                // 1次圧縮データを展開
                cont = expand1Element(ostream, olimit);
            } else {
                // 非圧縮データ
                cont = expand0Element(ostream, olimit);
            }
        } while (cont);
    }

    /**
     * 2次圧縮データを展開
     *
     * @param istream 元データ
     * @note estreamを入力ストリームとする
     */
    private void expand2Element(InputStream istream) throws IOException {
        byte[] ibuf = new byte[16];
        int ibufLen;
        byte[] buf = new byte[256];

        int ch = istream.read();
        if (ch == -1) return; // EOF

        int ipos = (int) ((SeekableDataInputStream) istream).position();
        mEstream.setIStreamPos(0, ipos);
        mEstream.setEStreamPos(0, mEstream.getWritePos());

        int cmd = (ch & 0xff);

        ibufLen = 0;
        int tempCmd = cmd;
        for (int i = 0; i < 8; i++) {
            if ((tempCmd & 1) != 0) {
                ibufLen++;
                mEstream.setLastPos(ipos + ibufLen);
            } else {
                ibufLen += 2;
            }
            mEstream.setIStreamPos(i + 1, ipos + ibufLen);
            tempCmd >>= 1;
        }

        Arrays.fill(ibuf, (byte) 0);
        istream.readNBytes(ibuf, 0, ibufLen);

        tempCmd = cmd;
        ibufLen = 0;
        for (int i = 0; i < 8; i++) {
            if ((tempCmd & 1) != 0) {
                // そのまま出力
                mEstream.appendByte(ibuf[ibufLen++]);
                mEstream.setEStreamPos(i + 1, mEstream.getWritePos());
            } else {
                int d1 = ibuf[ibufLen++] & 0xFF;
                int d2 = ibuf[ibufLen++] & 0xFF;

                int len = (d2 & 0xf) + 3;
                int idx = ((d2 & 0xf0) << 4) | d1;
                idx += 18;

                // コピー元となるデータ位置を計算
                int elen = mEstream.getWritePos();
                if (elen >= 0x2000) {
                    idx += (elen & ~0xfff) - 0x1000;
                } else if (elen < idx) {
                    idx -= 0x1000;
                }

                // idx: 展開後データの絶対位置となる
                if (idx >= 0) {
                    // ポジション補正
                    if (!(elen <= idx + 0x1000 && idx < elen)) {
                        idx += 0x1000;
                    }

                    // 元データを取得
                    byte[] data = mEstream.getData();

                    int blen = Math.min(len, elen - idx);
                    System.arraycopy(data, idx, buf, 0, blen);

                    int bpos = blen;
                    while (bpos < len) {
                        // データを埋め合わせる
                        int copyLen = Math.min(len - bpos, blen);
                        System.arraycopy(buf, 0, buf, bpos, copyLen);
                        bpos += copyLen;
                    }
                } else {
                    // 負になる場合は仮想的な位置で計算
                    int nlen = -idx;
                    if (nlen > len) nlen = len;
                    if (nlen > 0) {
                        Arrays.fill(buf, 0, nlen, (byte) 0);
                    }
                    int plen = (len + idx);
                    if (plen > 0) {
                        byte[] data = mEstream.getData();
                        System.arraycopy(data, 0, buf, nlen, plen);
                    }
                }

                // 展開データに追記
                mEstream.appendData(buf, len);
                mEstream.setEStreamPos(i + 1, mEstream.getWritePos());
            }
            tempCmd >>= 1;
        }
    }

    /**
     * 1次圧縮データを展開
     *
     * @param istream 元データ
     * @param ostream 展開後データ
     * @param olimit  出力バッファサイズ
     */
    private void expand1(InputStream istream, OutputStream ostream, int olimit) throws IOException {
        byte[] buf = new byte[16];

        boolean cont = (mEstream.remain() == 0);
        do {
            if (cont) {
                int len = istream.read(buf, 0, buf.length);
                if (len > 0) {
                    mEstream.appendData(buf, len);
                }
            }
            cont = expand1Element(ostream, olimit);
        } while (cont);

        if (mEstream.remain() > 0) {
            int pos = (int) ((SeekableDataInputStream) istream).position();
            ((SeekableDataInputStream) istream).position(pos - mEstream.remain());
            mEstream.setWritePos(mEstream.getReadPos());
        }
    }

    /**
     * 1次圧縮データを展開
     *
     * @param ostream 展開後データ
     * @param olimit  出力バッファサイズ
     * @return 出力データサイズがolimitに達したらfalse
     * @note estreamを入力ストリームとする
     */
    private boolean expand1Element(OutputStream ostream, int olimit) throws IOException {
        int siz;
        int osize = 0;
        if (ostream instanceof ByteArrayOutputStream) {
            osize = ((ByteArrayOutputStream) ostream).size();
        }
        byte[] buf = new byte[128];

        do {
            // 先頭文字チェック
            int ch = mEstream.peekByte();
            if (ch == -1) {
                break;
            }
            siz = (ch & 0xff);
            if (ch < 0x80) {
                if (siz == 0) siz = 0x80;
            } else {
                siz = 1;
            }
            if (mEstream.remain() < (siz + 1)) {
                break;
            }

            // 展開
            ch = mEstream.getByte();
            siz = (ch & 0xff);
            if (ch < 0x80) {
                if (siz == 0) siz = 0x80;
                siz = mEstream.getData(buf, siz);
                ostream.write(buf, 0, siz);
                osize += siz;
            } else {
                siz = (ch & 0x7f);
                ch = mEstream.getByte();
                if (siz == 0) siz = 0x80;
                Arrays.fill(buf, 0, siz, (byte) ch);
                ostream.write(buf, 0, siz);
                osize += siz;
            }
        } while (osize < olimit);

        return (osize < olimit);
    }

    /**
     * 非圧縮データをそのまま展開
     *
     * @param istream 元データ
     * @param ostream 展開後データ
     * @param olimit  出力バッファサイズ
     */
    private void expand0(InputStream istream, OutputStream ostream, int olimit) throws IOException {
        int siz;
        int osize = 0;
        if (ostream instanceof ByteArrayOutputStream) {
            osize = ((ByteArrayOutputStream) ostream).size();
        }
        byte[] buf = new byte[128];

        while (osize < olimit) {
            siz = Math.min(buf.length, olimit - osize);

            siz = istream.read(buf, 0, siz);
            if (siz <= 0) {
                break;
            }
            ostream.write(buf, 0, siz);
            osize += siz;
        }
    }

    /**
     * 非圧縮データをそのまま展開
     *
     * @param ostream 展開後データ
     * @param olimit  出力バッファサイズ
     * @return 出力データサイズがolimitに達したらfalse
     * @note estreamを入力ストリームとする
     */
    private boolean expand0Element(OutputStream ostream, int olimit) throws IOException {
        int siz;
        int osize = 0;
        if (ostream instanceof ByteArrayOutputStream) {
            osize = ((ByteArrayOutputStream) ostream).size();
        }
        byte[] buf = new byte[128];

        while (osize < olimit) {
            siz = Math.min(buf.length, olimit - osize);

            siz = mEstream.getData(buf, siz);
            if (siz == 0) {
                break;
            }
            ostream.write(buf, 0, siz);
            osize += siz;
        }
        return (osize < olimit);
    }

    @Override
    public int check(InputStream istream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) {
        return -1;
    }

    /**
     * チェック
     *
     * @param istream 解析対象データ
     * @return 1 選択ダイアログ表示
     * @return 0 正常（候補が複数ある時はダイアログ表示）
     */
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        if (parseHeader(istream, 0) < 0) {
            return -1;
        }

        return 0;
    }

    /**
     * ファイルを解析
     *
     * @param istream   解析対象データ
     * @param diskParam パラメータ通常不要
     * @return 0 正常
     * @return -1 エラーあり
     * @return 1 警告あり
     */
    @Override
    public int parse(InputStream istream, DiskParam diskParam) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        for (int diskNumber = 0; diskNumber < 1; diskNumber++) {
            if (parseDisk(istream, diskNumber) < 0) {
                break;
            }
        }
        return result.getValid();
    }
}
