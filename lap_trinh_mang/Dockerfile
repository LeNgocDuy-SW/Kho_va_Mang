FROM eclipse-temurin:21-jdk

# Cài curl để dự phòng tự tải thư viện JDBC nếu không đẩy file .jar >10MB lên Git
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*

# Tạo user non-root (uid 1000) theo chuẩn bảo mật của Hugging Face Spaces
RUN useradd -m -u 1000 user

WORKDIR /app

# Copy toàn bộ mã nguồn và thư mục web/ vào container
COPY --chown=user:user . .

# Nếu chưa có sqlite-jdbc.jar trong lib/ (do giới hạn 10MB của Git), tự động tải từ Maven Central
RUN mkdir -p lib && \
    if [ ! -f lib/sqlite-jdbc.jar ]; then \
        curl -fL "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.2.0/sqlite-jdbc-3.45.2.0.jar" -o lib/sqlite-jdbc.jar; \
    fi && \
    if [ ! -f lib/slf4j-api.jar ]; then \
        curl -fL "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar" -o lib/slf4j-api.jar; \
    fi && \
    if [ ! -f lib/slf4j-nop.jar ]; then \
        curl -fL "https://repo1.maven.org/maven2/org/slf4j/slf4j-nop/1.7.36/slf4j-nop-1.7.36.jar" -o lib/slf4j-nop.jar; \
    fi

# Biên dịch toàn bộ mã nguồn Java
RUN javac -encoding UTF-8 -cp ".:lib/*" \
    common/*.java \
    server/*.java \
    server/db/*.java \
    server/web/*.java \
    server/ui/*.java \
    client/*.java \
    client/network/*.java \
    client/ui/*.java

# Cấp quyền ghi cho user 1000 để SQLite tạo file chat.db
RUN chown -R user:user /app

USER user

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-Dfile.encoding=UTF-8", "-cp", ".:lib/*", "server.ServerMain", "--cli"]
