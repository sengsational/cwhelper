/*
 * Created on Apr 8, 2018
 *
 */
package org.cwepg.svc;

public class HtmlSettingsDoc {

    public static String page;
    
    public static String get() {
        if (HtmlSettingsDoc.page != null) return HtmlSettingsDoc.page;
        else return getDefault();
    }
    
    public static String getDefault() {
        StringBuffer buf = new StringBuffer();
        buf.append("<!DOCTYPE html>\n");
        buf.append("<html lang=\"en\">\n");
        buf.append("<head>\n");
        buf.append("<title>CWHelper Configuration Settings</title>\n");
        buf.append("<script>\n");
        buf.append("/* Version 1.1.8\n");
        buf.append("*\n");
        buf.append("* This page allows user input for various config parameters\n");
        buf.append("*\n");
        buf.append("*/\n");
        buf.append("// Global variables\n");
        buf.append("var hlpTxt = {};\n");
        buf.append("\n");
        buf.append("function vID(Id) { // Shorthand for get element value by ID\n");
        buf.append("return document.getElementById(Id).value }\n");
        buf.append("\n");
        buf.append("function sID(Id, x) { // Shorthand for set element value by ID\n");
        buf.append("document.getElementById(Id).value = x }\n");
        buf.append("\n");
        buf.append("function getdest() { // Determine current URL for fetching data\n");
        buf.append("if (document.URL.substring(0,4).toLowerCase() == \"http\") { // Not running a file\n");
        buf.append("var x = document.URL.split(':')[1].substring(2)\n");
        buf.append("} else { x = \"localhost\" }; // Running a file, ignore the URL\n");
        buf.append("sID('destination', \"http://\" + x);\n");
        buf.append("document.getElementById('destination').innerHTML = vID(\"destination\"); // Display it\n");
        buf.append("getHelp(); // Get descriptions\n");
        buf.append("// Show the table after 500-ms delay (to finish getHelp??) otherwise descriptions are sometimes blank\n");
        buf.append("setTimeout(function(){setConfiguration('')}, 500);\n");
        buf.append("}\n");
        buf.append("\n");
        buf.append("function setIt(paramName, paramValue) {\n");
        buf.append("setConfiguration(\"?\" + paramName + \"=\" + paramValue.toLowerCase());\n");
        buf.append("setConfiguration(\"\"); // call again to confirm setting change\n");
        buf.append("}\n");
        buf.append("</script>\n");
        buf.append("\n");
        buf.append("</head>\n");
        buf.append("\n");
        buf.append("<body onload=\"getdest();\">\n");
        buf.append("<small>CWHelper URL: <span id=\"destination\"></span><br>\n");
        buf.append("<span id=\"version\"></span></small><br>\n");
        buf.append("<h2>Configuration<br></h2>\n");
        buf.append("\n");
        buf.append("<span id=\"configTable\"></span> <!-- This will display config settings and allow edits where possible -->\n");
        buf.append("\n");
        buf.append("<script>\n");
        buf.append("function getXML(x) {\n");
        buf.append("x = x.substring(x.indexOf(\"<xml\"),x.indexOf(\"</xml\")+6); //strip HTML from XML\n");
        buf.append("x = x.replace(/&/g,'&amp;'); // XML doesn't do ampersands!\n");
        buf.append("return x; // new DOMParser().parseFromString(x,\"text/xml\"); <<<Does not work in all browsers!\n");
        buf.append("}\n");
        buf.append("\n");
        buf.append("/*\n");
        buf.append("NOTE TO FUTURE SELF: setConfiguration makes use of the fact that\n");
        buf.append("the /set command also provides the /properties data when no setting\n");
        buf.append("is specified.\n");
        buf.append("*/\n");
        buf.append("function setConfiguration(m) { // use to list & change parameters\n");
        buf.append("var xhttp = new XMLHttpRequest();\n");
        buf.append("xhttp.onreadystatechange = function() {\n");
        buf.append("if (this.readyState == 4 && this.status == 200) {\n");
        buf.append("var x = this.responseText;\n");
        buf.append("var text = \"\";\n");
        buf.append("if (x.indexOf(\"<xml\") > -1) { // Got XML properties list, success!\n");
        buf.append("x = getXML(x);\n");
        buf.append("/* Enumerate the list of Properties in y\n");
        buf.append("(since DOMParser doesn't play well in all browsers we'll just parse the string x)\n");
        buf.append("Typical x:\n");
        buf.append("\"<xml id=\\\"properties\\\">\\n <capture dataPath=\\\"C:\\\\ProgramData\\\\CW_EPG\\\\\\\" hdhrPath=\\\"C:\\\\Program Files\\\\Silicondust\\\n");
        buf.append("\\HDHomeRun\\\\\\\" sleepManaged=\\\"false\\\" leadTime=\\\"90\\\" fusionLeadTime=\\\"120\\\" simulate=\\\"false\\\" endFusionWatchEvents=\\\"false\\\"\n");
        buf.append("trayIcon=\\\"true\\\" shortenExternalRecordingsSeconds=\\\"15\\\" discoverRetries=\\\"5\\\" discoverDelay=\\\"1000\\\" clockOffset=\\\"0\\\"\n");
        buf.append("myhdWakeup=\\\"false\\\" hdhrRecordMonitorSeconds=\\\"-1\\\" hdhrBadRecordingPercent=\\\"5.0\\\" alternateChannels=\n");
        buf.append("\\\"11.3^11.1,60.1^0,60.4^0,60.2^0\\\" pollIntervalSeconds=\\\"60\\\" recreateIconUponWakeup=\\\"true\\\" allTraditionalHdhr=\\\"false\\\"\n");
        buf.append("rerunDiscover=\\\"false\\\" />\\n</xml>\"\n");
        buf.append("*/\n");
        buf.append("var y = {};\n");
        buf.append("var k = x.indexOf(\"<capture \") + 9;\n");
        buf.append("if (k > 8) {\n");
        buf.append("while (x.indexOf(\"=\", k) > 0) { // Still have more parameters\n");
        buf.append("var m = x.indexOf(\"=\",k);\n");
        buf.append("var name = x.substring(k,m); // Get parameter name\n");
        buf.append("k = m + 2; m = x.indexOf(\"\\\"\",k);\n");
        buf.append("var value = x.substring(k,m); // Get parameter value\n");
        buf.append("k = m + 2;\n");
        buf.append("y[name] = value; // Construct object of parameters\n");
        buf.append("}\n");
        buf.append("}\n");
        buf.append("} else { // Got error response, show it above preserved table\n");
        buf.append("text += x.substring(x.indexOf(\"<br>\"),x.indexOf(\"ERROR\"))\n");
        buf.append("}\n");
        buf.append("if (y.length == 0) text += '<h2>No Parameters</h2>'; else\n");
        buf.append("{ // Construct table of Properties, values, and Help-page descriptions where avail.\n");
        buf.append("var pname;\n");
        buf.append("text += '<table border=\"2\" style=\"text-size-adjust: none\"><tr><th>Parameter</th>' +\n");
        buf.append("'<th>Value</th><th>Description</th></tr>';\n");
        buf.append("for(name in y) {\n");
        buf.append("text += '<tr><td> ' + name + '</td><td><input type=\"text\" style=\"text-align: center\" id=\"p' +\n");
        buf.append("name + '\" onchange=\"setIt(\\'' + name + '\\', p' + name + '.value);\" value=\"' + y[name] + '\"></td><td><small>';\n");
        buf.append("if (hlpTxt[name.toLowerCase()]) { // Add any available descriptive text\n");
        buf.append("text += hlpTxt[name.toLowerCase()];\n");
        buf.append("} else {\n");
        buf.append("text += ' ';\n");
        buf.append("}\n");
        buf.append("text += '</small></td></tr>';\n");
        buf.append("}\n");
        buf.append("text += '<tr></tr></table>';\n");
        buf.append("}\n");
        buf.append("document.getElementById(\"configTable\").innerHTML = text // Show the table\n");
        buf.append("}\n");
        buf.append("}\n");
        buf.append("xhttp.open(\"GET\", vID(\"destination\") + \":8181/set\" + m, true);\n");
        buf.append("xhttp.send();\n");
        buf.append("while (this.readyState != 4 && this.status == 200) {} // wait for completion\n");
        buf.append("}\n");
        buf.append("\n");
        buf.append("function getHelp() { // Fetch /help page descriptions of /set params\n");
        buf.append("var xhttp = new XMLHttpRequest();\n");
        buf.append("xhttp.onreadystatechange = function() {\n");
        buf.append("if (this.readyState == 4 && this.status == 200) {\n");
        buf.append("var x = this.responseText, k = 0;\n");
        buf.append("k = x.indexOf(\"Version:\", k); // x has page text, k is at Version #\n");
        buf.append("document.getElementById(\"version\").innerHTML =\n");
        buf.append("x.substring(k, x.indexOf(\"</\", k)); // Keep version number\n");
        buf.append("while (x.indexOf(\"<tr><td>/set</td><td>\", k) > -1) { // Still have \"/set\" items after k\n");
        buf.append("k = x.indexOf(\"<tr><td>/set</td><td>\", k)+21; // Position at a \"/set\"\n");
        buf.append("hName = x.substring(k,x.indexOf(\"</td>\", k)); // Copy name text\n");
        buf.append("k = x.indexOf(\"</td>\", k) + 9; // Advance to end of name\n");
        buf.append("k = x.indexOf(\"</td>\", k) + 9; // Skip the value field\n");
        buf.append("hTxt = x.substring(k,x.indexOf(\"</td>\", k)); // Copy description text\n");
        buf.append("k = x.indexOf(\"</td>\", k) + 9; // Advance to end of description\n");
        buf.append("hlpTxt[hName.toLowerCase()] = hTxt; // Add the new /set property's description\n");
        buf.append("}\n");
        buf.append("}\n");
        buf.append("}\n");
        buf.append("xhttp.open(\"GET\", vID(\"destination\") + \":8181/help\", true);\n");
        buf.append("xhttp.send();\n");
        buf.append("while (this.readyState != 4 && this.status == 200) {} // wait for completion\n");
        buf.append("}\n");
        buf.append("\n");
        buf.append("</script>\n");
        buf.append("</body>\n");
        buf.append("</html>\n");
        return buf.toString();
    }

    public static void replacePage(String page) {
        HtmlSettingsDoc.page = page;
    }
}
