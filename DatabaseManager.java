import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DatabaseManager {

    private String dbUrl = "jdbc:sqlite:virtual_lab.db"; 

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    public void setupDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Create Tables
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT, role TEXT, full_name TEXT, avatar TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS quiz_performance (id INTEGER PRIMARY KEY AUTOINCREMENT, student_username TEXT, topic TEXT, score INTEGER, total INTEGER, quiz_date TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS feedback (id INTEGER PRIMARY KEY AUTOINCREMENT, teacher_username TEXT, message TEXT, date TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS questions (id INTEGER PRIMARY KEY AUTOINCREMENT, topic TEXT, question TEXT, opt0 TEXT, opt1 TEXT, opt2 TEXT, opt3 TEXT, correct_index INTEGER, explanation TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS experiments (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, description TEXT, filename TEXT, category TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS lectures (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, summary TEXT, video_url TEXT, category TEXT, teacher_username TEXT, upload_date TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS materials (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, description TEXT, file_path TEXT, file_type TEXT, file_size TEXT, category TEXT, teacher_username TEXT, upload_date TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS student_feedback (id INTEGER PRIMARY KEY AUTOINCREMENT, student_username TEXT, student_name TEXT, subject TEXT, message TEXT, feedback_date TEXT, is_read INTEGER DEFAULT 0)");
            
            // Test Module Tables
            stmt.execute("CREATE TABLE IF NOT EXISTS test_questions (id INTEGER PRIMARY KEY AUTOINCREMENT, subject TEXT, question TEXT, opt0 TEXT, opt1 TEXT, opt2 TEXT, opt3 TEXT, correct_index INTEGER, created_by TEXT, created_date TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS scheduled_tests (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, subject TEXT, duration_minutes INTEGER, num_questions INTEGER, scheduled_date TEXT, scheduled_time TEXT, status TEXT DEFAULT 'scheduled', created_by TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS test_results (id INTEGER PRIMARY KEY AUTOINCREMENT, test_id INTEGER, student_username TEXT, student_name TEXT, score INTEGER, total INTEGER, start_time TEXT, end_time TEXT, video_path TEXT, time_taken TEXT, status TEXT)");
            
            // Migration: Add time_taken column if it doesn't exist
            try { stmt.execute("ALTER TABLE test_results ADD COLUMN time_taken TEXT"); } catch (SQLException e) { /* Column already exists */ }

            // 2. Create Default Users
            if (getUser("student") == null) createUser(new User("student", "pass", "STUDENT", "Alex Student", ""));
            if (getUser("teacher") == null) createUser(new User("teacher", "pass", "TEACHER", "Mrs. Roberts", ""));
            if (getUser("admin") == null)   createUser(new User("admin", "pass", "ADMIN", "System Admin", ""));

            // 3. Seed Data if empty
            if (getAllQuestions().isEmpty()) seedQuestions();
            if (getAllExperiments().isEmpty()) seedExperiments();
            
            System.out.println("SQLite Database setup complete.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- User Management ---
    public User getUser(String username) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return new User(rs.getString("username"), rs.getString("password"), rs.getString("role"), rs.getString("full_name"), rs.getString("avatar"));
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public List<User> getUsersByRole(String role) {
        List<User> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE role = ?")) {
            stmt.setString(1, role);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) list.add(new User(rs.getString("username"), "", rs.getString("role"), rs.getString("full_name"), ""));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean createUser(User user) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO users VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, user.username());
            stmt.setString(2, user.password());
            stmt.setString(3, user.role());
            stmt.setString(4, user.fullName());
            stmt.setString(5, user.avatar() == null ? "" : user.avatar());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    // Update Avatar
    public boolean updateAvatar(String username, String base64) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("UPDATE users SET avatar = ? WHERE username = ?")) {
            stmt.setString(1, base64);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // Teacher Update Student (Name/Pass)
    public boolean updateStudent(String username, String newPassword, String newFullName) {
        String sql = (newPassword != null && !newPassword.trim().isEmpty()) ? "UPDATE users SET full_name = ?, password = ? WHERE username = ?" : "UPDATE users SET full_name = ? WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newFullName);
            if (newPassword != null && !newPassword.trim().isEmpty()) { stmt.setString(2, newPassword); stmt.setString(3, username); } 
            else { stmt.setString(2, username); }
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // Admin Update User (Name/Role/Pass)
    public boolean updateUser(String username, String newPassword, String newFullName, String newRole) {
        String sql = (newPassword != null && !newPassword.trim().isEmpty()) ? "UPDATE users SET full_name = ?, role = ?, password = ? WHERE username = ?" : "UPDATE users SET full_name = ?, role = ? WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newFullName);
            stmt.setString(2, newRole);
            if (newPassword != null && !newPassword.trim().isEmpty()) { stmt.setString(3, newPassword); stmt.setString(4, username); } 
            else { stmt.setString(3, username); }
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean updatePassword(String username, String newPassword) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("UPDATE users SET password = ? WHERE username = ?")) {
            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }
    
    public boolean deleteUser(String username) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM quiz_performance WHERE student_username = ?")) { stmt.setString(1, username); stmt.executeUpdate(); } catch (SQLException e) {}
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) { stmt.setString(1, username); return stmt.executeUpdate() > 0; } catch (SQLException e) { return false; }
    }

    // --- Experiment Management ---
    public void addExperiment(String title, String desc, String filename, String category) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO experiments (title, description, filename, category) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, title); stmt.setString(2, desc); stmt.setString(3, filename); stmt.setString(4, category); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public void deleteExperiment(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM experiments WHERE id = ?")) { stmt.setInt(1, id); stmt.executeUpdate(); } catch (SQLException e) { e.printStackTrace(); }
    }
    public List<Experiment> getAllExperiments() {
        List<Experiment> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM experiments")) {
            while (rs.next()) {
                list.add(new Experiment(rs.getInt("id"), rs.getString("title"), rs.getString("description"), rs.getString("filename"), rs.getString("category")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    private void seedExperiments() {
        addExperiment("Simple Pendulum", "Study harmonic motion, length, and mass.", "/simplependulum", "Physics");
        addExperiment("Ohm's Law", "Explore voltage, current, and resistance.", "/ohmslaw", "Physics");
        addExperiment("pH Scale Indicator", "Test acidity and alkalinity of liquids.", "/phscale", "Chemistry");
        addExperiment("Beam Deflection", "Apply loads to a cantilever beam.", "/beam", "Engineering");
        addExperiment("States of Matter", "Watch particles change from solid to gas.", "/statesofmatter", "Chemistry");
        addExperiment("Simple Gear Train", "See how gears transfer speed and torque.", "/geartrain", "Engineering");
    }

    // --- Question Management ---
    public void addQuestion(Question q) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO questions (topic, question, opt0, opt1, opt2, opt3, correct_index, explanation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, q.topic()); stmt.setString(2, q.question()); stmt.setString(3, q.options()[0]); stmt.setString(4, q.options()[1]); stmt.setString(5, q.options()[2]); stmt.setString(6, q.options()[3]); stmt.setInt(7, q.correctIndex()); stmt.setString(8, q.explanation()); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public void updateQuestion(int id, Question q) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("UPDATE questions SET topic=?, question=?, opt0=?, opt1=?, opt2=?, opt3=?, correct_index=?, explanation=? WHERE id=?")) {
            stmt.setString(1, q.topic()); stmt.setString(2, q.question()); stmt.setString(3, q.options()[0]); stmt.setString(4, q.options()[1]); stmt.setString(5, q.options()[2]); stmt.setString(6, q.options()[3]); stmt.setInt(7, q.correctIndex()); stmt.setString(8, q.explanation()); stmt.setInt(9, id); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public void deleteQuestion(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM questions WHERE id = ?")) { stmt.setInt(1, id); stmt.executeUpdate(); } catch (SQLException e) { e.printStackTrace(); }
    }
    public List<Question> getAllQuestions() {
        List<Question> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM questions")) {
            while (rs.next()) {
                String[] opts = {rs.getString("opt0"), rs.getString("opt1"), rs.getString("opt2"), rs.getString("opt3")};
                list.add(new Question(rs.getString("topic"), rs.getString("question"), opts, rs.getInt("correct_index"), rs.getString("explanation")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    public String getAllQuestionsAsJson() {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM questions ORDER BY id DESC")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(","); first = false;
                json.append(String.format("{\"id\":%d, \"topic\":\"%s\", \"question\":\"%s\", \"options\":[\"%s\",\"%s\",\"%s\",\"%s\"], \"correctIndex\":%d, \"explanation\":\"%s\"}",
                    rs.getInt("id"), rs.getString("topic"), escape(rs.getString("question")),
                    escape(rs.getString("opt0")), escape(rs.getString("opt1")), escape(rs.getString("opt2")), escape(rs.getString("opt3")),
                    rs.getInt("correct_index"), escape(rs.getString("explanation"))));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]"); return json.toString();
    }
    public List<Question> getRandomQuestions(String topic, int limit) {
        List<Question> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM questions WHERE topic = ?")) {
            stmt.setString(1, topic); ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String[] opts = {rs.getString("opt0"), rs.getString("opt1"), rs.getString("opt2"), rs.getString("opt3")};
                list.add(new Question(rs.getString("topic"), rs.getString("question"), opts, rs.getInt("correct_index"), rs.getString("explanation")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        Collections.shuffle(list);
        return list.subList(0, Math.min(limit, list.size()));
    }
    private void seedQuestions() {
        addQuestion(new Question("physics", "Formula for Ohm's Law?", new String[]{"V=IR", "F=ma", "E=mc^2", "P=VI"}, 0, "V=IR is the standard formula."));
    }
    private String escape(String s) { 
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); 
    }

    // --- Performance & Feedback ---
    public void saveQuizPerformance(QuizPerformance p) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO quiz_performance (student_username, topic, score, total, quiz_date) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, p.username()); stmt.setString(2, p.topic()); stmt.setInt(3, p.score()); stmt.setInt(4, p.total()); stmt.setString(5, p.timestamp().toString()); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public List<QuizPerformance> getPerformance(String username) {
        List<QuizPerformance> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM quiz_performance WHERE student_username = ? ORDER BY id DESC")) {
            stmt.setString(1, username); ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(new QuizPerformance(rs.getString("student_username"), rs.getString("topic"), rs.getInt("score"), rs.getInt("total"), java.time.LocalDateTime.parse(rs.getString("quiz_date"))));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    public List<QuizPerformance> getAllPerformance() {
        List<QuizPerformance> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM quiz_performance ORDER BY id DESC")) {
            while (rs.next()) list.add(new QuizPerformance(rs.getString("student_username"), rs.getString("topic"), rs.getInt("score"), rs.getInt("total"), java.time.LocalDateTime.parse(rs.getString("quiz_date"))));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    public void addFeedback(String teacher, String msg) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO feedback (teacher_username, message, date) VALUES (?, ?, ?)")) {
            stmt.setString(1, teacher); stmt.setString(2, msg); stmt.setString(3, java.time.LocalDate.now().toString()); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public void clearFeedback() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) { stmt.execute("DELETE FROM feedback"); } catch (SQLException e) { e.printStackTrace(); }
    }
    public List<String> getAllFeedback() {
        List<String> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM feedback ORDER BY id DESC")) {
            while(rs.next()) list.add(rs.getString("date") + " - " + rs.getString("teacher_username") + ": " + rs.getString("message"));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // --- Lecture Management ---
    public void addLecture(String title, String summary, String videoUrl, String category, String teacherUsername) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO lectures (title, summary, video_url, category, teacher_username, upload_date) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, title);
            stmt.setString(2, summary);
            stmt.setString(3, videoUrl);
            stmt.setString(4, category);
            stmt.setString(5, teacherUsername);
            stmt.setString(6, java.time.LocalDate.now().toString());
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void deleteLecture(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM lectures WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String getAllLecturesAsJson() {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM lectures ORDER BY id DESC")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append(String.format("{\"id\":%d, \"title\":\"%s\", \"summary\":\"%s\", \"videoUrl\":\"%s\", \"category\":\"%s\", \"teacher\":\"%s\", \"date\":\"%s\"}",
                    rs.getInt("id"), escape(rs.getString("title")), escape(rs.getString("summary")),
                    escape(rs.getString("video_url")), escape(rs.getString("category")),
                    escape(rs.getString("teacher_username")), rs.getString("upload_date")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    // --- Study Materials Management ---
    public void addMaterial(String title, String description, String filePath, String fileType, String fileSize, String category, String teacherUsername) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO materials (title, description, file_path, file_type, file_size, category, teacher_username, upload_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, title);
            stmt.setString(2, description);
            stmt.setString(3, filePath);
            stmt.setString(4, fileType);
            stmt.setString(5, fileSize);
            stmt.setString(6, category);
            stmt.setString(7, teacherUsername);
            stmt.setString(8, java.time.LocalDate.now().toString());
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void deleteMaterial(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM materials WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String getMaterialFilePath(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT file_path FROM materials WHERE id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("file_path");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public String getAllMaterialsAsJson() {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM materials ORDER BY id DESC")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append(String.format("{\"id\":%d, \"title\":\"%s\", \"description\":\"%s\", \"filePath\":\"%s\", \"fileType\":\"%s\", \"fileSize\":\"%s\", \"category\":\"%s\", \"teacher\":\"%s\", \"date\":\"%s\"}",
                    rs.getInt("id"), escape(rs.getString("title")), escape(rs.getString("description")),
                    escape(rs.getString("file_path")), escape(rs.getString("file_type")), escape(rs.getString("file_size")),
                    escape(rs.getString("category")), escape(rs.getString("teacher_username")), rs.getString("upload_date")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    // --- Student Feedback Management ---
    public void addStudentFeedback(String studentUsername, String studentName, String subject, String message) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO student_feedback (student_username, student_name, subject, message, feedback_date, is_read) VALUES (?, ?, ?, ?, ?, 0)")) {
            stmt.setString(1, studentUsername);
            stmt.setString(2, studentName);
            stmt.setString(3, subject);
            stmt.setString(4, message);
            stmt.setString(5, java.time.LocalDateTime.now().toString());
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void markFeedbackAsRead(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("UPDATE student_feedback SET is_read = 1 WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void deleteStudentFeedback(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM student_feedback WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public int getUnreadFeedbackCount() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM student_feedback WHERE is_read = 0")) {
            if (rs.next()) return rs.getInt("count");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public String getAllStudentFeedbackAsJson() {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM student_feedback ORDER BY id DESC")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                String date = rs.getString("feedback_date");
                if (date.contains("T")) date = date.split("T")[0];
                json.append(String.format("{\"id\":%d, \"student\":\"%s\", \"studentName\":\"%s\", \"subject\":\"%s\", \"message\":\"%s\", \"date\":\"%s\", \"isRead\":%d}",
                    rs.getInt("id"), escape(rs.getString("student_username")), escape(rs.getString("student_name")),
                    escape(rs.getString("subject")), escape(rs.getString("message")), date, rs.getInt("is_read")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    // === TEST MODULE METHODS ===
    
    // Test Questions Management
    public void addTestQuestion(String subject, String question, String opt0, String opt1, String opt2, String opt3, int correctIndex, String createdBy) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO test_questions (subject, question, opt0, opt1, opt2, opt3, correct_index, created_by, created_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, subject);
            stmt.setString(2, question);
            stmt.setString(3, opt0);
            stmt.setString(4, opt1);
            stmt.setString(5, opt2);
            stmt.setString(6, opt3);
            stmt.setInt(7, correctIndex);
            stmt.setString(8, createdBy);
            stmt.setString(9, java.time.LocalDate.now().toString());
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void updateTestQuestion(int id, String subject, String question, String opt0, String opt1, String opt2, String opt3, int correctIndex) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "UPDATE test_questions SET subject=?, question=?, opt0=?, opt1=?, opt2=?, opt3=?, correct_index=? WHERE id=?")) {
            stmt.setString(1, subject);
            stmt.setString(2, question);
            stmt.setString(3, opt0);
            stmt.setString(4, opt1);
            stmt.setString(5, opt2);
            stmt.setString(6, opt3);
            stmt.setInt(7, correctIndex);
            stmt.setInt(8, id);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void deleteTestQuestion(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM test_questions WHERE id=?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String getAllTestQuestionsAsJson() {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery("SELECT * FROM test_questions ORDER BY id DESC")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append(String.format("{\"id\":%d,\"subject\":\"%s\",\"question\":\"%s\",\"opt0\":\"%s\",\"opt1\":\"%s\",\"opt2\":\"%s\",\"opt3\":\"%s\",\"correctIndex\":%d,\"createdBy\":\"%s\",\"createdDate\":\"%s\"}",
                    rs.getInt("id"), escape(rs.getString("subject")), escape(rs.getString("question")),
                    escape(rs.getString("opt0")), escape(rs.getString("opt1")), escape(rs.getString("opt2")), escape(rs.getString("opt3")),
                    rs.getInt("correct_index"), escape(rs.getString("created_by")), rs.getString("created_date")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    public String getRandomTestQuestions(int count) {
        StringBuilder json = new StringBuilder("[");
        String query = "SELECT * FROM test_questions ORDER BY RANDOM() LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, count);
            ResultSet rs = stmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append(String.format("{\"id\":%d,\"subject\":\"%s\",\"question\":\"%s\",\"options\":[\"%s\",\"%s\",\"%s\",\"%s\"],\"correctIndex\":%d}",
                    rs.getInt("id"), escape(rs.getString("subject")), escape(rs.getString("question")),
                    escape(rs.getString("opt0")), escape(rs.getString("opt1")), escape(rs.getString("opt2")), escape(rs.getString("opt3")),
                    rs.getInt("correct_index")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    public int getScheduledTestQuestionCount(int testId) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "SELECT num_questions FROM scheduled_tests WHERE id=?")) {
            stmt.setInt(1, testId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("num_questions");
        } catch (SQLException e) { e.printStackTrace(); }
        return 10;
    }

    // Scheduled Tests Management
    public int scheduleTest(String title, String subject, int duration, int numQuestions, String date, String time, String createdBy) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO scheduled_tests (title, subject, duration_minutes, num_questions, scheduled_date, scheduled_time, status, created_by) VALUES (?, ?, ?, ?, ?, ?, 'scheduled', ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, title);
            stmt.setString(2, subject);
            stmt.setInt(3, duration);
            stmt.setInt(4, numQuestions);
            stmt.setString(5, date);
            stmt.setString(6, time);
            stmt.setString(7, createdBy);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public void updateScheduledTest(int id, String title, String subject, int duration, int numQuestions, String date, String time, String status) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "UPDATE scheduled_tests SET title=?, subject=?, duration_minutes=?, num_questions=?, scheduled_date=?, scheduled_time=?, status=? WHERE id=?")) {
            stmt.setString(1, title);
            stmt.setString(2, subject);
            stmt.setInt(3, duration);
            stmt.setInt(4, numQuestions);
            stmt.setString(5, date);
            stmt.setString(6, time);
            stmt.setString(7, status);
            stmt.setInt(8, id);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void deleteScheduledTest(int id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM scheduled_tests WHERE id=?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String getAllScheduledTestsAsJson() {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery("SELECT * FROM scheduled_tests ORDER BY scheduled_date DESC, scheduled_time DESC")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append(String.format("{\"id\":%d,\"title\":\"%s\",\"subject\":\"%s\",\"duration\":%d,\"numQuestions\":%d,\"scheduledDate\":\"%s\",\"scheduledTime\":\"%s\",\"status\":\"%s\",\"createdBy\":\"%s\"}",
                    rs.getInt("id"), escape(rs.getString("title")), escape(rs.getString("subject")),
                    rs.getInt("duration_minutes"), rs.getInt("num_questions"),
                    rs.getString("scheduled_date"), rs.getString("scheduled_time"),
                    rs.getString("status"), escape(rs.getString("created_by"))));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    public String getActiveTestsForStudent() {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM scheduled_tests WHERE status IN ('scheduled', 'active') ORDER BY scheduled_date, scheduled_time")) {
            ResultSet rs = stmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append(String.format("{\"id\":%d,\"title\":\"%s\",\"subject\":\"%s\",\"duration\":%d,\"numQuestions\":%d,\"scheduledDate\":\"%s\",\"scheduledTime\":\"%s\"}",
                    rs.getInt("id"), escape(rs.getString("title")), escape(rs.getString("subject")),
                    rs.getInt("duration_minutes"), rs.getInt("num_questions"),
                    rs.getString("scheduled_date"), rs.getString("scheduled_time")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    // Test Results Management
    public int startTestResult(int testId, String studentUsername, String studentName) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO test_results (test_id, student_username, student_name, score, total, start_time, status) VALUES (?, ?, ?, 0, 0, ?, 'in_progress')", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, testId);
            stmt.setString(2, studentUsername);
            stmt.setString(3, studentName);
            stmt.setString(4, java.time.LocalDateTime.now().toString());
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public void completeTestResult(int resultId, int score, int total, String videoPath, String timeTaken) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "UPDATE test_results SET score=?, total=?, end_time=?, video_path=?, time_taken=?, status='completed' WHERE id=?")) {
            stmt.setInt(1, score);
            stmt.setInt(2, total);
            stmt.setString(3, java.time.LocalDateTime.now().toString());
            stmt.setString(4, videoPath);
            stmt.setString(5, timeTaken);
            stmt.setInt(6, resultId);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public boolean hasStudentTakenTest(int testId, String studentUsername) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "SELECT COUNT(*) as count FROM test_results WHERE test_id=? AND student_username=? AND status='completed'")) {
            stmt.setInt(1, testId);
            stmt.setString(2, studentUsername);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("count") > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public String getAllTestResultsAsJson() {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery("SELECT tr.*, st.title as test_title FROM test_results tr LEFT JOIN scheduled_tests st ON tr.test_id = st.id ORDER BY tr.end_time DESC")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                String endTime = rs.getString("end_time");
                String dateStr = endTime != null && endTime.contains("T") ? endTime.split("T")[0] : (endTime != null ? endTime : "");
                String timeTaken = rs.getString("time_taken");
                json.append(String.format("{\"id\":%d,\"testId\":%d,\"testTitle\":\"%s\",\"student\":\"%s\",\"studentName\":\"%s\",\"score\":%d,\"totalQuestions\":%d,\"completedAt\":\"%s\",\"videoPath\":\"%s\",\"timeTaken\":\"%s\",\"status\":\"%s\"}",
                    rs.getInt("id"), rs.getInt("test_id"), escape(rs.getString("test_title") != null ? rs.getString("test_title") : "Unknown Test"),
                    escape(rs.getString("student_username")), escape(rs.getString("student_name")),
                    rs.getInt("score"), rs.getInt("total"), dateStr,
                    escape(rs.getString("video_path") != null ? rs.getString("video_path") : ""),
                    escape(timeTaken != null ? timeTaken : ""),
                    rs.getString("status")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    public String getStudentTestResults(String studentUsername) {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(
            "SELECT tr.*, st.title as test_title FROM test_results tr LEFT JOIN scheduled_tests st ON tr.test_id = st.id WHERE tr.student_username=? AND tr.status='completed' ORDER BY tr.end_time DESC")) {
            stmt.setString(1, studentUsername);
            ResultSet rs = stmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                String endTime = rs.getString("end_time");
                String dateStr = endTime != null && endTime.contains("T") ? endTime.split("T")[0] : (endTime != null ? endTime : "");
                json.append(String.format("{\"id\":%d,\"testId\":%d,\"testTitle\":\"%s\",\"score\":%d,\"total\":%d,\"date\":\"%s\",\"status\":\"%s\"}",
                    rs.getInt("id"), rs.getInt("test_id"), escape(rs.getString("test_title") != null ? rs.getString("test_title") : "Unknown Test"),
                    rs.getInt("score"), rs.getInt("total"), dateStr, rs.getString("status")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        json.append("]");
        return json.toString();
    }

    public int getTestQuestionCount(String subject) {
        String query = subject.equals("all") ? "SELECT COUNT(*) as count FROM test_questions" : 
            "SELECT COUNT(*) as count FROM test_questions WHERE subject=?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            if (!subject.equals("all")) stmt.setString(1, subject);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("count");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }
}