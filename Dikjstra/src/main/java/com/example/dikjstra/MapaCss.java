package com.example.dikjstra;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.geometry.Pos;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Mapa "hecho con CSS" en estilo plano/blueprint oscuro con edificios en 3D (extrusión).
 *
 * Las coordenadas están medidas sobre la imagen original (1172 x 896 px) y se escalan
 * automáticamente a (ancho, alto), así los nodos de Dijkstra siguen coincidiendo.
 *
 * Todos los colores/grosores/efectos están en estilos.css:
 *   .rejilla  .calle  .sendero  .techo  .cara-luz  .cara-sombra  .estacionamiento ...
 */
public final class MapaCss {

    private static final double W0 = 1172, H0 = 896;

    /** Separación (px) entre el centro del nodo y la tarjeta con el nombre. Súbela si aún tapa el nodo. */
    private static final double DISTANCIA_ETIQUETA = 20;

    private MapaCss() {}

    /** Edificio extruido: huella (px de la imagen original) y altura. */
    private static final class Pieza {
        final double[] p;
        final double h;
        Pieza(double h, double... p) { this.p = p; this.h = h; }
        double cy() {
            double s = 0;
            for (int i = 1; i < p.length; i += 2) s += p[i];
            return s / (p.length / 2.0);
        }
    }

