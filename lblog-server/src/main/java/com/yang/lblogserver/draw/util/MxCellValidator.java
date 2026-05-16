package com.yang.lblogserver.draw.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class MxCellValidator {

    private static final int MAX_XML_SIZE = 1_000_000;
    private static final Set<String> STRUCTURAL_ATTRS = Set.of("edge", "parent", "source", "target", "vertex", "connectable");
    private static final Set<String> VALID_ENTITIES = Set.of("lt", "gt", "amp", "quot", "apos");
    private static final Set<String> VALID_DRAWIO_TAGS = Set.of(
            "mxfile", "diagram", "mxGraphModel", "root", "mxCell", "mxGeometry", "mxPoint", "Array", "Object", "mxRectangle"
    );

    public String validate(String xml) {
        if (xml == null || xml.isBlank()) return "Invalid XML: Empty input";

        if (xml.length() > MAX_XML_SIZE) {
            return null;
        }

        if (xml.trim().startsWith("<![CDATA[")) {
            return "Invalid XML: XML is wrapped in CDATA section";
        }

        String dupAttrError = checkDuplicateAttributes(xml);
        if (dupAttrError != null) return dupAttrError;

        String dupIdError = checkDuplicateIds(xml);
        if (dupIdError != null) return dupIdError;

        String tagMismatchError = checkTagMismatches(xml);
        if (tagMismatchError != null) return tagMismatchError;

        String entityError = checkEntityReferences(xml);
        if (entityError != null) return entityError;

        if (Pattern.compile("<mxCell[^>]*\\sid\\s*=\\s*[\"']\\s*[\"'][^>]*>").matcher(xml).find()) {
            return "Invalid XML: Found mxCell element(s) with empty id attribute";
        }

        String nestedError = checkNestedMxCells(xml);
        if (nestedError != null) return nestedError;

        return null;
    }

    public String sanitize(String xml) {
        String fixed = xml;
        List<String> fixes = new ArrayList<>();

        if (fixed == null || fixed.isBlank()) return xml;

        if (fixed.matches("(?s).*=\\\\\".*")) {
            fixed = fixed.replace("\\\"", "\"");
            fixed = fixed.replace("\\n", "\n");
        }

        if (fixed.trim().startsWith("<![CDATA[")) {
            fixed = fixed.replaceAll("(?s)^\\s*<!\\[CDATA\\[", "").replaceAll("(?s)\\]\\]>\\s*$", "");
        }

        int lastSelfClose = fixed.lastIndexOf("/>");
        int lastMxCellClose = fixed.lastIndexOf("</mxCell>");
        int lastValidEnd = Math.max(lastSelfClose, lastMxCellClose);
        if (lastValidEnd != -1) {
            int endOffset = lastMxCellClose > lastSelfClose ? 9 : 2;
            String suffix = fixed.substring(lastValidEnd + endOffset);
            if (suffix.matches("^(\\s*</[^>]+>)+\\s*$")) {
                fixed = fixed.substring(0, lastValidEnd + endOffset);
            }
        }

        int xmlStart = fixed.indexOf("<");
        if (xmlStart > 0 && !fixed.trim().startsWith("<")) {
            fixed = fixed.substring(xmlStart);
        }

        fixed = fixDuplicateAttributes(fixed);

        fixed = fixed.replaceAll("&(?!(?:lt|gt|amp|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)", "&amp;");

        String[][] doubleEscaped = {
                {"&ampquot;", "&quot;"}, {"&amplt;", "&lt;"}, {"&ampgt;", "&gt;"},
                {"&ampapos;", "&apos;"}, {"&ampamp;", "&amp;"}
        };
        for (String[] de : doubleEscaped) {
            fixed = fixed.replace(de[0], de[1]);
        }

        fixed = fixed.replaceAll("(\\s[a-zA-Z][a-zA-Z0-9_:-]*)=&quot;([^&\"]*?)&quot;", "$1=\"$2\"");

        fixed = fixed.replaceAll("</([a-zA-Z][a-zA-Z0-9]*)\\s*/>", "</$1>");

        fixed = fixed.replaceAll("(\"[^\"]*\")([a-zA-Z][a-zA-Z0-9_:-]*=)", "$1 $2");

        fixed = fixed.replaceAll(";([a-zA-Z]*[Cc]olor)=\"#", ";$1=#");

        Pattern ltInAttr = Pattern.compile("=\\s*\"[^\"]*<[^\"]*\"");
        Matcher ltMatcher = ltInAttr.matcher(fixed);
        if (ltMatcher.find()) {
            fixed = ltInAttr.matcher(fixed).replaceAll((m) -> {
                String value = m.group(1);
                String escaped = value.replace("<", "&lt;").replace(">", "&gt;");
                return "=\"" + escaped + "\"";
            });
        }

        Map<String, String> tagTypos = new LinkedHashMap<>();
        tagTypos.put("(?i)</mxElement>", "</mxCell>");
        tagTypos.put("</mxcell>", "</mxCell>");
        tagTypos.put("</mxgeometry>", "</mxGeometry>");
        tagTypos.put("</mxpoint>", "</mxPoint>");
        tagTypos.put("(?i)</mxgraphmodel>", "</mxGraphModel>");
        for (Map.Entry<String, String> entry : tagTypos.entrySet()) {
            fixed = fixed.replaceAll(entry.getKey(), entry.getValue());
        }

        fixed = fixed.replaceAll("(?i)<Cell(\\s)", "<mxCell$1");
        fixed = fixed.replaceAll("(?i)<Cell>", "<mxCell>");
        fixed = fixed.replaceAll("(?i)</Cell>", "</mxCell>");

        fixed = removeForeignTags(fixed);

        String tagBalanceResult = fixTagBalance(fixed);
        fixed = tagBalanceResult;

        return fixed;
    }

    public String wrapWithMxFile(String mxCellXml) {
        String ROOT_CELLS = "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>";

        if (mxCellXml == null || mxCellXml.isBlank()) {
            return "<mxfile><diagram name=\"Page-1\" id=\"page-1\"><mxGraphModel><root>" + ROOT_CELLS + "</root></mxGraphModel></diagram></mxfile>";
        }

        if (mxCellXml.contains("<mxfile")) {
            return mxCellXml;
        }

        if (mxCellXml.contains("<mxGraphModel")) {
            return "<mxfile><diagram name=\"Page-1\" id=\"page-1\">" + mxCellXml + "</diagram></mxfile>";
        }

        String content = mxCellXml;
        if (mxCellXml.contains("<root>")) {
            content = mxCellXml.replaceAll("</?root>", "").trim();
        }

        int lastSelfClose = content.lastIndexOf("/>");
        int lastMxCellClose = content.lastIndexOf("</mxCell>");
        int lastValidEnd = Math.max(lastSelfClose, lastMxCellClose);
        if (lastValidEnd != -1) {
            int endOffset = lastMxCellClose > lastSelfClose ? 9 : 2;
            String suffix = content.substring(lastValidEnd + endOffset);
            if (suffix.matches("^(\\s*</[^>]+>)*\\s*$")) {
                content = content.substring(0, lastValidEnd + endOffset);
            }
        }

        content = content.replaceAll("<mxCell[^>]*\\bid=[\"']0[\"'][^>]*(?:/>|></mxCell>)", "");
        content = content.replaceAll("<mxCell[^>]*\\bid=[\"']1[\"'][^>]*(?:/>|></mxCell>)", "");
        content = content.trim();

        return "<mxfile><diagram name=\"Page-1\" id=\"page-1\"><mxGraphModel><root>" + ROOT_CELLS + content + "</root></mxGraphModel></diagram></mxfile>";
    }

    public boolean isRealDiagram(String xml) {
        return MxCellHelper.isRealDiagram(xml);
    }

    // ========== Private helpers ==========

    private String checkDuplicateAttributes(String xml) {
        Pattern tagPattern = Pattern.compile("<[^>]+>");
        java.util.regex.Matcher tagMatcher = tagPattern.matcher(xml);
        while (tagMatcher.find()) {
            String tag = tagMatcher.group();
            Pattern attrPattern = Pattern.compile("\\s([a-zA-Z_:][a-zA-Z0-9_:.-]*)\\s*=");
            java.util.regex.Matcher attrMatcher = attrPattern.matcher(tag);
            Map<String, Integer> counts = new HashMap<>();
            while (attrMatcher.find()) {
                String name = attrMatcher.group(1);
                counts.merge(name, 1, Integer::sum);
            }
            List<String> dupes = counts.entrySet().stream()
                    .filter(e -> e.getValue() > 1 && STRUCTURAL_ATTRS.contains(e.getKey()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            if (!dupes.isEmpty()) {
                return "Invalid XML: Duplicate structural attribute(s): " + String.join(", ", dupes);
            }
        }
        return null;
    }

    private String checkDuplicateIds(String xml) {
        Pattern idPattern = Pattern.compile("\\bid\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = idPattern.matcher(xml);
        Map<String, Integer> ids = new HashMap<>();
        while (m.find()) {
            ids.merge(m.group(1), 1, Integer::sum);
        }
        List<String> dupes = ids.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> "'" + e.getKey() + "' (" + e.getValue() + "x)")
                .limit(3)
                .collect(Collectors.toList());
        if (!dupes.isEmpty()) {
            return "Invalid XML: Found duplicate ID(s): " + String.join(", ", dupes);
        }
        return null;
    }

    private String checkTagMismatches(String xml) {
        String noComments = xml.replaceAll("(?s)<!--.*?-->", "");
        Pattern tagPattern = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9:_-]*)[^>]*>");
        java.util.regex.Matcher m = tagPattern.matcher(noComments);
        Deque<String> stack = new ArrayDeque<>();
        while (m.find()) {
            boolean isClosing = "/".equals(m.group(1));
            String tagName = m.group(2);
            String full = m.group(0);
            boolean isSelfClosing = full.endsWith("/>");
            if (isClosing) {
                if (stack.isEmpty()) {
                    return "Invalid XML: Closing tag </" + tagName + "> without matching opening tag";
                }
                String expected = stack.pop();
                if (!expected.equalsIgnoreCase(tagName)) {
                    return "Invalid XML: Expected closing tag </" + expected + "> but found </" + tagName + ">";
                }
            } else if (!isSelfClosing) {
                stack.push(tagName);
            }
        }
        if (!stack.isEmpty()) {
            return "Invalid XML: Document has " + stack.size() + " unclosed tag(s): " + String.join(", ", stack);
        }
        return null;
    }

    private String checkEntityReferences(String xml) {
        String noComments = xml.replaceAll("(?s)<!--.*?-->", "");
        Pattern invalidEntity = Pattern.compile("&([a-zA-Z][a-zA-Z0-9]*);");
        java.util.regex.Matcher m = invalidEntity.matcher(noComments);
        while (m.find()) {
            if (!VALID_ENTITIES.contains(m.group(1))) {
                return "Invalid XML: Invalid entity reference: &" + m.group(1) + ";";
            }
        }
        return null;
    }

    private String checkNestedMxCells(String xml) {
        Pattern cellTagPattern = Pattern.compile("</?mxCell[^>]*>");
        java.util.regex.Matcher m = cellTagPattern.matcher(xml);
        int depth = 0;
        while (m.find()) {
            String tag = m.group();
            if (tag.startsWith("</mxCell>")) {
                depth = Math.max(0, depth - 1);
            } else if (!tag.endsWith("/>")) {
                boolean isLabelOrGeometry = Pattern.compile("\\sas\\s*=\\s*[\"'](valueLabel|geometry)[\"']").matcher(tag).find();
                if (!isLabelOrGeometry) {
                    depth++;
                    if (depth > 1) {
                        return "Invalid XML: Found nested mxCell tags";
                    }
                }
            }
        }
        return null;
    }

    private String fixDuplicateAttributes(String xml) {
        StringBuilder result = new StringBuilder();
        Pattern tagPattern = Pattern.compile("<[^>]+>");
        java.util.regex.Matcher tagMatcher = tagPattern.matcher(xml);
        int lastEnd = 0;

        while (tagMatcher.find()) {
            result.append(xml, lastEnd, tagMatcher.start());
            String tag = tagMatcher.group();
            String fixedTag = tag;

            for (String attr : STRUCTURAL_ATTRS) {
                Pattern attrRegex = Pattern.compile("\\s" + attr + "\\s*=\\s*[\"'][^\"']*[\"']", Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher attrMatcher = attrRegex.matcher(fixedTag);
                boolean first = true;
                StringBuilder newTag = new StringBuilder();
                int prev = 0;
                while (attrMatcher.find()) {
                    newTag.append(fixedTag, prev, attrMatcher.start());
                    if (first) {
                        newTag.append(attrMatcher.group());
                        first = false;
                    }
                    prev = attrMatcher.end();
                }
                newTag.append(fixedTag.substring(prev));
                fixedTag = newTag.toString();
            }

            result.append(fixedTag);
            lastEnd = tagMatcher.end();
        }

        result.append(xml.substring(lastEnd));
        return result.toString();
    }

    private String removeForeignTags(String xml) {
        StringBuilder result = new StringBuilder();
        Pattern tagPattern = Pattern.compile("<(/?[a-zA-Z][a-zA-Z0-9_]*)[^>]*>");
        java.util.regex.Matcher m = tagPattern.matcher(xml);
        int lastEnd = 0;

        while (m.find()) {
            String tagName = m.group(1).startsWith("/") ? m.group(1).substring(1) : m.group(1);
            if (VALID_DRAWIO_TAGS.contains(tagName)) {
                continue;
            }
            if (isInsideQuotes(xml, m.start())) {
                continue;
            }
            result.append(xml, lastEnd, m.start());
            lastEnd = m.end();
        }

        result.append(xml.substring(lastEnd));
        return result.toString();
    }

    private boolean isInsideQuotes(String str, int pos) {
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < pos && i < str.length(); i++) {
            char c = str.charAt(i);
            if (inQuote) {
                if (c == quoteChar) inQuote = false;
            } else if (c == '"' || c == '\'') {
                int j = i - 1;
                while (j >= 0 && Character.isWhitespace(str.charAt(j))) j--;
                if (j >= 0 && str.charAt(j) == '=') {
                    inQuote = true;
                    quoteChar = c;
                }
            }
        }
        return inQuote;
    }

    private String fixTagBalance(String xml) {
        String noComments = xml.replaceAll("(?s)<!--.*?-->", "");
        Pattern tagPattern = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9:_-]*)[^>]*>");
        java.util.regex.Matcher m = tagPattern.matcher(noComments);
        Deque<String> stack = new ArrayDeque<>();

        while (m.find()) {
            boolean isClosing = "/".equals(m.group(1));
            String tagName = m.group(2);
            String full = m.group(0);
            boolean isSelfClosing = full.endsWith("/>");
            if (isClosing) {
                if (!stack.isEmpty()) {
                    String expected = stack.peek();
                    if (expected.equalsIgnoreCase(tagName)) {
                        stack.pop();
                    }
                }
            } else if (!isSelfClosing) {
                if (isInsideQuotes(noComments, m.start())) continue;
                stack.push(tagName);
            }
        }

        StringBuilder result = new StringBuilder(xml.trim());
        while (!stack.isEmpty()) {
            result.append("\n</").append(stack.pop()).append(">");
        }

        return result.toString();
    }
}
