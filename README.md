# ShrinkSpace

App Android giải phóng bộ nhớ bằng cách nén toàn bộ ảnh trong máy xuống dung lượng nhỏ nhất.

## Cách hoạt động

1. Quét toàn bộ ảnh trong máy qua MediaStore (sắp xếp ảnh nặng nhất trước).
2. Chọn chất lượng nén (30–90%) và kích thước tối đa (1280 / 1920 / 2560 px).
3. Nén từng ảnh: thu nhỏ + encode WebP (Android 11+) hoặc JPEG, lưu vào album `Pictures/ShrinkSpace`.
   - Nếu ảnh sau nén không nhỏ hơn ảnh gốc thì tự bỏ qua, giữ nguyên gốc.
   - Giữ đúng chiều xoay ảnh (EXIF).
4. Sau khi nén xong, bấm nút xoá ảnh gốc — ảnh gốc vào Thùng rác hệ thống 30 ngày (khôi phục được nếu lỡ tay).

Thường tiết kiệm **60–90%** dung lượng ảnh tuỳ mức chất lượng chọn.

## Giới hạn của Android (đọc trước khi hỏi vì sao không nén được app)

- Android **không cho phép** app thường xoá cache/data hoặc nén app khác (bị chặn từ Android 6).
- Muốn dọn cache app khác: vào **Cài đặt > Bộ nhớ** của hệ thống.
- Thứ chiếm nhiều nhất và nén được hợp pháp là **ảnh/video** — app này lo phần đó.

## Build APK

Cần [Android Studio](https://developer.android.com/studio) (bản mới nhất).

1. Mở Android Studio → **Open** → chọn thư mục `ShrinkSpace`.
2. Đợi Gradle sync xong (lần đầu sẽ tải dependencies, hơi lâu).
3. Build APK: menu **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
4. APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.
5. Copy APK sang điện thoại, mở file để cài (cần bật "Cài đặt từ nguồn không xác định").

Hoặc cắm điện thoại qua USB (bật USB debugging) rồi bấm **Run ▶** để cài thẳng.

### Build bằng dòng lệnh (nếu đã có Android SDK)

```bash
cd ShrinkSpace
gradle wrapper          # tạo gradlew lần đầu
./gradlew assembleDebug
```

## Yêu cầu

- Android 8.0 (API 26) trở lên.
- Quyền đọc ảnh (app sẽ tự hỏi khi bấm Quét).
- Nút xoá ảnh gốc hàng loạt cần Android 11+ (API 30); máy cũ hơn thì xoá thủ công trong Gallery.

## Cấu trúc code

| File | Vai trò |
|---|---|
| `MainActivity.kt` | UI Compose + ViewModel (quét → chỉnh → nén → xoá gốc) |
| `MediaScanner.kt` | Quét ảnh qua MediaStore |
| `ImageCompressor.kt` | Thu nhỏ + nén WebP/JPEG, xử lý EXIF, lưu MediaStore |
