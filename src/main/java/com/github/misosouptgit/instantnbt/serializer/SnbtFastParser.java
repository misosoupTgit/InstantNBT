package com.github.misosouptgit.instantnbt.serializer;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Hand-rolled SNBT recursive-descent parser (Project Plan 10.2 Fast Parser).
 * Falls back to vanilla TagParser via {@link SnbtCodec} on failure.
 */
public final class SnbtFastParser {
	private final String src;
	private int pos;

	private SnbtFastParser(String src) {
		this.src = src;
	}

	public static Tag parse(String snbt) throws SnbtParseException {
		if (snbt == null) {
			throw new SnbtParseException("null snbt");
		}
		SnbtFastParser parser = new SnbtFastParser(snbt.trim());
		if (parser.src.isEmpty()) {
			return new CompoundTag();
		}
		Tag tag = parser.parseValue();
		parser.skipWs();
		if (!parser.eof()) {
			throw parser.error("trailing input at " + parser.pos);
		}
		return tag;
	}

	private Tag parseValue() throws SnbtParseException {
		skipWs();
		if (eof()) {
			throw error("unexpected eof");
		}
		char c = peek();
		if (c == '{') {
			return parseCompound();
		}
		if (c == '[') {
			return parseListOrArray();
		}
		if (c == '"' || c == '\'') {
			return StringTag.valueOf(parseQuotedString());
		}
		return parseBare();
	}

	private CompoundTag parseCompound() throws SnbtParseException {
		expect('{');
		CompoundTag out = new CompoundTag();
		skipWs();
		if (tryConsume('}')) {
			return out;
		}
		while (true) {
			skipWs();
			String key = parseKey();
			skipWs();
			expect(':');
			Tag value = parseValue();
			out.put(key, value);
			skipWs();
			if (tryConsume('}')) {
				return out;
			}
			expect(',');
		}
	}

	private Tag parseListOrArray() throws SnbtParseException {
		expect('[');
		skipWs();
		if (tryConsume(']')) {
			return new ListTag();
		}
		if (matchArrayPrefix("B;")) {
			return new ByteArrayTag(parseByteArrayBody());
		}
		if (matchArrayPrefix("I;")) {
			return new IntArrayTag(parseIntArrayBody());
		}
		if (matchArrayPrefix("L;")) {
			return new LongArrayTag(parseLongArrayBody());
		}
		ListTag list = new ListTag();
		while (true) {
			list.add(parseValue());
			skipWs();
			if (tryConsume(']')) {
				return list;
			}
			expect(',');
			skipWs();
		}
	}

	private boolean matchArrayPrefix(String prefix) {
		skipWs();
		if (pos + prefix.length() > src.length()) {
			return false;
		}
		if (src.regionMatches(true, pos, prefix, 0, prefix.length())) {
			pos += prefix.length();
			return true;
		}
		return false;
	}

	private byte[] parseByteArrayBody() throws SnbtParseException {
		java.util.ArrayList<Byte> values = new java.util.ArrayList<>();
		skipWs();
		if (tryConsume(']')) {
			return new byte[0];
		}
		while (true) {
			skipWs();
			values.add((byte) parseSignedLong());
			skipWs();
			if (peek() == 'b' || peek() == 'B') {
				pos++;
			}
			skipWs();
			if (tryConsume(']')) {
				byte[] out = new byte[values.size()];
				for (int i = 0; i < values.size(); i++) {
					out[i] = values.get(i);
				}
				return out;
			}
			expect(',');
		}
	}

	private int[] parseIntArrayBody() throws SnbtParseException {
		java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
		skipWs();
		if (tryConsume(']')) {
			return new int[0];
		}
		while (true) {
			skipWs();
			values.add((int) parseSignedLong());
			skipWs();
			if (tryConsume(']')) {
				int[] out = new int[values.size()];
				for (int i = 0; i < values.size(); i++) {
					out[i] = values.get(i);
				}
				return out;
			}
			expect(',');
		}
	}

	private long[] parseLongArrayBody() throws SnbtParseException {
		java.util.ArrayList<Long> values = new java.util.ArrayList<>();
		skipWs();
		if (tryConsume(']')) {
			return new long[0];
		}
		while (true) {
			skipWs();
			values.add(parseSignedLong());
			skipWs();
			if (peek() == 'l' || peek() == 'L') {
				pos++;
			}
			skipWs();
			if (tryConsume(']')) {
				long[] out = new long[values.size()];
				for (int i = 0; i < values.size(); i++) {
					out[i] = values.get(i);
				}
				return out;
			}
			expect(',');
		}
	}

