/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;


public class BasicCommon {

    static class CommonUtil {

        // Placeholder for C++'s mem_invert.
        // In Java, byte is signed, so 0xFF is necessary for unsigned operations.
        public static void memInvert(byte[] data, int size) {
            for (int i = 0; i < size; i++) {
                data[i] = (byte) (~data[i]);
            }
        }

        // Helper to write a short (wxUint16) into a byte array with specified endianness
        public static void writeUint16(byte[] dest, short val, boolean bigEndian) {
            if (bigEndian) {
                dest[0] = (byte) (val >> 8);
                dest[1] = (byte) (val & 0xff);
            } else {
                dest[0] = (byte) (val & 0xff);
                dest[1] = (byte) (val >> 8);
            }
        }

        // Helper to write an int (int) into a byte array with specified endianness
        public static void writeUint32(byte[] dest, int val, boolean bigEndian) {
            if (bigEndian) {
                dest[0] = (byte) (val >> 24);
                dest[1] = (byte) (val >> 16);
                dest[2] = (byte) (val >> 8);
                dest[3] = (byte) (val & 0xff);
            } else {
                dest[0] = (byte) (val & 0xff);
                dest[1] = (byte) (val >> 8);
                dest[2] = (byte) (val >> 16);
                dest[3] = (byte) (val >> 24);
            }
        }
    }

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
        FILE_TYPE_SOFTLINK_MASK(0x200000),

        // Calculated field, not a constant in the enum itself in Java style
        // FILE_TYPE_EXTENSION_MASK = FILE_TYPE_BASIC_MASK | ... | FILE_TYPE_INTEGER_MASK
        ;

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
     * 共通属性フラグ位置
     */
    enum FileTypePos {
        FILE_TYPE_BASIC_POS(0),
        FILE_TYPE_DATA_POS(1),
        FILE_TYPE_MACHINE_POS(2),
        FILE_TYPE_ASCII_POS(3),
        FILE_TYPE_BINARY_POS(4),
        FILE_TYPE_RANDOM_POS(5),
        FILE_TYPE_ENCRYPTED_POS(6),
        FILE_TYPE_READWRITE_POS(7),
        FILE_TYPE_READONLY_POS(8),
        FILE_TYPE_HIDDEN_POS(9),
        FILE_TYPE_SYSTEM_POS(10),
        FILE_TYPE_VOLUME_POS(11),
        FILE_TYPE_DIRECTORY_POS(12),
        FILE_TYPE_ARCHIVE_POS(13),
        FILE_TYPE_LIBRARY_POS(14),
        FILE_TYPE_NONSHARE_POS(15),
        FILE_TYPE_UNDELETE_POS(16),
        FILE_TYPE_WRITEONLY_POS(17),
        FILE_TYPE_TEMPORARY_POS(18),
        FILE_TYPE_INTEGER_POS(19),
        FILE_TYPE_HARDLINK_POS(20),
        FILE_TYPE_SOFTLINK_POS(21);

        private final int value;

