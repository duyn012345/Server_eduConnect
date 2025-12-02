package org.example.server_eduConnect.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    public static boolean saveMessage(int convId, int senderId, String content) {
        String sql = "INSERT INTO messages (conversation_id, sender_id, content) VALUES (?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, convId);
            ps.setInt(2, senderId);
            ps.setString(3, content);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // MessageDAO.java
    public static List<String> getChatHistory(int convId) {
        List<String> list = new ArrayList<>();
        String sql = """
                SELECT u.username, m.content, DATE_FORMAT(m.created_at, '%H:%i %d/%m') as time_formatted
                FROM messages m
                JOIN users u ON m.sender_id = u.user_id
                WHERE m.conversation_id = ?
                ORDER BY m.created_at ASC
                LIMIT 300
                """;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, convId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String username = rs.getString("username");
                String content = rs.getString("content");
                String time = rs.getString("time_formatted");

                // CHỈ GỬI USERNAME + NỘI DUNG → SIÊU SẠCH!
                list.add(username + "|" + time + "|" + content);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}