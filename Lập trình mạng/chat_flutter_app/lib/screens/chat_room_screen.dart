import 'package:flutter/material.dart';
import '../models/chat_models.dart';
import '../services/socket_service.dart';
import 'auth_screen.dart';

class ChatRoomScreen extends StatefulWidget {
  const ChatRoomScreen({super.key});

  @override
  State<ChatRoomScreen> createState() => _ChatRoomScreenState();
}

class _ChatRoomScreenState extends State<ChatRoomScreen> {
  final SocketService _socket = SocketService();
  final List<ChatMessage> _messages = [];
  final List<OnlineUser> _onlineUsers = [];
  final TextEditingController _textCtrl = TextEditingController();
  final ScrollController _scrollCtrl = ScrollController();
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();

  String? _selectedRecipient; // null = Chat chung toàn phòng

  final List<String> _quickEmojis = ['💯', '🍗', '🍔', '👍', '😂', '❤️', '🔥'];

  static const List<Color> _avatarColors = [
    Color(0xFFEC4899), // Pink
    Color(0xFF8B5CF6), // Purple
    Color(0xFF3B82F6), // Blue
    Color(0xFF10B981), // Emerald
    Color(0xFFF59E0B), // Amber
    Color(0xFFEF4444), // Red
    Color(0xFF14B8A6), // Teal
    Color(0xFF6366F1), // Indigo
  ];

  @override
  void initState() {
    super.initState();
    _initSocketCallbacks();
  }

