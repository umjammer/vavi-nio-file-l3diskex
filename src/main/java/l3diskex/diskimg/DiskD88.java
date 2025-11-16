///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringJoiner;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageDiskHeader;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageSectorHeader;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/** D88ディスクイメージ入出力 */
public class DiskD88 {

    /** disk density 0: 2D, 1: 2DD, 2: 2HD */
    public static class DiskDensity {

        byte val;
        String name;

        DiskDensity(int val, String name) {
            this.val = (byte) val;
            this.name = name;
        }
    }

    /** disk density 0: 2D, 1: 2DD, 2: 2HD, 3: 1DD (unofficial) */
    public static final DiskDensity[] gDiskDensity = {
            new DiskDensity(0x00, "2D"),
            new DiskDensity(0x10, "2DD"),
            new DiskDensity(0x20, "2HD"),
            new DiskDensity(0x30, "0x30 1DD"),
    };

    /** D88 sector id */
    @Serdes(bigEndian = false)
    public static class D88SectorId {

        /** cylinder (track) id (0...) */
        @Element(sequence = 1)
        public byte c;
        /** head (side) id */
        @Element(sequence = 2)
        public byte h;
        /** record (sector) id (1...) */
        @Element(sequence = 3)
        byte r;
        /** sector size (0: 128 bytes, 1: 256 bytes, 2: 512 bytes, 3: 1024 bytes) */
        @Element(sequence = 4)
        byte n;
    }

    /** D88 sector header */
    @Serdes(bigEndian = false)
    public static class D88SectorHeader {

        public static final int SIZE = 16;

        @Element(sequence = 1)
        public D88SectorId id = new D88SectorId();
        /** sector numbers per track */
        @Element(sequence = 2)
        public short numOfSectors;
        /** 0x00:double density 0x40:single density */
        @Element(sequence = 3)
        byte density;
        /** 0x10:deleted data */
        @Element(sequence = 4)
        byte deleted;
        /** 0x00:no error */
        @Element(sequence = 5)
        byte status;
        @Element(sequence = 6)
        byte[] reserved = new byte[5];
        /** sector size (bytes) */
        @Element(sequence = 7)
        public short size;
    }

    /** D88 disk header */
    @Serdes(bigEndian = false)
    public static class D88Header {

        public static final int DISKD88_MAX_TRACKS = 164;
        public static final int HEADER_TYPE_D88 = 1;

        public static final int SIZE = 17 + 9 + 1 + 1 + 4 + 4 * DISKD88_MAX_TRACKS;

        /** disk name */
        @Element(sequence = 1)
        public byte[] diskName = new byte[17];
        @Element(sequence = 2)
        byte[] reserved1 = new byte[9];
        /** 0x10 write protected */
        @Element(sequence = 3)
        byte writeProtect;
        /** disk density 00H: 2D, 10H: 2DD, 20H: 2HD */
        @Element(sequence = 4)
        byte diskDensity;
        /** disk size */
        @Element(sequence = 5)
        int diskSize;
        /** track table */
        @Element(sequence = 6)
        public int[] offsets = new int[DISKD88_MAX_TRACKS];

        @Override
        public String toString() {
            return new StringJoiner(", ", D88Header.class.getSimpleName() + "[", "]")
                    .add("diskName=" + Arrays.toString(diskName))
                    .add("reserved1=" + Arrays.toString(reserved1))
                    .add("writeProtect=" + writeProtect)
                    .add("diskDensity=" + diskDensity)
                    .add("diskSize=" + diskSize)
                    .add("offsets=" + Arrays.toString(offsets))
                    .toString();
        }
    }

    /** セクタデータへのヘッダ部分を渡すクラス */
    public static class DiskD88SectorHeader extends DiskImageSectorHeader {

        /** sector header */
        private D88SectorHeader header;

        public DiskD88SectorHeader() {
            header = null;
        }

        @Override
        public int getHeaderType() {
            return D88Header.HEADER_TYPE_D88;
        }

        public D88SectorHeader getHeader() {
            return header;
        }

