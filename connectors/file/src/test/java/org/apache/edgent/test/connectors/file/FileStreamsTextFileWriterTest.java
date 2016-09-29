/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.edgent.test.connectors.file;

import static org.apache.edgent.test.connectors.common.FileUtil.createTempFile;

//import static org.junit.Assume.assumeFalse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.edgent.connectors.file.CompressedFileWriterPolicy;
import org.apache.edgent.connectors.file.FileStreams;
import org.apache.edgent.connectors.file.FileWriterCycleConfig;
import org.apache.edgent.connectors.file.FileWriterFlushConfig;
import org.apache.edgent.connectors.file.FileWriterPolicy;
import org.apache.edgent.connectors.file.FileWriterRetentionConfig;
import org.apache.edgent.connectors.file.runtime.IFileWriterPolicy;
import org.apache.edgent.function.Predicate;
import org.apache.edgent.test.providers.direct.DirectTestSetup;
import org.apache.edgent.test.topology.TopologyAbstractTest;
import org.apache.edgent.topology.TSink;
import org.apache.edgent.topology.TStream;
import org.apache.edgent.topology.Topology;
import org.apache.edgent.topology.plumbing.PlumbingStreams;
import org.apache.edgent.topology.tester.Condition;
import org.junit.Test;

public class FileStreamsTextFileWriterTest extends TopologyAbstractTest implements DirectTestSetup {
    
    String str = "123456789";
    String[] stdLines = new String[] {
            "1-"+str,
            "2-"+str,
            "3-"+str,
            "4-"+str
    };
    
    private int TMO_SEC = 2;

    public String getStr() {
        return str;
    }

    public String[] getLines() {
        return stdLines;
    }

    @Test
    public void testFlushConfig() throws Exception {
        FileWriterFlushConfig<String> cfg;

        String trueTuple = "true";
        String falseTuple = "false";
        Predicate<String> p = tuple -> tuple.equals("true");

        cfg = FileWriterFlushConfig.newImplicitConfig();
        checkFileWriterConfig(cfg, 0, 0, null, trueTuple, falseTuple);
        
        cfg = FileWriterFlushConfig.newCountBasedConfig(3);
        checkFileWriterConfig(cfg, 3, 0, null, trueTuple, falseTuple);
        expectIAE(() -> FileWriterFlushConfig.newCountBasedConfig(0));
        
        cfg = FileWriterFlushConfig.newTimeBasedConfig(10);
        checkFileWriterConfig(cfg, 0, 10, null, trueTuple, falseTuple);
        expectIAE(() -> FileWriterFlushConfig.newTimeBasedConfig(0));
        
        cfg = FileWriterFlushConfig.newPredicateBasedConfig(p);
        checkFileWriterConfig(cfg, 0, 0, p, trueTuple, falseTuple);
        expectIAE(() -> FileWriterFlushConfig.newPredicateBasedConfig(null));
        
        cfg = FileWriterFlushConfig.newConfig(1, 2, p);
        checkFileWriterConfig(cfg, 1, 2, p, trueTuple, falseTuple);
        cfg = FileWriterFlushConfig.newConfig(0, 0, null);
        checkFileWriterConfig(cfg, 0, 0, null, trueTuple, falseTuple);
        expectIAE(() -> FileWriterFlushConfig.newConfig(-1, 0, null));
        expectIAE(() -> FileWriterFlushConfig.newConfig(0, -1, null));
    }
    
    private static <T> void checkFileWriterConfig(FileWriterFlushConfig<T> cfg,
            int cntTuples, long periodMsec, Predicate<T> tuplePredicate,
            T trueTuple, T falseTuple) {
        assertEquals(cntTuples, cfg.getCntTuples());
        assertEquals(periodMsec, cfg.getPeriodMsec());
        assertEquals(tuplePredicate, cfg.getTuplePredicate());
        cfg.toString();
       
        int falseNTuples = cntTuples==1 ? 0 : cntTuples+1;
        int trueNTuples = 3*cntTuples;
        
        assertFalse("cntTuples:"+cntTuples+" pred:"+tuplePredicate,
                    cfg.evaluate(falseNTuples, falseTuple));
        if (cntTuples!=0)
            assertTrue("cntTuples:"+cntTuples+" pred:"+tuplePredicate,
                    cfg.evaluate(trueNTuples, falseTuple));
        if (tuplePredicate!=null)
            assertTrue("cntTuples:"+cntTuples+" pred:"+tuplePredicate,
                    cfg.evaluate(falseNTuples, trueTuple));
    }

