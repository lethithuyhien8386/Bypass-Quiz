# AI Quiz Scanner

Ứng dụng Android native: **chụp màn hình → gửi ảnh tới Gemini → nhận số thứ tự đáp án → tự chạm đáp án**.

> Yêu cầu Android 11+. App cần Accessibility Service để chụp màn hình và thực hiện gesture. API key không được ghi vào source code.

## Build APK bằng GitHub Actions (không cần Android Studio)

1. Tạo repository GitHub mới, ví dụ `AIQuizScanner`.
2. Upload toàn bộ nội dung project này vào repository, hoặc dùng Git:

```bash
git init
git add .
git commit -m "Initial AI Quiz Scanner"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/AIQuizScanner.git
git push -u origin main
```

3. Vào tab **Actions** → workflow **Build APK**.
4. Chọn lần chạy mới nhất. Khi chạy xong, kéo xuống **Artifacts** → tải `AIQuizScanner-debug-apk`.
5. Giải nén artifact, file APK là `app-debug.apk`.

Workflow tự cài JDK 17 và Gradle 8.11.1 trên GitHub Actions.

## Cài và dùng

1. Cài `app-debug.apk`.
2. Mở app → dán Google AI Studio API key → **Lưu API Key**.
3. Bấm **Mở quyền Accessibility / chụp màn hình** → bật **AI Quiz Scanner**.
4. Nếu muốn sử dụng overlay thì cấp thêm quyền hiển thị trên ứng dụng khác.
5. Mở màn hình có câu hỏi trắc nghiệm.
6. Bấm **Quét màn hình ngay** để thử một lần.
7. Nếu hoạt động đúng, bấm **Bật tự động quét**. Khi màn hình thay đổi, service sẽ chờ khoảng 1,2 giây rồi quét.

## API

App gọi Gemini REST API trực tiếp từ Android:

`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`

Ảnh màn hình được nén JPEG trước khi gửi. Prompt yêu cầu Gemini chỉ trả JSON dạng `{"answer_index": 1}`.

## Giới hạn quan trọng

- Một số app/game không cung cấp Accessibility nodes nên app có thể biết đáp án nhưng không tìm được vị trí để tự chạm.
- Thứ tự node Accessibility có thể khác thứ tự hiển thị trong một số giao diện phức tạp.
- Gemini có thể trả lời sai; app không có cơ chế đảm bảo đáp án đúng.
- Không commit API key vào GitHub. Key hiện được lưu cục bộ trong SharedPreferences của thiết bị.
