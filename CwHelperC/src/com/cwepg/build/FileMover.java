package com.cwepg.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;

public class FileMover {
    public static boolean moveFile(String sourcePath, String destinationPath) {
        Path source = Paths.get(sourcePath);
        Path destination = Paths.get(destinationPath);

        try {
            // Ensure the destination directory exists
            Files.createDirectories(destination.getParent());

            // Use Files.move with REPLACE_EXISTING option
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to move file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

