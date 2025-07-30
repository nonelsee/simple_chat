# simple_chat
Ứng Dụng Chat Đơn Giản (Simple Chat Application)
Ứng dụng này là một hệ thống chat đơn giản, cung cấp các API RESTful cho phép người dùng đăng nhập, quản lý danh sách bạn bè, gửi/nhận tin nhắn (bao gồm cả tin nhắn văn bản và file), và tải xuống file. Backend được xây dựng bằng Spring Boot (Java), sử dụng file JSON làm cơ sở dữ liệu mô phỏng.

1. Tính năng chính
Ứng dụng bao gồm các API chính sau:

Đăng nhập (POST /api/login): Xác thực người dùng và cấp Access Token. Access Token có thời gian hết hạn và cần được gửi trong Header (Access-Token) cho hầu hết các yêu cầu tiếp theo.

Lấy danh sách bạn bè (GET /api/friends): Trả về danh sách bạn bè của người dùng hiện tại.

Gửi tin nhắn (POST /api/send-message): Gửi tin nhắn văn bản hoặc file đến một người dùng khác. Hỗ trợ gửi file và lưu trữ chúng vào thư mục storage/. Tin nhắn được đưa vào hàng chờ nếu người nhận offline.

Nhận tin nhắn mới (GET /api/get-new-messages): Sử dụng cơ chế Long Polling để nhận tin nhắn mới theo thời gian thực. Nếu không có tin nhắn mới, yêu cầu sẽ treo tối đa 10 giây trước khi trả về danh sách rỗng. Trả về link tải file đối với tin nhắn file.

Tải file (GET /api/files/{filename}): Cho phép người dùng tải xuống các file đã nhận.

2. Công nghệ sử dụng
Backend: Java, Spring Boot

Database (mô phỏng): JSON Files

Xử lý JSON: Jackson

Quản lý Dependency: Maven

3. Cài đặt và Chạy ứng dụng
Để chạy ứng dụng này trên máy cục bộ của bạn, hãy làm theo các bước dưới đây.

3.1. Yêu cầu hệ thống
Java Development Kit (JDK): Phiên bản 17 trở lên (đảm bảo tương thích với Spring Boot 3.x).

Apache Maven: Phiên bản 3.x trở lên.

IDE (tùy chọn): IntelliJ IDEA, Eclipse, hoặc VS Code với các plugin hỗ trợ Java và Spring Boot.

3.2. Cấu hình dự án
Clone repository (nếu có): Nếu dự án được lưu trữ trên Git, hãy clone nó về máy của bạn.

Bash

git clone <URL_repository_của_bạn>
cd simplechat
Tạo cấu trúc thư mục dữ liệu:
Đảm bảo bạn có các file và thư mục sau trong src/main/resources/:

src/main/resources/users.json

src/main/resources/messages.json

src/main/resources/storage/ (thư mục rỗng để lưu file upload)

Bạn có thể sử dụng nội dung JSON mẫu đã cung cấp trước đó cho users.json và messages.json.

users.json (ví dụ):

JSON

[
  {
    "username": "user1",
    "passwordHash": "e10adc3949ba59abbe56e057f20f883e",
    "friends": ["user2", "user3"],
    "accessToken": null,
    "accessTokenExpiry": null
  },
  {
    "username": "user2",
    "passwordHash": "e10adc3949ba59abbe56e057f20f883e",
    "friends": ["user1", "user4"],
    "accessToken": null,
    "accessTokenExpiry": null
  },
  {
    "username": "user3",
    "passwordHash": "e10adc3949ba59abbe56e057f20f883e",
    "friends": ["user1", "user5"],
    "accessToken": null,
    "accessTokenExpiry": null
  },
  {
    "username": "user4",
    "passwordHash": "e10adc3949ba59abbe56e057f20f883e",
    "friends": ["user2"],
    "accessToken": null,
    "accessTokenExpiry": null
  },
  {
    "username": "user5",
    "passwordHash": "e10adc3949ba59abbe56e057f20f883e",
    "friends": ["user3"],
    "accessToken": null,
    "accessTokenExpiry": null
  }
]
messages.json (ban đầu rỗng):

JSON

[]
3.3. Build và Chạy
Mở Terminal hoặc Command Prompt.

