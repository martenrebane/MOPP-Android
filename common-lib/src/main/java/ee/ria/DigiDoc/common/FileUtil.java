package ee.ria.DigiDoc.common;

import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtil {

    /**
     * Check if file path is in cache directory
     *
     * @param file File to check
     * @return Boolean indicating if file is in the cache directory.
     */
    public static File getFileInDirectory(File file, File directory) throws IOException {
        if (file.getCanonicalPath().startsWith(directory.getCanonicalPath())) {
            return file;
        }

        throw new IOException("Invalid file path");
    }

    /**
     * Replace invalid string characters
     *
     * @param input Input to replace invalid characters
     * @param replacement Replacement to replace invalid characters with
     * @return String with valid characters
     */
    public static String sanitizeString(String input, char replacement) {
        if (input == null) {
            return null;
        }

        String allowedCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_,.:/%;+=@?&()";

        StringBuilder sb = new StringBuilder(input.length());

        for (int offset = 0; offset < input.length(); offset++) {
            char c = input.charAt(offset);

            if (allowedCharacters.indexOf(c) == -1) {
                sb.append(replacement);
            }
            else {
                // Coverity does not want to see usages of the original string
                sb.append(allowedCharacters.charAt(allowedCharacters.indexOf(c)));
            }
        }

        return Uri.parse(sb.toString());
    }

    public static Uri normalizePath(String filePath) {
        return Uri.parse(FilenameUtils.normalize(filePath));
    }

    public static boolean isPDF(File file) {
        try (ParcelFileDescriptor parcelFileDescriptor = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY)) {
            // Try to render as PDF. Throws exception if not a PDF file.
            new PdfRenderer(parcelFileDescriptor);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static Path renameFile(Path path, String fileNameWithExtension) {
        try {
            Files.deleteIfExists(path.resolveSibling(fileNameWithExtension));
            Path newFilePath = path.resolveSibling(fileNameWithExtension);
            Files.move(path, newFilePath);
            return newFilePath;
        } catch (IOException e) {
            return path;
        }
    }

    private static boolean isRawUrl(String url) {
        if (url == null || url.length() == 0) {
            return false;
        }

        return url.startsWith("raw:");
    }
}
