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


public class DiskD88 {

    public static class DiskDensity {

        byte val;
        String name;

        DiskDensity(int val, String name) {
            this.val = (byte) val;
            this.name = name;
        }
    }

    public static final DiskDensity[] gDiskDensity = {
            new DiskDensity(0x00, "2D"),
            new DiskDensity(0x10, "2DD"),
            new DiskDensity(0x20, "2HD"),
            new DiskDensity(0x30, "0x30 1DD"),
            new DiskDensity(0xff, null)
    };

    public static final int DISKD88_MAX_TRACKS = 164;
    public static final int HEADER_TYPE_D88 = 1;

    @Serdes(bigEndian = false)
    static class D88SectorId {

        @Element(sequence = 1)
        byte c;
        @Element(sequence = 2)
        byte h;
        @Element(sequence = 3)
        byte r;
        @Element(sequence = 4)
        byte n;
    }

    @Serdes(bigEndian = false)
    static class D88SectorHeader {

        @Element(sequence = 1)
        D88SectorId id = new D88SectorId();
        @Element(sequence = 2)
        short secnums;
        @Element(sequence = 3)
        byte density;
        @Element(sequence = 4)
        byte deleted;
        @Element(sequence = 5)
        byte status;
        @Element(sequence = 6)
        byte[] reserved = new byte[5];
        @Element(sequence = 7)
        short size;
    }

    @Serdes(bigEndian = false)
    static class D88Header {

        public static final int SIZE = 17 + 9 + 1 + 1 + 4 + 4 * DiskD88.DISKD88_MAX_TRACKS;
        @Element(sequence = 1)
        byte[] diskname = new byte[17];
        @Element(sequence = 2)
        byte[] reserved1 = new byte[9];
        @Element(sequence = 3)
        byte write_protect;
        @Element(sequence = 4)
        byte disk_density;
        @Element(sequence = 5)
        int disk_size;
        @Element(sequence = 6)
        int[] offsets = new int[DiskD88.DISKD88_MAX_TRACKS];

        @Override
        public String toString() {
            return new StringJoiner(", ", D88Header.class.getSimpleName() + "[", "]")
                    .add("diskname=" + Arrays.toString(diskname))
                    .add("reserved1=" + Arrays.toString(reserved1))
                    .add("write_protect=" + write_protect)
                    .add("disk_density=" + disk_density)
                    .add("disk_size=" + disk_size)
                    .add("offsets=" + Arrays.toString(offsets))
                    .toString();
        }
    }

    static class DiskD88SectorHeader extends DiskImageSectorHeader {

        public static final int SIZE = 16;

        private D88SectorHeader m_header;

        public DiskD88SectorHeader() {
            m_header = null;
        }

        @Override
        public int getHeaderType() {
            return DiskD88.HEADER_TYPE_D88;
        }

        public D88SectorHeader getHeader() {
            return m_header;
        }

        public int getHeaderSize() {
            return SIZE;
        }

        public void alloc() {
            if (m_header == null) {
                m_header = new D88SectorHeader();
            }
            m_header.id.c = 0;
            m_header.id.h = 0;
            m_header.id.r = 0;
            m_header.id.n = 0;
            m_header.secnums = 0;
            m_header.density = 0;
            m_header.deleted = 0;
            m_header.status = 0;
            Arrays.fill(m_header.reserved, (byte) 0);
            m_header.size = 0;
        }

        public void free() {
            m_header = null;
        }

        public void newHeader(DiskImageSectorHeader src) {
            if (m_header == null) {
                m_header = new D88SectorHeader();
            }
            if (src.getHeaderType() == DiskD88.HEADER_TYPE_D88) {
                D88SectorHeader srcHeader = ((DiskD88SectorHeader) src).m_header;
                m_header.id.c = srcHeader.id.c;
                m_header.id.h = srcHeader.id.h;
                m_header.id.r = srcHeader.id.r;
                m_header.id.n = srcHeader.id.n;
                m_header.secnums = srcHeader.secnums;
                m_header.density = srcHeader.density;
                m_header.deleted = srcHeader.deleted;
                m_header.status = srcHeader.status;
                System.arraycopy(srcHeader.reserved, 0, m_header.reserved, 0, 5);
                m_header.size = srcHeader.size;
            }
        }

