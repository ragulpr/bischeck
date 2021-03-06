<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:template match="/">
<html>
<link rel="stylesheet" type="text/css" href="bischeck.css" />   

<head><title>bischeck url property configuration</title></head>
<body>
<table class="property">
    <CAPTION CLASS="property">
        URL services
    </CAPTION>

    <thead>
        <tr>
            <th>Key</th>
            <th>Value</th>
        </tr>
    </thead>

<xsl:for-each select="urlservices/urlproperty">
    
        <tr>
            <td><xsl:value-of select="key"/> </td>
            <td><xsl:value-of select="value"/> </td>
        </tr>
    

</xsl:for-each>
</table>

</body>
</html>
</xsl:template>
    

</xsl:stylesheet>