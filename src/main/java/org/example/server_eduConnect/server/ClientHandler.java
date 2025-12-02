package org.example.server_eduConnect.server;

import org.example.server_eduConnect.dao.*;
import org.example.server_eduConnect.model.Notification;
import org.example.server_eduConnect.model.User;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private User currentUser;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received: " + line);
                if (line.equalsIgnoreCase("LOGOUT")) {
                    send("LOGOUT_OK");
                    break;
                }
                handleCommand(line);
            }
        } catch (IOException e) {
            System.err.println("Mất kết nối với client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleCommand(String line) {
        String[] parts = line.split("\\|", 2);
        String cmd = parts[0].trim().toUpperCase();
        String rest = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "REGISTER" -> handleRegister(rest);
            case "LOGIN" -> handleLogin(rest);
            case "GET_CONVERSATION" -> handleGetOrCreateConversation(rest);
            case "CREATE_CONVERSATION" -> handleCreateConversation(rest);
            case "SEND_MESSAGE" -> handleSendMessage(rest);
            case "GET_MESSAGES" -> handleGetMessages(rest);
            case "FRIEND_REQUEST" -> handleFriendRequest(rest);
            case "GET_FRIEND_REQUESTS" -> { if (currentUser != null) handleGetFriendRequests(); }
            case "REQUEST_ONLINE_LIST" -> {
                broadcastOnlineListToAll();  // Gửi danh sách online có tên thật cho tất cả
                send("ONLINE_USERS|" + getOnlineListPayload()); // Gửi riêng cho người yêu cầu (an toàn tuyệt đối)
            }
            case "ACCEPT_FRIEND" -> handleAcceptFriend(rest);
            case "REJECT_FRIEND" -> handleRejectFriend(rest);
            case "GET_FRIEND_LIST" -> sendFriendList();
            case "GET_NOTIFICATIONS" -> handleGetNotifications();
            case "GET_SUGGESTIONS" -> { if (currentUser != null) handleGetSuggestions(); }
            case "MARK_NOTIFICATION_READ" -> handleMarkNotificationRead(rest);
            case "MARK_ALL_NOTIFICATIONS_READ" -> handleMarkAllRead();
            case "FILE_OFFER" -> handleFileOffer(rest);
            case "UPDATE_PROFILE" -> handleUpdateProfile(rest);
            case "LOGOUT" -> { send("LOGOUT_OK"); return; }
            default -> send("ERROR|Lệnh không hợp lệ: " + cmd);
        }
    }

    private void handleRegister(String rest) {
        String[] p = rest.split("\\|", 4);
        if (p.length < 4) { send("REGISTER_FAIL|Thiếu thông tin"); return; }
        String name = p[0], username = p[1], email = p[2], pass = p[3];

        if (UserDAO.isEmailExists(email)) { send("REGISTER_FAIL|Email đã tồn tại"); return; }
        if (UserDAO.isUsernameExists(username)) { send("REGISTER_FAIL|Username đã tồn tại"); return; }

        boolean ok = UserDAO.register(name, username, email, pass);
        send(ok ? "REGISTER_OK" : "REGISTER_FAIL|Lỗi hệ thống");
    }

    private void handleLogin(String rest) {
        String[] p = rest.split("\\|", 2);
        if (p.length < 2) { send("LOGIN_FAIL|Thiếu thông tin"); return; }
        String id = p[0], pass = p[1];

        User user = UserDAO.login(id, pass);
        if (user == null) { send("LOGIN_FAIL|Sai tài khoản hoặc mật khẩu"); return; }

        // Ngăn đăng nhập trùng
        boolean already = Server.onlineUsers.values().stream()
                .anyMatch(ch -> ch.currentUser != null && ch.currentUser.getUserId() == user.getUserId());
        if (already) { send("LOGIN_FAIL|Tài khoản đang được đăng nhập ở nơi khác"); return; }

        this.currentUser = user;
        Server.onlineUsers.put(user.getUsername(), this);
        // THÔNG BÁO CHO TẤT CẢ MỌI NGƯỜI: NGƯỜI NÀY VỪA ONLINE
        broadcastToOthers("USER_ONLINE|" + user.getUsername());
        // Gửi danh sách online ngay lập tức cho người mới login
        broadcastOnlineListToAll();
        UserDAO.setOnline(user.getUserId(), true);

        String avatar = user.getAvatar() != null ? user.getAvatar() : "";
        String email = user.getEmail() != null ? user.getEmail() : "";
        String createdAt = user.getCreatedAt() != null ? user.getCreatedAt() : "";
        send("LOGIN_OK|" +
                user.getName() + "|" +
                user.getUsername() + "|" +
                email + "|" +
                user.getRole() + "|" +
                user.getUserId() + "|" +
                avatar + "|" +
                createdAt);  // THÊM DÒNG NÀY   // GỌI LẠI 1 LẦN NỮA SAU 1 GIÂY (SIÊU AN TOÀN)
        new Thread(() -> {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            sendFriendList();
            broadcastOnlineListToAll();
        }).start();
    }

    private void cleanup() {
        if (currentUser != null) {
            String username = currentUser.getUsername();
            Server.onlineUsers.remove(username);
            UserDAO.setOnline(currentUser.getUserId(), false);
            broadcastOnlineListToAll();
            broadcastToOthers("USER_OFFLINE|" + username);
            currentUser = null;
            broadcastToOthers("USER_OFFLINE|" + username);
            broadcastOnlineListToAll(); // Cập nhật lại danh sách cho tất cả
        }
        try { socket.close(); } catch (Exception ignored) {}
    }

    // === XỬ LÝ KẾT BẠN ===
    private void handleFriendRequest(String rest) {
        if (currentUser == null) { send("FRIEND_REQUEST_FAIL|Chưa đăng nhập"); return; }
        User receiver = UserDAO.findByNameOrUsername(rest.trim());
        if (receiver == null) { send("FRIEND_REQUEST_FAIL|Không tìm thấy người dùng"); return; }

        boolean success = FriendRequestDAO.sendFriendRequest(currentUser.getUserId(), receiver.getUserId());
        if (success) {
            send("FRIEND_REQUEST_SUCCESS|" + receiver.getName());
            ClientHandler target = Server.onlineUsers.get(receiver.getUsername());
            if (target != null) {
                target.send("FRIEND_REQUEST_RECEIVED|" + currentUser.getUserId() + "|" + currentUser.getName() + "|" + currentUser.getUsername());
            }
        } else {
            send("FRIEND_REQUEST_FAIL|Đã gửi rồi hoặc lỗi");
        }
    }

    private void handleGetFriendRequests() {
        List<User> requests = FriendRequestDAO.getPendingFriendRequests(currentUser.getUserId());
        String json = User.toJsonArrayForClient(requests);
        send("FRIEND_REQUEST_LIST|" + json);
    }

    private void handleAcceptFriend(String rest) {
        if (currentUser == null) return;
        int requesterId = Integer.parseInt(rest.trim());
        User requester = UserDAO.getById(requesterId);
        if (requester == null) { send("ACCEPT_FRIEND_FAIL|Không tìm thấy"); return; }

        boolean success = FriendRequestDAO.acceptFriendRequest(requesterId, currentUser.getUserId());
        if (success) {
            send("FRIEND_ACCEPTED|" + requester.getUsername() + "|" + requesterId);
            ClientHandler target = Server.onlineUsers.get(requester.getUsername());
            if (target != null) {
                target.send("FRIEND_ACCEPTED_BY|" + currentUser.getUsername() + "|" + currentUser.getUserId());
                target.sendFriendList();
            }
            sendFriendList();
            NotificationDAO.addNotification(requesterId, "Kết bạn thành công", currentUser.getName() + " đã chấp nhận lời mời kết bạn");
            // target = Server.onlineUsers.get(requester.getUsername());
            if (target != null) {
                target.send("NEW_NOTIFICATION"); // hoặc gửi luôn nội dung cũng được
                target.send("NOTIFICATION_UNREAD_COUNT|" + NotificationDAO.getUnreadCount(requesterId));
            }
        } else {
            send("ACCEPT_FRIEND_FAIL|Lỗi xử lý");
        }
    }

    private void handleRejectFriend(String rest) {
        if (currentUser == null) return;
        int requesterId = Integer.parseInt(rest.trim());
        User requester = UserDAO.getById(requesterId);
        if (requester == null) return;

        boolean success = FriendRequestDAO.rejectFriendRequest(requesterId, currentUser.getUserId());
        if (success) {
            send("REJECT_FRIEND_OK|" + requesterId);
            ClientHandler target = Server.onlineUsers.get(requester.getUsername());
            if (target != null) {
                target.send("FRIEND_REQUEST_REJECTED|" + currentUser.getUsername() + "|" + currentUser.getUserId());
            }
            NotificationDAO.addNotification(requesterId, "Lời mời bị từ chối", currentUser.getName() + " đã từ chối kết bạn");
            if (target != null) {
                target.send("NEW_NOTIFICATION");
                target.send("NOTIFICATION_UNREAD_COUNT|" + NotificationDAO.getUnreadCount(requesterId));
            }
        }
    }
    // === CHAT RIÊNG TƯ ===
    // === CHAT RIÊNG TƯ ===
    private void handleGetOrCreateConversation(String username) {
        if (currentUser == null) return;
        User friend = UserDAO.findByNameOrUsername(username);
        if (friend == null) { send("ERROR|Không tìm thấy người dùng"); return; }
        int convId = ConversationDAO.getOrCreatePrivateConversation(currentUser.getUserId(), friend.getUserId());
        send(convId > 0 ? "CONVERSATION_ID|" + username + "|" + convId : "ERROR|Tạo cuộc trò chuyện thất bại");
    }
    private void handleCreateConversation(String rest) {
        if (currentUser == null) return;

        String[] p = rest.split("\\|", 2);
        if (p.length < 2) return;

        String toUsername = p[0].trim();
        String firstMsg = p[1].trim();

        User friend = UserDAO.findByNameOrUsername(toUsername);
        if (friend == null) {
            send("ERROR|Không tìm thấy người dùng");
            return;
        }

        int convId = ConversationDAO.getOrCreatePrivateConversation(currentUser.getUserId(), friend.getUserId());
        if (convId <= 0) {
            send("ERROR|Tạo cuộc trò chuyện thất bại");
            return;
        }

        boolean saved = MessageDAO.saveMessage(convId, currentUser.getUserId(), firstMsg);
        if (!saved) {
            send("ERROR|Lưu tin nhắn thất bại");
            return;
        }
        // Gửi conversation_id cho người gửi trước
        send("CONVERSATION_ID|" + toUsername + "|" + convId);

        // Gửi tin nhắn realtime (có tên + giờ) cho cả 2
        broadcastPrivateMessage(convId, currentUser.getUserId(), firstMsg);
    }
    private void handleSendMessage(String rest) {
        if (currentUser == null) return;

        String[] p = rest.split("\\|", 2);
        if (p.length < 2) return;

        int convId;
        try {
            convId = Integer.parseInt(p[0]);
        } catch (Exception e) {return;}

        String message = p[1].trim();
        if (message.isEmpty()) return;

        if (!ConversationDAO.isMember(convId, currentUser.getUserId())) {
            send("ERROR|Không có quyền");
            return;
        }

        boolean saved = MessageDAO.saveMessage(convId, currentUser.getUserId(), message);
        if (!saved) {
            send("ERROR|Lưu tin nhắn thất bại");
            return;
        }

        // Gửi tin nhắn realtime cho cả 2 người (có tên thật + giờ)
        broadcastPrivateMessage(convId, currentUser.getUserId(), message);
    }

    private void broadcastPrivateMessage(int convId, int senderUserId, String messageContent) {
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm dd/MM"));

        String senderUsername = UserDAO.getUsernameByUserId(senderUserId);

        // ĐỊNH DẠNG SIÊU SẠCH: chỉ username + giờ + nội dung
        String packet = "PRIVATE_MSG|" + convId + "|" + senderUsername + "|" + time + "|" + messageContent;

        List<Integer> members = ConversationDAO.getMemberIds(convId);
        for (int memberId : members) {
            if (memberId == senderUserId) continue;

            User u = UserDAO.getById(memberId);
            if (u != null) {
                ClientHandler target = Server.onlineUsers.get(u.getUsername());
                if (target != null) {
                    target.send(packet);
                }
            }
        }
    }
    private void handleGetMessages(String rest) {
        if (currentUser == null) return;
        int convId;
        try {
            convId = Integer.parseInt(rest.trim());
        } catch (Exception e){ return;}

        if (!ConversationDAO.isMember(convId, currentUser.getUserId())) {
            send("CHAT_HISTORY|");
            return;
        }

        // ĐÃ SỬA: chỉ truyền 1 tham số
        List<String> history = MessageDAO.getChatHistory(convId);
        String payload = history.isEmpty() ? "" : String.join("\n", history);
        send("CHAT_HISTORY|" + payload);
    }

    // === DANH SÁCH BẠN BÈ & ONLINE ===
    private void sendFriendList() {
        if (currentUser == null) return;
        send("FRIEND_LIST|" + getFriendListPayload(currentUser.getUserId()));
    }

    private String getFriendListPayload(int userId) {
        List<User> friends = FriendshipDAO.getFriends(userId);
        if (friends.isEmpty()) return "";
        return friends.stream()
                .map(u -> u.getName() + "|" + u.getUsername() + "|" + u.getUserId())  // GỬI TÊN THẬT TRƯỚC
                .collect(java.util.stream.Collectors.joining(","));
    }
    private void broadcastOnlineListToAll() {
        StringBuilder list = new StringBuilder();
        for (ClientHandler ch : Server.onlineUsers.values()) {
            if (ch.currentUser != null) {
                if (list.length() > 0) list.append(",");
                list.append(ch.currentUser.getName())
                        .append("|")
                        .append(ch.currentUser.getUsername());
            }
        }
        String message = "ONLINE_USERS|" + list.toString();
        Server.onlineUsers.values().forEach(ch -> ch.send(message));
    }

    private void broadcastToOthers(String message) {
        Server.onlineUsers.values().stream()
                .filter(ch -> ch != this)
                .forEach(ch -> ch.send(message));
    }
    private String getOnlineListPayload() {
        StringBuilder list = new StringBuilder();
        for (ClientHandler ch : Server.onlineUsers.values()) {
            if (ch.currentUser != null) {
                if (list.length() > 0) list.append(",");
                list.append(ch.currentUser.getName())
                        .append("|")
                        .append(ch.currentUser.getUsername());
            }
        }
        return list.toString();
    }
    // === GỢI Ý, THÔNG BÁO ===
    private void handleGetSuggestions() {
        List<User> suggestions = FriendRequestDAO.getFriendSuggestions(currentUser.getUserId());
        send("SUGGESTIONS|" + User.toJsonArrayForClient(suggestions));
    }

    private void handleGetNotifications() {
        List<Notification> list = NotificationDAO.getNotifications(currentUser.getUserId());
        int unread = NotificationDAO.getUnreadCount(currentUser.getUserId());
        send("NOTIFICATIONS|" + unread + "|" + Notification.toJsonArray(list));
    }

    private void handleMarkNotificationRead(String rest) {
        int id = Integer.parseInt(rest.trim());
        NotificationDAO.markAsRead(id);
        send("NOTIFICATION_UNREAD_COUNT|" + NotificationDAO.getUnreadCount(currentUser.getUserId()));
    }

    private void handleMarkAllRead() {
        NotificationDAO.markAllAsRead(currentUser.getUserId());
        send("NOTIFICATION_UNREAD_COUNT|0");
    }

    private void handleFileOffer(String rest) {
        String[] p = rest.split("\\|", 5);
        if (p.length < 5) return;
        String to = p[0], filename = p[1], size = p[2], ip = p[3], port = p[4];
        ClientHandler target = Server.onlineUsers.get(to);
        if (target != null) {
            target.send("FILE_OFFER_FROM|" + currentUser.getUsername() + "|" + filename + "|" + size + "|" + ip + "|" + port);
        } else {
            send("FILE_OFFER_FAIL|" + to + " không online");
        }
    }
    //editprofile
    private void handleUpdateProfile(String rest) {
        if (currentUser == null) {
            send("UPDATE_PROFILE_FAIL|Chưa đăng nhập");
            return;
        }

        String[] p = rest.split("\\|", 3);
        if (p.length < 2) {
            send("UPDATE_PROFILE_FAIL|Thiếu thông tin");
            return;
        }

        String newName = p[0].trim();
        String newEmail = p[1].trim();
        String newAvatar = p.length > 2 ? p[2].trim() : (currentUser.getAvatar() != null ? currentUser.getAvatar() : "");

        if (newName.isEmpty() || newEmail.isEmpty()) {
            send("UPDATE_PROFILE_FAIL|Vui lòng điền đầy đủ");
            return;
        }

        if (!newEmail.equals(currentUser.getEmail()) && UserDAO.isEmailExists(newEmail)) {
            send("UPDATE_PROFILE_FAIL|Email đã được sử dụng");
            return;
        }

        boolean success = UserDAO.updateProfile(
                currentUser.getUserId(), newName, currentUser.getUsername(), newEmail,
                newAvatar.isEmpty() ? null : newAvatar
        );

        if (success) {
            currentUser.setName(newName);
            currentUser.setEmail(newEmail);
            if (!newAvatar.isEmpty()) currentUser.setAvatar(newAvatar);

            String avatarToSend = newAvatar != null ? newAvatar : "";
            String createdAt = (currentUser.getCreatedAt() != null) ? currentUser.getCreatedAt() : "";

            send("UPDATE_PROFILE_OK|" + newName + "|" + currentUser.getUsername() + "|" +
                    newEmail + "|" + currentUser.getRole() + "|" + currentUser.getUserId() + "|" +
                    avatarToSend + "|" + createdAt);

            // CẬP NHẬT CHO BẠN BÈ
            broadcastToFriends("USER_NAME_CHANGED|" + currentUser.getUserId() + "|" + newName);
            broadcastOnlineListToAll(); // Cập nhật tên mới realtime
        } else {
            send("UPDATE_PROFILE_FAIL|Lỗi hệ thống");
        }
    }
    // Gửi thông báo tới tất cả bạn bè của currentUser
    private void broadcastToFriends(String message) {
        if (currentUser == null) return;

        List<User> friends = FriendshipDAO.getFriends(currentUser.getUserId());
        for (User friend : friends) {
            ClientHandler friendHandler = Server.onlineUsers.get(friend.getUsername());
            if (friendHandler != null) {
                friendHandler.send(message);
            }
        }
    }
}