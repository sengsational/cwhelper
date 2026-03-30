package com.cwepg.build;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class JreFinder {
    public static String getDynamicJrePath() throws Exception {
    	boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    	File pluginsDir = null;
    	if (isWindows) {
    		pluginsDir = new File("C:\\Users\\tmpet\\.p2\\pool\\plugins");
    	} else {
            pluginsDir = new File("/home/owner/Eclipse/plugins");
    	}

        if (pluginsDir.exists() && pluginsDir.isDirectory()) {
            // Filter for folders starting with the JustJ JRE prefix
            File[] matches = pluginsDir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith("org.eclipse.justj.openjdk.hotspot.jre.full");
				}
			});

            if (matches != null && matches.length > 0) {
                // Sort to get the latest version if multiple exist
                Arrays.sort(matches);
                File latestJreDir = matches[matches.length - 1];

                // Construct the path to the internal /jre folder
                return new File(latestJreDir, "jre").getAbsolutePath() + File.separator;
            }
        }
        throw new Exception("ERROR: Looked in [" + pluginsDir + "] for [org.eclipse.justj.openjdk.hotspot.jre.full ] but did not find it.");
    }

    public static void main(String[] args) throws Exception {
        String JRE_PATH = getDynamicJrePath();
        System.out.println("Detected JRE: " + JRE_PATH);
        //Detected JRE [/home/owner/Eclipse/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.linux.x86_64_21.0.10.v20260205-0638/jre/]
    }
}