        public void newHeader(int track_number, int side_number, int sector_number, int sector_size, int number_of_sector, boolean single_density, int status) {
            alloc();
            m_header.id.c = (byte) track_number;
            m_header.id.h = (byte) side_number;
            m_header.id.r = (byte) sector_number;
            m_header.size = (short) sector_size;
            m_header.secnums = (short) number_of_sector;
            m_header.density = (byte) (single_density ? 0x40 : 0);
            m_header.status = (byte) status;
        }

        public void fill(byte data) {
            if (m_header == null) return;
            m_header.id.c = data;
            m_header.id.h = data;
            m_header.id.r = data;
            m_header.id.n = data;
            m_header.secnums = (short) ((data & 0xFF) | ((data & 0xFF) << 8));
            m_header.density = data;
            m_header.deleted = data;
            m_header.status = data;
            Arrays.fill(m_header.reserved, data);
            m_header.size = (short) ((data & 0xFF) | ((data & 0xFF) << 8));
        }

        public void copy(DiskD88SectorHeader src) {
            if (m_header == null || src.m_header == null) return;
            m_header.id.c = src.m_header.id.c;
            m_header.id.h = src.m_header.id.h;
            m_header.id.r = src.m_header.id.r;
            m_header.id.n = src.m_header.id.n;
            m_header.secnums = src.m_header.secnums;
            m_header.density = src.m_header.density;
            m_header.deleted = src.m_header.deleted;
            m_header.status = src.m_header.status;
            System.arraycopy(src.m_header.reserved, 0, m_header.reserved, 0, 5);
            m_header.size = src.m_header.size;
        }

        public boolean isSame(DiskD88SectorHeader src) {
            if (m_header == null || src.m_header == null) return false;
            return m_header.id.c == src.m_header.id.c &&
                    m_header.id.h == src.m_header.id.h &&
                    m_header.id.r == src.m_header.id.r &&
                    m_header.id.n == src.m_header.id.n &&
                    m_header.secnums == src.m_header.secnums &&
                    m_header.density == src.m_header.density &&
                    m_header.deleted == src.m_header.deleted &&
                    m_header.status == src.m_header.status &&
                    Arrays.equals(m_header.reserved, src.m_header.reserved) &&
                    m_header.size == src.m_header.size;
        }

        public byte getIDC() {
            return m_header != null ? m_header.id.c : 0;
        }

        public byte getIDH() {
            return m_header != null ? m_header.id.h : 0;
        }

        public byte getIDR() {
            return m_header != null ? m_header.id.r : 0;
        }

        public byte getIDN() {
            return m_header != null ? m_header.id.n : 0;
        }

        public short getNumberOfSectors() {
            return m_header != null ? m_header.secnums : 0;
        }

        public byte getDensity() {
            return m_header != null ? m_header.density : 0;
        }

        public byte getDeleted() {
            return m_header != null ? m_header.deleted : 0;
        }

        public byte getStatus() {
            return m_header != null ? m_header.status : 0;
        }

        public short getSize() {
            return m_header != null ? m_header.size : 0;
        }

        public void setIDC(byte val) {
            if (m_header != null) {
                m_header.id.c = val;
            }
        }

        public void setIDH(byte val) {
            if (m_header != null) {
                m_header.id.h = val;
            }
        }

        public void setIDR(byte val) {
            if (m_header != null) {
                m_header.id.r = val;
            }
        }

        public void setIDN(byte val) {
            if (m_header != null) {
                m_header.id.n = val;
            }
        }

        public void setNumberOfSectors(short val) {
            if (m_header != null) {
                m_header.secnums = val;
            }
        }

        public void setDensity(byte val) {
            if (m_header != null) {
                m_header.density = val;
            }
        }

        public void setDeleted(byte val) {
            if (m_header != null) {
                m_header.deleted = val;
            }
        }

        public void setStatus(byte val) {
            if (m_header != null) {
                m_header.status = val;
            }
        }

        public void setSize(short val) {
            if (m_header != null) {
                m_header.size = val;
            }
        }
    }

    static class DiskD88Sector extends DiskImageSector {

        private final DiskD88SectorHeader m_header = new DiskD88SectorHeader();
        private byte[] data;
        private final DiskD88SectorHeader m_header_origin = new DiskD88SectorHeader();
        private byte[] data_origin;

        public DiskD88Sector(int n_num, DiskImageSectorHeader n_header, byte[] n_data) {
            super(n_num);
            m_header.newHeader(n_header);
            data = n_data;

            m_header_origin.newHeader(n_header);
            data_origin = new byte[m_header.getSize() & 0xFFFF];
            System.arraycopy(data, 0, data_origin, 0, m_header.getSize() & 0xFFFF);
        }