        public int getHeaderSize() {
            return D88SectorHeader.SIZE;
        }

        public void alloc() {
            if (header == null) {
                header = new D88SectorHeader();
            }
            header.id.c = 0;
            header.id.h = 0;
            header.id.r = 0;
            header.id.n = 0;
            header.numOfSectors = 0;
            header.density = 0;
            header.deleted = 0;
            header.status = 0;
            Arrays.fill(header.reserved, (byte) 0);
            header.size = 0;
        }

        public void free() {
            header = null;
        }

        public void newHeader(DiskImageSectorHeader src) {
            if (header == null) {
                header = new D88SectorHeader();
            }
            if (src.getHeaderType() == D88Header.HEADER_TYPE_D88) {
                D88SectorHeader srcHeader = ((DiskD88SectorHeader) src).header;
                header.id.c = srcHeader.id.c;
                header.id.h = srcHeader.id.h;
                header.id.r = srcHeader.id.r;
                header.id.n = srcHeader.id.n;
                header.numOfSectors = srcHeader.numOfSectors;
                header.density = srcHeader.density;
                header.deleted = srcHeader.deleted;
                header.status = srcHeader.status;
                System.arraycopy(srcHeader.reserved, 0, header.reserved, 0, 5);
                header.size = srcHeader.size;
            }
        }

        public void newHeader(int trackNumber, int sideNumber, int sectorNumber, int sectorSize, int numOfSectors, boolean singleDensity, int status) {
            alloc();
            header.id.c = (byte) trackNumber;
            header.id.h = (byte) sideNumber;
            header.id.r = (byte) sectorNumber;
            header.size = (short) sectorSize;
            header.numOfSectors = (short) numOfSectors;
            header.density = (byte) (singleDensity ? 0x40 : 0);
            header.status = (byte) status;
        }

        public void fill(byte data) {
            if (header == null) return;
            header.id.c = data;
            header.id.h = data;
            header.id.r = data;
            header.id.n = data;
            header.numOfSectors = (short) ((data & 0xFF) | ((data & 0xFF) << 8));
            header.density = data;
            header.deleted = data;
            header.status = data;
            Arrays.fill(header.reserved, data);
            header.size = (short) ((data & 0xFF) | ((data & 0xFF) << 8));
        }

        public void copy(DiskD88SectorHeader src) {
            if (header == null || src.header == null) return;
            header.id.c = src.header.id.c;
            header.id.h = src.header.id.h;
            header.id.r = src.header.id.r;
            header.id.n = src.header.id.n;
            header.numOfSectors = src.header.numOfSectors;
            header.density = src.header.density;
            header.deleted = src.header.deleted;
            header.status = src.header.status;
            System.arraycopy(src.header.reserved, 0, header.reserved, 0, 5);
            header.size = src.header.size;
        }

        public boolean isSame(DiskD88SectorHeader src) {
            if (header == null || src.header == null) return false;
            return header.id.c == src.header.id.c &&
                    header.id.h == src.header.id.h &&
                    header.id.r == src.header.id.r &&
                    header.id.n == src.header.id.n &&
                    header.numOfSectors == src.header.numOfSectors &&
                    header.density == src.header.density &&
                    header.deleted == src.header.deleted &&
                    header.status == src.header.status &&
                    Arrays.equals(header.reserved, src.header.reserved) &&
                    header.size == src.header.size;
        }

        public byte getIDC() {
            return header != null ? header.id.c : 0;
        }

        public byte getIDH() {
            return header != null ? header.id.h : 0;
        }

        public byte getIDR() {
            return header != null ? header.id.r : 0;
        }

        public byte getIDN() {
            return header != null ? header.id.n : 0;
        }

        public short getNumberOfSectors() {
            return header != null ? header.numOfSectors : 0;
        }

        public byte getDensity() {
            return header != null ? header.density : 0;
        }

        public byte getDeleted() {
            return header != null ? header.deleted : 0;
        }

        public byte getStatus() {
            return header != null ? header.status : 0;
        }

        public short getSize() {
            return header != null ? header.size : 0;
        }

