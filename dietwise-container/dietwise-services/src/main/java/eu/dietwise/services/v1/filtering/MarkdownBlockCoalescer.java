package eu.dietwise.services.v1.filtering;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownBlockCoalescer {
	private MarkdownBlockCoalescer() {
	}

	public static List<String> coalesce(List<MarkdownBlockSegmenter.Block> blocks, int maxChars) {
		if (blocks == null || blocks.isEmpty()) {
			return List.of();
		}
		if (maxChars <= 0) {
			throw new IllegalArgumentException("maxChars must be positive");
		}

		List<String> output = new ArrayList<>();
		int i = 0;
		while (i < blocks.size()) {
			MarkdownBlockSegmenter.Block block = blocks.get(i);
			if (block.type() == MarkdownBlockSegmenter.BlockType.HEADING) {
				JoinResult joinResult = joinHeadingChainWithFirstText(blocks, i);
				output.add(joinResult.content());
				i = joinResult.nextIndex();
				continue;
			}
			if (block.type() == MarkdownBlockSegmenter.BlockType.LIST) {
				output.add(block.content());
				i++;
				continue;
			}

			StringBuilder content = new StringBuilder(block.content());
			int j = i + 1;
			while (j < blocks.size()) {
				MarkdownBlockSegmenter.Block next = blocks.get(j);
				if (next.type() != MarkdownBlockSegmenter.BlockType.TEXT) {
					break;
				}
				int candidateLength = content.length() + 1 + next.content().length();
				if (candidateLength > maxChars) {
					break;
				}
				content.append('\n').append(next.content());
				j++;
			}
			output.add(content.toString());
			i = j;
		}
		return output;
	}

	private static JoinResult joinHeadingChainWithFirstText(List<MarkdownBlockSegmenter.Block> blocks, int startIndex) {
		MarkdownBlockSegmenter.Block first = blocks.get(startIndex);
		int currentLevel = headingLevel(first.content());
		StringBuilder content = new StringBuilder(first.content());
		int j = startIndex + 1;
		while (j < blocks.size()) {
			MarkdownBlockSegmenter.Block next = blocks.get(j);
			if (next.type() != MarkdownBlockSegmenter.BlockType.HEADING) {
				break;
			}
			int nextLevel = headingLevel(next.content());
			if (nextLevel <= currentLevel) {
				break;
			}
			content.append('\n').append(next.content());
			currentLevel = nextLevel;
			j++;
		}

		if (j < blocks.size() && blocks.get(j).type() == MarkdownBlockSegmenter.BlockType.TEXT) {
			content.append('\n').append(blocks.get(j).content());
			j++;
		}
		return new JoinResult(content.toString(), j);
	}

	private static int headingLevel(String content) {
		int level = 0;
		while (level < content.length() && content.charAt(level) == '#') {
			level++;
		}
		return level == 0 ? Integer.MAX_VALUE : level;
	}

	private record JoinResult(String content, int nextIndex) {
	}
}
