module cwhelper {
    requires java.security.sasl;
    requires jdk.httpserver;
    requires jdk.unsupported;
    requires java.compiler;
    requires java.datatransfer;
    requires java.desktop;
    requires java.logging;
    requires java.naming;
    requires java.scripting;
    requires java.security.jgss;
    requires java.sql;
    requires java.transaction.xa;
    requires java.xml;
    requires com.sun.jna.platform;
    requires com.sun.jna;
    requires ucanaccess;
}

/** This worked to generate output on Linux with 1097 version fat jar
 * 
jlink \
  --module-path $JAVA_HOME/jmods \
  --add-modules java.base \
  --add-modules java.compiler \
  --add-modules java.desktop \
  --add-modules java.naming \
  --add-modules java.scripting \
  --add-modules java.security.jgss \
  --add-modules java.security.sasl \
  --add-modules java.sql \
  --add-modules jdk.httpserver \
  --add-modules jdk.unsupported \
  --add-modules java.datatransfer \
  --add-modules java.logging \
  --add-modules java.transaction.xa \
  --add-modules java.xml \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2 \
  --output ./custom-runtime
 * 
 * jlink did not like it when these were included:
 * 
 *  --add-modules com.sun.jna.platform \
 *  --add-modules com.sun.jna \
 */


/* Earlier suggestion for jlink that did not work
jlink \
  --module-path $JAVA_HOME/jmods \
  --add-modules java.security.sasl \
  --add-modules jdk.httpserver \
  --add-modules jdk.unsupported \
  --add-modules java.compiler \
  --add-modules java.datatransfer \
  --add-modules java.desktop \
  --add-modules java.logging \
  --add-modules java.naming \
  --add-modules java.scripting \
  --add-modules java.security.jgss \
  --add-modules java.sql \
  --add-modules java.transaction.xa \
  --add-modules java.xml \
  --add-modules com.sun.jna.platform \
  --add-modules com.sun.jna \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2 \
  --output ./custom-runtime
*/
