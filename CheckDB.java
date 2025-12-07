import java.sql.*;
public class CheckDB {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:virtual_lab.db");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM student_feedback");
        System.out.println("=== Student Feedback Table ===");
        int count = 0;
        while(rs.next()) {
            count++;
            System.out.println("ID: " + rs.getInt("id") + 
                             " | Student: " + rs.getString("student_username") +
                             " | Subject: " + rs.getString("subject") +
                             " | Read: " + rs.getInt("is_read"));
        }
        System.out.println("Total records: " + count);
        conn.close();
    }
}
