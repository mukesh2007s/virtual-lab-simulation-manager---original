import java.sql.*;

public class CheckResults {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:virtual_lab.db")) {
            System.out.println("=== Test Results ===");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM test_results");
            
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            
            // Print column names
            for (int i = 1; i <= columnCount; i++) {
                System.out.print(rsmd.getColumnName(i) + " | ");
            }
            System.out.println();
            System.out.println("-".repeat(80));
            
            // Print data
            int count = 0;
            while (rs.next()) {
                count++;
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(rs.getString(i) + " | ");
                }
                System.out.println();
            }
            System.out.println("\nTotal results: " + count);
            
            // Check the schema
            System.out.println("\n=== Table Schema ===");
            rs = stmt.executeQuery("PRAGMA table_info(test_results)");
            while (rs.next()) {
                System.out.println("Column: " + rs.getString("name") + " Type: " + rs.getString("type"));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
