package com.example.dikjstra;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import javafx.scene.shape.Ellipse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.shape.Line;

/**
 * Muestra el mapa (imagen, versión CSS o mapa extra) y un marcador clicable sobre cada edificio.
 * Las posiciones se guardan en píxeles de la imagen original y se convierten a
 * pantalla en cada layout, así los marcadores siempre quedan sobre su edificio.
 *
 * El Consumer recibe el edificio seleccionado, o null cuando se deselecciona
 * (segundo clic en el mismo nodo o clic fuera de los nodos).
 */
public class MapaView extends Pane {

    private static final double RADIO = 12;
    private static final double TAM_ICONO = 16;

    // Zoom: 1 = mapa completo (tamaño base, no se puede alejar más); hasta MAX_ZOOM veces más cerca
    private static final double MIN_ZOOM = 1.0;
    private static final double MAX_ZOOM = 5.0;
    private static final double SENSIBILIDAD_ZOOM = 0.002;

    // Tamaño de los nodos al hacer zoom: tamaño = zoom ^ EXPONENTE (0 = fijo, 1 = crece igual que el mapa)
    private static final double EXPONENTE_ZOOM_NODOS = 0.75;
    private static final double MAX_ESCALA_NODO = 3.0;        // tope: los nodos nunca crecen más de 3 veces   // sube este valor para que el zoom sea más rápido
    private static final Color COLOR_ICONO = Color.web("#f2f2f2");   // color de los iconos SVG

    private final ImageView imagen = new ImageView();
    private final Pane fondoCss = new Pane();
    private final Scale escalaFondo = new Scale(1, 1, 0, 0);
    private final ImageView imagenExtra = new ImageView();     // mapa secreto (easter egg), vacío hasta que se activa

    private final double anchoImg;
    private final double altoImg;
    private final List<Edificio> edificios;
    private final Map<Edificio, Group> marcadores = new LinkedHashMap<>();
    private final Map<String, Image> cacheIconos = new HashMap<>();
    private final Map<Edificio, Circle> auras = new HashMap<>();   // brillo verde (origen) / rojo (destino)

    // --- Rutas (la real y la simulada conviven, cada una en su capa) ---
    private final Map<String, Edificio> porId = new HashMap<>();
    private final Group capaRutas = new Group();
    // --- Conexiones del grafo (solo para depuración) ---capa
    private final Group capaGrafo = new Group();
    private final List<Edificio[]> aristasGrafo = new ArrayList<>();
    private final List<Line> lineasGrafo = new ArrayList<>();
    private final Map<TipoRuta, RutaDibujada> rutas = new EnumMap<>(TipoRuta.class);
    private final Consumer<Edificio> alSeleccionar;

    private Circle circuloSeleccionado;
    private boolean modoCss = false;
    private ModoMapa modoActual = ModoMapa.SATELITE;

    // Transformación imagen -> pantalla (se actualiza en layoutChildren)
    private double escala = 1, offsetX = 0, offsetY = 0;

    // Zoom y desplazamiento (pan) del usuario
    private double zoom = MIN_ZOOM;
    private double panX = 0, panY = 0;                 // desplazamiento respecto al mapa centrado (px de pantalla)
    private double arrastreX, arrastreY, panInicialX, panInicialY;
    private final Rectangle recorte = new Rectangle();
    private final Scale escalaNodos = new Scale(1, 1, 0, 0);   // compartida por todos los nodos

    public MapaView(Image img, List<Edificio> edificios, Consumer<Edificio> alSeleccionar) {
        this.anchoImg = img.getWidth();
        this.altoImg = img.getHeight();
        this.edificios = edificios;
        this.alSeleccionar = alSeleccionar;

        setMinSize(0, 0);

        // El mapa puede tener el foco del teclado (así funciona el código secreto aunque
        // antes hayas escrito en un campo de texto)
        setFocusTraversable(true);
        addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> requestFocus());

        imagen.setImage(img);
        imagen.setPreserveRatio(true);

