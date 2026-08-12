package net.maksy.mcmmoparties.utils;

public class ArithmeticExpression {
    private final String expression;
    private final double level;
    private int position;

    public ArithmeticExpression(String expression, long level) {
        this.expression = expression;
        this.level = level;
    }

    public double parse() {
        double result = parseExpression();
        skipWhitespace();
        if (position != expression.length()) {
            throw error("unexpected character '" + expression.charAt(position) + "'");
        }
        return result;
    }

    private double parseExpression() {
        double result = parseTerm();
        while (true) {
            skipWhitespace();
            if (match('+')) {
                result += parseTerm();
            } else if (match('-')) {
                result -= parseTerm();
            } else {
                return result;
            }
        }
    }

    private double parseTerm() {
        double result = parseUnary();
        while (true) {
            skipWhitespace();
            if (match('*')) {
                result *= parseUnary();
            } else if (match('/')) {
                result /= parseUnary();
            } else {
                return result;
            }
        }
    }

    private double parseUnary() {
        skipWhitespace();
        if (match('+')) {
            return parseUnary();
        }
        if (match('-')) {
            return -parseUnary();
        }
        return parsePrimary();
    }

    private double parsePrimary() {
        skipWhitespace();
        if (match('(')) {
            double result = parseExpression();
            if (!match(')')) {
                throw error("missing closing ')'");
            }
            return result;
        }
        if (match('x')) {
            return level;
        }
        return parseNumber();
    }

    private double parseNumber() {
        skipWhitespace();
        int start = position;
        while (position < expression.length()
                && (Character.isDigit(expression.charAt(position)) || expression.charAt(position) == '.')) {
            position++;
        }
        if (position < expression.length()
                && (expression.charAt(position) == 'e' || expression.charAt(position) == 'E')) {
            position++;
            if (position < expression.length()
                    && (expression.charAt(position) == '+' || expression.charAt(position) == '-')) {
                position++;
            }
            while (position < expression.length() && Character.isDigit(expression.charAt(position))) {
                position++;
            }
        }
        if (start == position) {
            throw error("expected a number or 'x'");
        }
        try {
            return Double.parseDouble(expression.substring(start, position));
        } catch (NumberFormatException e) {
            throw error("invalid number");
        }
    }

    private boolean match(char expected) {
        if (position < expression.length() && expression.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
            position++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + position);
    }
}
