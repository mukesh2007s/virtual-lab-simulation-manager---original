import java.sql.*;

public class CleanTests {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:virtual_lab.db")) {
            // Delete all scheduled tests
            conn.createStatement().executeUpdate("DELETE FROM scheduled_tests");
            System.out.println("Deleted all scheduled tests");
            
            // Delete all test results
            conn.createStatement().executeUpdate("DELETE FROM test_results");
            System.out.println("Deleted all test results");
            
            System.out.println("\nDatabase cleaned!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