        public DiskD88Sector(int track_number, int side_number, int sector_number, int sector_size, int number_of_sector, boolean single_density, int status) {
            super(sector_number);
            m_header.newHeader(track_number, side_number, sector_number, sector_size, number_of_sector, single_density, status);
            this.setSectorSize(sector_size);

            data = new byte[m_header.getSize() & 0xFFFF];
            Arrays.fill(data, (byte) 0);

            m_header_origin.newHeader(m_header);

            data_origin = new byte[m_header.getSize() & 0xFFFF];
            Arrays.fill(data_origin, (byte) 0);
        }

        @Override
        public boolean replace(DiskImageSector src_sector) {
            if (data == null) {
                return false;
            }
            byte[] src_data = src_sector.getSectorBuffer();
            if (src_data == null) {
                return false;
            }
            int sz = Math.min(src_sector.getSectorBufferSize(), getSectorBufferSize());
            if (sz > 0) {
                Arrays.fill(data, (byte) 0);
                System.arraycopy(src_data, 0, data, 0, sz);
            }
            return true;
        }

        @Override
        public boolean fill(byte code, int len, int start) {
            if (data == null) {
                return false;
            }
            if (start < 0) {
                start = (m_header.getSize() & 0xFFFF) + start;
            }
            if (start < 0 || start >= (m_header.getSize() & 0xFFFF)) {
                return false;
            }

            if (len < 0) len = (m_header.getSize() & 0xFFFF) - start;
            else if ((start + len) > (m_header.getSize() & 0xFFFF)) len = (m_header.getSize() & 0xFFFF) - start;
            Arrays.fill(data, start, start + len, code);
            return true;
        }

