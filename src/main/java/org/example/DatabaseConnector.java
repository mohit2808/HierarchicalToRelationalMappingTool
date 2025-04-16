package org.example;

import java.sql.*;
import java.util.List;

public class DatabaseConnector {
    private static final String URL = "jdbc:mysql://localhost:3306/";
    private static final String USER = "xsduser";
    private static final String PASSWORD = "1234"; // Use your actual password
    private static String databaseName;

    public static boolean createDatabase(String dbName) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            String sql = "CREATE DATABASE IF NOT EXISTS " + dbName;
            stmt.executeUpdate(sql);
            System.out.println("Created database: " + dbName);
            databaseName = dbName;
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Connection getConnection() throws SQLException {
        if (databaseName == null || databaseName.isEmpty()) {
            throw new SQLException("Database name not set");
        }
        return DriverManager.getConnection(URL + databaseName, USER, PASSWORD);
    }

    public static void setDatabaseName(String dbName) {
        databaseName = dbName;
        System.out.println("Database name set to: " + dbName);
    }

    public static String getDatabaseName() {
        return databaseName;
    }

    public static void executeSQL(List<String> sqlStatements) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            for (String sql : sqlStatements) {
                try {
                    stmt.execute(sql);
                    System.out.println("Executed: " + sql);
                } catch (SQLException e) {
                    System.err.println("Error executing: " + sql);
                    e.printStackTrace();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}