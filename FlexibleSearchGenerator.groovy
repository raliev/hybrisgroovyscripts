TYPE = "Product"

dontGoIntoTypes = ["Media"];
dontGoIntoRelations  = false;
dontGoIntoTheRelations = ["Category"];
dontGoIntoAttributes = ["itemtype", "owner"];
strangeRelationTypes = ["ItemDocr", "Item2CockpitItemTemplate", "CommentItem", "ItemSavedValues"];
GoIntoTheRelationTypeAttributes = ["AmwayDAMAsset.languages"];

println createFSComponentsForTheType(TYPE).toString();

/**************************************************************/

import de.hybris.platform.cms2.model.pages.*;
import de.hybris.platform.catalog.model.*;
import de.hybris.platform.core.model.c2l.*;
import de.hybris.platform.cms2.model.contents.contentslot.*;
import de.hybris.platform.cms2.servicelayer.data.*;
import de.hybris.platform.acceleratorcms.data.*;
import de.hybris.platform.cms2.model.contents.components.*;
import de.hybris.platform.catalog.*;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.type.*;
import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.type.CollectionType;
import de.hybris.platform.servicelayer.config.*;
import de.hybris.platform.servicelayer.i18n.*;
import de.hybris.platform.servicelayer.model.*;
import de.hybris.platform.servicelayer.search.*;
import de.hybris.platform.servicelayer.session.*;
import de.hybris.platform.servicelayer.type.*;
import de.hybris.platform.servicelayer.user.*;
import org.apache.log4j.Logger;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.*;
import de.hybris.platform.core.model.type.*;


typeService = spring.getBean("typeService");

FSComponent createFSComponentsForTheType(String typeName, boolean onlyUnique = false, String typeAsPart = "") {
    if (typeAsPart == "" || typeAsPart == null) { typeAsPart = typeName; }
    FSComponent fsComponent = new FSComponent();
    attributes = ListOfAttributesForTheComposedType(typeName);

    for (attribute in attributes) {
        qualifier = attribute.getQualifier();
        if (qualifier in dontGoIntoAttributes) { continue; }
        String type = attribute.getAttributeType();
        if (type in dontGoIntoTypes) { continue; }
        clazz = attribute.getPersistenceClass();
        classType = typeService.getTypeForCode(type);
        if (onlyUnique && !attribute.getUnique()) { continue; }
        String classSimpleName = classType.getClass().getSimpleName();

        if (classSimpleName == "CollectionTypeModel" && attribute.getSearch()) {
            if (attribute.getItemType() != "RelationDescriptor") {
                fsComponent.addToFields(typeAsPart + "." + attribute.getQualifier() );
            } else {
                if ((!dontGoIntoRelations || (typeAsPart + "." + qualifier in GoIntoTheRelationTypeAttributes))) {
                    attrDesc = (typeService.getAttributeDescriptor(typeName, qualifier));
                    if (attrDesc instanceof RelationDescriptorModel && (!dontGoIntoRelations || (typeAsPart + "." + qualifier in GoIntoTheRelationTypeAttributes))) {
                        processRelations(attrDesc, typeAsPart, qualifier, typeName, fsComponent)
                    }
                }

            }
        }
        if (classSimpleName == "EnumerationMetaTypeModel" && attribute.getSearch()) {
            // enums - don't process
            fsComponent.addToFields(typeAsPart+ "." + attribute.getQualifier());
        }
        if (classSimpleName == "MapTypeModel" && attribute.getSearch() ) {
            // maps  - don't process in general, but there is an exception
            attrDesc = (typeService.getAttributeDescriptor(typeName, qualifier));
            if (attrDesc instanceof RelationDescriptorModel && attribute.getItemType() == "RelationDescriptor") {
                if ((!dontGoIntoRelations || (typeAsPart + "." + qualifier in GoIntoTheRelationTypeAttributes)) ) {
                    //relation
                    processRelations(attrDesc, typeAsPart, qualifier, typeName, fsComponent)
                }
            } else {
                // simple map
                // don't process
                fsComponent.addToFields(typeAsPart + "." + attribute.getQualifier());
            }

        }
        if (classSimpleName == "AtomicTypeModel" && attribute.getSearch()) {
            // atomic
            fsComponent.addToFields(typeAsPart+ "." + attribute.getQualifier());
        }
        if (classSimpleName == "ComposedTypeModel" && attribute.getSearch()) {
            uniqueAttributes = [];
            String asPartJoin = typeAsPart+"_"+qualifier+"_"+"Join";
            FSCondition fsCondition = new FSCondition(typeAsPart, qualifier,  asPartJoin, "pk");
            FSJoin fsJoin = new FSJoin(type + " as " + asPartJoin+"", fsCondition);
            subattributes = ListOfAttributesForTheComposedType(type);
            boolean atLeastOneUniqueAttributeIsFound = isAnyUniqueAttributes(subattributes)
            if (!atLeastOneUniqueAttributeIsFound) {
                fsComponent.addToFields(typeName + ".pk");
            } else {
                fsComponent.addToJoins(fsJoin);
                processSubAttributes(subattributes, typeAsPart, qualifier, asPartJoin, fsComponent)
            }
        }
    }
    fsComponent.addToObjects(typeName);
    return fsComponent;
}

