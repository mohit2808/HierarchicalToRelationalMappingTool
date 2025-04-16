package org.example;

import java.sql.*;

public class DataRetriever {
    public static void displayInsertedTables() {
        String dbName = DatabaseConnector.getDatabaseName();
        try (Connection conn = DatabaseConnector.getConnection();
             Statement stmt = conn.createStatement()) {

            // Determine root table based on database
            String rootTable;
            if (dbName.equals("lib")) {
                rootTable = "library";
            } else if (dbName.equals("store")) {
                rootTable = "store";
            } else {
                System.out.println("Unknown database schema: " + dbName);
                return;
            }

            ResultSet rs = stmt.executeQuery("SELECT * FROM " + rootTable);

            // Display results
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            System.out.println("\nData in " + rootTable + " table:");

            // Print column headers
            for (int i = 1; i <= columnCount; i++) {
                System.out.print(metaData.getColumnName(i) + "\t");
            }
            System.out.println();

            // Print data rows
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(rs.getString(i) + "\t");
                }
                System.out.println();
            }

            // Also display relationships
            String leafEntity = dbName.equals("lib") ? "book" : "product";
            displayRelationships(conn, leafEntity);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void displayRelationships(Connection conn, String leafEntity) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + leafEntity + "_relationships");

            // Display results
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            System.out.println("\nData in " + leafEntity + "_relationships table:");

            // Print column headers
            for (int i = 1; i <= columnCount; i++) {
                System.out.print(metaData.getColumnName(i) + "\t");
            }
            System.out.println();

            // Print data rows
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(rs.getString(i) + "\t");
                }
                System.out.println();
            }
        } catch (SQLException e) {
            // Table might not exist yet or have no data
            System.out.println("No relationship data available for " + leafEntity);
        }
    }
}