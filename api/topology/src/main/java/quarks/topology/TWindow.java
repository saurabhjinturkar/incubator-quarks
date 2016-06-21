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
package quarks.topology;

import java.util.List;

import quarks.function.BiFunction;
import quarks.function.Function;

/**
 * Partitioned window of tuples. Logically a window
 * represents an continuously updated ordered list of tuples according to the
 * criteria that created it. For example {@link TStream#last(int, Function) s.last(10, zero())}
 * declares a window with a single partition that at any time contains the last ten tuples seen on
 * stream {@code s}.
 * <P>
 * Windows are partitioned which means the window's configuration
 * is independently maintained for each key seen on the stream.
 * For example with a window created using {@link TStream#last(int, Function) last(3, tuple -> tuple.getId())}
 * then each key has its own window containing the last
 * three tuples with the same key obtained from the tuple's identity using {@code getId()}.
 * </P>
 *
 * @param <T> Tuple type
 * @param <K> Partition key type
 * 
 * @see TStream#last(int, Function) Count based window
 * @see TStream#last(long, java.util.concurrent.TimeUnit, Function) Time based window
 */
public interface TWindow<T, K> extends TopologyElement {
    /**
     * Declares a stream that is a continuous aggregation of
     * partitions in this window. Each time the contents of a partition is updated by a new
     * tuple being added to it, or tuples being evicted
     * {@code aggregator.apply(tuples, key)} is called, where {@code tuples} is an
     * {@code List} that containing all the tuples in the partition.
     * The {@code List} is stable during the method call, and returns the
     * tuples in order of insertion into the window, from oldest to newest. <BR>
     * Thus the returned stream will contain a sequence of tuples that where the
     * most recent tuple represents the most up to date aggregation of a
     * partition.
     *
     * @param <U> Tuple type
     * @param aggregator
     *            Logic to aggregation a partition.
     * @return A stream that contains the latest aggregations of partitions in this window.
     */
    <U> TStream<U> aggregate(BiFunction<List<T>, K, U> aggregator);
    
    /**
     * Declares a stream that represents a batched aggregation of
     * partitions in this window. Each time the contents of a partition equals 
     * the window size or the time duration,
     * {@code batcher.apply(tuples, key)} is called, where {@code tuples} is an
     * {@code List} that containing all the tuples in the partition.
     * The {@code List} is stable during the method call, and returns the
     * tuples in order of insertion into the window, from oldest to newest. <BR>
     * Thus the returned stream will contain a sequence of tuples that where 
     * each tuple represents the output of the most recent batch of a partition.
     * The tuples contained in a partition during a batch do not overlap with 
     * the tuples in any subsequent batch. After a partition is batched, its 
     * contents are cleared.
     * 
     * @param <U> Tuple type
     * @param batcher
     *            Logic to aggregation a partition.
     * @return A stream that contains the latest aggregations of partitions in this window.
     */
    <U> TStream<U> batch(BiFunction<List<T>, K, U> batcher);
    
    /**
     * Returns the key function used to map tuples to partitions.
     * @return Key function used to map tuples to partitions.
     */
    Function<T, K> getKeyFunction();
    
    /**
     * Get the stream that feeds this window.
     * @return stream that feeds this window.
     */
    TStream<T> feeder();
}