  void _initSocketCallbacks() {
    _socket.onMessageReceived = (msg) {
      if (!mounted) return;
      setState(() {
        _messages.add(msg);
      });
      _scrollToBottom();
    };

    _socket.onOnlineListUpdated = (users) {
      if (!mounted) return;
      setState(() {
        _onlineUsers.clear();
        _onlineUsers.addAll(users);
      });
    };

    _socket.onSystemNotice = (notice) {
      if (!mounted) return;
      setState(() {
        _messages.add(ChatMessage(
          sender: 'Hệ thống',
          role: 'SYSTEM',
          text: notice,
          time: '',
          isMe: false,
          isSystem: true,
        ));
      });
      _scrollToBottom();
    };

    _socket.onKicked = (reason) {
      if (!mounted) return;
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (_) => AlertDialog(
          title: const Text('🚫 Bị trục xuất'),
          content: Text(reason),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
                _handleLogout();
              },
              child: const Text('Đồng ý'),
            ),
          ],
        ),
      );
    };

    _socket.onDisconnected = () {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('🔴 Mất kết nối khỏi Server Java.'),
          backgroundColor: Colors.redAccent,
        ),
      );
    };
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _sendMessage() {
    final text = _textCtrl.text.trim();
    if (text.isEmpty) return;

    _socket.sendMessage(text, _selectedRecipient);
    _textCtrl.clear();
  }

  void _handleLogout() {
    _socket.disconnect();
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (_) => const AuthScreen()),
    );
  }

  void _openHistoryDialog() {
    showDialog(
      context: context,
      builder: (_) => HistoryViewerDialog(messages: _messages),
    );
  }

  Color _getAvatarColor(String name) {
    if (name.isEmpty) return _avatarColors[0];
    final hash = name.codeUnits.fold(0, (prev, elem) => prev + elem);
    return _avatarColors[hash % _avatarColors.length];
  }

  @override
  void dispose() {
    _textCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isAdmin = _socket.isAdmin;

    return Scaffold(
      key: _scaffoldKey,
      backgroundColor: Colors.white,
      appBar: _buildMessengerAppBar(isAdmin),
      endDrawer: _buildOnlineDrawer(),
      body: Column(
        children: [
          // Khu vực hiển thị tin nhắn (Messenger Bubbles Stream)
          Expanded(
            child: ListView.builder(
              controller: _scrollCtrl,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                return _buildMessageBubble(_messages[index]);
              },
            ),
          ),

          // Thanh nhập tin nhắn và biểu tượng cảm xúc nhanh
          _buildBottomInputBar(),
        ],
      ),
    );
  }

  // ================= APPBAR PHONG CÁCH MESSENGER =================
  PreferredSizeWidget _buildMessengerAppBar(bool isAdmin) {
    return AppBar(
      elevation: 1,
      backgroundColor: Colors.white,
      shadowColor: Colors.black12,
      titleSpacing: 0,
      leading: Padding(
        padding: const EdgeInsets.all(8.0),
        child: Container(
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            gradient: LinearGradient(
              colors: [Color(0xFF9333EA), Color(0xFF6366F1)],
            ),
          ),
          child: const Center(
            child: Text('💬', style: TextStyle(fontSize: 18)),
          ),
        ),
      ),
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text(
                'Tour Crew',
                style: TextStyle(
                  color: Color(0xFF0F172A),
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
              if (isAdmin) ...[
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.red.shade100,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Text(
                    '👑 ADMIN',
                    style: TextStyle(
                      color: Color(0xFFB91C1C),
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 2),
          Text(
            '🟢 ${_onlineUsers.length} thành viên đang hoạt động • Bạn là ${_socket.currentUsername}',
            style: const TextStyle(
              color: Color(0xFF64748B),
              fontSize: 11,
              fontWeight: FontWeight.w400,
            ),
          ),
        ],
      ),
      actions: [
        // Nút Lịch sử chat
        IconButton(
          tooltip: 'Xem Lịch sử chat',
          icon: const Icon(Icons.history_edu, color: Color(0xFF475569)),
          onPressed: _openHistoryDialog,
        ),
        // Nút Danh sách Online (Mở Drawer)
        IconButton(
          tooltip: 'Thành viên Online',
          icon: const Icon(Icons.people_alt_outlined, color: Color(0xFF475569)),
          onPressed: () {
            _scaffoldKey.currentState?.openEndDrawer();
          },
        ),
        // Nút Đăng xuất
        IconButton(
          tooltip: 'Đăng xuất',
          icon: const Icon(Icons.logout, color: Colors.redAccent),
          onPressed: _handleLogout,
        ),
      ],
    );
  }

  // ================= BONG BÓNG CHAT MESSENGER (BUBBLE) =================
  Widget _buildMessageBubble(ChatMessage msg) {
    if (msg.isSystem) {
      // Tin nhắn hệ thống căn giữa dạng viên nang (Pill)
      return Center(
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 6),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
          decoration: BoxDecoration(
            color: const Color(0xFFF1F5F9),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFE2E8F0)),
          ),
          child: Text(
            '📢 ${msg.text}',
            style: const TextStyle(
              fontSize: 11,
              fontStyle: FontStyle.italic,
              color: Color(0xFF64748B),
            ),
          ),
        ),
      );
    }

    if (msg.isMe) {
      // Tin nhắn của tôi: CĂN PHẢI, Gradient Tím/Xanh Messenger
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            if (msg.isPrivate)
              const Padding(
                padding: EdgeInsets.only(bottom: 2, right: 4),
                child: Text(
                  '🔒 Nhắn riêng',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF9333EA),
                  ),
                ),
              ),
            Container(
              constraints: const BoxConstraints(maxWidth: 380),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF8B2DF0), Color(0xFF6366F1)], // Messenger Purple Gradient
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(20).copyWith(
                  bottomRight: const Radius.circular(4),
                ),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF8B2DF0).withValues(alpha: 0.2),
                    blurRadius: 6,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: Text(
                msg.text,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  height: 1.3,
                ),
              ),
            ),
            if (msg.time.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 3, right: 4),
                child: Text(
                  msg.time,
                  style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8)),
                ),
              ),
          ],
        ),
      );
    } else {
      // Tin nhắn người khác: CĂN TRÁI, Avatar tròn, Tên bên trên, Bong bóng xám (#F0F2F5)
      final avatarBg = _getAvatarColor(msg.sender);
      final initial = msg.sender.isNotEmpty ? msg.sender[0].toUpperCase() : '?';

      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            // Avatar tròn
            CircleAvatar(
              radius: 16,
              backgroundColor: avatarBg,
              child: Text(
                initial,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
            const SizedBox(width: 8),

            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Tên người gửi phía trên bong bóng
                Padding(
                  padding: const EdgeInsets.only(left: 4, bottom: 2),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (msg.isAdmin) ...[
                        const Text('👑 ', style: TextStyle(fontSize: 11)),
                        Text(
                          msg.sender,
                          style: const TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFFDC2626), // Red for Admin
                          ),
                        ),
                        const Text(
                          ' [ADMIN]',
                          style: TextStyle(
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFFDC2626),
                          ),
                        ),
                      ] else ...[
                        Text(
                          msg.sender,
                          style: const TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: Color(0xFF64748B),
                          ),
                        ),
                      ],
                      if (msg.isPrivate)
                        const Text(
                          ' 🔒 (Mật)',
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF9333EA),
                          ),
                        ),
                    ],
                  ),
                ),

                // Bong bóng màu xám Messenger
                Container(
                  constraints: const BoxConstraints(maxWidth: 380),
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  decoration: BoxDecoration(
                    color: msg.isPrivate ? const Color(0xFFEDE9FE) : const Color(0xFFF0F2F5),
                    borderRadius: BorderRadius.circular(20).copyWith(
                      bottomLeft: const Radius.circular(4),
                    ),
                  ),
                  child: Text(
                    msg.text,
                    style: const TextStyle(
                      color: Color(0xFF0F172A),
                      fontSize: 14,
                      height: 1.3,
                    ),
                  ),
                ),

                if (msg.time.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 3, left: 4),
                    child: Text(
                      msg.time,
                      style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8)),
                    ),
                  ),
              ],
            ),
          ],
        ),
      );
    }
  }

  // ================= THANH NHẬP TIN NHẮN (BOTTOM BAR) =================
  Widget _buildBottomInputBar() {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 8, 14, 12),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: Color(0xFFE2E8F0))),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Hàng trên: Target chip và Reaction Emojis nhanh
          Row(
            children: [
              // Chọn đối tượng gửi
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                decoration: BoxDecoration(
                  color: _selectedRecipient == null
                      ? const Color(0xFFF1F5F9)
                      : const Color(0xFFEDE9FE),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: _selectedRecipient == null
                        ? const Color(0xFFE2E8F0)
                        : const Color(0xFFC4B5FD),
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      _selectedRecipient == null
                          ? '🌐 Tất cả phòng'
                          : '🔒 @$_selectedRecipient',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: _selectedRecipient == null
                            ? const Color(0xFF475569)
                            : const Color(0xFF7C3AED),
                      ),
                    ),
                    if (_selectedRecipient != null) ...[
                      const SizedBox(width: 4),
                      GestureDetector(
                        onTap: () => setState(() => _selectedRecipient = null),
                        child: const Icon(Icons.close, size: 14, color: Color(0xFF7C3AED)),
                      ),
                    ],
                  ],
                ),
              ),

              const Spacer(),

              // Emojis nhanh giống trong ảnh: 💯, 🍗, 🍔, 👍, 😂
              ..._quickEmojis.map((emo) {
                return InkWell(
                  onTap: () {
                    _textCtrl.text += emo;
                  },
                  borderRadius: BorderRadius.circular(12),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                    child: Text(emo, style: const TextStyle(fontSize: 16)),
                  ),
                );
              }),
            ],
          ),
          const SizedBox(height: 6),

          // Khung nhập văn bản bo tròn hình viên thuốc (Pill Capsule)
          Row(
            children: [
              Expanded(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(24),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: TextField(
                    controller: _textCtrl,
                    decoration: const InputDecoration(
                      hintText: 'Aa Nhập tin nhắn...',
                      hintStyle: TextStyle(color: Color(0xFF94A3B8), fontSize: 14),
                      border: InputBorder.none,
                      isDense: true,
                      contentPadding: EdgeInsets.symmetric(vertical: 11),
                    ),
                    onSubmitted: (_) => _sendMessage(),
                  ),
                ),
              ),
              const SizedBox(width: 8),

              // Nút Gửi tròn màu tím/xanh gradient
              Container(
                width: 42,
                height: 42,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: [Color(0xFF8B2DF0), Color(0xFF6366F1)],
                  ),
                ),
                child: IconButton(
                  icon: const Icon(Icons.send_rounded, color: Colors.white, size: 18),
                  onPressed: _sendMessage,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ================= DRAWER DANH SÁCH ONLINE & ADMIN TOOLS =================
  Widget _buildOnlineDrawer() {
    final isAdmin = _socket.isAdmin;

    return Drawer(
      backgroundColor: const Color(0xFFF8FAFC),
      child: SafeArea(
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              color: Colors.white,
              child: Row(
                children: [
                  const Text(
                    '👥 Đang Online',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF0F172A),
                    ),
                  ),
                  const Spacer(),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: const Color(0xFFDCFCE7),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '${_onlineUsers.length}',
                      style: const TextStyle(
                        color: Color(0xFF15803D),
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),

            Expanded(
              child: ListView.separated(
                itemCount: _onlineUsers.length,
                separatorBuilder: (_, _) => const Divider(height: 1, indent: 60),
                itemBuilder: (context, index) {
                  final user = _onlineUsers[index];
                  final isMe = user.username.toLowerCase() == _socket.currentUsername.toLowerCase();
                  final avatarBg = _getAvatarColor(user.username);

                  return ListTile(
                    dense: true,
                    leading: CircleAvatar(
                      radius: 18,
                      backgroundColor: avatarBg,
                      child: Text(
                        user.username.isNotEmpty ? user.username[0].toUpperCase() : '?',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    title: Row(
                      children: [
                        Text(
                          user.username + (isMe ? ' (Bạn)' : ''),
                          style: TextStyle(
                            fontWeight: (isMe || user.isAdmin) ? FontWeight.bold : FontWeight.w500,
                            color: user.isAdmin ? const Color(0xFFB91C1C) : const Color(0xFF1E293B),
                          ),
                        ),
                        if (user.isAdmin) ...[
                          const SizedBox(width: 4),
                          const Text('👑', style: TextStyle(fontSize: 12)),
                        ],
                      ],
                    ),
                    subtitle: Text(
                      user.isAdmin ? 'Quản trị viên' : 'Thành viên',
                      style: const TextStyle(fontSize: 11, color: Color(0xFF64748B)),
                    ),
                    trailing: isMe
                        ? null
                        : IconButton(
                            icon: const Icon(Icons.chat_bubble_outline, size: 18, color: Color(0xFF8B5CF6)),
                            tooltip: 'Nhắn riêng 1-1',
                            onPressed: () {
                              setState(() {
                                _selectedRecipient = user.username;
                              });
                              Navigator.pop(context);
                            },
                          ),
                    onTap: isMe
                        ? null
                        : () {
                            if (isAdmin) {
                              _showAdminActionModal(user);
                            } else {
                              setState(() {
                                _selectedRecipient = user.username;
                              });
                              Navigator.pop(context);
                            }
                          },
                  );
                },
              ),
            ),

            if (isAdmin) ...[
              Container(
                padding: const EdgeInsets.all(12),
                color: Colors.red.shade50,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text(
                      '👑 QUẢN TRỊ VIÊN HỆ THỐNG',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF991B1B),
                      ),
                    ),
                    const SizedBox(height: 8),
                    ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFEF4444),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 8),
                      ),
                      icon: const Icon(Icons.campaign, size: 18),
                      label: const Text('Phát thông báo khẩn', style: TextStyle(fontSize: 12)),
                      onPressed: _showAnnouncementDialog,
                    ),
                    const SizedBox(height: 6),
                    OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.red.shade700,
                        padding: const EdgeInsets.symmetric(vertical: 8),
                      ),
                      icon: const Icon(Icons.delete_sweep, size: 18),
                      label: const Text('Xóa sạch lịch sử', style: TextStyle(fontSize: 12)),
                      onPressed: () {
                        _socket.clearHistory();
                      },
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _showAdminActionModal(OnlineUser target) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ListTile(
                  leading: const Icon(Icons.chat, color: Color(0xFF8B5CF6)),
                  title: Text('Nhắn riêng với ${target.username}'),
                  onTap: () {
                    setState(() => _selectedRecipient = target.username);
                    Navigator.pop(context); // Close bottomsheet
                    Navigator.pop(context); // Close drawer
                  },
                ),
                ListTile(
                  leading: const Icon(Icons.person_remove, color: Colors.red),
                  title: Text('Kick ${target.username} khỏi phòng'),
                  onTap: () {
                    Navigator.pop(context);
                    _socket.kickUser(target.username);
                  },
                ),
                ListTile(
                  leading: const Icon(Icons.volume_off, color: Colors.amber),
                  title: Text('Cấm chat (Mute) ${target.username}'),
                  onTap: () {
                    Navigator.pop(context);
                    _socket.muteUser(target.username, true);
                  },
                ),
                ListTile(
                  leading: const Icon(Icons.volume_up, color: Colors.green),
                  title: Text('Mở cấm (Unmute) ${target.username}'),
                  onTap: () {
                    Navigator.pop(context);
                    _socket.muteUser(target.username, false);
                  },
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showAnnouncementDialog() {
    final ctrl = TextEditingController();
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('📢 Phát thông báo Admin'),
        content: TextField(
          controller: ctrl,
          decoration: const InputDecoration(hintText: 'Nhập nội dung thông báo khẩn...'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Hủy')),
          ElevatedButton(
            onPressed: () {
              if (ctrl.text.trim().isNotEmpty) {
                _socket.sendAnnouncement(ctrl.text.trim());
                Navigator.pop(context);
              }
            },
            child: const Text('Gửi thông báo'),
          ),
        ],
      ),
    );
  }
}

