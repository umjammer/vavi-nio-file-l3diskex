/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.awt.Choice;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemXDOS;
import l3diskex.basicfmt.DiskBasicDirItemXDOS.XdosSubTypeT;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.DiskBasicDirItemXDOS.convStrToUserFileType;
import static l3diskex.basicfmt.DiskBasicDirItemXDOS.gTypeNameXDOS1;
import static l3diskex.basicfmt.DiskBasicDirItemXDOS.gTypeNameXDOS2;
import static l3diskex.basicfmt.DiskBasicDirItemXDOS.xdosSubTypes;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemXDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemXDOS extends UiDirItem {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    DiskBasicDirItemXDOS dirItem;

    protected int getFileType1InAttrDialog(IntNameBox parent) {
        JComboBox comFType = (JComboBox) parent.getComponent(51);
        JComboBox comSType = (JComboBox) parent.getComponent(52);

        int typ1 = 0;

        int fidx = comFType.getSelection();
        if (fidx >= 0) {
            if (fidx < 8) {
                int sidx = comSType.getSelection();
                XdosSubTypeT[] stypes = xdosSubTypes[fidx + 1];

                typ1 = ((fidx + 1) << 8);
                typ1 |= getSubTypeInAttrDialog(parent, stypes, sidx, comSType);
            } else {
                typ1 = 0x8000;
            }
        } else {
            typ1 = convStrToUserFileType(comFType.getValue());
        }
        return typ1;
    }

    protected int getFileType2InAttrDialog(IntNameBox parent) {
        int typ2 = 0;
        for (int idx = 0; gTypeNameXDOS2[idx] != null; idx++) {
            JComboBox chk = (JComboBox) parent.getComponent(53 + idx);
            typ2 |= (chk.getValue() ? (0x80 >> idx) : 0);
        }
        return typ2;
    }

    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int showFlags, String filePath, BoxLayout sizer, Object flags) {
        int t1 = dirItem.getFileType1();
        int t2 = dirItem.getFileType2();

        if ((showFlags & INTNAME_NEW_FILE) != 0) {
            t1 = dirItem.convOriginalTypeFromFileName(filePath);
        }

        BoxLayout staFType = new BoxLayout(new StaticBox(parent, -1, "File Type"), VERTICAL);
        JComboBox comFType = new JComboBox(parent, 51);
        for (int idx = 1; idx < gTypeNameXDOS1.size(); idx++) {
            comFType.append(Utils.keyAt(gTypeNameXDOS1, idx));
        }
        int ftype = (t1 >> 8);
        if (1 <= ftype && ftype <= 8) {
            comFType.select(ftype - 1);
        } else {
            comFType.setValue(dirItem.convFileType1Str(t1));
        }
        staFType.add(comFType, flags);

        Choice comSType = new Choice(parent, 52);
        staFType.add(comSType, flags);

        sizer.add(staFType, flags);

        BoxLayout staFAttr = new BoxLayout(new BoxLayout(parent, -1, "File Attributes"), VERTICAL);
        for (int idx = 0; gTypeNameXDOS2[idx] != null; idx++) {
            JCheckBox chk = new JCheckBox(parent, 53 + idx, gTypeNameXDOS2[idx]);
            staFAttr.add(chk, flags);
            chk.setValue((t2 & (0x80 >> idx)) != 0);
        }
        sizer.add(staFAttr, flags);

        parent.bind(EVT_COMBOBOX, parent::onChangeType1, 51);
    }

    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        JComboBox comFType = (JComboBox) parent.getComponent(51);
        JComboBox comSType = (JComboBox) parent.getComponent(52);

        if (comFType == null || comSType == null) return;

        int typ1 = dirItem.getFileType1();
        int ftype = (typ1 >> 8);
        int stype = (typ1 & 0xff);
        int fidx = comFType.getSelection();
        XdosSubTypeT[] stypes = null;
        if (fidx >= 0) {
            stypes = xdosSubTypes[fidx + 1];
            if (fidx + 1 != ftype) {
                stype = -1;
            }
        }
        comSType.clear();

        setSubTypeInAttrDialog(parent, stypes, stype, comSType);
    }

    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int val = getFileType1InAttrDialog(parent) | (getFileType2InAttrDialog(parent) << 16);
        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, val);
        return true;
    }

    private static void setSubTypeInAttrDialog(IntNameBox parent, XdosSubTypeT[] stypes, int stype, JComboBox comSType) {
        if (stypes != null) {
            for (int idx = 0; stypes[idx].desc != null; idx++) {
                for (int i = stypes[idx].start; i <= stypes[idx].end; i++) {
                    String str = String.format("0x%02x ", i);
                    str += stypes[idx].desc;
                    int row = comSType.append(str);
                    if (stype == i) {
                        comSType.select(row);
                    }
                }
            }
        } else {
            comSType.append("0x00");
            comSType.select(0);
        }
    }

    private static int getSubTypeInAttrDialog(IntNameBox parent, XdosSubTypeT[] stypes, int selIdx, JComboBox comSType) {
        int stype = 0;
        if (stypes != null) {
            int row = 0;
            for (int idx = 0; stypes[idx].desc != null; idx++) {
                for (int i = stypes[idx].start; i <= stypes[idx].end; i++) {
                    if (selIdx == row) {
                        stype = i;
                        break;
                    }
                    row++;
                }
            }
        }
        return stype;
    }

    @Override
    public boolean isFileValidSize(IntNameBox parent, int size, int[] limit) {
        int limitSize = 0xffff;
        if (limit != null) limit[0] = limitSize;
        return (size <= limitSize);
    }
}
