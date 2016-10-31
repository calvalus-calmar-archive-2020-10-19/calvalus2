<?xml version="1.0"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="2.0">

    <xsl:output method="html" encoding="UTF-8" indent="no"/>

    <xsl:template match="listing">
        <html>
            <head>
                <title>WasMon-CT Calvalus Staging</title>
                <link type="text/css" rel="stylesheet" href="/calvalus/calvalus.css"/>
                <meta http-equiv="content-type" content="text/html; charset=UTF-8"/>
                <style>
                    body {
                    font-family : "Lucida Calligraphy",Calibri,Verdana,Arial,sans-serif;
                    color : black;
                    background-color : white;
                    }
                    b {
                    color : white;
                    background-color : #0086b2;
                    }
                    a {
                    color : black;
                    }
                </style>
            </head>
            <body>
                <table class="headerPanel">
                    <tr>
                        <td width="200">
                            <a href="http://www.brockmann-consult.de/"><img src="/calbfg/images/bfg_logoFL1_A4_rgb.jpg" width="200" alt="BfG logo"/></a>
                        </td>
                        <td>
                            <h1 class="title">
                <style>
                    body {
                    font-family : "Lucida Calligraphy",Calibri,Verdana,Arial,sans-serif;
                    color : black;
                    background-color : white;
                    }
                    b {
                    color : white;
                    background-color : #0086b2;
                    }
                    a {
                    color : black;
                    }
                </style>WasMon-CT Calvalus</h1>

                            <h2 class="subTitle">Calvalus portal for on-demand processing</h2>
                        </td>
                    </tr>
                </table>

                <hr/>

                <p>Calvalus staging area
                    <xsl:value-of select="substring-after(substring-after(@directory,'/staging/'),'/')"/>
                </p>
                <it>Use FTP on ftp.brockmann-consult.de with your portal credentials for bulk download.</it>

                <hr/>
                <table cellspacing="0"
                       width="100%"
                       cellpadding="5"
                       align="center">
                    <tr>
                        <th align="left">Filename</th>
                        <th align="center">Size</th>
                        <th align="right">Last Modified</th>
                    </tr>
                    <xsl:apply-templates select="entries"/>
                </table>
                <xsl:apply-templates select="readme"/>
                <hr/>
                <p class="copyright">Calvalus - Version 2.10-SNAPSHOT, &#169; 2016 Brockmann Consult GmbH</p>
            </body>
        </html>
    </xsl:template>


    <xsl:template match="entries">
        <xsl:apply-templates select="entry"/>
    </xsl:template>

    <xsl:template match="readme">
        <hr size="1"/>
        <pre>
            <xsl:apply-templates/>
        </pre>
    </xsl:template>

    <xsl:template match="entry">
        <tr>
            <td align="left">
                <xsl:variable name="urlPath" select="@urlPath"/>
                <a href="{$urlPath}">
                    <tt>
                        <xsl:apply-templates/>
                    </tt>
                </a>
            </td>
            <td align="right">
                <tt>
                    <xsl:value-of select="@size"/>
                </tt>
            </td>
            <td align="right">
                <tt>
                    <xsl:value-of select="@date"/>
                </tt>
            </td>
        </tr>
    </xsl:template>

</xsl:stylesheet>