    @Test
    public void testCycleConfig() throws Exception {
        FileWriterCycleConfig<String> cfg;

        String trueTuple = "true";
        String falseTuple = "false";
        Predicate<String> p = tuple -> tuple.equals("true");
        
        cfg = FileWriterCycleConfig.newFileSizeBasedConfig(2);
        checkFileWriterConfig(cfg, 2, 0, 0, null, trueTuple, falseTuple);
        expectIAE(() -> FileWriterCycleConfig.newFileSizeBasedConfig(0));
        
        cfg = FileWriterCycleConfig.newCountBasedConfig(3);
        checkFileWriterConfig(cfg, 0, 3, 0, null, trueTuple, falseTuple);
        expectIAE(() -> FileWriterCycleConfig.newCountBasedConfig(0));
        
        cfg = FileWriterCycleConfig.newTimeBasedConfig(10);
        checkFileWriterConfig(cfg, 0, 0, 10, null, trueTuple, falseTuple);
        expectIAE(() -> FileWriterCycleConfig.newTimeBasedConfig(0));
        
        cfg = FileWriterCycleConfig.newPredicateBasedConfig(p);
        checkFileWriterConfig(cfg, 0, 0, 0, p, trueTuple, falseTuple);
        expectIAE(() -> FileWriterCycleConfig.newPredicateBasedConfig(null));
        
        cfg = FileWriterCycleConfig.newConfig(1, 2, 3, p);
        checkFileWriterConfig(cfg, 1, 2, 3, p, trueTuple, falseTuple);
        expectIAE(() -> FileWriterCycleConfig.newConfig(0, 0, 0, null));
        expectIAE(() -> FileWriterCycleConfig.newConfig(-1, 0, 0, null));
        expectIAE(() -> FileWriterCycleConfig.newConfig(0, -1, 0, null));
        expectIAE(() -> FileWriterCycleConfig.newConfig(0, 0, -1, null));
    }
    
    private static <T> void checkFileWriterConfig(FileWriterCycleConfig<T> cfg,
            long fileSize, int cntTuples, long periodMsec, Predicate<T> tuplePredicate,
            T trueTuple, T falseTuple) {
        assertEquals(fileSize, cfg.getFileSize());
        assertEquals(cntTuples, cfg.getCntTuples());
        assertEquals(periodMsec, cfg.getPeriodMsec());
        assertEquals(tuplePredicate, cfg.getTuplePredicate());
        cfg.toString();
        
        long falseFileSize = fileSize-1;
        long trueFileSize = fileSize+1;
        int falseNTuples = cntTuples==1 ? 0 : cntTuples+1;
        int trueNTuples = 3*cntTuples;
        
        assertFalse("fileSize:"+fileSize+" cntTuples:"+cntTuples+" pred:"+tuplePredicate,
                    cfg.evaluate(falseFileSize, falseNTuples, falseTuple));
        if (fileSize!=0)
            assertTrue("fileSize:"+fileSize+" cntTuples:"+cntTuples+" pred:"+tuplePredicate,
                    cfg.evaluate(trueFileSize, trueNTuples, falseTuple));
        if (cntTuples!=0)
            assertTrue("fileSize:"+fileSize+" cntTuples:"+cntTuples+" pred:"+tuplePredicate,
                    cfg.evaluate(falseFileSize, trueNTuples, falseTuple));
        if (tuplePredicate!=null)
            assertTrue("fileSize:"+fileSize+" cntTuples:"+cntTuples+" pred:"+tuplePredicate,
                    cfg.evaluate(falseFileSize, falseNTuples, trueTuple));
    }

