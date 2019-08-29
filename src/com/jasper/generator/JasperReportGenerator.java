package com.jasper.generator;


import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRCsvDataSource;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


/**
 * Generates PDF report using JasperReports lib
 *
 * @author Eugene Luzgin, August 2019
 */
public class JasperReportGenerator {

  static final Logger logger = Logger.getLogger(JasperReportGenerator.class);

  private static final int min_page_width = 555;
  private static final int col_width = 80;
  private static final int col_pad = 10;
  private static final int col_height = 20;

  /** copy the file to the output stream */
  public void writePDF(OutputStream pdfOut, File csvFile) throws IOException {
    try {
      // Read first line from CSV file as columns:
      BufferedReader csvReader = new BufferedReader(new FileReader(csvFile));
      String columnsLine = csvReader.readLine();
      String[] columns = columnsLine.split(",");
      Map<String, Integer> colAvgLen = new HashMap<>(columns.length);
      for (String column : columns) {
        colAvgLen.put(column, column.length());
      }
      String line;
      int counter = 0;
      while ((line = csvReader.readLine()) != null && counter < 10) {
        String[] values = line.split(",");
        for (int i = 0; i < columns.length; i++) {
          String col = columns[i];
          Integer len = (values[i] != null) ? values[i].length() : 0;
          if (colAvgLen.get(col) < len) colAvgLen.put(col, len);
        }
        counter++;
      }
      csvReader.close();
      Map<String, Float> colAvgPerWidth = convertAbsoluteAveragesToPercents(colAvgLen);
      // construct and run JasperReports:
      JasperPrint jprint = createJasperReport(csvFile, columns, colAvgPerWidth);
      JasperExportManager.exportReportToPdfStream(jprint, pdfOut);
    } catch (JRException jrex) {
      logger.error("JasperReports error", jrex);
      throw new IOException("Error generating PDF report", jrex);
    }
  }

  private Map<String, Float> convertAbsoluteAveragesToPercents(Map<String, Integer> colAvgLen) {
    Map<String, Float> colAvgPer = new HashMap<>(colAvgLen.size());
    Integer total = 0;
    for (String col : colAvgLen.keySet()) {
      total += colAvgLen.get(col);
    }
    for (String col : colAvgLen.keySet()) {
      Float percent = (100.0F * colAvgLen.get(col).floatValue()) / total;
      colAvgPer.put(col, percent);
    }
    return colAvgPer;
  }

  private JasperPrint createJasperReport(
      File csvFile, String[] columns, Map<String, Float> colAvgPerWidth)
      throws JRException, IOException {
    InputStream csvIn = new FileInputStream(csvFile);
    JRCsvDataSource jrCsvDataSource = new JRCsvDataSource(csvIn);
    jrCsvDataSource.setUseFirstRowAsHeader(true);
    jrCsvDataSource.setColumnNames(columns);
    String xmlReport = createJasperXMLReport(columns, colAvgPerWidth);
    InputStream inputStream =
        new ByteArrayInputStream(xmlReport.getBytes(Charset.forName("UTF-8")));
    JasperReport report = JasperCompileManager.compileReport(inputStream);
    Map<String, Object> parameters = new HashMap<>();
    return JasperFillManager.fillReport(report, parameters, jrCsvDataSource);
  }


