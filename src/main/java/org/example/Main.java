package org.example;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String metaMetaModelPath = "src/main/resources/meta-meta-model.xsd";
        String metaModelPath = "src/main/resources/meta-model-lib.xsd"; // Change between lib and store as needed
        String xmlPath = "src/main/resources/lib.xml"; // Change between lib.xml and store.xml as needed

        if (!XMLValidator.validateXMLSchema(metaMetaModelPath, metaModelPath)) {
            System.out.println("Validation failed: meta-model.xsd is not valid against meta-meta-model.xsd");
            return;
        }

        System.out.println("Validation successful.");

        // Extract database name from meta model file
        String dbName = extractDatabaseName(metaModelPath);

        // Create the database
        if (!DatabaseConnector.createDatabase(dbName)) {
            System.out.println("Failed to create database. Exiting.");
            return;
        }

        // Set the database name for all subsequent operations
        DatabaseConnector.setDatabaseName(dbName);

        List<String> sqlStatements = XSDToSQLConverter.parseXSD(metaModelPath);
        if (sqlStatements.isEmpty()) {
            System.out.println("No SQL statements were generated.");
            return;
        }
        System.out.println("SQL statements generated:");
        System.out.println(sqlStatements);

        DatabaseConnector.executeSQL(sqlStatements);

        XMLDataInserter.insertStoreData(xmlPath);


        DataRetriever.displayInsertedTables();
    }

    private static String extractDatabaseName(String metaModelPath) {
        // Extract the filename without extension
        File file = new File(metaModelPath);
        String fileName = file.getName();

        // Check if the filename follows the meta-model-<dbname>.xml pattern
        if (fileName.startsWith("meta-model-") && fileName.contains(".")) {
            return fileName.substring("meta-model-".length(), fileName.lastIndexOf('.'));
        }

        // If the filename doesn't match the pattern, use a default name with timestamp
        return "xsddb_" + System.currentTimeMillis();
    }
}