void processRelations(RelationDescriptorModel attrDesc, String typeAsPart, qualifier, String typeName, FSComponent fsComponent) {
    relObj = ((RelationDescriptorModel) attrDesc);
    if (relObj != null) {
        relName = relObj.getRelationName();
        String asPartJoin = typeAsPart + "_" + qualifier + "_" + "RelJoin";
        FSCondition fsCondition = new FSCondition(typeName, "pk", asPartJoin, relObj.getIsSource() ? "source" : "target");
        FSJoin fsJoin = new FSJoin(relObj.getRelationName() + " as " + asPartJoin + "", fsCondition);
        fsComponent.addToJoins(fsJoin);
        fsComponent.addToFields(asPartJoin + "." + (relObj.getIsSource() ? "target" : "source"))

        String rightHandtype = relObj.getRelationType().getSourceType().getCode();
        relationClassType = typeService.getTypeForCode(relObj.getRelationType().getSourceType().getCode()).getClass().getSimpleName();

        if (relationClassType == "ComposedTypeModel") {
            subattributes = ListOfAttributesForTheComposedType(rightHandtype);
            String relationAsPartJoin = typeAsPart + "_rel_" + rightHandtype + "_" + qualifier + "_" + "Join";
            FSCondition fsRelationCondition = new FSCondition(asPartJoin, relObj.getIsSource() ? "target" : "source", relationAsPartJoin, "pk");
            FSJoin fsRelationJoin = new FSJoin(rightHandtype + " as " + relationAsPartJoin + "", fsRelationCondition);
            fsComponent.addToJoins(fsRelationJoin)
            List<DescriptorRecord> subattributes = ListOfAttributesForTheComposedType(rightHandtype);
            boolean atLeastOneUniqueAttributeIsFound = isAnyUniqueAttributes(subattributes)
            if (!atLeastOneUniqueAttributeIsFound) {
                fsComponent.addToFields(typeName + ".pk");
            } else {
                fsComponent.addToJoins(fsJoin);
                processSubAttributes(subattributes, typeAsPart, qualifier, relationAsPartJoin, fsComponent)
            }
        }


    }
}

private void processSubAttributes(List<DescriptorRecord> subattributes, String typeAsPart, String qualifier, String asPartJoin, FSComponent fsComponent) {
    for (subattribute in subattributes) {
        if (subattribute.getUnique() && subattribute.getSearch()) {

            if (typeService.getTypeForCode(subattribute.getAttributeType()).getClass().getSimpleName() == "ComposedTypeModel") {
                asPart = typeAsPart + "_" + qualifier + "_" + subattribute.getQualifier() + "_Join";
                FSCondition subcondition = new FSCondition(asPart, "pk", asPartJoin, subattribute.qualifier);
                FSJoin subelementjoin = new FSJoin(subattribute.getAttributeType() + " as " + asPart, subcondition);
                List<FSJoin> joinList = new ArrayList();
                joinList.add(subelementjoin);
                fsComponent.addToJoins(joinList);

                subFS = createFSComponentsForTheType(subattribute.getAttributeType(), true, asPart);
                fsComponent.addToJoins(subFS.getJoins());

                for (field in subFS.getFields()) {
                    fsComponent.addToFields(field)
                }
            } else {
                fsComponent.addToFields(asPartJoin + "." + subattribute.getQualifier());
            }
        }
    }
}

private boolean isAnyUniqueAttributes(List<DescriptorRecord> subattributes) {
    boolean atLeastOneUniqueAttributeIsFound = false;
    for (subattribute in subattributes) {
        if (subattribute.getUnique() && subattribute.getSearch()) {
            atLeastOneUniqueAttributeIsFound = true;
        }
    }
    atLeastOneUniqueAttributeIsFound
}