        public void setIDC(byte val) {
            if (header != null) {
                header.id.c = val;
            }
        }

        public void setIDH(byte val) {
            if (header != null) {
                header.id.h = val;
            }
        }

        public void setIDR(byte val) {
            if (header != null) {
                header.id.r = val;
            }
        }

        public void setIDN(byte val) {
            if (header != null) {
                header.id.n = val;
            }
        }

        public void setNumberOfSectors(short val) {
            if (header != null) {
                header.numOfSectors = val;
            }
        }

        public void setDensity(byte val) {
            if (header != null) {
                header.density = val;
            }
        }

        public void setDeleted(byte val) {
            if (header != null) {
                header.deleted = val;
            }
        }

        public void setStatus(byte val) {
            if (header != null) {
                header.status = val;
            }
        }

        public void setSize(short val) {
            if (header != null) {
                header.size = val;
            }
        }
    }

    /** セクタデータへのポインタを保持するクラス */
    static class DiskD88Sector extends DiskImageSector {

        /** sector header */
        private final DiskD88SectorHeader header = new DiskD88SectorHeader();
        /** sector data */
        private byte[] data;

        /** pre-save header */
        private final DiskD88SectorHeader headerOrigin = new DiskD88SectorHeader();
        /** pre-save data */
        private byte[] dataOrigin;

        public DiskD88Sector(int num, DiskImageSectorHeader header, byte[] data) {
            super(num);
            this.header.newHeader(header);
            this.data = data;

            headerOrigin.newHeader(header);
            dataOrigin = new byte[this.header.getSize() & 0xffff];
            System.arraycopy(this.data, 0, dataOrigin, 0, this.header.getSize() & 0xffff);
        }

        public DiskD88Sector(int trackNumber, int sideNumber, int sectorNumber, int sectorSize, int numberOfSector, boolean singleDensity, int status) {
            super(sectorNumber);
            header.newHeader(trackNumber, sideNumber, sectorNumber, sectorSize, numberOfSector, singleDensity, status);
            this.setSectorSize(sectorSize);

            data = new byte[header.getSize() & 0xffff];
            Arrays.fill(data, (byte) 0);

            headerOrigin.newHeader(header);

            dataOrigin = new byte[header.getSize() & 0xffff];
            Arrays.fill(dataOrigin, (byte) 0);
        }

        @Override
        public boolean replace(DiskImageSector srcSector) {
            if (data == null) {
                return false;
            }
            byte[] srcData = srcSector.getSectorBuffer();
            if (srcData == null) {
                return false;
            }
            int size = Math.min(srcSector.getSectorBufferSize(), getSectorBufferSize());
            if (size > 0) {
                Arrays.fill(data, (byte) 0);
                System.arraycopy(srcData, 0, data, 0, size);
            }
            return true;
        }

        @Override
        public boolean fill(byte code, int len, int start) {
            if (data == null) {
                return false;
            }
            if (start < 0) {
                start = (header.getSize() & 0xffff) + start;
            }
            if (start < 0 || start >= (header.getSize() & 0xffff)) {
                return false;
            }

            if (len < 0) len = (header.getSize() & 0xffff) - start;
            else if ((start + len) > (header.getSize() & 0xffff)) len = (header.getSize() & 0xffff) - start;
            Arrays.fill(data, start, start + len, code);
            return true;
        }

        @Override
        public boolean copy(byte[] buf, int len, int start) {
            if (data == null) {
                return false;
            }
            if (start < 0) {
                start = (header.getSize() & 0xffff) + start;
            }
            if (len < 0 || start < 0 || start >= (header.getSize() & 0xffff)) {
                return false;
            }

            if ((start + len) > (header.getSize() & 0xffff)) len = (header.getSize() & 0xffff) - start;
            System.arraycopy(buf, 0, data, start, len);
            return true;
        }

