package com.example.dikjstra;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HelloApplication extends Application {

    /** El panel izquierdo mide como máximo lo que medía antes (500) y se puede encoger hasta el mínimo. */
    private static final double ANCHO_MAX_PANEL = 500;
    private static final double ANCHO_MIN_PANEL = 300;      // ancho mínimo del CONTENIDO del panel
    private static final double ANCHO_MIN_VISIBLE = 200;    // lo más angosto que se puede dejar la zona visible; el resto se desliza

    // Ruta real (la que devuelve Dijkstra)
    private final Label lblRuta = new Label("Selecciona origen y destino");
    private final Label lblDistancia = new Label();

    // Ruta simulada
    private final Label lblSimulada = new Label("Sin ruta simulada");

    // Información del edificio seleccionado
    private final Label lblNombre = new Label();
    private final Label lblDescripcion = new Label();
    private final VBox tarjetaInfo = new VBox(6);   // oculta hasta que se selecciona un nodo

    private MapaView mapa;                          // null si la imagen no se pudo cargar

    @Override
    public void start(Stage stage) {
        BorderPane raiz = new BorderPane();
        raiz.setCenter(crearZonaMapa());     // primero el mapa...

        ScrollPane scroll = envolverEnScroll(crearPanelLateral());   // ...porque el panel lo controla
        raiz.setLeft(new HBox(scroll, crearDivisor(scroll)));

        Scene escena = new Scene(raiz, 1100, 700);

        var css = getClass().getResource("css/estilos.css");
        if (css != null) {
            escena.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Rutas ITSUR - Dijkstra");
        stage.setMinWidth(1000);
        stage.setMinHeight(500);
        stage.setScene(escena);
        stage.show();

        // PRUEBA (descomenta para ver la ruta real y la simulada a la vez):
        // mostrarRutaReal(List.of("L", "A", "G", "F", "I"), 842.6);
    }

    /**
     * AQUÍ AÑADES / EDITAS EDIFICIOS.
     * x e y son píxeles de la imagen ORIGINAL itsur.jpeg.
     * Opcional por edificio: .conColor("#hex")  .conIcono("/ICONOS/archivo.png")  .conTamano(ancho, alto)
     */
    private List<Edificio> crearEdificios() {
        return List.of(
                new Edificio("A", "Biblioteca", "Aulas de ingeniería.", 390, 200),
                new Edificio("B", "Vinculacion", "Laboratorios de cómputo.", 170, 350),
                new Edificio("C", "Estacionamiento", "Edificio de facultad de Sistemas, Gastronomia, etc", 800, 250),
                new Edificio("D", "Auditorio abandonado", " etc", 175, 710),
                new Edificio("E", "Edificio B", "Edificio de facultad de Sistemas, Gastronomia, etc", 710, 495),
                new Edificio("F", "Edificio A", "Edificio de facultad de Sistemas, Gastronomia, etc", 475, 450),
                new Edificio("G", "Edificio E", "Edificio de facultad de Sistemas, Gastronomia, etc", 400, 680),
                new Edificio("H", "TICS", "Edificio de facultad de Sistemas, Gastronomia, etc", 640, 682),
                new Edificio("I", "Cafeteria", "Edificio de facultad de Sistemas, Gastronomia, etc", 480, 795),
                new Edificio("J", "Palapas", "Edificio de facultad de Sistemas, Gastronomia, etc", 370, 350),
                new Edificio("K", "Entrada principal", "Edificio de facultad de Sistemas, Gastronomia, etc", 520, 150),
                new Edificio("L", "Cabaña", "Edificio de facultad de Sistemas, Gastronomia, etc", 130, 500),
                new Edificio("M", "Chancha de Futbol", "Edificio de facultad de Sistemas, Gastronomia, etc", 975, 650),

                //RUTAS
                new Edificio("N", "ruta 1", "Edificio de facultad de Sistemas, Gastronomia, etc", 640, 682),
                new Edificio("O", "ruta 2", "Edificio de facultad de Sistemas, Gastronomia, etc", 480, 795),
                new Edificio("P", "ruta 3", "Edificio de facultad de Sistemas, Gastronomia, etc", 370, 350),
                new Edificio("Q", "ruta 4", "Edificio de facultad de Sistemas, Gastronomia, etc", 520, 150),
                new Edificio("R", "ruta 5", "Edificio de facultad de Sistemas, Gastronomia, etc", 130, 500),
                new Edificio("S", "ruta 6", "Edificio de facultad de Sistemas, Gastronomia, etc", 975, 650),
                new Edificio("U", "ruta 7", "Edificio de facultad de Sistemas, Gastronomia, etc", 520, 150),
                new Edificio("V", "ruta 8", "Edificio de facultad de Sistemas, Gastronomia, etc", 130, 500),
                new Edificio("W", "ruta 9", "Edificio de facultad de Sistemas, Gastronomia, etc", 975, 650)
        );
    }

    // ===================================================================== rutas

    /**
     * PUNTO DE ENTRADA PARA DIJKSTRA.
     * Recibe los ids del camino más corto (en orden) y la distancia en metros que calculó
     * el algoritmo. Ej: mostrarRutaReal(List.of("A", "B", "C", "D"), 523.4);
     * Convive con la ruta simulada: cada una se dibuja en su propia capa.
     */
    public void mostrarRutaReal(List<String> ids, double metros) {
        if (mapa == null) return;
        try {
            mapa.dibujarRuta(MapaView.TipoRuta.REAL, ids);
        } catch (IllegalArgumentException ex) {
            lblRuta.setText(ex.getMessage());
            ocultar(lblDistancia);
            return;
        }
        if (ids == null || ids.size() < 2) {
            limpiarRutaReal();
            return;
        }
        lblRuta.setText(String.join(" → ", ids));
        lblDistancia.setText(formatearMetros(metros));
        mostrar(lblDistancia);
    }

    public void limpiarRutaReal() {
        if (mapa != null) mapa.limpiarRuta(MapaView.TipoRuta.REAL);
        lblRuta.setText("Selecciona origen y destino");
        ocultar(lblDistancia);
    }

    private void mostrarRutaSimulada(List<String> ids, Double metros) {
        if (mapa == null) return;
        try {
            mapa.dibujarRuta(MapaView.TipoRuta.SIMULADA, ids);
        } catch (IllegalArgumentException ex) {
            lblSimulada.setText(ex.getMessage());
            return;
        }
        if (ids.size() < 2) {
            limpiarRutaSimulada();
            return;
        }
        String texto = String.join(" → ", ids);
        if (metros != null) texto += "\n" + formatearMetros(metros);
        lblSimulada.setText(texto);
    }

    private void limpiarRutaSimulada() {
        if (mapa != null) mapa.limpiarRuta(MapaView.TipoRuta.SIMULADA);
        lblSimulada.setText("Sin ruta simulada");
    }

    private static String formatearMetros(double metros) {
        return String.format(Locale.US, "%,.1f m", metros);
    }

    // ===================================================================== panel

    /** Recibe el edificio seleccionado, o null si se deseleccionó. */
    private void mostrarInfo(Edificio e) {
        if (e == null) {
            ocultar(tarjetaInfo);
            return;
        }

        lblNombre.setText(e.nombre());
        lblDescripcion.setText(e.descripcion());

        boolean aparece = !tarjetaInfo.isVisible();
        mostrar(tarjetaInfo);
        if (aparece) {
            FadeTransition fade = new FadeTransition(Duration.millis(180), tarjetaInfo);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
    }

    private VBox crearPanelLateral() {
        // --- Ruta real ---
        Label titulo = new Label("Ruta más corta");
        titulo.getStyleClass().add("titulo");
        lblRuta.setWrapText(true);
        lblRuta.getStyleClass().add("texto-real");
        lblDistancia.getStyleClass().add("distancia-real");
        ocultar(lblDistancia);

        VBox tarjetaReal = new VBox(6, titulo, lblRuta, lblDistancia);
        tarjetaReal.getStyleClass().addAll("tarjeta", "tarjeta-real");
        tarjetaReal.setPadding(new Insets(14));

        // --- Información del edificio (empieza oculta) ---
        lblNombre.getStyleClass().add("nombre-edificio");
        lblDescripcion.setWrapText(true);
        tarjetaInfo.getChildren().addAll(lblNombre, lblDescripcion);
        tarjetaInfo.getStyleClass().add("tarjeta");
        tarjetaInfo.setPadding(new Insets(14));
        ocultar(tarjetaInfo);

        // --- Ajustes (tarjeta desplegable): solo el mapa dibujado con CSS ---
        HBox swCss = crearSwitch("Mapa dibujado con CSS", v -> {
            if (mapa != null) mapa.setModoCss(v);
        });

        TitledPane ajustes = new TitledPane("Ajustes", new VBox(14, swCss));
        ajustes.getStyleClass().add("tarjeta-ajustes");
        ajustes.setExpanded(false);
        ajustes.setAnimated(true);

        // Espacio flexible: empuja los ajustes al fondo del panel
        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);

        VBox panel = new VBox(12, tarjetaReal, crearSimuladorRuta(), new Separator(),
                tarjetaInfo, espacio, ajustes);
        panel.setPadding(new Insets(16));
        panel.setMinWidth(ANCHO_MIN_PANEL);
        panel.getStyleClass().add("panel-lateral");
        return panel;
    }

    /**
     * Pone el panel dentro de un ScrollPane: barra vertical y horizontal que aparecen solo cuando
     * hacen falta (tarjetas altas, ajustes desplegados, panel estrechado con el divisor...).
     */
    private ScrollPane envolverEnScroll(VBox contenido) {
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.getStyleClass().add("scroll-panel");
        scroll.setFitToWidth(true);                                   // el contenido toma el ancho visible
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMinWidth(ANCHO_MIN_VISIBLE);
        scroll.setPrefWidth(ANCHO_MAX_PANEL);
        scroll.setMaxWidth(ANCHO_MAX_PANEL);

        // El contenido mide al menos todo el alto visible (así "Ajustes" queda abajo)
        // y, si es más alto, aparece la barra vertical.
        contenido.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));
        return scroll;
    }

    /** Barra delgada a la derecha del panel: al arrastrarla cambia su ancho (entre el mínimo y el máximo). */
    private Node crearDivisor(Region panel) {
        Region divisor = new Region();
        divisor.getStyleClass().add("divisor-panel");
        divisor.setMinWidth(6);
        divisor.setPrefWidth(6);
        divisor.setMaxWidth(6);
        divisor.setCursor(Cursor.H_RESIZE);

        double[] inicio = new double[2];   // [0] ancho del panel al empezar, [1] posición X del mouse
        divisor.setOnMousePressed(ev -> {
            inicio[0] = panel.getWidth();
            inicio[1] = ev.getScreenX();
        });
        divisor.setOnMouseDragged(ev -> {
            double nuevo = inicio[0] + (ev.getScreenX() - inicio[1]);
            panel.setPrefWidth(Math.max(ANCHO_MIN_VISIBLE, Math.min(ANCHO_MAX_PANEL, nuevo)));
        });
        return divisor;
    }

    /** Tarjeta para probar el dibujo de una ruta a mano. Convive con la ruta real. */
    private Node crearSimuladorRuta() {
        Label etiqueta = new Label("Simulador de ruta");
        etiqueta.getStyleClass().add("subtitulo");

        TextField txtRuta = new TextField("A,B,C,D,F,I");
        txtRuta.setPromptText("Ids separados por coma");

        TextField txtMetros = new TextField("350");
        txtMetros.setPromptText("Distancia en metros (opcional)");

        lblSimulada.setWrapText(true);
        lblSimulada.getStyleClass().add("texto-sim");

        Button btnDibujar = new Button("Dibujar");
        btnDibujar.getStyleClass().add("boton");
        btnDibujar.setOnAction(ev -> {
            List<String> ids = Arrays.stream(txtRuta.getText().split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .collect(Collectors.toList());

            Double metros = null;
            String texto = txtMetros.getText().trim().replace(',', '.');
            if (!texto.isEmpty()) {
                try {
                    metros = Double.parseDouble(texto);
                } catch (NumberFormatException ex) {
                    lblSimulada.setText("La distancia debe ser un número");
                    return;
                }
            }
            mostrarRutaSimulada(ids, metros);
        });
        txtRuta.setOnAction(ev -> btnDibujar.fire());   // Enter también dibuja

        Button btnLimpiar = new Button("Limpiar");
        btnLimpiar.getStyleClass().addAll("boton", "boton-secundario");
        btnLimpiar.setOnAction(ev -> limpiarRutaSimulada());

        VBox caja = new VBox(8, etiqueta, txtRuta, txtMetros,
                new HBox(8, btnDibujar, btnLimpiar), lblSimulada);
        caja.getStyleClass().addAll("tarjeta", "tarjeta-sim");
        caja.setPadding(new Insets(14));
        return caja;
    }

    /** Un "switch" hecho con un ToggleButton estilizado por CSS (clase .switch). */
    private HBox crearSwitch(String texto, Consumer<Boolean> alCambiar) {
        ToggleButton sw = new ToggleButton();
        sw.getStyleClass().add("switch");
        sw.setGraphic(new Circle(9, Color.WHITE));
        sw.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        sw.selectedProperty().addListener((obs, antes, ahora) -> alCambiar.accept(ahora));

        Label etiqueta = new Label(texto);
        etiqueta.setWrapText(true);                         // si el panel se encoge, el texto baja de línea
        etiqueta.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(etiqueta, Priority.ALWAYS);

        HBox fila = new HBox(10, sw, etiqueta);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    private static void mostrar(Node n) {
        n.setVisible(true);
        n.setManaged(true);
    }

    private static void ocultar(Node n) {
        n.setVisible(false);
        n.setManaged(false);   // también libera el espacio que ocupaba
    }

    // ===================================================================== mapa

    private Node crearZonaMapa() {
        StackPane zona = new StackPane();
        zona.getStyleClass().add("zona-mapa");
        zona.setMinSize(0, 0);

        URL url = getClass().getResource("/MAPAS/itsur.jpeg");
        if (url == null) {
            zona.getChildren().add(new Label("No se encontró /MAPAS/itsur.jpeg en resources"));
            return zona;
        }

        Image imagen = new Image(url.toExternalForm());
        if (imagen.isError()) {
            zona.getChildren().add(new Label("No se pudo cargar el mapa: " + imagen.getException()));
            return zona;
        }

        mapa = new MapaView(imagen, crearEdificios(), this::mostrarInfo);
        zona.getChildren().add(mapa);
        return zona;
    }
}