        @Override
        public boolean copy(byte[] buf, int len, int start) {
            if (data == null) {
                return false;
            }
            if (start < 0) {
                start = (m_header.getSize() & 0xFFFF) + start;
            }
            if (len < 0 || start < 0 || start >= (m_header.getSize() & 0xFFFF)) {
                return false;
            }

            if ((start + len) > (m_header.getSize() & 0xFFFF)) len = (m_header.getSize() & 0xFFFF) - start;
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
        public short get16(int pos, boolean big_endian) {
            if (data == null) {
                return 0;
            }
            if (pos < 0) {
                pos += getSectorSize();
            }
            if (big_endian) {
                return (short) (((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF));
            } else {
                return (short) (((data[pos + 1] & 0xFF) << 8) | (data[pos] & 0xFF));
            }
        }

        @Override
        public int modifySectorSize(int size) {
            int diff = 0;
            if (data == null) {
                return diff;
            }
            if (size != (m_header.getSize() & 0xFFFF)) {
                diff = (m_header.getSize() & 0xFFFF) - size;

                byte[] newdata = new byte[size];
                Arrays.fill(newdata, (byte) 0);
                System.arraycopy(data, 0, newdata, 0, Math.min(size, m_header.getSize() & 0xFFFF));
                data = newdata;

                newdata = new byte[size];
                Arrays.fill(newdata, (byte) 0);
                System.arraycopy(data_origin, 0, newdata, 0, Math.min(size, m_header.getSize() & 0xFFFF));
                data_origin = newdata;

                m_header.setSize((short) size);
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
            boolean mod = !m_header_origin.isSame(m_header);
            if (!mod && data != null && data_origin != null) {
                mod = !Arrays.equals(Arrays.copyOf(data_origin, m_header_origin.getSize() & 0xFFFF),
                        Arrays.copyOf(data, m_header_origin.getSize() & 0xFFFF));
            }
            return mod;
        }

        @Override
        public void clearModify() {
            if (data == null) {
                return;
            }
            m_header_origin.copy(m_header);
            if (data != null && data_origin != null) {
                System.arraycopy(data, 0, data_origin, 0, m_header_origin.getSize() & 0xFFFF);
            }
        }

        @Override
        public void setSectorNumber(int val) {
            super.setSectorNumber(val);
            m_header.setIDR((byte) val);
        }

        @Override
        public boolean isDeleted() {
            return m_header.getDeleted() != 0;
        }

        @Override
        public void setDeletedMark(boolean val) {
            m_header.setDeleted((byte) (val ? 0x10 : 0));
        }

        @Override
        public boolean isSameSector(int sector_number, int density, boolean deleted_mark) {
            return sector_number == mNum &&
                    (density < 0 || (density == (isSingleDensity() ? 1 : 0))) &&
                    deleted_mark == isDeleted();
        }

        @Override
        public int getHeaderSize() {
            return m_header.getHeaderSize();
        }

        @Override
        public int getSectorSize() {
            int sec = convIDNToSecSize(m_header.getIDN());
            if (sec <= 0) return 0;
            if (sec > getSectorBufferSize()) sec = getSectorBufferSize();
            return sec;
        }

        @Override
        public void setSectorSize(int val) {
            m_header.setIDN(convSecSizeToIDN(val));
        }

        @Override
        public int getSectorBufferSize() {
            return m_header.getSize() & 0xFFFF;
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
            return (data != null && offset < (m_header.getSize() & 0xFFFF)) ? Arrays.copyOfRange(data, offset, data.length) : null;
        }

        @Override
        public void setSectorBuffer(byte[] data, int ofs, int len) {
            System.arraycopy(data, 0, this.data, ofs, len);
        }

        @Override
        public short getSectorsPerTrack() {
            return m_header.getNumberOfSectors();
        }

        @Override
        public void setSectorsPerTrack(short val) {
            m_header.setNumberOfSectors(val);
        }

        @Override
        public byte getSectorStatus() {
            return m_header.getStatus();
        }

        @Override
        public void setSectorStatus(byte val) {
            m_header.setStatus(val);
        }

        @Override
        public byte getIDC() {
            return m_header.getIDC();
        }

        @Override
        public byte getIDH() {
            return m_header.getIDH();
        }

        @Override
        public byte getIDR() {
            return m_header.getIDR();
        }

        @Override
        public byte getIDN() {
            return m_header.getIDN();
        }

        @Override
        public void setIDC(byte val) {
            m_header.setIDC(val);
        }

        @Override
        public void setIDH(byte val) {
            m_header.setIDH(val);
        }

        @Override
        public void setIDR(byte val) {
            m_header.setIDR(val);
        }

        @Override
        public void setIDN(byte val) {
            m_header.setIDN(val);
        }

        @Override
        public boolean isSingleDensity() {
            return m_header.getDensity() == 0x40;
        }

        @Override
        public void setSingleDensity(boolean val) {
            m_header.setDensity((byte) (val ? 0x40 : 0));
        }
    }

    public static class DiskD88Track extends DiskImageTrack {

        public DiskD88Track(DiskImageDisk disk) {
            super(disk);
        }

        public DiskD88Track(DiskImageDisk disk, int n_trk_num, int n_sid_num, int n_offset_pos, int n_interleave) {
            super(disk, n_trk_num, n_sid_num, n_offset_pos, n_interleave);
        }

        @Override
        public DiskImageSector newImageSector(int n_num, DiskImageSectorHeader n_header, byte[] n_data) {
            return new DiskD88Sector(n_num, n_header, n_data);
        }

        @Override
        public DiskImageSector newImageSector(int track_number, int side_number, int sector_number, int sector_size, int number_of_sector, boolean single_density, int status) {
            return new DiskD88Sector(track_number, side_number, sector_number, sector_size, number_of_sector, single_density, status);
        }
    }

    public static class DiskD88DiskHeader extends DiskImageDiskHeader {

        public static final int SIZE = 688;

        private D88Header m_header;

        public DiskD88DiskHeader() {
            m_header = null;
        }

        @Override
        public int getHeaderType() {
            return DiskD88.HEADER_TYPE_D88;
        }

        public D88Header getHeader() {
            return m_header;
        }

        public int getHeaderSize() {
            return SIZE;
        }

        public void alloc() {
            if (m_header == null) {
                m_header = new D88Header();
            }
            Arrays.fill(m_header.diskname, (byte) 0);
            Arrays.fill(m_header.reserved1, (byte) 0);
            m_header.write_protect = 0;
            m_header.disk_density = 0;
            m_header.disk_size = 0;
            Arrays.fill(m_header.offsets, 0);
        }

        public void free() {
            m_header = null;
        }

        public void newHeader(DiskImageDiskHeader src) {
            if (m_header == null) {
                m_header = new D88Header();
            }
            if (src.getHeaderType() == DiskD88.HEADER_TYPE_D88) {
                D88Header srcHeader = ((DiskD88DiskHeader) src).m_header;
                System.arraycopy(srcHeader.diskname, 0, m_header.diskname, 0, 17);
                System.arraycopy(srcHeader.reserved1, 0, m_header.reserved1, 0, 9);
                m_header.write_protect = srcHeader.write_protect;
                m_header.disk_density = srcHeader.disk_density;
                m_header.disk_size = srcHeader.disk_size;
                System.arraycopy(srcHeader.offsets, 0, m_header.offsets, 0, DiskD88.DISKD88_MAX_TRACKS);
            }
        }

        public void fill(byte data) {
            if (m_header == null) return;
            Arrays.fill(m_header.diskname, data);
            Arrays.fill(m_header.reserved1, data);
            m_header.write_protect = data;
            m_header.disk_density = data;
            m_header.disk_size = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).put(data).put(data).put(data).put(data).getInt(0);
            Arrays.fill(m_header.offsets, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).put(data).put(data).put(data).put(data).getInt(0));
        }

        public void copy(DiskD88DiskHeader src) {
            if (m_header == null || src.m_header == null) return;
            System.arraycopy(src.m_header.diskname, 0, m_header.diskname, 0, 17);
            System.arraycopy(src.m_header.reserved1, 0, m_header.reserved1, 0, 9);
            m_header.write_protect = src.m_header.write_protect;
            m_header.disk_density = src.m_header.disk_density;
            m_header.disk_size = src.m_header.disk_size;
            System.arraycopy(src.m_header.offsets, 0, m_header.offsets, 0, DiskD88.DISKD88_MAX_TRACKS);
        }

        public void clearOffsets() {
            if (m_header == null) return;
            Arrays.fill(m_header.offsets, 0);
        }

        public boolean isSame(DiskD88DiskHeader src) {
            if (m_header == null || src.m_header == null) return false;
            return Arrays.equals(m_header.diskname, src.m_header.diskname) &&
                    Arrays.equals(m_header.reserved1, src.m_header.reserved1) &&
                    m_header.write_protect == src.m_header.write_protect &&
                    m_header.disk_density == src.m_header.disk_density &&
                    m_header.disk_size == src.m_header.disk_size &&
                    Arrays.equals(m_header.offsets, src.m_header.offsets);
        }

        @Override
        public String getName(boolean real) {
            String name = m_header != null ? new String(m_header.diskname).trim() : "";
            if (!real && name.isEmpty()) {
                name = "(no name)";
            }
            return name;
        }

        @Override
        public boolean isWriteProtected() {
            return m_header != null && m_header.write_protect != 0;
        }

        public byte getDensity() {
            return m_header != null ? m_header.disk_density : 0;
        }

        public int getDiskSize() {
            if (m_header == null) return 0;
            return m_header.disk_size;
        }

        public int getOffset(int num) {
            if (m_header == null || num < 0 || num >= DiskD88.DISKD88_MAX_TRACKS) return 0;
            return m_header.offsets[num];
        }

        public void setName(String val) {
            if (m_header == null) return;

            String name = val;
            if (name.equals("(no name)")) {
                name = "";
            }

            byte[] nameBytes = name.getBytes();
            int len = Math.min(nameBytes.length, 16);
            System.arraycopy(nameBytes, 0, m_header.diskname, 0, len);
            if (len < 17) {
                m_header.diskname[len] = 0;
            }
        }

        public void setName(byte[] buf, int len) {
            if (m_header == null) return;

            if (len > 16) len = 16;
            System.arraycopy(buf, 0, m_header.diskname, 0, len);
            if (len < 16) len++;
            m_header.diskname[len] = 0;
        }

        public void setWriteProtect(boolean val) {
            if (m_header != null) m_header.write_protect = (byte) (val ? 0x10 : 0);
        }

        public void setDensity(byte val) {
            if (m_header != null) m_header.disk_density = val;
        }

        public void setDiskSize(int val) {
            if (m_header != null) m_header.disk_size = val;
        }

        public void setOffset(int num, int val) {
            if (num < 0 || num >= DiskD88.DISKD88_MAX_TRACKS) return;
            if (m_header != null) m_header.offsets[num] = val;
        }
    }

