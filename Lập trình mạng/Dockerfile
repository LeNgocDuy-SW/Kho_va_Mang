# Dockerfile triển khai ChatServer Java lên Cloud 24/7 (Render.com / Railway / Fly.io)
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy toàn bộ code server, web UI và database tài khoản
COPY ChatServer.java .
COPY web_chat.html .
COPY accounts.txt .
COPY chat_history.txt .

# Biên dịch mã nguồn Java
RUN javac -encoding UTF-8 ChatServer.java

# Khởi chạy server
CMD ["java", "-Dfile.encoding=UTF-8", "ChatServer"]
