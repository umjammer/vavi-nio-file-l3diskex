/*
 * @author Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.util.ArrayList;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * DISK BASICのカテゴリ(メーカ毎、OS毎にまとめる)クラス
 */
public class BasicCategory {

    /** */
    public static class DiskBasicCategory {

        private String name;
        private String description;

        public DiskBasicCategory() {
        }

        public DiskBasicCategory(DiskBasicCategory src) {
            this.name = src.name;
            this.description = src.description;
        }

        public DiskBasicCategory(String n_name, String n_description) {
            this.name = n_name;
            this.description = n_description;
        }

        /** カテゴリ名 */
        public String getName() {
            return name;
        }

        /** 説明 */
        public String getDescription() {
            return description;
        }

        /** 説明の設定 */
        public void setDescription(String str) {
            description = str;
        }
    }

    /** */
    public static class DiskBasicCategories {

        private final ArrayList<DiskBasicCategory> list = new ArrayList<>();

        /** DiskBasicCategoryエレメントのロード */
        public boolean load(Node node, String localeName, StringBuilder errmsgs) {
            boolean valid = false;

            /* nodeの検索 */
            while (node != null && !valid) {
                if ("DiskBasicCategories".equals(node.getLocalName())) {
                    valid = true;
                    break;
                }
                node = node.getNextSibling();
            }
            if (!valid) {
                return false;
            }

            /* アイテムの読み込み */
            Node item = node.getFirstChild();
            while (item != null && valid) {
                if ("DiskBasicCategory".equals(item.getLocalName())) {
                    String type_name = ((Element) item).getAttribute("name");
                    String desc = "";
                    String desc_locale = "";

                    Node itemnode = item.getFirstChild();
                    while (itemnode != null) {
                        if ("Description".equals(itemnode.getLocalName())) {
                            if (((Element) itemnode).hasAttribute("lang")) {
                                String lang = ((Element) itemnode).getAttribute("lang");
                                if (localeName.contains(lang)) {
                                    desc_locale = itemnode.getTextContent();
                                }
                            } else {
                                desc = itemnode.getTextContent();
                            }
                        }
                        itemnode = itemnode.getNextSibling();
                    }
                    if (!desc_locale.isEmpty()) {
                        desc = desc_locale;
                    }

                    DiskBasicCategory c = new DiskBasicCategory(type_name, desc);

                    if (find(type_name) == null) {
                        add(c);
                    } else {
                        errmsgs.append("\n");
                        errmsgs.append("Duplicate type name in DiskBasicCategory : ");
                        errmsgs.append(type_name);
                        valid = false;
                        break;
                    }
                }
                item = item.getNextSibling();
            }
            return valid;
        }

        /** カテゴリを検索 */
        public DiskBasicCategory find(String n_category) {
            for (DiskBasicCategory item : list) {
                if (item.getName().equals(n_category)) {
                    return item;
                }
            }
            return null;
        }

        /** カテゴリ名を返す */
        public String getName(int idx) {
            return list.get(idx).getName();
        }

        public void add(DiskBasicCategory c) {
            list.add(c);
        }

        public int count() {
            return list.size();
        }

        public DiskBasicCategory item(int n) {
            return list.get(n);
        }
    }
}