        FileTypePos(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * ディレクトリエントリ L3,S1 ５インチ,８インチ(倍密度)
     */
    public static class DirectoryL32d implements DirectoryT {

        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte type; // byte
        public byte type2; // byte
        public byte startGroup; // byte
        public short endBytes; // wxUint16 (big endien) - unsigned 16-bit is best represented by a Java 'int' if arithmetic is needed, but 'short' for storage.

        public byte[] reserved = new byte[16]; // char reserved[16]
    }

    /**
     * ディレクトリエントリ L3 ３インチ(単密度) / F-BASIC 倍密度
     */
    public static class DirectoryFat8f implements DirectoryT {

        public byte[] name = new byte[8];
        public byte[] ext = new byte[3]; // not used.
        public byte type; // byte
        public byte type2; // byte
        public byte type3; // byte
        public byte startGroup; // byte

        public byte[] reserved = new byte[17];
    }

    /**
     * ディレクトリエントリ n88 BASIC (16bytes)
     */
    public static class DirectoryN88 implements DirectoryT {

        public byte[] name = new byte[6];
        public byte[] ext = new byte[3];
        public byte type; // byte
        public byte startGroup; // byte
        public byte[] reserved = new byte[5];
    }

    /**
     * ディレクトリエントリ X1 Hu-BASIC (32bytes)
     */
    public static class DirectoryX1Hu implements DirectoryT{

        public byte type; // byte
        public byte[] name = new byte[13];
        public byte[] ext = new byte[3];
        public byte password; // byte
        public short fileSize; // wxUint16
        public short loadAddr; // wxUint16
        public short execAddr; // wxUint16
        public byte[] date = new byte[3]; // yymwdd yy:BCD 00-99 m:HEX 0-C w:WEEK HEX 0(SUN)-7(SAT) dd:BCD
        public byte[] time = new byte[2]; // hhmi BCD
        public byte startGroupH; // byte
        public short startGroupL; // wxUint16
    }

    /**
     * ディレクトリエントリ MZ DISK BASIC (32bytes)
     */
    public static class DirectoryMz implements DirectoryT {

        public byte type; // byte
        public byte[] name = new byte[17]; // file name has $0D on the end of string
        public byte type2; // byte
        public byte reserved; // byte
        public short fileSize; // wxUint16
        public short loadAddr; // wxUint16
        public short execAddr; // wxUint16
        public byte[] dateTime = new byte[4];
        public short startSector; // wxUint16
    }

    /**
     * ディレクトリエントリ MS-DOS FAT (32bytes)
     */
    public static class DirectoryMsDos implements DirectoryT {

        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte type; // byte
        public byte ntres; // byte
        public byte ctimeTenth; // byte
        public short ctime; // wxUint16
        public short cdate; // wxUint16
        public short adate; // wxUint16
        public short startGroupHi; // wxUint16
        public short wtime; // wxUint16
        public short wdate; // wxUint16
        public short startGroup; // wxUint16
        public int fileSize; // int
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
    }

    /**
     * FLEX top of each sector
     */
    public static class FlexPtr {

        public byte nextTrack; // byte
        public byte nextSector; // byte
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
    }

    /**
     * OS-9 File Descriptor
     */
    public static class DirectoryOs9Fd implements DirectoryT {

        public byte fdAtt; // byte attr
        public short fdOwn; // wxUint16 owner id
        public Os9Date fdDat = new Os9Date(); // date
        public byte fdLnk; // byte link count
        public int fdSiz; // int in bytes
        public Os9Cdate fdDcr = new Os9Cdate(); // created date
        public Os9Segment[] fdSeg = new Os9Segment[48]; // 5*48=240

        public DirectoryOs9Fd() {
            for (int i = 0; i < 48; i++) {
                fdSeg[i] = new Os9Segment();
            }
        }
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
        };
    }

    /**
     * ディレクトリエントリ TF-DOS (16bytes)
     */
    public static class DirectoryTfdos implements DirectoryT {

        public byte type; // byte
        public byte[] name = new byte[8]; // file name ends with $0D and fills rest with $20
        public short fileSize; // wxUint16
        public short loadAddr; // wxUint16
        public short execAddr; // wxUint16
        public byte track; // byte
    }

    /**
     * ディレクトリエントリ PC-8001 DOS (New PC.DOS) (16bytes)
     */
    public static class DirectoryDos80 implements DirectoryT {

        public byte[] name = new byte[16];
    }

    /**
     * PC-8001 DOS (New PC.DOS) グループエントリ
     */
    static class DirectoryDos80Grp implements DirectoryT {

        public byte g; // byte
        public short a; // wxUint16
    }

    /**
     * ディレクトリエントリ2 PC-8001 DOS (New PC.DOS) (16bytes)
     */
    public static class DirectoryDos80_2 implements DirectoryT {

        public DirectoryDos80Grp[] grps = new DirectoryDos80Grp[5];
        public byte reserved; // byte

        public DirectoryDos80_2() {
            for (int i = 0; i < 5; i++) {
                grps[i] = new DirectoryDos80Grp();
            }
        }
    }

    /**
     * ディレクトリエントリ C-DOS (32bytes)
     */
    public static class DirectoryCdos implements DirectoryT {

        public byte type; // byte
        public byte[] name = new byte[17]; // file name ends with $0D
        public byte type2; // byte
        public byte byteOrder; // byte
        public short fileSize; // wxUint16
        public short loadAddr; // wxUint16
        public short execAddr; // wxUint16
        public byte yy; // byte
        public byte mm; // byte
        public byte dd; // byte
        public byte reserved2; // byte
        public byte track; // byte
        public byte sector; // byte
    }

    /**
     * ディレクトリエントリ MZ Floppy DOS (64bytes)
     */
    public static class DirectoryMzFdos implements DirectoryT {

        public byte type; // byte
        public byte[] name = new byte[17]; // file name has $0D on the end of string
        public short fileSize; // wxUint16
        public short loadAddr; // wxUint16
        public short execAddr; // wxUint16
        public short groups; // wxUint16
        public byte[] attr = new byte[2]; // 0x30 0x53
        public byte[] password = new byte[2]; // 0x00 0x00
        public short dummySector; // 0x01 0x02

