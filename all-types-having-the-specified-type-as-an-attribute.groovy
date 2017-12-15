/* (c) 2017 Rauf Aliev, http://hybrismart.com */

String pkObj     = "8796141715457"
type 		 = "Product";

/****************************************************** */
import de.hybris.platform.hac.data.dto.SqlSearchResultData;
import de.hybris.platform.hac.facade.impl.DefaultFlexibleSearchFacade;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.type.TypeModel;
import de.hybris.platform.persistence.type.ComposedTypeEJBImpl;
import de.hybris.platform.persistence.type.ComposedTypeRemote;
import de.hybris.platform.jalo.type.ComposedType;


obj = modelService.get(new PK(pkObj as Long));
types = []
types.add(type);
List<String> queries = new ArrayList<>();
for (String childType in types) {
    queries.addAll(whereThisTypeIsUsed(childType, pkObj));
}
String finalQuery= queries.join("\n UNION \n").replace("<condition>","").replace("</condition>","");
flexibleSearchFacade = new DefaultFlexibleSearchFacade();
result = flexibleSearchFacade.executeRawSql(finalQuery, 2000000, false);

for (item in result.getResultList()) {
    println (item.join("\t"));
}

List<String> getSuperTypes(String type, List<String> eSuperTypes)
{

    TypeModel typeObj = typeService.getTypeForCode(type);
    if (typeObj.getClass().getSimpleName().contains("ComposedType")) {
        superType = typeObj.getSuperType();
        if (superType) {
            if (superType.getCode() != "GenericItem")  {
                eSuperTypes.add(superType.getCode());
                getSuperTypes(superType.getCode(), eSuperTypes);
            }
        }
    }
    return eSuperTypes;
}
List<String> getSubTypes (String type, List<String> eSubTypes)
{
    TypeModel typeObj = typeService.getTypeForCode(type);
    if (typeObj.getClass().getSimpleName().contains("ComposedType")) {
        subTypes = typeObj.getAllSubTypes();
        for (subType in subTypes)
        {
            eSubTypes.add(subType.getCode());
            getSubTypes(subType.getCode(), eSubTypes);
        }
    }
    return eSubTypes;
}

List<String> whereThisTypeIsUsed (String requestedType, String pkObj) {

    typeService = spring.getBean("typeService");
    typePK = typeService.getTypeForCode(requestedType).getPk();

    SqlSearchResultData searchResult;
    query =
            "select OwnerPkString, columnName from attributedescriptors where AttributeTypePK = '"+typePK + "' and columnName <> ''";
    ;
    flexibleSearchFacade = new DefaultFlexibleSearchFacade();
    result = flexibleSearchFacade.executeRawSql(query, 2000000, false);
    resultList = [];
    for (item in result.getResultList()) {
        enclosingTypePk = item[0];
        enclosingType = modelService.get(new PK(enclosingTypePk as Long));
        if (enclosingType) {
            itemtype = typeService.getTypeForCode(enclosingType.getCode());
            if (itemtype && !itemtype.getAbstract()) {
                table = itemtype.getTable();

                blacklistTables = ["attributedescriptors", "maptypes", "collectiontypes", "widgetparameter", "savedvalues", "savedqueries"]
                blacklistAttrs  = ["TypePkString"]
                if (table
                        && !blacklistTables.contains(table)
                        && !blacklistAttrs.contains(item[1])) {
                    String select = query = "SELECT '" +
                            enclosingType.getCode() +
                            "', '" +
                            table;
                    String condition = "\n       (" + table + "." + item[1] + " = '" + pkObj + "'" +
                            " AND " +
                            " TypePkString = " + item[0] + ")";
                    String select2 =
                            "', \"<condition>" +
                                    condition.replace("\n","").trim() +
                                    "</condition>\", " +
                                    table + ".pk ";
                    String from =
                            "\n    FROM " + table +
                                    "\n    WHERE ";

                    String query = select + select2 + from + condition;
                    fired = 0;
                    if (!resultList.contains(query)) {
                        for (Integer i=0; i<resultList.size(); i++) {
                            element = resultList.get(i);
                            if (element.indexOf(from) != -1) {
                                element = element.replace("</condition>", " OR "+condition.replace("\n", "").trim() +"</condition>")
                                resultList.set(i, element + " OR " + condition)
                                fired = 1;
                            }
                        }
                        if (fired == 0) {
                            resultList.add(query);
                        }
                    }
                }
            }
        }

    }
    return resultList;
}



/*println result.getHeaders().join("\t");

for (item in result.getResultList()) {
  println (item.join("\t"));
}
*/
