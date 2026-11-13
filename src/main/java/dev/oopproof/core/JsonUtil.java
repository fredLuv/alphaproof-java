package dev.oopproof.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, strict, zero-dependency JSON parser and serializer for proof certificates
 * and declarative formal specifications.
 */
public final class JsonUtil {

    private JsonUtil() {}

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, value, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Double) {
            if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
                sb.append('"').append(d).append('"');
            } else {
                sb.append(value);
            }
        } else if (value instanceof String s) {
            writeEscapedString(sb, s);
        } else if (value instanceof Enum<?> e) {
            writeEscapedString(sb, e.name());
        } else if (value instanceof Map<?, ?> rawMap) {
            Map<Object, Object> map = (Map<Object, Object>) rawMap;
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                appendIndent(sb, indent + 2);
                writeEscapedString(sb, String.valueOf(entry.getKey()));
                sb.append(": ");
                writeJson(sb, entry.getValue(), indent + 2);
                if (++i < map.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            appendIndent(sb, indent);
            sb.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                appendIndent(sb, indent + 2);
                writeJson(sb, list.get(i), indent + 2);
                if (i + 1 < list.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            appendIndent(sb, indent);
            sb.append(']');
        } else {
            writeEscapedString(sb, String.valueOf(value));
        }
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
    }

    private static void writeEscapedString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    public static Object parse(String json) {
        Parser parser = new Parser(json);
        Object val = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Unexpected trailing JSON content at index " + parser.pos);
        }
        return val;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object parsed = parse(json);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected JSON object at root");
        }
        return (Map<String, Object>) parsed;
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        boolean isAtEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected JSON char '" + c + "' at " + pos);
                }
            };
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' in object at " + pos);
                }
            }
            return map;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' in array at " + pos);
                }
            }
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        throw new IllegalArgumentException("Unterminated escape at " + pos);
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Incomplete unicode escape at " + pos);
                            }
                            int code = Integer.parseInt(src.substring(pos, pos + 4), 16);
                            sb.append((char) code);
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape \\" + esc + " at " + pos);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string literal");
        }

        Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean at " + pos);
        }

        Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null at " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
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
            String num = src.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(num);
            }
            long l = Long.parseLong(num);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
        }

        boolean peek(char c) {
            return !isAtEnd() && src.charAt(pos) == c;
        }

        void expect(char c) {
            if (isAtEnd() || src.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at " + pos);
            }
            pos++;
        }
    }
}
