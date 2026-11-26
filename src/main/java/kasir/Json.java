package kasir;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON sederhana (baca & tulis) tanpa library tambahan. */
public final class Json {
    private Json() { }

    // ---------------- tulis ----------------
    public static String tulis(Object nilai) {
        StringBuilder sb = new StringBuilder();
        tulis(sb, nilai);
        return sb.toString();
    }

    private static void tulis(StringBuilder sb, Object nilai) {
        if (nilai == null) {
            sb.append("null");
        } else if (nilai instanceof Boolean || nilai instanceof Number) {
            sb.append(nilai);
        } else if (nilai instanceof Map<?, ?> peta) {
            sb.append('{');
            boolean pertama = true;
            for (Map.Entry<?, ?> e : peta.entrySet()) {
                if (!pertama) {
                    sb.append(',');
                }
                pertama = false;
                teks(sb, String.valueOf(e.getKey()));
                sb.append(':');
                tulis(sb, e.getValue());
            }
            sb.append('}');
        } else if (nilai instanceof Collection<?> koleksi) {
            sb.append('[');
            boolean pertama = true;
            for (Object o : koleksi) {
                if (!pertama) {
                    sb.append(',');
                }
                pertama = false;
                tulis(sb, o);
            }
            sb.append(']');
        } else {
            teks(sb, String.valueOf(nilai));
        }
    }

    private static void teks(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c); // karakter non-ASCII ditulis apa adanya (UTF-8)
                    }
            }
        }
        sb.append('"');
    }

    // ---------------- baca ----------------
    /** Baca teks JSON; lempar IllegalArgumentException bila tidak valid. */
    public static Object baca(String teks) {
        Pembaca p = new Pembaca(teks);
        p.lewatiSpasi();
        Object hasil = p.nilai();
        p.lewatiSpasi();
        if (!p.habis()) {
            throw new IllegalArgumentException("Data tambahan setelah JSON");
        }
        return hasil;
    }

    private static final class Pembaca {
        private final String s;
        private int i = 0;

        Pembaca(String s) {
            this.s = s;
        }

        boolean habis() {
            return i >= s.length();
        }

        void lewatiSpasi() {
            while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t'
                    || s.charAt(i) == '\n' || s.charAt(i) == '\r')) {
                i++;
            }
        }

        private IllegalArgumentException salah() {
            return new IllegalArgumentException("JSON tidak valid di posisi " + i);
        }

        private void harap(char c) {
            if (habis() || s.charAt(i) != c) {
                throw salah();
            }
            i++;
        }

        Object nilai() {
            if (habis()) {
                throw salah();
            }
            char c = s.charAt(i);
            if (c == '{') {
                return objek();
            }
            if (c == '[') {
                return larik();
            }
            if (c == '"') {
                return string();
            }
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            return angka();
        }

        private Map<String, Object> objek() {
            Map<String, Object> m = new LinkedHashMap<>();
            harap('{');
            lewatiSpasi();
            if (!habis() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                lewatiSpasi();
                String kunci = string();
                lewatiSpasi();
                harap(':');
                lewatiSpasi();
                m.put(kunci, nilai());
                lewatiSpasi();
                if (habis()) {
                    throw salah();
                }
                if (s.charAt(i) == ',') {
                    i++;
                } else {
                    harap('}');
                    return m;
                }
            }
        }

        private List<Object> larik() {
            List<Object> l = new ArrayList<>();
            harap('[');
            lewatiSpasi();
            if (!habis() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                lewatiSpasi();
                l.add(nilai());
                lewatiSpasi();
                if (habis()) {
                    throw salah();
                }
                if (s.charAt(i) == ',') {
                    i++;
                } else {
                    harap(']');
                    return l;
                }
            }
        }

        private String string() {
            harap('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (habis()) {
                    throw salah();
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw salah();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (habis()) {
                    throw salah();
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) {
                            throw salah();
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw salah();
                        }
                        i += 4;
                        break;
                    default:
                        throw salah();
                }
            }
        }

        private Object angka() {
            int awal = i;
            if (!habis() && s.charAt(i) == '-') {
                i++;
            }
            int digitAwal = i;
            while (!habis() && Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) {
                i++;
            }
            if (i == digitAwal) {
                throw salah();
            }
            boolean pecahan = false;
            if (!habis() && s.charAt(i) == '.') {
                pecahan = true;
                i++;
                int d = i;
                while (!habis() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                    i++;
                }
                if (i == d) {
                    throw salah();
                }
            }
            if (!habis() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                pecahan = true;
                i++;
                if (!habis() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                int d = i;
                while (!habis() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                    i++;
                }
                if (i == d) {
                    throw salah();
                }
            }
            String t = s.substring(awal, i);
            try {
                return pecahan ? (Object) Double.valueOf(t) : (Object) Long.valueOf(t);
            } catch (NumberFormatException e) {
                return Double.valueOf(t); // bilangan bulat terlalu besar
            }
        }
    }
}
