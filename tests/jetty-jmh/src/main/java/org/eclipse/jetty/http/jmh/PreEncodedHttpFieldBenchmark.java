//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.http.jmh;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.util.BufferUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@Threads(4)
@Warmup(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
public class PreEncodedHttpFieldBenchmark
{
    final PreEncodedHttpField preEncodedHttpField = new PreEncodedHttpField(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE.asString());
    final ByteBuffer buffer = BufferUtil.allocateDirect(19);
    final ByteBuffer srcBuf = BufferUtil.allocate(19);
    final byte[] srcArray = "Connection: close\n\r".getBytes(StandardCharsets.ISO_8859_1);

    @Setup
    public void setupSrcBuf()
    {
        BufferUtil.clearToFill(buffer);
        buffer.put("Connection: close\n\r".getBytes(StandardCharsets.ISO_8859_1));
        BufferUtil.flipToFlush(buffer, 0);
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public void testPreEncodedHttpField()
    {
        BufferUtil.clearToFill(buffer);
        preEncodedHttpField.putTo(buffer, HttpVersion.HTTP_1_1);
        BufferUtil.flipToFlush(buffer, 0);
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public void testByteBuffer()
    {
        BufferUtil.clearToFill(buffer);
        buffer.put(0, srcBuf, 0, srcBuf.remaining());
        BufferUtil.flipToFlush(buffer, 0);
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public void testByteArray()
    {
        BufferUtil.clearToFill(buffer);
        buffer.put(srcArray);
        BufferUtil.flipToFlush(buffer, 0);
    }

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(PreEncodedHttpFieldBenchmark.class.getSimpleName())
            .warmupIterations(5)
            .measurementIterations(5)
//            .addProfiler(AsyncProfiler.class, "output=flamegraph") // requires env var LD_LIBRARY_PATH=/path/to/async-profiler-linux-x64/build
            .forks(1)
            .threads(1)
            .build();

        new Runner(opt).run();
    }
}


