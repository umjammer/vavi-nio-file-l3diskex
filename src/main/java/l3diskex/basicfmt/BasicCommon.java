/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.FileTypeMask;
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


// TODO demolish
public class BasicCommon {

    /**
     * 共通属性フラグ
     */
    public enum FileTypeMask {
        FILE_TYPE_BASIC_MASK(0x000001),
        FILE_TYPE_DATA_MASK(0x000002),
        FILE_TYPE_MACHINE_MASK(0x000004),
        FILE_TYPE_ASCII_MASK(0x000008),
        FILE_TYPE_BINARY_MASK(0x000010),
        FILE_TYPE_RANDOM_MASK(0x000020),
        FILE_TYPE_ENCRYPTED_MASK(0x000040),
        FILE_TYPE_READWRITE_MASK(0x000080),
        FILE_TYPE_READONLY_MASK(0x000100),
        FILE_TYPE_HIDDEN_MASK(0x000200),
        FILE_TYPE_SYSTEM_MASK(0x000400),
        FILE_TYPE_VOLUME_MASK(0x000800),
        FILE_TYPE_DIRECTORY_MASK(0x001000),
        FILE_TYPE_ARCHIVE_MASK(0x002000),
        FILE_TYPE_LIBRARY_MASK(0x004000),
        FILE_TYPE_NONSHARE_MASK(0x008000),
        FILE_TYPE_UNDELETE_MASK(0x010000),
        FILE_TYPE_WRITEONLY_MASK(0x020000),
        FILE_TYPE_TEMPORARY_MASK(0x040000),
        FILE_TYPE_INTEGER_MASK(0x080000),
        FILE_TYPE_HARDLINK_MASK(0x100000),
        FILE_TYPE_SOFTLINK_MASK(0x200000);

        private final int value;

        FileTypeMask(int value) {
            this.value = value;
        }

        public final int getValue() {
            return value;
        }

        public static final int FILE_TYPE_EXTENSION_MASK = FILE_TYPE_BASIC_MASK.getValue()
                | FILE_TYPE_DATA_MASK.getValue()
                | FILE_TYPE_MACHINE_MASK.getValue()
                | FILE_TYPE_ASCII_MASK.getValue()
                | FILE_TYPE_BINARY_MASK.getValue()
                | FILE_TYPE_RANDOM_MASK.getValue()
                | FILE_TYPE_INTEGER_MASK.getValue();
    }

    /**
     * ディレクトリエントリ L3,S1 ５インチ,８インチ(倍密度)
     */
    @Serdes
    public static class DirectoryL32d implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte type2;
        @Element(sequence = 5)
        public byte startGroup;
        @Element(sequence = 6)
        public short endBytes; // used size of end cluster (big endian)

        @Element(sequence = 7)
        public byte[] reserved = new byte[16]; // char reserved[16]

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ L3 ３インチ(単密度) / F-BASIC 倍密度
     */
    @Serdes(bigEndian = false)
    public static class DirectoryFat8f implements DirectoryT {
        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3]; // not used.
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte type2;
        @Element(sequence = 5)
        public byte type3;
        @Element(sequence = 6)
        public byte startGroup;

        @Element(sequence = 7)
        public byte[] reserved = new byte[17];

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ n88 BASIC
     */
    @Serdes(bigEndian = false)
    public static class DirectoryN88 implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[6];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type; // byte
        @Element(sequence = 4)
        public byte startGroup; // byte
        @Element(sequence = 5)
        public byte[] reserved = new byte[5];

