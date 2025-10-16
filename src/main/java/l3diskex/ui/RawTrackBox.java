/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.ui;

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/*  */
public class RawTrackBox extends JDialog {

    public RawTrackBox(Frame parent, int id, int num, int offset, DiskImageDisk disk) {
        super(parent, "Track Information", true); // modal dialog

        // Main panel – vertical box layout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(mainPanel);

        // --- Grid panel ------------------------------------------------
        JPanel gridPanel = new JPanel();
        gridPanel.setLayout(new GridLayout(0, 2, 4, 4));   // 2 columns, variable rows

        // --- Helpers ---------------------------------------------------
        DiskImageTrack track = disk.getTrackByOffset(offset);
        String labelText, valueText;
        int val;
        int longVal;

        // Number
        addLabel(gridPanel, "Number :");
        addValue(gridPanel, "#" + num);

        // Track Number
        addLabel(gridPanel, "Track Number :");
        if (track != null) {
            val = track.getTrackNumber();
            valueText = Integer.toString(val);
        } else {
            valueText = "--";
        }
        addValue(gridPanel, valueText);

        // Side Number
        addLabel(gridPanel, "Side Number :");
        if (track != null) {
            val = track.getSideNumber();
            valueText = Integer.toString(val);
        } else {
            valueText = "--";
        }
        addValue(gridPanel, valueText);

        // Offset
        addLabel(gridPanel, "Offset :");
        longVal = offset;
        valueText = Long.toString(longVal) + " (0x" + Long.toHexString(longVal) + ")";
        addValue(gridPanel, valueText);

        // Number of Sectors
        List<DiskImageSector> sectors = (track != null) ? track.getSectors() : null;
        int ssCount = (sectors != null) ? sectors.count() : 0;
        addLabel(gridPanel, "Number of Sectors :");
        addValue(gridPanel, Integer.toString(ssCount));

        // Total Size of Sectors
        int sectorTotalSize = 0;
        if (sectors != null) {
            for (int i = 0; i < ssCount; i++) {
                sectorTotalSize += sectors.getItem(i).getSize();
            }
        }
        addLabel(gridPanel, "Total Size of Sectors :");
        addValue(gridPanel, Integer.toString(sectorTotalSize));

        // Extra Data Size
        addLabel(gridPanel, "Extra Data Size :");
        if (track != null) {
            addValue(gridPanel, Integer.toString(track.getExtraDataSize()));
        } else {
            addValue(gridPanel, "0");
        }

        // Total Size of This Track
        addLabel(gridPanel, "Total Size of This Track :");
        if (track != null) {
            addValue(gridPanel, Integer.toString(track.getSize()));
        } else {
            addValue(gridPanel, "0");
        }

        // Add the grid panel to the main panel
        mainPanel.add(gridPanel);

        // --- Button panel ---------------------------------------------
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);

        mainPanel.add(buttonPanel);

        // Size and location
        pack();
        setLocationRelativeTo(parent);
    }

    /* ---------------------------------------------------------------- */
    /*  ShowModal – shows the dialog and returns an int that mimics
     *  the wxDialog::ShowModal() return value.  In Swing we simply
     *  return 0 (OK) after the dialog is closed.
     * ---------------------------------------------------------------- */
    public int ShowModal() {
        setVisible(true);      // modal
        return 0;              // mimicking wxID_OK
    }

    /* ---------------------------------------------------------------- */
    /*  Helper methods for adding labels / values to the grid
     * ---------------------------------------------------------------- */
    private void addLabel(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(label);
    }

    private void addValue(JPanel panel, String text) {
        JLabel value = new JLabel(text);
        value.setHorizontalAlignment(SwingConstants.LEFT);
        panel.add(value);
    }

    /* ---------------------------------------------------------------- */
    /*  Main method – for quick manual testing of the dialog
     * ---------------------------------------------------------------- */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Frame dummyFrame = new Frame(); // dummy parent
            DiskImageDisk disk = new DiskImageDisk();
            RawTrackBox dlg = new RawTrackBox(dummyFrame, 0, 3, 0x12345, disk);
            dlg.ShowModal();
        });
    }
}
