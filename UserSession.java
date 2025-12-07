public class UserSession {
    public String username;
    public String role;
    public String fullName;
    public java.util.List<Question> currentQuiz; // Stores the quiz for grading

    public UserSession(String username, String role, String fullName) {
        this.username = username;
        this.role = role;
        this.fullName = fullName;
    }
}