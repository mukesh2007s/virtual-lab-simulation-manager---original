import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

public class VirtualLabServer {

    private static DatabaseManager dbManager = new DatabaseManager();
    private static Map<String, UserSession> sessionDatabase = new HashMap<>(); 

    public static void main(String[] args) throws IOException {
        try { Class.forName("org.sqlite.JDBC"); } 
        catch (ClassNotFoundException e) { System.out.println("FATAL ERROR: SQLite JAR Missing! Download sqlite-jdbc-3.30.1.jar"); return; }
        
        dbManager.setupDatabase();
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Static & Login
        server.createContext("/style.css", ex -> serveFile(ex, "style.css", "text/css"));
        server.createContext("/login", ex -> handleLogin(ex));
        server.createContext("/logout", ex -> handleLogout(ex));
        server.createContext("/signup", ex -> serveFile(ex, "signup.html", "text/html"));
        server.createContext("/register", ex -> handleRegister(ex));
        server.createContext("/", ex -> redirect(ex, "/login"));

        // Menus
        server.createContext("/student_menu", ex -> { if(checkRole(ex, "STUDENT")) serveFile(ex, "student_menu.html", "text/html"); });
        server.createContext("/teacher_menu", ex -> { if(checkRole(ex, "TEACHER")) serveFile(ex, "teacher_menu.html", "text/html"); });
        server.createContext("/admin_menu", ex -> { if(checkRole(ex, "ADMIN")) serveFile(ex, "admin_menu.html", "text/html"); });

        // Pages
        server.createContext("/dashboard", ex -> { if(checkRole(ex, "STUDENT")) serveFile(ex, "dashboard.html", "text/html"); });
        server.createContext("/profile", ex -> { if(checkAuth(ex)) serveFile(ex, "profile.html", "text/html"); });
        server.createContext("/quiz", ex -> { if(checkRoleMulti(ex, "STUDENT", "TEACHER")) serveFile(ex, "quiz.html", "text/html"); });

        // Experiments
        server.createContext("/ohmslaw", ex -> serveFile(ex, "ohmslaw.html", "text/html"));
        server.createContext("/statesofmatter", ex -> serveFile(ex, "statesofmatter.html", "text/html"));
        server.createContext("/simplependulum", ex -> serveFile(ex, "simplependulum.html", "text/html"));
        server.createContext("/phscale", ex -> serveFile(ex, "phscale.html", "text/html"));
        server.createContext("/beam", ex -> serveFile(ex, "beam.html", "text/html"));
        server.createContext("/geartrain", ex -> serveFile(ex, "geartrain.html", "text/html"));

        // Logic APIs
        server.createContext("/calculate", (ex) -> calculateOhmsLaw(ex));
        server.createContext("/getstate", (ex) -> getMatterState(ex));
        server.createContext("/calculatependulum", (ex) -> calculatePendulum(ex));
        server.createContext("/calculateph", (ex) -> calculatePh(ex));
        server.createContext("/calculatebeam", (ex) -> calculateBeam(ex));
        server.createContext("/calculategears", (ex) -> calculateGears(ex));

        // User & Data APIs
        server.createContext("/getdashboarddata", (ex) -> handleDashboardData(ex));
        server.createContext("/getprofile", (ex) -> handleProfileData(ex));
        server.createContext("/getquiz", (ex) -> handleGetQuiz(ex));
        server.createContext("/submitquiz", (ex) -> handleSubmitQuiz(ex));
        server.createContext("/api/user/changepassword", (ex) -> handleChangePassword(ex));
        server.createContext("/api/user/deleteaccount", (ex) -> handleDeleteAccount(ex));
        server.createContext("/api/user/uploadavatar", (ex) -> handleAvatarUpload(ex));

        // Teacher APIs
        server.createContext("/api/teacher/students", (ex) -> handleTeacherGetStudents(ex));
        server.createContext("/api/teacher/addstudent", (ex) -> handleTeacherAddStudent(ex));
        server.createContext("/api/teacher/updatestudent", (ex) -> handleTeacherUpdateStudent(ex));
        server.createContext("/api/teacher/delete", (ex) -> handleTeacherDeleteStudent(ex));
        server.createContext("/api/teacher/feedback", (ex) -> handleTeacherFeedback(ex));
        server.createContext("/api/teacher/performance", (ex) -> handleTeacherGetPerformance(ex));
        server.createContext("/api/teacher/questions", (ex) -> { if(checkRole(ex, "TEACHER")) send(ex, 200, "application/json", dbManager.getAllQuestionsAsJson()); });
        server.createContext("/api/teacher/save_question", (ex) -> handleTeacherSaveQuestion(ex));
        server.createContext("/api/teacher/delete_question", (ex) -> handleTeacherDeleteQuestion(ex));

        // Lecture APIs
        server.createContext("/api/lectures", (ex) -> { if(checkAuth(ex)) send(ex, 200, "application/json", dbManager.getAllLecturesAsJson()); });
        server.createContext("/api/teacher/add_lecture", (ex) -> handleTeacherAddLecture(ex));
        server.createContext("/api/teacher/upload_video", (ex) -> handleTeacherUploadVideo(ex));
        server.createContext("/api/teacher/delete_lecture", (ex) -> handleTeacherDeleteLecture(ex));
        server.createContext("/lectures", ex -> { if(checkRoleMulti(ex, "STUDENT", "TEACHER")) serveFile(ex, "lectures.html", "text/html"); });
        server.createContext("/videos/", ex -> serveVideoFile(ex));

        // Study Materials APIs
        server.createContext("/api/materials", (ex) -> { if(checkAuth(ex)) send(ex, 200, "application/json", dbManager.getAllMaterialsAsJson()); });
        server.createContext("/api/teacher/upload_material", (ex) -> handleTeacherUploadMaterial(ex));
        server.createContext("/api/teacher/delete_material", (ex) -> handleTeacherDeleteMaterial(ex));
        server.createContext("/materials", ex -> { if(checkRoleMulti(ex, "STUDENT", "TEACHER")) serveFile(ex, "materials.html", "text/html"); });
        server.createContext("/materials/download", ex -> handleMaterialDownload(ex));
        server.createContext("/files/", ex -> serveMaterialFile(ex));

        // Student Feedback APIs
        server.createContext("/api/student/submit_feedback", (ex) -> handleStudentSubmitFeedback(ex));
        server.createContext("/api/teacher/student_feedback", (ex) -> handleGetStudentFeedback(ex));
        server.createContext("/api/teacher/feedback_count", (ex) -> { if(checkRole(ex, "TEACHER")) send(ex, 200, "application/json", "{\"count\":" + dbManager.getUnreadFeedbackCount() + "}"); });
        server.createContext("/api/teacher/mark_feedback_read", (ex) -> handleMarkFeedbackRead(ex));
        server.createContext("/api/teacher/delete_student_feedback", (ex) -> handleDeleteStudentFeedback(ex));

        // Student Pages
        server.createContext("/tests", ex -> { if(checkRoleMulti(ex, "STUDENT", "TEACHER")) serveFile(ex, "tests.html", "text/html"); });
        server.createContext("/feedback", ex -> { 
            // Allow students, teachers, and admins to access feedback
            if(checkAuth(ex)) {
                String role = getSessionRole(ex);
                if("STUDENT".equals(role)) {
                    serveFile(ex, "feedback.html", "text/html");
                } else if("TEACHER".equals(role)) {
                    serveFile(ex, "teacher_feedback.html", "text/html");
                } else if("ADMIN".equals(role)) {
                    serveFile(ex, "admin_feedback.html", "text/html");
                } else {
                    send(ex, 403, "text/html", "Forbidden");
                }
            }
        });

        // Test Module APIs
        server.createContext("/test_exam", ex -> { if(checkRole(ex, "STUDENT")) serveFile(ex, "test_exam.html", "text/html"); });
        server.createContext("/api/test/questions", ex -> handleGetTestQuestions(ex));
        server.createContext("/api/test/add_question", ex -> handleAddTestQuestion(ex));
        server.createContext("/api/test/update_question", ex -> handleUpdateTestQuestion(ex));
        server.createContext("/api/test/delete_question", ex -> handleDeleteTestQuestion(ex));
        server.createContext("/api/test/schedule", ex -> handleScheduleTest(ex));
        server.createContext("/api/test/scheduled_tests", ex -> handleGetScheduledTests(ex));
        server.createContext("/api/test/update_scheduled", ex -> handleUpdateScheduledTest(ex));
        server.createContext("/api/test/delete_scheduled", ex -> handleDeleteScheduledTest(ex));
        server.createContext("/api/test/active_tests", ex -> handleGetActiveTests(ex));
        server.createContext("/api/test/start", ex -> handleStartTest(ex));
        server.createContext("/api/test/get_questions", ex -> handleGetTestQuestionsForExam(ex));
        server.createContext("/api/test/submit", ex -> handleSubmitTest(ex));
        server.createContext("/api/test/results", ex -> handleGetTestResults(ex));
        server.createContext("/api/test/student_results", ex -> handleGetStudentResults(ex));
        server.createContext("/api/test/upload_video", ex -> handleUploadTestVideo(ex));
        server.createContext("/api/test/video/", ex -> serveTestVideo(ex));
        server.createContext("/api/test/question_count", ex -> handleGetQuestionCount(ex));
        server.createContext("/api/test/check_taken", ex -> handleCheckTestTaken(ex));

        // Admin APIs
        server.createContext("/api/admin/users", (ex) -> handleAdminGetUsers(ex));
        server.createContext("/api/admin/adduser", (ex) -> handleAdminAddUser(ex));
        server.createContext("/api/admin/updateuser", (ex) -> handleAdminUpdateUser(ex));
        server.createContext("/api/admin/deleteuser", (ex) -> handleAdminDeleteUser(ex));
        server.createContext("/api/admin/feedbacks", (ex) -> handleAdminGetFeedback(ex));
        server.createContext("/api/admin/clearfeedback", (ex) -> handleAdminClearFeedback(ex));
        server.createContext("/api/experiments", (ex) -> {
            if(!checkAuth(ex)) return;
            List<Experiment> exps = dbManager.getAllExperiments();
            StringBuilder json = new StringBuilder("[");
            for(int i=0; i<exps.size(); i++) {
                Experiment e = exps.get(i);
                json.append(String.format("{\"id\":%d, \"title\":\"%s\", \"description\":\"%s\", \"filename\":\"%s\", \"category\":\"%s\"}", 
                    e.id(), e.title(), e.description(), e.filename(), e.category()));
                if(i<exps.size()-1) json.append(",");
            }
            json.append("]");
            send(ex, 200, "application/json", json.toString());
        });
        server.createContext("/api/admin/add_experiment", (ex) -> {
            if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "ADMIN")) return;
            Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
            dbManager.addExperiment(f.get("title"), f.get("description"), f.get("filename"), f.get("category"));
            redirect(ex, "/admin_menu");
        });
        server.createContext("/api/admin/delete_experiment", (ex) -> {
            if(!checkRole(ex, "ADMIN")) return;
            dbManager.deleteExperiment(Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("id")));
            redirect(ex, "/admin_menu");
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + port);
        try { if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(new URI("http://localhost:" + port)); } catch (Exception e) {}
    }
    
    // ** New Utility Method to correctly escape JSON strings **
    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\") // Escape backslashes first
                  .replace("\"", "\\\"") // Escape double quotes
                  .replace("\n", "\\n")   // Escape newlines
                  .replace("\r", "\\r");  // Escape carriage returns
    }

    // ** Simple JSON parser for test endpoints **
    private static Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return result;
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        int i = 0;
        while (i < json.length()) {
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;
            
            // Find key
            if (json.charAt(i) != '"') { i++; continue; }
            int keyStart = ++i;
            while (i < json.length() && json.charAt(i) != '"') i++;
            String key = json.substring(keyStart, i);
            i++; // skip closing quote
            
            // Skip to colon
            while (i < json.length() && json.charAt(i) != ':') i++;
            i++; // skip colon
            
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;
            
            // Parse value
            if (json.charAt(i) == '"') {
                // String value
                int valStart = ++i;
                StringBuilder sb = new StringBuilder();
                while (i < json.length() && json.charAt(i) != '"') {
                    if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                        i++;
                        if (json.charAt(i) == 'n') sb.append('\n');
                        else if (json.charAt(i) == 'r') sb.append('\r');
                        else if (json.charAt(i) == 't') sb.append('\t');
                        else sb.append(json.charAt(i));
                    } else {
                        sb.append(json.charAt(i));
                    }
                    i++;
                }
                result.put(key, sb.toString());
                i++; // skip closing quote
            } else if (json.charAt(i) == '[') {
                // Array value - find matching bracket
                int depth = 1;
                int arrStart = i;
                i++;
                while (i < json.length() && depth > 0) {
                    if (json.charAt(i) == '[') depth++;
                    else if (json.charAt(i) == ']') depth--;
                    i++;
                }
                String arrStr = json.substring(arrStart, i);
                // Parse array of strings
                java.util.List<String> arr = new java.util.ArrayList<>();
                int j = 1;
                while (j < arrStr.length() - 1) {
                    while (j < arrStr.length() && arrStr.charAt(j) != '"') j++;
                    if (j >= arrStr.length() - 1) break;
                    j++;
                    StringBuilder sb = new StringBuilder();
                    while (j < arrStr.length() && arrStr.charAt(j) != '"') {
                        if (arrStr.charAt(j) == '\\' && j + 1 < arrStr.length()) {
                            j++;
                            sb.append(arrStr.charAt(j));
                        } else {
                            sb.append(arrStr.charAt(j));
                        }
                        j++;
                    }
                    arr.add(sb.toString());
                    j++;
                }
                result.put(key, arr);
            } else {
                // Number or other value
                int valStart = i;
                while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                String val = json.substring(valStart, i).trim();
                try {
                    result.put(key, Integer.parseInt(val));
                } catch (NumberFormatException e) {
                    result.put(key, val);
                }
            }
            
            // Skip to next key or end
            while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
            if (i < json.length() && json.charAt(i) == ',') i++;
        }
        return result;
    }

    // --- Handlers ---
    private static void handleLogin(HttpExchange ex) throws IOException {
        if ("GET".equals(ex.getRequestMethod())) {
            String html = readFile("login.html").replace("", ex.getRequestURI().getQuery() != null ? "<p style='color:red;text-align:center;'>Invalid credentials.</p>" : "");
            send(ex, 200, "text/html", html);
        } else { 
            Map<String, String> form = parseQuery(new String(ex.getRequestBody().readAllBytes()));
            User user = dbManager.getUser(form.get("username"));
            if (user != null && user.password().equals(form.get("password"))) {
                String token = UUID.randomUUID().toString();
                sessionDatabase.put(token, new UserSession(user.username(), user.role(), user.fullName()));
                ex.getResponseHeaders().set("Set-Cookie", "token=" + token + "; HttpOnly; Path=/");
                redirect(ex, user.role().equals("ADMIN") ? "/admin_menu" : (user.role().equals("TEACHER") ? "/teacher_menu" : "/student_menu"));
            } else { redirect(ex, "/login?error=true"); }
        }
    }
    private static void handleRegister(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/signup"); return; }
        Map<String, String> form = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        String username = form.get("username");
        if (dbManager.getUser(username) != null) { redirect(ex, "/signup?error=exists"); return; }
        dbManager.createUser(new User(username, form.get("password"), "STUDENT", form.get("fullName"), ""));
        redirect(ex, "/login");
    }
    private static void handleLogout(HttpExchange ex) throws IOException { ex.getResponseHeaders().set("Set-Cookie", "token=; Path=/; Max-Age=0"); redirect(ex, "/login"); }
    private static void handleChangePassword(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkAuth(ex)) return;
        UserSession s = getSession(ex); User u = dbManager.getUser(s.username);
        Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        if(u.password().equals(f.get("old_password"))) { dbManager.updatePassword(u.username(), f.get("new_password")); send(ex, 200, "application/json", "{\"success\":true}"); }
        else send(ex, 400, "application/json", "{\"success\":false, \"message\":\"Wrong password\"}");
    }
    private static void handleDeleteAccount(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkAuth(ex)) return;
        UserSession s = getSession(ex); dbManager.deleteUser(s.username); sessionDatabase.remove(getCookie(ex)); handleLogout(ex);
    }
    private static void handleAvatarUpload(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod()) || !checkAuth(ex)) return;
        UserSession s = getSession(ex);
        String base64Image = new String(ex.getRequestBody().readAllBytes());
        boolean success = dbManager.updateAvatar(s.username, base64Image);
        if(success) send(ex, 200, "application/json", "{\"success\": true}");
        else send(ex, 500, "application/json", "{\"success\": false}");
    }
    private static void handleProfileData(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return; UserSession s = getSession(ex); 
        User u = dbManager.getUser(s.username);
        send(ex, 200, "application/json", String.format("{\"fullName\":\"%s\", \"username\":\"%s\", \"role\":\"%s\", \"avatar\":\"%s\"}", u.fullName(), u.username(), u.role(), u.avatar()));
    }
    
    // --- Teacher Handlers ---
    private static void handleTeacherGetStudents(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        List<User> list = dbManager.getUsersByRole("STUDENT");
        StringBuilder json = new StringBuilder("[");
        for(int i=0; i<list.size(); i++) { json.append(String.format("{\"username\":\"%s\", \"fullName\":\"%s\"}", list.get(i).username(), list.get(i).fullName())); if(i<list.size()-1)json.append(","); }
        json.append("]"); send(ex, 200, "application/json", json.toString());
    }
    private static void handleTeacherAddStudent(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        dbManager.createUser(new User(f.get("username"), f.get("password"), "STUDENT", f.get("fullName"), "")); redirect(ex, "/teacher_menu");
    }
    private static void handleTeacherUpdateStudent(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        dbManager.updateStudent(f.get("username"), f.get("password"), f.get("fullName")); redirect(ex, "/teacher_menu");
    }
    private static void handleTeacherDeleteStudent(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return; dbManager.deleteUser(parseQuery(ex.getRequestURI().getQuery()).get("username")); redirect(ex, "/teacher_menu");
    }
    private static void handleTeacherFeedback(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod())) return;
        UserSession s = getSession(ex); Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        dbManager.addFeedback(s.username, f.get("message")); redirect(ex, "/teacher_menu");
    }
    private static void handleTeacherGetPerformance(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        List<QuizPerformance> all = dbManager.getAllPerformance();
        StringBuilder json = new StringBuilder("[");
        for(int i=0; i<all.size(); i++) {
            QuizPerformance p = all.get(i);
            json.append(String.format("{\"student\":\"%s\", \"topic\":\"%s\", \"score\":%d, \"total\":%d, \"date\":\"%s\"}", p.username(), p.topic(), p.score(), p.total(), p.timestamp().toString().split("T")[0]));
            if(i<all.size()-1)json.append(",");
        }
        json.append("]"); send(ex, 200, "application/json", json.toString());
    }
    private static void handleTeacherSaveQuestion(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        String[] opts = {f.get("opt0"), f.get("opt1"), f.get("opt2"), f.get("opt3")};
        Question q = new Question(f.get("topic"), f.get("question"), opts, Integer.parseInt(f.get("correctIndex")), f.get("explanation"));
        if(f.containsKey("id") && !f.get("id").isEmpty()) dbManager.updateQuestion(Integer.parseInt(f.get("id")), q); else dbManager.addQuestion(q);
        redirect(ex, "/teacher_menu");
    }
    private static void handleTeacherDeleteQuestion(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return; dbManager.deleteQuestion(Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("id"))); redirect(ex, "/teacher_menu");
    }

    // --- Lecture Handlers ---
    private static void handleTeacherAddLecture(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        UserSession s = getSession(ex);
        Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        dbManager.addLecture(f.get("title"), f.get("summary"), f.get("videoUrl"), f.get("category"), s.username);
        redirect(ex, "/teacher_menu");
    }
    private static void handleTeacherDeleteLecture(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        dbManager.deleteLecture(Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("id")));
        redirect(ex, "/teacher_menu");
    }

    private static void handleTeacherUploadVideo(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        UserSession s = getSession(ex);
        
        // Create videos directory if it doesn't exist
        File videosDir = new File("videos");
        if (!videosDir.exists()) videosDir.mkdir();
        
        // Parse multipart form data
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            send(ex, 400, "application/json", "{\"success\":false,\"error\":\"Invalid content type\"}");
            return;
        }
        
        String boundary = "--" + contentType.split("boundary=")[1];
        byte[] bodyBytes = ex.getRequestBody().readAllBytes();
        
        // Parse form fields
        String title = "", summary = "", category = "";
        String videoFileName = "";
        byte[] videoData = null;
        
        // Find all parts by searching for boundary in bytes
        byte[] boundaryBytes = boundary.getBytes(StandardCharsets.UTF_8);
        int pos = 0;
        
        while (pos < bodyBytes.length) {
            // Find start of boundary
            int boundaryStart = indexOf(bodyBytes, boundaryBytes, pos);
            if (boundaryStart == -1) break;
            
            // Find end of this part (next boundary)
            int nextBoundary = indexOf(bodyBytes, boundaryBytes, boundaryStart + boundaryBytes.length);
            if (nextBoundary == -1) nextBoundary = bodyBytes.length;
            
            // Extract this part
            int partStart = boundaryStart + boundaryBytes.length;
            // Skip \r\n after boundary
            if (partStart < bodyBytes.length && bodyBytes[partStart] == '\r') partStart++;
            if (partStart < bodyBytes.length && bodyBytes[partStart] == '\n') partStart++;
            
            int partEnd = nextBoundary;
            // Remove trailing \r\n before next boundary
            if (partEnd > 2 && bodyBytes[partEnd-1] == '\n' && bodyBytes[partEnd-2] == '\r') partEnd -= 2;
            
            if (partStart < partEnd) {
                byte[] partBytes = Arrays.copyOfRange(bodyBytes, partStart, partEnd);
                
                // Find header/body separator (\r\n\r\n)
                int headerEnd = indexOf(partBytes, "\r\n\r\n".getBytes(), 0);
                if (headerEnd != -1) {
                    String header = new String(partBytes, 0, headerEnd, StandardCharsets.UTF_8);
                    int bodyStart = headerEnd + 4;
                    
                    if (header.contains("name=\"title\"")) {
                        title = new String(partBytes, bodyStart, partBytes.length - bodyStart, StandardCharsets.UTF_8).trim();
                    } else if (header.contains("name=\"summary\"")) {
                        summary = new String(partBytes, bodyStart, partBytes.length - bodyStart, StandardCharsets.UTF_8).trim();
                    } else if (header.contains("name=\"category\"")) {
                        category = new String(partBytes, bodyStart, partBytes.length - bodyStart, StandardCharsets.UTF_8).trim();
                    } else if (header.contains("name=\"videoFile\"")) {
                        // Extract filename
                        if (header.contains("filename=\"")) {
                            int fnStart = header.indexOf("filename=\"") + 10;
                            int fnEnd = header.indexOf("\"", fnStart);
                            String origName = header.substring(fnStart, fnEnd);
                            String ext = origName.contains(".") ? origName.substring(origName.lastIndexOf(".")) : ".mp4";
                            videoFileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0,8) + ext;
                            
                            // Video data is everything after the header
                            videoData = Arrays.copyOfRange(partBytes, bodyStart, partBytes.length);
                        }
                    }
                }
            }
            
            pos = nextBoundary;
        }
        
        if (videoFileName.isEmpty() || videoData == null || videoData.length == 0 || title.isEmpty()) {
            send(ex, 400, "application/json", "{\"success\":false,\"error\":\"Missing required fields or empty video\"}");
            return;
        }
        
        // Save video file
        try (FileOutputStream fos = new FileOutputStream(new File(videosDir, videoFileName))) {
            fos.write(videoData);
        }
        
        String videoUrl = "/videos/" + videoFileName;
        dbManager.addLecture(title, summary, videoUrl, category, s.username);
        
        send(ex, 200, "application/json", "{\"success\":true,\"videoUrl\":\"" + videoUrl + "\"}");
    }
    
    // Helper method to find byte array in another byte array
    private static int indexOf(byte[] source, byte[] target, int fromIndex) {
        if (target.length == 0) return fromIndex;
        if (target.length > source.length - fromIndex) return -1;
        
        outer: for (int i = fromIndex; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static void serveVideoFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String fileName = path.substring("/videos/".length());
        File videoFile = new File("videos", fileName);
        
        if (!videoFile.exists()) {
            send(ex, 404, "text/plain", "Video not found");
            return;
        }
        
        String contentType = "video/mp4";
        if (fileName.endsWith(".webm")) contentType = "video/webm";
        else if (fileName.endsWith(".ogg")) contentType = "video/ogg";
        else if (fileName.endsWith(".mov")) contentType = "video/quicktime";
        else if (fileName.endsWith(".avi")) contentType = "video/x-msvideo";
        
        byte[] fileBytes = Files.readAllBytes(videoFile.toPath());
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        ex.sendResponseHeaders(200, fileBytes.length);
        ex.getResponseBody().write(fileBytes);
        ex.getResponseBody().close();
    }

    // --- Study Materials Handlers ---
    private static void handleTeacherUploadMaterial(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        UserSession s = getSession(ex);
        
        File materialsDir = new File("materials");
        if (!materialsDir.exists()) materialsDir.mkdir();
        
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            send(ex, 400, "application/json", "{\"success\":false,\"error\":\"Invalid content type\"}");
            return;
        }
        
        String boundary = contentType.split("boundary=")[1];
        byte[] bodyBytes = ex.getRequestBody().readAllBytes();
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        
        String title = "", description = "", category = "";
        String materialFileName = "", originalFileName = "", fileType = "";
        byte[] fileData = null;
        
        String[] parts = body.split("--" + boundary);
        for (String part : parts) {
            if (part.contains("name=\"title\"")) {
                title = part.split("\\r\\n\\r\\n")[1].trim().split("\\r\\n")[0];
            } else if (part.contains("name=\"description\"")) {
                description = part.split("\\r\\n\\r\\n")[1].trim().split("\\r\\n")[0];
            } else if (part.contains("name=\"category\"")) {
                category = part.split("\\r\\n\\r\\n")[1].trim().split("\\r\\n")[0];
            } else if (part.contains("name=\"materialFile\"")) {
                if (part.contains("filename=\"")) {
                    int fnStart = part.indexOf("filename=\"") + 10;
                    int fnEnd = part.indexOf("\"", fnStart);
                    originalFileName = part.substring(fnStart, fnEnd);
                    String ext = originalFileName.contains(".") ? originalFileName.substring(originalFileName.lastIndexOf(".")) : "";
                    materialFileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0,8) + ext;
                    
                    fileType = ext.replace(".", "").toUpperCase();
                    if (fileType.isEmpty()) fileType = "FILE";
                    
                    String partHeader = part.split("\\r\\n\\r\\n")[0];
                    int headerEndInBody = body.indexOf(partHeader) + partHeader.length() + 4;
                    
                    int dataStart = 0, dataEnd = 0;
                    for (int i = 0, pos = 0; i < bodyBytes.length && pos <= headerEndInBody; i++) {
                        if (bodyBytes[i] == body.charAt(pos)) pos++;
                        else pos = 0;
                        if (pos == headerEndInBody) { dataStart = i + 1; break; }
                    }
                    
                    byte[] boundaryBytes = ("\r\n--" + boundary).getBytes(StandardCharsets.UTF_8);
                    outer: for (int i = dataStart; i < bodyBytes.length - boundaryBytes.length; i++) {
                        for (int j = 0; j < boundaryBytes.length; j++) {
                            if (bodyBytes[i + j] != boundaryBytes[j]) continue outer;
                        }
                        dataEnd = i;
                        break;
                    }
                    if (dataEnd > dataStart) {
                        fileData = Arrays.copyOfRange(bodyBytes, dataStart, dataEnd);
                    }
                }
            }
        }
        
        if (materialFileName.isEmpty() || fileData == null || title.isEmpty()) {
            send(ex, 400, "application/json", "{\"success\":false,\"error\":\"Missing required fields\"}");
            return;
        }
        
        try (FileOutputStream fos = new FileOutputStream(new File(materialsDir, materialFileName))) {
            fos.write(fileData);
        }
        
        String fileSize = fileData.length < 1024 ? fileData.length + " B" : 
                          fileData.length < 1024*1024 ? String.format("%.1f KB", fileData.length/1024.0) :
                          String.format("%.1f MB", fileData.length/(1024.0*1024.0));
        
        String filePath = "/files/" + materialFileName;
        dbManager.addMaterial(title, description, filePath, fileType, fileSize, category, s.username);
        
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    private static void handleTeacherDeleteMaterial(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        int id = Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("id"));
        String filePath = dbManager.getMaterialFilePath(id);
        if (filePath != null && filePath.startsWith("/files/")) {
            File file = new File("materials", filePath.substring("/files/".length()));
            if (file.exists()) file.delete();
        }
        dbManager.deleteMaterial(id);
        redirect(ex, "/teacher_menu");
    }

    private static void handleMaterialDownload(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        int id = Integer.parseInt(params.get("id"));
        String filePath = dbManager.getMaterialFilePath(id);
        if (filePath == null || !filePath.startsWith("/files/")) {
            send(ex, 404, "text/plain", "File not found");
            return;
        }
        String fileName = filePath.substring("/files/".length());
        File file = new File("materials", fileName);
        if (!file.exists()) {
            send(ex, 404, "text/plain", "File not found");
            return;
        }
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        ex.sendResponseHeaders(200, fileBytes.length);
        ex.getResponseBody().write(fileBytes);
        ex.getResponseBody().close();
    }

    private static void serveMaterialFile(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        String path = ex.getRequestURI().getPath();
        String fileName = path.substring("/files/".length());
        File file = new File("materials", fileName);
        
        if (!file.exists()) {
            send(ex, 404, "text/plain", "File not found");
            return;
        }
        
        String contentType = "application/octet-stream";
        if (fileName.endsWith(".pdf")) contentType = "application/pdf";
        else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) contentType = "application/msword";
        else if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) contentType = "application/vnd.ms-powerpoint";
        else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) contentType = "application/vnd.ms-excel";
        else if (fileName.endsWith(".txt")) contentType = "text/plain";
        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (fileName.endsWith(".png")) contentType = "image/png";
        
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, fileBytes.length);
        ex.getResponseBody().write(fileBytes);
        ex.getResponseBody().close();
    }

    // --- Student Feedback Handlers ---
    private static void handleStudentSubmitFeedback(HttpExchange ex) throws IOException {
        System.out.println("📨 Feedback API called - Method: " + ex.getRequestMethod());
        if(!"POST".equals(ex.getRequestMethod())) {
            System.out.println("❌ Not a POST request");
            return;
        }
        UserSession s = getSession(ex);
        if(s == null || !"STUDENT".equals(s.role)) {
            System.out.println("❌ No valid student session");
            redirect(ex, "/login");
            return;
        }
        Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        System.out.println("📝 Feedback from: " + s.username + " | Subject: " + f.get("subject"));
        dbManager.addStudentFeedback(s.username, s.fullName, f.get("subject"), f.get("message"));
        System.out.println("✅ Feedback saved to database!");
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    private static void handleGetStudentFeedback(HttpExchange ex) throws IOException {
        System.out.println("📋 Teacher requesting student feedback...");
        UserSession s = getSession(ex);
        if(s == null || !"TEACHER".equals(s.role)) {
            System.out.println("❌ Not a valid teacher session");
            send(ex, 401, "application/json", "{\"error\":\"Unauthorized\"}");
            return;
        }
        String json = dbManager.getAllStudentFeedbackAsJson();
        System.out.println("📤 Returning feedback JSON: " + json);
        send(ex, 200, "application/json", json);
    }

    private static void handleMarkFeedbackRead(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        int id = Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("id"));
        dbManager.markFeedbackAsRead(id);
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    private static void handleDeleteStudentFeedback(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        int id = Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("id"));
        dbManager.deleteStudentFeedback(id);
        redirect(ex, "/teacher_menu");
    }

    // === TEST MODULE HANDLERS ===
    private static void handleGetTestQuestions(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        send(ex, 200, "application/json", dbManager.getAllTestQuestionsAsJson());
    }

    @SuppressWarnings("unchecked")
    private static void handleAddTestQuestion(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        UserSession s = getSession(ex);
        String body = new String(ex.getRequestBody().readAllBytes());
        Map<String, Object> json = parseJson(body);
        java.util.List<String> opts = (java.util.List<String>) json.get("options");
        dbManager.addTestQuestion(
            (String) json.get("subject"), 
            (String) json.get("question"), 
            opts.get(0), opts.get(1), opts.get(2), opts.get(3), 
            ((Number) json.get("correctIndex")).intValue(), 
            s.username
        );
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    @SuppressWarnings("unchecked")
    private static void handleUpdateTestQuestion(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        String body = new String(ex.getRequestBody().readAllBytes());
        Map<String, Object> json = parseJson(body);
        java.util.List<String> opts = (java.util.List<String>) json.get("options");
        dbManager.updateTestQuestion(
            ((Number) json.get("id")).intValue(),
            (String) json.get("subject"), 
            (String) json.get("question"), 
            opts.get(0), opts.get(1), opts.get(2), opts.get(3), 
            ((Number) json.get("correctIndex")).intValue()
        );
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    private static void handleDeleteTestQuestion(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        int id = Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("id"));
        dbManager.deleteTestQuestion(id);
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    private static void handleScheduleTest(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        UserSession s = getSession(ex);
        String body = new String(ex.getRequestBody().readAllBytes());
        Map<String, Object> json = parseJson(body);
        int id = dbManager.scheduleTest(
            (String) json.get("title"), 
            "", // subject not used
            ((Number) json.get("duration")).intValue(), 
            ((Number) json.get("numQuestions")).intValue(), 
            (String) json.get("scheduledDate"), 
            (String) json.get("scheduledTime"), 
            s.username
        );
        send(ex, 200, "application/json", "{\"success\":true,\"id\":" + id + "}");
    }

    private static void handleGetScheduledTests(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        send(ex, 200, "application/json", dbManager.getAllScheduledTestsAsJson());
    }

    private static void handleUpdateScheduledTest(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "TEACHER")) return;
        String body = new String(ex.getRequestBody().readAllBytes());
        Map<String, Object> json = parseJson(body);
        dbManager.updateScheduledTest(
            ((Number) json.get("id")).intValue(),
            (String) json.get("title"), 
            "", 
            ((Number) json.get("duration")).intValue(), 
            ((Number) json.get("numQuestions")).intValue(), 
            (String) json.get("scheduledDate"), 
            (String) json.get("scheduledTime"), 
            "active"
        );
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    private static void handleDeleteScheduledTest(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        int id = Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("id"));
        dbManager.deleteScheduledTest(id);
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    private static void handleGetActiveTests(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "STUDENT")) return;
        send(ex, 200, "application/json", dbManager.getActiveTestsForStudent());
    }

    private static void handleStartTest(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "STUDENT")) return;
        UserSession s = getSession(ex);
        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        int testId = Integer.parseInt(params.get("testId"));
        if(dbManager.hasStudentTakenTest(testId, s.username)) {
            send(ex, 200, "application/json", "{\"success\":false,\"error\":\"already_taken\"}");
            return;
        }
        int resultId = dbManager.startTestResult(testId, s.username, s.fullName);
        send(ex, 200, "application/json", "{\"success\":true,\"resultId\":" + resultId + "}");
    }

    private static void handleGetTestQuestionsForExam(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "STUDENT")) return;
        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        int testId = Integer.parseInt(params.get("testId"));
        // Get the num_questions from the scheduled test
        int count = dbManager.getScheduledTestQuestionCount(testId);
        if (count <= 0) count = 10; // Default to 10
        send(ex, 200, "application/json", dbManager.getRandomTestQuestions(count));
    }

    private static void handleSubmitTest(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "STUDENT")) return;
        String body = new String(ex.getRequestBody().readAllBytes());
        Map<String, Object> json = parseJson(body);
        int resultId = ((Number) json.get("resultId")).intValue();
        int score = ((Number) json.get("score")).intValue();
        int total = ((Number) json.get("totalQuestions")).intValue();
        String videoPath = json.get("videoPath") != null ? (String) json.get("videoPath") : "";
        String timeTaken = json.get("timeTaken") != null ? (String) json.get("timeTaken") : "";
        dbManager.completeTestResult(resultId, score, total, videoPath, timeTaken);
        send(ex, 200, "application/json", "{\"success\":true}");
    }

    private static void handleGetTestResults(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        send(ex, 200, "application/json", dbManager.getAllTestResultsAsJson());
    }

    private static void handleGetStudentResults(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "STUDENT")) return;
        UserSession s = getSession(ex);
        send(ex, 200, "application/json", dbManager.getStudentTestResults(s.username));
    }

    private static void handleUploadTestVideo(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "STUDENT")) return;
        UserSession s = getSession(ex);
        
        // Create test_videos directory if it doesn't exist
        java.io.File videoDir = new java.io.File("test_videos");
        if (!videoDir.exists()) videoDir.mkdirs();
        
        // Generate unique filename
        String filename = "test_" + s.username + "_" + System.currentTimeMillis() + ".webm";
        java.io.File videoFile = new java.io.File(videoDir, filename);
        
        // Read all data from request body
        byte[] allData = ex.getRequestBody().readAllBytes();
        
        // Parse multipart form data to extract the video bytes
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.contains("multipart/form-data")) {
            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            byte[] boundaryBytes = ("--" + boundary).getBytes();
            
            // Find the start of the video content (after the headers)
            int dataStart = -1;
            for (int i = 0; i < allData.length - 4; i++) {
                // Look for \r\n\r\n which marks end of headers
                if (allData[i] == '\r' && allData[i+1] == '\n' && allData[i+2] == '\r' && allData[i+3] == '\n') {
                    dataStart = i + 4;
                    break;
                }
            }
            
            // Find the end boundary
            int dataEnd = allData.length;
            for (int i = dataStart; i < allData.length - boundaryBytes.length; i++) {
                boolean found = true;
                for (int j = 0; j < boundaryBytes.length && found; j++) {
                    if (allData[i + j] != boundaryBytes[j]) found = false;
                }
                if (found) {
                    dataEnd = i - 2; // -2 for the \r\n before boundary
                    break;
                }
            }
            
            if (dataStart > 0 && dataEnd > dataStart) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(videoFile)) {
                    fos.write(allData, dataStart, dataEnd - dataStart);
                }
            }
        } else {
            // Raw video data
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(videoFile)) {
                fos.write(allData);
            }
        }
        
        send(ex, 200, "application/json", "{\"success\":true,\"videoPath\":\"" + filename + "\"}");
    }

    private static void serveTestVideo(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "TEACHER")) return;
        String path = ex.getRequestURI().getPath();
        String filename = path.substring("/api/test/video/".length());
        java.io.File videoFile = new java.io.File("test_videos", filename);
        
        if (!videoFile.exists()) {
            send(ex, 404, "text/plain", "Video not found");
            return;
        }
        
        ex.getResponseHeaders().set("Content-Type", "video/webm");
        ex.sendResponseHeaders(200, videoFile.length());
        try (java.io.FileInputStream fis = new java.io.FileInputStream(videoFile);
             java.io.OutputStream os = ex.getResponseBody()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }

    private static void handleGetQuestionCount(HttpExchange ex) throws IOException {
        if(!checkAuth(ex)) return;
        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        String subject = params.get("subject") != null ? params.get("subject") : "all";
        int count = dbManager.getTestQuestionCount(subject);
        send(ex, 200, "application/json", "{\"count\":" + count + "}");
    }

    private static void handleCheckTestTaken(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "STUDENT")) return;
        UserSession s = getSession(ex);
        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        int testId = Integer.parseInt(params.get("testId"));
        boolean taken = dbManager.hasStudentTakenTest(testId, s.username);
        send(ex, 200, "application/json", "{\"taken\":" + taken + "}");
    }

    // --- Admin Handlers ---
    private static void handleAdminGetUsers(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "ADMIN")) return;
        List<User> users = dbManager.getUsersByRole("TEACHER"); users.addAll(dbManager.getUsersByRole("STUDENT"));
        StringBuilder json = new StringBuilder("[");
        for(int i=0; i<users.size(); i++) {
             json.append(String.format("{\"username\":\"%s\", \"role\":\"%s\", \"fullName\":\"%s\"}", users.get(i).username(), users.get(i).role(), users.get(i).fullName()));
             if(i < users.size()-1) json.append(",");
        }
        json.append("]"); send(ex, 200, "application/json", json.toString());
    }
    private static void handleAdminAddUser(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "ADMIN")) return;
        Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        dbManager.createUser(new User(f.get("username"), f.get("password"), f.get("role"), f.get("fullName"), "")); 
        redirect(ex, "/admin_menu");
    }
    private static void handleAdminUpdateUser(HttpExchange ex) throws IOException {
        if(!"POST".equals(ex.getRequestMethod()) || !checkRole(ex, "ADMIN")) return;
        Map<String, String> f = parseQuery(new String(ex.getRequestBody().readAllBytes()));
        dbManager.updateUser(f.get("username"), f.get("password"), f.get("fullName"), f.get("role")); redirect(ex, "/admin_menu");
    }
    private static void handleAdminDeleteUser(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "ADMIN")) return;
        dbManager.deleteUser(parseQuery(ex.getRequestURI().getQuery()).get("username")); redirect(ex, "/admin_menu");
    }
    private static void handleAdminGetFeedback(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "ADMIN")) return;
        List<String> fbs = dbManager.getAllFeedback();
        StringBuilder json = new StringBuilder("[");
        for(int i=0; i<fbs.size(); i++) { json.append("\"").append(fbs.get(i)).append("\""); if(i < fbs.size()-1) json.append(","); }
        json.append("]"); send(ex, 200, "application/json", json.toString());
    }
    private static void handleAdminClearFeedback(HttpExchange ex) throws IOException {
        if(!checkRole(ex, "ADMIN")) return; dbManager.clearFeedback(); redirect(ex, "/admin_menu");
    }

    // --- Experiments ---
    private static void calculateOhmsLaw(HttpExchange ex) throws IOException {
        Map<String, String> p = parseQuery(ex.getRequestURI().getQuery());
        try { double v = Double.parseDouble(p.get("voltage")), r = Double.parseDouble(p.get("resistance")); send(ex, 200, "application/json", "{\"current\": " + ((r==0)?0:v/r) + "}"); } catch(Exception e) { send(ex, 400, "text/plain", "Bad Request"); }
    }
    private static void getMatterState(HttpExchange ex) throws IOException {
        try { int t = Integer.parseInt(parseQuery(ex.getRequestURI().getQuery()).get("temperature"));
        send(ex, 200, "application/json", String.format("{\"state\": \"%s\", \"description\": \"State changed.\"}", (t<0?"Solid":(t<100?"Liquid":"Gas")))); } catch(Exception e) { send(ex, 400, "text/plain", "Bad Request"); }
    }
    private static void calculatePendulum(HttpExchange ex) throws IOException {
        Map<String, String> p = parseQuery(ex.getRequestURI().getQuery());
        try { double l = Double.parseDouble(p.get("length")); send(ex, 200, "application/json", String.format("{\"timePeriod\": %.3f}", 2*Math.PI*Math.sqrt(l/9.81))); } catch(Exception e) { send(ex, 400, "text/plain", "Bad Request"); }
    }
    private static void calculatePh(HttpExchange ex) throws IOException {
        try { double ph = Double.parseDouble(parseQuery(ex.getRequestURI().getQuery()).get("ph"));
            String c, color, examples;
            if (ph < 3) { c = "Strong Acid"; color = "#d90429"; examples = "Battery Acid, Stomach Acid"; }
            else if (ph < 5) { c = "Weak Acid"; color = "#f77f00"; examples = "Lemon Juice, Vinegar, Orange Juice"; }
            else if (ph < 7) { c = "Mild Acid"; color = "#fcbf49"; examples = "Coffee, Milk, Tomatoes"; }
            else if (ph == 7) { c = "Neutral"; color = "#2a9d8f"; examples = "Pure Water, Blood"; }
            else if (ph < 9) { c = "Mild Alkaline"; color = "#007f5f"; examples = "Baking Soda, Sea Water, Eggs"; }
            else if (ph < 12) { c = "Weak Alkaline"; color = "#005f73"; examples = "Soap, Toothpaste, Antacids"; }
            else { c = "Strong Alkaline"; color = "#03045e"; examples = "Bleach, Oven Cleaner, Drain Cleaner"; }
            send(ex, 200, "application/json", String.format("{\"classification\": \"%s\", \"color\": \"%s\", \"examples\": \"%s\"}", c, color, examples));
        } catch (Exception e) { send(ex, 400, "text/plain", "Bad Request"); }
    }
    private static void calculateBeam(HttpExchange ex) throws IOException {
        Map<String, String> p = parseQuery(ex.getRequestURI().getQuery());
        try { double l = Double.parseDouble(p.get("length")), load = Double.parseDouble(p.get("load")); send(ex, 200, "application/json", String.format("{\"deflectionDegrees\": %.2f}", (load * Math.pow(l, 3)) / 50.0)); } catch(Exception e) { send(ex, 400, "text/plain", "Bad Request"); }
    }
    private static void calculateGears(HttpExchange ex) throws IOException {
        Map<String, String> p = parseQuery(ex.getRequestURI().getQuery());
        try { double s1 = Double.parseDouble(p.get("speed1")), r1 = Double.parseDouble(p.get("r1")), r2 = Double.parseDouble(p.get("r2")); send(ex, 200, "application/json", String.format("{\"speed2\": %.2f, \"direction2\": \"Opposite\"}", s1 * (r1/r2))); } catch(Exception e) { send(ex, 400, "text/plain", "Bad Request"); }
    }

    // --- Quiz ---
    private static void handleGetQuiz(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return; UserSession session = getSession(ex);
        String topic = parseQuery(ex.getRequestURI().getQuery()).getOrDefault("topic", "physics");
        List<Question> questions = dbManager.getRandomQuestions(topic, 5);
        session.currentQuiz = questions;
        StringBuilder json = new StringBuilder("[");
        for(int i=0; i<questions.size(); i++){
            Question q = questions.get(i);
            json.append("{");
            // ** Use the new jsonEscape method here **
            json.append("\"question\": \"").append(jsonEscape(q.question())).append("\",");
            json.append("\"explanation\": \"").append(jsonEscape(q.explanation())).append("\",");
            json.append("\"options\": [");
            for(int j=0; j<q.options().length; j++) { json.append("\"").append(jsonEscape(q.options()[j])).append("\""); if(j<q.options().length-1)json.append(","); }
            json.append("], \"correctIndex\": ").append(q.correctIndex()).append("}");
            if(i<questions.size()-1)json.append(",");
        }
        json.append("]");
        send(ex, 200, "application/json", json.toString());
    }
    private static void handleSubmitQuiz(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) return; UserSession session = getSession(ex);
        if (session == null || session.currentQuiz == null) return;
        String body = new String(ex.getRequestBody().readAllBytes()); Map<String, String> answers = parseQuery(body);
        int score = 0;
        for (int i = 0; i < session.currentQuiz.size(); i++) {
            String ans = answers.get("question-" + i);
            if (ans != null && Integer.parseInt(ans) == session.currentQuiz.get(i).correctIndex()) score++;
        }
        dbManager.saveQuizPerformance(new QuizPerformance(session.username, session.currentQuiz.get(0).topic(), score, session.currentQuiz.size(), LocalDateTime.now()));
        session.currentQuiz = null;
        send(ex, 200, "application/json", "{\"score\": " + score + "}");
    }

    // --- Data ---
    private static void handleDashboardData(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return; UserSession s = getSession(ex); List<QuizPerformance> scores = dbManager.getPerformance(s.username);
        StringBuilder json = new StringBuilder("{\"fullName\": \"" + s.fullName + "\", \"recentScores\": [");
        for(int i=0; i<scores.size(); i++) {
            QuizPerformance p = scores.get(i);
            json.append(String.format("{\"topic\":\"%s\", \"score\":%d, \"total\":%d}", p.topic(), p.score(), p.total()));
            if(i < scores.size()-1) json.append(",");
        }
        json.append("], \"averageScores\": {} }"); send(ex, 200, "application/json", json.toString());
    }

    // --- Utilities ---
    private static boolean checkAuth(HttpExchange ex) throws IOException { if (getSession(ex) != null) return true; redirect(ex, "/login"); return false; }
    private static boolean checkRole(HttpExchange ex, String role) throws IOException { UserSession s = getSession(ex); if (s != null && s.role.equals(role)) return true; redirect(ex, "/login"); return false; }
    private static boolean checkRoleMulti(HttpExchange ex, String... roles) throws IOException { UserSession s = getSession(ex); if (s != null) { for(String role : roles) { if(s.role.equals(role)) return true; } } redirect(ex, "/login"); return false; }
    private static String getSessionRole(HttpExchange ex) { UserSession s = getSession(ex); return (s != null) ? s.role : null; }
    private static UserSession getSession(HttpExchange ex) { String c = ex.getRequestHeaders().getFirst("Cookie"); if (c != null && c.contains("token=")) return sessionDatabase.get(c.split("token=")[1].split(";")[0]); return null; }
    private static String getCookie(HttpExchange ex) { String c = ex.getRequestHeaders().getFirst("Cookie"); return (c != null && c.contains("token=")) ? c.split("token=")[1].split(";")[0] : null; }
    private static void redirect(HttpExchange ex, String loc) throws IOException { ex.getResponseHeaders().set("Location", loc); ex.sendResponseHeaders(302, -1); }
    private static void send(HttpExchange ex, int code, String type, String body) throws IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type", type); ex.sendResponseHeaders(code, bytes.length); ex.getResponseBody().write(bytes); ex.getResponseBody().close(); }
    private static void serveFile(HttpExchange ex, String f, String type) throws IOException { try { send(ex, 200, type, new String(Files.readAllBytes(Paths.get(f)), StandardCharsets.UTF_8)); } catch (Exception e) { send(ex, 404, "text/plain", "File missing: " + f); } }
    private static Map<String, String> parseQuery(String q) { Map<String, String> map = new HashMap<>(); if (q == null) return map; for (String s : q.split("&")) { String[] p = s.split("="); if(p.length==2) { try { map.put(URLDecoder.decode(p[0], "UTF-8"), URLDecoder.decode(p[1], "UTF-8")); } catch(Exception e) {} } } return map; }
    private static String readFile(String f) throws IOException { return new String(Files.readAllBytes(Paths.get(f)), StandardCharsets.UTF_8); }
}