package com.example.dikjstra.algoritmo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DijkstraTest
{
    private static final double DELTA = 1e-9;

    private final Nodo A = new Nodo("A");
    private final Nodo B = new Nodo("B");
    private final Nodo C = new Nodo("C");
    private final Nodo D = new Nodo("D");
    private final Nodo E = new Nodo("E");
    private final Nodo F = new Nodo("F");   // queda aislado a propósito

    private Grafo grafo;
    private Dijkstra dijkstra;

    /*
     *        4        5        3
     *    A ------ B ------ D ------ E        F (sin conexiones)
     *     \      /        /
     *    1 \    / 2      / 8
     *       \  /        /
     *         C --------
     */
    @BeforeEach
    void crearGrafo()
    {
        grafo = new Grafo();
        grafo.crearArista(A, B, 4);
        grafo.crearArista(A, C, 1);
        grafo.crearArista(C, B, 2);
        grafo.crearArista(B, D, 5);
        grafo.crearArista(C, D, 8);
        grafo.crearArista(D, E, 3);
        grafo.agregarNodo(F);

        dijkstra = new Dijkstra(grafo);
    }

    @Test
    void rutaMasCortaNoEsLaDeMenosNodos()
    {
        // A-B-D tiene menos nodos pero mide 9; A-C-B-D mide 1+2+5 = 8
        ResRuta r = dijkstra.calcular(A, D);

        assertTrue(r.hay_Camino());
        assertEquals(List.of(A, C, B, D), r.camino());
        assertEquals(8.0, r.distancia(), DELTA);
    }

    @Test
    void rutaMasLarga()
    {
        ResRuta r = dijkstra.calcular(A, E);

        assertEquals(List.of(A, C, B, D, E), r.camino());
        assertEquals(11.0, r.distancia(), DELTA);
    }

    @Test
    void rutaEnSentidoContrarioMideLoMismo()
    {
        ResRuta r = dijkstra.calcular(D, A);

        assertEquals(List.of(D, B, C, A), r.camino());
        assertEquals(8.0, r.distancia(), DELTA);
    }

    @Test
    void nodosDesconectadosNoTienenCamino()
    {
        ResRuta r = dijkstra.calcular(A, F);

        assertFalse(r.hay_Camino());
        assertEquals(-1, r.distancia(), DELTA);
    }

    @Test
    void origenIgualADestino()
    {
        ResRuta r = dijkstra.calcular(A, A);

        assertEquals(List.of(A), r.camino());
        assertEquals(0.0, r.distancia(), DELTA);
    }

    @Test
    void nodoQueNoEstaEnElGrafo()
    {
        assertFalse(dijkstra.calcular(A, new Nodo("Z")).hay_Camino());
        assertFalse(dijkstra.calcular(new Nodo("Z"), A).hay_Camino());
    }

    @Test
    void nodosNullNoTronan()
    {
        assertFalse(dijkstra.calcular(null, A).hay_Camino());
        assertFalse(dijkstra.calcular(A, null).hay_Camino());
    }

    @Test
    void grafoRechazaAristasInvalidas()
    {
        assertThrows(IllegalArgumentException.class, () -> grafo.crearArista(A, A, 3));
        assertThrows(IllegalArgumentException.class, () -> grafo.crearArista(A, B, -1));
        assertThrows(IllegalArgumentException.class, () -> grafo.crearArista(null, B, 1));
    }
}