	private Tag parseBare() throws SnbtParseException {
		int start = pos;
		while (!eof()) {
			char c = peek();
			if (c == ',' || c == ']' || c == '}' || Character.isWhitespace(c)) {
				break;
			}
			pos++;
		}
		String token = src.substring(start, pos);
		if (token.isEmpty()) {
			throw error("empty token");
		}
		if ("true".equalsIgnoreCase(token)) {
			return ByteTag.valueOf((byte) 1);
		}
		if ("false".equalsIgnoreCase(token)) {
			return ByteTag.valueOf((byte) 0);
		}
		char last = token.charAt(token.length() - 1);
		String num = token;
		try {
			if (last == 'b' || last == 'B') {
				return ByteTag.valueOf(Byte.parseByte(num.substring(0, num.length() - 1)));
			}
			if (last == 's' || last == 'S') {
				return ShortTag.valueOf(Short.parseShort(num.substring(0, num.length() - 1)));
			}
			if (last == 'l' || last == 'L') {
				return LongTag.valueOf(Long.parseLong(num.substring(0, num.length() - 1)));
			}
			if (last == 'f' || last == 'F') {
				return FloatTag.valueOf(Float.parseFloat(num.substring(0, num.length() - 1)));
			}
			if (last == 'd' || last == 'D') {
				return DoubleTag.valueOf(Double.parseDouble(num.substring(0, num.length() - 1)));
			}
			if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
				return DoubleTag.valueOf(Double.parseDouble(token));
			}
			long v = Long.parseLong(token);
			if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
				return IntTag.valueOf((int) v);
			}
			return LongTag.valueOf(v);
		} catch (NumberFormatException ex) {
			return StringTag.valueOf(token);
		}
	}

	private String parseKey() throws SnbtParseException {
		skipWs();
		if (peek() == '"' || peek() == '\'') {
			return parseQuotedString();
		}
		int start = pos;
		while (!eof()) {
			char c = peek();
			if (c == ':' || Character.isWhitespace(c)) {
				break;
			}
			pos++;
		}
		if (start == pos) {
			throw error("expected key");
		}
		return src.substring(start, pos);
	}

	private String parseQuotedString() throws SnbtParseException {
		char quote = next();
		StringBuilder sb = new StringBuilder();
		while (!eof()) {
			char c = next();
			if (c == quote) {
				return sb.toString();
			}
			if (c == '\\') {
				if (eof()) {
					throw error("bad escape");
				}
				char e = next();
				switch (e) {
					case 'n':
						sb.append('\n');
						break;
					case 'r':
						sb.append('\r');
						break;
					case 't':
						sb.append('\t');
						break;
					case '"':
					case '\'':
					case '\\':
						sb.append(e);
						break;
					default:
						sb.append(e);
						break;
				}
			} else {
				sb.append(c);
			}
		}
		throw error("unterminated string");
	}

	private long parseSignedLong() throws SnbtParseException {
		skipWs();
		int start = pos;
		if (!eof() && (peek() == '+' || peek() == '-')) {
			pos++;
		}
		while (!eof() && Character.isDigit(peek())) {
			pos++;
		}
		if (start == pos || (pos == start + 1 && !Character.isDigit(src.charAt(start)))) {
			throw error("expected number");
		}
		return Long.parseLong(src.substring(start, pos));
	}

	private void skipWs() {
		while (!eof() && Character.isWhitespace(peek())) {
			pos++;
		}
	}

	private boolean eof() {
		return pos >= src.length();
	}

	private char peek() {
		return src.charAt(pos);
	}

	private char next() throws SnbtParseException {
		if (eof()) {
			throw error("unexpected eof");
		}
		return src.charAt(pos++);
	}

	private void expect(char c) throws SnbtParseException {
		skipWs();
		if (eof() || next() != c) {
			throw error("expected '" + c + "'");
		}
	}

	private boolean tryConsume(char c) {
		skipWs();
		if (!eof() && peek() == c) {
			pos++;
			return true;
		}
		return false;
	}

	private SnbtParseException error(String message) {
		return new SnbtParseException(message + " at " + pos + " near '" + snippet() + "'");
	}

	private String snippet() {
		int a = Math.max(0, pos - 8);
		int b = Math.min(src.length(), pos + 8);
		return src.substring(a, b);
	}

	public static final class SnbtParseException extends Exception {
		public SnbtParseException(String message) {
			super(message);
		}
	}
}
