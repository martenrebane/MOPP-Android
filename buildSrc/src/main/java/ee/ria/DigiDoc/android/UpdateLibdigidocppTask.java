package ee.ria.DigiDoc.android;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class UpdateLibdigidocppTask extends DefaultTask {

    private static final String PREFIX = "libdigidocpp.";
    private static final String SUFFIX = ".zip";
    private static final String JAR = PREFIX + "jar";
    private static final String SCHEMA = "schema.zip";

    private static final List<String> ABIS = new ArrayList<>();
    private static final Map<String, String> ABI_FILES = new HashMap<>();
    static {
        ABIS.add("arm64-v8a");
        ABIS.add("armeabi-v7a");
        ABIS.add("x86_64");

        ABI_FILES.put("arm64-v8a", "androidarm64");
        ABI_FILES.put("armeabi-v7a", "androidarm");
        ABI_FILES.put("x86_64", "androidx86_64");
    }

    private String dir = ".";

    @Option(option = "dir", description = "Directory where the libdigidocpp ZIP files are")
    public void setDir(String dir) {
        this.dir = dir;
    }

    @Input
    public String getDir() {
        return dir;
    }

    @TaskAction
    public void run() throws IOException {
        File inputDir = new File(getProject().getRootDir(), getDir());
        File outputDir = getTemporaryDir();
        delete(outputDir);

        AtomicBoolean generateJar = new AtomicBoolean(true);
        AtomicBoolean generateSchema = new AtomicBoolean(true);

        for (String abi : ABIS) {
            update(
                    new File(inputDir, PREFIX + ABI_FILES.get(abi) + SUFFIX),
                    new File(outputDir, "unzipped"),
                    abi,
                    generateJar,
                    generateSchema
            );
        }
    }

    private void update(File zipFile, File outputDir, String abi, AtomicBoolean generateJar, AtomicBoolean generateSchema) throws IOException {
        if (!zipFile.exists()) {
            log("Could not find file %s", zipFile);
            return;
        }
        log("Updating from %s", zipFile);
        unzip(zipFile, outputDir);
        File cacheDir = new File(outputDir, PREFIX + ABI_FILES.get(abi));

        if (generateJar.getAndSet(false) && getProject().getName().equals("common-lib")) {
            log("Generating %s from %s", JAR, zipFile);
            File sourceDir = new File(cacheDir, "include");
            keepFolder(sourceDir, "ee");
            File jarFile = new File(cacheDir, JAR);
            compile(sourceDir);
            jar(sourceDir, jarFile);
            Files.copy(
                    jarFile.toPath(),
                    new File(getProject().getProjectDir(), "libs/" + JAR).toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
        if (generateSchema.getAndSet(false) && !getProject().getName().equals("common-lib")) {
            log("Generating %s from %s", SCHEMA, zipFile);
            File schemaCacheDir = new File(cacheDir, "etc");
            File schemaZipFile = new File(cacheDir, SCHEMA);

            try (FileOutputStream fileOutputStream = new FileOutputStream(schemaZipFile);
                 ZipOutputStream schemaOutputStream = new ZipOutputStream(fileOutputStream)) {

                for (File schemaFile : files(schemaCacheDir)) {
                    ZipEntry entry = new ZipEntry(schemaFile.getName());
                    entry.setTime(schemaFile.lastModified());
                    schemaOutputStream.putNextEntry(entry);
                    Files.copy(schemaFile.toPath(), schemaOutputStream);
                    schemaOutputStream.closeEntry();
                }
            }
            File schemaDir = new File(getProject().getProjectDir(), "src/main/res/raw");
            Files.copy(
                    schemaZipFile.toPath(),
                    new File(schemaDir, SCHEMA).toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        if (getProject().getName().equals("sign-lib")) {
            Files.copy(
                    new File(cacheDir, "lib/libdigidoc_java.so").toPath(),
                    new File(getProject().getProjectDir(), "src/main/jniLibs/" + abi + "/libdigidoc_java.so").toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
            Files.copy(
                    new File(cacheDir, "lib/libdigidoc_java.so").toPath(),
                    new File(getProject().getProjectDir(), "src/debug/jniLibs/" + abi + "/libdigidoc_java.so").toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void log(String message, Object... parameters) {
        getLogger().lifecycle(String.format(message, parameters));
    }

    private static void keepFolder(File folder, String keepFolderName) throws IOException {
        File[] files = folder.listFiles();

        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory() && !file.getName().equals(keepFolderName)) {
                    delete(file);
                }
            }
        }
    }

    private static void delete(File file) throws IOException {
        if (file != null) {
            File[] fileList = file.listFiles();
            if (fileList != null && fileList.length > 0) {
                for (File f : fileList) {
                    delete(f);
                }
            }
            boolean isFileDeleted = file.delete();
            if (!isFileDeleted) {
                throw new IOException("Failed to delete file " + file.getName());
            }
        }
    }

    private static void unzip(File zip, File destination) throws IOException {
        try (InputStream zipInputStream = new FileInputStream(zip)) {
            unzip(zipInputStream, destination);
        }
    }

    private static void unzip(InputStream stream, File destination) throws IOException {
        try (ZipInputStream inputStream = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                File entryFile = new File(destination, entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }
                if (!entryFile.toPath().normalize().startsWith(destination.toPath())) {
                    throw new ZipException("Bad zip entry: " + entry.getName());
                }
                File parentFile = entryFile.getParentFile();
                if (parentFile != null) {
                    Files.createDirectories(parentFile.toPath());
                    Files.copy(inputStream, entryFile.toPath());
                } else {
                    throw new FileNotFoundException("Unable to get file to make directories");
                }
            }
        }
    }

    private static void compile(File path) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        compiler.getTask(null, fileManager, null, null, null, fileManager.getJavaFileObjectsFromFiles(files(path))).call();
        fileManager.close();
    }

    private static void jar(File path, File jar) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (FileOutputStream fileOutputStream = new FileOutputStream(jar);
             JarOutputStream outputStream = new JarOutputStream(fileOutputStream, manifest)) {
            for (File file : files(path)) {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                JarEntry entry = new JarEntry(path.toPath().relativize(file.toPath()).toString());
                entry.setTime(file.lastModified());
                outputStream.putNextEntry(entry);
                Files.copy(file.toPath(), outputStream);
                outputStream.closeEntry();
            }
        }
    }

    private static List<File> files(File dir) {
        List<File> files = new ArrayList<>();
        if (dir != null) {
            File[] fileList = dir.listFiles();
            if (fileList != null && fileList.length > 0) {
                for (File file : fileList) {
                    if (file.isDirectory()) {
                        files.addAll(files(file));
                    } else {
                        files.add(file);
                    }
                }
            }
        }

        return files;
    }
}