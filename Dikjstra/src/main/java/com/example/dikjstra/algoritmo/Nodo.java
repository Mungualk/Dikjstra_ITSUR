package com.example.dikjstra.algoritmo;

public record Nodo(String id)
{
    public Nodo
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("El id del nodo no puede estar vacío");
        }
    }
}