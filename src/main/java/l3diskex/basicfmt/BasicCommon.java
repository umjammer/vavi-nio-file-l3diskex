/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

import l3diskex.Common;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAmiga.AmigaChain.Pointer;
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

        @Override
        public String toString() {
            return new StringJoiner(", ", DirectoryN88.class.getSimpleName() + "[", "]")
                    .add("name=" + new String(name) + "." + new String(ext))
                    .add("type=" + type)
                    .add("startGroup=" + startGroup)
                    .add("reserved=" + Arrays.toString(reserved))
                    .toString();
        }
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
    @Serdes(bigEndian = false)
    public static class DirectoryMsDos implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte ntres;
        @Element(sequence = 5)
        public byte ctimeTenth;
        @Element(sequence = 6)
        public short ctime;
        @Element(sequence = 7)
        public short cdate;
        @Element(sequence = 8)
        public short adate;
        @Element(sequence = 9)
        public short startGroupHi;
        @Element(sequence = 10)
        public short wtime;
        @Element(sequence = 11)
        public short wdate;
        @Element(sequence = 12)
        public short startGroup;
        @Element(sequence = 13)
        public int fileSize;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MS-DOS LFN (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMsLfn implements DirectoryT {

        @Element(sequence = 1)
        public byte order;
        @Element(sequence = 2)
        public byte[] name = new byte[10];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte type2;
        @Element(sequence = 5)
        public byte chksum;
        @Element(sequence = 6)
        public byte[] name2 = new byte[12];
        @Element(sequence = 7)
        public short dummyGroup;
        @Element(sequence = 8)
        public byte[] name3 = new byte[4];

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ Human68K (MS-DOS compatible) (32bytes)
     */
    @Serdes
    public static class DirectoryHu68k implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte[] name2 = new byte[10];
        @Element(sequence = 5)
        public short wtime;
        @Element(sequence = 6)
        public short wdate;
        @Element(sequence = 7)
        public short startGroup;
        @Element(sequence = 8)
        public int fileSize;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ L-os Angeles (MS-DOS compatible) (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryLosa implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 1)
        public byte[] ext = new byte[3];
        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 1)
        public byte[] startAddr = new byte[4];
        @Element(sequence = 1)
        public byte binaryType;
        @Element(sequence = 1)
        public byte[] execAddr = new byte[4];
        @Element(sequence = 1)
        public byte reserved;
        @Element(sequence = 1)
        public short wtime;
        @Element(sequence = 1)
        public short wdate;
        @Element(sequence = 1)
        public short startGroup;
        @Element(sequence = 1)
        public int fileSize;

        public static final int SIZE = 32;
    }

    /**
     * ディレクトリエントリ MS-DOS compatible (32bytes)
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
    @Serdes(bigEndian = false)
    public static class DirectoryFlex implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type; // byte
        @Element(sequence = 4)
        public byte reserved; // byte
        @Element(sequence = 5)
        public byte startTrack; // byte
        @Element(sequence = 6)
        public byte startSector; // byte
        @Element(sequence = 7)
        public byte lastTrack; // byte
        @Element(sequence = 8)
        public byte lastSector; // byte
        @Element(sequence = 9)
        public short totalSectors; // wxUint16
        @Element(sequence = 10)
        public byte randomAccess; // byte
        @Element(sequence = 11)
        public byte reserved2; // byte
        @Element(sequence = 12)
        public byte month; // byte
        @Element(sequence = 13)
        public byte day; // byte
        @Element(sequence = 14)
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
    @Serdes
    public static class Os9Lsn {

        @Element(sequence = 1)
        public byte h; // byte
        @Element(sequence = 2)
        public byte m; // byte
        @Element(sequence = 3)
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
    @Serdes
    public static class Os9Segment {

        @Element(sequence = 1)
        public Os9Lsn lsn = new Os9Lsn();
        @Element(sequence = 2)
        public short siz;
    }

    /**
     * OS-9 Date Format
     */
    @Serdes
    public static class Os9Date {

        @Element(sequence = 1)
        public byte yy;
        @Element(sequence = 2)
        public byte mm;
        @Element(sequence = 3)
        public byte dd;
        @Element(sequence = 4)
        public byte hh;
        @Element(sequence = 5)
        public byte mi;

        public Os9Cdate toCdate() {
            Os9Cdate cdate = new Os9Cdate();
            cdate.yy = this.yy;
            cdate.mm = this.mm;
            cdate.dd = this.dd;
            return cdate;
        }
    }

    /**
     * OS-9 Created Date
     */
    @Serdes
    public static class Os9Cdate {

        @Element(sequence = 1)
        public byte yy; // byte
        @Element(sequence = 2)
        public byte mm; // byte
        @Element(sequence = 3)
        public byte dd; // byte
    }

    /**
     * ディレクトリエントリ OS-9 (32bytes)
     */
    @Serdes
    public static class DirectoryOs9 implements DirectoryT {

        @Element(sequence = 1)
        public byte[] deNam = new byte[28];
        @Element(sequence = 2)
        public byte deReserved; // byte
        @Element(sequence = 3)
        public Os9Lsn deLsn = new Os9Lsn(); // link to FD

        public static final int SIZE = 32;
    }

    /**
     * OS-9 File Descriptor
     */
    @Serdes
    public static class DirectoryOs9Fd implements DirectoryT {

        @Element(sequence = 1)
        public byte fdAtt; // 1 attr
        @Element(sequence = 2)
        public short fdOwn; // 2 owner id
        @Element(sequence = 3)
        public Os9Date fdDat = new Os9Date(); // 5 date
        @Element(sequence = 4)
        public byte fdLnk; // 1 link count
        @Element(sequence = 5)
        public int fdSiz; // 4 in bytes
        @Element(sequence = 6)
        public Os9Cdate fdDcr = new Os9Cdate(); // 3 created date
        @Element(sequence = 7)
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
    @Serdes(bigEndian = false)
    public static class DirectoryCpm implements DirectoryT, Cloneable {

        @Element(sequence = 1)
        public byte type; // byte user id
        @Element(sequence = 2)
        public byte[] name = new byte[8];
        @Element(sequence = 3)
        public byte[] ext = new byte[3];
        @Element(sequence = 4)
        public byte extentNum; // byte
        @Element(sequence = 5)
        public byte[] reserved = new byte[2];
        @Element(sequence = 6)
        public byte recordNum; // byte

        // This union is complex. In Java, we'll store the bytes and provide accessors if needed.
        @Element(sequence = 7)
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
    @Serdes(bigEndian = false)
    public static class DirectoryDos80Grp {

        @Element(sequence = 1)
        public byte g; // byte
        @Element(sequence = 2)
        public short a; // wxUint16
    }

    /**
     * ディレクトリエントリ2 PC-8001 DOS (New PC.DOS) (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryDos80_2 implements DirectoryT {

        @Element(sequence = 1)
        public DirectoryDos80Grp[] grps = new DirectoryDos80Grp[5]; // 3 x 5
        @Element(sequence = 2)
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
    @Serdes(bigEndian = false)
    public static class DirectoryFrost implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[6];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte track;
        @Element(sequence = 5)
        public byte sector;
        @Element(sequence = 6)
        public short loadAddr;
        @Element(sequence = 7)
        public short size;

        public static final int SIZE = 16;
    }

    /**
     * X-DOSセグメント情報
     */
    @Serdes(bigEndian = false)
    public static class XdosSeg {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte size;
    }

    /**
     * ディレクトリエントリ X-DOS X1 (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryXdos implements DirectoryT {

        @Element(sequence = 1)
        public short ftype; // big endien
        @Element(sequence = 1)
        public byte[] name = new byte[16];
        @Element(sequence = 1)
        public short loadAddr;
        @Element(sequence = 1)
        public short fileSize;
        @Element(sequence = 1)
        public short execAddr;
        @Element(sequence = 1)
        public short date;
        @Element(sequence = 1)
        public short time;
        @Element(sequence = 1)
        public byte attr; // アトリビュート
        @Element(sequence = 1)
        public XdosSeg start = new XdosSeg();

        public static final int SIZE = 32;
    }

    /**
     * Magical DOS セグメント情報
     */
    @Serdes
    public static class MagicalSeg {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte size;
    }

    /**
     * ディレクトリエントリ Magical DOS
     */
    public static class DirectoryMagical extends DirectoryXdos {

        @Element(sequence = 1)
        public byte type; // 1
        @Element(sequence = 2)
        public byte[] name = new byte[31];
        @Element(sequence = 3)
        public byte type2; // 1
        @Element(sequence = 4)
        public short loadAddr; // 2
        @Element(sequence = 5)
        public short fileSize; // 2
        @Element(sequence = 6)
        public short execAddr; // 2
        @Element(sequence = 7)
        public byte[] date = new byte[2];
        @Element(sequence = 8)
        public byte[] time = new byte[2];
        @Element(sequence = 9)
        public byte[] reserved = new byte[2];
        @Element(sequence = 10)
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
    @Serdes
    public static class DirectoryMdos implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte unknown; // byte
        @Element(sequence = 4, bigEndian = "false")
        public short startGroup; // little endien
        @Element(sequence = 5)
        public short fileSize; // big endien

        public static final int SIZE = 16;
    }

    /**
     * ディレクトリエントリ Falcom (16bytes)
     */
    @Serdes
    public static class DirectoryFalcom implements DirectoryT {

        @Element(sequence = 1)
        public byte[] name = new byte[6];
        @Element(sequence = 2)
        public short execAddr;
        @Element(sequence = 3)
        public short startAddr;
        @Element(sequence = 4)
        public short endAddr;
        @Element(sequence = 5)
        public GroupPtr startGroup = new GroupPtr();
        @Element(sequence = 6)
        public GroupPtr endGroup = new GroupPtr();

        public static class GroupPtr {

            @Element(sequence = 1)
            public byte track;
            @Element(sequence = 2)
            public byte sector;

            public byte[] getBytes() {
                return new byte[0];
            }
        }

        public static final int SIZE = 16;
    }

    /**
     * ディレクトリエントリ Apple DOS (35bytes)
     */
    @Serdes
    public static class DirectoryApledos implements DirectoryT {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte[] name = new byte[30];
        @Element(sequence = 5)
        public short sectorCount; // size (little endien)

        public static final int SIZE = 35;
    }

    /**
     * Apple DOS top of each sector
     */
    @Serdes
    public static class ApledosPtr {

        @Element(sequence = 1)
        public byte reserved;
        @Element(sequence = 2)
        public byte nextTrack;
        @Element(sequence = 3)
        public byte nextSector;
    }

    /**
     * ディレクトリエントリ Apple ProDOS (39bytes)
     */
    @Serdes
    public static class DirectoryProdos implements DirectoryT {

        @Element(sequence = 1)
        public byte stypeAndNlen;
        @Element(sequence = 2)
        public byte[] name = new byte[15];
        @Element(sequence = 3)
        public byte fileType; // file only
        @Element(sequence = 4)
        public short keyPointer; // file only
        @Element(sequence = 5)
        public short blocksUsed; // file only
        @Element(sequence = 6)
        public byte[] eof = new byte[3]; // file only
        @Element(sequence = 7)
        public byte[] cdate = new byte[2];
        @Element(sequence = 8)
        public byte[] ctime = new byte[2];
        @Element(sequence = 9)
        public byte version; // byte
        @Element(sequence = 10)
        public byte minVersion; // byte
        @Element(sequence = 11)
        public byte access; // byte

        // Union for the variant part
        public ProdosAux aux = new ProdosAux();

        public static class ProdosAux {

            public V v = new V();
            public Sv sv = new Sv();
            public F f = new F();
        }

        public static class V {

            public byte entryLen;
            public byte entriesPerBlock;
            public short fileCount;
            public short bitmapPointer;
            public short totalBlocks;
        }

        public static class Sv {

            public byte entryLen;
            public byte entriesPerBlock;
            public short fileCount;
            public short parentPointer;
            public byte parentEntry;
            public byte parentEntryLen;
        }

        public static class F {

            public short auxType; // aux type
            public byte[] mdate = new byte[2];
            public byte[] mtime = new byte[2];
            public short headerPointer;
        }

        public static final int SIZE = 39;
    }

    /**
     * トラック＆セクタ Commodore 1541
     */
    @Serdes
    public static class C1541Ptr {
        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
    }

    /**
     * ディレクトリエントリ Commodore 1541 (32bytes)
     */
    @Serdes
    public static class DirectoryC1541 implements DirectoryT {

        @Element(sequence = 1)
        public byte[] doNotWrite = new byte[2]; // first entry only on each sector
        @Element(sequence = 2)
        public byte type;
        @Element(sequence = 3)
        public C1541Ptr firstData = new C1541Ptr();
        @Element(sequence = 4)
        public byte[] name = new byte[16];
        @Element(sequence = 5)
        public C1541Ptr firstSide = new C1541Ptr(); // relative file only
        @Element(sequence = 6)
        public byte recordSize; // relative file only
        @Element(sequence = 7)
        public byte[] unused = new byte[4];
        @Element(sequence = 8)
        public C1541Ptr replace = new C1541Ptr();
        @Element(sequence = 9)
        public short numOfBlocks;

        public static final int SIZE = 32;
    }

    /**
     * Amiga block structure head (all Big Endien)
     */
    @Serdes
    public static class AmigaBlockPre {

        public static final int SIZE = 4 + 4 + 4 + 4 + 4 + 4 + 4;

        /** starting block (T_HEADER:2 / T_LIST:16) */
        @Element(sequence = 1)
        public int type;
        /** self pointer (except Root) */
        @Element(sequence = 2)
        public int headerKey;
        /** number of data (File only) */
        @Element(sequence = 3)
        public int highSeq;
        /** hash table size (Root only) (72) */
        @Element(sequence = 4)
        public int tableSize;
        /** first data block pointer (File only) */
        @Element(sequence = 5)
        public int firstData;
        @Element(sequence = 6)
        public int checkSum;

        @Element(sequence = 7)
        public AmigaBlockPreUnion u = new AmigaBlockPreUnion();

        @Serdes
        public static class AmigaBlockPreUnion {

            /** block pointer (72 items) */
            public int[] table = new int[1];
            /** symbolic name (Soft link only) */
            @Element(sequence = 1)
            public byte[] symName = new byte[4];
        }
    }

    /**
     * Amiga Root Block (above hash_table)
     */
    @Serdes
    public static class AmigaRootBlockPost extends AmigaBlockPost {

        public static final int SIZE = 4 + 4 + 4 + 4 + 4 + 41 + 1 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4;

        // value is -1 if disk bitmap is valid
        @Element(sequence = 1)
        public int bmFlag;
        // blocks of disk bitmap
        @Element(sequence = 2)
        public int[] bmPages = new int[25];
        // ext blocks of disk bitmap
        @Element(sequence = 3)
        public int bmExt;
        // root dir modified date
        @Element(sequence = 4)
        public int rDays;
        // root dir modified time
        @Element(sequence = 5)
        public int rMins;
        // root dir modified seconds
        @Element(sequence = 6)
        public int rTicks;
        // in bytes
        @Element(sequence = 7)
        public byte diskNameLen;
        @Element(sequence = 8)
        public byte[] diskName = new byte[31];
        // set to 0
        @Element(sequence = 9)
        public int[] unused = new int[2];
        // disk modified date
        @Element(sequence = 10)
        public int vDays;
        // disk modified time
        @Element(sequence = 11)
        public int vMins;
        // disk modified seconds
        @Element(sequence = 12)
        public int vTicks;
        // disk creation date
        @Element(sequence = 12)
        public int cDays;
        // disk creation time
        @Element(sequence = 13)
        public int cMins;
        // disk creation seconds
        @Element(sequence = 14)
        public int cTicks;
        // always 0
        @Element(sequence = 15)
        public int nextHash;
        // always 0
        @Element(sequence = 16)
        public int parentDir;
        // always 0
        @Element(sequence = 17)
        public int extension;
        // always 1
        @Element(sequence = 18)
        public int secType;
    }

    /**
     * Amiga File / Directory Header Block (post table)
     */
    @Serdes
    public static class AmigaHeaderPost extends AmigaBlockPost {

        public static final int SIZE = 4 + 2 + 2 + 4 + 4 + 1 + 79 + 12 + 4 + 4 + 4 + 1 + 31 + 4 + 4 + 4 + 20 + 4 + 4 + 4 + 4;

        @Element(sequence = 1)
        public byte[] unused0 = new byte[4];
        // user id
        @Element(sequence = 2)
        public short uid;
        // user group
        @Element(sequence = 3)
        public short gid;
        @Element(sequence = 4)
        public int protect;
        // file size (File only)
        @Element(sequence = 5)
        public int byteSize;
        // in bytes
        @Element(sequence = 6)
        public byte commentLen;
        @Element(sequence = 7)
        public byte[] comment = new byte[79];
        @Element(sequence = 8)
        public byte[] unused1 = new byte[12];
        // modified date
        @Element(sequence = 9)
        public int days;
        // modified time
        @Element(sequence = 10)
        public int mins;
        // modified seconds
        @Element(sequence = 11)
        public int ticks;
        // in bytes
        @Element(sequence = 12)
        public byte nameLen;
        // 0 terminate
        @Element(sequence = 13)
        public byte[] name = new byte[31];
        @Element(sequence = 14)
        public byte[] unused2 = new byte[4];
        // FFS unused (File only)
        @Element(sequence = 15)
        public int realEntry;
        // FFS hardlinks chained list
        @Element(sequence = 16)
        public int nextLink;
        @Element(sequence = 17)
        public byte[] unused3 = new byte[20];
        // next entry with same hash
        @Element(sequence = 18)
        public Pointer hashChain;
        @Element(sequence = 19)
        public int parentDir;
        // 1st extension block / FFS: cache block
        @Element(sequence = 20)
        public int extension;
        // -2 (ST_USERDIR) / -3 (ST_FILE) / -4 (ST_LINKFILE) / 4 (ST_LINKDIR) / 3 (ST_SOFTLINK)
        @Element(sequence = 21)
        public int secType;
    }

    /**
     * Amiga block structure post table
     */
    public static class AmigaBlockPost {
        public static final int SIZE = 200;
        public static class Union {

            public AmigaRootBlockPost r;
            public AmigaHeaderPost h;
        }
        public Union u;
    }

    /**
     * ディレクトリエントリ Amiga DOS
     * <p>
     * AmigaDOSは1セクタ分になるのでブロック番号だけを保持
     */
    @Serdes
    public static class DirectoryAmiga implements DirectoryT {

        @Element(sequence = 1)
        public int blockNum; // int
        @Element(sequence = 2)
        public AmigaBlockPre pre; // pointer
        @Element(sequence = 3)
        public AmigaBlockPost post; // pointer

        public static final int SIZE = 9;
    }

    /**
     * ディレクトリエントリ M68 FDOS (31bytes)
     */
    @Serdes
    public static class DirectoryM68fdos implements DirectoryT {

        @Element(sequence = 1)
        public M68fdosName name = new M68fdosName();
        @Element(sequence = 2)
        public M68fdosExt ext = new M68fdosExt();
        @Element(sequence = 3)
        public short attr1;
        @Element(sequence = 4)
        public short attr2;
        @Element(sequence = 5)
        public short blockSize;
        @Element(sequence = 6)
        public byte eofInSector;
        @Element(sequence = 7)
        public short date;
        @Element(sequence = 8)
        public short time; // unknown
        @Element(sequence = 9)
        public M68fdosRev rev = new M68fdosRev();
        @Element(sequence = 10)
        public short startSector;
        @Element(sequence = 11)
        public byte attr3;
        @Element(sequence = 12)
        public short endSector; // unknown
        @Element(sequence = 13)
        public short loadAddr;
        @Element(sequence = 14)
        public short execAddr;
        @Element(sequence = 15)
        public byte[] unknown2 = new byte[3];

        @Serdes
        public static class M68fdosName {

            @Element(sequence = 1)
            public short[] w = new short[2]; // big endien
            @Element(sequence = 2)
            public byte[] b = new byte[4];
        }

        @Serdes
        public static class M68fdosExt {

            @Element(sequence = 1)
            public short w; // big endien
            @Element(sequence = 2)
            public byte[] b = new byte[2];
        }

        @Serdes
        public static class M68fdosRev {

            @Element(sequence = 1)
            public short w; // big endien
            @Element(sequence = 2)
            public byte[] b = new byte[2];
        }

        public static final int SIZE = 31;
    }

    /**
     * TRSDOS gap
     */
    @Serdes
    public static class TrsdosGap {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte granules;
    }

    /**
     * ディレクトリエントリ TRSDOS 2.x (32bytes)
     */
    @Serdes
    public static class DirectoryTrsd23 implements DirectoryT {

        @Element(sequence = 1)
        public byte accessControl;
        @Element(sequence = 2)
        public byte overflow;
        @Element(sequence = 3)
        public byte reserved1;
        @Element(sequence = 4)
        public byte eofByteOffset;
        @Element(sequence = 5)
        public byte recordLength;
        @Element(sequence = 6)
        public byte[] name = new byte[8];
        @Element(sequence = 7)
        public byte[] ext = new byte[3];
        @Element(sequence = 8)
        public short updatePassword;
        @Element(sequence = 9)
        public short accessPassword;
        @Element(sequence = 10)
        public short eofSector;
        @Element(sequence = 11)
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

        public byte accessControl;
        public byte month; // 0x01 - 0x0c
        public byte year;
        public byte eofByteOffset;
        public byte recordLength;
        public byte[] name = new byte[8];
        public byte[] ext = new byte[3];
        public short updatePassword;
        public short accessPassword;
        public short eofSector;
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
         * リストをクリア
         */
        public void clear() {
            contents.clear();
        }

        /**
         * リストをクリア
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
