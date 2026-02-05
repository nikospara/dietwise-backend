package eu.dietwise.services.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownBlockHeadingJoinerTest {
	@Test
	void joinsHeadingWithFollowingContent() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("# Title", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("Intro text.", MarkdownBlockSegmenter.BlockType.TEXT),
				new MarkdownBlockSegmenter.Block("- Item one\n- Item two", MarkdownBlockSegmenter.BlockType.LIST)
		);

		List<String> joined = MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent(blocks);

		assertThat(joined).containsExactly(
				"# Title\nIntro text.",
				"- Item one\n- Item two"
		);
	}

	@Test
	void keepsLowerLevelHeadingWithCurrentHeading() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("# Title", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("## Subtitle", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("Text", MarkdownBlockSegmenter.BlockType.TEXT),
				new MarkdownBlockSegmenter.Block("More text", MarkdownBlockSegmenter.BlockType.TEXT)
		);

		List<String> joined = MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent(blocks);

		assertThat(joined).containsExactly(
				"# Title\n## Subtitle\nText",
				"More text"
		);
	}

	@Test
	void splitsOnSameLevelHeading() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("# Introduction", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("# Chapter One", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("Body", MarkdownBlockSegmenter.BlockType.TEXT)
		);

		List<String> joined = MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent(blocks);

		assertThat(joined).containsExactly(
				"# Introduction",
				"# Chapter One\nBody"
		);
	}

	@Test
	void splitsOnHigherLevelHeading() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("## Section", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("# New Chapter", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("Text", MarkdownBlockSegmenter.BlockType.TEXT)
		);

		List<String> joined = MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent(blocks);

		assertThat(joined).containsExactly(
				"## Section",
				"# New Chapter\nText"
		);
	}

	@Test
	void keepsHeadingChainAndFirstNonHeadingOnly() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("# Title", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("## Subtitle", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("### Subsubtitle", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("First paragraph.", MarkdownBlockSegmenter.BlockType.TEXT),
				new MarkdownBlockSegmenter.Block("Second paragraph.", MarkdownBlockSegmenter.BlockType.TEXT)
		);

		List<String> joined = MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent(blocks);

		assertThat(joined).containsExactly(
				"# Title\n## Subtitle\n### Subsubtitle\nFirst paragraph.",
				"Second paragraph."
		);
	}

	@Test
	void splitsHeadingChainWhenEqualLevelAppears() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("# Title", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("## Subtitle", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("## Another Subtitle", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("Text", MarkdownBlockSegmenter.BlockType.TEXT)
		);

		List<String> joined = MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent(blocks);

		assertThat(joined).containsExactly(
				"# Title\n## Subtitle",
				"## Another Subtitle\nText"
		);
	}

	@Test
	void splitsHeadingChainWhenHigherLevelAppears() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("## Section", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("### Subsection", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("# New Chapter", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("Body", MarkdownBlockSegmenter.BlockType.TEXT)
		);

		List<String> joined = MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent(blocks);

		assertThat(joined).containsExactly(
				"## Section\n### Subsection",
				"# New Chapter\nBody"
		);
	}

	@Test
	void keepsHeadingChainAtEndWithoutContent() {
		List<MarkdownBlockSegmenter.Block> blocks = List.of(
				new MarkdownBlockSegmenter.Block("# Title", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("## Subtitle", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("### Subsubtitle", MarkdownBlockSegmenter.BlockType.HEADING)
		);

		List<String> joined = MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent(blocks);

		assertThat(joined).containsExactly(
				"# Title\n## Subtitle\n### Subsubtitle"
		);
	}
}