class FSComponent {
    List<String> fields;
    List<String> objects;
    List<FSCondition> conditions;
    List<FSJoin> joins;

    void addToFields(List<String> fields)
    {
        for (field in fields) { addToFields(field); }
    }
    void addToFields(String field) {
        if (fields == null) { fields = new ArrayList<>(); }
        fields.add(field);
    }
    void addToJoins(FSJoin join) {
        if (joins == null) { joins = new ArrayList<FSJoin>(); }
        joins.add(join);
    }
    void addToObjects(String object) {
        if (objects == null) { objects = new ArrayList<>(); }
        objects.add(object);
    }

    List<String> getFields() {
        return fields
    }

    void setFields(List<String> fields) {
        this.fields = fields
    }

    List<String> getObjects() {
        return objects
    }

    void setObjects(List<String> objects) {
        this.objects = objects
    }

    List<FSCondition> getConditions() {
        return conditions
    }

    void setConditions(List<FSCondition> conditions) {
        this.conditions = conditions
    }

    List<FSJoin> getJoins() {
        return joins
    }

    void setJoins(List<FSJoin> joins) {
        this.joins = joins
    }
    void addToJoins(List<FSJoin> joins) {
        for (join in joins)
        {
            this.joins.add(join);
        }
    }

    @Override
    public String toString() {
        String output = "SELECT \n  ";
        String attributesPart = "";
        for (field in fields) {
            String pieceOfAttribute = "{" + field + "}";
            if (attributesPart.indexOf(pieceOfAttribute) == -1 ) {
                String alias = field.replace("_Join","").replace("__","").replace(".", "_");
                if (attributesPart != "") { attributesPart = attributesPart + ",\n  "; }
                attributesPart = attributesPart + pieceOfAttribute + " as "+alias;
            }
        }
        output = output + attributesPart + "\n";
        String joinsPart = "\n   ";
        for (join in joins) {
            String pieceOfJoin = "LEFT JOIN " + join.getObject() + "\n       ON " + join.getCondition();
            if (joinsPart.indexOf(pieceOfJoin) == -1 ) {
                if (joinsPart != "") { joinsPart = joinsPart + "\n   "; }
                joinsPart = joinsPart + pieceOfJoin;
            }
        }

        output = output + "\nFROM\n  ";
        String objectPart = "";
        for (object in objects) {
            String  pieceOfObject = "{" + object + joinsPart + "}";
            if (objectPart.indexOf(pieceOfObject) == -1) {
                if (objectPart != "") {  objectPart = objectPart + ",\n  "; }
                objectPart = objectPart + pieceOfObject;
            }
        }
        output = output + objectPart + "\n";
        return output;
    }
}
class FSJoin {
    String object;
    FSCondition condition;

    FSJoin(String object, FSCondition condition) {
        this.object = object
        this.condition = condition
    }

}
class FSCondition {
    String leftobject;
    String leftattribute;
    String rightobject;
    String rightattribute;

    FSCondition(String leftobject, String leftattribute, String rightobject, String rightattribute) {
        this.leftobject = leftobject
        this.leftattribute = leftattribute
        this.rightobject = rightobject
        this.rightattribute = rightattribute
    }

    @Override
    public String toString() {
        return "{" + leftobject + "." + leftattribute + "} = {" + rightobject + "." + rightattribute + "}";
    }

}

public class DescriptorRecord {

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getAttributeType() {
        return attributeType;
    }

    String qualifier;
    String databaseColumn;
    String defaultValue;
    String description;
    Class persistenceClass;
    String modifiers;
    String itemType;

    boolean localized;
    boolean optional;
    boolean search;
    boolean partof;
    boolean unique;
    boolean inherited;
    String flags;
    private String attributeType;

    public String getDatabaseColumn() {
        return databaseColumn;
    }