        // Mapa extra: llena el mismo marco que la imagen (se recorta, no se deforma) y sin suavizar (pixel art)
        imagenExtra.setPreserveRatio(false);
        imagenExtra.setSmooth(false);
        imagenExtra.setMouseTransparent(true);
        imagenExtra.setVisible(false);

        fondoCss.getStyleClass().add("mapa-css");
        fondoCss.resize(anchoImg, altoImg);
        fondoCss.setClip(new Rectangle(anchoImg, altoImg));
        fondoCss.getTransforms().add(escalaFondo);
        fondoCss.setMouseTransparent(true);
        fondoCss.setVisible(false);

        capaRutas.setMouseTransparent(true);
        capaGrafo.setMouseTransparent(true);
        capaGrafo.setVisible(false);              // oculta hasta que se active

        getChildren().addAll(imagen, fondoCss, imagenExtra, capaGrafo, capaRutas);

        for (Edificio e : edificios) {
            Group g = crearMarcador(e);
            marcadores.put(e, g);
            porId.put(e.id(), e);
            getChildren().add(g);
        }

        // Lo que se sale del área del mapa (por el zoom) no se dibuja encima del panel
        setClip(recorte);

        // Clic izquierdo en zona vacía: quita la selección. Doble clic derecho: restablece el zoom.
        setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) {
                deseleccionar();
            } else if (ev.getButton() == MouseButton.SECONDARY && ev.getClickCount() == 2) {
                zoom = MIN_ZOOM;
                panX = 0;
                panY = 0;
                aplicarTransformacion();
            }
        });

        // Rueda del mouse: zoom hacia donde apunta el cursor
        setOnScroll(ev -> {
            if (ev.getDeltaY() == 0) return;
            double nuevoZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.exp(ev.getDeltaY() * SENSIBILIDAD_ZOOM)));
            if (nuevoZoom != zoom) {
                // punto de la imagen que está bajo el cursor: debe seguir bajo el cursor tras el zoom
                double ix = (ev.getX() - offsetX) / escala;
                double iy = (ev.getY() - offsetY) / escala;

                zoom = nuevoZoom;
                double nuevaEscala = Math.min(getWidth() / anchoImg, getHeight() / altoImg) * zoom;
                panX = (ev.getX() - ix * nuevaEscala) - (getWidth() - anchoImg * nuevaEscala) / 2;
                panY = (ev.getY() - iy * nuevaEscala) - (getHeight() - altoImg * nuevaEscala) / 2;
                aplicarTransformacion();
            }
            ev.consume();
        });

        // Clic derecho + arrastrar: mover el mapa
        setOnMousePressed(ev -> {
            if (ev.getButton() == MouseButton.SECONDARY) {
                arrastreX = ev.getX();
                arrastreY = ev.getY();
                panInicialX = panX;
                panInicialY = panY;
                setCursor(Cursor.CLOSED_HAND);
            }
        });
        setOnMouseDragged(ev -> {
            if (ev.getButton() == MouseButton.SECONDARY) {
                panX = panInicialX + (ev.getX() - arrastreX);
                panY = panInicialY + (ev.getY() - arrastreY);
                aplicarTransformacion();
            }
        });
        setOnMouseReleased(ev -> {
            if (ev.getButton() == MouseButton.SECONDARY) setCursor(Cursor.DEFAULT);
        });
    }

    // ------------------------------------------------------------------ API

    /** Hace brillar un poco en verde el nodo de origen y en rojo el de destino (null = ninguno). */
    public void marcarOrigenDestino(Edificio origen, Edificio destino) {
        auras.forEach((e, aura) -> {
            aura.getStyleClass().removeAll("aura-origen", "aura-destino");
            boolean esOrigen = e == origen;
            boolean esDestino = e == destino;
            aura.setVisible(esOrigen || esDestino);
            if (esOrigen) aura.getStyleClass().add("aura-origen");
            if (esDestino) aura.getStyleClass().add("aura-destino");
        });
    }

    /** Qué mapa se ve de fondo. EXTRA solo existe mientras el código secreto está activo. */
    public enum ModoMapa {
        SATELITE,   // la foto del mapa
        PLANO,      // el plano dibujado con CSS
        EXTRA       // el mapa secreto (easter egg)
    }

    /** Cambia el mapa de fondo. Los nodos y las rutas se quedan exactamente en su sitio. */
    public void setModoMapa(ModoMapa m) {
        if (m == ModoMapa.EXTRA && imagenExtra.getImage() == null) {
            m = ModoMapa.SATELITE;               // sin mapa extra cargado no hay nada que mostrar
        }
        modoActual = m;
        modoCss = (m == ModoMapa.PLANO);
        imagen.setVisible(m == ModoMapa.SATELITE);
        fondoCss.setVisible(m == ModoMapa.PLANO);
        imagenExtra.setVisible(m == ModoMapa.EXTRA);
        if (modoCss) redibujarFondoCss();
    }

    /** true = muestra el mapa hecho con CSS; false = muestra la imagen. (Se mantiene por compatibilidad.) */
    public void setModoCss(boolean activo) {
        setModoMapa(activo ? ModoMapa.PLANO : ModoMapa.SATELITE);
    }

    /**
     * Carga (o quita, con null) el mapa extra. Se recorta por el centro para llenar el mismo marco
     * que el mapa principal sin deformarse, así los nodos siempre quedan sobre el dibujo.
     * Si se quita mientras se está viendo, vuelve a la foto del mapa.
     */
    public void setMapaExtra(Image img) {
        imagenExtra.setImage(img);
        if (img == null) {
            imagenExtra.setViewport(null);
            if (modoActual == ModoMapa.EXTRA) setModoMapa(ModoMapa.SATELITE);
        } else {
            double marco = anchoImg / altoImg;
            double w = img.getWidth(), h = img.getHeight();
            double vw = w, vh = h;
            if (w / h > marco) vw = h * marco; else vh = w / marco;
            imagenExtra.setViewport(new Rectangle2D((w - vw) / 2, (h - vh) / 2, vw, vh));
        }
        requestLayout();
    }

    // ------------------------------------------------------------------ rutas

    /** Qué ruta se dibuja. Cada tipo tiene su propia capa y su propio color neón. */
    public enum TipoRuta {
        REAL("real"),       // la que devuelve el algoritmo (cian)
        SIMULADA("sim");    // la del simulador (magenta)

        private final String clave;

        TipoRuta(String clave) {
            this.clave = clave;
        }
    }

    // Tiempos de la animación (ms) ajustados para ser un poco más pausados y suaves.
    private static final double MS_POR_PIXEL = 2.4;   // velocidad del recorrido: sube = más lento
    private static final double MS_MIN = 900;         // duración mínima de todo el recorrido
    private static final double MS_MAX = 5000;        // duración máxima de todo el recorrido

    private static final String[] CLASES_RUTA_NODO = {
            "alcanzado-real", "origen-real", "destino-real",
            "alcanzado-sim", "origen-sim", "destino-sim"};

    /** Todo lo que necesita una ruta dibujada. */
    private static final class RutaDibujada {
        final TipoRuta tipo;
        final List<Edificio> nodos;
        final double[] acumulado;                 // px de imagen recorridos hasta cada nodo
        final Polyline sombra = new Polyline();     // sombra proyectada en el suelo
        final Polyline borde = new Polyline();      // contorno oscuro del tubo
        final Polyline cuerpo = new Polyline();     // color base
        final Polyline oscuro = new Polyline();     // franja de sombra (parte de abajo-derecha)
        final Polyline rebote = new Polyline();     // luz rebotada en el borde inferior
        final Polyline luz = new Polyline();        // franja clara (arriba-izquierda)
        final Polyline destello = new Polyline();   // reflejo blanco brillante
        final Polyline[] capas = {sombra, borde, cuerpo, oscuro, rebote, luz, destello};
        double cabeza;                            // distancia que lleva recorrida la punta
        int alcanzados;                           // cuántos nodos ya se iluminaron
        boolean completa;
        Transition animacion;

        RutaDibujada(TipoRuta tipo, List<Edificio> nodos) {
            this.tipo = tipo;
            this.nodos = nodos;
            this.acumulado = new double[nodos.size()];
            for (int i = 1; i < nodos.size(); i++) {
                acumulado[i] = acumulado[i - 1] + distancia(nodos.get(i - 1), nodos.get(i));
            }
        }
    }

    /** Dibuja (con animación) la ruta que pasa por estos ids, en orden. Ej: ["A","B","C"]. */
    public void dibujarRuta(TipoRuta tipo, List<String> ids) {
        dibujarRuta(tipo, ids, true);
    }

    /**
     * @param ids    ids de los edificios en orden (origen ... destino). Con menos de 2 se limpia ese tipo.
     * @param animar true = la línea se va trazando; false = aparece completa.
     * @throws IllegalArgumentException si algún id no existe
     */
    public void dibujarRuta(TipoRuta tipo, List<String> ids, boolean animar) {
        if (ids == null || ids.size() < 2) {
            limpiarRuta(tipo);
            return;
        }

        // Validar ANTES de tocar lo que ya hay dibujado
        List<Edificio> nodos = new ArrayList<>();
        for (String id : ids) {
            Edificio e = porId.get(id);
            if (e == null) {
                throw new IllegalArgumentException("No existe un edificio con id \"" + id + "\"");
            }
            nodos.add(e);
        }

        limpiarRuta(tipo);                       // solo reemplaza la del mismo tipo
        RutaDibujada r = new RutaDibujada(tipo, nodos);
        rutas.put(tipo, r);

        String k = "ruta-" + tipo.clave;
        r.sombra.getStyleClass().add("tubo-sombra");
        r.borde.getStyleClass().addAll("tubo-borde", k);
        r.cuerpo.getStyleClass().addAll("tubo-cuerpo", k);
        r.oscuro.getStyleClass().addAll("tubo-oscuro", k);
        r.rebote.getStyleClass().addAll("tubo-rebote", k);
        r.luz.getStyleClass().addAll("tubo-luz", k);
        r.destello.getStyleClass().add("tubo-destello");

        // La luz viene de ARRIBA-IZQUIERDA: la sombra y el rebote se corren al lado contrario.
        // Cada capa lleva su propio desenfoque para que todo se funda suavemente.
        r.sombra.setTranslateX(3.5);    r.sombra.setTranslateY(5);
        r.sombra.setEffect(new GaussianBlur(7));
        r.borde.setEffect(new GaussianBlur(0.8));
        r.oscuro.setTranslateX(1.3);    r.oscuro.setTranslateY(1.6);
        r.oscuro.setEffect(new GaussianBlur(2.5));
        r.rebote.setTranslateX(2.4);    r.rebote.setTranslateY(3);
        r.rebote.setEffect(new GaussianBlur(2));
        r.luz.setTranslateX(-1);        r.luz.setTranslateY(-1.4);
        r.luz.setEffect(new GaussianBlur(2));
        r.destello.setTranslateX(-1.6); r.destello.setTranslateY(-2.2);
        r.destello.setEffect(new GaussianBlur(1.4));

        for (Polyline p : r.capas) p.setMouseTransparent(true);

        // La real queda por encima de la simulada
        if (tipo == TipoRuta.SIMULADA) {
            capaRutas.getChildren().addAll(0, List.of(r.capas));
        } else {
            capaRutas.getChildren().addAll(r.capas);
        }

        if (animar) {
            r.cabeza = 0;
            r.alcanzados = 1;                    // el origen se enciende al arrancar
            refrescarEstilosNodos();
            pop(nodos.get(0));
            actualizarLinea(r);

            r.animacion = new AnimacionRuta(r);
            r.animacion.setOnFinished(ev -> {
                r.completa = true;
                actualizarLinea(r);
            });
            r.animacion.play();
        } else {
            r.completa = true;
            r.alcanzados = nodos.size();
            refrescarEstilosNodos();
            actualizarLinea(r);
        }
    }

    /** Quita la ruta de ese tipo (y apaga el brillo de sus nodos). */
    public void limpiarRuta(TipoRuta tipo) {
        RutaDibujada r = rutas.remove(tipo);
        if (r == null) return;
        if (r.animacion != null) r.animacion.stop();
        capaRutas.getChildren().removeAll(r.capas);
        refrescarEstilosNodos();
    }

    public void limpiarRutas() {
        for (TipoRuta t : TipoRuta.values()) limpiarRuta(t);
    }

    /**
     * Animación de la punta de la línea. Es UN solo movimiento continuo por todo el camino
     * (sin retrocesos ni pausas en los nodos): arranca y frena con una curva de seno muy suave.
     * Cada nodo se enciende y hace su "pop" justo cuando la punta pasa por él.
     */
    private final class AnimacionRuta extends Transition {
        private final RutaDibujada r;
        private final double largoTotal;

        AnimacionRuta(RutaDibujada r) {
            this.r = r;
            setInterpolator(Interpolator.LINEAR);   // la curva suave la calculamos a mano
            largoTotal = r.acumulado[r.acumulado.length - 1];
            setCycleDuration(Duration.millis(Math.max(MS_MIN, Math.min(MS_MAX, largoTotal * MS_POR_PIXEL))));
        }

        @Override
        protected void interpolate(double frac) {
            // Aceleración y frenado suaves en TODO el recorrido (seno: 0 -> 1 sin saltos de velocidad)
            double suave = 0.5 - 0.5 * Math.cos(Math.PI * frac);
            r.cabeza = largoTotal * suave;

            // Nodos que la punta ya alcanzó
            int alc = r.alcanzados;
            while (alc < r.nodos.size() && r.acumulado[alc] <= r.cabeza + 0.5) alc++;
            if (alc > r.alcanzados) {
                int desde = r.alcanzados;
                r.alcanzados = alc;
                refrescarEstilosNodos();
                for (int k = desde; k < alc; k++) pop(r.nodos.get(k));
            }

            actualizarLinea(r);
        }
    }

    /** Pequeño "golpe" de escala en el nodo cuando la línea llega (un poco más leve). */
    private void pop(Edificio e) {
        Group g = marcadores.get(e);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(g.scaleXProperty(), 1), new KeyValue(g.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(160),
                        new KeyValue(g.scaleXProperty(), 1.35, Interpolator.EASE_OUT),
                        new KeyValue(g.scaleYProperty(), 1.35, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(450),
                        new KeyValue(g.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(g.scaleYProperty(), 1, Interpolator.EASE_BOTH)));
        t.play();
    }

    /** Recalcula qué nodos brillan según TODAS las rutas activas (así no se pisan al borrar una). */
    private void refrescarEstilosNodos() {
        for (Edificio e : marcadores.keySet()) {
            circuloDe(e).getStyleClass().removeAll(CLASES_RUTA_NODO);
        }
        for (RutaDibujada r : rutas.values()) {
            String k = r.tipo.clave;
            for (int i = 0; i < r.alcanzados; i++) {
                List<String> clases = circuloDe(r.nodos.get(i)).getStyleClass();
                clases.add("alcanzado-" + k);
                if (i == 0) clases.add("origen-" + k);
                if (i == r.nodos.size() - 1) clases.add("destino-" + k);
            }
        }
    }

    /** Reconstruye la polilínea según hasta dónde llegó la punta y la escala actual. */
    private void actualizarLinea(RutaDibujada r) {
        List<Double> puntos = new ArrayList<>();
        Edificio primero = r.nodos.get(0);
        puntos.add(offsetX + primero.x() * escala);
        puntos.add(offsetY + primero.y() * escala);

        double acumulado = 0;
        for (int i = 1; i < r.nodos.size(); i++) {
            Edificio a = r.nodos.get(i - 1);
            Edificio b = r.nodos.get(i);
            double tramo = distancia(a, b);

            if (r.completa || acumulado + tramo <= r.cabeza) {
                puntos.add(offsetX + b.x() * escala);
                puntos.add(offsetY + b.y() * escala);
                acumulado += tramo;
            } else {
                double t = tramo == 0 ? 0 : (r.cabeza - acumulado) / tramo;
                puntos.add(offsetX + (a.x() + (b.x() - a.x()) * t) * escala);
                puntos.add(offsetY + (a.y() + (b.y() - a.y()) * t) * escala);
                break;
            }
        }

        for (Polyline p : r.capas) p.getPoints().setAll(puntos);
    }

    private static double distancia(Edificio a, Edificio b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }

    private Circle circuloDe(Edificio e) {
        return (Circle) marcadores.get(e).getChildren().get(0);
    }

    // ------------------------------------------------------------ conexiones (depuración)

    /** Dibuja una línea por cada conexión {idA, idB}. Ids que no existan se ignoran. */
    public void setConexiones(String[][] conexiones) {
        capaGrafo.getChildren().clear();
        aristasGrafo.clear();
        lineasGrafo.clear();

        for (String[] par : conexiones) {
            Edificio a = porId.get(par[0]);
            Edificio b = porId.get(par[1]);
            if (a == null || b == null) continue;

            Line l = new Line();
            l.setStroke(Color.web("#ffd54a"));
            l.setStrokeWidth(2);
            l.setOpacity(0.85);
            l.getStrokeDashArray().addAll(8.0, 5.0);   // punteada
            l.setMouseTransparent(true);

            aristasGrafo.add(new Edificio[]{a, b});
            lineasGrafo.add(l);
            capaGrafo.getChildren().add(l);
        }
        actualizarGrafo();
    }

    public void mostrarGrafo(boolean visible) {
        capaGrafo.setVisible(visible);
    }

    /** Recoloca cada línea según la escala, el zoom y el desplazamiento actuales. */
    private void actualizarGrafo() {
        for (int i = 0; i < lineasGrafo.size(); i++) {
            Edificio a = aristasGrafo.get(i)[0];
            Edificio b = aristasGrafo.get(i)[1];
            Line l = lineasGrafo.get(i);
            l.setStartX(offsetX + a.x() * escala);
            l.setStartY(offsetY + a.y() * escala);
            l.setEndX(offsetX + b.x() * escala);
            l.setEndY(offsetY + b.y() * escala);
        }
    }

    // ------------------------------------------------------------ marcadores

    private Group crearMarcador(Edificio e) {
        Circle circulo = new Circle(RADIO);
        circulo.getStyleClass().add("marcador");
        if (e.color() != null) {
            circulo.setStyle("-color-marcador: " + e.color() + ";");
        }

        // Capas de la esfera plástica (el círculo base DEBE ser el primer hijo)
        Circle rebote = new Circle(RADIO);                       // luz rebotada abajo
        rebote.getStyleClass().add("esfera-rebote");

        Ellipse brillo = new Ellipse(-RADIO * 0.34, -RADIO * 0.40, RADIO * 0.32, RADIO * 0.18);
        brillo.getStyleClass().add("esfera-brillo");             // halo suave alrededor del punto de luz
        brillo.setRotate(-35);

        Circle punto = new Circle(-RADIO * 0.36, -RADIO * 0.42, RADIO * 0.09);
        punto.getStyleClass().add("esfera-punto");               // punto de luz blanco

        rebote.setEffect(new GaussianBlur(3));
        brillo.setEffect(new GaussianBlur(2.5));
        punto.setEffect(new GaussianBlur(1.2));

        rebote.setMouseTransparent(true);
        brillo.setMouseTransparent(true);
        punto.setMouseTransparent(true);

        // Aura de origen/destino: anillo difuminado alrededor del nodo (oculto por defecto)
        Circle aura = new Circle(RADIO + 1);
        aura.getStyleClass().add("aura");
        aura.setEffect(new GaussianBlur(5));
        aura.setVisible(false);
        aura.setMouseTransparent(true);
        auras.put(e, aura);

        Group g = new Group(circulo, aura, rebote, brillo, punto);
        g.getTransforms().add(escalaNodos);   // los nodos crecen con el zoom

        Node icono = crearIcono(e.icono());
        if (icono != null) {
            g.getChildren().add(1, icono);   // encima de la esfera, debajo de los brillos
        }

        Tooltip.install(g, new Tooltip(e.nombre()));

        g.setOnMouseClicked(ev -> {
            if (ev.getButton() != MouseButton.PRIMARY) return;   // el clic derecho es para mover el mapa
            if (circulo == circuloSeleccionado) {
                deseleccionar();            // segundo clic en el mismo nodo
            } else {
                seleccionar(circulo);
                alSeleccionar.accept(e);
            }
            ev.consume();
        });

        return g;
    }

    private void seleccionar(Circle c) {
        if (circuloSeleccionado != null) {
            circuloSeleccionado.getStyleClass().remove("seleccionado");
        }
        circuloSeleccionado = c;
        c.getStyleClass().add("seleccionado");
    }

    /** Quita el resaltado y avisa con null a quien escucha (para ocultar la info). */
    private void deseleccionar() {
        if (circuloSeleccionado == null) return;
        circuloSeleccionado.getStyleClass().remove("seleccionado");
        circuloSeleccionado = null;
        alSeleccionar.accept(null);
    }

    /** Crea el icono de un nodo: acepta PNG/JPG y también SVG sencillos. */
    private Node crearIcono(String ruta) {
        if (ruta == null) return null;
        if (ruta.toLowerCase().endsWith(".svg")) return crearIconoSvg(ruta);

        Image img = cargarIcono(ruta);
        if (img == null) return null;
        ImageView iv = new ImageView(img);
        iv.setFitWidth(TAM_ICONO);
        iv.setFitHeight(TAM_ICONO);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setMouseTransparent(true);
        iv.setLayoutX(-TAM_ICONO / 2);
        iv.setLayoutY(-TAM_ICONO / 2);
        return iv;
    }

    /**
     * JavaFX no lee .svg con Image, así que se leen las etiquetas <path d="..."> del archivo
     * y se dibujan como formas (SVGPath), escaladas a TAM_ICONO y centradas en el nodo.
     * Sirve para iconos sencillos hechos solo con <path>. Si el SVG usa <circle>, <rect>,
     * transform o imágenes incrustadas, conviértelo a PNG.
     */
    private Node crearIconoSvg(String ruta) {
        try (var in = MapaView.class.getResourceAsStream(ruta)) {
            if (in == null) {
                System.err.println("No se encontró el icono: " + ruta);
                return null;
            }
            String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            // Tamaño original del dibujo (viewBox="x y ancho alto"); por defecto 24x24
            double vx = 0, vy = 0, vw = 24, vh = 24;
            Matcher mv = Pattern.compile("viewBox\\s*=\\s*\"([^\"]+)\"").matcher(svg);
            if (mv.find()) {
                String[] p = mv.group(1).trim().split("[\\s,]+");
                if (p.length == 4) {
                    vx = Double.parseDouble(p[0]);
                    vy = Double.parseDouble(p[1]);
                    vw = Double.parseDouble(p[2]);
                    vh = Double.parseDouble(p[3]);
                }
            }

            boolean contorno = svg.contains("fill=\"none\"") && svg.contains("stroke=");   // iconos de línea
            boolean parImpar = svg.contains("evenodd");

            Group forma = new Group();
            Matcher mp = Pattern.compile("<path[^>]*?\\sd\\s*=\\s*\"([^\"]+)\"").matcher(svg);
            while (mp.find()) {
                SVGPath sp = new SVGPath();
                sp.setContent(mp.group(1));
                if (parImpar) sp.setFillRule(FillRule.EVEN_ODD);
                if (contorno) {
                    sp.setFill(null);
                    sp.setStroke(COLOR_ICONO);
                    sp.setStrokeWidth(vw / 12);                 // grosor de línea proporcional
                    sp.setStrokeLineCap(StrokeLineCap.ROUND);
                    sp.setStrokeLineJoin(StrokeLineJoin.ROUND);
                } else {
                    sp.setFill(COLOR_ICONO);
                }
                forma.getChildren().add(sp);
            }
            if (forma.getChildren().isEmpty()) {
                System.err.println("El SVG no tiene <path> que se pueda dibujar: " + ruta);
                return null;
            }

            // Centro del dibujo en (0,0) y escala para que mida TAM_ICONO
            forma.setTranslateX(-(vx + vw / 2));
            forma.setTranslateY(-(vy + vh / 2));
            Group icono = new Group(forma);
            double k = TAM_ICONO / Math.max(vw, vh);
            icono.getTransforms().add(new Scale(k, k, 0, 0));
            icono.setMouseTransparent(true);
            return icono;
        } catch (Exception ex) {
            System.err.println("No se pudo leer el SVG " + ruta + ": " + ex);
            return null;
        }
    }

    private Image cargarIcono(String ruta) {
        if (ruta == null) return null;
        return cacheIconos.computeIfAbsent(ruta, r -> {
            var url = MapaView.class.getResource(r);
            if (url == null) {
                System.err.println("No se encontró el icono: " + r);
                return null;
            }
            return new Image(url.toExternalForm());
        });
    }

    private void redibujarFondoCss() {
        MapaCss.rellenar(fondoCss, anchoImg, altoImg, edificios);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void layoutChildren() {
        aplicarTransformacion();
    }

    /** Calcula escala y posición según tamaño de la ventana, zoom y desplazamiento, y recoloca todo. */
    private void aplicarTransformacion() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        recorte.setWidth(w);
        recorte.setHeight(h);

        escala = Math.min(w / anchoImg, h / altoImg) * zoom;   // zoom 1 = mapa completo (el máximo que se puede alejar)
        double dw = anchoImg * escala;
        double dh = altoImg * escala;

        // El mapa nunca deja ver el vacío: si es más grande que la zona, se limita cuánto se puede mover
        double maxPanX = Math.max(0, (dw - w) / 2);
        double maxPanY = Math.max(0, (dh - h) / 2);
        panX = Math.max(-maxPanX, Math.min(maxPanX, panX));
        panY = Math.max(-maxPanY, Math.min(maxPanY, panY));

        offsetX = (w - dw) / 2 + panX;
        offsetY = (h - dh) / 2 + panY;

        imagen.setFitWidth(dw);
        imagen.setFitHeight(dh);
        imagen.setLayoutX(offsetX);
        imagen.setLayoutY(offsetY);

        // El mapa extra ocupa exactamente el mismo rectángulo que la imagen
        imagenExtra.setFitWidth(dw);
        imagenExtra.setFitHeight(dh);
        imagenExtra.setLayoutX(offsetX);
        imagenExtra.setLayoutY(offsetY);

        // El mapa CSS se dibuja a tamaño de la imagen original y se escala igual que ella
        fondoCss.setLayoutX(offsetX);
        fondoCss.setLayoutY(offsetY);
        escalaFondo.setX(escala);
        escalaFondo.setY(escala);

        // Los nodos crecen con el zoom, pero menos que el mapa, para que no se vean ni diminutos ni enormes
        double k = Math.min(MAX_ESCALA_NODO, Math.pow(zoom, EXPONENTE_ZOOM_NODOS));
        escalaNodos.setX(k);
        escalaNodos.setY(k);

        marcadores.forEach((e, g) -> {
            g.setLayoutX(offsetX + e.x() * escala);
            g.setLayoutY(offsetY + e.y() * escala);
        });

        rutas.values().forEach(this::actualizarLinea);
        actualizarGrafo();
    }
}