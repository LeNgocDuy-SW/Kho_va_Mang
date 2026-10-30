class ChatMessage {
  final String sender;
  final String role;
  final String text;
  final String time;
  final bool isMe;
  final bool isPrivate;
  final bool isSystem;

  ChatMessage({
    required this.sender,
    required this.role,
    required this.text,
    required this.time,
    required this.isMe,
    this.isPrivate = false,
    this.isSystem = false,
  });

  bool get isAdmin => role.toUpperCase() == 'ADMIN';
}

class OnlineUser {
  final String username;
  final String role;

  OnlineUser({
    required this.username,
    required this.role,
  });

  bool get isAdmin => role.toUpperCase() == 'ADMIN';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OnlineUser &&
          runtimeType == other.runtimeType &&
          username.toLowerCase() == other.username.toLowerCase();

  @override
  int get hashCode => username.toLowerCase().hashCode;
}
