module com.example.dikjstra {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.dikjstra to javafx.fxml;
    exports com.example.dikjstra;
}