// ================= DIALOG XEM TOÀN BỘ LỊCH SỬ CHAT =================
class HistoryViewerDialog extends StatefulWidget {
  final List<ChatMessage> messages;
  const HistoryViewerDialog({super.key, required this.messages});

  @override
  State<HistoryViewerDialog> createState() => _HistoryViewerDialogState();
}

class _HistoryViewerDialogState extends State<HistoryViewerDialog> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _filter = '';

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final filtered = widget.messages.where((m) {
      if (_filter.isEmpty) return true;
      return m.text.toLowerCase().contains(_filter) ||
          m.sender.toLowerCase().contains(_filter);
    }).toList();

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Container(
        width: 600,
        height: 520,
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Row(
              children: [
                const Icon(Icons.history_edu, color: Color(0xFF9333EA)),
                const SizedBox(width: 8),
                const Text(
                  'Lịch Sử Trò Chuyện Chi Tiết',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                const Spacer(),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.pop(context),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Thanh tìm kiếm thời gian thực
            TextField(
              controller: _searchCtrl,
              decoration: InputDecoration(
                hintText: '🔍 Tìm kiếm theo từ khóa hoặc tên...',
                isDense: true,
                filled: true,
                fillColor: const Color(0xFFF1F5F9),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
              onChanged: (val) {
                setState(() => _filter = val.trim().toLowerCase());
              },
            ),
            const SizedBox(height: 12),

            Expanded(
              child: filtered.isEmpty
                  ? const Center(
                      child: Text(
                        'Không có tin nhắn nào khớp!',
                        style: TextStyle(color: Colors.grey),
                      ),
                    )
                  : ListView.separated(
                      itemCount: filtered.length,
                      separatorBuilder: (_, _) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final m = filtered[index];
                        return ListTile(
                          dense: true,
                          title: Row(
                            children: [
                              Text(
                                m.sender,
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  color: m.isAdmin
                                      ? Colors.red
                                      : const Color(0xFF334155),
                                ),
                              ),
                              if (m.isAdmin) const Text(' 👑'),
                              const Spacer(),
                              Text(
                                m.time,
                                style: const TextStyle(
                                    fontSize: 11, color: Colors.grey),
                              ),
                            ],
                          ),
                          subtitle: Text(m.text),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
