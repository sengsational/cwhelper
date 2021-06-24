package org.cwepg.hr;
import java.io.*;
public class DFile extends File{
	private static final long serialVersionUID = 2584068205192995243L;

	/** File decorator to help with copy of files
     * @param aFile A file object that you want to operate on.
     */
	public DFile(String aFile){
		super(aFile);
	}
        /** A method to copy from a file to another file.
         * @param dFile Destination file
         * @return True if it worked.
         */
	public boolean copyTo(File dFile){
        System.out.println("Trying this: [copy " + this.getAbsoluteFile() + " to " + dFile.getAbsoluteFile() + "]");
		try {
			writeInputStreamToOutputStream(new FileInputStream(this), new FileOutputStream(dFile));
		} catch (Exception e){
			return false;
		}
		return true;
	}

    public boolean copyTo(DFile dFile, boolean eatException) throws Exception {
        if (eatException) {
            return copyTo(dFile);
        } else {
            writeInputStreamToOutputStream(new FileInputStream(this), new FileOutputStream(dFile));
            return true;
        }
    }

        /** The mechanics of copy of file.  Should be private.
         * @param aInputStream input stream
         * @param aOutputStream output stream
         * @throws IOException exception
         */
    public static void writeInputStreamToOutputStream(java.io.FileInputStream aInputStream, java.io.FileOutputStream aOutputStream) throws java.io.IOException {
	    byte tmpBuffer[] = new byte[1024];
 	    java.io.BufferedInputStream tmpBufferedInputStream = new BufferedInputStream(aInputStream);
 	    java.io.BufferedOutputStream tmpBufferedOutputStream = new BufferedOutputStream(aOutputStream);

	    int bytesRead = 0;
	    while ((bytesRead = tmpBufferedInputStream.read(tmpBuffer)) > 0 )
	    {
		    tmpBufferedOutputStream.write(tmpBuffer, 0, bytesRead);
	    }

	    tmpBufferedOutputStream.flush();
	    tmpBufferedOutputStream.close();
        tmpBufferedInputStream.close();
    }


}
