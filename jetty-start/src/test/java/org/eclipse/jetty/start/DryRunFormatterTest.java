//
// ========================================================================
// Copyright (c) 1995-2023 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.start;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class DryRunFormatterTest
{
    public static Stream<Arguments> unixShellQuotingSource()
    {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of("", "''"),
            Arguments.of("Hello", "Hello"),
            Arguments.of("Hell0", "Hell0"),
            Arguments.of("Hello$World", "'Hello$World'"),
            Arguments.of("Hello\\World", "'Hello\\World'"),
            Arguments.of("Hello`World", "'Hello`World'"),
            Arguments.of("'Hello World'", "\\''Hello World'\\'"),
            Arguments.of("\"Hello World\"", "'\"Hello World\"'"),
            Arguments.of("H-llo_world", "H-llo_world"),
            Arguments.of("H:llo/world", "H:llo/world"),
            Arguments.of("Hello World", "'Hello World'"),
            Arguments.of("foo\\bar", "'foo\\bar'"),
            Arguments.of("foo'bar", "'foo'\\''bar'")
        );
    }

    @ParameterizedTest
    @MethodSource("unixShellQuotingSource")
    public void testShellQuoting(String string, String expected)
    {
        assertThat(DryRunFormatter.unixShellQuoteIfNeeded(string), is(expected));
    }
}
