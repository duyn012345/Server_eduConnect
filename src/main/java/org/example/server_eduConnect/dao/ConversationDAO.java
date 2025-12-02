package org.example.server_eduConnect.dao;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class ConversationDAO {
    public static int getOrCreatePrivateConversation(int userId1, int userId2) {
        // đảm bảo nhỏ trước lớn sau để tránh trùng lặp khi tìm
        int smaller = Math.min(userId1, userId2);
        int larger = Math.max(userId1, userId2);
        String sql = """
            SELECT c.conversation_id
            FROM conversations c
            JOIN conversation_members cm1 ON cm1.conversation_id = c.conversation_id AND cm1.user_id = ?
            JOIN conversation_members cm2 ON cm2.conversation_id = c.conversation_id AND cm2.user_id = ?
            WHERE c.type = 'private'
            LIMIT 1
            """;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, smaller);
            ps.setInt(2, larger);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // chưa có -> tạo mới (dùng userId1 làm creator)
        return createPrivateConversation(userId1, userId2);
    }
    private static int createPrivateConversation(int userId1, int userId2) {
        // chèn creator vào conversations (user_id NOT NULL theo schema ban đầu)
        String sqlConv = "INSERT INTO conversations (user_id, type) VALUES (?, 'private')";
        String sqlMember = "INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)";
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            int convId = -1;
            try (PreparedStatement ps = conn.prepareStatement(sqlConv, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId1); // creator
                int rows = ps.executeUpdate();
                if (rows == 0) throw new SQLException("Tạo conversation thất bại, không có hàng được chèn.");
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs != null && rs.next()) {
                        convId = rs.getInt(1);
                    } else {
                        throw new SQLException("Không lấy được generated key cho conversation.");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlMember)) {
                ps.setInt(1, convId);
                ps.setInt(2, userId1);
                ps.executeUpdate();
                ps.setInt(1, convId);
                ps.setInt(2, userId2);
                ps.executeUpdate();
            }
            conn.commit();
            return convId;
        } catch (SQLException e) {
            e.printStackTrace();
            // nếu rollback cần thiết, try-catch để đảm bảo rollback
            return -1;
        }
    }
    public static List<Integer> getMemberIds(int convId) {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT user_id FROM conversation_members WHERE conversation_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, convId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
    public static boolean isMember(int convId, int userId) {
        String sql = "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, convId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}