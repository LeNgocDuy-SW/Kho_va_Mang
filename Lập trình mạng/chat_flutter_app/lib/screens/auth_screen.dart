import 'package:flutter/material.dart';
import '../services/socket_service.dart';
import 'chat_room_screen.dart';

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // Login Controllers
  final _loginHostCtrl = TextEditingController(text: '127.0.0.1');
  final _loginPortCtrl = TextEditingController(text: '5000');
  final _loginUserCtrl = TextEditingController(text: 'admin');
  final _loginPassCtrl = TextEditingController(text: '123');

  // Register Controllers
  final _regHostCtrl = TextEditingController(text: '127.0.0.1');
  final _regPortCtrl = TextEditingController(text: '5000');
  final _regUserCtrl = TextEditingController();
  final _regPassCtrl = TextEditingController();
  final _regConfirmPassCtrl = TextEditingController();
  final _regAdminSecretCtrl = TextEditingController();
  String _selectedRole = 'USER';

  bool _isLoading = false;

  final List<String> _demoAccounts = ['admin', 'duy', 'hai', 'long'];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _loginHostCtrl.dispose();
    _loginPortCtrl.dispose();
    _loginUserCtrl.dispose();
    _loginPassCtrl.dispose();
    _regHostCtrl.dispose();
    _regPortCtrl.dispose();
    _regUserCtrl.dispose();
    _regPassCtrl.dispose();
    _regConfirmPassCtrl.dispose();
    _regAdminSecretCtrl.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    final host = _loginHostCtrl.text.trim();
    final port = int.tryParse(_loginPortCtrl.text.trim()) ?? 5000;
    final user = _loginUserCtrl.text.trim();
    final pass = _loginPassCtrl.text.trim();

    if (user.isEmpty || pass.isEmpty) {
      _showSnackbar('Vui lòng nhập tài khoản và mật khẩu!', isError: true);
      return;
    }

    setState(() => _isLoading = true);

    final error = await SocketService().connectAndLogin(
      host: host,
      port: port,
      username: user,
      password: pass,
    );

    if (!mounted) return;
    setState(() => _isLoading = false);

    if (error == null) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => const ChatRoomScreen()),
      );
    } else {
      _showSnackbar(error, isError: true);
    }
  }

  Future<void> _handleRegister() async {
    final host = _regHostCtrl.text.trim();
    final port = int.tryParse(_regPortCtrl.text.trim()) ?? 5000;
    final user = _regUserCtrl.text.trim();
    final pass = _regPassCtrl.text.trim();
    final confPass = _regConfirmPassCtrl.text.trim();
    final adminSecret = _regAdminSecretCtrl.text.trim();

    if (user.isEmpty || pass.isEmpty) {
      _showSnackbar('Tài khoản và mật khẩu không được để trống!', isError: true);
      return;
    }
    if (pass != confPass) {
      _showSnackbar('Mật khẩu xác nhận không trùng khớp!', isError: true);
      return;
    }
    if (_selectedRole == 'ADMIN' && adminSecret != 'admin123') {
      _showSnackbar('Mã bí mật Admin không chính xác! (Mẹo: admin123)', isError: true);
      return;
    }

    setState(() => _isLoading = true);

    final error = await SocketService.register(
      host: host,
      port: port,
      username: user,
      password: pass,
      role: _selectedRole,
    );

    if (!mounted) return;
    setState(() => _isLoading = false);

    if (error == null) {
      _showSnackbar('Đăng ký tài khoản thành công! Tự động đăng nhập...');
      // Tự động đăng nhập
      final loginErr = await SocketService().connectAndLogin(
        host: host,
        port: port,
        username: user,
        password: pass,
      );
      if (!mounted) return;
      if (loginErr == null) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (_) => const ChatRoomScreen()),
        );
      } else {
        _tabController.animateTo(0);
        _loginUserCtrl.text = user;
        _loginPassCtrl.text = pass;
      }
    } else {
      _showSnackbar(error, isError: true);
    }
  }

  void _showSnackbar(String msg, {bool isError = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        backgroundColor: isError ? Colors.redAccent : const Color(0xFF10B981),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A), // Slate 900
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
          child: Container(
            constraints: const BoxConstraints(maxWidth: 480),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(24),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.25),
                  blurRadius: 30,
                  offset: const Offset(0, 10),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Header Banner Gradient
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 20),
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        colors: [Color(0xFF9333EA), Color(0xFF4F46E5)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                    ),
                    child: Column(
                      children: [
                        Container(
                          width: 60,
                          height: 60,
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.2),
                            shape: BoxShape.circle,
                          ),
                          child: const Center(
                            child: Text(
                              '💬',
                              style: TextStyle(fontSize: 28),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        const Text(
                          'TOUR CREW CHAT',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 1.2,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Socket TCP • Đa luồng • Phân quyền & Lịch sử',
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.85),
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),

                  // Tab Selector
                  Container(
                    color: const Color(0xFFF8FAFC),
                    child: TabBar(
                      controller: _tabController,
                      indicatorColor: const Color(0xFF9333EA),
                      indicatorWeight: 3,
                      labelColor: const Color(0xFF9333EA),
                      unselectedLabelColor: const Color(0xFF64748B),
                      labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                      tabs: const [
                        Tab(text: '🔑 ĐĂNG NHẬP'),
                        Tab(text: '📝 ĐĂNG KÝ MỚI'),
                      ],
                    ),
                  ),

                  // Tab Views
                  SizedBox(
                    height: 390,
                    child: TabBarView(
                      controller: _tabController,
                      children: [
                        _buildLoginTab(),
                        _buildRegisterTab(),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLoginTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                flex: 3,
                child: TextField(
                  controller: _loginHostCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Máy chủ (Host)',
                    prefixIcon: Icon(Icons.dns, size: 20),
                    isDense: true,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                flex: 2,
                child: TextField(
                  controller: _loginPortCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Cổng',
                    prefixIcon: Icon(Icons.numbers, size: 20),
                    isDense: true,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Tài khoản chọn nhanh
          Wrap(
            spacing: 6,
            runSpacing: 4,
            children: _demoAccounts.map((acc) {
              final isSelected = _loginUserCtrl.text == acc;
              return ChoiceChip(
                label: Text(
                  acc == 'admin' ? '👑 $acc' : '👤 $acc',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                    color: isSelected ? Colors.white : const Color(0xFF334155),
                  ),
                ),
                selected: isSelected,
                selectedColor: const Color(0xFF9333EA),
                onSelected: (val) {
                  setState(() {
                    _loginUserCtrl.text = acc;
                    _loginPassCtrl.text = '123';
                  });
                },
              );
            }).toList(),
          ),
          const SizedBox(height: 12),

          TextField(
            controller: _loginUserCtrl,
            decoration: const InputDecoration(
              labelText: 'Tên tài khoản',
              prefixIcon: Icon(Icons.person, size: 20),
              isDense: true,
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _loginPassCtrl,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: 'Mật khẩu',
              prefixIcon: Icon(Icons.lock, size: 20),
              isDense: true,
            ),
          ),
          const SizedBox(height: 20),

          ElevatedButton(
            onPressed: _isLoading ? null : _handleLogin,
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              backgroundColor: const Color(0xFF9333EA),
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              elevation: 2,
            ),
            child: _isLoading
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                  )
                : const Text(
                    'Đăng nhập vào phòng ➔',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildRegisterTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: _regUserCtrl,
            decoration: const InputDecoration(
              labelText: 'Tên tài khoản mới',
              prefixIcon: Icon(Icons.person_add, size: 20),
              isDense: true,
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _regPassCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Mật khẩu',
                    prefixIcon: Icon(Icons.lock_outline, size: 20),
                    isDense: true,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: _regConfirmPassCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Xác nhận',
                    prefixIcon: Icon(Icons.check_circle_outline, size: 20),
                    isDense: true,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Lựa chọn vai trò
          DropdownButtonFormField<String>(
            initialValue: _selectedRole,
            decoration: const InputDecoration(
              labelText: 'Phân quyền tài khoản',
              prefixIcon: Icon(Icons.shield, size: 20),
              isDense: true,
            ),
            items: const [
              DropdownMenuItem(value: 'USER', child: Text('👤 USER (Thành viên)')),
              DropdownMenuItem(value: 'ADMIN', child: Text('👑 ADMIN (Quản trị viên)')),
            ],
            onChanged: (val) {
              setState(() {
                _selectedRole = val ?? 'USER';
              });
            },
          ),
          const SizedBox(height: 10),

          if (_selectedRole == 'ADMIN') ...[
            TextField(
              controller: _regAdminSecretCtrl,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'Mã bí mật Admin (mẹo: admin123)',
                prefixIcon: Icon(Icons.vpn_key, size: 20),
                isDense: true,
              ),
            ),
            const SizedBox(height: 10),
          ],

          const SizedBox(height: 8),
          ElevatedButton(
            onPressed: _isLoading ? null : _handleRegister,
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              backgroundColor: const Color(0xFF10B981), // Green
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              elevation: 2,
            ),
            child: _isLoading
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                  )
                : const Text(
                    'Tạo tài khoản & Tham gia ngay ➔',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                  ),
          ),
        ],
      ),
    );
  }
}
