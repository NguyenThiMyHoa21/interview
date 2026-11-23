# Intervia Backend (Java - Spring Boot 3)

Backend cho nền tảng luyện phỏng vấn kỹ thuật **Intervia**, được viết bằng **Java 21 / 17** và **Spring Boot 3.4.2**, kết nối trực tiếp với **MongoDB** và **Google Gemini 3.5 Flash**.

---

## 1. Cấu trúc dự án (Chuẩn MVC / 3-Tier Layered Architecture)

```
backend-java/
├── src/main/java/com/intervia/
│   ├── InterviaApplication.java       # File chạy chính của ứng dụng (@SpringBootApplication)
│   ├── config/
│   │   ├── CorsConfig.java            # Cấu hình CORS cho phép Frontend React (port 5173) gọi API
│   │   ├── GlobalExceptionHandler.java# Xử lý lỗi tập trung, trả về format JSON chuẩn { "error": { ... } }
│   │   ├── ApiException.java          # Custom exception chuẩn cho nghiệp vụ
│   │   └── AppConstants.java          # Các hằng số mặc định (DEFAULT_USER_ID, ...)
│   ├── controller/                    # Tầng Controller (tiếp nhận HTTP Request từ web)
│   │   ├── AuthController.java        # /api/auth/* (Kiểm tra trạng thái đăng nhập, thông tin user)
│   │   ├── CvController.java          # /api/cvs/* (Tải CV, đọc CV, lấy các phiên bản hồ sơ)
│   │   ├── JdController.java          # /api/jds/* (Tạo JD, trích xuất yêu cầu, lưu phiên bản)
│   │   ├── SessionController.java     # /api/sessions/* (Tạo phiên, sinh Blueprint, phỏng vấn SSE, báo cáo)
│   │   ├── AnswerController.java      # /api/answers/* (Chỉnh sửa câu trả lời của ứng viên)
│   │   ├── RoleController.java        # /api/roles (Danh sách vị trí tuyển dụng mẫu)
│   │   └── HealthController.java      # /api/health (Kiểm tra trạng thái máy chủ và MongoDB)
│   ├── service/                       # Tầng Service (xử lý nghiệp vụ logic & AI)
│   │   ├── GeminiService.java         # Gọi Google Gemini 3.5 Flash REST API trích xuất & sinh câu hỏi
│   │   ├── FileParserService.java     # Đọc nội dung file CV .docx (Apache POI) và .pdf (PDFBox)
│   │   ├── CvService.java             # Nghiệp vụ quản lý và phân tích CV
│   │   ├── JdService.java             # Nghiệp vụ quản lý và phân tích JD
│   │   ├── SessionService.java        # Quản lý luồng phỏng vấn, tạo câu hỏi tiếp theo theo tiến trình
│   │   ├── ReportService.java         # Đánh giá buổi phỏng vấn (A7 Evaluator + A8 Coach) và thống kê
│   │   └── AuthService.java           # Quản lý phiên làm việc của người dùng
│   ├── model/                         # Tầng Entity / Document (ánh xạ vào MongoDB)
│   │   ├── CvDocument.java            # Collection: `cvs`
│   │   ├── CvVersionDocument.java     # Collection: `cv_versions`
│   │   ├── JdDocument.java            # Collection: `jds`
│   │   ├── JdVersionDocument.java     # Collection: `jd_versions`
│   │   ├── SessionDocument.java       # Collection: `sessions`
│   │   ├── BlueprintDocument.java     # Collection: `blueprints`
│   │   ├── SessionReportDocument.java # Collection: `session_reports`
│   │   ├── QuestionDocument.java      # Collection: `questions`
│   │   ├── AnswerDocument.java        # Collection: `answers`
│   │   ├── SessionEventDocument.java  # Collection: `session_events`
│   │   └── UserDocument.java          # Collection: `users`
│   ├── repository/                    # Tầng Repository (kế thừa Spring Data MongoRepository)
│   │   ├── CvRepository.java
│   │   ├── JdRepository.java
│   │   ├── SessionRepository.java
│   │   ├── BlueprintRepository.java
│   │   ├── SessionReportRepository.java
│   │   └── ...
│   └── dto/                           # Data Transfer Object (Request/Response API)
│       ├── CvDto.java
│       ├── JdDto.java
│       ├── SessionDto.java
│       ├── ReportDto.java
│       └── ...
└── src/main/resources/
    └── application.yml                # Cấu hình cổng 8000, kết nối MongoDB và API key Gemini
```

---

## 2. Cách chạy Backend Java

### Cách 1: Chạy bằng dòng lệnh (Terminal)
Từ thư mục `backend-java`:
```bash
.\mvnw.cmd spring-boot:run
```
Máy chủ Spring Boot sẽ khởi động ở cổng **http://localhost:8000**.

### Cách 2: Chạy trong IntelliJ IDEA
1. Mở IntelliJ IDEA -> Chọn **Open** -> Trỏ vào thư mục `intervia-develop/backend-java`.
2. IntelliJ sẽ tự động nhận diện dự án Maven.
3. Mở file `src/main/java/com/intervia/InterviaApplication.java` và nhấn nút tam giác xanh (**Run**).

### Cách 3: Chạy trong VS Code
1. Mở thư mục `backend-java` trong VS Code.
2. Cài extension **Extension Pack for Java** và **Spring Boot Extension Pack**.
3. Mở `InterviaApplication.java` và chọn **Run Java** (hoặc nhấn `F5`).

---

## 3. Kết nối với Frontend React

Frontend (`web/`) đã được cấu hình proxy tự động chuyển tiếp toàn bộ yêu cầu `/api/*` tới cổng `8000`:
* Bật backend Java: cổng `8000`
* Bật frontend React (`npm run dev` trong thư mục `web/`): cổng `5173`
* Mở trình duyệt tại **http://localhost:5173** để sử dụng hoàn toàn bình thường!
