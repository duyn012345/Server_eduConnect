package org.example.server_eduConnect.dao;

import org.example.server_eduConnect.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FriendRequestDAO {

    public static boolean sendFriendRequest(int requesterId, int receiverId) {
        if (requesterId == receiverId) return false;

        String checkSql = """
        SELECT 1 FROM friend_requests 
        WHERE (requester_id = ? AND receiver_id = ?) 
           OR (requester_id = ? AND receiver_id = ?)
        """;

        String insertSql = "INSERT INTO friend_requests (requester_id, receiver_id, status) VALUES (?, ?, 'pending')";
        String notifSql = "INSERT INTO notifications (user_id, title, content, created_at) VALUES (?, ?, ?, NOW())";

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);

            // Kiểm tra đã gửi lời mời chưa (cả 2 chiều)
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setInt(1, requesterId);
                check.setInt(2, receiverId);
                check.setInt(3, receiverId);   // ← THIẾU DÒNG NÀY TRƯỚC ĐÂY!
                check.setInt(4, requesterId);   // ← THIẾU DÒNG NÀY TRƯỚC ĐÂY!
                if (check.executeQuery().next()) {
                    conn.rollback();
                    return false; // đã gửi rồi
                }
            }

            // Gửi lời mời
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, requesterId);
                ps.setInt(2, receiverId);
                ps.executeUpdate();
            }

            // Gửi thông báo cho người nhận
            String senderName = UserDAO.getById(requesterId).getName();
            try (PreparedStatement ps = conn.prepareStatement(notifSql)) {
                ps.setInt(1, receiverId);
                ps.setString(2, "Lời mời kết bạn");
                ps.setString(3, senderName + " đã gửi lời mời kết bạn");
                ps.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean acceptFriendRequest(int requesterId, int receiverId) {
        String updateRequest = "UPDATE friend_requests SET status='accepted' " +
                "WHERE requester_id=? AND receiver_id=? AND status='pending'";
        String insertFriend = "INSERT INTO friendships (user1_id, user2_id) VALUES (?, ?)";
        try (Connection c = DBUtil.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps1 = c.prepareStatement(updateRequest)) {
                ps1.setInt(1, requesterId);
                ps1.setInt(2, receiverId);
                int updated = ps1.executeUpdate();
                if (updated == 0) {
                    c.rollback();
                    return false;
                }
            }
            // insert friendship theo thứ tự nhỏ -> lớn để tránh trùng
            int a = Math.min(requesterId, receiverId);
            int b = Math.max(requesterId, receiverId);
            try (PreparedStatement ps2 = c.prepareStatement(insertFriend)) {
                ps2.setInt(1, a);
                ps2.setInt(2, b);
                ps2.executeUpdate();
            }
            c.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean rejectFriendRequest(int requesterId, int receiverId) {
        String sql = "DELETE FROM friend_requests WHERE requester_id=? AND receiver_id=? AND status='pending'";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requesterId);
            ps.setInt(2, receiverId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean areFriends(int userId1, int userId2) {
        int a = Math.min(userId1, userId2);
        int b = Math.max(userId1, userId2);
        String sql = "SELECT 1 FROM friendships WHERE user1_id=? AND user2_id=? LIMIT 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static List<User> getFriendSuggestions(int userId) {
        String sql = "SELECT * FROM users u " +
                "WHERE u.user_id <> ? " +
                "AND u.user_id NOT IN ( " +
                "    SELECT CASE WHEN f.user1_id = ? THEN f.user2_id ELSE f.user1_id END " +
                "    FROM friendships f " +
                "    WHERE f.user1_id=? OR f.user2_id=? " +
                ") " +
                "AND u.user_id NOT IN ( " +
                "    SELECT receiver_id FROM friend_requests WHERE requester_id=? AND status='pending' " +
                ") " +
                "AND u.user_id NOT IN ( " +
                "    SELECT requester_id FROM friend_requests WHERE receiver_id=? AND status='pending' " +
                ")"; // <-- thêm dòng này để loại những người đã gửi lời mời đến bạn
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ps.setInt(4, userId);
            ps.setInt(5, userId);
            ps.setInt(6, userId); // thêm tham số cho dòng mới
            ResultSet rs = ps.executeQuery();
            List<User> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new User(
                        rs.getInt("user_id"),
                        rs.getString("name"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("avatar"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getString("created_at"),
                        rs.getString("updated_at"),
                        rs.getString("last_online")
                ));
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    public static List<User> getPendingFriendRequests(int receiverId) {
        List<User> list = new ArrayList<>();
        String sql = """
            SELECT u.user_id, u.name, u.username, u.email, u.avatar, u.role, u.status,
                   u.created_at, u.updated_at, u.last_online
            FROM users u
            JOIN friend_requests fr ON u.user_id = fr.requester_id
            WHERE fr.receiver_id = ? AND fr.status = 'pending'
            """;

        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User u = new User(
                        rs.getInt("user_id"),
                        rs.getString("name"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("avatar"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getString("created_at"),
                        rs.getString("updated_at"),
                        rs.getString("last_online")
                );
                list.add(u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
