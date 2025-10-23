package l3diskex.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;

import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_LEAK;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_MISSING;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_NULLEND;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_FIRST;
import static l3diskex.basicfmt.BasicFat.FatAvailability.FAT_AVAIL_USED_LAST;


public class UiDiskFatArea {

    /* ------------------------------------------------------------------ */
    /*  CONSTANTS (portions of the original C++ definitions)              */
    /* ------------------------------------------------------------------ */

    static final int SELECT_FLAG = 0x10000;
    static final int EXTRA_FLAG = 0x20000;

    /* ------------------------------------------------------------------ */
    /*  BASIC STUB CLASSES                                                 */
    /* ------------------------------------------------------------------ */

    /** Minimal stub for UiDiskFrame. */
    static class UiDiskFrame {

        void FatAreaWindowClosed() {
            System.out.println("FAT area window closed");
        }
    }

    /** Minimal representation of a single group entry. */
    public static class DiskBasicGroup {

        int group;

        DiskBasicGroup(int g) {
            this.group = g;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  MAIN FRAME (corresponds to UiDiskFatAreaFrame)                     */
    /* ------------------------------------------------------------------ */
    static class UiDiskFatAreaFrame extends JFrame {

        private final UiDiskFatAreaPanel panel;

        UiDiskFatAreaFrame(UiDiskFrame parent, String title, Dimension size) {
            super(title);

            /* ---- Menu ------------------------------------------------- */
            JMenuBar menuBar = new JMenuBar();
            JMenu fileMenu = new JMenu("File");
            JMenuItem closeItem = new JMenuItem("Close");
            closeItem.addActionListener(e -> dispose());
            JMenuItem exitItem = new JMenuItem("Exit");
            exitItem.addActionListener(e -> dispose());
            fileMenu.add(closeItem);
            fileMenu.add(exitItem);
            menuBar.add(fileMenu);
            setJMenuBar(menuBar);

            /* ---- Panel + ScrollPane ----------------------------------- */
            panel = new UiDiskFatAreaPanel(this);
            JScrollPane scrollPane = new JScrollPane(panel);
            getContentPane().add(scrollPane, BorderLayout.CENTER);

            setSize(size);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            /* ---- Close handling --------------------------------------- */
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    if (parent != null) {
                        parent.FatAreaWindowClosed();
                    }
                }
            });
        }

        /* --------------------------------------------------------------- */
        /*  Public interface used by the original code.                    */
        /* --------------------------------------------------------------- */
        public void SetData(int offset, List<Integer> arr) {
            panel.setData(offset, arr);
        }

        public void ClearData() {
            panel.clearData();
        }

        public void SetGroup(int group_num) {
            panel.setGroup(group_num);
        }

        public void SetGroup(DiskBasicGroups group_items,
                             List<Integer> extra_group_nums) {
            panel.setGroup(group_items, extra_group_nums);
        }

        public void UnsetGroup(DiskBasicGroups group_items,
                               List<Integer> extra_group_nums) {
            panel.unsetGroup(group_items, extra_group_nums);
        }

