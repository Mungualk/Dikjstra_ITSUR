package com.example.dikjstra;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) {
        BorderPane raiz = new BorderPane();
        raiz.setLeft(crearPanelLateral());
        raiz.setCenter(crearZonaMapa());

        Scene escena = new Scene(raiz, 1100, 700);

        var css = getClass().getResource("css/estilos.css");
        if (css != null) {
            escena.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Rutas ITSUR - Dijkstra");
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setScene(escena);
        stage.show();
    }

    private Node crearPanelLateral() {
        Label titulo = new Label("Ruta más corta");
        titulo.getStyleClass().add("titulo");

        ComboBox<String> origen = new ComboBox<>();
        origen.setPromptText("Origen");
        origen.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> destino = new ComboBox<>();
        destino.setPromptText("Destino");
        destino.setMaxWidth(Double.MAX_VALUE);

        Button calcular = new Button("Calcular ruta");
        calcular.setMaxWidth(Double.MAX_VALUE);

        Label resultado = new Label("Selecciona origen y destino");
        resultado.setWrapText(true);

        VBox panel = new VBox(12, titulo, origen, destino, calcular, resultado);
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(260);
        panel.getStyleClass().add("panel-lateral");
        return panel;
    }

    private Node crearZonaMapa() {
        StackPane zona = new StackPane();
        zona.getStyleClass().add("zona-mapa");
        zona.setMinSize(0, 0);

        try (InputStream in = getClass().getResourceAsStream("mapas/itsur.png")) {
            if (in != null) {
                ImageView mapa = new ImageView(new Image(in));
                mapa.setPreserveRatio(true);
                mapa.fitWidthProperty().bind(zona.widthProperty());
                mapa.fitHeightProperty().bind(zona.heightProperty());
                zona.getChildren().add(mapa);
            } else {
                zona.getChildren().add(new Label(
                        "Coloca la imagen en resources/com/example/dikjstra/mapas/itsur.png"));
            }
        } catch (IOException e) {
            zona.getChildren().add(new Label("No se pudo cargar el mapa: " + e.getMessage()));
        }
        return zona;
    }
}