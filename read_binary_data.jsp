<%@ page import="java.io.*" %>
<HTML>
    <HEAD>
        <TITLE>Reading Binary Data</TITLE>
    </HEAD>

    <BODY>
        <H1>Reading Binary Data</H1>
        This page reads binary data from a file.
        <BR>
        Read this data:
        <BR>
        <BR>

        <%
            String file = application.getRealPath("/") + "test.dat";
            FileInputStream fileinputstream = new FileInputStream(file);

            int numberBytes = fileinputstream.available();
            byte bytearray[] = new byte[numberBytes];

            fileinputstream.read(bytearray);

            for(int i = 0; i < numberBytes; i++){
                out.println(bytearray[i]);
            }

            fileinputstream.close();
        %>
    </BODY>
</HTML>
