package l3diskex.ui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;


public class MyMenu extends JMenu {

    /*
     *  Constructor
     *  */
    public MyMenu() {
        super();
    }

    /*
     *  Append overloads
     *  */
    public JMenuItem Append(int id) {
        return Append(id, wxEmptyString, wxEmptyString, Object.wxITEM_NORMAL);
    }

    public JMenuItem Append(int id, String item) {
        return Append(id, item, wxEmptyString, Object.wxITEM_NORMAL);
    }

    public JMenuItem Append(int id, String item, String helpString) {
        return Append(id, item, helpString, Object.wxITEM_NORMAL);
    }

    public JMenuItem Append(int id, String item, String helpString, Object kind) {
        return super.Append(id, ConvItemString(item), helpString, kind);
    }

    public JMenuItem Append(int id, String item, JMenu subMenu) {
        return Append(id, item, subMenu, wxEmptyString);
    }

    public JMenuItem Append(int id, String item, JMenu subMenu, String helpString) {
        return super.Append(id, ConvItemString(item), subMenu, helpString);
    }

    public JMenuItem AppendCheckItem(int id, String item) {
        return AppendCheckItem(id, item, wxEmptyString);
    }

    public JMenuItem AppendCheckItem(int id, String item, String help) {
        return super.AppendCheckItem(id, ConvItemString(item), help);
    }

    public JMenuItem AppendRadioItem(int id, String item) {
        return AppendRadioItem(id, item, wxEmptyString);
    }

    public JMenuItem AppendRadioItem(int id, String item, String help) {
        return super.AppendRadioItem(id, ConvItemString(item), help);
    }

    /*
     *  Static helper – removes accelerator patterns
     *  */
    public static String ConvItemString(String str) {
        String nstr = str;

        // For MacOSX
        // Remove "( &A )" patterns
        java.util.regex.Pattern re1 = java.util.regex.Pattern.compile("\\(\\&[0-9A-Za-z]\\)");
        java.util.regex.Matcher m1 = re1.matcher(nstr);
        while (m1.find()) {
            int st = m1.start();
            int len = m1.end() - m1.start();
            nstr = nstr.substring(0, st) + nstr.substring(st + len);
            m1 = re1.matcher(nstr);  // restart after modification
        }

        // Remove "&A" patterns
        java.util.regex.Pattern re2 = java.util.regex.Pattern.compile("\\&[0-9A-Za-z]");
        java.util.regex.Matcher m2 = re2.matcher(nstr);
        while (m2.find()) {
            int st = m2.start();
            int len = m2.end() - m2.start();
            nstr = nstr.substring(0, st) + nstr.substring(st + len - 1);
            m2 = re2.matcher(nstr);
        }

        // Replace "ALT+F4" with "CTRL+Q" on MacOSX or GTK
        if (/* mac or gtk */ false) {   // placeholder – in real code check OS
            nstr = nstr.replace("ALT+F4", "CTRL+Q");
        }

        return nstr;
    }

    public static final String wxEmptyString = "";
    public static final String wxITEM_NORMAL = "normal";
    public static final String wxITEM_CHECK = "check";
    public static final String wxITEM_RADIO = "radio";
    public static final String wxITEM_NORMAL_CONST = "normal";
    public static final String wxEmptyString_CONST = "";
}
