/*
 * Created on Nov 9, 2009
 *
 */
package org.cwepg.hr;

import java.io.File;
import java.io.FilenameFilter;

public class DirectoryFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        File aFile =  new File(dir + File.separator + name);
        if (aFile.isHidden()) return false;
        return aFile.isDirectory();
    }
}
