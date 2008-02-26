/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.monitoring;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.monitoring.impl.DefaultStopWatch;

/**
 * Sometime we need to compare elapsed time from a high level process with fine-grained
 * sub-processes to find bottlenecks or errors. The <code>ExecutionStack</code> allows
 * the application to store the running StopWatches and maintain an execution plan, where
 * it can later retrieve all StopWatches and compare times.
 * <p>
 * It can also be used to exclude waiting time when an external service is called, simply by
 * retrieving all running stopWatches and pause/resume then.
 * <p>
 * The <code>clear</code> method MUST be called after process has finished to
 * clear the stack as the thread may start another process.
 *
 * @author <a href="mailto:nicolas@apache.org">Nicolas De Loof</a>
 */
public class ExecutionStack
{
    private ExecutionStack()
    {
        super();
    }

    private static ThreadLocal<List<DefaultStopWatch>> local = new ThreadLocal<List<DefaultStopWatch>>();

    public static void push( DefaultStopWatch stopWatch )
    {
        getExecution().add( stopWatch );
    }

    /**
     * Pause all running stopWatches
     */
    public static void pause()
    {
        for ( StopWatch stopWatch : getExecution() )
        {
            stopWatch.pause();
        }
    }

    /**
     * Resume all stopWatches
     */
    public static void resume()
    {
        for ( StopWatch stopWatch : getExecution() )
        {
            stopWatch.resume();
        }
    }

    /**
     *
     * @return <code>true</code> if all stopWatches are stopped (execution is finished)
     */
    public static boolean isFinished()
    {
        for ( StopWatch stopWatch : getExecution() )
        {
            if (!stopWatch.isStoped())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the ordered list of StopWatches used during execution
     */
    public static List<DefaultStopWatch> getExecution()
    {
        List<DefaultStopWatch> exec = local.get();
        if (exec == null)
        {
            exec = new LinkedList<DefaultStopWatch>();
            local.set( exec );
        }
        return local.get();
    }

    public static void clear()
    {
        getExecution().clear();
    }

}
