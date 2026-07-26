package app;

import javafx.application.Application;

/**
 * Lớp khởi động trung gian giúp ứng dụng chạy được bằng cả NetBeans Exec
 * và JavaFX Maven Plugin.
 */
public final class MainApp {
    private MainApp() {}

    public static void main(String[] args) {
        Application.launch(SalaryApplication.class, args);
    }
}