        public byte[] mmddyy = new byte[7];
        public byte[] reserved2 = new byte[13];
        public byte track; // byte
        public byte sector; // byte
        public byte[] reserved3 = new byte[5];
        public byte seqNum; // byte
        public byte unknown1; // 0x9x - 0xax
        public byte unknown2; // 0x15
        public byte dataTrack; // byte
        public byte dataSector; // byte
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

        public byte type; // byte
        public byte[] name = new byte[31];
        public byte type2; // byte
        public short loadAddr; // wxUint16
        public short fileSize; // wxUint16
        public short execAddr; // wxUint16
        public byte[] date = new byte[2];
        public byte[] time = new byte[2];
        public byte[] reserved = new byte[2];
        public MagicalSeg start = new MagicalSeg();
    }

    /**
     * ディレクトリエントリ S-DOS (32bytes)
     */
    public static class DirectorySdos implements DirectoryT {

        public byte[] name = new byte[22];
        public byte type; // byte
        public byte track; // byte
        public byte sector; // byte
        public byte size; // byte number of sector
        public byte restSize; // byte
        public short loadAddr; // wxUint16
        public short execAddr; // wxUint16
        public byte reserved; // byte
    }

    /**
     * ディレクトリエントリ C82-BASIC (32bytes)
     */
    public static class DirectoryFp implements DirectoryT {

        public byte type; // byte
        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public byte term; // byte
        public byte[] unknown = new byte[7];
        public short loadAddr; // wxUint16
        public short endAddr; // wxUint16
        public short execAddr; // wxUint16
        public short fileSize; // wxUint16
        public byte startGroup; // byte
        public byte attr; // byte
        public byte[] reserved = new byte[2];
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

        public AmigaRootBlockPost r = new AmigaRootBlockPost();
        public AmigaHeaderPost h = new AmigaHeaderPost();
    }

    /**
     * ディレクトリエントリ Amiga DOS
     * <p>
     * AmigaDOSは1セクタ分になるのでブロック番号だけを保持
     */
    public static class DirectoryAmiga implements DirectoryT {

        public int blockNum; // int
        public AmigaBlockPre pre; // pointer
        public AmigaBlockPost post; // pointer
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
     * 名前と値 定数リスト用
     */
    static class NameValue {

        /**
         * @param list リスト(NULL終り)
         * @param str  文字列
         * @return 一致する位置 or -1
         * 名前が一致するか
         */
        public static int indexOf(final Map<String, Object> list, final String str) {
            int match = -1;
            int i = 0;
            for (Map.Entry<String, Object> e : list.entrySet()) {
                if (str.equals(e.getKey())) {
                    match = i;
                    break;
                }
                i++;
            }
            return match;
        }

