/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint;

import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * A reporter which emits lint results into an XML report.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class XmlReporter extends Reporter {
    private final Writer mWriter;

    /**
     * Constructs a new {@link XmlReporter}
     *
     * @param client the client
     * @param output the output file
     * @throws IOException if an error occurs
     */
    public XmlReporter(Main client, File output) throws IOException {
        super(client, output);
        mWriter = new BufferedWriter(Files.newWriter(output, Charsets.UTF_8));
    }

    @Override
    public void write(int errorCount, int warningCount, List<Warning> issues) throws IOException {
        mWriter.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +     //$NON-NLS-1$
                "<issues>\n");                                       //$NON-NLS-1$

        if (issues.size() > 0) {
            for (Warning warning : issues) {
                mWriter.write('\n');
                indent(mWriter, 1);
                mWriter.write("<issue"); //$NON-NLS-1$
                writeAttribute(mWriter, 2, "id", warning.issue.getId());   //$NON-NLS-1$
                writeAttribute(mWriter, 2, "severity", warning.severity.getDescription()); //$NON-NLS-1$
                writeAttribute(mWriter, 2, "message", warning.message);  //$NON-NLS-1$
                assert (warning.file != null) == (warning.location != null);

                if (warning.file != null) {
                    assert warning.location.getFile() == warning.file;
                }

                Location location = warning.location;
                if (location != null) {
                    mWriter.write(">\n"); //$NON-NLS-1$
                    while (location != null) {
                        indent(mWriter, 2);
                        mWriter.write("<location"); //$NON-NLS-1$
                        String path = mClient.getDisplayPath(warning.project, location.getFile());
                        writeAttribute(mWriter, 3, "file", path);  //$NON-NLS-1$
                        Position start = location.getStart();
                        if (start != null) {
                            int line = start.getLine();
                            int column = start.getColumn();
                            if (line >= 0) {
                                // +1: Line numbers internally are 0-based, report should be
                                // 1-based.
                                writeAttribute(mWriter, 3, "line",         //$NON-NLS-1$
                                        Integer.toString(line + 1));
                                if (column >= 0) {
                                    writeAttribute(mWriter, 3, "column",   //$NON-NLS-1$
                                            Integer.toString(column + 1));
                                }
                            }
                        }

                        mWriter.write("/>\n"); //$NON-NLS-1$
                        location = location.getSecondary();
                    }
                    indent(mWriter, 1);
                    mWriter.write("</issue>\n"); //$NON-NLS-1$
                } else {
                    mWriter.write('\n');
                    indent(mWriter, 1);
                    mWriter.write("/>\n");  //$NON-NLS-1$
                }
            }
        }

        mWriter.write("\n</issues>\n");       //$NON-NLS-1$
        mWriter.close();

        String path = mOutput.getAbsolutePath();
        System.out.println(String.format("Wrote HTML report to %1$s", path));
    }

    private static void writeAttribute(Writer writer, int indent, String name, String value)
            throws IOException {
        writer.write('\n');
        indent(writer, indent);
        writer.write(name);
        writer.write('=');
        writer.write('"');
        for (int i = 0, n = value.length(); i < n; i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    writer.write("&quot;"); //$NON-NLS-1$
                    break;
                case '\'':
                    writer.write("&apos;"); //$NON-NLS-1$
                    break;
                case '&':
                    writer.write("&amp;");  //$NON-NLS-1$
                    break;
                case '<':
                    writer.write("&lt;");   //$NON-NLS-1$
                    break;
                default:
                    writer.write(c);
                    break;
            }
        }
        writer.write('"');
    }

    private static void indent(Writer writer, int indent) throws IOException {
        for (int level = 0; level < indent; level++) {
            writer.write("    "); //$NON-NLS-1$
        }
    }
}