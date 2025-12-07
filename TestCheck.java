import java.sql.*;
public class TestCheck {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:virtual_lab.db");
        
        System.out.println("=== Scheduled Tests ===");
        Statement stmt1 = conn.createStatement();
        ResultSet rs1 = stmt1.executeQuery("SELECT * FROM scheduled_tests");
        while(rs1.next()) {
            System.out.println("ID: " + rs1.getInt("id") + ", Title: " + rs1.getString("title") + 
                ", Date: " + rs1.getString("scheduled_date") + ", Time: " + rs1.getString("scheduled_time") +
                ", Status: " + rs1.getString("status"));
        }
        
        System.out.println("\n=== Test Questions ===");
        Statement stmt2 = conn.createStatement();
        ResultSet rs2 = stmt2.executeQuery("SELECT COUNT(*) as cnt FROM test_questions");
        if(rs2.next()) System.out.println("Total questions: " + rs2.getInt("cnt"));
        
        conn.close();
    }
}
