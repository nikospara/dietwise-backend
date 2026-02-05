package eu.dietwise.services.v1.ai;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownBlockHeadingJoiner {
	private MarkdownBlockHeadingJoiner() {
	}

	public static List<String> joinHeadingWithFollowingContent(List<MarkdownBlockSegmenter.Block> blocks) {
		if (blocks == null || blocks.isEmpty()) {
			return List.of();
		}

		List<String> joined = new ArrayList<>();
		int i = 0;
		while (i < blocks.size()) {
			MarkdownBlockSegmenter.Block block = blocks.get(i);
			if (block.type() != MarkdownBlockSegmenter.BlockType.HEADING) {
				joined.add(block.content());
				i++;
				continue;
			}

			int currentLevel = headingLevel(block.content());
			StringBuilder content = new StringBuilder(block.content());
			int j = i + 1;
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

			if (j < blocks.size() && blocks.get(j).type() != MarkdownBlockSegmenter.BlockType.HEADING) {
				content.append('\n').append(blocks.get(j).content());
				j++;
			}
			joined.add(content.toString());
			i = j;
		}
		return joined;
	}

	private static int headingLevel(String content) {
		int level = 0;
		while (level < content.length() && content.charAt(level) == '#') {
			level++;
		}
		return level == 0 ? Integer.MAX_VALUE : level;
	}
}
