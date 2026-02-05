package eu.dietwise.services.v1.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits Markdown into blocks without language-specific heuristics.
 * Preserves list blocks as contiguous chunks and splits paragraphs on blank lines.
 */
public final class MarkdownBlockSegmenter {
	private static final Pattern BULLET_LIST_PATTERN = Pattern.compile("^\\s*[*+-]\\s+.+");
	private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\s*\\d+[.)]\\s+.+");

	private MarkdownBlockSegmenter() {
	}

	public static List<String> segment(String markdown) {
		if (markdown == null || markdown.isBlank()) {
			return List.of();
		}

		String normalized = markdown.replace("\r\n", "\n").replace("\r", "\n");
		List<String> lines = List.of(normalized.split("\n", -1));
		List<String> blocks = new ArrayList<>();
		List<String> current = new ArrayList<>();
		BlockMode mode = BlockMode.NONE;
		ListKind listKind = ListKind.NONE;

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			boolean blank = line.isBlank();
			ListKind lineListKind = listKind(line);
			boolean listItem = lineListKind != ListKind.NONE;
			boolean indented = isIndented(line);

			if (blank) {
				if (mode == BlockMode.LIST) {
					int next = nextNonBlankIndex(lines, i + 1);
					if (next == -1) {
						flushBlock(blocks, current);
						mode = BlockMode.NONE;
						listKind = ListKind.NONE;
					} else if (isListItem(lines.get(next)) || isIndented(lines.get(next))) {
						ListKind nextKind = listKind(lines.get(next));
						if (nextKind == ListKind.NONE || nextKind == listKind) {
							continue;
						}
						flushBlock(blocks, current);
						mode = BlockMode.NONE;
						listKind = ListKind.NONE;
					} else {
						flushBlock(blocks, current);
						mode = BlockMode.NONE;
						listKind = ListKind.NONE;
					}
				} else if (mode == BlockMode.TEXT) {
					flushBlock(blocks, current);
					mode = BlockMode.NONE;
				}
				continue;
			}

			if (mode == BlockMode.NONE) {
				mode = listItem ? BlockMode.LIST : BlockMode.TEXT;
				if (mode == BlockMode.LIST) {
					listKind = lineListKind;
				}
				current.add(line);
				continue;
			}

			if (mode == BlockMode.LIST) {
				if (listItem && lineListKind != listKind) {
					flushBlock(blocks, current);
					listKind = lineListKind;
					current.add(line);
				} else if (listItem || indented) {
					current.add(line);
				} else {
					flushBlock(blocks, current);
					mode = BlockMode.TEXT;
					listKind = ListKind.NONE;
					current.add(line);
				}
			} else {
				if (listItem) {
					flushBlock(blocks, current);
					mode = BlockMode.LIST;
					listKind = lineListKind;
					current.add(line);
				} else {
					current.add(line);
				}
			}
		}

		flushBlock(blocks, current);
		return blocks;
	}

	private static boolean isListItem(String line) {
		return listKind(line) != ListKind.NONE;
	}

	private static ListKind listKind(String line) {
		if (BULLET_LIST_PATTERN.matcher(line).matches()) {
			return ListKind.BULLET;
		}
		if (ORDERED_LIST_PATTERN.matcher(line).matches()) {
			return ListKind.ORDERED;
		}
		return ListKind.NONE;
	}

	private static boolean isIndented(String line) {
		return !line.isBlank() && (line.startsWith(" ") || line.startsWith("\t"));
	}

	private static int nextNonBlankIndex(List<String> lines, int start) {
		for (int i = start; i < lines.size(); i++) {
			if (!lines.get(i).isBlank()) {
				return i;
			}
		}
		return -1;
	}

	private static void flushBlock(List<String> blocks, List<String> current) {
		if (current.isEmpty()) {
			return;
		}
		String block = String.join("\n", trimBlankEdges(current));
		if (!block.isBlank()) {
			blocks.add(block);
		}
		current.clear();
	}

	private static List<String> trimBlankEdges(List<String> lines) {
		int start = 0;
		int end = lines.size();
		while (start < end && lines.get(start).isBlank()) {
			start++;
		}
		while (end > start && lines.get(end - 1).isBlank()) {
			end--;
		}
		return lines.subList(start, end);
	}

	private enum BlockMode {
		NONE,
		TEXT,
		LIST
	}

	private enum ListKind {
		NONE,
		BULLET,
		ORDERED
	}
}
