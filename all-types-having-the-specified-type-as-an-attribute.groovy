import de.hybris.platform.hac.data.dto.SqlSearchResultData;
import de.hybris.platform.hac.facade.impl.DefaultFlexibleSearchFacade;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.type.TypeModel;
import de.hybris.platform.persistence.type.ComposedTypeEJBImpl;
import de.hybris.platform.persistence.type.ComposedTypeRemote;
import de.hybris.platform.jalo.type.ComposedType;


String type      = "AmwayDAMAsset"
String pkObj     = "8796580937778"
types = getSubTypes(type, new ArrayList()) + getSuperTypes(type, new ArrayList());
List<String> queries = new ArrayList<>();
for (String childType in types) {
    queries.addAll(whereThisTypeIsUsed(childType, pkObj));
}
String finalQuery= queries.join("\n UNION \n");
flexibleSearchFacade = new DefaultFlexibleSearchFacade();
result = flexibleSearchFacade.executeRawSql(finalQuery, 2000000, false);

println result.getHeaders().join("\t");

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
            "select EnclosingTypePK, columnName from attributedescriptors where AttributeTypePK = '"+typePK + "' and columnName <> ''";
    ;
    flexibleSearchFacade = new DefaultFlexibleSearchFacade();
    result = flexibleSearchFacade.executeRawSql(query, 2000000, false);
    resultList = [];
    for (item in result.getResultList()) {
        enclosingTypePk = item[0];

        enclosingType = modelService.get(new PK(enclosingTypePk as Long));
        /*
        composedType=de.hybris.platform.jalo.type.TypeManager.getInstance().getComposedType(enclosingType.getClass());
        depl = ((ComposedTypeRemote) ((ComposedTypeEJBImpl) composedType.getImplementation()).getRemote())
                .getDeployment();
        table = depl.getDatabaseTableName();
   	    */
        itemtype = typeService.getTypeForCode(enclosingType.getCode());
        table = itemtype.getTable();
        String query = "SELECT '"+
                    enclosingType.getCode()+
                    "', '"+
                    table+
                    "', '"+
                    item[1]+
                    "', '"+
                    pkObj+
                    "', " +
                    table + ".pk " +
                    "FROM " +  table +
                    " WHERE " +
                    table + "." + item[1] + " = '"+pkObj+"'" +
                    " AND " +
                    " TypePkString = " + item[0];
        if (!resultList.contains(query)) {
            resultList.add(query);
        }

    }
    return resultList;
}



/*println result.getHeaders().join("\t");

for (item in result.getResultList()) {
  println (item.join("\t"));
}
*/