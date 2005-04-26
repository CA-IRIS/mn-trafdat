
                            WebDocs README.TXT

    WebDocs is a suite of servlet appications used to generate Real-time traffic
information documents for the MnDOT website.  Currently it produces a flow map,
an image map, an incident table, atp reports and a table containing speed
information.  The associated application, FtpEngine, makes requests to the
webdocs application for each of these files and copies them to the web server
on a regular basis.
    The image application generates a flow map ("image/png" HTTP response)
that is, by default, 600x600 pixels in size. Cients can request the full map or any
portion thereof.
    The imagemap application generates a "text/html" repsonse that contains
an HTML <map> object containing incident information for the incidents that
are within the associated map boundaries.
    The Speed table application generates a "text/html" HTTP repsonse
that is
    All configurable settings can be changed in the webdocs.properties
file.  The program must be restarted after any changes are made.

2-24-2004 WebDocs is dependent on JDK1.4.
