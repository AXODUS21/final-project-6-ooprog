package plano.util;

import java.util.*;

/**
 * Lightweight, self-contained JSON serializer and recursive-descent parser
 * implemented using only the Java Standard Library.
 * A utility class: it only has static members and cannot be instantiated.
 */
public final class SimpleJson {

    private SimpleJson() {
    }

    // ==================== SERIALIZATION ====================

    public static String serialize(Object obj) {
        StringBuilder sb = new StringBuilder();
        serializeValue(obj, sb, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void serializeValue(Object obj, StringBuilder sb, int indent) {
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof String) {
            sb.append('"').append(escapeString((String) obj)).append('"');
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
        } else if (obj instanceof Map) {
            serializeMap((Map<String, Object>) obj, sb, indent);
        } else if (obj instanceof Collection) {
            serializeCollection((Collection<?>) obj, sb, indent);
        } else {
            sb.append('"').append(escapeString(obj.toString())).append('"');
        }
    }

    private static void serializeMap(Map<String, Object> map, StringBuilder sb, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int nextIndent = indent + 2;
        int count = 0;
        int total = map.size();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            indent(sb, nextIndent);
            sb.append('"').append(escapeString(entry.getKey())).append("\": ");
            serializeValue(entry.getValue(), sb, nextIndent);
            count++;
            if (count < total) {
                sb.append(",");
            }
            sb.append("\n");
        }
        indent(sb, indent);
        sb.append("}");
    }

    private static void serializeCollection(Collection<?> col, StringBuilder sb, int indent) {
        if (col.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        int nextIndent = indent + 2;
        int count = 0;
        int total = col.size();
        for (Object item : col) {
            indent(sb, nextIndent);
            serializeValue(item, sb, nextIndent);
            count++;
            if (count < total) {
                sb.append(",");
            }
            sb.append("\n");
        }
        indent(sb, indent);
        sb.append("]");
    }

    private static void indent(StringBuilder sb, int spaces) {
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
    }

    private static String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // ==================== PARSER ====================

    public static Object parse(String json) {
        if (json == null) return null;
        Parser parser = new Parser(json);
        return parser.parseValue();
    }

    private static class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                    // Line comment support
                    pos += 2;
                    while (pos < src.length() && src.charAt(pos) != '\n') {
                        pos++;
                    }
                } else {
                    break;
                }
            }
        }

        private char peek() {
            skipWhitespace();
            if (pos >= src.length()) return '\0';
            return src.charAt(pos);
        }

        private char nextChar() {
            skipWhitespace();
            if (pos >= src.length()) return '\0';
            return src.charAt(pos++);
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) return null;
            char c = peek();
            if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == '"') {
                return parseString();
            } else if (c == 't' || c == 'f') {
                return parseBoolean();
            } else if (c == 'n') {
                return parseNull();
            } else if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            }
            throw new IllegalArgumentException("Unexpected character in JSON at position " + pos + ": " + c);
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            nextChar(); // consume '{'
            skipWhitespace();
            if (peek() == '}') {
                nextChar();
                return map;
            }

            while (pos < src.length()) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new IllegalArgumentException("Expected string key at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                char colon = nextChar();
                if (colon != ':') {
                    throw new IllegalArgumentException("Expected ':' after key at position " + pos);
                }
                Object value = parseValue();
                map.put(key, value);

                skipWhitespace();
                char delim = peek();
                if (delim == ',') {
                    nextChar();
                } else if (delim == '}') {
                    nextChar();
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos + " but found '" + delim + "'");
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            nextChar(); // consume '['
            skipWhitespace();
            if (peek() == ']') {
                nextChar();
                return list;
            }

            while (pos < src.length()) {
                Object item = parseValue();
                list.add(item);
                skipWhitespace();
                char delim = peek();
                if (delim == ',') {
                    nextChar();
                } else if (delim == ']') {
                    nextChar();
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos + " but found '" + delim + "'");
                }
            }
            return list;
        }

        private String parseString() {
            nextChar(); // consume initial '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                } else if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalArgumentException("Unterminated escape sequence");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw new IllegalArgumentException("Invalid unicode escape");
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string literal at end of input");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null literal at position " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = src.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(numStr);
            } else {
                try {
                    return Long.parseLong(numStr);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(numStr);
                }
            }
        }
    }
}
