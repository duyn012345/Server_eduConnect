package org.example.server_eduConnect.dao;

import org.example.server_eduConnect.model.User; // ← ĐÚNG PACKAGE CHUNG
import java.sql.*;

public class UserDAO {
    public static User login(String identifier, String password) {
        String sql = "SELECT user_id, name, username, email, avatar, role, status, created_at, updated_at, last_online " +
                "FROM users WHERE (email = ? OR username = ?) AND password = ? LIMIT 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);  // cùng 1 giá trị cho cả email và username
            ps.setString(3, password);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
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
                // Tự động set online khi login thành công
                setOnline(u.getUserId(), true);
                return u;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static boolean register(String name, String username, String email, String password) {
        String sql = "INSERT INTO users (name, username, email, password, role, status) VALUES (?, ?, ?, ?, 'user', 'offline')";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, password);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean isEmailExists(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            return ps.executeQuery().next();
        } catch (Exception e) {
            return false;
        }
    }
    public static boolean isUsernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static void setOnline(int userId, boolean online) {
        String sql = "UPDATE users SET status = ?, last_online = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, online ? "online" : "offline");
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }    /**
     * Lấy thông tin user theo user_id
     * @param userId ID của user cần tìm
     * @return User object hoặc null nếu không tồn tại
     */
    public static User getById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ? LIMIT 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRowToUser(rs);  // tái sử dụng method có sẵn
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static User getByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ? LIMIT 1";

        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapRowToUser(rs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    private static User mapRowToUser(ResultSet rs) throws SQLException {
        return new User(
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
    }
    public static String getNameByUserId(int userId) {
        String sql = "SELECT name FROM users WHERE user_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Người dùng";
    }

    public static String getUsernameByUserId(int userId) {
        String sql = "SELECT username FROM users WHERE user_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "unknown";
    }
    public static User findByNameOrUsername(String value) {
        String sql = "SELECT user_id, name, username, email, avatar, role, status, created_at, updated_at, last_online " +
                "FROM users WHERE name = ? OR username = ? LIMIT 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, value);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(
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
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    //editprofile
    public static boolean updateProfile(int userId, String name, String username, String email, String avatar) {
        String sql = "UPDATE users SET name = ?, email = ?, avatar = ? WHERE user_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, avatar);
            ps.setInt(4, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
