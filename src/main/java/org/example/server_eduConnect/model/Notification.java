package org.example.server_eduConnect.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class Notification {

    public int id;
    public String title;
    public String content;
    public boolean isRead;
    public String createdAt;

    public Notification() {}

    public Notification(int id, String title, String content, boolean isRead, String createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    // THÊM ĐẦY ĐỦ GETTER – GIẢI QUYẾT 100% LỖI!
    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        this.isRead = read;
    }

    // JSON utils
    public static Notification fromJson(String json) {
        return new Gson().fromJson(json, Notification.class);
    }

    public static List<Notification> fromJsonArray(String json) {
        Type listType = new TypeToken<List<Notification>>(){}.getType();
        return new Gson().fromJson(json, listType);
    }

    public static String toJson(Notification n) {
        return new Gson().toJson(n);
    }

    public static String toJsonArray(List<Notification> list) {
        return new Gson().toJson(list);
    }
}