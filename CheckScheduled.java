import java.sql.*;

public class CheckScheduled {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:virtual_lab.db")) {
            System.out.println("=== Scheduled Tests ===");
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM scheduled_tests");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id") + 
                    " | Title: " + rs.getString("title") + 
                    " | Date: " + rs.getString("scheduled_date") + 
                    " | Time: " + rs.getString("scheduled_time") +
                    " | Status: " + rs.getString("status"));
            }
            
            System.out.println("\n=== Test Results ===");
            rs = conn.createStatement().executeQuery("SELECT * FROM test_results");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id") + 
                    " | TestID: " + rs.getInt("test_id") + 
                    " | Student: " + rs.getString("student_username") + 
                    " | Status: " + rs.getString("status"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
