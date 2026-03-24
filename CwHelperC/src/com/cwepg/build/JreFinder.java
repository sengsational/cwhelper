package com.cwepg.build;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class JreFinder {
    public static String getDynamicJrePath() {
        File pluginsDir = new File("/home/owner/Eclipse/plugins");
        
        if (pluginsDir.exists() && pluginsDir.isDirectory()) {
            // Filter for folders starting with the JustJ JRE prefix
            File[] matches = pluginsDir.listFiles((dir, name) -> 
                name.startsWith("org.eclipse.justj.openjdk.hotspot.jre.full.linux"));

            if (matches != null && matches.length > 0) {
                // Sort to get the latest version if multiple exist
                Arrays.sort(matches);
                File latestJreDir = matches[matches.length - 1];
                
                // Construct the path to the internal /jre folder
                return new File(latestJreDir, "jre").getAbsolutePath() + "/";
            }
        }
        return null; // Fallback or handle error
    }

    public static void main(String[] args) {
        String JRE_PATH = getDynamicJrePath();
        System.out.println("Detected JRE: " + JRE_PATH);
    }
}
