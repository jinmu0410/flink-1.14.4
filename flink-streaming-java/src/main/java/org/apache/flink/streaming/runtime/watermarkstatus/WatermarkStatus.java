/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.watermarkstatus;

import org.apache.flink.annotation.Internal;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;

/**
 * A Watermark Status element informs stream tasks whether or not they should continue to expect
 * watermarks from the input stream that sent them. There are 2 kinds of status, namely {@link
 * WatermarkStatus#IDLE} and {@link WatermarkStatus#ACTIVE}. Watermark Status elements are generated
 * at the sources, and may be propagated through the tasks of the topology. They directly infer the
 * current status of the emitting task; a {@link SourceStreamTask} or {@link StreamTask} emits a
 * {@link WatermarkStatus#IDLE} if it will temporarily halt to emit any watermarks (i.e. is idle),
 * and emits a {@link WatermarkStatus#ACTIVE} once it resumes to do so (i.e. is active). Tasks are
 * responsible for propagating their status further downstream once they toggle between being idle
 * and active. The cases that source tasks and downstream tasks are considered either idle or active
 * is explained below:
 *
 * <ul>
 *   <li>Source tasks: A source task is considered to be idle if its head operator, i.e. a {@link
 *       StreamSource}, will not emit watermarks for an indefinite amount of time. This is the case,
 *       for example, for Flink's Kafka Consumer, where sources might initially have no assigned
 *       partitions to read from, or no records can be read from the assigned partitions. Once the
 *       head {@link StreamSource} operator detects that it will resume emitting data, the source
 *       task is considered to be active. {@link StreamSource}s are responsible for toggling the
 *       status of the containing source task and ensuring that no watermarks will be emitted while
 *       the task is idle. This guarantee should be enforced on sources through {@link
 *       org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext}
 *       implementations.
 *   <li>Downstream tasks: a downstream task is considered to be idle if all its input streams are
 *       idle, i.e. the last received Watermark Status element from all input streams is a {@link
 *       WatermarkStatus#IDLE}. As long as one of its input streams is active, i.e. the last
 *       received Watermark Status element from the input stream is {@link WatermarkStatus#ACTIVE},
 *       the task is active.
 * </ul>
 *
 * <p>Watermark Status elements received at downstream tasks also affect and control how their
 * operators process and advance their watermarks. The below describes the effects (the logic is
 * implemented as a {@link StatusWatermarkValve} which downstream tasks should use for such
 * purposes):
 *
 * <ul>
 *   <li>Since there may be watermark generators that might produce watermarks anywhere in the
 *       middle of topologies regardless of whether there are input data at the operator, the
 *       current status of the task must be checked before forwarding watermarks emitted from an
 *       operator. If the status is actually idle, the watermark must be blocked.
 *   <li>For downstream tasks with multiple input streams, the watermarks of input streams that are
 *       temporarily idle, or has resumed to be active but its watermark is behind the overall min
 *       watermark of the operator, should not be accounted for when deciding whether or not to
 *       advance the watermark and propagated through the operator chain.
 * </ul>
 *
 * <p>Note that to notify downstream tasks that a source task is permanently closed and will no
 * longer send any more elements, the source should still send a {@link Watermark#MAX_WATERMARK}
 * instead of {@link WatermarkStatus#IDLE}. Watermark Status elements only serve as markers for
 * temporary status.
 */

/**
 * Watermark Status 元素通知流任务它们是否应该继续期望来自发送它们的输入流的水印。有2种状态，即IDLE和ACTIVE。水印状态元素在源头生成，
 * 并且可以通过拓扑的任务传播。它们直接推断发射任务的当前状态；如果 SourceStreamTask 或 StreamTask 将暂时停止以发出任何水印（即空闲）， 则发出
 * IDLE，并在恢复这样做（即处于活动状态）时发出 ACTIVE。任务在空闲和活动之间切换后，负责将其状态进一步传播到下游。下面解释源任务和下游任务被视为空闲或活动的情况：
 * 源任务：如果源任务的头操作员（即 StreamSource）在无限期的时间内不会发出水印，则认为源任务是空闲的。例如，对于 Flink 的 Kafka Consumer，
 * 源最初可能没有分配的分区可供读取，或者无法从分配的分区中读取记录。一旦头部 StreamSource 操作员检测到它将恢复发送数据，则认为源任务处于活动状态。 StreamSources
 * 负责切换包含源任务的状态，并确保在任务空闲时不会发出水印。应该通过
 * org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext 实现对源强制执行此保证。
 * 下游任务：如果下游任务的所有输入流都是空闲的，则认为下游任务是空闲的，即从所有输入流中最后收到的 Watermark Status 元素是
 * IDLE。只要它的一个输入流是活动的，即从输入流中最后接收到的水印状态元素是活动的，任务就是活动的。
 * 在下游任务中收到的水印状态元素也会影响和控制其操作员如何处理和推进水印。下面描述了效果（逻辑被实现为 StatusWatermarkValve，下游任务应该将其用于此类目的）：
 * 由于可能存在水印生成器，它们可能在拓扑中间的任何位置产生水印，而不管算子是否有输入数据，因此必须在转发算子发出的水印之前检查任务的当前状态。如果状态实际上是空闲的，则必须阻止水印。
 * 对于有多个输入流的下游任务，在决定是否推进时，不应考虑暂时空闲的输入流的水印，或已恢复活动但其水印低于算子整体最小水印的水印。水印并通过运营商链传播。
 * 请注意，要通知下游任务源任务已永久关闭并且将不再发送任何更多元素，源仍应发送 Watermark.MAX_WATERMARK 而不是 IDLE。水印状态元素仅用作临时状态的标记。
 */
@Internal
public final class WatermarkStatus extends StreamElement {

    public static final int IDLE_STATUS = -1;
    public static final int ACTIVE_STATUS = 0;

    public static final WatermarkStatus IDLE = new WatermarkStatus(IDLE_STATUS);
    public static final WatermarkStatus ACTIVE = new WatermarkStatus(ACTIVE_STATUS);

    public final int status;

    public WatermarkStatus(int status) {
        if (status != IDLE_STATUS && status != ACTIVE_STATUS) {
            throw new IllegalArgumentException(
                    "Invalid status value for WatermarkStatus; "
                            + "allowed values are "
                            + ACTIVE_STATUS
                            + " (for ACTIVE) and "
                            + IDLE_STATUS
                            + " (for IDLE).");
        }

        this.status = status;
    }

    public boolean isIdle() {
        return this.status == IDLE_STATUS;
    }

    public boolean isActive() {
        return !isIdle();
    }

    public int getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || o != null
                        && o.getClass() == WatermarkStatus.class
                        && ((WatermarkStatus) o).status == this.status;
    }

    @Override
    public int hashCode() {
        return status;
    }

    @Override
    public String toString() {
        String statusStr = (status == ACTIVE_STATUS) ? "ACTIVE" : "IDLE";
        return "WatermarkStatus(" + statusStr + ")";
    }
}
