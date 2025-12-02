package org.example.server_eduConnect.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private String name;
    private String username;
    private String email;
    private String password; // chỉ dùng ở server, không gửi qua mạng
    private String avatar;
    private String role;
    private String status;        // "online" / "offline"
    private String createdAt;
    private String updatedAt;
    private String lastOnline;
    private boolean online;

    private static final String DEFAULT_AVATAR = "/image/avatar.png";

    // Constructor rỗng
    public User() {}

    public User(int userId, String name, String username, String email, String avatar,
                String role, String status, String createdAt, String updatedAt, String lastOnline) {
        this.userId = userId;
        this.name = name;
        this.username = username;
        this.email = email;
        this.avatar = (avatar == null || avatar.isEmpty()) ? DEFAULT_AVATAR : avatar;
        this.role = role != null ? role : "user";
        this.status = status != null ? status : "offline";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastOnline = lastOnline;
    }

    // ==================== GETTER / SETTER ====================
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
   // public String getAvatar() { return avatar; }
    public String getAvatar() {
        if (avatar == null || avatar.trim().isEmpty() || "null".equals(avatar)) {
            return DEFAULT_AVATAR; // "/image/avatar.png"
        }
        // Nếu người dùng đã upload ảnh (file:// hoặc http://) thì giữ nguyên
        if (avatar.startsWith("file:") || avatar.startsWith("http")) {
            return avatar;
        }
        // Nếu là đường dẫn cũ trong DB (ví dụ: "/avatar/default.png") → chuyển về chuẩn mới
        return DEFAULT_AVATAR;
    }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    // THÊM 2 DÒNG NÀY VÀO PHẦN GETTER/SETTER
    public String getCreatedAt() {return createdAt;}
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    public String getLastOnline() { return lastOnline; }
    public void setLastOnline(String lastOnline) { this.lastOnline = lastOnline; }


    public boolean isAdmin() { return "admin".equalsIgnoreCase(role); }
    public boolean isOnline() { return "online".equalsIgnoreCase(status); }
    public void setOnline(boolean online) {
        this.online = online;
    }
    // ==================== JSON HELPER ====================
    public String toJson() {
        return new Gson().toJson(this);
    }

    public static User fromJson(String json) {
        return new Gson().fromJson(json, User.class);
    }

    public static List<User> fromJsonArray(String json) {
        Type listType = new TypeToken<List<User>>(){}.getType();
        return new Gson().fromJson(json, listType);
    }

    // DÀNH RIÊNG CHO CLIENT: chỉ gửi những field cần thiết
    public static String toJsonArrayForClient(List<User> users) {
        List<HashMap<String, Object>> list = new ArrayList<>();
        for (User u : users) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("userId", u.userId);
            map.put("name", u.name);
            map.put("username", u.username);
            map.put("avatar", u.getAvatar());
            map.put("online", u.isOnline());
            list.add(map);
        }
        return new Gson().toJson(list);
    }


    // ==================== OVERRIDE ====================
    @Override
    public String toString() {
        return username + (isAdmin() ? " (Admin)" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return userId == ((User) o).userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
