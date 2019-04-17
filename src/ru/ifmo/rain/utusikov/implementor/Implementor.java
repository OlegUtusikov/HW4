package ru.ifmo.rain.utusikov.implementor;

import info.kgeorgiy.java.advanced.implementor.*;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.*;
import java.util.stream.Collectors;

/**
 * This class overriding methods, constructors given super classes or interfaces.
 * Has a default constructor.
 * Can compress to Jar or create a package or folder.
 *
 * @author Oleg Utusikov
 * @version 1.0
 */

public class Implementor implements JarImpler {
    /**
     * Opening figured bracket {
     */
    private final String OPEN = "{";
    /**
     * Closing figured bracket }
     */
    private final String CLOSE = "}";
    /**
     * Opening round bracket (
     */
    private final String OPEN_BRACKET = "(";
    /**
     * Closing round bracket )
     */
    private final String CLOSE_BRACKET = ")";
    /**
     * A new line string. System dependent. Counting by {@link System#lineSeparator()}.
     */
    private final String NEW_LINE = System.lineSeparator();
    /**
     * String for semicolon ;.
     */
    private final String SEMICOLON = ";";
    /**
     * String for tabulation.
     */
    private final String TAB = "    ";
    /**
     * String for space.
     */
    private final String SPACE = " ";
    /**
     * String for file separator. System dependent. Counting by {@link File#separator}.
     */
    private final String FILE_SEPARATOR = File.separator;
    /**
     * Char for file separator. System dependent. Counting by {@link File#separatorChar}.
     */
    private final char FILE_SEPARATOR_CHAR = File.separatorChar;
    /**
     * Suffix for name of file.
     */
    private final String IMPL = "Impl";
    /**
     * String which contain a answer. There is a code of future class, which implements of given interface.
     */
    private StringBuilder result = null;
    /**
     * Set of methods. It's containing a deferents methods.
     */
    private Set<MyMethod> setMethods = null;


    /**
     * It's constructor of Implementor
     */
    public Implementor() {

    }

    /**
     * Find a path of parent's package.
     *
     * @param token is {@link Class}. Cannot be a null
     * @param root  is {@link Path}. Where save file. Cannot be a null.
     * @return {@link Path} of package where containing our parent. Cannot be a null.
     */
    private Path getPathOfPackage(final Class<?> token, final Path root) {
        return root.resolve(token.getPackageName().replace('.', FILE_SEPARATOR_CHAR));
    }
    /**
     * Find a path of generated file.
     *
     * @param token  is {@link Class}. Cannot be a null
     * @param root   is {@link Path}. Where save file. Cannot be a null.
     * @param format is {@link String}. Contains format of file(.java or .class). It doesn't be a null.
     * @return {@link Path} of package where containing our parent. Cannot be a null.
     */
    private Path getPathOfFile(final Class<?> token, final Path root, final String format) {
        return root.resolve(getPathOfPackage(token, root).toString().concat(FILE_SEPARATOR + token.getSimpleName() + IMPL + format));
    }

    private void toUnicode() {
        StringBuilder uni = new StringBuilder();
        for(int i = 0; i < result.length(); i++) {
            char curCh = result.charAt(i);
            if (curCh >= 128) {
                uni.append("\\u").append(String.format("%04X", (int)curCh));
            } else {
                uni.append(curCh);
            }
        }
        result = uni;
    }

    /**
     * This method writes a result in a file.
     * Use {@link Writer}
     *
     * @param token is {@link Class}. Cannot be a null.
     * @param root  is {@link Path}. Where save file. Cannot be a null.
     * @throws ImplerException throws when {@link Writer} failed to write a data.
     */
    private void write(final Class<?> token, final Path root) throws ImplerException {
        toUnicode();
        try (Writer out = Files.newBufferedWriter(getPathOfFile(token, root, ".java"))) {
            out.write(result.toString());
        } catch (IOException e) {
            throw new ImplerException("Couldn't write a text!");
        }
    }

    /**
     * This method creates a directories for a file.
     * Use {@link Files#createDirectories(Path, FileAttribute[])}
     *
     * @param token is {@link Class}. Cannot be a null.
     * @param root  is {@link Path}. Where save file. Cannot be a null.
     * @throws ImplerException throws when {@link Files#createDirectories(Path, FileAttribute[])} failed to create.
     * @see Files
     * @see Path
     */
    private void createDirectories(final Class<?> token, final Path root) throws ImplerException {
        if (getPathOfFile(token, root, ".java").getParent() != null) {
            try {
                Files.createDirectories(getPathOfFile(token, root, ".java").getParent());
            } catch (IOException e) {
                throw new ImplerException("Unable to create directories for output file", e);
            }
        }
    }

