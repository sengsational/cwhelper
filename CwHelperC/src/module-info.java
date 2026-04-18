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
	requires mailapi;
	requires smtp;
	requires com.google.api.client;
	requires com.google.api.client.json.gson;
	requires com.google.api.services.gmail;
	requires com.google.api.client.auth;
	requires google.api.client;
	requires com.google.api.client.extensions.jetty.auth;
	requires com.google.api.client.extensions.java6.auth;
	requires org.apache.httpcomponents.httpclient;
}

/**
 * Terry's actual command from 4/1/2026 (no kidding):
 * set JAVA_HOME "C:\Program Files (x86)\BellSoft\LibericaJDK-11\"
*  & $JAVA_HOME\bin\jlink.exe --module-path $JAVA_HOME\jmods --add-modules "java.base,java.compiler,java.desktop,java.naming,java.scripting,java.security.jgss,java.security.sasl,java.sql,jdk.crypto.ec,jdk.httpserver,jdk.unsupported,java.datatransfer,java.logging,java.transaction.xa,java.xml" --output .\custom-runtime --strip-debug --no-man-pages --no-header-files --compress=2
 */

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
  --add-modules jdk.crypto.ec \
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
