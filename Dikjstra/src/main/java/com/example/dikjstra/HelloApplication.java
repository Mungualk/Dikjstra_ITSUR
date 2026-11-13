package com.example.dikjstra;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class HelloApplication extends Application {

    /** El panel izquierdo mide como máximo 420 px y se puede encoger hasta el mínimo. */
    private static final double ANCHO_MAX_PANEL = 420;
    private static final double ANCHO_MIN_PANEL = 260;      // ancho mínimo del CONTENIDO del panel
    private static final double ANCHO_MIN_VISIBLE = 180;    // lo más angosto que se puede dejar la zona visible; el resto se desliza

    /**
     * true  = en modo "Mapa dibujado con CSS", la tarjeta del edificio seleccionado muestra sus coordenadas (x, y).
     * false = nunca se muestran. Cámbialo aquí cuando quieras.
     */
    private static final boolean MOSTRAR_COORDENADAS = true;

    // ---- EASTER EGG: mapa secreto en el botón de capa ----
    /** Se escribe con el teclado (con el foco en el mapa). Escribirlo otra vez lo quita. */
    private static final String CODIGO_EASTER_EGG = "2357111";
    /** Imagen del mapa extra: en src/main/resources/MAPAS/ (PNG o JPG; JavaFX no lee WebP). */
    private static final String RUTA_MAPA_EXTRA = "/MAPAS/easteregg.png";
    /** Nombre que se ve en el botón de capa cuando le toca al mapa extra. */
    private static final String NOMBRE_MAPA_EXTRA = "Kanto";

    // Ruta real (la que devuelve Dijkstra)
    private final Label lblRuta = new Label("Selecciona origen y destino");
    private final Label lblDistancia = new Label();

    // Ruta simulada
    private final Label lblSimulada = new Label("Sin ruta simulada");

    // Información del edificio seleccionado
    private final Label lblNombre = new Label();
    private final Label lblDescripcion = new Label();
    private final Label lblCoordenadas = new Label();
    private final ImageView imgEdificio = new ImageView();     // foto del edificio (opcional)
    private final Region brilloFoto = new Region();                              // reflejo de "cristal" sobre la foto
    private final StackPane marcoFoto = new StackPane(imgEdificio, brilloFoto);  // borde azul fino alrededor de la foto
    private final StackPane marco = new StackPane(marcoFoto);                    // marco biselado exterior
    private final HBox marcoImagen = new HBox(marco);                            // fila que centra el marco (se muestra/oculta)
    private final Map<String, Image> cacheImagenes = new HashMap<>();
    private static final double ALTO_MAX_IMAGEN = 200;        // alto máximo de la foto en la tarjeta
    // Origen y destino elegidos con los botones de "Ruta más corta"
    private Edificio origen;
    private Edificio destino;
    private final Button btnOrigen = new Button();
    private final Button btnDestino = new Button();

    private final VBox tarjetaInfo = new VBox(6);   // oculta hasta que se selecciona un nodo
    private Edificio seleccionado;                  // nodo seleccionado ahora mismo (null = ninguno)
    private MapaView.ModoMapa modo = MapaView.ModoMapa.SATELITE;   // mapa que se está viendo ahora

    // Botón de capa (estilo Google Maps): muestra la miniatura del modo al que se puede cambiar
    private static final double TAM_MINIATURA = 92;
    private final ImageView miniaturaCapa = new ImageView();
    private final Label lblCapa = new Label();
    private final StackPane contenidoCapa = new StackPane();
    private final StackPane botonCapa = new StackPane(contenidoCapa);
    private Image imagenSatelite;      // la foto del mapa (se recorta para la miniatura "Satélite")
    private Image miniaturaPlano;      // /MAPAS/miniatura_plano.png
    private final Tooltip tooltipCapa = new Tooltip();

    // Easter egg
    private boolean eggActivo = false;             // true = el mapa extra está disponible en el botón de capa
    private Image imagenExtra;                     // el mapa extra cargado (null mientras el egg está apagado)
    private final StringBuilder tecleado = new StringBuilder();
    private long ultimaTecla = 0;

    private MapaView mapa;                          // null si la imagen no se pudo cargar

    @Override
    public void start(Stage stage) {
        BorderPane raiz = new BorderPane();
        raiz.setCenter(crearZonaMapa());     // primero el mapa...

        ScrollPane scroll = envolverEnScroll(crearPanelLateral());   // ...porque el panel lo controla
        raiz.setLeft(new HBox(scroll, crearDivisor(scroll)));

        Scene escena = new Scene(raiz, 1100, 700);
        instalarCodigoSecreto(escena);

        var css = getClass().getResource("css/estilos.css");
        if (css != null) {
            escena.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Rutas ITSUR - Dijkstra");
        stage.setMinWidth(1000);
        stage.setMinHeight(500);
        stage.setScene(escena);
        stage.show();

        // El foco empieza en el mapa (así el código secreto funciona desde el primer momento)
        Platform.runLater(() -> {
            if (mapa != null) mapa.requestFocus();
        });

        // PRUEBA (descomenta para ver la ruta real y la simulada a la vez):
        // mostrarRutaReal(List.of("L", "A", "G", "F", "I"), 842.6);
    }

    /**
     * AQUÍ AÑADES / EDITAS EDIFICIOS.
     * x e y son píxeles de la imagen ORIGINAL itsur.jpeg.
     * Opcional por edificio: .conColor("#hex")  .conIcono("/ICONOS/archivo.png")  .conImagen("/IMAGENES/foto.jpg")  .conTamano(ancho, alto)
     */
    private static final String colorRuta = "#a77bff";
    private List<Edificio> crearEdificios() {
        return List.of(
                new Edificio("A", "Biblioteca", "Edificio dedicado a la concentracion y relajacion tanto para alumnos como profesores, contando con un amplio catalogo de libros, cubiculos, salsas de reuiniones y computadoras para uso libre.", 390, 200)
                        .conImagen("/IMAGENES/biblioteca.jpg").conIcono("/ICONOS/biblioteca.png"),   // <- ejemplo: pon aquí tu foto
                new Edificio("B", "Vinculacion", "Oficinas donde se maneja la venta de libros de ingles, inscripciones a los niveles etc. .", 200, 300).conIcono("/ICONOS/Vincula.png"),
                new Edificio("C", "Estacionamiento", "Espacio libre con espacios limitados bajo un techo de lamina. El estacionamiento mayormente conformado para brindar espacio a motocicletas y un breve esapcio para los coches.", 800, 250).conIcono("/ICONOS/estacionamiento.png"),
                new Edificio("D", "Auditorio abandonado", " Obra negra de lo que deberia ser un auditorio imponente, que quedo en nada por razones que al parecer no les gusta mencionar XD.", 175, 710).conIcono("/ICONOS/obranegra.png"),
                new Edificio("E", "Edificio B", "Edficio donde facultades: Sistemas, ambiental, electronica toman sus clases, con la particularidad de que su inmueble esta descuidado y maltratado.", 710, 495).conIcono("/ICONOS/EdiB.png"),
                new Edificio("F", "Edificio A", "Edifcio donde las facultades de industrial y principalmente Gestion toman sus clases, tambien se concentran aqui la mayoria de las clases de ingles. Este siendo el primer edifcio construido en el ITSUR.", 475, 450).conIcono("/ICONOS/EdiA.png").conImagen("/IMAGENES/EA.jpeg"),
                new Edificio("G", "Edificio E", "Edificio donde las siguientes facultades: Ambiental, Mecanica, Industrial, Electronica toman sus clase, siendo este el ultimo edificio construido en el ITSUR, contando con su laboratorio para experimentos quimicos y su equipo respectiv, su taller de electricidad y mecanica etc.", 400, 680).conIcono("/ICONOS/EdiE.png"),
                new Edificio("H", "TICS", "Edificio que contiene el centro de computo, un sitio de uso libre de computo, espacios para los distintos laboratorios de clase de computo para las distintas facultades.", 640, 682).conIcono("/ICONOS/tics.png"),
                new Edificio("I", "Cafeteria", "Punto de Reuinion y quiza el lugar mas concurrido por los estudiantes dia con dia, venta de comida, debidas, botanas etc. zona de conversaciones y ocio.", 480, 795).conIcono("/ICONOS/cafeteria.png"),
                new Edificio("J", "Palapas", "Salones que conforman las famosas palapas, sitio para los alumnos integrantes de la rondalla y aficionados al deporte con el gimnasio del ITSUR", 370, 350).conIcono("/ICONOS/palapas.png"),
                new Edificio("K", "Entrada principal", "Entrada al ITSUR, la cual controla la entrada y salida de miles de alumnos cada dia para ir asus clases o regresar a sus hogares.", 520, 150).conIcono("/ICONOS/entrada.png"),
                new Edificio("L", "Cabaña", "Cabaña de la facultad de gastronomia, donde se realizan sus practicas de cocina tradicional y diversas tecnicas locales.", 130, 500).conIcono("/ICONOS/caba.png"),
                new Edificio("M", "Chancha de Futbol", "Cancha de Futbol, utilizada por los alumnos y maestros en eventos recreativos. Tambien es la zona de practica para los alumnos del club y seleccion de Tocho Bandera.", 975, 650).conIcono("/ICONOS/canchas.png"),
                new Edificio("N", "Direccion", "Oficinas de los directivos del plantel, donde se discuten y se toman las decisiones que se lleavran acabo.", 200, 400).conIcono("/ICONOS/direccion.png"),
                new Edificio("O", "Auditorio", "Sala Audiovisual donde se presentan coferencias importantes para los alumnos y maestros del plantel. Punto de reuinion para premios y juntas", 110, 350).conIcono("/ICONOS/auditorio.png"),
                new Edificio("P", "Polis", "Entrada del estacionamiento donde operan los polis que monitorean el flujo de motos y autos que ingresan al ITSUR", 750, 150).conIcono("/ICONOS/polis.png"),
                new Edificio("Q", "Basket", "Canchas para jugar basketball o Volley. Sitio donde los alumnos del club de BasketBall practican en su respectivo horario.", 240, 790).conIcono("/ICONOS/basket.png"),
                //RUTAS
                ruta(new Edificio("R", "ruta 1", "camino que conectan todo el ITSUR", 542, 632)),
                ruta(new Edificio("S", "ruta 2", "camino que conectan todo el ITSUR", 562, 512)),
                ruta(new Edificio("T", "ruta 3", "camino que conectan todo el ITSUR", 490, 320)),
                ruta(new Edificio("U", "ruta 4", "camino que conectan todo el ITSUR", 390, 810)),
                ruta(new Edificio("V", "ruta 5", "camino que conectan todo el ITSUR", 650, 410)),
                ruta(new Edificio("W", "ruta 6", "camino que conectan todo el ITSUR", 750, 340)),

                ruta(new Edificio("X", "ruta 7", "camino que conectan todo el ITSUR", 300, 590)),
                ruta(new Edificio("Y", "ruta 8", "camino que conectan todo el ITSUR", 750, 540)),
                ruta(new Edificio("Z", "ruta 9", "camino que conectan todo el ITSUR", 458, 565)),
                ruta(new Edificio("LL", "ruta 10", "camino que conectan todo el ITSUR", 679, 595))

        );
    }
    /** Marca un nodo como punto de ruta (solo cambia el color). */
    private static Edificio ruta(Edificio e) {
        return e.conColor(colorRuta).conIcono("/ICONOS/ruta.png");
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
        seleccionado = e;
        actualizarCoordenadas();

        if (e == null) {
            ocultar(tarjetaInfo);
            return;
        }

        lblNombre.setText(e.nombre());
        lblDescripcion.setText(e.descripcion());
        mostrarImagen(e.imagen());

        boolean aparece = !tarjetaInfo.isVisible();
        mostrar(tarjetaInfo);
        if (aparece) {
            FadeTransition fade = new FadeTransition(Duration.millis(180), tarjetaInfo);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
    }

    /** Muestra la foto del edificio en la tarjeta; si no tiene (o no se encuentra), oculta el marco. */
    private void mostrarImagen(String ruta) {
        Image img = ruta == null ? null : cacheImagenes.computeIfAbsent(ruta, r -> {
            URL url = getClass().getResource(r);
            if (url == null) {
                System.err.println("No se encontró la imagen: " + r);
                return null;
            }
            Image i = new Image(url.toExternalForm(), 700, 0, true, true);   // se reduce para ahorrar memoria
            return i.isError() ? null : i;
        });

        if (img == null) {
            imgEdificio.setImage(null);
            ocultar(marcoImagen);
            return;
        }
        imgEdificio.setImage(img);
        mostrar(marcoImagen);
    }

    /** Muestra "x: ...  y: ..." solo si la bandera está activa, el mapa CSS está encendido y hay un nodo seleccionado. */
    private void actualizarCoordenadas() {
        boolean visible = MOSTRAR_COORDENADAS && modo == MapaView.ModoMapa.PLANO && seleccionado != null;
        if (visible) {
            lblCoordenadas.setText("x: " + formatearCoordenada(seleccionado.x())
                    + "    y: " + formatearCoordenada(seleccionado.y()));
            mostrar(lblCoordenadas);
        } else {
            ocultar(lblCoordenadas);
        }
    }

    private static String formatearCoordenada(double v) {
        return v == Math.rint(v)
                ? String.format(Locale.US, "%.0f", v)
                : String.format(Locale.US, "%.1f", v);
    }

    // ===================================================================== origen / destino

    /** Dos botones: "Origen: ..." y "Destino: ...". Guardan el nodo que esté seleccionado en el mapa. */
    private Node crearBotonesOrigenDestino() {
        btnOrigen.getStyleClass().addAll("boton-ruta", "boton-origen");
        btnDestino.getStyleClass().addAll("boton-ruta", "boton-destino");
        for (Button b : List.of(btnOrigen, btnDestino)) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setPrefWidth(10);                  // con hgrow, los dos miden lo mismo
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        btnOrigen.setOnAction(ev -> asignarSeleccionado(true));
        btnDestino.setOnAction(ev -> asignarSeleccionado(false));
        actualizarBotonesRuta();
        return new HBox(8, btnOrigen, btnDestino);
    }

    /**
     * Botón Origen (true) o Destino (false):
     *  - Si ya tiene un nodo y se vuelve a pulsar (sin nodo seleccionado, o con ese mismo nodo), vuelve a null.
     *  - Si hay otro nodo seleccionado, lo reemplaza.
     *  - Si está en null y no hay nodo seleccionado, el botón se sacude.
     */
    private void asignarSeleccionado(boolean esOrigen) {
        Edificio actual = esOrigen ? origen : destino;

        if (actual != null && (seleccionado == null || seleccionado == actual)) {
            if (esOrigen) origen = null; else destino = null;      // segundo clic: se limpia
        } else if (seleccionado == null) {
            sacudir(esOrigen ? btnOrigen : btnDestino);
            return;
        } else if (esOrigen) {
            origen = seleccionado;
            if (destino == origen) destino = null;                 // el mismo nodo no puede ser origen y destino
        } else {
            destino = seleccionado;
            if (origen == destino) origen = null;
        }
        actualizarBotonesRuta();
        alCambiarOrigenDestino();
    }

    private void actualizarBotonesRuta() {
        ponerTexto(btnOrigen, "Origen", origen);
        ponerTexto(btnDestino, "Destino", destino);
        if (mapa != null) mapa.marcarOrigenDestino(origen, destino);   // brillo verde / rojo en el mapa
    }

    private static void ponerTexto(Button b, String etiqueta, Edificio e) {
        b.setText(etiqueta + ": " + (e == null ? "null" : e.id()));
        b.getStyleClass().remove("asignado");
        if (e != null) b.getStyleClass().add("asignado");
        b.setTooltip(e == null ? null : new Tooltip(e.nombre()));
    }

    /** Se llama cada vez que cambia el origen o el destino. Aquí conectas tu Dijkstra. */
    private void alCambiarOrigenDestino() {
        if (origen != null && destino != null) {
            // Ejemplo:
            // List<String> camino = tuDijkstra(origen.id(), destino.id());
            // mostrarRutaReal(camino, metros);
        }
    }

    /** Pequeño "no" visual: el botón se mueve de lado a lado. */
    private static void sacudir(Node n) {
        new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(n.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(60),  new KeyValue(n.translateXProperty(), -5)),
                new KeyFrame(Duration.millis(120), new KeyValue(n.translateXProperty(), 5)),
                new KeyFrame(Duration.millis(180), new KeyValue(n.translateXProperty(), -3)),
                new KeyFrame(Duration.millis(240), new KeyValue(n.translateXProperty(), 0))
        ).play();
    }

    private VBox crearPanelLateral() {
        // --- Ruta real ---
        Label titulo = new Label("Ruta más corta");
        titulo.getStyleClass().add("titulo");
        lblRuta.setWrapText(true);
        lblRuta.getStyleClass().add("texto-real");
        lblDistancia.getStyleClass().add("distancia-real");
        ocultar(lblDistancia);

        VBox tarjetaReal = new VBox(8, titulo, crearBotonesOrigenDestino(), lblRuta, lblDistancia);
        tarjetaReal.getStyleClass().addAll("tarjeta", "tarjeta-real");
        tarjetaReal.setPadding(new Insets(12));

        // --- Información del edificio (empieza oculta) ---
        lblNombre.getStyleClass().add("nombre-edificio");
        lblDescripcion.setWrapText(true);
        lblCoordenadas.getStyleClass().add("coordenadas");
        ocultar(lblCoordenadas);                 // solo aparece en modo mapa CSS (ver MOSTRAR_COORDENADAS)
        // Foto con marco: se ajusta al ancho de la tarjeta (con alto máximo) y queda centrada
        imgEdificio.setPreserveRatio(true);
        imgEdificio.setSmooth(true);
        imgEdificio.fitWidthProperty().bind(tarjetaInfo.widthProperty().subtract(42));   // 24 padding tarjeta + 18 del marco
        imgEdificio.setFitHeight(ALTO_MAX_IMAGEN);

        brilloFoto.getStyleClass().add("brillo-foto");
        brilloFoto.setMouseTransparent(true);
        brilloFoto.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);   // que cubra toda la foto
        marcoFoto.getStyleClass().add("marco-foto");
        marco.getStyleClass().add("marco-imagen");
        marco.setMaxWidth(Region.USE_PREF_SIZE);
        marcoImagen.setAlignment(Pos.CENTER);
        ocultar(marcoImagen);

        tarjetaInfo.getChildren().addAll(marcoImagen, lblNombre, lblCoordenadas, lblDescripcion);
        tarjetaInfo.getStyleClass().add("tarjeta");
        tarjetaInfo.setPadding(new Insets(12));
        ocultar(tarjetaInfo);

        // Espacio flexible: empuja el contenido hacia arriba
        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);

        // Título del panel
        Label tituloPanel = new Label("User View");
        tituloPanel.getStyleClass().add("titulo-panel");
        tituloPanel.setMaxWidth(Double.MAX_VALUE);     // que la línea azul de abajo cubra todo el ancho

        VBox panel = new VBox(12, tituloPanel, tarjetaReal, crearSimuladorRuta(), new Separator(),
                tarjetaInfo, espacio);
        panel.setPadding(new Insets(14));
        panel.setMinWidth(ANCHO_MIN_PANEL);
        panel.getStyleClass().add("panel-lateral");
        return panel;
    }

    /**
     * Pone el panel dentro de un ScrollPane: barra vertical y horizontal que aparecen solo cuando
     * hacen falta (tarjetas altas, panel estrechado con el divisor...).
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
        caja.setPadding(new Insets(12));
        return caja;
    }

    // ===================================================================== botón de capa

    /** Botón cuadrado con miniatura, como el de capas de Google Maps. Un clic pasa al siguiente mapa. */
    private Node crearBotonCapa(Image satelite) {
        imagenSatelite = satelite;
        URL url = getClass().getResource("/MAPAS/miniatura_plano.png");
        if (url == null) {
            System.err.println("No se encontró /MAPAS/miniatura_plano.png (se usará un recorte del mapa)");
        } else {
            miniaturaPlano = new Image(url.toExternalForm());
        }

        miniaturaCapa.setFitWidth(TAM_MINIATURA);
        miniaturaCapa.setFitHeight(TAM_MINIATURA);
        miniaturaCapa.setSmooth(true);

        Region sombraTexto = new Region();                       // degradado oscuro para que el texto se lea
        sombraTexto.getStyleClass().add("boton-capa-sombra");
        sombraTexto.setMaxHeight(40);
        sombraTexto.setMouseTransparent(true);
        StackPane.setAlignment(sombraTexto, Pos.BOTTOM_CENTER);

        lblCapa.getStyleClass().add("boton-capa-texto");
        lblCapa.setMouseTransparent(true);
        StackPane.setAlignment(lblCapa, Pos.BOTTOM_CENTER);
        StackPane.setMargin(lblCapa, new Insets(0, 0, 7, 0));

        contenidoCapa.getChildren().addAll(miniaturaCapa, sombraTexto, lblCapa);
        contenidoCapa.setMinSize(TAM_MINIATURA, TAM_MINIATURA);
        contenidoCapa.setPrefSize(TAM_MINIATURA, TAM_MINIATURA);
        contenidoCapa.setMaxSize(TAM_MINIATURA, TAM_MINIATURA);
        Rectangle recorte = new Rectangle(TAM_MINIATURA, TAM_MINIATURA);   // esquinas redondeadas de la miniatura
        recorte.setArcWidth(22);
        recorte.setArcHeight(22);
        contenidoCapa.setClip(recorte);

        botonCapa.getStyleClass().add("boton-capa");
        botonCapa.setMaxSize(TAM_MINIATURA + 4, TAM_MINIATURA + 4);        // 4 = borde de 2 px por lado
        botonCapa.setOnMouseClicked(ev -> {
            if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                alternarCapa();
                ev.consume();
            }
        });

        tooltipCapa.setShowDelay(Duration.millis(300));
        Tooltip.install(botonCapa, tooltipCapa);
        actualizarBotonCapa();
        return botonCapa;
    }

    /**
     * Qué mapa viene después de este:
     *  Satélite -> Plano -> (Mapa extra, solo si el código secreto está activo) -> Satélite.
     */
    private MapaView.ModoMapa siguienteModo() {
        switch (modo) {
            case SATELITE:
                return MapaView.ModoMapa.PLANO;
            case PLANO:
                return eggActivo ? MapaView.ModoMapa.EXTRA : MapaView.ModoMapa.SATELITE;
            default:   // EXTRA
                return MapaView.ModoMapa.SATELITE;
        }
    }

    /** Pasa al siguiente mapa de la lista. */
    private void alternarCapa() {
        modo = siguienteModo();
        if (mapa != null) mapa.setModoMapa(modo);
        actualizarCoordenadas();
        actualizarBotonCapa();

        FadeTransition fade = new FadeTransition(Duration.millis(220), contenidoCapa);
        fade.setFromValue(0.35);
        fade.setToValue(1);
        fade.play();
    }

    /** El botón siempre muestra el mapa AL QUE se cambiaría (igual que Google Maps). */
    private void actualizarBotonCapa() {
        switch (siguienteModo()) {
            case SATELITE:
                // se ve el plano (o el mapa extra) -> el botón ofrece volver al satélite
                miniaturaCapa.setImage(imagenSatelite);
                miniaturaCapa.setViewport(new Rectangle2D(
                        imagenSatelite.getWidth() * 0.20, imagenSatelite.getHeight() * 0.15,
                        Math.min(imagenSatelite.getWidth(), imagenSatelite.getHeight()) * 0.75,
                        Math.min(imagenSatelite.getWidth(), imagenSatelite.getHeight()) * 0.75));
                lblCapa.setText("Satélite");
                tooltipCapa.setText("Cambiar a imagen satelital");
                break;

            case PLANO:
                // se ve el satélite -> el botón ofrece el plano
                if (miniaturaPlano != null) {
                    miniaturaCapa.setImage(miniaturaPlano);
                    miniaturaCapa.setViewport(null);
                } else {
                    miniaturaCapa.setImage(imagenSatelite);   // respaldo si falta la miniatura del plano
                    miniaturaCapa.setViewport(new Rectangle2D(0, 0, imagenSatelite.getHeight(), imagenSatelite.getHeight()));
                }
                lblCapa.setText("Plano");
                tooltipCapa.setText("Cambiar al plano 3D");
                break;

            default:   // EXTRA: miniatura cuadrada tomada del centro del mapa secreto
                double lado = Math.min(imagenExtra.getWidth(), imagenExtra.getHeight());
                miniaturaCapa.setImage(imagenExtra);
                miniaturaCapa.setViewport(new Rectangle2D(
                        (imagenExtra.getWidth() - lado) / 2, (imagenExtra.getHeight() - lado) / 2, lado, lado));
                lblCapa.setText(NOMBRE_MAPA_EXTRA);
                tooltipCapa.setText("Cambiar al mapa secreto");
                break;
        }
    }

    // ===================================================================== easter egg

    /**
     * Escucha el teclado en toda la ventana. Si se escribe el código seguido (con menos de 2.5 s
     * entre teclas) se activa o desactiva el mapa extra. No cuenta lo que se escribe dentro de
     * un campo de texto; haz clic en el mapa antes de escribirlo.
     */
    private void instalarCodigoSecreto(Scene escena) {
        escena.addEventFilter(KeyEvent.KEY_TYPED, ev -> {
            if (escena.getFocusOwner() instanceof TextInputControl) {
                tecleado.setLength(0);
                return;
            }

            long ahora = System.currentTimeMillis();
            if (ahora - ultimaTecla > 2500) tecleado.setLength(0);   // si te tardas, se reinicia
            ultimaTecla = ahora;

            tecleado.append(ev.getCharacter());
            int n = CODIGO_EASTER_EGG.length();
            if (tecleado.length() > n) tecleado.delete(0, tecleado.length() - n);

            if (tecleado.toString().equals(CODIGO_EASTER_EGG)) {
                tecleado.setLength(0);
                alternarMapaExtra();
            }
        });
    }

    /** Primera vez: añade el mapa extra a la rotación del botón de capa. Segunda vez: lo quita. */
    private void alternarMapaExtra() {
        if (!eggActivo) {
            Image img = cargarMapaExtra();
            if (img == null) return;               // ya se avisó en la consola
            imagenExtra = img;
            eggActivo = true;
            if (mapa != null) mapa.setMapaExtra(img);
        } else {
            eggActivo = false;
            if (modo == MapaView.ModoMapa.EXTRA) {   // si se estaba viendo, se vuelve a la foto del mapa
                modo = MapaView.ModoMapa.SATELITE;
                if (mapa != null) mapa.setModoMapa(modo);
                actualizarCoordenadas();
            }
            if (mapa != null) mapa.setMapaExtra(null);
            imagenExtra = null;
        }
        actualizarBotonCapa();
        destello(botonCapa);                       // el botón "late" para avisar de que el código funcionó
    }

    private Image cargarMapaExtra() {
        URL url = getClass().getResource(RUTA_MAPA_EXTRA);
        if (url == null) {
            System.err.println("No se encontró " + RUTA_MAPA_EXTRA
                    + " (ponlo en src/main/resources/MAPAS/; JavaFX no lee WebP, usa PNG o JPG)");
            return null;
        }
        Image img = new Image(url.toExternalForm());
        if (img.isError()) {
            System.err.println("No se pudo cargar " + RUTA_MAPA_EXTRA + ": " + img.getException());
            return null;
        }
        return img;
    }

    /** El nodo crece un poco y vuelve a su tamaño (aviso visual). */
    private static void destello(Node n) {
        new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(n.scaleXProperty(), 1), new KeyValue(n.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(140),
                        new KeyValue(n.scaleXProperty(), 1.18, Interpolator.EASE_OUT),
                        new KeyValue(n.scaleYProperty(), 1.18, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(380),
                        new KeyValue(n.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(n.scaleYProperty(), 1, Interpolator.EASE_BOTH))
        ).play();
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

        Node boton = crearBotonCapa(imagen);
        StackPane.setAlignment(boton, Pos.BOTTOM_LEFT);
        StackPane.setMargin(boton, new Insets(0, 0, 16, 16));
        zona.getChildren().add(boton);
        return zona;
    }
}