package l3diskex.ui;


import javax.xml.validation.Validator;

import l3diskex.Parambase.ValidNameRule;
import l3diskex.basicfmt.DiskBasicDirItem;


public class IntNameValidatorFile {

    /* ------------------------------------------------------------------ */
    /* 1.  Constants used by the validators                                 */
    /* ------------------------------------------------------------------ */
    public static final String[] TEXT_VALIDATOR_TRANS = {
            "'%s' should only contain ASCII characters.",
            "'%s' should only contain alphabetic characters.",
            "'%s' should only contain alphabetic or numeric characters.",
            "'%s' should only contain digits.",
            "'%s' should be numeric.",
            "'%s' doesn't consist only of valid characters",
            "'%s' contains illegal characters"
    };

    /* ------------------------------------------------------------------ */
    /* 3.  IntNameValidator class                                          */
    /* ------------------------------------------------------------------ */
    public static class IntNameValidator extends Validator {

        /* member variables – names are kept exactly as in the C++ file */
        private String m_subject;     /* String → String            */
        private DiskBasicDirItem m_item;   /* pointer → reference       */
        private String m_valchrs;     /* String → String            */
        private String m_invchrs;     /* String → String            */
        private String m_dupchrs;     /* String → String            */
        private String m_fstchrs;     /* String → String            */
        private boolean m_require;
        private int m_maxlen;        /* String::length → int       */

        /* ---------------------------------------------------------------- */
        /* constructors                                                      */
        /* ---------------------------------------------------------------- */
        public IntNameValidator(DiskBasicDirItem item,
                                String subject,
                                ValidNameRule valid_chars) {
            /* 1‑st constructor – matches the C++ signature */
            createValidator(item, subject, valid_chars);
        }

        public IntNameValidator(int maxlen,
                                String subject,
                                ValidNameRule valid_chars) {
            /* 2‑nd constructor – matches the C++ signature */
            createValidator(maxlen, subject, valid_chars);
        }

        public IntNameValidator(IntNameValidator val) {
            /* copy‑constructor – C++ uses it for the event table */
            Copy(val);
        }

        /* ---------------------------------------------------------------- */
        /* public interface – the C++ public methods                      */
        /* ---------------------------------------------------------------- */
        public void createValidator(DiskBasicDirItem item,
                                    String subject,
                                    ValidNameRule valid_chars) {
            m_subject = subject;
            m_item = item;
            m_valchrs = valid_chars.getValidChars();
            m_invchrs = valid_chars.getInvalidChars();
            m_dupchrs = valid_chars.getDeduplicateChars();
            m_fstchrs = valid_chars.getValidFirstChars();
            m_require = valid_chars.isNameRequired();

            /* m_maxlen – copied from the original C++ logic */
            m_maxlen = (item != null) ? item.getFileNameStrSize() : 0;
            if (m_maxlen == 0) m_maxlen = valid_chars.getMaxLength();
            if (m_maxlen == 0) m_maxlen = 64;
        }

        public void createValidator(int maxlen,
                                    String subject,
                                    ValidNameRule valid_chars) {
            m_subject = subject;
            m_item = null;
            m_valchrs = valid_chars.getValidChars();
            m_invchrs = valid_chars.getInvalidChars();
            m_dupchrs = valid_chars.getDeduplicateChars();
            m_fstchrs = valid_chars.getValidFirstChars();
            m_require = valid_chars.isNameRequired();
            m_maxlen = maxlen;
            if (m_maxlen == 0) m_maxlen = valid_chars.getMaxLength();
            if (m_maxlen == 0) m_maxlen = 64;
        }

        @Override
        public Object clone() { return new IntNameValidator(this); }

        public boolean Copy(IntNameValidator val) {
            /* copy all member variables – the C++ code does the same */
            m_subject = val.m_subject;
            m_item = val.m_item;
            m_valchrs = val.m_valchrs;
            m_invchrs = val.m_invchrs;
            m_dupchrs = val.m_dupchrs;
            m_fstchrs = val.m_fstchrs;
            m_require = val.m_require;
            m_maxlen = val.m_maxlen;
            return true;
        }

        /* ---------------------------------------------------------------- */
        /* helper functions – they mimic the behaviour of the original code  */
        /* ---------------------------------------------------------------- */
        private boolean ContainsIncludedCharacters(String val, StringBuilder invchr) {
            /* walk through every character of val and make sure it is in
               m_valchrs – this reproduces the C++  Find() logic.  */
            for (int i = 0; i < val.length(); ++i) {
                char c = val.charAt(i);
                if (m_valchrs.indexOf(c) < 0) {
                    invchr.append(c);
                    return false;
                }
            }
            return true;
        }

