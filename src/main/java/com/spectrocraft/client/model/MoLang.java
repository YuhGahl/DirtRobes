package com.spectrocraft.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sous-ensemble de MoLang (le langage d'expressions de Bedrock) suffisant pour les animations :
 * nombres, + - * /, parenthèses, q.anim_time, q.life_time, math.sin/cos/abs/clamp/min/max/lerp/sqrt/pi.
 * Les angles des fonctions trigonométriques sont en degrés, comme dans Bedrock.
 */
public final class MoLang {
    private MoLang() {}

    /** Contexte d'évaluation. */
    public static final class Context {
        public double animTime;
        public double lifeTime;
    }

    @FunctionalInterface
    public interface Expr {
        double eval(Context ctx);
    }

    public static Expr constant(double v) {
        return ctx -> v;
    }

    public static Expr parse(String source) {
        try {
            Parser p = new Parser(source.toLowerCase(Locale.ROOT));
            Expr e = p.expression();
            return e;
        } catch (RuntimeException ex) {
            return constant(0);
        }
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private boolean eat(char c) {
            skip();
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        Expr expression() {
            Expr left = term();
            while (true) {
                if (eat('+')) {
                    Expr l = left, r = term();
                    left = c -> l.eval(c) + r.eval(c);
                } else if (eat('-')) {
                    Expr l = left, r = term();
                    left = c -> l.eval(c) - r.eval(c);
                } else return left;
            }
        }

        Expr term() {
            Expr left = factor();
            while (true) {
                if (eat('*')) {
                    Expr l = left, r = factor();
                    left = c -> l.eval(c) * r.eval(c);
                } else if (eat('/')) {
                    Expr l = left, r = factor();
                    left = c -> {
                        double d = r.eval(c);
                        return d == 0 ? 0 : l.eval(c) / d;
                    };
                } else return left;
            }
        }

        Expr factor() {
            skip();
            if (eat('-')) {
                Expr f = factor();
                return c -> -f.eval(c);
            }
            if (eat('+')) return factor();
            if (eat('(')) {
                Expr e = expression();
                eat(')');
                return e;
            }
            int start = i;
            if (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                double v = Double.parseDouble(s.substring(start, i));
                return constant(v);
            }
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
            String name = s.substring(start, i);
            if (name.isEmpty()) throw new IllegalStateException("MoLang : expression invalide");
            List<Expr> args = new ArrayList<>();
            if (eat('(')) {
                if (!eat(')')) {
                    do {
                        args.add(expression());
                    } while (eat(','));
                    eat(')');
                }
            }
            return identifier(name, args);
        }

        private Expr arg(List<Expr> args, int n) {
            return n < args.size() ? args.get(n) : constant(0);
        }

        private Expr identifier(String name, List<Expr> args) {
            String n = name.replace("query.", "q.");
            switch (n) {
                case "q.anim_time":
                    return c -> c.animTime;
                case "q.life_time":
                    return c -> c.lifeTime;
                case "math.pi":
                    return constant(Math.PI);
                case "math.sin": {
                    Expr a = arg(args, 0);
                    return c -> Math.sin(Math.toRadians(a.eval(c)));
                }
                case "math.cos": {
                    Expr a = arg(args, 0);
                    return c -> Math.cos(Math.toRadians(a.eval(c)));
                }
                case "math.abs": {
                    Expr a = arg(args, 0);
                    return c -> Math.abs(a.eval(c));
                }
                case "math.sqrt": {
                    Expr a = arg(args, 0);
                    return c -> Math.sqrt(Math.max(0, a.eval(c)));
                }
                case "math.min": {
                    Expr a = arg(args, 0), b = arg(args, 1);
                    return c -> Math.min(a.eval(c), b.eval(c));
                }
                case "math.max": {
                    Expr a = arg(args, 0), b = arg(args, 1);
                    return c -> Math.max(a.eval(c), b.eval(c));
                }
                case "math.clamp": {
                    Expr v = arg(args, 0), lo = arg(args, 1), hi = arg(args, 2);
                    return c -> Math.max(lo.eval(c), Math.min(hi.eval(c), v.eval(c)));
                }
                case "math.lerp": {
                    Expr a = arg(args, 0), b = arg(args, 1), t = arg(args, 2);
                    return c -> a.eval(c) + (b.eval(c) - a.eval(c)) * t.eval(c);
                }
                default:
                    return constant(0); // variables inconnues = 0
            }
        }
    }
}
