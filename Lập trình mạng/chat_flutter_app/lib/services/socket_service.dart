import 'dart:async';
import 'dart:convert';
import 'dart:io';
import '../models/chat_models.dart';

class SocketService {
  static final SocketService _instance = SocketService._internal();
  factory SocketService() => _instance;
  SocketService._internal();

  Socket? _socket;
  StreamSubscription<String>? _subscription;

  String currentUsername = '';
  String currentRole = 'USER';
  bool get isAdmin => currentRole.toUpperCase() == 'ADMIN';

  // Callbacks
  Function(ChatMessage)? onMessageReceived;
  Function(List<OnlineUser>)? onOnlineListUpdated;
  Function(String)? onSystemNotice;
  Function(String)? onKicked;
  Function()? onDisconnected;
  Function(List<String>)? onFullHistoryLoaded;

  final List<String> _historyBuffer = [];

  // ================= KẾT NỐI VÀ ĐĂNG NHẬP =================
  Future<String?> connectAndLogin({
    required String host,
    required int port,
    required String username,
    required String password,
  }) async {
    try {
      disconnect();

      _socket = await Socket.connect(
        host,
        port,
        timeout: const Duration(seconds: 6),
      );

      final completer = Completer<String?>();

      _subscription = _socket!
          .cast<List<int>>()
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(
        (packet) {
          if (!completer.isCompleted && packet.startsWith('LOGIN_OK|')) {
            final parts = packet.split('|');
            currentUsername = username;
            currentRole = parts.length > 1 ? parts[1].toUpperCase() : 'USER';
            completer.complete(null); // null = thành công
          } else if (!completer.isCompleted && packet.startsWith('LOGIN_FAIL|')) {
            final err = packet.replaceFirst('LOGIN_FAIL|', '');
            completer.complete(err);
            disconnect();
          } else {
            _handlePacket(packet);
          }
        },
        onError: (err) {
          if (!completer.isCompleted) completer.complete('Lỗi Socket: $err');
          _handleDisconnect();
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete('Máy chủ ngắt kết nối đột ngột!');
          _handleDisconnect();
        },
      );

      // Gửi gói tin LOGIN lên Java Server
      sendPacket('LOGIN|$username|$password');

      final result = await completer.future;
      if (result == null) {
        requestOnlineList();
      }
      return result;
    } catch (e) {
      disconnect();
      return 'Không thể kết nối đến máy chủ ($host:$port).\nHãy kiểm tra xem ChatServer Java đã bật chưa!';
    }
  }

  // ================= ĐĂNG KÝ TÀI KHOẢN =================
  static Future<String?> register({
    required String host,
    required int port,
    required String username,
    required String password,
    required String role,
  }) async {
    Socket? tempSocket;
    try {
      tempSocket = await Socket.connect(
        host,
        port,
        timeout: const Duration(seconds: 5),
      );

      final completer = Completer<String?>();

      tempSocket
          .cast<List<int>>()
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(
        (packet) {
          if (!completer.isCompleted) {
            if (packet.startsWith('REGISTER_OK|')) {
              completer.complete(null); // null = Thành công
            } else if (packet.startsWith('REGISTER_FAIL|')) {
              completer.complete(packet.replaceFirst('REGISTER_FAIL|', ''));
            } else {
              completer.complete(packet);
            }
          }
        },
        onError: (e) {
          if (!completer.isCompleted) completer.complete('Lỗi: $e');
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete('Server đóng kết nối');
        },
      );

      tempSocket.writeln('REGISTER|$username|$password|$role');
      final res = await completer.future;
      tempSocket.destroy();
      return res;
    } catch (e) {
      tempSocket?.destroy();
      return 'Không thể kết nối tới Server ($host:$port)!';
    }
  }

  // ================= XỬ LÝ GÓI TIN NHẬN TỪ JAVA =================
  void _handlePacket(String packet) {
    if (packet.trim().isEmpty) return;

    final firstPipe = packet.indexOf('|');
    final type = firstPipe != -1 ? packet.substring(0, firstPipe).toUpperCase() : packet.toUpperCase();
    final content = firstPipe != -1 ? packet.substring(firstPipe + 1) : '';

    switch (type) {
      case 'CHAT':
        _handleChatPacket(content);
        break;
      case 'PRIVATE':
        _handlePrivatePacket(content);
        break;
      case 'ONLINE_LIST':
        _handleOnlineListPacket(content);
        break;
      case 'SERVER':
        onSystemNotice?.call(content);
        if (content.contains('tham gia') || content.contains('thoát') || content.contains('kick')) {
          requestOnlineList();
        }
        break;
      case 'ANNOUNCEMENT':
        onSystemNotice?.call(content);
        break;
      case 'HISTORY':
        _handleHistoryLine(content);
        break;
      case 'FULL_HISTORY_START':
        _historyBuffer.clear();
        break;
      case 'FULL_HISTORY_ITEM':
        _historyBuffer.add(content);
        break;
      case 'FULL_HISTORY_END':
        onFullHistoryLoaded?.call(List.from(_historyBuffer));
        break;
      case 'ERROR':
        onSystemNotice?.call('⚠️ $content');
        break;
      case 'KICKED':
        onKicked?.call(content);
        disconnect();
        break;
    }
  }

