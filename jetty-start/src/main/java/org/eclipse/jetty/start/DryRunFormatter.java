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

public class DryRunFormatter
{
    interface Function
    {
        /**
         * <p>Function to apply to the specific arg.</p>
         *
         * <p>Perform any quoting / escaping that you need to do here</p>
         * @param arg the raw arg to process
         * @return the arg, potentially post-processed
         */
        String applyArg(String arg);

        /**
         * The separator to use between arguments.
         *
         * @return the separator to use between arguments
         */
        String separator();
    }

    public static final Function SH_ONELINE = new Function()
    {
        @Override
        public String applyArg(String arg)
        {
            return unixShellQuoteIfNeeded(arg);
        }

        @Override
        public String separator()
        {
            return " ";
        }
    };

    public static final Function SH_MULTILINE = new Function()
    {
        @Override
        public String applyArg(String arg)
        {
            return unixShellQuoteIfNeeded(arg);
        }

        @Override
        public String separator()
        {
            return " \\\n  ";
        }
    };

    public static final Function CMD_ONELINE = new Function()
    {
        @Override
        public String applyArg(String arg)
        {
            return windowsShellQuoteIfNeeded(arg);
        }

        @Override
        public String separator()
        {
            return " ";
        }
    };

    public static final Function CMD_MULTILINE = new Function()
    {
        @Override
        public String applyArg(String arg)
        {
            return windowsShellQuoteIfNeeded(arg);
        }

        @Override
        public String separator()
        {
            return " ^\r\n  ";
        }
    };

    public static final Function POWERSHELL_ONELINE = new Function()
    {
        @Override
        public String applyArg(String arg)
        {
            return windowsShellQuoteIfNeeded(arg);
        }

        @Override
        public String separator()
        {
            return " ";
        }
    };

    public static final Function POWERSHELL_MULTILINE = new Function()
    {
        @Override
        public String applyArg(String arg)
        {
            return windowsShellQuoteIfNeeded(arg);
        }

        @Override
        public String separator()
        {
            return " `\r\n  ";
        }
    };

    public static Function fromArg(String format)
    {
        if (format == null)
            return SH_ONELINE;
        if (format.equalsIgnoreCase("sh"))
            return SH_ONELINE;
        if (format.equalsIgnoreCase("sh-multiline"))
            return SH_MULTILINE;
        if (format.equalsIgnoreCase("cmd"))
            return CMD_ONELINE;
        if (format.equalsIgnoreCase("cmd-multiline"))
            return CMD_MULTILINE;
        if (format.equalsIgnoreCase("powershell"))
            return POWERSHELL_ONELINE;
        if (format.equalsIgnoreCase("powershell-multiline"))
            return POWERSHELL_MULTILINE;
        return SH_ONELINE;
    }

    /**
     * This method applies single quotes suitable for a POSIX compliant shell if
     * necessary.
     *
     * @param input The string to quote if needed
     * @return The quoted string or the original string if quotes are not necessary
     */
    public static String unixShellQuoteIfNeeded(String input)
    {
        // Single quotes are used because double quotes are processed differently by some shells and the xarg
        // command used by jetty.sh

        if (input == null)
            return null;
        if (input.length() == 0)
            return "''";

        int i = 0;
        boolean needsQuoting = false;
        while (!needsQuoting && i < input.length())
        {
            char c = input.charAt(i++);

            // needs quoting unless a limited set of known good characters
            needsQuoting = !(
                (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') ||
                    c == '/' ||
                    c == ':' ||
                    c == '.' ||
                    c == ',' ||
                    c == '+' ||
                    c == '-' ||
                    c == '=' ||
                    c == '_'
                );
        }

        if (!needsQuoting)
            return input;

        StringBuilder builder = new StringBuilder(input.length() * 2);
        builder.append("'");
        builder.append(input, 0, --i);

        while (i < input.length())
        {
            char c = input.charAt(i++);
            if (c == '\'')
            {
                // There is no escape for a literal single quote, so we must leave the quotes
                // and then escape the single quote. We test for the start/end of the string, so
                // we can be less ugly in those cases.
                if (i == 1)
                    builder.insert(0, "\\").append("'");
                else if (i == input.length())
                    builder.append("'\\");
                else
                    builder.append("'\\''");
            }
            else
                builder.append(c);
        }
        builder.append("'");

        return builder.toString();
    }

    /**
     * This method applies Windows Double Quotes suitable for a Windows compliant shells (cmd, powershell) if
     * necessary.
     *
     * @param input The string to quote if needed
     * @return The quoted string or the original string if quotes are not necessary
     */
    public static String windowsShellQuoteIfNeeded(String input)
    {
        if (input == null)
            return null;
        if (input.length() == 0)
            return "\"\""; // this is an empty double quote

        int i = 0;
        boolean needsQuoting = false;
        while (!needsQuoting && i < input.length())
        {
            char c = input.charAt(i++);

            // needs quoting unless a limited set of known good characters
            needsQuoting = !(
                (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') ||
                    c == '/' ||
                    c == ':' ||
                    c == '.' ||
                    c == ',' ||
                    c == '+' ||
                    c == '-' ||
                    c == '=' ||
                    c == '_'
                );
        }

        if (!needsQuoting)
            return input;

        StringBuilder builder = new StringBuilder(input.length() * 2);
        builder.append('"');
        builder.append(input, 0, --i);

        while (i < input.length())
        {
            char c = input.charAt(i++);
            if (c == '\"')
            {
                builder.append("\"\"");
            }
            else
                builder.append(c);
        }
        builder.append('"');

        return builder.toString();
    }
}
