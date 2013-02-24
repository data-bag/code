<?xml version="1.0" encoding="UTF-8"?>
<!-- ====================================================================== 
    Copyright 2013 Konstantin Livitski

    This program is free software: you can redistribute it and/or modify
    it under the terms of the Data-bag Project License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    Data-bag Project License for more details.

    You should find a copy of the Data-bag Project License in the
    `data-bag.md` file in the `LICENSE` directory
    of this package or repository.  If not, see
    <http://www.livitski.name/projects/data-bag/license>. If you have any
    questions or concerns, contact the project's maintainers at
    <http://www.livitski.name/contact>. 
     ====================================================================== -->

<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:output
	method="xml"
	omit-xml-declaration="yes"
	indent="yes"
	/>

<xsl:param name="head-location" select="''" />

<xsl:template match="/html">

<xsl:if test="''=$head-location">
	<xsl:message terminate="yes">This transformation requires a non-empty "head-location" parameter.</xsl:message>
</xsl:if>

<xsl:text disable-output-escaping="yes"><![CDATA[<!DOCTYPE html>]]>
</xsl:text>

<html>
<head>
<xsl:copy-of select="head/*" />
<xsl:copy-of select="document($head-location)/head/*" />
</head>
<xsl:copy-of select="body" />
</html>

</xsl:template>

</xsl:transform>