    public static class DiskD88Disk extends DiskImageDisk {

        private final DiskD88DiskHeader m_header = new DiskD88DiskHeader();
        private final DiskD88DiskHeader m_header_origin = new DiskD88DiskHeader();
        private boolean m_modified;
        private final int m_offset_start;

        public DiskD88Disk(DiskImageFile file, int n_num) {
            super(file, n_num);
            m_header.alloc();
            m_offset_start = m_header.getHeaderSize();
            m_header_origin.alloc();
            m_modified = false;
        }

        public DiskD88Disk(DiskImageFile file, int n_num, DiskParam n_param, String n_diskname, boolean n_write_protect) {
            super(file, n_num, n_param, n_diskname, n_write_protect);
            m_header.alloc();
            m_offset_start = m_header.getHeaderSize();
            m_header_origin.alloc();
            m_header_origin.fill((byte) 0xff);
            m_header.setName(n_diskname);
            m_header.setDensity((byte) n_param.getParamDensity());
            m_header.setWriteProtect(n_write_protect);
            m_modified = true;
        }

        public DiskD88Disk(DiskImageFile file, int n_num, DiskImageDiskHeader n_header) {
            super(file, n_num, n_header);
            m_header.newHeader(n_header);
            m_offset_start = m_header.getHeaderSize();
            m_header_origin.newHeader(n_header);
            m_modified = false;
        }

