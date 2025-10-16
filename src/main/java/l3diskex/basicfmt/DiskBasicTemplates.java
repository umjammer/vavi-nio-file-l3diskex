/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.util.Comparator;
import java.util.List;

import l3diskex.Parambase.TemplatesBase;
import l3diskex.basicfmt.BasicCategory.DiskBasicCategories;
import l3diskex.basicfmt.BasicCategory.DiskBasicCategory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormats;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicParams;
import l3diskex.diskimg.DiskParam.DiskParamName;


/**
 * DISK BASICパラメータのテンプレートを提供する
 */
public class DiskBasicTemplates extends TemplatesBase {

    /**
     *  Member variables
     */
    private final DiskBasicFormats formats = new DiskBasicFormats();
    private final DiskBasicParams types = new DiskBasicParams();
    private final DiskBasicCategories categories = new DiskBasicCategories();

    /**
     *  Constructor / Destructor
     */
    public DiskBasicTemplates() {
        // Default constructor
    }

    /*
     *  Load method
     */

    /**
     * XMLファイル読み込み
     *
     * @param data_path   XMLファイルがあるフォルダ
     * @param locale_name ローケル名
     * @param errmsgs     エラーメッセージ
     * @return true/false
     */
    public boolean load(String data_path, String locale_name, StringBuilder errmsgs) {
        // The original implementation used wxXmlDocument.
        // Here we just return false because the XML library is not available.
        // This stub preserves the method signature.
        return false;
    }

    /*
     *  FindType overloads
     */

    /**
     * カテゴリとタイプに一致するパラメータを検索
     */
    public DiskBasicParam findType(String n_category, String n_basic_type) {
        return types.find(n_category, n_basic_type);
    }

    /**
     * カテゴリが一致し、タイプリストに含まれるパラメータを検索
     */
    public DiskBasicParam findType(String n_category, List<DiskParamName> n_basic_types) {
        return types.find(n_category, n_basic_types);
    }

    /**
     * カテゴリ、タイプ、サイド数とセクタ数が一致するパラメータを検索
     */
    public DiskBasicParam findType(String n_category, String n_basic_type, int n_sides, int n_sectors) {
        return types.find(n_category, n_basic_type, n_sides, n_sectors);
    }

    /*
     *  FindTypes
     */

    /**
     * DISK BASICフォーマット種類に一致するタイプを検索
     */
    public int findTypes(List<Integer> n_format_types, DiskBasicParams n_types) {
        return types.findTypes(n_format_types, n_types);
    }

    /**
     *  FindTypeNames by index
     */
    public int findTypeNames(int n_category_index, List<String> n_type_names) {
        return types.findNames(categories.getName(n_category_index), n_type_names);
    }

    /**
     *  FindTypeNames by name
     */
    public int findTypeNames(String n_category_name, List<String> n_type_names) {
        return types.findNames(n_category_name, n_type_names);
    }

    /**
     *  FindFormat
     */
    public DiskBasicFormat findFormat(DiskBasicFormatType format_type) {
        return formats.find(format_type);
    }

    /**
     *  FindParams
     */
    public int findParams(List<DiskParamName> n_type_names, DiskBasicParams params) {
        for (int n = 0; n < n_type_names.size(); n++) {
            DiskBasicParam param = findType("", n_type_names.get(n).getName());
            if (param == null) continue;
            params.content.add(param);
        }
        params.content.sort(Comparator.comparing(DiskBasicParam::getBasicDescription));
        return params.content.size();
    }

    /**
     *  FindCategory
     */
    public DiskBasicCategory findCategory(String n_category) {
        return categories.find(n_category);
    }

    /**
     *  GetCategoryName
     */
    public String getCategoryName(int idx) {
        return categories.getName(idx);
    }

    /**
     *  Global instance
     */
    public static final DiskBasicTemplates gDiskBasicTemplates = new DiskBasicTemplates();
}
