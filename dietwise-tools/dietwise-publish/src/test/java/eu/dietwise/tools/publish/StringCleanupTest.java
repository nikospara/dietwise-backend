package eu.dietwise.tools.publish;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringCleanupTest {

	@Test
	void nullIsReturnedUnchanged() {
		assertThat(StringCleanup.clean(null)).isNull();
	}

	@Test
	void leadingAndTrailingWhitespaceIsTrimmed() {
		assertThat(StringCleanup.clean("  hello  ")).isEqualTo("hello");
	}

	@Test
	void nonSpaceWhitespaceInTheBodyBecomesASpace() {
		assertThat(StringCleanup.clean("a\tb\nc\rd")).isEqualTo("a b c d");
	}

	@Test
	void runsOfWhitespaceCollapseToASingleSpace() {
		assertThat(StringCleanup.clean("a   b \t\n c")).isEqualTo("a b c");
	}

	@Test
	void aDirtyStringIsFullyCleaned() {
		assertThat(StringCleanup.clean("\t  Reduce   beef\nintake \r\n")).isEqualTo("Reduce beef intake");
	}

	@Test
	void anAlreadyCleanStringIsUnchanged() {
		assertThat(StringCleanup.clean("Reduce beef intake")).isEqualTo("Reduce beef intake");
	}

	@Test
	void aWhitespaceOnlyStringBecomesEmpty() {
		assertThat(StringCleanup.clean("  \t\n ")).isEmpty();
	}
}
