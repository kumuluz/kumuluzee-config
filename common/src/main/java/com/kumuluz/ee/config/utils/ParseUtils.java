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

import java.util.Optional;

/**
 * Util class for parsing configuration values.
 *
 * @author Urban Malc
 * @author Jan Meznarič
 * @since 1.0.0
 */
public class ParseUtils {

    public static Optional<Boolean> parseOptionalStringToOptionalBoolean(Optional<String> optString) {
        return optString.map(Boolean::valueOf);
    }

    public static Optional<Integer> parseOptionalStringToOptionalInteger(Optional<String> optString) {
        if (optString.isPresent()) {
            try {
                return Optional.of(Integer.valueOf(optString.get()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public static Optional<Long> parseOptionalStringToOptionalLong(Optional<String> optString) {
        if (optString.isPresent()) {
            try {
                return Optional.of(Long.valueOf(optString.get()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public static Optional<Double> parseOptionalStringToOptionalDouble(Optional<String> optString) {
        if (optString.isPresent()) {
            try {
                return Optional.of(Double.valueOf(optString.get()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public static Optional<Float> parseOptionalStringToOptionalFloat(Optional<String> optString) {
        if (optString.isPresent()) {
            try {
                return Optional.of(Float.valueOf(optString.get()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }

        } else {
            return Optional.empty();
        }
    }
}
