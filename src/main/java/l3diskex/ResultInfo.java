/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import javax.swing.JOptionPane;


/**
 * An abstract class for storing results.
 */
public abstract class ResultInfo {

    private static final Logger logger = System.getLogger(ResultInfo.class.getName());

    /** result level (0:ok, -1:error, 1:warning, 2:info) */
    protected int valid;

    /** all messages */
    protected List<String> messages;

    /** buffer to store fetched messages */
    protected List<String> buffers;

    /** default constructor */
    public ResultInfo() {
        messages = new ArrayList<>();
        buffers = new ArrayList<>();
        clear();
    }

    /** copy constructor */
    public ResultInfo(ResultInfo src) {
        this.valid = src.valid;
        this.messages = new ArrayList<>(src.messages);
        this.buffers = new ArrayList<>();
    }

    /** Clear method */
    public void clear() {
        valid = 0;
        messages.clear();
        buffers.clear();
    }

    /** @after {@link #valid} becomes -1 */
    public void setError(int errorNumber, Object... args) {
        setErrorV(errorNumber, args);
    }

    /** @after wben {@link #valid} is 0, it becomes 1 */
    public void setWarn(int errorNumber, Object... args) {
        setWarnV(errorNumber, args);
    }

    /** @after wben {@link #valid} is 0, it becomes 2 */
    public void setInfo(int errorNumber, Object... args) {
        setInfoV(errorNumber, args);
    }

    /** @after {@link #valid} becomes -1 */
    public void setErrorV(int errorNumber, Object[] args) {
        setMessageV(errorNumber, args);
        valid = -1;
    }

    /** @after wben {@link #valid} is 0, it becomes 1 */
    public void setWarnV(int errorNumber, Object[] args) {
        setMessageV(errorNumber, args);
        if (valid == 0) valid = 1;
    }

    /** @after wben {@link #valid} is 0, it becomes 2 */
    public void setInfoV(int errorNumber, Object[] args) {
        setMessageV(errorNumber, args);
        if (valid == 0) valid = 2;
    }

    /** An abstract method that delegates message generation to a derived class. */
    public abstract void setMessageV(int errorNumber, Object[] args);

    /** GetMessages (for array) */
    public void getMessages(List<String> result) {
        result.clear();
        result.addAll(messages);
    }

    /** GetMessages (for buffer) */
    public List<String> getMessages(int maxRow) {
        buffers.clear();
        Level level = (valid < 0) ? Level.ERROR : Level.INFO;

        if (valid < 2) {
            for (String message : messages) {
                logger.log(level, message);
            }
        }

        if (maxRow >= 0) {
            int count = messages.size();
            for (int i = 0; i < Math.min(maxRow, count); i++) {
                buffers.add(messages.get(i));
            }
            if (maxRow < count) {
                buffers.add(String.format(Locale.getDefault(), "And have more %d messages...", count - maxRow));
            }
            return buffers;
        } else {
            return messages;
        }
    }

    /** Show result dialog */
    public void show() {
        showMessage(getValid(), messages);
    }

    /** Show result dialog (static) */
    public static void showMessage(int level, List<String> messages) {
        StringJoiner sj = new StringJoiner("\n");
        for (String m : messages) {
            sj.add(m);
        }
        String message = sj.toString();
        if (message.isEmpty()) return;

        String caption;
        int icon;

        if (level < 0) {
            caption = "Error";
            icon = JOptionPane.ERROR_MESSAGE;
        } else if (level == 1) {
            caption = "Warning";
            icon = JOptionPane.WARNING_MESSAGE;
        } else {
            caption = "Information";
            icon = JOptionPane.INFORMATION_MESSAGE;
        }

        JOptionPane.showMessageDialog(null, message, caption, icon);
    }

    /** Show message dialog (static) */
    public static int showErrWarnMessage(int code, List<String> messages) {
        StringJoiner sj = new StringJoiner("\n");
        for (String m : messages) {
            sj.add(m);
        }
        String message = sj.toString();
        if (message.isEmpty()) return 0;

        String caption;
        int optionType = JOptionPane.OK_OPTION;
        int icon = JOptionPane.INFORMATION_MESSAGE;

        if (code < 0) {
            caption = "Error";
            icon = JOptionPane.ERROR_MESSAGE;
        } else if (code > 0) {
            caption = "Warning";
            message += "\nDo you want to continue?";
            optionType = JOptionPane.YES_NO_OPTION;
            icon = JOptionPane.WARNING_MESSAGE;
        } else {
            caption = "Information";
            icon = JOptionPane.INFORMATION_MESSAGE;
        }

        int ans = JOptionPane.showConfirmDialog(null, message, caption, optionType, icon);

        return (code == 0 || ans == JOptionPane.YES_OPTION) ? 0 : -1;
    }

    /** */
    public void setValid(int val) {
        this.valid = val;
    }

    public int getValid() {
        return valid;
    }
}
