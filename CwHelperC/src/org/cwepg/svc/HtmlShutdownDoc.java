/*
 * Created on Apr 8, 2018
 *
 */
package org.cwepg.svc;

public class HtmlShutdownDoc {

    public static String page;
    
    public static String get() {
        if (HtmlShutdownDoc.page != null) return HtmlShutdownDoc.page;
        else return getDefault();
    }
    
    public static String getDefault() {
        StringBuffer buf = new StringBuffer();
        buf.append("<!DOCTYPE html>\n");
        buf.append("<html lang=\"en\">\n");
        buf.append("<head>\n");
        buf.append("<title>CWHelper Shutdown Confirmation</title>\n");
        buf.append("<script>\n");
        buf.append("/* Version 1.3.0\n");
        buf.append("*\n");
        buf.append("*This page gets the user's confirmation of shutdown intent before execution\n");
        buf.append("*/\n");
        buf.append("\n");
        buf.append("var detailOn = false, numCaps = 0;\n");
        buf.append("\n");
        buf.append("function vID(Id) { // Shorthand for get element value by ID\n");
        buf.append("return document.getElementById(Id).value }\n");
        buf.append("\n");
        buf.append("function sID(Id, x) { // Shorthand for set element value by ID\n");
        buf.append("document.getElementById(Id).value = x }\n");
        buf.append("\n");
        buf.append("function KillCWHelper() { // Jump to /shutdown\n");
        buf.append("window.location.href= vID(\"destination\") + ':8181/shutdown' }\n");
        buf.append("\n");
        buf.append("function getJumpURL() {\n");
        buf.append("if (document.URL.substring(0,4).toLowerCase() == \"http\") { // Not running a file\n");
        buf.append("var x = document.URL.split(':')[1].substring(2)\n");
        buf.append("} else { x = \"localhost\" }; // Running a file, ignore the URL\n");
        buf.append("sID('destination', \"http://\" + x);\n");
        buf.append("document.getElementById('destination').innerHTML = vID(\"destination\"); // Display it\n");
        buf.append("listCaptures();\n");
        buf.append("}\n");
        buf.append("\n");
        buf.append("</script>\n");
        buf.append("</head>\n");
        buf.append("\n");
        buf.append("<body onload=\"getJumpURL();\">\n");
        buf.append("<small>CWHelper URL: <span id=\"destination\"></span></small>\n");
        buf.append("<h2>Shutting down CWHelper will <font color=\"#FF0000\">disable all HDHomerun</font> capture recordings<br>until it is restarted manually or by a scheduled CW_EPG auto run.</h2>\n");
        buf.append("<span id=\"capsWarn\"></span><br>\n");
        buf.append("\n");
        buf.append("<h2>Are you sure that this is OK?</h2>\n");
        buf.append("\n");
        buf.append("<input type=\"button\" value=\"Yes: Shutdown!\" onclick=\"KillCWHelper();\"> <input type=\"button\" value=\"No, cancel\" onclick=\"window.close();\">\n");
        buf.append("\n");
        buf.append("<span id=\"captureTable\"></span>\n");
        buf.append("<script>\n");
        buf.append("function listCaptures() { // use to list existing captures\n");
        buf.append("var xhttp = new XMLHttpRequest();\n");
        buf.append("xhttp.open(\"GET\", vID(\"destination\") + \":8181/captures\", true);\n");
        buf.append("xhttp.send();\n");
        buf.append("xhttp.onreadystatechange = function() {\n");
        buf.append("if (this.readyState == 4 && this.status == 200) {\n");
        buf.append("var x = this.responseText, text = \"\", y, warning;\n");
        buf.append("if (x.indexOf(\"<xml\") > -1) { // Got XML, success!\n");
        buf.append("x = getXML(x);\n");
        buf.append("y = x.getElementsByTagName(\"capture\");\n");
        buf.append("} else { // Got error response, show it above preserved table\n");
        buf.append("text += x.substring(x.indexOf(\"<br>\"),x.indexOf(\"ERROR\"))\n");
        buf.append("}\n");
        buf.append("var fclr, i, k, o = [];\n");
        buf.append("numCaps = y.length;\n");
        buf.append("if (numCaps == 0) text += '<h2>No Current Reservations</h2>'; else\n");
        buf.append("{\n");
        buf.append("numCaps = 0;\n");
        buf.append("for(k = 0; k < y.length; k++) if (y[k].getAttribute(\"tunerType\") == '2') numCaps++;\n");
        buf.append("if (numCaps > 0) {\n");
        buf.append("warning = '<i>The ';\n");
        buf.append("if (numCaps > 1) warning += numCaps + ' ';\n");
        buf.append("warning += '<font color=\"#FF0000\">red</font>-highlighted scheduled capture';\n");
        buf.append("if (numCaps > 1) warning += 's';\n");
        buf.append("warning += ' listed below <font color=\"#FF0000\">will not occur</font> unless CWHelper has been restarted.</i>';\n");
        buf.append("document.getElementById(\"capsWarn\").innerHTML = warning;\n");
        buf.append("}\n");
        buf.append("for (k=0; k<y.length; k++) o[k] = [Number(new Date(y[k].getAttribute(\"start\"))),y[k].getAttribute(\"sequence\")];\n");
        buf.append("o.sort(function(a, b){return a[0]-b[0]}); // List in start-time order\n");
        buf.append("text += '<h2>Current Reservation(s)</h2><form name=\"CapForm\"><table border=\"2\"><tr></tr>';\n");
        buf.append("for(k = 0; k < y.length; k++) { i = o[k][1];\n");
        buf.append("if (y[i].getAttribute(\"tunerType\") == '2') {fclr = '<font color=\"#FF0000\">';} else {fclr = '<font color=\"#000000\">';}\n");
        buf.append("text += '<tr>' +\n");
        buf.append("'<td>' + fclr + y[i].getAttribute(\"start\") + ' -- ' + y[i].getAttribute(\"end\") + '</td>' +\n");
        buf.append("'<td>' + fclr + y[i].getAttribute(\"channelName\") + '</td>' +\n");
        buf.append("'<td>' + fclr + y[i].getAttribute(\"alphaDescription\") + '</td>' +\n");
        buf.append("'<td>' + fclr + y[i].getAttribute(\"tuner\") + '</td>' +\n");
        buf.append("'<td>' + fclr + y[i].getAttribute(\"title\") + '</td>';\n");
        buf.append("if (detailOn) text += '<td><small>' + fclr + y[i].getAttribute(\"fileName\") + '</small></td>';\n");
        buf.append("text += '</font></tr>'\n");
        buf.append("}\n");
        buf.append("text += '<tr></tr></table></form>';\n");
        buf.append("if (detailOn) {text += '<input type=\"button\" value=\"Hide file names\" onclick=\"toggleDetail();\">'}\n");
        buf.append("else {text += '<input type=\"button\" value=\"Show file names\" onclick=\"toggleDetail()\">'};\n");
        buf.append("}\n");
        buf.append("document.getElementById(\"captureTable\").innerHTML = text\n");
        buf.append("}\n");
        buf.append("}\n");
        buf.append("while (this.readyState != 4 && this.status == 200) {} // wait for completion\n");
        buf.append("}\n");
        buf.append("\n");
        buf.append("function toggleDetail() {detailOn = !detailOn; listCaptures()}\n");
        buf.append("\n");
        buf.append("function getXML(x) {\n");
        buf.append("x = x.substring(x.indexOf(\"<xml\"),x.indexOf(\"</xml\")+6); //strip HTML from XML\n");
        buf.append("x = x.replace(/&/g,'&amp;'); // XML doesn't do ampersands!\n");
        buf.append("return new DOMParser().parseFromString(x,\"text/xml\");\n");
        buf.append("}\n");
        buf.append("\n");
        buf.append("</script>\n");
        buf.append("</body>\n");
        buf.append("</html>\n");
        return buf.toString();
    }

    public static void replacePage(String page) {
        HtmlShutdownDoc.page = page;
    }
}
