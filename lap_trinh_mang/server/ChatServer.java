package server;

import server.db.DatabaseConnection;
import server.db.MessageDAO;
import server.db.UserDAO;
import server.web.WebChatServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatServer: Lõi máy chủ Socket TCP đa luồng kết hợp Web Server HTTP/SSE.
 * Lắng nghe kết nối và điều phối các phiên làm việc của Client bằng ThreadPool.
 */
public class ChatServer {
    public static final int DEFAULT_PORT = 5000;
    public static final int DEFAULT_WEB_PORT = 8080;

    private final int port;
    private final int webPort;
    private final RoomManager roomManager;
    private final UserDAO userDAO;
    private final MessageDAO messageDAO;
    private final WebChatServer webChatServer;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;
    private Thread acceptThread;
    private ServerEventListener listener;

    public ChatServer(int port) {
        this(port, DEFAULT_WEB_PORT);
    }

    public ChatServer(int port, int webPort) {
        this.port = port > 0 ? port : DEFAULT_PORT;
        this.webPort = webPort > 0 ? webPort : DEFAULT_WEB_PORT;
        this.roomManager = new RoomManager();
        this.userDAO = new UserDAO();
        this.messageDAO = new MessageDAO();

        // Khởi tạo Database SQLite
        DatabaseConnection.initDatabase();

        // Khởi tạo cổng Web HTTP/SSE
        this.webChatServer = new WebChatServer(this.webPort, this.roomManager, this.userDAO, this.messageDAO);
    }

    public void setListener(ServerEventListener listener) {
        this.listener = listener;
        this.roomManager.setListener(listener);
    }

    public int getPort() { return port; }
    public int getWebPort() { return webPort; }
    public boolean isRunning() { return running; }
    public RoomManager getRoomManager() { return roomManager; }
    public UserDAO getUserDAO() { return userDAO; }
    public MessageDAO getMessageDAO() { return messageDAO; }
    public WebChatServer getWebChatServer() { return webChatServer; }

    /**
     * Khởi động Server trên một luồng nền độc lập
     */
    public synchronized boolean startServer() {
        if (running) return true;

        try {
            serverSocket = new ServerSocket(port);
            threadPool = Executors.newCachedThreadPool();
            running = true;

            log("[SERVER] Máy chủ đang lắng nghe tại cổng " + port + "...");

            // Khởi động cổng Web Server song song
            webChatServer.start();

            acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(clientSocket, roomManager, userDAO, messageDAO);
                        threadPool.execute(handler);
                    } catch (IOException e) {
                        if (!running) break;
                        log("[SERVER] Lỗi accept client: " + e.getMessage());
                    }
                }
            }, "Server-Accept-Thread");
            acceptThread.setDaemon(true);
            acceptThread.start();
            return true;
        } catch (IOException e) {
            log("[SERVER] Không thể mở cổng " + port + ": " + e.getMessage());
            running = false;
            return false;
        }
    }

    /**
     * Dừng máy chủ và giải phóng các socket
     */
    public synchronized void stopServer() {
        if (!running) return;
        running = false;

        log("[SERVER] Đang dừng máy chủ...");
        webChatServer.stop();
        roomManager.clearAll();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
        }

        log("[SERVER] Máy chủ đã dừng hoạt động.");
    }

    private void log(String msg) {
        if (listener != null) {
            listener.onLog(msg);
        } else {
            System.out.println(msg);
        }
    }
}