        /**
         * @param list リスト(NULL終り)
         * @param val  値
         * @return 一致する位置 or -1
         * 値が一致するか
         */
        public static int indexOf(final Map<String, Object> list, int val) {
            int match = -1;
            int i = 0;
            for (Map.Entry<String, Object> e : list.entrySet()) {
                if (val == (int) e.getValue()) {
                    match = i;
                    break;
                }
                i++;
            }
            return match;
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

        public DiskBasicFileName(final String nName, int nOptional) {
            this.name = nName;
            this.optional = nOptional;
        }

        // No destructor needed in Java

        /**
         * ファイル名
         */
        public final String getName() {
            return name;
        }

        /**
         * ファイル名
         */
        public void setName(final String val) {
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
        private int[] origin = new int[3];

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
     * @sa DiskBasicGroupItem
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
     * @sa DiskBasicGroups
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

        public DiskBasicGroupItem(final DiskBasicGroupItem src) {
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
        public DiskBasicGroupItem set(final DiskBasicGroupItem src) {
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

        /**
         * Comparator for sorting a list/array of DiskBasicGroupItem
         */
        public static class GroupComparator implements Comparator<DiskBasicGroupItem> {

            @Override
            public int compare(DiskBasicGroupItem item1, DiskBasicGroupItem item2) {
                return DiskBasicGroupItem.compare(item1, item2);
            }
        }
    }

    // Equivalent of WX_DECLARE_OBJARRAY(DiskBasicGroupItem, DiskBasicGroupItems);
    // Use a standard Java List<DiskBasicGroupItem> or ArrayList<DiskBasicGroupItem>
    public static class DiskBasicGroupItems extends ArrayList<DiskBasicGroupItem> {
        // Standard list functionality is inherited.
        // The C++ macro also defines methods like Item() and Last(), which we can implement
        // or rely on the standard Java List methods (get() and size()-1).

        public DiskBasicGroupItem last() {
            if (isEmpty()) throw new java.util.NoSuchElementException();
            return get(size() - 1);
        }

        public DiskBasicGroupItem item(int idx) {
            return get(idx);
        }

        // In C++, this might be a pointer to the element:
        public DiskBasicGroupItem itemPtr(int idx) {
            return get(idx);
        }
    }

    /**
     * グループ番号のリストを保持
     * <p>
     * ディスク内ファイルのチェインをこのリストに保持する
     * @sa DiskBasicGroupItem , DiskBasicDirItem
     */
    public static class DiskBasicGroups {

        /** グループ番号のリスト */
        private DiskBasicGroupItems items;
        /** グループ数 */
        private int nums;
        /** グループ内の占有サイズ (int) */
        private int size;
        /** １グループのサイズ (int) */
        private int sizePerGroup;

        public DiskBasicGroups() {
            items = new DiskBasicGroupItems();
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        public DiskBasicGroups(DiskBasicGroupItems items) {
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
         * 追加
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
         * 追加
         */
        public void add(int nGroup, int nNext, int nTrack, int nSide, int nStart, DiskBasicGroupUserData nUser) {
            items.add(new DiskBasicGroupItem(nGroup, nNext, nTrack, nSide, nStart, nUser));
        }

        /**
         * @param nItem アイテム
         * 追加
         */
        public void add(final DiskBasicGroupItem nItem) {
            items.add(new DiskBasicGroupItem(nItem)); // Add a copy to maintain ownership semantics
        }

        /**
         * @param nItems アイテムリスト
         * 追加
         */
        public void add(final DiskBasicGroups nItems) {
            for (int i = 0; i < nItems.count(); i++) {
                items.add(new DiskBasicGroupItem(nItems.item(i)));
            }
            nums += nItems.nums;
            size += nItems.size;
        }

        /**
         * リストをクリア
         */
        public void empty() {
            items.clear();
            nums = 0;
            size = 0;
            sizePerGroup = 0;
        }

        /**
         * リストの数を返す
         */
        public int count() {
            return items.size();
        }

        /**
         * リストの最後を返す
         */
        public DiskBasicGroupItem last() {
            return items.last();
        }

        /**
         * リストアイテムを返す
         */
        public DiskBasicGroupItem item(int idx) {
            return items.get(idx);
        }

        /**
         * リストアイテムを返す
         * C++ version returns a pointer, Java returns a reference (the object itself).
         */
        public DiskBasicGroupItem itemPtr(int idx) {
            return items.get(idx);
        }

        /**
         * リストを返す
         */
        public final DiskBasicGroupItems getItems() {
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
            return (int) size;
        }

        /**
         * グループ番号でソート
         */
        public void sortItems() {
            items.sort(new DiskBasicGroupItem.GroupComparator());
        }
    }

    /**
     * 汎用リスト用アイテム
     * @sa KeyValArray
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

        public KeyValItem(final String key, int val) {
            set(key, val);
        }

        public KeyValItem(final String key, byte val, boolean invert) {
            set(key, val, invert);
        }

        public KeyValItem(final String key, short val, boolean bigEndian, boolean invert) {
            set(key, val, bigEndian, invert);
        }

        public KeyValItem(final String key, int val, boolean bigEndian, boolean invert) {
            set(key, val, bigEndian, invert);
        }

        public KeyValItem(final String key, final Object val, int size, boolean invert) {
            if (val instanceof byte[]) {
                set(key, (byte[]) val, size, invert);
            } else {
                // Placeholder for other pointer types, assuming byte[] for raw memory
                // For now, only byte[] is supported for the void* overload
                throw new IllegalArgumentException("Unsupported type for raw data set.");
            }
        }

        public KeyValItem(final String key, boolean val) {
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
        public void set(final String key, int val) {
            clear();
            mKey = key;
            mValue = new byte[Integer.BYTES];
            // Little endian by default for Java int in byte array (if not specified)
            CommonUtil.writeUint32(mValue, val, false);
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
        public void set(final String key, byte val, boolean invert) {
            clear();
            mKey = key;
            mValue = new byte[Byte.BYTES];
            mValue[0] = val;
            mSize = Byte.BYTES;
            mType = Type.TYPE_UINT8;
            if (invert) CommonUtil.memInvert(mValue, (int) mSize);
        }

        /**
         * 設定 16bit
         *
         * @param key       キー名
         * @param val       値 (wxUint16)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void set(final String key, short val, boolean bigEndian, boolean invert) {
            clear();
            mKey = key;
            mValue = new byte[Short.BYTES];
            CommonUtil.writeUint16(mValue, val, bigEndian);
            mSize = Short.BYTES;
            mType = Type.TYPE_UINT16;
            if (invert) CommonUtil.memInvert(mValue, (int) mSize);
        }

        /**
         * 設定 32bit
         *
         * @param key       キー名
         * @param val       値 (int)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void set(final String key, int val, boolean bigEndian, boolean invert) {
            clear();
            mKey = key;
            mValue = new byte[Integer.BYTES];
            CommonUtil.writeUint32(mValue, val, bigEndian);
            mSize = Integer.BYTES;
            mType = Type.TYPE_UINT32;
            if (invert) CommonUtil.memInvert(mValue, (int) mSize);
        }

        /**
         * 設定 byte array
         *
         * @param key    キー名
         * @param val    バイト配列 (final void*)
         * @param size   配列サイズ
         * @param invert 値を反転するか
         */
        public void set(final String key, final byte[] val, int size, boolean invert) {
            clear();
            mKey = key;
            mValue = new byte[(int) size + 1]; // +1 for C-style string termination, though not used in the logic
            System.arraycopy(val, 0, mValue, 0, (int) size);
            mValue[(int) size] = 0; // Null terminator
            mSize = size;
            mType = Type.TYPE_STRING;
            if (invert) CommonUtil.memInvert(mValue, (int) mSize);
        }

        /**
         * 設定 bool
         *
         * @param key キー名
         * @param val 値
         */
        public void set(final String key, boolean val) {
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
                    return String.format("0x%08x", (int) uint32Val); // Format as 8 hex digits
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

    // WX_DEFINE_ARRAY(KeyValItem *, ArrayOfKeyValItem);
    // Use a standard Java List<KeyValItem> or ArrayList<KeyValItem>
    public static class ArrayOfKeyValItem extends ArrayList<KeyValItem> {

    }

    /**
     * 汎用リスト KeyValItem の配列
     */
    public static class KeyValArray extends ArrayOfKeyValItem {

        public KeyValArray() {
            super();
        }

        // No destructor needed in Java.

        private void deleteAll() {
            // In Java, this just clears the references in the list and relies on GC.
            // The KeyValItem objects themselves don't need explicit 'delete' if they manage their own resources (mValue) correctly.
            // Since KeyValItem's constructor allocates mValue, and it should be freed/null'd in clear, we just rely on clear and then ArrayList.clear.
        }

        /**
         * リストをクリア (and delete all elements in C++ semantics)
         */
        @Override
        public void clear() {
            deleteAll();
            super.clear();
        }

        /**
         * リストをクリア (and delete all elements in C++ semantics)
         */
        public void empty() {
            deleteAll();
            super.clear(); // ArrayList's clear() performs the same function as Empty() in wxWidgets/C++ world
        }

        /**
         * 追加 integer
         *
         * @param key キー名
         * @param val 値
         */
        public void add(final String key, int val) {
            super.add(new KeyValItem(key, val));
        }

        /**
         * 追加 8bit
         *
         * @param key    キー名
         * @param val    値 (byte)
         * @param invert 値を反転するか
         */
        public void add(final String key, byte val, boolean invert) {
            super.add(new KeyValItem(key, val, invert));
        }

        /**
         * 追加 16bit
         *
         * @param key       キー名
         * @param val       値 (wxUint16)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void add(final String key, short val, boolean bigEndian, boolean invert) {
            super.add(new KeyValItem(key, val, bigEndian, invert));
        }

        /**
         * 追加 32bit
         *
         * @param key       キー名
         * @param val       値 (int)
         * @param bigEndian 値がビッグエンディアンか
         * @param invert    値を反転するか
         */
        public void add(final String key, int val, boolean bigEndian, boolean invert) {
            super.add(new KeyValItem(key, val, bigEndian, invert));
        }

        /**
         * 追加 byte array
         *
         * @param key    キー名
         * @param val    バイト配列 (final void*)
         * @param size   配列サイズ
         * @param invert 値を反転するか
         */
        public void add(final String key, final byte[] val, int size, boolean invert) {
            super.add(new KeyValItem(key, val, size, invert));
        }

        public void add(final String key, final byte[] val, int size) {
            add(key, val, size, false);
        }

        /**
         * 追加 bool
         *
         * @param key キー名
         * @param val 値
         */
        public void add(final String key, boolean val) {
            super.add(new KeyValItem(key, val));
        }
    }
}
