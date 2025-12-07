public record Question(
    String topic, 
    String question, 
    String[] options, 
    int correctIndex, 
    String explanation // <--- This must be here!
) {}