        public static final int SIZE = 16;
    }

    /**
     * ディレクトリエントリ X1 Hu-BASIC
     */
    @Serdes(bigEndian = false)
    public static class DirectoryX1Hu implements DirectoryT {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[13];
        @Element(sequence = 3)
        public byte[] ext = new byte[3];
        @Element(sequence = 4)
        public byte password;
        @Element(sequence = 5)
        public short fileSize;
        @Element(sequence = 6)
        public short loadAddr;
        @Element(sequence = 7)
        public short execAddr;
        @Element(sequence = 8)
        public byte[] date = new byte[3]; // yymwdd yy:BCD 00-99 m:HEX 0-C w:WEEK HEX 0(SUN)-7(SAT) dd:BCD
        @Element(sequence = 9)
        public byte[] time = new byte[2]; // hhmi BCD
        @Element(sequence = 19)
        public byte startGroupH;
        @Element(sequence = 11)
        public short startGroupL;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MZ DISK BASIC
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMz implements DirectoryT {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[17]; // file name has $0D on the end of string
        @Element(sequence = 3)
        public byte type2;
        @Element(sequence = 4)
        public byte reserved;
        @Element(sequence = 5)
        public short fileSize;
        @Element(sequence = 6)
        public short loadAddr;
        @Element(sequence = 7)
        public short execAddr;
        @Element(sequence = 8)
        public byte[] dateTime = new byte[4];
        @Element(sequence = 9)
        public short startSector;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MS-DOS FAT (32bytes)
     */
    public static class DirectoryMsDos implements DirectoryT {

        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte type;
        public byte ntres;
        public byte ctimeTenth;
        public short ctime;
        public short cdate;
        public short adate;
        public short startGroupHi;
        public short wtime;
        public short wdate;
        public short startGroup;
        public int fileSize;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MS-DOS LFN (32bytes)
     */
    public static class DirectoryMsLfn implements DirectoryT {

        public byte order; // byte
        public byte[] name = new byte[10];
        public byte type; // byte
        public byte type2; // byte
        public byte chksum; // byte
        public byte[] name2 = new byte[12];
        public short dummyGroup; // wxUint16
        public byte[] name3 = new byte[4];

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ Human68K (MS-DOS compatible) (32bytes)
     */
    public static class DirectoryHu68k implements DirectoryT {

        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte type; // byte
        public byte[] name2 = new byte[10];
        public short wtime; // wxUint16
        public short wdate; // wxUint16
        public short startGroup; // wxUint16
        public int fileSize; // int

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ L-os Angeles (MS-DOS compatible) (32bytes)
     */
    public static class DirectoryLosa implements DirectoryT {

        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte type; // byte
        public byte[] startAddr = new byte[4]; // byte[4]
        public byte binaryType; // byte
        public byte[] execAddr = new byte[4]; // byte[4]
        public byte reserved; // byte
        public short wtime; // wxUint16
        public short wdate; // wxUint16
        public short startGroup; // wxUint16
        public int fileSize; // int

        public static final int SIZE = 32;
    }

    // Since Java doesn't have unions like C++, we'll use a common interface/superclass
    // and a structure that holds all of them, or a dedicated base class for MS-DOS like entries.

    /**
     * ディレクトリエントリ MS-DOS compatible (32bytes)
     * In C++, this was a union. In Java, we'll make a holder class.
     */
    public static class DirectoryMs implements DirectoryT {

        public DirectoryMsDos msdos = new DirectoryMsDos();
        public DirectoryMsLfn mslfn = new DirectoryMsLfn();
        public DirectoryHu68k hu68k = new DirectoryHu68k();
        public DirectoryLosa losa = new DirectoryLosa();

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ FLEX (24bytes)
     */
    public static class DirectoryFlex implements DirectoryT {

        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte type; // byte
        public byte reserved; // byte
        public byte startTrack; // byte
        public byte startSector; // byte
        public byte lastTrack; // byte
        public byte lastSector; // byte
        public short totalSectors; // wxUint16
        public byte randomAccess; // byte
        public byte reserved2; // byte
        public byte month; // byte
        public byte day; // byte
        public byte year; // byte

        public static final int SIZE = 24;
    }

    /**
     * FLEX top of each sector
     */
    @Serdes
    public static class FlexPtr {

        @Element(sequence = 1)
        public byte nextTrack; // byte
        @Element(sequence = 2)
        public byte nextSector; // byte
        @Element(sequence = 3)
        public short seqNum; // wxUint16
    }

    /**
     * OS-9 LSN
     */
    public static class Os9Lsn {

        public byte h; // byte
        public byte m; // byte
        public byte l; // byte

        public int getOs9Lsn() {
            return (((h & 0xff) << 16) | ((m & 0xff) << 8) | (l & 0xff));
        }

        public void setOs9Lsn(int val) {
            h = (byte) ((val & 0xff0000) >> 16);
            m = (byte) ((val & 0xff00) >> 8);
            l = (byte) (val & 0xff);
        }
    }

    /**
     * OS-9 Segment
     */
    public static class Os9Segment {

        public Os9Lsn lsn = new Os9Lsn();
        public short siz; // wxUint16
    }

    /**
     * OS-9 Date Format
     */
    public static class Os9Date {

        public byte yy; // byte
        public byte mm; // byte
        public byte dd; // byte
        public byte hh; // byte
        public byte mi; // byte
    }

    /**
     * OS-9 Created Date
     */
    public static class Os9Cdate {

        public byte yy; // byte
        public byte mm; // byte
        public byte dd; // byte
    }

    /**
     * ディレクトリエントリ OS-9 (32bytes)
     */
    public static class DirectoryOs9 implements DirectoryT {

        public byte[] deNam = new byte[28];
        public byte deReserved; // byte
        public Os9Lsn deLsn = new Os9Lsn(); // link to FD

        public static final int SIZE = 32;
    }

    /**
     * OS-9 File Descriptor
     */
    public static class DirectoryOs9Fd implements DirectoryT {

        public byte fdAtt; // 1 attr
        public short fdOwn; // 2 owner id
        public Os9Date fdDat = new Os9Date(); // 5 date
        public byte fdLnk; // 1 link count
        public int fdSiz; // 4 in bytes
        public Os9Cdate fdDcr = new Os9Cdate(); // 3 created date
        public Os9Segment[] fdSeg = new Os9Segment[48]; // 5*48=240

        public DirectoryOs9Fd() {
            for (int i = 0; i < 48; i++) {
                fdSeg[i] = new Os9Segment();
            }
        }

        public static final int SIZE = 256;
    }

    /**
     * ディレクトリエントリ CP/M (32bytes)
     */
    public static class DirectoryCpm implements DirectoryT, Cloneable {

        public byte type; // byte user id
        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte extentNum; // byte
        public byte[] reserved = new byte[2];
        public byte recordNum; // byte

        // This union is complex. In Java, we'll store the bytes and provide accessors if needed.
        public byte[] mapBytes = new byte[16]; // byte b[16]
        // public short[] mapWords = new short[8]; // wxUint16 w[8]

        @Override
        public DirectoryCpm clone() {
            return new DirectoryCpm();
        }

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ TF-DOS (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryTfdos implements DirectoryT {

        @Element(sequence = 1)
        public byte type; // byte
        @Element(sequence = 2)
        public byte[] name = new byte[8]; // file name ends with $0D and fills rest with $20
        @Element(sequence = 3)
        public short fileSize;
        @Element(sequence = 4)
        public short loadAddr;
        @Element(sequence = 5)
        public short execAddr;
        @Element(sequence = 6)
        public byte track; // byte

        public static final int SIZE = 16;
    }

    /**
     * ディレクトリエントリ PC-8001 DOS (New PC.DOS) (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryDos80 implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[16];

        public static final int SIZE = 16;
    }

    /**
     * PC-8001 DOS (New PC.DOS) グループエントリ
     */
    static class DirectoryDos80Grp {

        public byte g; // byte
        public short a; // wxUint16
    }

    /**
     * ディレクトリエントリ2 PC-8001 DOS (New PC.DOS) (16bytes)
     */
    public static class DirectoryDos80_2 implements DirectoryT {

        public DirectoryDos80Grp[] grps = new DirectoryDos80Grp[5]; // 3 x 5
        public byte reserved; // byte

        public DirectoryDos80_2() {
            for (int i = 0; i < 5; i++) {
                grps[i] = new DirectoryDos80Grp();
            }
        }

        public static final int SIZE = 16;
    }

    /**
     * ディレクトリエントリ C-DOS (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryCdos implements DirectoryT {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[17]; // file name ends with $0D
        @Element(sequence = 3)
        public byte type2;
        @Element(sequence = 4)
        public byte byteOrder;
        @Element(sequence = 5)
        public short fileSize;
        @Element(sequence = 6)
        public short loadAddr;
        @Element(sequence = 7)
        public short execAddr;
        @Element(sequence = 8)
        public byte yy;
        @Element(sequence = 9)
        public byte mm;
        @Element(sequence = 10)
        public byte dd;
        @Element(sequence = 11)
        public byte reserved2;
        @Element(sequence = 12)
        public byte track;
        @Element(sequence = 13)
        public byte sector;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MZ Floppy DOS (64bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMzFdos implements DirectoryT {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[17]; // file name has $0D on the end of string
        @Element(sequence = 3)
        public short fileSize;
        @Element(sequence = 4)
        public short loadAddr; // wxUint16
        @Element(sequence = 5)
        public short execAddr;
        @Element(sequence = 6)
        public short groups;
        @Element(sequence = 7)
        public byte[] attr = new byte[2]; // 0x30 0x53
        @Element(sequence = 8)
        public byte[] password = new byte[2]; // 0x00 0x00
        @Element(sequence = 9)
        public short dummySector; // 0x01 0x02

        @Element(sequence = 10)
        public byte[] mmddyy = new byte[7];
        @Element(sequence = 11)
        public byte[] reserved2 = new byte[13];
        @Element(sequence = 12)
        public byte track;
        @Element(sequence = 13)
        public byte sector;
        @Element(sequence = 14)
        public byte[] reserved3 = new byte[5];
        @Element(sequence = 15)
        public byte seqNum;
        @Element(sequence = 16)
        public byte unknown1; // 0x9x - 0xax
        @Element(sequence = 17)
        public byte unknown2; // 0x15
        @Element(sequence = 18)
        public byte dataTrack;
        @Element(sequence = 19)
        public byte dataSector;

        public static final int SIZE = 64;
    }

    /**
     * ディレクトリエントリ Frost-DOS (16bytes)
     */
    public static class DirectoryFrost implements DirectoryT {

        public byte[] name = new byte[6];
        public byte[] ext = new byte[3];
        public byte type; // byte
        public byte track; // byte
        public byte sector; // byte
        public short loadAddr; // wxUint16
        public short size; // wxUint16

        public static final int SIZE = 16;
    }

    /**
     * X-DOSセグメント情報
     */
    public static class XdosSeg {

        public byte track; // byte
        public byte sector; // byte
        public byte size; // byte
    }

    /**
     * ディレクトリエントリ X-DOS X1 (32bytes)
     */
    public static class DirectoryXdos implements DirectoryT {

        public short ftype; // wxUint16 big endien
        public byte[] name = new byte[16];
        public short loadAddr; // wxUint16
        public short fileSize; // wxUint16
        public short execAddr; // wxUint16
        public short date; // wxUint16
        public short time; // wxUint16
        public byte attr; // byte アトリビュート
        public XdosSeg start = new XdosSeg();

        public static final int SIZE = 32;
    }

    /**
     * Magical DOS セグメント情報
     */
    public static class MagicalSeg {

        public byte track; // byte
        public byte sector; // byte
        public byte size; // byte
    }

    /**
     * ディレクトリエントリ Magical DOS
     */
    public static class DirectoryMagical implements DirectoryT {

        public byte type; // 1
        public byte[] name = new byte[31];
        public byte type2; // 1
        public short loadAddr; // 2
        public short fileSize; // 2
        public short execAddr; // 2
        public byte[] date = new byte[2];
        public byte[] time = new byte[2];
        public byte[] reserved = new byte[2];
        public MagicalSeg start = new MagicalSeg(); // 3

        public static final int SIZE = 48;
    }

    /**
     * ディレクトリエントリ S-DOS (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectorySdos implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[22];
        @Element(sequence = 2)
        public byte type;
        @Element(sequence = 3)
        public byte track;
        @Element(sequence = 4)
        public byte sector;
        @Element(sequence = 5)
        public byte size; // number of sector
        @Element(sequence = 6)
        public byte restSize;
        @Element(sequence = 7)
        public short loadAddr;
        @Element(sequence = 8)
        public short execAddr;
        @Element(sequence = 9)
        public byte reserved;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ C82-BASIC (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryFp implements DirectoryT {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[8];
        @Element(sequence = 3)
        public byte[] ext = new byte[3];
        @Element(sequence = 4)
        public byte term;
        @Element(sequence = 5)
        public byte[] unknown = new byte[7];
        @Element(sequence = 6)
        public short loadAddr;
        @Element(sequence = 7)
        public short endAddr;
        @Element(sequence = 8)
        public short execAddr;
        @Element(sequence = 9)
        public short fileSize;
        @Element(sequence = 10)
        public byte startGroup;
        @Element(sequence = 11)
        public byte attr;
        @Element(sequence = 12)
        public byte[] reserved = new byte[2];

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MDOS (16bytes)
     */
    public static class DirectoryMdos implements DirectoryT {

        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte unknown; // byte
        public short startGroup; // wxUint16 little endien
        public short fileSize; // wxUint16 big endien

        public static final int SIZE = 16;
    }

    /**
     * ディレクトリエントリ Falcom (16bytes)
     */
    public static class DirectoryFalcom implements DirectoryT {

        public byte[] name = new byte[6];
        public short execAddr; // wxUint16
        public short startAddr; // wxUint16
        public short endAddr; // wxUint16
        public GroupPtr startGroup = new GroupPtr();
        public GroupPtr endGroup = new GroupPtr();

        public static class GroupPtr {

            public byte track; // byte
            public byte sector; // byte

            public byte[] getBytes() {
                return new byte[0];
            }
        }

        public static final int SIZE = 16;
    }

    /**
     * ディレクトリエントリ Apple DOS (35bytes)
     */
    public static class DirectoryApledos implements DirectoryT {

        public byte track; // byte
        public byte sector; // byte
        public byte type; // byte
        public byte[] name = new byte[30];
        public short sectorCount; // wxUint16 size (little endien)

        public static final int SIZE = 35;
    }

    /**
     * Apple DOS top of each sector
     */
    public static class ApledosPtr {

        public byte reserved; // byte
        public byte nextTrack; // byte
        public byte nextSector; // byte
    }

    /**
     * ディレクトリエントリ Apple ProDOS (39bytes)
     */
    public static class DirectoryProdos implements DirectoryT {

        public byte stypeAndNlen; // byte
        public byte[] name = new byte[15];
        public byte fileType; // byte file only
        public short keyPointer; // wxUint16 file only
        public short blocksUsed; // wxUint16 file only
        public byte[] eof = new byte[3]; // byte[3] file only
        public byte[] cdate = new byte[2];
        public byte[] ctime = new byte[2];
        public byte version; // byte
        public byte minVersion; // byte
        public byte access; // byte

        // Union for the variant part
        public ProdosAux aux = new ProdosAux();

        public static class ProdosAux {

            public V v = new V();
            public Sv sv = new Sv();
            public F f = new F();
        }

        public static class V {

            public byte entryLen; // byte
            public byte entriesPerBlock; // byte
            public short fileCount; // wxUint16
            public short bitmapPointer; // wxUint16
            public short totalBlocks; // wxUint16
        }

        public static class Sv {

            public byte entryLen; // byte
            public byte entriesPerBlock; // byte
            public short fileCount; // wxUint16
            public short parentPointer; // wxUint16
            public byte parentEntry; // byte
            public byte parentEntryLen; // byte
        }

        public static class F {

            public short auxType; // wxUint16 aux type
            public byte[] mdate = new byte[2];
            public byte[] mtime = new byte[2];
            public short headerPointer; // wxUint16
        }

        public static final int SIZE = 39;
    }

    /**
     * トラック＆セクタ Commodore 1541
     */
    public static class C1541Ptr {

        public byte track; // byte
        public byte sector; // byte
    }

    /**
     * ディレクトリエントリ Commodore 1541 (32bytes)
     */
    public static class DirectoryC1541 implements DirectoryT {

        public byte[] doNotWrite = new byte[2]; // first entry only on each sector
        public byte type; // byte
        public C1541Ptr firstData = new C1541Ptr();
        public byte[] name = new byte[16];
        public C1541Ptr firstSide = new C1541Ptr(); // relative file only
        public byte recordSize; // byte relative file only
        public byte[] unused = new byte[4];
        public C1541Ptr replace = new C1541Ptr();
        public short numOfBlocks; // wxUint16

        public static final int SIZE = 32;
    }

    /**
     * Amiga block structure head (all Big Endien)
     */
    public static class AmigaBlockPre {

        public static final int SIZE = 4 + 4 + 4 + 4 + 4 + 4 + 4;
        public int type; // int starting block (T_HEADER:2 / T_LIST:16)
        public int headerKey; // int self pointer (except Root)
        public int highSeq; // int number of data (File only)
        public int tableSize; // int hash table size (Root only) (72)
        public int firstData; // int first data block pointer (File only)
        public int checkSum; // int

        public AmigaBlockPreUnion u = new AmigaBlockPreUnion();

        public static class AmigaBlockPreUnion {

            public int[] table = new int[1]; // int block pointer (72 items) - size in C++ is dynamic/contextual, using size 1 for minimum
            public byte[] symName = new byte[4]; // byte symbolic name (Soft link only)
        }
    }

    /**
     * Amiga Root Block (above hash_table)
     */
    public static class AmigaRootBlockPost {

        public static final int SIZE = 4 + 4 + 4 + 4 + 4 + 41 + 1 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4;
        public int bmFlag; // int value is -1 if disk bitmap is valid
        public int[] bmPages = new int[25]; // int blocks of disk bitmap
        public int bmExt; // int ext blocks of disk bitmap
        public int rDays; // int root dir modified date
        public int rMins; // int root dir modified time
        public int rTicks; // int root dir modified seconds
        public byte diskNameLen; // byte in bytes
        public byte[] diskName = new byte[31];
        public int[] unused = new int[2]; // int set to 0
        public int vDays; // int disk modified date
        public int vMins; // int disk modified time
        public int vTicks; // int disk modified seconds
        public int cDays; // int disk creation date
        public int cMins; // int disk creation time
        public int cTicks; // int disk creation seconds
        public int nextHash; // int always 0
        public int parentDir; // int always 0
        public int extension; // int always 0
        public int secType; // int always 1
    }

    /**
     * Amiga File / Directory Header Block (post table)
     */
    public static class AmigaHeaderPost {

        public static final int SIZE = 4 + 2 + 2 + 4 + 4 + 1 + 79 + 12 + 4 + 4 + 4 + 1 + 31 + 4 + 4 + 4 + 20 + 4 + 4 + 4 + 4;
        public byte[] unused0 = new byte[4];
        public short uid; // wxUint16 user id
        public short gid; // wxUint16 user group
        public int protect; // int
        public int byteSize; // int file size (File only)
        public byte commentLen; // byte in bytes
        public byte[] comment = new byte[79];
        public byte[] unused1 = new byte[12];
        public int days; // int modified date
        public int mins; // int modified time
        public int ticks; // int modified seconds
        public byte nameLen; // byte in bytes
        public byte[] name = new byte[31]; // 0 terminate
        public byte[] unused2 = new byte[4];
        public int realEntry; // int FFS unused (File only)
        public int nextLink; // int FFS hardlinks chained list
        public byte[] unused3 = new byte[20];
        public int hashChain; // int next entry with same hash
        public int parentDir; // int
        public int extension; // int 1st extension block / FFS: cache block
        public int secType; // int -2 (ST_USERDIR) / -3 (ST_FILE) / -4 (ST_LINKFILE) / 4 (ST_LINKDIR) / 3 (ST_SOFTLINK)
    }

    /**
     * Amiga block structure post table
     * In C++, this was a union. In Java, we'll make a holder class.
     */
    public static class AmigaBlockPost {
        public static final int SIZE = Math.max(AmigaRootBlockPost.SIZE, AmigaHeaderPost.SIZE);

        public AmigaRootBlockPost r = new AmigaRootBlockPost();
        public AmigaHeaderPost h = new AmigaHeaderPost();
    }

    /**
     * ディレクトリエントリ Amiga DOS
     * <p>
     * AmigaDOSは1セクタ分になるのでブロック番号だけを保持
     */
    @Serdes
    public static class DirectoryAmiga implements DirectoryT {

        public int blockNum; // int
        public AmigaBlockPre pre; // pointer
        public AmigaBlockPost post; // pointer

        public static final int SIZE = 9;
    }

    /**
     * ディレクトリエントリ M68 FDOS (31bytes)
     */
    public static class DirectoryM68fdos implements DirectoryT {

        public M68fdosName name = new M68fdosName();
        public M68fdosExt ext = new M68fdosExt();
        public short attr1; // wxUint16
        public short attr2; // wxUint16 unknown
        public short blockSize; // wxUint16
        public byte eofInSector; // byte
        public short date; // wxUint16
        public short time; // wxUint16 unknown
        public M68fdosRev rev = new M68fdosRev();
        public short startSector; // wxUint16
        public byte attr3; // byte
        public short endSector; // wxUint16 unknown
        public short loadAddr; // wxUint16
        public short execAddr; // wxUint16
        public byte[] unknown2 = new byte[3];

        public static class M68fdosName {

            public short[] w = new short[2]; // big endien
            public byte[] b = new byte[4];
        }

        public static class M68fdosExt {

            public short w; // big endien
            public byte[] b = new byte[2];
        }

        public static class M68fdosRev {

            public short w; // big endien
            public byte[] b = new byte[2];
        }

        public static final int SIZE = 31;
    }

    /**
     * TRSDOS gap
     */
    public static class TrsdosGap {

        public byte track; // byte
        public byte granules; // byte
    }

    /**
     * ディレクトリエントリ TRSDOS 2.x (32bytes)
     */
    public static class DirectoryTrsd23 implements DirectoryT {

        public byte accessControl; // byte
        public byte overflow; // byte
        public byte reserved1; // byte
        public byte eofByteOffset; // byte
        public byte recordLength; // byte
        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public short updatePassword; // wxUint16
        public short accessPassword; // wxUint16
        public short eofSector; // wxUint16
        public TrsdosGap[] gap = new TrsdosGap[5];

        public DirectoryTrsd23() {
            for (int i = 0; i < 5; i++) {
                gap[i] = new TrsdosGap();
            }
        }

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ TRSDOS 1.3 (48bytes)
     */
    public static class DirectoryTrsd13 implements DirectoryT {

        public byte accessControl; // byte
        public byte month; // byte 0x01 - 0x0c
        public byte year; // byte
        public byte eofByteOffset; // byte
        public byte recordLength; // byte
        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public short updatePassword; // wxUint16
        public short accessPassword; // wxUint16
        public short eofSector; // wxUint16
        public TrsdosGap[] gap = new TrsdosGap[13];

        public DirectoryTrsd13() {
            for (int i = 0; i < 13; i++) {
                gap[i] = new TrsdosGap();
            }
        }

        public static final int SIZE = 48;
    }

    /**
     * ディレクトリエントリ
     */
    public interface DirectoryT {

    }

    /**
     * DISK BASIC種類 番号
     */
    public enum DiskBasicFormatType {
        FORMAT_TYPE_UNKNOWN(-1),
        FORMAT_TYPE_L3_1S(0),
        FORMAT_TYPE_L3S1_2D(1),
        FORMAT_TYPE_FM(2),
        FORMAT_TYPE_MSDOS(3),
        FORMAT_TYPE_MSX(4),
        FORMAT_TYPE_N88(5),
        FORMAT_TYPE_X1HU(6),
        FORMAT_TYPE_MZ(7),
        FORMAT_TYPE_FLEX(8),
        FORMAT_TYPE_OS9(9),
        FORMAT_TYPE_CPM(10),
        FORMAT_TYPE_PA(11),
        FORMAT_TYPE_SMC(12),
        FORMAT_TYPE_FP(13),
        FORMAT_TYPE_HU68K(14),
        FORMAT_TYPE_APLEDOS(15),
        FORMAT_TYPE_PRODOS(16),
        FORMAT_TYPE_TRSD23(17),
        FORMAT_TYPE_TRSD13(18),
        FORMAT_TYPE_C1541(20),
        FORMAT_TYPE_AMIGA(21),
        FORMAT_TYPE_LOSA(31),
        FORMAT_TYPE_CDOS2(32),
        FORMAT_TYPE_DOS80(51),
        FORMAT_TYPE_FROST(52),
        FORMAT_TYPE_MAGICAL(53),
        FORMAT_TYPE_SDOS(54),
        FORMAT_TYPE_MDOS(55),
        FORMAT_TYPE_XDOS(61),
        FORMAT_TYPE_TFDOS(71),
        FORMAT_TYPE_CDOS(72),
        FORMAT_TYPE_MZ_FDOS(73),
        FORMAT_TYPE_M68FDOS(81),
        FORMAT_TYPE_FALCOM(91);

        private final int value;

        DiskBasicFormatType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        static DiskBasicFormatType valueOf(int v) {
            return Arrays.stream(values()).filter(e -> e.value == v).findFirst().orElseThrow();
        }
    }

    /**
     * ファイルプロパティでファイル名変更した時に渡す値
     */
    public static class DiskBasicFileName {

        /** ファイル名 */
        private String name;
        /** 拡張属性 ファイル名が同じでも、この属性が異なれば違うファイルとして扱う */
        private int optional;

        public DiskBasicFileName() {
            this.name = "";
            this.optional = 0;
        }

        public DiskBasicFileName(String nName, int nOptional) {
            this.name = nName;
            this.optional = nOptional;
        }

        /**
         * ファイル名
         */
        public final String getName() {
            return name;
        }

        /**
         * ファイル名
         */
        public void setName(String val) {
            this.name = val;
        }

        /**
         * 拡張属性 ファイル名が同じでも、この属性が異なれば違うファイルとして扱う
         */
        public int getOptional() {
            return optional;
        }

        /**
         * 拡張属性 ファイル名が同じでも、この属性が異なれば違うファイルとして扱う
         */
        public void setOptional(int val) {
            this.optional = val;
        }
    }

    /**
     * 属性保存クラス
     */
    public static class DiskBasicFileType {

        /** DISK BASIC種類 */
        private DiskBasicFormatType format;
        /** 共通属性 enum #en_file_type_mask の値の組み合わせ */
        private int type;
        /** 本来の属性 */
        private final int[] origin = new int[3];

        public DiskBasicFileType() {
            format = DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
            type = 0;
            for (int i = 0; i < origin.length; i++) {
                origin[i] = 0;
            }
        }

        /**
         * @param nFormat  フォーマット
         * @param nType    enum #en_file_type_mask の値の組み合わせ
         * @param nOrigin0 本来の属性
         * @param nOrigin1 本来の属性 つづき1
         * @param nOrigin2 本来の属性 つづき2
         */
        public DiskBasicFileType(DiskBasicFormatType nFormat, int nType, int nOrigin0, int nOrigin1, int nOrigin2) {
            format = nFormat;
            type = nType;
            origin[0] = nOrigin0;
            origin[1] = nOrigin1;
            origin[2] = nOrigin2;
        }

        public DiskBasicFileType(DiskBasicFormatType nFormat, int nType, int nOrigin0) {
            this(nFormat, nType, nOrigin0, 0, 0);
        }

        /**
         * DISK BASIC種類
         */
        public DiskBasicFormatType getFormat() {
            return format;
        }

        /**
         * DISK BASIC種類
         */
        public void setFormat(DiskBasicFormatType val) {
            format = val;
        }

        /**
         * 共通属性 enum #en_file_type_mask の値の組み合わせ
         */
        public int getType() {
            return type;
        }

        /**
         * 共通属性 enum #en_file_type_mask の値の組み合わせ
         */
        public void setType(int val) {
            type = val;
        }

        /**
         * 本来の属性
         */
        public int getOrigin(int idx) {
            return origin[idx];
        }

        public int getOrigin() {
            return getOrigin(0);
        }

        /**
         * 本来の属性
         */
        public void setOrigin(int val) {
            origin[0] = val;
        }

        /**
         * 本来の属性
         */
        public void setOrigin(int idx, int val) {
            origin[idx] = val;
        }

        /**
         * 共通属性が一致するか
         */
        public boolean matchType(int mask, int value) {
            return ((type & mask) == value);
        }

        /**
         * 共通属性が一致しないか
         */
        public boolean unmatchType(int mask, int value) {
            return ((type & mask) != value);
        }

        /**
         * 共通属性がアスキー属性か
         */
        public boolean isAscii() {
            return ((type & FileTypeMask.FILE_TYPE_ASCII_MASK.getValue()) != 0);
        }

        /**
         * 共通属性がボリューム属性か
         */
        public boolean isVolume() {
            return ((type & (FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue() | FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue())) == FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue());
        }

        /**
         * 共通属性がディレクトリ属性か
         */
        public boolean isDirectory() {
            return ((type & (FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue() | FileTypeMask.FILE_TYPE_VOLUME_MASK.getValue())) == FileTypeMask.FILE_TYPE_DIRECTORY_MASK.getValue());
        }
    }

    /**
     * グループ番号に対応する機種依存データを保持
     *
     * @see DiskBasicGroupItem
     */
    public static class DiskBasicGroupUserData implements Cloneable {

        public DiskBasicGroupUserData() {
        }

        @Override
        public DiskBasicGroupUserData clone() {
            try {
                return (DiskBasicGroupUserData) super.clone();
            } catch (CloneNotSupportedException e) {
                // Should not happen as we implement Cloneable
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * グループ番号に対応するパラメータを保持
     *
     * @see DiskBasicGroups
     */
    public static class DiskBasicGroupItem {

        /** グループ番号 int */
        public int group;
        /** 次のグループ番号 int */
        public int next;
        /** トラック番号 */
        public int track;
        /** サイド番号 */
        public int side;
        /** グループ内の開始セクタ番号 */
        public int sectorStart;
        /** グループ内の終了セクタ番号 */
        public int sectorEnd;
        /** １グループがセクタ内に複数ある時の分割位置 */
        public int divNum;
        /** １グループがセクタ内に複数ある時の分割数 */
        public int divNums;
        /** 機種依存データ */
        public DiskBasicGroupUserData userData;

        public DiskBasicGroupItem() {
            group = 0;
            next = 0;
            track = 0;
            side = 0;
            sectorStart = 0;
            sectorEnd = 0;
            divNum = 0;
            divNums = 1;
            userData = null;
        }

        public DiskBasicGroupItem(DiskBasicGroupItem src) {
            group = src.group;
            next = src.next;
            track = src.track;
            side = src.side;
            sectorStart = src.sectorStart;
            sectorEnd = src.sectorEnd;
            divNum = src.divNum;
            divNums = src.divNums;
            userData = null;
            if (src.userData != null) {
                userData = src.userData.clone();
            }
        }

        /**
         * 代入
         */
        public DiskBasicGroupItem set(DiskBasicGroupItem src) {
            group = src.group;
            next = src.next;
            track = src.track;
            side = src.side;
            sectorStart = src.sectorStart;
            sectorEnd = src.sectorEnd;
            divNum = src.divNum;
            divNums = src.divNums;
            if (userData != null) {
                // Delete old data before assigning new
                userData = null;
            }
            if (src.userData != null) {
                userData = src.userData.clone();
            }
            return this;
        }

        /**
         * @param nGroup グループ番号
         * @param nNext  次のグループ番号（任意）
         * @param nTrack トラック番号
         * @param nSide  サイド番号
         * @param nStart グループ内の開始セクタ番号
         * @param nEnd   グループ内の終了セクタ番号
         * @param nDiv   １グループがセクタ内に複数ある時の分割位置
         * @param nDivs  １グループがセクタ内に複数ある時の分割数
         * @param nUser  機種依存データ
         */
        public DiskBasicGroupItem(int nGroup, int nNext, int nTrack, int nSide, int nStart, int nEnd, int nDiv, int nDivs, DiskBasicGroupUserData nUser) {
            this.set(nGroup, nNext, nTrack, nSide, nStart, nEnd, nDiv, nDivs, nUser);
        }

        public DiskBasicGroupItem(int nGroup, int nNext, int nTrack, int nSide, int nStart, int nEnd) {
            this(nGroup, nNext, nTrack, nSide, nStart, nEnd, 0, 1, null);
        }

        /**
         * @param nGroup グループ番号
         * @param nNext  次のグループ番号（任意）
         * @param nTrack トラック番号
         * @param nSide  サイド番号
         * @param nStart グループ内の開始セクタ番号
         * @param nUser  機種依存データ
         */
        public DiskBasicGroupItem(int nGroup, int nNext, int nTrack, int nSide, int nStart, DiskBasicGroupUserData nUser) {
            this.set(nGroup, nNext, nTrack, nSide, nStart, nUser);
        }

        /**
         * データセット
         *
         * @param nGroup グループ番号
         * @param nNext  次のグループ番号（任意）
         * @param nTrack トラック番号
         * @param nSide  サイド番号
         * @param nStart グループ内の開始セクタ番号
         * @param nEnd   グループ内の終了セクタ番号
         * @param nDiv   １グループがセクタ内に複数ある時の分割位置
         * @param nDivs  １グループがセクタ内に複数ある時の分割数
         * @param nUser  機種依存データ
         */
        public void set(int nGroup, int nNext, int nTrack, int nSide, int nStart, int nEnd, int nDiv, int nDivs, DiskBasicGroupUserData nUser) {
            group = nGroup;
            next = nNext;
            track = nTrack;
            side = nSide;
            sectorStart = nStart;
            sectorEnd = nEnd;
            divNum = nDiv;
            divNums = nDivs;
            userData = nUser;
        }

        /**
         * データセット
         *
         * @param nGroup グループ番号
         * @param nNext  次のグループ番号（任意）
         * @param nTrack トラック番号
         * @param nSide  サイド番号
         * @param nStart グループ内の開始セクタ番号
         * @param nUser  機種依存データ
         */
        public void set(int nGroup, int nNext, int nTrack, int nSide, int nStart, DiskBasicGroupUserData nUser) {
            group = nGroup;
            next = nNext;
            track = nTrack;
            side = nSide;
            sectorStart = nStart;
            sectorEnd = nStart;
            divNum = 0;
            divNums = 1;
            userData = nUser;
        }

        /**
         * グループ番号でソートする際の比較
         */
        public static int compare(DiskBasicGroupItem item1, DiskBasicGroupItem item2) {
            return Integer.compare(item1.group, item2.group);
        }
    }

    /**
     * グループ番号のリストを保持
     * <p>
     * ディスク内ファイルのチェインをこのリストに保持する
     *
     * @see DiskBasicGroupItem
     * @see DiskBasicDirItem
     */
    public static class DiskBasicGroups {

        /** グループ番号のリスト */
        private final List<DiskBasicGroupItem> items;
        /** グループ数 */
        private int nums;
        /** グループ内の占有サイズ (int) */
        private int size;
        /** １グループのサイズ (int) */
        private int sizePerGroup;

        public DiskBasicGroups() {
            items = new ArrayList<>();
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        public DiskBasicGroups(List<DiskBasicGroupItem> items) {
            this.items = items;
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        /**
         * @param nGroup グループ番号
         * @param nNext  次のグループ番号（任意）
         * @param nTrack トラック番号
         * @param nSide  サイド番号
         * @param nStart グループ内の開始セクタ番号
         * @param nEnd   グループ内の終了セクタ番号
         * @param nDiv   １グループがセクタ内に複数ある時の分割位置
         * @param nDivs  １グループがセクタ内に複数ある時の分割数
         * @param nUser  機種依存データ
         *               追加
         */
        public void add(int nGroup, int nNext, int nTrack, int nSide, int nStart, int nEnd, int nDiv, int nDivs, DiskBasicGroupUserData nUser) {
            items.add(new DiskBasicGroupItem(nGroup, nNext, nTrack, nSide, nStart, nEnd, nDiv, nDivs, nUser));
        }

        public void add(int nGroup, int nNext, int nTrack, int nSide, int nStart, int nEnd) {
            add(nGroup, nNext, nTrack, nSide, nStart, nEnd, 0, 1, null);
        }

        public void add(int nGroup, int nNext, int nTrack, int nSide, int nStart, int nEnd, int nDiv, int nDivs) {
            add(nGroup, nNext, nTrack, nSide, nStart, nEnd, nDiv, nDivs, null);
        }

        /**
         * @param nGroup グループ番号
         * @param nNext  次のグループ番号（任意）
         * @param nTrack トラック番号
         * @param nSide  サイド番号
         * @param nStart グループ内の開始セクタ番号
         * @param nUser  機種依存データ
         *               追加
         */
        public void add(int nGroup, int nNext, int nTrack, int nSide, int nStart, DiskBasicGroupUserData nUser) {
            items.add(new DiskBasicGroupItem(nGroup, nNext, nTrack, nSide, nStart, nUser));
        }

        /**
         * @param nItem アイテム
         *              追加
         */
        public void add(DiskBasicGroupItem nItem) {
            items.add(new DiskBasicGroupItem(nItem)); // Add a copy to maintain ownership semantics
        }

        /**
         * @param nItems アイテムリスト
         *               追加
         */
        public void add(DiskBasicGroups nItems) {
            for (int i = 0; i < nItems.size(); i++) {
                items.add(new DiskBasicGroupItem(nItems.get(i)));
            }
            nums += nItems.nums;
            size += nItems.size;
        }

        /**
         * リストをクリア
         */
        public void clear() {
            items.clear();
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        /**
         * リストの数を返す
         */
        public int size() {
            return items.size();
        }

        /**
         * リストの最後を返す
         */
        public DiskBasicGroupItem last() {
            return items.getLast();
        }

        /**
         * リストアイテムを返す
         */
        public DiskBasicGroupItem get(int idx) {
            return items.get(idx);
        }

        /**
         * リストを返す
         */
        public final List<DiskBasicGroupItem> getItems() {
            return items;
        }

        /**
         * グループ数を返す
         */
        public int getNums() {
            return nums;
        }

        /**
         * 占有サイズを返す
         */
        public int getSize() {
            return size;
        }

        /**
         * １グループのサイズを返す
         */
        public int getSizePerGroup() {
            return sizePerGroup;
        }

        /**
         * グループ数を設定
         */
        public void setNums(int val) {
            nums = val;
        }

        /**
         * 占有サイズを設定
         */
        public void setSize(int val) {
            size = val;
        }

        /**
         * １グループのサイズを設定
         */
        public void setSizePerGroup(int val) {
            sizePerGroup = val;
        }

        /**
         * グループ数を足す
         */
        public int addNums(int val) {
            nums += val;
            return nums;
        }

        /**
         * 占有サイズを足す
         */
        public int addSize(int val) {
            size += val;
            return size;
        }

        /**
         * グループ番号でソート
         */
        public void sortItems() {
            items.sort(DiskBasicGroupItem::compare);
        }
    }

    /**
     * 汎用リスト用アイテム
     *
     * @see KeyValArray
     */
    public static class KeyValItem {

        public enum Type {
            TYPE_UNKNOWN(0),
            TYPE_INTEGER(1),
            TYPE_UINT8(2),
            TYPE_UINT16(3),
            TYPE_UINT32(4),
            TYPE_STRING(5),
            TYPE_BOOL(6);

            private final int value;

            Type(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        private String mKey;
        private byte[] mValue;
        private int mSize; // int
        private Type mType;

        public KeyValItem() {
            mValue = null;
            mSize = 0;
            mType = Type.TYPE_UNKNOWN;
        }

        public KeyValItem(String key, int val) {
            set(key, val);
        }

        public KeyValItem(String key, byte val, boolean invert) {
            set(key, val, invert);
        }

        public KeyValItem(String key, short val, boolean bigEndian, boolean invert) {
            set(key, val, bigEndian, invert);
        }

        public KeyValItem(String key, int val, boolean bigEndian, boolean invert) {
            set(key, val, bigEndian, invert);
        }

        public KeyValItem(String key, Object val, int size, boolean invert) {
            if (val instanceof byte[]) {
                set(key, (byte[]) val, size, invert);
            } else {
                // Placeholder for other pointer types, assuming byte[] for raw memory
                // For now, only byte[] is supported for the void* overload
                throw new IllegalArgumentException("Unsupported type for raw data set.");
            }
        }

        public KeyValItem(String key, boolean val) {
            set(key, val);
        }

        // No destructor needed in Java. Garbage collector handles mValue.

        public void clear() {
            mValue = null;
            mSize = 0;
            mType = Type.TYPE_UNKNOWN;
        }

        /**
         * 設定 integer
         *
         * @param key キー名
         * @param val 値
         */
        public void set(String key, int val) {
            clear();
            mKey = key;
            mValue = new byte[Integer.BYTES];
            ByteUtil.writeLeInt(val, mValue, 0);
            mSize = Integer.BYTES;
            mType = Type.TYPE_INTEGER;
        }

        /**
         * 設定 8bit
         *
         * @param key    キー名
         * @param val    値 (byte)
         * @param invert 値を反転するか
         */
        public void set(String key, byte val, boolean invert) {
            clear();
            mKey = key;
            mValue = new byte[Byte.BYTES];
            mValue[0] = val;
            mSize = Byte.BYTES;
            mType = Type.TYPE_UINT8;
            if (invert) Common.mem_invert(mValue, mSize);
        }

        /**
         * 設定 16bit
         *
         * @param key       キー名
         * @param val       値 (wxUint16)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void set(String key, short val, boolean bigEndian, boolean invert) {
            clear();
            mKey = key;
            mValue = new byte[Short.BYTES];
            if (bigEndian)
                ByteUtil.writeBeShort(val, mValue, 0);
            else
                ByteUtil.writeLeShort(val, mValue, 0);
            mSize = Short.BYTES;
            mType = Type.TYPE_UINT16;
            if (invert) Common.mem_invert(mValue, mSize);
        }

        /**
         * 設定 32bit
         *
         * @param key       キー名
         * @param val       値 (int)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void set(String key, int val, boolean bigEndian, boolean invert) {
            clear();
            mKey = key;
            mValue = new byte[Integer.BYTES];
            if (bigEndian)
                ByteUtil.writeBeInt(val, mValue, 0);
            else
                ByteUtil.writeLeInt(val, mValue, 0);
            mSize = Integer.BYTES;
            mType = Type.TYPE_UINT32;
            if (invert) Common.mem_invert(mValue, mSize);
        }

        /**
         * 設定 byte array
         *
         * @param key    キー名
         * @param val    バイト配列 (final void*)
         * @param size   配列サイズ
         * @param invert 値を反転するか
         */
        public void set(String key, byte[] val, int size, boolean invert) {
            clear();
            mKey = key;
            mValue = new byte[size + 1]; // +1 for C-style string termination, though not used in the logic
            System.arraycopy(val, 0, mValue, 0, size);
            mValue[size] = 0; // Null terminator
            mSize = size;
            mType = Type.TYPE_STRING;
            if (invert) Common.mem_invert(mValue, mSize);
        }

        /**
         * 設定 bool
         *
         * @param key キー名
         * @param val 値
         */
        public void set(String key, boolean val) {
            clear();
            mKey = key;
            mValue = new byte[1];
            mValue[0] = val ? (byte) 1 : (byte) 0;
            mSize = 1;
            mType = Type.TYPE_BOOL;
        }

        /**
         * 値を文字列にして返す
         */
        public String getValueString() {
            if (mValue == null) return "";

            switch (mType) {
                case TYPE_INTEGER:
                    // Reading back the integer (assuming little endian as per C++ implicit behavior if not big endian)
                    int intVal = ((mValue[3] & 0xFF) << 24) | ((mValue[2] & 0xFF) << 16) | ((mValue[1] & 0xFF) << 8) | (mValue[0] & 0xFF);
                    return String.format("%d", intVal);
                case TYPE_UINT8:
                    return String.format("0x%02x", mValue[0] & 0xFF);
                case TYPE_UINT16:
                    // Reading back the short (assuming little endian)
                    short shortVal = (short) (((mValue[1] & 0xFF) << 8) | (mValue[0] & 0xFF));
                    return String.format("0x%04x", shortVal & 0xFFFF);
                case TYPE_UINT32:
                    // Reading back the int (assuming little endian)
                    int uint32Val = ((mValue[3] & 0xFF) << 24) | ((mValue[2] & 0xFF) << 16) | ((mValue[1] & 0xFF) << 8) | (mValue[0] & 0xFF);
                    return String.format("0x%08x", uint32Val); // Format as 8 hex digits
                case TYPE_STRING:
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mSize; i++) {
                        if (i > 0) sb.append(" ");
                        sb.append(String.format("%02x", mValue[i] & 0xFF));
                    }
                    return sb.toString();
                case TYPE_BOOL:
                    return mValue[0] != 0 ? "true" : "false";
                default:
                    return "";
            }
        }

        public final String getKey() {
            return mKey;
        }

        /**
         * キー名の比較
         */
        public static int compare(KeyValItem item1, KeyValItem item2) {
            return item1.mKey.compareTo(item2.mKey);
        }

        /**
         * Comparator for sorting a list/array of KeyValItem
         */
        public static class KeyComparator implements Comparator<KeyValItem> {

            @Override
            public int compare(KeyValItem item1, KeyValItem item2) {
                return KeyValItem.compare(item1, item2);
            }
        }
    }

    /**
     * 汎用リスト KeyValItem の配列
     */
    public static class KeyValArray {

        List<KeyValItem> contents = new ArrayList<>();

        public KeyValArray() {
            super();
        }

        /**
         * リストをクリア (and delete all elements in C++ semantics)
         */
        public void clear() {
            contents.clear();
        }

        /**
         * リストをクリア (and delete all elements in C++ semantics)
         */
        public void empty() {
            contents.clear();
        }

        /**
         * 追加 integer
         *
         * @param key キー名
         * @param val 値
         */
        public void add(String key, int val) {
            contents.add(new KeyValItem(key, val));
        }

        /**
         * 追加 8bit
         *
         * @param key    キー名
         * @param val    値 (byte)
         * @param invert 値を反転するか
         */
        public void add(String key, byte val, boolean invert) {
            contents.add(new KeyValItem(key, val, invert));
        }

        /**
         * 追加 16bit
         *
         * @param key       キー名
         * @param val       値 (wxUint16)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void add(String key, short val, boolean bigEndian, boolean invert) {
            contents.add(new KeyValItem(key, val, bigEndian, invert));
        }

        /**
         * 追加 32bit
         *
         * @param key       キー名
         * @param val       値 (int)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void add(String key, int val, boolean bigEndian, boolean invert) {
            contents.add(new KeyValItem(key, val, bigEndian, invert));
        }

        /**
         * 追加 byte array
         *
         * @param key    キー名
         * @param val    バイト配列 (final void*)
         * @param size   配列サイズ
         * @param invert 値を反転するか
         */
        public void add(String key, byte[] val, int size, boolean invert) {
            contents.add(new KeyValItem(key, val, size, invert));
        }

        public void add(String key, byte[] val, int size) {
            add(key, val, size, false);
        }

        /**
         * 追加 bool
         *
         * @param key キー名
         * @param val 値
         */
        public void add(String key, boolean val) {
            contents.add(new KeyValItem(key, val));
        }
    }
}