    public static void rellenar(Pane destino, double ancho, double alto, List<Edificio> edificios) {
        destino.getChildren().clear();
        double sx = ancho / W0, sy = alto / H0, s = Math.min(sx, sy);

        // 1) REJILLA de fondo
        double paso = 49 * s;
        for (double x = 0; x <= ancho; x += paso) destino.getChildren().add(linea("rejilla", x, 0, x, alto));
        for (double y = 0; y <= alto; y += paso)  destino.getChildren().add(linea("rejilla", 0, y, ancho, y));

        // 2) VEGETACIÓN (puntitos, como en el plano)
        Random rnd = new Random(7);
        double[][] zonas = {
                {540, 150, 300, 170}, {560, 330, 140, 100}, {260, 330, 90, 120},
                {20, 440, 200, 150}, {640, 420, 80, 60}, {780, 560, 60, 180}, {30, 700, 120, 150}
        };
        for (double[] z : zonas) {
            for (int i = 0; i < 28; i++) {
                Circle c = new Circle((z[0] + rnd.nextDouble() * z[2]) * sx,
                        (z[1] + rnd.nextDouble() * z[3]) * sy,
                        (1.2 + rnd.nextDouble() * 2.2) * s);
                c.getStyleClass().add("vegetacion");
                destino.getChildren().add(c);
            }
        }

        // 3) CALLES (anchas) y SENDEROS (angostos). Puntos suavizados con curvas.
        calle(destino, sx, sy, "calle", 500, 0, 520, 40, 600, 62, 780, 90, 980, 125, 1172, 160);       // avenida superior
        calle(destino, sx, sy, "calle", 905, 365, 872, 520, 822, 650, 745, 750, 620, 818, 480, 850, 400, 832); // vialidad derecha/inferior
        calle(destino, sx, sy, "calle", 240, 445, 255, 520, 200, 590, 90, 650, 0, 700);               // vialidad izquierda
        calle(destino, sx, sy, "sendero", 520, 135, 512, 230, 496, 330, 470, 385);                      // andador vertical
        calle(destino, sx, sy, "sendero", 255, 292, 380, 296, 520, 300, 735, 326);                      // andador horizontal
        calle(destino, sx, sy, "sendero", 530, 480, 600, 540, 680, 610, 745, 655);                      // andador diagonal
        calle(destino, sx, sy, "sendero", 575, 520, 562, 590, 572, 650);
        calle(destino, sx, sy, "sendero", 260, 650, 320, 740, 400, 832);

        // 4) PLATAFORMAS PLANAS: estacionamiento y cancha
        double[] A = pt(775, 135, sx, sy), B = pt(1155, 215, sx, sy), C = pt(1150, 385, sx, sy), D = pt(735, 325, sx, sy);
        destino.getChildren().add(poligono("estacionamiento", A, B, C, D));
        for (double t = 0.30; t < 0.99; t += 0.045)
            destino.getChildren().add(lineaP("linea-estac-fina", lerp(A, B, t), lerp(D, C, t)));
        for (double t : new double[]{0.12, 0.30, 0.50, 0.68, 0.86})
            destino.getChildren().add(lineaP("linea-estac", lerp(A, B, t), lerp(D, C, t)));

        double[] P0 = pt(945, 450, sx, sy), P1 = pt(1120, 505, sx, sy), P2 = pt(1010, 845, sx, sy), P3 = pt(840, 795, sx, sy);
        destino.getChildren().add(poligono("cancha", P0, P1, P2, P3));
        destino.getChildren().add(poligono("cancha-linea", lerp(P0, centro(P0, P1, P2, P3), 0.08), lerp(P1, centro(P0, P1, P2, P3), 0.08),
                lerp(P2, centro(P0, P1, P2, P3), 0.08), lerp(P3, centro(P0, P1, P2, P3), 0.08)));
        destino.getChildren().add(lineaP("cancha-linea", lerp(P0, P3, 0.33), lerp(P1, P2, 0.33)));

        // 5) EDIFICIOS EXTRUIDOS 3D (huellas calcadas de itsur_mark)
        List<Pieza> piezas = new ArrayList<>();
        piezas.add(new Pieza(12, 472, 78, 568, 84, 566, 122, 520, 124, 500, 124, 474, 114));       // techumbre superior
        piezas.add(new Pieza(28, 345, 115, 445, 128, 432, 278, 326, 268));                        // edificio superior central
        piezas.add(new Pieza(34, 95, 275, 255, 292, 248, 440, 82, 420));                          // edificio izquierdo grande
        piezas.add(new Pieza(10, 112, 492, 148, 492, 148, 516, 112, 516));                        // caseta
        piezas.add(new Pieza(18, 360, 298, 420, 320, 372, 385, 330, 355));                        // edificio blanco pequeño
        piezas.add(new Pieza(30, 490, 363, 555, 418, 463, 545, 385, 490));                        // edificio central
        piezas.add(new Pieza(28, 710, 402, 790, 455, 700, 585, 625, 525));                        // edificio derecha-centro
        piezas.add(new Pieza(24, 180, 625, 255, 700, 175, 795, 90, 715));                         // edificio inferior izquierdo
        piezas.add(new Pieza(32, 400, 590, 490, 655, 385, 775, 305, 700));                        // edificio inferior central
        piezas.add(new Pieza(30, 660, 598, 735, 660, 630, 790, 550, 715));                       // edificio inferior derecho
        piezas.add(new Pieza(14, 470, 752, 535, 762, 548, 790, 525, 812, 480, 830, 425, 832, 430, 805, 450, 775)); // auditorio / anfiteatro
        piezas.sort(Comparator.comparingDouble(Pieza::cy));   // los de arriba se dibujan primero

        for (Pieza pz : piezas) {
            double[] q = new double[pz.p.length];
            for (int i = 0; i < q.length; i += 2) { q[i] = pz.p[i] * sx; q[i + 1] = pz.p[i + 1] * sy; }
            destino.getChildren().add(extruir(q, pz.h * s));
        }

        // 6) ETIQUETAS (tarjetas): siempre DEBAJO del nodo, centradas horizontalmente
        for (Edificio e : edificios) {
            Label l = new Label(e.nombre());
            l.getStyleClass().add("etiqueta-edificio");
            l.setAlignment(Pos.CENTER);
            l.setLayoutY(e.y() + DISTANCIA_ETIQUETA);
            l.setLayoutX(e.x() - 40);
            // al conocer su ancho real, se vuelve a centrar bajo el nodo
            l.layoutBoundsProperty().addListener((obs, a, b) -> l.setLayoutX(e.x() - b.getWidth() / 2));
            destino.getChildren().add(l);
        }

        // Que el mapa no robe los clics a los nodos
        for (Node n : destino.getChildren()) n.setMouseTransparent(true);
    }

    // ------------------------------------------------------------------ 3D