    public void setDatabaseColumn(String databaseColumn) {
        this.databaseColumn = databaseColumn;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPersistanceType() {
        return persistanceType;
    }

    public void setPersistanceType(String persistanceType) {
        this.persistanceType = persistanceType;
    }

    public Class  getPersistenceClass() {
        return persistenceClass;
    }

    public void setPersistenceClass(Class persistenceClass) {
        this.persistenceClass = persistenceClass;
    }

    public String getModifiers() {
        return modifiers;
    }

    public void setModifiers(String modifiers) {
        this.modifiers = modifiers;
    }

    public boolean isLocalized() {
        return localized;
    }

    public void setLocalized(boolean localized) {
        this.localized = localized;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public boolean isPartof() {
        return partof;
    }

    public void setPartof(boolean partof) {
        this.partof = partof;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public void setAttributeType(String attributeType) {
        this.attributeType = attributeType;
    }

    public String getFlags() {
        return flags;
    }

    public void setFlags(String flags) {
        this.flags = flags;
    }

    String getItemType() {
        return itemType
    }

    void setItemType(String itemType) {
        this.itemType = itemType
    }

    boolean getLocalized() {
        return localized
    }

    boolean getOptional() {
        return optional
    }

    boolean getPartof() {
        return partof
    }

    boolean getUnique() {
        return unique
    }

    boolean getInherited() {
        return inherited
    }

    boolean getSearch() {
        return search
    }

    void setSearch(boolean search) {
        this.search = search
    }
}


private List<DescriptorRecord> ListOfAttributesForTheComposedType(String typeName) {
    final ComposedTypeModel type = typeService.getComposedTypeForCode(typeName);
    Collection<AttributeDescriptorModel> inheritedDescriptors = type.getInheritedattributedescriptors();
    Collection<AttributeDescriptorModel> declaredDescriptors = type.getDeclaredattributedescriptors();
    TypeDescriptorsDTO typeDescriptorsDTO = new TypeDescriptorsDTO();
    TypeDescriptorsDTO typeDescriptorsDTO2= new TypeDescriptorsDTO();
    processDescriptors(inheritedDescriptors, typeDescriptorsDTO, true);
    processDescriptors(declaredDescriptors, typeDescriptorsDTO2, false);
    typeDescriptorsDTO.getDescriptorRecordList().addAll(typeDescriptorsDTO2.getDescriptorRecordList());

    List<String> output = new ArrayList();
    for (DescriptorRecord descriptorRecord : typeDescriptorsDTO.getDescriptorRecordList())
    {
        output.add(descriptorRecord);
    }
    return output;
}

public class TypeDescriptorsDTO {
    List<DescriptorRecord> descriptorRecordList;

    public List<DescriptorRecord> getDescriptorRecordList() {
        return descriptorRecordList;
    }

    public void setDescriptorRecordList(List<DescriptorRecord> descriptorRecordList) {
        this.descriptorRecordList = descriptorRecordList;
    }
}


private void processDescriptors(Collection<AttributeDescriptorModel> descriptors, TypeDescriptorsDTO descriptorsDTO, boolean  inherited) {
    List<DescriptorRecord> descriptorRecords = new ArrayList<>();
    for (AttributeDescriptorModel attributeDescriptorModel : descriptors)
    {
        DescriptorRecord descriptorRecord = createDTO(attributeDescriptorModel);
        descriptorRecord.setInherited(inherited);
        descriptorRecords.add(descriptorRecord);
    }
    descriptorsDTO.setDescriptorRecordList(descriptorRecords);
}

private DescriptorRecord createDTO(AttributeDescriptorModel attributeDescriptorModel) {

    DescriptorRecord descriptorRecord = new DescriptorRecord();

    descriptorRecord.setQualifier(attributeDescriptorModel.getQualifier());
    descriptorRecord.setDatabaseColumn(attributeDescriptorModel.getDatabaseColumn());
    descriptorRecord.setDescription(attributeDescriptorModel.getDescription() == null ? "" : attributeDescriptorModel.getDescription());
    descriptorRecord.setLocalized(attributeDescriptorModel.getLocalized());
    descriptorRecord.setOptional(attributeDescriptorModel.getOptional());
    descriptorRecord.setPartof(attributeDescriptorModel.getPartOf());
    descriptorRecord.setSearch(attributeDescriptorModel.getSearch());
    descriptorRecord.setModifiers(attributeDescriptorModel.getModifiers().toString());
    descriptorRecord.setUnique(attributeDescriptorModel.getUnique());
    descriptorRecord.setItemType(attributeDescriptorModel.getItemtype());
    descriptorRecord.setPersistenceClass(attributeDescriptorModel.getPersistenceClass());
    descriptorRecord.setAttributeType(attributeDescriptorModel.getAttributeType().getCode());
    List<String> flags = new ArrayList<>();
    if (descriptorRecord.isUnique()) { flags.add("[UNIQ]"); }
    if (descriptorRecord.isOptional()) { flags.add("[o]"); } else {flags.add("[!]"); }
    descriptorRecord.setFlags(String.join(", ", flags));
    return descriptorRecord;
}



