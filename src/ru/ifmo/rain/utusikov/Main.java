package ru.ifmo.rain.utusikov;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import ru.ifmo.rain.utusikov.implementor.Implementor;

import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        Implementor impl = new Implementor();
        try {
            impl.implementJar(SDeprecated.class, Paths.get("Homework4/"));
        } catch (ImplerException e) {
            System.out.println("blya " + e.getMessage());
        }
    }
}