    /**
     * This method adds method's annotations in result. Also add a {@link SuppressWarnings}.
     *
     * @param annotations is a array of {@link Annotation} was taken by {@link Method#getDeclaredAnnotations()}.
     * @param flag        is a boolean. Detect constructor.
     * @see Annotation
     */
    private void addAnnotations(final Annotation[] annotations, final boolean flag) {
        if (flag) {
            result.append(TAB + "@Override").append(NEW_LINE);
        }
        result.append(TAB + "@SuppressWarnings(\"unchecked\")").append(NEW_LINE);
        for (Annotation annotation : annotations) {
            result.append(TAB).append(annotation.toString()).append(NEW_LINE);
        }
    }

    /**
     * Return a String method's arguments.
     *
     * @param parameters is  array of {@link Parameter} was taken by {@link Method#getParameterTypes()}. Cannot be a null.
     * @return String of arguments in format "X0, X1, ..., Xn"
     * @see Parameter
     */
    private String getArgs(final Parameter[] parameters) {
        StringBuilder tmp = new StringBuilder();
        for (int i = 0; i < parameters.length; i++) {
            tmp.append(parameters[i].getType().getCanonicalName()).append(SPACE).append("X")
                    .append(i).append((i + 1 == parameters.length) ? "" : "," + SPACE);
        }
        return tmp.toString();
    }

    /**
     * Parse a array of {@link Class} exceptions.
     *
     * @param exceptions is array of {@link Class} was taken by {@link Method#getExceptionTypes()}
     * @return string with exceptions in format " trows e1, e2, e3 ..."
     */
    private String getExceptions(final Class<?>[] exceptions) {
        StringBuilder tmp = new StringBuilder();
        if (exceptions.length > 0) {
            tmp.append(SPACE + "throws" + SPACE);
            for(int i = 0; i < exceptions.length; i++) {
                tmp.append(exceptions[i].getCanonicalName()).append((i + 1 == exceptions.length ? "" : ", "));
            }
        }
        return tmp.toString();
    }

    /**
     * Return a type of method.
     * Takes type token by {@link Method#getReturnType()}
     * If it's a void, then return "", else if it's a primitive type return false or 0, else return (Type)(new Object()).
     *
     * @param method is {@link Method}
     * @return return type of method.
     * @see Method
     */
    private String getReturnedValue(final Method method) {
        if (method.getReturnType().isPrimitive()) {
            return method.getReturnType().equals(boolean.class) ? "false" : (method.getReturnType().equals(void.class) ? "" : "0");
        } else {
            return OPEN_BRACKET + method.getReturnType().getCanonicalName() + CLOSE_BRACKET + OPEN_BRACKET + "new Object()" + CLOSE_BRACKET;
        }
    }

    /**
     * Parse a class constructor.
     * Call a super constuctor with given arguments.
     * Use {@link Implementor#getArgs(Parameter[])} for find arguments
     *
     * @param constructor is {@link Constructor}. It's a constructor of class
     * @param token       is {@link Class}
     * @see Constructor
     */
    private void parseCurConstructor(final Constructor constructor, final Class<?> token) {
        if (Modifier.isPrivate(constructor.getModifiers())) {
            return;
        }
        StringBuilder paramsOfConstructor = new StringBuilder();
        for (int i = 0; i < constructor.getParameterCount(); i++) {
            if (i == 0) {
                paramsOfConstructor.append("X").append(i);
            } else {
                paramsOfConstructor.append("," + SPACE + "X").append(i);
            }
        }
        addAnnotations(constructor.getDeclaredAnnotations(), false);
        result.append(TAB + "public" + SPACE).append(token.getSimpleName()).append(IMPL)
                .append(OPEN_BRACKET).append(getArgs(constructor.getParameters())).append(CLOSE_BRACKET)
                .append(getExceptions(constructor.getExceptionTypes())).append(SPACE)
                .append(OPEN).append(NEW_LINE);
        result.append(TAB + TAB + "super" + OPEN_BRACKET)
                .append(paramsOfConstructor).append(CLOSE_BRACKET).append(SEMICOLON).append(NEW_LINE);
        result.append(TAB + CLOSE).append(NEW_LINE).append(NEW_LINE);
    }