Điều hướng đến thư mục gốc của dự án simplechat (nơi chứa file pom.xml).

Chạy lệnh Maven để build dự án:

Bash

mvn clean install
Sau khi build thành công, chạy ứng dụng Spring Boot:

Bash

mvn spring-boot:run
Ứng dụng sẽ khởi động và mặc định chạy trên cổng 8080.

4. Kiểm thử API với Postman
Để kiểm thử các API, chúng ta sẽ sử dụng Postman.

4.1. Chuẩn bị Postman
Cài đặt Postman: Nếu chưa có, tải và cài đặt Postman từ https://www.postman.com/downloads/.

Tạo Postman Environment:

Trong Postman, nhấp vào dropdown "No Environment" (hoặc tên Environment hiện có) ở góc trên bên phải.

Chọn "Add new Environment" hoặc "Manage Environments" -> "+ Add".

Đặt tên cho Environment (ví dụ: Chat App Local).

Thêm một biến:

VARIABLE: baseUrl

INITIAL VALUE: http://localhost:8080

CURRENT VALUE: http://localhost:8080

Nhấp "Save".

Đảm bảo bạn đã chọn Environment này trong dropdown.

Tạo Postman Collection:

Trong thanh bên trái, nhấp vào nút "+" bên cạnh "Collections".

Đặt tên cho Collection (ví dụ: Simple Chat App API Tests).

4.2. Các Request cần tạo trong Postman
Trong Collection vừa tạo, hãy thêm các request sau:

4.2.1. Đăng nhập (Login User 1 & User 2)
Request Name: 1.1 Login User 1

Method: POST

URL: {{baseUrl}}/api/login

Headers: Content-Type: application/json

Body (raw - JSON):

JSON

{
    "username": "user1",
    "password": "123456"
}
Tests (Tab Tests):

JavaScript

var jsonData = pm.response.json();
pm.environment.set("accessToken_user1", jsonData.accessToken);
console.log("Access Token for User 1:", jsonData.accessToken);
Request Name: 1.2 Login User 2

Tương tự như trên, nhưng thay username là user2 và pm.environment.set("accessToken_user1", ...) thành pm.environment.set("accessToken_user2", ...).

Sau khi gửi các request này, hãy kiểm tra biểu tượng con mắt trong Postman để đảm bảo accessToken_user1 và accessToken_user2 đã được lưu.

4.2.2. Lấy danh sách bạn bè
Request Name: 2.1 Get Friends for User 1

Method: GET

URL: {{baseUrl}}/api/friends

Headers: Access-Token: {{accessToken_user1}}

4.2.3. Gửi tin nhắn
Request Name: 3.1 Send Text Message (User 1 to User 2)

Method: POST

URL: {{baseUrl}}/api/send-message

Headers: Access-Token: {{accessToken_user1}}

Body (form-data):

Key: receiver, Value: user2

Key: message, Value: Chào user2! Bạn khỏe không? Đây là tin nhắn text.

Request Name: 3.2 Send File Message (User 1 to User 2)

Method: POST

URL: {{baseUrl}}/api/send-message

Headers: Access-Token: {{accessToken_user1}}

Body (form-data):

Key: receiver, Value: user2

Key: file, Value: Chọn loại File và tải lên một file bất kỳ từ máy tính của bạn.

4.2.4. Nhận tin nhắn mới (Long Polling)
Request Name: 4.1 Get New Messages for User 2

Method: GET

URL: {{baseUrl}}/api/get-new-messages

Headers: Access-Token: {{accessToken_user2}}

4.2.5. Tải file
Request Name: 5.1 Download File (by User 2)

Method: GET

URL: {{baseUrl}}/api/files/{{filename}} (Bạn sẽ thay {{filename}} bằng tên file thật sự từ phản hồi của Get New Messages)

Headers: Access-Token: {{accessToken_user2}}

4.2.6. Kiểm tra lỗi (Tùy chọn)
Request Name: 6.1 Send Message (Not Friends)

Method: POST

URL: {{baseUrl}}/api/send-message

Headers: Access-Token: {{accessToken_user1}}

Body (form-data):

Key: receiver, Value: user4 (trong users.json, user1 không phải là bạn của user4, và ngược lại)

Key: message, Value: Tin nhắn này sẽ bị từ chối.
