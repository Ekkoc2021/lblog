package com.yang.lblogserver.draw.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Inflater;

public class MxCellHelper {

    private static final int MIN_REAL_DIAGRAM_LENGTH = 300;

    public static boolean isRealDiagram(String xml) {
        return xml != null && xml.length() > MIN_REAL_DIAGRAM_LENGTH;
    }

    public static boolean isXmlComplete(String xml) {
        String trimmed = xml == null ? "" : xml.trim();
        if (trimmed.isEmpty()) return false;

        int lastSelfClose = trimmed.lastIndexOf("/>");
        int lastMxCellClose = trimmed.lastIndexOf("</mxCell>");
        int lastValidEnd = Math.max(lastSelfClose, lastMxCellClose);

        if (lastValidEnd == -1) return false;

        int endOffset = lastMxCellClose > lastSelfClose ? 9 : 2;
        String suffix = trimmed.substring(lastValidEnd + endOffset);

        return suffix.matches("^(\\s*</[^>]+>)*\\s*$");
    }

    public static String formatXml(String xml) {
        StringBuilder formatted = new StringBuilder();
        int pad = 0;
        String indent = "  ";

        String compact = xml.replaceAll(">\\s*<", "><").trim();
        String[] parts = compact.split("(?=<)|(?<=>)");

        for (int i = 0; i < parts.length; i++) {
            String node = parts[i].trim();
            if (node.isEmpty()) continue;

            if (node.matches("^</\\w.*")) {
                pad = Math.max(0, pad - 1);
                formatted.append(indent.repeat(pad)).append(node).append("\n");
            } else if (node.matches("^<\\w[^>]*[^/]>.*$")) {
                formatted.append(indent.repeat(pad)).append(node);
                if (i + 1 < parts.length && parts[i + 1].trim().startsWith("<")) {
                    formatted.append("\n");
                    if (!node.matches("^<\\w[^>]*/>$")) {
                        pad++;
                    }
                }
            } else if (node.matches("^<\\w[^>]*/>$")) {
                formatted.append(indent.repeat(pad)).append(node).append("\n");
            } else if (node.startsWith("<")) {
                formatted.append(indent.repeat(pad)).append(node).append("\n");
            } else {
                formatted.append(node);
            }
        }

        return formatted.toString().trim();
    }

    public static String extractDiagramXml(String svgBase64) {
        try {
            String base64Data = svgBase64;
            if (svgBase64.startsWith("data:image/svg+xml;base64,")) {
                base64Data = svgBase64.substring(26);
            }

            byte[] svgBytes = Base64.getDecoder().decode(base64Data);
            String svgString = new String(svgBytes, StandardCharsets.UTF_8);

            Document svgDoc = Jsoup.parse(svgString, "", Parser.xmlParser());
            Element svgElement = svgDoc.selectFirst("svg");
            if (svgElement == null) throw new RuntimeException("No SVG element found");

            String encodedContent = svgElement.attr("content");
            if (encodedContent == null || encodedContent.isEmpty()) {
                throw new RuntimeException("SVG element does not have a 'content' attribute");
            }

            Document xmlDoc = Jsoup.parse(encodedContent, "", Parser.xmlParser());
            Element diagramElement = xmlDoc.selectFirst("diagram");
            if (diagramElement == null) throw new RuntimeException("No diagram element found");

            String base64EncodedData = diagramElement.text();
            byte[] compressed = Base64.getDecoder().decode(base64EncodedData);

            Inflater inflater = new Inflater(true);
            inflater.setInput(compressed);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int len = inflater.inflate(buf);
                baos.write(buf, 0, len);
            }
            inflater.end();
            String decompressed = baos.toString(StandardCharsets.UTF_8);

            return URLDecoder.decode(decompressed, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract diagram XML: " + e.getMessage(), e);
        }
    }
}