        @Override
        public int find(byte[] buf, int len) {
            if (data == null) {
                return -1;
            }
            int match = -1;
            for (int pos = 0; pos < (getSectorBufferSize() - len); pos++) {
                boolean found = true;
                for (int i = 0; i < len; i++) {
                    if (data[pos + i] != buf[i]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    match = pos;
                    break;
                }
            }
            return match;
        }

        @Override
        public byte get(int pos) {
            if (data == null) {
                return 0;
            }
            if (pos < 0) {
                pos += getSectorSize();
            }
            return data[pos];
        }

        @Override
        public short get16(int pos, boolean bigEndian) {
            if (data == null) {
                return 0;
            }
            if (pos < 0) {
                pos += getSectorSize();
            }
            if (bigEndian) {
                return (short) (((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff));
            } else {
                return (short) (((data[pos + 1] & 0xff) << 8) | (data[pos] & 0xff));
            }
        }

        @Override
        public int modifySectorSize(int size) {
            int diff = 0;
            if (data == null) {
                return diff;
            }
            if (size != (header.getSize() & 0xffff)) {
                diff = (header.getSize() & 0xffff) - size;

                byte[] newdata = new byte[size];
                Arrays.fill(newdata, (byte) 0);
                System.arraycopy(data, 0, newdata, 0, Math.min(size, header.getSize() & 0xffff));
                data = newdata;

                newdata = new byte[size];
                Arrays.fill(newdata, (byte) 0);
                System.arraycopy(dataOrigin, 0, newdata, 0, Math.min(size, header.getSize() & 0xffff));
                dataOrigin = newdata;

                header.setSize((short) size);
                setSectorSize(size);
            }
            return diff;
        }

        @Override
        public void setModify() {
        }

        @Override
        public boolean isModified() {
            if (data == null) {
                return false;
            }
            boolean mod = !headerOrigin.isSame(header);
            if (!mod && data != null && dataOrigin != null) {
                mod = !Arrays.equals(Arrays.copyOf(dataOrigin, headerOrigin.getSize() & 0xffff),
                        Arrays.copyOf(data, headerOrigin.getSize() & 0xffff));
            }
            return mod;
        }

        @Override
        public void clearModify() {
            if (data == null) {
                return;
            }
            headerOrigin.copy(header);
            if (data != null && dataOrigin != null) {
                System.arraycopy(data, 0, dataOrigin, 0, headerOrigin.getSize() & 0xffff);
            }
        }

        @Override
        public void setSectorNumber(int val) {
            super.setSectorNumber(val);
            header.setIDR((byte) val);
        }

        @Override
        public boolean isDeleted() {
            return header.getDeleted() != 0;
        }

        @Override
        public void setDeletedMark(boolean val) {
            header.setDeleted((byte) (val ? 0x10 : 0));
        }

        @Override
        public boolean isSameSector(int sectorNumber, int density, boolean deletedMark) {
            return sectorNumber == mNum &&
                    (density < 0 || (density == (isSingleDensity() ? 1 : 0))) &&
                    deletedMark == isDeleted();
        }

        @Override
        public int getHeaderSize() {
            return header.getHeaderSize();
        }

        @Override
        public int getSectorSize() {
            int sec = convIDNToSecSize(header.getIDN());
            if (sec <= 0) return 0;
            if (sec > getSectorBufferSize()) sec = getSectorBufferSize();
            return sec;
        }

        @Override
        public void setSectorSize(int val) {
            header.setIDN(convSecSizeToIDN(val));
        }

        @Override
        public int getSectorBufferSize() {
            return header.getSize() & 0xffff;
        }

        @Override
        public int getSize() {
            return 16 + getSectorBufferSize();
        }

        @Override
        public byte[] getSectorBuffer() {
            return data;
        }

        @Override
        public byte[] getSectorBuffer(int offset) {
            return (data != null && offset < (header.getSize() & 0xffff)) ? Arrays.copyOfRange(data, offset, data.length) : null;
        }

        @Override
        public void setSectorBuffer(byte[] data, int ofs, int len) {
            System.arraycopy(data, 0, this.data, ofs, len);
        }

        @Override
        public short getSectorsPerTrack() {
            return header.getNumberOfSectors();
        }

        @Override
        public void setSectorsPerTrack(short val) {
            header.setNumberOfSectors(val);
        }

        @Override
        public byte getSectorStatus() {
            return header.getStatus();
        }

        @Override
        public void setSectorStatus(byte val) {
            header.setStatus(val);
        }

        @Override
        public byte getIDC() {
            return header.getIDC();
        }

        @Override
        public byte getIDH() {
            return header.getIDH();
        }

        @Override
        public byte getIDR() {
            return header.getIDR();
        }

        @Override
        public byte getIDN() {
            return header.getIDN();
        }

        @Override
        public void setIDC(byte val) {
            header.setIDC(val);
        }

        @Override
        public void setIDH(byte val) {
            header.setIDH(val);
        }

        @Override
        public void setIDR(byte val) {
            header.setIDR(val);
        }

        @Override
        public void setIDN(byte val) {
            header.setIDN(val);
        }

        @Override
        public boolean isSingleDensity() {
            return header.getDensity() == 0x40;
        }

        @Override
        public void setSingleDensity(boolean val) {
            header.setDensity((byte) (val ? 0x40 : 0));
        }
    }

    /** トラックデータへのポインタを保持するクラス */
    public static class DiskD88Track extends DiskImageTrack {

        public DiskD88Track(DiskImageDisk disk) {
            super(disk);
        }

        public DiskD88Track(DiskImageDisk disk, int trackNum, int sideNum, int offsetPos, int interleave) {
            super(disk, trackNum, sideNum, offsetPos, interleave);
        }

        @Override
        public DiskImageSector newImageSector(int num, DiskImageSectorHeader header, byte[] data) {
            return new DiskD88Sector(num, header, data);
        }

        @Override
        public DiskImageSector newImageSector(int trackNumber, int sideNumber, int sectorNumber, int sectorSize, int numberOfSector, boolean singleDensity, int status) {
            return new DiskD88Sector(trackNumber, sideNumber, sectorNumber, sectorSize, numberOfSector, singleDensity, status);
        }
    }

    /** １ディスクのヘッダを渡すクラス */
    public static class DiskD88DiskHeader extends DiskImageDiskHeader {

        private D88Header header;

        public DiskD88DiskHeader() {
            header = null;
        }

        @Override
        public int getHeaderType() {
            return D88Header.HEADER_TYPE_D88;
        }

        public D88Header getHeader() {
            return header;
        }

        public int getHeaderSize() {
            return D88Header.SIZE;
        }

        public void alloc() {
            if (header == null) {
                header = new D88Header();
            }
            Arrays.fill(header.diskName, (byte) 0);
            Arrays.fill(header.reserved1, (byte) 0);
            header.writeProtect = 0;
            header.diskDensity = 0;
            header.diskSize = 0;
            Arrays.fill(header.offsets, 0);
        }

        public void free() {
            header = null;
        }

        public void newHeader(DiskImageDiskHeader src) {
            if (header == null) {
                header = new D88Header();
            }
            if (src.getHeaderType() == D88Header.HEADER_TYPE_D88) {
                D88Header srcHeader = ((DiskD88DiskHeader) src).header;
                System.arraycopy(srcHeader.diskName, 0, header.diskName, 0, 17);
                System.arraycopy(srcHeader.reserved1, 0, header.reserved1, 0, 9);
                header.writeProtect = srcHeader.writeProtect;
                header.diskDensity = srcHeader.diskDensity;
                header.diskSize = srcHeader.diskSize;
                System.arraycopy(srcHeader.offsets, 0, header.offsets, 0, D88Header.DISKD88_MAX_TRACKS);
            }
        }

        public void fill(byte data) {
            if (header == null) return;
            Arrays.fill(header.diskName, data);
            Arrays.fill(header.reserved1, data);
            header.writeProtect = data;
            header.diskDensity = data;
            header.diskSize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).put(data).put(data).put(data).put(data).getInt(0);
            Arrays.fill(header.offsets, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).put(data).put(data).put(data).put(data).getInt(0));
        }

