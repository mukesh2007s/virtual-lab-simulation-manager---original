import java.sql.*;

public class CheckScheduledTests {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:virtual_lab.db")) {
            System.out.println("=== Scheduled Tests ===");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM scheduled_tests");
            int count = 0;
            while(rs.next()) {
                count++;
                System.out.println("ID: " + rs.getInt("id"));
                System.out.println("  Title: " + rs.getString("title"));
                System.out.println("  Subject: " + rs.getString("subject"));
                System.out.println("  Status: " + rs.getString("status"));
                System.out.println("  Date: " + rs.getString("scheduled_date"));
                System.out.println("  Time: " + rs.getString("scheduled_time"));
                System.out.println("  Duration: " + rs.getInt("duration_minutes") + " min");
                System.out.println("  Questions: " + rs.getInt("num_questions"));
                System.out.println();
            }
            System.out.println("Total scheduled tests: " + count);
            
            System.out.println("\n=== Test Results ===");
            rs = stmt.executeQuery("SELECT * FROM test_results");
            count = 0;
            while(rs.next()) {
                count++;
                System.out.println("ID: " + rs.getInt("id"));
                System.out.println("  Test ID: " + rs.getInt("test_id"));
                System.out.println("  Student: " + rs.getString("student_username"));
                System.out.println("  Status: " + rs.getString("status"));
                System.out.println("  Score: " + rs.getInt("score") + "/" + rs.getInt("total"));
                System.out.println();
            }
            System.out.println("Total test results: " + count);
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
