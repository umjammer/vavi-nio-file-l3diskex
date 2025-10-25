/*
 * @author Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * DISK BASICのカテゴリ(メーカ毎、OS毎にまとめる)クラス
 */
public class DiskBasicCategory {

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

    /**
     * DiskBasicCategoryエレメントのロード
     *
     * @param node       ノード
     * @param localeName ローケル名
     * @param errmsgs    [out] エラー時メッセージ
     * @return true / false
     * @see "category_types.xml"
     */
    public static boolean load(List<DiskBasicCategory> list, Node node, String localeName, StringBuilder errmsgs) {
        boolean valid = false;

        while (node != null && !valid) {
            if ("DiskBasicCategories".equals(node.getNodeName())) {
                valid = true;
                break;
            }
            node = node.getNextSibling();
        }
        if (!valid) {
            return false;
        }

        Node item = node.getFirstChild();
        while (item != null && valid) {
            if ("DiskBasicCategory".equals(item.getNodeName())) {
                String type_name = ((Element) item).getAttribute("name");
                String desc = "";
                String desc_locale = "";

                Node itemnode = item.getFirstChild();
                while (itemnode != null) {
                    if ("Description".equals(itemnode.getNodeName())) {
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

                if (find(list, type_name) == null) {
                    list.add(c);
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

    /**
     * カテゴリを検索
     *
     * @param n_category カテゴリ名
     * @return カテゴリ
     */
    public static DiskBasicCategory find(List<DiskBasicCategory> list, String n_category) {
        for (DiskBasicCategory item : list) {
            if (item.getName().equals(n_category)) {
                return item;
            }
        }
        return null;
    }
}
