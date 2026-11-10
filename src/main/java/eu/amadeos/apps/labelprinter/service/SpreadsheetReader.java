package eu.amadeos.apps.labelprinter.service;

import eu.amadeos.apps.labelprinter.domain.DataTable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the first sheet of an OpenDocument ({@code .ods}) or Office Open XML ({@code .xlsx}) spreadsheet, the
 * cells as the spreadsheet shows them. Both are ZIP files with XML inside, so no spreadsheet library is needed.
 */
public final class SpreadsheetReader {
    private static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    private static final String TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    private static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    private static final String SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    /** Repeated empty rows and columns (a sheet's end) are not expanded beyond this. */
    private static final int MAX_REPEAT = 1000;
    private static final long MAX_ENTRY = 64L * 1024 * 1024;

    private SpreadsheetReader() {
    }

    public static boolean handles(String fileName) {
        String n = fileName.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".ods") || n.endsWith(".xlsx");
    }

    public static DataTable read(byte[] zip, String fileName, boolean header) throws IOException {
        Map<String, byte[]> entries = entries(zip);
        List<List<String>> rows;
        if (entries.containsKey("content.xml")) rows = ods(parse(entries.get("content.xml")));
        else if (entries.containsKey("xl/workbook.xml")) rows = xlsx(entries);
        else throw new IOException("Not a spreadsheet: " + fileName);
        return CsvReader.table(rows, fileName, header);
    }

    private static Map<String, byte[]> entries(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals("content.xml") || name.startsWith("xl/")) entries.put(name, bytes(in));
            }
        }
        return entries;
    }

    private static byte[] bytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_ENTRY) throw new IOException("Spreadsheet too large");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static Document parse(byte[] xml) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | org.xml.sax.SAXException e) {
            throw new IOException("Cannot read the spreadsheet: " + e.getMessage(), e);
        }
    }

    // ---- OpenDocument ------------------------------------------------------------------------------------------

    private static List<List<String>> ods(Document doc) {
        List<List<String>> rows = new ArrayList<>();
        NodeList tables = doc.getElementsByTagNameNS(TABLE, "table");
        if (tables.getLength() == 0) return rows;
        Element table = (Element) tables.item(0);
        for (Element row : children(table, TABLE, "table-row", "table-rows", "table-header-rows", "table-row-group")) {
            List<String> cells = new ArrayList<>();
            for (Node n = row.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element cell) || !TABLE.equals(cell.getNamespaceURI())) continue;
                if (!cell.getLocalName().equals("table-cell") && !cell.getLocalName().equals("covered-table-cell")) {
                    continue;
                }
                int repeat = repeat(cell.getAttributeNS(TABLE, "number-columns-repeated"));
                String text = odsText(cell);
                for (int i = 0; i < repeat; i++) cells.add(text);
            }
            trimEnd(cells);
            int repeat = repeat(row.getAttributeNS(TABLE, "number-rows-repeated"));
            for (int i = 0; i < repeat; i++) rows.add(cells);
        }
        while (!rows.isEmpty() && rows.getLast().isEmpty()) rows.removeLast();
        return rows;
    }

    /** The rows of a table, also inside header-row and row-group elements. */
    private static List<Element> children(Element parent, String ns, String rowName, String... groups) {
        List<Element> rows = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e) || !ns.equals(e.getNamespaceURI())) continue;
            if (e.getLocalName().equals(rowName)) rows.add(e);
            else if (List.of(groups).contains(e.getLocalName())) rows.addAll(children(e, ns, rowName, groups));
        }
        return rows;
    }

    private static String odsText(Element cell) {
        StringBuilder sb = new StringBuilder();
        NodeList ps = cell.getElementsByTagNameNS(TEXT, "p");
        for (int i = 0; i < ps.getLength(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(odsParagraph(ps.item(i)));
        }
        if (sb.isEmpty()) {
            String value = cell.getAttributeNS(OFFICE, "value");
            if (!value.isEmpty()) return value;
        }
        return sb.toString();
    }

    private static String odsParagraph(Node p) {
        StringBuilder sb = new StringBuilder();
        for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.TEXT_NODE) sb.append(n.getNodeValue());
            else if (n instanceof Element e && TEXT.equals(e.getNamespaceURI())) {
                switch (e.getLocalName()) {
                    case "s" -> sb.append(" ".repeat(Math.max(1, repeat(e.getAttributeNS(TEXT, "c")))));
                    case "tab" -> sb.append('\t');
                    case "line-break" -> sb.append('\n');
                    default -> sb.append(odsParagraph(e));
                }
            }
        }
        return sb.toString();
    }

    private static int repeat(String value) {
        if (value == null || value.isEmpty()) return 1;
        try {
            return Math.max(1, Math.min(MAX_REPEAT, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static void trimEnd(List<String> cells) {
        while (!cells.isEmpty() && cells.getLast().isEmpty()) cells.removeLast();
    }

    // ---- Office Open XML ---------------------------------------------------------------------------------------

    private static List<List<String>> xlsx(Map<String, byte[]> entries) throws IOException {
        List<String> shared = new ArrayList<>();
        if (entries.containsKey("xl/sharedStrings.xml")) {
            Document doc = parse(entries.get("xl/sharedStrings.xml"));
            NodeList items = doc.getElementsByTagNameNS(SHEET, "si");
            for (int i = 0; i < items.getLength(); i++) {
                StringBuilder sb = new StringBuilder();
                NodeList ts = ((Element) items.item(i)).getElementsByTagNameNS(SHEET, "t");
                for (int j = 0; j < ts.getLength(); j++) sb.append(ts.item(j).getTextContent());
                shared.add(sb.toString());
            }
        }
        byte[] sheet = firstSheet(entries);
        if (sheet == null) return List.of();
        Document doc = parse(sheet);
        TreeMap<Integer, List<String>> rows = new TreeMap<>();
        NodeList rs = doc.getElementsByTagNameNS(SHEET, "row");
        int next = 0;
        for (int i = 0; i < rs.getLength(); i++) {
            Element row = (Element) rs.item(i);
            int r = row.hasAttribute("r") ? Integer.parseInt(row.getAttribute("r")) - 1 : next;
            next = r + 1;
            List<String> cells = new ArrayList<>();
            NodeList cs = row.getElementsByTagNameNS(SHEET, "c");
            for (int j = 0; j < cs.getLength(); j++) {
                Element c = (Element) cs.item(j);
                int col = c.hasAttribute("r") ? column(c.getAttribute("r")) : cells.size();
                while (cells.size() < col) cells.add("");
                cells.add(xlsxValue(c, shared));
            }
            trimEnd(cells);
            if (r < 100_000) rows.put(r, cells);
        }
        List<List<String>> list = new ArrayList<>();
        if (rows.isEmpty()) return list;
        for (int r = 0; r <= rows.lastKey(); r++) list.add(rows.getOrDefault(r, List.of()));
        return list;
    }

    private static byte[] firstSheet(Map<String, byte[]> entries) {
        if (entries.containsKey("xl/worksheets/sheet1.xml")) return entries.get("xl/worksheets/sheet1.xml");
        return entries.keySet().stream().filter(n -> n.startsWith("xl/worksheets/") && n.endsWith(".xml")).sorted()
                .findFirst().map(entries::get).orElse(null);
    }

    private static String xlsxValue(Element c, List<String> shared) {
        String type = c.getAttribute("t");
        if (type.equals("inlineStr")) {
            StringBuilder sb = new StringBuilder();
            NodeList ts = c.getElementsByTagNameNS(SHEET, "t");
            for (int i = 0; i < ts.getLength(); i++) sb.append(ts.item(i).getTextContent());
            return sb.toString();
        }
        NodeList vs = c.getElementsByTagNameNS(SHEET, "v");
        if (vs.getLength() == 0) return "";
        String v = vs.item(0).getTextContent();
        if (type.equals("s")) {
            try {
                int i = Integer.parseInt(v.strip());
                return i >= 0 && i < shared.size() ? shared.get(i) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if (type.equals("b")) return v.equals("1") ? "TRUE" : "FALSE";
        // Whole numbers without ".0", so postcodes and house numbers read as written.
        if (v.matches("-?\\d+\\.0+")) return v.substring(0, v.indexOf('.'));
        return v;
    }

    /** {@code C7} is column 2 (from 0). */
    static int column(String ref) {
        int col = 0;
        for (int i = 0; i < ref.length() && Character.isLetter(ref.charAt(i)); i++) {
            col = col * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
        }
        return Math.max(0, col - 1);
    }
}
