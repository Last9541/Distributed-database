package cmd.lsmkv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserTest {

    @Test
    void splitsPlainArgumentsAndCollapsesRepeatedSpaces() {
        String[] parsed = Parser.parse("  lsmkv   put   --key alpha   --value beta  ".toCharArray());

        assertArrayEquals(new String[]{"lsmkv", "put", "--key", "alpha", "--value", "beta"}, parsed);
    }

    @Test
    void keepsSpacesInsideDoubleQuotedArgument() {
        String[] parsed = Parser.parse("lsmkv put --key \"full name\" --value \"Ada Lovelace\"".toCharArray());

        assertArrayEquals(new String[]{"lsmkv", "put", "--key", "full name", "--value", "Ada Lovelace"}, parsed);
    }

    @Test
    void keepsSpacesInsideSingleQuotedArgument() {
        String[] parsed = Parser.parse("lsmkv put --key 'full name' --value 'Ada Lovelace'".toCharArray());

        assertArrayEquals(new String[]{"lsmkv", "put", "--key", "full name", "--value", "Ada Lovelace"}, parsed);
    }

    @Test
    void preservesOppositeQuoteInsideQuotedArgument() {
        String[] parsed = Parser.parse("lsmkv put --key \"user's name\" --value 'says \"hi\"'".toCharArray());

        assertArrayEquals(new String[]{"lsmkv", "put", "--key", "user's name", "--value", "says \"hi\""}, parsed);
    }

    @Test
    void rejectsUnclosedQuotedArgument() {
        assertThrows(IllegalArgumentException.class, () -> Parser.parse("lsmkv get --key \"abc".toCharArray()));
    }
}
