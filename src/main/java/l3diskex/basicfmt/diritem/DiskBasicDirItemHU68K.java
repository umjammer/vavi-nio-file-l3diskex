/*
 * Sasaji (original C++ source)
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.type.DiskBasicTypeHU68K.FORMAT_TYPE_HU68K;


/**
 * Directory item for Human68k.
 */
public class DiskBasicDirItemHU68K extends DiskBasicDirItemMSDOS {

    /**
     * ディレクトリエントリ Human68K (MS-DOS compatible) (32bytes)
     */
    @Serdes
    public static class DirectoryHu68k implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte[] name2 = new byte[10];
        @Element(sequence = 5)
        public short wTime;
        @Element(sequence = 6)
        public short wDate;
        @Element(sequence = 7)
        public short startGroup;
        @Element(sequence = 8)
        public int fileSize;

        public static final int SIZE = 32;
    }

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_HU68K;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);
    }

    @Override
    public void init(DiskBasic basic,
                     DiskImageSector sector,
                     int sectorPos,
                     byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic,
                     int num,
                     DiskBasicGroupItem groupItem,
                     DiskImageSector sector,
                     int sectorPos,
                     byte[] data, int dataPos,
                     SectorParam next,
                     boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);
    }

    @Override
    public void setData(int num,
                        DiskBasicGroupItem groupItem,
                        DiskImageSector sector,
                        int sectorPos,
                        byte[] data,
                        int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);
    }

    @Override
    public byte[] getFileNamePos(int num, int[] size, int[] len) {
        return switch (num) {
            case 0 -> {
                size[0] = len[0] = data.data().hu68k().name.length;
                yield data.data().hu68k().name;
            }
            case 1 -> {
                size[0] = len[0] = data.data().hu68k().name2.length;
                yield data.data().hu68k().name2;
            }
            default -> {
                size[0] = len[0] = 0;
                yield null;
            }
        };
    }

    @Override
    public byte[] getFileExtPos(int[] len) {
        len[0] = data.data().hu68k().ext.length;
        return data.data().hu68k().ext;
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    //
    // ダイアログ用
    //

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().hu68k().name, data.data().hu68k().name.length);
        vals.add("EXT", data.data().hu68k().ext, data.data().hu68k().ext.length);
        vals.add("TYPE", data.data().hu68k().type);
        vals.add("NAME2", data.data().hu68k().name2, data.data().hu68k().name2.length);
        vals.add("WTIME", data.data().hu68k().wTime);
        vals.add("WDATE", data.data().hu68k().wDate);
        vals.add("START_GROUP", data.data().hu68k().startGroup);
        vals.add("FILE_SIZE", data.data().hu68k().fileSize);
    }
}