        public void ClearGroup() {
            panel.clearGroup();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  PANEL (corresponds to UiDiskFatAreaPanel)                         */
    /* ------------------------------------------------------------------ */
    static class UiDiskFatAreaPanel extends JPanel /* implements Scrollable */ {

        private final UiDiskFatAreaFrame frame;

        /* Data handling ------------------------------------------------ */
        private int offset = 0;                     // same as C++ `long` offset
        private List<Integer> datas = new ArrayList<>();

        /* Drawing configuration ---------------------------------------- */
        private final Dimension sq = new Dimension(10, 10); // square size
        private final int margin = 2;
        private final int lpadding = 8;
        private final int rpadding = lpadding + 16;
        private final int ll = 4;                     // see original code

        /* Colors (pens and brushes) ----------------------------------- */
        private final Color[] pens = new Color[FAT_AVAIL_NULLEND.getValue()];
        private final Color[] brushes = new Color[FAT_AVAIL_NULLEND.getValue()];
        private final Color brushSelect = Color.RED;
        private final Color brushExtra = new Color(0xff, 0x00, 0xff);

        /* Constructor -------------------------------------------------- */
        UiDiskFatAreaPanel(UiDiskFatAreaFrame frame) {
            this.frame = frame;
            setBackground(Color.WHITE);

            /* ---- Compute initial preferred size --------------------- */
            int width = lpadding + ll + margin
                    + (sq.width + margin) * 16   // 16 squares in a row
                    + rpadding;
            int height = 240;                     // arbitrary initial height
            setPreferredSize(new Dimension(width, height * 100));

            /* ---- Pens ------------------------------------------------ */
            pens[FAT_AVAIL_FREE.ordinal()] = Color.BLACK;
            pens[FAT_AVAIL_SYSTEM.getValue()] = Color.BLACK;
            pens[FAT_AVAIL_USED.getValue()] = Color.BLACK;
            pens[FAT_AVAIL_USED_FIRST.getValue()] = Color.BLACK;
            pens[FAT_AVAIL_USED_LAST.getValue()] = Color.BLACK;
            pens[FAT_AVAIL_MISSING.getValue()] = Color.GRAY;
            pens[FAT_AVAIL_LEAK.getValue()] = Color.LIGHT_GRAY;

            /* ---- Brushes -------------------------------------------- */
            brushes[FAT_AVAIL_FREE.getValue()] = Color.WHITE;
            brushes[FAT_AVAIL_SYSTEM.getValue()] = Color.GRAY;
            brushes[FAT_AVAIL_USED.getValue()] = Color.CYAN;
            brushes[FAT_AVAIL_USED_FIRST.getValue()] = new Color(0x00, 0xff, 0x80);
            brushes[FAT_AVAIL_USED_LAST.getValue()] = new Color(0x00, 0x80, 0xff);
            brushes[FAT_AVAIL_MISSING.getValue()] = Color.LIGHT_GRAY;
            brushes[FAT_AVAIL_LEAK.getValue()] = new Color(0xc0, 0xff, 0xff);
        }

        /* ---------------------------------------------------------------- */
        /*  Painting routine (simplified version of the original C++ code)  */
        /* ---------------------------------------------------------------- */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D dc = (Graphics2D) g;

            /* ---- Draw the vertical grid lines ----------------------- */
            int step = (sq.width + margin) * 4;
            int pos = 1;
            int x = lpadding;
            int y = lpadding;

            // Vertical lines labelled every 4 positions
            for (int px = lpadding + ll + margin; px < getWidth() - rpadding; px += step) {
                int py0 = y - (pos & 1) * ll;
                int py1 = y + ll;
                dc.setColor(Color.BLACK);
                dc.drawLine(px, py0, px, py1);

                if ((pos & 3) == 1) {
                    String lbl = Integer.toHexString(pos >> 2);
                    FontMetrics fm = dc.getFontMetrics();
                    int py0t = y - fm.getAscent() / 2;
                    dc.drawString(lbl, px + 2, py0t);
                }
                pos++;
            }

            /* ---- Draw the data squares -------------------------------- */
            y += (ll + margin);
            int row = 0;
            x = lpadding;

            for (int i = 0; i < datas.size(); i++) {
                /* wrap to next row if needed */
                if ((x + sq.width + rpadding) > getWidth()) {
                    y += (sq.height + margin);
                    row++;
                    x = lpadding;
                }

                /* ---- Optional row heading -------------------------------- */
                if (x == lpadding && (row % 4) == 0) {
                    int px0 = x - (1 - ((row / 4) & 1)) * ll;
                    int px1 = x + ll;
                    dc.setColor(Color.BLACK);
                    dc.drawLine(px0, y, px1, y);

                    if ((row & 0xf) == 0) {
                        String lbl = Integer.toHexString(row >> 4);
                        FontMetrics fm = dc.getFontMetrics();
                        int px1t = px1 + margin - fm.stringWidth(lbl);
                        dc.drawString(lbl, px1t, y + 2);
                    }
                }
                x += (ll + margin);

                /* ---- Draw the individual square ------------------------ */
                int sts = datas.get(i);
                Color fillColor;
                if ((sts & SELECT_FLAG) != 0) {
                    fillColor = brushSelect;
                } else if ((sts & EXTRA_FLAG) != 0) {
                    fillColor = brushExtra;
                } else if (sts < FAT_AVAIL_NULLEND.getValue()) {
                    fillColor = brushes[sts];
                } else {
                    fillColor = Color.WHITE;
                }

                dc.setColor(fillColor);
                dc.fillRect(x, y, sq.width, sq.height);
                dc.setColor(Color.BLACK);
                dc.drawRect(x, y, sq.width, sq.height);

                /* advance column */
                x += (sq.width + margin);
            }

            /* ---- Update the panel's preferred size for scrolling ----- */
            y += (sq.height + margin);
            setPreferredSize(new Dimension(getWidth(), y));
            revalidate();
        }

        /* ---------------------------------------------------------------- */
        /*  Data manipulation helpers                                         */
        /* ---------------------------------------------------------------- */
        void setData(int offset, List<Integer> arr) {
            if (arr != null) {
                this.offset = offset;
                this.datas = new ArrayList<>(arr);
                repaint();
            }
        }

        void clearData() {
            datas.clear();
            repaint();
        }

        private void setGroupBase(int groupNum, int highlight) {
            int pos = groupNum + offset;
            if (pos < datas.size()) {
                int val = datas.get(pos);
                val |= highlight;
                datas.set(pos, val);
            }
        }

        private void unsetGroupBase(int groupNum) {
            int pos = groupNum + offset;
            if (pos < datas.size()) {
                int val = datas.get(pos);
                val &= 0xffff;
                datas.set(pos, val);
            }
        }

        private void clearGroupBase() {
            for (int i = 0; i < datas.size(); i++) {
                int val = datas.get(i);
                val &= 0xffff;
                datas.set(i, val);
            }
        }

        /* Public API for the frame ------------------------------------- */
        void setGroup(int groupNum) {
            setGroupBase(groupNum, SELECT_FLAG);
            repaint();
        }

        void setGroup(DiskBasicGroups groups, List<Integer> extras) {
            for (DiskBasicGroupItem g : groups.getItems()) {
                setGroupBase(g.group, SELECT_FLAG);
            }
            for (int num : extras) {
                setGroupBase(num, EXTRA_FLAG);
            }
            repaint();
        }

        void unsetGroup(DiskBasicGroups groups, List<Integer> extras) {
            for (DiskBasicGroupItem g : groups.getItems()) {
                unsetGroupBase(g.group);
            }
            for (int num : extras) {
                unsetGroupBase(num);
            }
            repaint();
        }

        void clearGroup() {
            clearGroupBase();
            repaint();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Demo main method (creates a frame with dummy data)                */
    /* ------------------------------------------------------------------ */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            /* Create a dummy parent */
            UiDiskFrame parent = new UiDiskFrame();

            /* Build a frame with a small sample dataset */
            UiDiskFatArea.UiDiskFatAreaFrame frame =
                    new UiDiskFatAreaFrame(parent, "FAT Usage Demo",
                            new Dimension(400, 300));

            /* Sample data: 64 items with random values between 0 and 5 */
            List<Integer> sampleData = new ArrayList<>();
            Random rnd = new Random();
            for (int i = 0; i < 64; i++) {
                int val = rnd.nextInt(FAT_AVAIL_NULLEND.getValue());
                // occasionally add flags
                if (rnd.nextBoolean()) val |= SELECT_FLAG;
                if (rnd.nextBoolean()) val |= EXTRA_FLAG;
                sampleData.add(val);
            }
            frame.SetData(0, sampleData);
            frame.SetGroup(5);  // select a particular group

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
