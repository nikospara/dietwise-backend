package eu.dietwise.services.v1.ai;

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

		List<String> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				"First paragraph.",
				"Second paragraph."
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

		List<String> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				"Intro line.",
				"- Item one\n- Item two",
				"Tail line."
		);
	}

	@Test
	void preservesIndentedListContinuation() {
		String markdown = """
				- Item one
				  continuation line
				- Item two
				""";

		List<String> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				"- Item one\n  continuation line\n- Item two"
		);
	}

	@Test
	void keepsListAcrossBlankLineWhenContinuationFollows() {
		String markdown = """
				- Item one

				- Item two
				""";

		List<String> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				"- Item one\n- Item two"
		);
	}

	@Test
	void keepsNumberedListItemsTogether() {
		String markdown = """
				1. First item
				2. Second item
				3. Third item
				""";

		List<String> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				"1. First item\n2. Second item\n3. Third item"
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

		List<String> blocks = MarkdownBlockSegmenter.segment(markdown);

		assertThat(blocks).containsExactly(
				"- Bullet one\n- Bullet two",
				"1) Numbered one\n2) Numbered two"
		);
	}
}