        public void copy(DiskD88DiskHeader src) {
            if (header == null || src.header == null) return;
            System.arraycopy(src.header.diskName, 0, header.diskName, 0, 17);
            System.arraycopy(src.header.reserved1, 0, header.reserved1, 0, 9);
            header.writeProtect = src.header.writeProtect;
            header.diskDensity = src.header.diskDensity;
            header.diskSize = src.header.diskSize;
            System.arraycopy(src.header.offsets, 0, header.offsets, 0, D88Header.DISKD88_MAX_TRACKS);
        }

        public void clearOffsets() {
            if (header == null) return;
            Arrays.fill(header.offsets, 0);
        }

        public boolean isSame(DiskD88DiskHeader src) {
            if (header == null || src.header == null) return false;
            return Arrays.equals(header.diskName, src.header.diskName) &&
                    Arrays.equals(header.reserved1, src.header.reserved1) &&
                    header.writeProtect == src.header.writeProtect &&
                    header.diskDensity == src.header.diskDensity &&
                    header.diskSize == src.header.diskSize &&
                    Arrays.equals(header.offsets, src.header.offsets);
        }

        @Override
        public String getName(boolean real) {
            String name = header != null ? new String(header.diskName).trim() : "";
            if (!real && name.isEmpty()) {
                name = "(no name)";
            }
            return name;
        }

