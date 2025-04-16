package org.example;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.sql.*;
import java.util.*;

public class XMLDataInserter {
    public static void insertStoreData(String xmlFilePath) {
        try {
            System.out.println("Parsing XML data from " + xmlFilePath);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(xmlFilePath));
            Element root = document.getDocumentElement();

            try (Connection conn = DatabaseConnector.getConnection()) {
                // Load hierarchy rules from the database
                Map<String, Set<String>> hierarchyRules = loadHierarchyRules(conn);

                // Validate the XML structure against the hierarchy rules
                if (!validateXmlHierarchy(root, hierarchyRules)) {
                    System.out.println("XML hierarchy validation failed. Data will not be inserted.");
                    return;
                }

                System.out.println("XML hierarchy validation successful. Proceeding with data insertion.");

                // Process and insert the XML data
                Map<String, Integer> contextMap = new HashMap<>();
                processElement(conn, root, contextMap);

                System.out.println("Data insertion completed successfully.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, Set<String>> loadHierarchyRules(Connection conn) throws SQLException {
        Map<String, Set<String>> hierarchyRules = new HashMap<>();

        String query = "SELECT parent_entity, child_entity FROM entity_hierarchy";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String parent = rs.getString("parent_entity");
                String child = rs.getString("child_entity");

                hierarchyRules.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
            }
        }

        System.out.println("Loaded hierarchy rules: " + hierarchyRules);
        return hierarchyRules;
    }

    private static boolean validateXmlHierarchy(Element element, Map<String, Set<String>> hierarchyRules) {
        String tag = element.getTagName();

        // Get child elements
        NodeList childNodes = element.getChildNodes();
        List<Element> childElements = new ArrayList<>();

        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                childElements.add((Element) childNodes.item(i));
            }
        }

        // Check each child element against hierarchy rules
        for (Element child : childElements) {
            String childTag = child.getTagName();

            // Check if this parent-child relationship is allowed
            if (!hierarchyRules.containsKey(tag) || !hierarchyRules.get(tag).contains(childTag)) {
                System.out.println("Hierarchy violation: " + childTag + " cannot be a child of " + tag);
                return false;
            }

            // Recursively validate child's hierarchy
            if (!validateXmlHierarchy(child, hierarchyRules)) {
                return false;
            }
        }

        return true;
    }

    private static void processElement(Connection conn, Element element, Map<String, Integer> parentContext) throws SQLException {
        String tag = element.getTagName();

        // Get attributes
        Map<String, String> attributeMap = new HashMap<>();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            attributeMap.put(attr.getNodeName(), attr.getNodeValue());
        }

        // Insert entity
        int entityId = insertEntity(conn, tag, attributeMap);
        if (entityId > 0) {
            System.out.println("Inserted " + tag + ": " + attributeMap);

            // Create new context with this entity
            Map<String, Integer> currentContext = new HashMap<>(parentContext);
            currentContext.put(tag, entityId);

            // Check if this is a leaf entity
            String dbName = DatabaseConnector.getDatabaseName();
            if ((dbName.equals("lib") && tag.equals("book")) ||
                    (dbName.equals("store") && tag.equals("product"))) {
                insertRelationship(conn, currentContext);
                System.out.println("Inserted relationship for " + tag + " id: " + entityId);
            }

            // Process child elements
            NodeList childNodes = element.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    processElement(conn, (Element) childNodes.item(i), currentContext);
                }
            }
        }
    }

    private static int insertEntity(Connection conn, String entityType, Map<String, String> attributeMap) throws SQLException {
        if (attributeMap.isEmpty()) {
            return -1;
        }

        // Get table columns
        List<String> tableColumns = getTableColumns(conn, entityType);
        if (tableColumns.isEmpty()) {
            throw new SQLException("Table " + entityType + " does not exist or has no columns");
        }

        // Match attributes to columns
        StringBuilder sql = new StringBuilder("INSERT INTO " + entityType + " (");
        StringBuilder placeholders = new StringBuilder(") VALUES (");
        List<String> values = new ArrayList<>();

        for (String column : tableColumns) {
            if (column.equals("id")) continue; // Skip ID column

            for (Map.Entry<String, String> entry : attributeMap.entrySet()) {
                if (column.equals(entry.getKey())) {
                    sql.append(column).append(", ");
                    placeholders.append("?, ");
                    values.add(entry.getValue());
                    break;
                }
            }
        }

        if (values.isEmpty()) {
            return -1;
        }

        // Remove trailing commas
        sql.setLength(sql.length() - 2);
        placeholders.setLength(placeholders.length() - 2);

        // Complete SQL statement
        String insertSql = sql.toString() + placeholders.toString() + ")";

        try (PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setString(i + 1, values.get(i));
            }

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        }

        return -1;
    }

    private static List<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + tableName);
            while (rs.next()) {
                columns.add(rs.getString("Field"));
            }
        }
        return columns;
    }

    private static void insertRelationship(Connection conn, Map<String, Integer> contextMap) throws SQLException {
        // Get database name to determine the correct relationship table
        String dbName = DatabaseConnector.getDatabaseName();

        // Determine leaf entity based on database
        String leafEntity = dbName.equals("lib") ? "book" : "product";

        if (!contextMap.containsKey(leafEntity)) {
            return; // Can't find leaf entity in context
        }

        // Build the SQL statement
        String relationshipTable = leafEntity + "_relationships";
        StringBuilder sql = new StringBuilder("INSERT INTO " + relationshipTable + " (");
        StringBuilder valuesClause = new StringBuilder(") VALUES (");
        List<Integer> valuesList = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : contextMap.entrySet()) {
            sql.append(entry.getKey()).append("_id, ");
            valuesClause.append("?, ");
            valuesList.add(entry.getValue());
        }

        // If no columns match, return
        if (valuesList.isEmpty()) {
            return;
        }

        // Remove trailing commas
        sql.setLength(sql.length() - 2);
        valuesClause.setLength(valuesClause.length() - 2);

        // Complete SQL statement
        String insertSql = sql.toString() + valuesClause.toString() + ")";

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (int i = 0; i < valuesList.size(); i++) {
                stmt.setInt(i + 1, valuesList.get(i));
            }
            stmt.executeUpdate();
        }
    }
}