import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class QuizBank {
    private List<Question> allQuestions = new ArrayList<>();

    public QuizBank() {
        // --- Physics ---
        allQuestions.add(new Question("physics", "Formula for Ohm's Law?", new String[]{"V=IR", "F=ma", "E=mc^2", "P=VI"}, 0, "Ohm's Law states Voltage = Current x Resistance."));
        allQuestions.add(new Question("physics", "Unit of Resistance?", new String[]{"Volt", "Ampere", "Ohm", "Watt"}, 2, "The Ohm is the standard unit of electrical resistance."));
        allQuestions.add(new Question("physics", "Pendulum Period depends on?", new String[]{"Mass", "Length", "Amplitude", "Color"}, 1, "The period depends only on length and gravity, not mass."));
        allQuestions.add(new Question("physics", "Unit of Current?", new String[]{"Volt", "Ampere", "Ohm", "Watt"}, 1, "Current is measured in Amperes (A)."));
        allQuestions.add(new Question("physics", "Gravity on Earth is approx?", new String[]{"9.8 m/s2", "1.6 m/s2", "3.4 m/s2", "12 m/s2"}, 0, "Standard gravity on Earth is 9.8 m/s2."));
        
        // --- Chemistry ---
        allQuestions.add(new Question("chemistry", "pH of pure water?", new String[]{"0", "14", "7", "1"}, 2, "Pure water is neutral, which is pH 7."));
        allQuestions.add(new Question("chemistry", "pH < 7 is?", new String[]{"Acidic", "Basic", "Neutral", "None"}, 0, "Values below 7 indicate acidity."));
        allQuestions.add(new Question("chemistry", "Particle state in Gas?", new String[]{"Fixed", "Flowing", "Far apart", "Vibrating"}, 2, "Gas particles move freely and are far apart."));
        allQuestions.add(new Question("chemistry", "Symbol for Gold?", new String[]{"Ag", "Au", "Fe", "Cu"}, 1, "Au comes from the Latin 'Aurum'."));
        allQuestions.add(new Question("chemistry", "Water boiling point?", new String[]{"100 C", "90 C", "110 C", "120 C"}, 0, "Water boils at 100 degrees Celsius at sea level."));

        // --- Engineering ---
        allQuestions.add(new Question("engineering", "Cantilever beam is fixed at?", new String[]{"Both ends", "One end", "No ends", "Center"}, 1, "A cantilever is supported at only one end."));
        allQuestions.add(new Question("engineering", "Hooke's Law relates to?", new String[]{"Gears", "Springs", "Fluids", "Heat"}, 1, "F=kx describes the physics of springs."));
        allQuestions.add(new Question("engineering", "Gear ratio calculation?", new String[]{"Teeth/Teeth", "Weight/Weight", "Color/Color", "None"}, 0, "Ratio is Output Teeth divided by Input Teeth."));
        allQuestions.add(new Question("engineering", "Beam deflection depends on?", new String[]{"Load", "Color", "Smell", "Taste"}, 0, "Higher load causes more deflection."));
        allQuestions.add(new Question("engineering", "Unit of Force?", new String[]{"Joule", "Watt", "Newton", "Pascal"}, 2, "The Newton (N) is the unit of force."));
    }

    public List<Question> getQuizQuestions(String topic, int count) {
        List<Question> filtered = allQuestions.stream()
            .filter(q -> q.topic().equalsIgnoreCase(topic))
            .collect(Collectors.toList());
        Collections.shuffle(filtered);
        return filtered.subList(0, Math.min(count, filtered.size()));
    }

    // --- THIS IS THE FIX ---
    // We manually build the JSON string to include the "explanation" field.
    public String convertQuestionsToJson(List<Question> questions) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            json.append("{");
            json.append("\"question\": \"").append(q.question()).append("\",");
            
            // !!! THIS LINE ADDS THE EXPLANATION !!!
            json.append("\"explanation\": \"").append(q.explanation()).append("\",");
            
            json.append("\"options\": [");
            for (int j = 0; j < q.options().length; j++) {
                json.append("\"").append(q.options()[j]).append("\"");
                if (j < q.options().length - 1) json.append(",");
            }
            json.append("],");
            json.append("\"correctIndex\": ").append(q.correctIndex());
            json.append("}");
            if (i < questions.size() - 1) json.append(",");
        }
        json.append("]");
        return json.toString();
    }
}