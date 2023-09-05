package cz.habarta.typescript.generator.gradle;

import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GradlePluginClasspathProvider {

    public static List<String> getClasspath(File projectDir) throws NoSuchFieldException, IllegalAccessException {
        List<String> list = GradlePluginClasspathProvider.getUrls(ClassLoader.getSystemClassLoader()).stream().map(file -> file.getAbsolutePath()).collect(Collectors.toList());
        list.addAll(buildDirs(projectDir.toString()));
        return list;
    }

    public static List<File> getUrls(ClassLoader classLoader) throws NoSuchFieldException, IllegalAccessException {
        System.out.println(classLoader.getClass().getName());
        if (classLoader instanceof URLClassLoader) {
            return (Arrays.asList(((URLClassLoader) classLoader).getURLs())).stream().map(URL -> toFile(URL)).collect(Collectors.toList());
        }

        // jdk9
        if (classLoader.getClass().getName().startsWith("jdk.internal.loader.ClassLoaders$")) {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Unsafe unsafe = (Unsafe) field.get(null);

            // jdk.internal.loader.ClassLoaders.AppClassLoader.ucp
            Field ucpField = classLoader.getClass().getSuperclass().getDeclaredField("ucp");
            long ucpFieldOffset = unsafe.objectFieldOffset(ucpField);
            Object ucpObject = unsafe.getObject(classLoader, ucpFieldOffset);

            // jdk.internal.loader.URLClassPath.path
            Field pathField = ucpField.getType().getDeclaredField("path");
            long pathFieldOffset = unsafe.objectFieldOffset(pathField);
            List<URL> path = (ArrayList<URL>) unsafe.getObject(ucpObject, pathFieldOffset);

            Field mapField = ucpField.getType().getDeclaredField("lmap");
            long mapFieldOffset = unsafe.objectFieldOffset(mapField);
            Map<String, Object> map = (Map<String, Object>) unsafe.getObject(ucpObject, mapFieldOffset);
            List<File> all = new ArrayList<>();
            all.addAll(path.stream().map(URL -> toFile(URL)).collect(Collectors.toList()));
            all.addAll(map.keySet().stream().map(url -> toFile(asUrl(url))).collect(Collectors.toSet()));
            return all;
        }

        return null;
    }

    @NotNull
    private static URL asUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static File toFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    @NotNull
    private static List<String> buildDirs(String sampleGradleDir) {
        List<String> buildDirs = new ArrayList<>();
        buildDirs.add(String.join(File.separator, sampleGradleDir, "build", "classes", "java", "main"));
        buildDirs.add(String.join(File.separator, sampleGradleDir, "build", "classes", "groovy", "main"));
        buildDirs.add(String.join(File.separator, sampleGradleDir, "build", "classes", "scala", "main"));
        buildDirs.add(String.join(File.separator, sampleGradleDir, "build", "classes", "kotlin", "main"));

        return buildDirs;
    }

}