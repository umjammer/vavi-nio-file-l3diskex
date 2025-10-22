package l3diskex.basicfmt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


public class BasicFat {

    // Public constant defined in basiccommon.h (assumed)
    public static final int INVALID_GROUP_NUMBER = -1; // Assuming -1 or similar for invalid

    /**
     * 使用状況テーブル enum
     */
    public enum FatAvailability {
        FAT_AVAIL_FREE(0),
        FAT_AVAIL_SYSTEM(1),
        FAT_AVAIL_USED(2),
        FAT_AVAIL_USED_FIRST(3),
        FAT_AVAIL_USED_LAST(4),
        FAT_AVAIL_MISSING(5),
        FAT_AVAIL_LEAK(6),
        FAT_AVAIL_NULLEND(7);

        private final int value;

        FatAvailability(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * 使用状況テーブル
     */
    public static class DiskBasicAvailability extends ArrayList<Integer> {

        private int m_free_size;
        /// < 空きサイズ
        private int m_free_grps;

        /// < 空きグループ数

        public DiskBasicAvailability() {
            super();
            m_free_size = -1;
            m_free_grps = -1;
        }

        /**
         * 初期化 空きサイズを 0 にする
         */
        public void Clear() {
            super.clear();
            m_free_size = 0;
            m_free_grps = 0;
        }

        /**
         * 初期化 空きサイズを 0 にする
         */
        public void empty() {
            super.clear();
            m_free_size = 0;
            m_free_grps = 0;
        }

        /**
         * 初期化 空きサイズを -1 にする
         */
        public void emptyInit() {
            super.clear();
            m_free_size = -1;
            m_free_grps = -1;
        }

        /**
         * @param val   値
         * @param size  空きサイズ
         * @param group 空きグループ数
         * 追加
         */
        public void Add(int val, int size, int group) {
            super.add(val);
            m_free_size += size;
            m_free_grps += group;
        }

        /**
         * @param idx 位置
         * @param val 値
         * セット (Safety)
         */
        public void Set(int idx, int val) {
            if (idx < size()) {
                set(idx, val);
            }
        }

        /**
         * @param idx 位置
         * @return 値
         * ゲット (Safety)
         */
        public final int Get(int idx) {
            int val = 0;
            if (idx < size()) {
                val = get(idx);
            }
            return val;
        }

        // Size is int in Java, but for internal use, we map Count() to size()
        public int count() {
            return size();
        }

        /**
         * 空きサイズを返す
         */
        public int getFreeSize() {
            return m_free_size;
        }

        /**
         * 空きグループ数を返す
         */
        public int getFreeGroups() {
            return m_free_grps;
        }

        /**
         * 空きサイズをセット
         */
        public void setFreeSize(int val) {
            m_free_size = val;
        }

        /**
         * 空きグループ数をセット
         */
        public void SetFreeGroups(int val) {
            m_free_grps = val;
        }
    }

    /**
     * ビット ON/OFF バッファ １つ
     * @sa DiskBasicBitMLMap
     */
    static class BitMLBuffer {

        protected byte[] m_buffer;
        protected int m_size;

        public BitMLBuffer() {
            m_buffer = null;
            m_size = 0;
        }

        public BitMLBuffer(byte[] buffer, int size) {
            m_buffer = buffer;
            m_size = size;
        }

        /**
         * 指定位置のビットを変更する
         * @param num ビット位置
         * @param val true:セット / false:リセット
         */
        public void modify(int num, boolean val) {
            int pos = num >> 3;
            int bit = num & 7;
            if (m_buffer != null && pos < m_size) {
                if (val) {
                    m_buffer[pos] |= (byte) (0x80 >> bit);
                } else {
                    m_buffer[pos] &= (byte) ~(0x80 >> bit);
                }
            }
        }

        /**
         * 指定位置のビットがセットされているか
         * @param num ビット位置
         * @return true:セット / false:リセット
         */
        public boolean isSet(int num) {
            int pos = num >> 3;
            int bit = num & 7;
            if (m_buffer != null && pos < m_size) {
                return ((m_buffer[pos] & (0x80 >> bit)) != 0);
            }
            return false;
        }

        /**
         * 指定位置のビット位置を計算
         * @param num ビット位置
         * @param pos バッファ位置
         * @param bit ビット
         */
        public void getPos(int num, int[] pos, int[] bit) {
            pos[0] = num >> 3;
            bit[0] = num & 7;
        }

        /**
         * バッファを返す
         */
        public byte[] getBuffer() {
            return m_buffer;
        }

        /**
         * サイズ(ビット数)を返す
         */
        public int getBitSize() {
            return m_size << 3;
        }

        /**
         * バッファサイズを返す
         */
        public int getSize() {
            return m_size;
        }
    }

    /**
     * ビット ON/OFF マップ BitMLBuffer の配列
     */
    public static class DiskBasicBitMLMap extends ArrayList<BitMLBuffer> {

        public DiskBasicBitMLMap() {
            super();
        }

        /**
         * ポインタをセット
         */
        public void addBuffer(byte[] buffer, int size) {
            add(new BitMLBuffer(buffer, size));
        }

        /**
         * @param group_num 位置
         * @param val       true:セット / false:リセット
         * 指定位置のビットを変更する
         */
        public void modify(int group_num, boolean val) {
            for (int idx = 0; idx < size(); idx++) {
                BitMLBuffer item = get(idx);
                int size = item.getBitSize();
                if (group_num < size) {
                    item.modify(group_num, val);
                    break;
                }
                group_num -= size;
            }
        }

        /**
         * 指定位置が空いているか
         * @param group_num 位置
         * @return true:セット / false:リセット
         */
        public boolean isSet(int group_num) {
            boolean val = false;
            for (int idx = 0; idx < size(); idx++) {
                BitMLBuffer item = get(idx);
                int size = item.getBitSize();
                if (group_num < size) {
                    val = item.isSet(group_num);
                    break;
                }
                group_num -= size;
            }
            return val;
        }

        /**
         * 指定位置からバッファ内の位置を計算
         * @param group_num 位置
         * @param idx       MAPの位置
         * @param pos       MAP内のバッファ位置(byte)
         * @param bit       ビット位置
         * @return true / false オーバフロー
         */
        public boolean getPosInMap(int group_num, int[] idx, int[] pos, int[] bit) {
            boolean valid = false;
            for (idx[0] = 0; idx[0] < size(); idx[0]++) {
                BitMLBuffer item = get(idx[0]);
                int size = item.getBitSize();
                if (group_num < size) {
                    item.getPos(group_num, pos, bit);
                    valid = true;
                    break;
                }
                group_num -= size;
            }
            return valid;
        }
    }

    /**
     * FATエリア（セクタ）へのポインタを保持
     */
    public static class DiskBasicFatBuffer {

        /** バッファサイズ */
        private final int size;
        /** バッファポインタ（セクタ内の開始ポインタ）*/
        private final byte[] buffer;

        public DiskBasicFatBuffer() {
            size = 0;
            buffer = null;
        }

        public DiskBasicFatBuffer(byte[] newbuf, int newsize) {
            size = newsize;
            // In C++, the buffer points to a location *within* a larger block.
            // In Java, we'll use a reference to the byte array and manage the offset
            // in a separate class (DiskBasicFatBuffers) or assume this buffer is the
            // relevant *slice* of the data. For now, we'll store the reference.
            // The original C++ code uses buffer pointers with offsets.
            // Since Java doesn't have direct pointer arithmetic, we'll assume 'buffer'
            // is the start of the relevant data block of 'size' length.
            this.buffer = newbuf;
        }

        // In Java, we can't directly store the pointer *into* a larger array slice
        // without making a copy or managing the offset explicitly. The C++ code's
        // usage suggests 'buffer' is a pointer to the start of the usable FAT data.
        // Given the constraints, we'll keep the design as close as possible,
        // assuming 'buffer' holds the data *slice*.

        /**
         * バッファポインタを返す
         */
        public byte[] getBuffer() {
            return buffer;
        }

        /**
         * バッファサイズを返す
         */
        public int getSize() {
            return size;
        }

        /**
         * バッファを指定コードで埋める
         * @param code コード
         */
        public void fill(byte code) {
            if (buffer != null) {
                Arrays.fill(buffer, 0, size, code);
            }
        }

        /**
         * バッファにコピー
         * @param buf バッファ
         * @param len サイズ
         */
        public void copy(byte[] buf, int len) {
            if (buffer != null) {
                int copyLen = Math.min(len, size);
                System.arraycopy(buf, 0, buffer, 0, copyLen);
            }
        }

        /**
         * 指定位置のデータを返す(8ビット)
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int get(int pos) {
            // Use 0xFF to treat byte as unsigned when converting to int
            return (buffer != null && pos < size) ? (buffer[pos] & 0xFF) : BasicFat.INVALID_GROUP_NUMBER;
        }

        /**
         * 指定位置にデータをセット(8ビット)
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void set(int pos, int val) {
            if (buffer != null && pos < size) {
                buffer[pos] = (byte) (val & 0xFF);
            }
        }

        /**
         * 指定位置のビットをセット/リセット
         * @param pos    位置(8ビット1単位)
         * @param mask   対象のビット
         * @param val    セット/リセット
         * @param invert 反転するか
         * @return 処理したか
         */
        public boolean bit(int pos, int mask, boolean val, boolean invert) {
            if (pos >= size) return false;

            int bit = get(pos); // Get returns 0-255
            if (invert) bit ^= 0xFF;
            bit = (val ? (bit | (mask & 0xFF)) : (bit & ~(mask & 0xFF)));
            if (invert) bit ^= 0xFF;
            set(pos, bit);
            return true;
        }

        /**
         * 指定位置のビットがONか
         * @param pos    位置(8ビット1単位)
         * @param mask   対象のビット
         * @param invert 反転するか
         * @return ビットがON
         */
        public boolean BitTest(int pos, byte mask, boolean invert) {
            if (pos >= size) return false;

            int bit = get(pos); // Get returns 0-255
            if (invert) bit ^= 0xFF;
            return (bit & (mask & 0xFF)) != 0;
        }

        /**
         * 指定位置のデータを返す(16ビット 、 リトルエンディアン)
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int get16LE(int pos) {
            if (buffer == null || pos + 1 >= size) return BasicFat.INVALID_GROUP_NUMBER;
            int b0 = buffer[pos] & 0xFF;
            int b1 = buffer[pos + 1] & 0xFF;
            return (b1 << 8) | b0;
        }

        /**
         * 指定位置にデータをセット(16ビット 、 リトルエンディアン)
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void set16LE(int pos, int val) {
            if (buffer != null && pos + 1 < size) {
                buffer[pos] = (byte) (val & 0xFF);
                buffer[pos + 1] = (byte) ((val >> 8) & 0xFF);
            }
        }

        /**
         * 指定位置のデータを返す(16ビット 、 ビッグエンディアン)
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int get16BE(int pos) {
            if (buffer == null || pos + 1 >= size) return BasicFat.INVALID_GROUP_NUMBER;
            int b0 = buffer[pos] & 0xFF;
            int b1 = buffer[pos + 1] & 0xFF;
            return (b0 << 8) | b1;
        }

        /**
         * 指定位置にデータをセット(16ビット 、 ビッグエンディアン)
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void Set16BE(int pos, int val) {
            if (buffer != null && pos + 1 < size) {
                buffer[pos] = (byte) ((val >> 8) & 0xFF);
                buffer[pos + 1] = (byte) (val & 0xFF);
            }
        }
    }

    /**
     * FAT１つ分のバッファ（複数セクタあり） DiskBasicFatBuffer の配列
     */
    public static class DiskBasicFatBuffers extends ArrayList<DiskBasicFatBuffer> {

        /**
         * 8ビットデータを返す
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int getData8(int pos) {
            int val = BasicFat.INVALID_GROUP_NUMBER;
            for (int i = 0; i < size(); i++) {
                DiskBasicFatBuffer buf = get(i);
                if (pos < buf.getSize()) {
                    val = buf.get(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * 8ビットデータをセット
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void setData8(int pos, int val) {
            for (int i = 0; i < size(); i++) {
                DiskBasicFatBuffer buf = get(i);
                if (pos < buf.getSize()) {
                    buf.set(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }

        /**
         * 8ビットデータが一致するか
         * @param pos 位置(8ビット1単位)
         * @param val 値
         * @return 一致する
         */
        public boolean matchData8(int pos, int val) {
            boolean match = false;
            for (int i = 0; i < size(); i++) {
                DiskBasicFatBuffer buf = get(i);
                if (pos < buf.getSize()) {
                    match = (buf.get(pos) == val);
                    break;
                }
                pos -= buf.getSize();
            }
            return match;
        }

        /**
         * 8ビットデータのビットをセット/リセット
         * @param pos    位置(8ビット1単位)
         * @param mask   対象のビット
         * @param val    セット/リセット
         * @param invert 反転するか
         * @return 処理したか
         */
        public boolean bitData8(int pos, byte mask, boolean val, boolean invert) {
            boolean processed = false;
            for (int i = 0; i < size(); i++) {
                DiskBasicFatBuffer buf = get(i);
                processed = buf.bit(pos, mask, val, invert);
                if (processed) break;
                pos -= buf.getSize();
            }
            return processed;
        }

        /**
         * 12ビットデータ(リトルエンディアン)を返す
         * @param pos 位置(12ビット1単位)
         * @return 値
         */
        public int getData12LE(int pos) {
            int val = BasicFat.INVALID_GROUP_NUMBER;
            boolean odd = ((pos & 1) != 0);
            pos = pos * 3 / 2;
            int cnt = 0;
            for (int i = 0; i < size() && cnt < 2; i++) {
                DiskBasicFatBuffer buf = get(i);
                while (pos < buf.getSize() && cnt < 2) {
                    int tmp = buf.get(pos);
                    if (cnt == 0) {
                        val = odd ? tmp >> 4 : tmp;
                    } else {
                        val |= odd ? tmp << 4 : (tmp & 0x0f) << 8;
                    }
                    pos++;
                    cnt++;
                }
                // pos calculation needs to be correct for next iteration
                // The original C++ code logic seems to assume that the `pos` after the inner
                // while loop will be adjusted by the entire size of the buffer when doing `pos -= (int)buf->GetSize();`.
                // In Java, we track how many bytes were processed in this buffer.
                if (cnt < 2) { // Only adjust if we didn't complete the 12-bit read in this buffer
                    pos -= buf.getSize();
                } else {
                    // If we completed the read, pos should be correct for the next item's offset in the original stream
                    // but since the original pos was a running byte count, the `pos` value here is local to the last buf.
                    // We should break if we got both bytes.
                    break;
                }
            }
            if (cnt != 2) val = BasicFat.INVALID_GROUP_NUMBER;
            return val & 0xFFF; // Return 12 bits only
        }

        /**
         * 12ビットデータ(リトルエンディアン)をセット
         * @param pos 位置(12ビット1単位)
         * @param val 値
         */
        public void setData12LE(int pos, int val) {
            boolean odd = ((pos & 1) != 0);
            pos = pos * 3 / 2;
            int cnt = 0;
            for (int i = 0; i < size() && cnt < 2; i++) {
                DiskBasicFatBuffer buf = get(i);
                while (pos < buf.getSize() && cnt < 2) {
                    int tmp = buf.get(pos);
                    if (cnt == 0) {
                        tmp = odd ? ((val & 0x0f) << 4) | (tmp & 0x0f) : (val & 0xff);
                    } else {
                        tmp = odd ? (val >> 4) & 0xff : ((val >> 8) & 0x0f) | (tmp & 0xf0);
                    }
                    buf.set(pos, tmp);
                    pos++;
                    cnt++;
                }
                if (cnt < 2) {
                    pos -= buf.getSize();
                } else {
                    break;
                }
            }
        }

        /**
         * 16ビットデータ(リトルエンディアン)を返す
         * @param pos 位置(16ビット1単位)
         * @return 値
         */
        public int getData16LE(int pos) {
            int val = BasicFat.INVALID_GROUP_NUMBER;
            pos *= 2;
            for (int i = 0; i < size(); i++) {
                DiskBasicFatBuffer buf = get(i);
                if (pos < buf.getSize()) {
                    val = buf.get16LE(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * 16ビットデータ(リトルエンディアン)をセット
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16LE(int pos, int val) {
            pos *= 2;
            for (int i = 0; i < size(); i++) {
                DiskBasicFatBuffer buf = get(i);
                if (pos < buf.getSize()) {
                    buf.set16LE(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }

        /**
         * 16ビットデータ(ビッグエンディアン)を返す
         * @param pos 位置(16ビット1単位)
         * @return 値
         */
        public int getData16BE(int pos) {
            int val = BasicFat.INVALID_GROUP_NUMBER;
            pos *= 2;
            for (int i = 0; i < size(); i++) {
                DiskBasicFatBuffer buf = get(i);
                if (pos < buf.getSize()) {
                    val = buf.get16BE(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * 16ビットデータ(ビッグエンディアン)をセット
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16BE(int pos, int val) {
            pos *= 2;
            for (int i = 0; i < size(); i++) {
                DiskBasicFatBuffer buf = get(i);
                if (pos < buf.getSize()) {
                    buf.Set16BE(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }
    }

    /**
     * FATバッファ（ミラーリング含む） DiskBasicFatBuffers の配列
     */
    public static class DiskBasicFatArea extends ArrayList<DiskBasicFatBuffers> {

        /** 有効なバッファ数 */
        private int validCount;

        public DiskBasicFatArea() {
            super();
            validCount = size();
        }

        /**
         * 代入
         */
        public DiskBasicFatArea operatorAssign(DiskBasicFatArea src) {
            super.clear();
            super.addAll(src); // Similar to C++ operator=
            validCount = src.validCount;
            return this;
        }

        /**
         * クリア
         */
        public void empty() {
            super.clear();
            validCount = 0;
        }

        /**
         * 追加
         * @param lItem   追加するアイテム
         * @param nInsert 追加する数 (ignored in ArrayList add)
         */
        public void Add(DiskBasicFatBuffers lItem, int nInsert) {
            // C++ wxArray: Add(item, count) repeats item 'count' times.
            for (int i = 0; i < nInsert; i++) {
                super.add(lItem);
            }
            validCount = size();
        }

        // Simplification for the most common case:
        public void Add(DiskBasicFatBuffers lItem) {
            Add(lItem, 1);
        }

        /**
         * 有効なバッファ数をセット
         */
        public void setValidCount(int val) {
            validCount = val;
        }

        /**
         * 有効なバッファ数を返す
         */
        public int getValidCount() {
            return validCount;
        }

        /**
         * 8ビットデータを返す
         * @param idx ミラーリング位置
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int getData8(int idx, int pos) {
            int val = BasicFat.INVALID_GROUP_NUMBER;
            if (idx >= size()) return val;

            DiskBasicFatBuffers bufs = get(idx);
            val = bufs.getData8(pos);
            return val;
        }

        /**
         * 8ビットデータをセット
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void setData8(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData8(n, pos, val);
            }
        }

        /**
         * 8ビットデータをセット
         * @param idx ミラーリング位置
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void setData8(int idx, int pos, int val) {
            if (idx >= size()) return;

            DiskBasicFatBuffers bufs = get(idx);
            bufs.setData8(pos, val);
        }

        /**
         * 8ビットデータが一致するか
         * @param pos 位置(8ビット1単位)
         * @param val 値
         * @return 一致した数（多重分）
         */
        public int matchData8(int pos, int val) {
            int match_count = 0;
            for (int n = 0; n < getValidCount(); n++) {
                if (matchData8(n, pos, val)) match_count++;
            }
            return match_count;
        }

        /**
         * 8ビットデータが一致するか
         * @param idx ミラーリング位置
         * @param pos 位置(8ビット1単位)
         * @param val 値
         * @return 一致した
         */
        public boolean matchData8(int idx, int pos, int val) {
            if (idx >= size()) return false;

            DiskBasicFatBuffers bufs = get(idx);
            return bufs.matchData8(pos, val);
        }

        /**
         * 8ビットデータのビットをセット/リセット
         * @param idx    ミラーリング位置
         * @param pos    位置(8ビット1単位)
         * @param mask   対象のビット
         * @param val    セット/リセット
         * @param invert 反転するか
         */
        public void bitData8(int idx, int pos, byte mask, boolean val, boolean invert) {
            if (idx >= size()) return;

            DiskBasicFatBuffers bufs = get(idx);
            bufs.bitData8(pos, mask, val, invert);
        }

        /**
         * 12ビットデータ(リトルエンディアン)を返す
         * @param idx ミラーリング位置
         * @param pos 位置(12ビット1単位)
         * @return 値
         */
        public int getData12LE(int idx, int pos) {
            int val = BasicFat.INVALID_GROUP_NUMBER;
            if (idx >= size()) return val;

            DiskBasicFatBuffers bufs = get(idx);
            val = bufs.getData12LE(pos);
            return val;
        }

        /**
         * 12ビットデータ(リトルエンディアン)をセット
         * @param pos 位置(12ビット1単位)
         * @param val 値
         */
        public void setData12LE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData12LE(n, pos, val);
            }
        }

        /**
         * 12ビットデータ(リトルエンディアン)をセット
         * @param idx ミラーリング位置
         * @param pos 位置(12ビット1単位)
         * @param val 値
         */
        public void setData12LE(int idx, int pos, int val) {
            if (idx >= size()) return;

            DiskBasicFatBuffers bufs = get(idx);
            bufs.setData12LE(pos, val);
        }

        /**
         * 16ビットデータ(リトルエンディアン)を返す
         * @param idx ミラーリング位置
         * @param pos 位置(16ビット1単位)
         * @return 値
         */
        public int getData16LE(int idx, int pos) {
            int val = BasicFat.INVALID_GROUP_NUMBER;
            if (idx >= size()) return val;

            DiskBasicFatBuffers bufs = get(idx);
            val = bufs.getData16LE(pos);
            return val;
        }

        /**
         * 16ビットデータ(リトルエンディアン)をセット
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16LE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData16LE(n, pos, val);
            }
        }

        /**
         * 16ビットデータ(リトルエンディアン)をセット
         * @param idx ミラーリング位置
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16LE(int idx, int pos, int val) {
            if (idx >= size()) return;

            DiskBasicFatBuffers bufs = get(idx);
            bufs.setData16LE(pos, val);
        }

        /**
         * 16ビットデータ(ビッグエンディアン)を返す
         * @param idx ミラーリング位置
         * @param pos 位置(16ビット1単位)
         * @return 値
         */
        public int getData16BE(int idx, int pos) {
            int val = BasicFat.INVALID_GROUP_NUMBER;
            if (idx >= size()) return val;

            DiskBasicFatBuffers bufs = get(idx);
            val = bufs.getData16BE(pos);
            return val;
        }

        /**
         * 16ビットデータ(ビッグエンディアン)をセット
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16BE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData16BE(n, pos, val);
            }
        }

        /**
         * 16ビットデータ(ビッグエンディアン)をセット
         * @param idx ミラーリング位置
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16BE(int idx, int pos, int val) {
            if (idx >= size()) return;

            DiskBasicFatBuffers bufs = get(idx);
            bufs.setData16BE(pos, val);
        }
    }

    /**
     * FATアクセス
     */
    public static class DiskBasicFat {

        private DiskBasic basic;
        private DiskBasicType type;
        /** FATの数 */
        private int count;
        /** 使用しているFATの数 */
        private int vCount;
        /** FATサイズ(セクタ数) */
        private int size;
        /** 開始セクタ番号 */
        private int start;
        /** 開始位置 */
        private int startPos;

        private DiskBasicFatArea bufs;

        private DiskBasicFat() {
            // Private constructor prevents use of default constructor
        }

        public DiskBasicFat(DiskBasic basic) {
            this.basic = basic;
            type = null;
            clear();
        }

        /**
         * FATエリアをアサイン
         * @param is_formatting フォーマット中か
         * @return 1.0  正常
         *  <1.0 警告あり
         *  <0.0 エラーあり
         */
        public double assign(boolean is_formatting) throws IOException {
            double validRatio = 1.0;

            int sectorNum = basic.diskBasicParam.getFatStartSector();
            int sideNum = basic.diskBasicParam.getReversedSideNumber(basic.diskBasicParam.getFatSideNumber());

            bufs.empty();

            type = basic.getType();

            if (sectorNum >= 0) {
                DiskImageTrack managed_track;
                int[] sideNums = new int[] {sideNum};
                int[] sectorNums = new int[] {sectorNum};

                if (sideNum >= 0) {
                    // トラック、サイド番号から計算
                    managed_track = basic.getTrack(basic.diskBasicParam.getManagedTrackNumber(), sideNum);
                } else {
                    // セクタ番号の通し番号で計算
                    managed_track = basic.getManagedTrack(basic.diskBasicParam.getReservedSectors(), sideNums, sectorNums);
                }
                if (managed_track == null) {
                    return -1.0;
                }

                sideNum = sideNums[0];
                sectorNum = sectorNums[0];

                // セクタ位置を得る
                start = type.getSectorPosFromNum(basic.getManagedTrackNumber(), sideNum, sectorNum, 0, 1);

                count = basic.diskBasicParam.getNumberOfFats();
                size = basic.diskBasicParam.getSectorsPerFat();
                startPos = basic.diskBasicParam.getFatStartPos();
                vCount = basic.diskBasicParam.getValidNumberOfFats();
                if (vCount < 0) {
                    vCount = count;
                }

                type.calcManagedStartGroup();

                // set buffer pointer for useful accessing
                int startSector = start;
                int endSector = start + size - 1;
                for (int fatNum = 0; fatNum < count && validRatio >= 0.0; fatNum++) {
                    DiskBasicFatBuffers fatbufs = new DiskBasicFatBuffers();
                    for (int secNum = startSector; secNum <= endSector; secNum++) {
                        int[] divNumArr = new int[] {0};
                        int[] divNumsArr = new int[] {1};
                        DiskImageSector sector = basic.getSectorFromSectorPos(secNum, divNumArr, divNumsArr);
                        int divNum = divNumArr[0];
                        int divNums = divNumsArr[0];

                        if (sector == null) {
                            validRatio = -1.0;
                            break;
                        }

                        int ssize = sector.getSectorSize();
                        ssize /= divNums;
                        byte[] buf = sector.getSectorBuffer();

                        // The C++ original uses pointer arithmetic (buf += offset) to get the data start.
                        // In Java, we'll need to calculate the index offset and the actual size for the slice.
                        int offset = (ssize * divNum);
                        int buf_size = ssize;

                        if (secNum == startSector) {
                            // 最初のセクタだけ開始位置がずれる
                            offset += startPos;
                            buf_size -= startPos;
                        }

                        // Creating the slice/reference: assuming buf holds all data and we take a slice from offset of size buf_size.
                        // Since DiskBasicFatBuffer's buffer is a byte array, we'll pass the reference and manage offset/size.
                        // BUT, DiskBasicFatBuffer methods only use 'pos < size' without explicit offset.
                        // This implies the buffer passed to DiskBasicFatBuffer must be the *start* of the data chunk.
                        // Since Java doesn't do pointer arithmetic, we'll pass the original array and use offset/size logic
                        // here or create a temporary array (costly).
                        // Given the constraint of not changing method signatures if possible, we pass the original buffer
                        // to DiskBasicFatBuffer and rely on its access methods, which is problematic since the C++ logic
                        // is not offset-aware in Get/Set.
                        // Sticking to the C++ logic of passing a pointer (reference to start of segment) and segment size:

                        // We can't pass a "pointer" to an offset in Java. We must ensure the `buf` inside `DiskBasicFatBuffer`
                        // corresponds to the memory region. The best Java approximation is to copy the slice or
                        // use a custom class that tracks the base buffer and an offset.
                        // Since the logic relies on DiskBasicFatBuffer::Get/Set/etc. which *don't* take an offset,
                        // we must assume the C++ `buf` pointer after the offset calculation points to the *start* of the
                        // relevant data for the `DiskBasicFatBuffer` object.
                        // The simplest direct conversion is to treat the `buf` pointer as the base array, and the offset
                        // calculation as an instruction to skip data, effectively reducing the buffer size.

                        // Since buf is the entire sector buffer, and we can't change its reference to point to a memory address,
                        // we must create a slice array.

                        if (buf_size <= 0) continue; // Skip if no data left

                        byte[] sliced_buf = Arrays.copyOfRange(buf, offset, offset + buf_size);

                        DiskBasicFatBuffer fatbuf = new DiskBasicFatBuffer(sliced_buf, buf_size);
                        fatbufs.add(fatbuf);
                    }
                    bufs.Add(fatbufs);

                    startSector += size;
                    endSector += size;
                }

                bufs.setValidCount(vCount);
            }

            if (validRatio >= 0.0) {
                validRatio = type.checkFat(is_formatting);
            }

            return validRatio;
        }

        /**
         * FATエリアのアサインを解除
         */
        public void clear() {
            count = 0;
            vCount = 0;
            size = 0;
            start = 0;
            startPos = 0;

            bufs.clear();
        }

        /**
         * FATエリアのアサインを解除
         */
        public void empty() {
            clear();
        }

        /**
         * @param pos 位置
         * FAT領域の最初のセクタの指定位置のデータを取得
         */
        public int get(int pos) {
            int code = 0;
            DiskImageSector sector = basic.getSectorFromSectorPos(start);
            if (sector != null) {
                byte[] buf = sector.getSectorBuffer();
                int size = sector.getSectorBufferSize();
                if (buf != null && pos < size) {
                    code = buf[pos] & 0xFF;
                }
            }
            return code;
        }

        /**
         * @param pos  位置
         * @param code コード
         * FAT領域の最初のセクタにデータを書く
         */
        public void set(int pos, byte code) {
            int start_sector = start;
            for (int fat_num = 0; fat_num < vCount; fat_num++) {
                DiskImageSector sector = basic.getSectorFromSectorPos(start_sector);
                if (sector != null) {
                    byte[] buf = sector.getSectorBuffer();
                    int size = sector.getSectorBufferSize();
                    if (buf != null && pos < size) {
                        buf[pos] = code;
                    }
                }
                start_sector += size;
            }
        }

        /**
         * @param buf バッファ
         * @param len サイズ
         * FAT領域の最初のセクタにデータを書く
         */
        public void copy(byte[] buf, int len) {
            int start_sector = start;
            for (int fat_num = 0; fat_num < vCount; fat_num++) {
                DiskImageSector sector = basic.getSectorFromSectorPos(start_sector);
                if (sector != null) {
                    // Assuming DiskImageSector::Copy handles slicing/size checks internally
                    sector.copy(buf, len);
                }
                start_sector += size;
            }
        }

        /**
         * @param code コード
         * FAT領域を指定コードで埋める
         */
        public void fill(byte code) {
            int start_sector = start;
            int end_sector = start + size - 1;
            for (int fat_num = 0; fat_num < count; fat_num++) {
                for (int sec_num = start_sector; sec_num <= end_sector; sec_num++) {
                    DiskImageSector sector = basic.getSectorFromSectorPos(sec_num);
                    if (sector != null) {
                        sector.fill(code);
                    }
                }
                start_sector += size;
                end_sector += size;
            }
        }

        /**
         * FAT領域を返す
         */
        public DiskBasicFatArea getDiskBasicFatArea() {
            return bufs;
        }

        /**
         * @param idx ミラーリングしているときのインデックス
         * FATバッファを返す
         */
        public DiskBasicFatBuffers getDiskBasicFatBuffers(int idx) {
            if (idx >= bufs.size()) {
                return null;
            }
            return bufs.get(idx);
        }

        /**
         * @param idx    ミラーリングしているときのインデックス
         * @param subidx バッファ位置
         * FATバッファ（セクタ）を返す
         */
        public DiskBasicFatBuffer getDiskBasicFatBuffer(int idx, int subidx) {
            DiskBasicFatBuffers fatbufs = getDiskBasicFatBuffers(idx);
            if (fatbufs == null || subidx >= fatbufs.size()) {
                return null;
            }
            return fatbufs.get(subidx);
        }
    }
}