    @Test
    public void testRetentionConfig() throws Exception {
        FileWriterRetentionConfig cfg;

        cfg = FileWriterRetentionConfig.newFileCountBasedConfig(2);
        checkFileWriterConfig(cfg, 2, 0, 0, 0);
        expectIAE(() -> FileWriterRetentionConfig.newFileCountBasedConfig(0));
        
        cfg = FileWriterRetentionConfig.newAggregateFileSizeBasedConfig(3);
        checkFileWriterConfig(cfg, 0, 3, 0, 0);
        expectIAE(() -> FileWriterRetentionConfig.newAggregateFileSizeBasedConfig(0));
        
        cfg = FileWriterRetentionConfig.newAgeBasedConfig(10,11);
        checkFileWriterConfig(cfg, 0, 0, 10, 11);
        expectIAE(() -> FileWriterRetentionConfig.newAgeBasedConfig(0,1));
        expectIAE(() -> FileWriterRetentionConfig.newAgeBasedConfig(1,0));
        
        cfg = FileWriterRetentionConfig.newConfig(1, 2, 3, 0);
        checkFileWriterConfig(cfg, 1, 2, 3, 0);
        expectIAE(() -> FileWriterRetentionConfig.newConfig(0, 0, 0, 0));
        expectIAE(() -> FileWriterRetentionConfig.newConfig(0, 0, 1, 0));
        expectIAE(() -> FileWriterRetentionConfig.newConfig(0, 0, 0, 1));
        expectIAE(() -> FileWriterRetentionConfig.newConfig(-1, 0, 0, 0));
        expectIAE(() -> FileWriterRetentionConfig.newConfig(0, -1, 0, 0));
        expectIAE(() -> FileWriterRetentionConfig.newConfig(0, 0, -1, 0));
        expectIAE(() -> FileWriterRetentionConfig.newConfig(0, 0, 0, -1));
    }
    
    private void expectIAE(Runnable fn) {
        try { 
            fn.run();
            fail("expected IAE");
        } catch (IllegalArgumentException e) { /* expected */ }
    }
    
    private static <T> void checkFileWriterConfig(FileWriterRetentionConfig cfg,
            int fileCnt, long aggSize, long ageSec, long periodMsec) {
        assertEquals(fileCnt, cfg.getFileCount());
        assertEquals(aggSize, cfg.getAggregateFileSize());
        assertEquals(ageSec, cfg.getAgeSec());
        assertEquals(periodMsec, cfg.getPeriodMsec());
        cfg.toString();

        int falseFileCnt = fileCnt-1;
        int trueFileCnt = fileCnt+1;
        long falseAggSize = aggSize-1;
        long trueAggSize = aggSize+1;
        
        assertFalse("fileCnt:"+fileCnt+" aggSize:"+aggSize,
                    cfg.evaluate(falseFileCnt, falseAggSize));
        if (fileCnt!=0)
            assertTrue("fileCnt:"+fileCnt+" aggSize:"+aggSize,
                    cfg.evaluate(trueFileCnt, falseAggSize));
        if (aggSize!=0)
            assertTrue("fileCnt:"+fileCnt+" aggSize:"+aggSize,
                    cfg.evaluate(falseFileCnt, trueAggSize));
    }

    @Test
    public void testDefaultConfig() throws Exception {
        FileWriterPolicy<String> policy = new FileWriterPolicy<>();
        checkFileWriterConfig(policy.getFlushConfig(), 0, TimeUnit.SECONDS.toMillis(10), null, null, null);
        checkFileWriterConfig(policy.getCycleConfig(), 1*1024*1024, 0, 0, null, null, null);
        checkFileWriterConfig(policy.getRetentionConfig(), 10, 0, 0, 0);
        policy.toString();
        policy.close();
    }

