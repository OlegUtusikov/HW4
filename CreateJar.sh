#!/usr/bin/env bash
jar cmvf MANIFEST.MF Implementor.jar ru/ifmo/rain/utusikov/*.class
jar uf Implementor.jar info/kgeorgiy/java/advanced/implementor/Impler.class info/kgeorgiy/java/advanced/implementor/JarImpler.class info/kgeorgiy/java/advanced/implementor/ImplerException.class
java -jar Implementor.jar java.util.List .
