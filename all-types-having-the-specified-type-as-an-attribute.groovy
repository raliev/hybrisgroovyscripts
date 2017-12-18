/* (c) 2017 Rauf Aliev, http://hybrismart.com */

//String pkObj     = "8797136093214"
//String pkObj = "8796695330846";
//String pkObj = "8797134192670";
//String pkObj = "8796147122318"
String pkObj = "8797134192670"
//type 		 = "Category"  ;

/****************************************************** */
DEBUG = 1;
import de.hybris.platform.hac.data.dto.SqlSearchResultData;
import de.hybris.platform.hac.facade.impl.DefaultFlexibleSearchFacade;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.type.TypeModel;

if (!hasProperty('type')) { type = modelService.get(new PK(pkObj as Long)).itemtype as String; }

types = getSubTypes(type, new ArrayList()) + getSuperTypes(type, new ArrayList());
types.add(type);
List<String> queries = new ArrayList<>();
for (String childType in types) {
    if (DEBUG) { println ("type="+ childType )}
    queries.addAll(whereThisTypeIsUsed(childType, pkObj));
}
String finalQuery= queries.join("\n UNION \n").replace("<condition>","").replace("</condition>","");
flexibleSearchFacade = new DefaultFlexibleSearchFacade();
if (DEBUG) { println "Final Query: \n"+finalQuery; }
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
    requestedItemType = typeService.getTypeForCode(requestedType);
    typePK = requestedItemType.getPk();

    SqlSearchResultData searchResult;
    query = /select OwnerPkString, columnName, 0, 0 from attributedescriptors where (AttributeTypePK ='/+typePK + /'  and columnName <> '')
    union
    select attributedescriptors.OwnerPkString,  attributedescriptors.columnName, 1, 0
    from attributedescriptors, collectiontypes as a 
    where
      (a.PK = attributedescriptors.AttributeTypePK  
       and a.elementTypePk = '/+typePK+/'
       and columnName <> ""
       )
       union
   select distinct a.p_targettype, attributedescriptors.columnName, 2, ydeployments.TableName
 from attributedescriptors, composedtypes as a
 left join ydeployments on a.itemtypecode  = ydeployments.typecode 
    where
      (attributedescriptors.p_relationname=a.InternalCode 
       and a.p_sourcetype =  '/+typePK+/'          
       and columnName is null
       and tableName is not null
       )
union       
select distinct  attributedescriptors.OwnerPkString, attributedescriptors.columnName, 3, ydeployments.TableName
 from attributedescriptors, composedtypes as a
 left join ydeployments on a.itemtypecode  = ydeployments.typecode 
    where
      (attributedescriptors.p_relationname=a.InternalCode 
       and a.p_sourcetype = '/+typePK+/'       
       and columnName is not null       
       )      

     union
   select distinct a.p_sourcetype, attributedescriptors.columnName, "2_", ydeployments.TableName
 from attributedescriptors, composedtypes as a
 left join ydeployments on a.itemtypecode  = ydeployments.typecode 
    where
      (attributedescriptors.p_relationname=a.InternalCode 
       and a.p_targettype =  '/+typePK+/'          
       and columnName is null
       and tableName is not null
       )
union       
select distinct  attributedescriptors.OwnerPkString, attributedescriptors.columnName, "3_", ydeployments.TableName
 from attributedescriptors, composedtypes as a
 left join ydeployments on a.itemtypecode  = ydeployments.typecode 
    where
      (attributedescriptors.p_relationname=a.InternalCode 
       and a.p_targettype = '/+typePK+/'       
       and columnName is not null       
       )  
        
       
       /
    flexibleSearchFacade = new DefaultFlexibleSearchFacade();
    result = flexibleSearchFacade.executeRawSql(query, 2000000, false);
    if (DEBUG) { println "query=" + query; }
    resultList = [];
    for (item in result.getResultList()) {
        enclosingTypePk = item[0];
        enclosingType = modelService.get(new PK(enclosingTypePk as Long));
        if (enclosingType) {
            itemtype = typeService.getTypeForCode(enclosingType.getCode());
            boolean abstr = true;
            collectiontype = 0;
            String field = item[1];
            if (item[2] == "1") {
                abstr = false;
                table = requestedItemType.getTable();
                collectiontype = 1;
            }
            if (item[2] == "0" || item[2] == "3" || item[2] == "3_") {
                if (itemtype.getClass().getSimpleName().contains("ComposedType")) {
                    if (itemtype.getAbstract()) {
                        abstr = true;
                    } else {
                        abstr = false; table = itemtype.getTable();
                    }
                }
            }
            if (item[2] == "2" ) {
                table = item[3];
                field = "SourcePK";
                abstr = false;
            }
            if (item[2] == "2_" ) {
                table = item[3];
                field = "TargetPK";
                abstr = false;
            }
            if (itemtype && !abstr) {
                blacklistTables = ["attributedescriptors", "maptypes", "collectiontypes", "widgetparameter", "savedvalues", "savedqueries"]
                blacklistAttrs  = ["TypePkString"]
                if (table
                        && !blacklistTables.contains(table)
                        && !blacklistAttrs.contains(item[1])) { /* not for collections!*/

                    String select = query = "SELECT '" +enclosingType.getCode() + "'," +
                            "'" + table + "',";
                    String condition = "";
                    String from = "";
                    if (collectiontype) {
                            condition = "\n       (" + enclosingType.getTable() + "." + item[1] + " LIKE '%" + pkObj + "%')";
                            from =      "\n    FROM " + enclosingType.getTable() +
                                        "\n    WHERE ";
                            select3 = enclosingType.getTable() + ".pk ";
                    } else {
                            condition = "\n       (" + table + "." + field + " = '" + pkObj + "'";
                            if (item[2] != "2" && item[2] != "2_") {
                                condition = condition +
                                        " AND " +
                                    " TypePkString = " + item[0] ;
                                }
                                condition = condition + ")"
                            from =
                                "\n    FROM " + table +
                                        "\n    WHERE ";
                            select3 = table + ".pk ";
                    }
                    String select2 =
                            "\"<condition>" +
                            condition.replace("\n","").trim() +
                            "</condition>\", ";\

                    String query = select + select2 + select3 + from + condition;
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
