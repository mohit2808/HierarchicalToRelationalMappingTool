package org.example;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.*;

public class XSDToSQLConverter {
    public static List<String> parseXSD(String xsdFilePath) {
        List<String> tableDefinitions = new ArrayList<>();
        List<String> hierarchyInserts = new ArrayList<>();
        Set<String> entityNames = new HashSet<>();
        List<String> hierarchyEntities = new ArrayList<>();
        Map<String, String> parentChildMap = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(xsdFilePath));

            NodeList entities = document.getElementsByTagName("Enitity");

            // Create entity tables
            for (int i = 0; i < entities.getLength(); i++) {
                Element entity = (Element) entities.item(i);
                String tableName = entity.getAttribute("Name");
                entityNames.add(tableName);
                hierarchyEntities.add(tableName);

                StringBuilder tableSQL = new StringBuilder("CREATE TABLE " + tableName + " (id INT AUTO_INCREMENT PRIMARY KEY, ");
                NodeList attributes = entity.getElementsByTagName("attribute");

                for (int j = 0; j < attributes.getLength(); j++) {
                    Element attr = (Element) attributes.item(j);
                    tableSQL.append(attr.getAttribute("Name")).append(" ")
                            .append(mapXSDTypeToSQL(attr.getAttribute("type"))).append(", ");
                }

                if (attributes.getLength() > 0)
                    tableSQL.setLength(tableSQL.length() - 2);
                else
                    tableSQL.setLength(tableSQL.length() - 1);

                tableSQL.append(");");
                tableDefinitions.add(tableSQL.toString());
            }

            // Build the entity hierarchy relationships
            for (int i = 0; i < entities.getLength(); i++) {
                Element entity = (Element) entities.item(i);
                String source = entity.getAttribute("Name");
                NodeList relations = entity.getElementsByTagName("relation");

                for (int j = 0; j < relations.getLength(); j++) {
                    Element relation = (Element) relations.item(j);
                    String target = relation.getAttribute("target");
                    parentChildMap.put(target, source);
                }
            }

            // Find the leaf entity (one with no children)
            String leafEntity = null;
            for (String entity : hierarchyEntities) {
                if (!parentChildMap.containsValue(entity)) {
                    leafEntity = entity;
                    break;
                }
            }

            if (leafEntity != null) {
                // Build the complete hierarchy path from leaf to root
                List<String> hierarchyPath = new ArrayList<>();
                hierarchyPath.add(leafEntity);

                String current = leafEntity;
                while (parentChildMap.containsKey(current)) {
                    current = parentChildMap.get(current);
                    hierarchyPath.add(current);
                }

                // Create relationship table based on the hierarchy
                StringBuilder relationshipSQL = new StringBuilder("CREATE TABLE " + leafEntity + "_relationships (");
                relationshipSQL.append("id INT AUTO_INCREMENT PRIMARY KEY");

                for (String entity : hierarchyPath) {
                    relationshipSQL.append(", ").append(entity).append("_id INT NOT NULL");
                }

                for (String entity : hierarchyPath) {
                    relationshipSQL.append(", FOREIGN KEY (").append(entity).append("_id) ")
                            .append("REFERENCES ").append(entity).append("(id)");
                }

                relationshipSQL.append(");");
                tableDefinitions.add(relationshipSQL.toString());
            }

            // Create hierarchy table for metadata
            tableDefinitions.add("CREATE TABLE entity_hierarchy (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "parent_entity VARCHAR(255), " +
                    "child_entity VARCHAR(255), " +
                    "relation_name VARCHAR(255)" +
                    ");");

            // Add hierarchy metadata
            for (int i = 0; i < entities.getLength(); i++) {
                Element entity = (Element) entities.item(i);
                String source = entity.getAttribute("Name");
                NodeList relations = entity.getElementsByTagName("relation");

                for (int j = 0; j < relations.getLength(); j++) {
                    Element relation = (Element) relations.item(j);
                    String target = relation.getAttribute("target");
                    String relName = relation.getAttribute("Name");

                    hierarchyInserts.add("INSERT INTO entity_hierarchy (parent_entity, child_entity, relation_name) VALUES " +
                            "('" + source + "', '" + target + "', '" + relName + "');");
                }
            }

            tableDefinitions.addAll(hierarchyInserts);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return tableDefinitions;
    }

    private static String mapXSDTypeToSQL(String xsdType) {
        return switch (xsdType.toLowerCase()) {
            case "string" -> "VARCHAR(255)";
            case "int", "integer" -> "INT";
            case "float", "double" -> "DOUBLE";
            case "boolean" -> "BOOLEAN";
            case "date" -> "DATE";
            default -> "TEXT";
        };
    }
}