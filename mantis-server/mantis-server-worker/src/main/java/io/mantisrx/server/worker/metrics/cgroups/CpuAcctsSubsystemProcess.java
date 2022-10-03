/*
 * Copyright 2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mantisrx.server.worker.metrics.cgroups;

import io.mantisrx.server.master.client.config.Usage;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Uses cpuacct.stat file for setting various metrics.
 * Code uses ideas from the <a href="https://github.com/apache/mesos/blob/96339efb53f7cdf1126ead7755d2b83b435e3263/src/slave/containerizer/mesos/isolators/cgroups/subsystems/cpuacct.cpp#L90-L108">mesos implementation here</a> .
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class CpuAcctsSubsystemProcess implements SubsystemProcess {
    private final Cgroup cgroup;

    @Override
    public void getUsage(Usage.UsageBuilder resourceUsageBuilder) throws IOException {
        final Map<String, Long> stat = cgroup.getStats("cpuacct", "cpuacct.stat");
        Optional<Long> user = Optional.ofNullable(stat.get("user"));
        Optional<Long> system = Optional.ofNullable(stat.get("system"));

        if (user.isPresent() && system.isPresent()) {
            resourceUsageBuilder.cpusUserTimeSecs(user.get());
            resourceUsageBuilder.cpusSystemTimeSecs(system.get());
        } else {
            log.warn("Expected metrics not found; Found stats={}", stat);
        }

        Long quota = cgroup.getMetric("cpuacct", "cpu.cfs_quota_us");
        Long period = cgroup.getMetric("cpuacct", "cpu.cfs_period_us");
        double quotaCount = 0;
        if (quota > -1 && period > 0) {
            quotaCount = Math.ceil((float) quota / (float) period);
        }

        if (quotaCount > 0.) {
            resourceUsageBuilder.cpusLimit(quotaCount);
        } else {
            log.warn("quota={} & period={} are not configured correctly", quota, period);
        }
    }
}