  void _handleChatPacket(String content) {
    // [15:10:00] [ROLE-username]: message
    final regex = RegExp(r'^\[(.*?)\] \[(.*?)-(.*?)\:\s*(.*)$');
    final match = regex.firstMatch(content);
    if (match != null) {
      final time = match.group(1) ?? '';
      final role = match.group(2) ?? 'USER';
      final sender = match.group(3) ?? '';
      final msg = match.group(4) ?? '';
      final isMe = sender.toLowerCase() == currentUsername.toLowerCase();

      onMessageReceived?.call(ChatMessage(
        sender: sender,
        role: role,
        text: msg,
        time: time,
        isMe: isMe,
      ));
    } else {
      onMessageReceived?.call(ChatMessage(
        sender: 'Phòng',
        role: 'USER',
        text: content,
        time: '',
        isMe: false,
      ));
    }
  }

  void _handlePrivatePacket(String content) {
    // [15:10:00] [Mật từ duy]: alo  hoặc  [Mật tới duy]: alo
    final regex = RegExp(r'^\[(.*?)\] \[(Mật từ|Mật tới) (.*?)\:\s*(.*)$');
    final match = regex.firstMatch(content);
    if (match != null) {
      final time = match.group(1) ?? '';
      final direction = match.group(2) ?? '';
      final otherUser = match.group(3) ?? '';
      final msg = match.group(4) ?? '';
      final isMe = direction.startsWith('Mật tới');

      onMessageReceived?.call(ChatMessage(
        sender: isMe ? currentUsername : otherUser,
        role: 'USER',
        text: msg,
        time: time,
        isMe: isMe,
        isPrivate: true,
      ));
    } else {
      onMessageReceived?.call(ChatMessage(
        sender: 'Mật',
        role: 'USER',
        text: content,
        time: '',
        isMe: false,
        isPrivate: true,
      ));
    }
  }

  void _handleHistoryLine(String content) {
    if (content.startsWith('---') || content.contains('LỊCH SỬ')) {
      onSystemNotice?.call(content);
      return;
    }
    _handleChatPacket(content);
  }

  void _handleOnlineListPacket(String content) {
    final List<OnlineUser> users = [];
    if (content.trim().isNotEmpty) {
      final tokens = content.split(',');
      for (final t in tokens) {
        final pair = t.split(':');
        if (pair.isNotEmpty && pair[0].trim().isNotEmpty) {
          final uName = pair[0].trim();
          final uRole = pair.length > 1 ? pair[1].trim() : 'USER';
          users.add(OnlineUser(username: uName, role: uRole));
        }
      }
    }
    onOnlineListUpdated?.call(users);
  }

  void _handleDisconnect() {
    _subscription?.cancel();
    _subscription = null;
    _socket = null;
    onDisconnected?.call();
  }

  // ================= CÁC LỆNH GỬI ĐẾN SERVER =================
  void sendMessage(String text, String? targetUser) {
    if (text.trim().isEmpty) return;
    if (targetUser != null && targetUser.isNotEmpty && !targetUser.startsWith('🌐')) {
      final recipient = targetUser.replaceAll('@', '').trim();
      sendPacket('MSG_PRIVATE|$recipient|$text');
    } else {
      sendPacket('MSG_ALL|$text');
    }
  }

  void requestOnlineList() {
    sendPacket('ONLINE');
  }

  void requestFullHistory() {
    sendPacket('GET_FULL_HISTORY');
  }

  void kickUser(String targetUser) {
    sendPacket('ADMIN_KICK|$targetUser');
  }

  void muteUser(String targetUser, bool mute) {
    sendPacket('ADMIN_MUTE|$targetUser|$mute');
  }

  void clearHistory() {
    sendPacket('ADMIN_CLEAR_HISTORY');
  }

  void sendAnnouncement(String message) {
    sendPacket('ADMIN_ANNOUNCE|$message');
  }

  void sendPacket(String raw) {
    if (_socket != null) {
      _socket!.writeln(raw);
    }
  }

  void disconnect() {
    _handleDisconnect();
    _socket?.destroy();
  }
}
