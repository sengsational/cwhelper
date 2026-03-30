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

/*
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
