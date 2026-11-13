package com.example.dikjstra;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Muestra el mapa (imagen o versión CSS) y un marcador clicable sobre cada edificio.
 * Las posiciones se guardan en píxeles de la imagen original y se convierten a
 * pantalla en cada layout, así los marcadores siempre quedan sobre su edificio.
 *
 * El Consumer recibe el edificio seleccionado, o null cuando se deselecciona
 * (segundo clic en el mismo nodo o clic fuera de los nodos).
 */
public class MapaView extends Pane {

    private static final double RADIO = 12;
    private static final double TAM_ICONO = 16;

    private final ImageView imagen = new ImageView();
    private final Pane fondoCss = new Pane();
    private final Scale escalaFondo = new Scale(1, 1, 0, 0);

    private final double anchoImg;
    private final double altoImg;
    private final List<Edificio> edificios;
    private final Map<Edificio, Group> marcadores = new LinkedHashMap<>();
    private final Map<String, Image> cacheIconos = new HashMap<>();

    // --- Rutas (la real y la simulada conviven, cada una en su capa) ---
    private final Map<String, Edificio> porId = new HashMap<>();
    private final Group capaRutas = new Group();
    private final Map<TipoRuta, RutaDibujada> rutas = new EnumMap<>(TipoRuta.class);
    private final Consumer<Edificio> alSeleccionar;

    private Circle circuloSeleccionado;
    private boolean modoCss = false;

    // Transformación imagen -> pantalla (se actualiza en layoutChildren)
    private double escala = 1, offsetX = 0, offsetY = 0;

    public MapaView(Image img, List<Edificio> edificios, Consumer<Edificio> alSeleccionar) {
        this.anchoImg = img.getWidth();
        this.altoImg = img.getHeight();
        this.edificios = edificios;
        this.alSeleccionar = alSeleccionar;

        setMinSize(0, 0);

        imagen.setImage(img);
        imagen.setPreserveRatio(true);

        fondoCss.getStyleClass().add("mapa-css");
        fondoCss.resize(anchoImg, altoImg);
        fondoCss.setClip(new Rectangle(anchoImg, altoImg));
        fondoCss.getTransforms().add(escalaFondo);
        fondoCss.setMouseTransparent(true);
        fondoCss.setVisible(false);

        capaRutas.setMouseTransparent(true);

        getChildren().addAll(imagen, fondoCss, capaRutas);

        for (Edificio e : edificios) {
            Group g = crearMarcador(e);
            marcadores.put(e, g);
            porId.put(e.id(), e);
            getChildren().add(g);
        }

        // Clic en zona vacía: quita la selección
        setOnMouseClicked(ev -> deseleccionar());
    }

    // ------------------------------------------------------------------ API

