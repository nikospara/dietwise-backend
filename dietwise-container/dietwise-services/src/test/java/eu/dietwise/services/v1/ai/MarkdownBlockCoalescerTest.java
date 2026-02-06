package eu.dietwise.services.v1.ai;

import static eu.dietwise.services.v1.ai.MarkdownBlockSegmenter.BlockType.HEADING;
import static eu.dietwise.services.v1.ai.MarkdownBlockSegmenter.BlockType.LIST;
import static eu.dietwise.services.v1.ai.MarkdownBlockSegmenter.BlockType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class MarkdownBlockCoalescerTest {
	@Test
	void mergesConsecutiveTextBlocksUpToLimit() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("First paragraph.", TEXT),
				new MarkdownBlockSegmenter.Block("Second paragraph.", TEXT),
				new MarkdownBlockSegmenter.Block("Third paragraph.", TEXT)
		);

		List<String> result = MarkdownBlockCoalescer.coalesce(blocks, 64);

		assertThat(result).containsExactly(
				"First paragraph.\nSecond paragraph.\nThird paragraph."
		);
	}

	@Test
	void stopsMergingWhenMaxCharsExceeded() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("First paragraph.", TEXT),
				new MarkdownBlockSegmenter.Block("Second paragraph.", TEXT),
				new MarkdownBlockSegmenter.Block("Third paragraph.", TEXT)
		);

		List<String> result = MarkdownBlockCoalescer.coalesce(blocks, "First paragraph.\nSecond paragraph.".length());

		assertThat(result).containsExactly(
				"First paragraph.\nSecond paragraph.",
				"Third paragraph."
		);
	}

	@Test
	void treatsListsAndHeadingsAsBoundaries() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("# Title", HEADING),
				new MarkdownBlockSegmenter.Block("Intro text.", TEXT),
				new MarkdownBlockSegmenter.Block("- Item one\n- Item two", LIST),
				new MarkdownBlockSegmenter.Block("Tail text.", TEXT),
				new MarkdownBlockSegmenter.Block("# Other title", HEADING),
				new MarkdownBlockSegmenter.Block("More tail text.", TEXT)
		);

		List<String> result = MarkdownBlockCoalescer.coalesce(blocks, 128);

		assertThat(result).containsExactly(
				"# Title\nIntro text.",
				"- Item one\n- Item two",
				"Tail text.",
				"# Other title\nMore tail text."
		);
	}

	@Test
	void keepsHeadingChainAndFirstTextBlockTogether() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("# Title", HEADING),
				new MarkdownBlockSegmenter.Block("## Subtitle", HEADING),
				new MarkdownBlockSegmenter.Block("First paragraph.", TEXT),
				new MarkdownBlockSegmenter.Block("Second paragraph.", TEXT)
		);

		List<String> result = MarkdownBlockCoalescer.coalesce(blocks, 128);

		assertThat(result).containsExactly(
				"# Title\n## Subtitle\nFirst paragraph.",
				"Second paragraph."
		);
	}

	@Test
	void rejectsNonPositiveMaxChars() {
		assertThatThrownBy(() -> MarkdownBlockCoalescer.coalesce(List.of(new MarkdownBlockSegmenter.Block("xxx", TEXT)), 0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