        @Override
        public boolean isWriteProtected() {
            return header != null && header.writeProtect != 0;
        }

        public byte getDensity() {
            return header != null ? header.diskDensity : 0;
        }

        public int getDiskSize() {
            if (header == null) return 0;
            return header.diskSize;
        }

        public int getOffset(int num) {
            if (header == null || num < 0 || num >= D88Header.DISKD88_MAX_TRACKS) return 0;
            return header.offsets[num];
        }

        public void setName(String val) {
            if (header == null) return;

            String name = val;
            if (name.equals("(no name)")) {
                name = "";
            }

            byte[] nameBytes = name.getBytes();
            int len = Math.min(nameBytes.length, 16);
            System.arraycopy(nameBytes, 0, header.diskName, 0, len);
            if (len < 17) {
                header.diskName[len] = 0;
            }
        }

        public void setName(byte[] buf, int len) {
            if (header == null) return;

            if (len > 16) len = 16;
            System.arraycopy(buf, 0, header.diskName, 0, len);
            if (len < 16) len++;
            header.diskName[len] = 0;
        }

        public void setWriteProtect(boolean val) {
            if (header != null) header.writeProtect = (byte) (val ? 0x10 : 0);
        }

        public void setDensity(byte val) {
            if (header != null) header.diskDensity = val;
        }

        public void setDiskSize(int val) {
            if (header != null) header.diskSize = val;
        }

        public void setOffset(int num, int val) {
            if (num < 0 || num >= D88Header.DISKD88_MAX_TRACKS) return;
            if (header != null) header.offsets[num] = val;
        }
    }

    /** １ディスクへのポインタを保持するクラス */
    public static class DiskD88Disk extends DiskImageDisk {

        /** disk header */
        private final DiskD88DiskHeader header = new DiskD88DiskHeader();
        private final DiskD88DiskHeader headerOrigin = new DiskD88DiskHeader();

        /** 変更したか */
        private boolean modified;

        private final int offsetStart;

        public DiskD88Disk(DiskImageFile file, int n_num) {
            super(file, n_num);
            header.alloc();
            offsetStart = header.getHeaderSize();
            headerOrigin.alloc();
            modified = false;
        }

        public DiskD88Disk(DiskImageFile file, int num, DiskParam param, String diskName, boolean writeProtect) {
            super(file, num, param, diskName, writeProtect);
            header.alloc();
            offsetStart = header.getHeaderSize();
            headerOrigin.alloc();
            headerOrigin.fill((byte) 0xff);
            header.setName(diskName);
            header.setDensity((byte) param.getParamDensity());
            header.setWriteProtect(writeProtect);
            modified = true;
        }

        public DiskD88Disk(DiskImageFile file, int num, DiskImageDiskHeader header) {
            super(file, num, header);
            this.header.newHeader(header);
            offsetStart = this.header.getHeaderSize();
            headerOrigin.newHeader(header);
            modified = false;
        }

