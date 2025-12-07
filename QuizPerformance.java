import java.time.LocalDateTime;
public record QuizPerformance(String username, String topic, int score, int total, LocalDateTime timestamp) {}