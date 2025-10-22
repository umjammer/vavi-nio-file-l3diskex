/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.*;
import java.util.Arrays;

import l3diskex.Utils.FIFOBuffer;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParser.DiskImageParser;


/*  */
public class DiskSTRParser extends DiskImageParser {

    /*  */
    static class Expand2FIFOBuffer extends FIFOBuffer {
        private int m_last_pos;
        private final int[] m_istr_pos = new int[9];
        private final int[] m_estr_pos = new int[9];

        public Expand2FIFOBuffer() {
            super();
            m_last_pos = 0;
            Arrays.fill(m_istr_pos, 0);
            Arrays.fill(m_estr_pos, 0);
        }

        @Override
        public void clear() {
            super.clear();
            m_last_pos = 0;
            Arrays.fill(m_istr_pos, 0);
            Arrays.fill(m_estr_pos, 0);
        }

        public void SetLastPos(int val) { m_last_pos = val; }
        public int GetLastPos() { return m_last_pos; }

        public void SetIStreamPos(int idx, int val) { m_istr_pos[idx] = val; }
        public void SetEStreamPos(int idx, int val) { m_estr_pos[idx] = val; }

        public int GetIStreamPos(int idx) { return m_istr_pos[idx]; }
        public int GetEStreamPos(int idx) { return m_estr_pos[idx]; }
    }

    /* --- Internal data structures --------------------------------------------------- */
    private static class str_header_t {
        public int data;
    }

    private static class str_track_header_t {
        public int track;
        public int side;
        public int reserved;
        public int secsize;
    }

    private static class str_sector_id_t {
        public int track;
        public int side;
        public int number;
        public int sector;
        public int secsize;
    }

    /* -------- */

    private final int          m_compress_type;      // 0 = normal, 1 = X1, 2 = X2, 3 = X3
    private final Expand2FIFOBuffer m_estream;       // used by Xx/XX functions

    /* -------- */

    public DiskSTRParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
        m_compress_type = 0;
        m_estream = new Expand2FIFOBuffer();
    }

    private int ParseSector(byte[] buf, int sz) {
        return sz;
    }

    public int ParseSector(FileParam param, int secnum) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int ParseTrack(FileParam param, int tracknum, int secnum) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int ParseDisk(FileParam param) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int ParseHeader(FileParam param, int tracknum, int secnum) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int ExpandFirst(FileParam param, int sz) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int ExpandNext(FileParam param, int sz) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int Expand2(FileParam param, int sz) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int Expand2Element(FileParam param, int sz) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int Expand1(FileParam param, int sz) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int Expand1Element(FileParam param, int sz) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int Expand0(FileParam param, int sz) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public int Expand0Element(FileParam param, int sz) throws IOException {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }

    public void AdjustIStream(FileParam param, int sz) {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
    }

    public int Check(FileParam param, int sz) {
        /* The original C++ implementation is not translated.
           Replace with a simple stub that mimics the expected behaviour. */
        return 0;
    }
}