    @Test
    public void testNoFilesCreated() throws Exception {
        // complete before any files are generated
        Topology t = newTopology("testNoFilesCreated");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        // build expected results
        List<List<String>> expResults = Collections.emptyList();

        TStream<String> s = t.events(eventSetup -> { /* no tuples generated */ });
        
        FileStreams.textFileWriter(s, () -> basePath.toString());

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testOneFileCreated() throws Exception {
        // all lines into a single (the first) file
        Topology t = newTopology("testOneFileCreated");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();

        // build expected results
        // net all in one, the first, file
        List<List<String>> expResults = buildExpResults(lines, tuple -> false);
        assertEquals(1, expResults.size());
        
        TStream<String> s = t.strings(lines);
        
        // default writer policy
        TSink<String> sink = FileStreams.textFileWriter(s, () -> basePath.toString());

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
        
        assertNotNull(sink);
    }

    @Test
    public void testManyFiles() throws Exception {
        Topology t = newTopology("testManyFiles");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();

        // build expected results
        // net one tuples per file
        List<List<String>> expResults = buildExpResults(lines, tuple -> true);

        // in this config files are create very fast hence they end
        // up exercising the _<n> suffix to basePath_YYYYMMDD_HHMMSS
        
        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),  // no extra flush
                FileWriterCycleConfig.newCountBasedConfig(1), // yield one line per file
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testManyFilesSlow() throws Exception {
        Topology t = newTopology("testManyFilesSlow");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();

        // build expected results
        // net one tuples per file
        List<List<String>> expResults = buildExpResults(lines, tuple -> true);
        
        // add delay so we get different files w/o a _<n> suffix
        
        int throttleSec = 2;
        TStream<String> s = PlumbingStreams.blockingThrottle(
                t.strings(lines), throttleSec, TimeUnit.SECONDS);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),  // no extra flush
                FileWriterCycleConfig.newCountBasedConfig(1), // yield one line per file
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, (lines.length*throttleSec)+TMO_SEC,
                basePath, expResults);
    }

    @Test
    public void testRetainCntBased() throws Exception {
        // more lines than configured retained numFiles; only keep the last numFiles
        Topology t = newTopology("testRetainCntBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();
        
        // build expected results
        // net one tuples per file
        List<List<String>> expResults = buildExpResults(lines, tuple -> true);
        int keepCnt = 2;  // only keep the last n files
        for (int i = 0; i < keepCnt; i++)
            expResults.remove(0);
        assertEquals(keepCnt, expResults.size());
        
        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newCountBasedConfig(1),
                FileWriterRetentionConfig.newFileCountBasedConfig(keepCnt)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);
        
        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testRetainAggSizeBased() throws Exception {
        // more aggsize than configured; only keep aggsize worth
        Topology t = newTopology("testRetainAggSizeBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();
        
        // build expected results
        // net one tuple per file
        List<List<String>> expResults = buildExpResults(lines, tuple -> true);
        // agg size only enough for last two lines
        long aggregateFileSize = 2 * (("1-"+getStr()).getBytes().length + 1/*eol*/);
        expResults.remove(0);
        expResults.remove(0);
        assertEquals(2, expResults.size());
        
        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newCountBasedConfig(1),
                FileWriterRetentionConfig.newAggregateFileSizeBasedConfig(aggregateFileSize)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);
        
        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testRetainAgeBased() throws Exception {
        Topology t = newTopology("testRetainAgeBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();
        
        // build expected results
        int keepCnt = 2;  // only keep the last n files with throttling, age,
                          // and TMO_SEC
        int ageSec = 5;
        long periodMsec = TimeUnit.SECONDS.toMillis(1);
        // net one tuple per file
        List<List<String>> expResults = buildExpResults(lines, tuple -> true);
        for (int i = 0; i < keepCnt; i++)
            expResults.remove(0);
        assertEquals(keepCnt, expResults.size());
        
        // add delay so we can age out things
        //
        // After several runs this test seems reliable but
        // I suspect it may be fragile wrt timing hence the results.
        //
        // With 4 tuples, throttleDelay=2sec, and ageSec=5
        // t0=add-f1, t1, t2=add-f2, t3, t4=add-f3, t5-rm-f1, t6=add-f4, t7=rm-f2, t8, t9=rm-f3, ...
        //
        // So we want to check somewhere around t8 (after t7 and definitely before t9)
        // so all 4 files were created and the first 2 have been aged out.
        // with complete delay = #files-1*throttle + TMO_SEC, should be 6+2 == t8.
        
        int throttleSec = 2;
        TStream<String> s = PlumbingStreams.blockingThrottle(
                t.strings(lines), throttleSec, TimeUnit.SECONDS);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newCountBasedConfig(1),
                FileWriterRetentionConfig.newAgeBasedConfig(ageSec, periodMsec)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);
        
        completeAndValidateWriter(t, ((lines.length-1)*throttleSec)+TMO_SEC,
                basePath, expResults);
    }

    @Test
    public void testFlushImplicit() throws Exception {
        Topology t = newTopology("testFlushImplicit");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();

        // build expected results
        // net all in one, the first, file
        List<List<String>> expResults = buildExpResults(lines, tuple -> false);

        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newCountBasedConfig(1000),
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testFlushCntBased() throws Exception {
        Topology t = newTopology("testFlushCntBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();

        // build expected results
        // net all in one, the first, file
        List<List<String>> expResults = buildExpResults(lines, tuple -> false);

        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newCountBasedConfig(1),  // every tuple
                FileWriterCycleConfig.newCountBasedConfig(1000),  // all in 1 file
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testFlushTimeBased() throws Exception {
        Topology t = newTopology("testFlushTimeBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();

        // build expected results
        // net all in one, the first, file
        List<List<String>> expResults = buildExpResults(lines, tuple -> false);
        
        // add delay so time flush happens
        
        int throttleSec = 1;
        TStream<String> s = PlumbingStreams.blockingThrottle(
                t.strings(lines), throttleSec, TimeUnit.SECONDS);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newTimeBasedConfig(TimeUnit.MILLISECONDS.toMillis(250)),
                FileWriterCycleConfig.newCountBasedConfig(1000),  // all in 1 file
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, (lines.length*throttleSec)+TMO_SEC,
                basePath, expResults);
    }

    @Test
    public void testFlushTupleBased() throws Exception {
        Topology t = newTopology("testFlushTupleBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();

        // build expected results
        // net all in one, the first, file
        List<List<String>> expResults = buildExpResults(lines, tuple -> false);

        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newPredicateBasedConfig(
                        tuple -> tuple.startsWith("1-") || tuple.startsWith("3-")),
                FileWriterCycleConfig.newCountBasedConfig(1000),  // all in 1 file
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testCycleCntBased() throws Exception {
        Topology t = newTopology("testCycleCntBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();
        
        // build expected results
        // net two tuples per file
        int cntTuples = 2;
        AtomicInteger cnt = new AtomicInteger();
        Predicate<String> cycleIt = tuple -> cnt.incrementAndGet() % cntTuples == 0;
        List<List<String>> expResults = buildExpResults(lines, cycleIt);
        assertEquals(lines.length / cntTuples, expResults.size());

        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newCountBasedConfig(cntTuples),
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testCycleSizeBased() throws Exception {
        Topology t = newTopology("testCycleSizeBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();
        
        // build expected results
        // net one tuple per file 
        List<List<String>> expResults = buildExpResults(lines, tuple -> true);
        int fileSize = 2;

        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newFileSizeBasedConfig(fileSize),
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testCycleTimeBased() throws Exception {
        Topology t = newTopology("testCycleTimeBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();
        
        // build expected results
        // net one tuple per file with 250msec cycle config and 1 throttle
        List<List<String>> expResults = buildExpResults(lines, tuple -> true);
        
        // add delay so time cycle happens
        // also verifies only cycle if there's something to cycle
        // (i.e., these cycles happen faster than tuples are written)
        
        int throttleSec = 1;
        TStream<String> s = PlumbingStreams.blockingThrottle(
                t.strings(lines), throttleSec, TimeUnit.SECONDS);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newTimeBasedConfig(TimeUnit.MILLISECONDS.toMillis(250)),
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, (lines.length*throttleSec)+TMO_SEC,
                basePath, expResults);
    }

    @Test
    public void testCycleTupleBased() throws Exception {
        Topology t = newTopology("testCycleTupleBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();

        // build expected results
        // a tuple based config / predicate.  in this case should end up with 3 files.
        Predicate<String> cycleIt = tuple -> tuple.startsWith("1-") || tuple.startsWith("3-");
        List<List<String>> expResults = buildExpResults(lines, cycleIt);
        assertEquals(3, expResults.size());

        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newPredicateBasedConfig(cycleIt),
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testAllTimeBased() throws Exception {
        // exercise case with multiple timer based policies
        Topology t = newTopology("testAllTimeBased");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();
        
        // build expected results
        // keep all given age and TMO_SEC
        int ageSec = 10;
        long periodMsec = TimeUnit.SECONDS.toMillis(1);
        // net one tuple per file
        List<List<String>> expResults = buildExpResults(lines, tuple -> true);
        
        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newTimeBasedConfig(TimeUnit.MILLISECONDS.toMillis(250)),
                FileWriterCycleConfig.newConfig(1, 2000, TimeUnit.SECONDS.toMillis(10), null),
                FileWriterRetentionConfig.newAgeBasedConfig(ageSec, periodMsec)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);
        
        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    @Test
    public void testWriterWatcherReader() throws Exception {
        // verify all the pieces work together
        Topology t = newTopology("testWriterWatcherReader");
        
        String testDirPrefix = "testWriterWatcherReader";
        Path dir = Files.createTempDirectory(testDirPrefix);
        Path basePath = dir.resolve("writerCreated");
        
        String[] lines = getLines();

        System.out.println("########## "+t.getName());
        
        // Write the files
        // add delay so watcher starts first
        int throttleSec = 2;
        TStream<String> contents = PlumbingStreams.blockingThrottle(
                t.strings(lines), throttleSec, TimeUnit.SECONDS);
        
        IFileWriterPolicy<String> policy = new FileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newCountBasedConfig(1),  // one per file
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(contents, () -> basePath.toString(), () -> policy);
        
        // Watch and read contents
        TStream<String> pathnames = FileStreams.directoryWatcher(t,
                () -> dir.toAbsolutePath().toString());
        pathnames.sink(tuple -> System.out.println("watcher added "+tuple));
        pathnames.peek(tuple -> { if (new File(tuple).getName().startsWith("."))
            throw new RuntimeException("Not filtering active/hidden files "+tuple); });
        TStream<String> readContents = FileStreams.textFileReader(pathnames);

        boolean dump = true;
        try {
            completeAndValidate("", t, readContents,
                    (lines.length*throttleSec)+TMO_SEC, lines);
            dump = false;
        }
        finally {
            deleteDirAndFiles(dir, testDirPrefix, dump);
        }
    }

    @Test
    public void testCompressedFileWriterPolicy() throws Exception {
        Topology t = newTopology("testCompressedFileWriterPolicy");
        
        // establish a base path
        Path basePath = createTempFile("test1", "txt", new String[0]);
        
        String[] lines = getLines();
        
        // build expected results
        // net 2 tuples per file
        int cntTuples = 2;
        AtomicInteger cnt = new AtomicInteger();
        Predicate<String> cycleIt = tuple -> cnt.incrementAndGet() % cntTuples == 0;
        List<List<String>> expResults = buildExpResults(lines, cycleIt);
        assertEquals(lines.length / cntTuples, expResults.size());

        TStream<String> s = t.strings(lines);
        
        IFileWriterPolicy<String> policy = new CompressedFileWriterPolicy<String>(
                FileWriterFlushConfig.newImplicitConfig(),
                FileWriterCycleConfig.newCountBasedConfig(cntTuples),
                FileWriterRetentionConfig.newFileCountBasedConfig(10)
                );
        FileStreams.textFileWriter(s, () -> basePath.toString(), () -> policy);

        completeAndValidateWriter(t, TMO_SEC, basePath, expResults);
    }

    private void deleteDirAndFiles(Path dir, String dirPrefix, boolean dump) {
        // exercise caution before removing all files in dir
        if (!dirPrefix.startsWith("test"))
            throw new IllegalStateException("Yikes. dir:"+dir+" dirPrefix:"+dirPrefix);
        String leaf = dir.getFileName().toString();
        if (!leaf.startsWith(dirPrefix))
            throw new IllegalStateException("Yikes. dir:"+dir+" dirPrefix:"+dirPrefix);
        
        // Ok, delete all the files in the dir and then the dir
        for (File file : dir.toFile().listFiles()) {
            if (dump)
                dumpFile(file);
            file.delete();
        }
        dir.toFile().delete();
    }
    
    private void dumpFile(File f) {
        System.out.println("<<<<< Dumping "+f);
        try {
            Path path = f.toPath();
            try (BufferedReader br = Files.newBufferedReader(path)) {
                br.lines().forEach(line -> System.out.println(line));
            }
        }
        catch (Exception e) {
            System.out.println("##### exception: " + e.getLocalizedMessage());
        }
        System.out.println(">>>>> DONE "+f);
    }
    
    private <T> List<List<T>> buildExpResults(T[] tuples, Predicate<T> cycleIt) {
        List<List<T>> expResults = new ArrayList<>();
        List<T> oneFile = null;
        for (T tuple : tuples) {
            if (oneFile==null) {
                oneFile = new ArrayList<>();
                expResults.add(oneFile);
            }
            oneFile.add(tuple);
            if (cycleIt.test(tuple))
                oneFile = null;
        }
        return expResults;
    }
    
    private <T> void completeAndValidateWriter(Topology t, int tmoSec,
            Path basePath, List<List<T>> expResults) throws Exception {
        
        try {
            // just let it run to the tmo before we check the file contents
            Condition<Object> tc = new Condition<Object>() {
                public boolean valid() { return false; }
                public Object getResult() { return null; }
            };
            
            complete(t, tc, tmoSec, TimeUnit.SECONDS);

            System.out.println("########## "+t.getName());

            // right number of files?
            List<Path> actFiles = getActFiles(basePath);
            System.out.println("actFiles: "+actFiles);
            assertEquals(actFiles.toString(), expResults.size(), actFiles.size());
            
            // do the file(s) have the right contents?
            System.out.println("expResults: "+expResults);
            int i = 0;
            for (List<T> expFile : expResults) {
                Path path = actFiles.get(i++);
                checkContents(path, expFile.toArray(new String[0]));
            }
        }
        finally {
            deleteAll(basePath);
        }
    }
    
    private void deleteAll(Path basePath) {
        Path parent = basePath.getParent();
        String baseLeaf = basePath.getFileName().toString();
        String[] actLeafs = parent.toFile().list(
                (dir,leaf) -> leaf.startsWith(baseLeaf));
        for (String leaf : actLeafs) {
            parent.resolve(leaf).toFile().delete();
        }
    }
    
    private List<Path> getActFiles(Path basePath) {
        List<Path> paths = new ArrayList<>();
        Path parent = basePath.getParent();
        String baseLeaf = basePath.getFileName().toString();
        String[] actLeafs = parent.toFile().list(
                (dir,leaf) -> leaf.startsWith(baseLeaf+"_"));
        Arrays.sort(actLeafs, (o1,o2) -> o1.compareTo(o2));
        for (String leaf : actLeafs) {
            paths.add(parent.resolve(leaf));
        }
        return paths;
    }
    
    private void checkContents(Path path, String[] lines) {
        if (path.getFileName().toString().endsWith(".zip")) {
          checkZipContents(path, lines);
          return;
        }
        System.out.println("checking file "+path);
        int lineCnt = 0;
        try (BufferedReader br = Files.newBufferedReader(path)) {
            for (String line : lines) {
                ++lineCnt;
                String actLine = br.readLine();
                assertEquals("path:"+path+" line "+lineCnt, line, actLine);
            }
            assertNull("path:"+path+" line "+lineCnt+" expected EOF", br.readLine());
        }
        catch (IOException e) {
            assertNull("path:"+path+" line "+lineCnt+" unexpected IOException "+e, e);
        }
    }
    
    private void checkZipContents(Path path, String[] lines) {
        System.out.println("checking file "+path);
        String fileName = path.getFileName().toString();
        String entryName = fileName.substring(0, fileName.length() - ".zip".length());
        int lineCnt = 0;
        try (
            FileInputStream fis = new FileInputStream(path.toFile());
            ZipInputStream zin = new ZipInputStream((new BufferedInputStream(fis)));
            )
        {
          ZipEntry entry = zin.getNextEntry();
          
          assertEquals(entryName, entry.getName());

          BufferedReader br = new BufferedReader(new InputStreamReader(zin));
          for (String line : lines) {
            ++lineCnt;
            String actLine = br.readLine();
            assertEquals("path:"+path+" line "+lineCnt, line, actLine);
          }
          assertNull("path:"+path+" line "+lineCnt+" expected EOF", br.readLine());
        }
        catch (IOException e) {
          assertNull("path:"+path+" line "+lineCnt+" unexpected IOException "+e, e);
        }
    }
}
