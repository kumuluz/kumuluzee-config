/*
 *  Copyright (c) 2014-2017 Kumuluz and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
*/

package com.kumuluz.ee.config.utils;

import com.kumuluz.ee.configuration.utils.ConfigurationUtil;

import java.util.Optional;

/**
 * Util class for getting initialization parameters.
 *
 * @author Jan Meznarič, Urban Malc
 */
public class InitializationUtils {

    public static String getNamespace(ConfigurationUtil configurationUtil, String implementation) {
        String implementationNamespace = configurationUtil.get("kumuluzee.config." + implementation + ".namespace")
                .orElse(null);
        if (implementationNamespace != null && !implementationNamespace.isEmpty()) {
            return implementationNamespace;
        }

        String env = configurationUtil.get("kumuluzee.env").orElse(null);
        if (env != null && !env.isEmpty()) {
            return "environments." + env + ".services";
        } else {
            return "environments.dev.services";
        }
    }

    public static int getStartRetryDelayMs(ConfigurationUtil configurationUtil, String implementation) {
        Optional<Integer> universalConfig = configurationUtil.getInteger("kumuluzee.config.start-retry-delay-ms");
        if(universalConfig.isPresent()) {
            return universalConfig.get();
        } else {
            return configurationUtil.getInteger("kumuluzee.config." + implementation + ".start-retry-delay-ms")
                    .orElse(500);
        }
    }

    public static int getMaxRetryDelayMs(ConfigurationUtil configurationUtil, String implementation) {
        Optional<Integer> universalConfig = configurationUtil.getInteger("kumuluzee.config.max-retry-delay-ms");
        if(universalConfig.isPresent()) {
            return universalConfig.get();
        } else {
            return configurationUtil.getInteger("kumuluzee.config." + implementation + ".max-retry-delay-ms")
                    .orElse(900000);
        }
    }
}