    /** Extruye la huella hacia arriba: caras laterales (luz/sombra) + techo. */
    private static Group extruir(double[] p, double h) {
        double dx = -h * 0.35, dy = -h;
        int n = p.length / 2;

        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        double signo = area > 0 ? 1 : -1;

        class Cara { Polygon poly; double ny; }
        List<Cara> caras = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x1 = p[i * 2], y1 = p[i * 2 + 1], x2 = p[j * 2], y2 = p[j * 2 + 1];
            double nx = (y2 - y1) * signo, ny = -(x2 - x1) * signo;   // normal hacia afuera
            Cara c = new Cara();
            c.ny = ny;
            c.poly = new Polygon(x1, y1, x2, y2, x2 + dx, y2 + dy, x1 + dx, y1 + dy);
            c.poly.getStyleClass().add(nx < 0 ? "cara-luz" : "cara-sombra");
            caras.add(c);
        }
        caras.sort(Comparator.comparingDouble(c -> c.ny));  // las de atrás primero

        Group g = new Group();
        for (Cara c : caras) g.getChildren().add(c.poly);

        Polygon techo = new Polygon();
        Polygon detalle = new Polygon();
        double cx = 0, cy = 0;
        for (int i = 0; i < n; i++) { cx += p[i * 2]; cy += p[i * 2 + 1]; }
        cx /= n; cy /= n;
        for (int i = 0; i < n; i++) {
            double x = p[i * 2] + dx, y = p[i * 2 + 1] + dy;
            techo.getPoints().addAll(x, y);
            detalle.getPoints().addAll(cx + dx + (p[i * 2] - cx) * 0.80, cy + dy + (p[i * 2 + 1] - cy) * 0.80);
        }
        techo.getStyleClass().add("techo");
        detalle.getStyleClass().add("techo-detalle");
        g.getChildren().addAll(techo, detalle);
        return g;
    }

    // ------------------------------------------------------------------ helpers

    private static double[] pt(double x, double y, double sx, double sy) { return new double[]{x * sx, y * sy}; }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    private static double[] centro(double[]... ps) {
        double x = 0, y = 0;
        for (double[] p : ps) { x += p[0]; y += p[1]; }
        return new double[]{x / ps.length, y / ps.length};
    }

    private static Polygon poligono(String clase, double[]... ps) {
        Polygon pg = new Polygon();
        for (double[] p : ps) pg.getPoints().addAll(p[0], p[1]);
        pg.getStyleClass().add(clase);
        return pg;
    }

    private static Line linea(String clase, double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.getStyleClass().add(clase);
        return l;
    }

    private static Line lineaP(String clase, double[] a, double[] b) {
        return linea(clase, a[0], a[1], b[0], b[1]);
    }

    /** Dibuja una calle: primero el borde (más ancho) y encima el relleno. Puntos x,y,x,y,... */
    private static void calle(Pane d, double sx, double sy, String tipo, double... c) {
        d.getChildren().add(trazo(tipo + "-borde", sx, sy, c));
        d.getChildren().add(trazo(tipo, sx, sy, c));
    }

    /** Curva suave (Catmull-Rom → Bézier) que pasa por todos los puntos. */
    private static Path trazo(String clase, double sx, double sy, double[] c) {
        Path path = new Path();
        path.getStyleClass().add(clase);
        int n = c.length / 2;
        path.getElements().add(new MoveTo(c[0] * sx, c[1] * sy));
        for (int i = 0; i < n - 1; i++) {
            double x0 = c[Math.max(i - 1, 0) * 2],     y0 = c[Math.max(i - 1, 0) * 2 + 1];
            double x1 = c[i * 2],                      y1 = c[i * 2 + 1];
            double x2 = c[(i + 1) * 2],                y2 = c[(i + 1) * 2 + 1];
            double x3 = c[Math.min(i + 2, n - 1) * 2], y3 = c[Math.min(i + 2, n - 1) * 2 + 1];
            path.getElements().add(new CubicCurveTo(
                    (x1 + (x2 - x0) / 6) * sx, (y1 + (y2 - y0) / 6) * sy,
                    (x2 - (x3 - x1) / 6) * sx, (y2 - (y3 - y1) / 6) * sy,
                    x2 * sx, y2 * sy));
        }
        return path;
    }
}