    /** true = muestra el mapa hecho con CSS; false = muestra la imagen. */
    public void setModoCss(boolean activo) {
        modoCss = activo;
        imagen.setVisible(!activo);
        fondoCss.setVisible(activo);
        if (activo) redibujarFondoCss();
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
    private static final double MS_WIND_UP = 175;     // "carga" antes de lanzarse a cada tramo
    private static final double MS_PAUSA_NODO = 125;  // pausa al llegar a un nodo

    private static final String[] CLASES_RUTA_NODO = {
            "alcanzado-real", "origen-real", "destino-real",
            "alcanzado-sim", "origen-sim", "destino-sim"};

    /** Todo lo que necesita una ruta dibujada. */
    private static final class RutaDibujada {
        final TipoRuta tipo;
        final List<Edificio> nodos;
        final double[] acumulado;                 // px de imagen recorridos hasta cada nodo
        final Polyline halo = new Polyline();     // resplandor ancho y difuminado
        final Polyline nucleo = new Polyline();   // línea fina y brillante
        double cabeza;                            // distancia que lleva recorrida la punta
        int alcanzados;                           // cuántos nodos ya se iluminaron
        boolean completa;
        Transition animacion;
        Timeline respiracion;                     // pulso suave del neón

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

        String clase = "ruta-" + tipo.clave;
        r.halo.getStyleClass().addAll("ruta-halo", clase);
        r.nucleo.getStyleClass().addAll("ruta-nucleo", clase);
        r.halo.setEffect(new GaussianBlur(12));
        r.halo.setMouseTransparent(true);
        r.nucleo.setMouseTransparent(true);

        // La real queda por encima de la simulada
        if (tipo == TipoRuta.SIMULADA) {
            capaRutas.getChildren().addAll(0, List.of(r.halo, r.nucleo));
        } else {
            capaRutas.getChildren().addAll(r.halo, r.nucleo);
        }

        // Neón "respirando"
        r.respiracion = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(r.halo.opacityProperty(), 0.55)),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(r.halo.opacityProperty(), 1.0, Interpolator.EASE_BOTH)));
        r.respiracion.setCycleCount(Timeline.INDEFINITE);
        r.respiracion.setAutoReverse(true);
        r.respiracion.play();

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
        if (r.respiracion != null) r.respiracion.stop();
        capaRutas.getChildren().removeAll(r.halo, r.nucleo);
        refrescarEstilosNodos();
    }

    public void limpiarRutas() {
        for (TipoRuta t : TipoRuta.values()) limpiarRuta(t);
    }

    /**
     * Animación de la punta de la línea. Combina tres efectos en cada tramo:
     *  1) WIND UP : la punta se echa un poco hacia atrás ("carga") antes de salir (suavizado).
     *  2) EASE OUT: sale rápido y va frenando suavemente al acercarse al nodo.
     *  3) SNAP    : al llegar, el nodo se enciende y hace un ligero "pop".
     */
    private final class AnimacionRuta extends Transition {
        private final RutaDibujada r;
        private final int tramos;
        private final double[] inicio;      // ms en que arranca cada tramo
        private final double[] viaje;       // ms que dura el viaje de cada tramo
        private final double[] largo;       // px de imagen de cada tramo
        private final double[] retroceso;   // px que retrocede el wind up
        private final double total;

        AnimacionRuta(RutaDibujada r) {
            this.r = r;
            setInterpolator(Interpolator.LINEAR);   // el easing lo calculamos a mano

            tramos = r.nodos.size() - 1;
            inicio = new double[tramos];
            viaje = new double[tramos];
            largo = new double[tramos];
            retroceso = new double[tramos];

            double t = 0;
            for (int i = 0; i < tramos; i++) {
                largo[i] = r.acumulado[i + 1] - r.acumulado[i];
                // Viaje un poco más lento
                viaje[i] = Math.max(460, Math.min(1250, 300 + largo[i] * 1.35));
                // Retroceso (wind-up) ligeramente reducido para que sea más sutil
                retroceso[i] = (i == 0) ? 0 : Math.min(largo[i - 1] * 0.25, 0.08 * largo[i] + 4);
                inicio[i] = t;
                t += MS_WIND_UP + viaje[i] + (i < tramos - 1 ? MS_PAUSA_NODO : 0);
            }
            total = t;
            setCycleDuration(Duration.millis(total));
        }

        @Override
        protected void interpolate(double frac) {
            double t = frac * total;

            // tramo activo
            int i = tramos - 1;
            for (int k = 0; k < tramos - 1; k++) {
                if (t < inicio[k + 1]) {
                    i = k;
                    break;
                }
            }

            double dt = t - inicio[i];
            double base = r.acumulado[i];
            double d;
            if (dt < MS_WIND_UP) {
                // WIND UP: retrocede un poco
                d = base - retroceso[i] * easeInOut(dt / MS_WIND_UP);
            } else if (dt < MS_WIND_UP + viaje[i]) {
                // EASE OUT: arranca rápido, frena al llegar
                double v = (dt - MS_WIND_UP) / viaje[i];
                d = (base - retroceso[i]) + (largo[i] + retroceso[i]) * easeOut(v);
            } else {
                d = base + largo[i];
            }
            r.cabeza = Math.max(0, d);

            // SNAP: los nodos alcanzados se encienden de golpe
            int alc = 1;
            for (int k = 0; k < tramos; k++) {
                if (t >= inicio[k] + MS_WIND_UP + viaje[k]) alc = k + 2;
            }
            if (alc > r.alcanzados) {
                int desde = r.alcanzados;
                r.alcanzados = alc;
                refrescarEstilosNodos();
                for (int j = desde; j < alc; j++) pop(r.nodos.get(j));
            }

            actualizarLinea(r);
        }
    }

    // Curva cuadrática en lugar de cúbica para una desaceleración un poco más suave
    private static double easeOut(double v) {
        double u = 1 - v;
        return 1 - u * u;
    }

    private static double easeInOut(double x) {
        return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
    }

    /** Pequeño "golpe" de escala en el nodo cuando la línea llega (un poco más leve). */
    private void pop(Edificio e) {
        Group g = marcadores.get(e);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(g.scaleXProperty(), 1), new KeyValue(g.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(120),
                        new KeyValue(g.scaleXProperty(), 1.5, Interpolator.EASE_OUT),
                        new KeyValue(g.scaleYProperty(), 1.5, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(300),
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

        r.halo.getPoints().setAll(puntos);
        r.nucleo.getPoints().setAll(puntos);
    }

    private static double distancia(Edificio a, Edificio b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }

    private Circle circuloDe(Edificio e) {
        return (Circle) marcadores.get(e).getChildren().get(0);
    }

    // ------------------------------------------------------------ marcadores

    private Group crearMarcador(Edificio e) {
        Circle circulo = new Circle(RADIO);
        circulo.getStyleClass().add("marcador");
        if (e.color() != null) {
            circulo.setStyle("-color-marcador: " + e.color() + ";");
        }

        Group g = new Group(circulo);

        Image icono = cargarIcono(e.icono());
        if (icono != null) {
            ImageView iv = new ImageView(icono);
            iv.setFitWidth(TAM_ICONO);
            iv.setFitHeight(TAM_ICONO);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setMouseTransparent(true);
            iv.setLayoutX(-TAM_ICONO / 2);
            iv.setLayoutY(-TAM_ICONO / 2);
            g.getChildren().add(iv);
        }

        Tooltip.install(g, new Tooltip(e.nombre()));

        g.setOnMouseClicked(ev -> {
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
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        escala = Math.min(w / anchoImg, h / altoImg);
        double dw = anchoImg * escala;
        double dh = altoImg * escala;
        offsetX = (w - dw) / 2;
        offsetY = (h - dh) / 2;

        imagen.setFitWidth(dw);
        imagen.setFitHeight(dh);
        imagen.setLayoutX(offsetX);
        imagen.setLayoutY(offsetY);

        // El mapa CSS se dibuja a tamaño de la imagen original y se escala igual que ella
        fondoCss.setLayoutX(offsetX);
        fondoCss.setLayoutY(offsetY);
        escalaFondo.setX(escala);
        escalaFondo.setY(escala);

        marcadores.forEach((e, g) -> {
            g.setLayoutX(offsetX + e.x() * escala);
            g.setLayoutY(offsetY + e.y() * escala);
        });

        rutas.values().forEach(this::actualizarLinea);
    }
}