        @Override
        public DiskImageTrack newImageTrack() {
            return new DiskD88Track(this);
        }

        @Override
        public DiskImageTrack newImageTrack(int trackNum, int sideNum, int offsetPos, int interleave) {
            return new DiskD88Track(this, trackNum, sideNum, offsetPos, interleave);
        }

        @Override
        public void setModify() {
            modified = !headerOrigin.isSame(header);
        }

        @Override
        public void clearModify() {
            super.clearModify();
            headerOrigin.copy(header);
            modified = false;
        }

        @Override
        public boolean isModified() {
            if (!modified) {
                modified = super.isModified();
            }
            return modified;
        }

        @Override
        public String getName(boolean real) {
            return header.getName(real);
        }

        @Override
        public void setName(String val) {
            header.setName(val);
        }

        @Override
        public void setName(byte[] buf, int len) {
            header.setName(buf, len);
        }

        @Override
        public boolean isWriteProtected() {
            return header.isWriteProtected();
        }

        @Override
        public void setWriteProtect(boolean val) {
            header.setWriteProtect(val);
        }

        @Override
        public String getDensityText() {
            byte num = header.getDensity();
            int match = parent.getImage().findDensity(num & 0xFF);
            return match >= 0 ? DiskD88.gDiskDensity[match].name : "";
        }

        @Override
        public int getDensity() {
            return header.getDensity() & 0xFF;
        }

        @Override
        public void setDensity(int val) {
            header.setDensity((byte) val);
        }

        @Override
        public int getSize() {
            return header.getDiskSize();
        }

        @Override
        public void setSize(int val) {
            header.setDiskSize(val);
        }

        @Override
        public int getSizeWithoutHeader() {
            int size = header.getDiskSize();
            if (size >= offsetStart) size -= offsetStart;
            return size;
        }

        @Override
        public void setSizeWithoutHeader(int val) {
            header.setDiskSize(val + offsetStart);
        }

        @Override
        public int getOffset(int num) {
            return header.getOffset(num);
        }

        @Override
        public void setOffset(int num, int offset) {
            header.setOffset(num, offset);
        }

        @Override
        public void setOffsetWithoutHeader(int num, int offset) {
            header.setOffset(num, offset + offsetStart);
        }
    }

    static class DiskD88File extends DiskImageFile {

        public DiskD88File(DiskImage image) {
            super(image);
        }

        @Override
        public DiskImageDisk newImageDisk(int num) {
            return new DiskD88Disk(this, num);
        }

        @Override
        public DiskImageDisk newImageDisk(int num, DiskParam param, String diskName, boolean writeProtect) {
            return new DiskD88Disk(this, num, param, diskName, writeProtect);
        }

        @Override
        public DiskImageDisk newImageDisk(int num, DiskImageDiskHeader header) {
            return new DiskD88Disk(this, num, header);
        }
    }

    /** ディスクイメージへのポインタを保持するクラス */
    public static class DiskD88Image extends DiskImage {

        public DiskD88Image() {
        }

        @Override
        public DiskImageFile newImageFile() {
            return new DiskD88File(this);
        }

        public int getDensityNames(ArrayList<String> result) {
            for (int i = 0; DiskD88.gDiskDensity[i].name != null; i++) {
                result.add(DiskD88.gDiskDensity[i].name);
            }
            return result.size();
        }

        @Override
        public int findDensity(int val) {
            int match = -1;
            for (int i = 0; DiskD88.gDiskDensity[i].name != null; i++) {
                if ((DiskD88.gDiskDensity[i].val & 0xFF) == val) {
                    match = i;
                    break;
                }
            }
            return match;
        }

        @Override
        public int findDensityByIndex(int index) {
            int match = -1;
            for (int i = 0; DiskD88.gDiskDensity[i].name != null; i++) {
                if (i == index) {
                    match = i;
                    break;
                }
            }
            return match;
        }

        @Override
        public byte getDensity(int index) {
            return DiskD88.gDiskDensity[index].val;
        }
    }
}