    /**
     * Parse current method, which was given. Put in result modifier, name, args and body.
     * Use {@link Implementor#getArgs(Parameter[])}, {@link Implementor#getExceptions(Class[])} and {@link Implementor#getReturnedValue(Method)}
     *
     * @param method is {@link Method}.
     * @see Method
     */
    private void parseCurMethod(final Method method) {
        MyMethod myMethod = new MyMethod(method);
        if (setMethods.contains(myMethod)) {
            return;
        }
        setMethods.add(myMethod);
        if (!Modifier.isAbstract(method.getModifiers()) || method.isDefault() || Modifier.isStatic(method.getModifiers())
                || Modifier.isPrivate(method.getModifiers())) {
            return;
        }
        addAnnotations(method.getDeclaredAnnotations(), true);
        result.append(TAB).append(Modifier.toString(method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT & ~Modifier.NATIVE))
                .append(SPACE).append(method.getReturnType().getCanonicalName()).append(SPACE)
                .append(method.getName()).append(OPEN_BRACKET).append(getArgs(method.getParameters())).append(CLOSE_BRACKET).append(getExceptions(method.getExceptionTypes())).append(SPACE)
                .append(OPEN)
                .append(NEW_LINE);
        result.append(TAB + TAB + "return" + SPACE).append(getReturnedValue(method)).append(SEMICOLON).append(NEW_LINE);
        result.append(TAB + CLOSE);
        result.append(NEW_LINE);
        result.append(NEW_LINE);
    }

    /**
     * Parse methods current token.
     * Use {@link Class#getDeclaredMethods()}.
     *
     * @param cur is a {@link Class}. It's a token
     * @see Method
     */
    private void parseMethods(final Class<?> cur) {
        for (Method curMethod : cur.getDeclaredMethods()) {
            parseCurMethod(curMethod);
        }
    }

    /**
     * Method for searching a super classes or interfaces.
     * Use {@link Implementor#parseCurMethod(Method)}
     *
     * @param cur is {@link Class}.
     * @see Class
     */
    private void printMethods(final Class<?> cur) {
        if (cur == null) {
            return;
        }
        parseMethods(cur);
        printMethods(cur.getSuperclass());
        Class<?>[] ints = cur.getInterfaces();
        for (Class<?> curInt : ints) {
            parseMethods(curInt);
            printMethods(curInt);
        }
    }

    /**
     * Create body of file. Write imports, packages, header and body of classImpl.
     *
     * @param token is {@link Class}.
     * @param root  is {@link Path}.
     * @throws ImplerException when a token was a Abstract class and had only privates constructors.
     */
    private void createBodyOfFile(final Class<?> token, final Path root) throws ImplerException {
        StringBuilder tmp = new StringBuilder();
        if (!token.getPackageName().equals("")) {
            tmp.append("package" + SPACE).append(token.getPackageName()).append(SEMICOLON).append(NEW_LINE).append(NEW_LINE);
        }
        result.append("public" + SPACE + "class" + SPACE).append(token.getSimpleName()).append(IMPL + SPACE)
                .append(token.isInterface() ? "implements " : "extends ").append(SPACE)
                .append(token.getName())
                .append(SPACE + OPEN);
        result.append(NEW_LINE);
        if (!token.isInterface()) {
            int prevLength = result.length();
            for (Constructor constructor : token.getDeclaredConstructors()) {
                parseCurConstructor(constructor, token);
            }
            if (prevLength == result.length()) {
                throw new ImplerException("All constructors are private!");
            }
        }
        printMethods(token);
        tmp.append(result);
        result = tmp;
        result.append(CLOSE);
    }

    /**
     * Check input parameters. Check root isn't null.
     * Check that token isn't primitive, array, final class, enum and null
     *
     * @param token is {@link Class}.
     * @param root  is {@link Path}
     * @return false if token and root are correct else true.
     */
    private boolean check(final Class<?> token, final Path root) {
        return token.isPrimitive() || token.isArray() || Modifier.isFinal(token.getModifiers()) || token == Enum.class || root == null;
    }

