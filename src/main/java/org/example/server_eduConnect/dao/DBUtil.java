package org.example.server_eduConnect.dao;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBUtil {

    private static final String URL = "jdbc:mysql://127.0.0.1:3306/educonnect?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "Myduyen@0605";

    public static Connection getConnection() {
        try {
            // Bắt buộc phải có dòng này với driver 8.0+
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Kết nối CSDL thất bại: " + e.getMessage());
            return null;
        }
    }
}