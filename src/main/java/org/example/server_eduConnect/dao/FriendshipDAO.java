// src/org/example/server_eduConnect/dao/FriendshipDAO.java
package org.example.server_eduConnect.dao;
import org.example.server_eduConnect.model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FriendshipDAO {
    public static List<User> getFriends(int userId) {
        List<User> friends = new ArrayList<>();
        String sql = """
        SELECT DISTINCT
            u.user_id, u.name, u.username, u.email, u.avatar,
            u.role, u.status, u.last_online
        FROM users u
        INNER JOIN friendships f ON (
            (f.user1_id = ? AND f.user2_id = u.user_id) OR
            (f.user2_id = ? AND f.user1_id = u.user_id)
        )
        WHERE u.user_id != ?
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User friend = new User();
                friend.setUserId(rs.getInt("user_id"));
                friend.setName(rs.getString("name"));
                friend.setUsername(rs.getString("username"));
                friend.setEmail(rs.getString("email"));
                friend.setAvatar(rs.getString("avatar"));
                friend.setRole(rs.getString("role"));
                // SIÊU QUAN TRỌNG: CHUYỂN status ('online'/'offline') → boolean
                String status = rs.getString("status");
                friend.setOnline("online".equalsIgnoreCase(status));
                friends.add(friend);
            }
        } catch (SQLException e) {
            System.err.println("LỖI LẤY DANH SÁCH BẠN BÈ: " + e.getMessage());
            e.printStackTrace();
        }
        return friends;
    }
    public static boolean areFriends(int userId1, int userId2) {
        int a = Math.min(userId1, userId2);
        int b = Math.max(userId1, userId2);
        String sql = "SELECT 1 FROM friendships WHERE user1_id = ? AND user2_id = ? LIMIT 1";
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
}