  private String createJasperXMLReport(String[] columns, Map<String, Float> colAvgPerWidth) {
    Map<String, String> propUUIDMap = new HashMap<>(columns.length);
    int columnsTotalWidth = (col_width + col_pad) * columns.length;
    Map<String, Integer> columnWidthMap = new HashMap<>(columns.length);
    for (String column : colAvgPerWidth.keySet()) {
      Float ratio = colAvgPerWidth.get(column) / 100;
      Integer colWidth = Math.round(columnsTotalWidth * ratio);
      columnWidthMap.put(column, colWidth);
    }
    int pageWidth = columnsTotalWidth + 40;
    if (pageWidth < min_page_width) pageWidth = min_page_width;
    UUID reportUUID = UUID.randomUUID();
    StringBuffer sb = new StringBuffer();
    sb.append(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!-- Created with Jaspersoft Studio version 6.9.0.final using JasperReports Library version 6.9.0-cb8f9004be492ccc537180b49c026951f4220bf3  -->\n"
            + "<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
            + "xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\" name=\"PDF Report\" "
            + "pageWidth=\""
            + pageWidth
            + "\" pageHeight=\"842\" columnWidth=\""
            + columnsTotalWidth
            + "\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\" uuid=\""
            + reportUUID
            + "\">\n"
            + "\t<property name=\"com.jaspersoft.studio.data.defaultdataadapter\" value=\"CSV Adapter\"/>\n"
            + "\t<queryString>\n"
            + "\t\t<![CDATA[]]>\n"
            + "\t</queryString>\n");
    for (String column : columns) {
      sb.append("<field name=\"");
      sb.append(column);
      sb.append("\" class=\"java.lang.String\"/>\n");
    }
    sb.append(
        "\t<background>\n"
            + "\t\t<band splitType=\"Stretch\"/>\n"
            + "\t</background>\n"
            + "\t<title>\n"
            + "\t\t<band height=\"79\" splitType=\"Stretch\"/>\n"
            + "\t</title>\n"
            + "\t<pageHeader>\n"
            + "\t\t<band height=\"35\" splitType=\"Stretch\"/>\n"
            + "\t</pageHeader>\n"
            + "\t<columnHeader>\n"
            + "\t\t<band height=\"35\" splitType=\"Stretch\">\n");
    int x = 0;
    for (String column : columns) {
      UUID elemUUID = UUID.randomUUID();
      UUID propUUID = UUID.randomUUID();
      propUUIDMap.put(column, propUUID.toString());
      sb.append(
          "\t\t\t<staticText>\n"
              + "\t\t\t\t<reportElement x=\""
              + x
              + "\" y=\"0\" width=\""
              + columnWidthMap.get(column)
              + "\" height=\""
              + col_height
              + "\" uuid=\"");
      sb.append(elemUUID.toString());
      sb.append("\">\n");
      sb.append(
          "\t\t\t\t\t<property name=\"com.jaspersoft.studio.spreadsheet.connectionID\" value=\"");
      sb.append(propUUID.toString());
      sb.append("\"/>\n");
      sb.append("\t\t\t\t</reportElement>\n");
      sb.append("\t\t\t\t<text><![CDATA[");
      sb.append(column);
      sb.append("]]></text>\n");
      sb.append("\t\t\t</staticText>\n");
      x += columnWidthMap.get(column) + col_pad;
    }
    sb.append(
        "\t\t</band>\n"
            + "\t</columnHeader>\n"
            + "\t<detail>\n"
            + "\t\t<band height=\"47\" splitType=\"Stretch\">\n");
    x = 0;
    for (String column : columns) {
      UUID elemUUID = UUID.randomUUID();
      String propUUID = propUUIDMap.get(column);
      sb.append(
          "\t\t\t<textField>\n"
              + "\t\t\t\t<reportElement x=\""
              + x
              + "\" y=\"0\" width=\""
              + columnWidthMap.get(column)
              + "\" height=\""
              + col_height
              + "\" uuid=\"");
      sb.append(elemUUID.toString());
      sb.append("\">\n");
      sb.append(
          "\t\t\t\t\t<property name=\"com.jaspersoft.studio.spreadsheet.connectionID\" value=\"");
      sb.append(propUUID);
      sb.append("\"/>\n");
      sb.append("\t\t\t\t</reportElement>\n" + "\t\t\t\t<textFieldExpression><![CDATA[$F{");
      sb.append(column);
      sb.append("}]]></textFieldExpression>\n" + "\t\t\t</textField>\n");
      x += columnWidthMap.get(column) + col_pad;
    }
    sb.append(
        "\t\t</band>\n"
            + "\t</detail>\n"
            + "\t<pageFooter>\n"
            + "\t\t<band height=\"54\" splitType=\"Stretch\"/>\n"
            + "\t</pageFooter>\n"
            + "\t<summary>\n"
            + "\t\t<band height=\"42\" splitType=\"Stretch\"/>\n"
            + "\t</summary>\n"
            + "</jasperReport>");
    return sb.toString();
  }
}