    /**
     * Generate a java document with class.
     *
     * @param token is {@link Class}
     * @param root  is {@link Path}
     * @throws ImplerException when {@link Implementor#check(Class, Path)} returned true;
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        if (check(token, root)) {
            throw new ImplerException("Incorrect token or root!");
        }
        result = new StringBuilder();
        setMethods = new HashSet<>();
        createDirectories(token, root.toAbsolutePath());
        createBodyOfFile(token, root.toAbsolutePath());
        write(token, root.toAbsolutePath());
    }

    /**
     * Compress to Jar directory.
     * Creating manifest with author and version.
     *
     * @param token  is {@link Class}.
     * @param tmpDir is temp directory. Contain java documents.
     * @param root   is {@link Path}.
     * @throws ImplerException when couldn't write to root path.
     */
    private void compressToJar(final Class<?> token, final Path tmpDir, final Path root) throws ImplerException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VENDOR, "Oleg Utusikov");
        try (JarOutputStream writer = new JarOutputStream(Files.newOutputStream(root.toAbsolutePath()), manifest)) {
            writer.putNextEntry(new JarEntry(token.getName().replace('.', '/') + IMPL + ".class"));
            Files.copy(getPathOfFile(token, tmpDir, ".class"), writer);
        } catch (IOException e) {
            throw new ImplerException("Unable to write to JAR file! Cause: " + e.getMessage());
        }
    }

    /**
     * Generate Jar directory with classes.
     * Use {@link Implementor#implement(Class, Path)}, {@link JavaCompiler}, {@link Implementor#compressToJar(Class, Path, Path)}
     *
     * @param token is {@link Class}. It's class-token.(.class).
     * @param root  is {@link Path}. It's a way, where save a file.
     * @throws ImplerException when token or root was incorrect, or couldn't create a tmp dir, or couldn't compile, or compress to Jar.
     * @see System#getProperty(String)
     * @see ToolProvider
     */
    @Override
    public void implementJar(Class<?> token, Path root) throws ImplerException {
        if (check(token, root)) {
            throw new ImplerException("Incorrect token or root!");
        }
        Path tmpDir;
        try {
            tmpDir = Files.createTempDirectory(root.toAbsolutePath().getParent(), "temp");
        } catch (IOException e) {
            throw new ImplerException("Unable to create temp directory", e);
        }
        implement(token, tmpDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        System.out.println("!!!!!!! " + tmpDir.toString() + File.pathSeparator + System.getProperty("java.class.path"));
        String[] args = new String[]{
                "-cp", System.getProperty("java.class.path"),
                getPathOfFile(token, tmpDir, ".java").toString(),
                "-encoding", "UTF-8"
        };
        if (compiler == null || compiler.run(null, null, null, args) != 0) {
            throw new ImplerException("Unable to compile generated files");
        }
        compressToJar(token, tmpDir, root);
    }

    /**
     * Main void take a arguments of command line.\n
     *
     * @param args is array of {@link String}. Has a length a two or three strings.
     *             Three arguments : "-jar", "class_name", "jar_out" - if we want create jar from.
     *             Two arguments : "class_name", "jar_out" - if we want create only java file.
     *             class_name - name of input class.
     *             jar_out - path to jar file.
     */
    public static void main(String[] args) {
        if (args == null || (args.length != 2 && args.length != 3)) {
            System.out.println("Two or three arguments expected");
            return;
        }
        if (Arrays.stream(args).filter(Objects::isNull).collect(Collectors.toList()).size() > 0) {
            System.out.println("All arguments must be non-null");
            return;
        }
        JarImpler implementor = new Implementor();
        try {
            if (args.length == 3) { //
                implementor.implementJar(Class.forName(args[1]), Paths.get(args[2]));
            } else {
                implementor.implement(Class.forName(args[0]), Paths.get(args[1]));
            }
        } catch (InvalidPathException e) {
            System.out.println("Incorrect path to root: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("Incorrect class name: " + e.getMessage());
        } catch (ImplerException e) {
            System.out.println("An error occurred during implementation: " + e.getMessage());
        }
    }

    /**
     * Special class-wrapper for {@link Method}. This class has yours hashCode and equals methods.
     */
    private class MyMethod {
        /**
         * Contain a {@link Method}.
         */
        private final Method method;
        /**
         * It's a constant for hash-function. A simple number.
         */
        private final static int BASE = 53;
        /**
         * It's a constant for hash-function. Use how a module.
         */
        private final static int MOD = (int) (1e9 + 7);

        /**
         * Constructor for MyMethod
         *
         * @param method is given Method.
         */
        private MyMethod(Method method) {
            this.method = method;
        }

        /**
         * Count a hash of function.
         *
         * @return hash of function.
         */
        @Override
        public int hashCode() {
            return ((Arrays.hashCode(method.getParameterTypes())
                    + BASE * method.getReturnType().hashCode()) % MOD
                    + method.getName().hashCode() * BASE * BASE) % MOD;
        }

        /**
         * Compare a methods. Take a obj and cast to MyMethod.
         * If obj == null return false.
         *
         * @param obj other method.
         * @return true or false
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj instanceof MyMethod) {
                Method other = ((MyMethod) obj).method;
                return Arrays.equals(method.getParameterTypes(), other.getParameterTypes())
                        && method.getReturnType().equals(other.getReturnType())
                        && method.getName().equals(other.getName());
            }
            return false;
        }
    }
}