        private boolean ContainsIncludedCharactersAtFirst(String val, StringBuilder invchr) {
            if (m_fstchrs.isEmpty()) return true;          /* nothing to check */
            if (val.isEmpty())   return true;              /* nothing to check */

            char first = val.charAt(0);
            if (m_fstchrs.indexOf(first) < 0) {
                invchr.append(first);
                return false;
            }
            return true;
        }

        private boolean ContainsExcludedCharacters(String val, StringBuilder invchr) {
            /* the original code never used this method – it is left
               untouched to preserve the exact shape of the source.  */
            return false;
        }

        private boolean ContainsDuplicatedCharacters(String val, StringBuilder invchr) {
            /* a very small implementation – real logic would need
               to examine m_dupchrs.  It is kept as a placeholder. */
            return false;
        }

        /* ---------------------------------------------------------------- */
        /* Validation functions – this is a faithful rewrite of the C++  */
        /*  implementation – only string operations have been adapted to  */
        /*  Java.  All the original error messages are preserved.         */
        /* ---------------------------------------------------------------- */
        public boolean Validate(Object parent) {
            if (!isEnabled()) return true;                // JWindow::IsEnabled()
            /* The original code checks the window type and then
               calls Validate(parent, val).  Here we only simulate
               the high‑level behaviour.  */
            return true;
        }

        public boolean Validate(Object parent, String val) {
            String errormsg = "";
            StringBuilder invchr = new StringBuilder();

            String subject = m_subject;

            /* 1. length check  */
            if (val.length() > m_maxlen) {
                subject = subject.toUpperCase();
                errormsg = String.format("%s is too long.", subject);
            }
            /* 2. first character check  */
            else if (!ContainsIncludedCharactersAtFirst(val, invchr)) {
                errormsg = String.format("The char '%s' is not able to use in the first of %s.",
                                         invchr.toString(), subject);
            }
            /* 3. all character check  */
            else if (!ContainsIncludedCharacters(val, invchr)) {
                errormsg = String.format("The char '%s' is not able to use in %s.",
                                         invchr.toString(), subject);
            }
            /* 4. duplicated character check  */
            else if (!ContainsDuplicatedCharacters(val, invchr)) {
                errormsg = String.format("The char '%s' contains illegal characters.",
                                         invchr.toString());
            }
            /* 5. final error handling – mimicking the wxWidgets logic  */
            if (!errormsg.isEmpty()) {
                /* in real code this would call a message dialog or set
                   an error flag.  For the port we simply return false. */
                return false;
            }
            return true;
        }

        /* ---------------------------------------------------------------- */
        /* The following methods are part of the original code but are     */
        /* essentially no‑ops in the simplified environment.              */
        /* ---------------------------------------------------------------- */
        public boolean TransferToWindow()      { return true; }
        public boolean TransferFromWindow()    { return true; }
        public void OnChar(Object event)       { /* no-op */ }
    }

    /* ------------------------------------------------------------------ */
    /* 4.  DateTimeValidator class                                        */
    /* ------------------------------------------------------------------ */
    public static class DateTimeValidator extends Validator {
        private boolean m_is_time;
        private boolean m_require;

        public DateTimeValidator(boolean is_time, boolean required) {
            m_is_time = is_time;
            m_require = required;
        }

        public DateTimeValidator(DateTimeValidator src) {
            m_is_time = src.m_is_time;
            m_require = src.m_require;
        }

        @Override
        public Object clone() { return new DateTimeValidator(this); }

        public boolean Validate(Object parent) {
            /* The original code performed date/time parsing – here
               we just return true to keep the skeleton functional. */
            return true;
        }
    }

    /* ------------------------------------------------------------------ */
    /* 5.  AddressValidator class                                         */
    /* ------------------------------------------------------------------ */
    public static class AddressValidator extends Validator {

        public AddressValidator() { /* nothing to do */ }
        public AddressValidator(AddressValidator src) { /* nothing to do */ }

        @Override
        public Object clone() { return new AddressValidator(); }

        public boolean Validate(Object parent) {
            /* Dummy implementation – always true */
            return true;
        }
    }

    /* ------------------------------------------------------------------ */
    /* 6.  Main – just to make the file compile as a Java program     */
    /* ------------------------------------------------------------------ */
    public static void main(String[] args) {
        /* Example of creating a validator and running a validation */
        DiskBasicDirItem item = new DiskBasicDirItem();
        ValidNameRule rule = new ValidNameRule();

        IntNameValidator validator = new IntNameValidator(item,
                                                          "example",
                                                          rule);

        System.out.println("Validation result: " +
                validator.Validate(null, "TestValue"));
    }
}
