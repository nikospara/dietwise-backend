package eu.dietwise.services.v1.filtering;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MarkdownBlockSegmenterTest {
	@Test
	void splitsParagraphsOnBlankLines() {
		String markdown = """
				First paragraph.

				Second paragraph.
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("First paragraph.", MarkdownBlockSegmenter.BlockType.TEXT),
				new MarkdownBlockSegmenter.Block("Second paragraph.", MarkdownBlockSegmenter.BlockType.TEXT)
		);
	}

	@Test
	void keepsListItemsTogether() {
		String markdown = """
				Intro line.

				- Item one
				- Item two

				Tail line.
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("Intro line.", MarkdownBlockSegmenter.BlockType.TEXT),
				new MarkdownBlockSegmenter.Block("- Item one\n- Item two", MarkdownBlockSegmenter.BlockType.LIST),
				new MarkdownBlockSegmenter.Block("Tail line.", MarkdownBlockSegmenter.BlockType.TEXT)
		);
	}

	@Test
	void preservesIndentedListContinuation() {
		String markdown = """
				- Item one
				  continuation line
				- Item two
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("- Item one\n  continuation line\n- Item two", MarkdownBlockSegmenter.BlockType.LIST)
		);
	}

	@Test
	void keepsListAcrossBlankLineWhenContinuationFollows() {
		String markdown = """
				- Item one

				- Item two
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("- Item one\n- Item two", MarkdownBlockSegmenter.BlockType.LIST)
		);
	}

	@Test
	void keepsNumberedListItemsTogether() {
		String markdown = """
				1. First item
				2. Second item
				3. Third item
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("1. First item\n2. Second item\n3. Third item", MarkdownBlockSegmenter.BlockType.LIST)
		);
	}

	@Test
	void keepsMixedListTypesInSeparateBlocks() {
		String markdown = """
				- Bullet one
				- Bullet two

				1) Numbered one
				2) Numbered two
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("- Bullet one\n- Bullet two", MarkdownBlockSegmenter.BlockType.LIST),
				new MarkdownBlockSegmenter.Block("1) Numbered one\n2) Numbered two", MarkdownBlockSegmenter.BlockType.LIST)
		);
	}

	@Test
	void classifiesHeadingBlock() {
		String markdown = """
				# Heading Title
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("# Heading Title", MarkdownBlockSegmenter.BlockType.HEADING)
		);
	}

	@Test
	void classifiesHeadingWithFollowingTextAsHeadingBlock() {
		String markdown = """
				## Section Title
				Paragraph text that follows.
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("## Section Title", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("Paragraph text that follows.", MarkdownBlockSegmenter.BlockType.TEXT)
		);
	}

	@Test
	void classifiesHeadingWithListAsHeadingBlock() {
		String markdown = """
				### Ingredients
				- Item one
				- Item two
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("### Ingredients", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("- Item one\n- Item two", MarkdownBlockSegmenter.BlockType.LIST)
		);
	}

	@Test
	void splitsHeadingInsideTextBlock() {
		String markdown = """
				Intro paragraph line.
				## Mid Heading
				Following paragraph line.
				""";

		List<MarkdownBlockSegmenter.Block> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				new MarkdownBlockSegmenter.Block("Intro paragraph line.", MarkdownBlockSegmenter.BlockType.TEXT),
				new MarkdownBlockSegmenter.Block("## Mid Heading", MarkdownBlockSegmenter.BlockType.HEADING),
				new MarkdownBlockSegmenter.Block("Following paragraph line.", MarkdownBlockSegmenter.BlockType.TEXT)
		);
	}
}