        @Override
        public DiskImageTrack newImageTrack() {
            return new DiskD88Track(this);
        }

        @Override
        public DiskImageTrack newImageTrack(int n_trk_num, int n_sid_num, int n_offset_pos, int n_interleave) {
            return new DiskD88Track(this, n_trk_num, n_sid_num, n_offset_pos, n_interleave);
        }

        @Override
        public void setModify() {
            m_modified = !m_header_origin.isSame(m_header);
        }

        @Override
        public void clearModify() {
            super.clearModify();
            m_header_origin.copy(m_header);
            m_modified = false;
        }

        @Override
        public boolean isModified() {
            if (!m_modified) {
                m_modified = super.isModified();
            }
            return m_modified;
        }

        @Override
        public String getName(boolean real) {
            return m_header.getName(real);
        }

        @Override
        public void setName(String val) {
            m_header.setName(val);
        }

        @Override
        public void setName(byte[] buf, int len) {
            m_header.setName(buf, len);
        }

        @Override
        public boolean isWriteProtected() {
            return m_header.isWriteProtected();
        }

        @Override
        public void setWriteProtect(boolean val) {
            m_header.setWriteProtect(val);
        }

        @Override
        public String getDensityText() {
            byte num = m_header.getDensity();
            int match = parent.getImage().findDensity(num & 0xFF);
            return match >= 0 ? DiskD88.gDiskDensity[match].name : "";
        }

        @Override
        public int getDensity() {
            return m_header.getDensity() & 0xFF;
        }

        @Override
        public void setDensity(int val) {
            m_header.setDensity((byte) val);
        }

        @Override
        public int getSize() {
            return m_header.getDiskSize();
        }

        @Override
        public void setSize(int val) {
            m_header.setDiskSize(val);
        }

        @Override
        public int getSizeWithoutHeader() {
            int size = m_header.getDiskSize();
            if (size >= m_offset_start) size -= m_offset_start;
            return size;
        }

        @Override
        public void setSizeWithoutHeader(int val) {
            m_header.setDiskSize(val + m_offset_start);
        }

        @Override
        public int getOffset(int num) {
            return m_header.getOffset(num);
        }

        @Override
        public void setOffset(int num, int offset) {
            m_header.setOffset(num, offset);
        }

        @Override
        public void setOffsetWithoutHeader(int num, int offset) {
            m_header.setOffset(num, offset + m_offset_start);
        }
    }

    static class DiskD88File extends DiskImageFile {

        public DiskD88File(DiskImage image) {
            super(image);
        }

        @Override
        public DiskImageDisk newImageDisk(int n_num) {
            return new DiskD88Disk(this, n_num);
        }

        @Override
        public DiskImageDisk newImageDisk(int n_num, DiskParam n_param, String n_diskname, boolean n_write_protect) {
            return new DiskD88Disk(this, n_num, n_param, n_diskname, n_write_protect);
        }

        @Override
        public DiskImageDisk newImageDisk(int n_num, DiskImageDiskHeader n_header) {
            return new DiskD88Disk(this, n_num, n_header);
        }
    }

    public static class DiskD88Image extends DiskImage {

        public DiskD88Image() {
        }

        @Override
        public DiskImageFile newImageFile() {
            return new DiskD88File(this);
        }

        public int getDensityNames(ArrayList<String> arr) {
            for (int i = 0; DiskD88.gDiskDensity[i].name != null; i++) {
                arr.add(DiskD88.gDiskDensity[i].name);
            }
            return arr.size();
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
        public int findDensityByIndex(int idx) {
            int match = -1;
            for (int i = 0; DiskD88.gDiskDensity[i].name != null; i++) {
                if (i == idx) {
                    match = i;
                    break;
                }
            }
            return match;
        }

        @Override
        public byte getDensity(int idx) {
            return DiskD88.gDiskDensity[idx].val;
        }
    }
}
